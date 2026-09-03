package com.cbstudio.wearwallet.core.multichain.util

import com.cbstudio.wearwallet.core.domain.model.intent.ConfirmedEvmTransactionIntent
import com.cbstudio.wearwallet.core.domain.model.quantities.*
import com.cbstudio.wearwallet.core.multichain.monero.crypto.keccak256
import io.github.iml1s.crypto.Secp256k1Pure
import io.github.iml1s.crypto.Keccak256
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.Sign

/**
 * Ethereum 交易簽名工具 (Directive R5)
 * 實現 EIP-155 Legacy 與 EIP-1559 (0x02) Typed 交易簽名與驗證。
 * 嚴格限制所有參數必須為 Domain Typed Quantities (Nonce, ChainId, Wei, GasLimit, EvmAddress, Calldata, EvmEnvelope).
 */
object EthereumSigner {

    /**
     * 計算 EIP-155 Legacy 交易未簽名摘要 (Keccak-256)
     */
    fun computeLegacyTransactionDigest(
        nonce: Nonce,
        gasPrice: Wei,
        gasLimit: GasLimit,
        toAddress: EvmAddress,
        value: Wei,
        data: Calldata,
        chainId: ChainId
    ): ByteArray {
        val cleanToAddress = toAddress.value.removePrefix("0x")
        val rlpForSigning = buildLegacyRLPForSigning(
            nonce = nonce,
            gasPrice = gasPrice,
            gasLimit = gasLimit,
            cleanToAddress = cleanToAddress,
            value = value,
            data = data,
            chainId = chainId
        )
        return rlpForSigning.keccak256()
    }

    /**
     * 計算 EIP-1559 (0x02) 交易未簽名摘要 (Keccak-256)
     */
    fun computeEip1559TransactionDigest(
        chainId: ChainId,
        nonce: Nonce,
        maxPriorityFeePerGas: Wei,
        maxFeePerGas: Wei,
        gasLimit: GasLimit,
        toAddress: EvmAddress,
        value: Wei,
        data: Calldata,
        accessList: List<Any> = emptyList()
    ): ByteArray {
        val cleanToAddress = toAddress.value.removePrefix("0x")
        val toBytes = if (cleanToAddress.isEmpty()) byteArrayOf() else cleanToAddress.hexToByteArray()
        val dataBytes = if (data.isEmpty()) byteArrayOf() else data.toCleanHex().hexToByteArray()

        val unsignedList = listOf(
            chainId.toLong(),
            nonce.toLong(),
            maxPriorityFeePerGas.value,
            maxFeePerGas.value,
            gasLimit.toLong(),
            toBytes,
            value.value,
            dataBytes,
            accessList
        )
        val unsignedRlp = RLPEncoder.encode(unsignedList)
        val unsignedPayload = byteArrayOf(0x02.toByte()) + unsignedRlp
        return unsignedPayload.keccak256()
    }

    /**
     * 使用簽名重構已簽名 Legacy 交易
     */
    fun reconstructSignedLegacyTransaction(
        nonce: Nonce,
        gasPrice: Wei,
        gasLimit: GasLimit,
        toAddress: EvmAddress,
        value: Wei,
        data: Calldata,
        chainId: ChainId,
        signatureBytes: ByteArray
    ): String {
        require(signatureBytes.size in 64..65) { "Signature must be 64 or 65 bytes" }
        val r = signatureBytes.copyOfRange(0, 32)
        val s = signatureBytes.copyOfRange(32, 64)
        val yParity = if (signatureBytes.size == 65) {
            val rawV = signatureBytes[64].toInt() and 0xFF
            if (rawV >= 27) (rawV - 27) % 2 else rawV % 2
        } else 0

        val v = yParity.toLong() + chainId.toLong() * 2 + 35
        val cleanToAddress = toAddress.value.removePrefix("0x")

        val signedTxBytes = buildSignedLegacyTransaction(
            nonce = nonce,
            gasPrice = gasPrice,
            gasLimit = gasLimit,
            cleanToAddress = cleanToAddress,
            value = value,
            data = data,
            v = v,
            r = r,
            s = s
        )
        return RLPEncoder.toHexString(signedTxBytes)
    }

    /**
     * 使用簽名重構已簽名 EIP-1559 交易
     */
    fun reconstructSignedEip1559Transaction(
        chainId: ChainId,
        nonce: Nonce,
        maxPriorityFeePerGas: Wei,
        maxFeePerGas: Wei,
        gasLimit: GasLimit,
        toAddress: EvmAddress,
        value: Wei,
        data: Calldata,
        accessList: List<Any> = emptyList(),
        signatureBytes: ByteArray
    ): String {
        require(signatureBytes.size in 64..65) { "Signature must be 64 or 65 bytes" }
        val r = signatureBytes.copyOfRange(0, 32)
        val s = signatureBytes.copyOfRange(32, 64)
        val yParity = if (signatureBytes.size == 65) {
            val rawV = signatureBytes[64].toInt() and 0xFF
            if (rawV >= 27) (rawV - 27) % 2 else rawV % 2
        } else 0

        val cleanToAddress = toAddress.value.removePrefix("0x")
        val toBytes = if (cleanToAddress.isEmpty()) byteArrayOf() else cleanToAddress.hexToByteArray()
        val dataBytes = if (data.isEmpty()) byteArrayOf() else data.toCleanHex().hexToByteArray()

        val rBigInt = BigInteger.fromByteArray(r, Sign.POSITIVE)
        val sBigInt = BigInteger.fromByteArray(s, Sign.POSITIVE)

        val signedList = listOf(
            chainId.toLong(),
            nonce.toLong(),
            maxPriorityFeePerGas.value,
            maxFeePerGas.value,
            gasLimit.toLong(),
            toBytes,
            value.value,
            dataBytes,
            accessList,
            yParity,
            rBigInt,
            sBigInt
        )
        val signedRlp = RLPEncoder.encode(signedList)
        val signedPayload = byteArrayOf(0x02.toByte()) + signedRlp
        return RLPEncoder.toHexString(signedPayload)
    }

    /**
     * 驗證已簽名交易與 Intent 逐欄 1-by-1 一致性並恢復發送者地址
     */
    fun verifySignedTransactionMatchesIntent(
        signedTxHex: String,
        intent: ConfirmedEvmTransactionIntent
    ) {
        val recoveredSender = recoverSenderFromSignedTransaction(signedTxHex)
        if (!recoveredSender.equals(intent.sender.value, ignoreCase = true)) {
            throw IllegalStateException("Signed transaction sender recovery mismatch: recovered $recoveredSender vs intent ${intent.sender.value}")
        }

        val cleanHex = signedTxHex.removePrefix("0x").removePrefix("0X")
        val rawBytes = cleanHex.hexToByteArray()

        if (rawBytes[0] == 0x02.toByte()) {
            require(intent.envelopeType == EvmEnvelope.EIP1559) { "Envelope type mismatch: signed is EIP-1559 but intent is ${intent.envelopeType}" }
            val rlpPayload = rawBytes.copyOfRange(1, rawBytes.size)
            val items = RLPEncoder.decode(rlpPayload) as List<*>
            val chainId = parseRLPLong(items[0])
            val nonce = parseRLPLong(items[1])
            val maxPriorityFee = parseRLPBigInt(items[2])
            val maxFee = parseRLPBigInt(items[3])
            val gasLimit = parseRLPLong(items[4])
            val toBytes = items[5] as ByteArray
            val value = parseRLPBigInt(items[6])
            val dataBytes = items[7] as ByteArray

            val expectedTo = (intent.tokenContract ?: intent.recipient).value.removePrefix("0x").lowercase()
            val expectedValue = if (intent.tokenContract != null) BigInteger.ZERO else intent.nativeValue.value
            val expectedData = intent.calldata.toCleanHex().lowercase()

            require(chainId == intent.executionContext.chainId) { "Signed tx chainId mismatch: signed $chainId vs intent ${intent.executionContext.chainId}" }
            require(nonce == intent.nonce.toLong()) { "Signed tx nonce mismatch: signed $nonce vs intent ${intent.nonce.toLong()}" }
            require(gasLimit == intent.gasLimit.toLong()) { "Signed tx gasLimit mismatch: signed $gasLimit vs intent ${intent.gasLimit.toLong()}" }
            require(maxFee == intent.gasPrice.value) { "Signed tx gasPrice/maxFee mismatch: signed $maxFee vs intent ${intent.gasPrice.value}" }
            require(toBytes.toHexString().lowercase() == expectedTo) { "Signed tx recipient/contract mismatch: signed ${toBytes.toHexString()} vs expected $expectedTo" }
            require(value == expectedValue) { "Signed tx value mismatch: signed $value vs intent $expectedValue" }
            require(dataBytes.toHexString().lowercase() == expectedData) { "Signed tx calldata mismatch: signed ${dataBytes.toHexString()} vs intent $expectedData" }
        } else {
            require(intent.envelopeType == EvmEnvelope.LEGACY) { "Envelope type mismatch: signed is LEGACY but intent is ${intent.envelopeType}" }
            val items = RLPEncoder.decode(rawBytes) as List<*>
            val nonce = parseRLPLong(items[0])
            val gasPrice = parseRLPBigInt(items[1])
            val gasLimit = parseRLPLong(items[2])
            val toBytes = items[3] as ByteArray
            val value = parseRLPBigInt(items[4])
            val dataBytes = items[5] as ByteArray

            val expectedTo = (intent.tokenContract ?: intent.recipient).value.removePrefix("0x").lowercase()
            val expectedValue = if (intent.tokenContract != null) BigInteger.ZERO else intent.nativeValue.value
            val expectedData = intent.calldata.toCleanHex().lowercase()

            if (items.size >= 7) {
                val v = parseRLPLong(items[6])
                if (v >= 35L) {
                    val decodedChainId = (v - 35L) / 2L
                    require(decodedChainId == intent.executionContext.chainId) { "Signed legacy tx chainId mismatch: decoded $decodedChainId vs intent ${intent.executionContext.chainId}" }
                }
            }

            require(nonce == intent.nonce.toLong()) { "Signed tx nonce mismatch: signed $nonce vs intent ${intent.nonce.toLong()}" }
            require(gasPrice == intent.gasPrice.value) { "Signed tx gasPrice mismatch: signed $gasPrice vs intent ${intent.gasPrice.value}" }
            require(gasLimit == intent.gasLimit.toLong()) { "Signed tx gasLimit mismatch: signed $gasLimit vs intent ${intent.gasLimit.toLong()}" }
            require(toBytes.toHexString().lowercase() == expectedTo) { "Signed tx recipient/contract mismatch: signed ${toBytes.toHexString()} vs expected $expectedTo" }
            require(value == expectedValue) { "Signed tx value mismatch: signed $value vs intent $expectedValue" }
            require(dataBytes.toHexString().lowercase() == expectedData) { "Signed tx calldata mismatch: signed ${dataBytes.toHexString()} vs intent $expectedData" }
        }
    }

    /**
     * 簽署 EIP-155 Legacy 交易 (Envelope 0x00 / EIP-155)
     */
    fun signLegacyTransaction(
        nonce: Nonce,
        gasPrice: Wei,
        gasLimit: GasLimit,
        toAddress: EvmAddress,
        value: Wei,
        data: Calldata,
        privateKeyBytes: ByteArray,
        chainId: ChainId
    ): String {
        require(privateKeyBytes.size == 32) { "Private key bytes size must be 32 bytes" }

        val cleanToAddress = toAddress.value.removePrefix("0x")

        // 1. 構建用於簽名的 RLP 數據 [nonce, gasPrice, gasLimit, to, value, data, chainId, 0, 0]
        val rlpForSigning = buildLegacyRLPForSigning(
            nonce = nonce,
            gasPrice = gasPrice,
            gasLimit = gasLimit,
            cleanToAddress = cleanToAddress,
            value = value,
            data = data,
            chainId = chainId
        )

        // 2. 計算 Keccak-256 哈希
        val txHash = rlpForSigning.keccak256()

        // 3. 使用 Secp256k1Pure 進行 ECDSA 確定性簽名 (EIP-2 低 s 值標準化)
        val signature = Secp256k1Pure.signWithRecovery(txHash, privateKeyBytes)

        val yParity = signature.yParity
        require(yParity == 0 || yParity == 1) { "yParity/recoveryId 無法確定或不在有效範圍 [0, 1]" }

        val r = signature.r
        val s = signature.s

        // 4. EIP-155 計算 v = yParity + chainId * 2 + 35
        val v = yParity.toLong() + chainId.toLong() * 2 + 35

        // 5. 構建最終簽名交易 [nonce, gasPrice, gasLimit, to, value, data, v, r, s]
        val signedTxBytes = buildSignedLegacyTransaction(
            nonce = nonce,
            gasPrice = gasPrice,
            gasLimit = gasLimit,
            cleanToAddress = cleanToAddress,
            value = value,
            data = data,
            v = v,
            r = r,
            s = s
        )

        val signedTxHex = RLPEncoder.toHexString(signedTxBytes)

        // 6. 後置校驗：從已簽名的交易中 recover 出 sender 並驗證與私鑰產生的公鑰一致
        val expectedPubKeyPoint = Secp256k1Pure.generatePublicKeyPoint(privateKeyBytes)
        val expectedUncompressed = Secp256k1Pure.encodePublicKey(expectedPubKeyPoint, compressed = false)
        val expectedAddress = toEthereumAddress(expectedUncompressed)

        val recoveredSender = recoverSenderFromSignedTransaction(signedTxHex)
        if (!recoveredSender.equals(expectedAddress, ignoreCase = true)) {
            throw IllegalStateException("Post-sign recovery verification failed: recovered $recoveredSender vs expected $expectedAddress")
        }

        return signedTxHex
    }

    /**
     * 簽署 EIP-1559 交易 (Envelope 0x02)
     */
    fun signEip1559Transaction(
        chainId: ChainId,
        nonce: Nonce,
        maxPriorityFeePerGas: Wei,
        maxFeePerGas: Wei,
        gasLimit: GasLimit,
        toAddress: EvmAddress,
        value: Wei,
        data: Calldata,
        accessList: List<Any> = emptyList(),
        privateKeyBytes: ByteArray
    ): String {
        require(privateKeyBytes.size == 32) { "Private key bytes size must be 32 bytes" }

        val cleanToAddress = toAddress.value.removePrefix("0x")
        val toBytes = if (cleanToAddress.isEmpty()) byteArrayOf() else cleanToAddress.hexToByteArray()
        val dataBytes = if (data.isEmpty()) byteArrayOf() else data.toCleanHex().hexToByteArray()

        // 1. Build unsigned EIP-1559 RLP list: [chainId, nonce, maxPriorityFeePerGas, maxFeePerGas, gasLimit, to, value, data, accessList]
        val unsignedList = listOf(
            chainId.toLong(),
            nonce.toLong(),
            maxPriorityFeePerGas.value,
            maxFeePerGas.value,
            gasLimit.toLong(),
            toBytes,
            value.value,
            dataBytes,
            accessList
        )
        val unsignedRlp = RLPEncoder.encode(unsignedList)
        val unsignedPayload = byteArrayOf(0x02.toByte()) + unsignedRlp

        // 2. Hash unsigned payload
        val txHash = unsignedPayload.keccak256()

        // 3. ECDSA sign with secp256k1 (deterministic k, low s)
        val signature = Secp256k1Pure.signWithRecovery(txHash, privateKeyBytes)

        val yParity = signature.yParity
        require(yParity == 0 || yParity == 1) { "yParity must be 0 or 1" }

        val r = signature.r
        val s = signature.s

        val rBigInt = BigInteger.fromByteArray(r, Sign.POSITIVE)
        val sBigInt = BigInteger.fromByteArray(s, Sign.POSITIVE)

        // 4. Build signed EIP-1559 payload: 0x02 || rlp([chainId, nonce, maxPriorityFeePerGas, maxFeePerGas, gasLimit, to, value, data, accessList, yParity, r, s])
        val signedList = listOf(
            chainId.toLong(),
            nonce.toLong(),
            maxPriorityFeePerGas.value,
            maxFeePerGas.value,
            gasLimit.toLong(),
            toBytes,
            value.value,
            dataBytes,
            accessList,
            yParity,
            rBigInt,
            sBigInt
        )
        val signedRlp = RLPEncoder.encode(signedList)
        val signedPayload = byteArrayOf(0x02.toByte()) + signedRlp

        val signedTxHex = RLPEncoder.toHexString(signedPayload)

        // 5. Post-sign recovery check
        val expectedPubKeyPoint = Secp256k1Pure.generatePublicKeyPoint(privateKeyBytes)
        val expectedUncompressed = Secp256k1Pure.encodePublicKey(expectedPubKeyPoint, compressed = false)
        val expectedAddress = toEthereumAddress(expectedUncompressed)

        val recoveredSender = recoverSenderFromSignedTransaction(signedTxHex)
        if (!recoveredSender.equals(expectedAddress, ignoreCase = true)) {
            throw IllegalStateException("Post-sign recovery verification failed for EIP-1559: recovered $recoveredSender vs expected $expectedAddress")
        }

        return signedTxHex
    }

    /**
     * Unified Typed Signing entry point according to [EvmEnvelope].
     */
    fun signTypedTransaction(
        envelopeType: EvmEnvelope,
        chainId: ChainId,
        nonce: Nonce,
        maxPriorityFeePerGas: Wei? = null,
        maxFeePerGas: Wei? = null,
        gasPrice: Wei? = null,
        gasLimit: GasLimit,
        toAddress: EvmAddress,
        value: Wei,
        data: Calldata,
        accessList: List<Any> = emptyList(),
        privateKeyBytes: ByteArray
    ): String {
        return when (envelopeType) {
            EvmEnvelope.LEGACY -> {
                val effectiveGasPrice = gasPrice ?: maxFeePerGas ?: throw IllegalArgumentException("gasPrice is required for LEGACY transaction")
                signLegacyTransaction(
                    nonce = nonce,
                    gasPrice = effectiveGasPrice,
                    gasLimit = gasLimit,
                    toAddress = toAddress,
                    value = value,
                    data = data,
                    privateKeyBytes = privateKeyBytes,
                    chainId = chainId
                )
            }
            EvmEnvelope.EIP1559 -> {
                val priorityFee = maxPriorityFeePerGas ?: throw IllegalArgumentException("maxPriorityFeePerGas is required for EIP1559 transaction")
                val maxFee = maxFeePerGas ?: throw IllegalArgumentException("maxFeePerGas is required for EIP1559 transaction")
                signEip1559Transaction(
                    chainId = chainId,
                    nonce = nonce,
                    maxPriorityFeePerGas = priorityFee,
                    maxFeePerGas = maxFee,
                    gasLimit = gasLimit,
                    toAddress = toAddress,
                    value = value,
                    data = data,
                    accessList = accessList,
                    privateKeyBytes = privateKeyBytes
                )
            }
            EvmEnvelope.EIP2930 -> throw IllegalArgumentException("EIP-2930 transactions are unsupported")
        }
    }

    /**
     * 從已簽名的 RLP Hex 交易中解碼並恢復發送者地址
     * 支援 Legacy (0x00/EIP-155) 與 EIP-1559 (0x02) 交易。
     */
    fun recoverSenderFromSignedTransaction(signedTxHex: String): String {
        val cleanHex = signedTxHex.removePrefix("0x").removePrefix("0X")
        require(cleanHex.isNotEmpty()) { "Signed tx hex cannot be empty" }

        val rawBytes = cleanHex.hexToByteArray()

        if (rawBytes[0] == 0x02.toByte()) {
            // EIP-1559 transaction (Envelope 0x02)
            val rlpPayload = rawBytes.copyOfRange(1, rawBytes.size)
            val decoded = RLPEncoder.decode(rlpPayload)
            val items = (decoded as? List<*>) ?: throw IllegalArgumentException("Invalid EIP-1559 RLP structure")
            require(items.size == 12) { "EIP-1559 transaction RLP must have 12 items, got ${items.size}" }

            val chainId = parseRLPLong(items[0])
            val nonce = parseRLPLong(items[1])
            val maxPriorityFeePerGas = parseRLPBigInt(items[2])
            val maxFeePerGas = parseRLPBigInt(items[3])
            val gasLimit = parseRLPLong(items[4])
            val toBytes = items[5] as ByteArray
            val value = parseRLPBigInt(items[6])
            val dataBytes = items[7] as ByteArray
            val accessList = items[8] as List<*>
            val yParity = parseRLPLong(items[9]).toInt()
            val rBytes = parseRLP32Bytes(items[10])
            val sBytes = parseRLP32Bytes(items[11])

            // Reconstruct unsigned payload for hash calculation
            val unsignedList = listOf(
                chainId,
                nonce,
                maxPriorityFeePerGas,
                maxFeePerGas,
                gasLimit,
                toBytes,
                value,
                dataBytes,
                accessList
            )
            val unsignedRlp = RLPEncoder.encode(unsignedList)
            val unsignedPayload = byteArrayOf(0x02.toByte()) + unsignedRlp
            val txHash = unsignedPayload.keccak256()

            val z = Secp256k1Pure.BigInteger.fromByteArray(txHash)
            val r = Secp256k1Pure.BigInteger.fromByteArray(rBytes)
            val s = Secp256k1Pure.BigInteger.fromByteArray(sBytes)

            val pointQ = Secp256k1Pure.recoverPublicKeyPoint(z, r, s, yParity)
                ?: throw IllegalStateException("Failed to recover public key point from EIP-1559 signature")

            val uncompressed = Secp256k1Pure.encodePublicKey(pointQ, compressed = false)
            return toEthereumAddress(uncompressed)
        } else {
            // Legacy transaction (9 items)
            val decoded = RLPEncoder.decode(rawBytes)
            val items = (decoded as? List<*>) ?: throw IllegalArgumentException("Invalid RLP structure")
            require(items.size == 9) { "Legacy transaction RLP must have 9 items" }

            val nonce = parseRLPLong(items[0])
            val gasPrice = parseRLPBigInt(items[1])
            val gasLimit = parseRLPLong(items[2])
            val toBytes = items[3] as ByteArray
            val value = parseRLPBigInt(items[4])
            val dataBytes = items[5] as ByteArray
            val v = parseRLPLong(items[6])
            val rBytes = parseRLP32Bytes(items[7])
            val sBytes = parseRLP32Bytes(items[8])

            // EIP-155 解析 chainId 與 yParity
            val chainId = if (v >= 35) (v - 35) / 2 else 0L
            val yParity = if (v >= 35) ((v - 35) % 2).toInt() else ((v - 27) % 2).toInt()

            val toAddressHex = toBytes.toHexString()
            val gasPriceHex = "0x" + gasPrice.toString(16)
            val gasLimitHex = "0x" + gasLimit.toString(16)
            val valueHex = "0x" + value.toString(16)
            val dataHex = if (dataBytes.isEmpty()) "" else "0x" + dataBytes.toHexString()

            val rlpForSigning = buildLegacyRLPForSigning(
                nonce = Nonce.fromLong(nonce),
                gasPrice = Wei.fromWei(gasPrice),
                gasLimit = GasLimit.fromLong(gasLimit),
                cleanToAddress = toAddressHex,
                value = Wei.fromWei(value),
                data = Calldata.fromHex(dataHex),
                chainId = ChainId.fromLong(if (chainId > 0) chainId else 1L)
            )
            val txHash = rlpForSigning.keccak256()

            val z = Secp256k1Pure.BigInteger.fromByteArray(txHash)
            val r = Secp256k1Pure.BigInteger.fromByteArray(rBytes)
            val s = Secp256k1Pure.BigInteger.fromByteArray(sBytes)

            val pointQ = Secp256k1Pure.recoverPublicKeyPoint(z, r, s, yParity)
                ?: throw IllegalStateException("Failed to recover public key point from signature")

            val uncompressed = Secp256k1Pure.encodePublicKey(pointQ, compressed = false)
            return toEthereumAddress(uncompressed)
        }
    }

    private fun buildLegacyRLPForSigning(
        nonce: Nonce,
        gasPrice: Wei,
        gasLimit: GasLimit,
        cleanToAddress: String,
        value: Wei,
        data: Calldata,
        chainId: ChainId
    ): ByteArray {
        val cleanData = data.toCleanHex()
        val cleanTo = cleanToAddress.removePrefix("0x").removePrefix("0X")

        val rlpList = listOf(
            nonce.toLong(),
            gasPrice.value,
            gasLimit.toLong(),
            if (cleanTo.isEmpty()) byteArrayOf() else cleanTo.hexToByteArray(),
            value.value,
            if (cleanData.isEmpty()) byteArrayOf() else cleanData.hexToByteArray(),
            chainId.toLong(),
            0,
            0
        )

        return RLPEncoder.encode(rlpList)
    }

    private fun buildSignedLegacyTransaction(
        nonce: Nonce,
        gasPrice: Wei,
        gasLimit: GasLimit,
        cleanToAddress: String,
        value: Wei,
        data: Calldata,
        v: Long,
        r: ByteArray,
        s: ByteArray
    ): ByteArray {
        val cleanData = data.toCleanHex()
        val cleanTo = cleanToAddress.removePrefix("0x").removePrefix("0X")

        val rBigInt = BigInteger.fromByteArray(r, Sign.POSITIVE)
        val sBigInt = BigInteger.fromByteArray(s, Sign.POSITIVE)

        val rlpList = listOf(
            nonce.toLong(),
            gasPrice.value,
            gasLimit.toLong(),
            if (cleanTo.isEmpty()) byteArrayOf() else cleanTo.hexToByteArray(),
            value.value,
            if (cleanData.isEmpty()) byteArrayOf() else cleanData.hexToByteArray(),
            v,
            rBigInt,
            sBigInt
        )

        return RLPEncoder.encode(rlpList)
    }

    private fun parseRLPLong(item: Any?): Long {
        if (item is Long) return item
        if (item is Int) return item.toLong()
        if (item is ByteArray) {
            if (item.isEmpty()) return 0L
            return BigInteger.fromByteArray(item, Sign.POSITIVE).toString(10).toLong()
        }
        if (item is BigInteger) return item.toString(10).toLong()
        return 0L
    }

    private fun parseRLPBigInt(item: Any?): BigInteger {
        if (item is BigInteger) return item
        if (item is Long) return BigInteger.fromLong(item)
        if (item is Int) return BigInteger.fromInt(item)
        if (item is ByteArray) {
            if (item.isEmpty()) return BigInteger.ZERO
            return BigInteger.fromByteArray(item, Sign.POSITIVE)
        }
        return BigInteger.ZERO
    }

    private fun parseRLP32Bytes(item: Any?): ByteArray {
        val bytes = when (item) {
            is ByteArray -> item
            is BigInteger -> item.toByteArray()
            else -> byteArrayOf()
        }
        return if (bytes.size < 32) {
            ByteArray(32 - bytes.size) + bytes
        } else if (bytes.size > 32) {
            bytes.copyOfRange(bytes.size - 32, bytes.size)
        } else {
            bytes
        }
    }

    fun deriveAddressFromPrivateKey(privateKeyHex: String): String {
        val cleanKey = privateKeyHex.removePrefix("0x").removePrefix("0X")
        require(cleanKey.matches(Regex("^[0-9a-fA-F]{64}$"))) { "Invalid private key format: must be 64 hex characters" }
        val privateKeyBytes = cleanKey.hexToByteArray()
        val pubKeyPoint = Secp256k1Pure.generatePublicKeyPoint(privateKeyBytes)
        val uncompressed = Secp256k1Pure.encodePublicKey(pubKeyPoint, compressed = false)
        return toEthereumAddress(uncompressed)
    }

    fun toEthereumAddress(uncompressedPubKey: ByteArray): String {
        val dataToHash = uncompressedPubKey.copyOfRange(1, uncompressedPubKey.size)
        val hash = Keccak256.hash(dataToHash)
        val addressBytes = hash.copyOfRange(12, 32)
        val rawHex = addressBytes.toHexString().lowercase()

        // EIP-55 Checksum
        val hashHex = Keccak256.hash(rawHex.encodeToByteArray()).toHexString()
        val result = StringBuilder("0x")
        for (i in rawHex.indices) {
            val char = rawHex[i]
            if (char in '0'..'9') {
                result.append(char)
            } else {
                if (hashHex[i] >= '8') {
                    result.append(char.uppercaseChar())
                } else {
                    result.append(char)
                }
            }
        }
        return result.toString()
    }

    private fun String.hexToByteArray(): ByteArray {
        val cleanHex = this.removePrefix("0x").removePrefix("0X")
        if (cleanHex.isEmpty()) return byteArrayOf()
        require(cleanHex.length % 2 == 0) { "Hex string length must be even" }
        val data = ByteArray(cleanHex.length / 2)
        for (i in data.indices) {
            val highNibble = cleanHex[i * 2].digitToInt(16)
            val lowNibble = cleanHex[i * 2 + 1].digitToInt(16)
            data[i] = ((highNibble shl 4) + lowNibble).toByte()
        }
        return data
    }

    private fun ByteArray.toHexString(): String {
        return this.joinToString("") { byte ->
            val hex = (byte.toInt() and 0xFF).toString(16)
            if (hex.length == 1) "0$hex" else hex
        }
    }
}

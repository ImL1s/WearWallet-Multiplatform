package com.cbstudio.wearwallet.core.domain.model.intent

import com.cbstudio.wearwallet.core.domain.model.context.ChainExecutionContext
import com.cbstudio.wearwallet.core.domain.model.quantities.*
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.core.multichain.util.EthereumSigner
import com.cbstudio.wearwallet.core.security.toHexString

/**
 * Domain-owned 18-field immutable transaction intent data model for EVM transactions.
 */
data class ConfirmedEvmTransactionIntent(
    val walletId: String,
    val keyAlias: String = walletId,
    val sender: EvmAddress,
    val chain: MultiChainType,
    val executionContext: ChainExecutionContext,
    val envelopeType: EvmEnvelope,
    val recipient: EvmAddress,
    val tokenContract: EvmAddress?,
    val tokenSymbol: String?,
    val tokenDecimals: Int?,
    val humanAmount: String,
    val baseUnitAmount: BaseUnitAmount,
    val nativeValue: Wei,
    val calldata: Calldata,
    val nonce: Nonce,
    val gasPrice: Wei,
    val gasLimit: GasLimit,
    val fee: Wei,
    val canonicalFingerprint: String
) {
    init {
        require(walletId.isNotBlank()) { "walletId must not be blank" }
        require(keyAlias.isNotBlank()) { "keyAlias must not be blank" }
        require(humanAmount.isNotBlank()) { "humanAmount must not be blank" }
        require(canonicalFingerprint.isNotBlank()) { "canonicalFingerprint must not be blank" }
        if (tokenContract != null) {
            require(tokenDecimals != null && tokenDecimals in 0..77) {
                "tokenDecimals required when tokenContract is present"
            }
        }
        require(chain == executionContext.multiChainType) {
            "Intent chain $chain does not match executionContext.multiChainType ${executionContext.multiChainType}"
        }
        val expectedFingerprint = createFingerprint(
            walletId = walletId,
            keyAlias = keyAlias,
            sender = sender,
            chain = chain,
            executionContext = executionContext,
            envelopeType = envelopeType,
            recipient = recipient,
            tokenContract = tokenContract,
            tokenSymbol = tokenSymbol,
            tokenDecimals = tokenDecimals,
            humanAmount = humanAmount,
            baseUnitAmount = baseUnitAmount,
            nativeValue = nativeValue,
            calldata = calldata,
            nonce = nonce,
            gasPrice = gasPrice,
            gasLimit = gasLimit,
            fee = fee
        )
        require(canonicalFingerprint == expectedFingerprint) {
            "Canonical fingerprint mismatch: given '$canonicalFingerprint' vs computed '$expectedFingerprint'"
        }
    }

    val totalFee: String get() = fee.toEthString()

    /**
     * 計算 32-byte 未簽名交易 Keccak-256 摘要
     */
    fun computeSigningDigest(): ByteArray {
        val isTokenTransfer = tokenContract != null
        val targetAddress = if (isTokenTransfer) tokenContract else recipient
        val txValue = if (isTokenTransfer) Wei.ZERO else nativeValue
        val chainId = ChainId.fromLong(executionContext.chainId)

        return when (envelopeType) {
            EvmEnvelope.LEGACY -> {
                EthereumSigner.computeLegacyTransactionDigest(
                    nonce = nonce,
                    gasPrice = gasPrice,
                    gasLimit = gasLimit,
                    toAddress = targetAddress,
                    value = txValue,
                    data = calldata,
                    chainId = chainId
                )
            }
            EvmEnvelope.EIP1559 -> {
                EthereumSigner.computeEip1559TransactionDigest(
                    chainId = chainId,
                    nonce = nonce,
                    maxPriorityFeePerGas = gasPrice,
                    maxFeePerGas = gasPrice,
                    gasLimit = gasLimit,
                    toAddress = targetAddress,
                    value = txValue,
                    data = calldata
                )
            }
            else -> throw UnsupportedOperationException("Unsupported envelope type: $envelopeType")
        }
    }

    /**
     * 32-byte 交易未簽名 Keccak-256 摘要
     */
    val signingDigest: ByteArray get() = computeSigningDigest()

    /**
     * 64 字元小寫十六進制簽名指紋摘要 (P0-1 Unified Intent Fingerprint Protocol)
     */
    val signingDigestHex: String get() = computeSigningDigest().toHexString().lowercase()

    companion object {
        fun createFingerprint(
            walletId: String,
            keyAlias: String = walletId,
            sender: EvmAddress,
            chain: MultiChainType,
            executionContext: ChainExecutionContext,
            envelopeType: EvmEnvelope,
            recipient: EvmAddress,
            tokenContract: EvmAddress?,
            tokenSymbol: String?,
            tokenDecimals: Int?,
            humanAmount: String,
            baseUnitAmount: BaseUnitAmount,
            nativeValue: Wei,
            calldata: Calldata,
            nonce: Nonce,
            gasPrice: Wei,
            gasLimit: GasLimit,
            fee: Wei
        ): String {
            val raw = listOf(
                walletId,
                keyAlias,
                sender.value.lowercase(),
                chain.name,
                executionContext.multiChainType.name,
                executionContext.networkType.name,
                executionContext.chainId.toString(10),
                envelopeType.name,
                recipient.value.lowercase(),
                tokenContract?.value?.lowercase() ?: "",
                tokenSymbol ?: "",
                tokenDecimals?.toString() ?: "",
                humanAmount,
                baseUnitAmount.value.toString(10),
                nativeValue.value.toString(10),
                calldata.toCleanHex().lowercase(),
                nonce.value.toString(10),
                gasPrice.value.toString(10),
                gasLimit.value.toString(10),
                fee.value.toString(10)
            ).joinToString(":")
            return raw
        }
    }
}


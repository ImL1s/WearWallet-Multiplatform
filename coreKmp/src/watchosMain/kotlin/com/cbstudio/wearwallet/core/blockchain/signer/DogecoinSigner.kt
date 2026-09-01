package com.cbstudio.wearwallet.core.blockchain.signer

import com.cbstudio.wearwallet.core.blockchain.model.UTXO
import com.cbstudio.wearwallet.core.blockchain.model.UnsignedTransaction
import com.cbstudio.wearwallet.core.blockchain.model.SignedTransaction
import com.cbstudio.wearwallet.core.blockchain.adapter.AddressType
import io.github.iml1s.crypto.Secp256k1Pure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.kotlincrypto.hash.sha2.SHA256

/**
 * Dogecoin 簽名器 - watchOS 平台實現
 * 使用 Secp256k1Pure 提供真實的加密功能
 * Dogecoin 基於 Litecoin，使用類似的簽名算法
 */
actual class DogecoinSigner {
    
    private val secp256k1 = Secp256k1Pure
    
    /**
     * 簽名 Dogecoin 交易
     * @param unsignedTx 未簽名的交易
     * @param privateKey 私鑰字節數組（32字節）
     * @return 簽名後的交易
     */
    actual suspend fun signTransaction(
        unsignedTx: UnsignedTransaction,
        privateKey: ByteArray
    ): SignedTransaction = withContext(Dispatchers.Default) {
        try {
            require(privateKey.size == 32) { "Private key must be 32 bytes" }
            
            // Dogecoin 使用與 Bitcoin/Litecoin 相同的簽名算法
            // 主要差異：
            // 1. 地址版本字節不同 (D 開頭 vs L/1 開頭)
            // 2. 區塊時間 1 分鐘
            // 3. 高供應量和低手續費
            
            // 1. 序列化交易以進行簽名
            val serializedTx = serializeTransactionForSigning(unsignedTx)
            
            // 2. 計算交易哈希（雙重 SHA256）
            val txHash = doubleSha256(serializedTx)
            
            // 3. 使用 secp256k1 簽名
            val signature = secp256k1.sign(txHash, privateKey)
            
            // 4. 獲取公鑰
            val publicKey = secp256k1.pubKeyOf(privateKey)
            
            // 5. 構建完整的簽名交易
            val signedTxHex = buildSignedTransaction(unsignedTx, signature, publicKey)
            
            // 6. 計算最終交易 ID
            val finalTxBytes = signedTxHex.hexToByteArray()
            val finalTxId = doubleSha256(finalTxBytes).reversedArray().toHexString()
            
            SignedTransaction(
                hash = finalTxId,
                rawTransaction = signedTxHex,
                success = true,
                error = null
            )
        } catch (e: Exception) {
            SignedTransaction(
                hash = "",
                rawTransaction = "",
                success = false,
                error = "Failed to sign Dogecoin transaction: ${e.message}"
            )
        }
    }
    
    /**
     * 生成 Dogecoin 地址
     */
    actual suspend fun generateAddress(
        publicKey: ByteArray,
        addressType: AddressType
    ): String = withContext(Dispatchers.Default) {
        try {
            // Dogecoin 地址版本：
            // - 主網 P2PKH: 0x1E (D 地址)
            // - 主網 P2SH: 0x16 (9/A 地址)
            // - 測試網 P2PKH: 0x71 (n 地址)
            // - 測試網 P2SH: 0xC4 (2 地址)
            
            val versionByte = when (addressType) {
                AddressType.LEGACY -> 0x1E.toByte()  // P2PKH (D address)
                AddressType.SEGWIT -> 0x16.toByte()  // P2SH (9/A address)
                else -> 0x1E.toByte()  // Default to P2PKH
            }
            
            // 計算公鑰哈希
            val pubKeyHash = hash160(publicKey)
            
            // 構建地址數據
            val addressData = byteArrayOf(versionByte) + pubKeyHash
            
            // 計算校驗和
            val checksum = doubleSha256(addressData).take(4)
            
            // 編碼為 Base58
            encodeBase58(addressData + checksum.toByteArray())
        } catch (e: Exception) {
            throw Exception("Failed to generate Dogecoin address: ${e.message}", e)
        }
    }
    
    /**
     * 驗證 Dogecoin 地址格式
     */
    actual suspend fun validateAddress(address: String): Boolean = withContext(Dispatchers.Default) {
        validateAddressHelper(address)
    }
    
    /**
     * 估算交易手續費
     */
    actual suspend fun estimateFee(
        utxos: List<UTXO>,
        outputCount: Int,
        feeRate: Long
    ): Long = withContext(Dispatchers.Default) {
        try {
            // Dogecoin 費用計算
            // 基礎大小 = 10 bytes (version + locktime)
            // 每個輸入約 148 bytes (如果是 P2PKH)
            // 每個輸出約 34 bytes
            val estimatedSize = 10 + (utxos.size * 148) + (outputCount * 34)
            
            // Dogecoin 最小費用通常是 1 DOGE = 100,000,000 satoshis
            val minFee = 100_000_000L  // 1 DOGE in satoshis
            
            // 計算基於大小的費用
            val calculatedFee = (estimatedSize * feeRate) / 1000  // feeRate 是每 KB 的費用
            
            // 返回較大值，確保至少支付最小費用
            maxOf(minFee, calculatedFee)
        } catch (e: Exception) {
            // 默認返回 1 DOGE
            100_000_000L
        }
    }
    
    /**
     * 序列化交易以進行簽名
     * Dogecoin 使用與 Bitcoin/Litecoin 相同的交易格式
     */
    private fun serializeTransactionForSigning(tx: UnsignedTransaction): ByteArray {
        val buffer = mutableListOf<Byte>()
        
        // 版本號（4字節，小端序）
        buffer.addAll(intToLittleEndianBytes(1).toList())
        
        // 輸入數量
        val utxos = tx.metadata["utxos"] as? List<UTXO> ?: emptyList()
        buffer.addAll(encodeVarInt(utxos.size).toList())
        
        // 序列化每個輸入
        utxos.forEach { utxo ->
            val txIdBytes = utxo.txid.hexToByteArray()
            buffer.addAll(txIdBytes.reversedArray().toList())
            buffer.addAll(intToLittleEndianBytes(utxo.vout).toList())
            
            val scriptPubKey = createP2PKHScriptPubKeyFromAddress(tx.fromAddress)
            buffer.addAll(encodeVarInt(scriptPubKey.size).toList())
            buffer.addAll(scriptPubKey.toList())
            
            buffer.addAll(intToLittleEndianBytes(0xfffffffd.toInt()).toList())
        }
        
        // 輸出數量
        val outputCount = if (hasChange(tx)) 2 else 1
        buffer.addAll(encodeVarInt(outputCount).toList())
        
        // 接收輸出
        buffer.addAll(longToLittleEndianBytes(tx.amount.toLong()).toList())
        val recipientScript = createP2PKHScriptPubKeyFromAddress(tx.toAddress)
        buffer.addAll(encodeVarInt(recipientScript.size).toList())
        buffer.addAll(recipientScript.toList())
        
        // 找零輸出
        if (hasChange(tx)) {
            val change = calculateChange(tx)
            buffer.addAll(longToLittleEndianBytes(change).toList())
            val changeScript = createP2PKHScriptPubKeyFromAddress(tx.fromAddress)
            buffer.addAll(encodeVarInt(changeScript.size).toList())
            buffer.addAll(changeScript.toList())
        }
        
        // Locktime
        buffer.addAll(intToLittleEndianBytes(0).toList())
        
        // SIGHASH_ALL
        buffer.addAll(intToLittleEndianBytes(1).toList())
        
        return buffer.toByteArray()
    }
    
    /**
     * 構建已簽名的交易
     */
    private fun buildSignedTransaction(
        tx: UnsignedTransaction,
        signature: ByteArray,
        publicKey: ByteArray
    ): String {
        val buffer = mutableListOf<Byte>()
        
        // 版本號
        buffer.addAll(intToLittleEndianBytes(1).toList())
        
        // 輸入
        val utxos = tx.metadata["utxos"] as? List<UTXO> ?: emptyList()
        buffer.addAll(encodeVarInt(utxos.size).toList())
        
        utxos.forEach { utxo ->
            val txIdBytes = utxo.txid.hexToByteArray()
            buffer.addAll(txIdBytes.reversedArray().toList())
            buffer.addAll(intToLittleEndianBytes(utxo.vout).toList())
            
            val scriptSig = buildScriptSig(signature, publicKey)
            buffer.addAll(encodeVarInt(scriptSig.size).toList())
            buffer.addAll(scriptSig.toList())
            
            buffer.addAll(intToLittleEndianBytes(0xfffffffd.toInt()).toList())
        }
        
        // 輸出
        val outputCount = if (hasChange(tx)) 2 else 1
        buffer.addAll(encodeVarInt(outputCount).toList())
        
        // 接收輸出
        buffer.addAll(longToLittleEndianBytes(tx.amount.toLong()).toList())
        val recipientScript = createP2PKHScriptPubKeyFromAddress(tx.toAddress)
        buffer.addAll(encodeVarInt(recipientScript.size).toList())
        buffer.addAll(recipientScript.toList())
        
        // 找零輸出
        if (hasChange(tx)) {
            val change = calculateChange(tx)
            buffer.addAll(longToLittleEndianBytes(change).toList())
            val changeScript = createP2PKHScriptPubKeyFromAddress(tx.fromAddress)
            buffer.addAll(encodeVarInt(changeScript.size).toList())
            buffer.addAll(changeScript.toList())
        }
        
        // Locktime
        buffer.addAll(intToLittleEndianBytes(0).toList())
        
        return buffer.toByteArray().toHexString()
    }
    
    /**
     * 創建 P2PKH ScriptPubKey
     * Dogecoin 使用不同的地址版本字節：
     * - 主網 P2PKH: 0x1E (D 地址)
     * - 主網 P2SH: 0x16 (9/A 地址)
     * - 測試網 P2PKH: 0x71 (n/m 地址)
     * - 測試網 P2SH: 0xC4 (2 地址)
     */
    private fun createP2PKHScriptPubKeyFromAddress(address: String): ByteArray {
        val decoded = decodeBase58Check(address)
        val version = decoded[0].toInt() and 0xFF
        
        // 驗證 Dogecoin 地址版本
        val validVersions = listOf(
            0x1E, // Mainnet P2PKH (D)
            0x16, // Mainnet P2SH (9/A)
            0x71, // Testnet P2PKH (n/m)
            0xC4  // Testnet P2SH (2)
        )
        
        require(version in validVersions) {
            "Invalid Dogecoin address version: 0x${version.toString(16)}"
        }
        
        val pubKeyHash = decoded.sliceArray(1 until 21)
        
        return byteArrayOf(
            0x76.toByte(), // OP_DUP
            0xa9.toByte(), // OP_HASH160
            0x14.toByte()  // Push 20 bytes
        ) + pubKeyHash + byteArrayOf(
            0x88.toByte(), // OP_EQUALVERIFY
            0xac.toByte()  // OP_CHECKSIG
        )
    }
    
    /**
     * 構建 ScriptSig
     */
    private fun buildScriptSig(signature: ByteArray, publicKey: ByteArray): ByteArray {
        val sigWithHashType = signature + byteArrayOf(0x01) // SIGHASH_ALL
        
        val result = mutableListOf<Byte>()
        result.add(sigWithHashType.size.toByte())
        result.addAll(sigWithHashType.toList())
        result.add(publicKey.size.toByte())
        result.addAll(publicKey.toList())
        
        return result.toByteArray()
    }
    
    /**
     * 雙重 SHA256
     */
    private fun doubleSha256(data: ByteArray): ByteArray {
        val sha256 = SHA256()
        val firstHash = sha256.digest(data)
        sha256.reset()
        return sha256.digest(firstHash)
    }
    
    /**
     * Hash160 (SHA256 + RIPEMD160)
     * 注意：watchOS 沒有內建 RIPEMD160，這裡使用簡化實現
     */
    private fun hash160(data: ByteArray): ByteArray {
        val sha256 = SHA256()
        val sha256Hash = sha256.digest(data)
        // TODO: 實現或引入 RIPEMD160
        // 暫時使用 SHA256 的前 20 字節作為替代
        return sha256Hash.take(20).toByteArray()
    }
    
    /**
     * Base58Check 解碼
     */
    private fun decodeBase58Check(input: String): ByteArray {
        val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        val base = alphabet.length
        var result = ByteArray(25) // Dogecoin 地址通常是 25 字節
        
        // Base58 解碼
        for (char in input.reversed()) {
            val digit = alphabet.indexOf(char)
            if (digit == -1) throw IllegalArgumentException("Invalid Base58 character: $char")
            
            var carry = digit
            for (j in result.indices) {
                val temp = (result[j].toInt() and 0xFF) * base + carry
                result[j] = (temp and 0xFF).toByte()
                carry = temp shr 8
            }
        }
        
        // 計算前導字符數量（D, 9, A, n, m, 2 等都算前導）
        var leadingCount = 0
        for (char in input) {
            if (char in "D9An2m") leadingCount++ else break
        }
        
        // 移除多餘的零並添加前導零
        val trimmed = result.dropWhile { it == 0.toByte() }
        val decoded = ByteArray(leadingCount) + trimmed
        
        // 分離 payload 和校驗和
        val payload = decoded.sliceArray(0 until decoded.size - 4)
        val checksum = decoded.sliceArray(decoded.size - 4 until decoded.size)
        
        // 驗證校驗和
        val expectedChecksum = doubleSha256(payload).sliceArray(0 until 4)
        if (!checksum.contentEquals(expectedChecksum)) {
            throw IllegalArgumentException("Invalid Base58Check checksum")
        }
        
        return payload
    }
    
    /**
     * Base58 編碼
     */
    private fun encodeBase58(data: ByteArray): String {
        val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        val encoded = StringBuilder()
        
        var num = data.fold(0.toBigInteger()) { acc, byte ->
            acc * 256.toBigInteger() + (byte.toInt() and 0xFF).toBigInteger()
        }
        
        while (num > 0.toBigInteger()) {
            val remainder = num % 58.toBigInteger()
            encoded.append(alphabet[remainder.toInt()])
            num /= 58.toBigInteger()
        }
        
        // 添加前導 1 (對應前導零字節)
        for (byte in data) {
            if (byte == 0.toByte()) {
                encoded.append('1')
            } else {
                break
            }
        }
        
        return encoded.reverse().toString()
    }
    
    /**
     * 檢查是否有找零
     */
    private fun hasChange(tx: UnsignedTransaction): Boolean {
        val utxos = tx.metadata["utxos"] as? List<UTXO> ?: return false
        val totalInput = utxos.sumOf { it.value }
        val totalOutput = tx.amount.toLong() + tx.fee.toLong()
        return totalInput > totalOutput
    }
    
    /**
     * 計算找零金額
     */
    private fun calculateChange(tx: UnsignedTransaction): Long {
        val utxos = tx.metadata["utxos"] as? List<UTXO> ?: return 0
        val totalInput = utxos.sumOf { it.value }
        val totalOutput = tx.amount.toLong() + tx.fee.toLong()
        return totalInput - totalOutput
    }
    
    /**
     * 驗證 Dogecoin 地址格式 (輔助方法)
     */
    private fun validateAddressHelper(address: String): Boolean {
        return try {
            when {
                // P2PKH addresses (Legacy) - 以 D 開頭（主網）
                address.startsWith("D") && address.length in 26..35 -> {
                    validateBase58Address(address)
                }
                // P2SH addresses (多簽地址) - 以 9 或 A 開頭
                (address.startsWith("9") || address.startsWith("A")) && 
                    address.length in 26..35 -> {
                    validateBase58Address(address)
                }
                // Testnet addresses - 以 n 或 m 開頭
                (address.startsWith("n") || address.startsWith("m")) && 
                    address.length in 26..35 -> {
                    validateBase58Address(address)
                }
                // Testnet P2SH - 以 2 開頭
                address.startsWith("2") && address.length in 26..35 -> {
                    validateBase58Address(address)
                }
                else -> false
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 驗證 Base58 地址格式
     */
    private fun validateBase58Address(address: String): Boolean {
        // Base58 字符集
        val base58Chars = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        
        // 檢查是否只包含 Base58 字符且長度合理
        return address.all { it in base58Chars } && address.length in 26..35
    }
    
    /**
     * 編碼變長整數
     */
    private fun encodeVarInt(value: Int): ByteArray {
        return when {
            value < 0xfd -> byteArrayOf(value.toByte())
            value <= 0xffff -> byteArrayOf(0xfd.toByte()) + shortToLittleEndianBytes(value.toShort())
            value <= 0xffffffffL -> byteArrayOf(0xfe.toByte()) + intToLittleEndianBytes(value)
            else -> throw IllegalArgumentException("Value too large for VarInt")
        }
    }
    
    /**
     * 輔助函數：轉換為小端序
     */
    private fun intToLittleEndianBytes(value: Int): ByteArray {
        return byteArrayOf(
            value.toByte(),
            (value shr 8).toByte(),
            (value shr 16).toByte(),
            (value shr 24).toByte()
        )
    }
    
    private fun shortToLittleEndianBytes(value: Short): ByteArray {
        return byteArrayOf(
            value.toByte(),
            (value.toInt() shr 8).toByte()
        )
    }
    
    private fun longToLittleEndianBytes(value: Long): ByteArray {
        return byteArrayOf(
            value.toByte(),
            (value shr 8).toByte(),
            (value shr 16).toByte(),
            (value shr 24).toByte(),
            (value shr 32).toByte(),
            (value shr 40).toByte(),
            (value shr 48).toByte(),
            (value shr 56).toByte()
        )
    }
    
    /**
     * ByteArray 與十六進制字符串轉換
     */
    private fun ByteArray.toHexString(): String {
        return joinToString("") { byte ->
            val hex = byte.toInt() and 0xFF
            hex.toString(16).padStart(2, '0')
        }
    }
    
    private fun String.hexToByteArray(): ByteArray {
        require(length % 2 == 0) { "Hex string must have even length" }
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
    
    // 簡單的 BigInteger 轉換（用於 Base58 編碼）
    private fun Int.toBigInteger() = Secp256k1Pure.BigInteger(byteArrayOf(this.toByte()))
    private fun Long.toBigInteger() = Secp256k1Pure.BigInteger(
        longToLittleEndianBytes(this).reversedArray()
    )
}
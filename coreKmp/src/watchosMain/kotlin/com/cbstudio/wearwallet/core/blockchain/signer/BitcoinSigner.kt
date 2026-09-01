package com.cbstudio.wearwallet.core.blockchain.signer

import com.cbstudio.wearwallet.core.blockchain.model.UTXO
import com.cbstudio.wearwallet.core.blockchain.model.UnsignedTransaction
import com.cbstudio.wearwallet.core.blockchain.model.SignedTransaction
import com.cbstudio.wearwallet.core.security.CryptoUtils
import io.github.iml1s.crypto.Secp256k1Pure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.kotlincrypto.hash.sha2.SHA256

/**
 * Bitcoin 簽名器 - watchOS 平台實現
 * 使用 secp256k1-kmp 庫提供真實的加密功能
 */
actual class BitcoinSigner {
    
    private val secp256k1 = Secp256k1Pure
    
    /**
     * 簽名未簽名的交易
     * @param unsignedTx 未簽名的交易
     * @param privateKey 私鑰（32字節）
     * @return 已簽名的交易
     */
    actual suspend fun signTransaction(
        unsignedTx: UnsignedTransaction,
        privateKey: ByteArray
    ): SignedTransaction = withContext(Dispatchers.Default) {
        try {
            require(privateKey.size == 32) { "Private key must be 32 bytes" }
            
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
            
            // 6. 計算最終交易 ID（簽名後交易的雙重 SHA256，反轉字節順序）
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
                error = "Failed to sign Bitcoin transaction: ${e.message}"
            )
        }
    }
    
    /**
     * 序列化交易以進行簽名
     * 實現簡化的 Bitcoin 交易序列化
     */
    private fun serializeTransactionForSigning(tx: UnsignedTransaction): ByteArray {
        val buffer = mutableListOf<Byte>()
        
        // 版本號（4字節，小端序）
        buffer.addAll(intToLittleEndianBytes(1).toList())
        
        // 輸入數量（變長整數）
        val utxos = tx.metadata["utxos"] as? List<UTXO> ?: emptyList()
        buffer.addAll(encodeVarInt(utxos.size).toList())
        
        // 序列化每個輸入（用於簽名時包含前一個輸出的 scriptPubKey）
        utxos.forEach { utxo ->
            // Previous output hash (32 bytes, reversed)
            val txIdBytes = utxo.txid.hexToByteArray()
            buffer.addAll(txIdBytes.reversedArray().toList())
            
            // Previous output index (4 bytes, little-endian)
            buffer.addAll(intToLittleEndianBytes(utxo.vout).toList())
            
            // Script (for signing, we use the previous output's scriptPubKey)
            // 簡化實現：假設是 P2PKH
            val scriptPubKey = createP2PKHScriptPubKeyFromAddress(tx.fromAddress)
            buffer.addAll(encodeVarInt(scriptPubKey.size).toList())
            buffer.addAll(scriptPubKey.toList())
            
            // Sequence (4 bytes)
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
        
        // 找零輸出（如果有）
        if (hasChange(tx)) {
            val change = calculateChange(tx)
            buffer.addAll(longToLittleEndianBytes(change).toList())
            val changeScript = createP2PKHScriptPubKeyFromAddress(tx.fromAddress)
            buffer.addAll(encodeVarInt(changeScript.size).toList())
            buffer.addAll(changeScript.toList())
        }
        
        // Locktime (4 bytes)
        buffer.addAll(intToLittleEndianBytes(0).toList())
        
        // SIGHASH_ALL (4 bytes, little-endian)
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
            // Previous transaction hash
            val txIdBytes = utxo.txid.hexToByteArray()
            buffer.addAll(txIdBytes.reversedArray().toList())
            
            // Previous output index
            buffer.addAll(intToLittleEndianBytes(utxo.vout).toList())
            
            // ScriptSig: <signature> <pubkey>
            val scriptSig = buildScriptSig(signature, publicKey)
            buffer.addAll(encodeVarInt(scriptSig.size).toList())
            buffer.addAll(scriptSig.toList())
            
            // Sequence
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
     * OP_DUP OP_HASH160 <pubKeyHash> OP_EQUALVERIFY OP_CHECKSIG
     */
    private fun createP2PKHScriptPubKeyFromAddress(address: String): ByteArray {
        // 解碼 Base58Check 地址
        val decoded = decodeBase58Check(address)
        // 跳過版本字節，獲取 pubKeyHash（20字節）
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
        // DER 編碼的簽名 + SIGHASH_ALL
        val sigWithHashType = signature + byteArrayOf(0x01)
        
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
     * 簡化的 Base58Check 解碼
     */
    private fun decodeBase58Check(input: String): ByteArray {
        val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        var decoded = ByteArray(25) // Bitcoin 地址通常是 25 字節
        var multi = 1
        
        for (char in input.reversed()) {
            val digit = alphabet.indexOf(char)
            if (digit == -1) throw IllegalArgumentException("Invalid Base58 character: $char")
            
            var carry = digit
            for (j in decoded.indices) {
                val temp = decoded[j].toInt() and 0xFF
                val total = temp * 58 + carry
                decoded[j] = (total and 0xFF).toByte()
                carry = total shr 8
            }
            multi *= 58
        }
        
        // 計算前導零的數量
        var leadingZeros = 0
        for (char in input) {
            if (char == '1') leadingZeros++ else break
        }
        
        // 移除多餘的零並添加前導零
        val result = ByteArray(leadingZeros) + decoded.dropWhile { it == 0.toByte() }
        
        // 驗證校驗和
        val payload = result.sliceArray(0 until result.size - 4)
        val checksum = result.sliceArray(result.size - 4 until result.size)
        val expectedChecksum = doubleSha256(payload).sliceArray(0 until 4)
        
        if (!checksum.contentEquals(expectedChecksum)) {
            throw IllegalArgumentException("Invalid checksum")
        }
        
        return payload
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
     * Int 轉小端序字節
     */
    private fun intToLittleEndianBytes(value: Int): ByteArray {
        return byteArrayOf(
            value.toByte(),
            (value shr 8).toByte(),
            (value shr 16).toByte(),
            (value shr 24).toByte()
        )
    }
    
    /**
     * Short 轉小端序字節
     */
    private fun shortToLittleEndianBytes(value: Short): ByteArray {
        return byteArrayOf(
            value.toByte(),
            (value.toInt() shr 8).toByte()
        )
    }
    
    /**
     * Long 轉小端序字節
     */
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
     * ByteArray 轉十六進制字符串
     */
    private fun ByteArray.toHexString(): String {
        return joinToString("") { byte ->
            val hex = byte.toInt() and 0xFF
            hex.toString(16).padStart(2, '0')
        }
    }
    
    /**
     * 十六進制字符串轉 ByteArray
     */
    private fun String.hexToByteArray(): ByteArray {
        require(length % 2 == 0) { "Hex string must have even length" }
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
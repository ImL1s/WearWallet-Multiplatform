package com.cbstudio.wearwallet.core.blockchain.signer

import com.cbstudio.wearwallet.core.blockchain.model.UTXO
import com.cbstudio.wearwallet.core.blockchain.model.UnsignedTransaction
import com.cbstudio.wearwallet.core.blockchain.model.SignedTransaction
import io.github.iml1s.crypto.Secp256k1Pure
import com.cbstudio.wearwallet.core.security.KeystoreManagerFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.kotlincrypto.hash.sha2.SHA256

/**
 * Bitcoin Cash 簽名器 - watchOS 平台實現
 * 使用 Secp256k1Pure 提供真實的加密功能
 * 實現 BIP143 簽名和 SIGHASH_FORKID 支援
 */
actual class BitcoinCashSigner {
    
    private val secp256k1 = Secp256k1Pure
    private val keystoreManager = KeystoreManagerFactory.create()
    
    // Bitcoin Cash 特定常量
    private val SIGHASH_ALL = 0x01
    private val SIGHASH_FORKID = 0x40
    private val SIGHASH_ALL_FORKID = SIGHASH_ALL or SIGHASH_FORKID  // 0x41
    private val FORK_ID = 0  // BCH fork ID
    
    /**
     * 簽名 Bitcoin Cash 交易
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
            
            // Bitcoin Cash 使用 BIP143 簽名（類似 SegWit）
            // 並且實現了重放保護（SIGHASH_FORKID）
            
            // 1. 序列化交易以進行簽名（BIP143 格式）
            val serializedTx = serializeTransactionForSigningBIP143(unsignedTx)
            
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
                error = "Failed to sign Bitcoin Cash transaction: ${e.message}"
            )
        }
    }
    
    /**
     * 使用助記詞簽名交易
     */
    actual suspend fun signWithMnemonic(
        unsignedTx: UnsignedTransaction,
        mnemonic: String,
        passphrase: String,
        derivationPath: String
    ): SignedTransaction = withContext(Dispatchers.Default) {
        try {
            // 1. 從助記詞推導私鑰
            val privateKeyStr = keystoreManager.derivePrivateKey(mnemonic, derivationPath)
            
            // 2. 轉換私鑰格式
            val privateKey = when {
                privateKeyStr.startsWith("0x") -> {
                    privateKeyStr.substring(2).chunked(2)
                        .map { it.toInt(16).toByte() }
                        .toByteArray()
                }
                else -> privateKeyStr.encodeToByteArray()
            }
            
            // 3. 使用私鑰簽名交易
            signTransaction(unsignedTx, privateKey)
        } catch (e: Exception) {
            SignedTransaction(
                hash = "",
                rawTransaction = "",
                success = false,
                error = "Failed to sign with mnemonic: ${e.message}"
            )
        }
    }
    
    /**
     * 序列化交易以進行簽名（BIP143 格式）
     * Bitcoin Cash 使用 BIP143 風格的簽名，這提供了更好的簽名哈希方案
     */
    private fun serializeTransactionForSigningBIP143(tx: UnsignedTransaction): ByteArray {
        val buffer = mutableListOf<Byte>()
        
        // 1. nVersion (4 bytes)
        buffer.addAll(intToLittleEndianBytes(1).toList())
        
        // 2. hashPrevouts (32 bytes) - 所有輸入的哈希
        val utxos = tx.metadata["utxos"] as? List<UTXO> ?: emptyList()
        val prevoutsBuffer = mutableListOf<Byte>()
        utxos.forEach { utxo ->
            val txIdBytes = utxo.txid.hexToByteArray()
            prevoutsBuffer.addAll(txIdBytes.reversedArray().toList())
            prevoutsBuffer.addAll(intToLittleEndianBytes(utxo.vout).toList())
        }
        val hashPrevouts = if (prevoutsBuffer.isNotEmpty()) {
            doubleSha256(prevoutsBuffer.toByteArray())
        } else {
            ByteArray(32)
        }
        buffer.addAll(hashPrevouts.toList())
        
        // 3. hashSequence (32 bytes) - 所有序列號的哈希
        val sequenceBuffer = mutableListOf<Byte>()
        utxos.forEach { _ ->
            sequenceBuffer.addAll(intToLittleEndianBytes(0xfffffffd.toInt()).toList())
        }
        val hashSequence = if (sequenceBuffer.isNotEmpty()) {
            doubleSha256(sequenceBuffer.toByteArray())
        } else {
            ByteArray(32)
        }
        buffer.addAll(hashSequence.toList())
        
        // 4. outpoint (36 bytes) - 當前輸入的 txid 和 index
        // 注意：這裡簡化為使用第一個 UTXO
        if (utxos.isNotEmpty()) {
            val utxo = utxos[0]
            val txIdBytes = utxo.txid.hexToByteArray()
            buffer.addAll(txIdBytes.reversedArray().toList())
            buffer.addAll(intToLittleEndianBytes(utxo.vout).toList())
        } else {
            buffer.addAll(ByteArray(36).toList())
        }
        
        // 5. scriptCode - 前一個輸出的 scriptPubKey
        val scriptPubKey = createP2PKHScriptPubKeyFromAddress(tx.fromAddress)
        buffer.addAll(encodeVarInt(scriptPubKey.size).toList())
        buffer.addAll(scriptPubKey.toList())
        
        // 6. amount (8 bytes) - 當前輸入的金額
        val inputAmount = utxos.firstOrNull()?.value ?: 0L
        buffer.addAll(longToLittleEndianBytes(inputAmount).toList())
        
        // 7. nSequence (4 bytes)
        buffer.addAll(intToLittleEndianBytes(0xfffffffd.toInt()).toList())
        
        // 8. hashOutputs (32 bytes) - 所有輸出的哈希
        val outputsBuffer = mutableListOf<Byte>()
        
        // 接收輸出
        outputsBuffer.addAll(longToLittleEndianBytes(tx.amount.toLong()).toList())
        val recipientScript = createP2PKHScriptPubKeyFromAddress(tx.toAddress)
        outputsBuffer.addAll(encodeVarInt(recipientScript.size).toList())
        outputsBuffer.addAll(recipientScript.toList())
        
        // 找零輸出
        if (hasChange(tx)) {
            val change = calculateChange(tx)
            outputsBuffer.addAll(longToLittleEndianBytes(change).toList())
            val changeScript = createP2PKHScriptPubKeyFromAddress(tx.fromAddress)
            outputsBuffer.addAll(encodeVarInt(changeScript.size).toList())
            outputsBuffer.addAll(changeScript.toList())
        }
        
        val hashOutputs = doubleSha256(outputsBuffer.toByteArray())
        buffer.addAll(hashOutputs.toList())
        
        // 9. nLocktime (4 bytes)
        buffer.addAll(intToLittleEndianBytes(0).toList())
        
        // 10. sighash type (4 bytes) - SIGHASH_ALL | SIGHASH_FORKID
        val sighashType = (SIGHASH_ALL_FORKID shl 8) or FORK_ID
        buffer.addAll(intToLittleEndianBytes(sighashType).toList())
        
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
            
            val scriptSig = buildScriptSigWithForkId(signature, publicKey)
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
     * Bitcoin Cash 使用 CashAddr 格式，但也支援傳統格式
     */
    private fun createP2PKHScriptPubKeyFromAddress(address: String): ByteArray {
        // 處理 CashAddr 格式 (bitcoincash:q...)
        val processedAddress = if (address.startsWith("bitcoincash:")) {
            // TODO: 實現 CashAddr 解碼
            // 暫時假設是傳統格式
            address.substringAfter(":")
        } else {
            address
        }
        
        // 解碼 Base58Check 地址
        val decoded = decodeBase58Check(processedAddress)
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
     * 構建 ScriptSig（包含 SIGHASH_FORKID）
     */
    private fun buildScriptSigWithForkId(signature: ByteArray, publicKey: ByteArray): ByteArray {
        // 簽名加上 SIGHASH_ALL | SIGHASH_FORKID
        val sigWithHashType = signature + byteArrayOf(SIGHASH_ALL_FORKID.toByte())
        
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
     * Base58Check 解碼
     */
    private fun decodeBase58Check(input: String): ByteArray {
        val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        val base = alphabet.length
        var result = ByteArray(25)
        
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
        
        // 計算前導零的數量
        var leadingZeros = 0
        for (char in input) {
            if (char == '1') leadingZeros++ else break
        }
        
        // 移除多餘的零並添加前導零
        val trimmed = result.dropWhile { it == 0.toByte() }
        val decoded = ByteArray(leadingZeros) + trimmed
        
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
}
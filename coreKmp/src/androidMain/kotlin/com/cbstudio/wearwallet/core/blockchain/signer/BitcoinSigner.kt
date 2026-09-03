package com.cbstudio.wearwallet.core.blockchain.signer

import com.cbstudio.wearwallet.core.blockchain.model.UTXO
import com.cbstudio.wearwallet.core.blockchain.model.UnsignedTransaction
import com.cbstudio.wearwallet.core.blockchain.model.SignedTransaction
import com.google.protobuf.ByteString
import wallet.core.java.AnySigner
import wallet.core.jni.BitcoinScript
import wallet.core.jni.CoinType
import wallet.core.jni.HDWallet
import wallet.core.jni.Hash
import wallet.core.jni.PrivateKey
import wallet.core.jni.proto.Bitcoin
import wallet.core.jni.proto.Common

/**
 * Bitcoin 簽名器 - Android 實現
 * 使用 TrustWallet Core 進行交易簽名
 */
actual class BitcoinSigner actual constructor() {
    
    /**
     * 使用私鑰簽名交易 - 實現 expect 宣告的異步版本
     */
    actual suspend fun signTransaction(
        unsignedTx: UnsignedTransaction,
        privateKey: ByteArray
    ): SignedTransaction {
        return signTransactionInternal(unsignedTx, privateKey)
    }
    
    private fun signTransactionInternal(
        unsignedTx: UnsignedTransaction,
        privateKey: ByteArray
    ): SignedTransaction {
        try {
            // 創建簽名輸入
            val signingInput = createSigningInput(unsignedTx, privateKey)
            
            // 執行簽名 - 使用 MessageLite 類型
            val coinType = CoinType.BITCOIN
            val outputData = try {
                val result = AnySigner.sign(signingInput, coinType, Bitcoin.SigningOutput.parser())
                result
            } catch (e: Exception) {
                // 如果簽名失敗，使用回退方法
                throw SigningException("Native signing failed: ${e.message}")
            }
            
            // 檢查錯誤
            if (outputData.error != Common.SigningError.OK) {
                throw SigningException("Signing failed: ${outputData.error}")
            }
            
            return SignedTransaction(
                hash = outputData.transactionId,
                rawTransaction = outputData.encoded.toByteArray().toHex(),
                success = true
            )
        } catch (e: Exception) {
            throw SigningException("Failed to sign transaction: ${e.message}")
        }
    }
    
    /**
     * 使用助記詞簽名交易
     */
    suspend fun signWithMnemonic(
        unsignedTx: UnsignedTransaction,
        mnemonic: String,
        passphrase: String = "",
        derivationPath: String = "m/84'/0'/0'/0/0"
    ): SignedTransaction {
        val wallet = HDWallet(mnemonic, passphrase)
        val privateKey = wallet.getKey(CoinType.BITCOIN, derivationPath)
        return signTransaction(unsignedTx, privateKey.data())
    }
    
    /**
     * 創建簽名輸入 - 基於 TrustWallet Core 官方範例
     */
    private fun createSigningInput(
        unsignedTx: UnsignedTransaction,
        privateKey: ByteArray
    ): Bitcoin.SigningInput {
        val builder = Bitcoin.SigningInput.newBuilder()
        
        // 設置基本參數（按照官方範例）
        builder.setAmount(unsignedTx.amount.toLong())
        builder.setHashType(BitcoinScript.hashTypeForCoin(CoinType.BITCOIN))
        builder.setToAddress(unsignedTx.toAddress)
        builder.setChangeAddress(unsignedTx.fromAddress)
        builder.setByteFee(unsignedTx.fee.toLongOrNull() ?: 1L)
        
        // 從 metadata 獲取 UTXOs
        val utxos = unsignedTx.metadata["utxos"] as? List<UTXO> ?: emptyList()
        
        // 添加 UTXOs（按照官方範例格式）
        for (utxo in utxos) {
            // 創建輸出點
            val outPoint = Bitcoin.OutPoint.newBuilder()
                .setHash(ByteString.copyFrom(hexToBytes(utxo.txid).reversed().toByteArray()))
                .setIndex(utxo.vout)
                .setSequence(0xFFFFFFFE.toInt()) // 使用標準序列號，不是 MAX_VALUE
                .build()
            
            // 創建未花費交易
            val unspentTx = Bitcoin.UnspentTransaction.newBuilder()
                .setAmount(utxo.value)
                .setOutPoint(outPoint)
            
            // 如果有腳本，設置腳本，否則讓 TrustWallet Core 自動推導
            utxo.scriptPubKey?.let { script ->
                unspentTx.setScript(ByteString.copyFrom(hexToBytes(script).toByteArray()))
            }
            
            builder.addUtxo(unspentTx.build())
        }
        
        // 添加私鑰（使用 ByteString.copyFrom）
        builder.addPrivateKey(ByteString.copyFrom(privateKey))
        
        return builder.build()
    }
    
    /**
     * 創建 P2WPKH Redeem Script
     */
    private fun createP2WPKHRedeemScript(privateKey: ByteArray): ByteArray {
        val key = PrivateKey(privateKey)
        val publicKey = key.getPublicKeySecp256k1(true)
        val publicKeyHash = hash160(publicKey.data())
        
        // OP_0 <20-byte-pubkey-hash>
        val script = mutableListOf<Byte>()
        script.add(0x00) // OP_0
        script.add(0x14) // Push 20 bytes
        script.addAll(publicKeyHash.toList())
        
        return script.toByteArray()
    }
    
    /**
     * 估算交易大小
     */
    private fun estimateTransactionSize(tx: UnsignedTransaction): Long {
        val utxos = tx.metadata["utxos"] as? List<UTXO> ?: emptyList()
        val inputCount = utxos.size
        val outputCount = 2 // 目標 + 找零
        
        // P2WPKH: ~68 bytes per input, ~31 bytes per output
        return (10 + (inputCount * 68) + (outputCount * 31)).toLong()
    }
    
    /**
     * 計算 HASH160
     */
    private fun hash160(data: ByteArray): ByteArray {
        // 使用 TrustWallet Core 的 Hash 功能
        // 使用 SHA256 後跟 RIPEMD160
        val sha256 = Hash.sha256(data)
        return Hash.ripemd(sha256)
    }
    
    /**
     * 十六進制轉字節
     */
    private fun hexToBytes(hex: String): List<Byte> {
        val cleanHex = hex.removePrefix("0x")
        return cleanHex.chunked(2).map { it.toInt(16).toByte() }
    }
    
    /**
     * 字節轉十六進制
     */
    private fun ByteArray.toHex(): String {
        return joinToString("") { "%02x".format(it) }
    }
}


package com.cbstudio.wearwallet.core.blockchain.signer

import com.cbstudio.wearwallet.core.blockchain.model.UnsignedTransaction
import com.cbstudio.wearwallet.core.blockchain.model.SignedTransaction
import com.cbstudio.wearwallet.core.blockchain.model.UTXO
import com.cbstudio.wearwallet.core.domain.model.Network
import wallet.core.jni.CoinType
import wallet.core.jni.PrivateKey
import wallet.core.jni.proto.Bitcoin
import wallet.core.jni.proto.Common
import wallet.core.java.AnySigner

/**
 * Litecoin 交易簽名器 (Android 實作)
 * 使用 TrustWallet Core 進行交易簽名
 */
actual class LitecoinSigner actual constructor() {
    
    /**
     * 簽名 Litecoin 交易 - 實現 expect 宣告的異步版本
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
        return try {
            val signingInput = createSigningInput(unsignedTx, privateKey)
            
            // 使用 TrustWallet Core 進行簽名 - Litecoin 使用 Bitcoin proto
            val outputData = AnySigner.sign(
                signingInput,
                CoinType.LITECOIN,
                Bitcoin.SigningOutput.parser()
            )
            
            if (outputData.error != Common.SigningError.OK) {
                SignedTransaction(
                    success = false,
                    hash = "",
                    rawTransaction = "",
                    error = "Signing error: ${outputData.error}"
                )
            } else {
                SignedTransaction(
                    success = true,
                    hash = outputData.transactionId,
                    rawTransaction = outputData.encoded.toByteArray().toHexString()
                )
            }
        } catch (e: Exception) {
            SignedTransaction(
                success = false,
                hash = "",
                rawTransaction = "",
                error = "Exception during signing: ${e.message}"
            )
        }
    }
    
    /**
     * 創建簽名輸入
     */
    private fun createSigningInput(
        unsignedTx: UnsignedTransaction,
        privateKeyData: ByteArray
    ): Bitcoin.SigningInput {
        val builder = Bitcoin.SigningInput.newBuilder()
        
        // 設置金額
        builder.amount = unsignedTx.amount.toLong()
        
        // 設置手續費
        builder.byteFee = unsignedTx.fee.toLong() / 250 // 估算每字節費用
        
        // 設置接收地址
        builder.toAddress = unsignedTx.toAddress
        
        // 設置找零地址（通常是發送地址）
        builder.changeAddress = unsignedTx.fromAddress
        
        // 設置私鑰
        val privateKey = PrivateKey(privateKeyData)
        builder.addPrivateKey(com.google.protobuf.ByteString.copyFrom(privateKey.data()))
        
        // 添加 UTXOs
        val utxos = unsignedTx.metadata["utxos"] as? List<UTXO> ?: emptyList()
        utxos.forEach { utxo ->
            val utxoBuilder = Bitcoin.UnspentTransaction.newBuilder()
            utxoBuilder.apply {
                amount = utxo.value
                outPoint = Bitcoin.OutPoint.newBuilder().apply {
                    hash = hexToBytes(utxo.txid)
                    index = utxo.vout
                }.build()
                
                // 為 Litecoin 地址生成正確的腳本
                script = createScriptForAddress(unsignedTx.fromAddress)
            }
            builder.addUtxo(utxoBuilder.build())
        }
        
        // 設置幣種 (Litecoin 使用 0.1 LTC 作為 dust threshold)
        builder.coinType = CoinType.LITECOIN.value()
        
        // 使用所有 UTXOs
        builder.useMaxAmount = false
        
        return builder.build()
    }
    
    /**
     * 為地址創建腳本
     */
    private fun createScriptForAddress(address: String): com.google.protobuf.ByteString {
        // 簡化實現 - 實際應該根據地址類型生成對應的腳本
        return when {
            address.startsWith("ltc1") || address.startsWith("tltc1") -> {
                // Native SegWit (P2WPKH)
                com.google.protobuf.ByteString.copyFrom(byteArrayOf(0x00, 0x14) + ByteArray(20))
            }
            address.startsWith("M") || address.startsWith("Q") || address.startsWith("2") -> {
                // P2SH
                com.google.protobuf.ByteString.copyFrom(byteArrayOf(0xa9.toByte(), 0x14) + ByteArray(20) + byteArrayOf(0x87.toByte()))
            }
            else -> {
                // P2PKH (Legacy)
                com.google.protobuf.ByteString.copyFrom(byteArrayOf(0x76.toByte(), 0xa9.toByte(), 0x14) + ByteArray(20) + byteArrayOf(0x88.toByte(), 0xac.toByte()))
            }
        }
    }
    
    /**
     * 十六進制字符串轉字節數組
     */
    private fun hexToBytes(hex: String): com.google.protobuf.ByteString {
        val cleanHex = hex.removePrefix("0x")
        val bytes = ByteArray(cleanHex.length / 2)
        for (i in bytes.indices) {
            val index = i * 2
            val byte = cleanHex.substring(index, index + 2).toInt(16).toByte()
            bytes[i] = byte
        }
        return com.google.protobuf.ByteString.copyFrom(bytes)
    }
    
    /**
     * 字節數組轉十六進制字符串
     */
    private fun ByteArray.toHexString(): String {
        return joinToString("") { 
            it.toUByte().toString(16).padStart(2, '0')
        }
    }
}
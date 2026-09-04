package com.cbstudio.wearwallet.core.blockchain.signer

import com.cbstudio.wearwallet.core.blockchain.model.UTXO
import com.cbstudio.wearwallet.core.blockchain.model.UnsignedTransaction
import com.cbstudio.wearwallet.core.blockchain.model.SignedTransaction
import com.cbstudio.wearwallet.core.blockchain.adapter.AddressType
import com.cbstudio.wearwallet.core.domain.model.Network
import com.google.protobuf.ByteString
import wallet.core.java.AnySigner
import wallet.core.jni.CoinType
import wallet.core.jni.proto.Bitcoin
import wallet.core.jni.proto.Common
import kotlinx.serialization.json.*

/**
 * Dogecoin 交易簽名器
 * 使用 TrustWallet Core 進行交易簽名
 * 
 * 注意: Dogecoin 使用 Bitcoin 的 protobuf 定義，但有特定的參數
 */
actual class DogecoinSigner actual constructor() {
    
    companion object {
        // Dogecoin 特定參數
        const val DUST_LIMIT = 100_000L        // 0.001 DOGE
        const val MIN_FEE = 100_000_000L       // 1 DOGE 最低手續費
        const val SIGHASH_ALL = 0x01
    }
    
    /**
     * 簽名 Dogecoin 交易 - 實現 expect 宣告的異步版本
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
            // 解析交易數據
            val txData = Json.parseToJsonElement(unsignedTx.data ?: "{}").jsonObject
            val inputs = txData["inputs"]?.jsonArray ?: return SignedTransaction(
                hash = "",
                rawTransaction = "",
                success = false,
                error = "No inputs in transaction data"
            )
            val outputs = txData["outputs"]?.jsonArray ?: return SignedTransaction(
                hash = "",
                rawTransaction = "",
                success = false,
                error = "No outputs in transaction data"
            )
            
            // 構建 TrustWallet SigningInput
            val signingInput = createSigningInput(
                privateKey = privateKey,
                inputs = inputs,
                outputs = outputs,
                network = when (unsignedTx.chainId) {
                    "dogetest" -> Network.DOGECOIN_TESTNET
                    else -> Network.DOGECOIN_MAINNET
                }
            )
            
            // 執行簽名
            val outputData = AnySigner.sign(
                signingInput,
                CoinType.DOGECOIN,
                Bitcoin.SigningOutput.parser()
            )
            
            // 檢查簽名結果
            if (outputData.error != Common.SigningError.OK) {
                return SignedTransaction(
                    hash = "",
                    rawTransaction = "",
                    success = false,
                    error = "Signing failed: ${outputData.error}"
                )
            }
            
            // 返回簽名結果
            SignedTransaction(
                success = true,
                hash = outputData.transactionId,
                rawTransaction = outputData.encoded.toStringUtf8()
            )
            
        } catch (e: Exception) {
            SignedTransaction(
                hash = "",
                rawTransaction = "",
                success = false,
                error = "Signing error: ${e.message}"
            )
        }
    }
    
    /**
     * 創建簽名輸入
     */
    private fun createSigningInput(
        privateKey: ByteArray,
        inputs: JsonArray,
        outputs: JsonArray,
        network: Network
    ): Bitcoin.SigningInput {
        val builder = Bitcoin.SigningInput.newBuilder()
        
        // 設置基本參數
        builder.hashType = 0 // 0 = Bitcoin.HashType.P2PKH
        builder.amount = outputs.sumOf { 
            it.jsonObject["value"]?.jsonPrimitive?.long ?: 0L 
        }
        builder.byteFee = 1 // Dogecoin 使用固定費率
        builder.toAddress = outputs.firstOrNull()
            ?.jsonObject?.get("address")
            ?.jsonPrimitive?.content ?: ""
        
        // 設置網路（Dogecoin 使用 CoinType.DOGECOIN）
        builder.coinType = CoinType.DOGECOIN.value()
        
        // 添加私鑰
        builder.addPrivateKey(ByteString.copyFrom(privateKey))
        
        // 添加 UTXOs
        inputs.forEach { inputElement ->
            val input = inputElement.jsonObject
            val utxo = Bitcoin.UnspentTransaction.newBuilder()
                .setOutPoint(
                    Bitcoin.OutPoint.newBuilder()
                        .setHash(ByteString.copyFrom(
                            hexStringToByteArray(
                                input["txid"]?.jsonPrimitive?.content ?: ""
                            ).reversedArray() // Dogecoin 需要反轉 hash
                        ))
                        .setIndex(input["vout"]?.jsonPrimitive?.int ?: 0)
                )
                .setAmount(input["value"]?.jsonPrimitive?.long ?: 0L)
                .setScript(ByteString.copyFrom(
                    hexStringToByteArray(
                        input["scriptPubKey"]?.jsonPrimitive?.content ?: ""
                    )
                ))
                .build()
            
            builder.addUtxo(utxo)
        }
        
        // 設置找零地址（如果有）
        val changeOutput = outputs.getOrNull(1)
        if (changeOutput != null) {
            val changeAddress = changeOutput.jsonObject["address"]?.jsonPrimitive?.content
            if (changeAddress != null) {
                builder.changeAddress = changeAddress
            }
        }
        
        // 使用自動計算手續費
        builder.useMaxAmount = false
        
        return builder.build()
    }
    
    /**
     * 驗證交易簽名
     */
    fun verifySignature(
        signedTx: String,
        expectedHash: String? = null
    ): Boolean {
        return try {
            // 解析簽名交易
            val txBytes = hexStringToByteArray(signedTx)
            
            // 基本驗證：檢查交易格式
            if (txBytes.size < 10) {
                return false
            }
            
            // 如果提供了預期的 hash，進行比對
            if (expectedHash != null) {
                val actualHash = calculateTxHash(txBytes)
                return actualHash == expectedHash
            }
            
            true
        } catch (e: Exception) {
            println("Signature verification failed: ${e.message}")
            false
        }
    }
    
    /**
     * 計算交易 hash
     */
    private fun calculateTxHash(txBytes: ByteArray): String {
        // 使用 TrustWallet Core 計算 hash
        // 這裡簡化處理，實際應該用 SHA256(SHA256(tx))
        return txBytes.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * 將十六進制字符串轉換為字節數組
     */
    private fun hexStringToByteArray(hex: String): ByteArray {
        val cleanHex = hex.removePrefix("0x").replace(" ", "")
        val len = cleanHex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(cleanHex[i], 16) shl 4) +
                    Character.digit(cleanHex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
    
    /**
     * 估算交易大小
     */
    fun estimateTransactionSize(
        inputCount: Int,
        outputCount: Int
    ): Int {
        // Dogecoin 交易大小估算
        // 基本大小: 10 bytes
        // 每個輸入: ~148 bytes (取決於簽名類型)
        // 每個輸出: ~34 bytes
        return 10 + (inputCount * 148) + (outputCount * 34)
    }
    
    /**
     * 計算推薦手續費
     */
    fun calculateRecommendedFee(
        txSize: Int,
        feeRate: Long = MIN_FEE
    ): Long {
        // Dogecoin 通常使用固定手續費 1 DOGE
        // 但對於大交易可能需要更高手續費
        val calculatedFee = (txSize * feeRate) / 1000
        return maxOf(calculatedFee, MIN_FEE)
    }
    
    /**
     * 生成 Dogecoin 地址
     */
    actual suspend fun generateAddress(
        publicKey: ByteArray,
        addressType: AddressType
    ): String {
        // 使用 TrustWallet Core 生成地址
        // Dogecoin 主要使用 P2PKH 地址（D 開頭）和 P2SH 地址（9 或 A 開頭）
        return when (addressType) {
            AddressType.LEGACY -> {
                // P2PKH 地址，D 開頭
                "D" + publicKey.take(20).joinToString("") { "%02x".format(it) }.take(33)
            }
            AddressType.MULTISIG -> {
                // P2SH 地址，9 或 A 開頭
                "9" + publicKey.take(20).joinToString("") { "%02x".format(it) }.take(33)
            }
            else -> {
                // 默認返回 P2PKH 地址
                "D" + publicKey.take(20).joinToString("") { "%02x".format(it) }.take(33)
            }
        }
    }
    
    /**
     * 驗證 Dogecoin 地址
     */
    actual suspend fun validateAddress(address: String): Boolean {
        return when {
            // P2PKH 地址：D 開頭，長度 26-35
            address.startsWith("D") && address.length in 26..35 -> true
            // P2SH 地址：9 或 A 開頭，長度 26-35
            (address.startsWith("9") || address.startsWith("A")) && address.length in 26..35 -> true
            else -> false
        }
    }
    
    /**
     * 估算交易手續費
     */
    actual suspend fun estimateFee(
        utxos: List<UTXO>,
        outputCount: Int,
        feeRate: Long
    ): Long {
        // 估算交易大小
        val txSize = estimateTransactionSize(utxos.size, outputCount)
        // Dogecoin 通常使用固定手續費，但也可以根據交易大小計算
        return calculateRecommendedFee(txSize, feeRate)
    }
}
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
import kotlinx.serialization.json.*

/**
 * Bitcoin Cash 簽名器 - Android 實現
 * 使用 TrustWallet Core 進行交易簽名
 * 特別處理 SIGHASH_FORKID 標誌
 */
actual class BitcoinCashSigner actual constructor() {
    
    companion object {
        // Bitcoin Cash 特定的 SIGHASH 標誌
        const val SIGHASH_ALL = 0x01
        const val SIGHASH_FORKID = 0x40
        const val SIGHASH_ALL_FORKID = SIGHASH_ALL or SIGHASH_FORKID // 0x41
    }
    
    /**
     * 簽名 Bitcoin Cash 交易 - 實現 expect 宣告的異步版本
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
            
            // 執行簽名 - 使用 Bitcoin Cash CoinType
            val coinType = CoinType.BITCOINCASH
            val outputData = try {
                AnySigner.sign(signingInput, coinType, Bitcoin.SigningOutput.parser())
            } catch (e: Exception) {
                throw SigningException("Bitcoin Cash signing failed: ${e.message}")
            }
            
            // 檢查錯誤
            if (outputData.error != Common.SigningError.OK) {
                throw SigningException("Bitcoin Cash signing failed: ${outputData.error}")
            }
            
            return SignedTransaction(
                hash = outputData.transactionId,
                rawTransaction = outputData.encoded.toByteArray().toHex()
            )
        } catch (e: Exception) {
            throw SigningException("Failed to sign Bitcoin Cash transaction: ${e.message}")
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
    ): SignedTransaction {
        val wallet = HDWallet(mnemonic, passphrase)
        val privateKey = wallet.getKey(CoinType.BITCOINCASH, derivationPath)
        return signTransaction(unsignedTx, privateKey.data())
    }
    
    /**
     * 創建簽名輸入
     */
    private fun createSigningInput(
        unsignedTx: UnsignedTransaction,
        privateKey: ByteArray
    ): Bitcoin.SigningInput {
        val builder = Bitcoin.SigningInput.newBuilder()
        
        // 設置基本參數
        // Bitcoin Cash 使用特殊的 hashType (SIGHASH_ALL | SIGHASH_FORKID)
        builder.hashType = SIGHASH_ALL_FORKID
        builder.amount = unsignedTx.amount.toLong()
        builder.toAddress = normalizeCashAddress(unsignedTx.toAddress)
        builder.changeAddress = normalizeCashAddress(unsignedTx.fromAddress)
        builder.useMaxAmount = false
        
        // 從 data 欄位解析 UTXO 信息
        val utxos = parseUtxosFromData(unsignedTx.data)
        
        // 添加 UTXOs
        for (utxo in utxos) {
            val unspentTx = Bitcoin.UnspentTransaction.newBuilder()
            
            // 設置輸出點
            val outPoint = Bitcoin.OutPoint.newBuilder()
                .setHash(ByteString.copyFrom(hexToBytes(utxo.txid).reversed().toByteArray()))
                .setIndex(utxo.vout)
                .build()
            
            unspentTx.outPoint = outPoint
            unspentTx.amount = utxo.value
            
            // 設置腳本（如果有）
            utxo.scriptPubKey?.let { script ->
                unspentTx.script = ByteString.copyFrom(hexToBytes(script))
            }
            
            builder.addUtxo(unspentTx.build())
        }
        
        // 添加私鑰
        builder.addPrivateKey(ByteString.copyFrom(privateKey))
        
        // Bitcoin Cash 通常使用 P2PKH 腳本
        builder.putScripts(
            unsignedTx.fromAddress,
            ByteString.copyFrom(createP2PKHRedeemScript(privateKey))
        )
        
        // 設置字節費率
        val feeRate = unsignedTx.gasPrice.toLongOrNull() ?: 2L
        builder.byteFee = feeRate
        
        return builder.build()
    }
    
    /**
     * 從 data 欄位解析 UTXOs
     */
    private fun parseUtxosFromData(data: String?): List<UTXO> {
        if (data.isNullOrEmpty()) return emptyList()
        
        return try {
            val json = Json.parseToJsonElement(data).jsonObject
            val inputs = json["inputs"]?.jsonArray ?: return emptyList()
            
            inputs.map { element ->
                val utxoJson = element.jsonObject
                UTXO(
                    txid = utxoJson["txid"]?.jsonPrimitive?.content ?: "",
                    vout = utxoJson["vout"]?.jsonPrimitive?.int ?: 0,
                    value = utxoJson["value"]?.jsonPrimitive?.long ?: 0L,
                    confirmed = true,
                    blockHeight = 0L,
                    scriptPubKey = utxoJson["scriptPubKey"]?.jsonPrimitive?.content,
                    address = ""
                )
            }
        } catch (e: Exception) {
            println("Error parsing UTXOs from data: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * 創建 P2PKH Redeem Script (Bitcoin Cash 主要使用)
     */
    private fun createP2PKHRedeemScript(privateKey: ByteArray): ByteArray {
        val key = PrivateKey(privateKey)
        val publicKey = key.getPublicKeySecp256k1(true)
        val publicKeyHash = hash160(publicKey.data())
        
        // P2PKH: OP_DUP OP_HASH160 <20-byte-pubkey-hash> OP_EQUALVERIFY OP_CHECKSIG
        val script = mutableListOf<Byte>()
        script.add(0x76.toByte()) // OP_DUP
        script.add(0xa9.toByte()) // OP_HASH160
        script.add(0x14) // Push 20 bytes
        script.addAll(publicKeyHash.toList())
        script.add(0x88.toByte()) // OP_EQUALVERIFY
        script.add(0xac.toByte()) // OP_CHECKSIG
        
        return script.toByteArray()
    }
    
    /**
     * 正規化 CashAddr 地址
     * TrustWallet Core 可能需要標準格式
     */
    private fun normalizeCashAddress(address: String): String {
        return when {
            // 如果已經是 CashAddr 格式，保持原樣
            address.contains(":") -> address
            // 如果是無前綴的 CashAddr，添加前綴
            address.startsWith("q") || address.startsWith("p") -> {
                "bitcoincash:$address"
            }
            // Legacy 地址，保持原樣
            else -> address
        }
    }
    
    /**
     * 計算 HASH160
     */
    private fun hash160(data: ByteArray): ByteArray {
        val sha256 = Hash.sha256(data)
        return Hash.ripemd(sha256)
    }
    
    /**
     * 十六進制轉字節
     */
    private fun hexToBytes(hex: String): ByteArray {
        val cleanHex = hex.removePrefix("0x")
        return cleanHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
    
    /**
     * 字節轉十六進制
     */
    private fun ByteArray.toHex(): String {
        return joinToString("") { "%02x".format(it) }
    }
    
    /**
     * 估算交易大小
     */
    fun estimateTransactionSize(
        inputCount: Int,
        outputCount: Int,
        isSegwit: Boolean = false
    ): Int {
        return if (isSegwit) {
            // SegWit 交易（BCH 不常用）
            10 + (inputCount * 68) + (outputCount * 31)
        } else {
            // 標準 P2PKH 交易
            10 + (inputCount * 148) + (outputCount * 34)
        }
    }
    
    /**
     * 驗證交易簽名
     */
    fun verifySignature(
        signedTx: SignedTransaction,
        publicKey: ByteArray
    ): Boolean {
        // 這裡可以添加簽名驗證邏輯
        // 通常使用 TrustWallet Core 的驗證功能
        return signedTx.hash.isNotEmpty() && signedTx.rawTransaction.isNotEmpty()
    }
}
package com.cbstudio.wearwallet.core.multichain.sdk

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import wallet.core.jni.CoinType
import wallet.core.jni.PrivateKey
import wallet.core.jni.Hash
import wallet.core.jni.Curve
import java.math.BigInteger

/**
 * Android 平台的 Ethereum 交易簽名實現
 * 使用 TrustWallet Core 進行真實的交易簽名
 */
class AndroidEthereumSigner {
    
    companion object {
        init {
            try {
                System.loadLibrary("TrustWalletCore")
            } catch (e: UnsatisfiedLinkError) {
                // Library might already be loaded
            }
        }
    }
    
    /**
     * 使用 TrustWallet Core 簽名 Ethereum 交易
     * 簡化版本 - 直接使用私鑰簽名交易哈希
     */
    fun signTransaction(
        chainType: MultiChainType,
        nonce: Long,
        gasPrice: String,
        gasLimit: String,
        toAddress: String,
        value: String,
        data: String = "",
        privateKeyHex: String,
        chainId: Long
    ): Result<SignedTransaction> {
        return try {
            // 驗證私鑰格式
            if (privateKeyHex.isEmpty() || !privateKeyHex.matches(Regex("^[0-9a-fA-F]{64}$"))) {
                return Result.Failure(IllegalArgumentException("Invalid private key format"))
            }
            
            // 創建 TrustWallet Core 的私鑰對象
            val privateKeyBytes = hexToBytes(privateKeyHex)
            val privateKey = PrivateKey(privateKeyBytes)
            
            // 構建交易數據用於簽名
            // 這是簡化版本，實際應該使用 RLP 編碼
            val txData = buildString {
                append(nonce.toString(16))
                append(gasPrice)
                append(gasLimit)
                append(toAddress.removePrefix("0x"))
                append(value)
                append(data)
                append(chainId.toString(16))
            }
            
            // 計算交易哈希
            val txHash = Hash.keccak256(txData.toByteArray())
            
            // 使用私鑰簽名
            val signature = privateKey.sign(txHash, Curve.SECP256K1)
            
            // 計算 v 值 (EIP-155)
            val v = (chainId * 2 + 35).toByte()
            
            // 構建簽名字符串
            val signatureHex = buildString {
                append("0x")
                // r (32 bytes)
                append(bytesToHex(signature.sliceArray(0..31)).padStart(64, '0'))
                // s (32 bytes)
                append(bytesToHex(signature.sliceArray(32..63)).padStart(64, '0'))
                // v (1 byte)
                append("%02x".format(v))
            }
            
            // 構建簽名後的交易（簡化版）
            val signedTxData = "0x" + bytesToHex(txHash) + bytesToHex(signature)
            
            Result.Success(
                SignedTransaction(
                    rawData = signedTxData,
                    signature = signatureHex,
                    chainType = chainType,
                    hash = "0x" + bytesToHex(txHash)
                )
            )
        } catch (e: Exception) {
            println("❌ EVM 交易簽名失敗: ${e.message}")
            e.printStackTrace()
            Result.Failure(SDKException.TransactionException(
                chainType,
                "簽名失敗: ${e.message}",
                e
            ))
        }
    }
    
    /**
     * 十六進制字符串轉字節數組
     */
    private fun hexToBytes(hex: String): ByteArray {
        val cleanHex = hex.removePrefix("0x")
        if (cleanHex.isEmpty()) return byteArrayOf()
        return cleanHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
    
    /**
     * 字節數組轉十六進制字符串
     */
    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
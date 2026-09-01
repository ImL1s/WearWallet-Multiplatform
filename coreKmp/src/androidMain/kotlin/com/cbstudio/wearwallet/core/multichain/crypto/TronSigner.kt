package com.cbstudio.wearwallet.core.multichain.crypto

import com.cbstudio.wearwallet.core.common.Result
import wallet.core.jni.PrivateKey
import wallet.core.jni.CoinType
import wallet.core.jni.Hash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * TRON 交易簽名器 - Android 實現
 * 使用 TrustWallet Core 的 ECDSA secp256k1 算法
 */
actual class TronSigner {

    companion object {
        init {
            try {
                System.loadLibrary("TrustWalletCore")
                println("✅ TrustWalletCore library loaded for TronSigner")
            } catch (e: UnsatisfiedLinkError) {
                println("⚠️ TrustWalletCore library already loaded or not found: ${e.message}")
            }
        }
    }
    /**
     * 對 TRON 交易的原始數據進行簽名
     *
     * 實現步驟：
     * 1. 將十六進制字符串轉換為字節數組
     * 2. 使用 SHA-256 計算交易哈希
     * 3. 使用 ECDSA secp256k1 對哈希進行簽名
     * 4. 返回簽名結果（65 字節）
     *
     * @param rawDataHex 交易原始數據的十六進制字符串
     * @param privateKey 私鑰字節數組（32 字節）
     * @return 簽名結果（65 字節：r(32) + s(32) + v(1)）
     */
    actual suspend fun signTransaction(
        rawDataHex: String,
        privateKey: ByteArray
    ): Result<ByteArray> = withContext(Dispatchers.Default) {
        var privKey: PrivateKey? = null
        try {
            // 驗證輸入
            if (rawDataHex.isEmpty()) {
                return@withContext Result.Failure(
                    IllegalArgumentException("Raw data hex cannot be empty")
                )
            }

            if (privateKey.size != 32) {
                return@withContext Result.Failure(
                    IllegalArgumentException("Private key must be 32 bytes, got ${privateKey.size}")
                )
            }

            // 1. 將十六進制字符串轉換為字節數組
            val rawDataBytes = hexToByteArray(rawDataHex)

            // 2. 計算 SHA-256 哈希
            val txHash = Hash.sha256(rawDataBytes)

            // 3. 創建 PrivateKey 對象並簽名
            privKey = PrivateKey(privateKey)
            val signature = privKey.sign(txHash, CoinType.TRON.curve())

            // 驗證簽名長度
            if (signature.size != 65) {
                return@withContext Result.Failure(
                    IllegalStateException("Invalid signature length: ${signature.size}, expected 65")
                )
            }

            Result.Success(signature)
        } catch (e: Exception) {
            Result.Failure(
                Exception("TRON transaction signing failed: ${e.message}", e)
            )
        } finally {
            // 清除敏感數據
            try {
                privateKey.fill(0)
                privKey?.let {
                    // TrustWallet Core 的 PrivateKey 會在 GC 時自動清理
                }
            } catch (e: Exception) {
                // 忽略清理錯誤
            }
        }
    }

    /**
     * 將十六進制字符串轉換為字節數組
     */
    private fun hexToByteArray(hex: String): ByteArray {
        val cleanHex = hex.removePrefix("0x").replace(" ", "")
        if (cleanHex.length % 2 != 0) {
            throw IllegalArgumentException("Hex string must have even length")
        }

        return ByteArray(cleanHex.length / 2) { i ->
            val index = i * 2
            cleanHex.substring(index, index + 2).toInt(16).toByte()
        }
    }
}
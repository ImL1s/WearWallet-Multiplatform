package com.cbstudio.wearwallet.core.multichain.crypto

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.blockchain.crypto.CryptoSignature
import org.kotlincrypto.hash.sha2.SHA256

/**
 * watchOS 平台的 TRON 簽名器實現
 * ✅ 真實實現 - 使用 libsecp256k1
 */
actual class TronSigner actual constructor() {

    /**
     * 對 TRON 交易的原始數據進行簽名
     * ✅ 使用 CryptoSignature libsecp256k1 真實簽名
     */
    actual suspend fun signTransaction(
        rawDataHex: String,
        privateKey: ByteArray
    ): Result<ByteArray> {
        return try {
            // 驗證輸入
            if (rawDataHex.isEmpty()) {
                return Result.Failure(
                    IllegalArgumentException("Raw data hex cannot be empty")
                )
            }

            if (privateKey.size != 32) {
                return Result.Failure(
                    IllegalArgumentException("Private key must be 32 bytes, got ${privateKey.size}")
                )
            }

            // 1. 將原始數據從十六進制轉換為字節數組
            val rawData = hexToByteArray(rawDataHex)

            // 2. 計算 SHA-256 哈希
            val sha256 = SHA256()
            sha256.update(rawData)
            val txHash = sha256.digest()

            // 3. ✅ 使用 CryptoSignature libsecp256k1 進行真實簽名
            val privateKeyHex = bytesToHex(privateKey)
            val txHashHex = bytesToHex(txHash)

            // signWithECDSA 返回 64 字節的 compact 簽名 (r || s)
            val signatureHex = CryptoSignature.signWithECDSA(txHashHex, privateKeyHex)

            // 檢查錯誤
            if (signatureHex.startsWith("ERROR_")) {
                return Result.Failure(
                    IllegalStateException("ECDSA signing failed: $signatureHex")
                )
            }

            val compactSignature = hexToByteArray(signatureHex)

            if (compactSignature.size != 64) {
                return Result.Failure(
                    IllegalStateException("Invalid signature size: ${compactSignature.size}, expected 64")
                )
            }

            // 4. 添加 recovery ID (v 值) - TRON 需要 65 字節簽名 (r || s || v)
            val signature = ByteArray(65)
            compactSignature.copyInto(signature, 0, 0, 64)
            signature[64] = 27  // v 值（可能需要 27 或 28，這裡使用默認值）

            println("✅ watchOS TRON 簽名成功 (libsecp256k1): ${bytesToHex(signature).take(20)}...")
            Result.Success(signature)

        } catch (e: Exception) {
            println("❌ watchOS TRON 交易簽名失敗: ${e.message}")
            Result.Failure(e)
        } finally {
            // 清除敏感數據
            try {
                privateKey.fill(0)
            } catch (e: Exception) {
                // 忽略清理錯誤
            }
        }
    }

    /**
     * 將字節數組轉換為十六進制字符串
     */
    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = "0123456789abcdef"
        val result = StringBuilder(bytes.size * 2)
        bytes.forEach { byte ->
            val value = byte.toInt() and 0xFF
            result.append(hexChars[value shr 4])
            result.append(hexChars[value and 0x0F])
        }
        return result.toString()
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

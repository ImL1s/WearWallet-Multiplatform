package io.github.iml1s.crypto

import org.kotlincrypto.hash.sha3.Keccak256 as KotlinCryptoKeccak256

/**
 * Keccak-256 哈希算法實現
 *
 * 注意：Keccak-256 ≠ SHA3-256
 * - Keccak-256: 以太坊使用的原始 Keccak 算法
 * - SHA3-256: NIST 標準化後的版本（有不同的填充）
 *
 * 本實現使用 KotlinCrypto/hash 庫，支援所有 KMP 平台：
 * - Android (JVM)
 * - iOS (Native)
 * - watchOS (Native)
 */
object Keccak256 {
    /**
     * 計算 Keccak-256 哈希
     *
     * @param data 要哈希的原始數據
     * @return 32 字節的哈希結果
     */
    fun hash(data: ByteArray): ByteArray {
        return KotlinCryptoKeccak256().digest(data)
    }

    /**
     * 從公鑰生成以太坊地址
     *
     * 以太坊地址生成規則：
     * 1. 對公鑰（64 字節，不含 0x04 前綴）計算 Keccak-256
     * 2. 取哈希結果的最後 20 字節
     * 3. 添加 "0x" 前綴
     *
     * @param publicKey 未壓縮的公鑰（64 字節，不含 0x04 前綴）
     * @return 以太坊地址（42 字符，含 "0x" 前綴）
     */
    fun ethereumAddress(publicKey: ByteArray): String {
        // 確保公鑰是 64 字節（不含 0x04 前綴）
        val pubKeyToHash = when (publicKey.size) {
            65 -> publicKey.sliceArray(1 until 65) // 去掉 0x04 前綴
            64 -> publicKey // 已經是正確格式
            33 -> {
                val point = Secp256k1Pure.decodePublicKey(publicKey)
                val uncompressed = Secp256k1Pure.encodePublicKey(point, compressed = false)
                uncompressed.sliceArray(1 until 65)
            }
            else -> throw IllegalArgumentException(
                "Invalid public key length: ${publicKey.size}, expected 33, 64 or 65 bytes"
            )
        }

        val hash = hash(pubKeyToHash)
        val addressBytes = hash.sliceArray(12 until 32) // 取最後 20 字節
        return "0x" + addressBytes.toHexString()
    }

    /**
     * 將 ByteArray 轉換為小寫十六進制字串
     */
    private fun ByteArray.toHexString(): String {
        return joinToString("") { byte ->
            byte.toUByte().toString(16).padStart(2, '0')
        }
    }
}

/**
 * ByteArray 擴展函數：計算 Keccak-256 哈希
 */
fun ByteArray.keccak256(): ByteArray = Keccak256.hash(this)

/**
 * String 擴展函數：將十六進制字串轉換為 ByteArray 並計算 Keccak-256
 */
fun String.keccak256(): ByteArray {
    val hex = this.removePrefix("0x")
    val bytes = ByteArray(hex.length / 2) { i ->
        hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
    }
    return bytes.keccak256()
}

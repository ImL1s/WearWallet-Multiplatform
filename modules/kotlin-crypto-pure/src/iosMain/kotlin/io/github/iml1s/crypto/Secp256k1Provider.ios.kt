package io.github.iml1s.crypto

import fr.acinq.secp256k1.Secp256k1

/**
 * iOS 平台 secp256k1 實現
 * 使用 ACINQ secp256k1-kmp 庫
 */
actual object Secp256k1Provider {

    /**
     * ECDSA 簽名
     * @param privateKey 32 字節私鑰
     * @param messageHash 32 字節消息哈希
     * @return 64 字節簽名 (r + s)
     */
    actual fun sign(privateKey: ByteArray, messageHash: ByteArray): ByteArray {
        require(privateKey.size == 32) { "Private key must be 32 bytes" }
        require(messageHash.size == 32) { "Message hash must be 32 bytes" }

        return Secp256k1.sign(messageHash, privateKey)
    }

    /**
     * ECDSA 簽名驗證（增強安全版本）
     *
     * 包含多層安全檢查：
     * 1. 輸入參數長度驗證
     * 2. 簽名參數 (r, s) 範圍檢查（由 ACINQ secp256k1-kmp 提供）
     * 3. 公鑰點驗證（由 ACINQ secp256k1-kmp 提供）
     * 4. 底層密碼學庫驗證
     *
     * @param signature 64 字節簽名 (r || s)
     * @param messageHash 32 字節消息哈希
     * @param publicKey 33 或 65 字節公鑰
     * @return true 如果簽名有效且安全，false 否則
     */
    actual fun verify(signature: ByteArray, messageHash: ByteArray, publicKey: ByteArray): Boolean {
        require(signature.size == 64) { "Signature must be 64 bytes" }
        require(messageHash.size == 32) { "Message hash must be 32 bytes" }
        require(publicKey.size == 33 || publicKey.size == 65) {
            "Public key must be 33 (compressed) or 65 (uncompressed) bytes"
        }

        return try {
            // ✅ 使用 ACINQ secp256k1-kmp 進行實際的 ECDSA 簽名驗證
            // 該庫已包含所有必要的安全檢查：
            // - 簽名參數 (r, s) 範圍檢查
            // - 公鑰點驗證
            // - 低 S 值驗證
            Secp256k1.verify(signature, messageHash, publicKey)
        } catch (e: Exception) {
            // 任何異常都視為驗證失敗
            false
        }
    }

    /**
     * 從私鑰派生公鑰
     *
     * ✅ 修復：正確處理壓縮/未壓縮格式轉換
     * ACINQ secp256k1-kmp 的 pubkeyCreate() 在 iOS 平台可能返回未壓縮格式
     *
     * @param privateKey 32 字節私鑰
     * @param compressed 是否返回壓縮格式（預設 true）
     * @return 33 字節（壓縮）或 65 字節（未壓縮）公鑰
     */
    actual fun computePublicKey(privateKey: ByteArray, compressed: Boolean): ByteArray {
        require(privateKey.size == 32) {
            "Private key must be 32 bytes, got ${privateKey.size}"
        }

        return try {
            // 從私鑰生成公鑰（ACINQ API）
            val rawPubkey = Secp256k1.pubkeyCreate(privateKey)

            // ✅ 根據實際大小和需求進行格式轉換
            when {
                // 情況 1: 需要壓縮，已經是壓縮格式
                compressed && rawPubkey.size == 33 -> {
                    rawPubkey
                }

                // 情況 2: 需要壓縮，但是未壓縮格式 (65 bytes)
                compressed && rawPubkey.size == 65 -> {
                    compressPublicKey(rawPubkey)
                }

                // 情況 3: 需要未壓縮，已經是未壓縮格式
                !compressed && rawPubkey.size == 65 -> {
                    rawPubkey
                }

                // 情況 4: 需要未壓縮，但是壓縮格式 (33 bytes)
                !compressed && rawPubkey.size == 33 -> {
                    // 使用 ACINQ API 解壓縮
                    Secp256k1.pubkeyParse(rawPubkey)
                }

                // 未預期的大小
                else -> {
                    throw IllegalStateException(
                        "Unexpected public key size: ${rawPubkey.size} bytes. " +
                                "Expected 33 (compressed) or 65 (uncompressed)."
                    )
                }
            }
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: IllegalStateException) {
            throw e
        } catch (e: Exception) {
            throw RuntimeException("Failed to compute public key: ${e.message}", e)
        }
    }

    /**
     * 壓縮公鑰（從 65-byte 未壓縮格式轉為 33-byte 壓縮格式）
     *
     * 公鑰格式：
     * - 未壓縮：04 || x (32 bytes) || y (32 bytes) = 65 bytes
     * - 壓縮：   prefix (1 byte) || x (32 bytes) = 33 bytes
     *   - prefix = 0x02 if y is even
     *   - prefix = 0x03 if y is odd
     */
    private fun compressPublicKey(uncompressed: ByteArray): ByteArray {
        require(uncompressed.size == 65) {
            "Uncompressed public key must be 65 bytes, got ${uncompressed.size}"
        }
        require(uncompressed[0] == 0x04.toByte()) {
            "Invalid uncompressed public key prefix: 0x${uncompressed[0].toHexString()}"
        }

        // 提取 x 和 y 座標
        val x = uncompressed.copyOfRange(1, 33)
        val y = uncompressed.copyOfRange(33, 65)

        // 判斷 y 的奇偶性
        val prefix = if (y.last().toInt() and 1 == 0) {
            0x02.toByte()  // y 是偶數
        } else {
            0x03.toByte()  // y 是奇數
        }

        // 構造壓縮公鑰：prefix || x
        return byteArrayOf(prefix) + x
    }

    /**
     * 輔助函數：Byte 轉 Hex 字符串（用於錯誤訊息）
     */
    private fun Byte.toHexString(): String {
        val value = this.toInt() and 0xFF
        return value.toString(16).padStart(2, '0')
    }

    /**
     * 驗證私鑰是否有效
     *
     * ✅ P1 修復：明確檢查全零私鑰
     * ACINQ secp256k1-kmp 的 secKeyVerify() 在某些版本未能正確拒絕全零私鑰
     */
    actual fun isValidPrivateKey(privateKey: ByteArray): Boolean {
        if (privateKey.size != 32) return false

        // ✅ 明確檢查全零私鑰（ACINQ 庫的 bug 修正）
        if (privateKey.all { it == 0.toByte() }) return false

        return try {
            Secp256k1.secKeyVerify(privateKey)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * ECDH 密鑰交換
     * @param privateKey 己方私鑰
     * @param publicKey 對方公鑰
     * @return 共享密鑰（32 字節）
     */
    actual fun ecdh(privateKey: ByteArray, publicKey: ByteArray): ByteArray {
        require(privateKey.size == 32) { "Private key must be 32 bytes" }
        require(publicKey.size == 33 || publicKey.size == 65) { "Invalid public key size" }

        return Secp256k1.ecdh(privateKey, publicKey)
    }
}

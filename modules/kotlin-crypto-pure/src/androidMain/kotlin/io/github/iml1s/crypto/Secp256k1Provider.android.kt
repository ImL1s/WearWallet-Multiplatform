package io.github.iml1s.crypto

import fr.acinq.secp256k1.Secp256k1

/**
 * Android 平台 secp256k1 實現
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
     * 2. 簽名參數 (r, s) 範圍檢查：r, s ∈ [1, n-1]
     * 3. 低 S 值驗證（防範簽名可塑性攻擊）
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
            // ✅ 安全層 1: 簽名參數範圍和安全性驗證
            // 檢查 r, s 範圍、低 S 值等安全要求
            if (!Secp256k1SecurityValidator.validateSignatureSecurity(signature, enforceLowS = true)) {
                // 簽名參數不符合安全要求（r 或 s 超出範圍，或 s 值過高）
                return false
            }

            // ✅ 安全層 2: 底層密碼學庫驗證
            // ACINQ secp256k1-kmp 進行實際的 ECDSA 簽名驗證
            Secp256k1.verify(signature, messageHash, publicKey)
        } catch (e: Exception) {
            // 任何異常都視為驗證失敗
            false
        }
    }

    /**
     * 從私鑰派生公鑰
     * @param privateKey 32 字節私鑰
     * @param compressed 是否返回壓縮格式（預設 true）
     * @return 33 字節（壓縮）或 65 字節（未壓縮）公鑰
     */
    actual fun computePublicKey(privateKey: ByteArray, compressed: Boolean): ByteArray {
        require(privateKey.size == 32) { "Private key must be 32 bytes" }

        val pubkey = Secp256k1.pubkeyCreate(privateKey)

        return if (compressed) {
            pubkey // 預設就是壓縮格式
        } else {
            Secp256k1.pubkeyParse(pubkey) // 轉換為未壓縮格式
        }
    }

    /**
     * 驗證私鑰是否有效
     */
    actual fun isValidPrivateKey(privateKey: ByteArray): Boolean {
        if (privateKey.size != 32) return false

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

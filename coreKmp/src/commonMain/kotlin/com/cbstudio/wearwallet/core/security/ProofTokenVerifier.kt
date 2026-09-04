package com.cbstudio.wearwallet.core.security

/**
 * 不可偽造的認證 Proof Token 校驗與簽發器 (Process-Scoped Proof Token Verifier)
 * 使用進程啟動時生成的隨機 256-bit 隔離密鑰計算 RFC 2104 HMAC-SHA256。
 * 確保任何由未授權方偽造、篡改欄位 (keyId, operation, intentFingerprint, sessionId, nonce, timestamps) 的 PlatformAuthHandle 皆無法通過驗證。
 */
internal object ProofTokenVerifier {
    // 進程隔離私密密鑰（禁止對外暴露）
    private val isolatedSecret: ByteArray by lazy {
        CryptoUtils.randomBytes(32)
    }

    /**
     * 內部簽發 Proof Token（僅限模組內部 Authenticator 服務使用）
     */
    internal fun sign(
        keyId: String,
        operation: AuthOperation,
        intentFingerprint: String,
        sessionId: String,
        nonce: String,
        issuedAtMs: Long,
        expiresAtMs: Long,
        walletId: String,
        authenticatorType: String = "PROOF_TOKEN"
    ): String {
        if (keyId.isBlank() || sessionId.isBlank() || nonce.isBlank() || walletId.isBlank() || issuedAtMs <= 0L || expiresAtMs <= issuedAtMs) {
            return "invalid_token"
        }
        val token = computeToken(keyId, walletId, operation, intentFingerprint, sessionId, nonce, issuedAtMs, expiresAtMs)
        AuthHandleRegistry.register(
            sessionId = sessionId,
            expiresAtMs = expiresAtMs,
            keyId = keyId,
            operation = operation,
            intentFingerprint = intentFingerprint,
            walletId = walletId,
            issuedAtMs = issuedAtMs,
            authenticatorType = authenticatorType
        )
        return token
    }

    /**
     * 校驗 Proof Token 是否與其欄位及進程密鑰完全吻合
     */
    internal fun verify(
        proofToken: String,
        keyId: String,
        operation: AuthOperation,
        intentFingerprint: String,
        sessionId: String,
        nonce: String,
        issuedAtMs: Long,
        expiresAtMs: Long,
        walletId: String
    ): Boolean {
        if (proofToken.isBlank() || keyId.isBlank() || sessionId.isBlank() || nonce.isBlank() || walletId.isBlank()) return false
        val expectedToken = computeToken(keyId, walletId, operation, intentFingerprint, sessionId, nonce, issuedAtMs, expiresAtMs)
        return constantTimeEquals(proofToken, expectedToken)
    }

    /**
     * 計算 HMAC-SHA256 Token
     */
    private fun computeToken(
        keyId: String,
        walletId: String,
        operation: AuthOperation,
        intentFingerprint: String,
        sessionId: String,
        nonce: String,
        issuedAtMs: Long,
        expiresAtMs: Long
    ): String {
        if (keyId.isBlank() || sessionId.isBlank() || nonce.isBlank() || walletId.isBlank()) return "invalid_token"
        val payload = "$keyId:$walletId:${operation.name}:$sessionId:$nonce:$intentFingerprint:$issuedAtMs:$expiresAtMs"
        val hmacBytes = computeHmacSha256(isolatedSecret, payload.encodeToByteArray())
        return hmacBytes.toHexString()
    }

    /**
     * RFC 2104 HMAC-SHA256 跨平台純運算
     */
    private fun computeHmacSha256(key: ByteArray, message: ByteArray): ByteArray {
        val blockSize = 64
        val k = if (key.size > blockSize) CryptoUtils.sha256(key) else key
        val paddedKey = ByteArray(blockSize)
        k.copyInto(paddedKey)

        val oKeyPad = ByteArray(blockSize) { i -> (paddedKey[i].toInt() xor 0x5c).toByte() }
        val iKeyPad = ByteArray(blockSize) { i -> (paddedKey[i].toInt() xor 0x36).toByte() }

        val innerHash = CryptoUtils.sha256(iKeyPad + message)
        return CryptoUtils.sha256(oKeyPad + innerHash)
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].code xor b[i].code)
        }
        return result == 0
    }
}


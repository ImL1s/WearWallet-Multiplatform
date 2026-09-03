package com.cbstudio.wearwallet.core.security

/**
 * 不可偽造的 Deletion Authorization Grant 校驗與簽發器 (Process-Scoped Deletion Grant Verifier)
 * 使用進程啟動時生成的隨機 256-bit 隔離密鑰計算 RFC 2104 HMAC-SHA256。
 * 確保任何由未授權方偽造、篡改欄位 (walletId, keyAlias, operation, originalSessionId, nonce, timestamps) 的 Grant 皆無法通過驗證。
 */
internal object DeletionGrantVerifier {
    // 進程隔離私密密鑰（禁止對外暴露）
    private val isolatedSecret: ByteArray by lazy {
        CryptoUtils.randomBytes(32)
    }

    /**
     * 內部簽發 Grant HMAC-SHA256 Token
     */
    internal fun sign(
        walletId: String,
        keyAlias: String,
        operation: AuthOperation,
        originalSessionId: String,
        nonce: String,
        issuedAtMs: Long,
        expiresAtMs: Long
    ): String {
        if (walletId.isBlank() || keyAlias.isBlank() || originalSessionId.isBlank() || nonce.isBlank()) {
            return "invalid_grant_token"
        }
        return computeToken(walletId, keyAlias, operation, originalSessionId, nonce, issuedAtMs, expiresAtMs)
    }

    /**
     * 校驗 DeletionAuthorizationGrant 是否吻合 HMAC 防偽簽名
     */
    internal fun verify(grant: DeletionAuthorizationGrant): Boolean {
        if (grant.walletId.isBlank() || grant.keyAlias.isBlank() || grant.originalAuthSessionId.isBlank() || grant.nonce.isBlank() || grant.proofToken.isBlank()) {
            return false
        }
        if (grant.operation != AuthOperation.DELETE) {
            return false
        }
        val expectedToken = computeToken(
            walletId = grant.walletId,
            keyAlias = grant.keyAlias,
            operation = grant.operation,
            originalSessionId = grant.originalAuthSessionId,
            nonce = grant.nonce,
            issuedAtMs = grant.issuedAtMs,
            expiresAtMs = grant.expiresAtMs
        )
        return constantTimeEquals(grant.proofToken, expectedToken)
    }

    /**
     * 直接以各欄位校驗 HMAC 防偽簽名
     */
    internal fun verifyDirect(
        proofToken: String,
        walletId: String,
        keyAlias: String,
        operation: AuthOperation,
        originalSessionId: String,
        nonce: String,
        issuedAtMs: Long,
        expiresAtMs: Long
    ): Boolean {
        if (proofToken.isBlank() || walletId.isBlank() || keyAlias.isBlank() || originalSessionId.isBlank() || nonce.isBlank()) {
            return false
        }
        if (operation != AuthOperation.DELETE) {
            return false
        }
        val expectedToken = computeToken(walletId, keyAlias, operation, originalSessionId, nonce, issuedAtMs, expiresAtMs)
        return constantTimeEquals(proofToken, expectedToken)
    }

    /**
     * 計算 HMAC-SHA256 Token
     */
    private fun computeToken(
        walletId: String,
        keyAlias: String,
        operation: AuthOperation,
        originalSessionId: String,
        nonce: String,
        issuedAtMs: Long,
        expiresAtMs: Long
    ): String {
        if (walletId.isBlank() || keyAlias.isBlank() || originalSessionId.isBlank() || nonce.isBlank()) {
            return "invalid_grant_token"
        }
        val payload = "$walletId:$keyAlias:${operation.name}:$originalSessionId:$nonce:$issuedAtMs:$expiresAtMs"
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

package com.cbstudio.wearwallet.core.security

/**
 * 不可偽造的 Recovery Grant 校驗與簽發器 (Process-Scoped Recovery Grant Verifier)
 * 使用進程啟動時生成的隨機 256-bit 隔離密鑰計算 RFC 2104 HMAC-SHA256。
 * 確保任何由未授權方偽造、篡改欄位的 RecoveryGrant 皆無法通過驗證。
 */
internal object RecoveryGrantVerifier {
    private val isolatedSecret: ByteArray by lazy {
        CryptoUtils.randomBytes(32)
    }

    internal fun sign(
        journalRowHash: String,
        sessionId: String,
        alias: String,
        state: String,
        zeroActiveReferenceProof: String,
        nonce: String,
        issuedAtMs: Long,
        expiresAtMs: Long
    ): String {
        if (journalRowHash.isBlank() || sessionId.isBlank() || alias.isBlank() || state.isBlank() || zeroActiveReferenceProof.isBlank() || nonce.isBlank()) {
            return "invalid_grant_token"
        }
        return computeToken(journalRowHash, sessionId, alias, state, zeroActiveReferenceProof, nonce, issuedAtMs, expiresAtMs)
    }

    internal fun verify(grant: RecoveryGrant): Boolean {
        if (grant.journalRowHash.isBlank() || grant.sessionId.isBlank() || grant.alias.isBlank() ||
            grant.state.isBlank() || grant.zeroActiveReferenceProof.isBlank() || grant.nonce.isBlank() || grant.proofToken.isBlank()
        ) {
            return false
        }
        val expectedToken = computeToken(
            journalRowHash = grant.journalRowHash,
            sessionId = grant.sessionId,
            alias = grant.alias,
            state = grant.state,
            zeroActiveReferenceProof = grant.zeroActiveReferenceProof,
            nonce = grant.nonce,
            issuedAtMs = grant.issuedAtMs,
            expiresAtMs = grant.expiresAtMs
        )
        return constantTimeEquals(grant.proofToken, expectedToken)
    }

    private fun computeToken(
        journalRowHash: String,
        sessionId: String,
        alias: String,
        state: String,
        zeroActiveReferenceProof: String,
        nonce: String,
        issuedAtMs: Long,
        expiresAtMs: Long
    ): String {
        if (journalRowHash.isBlank() || sessionId.isBlank() || alias.isBlank() || state.isBlank() || zeroActiveReferenceProof.isBlank() || nonce.isBlank()) {
            return "invalid_grant_token"
        }
        val payload = "$journalRowHash:$sessionId:$alias:$state:$zeroActiveReferenceProof:$nonce:$issuedAtMs:$expiresAtMs"
        val hmacBytes = computeHmacSha256(isolatedSecret, payload.encodeToByteArray())
        return hmacBytes.toHexString()
    }

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

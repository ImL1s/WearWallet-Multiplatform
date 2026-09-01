package com.cbstudio.wearwallet.core.security

import kotlinx.datetime.Clock

/**
 * 敏感操作枚舉 (Operation Type)
 */
enum class AuthOperation {
    SIGN,
    EXPORT,
    DELETE,
    REVEAL,
    IMPORT;

    companion object {
        val REVEAL_SECRET = REVEAL
        val REVEAL_SEED = REVEAL
    }
}

/**
 * 平台特定認證句柄 (Platform-Specific Typed Authorization Enclave Handle)
 * 跨平台封裝硬體認證/生物識別認證物件 (例如 Android BiometricPrompt.CryptoObject、Apple LAContext)
 *
 * 安全規範：
 * 1. 嚴格防偽：內建不可偽造的 proofToken，由 ProofTokenVerifier 進行完整性校驗。
 * 2. 嚴格比對：isValid 拒絕空字串/萬用字元繞過，精確比對 keyId、operation、intentFingerprint。
 * 3. 生命週期保護：支援 invalidate() 單次使用即刻作廢，防重放攻擊。
 */
expect class PlatformAuthHandle {
    val keyId: String
    val operation: AuthOperation
    val intentFingerprint: String
    val sessionId: String
    val nonce: String
    val issuedAtMs: Long
    val expiresAtMs: Long
    val isInvalidated: Boolean
    val proofToken: String
    val walletId: String

    internal constructor(
        keyId: String,
        operation: AuthOperation,
        intentFingerprint: String,
        sessionId: String,
        nonce: String,
        issuedAtMs: Long,
        expiresAtMs: Long,
        proofToken: String,
        walletId: String
    )

    fun invalidate()
    fun isExpired(currentTimeMs: Long = Clock.System.now().toEpochMilliseconds()): Boolean
    fun isValid(
        expectedKeyId: String? = null,
        expectedIntentFingerprint: String? = null,
        expectedOperation: AuthOperation? = null,
        currentTimeMs: Long = Clock.System.now().toEpochMilliseconds(),
        expectedWalletId: String? = null
    ): Boolean
}
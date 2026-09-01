package com.cbstudio.wearwallet.core.security

import kotlinx.datetime.Clock

/**
 * 一次性錢包刪除授權憑證 (Single-Use Deletion Authorization Grant)
 *
 * 當 PlatformAuthHandle 於 Phase 1 經 AuthHandleRegistry.validateAndConsume 成功消費後簽發。
 * 僅限用於驅動該特定錢包與金鑰的完整 5-State Deletion Machine 與 KeyVault 實體刪除。
 *
 * 構造函數設為 internal，禁止外部模組或 Repository 直接 new 出 Grant 物件。
 */
class DeletionAuthorizationGrant internal constructor(
    val walletId: String,
    val keyAlias: String,
    val operation: AuthOperation = AuthOperation.DELETE,
    val originalAuthSessionId: String,
    val issuedAtMs: Long = Clock.System.now().toEpochMilliseconds(),
    val expiresAtMs: Long,
    val nonce: String,
    val proofToken: String
) {
    val isExpired: Boolean
        get() = expiresAtMs > 0 && Clock.System.now().toEpochMilliseconds() > expiresAtMs

    fun isExpired(currentTimeMs: Long = Clock.System.now().toEpochMilliseconds()): Boolean {
        return expiresAtMs > 0 && currentTimeMs > expiresAtMs
    }

    fun isValidFor(targetWalletId: String, targetKeyAlias: String): Boolean {
        if (isExpired) return false
        if (walletId != targetWalletId) return false
        if (keyAlias != targetKeyAlias) return false
        if (operation != AuthOperation.DELETE) return false
        return true
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DeletionAuthorizationGrant) return false
        return walletId == other.walletId &&
                keyAlias == other.keyAlias &&
                operation == other.operation &&
                originalAuthSessionId == other.originalAuthSessionId &&
                issuedAtMs == other.issuedAtMs &&
                expiresAtMs == other.expiresAtMs &&
                nonce == other.nonce &&
                proofToken == other.proofToken
    }

    override fun hashCode(): Int {
        var result = walletId.hashCode()
        result = 31 * result + keyAlias.hashCode()
        result = 31 * result + operation.hashCode()
        result = 31 * result + originalAuthSessionId.hashCode()
        result = 31 * result + issuedAtMs.hashCode()
        result = 31 * result + expiresAtMs.hashCode()
        result = 31 * result + nonce.hashCode()
        result = 31 * result + proofToken.hashCode()
        return result
    }

    override fun toString(): String {
        return "DeletionAuthorizationGrant(walletId='$walletId', keyAlias='$keyAlias', operation=$operation, " +
                "originalAuthSessionId='$originalAuthSessionId', issuedAtMs=$issuedAtMs, expiresAtMs=$expiresAtMs, nonce='$nonce')"
    }
}

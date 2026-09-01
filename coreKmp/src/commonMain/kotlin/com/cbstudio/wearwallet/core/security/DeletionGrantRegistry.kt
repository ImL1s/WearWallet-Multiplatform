package com.cbstudio.wearwallet.core.security

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.multichain.monero.synchronized
import kotlinx.datetime.Clock

internal data class DeletionGrantMetadata(
    val nonce: String,
    val walletId: String,
    val keyAlias: String,
    val operation: AuthOperation,
    val originalAuthSessionId: String,
    val issuedAtMs: Long,
    val expiresAtMs: Long,
    val proofToken: String
)

/**
 * 進程範圍單次使用 Deletion Grant Nonce 註冊表 (Process-Scoped Single-Use Deletion Grant Registry)
 * 負責追蹤已簽發的有效 DeletionAuthorizationGrant，確保每個 Grant Nonce 僅能被原子消費一次。
 * 杜絕 Grant 重放、併發 TOCTOU 競爭與跨金鑰/跨操作刪除。
 */
internal object DeletionGrantRegistry {
    private val lock = Any()
    private val activeGrants = mutableMapOf<String, DeletionGrantMetadata>() // nonce -> metadata
    private val consumedGrants = mutableSetOf<String>()                     // nonce

    /**
     * 註冊新簽發的 Deletion Authorization Grant
     */
    internal fun register(grant: DeletionAuthorizationGrant) {
        val nonce = grant.nonce
        if (nonce.isBlank()) throw IllegalArgumentException("Grant nonce must not be blank")
        synchronized(lock) {
            if (consumedGrants.contains(nonce)) {
                throw IllegalStateException("Cannot register already consumed deletion grant nonce: $nonce")
            }
            if (activeGrants.containsKey(nonce)) {
                throw IllegalStateException("Cannot register already active deletion grant nonce: $nonce")
            }
            activeGrants[nonce] = DeletionGrantMetadata(
                nonce = nonce,
                walletId = grant.walletId,
                keyAlias = grant.keyAlias,
                operation = grant.operation,
                originalAuthSessionId = grant.originalAuthSessionId,
                issuedAtMs = grant.issuedAtMs,
                expiresAtMs = grant.expiresAtMs,
                proofToken = grant.proofToken
            )
        }
    }

    /**
     * 在單一同步鎖中原子性校驗並消費 DeletionAuthorizationGrant
     * 消除 Time-of-Check to Time-of-Use (TOCTOU) 併發重放與竄改漏洞。
     *
     * 原子校驗項：
     * 1. grant 非空且 expectedKeyAlias 非空
     * 2. nonce 非空、已於 activeGrants 註冊且未被消費 (consumedGrants.contains == false)
     * 3. grant 未過期 (expiresAtMs <= 0 || now <= expiresAtMs) 且非未來簽發 (issuedAtMs <= 0 || now >= issuedAtMs)
     * 4. keyAlias 精確吻合 expectedKeyAlias (拒絕空字串、跨金鑰或萬用字元)
     * 5. operation 精確為 AuthOperation.DELETE
     * 6. walletId 與 originalAuthSessionId 與登記的 metadata 精確一致
     * 7. DeletionGrantVerifier.verify 驗證進程級 HMAC-SHA256 防偽簽名
     *
     * 若全數通過，則在同一鎖內立即將 nonce 自 activeGrants 移除並加入 consumedGrants，
     * 並回傳 Result.Success(Unit)；若有任何一項不符合，則回傳 Result.Failure(AuthenticationRequiredException(...))。
     */
    internal fun validateAndConsume(
        grant: DeletionAuthorizationGrant?,
        expectedKeyAlias: String,
        currentTimeMs: Long = Clock.System.now().toEpochMilliseconds(),
        expectedWalletId: String = ""
    ): Result<Unit> {
        if (expectedKeyAlias.isBlank()) {
            return Result.Failure(IllegalArgumentException("expectedKeyAlias must not be blank"))
        }
        if (grant == null) {
            return Result.Failure(AuthenticationRequiredException("Deletion grant is null"))
        }

        return synchronized(lock) {
            val nonce = grant.nonce
            if (nonce.isBlank()) {
                return@synchronized Result.Failure(AuthenticationRequiredException("Grant nonce is blank"))
            }
            if (consumedGrants.contains(nonce)) {
                return@synchronized Result.Failure(
                    AuthenticationRequiredException("Deletion grant nonce '$nonce' has already been consumed (replay rejected)")
                )
            }
            val meta = activeGrants[nonce]
                ?: return@synchronized Result.Failure(
                    AuthenticationRequiredException("Deletion grant nonce '$nonce' is not registered in active grants")
                )

            if (grant.isExpired(currentTimeMs) || (meta.expiresAtMs > 0 && currentTimeMs > meta.expiresAtMs)) {
                activeGrants.remove(nonce)
                return@synchronized Result.Failure(
                    AuthenticationRequiredException("Deletion grant for key '$expectedKeyAlias' has expired")
                )
            }
            if (grant.issuedAtMs > 0 && currentTimeMs < grant.issuedAtMs) {
                return@synchronized Result.Failure(
                    AuthenticationRequiredException("Deletion grant for key '$expectedKeyAlias' is not yet valid")
                )
            }
            if (grant.keyAlias != expectedKeyAlias || meta.keyAlias != expectedKeyAlias) {
                return@synchronized Result.Failure(
                    AuthenticationRequiredException("Key alias mismatch: expected '$expectedKeyAlias' but grant has '${grant.keyAlias}'")
                )
            }
            if (grant.operation != AuthOperation.DELETE || meta.operation != AuthOperation.DELETE) {
                return@synchronized Result.Failure(
                    AuthenticationRequiredException("Grant operation '${grant.operation}' is not DELETE")
                )
            }
            if (expectedWalletId.isNotBlank()) {
                if (grant.walletId != expectedWalletId || meta.walletId != expectedWalletId) {
                    return@synchronized Result.Failure(
                        AuthenticationRequiredException("Cross-wallet deletion grant rejected: expected walletId '$expectedWalletId' but grant has '${grant.walletId}'")
                    )
                }
            }
            if (grant.walletId != meta.walletId || grant.originalAuthSessionId != meta.originalAuthSessionId) {
                return@synchronized Result.Failure(
                    AuthenticationRequiredException("Grant metadata mismatch (tampering detected)")
                )
            }

            // 校驗 HMAC 防偽簽名
            val isHmacValid = DeletionGrantVerifier.verify(grant)
            if (!isHmacValid) {
                return@synchronized Result.Failure(
                    AuthenticationRequiredException("Deletion grant HMAC signature verification failed for key '$expectedKeyAlias'")
                )
            }

            // 原子性消費
            activeGrants.remove(nonce)
            consumedGrants.add(nonce)
            Result.Success(Unit)
        }
    }

    /**
     * 檢查 Grant Nonce 是否已被消費
     */
    internal fun isConsumed(grantNonce: String): Boolean = synchronized(lock) {
        consumedGrants.contains(grantNonce)
    }

    /**
     * 檢查 Grant Nonce 是否處於註冊且未被消費狀態
     */
    internal fun isRegistered(grantNonce: String): Boolean = synchronized(lock) {
        activeGrants.containsKey(grantNonce)
    }

    /**
     * 測試重置使用
     */
    internal fun clearForTesting() = synchronized(lock) {
        activeGrants.clear()
        consumedGrants.clear()
    }
}

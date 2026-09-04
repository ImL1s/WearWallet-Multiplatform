package com.cbstudio.wearwallet.core.security

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.multichain.monero.synchronized
import kotlinx.datetime.Clock

internal data class RecoveryGrantMetadata(
    val journalRowHash: String,
    val sessionId: String,
    val alias: String,
    val state: String,
    val zeroActiveReferenceProof: String,
    val nonce: String,
    val issuedAtMs: Long,
    val expiresAtMs: Long,
    val proofToken: String
)

/**
 * 進程範圍單次使用 Recovery Grant Nonce 註冊表 (Process-Scoped Single-Use Recovery Grant Registry)
 * 確保每個 RecoveryGrant 僅能被原子消費一次，消除重放與併發 TOCTOU 競爭。
 */
internal object RecoveryGrantRegistry {
    private val lock = Any()
    private val activeGrants = mutableMapOf<String, RecoveryGrantMetadata>()
    private val consumedGrants = mutableSetOf<String>()

    internal fun register(grant: RecoveryGrant) {
        val nonce = grant.nonce
        if (nonce.isBlank()) throw IllegalArgumentException("Grant nonce must not be blank")
        synchronized(lock) {
            if (consumedGrants.contains(nonce)) {
                throw IllegalStateException("Cannot register already consumed recovery grant nonce: $nonce")
            }
            if (activeGrants.containsKey(nonce)) {
                throw IllegalStateException("Cannot register already active recovery grant nonce: $nonce")
            }
            activeGrants[nonce] = RecoveryGrantMetadata(
                journalRowHash = grant.journalRowHash,
                sessionId = grant.sessionId,
                alias = grant.alias,
                state = grant.state,
                zeroActiveReferenceProof = grant.zeroActiveReferenceProof,
                nonce = nonce,
                issuedAtMs = grant.issuedAtMs,
                expiresAtMs = grant.expiresAtMs,
                proofToken = grant.proofToken
            )
        }
    }

    internal fun validateAndConsume(
        grant: RecoveryGrant?,
        expectedAlias: String,
        currentTimeMs: Long = Clock.System.now().toEpochMilliseconds()
    ): Result<Unit> {
        if (expectedAlias.isBlank()) {
            return Result.Failure(IllegalArgumentException("expectedAlias must not be blank"))
        }
        if (grant == null) {
            return Result.Failure(AuthenticationRequiredException("Recovery grant is null"))
        }

        return synchronized(lock) {
            val nonce = grant.nonce
            if (nonce.isBlank()) {
                return@synchronized Result.Failure(AuthenticationRequiredException("Grant nonce is blank"))
            }
            if (consumedGrants.contains(nonce)) {
                return@synchronized Result.Failure(
                    AuthenticationRequiredException("Recovery grant nonce '$nonce' has already been consumed (replay rejected)")
                )
            }
            val meta = activeGrants[nonce]
                ?: return@synchronized Result.Failure(
                    AuthenticationRequiredException("Recovery grant nonce '$nonce' is not registered in active grants")
                )

            if (grant.isExpired(currentTimeMs) || (meta.expiresAtMs > 0 && currentTimeMs > meta.expiresAtMs)) {
                activeGrants.remove(nonce)
                return@synchronized Result.Failure(
                    AuthenticationRequiredException("Recovery grant for alias '$expectedAlias' has expired")
                )
            }
            if (grant.issuedAtMs > 0 && currentTimeMs < grant.issuedAtMs) {
                return@synchronized Result.Failure(
                    AuthenticationRequiredException("Recovery grant for alias '$expectedAlias' is not yet valid")
                )
            }
            if (grant.alias != expectedAlias || meta.alias != expectedAlias) {
                return@synchronized Result.Failure(
                    AuthenticationRequiredException("Alias mismatch: expected '$expectedAlias' but grant has '${grant.alias}'")
                )
            }
            if (grant.journalRowHash != meta.journalRowHash || grant.sessionId != meta.sessionId ||
                grant.state != meta.state || grant.zeroActiveReferenceProof != meta.zeroActiveReferenceProof
            ) {
                return@synchronized Result.Failure(
                    AuthenticationRequiredException("Recovery grant metadata mismatch (tampering detected)")
                )
            }

            val isHmacValid = RecoveryGrantVerifier.verify(grant)
            if (!isHmacValid) {
                return@synchronized Result.Failure(
                    AuthenticationRequiredException("Recovery grant HMAC signature verification failed for alias '$expectedAlias'")
                )
            }

            activeGrants.remove(nonce)
            consumedGrants.add(nonce)
            Result.Success(Unit)
        }
    }

    internal fun isConsumed(grantNonce: String): Boolean = synchronized(lock) {
        consumedGrants.contains(grantNonce)
    }

    internal fun isRegistered(grantNonce: String): Boolean = synchronized(lock) {
        activeGrants.containsKey(grantNonce)
    }

    internal fun clearForTesting() = synchronized(lock) {
        activeGrants.clear()
        consumedGrants.clear()
    }
}

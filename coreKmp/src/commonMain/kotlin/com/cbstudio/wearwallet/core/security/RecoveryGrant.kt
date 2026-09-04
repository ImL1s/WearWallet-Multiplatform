package com.cbstudio.wearwallet.core.security

import kotlinx.datetime.Clock

/**
 * 一次性金鑰恢復與回滾授權憑證 (Single-Use Recovery Grant)
 *
 * 當未完成之 StagingJournal / 未綁定 Staged Key 於啟動對齊流程經 5-Layer 嚴格檢驗合格後簽發。
 * 僅限用於驅動 KeyVaultReconciliationCapability.rollbackStagedKeyInternal 實體回滾/清除孤立金鑰。
 *
 * 構造函數設為 internal，禁止外部模組或惡意調用方直接 new 出 Grant 物件。
 * 非 data class，防止透過 .copy() 繞過驗證欄位。
 */
class RecoveryGrant internal constructor(
    val journalRowHash: String,
    val sessionId: String,
    val alias: String,
    val state: String,
    val zeroActiveReferenceProof: String,
    val nonce: String,
    val issuedAtMs: Long = Clock.System.now().toEpochMilliseconds(),
    val expiresAtMs: Long,
    val proofToken: String
) {
    internal companion object {
        internal fun create(
            journalRowHash: String,
            sessionId: String,
            alias: String,
            state: String,
            zeroActiveReferenceProof: String,
            currentTimeMs: Long = Clock.System.now().toEpochMilliseconds()
        ): RecoveryGrant {
            val nonce = CryptoUtils.randomBytes(16).toHexString()
            val expiresAtMs = currentTimeMs + 60_000L
            val proofToken = RecoveryGrantVerifier.sign(
                journalRowHash = journalRowHash,
                sessionId = sessionId,
                alias = alias,
                state = state,
                zeroActiveReferenceProof = zeroActiveReferenceProof,
                nonce = nonce,
                issuedAtMs = currentTimeMs,
                expiresAtMs = expiresAtMs
            )
            return RecoveryGrant(
                journalRowHash = journalRowHash,
                sessionId = sessionId,
                alias = alias,
                state = state,
                zeroActiveReferenceProof = zeroActiveReferenceProof,
                nonce = nonce,
                issuedAtMs = currentTimeMs,
                expiresAtMs = expiresAtMs,
                proofToken = proofToken
            )
        }
    }

    val isExpired: Boolean
        get() = expiresAtMs > 0 && Clock.System.now().toEpochMilliseconds() > expiresAtMs

    fun isExpired(currentTimeMs: Long = Clock.System.now().toEpochMilliseconds()): Boolean {
        return expiresAtMs > 0 && currentTimeMs > expiresAtMs
    }

    fun isValidFor(targetAlias: String, targetSessionId: String): Boolean {
        if (isExpired()) return false
        if (alias != targetAlias) return false
        if (sessionId != targetSessionId) return false
        return true
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RecoveryGrant) return false
        return journalRowHash == other.journalRowHash &&
                sessionId == other.sessionId &&
                alias == other.alias &&
                state == other.state &&
                zeroActiveReferenceProof == other.zeroActiveReferenceProof &&
                nonce == other.nonce &&
                issuedAtMs == other.issuedAtMs &&
                expiresAtMs == other.expiresAtMs &&
                proofToken == other.proofToken
    }

    override fun hashCode(): Int {
        var result = journalRowHash.hashCode()
        result = 31 * result + sessionId.hashCode()
        result = 31 * result + alias.hashCode()
        result = 31 * result + state.hashCode()
        result = 31 * result + zeroActiveReferenceProof.hashCode()
        result = 31 * result + nonce.hashCode()
        result = 31 * result + issuedAtMs.hashCode()
        result = 31 * result + expiresAtMs.hashCode()
        result = 31 * result + proofToken.hashCode()
        return result
    }

    override fun toString(): String {
        return "RecoveryGrant(journalRowHash='$journalRowHash', sessionId='$sessionId', alias='$alias', state='$state', " +
                "zeroActiveReferenceProof='$zeroActiveReferenceProof', nonce='$nonce', issuedAtMs=$issuedAtMs, expiresAtMs=$expiresAtMs)"
    }
}

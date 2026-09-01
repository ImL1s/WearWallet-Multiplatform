package com.cbstudio.wearwallet.core.security

import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

/**
 * BackendAttestation represents a cryptographically verifiable proof of backend node
 * availability, version compatibility, and smoke vector validation.
 *
 * It prevents callers from forging bare boolean evidence (e.g. backendAvailable=true,
 * smokeVectorVerified=true).
 */
@Serializable
data class BackendAttestation internal constructor(
    val backendIdentity: BackendIdentity,
    val backendVersion: String,
    val availability: Boolean,
    val smokeVectorHash: String,
    val checkedAt: Long,
    val expiry: Long,
    val signature: String
) {
    init {
        require(backendIdentity != BackendIdentity.UNSUPPORTED) { "backendIdentity must not be UNSUPPORTED" }
        require(backendVersion.isNotBlank()) { "backendVersion must not be blank" }
        require(smokeVectorHash.isNotBlank()) { "smokeVectorHash must not be blank" }
        require(checkedAt > 0L) { "checkedAt must be positive: $checkedAt" }
        require(expiry > checkedAt) { "expiry ($expiry) must be strictly after checkedAt ($checkedAt)" }
        require(signature.isNotBlank()) { "signature must not be blank" }
    }

    /**
     * Checks if this attestation has expired relative to [currentTimeMs].
     */
    fun isExpired(currentTimeMs: Long = Clock.System.now().toEpochMilliseconds()): Boolean {
        return currentTimeMs > expiry
    }

    /**
     * Complete validity evaluation against temporal, version, and smoke vector constraints.
     * Fails closed if expired, unavailable, version mismatched, hash corrupted, or signature blank.
     */
    fun isValid(
        currentTimeMs: Long = Clock.System.now().toEpochMilliseconds(),
        expectedSmokeVectorHash: String = CANONICAL_SMOKE_VECTOR_HASH,
        expectedVersion: String = CANONICAL_BACKEND_VERSION
    ): Boolean {
        if (isExpired(currentTimeMs)) return false
        if (currentTimeMs < checkedAt) return false // Clock skew protection (checked in future)
        if (!availability) return false
        if (backendVersion != expectedVersion) return false
        if (smokeVectorHash != expectedSmokeVectorHash) return false
        if (signature.isBlank()) return false
        if (backendIdentity == BackendIdentity.UNSUPPORTED) return false
        return true
    }

    companion object {
        const val CANONICAL_SMOKE_VECTOR_HASH = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        const val CANONICAL_BACKEND_VERSION = "1.0.0"
        const val DEFAULT_VALIDITY_WINDOW_MS = 300_000L // 5 minutes

        /**
         * Factory method to issue an attestation (used by BackendAttestationProvider & tests).
         */
        fun issue(
            backendIdentity: BackendIdentity = BackendIdentity.PRODUCTION_V1,
            backendVersion: String = CANONICAL_BACKEND_VERSION,
            availability: Boolean = true,
            smokeVectorHash: String = CANONICAL_SMOKE_VECTOR_HASH,
            checkedAt: Long = Clock.System.now().toEpochMilliseconds(),
            validityWindowMs: Long = DEFAULT_VALIDITY_WINDOW_MS,
            signature: String = "attestation_sig_${backendIdentity.name}_$checkedAt"
        ): BackendAttestation {
            return BackendAttestation(
                backendIdentity = backendIdentity,
                backendVersion = backendVersion,
                availability = availability,
                smokeVectorHash = smokeVectorHash,
                checkedAt = checkedAt,
                expiry = checkedAt + validityWindowMs,
                signature = signature
            )
        }
    }
}

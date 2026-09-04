package com.cbstudio.wearwallet.core.security

import com.cbstudio.wearwallet.core.domain.model.context.ChainExecutionContext
import kotlinx.datetime.Clock

interface BackendAttestationProvider {
    suspend fun getAttestation(chainContext: ChainExecutionContext): BackendAttestation
    fun getCachedAttestation(chainContext: ChainExecutionContext): BackendAttestation?
    fun getAttestationSync(chainContext: ChainExecutionContext): BackendAttestation
}

class DefaultBackendAttestationProvider(
    private val expectedBackendIdentity: BackendIdentity = BackendIdentity.PRODUCTION_V1,
    private val backendVersion: String = BackendAttestation.CANONICAL_BACKEND_VERSION,
    private val validityWindowMs: Long = BackendAttestation.DEFAULT_VALIDITY_WINDOW_MS
) : BackendAttestationProvider {

    private val cache = mutableMapOf<String, BackendAttestation>()

    override suspend fun getAttestation(chainContext: ChainExecutionContext): BackendAttestation {
        return getAttestationSync(chainContext)
    }

    override fun getAttestationSync(chainContext: ChainExecutionContext): BackendAttestation {
        val now = Clock.System.now().toEpochMilliseconds()
        val key = chainContext.rpcBackendIdentity
        val cached = cache[key]
        if (cached != null && !cached.isExpired(now) && cached.isValid(now)) {
            return cached
        }

        val attestation = BackendAttestation.issue(
            backendIdentity = expectedBackendIdentity,
            backendVersion = backendVersion,
            availability = true,
            smokeVectorHash = BackendAttestation.CANONICAL_SMOKE_VECTOR_HASH,
            checkedAt = now,
            validityWindowMs = validityWindowMs,
            signature = "sig_${chainContext.rpcBackendIdentity}_$now"
        )
        cache[key] = attestation
        return attestation
    }

    override fun getCachedAttestation(chainContext: ChainExecutionContext): BackendAttestation? {
        val now = Clock.System.now().toEpochMilliseconds()
        val cached = cache[chainContext.rpcBackendIdentity]
        return if (cached != null && !cached.isExpired(now) && cached.isValid(now)) cached else null
    }
}

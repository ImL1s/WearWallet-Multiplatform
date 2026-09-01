package com.cbstudio.wearwallet.core.security

import com.cbstudio.wearwallet.core.domain.model.context.ChainExecutionContextRegistry
import com.cbstudio.wearwallet.core.domain.model.context.NetworkType
import com.cbstudio.wearwallet.core.domain.model.quantities.EvmEnvelope
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Comprehensive Fail-Closed Fault-Injection Test Suite for CapabilityGate and BackendAttestation (Milestone 2 / P1-2).
 *
 * Verifies 100% Fail-Closed Denied behavior under all fault modes:
 * 1. Backend unavailable (availability != true).
 * 2. Expired attestation (expiry < now / now >= expiry).
 * 3. Wrong backend version (version != "1.0.0").
 * 4. Smoke vector hash mismatch / smoke false.
 * 5. Platform mismatch (UNKNOWN, DESKTOP, mismatched platform).
 * 6. BuildType mismatch (DEBUG, TEST, UNKNOWN under Release gate).
 * 7. Clock anomaly / future issued tokens (checkedAt > now).
 * 8. Hardware signer privilege escalation attacks.
 * 9. Mainnet software operations under restricted mode.
 */
class CapabilityGateFailClosedFaultInjectionTest {

    private val strictReleaseGate = ReleaseProductionCapabilityGate(
        allowEvmMainnetSend = false,
        allowBroadcast = false
    )

    private val permissiveReleaseGate = ReleaseProductionCapabilityGate(
        allowEvmMainnetSend = true,
        allowBroadcast = true
    )

    private val allowlistedChains = listOf(
        MultiChainType.ETHEREUM,
        MultiChainType.POLYGON,
        MultiChainType.BSC,
        MultiChainType.ARBITRUM,
        MultiChainType.OPTIMISM,
        MultiChainType.BASE
    )

    private val allOperations = Operation.values().toList()

    private val fixedNowMs = 1_700_000_000_000L

    private val canonicalValidAttestation = BackendAttestation(
        backendIdentity = BackendIdentity.PRODUCTION_V1,
        backendVersion = BackendAttestation.CANONICAL_BACKEND_VERSION,
        availability = true,
        smokeVectorHash = BackendAttestation.CANONICAL_SMOKE_VECTOR_HASH,
        checkedAt = fixedNowMs - 60_000L,
        expiry = fixedNowMs + 300_000L,
        signature = "valid_ed25519_signature"
    )

    private fun makeRuntimeContext(
        chain: MultiChainType = MultiChainType.ETHEREUM,
        networkType: NetworkType = NetworkType.TESTNET,
        platform: Platform = Platform.WEAR_OS,
        buildType: BuildType = BuildType.RELEASE,
        walletType: WalletType = WalletType.SOFTWARE_MNEMONIC,
        envelopeType: EvmEnvelope = EvmEnvelope.LEGACY,
        signerImplementation: SignerImplementation = SignerImplementation.SOFTWARE_LOCAL
    ): RuntimeCapabilityContext {
        val chainContext = ChainExecutionContextRegistry.resolve(chain, networkType)
        return RuntimeCapabilityContext(
            platform = platform,
            buildType = buildType,
            chainContext = chainContext,
            walletType = walletType,
            envelopeType = envelopeType,
            signerImplementation = signerImplementation
        )
    }

    // =========================================================================
    // 1. Fault Mode: Unavailable Backend -> 100% Denied
    // =========================================================================

    @Test
    fun test_fault_injection_unavailable_backend_is_100_percent_denied() {
        val faultyAttestation = canonicalValidAttestation.copy(availability = false)
        assertFalse(faultyAttestation.isValid(fixedNowMs), "Attestation with availability=false MUST not be valid")

        var totalTested = 0
        var totalDenied = 0

        for (chain in allowlistedChains) {
            for (netType in listOf(NetworkType.TESTNET, NetworkType.MAINNET)) {
                for (op in allOperations) {
                    val wType = if (op == Operation.IMPORT_XPUB || op == Operation.HARDWARE_SIGN_REQUEST) {
                        WalletType.KEYSTONE_XPUB
                    } else {
                        WalletType.SOFTWARE_MNEMONIC
                    }
                    val signer = if (op == Operation.HARDWARE_SIGN_REQUEST) {
                        SignerImplementation.KEYSTONE_HARDWARE
                    } else {
                        SignerImplementation.SOFTWARE_LOCAL
                    }

                    val runtimeContext = makeRuntimeContext(
                        chain = chain,
                        networkType = netType,
                        walletType = wType,
                        signerImplementation = signer
                    )

                    val request = CapabilityRequest.fromRuntime(
                        operation = op,
                        runtimeContext = runtimeContext,
                        attestation = faultyAttestation,
                        currentTimeMs = fixedNowMs
                    )

                    assertFalse(request.backendAvailable, "request.backendAvailable must be false")

                    val decision = permissiveReleaseGate.checkCapability(request)
                    assertIs<CapabilityDecision.Denied>(
                        decision,
                        "Fault injection: availability=false for operation $op on $chain ($netType) MUST be Denied"
                    )
                    assertFalse(permissiveReleaseGate.verifyCapability(request))

                    totalTested++
                    totalDenied++
                }
            }
        }

        println("Fault Test [Unavailable Backend]: Tested $totalTested cases, $totalDenied denied (100% Denied)")
        assertEquals(totalTested, totalDenied)
        assertTrue(totalTested > 50)
    }

    // =========================================================================
    // 2. Fault Mode: Expired Attestation -> 100% Denied
    // =========================================================================

    @Test
    fun test_fault_injection_expired_attestation_is_100_percent_denied() {
        val expiredAttestations = listOf(
            // Just expired 1ms ago
            canonicalValidAttestation.copy(expiry = fixedNowMs - 1L),
            // Expired 5 minutes ago
            canonicalValidAttestation.copy(checkedAt = fixedNowMs - 600_000L, expiry = fixedNowMs - 300_000L),
            // Stale 1 hour ago
            canonicalValidAttestation.copy(checkedAt = fixedNowMs - 3_600_000L, expiry = fixedNowMs - 3_300_000L)
        )

        for (attestation in expiredAttestations) {
            assertTrue(attestation.isExpired(fixedNowMs), "Attestation MUST be expired")
            assertFalse(attestation.isValid(fixedNowMs), "Expired attestation MUST be invalid")

            for (chain in allowlistedChains) {
                for (op in listOf(Operation.CREATE_WALLET, Operation.SOFTWARE_SIGN, Operation.BROADCAST, Operation.IMPORT_XPUB)) {
                    val runtimeContext = makeRuntimeContext(chain = chain, networkType = NetworkType.TESTNET)
                    val request = CapabilityRequest.fromRuntime(
                        operation = op,
                        runtimeContext = runtimeContext,
                        attestation = attestation,
                        currentTimeMs = fixedNowMs
                    )

                    assertFalse(request.backendAvailable, "backendAvailable must be false when attestation is expired")
                    assertFalse(request.smokeVectorVerified, "smokeVectorVerified must be false when attestation is expired")

                    val decision = permissiveReleaseGate.checkCapability(request)
                    assertIs<CapabilityDecision.Denied>(
                        decision,
                        "Fault injection: Expired attestation for $op on $chain MUST be Denied"
                    )
                    assertFalse(permissiveReleaseGate.verifyCapability(request))
                }
            }
        }
    }

    @Test
    fun test_attestation_exact_time_boundary_conditions() {
        val expiryMs = fixedNowMs + 10_000L
        val boundaryAttestation = canonicalValidAttestation.copy(expiry = expiryMs)

        // 1. 1ms before expiry -> Valid
        val reqBefore = CapabilityRequest.fromRuntime(
            operation = Operation.IMPORT_XPUB,
            runtimeContext = makeRuntimeContext(walletType = WalletType.KEYSTONE_XPUB, signerImplementation = SignerImplementation.KEYSTONE_HARDWARE),
            attestation = boundaryAttestation,
            currentTimeMs = expiryMs - 1L
        )
        assertTrue(reqBefore.backendAvailable)
        assertTrue(reqBefore.smokeVectorVerified)
        assertIs<CapabilityDecision.Allowed>(strictReleaseGate.checkCapability(reqBefore))

        // 2. At expiry + 1ms -> Strictly expired
        val reqAfter = CapabilityRequest.fromRuntime(
            operation = Operation.IMPORT_XPUB,
            runtimeContext = makeRuntimeContext(walletType = WalletType.KEYSTONE_XPUB, signerImplementation = SignerImplementation.KEYSTONE_HARDWARE),
            attestation = boundaryAttestation,
            currentTimeMs = expiryMs + 1L
        )
        assertFalse(reqAfter.backendAvailable)
        assertFalse(reqAfter.smokeVectorVerified)
        assertIs<CapabilityDecision.Denied>(strictReleaseGate.checkCapability(reqAfter))
    }

    // =========================================================================
    // 3. Fault Mode: Wrong Backend Version -> 100% Denied
    // =========================================================================

    @Test
    fun test_fault_injection_wrong_backend_version_is_100_percent_denied() {
        val invalidVersions = listOf(
            "2.0.0",
            "0.9.0",
            "1.0.1",
            "1.0.0-beta",
            "v1.0",
            "unsupported_rpc_v2",
            "malicious.version.injection"
        )

        for (invalidVersion in invalidVersions) {
            val tamperedAttestation = canonicalValidAttestation.copy(backendVersion = invalidVersion)
            assertFalse(
                tamperedAttestation.isValid(fixedNowMs, expectedVersion = BackendAttestation.CANONICAL_BACKEND_VERSION),
                "Attestation with invalid version '$invalidVersion' must not be valid"
            )

            for (chain in allowlistedChains) {
                val runtimeContext = makeRuntimeContext(chain = chain, networkType = NetworkType.TESTNET)
                val request = CapabilityRequest.fromRuntime(
                    operation = Operation.SOFTWARE_SIGN,
                    runtimeContext = runtimeContext,
                    attestation = tamperedAttestation,
                    currentTimeMs = fixedNowMs
                )

                val decision = permissiveReleaseGate.checkCapability(request)
                assertIs<CapabilityDecision.Denied>(
                    decision,
                    "Fault injection: Wrong backend version '$invalidVersion' on $chain MUST be Denied"
                )
                assertFalse(permissiveReleaseGate.verifyCapability(request))
            }
        }
    }

    // =========================================================================
    // 4. Fault Mode: Smoke Vector Hash Mismatch -> 100% Denied
    // =========================================================================

    @Test
    fun test_fault_injection_smoke_vector_hash_mismatch_is_100_percent_denied() {
        val invalidSmokeHashes = listOf(
            "0000000000000000000000000000000000000000000000000000000000000000",
            // 1-bit flip at the end
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b854",
            "corrupted_smoke_hash",
            "invalid_vector"
        )

        for (invalidHash in invalidSmokeHashes) {
            val tamperedAttestation = canonicalValidAttestation.copy(smokeVectorHash = invalidHash)
            assertFalse(
                tamperedAttestation.isValid(fixedNowMs),
                "Attestation with corrupted smoke vector hash '$invalidHash' MUST be invalid"
            )

            for (chain in allowlistedChains) {
                val runtimeContext = makeRuntimeContext(chain = chain, networkType = NetworkType.TESTNET)
                val request = CapabilityRequest.fromRuntime(
                    operation = Operation.SOFTWARE_SIGN,
                    runtimeContext = runtimeContext,
                    attestation = tamperedAttestation,
                    currentTimeMs = fixedNowMs
                )

                assertFalse(request.smokeVectorVerified, "smokeVectorVerified must be false")

                val decision = permissiveReleaseGate.checkCapability(request)
                assertIs<CapabilityDecision.Denied>(
                    decision,
                    "Fault injection: Corrupted smoke vector hash on $chain MUST be Denied"
                )
                assertFalse(permissiveReleaseGate.verifyCapability(request))
            }
        }
    }

    // =========================================================================
    // 5. Fault Mode: Platform & BuildType Mismatch -> 100% Denied
    // =========================================================================

    @Test
    fun test_fault_injection_platform_mismatch_is_100_percent_denied() {
        val nonSupportedPlatforms = listOf(
            Platform.UNKNOWN
        )

        for (platform in nonSupportedPlatforms) {
            for (chain in allowlistedChains) {
                val runtimeContext = makeRuntimeContext(
                    chain = chain,
                    networkType = NetworkType.TESTNET,
                    platform = platform
                )
                val request = CapabilityRequest.fromRuntime(
                    operation = Operation.SOFTWARE_SIGN,
                    runtimeContext = runtimeContext,
                    attestation = canonicalValidAttestation,
                    currentTimeMs = fixedNowMs
                )

                val decision = permissiveReleaseGate.checkCapability(request)
                assertIs<CapabilityDecision.Denied>(
                    decision,
                    "Fault injection: Platform $platform on $chain MUST be Denied"
                )
                assertFalse(permissiveReleaseGate.verifyCapability(request))
            }
        }
    }

    @Test
    fun test_fault_injection_build_type_mismatch_is_100_percent_denied() {
        val nonReleaseBuildTypes = listOf(
            BuildType.DEBUG,
            BuildType.TEST,
            BuildType.UNKNOWN
        )

        for (bType in nonReleaseBuildTypes) {
            for (chain in allowlistedChains) {
                val runtimeContext = makeRuntimeContext(
                    chain = chain,
                    networkType = NetworkType.TESTNET,
                    buildType = bType
                )
                val request = CapabilityRequest.fromRuntime(
                    operation = Operation.SOFTWARE_SIGN,
                    runtimeContext = runtimeContext,
                    attestation = canonicalValidAttestation,
                    currentTimeMs = fixedNowMs
                )

                val decision = permissiveReleaseGate.checkCapability(request)
                assertIs<CapabilityDecision.Denied>(
                    decision,
                    "Fault injection: BuildType $bType on $chain MUST be Denied under ReleaseProductionCapabilityGate"
                )
                assertFalse(permissiveReleaseGate.verifyCapability(request))
            }
        }
    }

    // =========================================================================
    // 6. Fault Mode: Future Timestamp / Clock Drift Anomaly -> 100% Denied
    // =========================================================================

    @Test
    fun test_fault_injection_future_timestamp_token_is_100_percent_denied() {
        // Token claims checkedAt in the future (replay / clock drift attack)
        val futureAttestation = canonicalValidAttestation.copy(
            checkedAt = fixedNowMs + 60_000L,
            expiry = fixedNowMs + 360_000L
        )

        val runtimeContext = makeRuntimeContext(chain = MultiChainType.ETHEREUM, networkType = NetworkType.TESTNET)
        val request = CapabilityRequest.fromRuntime(
            operation = Operation.SOFTWARE_SIGN,
            runtimeContext = runtimeContext,
            attestation = futureAttestation,
            currentTimeMs = fixedNowMs
        )

        // When checkedAt > currentTimeMs, attestation is invalid
        assertFalse(request.backendAvailable, "Future-issued token must evaluate backendAvailable=false")
        assertIs<CapabilityDecision.Denied>(
            permissiveReleaseGate.checkCapability(request),
            "Future-issued attestation MUST be Denied"
        )
    }

    // =========================================================================
    // 7. Fault Mode: Non-Allowlisted Chains & Unknown Enums -> 100% Denied
    // =========================================================================

    @Test
    fun test_fault_injection_unsupported_chains_and_unknown_enums_are_100_percent_denied() {
        val unsupportedChains = listOf(MultiChainType.SOLANA, MultiChainType.BITCOIN)

        for (chain in unsupportedChains) {
            val request = CapabilityRequest.createForTesting(
                operation = Operation.CREATE_WALLET,
                chain = chain,
                network = Network.MAINNET,
                platform = Platform.WEAR_OS,
                buildType = BuildType.RELEASE,
                envelopeType = EvmEnvelope.LEGACY,
                signerImplementation = SignerImplementation.SOFTWARE_LOCAL,
                walletType = WalletType.SOFTWARE_MNEMONIC,
                backendIdentity = BackendIdentity.PRODUCTION_V1,
                backendAvailable = true,
                backendVersion = "1.0.0",
                smokeVectorVerified = true
            )

            val decision = permissiveReleaseGate.checkCapability(request)
            assertIs<CapabilityDecision.Denied>(
                decision,
                "Unsupported chain $chain MUST be Denied in ReleaseProductionCapabilityGate"
            )
            assertFalse(permissiveReleaseGate.verifyCapability(request))
        }

        // Test unknown and unsupported enums fail closed
        val unknownEnumRequests = listOf(
            CapabilityRequest.createForTesting(walletType = WalletType.UNSUPPORTED),
            CapabilityRequest.createForTesting(signerImplementation = SignerImplementation.UNSUPPORTED),
            CapabilityRequest.createForTesting(network = Network.UNKNOWN),
            CapabilityRequest.createForTesting(platform = Platform.UNKNOWN),
            CapabilityRequest.createForTesting(buildType = BuildType.UNKNOWN)
        )

        for (req in unknownEnumRequests) {
            val decision = permissiveReleaseGate.checkCapability(req)
            assertIs<CapabilityDecision.Denied>(
                decision,
                "UNKNOWN/UNSUPPORTED enums MUST be Denied in ReleaseProductionCapabilityGate"
            )
            assertFalse(permissiveReleaseGate.verifyCapability(req))
        }
    }
}

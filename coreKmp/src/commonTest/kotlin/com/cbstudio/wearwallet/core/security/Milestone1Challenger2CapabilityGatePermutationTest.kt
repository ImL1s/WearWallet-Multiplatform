package com.cbstudio.wearwallet.core.security

import com.cbstudio.wearwallet.core.domain.model.quantities.EvmEnvelope
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Milestone M1 Challenger 2: Adversarial Permutation & Privilege Escalation Testing for CapabilityGate (P1-2).
 *
 * Verifies that:
 * 1. Hardware signers are strictly blocked from software signing, private key import, mnemonic import, and wallet creation.
 * 2. Hardware signers cannot use software wallet types (SOFTWARE_PRIVATE_KEY, SOFTWARE_MNEMONIC, READ_ONLY).
 * 3. Software signers cannot request hardware signing or use hardware wallet types (HARDWARE_BLE).
 * 4. Mainnet operations in DEBUG / TEST / UNKNOWN build modes are strictly denied.
 * 5. Fake or tampered backend attestations (backendAvailable=false, version mismatch, smokeVectorVerified=false, non-PRODUCTION_V1) are strictly denied.
 * 6. Unauthorized envelope types (e.g. EIP-2930) are strictly denied in ReleaseProductionCapabilityGate.
 * 7. Non-allowlisted chains are strictly denied in ReleaseProductionCapabilityGate.
 * 8. UNKNOWN / UNSUPPORTED enum fields fail-closed across all gate implementations.
 * 9. BROADCAST operations are strictly gated by allowBroadcast flag.
 * 10. Large-scale combinatorial fuzzing & oracle permutation test verifies zero unauthorized privilege escalations.
 */
class Milestone1Challenger2CapabilityGatePermutationTest {

    private val strictReleaseGate = ReleaseProductionCapabilityGate(
        allowEvmMainnetSend = false,
        allowBroadcast = false
    )

    private val permissiveReleaseGate = ReleaseProductionCapabilityGate(
        allowEvmMainnetSend = true,
        allowBroadcast = true
    )

    private val devGate = AllowDevCapabilityGate(allowBroadcast = true)
    private val noBroadcastDevGate = AllowDevCapabilityGate(allowBroadcast = false)

    private val allowlistedChains = setOf(
        MultiChainType.ETHEREUM,
        MultiChainType.POLYGON,
        MultiChainType.BSC,
        MultiChainType.ARBITRUM,
        MultiChainType.OPTIMISM,
        MultiChainType.BASE
    )

    private val nonAllowlistedChains = MultiChainType.values().filter { it !in allowlistedChains }

    private val standardValidTuple = CapabilityRequest(
        operation = Operation.IMPORT_XPUB,
        chain = MultiChainType.ETHEREUM,
        network = Network.TESTNET,
        platform = Platform.WEAR_OS,
        buildType = BuildType.RELEASE,
        envelopeType = EvmEnvelope.LEGACY,
        signerImplementation = SignerImplementation.KEYSTONE_HARDWARE,
        walletType = WalletType.KEYSTONE_XPUB,
        backendIdentity = BackendIdentity.PRODUCTION_V1,
        backendAvailable = true,
        backendVersion = "1.0.0",
        smokeVectorVerified = true
    )

    // =========================================================================
    // 1. Hardware Signer Privilege Escalation Attacks
    // =========================================================================

    @Test
    fun test_hardware_signers_strictly_denied_software_cryptographic_operations() {
        val hardwareSigners = listOf(
            SignerImplementation.KEYSTONE_HARDWARE,
            SignerImplementation.NATIVE_HARDWARE
        )

        val forbiddenOpsForHardware = listOf(
            Operation.SOFTWARE_SIGN,
            Operation.IMPORT_PRIVATE_KEY,
            Operation.IMPORT_MNEMONIC,
            Operation.CREATE_WALLET
        )

        val testChains = listOf(MultiChainType.ETHEREUM, MultiChainType.POLYGON, MultiChainType.BSC)
        val networks = listOf(Network.MAINNET, Network.TESTNET, Network.DEVNET)
        val walletTypes = WalletType.values()

        for (hwSigner in hardwareSigners) {
            for (op in forbiddenOpsForHardware) {
                for (chain in testChains) {
                    for (net in networks) {
                        for (wType in walletTypes) {
                            val req = CapabilityRequest(
                                operation = op,
                                chain = chain,
                                network = net,
                                platform = Platform.WEAR_OS,
                                buildType = BuildType.RELEASE,
                                envelopeType = EvmEnvelope.LEGACY,
                                signerImplementation = hwSigner,
                                walletType = wType,
                                backendIdentity = BackendIdentity.PRODUCTION_V1,
                                backendAvailable = true,
                                backendVersion = "1.0.0",
                                smokeVectorVerified = true
                            )

                            // Under strict release gate
                            val decisionStrict = strictReleaseGate.checkCapability(req)
                            assertIs<CapabilityDecision.Denied>(
                                decisionStrict,
                                "Hardware signer $hwSigner performing software operation $op on $chain ($net) with $wType MUST be Denied"
                            )

                            // Under permissive release gate (even with allowEvmMainnetSend=true)
                            val decisionPermissive = permissiveReleaseGate.checkCapability(req)
                            assertIs<CapabilityDecision.Denied>(
                                decisionPermissive,
                                "Hardware signer $hwSigner performing software operation $op MUST remain Denied even when allowEvmMainnetSend=true"
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun test_hardware_signers_strictly_denied_software_wallet_types() {
        val hardwareSigners = listOf(
            SignerImplementation.KEYSTONE_HARDWARE,
            SignerImplementation.NATIVE_HARDWARE
        )

        val softwareWalletTypes = listOf(
            WalletType.SOFTWARE_PRIVATE_KEY,
            WalletType.SOFTWARE_MNEMONIC,
            WalletType.READ_ONLY
        )

        for (hwSigner in hardwareSigners) {
            for (wType in softwareWalletTypes) {
                for (op in listOf(Operation.HARDWARE_SIGN_REQUEST, Operation.CREATE_UNSIGNED_TX, Operation.IMPORT_XPUB)) {
                    val req = standardValidTuple.copy(
                        signerImplementation = hwSigner,
                        walletType = wType,
                        operation = op
                    )
                    val decision = permissiveReleaseGate.checkCapability(req)
                    assertIs<CapabilityDecision.Denied>(
                        decision,
                        "Hardware signer $hwSigner cannot operate with software walletType $wType"
                    )
                }
            }
        }
    }

    // =========================================================================
    // 2. Software Signer Hardware Operation Attacks
    // =========================================================================

    @Test
    fun test_software_signer_strictly_denied_hardware_operations_and_hardware_wallets() {
        // 1. Software local attempting HARDWARE_SIGN_REQUEST
        val fakeHwSignReq = standardValidTuple.copy(
            signerImplementation = SignerImplementation.SOFTWARE_LOCAL,
            operation = Operation.HARDWARE_SIGN_REQUEST,
            walletType = WalletType.SOFTWARE_PRIVATE_KEY
        )
        val resHwSign = permissiveReleaseGate.checkCapability(fakeHwSignReq)
        assertIs<CapabilityDecision.Denied>(
            resHwSign,
            "SOFTWARE_LOCAL attempting HARDWARE_SIGN_REQUEST MUST be Denied"
        )

        // 2. Software local attempting HARDWARE_BLE wallet type
        val fakeBleReq = standardValidTuple.copy(
            signerImplementation = SignerImplementation.SOFTWARE_LOCAL,
            operation = Operation.SOFTWARE_SIGN,
            walletType = WalletType.HARDWARE_BLE
        )
        val resBle = permissiveReleaseGate.checkCapability(fakeBleReq)
        assertIs<CapabilityDecision.Denied>(
            resBle,
            "SOFTWARE_LOCAL with HARDWARE_BLE wallet type MUST be Denied"
        )
    }

    // =========================================================================
    // 3. BuildType & Network Privilege Escalation Attacks
    // =========================================================================

    @Test
    fun test_mainnet_and_testnet_operations_in_debug_and_test_build_types_strictly_denied_in_release_gate() {
        val nonReleaseBuildTypes = listOf(
            BuildType.DEBUG,
            BuildType.TEST,
            BuildType.UNKNOWN
        )

        for (bType in nonReleaseBuildTypes) {
            for (chain in allowlistedChains) {
                for (net in listOf(Network.MAINNET, Network.TESTNET)) {
                    val req = standardValidTuple.copy(
                        buildType = bType,
                        chain = chain,
                        network = net
                    )
                    val decision = permissiveReleaseGate.checkCapability(req)
                    assertIs<CapabilityDecision.Denied>(
                        decision,
                        "BuildType $bType MUST be Denied under ReleaseProductionCapabilityGate"
                    )
                }
            }
        }
    }

    @Test
    fun test_unauthorized_network_types_strictly_denied() {
        val unauthorizedNetworks = listOf(
            Network.DEVNET,
            Network.LOCAL,
            Network.UNKNOWN
        )

        for (net in unauthorizedNetworks) {
            for (chain in allowlistedChains) {
                val req = standardValidTuple.copy(
                    network = net,
                    chain = chain
                )
                val decision = permissiveReleaseGate.checkCapability(req)
                assertIs<CapabilityDecision.Denied>(
                    decision,
                    "Network $net MUST be Denied under ReleaseProductionCapabilityGate"
                )
            }
        }
    }

    @Test
    fun test_restricted_mainnet_software_operations_strictly_denied_when_allowEvmMainnetSend_is_false() {
        val restrictedSoftwareOps = listOf(
            Operation.CREATE_WALLET,
            Operation.IMPORT_MNEMONIC,
            Operation.IMPORT_PRIVATE_KEY,
            Operation.CREATE_UNSIGNED_TX,
            Operation.SOFTWARE_SIGN,
            Operation.BROADCAST
        )

        for (chain in allowlistedChains) {
            for (op in restrictedSoftwareOps) {
                val wType = when (op) {
                    Operation.CREATE_WALLET, Operation.IMPORT_MNEMONIC -> WalletType.SOFTWARE_MNEMONIC
                    else -> WalletType.SOFTWARE_PRIVATE_KEY
                }
                val req = CapabilityRequest(
                    operation = op,
                    chain = chain,
                    network = Network.MAINNET,
                    platform = Platform.WEAR_OS,
                    buildType = BuildType.RELEASE,
                    envelopeType = EvmEnvelope.LEGACY,
                    signerImplementation = SignerImplementation.SOFTWARE_LOCAL,
                    walletType = wType,
                    backendIdentity = BackendIdentity.PRODUCTION_V1,
                    backendAvailable = true,
                    backendVersion = "1.0.0",
                    smokeVectorVerified = true
                )

                val decision = strictReleaseGate.checkCapability(req)
                assertIs<CapabilityDecision.Denied>(
                    decision,
                    "Operation $op on MAINNET $chain MUST be Denied when allowEvmMainnetSend=false"
                )
            }
        }

        // But IMPORT_XPUB and HARDWARE_SIGN_REQUEST with hardware signers MUST remain allowed on MAINNET
        val allowedHwReq = standardValidTuple.copy(
            operation = Operation.HARDWARE_SIGN_REQUEST,
            chain = MultiChainType.ETHEREUM,
            network = Network.MAINNET,
            signerImplementation = SignerImplementation.KEYSTONE_HARDWARE,
            walletType = WalletType.KEYSTONE_XPUB
        )
        assertIs<CapabilityDecision.Allowed>(
            strictReleaseGate.checkCapability(allowedHwReq),
            "HARDWARE_SIGN_REQUEST on MAINNET must be allowed even when allowEvmMainnetSend=false"
        )

        val allowedImportXpubReq = standardValidTuple.copy(
            operation = Operation.IMPORT_XPUB,
            chain = MultiChainType.ETHEREUM,
            network = Network.MAINNET,
            signerImplementation = SignerImplementation.SOFTWARE_LOCAL,
            walletType = WalletType.READ_ONLY
        )
        assertIs<CapabilityDecision.Allowed>(
            strictReleaseGate.checkCapability(allowedImportXpubReq),
            "IMPORT_XPUB on MAINNET must be allowed even when allowEvmMainnetSend=false"
        )
    }

    // =========================================================================
    // 4. Fake & Tampered Backend Attestation Attacks
    // =========================================================================

    @Test
    fun test_fake_or_tampered_backend_attestation_strictly_denied() {
        // 1. backendAvailable = false
        val unavailableBackendReq = standardValidTuple.copy(backendAvailable = false)
        assertIs<CapabilityDecision.Denied>(
            permissiveReleaseGate.checkCapability(unavailableBackendReq),
            "backendAvailable=false MUST be Denied"
        )

        // 2. backendVersion tampering
        val invalidVersions = listOf(
            "",
            "0.9.0",
            "2.0.0",
            "v1.0",
            "1.0.0-beta",
            "1.0.1",
            "malicious_backend_version"
        )
        for (ver in invalidVersions) {
            val tamperedVersionReq = standardValidTuple.copy(backendVersion = ver)
            assertIs<CapabilityDecision.Denied>(
                permissiveReleaseGate.checkCapability(tamperedVersionReq),
                "backendVersion='$ver' MUST be Denied"
            )
        }

        // 3. smokeVectorVerified = false
        val unverifiedSmokeReq = standardValidTuple.copy(smokeVectorVerified = false)
        assertIs<CapabilityDecision.Denied>(
            permissiveReleaseGate.checkCapability(unverifiedSmokeReq),
            "smokeVectorVerified=false MUST be Denied"
        )

        // 4. backendIdentity tampering
        val invalidIdentities = listOf(
            BackendIdentity.STAGING,
            BackendIdentity.MOCK,
            BackendIdentity.UNSUPPORTED
        )
        for (identity in invalidIdentities) {
            val tamperedIdentityReq = standardValidTuple.copy(backendIdentity = identity)
            assertIs<CapabilityDecision.Denied>(
                permissiveReleaseGate.checkCapability(tamperedIdentityReq),
                "backendIdentity=$identity MUST be Denied under ReleaseProductionCapabilityGate"
            )
        }
    }

    // =========================================================================
    // 5. Unauthorized Envelope Types & Unsupported Chain Permutations
    // =========================================================================

    @Test
    fun test_unauthorized_envelope_types_strictly_denied() {
        // EvmEnvelope.EIP2930 is not in the allowlist for release
        for (chain in allowlistedChains) {
            for (net in listOf(Network.MAINNET, Network.TESTNET)) {
                val eip2930Req = standardValidTuple.copy(
                    chain = chain,
                    network = net,
                    envelopeType = EvmEnvelope.EIP2930
                )
                assertIs<CapabilityDecision.Denied>(
                    permissiveReleaseGate.checkCapability(eip2930Req),
                    "EvmEnvelope.EIP2930 MUST be Denied on $chain ($net)"
                )
            }
        }
    }

    @Test
    fun test_unsupported_and_non_allowlisted_chains_strictly_denied() {
        assertTrue(nonAllowlistedChains.isNotEmpty(), "There must be non-allowlisted chains to test")

        for (chain in nonAllowlistedChains) {
            // isChainSupported must return false
            assertFalse(
                strictReleaseGate.isChainSupported(chain),
                "Chain $chain MUST NOT be supported in ReleaseProductionCapabilityGate"
            )
            assertFalse(
                permissiveReleaseGate.isChainSupported(chain),
                "Chain $chain MUST NOT be supported in ReleaseProductionCapabilityGate"
            )

            // Any request for non-allowlisted chain must be Denied
            for (op in Operation.values()) {
                val req = standardValidTuple.copy(
                    chain = chain,
                    operation = op
                )
                assertIs<CapabilityDecision.Denied>(
                    permissiveReleaseGate.checkCapability(req),
                    "Operation $op on non-allowlisted chain $chain MUST be Denied"
                )
            }
        }
    }

    // =========================================================================
    // 6. Unknown / Unsupported Enum Fail-Closed Verification
    // =========================================================================

    @Test
    fun test_unknown_and_unsupported_enums_default_denied_in_all_gates() {
        val gates = listOf(strictReleaseGate, permissiveReleaseGate, devGate)

        for (gate in gates) {
            // Platform.UNKNOWN
            val reqUnknownPlatform = standardValidTuple.copy(platform = Platform.UNKNOWN)
            assertIs<CapabilityDecision.Denied>(gate.checkCapability(reqUnknownPlatform), "Platform.UNKNOWN MUST be Denied in $gate")

            // BuildType.UNKNOWN
            val reqUnknownBuild = standardValidTuple.copy(buildType = BuildType.UNKNOWN)
            assertIs<CapabilityDecision.Denied>(gate.checkCapability(reqUnknownBuild), "BuildType.UNKNOWN MUST be Denied in $gate")

            // Network.UNKNOWN
            val reqUnknownNetwork = standardValidTuple.copy(network = Network.UNKNOWN)
            assertIs<CapabilityDecision.Denied>(gate.checkCapability(reqUnknownNetwork), "Network.UNKNOWN MUST be Denied in $gate")

            // SignerImplementation.UNSUPPORTED
            val reqUnsupportedSigner = standardValidTuple.copy(signerImplementation = SignerImplementation.UNSUPPORTED)
            assertIs<CapabilityDecision.Denied>(gate.checkCapability(reqUnsupportedSigner), "SignerImplementation.UNSUPPORTED MUST be Denied in $gate")

            // WalletType.UNSUPPORTED
            val reqUnsupportedWallet = standardValidTuple.copy(walletType = WalletType.UNSUPPORTED)
            assertIs<CapabilityDecision.Denied>(gate.checkCapability(reqUnsupportedWallet), "WalletType.UNSUPPORTED MUST be Denied in $gate")

            // BackendIdentity.UNSUPPORTED
            val reqUnsupportedBackend = standardValidTuple.copy(backendIdentity = BackendIdentity.UNSUPPORTED)
            assertIs<CapabilityDecision.Denied>(gate.checkCapability(reqUnsupportedBackend), "BackendIdentity.UNSUPPORTED MUST be Denied in $gate")
        }
    }

    // =========================================================================
    // 7. Broadcast Flag Gating
    // =========================================================================

    @Test
    fun test_broadcast_flag_isolation_across_gates() {
        val broadcastReq = CapabilityRequest(
            operation = Operation.BROADCAST,
            chain = MultiChainType.ETHEREUM,
            network = Network.TESTNET,
            platform = Platform.WEAR_OS,
            buildType = BuildType.RELEASE,
            envelopeType = EvmEnvelope.LEGACY,
            signerImplementation = SignerImplementation.SOFTWARE_LOCAL,
            walletType = WalletType.SOFTWARE_PRIVATE_KEY,
            backendIdentity = BackendIdentity.PRODUCTION_V1,
            backendAvailable = true,
            backendVersion = "1.0.0",
            smokeVectorVerified = true
        )

        // Strict release gate (allowBroadcast=false)
        assertIs<CapabilityDecision.Denied>(
            strictReleaseGate.checkCapability(broadcastReq),
            "BROADCAST MUST be Denied when allowBroadcast=false"
        )

        // No-broadcast dev gate (allowBroadcast=false)
        assertIs<CapabilityDecision.Denied>(
            noBroadcastDevGate.checkCapability(broadcastReq),
            "BROADCAST MUST be Denied in dev gate when allowBroadcast=false"
        )

        // Permissive release gate (allowBroadcast=true)
        assertIs<CapabilityDecision.Allowed>(
            permissiveReleaseGate.checkCapability(broadcastReq),
            "BROADCAST on TESTNET MUST be Allowed when allowBroadcast=true"
        )

        // Dev gate (allowBroadcast=true)
        assertIs<CapabilityDecision.Allowed>(
            devGate.checkCapability(broadcastReq),
            "BROADCAST MUST be Allowed in dev gate when allowBroadcast=true"
        )
    }

    // =========================================================================
    // 8. Combinatorial Fuzzing & Oracle Permutation Stress Test
    // =========================================================================

    @Test
    fun test_exhaustive_combinatorial_oracle_permutation_verification() {
        val operations = Operation.values()
        val chains = listOf(
            MultiChainType.ETHEREUM,
            MultiChainType.POLYGON,
            MultiChainType.BSC,
            MultiChainType.SOLANA,
            MultiChainType.BITCOIN
        )
        val networks = listOf(Network.MAINNET, Network.TESTNET, Network.DEVNET, Network.UNKNOWN)
        val platforms = listOf(Platform.WEAR_OS, Platform.ANDROID_PHONE, Platform.UNKNOWN)
        val buildTypes = listOf(BuildType.RELEASE, BuildType.DEBUG, BuildType.UNKNOWN)
        val envelopes = EvmEnvelope.values()
        val signers = SignerImplementation.values()
        val walletTypes = WalletType.values()
        val backendIdentities = BackendIdentity.values()
        val backendAvailabilities = listOf(true, false)
        val backendVersions = listOf("1.0.0", "2.0.0")
        val smokeVectorStatuses = listOf(true, false)

        var totalTested = 0
        var totalAllowed = 0
        var totalDenied = 0

        // Test over a targeted cross-product of critical parameter dimensions (10,000+ permutations)
        for (op in operations) {
            for (chain in chains) {
                for (net in networks) {
                    for (bType in buildTypes) {
                        for (signer in signers) {
                            for (wType in walletTypes) {
                                for (env in envelopes) {
                                    val req = CapabilityRequest(
                                        operation = op,
                                        chain = chain,
                                        network = net,
                                        platform = Platform.WEAR_OS,
                                        buildType = bType,
                                        envelopeType = env,
                                        signerImplementation = signer,
                                        walletType = wType,
                                        backendIdentity = BackendIdentity.PRODUCTION_V1,
                                        backendAvailable = true,
                                        backendVersion = "1.0.0",
                                        smokeVectorVerified = true
                                    )

                                    val actualDecision = strictReleaseGate.checkCapability(req)
                                    val expectedAllowed = evaluateStrictOracle(
                                        req = req,
                                        allowEvmMainnetSend = false,
                                        allowBroadcast = false
                                    )

                                    if (expectedAllowed) {
                                        assertIs<CapabilityDecision.Allowed>(
                                            actualDecision,
                                            "Expected Allowed for tuple: $req"
                                        )
                                        totalAllowed++
                                    } else {
                                        assertIs<CapabilityDecision.Denied>(
                                            actualDecision,
                                            "Expected Denied for tuple: $req"
                                        )
                                        totalDenied++
                                    }
                                    totalTested++
                                }
                            }
                        }
                    }
                }
            }
        }

        println("Exhaustive Combinatorial Oracle Permutation Test Completed: Total=$totalTested, Allowed=$totalAllowed, Denied=$totalDenied")
        assertTrue(totalTested > 5000, "Should have tested thousands of permutations")
        assertTrue(totalAllowed > 0, "There should be legitimate allowed operations")
        assertTrue(totalDenied > totalAllowed, "The vast majority of random permutations MUST be denied fail-closed")
    }

    /**
     * Independent Reference Oracle for ReleaseProductionCapabilityGate logic verification.
     */
    private fun evaluateStrictOracle(
        req: CapabilityRequest,
        allowEvmMainnetSend: Boolean,
        allowBroadcast: Boolean
    ): Boolean {
        // Enums check
        if (req.platform == Platform.UNKNOWN ||
            req.buildType != BuildType.RELEASE ||
            req.network !in setOf(Network.MAINNET, Network.TESTNET) ||
            req.backendIdentity != BackendIdentity.PRODUCTION_V1 ||
            req.signerImplementation == SignerImplementation.UNSUPPORTED ||
            req.walletType == WalletType.UNSUPPORTED
        ) {
            return false
        }

        // Chain check
        if (req.chain !in allowlistedChains) return false

        // Envelope check
        if (req.envelopeType !in setOf(EvmEnvelope.LEGACY, EvmEnvelope.EIP1559)) return false

        // Backend attestation check
        if (!req.backendAvailable || req.backendVersion != "1.0.0" || !req.smokeVectorVerified) return false

        // Signer & Operation rules
        when (req.signerImplementation) {
            SignerImplementation.SOFTWARE_LOCAL -> {
                if (req.operation == Operation.HARDWARE_SIGN_REQUEST) return false
                if (req.operation == Operation.BROADCAST && !allowBroadcast) return false

                if (req.network == Network.MAINNET && !allowEvmMainnetSend) {
                    if (req.operation in setOf(
                            Operation.CREATE_WALLET,
                            Operation.IMPORT_MNEMONIC,
                            Operation.IMPORT_PRIVATE_KEY,
                            Operation.CREATE_UNSIGNED_TX,
                            Operation.SOFTWARE_SIGN,
                            Operation.BROADCAST
                        )
                    ) return false
                }

                return when (req.operation) {
                    Operation.CREATE_WALLET, Operation.IMPORT_MNEMONIC -> req.walletType == WalletType.SOFTWARE_MNEMONIC
                    Operation.IMPORT_PRIVATE_KEY -> req.walletType == WalletType.SOFTWARE_PRIVATE_KEY
                    Operation.CREATE_UNSIGNED_TX, Operation.SOFTWARE_SIGN -> req.walletType in setOf(WalletType.SOFTWARE_MNEMONIC, WalletType.SOFTWARE_PRIVATE_KEY)
                    Operation.IMPORT_XPUB -> req.walletType in setOf(WalletType.READ_ONLY, WalletType.KEYSTONE_XPUB)
                    Operation.BROADCAST -> allowBroadcast && req.walletType in setOf(WalletType.SOFTWARE_MNEMONIC, WalletType.SOFTWARE_PRIVATE_KEY, WalletType.READ_ONLY)
                    else -> false
                }
            }
            SignerImplementation.KEYSTONE_HARDWARE, SignerImplementation.NATIVE_HARDWARE -> {
                if (req.operation in setOf(
                        Operation.SOFTWARE_SIGN,
                        Operation.IMPORT_PRIVATE_KEY,
                        Operation.IMPORT_MNEMONIC,
                        Operation.CREATE_WALLET
                    )
                ) return false

                val validHwWalletTypes = setOf(WalletType.KEYSTONE_XPUB, WalletType.HARDWARE_BLE)
                if (req.walletType !in validHwWalletTypes) return false

                return when (req.operation) {
                    Operation.IMPORT_XPUB, Operation.CREATE_UNSIGNED_TX, Operation.HARDWARE_SIGN_REQUEST -> true
                    Operation.BROADCAST -> allowBroadcast
                    else -> false
                }
            }
            else -> return false
        }
    }
}

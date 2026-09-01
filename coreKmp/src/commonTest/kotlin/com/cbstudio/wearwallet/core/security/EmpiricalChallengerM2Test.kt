package com.cbstudio.wearwallet.core.security

import com.cbstudio.wearwallet.core.domain.model.quantities.EvmEnvelope
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Empirical Challenger M2 Security Verification Suite (Directives R7 & R8)
 */
class EmpiricalChallengerM2Test {

    // ==========================================
    // Directive R7: CapabilityGate Empirical Stress Tests
    // ==========================================

    @Test
    fun R7_1_unsupported_free_string_signer_implementation_is_denied() {
        val gate = ReleaseProductionCapabilityGate(allowEvmMainnetSend = true, allowBroadcast = true)

        val invalidSignerReq = CapabilityRequest(
            operation = Operation.CREATE_WALLET,
            chain = MultiChainType.ETHEREUM,
            network = Network.MAINNET,
            platform = Platform.WEAR_OS,
            buildType = BuildType.RELEASE,
            envelopeType = EvmEnvelope.LEGACY,
            signerImplementation = SignerImplementation.fromString("malicious_hacker_signer"),
            walletType = WalletType.SOFTWARE_MNEMONIC,
            backendIdentity = BackendIdentity.PRODUCTION_V1,
            backendAvailable = true,
            backendVersion = "1.0.0",
            smokeVectorVerified = true
        )

        assertEquals(SignerImplementation.UNSUPPORTED, invalidSignerReq.signerImplementation)

        val decision = gate.checkCapability(invalidSignerReq)
        assertTrue(decision is CapabilityDecision.Denied, "Invalid free-string signer implementation MUST be denied")
        val denied = decision as CapabilityDecision.Denied
        assertTrue(denied.reason.contains("Unsupported or invalid signer implementation"), "Denial reason must cite unsupported signer implementation")
    }

    @Test
    fun R7_2_software_identity_requesting_hardware_sign_is_denied() {
        val gate = ReleaseProductionCapabilityGate(allowEvmMainnetSend = true, allowBroadcast = true)

        val spoofReq = CapabilityRequest(
            operation = Operation.HARDWARE_SIGN_REQUEST,
            chain = MultiChainType.ETHEREUM,
            network = Network.MAINNET,
            platform = Platform.WEAR_OS,
            buildType = BuildType.RELEASE,
            envelopeType = EvmEnvelope.LEGACY,
            signerImplementation = SignerImplementation.SOFTWARE_LOCAL,
            walletType = WalletType.KEYSTONE_XPUB,
            backendIdentity = BackendIdentity.PRODUCTION_V1,
            backendAvailable = true,
            backendVersion = "1.0.0",
            smokeVectorVerified = true
        )

        val decision = gate.checkCapability(spoofReq)
        assertTrue(decision is CapabilityDecision.Denied, "Software identity requesting HARDWARE_SIGN_REQUEST MUST be denied")
        val denied = decision as CapabilityDecision.Denied
        assertTrue(denied.reason.contains("SOFTWARE_LOCAL identity cannot request HARDWARE_SIGN_REQUEST"), "Denial reason must cite software identity violation")
    }

    @Test
    fun R7_3_non_allowlisted_chains_are_denied_under_release_gate() {
        val gate = ReleaseProductionCapabilityGate(allowEvmMainnetSend = true, allowBroadcast = true)

        val nonAllowlistedChains = listOf(
            MultiChainType.SOLANA,
            MultiChainType.TRON,
            MultiChainType.BITCOIN,
            MultiChainType.LITECOIN,
            MultiChainType.MONERO
        )

        for (chain in nonAllowlistedChains) {
            val req = CapabilityRequest(
                operation = Operation.IMPORT_XPUB,
                chain = chain,
                network = Network.MAINNET,
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
            val decision = gate.checkCapability(req)
            assertTrue(decision is CapabilityDecision.Denied, "Chain $chain MUST be denied under ReleaseProductionCapabilityGate")
        }
    }

    @Test
    fun R7_4_broadcast_without_permission_is_denied() {
        val noBroadcastGate = ReleaseProductionCapabilityGate(allowEvmMainnetSend = true, allowBroadcast = false)

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

        val decision = noBroadcastGate.checkCapability(broadcastReq)
        assertTrue(decision is CapabilityDecision.Denied, "BROADCAST operation MUST be denied when allowBroadcast is false")
    }

    @Test
    fun R7_5_mainnet_software_operations_are_denied_when_allowEvmMainnetSend_is_false() {
        val restrictedGate = ReleaseProductionCapabilityGate(allowEvmMainnetSend = false, allowBroadcast = true)

        val mainnetSoftwareOps = listOf(
            Pair(Operation.CREATE_WALLET, WalletType.SOFTWARE_MNEMONIC),
            Pair(Operation.IMPORT_MNEMONIC, WalletType.SOFTWARE_MNEMONIC),
            Pair(Operation.IMPORT_PRIVATE_KEY, WalletType.SOFTWARE_PRIVATE_KEY),
            Pair(Operation.CREATE_UNSIGNED_TX, WalletType.SOFTWARE_MNEMONIC),
            Pair(Operation.SOFTWARE_SIGN, WalletType.SOFTWARE_MNEMONIC),
            Pair(Operation.BROADCAST, WalletType.SOFTWARE_MNEMONIC)
        )

        for ((op, walletType) in mainnetSoftwareOps) {
            val req = CapabilityRequest(
                operation = op,
                chain = MultiChainType.ETHEREUM,
                network = Network.MAINNET,
                platform = Platform.WEAR_OS,
                buildType = BuildType.RELEASE,
                envelopeType = EvmEnvelope.LEGACY,
                signerImplementation = SignerImplementation.SOFTWARE_LOCAL,
                walletType = walletType,
                backendIdentity = BackendIdentity.PRODUCTION_V1,
                backendAvailable = true,
                backendVersion = "1.0.0",
                smokeVectorVerified = true
            )
            val decision = restrictedGate.checkCapability(req)
            assertTrue(decision is CapabilityDecision.Denied, "Operation $op on mainnet software MUST be denied when allowEvmMainnetSend=false")
        }
    }

    // ==========================================
    // Directive R8: ExtendedPublicKeyPolicy Validation Empirical Stress Tests
    // ==========================================

    private val VALID_XPUB = "xpub661MyMwAqRbcFtXgS5sYJABqqG9YLmC4Q1Rdap9gSE8NqtwybGhePY2gZ29ESFjqJoCu1Rupje8YtGqsefD265TMg7usUDFdp6W1EGMcet8"

    private fun getValidTpub(): String {
        val rawBytes = io.github.iml1s.crypto.Base58.decode(VALID_XPUB)!!
        val payload = rawBytes.copyOfRange(0, 78)
        payload[0] = 0x04.toByte()
        payload[1] = 0x35.toByte()
        payload[2] = 0x87.toByte()
        payload[3] = 0xCF.toByte()
        val checksum = platformSha256(platformSha256(payload)).copyOfRange(0, 4)
        return io.github.iml1s.crypto.Base58.encode(payload + checksum)
    }

    @Test
    fun R8_1_valid_master_fingerprint_formats_pass_validation() {
        val policy = ExtendedPublicKeyPolicy.STRICT_DEFAULT
        val validFingerprints = listOf("12345678", "A1B2C3D4", "00000000", "ffffffff", "AaBb1234")
        val samplePath = "0/0"

        for (fp in validFingerprints) {
            try {
                policy.validate(masterFingerprint = fp, xpub = VALID_XPUB, derivationPath = samplePath, isTestnet = false)
            } catch (e: InvalidExtendedPublicKeyPolicyException) {
                assertFalse(e.message?.contains("Invalid master fingerprint format") == true, "Valid 8-char hex fingerprint '$fp' should not fail format validation")
            }
        }
    }

    @Test
    fun R8_2_invalid_master_fingerprint_formats_fail_closed() {
        val policy = ExtendedPublicKeyPolicy.STRICT_DEFAULT
        val invalidFingerprints = listOf("1234567", "123456789", "1234567Z", "12 34 56", "G1B2C3D4")

        for (fp in invalidFingerprints) {
            assertFailsWith<InvalidExtendedPublicKeyPolicyException>("Invalid fingerprint '$fp' MUST throw InvalidExtendedPublicKeyPolicyException") {
                policy.validate(masterFingerprint = fp, xpub = VALID_XPUB, derivationPath = "0/0", isTestnet = false)
            }
        }
    }

    @Test
    fun R8_3_empty_master_fingerprint_disallowed_by_default() {
        val policy = ExtendedPublicKeyPolicy.STRICT_DEFAULT

        assertFailsWith<InvalidExtendedPublicKeyPolicyException>("Empty master fingerprint MUST throw when allowEmptyMasterFingerprint=false") {
            policy.validate(masterFingerprint = "", xpub = VALID_XPUB, derivationPath = "0/0", isTestnet = false)
        }
    }

    @Test
    fun R8_4_expected_master_fingerprint_mismatch_fails_closed() {
        val policy = ExtendedPublicKeyPolicy(expectedMasterFingerprint = "12345678")

        // Mismatch
        assertFailsWith<InvalidExtendedPublicKeyPolicyException>("Fingerprint mismatch MUST throw") {
            policy.validate(masterFingerprint = "87654321", xpub = VALID_XPUB, derivationPath = "0/0", isTestnet = false)
        }

        // Matching
        policy.validate(masterFingerprint = "12345678", xpub = VALID_XPUB, derivationPath = "0/0", isTestnet = false)
    }

    @Test
    fun R8_5_derivation_path_mismatch_fails_closed() {
        val policy = ExtendedPublicKeyPolicy(expectedDerivationPath = "0/0")

        assertFailsWith<InvalidExtendedPublicKeyPolicyException>("Derivation path mismatch MUST throw") {
            policy.validate(masterFingerprint = "12345678", xpub = VALID_XPUB, derivationPath = "0/1", isTestnet = false)
        }

        // Matching
        policy.validate(masterFingerprint = "12345678", xpub = VALID_XPUB, derivationPath = "0/0", isTestnet = false)
    }

    @Test
    fun R8_6_network_context_scope_enforcement_tpub_vs_xpub() {
        val policy = ExtendedPublicKeyPolicy.STRICT_DEFAULT
        val validTpub = getValidTpub()

        // Testnet with mainnet xpub string
        val errTestnet = assertFailsWith<InvalidExtendedPublicKeyPolicyException>("Should reject mainnet xpub on testnet") {
            policy.validate(masterFingerprint = "12345678", xpub = VALID_XPUB, derivationPath = "0/0", isTestnet = true)
        }
        assertTrue(errTestnet.message?.contains("Mainnet xpub (xpub) is prohibited on testnet context") == true)

        // Mainnet with testnet tpub string
        val errMainnet = assertFailsWith<InvalidExtendedPublicKeyPolicyException>("Should reject testnet tpub on mainnet") {
            policy.validate(masterFingerprint = "12345678", xpub = validTpub, derivationPath = "0/0", isTestnet = false)
        }
        assertTrue(errMainnet.message?.contains("Testnet xpub (tpub) is prohibited on mainnet context") == true)
    }
}

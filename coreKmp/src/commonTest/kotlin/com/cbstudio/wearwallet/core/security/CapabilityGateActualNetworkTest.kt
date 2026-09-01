package com.cbstudio.wearwallet.core.security

import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

import com.cbstudio.wearwallet.core.domain.model.quantities.EvmEnvelope

/**
 * Comprehensive tests verifying CapabilityGate evaluates caller-supplied actual network (Blocker 3 / R4).
 *
 * Requirements:
 * 1. Ethereum mainnet stays MAINNET even though Sepolia exists.
 * 2. Polygon mainnet stays MAINNET even though Mumbai exists.
 * 3. Mainnet software sign is denied under ReleaseProductionCapabilityGate (allowEvmMainnetSend = false).
 * 4. Testnet software sign is allowed.
 * 5. Testnet chainId is preserved (Sepolia = 11155111, Goerli = 5, Mumbai = 80001).
 * 6. Unknown chain/network is denied.
 * 7. MultiChainType.isTestnetSupported is feature metadata only and NEVER overrides caller network.
 */
class CapabilityGateActualNetworkTest {

    private val releaseGate = ReleaseProductionCapabilityGate(
        allowEvmMainnetSend = false,
        allowBroadcast = true
    )

    private val devGate = AllowDevCapabilityGate(allowBroadcast = true)

    private fun makeRequest(
        operation: Operation,
        chain: MultiChainType,
        network: Network,
        signerImplementation: SignerImplementation = SignerImplementation.SOFTWARE_LOCAL,
        walletType: WalletType = if (signerImplementation == SignerImplementation.KEYSTONE_HARDWARE) WalletType.KEYSTONE_XPUB else WalletType.SOFTWARE_MNEMONIC
    ): CapabilityRequest {
        return CapabilityRequest(
            operation = operation,
            chain = chain,
            network = network,
            platform = Platform.WEAR_OS,
            buildType = BuildType.RELEASE,
            envelopeType = EvmEnvelope.LEGACY,
            signerImplementation = signerImplementation,
            walletType = walletType,
            backendIdentity = BackendIdentity.PRODUCTION_V1,
            backendAvailable = true,
            backendVersion = "1.0.0",
            smokeVectorVerified = true
        )
    }

    @Test
    fun test_ethereum_mainnet_stays_MAINNET_and_software_sign_is_denied_even_though_sepolia_exists() {
        // MultiChainType.ETHEREUM has isTestnetSupported = true
        assertTrue(MultiChainType.ETHEREUM.isTestnetSupported, "MultiChainType.ETHEREUM has testnet support metadata")

        // When requesting with Network.MAINNET, it MUST be evaluated as MAINNET
        val mainnetAllowed = releaseGate.verifyCapability(
            makeRequest(
                operation = Operation.SOFTWARE_SIGN,
                chain = MultiChainType.ETHEREUM,
                network = Network.MAINNET,
                signerImplementation = SignerImplementation.SOFTWARE_LOCAL
            )
        )
        assertFalse(
            mainnetAllowed,
            "Ethereum MAINNET software sign MUST be denied under ReleaseProductionCapabilityGate"
        )
    }

    @Test
    fun test_polygon_mainnet_stays_MAINNET_and_software_sign_is_denied_even_though_mumbai_exists() {
        // MultiChainType.POLYGON has isTestnetSupported = true
        assertTrue(MultiChainType.POLYGON.isTestnetSupported, "MultiChainType.POLYGON has testnet support metadata")

        // When requesting with Network.MAINNET, it MUST be evaluated as MAINNET
        val mainnetAllowed = releaseGate.verifyCapability(
            makeRequest(
                operation = Operation.SOFTWARE_SIGN,
                chain = MultiChainType.POLYGON,
                network = Network.MAINNET,
                signerImplementation = SignerImplementation.SOFTWARE_LOCAL
            )
        )
        assertFalse(
            mainnetAllowed,
            "Polygon MAINNET software sign MUST be denied under ReleaseProductionCapabilityGate"
        )
    }

    @Test
    fun test_testnet_software_sign_is_allowed() {
        // Ethereum Sepolia (TESTNET)
        val sepoliaAllowed = releaseGate.verifyCapability(
            makeRequest(
                operation = Operation.SOFTWARE_SIGN,
                chain = MultiChainType.ETHEREUM,
                network = Network.TESTNET,
                signerImplementation = SignerImplementation.SOFTWARE_LOCAL
            )
        )
        assertTrue(sepoliaAllowed, "Ethereum TESTNET software sign must be allowed")

        // Polygon Mumbai (TESTNET)
        val mumbaiAllowed = releaseGate.verifyCapability(
            makeRequest(
                operation = Operation.SOFTWARE_SIGN,
                chain = MultiChainType.POLYGON,
                network = Network.TESTNET,
                signerImplementation = SignerImplementation.SOFTWARE_LOCAL
            )
        )
        assertTrue(mumbaiAllowed, "Polygon TESTNET software sign must be allowed")
    }

    @Test
    fun test_mainnet_software_sign_is_denied_for_all_chains_under_release_gate() {
        val evmChains = listOf(
            MultiChainType.ETHEREUM,
            MultiChainType.POLYGON,
            MultiChainType.BSC,
            MultiChainType.ARBITRUM,
            MultiChainType.OPTIMISM,
            MultiChainType.BASE
        )

        for (chain in evmChains) {
            val allowed = releaseGate.verifyCapability(
                makeRequest(
                    operation = Operation.SOFTWARE_SIGN,
                    chain = chain,
                    network = Network.MAINNET,
                    signerImplementation = SignerImplementation.SOFTWARE_LOCAL
                )
            )
            assertFalse(
                allowed,
                "Chain $chain MAINNET software sign MUST be denied under ReleaseProductionCapabilityGate(allowEvmMainnetSend = false)"
            )
        }
    }

    @Test
    fun test_mainnet_software_sign_is_allowed_when_allowEvmMainnetSend_is_true() {
        val permissiveGate = ReleaseProductionCapabilityGate(
            allowEvmMainnetSend = true,
            allowBroadcast = true
        )

        val evmChains = listOf(
            MultiChainType.ETHEREUM,
            MultiChainType.POLYGON,
            MultiChainType.BSC,
            MultiChainType.ARBITRUM,
            MultiChainType.OPTIMISM,
            MultiChainType.BASE
        )

        for (chain in evmChains) {
            val allowed = permissiveGate.verifyCapability(
                makeRequest(
                    operation = Operation.SOFTWARE_SIGN,
                    chain = chain,
                    network = Network.MAINNET,
                    signerImplementation = SignerImplementation.SOFTWARE_LOCAL
                )
            )
            assertTrue(
                allowed,
                "Chain $chain MAINNET software sign SHOULD be allowed when allowEvmMainnetSend = true"
            )
        }
    }

    @Test
    fun test_testnet_chain_ids_are_preserved() {
        assertEquals(11155111L, ChainType.SEPOLIA.getChainId(), "Sepolia chainId must be 11155111")
        assertEquals(5L, ChainType.GOERLI.getChainId(), "Goerli chainId must be 5")
        assertEquals(80001L, ChainType.MUMBAI.getChainId(), "Mumbai chainId must be 80001")
        assertEquals(1L, ChainType.ETHEREUM.getChainId(), "Ethereum mainnet chainId must be 1")
        assertEquals(137L, ChainType.POLYGON.getChainId(), "Polygon mainnet chainId must be 137")
        assertEquals(56L, ChainType.BSC.getChainId(), "BSC mainnet chainId must be 56")
        assertEquals(42161L, ChainType.ARBITRUM.getChainId(), "Arbitrum mainnet chainId must be 42161")
        assertEquals(10L, ChainType.OPTIMISM.getChainId(), "Optimism mainnet chainId must be 10")
        assertEquals(8453L, ChainType.BASE.getChainId(), "Base mainnet chainId must be 8453")
    }

    @Test
    fun test_unknown_chain_and_unknown_network_are_default_denied() {
        // Unknown network
        val unknownNetworkReq = CapabilityRequest(
            operation = Operation.IMPORT_XPUB,
            chain = MultiChainType.ETHEREUM,
            network = Network.UNKNOWN,
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
        assertTrue(
            releaseGate.checkCapability(unknownNetworkReq) is CapabilityDecision.Denied,
            "Network.UNKNOWN MUST be denied"
        )

        // Unsupported signer
        val unsupportedSignerReq = CapabilityRequest(
            operation = Operation.IMPORT_XPUB,
            chain = MultiChainType.ETHEREUM,
            network = Network.MAINNET,
            platform = Platform.WEAR_OS,
            buildType = BuildType.RELEASE,
            envelopeType = EvmEnvelope.LEGACY,
            signerImplementation = SignerImplementation.UNSUPPORTED,
            walletType = WalletType.KEYSTONE_XPUB,
            backendIdentity = BackendIdentity.PRODUCTION_V1,
            backendAvailable = true,
            backendVersion = "1.0.0",
            smokeVectorVerified = true
        )
        assertTrue(
            releaseGate.checkCapability(unsupportedSignerReq) is CapabilityDecision.Denied,
            "SignerImplementation.UNSUPPORTED MUST be denied"
        )

        // Non-allowlisted chain
        val nonAllowlistedChainReq = CapabilityRequest(
            operation = Operation.CREATE_WALLET,
            chain = MultiChainType.SOLANA,
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
        assertTrue(
            releaseGate.checkCapability(nonAllowlistedChainReq) is CapabilityDecision.Denied,
            "Non-allowlisted chain MUST be denied"
        )
    }

    @Test
    fun test_hardware_sign_is_allowed_on_mainnet_and_testnet() {
        // Keystone hardware sign on Ethereum MAINNET
        val mainnetHwAllowed = releaseGate.verifyCapability(
            makeRequest(
                operation = Operation.HARDWARE_SIGN_REQUEST,
                chain = MultiChainType.ETHEREUM,
                network = Network.MAINNET,
                signerImplementation = SignerImplementation.KEYSTONE_HARDWARE
            )
        )
        assertTrue(mainnetHwAllowed, "Hardware sign on Ethereum MAINNET must be allowed")

        // Keystone hardware sign on Ethereum TESTNET
        val testnetHwAllowed = releaseGate.verifyCapability(
            makeRequest(
                operation = Operation.HARDWARE_SIGN_REQUEST,
                chain = MultiChainType.ETHEREUM,
                network = Network.TESTNET,
                signerImplementation = SignerImplementation.KEYSTONE_HARDWARE
            )
        )
        assertTrue(testnetHwAllowed, "Hardware sign on Ethereum TESTNET must be allowed")
    }
}

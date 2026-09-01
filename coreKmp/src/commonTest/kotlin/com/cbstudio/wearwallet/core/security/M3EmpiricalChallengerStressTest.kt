package com.cbstudio.wearwallet.core.security

import com.cbstudio.wearwallet.core.di.getAllCoreModules
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.core.multichain.sdk.WalletManager
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import kotlin.test.*

import com.cbstudio.wearwallet.core.domain.model.quantities.EvmEnvelope

class M3EmpiricalChallengerStressTest : KoinTest {

    @Test
    fun testReleaseDIGraphContainsNoSwapBindings() {
        try {
            stopKoin()
        } catch (_: Exception) {}

        startKoin {
            modules(getAllCoreModules())
        }

        try {
            val swapExecutor = getKoin().getOrNull<com.cbstudio.wearwallet.core.swap.SwapExecutor>()
            assertNull(swapExecutor, "SwapExecutor MUST NOT be registered in release DI graph")

            val executeSwapUseCase = getKoin().getOrNull<com.cbstudio.wearwallet.core.domain.usecase.swap.ExecuteSwapUseCase>()
            assertNull(executeSwapUseCase, "ExecuteSwapUseCase MUST NOT be registered in release DI graph")
        } finally {
            stopKoin()
        }
    }

    @Test
    fun testAllowlistExcludesAllNonApprovedChains() {
        val gate = ReleaseProductionCapabilityGate()
        val walletManager = WalletManager(
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about",
            gate
        )

        val supportedChains = walletManager.getSupportedChains()

        val expectedAllowlisted = setOf(
            MultiChainType.ETHEREUM,
            MultiChainType.POLYGON,
            MultiChainType.BSC,
            MultiChainType.ARBITRUM,
            MultiChainType.OPTIMISM,
            MultiChainType.BASE
        )

        assertEquals(expectedAllowlisted, supportedChains.toSet(), "getSupportedChains() must return exact 6 allowlisted EVM chains")

        // Test every MultiChainType individually against capabilityGate.isChainSupported
        for (chain in MultiChainType.values()) {
            if (chain in expectedAllowlisted) {
                assertTrue(gate.isChainSupported(chain), "Chain $chain should be supported by gate")
            } else {
                assertFalse(gate.isChainSupported(chain), "Chain $chain MUST NOT be supported by gate")
            }
        }
    }

    @Test
    fun testCapabilityGateDefaultDenyAndInvalidInputs() {
        val gate = ReleaseProductionCapabilityGate(allowEvmMainnetSend = false, allowBroadcast = true)

        // 1. Invalid signers
        val invalidSigners = listOf("", "   ", "malicious_signer", "custom_eval", "hacked_signer")
        for (signer in invalidSigners) {
            val req = CapabilityRequest(
                operation = Operation.SOFTWARE_SIGN,
                chain = MultiChainType.ETHEREUM,
                network = Network.MAINNET,
                platform = Platform.WEAR_OS,
                buildType = BuildType.RELEASE,
                envelopeType = EvmEnvelope.LEGACY,
                signerImplementation = SignerImplementation.fromString(signer),
                walletType = WalletType.SOFTWARE_PRIVATE_KEY,
                backendIdentity = BackendIdentity.PRODUCTION_V1,
                backendAvailable = true,
                backendVersion = "1.0.0",
                smokeVectorVerified = true
            )
            val decision = gate.checkCapability(req)
            assertTrue(decision is CapabilityDecision.Denied, "Signer '$signer' should be denied")
        }

        // 2. Unapproved chain (e.g. SOLANA)
        val solanaReq = CapabilityRequest(
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
        val solanaDecision = gate.checkCapability(solanaReq)
        assertTrue(solanaDecision is CapabilityDecision.Denied, "Solana operations must be denied in release gate")

        // 3. Broadcast disabled
        val gateNoBroadcast = ReleaseProductionCapabilityGate(allowEvmMainnetSend = false, allowBroadcast = false)
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
        val broadcastDecision = gateNoBroadcast.checkCapability(broadcastReq)
        assertTrue(broadcastDecision is CapabilityDecision.Denied, "BROADCAST operation should be denied when allowBroadcast=false")
    }
}

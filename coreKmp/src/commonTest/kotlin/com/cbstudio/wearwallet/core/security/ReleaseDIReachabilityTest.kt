package com.cbstudio.wearwallet.core.security

import com.cbstudio.wearwallet.core.di.coreModule
import com.cbstudio.wearwallet.core.di.getAllCoreModules
import com.cbstudio.wearwallet.core.domain.model.ChainType
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import org.koin.test.get
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

import com.cbstudio.wearwallet.core.domain.model.context.ChainExecutionContextRegistry
import com.cbstudio.wearwallet.core.security.CapabilityRequest
import com.cbstudio.wearwallet.core.security.Platform
import com.cbstudio.wearwallet.core.security.BuildType
import com.cbstudio.wearwallet.core.domain.model.quantities.EvmEnvelope
import com.cbstudio.wearwallet.core.security.SignerImplementation
import com.cbstudio.wearwallet.core.security.WalletType
import com.cbstudio.wearwallet.core.security.BackendIdentity

class ReleaseDIReachabilityTest : KoinTest {

    private fun stopKoinQuietly() {
        try {
            stopKoin()
        } catch (_: Exception) {
            // Ignore if Koin not started
        }
    }

    @Test
    fun testCoreModuleAloneDoesNotBindPlaceholderProductionCryptoProvider() {
        stopKoinQuietly()
        startKoin {
            modules(coreModule)
        }
        try {
            val resolved = getKoin().getOrNull<CryptoProvider>()
            assertNull(
                resolved,
                "Loading only coreModule must not resolve a CryptoProvider. " +
                    "Placeholder CommonCryptoProvider must not be a production binding; " +
                    "got ${resolved?.let { it::class.simpleName }}"
            )
            assertTrue(
                resolved !is CommonCryptoProvider,
                "coreModule must not bind CommonCryptoProvider as production signing"
            )
        } finally {
            stopKoinQuietly()
        }
    }

    @Test
    fun testGetAllCoreModulesBindsPlatformCryptoProviderAndReleaseGateDefaults() {
        stopKoinQuietly()
        startKoin {
            modules(getAllCoreModules())
        }
        try {
            val cryptoProvider: CryptoProvider = get()
            val capabilityGate: CapabilityGate = get()

            assertEquals(
                "AndroidCryptoProvider",
                cryptoProvider::class.simpleName,
                "Android getAllCoreModules() must bind platform AndroidCryptoProvider, not a common placeholder"
            )
            assertFalse(
                cryptoProvider is CommonCryptoProvider,
                "Production graph must not resolve CommonCryptoProvider"
            )
            assertTrue(
                capabilityGate is ReleaseProductionCapabilityGate,
                "CapabilityGate must resolve to ReleaseProductionCapabilityGate"
            )
            assertFalse(
                capabilityGate.isEvmMainnetSendAllowed(),
                "Default allowEvmMainnetSend must be false"
            )

            val ctx = ChainExecutionContextRegistry.resolve(ChainType.ETHEREUM)
            val broadcastReq = CapabilityRequest(
                operation = Operation.BROADCAST,
                chain = ctx.multiChainType,
                network = ctx.capabilityNetwork,
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
            val broadcastDecision = capabilityGate.checkCapability(broadcastReq)
            assertTrue(
                broadcastDecision is CapabilityDecision.Denied,
                "Default allowBroadcast must be false so BROADCAST is denied"
            )
            assertTrue(
                (broadcastDecision as CapabilityDecision.Denied).reason.contains("allowBroadcast=false"),
                "BROADCAST denial must cite allowBroadcast=false, got: ${broadcastDecision.reason}"
            )
        } finally {
            stopKoinQuietly()
        }
    }

    @Test
    fun testReleaseKoinGraphStartsAndCapabilityGateFailsClosedOnUnapprovedMainnetSend() {
        try {
            stopKoin()
        } catch (e: Exception) {
            // Ignore if Koin not started
        }

        startKoin {
            modules(getAllCoreModules())
        }

        try {
            val capabilityGate: CapabilityGate = get()

            assertFalse(capabilityGate.isEvmMainnetSendAllowed(), "Release capability gate MUST disallow EVM mainnet software sending by default")

            val mainnetChains = listOf(
                ChainType.ETHEREUM,
                ChainType.BSC,
                ChainType.POLYGON,
                ChainType.ARBITRUM,
                ChainType.OPTIMISM,
                ChainType.BASE,
                ChainType.AVALANCHE
            )

            for (chain in mainnetChains) {
                val ctx = ChainExecutionContextRegistry.resolve(chain)
                for (op in Operation.entries) {
                    if (op == Operation.SOFTWARE_SIGN || op == Operation.BROADCAST) {
                        val req = CapabilityRequest(
                            operation = op,
                            chain = ctx.multiChainType,
                            network = ctx.capabilityNetwork,
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
                        assertFalse(
                            capabilityGate.verifyCapability(req),
                            "CapabilityGate must report false for $op on $chain with software signer by default"
                        )
                    }
                }
            }
            val swapExecutor = getKoin().getOrNull<com.cbstudio.wearwallet.core.swap.SwapExecutor>()
            kotlin.test.assertNull(swapExecutor, "SwapExecutor MUST NOT be registered in release DI graph")

            val executeSwapUseCase = getKoin().getOrNull<com.cbstudio.wearwallet.core.domain.usecase.swap.ExecuteSwapUseCase>()
            kotlin.test.assertNull(executeSwapUseCase, "ExecuteSwapUseCase MUST NOT be registered in release DI graph")
        } finally {
            stopKoin()
        }
    }

    @Test
    fun testDeriveAddressFromXpubRejectsEmptyAndInvalidXpub() {
        runBlocking {
            val provider: CryptoProvider = CommonCryptoProvider()
            assertFailsWith<IllegalArgumentException> {
                provider.deriveAddressFromXpub("", "m/44'/60'/0'/0/0")
            }
        }
    }
}

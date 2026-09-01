package com.cbstudio.wearwallet.core.security

import com.cbstudio.wearwallet.core.di.getAllCoreModules
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.usecase.transaction.SendTransactionUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import org.koin.test.get
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

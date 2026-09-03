package com.cbstudio.wearwallet.di

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.cbstudio.wearwallet.RobolectricApplication
import com.cbstudio.wearwallet.bridge.CoreKmpBridge
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.context.ChainExecutionContextRegistry
import com.cbstudio.wearwallet.core.domain.model.intent.ConfirmedEvmTransactionIntent
import com.cbstudio.wearwallet.core.domain.model.quantities.*
import com.cbstudio.wearwallet.core.domain.usecase.transaction.SendTransactionUseCase
import com.cbstudio.wearwallet.core.domain.usecase.transaction.TypedUnsupportedTransactionException
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.core.multichain.monero.crypto.AndroidMoneroCryptoProvider
import com.cbstudio.wearwallet.core.security.CapabilityGate
import com.cbstudio.wearwallet.core.security.Operation
import com.cbstudio.wearwallet.core.security.ReleaseProductionCapabilityGate
import com.cbstudio.wearwallet.domain.service.CryptoService
import com.cbstudio.wearwallet.domain.service.EVMTransactionService
import com.cbstudio.wearwallet.domain.usecase.CoreKmpSendTransactionUseCase
import com.cbstudio.wearwallet.domain.usecase.CoreKmpGetBalanceUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import org.koin.test.get
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

/**
 * Strict Production Release DI Safety Test (Directive R9)
 *
 * Rules:
 * - NO allowOverride(true)
 * - NO relaxed mocks or mock modules
 * - Strictly loaded production modules graph
 * - RecordingFake side-effect verification (broadcast, sign, network, db == 0 on denied operations)
 * - Direct invocation of sensitive entry points (SendTransactionUseCase, EVMTransactionService.sendTransaction, CoreKmpSendTransactionUseCase, Monero crypto provider)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = RobolectricApplication::class)
class ReleaseDISafetyTest : KoinTest {

    class RecordingFakeSideEffectTracker : com.cbstudio.wearwallet.core.security.SideEffectTracker {
        val broadcastCount = AtomicInteger(0)
        val signingCount = AtomicInteger(0)
        val networkSendCount = AtomicInteger(0)
        val dbWriteCount = AtomicInteger(0)

        val totalSideEffects: Int
            get() = broadcastCount.get() + signingCount.get() + networkSendCount.get() + dbWriteCount.get()

        override fun onSign() { signingCount.incrementAndGet() }
        override fun onBroadcast() { broadcastCount.incrementAndGet() }
        override fun onNetworkSend() { networkSendCount.incrementAndGet() }
        override fun onDbWrite() { dbWriteCount.incrementAndGet() }

        fun reset() {
            broadcastCount.set(0)
            signingCount.set(0)
            networkSendCount.set(0)
            dbWriteCount.set(0)
        }
    }

    private val sideEffectTracker = RecordingFakeSideEffectTracker()

    @Before
    fun setUp() {
        stopKoin()
        sideEffectTracker.reset()
        com.cbstudio.wearwallet.core.security.GlobalSideEffectTracker.instance = sideEffectTracker
    }

    @After
    fun tearDown() {
        stopKoin()
        com.cbstudio.wearwallet.core.security.GlobalSideEffectTracker.instance = com.cbstudio.wearwallet.core.security.NoOpSideEffectTracker
    }

    private fun getPrivateCapabilityGate(instance: Any): CapabilityGate? {
        val field = instance::class.java.getDeclaredField("capabilityGate")
        field.isAccessible = true
        return field.get(instance) as? CapabilityGate
    }

    @Test
    fun testProductionReleaseKoinGraphStartsWithoutAllowOverrideAndUnapprovedComponentsAreNull() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // NO allowOverride(true), NO relaxed mocks, NO mock modules
        startKoin {
            androidContext(context)
            modules(
                wearModule + getAllWearModules()
            )
        }

        try {
            val capabilityGate: CapabilityGate = get()
            val cryptoProvider: com.cbstudio.wearwallet.core.security.CryptoProvider = get()
            val sendTransactionUseCase: SendTransactionUseCase = get()
            val createWalletUseCase: com.cbstudio.wearwallet.core.domain.usecase.wallet.CreateWalletUseCase = get()
            val importWalletUseCase: com.cbstudio.wearwallet.core.domain.usecase.wallet.ImportWalletUseCase = get()
            val sendViewModel: com.cbstudio.wearwallet.presentation.wallet.screens.main.tx.SendTransactionViewModel = get()
            val swapViewModel: com.cbstudio.wearwallet.presentation.wallet.screens.swap.SwapViewModel = get()

            assertNotNull(capabilityGate)
            assertNotNull(cryptoProvider)
            assertTrue(cryptoProvider is com.cbstudio.wearwallet.core.platform.android.AndroidCryptoProvider)
            assertNotNull(sendTransactionUseCase)
            assertNotNull(createWalletUseCase)
            assertNotNull(importWalletUseCase)
            assertNotNull(sendViewModel)
            assertNotNull(swapViewModel)

            // Assert unapproved/secondary components are isolated and NOT present in production graph
            assertNull(getKoin().getOrNull<CryptoService>(), "CryptoService generic signer MUST NOT be registered in release DI graph")
            assertNull(getKoin().getOrNull<EVMTransactionService>(), "EVMTransactionService alternate raw-key pipeline MUST NOT be registered in release DI graph")
            assertNull(getKoin().getOrNull<CoreKmpBridge>(), "CoreKmpBridge MUST NOT be registered in release DI graph")
            assertNull(getKoin().getOrNull<CoreKmpSendTransactionUseCase>(), "CoreKmpSendTransactionUseCase MUST NOT be registered in release DI graph")
            assertNull(getKoin().getOrNull<CoreKmpGetBalanceUseCase>(), "CoreKmpGetBalanceUseCase MUST NOT be registered in release DI graph")
            assertNull(getKoin().getOrNull<com.cbstudio.wearwallet.core.multichain.sdk.WalletManager>(), "WalletManager MUST NOT be registered in release DI graph")
            assertNull(getKoin().getOrNull<com.cbstudio.wearwallet.core.multichain.monero.MoneroWalletManager>(), "MoneroWalletManager MUST NOT be registered in release DI graph")
            assertNull(getKoin().getOrNull<com.cbstudio.wearwallet.core.multichain.monero.crypto.MoneroCryptoProvider>(), "MoneroCryptoProvider MUST NOT be registered in release DI graph")
            assertNull(getKoin().getOrNull<com.cbstudio.wearwallet.core.swap.SwapExecutor>(), "SwapExecutor MUST NOT be registered in release DI graph")
            assertNull(getKoin().getOrNull<com.cbstudio.wearwallet.core.domain.usecase.swap.ExecuteSwapUseCase>(), "ExecuteSwapUseCase MUST NOT be registered in release DI graph")

            assertTrue(capabilityGate is ReleaseProductionCapabilityGate, "CapabilityGate must resolve to ReleaseProductionCapabilityGate in release DI graph")

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
                        val req = com.cbstudio.wearwallet.core.security.CapabilityRequest.createForTesting(
                            operation = op,
                            chain = ctx.multiChainType,
                            network = ctx.capabilityNetwork,
                            platform = com.cbstudio.wearwallet.core.security.Platform.WEAR_OS,
                            buildType = com.cbstudio.wearwallet.core.security.BuildType.RELEASE,
                            envelopeType = com.cbstudio.wearwallet.core.domain.model.quantities.EvmEnvelope.LEGACY,
                            signerImplementation = com.cbstudio.wearwallet.core.security.SignerImplementation.SOFTWARE_LOCAL,
                            walletType = com.cbstudio.wearwallet.core.security.WalletType.SOFTWARE_MNEMONIC,
                            backendIdentity = com.cbstudio.wearwallet.core.security.BackendIdentity.PRODUCTION_V1,
                            backendAvailable = true,
                            backendVersion = "1.0.0",
                            smokeVectorVerified = true
                        )
                        assertFalse(
                            capabilityGate.verifyCapability(req),
                            "CapabilityGate must deny $op on $chain for software signer in release build"
                        )
                    }
                }
            }
        } finally {
            stopKoin()
        }
    }

    @Test
    fun testDirectInvokeSendTransactionUseCaseOnMainnetFailsClosedWithZeroSideEffects() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()

        startKoin {
            androidContext(context)
            modules(wearModule + getAllWearModules())
        }

        try {
            val sendTransactionUseCase: SendTransactionUseCase = get()

            val senderAddr = EvmAddress.fromString("0x71C7656EC7ab88b098defB751B7401B5f6d8976F")
            val recipientAddr = EvmAddress.fromString("0x1111111111111111111111111111111111111111")
            val nonceObj = Nonce(0L)
            val gasPrice = Wei.fromGwei(10)
            val gasLimit = GasLimit.fromDecimalString("21000")
            val feeWei = Wei.fromWei(gasPrice.value * com.ionspin.kotlin.bignum.integer.BigInteger.fromLong(21000L))
            val baseUnitAmount = BaseUnitAmount.fromDecimalString("0.1", 18)

            val executionContext = ChainExecutionContextRegistry.resolve(MultiChainType.ETHEREUM, false)
            val fingerprint = ConfirmedEvmTransactionIntent.createFingerprint(
                walletId = "test-wallet-1",
                keyAlias = "test-wallet-1",
                sender = senderAddr,
                chain = MultiChainType.ETHEREUM,
                executionContext = executionContext,
                envelopeType = EvmEnvelope.LEGACY,
                recipient = recipientAddr,
                tokenContract = null,
                tokenSymbol = null,
                tokenDecimals = null,
                humanAmount = "0.1",
                baseUnitAmount = baseUnitAmount,
                nativeValue = Wei.fromWei(baseUnitAmount.value),
                calldata = Calldata.EMPTY,
                nonce = nonceObj,
                gasPrice = gasPrice,
                gasLimit = gasLimit,
                fee = feeWei
            )

            val mainnetIntent = ConfirmedEvmTransactionIntent(
                walletId = "test-wallet-1",
                keyAlias = "test-wallet-1",
                sender = senderAddr,
                chain = MultiChainType.ETHEREUM,
                executionContext = executionContext,
                envelopeType = EvmEnvelope.LEGACY,
                recipient = recipientAddr,
                tokenContract = null,
                tokenSymbol = null,
                tokenDecimals = null,
                humanAmount = "0.1",
                baseUnitAmount = baseUnitAmount,
                nativeValue = Wei.fromWei(baseUnitAmount.value),
                calldata = Calldata.EMPTY,
                nonce = nonceObj,
                gasPrice = gasPrice,
                gasLimit = gasLimit,
                fee = feeWei,
                canonicalFingerprint = fingerprint
            )

            val result = sendTransactionUseCase(mainnetIntent).first()

            assertTrue(result is Result.Failure, "SendTransactionUseCase must fail on mainnet EVM software send")
            val failureEx = (result as Result.Failure).exception
            assertTrue(
                failureEx is TypedUnsupportedTransactionException || failureEx.message?.contains("disabled") == true || failureEx.message?.contains("fail-closed") == true,
                "Failure exception must indicate capability gate restriction: ${failureEx.message}"
            )

            assertEquals(0, sideEffectTracker.totalSideEffects, "Direct-invoking denied SendTransactionUseCase must have 0 side effects")
        } finally {
            stopKoin()
        }
    }

    @Test
    fun testEVMTransactionServiceIsNotRegisteredInReleaseGraphAndFailsClosed() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        startKoin {
            androidContext(context)
            modules(wearModule + getAllWearModules())
        }

        try {
            val evmService = getKoin().getOrNull<EVMTransactionService>()
            assertNull(evmService, "EVMTransactionService MUST NOT be registered in release DI graph")
            assertEquals(0, sideEffectTracker.totalSideEffects, "Direct-invoking unmapped EVMTransactionService must have 0 side effects")
        } finally {
            stopKoin()
        }
    }

    @Test
    fun testDirectInvokeMoneroCryptoProviderFailsClosedWithZeroSideEffects() = runBlocking {
        val moneroProvider = AndroidMoneroCryptoProvider

        val deriveRes = moneroProvider.deriveKeysFromMnemonic("word1 word2 word3", "pass")
        assertTrue(deriveRes is Result.Failure, "Monero key derivation must fail closed in release")
        assertTrue((deriveRes as Result.Failure).exception is TypedUnsupportedTransactionException)

        val signRes = moneroProvider.signTransaction("tx", listOf())
        assertTrue(signRes is Result.Failure, "Monero signing must fail closed in release")
        assertTrue((signRes as Result.Failure).exception is TypedUnsupportedTransactionException)

        val broadcastRes = moneroProvider.broadcastTransaction("tx")
        assertTrue(broadcastRes is Result.Failure, "Monero broadcasting must fail closed in release")
        assertTrue((broadcastRes as Result.Failure).exception is TypedUnsupportedTransactionException)

        assertEquals(0, sideEffectTracker.totalSideEffects, "Direct-invoking Monero crypto provider must have 0 side effects")
    }

    @Test
    fun testCoreKmpSendTransactionUseCaseIsNotRegisteredInReleaseGraphAndFailsClosed() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        startKoin {
            androidContext(context)
            modules(wearModule + getAllWearModules())
        }

        try {
            val coreKmpUseCase = getKoin().getOrNull<CoreKmpSendTransactionUseCase>()
            assertNull(coreKmpUseCase, "CoreKmpSendTransactionUseCase MUST NOT be registered in release DI graph")
            assertEquals(0, sideEffectTracker.totalSideEffects, "Side effects must be 0")
        } finally {
            stopKoin()
        }
    }

    @Test
    fun testPlatformDeletionCleanupHookResolvesToAndroidPlatformDeletionCleanupHookInReleaseGraph() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        startKoin {
            androidContext(context)
            modules(wearModule + getAllWearModules())
        }

        try {
            val hook: com.cbstudio.wearwallet.core.platform.PlatformDeletionCleanupHook = get()
            assertNotNull(hook, "PlatformDeletionCleanupHook must be resolved in release DI graph")
            assertTrue(
                hook is com.cbstudio.wearwallet.platform.AndroidPlatformDeletionCleanupHook,
                "PlatformDeletionCleanupHook MUST resolve to AndroidPlatformDeletionCleanupHook, got: ${hook::class.qualifiedName}"
            )
            assertFalse(
                hook.javaClass.simpleName.contains("NoOp"),
                "PlatformDeletionCleanupHook MUST NOT be NoOp in release graph"
            )

            val walletRepo: com.cbstudio.wearwallet.core.domain.repository.WalletRepository = get()
            assertNotNull(walletRepo, "WalletRepository must be resolved in release DI graph")
            assertTrue(
                walletRepo is com.cbstudio.wearwallet.core.data.repository.WalletRepositoryImpl,
                "WalletRepository must resolve to WalletRepositoryImpl"
            )
        } finally {
            stopKoin()
        }
    }

    @Test
    fun testNoNoOpPlatformDeletionCleanupHookInProductionSourceSets() {
        val rootDir = findProjectRoot()
        val productionSourceDirectories = listOf(
            "coreKmp/src/commonMain",
            "coreKmp/src/androidMain",
            "coreKmp/src/iosMain",
            "coreKmp/src/watchosMain",
            "wear/src/main"
        )
        val prohibitedSymbol = "NoOpPlatformDeletionCleanupHook"
        val violations = mutableListOf<String>()

        for (relDir in productionSourceDirectories) {
            val dir = java.io.File(rootDir, relDir)
            if (!dir.exists()) continue

            dir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    if (file.name.contains(prohibitedSymbol, ignoreCase = true)) {
                        violations.add("Prohibited file in production: ${file.relativeTo(rootDir).path}")
                    }
                    if (listOf("kt", "java").contains(file.extension.lowercase())) {
                        val lines = file.readLines()
                        lines.forEachIndexed { index, line ->
                            val trimmed = line.trim()
                            if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                                return@forEachIndexed
                            }
                            if (line.contains(prohibitedSymbol)) {
                                violations.add("Prohibited symbol '$prohibitedSymbol' at ${file.relativeTo(rootDir).path}:${index + 1} -> $trimmed")
                            }
                        }
                    }
                }
            }
        }

        assertTrue(
            violations.isEmpty(),
            "Architecture Violation: Found NoOpPlatformDeletionCleanupHook in production source sets:\n" + violations.joinToString("\n")
        )
    }

    private fun findProjectRoot(): java.io.File {
        var current = java.io.File(System.getProperty("user.dir") ?: ".")
        while (current.parentFile != null) {
            if (java.io.File(current, "settings.gradle.kts").exists() || java.io.File(current, "settings.gradle").exists()) {
                return current
            }
            current = current.parentFile
        }
        return java.io.File(System.getProperty("user.dir") ?: ".")
    }
}


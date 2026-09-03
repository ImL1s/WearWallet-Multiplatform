package com.cbstudio.wearwallet.di

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.cbstudio.wearwallet.RobolectricApplication
import com.cbstudio.wearwallet.core.domain.model.quantities.EvmEnvelope
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.core.security.*
import com.cbstudio.wearwallet.domain.service.CryptoService
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
import kotlin.test.*

/**
 * Challenger 1 Adversarial Stress Test Suite for Requirements R2, R3, R4
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = RobolectricApplication::class)
class Challenger1R2R3R4AdversarialTest : KoinTest {

    @Before
    fun setUp() {
        try {
            stopKoin()
        } catch (e: Exception) {
            // Ignore
        }
    }

    @After
    fun tearDown() {
        try {
            stopKoin()
        } catch (e: Exception) {
            // Ignore
        }
    }

    // =========================================================================
    // R2: Purge CryptoService Generic Signer in Wear Release DI
    // =========================================================================
    @Test
    fun testR2_CryptoServiceGenericSignerPurgedFromReleaseDI() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        startKoin {
            androidContext(context)
            modules(wearModule + getAllWearModules())
        }

        try {
            // 1. getOrNull<CryptoService>() must return null
            val cryptoService = getKoin().getOrNull<CryptoService>()
            assertNull(cryptoService, "CryptoService generic signer MUST NOT be registered in release DI graph")

            // 2. Direct get<CryptoService>() must fail / throw exception
            assertFails {
                get<CryptoService>()
            }
        } finally {
            stopKoin()
        }
    }

    // =========================================================================
    // R3: Strict Enum Validation & Fuzzing in CapabilityRequest
    // =========================================================================
    @Test
    fun testR3_EnumFromStringFuzzingAndBoundaryValuesReturnUnknownOrUnsupported() {
        val adversarialInputs = listOf(
            null,
            "",
            "   ",
            "\t\n\r",
            "\u0000",
            "UNKNOWN",
            "UNSUPPORTED",
            "invalid_enum_name_123",
            "<script>alert('xss')</script>",
            "SELECT * FROM users;",
            "A".repeat(500),
            "WEAR",
            "ANDROID",
            "DEVELOPMENT",
            "MAIN",
            "MOCK_BACKEND",
            "STAGING_V2",
            "12345",
            "true",
            "false"
        )

        for (input in adversarialInputs) {
            val normalizedInput = input?.lowercase()?.trim()

            // Platform
            if (normalizedInput !in listOf("wearos", "wear_os", "android_wear", "android", "android_phone", "phone", "ios", "watchos", "watch_os", "desktop")) {
                assertEquals(
                    Platform.UNKNOWN,
                    Platform.fromString(input),
                    "Platform.fromString('$input') should return UNKNOWN"
                )
            }

            // BuildType
            if (normalizedInput !in listOf("release", "debug", "test")) {
                assertEquals(
                    BuildType.UNKNOWN,
                    BuildType.fromString(input),
                    "BuildType.fromString('$input') should return UNKNOWN"
                )
            }

            // Network
            if (normalizedInput !in listOf("mainnet", "testnet", "devnet", "local")) {
                assertEquals(
                    Network.UNKNOWN,
                    Network.fromString(input),
                    "Network.fromString('$input') should return UNKNOWN"
                )
            }

            // BackendIdentity
            if (normalizedInput !in listOf("production", "production_v1", "v1", "staging", "mock")) {
                assertEquals(
                    BackendIdentity.UNSUPPORTED,
                    BackendIdentity.fromString(input),
                    "BackendIdentity.fromString('$input') should return UNSUPPORTED"
                )
            }

            // SignerImplementation
            if (normalizedInput !in listOf("software", "software_local", "keystone", "keystone_hardware", "hardware", "native_hardware")) {
                assertEquals(
                    SignerImplementation.UNSUPPORTED,
                    SignerImplementation.fromString(input),
                    "SignerImplementation.fromString('$input') should return UNSUPPORTED"
                )
            }

            // WalletType
            if (normalizedInput !in listOf("software_mnemonic", "mnemonic", "software_private_key", "private_key", "keystone", "keystone_xpub", "hardware_ble", "ble", "read_only")) {
                assertEquals(
                    WalletType.UNSUPPORTED,
                    WalletType.fromString(input),
                    "WalletType.fromString('$input') should return UNSUPPORTED"
                )
            }
        }
    }

    @Test
    fun testR3_UnknownOrUnsupportedEnumsDefaultDenyInGate() {
        val gate = ReleaseProductionCapabilityGate()

        val baseTemplate = CapabilityRequest.createForTesting(
            operation = Operation.IMPORT_XPUB,
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

        val invalidRequests = listOf(
            baseTemplate.copy(platform = Platform.UNKNOWN),
            baseTemplate.copy(network = Network.UNKNOWN),
            baseTemplate.copy(buildType = BuildType.UNKNOWN),
            baseTemplate.copy(backendIdentity = BackendIdentity.UNSUPPORTED),
            baseTemplate.copy(signerImplementation = SignerImplementation.UNSUPPORTED),
            baseTemplate.copy(walletType = WalletType.UNSUPPORTED)
        )

        for ((index, req) in invalidRequests.withIndex()) {
            val decision = gate.checkCapability(req)
            assertTrue(
                decision is CapabilityDecision.Denied,
                "Request #$index containing UNKNOWN/UNSUPPORTED enum must be denied. Got: $decision"
            )
        }
    }

    // =========================================================================
    // R4: Strict 12-Tuple Allowlist Perturbations in ReleaseProductionCapabilityGate
    // =========================================================================
    @Test
    fun testR4_12TupleSingleFieldPerturbationsDeniedByReleaseGate() {
        val gate = ReleaseProductionCapabilityGate(allowEvmMainnetSend = true, allowBroadcast = true)

        val baselineValidRequest = CapabilityRequest.createForTesting(
            operation = Operation.IMPORT_XPUB,
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

        val baselineDecision = gate.checkCapability(baselineValidRequest)
        assertTrue(
            baselineDecision is CapabilityDecision.Allowed,
            "Baseline request must be allowed, got: $baselineDecision"
        )

        // Single field perturbations across all 12 fields
        val perturbations = listOf(
            "smokeVectorVerified = false" to baselineValidRequest.copy(smokeVectorVerified = false),
            "backendAvailable = false" to baselineValidRequest.copy(backendAvailable = false),
            "buildType = DEBUG" to baselineValidRequest.copy(buildType = BuildType.DEBUG),
            "buildType = TEST" to baselineValidRequest.copy(buildType = BuildType.TEST),
            "buildType = UNKNOWN" to baselineValidRequest.copy(buildType = BuildType.UNKNOWN),
            "backendIdentity = STAGING" to baselineValidRequest.copy(backendIdentity = BackendIdentity.STAGING),
            "backendIdentity = MOCK" to baselineValidRequest.copy(backendIdentity = BackendIdentity.MOCK),
            "backendIdentity = UNSUPPORTED" to baselineValidRequest.copy(backendIdentity = BackendIdentity.UNSUPPORTED),
            "backendVersion = '1.0.1'" to baselineValidRequest.copy(backendVersion = "1.0.1"),
            "backendVersion = ''" to baselineValidRequest.copy(backendVersion = ""),
            "signerImplementation = UNSUPPORTED" to baselineValidRequest.copy(signerImplementation = SignerImplementation.UNSUPPORTED),
            "platform = UNKNOWN" to baselineValidRequest.copy(platform = Platform.UNKNOWN),
            "network = DEVNET" to baselineValidRequest.copy(network = Network.DEVNET),
            "network = LOCAL" to baselineValidRequest.copy(network = Network.LOCAL),
            "network = UNKNOWN" to baselineValidRequest.copy(network = Network.UNKNOWN),
            "walletType = UNSUPPORTED" to baselineValidRequest.copy(walletType = WalletType.UNSUPPORTED),
            "chain = SOLANA" to baselineValidRequest.copy(chain = MultiChainType.SOLANA),
            "chain = MONERO" to baselineValidRequest.copy(chain = MultiChainType.MONERO),
            "chain = BITCOIN" to baselineValidRequest.copy(chain = MultiChainType.BITCOIN),
            "operation = HARDWARE_SIGN_REQUEST with SOFTWARE_LOCAL" to baselineValidRequest.copy(
                operation = Operation.HARDWARE_SIGN_REQUEST,
                signerImplementation = SignerImplementation.SOFTWARE_LOCAL
            )
        )

        for ((description, perturbedReq) in perturbations) {
            val decision = gate.checkCapability(perturbedReq)
            assertTrue(
                decision is CapabilityDecision.Denied,
                "Perturbation '$description' MUST be denied by ReleaseProductionCapabilityGate. Got: $decision"
            )
        }
    }
}

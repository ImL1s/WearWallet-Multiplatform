package com.cbstudio.wearwallet.core.security

import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.usecase.bitcoin.SendBitcoinTransactionUseCase
import com.cbstudio.wearwallet.core.domain.usecase.swap.ExecuteSwapUseCase
import com.cbstudio.wearwallet.core.domain.usecase.transaction.SendTransactionUseCase
import com.cbstudio.wearwallet.core.domain.usecase.utxo.SendUTXOTransactionUseCase
import com.cbstudio.wearwallet.core.domain.usecase.wallet.CreateWalletUseCase
import com.cbstudio.wearwallet.core.domain.usecase.wallet.ImportWalletUseCase
import com.cbstudio.wearwallet.core.domain.model.quantities.EvmEnvelope
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.core.multichain.MultiChainWalletManager
import com.cbstudio.wearwallet.core.multichain.UnifiedWalletManager
import com.cbstudio.wearwallet.core.multichain.sdk.WalletManager
import com.cbstudio.wearwallet.core.swap.SwapExecutor
import org.junit.Assert.*
import org.junit.Test
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.full.valueParameters

/**
 * Empirical stress test for DI Scope Isolation, CapabilityGate security logic,
 * isChainSupported allowlisting, and constructor default parameter absence.
 */
class DiscopeCapabilityGateStressTest {

    @Test
    fun `test_ReleaseProductionCapabilityGate_isChainSupported_strictly_restricts_to_EVM_allowlist`() {
        val gate = ReleaseProductionCapabilityGate(allowEvmMainnetSend = false, allowBroadcast = true)

        val expectedAllowedChains = setOf(
            MultiChainType.ETHEREUM,
            MultiChainType.POLYGON,
            MultiChainType.BSC,
            MultiChainType.ARBITRUM,
            MultiChainType.OPTIMISM,
            MultiChainType.BASE
        )

        // Check MultiChainType allowlist
        for (chain in MultiChainType.values()) {
            if (chain in expectedAllowedChains) {
                assertTrue("Chain $chain MUST be supported in ReleaseProductionCapabilityGate", gate.isChainSupported(chain))
            } else {
                assertFalse("Chain $chain MUST NOT be supported in ReleaseProductionCapabilityGate", gate.isChainSupported(chain))
            }
        }

        // Check ChainType enum overload
        assertTrue(gate.isChainSupported(ChainType.ETHEREUM))
        assertTrue(gate.isChainSupported(ChainType.POLYGON))
        assertTrue(gate.isChainSupported(ChainType.BSC))
        assertTrue(gate.isChainSupported(ChainType.ARBITRUM))
        assertTrue(gate.isChainSupported(ChainType.OPTIMISM))
        assertTrue(gate.isChainSupported(ChainType.BASE))
        assertFalse(gate.isChainSupported(ChainType.SOLANA))
        assertFalse(gate.isChainSupported(ChainType.BITCOIN))
    }

    @Test
    fun `test_ReleaseProductionCapabilityGate_default_deny_policy_for_unauthorized_operations_and_signers`() {
        val gate = ReleaseProductionCapabilityGate(allowEvmMainnetSend = false, allowBroadcast = true)

        // 1. Non-allowlisted chain request MUST be denied
        val invalidChainReq = CapabilityRequest(
            operation = Operation.IMPORT_XPUB,
            chain = MultiChainType.BITCOIN,
            network = Network.MAINNET,
            platform = Platform.WEAR_OS,
            buildType = BuildType.RELEASE,
            envelopeType = EvmEnvelope.LEGACY,
            signerImplementation = SignerImplementation.SOFTWARE_LOCAL,
            walletType = WalletType.READ_ONLY,
            backendIdentity = BackendIdentity.PRODUCTION_V1,
            backendAvailable = true,
            backendVersion = "1.0.0",
            smokeVectorVerified = true
        )
        val resInvalidChain = gate.checkCapability(invalidChainReq)
        assertTrue("BITCOIN chain MUST be denied", resInvalidChain is CapabilityDecision.Denied)

        // 2. Invalid signer implementation MUST be denied
        val invalidSignerReq = CapabilityRequest(
            operation = Operation.IMPORT_XPUB,
            chain = MultiChainType.ETHEREUM,
            network = Network.MAINNET,
            platform = Platform.WEAR_OS,
            buildType = BuildType.RELEASE,
            envelopeType = EvmEnvelope.LEGACY,
            signerImplementation = SignerImplementation.fromString("malicious_signer"),
            walletType = WalletType.READ_ONLY,
            backendIdentity = BackendIdentity.PRODUCTION_V1,
            backendAvailable = true,
            backendVersion = "1.0.0",
            smokeVectorVerified = true
        )
        val resInvalidSigner = gate.checkCapability(invalidSignerReq)
        assertTrue("Invalid signer implementation MUST be denied", resInvalidSigner is CapabilityDecision.Denied)

        // 3. Software mainnet send/sign MUST be denied when allowEvmMainnetSend = false
        val mainnetSoftwareSignReq = CapabilityRequest(
            operation = Operation.SOFTWARE_SIGN,
            chain = MultiChainType.ETHEREUM,
            network = Network.MAINNET,
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
        val resMainnetSign = gate.checkCapability(mainnetSoftwareSignReq)
        assertTrue("Mainnet software sign MUST be denied when allowEvmMainnetSend = false", resMainnetSign is CapabilityDecision.Denied)

        // 4. Hardware signer on mainnet SHOULD be allowed when signer is keystone/hardware
        val mainnetHardwareSignReq = CapabilityRequest(
            operation = Operation.HARDWARE_SIGN_REQUEST,
            chain = MultiChainType.ETHEREUM,
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
        val resHardwareSign = gate.checkCapability(mainnetHardwareSignReq)
        assertTrue("Hardware signer on mainnet SHOULD be allowed", resHardwareSign is CapabilityDecision.Allowed)

        // 5. Broadcast disallowance check when allowBroadcast = false
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
        val resBroadcast = noBroadcastGate.checkCapability(broadcastReq)
        assertTrue("Broadcast MUST be denied when allowBroadcast = false", resBroadcast is CapabilityDecision.Denied)
    }

    @Test
    fun `test_constructor_default_CapabilityGate_absence_across_all_crypto_and_usecase_classes`() {
        val targetClasses: List<KClass<*>> = listOf(
            SendTransactionUseCase::class,
            CreateWalletUseCase::class,
            ImportWalletUseCase::class,
            SendBitcoinTransactionUseCase::class,
            SendUTXOTransactionUseCase::class,
            ExecuteSwapUseCase::class,
            SwapExecutor::class,
            MultiChainWalletManager::class,
            UnifiedWalletManager::class,
            WalletManager::class
        )

        for (kClass in targetClasses) {
            val primaryConstructor = kClass.primaryConstructor
            assertNotNull("Primary constructor for ${kClass.simpleName} MUST exist", primaryConstructor)

            val gateParams = primaryConstructor!!.valueParameters.filter { param ->
                param.type.classifier == CapabilityGate::class
            }

            for (gateParam in gateParams) {
                assertFalse(
                    "Constructor parameter '${gateParam.name}' of type CapabilityGate in ${kClass.simpleName} MUST NOT have a default value (isOptional must be false)",
                    gateParam.isOptional
                )
            }
        }
    }

    @Test
    fun `test_ReleaseProductionCapabilityGate_12_tuple_allowlist_matching_and_default_deny`() {
        val gate = ReleaseProductionCapabilityGate(allowEvmMainnetSend = true, allowBroadcast = true)

        // Valid 12-tuple request -> Allowed
        val validReq = CapabilityRequest(
            operation = Operation.IMPORT_XPUB,
            chain = MultiChainType.ETHEREUM,
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
        val validDecision = gate.checkCapability(validReq)
        assertTrue("Valid 12-tuple request MUST be allowed", validDecision is CapabilityDecision.Allowed)

        // Invalid backendAvailable -> Denied
        val invalidBackendReq = validReq.copy(backendAvailable = false)
        val invalidBackendDecision = gate.checkCapability(invalidBackendReq)
        assertTrue("Request with backendAvailable=false MUST be denied", invalidBackendDecision is CapabilityDecision.Denied)

        // Invalid backendVersion -> Denied
        val invalidVersionReq = validReq.copy(backendVersion = "2.0.0")
        val invalidVersionDecision = gate.checkCapability(invalidVersionReq)
        assertTrue("Request with unsupported backendVersion MUST be denied", invalidVersionDecision is CapabilityDecision.Denied)

        // Invalid smokeVectorVerified -> Denied
        val invalidSmokeReq = validReq.copy(smokeVectorVerified = false)
        val invalidSmokeDecision = gate.checkCapability(invalidSmokeReq)
        assertTrue("Request with smokeVectorVerified=false MUST be denied", invalidSmokeDecision is CapabilityDecision.Denied)

        // Invalid buildType -> Denied
        val invalidBuildReq = validReq.copy(buildType = BuildType.DEBUG)
        val invalidBuildDecision = gate.checkCapability(invalidBuildReq)
        assertTrue("Request with buildType=DEBUG MUST be denied in ReleaseProductionCapabilityGate", invalidBuildDecision is CapabilityDecision.Denied)

        // Invalid backendIdentity -> Denied
        val invalidIdentityReq = validReq.copy(backendIdentity = BackendIdentity.STAGING)
        val invalidIdentityDecision = gate.checkCapability(invalidIdentityReq)
        assertTrue("Request with backendIdentity=STAGING MUST be denied in ReleaseProductionCapabilityGate", invalidIdentityDecision is CapabilityDecision.Denied)
    }
}

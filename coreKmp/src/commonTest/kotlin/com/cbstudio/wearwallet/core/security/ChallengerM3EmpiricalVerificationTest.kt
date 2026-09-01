package com.cbstudio.wearwallet.core.security

import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.core.multichain.MultiChainWalletManager
import com.cbstudio.wearwallet.core.multichain.UnifiedWalletManager
import com.cbstudio.wearwallet.core.multichain.sdk.WalletManager
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChallengerM3EmpiricalVerificationTest {

    private val testMnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"

    @Test
    fun testConstructorParameterEnforcementReflection() {
        // 1. Verify WalletManager constructors
        val wmConstructors = WalletManager::class.java.declaredConstructors
        for (ctor in wmConstructors) {
            val paramTypes = ctor.parameterTypes
            assertFalse(
                paramTypes.size == 1 && paramTypes[0] == String::class.java,
                "WalletManager MUST NOT have a single-parameter constructor (mnemonic: String) without CapabilityGate"
            )
        }
        assertTrue(
            wmConstructors.any { ctor ->
                val params = ctor.parameterTypes
                params.size == 2 && params[0] == String::class.java && CapabilityGate::class.java.isAssignableFrom(params[1])
            },
            "WalletManager MUST have constructor (String, CapabilityGate)"
        )

        // 2. Verify MultiChainWalletManager constructors
        val mcwmConstructors = MultiChainWalletManager::class.java.declaredConstructors
        for (ctor in mcwmConstructors) {
            val paramTypes = ctor.parameterTypes
            assertFalse(
                paramTypes.isEmpty(),
                "MultiChainWalletManager MUST NOT have a zero-parameter default constructor"
            )
        }

        // 3. Verify UnifiedWalletManager constructors
        val uwmConstructors = UnifiedWalletManager::class.java.declaredConstructors
        for (ctor in uwmConstructors) {
            val paramTypes = ctor.parameterTypes
            assertFalse(
                paramTypes.isEmpty(),
                "UnifiedWalletManager MUST NOT have a zero-parameter default constructor"
            )
        }

        // 4. Verify MultiChainWalletManager.createDefault method parameters
        val createDefaultMethods = MultiChainWalletManager.Companion::class.java.declaredMethods.filter { it.name == "createDefault" }
        for (method in createDefaultMethods) {
            val params = method.parameterTypes
            assertFalse(
                params.isEmpty(),
                "MultiChainWalletManager.createDefault() MUST NOT have a zero-parameter overload"
            )
        }
    }

    @Test
    fun testIsChainSupportedMatrix() {
        val releaseGateDefault = ReleaseProductionCapabilityGate(allowEvmMainnetSend = false)
        val releaseGateSendAllowed = ReleaseProductionCapabilityGate(allowEvmMainnetSend = true)
        val devGate = AllowDevCapabilityGate()

        val releaseAllowlisted = setOf(
            MultiChainType.ETHEREUM,
            MultiChainType.POLYGON,
            MultiChainType.BSC,
            MultiChainType.ARBITRUM,
            MultiChainType.OPTIMISM,
            MultiChainType.BASE
        )

        // Test ReleaseProductionCapabilityGate with allowEvmMainnetSend = false
        for (chain in MultiChainType.values()) {
            if (chain in releaseAllowlisted) {
                assertTrue(
                    releaseGateDefault.isChainSupported(chain),
                    "ReleaseProductionCapabilityGate MUST support allowlisted chain $chain when allowEvmMainnetSend=false"
                )
                assertTrue(
                    releaseGateSendAllowed.isChainSupported(chain),
                    "ReleaseProductionCapabilityGate MUST support allowlisted chain $chain when allowEvmMainnetSend=true"
                )
            } else {
                assertFalse(
                    releaseGateDefault.isChainSupported(chain),
                    "ReleaseProductionCapabilityGate MUST NOT support non-allowlisted chain $chain when allowEvmMainnetSend=false"
                )
                assertFalse(
                    releaseGateSendAllowed.isChainSupported(chain),
                    "ReleaseProductionCapabilityGate MUST NOT support non-allowlisted chain $chain when allowEvmMainnetSend=true"
                )
            }
            assertTrue(
                devGate.isChainSupported(chain),
                "AllowDevCapabilityGate MUST support chain $chain"
            )
        }

        // Test ChainType overload
        assertTrue(releaseGateDefault.isChainSupported(ChainType.ETHEREUM))
        assertTrue(releaseGateDefault.isChainSupported(ChainType.SEPOLIA))
        assertTrue(releaseGateDefault.isChainSupported(ChainType.GOERLI))
        assertTrue(releaseGateDefault.isChainSupported(ChainType.MUMBAI))
        assertFalse(releaseGateDefault.isChainSupported(ChainType.BITCOIN))
        assertFalse(releaseGateDefault.isChainSupported(ChainType.MONERO))
        assertFalse(releaseGateDefault.isChainSupported(ChainType.SOLANA))
    }

    @Test
    fun testGetSupportedChainsBehaviorAcrossComponents() = runBlocking {
        val releaseGate = ReleaseProductionCapabilityGate(allowEvmMainnetSend = false)
        val devGate = AllowDevCapabilityGate()

        val expectedAllowlisted = setOf(
            MultiChainType.ETHEREUM,
            MultiChainType.POLYGON,
            MultiChainType.BSC,
            MultiChainType.ARBITRUM,
            MultiChainType.OPTIMISM,
            MultiChainType.BASE
        )

        // 1. WalletManager with ReleaseProductionCapabilityGate
        val wmRelease = WalletManager(testMnemonic, releaseGate)
        assertEquals(expectedAllowlisted, wmRelease.getSupportedChains().toSet())

        // 2. WalletManager with AllowDevCapabilityGate
        val wmDev = WalletManager(testMnemonic, devGate)
        assertEquals(MultiChainType.values().toSet(), wmDev.getSupportedChains().toSet())

        // 3. MultiChainWalletManager with ReleaseProductionCapabilityGate
        // MultiChainWalletManager's default adapters are non-EVM (SOLANA, TRON, POLKADOT, CARDANO, MONERO).
        // Since non-EVM chains are excluded under ReleaseProductionCapabilityGate, getSupportedChains() returns empty set.
        val mcwmRelease = MultiChainWalletManager(releaseGate)
        assertEquals(emptySet(), mcwmRelease.getSupportedChains().toSet())

        // 4. MultiChainWalletManager with AllowDevCapabilityGate
        val mcwmDev = MultiChainWalletManager(devGate)
        val expectedDevAdapters = setOf(
            MultiChainType.SOLANA,
            MultiChainType.TRON,
            MultiChainType.POLKADOT,
            MultiChainType.CARDANO,
            MultiChainType.MONERO
        )
        assertEquals(expectedDevAdapters, mcwmDev.getSupportedChains().toSet())
    }
}

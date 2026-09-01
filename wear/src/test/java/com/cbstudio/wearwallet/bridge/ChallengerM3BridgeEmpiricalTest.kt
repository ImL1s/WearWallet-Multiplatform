package com.cbstudio.wearwallet.bridge

import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.core.security.AllowDevCapabilityGate
import com.cbstudio.wearwallet.core.security.ReleaseProductionCapabilityGate
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ChallengerM3BridgeEmpiricalTest {

    @Test
    fun testCoreKmpBridgeGetSupportedChainsEmpirical() {
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

        // 1. CoreKmpBridge with ReleaseProductionCapabilityGate
        val bridgeRelease = CoreKmpBridge(releaseGate)
        val supportedRelease = bridgeRelease.getSupportedChains()
        assertEquals(expectedAllowlisted, supportedRelease.toSet())
        assertFalse(supportedRelease.contains(MultiChainType.MONERO))
        assertFalse(supportedRelease.contains(MultiChainType.SOLANA))
        assertFalse(supportedRelease.contains(MultiChainType.BITCOIN))

        // 2. CoreKmpBridge with AllowDevCapabilityGate
        val bridgeDev = CoreKmpBridge(devGate)
        val supportedDev = bridgeDev.getSupportedChains()
        assertEquals(MultiChainType.values().toSet(), supportedDev.toSet())
    }
}

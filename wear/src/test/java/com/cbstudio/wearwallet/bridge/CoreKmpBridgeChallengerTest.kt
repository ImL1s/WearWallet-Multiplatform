package com.cbstudio.wearwallet.bridge

import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.core.security.AllowDevCapabilityGate
import com.cbstudio.wearwallet.core.security.ReleaseProductionCapabilityGate
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals

class CoreKmpBridgeChallengerTest {

    @Test
    fun testCoreKmpBridgeGetSupportedChainsBehavior() = runBlocking {
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
        assertEquals(expectedAllowlisted, bridgeRelease.getSupportedChains().toSet())

        // 2. CoreKmpBridge with AllowDevCapabilityGate
        val bridgeDev = CoreKmpBridge(devGate)
        assertEquals(MultiChainType.values().toSet(), bridgeDev.getSupportedChains().toSet())
    }
}

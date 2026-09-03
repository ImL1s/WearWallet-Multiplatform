package com.cbstudio.wearwallet.core.security

import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.core.multichain.sdk.WalletManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class M3AllowlistAndReachabilityTest {

    @Test
    fun testWalletManagerGetSupportedChainsFiltersNonAllowlistedChains() {
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

        assertEquals(expectedAllowlisted.size, supportedChains.size)
        assertEquals(expectedAllowlisted, supportedChains.toSet())

        val excludedChains = listOf(
            MultiChainType.MONERO,
            MultiChainType.SOLANA,
            MultiChainType.BITCOIN,
            MultiChainType.TRON,
            MultiChainType.AVALANCHE,
            MultiChainType.LITECOIN,
            MultiChainType.DOGECOIN,
            MultiChainType.BITCOIN_CASH,
            MultiChainType.FANTOM,
            MultiChainType.CRONOS,
            MultiChainType.CELO,
            MultiChainType.MOONBEAM
        )

        for (chain in excludedChains) {
            assertFalse(
                supportedChains.contains(chain),
                "Non-allowlisted chain $chain MUST be excluded from getSupportedChains()"
            )
        }
    }
}

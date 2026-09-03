package com.cbstudio.wearwallet.core.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChainTypeTest {

    @Test
    fun testRangoChainNameValues() {
        assertEquals("ETH", ChainType.ETHEREUM.rangoChainName)
        assertEquals("BSC", ChainType.BSC.rangoChainName)
        assertEquals("POLYGON", ChainType.POLYGON.rangoChainName)
        assertEquals("ARBITRUM", ChainType.ARBITRUM.rangoChainName)
        assertEquals("OPTIMISM", ChainType.OPTIMISM.rangoChainName)
        assertEquals("AVAX_CCHAIN", ChainType.AVALANCHE.rangoChainName)
        assertEquals("FANTOM", ChainType.FANTOM.rangoChainName)
        assertEquals("BASE", ChainType.BASE.rangoChainName)
        assertEquals("LINEA", ChainType.LINEA.rangoChainName)
        assertEquals("ZKSYNC", ChainType.ZKSYNC.rangoChainName)
        
        // Default fallbacks (should match enum name if not overridden)
        assertEquals("SOLANA", ChainType.SOLANA.rangoChainName)
    }

    @Test
    fun testFromRangoChainName() {
        assertEquals(ChainType.ETHEREUM, ChainType.fromRangoChainName("ETH"))
        assertEquals(ChainType.ETHEREUM, ChainType.fromRangoChainName("ETHEREUM"))
        assertEquals(ChainType.BSC, ChainType.fromRangoChainName("BSC"))
        assertEquals(ChainType.POLYGON, ChainType.fromRangoChainName("POLYGON"))
        assertEquals(ChainType.AVALANCHE, ChainType.fromRangoChainName("AVAX_CCHAIN"))
        assertEquals(ChainType.AVALANCHE, ChainType.fromRangoChainName("AVALANCHE"))
        assertEquals(ChainType.BASE, ChainType.fromRangoChainName("BASE"))
        
        // Case insensitivity
        assertEquals(ChainType.ETHEREUM, ChainType.fromRangoChainName("eth"))
        assertEquals(ChainType.BSC, ChainType.fromRangoChainName("bsc"))
        
        // Invalid name
        assertNull(ChainType.fromRangoChainName("INVALID_CHAIN_NAME"))
    }

    @Test
    fun testFromChainId() {
        assertEquals(ChainType.ETHEREUM, ChainType.fromChainId(1L))
        assertEquals(ChainType.BSC, ChainType.fromChainId(56L))
        assertEquals(ChainType.POLYGON, ChainType.fromChainId(137L))
        assertEquals(ChainType.ARBITRUM, ChainType.fromChainId(42161L))
        assertEquals(ChainType.OPTIMISM, ChainType.fromChainId(10L))
        assertEquals(ChainType.BASE, ChainType.fromChainId(8453L))
        assertEquals(ChainType.AVALANCHE, ChainType.fromChainId(43114L))
        
        // Invalid ID
        assertNull(ChainType.fromChainId(999999L))
    }
}

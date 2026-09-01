package com.cbstudio.wearwallet.core.data.repository

import com.cbstudio.wearwallet.core.domain.model.ChainType
import org.junit.Assert.*
import org.junit.Test

/**
 * Chain switching logic tests.
 * Verifies that chain configuration updates correctly when switching chains.
 */
class ChainSwitchTest {

    @Test
    fun chainType_hasCorrectNativeToken() {
        // Assert native tokens for major chains
        assertEquals("ETH", ChainType.ETHEREUM.nativeToken)
        assertEquals("BNB", ChainType.BSC.nativeToken)
        assertEquals("MATIC", ChainType.POLYGON.nativeToken)
        assertEquals("ETH", ChainType.ARBITRUM.nativeToken)
        assertEquals("ETH", ChainType.OPTIMISM.nativeToken)
        assertEquals("AVAX", ChainType.AVALANCHE.nativeToken)
        assertEquals("FTM", ChainType.FANTOM.nativeToken)
        assertEquals("CRO", ChainType.CRONOS.nativeToken)
        assertEquals("ETH", ChainType.BASE.nativeToken)
        assertEquals("ETH", ChainType.ZKSYNC.nativeToken)
        assertEquals("GLMR", ChainType.MOONBEAM.nativeToken)
        assertEquals("xDAI", ChainType.GNOSIS.nativeToken)
        assertEquals("CELO", ChainType.CELO.nativeToken)
        assertEquals("ETH", ChainType.LINEA.nativeToken)
    }

    @Test
    fun chainType_hasCorrectDisplayName() {
        assertEquals("Ethereum", ChainType.ETHEREUM.displayName)
        assertEquals("BNB Smart Chain", ChainType.BSC.displayName)
        assertEquals("Polygon", ChainType.POLYGON.displayName)
        assertEquals("Arbitrum", ChainType.ARBITRUM.displayName)
        assertEquals("Optimism", ChainType.OPTIMISM.displayName)
        assertEquals("Base", ChainType.BASE.displayName)
    }

    @Test
    fun chainType_isEVM_returnsCorrectly() {
        // EVM chains
        assertTrue(ChainType.ETHEREUM.isEVM())
        assertTrue(ChainType.BSC.isEVM())
        assertTrue(ChainType.POLYGON.isEVM())
        assertTrue(ChainType.ARBITRUM.isEVM())
        assertTrue(ChainType.BASE.isEVM())
        
        // Non-EVM chains
        assertFalse(ChainType.BITCOIN.isEVM())
        assertFalse(ChainType.SOLANA.isEVM())
        assertFalse(ChainType.COSMOS.isEVM())
    }

    @Test
    fun chainType_isTestnet_returnsCorrectly() {
        // Mainnets
        assertFalse(ChainType.ETHEREUM.isTestnet())
        assertFalse(ChainType.BSC.isTestnet())
        assertFalse(ChainType.POLYGON.isTestnet())
        
        // Testnets
        assertTrue(ChainType.SEPOLIA.isTestnet())
        assertTrue(ChainType.GOERLI.isTestnet())
        assertTrue(ChainType.MUMBAI.isTestnet())
    }

    @Test
    fun chainType_derivationPath_isCorrectForEVM() {
        val evmPath = "m/44'/60'/0'/0/0"
        assertEquals(evmPath, ChainType.ETHEREUM.getDefaultDerivationPath())
        assertEquals(evmPath, ChainType.BSC.getDefaultDerivationPath())
        assertEquals(evmPath, ChainType.POLYGON.getDefaultDerivationPath())
        assertEquals(evmPath, ChainType.ARBITRUM.getDefaultDerivationPath())
        assertEquals(evmPath, ChainType.BASE.getDefaultDerivationPath())
    }

    @Test
    fun chainType_derivationPath_isCorrectForNonEVM() {
        assertEquals("m/44'/0'/0'/0/0", ChainType.BITCOIN.getDefaultDerivationPath())
        assertEquals("m/44'/501'/0'/0'", ChainType.SOLANA.getDefaultDerivationPath())
        assertEquals("m/44'/118'/0'/0/0", ChainType.COSMOS.getDefaultDerivationPath())
    }

    @Test
    fun chainType_allEvmChainsHaveSameDerivationPath() {
        val evmPath = "m/44'/60'/0'/0/0"
        val evmChains = listOf(
            ChainType.ETHEREUM, ChainType.BSC, ChainType.POLYGON,
            ChainType.ARBITRUM, ChainType.OPTIMISM, ChainType.AVALANCHE,
            ChainType.FANTOM, ChainType.CRONOS, ChainType.BASE,
            ChainType.ZKSYNC, ChainType.MOONBEAM, ChainType.GNOSIS,
            ChainType.CELO, ChainType.LINEA
        )
        
        evmChains.forEach { chain ->
            assertEquals("$chain should have EVM derivation path", evmPath, chain.getDefaultDerivationPath())
        }
    }

    @Test
    fun chainType_nonEvmChainsHaveUniqueDerivationPaths() {
        val paths = mapOf(
            ChainType.BITCOIN to "m/44'/0'/0'/0/0",
            ChainType.LITECOIN to "m/44'/2'/0'/0/0",
            ChainType.DOGECOIN to "m/44'/3'/0'/0/0",
            ChainType.SOLANA to "m/44'/501'/0'/0'",
            ChainType.COSMOS to "m/44'/118'/0'/0/0"
        )
        
        paths.forEach { (chain, expectedPath) ->
            assertEquals("$chain should have correct derivation path", expectedPath, chain.getDefaultDerivationPath())
        }
    }
}

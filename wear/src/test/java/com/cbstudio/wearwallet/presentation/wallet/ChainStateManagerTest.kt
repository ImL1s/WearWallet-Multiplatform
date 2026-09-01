package com.cbstudio.wearwallet.presentation.wallet

import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.context.ChainSelection
import com.cbstudio.wearwallet.core.domain.model.context.NetworkType
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit test suite for [ChainStateManager].
 * Verifies:
 * 1. Default selection starts at Ethereum Mainnet.
 * 2. `selectChain` updates both `currentSelection` and legacy `currentChain`.
 * 3. `setCurrentChain(chain, networkType)` selects correct testnet / mainnet selection.
 * 4. Legacy `setCurrentChain(chain)` backward compatibility.
 * 5. Helper accessors (currentChainType, currentNetworkType, currentMultiChainType, currentChainId, isTestnet).
 */
class ChainStateManagerTest {

    @Before
    fun setUp() {
        ChainStateManager.selectChain(ChainSelection.default())
    }

    @After
    fun tearDown() {
        ChainStateManager.selectChain(ChainSelection.default())
    }

    @Test
    fun test_default_selection_is_Ethereum_Mainnet() {
        val selection = ChainStateManager.getSelection()
        assertEquals(ChainSelection.ETHEREUM_MAINNET, selection)
        assertEquals(1L, ChainStateManager.currentChainId)
        assertEquals(MultiChainType.ETHEREUM, ChainStateManager.currentMultiChainType)
        assertEquals(NetworkType.MAINNET, ChainStateManager.currentNetworkType)
        assertEquals(ChainType.ETHEREUM, ChainStateManager.getCurrentChain())
        assertEquals(ChainType.ETHEREUM, ChainStateManager.currentChainType)
        assertFalse(ChainStateManager.isTestnet())

        assertEquals(ChainSelection.ETHEREUM_MAINNET, ChainStateManager.currentSelection.value)
        assertEquals(ChainType.ETHEREUM, ChainStateManager.currentChain.value)
    }

    @Test
    fun test_selectChain_updates_selection_and_legacy_currentChain() {
        ChainStateManager.selectChain(ChainSelection.BSC_TESTNET)

        assertEquals(ChainSelection.BSC_TESTNET, ChainStateManager.getSelection())
        assertEquals(97L, ChainStateManager.currentChainId)
        assertEquals(MultiChainType.BSC, ChainStateManager.currentMultiChainType)
        assertEquals(NetworkType.TESTNET, ChainStateManager.currentNetworkType)
        assertEquals(ChainType.BSC, ChainStateManager.getCurrentChain())
        assertTrue(ChainStateManager.isTestnet())

        assertEquals(ChainSelection.BSC_TESTNET, ChainStateManager.currentSelection.value)
        assertEquals(ChainType.BSC, ChainStateManager.currentChain.value)
    }

    @Test
    fun test_setCurrentChain_with_ChainType_and_NetworkType() {
        // Testnet for Polygon should select Polygon Amoy (80002L)
        ChainStateManager.setCurrentChain(ChainType.POLYGON, NetworkType.TESTNET)
        assertEquals(ChainSelection.POLYGON_AMOY, ChainStateManager.getSelection())
        assertEquals(80002L, ChainStateManager.currentChainId)
        assertTrue(ChainStateManager.isTestnet())

        // Mainnet for Avalanche
        ChainStateManager.setCurrentChain(ChainType.AVALANCHE, NetworkType.MAINNET)
        assertEquals(ChainSelection.AVALANCHE_MAINNET, ChainStateManager.getSelection())
        assertEquals(43114L, ChainStateManager.currentChainId)
        assertFalse(ChainStateManager.isTestnet())
    }

    @Test
    fun test_setCurrentChain_legacy_overload_defaults_to_mainnet_except_known_testnet() {
        ChainStateManager.setCurrentChain(ChainType.ARBITRUM)
        assertEquals(ChainSelection.ARBITRUM_MAINNET, ChainStateManager.getSelection())
        assertEquals(42161L, ChainStateManager.currentChainId)

        ChainStateManager.setCurrentChain(ChainType.SEPOLIA)
        assertEquals(ChainSelection.ETHEREUM_SEPOLIA, ChainStateManager.getSelection())
        assertEquals(11155111L, ChainStateManager.currentChainId)
        assertTrue(ChainStateManager.isTestnet())

        ChainStateManager.setCurrentChain(ChainType.MUMBAI)
        assertEquals(ChainSelection.POLYGON_MUMBAI, ChainStateManager.getSelection())
        assertEquals(80001L, ChainStateManager.currentChainId)
        assertTrue(ChainStateManager.isTestnet())
    }

    @Test
    fun test_setSelection_alias() {
        ChainStateManager.setSelection(ChainSelection.BASE_SEPOLIA)
        assertEquals(84532L, ChainStateManager.currentChainId)
        assertEquals(MultiChainType.BASE, ChainStateManager.currentMultiChainType)
        assertEquals(NetworkType.TESTNET, ChainStateManager.currentNetworkType)
    }
}

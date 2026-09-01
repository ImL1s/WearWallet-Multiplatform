package com.cbstudio.wearwallet.core.domain.model.context

import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit test suite for [ChainSelection].
 * Verifies:
 * 1. Invariant validation (chainId > 0, non-blank canonicalContextId).
 * 2. Resolution to [ChainExecutionContext] for all 16 canonical networks.
 * 3. Correctness of factory methods (fromExecutionContext, fromMultiChain, fromChainType, fromChainId).
 * 4. Helper methods (displayName, nativeSymbol, isTestnet, isEvm).
 * 5. JSON Serialization / Deserialization fidelity.
 */
class ChainSelectionTest {

    @Test
    fun test_all_canonical_selections_count_is_16() {
        val all = ChainSelection.allCanonicalSelections()
        assertEquals(16, all.size)

        val mainnets = ChainSelection.mainnetSelections()
        assertEquals(7, mainnets.size)
        assertTrue(mainnets.all { !it.isTestnet() })

        val testnets = ChainSelection.testnetSelections()
        assertEquals(9, testnets.size)
        assertTrue(testnets.all { it.isTestnet() })
    }

    @Test
    fun test_invariants_positive_chainId() {
        // Valid
        val valid = ChainSelection(
            multiChainType = MultiChainType.ETHEREUM,
            networkType = NetworkType.MAINNET,
            chainId = 1L,
            canonicalContextId = "ethereum-mainnet"
        )
        assertEquals(1L, valid.chainId)

        // Invalid: zero chainId
        try {
            ChainSelection(
                multiChainType = MultiChainType.ETHEREUM,
                networkType = NetworkType.MAINNET,
                chainId = 0L,
                canonicalContextId = "ethereum-mainnet"
            )
            fail("Expected IllegalArgumentException for chainId = 0")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("positive") == true)
        }

        // Invalid: negative chainId
        try {
            ChainSelection(
                multiChainType = MultiChainType.ETHEREUM,
                networkType = NetworkType.MAINNET,
                chainId = -1L,
                canonicalContextId = "ethereum-mainnet"
            )
            fail("Expected IllegalArgumentException for negative chainId")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("positive") == true)
        }
    }

    @Test
    fun test_invariants_non_blank_context_id() {
        try {
            ChainSelection(
                multiChainType = MultiChainType.ETHEREUM,
                networkType = NetworkType.MAINNET,
                chainId = 1L,
                canonicalContextId = "  "
            )
            fail("Expected IllegalArgumentException for blank canonicalContextId")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("blank") == true)
        }
    }

    @Test
    fun test_toChainExecutionContext_for_all_16_canonical_selections() {
        for (selection in ChainSelection.allCanonicalSelections()) {
            val context = selection.toChainExecutionContext()
            assertEquals(selection.multiChainType, context.multiChainType)
            assertEquals(selection.networkType, context.networkType)
            assertEquals(selection.chainId, context.chainId)
        }
    }

    @Test
    fun test_canonical_constants_parity() {
        assertEquals(1L, ChainSelection.ETHEREUM_MAINNET.chainId)
        assertEquals(11155111L, ChainSelection.ETHEREUM_SEPOLIA.chainId)
        assertEquals(5L, ChainSelection.ETHEREUM_GOERLI.chainId)

        assertEquals(56L, ChainSelection.BSC_MAINNET.chainId)
        assertEquals(97L, ChainSelection.BSC_TESTNET.chainId)

        assertEquals(137L, ChainSelection.POLYGON_MAINNET.chainId)
        assertEquals(80002L, ChainSelection.POLYGON_AMOY.chainId)
        assertEquals(80001L, ChainSelection.POLYGON_MUMBAI.chainId)

        assertEquals(42161L, ChainSelection.ARBITRUM_MAINNET.chainId)
        assertEquals(421614L, ChainSelection.ARBITRUM_SEPOLIA.chainId)

        assertEquals(10L, ChainSelection.OPTIMISM_MAINNET.chainId)
        assertEquals(11155420L, ChainSelection.OPTIMISM_SEPOLIA.chainId)

        assertEquals(8453L, ChainSelection.BASE_MAINNET.chainId)
        assertEquals(84532L, ChainSelection.BASE_SEPOLIA.chainId)

        assertEquals(43114L, ChainSelection.AVALANCHE_MAINNET.chainId)
        assertEquals(43113L, ChainSelection.AVALANCHE_FUJI.chainId)
    }

    @Test
    fun test_factory_fromExecutionContext() {
        val context = ChainExecutionContextRegistry.resolve(MultiChainType.BSC, isTestnet = true)
        val selection = ChainSelection.fromExecutionContext(context)
        assertEquals(MultiChainType.BSC, selection.multiChainType)
        assertEquals(NetworkType.TESTNET, selection.networkType)
        assertEquals(97L, selection.chainId)
    }

    @Test
    fun test_factory_fromMultiChain() {
        val arbMain = ChainSelection.fromMultiChain(MultiChainType.ARBITRUM, NetworkType.MAINNET)
        assertEquals(42161L, arbMain.chainId)

        val arbSepolia = ChainSelection.fromMultiChain(MultiChainType.ARBITRUM, NetworkType.TESTNET)
        assertEquals(421614L, arbSepolia.chainId)
    }

    @Test
    fun test_factory_fromChainType() {
        val eth = ChainSelection.fromChainType(ChainType.ETHEREUM)
        assertEquals(ChainSelection.ETHEREUM_MAINNET, eth)

        val sepolia = ChainSelection.fromChainType(ChainType.SEPOLIA)
        assertEquals(ChainSelection.ETHEREUM_SEPOLIA, sepolia)

        val mumbai = ChainSelection.fromChainType(ChainType.MUMBAI)
        assertEquals(ChainSelection.POLYGON_MUMBAI, mumbai)

        val polyTestnet = ChainSelection.fromChainType(ChainType.POLYGON, NetworkType.TESTNET)
        assertEquals(80002L, polyTestnet.chainId) // Polygon default testnet is Amoy (80002L)
    }

    @Test
    fun test_factory_fromChainId() {
        val bscMain = ChainSelection.fromChainId(56L)
        assertNotNull(bscMain)
        assertEquals(ChainSelection.BSC_MAINNET, bscMain)

        val amoy = ChainSelection.fromChainId(80002L)
        assertNotNull(amoy)
        assertEquals(ChainSelection.POLYGON_AMOY, amoy)

        val unknown = ChainSelection.fromChainId(99999999L)
        assertNull(unknown)
    }

    @Test
    fun test_display_and_symbols() {
        assertEquals("Ethereum", ChainSelection.ETHEREUM_MAINNET.displayName())
        assertEquals("Sepolia", ChainSelection.ETHEREUM_SEPOLIA.displayName())
        assertEquals("Polygon Amoy", ChainSelection.POLYGON_AMOY.displayName())
        assertEquals("Arbitrum Sepolia", ChainSelection.ARBITRUM_SEPOLIA.displayName())
        assertEquals("Base Sepolia", ChainSelection.BASE_SEPOLIA.displayName())
        assertEquals("Avalanche Fuji", ChainSelection.AVALANCHE_FUJI.displayName())

        assertEquals("ETH", ChainSelection.ETHEREUM_MAINNET.nativeSymbol())
        assertEquals("BNB", ChainSelection.BSC_MAINNET.nativeSymbol())
        assertEquals("POL", ChainSelection.POLYGON_MAINNET.nativeSymbol())
        assertEquals("AVAX", ChainSelection.AVALANCHE_MAINNET.nativeSymbol())
    }

    @Test
    fun test_json_serialization_roundtrip() {
        val original = ChainSelection.BASE_SEPOLIA
        val json = Json.encodeToString(original)
        val decoded = Json.decodeFromString<ChainSelection>(json)

        assertEquals(original, decoded)
        assertEquals(original.chainId, decoded.chainId)
        assertEquals(original.canonicalContextId, decoded.canonicalContextId)
    }
}

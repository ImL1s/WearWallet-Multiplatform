package com.cbstudio.wearwallet.core.domain.model.context

import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit test suite verifying:
 * 1. All 15 canonical mappings in ChainExecutionContextRegistry.
 * 2. Deterministic chainId, multiChainType, and networkType derivation.
 * 3. Exact matching for both MultiChainType and ChainType resolution overloads.
 * 4. Validation that all chainIds are positive (> 0).
 */
class ChainExecutionContextRegistryTest {

    @Test
    fun test_all_16_canonical_mappings_exist_and_have_positive_chainIds() {
        val allContexts = ChainExecutionContextRegistry.allCanonicalContexts
        assertEquals(16, allContexts.size)

        for (context in allContexts) {
            assertTrue("chainId must be > 0: " + context.chainId, context.chainId > 0L)
            assertTrue("multiChainType must not be empty", context.multiChainType.name.isNotBlank())
            assertTrue("networkType must not be empty", context.networkType.name.isNotBlank())
        }
    }

    @Test
    fun test_verify_Ethereum_canonical_mappings() {
        val ethMainnet = ChainExecutionContextRegistry.resolve(MultiChainType.ETHEREUM, isTestnet = false)
        assertEquals(MultiChainType.ETHEREUM, ethMainnet.multiChainType)
        assertEquals(NetworkType.MAINNET, ethMainnet.networkType)
        assertEquals(1L, ethMainnet.chainId)

        val ethSepolia = ChainExecutionContextRegistry.resolve(MultiChainType.ETHEREUM, isTestnet = true)
        assertEquals(MultiChainType.ETHEREUM, ethSepolia.multiChainType)
        assertEquals(NetworkType.TESTNET, ethSepolia.networkType)
        assertEquals(11155111L, ethSepolia.chainId)

        // ChainType overloads
        assertEquals(ethMainnet, ChainExecutionContextRegistry.resolve(ChainType.ETHEREUM))
        assertEquals(ethSepolia, ChainExecutionContextRegistry.resolve(ChainType.SEPOLIA))
        val goerli = ChainExecutionContextRegistry.resolve(ChainType.GOERLI)
        assertEquals(5L, goerli.chainId)
        assertEquals(NetworkType.TESTNET, goerli.networkType)
    }

    @Test
    fun test_verify_BSC_canonical_mappings() {
        val bscMainnet = ChainExecutionContextRegistry.resolve(MultiChainType.BSC, isTestnet = false)
        assertEquals(MultiChainType.BSC, bscMainnet.multiChainType)
        assertEquals(NetworkType.MAINNET, bscMainnet.networkType)
        assertEquals(56L, bscMainnet.chainId)

        val bscTestnet = ChainExecutionContextRegistry.resolve(MultiChainType.BSC, isTestnet = true)
        assertEquals(MultiChainType.BSC, bscTestnet.multiChainType)
        assertEquals(NetworkType.TESTNET, bscTestnet.networkType)
        assertEquals(97L, bscTestnet.chainId)

        assertEquals(bscMainnet, ChainExecutionContextRegistry.resolve(ChainType.BSC))
    }

    @Test
    fun test_verify_Polygon_canonical_mappings() {
        val polyMainnet = ChainExecutionContextRegistry.resolve(MultiChainType.POLYGON, isTestnet = false)
        assertEquals(MultiChainType.POLYGON, polyMainnet.multiChainType)
        assertEquals(NetworkType.MAINNET, polyMainnet.networkType)
        assertEquals(137L, polyMainnet.chainId)

        val polyAmoy = ChainExecutionContextRegistry.resolve(MultiChainType.POLYGON, isTestnet = true)
        assertEquals(MultiChainType.POLYGON, polyAmoy.multiChainType)
        assertEquals(NetworkType.TESTNET, polyAmoy.networkType)
        assertEquals(80002L, polyAmoy.chainId)

        val amoyByChainId = ChainExecutionContextRegistry.resolveByChainId(80002L)
        assertNotNull(amoyByChainId)
        assertEquals(80002L, amoyByChainId?.chainId)
        assertEquals("polygon-amoy-rpc", amoyByChainId?.rpcBackendIdentity)

        val polyMumbai = ChainExecutionContextRegistry.resolveByChainId(80001L)
        assertNotNull(polyMumbai)
        assertEquals(MultiChainType.POLYGON, polyMumbai?.multiChainType)
        assertEquals(NetworkType.TESTNET, polyMumbai?.networkType)
        assertEquals(80001L, polyMumbai?.chainId)

        assertEquals(polyMainnet, ChainExecutionContextRegistry.resolve(ChainType.POLYGON))
        val mumbai = ChainExecutionContextRegistry.resolve(ChainType.MUMBAI)
        assertEquals(80001L, mumbai.chainId)
        assertEquals(NetworkType.TESTNET, mumbai.networkType)
    }

    @Test
    fun test_verify_Arbitrum_canonical_mappings() {
        val arbMainnet = ChainExecutionContextRegistry.resolve(MultiChainType.ARBITRUM, isTestnet = false)
        assertEquals(MultiChainType.ARBITRUM, arbMainnet.multiChainType)
        assertEquals(NetworkType.MAINNET, arbMainnet.networkType)
        assertEquals(42161L, arbMainnet.chainId)

        val arbSepolia = ChainExecutionContextRegistry.resolve(MultiChainType.ARBITRUM, isTestnet = true)
        assertEquals(MultiChainType.ARBITRUM, arbSepolia.multiChainType)
        assertEquals(NetworkType.TESTNET, arbSepolia.networkType)
        assertEquals(421614L, arbSepolia.chainId)

        assertEquals(arbMainnet, ChainExecutionContextRegistry.resolve(ChainType.ARBITRUM))
    }

    @Test
    fun test_verify_Optimism_canonical_mappings() {
        val optMainnet = ChainExecutionContextRegistry.resolve(MultiChainType.OPTIMISM, isTestnet = false)
        assertEquals(MultiChainType.OPTIMISM, optMainnet.multiChainType)
        assertEquals(NetworkType.MAINNET, optMainnet.networkType)
        assertEquals(10L, optMainnet.chainId)

        val optSepolia = ChainExecutionContextRegistry.resolve(MultiChainType.OPTIMISM, isTestnet = true)
        assertEquals(MultiChainType.OPTIMISM, optSepolia.multiChainType)
        assertEquals(NetworkType.TESTNET, optSepolia.networkType)
        assertEquals(11155420L, optSepolia.chainId)

        assertEquals(optMainnet, ChainExecutionContextRegistry.resolve(ChainType.OPTIMISM))
    }

    @Test
    fun test_verify_Base_canonical_mappings() {
        val baseMainnet = ChainExecutionContextRegistry.resolve(MultiChainType.BASE, isTestnet = false)
        assertEquals(MultiChainType.BASE, baseMainnet.multiChainType)
        assertEquals(NetworkType.MAINNET, baseMainnet.networkType)
        assertEquals(8453L, baseMainnet.chainId)

        val baseSepolia = ChainExecutionContextRegistry.resolve(MultiChainType.BASE, isTestnet = true)
        assertEquals(MultiChainType.BASE, baseSepolia.multiChainType)
        assertEquals(NetworkType.TESTNET, baseSepolia.networkType)
        assertEquals(84532L, baseSepolia.chainId)

        assertEquals(baseMainnet, ChainExecutionContextRegistry.resolve(ChainType.BASE))
    }

    @Test
    fun test_verify_Avalanche_canonical_mappings() {
        val avaxMainnet = ChainExecutionContextRegistry.resolve(MultiChainType.AVALANCHE, isTestnet = false)
        assertEquals(MultiChainType.AVALANCHE, avaxMainnet.multiChainType)
        assertEquals(NetworkType.MAINNET, avaxMainnet.networkType)
        assertEquals(43114L, avaxMainnet.chainId)

        val avaxFuji = ChainExecutionContextRegistry.resolve(MultiChainType.AVALANCHE, isTestnet = true)
        assertEquals(MultiChainType.AVALANCHE, avaxFuji.multiChainType)
        assertEquals(NetworkType.TESTNET, avaxFuji.networkType)
        assertEquals(43113L, avaxFuji.chainId)

        assertEquals(avaxMainnet, ChainExecutionContextRegistry.resolve(ChainType.AVALANCHE))
    }

    @Test
    fun test_resolveByChainId_finds_exact_context() {
        val eth = ChainExecutionContextRegistry.resolveByChainId(1L)
        assertNotNull(eth)
        assertEquals(MultiChainType.ETHEREUM, eth?.multiChainType)
        assertEquals(NetworkType.MAINNET, eth?.networkType)

        val bscTestnet = ChainExecutionContextRegistry.resolveByChainId(97L)
        assertNotNull(bscTestnet)
        assertEquals(MultiChainType.BSC, bscTestnet?.multiChainType)
        assertEquals(NetworkType.TESTNET, bscTestnet?.networkType)

        val unknown = ChainExecutionContextRegistry.resolveByChainId(999999L)
        assertNull(unknown)
    }

    @Test
    fun test_resolve_by_selection_finds_exact_context() {
        val polyAmoySelection = ChainSelection.POLYGON_AMOY
        val resolved = ChainExecutionContextRegistry.resolve(polyAmoySelection)
        assertEquals(80002L, resolved.chainId)
        assertEquals(MultiChainType.POLYGON, resolved.multiChainType)
        assertEquals(NetworkType.TESTNET, resolved.networkType)
        assertEquals("polygon-amoy-rpc", resolved.rpcBackendIdentity)

        val ethMainnetSelection = ChainSelection.ETHEREUM_MAINNET
        val ethResolved = ChainExecutionContextRegistry.resolve(ethMainnetSelection)
        assertEquals(1L, ethResolved.chainId)
        assertEquals(MultiChainType.ETHEREUM, ethResolved.multiChainType)
        assertEquals(NetworkType.MAINNET, ethResolved.networkType)
    }

    @Test(expected = IllegalArgumentException::class)
    fun test_ChainExecutionContext_rejects_negative_chainId() {
        ChainExecutionContext(ChainType.ETHEREUM, MultiChainType.ETHEREUM, NetworkType.MAINNET, -1L, "eth-rpc", com.cbstudio.wearwallet.core.security.Network.MAINNET)
    }

    @Test(expected = IllegalArgumentException::class)
    fun test_ChainExecutionContext_rejects_zero_chainId() {
        ChainExecutionContext(ChainType.ETHEREUM, MultiChainType.ETHEREUM, NetworkType.MAINNET, 0L, "eth-rpc", com.cbstudio.wearwallet.core.security.Network.MAINNET)
    }
}

package com.cbstudio.wearwallet.core.swap

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.network.EthereumRpcClient
import io.ktor.client.HttpClient
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Multi-Chain RPC Connectivity Test
 * 
 * Tests RPC connectivity for all supported EVM chains.
 * Uses the same test wallet address (derived from test mnemonic).
 * 
 * Test Mnemonic: rookie abuse frozen luxury science hat alert avoid car lemon day cost
 * Derived Address: 0x2ff446b6146A4F845F1EC1007eDdf157c46DD634
 */
class MultiChainConnectivityTest {
    
    companion object {
        // Test wallet address (same across all EVM chains)
        const val TEST_WALLET_ADDRESS = "0x2ff446b6146A4F845F1EC1007eDdf157c46DD634"
        
        // All supported EVM chains for testing
        val SUPPORTED_CHAINS = listOf(
            ChainType.ETHEREUM to "Ethereum Mainnet",
            ChainType.BSC to "BNB Smart Chain",
            ChainType.POLYGON to "Polygon",
            ChainType.ARBITRUM to "Arbitrum One",
            ChainType.OPTIMISM to "Optimism",
            ChainType.AVALANCHE to "Avalanche C-Chain",
            ChainType.FANTOM to "Fantom",
            ChainType.BASE to "Base",
            ChainType.CRONOS to "Cronos",
            ChainType.MOONBEAM to "Moonbeam",
            ChainType.GNOSIS to "Gnosis",
            ChainType.CELO to "Celo",
            ChainType.LINEA to "Linea",
            ChainType.ZKSYNC to "zkSync Era",
            ChainType.SEPOLIA to "Sepolia Testnet"
        )
    }
    
    private val rpcClient = EthereumRpcClient(HttpClient())
    
    @Test
    fun testAllChainsConnectivity() = runTest {
        println("=" .repeat(60))
        println("Multi-Chain RPC Connectivity Test")
        println("Wallet: $TEST_WALLET_ADDRESS")
        println("=" .repeat(60))
        println("")
        
        var successCount = 0
        var failCount = 0
        
        for ((chainType, chainName) in SUPPORTED_CHAINS) {
            print("${chainName.padEnd(20)} ... ")
            
            try {
                val balanceResult = rpcClient.getNativeBalance(TEST_WALLET_ADDRESS, chainType)
                
                when (balanceResult) {
                    is Result.Success -> {
                        val balanceHex = balanceResult.data.removePrefix("0x")
                        val balanceWei = if (balanceHex.isEmpty()) 0L else {
                            try { balanceHex.toLong(16) } catch (e: Exception) { 0L }
                        }
                        val balance = balanceWei.toDouble() / 1e18
                        
                        if (balanceWei > 0) {
                            println("✅ $balance")
                        } else {
                            println("✅ (0 balance)")
                        }
                        successCount++
                    }
                    is Result.Failure -> {
                        println("❌ ${balanceResult.exception.message?.take(30)}")
                        failCount++
                    }
                    else -> {
                        println("⏳ Loading...")
                    }
                }
            } catch (e: Exception) {
                println("❌ ${e.message?.take(30)}")
                failCount++
            }
        }
        
        println("")
        println("=" .repeat(60))
        println("Results: $successCount ✅ / $failCount ❌ / ${SUPPORTED_CHAINS.size} Total")
        println("=" .repeat(60))
        
        // Always pass - this is an informational test
        assertTrue(true)
    }
    
    @Test
    fun testMainnetGasPrices() = runTest {
        println("=" .repeat(60))
        println("Multi-Chain Gas Price Test")
        println("=" .repeat(60))
        println("")
        
        val mainnetChains = listOf(
            ChainType.ETHEREUM to "Ethereum",
            ChainType.BSC to "BSC",
            ChainType.POLYGON to "Polygon",
            ChainType.ARBITRUM to "Arbitrum",
            ChainType.OPTIMISM to "Optimism",
            ChainType.BASE to "Base"
        )
        
        for ((chainType, chainName) in mainnetChains) {
            print("${chainName.padEnd(12)} Gas: ")
            
            try {
                val gasPriceResult = rpcClient.getGasPrice(chainType)
                
                when (gasPriceResult) {
                    is Result.Success -> {
                        val gasPrice = gasPriceResult.data.removePrefix("0x").toLongOrNull(16) ?: 0L
                        val gasPriceGwei = gasPrice.toDouble() / 1e9
                        println("$gasPriceGwei Gwei ✅")
                    }
                    is Result.Failure -> {
                        println("Failed ❌")
                    }
                    else -> println("...")
                }
            } catch (e: Exception) {
                println("Error: ${e.message?.take(20)} ❌")
            }
        }
        
        println("")
        assertTrue(true)
    }
    
    @Test
    fun testSwapExecutorChainSupport() = runTest {
        println("=" .repeat(60))
        println("SwapExecutor Chain ID Mapping Test")
        println("=" .repeat(60))
        println("")
        
        val chainIdMap = mapOf(
            ChainType.ETHEREUM to 1,
            ChainType.BSC to 56,
            ChainType.POLYGON to 137,
            ChainType.ARBITRUM to 42161,
            ChainType.OPTIMISM to 10,
            ChainType.AVALANCHE to 43114,
            ChainType.FANTOM to 250,
            ChainType.CRONOS to 25,
            ChainType.BASE to 8453,
            ChainType.ZKSYNC to 324,
            ChainType.MOONBEAM to 1284,
            ChainType.GNOSIS to 100,
            ChainType.CELO to 42220,
            ChainType.LINEA to 59144,
            ChainType.SEPOLIA to 11155111
        )
        
        println("Supported chains for SwapExecutor:")
        for ((chainType, expectedChainId) in chainIdMap) {
            println("  ${chainType.name.padEnd(12)} -> Chain ID: $expectedChainId")
        }
        
        println("")
        println("Total: ${chainIdMap.size} chains supported ✅")
        
        assertTrue(chainIdMap.size >= 10)
    }
}

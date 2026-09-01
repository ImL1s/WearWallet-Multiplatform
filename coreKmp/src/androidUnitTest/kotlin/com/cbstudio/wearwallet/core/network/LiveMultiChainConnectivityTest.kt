package com.cbstudio.wearwallet.core.network

import com.cbstudio.wearwallet.core.domain.model.ChainType
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*

/**
 * Multi-Chain Connectivity Test
 * 
 * Tests that all supported chains can be queried for balance.
 * Uses mock responses to verify the RPC client handles each chain correctly.
 */
class MultiChainConnectivityTest {
    
    companion object {
        // Test wallet address
        const val TEST_WALLET = "0x742d35Cc6634C0532925a3b844Bc9e7595f3B4E0"
        
        // Sample hex balance response
        const val SAMPLE_BALANCE_HEX = "0x2386f26fc10000"
    }
    
    private fun createMockRpcClient(): EthereumRpcClient {
        val rpcResponse = """
            {
                "jsonrpc": "2.0",
                "id": 1,
                "result": "$SAMPLE_BALANCE_HEX"
            }
        """.trimIndent()

        val mockEngine = MockEngine { request ->
            respond(
                content = ByteReadChannel(rpcResponse),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        
        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        
        return EthereumRpcClient(httpClient)
    }

    @Test
    fun allEvmMainnets_handleBalanceRequest() = runBlocking {
        val rpcClient = createMockRpcClient()
        
        val evmMainnets = listOf(
            ChainType.ETHEREUM,
            ChainType.BSC,
            ChainType.POLYGON,
            ChainType.ARBITRUM,
            ChainType.OPTIMISM,
            ChainType.AVALANCHE,
            ChainType.FANTOM,
            ChainType.CRONOS,
            ChainType.BASE,
            ChainType.ZKSYNC,
            ChainType.MOONBEAM,
            ChainType.GNOSIS,
            ChainType.CELO,
            ChainType.LINEA
        )
        
        val results = mutableMapOf<ChainType, Boolean>()
        
        evmMainnets.forEach { chain ->
            val result = rpcClient.getNativeBalance(TEST_WALLET, chain)
            val success = result.isSuccess()
            results[chain] = success
            println("${chain.displayName}: ${if (success) "✅" else "❌"}")
        }
        
        val successCount = results.count { it.value }
        println("\n=== EVM Mainnets ===")
        println("$successCount / ${evmMainnets.size} chains handled correctly")
        
        assertEquals("All EVM mainnets should work", evmMainnets.size, successCount)
    }

    @Test
    fun allTestnets_handleBalanceRequest() = runBlocking {
        val rpcClient = createMockRpcClient()
        
        val testnets = listOf(
            ChainType.SEPOLIA,
            ChainType.GOERLI,
            ChainType.MUMBAI
        )
        
        val results = mutableMapOf<ChainType, Boolean>()
        
        testnets.forEach { chain ->
            val result = rpcClient.getNativeBalance(TEST_WALLET, chain)
            val success = result.isSuccess()
            results[chain] = success
            println("${chain.displayName}: ${if (success) "✅" else "❌"}")
        }
        
        val successCount = results.count { it.value }
        println("\n=== Testnets ===")
        println("$successCount / ${testnets.size} chains handled correctly")
        
        assertEquals("All testnets should work", testnets.size, successCount)
    }
    
    @Test
    fun chainType_allChainsHaveValidConfig() {
        // Verify all chains have required configuration
        val allChains = ChainType.entries.filter { it.isEVM() }
        
        allChains.forEach { chain ->
            assertNotNull("${chain.name} should have displayName", chain.displayName)
            assertNotNull("${chain.name} should have nativeToken", chain.nativeToken)
            assertTrue("${chain.name} nativeToken should not be empty", chain.nativeToken.isNotBlank())
            assertTrue("${chain.name} displayName should not be empty", chain.displayName.isNotBlank())
        }
        
        println("All ${allChains.size} EVM chains have valid configuration ✅")
    }
}

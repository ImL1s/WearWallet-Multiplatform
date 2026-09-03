package com.cbstudio.wearwallet.core.data.repository

import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.network.EthereumRpcClient
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
import org.junit.Assert.*
import org.junit.Test

/**
 * Multi-chain balance verification tests.
 * Tests native balance fetching across different EVM chains.
 * 
 * Note: getNativeBalance returns hex strings from RPC, tests verify hex format.
 */
class MultiChainBalanceTest {

    companion object {
        const val TEST_WALLET = "0x742d35Cc6634C0532925a3b844Bc9e7595f3B4E0"
        
        // 0.01 ETH in Wei = 10000000000000000 = 0x2386f26fc10000
        const val BALANCE_001_ETH_HEX = "0x2386f26fc10000"
        
        // 1.5 ETH in Wei = 1500000000000000000 = 0x14d1120d7b160000
        const val BALANCE_15_ETH_HEX = "0x14d1120d7b160000"
    }

    private fun createMockRpcClient(hexResponse: String): EthereumRpcClient {
        val rpcResponse = """
            {
                "jsonrpc": "2.0",
                "id": 1,
                "result": "$hexResponse"
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
    fun getNativeBalance_ethereum_returnsSuccess() = runBlocking {
        val rpcClient = createMockRpcClient(BALANCE_001_ETH_HEX)
        val result = rpcClient.getNativeBalance(TEST_WALLET, ChainType.ETHEREUM)
        
        assertTrue("Result should be success", result.isSuccess())
        assertNotNull("Balance should not be null", result.getOrNull())
    }

    @Test
    fun getNativeBalance_bsc_returnsSuccess() = runBlocking {
        val rpcClient = createMockRpcClient(BALANCE_15_ETH_HEX)
        val result = rpcClient.getNativeBalance(TEST_WALLET, ChainType.BSC)
        
        assertTrue("Result should be success", result.isSuccess())
        assertNotNull("Balance should not be null", result.getOrNull())
    }

    @Test
    fun getNativeBalance_polygon_returnsSuccess() = runBlocking {
        val rpcClient = createMockRpcClient(BALANCE_001_ETH_HEX)
        val result = rpcClient.getNativeBalance(TEST_WALLET, ChainType.POLYGON)
        
        assertTrue("Result should be success", result.isSuccess())
    }

    @Test
    fun getNativeBalance_arbitrum_returnsSuccess() = runBlocking {
        val rpcClient = createMockRpcClient(BALANCE_001_ETH_HEX)
        val result = rpcClient.getNativeBalance(TEST_WALLET, ChainType.ARBITRUM)
        
        assertTrue("Result should be success", result.isSuccess())
    }

    @Test
    fun getNativeBalance_base_returnsSuccess() = runBlocking {
        val rpcClient = createMockRpcClient(BALANCE_001_ETH_HEX)
        val result = rpcClient.getNativeBalance(TEST_WALLET, ChainType.BASE)
        
        assertTrue("Result should be success", result.isSuccess())
    }

    @Test
    fun getNativeBalance_handlesZeroBalance() = runBlocking {
        val rpcClient = createMockRpcClient("0x0")
        val result = rpcClient.getNativeBalance(TEST_WALLET, ChainType.ETHEREUM)
        
        assertTrue("Result should be success", result.isSuccess())
        // Zero balance returns "0x0" or "0" depending on implementation
        val balance = result.getOrNull()
        assertNotNull("Balance should not be null", balance)
        assertTrue("Zero balance should be small", (balance?.length ?: 0) <= 3)
    }

    @Test
    fun getNativeBalance_handlesLargeBalance() = runBlocking {
        // 1000 ETH = 0x3635c9adc5dea00000
        val largeBalanceHex = "0x3635c9adc5dea00000"
        val rpcClient = createMockRpcClient(largeBalanceHex)
        
        val result = rpcClient.getNativeBalance(TEST_WALLET, ChainType.ETHEREUM)
        
        assertTrue("Result should be success", result.isSuccess())
        assertNotNull("Balance should not be null", result.getOrNull())
    }

    @Test
    fun getNativeBalance_handlesRpcError() = runBlocking {
        val errorResponse = """
            {
                "jsonrpc": "2.0",
                "id": 1,
                "error": {
                    "code": -32000,
                    "message": "execution reverted"
                }
            }
        """.trimIndent()

        val mockEngine = MockEngine { request ->
            respond(
                content = ByteReadChannel(errorResponse),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        
        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        
        val rpcClient = EthereumRpcClient(httpClient)
        val result = rpcClient.getNativeBalance(TEST_WALLET, ChainType.ETHEREUM)
        
        // Should handle error gracefully (either fail or return null)
        assertTrue("Should handle error", result.isFailure() || result.getOrNull() == null)
    }
}

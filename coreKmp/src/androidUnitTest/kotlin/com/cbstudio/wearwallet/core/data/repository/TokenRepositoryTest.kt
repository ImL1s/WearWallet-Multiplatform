package com.cbstudio.wearwallet.core.data.repository

import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.network.EthereumRpcClient
import com.cbstudio.wearwallet.core.network.PriceApiClient
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
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock

class TokenRepositoryTest {

    private lateinit var priceApiClient: PriceApiClient
    private lateinit var tokenRepository: TokenRepositoryImpl

    @Before
    fun setup() {
        priceApiClient = mock()
    }

    @Test
    fun getTokenBalance_correctlyParsesSmallBalance() = runBlocking {
        // Arrange
        val hexBalance = "0x2710" // 10000 decimal
        val rpcResponse = """
            {
                "jsonrpc": "2.0",
                "id": 1,
                "result": "$hexBalance"
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
        val rpcClient = EthereumRpcClient(httpClient)

        tokenRepository = TokenRepositoryImpl(
            rpcClient = rpcClient,
            priceApiClient = priceApiClient,
            database = com.cbstudio.wearwallet.core.database.createInMemoryCoreWalletDatabase()
        )

        // Act
        val balance = tokenRepository.getTokenBalance("0xWallet", "0xToken", ChainType.ETHEREUM)

        // Assert
        assertEquals("10000", balance)
    }

    @Test
    fun getTokenBalance_correctlyParsesLargeBalance() = runBlocking {
        // Arrange
        // 1000 ETH (18 decimals) = 1 * 10^21 Wei
        // 10^21 = 3635C9ADC5DEA00000 (hex)
        // Let's use a very large number > Long.MAX_VALUE (9.22 * 10^18)
        // 10^20 is enough.
        // 0x56BC75E2D63100000 (100 * 10^18) = 100 quintillion wei
        val largeBalanceVal = "100000000000000000000" // 100 ETH (10^20)
        val hexBalance = "0x" + com.ionspin.kotlin.bignum.integer.BigInteger.parseString(largeBalanceVal).toString(16)
        
        val rpcResponse = """
            {
                "jsonrpc": "2.0",
                "id": 1,
                "result": "$hexBalance"
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
        val rpcClient = EthereumRpcClient(httpClient)

        tokenRepository = TokenRepositoryImpl(
            rpcClient = rpcClient,
            priceApiClient = priceApiClient,
            database = com.cbstudio.wearwallet.core.database.createInMemoryCoreWalletDatabase()
        )

        // Act
        val balance = tokenRepository.getTokenBalance("0xWallet", "0xToken", ChainType.ETHEREUM)

        // Assert
        assertEquals(largeBalanceVal, balance)
    }
}

package com.cbstudio.wearwallet.core.data.repository

import com.cbstudio.wearwallet.core.database.DatabaseDriverFactory
import com.cbstudio.wearwallet.core.security.CryptoProvider
import com.cbstudio.wearwallet.core.network.EthereumRpcClient
import com.cbstudio.wearwallet.core.domain.model.ChainType
import app.cash.sqldelight.db.SqlDriver
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
import org.mockito.kotlin.whenever

class WalletRepositoryTest {

    private lateinit var databaseDriverFactory: DatabaseDriverFactory
    private lateinit var cryptoProvider: CryptoProvider
    private lateinit var walletRepository: WalletRepositoryImpl
    private lateinit var mockSqlDriver: SqlDriver

    @Before
    fun setup() {
        databaseDriverFactory = mock()
        cryptoProvider = mock()
        mockSqlDriver = mock()

        whenever(databaseDriverFactory.createDriver()).thenReturn(mockSqlDriver)
    }

    @Test
    fun getNativeBalance_correctlyParses_0_01_ETH_from_RPC_response() = runBlocking {
        // Arrange
        // 0x2386F26FC10000 = 0.01 ETH (10000000000000000 Wei)
        val hexBalance = "0x2386F26FC10000" 
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
        val ethereumRpcClient = EthereumRpcClient(httpClient)

        walletRepository = WalletRepositoryImpl(
            databaseDriverFactory = databaseDriverFactory,
            cryptoProvider = cryptoProvider,
            ethereumRpcClient = ethereumRpcClient,
            secureKeyManager = com.cbstudio.wearwallet.core.security.FakeSecureKeyManager(),
            platformDeletionCleanupHook = com.cbstudio.wearwallet.core.platform.NoOpPlatformDeletionCleanupHook()
        )

        // Act
        val balance = walletRepository.getNativeBalance("0x123", ChainType.ETHEREUM)

        // Assert
        // 10000000000000000 / 10^18 = 0.01
        assertEquals(0.01, balance, 0.0)
    }
}

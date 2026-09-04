package com.cbstudio.wearwallet.core.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.cbstudio.wearwallet.core.database.CoreWalletDatabase
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.Token
import com.cbstudio.wearwallet.core.network.EthereumRpcClient
import com.cbstudio.wearwallet.core.network.PriceApiClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

class TokenRepositoryPersistenceTest {

    private val walletAddress = "0x1111111111111111111111111111111111111111"
    private val customToken = Token(
        address = "0x2222222222222222222222222222222222222222",
        name = "Custom Token",
        symbol = "CUST",
        decimals = 18,
        chainType = ChainType.ETHEREUM,
        balance = "0"
    )

    private fun createDatabase(): CoreWalletDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CoreWalletDatabase.Schema.create(driver)
        return CoreWalletDatabase(driver)
    }

    private fun insertWallet(database: CoreWalletDatabase, address: String = walletAddress) {
        database.walletQueries.insert(
            name = "Persist Wallet",
            address = address,
            public_key = "0x04pubkey",
            encrypted_private_key = "WWEN_V2_PAYLOAD",
            encrypted_mnemonic = null,
            derivation_path = "m/44'/60'/0'/0/0",
            chain_type = "ETHEREUM",
            wallet_type = "HOT_WALLET",
            is_watch_only = 0L,
            master_fingerprint = null,
            keystone_sign_request = null,
            keystone_sync_data = null,
            metadata = "{}",
            avatar_id = null,
            chain_id = 1L,
            key_alias = "ww_key_persist",
            key_backend = "KEYSTORE",
            key_format_version = 2L,
            requires_auth = 1L,
            is_deletion_pending = 0L
        )
    }

    private fun createRepository(database: CoreWalletDatabase): TokenRepositoryImpl {
        val mockEngine = MockEngine {
            respond(
                content = ByteReadChannel(
                    """{"jsonrpc":"2.0","id":1,"result":"0x0"}"""
                ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        return TokenRepositoryImpl(
            rpcClient = EthereumRpcClient(httpClient),
            priceApiClient = PriceApiClient(httpClient),
            database = database
        )
    }

    @Test
    fun saveUserToken_persistsAcrossRepositoryInstances() = runTest {
        val database = createDatabase()
        insertWallet(database)

        createRepository(database).saveUserToken(walletAddress, customToken)

        val reopened = createRepository(database)
        val tokens = reopened.scanUserTokens(walletAddress, ChainType.ETHEREUM)

        assertTrue(
            tokens.any { it.address.equals(customToken.address, ignoreCase = true) && it.symbol == "CUST" },
            "Persisted custom token must appear after reopening repository"
        )
    }

    @Test
    fun saveUserToken_withoutWalletRow_failsClosed() = runTest {
        val database = createDatabase()
        val repository = createRepository(database)

        assertFailsWith<IllegalStateException> {
            repository.saveUserToken(walletAddress, customToken)
        }
        assertTrue(database.tokenQueries.selectByWalletId(1).executeAsList().isEmpty())
    }

    @Test
    fun removeUserToken_persistsDeletion() = runTest {
        val database = createDatabase()
        insertWallet(database)
        val repository = createRepository(database)
        repository.saveUserToken(walletAddress, customToken)

        repository.removeUserToken(walletAddress, customToken.address)

        val reopened = createRepository(database)
        val tokens = reopened.scanUserTokens(walletAddress, ChainType.ETHEREUM)
        assertTrue(tokens.none { it.address.equals(customToken.address, ignoreCase = true) })
    }
}

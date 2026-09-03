package io.github.iml1s.client

import io.github.iml1s.client.ethereum.EvmJsonRpcClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class EvmClientTest {

    @Test
    fun testEvmGetBalance() = runTest {
        val mockEngine = MockEngine { request ->
            // {"jsonrpc":"2.0","id":1,"result":"0x... value"}
            respond(
                content = ByteReadChannel("""
                    {
                        "jsonrpc": "2.0",
                        "id": 1,
                        "result": "0x4563918244f40000" 
                    }
                """.trimIndent()), // 5 ETH = 5 000 000 000 000 000 000 wei = 4563918244f40000 hex
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val evmClient = EvmJsonRpcClient(client, "http://localhost:8545")
        val balance = evmClient.getBalance("0x123")
        
        assertEquals(5000000000000000000L, balance)
    }
}

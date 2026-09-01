package io.github.iml1s.client

import io.github.iml1s.client.bitcoin.MempoolSpaceClient
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

class ClientTest {

    @Test
    fun testMempoolSpaceGetUTXOs() = runTest {
        val mockEngine = MockEngine { request ->
            respond(
                content = ByteReadChannel("""
                    [
                        {
                            "txid": "7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f",
                            "vout": 0,
                            "status": {
                                "confirmed": true,
                                "block_height": 100000,
                                "block_hash": "000000...",
                                "block_time": 1234567890
                            },
                            "value": 50000
                        }
                    ]
                """.trimIndent()),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val btcClient = MempoolSpaceClient(client)
        val utxos = btcClient.getUTXOs("dummy_address")

        assertEquals(1, utxos.size)
        assertEquals("7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f", utxos[0].txid)
        assertEquals(50000L, utxos[0].value)
    }
}

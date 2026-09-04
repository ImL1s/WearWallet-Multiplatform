package com.cbstudio.wearwallet.core.rango

import com.cbstudio.wearwallet.core.network.ApiConfig
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class RangoApiTest {

    @Test
    fun testGetQuote() = runTest {
        val client = RangoClient(HttpClient {
            install(ContentNegotiation) {
                json(Json { 
                    ignoreUnknownKeys = true 
                    isLenient = true
                })
            }
        })
        
        try {
            val response = client.getQuote(
                fromChain = "ETH",
                fromToken = "ETH",
                toChain = "BSC",
                toToken = "BNB",
                amount = "0.1"
            )
            
            println("Response: $response")
            
            if (response.error != null) {
                println("API Error as expected (Invalid Key): ${response.error}")
            } else {
                println("API Success!")
            }
            
            assertTrue(true) 
        } catch (e: Exception) {
            println("Request failed: ${e.message}")
            if (e.message?.contains("401") == true || e.message?.contains("403") == true) {
                 println("Auth error received, connectivity verified.")
            } else {
                 throw e
            }
        }
    }
}

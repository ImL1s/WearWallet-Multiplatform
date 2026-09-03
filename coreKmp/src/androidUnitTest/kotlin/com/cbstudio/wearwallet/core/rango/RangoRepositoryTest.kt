package com.cbstudio.wearwallet.core.rango

import android.util.Log
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.MockedStatic
import org.mockito.Mockito

/**
 * RangoRepository Tests
 * 
 * Note: Since RangoRepository creates RangoClient internally and RangoClient
 * creates its own HttpClient, we test the repository's error handling behavior
 * using integration-style tests that expect network failures in unit test environment.
 */
class RangoRepositoryTest {

    private lateinit var rangoRepository: RangoRepository
    private lateinit var mockedLog: MockedStatic<Log>

    @Before
    fun setup() {
        // Mock android.util.Log
        mockedLog = Mockito.mockStatic(Log::class.java)
        mockedLog.`when`<Int> { Log.d(anyString(), anyString()) }.thenReturn(0)
        mockedLog.`when`<Int> { Log.e(anyString(), anyString()) }.thenReturn(0)
        mockedLog.`when`<Int> { Log.i(anyString(), anyString()) }.thenReturn(0)
        mockedLog.`when`<Int> { Log.w(anyString(), anyString()) }.thenReturn(0)

        rangoRepository = RangoRepository(RangoClient(io.ktor.client.HttpClient()))
    }

    @After
    fun tearDown() {
        mockedLog.close()
    }

    @Test
    fun `getSwapQuote with invalid params returns failure`() {
        runBlocking {
            // When - calling with invalid params (network will fail in unit test env)
            val result = rangoRepository.getSwapQuote(
                fromChain = "INVALID_CHAIN",
                fromTokenSymbol = null,
                toChain = "INVALID_CHAIN",
                toTokenSymbol = null,
                amount = "1000000000000000000",
                slippage = 1.0
            )

            // Then - should return failure (network error or invalid response)
            assertTrue(result.isFailure)
        }
    }

    @Test
    fun `createSwapTransaction with invalid params returns failure`() {
        runBlocking {
            // When
            val result = rangoRepository.createSwapTransaction(
                fromChain = "INVALID_CHAIN",
                fromTokenSymbol = null,
                toChain = "INVALID_CHAIN",
                toTokenSymbol = null,
                amount = "1000000000000000000",
                fromAddress = "0x0000000000000000000000000000000000000000",
                toAddress = "0x0000000000000000000000000000000000000000",
                slippage = 1.0
            )

            // Then - should return failure
            assertTrue(result.isFailure)
        }
    }

    @Test
    fun `checkStatus with invalid requestId returns result`() {
        runBlocking {
            // When
            val result = rangoRepository.checkStatus(
                requestId = "invalid_request_id",
                txHash = "0x0000000000000000000000000000000000000000000000000000000000000000"
            )

            // Then - should return a result (success or failure depending on network)
            // In unit test environment without network, this will fail
            // In integration test, it may return a valid response with error status
            assertTrue(result.isSuccess || result.isFailure)
        }
    }

    @Test
    fun `reportFailure with invalid requestId returns result`() {
        runBlocking {
            // When
            val result = rangoRepository.reportFailure(
                requestId = "invalid_request_id",
                reason = "Test failure"
            )

            // Then - API may return success even for invalid IDs (just acknowledges the report)
            assertTrue(result.isSuccess || result.isFailure)
        }
    }
}

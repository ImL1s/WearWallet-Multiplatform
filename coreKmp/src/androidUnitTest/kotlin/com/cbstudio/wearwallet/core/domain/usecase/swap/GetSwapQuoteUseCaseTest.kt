package com.cbstudio.wearwallet.core.domain.usecase.swap

import android.util.Log
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.rango.RangoRepository
import com.cbstudio.wearwallet.core.rango.model.RangoQuoteResponse
import com.cbstudio.wearwallet.core.rango.model.RangoTokenMeta
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever
import org.mockito.ArgumentMatchers.anyString

class GetSwapQuoteUseCaseTest {

    @Mock
    lateinit var rangoRepository: RangoRepository

    private lateinit var getSwapQuoteUseCase: GetSwapQuoteUseCase
    private lateinit var mockedLog: MockedStatic<Log>

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        
        mockedLog = Mockito.mockStatic(Log::class.java)
        mockedLog.`when`<Int> { Log.d(anyString(), anyString()) }.thenReturn(0)
        mockedLog.`when`<Int> { Log.e(anyString(), anyString()) }.thenReturn(0)
        
        getSwapQuoteUseCase = GetSwapQuoteUseCase(rangoRepository)
    }

    @After
    fun tearDown() {
        mockedLog.close()
    }

    @Test
    fun `invoke returns success when repository returns success`() = runBlocking {
        // Arrange
        val params = GetSwapQuoteUseCase.Params(
            fromToken = RangoTokenMeta(blockchain = "ETH", symbol = "ETH", address = null),
            toToken = RangoTokenMeta(blockchain = "BSC", symbol = "BNB", address = null),
            amountInWei = "1000000000000000000"
        )
        val mockResponse = RangoQuoteResponse(
            requestId = "123",
            route = null,
            error = null,
            resultType = "OK"
        )
        
        whenever(
            rangoRepository.getSwapQuote(
                fromChain = "ETH",
                fromTokenSymbol = null,
                toChain = "BSC",
                toTokenSymbol = null,
                amount = "1000000000000000000",
                slippage = 1.0
            )
        ).thenReturn(kotlin.Result.success(mockResponse))

        // Act
        val result = getSwapQuoteUseCase(params)

        // Assert
        assertTrue(result is Result.Success)
    }

    @Test
    fun `invoke returns failure when repository returns failure`() = runBlocking {
        // Arrange
        val params = GetSwapQuoteUseCase.Params(
            fromToken = RangoTokenMeta(blockchain = "ETH", symbol = "ETH", address = null),
            toToken = RangoTokenMeta(blockchain = "BSC", symbol = "BNB", address = null),
            amountInWei = "1000000000000000000"
        )
        val exception = Exception("Network error")

        whenever(
            rangoRepository.getSwapQuote(
                fromChain = "ETH",
                fromTokenSymbol = null,
                toChain = "BSC",
                toTokenSymbol = null,
                amount = "1000000000000000000",
                slippage = 1.0
            )
        ).thenReturn(kotlin.Result.failure(exception))

        // Act
        val result = getSwapQuoteUseCase(params)

        // Assert
        assertTrue(result is Result.Failure)
    }
}

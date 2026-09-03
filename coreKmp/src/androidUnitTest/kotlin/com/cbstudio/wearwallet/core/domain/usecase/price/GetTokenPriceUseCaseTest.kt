package com.cbstudio.wearwallet.core.domain.usecase.price

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.repository.TokenRepository
import com.cbstudio.wearwallet.core.network.PriceApiClient
import com.cbstudio.wearwallet.core.network.PriceData
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.anyList

class GetTokenPriceUseCaseTest {

    @Mock
    lateinit var tokenRepository: TokenRepository
    @Mock
    lateinit var priceApiClient: PriceApiClient

    private lateinit var getTokenPriceUseCase: GetTokenPriceUseCase

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        getTokenPriceUseCase = GetTokenPriceUseCase(tokenRepository, priceApiClient)
    }

    @Test
    fun `getPrice success returns price from repository`() {
        runBlocking {
            // Given
            val symbol = "ETH"
            val expectedPrice = 2000.0
            whenever(tokenRepository.getTokenPrice(symbol)).thenReturn(expectedPrice)

            // When
            val result = getTokenPriceUseCase.getPrice(symbol)

            // Then
            assertTrue(result is Result.Success)
            assertEquals(expectedPrice, (result as Result.Success).data, 0.0)
        }
    }

    @Test
    fun `getPrice failure returns error when price not found`() {
        runBlocking {
            // Given
            val symbol = "UNKNOWN"
            whenever(tokenRepository.getTokenPrice(symbol)).thenReturn(null)

            // When
            val result = getTokenPriceUseCase.getPrice(symbol)

            // Then
            assertTrue(result is Result.Failure)
            assertEquals("Price not available for $symbol", (result as Result.Failure).exception.message)
        }
    }

    @Test
    fun `getPrices delegates to priceApiClient`() {
        runBlocking {
            // Given
            val symbols = listOf("ETH", "BTC")
            val priceDataMap = mapOf(
                "ETH" to PriceData("ETH", 2000.0),
                "BTC" to PriceData("BTC", 30000.0)
            )
            whenever(priceApiClient.getSimplePrice(anyList(), anyString(), any())).thenReturn(Result.Success(priceDataMap))

            // When
            val result = getTokenPriceUseCase.getPrices(symbols)

            // Then
            assertTrue(result is Result.Success)
            assertEquals(priceDataMap, (result as Result.Success).data)
        }
    }

    @Test
    fun `observePrice emits initial price`() {
        runTest {
            // Given
            val symbol = "ETH"
            val expectedPrice = 2000.0
            whenever(tokenRepository.getTokenPrice(symbol)).thenReturn(expectedPrice)

            // When
            val result = getTokenPriceUseCase.observePrice(symbol).take(1).toList()

            // Then
            assertEquals(1, result.size)
            assertTrue(result.first() is Result.Success)
            assertEquals(expectedPrice, (result.first() as Result.Success).data, 0.0)
        }
    }

    @Test
    fun `calculatePortfolioValue sums up values correctly`() {
        runBlocking {
            // Given
            val holdings = mapOf(
                "ETH" to 2.0,
                "BTC" to 0.5
            )
            val priceDataMap = mapOf(
                "ETH" to PriceData("ETH", 2000.0),
                "BTC" to PriceData("BTC", 30000.0)
            )
            val expectedTotal = (2.0 * 2000.0) + (0.5 * 30000.0) // 4000 + 15000 = 19000
            
            whenever(priceApiClient.getSimplePrice(anyList(), anyString(), any())).thenReturn(Result.Success(priceDataMap))

            // When
            val result = getTokenPriceUseCase.calculatePortfolioValue(holdings)

            // Then
            assertTrue(result is Result.Success)
            assertEquals(expectedTotal, (result as Result.Success).data, 0.0)
        }
    }
}

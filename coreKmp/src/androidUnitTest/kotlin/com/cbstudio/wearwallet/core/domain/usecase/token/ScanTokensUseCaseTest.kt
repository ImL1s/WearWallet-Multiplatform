package com.cbstudio.wearwallet.core.domain.usecase.token

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.Token
import com.cbstudio.wearwallet.core.domain.repository.TokenRepository
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations

class ScanTokensUseCaseTest {

    @Mock
    private lateinit var tokenRepository: TokenRepository

    private lateinit var scanTokensUseCase: ScanTokensUseCase

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        scanTokensUseCase = ScanTokensUseCase(tokenRepository)
    }

    @Test
    fun `scan tokens successfully and filters zero balance`() {
        runBlocking {
            val address = "0xWallet"
            val chainType = ChainType.ETHEREUM
            val tokens = listOf(
                createMockToken("TokenA", "10.0"),
                createMockToken("TokenB", "0.0"), // Should be filtered
                createMockToken("TokenC", "5.0")
            )

            Mockito.`when`(tokenRepository.scanTokens(address, chainType)).thenReturn(tokens)

            val flow = scanTokensUseCase(address, chainType)
            val results = flow.toList()

            // First emission should be Loading
            assertTrue(results[0] is Result.Loading)
            
            // Second emission should be Success with filtered tokens
            assertTrue(results[1] is Result.Success)
            val data = (results[1] as Result.Success).data
            assertEquals(2, data.size)
            assertEquals("TokenA", data[0].symbol)
            assertEquals("TokenC", data[1].symbol)
        }
    }

    @Test
    fun `scan tokens emits failure on exception`() {
        runBlocking {
            val address = "0xWallet"
            val chainType = ChainType.ETHEREUM
            val exception = RuntimeException("Network error")

            Mockito.`when`(tokenRepository.scanTokens(address, chainType)).thenThrow(exception)

            val flow = scanTokensUseCase(address, chainType)
            val results = flow.toList()

            assertTrue(results[0] is Result.Loading)
            assertTrue(results[1] is Result.Failure)
            assertEquals(exception, (results[1] as Result.Failure).exception)
        }
    }

    private fun createMockToken(symbol: String, balance: String): Token {
        return Token(
            address = "0x$symbol",
            name = symbol,
            symbol = symbol,
            decimals = 18,
            chainType = ChainType.ETHEREUM,
            balance = balance,
            isNative = false
        )
    }
}

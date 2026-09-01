package com.cbstudio.wearwallet.core.domain.usecase.token

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.Token
import com.cbstudio.wearwallet.core.domain.repository.TokenRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations

class GetUserTokensUseCaseTest {

    @Mock
    private lateinit var tokenRepository: TokenRepository

    private lateinit var getUserTokensUseCase: GetUserTokensUseCase

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        getUserTokensUseCase = GetUserTokensUseCase(tokenRepository)
    }

    @Test
    fun `invoke fetches user tokens and adds native token`() {
        runBlocking {
            val address = "0xWallet"
            val chainType = ChainType.ETHEREUM
            val userTokens = listOf(createMockToken("USDT", "100.0"))
            val nativeBalance = "2.5"

            Mockito.`when`(tokenRepository.scanUserTokens(address, chainType)).thenReturn(userTokens)
            Mockito.`when`(tokenRepository.getNativeBalance(address, chainType)).thenReturn(nativeBalance)

            val flow = getUserTokensUseCase(address, chainType)
            val results = flow.toList()

            assertTrue(results[0] is Result.Loading)
            assertTrue(results[1] is Result.Success)

            val data = (results[1] as Result.Success).data
            // Expecting native token + 1 user token = 2 tokens
            assertEquals(2, data.size)
            
            // First token should be Native (ETH)
            val nativeToken = data[0]
            assertTrue(nativeToken.isNative)
            assertEquals("ETH", nativeToken.symbol)
            assertEquals(nativeBalance, nativeToken.balance)

            // Second token should be USDT
            val usdtToken = data[1]
            assertEquals("USDT", usdtToken.symbol)
        }
    }

    @Test
    fun `observeUserTokens returns flow from repository`() {
        val address = "0xWallet"
        val tokens = listOf(createMockToken("USDT", "100.0"))
        Mockito.`when`(tokenRepository.observeUserTokens(address)).thenReturn(flowOf(tokens))

        runBlocking {
            val result = getUserTokensUseCase.observeUserTokens(address)
            result.collect {
                assertEquals(tokens, it)
            }
            verify(tokenRepository).observeUserTokens(address)
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

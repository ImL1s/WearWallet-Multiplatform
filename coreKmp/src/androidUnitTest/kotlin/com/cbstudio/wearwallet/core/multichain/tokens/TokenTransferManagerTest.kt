package com.cbstudio.wearwallet.core.multichain.tokens

import android.util.Log
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.core.network.EthereumRpcClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import kotlinx.coroutines.runBlocking

class TokenTransferManagerTest {

    @Mock
    lateinit var mockRpcClient: EthereumRpcClient

    private lateinit var tokenTransferManager: TokenTransferManager
    private lateinit var mockedLog: MockedStatic<Log>

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)

        // Mock android.util.Log
        mockedLog = Mockito.mockStatic(Log::class.java)
        mockedLog.`when`<Int> { Log.d(anyString(), anyString()) }.thenReturn(0)
        mockedLog.`when`<Int> { Log.e(anyString(), anyString()) }.thenReturn(0)
        mockedLog.`when`<Int> { Log.i(anyString(), anyString()) }.thenReturn(0)
        mockedLog.`when`<Int> { Log.w(anyString(), anyString()) }.thenReturn(0)

        // Setup Koin with mocked RpcClient
        stopKoin() // Stop any existing Koin instance
        startKoin {
            modules(module {
                single<EthereumRpcClient> { mockRpcClient }
            })
        }

        tokenTransferManager = TokenTransferManager()
    }

    @After
    fun tearDown() {
        mockedLog.close()
        stopKoin()
    }

    @Test
    fun `getPopularTokens returns USDT and USDC for Ethereum`() {
        // When
        val tokens = tokenTransferManager.getPopularTokens(MultiChainType.ETHEREUM)

        // Then
        assertEquals(2, tokens.size)
        assertTrue(tokens.any { it.symbol == "USDT" })
        assertTrue(tokens.any { it.symbol == "USDC" })
    }

    @Test
    fun `getPopularTokens returns USDT for BSC`() {
        // When
        val tokens = tokenTransferManager.getPopularTokens(MultiChainType.BSC)

        // Then
        assertTrue(tokens.any { it.symbol == "USDT" })
        assertTrue(tokens.any { it.symbol == "USDC" })
    }

    @Test
    fun `getPopularTokens returns USDT for TRON`() {
        // When
        val tokens = tokenTransferManager.getPopularTokens(MultiChainType.TRON)

        // Then
        assertTrue(tokens.any { it.symbol == "USDT" })
    }

    @Test
    fun `getPopularTokens returns empty for Bitcoin`() {
        // When
        val tokens = tokenTransferManager.getPopularTokens(MultiChainType.BITCOIN)

        // Then - Bitcoin has no ERC20/TRC20 tokens
        assertTrue(tokens.isEmpty())
    }

    @Test
    fun `getTokenBalance for ERC20 returns balance from RPC`() {
        runBlocking {
            // Given
            val tokenAddress = "0xdac17f958d2ee523a2206206994597c13d831ec7"
            val walletAddress = "0x1234567890123456789012345678901234567890"
            val balanceHex = "0x5f5e100" // 100000000 in hex (100 USDT with 6 decimals)

            whenever(mockRpcClient.getTokenBalance(any<String>(), any<String>(), any<ChainType>()))
                .thenReturn(Result.Success(balanceHex))

            // When
            val result = tokenTransferManager.getTokenBalance(
                MultiChainType.ETHEREUM,
                tokenAddress,
                walletAddress
            )

            // Then
            assertTrue(result is Result.Success)
        }
    }

    @Test
    fun `getTokenBalance for TRC20 returns failure pending SDK`() {
        runBlocking {
            // Given
            val tokenAddress = "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t"
            val walletAddress = "TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE"

            // When
            val result = tokenTransferManager.getTokenBalance(
                MultiChainType.TRON,
                tokenAddress,
                walletAddress
            )

            // Then - TRC20 is pending SDK integration
            assertTrue(result is Result.Failure)
        }
    }
}

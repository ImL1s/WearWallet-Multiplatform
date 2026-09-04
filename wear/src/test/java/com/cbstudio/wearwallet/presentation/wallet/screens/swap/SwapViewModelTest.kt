package com.cbstudio.wearwallet.presentation.wallet.screens.swap

import com.cbstudio.wearwallet.core.domain.usecase.swap.ExecuteSwapUseCase
import com.cbstudio.wearwallet.core.domain.usecase.swap.GetSwapQuoteUseCase
import com.cbstudio.wearwallet.core.rango.RangoMetadataRepository
import com.cbstudio.wearwallet.core.rango.model.*
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SwapViewModelTest : KoinTest {

    private lateinit var viewModel: SwapViewModel
    private lateinit var metadataRepository: RangoMetadataRepository
    private lateinit var walletRepository: com.cbstudio.wearwallet.core.domain.repository.WalletRepository
    private lateinit var getSwapQuoteUseCase: GetSwapQuoteUseCase
    private lateinit var executeSwapUseCase: ExecuteSwapUseCase
    
    private val testDispatcher = StandardTestDispatcher()

    private val mockBlockchains = listOf(
        RangoBlockchain(name = "ETH", displayName = "Ethereum", shortName = "ETH", chainId = "1", type = "EVM", enabled = true),
        RangoBlockchain(name = "BSC", displayName = "BNB Chain", shortName = "BSC", chainId = "56", type = "EVM", enabled = true)
    )

    private val mockTokens = listOf(
        RangoTokenMeta(symbol = "ETH", name = "Ethereum", blockchain = "ETH", address = null, decimals = 18, usdPrice = 2000.0, image = "eth.png"),
        RangoTokenMeta(symbol = "BNB", name = "BNB", blockchain = "BSC", address = null, decimals = 18, usdPrice = 300.0, image = "bnb.png"),
        RangoTokenMeta(symbol = "USDT", name = "Tether", blockchain = "ETH", address = "0xdac17f958d2ee523a2206206994597c13d831ec7", decimals = 6, usdPrice = 1.0, image = "usdt.png")
    )

    private val mockMetadataResponse = RangoMetaResponse(
        blockchains = mockBlockchains,
        tokens = mockTokens,
        swappers = emptyList()
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        metadataRepository = mockk(relaxed = true)
        walletRepository = mockk(relaxed = true)
        getSwapQuoteUseCase = mockk(relaxed = true)
        executeSwapUseCase = mockk(relaxed = true)
        
        startKoin {
            modules(module {
                single { metadataRepository }
                single { walletRepository }
                single { getSwapQuoteUseCase }
                single { executeSwapUseCase }
                single<com.cbstudio.wearwallet.core.security.CapabilityGate> { com.cbstudio.wearwallet.core.security.AllowDevCapabilityGate() }
            })
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        stopKoin()
    }

    @Test
    fun `loadMetadata success should update available chains and tokens`() = runTest {
        // Given
        coEvery { metadataRepository.getMetadata() } returns Result.success(mockMetadataResponse)

        // When
        viewModel = SwapViewModel() // Init calls loadMetadata
        testScheduler.advanceUntilIdle()

        // Then
        val state = viewModel.uiState.value
        assertEquals(SwapStatus.IDLE, state.status)
    }

    @Test
    fun `setAmount should update amount and calculate usd value`() = runTest {
        // Given
        coEvery { metadataRepository.getMetadata() } returns Result.success(mockMetadataResponse)
        viewModel = SwapViewModel()
        testScheduler.advanceUntilIdle()
        
        val ethToken = mockTokens.find { it.symbol == "ETH" }!!
        viewModel.setFromToken(ethToken)
        
        // When
        viewModel.setAmount("0.5")
        
        // Then
        val state = viewModel.uiState.value
        assertEquals("0.5", state.amount)
        assertEquals(1000.0, state.amountUsd) // 0.5 * 2000
    }

    @Test
    fun `getQuote success should update state with quote`() = runTest {
        // Given
        coEvery { metadataRepository.getMetadata() } returns Result.success(mockMetadataResponse)
        viewModel = SwapViewModel()
        testScheduler.advanceUntilIdle()
        
        val fromToken = mockTokens.find { it.symbol == "ETH" }!!
        val toToken = mockTokens.find { it.symbol == "USDT" }!!
        viewModel.setFromToken(fromToken)
        viewModel.setToToken(toToken)
        viewModel.setAmount("1.0")

        val mockQuote = RangoQuoteResponse(
            resultType = "OK",
            route = RangoRoute(
                outputAmount = "1990.0", 
                swapper = RangoSwapper(id = "TestSwapper", title = "Test", logo = "")
            ),
            requestId = "req-123"
        )
        
        coEvery { 
            getSwapQuoteUseCase(any()) 
        } returns com.cbstudio.wearwallet.core.common.Result.Success(mockQuote)

        // When
        viewModel.getQuote()
        testScheduler.advanceUntilIdle()

        // Then
        val state = viewModel.uiState.value
        assertEquals(SwapStatus.QUOTE_READY, state.status)
        assertEquals(mockQuote, state.quote)
        assertEquals("req-123", state.requestId)
    }

    @Test
    fun `getQuote failure should update state with error`() = runTest {
         // Given
        coEvery { metadataRepository.getMetadata() } returns Result.success(mockMetadataResponse)
        viewModel = SwapViewModel()
        testScheduler.advanceUntilIdle()
        
        val fromToken = mockTokens.find { it.symbol == "ETH" }!!
        val toToken = mockTokens.find { it.symbol == "USDT" }!!
        viewModel.setFromToken(fromToken)
        viewModel.setToToken(toToken)
        viewModel.setAmount("1.0")

        coEvery { 
             getSwapQuoteUseCase(any())  
        } returns com.cbstudio.wearwallet.core.common.Result.Failure(Exception("Quote failed"))

        // When
        viewModel.getQuote()
        testScheduler.advanceUntilIdle()

        // Then
        val state = viewModel.uiState.value
        assertEquals(SwapStatus.FAILED, state.status)
    }

    @Test
    fun `unlockWallet and execute swap success`() = runTest {
        // Given
        coEvery { metadataRepository.getMetadata() } returns Result.success(mockMetadataResponse)
        viewModel = SwapViewModel()
        testScheduler.advanceUntilIdle()

        // Setup Swap State
        val fromToken = mockTokens.find { it.symbol == "ETH" }!!
        val toToken = mockTokens.find { it.symbol == "USDT" }!!
        viewModel.setFromToken(fromToken)
        viewModel.setToToken(toToken)
        viewModel.setAmount("1.0")

        // Setup Mocks
        val walletId = "wallet-123"
        val mockWallet = mockk<com.cbstudio.wearwallet.core.domain.model.WalletAccount> {
            every { id } returns walletId
            every { address } returns "0x123"
            every { chainType } returns com.cbstudio.wearwallet.core.domain.model.ChainType.ETHEREUM
        }
        
        coEvery { walletRepository.getActiveWallet() } returns com.cbstudio.wearwallet.core.common.Result.Success(mockWallet)
        
        // Mock ExecuteSwapUseCase
        val successResult = ExecuteSwapUseCase.Success(
            txHash = "0xHash",
            requestId = "req-456",
            isCrossChain = false
        )
        
        coEvery { 
            executeSwapUseCase(any()) 
        } returns com.cbstudio.wearwallet.core.common.Result.Success(successResult)

        // When
        viewModel.unlockWallet("password")
        testScheduler.advanceUntilIdle()

        // Then
        val state = viewModel.uiState.value
        assertEquals(SwapStatus.SUCCESS, state.status)
        assertEquals("0xHash", state.txHash)
    }

    @Test
    fun `unlockWallet fails closed under ReleaseProductionCapabilityGate`() = runTest {
        stopKoin()
        startKoin {
            modules(module {
                single { metadataRepository }
                single { walletRepository }
                single { getSwapQuoteUseCase }
                single { executeSwapUseCase }
                single<com.cbstudio.wearwallet.core.security.CapabilityGate> { com.cbstudio.wearwallet.core.security.ReleaseProductionCapabilityGate(allowEvmMainnetSend = false) }
            })
        }

        val mockWallet = mockk<com.cbstudio.wearwallet.core.domain.model.WalletAccount> {
            every { id } returns "wallet-123"
            every { address } returns "0x123"
            every { chainType } returns com.cbstudio.wearwallet.core.domain.model.ChainType.ETHEREUM
        }
        coEvery { walletRepository.getActiveWallet() } returns com.cbstudio.wearwallet.core.common.Result.Success(mockWallet)

        coEvery { metadataRepository.getMetadata() } returns Result.success(mockMetadataResponse)
        viewModel = SwapViewModel()
        testScheduler.advanceUntilIdle()

        val fromToken = mockTokens.find { it.symbol == "ETH" }!!
        val toToken = mockTokens.find { it.symbol == "USDT" }!!
        viewModel.setFromToken(fromToken)
        viewModel.setToToken(toToken)
        viewModel.setAmount("1.0")

        viewModel.unlockWallet("password")
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(SwapStatus.FAILED, state.status)
        assertEquals("Production capability gate fail-closed: swap disabled for Ethereum", state.error)
    }
}

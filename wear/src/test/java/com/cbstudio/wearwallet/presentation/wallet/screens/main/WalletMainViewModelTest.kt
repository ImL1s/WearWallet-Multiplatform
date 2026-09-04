package com.cbstudio.wearwallet.presentation.wallet.screens.main

import com.cbstudio.wearwallet.core.blockchain.api.UTXOApiClient
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.*
import com.cbstudio.wearwallet.core.domain.repository.TokenRepository
import com.cbstudio.wearwallet.core.domain.repository.WalletRepository
import com.cbstudio.wearwallet.core.domain.usecase.price.GetTokenPriceUseCase
import com.cbstudio.wearwallet.core.domain.usecase.token.ScanTokensUseCase
import com.cbstudio.wearwallet.core.domain.usecase.transaction.GetTransactionHistoryUseCase
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.presentation.wallet.ChainStateManager
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WalletMainViewModelTest : KoinTest {

    private lateinit var viewModel: WalletMainViewModel
    private lateinit var walletRepository: WalletRepository
    private lateinit var tokenRepository: TokenRepository
    private lateinit var scanTokensUseCase: ScanTokensUseCase
    private lateinit var getTransactionHistoryUseCase: GetTransactionHistoryUseCase
    private lateinit var getTokenPriceUseCase: GetTokenPriceUseCase
    private lateinit var utxoApiClient: UTXOApiClient
    
    private val testDispatcher = StandardTestDispatcher()

    private val validMockWallet = WalletAccount(
        id = "wallet-1",
        name = "Test Wallet",
        address = "0x123",
        publicKey = "0xpubkey",
        chainType = ChainType.ETHEREUM,
        walletType = WalletType.HOT_WALLET,
        isActive = true
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        walletRepository = mockk(relaxed = true)
        tokenRepository = mockk(relaxed = true)
        scanTokensUseCase = mockk(relaxed = true)
        getTransactionHistoryUseCase = mockk(relaxed = true)
        getTokenPriceUseCase = mockk(relaxed = true)
        utxoApiClient = mockk(relaxed = true)
        
        // Default mocks for init-time flows (observeActiveWallet + observeChainChanges)
        coEvery { walletRepository.getAllWallets() } returns Result.Success(listOf(validMockWallet))
        coEvery { walletRepository.getActiveWallet() } returns Result.Success(validMockWallet)
        coEvery { walletRepository.observeActiveWallet() } returns flowOf(validMockWallet)
        coEvery { walletRepository.getNativeBalance(any(), any()) } returns 0.0
        coEvery { walletRepository.updateWallet(any()) } returns Result.Success(Unit)
        coEvery { getTransactionHistoryUseCase(any(), any(), any()) } returns flowOf(Result.Success(emptyList()))
        
        startKoin {
            modules(module {
                single { walletRepository }
                single { tokenRepository }
                single { scanTokensUseCase }
                single { getTransactionHistoryUseCase }
                single { getTokenPriceUseCase }
                single { utxoApiClient }
            })
        }
        
        // Reset ChainStateManager to match mock wallet
        ChainStateManager.setCurrentChain(ChainType.ETHEREUM)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        stopKoin()
    }

    @Test
    fun `init should load wallets and active wallet`() = runTest {
        // Given — default mocks from setup() are sufficient
        coEvery { walletRepository.getActiveWallet() } returns Result.Success(null)
        
        // When
        viewModel = WalletMainViewModel()
        testScheduler.advanceUntilIdle()

        // Then
        coVerify { walletRepository.getAllWallets() }
        coVerify { walletRepository.setActiveWallet(validMockWallet.id) }
        
        val state = viewModel.uiState.value
        assertEquals(validMockWallet, state.currentWallet)
        assertEquals(1, state.walletCount)
    }

    @Test
    fun `loadBalance for EVM chain should use WalletRepository`() = runTest {
        // Given — set balance mock BEFORE creating ViewModel
        coEvery { walletRepository.getNativeBalance(any(), any()) } returns 1.5
        coEvery { getTokenPriceUseCase.getPrice("ETH") } returns Result.Success(2000.0)
        
        // When
        viewModel = WalletMainViewModel()
        testScheduler.advanceUntilIdle()

        // Then
        coVerify { walletRepository.getNativeBalance(validMockWallet.address, ChainType.ETHEREUM) }
        
        val state = viewModel.uiState.value
        assertEquals(1.5, state.nativeBalance)
        assertFalse(state.isLoading)
    }

    @Test
    fun `loadBalance for UTXO chain should use UTXOApiClient`() = runTest {
        // Given — create a BTC wallet and set chain to BITCOIN
        val btcWallet = validMockWallet.copy(chainType = ChainType.BITCOIN)
        ChainStateManager.setCurrentChain(ChainType.BITCOIN)
        coEvery { walletRepository.getAllWallets() } returns Result.Success(listOf(btcWallet))
        coEvery { walletRepository.getActiveWallet() } returns Result.Success(btcWallet)
        coEvery { walletRepository.observeActiveWallet() } returns flowOf(btcWallet)
        // 1.5 BTC = 150,000,000 satoshis
        coEvery { utxoApiClient.getBalance(any(), any()) } returns 150_000_000L
        coEvery { getTokenPriceUseCase.getPrice("BTC") } returns Result.Success(30000.0)
        
        // When
        viewModel = WalletMainViewModel()
        testScheduler.advanceUntilIdle()

        // Then
        coVerify { utxoApiClient.getBalance(btcWallet.address, ChainType.BITCOIN) }
        
        val state = viewModel.uiState.value
        assertEquals(1.5, state.nativeBalance)
    }

    @Test
    fun `loadTokens should update state with tokens`() = runTest {
        // Given
        ChainStateManager.setCurrentChain(ChainType.ETHEREUM)
        val mockToken = Token(
            id = "token-1",
            address = "0x123",
            name = "Tether",
            symbol = "USDT",
            decimals = 6,
            chainType = ChainType.ETHEREUM,
            logoUrl = "",
            balance = "100000000", // 100 USDT (6 decimals)
            usdPrice = 1.0,
            isNative = false
        )
        val mockTokens = listOf(mockToken)

        coEvery { walletRepository.getAllWallets() } returns Result.Success(listOf(validMockWallet))
        coEvery { walletRepository.observeActiveWallet() } returns flowOf(validMockWallet)
        // Corrected parameter name from address to walletAddress
        coEvery { tokenRepository.scanUserTokens(walletAddress = any(), chainType = any()) } returns mockTokens
        coEvery { getTokenPriceUseCase.getPrice("USDT") } returns Result.Success(1.0)
        
        // When
        viewModel = WalletMainViewModel()
        testScheduler.advanceUntilIdle()

        // Then
        val state = viewModel.uiState.value
        assertEquals(mockTokens, state.tokens)
        assertEquals(1, state.tokenCount)
        assertTrue(BigDecimal("100.0").compareTo(state.tokensTotalValue) == 0, "Expected 100.0 but got ${state.tokensTotalValue}")
    }

    @Test
    fun `scanTokens should update tokens and loading state`() = runTest {
        // Given
        ChainStateManager.setCurrentChain(ChainType.ETHEREUM)
        coEvery { walletRepository.getAllWallets() } returns Result.Success(listOf(validMockWallet))
        coEvery { walletRepository.observeActiveWallet() } returns flowOf(validMockWallet)
        
        // Fix flow type inference by explicitly typing or instantiated Result.Loading()
        val loadingResult = Result.Loading<List<Token>>()
        val successResult = Result.Success<List<Token>>(emptyList())
        coEvery { scanTokensUseCase(any(), any()) } returns flowOf(loadingResult, successResult)
        
        // When
        viewModel = WalletMainViewModel()
        testScheduler.advanceUntilIdle()
        viewModel.scanTokens()
        
        // Verify loading state (might miss it if coroutine runs too fast, but verifying call happened)
        testScheduler.advanceUntilIdle()
        
        // Then
        coVerify { scanTokensUseCase(validMockWallet.address, ChainType.ETHEREUM) }
        assertFalse(viewModel.uiState.value.isScanningTokens)
    }

    @Test
    fun `switchChain should update state and reload data`() = runTest {
        // Given — ViewModel starts on ETHEREUM
        viewModel = WalletMainViewModel()
        testScheduler.advanceUntilIdle()
        
        // When
        viewModel.switchChain(ChainType.BSC)
        testScheduler.advanceUntilIdle()

        // Then
        val state = viewModel.uiState.value
        assertEquals(ChainType.BSC, state.currentChain)
        assertEquals(MultiChainType.BSC, state.currentMultiChain)
    }
    
    @Test
    fun `loadTransactions success should update state`() = runTest {
        // Given
        ChainStateManager.setCurrentChain(ChainType.ETHEREUM)
        
        val mockTx = Transaction(
            hash = "0x123",
            timestamp = Clock.System.now(),
            from = "0x1",
            to = "0x2",
            value = "1000000000000000000", // 1 ETH
            chainType = ChainType.ETHEREUM,
            network = Network.Mainnet,
            status = TransactionStatus.CONFIRMED,
            type = TransactionType.TRANSFER,
            nonce = 0L
        )
        
        val mockTxs = listOf(mockTx)
        
        coEvery { walletRepository.getAllWallets() } returns Result.Success(listOf(validMockWallet))
        coEvery { walletRepository.observeActiveWallet() } returns flowOf(validMockWallet)
        coEvery { getTransactionHistoryUseCase(any(), any(), any()) } returns flowOf(Result.Success(mockTxs))
        
        // When
        viewModel = WalletMainViewModel()
        testScheduler.advanceUntilIdle()

        // Then
        val state = viewModel.uiState.value
        assertEquals(mockTxs, state.transactions)
        assertEquals(null, state.error)
    }

    @Test
    fun `loadTransactions for UTXO chain should call history use case`() = runTest {
        val btcWallet = validMockWallet.copy(chainType = ChainType.BITCOIN)
        ChainStateManager.setCurrentChain(ChainType.BITCOIN)
        coEvery { walletRepository.getAllWallets() } returns Result.Success(listOf(btcWallet))
        coEvery { walletRepository.getActiveWallet() } returns Result.Success(btcWallet)
        coEvery { walletRepository.observeActiveWallet() } returns flowOf(btcWallet)
        coEvery { utxoApiClient.getBalance(any(), any()) } returns 0L
        coEvery { getTransactionHistoryUseCase(any(), any(), any()) } returns flowOf(Result.Success(emptyList()))

        viewModel = WalletMainViewModel()
        testScheduler.advanceUntilIdle()

        coVerify { getTransactionHistoryUseCase(btcWallet.address, ChainType.BITCOIN, 10) }
        assertEquals(null, viewModel.uiState.value.error)
    }

    @Test
    fun `loadTransactions for UTXO failure should surface error`() = runTest {
        val btcWallet = validMockWallet.copy(chainType = ChainType.BITCOIN)
        ChainStateManager.setCurrentChain(ChainType.BITCOIN)
        coEvery { walletRepository.getAllWallets() } returns Result.Success(listOf(btcWallet))
        coEvery { walletRepository.getActiveWallet() } returns Result.Success(btcWallet)
        coEvery { walletRepository.observeActiveWallet() } returns flowOf(btcWallet)
        coEvery { utxoApiClient.getBalance(any(), any()) } returns 0L
        coEvery { getTransactionHistoryUseCase(any(), any(), any()) } returns flowOf(
            Result.Failure(IllegalStateException("utxo history unavailable"))
        )

        viewModel = WalletMainViewModel()
        testScheduler.advanceUntilIdle()

        assertEquals("載入交易記錄失敗", viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.transactions.isEmpty())
    }
}

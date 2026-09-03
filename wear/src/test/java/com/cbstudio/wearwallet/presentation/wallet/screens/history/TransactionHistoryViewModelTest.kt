package com.cbstudio.wearwallet.presentation.wallet.screens.history

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.*
import com.cbstudio.wearwallet.core.domain.repository.WalletRepository
import com.cbstudio.wearwallet.core.domain.usecase.transaction.GetTransactionHistoryUseCase
import com.cbstudio.wearwallet.presentation.wallet.screens.main.tx.TransactionFilter
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import kotlinx.datetime.Clock
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * TransactionHistoryViewModel 單元測試
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TransactionHistoryViewModelTest : KoinTest {
    
    private lateinit var viewModel: TransactionHistoryViewModel
    private lateinit var walletRepository: WalletRepository
    private lateinit var getTransactionHistoryUseCase: GetTransactionHistoryUseCase
    private val testDispatcher = StandardTestDispatcher()
    
    private val mockWallet = WalletAccount(
        id = "test-wallet-id",
        name = "Test Wallet",
        address = "0x1234567890abcdef",
        publicKey = "0xpubkey",
        chainType = ChainType.ETHEREUM,
        isActive = true,
        createdAt = Clock.System.now().toEpochMilliseconds()
    )
    
    private val mockTransactions = listOf(
        Transaction(
            hash = "0xabc123",
            from = "0x1234567890abcdef",
            to = "0xfedcba0987654321",
            value = "10.0",
            gasPrice = "20",
            gasLimit = "21000",
            nonce = 1,
            chainType = ChainType.ETHEREUM,
            status = TransactionStatus.CONFIRMED,
            timestamp = Clock.System.now()
        ),
        Transaction(
            hash = "0xdef456",
            from = "0xfedcba0987654321",
            to = "0x1234567890abcdef",
            value = "5.0",
            gasPrice = "25",
            gasLimit = "21000",
            nonce = 2,
            chainType = ChainType.ETHEREUM,
            status = TransactionStatus.CONFIRMED,
            timestamp = Clock.System.now()
        )
    )
    
    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        walletRepository = mockk(relaxed = true)
        getTransactionHistoryUseCase = mockk(relaxed = true)
        
        startKoin {
            modules(module {
                single { walletRepository }
                single { getTransactionHistoryUseCase }
            })
        }
        
        viewModel = TransactionHistoryViewModel()
    }
    
    @After
    fun tearDown() {
        Dispatchers.resetMain()
        stopKoin()
    }
    
    @Test
    fun `載入活動錢包成功`() = runTest {
        // Given
        coEvery { walletRepository.getActiveWallet() } returns Result.Success(mockWallet)
        coEvery { 
            getTransactionHistoryUseCase(any(), any(), any()) 
        } returns flowOf(Result.Success(mockTransactions))
        
        // When
        viewModel = TransactionHistoryViewModel()
        testScheduler.advanceUntilIdle()
        
        // Then
        val state = viewModel.uiState.value
        assertNotNull(state.activeWallet)
        assertEquals(mockWallet.address, state.activeWallet?.address)
        assertEquals(2, state.transactions.size)
    }
    
    @Test
    fun `載入活動錢包失敗顯示錯誤`() = runTest {
        // Given
        val errorMessage = "無法載入錢包"
        coEvery { 
            walletRepository.getActiveWallet() 
        } returns Result.Failure(Exception(errorMessage))
        
        // When
        viewModel = TransactionHistoryViewModel()
        testScheduler.advanceUntilIdle()
        
        // Then
        val state = viewModel.uiState.value
        assertNotNull(state.error)
        assertTrue(state.error!!.contains(errorMessage))
    }
    
    @Test
    fun `設定交易篩選器`() = runTest {
        // Given
        viewModel = TransactionHistoryViewModel()
        
        // When
        viewModel.setFilter(TransactionFilter.SENT)
        
        // Then
        assertEquals(TransactionFilter.SENT, viewModel.uiState.value.filter)
        
        // When
        viewModel.setFilter(TransactionFilter.RECEIVED)
        
        // Then
        assertEquals(TransactionFilter.RECEIVED, viewModel.uiState.value.filter)
    }
    
    @Test
    fun `刷新交易記錄`() = runTest {
        // Given
        coEvery { walletRepository.getActiveWallet() } returns Result.Success(mockWallet)
        coEvery { 
            getTransactionHistoryUseCase(any(), any(), any()) 
        } returns flowOf(Result.Success(mockTransactions))
        
        viewModel = TransactionHistoryViewModel()
        testScheduler.advanceUntilIdle()
        
        // When
        viewModel.refreshTransactions()
        testScheduler.advanceUntilIdle()
        
        // Then
        val state = viewModel.uiState.value
        assertEquals(2, state.transactions.size)
        assertEquals(false, state.isRefreshing)
    }
    
    @Test
    fun `清除錯誤訊息`() = runTest {
        // Given
        coEvery { 
            walletRepository.getActiveWallet() 
        } returns Result.Failure(Exception("錯誤"))
        
        viewModel = TransactionHistoryViewModel()
        testScheduler.advanceUntilIdle()
        
        assertNotNull(viewModel.uiState.value.error)
        
        // When
        viewModel.clearError()
        
        // Then
        assertEquals(null, viewModel.uiState.value.error)
    }
    
    @Test
    fun `切換區塊鏈網路`() = runTest {
        // Given
        coEvery { walletRepository.getActiveWallet() } returns Result.Success(mockWallet)
        coEvery { 
            getTransactionHistoryUseCase(any(), any(), any()) 
        } returns flowOf(Result.Success(emptyList()))
        
        viewModel = TransactionHistoryViewModel()
        testScheduler.advanceUntilIdle()
        
        // When
        viewModel.switchChain(ChainType.BSC)
        testScheduler.advanceUntilIdle()
        
        // Then
        assertEquals(ChainType.BSC, viewModel.uiState.value.currentChain)
        assertEquals(0, viewModel.uiState.value.transactions.size)
    }
    
    @Test
    fun `載入更多交易`() = runTest {
        // Given
        val moreTxs = listOf(
            Transaction(
                hash = "0xnew123",
                from = "0x1234567890abcdef",
                to = "0xnewaddress",
                value = "15.0",
                nonce = 3,
                chainType = ChainType.ETHEREUM,
                status = TransactionStatus.PENDING
            )
        )
        
        // Create a list of 20 transactions to trigger hasMore = true
        val fullPageTxs = MutableList(20) { index ->
            Transaction(
                hash = "0xhash$index",
                from = "0xfrom",
                to = "0xto",
                value = "1.0",
                nonce = index.toLong(),
                chainType = ChainType.ETHEREUM,
                status = TransactionStatus.CONFIRMED,
                timestamp = Clock.System.now()
            )
        }

        coEvery { walletRepository.getActiveWallet() } returns Result.Success(mockWallet)
        coEvery { 
            getTransactionHistoryUseCase(any(), any(), any()) 
        } returnsMany listOf(
            flowOf(Result.Success(fullPageTxs)),
            flowOf(Result.Success(moreTxs))
        )
        
        viewModel = TransactionHistoryViewModel()
        testScheduler.advanceUntilIdle()
        
        // When
        viewModel.loadMore()
        testScheduler.advanceUntilIdle()
        
        // Then
        val state = viewModel.uiState.value
        assertEquals(21, state.transactions.size)
        assertEquals(2, state.currentPage)
    }
}
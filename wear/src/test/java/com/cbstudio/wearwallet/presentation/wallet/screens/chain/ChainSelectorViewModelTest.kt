package com.cbstudio.wearwallet.presentation.wallet.screens.chain

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.WalletAccount
import com.cbstudio.wearwallet.core.domain.model.WalletType
import com.cbstudio.wearwallet.core.domain.repository.WalletRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ChainSelectorViewModelTest : KoinTest {

    private lateinit var viewModel: ChainSelectorViewModel
    private lateinit var walletRepository: WalletRepository
    
    private val testDispatcher = StandardTestDispatcher()

    private val mockWallet = WalletAccount(
        id = "wallet-1",
        name = "Test Wallet",
        address = "0x123",
        publicKey = "0xpubkey",
        chainType = ChainType.ETHEREUM,
        walletType = WalletType.HOT_WALLET
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        walletRepository = mockk(relaxed = true)
        
        startKoin {
            modules(module {
                single { walletRepository }
            })
        }
        
        coEvery { walletRepository.getActiveWallet() } returns Result.Success(mockWallet)
        
        viewModel = ChainSelectorViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        stopKoin()
    }

    @Test
    fun `init should load active wallet and available chains`() = runTest {
        testScheduler.advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertEquals(mockWallet, state.activeWallet)
        assertEquals(ChainType.ETHEREUM, state.currentChain)
        assertTrue(state.availableChains.isNotEmpty())
    }

    @Test
    fun `loadActiveWallet failure should set error`() = runTest {
        coEvery { walletRepository.getActiveWallet() } returns Result.Failure(Exception("Load Failed"))
        
        viewModel = ChainSelectorViewModel()
        testScheduler.advanceUntilIdle()
        
        assertTrue(viewModel.uiState.value.error?.contains("載入錢包失敗") == true)
    }

    @Test
    fun `selectChain should update current chain`() = runTest {
        testScheduler.advanceUntilIdle()
        
        val bscChain = viewModel.uiState.value.availableChains.find { it.chainType == ChainType.BSC }!!
        
        // When
        viewModel.selectChain(bscChain)
        testScheduler.advanceUntilIdle()
        
        // Then
        assertEquals(ChainType.BSC, viewModel.uiState.value.currentChain)
        coVerify { walletRepository.updateWallet(any()) }
    }

    @Test
    fun `updateSearchQuery should filter chains`() = runTest {
        testScheduler.advanceUntilIdle()
        
        // When search for "Ethereum"
        viewModel.updateSearchQuery("Ethereum")
        
        // Then
        val filtered = viewModel.filteredChains.first()
        assertEquals(1, filtered.size)
        assertEquals(ChainType.ETHEREUM, filtered.first().chainType)
    }

    @Test
    fun `updateSearchQuery with symbol should filter chains`() = runTest {
        testScheduler.advanceUntilIdle()
        
        // When search for "BNB"
        viewModel.updateSearchQuery("BNB")
        
        // Then
        val filtered = viewModel.filteredChains.first()
        assertEquals(1, filtered.size)
        assertEquals(ChainType.BSC, filtered.first().chainType)
    }
}

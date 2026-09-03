package com.cbstudio.wearwallet.presentation.wallet.screens.token

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.Token
import com.cbstudio.wearwallet.core.domain.model.WalletAccount
import com.cbstudio.wearwallet.core.domain.model.WalletType
import com.cbstudio.wearwallet.core.domain.repository.TokenRepository
import com.cbstudio.wearwallet.core.domain.repository.WalletRepository
import com.cbstudio.wearwallet.core.domain.usecase.token.ScanTokensUseCase
import com.cbstudio.wearwallet.presentation.wallet.ChainStateManager
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class TokenSelectorViewModelTest : KoinTest {

    private lateinit var viewModel: TokenSelectorViewModel
    private lateinit var walletRepository: WalletRepository
    private lateinit var tokenRepository: TokenRepository
    private lateinit var scanTokensUseCase: ScanTokensUseCase
    
    private val testDispatcher = StandardTestDispatcher()

    private val mockWallet = WalletAccount(
        id = "wallet-1",
        name = "Test Wallet",
        address = "0x71C7656EC7ab88b098defB751B7401B5f6d8976F",
        publicKey = "0xpubkey",
        chainType = ChainType.ETHEREUM,
        walletType = WalletType.HOT_WALLET
    )
    
    private val mockToken = Token(
        address = "0x123",
        symbol = "TEST",
        name = "Test Token",
        decimals = 18,
        balance = "10.0",
        chainType = ChainType.ETHEREUM
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        // Reset global state
        ChainStateManager.setCurrentChain(ChainType.ETHEREUM)
        
        walletRepository = mockk(relaxed = true)
        tokenRepository = mockk(relaxed = true)
        scanTokensUseCase = mockk(relaxed = true)
        
        startKoin {
            modules(module {
                single { walletRepository }
                single { tokenRepository }
                single { scanTokensUseCase }
            })
        }
        
        coEvery { walletRepository.getActiveWallet() } returns Result.Success(mockWallet)
        coEvery { tokenRepository.scanUserTokens(any(), any()) } returns listOf(mockToken)
        
        viewModel = TokenSelectorViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        stopKoin()
    }

    @Test
    fun `init should load active wallet and tokens`() = runTest {
        testScheduler.advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertEquals(mockWallet, state.activeWallet)
        assertEquals(1, state.tokens.size)
        assertEquals(mockToken, state.tokens[0])
        assertFalse(state.isLoading)
        
        coVerify { tokenRepository.scanUserTokens(mockWallet.address, ChainType.ETHEREUM) }
    }

    @Test
    fun `switchChain should update state and reload tokens`() = runTest {
        val bscToken = mockToken.copy(symbol = "BNB", chainType = ChainType.BSC)
        coEvery { tokenRepository.scanUserTokens(any(), eq(ChainType.BSC)) } returns listOf(bscToken)
        
        viewModel = TokenSelectorViewModel()
        testScheduler.advanceUntilIdle()
        
        // When
        viewModel.switchChain(ChainType.BSC)
        testScheduler.advanceUntilIdle()
        
        // Then
        val state = viewModel.uiState.value
        assertEquals(ChainType.BSC, state.currentChain)
        assertEquals(ChainType.BSC, ChainStateManager.getCurrentChain())
        assertEquals(1, state.tokens.size)
        assertEquals("BNB", state.tokens[0].symbol)
    }

    @Test
    fun `searchQuery should filter tokens`() = runTest {
        viewModel = TokenSelectorViewModel()
        testScheduler.advanceUntilIdle()
        
        // When
        viewModel.updateSearchQuery("TEST")
        val filtered = viewModel.filteredTokens.first()
        
        // Then
        assertEquals("TEST", viewModel.uiState.value.searchQuery)
        assertEquals(1, filtered.size)
        
        // When - non matching query
        viewModel.updateSearchQuery("UNKNOWN")
        val filteredEmpty = viewModel.filteredTokens.first()
        
        // Then
        assertEquals(0, filteredEmpty.size)
    }

    @Test
    fun `scanTokens should use useCase and update tokens`() = runTest {
        val scannedToken = mockToken.copy(symbol = "SCANNED")
        coEvery { scanTokensUseCase(any(), any()) } returns flowOf(Result.Success(listOf(scannedToken)))
        
        viewModel = TokenSelectorViewModel()
        testScheduler.advanceUntilIdle()
        
        // When
        viewModel.scanTokens()
        testScheduler.advanceUntilIdle()
        
        // Then
        assertFalse(viewModel.uiState.value.isScanning)
        assertEquals(1, viewModel.uiState.value.tokens.size)
        assertEquals("SCANNED", viewModel.uiState.value.tokens[0].symbol)
    }
    
    @Test
    fun `UTXO chain should load native token only`() = runTest {
        // When
        viewModel.switchChain(ChainType.BITCOIN)
        testScheduler.advanceUntilIdle()
        
        // Then
        val state = viewModel.uiState.value
        assertEquals(1, state.tokens.size)
        assertEquals("BTC", state.tokens[0].symbol)
        assertTrue(state.tokens[0].isNative)
        
        // Verify repository was NOT called for UTXO
        coVerify(exactly = 0) { tokenRepository.scanUserTokens(any(), eq(ChainType.BITCOIN)) }
    }
}

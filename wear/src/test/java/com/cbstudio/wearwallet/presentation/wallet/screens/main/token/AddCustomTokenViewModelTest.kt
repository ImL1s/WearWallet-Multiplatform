package com.cbstudio.wearwallet.presentation.wallet.screens.main.token

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.*
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import com.cbstudio.wearwallet.core.domain.repository.TokenRepository
import com.cbstudio.wearwallet.core.domain.repository.WalletRepository
import com.cbstudio.wearwallet.core.domain.model.WalletAccount
import com.cbstudio.wearwallet.core.domain.model.WalletType
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.common.Result

@OptIn(ExperimentalCoroutinesApi::class)
class AddCustomTokenViewModelTest {

    private lateinit var viewModel: AddCustomTokenViewModel
    private lateinit var tokenRepository: TokenRepository
    private lateinit var walletRepository: WalletRepository
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        tokenRepository = mock()
        walletRepository = mock()
        viewModel = AddCustomTokenViewModel(tokenRepository, walletRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `updateContractAddress should update state`() = runTest {
        viewModel.updateContractAddress("0x123")
        
        val state = viewModel.uiState.value
        assertEquals("0x123", state.contractAddress)
        assertNull(state.errorMessage)
    }

    @Test
    fun `addToken with empty address should set error`() = runTest {
        viewModel.updateContractAddress("")
        viewModel.addToken()
        testScheduler.advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertEquals("請輸入合約地址", state.errorMessage)
    }

    @Test
    fun `addToken success should call repository`() = runTest {
        // Given
        val mockWallet = WalletAccount(
            id = "1",
            name = "Test Wallet",
            address = "0xWalletAddress",
            publicKey = "0xPubKey",
            chainType = ChainType.ETHEREUM,
            walletType = WalletType.HOT_WALLET
        )
        whenever(walletRepository.getActiveWallet()).thenReturn(Result.Success(mockWallet))
        
        viewModel.updateContractAddress("0xToken")
        viewModel.updateSymbol("TEST")
        
        // When
        viewModel.addToken()
        testScheduler.advanceUntilIdle()
        
        // Then
        val state = viewModel.uiState.value
        assertTrue(state.isSuccess)
        verify(tokenRepository).saveUserToken(eq("0xWalletAddress"), any())
    }
    
    @Test
    fun `resetState should clear all fields`() = runTest {
        viewModel.updateContractAddress("0x123")
        viewModel.updateSymbol("TEST")
        
        // When
        viewModel.resetState()
        
        // Then
        val state = viewModel.uiState.value
        assertEquals("", state.contractAddress)
        assertEquals("", state.symbol)
        assertNull(state.errorMessage)
    }
}

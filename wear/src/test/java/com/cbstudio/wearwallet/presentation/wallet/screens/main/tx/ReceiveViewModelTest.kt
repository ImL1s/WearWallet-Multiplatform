package com.cbstudio.wearwallet.presentation.wallet.screens.main.tx

import androidx.compose.ui.graphics.ImageBitmap
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.WalletAccount
import com.cbstudio.wearwallet.core.domain.repository.WalletRepository
import com.cbstudio.wearwallet.presentation.wallet.ChainStateManager
import com.cbstudio.wearwallet.presentation.wallet.utils.QRCodeGenerator
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReceiveViewModelTest : KoinTest {

    private lateinit var viewModel: ReceiveViewModel
    private lateinit var walletRepository: WalletRepository
    private lateinit var qrCodeGenerator: QRCodeGenerator
    
    private val testDispatcher = StandardTestDispatcher()

    private val mockWallet = WalletAccount(
        id = "wallet-1",
        name = "Test Wallet",
        address = "0x123",
        publicKey = "0xpubkey",
        chainType = ChainType.ETHEREUM
    )

    private val mockImageBitmap = mockk<ImageBitmap>()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        walletRepository = mockk(relaxed = true)
        qrCodeGenerator = mockk(relaxed = true)
        
        startKoin {
            modules(module {
                single { walletRepository }
                single { qrCodeGenerator }
            })
        }
        
        ChainStateManager.setCurrentChain(ChainType.ETHEREUM)
        
        // Default behavior for QR
        coEvery { qrCodeGenerator.generateQrCode(any()) } returns mockImageBitmap
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        stopKoin()
    }

    @Test
    fun `init should load wallet address for current chain and generate QR`() = runTest {
        // Given
        coEvery { walletRepository.getActiveWallet() } returns Result.Success(mockWallet)
        
        // When
        viewModel = ReceiveViewModel()
        testScheduler.advanceUntilIdle()

        // Then
        // Verify address loading
        val state = viewModel.uiState.value
        assertEquals(mockWallet.address, state.walletAddress)
        assertEquals(mockWallet.name, state.walletName)
        assertEquals(ChainType.ETHEREUM.displayName, state.chainName)
        assertFalse(state.isLoading)
        
        // Verify QR generation
        coVerify { qrCodeGenerator.generateQrCode(mockWallet.address) }
        assertEquals(mockImageBitmap, state.qrCodeBitmap)
    }

    @Test
    fun `setChainType should update chain name and retain wallet address`() = runTest {
        // Given
        coEvery { walletRepository.getActiveWallet() } returns Result.Success(mockWallet)
        
        viewModel = ReceiveViewModel()
        testScheduler.advanceUntilIdle()
        
        // When
        viewModel.setChainType(ChainType.BITCOIN)
        testScheduler.advanceUntilIdle()
        
        // Then
        val state = viewModel.uiState.value
        assertEquals(mockWallet.address, state.walletAddress)
        assertEquals(ChainType.BITCOIN.displayName, state.chainName)
        
        // Verify QR generated for wallet address
        coVerify { qrCodeGenerator.generateQrCode(mockWallet.address) }
    }

    @Test
    fun `loadWalletAddress failure should update error`() = runTest {
        // Given
        coEvery { walletRepository.getActiveWallet() } returns Result.Failure(Exception("Load Failed"))
        
        // When
        viewModel = ReceiveViewModel()
        testScheduler.advanceUntilIdle()
        
        // Then
        val state = viewModel.uiState.value
        assertEquals("載入錢包失敗: Load Failed", state.error)
        assertFalse(state.isLoading)
    }

    @Test
    fun `onAddressCopied should show success state and reset`() = runTest {
        // Given
        viewModel = ReceiveViewModel()
        
        // When
        viewModel.onAddressCopied()
        
        // Then
        assertTrue(viewModel.uiState.value.copySuccess)
        
        // Advance time to verify auto-reset (3000ms delay in VM)
        testScheduler.advanceTimeBy(3001)
        testScheduler.runCurrent()
        assertFalse(viewModel.uiState.value.copySuccess)
    }

    @Test
    fun `toggleAddressDisplay should switch display mode`() = runTest {
        // Given
        viewModel = ReceiveViewModel()
        assertFalse(viewModel.uiState.value.showFullAddress) // Default false
        
        // When
        viewModel.toggleAddressDisplay()
        
        // Then
        assertTrue(viewModel.uiState.value.showFullAddress)
        
        // When again
        viewModel.toggleAddressDisplay()
        
        // Then
        assertFalse(viewModel.uiState.value.showFullAddress)
    }
}

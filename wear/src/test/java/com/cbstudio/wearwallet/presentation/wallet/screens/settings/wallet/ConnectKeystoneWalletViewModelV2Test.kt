package com.cbstudio.wearwallet.presentation.wallet.screens.settings.wallet

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.keystone.KeystoneWallet
import com.cbstudio.wearwallet.core.domain.model.keystone.KeystoneAddress
import com.cbstudio.wearwallet.core.keystone.KeystoneManager
import com.cbstudio.wearwallet.core.keystone.ScanResult
import com.cbstudio.wearwallet.presentation.service.WearCommunicationRepository
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.mockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectKeystoneWalletViewModelV2Test {

    @MockK
    private lateinit var keystoneManager: KeystoneManager

    @MockK
    private lateinit var communicationRepository: WearCommunicationRepository

    private lateinit var viewModel: ConnectKeystoneWalletViewModelV2

    private val testDispatcher = StandardTestDispatcher()
    
    private val mockKeystoneFlow = MutableSharedFlow<String>()

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        Dispatchers.setMain(testDispatcher)

        // Mock Singleton WearCommunicationRepository
        mockkObject(WearCommunicationRepository)
        every { WearCommunicationRepository.getInstance() } returns communicationRepository
        every { communicationRepository.keystoneConnectResults } returns mockKeystoneFlow

        viewModel = ConnectKeystoneWalletViewModelV2(keystoneManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is correct`() = runTest {
        val state = viewModel.uiState.value
        assertEquals("Keystone 服務已就緒", state.statusMessage)
        assertEquals(true, state.isInitialized)
        assertEquals(false, state.isLoading)
    }

    @Test
    fun `processQrCode handles complete scan and sync success`() = runTest {
        val qrData = "UR:BYTES/..."
        val urComplete = "UR-COMPLETE-DATA"
        
        // Mock ScanResult.Complete
        coEvery { keystoneManager.handleScan(qrData) } returns Result.Success(
            ScanResult.Complete(urComplete)
        )

        // Mock Sync Response (Import Wallet)
        val mockWalletAccount = com.cbstudio.wearwallet.core.domain.model.WalletAccount(
            id = "ks-123",
            name = "My Keystone",
            address = "0xAdd",
            publicKey = "",
            chainType = com.cbstudio.wearwallet.core.domain.model.ChainType.ETHEREUM,
            walletType = com.cbstudio.wearwallet.core.domain.model.WalletType.KEYSTONE
        )
        
        coEvery { keystoneManager.handleSyncResponse(urComplete, any()) } returns Result.Success(mockWalletAccount)

        // Act
        viewModel.processQrCode(qrData)
        advanceUntilIdle()

        // Assert
        val state = viewModel.uiState.value
        assertEquals("成功連接 My Keystone", state.statusMessage)
        assertEquals("ks-123", state.connectedWallet?.id)
    }
    
    @Test
    fun `processQrCode handles scan progress`() = runTest {
        val qrData = "UR:BYTES/1-3/..."
        
        // Mock ScanResult.Progress
        coEvery { keystoneManager.handleScan(qrData) } returns Result.Success(
            ScanResult.Progress(1, 3)
        )

        // Act
        viewModel.processQrCode(qrData)
        advanceUntilIdle()

        // Assert
        val state = viewModel.uiState.value
        assertEquals("掃描進度: 1/3", state.statusMessage)
    }

    @Test
    fun `processQrCode handles failure`() = runTest {
        val qrData = "INVALID"
        val errorMsg = "Invalid QR"
        
        coEvery { keystoneManager.handleScan(qrData) } returns Result.Failure(Exception(errorMsg))

        viewModel.processQrCode(qrData)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(errorMsg, state.errorMessage)
    }
}

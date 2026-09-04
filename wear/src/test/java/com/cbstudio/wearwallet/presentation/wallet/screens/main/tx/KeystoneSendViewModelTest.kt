package com.cbstudio.wearwallet.presentation.wallet.screens.main.tx

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.keystone.KeystoneSignatureResult
import com.cbstudio.wearwallet.core.domain.model.keystone.KeystoneTransaction
import com.cbstudio.wearwallet.core.keystone.ScanResult
import com.cbstudio.wearwallet.core.domain.repository.TransactionRepository
import com.cbstudio.wearwallet.core.domain.repository.WalletRepository
import com.cbstudio.wearwallet.core.domain.service.KeystoneService
import com.cbstudio.wearwallet.core.keystone.KeystoneManager
import com.cbstudio.wearwallet.core.domain.model.WalletAccount
import com.cbstudio.wearwallet.core.domain.model.ChainType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.mockito.kotlin.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class KeystoneSendViewModelTest : KoinTest {

    private val keystoneManager: KeystoneManager = mock()
    private val keystoneService: KeystoneService = mock()
    private val walletRepository: WalletRepository = mock()
    private val transactionRepository: TransactionRepository = mock()
    
    private lateinit var viewModel: KeystoneSendViewModel
    private val testDispatcher = StandardTestDispatcher()

    private val mockTxHex = "0x02" // Simplified
    private val mockTxJson = "{\"chainId\":\"1\", \"data\":\"0x123\"}" // Simplified KeystoneTransaction JSON

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        startKoin {
            modules(module {
                single { keystoneManager }
                single { keystoneService }
                single { walletRepository }
                single { transactionRepository }
            })
        }
        
        // Mock wallet
        val mockWallet = WalletAccount(
            id = "1", 
            name = "Test", 
            address = "0x123",
            publicKey = "0xpub",
            chainType = ChainType.ETHEREUM,
            isActive = true
        )
        
        runBlocking {
            whenever(walletRepository.getActiveWallet()).thenReturn(Result.Success(mockWallet))
            whenever(walletRepository.getWallet(any<String>())).thenReturn(Result.Success(mockWallet))
        }
        
        // Mock init generation
        viewModel = KeystoneSendViewModel(mockTxJson)
    }

    @After
    fun tearDown() {
        stopKoin()
        Dispatchers.resetMain()
    }

    @Test
    fun `test onScanClick updates step to SCAN_QR`() = runTest {
        viewModel.onScanClick()
        assertEquals(KeystoneSendStep.SCAN_QR, viewModel.uiState.value.step)
    }

    @Test
    fun `test handleScanResult updates progress`() = runTest {
        val mockData = "UR:BYTES/..."
        val progressResult = ScanResult.Progress(current = 1, total = 2)
        
        whenever(keystoneManager.handleScan(any<String>())).thenReturn(Result.Success(progressResult))
        
        viewModel.handleScanResult(mockData)
        advanceUntilIdle()
        
        assertEquals(0.5f, viewModel.uiState.value.scanProgress)
    }

    @Test
    fun `test handleScanResult complete parses signature and broadcasts`() = runTest {
        val mockData = "UR:ETH-SIGNATURE/..."
        val mockSignature = "0xabc123"
        val completeResult = ScanResult.Complete(data = mockData)
        
        whenever(keystoneManager.handleScan(any<String>())).thenReturn(Result.Success(completeResult))
        whenever(keystoneService.parseSignature(any<String>())).thenReturn(KeystoneSignatureResult.Success(mockSignature, "req1"))
        whenever(transactionRepository.sendTransaction(any<String>(), any<com.cbstudio.wearwallet.core.domain.model.ChainType>())).thenReturn("0xhash")
        
        viewModel.handleScanResult(mockData)
        advanceUntilIdle()
        
        assertEquals(KeystoneSendStep.SUCCESS, viewModel.uiState.value.step)
        assertEquals("0xhash", viewModel.uiState.value.txHash)
        assertEquals(mockSignature, viewModel.uiState.value.signature)
    }
    
    @Test
    fun `test handleScanResult failure updates error`() = runTest {
        whenever(keystoneManager.handleScan(any<String>())).thenReturn(Result.Failure(Exception("Scan failed")))
        
        viewModel.handleScanResult("bad_data")
        advanceUntilIdle()
        
        assertEquals("Scan failed", viewModel.uiState.value.error)
    }
}

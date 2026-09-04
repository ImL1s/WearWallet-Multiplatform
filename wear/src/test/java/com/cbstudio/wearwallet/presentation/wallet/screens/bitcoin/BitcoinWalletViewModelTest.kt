package com.cbstudio.wearwallet.presentation.wallet.screens.bitcoin

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.Network
import com.cbstudio.wearwallet.core.domain.model.WalletAccount
import com.cbstudio.wearwallet.core.domain.model.WalletType
import com.cbstudio.wearwallet.core.domain.repository.WalletRepository
import com.cbstudio.wearwallet.core.blockchain.adapter.BitcoinPlatformAdapter
import com.cbstudio.wearwallet.core.blockchain.api.BlockstreamApiClient
import com.cbstudio.wearwallet.core.blockchain.model.UTXO
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BitcoinWalletViewModelTest : KoinTest {

    private lateinit var viewModel: BitcoinWalletViewModel
    private lateinit var walletRepository: WalletRepository
    private lateinit var bitcoinAdapter: BitcoinPlatformAdapter
    private lateinit var blockstreamApiClient: BlockstreamApiClient
    
    private val testDispatcher = StandardTestDispatcher()

    private val mockBtcWallet = WalletAccount(
        id = "btc-wallet-1",
        name = "Bitcoin Wallet",
        address = "bc1qtest",
        publicKey = "0xpubkey",
        chainType = ChainType.BITCOIN,
        walletType = WalletType.HOT_WALLET
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        walletRepository = mockk(relaxed = true)
        bitcoinAdapter = mockk(relaxed = true)
        blockstreamApiClient = mockk(relaxed = true)
        
        startKoin {
            modules(module {
                single { walletRepository }
                single { bitcoinAdapter }
                single { blockstreamApiClient }
            })
        }
        
        viewModel = BitcoinWalletViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        stopKoin()
    }

    @Test
    fun `initializeWallet success should load wallet address`() = runTest {
        // Given
        coEvery { walletRepository.getAllWallets() } returns Result.Success(listOf(mockBtcWallet))
        coEvery { bitcoinAdapter.getBalance(any()) } returns 100000L
        coEvery { blockstreamApiClient.getUtxos(any()) } returns emptyList()
        coEvery { blockstreamApiClient.getFeeEstimates() } returns mapOf("medium" to 10.0)
        
        // When
        viewModel.initializeWallet("btc-wallet-1")
        testScheduler.advanceUntilIdle()
        
        // Then
        val state = viewModel.uiState.value
        assertEquals("bc1qtest", state.address)
        assertEquals(100000L, state.balance)
        assertFalse(state.isLoading)
    }

    @Test
    fun `initializeWallet wallet not found should set error`() = runTest {
        // Given
        coEvery { walletRepository.getAllWallets() } returns Result.Success(listOf(mockBtcWallet))
        
        // When
        viewModel.initializeWallet("non-existent-wallet")
        testScheduler.advanceUntilIdle()
        
        // Then
        assertEquals("找不到錢包", viewModel.uiState.value.error)
    }

    @Test
    fun `refreshBalance should update balance and UTXOs`() = runTest {
        // Given - Initialize first
        coEvery { walletRepository.getAllWallets() } returns Result.Success(listOf(mockBtcWallet))
        coEvery { bitcoinAdapter.getBalance("bc1qtest") } returns 500000L
        coEvery { blockstreamApiClient.getUtxos("bc1qtest") } returns listOf(
            UTXO(txid = "tx1", vout = 0, value = 500000L, confirmed = true)
        )
        coEvery { blockstreamApiClient.getFeeEstimates() } returns mapOf("medium" to 15.0)
        
        viewModel.initializeWallet("btc-wallet-1")
        testScheduler.advanceUntilIdle()
        
        // When
        viewModel.refreshBalance()
        testScheduler.advanceUntilIdle()
        
        // Then
        val state = viewModel.uiState.value
        assertEquals(500000L, state.balance)
        assertEquals(1, state.utxos.size)
    }

    @Test
    fun `switchNetwork should toggle network and refresh`() = runTest {
        // Given
        coEvery { walletRepository.getAllWallets() } returns Result.Success(listOf(mockBtcWallet))
        coEvery { bitcoinAdapter.getBalance(any()) } returns 0L
        coEvery { blockstreamApiClient.getUtxos(any()) } returns emptyList()
        coEvery { blockstreamApiClient.getFeeEstimates() } returns mapOf("medium" to 10.0)
        
        viewModel.initializeWallet("btc-wallet-1")
        testScheduler.advanceUntilIdle()
        
        // When
        viewModel.switchNetwork(true)
        testScheduler.advanceUntilIdle()
        
        // Then
        assertEquals(Network.BITCOIN_TESTNET, viewModel.uiState.value.selectedNetwork)
    }

    @Test
    fun `sendTransaction with invalid address should show error`() = runTest {
        // Given
        coEvery { walletRepository.getAllWallets() } returns Result.Success(listOf(mockBtcWallet))
        coEvery { bitcoinAdapter.getBalance(any()) } returns 100000L
        coEvery { blockstreamApiClient.getUtxos(any()) } returns emptyList()
        coEvery { blockstreamApiClient.getFeeEstimates() } returns mapOf("medium" to 10.0)
        every { bitcoinAdapter.validateAddress(any()) } returns false
        
        viewModel.initializeWallet("btc-wallet-1")
        viewModel.updateRecipientAddress("invalid")
        viewModel.updateSendAmount("0.001")
        testScheduler.advanceUntilIdle()
        
        // When
        viewModel.sendTransaction("password")
        testScheduler.advanceUntilIdle()
        
        // Then
        assertEquals("無效的接收地址", viewModel.uiState.value.error)
    }

    @Test
    fun `formatBalanceAsBTC should format correctly`() = runTest {
        // Given - Set balance via internal state (by initializing)
        coEvery { walletRepository.getAllWallets() } returns Result.Success(listOf(mockBtcWallet))
        coEvery { bitcoinAdapter.getBalance(any()) } returns 12345678L // 0.12345678 BTC
        coEvery { blockstreamApiClient.getUtxos(any()) } returns emptyList()
        coEvery { blockstreamApiClient.getFeeEstimates() } returns mapOf("medium" to 10.0)
        
        viewModel.initializeWallet("btc-wallet-1")
        testScheduler.advanceUntilIdle()
        
        // When
        val formatted = viewModel.formatBalanceAsBTC()
        
        // Then
        assertTrue(formatted.contains("0.12345678"))
    }
}

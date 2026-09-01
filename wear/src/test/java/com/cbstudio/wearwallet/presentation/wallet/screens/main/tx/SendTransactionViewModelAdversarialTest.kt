package com.cbstudio.wearwallet.presentation.wallet.screens.main.tx

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.Token
import com.cbstudio.wearwallet.core.domain.model.WalletAccount
import com.cbstudio.wearwallet.core.domain.model.WalletType
import com.cbstudio.wearwallet.core.domain.repository.TokenRepository
import com.cbstudio.wearwallet.core.domain.repository.WalletRepository
import com.cbstudio.wearwallet.core.domain.usecase.addressbook.GetAddressContactsUseCase
import com.cbstudio.wearwallet.core.domain.usecase.addressbook.SearchAddressBookUseCase
import com.cbstudio.wearwallet.core.domain.usecase.transaction.EstimateGasUseCase
import com.cbstudio.wearwallet.core.domain.usecase.transaction.SendTransactionUseCase
import com.cbstudio.wearwallet.presentation.wallet.ChainStateManager
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
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

import com.cbstudio.wearwallet.core.domain.model.context.ChainExecutionContext
import com.cbstudio.wearwallet.core.domain.repository.TransactionRepository

@OptIn(ExperimentalCoroutinesApi::class)
class SendTransactionViewModelAdversarialTest : KoinTest {

    private lateinit var viewModel: SendTransactionViewModel
    private lateinit var walletRepository: WalletRepository
    private lateinit var tokenRepository: TokenRepository
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var sendTransactionUseCase: SendTransactionUseCase
    private lateinit var estimateGasUseCase: EstimateGasUseCase
    private lateinit var getAddressContactsUseCase: GetAddressContactsUseCase
    private lateinit var searchAddressBookUseCase: SearchAddressBookUseCase
    
    private val testDispatcher = StandardTestDispatcher()

    private val mockWallet = WalletAccount(
        id = "wallet-1",
        name = "Test Wallet",
        address = "0x1234567890123456789012345678901234567890",
        publicKey = "0xpubkey",
        keyAlias = "ww_key_mock_wallet_1",
        keyBackend = "KEYSTORE",
        keyFormatVersion = 2,
        requiresAuth = true,
        chainType = ChainType.ETHEREUM,
        walletType = WalletType.HOT_WALLET,
        isActive = true
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        walletRepository = mockk(relaxed = true)
        tokenRepository = mockk(relaxed = true)
        transactionRepository = mockk(relaxed = true)
        sendTransactionUseCase = mockk(relaxed = true)
        estimateGasUseCase = mockk(relaxed = true)
        getAddressContactsUseCase = mockk(relaxed = true)
        searchAddressBookUseCase = mockk(relaxed = true)
        
        coEvery { transactionRepository.getNonce(any(), any<ChainExecutionContext>()) } returns 0L
        
        startKoin {
            modules(module {
                single { walletRepository }
                single { tokenRepository }
                single { transactionRepository }
                single { sendTransactionUseCase }
                single { estimateGasUseCase }
                single { getAddressContactsUseCase }
                single { searchAddressBookUseCase }
                single { mockk<com.cbstudio.wearwallet.core.security.SecureKeyManager>(relaxed = true) }
            })
        }
        
        ChainStateManager.setCurrentChain(ChainType.ETHEREUM)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        stopKoin()
    }

    @Test
    fun `UI fails closed when gas estimation fails - no hardcoded fallbacks`() = runTest {
        coEvery { walletRepository.getActiveWallet() } returns Result.Success(mockWallet)
        coEvery { walletRepository.getNativeBalance(any(), any()) } returns 10.0

        // Mock estimateGasUseCase failure
        coEvery { estimateGasUseCase(any(), any(), any(), any(), any()) } returns flowOf(
            Result.Failure(IllegalStateException("RPC Network Timeout"))
        )

        viewModel = SendTransactionViewModel()
        testScheduler.advanceUntilIdle()

        viewModel.setRecipientAddress("0x71C7656EC7ab88b098defB751B7401B5f6d8976F")
        viewModel.proceedToAmount()
        viewModel.setAmount("1.0")
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        // Gas estimation fields MUST be null (no hardcoded fallback like "5", "21000", "0.000105")
        assertNull(state.estimatedGasPrice, "Gas price must be null on failure")
        assertNull(state.estimatedGasLimit, "Gas limit must be null on failure")
        assertNull(state.estimatedTotalFee, "Total fee must be null on failure")
        assertTrue(state.error?.contains("Gas 估算失敗") == true, "Error message must indicate gas estimation failure")

        // Attempt proceedToConfirm() must be blocked
        viewModel.proceedToConfirm()
        assertEquals(SendTransactionViewModel.TransactionStep.INPUT_AMOUNT, viewModel.uiState.value.currentStep, "Must remain in INPUT_AMOUNT step")
        assertTrue(viewModel.uiState.value.error?.contains("Gas 估算未完成或失敗") == true)

        // Attempt sendTransaction() must be blocked
        viewModel.sendTransaction()
        testScheduler.advanceUntilIdle()
        assertEquals(SendTransactionViewModel.TransactionStep.FAILED, viewModel.uiState.value.currentStep)
        assertTrue(viewModel.uiState.value.error?.contains("Gas 估算未完成或失敗") == true)

        // Attempt setMaxAmount() must be blocked
        viewModel.setMaxAmount()
        assertTrue(viewModel.uiState.value.error?.contains("Gas 估算未完成或失敗") == true)
    }

    @Test
    fun `modifying recipient or amount invalidates confirmedSnapshot`() = runTest {
        coEvery { walletRepository.getActiveWallet() } returns Result.Success(mockWallet)
        coEvery { walletRepository.getNativeBalance(any(), any()) } returns 10.0
        val gasEstimation = EstimateGasUseCase.GasEstimation(
            weiGasPrice = com.cbstudio.wearwallet.core.domain.model.quantities.Wei.fromGwei(10),
            gasLimitObj = com.cbstudio.wearwallet.core.domain.model.quantities.GasLimit.fromDecimalString("21000"),
            totalFee = "0.00021"
        )
        coEvery { estimateGasUseCase(any(), any(), any(), any(), any()) } returns flowOf(
            Result.Success(gasEstimation)
        )

        viewModel = SendTransactionViewModel()
        testScheduler.advanceUntilIdle()

        viewModel.setRecipientAddress("0x71C7656EC7ab88b098defB751B7401B5f6d8976F")
        viewModel.proceedToAmount()
        viewModel.setAmount("1.0")
        testScheduler.advanceUntilIdle()

        viewModel.proceedToConfirm()
        testScheduler.advanceUntilIdle()
        val snapshotBefore = viewModel.uiState.value.confirmedSnapshot
        assertTrue(snapshotBefore != null, "Snapshot must be created on proceedToConfirm")

        // Modifying recipient after confirmation MUST invalidate confirmedSnapshot
        viewModel.setRecipientAddress("0x1111111111111111111111111111111111111111")
        assertNull(viewModel.uiState.value.confirmedSnapshot, "Recipient change must clear confirmedSnapshot")

        // Re-confirm
        testScheduler.advanceUntilIdle()
        viewModel.proceedToConfirm()
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.confirmedSnapshot != null)

        // Modifying amount after confirmation MUST invalidate confirmedSnapshot
        viewModel.setAmount("2.0")
        assertNull(viewModel.uiState.value.confirmedSnapshot, "Amount change must clear confirmedSnapshot")
    }

    @Test
    fun `selectToken updates selectedToken and invalidates confirmedSnapshot`() = runTest {
        coEvery { walletRepository.getActiveWallet() } returns Result.Success(mockWallet)
        coEvery { walletRepository.getNativeBalance(any(), any()) } returns 10.0
        val gasEstimation = EstimateGasUseCase.GasEstimation(
            weiGasPrice = com.cbstudio.wearwallet.core.domain.model.quantities.Wei.fromGwei(10),
            gasLimitObj = com.cbstudio.wearwallet.core.domain.model.quantities.GasLimit.fromDecimalString("21000"),
            totalFee = "0.00021"
        )
        coEvery { estimateGasUseCase(any(), any(), any(), any(), any()) } returns flowOf(
            Result.Success(gasEstimation)
        )

        viewModel = SendTransactionViewModel()
        testScheduler.advanceUntilIdle()

        viewModel.setRecipientAddress("0x71C7656EC7ab88b098defB751B7401B5f6d8976F")
        viewModel.proceedToAmount()
        viewModel.setAmount("1.0")
        testScheduler.advanceUntilIdle()

        viewModel.proceedToConfirm()
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.confirmedSnapshot != null, "Snapshot must exist before token selection")

        val mockToken = Token(
            id = "token-1",
            address = "0x1234567890123456789012345678901234567890",
            name = "Test Token",
            symbol = "TT",
            decimals = 18,
            chainType = ChainType.ETHEREUM,
            logoUrl = "https://example.com/icon.png",
            balance = "100.0"
        )
        val tokenBalanceDecimal = BigDecimal(mockToken.balance)
        assertTrue(tokenBalanceDecimal.compareTo(BigDecimal("100.0")) == 0)

        val genBefore = viewModel.getTransactionIntentGeneration()
        viewModel.selectToken(mockToken)
        val genAfter = viewModel.getTransactionIntentGeneration()

        assertTrue(genAfter > genBefore, "Generation must increment on token selection")
        assertEquals(mockToken, viewModel.uiState.value.selectedToken, "Selected token must be updated in UI state")
        assertNull(viewModel.uiState.value.confirmedSnapshot, "Snapshot must be invalidated when selected token changes")
    }
}

package com.cbstudio.wearwallet.presentation.wallet.screens.main.tx

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.Token
import com.cbstudio.wearwallet.core.domain.model.WalletAccount
import com.cbstudio.wearwallet.core.domain.model.WalletType
import com.cbstudio.wearwallet.core.domain.model.context.ChainExecutionContext
import com.cbstudio.wearwallet.core.domain.model.quantities.GasLimit
import com.cbstudio.wearwallet.core.domain.model.quantities.Wei
import com.cbstudio.wearwallet.core.domain.repository.TokenRepository
import com.cbstudio.wearwallet.core.domain.repository.TransactionRepository
import com.cbstudio.wearwallet.core.domain.repository.WalletRepository
import com.cbstudio.wearwallet.core.domain.usecase.addressbook.GetAddressContactsUseCase
import com.cbstudio.wearwallet.core.domain.usecase.addressbook.SearchAddressBookUseCase
import com.cbstudio.wearwallet.core.domain.usecase.transaction.EstimateGasUseCase
import com.cbstudio.wearwallet.core.domain.usecase.transaction.SendTransactionUseCase
import com.cbstudio.wearwallet.core.security.AuthOperation
import com.cbstudio.wearwallet.core.security.AuthenticationContext
import com.cbstudio.wearwallet.core.security.PlatformAuthHandle
import com.cbstudio.wearwallet.core.security.TestPlatformAuthenticator
import com.cbstudio.wearwallet.presentation.wallet.ChainStateManager
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

/**
 * Empirical Challenger 2 Test Suite for Wear UI Milestone 2 (Option A Auth & State Transitions).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Challenger2M2OptionAUiStressTest : KoinTest {

    private lateinit var viewModel: SendTransactionViewModel
    private lateinit var walletRepository: WalletRepository
    private lateinit var tokenRepository: TokenRepository
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var sendTransactionUseCase: SendTransactionUseCase
    private lateinit var estimateGasUseCase: EstimateGasUseCase
    private lateinit var getAddressContactsUseCase: GetAddressContactsUseCase
    private lateinit var searchAddressBookUseCase: SearchAddressBookUseCase

    private val testDispatcher = StandardTestDispatcher()

    private val mockHotWallet = WalletAccount(
        id = "hot-wallet-uuid-1",
        name = "Wear Hot Wallet",
        address = "0x71C7656EC7ab88b098defB751B7401B5f6d8976F",
        publicKey = "0x04pubkey",
        keyAlias = "hardware-key-alias-1",
        keyBackend = "KEYSTORE",
        keyFormatVersion = 2,
        requiresAuth = true,
        chainType = ChainType.ETHEREUM,
        walletType = WalletType.HOT_WALLET,
        isActive = true
    )

    private val standardGasEstimation = EstimateGasUseCase.GasEstimation(
        weiGasPrice = Wei.fromGwei(25),
        gasLimitObj = GasLimit.fromDecimalString("21000"),
        totalFee = "0.000525"
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

        coEvery { transactionRepository.getNonce(any(), any<ChainExecutionContext>()) } returns 7L
        coEvery { walletRepository.getActiveWallet() } returns Result.Success(mockHotWallet)
        coEvery { walletRepository.getNativeBalance(any(), any()) } returns 10.0
        coEvery { estimateGasUseCase(any(), any(), any(), any(), any(), any()) } returns flowOf(
            Result.Success(standardGasEstimation)
        )

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

    private fun setupViewModelToReviewed(): SendTransactionViewModel {
        val vm = SendTransactionViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.setRecipientAddress("0x71C7656EC7ab88b098defB751B7401B5f6d8976F")
        vm.proceedToAmount()
        vm.setAmount("2.5")
        testDispatcher.scheduler.advanceUntilIdle()

        vm.proceedToConfirm()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(SendTransactionViewModel.TransactionStep.REVIEWED, vm.uiState.value.currentStep)
        assertNotNull(vm.uiState.value.confirmedSnapshot)
        return vm
    }

    // =========================================================================
    // 1. Option A State Transitions: Full Pipeline & Lifecycle Safety
    // =========================================================================

    @Test
    fun `option A - complete successful biometric authorization pipeline`() = runTest {
        coEvery { sendTransactionUseCase(intent = any(), authContext = any()) } returns flow {
            emit(Result.Loading())
            delay(150)
            emit(Result.Success("0xsuccessful_tx_hash_option_a"))
        }

        viewModel = setupViewModelToReviewed()
        val snapshot = viewModel.uiState.value.confirmedSnapshot!!

        // Step 1: REVIEWED -> AUTH_REQUIRED
        viewModel.proceedToAuthorize()
        assertEquals(SendTransactionViewModel.TransactionStep.AUTH_REQUIRED, viewModel.uiState.value.currentStep)

        // Step 2: Biometric authentication succeeds
        val handle = TestPlatformAuthenticator.issueHandle(
            keyId = snapshot.keyAlias,
            operation = AuthOperation.SIGN,
            intentFingerprint = snapshot.signingDigestHex
        )
        viewModel.onBiometricAuthSuccess(handle)

        // Step 3: In-flight states (AUTHORIZED, SIGNING / BROADCASTING)
        testScheduler.advanceTimeBy(50)
        assertTrue(viewModel.uiState.value.isSubmitting)
        assertEquals(snapshot.signingDigestHex, viewModel.uiState.value.authorizedFingerprint)

        // Step 4: Final SUCCESS state
        testScheduler.advanceUntilIdle()
        assertEquals(SendTransactionViewModel.TransactionStep.BROADCASTED, viewModel.uiState.value.currentStep)
        assertEquals("0xsuccessful_tx_hash_option_a", viewModel.uiState.value.txHash)
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun `option A - app backgrounding invalidates in-progress authorization handle`() = runTest {
        viewModel = setupViewModelToReviewed()
        viewModel.proceedToAuthorize()
        assertEquals(SendTransactionViewModel.TransactionStep.AUTH_REQUIRED, viewModel.uiState.value.currentStep)

        // User switches app / screen locks while prompt is open
        viewModel.onAppBackgrounded()
        assertEquals(SendTransactionViewModel.TransactionStep.AUTH_CANCELLED, viewModel.uiState.value.currentStep)
        assertNull(viewModel.uiState.value.authorizedFingerprint)
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun `option A - auth cancellation and expiration transitions fail closed`() = runTest {
        viewModel = setupViewModelToReviewed()

        // 1. Cancellation
        viewModel.proceedToAuthorize()
        viewModel.onAuthCancel()
        assertEquals(SendTransactionViewModel.TransactionStep.AUTH_CANCELLED, viewModel.uiState.value.currentStep)

        // Re-confirm
        viewModel.proceedToConfirm()
        testScheduler.advanceUntilIdle()
        assertEquals(SendTransactionViewModel.TransactionStep.REVIEWED, viewModel.uiState.value.currentStep)

        // 2. Expiration
        viewModel.proceedToAuthorize()
        viewModel.onAuthExpired()
        assertEquals(SendTransactionViewModel.TransactionStep.AUTH_EXPIRED, viewModel.uiState.value.currentStep)
        assertNull(viewModel.uiState.value.confirmedSnapshot)
    }

    @Test
    fun `option A - auth error transitions to FAILED state with descriptive message`() = runTest {
        viewModel = setupViewModelToReviewed()
        viewModel.proceedToAuthorize()

        viewModel.onAuthError("Biometric sensor locked out")
        assertEquals(SendTransactionViewModel.TransactionStep.FAILED, viewModel.uiState.value.currentStep)
        assertTrue(viewModel.uiState.value.error?.contains("Biometric sensor locked out") == true)
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    // =========================================================================
    // 2. Concurrency & Mutex Re-Entrancy Stress Test
    // =========================================================================

    @Test
    fun `concurrency stress - 100 rapid concurrent submissions dropped by Mutex without deadlock`() = runTest {
        val executionCounter = AtomicInteger(0)
        coEvery { sendTransactionUseCase(intent = any(), authContext = any()) } answers {
            executionCounter.incrementAndGet()
            flow {
                emit(Result.Loading())
                delay(300)
                emit(Result.Success("0xsingle_execution_hash"))
            }
        }

        viewModel = setupViewModelToReviewed()
        val snapshot = viewModel.uiState.value.confirmedSnapshot!!

        // Launch 100 concurrent triggers to onBiometricAuthSuccess
        val jobs = (1..100).map {
            launch {
                val handle = TestPlatformAuthenticator.issueHandle(
                    keyId = snapshot.keyAlias,
                    operation = AuthOperation.SIGN,
                    intentFingerprint = snapshot.signingDigestHex
                )
                viewModel.onBiometricAuthSuccess(handle)
            }
        }

        testScheduler.advanceTimeBy(50)
        assertEquals(1, executionCounter.get(), "Mutex must restrict execution to strictly 1 active use case call")

        testScheduler.advanceUntilIdle()
        assertEquals(1, executionCounter.get())
        assertEquals(SendTransactionViewModel.TransactionStep.BROADCASTED, viewModel.uiState.value.currentStep)
        assertEquals("0xsingle_execution_hash", viewModel.uiState.value.txHash)
    }

    // =========================================================================
    // 3. Reactive Field Invalidation & Tamper Resistance
    // =========================================================================

    @Test
    fun `tamper resistance - any input field modification after REVIEWED immediately revokes snapshot`() = runTest {
        viewModel = setupViewModelToReviewed()
        assertNotNull(viewModel.uiState.value.confirmedSnapshot)

        // Case A: Recipient changed
        viewModel.setRecipientAddress("0x1111111111111111111111111111111111111111")
        assertNull(viewModel.uiState.value.confirmedSnapshot, "Recipient change must clear snapshot")

        // Wait for gas estimation triggered by setRecipientAddress
        testScheduler.advanceUntilIdle()

        // Re-confirm
        viewModel.proceedToConfirm()
        testScheduler.advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.confirmedSnapshot)

        // Case B: Amount changed
        viewModel.setAmount("0.05")
        assertNull(viewModel.uiState.value.confirmedSnapshot, "Amount change must clear snapshot")

        // Wait for gas estimation triggered by setAmount
        testScheduler.advanceUntilIdle()

        // Re-confirm
        viewModel.proceedToConfirm()
        testScheduler.advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.confirmedSnapshot)

        // Case C: Gas parameter changed
        viewModel.updateGasParameters(Wei.fromGwei(50), GasLimit.fromDecimalString("30000"))
        assertNull(viewModel.uiState.value.confirmedSnapshot, "Gas update must clear snapshot")
    }
}



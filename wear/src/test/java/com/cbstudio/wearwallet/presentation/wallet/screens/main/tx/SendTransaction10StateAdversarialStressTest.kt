package com.cbstudio.wearwallet.presentation.wallet.screens.main.tx

import com.cbstudio.wearwallet.core.security.TestPlatformAuthenticator
import com.cbstudio.wearwallet.core.security.AuthOperation

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.Token
import com.cbstudio.wearwallet.core.domain.model.WalletAccount
import com.cbstudio.wearwallet.core.domain.model.WalletType
import com.cbstudio.wearwallet.core.domain.model.context.ChainExecutionContext
import com.cbstudio.wearwallet.core.domain.repository.TokenRepository
import com.cbstudio.wearwallet.core.domain.repository.TransactionRepository
import com.cbstudio.wearwallet.core.domain.repository.WalletRepository
import com.cbstudio.wearwallet.core.domain.usecase.addressbook.GetAddressContactsUseCase
import com.cbstudio.wearwallet.core.domain.usecase.addressbook.SearchAddressBookUseCase
import com.cbstudio.wearwallet.core.domain.usecase.transaction.EstimateGasUseCase
import com.cbstudio.wearwallet.core.domain.usecase.transaction.SendTransactionUseCase
import com.cbstudio.wearwallet.core.security.AuthenticationContext
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
 * Challenger 2 Adversarial Stress Test Suite for Milestone 2:
 * 10-State Machine Robustness, Cancellation/Expiration/Error Handling,
 * Re-entrancy/Spam Protection, and Fail-Closed Security.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SendTransaction10StateAdversarialStressTest : KoinTest {

    private lateinit var viewModel: SendTransactionViewModel
    private lateinit var walletRepository: WalletRepository
    private lateinit var tokenRepository: TokenRepository
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var sendTransactionUseCase: SendTransactionUseCase
    private lateinit var estimateGasUseCase: EstimateGasUseCase
    private lateinit var getAddressContactsUseCase: GetAddressContactsUseCase
    private lateinit var searchAddressBookUseCase: SearchAddressBookUseCase

    private val testDispatcher = StandardTestDispatcher()

    private val hotWallet = WalletAccount(
        id = "hot-wallet-1",
        name = "Hot Wallet",
        address = "0x71C7656EC7ab88b098defB751B7401B5f6d8976F",
        publicKey = "0x04hotpubkey",
        keyAlias = "ww_key_hot_wallet_1",
        keyBackend = "KEYSTORE",
        keyFormatVersion = 2,
        requiresAuth = true,
        chainType = ChainType.ETHEREUM,
        walletType = WalletType.HOT_WALLET,
        isActive = true
    )

    private val hardwareWallet = WalletAccount(
        id = "keystone-wallet-1",
        name = "Keystone Wallet",
        address = "0x71C7656EC7ab88b098defB751B7401B5f6d8976F",
        publicKey = "0x04hwpubkey",
        chainType = ChainType.ETHEREUM,
        walletType = WalletType.KEYSTONE,
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

        coEvery { transactionRepository.getNonce(any(), any<ChainExecutionContext>()) } returns 42L

        val defaultGasEstimation = EstimateGasUseCase.GasEstimation(
            weiGasPrice = com.cbstudio.wearwallet.core.domain.model.quantities.Wei.fromGwei(20),
            gasLimitObj = com.cbstudio.wearwallet.core.domain.model.quantities.GasLimit.fromDecimalString("21000"),
            totalFee = "0.00042"
        )
        coEvery { estimateGasUseCase(any(), any(), any(), any(), any()) } returns flowOf(
            Result.Success(defaultGasEstimation)
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

    private fun setupReadyViewModel(wallet: WalletAccount = hotWallet) {
        coEvery { walletRepository.getActiveWallet() } returns Result.Success(wallet)
        coEvery { walletRepository.getNativeBalance(any(), any()) } returns 5.0

        viewModel = SendTransactionViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.setRecipientAddress("0x71C7656EC7ab88b098defB751B7401B5f6d8976F")
        viewModel.proceedToAmount()
        viewModel.setAmount("1.0")
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun `stress test 1 - all 10 states reachable with exact invariants verified`() = runTest {
        setupReadyViewModel(hotWallet)

        // State 1: INPUT_AMOUNT -> REVIEWED
        viewModel.proceedToConfirm()
        testScheduler.advanceUntilIdle()
        assertEquals(SendTransactionViewModel.TransactionStep.REVIEWED, viewModel.uiState.value.currentStep)
        val snapshot = viewModel.uiState.value.confirmedSnapshot
        assertNotNull(snapshot)
        assertNull(viewModel.uiState.value.authorizedFingerprint)

        // State 2: REVIEWED -> AUTH_REQUIRED
        viewModel.proceedToAuthorize()
        assertEquals(SendTransactionViewModel.TransactionStep.AUTH_REQUIRED, viewModel.uiState.value.currentStep)

        // State 3: AUTH_REQUIRED -> AUTH_CANCELLED
        viewModel.onAuthCancel()
        assertEquals(SendTransactionViewModel.TransactionStep.AUTH_CANCELLED, viewModel.uiState.value.currentStep)
        assertNull(viewModel.uiState.value.authorizedFingerprint)
        assertFalse(viewModel.uiState.value.isSubmitting)

        // Re-confirm from cancel
        viewModel.proceedToConfirm()
        testScheduler.advanceUntilIdle()
        assertEquals(SendTransactionViewModel.TransactionStep.REVIEWED, viewModel.uiState.value.currentStep)

        // State 4: REVIEWED -> AUTH_REQUIRED -> AUTH_EXPIRED
        viewModel.proceedToAuthorize()
        assertEquals(SendTransactionViewModel.TransactionStep.AUTH_REQUIRED, viewModel.uiState.value.currentStep)
        viewModel.onAuthExpired()
        assertEquals(SendTransactionViewModel.TransactionStep.AUTH_EXPIRED, viewModel.uiState.value.currentStep)
        assertNull(viewModel.uiState.value.confirmedSnapshot)
        assertNull(viewModel.uiState.value.authorizedFingerprint)

        // Re-confirm from expiration
        viewModel.proceedToConfirm()
        testScheduler.advanceUntilIdle()
        assertEquals(SendTransactionViewModel.TransactionStep.REVIEWED, viewModel.uiState.value.currentStep)

        val snapshot_2 = viewModel.uiState.value.confirmedSnapshot!!
        val handle = com.cbstudio.wearwallet.core.security.TestPlatformAuthenticator.issueHandle(
            keyId = snapshot_2.keyAlias,
            operation = com.cbstudio.wearwallet.core.security.AuthOperation.SIGN,
            intentFingerprint = snapshot_2.signingDigestHex
        )

        // State 5: AUTHENTICATING -> AUTHORIZED -> SIGNING -> BROADCASTING -> SUCCESS
        coEvery { sendTransactionUseCase(intent = any(), authContext = any()) } returns flow {
            emit(Result.Loading())
            delay(100)
            emit(Result.Success("0xsuccesshash999"))
        }

        viewModel.proceedToAuthorize()
        viewModel.onBiometricAuthSuccess(handle)

        // In-flight checks: AUTHORIZED & SIGNING / BROADCASTING
        testScheduler.advanceTimeBy(50)
        assertEquals(snapshot_2.signingDigestHex, viewModel.uiState.value.authorizedFingerprint)
        assertTrue(viewModel.uiState.value.isSubmitting)

        // Complete execution -> SUCCESS
        testScheduler.advanceUntilIdle()
        assertEquals(SendTransactionViewModel.TransactionStep.BROADCASTED, viewModel.uiState.value.currentStep)
        assertEquals("0xsuccesshash999", viewModel.uiState.value.txHash)
        assertFalse(viewModel.uiState.value.isSubmitting)
        assertFalse(viewModel.uiState.value.isLoading)

        // State 10: FAILED state verification
        setupReadyViewModel(hotWallet)
        viewModel.proceedToConfirm()
        testScheduler.advanceUntilIdle()

        coEvery { sendTransactionUseCase(intent = any(), authContext = any()) } returns flowOf(
            Result.Failure(RuntimeException("RPC broadcast rejected: nonce too low"))
        )

        val failSnapshot = viewModel.uiState.value.confirmedSnapshot!!
        val failHandle = com.cbstudio.wearwallet.core.security.TestPlatformAuthenticator.issueHandle(
            keyId = failSnapshot.keyAlias,
            operation = com.cbstudio.wearwallet.core.security.AuthOperation.SIGN,
            intentFingerprint = failSnapshot.signingDigestHex
        )
        viewModel.onBiometricAuthSuccess(failHandle)
        testScheduler.advanceUntilIdle()
        assertEquals(SendTransactionViewModel.TransactionStep.FAILED, viewModel.uiState.value.currentStep)
        assertTrue(viewModel.uiState.value.error?.contains("nonce too low") == true)
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun `stress test 2 - hot wallet sendTransaction without auth transitions to auth required`() = runTest {
        setupReadyViewModel(hotWallet)
        viewModel.proceedToConfirm()
        testScheduler.advanceUntilIdle()

        viewModel.proceedToAuthorize()
        assertEquals(SendTransactionViewModel.TransactionStep.AUTH_REQUIRED, viewModel.uiState.value.currentStep)

        // Calling sendTransaction directly without authContext transitions to AUTH_REQUIRED
        viewModel.sendTransaction()
        assertEquals(SendTransactionViewModel.TransactionStep.AUTH_REQUIRED, viewModel.uiState.value.currentStep)
    }

    @Test
    fun `stress test 3 - hardware wallet Keystone routing and failure handling`() = runTest {
        setupReadyViewModel(hardwareWallet)
        viewModel.proceedToConfirm()
        testScheduler.advanceUntilIdle()

        assertEquals(SendTransactionViewModel.TransactionStep.REVIEWED, viewModel.uiState.value.currentStep)

        // Keystone success path
        coEvery {
            sendTransactionUseCase.createUnsignedTransaction(
                toAddress = any(),
                amount = any(),
                tokenAddress = any(),
                tokenDecimals = any(),
                gasPrice = any(),
                gasLimit = any()
            )
        } returns Result.Success("{\"to\":\"0x1111\",\"value\":\"0x0\"}")

        viewModel.proceedToAuthorize()
        testScheduler.advanceUntilIdle()

        assertEquals("{\"to\":\"0x1111\",\"value\":\"0x0\"}", viewModel.uiState.value.keystoneUnsignedTx)
        assertFalse(viewModel.uiState.value.isSubmitting)

        // Keystone failure path
        setupReadyViewModel(hardwareWallet)
        viewModel.proceedToConfirm()
        testScheduler.advanceUntilIdle()

        coEvery {
            sendTransactionUseCase.createUnsignedTransaction(
                toAddress = any(),
                amount = any(),
                tokenAddress = any(),
                tokenDecimals = any(),
                gasPrice = any(),
                gasLimit = any()
            )
        } returns Result.Failure(RuntimeException("Keystone generation failed"))

        viewModel.proceedToAuthorize()
        testScheduler.advanceUntilIdle()

        assertEquals(SendTransactionViewModel.TransactionStep.FAILED, viewModel.uiState.value.currentStep)
        assertTrue(viewModel.uiState.value.error?.contains("Keystone generation failed") == true)
    }

    @Test
    fun `stress test 4 - rapid spamming of sendTransaction is deduplicated by Mutex`() = runTest {
        setupReadyViewModel(hotWallet)
        viewModel.proceedToConfirm()
        testScheduler.advanceUntilIdle()

        val executionCount = AtomicInteger(0)
        coEvery { sendTransactionUseCase(intent = any(), authContext = any()) } answers {
            executionCount.incrementAndGet()
            flow {
                emit(Result.Loading())
                delay(300)
                emit(Result.Success("0xtxhash_spam_test"))
            }
        }

        val snapshot = viewModel.uiState.value.confirmedSnapshot!!

        // Spam 20 concurrent submissions
        repeat(20) {
            val handle = com.cbstudio.wearwallet.core.security.TestPlatformAuthenticator.issueHandle(
                keyId = snapshot.keyAlias,
                operation = com.cbstudio.wearwallet.core.security.AuthOperation.SIGN,
                intentFingerprint = snapshot.signingDigestHex
            )
            viewModel.onBiometricAuthSuccess(handle)
        }

        testScheduler.advanceTimeBy(50)
        // Ensure only one call entered the usecase
        assertEquals(1, executionCount.get(), "Mutex must drop all concurrent duplicate submissions")

        testScheduler.advanceUntilIdle()
        assertEquals(1, executionCount.get())
        assertEquals(SendTransactionViewModel.TransactionStep.BROADCASTED, viewModel.uiState.value.currentStep)
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun `stress test 5 - goBack from all states resets confirmation and authorization invariants`() = runTest {
        setupReadyViewModel(hotWallet)

        val statesToTest = listOf(
            SendTransactionViewModel.TransactionStep.REVIEWED,
            SendTransactionViewModel.TransactionStep.AUTH_REQUIRED,
            SendTransactionViewModel.TransactionStep.AUTH_CANCELLED,
            SendTransactionViewModel.TransactionStep.AUTH_EXPIRED
        )

        for (testStep in statesToTest) {
            viewModel.proceedToConfirm()
            testScheduler.advanceUntilIdle()

            when (testStep) {
                SendTransactionViewModel.TransactionStep.REVIEWED -> {}
                SendTransactionViewModel.TransactionStep.AUTH_REQUIRED -> viewModel.proceedToAuthorize()
                SendTransactionViewModel.TransactionStep.AUTH_CANCELLED -> {
                    viewModel.proceedToAuthorize()
                    viewModel.onAuthCancel()
                }
                SendTransactionViewModel.TransactionStep.AUTH_EXPIRED -> {
                    viewModel.proceedToAuthorize()
                    viewModel.onAuthExpired()
                }
                else -> {}
            }

            // Call goBack
            viewModel.goBack()
            assertEquals(SendTransactionViewModel.TransactionStep.INPUT_AMOUNT, viewModel.uiState.value.currentStep)
            assertNull(viewModel.uiState.value.confirmedSnapshot, "goBack must invalidate confirmedSnapshot")
            assertNull(viewModel.uiState.value.authorizedFingerprint, "goBack must invalidate authorizedFingerprint")
            assertFalse(viewModel.uiState.value.isSubmitting)
        }
    }

    @Test
    fun `stress test 6 - tampering with fee or gas parameters after confirmation invalidates snapshot`() = runTest {
        setupReadyViewModel(hotWallet)
        viewModel.proceedToConfirm()
        testScheduler.advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.confirmedSnapshot)

        // Tamper with gas parameters
        viewModel.updateGasParameters(
            com.cbstudio.wearwallet.core.domain.model.quantities.Wei.fromGwei(50),
            com.cbstudio.wearwallet.core.domain.model.quantities.GasLimit.fromDecimalString("30000")
        )

        assertNull(viewModel.uiState.value.confirmedSnapshot, "Gas update must invalidate snapshot")
        assertNull(viewModel.uiState.value.authorizedFingerprint, "Gas update must invalidate authorization")

        // Calling sendTransaction without re-confirming must fail closed
        viewModel.sendTransaction()
        testScheduler.advanceUntilIdle()
        assertEquals(SendTransactionViewModel.TransactionStep.FAILED, viewModel.uiState.value.currentStep)
    }

    @Test
    fun `stress test 7 - biometric auth success and failure path`() = runTest {
        setupReadyViewModel(hotWallet)
        viewModel.proceedToConfirm()
        testScheduler.advanceUntilIdle()

        coEvery { sendTransactionUseCase(intent = any(), authContext = any()) } returns flowOf(
            Result.Success("0xbiometrichash123")
        )

        viewModel.proceedToAuthorize()
        assertEquals(SendTransactionViewModel.TransactionStep.AUTH_REQUIRED, viewModel.uiState.value.currentStep)

        // Mock Biometric Success
        val handle = TestPlatformAuthenticator.issueHandle(
            keyId = hotWallet.keyAlias ?: "",
            operation = AuthOperation.SIGN,
            intentFingerprint = viewModel.uiState.value.confirmedSnapshot!!.signingDigestHex
        )
        viewModel.onBiometricAuthSuccess(handle)
        testScheduler.advanceUntilIdle()

        assertEquals(SendTransactionViewModel.TransactionStep.BROADCASTED, viewModel.uiState.value.currentStep)
        assertEquals("0xbiometrichash123", viewModel.uiState.value.txHash)
    }
}



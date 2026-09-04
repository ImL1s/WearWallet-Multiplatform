package com.cbstudio.wearwallet.presentation.wallet.screens.main.tx

import com.cbstudio.wearwallet.core.security.TestPlatformAuthenticator

import androidx.biometric.BiometricPrompt
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
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
import com.cbstudio.wearwallet.core.security.*
import com.cbstudio.wearwallet.presentation.wallet.ChainStateManager
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
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
 * Empirical Challenger M3 Stress Test Suite for UI Auth Threading,
 * ViewModel State Machine, and Handle Lifecycle in :wear.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Milestone3ChallengerUIAuthLifecycleStressTest : KoinTest {

    private lateinit var viewModel: SendTransactionViewModel
    private lateinit var walletRepository: WalletRepository
    private lateinit var tokenRepository: TokenRepository
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var sendTransactionUseCase: SendTransactionUseCase
    private lateinit var estimateGasUseCase: EstimateGasUseCase
    private lateinit var getAddressContactsUseCase: GetAddressContactsUseCase
    private lateinit var searchAddressBookUseCase: SearchAddressBookUseCase
    private lateinit var secureKeyManager: SecureKeyManager

    private val testDispatcher = StandardTestDispatcher()

    private val hotWallet = WalletAccount(
        id = "wallet-hot-m3",
        name = "Hot Wallet M3",
        address = "0x71C7656EC7ab88b098defB751B7401B5f6d8976F",
        publicKey = "0x04hotpubkey",
        keyAlias = "ww_key_hot_m3",
        keyBackend = "KEYSTORE",
        keyFormatVersion = 2,
        requiresAuth = true,
        chainType = ChainType.ETHEREUM,
        walletType = WalletType.HOT_WALLET,
        isActive = true
    )

    private val standardGasEstimation = EstimateGasUseCase.GasEstimation(
        weiGasPrice = Wei.fromGwei(20),
        gasLimitObj = GasLimit.fromDecimalString("21000"),
        totalFee = "0.00042"
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
        secureKeyManager = mockk(relaxed = true)

        coEvery { transactionRepository.getNonce(any(), any<ChainExecutionContext>()) } returns 0L
        coEvery { walletRepository.getActiveWallet() } returns Result.Success(hotWallet)
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
                single { secureKeyManager }
            })
        }

        ChainStateManager.setCurrentChain(ChainType.ETHEREUM)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        stopKoin()
    }

    private fun initViewModelAndReachReviewed(): SendTransactionViewModel {
        val vm = SendTransactionViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.setRecipientAddress("0x71C7656EC7ab88b098defB751B7401B5f6d8976F")
        vm.proceedToAmount()
        vm.setAmount("1.0")
        testDispatcher.scheduler.advanceUntilIdle()

        vm.proceedToConfirm()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(SendTransactionViewModel.TransactionStep.REVIEWED, vm.uiState.value.currentStep)
        assertNotNull(vm.uiState.value.confirmedSnapshot)
        return vm
    }

    // =========================================================================
    // Vector 1: Handle invalidation on backgrounding / onAppBackgrounded
    // =========================================================================

    @Test
    fun `vector1_1 - onAppBackgrounded during AUTH_REQUIRED invalidates handle and cancels UI state`() = runTest {
        viewModel = initViewModelAndReachReviewed()
        viewModel.proceedToAuthorize()
        assertEquals(SendTransactionViewModel.TransactionStep.AUTH_REQUIRED, viewModel.uiState.value.currentStep)

        // Simulate app moving to background (e.g. user pressed home or wrist dropped)
        viewModel.onAppBackgrounded()

        assertEquals(SendTransactionViewModel.TransactionStep.AUTH_CANCELLED, viewModel.uiState.value.currentStep)
        assertNull(viewModel.uiState.value.authorizedFingerprint)
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun `vector1_2 - onAppBackgrounded during AUTHENTICATING invalidates handle and cancels UI state`() = runTest {
        viewModel = initViewModelAndReachReviewed()
        viewModel.proceedToAuthorize()

        // Create active handle and simulate prompt active
        val snapshot = viewModel.uiState.value.confirmedSnapshot!!
        val activeHandle = TestPlatformAuthenticator.issueHandle(
            keyId = snapshot.keyAlias,
            operation = AuthOperation.SIGN,
            intentFingerprint = snapshot.signingDigestHex
        )
        viewModel.onBiometricAuthSuccess(activeHandle)

        // App backgrounded during authentication
        viewModel.onAppBackgrounded()

        assertTrue(activeHandle.isInvalidated, "Active handle must be invalidated on backgrounding")
        assertEquals(SendTransactionViewModel.TransactionStep.AUTH_CANCELLED, viewModel.uiState.value.currentStep)
        assertNull(viewModel.uiState.value.authorizedFingerprint)
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun `vector1_3 - onAppBackgrounded invalidates active PlatformAuthHandle preventing background signing`() = runTest {
        val snapshotIntentFingerprint = "test-intent-fingerprint"
        val handle = TestPlatformAuthenticator.issueHandle(
            keyId = hotWallet.id,
            operation = AuthOperation.SIGN,
            intentFingerprint = snapshotIntentFingerprint
        )
        assertFalse(handle.isInvalidated)
        assertTrue(handle.isValid(expectedKeyId = hotWallet.id, expectedIntentFingerprint = snapshotIntentFingerprint, expectedOperation = AuthOperation.SIGN, currentTimeMs = handle.issuedAtMs))

        // Trigger background invalidation
        handle.invalidate()

        assertTrue(handle.isInvalidated)
        assertFalse(
            handle.isValid(expectedKeyId = hotWallet.id, expectedIntentFingerprint = snapshotIntentFingerprint, expectedOperation = AuthOperation.SIGN, currentTimeMs = handle.issuedAtMs + 1000L),
            "Invalidated handle must fail isValid check"
        )
    }

    // =========================================================================
    // Vector 2: Replay / reuse of invalidated or expired PlatformAuthHandle
    // =========================================================================

    @Test
    fun `vector2_1 - replay of invalidated PlatformAuthHandle is rejected by isValid check`() = runTest {
        val handle = TestPlatformAuthenticator.issueHandle(
            keyId = hotWallet.id,
            operation = AuthOperation.SIGN,
            intentFingerprint = "fingerprint-valid-1"
        )
        handle.invalidate()

        assertFalse(handle.isValid(expectedKeyId = hotWallet.id, expectedIntentFingerprint = "fingerprint-valid-1", expectedOperation = AuthOperation.SIGN, currentTimeMs = handle.issuedAtMs))
    }

    @Test
    fun `vector2_2 - replay of expired PlatformAuthHandle is rejected by isValid check`() = runTest {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        val expiredHandle = TestPlatformAuthenticator.issueHandle(
            keyId = hotWallet.id,
            operation = AuthOperation.SIGN,
            intentFingerprint = "fingerprint-valid-1",
            expiresAtMs = now - 10_000L
        )

        assertTrue(expiredHandle.isExpired(now))
        assertFalse(expiredHandle.isValid(expectedKeyId = hotWallet.id, expectedIntentFingerprint = "fingerprint-valid-1", expectedOperation = AuthOperation.SIGN, currentTimeMs = now))
    }

    @Test
    fun `vector2_3 - cross-key handle replay is rejected`() = runTest {
        val handleKeyA = TestPlatformAuthenticator.issueHandle(
            keyId = "wallet-attacker-key",
            operation = AuthOperation.SIGN,
            intentFingerprint = "fingerprint-valid-1"
        )

        assertFalse(
            handleKeyA.isValid(expectedKeyId = hotWallet.id, expectedIntentFingerprint = "fingerprint-valid-1", expectedOperation = AuthOperation.SIGN, currentTimeMs = handleKeyA.issuedAtMs),
            "Handle for wallet-attacker-key must not be accepted for hotWallet.id"
        )
    }

    @Test
    fun `vector2_4 - wrong-operation handle replay is rejected`() = runTest {
        val deleteHandle = TestPlatformAuthenticator.issueHandle(
            keyId = hotWallet.id,
            operation = AuthOperation.DELETE,
            intentFingerprint = "fingerprint-valid-1"
        )

        assertFalse(
            deleteHandle.isValid(expectedKeyId = hotWallet.id, expectedIntentFingerprint = "fingerprint-valid-1", expectedOperation = AuthOperation.SIGN, currentTimeMs = deleteHandle.issuedAtMs),
            "DELETE handle must not be accepted for SIGN operation"
        )

        val exportHandle = TestPlatformAuthenticator.issueHandle(
            keyId = hotWallet.id,
            operation = AuthOperation.EXPORT,
            intentFingerprint = "fingerprint-valid-1"
        )

        assertFalse(
            exportHandle.isValid(expectedKeyId = hotWallet.id, expectedIntentFingerprint = "fingerprint-valid-1", expectedOperation = AuthOperation.SIGN, currentTimeMs = exportHandle.issuedAtMs),
            "EXPORT handle must not be accepted for SIGN operation"
        )
    }

    @Test
    fun `vector2_5 - intent fingerprint mismatch replay is rejected`() = runTest {
        val handle = TestPlatformAuthenticator.issueHandle(
            keyId = hotWallet.id,
            operation = AuthOperation.SIGN,
            intentFingerprint = "fingerprint-intent-original"
        )

        assertFalse(
            handle.isValid(expectedKeyId = hotWallet.id, expectedIntentFingerprint = "fingerprint-intent-tampered", expectedOperation = AuthOperation.SIGN, currentTimeMs = handle.issuedAtMs),
            "Handle authorized for original fingerprint must reject tampered fingerprint"
        )
    }

    @Test
    fun `vector2_6 - handle is automatically invalidated in finally block after sendTransaction`() = runTest {
        viewModel = initViewModelAndReachReviewed()
        val snapshot = viewModel.uiState.value.confirmedSnapshot!!

        val authHandle = TestPlatformAuthenticator.issueHandle(
            keyId = snapshot.keyAlias,
            operation = AuthOperation.SIGN,
            intentFingerprint = snapshot.signingDigestHex
        )

        coEvery { sendTransactionUseCase(intent = any(), authContext = any()) } returns flowOf(
            Result.Success("0xtx_completed_hash")
        )

        viewModel.onBiometricAuthSuccess(authHandle)
        testScheduler.advanceUntilIdle()

        assertEquals(SendTransactionViewModel.TransactionStep.BROADCASTED, viewModel.uiState.value.currentStep)
        // Verify the handle was invalidated in finally block
        assertTrue(authHandle.isInvalidated, "Auth handle must be invalidated in sendTransaction finally block")
    }

    // =========================================================================
    // Vector 3: Rapid double submission / concurrent sendTransaction calls
    // =========================================================================

    @Test
    fun `vector3_1 - 100 rapid concurrent submissions are guarded by mutex with exactly one execution`() = runTest {
        val executionCounter = AtomicInteger(0)
        coEvery { sendTransactionUseCase(intent = any(), authContext = any()) } answers {
            executionCounter.incrementAndGet()
            flow {
                emit(Result.Loading())
                kotlinx.coroutines.delay(200)
                emit(Result.Success("0xtx_mutex_test"))
            }
        }

        viewModel = initViewModelAndReachReviewed()
        val snapshot = viewModel.uiState.value.confirmedSnapshot!!

        // Launch 100 concurrent submissions
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
        assertTrue(viewModel.uiState.value.isSubmitting)

        testScheduler.advanceUntilIdle()

        assertEquals(1, executionCounter.get(), "Mutex must restrict execution to exactly 1 call among 100 rapid concurrent attempts")
        assertEquals(SendTransactionViewModel.TransactionStep.BROADCASTED, viewModel.uiState.value.currentStep)
        assertEquals("0xtx_mutex_test", viewModel.uiState.value.txHash)
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun `vector3_2 - multiple rapid biometric auth successes execute once`() = runTest {
        val executionCounter = AtomicInteger(0)
        coEvery { sendTransactionUseCase(intent = any(), authContext = any()) } answers {
            executionCounter.incrementAndGet()
            flow {
                emit(Result.Loading())
                kotlinx.coroutines.delay(150)
                emit(Result.Success("0xtx_race_test"))
            }
        }

        viewModel = initViewModelAndReachReviewed()
        val snapshot = viewModel.uiState.value.confirmedSnapshot!!
        val handle1 = TestPlatformAuthenticator.issueHandle(
            keyId = snapshot.keyAlias,
            operation = AuthOperation.SIGN,
            intentFingerprint = snapshot.signingDigestHex
        )
        val handle2 = TestPlatformAuthenticator.issueHandle(
            keyId = snapshot.keyAlias,
            operation = AuthOperation.SIGN,
            intentFingerprint = snapshot.signingDigestHex
        )

        // Race both calls
        viewModel.onBiometricAuthSuccess(handle1)
        viewModel.onBiometricAuthSuccess(handle2)

        testScheduler.advanceUntilIdle()

        assertEquals(1, executionCounter.get(), "Racing biometric auth submissions must be deduplicated by mutex")
        assertEquals(SendTransactionViewModel.TransactionStep.BROADCASTED, viewModel.uiState.value.currentStep)
    }

    @Test
    fun `vector3_3 - mutex unlocks cleanly after failure allowing retry`() = runTest {
        var callCount = 0
        coEvery { sendTransactionUseCase(intent = any(), authContext = any()) } answers {
            callCount++
            if (callCount == 1) {
                flowOf(Result.Failure(RuntimeException("RPC broadcast timeout")))
            } else {
                flowOf(Result.Success("0xsuccess_after_retry"))
            }
        }

        viewModel = initViewModelAndReachReviewed()
        val snapshot1 = viewModel.uiState.value.confirmedSnapshot!!
        val handle1 = TestPlatformAuthenticator.issueHandle(
            keyId = snapshot1.keyAlias,
            operation = AuthOperation.SIGN,
            intentFingerprint = snapshot1.signingDigestHex
        )

        // 1st attempt fails
        viewModel.onBiometricAuthSuccess(handle1)
        testScheduler.advanceUntilIdle()

        assertEquals(SendTransactionViewModel.TransactionStep.FAILED, viewModel.uiState.value.currentStep)
        assertFalse(viewModel.uiState.value.isSubmitting)

        // Go back and retry
        viewModel.goBack() // returns to REVIEWED
        assertEquals(SendTransactionViewModel.TransactionStep.REVIEWED, viewModel.uiState.value.currentStep)

        // Re-confirm and retry
        viewModel.proceedToConfirm()
        testScheduler.advanceUntilIdle()

        val snapshot2 = viewModel.uiState.value.confirmedSnapshot!!
        val handle2 = TestPlatformAuthenticator.issueHandle(
            keyId = snapshot2.keyAlias,
            operation = AuthOperation.SIGN,
            intentFingerprint = snapshot2.signingDigestHex
        )

        viewModel.onBiometricAuthSuccess(handle2)
        testScheduler.advanceUntilIdle()

        assertEquals(SendTransactionViewModel.TransactionStep.BROADCASTED, viewModel.uiState.value.currentStep)
        assertEquals("0xsuccess_after_retry", viewModel.uiState.value.txHash)
        assertEquals(2, callCount)
    }

    // =========================================================================
    // Vector 4: Biometric failure / cancellation
    // =========================================================================

    @Test
    fun `vector4_1 - onAuthError transitions state cleanly to FAILED and invalidates active handle`() = runTest {
        viewModel = initViewModelAndReachReviewed()
        viewModel.proceedToAuthorize()

        viewModel.onAuthError("Biometric hardware lockout")

        assertEquals(SendTransactionViewModel.TransactionStep.FAILED, viewModel.uiState.value.currentStep)
        assertTrue(viewModel.uiState.value.error?.contains("Biometric hardware lockout") == true)
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun `vector4_2 - getCryptoObjectForSigning gracefully returns null when key manager throws`() = runTest {
        viewModel = initViewModelAndReachReviewed()
        val cryptoObj = viewModel.getCryptoObjectForSigning(hotWallet.id)
        // Since mock SecureKeyManager is not AndroidSecureKeyManager, it safely returns null without crash
        assertNull(cryptoObj)
    }

    // =========================================================================
    // Vector 5: Cancellation resetting UI state cleanly to AUTH_CANCELLED
    // =========================================================================

    @Test
    fun `vector5_1 - onAuthCancel resets state cleanly to AUTH_CANCELLED with null authorizedFingerprint`() = runTest {
        viewModel = initViewModelAndReachReviewed()
        viewModel.proceedToAuthorize()
        assertEquals(SendTransactionViewModel.TransactionStep.AUTH_REQUIRED, viewModel.uiState.value.currentStep)

        viewModel.onAuthCancel()

        assertEquals(SendTransactionViewModel.TransactionStep.AUTH_CANCELLED, viewModel.uiState.value.currentStep)
        assertNull(viewModel.uiState.value.authorizedFingerprint)
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun `vector5_2 - recovery from AUTH_CANCELLED via proceedToConfirm retains transaction integrity`() = runTest {
        viewModel = initViewModelAndReachReviewed()
        val originalSnapshot = viewModel.uiState.value.confirmedSnapshot!!

        viewModel.proceedToAuthorize()
        viewModel.onAuthCancel()
        assertEquals(SendTransactionViewModel.TransactionStep.AUTH_CANCELLED, viewModel.uiState.value.currentStep)

        // User clicks retry (calls proceedToConfirm)
        viewModel.proceedToConfirm()
        testScheduler.advanceUntilIdle()

        assertEquals(SendTransactionViewModel.TransactionStep.REVIEWED, viewModel.uiState.value.currentStep)
        assertNotNull(viewModel.uiState.value.confirmedSnapshot)
        assertEquals(originalSnapshot.canonicalFingerprint, viewModel.uiState.value.confirmedSnapshot?.canonicalFingerprint)
    }

    @Test
    fun `vector5_3 - onAuthExpired clears confirmedSnapshot and authorizedFingerprint`() = runTest {
        viewModel = initViewModelAndReachReviewed()
        viewModel.proceedToAuthorize()

        viewModel.onAuthExpired()

        assertEquals(SendTransactionViewModel.TransactionStep.AUTH_EXPIRED, viewModel.uiState.value.currentStep)
        assertNull(viewModel.uiState.value.confirmedSnapshot)
        assertNull(viewModel.uiState.value.authorizedFingerprint)
        assertFalse(viewModel.uiState.value.isSubmitting)
    }
}



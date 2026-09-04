package com.cbstudio.wearwallet.presentation.wallet.screens.main.tx

import com.cbstudio.wearwallet.core.security.TestPlatformAuthenticator
import com.cbstudio.wearwallet.core.security.AuthOperation

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
import com.cbstudio.wearwallet.core.security.AuthenticationContext
import com.cbstudio.wearwallet.presentation.wallet.ChainStateManager
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class Milestone2WearChallengerStressTest : KoinTest {

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
        id = "hot-wallet-1",
        name = "Hot Wallet",
        address = "0x71C7656EC7ab88b098defB751B7401B5f6d8976F",
        publicKey = "0xpubkey",
        keyAlias = "ww_key_hot_wallet_1",
        keyBackend = "KEYSTORE",
        keyFormatVersion = 2,
        requiresAuth = true,
        chainType = ChainType.ETHEREUM,
        walletType = WalletType.HOT_WALLET,
        isActive = true
    )

    private val mockHardwareWallet = WalletAccount(
        id = "keystone-wallet-1",
        name = "Keystone Wallet",
        address = "0x71C7656EC7ab88b098defB751B7401B5f6d8976F",
        publicKey = "0xpubkey",
        chainType = ChainType.ETHEREUM,
        walletType = WalletType.KEYSTONE,
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

        coEvery { transactionRepository.getNonce(any(), any<ChainExecutionContext>()) } returns 12L
        coEvery { walletRepository.getActiveWallet() } returns Result.Success(mockHotWallet)
        coEvery { walletRepository.getNativeBalance(any(), any()) } returns 5.0
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
    // 1. Rapid Concurrent Stress Test (Mutex & Re-entrancy Protection)
    // =========================================================================
    @Test
    fun `adversarial stress - 50 concurrent calls to sendTransaction execute exactly once under Mutex guard`() = runTest {
        val callCount = AtomicInteger(0)
        coEvery { sendTransactionUseCase(intent = any(), authContext = any()) } answers {
            callCount.incrementAndGet()
            flow {
                emit(Result.Loading())
                kotlinx.coroutines.delay(200)
                emit(Result.Success("0xhash_concurrent_success"))
            }
        }

        viewModel = initViewModelAndReachReviewed()
        val snapshot = viewModel.uiState.value.confirmedSnapshot!!

        // Trigger 50 rapid concurrent calls to onBiometricAuthSuccess
        val jobs = (1..50).map { i ->
            launch {
                val handle = com.cbstudio.wearwallet.core.security.TestPlatformAuthenticator.issueHandle(
                    keyId = snapshot.keyAlias,
                    operation = com.cbstudio.wearwallet.core.security.AuthOperation.SIGN,
                    intentFingerprint = snapshot.signingDigestHex
                )
                viewModel.onBiometricAuthSuccess(handle)
            }
        }

        testScheduler.advanceTimeBy(50)
        // Verify state is in flight and submitting
        assertTrue(viewModel.uiState.value.isSubmitting)

        testScheduler.advanceUntilIdle()

        assertEquals(1, callCount.get(), "SendTransactionUseCase must only be invoked exactly ONCE among 50 concurrent submissions")
        assertEquals(SendTransactionViewModel.TransactionStep.BROADCASTED, viewModel.uiState.value.currentStep)
        assertEquals("0xhash_concurrent_success", viewModel.uiState.value.txHash)
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    // =========================================================================
    // 2. Intent Tampering Attacks: Recipient, Amount, Gas, Token, Chain
    // =========================================================================
    @Test
    fun `tampering attack - changing recipient after confirmation revokes authorization and clears snapshot`() = runTest {
        viewModel = initViewModelAndReachReviewed()
        val originalFingerprint = viewModel.uiState.value.confirmedSnapshot!!.canonicalFingerprint

        // Attacker alters recipient
        viewModel.setRecipientAddress("0x2222222222222222222222222222222222222222")

        // Assert snapshot & auth fingerprint are immediately cleared
        assertNull(viewModel.uiState.value.confirmedSnapshot, "Snapshot must be null after recipient alteration")
        assertNull(viewModel.uiState.value.authorizedFingerprint, "Authorized fingerprint must be null")

        // Attempting to send directly without valid snapshot must fail immediately
        viewModel.sendTransaction()
        testScheduler.advanceUntilIdle()
        assertEquals(SendTransactionViewModel.TransactionStep.FAILED, viewModel.uiState.value.currentStep)
        assertTrue(viewModel.uiState.value.error?.contains("快照無效") == true)
    }

    @Test
    fun `tampering attack - changing amount after confirmation revokes authorization and clears snapshot`() = runTest {
        viewModel = initViewModelAndReachReviewed()

        // Attacker alters amount
        viewModel.setAmount("999.0")

        assertNull(viewModel.uiState.value.confirmedSnapshot, "Snapshot must be null after amount alteration")
        assertNull(viewModel.uiState.value.authorizedFingerprint, "Authorized fingerprint must be null")

        viewModel.sendTransaction()
        testScheduler.advanceUntilIdle()
        assertEquals(SendTransactionViewModel.TransactionStep.FAILED, viewModel.uiState.value.currentStep)
    }

    @Test
    fun `tampering attack - changing gas parameters after confirmation revokes authorization and clears snapshot`() = runTest {
        viewModel = initViewModelAndReachReviewed()

        // Attacker updates gas parameters to front-run or tamper fee
        viewModel.updateGasParameters(Wei.fromGwei(100), GasLimit.fromDecimalString("50000"))

        assertNull(viewModel.uiState.value.confirmedSnapshot, "Snapshot must be null after gas parameter alteration")
        assertNull(viewModel.uiState.value.authorizedFingerprint, "Authorized fingerprint must be null")

        viewModel.sendTransaction()
        testScheduler.advanceUntilIdle()
        assertEquals(SendTransactionViewModel.TransactionStep.FAILED, viewModel.uiState.value.currentStep)
    }

    @Test
    fun `tampering attack - switching token after confirmation revokes authorization and clears snapshot`() = runTest {
        viewModel = initViewModelAndReachReviewed()

        val mockToken = Token(
            id = "token-usdt",
            address = "0xdAC17F958D2ee523a2206206994597C13D831ec7",
            name = "Tether USD",
            symbol = "USDT",
            decimals = 6,
            chainType = ChainType.ETHEREUM,
            logoUrl = "https://example.com/usdt.png",
            balance = "500.0"
        )

        viewModel.selectToken(mockToken)

        assertNull(viewModel.uiState.value.confirmedSnapshot, "Snapshot must be null after token selection")
        assertEquals(mockToken, viewModel.uiState.value.selectedToken)

        viewModel.sendTransaction()
        testScheduler.advanceUntilIdle()
        assertEquals(SendTransactionViewModel.TransactionStep.FAILED, viewModel.uiState.value.currentStep)
    }

    // =========================================================================
    // 3. UI Hang Fix: Hot Wallet Without Auth Context Transitions to AUTH_REQUIRED
    // =========================================================================
    @Test
    fun `ui hang fix - hot wallet calling sendTransaction without auth credentials transitions to AUTH_REQUIRED`() = runTest {
        viewModel = initViewModelAndReachReviewed()

        // When user reaches confirm screen and clicks Send without auth yet (hot wallet)
        viewModel.sendTransaction(authContext = null)
        testScheduler.advanceUntilIdle()

        // Must transition to AUTH_REQUIRED, not hang in SENDING or FAILED
        assertEquals(SendTransactionViewModel.TransactionStep.AUTH_REQUIRED, viewModel.uiState.value.currentStep)
        assertFalse(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    // =========================================================================
    // 4. Biometric Authorization Flow with CryptoObject
    // =========================================================================
    @Test
    fun `biometric flow - onBiometricSuccess passes CryptoObject and executes successfully`() = runTest {
        coEvery { sendTransactionUseCase(intent = any(), authContext = any()) } returns flowOf(
            Result.Success("0xbio_tx_hash")
        )

        viewModel = initViewModelAndReachReviewed()
        val expectedFingerprint = viewModel.uiState.value.confirmedSnapshot!!.signingDigestHex

        viewModel.proceedToAuthorize()
        assertEquals(SendTransactionViewModel.TransactionStep.AUTH_REQUIRED, viewModel.uiState.value.currentStep)

        // Biometric prompt completes successfully
        val handle = TestPlatformAuthenticator.issueHandle(
            keyId = mockHotWallet.keyAlias ?: "",
            operation = AuthOperation.SIGN,
            intentFingerprint = expectedFingerprint
        )
        viewModel.onBiometricAuthSuccess(handle)
        testScheduler.advanceUntilIdle()

        assertEquals(SendTransactionViewModel.TransactionStep.BROADCASTED, viewModel.uiState.value.currentStep)
        assertEquals("0xbio_tx_hash", viewModel.uiState.value.txHash)
        assertEquals(expectedFingerprint, viewModel.uiState.value.authorizedFingerprint)
    }

    // =========================================================================
    // 5. Keystone Hardware Wallet Flow
    // =========================================================================
    @Test
    fun `keystone hardware wallet flow - proceedToAuthorize creates unsigned tx and enters SIGNING`() = runTest {
        coEvery { walletRepository.getActiveWallet() } returns Result.Success(mockHardwareWallet)
        coEvery { sendTransactionUseCase.createUnsignedTransaction(any(), any(), any(), any(), any(), any()) } returns Result.Success("0xunsigned_keystone_payload")

        viewModel = initViewModelAndReachReviewed()

        // Hardware wallet proceeds directly to authorize/sign without password prompt
        viewModel.proceedToAuthorize()
        testScheduler.advanceUntilIdle()

        assertEquals(SendTransactionViewModel.TransactionStep.SIGNING, viewModel.uiState.value.currentStep)
        assertEquals("0xunsigned_keystone_payload", viewModel.uiState.value.keystoneUnsignedTx)
        assertFalse(viewModel.uiState.value.isSubmitting)
    }
}



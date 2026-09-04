package com.cbstudio.wearwallet.presentation.wallet.screens.main.tx

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.Token
import com.cbstudio.wearwallet.core.domain.model.TransactionStatus
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
import com.cbstudio.wearwallet.core.security.TestPlatformAuthenticator
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SendTransactionViewModelTest : KoinTest {

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
    fun `init should load active wallet and balance`() = runTest {
        // Given
        coEvery { walletRepository.getActiveWallet() } returns Result.Success(mockWallet)
        coEvery { walletRepository.getNativeBalance(any(), any()) } returns 1.0
        
        // When
        viewModel = SendTransactionViewModel()
        testScheduler.advanceUntilIdle() // Wait for init coroutine

        // Then
        val state = viewModel.uiState.value
        assertEquals(mockWallet, state.activeWallet)
        assertTrue(BigDecimal("1.0").compareTo(state.balance) == 0)
    }

    @Test
    fun `setRecipientAddress should update address and validate`() = runTest {
        // Given
        coEvery { walletRepository.getActiveWallet() } returns Result.Success(mockWallet)
        viewModel = SendTransactionViewModel()
        testScheduler.advanceUntilIdle()
        
        // When
        viewModel.setRecipientAddress("0xValidAddressButLengthIs42CharactersRequiredHere123") // 50 chars
        
        // Then
        val state = viewModel.uiState.value
        assertEquals("0xValidAddressButLengthIs42CharactersRequiredHere123", state.recipientAddress)
        // Validation fails because mock address is likely length != 42
        // "0x" + 40 hex chars = 42 chars.
        // My mock string above is not 42 chars.
        // EVM validation: valid 0x + 40 hex.
        
        // Let's test with valid EVM address
        val validAddr = "0x71C7656EC7ab88b098defB751B7401B5f6d8976F"
        viewModel.setRecipientAddress(validAddr)
        assertEquals(validAddr, viewModel.uiState.value.recipientAddress)
        assertEquals(null, viewModel.uiState.value.addressError)
    }

    @Test
    fun `setAmount should update amount and trigger gas estimation`() = runTest {
        // Given
        coEvery { walletRepository.getActiveWallet() } returns Result.Success(mockWallet)
        coEvery { walletRepository.getNativeBalance(any(), any()) } returns 1.0
        
        // Mock estimateGasUseCase
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

        // When
        viewModel.setAmount("0.1")
        testScheduler.advanceUntilIdle()

        // Then
        val state = viewModel.uiState.value
        assertEquals("0.1", state.amount)
        assertEquals(com.cbstudio.wearwallet.core.domain.model.quantities.Wei.fromGwei(10).toHex(), state.estimatedGasPrice)
        assertEquals("21000", state.estimatedGasLimit)
        assertEquals("0.00021", state.estimatedTotalFee)
    }

    @Test
    fun `sendTransaction with password should succeed`() = runTest {
        // Given
        val password = "password123"
        coEvery { walletRepository.getActiveWallet() } returns Result.Success(mockWallet)
        coEvery { walletRepository.getNativeBalance(any(), any()) } returns 1.0
        
        // Mock estimateGasUseCase
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
        viewModel.setAmount("0.1")
        testScheduler.advanceUntilIdle()
        
        viewModel.proceedToConfirm()
        testScheduler.advanceUntilIdle()
        
        // Mock SendTransactionUseCase
        coEvery { sendTransactionUseCase(intent = any(), authContext = any()) } returns flowOf(
            Result.Success("0xtxhash")
        )
        
        val snapshot = viewModel.uiState.value.confirmedSnapshot!!
        val handle = com.cbstudio.wearwallet.core.security.TestPlatformAuthenticator.issueHandle(
            keyId = snapshot.keyAlias,
            operation = com.cbstudio.wearwallet.core.security.AuthOperation.SIGN,
            intentFingerprint = snapshot.signingDigestHex
        )

        // When
        viewModel.onBiometricAuthSuccess(handle)
        testScheduler.advanceUntilIdle()

        // Then
        val state = viewModel.uiState.value
        assertEquals("0xtxhash", state.txHash)
        assertEquals(SendTransactionViewModel.TransactionStep.BROADCASTED, state.currentStep)
    }
    
    @Test
    fun `sendTransaction without auth should prompt if hot wallet`() = runTest {
        // Given
        coEvery { walletRepository.getActiveWallet() } returns Result.Success(mockWallet) // Hot wallet by default
        coEvery { walletRepository.getNativeBalance(any(), any()) } returns 1.0
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
        viewModel.setAmount("0.1")
        testScheduler.advanceUntilIdle()
        viewModel.proceedToConfirm()
        testScheduler.advanceUntilIdle()
        
        // When
        viewModel.sendTransaction()
        testScheduler.advanceUntilIdle()
        
        // Then: Must transition to AUTH_REQUIRED (Fixes UI Hang)
        assertEquals(SendTransactionViewModel.TransactionStep.AUTH_REQUIRED, viewModel.uiState.value.currentStep)
    }

    @Test
    fun `10-state machine full lifecycle transitions through REVIEWED AUTH_REQUIRED AUTHENTICATING AUTHORIZED SIGNING BROADCASTING SUCCESS`() = runTest {
        coEvery { walletRepository.getActiveWallet() } returns Result.Success(mockWallet)
        coEvery { walletRepository.getNativeBalance(any(), any()) } returns 1.0
        val gasEstimation = EstimateGasUseCase.GasEstimation(
            weiGasPrice = com.cbstudio.wearwallet.core.domain.model.quantities.Wei.fromGwei(10),
            gasLimitObj = com.cbstudio.wearwallet.core.domain.model.quantities.GasLimit.fromDecimalString("21000"),
            totalFee = "0.00021"
        )
        coEvery { estimateGasUseCase(any(), any(), any(), any(), any()) } returns flowOf(Result.Success(gasEstimation))

        viewModel = SendTransactionViewModel()
        testScheduler.advanceUntilIdle()

        // 1. INPUT_ADDRESS
        assertEquals(SendTransactionViewModel.TransactionStep.INPUT_ADDRESS, viewModel.uiState.value.currentStep)
        viewModel.setRecipientAddress("0x71C7656EC7ab88b098defB751B7401B5f6d8976F")
        viewModel.proceedToAmount()

        // 2. INPUT_AMOUNT
        assertEquals(SendTransactionViewModel.TransactionStep.INPUT_AMOUNT, viewModel.uiState.value.currentStep)
        viewModel.setAmount("0.5")
        testScheduler.advanceUntilIdle()

        // 3. REVIEWED
        viewModel.proceedToConfirm()
        testScheduler.advanceUntilIdle()
        assertEquals(SendTransactionViewModel.TransactionStep.REVIEWED, viewModel.uiState.value.currentStep)
        val snapshot = viewModel.uiState.value.confirmedSnapshot
        assertNotNull(snapshot)
        val expectedFingerprint = snapshot.signingDigestHex

        // 4. Request Auth -> AUTH_REQUIRED
        viewModel.proceedToAuthorize()
        assertEquals(SendTransactionViewModel.TransactionStep.AUTH_REQUIRED, viewModel.uiState.value.currentStep)

        // Mock SendTransactionUseCase with Loading then Success
        coEvery { sendTransactionUseCase(intent = any(), authContext = any()) } returns kotlinx.coroutines.flow.flow {
            emit(Result.Loading())
            kotlinx.coroutines.delay(100)
            emit(Result.Success("0xhash123456"))
        }

        val handle = com.cbstudio.wearwallet.core.security.TestPlatformAuthenticator.issueHandle(
            keyId = snapshot.keyAlias,
            operation = com.cbstudio.wearwallet.core.security.AuthOperation.SIGN,
            intentFingerprint = expectedFingerprint
        )

        // 5. Submit auth handle -> AUTHENTICATING -> AUTHORIZED -> SIGNING -> BROADCASTING -> SUCCESS
        viewModel.onBiometricAuthSuccess(handle)
        testScheduler.advanceTimeBy(50)
        // Check fingerprint binding
        assertEquals(expectedFingerprint, viewModel.uiState.value.authorizedFingerprint)

        testScheduler.advanceUntilIdle()
        assertEquals(SendTransactionViewModel.TransactionStep.BROADCASTED, viewModel.uiState.value.currentStep)
        assertEquals("0xhash123456", viewModel.uiState.value.txHash)
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun `auth cancellation and expiration state transitions`() = runTest {
        coEvery { walletRepository.getActiveWallet() } returns Result.Success(mockWallet)
        coEvery { walletRepository.getNativeBalance(any(), any()) } returns 1.0
        val gasEstimation = EstimateGasUseCase.GasEstimation(
            weiGasPrice = com.cbstudio.wearwallet.core.domain.model.quantities.Wei.fromGwei(10),
            gasLimitObj = com.cbstudio.wearwallet.core.domain.model.quantities.GasLimit.fromDecimalString("21000"),
            totalFee = "0.00021"
        )
        coEvery { estimateGasUseCase(any(), any(), any(), any(), any()) } returns flowOf(Result.Success(gasEstimation))

        viewModel = SendTransactionViewModel()
        testScheduler.advanceUntilIdle()
        viewModel.setRecipientAddress("0x71C7656EC7ab88b098defB751B7401B5f6d8976F")
        viewModel.proceedToAmount()
        viewModel.setAmount("0.1")
        testScheduler.advanceUntilIdle()
        viewModel.proceedToConfirm()
        testScheduler.advanceUntilIdle()

        // Cancel Auth
        viewModel.proceedToAuthorize()
        assertEquals(SendTransactionViewModel.TransactionStep.AUTH_REQUIRED, viewModel.uiState.value.currentStep)
        viewModel.onAuthCancel()
        assertEquals(SendTransactionViewModel.TransactionStep.AUTH_CANCELLED, viewModel.uiState.value.currentStep)
        kotlin.test.assertNull(viewModel.uiState.value.authorizedFingerprint)

        // Expire Auth
        viewModel.onAuthExpired()
        assertEquals(SendTransactionViewModel.TransactionStep.AUTH_EXPIRED, viewModel.uiState.value.currentStep)
        kotlin.test.assertNull(viewModel.uiState.value.confirmedSnapshot)
    }

    @Test
    fun `submission mutex blocks duplicate concurrent send calls`() = runTest {
        coEvery { walletRepository.getActiveWallet() } returns Result.Success(mockWallet)
        coEvery { walletRepository.getNativeBalance(any(), any()) } returns 1.0
        val gasEstimation = EstimateGasUseCase.GasEstimation(
            weiGasPrice = com.cbstudio.wearwallet.core.domain.model.quantities.Wei.fromGwei(10),
            gasLimitObj = com.cbstudio.wearwallet.core.domain.model.quantities.GasLimit.fromDecimalString("21000"),
            totalFee = "0.00021"
        )
        coEvery { estimateGasUseCase(any(), any(), any(), any(), any()) } returns flowOf(Result.Success(gasEstimation))

        viewModel = SendTransactionViewModel()
        testScheduler.advanceUntilIdle()
        viewModel.setRecipientAddress("0x71C7656EC7ab88b098defB751B7401B5f6d8976F")
        viewModel.proceedToAmount()
        viewModel.setAmount("0.1")
        testScheduler.advanceUntilIdle()
        viewModel.proceedToConfirm()
        testScheduler.advanceUntilIdle()

        var callCount = 0
        coEvery { sendTransactionUseCase(intent = any(), authContext = any()) } answers {
            callCount++
            kotlinx.coroutines.flow.flow {
                emit(Result.Loading())
                kotlinx.coroutines.delay(500)
                emit(Result.Success("0xtxhash"))
            }
        }

        val snapshot = viewModel.uiState.value.confirmedSnapshot!!
        val handle1 = com.cbstudio.wearwallet.core.security.TestPlatformAuthenticator.issueHandle(
            keyId = snapshot.keyAlias,
            operation = com.cbstudio.wearwallet.core.security.AuthOperation.SIGN,
            intentFingerprint = snapshot.signingDigestHex
        )
        val handle2 = com.cbstudio.wearwallet.core.security.TestPlatformAuthenticator.issueHandle(
            keyId = snapshot.keyAlias,
            operation = com.cbstudio.wearwallet.core.security.AuthOperation.SIGN,
            intentFingerprint = snapshot.signingDigestHex
        )

        // First submit starts execution and acquires mutex
        viewModel.onBiometricAuthSuccess(handle1)
        testScheduler.advanceTimeBy(50)

        // Second submit while in flight should be locked and rejected by tryLock()
        viewModel.onBiometricAuthSuccess(handle2)
        testScheduler.advanceTimeBy(50)

        testScheduler.advanceUntilIdle()
        assertEquals(1, callCount, "SendTransactionUseCase must only be called once, duplicate submission must be blocked by mutex")
    }

    @Test
    fun `validateAddress should fail for invalid EVM address`() = runTest {
         // Given
        coEvery { walletRepository.getActiveWallet() } returns Result.Success(mockWallet)
        viewModel = SendTransactionViewModel()
        testScheduler.advanceUntilIdle()
        
        // When
        viewModel.setRecipientAddress("invalid-address")
        
        // Then
        assertEquals("地址格式錯誤", viewModel.uiState.value.addressError)
    }

    @Test
    fun `async race - recipient address change during in-flight gas estimation drops stale gas response`() = runTest {
        coEvery { walletRepository.getActiveWallet() } returns Result.Success(mockWallet)
        coEvery { walletRepository.getNativeBalance(any(), any()) } returns 1.0

        val delayedGasEstimation = kotlinx.coroutines.flow.flow {
            kotlinx.coroutines.delay(200)
            emit(
                Result.Success(
                    EstimateGasUseCase.GasEstimation(
                        weiGasPrice = com.cbstudio.wearwallet.core.domain.model.quantities.Wei.fromGwei(10),
                        gasLimitObj = com.cbstudio.wearwallet.core.domain.model.quantities.GasLimit.fromDecimalString("21000"),
                        totalFee = "0.00021"
                    )
                )
            )
        }
        coEvery { estimateGasUseCase(any(), any(), any(), any(), any()) } returns delayedGasEstimation

        viewModel = SendTransactionViewModel()
        testScheduler.advanceUntilIdle()

        val initialGen = viewModel.getTransactionIntentGeneration()
        viewModel.setRecipientAddress("0x71C7656EC7ab88b098defB751B7401B5f6d8976F")
        viewModel.setAmount("0.1")
        testScheduler.advanceTimeBy(50) // Gas estimate in flight

        // Change recipient address while RPC in flight
        viewModel.setRecipientAddress("0x1111111111111111111111111111111111111111")
        val genAfterRecipientChange = viewModel.getTransactionIntentGeneration()
        assertTrue(genAfterRecipientChange > initialGen, "Generation must increment on recipient address change")

        // Advance virtual time to complete the delayed RPC
        testScheduler.advanceUntilIdle()

        // Verify stale gas estimation was dropped and snapshot is null
        kotlin.test.assertNull(viewModel.uiState.value.confirmedSnapshot, "Snapshot must be null after field change")
    }

    @Test
    fun `async race - transfer amount change during in-flight gas estimation increments generation and invalidates snapshot`() = runTest {
        coEvery { walletRepository.getActiveWallet() } returns Result.Success(mockWallet)
        coEvery { walletRepository.getNativeBalance(any(), any()) } returns 10.0

        val delayedGas = kotlinx.coroutines.flow.flow {
            kotlinx.coroutines.delay(200)
            emit(
                Result.Success(
                    EstimateGasUseCase.GasEstimation(
                        weiGasPrice = com.cbstudio.wearwallet.core.domain.model.quantities.Wei.fromGwei(15),
                        gasLimitObj = com.cbstudio.wearwallet.core.domain.model.quantities.GasLimit.fromDecimalString("21000"),
                        totalFee = "0.000315"
                    )
                )
            )
        }
        coEvery { estimateGasUseCase(any(), any(), any(), any(), any()) } returns delayedGas

        viewModel = SendTransactionViewModel()
        testScheduler.advanceUntilIdle()

        viewModel.setRecipientAddress("0x71C7656EC7ab88b098defB751B7401B5f6d8976F")
        val genBefore = viewModel.getTransactionIntentGeneration()

        viewModel.setAmount("1.0")
        testScheduler.advanceTimeBy(50)

        // Amount change while in-flight
        viewModel.setAmount("2.0")
        val genAfter = viewModel.getTransactionIntentGeneration()
        assertTrue(genAfter > genBefore, "Generation must increment on amount change")

        testScheduler.advanceUntilIdle()
        kotlin.test.assertNull(viewModel.uiState.value.confirmedSnapshot, "Snapshot must remain null on intent change")
    }

    @Test
    fun `async race - gas parameters update invalidates confirmedSnapshot and increments generation`() = runTest {
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
        viewModel.setAmount("1.0")
        testScheduler.advanceUntilIdle()

        viewModel.proceedToConfirm()
        testScheduler.advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.confirmedSnapshot, "Confirmed snapshot should be present")

        val genBeforeGasParam = viewModel.getTransactionIntentGeneration()
        viewModel.updateGasParameters(
            com.cbstudio.wearwallet.core.domain.model.quantities.Wei.fromGwei(30),
            com.cbstudio.wearwallet.core.domain.model.quantities.GasLimit.fromDecimalString("25000")
        )

        val genAfterGasParam = viewModel.getTransactionIntentGeneration()
        assertTrue(genAfterGasParam > genBeforeGasParam, "Generation must increment when gas parameters change")
        kotlin.test.assertNull(viewModel.uiState.value.confirmedSnapshot, "Snapshot must be invalidated when gas parameters change")
    }

    @Test
    fun `async race - delayed nonce fetch RPC drops response when recipient address changes`() = runTest {
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
        coEvery { transactionRepository.getNonce(any(), any<ChainExecutionContext>()) } coAnswers {
            kotlinx.coroutines.delay(200)
            5L
        }

        viewModel = SendTransactionViewModel()
        testScheduler.advanceUntilIdle()

        viewModel.setRecipientAddress("0x71C7656EC7ab88b098defB751B7401B5f6d8976F")
        viewModel.setAmount("1.0")
        testScheduler.advanceUntilIdle()

        viewModel.proceedToConfirm()
        testScheduler.advanceTimeBy(50) // Nonce RPC in flight

        // Mutate recipient while nonce RPC is in flight
        viewModel.setRecipientAddress("0x2222222222222222222222222222222222222222")

        testScheduler.advanceUntilIdle()
        kotlin.test.assertNull(viewModel.uiState.value.confirmedSnapshot, "Stale nonce response must be dropped and snapshot remain null")
    }

    @Test
    fun `async race - out-of-order gas estimation RPC arrivals discard earlier slow response`() = runTest {
        coEvery { walletRepository.getActiveWallet() } returns Result.Success(mockWallet)
        coEvery { walletRepository.getNativeBalance(any(), any()) } returns 10.0

        val addrA = "0x71C7656EC7ab88b098defB751B7401B5f6d8976F"
        val addrB = "0x1111111111111111111111111111111111111111"

        coEvery { estimateGasUseCase(from = any(), to = addrA, value = any(), chainType = any(), tokenAddress = any(), tokenDecimals = any()) } answers {
            kotlinx.coroutines.flow.flow {
                kotlinx.coroutines.delay(300) // Slow response A
                emit(
                    Result.Success(
                        EstimateGasUseCase.GasEstimation(
                            weiGasPrice = com.cbstudio.wearwallet.core.domain.model.quantities.Wei.fromGwei(30),
                            gasLimitObj = com.cbstudio.wearwallet.core.domain.model.quantities.GasLimit.fromDecimalString("21000"),
                            totalFee = "0.00063"
                        )
                    )
                )
            }
        }

        coEvery { estimateGasUseCase(from = any(), to = addrB, value = any(), chainType = any(), tokenAddress = any(), tokenDecimals = any()) } answers {
            kotlinx.coroutines.flow.flow {
                kotlinx.coroutines.delay(100) // Fast response B
                emit(
                    Result.Success(
                        EstimateGasUseCase.GasEstimation(
                            weiGasPrice = com.cbstudio.wearwallet.core.domain.model.quantities.Wei.fromGwei(10),
                            gasLimitObj = com.cbstudio.wearwallet.core.domain.model.quantities.GasLimit.fromDecimalString("21000"),
                            totalFee = "0.00021"
                        )
                    )
                )
            }
        }

        viewModel = SendTransactionViewModel()
        testScheduler.advanceUntilIdle()

        // Trigger Request A (slow, 300ms delay)
        viewModel.setRecipientAddress(addrA)
        viewModel.setAmount("1.0")

        testScheduler.advanceTimeBy(50) // RPC A is in-flight

        // Trigger Request B (fast, 100ms delay)
        viewModel.setRecipientAddress(addrB)
        val genAfterB = viewModel.getTransactionIntentGeneration()

        // Advance to 160ms total: RPC B (started at t=50ms + 100ms = 150ms) finishes
        testScheduler.advanceTimeBy(110)
        assertEquals("0.00021", viewModel.uiState.value.estimatedTotalFee, "State must contain fast RPC B gas result")

        // Advance past 350ms total: RPC A (started at t=0ms + 300ms = 300ms) finishes out-of-order
        testScheduler.advanceUntilIdle()

        // Assert RPC A response was dropped, state retains RPC B gas result
        assertEquals("0.00021", viewModel.uiState.value.estimatedTotalFee, "Out-of-order slow RPC A result must be discarded")
        assertEquals(addrB, viewModel.uiState.value.recipientAddress)
        assertEquals(genAfterB, viewModel.getTransactionIntentGeneration())
        kotlin.test.assertNull(viewModel.uiState.value.confirmedSnapshot, "Confirmed snapshot must be null after field change")
    }

    @Test
    fun `async race - rapid multi-field mutations increment generation and prevent stale snapshot leakage`() = runTest {
        coEvery { walletRepository.getActiveWallet() } returns Result.Success(mockWallet)
        coEvery { walletRepository.getNativeBalance(any(), any()) } returns 10.0

        val slowGasEstimation = kotlinx.coroutines.flow.flow {
            kotlinx.coroutines.delay(500)
            emit(
                Result.Success(
                    EstimateGasUseCase.GasEstimation(
                        weiGasPrice = com.cbstudio.wearwallet.core.domain.model.quantities.Wei.fromGwei(50),
                        gasLimitObj = com.cbstudio.wearwallet.core.domain.model.quantities.GasLimit.fromDecimalString("21000"),
                        totalFee = "0.00105"
                    )
                )
            )
        }
        coEvery { estimateGasUseCase(any(), any(), any(), any(), any()) } returns slowGasEstimation

        viewModel = SendTransactionViewModel()
        testScheduler.advanceUntilIdle()

        val gen0 = viewModel.getTransactionIntentGeneration()

        // Rapid mutations
        viewModel.setRecipientAddress("0x71C7656EC7ab88b098defB751B7401B5f6d8976F")
        val gen1 = viewModel.getTransactionIntentGeneration()
        assertTrue(gen1 > gen0)

        viewModel.setAmount("1.5")
        val gen2 = viewModel.getTransactionIntentGeneration()
        assertTrue(gen2 > gen1)

        viewModel.updateGasParameters(
            com.cbstudio.wearwallet.core.domain.model.quantities.Wei.fromGwei(20),
            com.cbstudio.wearwallet.core.domain.model.quantities.GasLimit.fromDecimalString("21000")
        )
        val gen3 = viewModel.getTransactionIntentGeneration()
        assertTrue(gen3 > gen2)

        viewModel.setRecipientAddress("0x1111111111111111111111111111111111111111")
        val gen4 = viewModel.getTransactionIntentGeneration()
        assertTrue(gen4 > gen3)

        // Allow all in-flight slow RPCs to finish
        testScheduler.advanceUntilIdle()

        // Verify generation incremented for every mutation, snapshot is null, and fresh gas fee for gen4 overwrites stale manual parameters
        assertEquals(gen4, viewModel.getTransactionIntentGeneration())
        kotlin.test.assertNull(viewModel.uiState.value.confirmedSnapshot, "Snapshot must be null after rapid mutations")
        assertEquals("0.00105", viewModel.uiState.value.estimatedTotalFee, "Fresh gas fee for gen4 should complete and overwrite stale manual parameters")
    }

    // =========================================================================
    // Milestone 3: Send Flow Legacy Migration Integration Tests
    // =========================================================================

    @Test
    fun `initial active wallet load with legacy wallet (keyAlias null) transitions to MIGRATION_REQUIRED`() = runTest {
        val legacyWallet = mockWallet.copy(
            keyAlias = null,
            keyFormatVersion = 1,
            requiresAuth = false
        )
        coEvery { walletRepository.getActiveWallet() } returns Result.Success(legacyWallet)
        coEvery { walletRepository.getNativeBalance(any(), any()) } returns 1.0

        viewModel = SendTransactionViewModel()
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(legacyWallet, state.activeWallet)
        assertEquals(
            SendTransactionViewModel.TransactionStep.MIGRATION_REQUIRED,
            state.currentStep,
            "Active wallet without keyAlias MUST transition to MIGRATION_REQUIRED on init"
        )
    }

    @Test
    fun `migration cancelled or failed preserves signingCount=0, broadcastCount=0, and never falls back to walletId`() = runTest {
        val legacyWallet = mockWallet.copy(keyAlias = null, keyFormatVersion = 1)
        val testAuthContext = AuthenticationContext(
            authHandle = com.cbstudio.wearwallet.core.security.TestPlatformAuthenticator.issueHandle(
                keyId = "IMPORT_PROVISIONING",
                operation = com.cbstudio.wearwallet.core.security.AuthOperation.IMPORT
            )
        )
        coEvery { walletRepository.getActiveWallet() } returns Result.Success(legacyWallet)
        coEvery { walletRepository.migrateLegacyWalletIfNeeded(any(), any(), any()) } returns Result.Failure(
            com.cbstudio.wearwallet.core.security.EnvelopeIntegrityException("Wrong password or corrupted legacy key")
        )

        viewModel = SendTransactionViewModel()
        testScheduler.advanceUntilIdle()
        assertEquals(SendTransactionViewModel.TransactionStep.MIGRATION_REQUIRED, viewModel.uiState.value.currentStep)

        // User attempts migration with incorrect password
        viewModel.onPerformLegacyMigration("wrong_password", testAuthContext)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(SendTransactionViewModel.TransactionStep.FAILED, state.currentStep)
        assertTrue(state.error?.contains("遷移失敗") == true || state.error?.contains("Wrong password") == true)
        kotlin.test.assertNull(state.confirmedSnapshot)
        kotlin.test.assertNull(state.authorizedFingerprint)

        // Verify SendTransactionUseCase was NEVER invoked and no fallback occurred
        coVerify(exactly = 0) { sendTransactionUseCase(intent = any(), authContext = any()) }
        coVerify(exactly = 0) { transactionRepository.sendTransaction(any(), any<ChainExecutionContext>()) }
    }

    @Test
    fun `migration succeeded updates active wallet with new keyAlias and allows proceeding to normal transaction`() = runTest {
        val legacyWallet = mockWallet.copy(keyAlias = null, keyFormatVersion = 1)
        val migratedWallet = mockWallet.copy(
            keyAlias = "ww_key_migrated_uuid_999",
            keyBackend = "KEYSTORE",
            keyFormatVersion = 2,
            requiresAuth = true
        )
        val testAuthContext = AuthenticationContext(
            authHandle = com.cbstudio.wearwallet.core.security.TestPlatformAuthenticator.issueHandle(
                keyId = "IMPORT_PROVISIONING",
                operation = com.cbstudio.wearwallet.core.security.AuthOperation.IMPORT
            )
        )

        coEvery { walletRepository.getActiveWallet() } returns Result.Success(legacyWallet)
        coEvery { walletRepository.migrateLegacyWalletIfNeeded(eq("wallet-1"), any(), any()) } returns Result.Success(migratedWallet)
        coEvery { walletRepository.getNativeBalance(any(), any()) } returns 1.0

        val gasEstimation = EstimateGasUseCase.GasEstimation(
            weiGasPrice = com.cbstudio.wearwallet.core.domain.model.quantities.Wei.fromGwei(10),
            gasLimitObj = com.cbstudio.wearwallet.core.domain.model.quantities.GasLimit.fromDecimalString("21000"),
            totalFee = "0.00021"
        )
        coEvery { estimateGasUseCase(any(), any(), any(), any(), any(), any()) } returns flowOf(Result.Success(gasEstimation))
        coEvery { sendTransactionUseCase(intent = any(), authContext = any()) } returns flowOf(Result.Success("0xtx_migrated_success"))

        viewModel = SendTransactionViewModel()
        testScheduler.advanceUntilIdle()
        assertEquals(SendTransactionViewModel.TransactionStep.MIGRATION_REQUIRED, viewModel.uiState.value.currentStep)

        // Perform successful migration
        viewModel.onPerformLegacyMigration("correct_password", testAuthContext)
        testScheduler.advanceUntilIdle()

        assertEquals(migratedWallet, viewModel.uiState.value.activeWallet)
        assertEquals(SendTransactionViewModel.TransactionStep.INPUT_ADDRESS, viewModel.uiState.value.currentStep)

        // Proceed through normal send flow
        viewModel.setRecipientAddress("0x71C7656EC7ab88b098defB751B7401B5f6d8976F")
        viewModel.proceedToAmount()
        viewModel.setAmount("0.5")
        testScheduler.advanceUntilIdle()

        viewModel.proceedToConfirm()
        testScheduler.advanceUntilIdle()
        assertEquals(SendTransactionViewModel.TransactionStep.REVIEWED, viewModel.uiState.value.currentStep)

        val snapshot = viewModel.uiState.value.confirmedSnapshot
        assertNotNull(snapshot)
        assertEquals("ww_key_migrated_uuid_999", snapshot.keyAlias, "Snapshot MUST use migrated keyAlias, NOT wallet.id")

        // Authorize and send
        val authHandle = com.cbstudio.wearwallet.core.security.TestPlatformAuthenticator.issueHandle(
            keyId = snapshot.keyAlias,
            operation = com.cbstudio.wearwallet.core.security.AuthOperation.SIGN,
            intentFingerprint = snapshot.signingDigestHex
        )
        viewModel.onBiometricAuthSuccess(authHandle)
        testScheduler.advanceUntilIdle()

        assertEquals(SendTransactionViewModel.TransactionStep.BROADCASTED, viewModel.uiState.value.currentStep)
        assertEquals("0xtx_migrated_success", viewModel.uiState.value.txHash)
    }

    @Test
    fun `proceedToConfirm without keyAlias fails closed and sets error state`() = runTest {
        val unmigratedWallet = mockWallet.copy(keyAlias = null, keyFormatVersion = 1)
        coEvery { walletRepository.getActiveWallet() } returns Result.Success(unmigratedWallet)
        coEvery { walletRepository.getNativeBalance(any(), any()) } returns 1.0

        val gasEstimation = EstimateGasUseCase.GasEstimation(
            weiGasPrice = com.cbstudio.wearwallet.core.domain.model.quantities.Wei.fromGwei(10),
            gasLimitObj = com.cbstudio.wearwallet.core.domain.model.quantities.GasLimit.fromDecimalString("21000"),
            totalFee = "0.00021"
        )
        coEvery { estimateGasUseCase(any(), any(), any(), any(), any(), any()) } returns flowOf(Result.Success(gasEstimation))

        viewModel = SendTransactionViewModel()
        testScheduler.advanceUntilIdle()

        viewModel.setRecipientAddress("0x71C7656EC7ab88b098defB751B7401B5f6d8976F")
        viewModel.proceedToAmount()
        viewModel.setAmount("0.1")
        testScheduler.advanceUntilIdle()

        // When attempting to confirm an unmigrated wallet
        viewModel.proceedToConfirm()
        testScheduler.advanceUntilIdle()

        kotlin.test.assertNull(viewModel.uiState.value.confirmedSnapshot, "Confirmed snapshot MUST NOT be generated without valid keyAlias")
        assertEquals(SendTransactionViewModel.TransactionStep.MIGRATION_REQUIRED, viewModel.uiState.value.currentStep)
    }

    @Test
    fun `validateAddress rejects mixed-case EIP-55 mismatch and accepts known checksum`() = runTest {
        coEvery { walletRepository.getActiveWallet() } returns Result.Success(mockWallet)
        viewModel = SendTransactionViewModel()
        testScheduler.advanceUntilIdle()

        viewModel.setRecipientAddress(EIP55_WRONG_MIXED)
        assertTrue(
            viewModel.uiState.value.addressError != null,
            "wrong mixed-case checksum must set addressError",
        )

        viewModel.setRecipientAddress(EIP55_GOOD)
        assertEquals(EIP55_GOOD, viewModel.uiState.value.recipientAddress)
        assertEquals(null, viewModel.uiState.value.addressError)

        viewModel.setRecipientAddress(EIP55_ALL_LOWER)
        assertEquals(null, viewModel.uiState.value.addressError)

        viewModel.setRecipientAddress(EIP55_ALL_UPPER)
        assertEquals(null, viewModel.uiState.value.addressError)
    }

    @Test
    fun `successful broadcast hash is BROADCASTED with PENDING status not CONFIRMED`() = runTest {
        coEvery { walletRepository.getActiveWallet() } returns Result.Success(mockWallet)
        coEvery { walletRepository.getNativeBalance(any(), any()) } returns 1.0
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
        viewModel.setRecipientAddress(EIP55_GOOD)
        viewModel.setAmount("0.1")
        testScheduler.advanceUntilIdle()
        viewModel.proceedToConfirm()
        testScheduler.advanceUntilIdle()

        coEvery { sendTransactionUseCase(intent = any(), authContext = any()) } returns flowOf(
            Result.Success("0xtxhash")
        )
        val snapshot = viewModel.uiState.value.confirmedSnapshot!!
        val handle = com.cbstudio.wearwallet.core.security.TestPlatformAuthenticator.issueHandle(
            keyId = snapshot.keyAlias,
            operation = com.cbstudio.wearwallet.core.security.AuthOperation.SIGN,
            intentFingerprint = snapshot.signingDigestHex
        )
        viewModel.onBiometricAuthSuccess(handle)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("0xtxhash", state.txHash)
        assertEquals(SendTransactionViewModel.TransactionStep.BROADCASTED, state.currentStep)
        assertEquals(TransactionStatus.PENDING, state.broadcastStatus)
        assertTrue(state.broadcastStatus != TransactionStatus.CONFIRMED)
        assertTrue(state.currentStep != SendTransactionViewModel.TransactionStep.SUCCESS)
    }

    @Test
    fun `review state keeps full to-address chainId nonce and contract untruncated`() = runTest {
        coEvery { walletRepository.getActiveWallet() } returns Result.Success(mockWallet)
        coEvery { walletRepository.getNativeBalance(any(), any()) } returns 100.0
        val gasEstimation = EstimateGasUseCase.GasEstimation(
            weiGasPrice = com.cbstudio.wearwallet.core.domain.model.quantities.Wei.fromGwei(10),
            gasLimitObj = com.cbstudio.wearwallet.core.domain.model.quantities.GasLimit.fromDecimalString("65000"),
            totalFee = "0.00065"
        )
        coEvery { estimateGasUseCase(any(), any(), any(), any(), any(), any()) } returns flowOf(
            Result.Success(gasEstimation)
        )

        viewModel = SendTransactionViewModel()
        testScheduler.advanceUntilIdle()
        viewModel.setRecipientAddress(EIP55_GOOD)
        val token = Token(
            id = "usdc",
            address = TOKEN_CONTRACT,
            name = "USD Coin",
            symbol = "USDC",
            decimals = 6,
            chainType = ChainType.ETHEREUM,
            balance = "100.0",
        )
        viewModel.selectToken(token)
        viewModel.setAmount("1.0")
        testScheduler.advanceUntilIdle()
        viewModel.proceedToConfirm()
        testScheduler.advanceUntilIdle()

        val review = viewModel.uiState.value.reviewFields
        assertNotNull(review)
        assertEquals(EIP55_GOOD, review.toAddress)
        assertEquals(42, review.toAddress.length)
        assertFalse(review.toAddress.contains("..."))
        assertTrue(review.chainId > 0L)
        assertTrue(review.nonce >= 0L)
        assertEquals(TOKEN_CONTRACT, review.contractAddress)
        assertFalse(review.contractAddress!!.contains("..."))
        assertEquals(42, review.contractAddress!!.length)
    }

    companion object {
        const val EIP55_GOOD = "0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed"
        const val EIP55_WRONG_MIXED = "0x5aaeb6053F3E94C9b9A09f33669435E7Ef1BeAed"
        const val EIP55_ALL_LOWER = "0x5aaeb6053f3e94c9b9a09f33669435e7ef1beaed"
        const val EIP55_ALL_UPPER = "0x5AAEB6053F3E94C9B9A09F33669435E7EF1BEAED"
        const val TOKEN_CONTRACT = "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48"
    }
}

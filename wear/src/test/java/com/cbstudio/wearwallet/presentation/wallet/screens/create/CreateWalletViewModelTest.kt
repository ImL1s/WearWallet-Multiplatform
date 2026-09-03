package com.cbstudio.wearwallet.presentation.wallet.screens.create

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.WalletAccount
import com.cbstudio.wearwallet.core.domain.usecase.wallet.CreateWalletUseCase
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
import kotlin.test.assertTrue
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class CreateWalletViewModelTest : KoinTest {

    private lateinit var viewModel: CreateWalletViewModel
    private lateinit var createWalletUseCase: CreateWalletUseCase
    
    private val testDispatcher = StandardTestDispatcher()

    private val mockWallet = WalletAccount(
        id = "wallet-1",
        name = "Test Wallet",
        address = "0x123",
        publicKey = "0xpubkey",
        chainType = ChainType.ETHEREUM
    )

    private val testAuthContext = com.cbstudio.wearwallet.core.security.AuthenticationContext(
        authHandle = com.cbstudio.wearwallet.core.security.TestPlatformAuthenticator.issueHandle(
            keyId = "IMPORT_PROVISIONING",
            operation = com.cbstudio.wearwallet.core.security.AuthOperation.IMPORT
        )
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        createWalletUseCase = mockk(relaxed = true)
        
        startKoin {
            modules(module {
                single { createWalletUseCase }
            })
        }
        
        viewModel = CreateWalletViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        stopKoin()
    }

    @Test
    fun `startWalletCreation should update step and show password input`() = runTest {
        // When
        viewModel.startWalletCreation("My Wallet")
        
        // Then
        val state = viewModel.uiState.value
        assertEquals("My Wallet", state.walletName)
        assertEquals(CreateWalletViewModel.CreationStep.PASSWORD_INPUT, state.currentStep)
        assertTrue(state.showPasswordInput)
    }

    @Test
    fun `setPassword and confirmPassword should accept ephemeral input`() = runTest {
        // When
        viewModel.setPassword("password123".toCharArray())
        viewModel.setConfirmPassword("password123".toCharArray())
        
        // Then: StateFlow should NOT leak password plaintext
        val state = viewModel.uiState.value
        assertNull(state.error)
    }

    @Test
    fun `confirmPasswordAndCreate validation should fail if empty`() = runTest {
        // Given
        viewModel.setPassword("".toCharArray())
        
        // When
        viewModel.confirmPasswordAndCreate(testAuthContext)
        
        // Then
        assertEquals("請輸入密碼", viewModel.uiState.value.error)
    }

    @Test
    fun `confirmPasswordAndCreate validation should fail if mismatch`() = runTest {
        // Given
        viewModel.setPassword("pass123".toCharArray())
        viewModel.setConfirmPassword("pass456".toCharArray())
        
        // When
        viewModel.confirmPasswordAndCreate(testAuthContext)
        
        // Then
        assertEquals("密碼不一致", viewModel.uiState.value.error)
    }
    
    @Test
    fun `confirmPasswordAndCreate validation should fail if too short`() = runTest {
        // Given
        viewModel.setPassword("123".toCharArray())
        viewModel.setConfirmPassword("123".toCharArray())
        
        // When
        viewModel.confirmPasswordAndCreate(testAuthContext)
        
        // Then
        assertEquals("密碼至少需要6個字符", viewModel.uiState.value.error)
    }

    @Test
    fun `confirmPasswordAndCreate success should create wallet and get mnemonic`() = runTest {
        // Given
        viewModel.startWalletCreation("New Wallet")
        viewModel.setPassword("password123".toCharArray())
        viewModel.setConfirmPassword("password123".toCharArray())
        
        coEvery { createWalletUseCase.createWithMnemonic(any(), any(), any(), any(), any()) } returns flowOf(
            Result.Success(CreateWalletUseCase.CreatedWallet(mockWallet, com.cbstudio.wearwallet.core.security.EphemeralMnemonicHolder.fromWords(listOf("word1", "word2", "word3"))))
        )
        
        // When
        viewModel.confirmPasswordAndCreate(testAuthContext)
        testScheduler.advanceUntilIdle()
        
        // Then
        coVerify { createWalletUseCase.createWithMnemonic(eq("New Wallet"), any(), eq(ChainType.ETHEREUM), isNull(), eq(testAuthContext)) }
        
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(listOf("word1", "word2", "word3"), state.mnemonicHolder?.getWords())
        assertEquals(CreateWalletViewModel.CreationStep.SHOW_WARNING, state.currentStep)
        assertTrue(state.showSafetyWarning)
        assertNull(state.error)
    }
    
    @Test
    fun `confirmPasswordAndCreate failure should update error`() = runTest {
        // Given
        viewModel.startWalletCreation("New Wallet")
        viewModel.setPassword("password123".toCharArray())
        viewModel.setConfirmPassword("password123".toCharArray())
        
        coEvery { createWalletUseCase.createWithMnemonic(any(), any(), any(), any(), any()) } returns flowOf(
            Result.Failure(Exception("Creation Failed"))
        )
        
        // When
        viewModel.confirmPasswordAndCreate(testAuthContext)
        testScheduler.advanceUntilIdle()
        
        // Then
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Creation Failed", state.error)
        assertEquals(CreateWalletViewModel.CreationStep.INITIAL, state.currentStep)
    }

    @Test
    fun `acknowledgeWarning should show mnemonic`() = runTest {
        // Given
        // Manually set state or simulate flow. Here manually checking method logic implies previous state.
        // We can just call the method as it updates state independently of previous state usually or just updates specific fields.
        
        // When
        viewModel.acknowledgeWarning()
        
        // Then
        val state = viewModel.uiState.value
        assertFalse(state.showSafetyWarning)
        assertTrue(state.showMnemonic)
        assertEquals(CreateWalletViewModel.CreationStep.SHOW_MNEMONIC, state.currentStep)
    }

    @Test
    fun `confirmBackup should complete creation`() = runTest {
        // When
        viewModel.confirmBackup()
        
        // Then
        val state = viewModel.uiState.value
        assertFalse(state.showMnemonic)
        assertTrue(state.walletCreated)
        assertEquals(CreateWalletViewModel.CreationStep.COMPLETED, state.currentStep)
    }
}

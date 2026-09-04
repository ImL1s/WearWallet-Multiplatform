package com.cbstudio.wearwallet.presentation.wallet.screens.import

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.WalletAccount
import com.cbstudio.wearwallet.core.domain.usecase.wallet.ImportWalletUseCase
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ImportWalletViewModelTest : KoinTest {

    private lateinit var viewModel: ImportWalletViewModel
    private lateinit var importWalletUseCase: ImportWalletUseCase
    
    private val testDispatcher = StandardTestDispatcher()

    private val mockWallet = WalletAccount(
        id = "wallet-1",
        name = "Imported Wallet",
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
        
        importWalletUseCase = mockk(relaxed = true)
        
        startKoin {
            modules(module {
                single { importWalletUseCase }
            })
        }
        
        viewModel = ImportWalletViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        stopKoin()
    }

    @Test
    fun `selectImportType should update state`() = runTest {
        // When
        viewModel.selectImportType(ImportWalletViewModel.ImportType.MNEMONIC)
        
        // Then
        val state = viewModel.uiState.value
        assertEquals(ImportWalletViewModel.ImportType.MNEMONIC, state.importType)
        assertEquals(ImportWalletViewModel.ImportStep.INPUT_DATA, state.currentStep)
    }

    @Test
    fun `updateInput with valid mnemonic should set inputValid to true`() = runTest {
        // Given
        viewModel.selectImportType(ImportWalletViewModel.ImportType.MNEMONIC)
        val validMnemonic = "word1 word2 word3 word4 word5 word6 word7 word8 word9 word10 word11 word12"
        
        // When
        viewModel.updateInput(validMnemonic)
        
        // Then
        assertTrue(viewModel.uiState.value.inputValid)
    }

    @Test
    fun `updateInput with invalid mnemonic should set inputValid to false`() = runTest {
        // Given
        viewModel.selectImportType(ImportWalletViewModel.ImportType.MNEMONIC)
        val invalidMnemonic = "word1 word2"
        
        // When
        viewModel.updateInput(invalidMnemonic)
        
        // Then
        assertFalse(viewModel.uiState.value.inputValid)
    }

    @Test
    fun `updateInput with valid private key should set inputValid to true`() = runTest {
        // Given
        viewModel.selectImportType(ImportWalletViewModel.ImportType.PRIVATE_KEY)
        // 64 hex chars
        val validKey = "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef" 
        
        // When
        viewModel.updateInput(validKey)
        
        // Then
        assertTrue(viewModel.uiState.value.inputValid)
    }

    @Test
    fun `proceedToPasswordInput with valid input should succeed`() = runTest {
        // Given
        viewModel.selectImportType(ImportWalletViewModel.ImportType.MNEMONIC)
        viewModel.updateInput("word1 word2 word3 word4 word5 word6 word7 word8 word9 word10 word11 word12")
        
        // When
        viewModel.proceedToPasswordInput()
        
        // Then
        assertEquals(ImportWalletViewModel.ImportStep.PASSWORD_INPUT, viewModel.uiState.value.currentStep)
        assertTrue(viewModel.uiState.value.showPasswordInput)
    }

    @Test
    fun `importFromMnemonic success should update state`() = runTest {
        // Given
        val mnemonic = "word1 word2 word3 word4 word5 word6 word7 word8 word9 word10 word11 word12"
        val password = "password123"
        
        viewModel.selectImportType(ImportWalletViewModel.ImportType.MNEMONIC)
        viewModel.updateInput(mnemonic)
        viewModel.proceedToPasswordInput()
        viewModel.setPassword(password.toCharArray())
        viewModel.setConfirmPassword(password.toCharArray())
        viewModel.setWalletName("My Wallet")
        
        coEvery { 
            importWalletUseCase.importFromMnemonic(any(), any(), any(), any(), any()) 
        } returns flowOf(Result.Success(mockWallet))
        
        // When
        viewModel.importWallet(testAuthContext)
        testScheduler.advanceUntilIdle()
        
        // Then
        val state = viewModel.uiState.value
        assertTrue(state.walletImported)
        assertTrue(state.importCompleted)
        assertEquals(ImportWalletViewModel.ImportStep.COMPLETED, state.currentStep)
        
        coVerify { 
            importWalletUseCase.importFromMnemonic(eq("My Wallet"), any(), any(), any(), eq(testAuthContext)) 
        }
    }

    @Test
    fun `importFromMnemonic failure should show error`() = runTest {
        // Given
        val mnemonic = "word1 word2 word3 word4 word5 word6 word7 word8 word9 word10 word11 word12"
        val password = "password123"
        
        viewModel.selectImportType(ImportWalletViewModel.ImportType.MNEMONIC)
        viewModel.updateInput(mnemonic)
        viewModel.proceedToPasswordInput()
        viewModel.setPassword(password.toCharArray())
        viewModel.setConfirmPassword(password.toCharArray())
        
        coEvery { 
            importWalletUseCase.importFromMnemonic(any(), any(), any(), any(), any()) 
        } returns flowOf(Result.Failure(Exception("Import Failed")))
        
        // When
        viewModel.importWallet(testAuthContext)
        testScheduler.advanceUntilIdle()
        
        // Then
        val state = viewModel.uiState.value
        assertEquals("Import Failed", state.error)
        assertFalse(state.walletImported)
    }

    @Test
    fun `validate passwords mismatch`() = runTest {
        // Given
        viewModel.setPassword("pass123".toCharArray())
        viewModel.setConfirmPassword("pass456".toCharArray())
        
        // When
        viewModel.importWallet(testAuthContext)
        
        // Then
        assertEquals("密碼不一致", viewModel.uiState.value.error)
    }
}

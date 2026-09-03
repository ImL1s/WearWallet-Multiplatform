package com.cbstudio.wearwallet.presentation.wallet.screens.settings

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.WalletAccount
import com.cbstudio.wearwallet.core.domain.model.WalletType
import com.cbstudio.wearwallet.core.domain.repository.WalletRepository
import com.cbstudio.wearwallet.core.domain.usecase.wallet.CreateWalletUseCase
import com.cbstudio.wearwallet.core.security.AuthOperation
import com.cbstudio.wearwallet.core.security.AuthenticationContext
import com.cbstudio.wearwallet.core.security.PlatformAuthHandle
import com.cbstudio.wearwallet.core.security.TestPlatformAuthenticator
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WalletManagementViewModelTest : KoinTest {

    private lateinit var viewModel: WalletManagementViewModel
    private lateinit var walletRepository: WalletRepository
    private lateinit var createWalletUseCase: CreateWalletUseCase

    private val testDispatcher = StandardTestDispatcher()

    private val hotWallet1 = WalletAccount(
        id = "1",
        name = "Primary Hot Wallet",
        address = "0x71C7656EC7ab88b098defB751B7401B5f6d8976F",
        publicKey = "0x04pubkey1",
        keyAlias = "ww_key_alias_hot_1",
        keyBackend = "KEYSTORE",
        keyFormatVersion = 2,
        requiresAuth = true,
        chainType = ChainType.ETHEREUM,
        walletType = WalletType.HOT_WALLET,
        isActive = true
    )

    private val hotWallet2 = WalletAccount(
        id = "2",
        name = "Secondary Hot Wallet",
        address = "0x1234567890123456789012345678901234567890",
        publicKey = "0x04pubkey2",
        keyAlias = "ww_key_alias_hot_2",
        keyBackend = "KEYSTORE",
        keyFormatVersion = 2,
        requiresAuth = true,
        chainType = ChainType.ETHEREUM,
        walletType = WalletType.HOT_WALLET,
        isActive = false
    )

    private val hardwareWallet = WalletAccount(
        id = "3",
        name = "Keystone Hardware Wallet",
        address = "0x9876543210987654321098765432109876543210",
        publicKey = "0x04keystonepub",
        keyAlias = null,
        keyBackend = null,
        keyFormatVersion = 1,
        requiresAuth = false,
        chainType = ChainType.ETHEREUM,
        walletType = WalletType.KEYSTONE,
        isActive = false
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        walletRepository = mockk(relaxed = true)
        createWalletUseCase = mockk(relaxed = true)

        coEvery { walletRepository.getAllWallets() } returns Result.Success(listOf(hotWallet1, hotWallet2))
        coEvery { walletRepository.getActiveWallet() } returns Result.Success(hotWallet1)

        startKoin {
            modules(module {
                single { walletRepository }
                single { createWalletUseCase }
            })
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        stopKoin()
    }

    @Test
    fun `init loads all wallets and active wallet correctly`() = runTest {
        viewModel = WalletManagementViewModel()
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.wallets.size)
        assertEquals(hotWallet1, state.activeWallet)
        assertFalse(state.isLoading)
        assertNull(state.error)
    }

    @Test
    fun `requestDeleteWallet on hot wallet enters DELETE_AUTH_REQUIRED state`() = runTest {
        viewModel = WalletManagementViewModel()
        testScheduler.advanceUntilIdle()

        // User clicks delete on hot wallet 2
        viewModel.requestDeleteWallet(hotWallet2)

        val state = viewModel.uiState.value
        assertEquals(hotWallet2, state.walletToDelete)
        assertTrue(state.isDeleteAuthRequired, "Must require auth to delete hot wallet")
        assertTrue(state.showDeleteDialog)
    }

    @Test
    fun `confirmDeleteWallet with valid DELETE auth handle executes 2-phase deletion`() = runTest {
        viewModel = WalletManagementViewModel()
        testScheduler.advanceUntilIdle()

        viewModel.requestDeleteWallet(hotWallet2)

        val deleteHandle = TestPlatformAuthenticator.issueHandle(
            keyId = hotWallet2.keyAlias!!,
            operation = AuthOperation.DELETE,
            intentFingerprint = hotWallet2.address
        )
        val authContext = AuthenticationContext(authHandle = deleteHandle)

        coEvery { walletRepository.deleteWallet("2", any()) } returns Result.Success(Unit)
        coEvery { walletRepository.getAllWallets() } returns Result.Success(listOf(hotWallet1))

        // When
        viewModel.confirmDeleteWallet(authContext)
        testScheduler.advanceUntilIdle()

        // Then
        coVerify(exactly = 1) { 
            walletRepository.deleteWallet("2", match { 
                it?.authHandle?.operation == AuthOperation.DELETE && it.authHandle?.keyId == hotWallet2.keyAlias 
            }) 
        }
        val state = viewModel.uiState.value
        assertEquals(1, state.wallets.size)
        assertFalse(state.showDeleteDialog)
        assertNull(state.walletToDelete)
        assertTrue(deleteHandle.isInvalidated, "DELETE handle must be invalidated post-operation")
    }

    @Test
    fun `auth failure or cancellation aborts deletion and leaves key and DB row intact`() = runTest {
        viewModel = WalletManagementViewModel()
        testScheduler.advanceUntilIdle()

        viewModel.requestDeleteWallet(hotWallet2)

        // Simulate user cancellation / auth error
        viewModel.onDeleteAuthCancelled()
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 0) { walletRepository.deleteWallet(any(), any()) }

        val state = viewModel.uiState.value
        assertFalse(state.showDeleteDialog)
        assertNull(state.walletToDelete)
        assertEquals(2, state.wallets.size)
    }

    @Test
    fun `deleting hardware wallet bypasses biometric auth prompt`() = runTest {
        coEvery { walletRepository.getAllWallets() } returns Result.Success(listOf(hotWallet1, hardwareWallet))
        coEvery { walletRepository.deleteWallet("3", null) } returns Result.Success(Unit)

        viewModel = WalletManagementViewModel()
        testScheduler.advanceUntilIdle()

        viewModel.requestDeleteWallet(hardwareWallet)

        val state = viewModel.uiState.value
        assertFalse(state.isDeleteAuthRequired, "Hardware wallet holds no KeyVault key, no biometric auth required")

        viewModel.confirmDeleteWallet(authContext = null)
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { walletRepository.deleteWallet("3", null) }
    }

    @Test
    fun `deleting active wallet automatically switches active wallet to remaining wallet`() = runTest {
        viewModel = WalletManagementViewModel()
        testScheduler.advanceUntilIdle()

        // Deleting active wallet 1
        viewModel.requestDeleteWallet(hotWallet1)

        val deleteHandle = TestPlatformAuthenticator.issueHandle(
            keyId = hotWallet1.keyAlias!!,
            operation = AuthOperation.DELETE,
            intentFingerprint = hotWallet1.address
        )
        coEvery { walletRepository.deleteWallet("1", any()) } returns Result.Success(Unit)
        coEvery { walletRepository.setActiveWallet("2") } returns Result.Success(Unit)
        coEvery { walletRepository.getAllWallets() } returns Result.Success(listOf(hotWallet2))
        coEvery { walletRepository.getActiveWallet() } returns Result.Success(hotWallet2)

        viewModel.confirmDeleteWallet(AuthenticationContext(authHandle = deleteHandle))
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { walletRepository.deleteWallet("1", any()) }
        val state = viewModel.uiState.value
        assertEquals(1, state.wallets.size)
        assertEquals(hotWallet2, state.activeWallet)
    }

    @Test
    fun `prevent deleting the last remaining wallet`() = runTest {
        coEvery { walletRepository.getAllWallets() } returns Result.Success(listOf(hotWallet1))
        viewModel = WalletManagementViewModel()
        testScheduler.advanceUntilIdle()

        viewModel.requestDeleteWallet(hotWallet1)

        val state = viewModel.uiState.value
        assertTrue(state.error?.contains("最後一個錢包") == true || state.error?.contains("Cannot delete last wallet") == true)
        assertFalse(state.showDeleteDialog)
        coVerify(exactly = 0) { walletRepository.deleteWallet(any(), any()) }
    }
}



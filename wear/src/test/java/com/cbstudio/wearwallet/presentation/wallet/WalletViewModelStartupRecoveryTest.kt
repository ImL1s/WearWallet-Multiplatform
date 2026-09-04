package com.cbstudio.wearwallet.presentation.wallet

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.WalletAccount
import com.cbstudio.wearwallet.core.domain.repository.WalletRepository
import com.cbstudio.wearwallet.core.recovery.StartupRecoveryCoordinator
import com.cbstudio.wearwallet.core.recovery.StartupRecoveryState
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WalletViewModelStartupRecoveryTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockRepository: WalletRepository
    private lateinit var mockCoordinator: StartupRecoveryCoordinator

    private val mockCoordinatorState = MutableStateFlow<StartupRecoveryState>(StartupRecoveryState.Initializing)
    private val mockCoordinatorError = MutableStateFlow<Throwable?>(null)

    private val testWallet = WalletAccount(
        id = "wallet-1",
        name = "Main Wallet",
        address = "0x1234567890abcdef1234567890abcdef12345678",
        publicKey = "0xpubkey",
        chainType = ChainType.ETHEREUM
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockRepository = mockk(relaxed = true)
        mockCoordinator = mockk(relaxed = true)

        every { mockCoordinator.state } returns mockCoordinatorState
        every { mockCoordinator.reconciliationError } returns mockCoordinatorError
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testWhenCoordinatorFails_walletViewModelIsBlockedAndDoesNotNavigateToCreate() = runTest(testDispatcher) {
        // Given: coordinator reconciliation fails
        val failureEx = IllegalStateException("Disk corrupted during reconciliation")
        coEvery { mockCoordinator.awaitReady() } returns Result.Failure(failureEx)

        // When: WalletViewModel is initialized
        val viewModel = WalletViewModel(
            walletRepository = mockRepository,
            startupRecoveryCoordinator = mockCoordinator
        )
        testScheduler.advanceUntilIdle()

        // Then: appState must be blocked, and shouldNavigateToCreate MUST be false (NEVER overwrite corrupt DB)
        val state = viewModel.appState.value
        assertFalse(state.isLoading)
        assertFalse(state.hasWallet)
        assertFalse(state.shouldNavigateToCreate, "MUST NOT navigate to create wallet when startup recovery fails")
        assertTrue(state.isBlocked)
        assertEquals("Disk corrupted during reconciliation", state.error)

        // And: repository.getAllWallets() must NOT have been called
        coVerify(exactly = 0) { mockRepository.getAllWallets() }
    }

    @Test
    fun testWhenCoordinatorReadyAndWalletsExist_hasWalletIsTrue() = runTest(testDispatcher) {
        // Given: coordinator is ready and repository has wallet
        coEvery { mockCoordinator.awaitReady() } returns Result.Success(Unit)
        coEvery { mockRepository.getAllWallets() } returns Result.Success(listOf(testWallet))

        // When: WalletViewModel is initialized
        val viewModel = WalletViewModel(
            walletRepository = mockRepository,
            startupRecoveryCoordinator = mockCoordinator
        )
        testScheduler.advanceUntilIdle()

        // Then: state is normal with hasWallet = true
        val state = viewModel.appState.value
        assertFalse(state.isLoading)
        assertTrue(state.hasWallet)
        assertFalse(state.shouldNavigateToCreate)
        assertFalse(state.isBlocked)
        assertEquals(null, state.error)
    }

    @Test
    fun testWhenCoordinatorReadyAndNoWallets_shouldNavigateToCreateIsTrue() = runTest(testDispatcher) {
        // Given: coordinator is ready and repository is cleanly empty
        coEvery { mockCoordinator.awaitReady() } returns Result.Success(Unit)
        coEvery { mockRepository.getAllWallets() } returns Result.Success(emptyList())

        // When: WalletViewModel is initialized
        val viewModel = WalletViewModel(
            walletRepository = mockRepository,
            startupRecoveryCoordinator = mockCoordinator
        )
        testScheduler.advanceUntilIdle()

        // Then: shouldNavigateToCreate is true for fresh onboarding
        val state = viewModel.appState.value
        assertFalse(state.isLoading)
        assertFalse(state.hasWallet)
        assertTrue(state.shouldNavigateToCreate)
        assertFalse(state.isBlocked)
    }

    @Test
    fun testWhenCoordinatorReadyAndRepositoryQueryFails_doesNotNavigateToCreate() = runTest(testDispatcher) {
        // Given: coordinator is ready, but repository query fails with SQLite exception
        coEvery { mockCoordinator.awaitReady() } returns Result.Success(Unit)
        coEvery { mockRepository.getAllWallets() } returns Result.Failure(IllegalStateException("SQLite database locked"))

        // When: WalletViewModel is initialized
        val viewModel = WalletViewModel(
            walletRepository = mockRepository,
            startupRecoveryCoordinator = mockCoordinator
        )
        testScheduler.advanceUntilIdle()

        // Then: shouldNavigateToCreate MUST be false to prevent user from overwriting locked/corrupted DB
        val state = viewModel.appState.value
        assertFalse(state.isLoading)
        assertFalse(state.hasWallet)
        assertFalse(state.shouldNavigateToCreate, "MUST NOT navigate to create wallet when DB query fails")
        assertTrue(state.isBlocked)
        assertNotNull(state.error)
    }
}

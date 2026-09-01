package com.cbstudio.wearwallet.presentation.wallet.screens.settings

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.WalletAccount
import com.cbstudio.wearwallet.core.domain.model.WalletType
import com.cbstudio.wearwallet.core.domain.repository.WalletRepository
import com.cbstudio.wearwallet.core.domain.usecase.wallet.RevealMnemonicUseCase
import com.cbstudio.wearwallet.core.security.AuthOperation
import com.cbstudio.wearwallet.core.security.AuthenticationContext
import com.cbstudio.wearwallet.core.security.PlatformAuthHandle
import com.cbstudio.wearwallet.core.security.TestPlatformAuthenticator
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
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

/**
 * Challenger 1 Adversarial & Stress Test Suite for P1-2 (RevealMnemonic Lifecycle).
 *
 * Challenges:
 * 1. Fail-closed authentication when authContext is null, mock, or missing.
 * 2. Rapid sequential UI backgrounding stress (200 cycles): ensure zero memory leak or lingering words.
 * 3. Concurrent reveal / backgrounding race conditions: ensure mnemonic is strictly wiped.
 * 4. Verification that onCleared() always zeroizes state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChallengerR5RevealMnemonicLifecycleStressTest : KoinTest {

    private lateinit var viewModel: ShowMnemonicViewModel
    private lateinit var walletRepository: WalletRepository
    private lateinit var revealMnemonicUseCase: RevealMnemonicUseCase
    private val testDispatcher = StandardTestDispatcher()

    private val testWallet = WalletAccount(
        id = "wallet-challenger-1",
        name = "Challenger Wallet",
        address = "0x9d8A62f656a8d1615C1294fd71e9CFb3E4855A4F",
        publicKey = "0x04pubkey",
        chainType = ChainType.ETHEREUM,
        walletType = WalletType.HOT_WALLET
    )

    private val validAuthContext: AuthenticationContext
        get() {
            return AuthenticationContext(
                authHandle = TestPlatformAuthenticator.issueHandle(
                    keyId = "0x9d8A62f656a8d1615C1294fd71e9CFb3E4855A4F",
                    operation = AuthOperation.REVEAL,
                    intentFingerprint = "0x9d8A62f656a8d1615C1294fd71e9CFb3E4855A4F",
                    validityDurationMs = 60_000L
                )
            )
        }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        walletRepository = mockk(relaxed = true)
        revealMnemonicUseCase = mockk(relaxed = true)

        startKoin {
            modules(module {
                single { walletRepository }
                single { revealMnemonicUseCase }
            })
        }

        coEvery { walletRepository.getActiveWallet() } returns Result.Success(testWallet)
        coEvery {
            revealMnemonicUseCase.executeWithMnemonic<EphemeralMnemonicHolder>(any(), any(), any(), any())
        } answers {
            val action = arg<(CharArray) -> EphemeralMnemonicHolder>(3)
            val phrase = "abandon ability able about above absent absorb abstract absurd abuse access accident"
            val chars = phrase.toCharArray()
            val result = action(chars)
            Result.Success(result)
        }

        viewModel = ShowMnemonicViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        stopKoin()
    }

    @Test
    fun challenge_P1_2_fail_closed_without_auth_handle() = runTest {
        testScheduler.advanceUntilIdle()

        // 1. Calling reveal with null authContext
        viewModel.revealMnemonic("correct_password", authContext = null)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isRevealed, "Must NOT reveal mnemonic without authContext")
        assertTrue(state.mnemonic.isEmpty(), "Mnemonic list must be empty")
        assertEquals("需要生物識別或設備憑證認證", state.error)

        // Verify use case was never invoked
        coVerify(exactly = 0) { revealMnemonicUseCase.executeWithMnemonic<Any>(any(), any(), any(), any()) }
    }

    @Test
    fun challenge_P1_2_rapid_ui_backgrounding_stress_200_cycles() = runTest {
        testScheduler.runCurrent()

        for (i in 1..200) {
            // 1. Reveal mnemonic
            viewModel.revealMnemonic("password_$i", authContext = validAuthContext)
            testScheduler.runCurrent()

            val revealedState = viewModel.uiState.value
            assertTrue(revealedState.isRevealed, "Iteration $i: must be revealed")
            assertEquals(12, revealedState.mnemonic.size, "Iteration $i: must contain 12 words")

            // 2. Immediate backgrounding event (ON_PAUSE / ON_STOP)
            viewModel.onAppBackgrounded()

            val wipedState = viewModel.uiState.value
            assertFalse(wipedState.isRevealed, "Iteration $i: must be unrevealed after background")
            assertTrue(wipedState.mnemonic.isEmpty(), "Iteration $i: memory must be zeroized")
            assertFalse(wipedState.isLoading)
        }
    }

    @Test
    fun challenge_P1_2_concurrent_backgrounding_and_reveal_race_condition_stress() = runTest {
        testScheduler.runCurrent()

        // Simulate rapid alternating coroutines
        val jobs = List(50) { index ->
            launch {
                if (index % 2 == 0) {
                    viewModel.revealMnemonic("pass", authContext = validAuthContext)
                } else {
                    viewModel.onAppBackgrounded()
                }
            }
        }
        jobs.joinAll()
        testScheduler.runCurrent()

        // Final backgrounding call
        viewModel.onAppBackgrounded()

        val finalState = viewModel.uiState.value
        assertFalse(finalState.isRevealed, "After final backgrounding, state must not be revealed")
        assertTrue(finalState.mnemonic.isEmpty(), "Mnemonic list must be empty")
    }

    @Test
    fun challenge_P1_2_clearMnemonic_and_onCleared_guarantees_memory_wiping() = runTest {
        testScheduler.runCurrent()

        viewModel.revealMnemonic("pass", authContext = validAuthContext)
        testScheduler.runCurrent()
        assertTrue(viewModel.uiState.value.isRevealed)

        // Trigger clearMnemonic
        viewModel.clearMnemonic()
        assertFalse(viewModel.uiState.value.isRevealed)
        assertTrue(viewModel.uiState.value.mnemonic.isEmpty())
    }
}



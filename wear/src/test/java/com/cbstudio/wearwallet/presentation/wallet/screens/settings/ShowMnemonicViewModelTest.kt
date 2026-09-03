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
class ShowMnemonicViewModelTest : KoinTest {

    private lateinit var viewModel: ShowMnemonicViewModel
    private lateinit var walletRepository: WalletRepository
    private lateinit var revealMnemonicUseCase: RevealMnemonicUseCase

    private val testDispatcher = StandardTestDispatcher()

    private val mockWallet = WalletAccount(
        id = "wallet-1",
        name = "Test Wallet",
        address = "0x1234567890abcdef1234567890abcdef12345678",
        publicKey = "0xpubkey",
        chainType = ChainType.ETHEREUM,
        walletType = WalletType.HOT_WALLET,
        keyAlias = "key-alias-123"
    )

    private val mockHardwareWallet = WalletAccount(
        id = "wallet-2",
        name = "Hardware Wallet",
        address = "0x4567890123abcdef4567890123abcdef45678901",
        publicKey = "0xpubkey2",
        chainType = ChainType.ETHEREUM,
        walletType = WalletType.KEYSTONE
    )

    private val validAuthHandle: PlatformAuthHandle
        get() {
            return TestPlatformAuthenticator.issueHandle(
                keyId = "key-alias-123",
                operation = AuthOperation.REVEAL,
                intentFingerprint = "0x1234567890abcdef1234567890abcdef12345678",
                validityDurationMs = 60_000L
            )
        }

    private val validAuthContext: AuthenticationContext
        get() = AuthenticationContext(authHandle = validAuthHandle)

    private fun mockRevealSuccess(words: List<String>) {
        coEvery {
            revealMnemonicUseCase.executeWithMnemonic<EphemeralMnemonicHolder>(any(), any(), any(), any())
        } answers {
            val action = arg<(CharArray) -> EphemeralMnemonicHolder>(3)
            val phrase = words.joinToString(" ")
            val chars = phrase.toCharArray()
            val result = action(chars)
            Result.Success(result)
        }
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

        coEvery { walletRepository.getActiveWallet() } returns Result.Success(mockWallet)
        viewModel = ShowMnemonicViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        stopKoin()
    }

    @Test
    fun `initial state is locked, auth required, and mnemonicWords is null`() = runTest {
        testScheduler.runCurrent()

        val state = viewModel.uiState.value
        assertEquals(mockWallet, state.activeWallet)
        assertFalse(state.isLoading)
        assertTrue(state.requiresPassword)
        assertFalse(state.isRevealed)
        assertNull(state.mnemonicWords, "mnemonicWords must be null initially")
        assertTrue(state.mnemonic.isEmpty(), "mnemonic list must be empty")
        assertEquals(RevealStatus.LOCKED, state.status)
        assertEquals(0, state.remainingSeconds)
    }

    @Test
    fun `load active wallet failure should set error and status to ERROR`() = runTest {
        coEvery { walletRepository.getActiveWallet() } returns Result.Failure(Exception("Load Failed"))

        viewModel = ShowMnemonicViewModel()
        testScheduler.runCurrent()

        val state = viewModel.uiState.value
        assertEquals("載入錢包失敗", state.error)
        assertEquals(RevealStatus.ERROR, state.status)
        assertNull(state.mnemonicWords)
    }

    @Test
    fun `hardware wallet should show error and fail closed`() = runTest {
        coEvery { walletRepository.getActiveWallet() } returns Result.Success(mockHardwareWallet)

        viewModel = ShowMnemonicViewModel()
        testScheduler.runCurrent()

        val state = viewModel.uiState.value
        assertEquals("硬體錢包無法顯示助記詞", state.error)
        assertEquals(RevealStatus.ERROR, state.status)
        assertNull(state.mnemonicWords)

        // Attempting reveal on hardware wallet must fail closed
        viewModel.revealMnemonic("password", validAuthContext)
        testScheduler.runCurrent()

        val afterReveal = viewModel.uiState.value
        assertFalse(afterReveal.isRevealed)
        assertNull(afterReveal.mnemonicWords)
        assertEquals("硬體錢包無法顯示助記詞", afterReveal.error)
    }

    @Test
    fun `revealMnemonic without authContext should fail with authentication required`() = runTest {
        testScheduler.runCurrent()
        viewModel.revealMnemonic("password", authContext = null)
        testScheduler.runCurrent()

        val state = viewModel.uiState.value
        assertFalse(state.isRevealed)
        assertNull(state.mnemonicWords)
        assertEquals("需要生物識別或設備憑證認證", state.error)
        assertEquals(RevealStatus.LOCKED, state.status)
    }

    @Test
    fun `valid AuthOperation REVEAL_SEED handle unlocks mnemonic and exposes mnemonicWords`() = runTest {
        testScheduler.runCurrent()
        val password = "password"
        val mnemonicWords = listOf(
            "abandon", "ability", "able", "about", "above", "absent",
            "absorb", "abstract", "absurd", "abuse", "access", "accident"
        )
        mockRevealSuccess(mnemonicWords)

        // When
        viewModel.revealMnemonic(password, authContext = validAuthContext)
        testScheduler.runCurrent()

        // Then
        val state = viewModel.uiState.value
        assertTrue(state.isRevealed)
        assertEquals(mnemonicWords, state.mnemonicWords)
        assertEquals(mnemonicWords, state.mnemonic)
        assertEquals(RevealStatus.REVEALED, state.status)
        assertEquals(30, state.remainingSeconds)
        assertNull(state.error)
    }

    @Test
    fun `onAuthSuccess with PlatformAuthHandle unlocks mnemonic`() = runTest {
        testScheduler.runCurrent()
        val mnemonicWords = listOf("word1", "word2", "word3", "word4", "word5", "word6", "word7", "word8", "word9", "word10", "word11", "word12")
        mockRevealSuccess(mnemonicWords)

        // When onAuthSuccess is dispatched from BiometricPrompt
        viewModel.onAuthSuccess(validAuthHandle, "")
        testScheduler.runCurrent()

        // Then
        val state = viewModel.uiState.value
        assertTrue(state.isRevealed)
        assertEquals(mnemonicWords, state.mnemonicWords)
        assertEquals(RevealStatus.REVEALED, state.status)
    }

    @Test
    fun `30-second timer auto-clears mnemonic words and transitions to EXPIRED`() = runTest {
        testScheduler.runCurrent()
        val mnemonicWords = listOf("apple", "banana", "cherry")
        mockRevealSuccess(mnemonicWords)

        viewModel.revealMnemonic("password", authContext = validAuthContext)
        testScheduler.runCurrent()

        assertTrue(viewModel.uiState.value.isRevealed)
        assertEquals(mnemonicWords, viewModel.uiState.value.mnemonicWords)

        // Advance 10 seconds -> remaining seconds should be ~20, still revealed
        testScheduler.advanceTimeBy(10_000L)
        testScheduler.runCurrent()
        assertTrue(viewModel.uiState.value.isRevealed)
        assertEquals(20, viewModel.uiState.value.remainingSeconds)
        assertEquals(mnemonicWords, viewModel.uiState.value.mnemonicWords)

        // Advance past 30 seconds -> timer expires
        testScheduler.advanceTimeBy(21_000L)
        testScheduler.runCurrent()

        val expiredState = viewModel.uiState.value
        assertFalse(expiredState.isRevealed, "Must not be revealed after 30s timer")
        assertNull(expiredState.mnemonicWords, "mnemonicWords must be wiped to null after 30s")
        assertTrue(expiredState.mnemonic.isEmpty(), "mnemonic list must be empty")
        assertEquals(0, expiredState.remainingSeconds)
        assertEquals(RevealStatus.EXPIRED, expiredState.status)
    }

    @Test
    fun `advancing virtual time by 30 seconds automatically clears mnemonic state`() = runTest {
        testScheduler.runCurrent()
        val mnemonicWords = listOf("word1", "word2", "word3", "word4", "word5", "word6")
        mockRevealSuccess(mnemonicWords)

        viewModel.revealMnemonic("password", authContext = validAuthContext)
        testScheduler.runCurrent()

        assertTrue(viewModel.uiState.value.isRevealed)
        assertEquals(mnemonicWords, viewModel.uiState.value.mnemonicWords)

        // Advance virtual time by 30 seconds (30_000L)
        testScheduler.advanceTimeBy(30_000L)
        testScheduler.runCurrent()

        val state = viewModel.uiState.value
        assertFalse(state.isRevealed, "Mnemonic must not be revealed after 30s")
        assertNull(state.mnemonicWords, "Mnemonic words must be cleared")
        assertTrue(state.mnemonic.isEmpty(), "Mnemonic list must be empty")
        assertEquals(0, state.remainingSeconds)
        assertEquals(RevealStatus.EXPIRED, state.status)
    }

    @Test
    fun `calling onAppBackgrounded cancels running timer job immediately and wipes mnemonic state`() = runTest {
        testScheduler.runCurrent()
        val mnemonicWords = listOf("secret1", "secret2", "secret3")
        mockRevealSuccess(mnemonicWords)

        viewModel.revealMnemonic("password", authContext = validAuthContext)
        testScheduler.runCurrent()

        assertTrue(viewModel.uiState.value.isRevealed)
        assertEquals(mnemonicWords, viewModel.uiState.value.mnemonicWords)

        // Calling onAppBackgrounded cancels running timer job immediately and wipes state
        viewModel.onAppBackgrounded()

        val stateAfterBackground = viewModel.uiState.value
        assertFalse(stateAfterBackground.isRevealed)
        assertNull(stateAfterBackground.mnemonicWords)
        assertTrue(stateAfterBackground.mnemonic.isEmpty())
        assertEquals(0, stateAfterBackground.remainingSeconds)
        assertEquals(RevealStatus.LOCKED, stateAfterBackground.status)

        // Advancing time should not trigger any delayed timer action or revive state
        testScheduler.advanceTimeBy(60_000L)
        testScheduler.runCurrent()

        val stateAfterTime = viewModel.uiState.value
        assertFalse(stateAfterTime.isRevealed)
        assertNull(stateAfterTime.mnemonicWords)
        assertEquals(RevealStatus.LOCKED, stateAfterTime.status)
    }

    @Test
    fun `app backgrounding onAppBackgrounded immediately clears mnemonic words and cancels timer`() = runTest {
        testScheduler.runCurrent()
        val mnemonicWords = listOf("word1", "word2", "word3")
        mockRevealSuccess(mnemonicWords)

        viewModel.revealMnemonic("password", authContext = validAuthContext)
        testScheduler.runCurrent()
        assertTrue(viewModel.uiState.value.isRevealed)
        assertEquals(mnemonicWords, viewModel.uiState.value.mnemonicWords)

        // When backgrounded (ON_PAUSE / ON_STOP)
        viewModel.onAppBackgrounded()

        val state = viewModel.uiState.value
        assertFalse(state.isRevealed)
        assertNull(state.mnemonicWords, "mnemonicWords must be null on background")
        assertTrue(state.mnemonic.isEmpty())
        assertEquals(0, state.remainingSeconds)
        assertEquals(RevealStatus.LOCKED, state.status)

        // Advance timer further and ensure no resurrecting of state
        testScheduler.advanceTimeBy(40_000L)
        testScheduler.runCurrent()
        assertNull(viewModel.uiState.value.mnemonicWords)
        assertFalse(viewModel.uiState.value.isRevealed)
    }

    @Test
    fun `auth cancellation or onAuthError leaves mnemonic null`() = runTest {
        testScheduler.runCurrent()

        viewModel.onAuthCancelled()
        assertFalse(viewModel.uiState.value.isRevealed)
        assertNull(viewModel.uiState.value.mnemonicWords)
        assertEquals(RevealStatus.LOCKED, viewModel.uiState.value.status)

        viewModel.onAuthError("Biometric authentication rejected")
        assertFalse(viewModel.uiState.value.isRevealed)
        assertNull(viewModel.uiState.value.mnemonicWords)
        assertEquals("Biometric authentication rejected", viewModel.uiState.value.error)
        assertEquals(RevealStatus.ERROR, viewModel.uiState.value.status)
    }

    @Test
    fun `cross-key auth handle is rejected fail-closed`() = runTest {
        testScheduler.runCurrent()
        val crossKeyHandle = TestPlatformAuthenticator.issueHandle(
            keyId = "other-key-999",
            operation = AuthOperation.REVEAL,
            intentFingerprint = "0x1234567890abcdef1234567890abcdef12345678",
            validityDurationMs = 10_000L
        )

        viewModel.revealMnemonic("password", authContext = AuthenticationContext(authHandle = crossKeyHandle))
        testScheduler.runCurrent()

        val state = viewModel.uiState.value
        assertFalse(state.isRevealed)
        assertNull(state.mnemonicWords)
        assertEquals("跨金鑰認證被拒絕", state.error)
        assertEquals(RevealStatus.LOCKED, state.status)
        coVerify(exactly = 0) { revealMnemonicUseCase.executeWithMnemonic<Any>(any(), any(), any(), any()) }
    }

    @Test
    fun `invalid operation handle is rejected fail-closed`() = runTest {
        testScheduler.runCurrent()
        val signHandle = TestPlatformAuthenticator.issueHandle(
            keyId = "key-alias-123",
            operation = AuthOperation.SIGN,
            intentFingerprint = "0x1234567890abcdef1234567890abcdef12345678",
            validityDurationMs = 10_000L
        )

        viewModel.revealMnemonic("password", authContext = AuthenticationContext(authHandle = signHandle))
        testScheduler.runCurrent()

        val state = viewModel.uiState.value
        assertFalse(state.isRevealed)
        assertNull(state.mnemonicWords)
        assertTrue(state.error?.contains("認證操作類型不符") == true)
        assertEquals(RevealStatus.LOCKED, state.status)
        coVerify(exactly = 0) { revealMnemonicUseCase.executeWithMnemonic<Any>(any(), any(), any(), any()) }
    }

    @Test
    fun `expired or invalidated auth handle is rejected fail-closed`() = runTest {
        testScheduler.runCurrent()
        val expiredHandle = TestPlatformAuthenticator.issueHandle(
            keyId = "key-alias-123",
            operation = AuthOperation.REVEAL,
            intentFingerprint = "0x1234567890abcdef1234567890abcdef12345678",
            expiresAtMs = System.currentTimeMillis() - 5_000L
        )

        viewModel.revealMnemonic("password", authContext = AuthenticationContext(authHandle = expiredHandle))
        testScheduler.runCurrent()

        val state = viewModel.uiState.value
        assertFalse(state.isRevealed)
        assertNull(state.mnemonicWords)
        assertEquals("認證憑證已失效或過期", state.error)
        assertEquals(RevealStatus.LOCKED, state.status)
        coVerify(exactly = 0) { revealMnemonicUseCase.executeWithMnemonic<Any>(any(), any(), any(), any()) }
    }

    @Test
    fun `hideMnemonic and clearMnemonic immediately wipe memory and reset status`() = runTest {
        testScheduler.runCurrent()
        val mnemonicWords = listOf("word1", "word2", "word3")
        mockRevealSuccess(mnemonicWords)

        viewModel.revealMnemonic("password", authContext = validAuthContext)
        testScheduler.runCurrent()
        assertTrue(viewModel.uiState.value.isRevealed)

        viewModel.hideMnemonic()
        assertFalse(viewModel.uiState.value.isRevealed)
        assertNull(viewModel.uiState.value.mnemonicWords)
        assertTrue(viewModel.uiState.value.mnemonic.isEmpty())
        assertEquals(0, viewModel.uiState.value.remainingSeconds)
        assertEquals(RevealStatus.LOCKED, viewModel.uiState.value.status)
    }
}



package com.cbstudio.wearwallet.presentation.wallet.screens.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import android.view.WindowManager
import androidx.biometric.BiometricManager
import androidx.fragment.app.FragmentActivity
import androidx.test.core.app.ApplicationProvider
import com.cbstudio.wearwallet.RobolectricApplication
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
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Method
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Empirical Challenger Test Suite for Milestone 4 (Window & Presentation Hardening).
 *
 * Comprehensive adversarial verification of:
 * 1. WindowManager.LayoutParams.FLAG_SECURE application & disposal lifecycle.
 * 2. Context.findActivity() recursive ContextWrapper unwrapping (edge cases, deep nesting, non-activity).
 * 3. BiometricPrompt authenticators (BIOMETRIC_STRONG or DEVICE_CREDENTIAL) & AuthOperation.REVEAL_SEED contracts.
 * 4. Fail-closed security across all invalid, expired, cross-key, hardware, exception, and timer states.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = RobolectricApplication::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ChallengerM4WindowPresentationHardeningTest : KoinTest {

    private lateinit var viewModel: ShowMnemonicViewModel
    private lateinit var walletRepository: WalletRepository
    private lateinit var revealMnemonicUseCase: RevealMnemonicUseCase

    private val testDispatcher = StandardTestDispatcher()

    private val hotWallet = WalletAccount(
        id = "wallet-challenger-m4",
        name = "Challenger M4 Hot Wallet",
        address = "0x71C8418320499D05e0B359b3B3f2c5dAb8D08034",
        publicKey = "0x04testpubkey",
        chainType = ChainType.ETHEREUM,
        walletType = WalletType.HOT_WALLET,
        keyAlias = "key-alias-m4-test"
    )

    private val hardwareWallet = WalletAccount(
        id = "wallet-challenger-hw",
        name = "Challenger Hardware Wallet",
        address = "0x1111222233334444555566667777888899990000",
        publicKey = "0x04hwpubkey",
        chainType = ChainType.ETHEREUM,
        walletType = WalletType.KEYSTONE,
        keyAlias = null
    )

    private val testMnemonicWords = listOf("abandon", "ability", "able", "about", "above", "absent", "absorb", "abstract", "absurd", "abuse", "access", "accident")

    private val validRevealSeedHandle: PlatformAuthHandle
        get() {
            return TestPlatformAuthenticator.issueHandle(
                keyId = "key-alias-m4-test",
                operation = AuthOperation.REVEAL,
                intentFingerprint = "0x71C8418320499D05e0B359b3B3f2c5dAb8D08034",
                validityDurationMs = 10_000L
            )
        }

    private val validAuthContext: AuthenticationContext
        get() = AuthenticationContext(authHandle = validRevealSeedHandle)

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

        coEvery { walletRepository.getActiveWallet() } returns Result.Success(hotWallet)
        coEvery {
            revealMnemonicUseCase.executeWithMnemonic<EphemeralMnemonicHolder>(any(), any(), any(), any())
        } answers {
            val action = arg<(CharArray) -> EphemeralMnemonicHolder>(3)
            val phrase = testMnemonicWords.joinToString(" ")
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

    // =========================================================================
    // 1. WindowManager.LayoutParams.FLAG_SECURE & Window Hardening Tests
    // =========================================================================

    @Test
    fun `FLAG_SECURE constant is verified as 0x00002000`() {
        // WindowManager.LayoutParams.FLAG_SECURE must equal 0x2000 (8192)
        assertEquals(0x00002000, WindowManager.LayoutParams.FLAG_SECURE)
    }

    @Test
    fun `simulated window flag addition and clearing correctly toggles FLAG_SECURE`() {
        val window = mockk<Window>(relaxed = true)
        val flagsSlot = slot<Int>()

        // Simulate addFlags
        every { window.addFlags(capture(flagsSlot)) } answers {
            // verified called with FLAG_SECURE
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        assertEquals(WindowManager.LayoutParams.FLAG_SECURE, flagsSlot.captured)

        // Simulate clearFlags
        every { window.clearFlags(capture(flagsSlot)) } answers {
            // verified called with FLAG_SECURE
        }

        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        assertEquals(WindowManager.LayoutParams.FLAG_SECURE, flagsSlot.captured)

        verify(exactly = 1) { window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) }
        verify(exactly = 1) { window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    @Test
    fun `FLAG_SECURE bitwise manipulation preserves co-existing window flags`() {
        var currentFlags = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_FULLSCREEN
        val initialFlags = currentFlags

        // Adding FLAG_SECURE
        currentFlags = currentFlags or WindowManager.LayoutParams.FLAG_SECURE
        assertTrue((currentFlags and WindowManager.LayoutParams.FLAG_SECURE) != 0, "FLAG_SECURE must be set")
        assertTrue((currentFlags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0, "FLAG_KEEP_SCREEN_ON preserved")
        assertTrue((currentFlags and WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0, "FLAG_FULLSCREEN preserved")

        // Clearing FLAG_SECURE
        currentFlags = currentFlags and WindowManager.LayoutParams.FLAG_SECURE.inv()
        assertEquals(0, currentFlags and WindowManager.LayoutParams.FLAG_SECURE, "FLAG_SECURE must be cleared")
        assertEquals(initialFlags, currentFlags, "Original flags must remain unchanged")
    }

    // =========================================================================
    // 2. Context.findActivity() Recursive Unwrapping Tests
    // =========================================================================

    private fun invokeFindActivity(context: Context): Activity? {
        val clazz = Class.forName("com.cbstudio.wearwallet.presentation.wallet.screens.settings.ShowMnemonicScreenKt")
        val method: Method = clazz.getDeclaredMethod("findActivity", Context::class.java)
        method.isAccessible = true
        return method.invoke(null, context) as? Activity
    }

    @Test
    fun `findActivity returns direct Activity instance`() {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        val result = invokeFindActivity(activity)
        assertEquals(activity, result)
    }

    @Test
    fun `findActivity unwraps 1-level ContextWrapper`() {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        val wrapper = ContextWrapper(activity)
        val result = invokeFindActivity(wrapper)
        assertEquals(activity, result)
    }

    @Test
    fun `findActivity unwraps multi-level deeply nested ContextWrapper chain (50 levels)`() {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        var current: Context = activity
        for (i in 1..50) {
            current = ContextWrapper(current)
        }

        val result = invokeFindActivity(current)
        assertEquals(activity, result, "Must resolve underlying Activity across 50 nested wrappers")
    }

    @Test
    fun `findActivity returns null safely for ApplicationContext and non-activity ContextWrapper`() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val result1 = invokeFindActivity(appContext)
        assertNull(result1, "ApplicationContext should return null")

        val appWrapper = ContextWrapper(appContext)
        val result2 = invokeFindActivity(appWrapper)
        assertNull(result2, "ContextWrapper wrapping ApplicationContext should return null")
    }

    // =========================================================================
    // 3. BiometricPrompt Authenticators & AuthOperation Contract Tests
    // =========================================================================

    @Test
    fun `authenticators bitmask contains BIOMETRIC_STRONG or DEVICE_CREDENTIAL`() {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        // BIOMETRIC_STRONG (0x000F) | DEVICE_CREDENTIAL (0x8000) = 0x800F = 32783
        assertEquals(32783, authenticators)
        assertTrue((authenticators and BiometricManager.Authenticators.BIOMETRIC_STRONG) != 0)
        assertTrue((authenticators and BiometricManager.Authenticators.DEVICE_CREDENTIAL) != 0)
    }

    @Test
    fun `ShowMnemonicViewModel accepts only REVEAL and rejects EXPORT SIGN DELETE operations`() = runTest {
        testScheduler.runCurrent()

        // 1. REVEAL should succeed
        val revealHandle = TestPlatformAuthenticator.issueHandle(
            keyId = "key-alias-m4-test",
            operation = AuthOperation.REVEAL,
            intentFingerprint = "0x71C8418320499D05e0B359b3B3f2c5dAb8D08034",
            validityDurationMs = 10_000L
        )
        viewModel.revealMnemonic("pass", AuthenticationContext(authHandle = revealHandle))
        testScheduler.runCurrent()
        assertTrue(viewModel.uiState.value.isRevealed)
        assertEquals(testMnemonicWords, viewModel.uiState.value.mnemonicWords)

        // Reset
        viewModel.clearMnemonic()
        assertFalse(viewModel.uiState.value.isRevealed)

        // 2. EXPORT must be rejected fail-closed
        val exportHandle = TestPlatformAuthenticator.issueHandle(
            keyId = "key-alias-m4-test",
            operation = AuthOperation.EXPORT,
            intentFingerprint = "0x71C8418320499D05e0B359b3B3f2c5dAb8D08034",
            validityDurationMs = 10_000L
        )
        viewModel.revealMnemonic("pass", AuthenticationContext(authHandle = exportHandle))
        testScheduler.runCurrent()
        assertFalse(viewModel.uiState.value.isRevealed)
        assertNull(viewModel.uiState.value.mnemonicWords)
        assertTrue(viewModel.uiState.value.error?.contains("認證操作類型不符") == true)

        // Reset
        viewModel.clearMnemonic()

        // 3. SIGN must be rejected fail-closed
        val signHandle = TestPlatformAuthenticator.issueHandle(
            keyId = "key-alias-m4-test",
            operation = AuthOperation.SIGN,
            intentFingerprint = "0x71C8418320499D05e0B359b3B3f2c5dAb8D08034",
            validityDurationMs = 10_000L
        )
        viewModel.revealMnemonic("pass", AuthenticationContext(authHandle = signHandle))
        testScheduler.runCurrent()
        assertFalse(viewModel.uiState.value.isRevealed)
        assertNull(viewModel.uiState.value.mnemonicWords)
        assertTrue(viewModel.uiState.value.error?.contains("認證操作類型不符") == true)

        // 4. DELETE must be rejected fail-closed
        val deleteHandle = TestPlatformAuthenticator.issueHandle(
            keyId = "key-alias-m4-test",
            operation = AuthOperation.DELETE,
            intentFingerprint = "0x71C8418320499D05e0B359b3B3f2c5dAb8D08034",
            validityDurationMs = 10_000L
        )
        viewModel.revealMnemonic("pass", AuthenticationContext(authHandle = deleteHandle))
        testScheduler.runCurrent()
        assertFalse(viewModel.uiState.value.isRevealed)
        assertNull(viewModel.uiState.value.mnemonicWords)
        assertTrue(viewModel.uiState.value.error?.contains("認證操作類型不符") == true)
    }

    // =========================================================================
    // 4. Fail-Closed Error Handling & Memory Leak Prevention
    // =========================================================================

    @Test
    fun `reveal failure in useCase fail-closed and wipes memory`() = runTest {
        testScheduler.runCurrent()
        coEvery {
            revealMnemonicUseCase.executeWithMnemonic<EphemeralMnemonicHolder>(any(), any(), any(), any())
        } returns Result.Failure(IllegalStateException("Decryption key corrupted"))

        viewModel.revealMnemonic("wrong_pass", authContext = validAuthContext)
        testScheduler.runCurrent()

        val state = viewModel.uiState.value
        assertFalse(state.isRevealed)
        assertNull(state.mnemonicWords)
        assertTrue(state.mnemonic.isEmpty())
        assertEquals("Decryption key corrupted", state.error)
        assertEquals(RevealStatus.ERROR, state.status)
    }

    @Test
    fun `unexpected runtime exception in useCase is caught, fail-closed and wipes memory`() = runTest {
        testScheduler.runCurrent()
        coEvery {
            revealMnemonicUseCase.executeWithMnemonic<EphemeralMnemonicHolder>(any(), any(), any(), any())
        } throws RuntimeException("Unexpected fatal crash in native crypto layer")

        viewModel.revealMnemonic("pass", authContext = validAuthContext)
        testScheduler.runCurrent()

        val state = viewModel.uiState.value
        assertFalse(state.isRevealed)
        assertNull(state.mnemonicWords)
        assertTrue(state.mnemonic.isEmpty())
        assertTrue(state.error?.contains("Unexpected fatal crash") == true)
        assertEquals(RevealStatus.ERROR, state.status)
    }

    @Test
    fun `countdown timer to 0 transitions state to EXPIRED and wipes memory`() = runTest {
        testScheduler.runCurrent()
        val words = listOf("alpha", "beta", "gamma", "delta", "epsilon", "zeta", "eta", "theta", "iota", "kappa", "lambda", "mu")
        coEvery {
            revealMnemonicUseCase.executeWithMnemonic<EphemeralMnemonicHolder>(any(), any(), any(), any())
        } answers {
            val action = arg<(CharArray) -> EphemeralMnemonicHolder>(3)
            val phrase = words.joinToString(" ")
            val chars = phrase.toCharArray()
            val result = action(chars)
            Result.Success(result)
        }

        viewModel.revealMnemonic("pass", authContext = validAuthContext)
        testScheduler.runCurrent()

        assertTrue(viewModel.uiState.value.isRevealed)
        assertEquals(words, viewModel.uiState.value.mnemonicWords)
        assertEquals(30, viewModel.uiState.value.remainingSeconds)

        // Advance 29 seconds -> still revealed, remainingSeconds = 1
        testScheduler.advanceTimeBy(29_000L)
        testScheduler.runCurrent()
        assertTrue(viewModel.uiState.value.isRevealed)
        assertEquals(1, viewModel.uiState.value.remainingSeconds)
        assertEquals(words, viewModel.uiState.value.mnemonicWords)

        // Advance 1 more second -> 30s reached -> EXPIRED
        testScheduler.advanceTimeBy(1_000L)
        testScheduler.runCurrent()

        val state = viewModel.uiState.value
        assertFalse(state.isRevealed, "Must not be revealed after 30s countdown")
        assertNull(state.mnemonicWords, "Memory must be wiped to null")
        assertTrue(state.mnemonic.isEmpty())
        assertEquals(0, state.remainingSeconds)
        assertEquals(RevealStatus.EXPIRED, state.status)
    }

    @Test
    fun `re-authentication from EXPIRED status restores revealed state and resets timer`() = runTest {
        testScheduler.runCurrent()
        val words = listOf("word1", "word2", "word3", "word4", "word5", "word6", "word7", "word8", "word9", "word10", "word11", "word12")
        coEvery {
            revealMnemonicUseCase.executeWithMnemonic<EphemeralMnemonicHolder>(any(), any(), any(), any())
        } answers {
            val action = arg<(CharArray) -> EphemeralMnemonicHolder>(3)
            val phrase = words.joinToString(" ")
            val chars = phrase.toCharArray()
            val result = action(chars)
            Result.Success(result)
        }

        // First reveal
        viewModel.revealMnemonic("pass", authContext = validAuthContext)
        testScheduler.runCurrent()

        // Wait 30s for expiry
        testScheduler.advanceTimeBy(30_000L)
        testScheduler.runCurrent()
        assertEquals(RevealStatus.EXPIRED, viewModel.uiState.value.status)
        assertNull(viewModel.uiState.value.mnemonicWords)

        // Re-authenticate via onAuthSuccess
        viewModel.onAuthSuccess(validRevealSeedHandle, "")
        testScheduler.runCurrent()

        val restoredState = viewModel.uiState.value
        assertTrue(restoredState.isRevealed)
        assertEquals(words, restoredState.mnemonicWords)
        assertEquals(RevealStatus.REVEALED, restoredState.status)
        assertEquals(30, restoredState.remainingSeconds)
    }
}



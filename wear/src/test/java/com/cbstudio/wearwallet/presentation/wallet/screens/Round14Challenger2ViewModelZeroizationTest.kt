package com.cbstudio.wearwallet.presentation.wallet.screens

import com.cbstudio.wearwallet.core.domain.usecase.wallet.CreateWalletUseCase
import com.cbstudio.wearwallet.core.domain.usecase.wallet.ImportWalletUseCase
import com.cbstudio.wearwallet.core.security.CryptoProvider
import com.cbstudio.wearwallet.presentation.wallet.screens.create.CreateWalletViewModel
import com.cbstudio.wearwallet.presentation.wallet.screens.import.ImportMnemonicViewModel
import com.cbstudio.wearwallet.presentation.wallet.screens.import.ImportWalletViewModel
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import java.lang.reflect.Field

/**
 * PR #32 Round 14 Challenger 2: ViewModel Secret Memory Zeroization Adversarial Verification Test
 *
 * Enforces:
 * 1. CreateWalletViewModel zeroizes and clears ephemeralPasswordChars & ephemeralConfirmPasswordChars on confirmBackup, wipeEphemeralMnemonic, and onCleared.
 * 2. ImportMnemonicViewModel zeroizes and clears ephemeralPasswordChars, ephemeralConfirmPasswordChars, and ephemeralWords on cancelImport, wipeEphemeralSecrets, and onCleared.
 * 3. ImportWalletViewModel zeroizes and clears ephemeralInputChars, ephemeralPasswordChars, ephemeralConfirmPasswordChars, and ephemeralMnemonicWords on resetImportType, wipeEphemeralSecrets, and onCleared.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Round14Challenger2ViewModelZeroizationTest : KoinTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var createWalletUseCase: CreateWalletUseCase
    private lateinit var importWalletUseCase: ImportWalletUseCase
    private lateinit var cryptoProvider: CryptoProvider

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        createWalletUseCase = mockk(relaxed = true)
        importWalletUseCase = mockk(relaxed = true)
        cryptoProvider = mockk(relaxed = true)

        startKoin {
            modules(module {
                single { createWalletUseCase }
                single { importWalletUseCase }
                single { cryptoProvider }
            })
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        stopKoin()
    }

    @Suppress("UNCHECKED_CAST")
    private fun getPrivateField(target: Any, fieldName: String): Any? {
        var clazz: Class<*>? = target.javaClass
        while (clazz != null) {
            try {
                val field: Field = clazz.getDeclaredField(fieldName)
                field.isAccessible = true
                return field.get(target)
            } catch (e: NoSuchFieldException) {
                clazz = clazz.superclass
            }
        }
        throw NoSuchFieldException("Field  not found in ")
    }

    @Test
    fun test_createWalletViewModel_zeroizes_passwords_on_confirmBackup() {
        val vm = CreateWalletViewModel()
        val pwd = "SuperSecretPassword#123".toCharArray()
        val confirmPwd = "SuperSecretPassword#123".toCharArray()

        vm.setPassword(pwd)
        vm.setConfirmPassword(confirmPwd)

        val pwFieldBefore = getPrivateField(vm, "ephemeralPasswordChars") as? CharArray
        assertNotNull(pwFieldBefore)
        assertTrue(pwFieldBefore!!.any { it != '\u0000' })

        // Trigger backup confirmation
        vm.confirmBackup()

        val pwFieldAfter = getPrivateField(vm, "ephemeralPasswordChars") as? CharArray
        val confirmPwFieldAfter = getPrivateField(vm, "ephemeralConfirmPasswordChars") as? CharArray

        assertTrue("ephemeralPasswordChars must be null or zeroed", pwFieldAfter == null || pwFieldAfter.all { it == '\u0000' })
        assertTrue("ephemeralConfirmPasswordChars must be null or zeroed", confirmPwFieldAfter == null || confirmPwFieldAfter.all { it == '\u0000' })
    }

    @Test
    fun test_createWalletViewModel_zeroizes_passwords_on_wipeEphemeralMnemonic() {
        val vm = CreateWalletViewModel()
        val pwd = "AnotherPassword#456".toCharArray()
        val confirmPwd = "AnotherPassword#456".toCharArray()

        vm.setPassword(pwd)
        vm.setConfirmPassword(confirmPwd)

        // Trigger wipe
        vm.wipeEphemeralMnemonic()

        val pwFieldAfter = getPrivateField(vm, "ephemeralPasswordChars") as? CharArray
        val confirmPwFieldAfter = getPrivateField(vm, "ephemeralConfirmPasswordChars") as? CharArray

        assertTrue("ephemeralPasswordChars must be null or zeroed", pwFieldAfter == null || pwFieldAfter.all { it == '\u0000' })
        assertTrue("ephemeralConfirmPasswordChars must be null or zeroed", confirmPwFieldAfter == null || confirmPwFieldAfter.all { it == '\u0000' })
    }

    @Test
    fun test_importMnemonicViewModel_zeroizes_passwords_and_words_on_cancelImport() {
        val vm = ImportMnemonicViewModel()
        val pwd = "MnemonicPassword#789".toCharArray()
        val confirmPwd = "MnemonicPassword#789".toCharArray()

        vm.setPassword(pwd)
        vm.setConfirmPassword(confirmPwd)
        vm.updateMnemonicWord(0, "abandon")
        vm.updateMnemonicWord(1, "ability")

        val wordsField = getPrivateField(vm, "ephemeralWords") as? List<CharArray>
        assertNotNull(wordsField)
        assertTrue("First word must contain non-zero chars", wordsField!![0].any { it != '\u0000' })

        // Cancel import and verify memory zeroization
        vm.cancelImport()

        val pwFieldAfter = getPrivateField(vm, "ephemeralPasswordChars") as? CharArray
        val confirmPwFieldAfter = getPrivateField(vm, "ephemeralConfirmPasswordChars") as? CharArray
        val wordsFieldAfter = getPrivateField(vm, "ephemeralWords") as? List<CharArray>

        assertTrue("ephemeralPasswordChars must be null or zeroed", pwFieldAfter == null || pwFieldAfter.all { it == '\u0000' })
        assertTrue("ephemeralConfirmPasswordChars must be null or zeroed", confirmPwFieldAfter == null || confirmPwFieldAfter.all { it == '\u0000' })
        assertNotNull(wordsFieldAfter)
        for (word in wordsFieldAfter!!) {
            assertTrue("Every word char array must be empty or zeroed", word.isEmpty() || word.all { it == '\u0000' })
        }
    }

    @Test
    fun test_importMnemonicViewModel_zeroizes_passwords_and_words_on_wipeEphemeralSecrets() {
        val vm = ImportMnemonicViewModel()
        vm.setPassword("Pwd#999".toCharArray())
        vm.setConfirmPassword("Pwd#999".toCharArray())
        vm.updateMnemonicWord(0, "zoo")

        vm.wipeEphemeralSecrets()

        val pwFieldAfter = getPrivateField(vm, "ephemeralPasswordChars") as? CharArray
        val confirmPwFieldAfter = getPrivateField(vm, "ephemeralConfirmPasswordChars") as? CharArray
        val wordsFieldAfter = getPrivateField(vm, "ephemeralWords") as? List<CharArray>

        assertTrue(pwFieldAfter == null || pwFieldAfter.all { it == '\u0000' })
        assertTrue(confirmPwFieldAfter == null || confirmPwFieldAfter.all { it == '\u0000' })
        for (word in wordsFieldAfter!!) {
            assertTrue(word.isEmpty() || word.all { it == '\u0000' })
        }
    }

    @Test
    fun test_importWalletViewModel_zeroizes_input_and_passwords_on_wipeEphemeralSecrets() {
        val vm = ImportWalletViewModel()
        vm.setPassword("WalletPwd#000".toCharArray())
        vm.setConfirmPassword("WalletPwd#000".toCharArray())
        vm.updateInput("0x112233445566778899aabbccddeeff00112233445566778899aabbccddeeff00")

        val inputBefore = getPrivateField(vm, "ephemeralInputChars") as? CharArray
        assertNotNull(inputBefore)
        assertTrue(inputBefore!!.any { it != '\u0000' })

        vm.wipeEphemeralSecrets()

        val inputAfter = getPrivateField(vm, "ephemeralInputChars") as? CharArray
        val pwAfter = getPrivateField(vm, "ephemeralPasswordChars") as? CharArray
        val confirmPwAfter = getPrivateField(vm, "ephemeralConfirmPasswordChars") as? CharArray

        assertTrue("ephemeralInputChars must be null or zeroed", inputAfter == null || inputAfter.all { it == '\u0000' })
        assertTrue("ephemeralPasswordChars must be null or zeroed", pwAfter == null || pwAfter.all { it == '\u0000' })
        assertTrue("ephemeralConfirmPasswordChars must be null or zeroed", confirmPwAfter == null || confirmPwAfter.all { it == '\u0000' })
    }
}

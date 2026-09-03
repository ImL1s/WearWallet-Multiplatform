package com.cbstudio.wearwallet.core.platform

import android.content.Context
import com.cbstudio.wearwallet.core.platform.android.AndroidSecureStorage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File

/**
 * Unit and architecture tests verifying that SecureStorage and AndroidSecureKeyManager
 * are strictly fail-closed (P0-1).
 *
 * Guarantees:
 * 1. Zero KeyStore.getDefaultType() fallback in all androidMain source files.
 * 2. Zero plain context.getSharedPreferences in AndroidSecureKeyManager.
 * 3. Zero ephemeral software KeyGenerator.getInstance("AES") fallback in AndroidSecureKeyManager.
 * 4. Zero secure_wallet_prefs_test references in all androidMain source files.
 * 5. AndroidSecureKeyManager strictly throws/references typed exceptions:
 *    - AndroidKeyStoreUnavailableException
 *    - EncryptedStorageUnavailableException
 *    - KeyGenerationException
 */
class FailClosedSecureStorageTest {

    private fun findAndroidMainDir(): File {
        val candidates = listOf(
            File("src/androidMain"),
            File("coreKmp/src/androidMain"),
            File("../coreKmp/src/androidMain")
        )
        return candidates.firstOrNull { it.exists() && it.isDirectory }
            ?: throw IllegalStateException("Cannot find androidMain source directory")
    }

    @Test
    fun test_AndroidSecureStorage_throws_SecureStorageInitializationException_on_failure() {
        val mockContext = mock<Context>()
        whenever(mockContext.applicationContext).thenReturn(mockContext)
        whenever(mockContext.getSharedPreferences(any(), any())).thenThrow(
            RuntimeException("KeyStore or MasterKey initialization failure simulation")
        )

        val storage = AndroidSecureStorage(mockContext)

        val exception = assertThrows(SecureStorageInitializationException::class.java) {
            runBlocking {
                storage.saveSecure("test_key", "secret_value")
            }
        }

        assertTrue(
            "Exception message must indicate fail-closed EncryptedSharedPreferences failure",
            exception.message?.contains("Failed to initialize EncryptedSharedPreferences") == true
        )

        // Verify no plaintext write occurred on fallback preferences
        verify(mockContext, never()).getSharedPreferences("secure_wallet_prefs_test", Context.MODE_PRIVATE)
    }

    @Test
    fun test_saveSecure_fails_closed_with_zero_writes_on_initialization_error() {
        val mockContext = mock<Context>()
        var writeCount = 0
        var plaintextWriteCount = 0

        whenever(mockContext.getSharedPreferences(any(), any())).thenAnswer { invocation ->
            val name = invocation.getArgument<String>(0)
            if (name == "secure_wallet_prefs_test") {
                plaintextWriteCount++
            }
            throw IllegalStateException("EncryptedSharedPreferences MasterKey corrupted")
        }

        val storage = AndroidSecureStorage(mockContext)

        assertThrows(SecureStorageInitializationException::class.java) {
            runBlocking {
                storage.saveSecure("mnemonic", "abandon abandon abandon ...")
                writeCount++
            }
        }

        assertEquals("writeCount must be 0 on storage initialization failure", 0, writeCount)
        assertEquals("plaintextWriteCount must be 0 (no fallback storage file written)", 0, plaintextWriteCount)
    }

    // =========================================================================
    // 規則 1: 全體 androidMain 嚴禁引用 KeyStore.getDefaultType()
    // =========================================================================
    @Test
    fun test_all_androidMain_sources_contain_no_getDefaultType_reference() {
        val dir = findAndroidMainDir()
        val violatingFiles = mutableListOf<String>()

        dir.walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            val text = file.readText()
            if (text.contains("getDefaultType()")) {
                violatingFiles.add("${file.name} contains 'getDefaultType()'")
            }
        }

        assertTrue(
            "Found violating files with getDefaultType fallback: $violatingFiles",
            violatingFiles.isEmpty()
        )
    }

    // =========================================================================
    // 規則 2: AndroidSecureKeyManager 嚴禁呼叫 plain getSharedPreferences
    // =========================================================================
    @Test
    fun test_AndroidSecureKeyManager_contains_no_plain_shared_preferences() {
        val dir = findAndroidMainDir()
        val keyManagerFile = File(dir, "kotlin/com/cbstudio/wearwallet/core/security/AndroidSecureKeyManager.kt")
        assertTrue("AndroidSecureKeyManager.kt must exist", keyManagerFile.exists())

        val content = keyManagerFile.readText()

        // 嚴禁 context.getSharedPreferences 降級
        assertFalse(
            "AndroidSecureKeyManager.kt MUST NOT contain 'context.getSharedPreferences'",
            content.contains("context.getSharedPreferences")
        )

        // 嚴禁 catch (e: Throwable) 後 fallback 到普通 prefs
        assertFalse(
            "AndroidSecureKeyManager.kt MUST NOT contain fallback prefs name 'secure_wallet_keys' via plain getSharedPreferences",
            content.contains("getSharedPreferences(ENCRYPTED_PREFS_NAME")
        )
    }

    // =========================================================================
    // 規則 3: AndroidSecureKeyManager 嚴禁未錨定的軟體 AES KeyGenerator Fallback
    // =========================================================================
    @Test
    fun test_AndroidSecureKeyManager_contains_no_ephemeral_software_key_generator() {
        val dir = findAndroidMainDir()
        val keyManagerFile = File(dir, "kotlin/com/cbstudio/wearwallet/core/security/AndroidSecureKeyManager.kt")
        val content = keyManagerFile.readText()

        // 檢查是否有未指定 Keystore provider 的 KeyGenerator.getInstance("AES")
        val matches = Regex("""KeyGenerator\.getInstance\(\s*"AES"\s*\)""").findAll(content).toList()
        assertTrue(
            "AndroidSecureKeyManager.kt MUST NOT contain fallback ephemeral KeyGenerator.getInstance(\"AES\"): found ${matches.size}",
            matches.isEmpty()
        )
    }

    // =========================================================================
    // 規則 4: 全體 androidMain 原始碼嚴禁包含 secure_wallet_prefs_test
    // =========================================================================
    @Test
    fun test_all_androidMain_sources_contain_no_secure_wallet_prefs_test_reference() {
        val dir = findAndroidMainDir()
        dir.walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            val content = file.readText()
            assertFalse(
                "File ${file.name} MUST NOT contain 'secure_wallet_prefs_test'",
                content.contains("secure_wallet_prefs_test")
            )
        }
    }

    // =========================================================================
    // 規則 5: AndroidSecureKeyManager 必須明確引用型別化例外
    // =========================================================================
    @Test
    fun test_AndroidSecureKeyManager_throws_typed_fail_closed_exceptions() {
        val dir = findAndroidMainDir()
        val keyManagerFile = File(dir, "kotlin/com/cbstudio/wearwallet/core/security/AndroidSecureKeyManager.kt")
        val content = keyManagerFile.readText()

        assertTrue(
            "AndroidSecureKeyManager.kt MUST reference AndroidKeyStoreUnavailableException",
            content.contains("AndroidKeyStoreUnavailableException")
        )
        assertTrue(
            "AndroidSecureKeyManager.kt MUST reference EncryptedStorageUnavailableException",
            content.contains("EncryptedStorageUnavailableException")
        )
        assertTrue(
            "AndroidSecureKeyManager.kt MUST reference KeyGenerationException",
            content.contains("KeyGenerationException")
        )
    }
}

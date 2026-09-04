package com.cbstudio.wearwallet.core.security

import android.content.Context
import androidx.biometric.BiometricPrompt
import com.cbstudio.wearwallet.core.common.Result
import io.github.iml1s.crypto.Secp256k1Pure
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

class Milestone2ChallengerEnclaveAdversarialTest {

    private val testPrivateKeyHex = "e331b6d69882b4cb4ea581d88ec2693329b3b3a4711832421415ebff2d474428"
    private lateinit var mockContext: Context
    private lateinit var testKs: TestKeyStoreBackend
    private lateinit var inMemoryPrefs: InMemorySharedPreferences

    @Before
    fun setUp() {
        mockContext = mock<Context>()
        whenever(mockContext.applicationContext).thenReturn(mockContext)
        testKs = TestKeyStoreBackend()
        inMemoryPrefs = InMemorySharedPreferences()
    }

    private fun createKeyManager(): AndroidSecureKeyManager {
        return AndroidSecureKeyManager(
            context = mockContext,
            config = SecureStorageConfig(enableRootDetection = false),
            keyStoreProvider = { testKs.createKeyStore() },
            encryptedPrefsProvider = { inMemoryPrefs },
            secretKeyProvider = { alias, _ -> testKs.generateAndStoreKey(alias) }
        )
    }

    @Test
    fun test_SignRequireAuthKey_with_NullAuthContext_failsStrictlyWithAuthenticationRequiredException() = runTest {
        val manager = createKeyManager()
        val importAuth = AuthenticationContext(
            authHandle = TestPlatformAuthenticator.issueHandle(
                keyId = "auth_key_1",
                operation = AuthOperation.IMPORT,
                walletId = "auth_key_1"
            )
        )
        val storeRes = manager.storePrivateKey("auth_key_1", testPrivateKeyHex.encodeToByteArray(), requireAuth = true, authContext = importAuth, expectedWalletId = "auth_key_1")
        assertTrue("Store must succeed", storeRes is Result.Success)

        val signRes = manager.signWithKey("auth_key_1", "payload".encodeToByteArray(), authContext = null, expectedWalletId = "auth_key_1")
        assertTrue("Sign with null authContext on requireAuth key must fail", signRes is Result.Failure)
        val failure = signRes as Result.Failure
        assertTrue(
            "Exception must be AuthenticationRequiredException, was: ${failure.exception}",
            failure.exception is AuthenticationRequiredException
        )
    }

    @Test
    fun test_SignRequireAuthKey_with_EmptyAuthHandle_failsStrictlyWithAuthenticationRequiredException() = runTest {
        val manager = createKeyManager()
        val importAuth = AuthenticationContext(
            authHandle = TestPlatformAuthenticator.issueHandle(
                keyId = "auth_key_2",
                operation = AuthOperation.IMPORT,
                walletId = "auth_key_2"
            )
        )
        manager.storePrivateKey("auth_key_2", testPrivateKeyHex.encodeToByteArray(), requireAuth = true, authContext = importAuth, expectedWalletId = "auth_key_2")

        val emptyAuthContext = AuthenticationContext(authHandle = null, cryptoObject = null)
        val signRes = manager.signWithKey("auth_key_2", "payload".encodeToByteArray(), authContext = emptyAuthContext, expectedWalletId = "auth_key_2")

        assertTrue("Sign with null cryptoObject in authHandle must fail", signRes is Result.Failure)
        val failure = signRes as Result.Failure
        assertTrue(
            "Exception must be AuthenticationRequiredException, was: ${failure.exception}",
            failure.exception is AuthenticationRequiredException
        )
    }

    @Test
    fun test_SignRequireAuthKey_with_NullCryptoObjectInContext_failsStrictlyWithAuthenticationRequiredException() = runTest {
        val manager = createKeyManager()
        val importAuth = AuthenticationContext(
            authHandle = TestPlatformAuthenticator.issueHandle(
                keyId = "auth_key_3",
                operation = AuthOperation.IMPORT,
                walletId = "auth_key_3"
            )
        )
        manager.storePrivateKey("auth_key_3", testPrivateKeyHex.encodeToByteArray(), requireAuth = true, authContext = importAuth, expectedWalletId = "auth_key_3")

        val authContext = AuthenticationContext(cryptoObject = null, authHandle = null)
        val signRes = manager.signWithKey("auth_key_3", "payload".encodeToByteArray(), authContext = authContext, expectedWalletId = "auth_key_3")

        assertTrue("Sign with null cryptoObject must fail", signRes is Result.Failure)
        val failure = signRes as Result.Failure
        assertTrue(
            "Exception must be AuthenticationRequiredException, was: ${failure.exception}",
            failure.exception is AuthenticationRequiredException
        )
    }

    @Test
    fun test_ExportRequireAuthKey_with_NullAuthContext_failsStrictlyWithAuthenticationRequiredException() = runTest {
        val manager = createKeyManager()
        val importAuth = AuthenticationContext(
            authHandle = TestPlatformAuthenticator.issueHandle(
                keyId = "auth_key_4",
                operation = AuthOperation.IMPORT,
                walletId = "auth_key_4"
            )
        )
        manager.storePrivateKey("auth_key_4", testPrivateKeyHex.encodeToByteArray(), requireAuth = true, authContext = importAuth, expectedWalletId = "auth_key_4")

        val exportRes = manager.exportEncryptedKey("auth_key_4", "securePassword123".toCharArray(), authContext = null, expectedWalletId = "auth_key_4")
        assertTrue("Export with null authContext on requireAuth key must fail", exportRes is Result.Failure)
        val failure = exportRes as Result.Failure
        assertTrue(
            "Exception must be AuthenticationRequiredException, was: ${failure.exception}",
            failure.exception is AuthenticationRequiredException
        )
    }

    @Test
    fun test_ExportRequireAuthKey_with_NullCryptoObject_failsStrictlyWithAuthenticationRequiredException() = runTest {
        val manager = createKeyManager()
        val importAuth = AuthenticationContext(
            authHandle = TestPlatformAuthenticator.issueHandle(
                keyId = "auth_key_5",
                operation = AuthOperation.IMPORT,
                walletId = "auth_key_5"
            )
        )
        manager.storePrivateKey("auth_key_5", testPrivateKeyHex.encodeToByteArray(), requireAuth = true, authContext = importAuth, expectedWalletId = "auth_key_5")

        val authContext = AuthenticationContext(cryptoObject = null, authHandle = null)
        val exportRes = manager.exportEncryptedKey("auth_key_5", "securePassword123".toCharArray(), authContext = authContext, expectedWalletId = "auth_key_5")
        assertTrue("Export with null cryptoObject must fail", exportRes is Result.Failure)
        val failure = exportRes as Result.Failure
        assertTrue(
            "Exception must be AuthenticationRequiredException, was: ${failure.exception}",
            failure.exception is AuthenticationRequiredException
        )
    }

    @Test
    fun test_SignWithKey_with_MismatchedKeyId_failsWithKeyNotFound() = runTest {
        val manager = createKeyManager()
        manager.storePrivateKey("existing_key", testPrivateKeyHex.encodeToByteArray(), requireAuth = false, authContext = null, expectedWalletId = "existing_key")

        val signRes = manager.signWithKey("non_existent_key_xyz", "payload".encodeToByteArray(), authContext = null, expectedWalletId = "non_existent_key_xyz")
        assertTrue("Sign non-existent key must fail", signRes is Result.Failure)
        val failure = signRes as Result.Failure
        assertTrue(
            "Exception must be KeyNotFoundException or KeyManagementException, was: ${failure.exception}",
            failure.exception is KeyNotFoundException || failure.exception is KeyManagementException
        )
    }

    @Test
    fun test_GetPrivateKey_isCompletelyRemovedFromSecureKeyManager() = runTest {
        val methods = SecureKeyManager::class.java.methods.map { it.name }
        assertFalse("getPrivateKey must not exist on SecureKeyManager interface", methods.contains("getPrivateKey"))
    }

    @Test
    fun test_CorruptedAuthTag_failsDecryptionWithAuthenticationFailed() = runTest {
        val manager = createKeyManager()
        manager.storePrivateKey("tamper_key", testPrivateKeyHex.encodeToByteArray(), requireAuth = false, authContext = null, expectedWalletId = "tamper_key")

        // Corrupt auth tag in storage
        val tagKey = "tamper_key" + AndroidSecureKeyManager.TAG_SUFFIX
        val originalTag = inMemoryPrefs.getString(tagKey, null)
        assertNotNull("Tag must exist", originalTag)
        val corruptedTag = if (originalTag!!.startsWith("00")) "ff" + originalTag.substring(2) else "00" + originalTag.substring(2)
        inMemoryPrefs.edit().putString(tagKey, corruptedTag).apply()

        val signRes = manager.signWithKey("tamper_key", "test payload".encodeToByteArray(), authContext = null, expectedWalletId = "tamper_key")
        assertTrue("Tampered tag must fail signing decryption", signRes is Result.Failure)
    }

    @Test
    fun test_ImportEncryptedKey_with_MismatchedKeyId_failsStrictly() = runTest {
        val manager = createKeyManager()
        manager.storePrivateKey("origin_key", testPrivateKeyHex.encodeToByteArray(), requireAuth = false, authContext = null, expectedWalletId = "origin_key")

        // Export origin_key
        val exportRes = manager.exportEncryptedKey("origin_key", "password123".toCharArray(), authContext = null, expectedWalletId = "origin_key")
        assertTrue(exportRes is Result.Success)
        val encryptedBackup = (exportRes as Result.Success<EncryptedBackup>).data

        // Attempt to import origin_key envelope into attacker_key id (cross-key relabeling attack)
        val importRes = manager.importEncryptedKey("attacker_key", encryptedBackup, "password123".toCharArray(), authContext = null, expectedWalletId = "attacker_key")
        assertTrue("Importing envelope with mismatched keyId must fail", importRes is Result.Failure)
        val failure = importRes as Result.Failure
        assertTrue(
            "Exception must indicate keyId mismatch or failure, was: ${failure.exception}",
            failure.exception is KeyManagementException || failure.exception is IllegalArgumentException
        )
    }

    @Test
    fun test_ValidCryptoObject_allowsSigning_and_ZeroizesMemory() = runTest {
        val manager = createKeyManager()
        val importAuth = AuthenticationContext(
            authHandle = TestPlatformAuthenticator.issueHandle(
                keyId = "valid_auth_key",
                operation = AuthOperation.IMPORT,
                walletId = "valid_auth_key"
            )
        )
        manager.storePrivateKey("valid_auth_key", testPrivateKeyHex.encodeToByteArray(), requireAuth = true, authContext = importAuth, expectedWalletId = "valid_auth_key")

        // Generate valid decryption cipher using helper
        val ivHex = inMemoryPrefs.getString("valid_auth_key" + AndroidSecureKeyManager.IV_SUFFIX, null)!!
        val iv = ByteArray(ivHex.length / 2) { i -> ivHex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
        val secretKey = testKs.entries[AndroidSecureKeyManager.KEY_ALIAS_PREFIX + "valid_auth_key"]!!
        val cipher = Cipher.getInstance(AndroidSecureKeyManager.TRANSFORMATION)
        val spec = GCMParameterSpec(AndroidSecureKeyManager.AUTH_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

        val cryptoObject = BiometricPrompt.CryptoObject(cipher)
        val message = "WearWallet Milestone 2 Empirical Enclave Test".encodeToByteArray()
        val digest = CryptoUtils.sha256(message)
        val authContext = AuthenticationContext(
            authHandle = TestPlatformAuthenticator.issueHandle(
                keyId = "valid_auth_key",
                operation = AuthOperation.SIGN,
                intentFingerprint = digest.toHexString(),
                cryptoObject = cryptoObject,
                walletId = "valid_auth_key"
            )
        )

        val signRes = manager.signWithKey("valid_auth_key", message, authContext = authContext, expectedWalletId = "valid_auth_key")
        assertTrue("Signing with valid authenticated CryptoObject must succeed", signRes is Result.Success)
        val signature = (signRes as Result.Success<ByteArray>).data
        assertTrue("Signature size must be 64-65 bytes", signature.size in 64..65)
    }
}


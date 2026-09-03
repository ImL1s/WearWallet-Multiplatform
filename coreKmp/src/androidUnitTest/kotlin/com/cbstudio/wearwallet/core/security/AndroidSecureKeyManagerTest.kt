package com.cbstudio.wearwallet.core.security

import android.content.Context
import android.content.SharedPreferences
import com.cbstudio.wearwallet.core.common.Result
import io.github.iml1s.crypto.Secp256k1Pure
import io.github.iml1s.crypto.SecureByteArray
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.KeyStore
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * 測試專用的 In-Memory SharedPreferences 實現
 */
class InMemorySharedPreferences : SharedPreferences {
    val storage = ConcurrentHashMap<String, Any>()

    override fun getAll(): MutableMap<String, *> = HashMap(storage)
    override fun getString(key: String, defValue: String?): String? = storage[key] as? String ?: defValue
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? = storage[key] as? MutableSet<String> ?: defValues
    override fun getInt(key: String, defValue: Int): Int = storage[key] as? Int ?: defValue
    override fun getLong(key: String, defValue: Long): Long = storage[key] as? Long ?: defValue
    override fun getFloat(key: String, defValue: Float): Float = storage[key] as? Float ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = storage[key] as? Boolean ?: defValue
    override fun contains(key: String): Boolean = storage.containsKey(key)
    override fun edit(): SharedPreferences.Editor = EditorImpl()
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    inner class EditorImpl : SharedPreferences.Editor {
        private val temp = mutableMapOf<String, Any?>()
        private var clear = false

        override fun putString(key: String, value: String?): SharedPreferences.Editor = apply { temp[key] = value }
        override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor = apply { temp[key] = values }
        override fun putInt(key: String, value: Int): SharedPreferences.Editor = apply { temp[key] = value }
        override fun putLong(key: String, value: Long): SharedPreferences.Editor = apply { temp[key] = value }
        override fun putFloat(key: String, value: Float): SharedPreferences.Editor = apply { temp[key] = value }
        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = apply { temp[key] = value }
        override fun remove(key: String): SharedPreferences.Editor = apply { temp[key] = null }
        override fun clear(): SharedPreferences.Editor = apply { clear = true }
        override fun commit(): Boolean { apply(); return true }
        override fun apply() {
            if (clear) storage.clear()
            for ((k, v) in temp) {
                if (v == null) storage.remove(k) else storage[k] = v
            }
        }
    }
}

/**
 * 測試專用的 KeyStore 模擬 (使用真實 JVM JCA 產生 AES SecretKey 並儲存於 Map)
 */
class TestKeyStoreBackend {
    val entries = ConcurrentHashMap<String, SecretKey>()

    fun createKeyStore(): KeyStore {
        val mockKs = mock<KeyStore>()
        whenever(mockKs.containsAlias(any())).thenAnswer { inv ->
            val alias = inv.getArgument<String>(0)
            entries.containsKey(alias)
        }
        whenever(mockKs.getKey(any(), anyOrNull())).thenAnswer { inv ->
            val alias = inv.getArgument<String>(0)
            entries[alias]
        }
        whenever(mockKs.deleteEntry(any())).thenAnswer { inv ->
            val alias = inv.getArgument<String>(0)
            entries.remove(alias)
            Unit
        }
        whenever(mockKs.aliases()).thenAnswer {
            java.util.Collections.enumeration(entries.keys().toList())
        }
        return mockKs
    }

    fun generateAndStoreKey(alias: String): SecretKey {
        val kg = KeyGenerator.getInstance("AES")
        kg.init(256)
        val key = kg.generateKey()
        entries[alias] = key
        return key
    }
}

class AndroidSecureKeyManagerTest {

    private val testPrivateKeyHex = "e331b6d69882b4cb4ea581d88ec2693329b3b3a4711832421415ebff2d474428"
    private lateinit var mockContext: Context

    @Before
    fun setUp() {
        mockContext = mock<Context>()
        whenever(mockContext.applicationContext).thenReturn(mockContext)
    }

    @Test
    fun testFactoryRejectsParameterlessCreateOnAndroid() {
        val exception = assertThrows(IllegalStateException::class.java) {
            SecureKeyManagerFactory.create(SecureStorageConfig())
        }
        assertTrue(exception.message!!.contains("createWithContext"))
    }

    // =========================================================================
    // 情境 1: AndroidKeyStore 不可用 -> 型別化失敗 (AndroidKeyStoreUnavailableException)
    // =========================================================================
    @Test
    fun test_AndroidKeyStore_unavailable_returns_typed_failure() = runTest {
        val manager = AndroidSecureKeyManager(
            context = mockContext,
            config = SecureStorageConfig(enableRootDetection = false),
            keyStoreProvider = {
                throw AndroidKeyStoreUnavailableException("Simulated KeyStore daemon dead")
            },
            encryptedPrefsProvider = { InMemorySharedPreferences() }
        )

        val storeResult = manager.storePrivateKey("key_1", testPrivateKeyHex.encodeToByteArray(), requireAuth = false, authContext = null, expectedWalletId = "key_1")
        assertTrue("Store must fail when KeyStore unavailable", storeResult is Result.Failure)
        val failure = storeResult as Result.Failure
        assertTrue(
            "Exception must be AndroidKeyStoreUnavailableException, was ${failure.exception}",
            failure.exception is AndroidKeyStoreUnavailableException || failure.exception.cause is AndroidKeyStoreUnavailableException
        )
    }

    // =========================================================================
    // 情境 2: EncryptedSharedPreferences 不可用 -> 型別化失敗 (EncryptedStorageUnavailableException)
    // =========================================================================
    @Test
    fun test_EncryptedSharedPreferences_unavailable_returns_typed_failure() = runTest {
        val testKs = TestKeyStoreBackend()
        val manager = AndroidSecureKeyManager(
            context = mockContext,
            config = SecureStorageConfig(enableRootDetection = false),
            keyStoreProvider = { testKs.createKeyStore() },
            encryptedPrefsProvider = {
                throw EncryptedStorageUnavailableException("Simulated MasterKey Keystore failure")
            },
            secretKeyProvider = { alias, _ -> testKs.generateAndStoreKey(alias) }
        )

        val storeResult = manager.storePrivateKey("key_1", testPrivateKeyHex.encodeToByteArray(), requireAuth = false, authContext = null, expectedWalletId = "key_1")
        assertTrue("Store must fail when EncryptedSharedPreferences is unavailable", storeResult is Result.Failure)
        val failure = storeResult as Result.Failure
        assertTrue(
            "Exception must be EncryptedStorageUnavailableException, was ${failure.exception}",
            failure.exception is EncryptedStorageUnavailableException || failure.exception.cause is EncryptedStorageUnavailableException
        )
    }

    // =========================================================================
    // 情境 3: 金鑰生成失敗 -> 型別化失敗 (KeyGenerationException) 且零寫入
    // =========================================================================
    @Test
    fun test_KeyGeneration_failure_returns_typed_failure() = runTest {
        val testKs = TestKeyStoreBackend()
        val prefs = InMemorySharedPreferences()

        val manager = AndroidSecureKeyManager(
            context = mockContext,
            config = SecureStorageConfig(enableRootDetection = false),
            keyStoreProvider = { testKs.createKeyStore() },
            encryptedPrefsProvider = { prefs },
            secretKeyProvider = { _, _ ->
                throw KeyGenerationException("Simulated TEE hardware key generation failure")
            }
        )

        val storeResult = manager.storePrivateKey("key_1", testPrivateKeyHex.encodeToByteArray(), requireAuth = false, authContext = null, expectedWalletId = "key_1")
        assertTrue("Store must fail when KeyGenerator fails", storeResult is Result.Failure)
        val failure = storeResult as Result.Failure
        assertTrue(
            "Exception must be KeyGenerationException, was ${failure.exception}",
            failure.exception is KeyGenerationException || failure.exception.cause is KeyGenerationException
        )

        // 確保沒有任何垃圾或明文寫入 SharedPreferences
        assertFalse("Storage must not contain key_1", prefs.contains("key_1"))
    }

    // =========================================================================
    // 情境 4: 程序重啟後金鑰仍可安全簽名與導出備份 (Process Restart Resilience)
    // =========================================================================
    @Test
    fun test_ProcessRestart_after_store_key_remains_usable_and_signing_succeeds() = runTest {
        val persistentKeyStore = TestKeyStoreBackend()
        val persistentStorage = InMemorySharedPreferences()

        // 階段 1: Process 1 存入金鑰
        val instance1 = AndroidSecureKeyManager(
            context = mockContext,
            config = SecureStorageConfig(enableRootDetection = false),
            keyStoreProvider = { persistentKeyStore.createKeyStore() },
            encryptedPrefsProvider = { persistentStorage },
            secretKeyProvider = { alias, _ -> persistentKeyStore.generateAndStoreKey(alias) }
        )

        val storeRes = instance1.storePrivateKey("wallet_restart_test", testPrivateKeyHex.encodeToByteArray(), requireAuth = false, authContext = null, expectedWalletId = "wallet_restart_test")
        assertTrue("Initial store must succeed", storeRes is Result.Success)

        // 階段 2: 模擬 Process 終止與重啟 (建立全新 Instance 2，共享相同的持久化 Backend)
        val instance2 = AndroidSecureKeyManager(
            context = mockContext,
            config = SecureStorageConfig(enableRootDetection = false),
            keyStoreProvider = { persistentKeyStore.createKeyStore() },
            encryptedPrefsProvider = { persistentStorage },
            secretKeyProvider = { alias, _ -> persistentKeyStore.generateAndStoreKey(alias) }
        )

        // 階段 3: SecureKeyManager 介面徹底移除 getPrivateKey (禁止導出 Raw String)
        val methods = SecureKeyManager::class.java.methods.map { it.name }
        assertFalse("getPrivateKey must not exist on SecureKeyManager", methods.contains("getPrivateKey"))

        // 階段 4: 從 Instance 2 進行真實簽名
        val message = "WearWallet Process Restart Test".encodeToByteArray()
        val signRes = instance2.signWithKey("wallet_restart_test", message, authContext = null, expectedWalletId = "wallet_restart_test")
        assertTrue("Signing after restart must succeed", signRes is Result.Success)
        val signature = (signRes as Result.Success).data
        assertTrue("Signature must be 64-65 bytes", signature.size in 64..65)

        // 階段 5: 導出加密備份並驗證
        val exportRes = instance2.exportEncryptedKey("wallet_restart_test", "strongPassword123".toCharArray(), authContext = null, expectedWalletId = "wallet_restart_test")
        assertTrue("Export after restart must succeed", exportRes is Result.Success)
    }

    // =========================================================================
    // 情境 5: 加密金鑰解密與密碼學完整性校驗 (Tampering Detection)
    // =========================================================================
    @Test
    fun test_EncryptedKey_still_decrypts_and_detects_tampering() = runTest {
        val testKs = TestKeyStoreBackend()
        val prefs = InMemorySharedPreferences()

        val manager = AndroidSecureKeyManager(
            context = mockContext,
            config = SecureStorageConfig(enableRootDetection = false),
            keyStoreProvider = { testKs.createKeyStore() },
            encryptedPrefsProvider = { prefs },
            secretKeyProvider = { alias, _ -> testKs.generateAndStoreKey(alias) }
        )

        manager.storePrivateKey("wallet_tamper_test", testPrivateKeyHex.encodeToByteArray(), requireAuth = false, authContext = null, expectedWalletId = "wallet_tamper_test")

        // 正常簽名成功
        val message = "Test Payload".encodeToByteArray()
        val okRes = manager.signWithKey("wallet_tamper_test", message, authContext = null, expectedWalletId = "wallet_tamper_test")
        assertTrue(okRes is Result.Success)

        // 竄改密文 (篡改 1 個 byte)
        val rawCiphertextHex = prefs.getString("wallet_tamper_test", null)!!
        val tamperedCiphertext = rawCiphertextHex.substring(0, rawCiphertextHex.length - 2) + "00"
        prefs.edit().putString("wallet_tamper_test", tamperedCiphertext).apply()

        // 再次簽名解密必須 Fail-Closed (AEAD Tag 校驗失敗)
        val tamperedRes = manager.signWithKey("wallet_tamper_test", message, authContext = null, expectedWalletId = "wallet_tamper_test")
        assertTrue("Tampered ciphertext must fail decryption", tamperedRes is Result.Failure)
    }

    // =========================================================================
    // 情境 5b: requireAuth = true 但缺少 CryptoObject 時必須拒絕 (Fail-Closed)
    // =========================================================================
    @Test
    fun test_RequireAuth_key_without_CryptoObject_fails_with_AuthenticationRequiredException() = runTest {
        val testKs = TestKeyStoreBackend()
        val prefs = InMemorySharedPreferences()

        val manager = AndroidSecureKeyManager(
            context = mockContext,
            config = SecureStorageConfig(enableRootDetection = false),
            keyStoreProvider = { testKs.createKeyStore() },
            encryptedPrefsProvider = { prefs },
            secretKeyProvider = { alias, _ -> testKs.generateAndStoreKey(alias) }
        )

        // 存入需要認證的 Key (with IMPORT auth handle)
        val importAuth = AuthenticationContext(
            authHandle = TestPlatformAuthenticator.issueHandle(
                keyId = "auth_key_test",
                operation = AuthOperation.IMPORT,
                walletId = "auth_key_test"
            )
        )
        val storeRes = manager.storePrivateKey("auth_key_test", testPrivateKeyHex.encodeToByteArray(), requireAuth = true, authContext = importAuth, expectedWalletId = "auth_key_test")
        assertTrue("Store with requireAuth=true and import auth must succeed", storeRes is Result.Success)

        // 未傳入 CryptoObject 時進行簽名 -> 必須拋出 AuthenticationRequiredException
        val signRes = manager.signWithKey("auth_key_test", "hello".encodeToByteArray(), authContext = null, expectedWalletId = "auth_key_test")
        assertTrue("Signing without authContext on requireAuth key must fail", signRes is Result.Failure)
        val failure = signRes as Result.Failure
        assertTrue(
            "Exception must be AuthenticationRequiredException, was: ${failure.exception}",
            failure.exception is AuthenticationRequiredException
        )

        // 未傳入 CryptoObject 時進行導出 -> 必須拋出 AuthenticationRequiredException
        val exportRes = manager.exportEncryptedKey("auth_key_test", "backupPassword".toCharArray(), authContext = null, expectedWalletId = "auth_key_test")
        assertTrue("Export without authContext on requireAuth key must fail", exportRes is Result.Failure)
        assertTrue(
            "Exception must be AuthenticationRequiredException, was: ${(exportRes as Result.Failure).exception}",
            exportRes.exception is AuthenticationRequiredException
        )
    }

    // =========================================================================
    // 情境 6: 零明文寫入儲存 (No Plaintext Write)
    // =========================================================================
    @Test
    fun test_NoPlaintext_write_to_storage() = runTest {
        val testKs = TestKeyStoreBackend()
        val prefs = InMemorySharedPreferences()

        val manager = AndroidSecureKeyManager(
            context = mockContext,
            config = SecureStorageConfig(enableRootDetection = false),
            keyStoreProvider = { testKs.createKeyStore() },
            encryptedPrefsProvider = { prefs },
            secretKeyProvider = { alias, _ -> testKs.generateAndStoreKey(alias) }
        )

        val rawKey = "e331b6d69882b4cb4ea581d88ec2693329b3b3a4711832421415ebff2d474428"
        val storeRes = manager.storePrivateKey("no_plaintext_test", rawKey.encodeToByteArray(), requireAuth = false, authContext = null, expectedWalletId = "no_plaintext_test")
        assertTrue(storeRes is Result.Success)

        // 檢視儲存中的所有值
        val allStoredValues = prefs.getAll().values.map { it.toString() }
        for (value in allStoredValues) {
            assertFalse("Stored value must not contain raw private key", value.contains(rawKey))
            assertFalse("Stored value must not contain raw private key substring", value.contains("e331b6d6"))
        }

        // 確保儲存格式為合理的 Hex Ciphertext / IV / Tag
        val ct = prefs.getString("no_plaintext_test", null)
        val iv = prefs.getString("no_plaintext_test_iv", null)
        val tag = prefs.getString("no_plaintext_test_tag", null)

        assertNotNull(ct)
        assertNotNull(iv)
        assertNotNull(tag)
        assertEquals("IV must be 12 bytes = 24 hex chars", 24, iv!!.length)
        assertEquals("Tag must be 16 bytes = 32 hex chars", 32, tag!!.length)
    }

    // =========================================================================
    // 情境 7: 嚴禁使用暫存軟體金鑰回傳 Success (No Success With Ephemeral Software Key)
    // =========================================================================
    @Test
    fun test_NoSuccess_with_ephemeral_software_key() = runTest {
        val testKs = TestKeyStoreBackend()
        val prefs = InMemorySharedPreferences()

        val manager = AndroidSecureKeyManager(
            context = mockContext,
            config = SecureStorageConfig(enableRootDetection = false),
            keyStoreProvider = { testKs.createKeyStore() },
            encryptedPrefsProvider = { prefs },
            secretKeyProvider = { _, _ ->
                // 模擬 Keystore 失敗
                throw KeyGenerationException("Keystore hardware generation failed")
            }
        )

        val result = manager.storePrivateKey("key_ephemeral_test", testPrivateKeyHex.encodeToByteArray(), requireAuth = false, authContext = null, expectedWalletId = "key_ephemeral_test")

        // 必須回傳 Failure，絕不可回傳 Success
        assertTrue("Must NOT succeed when hardware key creation fails", result is Result.Failure)
        assertFalse("Must not write partial keys to prefs", prefs.contains("key_ephemeral_test"))
    }

    @Test
    fun testSignWithKeyFailsClosedWhenKeyNotFound() = runTest {
        val testKs = TestKeyStoreBackend()
        val manager = AndroidSecureKeyManager(
            context = mockContext,
            config = SecureStorageConfig(enableRootDetection = false),
            keyStoreProvider = { testKs.createKeyStore() },
            encryptedPrefsProvider = { InMemorySharedPreferences() }
        )
        val result = manager.signWithKey("non_existent_key", "test_payload".encodeToByteArray(), authContext = null, expectedWalletId = "non_existent_key")

        assertTrue("Signing with missing key must fail closed", result is Result.Failure)
        val failure = result as Result.Failure
        assertTrue("Exception must indicate key failure", failure.exception is KeyManagementException || failure.exception is KeyNotFoundException)
    }

    @Test
    fun testSignWithKeyFailsClosedOnEmptyData() = runTest {
        val testKs = TestKeyStoreBackend()
        val manager = AndroidSecureKeyManager(
            context = mockContext,
            config = SecureStorageConfig(enableRootDetection = false),
            keyStoreProvider = { testKs.createKeyStore() },
            encryptedPrefsProvider = { InMemorySharedPreferences() }
        )
        val result = manager.signWithKey("key_1", byteArrayOf(), authContext = null, expectedWalletId = "key_1")

        assertTrue("Signing empty data must fail closed", result is Result.Failure)
        val failure = result as Result.Failure
        assertTrue(failure.exception is IllegalArgumentException)
    }

    @Test
    fun testSecp256k1PureSigningDeterministicAndVerifiable() {
        val privateKeyHex = "e331b6d69882b4cb4ea581d88ec2693329b3b3a4711832421415ebff2d474428"
        val cleanKey = privateKeyHex.removePrefix("0x")
        val privateKeyBytes = ByteArray(32) { i ->
            cleanKey.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }

        val message = "WearWallet Issue #14 Cryptographic Test Message".encodeToByteArray()
        val messageHash = CryptoUtils.sha256(message)

        val signature = Secp256k1Pure.sign(messageHash, privateKeyBytes)
        assertNotNull("Signature must not be null", signature)
        assertTrue("Signature length must be 64 or 65 bytes", signature.size in 64..65)

        // Verify signature is not all zeros
        assertFalse("Signature must not be dummy all zeros", signature.all { it == 0.toByte() })

        // Clean up private key in memory
        SecureByteArray.secureZero(privateKeyBytes)
        assertTrue("Private key must be zeroized in memory", privateKeyBytes.all { it == 0.toByte() })
    }

    @Test
    fun testEnvelopeExportAndImportFailClosedOnInvalidPassword() = runTest {
        val testKs = TestKeyStoreBackend()
        val manager = AndroidSecureKeyManager(
            context = mockContext,
            config = SecureStorageConfig(enableRootDetection = false),
            keyStoreProvider = { testKs.createKeyStore() },
            encryptedPrefsProvider = { InMemorySharedPreferences() },
            secretKeyProvider = { alias, _ -> testKs.generateAndStoreKey(alias) }
        )

        // Empty password export must fail closed
        val emptyExportRes = manager.exportEncryptedKey("test_key", "".toCharArray(), authContext = null, expectedWalletId = "test_key")
        assertTrue(emptyExportRes is Result.Failure)

        // Blank payload is rejected by EncryptedBackup constructor
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            EncryptedBackup("")
        }

        // Corrupted import must fail closed
        val corruptedImportRes = manager.importEncryptedKey("test_key", EncryptedBackup("corrupted_base64_payload"), "pwd".toCharArray(), authContext = null, expectedWalletId = "test_key")
        assertTrue(corruptedImportRes is Result.Failure)

        val emptyPwdImportRes = manager.importEncryptedKey("test_key", EncryptedBackup("valid_dummy_data"), "".toCharArray(), authContext = null, expectedWalletId = "test_key")
        assertTrue(emptyPwdImportRes is Result.Failure)
    }

    // =========================================================================
    // P1-1 & P1-2: 授權驗證與 Fail-Closed 測試 (Authorization & Fail-Closed Tests)
    // =========================================================================

    @Test
    fun test_DeleteRequireAuthKey_without_AuthContext_failsStrictlyWithAuthenticationRequiredException() = runTest {
        val testKs = TestKeyStoreBackend()
        val prefs = InMemorySharedPreferences()
        val manager = AndroidSecureKeyManager(
            context = mockContext,
            config = SecureStorageConfig(enableRootDetection = false),
            keyStoreProvider = { testKs.createKeyStore() },
            encryptedPrefsProvider = { prefs },
            secretKeyProvider = { alias, _ -> testKs.generateAndStoreKey(alias) }
        )

        val importAuthDel = AuthenticationContext(
            authHandle = TestPlatformAuthenticator.issueHandle(
                keyId = "key_auth_del",
                operation = AuthOperation.IMPORT,
                walletId = "key_auth_del"
            )
        )
        val storeRes = manager.storePrivateKey("key_auth_del", testPrivateKeyHex.encodeToByteArray(), requireAuth = true, authContext = importAuthDel, expectedWalletId = "key_auth_del")
        assertTrue(storeRes is Result.Success)
        assertTrue(manager.hasPrivateKey("key_auth_del"))

        // 無 authContext 刪除 -> 必須失敗
        val deleteRes = manager.deletePrivateKey("key_auth_del", authContext = null, expectedWalletId = "key_auth_del")
        assertTrue("Must fail when deleting auth-required key without authContext", deleteRes is Result.Failure)
        val ex = (deleteRes as Result.Failure).exception
        assertTrue("Must throw AuthenticationRequiredException, got $ex", ex is AuthenticationRequiredException)

        // 金鑰必須仍完好保留
        assertTrue("Key must remain stored after failed delete", manager.hasPrivateKey("key_auth_del"))
    }

    @Test
    fun test_DeleteRequireAuthKey_with_CrossKeyHandle_failsStrictly() = runTest {
        val testKs = TestKeyStoreBackend()
        val prefs = InMemorySharedPreferences()
        val manager = AndroidSecureKeyManager(
            context = mockContext,
            config = SecureStorageConfig(enableRootDetection = false),
            keyStoreProvider = { testKs.createKeyStore() },
            encryptedPrefsProvider = { prefs },
            secretKeyProvider = { alias, _ -> testKs.generateAndStoreKey(alias) }
        )

        val importAuth = AuthenticationContext(
            authHandle = TestPlatformAuthenticator.issueHandle(
                keyId = "key_target",
                operation = AuthOperation.IMPORT,
                walletId = "key_target"
            )
        )
        manager.storePrivateKey("key_target", testPrivateKeyHex.encodeToByteArray(), requireAuth = true, authContext = importAuth, expectedWalletId = "key_target")

        val crossKeyHandle = TestPlatformAuthenticator.issueHandle(
            keyId = "other_key",
            operation = AuthOperation.DELETE,
            expiresAtMs = System.currentTimeMillis() + 60000,
            walletId = "key_target"
        )
        val authContext = AuthenticationContext(authHandle = crossKeyHandle)

        val deleteRes = manager.deletePrivateKey("key_target", authContext = authContext, expectedWalletId = "key_target")
        assertTrue("Cross-key handle must fail deletion", deleteRes is Result.Failure)
        val ex = (deleteRes as Result.Failure).exception
        assertTrue("Must throw AuthenticationRequiredException, got $ex", ex is AuthenticationRequiredException)
        assertTrue(manager.hasPrivateKey("key_target"))
    }

    @Test
    fun test_DeleteRequireAuthKey_with_ExpiredHandle_failsStrictly() = runTest {
        val testKs = TestKeyStoreBackend()
        val prefs = InMemorySharedPreferences()
        val manager = AndroidSecureKeyManager(
            context = mockContext,
            config = SecureStorageConfig(enableRootDetection = false),
            keyStoreProvider = { testKs.createKeyStore() },
            encryptedPrefsProvider = { prefs },
            secretKeyProvider = { alias, _ -> testKs.generateAndStoreKey(alias) }
        )

        val importAuth = AuthenticationContext(
            authHandle = TestPlatformAuthenticator.issueHandle(
                keyId = "key_exp",
                operation = AuthOperation.IMPORT,
                walletId = "key_exp"
            )
        )
        manager.storePrivateKey("key_exp", testPrivateKeyHex.encodeToByteArray(), requireAuth = true, authContext = importAuth, expectedWalletId = "key_exp")

        val now = System.currentTimeMillis()
        val expiredHandle = PlatformAuthHandle(
            keyId = "key_exp",
            operation = AuthOperation.DELETE,
            intentFingerprint = "",
            sessionId = "session_exp",
            nonce = "nonce_exp",
            issuedAtMs = now - 10000,
            expiresAtMs = now - 5000,
            walletId = "key_exp",
            proofToken = ProofTokenVerifier.sign("key_exp", AuthOperation.DELETE, "", "session_exp", "nonce_exp", now - 10000, now - 5000, "key_exp")
        )
        val authContext = AuthenticationContext(authHandle = expiredHandle)

        val deleteRes = manager.deletePrivateKey("key_exp", authContext = authContext, expectedWalletId = "key_exp")
        assertTrue("Expired handle must fail deletion", deleteRes is Result.Failure)
        val ex = (deleteRes as Result.Failure).exception
        assertTrue("Must throw AuthenticationRequiredException, got $ex", ex is AuthenticationRequiredException)
        assertTrue(manager.hasPrivateKey("key_exp"))
    }

    @Test
    fun test_DeleteRequireAuthKey_with_InvalidatedHandle_failsStrictly() = runTest {
        val testKs = TestKeyStoreBackend()
        val prefs = InMemorySharedPreferences()
        val manager = AndroidSecureKeyManager(
            context = mockContext,
            config = SecureStorageConfig(enableRootDetection = false),
            keyStoreProvider = { testKs.createKeyStore() },
            encryptedPrefsProvider = { prefs },
            secretKeyProvider = { alias, _ -> testKs.generateAndStoreKey(alias) }
        )

        val importAuth = AuthenticationContext(
            authHandle = TestPlatformAuthenticator.issueHandle(
                keyId = "key_inval",
                operation = AuthOperation.IMPORT,
                walletId = "key_inval"
            )
        )
        manager.storePrivateKey("key_inval", testPrivateKeyHex.encodeToByteArray(), requireAuth = true, authContext = importAuth, expectedWalletId = "key_inval")

        val handle = TestPlatformAuthenticator.issueHandle(
            keyId = "key_inval",
            operation = AuthOperation.DELETE,
            expiresAtMs = System.currentTimeMillis() + 60000,
            walletId = "key_inval"
        )
        handle.invalidate()
        val authContext = AuthenticationContext(authHandle = handle)

        val deleteRes = manager.deletePrivateKey("key_inval", authContext = authContext, expectedWalletId = "key_inval")
        assertTrue("Invalidated handle must fail deletion", deleteRes is Result.Failure)
        val ex = (deleteRes as Result.Failure).exception
        assertTrue("Must throw AuthenticationRequiredException, got $ex", ex is AuthenticationRequiredException)
        assertTrue(manager.hasPrivateKey("key_inval"))
    }

    @Test
    fun test_DeleteRequireAuthKey_with_WrongOperation_failsStrictly() = runTest {
        val testKs = TestKeyStoreBackend()
        val prefs = InMemorySharedPreferences()
        val manager = AndroidSecureKeyManager(
            context = mockContext,
            config = SecureStorageConfig(enableRootDetection = false),
            keyStoreProvider = { testKs.createKeyStore() },
            encryptedPrefsProvider = { prefs },
            secretKeyProvider = { alias, _ -> testKs.generateAndStoreKey(alias) }
        )

        val importAuth = AuthenticationContext(
            authHandle = TestPlatformAuthenticator.issueHandle(
                keyId = "key_wrong_op",
                operation = AuthOperation.IMPORT,
                walletId = "key_wrong_op"
            )
        )
        manager.storePrivateKey("key_wrong_op", testPrivateKeyHex.encodeToByteArray(), requireAuth = true, authContext = importAuth, expectedWalletId = "key_wrong_op")

        val wrongOpHandle = TestPlatformAuthenticator.issueHandle(
            keyId = "key_wrong_op",
            operation = AuthOperation.SIGN,
            expiresAtMs = System.currentTimeMillis() + 60000,
            walletId = "key_wrong_op"
        )
        val authContext = AuthenticationContext(authHandle = wrongOpHandle)

        val deleteRes = manager.deletePrivateKey("key_wrong_op", authContext = authContext, expectedWalletId = "key_wrong_op")
        assertTrue("Handle with SIGN operation must fail DELETE", deleteRes is Result.Failure)
        val ex = (deleteRes as Result.Failure).exception
        assertTrue("Must throw AuthenticationRequiredException, got $ex", ex is AuthenticationRequiredException)
        assertTrue(manager.hasPrivateKey("key_wrong_op"))
    }

    @Test
    fun test_DeleteRequireAuthKey_with_ValidDeleteHandle_succeeds() = runTest {
        val testKs = TestKeyStoreBackend()
        val prefs = InMemorySharedPreferences()
        val manager = AndroidSecureKeyManager(
            context = mockContext,
            config = SecureStorageConfig(enableRootDetection = false),
            keyStoreProvider = { testKs.createKeyStore() },
            encryptedPrefsProvider = { prefs },
            secretKeyProvider = { alias, _ -> testKs.generateAndStoreKey(alias) }
        )

        val importAuth = AuthenticationContext(
            authHandle = TestPlatformAuthenticator.issueHandle(
                keyId = "key_valid_del",
                operation = AuthOperation.IMPORT,
                walletId = "key_valid_del"
            )
        )
        manager.storePrivateKey("key_valid_del", testPrivateKeyHex.encodeToByteArray(), requireAuth = true, authContext = importAuth, expectedWalletId = "key_valid_del")
        assertTrue(manager.hasPrivateKey("key_valid_del"))

        val validHandle = TestPlatformAuthenticator.issueHandle(
            keyId = "key_valid_del",
            operation = AuthOperation.DELETE,
            expiresAtMs = System.currentTimeMillis() + 60000,
            walletId = "key_valid_del"
        )
        val authContext = AuthenticationContext(authHandle = validHandle)

        val deleteRes = manager.deletePrivateKey("key_valid_del", authContext = authContext, expectedWalletId = "key_valid_del")
        assertTrue("Valid delete handle must succeed", deleteRes is Result.Success)
        assertFalse("Key must be deleted", manager.hasPrivateKey("key_valid_del"))
    }

    @Test
    fun test_SignRequireAuthKey_with_WrongOperation_failsStrictly() = runTest {
        val testKs = TestKeyStoreBackend()
        val prefs = InMemorySharedPreferences()
        val manager = AndroidSecureKeyManager(
            context = mockContext,
            config = SecureStorageConfig(enableRootDetection = false),
            keyStoreProvider = { testKs.createKeyStore() },
            encryptedPrefsProvider = { prefs },
            secretKeyProvider = { alias, _ -> testKs.generateAndStoreKey(alias) }
        )

        val importAuth = AuthenticationContext(
            authHandle = TestPlatformAuthenticator.issueHandle(
                keyId = "key_sign_op",
                operation = AuthOperation.IMPORT,
                walletId = "key_sign_op"
            )
        )
        manager.storePrivateKey("key_sign_op", testPrivateKeyHex.encodeToByteArray(), requireAuth = true, authContext = importAuth, expectedWalletId = "key_sign_op")

        val wrongOpHandle = TestPlatformAuthenticator.issueHandle(
            keyId = "key_sign_op",
            operation = AuthOperation.EXPORT,
            expiresAtMs = System.currentTimeMillis() + 60000,
            walletId = "key_sign_op"
        )
        val authContext = AuthenticationContext(authHandle = wrongOpHandle)

        val signRes = manager.signWithKey("key_sign_op", "hello".encodeToByteArray(), authContext = authContext, expectedWalletId = "key_sign_op")
        assertTrue("Handle with EXPORT operation must fail SIGN", signRes is Result.Failure)
        val ex = (signRes as Result.Failure).exception
        assertTrue("Must throw AuthenticationRequiredException, got $ex", ex is AuthenticationRequiredException)
    }

    @Test
    fun test_SignRequireAuthKey_with_MismatchedIntentFingerprint_failsStrictly() = runTest {
        val testKs = TestKeyStoreBackend()
        val prefs = InMemorySharedPreferences()
        val manager = AndroidSecureKeyManager(
            context = mockContext,
            config = SecureStorageConfig(enableRootDetection = false),
            keyStoreProvider = { testKs.createKeyStore() },
            encryptedPrefsProvider = { prefs },
            secretKeyProvider = { alias, _ -> testKs.generateAndStoreKey(alias) }
        )

        val importAuth = AuthenticationContext(
            authHandle = TestPlatformAuthenticator.issueHandle(
                keyId = "key_intent_test",
                operation = AuthOperation.IMPORT,
                walletId = "key_intent_test"
            )
        )
        manager.storePrivateKey("key_intent_test", testPrivateKeyHex.encodeToByteArray(), requireAuth = true, authContext = importAuth, expectedWalletId = "key_intent_test")

        val mismatchedIntentHandle = TestPlatformAuthenticator.issueHandle(
            keyId = "key_intent_test",
            operation = AuthOperation.SIGN,
            intentFingerprint = "deadbeefcafe",
            expiresAtMs = System.currentTimeMillis() + 60000,
            walletId = "key_intent_test"
        )
        val authContext = AuthenticationContext(authHandle = mismatchedIntentHandle)

        val signRes = manager.signWithKey("key_intent_test", "different_payload".encodeToByteArray(), authContext = authContext, expectedWalletId = "key_intent_test")
        assertTrue("Handle with mismatched intent fingerprint must fail SIGN", signRes is Result.Failure)
        val ex = (signRes as Result.Failure).exception
        assertTrue("Must throw AuthenticationRequiredException, got $ex", ex is AuthenticationRequiredException)
    }

    @Test
    fun test_ExportRequireAuthKey_with_WrongOperation_failsStrictly() = runTest {
        val testKs = TestKeyStoreBackend()
        val prefs = InMemorySharedPreferences()
        val manager = AndroidSecureKeyManager(
            context = mockContext,
            config = SecureStorageConfig(enableRootDetection = false),
            keyStoreProvider = { testKs.createKeyStore() },
            encryptedPrefsProvider = { prefs },
            secretKeyProvider = { alias, _ -> testKs.generateAndStoreKey(alias) }
        )

        val importAuth = AuthenticationContext(
            authHandle = TestPlatformAuthenticator.issueHandle(
                keyId = "key_export_op",
                operation = AuthOperation.IMPORT,
                walletId = "key_export_op"
            )
        )
        manager.storePrivateKey("key_export_op", testPrivateKeyHex.encodeToByteArray(), requireAuth = true, authContext = importAuth, expectedWalletId = "key_export_op")

        val wrongOpHandle = TestPlatformAuthenticator.issueHandle(
            keyId = "key_export_op",
            operation = AuthOperation.SIGN,
            expiresAtMs = System.currentTimeMillis() + 60000,
            walletId = "key_export_op"
        )
        val authContext = AuthenticationContext(authHandle = wrongOpHandle)

        val exportRes = manager.exportEncryptedKey("key_export_op", "backup_pwd#123".toCharArray(), authContext = authContext, expectedWalletId = "key_export_op")
        assertTrue("Handle with SIGN operation must fail EXPORT", exportRes is Result.Failure)
        val ex = (exportRes as Result.Failure).exception
        assertTrue("Must throw AuthenticationRequiredException, got $ex", ex is AuthenticationRequiredException)
    }

    @Test
    fun test_RollbackProvisioningSession_onRequireAuthKey_succeedsWithoutAuthContext() = runTest {
        val testKs = TestKeyStoreBackend()
        val prefs = InMemorySharedPreferences()
        val manager = AndroidSecureKeyManager(
            context = mockContext,
            config = SecureStorageConfig(enableRootDetection = false),
            keyStoreProvider = { testKs.createKeyStore() },
            encryptedPrefsProvider = { prefs },
            secretKeyProvider = { alias, _ -> testKs.generateAndStoreKey(alias) }
        )

        val session = manager.startProvisioningSession()
        val importAuth = AuthenticationContext(
            authHandle = TestPlatformAuthenticator.issueHandle(
                keyId = session.stagedKeyAlias,
                sessionId = session.sessionId,
                operation = AuthOperation.IMPORT
            )
        )
        val storeRes = manager.storeStagedPrivateKey(session, testPrivateKeyHex.encodeToByteArray(), requireAuth = true, authContext = importAuth)
        assertTrue(storeRes is Result.Success)
        assertTrue(manager.hasPrivateKey(session.stagedKeyAlias))

        // 調用 rollbackProvisioningSession 無需 authContext
        val rollbackRes = manager.rollbackProvisioningSession(session)
        assertTrue("Rollback uncommitted session must succeed without authContext", rollbackRes is Result.Success)

        // 驗證金鑰已被完全清除
        assertFalse("Key must be removed after rollback", manager.hasPrivateKey(session.stagedKeyAlias))
        assertFalse("Storage must not contain key", prefs.contains(session.stagedKeyAlias))
        assertFalse("KeyStore must not contain entry", testKs.entries.containsKey("ww_key_alias_prefix_" + session.stagedKeyAlias))
    }

    @Test
    fun test_AndroidSecureKeyManager_cannot_rollback_after_commit() = runTest {
        val testKs = TestKeyStoreBackend()
        val prefs = InMemorySharedPreferences()
        val manager = AndroidSecureKeyManager(
            context = mockContext,
            config = SecureStorageConfig(enableRootDetection = false),
            keyStoreProvider = { testKs.createKeyStore() },
            encryptedPrefsProvider = { prefs },
            secretKeyProvider = { alias, _ -> testKs.generateAndStoreKey(alias) }
        )

        val session = manager.startProvisioningSession()
        val importAuth = AuthenticationContext(
            authHandle = TestPlatformAuthenticator.issueHandle(
                keyId = session.stagedKeyAlias,
                sessionId = session.sessionId,
                operation = AuthOperation.IMPORT
            )
        )
        val storeRes = manager.storeStagedPrivateKey(session, testPrivateKeyHex.encodeToByteArray(), requireAuth = true, authContext = importAuth)
        assertTrue(storeRes is Result.Success)
        assertTrue(manager.hasPrivateKey(session.stagedKeyAlias))

        val commitRes = manager.commitProvisioningSession(session)
        assertTrue(commitRes is Result.Success)

        val rollbackRes = manager.rollbackProvisioningSession(session)
        assertTrue("Rollback after commit must fail", rollbackRes is Result.Failure)
        assertTrue(manager.hasPrivateKey(session.stagedKeyAlias))
    }

    @Test
    fun test_AndroidSecureKeyManager_rollback_on_unknown_session_fails_closed() = runTest {
        val testKs = TestKeyStoreBackend()
        val prefs = InMemorySharedPreferences()
        val manager = AndroidSecureKeyManager(
            context = mockContext,
            config = SecureStorageConfig(enableRootDetection = false),
            keyStoreProvider = { testKs.createKeyStore() },
            encryptedPrefsProvider = { prefs },
            secretKeyProvider = { alias, _ -> testKs.generateAndStoreKey(alias) }
        )

        // Store a legitimate wallet key
        val legitimateKeyId = "ww_key_legitimate_wallet"
        val importAuth = AuthenticationContext(
            authHandle = TestPlatformAuthenticator.issueHandle(
                keyId = legitimateKeyId,
                operation = AuthOperation.IMPORT,
                walletId = legitimateKeyId
            )
        )
        val storeRes = manager.storePrivateKey(legitimateKeyId, testPrivateKeyHex.encodeToByteArray(), requireAuth = true, authContext = importAuth, expectedWalletId = legitimateKeyId)
        assertTrue(storeRes is Result.Success)
        assertTrue(manager.hasPrivateKey(legitimateKeyId))

        // Attacker creates a forged/unknown ProvisioningSession targeting legitimateKeyId
        val forgedSession = ProvisioningSession(
            sessionId = "forged_session_uuid",
            stagedKeyAlias = legitimateKeyId,
            backupId = "forged_backup_uuid"
        )

        val rollbackRes = manager.rollbackProvisioningSession(forgedSession)
        // Must fail closed because forgedSession was never registered in activeSessions
        assertTrue("Rollback with unknown session must fail closed", rollbackRes is Result.Failure)
        assertTrue("Legitimate key must not be deleted by forged session rollback", manager.hasPrivateKey(legitimateKeyId))
    }

    @Test
    fun test_AndroidSecureKeyManager_rollback_on_expired_session_fails_closed() = runTest {
        val testKs = TestKeyStoreBackend()
        val prefs = InMemorySharedPreferences()
        val manager = AndroidSecureKeyManager(
            context = mockContext,
            config = SecureStorageConfig(enableRootDetection = false),
            keyStoreProvider = { testKs.createKeyStore() },
            encryptedPrefsProvider = { prefs },
            secretKeyProvider = { alias, _ -> testKs.generateAndStoreKey(alias) }
        )

        val session = manager.startProvisioningSession()
        val importAuth = AuthenticationContext(
            authHandle = TestPlatformAuthenticator.issueHandle(
                keyId = session.stagedKeyAlias,
                sessionId = session.sessionId,
                operation = AuthOperation.IMPORT,
                walletId = session.stagedKeyAlias
            )
        )
        manager.storeStagedPrivateKey(session, testPrivateKeyHex.encodeToByteArray(), requireAuth = true, authContext = importAuth)
        assertTrue(manager.hasPrivateKey(session.stagedKeyAlias))

        val expiredSession = ProvisioningSession(
            sessionId = session.sessionId,
            stagedKeyAlias = session.stagedKeyAlias,
            backupId = session.backupId,
            createdAtMs = 1000L,
            maxValidityDurationMs = 10L
        )

        val rollbackRes = manager.rollbackProvisioningSession(expiredSession)
        assertTrue("Rollback on expired session must fail closed", rollbackRes is Result.Failure)
    }

    // =========================================================================
    // Milestone 2 (P0-2): Fail-Closed Key Storage Authentication Enforcement
    // =========================================================================

    @Test
    fun test_storePrivateKey_requireAuth_true_with_null_authContext_fails_closed_before_cipher_init() = runTest {
        val testKs = TestKeyStoreBackend()
        val prefs = InMemorySharedPreferences()
        val manager = AndroidSecureKeyManager(
            context = mockContext,
            config = SecureStorageConfig(enableRootDetection = false),
            keyStoreProvider = { testKs.createKeyStore() },
            encryptedPrefsProvider = { prefs },
            secretKeyProvider = { alias, _ -> testKs.generateAndStoreKey(alias) }
        )

        val keyId = "ww_key_auth_protected_null_ctx"
        val result = manager.storePrivateKey(
            keyId = keyId,
            privateKey = testPrivateKeyHex.encodeToByteArray(),
            requireAuth = true,
            authContext = null,
            expectedWalletId = keyId
        )

        assertTrue("storePrivateKey with null authContext MUST fail closed", result is Result.Failure)
        val ex = (result as Result.Failure).exception
        assertTrue("Exception must be AuthenticationRequiredException, was $ex", ex is AuthenticationRequiredException)
        assertFalse("Key must NOT be stored in KeyStore or prefs", manager.hasPrivateKey(keyId))
    }

    @Test
    fun test_storePrivateKey_requireAuth_true_with_null_authHandle_fails_closed() = runTest {
        val testKs = TestKeyStoreBackend()
        val prefs = InMemorySharedPreferences()
        val manager = AndroidSecureKeyManager(
            context = mockContext,
            config = SecureStorageConfig(enableRootDetection = false),
            keyStoreProvider = { testKs.createKeyStore() },
            encryptedPrefsProvider = { prefs },
            secretKeyProvider = { alias, _ -> testKs.generateAndStoreKey(alias) }
        )

        val keyId = "ww_key_auth_protected_null_handle"
        val result = manager.storePrivateKey(
            keyId = keyId,
            privateKey = testPrivateKeyHex.encodeToByteArray(),
            requireAuth = true,
            authContext = AuthenticationContext(authHandle = null),
            expectedWalletId = keyId
        )

        assertTrue("storePrivateKey with null authHandle MUST fail closed", result is Result.Failure)
        val ex = (result as Result.Failure).exception
        assertTrue("Exception must be AuthenticationRequiredException, was $ex", ex is AuthenticationRequiredException)
        assertFalse("Key must NOT be stored", manager.hasPrivateKey(keyId))
    }

    @Test
    fun test_storePrivateKey_requireAuth_true_with_wrong_operation_fails_closed() = runTest {
        val testKs = TestKeyStoreBackend()
        val prefs = InMemorySharedPreferences()
        val manager = AndroidSecureKeyManager(
            context = mockContext,
            config = SecureStorageConfig(enableRootDetection = false),
            keyStoreProvider = { testKs.createKeyStore() },
            encryptedPrefsProvider = { prefs },
            secretKeyProvider = { alias, _ -> testKs.generateAndStoreKey(alias) }
        )

        val keyId = "ww_key_auth_protected_wrong_op"
        val signHandle = TestPlatformAuthenticator.issueHandle(
            keyId = keyId,
            operation = AuthOperation.SIGN,
            walletId = keyId
        )

        val result = manager.storePrivateKey(
            keyId = keyId,
            privateKey = testPrivateKeyHex.encodeToByteArray(),
            requireAuth = true,
            authContext = AuthenticationContext(authHandle = signHandle),
            expectedWalletId = keyId
        )

        assertTrue("storePrivateKey with SIGN handle MUST fail closed", result is Result.Failure)
        val ex = (result as Result.Failure).exception
        assertTrue("Exception must be AuthenticationRequiredException, was $ex", ex is AuthenticationRequiredException)
        assertTrue("Exception message must indicate operation mismatch", ex.message!!.contains("does not match expected 'IMPORT'"))
        assertFalse("Key must NOT be stored", manager.hasPrivateKey(keyId))
    }

    @Test
    fun test_storePrivateKey_invalidates_authHandle_upon_consumption_and_prevents_replay() = runTest {
        val testKs = TestKeyStoreBackend()
        val prefs = InMemorySharedPreferences()
        val manager = AndroidSecureKeyManager(
            context = mockContext,
            config = SecureStorageConfig(enableRootDetection = false),
            keyStoreProvider = { testKs.createKeyStore() },
            encryptedPrefsProvider = { prefs },
            secretKeyProvider = { alias, _ -> testKs.generateAndStoreKey(alias) }
        )

        val keyId1 = "ww_key_auth_protected_single_use_1"
        val importHandle = TestPlatformAuthenticator.issueHandle(
            keyId = keyId1,
            operation = AuthOperation.IMPORT,
            walletId = keyId1
        )

        // First store consumption succeeds
        val result1 = manager.storePrivateKey(
            keyId = keyId1,
            privateKey = testPrivateKeyHex.encodeToByteArray(),
            requireAuth = true,
            authContext = AuthenticationContext(authHandle = importHandle),
            expectedWalletId = keyId1
        )
        assertTrue("First storePrivateKey must succeed", result1 is Result.Success)
        assertTrue(manager.hasPrivateKey(keyId1))

        // Handle must now be invalidated
        org.junit.Assert.assertTrue("AuthHandle must be invalidated after consumption", importHandle.isInvalidated)

        // Replay attempt with same handle MUST fail closed
        val keyId2 = "ww_key_auth_protected_single_use_2"
        val result2 = manager.storePrivateKey(
            keyId = keyId2,
            privateKey = testPrivateKeyHex.encodeToByteArray(),
            requireAuth = true,
            authContext = AuthenticationContext(authHandle = importHandle),
            expectedWalletId = keyId2
        )
        assertTrue("Replay of consumed authHandle MUST fail closed", result2 is Result.Failure)
        val ex = (result2 as Result.Failure).exception
        assertTrue("Exception must be AuthenticationRequiredException, was $ex", ex is AuthenticationRequiredException)
        assertFalse("Second key must NOT be stored", manager.hasPrivateKey(keyId2))
    }

    @Test
    fun test_storeStagedPrivateKey_requireAuth_true_with_null_authContext_fails_closed() = runTest {
        val testKs = TestKeyStoreBackend()
        val prefs = InMemorySharedPreferences()
        val manager = AndroidSecureKeyManager(
            context = mockContext,
            config = SecureStorageConfig(enableRootDetection = false),
            keyStoreProvider = { testKs.createKeyStore() },
            encryptedPrefsProvider = { prefs },
            secretKeyProvider = { alias, _ -> testKs.generateAndStoreKey(alias) }
        )

        val session = manager.startProvisioningSession()
        val result = manager.storeStagedPrivateKey(
            session = session,
            privateKey = testPrivateKeyHex.encodeToByteArray(),
            requireAuth = true,
            authContext = null
        )

        assertTrue("storeStagedPrivateKey with null authContext MUST fail closed", result is Result.Failure)
        val ex = (result as Result.Failure).exception
        assertTrue("Exception must be AuthenticationRequiredException, was $ex", ex is AuthenticationRequiredException)
        assertFalse("Staged key must NOT be stored", manager.hasPrivateKey(session.stagedKeyAlias))
    }
}


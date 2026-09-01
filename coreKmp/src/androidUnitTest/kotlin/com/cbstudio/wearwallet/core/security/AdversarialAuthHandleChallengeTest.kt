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
import java.security.KeyStore

/**
 * Challenger M2-2: Adversarial Stress Test Suite for PlatformAuthHandle & AndroidSecureKeyManager
 *
 * Challenge Objectives:
 * 1. Attempt cross-key auth replay: use Handle for Key A on Key B -> must fail.
 * 2. Attempt cross-operation auth replay: use Handle with AuthOperation.EXPORT on signWithKey -> must fail.
 * 3. Attempt intent tampering: use Handle with digest X on digest Y -> must fail.
 * 4. Attempt expired / invalidated handle reuse -> must fail.
 * 5. Attempt delete without authContext -> must fail.
 */
class AdversarialAuthHandleChallengeTest {

    private val testPrivateKeyHexA = "e331b6d69882b4cb4ea581d88ec2693329b3b3a4711832421415ebff2d474428"
    private val testPrivateKeyHexB = "4c0883a69102937d6231471b5dbb6204db7e716b78ac387728b80b7b1340a69f"
    private lateinit var mockContext: Context

    @Before
    fun setUp() {
        AuthHandleRegistry.clearForTesting()
        mockContext = mock<Context>()
        whenever(mockContext.applicationContext).thenReturn(mockContext)
    }

    @org.junit.After
    fun tearDown() {
        AuthHandleRegistry.clearForTesting()
    }

    private fun createTestEnvironment(): Triple<AndroidSecureKeyManager, TestKeyStoreBackend, InMemorySharedPreferences> {
        val testKs = TestKeyStoreBackend()
        val prefs = InMemorySharedPreferences()
        val manager = AndroidSecureKeyManager(
            context = mockContext,
            config = SecureStorageConfig(enableRootDetection = false),
            keyStoreProvider = { testKs.createKeyStore() },
            encryptedPrefsProvider = { prefs },
            secretKeyProvider = { alias, _ -> testKs.generateAndStoreKey(alias) }
        )
        return Triple(manager, testKs, prefs)
    }

    // =========================================================================
    // 1. Cross-Key Auth Replay Attacks
    // =========================================================================

    @Test
    fun challenge1_cross_key_auth_replay_sign_must_fail() = runTest {
        val (manager, _, _) = createTestEnvironment()

        val authA = AuthenticationContext(authHandle = TestPlatformAuthenticator.issueHandle("key_A", AuthOperation.IMPORT, walletId = "key_A"))
        val authB = AuthenticationContext(authHandle = TestPlatformAuthenticator.issueHandle("key_B", AuthOperation.IMPORT, walletId = "key_B"))
        // Setup Key A and Key B
        manager.storePrivateKey("key_A", testPrivateKeyHexA.encodeToByteArray(), requireAuth = true, authContext = authA, expectedWalletId = "key_A")
        manager.storePrivateKey("key_B", testPrivateKeyHexB.encodeToByteArray(), requireAuth = true, authContext = authB, expectedWalletId = "key_B")

        val cryptoObjectA = manager.createCryptoObjectForDecryption("key_A")
        assertNotNull("CryptoObject for key_A must be created", cryptoObjectA)

        val txData = "Transfer 1 ETH to Bob".encodeToByteArray()
        val txDigest = CryptoUtils.sha256(txData).toHexString()

        // Handle minted specifically for Key A
        val handleA = TestPlatformAuthenticator.issueHandle(
            keyId = "key_A",
            operation = AuthOperation.SIGN,
            intentFingerprint = txDigest,
            expiresAtMs = System.currentTimeMillis() + 60000,
            cryptoObject = cryptoObjectA,
            walletId = "key_A"
        )
        val authContextA = AuthenticationContext(authHandle = handleA)

        // Attempt to replay Handle A to sign with Key B
        val signResult = manager.signWithKey("key_B", txData, authContext = authContextA, expectedWalletId = "key_B")

        assertTrue("Cross-key sign replay MUST fail", signResult is Result.Failure)
        val ex = (signResult as Result.Failure).exception
        assertTrue("Must throw AuthenticationRequiredException, got $ex", ex is AuthenticationRequiredException)
        assertTrue("Message must indicate cross-key rejection", ex.message!!.contains("Cross-key handle rejected"))
    }

    @Test
    fun challenge1_cross_key_auth_replay_delete_must_fail() = runTest {
        val (manager, _, _) = createTestEnvironment()

        val authA = AuthenticationContext(authHandle = TestPlatformAuthenticator.issueHandle("key_A", AuthOperation.IMPORT, walletId = "key_A"))
        val authB = AuthenticationContext(authHandle = TestPlatformAuthenticator.issueHandle("key_B", AuthOperation.IMPORT, walletId = "key_B"))
        manager.storePrivateKey("key_A", testPrivateKeyHexA.encodeToByteArray(), requireAuth = true, authContext = authA, expectedWalletId = "key_A")
        manager.storePrivateKey("key_B", testPrivateKeyHexB.encodeToByteArray(), requireAuth = true, authContext = authB, expectedWalletId = "key_B")

        val handleA = TestPlatformAuthenticator.issueHandle(
            keyId = "key_A",
            operation = AuthOperation.DELETE,
            expiresAtMs = System.currentTimeMillis() + 60000,
            walletId = "key_A"
        )
        val authContextA = AuthenticationContext(authHandle = handleA)

        // Attempt to replay Handle A to delete Key B
        val deleteResult = manager.deletePrivateKey("key_B", authContext = authContextA, expectedWalletId = "key_B")

        assertTrue("Cross-key delete replay MUST fail", deleteResult is Result.Failure)
        val ex = (deleteResult as Result.Failure).exception
        assertTrue("Must throw AuthenticationRequiredException, got $ex", ex is AuthenticationRequiredException)
        assertTrue("Key B must NOT be deleted", manager.hasPrivateKey("key_B"))
        assertTrue("Key A must NOT be deleted", manager.hasPrivateKey("key_A"))
    }

    @Test
    fun challenge1_cross_key_auth_replay_export_must_fail() = runTest {
        val (manager, _, _) = createTestEnvironment()

        val authA = AuthenticationContext(authHandle = TestPlatformAuthenticator.issueHandle("key_A", AuthOperation.IMPORT, walletId = "key_A"))
        val authB = AuthenticationContext(authHandle = TestPlatformAuthenticator.issueHandle("key_B", AuthOperation.IMPORT, walletId = "key_B"))
        manager.storePrivateKey("key_A", testPrivateKeyHexA.encodeToByteArray(), requireAuth = true, authContext = authA, expectedWalletId = "key_A")
        manager.storePrivateKey("key_B", testPrivateKeyHexB.encodeToByteArray(), requireAuth = true, authContext = authB, expectedWalletId = "key_B")

        val cryptoObjectA = manager.createCryptoObjectForDecryption("key_A")
        assertNotNull(cryptoObjectA)

        val handleA = TestPlatformAuthenticator.issueHandle(
            keyId = "key_A",
            operation = AuthOperation.EXPORT,
            expiresAtMs = System.currentTimeMillis() + 60000,
            cryptoObject = cryptoObjectA,
            walletId = "key_A"
        )
        val authContextA = AuthenticationContext(authHandle = handleA)

        // Attempt to replay Handle A to export Key B
        val exportResult = manager.exportEncryptedKey("key_B", "StrongBackupPassword#123".toCharArray(), authContext = authContextA, expectedWalletId = "key_B")

        assertTrue("Cross-key export replay MUST fail", exportResult is Result.Failure)
        val ex = (exportResult as Result.Failure).exception
        assertTrue("Must throw AuthenticationRequiredException, got $ex", ex is AuthenticationRequiredException)
        assertTrue("Message must indicate cross-key rejection", ex.message!!.contains("Cross-key handle rejected"))
    }

    // =========================================================================
    // 2. Cross-Operation Auth Replay Attacks
    // =========================================================================

    @Test
    fun challenge2_cross_operation_replay_export_handle_on_sign_must_fail() = runTest {
        val (manager, _, _) = createTestEnvironment()
        val auth = AuthenticationContext(authHandle = TestPlatformAuthenticator.issueHandle("key_test", AuthOperation.IMPORT, walletId = "key_test"))
        manager.storePrivateKey("key_test", testPrivateKeyHexA.encodeToByteArray(), requireAuth = true, authContext = auth, expectedWalletId = "key_test")

        val cryptoObject = manager.createCryptoObjectForDecryption("key_test")
        assertNotNull(cryptoObject)

        val txData = "Transfer 100 USDC".encodeToByteArray()
        val txDigest = CryptoUtils.sha256(txData).toHexString()

        // Handle minted for EXPORT operation, but attempted on signWithKey
        val exportHandle = TestPlatformAuthenticator.issueHandle(
            keyId = "key_test",
            operation = AuthOperation.EXPORT,
            intentFingerprint = txDigest,
            expiresAtMs = System.currentTimeMillis() + 60000,
            cryptoObject = cryptoObject,
            walletId = "key_test"
        )
        val authContext = AuthenticationContext(authHandle = exportHandle)

        val signResult = manager.signWithKey("key_test", txData, authContext = authContext, expectedWalletId = "key_test")

        assertTrue("EXPORT handle on signWithKey MUST fail", signResult is Result.Failure)
        val ex = (signResult as Result.Failure).exception
        assertTrue("Must throw AuthenticationRequiredException, got $ex", ex is AuthenticationRequiredException)
        assertTrue("Message must indicate operation mismatch", ex.message!!.contains("does not match expected 'SIGN'"))
    }

    @Test
    fun challenge2_cross_operation_replay_sign_handle_on_export_must_fail() = runTest {
        val (manager, _, _) = createTestEnvironment()
        val auth = AuthenticationContext(authHandle = TestPlatformAuthenticator.issueHandle("key_test", AuthOperation.IMPORT, walletId = "key_test"))
        manager.storePrivateKey("key_test", testPrivateKeyHexA.encodeToByteArray(), requireAuth = true, authContext = auth, expectedWalletId = "key_test")

        val cryptoObject = manager.createCryptoObjectForDecryption("key_test")
        assertNotNull(cryptoObject)

        // Handle minted for SIGN operation, attempted on exportEncryptedKey
        val signHandle = TestPlatformAuthenticator.issueHandle(
            keyId = "key_test",
            operation = AuthOperation.SIGN,
            expiresAtMs = System.currentTimeMillis() + 60000,
            cryptoObject = cryptoObject,
            walletId = "key_test"
        )
        val authContext = AuthenticationContext(authHandle = signHandle)

        val exportResult = manager.exportEncryptedKey("key_test", "BackupPwd#123".toCharArray(), authContext = authContext, expectedWalletId = "key_test")

        assertTrue("SIGN handle on exportEncryptedKey MUST fail", exportResult is Result.Failure)
        val ex = (exportResult as Result.Failure).exception
        assertTrue("Must throw AuthenticationRequiredException, got $ex", ex is AuthenticationRequiredException)
        assertTrue("Message must indicate operation mismatch", ex.message!!.contains("does not match expected 'EXPORT'"))
    }

    @Test
    fun challenge2_cross_operation_replay_sign_handle_on_delete_must_fail() = runTest {
        val (manager, _, _) = createTestEnvironment()
        val auth = AuthenticationContext(authHandle = TestPlatformAuthenticator.issueHandle("key_test", AuthOperation.IMPORT, walletId = "key_test"))
        manager.storePrivateKey("key_test", testPrivateKeyHexA.encodeToByteArray(), requireAuth = true, authContext = auth, expectedWalletId = "key_test")

        val signHandle = TestPlatformAuthenticator.issueHandle(
            keyId = "key_test",
            operation = AuthOperation.SIGN,
            expiresAtMs = System.currentTimeMillis() + 60000,
            walletId = "key_test"
        )
        val authContext = AuthenticationContext(authHandle = signHandle)

        val deleteResult = manager.deletePrivateKey("key_test", authContext = authContext, expectedWalletId = "key_test")

        assertTrue("SIGN handle on deletePrivateKey MUST fail", deleteResult is Result.Failure)
        val ex = (deleteResult as Result.Failure).exception
        assertTrue("Must throw AuthenticationRequiredException, got $ex", ex is AuthenticationRequiredException)
        assertTrue("Key must remain after invalid operation delete attempt", manager.hasPrivateKey("key_test"))
    }

    @Test
    fun challenge2_cross_operation_replay_delete_handle_on_sign_must_fail() = runTest {
        val (manager, _, _) = createTestEnvironment()
        val auth = AuthenticationContext(authHandle = TestPlatformAuthenticator.issueHandle("key_test", AuthOperation.IMPORT, walletId = "key_test"))
        manager.storePrivateKey("key_test", testPrivateKeyHexA.encodeToByteArray(), requireAuth = true, authContext = auth, expectedWalletId = "key_test")

        val cryptoObject = manager.createCryptoObjectForDecryption("key_test")
        assertNotNull(cryptoObject)

        val deleteHandle = TestPlatformAuthenticator.issueHandle(
            keyId = "key_test",
            operation = AuthOperation.DELETE,
            expiresAtMs = System.currentTimeMillis() + 60000,
            cryptoObject = cryptoObject,
            walletId = "key_test"
        )
        val authContext = AuthenticationContext(authHandle = deleteHandle)

        val signResult = manager.signWithKey("key_test", "data".encodeToByteArray(), authContext = authContext, expectedWalletId = "key_test")

        assertTrue("DELETE handle on signWithKey MUST fail", signResult is Result.Failure)
        val ex = (signResult as Result.Failure).exception
        assertTrue("Must throw AuthenticationRequiredException, got $ex", ex is AuthenticationRequiredException)
    }

    // =========================================================================
    // 3. Intent Tampering Attacks
    // =========================================================================

    @Test
    fun challenge3_intent_tampering_digest_mismatch_must_fail() = runTest {
        val (manager, _, _) = createTestEnvironment()
        val auth = AuthenticationContext(authHandle = TestPlatformAuthenticator.issueHandle("key_test", AuthOperation.IMPORT, walletId = "key_test"))
        manager.storePrivateKey("key_test", testPrivateKeyHexA.encodeToByteArray(), requireAuth = true, authContext = auth, expectedWalletId = "key_test")

        val cryptoObject = manager.createCryptoObjectForDecryption("key_test")
        assertNotNull(cryptoObject)

        val intentPayloadA = "Send 1 ETH to Alice".encodeToByteArray()
        val digestA = CryptoUtils.sha256(intentPayloadA).toHexString()

        val intentPayloadB = "Send 1000 ETH to Attacker".encodeToByteArray()

        // Auth handle is authorized specifically for digestA
        val handleForIntentA = TestPlatformAuthenticator.issueHandle(
            keyId = "key_test",
            operation = AuthOperation.SIGN,
            intentFingerprint = digestA,
            expiresAtMs = System.currentTimeMillis() + 60000,
            cryptoObject = cryptoObject,
            walletId = "key_test"
        )
        val authContext = AuthenticationContext(authHandle = handleForIntentA)

        // Attacker attempts to use Handle A on intentPayloadB
        val signResult = manager.signWithKey("key_test", intentPayloadB, authContext = authContext, expectedWalletId = "key_test")

        assertTrue("Tampered intent sign MUST fail", signResult is Result.Failure)
        val ex = (signResult as Result.Failure).exception
        assertTrue("Must throw AuthenticationRequiredException, got $ex", ex is AuthenticationRequiredException)
        assertTrue("Message must indicate Intent fingerprint mismatch", ex.message!!.contains("Intent fingerprint mismatch"))
    }

    @Test
    fun challenge3_intent_exact_match_succeeds_and_produces_valid_signature() = runTest {
        val (manager, _, _) = createTestEnvironment()
        val auth = AuthenticationContext(authHandle = TestPlatformAuthenticator.issueHandle("key_test", AuthOperation.IMPORT, walletId = "key_test"))
        manager.storePrivateKey("key_test", testPrivateKeyHexA.encodeToByteArray(), requireAuth = true, authContext = auth, expectedWalletId = "key_test")

        val cryptoObject = manager.createCryptoObjectForDecryption("key_test")
        assertNotNull(cryptoObject)

        val intentPayload = "Send 1 ETH to Alice".encodeToByteArray()
        val digest = CryptoUtils.sha256(intentPayload)
        val digestHex = digest.toHexString()

        val handle = TestPlatformAuthenticator.issueHandle(
            keyId = "key_test",
            operation = AuthOperation.SIGN,
            intentFingerprint = digestHex,
            expiresAtMs = System.currentTimeMillis() + 60000,
            cryptoObject = cryptoObject,
            walletId = "key_test"
        )
        val authContext = AuthenticationContext(authHandle = handle)

        val signResult = manager.signWithKey("key_test", intentPayload, authContext = authContext, expectedWalletId = "key_test")

        assertTrue("Untampered intent sign MUST succeed", signResult is Result.Success)
        val signature = (signResult as Result.Success).data
        assertNotNull(signature)
        assertTrue("Signature size must be 64 or 65 bytes", signature.size in 64..65)
    }

    // =========================================================================
    // 4. Expired / Invalidated Handle Reuse Attacks
    // =========================================================================

    @Test
    fun challenge4_expired_handle_reuse_on_sign_must_fail() = runTest {
        val (manager, _, _) = createTestEnvironment()
        val auth = AuthenticationContext(authHandle = TestPlatformAuthenticator.issueHandle("key_test", AuthOperation.IMPORT, walletId = "key_test"))
        manager.storePrivateKey("key_test", testPrivateKeyHexA.encodeToByteArray(), requireAuth = true, authContext = auth, expectedWalletId = "key_test")

        val cryptoObject = manager.createCryptoObjectForDecryption("key_test")
        assertNotNull(cryptoObject)

        val txData = "Tx data".encodeToByteArray()
        val txDigest = CryptoUtils.sha256(txData).toHexString()

        // Handle expired 5 seconds ago
        val issued = System.currentTimeMillis() - 60000
        val expires = System.currentTimeMillis() - 5000
        val expiredHandle = PlatformAuthHandle(
            keyId = "key_test",
            operation = AuthOperation.SIGN,
            intentFingerprint = txDigest,
            sessionId = "session-exp-sign",
            nonce = "nonce-exp-sign",
            issuedAtMs = issued,
            expiresAtMs = expires,
            walletId = "key_test",
            proofToken = ProofTokenVerifier.sign("key_test", AuthOperation.SIGN, txDigest, "session-exp-sign", "nonce-exp-sign", issued, expires, "key_test")
        ).apply {
            this.cryptoObject = cryptoObject
        }
        val authContext = AuthenticationContext(authHandle = expiredHandle)

        val signResult = manager.signWithKey("key_test", txData, authContext = authContext, expectedWalletId = "key_test")

        assertTrue("Expired handle on signWithKey MUST fail", signResult is Result.Failure)
        val ex = (signResult as Result.Failure).exception
        assertTrue("Must throw AuthenticationRequiredException, got $ex", ex is AuthenticationRequiredException)
        assertTrue("Message must indicate handle expiration", ex.message!!.contains("has expired"))
    }

    @Test
    fun challenge4_expired_handle_reuse_on_delete_must_fail() = runTest {
        val (manager, _, _) = createTestEnvironment()
        val auth = AuthenticationContext(authHandle = TestPlatformAuthenticator.issueHandle("key_test", AuthOperation.IMPORT, walletId = "key_test"))
        manager.storePrivateKey("key_test", testPrivateKeyHexA.encodeToByteArray(), requireAuth = true, authContext = auth, expectedWalletId = "key_test")

        val issued = System.currentTimeMillis() - 60000
        val expires = System.currentTimeMillis() - 5000
        val expiredHandle = PlatformAuthHandle(
            keyId = "key_test",
            operation = AuthOperation.DELETE,
            intentFingerprint = "",
            sessionId = "session-exp-del",
            nonce = "nonce-exp-del",
            issuedAtMs = issued,
            expiresAtMs = expires,
            walletId = "key_test",
            proofToken = ProofTokenVerifier.sign("key_test", AuthOperation.DELETE, "", "session-exp-del", "nonce-exp-del", issued, expires, "key_test")
        )
        val authContext = AuthenticationContext(authHandle = expiredHandle)

        val deleteResult = manager.deletePrivateKey("key_test", authContext = authContext, expectedWalletId = "key_test")

        assertTrue("Expired handle on deletePrivateKey MUST fail", deleteResult is Result.Failure)
        val ex = (deleteResult as Result.Failure).exception
        assertTrue("Must throw AuthenticationRequiredException, got $ex", ex is AuthenticationRequiredException)
        assertTrue("Key must remain stored", manager.hasPrivateKey("key_test"))
    }

    @Test
    fun challenge4_invalidated_handle_reuse_on_sign_must_fail() = runTest {
        val (manager, _, _) = createTestEnvironment()
        val auth = AuthenticationContext(authHandle = TestPlatformAuthenticator.issueHandle("key_test", AuthOperation.IMPORT, walletId = "key_test"))
        manager.storePrivateKey("key_test", testPrivateKeyHexA.encodeToByteArray(), requireAuth = true, authContext = auth, expectedWalletId = "key_test")

        val cryptoObject = manager.createCryptoObjectForDecryption("key_test")
        assertNotNull(cryptoObject)

        val txData = "Tx data".encodeToByteArray()
        val txDigest = CryptoUtils.sha256(txData).toHexString()

        val handle = TestPlatformAuthenticator.issueHandle(
            keyId = "key_test",
            operation = AuthOperation.SIGN,
            intentFingerprint = txDigest,
            expiresAtMs = System.currentTimeMillis() + 60000,
            cryptoObject = cryptoObject,
            walletId = "key_test"
        )
        // Explicitly invalidate handle (e.g. user cancelled or timeout/logout)
        handle.invalidate()
        assertTrue(handle.isInvalidated)

        val authContext = AuthenticationContext(authHandle = handle)
        val signResult = manager.signWithKey("key_test", txData, authContext = authContext, expectedWalletId = "key_test")

        assertTrue("Invalidated handle on signWithKey MUST fail", signResult is Result.Failure)
        val ex = (signResult as Result.Failure).exception
        assertTrue("Must throw AuthenticationRequiredException, got $ex", ex is AuthenticationRequiredException)
        assertTrue("Message must indicate handle invalidation", ex.message!!.contains("is invalidated"))
    }

    @Test
    fun challenge4_invalidated_handle_reuse_on_delete_must_fail() = runTest {
        val (manager, _, _) = createTestEnvironment()
        val auth = AuthenticationContext(authHandle = TestPlatformAuthenticator.issueHandle("key_test", AuthOperation.IMPORT, walletId = "key_test"))
        manager.storePrivateKey("key_test", testPrivateKeyHexA.encodeToByteArray(), requireAuth = true, authContext = auth, expectedWalletId = "key_test")

        val handle = TestPlatformAuthenticator.issueHandle(
            keyId = "key_test",
            operation = AuthOperation.DELETE,
            expiresAtMs = System.currentTimeMillis() + 60000,
            walletId = "key_test"
        )
        handle.invalidate()

        val authContext = AuthenticationContext(authHandle = handle)
        val deleteResult = manager.deletePrivateKey("key_test", authContext = authContext, expectedWalletId = "key_test")

        assertTrue("Invalidated handle on deletePrivateKey MUST fail", deleteResult is Result.Failure)
        val ex = (deleteResult as Result.Failure).exception
        assertTrue("Must throw AuthenticationRequiredException, got $ex", ex is AuthenticationRequiredException)
        assertTrue("Key must remain stored", manager.hasPrivateKey("key_test"))
    }

    @Test
    fun challenge4_platform_auth_handle_unit_contract_checks() {
        val now = System.currentTimeMillis()
        val token = ProofTokenVerifier.sign("target_key", AuthOperation.SIGN, "abcd1234", "session_test", "nonce_test", now - 1000, now + 5000, "target_key")
        val handle = PlatformAuthHandle(
            keyId = "target_key",
            operation = AuthOperation.SIGN,
            intentFingerprint = "abcd1234",
            sessionId = "session_test",
            nonce = "nonce_test",
            issuedAtMs = now - 1000,
            expiresAtMs = now + 5000,
            walletId = "target_key",
            proofToken = token
        )

        // Valid conditions
        assertTrue("Valid handle must return true", handle.isValid("target_key", "abcd1234", AuthOperation.SIGN, now, "target_key"))
        assertFalse("Should not be expired at now", handle.isExpired(now))

        // Expired conditions
        assertTrue("Should be expired at now + 6000", handle.isExpired(now + 6000))
        assertFalse("isValid must return false when expired", handle.isValid("target_key", "abcd1234", AuthOperation.SIGN, now + 6000, "target_key"))

        // Mismatches
        assertFalse("isValid must return false on keyId mismatch", handle.isValid("other_key", "abcd1234", AuthOperation.SIGN, now, "target_key"))
        assertFalse("isValid must return false on intent mismatch", handle.isValid("target_key", "deadbeef", AuthOperation.SIGN, now, "target_key"))
        assertFalse("isValid must return false on operation mismatch", handle.isValid("target_key", "abcd1234", AuthOperation.EXPORT, now, "target_key"))

        // Invalidation
        handle.invalidate()
        assertTrue(handle.isInvalidated)
        assertFalse("isValid must return false after invalidation", handle.isValid("target_key", "abcd1234", AuthOperation.SIGN, now, "target_key"))
    }

    // =========================================================================
    // 5. Delete Without AuthContext Attacks
    // =========================================================================

    @Test
    fun challenge5_delete_require_auth_null_auth_context_must_fail() = runTest {
        val (manager, _, _) = createTestEnvironment()
        val auth = AuthenticationContext(authHandle = TestPlatformAuthenticator.issueHandle("protected_key", AuthOperation.IMPORT, walletId = "protected_key"))
        manager.storePrivateKey("protected_key", testPrivateKeyHexA.encodeToByteArray(), requireAuth = true, authContext = auth, expectedWalletId = "protected_key")
        assertTrue("Key must exist initially", manager.hasPrivateKey("protected_key"))

        val deleteResult = manager.deletePrivateKey("protected_key", authContext = null, expectedWalletId = "protected_key")

        assertTrue("Delete without authContext on requireAuth key MUST fail", deleteResult is Result.Failure)
        val ex = (deleteResult as Result.Failure).exception
        assertTrue("Must throw AuthenticationRequiredException, got $ex", ex is AuthenticationRequiredException)
        assertTrue("Key MUST remain intact", manager.hasPrivateKey("protected_key"))
    }

    @Test
    fun challenge5_delete_require_auth_empty_auth_context_must_fail() = runTest {
        val (manager, _, _) = createTestEnvironment()
        val auth = AuthenticationContext(authHandle = TestPlatformAuthenticator.issueHandle("protected_key", AuthOperation.IMPORT, walletId = "protected_key"))
        manager.storePrivateKey("protected_key", testPrivateKeyHexA.encodeToByteArray(), requireAuth = true, authContext = auth, expectedWalletId = "protected_key")

        val emptyAuthContext = AuthenticationContext(authHandle = null, cryptoObject = null)
        val deleteResult = manager.deletePrivateKey("protected_key", authContext = emptyAuthContext, expectedWalletId = "protected_key")

        assertTrue("Delete with empty authContext on requireAuth key MUST fail", deleteResult is Result.Failure)
        val ex = (deleteResult as Result.Failure).exception
        assertTrue("Must throw AuthenticationRequiredException, got $ex", ex is AuthenticationRequiredException)
        assertTrue("Key MUST remain intact", manager.hasPrivateKey("protected_key"))
    }

    @Test
    fun challenge5_delete_non_require_auth_key_without_auth_context_succeeds() = runTest {
        val (manager, _, _) = createTestEnvironment()
        manager.storePrivateKey("unprotected_key", testPrivateKeyHexA.encodeToByteArray(), requireAuth = false, authContext = null, expectedWalletId = "unprotected_key")
        assertTrue(manager.hasPrivateKey("unprotected_key"))

        val deleteResult = manager.deletePrivateKey("unprotected_key", authContext = null, expectedWalletId = "unprotected_key")

        assertTrue("Delete without authContext on non-requireAuth key should succeed", deleteResult is Result.Success)
        assertFalse("Key must be deleted", manager.hasPrivateKey("unprotected_key"))
    }

    // =========================================================================
    // 6. Adversarial Probes for Blank / Wildcard Parameter Loopholes
    // =========================================================================

    @Test
    fun adversarial_probe_empty_keyId_in_delete_handle_must_be_strictly_evaluated() = runTest {
        val (manager, _, _) = createTestEnvironment()
        val auth = AuthenticationContext(authHandle = TestPlatformAuthenticator.issueHandle("strictly_protected_key", AuthOperation.IMPORT, walletId = "strictly_protected_key"))
        manager.storePrivateKey("strictly_protected_key", testPrivateKeyHexA.encodeToByteArray(), requireAuth = true, authContext = auth, expectedWalletId = "strictly_protected_key")

        // 1. Authenticator rejects issuing handle with blank keyId
        assertThrows(IllegalArgumentException::class.java) {
            TestPlatformAuthenticator.issueHandle(
                keyId = "",
                operation = AuthOperation.DELETE,
                expiresAtMs = System.currentTimeMillis() + 60000,
                walletId = "strictly_protected_key"
            )
        }

        // 2. Raw handle with empty keyId passed to deletePrivateKey MUST fail
        val rawWildcardHandle = PlatformAuthHandle(
            keyId = "",
            operation = AuthOperation.DELETE,
            intentFingerprint = "",
            sessionId = "wildcard_session",
            nonce = "wildcard_nonce",
            issuedAtMs = System.currentTimeMillis(),
            expiresAtMs = System.currentTimeMillis() + 60000,
            proofToken = "fake_proof",
            walletId = "strictly_protected_key"
        )
        val authContext = AuthenticationContext(authHandle = rawWildcardHandle)

        val deleteResult = manager.deletePrivateKey("strictly_protected_key", authContext = authContext, expectedWalletId = "strictly_protected_key")
        assertTrue("Empty keyId handle on deletePrivateKey MUST fail", deleteResult is Result.Failure)
        assertTrue(manager.hasPrivateKey("strictly_protected_key"))
    }

    // =========================================================================
    // 7. FakeSecureKeyManager High-Fidelity Parity Challenges
    // =========================================================================

    @Test
    fun challenge7_fakeKeyManager_cross_key_sign_replay_must_fail() = runTest {
        val fakeManager = FakeSecureKeyManager()
        val authA = AuthenticationContext(authHandle = TestPlatformAuthenticator.issueHandle("key_A", AuthOperation.IMPORT, walletId = "key_A"))
        val authB = AuthenticationContext(authHandle = TestPlatformAuthenticator.issueHandle("key_B", AuthOperation.IMPORT, walletId = "key_B"))
        fakeManager.storePrivateKey("key_A", testPrivateKeyHexA.encodeToByteArray(), requireAuth = true, authContext = authA, expectedWalletId = "key_A")
        fakeManager.storePrivateKey("key_B", testPrivateKeyHexB.encodeToByteArray(), requireAuth = true, authContext = authB, expectedWalletId = "key_B")

        val txData = "Transfer 1 ETH to Bob".encodeToByteArray()
        val txDigest = CryptoUtils.sha256(txData).toHexString()

        val handleA = TestPlatformAuthenticator.issueHandle(
            keyId = "key_A",
            operation = AuthOperation.SIGN,
            intentFingerprint = txDigest,
            walletId = "key_A"
        )
        val authContextA = AuthenticationContext(authHandle = handleA)

        val signResult = fakeManager.signWithKey("key_B", txData, authContext = authContextA, expectedWalletId = "key_B")
        assertTrue("Cross-key sign replay MUST fail on FakeSecureKeyManager", signResult is Result.Failure)
        val ex = (signResult as Result.Failure).exception
        assertTrue("Must throw AuthenticationRequiredException, got $ex", ex is AuthenticationRequiredException)
        assertTrue("Message must indicate cross-key rejection", ex.message!!.contains("Cross-key handle rejected"))
    }

    @Test
    fun challenge7_fakeKeyManager_cross_operation_replay_must_fail() = runTest {
        val fakeManager = FakeSecureKeyManager()
        val authA = AuthenticationContext(authHandle = TestPlatformAuthenticator.issueHandle("key_A", AuthOperation.IMPORT, walletId = "key_A"))
        fakeManager.storePrivateKey("key_A", testPrivateKeyHexA.encodeToByteArray(), requireAuth = true, authContext = authA, expectedWalletId = "key_A")

        val txData = "Transfer 1 ETH to Bob".encodeToByteArray()
        val txDigest = CryptoUtils.sha256(txData).toHexString()

        // 1. DELETE handle passed to signWithKey
        val deleteHandle = TestPlatformAuthenticator.issueHandle(
            keyId = "key_A",
            operation = AuthOperation.DELETE,
            intentFingerprint = txDigest,
            walletId = "key_A"
        )
        val signResult = fakeManager.signWithKey("key_A", txData, AuthenticationContext(authHandle = deleteHandle), expectedWalletId = "key_A")
        assertTrue("DELETE handle on signWithKey MUST fail", signResult is Result.Failure)
        assertTrue((signResult as Result.Failure).exception is AuthenticationRequiredException)

        // 2. SIGN handle passed to deletePrivateKey
        val signHandle = TestPlatformAuthenticator.issueHandle(
            keyId = "key_A",
            operation = AuthOperation.SIGN,
            intentFingerprint = txDigest,
            walletId = "key_A"
        )
        val deleteResult = fakeManager.deletePrivateKey("key_A", AuthenticationContext(authHandle = signHandle), expectedWalletId = "key_A")
        assertTrue("SIGN handle on deletePrivateKey MUST fail", deleteResult is Result.Failure)
        assertTrue((deleteResult as Result.Failure).exception is AuthenticationRequiredException)
        assertTrue(fakeManager.hasPrivateKey("key_A"))
    }

    @Test
    fun challenge7_fakeKeyManager_intent_tampering_must_fail() = runTest {
        val fakeManager = FakeSecureKeyManager()
        val authA = AuthenticationContext(authHandle = TestPlatformAuthenticator.issueHandle("key_A", AuthOperation.IMPORT, walletId = "key_A"))
        fakeManager.storePrivateKey("key_A", testPrivateKeyHexA.encodeToByteArray(), requireAuth = true, authContext = authA, expectedWalletId = "key_A")

        val originalData = "Transfer 1 ETH to Bob".encodeToByteArray()
        val originalDigest = CryptoUtils.sha256(originalData).toHexString()
        val tamperedData = "Transfer 100 ETH to Mallory".encodeToByteArray()

        val handle = TestPlatformAuthenticator.issueHandle(
            keyId = "key_A",
            operation = AuthOperation.SIGN,
            intentFingerprint = originalDigest,
            walletId = "key_A"
        )
        val signResult = fakeManager.signWithKey("key_A", tamperedData, AuthenticationContext(authHandle = handle), expectedWalletId = "key_A")
        assertTrue("Intent tampering MUST fail on FakeSecureKeyManager", signResult is Result.Failure)
        val ex = (signResult as Result.Failure).exception
        assertTrue("Must throw AuthenticationRequiredException, got $ex", ex is AuthenticationRequiredException)
        assertTrue("Message must indicate fingerprint mismatch", ex.message!!.contains("Intent fingerprint mismatch"))
    }

    @Test
    fun challenge7_fakeKeyManager_rollback_uncommitted_key_bypasses_auth() = runTest {
        val fakeManager = FakeSecureKeyManager()
        val session = fakeManager.startProvisioningSession()
        val authContext = AuthenticationContext(
            authHandle = TestPlatformAuthenticator.issueHandle(
                keyId = session.stagedKeyAlias,
                sessionId = session.sessionId,
                operation = AuthOperation.IMPORT,
                walletId = session.stagedKeyAlias
            )
        )
        fakeManager.storeStagedPrivateKey(session, testPrivateKeyHexA.encodeToByteArray(), requireAuth = true, authContext = authContext)

        // deletePrivateKey without auth fails
        val deleteResult = fakeManager.deletePrivateKey(session.stagedKeyAlias, authContext = null, expectedWalletId = session.stagedKeyAlias)
        assertTrue(deleteResult is Result.Failure)
        assertTrue(fakeManager.hasPrivateKey(session.stagedKeyAlias))

        // rollbackProvisioningSession deletes without auth
        val rollbackResult = fakeManager.rollbackProvisioningSession(session)
        assertTrue(rollbackResult is Result.Success)
        assertFalse(fakeManager.hasPrivateKey(session.stagedKeyAlias))
    }

    @Test
    fun challenge7_fakeKeyManager_expired_handle_reuse_on_sign_must_fail() = runTest {
        val fakeManager = FakeSecureKeyManager()
        val authA = AuthenticationContext(authHandle = TestPlatformAuthenticator.issueHandle("key_A", AuthOperation.IMPORT, walletId = "key_A"))
        fakeManager.storePrivateKey("key_A", testPrivateKeyHexA.encodeToByteArray(), requireAuth = true, authContext = authA, expectedWalletId = "key_A")

        val txData = "Transfer 5 SOL".encodeToByteArray()
        val txDigest = CryptoUtils.sha256(txData).toHexString()

        val issued = System.currentTimeMillis() - 100_000L
        val expires = System.currentTimeMillis() - 10_000L
        val expiredHandle = PlatformAuthHandle(
            keyId = "key_A",
            operation = AuthOperation.SIGN,
            intentFingerprint = txDigest,
            sessionId = "session-expired",
            nonce = "nonce-expired",
            issuedAtMs = issued,
            expiresAtMs = expires,
            walletId = "key_A",
            proofToken = ProofTokenVerifier.sign("key_A", AuthOperation.SIGN, txDigest, "session-expired", "nonce-expired", issued, expires, "key_A")
        )
        val signResult = fakeManager.signWithKey("key_A", txData, AuthenticationContext(authHandle = expiredHandle), expectedWalletId = "key_A")
        assertTrue("Expired handle on sign MUST fail", signResult is Result.Failure)
        val ex = (signResult as Result.Failure).exception
        assertTrue("Must throw AuthenticationRequiredException, got $ex", ex is AuthenticationRequiredException)
        assertTrue("Message must indicate expiration", ex.message!!.contains("has expired"))
    }

    @Test
    fun challenge7_fakeKeyManager_invalidated_handle_reuse_on_sign_must_fail() = runTest {
        val fakeManager = FakeSecureKeyManager()
        val authA = AuthenticationContext(authHandle = TestPlatformAuthenticator.issueHandle("key_A", AuthOperation.IMPORT, walletId = "key_A"))
        fakeManager.storePrivateKey("key_A", testPrivateKeyHexA.encodeToByteArray(), requireAuth = true, authContext = authA, expectedWalletId = "key_A")

        val txData = "Transfer 5 SOL".encodeToByteArray()
        val txDigest = CryptoUtils.sha256(txData).toHexString()

        val handle = TestPlatformAuthenticator.issueHandle(
            keyId = "key_A",
            operation = AuthOperation.SIGN,
            intentFingerprint = txDigest,
            expiresAtMs = System.currentTimeMillis() + 60_000L,
            walletId = "key_A"
        )
        handle.invalidate()

        val signResult = fakeManager.signWithKey("key_A", txData, AuthenticationContext(authHandle = handle), expectedWalletId = "key_A")
        assertTrue("Invalidated handle on sign MUST fail", signResult is Result.Failure)
        val ex = (signResult as Result.Failure).exception
        assertTrue("Must throw AuthenticationRequiredException, got $ex", ex is AuthenticationRequiredException)
        assertTrue("Message must indicate invalidation", ex.message!!.contains("is invalidated"))
    }

    @Test
    fun challenge7_fakeKeyManager_unauthenticated_sign_on_require_auth_key_must_fail() = runTest {
        val fakeManager = FakeSecureKeyManager()
        val authA = AuthenticationContext(authHandle = TestPlatformAuthenticator.issueHandle("key_A", AuthOperation.IMPORT, walletId = "key_A"))
        fakeManager.storePrivateKey("key_A", testPrivateKeyHexA.encodeToByteArray(), requireAuth = true, authContext = authA, expectedWalletId = "key_A")

        val txData = "Transfer 10 ETH".encodeToByteArray()
        val signResult = fakeManager.signWithKey("key_A", txData, authContext = null, expectedWalletId = "key_A")
        assertTrue("Unauthenticated sign MUST fail", signResult is Result.Failure)
        val ex = (signResult as Result.Failure).exception
        assertTrue("Must throw AuthenticationRequiredException, got $ex", ex is AuthenticationRequiredException)
        assertTrue("Message must indicate auth is required", ex.message!!.contains("authContext is null"))
    }

    @Test
    fun challenge7_fakeKeyManager_unauthenticated_delete_on_require_auth_key_must_fail() = runTest {
        val fakeManager = FakeSecureKeyManager()
        val authA = AuthenticationContext(authHandle = TestPlatformAuthenticator.issueHandle("key_A", AuthOperation.IMPORT, walletId = "key_A"))
        fakeManager.storePrivateKey("key_A", testPrivateKeyHexA.encodeToByteArray(), requireAuth = true, authContext = authA, expectedWalletId = "key_A")

        val deleteResult = fakeManager.deletePrivateKey("key_A", authContext = null, expectedWalletId = "key_A")
        assertTrue("Unauthenticated delete MUST fail", deleteResult is Result.Failure)
        val ex = (deleteResult as Result.Failure).exception
        assertTrue("Must throw AuthenticationRequiredException, got $ex", ex is AuthenticationRequiredException)
        assertTrue("Key must remain stored", fakeManager.hasPrivateKey("key_A"))
    }

    @Test
    fun challenge7_fakeKeyManager_valid_signing_and_verification_succeeds() = runTest {
        val fakeManager = FakeSecureKeyManager()
        val authA = AuthenticationContext(authHandle = TestPlatformAuthenticator.issueHandle("key_A", AuthOperation.IMPORT, walletId = "key_A"))
        fakeManager.storePrivateKey("key_A", testPrivateKeyHexA.encodeToByteArray(), requireAuth = true, authContext = authA, expectedWalletId = "key_A")

        val txData = CryptoUtils.sha256("EVM Transaction Payload".encodeToByteArray())
        val txDigest = txData.toHexString()

        val validHandle = TestPlatformAuthenticator.issueHandle(
            keyId = "key_A",
            operation = AuthOperation.SIGN,
            intentFingerprint = txDigest,
            expiresAtMs = System.currentTimeMillis() + 60_000L,
            walletId = "key_A"
        )

        val signResult = fakeManager.signWithKey("key_A", txData, AuthenticationContext(authHandle = validHandle), expectedWalletId = "key_A")
        assertTrue("Valid authenticated sign MUST succeed", signResult is Result.Success)
        val sigBytes = (signResult as Result.Success).data
        assertEquals("Signature must be 65 bytes (r + s + v)", 65, sigBytes.size)

        // Verify recovered point matches public key of testPrivateKeyHexA
        val r = sigBytes.copyOfRange(0, 32)
        val s = sigBytes.copyOfRange(32, 64)
        val yParity = sigBytes[64].toInt() and 0xFF
        val z = Secp256k1Pure.BigInteger.fromByteArray(txData)
        val rBig = Secp256k1Pure.BigInteger.fromByteArray(r)
        val sBig = Secp256k1Pure.BigInteger.fromByteArray(s)
        val pointQ = Secp256k1Pure.recoverPublicKeyPoint(z, rBig, sBig, yParity)
        assertNotNull("Public key point must be recoverable from signature", pointQ)
    }

    // =========================================================================
    // 8. WalletId Binding Negative Security Tests (P1-2)
    // =========================================================================

    @Test
    fun challenge8_tampered_wallet_id_in_platform_auth_handle_must_fail_verification() = runTest {
        val txDigest = "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890"
        val now = System.currentTimeMillis()
        val handle = TestPlatformAuthenticator.issueHandle(
            keyId = "key_wallet_1",
            operation = AuthOperation.SIGN,
            intentFingerprint = txDigest,
            expiresAtMs = now + 60_000L,
            walletId = "wallet_1"
        )

        // 1. Validate with correct walletId -> true
        assertTrue("Matching walletId must pass validation", handle.isValid(expectedKeyId = "key_wallet_1", expectedIntentFingerprint = txDigest, expectedOperation = AuthOperation.SIGN, currentTimeMs = handle.issuedAtMs, expectedWalletId = "wallet_1"))

        // 2. Validate with mismatched/tampered walletId -> false
        assertFalse("Mismatched walletId must fail validation", handle.isValid(expectedKeyId = "key_wallet_1", expectedIntentFingerprint = txDigest, expectedOperation = AuthOperation.SIGN, currentTimeMs = handle.issuedAtMs, expectedWalletId = "wallet_2"))

        // 3. Validate when handle has walletId but caller expectedWalletId differs -> false
        assertFalse("Cross-wallet expectedId must fail validation", handle.isValid(expectedKeyId = "key_wallet_1", expectedIntentFingerprint = txDigest, expectedOperation = AuthOperation.SIGN, currentTimeMs = handle.issuedAtMs, expectedWalletId = "attacker_wallet"))
    }

    @Test
    fun challenge8_tampered_proof_token_fails_hmac_verification() = runTest {
        val now = System.currentTimeMillis()
        val token = ProofTokenVerifier.sign(
            keyId = "key_test_1",
            operation = AuthOperation.SIGN,
            intentFingerprint = "fingerprint_1",
            sessionId = "session_123",
            nonce = "nonce_456",
            issuedAtMs = now,
            expiresAtMs = now + 60_000L,
            walletId = "wallet_original"
        )

        // Valid token
        assertTrue("Authentic proof token must verify", ProofTokenVerifier.verify(
            proofToken = token,
            keyId = "key_test_1",
            operation = AuthOperation.SIGN,
            intentFingerprint = "fingerprint_1",
            sessionId = "session_123",
            nonce = "nonce_456",
            issuedAtMs = now,
            expiresAtMs = now + 60_000L,
            walletId = "wallet_original"
        ))

        // Tampered walletId in verification
        assertFalse("Verification with different walletId must fail HMAC", ProofTokenVerifier.verify(
            proofToken = token,
            keyId = "key_test_1",
            operation = AuthOperation.SIGN,
            intentFingerprint = "fingerprint_1",
            sessionId = "session_123",
            nonce = "nonce_456",
            issuedAtMs = now,
            expiresAtMs = now + 60_000L,
            walletId = "wallet_tampered"
        ))

        // Tampered token payload
        val tamperedToken = if (token.startsWith("0")) "1" + token.substring(1) else "0" + token.substring(1)
        assertFalse("Tampered token payload must fail HMAC signature check", ProofTokenVerifier.verify(
            proofToken = tamperedToken,
            keyId = "key_test_1",
            operation = AuthOperation.SIGN,
            intentFingerprint = "fingerprint_1",
            sessionId = "session_123",
            nonce = "nonce_456",
            issuedAtMs = now,
            expiresAtMs = now + 60_000L,
            walletId = "wallet_original"
        ))
    }

    // =========================================================================
    // 9. Milestone 4: P2 AuthHandleRegistry Invariant Adversarial Attacks
    // =========================================================================

    @Test
    fun challenge9_consume_unregistered_session_returns_false_and_zero_records_in_consumedSessions() {
        val nonExistentSession = "adversarial_unregistered_session_12345"
        val blankSession = "   "
        val emptySession = ""

        // 1. Attempt to consume unregistered session
        val result1 = AuthHandleRegistry.consume(nonExistentSession)
        assertFalse("Consuming unregistered session must return false", result1)

        // 2. Attempt to consume blank / empty session
        assertFalse("Consuming blank session must return false", AuthHandleRegistry.consume(blankSession))
        assertFalse("Consuming empty session must return false", AuthHandleRegistry.consume(emptySession))

        // 3. Verify zero state in consumedSessions and activeSessions
        assertFalse("Unregistered session must not be consumed", AuthHandleRegistry.isConsumed(nonExistentSession))
        assertFalse("Unregistered session must not be registered", AuthHandleRegistry.isRegistered(nonExistentSession))
        assertNull("Unregistered session metadata must be null", AuthHandleRegistry.getConsumedSessionMetadata(nonExistentSession))
        assertNull("Active session metadata must be null", AuthHandleRegistry.getActiveSessionMetadata(nonExistentSession))
    }

    @Test
    fun challenge9_blank_and_cross_wallet_id_unconditionally_fails_validateAndConsume() {
        val now = 500_000L
        val expiresAt = 550_000L
        val sessionId = "adv_session_wallet_cross"
        val keyId = "key_cross_wallet"
        val attackerWalletId = "attacker_wallet_666"
        val victimWalletId = "victim_wallet_777"
        val txFingerprint = "deadbeef1234"

        val proofToken = ProofTokenVerifier.sign(
            keyId = keyId,
            operation = AuthOperation.SIGN,
            intentFingerprint = txFingerprint,
            sessionId = sessionId,
            nonce = "nonce_adv_1",
            issuedAtMs = now,
            expiresAtMs = expiresAt,
            walletId = attackerWalletId,
            authenticatorType = "ADVERSARIAL_TEST"
        )

        val handle = PlatformAuthHandle(
            keyId = keyId,
            operation = AuthOperation.SIGN,
            intentFingerprint = txFingerprint,
            sessionId = sessionId,
            nonce = "nonce_adv_1",
            issuedAtMs = now,
            expiresAtMs = expiresAt,
            proofToken = proofToken,
            walletId = attackerWalletId
        )

        // 1. Pass blank expectedWalletId -> Must fail with IllegalArgumentException
        val blankResult = AuthHandleRegistry.validateAndConsume(
            handle = handle,
            expectedKeyId = keyId,
            expectedOperation = AuthOperation.SIGN,
            currentTimeMs = now + 1000L,
            expectedWalletId = ""
        )
        assertTrue("Blank expectedWalletId must fail", blankResult is Result.Failure)
        assertTrue((blankResult as Result.Failure).exception is IllegalArgumentException)

        val spacesResult = AuthHandleRegistry.validateAndConsume(
            handle = handle,
            expectedKeyId = keyId,
            expectedOperation = AuthOperation.SIGN,
            currentTimeMs = now + 1000L,
            expectedWalletId = "   "
        )
        assertTrue("Whitespace expectedWalletId must fail", spacesResult is Result.Failure)
        assertTrue((spacesResult as Result.Failure).exception is IllegalArgumentException)

        // 2. Cross-wallet attempt: caller expects victimWalletId, but handle/session is attackerWalletId
        val crossResult = AuthHandleRegistry.validateAndConsume(
            handle = handle,
            expectedKeyId = keyId,
            expectedOperation = AuthOperation.SIGN,
            expectedFingerprint = txFingerprint,
            currentTimeMs = now + 1000L,
            expectedWalletId = victimWalletId
        )
        assertTrue("Cross-wallet validateAndConsume must fail", crossResult is Result.Failure)
        val crossEx = (crossResult as Result.Failure).exception
        assertTrue("Must throw AuthenticationRequiredException, got $crossEx", crossEx is AuthenticationRequiredException)
        assertTrue("Message must indicate cross-wallet rejection", crossEx.message!!.contains("Cross-wallet"))

        // Session must NOT have been consumed
        assertFalse("Session must remain unconsumed after failed cross-wallet attempt", AuthHandleRegistry.isConsumed(sessionId))
        assertTrue("Session must remain registered after failed cross-wallet attempt", AuthHandleRegistry.isRegistered(sessionId, now + 1000L))

        // 3. Valid expectedWalletId -> Must succeed
        val validResult = AuthHandleRegistry.validateAndConsume(
            handle = handle,
            expectedKeyId = keyId,
            expectedOperation = AuthOperation.SIGN,
            expectedFingerprint = txFingerprint,
            currentTimeMs = now + 1000L,
            expectedWalletId = attackerWalletId
        )
        assertTrue("Valid walletId validateAndConsume must succeed", validResult is Result.Success)
        assertTrue("Session must be consumed after valid consumption", AuthHandleRegistry.isConsumed(sessionId))
    }

    @Test
    fun challenge9_boundary_at_currentTimeMs_equals_expiresAtMs_rejected() {
        val now = 600_000L
        val expiresAt = 610_000L
        val sessionId = "adv_session_boundary"
        val keyId = "key_boundary"
        val walletId = "wallet_boundary"
        val txFingerprint = "boundary_fp"

        val proofToken = ProofTokenVerifier.sign(
            keyId = keyId,
            operation = AuthOperation.SIGN,
            intentFingerprint = txFingerprint,
            sessionId = sessionId,
            nonce = "nonce_boundary",
            issuedAtMs = now,
            expiresAtMs = expiresAt,
            walletId = walletId,
            authenticatorType = "ADVERSARIAL_TEST"
        )

        val handle = PlatformAuthHandle(
            keyId = keyId,
            operation = AuthOperation.SIGN,
            intentFingerprint = txFingerprint,
            sessionId = sessionId,
            nonce = "nonce_boundary",
            issuedAtMs = now,
            expiresAtMs = expiresAt,
            proofToken = proofToken,
            walletId = walletId
        )

        // Attempt at currentTimeMs == expiresAtMs
        val boundaryResult = AuthHandleRegistry.validateAndConsume(
            handle = handle,
            expectedKeyId = keyId,
            expectedOperation = AuthOperation.SIGN,
            expectedFingerprint = txFingerprint,
            currentTimeMs = expiresAt,
            expectedWalletId = walletId
        )

        assertTrue("currentTimeMs == expiresAtMs must FAIL", boundaryResult is Result.Failure)
        val ex = (boundaryResult as Result.Failure).exception
        assertTrue("Must throw AuthenticationRequiredException, got $ex", ex is AuthenticationRequiredException)
        assertTrue("Message must indicate expiration", ex.message!!.contains("has expired"))

        // Active session must have been purged due to expiry, and NOT added to consumedSessions
        assertFalse("Expired session must not be active", AuthHandleRegistry.isRegistered(sessionId, expiresAt))
        assertFalse("Expired session must not be recorded as consumed", AuthHandleRegistry.isConsumed(sessionId))
    }

    @Test
    fun challenge9_registering_colliding_sessions_throws_IllegalStateException() {
        val now = 700_000L
        val expiresAt = 720_000L
        val sessionId = "adv_session_collision"
        val keyId = "key_collision"
        val walletId = "wallet_collision"

        // Initial registration
        AuthHandleRegistry.register(
            sessionId = sessionId,
            expiresAtMs = expiresAt,
            keyId = keyId,
            operation = AuthOperation.SIGN,
            intentFingerprint = "fp",
            walletId = walletId,
            issuedAtMs = now,
            authenticatorType = "ADVERSARIAL_TEST"
        )

        // Re-register active session -> IllegalStateException
        val exActive = assertThrows(IllegalStateException::class.java) {
            AuthHandleRegistry.register(
                sessionId = sessionId,
                expiresAtMs = expiresAt,
                keyId = keyId,
                operation = AuthOperation.SIGN,
                intentFingerprint = "fp",
                walletId = walletId,
                issuedAtMs = now,
                authenticatorType = "ADVERSARIAL_TEST"
            )
        }
        assertTrue(exActive.message!!.contains("already active"))

        // Consume session
        assertTrue(AuthHandleRegistry.consume(sessionId))

        // Re-register consumed session -> IllegalStateException
        val exConsumed = assertThrows(IllegalStateException::class.java) {
            AuthHandleRegistry.register(
                sessionId = sessionId,
                expiresAtMs = expiresAt,
                keyId = keyId,
                operation = AuthOperation.SIGN,
                intentFingerprint = "fp",
                walletId = walletId,
                issuedAtMs = now,
                authenticatorType = "ADVERSARIAL_TEST"
            )
        }
        assertTrue(exConsumed.message!!.contains("already consumed"))
    }

    @Test
    fun challenge9_multithreaded_adversarial_concurrent_consumption_race_condition() {
        val threadCount = 30
        val now = 800_000L
        val expiresAt = 850_000L
        val sessionId = "adv_session_race"
        val keyId = "key_race"
        val walletId = "wallet_race"
        val txFingerprint = "race_fp"

        val proofToken = ProofTokenVerifier.sign(
            keyId = keyId,
            operation = AuthOperation.SIGN,
            intentFingerprint = txFingerprint,
            sessionId = sessionId,
            nonce = "nonce_race",
            issuedAtMs = now,
            expiresAtMs = expiresAt,
            walletId = walletId,
            authenticatorType = "ADVERSARIAL_TEST"
        )

        val handle = PlatformAuthHandle(
            keyId = keyId,
            operation = AuthOperation.SIGN,
            intentFingerprint = txFingerprint,
            sessionId = sessionId,
            nonce = "nonce_race",
            issuedAtMs = now,
            expiresAtMs = expiresAt,
            proofToken = proofToken,
            walletId = walletId
        )

        val executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount)
        val latch = java.util.concurrent.CountDownLatch(1)
        val doneLatch = java.util.concurrent.CountDownLatch(threadCount)
        val successCount = java.util.concurrent.atomic.AtomicInteger(0)
        val failureCount = java.util.concurrent.atomic.AtomicInteger(0)

        for (i in 0 until threadCount) {
            executor.submit {
                try {
                    latch.await()
                    val res = AuthHandleRegistry.validateAndConsume(
                        handle = handle,
                        expectedKeyId = keyId,
                        expectedOperation = AuthOperation.SIGN,
                        expectedFingerprint = txFingerprint,
                        currentTimeMs = now + 1000L,
                        expectedWalletId = walletId
                    )
                    if (res is Result.Success) {
                        successCount.incrementAndGet()
                    } else {
                        failureCount.incrementAndGet()
                    }
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        latch.countDown()
        doneLatch.await(5, java.util.concurrent.TimeUnit.SECONDS)
        executor.shutdown()

        assertEquals("Exactly ONE thread must succeed in consuming the session", 1, successCount.get())
        assertEquals("All other threads must fail", threadCount - 1, failureCount.get())
        assertTrue("Session must be consumed", AuthHandleRegistry.isConsumed(sessionId))
        assertFalse("Session must no longer be registered", AuthHandleRegistry.isRegistered(sessionId, now + 1000L))
    }
}



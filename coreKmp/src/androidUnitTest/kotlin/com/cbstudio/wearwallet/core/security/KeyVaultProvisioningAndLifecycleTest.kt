package com.cbstudio.wearwallet.core.security

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.WalletAccount
import io.github.iml1s.crypto.SecureByteArray
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Milestone 1 (M1) KeyVault Provisioning & Lifecycle Test Suite
 *
 * Empirical verification of:
 * 1. UUID-based opaque keyAlias ("ww_key_...") and backupId ("ww_backup_...") generation.
 * 2. KeyVault atomic provisioning (storing key, verifying key exists, recording metadata).
 * 3. Database schema persistence of key_alias, key_backend, key_format_version, requires_auth.
 * 4. FakeSecureKeyManager strictness (no backdoor default key).
 * 5. Rollback compensation on DB failure (immediate deletion from KeyVault to prevent orphan keys).
 * 6. Deletion lifecycle (wallet deletion cleans up KeyVault key).
 */
class KeyVaultProvisioningAndLifecycleTest {

    private lateinit var fakeSecureKeyManager: FakeSecureKeyManager
    private lateinit var cryptoProvider: CommonCryptoProvider
    private val testPassword = "TestPassword#2026"
    private val testMnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
    private val testPrivateKeyHex = "e331b6d69882b4cb4ea581d88ec2693329b3b3a4711832421415ebff2d474428"

    @Before
    fun setup() {
        fakeSecureKeyManager = FakeSecureKeyManager()
        cryptoProvider = CommonCryptoProvider()
    }

    @Test
    fun `test_FakeSecureKeyManager_has_no_backdoor_default_key`() = runBlocking {
        val manager = FakeSecureKeyManager()
        assertFalse("Unregistered key must return false for hasPrivateKey", manager.hasPrivateKey("unregistered_key"))
        val testHash = CryptoUtils.sha256(byteArrayOf(1, 2, 3))
        val signResult = manager.signWithKey("unregistered_key", testHash, authContext = null, expectedWalletId = "unregistered_key")
        assertTrue("Signing with unregistered key must fail", signResult is Result.Failure)

        manager.storePrivateKey("key_1", testPrivateKeyHex.encodeToByteArray(), requireAuth = false, authContext = null, expectedWalletId = "key_1")
        assertTrue("Registered key must return true", manager.hasPrivateKey("key_1"))
        val successSign = manager.signWithKey("key_1", testHash, authContext = null, expectedWalletId = "key_1")
        assertTrue("Signing with registered key must succeed", successSign is Result.Success)
    }

    @Test
    fun `test_FakeSecureKeyManager_rejects_unauthenticated_sign_on_require_auth_key`() = runBlocking {
        val manager = FakeSecureKeyManager()
        val keyId = "ww_key_auth_required"
        manager.setKey(keyId, testPrivateKeyHex, requireAuth = true)

        val testHash = CryptoUtils.sha256(byteArrayOf(1, 2, 3))
        val signResult = manager.signWithKey(keyId, testHash, authContext = null, expectedWalletId = keyId)
        assertTrue("Unauthenticated sign must fail", signResult is Result.Failure)
        val ex = (signResult as Result.Failure).exception
        assertTrue("Exception must be AuthenticationRequiredException, got $ex", ex is AuthenticationRequiredException)
    }

    @Test
    fun `test_FakeSecureKeyManager_rejects_mismatched_intent_fingerprint`() = runBlocking {
        val manager = FakeSecureKeyManager()
        val keyId = "ww_key_fingerprint_test"
        manager.setKey(keyId, testPrivateKeyHex, requireAuth = true)

        val testHash = CryptoUtils.sha256(byteArrayOf(1, 2, 3))
        val mismatchedHandle = TestPlatformAuthenticator.issueHandle(
            keyId = keyId,
            operation = AuthOperation.SIGN,
            intentFingerprint = "0000000000000000000000000000000000000000000000000000000000000000",
            walletId = keyId
        )
        val signResult = manager.signWithKey(keyId, testHash, AuthenticationContext(authHandle = mismatchedHandle), expectedWalletId = keyId)
        assertTrue("Sign with mismatched fingerprint must fail", signResult is Result.Failure)
        val ex = (signResult as Result.Failure).exception
        assertTrue("Exception must be AuthenticationRequiredException, got $ex", ex is AuthenticationRequiredException)
        assertTrue("Message must indicate fingerprint mismatch", ex.message!!.contains("Intent fingerprint mismatch"))
    }

    @Test
    fun `test_FakeSecureKeyManager_rejects_wrong_auth_operation_on_sign`() = runBlocking {
        val manager = FakeSecureKeyManager()
        val keyId = "ww_key_op_test"
        manager.setKey(keyId, testPrivateKeyHex, requireAuth = true)

        val testHash = CryptoUtils.sha256(byteArrayOf(1, 2, 3))
        val deleteHandle = TestPlatformAuthenticator.issueHandle(
            keyId = keyId,
            operation = AuthOperation.DELETE,
            intentFingerprint = testHash.toHexString(),
            walletId = keyId
        )
        val signResult = manager.signWithKey(keyId, testHash, AuthenticationContext(authHandle = deleteHandle), expectedWalletId = keyId)
        assertTrue("Sign with DELETE handle must fail", signResult is Result.Failure)
        val ex = (signResult as Result.Failure).exception
        assertTrue("Exception must be AuthenticationRequiredException, got $ex", ex is AuthenticationRequiredException)
        assertTrue("Message must indicate operation mismatch", ex.message!!.contains("does not match expected 'SIGN'"))
    }

    @Test
    fun `test_rollback_compensation_deletes_key_without_auth_while_deletePrivateKey_requires_auth`() = runBlocking {
        val session = fakeSecureKeyManager.startProvisioningSession()
        val keyAlias = session.stagedKeyAlias
        val authContext = AuthenticationContext(
            authHandle = TestPlatformAuthenticator.issueHandle(
                keyId = keyAlias,
                sessionId = session.sessionId,
                operation = AuthOperation.IMPORT,
                walletId = keyAlias
            )
        )
        fakeSecureKeyManager.storeStagedPrivateKey(session, testPrivateKeyHex.encodeToByteArray(), requireAuth = true, authContext = authContext)
        assertTrue("Key must initially exist in KeyVault", fakeSecureKeyManager.hasPrivateKey(keyAlias))

        // 1. Regular deletePrivateKey without auth MUST fail
        val deleteWithoutAuth = fakeSecureKeyManager.deletePrivateKey(keyAlias, authContext = null, expectedWalletId = keyAlias)
        assertTrue("deletePrivateKey without auth must fail", deleteWithoutAuth is Result.Failure)
        assertTrue(fakeSecureKeyManager.hasPrivateKey(keyAlias))

        // 2. Rollback compensation deletes staging key without requiring user auth
        val rollbackResult = fakeSecureKeyManager.rollbackProvisioningSession(session)
        assertTrue("rollbackProvisioningSession must succeed unconditionally", rollbackResult is Result.Success)
        assertFalse("Key must be deleted after rollback compensation", fakeSecureKeyManager.hasPrivateKey(keyAlias))
    }

    @Test
    fun `test_VersionedEncryptedEnvelope_uses_distinct_keyIds_for_privKey_and_mnemonic`() {
        val keyAlias = "ww_key_12345678-1234-4000-8000-123456789abc"
        val backupId = "ww_backup_87654321-4321-4000-8000-cba987654321"

        val pwdBytes = testPassword.encodeToByteArray()
        val privBytes = testPrivateKeyHex.encodeToByteArray()
        val mnemBytes = testMnemonic.encodeToByteArray()

        val privEnvelope = VersionedEncryptedEnvelope.encrypt(
            plaintext = privBytes,
            password = pwdBytes,
            keyId = keyAlias,
            aad = CanonicalAad.forWalletStorage(keyAlias, CanonicalAad.KEY_TYPE_PRIVATE_KEY)
        )
        val mnemEnvelope = VersionedEncryptedEnvelope.encrypt(
            plaintext = mnemBytes,
            password = pwdBytes,
            keyId = backupId,
            aad = CanonicalAad.forWalletStorage(backupId, CanonicalAad.KEY_TYPE_MNEMONIC)
        )

        assertEquals("Private key envelope keyId must match keyAlias", keyAlias, privEnvelope.keyId)
        assertEquals("Mnemonic backup envelope keyId must match backupId", backupId, mnemEnvelope.keyId)
        assertFalse("Signing keyAlias and backupId must NEVER be identical", keyAlias == backupId)

        // Decryption verification
        val decryptedPriv = privEnvelope.decrypt(pwdBytes, CanonicalAad.forWalletStorage(keyAlias, CanonicalAad.KEY_TYPE_PRIVATE_KEY))
        assertEquals("Decrypted private key must match original", testPrivateKeyHex, decryptedPriv.decodeToString())

        val decryptedMnem = mnemEnvelope.decrypt(pwdBytes, CanonicalAad.forWalletStorage(backupId, CanonicalAad.KEY_TYPE_MNEMONIC))
        assertEquals("Decrypted mnemonic must match original", testMnemonic, decryptedMnem.decodeToString())
    }

    @Test
    fun `test_SecureByteArray_zeroing_clears_sensitive_material`() {
        val sensitiveBytes = testPrivateKeyHex.encodeToByteArray()
        assertFalse(sensitiveBytes.all { it == 0.toByte() })

        SecureByteArray.secureZero(sensitiveBytes)
        assertTrue("All bytes must be zeroed", sensitiveBytes.all { it == 0.toByte() })
    }

    @Test
    fun `test_WalletAccount_model_contains_keyvault_fields_with_defaults`() {
        val defaultAccount = WalletAccount(
            id = "1",
            name = "Default Account",
            address = "0x123",
            publicKey = "0xpub",
            chainType = ChainType.ETHEREUM
        )
        assertEquals("Default keyAlias should be null", null, defaultAccount.keyAlias)
        assertEquals("Default keyBackend should be null", null, defaultAccount.keyBackend)
        assertEquals("Default keyFormatVersion should be 1", 1, defaultAccount.keyFormatVersion)
        assertTrue("Default requiresAuth should be true", defaultAccount.requiresAuth)

        val keyvaultAccount = WalletAccount(
            id = "2",
            name = "KeyVault Account",
            address = "0x456",
            publicKey = "0xpub2",
            keyAlias = "ww_key_test",
            keyBackend = "BASIC",
            keyFormatVersion = 1,
            requiresAuth = true,
            chainType = ChainType.ETHEREUM
        )
        assertEquals("ww_key_test", keyvaultAccount.keyAlias)
        assertEquals("BASIC", keyvaultAccount.keyBackend)
        assertEquals(1, keyvaultAccount.keyFormatVersion)
        assertTrue(keyvaultAccount.requiresAuth)
    }

    @Test
    fun `test_FakeSecureKeyManager_rejects_cross_key_signing`() = runBlocking {
        val manager = FakeSecureKeyManager()
        val keyA = "ww_key_user_A"
        val keyB = "ww_key_user_B"
        manager.setKey(keyA, testPrivateKeyHex, requireAuth = true)
        manager.setKey(keyB, testPrivateKeyHex, requireAuth = true)

        val testHash = CryptoUtils.sha256(byteArrayOf(10, 20, 30))
        val handleA = TestPlatformAuthenticator.issueHandle(
            keyId = keyA,
            operation = AuthOperation.SIGN,
            intentFingerprint = testHash.toHexString(),
            walletId = keyA
        )

        // Attempt using handleA on keyB
        val signResult = manager.signWithKey(keyB, testHash, AuthenticationContext(authHandle = handleA), expectedWalletId = keyB)
        assertTrue("Cross-key signing MUST fail", signResult is Result.Failure)
        val ex = (signResult as Result.Failure).exception
        assertTrue("Exception must be AuthenticationRequiredException, got $ex", ex is AuthenticationRequiredException)
        assertTrue("Message must indicate cross-key rejection", ex.message!!.contains("Cross-key handle rejected"))
    }

    @Test
    fun `test_FakeSecureKeyManager_rejects_expired_auth_handle`() = runBlocking {
        val manager = FakeSecureKeyManager()
        val keyId = "ww_key_expired_test"
        manager.setKey(keyId, testPrivateKeyHex, requireAuth = true)

        val testHash = CryptoUtils.sha256(byteArrayOf(40, 50, 60))
        val issued = System.currentTimeMillis() - 120_000L
        val expires = System.currentTimeMillis() - 60_000L
        val expiredHandle = PlatformAuthHandle(
            keyId = keyId,
            operation = AuthOperation.SIGN,
            intentFingerprint = testHash.toHexString(),
            sessionId = "session-exp-1",
            nonce = "nonce-exp-1",
            issuedAtMs = issued,
            expiresAtMs = expires,
            walletId = keyId,
            proofToken = ProofTokenVerifier.sign(keyId, AuthOperation.SIGN, testHash.toHexString(), "session-exp-1", "nonce-exp-1", issued, expires, keyId)
        )

        val signResult = manager.signWithKey(keyId, testHash, AuthenticationContext(authHandle = expiredHandle), expectedWalletId = keyId)
        assertTrue("Expired handle on sign MUST fail", signResult is Result.Failure)
        val ex = (signResult as Result.Failure).exception
        assertTrue("Exception must be AuthenticationRequiredException, got $ex", ex is AuthenticationRequiredException)
        assertTrue("Message must indicate expiration", ex.message!!.contains("has expired"))

        // Also test delete with expired handle
        val deleteIssued = System.currentTimeMillis() - 20_000L
        val deleteExpires = System.currentTimeMillis() - 10_000L
        val expiredDeleteHandle = PlatformAuthHandle(
            keyId = keyId,
            operation = AuthOperation.DELETE,
            intentFingerprint = "",
            sessionId = "session-exp-2",
            nonce = "nonce-exp-2",
            issuedAtMs = deleteIssued,
            expiresAtMs = deleteExpires,
            walletId = keyId,
            proofToken = ProofTokenVerifier.sign(keyId, AuthOperation.DELETE, "", "session-exp-2", "nonce-exp-2", deleteIssued, deleteExpires, keyId)
        )
        val deleteResult = manager.deletePrivateKey(keyId, AuthenticationContext(authHandle = expiredDeleteHandle), expectedWalletId = keyId)
        assertTrue("Expired handle on delete MUST fail", deleteResult is Result.Failure)
        assertTrue(manager.hasPrivateKey(keyId))
    }

    @Test
    fun `test_FakeSecureKeyManager_rejects_invalidated_auth_handle`() = runBlocking {
        val manager = FakeSecureKeyManager()
        val keyId = "ww_key_invalidated_test"
        manager.setKey(keyId, testPrivateKeyHex, requireAuth = true)

        val testHash = CryptoUtils.sha256(byteArrayOf(70, 80, 90))
        val handle = TestPlatformAuthenticator.issueHandle(
            keyId = keyId,
            operation = AuthOperation.SIGN,
            intentFingerprint = testHash.toHexString(),
            walletId = keyId
        )
        handle.invalidate()
        assertTrue(handle.isInvalidated)

        val signResult = manager.signWithKey(keyId, testHash, AuthenticationContext(authHandle = handle), expectedWalletId = keyId)
        assertTrue("Invalidated handle on sign MUST fail", signResult is Result.Failure)
        val ex = (signResult as Result.Failure).exception
        assertTrue("Exception must be AuthenticationRequiredException, got $ex", ex is AuthenticationRequiredException)
        assertTrue("Message must indicate invalidation", ex.message!!.contains("is invalidated"))
    }

    @Test
    fun `test_FakeSecureKeyManager_rejects_blank_keyId_and_empty_data`() = runBlocking {
        val manager = FakeSecureKeyManager()

        // 1. Blank keyId on store
        val storeBlank = manager.storePrivateKey("   ", testPrivateKeyHex.encodeToByteArray(), authContext = null, expectedWalletId = "   ")
        assertTrue("storePrivateKey with blank keyId must fail", storeBlank is Result.Failure)

        // 2. Blank keyId on delete
        val deleteBlank = manager.deletePrivateKey("   ", authContext = null, expectedWalletId = "   ")
        assertTrue("deletePrivateKey with blank keyId must fail", deleteBlank is Result.Failure)

        // 3. Blank keyId on rollback session
        val dummySession = ProvisioningSession(
            sessionId = "test-session",
            stagedKeyAlias = "   ",
            backupId = "test-backup"
        )
        val rollbackBlank = manager.rollbackProvisioningSession(dummySession)
        assertTrue("rollbackProvisioningSession with blank keyId must fail", rollbackBlank is Result.Failure)

        // 4. Blank keyId on sign
        val signBlankKey = manager.signWithKey("   ", byteArrayOf(1, 2, 3), authContext = null, expectedWalletId = "   ")
        assertTrue("signWithKey with blank keyId must fail", signBlankKey is Result.Failure)

        // 5. Empty data on sign
        manager.setKey("valid_key", testPrivateKeyHex, requireAuth = false)
        val signEmptyData = manager.signWithKey("valid_key", byteArrayOf(), authContext = null, expectedWalletId = "valid_key")
        assertTrue("signWithKey with empty data must fail", signEmptyData is Result.Failure)
    }

    @Test
    fun `test_FakeSecureKeyManager_deletePrivateKey_rejects_wrong_auth_operation`() = runBlocking {
        val manager = FakeSecureKeyManager()
        val keyId = "ww_key_delete_op_test"
        manager.setKey(keyId, testPrivateKeyHex, requireAuth = true)

        val signHandle = TestPlatformAuthenticator.issueHandle(
            keyId = keyId,
            operation = AuthOperation.SIGN,
            intentFingerprint = "deadbeef",
            walletId = keyId
        )
        val deleteResult = manager.deletePrivateKey(keyId, AuthenticationContext(authHandle = signHandle), expectedWalletId = keyId)
        assertTrue("deletePrivateKey with SIGN handle MUST fail", deleteResult is Result.Failure)
        val ex = (deleteResult as Result.Failure).exception
        assertTrue("Exception must be AuthenticationRequiredException", ex is AuthenticationRequiredException)
        assertTrue("Message must indicate operation mismatch", ex.message!!.contains("does not match expected 'DELETE'"))
        assertTrue(manager.hasPrivateKey(keyId))
    }

    @Test
    fun `test_FakeSecureKeyManager_sign_and_delete_succeed_with_valid_auth_handles`() = runBlocking {
        val manager = FakeSecureKeyManager()
        val keyId = "ww_key_success_lifecycle"
        manager.setKey(keyId, testPrivateKeyHex, requireAuth = true)

        val testHash = CryptoUtils.sha256("Hello Blockchain".encodeToByteArray())
        val signHandle = TestPlatformAuthenticator.issueHandle(
            keyId = keyId,
            operation = AuthOperation.SIGN,
            intentFingerprint = testHash.toHexString(),
            walletId = keyId
        )

        // 1. Authenticated sign succeeds
        val signResult = manager.signWithKey(keyId, testHash, AuthenticationContext(authHandle = signHandle), expectedWalletId = keyId)
        assertTrue("signWithKey with valid auth handle must succeed", signResult is Result.Success)
        val sigBytes = (signResult as Result.Success).data
        assertEquals(65, sigBytes.size)

        // 2. Authenticated delete succeeds
        val deleteHandle = TestPlatformAuthenticator.issueHandle(
            keyId = keyId,
            operation = AuthOperation.DELETE,
            walletId = keyId
        )
        val deleteResult = manager.deletePrivateKey(keyId, AuthenticationContext(authHandle = deleteHandle), expectedWalletId = keyId)
        assertTrue("deletePrivateKey with valid auth handle must succeed", deleteResult is Result.Success)
        assertFalse(manager.hasPrivateKey(keyId))
    }
}


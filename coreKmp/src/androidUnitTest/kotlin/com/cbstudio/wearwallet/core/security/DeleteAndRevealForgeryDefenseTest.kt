package com.cbstudio.wearwallet.core.security

import app.cash.sqldelight.Query
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.database.DatabaseDriverFactory
import com.cbstudio.wearwallet.core.database.Wallet
import com.cbstudio.wearwallet.core.database.WalletQueries
import com.cbstudio.wearwallet.core.domain.model.WalletType
import com.cbstudio.wearwallet.core.domain.usecase.wallet.RealRevealMnemonicUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

/**
 * Empirical Security Test: Forgery Defense for Delete & Reveal Operations
 *
 * Verifies that:
 * 1. Forged proof tokens are rejected by RevealMnemonicUseCase and SecureKeyManager.
 * 2. Unregistered / replay-consumed auth handles are rejected (single-use defense).
 * 3. Operation mismatch (e.g. SIGN/EXPORT for REVEAL, or REVEAL for DELETE) is rejected.
 * 4. Cross-key auth handle attacks are strictly rejected.
 */
class DeleteAndRevealForgeryDefenseTest {

    private lateinit var databaseDriverFactory: DatabaseDriverFactory
    private lateinit var walletQueries: WalletQueries
    private lateinit var auditLogger: SecurityAuditLogger
    private lateinit var revealUseCase: RealRevealMnemonicUseCase
    private lateinit var fakeSecureKeyManager: FakeSecureKeyManager

    private val testWalletId = "100"
    private val testWalletIdLong = 100L
    private val testAddress = "0x71C7656EC7ab88b098defB751B7401B5f6d8976F"
    private val testKeyAlias = "ww_key_defense_100"
    private val testPassword = "VerySecurePassword!999"
    private val testMnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"

    private lateinit var validEnvelopeBase64: String

    @Before
    fun setup() {
        AuthHandleRegistry.clearForTesting()
        databaseDriverFactory = mock()
        walletQueries = mock()
        auditLogger = mock()
        fakeSecureKeyManager = FakeSecureKeyManager()

        val mnemBytes = testMnemonic.encodeToByteArray()
        val pwdBytes = testPassword.encodeToByteArray()
        val envelope = VersionedEncryptedEnvelope.encrypt(
            plaintext = mnemBytes,
            password = pwdBytes,
            keyId = testKeyAlias,
            aad = CanonicalAad.forWalletStorage(testKeyAlias, CanonicalAad.KEY_TYPE_MNEMONIC)
        )
        validEnvelopeBase64 = envelope.serializeToBase64()

        revealUseCase = RealRevealMnemonicUseCase(
            databaseDriverFactory = databaseDriverFactory,
            securityAuditLogger = auditLogger,
            customWalletQueries = walletQueries
        )

        mockWalletQuery()
    }

    private fun mockWalletQuery() {
        val mockWallet = Wallet(
            id = testWalletIdLong,
            name = "Test Wallet",
            address = testAddress,
            public_key = "0x04publickey",
            encrypted_private_key = "dummy_encrypted_key",
            encrypted_mnemonic = validEnvelopeBase64,
            derivation_path = "m/44'/60'/0'/0/0",
            chain_type = "ETHEREUM",
            wallet_type = WalletType.HOT_WALLET.name,
            is_active = 1L,
            is_watch_only = 0L,
            master_fingerprint = null,
            keystone_sign_request = null,
            keystone_sync_data = null,
            metadata = "{}",
            avatar_id = null,
            chain_id = 1L,
            key_alias = testKeyAlias,
            key_backend = "KEYSTORE",
            key_format_version = 1L,
            requires_auth = 1L,
            is_deletion_pending = 0L,
            created_at = 1000L,
            updated_at = 1000L
        )

        val queryMock = mock<Query<Wallet>>()
        whenever(queryMock.executeAsOneOrNull()).thenReturn(mockWallet)
        whenever(walletQueries.selectById(testWalletIdLong)).thenReturn(queryMock)
    }

    @Test
    fun test_reveal_mnemonic_rejects_forged_proof_token() = runBlocking {
        // Construct handle with forged HMAC proof token
        val forgedToken = "FORGED_PROOF_TOKEN_XYZ_12345"
        val forgedHandle = PlatformAuthHandle(
            keyId = testKeyAlias,
            operation = AuthOperation.REVEAL,
            intentFingerprint = "",
            sessionId = "forged_session_1",
            nonce = "random_nonce_1",
            issuedAtMs = System.currentTimeMillis(),
            expiresAtMs = System.currentTimeMillis() + 60_000L,
            walletId = testWalletId,
            proofToken = forgedToken
        )

        val result = revealUseCase.executeWithMnemonic(
            walletId = testWalletId,
            password = testPassword,
            authContext = AuthenticationContext(authHandle = forgedHandle)
        ) { chars -> chars.size }

        assertTrue("Reveal MUST fail with forged proof token", result is Result.Failure)
        assertTrue(
            "Exception must be AuthenticationRequiredException",
            (result as Result.Failure).exception is AuthenticationRequiredException
        )
    }

    @Test
    fun test_reveal_mnemonic_rejects_unregistered_session() = runBlocking {
        // Valid HMAC signature format, but session not registered in AuthHandleRegistry
        val now = System.currentTimeMillis()
        val validSigToken = ProofTokenVerifier.sign(
            keyId = testKeyAlias,
            operation = AuthOperation.REVEAL,
            intentFingerprint = "",
            sessionId = "unregistered_session_xyz",
            nonce = "nonce_xyz",
            issuedAtMs = now,
            expiresAtMs = now + 60_000L,
            walletId = testWalletId
        )
        // Clear the registry so this session is not registered in the process registry
        AuthHandleRegistry.clearForTesting()

        val unregisteredHandle = PlatformAuthHandle(
            keyId = testKeyAlias,
            operation = AuthOperation.REVEAL,
            intentFingerprint = "",
            sessionId = "unregistered_session_xyz",
            nonce = "nonce_xyz",
            issuedAtMs = now,
            expiresAtMs = now + 60_000L,
            walletId = testWalletId,
            proofToken = validSigToken
        )

        val result = revealUseCase.executeWithMnemonic(
            walletId = testWalletId,
            password = testPassword,
            authContext = AuthenticationContext(authHandle = unregisteredHandle)
        ) { chars -> chars.size }

        assertTrue("Reveal MUST fail with unregistered session", result is Result.Failure)
        assertTrue(
            "Exception must be AuthenticationRequiredException",
            (result as Result.Failure).exception is AuthenticationRequiredException
        )
    }

    @Test
    fun test_reveal_mnemonic_rejects_replay_consumed_handle() = runBlocking {
        val validHandle = TestPlatformAuthenticator.issueHandle(
            keyId = testKeyAlias,
            operation = AuthOperation.REVEAL,
            walletId = testWalletId
        )

        // First execution succeeds
        val firstResult = revealUseCase.executeWithMnemonic(
            walletId = testWalletId,
            password = testPassword,
            authContext = AuthenticationContext(authHandle = validHandle)
        ) { chars -> chars.size }
        assertTrue("First reveal execution must succeed", firstResult is Result.Success)

        // Replay attempt with same handle MUST be rejected
        val replayResult = revealUseCase.executeWithMnemonic(
            walletId = testWalletId,
            password = testPassword,
            authContext = AuthenticationContext(authHandle = validHandle)
        ) { chars -> chars.size }

        assertTrue("Replayed handle reveal MUST fail", replayResult is Result.Failure)
        assertTrue(
            "Replay exception must be AuthenticationRequiredException",
            (replayResult as Result.Failure).exception is AuthenticationRequiredException
        )
    }

    @Test
    fun test_reveal_mnemonic_rejects_operation_mismatch() = runBlocking {
        // Handle issued with SIGN operation instead of REVEAL
        val signHandle = TestPlatformAuthenticator.issueHandle(
            keyId = testKeyAlias,
            operation = AuthOperation.SIGN,
            walletId = testWalletId
        )

        val signResult = revealUseCase.executeWithMnemonic(
            walletId = testWalletId,
            password = testPassword,
            authContext = AuthenticationContext(authHandle = signHandle)
        ) { chars -> chars.size }

        assertTrue("Reveal MUST reject SIGN auth handle", signResult is Result.Failure)

        // Handle issued with DELETE operation instead of REVEAL
        val deleteHandle = TestPlatformAuthenticator.issueHandle(
            keyId = testKeyAlias,
            operation = AuthOperation.DELETE,
            walletId = testWalletId
        )

        val deleteResult = revealUseCase.executeWithMnemonic(
            walletId = testWalletId,
            password = testPassword,
            authContext = AuthenticationContext(authHandle = deleteHandle)
        ) { chars -> chars.size }

        assertTrue("Reveal MUST reject DELETE auth handle", deleteResult is Result.Failure)
    }

    @Test
    fun test_reveal_mnemonic_rejects_cross_key_handle() = runBlocking {
        val crossKeyHandle = TestPlatformAuthenticator.issueHandle(
            keyId = "ww_key_attacker_victim_mismatch",
            operation = AuthOperation.REVEAL,
            walletId = testWalletId
        )

        val result = revealUseCase.executeWithMnemonic(
            walletId = testWalletId,
            password = testPassword,
            authContext = AuthenticationContext(authHandle = crossKeyHandle)
        ) { chars -> chars.size }

        assertTrue("Reveal MUST reject cross-key auth handle", result is Result.Failure)
        assertTrue(
            "Exception must be AuthenticationRequiredException",
            (result as Result.Failure).exception is AuthenticationRequiredException
        )
    }

    @Test
    fun test_delete_key_rejects_forged_handle_and_operation_mismatch() = runBlocking {
        val testKey = "test_key_delete_defense"
        fakeSecureKeyManager.setKey(testKey, "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", requireAuth = true)

        // 1. Forged handle
        val forgedHandle = PlatformAuthHandle(
            keyId = testKey,
            operation = AuthOperation.DELETE,
            intentFingerprint = "",
            sessionId = "session_forged",
            nonce = "nonce_1",
            issuedAtMs = System.currentTimeMillis(),
            expiresAtMs = System.currentTimeMillis() + 60_000L,
            walletId = testKey,
            proofToken = "BAD_PROOF_TOKEN"
        )
        val forgedResult = fakeSecureKeyManager.deletePrivateKey(testKey, AuthenticationContext(authHandle = forgedHandle), expectedWalletId = testKey)
        assertTrue("Delete MUST fail with forged proof token", forgedResult is Result.Failure)
        assertTrue("Key must still exist after failed delete", fakeSecureKeyManager.hasPrivateKey(testKey))

        // 2. Operation mismatch (REVEAL handle for DELETE)
        val revealHandle = TestPlatformAuthenticator.issueHandle(
            keyId = testKey,
            operation = AuthOperation.REVEAL,
            walletId = testKey
        )
        val opMismatchResult = fakeSecureKeyManager.deletePrivateKey(testKey, AuthenticationContext(authHandle = revealHandle), expectedWalletId = testKey)
        assertTrue("Delete MUST fail with REVEAL auth handle", opMismatchResult is Result.Failure)
        assertTrue("Key must still exist after op mismatch delete", fakeSecureKeyManager.hasPrivateKey(testKey))

        // 3. Cross key handle
        val otherKeyHandle = TestPlatformAuthenticator.issueHandle(
            keyId = "other_key_xyz",
            operation = AuthOperation.DELETE,
            walletId = testKey
        )
        val crossKeyResult = fakeSecureKeyManager.deletePrivateKey(testKey, AuthenticationContext(authHandle = otherKeyHandle), expectedWalletId = testKey)
        assertTrue("Delete MUST fail with cross-key handle", crossKeyResult is Result.Failure)
        assertTrue("Key must still exist after cross-key delete", fakeSecureKeyManager.hasPrivateKey(testKey))

        // 4. Valid DELETE handle succeeds
        val validDeleteHandle = TestPlatformAuthenticator.issueHandle(
            keyId = testKey,
            operation = AuthOperation.DELETE,
            walletId = testKey
        )
        val validResult = fakeSecureKeyManager.deletePrivateKey(testKey, AuthenticationContext(authHandle = validDeleteHandle), expectedWalletId = testKey)
        assertTrue("Delete with valid DELETE handle MUST succeed", validResult is Result.Success)
        assertFalse("Key must be deleted", fakeSecureKeyManager.hasPrivateKey(testKey))
    }
}

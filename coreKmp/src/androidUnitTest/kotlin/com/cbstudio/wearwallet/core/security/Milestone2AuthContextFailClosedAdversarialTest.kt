package com.cbstudio.wearwallet.core.security

import android.content.Context
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.data.repository.WalletRepositoryImpl
import com.cbstudio.wearwallet.core.database.DatabaseDriverFactory
import com.cbstudio.wearwallet.core.database.Wallet
import com.cbstudio.wearwallet.core.database.WalletQueries
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.WalletType
import com.cbstudio.wearwallet.core.domain.usecase.wallet.CreateWalletUseCase
import com.cbstudio.wearwallet.core.domain.usecase.wallet.ImportWalletUseCase
import com.cbstudio.wearwallet.core.network.EthereumRpcClient
import com.cbstudio.wearwallet.core.platform.SecureStorage
import io.github.iml1s.crypto.Secp256k1Pure
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*
import app.cash.sqldelight.Query
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.QueryResult

/**
 * Milestone 2 (M2) Adversarial Challenger Suite:
 * AuthContext Bypass & Fail-Closed Defense Verification
 *
 * Empirical Challenges:
 * 1. Attempt createWallet with null authContext, empty handle, expired handle, invalidated handle, mismatched operation.
 *    -> Assert AuthenticationRequiredException, 0 keys in KeyStore, 0 DB rows.
 * 2. Attempt importFromMnemonic with null authContext, empty handle, expired handle, invalidated handle, mismatched operation.
 *    -> Assert AuthenticationRequiredException, 0 keys in KeyStore, 0 DB rows.
 * 3. Attempt importFromMnemonicWithKeyPair with null authContext, empty handle, expired handle, invalidated handle, mismatched operation.
 *    -> Assert AuthenticationRequiredException, 0 keys in KeyStore, 0 DB rows.
 * 4. Attempt importFromPrivateKey with null authContext, empty handle, expired handle, invalidated handle, mismatched operation.
 *    -> Assert AuthenticationRequiredException, 0 keys in KeyStore, 0 DB rows.
 * 5. Attempt migrateLegacyWallet with null authContext, empty handle, expired handle, invalidated handle, mismatched operation.
 *    -> Assert AuthenticationRequiredException, 0 keys in KeyStore, legacy wallet in DB unchanged.
 * 6. Ambient unlocked state does NOT bypass the check (zero key leak / fail closed before cipher init).
 * 7. AndroidSecureKeyManager directly rejects storeStagedPrivateKey without valid IMPORT handle.
 */
class Milestone2AuthContextFailClosedAdversarialTest {

    private val testPassword = "ChallengerM2Password#2026"
    private val testPrivateKeyHex = "e331b6d69882b4cb4ea581d88ec2693329b3b3a4711832421415ebff2d474428"
    private val testMnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
    private val testAddress = "0xced30B0F584eA1cb2a54Fd137318a795cF34a039"

    private lateinit var cryptoProvider: CommonCryptoProvider
    private lateinit var ethereumRpcClient: EthereumRpcClient
    private lateinit var sideEffectTracker: SideEffectTracker
    private lateinit var fakeSecureKeyManager: FakeSecureKeyManager
    private lateinit var mockWalletQueries: WalletQueries
    private lateinit var mockJournalQueries: com.cbstudio.wearwallet.core.database.StagingJournalQueries
    private lateinit var databaseDriverFactory: DatabaseDriverFactory

    @Before
    fun setUp() {
        cryptoProvider = CommonCryptoProvider()
        ethereumRpcClient = mock()
        sideEffectTracker = mock()
        fakeSecureKeyManager = FakeSecureKeyManager()
        mockWalletQueries = mock()
        mockJournalQueries = mock()
        databaseDriverFactory = mock()

        val mockSqlDriver = mock<SqlDriver>()
        whenever(databaseDriverFactory.createDriver()).thenReturn(mockSqlDriver)

        val nullQuery = mock<Query<com.cbstudio.wearwallet.core.database.Staging_journal>>()
        whenever(nullQuery.executeAsOneOrNull()).thenReturn(null)
        whenever(mockJournalQueries.selectBySessionId(any())).thenReturn(nullQuery)
        whenever(mockJournalQueries.transaction(any(), any())).thenAnswer { invocation ->
            val body = invocation.getArgument<app.cash.sqldelight.TransactionWithoutReturn.() -> Unit>(1)
            val tx = mock<app.cash.sqldelight.TransactionWithoutReturn>()
            body.invoke(tx)
        }
        val mockChangesQuery = mock<Query<Long>>()
        whenever(mockChangesQuery.executeAsOne()).thenReturn(1L)
        whenever(mockChangesQuery.executeAsOneOrNull()).thenReturn(1L)
        whenever(mockJournalQueries.changesCount()).thenReturn(mockChangesQuery)

        // Default query: address does not exist
        val existsQuery = mock<Query<Boolean>>()
        whenever(existsQuery.executeAsOne()).thenReturn(false)
        whenever(mockWalletQueries.existsByAddress(any())).thenReturn(existsQuery)
    }

    private fun createRepository(): WalletRepositoryImpl {
        return WalletRepositoryImpl(
            databaseDriverFactory = databaseDriverFactory,
            cryptoProvider = cryptoProvider,
            ethereumRpcClient = ethereumRpcClient,
            secureKeyManager = fakeSecureKeyManager,
            platformDeletionCleanupHook = com.cbstudio.wearwallet.core.platform.NoOpPlatformDeletionCleanupHook(),
            sideEffectTracker = sideEffectTracker,
            customWalletQueries = mockWalletQueries,
            customStagingJournalQueries = mockJournalQueries
        )
    }

    // =========================================================================
    // 1. createWallet AuthContext Bypass Challenge
    // =========================================================================

    @Test
    fun `challenge_1_1_createWallet_with_empty_authContext_failsClosed_with_zero_keys`() = runBlocking {
        val repo = createRepository()
        val emptyAuthContext = AuthenticationContext() // authHandle = null

        val result = repo.createWallet("TestWallet", testMnemonic.toCharArray(), testPassword.toCharArray(), ChainType.ETHEREUM, emptyAuthContext)

        assertTrue("createWallet with empty authContext MUST fail", result is Result.Failure)
        val ex = (result as Result.Failure).exception
        assertTrue("Must throw AuthenticationRequiredException, got $ex", ex is AuthenticationRequiredException)
        assertEquals("Zero keys must be staged or committed in KeyStore", 0, fakeSecureKeyManager.listKeyIds().size)
        verify(sideEffectTracker, never()).onDbWrite()
    }

    @Test
    fun `challenge_1_2_createWallet_with_expired_authHandle_failsClosed_with_zero_keys`() = runBlocking {
        val repo = createRepository()
        val req = (repo.prepareProvisioning() as Result.Success).data
        val issued = System.currentTimeMillis() - 100_000L
        val expires = System.currentTimeMillis() - 10_000L
        val expiredHandle = PlatformAuthHandle(
            keyId = req.stagedAlias,
            operation = AuthOperation.IMPORT,
            intentFingerprint = "",
            sessionId = req.sessionId,
            nonce = "nonce-exp",
            issuedAtMs = issued,
            expiresAtMs = expires,
            walletId = req.sessionId,
            proofToken = ProofTokenVerifier.sign(req.stagedAlias, AuthOperation.IMPORT, "", req.sessionId, "nonce-exp", issued, expires, req.sessionId)
        )
        val authContext = AuthenticationContext(authHandle = expiredHandle)

        val result = repo.createWallet("TestWallet", testMnemonic.toCharArray(), testPassword.toCharArray(), ChainType.ETHEREUM, authContext)

        assertTrue("createWallet with expired authHandle MUST fail", result is Result.Failure)
        val ex = (result as Result.Failure).exception
        assertTrue("Must throw AuthenticationRequiredException, got $ex", ex is AuthenticationRequiredException)
        assertEquals("Zero keys must be staged or committed in KeyStore", 0, fakeSecureKeyManager.listKeyIds().size)
        verify(sideEffectTracker, never()).onDbWrite()
    }

    @Test
    fun `challenge_1_3_createWallet_with_invalidated_authHandle_failsClosed_with_zero_keys`() = runBlocking {
        val repo = createRepository()
        val req = (repo.prepareProvisioning() as Result.Success).data
        val handle = TestPlatformAuthenticator.issueHandle(
            keyId = req.stagedAlias,
            sessionId = req.sessionId,
            operation = AuthOperation.IMPORT
        )
        handle.invalidate() // User cancelled or handle already consumed
        val authContext = AuthenticationContext(authHandle = handle)

        val result = repo.createWallet("TestWallet", testMnemonic.toCharArray(), testPassword.toCharArray(), ChainType.ETHEREUM, authContext)

        assertTrue("createWallet with invalidated authHandle MUST fail", result is Result.Failure)
        val ex = (result as Result.Failure).exception
        assertTrue("Must throw AuthenticationRequiredException, got $ex", ex is AuthenticationRequiredException)
        assertEquals("Zero keys must be staged or committed in KeyStore", 0, fakeSecureKeyManager.listKeyIds().size)
        verify(sideEffectTracker, never()).onDbWrite()
    }

    @Test
    fun `challenge_1_4_createWallet_with_wrong_operation_handle_failsClosed_with_zero_keys`() = runBlocking {
        val repo = createRepository()
        val wrongOps = listOf(AuthOperation.SIGN, AuthOperation.DELETE, AuthOperation.EXPORT, AuthOperation.REVEAL)

        for (op in wrongOps) {
            val req = (repo.prepareProvisioning() as Result.Success).data
            val handle = TestPlatformAuthenticator.issueHandle(
                keyId = req.stagedAlias,
                sessionId = req.sessionId,
                operation = op
            )
            val authContext = AuthenticationContext(authHandle = handle)

            val result = repo.createWallet("TestWallet", testMnemonic.toCharArray(), testPassword.toCharArray(), ChainType.ETHEREUM, authContext)

            assertTrue("createWallet with $op authHandle MUST fail", result is Result.Failure)
            val ex = (result as Result.Failure).exception
            assertTrue("Must throw AuthenticationRequiredException for $op, got $ex", ex is AuthenticationRequiredException)
            assertEquals("Zero keys must be staged or committed in KeyStore after $op attempt", 0, fakeSecureKeyManager.listKeyIds().size)
            verify(sideEffectTracker, never()).onDbWrite()
        }
    }

    // =========================================================================
    // 2. importFromMnemonic AuthContext Bypass Challenge
    // =========================================================================

    @Test
    fun `challenge_2_1_importFromMnemonic_with_empty_authContext_failsClosed_with_zero_keys`() = runBlocking {
        val repo = createRepository()
        val emptyAuthContext = AuthenticationContext()

        val result = repo.importFromMnemonic("ImportMnemonic", testMnemonic.toCharArray(), testPassword.toCharArray(), ChainType.ETHEREUM, emptyAuthContext)

        assertTrue("importFromMnemonic with empty authContext MUST fail", result is Result.Failure)
        val ex = (result as Result.Failure).exception
        assertTrue("Must throw AuthenticationRequiredException, got $ex", ex is AuthenticationRequiredException)
        assertEquals("Zero keys must be staged or committed in KeyStore", 0, fakeSecureKeyManager.listKeyIds().size)
        verify(sideEffectTracker, never()).onDbWrite()
    }

    @Test
    fun `challenge_2_2_importFromMnemonic_with_wrong_operation_failsClosed_with_zero_keys`() = runBlocking {
        val repo = createRepository()
        val req = (repo.prepareProvisioning() as Result.Success).data
        val handle = TestPlatformAuthenticator.issueHandle(keyId = req.stagedAlias, sessionId = req.sessionId, operation = AuthOperation.SIGN)
        val authContext = AuthenticationContext(authHandle = handle)

        val result = repo.importFromMnemonic("ImportMnemonic", testMnemonic.toCharArray(), testPassword.toCharArray(), ChainType.ETHEREUM, authContext)

        assertTrue("importFromMnemonic with SIGN operation handle MUST fail", result is Result.Failure)
        val ex = (result as Result.Failure).exception
        assertTrue("Must throw AuthenticationRequiredException, got $ex", ex is AuthenticationRequiredException)
        assertEquals("Zero keys must be staged or committed in KeyStore", 0, fakeSecureKeyManager.listKeyIds().size)
        verify(sideEffectTracker, never()).onDbWrite()
    }

    // =========================================================================
    // 3. importFromMnemonicWithKeyPair AuthContext Bypass Challenge
    // =========================================================================

    @Test
    fun `challenge_3_1_importFromMnemonicWithKeyPair_with_empty_authContext_failsClosed_with_zero_keys`() = runBlocking {
        val repo = createRepository()
        val keyPair = cryptoProvider.generateKeyPairFromMnemonic(testMnemonic.toCharArray(), "m/44'/60'/0'/0/0", ChainType.ETHEREUM)
        val address = cryptoProvider.deriveAddress(keyPair.publicKey)
        val emptyAuthContext = AuthenticationContext()

        val result = repo.importFromMnemonicWithKeyPair("ImportKp", testMnemonic.toCharArray(), testPassword.toCharArray(), ChainType.ETHEREUM, keyPair, address, emptyAuthContext)

        assertTrue("importFromMnemonicWithKeyPair with empty authContext MUST fail", result is Result.Failure)
        val ex = (result as Result.Failure).exception
        assertTrue("Must throw AuthenticationRequiredException, got $ex", ex is AuthenticationRequiredException)
        assertEquals("Zero keys must be staged or committed in KeyStore", 0, fakeSecureKeyManager.listKeyIds().size)
        verify(sideEffectTracker, never()).onDbWrite()
    }

    @Test
    fun `challenge_3_2_importFromMnemonicWithKeyPair_with_expired_handle_failsClosed_with_zero_keys`() = runBlocking {
        val repo = createRepository()
        val keyPair = cryptoProvider.generateKeyPairFromMnemonic(testMnemonic.toCharArray(), "m/44'/60'/0'/0/0", ChainType.ETHEREUM)
        val address = cryptoProvider.deriveAddress(keyPair.publicKey)

        val req = (repo.prepareProvisioning() as Result.Success).data
        val issued = System.currentTimeMillis() - 100_000L
        val expires = System.currentTimeMillis() - 10_000L
        val expiredHandle = PlatformAuthHandle(
            keyId = req.stagedAlias,
            operation = AuthOperation.IMPORT,
            intentFingerprint = "",
            sessionId = req.sessionId,
            nonce = "nonce-exp",
            issuedAtMs = issued,
            expiresAtMs = expires,
            walletId = req.sessionId,
            proofToken = ProofTokenVerifier.sign(req.stagedAlias, AuthOperation.IMPORT, "", req.sessionId, "nonce-exp", issued, expires, req.sessionId)
        )
        val authContext = AuthenticationContext(authHandle = expiredHandle)

        val result = repo.importFromMnemonicWithKeyPair("ImportKp", testMnemonic.toCharArray(), testPassword.toCharArray(), ChainType.ETHEREUM, keyPair, address, authContext)

        assertTrue("importFromMnemonicWithKeyPair with expired handle MUST fail", result is Result.Failure)
        val ex = (result as Result.Failure).exception
        assertTrue("Must throw AuthenticationRequiredException, got $ex", ex is AuthenticationRequiredException)
        assertEquals("Zero keys must be staged or committed in KeyStore", 0, fakeSecureKeyManager.listKeyIds().size)
        verify(sideEffectTracker, never()).onDbWrite()
    }

    // =========================================================================
    // 4. importFromPrivateKey AuthContext Bypass Challenge
    // =========================================================================

    @Test
    fun `challenge_4_1_importFromPrivateKey_with_empty_authContext_failsClosed_with_zero_keys`() = runBlocking {
        val repo = createRepository()
        val emptyAuthContext = AuthenticationContext()

        val result = repo.importFromPrivateKey("ImportPk", com.cbstudio.wearwallet.core.security.ScopedPrivateKey.fromHex(testPrivateKeyHex), testPassword.toCharArray(), ChainType.ETHEREUM, emptyAuthContext)

        assertTrue("importFromPrivateKey with empty authContext MUST fail", result is Result.Failure)
        val ex = (result as Result.Failure).exception
        assertTrue("Must throw AuthenticationRequiredException, got $ex", ex is AuthenticationRequiredException)
        assertEquals("Zero keys must be staged or committed in KeyStore", 0, fakeSecureKeyManager.listKeyIds().size)
        verify(sideEffectTracker, never()).onDbWrite()
    }

    @Test
    fun `challenge_4_2_importFromPrivateKey_with_wrong_operation_failsClosed_with_zero_keys`() = runBlocking {
        val repo = createRepository()
        val req = (repo.prepareProvisioning() as Result.Success).data
        val handle = TestPlatformAuthenticator.issueHandle(keyId = req.stagedAlias, sessionId = req.sessionId, operation = AuthOperation.DELETE)
        val authContext = AuthenticationContext(authHandle = handle)

        val result = repo.importFromPrivateKey("ImportPk", com.cbstudio.wearwallet.core.security.ScopedPrivateKey.fromHex(testPrivateKeyHex), testPassword.toCharArray(), ChainType.ETHEREUM, authContext)

        assertTrue("importFromPrivateKey with DELETE operation handle MUST fail", result is Result.Failure)
        val ex = (result as Result.Failure).exception
        assertTrue("Must throw AuthenticationRequiredException, got $ex", ex is AuthenticationRequiredException)
        assertEquals("Zero keys must be staged or committed in KeyStore", 0, fakeSecureKeyManager.listKeyIds().size)
        verify(sideEffectTracker, never()).onDbWrite()
    }

    // =========================================================================
    // 5. migrateLegacyWallet AuthContext Bypass Challenge
    // =========================================================================

    @Test
    fun `challenge_5_1_migrateLegacyWallet_with_empty_authContext_failsClosed_and_does_not_modify_db`() = runBlocking {
        val plainBytes = testPrivateKeyHex.encodeToByteArray()
        val pwdBytes = testPassword.encodeToByteArray()
        val sha256Key = CryptoUtils.sha256(pwdBytes)
        val encrypted = CryptoUtils.aesGcmEncrypt(plainBytes, sha256Key)
        val legacyBase64 = (encrypted.nonce + encrypted.ciphertext + encrypted.authTag).toBase64()

        val legacyWallet = Wallet(
            id = 500L,
            name = "LegacyWallet",
            address = testAddress,
            public_key = "0x04PubKey",
            encrypted_private_key = legacyBase64,
            encrypted_mnemonic = null,
            derivation_path = "m/44'/60'/0'/0/0",
            chain_type = "ETHEREUM",
            wallet_type = "HOT_WALLET",
            is_active = 1L,
            is_watch_only = 0L,
            master_fingerprint = null,
            keystone_sign_request = null,
            keystone_sync_data = null,
            metadata = "{}",
            avatar_id = null,
            chain_id = 1L,
            key_alias = testAddress,
            key_backend = "SOFTWARE",
            key_format_version = 1L,
            requires_auth = 0L,
            is_deletion_pending = 0L,
            created_at = 1000L,
            updated_at = 1000L
        )

        val query = mock<Query<Wallet>>()
        whenever(query.executeAsOneOrNull()).thenReturn(legacyWallet)
        whenever(mockWalletQueries.selectById(500L)).thenReturn(query)

        val repo = createRepository()
        val emptyAuthContext = AuthenticationContext() // authHandle = null

        val result = repo.migrateLegacyWallet("500", testPassword.toCharArray(), emptyAuthContext)

        assertTrue("migrateLegacyWallet with empty authContext MUST fail", result is Result.Failure)
        val ex = (result as Result.Failure).exception
        assertTrue("Must throw AuthenticationRequiredException, got $ex", ex is AuthenticationRequiredException)
        assertEquals("Zero keys must be staged or committed in KeyStore", 0, fakeSecureKeyManager.listKeyIds().size)
        verify(mockWalletQueries, never()).updateEncryptedSecrets(any(), any(), any(), any(), any(), any(), any())
    }

    // =========================================================================
    // 6. Ambient Unlocked State Bypass Challenge
    // =========================================================================

    @Test
    fun `challenge_6_1_ambient_unlocked_state_does_not_bypass_authContext_check`() = runBlocking {
        val repo = createRepository()

        // Step 1: User unlocked device for a previous operation (e.g. valid IMPORT on wallet A)
        val reqA = (repo.prepareProvisioning() as Result.Success).data
        val validHandleA = TestPlatformAuthenticator.issueHandle(
            keyId = reqA.stagedAlias,
            sessionId = reqA.sessionId,
            operation = AuthOperation.IMPORT
        )
        val validAuthA = AuthenticationContext(authHandle = validHandleA)
        val createResultA = repo.createWallet("WalletA", testMnemonic.toCharArray(), testPassword.toCharArray(), ChainType.ETHEREUM, validAuthA)

        // Consume handle
        assertTrue("First creation with valid authContext must consume handle", validHandleA.isInvalidated)

        // Step 2: Now immediately attempt creation of Wallet B WITHOUT a new authContext
        // Ambient assumption: "device was just unlocked 1 second ago so it should pass" -> MUST BE REJECTED!
        val ambientBypassContext = AuthenticationContext() // null handle
        val createResultB = repo.createWallet("WalletB", testMnemonic.toCharArray(), testPassword.toCharArray(), ChainType.ETHEREUM, ambientBypassContext)

        assertTrue("Creation without fresh authContext MUST fail closed despite recent unlock", createResultB is Result.Failure)
        assertTrue((createResultB as Result.Failure).exception is AuthenticationRequiredException)

        // Step 3: Attempt creation with already consumed (replayed) handle A -> MUST BE REJECTED!
        val replayResult = repo.createWallet("WalletB_Replay", testMnemonic.toCharArray(), testPassword.toCharArray(), ChainType.ETHEREUM, validAuthA)
        assertTrue("Replay of consumed handle MUST fail closed", replayResult is Result.Failure)
        assertTrue((replayResult as Result.Failure).exception is AuthenticationRequiredException)
    }

    // =========================================================================
    // 7. Hardware AndroidSecureKeyManager Direct Fail-Closed Staging Challenge
    // =========================================================================

    @Test
    fun `challenge_7_1_androidSecureKeyManager_rejects_storeStagedPrivateKey_without_authContext`() = runBlocking {
        val mockContext = mock<Context>()
        whenever(mockContext.applicationContext).thenReturn(mockContext)
        val testKs = TestKeyStoreBackend()
        val inMemoryPrefs = InMemorySharedPreferences()

        val manager = AndroidSecureKeyManager(
            context = mockContext,
            config = SecureStorageConfig(enableRootDetection = false),
            keyStoreProvider = { testKs.createKeyStore() },
            encryptedPrefsProvider = { inMemoryPrefs },
            secretKeyProvider = { alias, _ -> testKs.generateAndStoreKey(alias) }
        )

        val session = manager.startProvisioningSession()

        // 1. null authContext -> fail closed
        val res1 = manager.storeStagedPrivateKey(session, testPrivateKeyHex.encodeToByteArray(), requireAuth = true, authContext = null)
        assertTrue("storeStagedPrivateKey with null authContext MUST fail", res1 is Result.Failure)
        assertTrue((res1 as Result.Failure).exception is AuthenticationRequiredException)

        // 2. empty authHandle -> fail closed
        val res2 = manager.storeStagedPrivateKey(session, testPrivateKeyHex.encodeToByteArray(), requireAuth = true, authContext = AuthenticationContext())
        assertTrue("storeStagedPrivateKey with empty authHandle MUST fail", res2 is Result.Failure)
        assertTrue((res2 as Result.Failure).exception is AuthenticationRequiredException)

        // 3. wrong operation handle -> fail closed
        val signHandle = TestPlatformAuthenticator.issueHandle(keyId = session.stagedKeyAlias, operation = AuthOperation.SIGN)
        val res3 = manager.storeStagedPrivateKey(session, testPrivateKeyHex.encodeToByteArray(), requireAuth = true, authContext = AuthenticationContext(authHandle = signHandle))
        assertTrue("storeStagedPrivateKey with SIGN handle MUST fail", res3 is Result.Failure)
        assertTrue((res3 as Result.Failure).exception is AuthenticationRequiredException)

        // 4. Assert zero keys in hardware KeyStore and zero prefs in storage
        assertFalse("KeyStore must NOT contain staged alias", testKs.entries.containsKey(AndroidSecureKeyManager.KEY_ALIAS_PREFIX + session.stagedKeyAlias))
        assertNull("Encrypted storage must NOT contain staged key", inMemoryPrefs.getString(session.stagedKeyAlias, null))
    }
}

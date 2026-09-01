package com.cbstudio.wearwallet.core.security

import android.content.Context
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.data.repository.WalletRepositoryImpl
import com.cbstudio.wearwallet.core.database.DatabaseDriverFactory
import com.cbstudio.wearwallet.core.database.WalletQueries
import com.cbstudio.wearwallet.core.domain.model.ChainType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import app.cash.sqldelight.Query
import app.cash.sqldelight.db.SqlDriver
import java.util.concurrent.atomic.AtomicInteger

/**
 * Milestone 2 (M2) Adversarial Challenger Suite:
 * P1-5 Provisioning & Magic Key Challenge
 *
 * Empirical Challenges:
 * 1. Magic Keys Rejection:
 *    - Calling storeStagedPrivateKey or storePrivateKey with magic keys
 *      ("IMPORT_PROVISIONING", "wallet_creation", "*", "temp_*") is completely rejected.
 * 2. Mismatched Session & Alias Fail-Closed:
 *    - Mismatched sessionId or mismatched stagedAlias fails closed with zero key leaks.
 * 3. 1-Handle-1-Key Single-Use Binding:
 *    - 1 handle binds to exactly 1 key in 1 session.
 *    - Replay across keys or sessions is strictly rejected.
 *    - Concurrent multi-thread race on single handle yields exactly 1 success and N-1 failures.
 */
class Milestone2ProvisioningMagicKeyAdversarialTest {

    private val testPrivateKeyHex = "e331b6d69882b4cb4ea581d88ec2693329b3b3a4711832421415ebff2d474428"
    private val testPassword = "AdversarialPassword#2026"
    private val testMnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"

    private lateinit var fakeSecureKeyManager: FakeSecureKeyManager
    private lateinit var mockContext: Context
    private lateinit var testKeyStoreBackend: TestKeyStoreBackend
    private lateinit var inMemoryPrefs: InMemorySharedPreferences
    private lateinit var androidKeyManager: AndroidSecureKeyManager

    @Before
    fun setUp() {
        AuthHandleRegistry.clearForTesting()
        fakeSecureKeyManager = FakeSecureKeyManager()

        mockContext = mock()
        testKeyStoreBackend = TestKeyStoreBackend()
        inMemoryPrefs = InMemorySharedPreferences()

        androidKeyManager = AndroidSecureKeyManager(
            context = mockContext,
            config = SecureStorageConfig(enableRootDetection = false),
            keyStoreProvider = { testKeyStoreBackend.createKeyStore() },
            encryptedPrefsProvider = { inMemoryPrefs },
            secretKeyProvider = { alias, _ ->
                testKeyStoreBackend.generateAndStoreKey(AndroidSecureKeyManager.KEY_ALIAS_PREFIX + alias)
            }
        )
    }

    // =========================================================================
    // 1. Magic Keys Rejection Challenges
    // =========================================================================

    @Test
    fun `challenge_1_1_storeStagedPrivateKey_rejects_magic_key_IMPORT_PROVISIONING`() = runTest {
        val session = fakeSecureKeyManager.startProvisioningSession()
        val legitStagedAlias = session.stagedKeyAlias

        // Attacker attempts to use magic key "IMPORT_PROVISIONING"
        val magicHandle = TestPlatformAuthenticator.issueHandle(
            keyId = "IMPORT_PROVISIONING",
            sessionId = session.sessionId,
            operation = AuthOperation.IMPORT
        )

        val result = fakeSecureKeyManager.storeStagedPrivateKey(
            session = session,
            privateKey = testPrivateKeyHex.encodeToByteArray(),
            requireAuth = true,
            authContext = AuthenticationContext(authHandle = magicHandle)
        )

        assertTrue("storeStagedPrivateKey with magic key 'IMPORT_PROVISIONING' MUST fail", result is Result.Failure)
        val ex = (result as Result.Failure).exception
        assertTrue("Exception must be AuthenticationRequiredException", ex is AuthenticationRequiredException)
        assertTrue("Message must indicate cross-key rejection", ex.message!!.contains("Cross-key handle rejected"))
        assertFalse("Key must NOT be stored in KeyVault", fakeSecureKeyManager.hasPrivateKey(legitStagedAlias))
    }

    @Test
    fun `challenge_1_2_storeStagedPrivateKey_rejects_magic_key_wallet_creation`() = runTest {
        val session = fakeSecureKeyManager.startProvisioningSession()
        val legitStagedAlias = session.stagedKeyAlias

        // Attacker attempts to use magic key "wallet_creation"
        val magicHandle = TestPlatformAuthenticator.issueHandle(
            keyId = "wallet_creation",
            sessionId = session.sessionId,
            operation = AuthOperation.IMPORT
        )

        val result = fakeSecureKeyManager.storeStagedPrivateKey(
            session = session,
            privateKey = testPrivateKeyHex.encodeToByteArray(),
            requireAuth = true,
            authContext = AuthenticationContext(authHandle = magicHandle)
        )

        assertTrue("storeStagedPrivateKey with magic key 'wallet_creation' MUST fail", result is Result.Failure)
        val ex = (result as Result.Failure).exception
        assertTrue("Exception must be AuthenticationRequiredException", ex is AuthenticationRequiredException)
        assertTrue("Message must indicate cross-key rejection", ex.message!!.contains("Cross-key handle rejected"))
        assertFalse("Key must NOT be stored in KeyVault", fakeSecureKeyManager.hasPrivateKey(legitStagedAlias))
    }

    @Test
    fun `challenge_1_3_storeStagedPrivateKey_rejects_magic_key_wildcard_star`() = runTest {
        val session = fakeSecureKeyManager.startProvisioningSession()
        val legitStagedAlias = session.stagedKeyAlias

        // Attacker attempts to use wildcard "*"
        val wildcardHandle = TestPlatformAuthenticator.issueHandle(
            keyId = "*",
            sessionId = session.sessionId,
            operation = AuthOperation.IMPORT
        )

        val result = fakeSecureKeyManager.storeStagedPrivateKey(
            session = session,
            privateKey = testPrivateKeyHex.encodeToByteArray(),
            requireAuth = true,
            authContext = AuthenticationContext(authHandle = wildcardHandle)
        )

        assertTrue("storeStagedPrivateKey with wildcard '*' MUST fail", result is Result.Failure)
        val ex = (result as Result.Failure).exception
        assertTrue("Exception must be AuthenticationRequiredException", ex is AuthenticationRequiredException)
        assertTrue("Message must indicate cross-key rejection", ex.message!!.contains("Cross-key handle rejected"))
        assertFalse("Key must NOT be stored in KeyVault", fakeSecureKeyManager.hasPrivateKey(legitStagedAlias))
    }

    @Test
    fun `challenge_1_4_storeStagedPrivateKey_rejects_magic_key_temp_prefix`() = runTest {
        val session = fakeSecureKeyManager.startProvisioningSession()
        val legitStagedAlias = session.stagedKeyAlias

        // Attacker attempts to use "temp_wallet_key_12345"
        val tempHandle = TestPlatformAuthenticator.issueHandle(
            keyId = "temp_wallet_key_12345",
            sessionId = session.sessionId,
            operation = AuthOperation.IMPORT
        )

        val result = fakeSecureKeyManager.storeStagedPrivateKey(
            session = session,
            privateKey = testPrivateKeyHex.encodeToByteArray(),
            requireAuth = true,
            authContext = AuthenticationContext(authHandle = tempHandle)
        )

        assertTrue("storeStagedPrivateKey with 'temp_*' MUST fail", result is Result.Failure)
        val ex = (result as Result.Failure).exception
        assertTrue("Exception must be AuthenticationRequiredException", ex is AuthenticationRequiredException)
        assertTrue("Message must indicate cross-key rejection", ex.message!!.contains("Cross-key handle rejected"))
        assertFalse("Key must NOT be stored in KeyVault", fakeSecureKeyManager.hasPrivateKey(legitStagedAlias))
    }

    @Test
    fun `challenge_1_5_androidKeyManager_storeStagedPrivateKey_rejects_all_magic_keys`() = runTest {
        val magicKeys = listOf("IMPORT_PROVISIONING", "wallet_creation", "*", "temp_1", "temp_*")
        for (magic in magicKeys) {
            val session = androidKeyManager.startProvisioningSession()
            val legitAlias = session.stagedKeyAlias
            val handle = TestPlatformAuthenticator.issueHandle(
                keyId = magic,
                sessionId = session.sessionId,
                operation = AuthOperation.IMPORT
            )
            val result = androidKeyManager.storeStagedPrivateKey(
                session = session,
                privateKey = testPrivateKeyHex.encodeToByteArray(),
                requireAuth = true,
                authContext = AuthenticationContext(authHandle = handle)
            )
            assertTrue("AndroidKeyManager MUST reject magic key '$magic'", result is Result.Failure)
            val ex = (result as Result.Failure).exception
            assertTrue("Exception must be AuthenticationRequiredException", ex is AuthenticationRequiredException)
            assertFalse("Key must NOT exist in KeyStore", androidKeyManager.hasPrivateKey(legitAlias))
        }
    }

    // =========================================================================
    // 2. Mismatched Session & Staged Alias Fail-Closed Challenges
    // =========================================================================

    @Test
    fun `challenge_2_1_storeStagedPrivateKey_failsClosed_on_mismatched_sessionId`() = runTest {
        val session = fakeSecureKeyManager.startProvisioningSession()
        val legitStagedAlias = session.stagedKeyAlias

        // Handle issued with different sessionId
        val mismatchedSessionHandle = TestPlatformAuthenticator.issueHandle(
            keyId = legitStagedAlias,
            sessionId = "ww_sess_forged_attacker_session",
            operation = AuthOperation.IMPORT
        )

        val result = fakeSecureKeyManager.storeStagedPrivateKey(
            session = session,
            privateKey = testPrivateKeyHex.encodeToByteArray(),
            requireAuth = true,
            authContext = AuthenticationContext(authHandle = mismatchedSessionHandle)
        )

        assertTrue("storeStagedPrivateKey with mismatched sessionId MUST fail", result is Result.Failure)
        val ex = (result as Result.Failure).exception
        assertTrue("Exception must be AuthenticationRequiredException", ex is AuthenticationRequiredException)
        assertTrue("Message must indicate session mismatch", ex.message!!.contains("Session mismatch"))
        assertFalse("Key must NOT be stored in KeyVault", fakeSecureKeyManager.hasPrivateKey(legitStagedAlias))
    }

    @Test
    fun `challenge_2_2_storeStagedPrivateKey_failsClosed_on_empty_sessionId`() = runTest {
        val session = fakeSecureKeyManager.startProvisioningSession()
        val legitStagedAlias = session.stagedKeyAlias

        val emptySessionHandle = TestPlatformAuthenticator.issueHandle(
            keyId = legitStagedAlias,
            sessionId = "",
            operation = AuthOperation.IMPORT
        )

        val result = fakeSecureKeyManager.storeStagedPrivateKey(
            session = session,
            privateKey = testPrivateKeyHex.encodeToByteArray(),
            requireAuth = true,
            authContext = AuthenticationContext(authHandle = emptySessionHandle)
        )

        assertTrue("storeStagedPrivateKey with blank sessionId MUST fail", result is Result.Failure)
        val ex = (result as Result.Failure).exception
        assertTrue("Exception must be AuthenticationRequiredException", ex is AuthenticationRequiredException)
        assertFalse("Key must NOT be stored in KeyVault", fakeSecureKeyManager.hasPrivateKey(legitStagedAlias))
    }

    @Test
    fun `challenge_2_3_storeStagedPrivateKey_failsClosed_on_mismatched_stagedAlias`() = runTest {
        val session = fakeSecureKeyManager.startProvisioningSession()

        // Handle issued with different keyId
        val mismatchedAliasHandle = TestPlatformAuthenticator.issueHandle(
            keyId = "ww_key_other_target_victim",
            sessionId = session.sessionId,
            operation = AuthOperation.IMPORT
        )

        val result = fakeSecureKeyManager.storeStagedPrivateKey(
            session = session,
            privateKey = testPrivateKeyHex.encodeToByteArray(),
            requireAuth = true,
            authContext = AuthenticationContext(authHandle = mismatchedAliasHandle)
        )

        assertTrue("storeStagedPrivateKey with mismatched keyId MUST fail", result is Result.Failure)
        val ex = (result as Result.Failure).exception
        assertTrue("Exception must be AuthenticationRequiredException", ex is AuthenticationRequiredException)
        assertTrue("Message must indicate cross-key rejection", ex.message!!.contains("Cross-key handle rejected"))
        assertFalse("Target victim key must NOT be created", fakeSecureKeyManager.hasPrivateKey("ww_key_other_target_victim"))
        assertFalse("Staged alias must NOT be created", fakeSecureKeyManager.hasPrivateKey(session.stagedKeyAlias))
    }

    @Test
    fun `challenge_2_4_androidKeyManager_failsClosed_on_mismatched_session_and_alias`() = runTest {
        val session = androidKeyManager.startProvisioningSession()

        // Mismatched session
        val mismatchedSess = TestPlatformAuthenticator.issueHandle(
            keyId = session.stagedKeyAlias,
            sessionId = "ww_sess_mismatched_12345",
            operation = AuthOperation.IMPORT
        )
        val res1 = androidKeyManager.storeStagedPrivateKey(
            session = session,
            privateKey = testPrivateKeyHex.encodeToByteArray(),
            requireAuth = true,
            authContext = AuthenticationContext(authHandle = mismatchedSess)
        )
        assertTrue("Mismatched session MUST fail on AndroidKeyManager", res1 is Result.Failure)
        assertFalse(androidKeyManager.hasPrivateKey(session.stagedKeyAlias))

        // Mismatched alias
        val mismatchedAlias = TestPlatformAuthenticator.issueHandle(
            keyId = "ww_key_forged_alias",
            sessionId = session.sessionId,
            operation = AuthOperation.IMPORT
        )
        val res2 = androidKeyManager.storeStagedPrivateKey(
            session = session,
            privateKey = testPrivateKeyHex.encodeToByteArray(),
            requireAuth = true,
            authContext = AuthenticationContext(authHandle = mismatchedAlias)
        )
        assertTrue("Mismatched alias MUST fail on AndroidKeyManager", res2 is Result.Failure)
        assertFalse(androidKeyManager.hasPrivateKey("ww_key_forged_alias"))
        assertFalse(androidKeyManager.hasPrivateKey(session.stagedKeyAlias))
    }

    // =========================================================================
    // 3. 1-Handle-1-Key Single-Use Binding Challenges
    // =========================================================================

    @Test
    fun `challenge_3_1_1_handle_cannot_be_reused_for_second_key`() = runTest {
        val session1 = fakeSecureKeyManager.startProvisioningSession()
        val session2 = fakeSecureKeyManager.startProvisioningSession()

        val handle1 = TestPlatformAuthenticator.issueHandle(
            keyId = session1.stagedKeyAlias,
            sessionId = session1.sessionId,
            operation = AuthOperation.IMPORT
        )

        // First use: store key 1 in session 1 -> SUCCEEDS
        val result1 = fakeSecureKeyManager.storeStagedPrivateKey(
            session = session1,
            privateKey = testPrivateKeyHex.encodeToByteArray(),
            requireAuth = true,
            authContext = AuthenticationContext(authHandle = handle1)
        )
        assertTrue("First use must succeed", result1 is Result.Success)
        assertTrue("Key 1 must exist", fakeSecureKeyManager.hasPrivateKey(session1.stagedKeyAlias))

        // Replay attempt: reuse handle1 for key 2 in session 2 -> MUST FAIL CLOSED
        val result2 = fakeSecureKeyManager.storeStagedPrivateKey(
            session = session2,
            privateKey = testPrivateKeyHex.encodeToByteArray(),
            requireAuth = true,
            authContext = AuthenticationContext(authHandle = handle1)
        )
        assertTrue("Reusing handle1 for key 2 MUST fail closed", result2 is Result.Failure)
        val ex = (result2 as Result.Failure).exception
        assertTrue("Exception must be AuthenticationRequiredException", ex is AuthenticationRequiredException)
        assertFalse("Key 2 must NOT be stored", fakeSecureKeyManager.hasPrivateKey(session2.stagedKeyAlias))
    }

    @Test
    fun `challenge_3_2_1_handle_cannot_be_used_twice_for_same_key`() = runTest {
        val session = fakeSecureKeyManager.startProvisioningSession()

        val handle = TestPlatformAuthenticator.issueHandle(
            keyId = session.stagedKeyAlias,
            sessionId = session.sessionId,
            operation = AuthOperation.IMPORT
        )

        // First store succeeds
        val result1 = fakeSecureKeyManager.storeStagedPrivateKey(
            session = session,
            privateKey = testPrivateKeyHex.encodeToByteArray(),
            requireAuth = true,
            authContext = AuthenticationContext(authHandle = handle)
        )
        assertTrue("First use must succeed", result1 is Result.Success)

        // Second store using identical handle -> MUST FAIL because handle is consumed
        val result2 = fakeSecureKeyManager.storeStagedPrivateKey(
            session = session,
            privateKey = testPrivateKeyHex.encodeToByteArray(),
            requireAuth = true,
            authContext = AuthenticationContext(authHandle = handle)
        )
        assertTrue("Second store with consumed handle MUST fail closed", result2 is Result.Failure)
        val ex = (result2 as Result.Failure).exception
        assertTrue("Exception must be AuthenticationRequiredException", ex is AuthenticationRequiredException)
        assertTrue("Message must indicate consumed/invalidated session", ex.message!!.contains("invalidated or already consumed"))
    }

    @Test
    fun `challenge_3_3_concurrent_20_threads_with_single_handle_guarantees_exactly_1_success`() = runBlocking {
        val session = fakeSecureKeyManager.startProvisioningSession()

        val handle = TestPlatformAuthenticator.issueHandle(
            keyId = session.stagedKeyAlias,
            sessionId = session.sessionId,
            operation = AuthOperation.IMPORT
        )
        val authContext = AuthenticationContext(authHandle = handle)

        val successCount = AtomicInteger(0)
        val failureCount = AtomicInteger(0)

        val deferreds = (1..20).map {
            async(Dispatchers.Default) {
                val res = fakeSecureKeyManager.storeStagedPrivateKey(
                    session = session,
                    privateKey = testPrivateKeyHex.encodeToByteArray(),
                    requireAuth = true,
                    authContext = authContext
                )
                if (res is Result.Success) {
                    successCount.incrementAndGet()
                } else if (res is Result.Failure && res.exception is AuthenticationRequiredException) {
                    failureCount.incrementAndGet()
                }
            }
        }
        deferreds.awaitAll()

        assertEquals("Exactly 1 thread must succeed in consuming the handle", 1, successCount.get())
        assertEquals("Exactly 19 threads must fail closed with AuthenticationRequiredException", 19, failureCount.get())
        assertTrue("Key must exist in KeyVault", fakeSecureKeyManager.hasPrivateKey(session.stagedKeyAlias))
    }

    @Test
    fun `challenge_3_4_repository_createWallet_rejects_magic_keys_and_mismatched_sessions`() = runBlocking {
        val mockWalletQueries = mock<WalletQueries>()
        val mockJournalQueries = mock<com.cbstudio.wearwallet.core.database.StagingJournalQueries>()
        val databaseDriverFactory = mock<DatabaseDriverFactory>()
        val mockSqlDriver = mock<SqlDriver>()
        whenever(databaseDriverFactory.createDriver()).thenReturn(mockSqlDriver)

        val nullQuery = mock<Query<com.cbstudio.wearwallet.core.database.Staging_journal>>()
        whenever(nullQuery.executeAsOneOrNull()).thenReturn(null)
        whenever(mockJournalQueries.selectBySessionId(any())).thenReturn(nullQuery)

        val existsQuery = mock<Query<Boolean>>()
        whenever(existsQuery.executeAsOne()).thenReturn(false)
        whenever(mockWalletQueries.existsByAddress(any())).thenReturn(existsQuery)

        val repo = WalletRepositoryImpl(
            databaseDriverFactory = databaseDriverFactory,
            cryptoProvider = CommonCryptoProvider(),
            ethereumRpcClient = mock(),
            secureKeyManager = fakeSecureKeyManager,
            platformDeletionCleanupHook = com.cbstudio.wearwallet.core.platform.NoOpPlatformDeletionCleanupHook(),
            sideEffectTracker = mock(),
            customWalletQueries = mockWalletQueries,
            customStagingJournalQueries = mockJournalQueries
        )

        // 1. prepareProvisioning returns valid ProvisioningRequest
        val prepResult = repo.prepareProvisioning()
        assertTrue("prepareProvisioning must succeed", prepResult is Result.Success)
        val req = (prepResult as Result.Success).data
        assertTrue("stagedAlias must start with 'ww_key_'", req.stagedAlias.startsWith("ww_key_"))
        assertTrue("sessionId must start with 'ww_sess_'", req.sessionId.startsWith("ww_sess_"))

        // 2. Attacker uses magic key "IMPORT_PROVISIONING" in authContext
        val magicHandle = TestPlatformAuthenticator.issueHandle(
            keyId = "IMPORT_PROVISIONING",
            sessionId = req.sessionId,
            operation = AuthOperation.IMPORT
        )
        val magicRes = repo.createWallet("W1", testMnemonic.toCharArray(), testPassword.toCharArray(), ChainType.ETHEREUM, AuthenticationContext(authHandle = magicHandle))
        assertTrue("createWallet with magic key MUST fail", magicRes is Result.Failure)
        assertEquals("Zero keys in KeyVault", 0, fakeSecureKeyManager.listKeyIds().size)

        // 3. Attacker uses mismatched session
        val mismatchedSessHandle = TestPlatformAuthenticator.issueHandle(
            keyId = req.stagedAlias,
            sessionId = "ww_sess_forged_123",
            operation = AuthOperation.IMPORT
        )
        val sessRes = repo.createWallet("W1", testMnemonic.toCharArray(), testPassword.toCharArray(), ChainType.ETHEREUM, AuthenticationContext(authHandle = mismatchedSessHandle))
        assertTrue("createWallet with mismatched session MUST fail", sessRes is Result.Failure)
        assertEquals("Zero keys in KeyVault", 0, fakeSecureKeyManager.listKeyIds().size)
    }
}

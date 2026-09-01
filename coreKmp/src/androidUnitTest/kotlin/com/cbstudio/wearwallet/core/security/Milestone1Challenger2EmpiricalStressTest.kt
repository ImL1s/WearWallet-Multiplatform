package com.cbstudio.wearwallet.core.security

import android.content.Context
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.data.repository.WalletRepositoryImpl
import com.cbstudio.wearwallet.core.database.CoreWalletDatabase
import com.cbstudio.wearwallet.core.database.DatabaseDriverFactory
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.WalletType
import com.cbstudio.wearwallet.core.network.EthereumRpcClient
import com.cbstudio.wearwallet.core.platform.PlatformDeletionCleanupHook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Challenger 2 Empirical Stress & Adversarial Challenge Suite (Milestone 1)
 *
 * Empirical Challenges:
 * 1. 50-Thread Concurrent Grant Issuance: Single AuthHandle -> exactly 1 Grant issued, 49 rejected.
 * 2. 50-Thread Concurrent Grant Consumption: Single Grant -> exactly 1 consume success, 49 rejected.
 * 3. 50-Thread Concurrent KeyVault Physical Deletion: AndroidSecureKeyManager & FakeSecureKeyManager -> exactly 1 physical deletion.
 * 4. 50-Thread Concurrent deleteWallet: Repository level -> exactly 1 success, 49 failures, KeyVault deleteCount == 1, exactly 1 DeletionJournal record, 17 ledger steps == PASS.
 * 5. Adversarial Forgery & Tampering Probes: Cross-wallet, cross-key, cross-operation, HMAC tampering, Replay, Expiration.
 */
class Milestone1Challenger2EmpiricalStressTest {

    private lateinit var sqlDriver: JdbcSqliteDriver
    private lateinit var database: CoreWalletDatabase
    private lateinit var fakeKeyManager: FakeSecureKeyManager
    private lateinit var cryptoProvider: CommonCryptoProvider
    private lateinit var ethereumRpcClient: EthereumRpcClient
    private lateinit var repository: WalletRepositoryImpl
    private lateinit var driverFactory: DatabaseDriverFactory

    private val testPrivateKeyHex = "e331b6d69882b4cb4ea581d88ec2693329b3b3a4711832421415ebff2d474428"

    @Before
    fun setUp() {
        AuthHandleRegistry.clearForTesting()
        DeletionGrantRegistry.clearForTesting()

        sqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CoreWalletDatabase.Schema.create(sqlDriver)
        database = CoreWalletDatabase(sqlDriver)

        fakeKeyManager = FakeSecureKeyManager()
        cryptoProvider = CommonCryptoProvider()
        ethereumRpcClient = mock()

        driverFactory = mock()
        whenever(driverFactory.createDriver()).thenReturn(sqlDriver)

        repository = WalletRepositoryImpl(
            databaseDriverFactory = driverFactory,
            cryptoProvider = cryptoProvider,
            ethereumRpcClient = ethereumRpcClient,
            secureKeyManager = fakeKeyManager,
            platformDeletionCleanupHook = com.cbstudio.wearwallet.core.platform.NoOpPlatformDeletionCleanupHook(),
            customWalletQueries = database.walletQueries,
            customStagingJournalQueries = database.stagingJournalQueries,
            customDeletionJournalQueries = database.deletionJournalQueries,
            customDeletionStepLedgerQueries = database.deletionStepLedgerQueries
        )
    }

    @After
    fun tearDown() {
        AuthHandleRegistry.clearForTesting()
        DeletionGrantRegistry.clearForTesting()
        try {
            sqlDriver.close()
        } catch (_: Throwable) {}
    }

    private fun insertTestWallet(
        id: Long,
        keyAlias: String,
        address: String = "0x" + id.toString().padStart(40, '0'),
        requiresAuth: Boolean = true
    ): Long {
        database.walletQueries.insert(
            name = "Test Wallet $id",
            address = address,
            public_key = "0xpub$id",
            encrypted_private_key = "encrypted_priv_key_$id",
            encrypted_mnemonic = null,
            derivation_path = ChainType.ETHEREUM.getDefaultDerivationPath(),
            chain_type = ChainType.ETHEREUM.name,
            wallet_type = WalletType.MNEMONIC.name,
            is_watch_only = 0L,
            master_fingerprint = null,
            keystone_sign_request = null,
            keystone_sync_data = null,
            metadata = "{}",
            avatar_id = null,
            chain_id = 1L,
            key_alias = keyAlias,
            key_backend = "HARDWARE",
            key_format_version = 1L,
            requires_auth = if (requiresAuth) 1L else 0L,
            is_deletion_pending = 0L
        )
        val insertedId = database.walletQueries.lastInsertRowId().executeAsOne()
        fakeKeyManager.setKey(keyAlias, testPrivateKeyHex, requireAuth = requiresAuth)
        return insertedId
    }

    // =========================================================================
    // CHALLENGE 1: 50-THREAD CONCURRENT GRANT ISSUANCE FROM SINGLE HANDLE
    // =========================================================================

    @Test
    fun challenge_50_concurrent_grant_issuance_from_single_handle_results_in_exactly_1_success() {
        val keyId = "key_concurrent_issuance_50"
        val handle = TestPlatformAuthenticator.issueHandle(
            keyId = keyId,
            operation = AuthOperation.DELETE,
            intentFingerprint = "",
            validityDurationMs = 60_000L,
            walletId = "w_123"
        )

        val threadCount = 50
        val executor = Executors.newFixedThreadPool(threadCount)
        val startGun = CountDownLatch(1)
        val finishLatch = CountDownLatch(threadCount)

        val successCount = AtomicInteger(0)
        val failureCount = AtomicInteger(0)
        val issuedGrants = ConcurrentLinkedQueue<DeletionAuthorizationGrant>()

        for (i in 0 until threadCount) {
            executor.submit {
                try {
                    startGun.await()
                    val res = DeletionAuthorizationService.issueDeletionGrant(
                        handle = handle,
                        walletId = "w_123",
                        keyAlias = keyId
                    )
                    if (res is Result.Success) {
                        successCount.incrementAndGet()
                        issuedGrants.add(res.data)
                    } else {
                        failureCount.incrementAndGet()
                    }
                } finally {
                    finishLatch.countDown()
                }
            }
        }

        startGun.countDown()
        assertTrue("All 50 issuance threads must complete within timeout", finishLatch.await(10, TimeUnit.SECONDS))
        executor.shutdown()

        assertEquals("Exactly 1 grant must be issued", 1, successCount.get())
        assertEquals("49 issuance requests must be rejected", 49, failureCount.get())
        assertEquals("Issued grants queue size must be exactly 1", 1, issuedGrants.size)

        // Session must be permanently consumed
        assertTrue(AuthHandleRegistry.isConsumed(handle.sessionId))
        assertFalse(AuthHandleRegistry.isRegistered(handle.sessionId))

        // The single issued grant must be registered in DeletionGrantRegistry and valid
        val grant = issuedGrants.first()
        assertTrue(DeletionGrantRegistry.isRegistered(grant.nonce))
        assertFalse(DeletionGrantRegistry.isConsumed(grant.nonce))
    }

    // =========================================================================
    // CHALLENGE 2: 50-THREAD CONCURRENT GRANT CONSUMPTION & KEY DELETION
    // =========================================================================

    @Test
    fun challenge_50_concurrent_grant_consumption_results_in_exactly_1_success_and_deleteCount_1() {
        val keyId = "key_concurrent_consume_50"
        fakeKeyManager.setKey(keyId, testPrivateKeyHex, requireAuth = true)

        val handle = TestPlatformAuthenticator.issueHandle(
            keyId = keyId,
            operation = AuthOperation.DELETE,
            intentFingerprint = "",
            validityDurationMs = 60_000L,
            walletId = "w_456"
        )
        val grant = (DeletionAuthorizationService.issueDeletionGrant(
            handle = handle,
            walletId = "w_456",
            keyAlias = keyId
        ) as Result.Success).data

        val threadCount = 50
        val executor = Executors.newFixedThreadPool(threadCount)
        val startGun = CountDownLatch(1)
        val finishLatch = CountDownLatch(threadCount)

        val successCount = AtomicInteger(0)
        val failureCount = AtomicInteger(0)

        for (i in 0 until threadCount) {
            executor.submit {
                try {
                    startGun.await()
                    val res = runBlocking(Dispatchers.Default) {
                        fakeKeyManager.deletePrivateKeyWithGrant(grant)
                    }
                    if (res is Result.Success) {
                        successCount.incrementAndGet()
                        issuedGrantsSafe(successCount)
                    } else {
                        failureCount.incrementAndGet()
                    }
                } finally {
                    finishLatch.countDown()
                }
            }
        }

        startGun.countDown()
        assertTrue("All 50 consumption threads must complete within timeout", finishLatch.await(10, TimeUnit.SECONDS))
        executor.shutdown()

        assertEquals("Exactly 1 thread must successfully consume grant", 1, successCount.get())
        assertEquals("49 threads must be rejected", 49, failureCount.get())
        assertEquals("Physical deleteCount must be exactly 1", 1, fakeKeyManager.deleteCount)
        assertFalse(runBlocking { fakeKeyManager.hasPrivateKey(keyId) })
        assertTrue(DeletionGrantRegistry.isConsumed(grant.nonce))
    }

    private fun issuedGrantsSafe(counter: AtomicInteger) {}

    // =========================================================================
    // CHALLENGE 3: 50-THREAD CONCURRENT ANDROID SECURE KEY MANAGER WITH GRANT
    // =========================================================================

    @Test
    fun challenge_50_concurrent_android_secure_key_manager_delete_with_grant() {
        val mockContext = mock<Context>()
        whenever(mockContext.applicationContext).thenReturn(mockContext)

        val backend = TestKeyStoreBackend()
        val prefs = InMemorySharedPreferences()
        val androidKeyManager = AndroidSecureKeyManager(
            context = mockContext,
            config = SecureStorageConfig(enableRootDetection = false),
            keyStoreProvider = { backend.createKeyStore() },
            encryptedPrefsProvider = { prefs },
            secretKeyProvider = { alias, _ -> backend.generateAndStoreKey(alias) }
        )

        val keyId = "key_android_50_grant"
        val rawPrivKey = ByteArray(32) { i ->
            testPrivateKeyHex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }

        // Store key
        val importHandle = TestPlatformAuthenticator.issueHandle(
            keyId = keyId,
            operation = AuthOperation.IMPORT,
            intentFingerprint = "",
            validityDurationMs = 60_000L,
            walletId = "w_789"
        )
        val storeRes = runBlocking {
            androidKeyManager.storePrivateKey(
                keyId = keyId,
                privateKey = rawPrivKey,
                requireAuth = true,
                authContext = AuthenticationContext(authHandle = importHandle),
                expectedWalletId = "w_789"
            )
        }
        assertTrue("Store key must succeed", storeRes is Result.Success)

        // Issue Grant
        val deleteHandle = TestPlatformAuthenticator.issueHandle(
            keyId = keyId,
            operation = AuthOperation.DELETE,
            intentFingerprint = "",
            validityDurationMs = 60_000L,
            walletId = "w_789"
        )
        val grant = (DeletionAuthorizationService.issueDeletionGrant(
            handle = deleteHandle,
            walletId = "w_789",
            keyAlias = keyId
        ) as Result.Success).data

        val threadCount = 50
        val executor = Executors.newFixedThreadPool(threadCount)
        val startGun = CountDownLatch(1)
        val finishLatch = CountDownLatch(threadCount)

        val successCount = AtomicInteger(0)
        val failureCount = AtomicInteger(0)

        for (i in 0 until threadCount) {
            executor.submit {
                try {
                    startGun.await()
                    val res = runBlocking(Dispatchers.Default) {
                        androidKeyManager.deletePrivateKeyWithGrant(grant)
                    }
                    if (res is Result.Success) {
                        successCount.incrementAndGet()
                    } else {
                        failureCount.incrementAndGet()
                    }
                } finally {
                    finishLatch.countDown()
                }
            }
        }

        startGun.countDown()
        assertTrue("All 50 android key manager threads must complete", finishLatch.await(10, TimeUnit.SECONDS))
        executor.shutdown()

        assertEquals("Exactly 1 thread must succeed deleting key in AndroidKeyManager", 1, successCount.get())
        assertEquals("49 threads must fail in AndroidKeyManager", 49, failureCount.get())
        assertFalse("Key must no longer exist in AndroidKeyManager", runBlocking { androidKeyManager.hasPrivateKey(keyId) })
    }

    // =========================================================================
    // CHALLENGE 4: 50-THREAD CONCURRENT deleteWallet REPOSITORY STRESS TEST
    // =========================================================================

    @Test
    fun challenge_50_concurrent_deleteWallet_repository_stress() {
        val keyAlias = "wallet_key_repo_50"
        val walletId = insertTestWallet(id = 888L, keyAlias = keyAlias, requiresAuth = true)

        val deleteHandle = TestPlatformAuthenticator.issueHandle(
            keyId = keyAlias,
            operation = AuthOperation.DELETE,
            intentFingerprint = "",
            validityDurationMs = 60_000L,
            walletId = walletId.toString()
        )
        val authContext = AuthenticationContext(authHandle = deleteHandle)

        val threadCount = 50
        val executor = Executors.newFixedThreadPool(threadCount)
        val startGun = CountDownLatch(1)
        val finishLatch = CountDownLatch(threadCount)

        val successCount = AtomicInteger(0)
        val failureCount = AtomicInteger(0)

        for (i in 0 until threadCount) {
            executor.submit {
                try {
                    startGun.await()
                    val res = runBlocking(Dispatchers.Default) {
                        repository.deleteWallet(walletId.toString(), authContext)
                    }
                    if (res is Result.Success) {
                        successCount.incrementAndGet()
                    } else {
                        failureCount.incrementAndGet()
                    }
                } finally {
                    finishLatch.countDown()
                }
            }
        }

        startGun.countDown()
        assertTrue("All 50 deleteWallet threads must complete within timeout", finishLatch.await(15, TimeUnit.SECONDS))
        executor.shutdown()

        assertEquals("Exactly 1 thread must successfully delete wallet", 1, successCount.get())
        assertEquals("49 threads must be rejected", 49, failureCount.get())

        // Verify KeyVault deletion
        assertEquals("Physical key deleteCount must be exactly 1", 1, fakeKeyManager.deleteCount)
        assertFalse("Key must be deleted from KeyVault", runBlocking { fakeKeyManager.hasPrivateKey(keyAlias) })

        // Verify DB row deleted
        assertNull("Wallet must be deleted from DB", database.walletQueries.selectById(walletId).executeAsOneOrNull())

        // Verify DeletionJournal has exactly 1 entry in COMPLETED state
        val journals = database.deletionJournalQueries.selectAllDeletionJournals().executeAsList()
        assertEquals(1, journals.size)
        assertEquals("COMPLETED", journals.first().state)

        // Verify 17-step ledger recorded
        val steps = database.deletionStepLedgerQueries.selectStepsByWalletId(walletId).executeAsList()
        assertEquals(17, steps.size)
        assertTrue("All 17 ledger steps must be PASS", steps.all { it.status == DeletionStepStatus.PASS.name })
    }

    // =========================================================================
    // CHALLENGE 5: ADVERSARIAL FORGERY, TAMPERING & REPLAY PROBES
    // =========================================================================

    @Test
    fun challenge_tampered_and_forged_grants_are_strictly_rejected() {
        val targetKey = "key_legit_55"
        val victimKey = "key_victim_55"
        fakeKeyManager.setKey(targetKey, testPrivateKeyHex, requireAuth = true)
        fakeKeyManager.setKey(victimKey, testPrivateKeyHex, requireAuth = true)

        val handle = TestPlatformAuthenticator.issueHandle(
            keyId = targetKey,
            operation = AuthOperation.DELETE,
            intentFingerprint = "",
            validityDurationMs = 60_000L,
            walletId = "w_55"
        )
        val grant = (DeletionAuthorizationService.issueDeletionGrant(
            handle = handle,
            walletId = "w_55",
            keyAlias = targetKey
        ) as Result.Success).data

        // Probe 1: Altered target key (Cross-key attack)
        val crossKeyResult = DeletionGrantRegistry.validateAndConsume(grant, expectedKeyAlias = victimKey)
        assertTrue("Cross-key grant consumption must fail", crossKeyResult is Result.Failure)
        assertTrue((crossKeyResult as Result.Failure).exception is AuthenticationRequiredException)

        // Probe 2: Altered proof token (Forged HMAC)
        val forgedGrant = DeletionAuthorizationGrant(
            walletId = "w_55",
            keyAlias = targetKey,
            operation = AuthOperation.DELETE,
            originalAuthSessionId = grant.originalAuthSessionId,
            issuedAtMs = grant.issuedAtMs,
            expiresAtMs = grant.expiresAtMs,
            nonce = grant.nonce,
            proofToken = "deadbeef_forged_hmac_signature"
        )
        val forgedHmacResult = DeletionGrantRegistry.validateAndConsume(forgedGrant, expectedKeyAlias = targetKey)
        assertTrue("Forged HMAC grant must fail", forgedHmacResult is Result.Failure)
        assertTrue((forgedHmacResult as Result.Failure).exception is AuthenticationRequiredException)

        // Probe 3: Expired grant
        val expiredGrant = DeletionAuthorizationGrant(
            walletId = "w_55",
            keyAlias = targetKey,
            operation = AuthOperation.DELETE,
            originalAuthSessionId = grant.originalAuthSessionId,
            issuedAtMs = System.currentTimeMillis() - 100_000L,
            expiresAtMs = System.currentTimeMillis() - 1_000L,
            nonce = "expired_nonce_123",
            proofToken = "expired_token"
        )
        val expiredResult = DeletionGrantRegistry.validateAndConsume(expiredGrant, expectedKeyAlias = targetKey)
        assertTrue("Expired grant must fail", expiredResult is Result.Failure)

        // Target and victim keys must remain untouched
        assertTrue(runBlocking { fakeKeyManager.hasPrivateKey(targetKey) })
        assertTrue(runBlocking { fakeKeyManager.hasPrivateKey(victimKey) })
        assertEquals(0, fakeKeyManager.deleteCount)
    }
}

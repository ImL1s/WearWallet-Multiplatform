package com.cbstudio.wearwallet.core.security

import android.content.Context
import app.cash.sqldelight.Query
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.data.repository.ReconciliationVerdict
import com.cbstudio.wearwallet.core.data.repository.WalletRepositoryImpl
import com.cbstudio.wearwallet.core.data.repository.validateReconciliationCandidate
import com.cbstudio.wearwallet.core.database.CoreWalletDatabase
import com.cbstudio.wearwallet.core.database.DatabaseDriverFactory
import com.cbstudio.wearwallet.core.database.DeletionStepLedgerQueries
import com.cbstudio.wearwallet.core.database.Staging_journal
import com.cbstudio.wearwallet.core.database.Wallet
import com.cbstudio.wearwallet.core.database.WalletQueries
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.WalletType
import com.cbstudio.wearwallet.core.network.EthereumRpcClient
import com.cbstudio.wearwallet.core.platform.PlatformDeletionCleanupHook
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Adversarial Challenger 1 Empirical Verification Suite for PR #32 Round 14
 *
 * Direct Empirical Challenges:
 * 1. P0 RecoveryGrant Adversarial Attacks:
 *    - HMAC Forgery & Tamper Rejection (altered alias, sessionId, state, proof, hash, bit-flip).
 *    - Replay Defense: Consumed RecoveryGrant nonce rejected on reuse.
 *    - Expiration Defense: Expired RecoveryGrant rejected.
 *    - High-Concurrency Race: 50 concurrent threads consuming the exact same RecoveryGrant yield exactly 1 success and 49 failures.
 * 2. P0 DB Fault Injection on Reconciliation:
 *    - Inject DB lock / SQLite error / corruption during validateReconciliationCandidate -> verify candidate rejected, keyDeleteCount == 0, and keys preserved.
 * 3. P1 KeyPresence Storage Fault Injection:
 *    - Inject Keystore / storage exception during checkKeyPresence -> verify KeyPresence.Unavailable returned, Deletion Ledger Step KEY_VAULT records FAILED, deletion CAS transitions to RECOVERY_REQUIRED, and DB wallet row is preserved.
 * 4. P1 PRICE_ALERT_ROWS Schema Isolation & Migration v7:
 *    - Multi-wallet isolation: Deleting Wallet A preserves Wallet B's price alerts on shared assets.
 */
class Round14AdversarialChallenger1Test {

    private lateinit var mockContext: Context
    private lateinit var sqlDriver: JdbcSqliteDriver
    private lateinit var database: CoreWalletDatabase
    private lateinit var fakeKeyManager: FakeSecureKeyManager
    private lateinit var cryptoProvider: CommonCryptoProvider
    private lateinit var ethereumRpcClient: EthereumRpcClient
    private lateinit var driverFactory: DatabaseDriverFactory

    private val testPrivateKeyHex = "e331b6d69882b4cb4ea581d88ec2693329b3b3a4711832421415ebff2d474428"

    @Before
    fun setUp() {
        AuthHandleRegistry.clearForTesting()
        DeletionGrantRegistry.clearForTesting()
        RecoveryGrantRegistry.clearForTesting()

        mockContext = mock()
        whenever(mockContext.applicationContext).thenReturn(mockContext)

        sqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CoreWalletDatabase.Schema.create(sqlDriver)
        database = CoreWalletDatabase(sqlDriver)

        fakeKeyManager = FakeSecureKeyManager()
        cryptoProvider = CommonCryptoProvider()
        ethereumRpcClient = mock()

        driverFactory = mock()
        whenever(driverFactory.createDriver()).thenReturn(sqlDriver)
    }

    @After
    fun tearDown() {
        AuthHandleRegistry.clearForTesting()
        DeletionGrantRegistry.clearForTesting()
        RecoveryGrantRegistry.clearForTesting()
        try {
            sqlDriver.close()
        } catch (_: Throwable) {}
    }

    private fun createRepository(
        keyManager: SecureKeyManager = fakeKeyManager,
        hook: PlatformDeletionCleanupHook = com.cbstudio.wearwallet.core.platform.NoOpPlatformDeletionCleanupHook(),
        customWalletQueries: WalletQueries? = database.walletQueries,
        customLedgerQueries: DeletionStepLedgerQueries? = database.deletionStepLedgerQueries
    ): WalletRepositoryImpl {
        return WalletRepositoryImpl(
            databaseDriverFactory = driverFactory,
            cryptoProvider = cryptoProvider,
            ethereumRpcClient = ethereumRpcClient,
            secureKeyManager = keyManager,
            platformDeletionCleanupHook = hook,
            customWalletQueries = customWalletQueries,
            customStagingJournalQueries = database.stagingJournalQueries,
            customDeletionJournalQueries = database.deletionJournalQueries,
            customDeletionStepLedgerQueries = customLedgerQueries
        )
    }

    private fun insertTestWallet(
        id: Long = 1L,
        name: String = "Test Wallet $id",
        keyAlias: String = "wallet_key_$id",
        requiresAuth: Boolean = true,
        isActive: Boolean = true
    ): Long {
        database.walletQueries.insert(
            name = name,
            address = "0x" + id.toString().padStart(40, '0'),
            public_key = "0xpub",
            encrypted_private_key = "encrypted_priv_key_payload",
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
            key_backend = "KEYSTORE",
            key_format_version = 1L,
            requires_auth = if (requiresAuth) 1L else 0L,
            is_deletion_pending = 0L
        )
        val insertedId = database.walletQueries.lastInsertRowId().executeAsOne()
        if (isActive) {
            database.walletQueries.setActiveWallet(insertedId)
        }
        fakeKeyManager.setKey(keyAlias, testPrivateKeyHex, requireAuth = requiresAuth)
        return insertedId
    }

    private fun issueDeleteAuth(keyAlias: String, walletId: String = "1"): AuthenticationContext {
        val handle = TestPlatformAuthenticator.issueHandle(
            keyId = keyAlias,
            operation = AuthOperation.DELETE,
            intentFingerprint = "",
            validityDurationMs = 60_000L,
            walletId = walletId
        )
        return AuthenticationContext(authHandle = handle)
    }

    // =========================================================================
    // SECTION 1: P0 RecoveryGrant Adversarial Attacks
    // =========================================================================

    @Test
    fun p0_challenge_1_1_attempt_to_forge_recovery_grant_hmac_fails_verification() {
        val now = Clock.System.now().toEpochMilliseconds()
        val genuineGrant = RecoveryGrant.create(
            journalRowHash = "hash_gen_1",
            sessionId = "session_gen_1",
            alias = "ww_alias_gen_1",
            state = "KEY_STAGED",
            zeroActiveReferenceProof = "ZERO_ACTIVE_REF_VALIDATED",
            currentTimeMs = now
        )

        // 1. Genuine grant verifies
        assertTrue("Genuine grant must pass HMAC verification", RecoveryGrantVerifier.verify(genuineGrant))

        // 2. Tampering alias
        val tamperedAliasGrant = RecoveryGrant(
            journalRowHash = genuineGrant.journalRowHash,
            sessionId = genuineGrant.sessionId,
            alias = "ww_alias_attacker_target",
            state = genuineGrant.state,
            zeroActiveReferenceProof = genuineGrant.zeroActiveReferenceProof,
            nonce = genuineGrant.nonce,
            issuedAtMs = genuineGrant.issuedAtMs,
            expiresAtMs = genuineGrant.expiresAtMs,
            proofToken = genuineGrant.proofToken
        )
        assertFalse("Tampered alias must fail HMAC verification", RecoveryGrantVerifier.verify(tamperedAliasGrant))

        // 3. Tampering state
        val tamperedStateGrant = RecoveryGrant(
            journalRowHash = genuineGrant.journalRowHash,
            sessionId = genuineGrant.sessionId,
            alias = genuineGrant.alias,
            state = "COMMITTED",
            zeroActiveReferenceProof = genuineGrant.zeroActiveReferenceProof,
            nonce = genuineGrant.nonce,
            issuedAtMs = genuineGrant.issuedAtMs,
            expiresAtMs = genuineGrant.expiresAtMs,
            proofToken = genuineGrant.proofToken
        )
        assertFalse("Tampered state must fail HMAC verification", RecoveryGrantVerifier.verify(tamperedStateGrant))

        // 4. Tampering zeroActiveReferenceProof
        val tamperedProofGrant = RecoveryGrant(
            journalRowHash = genuineGrant.journalRowHash,
            sessionId = genuineGrant.sessionId,
            alias = genuineGrant.alias,
            state = genuineGrant.state,
            zeroActiveReferenceProof = "FORGED_PROOF",
            nonce = genuineGrant.nonce,
            issuedAtMs = genuineGrant.issuedAtMs,
            expiresAtMs = genuineGrant.expiresAtMs,
            proofToken = genuineGrant.proofToken
        )
        assertFalse("Tampered proof must fail HMAC verification", RecoveryGrantVerifier.verify(tamperedProofGrant))

        // 5. Bit-flipped proofToken
        val flippedToken = if (genuineGrant.proofToken[0] == '0') "1" + genuineGrant.proofToken.substring(1) else "0" + genuineGrant.proofToken.substring(1)
        val bitFlippedGrant = RecoveryGrant(
            journalRowHash = genuineGrant.journalRowHash,
            sessionId = genuineGrant.sessionId,
            alias = genuineGrant.alias,
            state = genuineGrant.state,
            zeroActiveReferenceProof = genuineGrant.zeroActiveReferenceProof,
            nonce = genuineGrant.nonce,
            issuedAtMs = genuineGrant.issuedAtMs,
            expiresAtMs = genuineGrant.expiresAtMs,
            proofToken = flippedToken
        )
        assertFalse("Bit-flipped proofToken must fail HMAC verification", RecoveryGrantVerifier.verify(bitFlippedGrant))

        // 6. Validating unauthenticated/forged grant in Registry must return Failure
        RecoveryGrantRegistry.register(genuineGrant)
        val consumeTampered = RecoveryGrantRegistry.validateAndConsume(tamperedAliasGrant, "ww_alias_attacker_target", now)
        assertTrue("Consuming tampered grant in Registry MUST fail", consumeTampered is Result.Failure)
    }

    @Test
    fun p0_challenge_1_2_attempt_to_replay_consumed_recovery_grant_nonce_fails() {
        val now = Clock.System.now().toEpochMilliseconds()
        val alias = "ww_alias_replay_test"
        val grant = RecoveryGrant.create(
            journalRowHash = "hash_replay",
            sessionId = "session_replay",
            alias = alias,
            state = "KEY_STAGED",
            zeroActiveReferenceProof = "ZERO_ACTIVE_REF_VALIDATED",
            currentTimeMs = now
        )

        RecoveryGrantRegistry.register(grant)
        assertTrue("Grant should be registered", RecoveryGrantRegistry.isRegistered(grant.nonce))
        assertFalse("Grant should not yet be consumed", RecoveryGrantRegistry.isConsumed(grant.nonce))

        // First consumption succeeds
        val firstConsume = RecoveryGrantRegistry.validateAndConsume(grant, alias, now)
        assertTrue("First consumption of valid grant must succeed", firstConsume is Result.Success)
        assertFalse("Grant should no longer be active", RecoveryGrantRegistry.isRegistered(grant.nonce))
        assertTrue("Grant must be recorded as consumed", RecoveryGrantRegistry.isConsumed(grant.nonce))

        // Replay attempt fails immediately
        val replayConsume = RecoveryGrantRegistry.validateAndConsume(grant, alias, now)
        assertTrue("Replaying consumed grant MUST fail", replayConsume is Result.Failure)
        val ex = (replayConsume as Result.Failure).exception
        assertTrue("Exception must be AuthenticationRequiredException", ex is AuthenticationRequiredException)
        assertTrue("Error message must indicate replay rejection", ex.message!!.contains("already been consumed (replay rejected)"))
    }

    @Test
    fun p0_challenge_1_3_attempt_to_use_expired_recovery_grant_fails() {
        val now = Clock.System.now().toEpochMilliseconds()
        val alias = "ww_alias_expired_test"
        val grant = RecoveryGrant.create(
            journalRowHash = "hash_exp",
            sessionId = "session_exp",
            alias = alias,
            state = "KEY_STAGED",
            zeroActiveReferenceProof = "ZERO_ACTIVE_REF_VALIDATED",
            currentTimeMs = now
        )

        RecoveryGrantRegistry.register(grant)

        // Attempt consumption past expiration (+61 seconds)
        val futureTime = now + 61_000L
        assertTrue("Grant must report isExpired() == true in the future", grant.isExpired(futureTime))

        val expiredConsume = RecoveryGrantRegistry.validateAndConsume(grant, alias, futureTime)
        assertTrue("Consuming expired grant MUST fail", expiredConsume is Result.Failure)
        val ex = (expiredConsume as Result.Failure).exception
        assertTrue("Error message must cite expiration", ex.message!!.contains("expired"))
    }

    @Test
    fun p0_challenge_1_4_50_concurrent_threads_consuming_same_recovery_grant_yield_exactly_1_success_49_failures() {
        val now = Clock.System.now().toEpochMilliseconds()
        val alias = "ww_alias_concurrent_p0"
        val grant = RecoveryGrant.create(
            journalRowHash = "hash_50_threads",
            sessionId = "session_50_threads",
            alias = alias,
            state = "KEY_STAGED",
            zeroActiveReferenceProof = "ZERO_ACTIVE_REF_VALIDATED",
            currentTimeMs = now
        )

        RecoveryGrantRegistry.register(grant)

        val threadCount = 50
        val executor = Executors.newFixedThreadPool(threadCount)
        val startLatch = CountDownLatch(1)
        val finishLatch = CountDownLatch(threadCount)

        val results = ConcurrentLinkedQueue<Result<Unit>>()
        val successCount = AtomicInteger(0)
        val failureCount = AtomicInteger(0)

        for (i in 0 until threadCount) {
            executor.submit {
                try {
                    startLatch.await()
                    val res = RecoveryGrantRegistry.validateAndConsume(grant, alias, now)
                    results.add(res)
                    when (res) {
                        is Result.Success -> { successCount.incrementAndGet() }
                        is Result.Failure -> { failureCount.incrementAndGet() }
                        is Result.Loading -> {}
                    }
                } finally {
                    finishLatch.countDown()
                }
            }
        }

        startLatch.countDown()
        val completed = finishLatch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        assertTrue("All 50 threads must complete within timeout", completed)
        assertEquals("Total results collected must be 50", 50, results.size)
        assertEquals("CRITICAL: Exactly 1 thread must successfully consume the RecoveryGrant", 1, successCount.get())
        assertEquals("CRITICAL: Exactly 49 threads must fail to consume (replay / concurrency protection)", 49, failureCount.get())
        assertTrue("Grant nonce must be marked consumed in registry", RecoveryGrantRegistry.isConsumed(grant.nonce))
    }

    // =========================================================================
    // SECTION 2: P0 DB Fault Injection on Reconciliation Candidate Validation
    // =========================================================================

    @Test
    fun p0_challenge_2_1_db_lock_or_sqlite_error_during_validate_reconciliation_candidate_rejects_and_preserves_keys() = runBlocking {
        val orphanAlias = "ww_key_orphan_victim"
        val sessionId = "session_orphan_db_err"
        fakeKeyManager.setKey(orphanAlias, testPrivateKeyHex)
        fakeKeyManager.resetDeleteCount()

        val journal = Staging_journal(
            session_id = sessionId,
            staged_alias = orphanAlias,
            backup_id = "backup_orphan",
            state = ProvisioningState.KEY_STAGED.name,
            created_at = 1000L,
            expires_at = 2000L
        )

        // Simulate DB lock / SQLite error during Layer 4 query
        val mockWalletQueries = mock<WalletQueries>()
        val queryMock = mock<Query<Wallet>>()
        whenever(queryMock.executeAsOneOrNull()).thenThrow(android.database.sqlite.SQLiteDatabaseLockedException("Database locked by concurrent writer"))
        whenever(mockWalletQueries.selectByKeyAlias(orphanAlias)).thenReturn(queryMock)

        val verdict = validateReconciliationCandidate(
            journal = journal,
            expectedSessionId = sessionId,
            expectedKeyAlias = orphanAlias,
            walletQueries = mockWalletQueries
        )

        // 1. Candidate must be REJECTED (Fail-Closed)
        assertTrue("DB query exception MUST cause ReconciliationVerdict.Rejected", verdict is ReconciliationVerdict.Rejected)
        val reason = (verdict as ReconciliationVerdict.Rejected).reason
        assertTrue("Rejection reason must mention Layer 4 violation and DB query error", reason.contains("Layer 4 Violation: DB query error"))

        // 2. Key MUST NOT be deleted
        assertTrue("Key in KeyVault must remain intact", fakeKeyManager.hasPrivateKey(orphanAlias))
        assertEquals("Key delete count must be 0", 0, fakeKeyManager.deleteCount)
    }

    @Test
    fun p0_challenge_2_2_db_disk_io_corruption_during_reconcile_startup_state_prevents_key_deletion() = runBlocking {
        val orphanAlias = "ww_key_orphan_corrupt"
        val sessionId = "session_corrupt_test"
        fakeKeyManager.setKey(orphanAlias, testPrivateKeyHex)
        fakeKeyManager.resetDeleteCount()

        val journal = Staging_journal(
            session_id = sessionId,
            staged_alias = orphanAlias,
            backup_id = "backup_orphan",
            state = ProvisioningState.KEY_STAGED.name,
            created_at = 1000L,
            expires_at = 2000L
        )

        val mockWalletQueries = mock<WalletQueries>()
        val mockStagingQueries = mock<com.cbstudio.wearwallet.core.database.StagingJournalQueries>()
        val mockDeletionQueries = mock<com.cbstudio.wearwallet.core.database.DeletionJournalQueries>()

        val pendingStagingQuery = mock<Query<Staging_journal>>()
        whenever(pendingStagingQuery.executeAsList()).thenReturn(listOf(journal))
        whenever(mockStagingQueries.selectPendingJournals()).thenReturn(pendingStagingQuery)

        val pendingDeletionQuery = mock<Query<com.cbstudio.wearwallet.core.database.Deletion_journal>>()
        whenever(pendingDeletionQuery.executeAsList()).thenReturn(emptyList())
        whenever(mockDeletionQueries.selectPendingDeletions()).thenReturn(pendingDeletionQuery)

        val pendingTombstoneQuery = mock<Query<Wallet>>()
        whenever(pendingTombstoneQuery.executeAsList()).thenReturn(emptyList())
        whenever(mockWalletQueries.selectDeletionPending()).thenReturn(pendingTombstoneQuery)

        // Throw disk I/O error on selectByKeyAlias
        whenever(mockWalletQueries.selectByKeyAlias(orphanAlias)).thenThrow(android.database.sqlite.SQLiteDiskIOException("Disk bad blocks detected"))

        val repo = WalletRepositoryImpl(
            databaseDriverFactory = driverFactory,
            cryptoProvider = cryptoProvider,
            ethereumRpcClient = ethereumRpcClient,
            secureKeyManager = fakeKeyManager,
            platformDeletionCleanupHook = com.cbstudio.wearwallet.core.platform.NoOpPlatformDeletionCleanupHook(),
            customWalletQueries = mockWalletQueries,
            customStagingJournalQueries = mockStagingQueries,
            customDeletionJournalQueries = mockDeletionQueries
        )

        val result = repo.reconcileStartupState()

        // Key MUST NOT be deleted despite staging journal presence
        assertTrue("Key in KeyVault must remain intact", fakeKeyManager.hasPrivateKey(orphanAlias))
        assertEquals("Key delete count must remain 0", 0, fakeKeyManager.deleteCount)
    }

    // =========================================================================
    // SECTION 3: P1 KeyPresence Storage Fault Injection
    // =========================================================================

    @Test
    fun p1_challenge_3_1_keypresence_unavailable_storage_fault_transitions_to_recovery_required_and_preserves_db_row() = runBlocking {
        val walletId = insertTestWallet(id = 50L, keyAlias = "key_storage_fault")
        val auth = issueDeleteAuth("key_storage_fault", walletId = walletId.toString())

        // KeyManager that returns KeyPresence.Unavailable on checkKeyPresence
        val faultKeyManager = object : SecureKeyManager by fakeKeyManager, KeyVaultDeletionCapability {
            override suspend fun checkKeyPresence(keyId: String): KeyPresence {
                return KeyPresence.Unavailable(KeyStorageException("Keystore daemon crashed / hardware I/O timeout"))
            }
            override suspend fun deletePrivateKeyWithGrant(grant: DeletionAuthorizationGrant, expectedWalletId: String): Result<Unit> {
                return Result.Failure(KeyStorageException("Keystore unreachable"))
            }
        }

        val repo = createRepository(keyManager = faultKeyManager)
        val result = repo.deleteWallet(walletId.toString(), auth)

        // 1. Operation must fail
        assertTrue("deleteWallet MUST fail when checkKeyPresence is Unavailable", result is Result.Failure)

        // 2. Deletion Ledger Step KEY_VAULT must record FAILED
        val keyVaultStep = database.deletionStepLedgerQueries.selectStep(walletId, DeletionStep.KEY_VAULT.name).executeAsOneOrNull()
        assertNotNull("KEY_VAULT step must be recorded in ledger", keyVaultStep)
        assertEquals("KEY_VAULT step status must be FAILED", DeletionStepStatus.FAILED.name, keyVaultStep?.status)
        assertTrue("Step error message must describe key presence unavailable", keyVaultStep?.error_message!!.contains("Key presence check unavailable"))

        // 3. Deletion Journal CAS must transition to RECOVERY_REQUIRED
        val journal = database.deletionJournalQueries.selectByWalletId(walletId).executeAsOneOrNull()
        assertNotNull("Deletion journal must exist", journal)
        assertEquals("Deletion journal state MUST be RECOVERY_REQUIRED", DeletionState.RECOVERY_REQUIRED.name, journal?.state)

        // 4. Wallet DB row MUST be PRESERVED in database (not deleted)
        val walletInDb = database.walletQueries.selectById(walletId).executeAsOneOrNull()
        assertNotNull("CRITICAL: Wallet DB row MUST NOT be deleted when key presence check fails", walletInDb)
        assertEquals("Wallet id must match", walletId, walletInDb?.id)
        assertEquals("Wallet is marked deletion pending (tombstoned for recovery)", 1L, walletInDb?.is_deletion_pending)
    }

    @Test
    fun p1_challenge_3_2_keypresence_partial_transitions_to_recovery_required_and_preserves_db_row() = runBlocking {
        val walletId = insertTestWallet(id = 51L, keyAlias = "key_partial_fault")
        val auth = issueDeleteAuth("key_partial_fault", walletId = walletId.toString())

        // KeyManager that returns KeyPresence.Partial on checkKeyPresence
        val partialKeyManager = object : SecureKeyManager by fakeKeyManager, KeyVaultDeletionCapability {
            override suspend fun checkKeyPresence(keyId: String): KeyPresence {
                return KeyPresence.Partial("Missing IV and ciphertext tuples")
            }
            override suspend fun deletePrivateKeyWithGrant(grant: DeletionAuthorizationGrant, expectedWalletId: String): Result<Unit> {
                return Result.Failure(KeyStorageException("Partial state prevents normal grant deletion"))
            }
        }

        val repo = createRepository(keyManager = partialKeyManager)
        val result = repo.deleteWallet(walletId.toString(), auth)

        // 1. Operation must fail
        assertTrue("deleteWallet MUST fail when checkKeyPresence is Partial", result is Result.Failure)

        // 2. Deletion Ledger Step KEY_VAULT must record FAILED
        val keyVaultStep = database.deletionStepLedgerQueries.selectStep(walletId, DeletionStep.KEY_VAULT.name).executeAsOneOrNull()
        assertNotNull("KEY_VAULT step must be recorded in ledger", keyVaultStep)
        assertEquals("KEY_VAULT step status must be FAILED", DeletionStepStatus.FAILED.name, keyVaultStep?.status)
        assertTrue("Step error message must describe key presence partial", keyVaultStep?.error_message!!.contains("Key presence partial"))

        // 3. Deletion Journal CAS must transition to RECOVERY_REQUIRED
        val journal = database.deletionJournalQueries.selectByWalletId(walletId).executeAsOneOrNull()
        assertNotNull("Deletion journal must exist", journal)
        assertEquals("Deletion journal state MUST be RECOVERY_REQUIRED", DeletionState.RECOVERY_REQUIRED.name, journal?.state)

        // 4. Wallet DB row MUST be PRESERVED
        val walletInDb = database.walletQueries.selectById(walletId).executeAsOneOrNull()
        assertNotNull("Wallet DB row MUST NOT be deleted when key presence is Partial", walletInDb)
        assertEquals("Wallet is marked deletion pending", 1L, walletInDb?.is_deletion_pending)
    }

    @Test
    fun p1_challenge_3_3_keypresence_post_delete_not_absent_fails_closed() = runBlocking {
        val walletId = insertTestWallet(id = 52L, keyAlias = "key_post_delete_fail")
        val auth = issueDeleteAuth("key_post_delete_fail", walletId = walletId.toString())

        // KeyManager where checkKeyPresence returns Present even after delete
        var deleteCalled = false
        val stubbornKeyManager = object : SecureKeyManager by fakeKeyManager, KeyVaultDeletionCapability {
            override suspend fun checkKeyPresence(keyId: String): KeyPresence {
                return KeyPresence.Present // Stubbornly present
            }
            override suspend fun deletePrivateKeyWithGrant(grant: DeletionAuthorizationGrant, expectedWalletId: String): Result<Unit> {
                deleteCalled = true
                return Result.Success(Unit)
            }
        }

        val repo = createRepository(keyManager = stubbornKeyManager)
        val result = repo.deleteWallet(walletId.toString(), auth)

        assertTrue("deletePrivateKeyWithGrant was called", deleteCalled)
        // Operation MUST fail closed because post-delete checkKeyPresence is NOT Absent
        assertTrue("deleteWallet MUST fail when key is not verified Absent after deletion", result is Result.Failure)

        // Deletion Ledger Step KEY_VAULT must record FAILED
        val keyVaultStep = database.deletionStepLedgerQueries.selectStep(walletId, DeletionStep.KEY_VAULT.name).executeAsOneOrNull()
        assertNotNull("KEY_VAULT step must be recorded in ledger", keyVaultStep)
        assertEquals("KEY_VAULT step status must be FAILED", DeletionStepStatus.FAILED.name, keyVaultStep?.status)
        assertTrue("Step error message must describe non-Absent state", keyVaultStep?.error_message!!.contains("was not verified Absent after deletion"))

        // Journal state must be RECOVERY_REQUIRED
        val journal = database.deletionJournalQueries.selectByWalletId(walletId).executeAsOneOrNull()
        assertEquals("Journal state MUST be RECOVERY_REQUIRED", DeletionState.RECOVERY_REQUIRED.name, journal?.state)

        // Wallet DB row must be preserved
        val walletInDb = database.walletQueries.selectById(walletId).executeAsOneOrNull()
        assertNotNull("Wallet DB row must be preserved", walletInDb)
    }

    // =========================================================================
    // SECTION 4: P1 PRICE_ALERT_ROWS Schema Isolation
    // =========================================================================

    @Test
    fun p1_challenge_4_1_price_alert_rows_isolated_by_wallet_id_when_deleting_shared_token_wallet() = runBlocking {
        val walletAId = insertTestWallet(id = 60L, keyAlias = "key_wallet_A", isActive = true)
        val walletBId = insertTestWallet(id = 61L, keyAlias = "key_wallet_B", isActive = false)

        // Both wallets have an ETH price alert
        database.priceAlertQueries.insert(
            wallet_id = walletAId.toString(),
            asset_symbol = "ETH",
            asset_name = "Ethereum",
            contract_address = "0x0000000000000000000000000000000000000000",
            chain_type = ChainType.ETHEREUM.name,
            chain_id = 1L,
            alert_type = "ABOVE",
            target_price = 4000.0,
            current_price = 3000.0,
            percentage_threshold = null,
            is_enabled = 1L,
            user_notes = "Wallet A Target",
            webhook_url = null,
            repeat_interval = 0L
        )

        database.priceAlertQueries.insert(
            wallet_id = walletBId.toString(),
            asset_symbol = "ETH",
            asset_name = "Ethereum",
            contract_address = "0x0000000000000000000000000000000000000000",
            chain_type = ChainType.ETHEREUM.name,
            chain_id = 1L,
            alert_type = "ABOVE",
            target_price = 4500.0,
            current_price = 3000.0,
            percentage_threshold = null,
            is_enabled = 1L,
            user_notes = "Wallet B Target",
            webhook_url = null,
            repeat_interval = 0L
        )

        assertEquals("Wallet A must have 1 alert", 1L, database.priceAlertQueries.countByWalletId(walletAId.toString()).executeAsOne())
        assertEquals("Wallet B must have 1 alert", 1L, database.priceAlertQueries.countByWalletId(walletBId.toString()).executeAsOne())

        val authA = issueDeleteAuth("key_wallet_A", walletId = walletAId.toString())
        val repo = createRepository()

        // Delete Wallet A
        val delResult = repo.deleteWallet(walletAId.toString(), authA)
        assertTrue("deleteWallet for Wallet A must succeed", delResult is Result.Success)

        // Verify Wallet A's alerts are deleted, but Wallet B's alerts are completely intact
        assertEquals("Wallet A price alerts must be 0", 0L, database.priceAlertQueries.countByWalletId(walletAId.toString()).executeAsOne())
        assertEquals("CRITICAL: Wallet B price alerts MUST be preserved (isolation)", 1L, database.priceAlertQueries.countByWalletId(walletBId.toString()).executeAsOne())
        val bAlerts = database.priceAlertQueries.selectByWalletId(walletBId.toString()).executeAsList()
        assertEquals(1, bAlerts.size)
        assertEquals("Wallet B Target", bAlerts[0].user_notes)
    }
}

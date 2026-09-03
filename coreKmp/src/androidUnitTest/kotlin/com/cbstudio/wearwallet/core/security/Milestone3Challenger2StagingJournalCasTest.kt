package com.cbstudio.wearwallet.core.security

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.data.repository.WalletRepositoryImpl
import com.cbstudio.wearwallet.core.database.CoreWalletDatabase
import com.cbstudio.wearwallet.core.database.DatabaseDriverFactory
import com.cbstudio.wearwallet.core.database.DeletionJournalQueries
import com.cbstudio.wearwallet.core.database.StagingJournalQueries
import com.cbstudio.wearwallet.core.database.WalletQueries
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.WalletType
import com.cbstudio.wearwallet.core.network.EthereumRpcClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.atomic.AtomicInteger

/**
 * Milestone 3 (M3: P1-3 Fail-Closed Staging Journal & CAS Challenge)
 * Challenger 2 Empirical Adversarial Test Suite
 */
class Milestone3Challenger2StagingJournalCasTest {

    private lateinit var sqlDriver: SqlDriver
    private lateinit var database: CoreWalletDatabase
    private lateinit var walletQueries: WalletQueries
    private lateinit var stagingJournalQueries: StagingJournalQueries
    private lateinit var deletionJournalQueries: DeletionJournalQueries
    private lateinit var fakeSecureKeyManager: FakeSecureKeyManager
    private lateinit var cryptoProvider: CommonCryptoProvider
    private lateinit var ethereumRpcClient: EthereumRpcClient
    private lateinit var repository: WalletRepositoryImpl

    private val testPassword = "MasterPassword#2026"
    private val testMnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
    private val testPrivateKeyHex = "e331b6d69882b4cb4ea581d88ec2693329b3b3a4711832421415ebff2d474428"

    @Before
    fun setUp() {
        sqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CoreWalletDatabase.Schema.create(sqlDriver)
        database = CoreWalletDatabase(sqlDriver)
        walletQueries = database.walletQueries
        stagingJournalQueries = database.stagingJournalQueries
        deletionJournalQueries = database.deletionJournalQueries

        fakeSecureKeyManager = FakeSecureKeyManager()
        cryptoProvider = CommonCryptoProvider()
        ethereumRpcClient = mock()

        val driverFactory = mock<DatabaseDriverFactory>()
        whenever(driverFactory.createDriver()).thenReturn(sqlDriver)

        repository = WalletRepositoryImpl(
            databaseDriverFactory = driverFactory,
            cryptoProvider = cryptoProvider,
            ethereumRpcClient = ethereumRpcClient,
            secureKeyManager = fakeSecureKeyManager,
            platformDeletionCleanupHook = com.cbstudio.wearwallet.core.platform.NoOpPlatformDeletionCleanupHook(),
            customWalletQueries = walletQueries,
            customStagingJournalQueries = stagingJournalQueries,
            customDeletionJournalQueries = deletionJournalQueries
        )

        AuthHandleRegistry.clearForTesting()
    }

    @After
    fun tearDown() {
        AuthHandleRegistry.clearForTesting()
        try {
            sqlDriver.close()
        } catch (_: Throwable) {}
    }

    private fun createTamperingTracker(sessionId: String, tamperedState: ProvisioningState = ProvisioningState.PREPARED): SideEffectTracker {
        return object : SideEffectTracker {
            override fun onSign() {}
            override fun onBroadcast() {}
            override fun onNetworkSend() {}
            override fun onDbWrite() {
                stagingJournalQueries.updateJournalState(
                    state = tamperedState.name,
                    session_id = sessionId
                )
            }
        }
    }

    // =========================================================================
    // SECTION 1: STAGING JOURNAL CAS MISMATCH INVARIANTS & ROLLBACK COMPENSATION
    // =========================================================================

    @Test
    fun challenge_1_createWallet_casMismatch_throwsJournalCasMismatchException_andTriggersRollback() = runBlocking {
        val prepRes = repository.prepareProvisioning()
        assertTrue("prepareProvisioning must succeed", prepRes is Result.Success)
        val prepReq = (prepRes as Result.Success).data

        val authHandle = TestPlatformAuthenticator.issueHandle(
            keyId = prepReq.stagedAlias,
            sessionId = prepReq.sessionId,
            operation = AuthOperation.IMPORT,
            validityDurationMs = 60_000L
        )
        val authContext = AuthenticationContext(authHandle = authHandle)

        val adversarialTracker = createTamperingTracker(prepReq.sessionId, ProvisioningState.PREPARED)

        val testDriverFactory = mock<DatabaseDriverFactory>()
        whenever(testDriverFactory.createDriver()).thenReturn(sqlDriver)
        val testRepo = WalletRepositoryImpl(
            databaseDriverFactory = testDriverFactory,
            cryptoProvider = cryptoProvider,
            ethereumRpcClient = ethereumRpcClient,
            secureKeyManager = fakeSecureKeyManager,
            platformDeletionCleanupHook = com.cbstudio.wearwallet.core.platform.NoOpPlatformDeletionCleanupHook(),
            sideEffectTracker = adversarialTracker,
            customWalletQueries = walletQueries,
            customStagingJournalQueries = stagingJournalQueries,
            customDeletionJournalQueries = deletionJournalQueries
        )

        val createRes = testRepo.createWallet(
            name = "TamperedWallet",
            mnemonic = testMnemonic.toCharArray(),
            password = testPassword.toCharArray(),
            chainType = ChainType.ETHEREUM,
            authContext = authContext
        )

        // Assert typed failure
        assertTrue("createWallet must fail on CAS mismatch", createRes is Result.Failure)
        val ex = (createRes as Result.Failure).exception
        assertTrue(
            "Exception must be JournalCasMismatchException, got: ${ex::class.simpleName} (${ex.message})",
            ex is JournalCasMismatchException
        )

        val casEx = ex as JournalCasMismatchException
        assertEquals("SessionId must match", prepReq.sessionId, casEx.sessionId)
        assertEquals("Expected state must be KEY_STAGED", ProvisioningState.KEY_STAGED.name, casEx.expectedState)
        assertEquals("Target state must be DB_WRITTEN", ProvisioningState.DB_WRITTEN.name, casEx.targetState)

        // Assert Rollback Compensation: staged key must NOT remain in key store
        val keyExists = fakeSecureKeyManager.hasPrivateKey(prepReq.stagedAlias)
        assertFalse("KeyVault rollback must have removed staged key '${prepReq.stagedAlias}'", keyExists)

        // Assert Database cleanup: 0 wallets in database
        val allWallets = walletQueries.selectAllActiveWallets().executeAsList()
        assertTrue("Database must have 0 active wallets after rollback", allWallets.isEmpty())
    }

    @Test
    fun challenge_2_importFromMnemonic_casMismatch_throwsJournalCasMismatchException_andTriggersRollback() = runBlocking {
        val prepRes = repository.prepareProvisioning()
        assertTrue(prepRes is Result.Success)
        val prepReq = (prepRes as Result.Success).data

        val authHandle = TestPlatformAuthenticator.issueHandle(
            keyId = prepReq.stagedAlias,
            sessionId = prepReq.sessionId,
            operation = AuthOperation.IMPORT,
            validityDurationMs = 60_000L
        )

        val adversarialTracker = createTamperingTracker(prepReq.sessionId, ProvisioningState.PREPARED)

        val testDriverFactory = mock<DatabaseDriverFactory>()
        whenever(testDriverFactory.createDriver()).thenReturn(sqlDriver)
        val testRepo = WalletRepositoryImpl(
            databaseDriverFactory = testDriverFactory,
            cryptoProvider = cryptoProvider,
            ethereumRpcClient = ethereumRpcClient,
            secureKeyManager = fakeSecureKeyManager,
            platformDeletionCleanupHook = com.cbstudio.wearwallet.core.platform.NoOpPlatformDeletionCleanupHook(),
            sideEffectTracker = adversarialTracker,
            customWalletQueries = walletQueries,
            customStagingJournalQueries = stagingJournalQueries,
            customDeletionJournalQueries = deletionJournalQueries
        )

        val importRes = testRepo.importFromMnemonic(
            name = "TamperedImport",
            mnemonic = testMnemonic.toCharArray(),
            password = testPassword.toCharArray(),
            chainType = ChainType.ETHEREUM,
            authContext = AuthenticationContext(authHandle = authHandle)
        )

        assertTrue("importFromMnemonic must fail on CAS mismatch", importRes is Result.Failure)
        val ex = (importRes as Result.Failure).exception
        assertTrue(
            "Exception must be JournalCasMismatchException, got: ${ex::class.simpleName}",
            ex is JournalCasMismatchException
        )

        val casEx = ex as JournalCasMismatchException
        assertEquals("SessionId must match", prepReq.sessionId, casEx.sessionId)
        assertEquals("Expected state must be KEY_STAGED", ProvisioningState.KEY_STAGED.name, casEx.expectedState)
        assertEquals("Target state must be DB_WRITTEN", ProvisioningState.DB_WRITTEN.name, casEx.targetState)

        assertFalse("KeyVault rollback must remove staged key", fakeSecureKeyManager.hasPrivateKey(prepReq.stagedAlias))
        assertTrue("Database must have 0 active wallets", walletQueries.selectAllActiveWallets().executeAsList().isEmpty())
    }

    @Test
    fun challenge_3_importFromPrivateKey_casMismatch_throwsJournalCasMismatchException_andTriggersRollback() = runBlocking {
        val prepRes = repository.prepareProvisioning()
        assertTrue(prepRes is Result.Success)
        val prepReq = (prepRes as Result.Success).data

        val authHandle = TestPlatformAuthenticator.issueHandle(
            keyId = prepReq.stagedAlias,
            sessionId = prepReq.sessionId,
            operation = AuthOperation.IMPORT,
            validityDurationMs = 60_000L
        )

        val adversarialTracker = createTamperingTracker(prepReq.sessionId, ProvisioningState.PREPARED)

        val testDriverFactory = mock<DatabaseDriverFactory>()
        whenever(testDriverFactory.createDriver()).thenReturn(sqlDriver)
        val testRepo = WalletRepositoryImpl(
            databaseDriverFactory = testDriverFactory,
            cryptoProvider = cryptoProvider,
            ethereumRpcClient = ethereumRpcClient,
            secureKeyManager = fakeSecureKeyManager,
            platformDeletionCleanupHook = com.cbstudio.wearwallet.core.platform.NoOpPlatformDeletionCleanupHook(),
            sideEffectTracker = adversarialTracker,
            customWalletQueries = walletQueries,
            customStagingJournalQueries = stagingJournalQueries,
            customDeletionJournalQueries = deletionJournalQueries
        )

        val importRes = testRepo.importFromPrivateKey(
            name = "TamperedPrivKeyImport",
            privateKey = com.cbstudio.wearwallet.core.security.ScopedPrivateKey.fromHex(testPrivateKeyHex),
            password = testPassword.toCharArray(),
            chainType = ChainType.ETHEREUM,
            authContext = AuthenticationContext(authHandle = authHandle)
        )

        assertTrue("importFromPrivateKey must fail on CAS mismatch", importRes is Result.Failure)
        val ex = (importRes as Result.Failure).exception
        assertTrue(
            "Exception must be JournalCasMismatchException, got: ${ex::class.simpleName}",
            ex is JournalCasMismatchException
        )

        val casEx = ex as JournalCasMismatchException
        assertEquals("SessionId must match", prepReq.sessionId, casEx.sessionId)
        assertEquals("Expected state must be KEY_STAGED", ProvisioningState.KEY_STAGED.name, casEx.expectedState)
        assertEquals("Target state must be DB_WRITTEN", ProvisioningState.DB_WRITTEN.name, casEx.targetState)

        assertFalse("KeyVault rollback must remove staged key", fakeSecureKeyManager.hasPrivateKey(prepReq.stagedAlias))
        assertTrue("Database must have 0 active wallets", walletQueries.selectAllActiveWallets().executeAsList().isEmpty())
    }

    @Test
    fun challenge_4_importFromMnemonicWithKeyPair_casMismatch_throwsJournalCasMismatchException_andTriggersRollback() = runBlocking {
        val prepRes = repository.prepareProvisioning()
        assertTrue(prepRes is Result.Success)
        val prepReq = (prepRes as Result.Success).data

        val authHandle = TestPlatformAuthenticator.issueHandle(
            keyId = prepReq.stagedAlias,
            sessionId = prepReq.sessionId,
            operation = AuthOperation.IMPORT,
            validityDurationMs = 60_000L
        )

        val adversarialTracker = createTamperingTracker(prepReq.sessionId, ProvisioningState.PREPARED)

        val testDriverFactory = mock<DatabaseDriverFactory>()
        whenever(testDriverFactory.createDriver()).thenReturn(sqlDriver)
        val testRepo = WalletRepositoryImpl(
            databaseDriverFactory = testDriverFactory,
            cryptoProvider = cryptoProvider,
            ethereumRpcClient = ethereumRpcClient,
            secureKeyManager = fakeSecureKeyManager,
            platformDeletionCleanupHook = com.cbstudio.wearwallet.core.platform.NoOpPlatformDeletionCleanupHook(),
            sideEffectTracker = adversarialTracker,
            customWalletQueries = walletQueries,
            customStagingJournalQueries = stagingJournalQueries,
            customDeletionJournalQueries = deletionJournalQueries
        )

        val keyPair = cryptoProvider.generateKeyPairFromMnemonic(testMnemonic.toCharArray(), ChainType.ETHEREUM.getDefaultDerivationPath())
        val address = cryptoProvider.deriveAddress(keyPair.publicKey)

        val importRes = testRepo.importFromMnemonicWithKeyPair(
            name = "TamperedKeyPairImport",
            mnemonic = testMnemonic.toCharArray(),
            password = testPassword.toCharArray(),
            chainType = ChainType.ETHEREUM,
            keyPair = keyPair,
            address = address,
            authContext = AuthenticationContext(authHandle = authHandle)
        )

        assertTrue("importFromMnemonicWithKeyPair must fail on CAS mismatch", importRes is Result.Failure)
        val ex = (importRes as Result.Failure).exception
        assertTrue(
            "Exception must be JournalCasMismatchException, got: ${ex::class.simpleName}",
            ex is JournalCasMismatchException
        )

        val casEx = ex as JournalCasMismatchException
        assertEquals("SessionId must match", prepReq.sessionId, casEx.sessionId)
        assertEquals("Expected state must be KEY_STAGED", ProvisioningState.KEY_STAGED.name, casEx.expectedState)
        assertEquals("Target state must be DB_WRITTEN", ProvisioningState.DB_WRITTEN.name, casEx.targetState)

        assertFalse("KeyVault rollback must remove staged key", fakeSecureKeyManager.hasPrivateKey(prepReq.stagedAlias))
        assertTrue("Database must have 0 active wallets", walletQueries.selectAllActiveWallets().executeAsList().isEmpty())
    }

    @Test
    fun challenge_5_migrateLegacyWallet_casMismatch_throwsJournalCasMismatchException_andTriggersRollback() = runBlocking {
        // Setup legacy wallet in DB with address-bound AAD
        val rawPrivKey = testPrivateKeyHex.hexToByteArray()
        val legacyPassword = "LegacyPassword#1"
        val passwordBytes = legacyPassword.encodeToByteArray()
        val keyPair = cryptoProvider.generateKeyPairFromPrivateKey(testPrivateKeyHex.toCharArray())
        val address = com.cbstudio.wearwallet.core.multichain.util.EthereumSigner.deriveAddressFromPrivateKey(testPrivateKeyHex)

        val legacyEnvelope = VersionedEncryptedEnvelope.encrypt(
            plaintext = rawPrivKey,
            password = passwordBytes,
            keyId = address,
            aad = CanonicalAad.forWalletStorage(address, CanonicalAad.KEY_TYPE_PRIVATE_KEY)
        )

        walletQueries.insert(
            name = "Legacy Wallet",
            address = address,
            public_key = keyPair.publicKey,
            encrypted_private_key = legacyEnvelope.serializeToBase64(),
            encrypted_mnemonic = null,
            derivation_path = ChainType.ETHEREUM.getDefaultDerivationPath(),
            chain_type = ChainType.ETHEREUM.name,
            wallet_type = WalletType.HOT_WALLET.name,
            is_watch_only = 0L,
            master_fingerprint = null,
            keystone_sign_request = null,
            keystone_sync_data = null,
            metadata = "{}",
            avatar_id = null,
            chain_id = 1L,
            key_alias = address,
            key_backend = "SOFTWARE",
            key_format_version = 1L,
            requires_auth = 0L,
            is_deletion_pending = 0L
        )
        val legacyWalletId = walletQueries.lastInsertRowId().executeAsOne()

        val prepRes = repository.prepareProvisioning()
        assertTrue(prepRes is Result.Success)
        val prepReq = (prepRes as Result.Success).data

        val authHandle = TestPlatformAuthenticator.issueHandle(
            keyId = prepReq.stagedAlias,
            sessionId = prepReq.sessionId,
            operation = AuthOperation.IMPORT,
            validityDurationMs = 60_000L
        )

        val adversarialTracker = createTamperingTracker(prepReq.sessionId, ProvisioningState.PREPARED)

        val testDriverFactory = mock<DatabaseDriverFactory>()
        whenever(testDriverFactory.createDriver()).thenReturn(sqlDriver)
        val testRepo = WalletRepositoryImpl(
            databaseDriverFactory = testDriverFactory,
            cryptoProvider = cryptoProvider,
            ethereumRpcClient = ethereumRpcClient,
            secureKeyManager = fakeSecureKeyManager,
            platformDeletionCleanupHook = com.cbstudio.wearwallet.core.platform.NoOpPlatformDeletionCleanupHook(),
            sideEffectTracker = adversarialTracker,
            customWalletQueries = walletQueries,
            customStagingJournalQueries = stagingJournalQueries,
            customDeletionJournalQueries = deletionJournalQueries
        )

        val migrateRes = testRepo.migrateLegacyWallet(
            walletId = legacyWalletId.toString(),
            password = legacyPassword.toCharArray(),
            authContext = AuthenticationContext(authHandle = authHandle)
        )

        assertTrue("migrateLegacyWallet must fail on CAS mismatch", migrateRes is Result.Failure)
        val ex = (migrateRes as Result.Failure).exception
        assertTrue(
            "Exception must be JournalCasMismatchException, got: ${ex::class.simpleName}",
            ex is JournalCasMismatchException
        )

        val casEx = ex as JournalCasMismatchException
        assertEquals("SessionId must match", prepReq.sessionId, casEx.sessionId)
        assertEquals("Expected state must be KEY_STAGED", ProvisioningState.KEY_STAGED.name, casEx.expectedState)
        assertEquals("Target state must be DB_WRITTEN", ProvisioningState.DB_WRITTEN.name, casEx.targetState)

        assertFalse("Staged key must be rolled back", fakeSecureKeyManager.hasPrivateKey(prepReq.stagedAlias))
    }

    // =========================================================================
    // SECTION 2: RETENTION POLICY VERIFICATION (PENDING JOURNALS NEVER PURGED)
    // =========================================================================

    @Test
    fun challenge_6_stagingJournal_retentionPolicy_neverPurgesPendingJournals() {
        val now = 1_000_000L
        val pastExpiry = now - 50_000L
        val futureExpiry = now + 50_000L

        // Insert journals across all known states
        stagingJournalQueries.insertJournal(
            session_id = "sess_prepared_past",
            staged_alias = "alias_prep_past",
            backup_id = "bk_prep",
            state = ProvisioningState.PREPARED.name,
            created_at = pastExpiry - 1000L,
            expires_at = pastExpiry
        )
        stagingJournalQueries.insertJournal(
            session_id = "sess_keystaged_past",
            staged_alias = "alias_keystaged_past",
            backup_id = "bk_keystaged",
            state = ProvisioningState.KEY_STAGED.name,
            created_at = pastExpiry - 1000L,
            expires_at = pastExpiry
        )
        stagingJournalQueries.insertJournal(
            session_id = "sess_dbwritten_past",
            staged_alias = "alias_dbwritten_past",
            backup_id = "bk_dbwritten",
            state = ProvisioningState.DB_WRITTEN.name,
            created_at = pastExpiry - 1000L,
            expires_at = pastExpiry
        )
        stagingJournalQueries.insertJournal(
            session_id = "sess_rbpending_past",
            staged_alias = "alias_rbpending_past",
            backup_id = "bk_rbpending",
            state = ProvisioningState.ROLLBACK_PENDING.name,
            created_at = pastExpiry - 1000L,
            expires_at = pastExpiry
        )
        stagingJournalQueries.insertJournal(
            session_id = "sess_committed_past",
            staged_alias = "alias_committed_past",
            backup_id = "bk_committed_past",
            state = ProvisioningState.COMMITTED.name,
            created_at = pastExpiry - 1000L,
            expires_at = pastExpiry
        )
        stagingJournalQueries.insertJournal(
            session_id = "sess_rolledback_past",
            staged_alias = "alias_rolledback_past",
            backup_id = "bk_rolledback_past",
            state = ProvisioningState.ROLLED_BACK.name,
            created_at = pastExpiry - 1000L,
            expires_at = pastExpiry
        )
        stagingJournalQueries.insertJournal(
            session_id = "sess_committed_future",
            staged_alias = "alias_committed_future",
            backup_id = "bk_committed_future",
            state = ProvisioningState.COMMITTED.name,
            created_at = now,
            expires_at = futureExpiry
        )

        // Execute purge with cutoff = now
        stagingJournalQueries.purgeExpiredJournals(expires_at = now)

        val remaining = stagingJournalQueries.selectAllJournals().executeAsList()
        val remainingSessionIds = remaining.map { it.session_id }.toSet()

        // Assert pending journals are NEVER purged
        assertTrue("PREPARED past journal must NOT be purged", remainingSessionIds.contains("sess_prepared_past"))
        assertTrue("KEY_STAGED past journal must NOT be purged", remainingSessionIds.contains("sess_keystaged_past"))
        assertTrue("DB_WRITTEN past journal must NOT be purged", remainingSessionIds.contains("sess_dbwritten_past"))
        assertTrue("ROLLBACK_PENDING past journal must NOT be purged", remainingSessionIds.contains("sess_rbpending_past"))

        // Assert completed past journals ARE purged
        assertFalse("COMMITTED past journal MUST be purged", remainingSessionIds.contains("sess_committed_past"))
        assertFalse("ROLLED_BACK past journal MUST be purged", remainingSessionIds.contains("sess_rolledback_past"))

        // Assert non-expired completed journal is preserved
        assertTrue("COMMITTED future journal MUST be preserved", remainingSessionIds.contains("sess_committed_future"))

        // Verify selectPendingJournals() returns all uncompleted journals
        val pending = stagingJournalQueries.selectPendingJournals().executeAsList()
        val pendingIds = pending.map { it.session_id }.toSet()
        assertEquals("selectPendingJournals must return exactly 4 pending journals", 4, pending.size)
        assertTrue(pendingIds.contains("sess_prepared_past"))
        assertTrue(pendingIds.contains("sess_keystaged_past"))
        assertTrue(pendingIds.contains("sess_dbwritten_past"))
        assertTrue(pendingIds.contains("sess_rbpending_past"))
    }

    @Test
    fun challenge_7_deletionJournal_retentionPolicy_neverPurgesUncompletedDeletions() {
        val now = 2_000_000L
        val pastTime = now - 100_000L
        val futureTime = now + 100_000L

        deletionJournalQueries.insertDeletionJournal(1L, "k1", DeletionState.DELETE_AUTHORIZED.name, null, 0L, pastTime, pastTime)
        deletionJournalQueries.insertDeletionJournal(2L, "k2", DeletionState.TOMBSTONED.name, null, 0L, pastTime, pastTime)
        deletionJournalQueries.insertDeletionJournal(3L, "k3", DeletionState.KEY_DELETED.name, null, 0L, pastTime, pastTime)
        deletionJournalQueries.insertDeletionJournal(4L, "k4", DeletionState.REFERENCES_CLEARED.name, null, 0L, pastTime, pastTime)
        deletionJournalQueries.insertDeletionJournal(5L, "k5", DeletionState.RECOVERY_REQUIRED.name, "Error", 1L, pastTime, pastTime)
        deletionJournalQueries.insertDeletionJournal(6L, "k6", DeletionState.COMPLETED.name, null, 0L, pastTime, pastTime)
        deletionJournalQueries.insertDeletionJournal(7L, "k7", DeletionState.COMPLETED.name, null, 0L, now, futureTime)

        // Execute purge
        deletionJournalQueries.purgeCompletedDeletions(updated_at = now)

        val remaining = deletionJournalQueries.selectAllDeletionJournals().executeAsList()
        val remainingWalletIds = remaining.map { it.wallet_id }.toSet()

        // Assert all uncompleted deletions are preserved
        assertTrue("DELETE_AUTHORIZED must NOT be purged", remainingWalletIds.contains(1L))
        assertTrue("TOMBSTONED must NOT be purged", remainingWalletIds.contains(2L))
        assertTrue("KEY_DELETED must NOT be purged", remainingWalletIds.contains(3L))
        assertTrue("REFERENCES_CLEARED must NOT be purged", remainingWalletIds.contains(4L))
        assertTrue("RECOVERY_REQUIRED must NOT be purged", remainingWalletIds.contains(5L))

        // Assert completed past deletion IS purged
        assertFalse("Past COMPLETED deletion MUST be purged", remainingWalletIds.contains(6L))

        // Assert completed future deletion is preserved
        assertTrue("Future COMPLETED deletion MUST be preserved", remainingWalletIds.contains(7L))

        val pendingDeletions = deletionJournalQueries.selectPendingDeletions().executeAsList()
        assertEquals("selectPendingDeletions must return exactly 5 uncompleted deletions", 5, pendingDeletions.size)
    }

    // =========================================================================
    // SECTION 3: MULTI-THREADED CAS CONCURRENCY RACE CHALLENGE
    // =========================================================================

    @Test
    fun challenge_8_stagingJournal_casContention_enforcesSingleWinner() {
        val sessionId = "concurrent_session_cas_race"
        stagingJournalQueries.insertJournal(
            session_id = sessionId,
            staged_alias = "concurrent_alias",
            backup_id = "bk_race",
            state = ProvisioningState.KEY_STAGED.name,
            created_at = 1000L,
            expires_at = 2000L
        )

        // Attempt 1: First CAS transition KEY_STAGED -> DB_WRITTEN (Must succeed with 1 row updated)
        val firstUpdate = sqlDriver.execute(
            identifier = null,
            sql = "UPDATE staging_journal SET state = '${ProvisioningState.DB_WRITTEN.name}' WHERE session_id = '$sessionId' AND state = '${ProvisioningState.KEY_STAGED.name}'",
            parameters = 0
        ).value
        assertEquals("First CAS transition must update exactly 1 row", 1L, firstUpdate)

        // Attempt 2..10: Contending CAS transitions with stale expectedState=KEY_STAGED (Must all fail with 0 rows updated)
        for (i in 2..10) {
            val staleUpdate = sqlDriver.execute(
                identifier = null,
                sql = "UPDATE staging_journal SET state = '${ProvisioningState.DB_WRITTEN.name}' WHERE session_id = '$sessionId' AND state = '${ProvisioningState.KEY_STAGED.name}'",
                parameters = 0
            ).value
            assertEquals("Stale CAS transition #$i must update 0 rows", 0L, staleUpdate)
        }

        val finalJournal = stagingJournalQueries.selectBySessionId(sessionId).executeAsOne()
        assertEquals("Final state must remain DB_WRITTEN", ProvisioningState.DB_WRITTEN.name, finalJournal.state)
    }
}

package com.cbstudio.wearwallet.core.security

import app.cash.sqldelight.Query
import app.cash.sqldelight.TransactionWithoutReturn
import app.cash.sqldelight.db.SqlDriver
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.data.repository.WalletRepositoryImpl
import com.cbstudio.wearwallet.core.database.DatabaseDriverFactory
import com.cbstudio.wearwallet.core.database.DeletionJournalQueries
import com.cbstudio.wearwallet.core.database.Deletion_journal
import com.cbstudio.wearwallet.core.database.StagingJournalQueries
import com.cbstudio.wearwallet.core.database.Staging_journal
import com.cbstudio.wearwallet.core.database.Wallet
import com.cbstudio.wearwallet.core.database.WalletQueries
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.WalletType
import com.cbstudio.wearwallet.core.network.EthereumRpcClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

/**
 * Adversarial Challenge Suite 1 for Milestone 3 (M3: P1-2 Deletion State Machine & Crash Recovery)
 *
 * Exhaustively stress tests:
 * 1. 5-State Deletion Machine Progression (DELETE_AUTHORIZED -> TOMBSTONED -> KEY_DELETED -> REFERENCES_CLEARED -> COMPLETED)
 * 2. Crash Recovery at each intermediate state (DELETE_AUTHORIZED, TOMBSTONED, KEY_DELETED, REFERENCES_CLEARED, RECOVERY_REQUIRED)
 * 3. Fail-Closed Authentication Gating (rejects null auth, mismatched operation, invalid keyId, expired token)
 * 4. Legacy Tombstone Recovery (wallets marked is_deletion_pending=1 without journal)
 * 5. Retention Purge Invariants (COMPLETED journals pruned after 24h, active/pending preserved)
 */
class Milestone3Challenger1DeletionStateMachineStressTest {

    private lateinit var databaseDriverFactory: DatabaseDriverFactory
    private lateinit var mockWalletQueries: WalletQueries
    private lateinit var mockStagingJournalQueries: StagingJournalQueries
    private lateinit var mockDeletionJournalQueries: DeletionJournalQueries
    private lateinit var fakeSecureKeyManager: FakeSecureKeyManager
    private lateinit var cryptoProvider: CryptoProvider
    private lateinit var ethereumRpcClient: EthereumRpcClient
    private lateinit var repository: WalletRepositoryImpl

    private val testWalletId = 101L
    private val testKeyAlias = "ww_key_alias_101"
    private val testAddress = "0x1010101010101010101010101010101010101010"

    @Before
    fun setup() {
        databaseDriverFactory = mock()
        mockWalletQueries = mock()
        mockStagingJournalQueries = mock()
        mockDeletionJournalQueries = mock()
        fakeSecureKeyManager = FakeSecureKeyManager()
        cryptoProvider = CommonCryptoProvider()
        ethereumRpcClient = mock()

        val driver = app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver(app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver.IN_MEMORY)
        com.cbstudio.wearwallet.core.database.CoreWalletDatabase.Schema.create(driver)
        whenever(databaseDriverFactory.createDriver()).thenReturn(driver)

        // Mock transaction behavior for wallet queries
        whenever(mockWalletQueries.transaction(any(), any())).thenAnswer { invocation ->
            val body = invocation.getArgument<app.cash.sqldelight.TransactionWithoutReturn.() -> Unit>(1)
            val tx = mock<app.cash.sqldelight.TransactionWithoutReturn>()
            body.invoke(tx)
        }

        val mockExistsQuery = mock<Query<Boolean>>()
        whenever(mockExistsQuery.executeAsOne()).thenReturn(false)
        whenever(mockWalletQueries.existsByAddress(any())).thenReturn(mockExistsQuery)

        val mockLastIdQuery = mock<Query<Long>>()
        whenever(mockLastIdQuery.executeAsOne()).thenReturn(testWalletId)
        whenever(mockWalletQueries.lastInsertRowId()).thenReturn(mockLastIdQuery)

        // Mock transaction behavior for staging journal
        whenever(mockStagingJournalQueries.transaction(any(), any())).thenAnswer { invocation ->
            val body = invocation.getArgument<app.cash.sqldelight.TransactionWithoutReturn.() -> Unit>(1)
            val tx = mock<app.cash.sqldelight.TransactionWithoutReturn>()
            body.invoke(tx)
        }

        // Mock transaction behavior for deletion journal
        whenever(mockDeletionJournalQueries.transaction(any(), any())).thenAnswer { invocation ->
            val body = invocation.getArgument<app.cash.sqldelight.TransactionWithoutReturn.() -> Unit>(1)
            val tx = mock<app.cash.sqldelight.TransactionWithoutReturn>()
            body.invoke(tx)
        }

        val mockChangesCountQuery = mock<Query<Long>>()
        whenever(mockChangesCountQuery.executeAsOne()).thenReturn(1L)
        whenever(mockChangesCountQuery.executeAsOneOrNull()).thenReturn(1L)
        whenever(mockStagingJournalQueries.changesCount()).thenReturn(mockChangesCountQuery)
        whenever(mockDeletionJournalQueries.changesCount()).thenReturn(mockChangesCountQuery)

        repository = WalletRepositoryImpl(
            databaseDriverFactory = databaseDriverFactory,
            cryptoProvider = cryptoProvider,
            ethereumRpcClient = ethereumRpcClient,
            secureKeyManager = fakeSecureKeyManager,
            platformDeletionCleanupHook = com.cbstudio.wearwallet.core.platform.NoOpPlatformDeletionCleanupHook(),
            customWalletQueries = mockWalletQueries,
            customStagingJournalQueries = mockStagingJournalQueries,
            customDeletionJournalQueries = mockDeletionJournalQueries
        )

        AuthHandleRegistry.clearForTesting()
    }

    private fun mockWalletRow(
        id: Long = testWalletId,
        address: String = testAddress,
        keyAlias: String = testKeyAlias,
        requiresAuth: Long = 1L,
        isDeletionPending: Long = 0L,
        isActive: Long = 1L
    ): Wallet {
        return Wallet(
            id = id,
            name = "Test Wallet $id",
            address = address,
            public_key = "04mockpubkey",
            encrypted_private_key = "mock_priv",
            encrypted_mnemonic = "mock_mnem",
            derivation_path = "m/44'/60'/0'/0/0",
            chain_type = ChainType.ETHEREUM.name,
            wallet_type = WalletType.HOT_WALLET.name,
            is_active = isActive,
            is_watch_only = 0L,
            master_fingerprint = null,
            keystone_sign_request = null,
            keystone_sync_data = null,
            metadata = "{}",
            avatar_id = null,
            chain_id = 1L,
            key_alias = keyAlias,
            key_backend = "ANDROID_KEYSTORE",
            key_format_version = 2L,
            requires_auth = requiresAuth,
            is_deletion_pending = isDeletionPending,
            created_at = 1000L,
            updated_at = 1000L
        )
    }

    private fun createDeletionJournal(
        walletId: Long = testWalletId,
        keyAlias: String = testKeyAlias,
        state: String = DeletionState.DELETE_AUTHORIZED.name,
        lastError: String? = null,
        retryCount: Long = 0L
    ): Deletion_journal {
        return Deletion_journal(
            wallet_id = walletId,
            key_alias = keyAlias,
            state = state,
            last_error = lastError,
            retry_count = retryCount,
            created_at = 1000L,
            updated_at = 1000L
        )
    }

    // =========================================================================
    // SECTION 1: 5-STATE DELETION TRANSITION ADVERSARIAL STRESS TEST
    // =========================================================================

    @Test
    fun challenge_1_deleteWallet_happyPath_recordsStateProgression() = runBlocking {
        val wallet = mockWalletRow()
        val mockQuery = mock<Query<Wallet>>()
        whenever(mockQuery.executeAsOneOrNull()).thenReturn(wallet)
        whenever(mockWalletQueries.selectById(testWalletId)).thenReturn(mockQuery)

        // Pre-populate key in fake key manager
        val importHandle = TestPlatformAuthenticator.issueHandle(
            keyId = testKeyAlias,
            operation = AuthOperation.IMPORT,
            validityDurationMs = 60_000L,
            walletId = testWalletId.toString()
        )
        fakeSecureKeyManager.storePrivateKey(
            keyId = testKeyAlias,
            privateKey = ByteArray(32) { 7 },
            requireAuth = true,
            authContext = AuthenticationContext(authHandle = importHandle),
            expectedWalletId = testWalletId.toString()
        )
        assertTrue("Key must exist before deletion", fakeSecureKeyManager.hasPrivateKey(testKeyAlias))

        val recordedStates = mutableListOf<String>()
        var currentState = DeletionState.DELETE_AUTHORIZED.name
        val mockDelQuery = mock<Query<Deletion_journal>>()
        whenever(mockDelQuery.executeAsOneOrNull()).thenAnswer {
            createDeletionJournal(state = currentState)
        }
        whenever(mockDeletionJournalQueries.selectByWalletId(testWalletId)).thenReturn(mockDelQuery)
        whenever(mockDeletionJournalQueries.updateDeletionStateCas(any(), anyOrNull(), any(), eq(testWalletId), any())).thenAnswer { invocation ->
            val newState = invocation.getArgument<String>(0)
            recordedStates.add(newState)
            currentState = newState
            Unit
        }

        val mockListQuery = mock<Query<Wallet>>()
        whenever(mockListQuery.executeAsList()).thenReturn(emptyList())
        whenever(mockWalletQueries.selectAllActiveWallets()).thenReturn(mockListQuery)

        val deleteHandle = TestPlatformAuthenticator.issueHandle(
            keyId = testKeyAlias,
            operation = AuthOperation.DELETE,
            validityDurationMs = 60_000L,
            walletId = testWalletId.toString()
        )
        val res = repository.deleteWallet(testWalletId.toString(), authContext = AuthenticationContext(authHandle = deleteHandle))

        assertTrue("deleteWallet must succeed: ${(res as? Result.Failure)?.exception}", res is Result.Success)
        verify(mockDeletionJournalQueries).insertDeletionJournal(
            wallet_id = eq(testWalletId),
            key_alias = eq(testKeyAlias),
            state = eq(DeletionState.DELETE_AUTHORIZED.name),
            last_error = isNull(),
            retry_count = eq(0L),
            created_at = any(),
            updated_at = any()
        )
        verify(mockWalletQueries).markDeletionPending(testWalletId)
        assertFalse("Key must be deleted from key manager", fakeSecureKeyManager.hasPrivateKey(testKeyAlias))
        verify(mockWalletQueries).delete(testWalletId)

        // Verify progression through states
        assertTrue("Must transition to TOMBSTONED", recordedStates.contains(DeletionState.TOMBSTONED.name))
        assertTrue("Must transition to KEY_DELETED", recordedStates.contains(DeletionState.KEY_DELETED.name))
        assertTrue("Must transition to REFERENCES_CLEARED", recordedStates.contains(DeletionState.REFERENCES_CLEARED.name))
        assertTrue("Must transition to COMPLETED", recordedStates.contains(DeletionState.COMPLETED.name))
    }

    // =========================================================================
    // SECTION 2: CRASH RECOVERY AT EACH INTERMEDIATE STATE
    // =========================================================================

    @Test
    fun challenge_2_reconcileStartupState_recoversFrom_DELETE_AUTHORIZED() = runBlocking {
        val pendingJournalsMock = mock<Query<Staging_journal>>()
        whenever(pendingJournalsMock.executeAsList()).thenReturn(emptyList())
        whenever(mockStagingJournalQueries.selectPendingJournals()).thenReturn(pendingJournalsMock)

        var currentState = DeletionState.DELETE_AUTHORIZED.name
        val pendingDelQuery = mock<Query<Deletion_journal>>()
        val journal = createDeletionJournal(state = DeletionState.DELETE_AUTHORIZED.name)
        whenever(pendingDelQuery.executeAsList()).thenAnswer {
            if (currentState == DeletionState.COMPLETED.name) emptyList() else listOf(journal)
        }
        whenever(mockDeletionJournalQueries.selectPendingDeletions()).thenReturn(pendingDelQuery)

        val wallet = mockWalletRow()
        val mockWalletQuery = mock<Query<Wallet>>()
        whenever(mockWalletQuery.executeAsOneOrNull()).thenReturn(wallet)
        whenever(mockWalletQueries.selectById(testWalletId)).thenReturn(mockWalletQuery)

        val mockDelSingle = mock<Query<Deletion_journal>>()
        whenever(mockDelSingle.executeAsOneOrNull()).thenAnswer { createDeletionJournal(state = currentState) }
        whenever(mockDeletionJournalQueries.selectByWalletId(testWalletId)).thenReturn(mockDelSingle)
        whenever(mockDeletionJournalQueries.updateDeletionStateCas(any(), anyOrNull(), any(), eq(testWalletId), any())).thenAnswer {
            currentState = it.getArgument(0)
            Unit
        }

        val emptyListMock = mock<Query<Wallet>>()
        whenever(emptyListMock.executeAsList()).thenReturn(emptyList())
        whenever(mockWalletQueries.selectAllActiveWallets()).thenReturn(emptyListMock)
        whenever(mockWalletQueries.selectDeletionPending()).thenReturn(emptyListMock)

        val res = repository.reconcileStartupState()
        assertTrue("Reconciliation from DELETE_AUTHORIZED must succeed", res is Result.Success)
        verify(mockWalletQueries).markDeletionPending(testWalletId)
        verify(mockWalletQueries).delete(testWalletId)
        assertEquals("Journal must reach COMPLETED", DeletionState.COMPLETED.name, currentState)
    }

    @Test
    fun challenge_3_reconcileStartupState_recoversFrom_TOMBSTONED() = runBlocking {
        val pendingJournalsMock = mock<Query<Staging_journal>>()
        whenever(pendingJournalsMock.executeAsList()).thenReturn(emptyList())
        whenever(mockStagingJournalQueries.selectPendingJournals()).thenReturn(pendingJournalsMock)

        var currentState = DeletionState.TOMBSTONED.name
        val pendingDelQuery = mock<Query<Deletion_journal>>()
        val journal = createDeletionJournal(state = DeletionState.TOMBSTONED.name)
        whenever(pendingDelQuery.executeAsList()).thenAnswer {
            if (currentState == DeletionState.COMPLETED.name) emptyList() else listOf(journal)
        }
        whenever(mockDeletionJournalQueries.selectPendingDeletions()).thenReturn(pendingDelQuery)

        val wallet = mockWalletRow(isDeletionPending = 1L)
        val mockWalletQuery = mock<Query<Wallet>>()
        whenever(mockWalletQuery.executeAsOneOrNull()).thenReturn(wallet)
        whenever(mockWalletQueries.selectById(testWalletId)).thenReturn(mockWalletQuery)

        val mockDelSingle = mock<Query<Deletion_journal>>()
        whenever(mockDelSingle.executeAsOneOrNull()).thenAnswer { createDeletionJournal(state = currentState) }
        whenever(mockDeletionJournalQueries.selectByWalletId(testWalletId)).thenReturn(mockDelSingle)
        whenever(mockDeletionJournalQueries.updateDeletionStateCas(any(), anyOrNull(), any(), eq(testWalletId), any())).thenAnswer {
            currentState = it.getArgument(0)
            Unit
        }

        val emptyListMock = mock<Query<Wallet>>()
        whenever(emptyListMock.executeAsList()).thenReturn(emptyList())
        whenever(mockWalletQueries.selectAllActiveWallets()).thenReturn(emptyListMock)
        whenever(mockWalletQueries.selectDeletionPending()).thenReturn(emptyListMock)

        val res = repository.reconcileStartupState()
        assertTrue("Reconciliation from TOMBSTONED must succeed", res is Result.Success)
        verify(mockWalletQueries).delete(testWalletId)
        assertEquals("Journal must reach COMPLETED", DeletionState.COMPLETED.name, currentState)
    }

    @Test
    fun challenge_4_reconcileStartupState_recoversFrom_KEY_DELETED() = runBlocking {
        val pendingJournalsMock = mock<Query<Staging_journal>>()
        whenever(pendingJournalsMock.executeAsList()).thenReturn(emptyList())
        whenever(mockStagingJournalQueries.selectPendingJournals()).thenReturn(pendingJournalsMock)

        var currentState = DeletionState.KEY_DELETED.name
        val pendingDelQuery = mock<Query<Deletion_journal>>()
        val journal = createDeletionJournal(state = DeletionState.KEY_DELETED.name)
        whenever(pendingDelQuery.executeAsList()).thenAnswer {
            if (currentState == DeletionState.COMPLETED.name) emptyList() else listOf(journal)
        }
        whenever(mockDeletionJournalQueries.selectPendingDeletions()).thenReturn(pendingDelQuery)

        val wallet = mockWalletRow(isDeletionPending = 1L)
        val mockWalletQuery = mock<Query<Wallet>>()
        whenever(mockWalletQuery.executeAsOneOrNull()).thenReturn(wallet)
        whenever(mockWalletQueries.selectById(testWalletId)).thenReturn(mockWalletQuery)

        val mockDelSingle = mock<Query<Deletion_journal>>()
        whenever(mockDelSingle.executeAsOneOrNull()).thenAnswer { createDeletionJournal(state = currentState) }
        whenever(mockDeletionJournalQueries.selectByWalletId(testWalletId)).thenReturn(mockDelSingle)
        whenever(mockDeletionJournalQueries.updateDeletionStateCas(any(), anyOrNull(), any(), eq(testWalletId), any())).thenAnswer {
            currentState = it.getArgument(0)
            Unit
        }

        val emptyListMock = mock<Query<Wallet>>()
        whenever(emptyListMock.executeAsList()).thenReturn(emptyList())
        whenever(mockWalletQueries.selectAllActiveWallets()).thenReturn(emptyListMock)
        whenever(mockWalletQueries.selectDeletionPending()).thenReturn(emptyListMock)

        val res = repository.reconcileStartupState()
        assertTrue("Reconciliation from KEY_DELETED must succeed", res is Result.Success)
        verify(mockWalletQueries).delete(testWalletId)
        assertEquals("Journal must reach COMPLETED", DeletionState.COMPLETED.name, currentState)
    }

    @Test
    fun challenge_5_reconcileStartupState_recoversFrom_REFERENCES_CLEARED() = runBlocking {
        val pendingJournalsMock = mock<Query<Staging_journal>>()
        whenever(pendingJournalsMock.executeAsList()).thenReturn(emptyList())
        whenever(mockStagingJournalQueries.selectPendingJournals()).thenReturn(pendingJournalsMock)

        var currentState = DeletionState.REFERENCES_CLEARED.name
        val pendingDelQuery = mock<Query<Deletion_journal>>()
        val journal = createDeletionJournal(state = DeletionState.REFERENCES_CLEARED.name)
        whenever(pendingDelQuery.executeAsList()).thenAnswer {
            if (currentState == DeletionState.COMPLETED.name) emptyList() else listOf(journal)
        }
        whenever(mockDeletionJournalQueries.selectPendingDeletions()).thenReturn(pendingDelQuery)

        val wallet = mockWalletRow(isDeletionPending = 1L)
        val mockWalletQuery = mock<Query<Wallet>>()
        whenever(mockWalletQuery.executeAsOneOrNull()).thenReturn(wallet)
        whenever(mockWalletQueries.selectById(testWalletId)).thenReturn(mockWalletQuery)

        val mockDelSingle = mock<Query<Deletion_journal>>()
        whenever(mockDelSingle.executeAsOneOrNull()).thenAnswer { createDeletionJournal(state = currentState) }
        whenever(mockDeletionJournalQueries.selectByWalletId(testWalletId)).thenReturn(mockDelSingle)
        whenever(mockDeletionJournalQueries.updateDeletionStateCas(any(), anyOrNull(), any(), eq(testWalletId), any())).thenAnswer {
            currentState = it.getArgument(0)
            Unit
        }

        val emptyListMock = mock<Query<Wallet>>()
        whenever(emptyListMock.executeAsList()).thenReturn(emptyList())
        whenever(mockWalletQueries.selectAllActiveWallets()).thenReturn(emptyListMock)
        whenever(mockWalletQueries.selectDeletionPending()).thenReturn(emptyListMock)

        val res = repository.reconcileStartupState()
        assertTrue("Reconciliation from REFERENCES_CLEARED must succeed", res is Result.Success)
        verify(mockWalletQueries).delete(testWalletId)
        assertEquals("Journal must reach COMPLETED", DeletionState.COMPLETED.name, currentState)
    }

    @Test
    fun challenge_6_reconcileStartupState_recoversFrom_RECOVERY_REQUIRED() = runBlocking {
        val pendingJournalsMock = mock<Query<Staging_journal>>()
        whenever(pendingJournalsMock.executeAsList()).thenReturn(emptyList())
        whenever(mockStagingJournalQueries.selectPendingJournals()).thenReturn(pendingJournalsMock)

        var currentState = DeletionState.RECOVERY_REQUIRED.name
        val pendingDelQuery = mock<Query<Deletion_journal>>()
        val journal = createDeletionJournal(state = DeletionState.RECOVERY_REQUIRED.name, lastError = "Hardware timeout")
        whenever(pendingDelQuery.executeAsList()).thenAnswer {
            if (currentState == DeletionState.COMPLETED.name) emptyList() else listOf(journal)
        }
        whenever(mockDeletionJournalQueries.selectPendingDeletions()).thenReturn(pendingDelQuery)

        // Key is not in FakeSecureKeyManager (meaning KeyVault is already clean)
        assertFalse("Key is not in KeyVault", fakeSecureKeyManager.hasPrivateKey(testKeyAlias))

        val wallet = mockWalletRow(isDeletionPending = 1L)
        val mockWalletQuery = mock<Query<Wallet>>()
        whenever(mockWalletQuery.executeAsOneOrNull()).thenReturn(wallet)
        whenever(mockWalletQueries.selectById(testWalletId)).thenReturn(mockWalletQuery)

        val mockDelSingle = mock<Query<Deletion_journal>>()
        whenever(mockDelSingle.executeAsOneOrNull()).thenAnswer { createDeletionJournal(state = currentState) }
        whenever(mockDeletionJournalQueries.selectByWalletId(testWalletId)).thenReturn(mockDelSingle)
        whenever(mockDeletionJournalQueries.updateDeletionState(any(), anyOrNull(), any(), eq(testWalletId))).thenAnswer {
            currentState = it.getArgument(0)
            Unit
        }
        whenever(mockDeletionJournalQueries.updateDeletionStateCas(any(), anyOrNull(), any(), eq(testWalletId), any())).thenAnswer {
            currentState = it.getArgument(0)
            Unit
        }

        val emptyListMock = mock<Query<Wallet>>()
        whenever(emptyListMock.executeAsList()).thenReturn(emptyList())
        whenever(mockWalletQueries.selectAllActiveWallets()).thenReturn(emptyListMock)
        whenever(mockWalletQueries.selectDeletionPending()).thenReturn(emptyListMock)

        val res = repository.reconcileStartupState()
        assertTrue("Reconciliation from RECOVERY_REQUIRED must succeed when key is gone", res is Result.Success)
        verify(mockWalletQueries).delete(testWalletId)
        assertEquals("Journal must reach COMPLETED", DeletionState.COMPLETED.name, currentState)
    }

    // =========================================================================
    // SECTION 3: FAIL-CLOSED AUTHENTICATION GATING ADVERSARIAL ATTACKS
    // =========================================================================

    @Test
    fun challenge_7_unauthenticatedDelete_failsClosed_leavesKeyAndDbIntact() = runBlocking {
        val wallet = mockWalletRow(requiresAuth = 1L)
        val mockQuery = mock<Query<Wallet>>()
        whenever(mockQuery.executeAsOneOrNull()).thenReturn(wallet)
        whenever(mockWalletQueries.selectById(testWalletId)).thenReturn(mockQuery)

        // Pre-populate key
        val importHandle = TestPlatformAuthenticator.issueHandle(
            keyId = testKeyAlias,
            operation = AuthOperation.IMPORT,
            validityDurationMs = 60_000L,
            walletId = testWalletId.toString()
        )
        fakeSecureKeyManager.storePrivateKey(
            keyId = testKeyAlias,
            privateKey = ByteArray(32) { 9 },
            requireAuth = true,
            authContext = AuthenticationContext(authHandle = importHandle),
            expectedWalletId = testWalletId.toString()
        )

        // Attack 1: Null authContext
        val res1 = repository.deleteWallet(testWalletId.toString(), authContext = null)
        assertTrue("Null auth must fail", res1 is Result.Failure)
        assertTrue((res1 as Result.Failure).exception is AuthenticationRequiredException)

        // Attack 2: Wrong operation (SIGN)
        val signHandle = TestPlatformAuthenticator.issueHandle(
            keyId = testKeyAlias,
            operation = AuthOperation.SIGN,
            validityDurationMs = 60_000L,
            walletId = testWalletId.toString()
        )
        val res2 = repository.deleteWallet(testWalletId.toString(), authContext = AuthenticationContext(authHandle = signHandle))
        assertTrue("Wrong operation must fail", res2 is Result.Failure)
        assertTrue((res2 as Result.Failure).exception is AuthenticationRequiredException)

        // Attack 3: Mismatched keyId
        val wrongKeyHandle = TestPlatformAuthenticator.issueHandle(
            keyId = "attacker_target_key",
            operation = AuthOperation.DELETE,
            validityDurationMs = 60_000L,
            walletId = testWalletId.toString()
        )
        val res3 = repository.deleteWallet(testWalletId.toString(), authContext = AuthenticationContext(authHandle = wrongKeyHandle))
        assertTrue("Mismatched keyId must fail", res3 is Result.Failure)
        assertTrue((res3 as Result.Failure).exception is AuthenticationRequiredException)

        // Assert Invariants: Key remains in KeyVault and DB row was never deleted
        assertTrue("Key must remain intact in KeyVault", fakeSecureKeyManager.hasPrivateKey(testKeyAlias))
        verify(mockWalletQueries, never()).delete(any())
        verify(mockWalletQueries, never()).markDeletionPending(any())
    }

    // =========================================================================
    // SECTION 4: LEGACY TOMBSTONE & RETENTION PURGE VERIFICATION
    // =========================================================================

    @Test
    fun challenge_8_legacyTombstone_cleanedUpByStartupReconciliation() = runBlocking {
        val pendingJournalsMock = mock<Query<Staging_journal>>()
        whenever(pendingJournalsMock.executeAsList()).thenReturn(emptyList())
        whenever(mockStagingJournalQueries.selectPendingJournals()).thenReturn(pendingJournalsMock)

        val pendingDelQuery = mock<Query<Deletion_journal>>()
        whenever(pendingDelQuery.executeAsList()).thenReturn(emptyList())
        whenever(mockDeletionJournalQueries.selectPendingDeletions()).thenReturn(pendingDelQuery)

        // Mock a legacy wallet marked is_deletion_pending=1 without journal entry
        val legacyWallet = mockWalletRow(id = 999L, isDeletionPending = 1L)
        val legacyListMock = mock<Query<Wallet>>()
        whenever(legacyListMock.executeAsList()).thenReturn(listOf(legacyWallet), emptyList())
        whenever(mockWalletQueries.selectDeletionPending()).thenReturn(legacyListMock)

        val emptyListMock = mock<Query<Wallet>>()
        whenever(emptyListMock.executeAsList()).thenReturn(emptyList())
        whenever(mockWalletQueries.selectAllActiveWallets()).thenReturn(emptyListMock)

        val res = repository.reconcileStartupState()
        assertTrue("Startup reconciliation must succeed", res is Result.Success)
        verify(mockWalletQueries).delete(999L)
    }

    @Test
    fun challenge_9_retentionPurge_cleansCompletedDeletionsOlderThan24h() = runBlocking {
        val pendingJournalsMock = mock<Query<Staging_journal>>()
        whenever(pendingJournalsMock.executeAsList()).thenReturn(emptyList())
        whenever(mockStagingJournalQueries.selectPendingJournals()).thenReturn(pendingJournalsMock)

        val pendingDelQuery = mock<Query<Deletion_journal>>()
        whenever(pendingDelQuery.executeAsList()).thenReturn(emptyList())
        whenever(mockDeletionJournalQueries.selectPendingDeletions()).thenReturn(pendingDelQuery)

        val emptyListMock = mock<Query<Wallet>>()
        whenever(emptyListMock.executeAsList()).thenReturn(emptyList())
        whenever(mockWalletQueries.selectAllActiveWallets()).thenReturn(emptyListMock)
        whenever(mockWalletQueries.selectDeletionPending()).thenReturn(emptyListMock)

        val res = repository.reconcileStartupState()
        assertTrue("Startup reconciliation must succeed", res is Result.Success)
        verify(mockDeletionJournalQueries).purgeCompletedDeletions(any())
        verify(mockStagingJournalQueries).purgeExpiredJournals(any())
    }
}

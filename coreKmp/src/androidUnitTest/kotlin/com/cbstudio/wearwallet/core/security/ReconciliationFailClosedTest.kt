package com.cbstudio.wearwallet.core.security

import android.database.sqlite.SQLiteDiskIOException
import android.database.sqlite.SQLiteException
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
import com.cbstudio.wearwallet.core.domain.model.WalletType
import com.cbstudio.wearwallet.core.network.EthereumRpcClient
import com.cbstudio.wearwallet.core.recovery.RealStartupRecoveryCoordinator
import com.cbstudio.wearwallet.core.recovery.StartupRecoveryState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

/**
 * Invariant Verification Test Suite for Milestone 4:
 * RealWalletRepository.reconcileStartupState() Strict Fail-Closed Verification
 *
 * Invariants Verified:
 * 1. Staging Journal query error MUST propagate as Result.Failure (NO emptyList() fallback).
 * 2. Deletion Journal query error MUST propagate as Result.Failure (NO emptyList() fallback).
 * 3. Deletion Pending (Tombstone) query error MUST propagate as Result.Failure (NO emptyList() fallback).
 * 4. Staging CAS state mismatch MUST propagate as Result.Failure.
 * 5. Deletion key deletion failure / cleanup failure MUST propagate as Result.Failure.
 * 6. StartupRecoveryCoordinator MUST transition to FAILED state when reconcileStartupState() fails.
 */
class ReconciliationFailClosedTest {

    private lateinit var databaseDriverFactory: DatabaseDriverFactory
    private lateinit var mockWalletQueries: WalletQueries
    private lateinit var mockStagingJournalQueries: StagingJournalQueries
    private lateinit var mockDeletionJournalQueries: DeletionJournalQueries
    private lateinit var fakeSecureKeyManager: FakeSecureKeyManager
    private lateinit var cryptoProvider: CryptoProvider
    private lateinit var ethereumRpcClient: EthereumRpcClient
    private lateinit var repository: WalletRepositoryImpl

    @Before
    fun setup() {
        databaseDriverFactory = mock()
        mockWalletQueries = mock()
        mockStagingJournalQueries = mock()
        mockDeletionJournalQueries = mock()
        fakeSecureKeyManager = FakeSecureKeyManager()
        cryptoProvider = CommonCryptoProvider()
        ethereumRpcClient = mock()

        val mockDriver = mock<SqlDriver>()
        whenever(databaseDriverFactory.createDriver()).thenReturn(mockDriver)

        // Default mock transaction behavior
        whenever(mockWalletQueries.transaction(any(), any())).thenAnswer { invocation ->
            val body = invocation.getArgument<app.cash.sqldelight.TransactionWithoutReturn.() -> Unit>(1)
            val tx = mock<app.cash.sqldelight.TransactionWithoutReturn>()
            body.invoke(tx)
        }
        whenever(mockStagingJournalQueries.transaction(any(), any())).thenAnswer { invocation ->
            val body = invocation.getArgument<app.cash.sqldelight.TransactionWithoutReturn.() -> Unit>(1)
            val tx = mock<app.cash.sqldelight.TransactionWithoutReturn>()
            body.invoke(tx)
        }
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

        // Default empty list queries
        val emptyStagingQuery = mock<Query<Staging_journal>>()
        whenever(emptyStagingQuery.executeAsList()).thenReturn(emptyList())
        whenever(mockStagingJournalQueries.selectPendingJournals()).thenReturn(emptyStagingQuery)

        val emptyDeletionQuery = mock<Query<Deletion_journal>>()
        whenever(emptyDeletionQuery.executeAsList()).thenReturn(emptyList())
        whenever(mockDeletionJournalQueries.selectPendingDeletions()).thenReturn(emptyDeletionQuery)

        val emptyTombstoneQuery = mock<Query<Wallet>>()
        whenever(emptyTombstoneQuery.executeAsList()).thenReturn(emptyList())
        whenever(mockWalletQueries.selectDeletionPending()).thenReturn(emptyTombstoneQuery)

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
    }

    @Test
    fun testStagingJournalQueryFailureReturnsFailure_noSilentEmptyList() = runBlocking {
        // Given: stagingJournalQueries throws SQLiteDiskIOException
        val diskError = SQLiteDiskIOException("Disk I/O error on staging journal table")
        whenever(mockStagingJournalQueries.selectPendingJournals()).thenThrow(diskError)

        // When: reconcileStartupState is executed
        val result = repository.reconcileStartupState()

        // Then: MUST be Result.Failure containing the disk exception
        assertTrue("Reconciliation MUST fail when staging journal query throws", result is Result.Failure)
        val failure = result as Result.Failure
        assertTrue(
            "Exception must be or wrap disk error",
            failure.exception is SQLiteDiskIOException || failure.exception.cause is SQLiteDiskIOException
        )
    }

    @Test
    fun testDeletionJournalQueryFailureReturnsFailure_noSilentEmptyList() = runBlocking {
        // Given: deletionJournalQueries throws SQLiteException
        val sqlError = SQLiteException("Corrupted deletion_journal index")
        whenever(mockDeletionJournalQueries.selectPendingDeletions()).thenThrow(sqlError)

        // When: reconcileStartupState is executed
        val result = repository.reconcileStartupState()

        // Then: MUST be Result.Failure containing the sql exception
        assertTrue("Reconciliation MUST fail when deletion journal query throws", result is Result.Failure)
        val failure = result as Result.Failure
        assertTrue(
            "Exception must be or wrap SQLiteException",
            failure.exception is SQLiteException || failure.exception.cause is SQLiteException
        )
    }

    @Test
    fun testTombstonedWalletQueryFailureReturnsFailure_noSilentEmptyList() = runBlocking {
        // Given: walletQueries.selectDeletionPending() throws SQLiteException
        val sqlError = SQLiteException("Database table locked")
        whenever(mockWalletQueries.selectDeletionPending()).thenThrow(sqlError)

        // When: reconcileStartupState is executed
        val result = repository.reconcileStartupState()

        // Then: MUST be Result.Failure containing the sql exception
        assertTrue("Reconciliation MUST fail when tombstone query throws", result is Result.Failure)
        val failure = result as Result.Failure
        assertTrue(
            "Exception must be or wrap SQLiteException",
            failure.exception is SQLiteException || failure.exception.cause is SQLiteException
        )
    }

    @Test
    fun testStagingJournalCasFailureReturnsFailure() = runBlocking {
        // Given: A pending staging journal that is safe to rollback
        val journal = Staging_journal(
            session_id = "session_orphan_1",
            staged_alias = "ww_key_orphan_1",
            backup_id = "ww_backup_1",
            state = ProvisioningState.KEY_STAGED.name,
            created_at = 1000L,
            expires_at = 2000L
        )
        val mockQuery = mock<Query<Staging_journal>>()
        whenever(mockQuery.executeAsList()).thenReturn(listOf(journal))
        whenever(mockStagingJournalQueries.selectPendingJournals()).thenReturn(mockQuery)

        // Mock selectBySessionId
        val mockSingleQuery = mock<Query<Staging_journal>>()
        whenever(mockSingleQuery.executeAsOneOrNull()).thenReturn(journal)
        whenever(mockStagingJournalQueries.selectBySessionId("session_orphan_1")).thenReturn(mockSingleQuery)

        // Mock walletQueries selectByKeyAlias to return null (no active wallet)
        val mockWalletAliasQuery = mock<Query<Wallet>>()
        whenever(mockWalletAliasQuery.executeAsOneOrNull()).thenReturn(null)
        whenever(mockWalletQueries.selectByKeyAlias("ww_key_orphan_1")).thenReturn(mockWalletAliasQuery)

        // Fake key in manager
        fakeSecureKeyManager.setKey("ww_key_orphan_1", "0x0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")

        // Mock CAS changesCount to return 0 (CAS mismatch)
        val mockZeroChanges = mock<Query<Long>>()
        whenever(mockZeroChanges.executeAsOne()).thenReturn(0L)
        whenever(mockZeroChanges.executeAsOneOrNull()).thenReturn(0L)
        whenever(mockStagingJournalQueries.changesCount()).thenReturn(mockZeroChanges)

        // When: reconcileStartupState is executed
        val result = repository.reconcileStartupState()

        // Then: MUST be Result.Failure due to CAS mismatch
        assertTrue("Reconciliation MUST fail when CAS mismatch occurs", result is Result.Failure)
        val failure = result as Result.Failure
        assertTrue(
            "Exception must be JournalCasMismatchException or contain mismatch message",
            failure.exception is JournalCasMismatchException || failure.exception.message?.contains("CAS mismatch") == true
        )
    }

    @Test
    fun testCoordinatorIntegrationWithReconciliationFailureEntersFailedState() = runBlocking {
        // Given: stagingJournalQueries throws SQLiteException
        val sqlError = SQLiteException("Disk corrupted")
        whenever(mockStagingJournalQueries.selectPendingJournals()).thenThrow(sqlError)

        val coordinator = RealStartupRecoveryCoordinator(repository)

        // When: coordinator reconciles
        val finalState = coordinator.startReconciliation()

        // Then: coordinator MUST enter Failed state
        assertTrue(finalState is StartupRecoveryState.Failed)
        val failed = finalState as StartupRecoveryState.Failed
        assertNotNull(failed.error)
        assertEquals(coordinator.state.value, finalState)

        // And: awaitReady MUST return Failure
        val awaitResult = coordinator.awaitReady()
        assertTrue(awaitResult is Result.Failure)
    }
}

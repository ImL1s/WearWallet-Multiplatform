package com.cbstudio.wearwallet.core.security

import app.cash.sqldelight.Query
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.data.repository.ReconciliationVerdict
import com.cbstudio.wearwallet.core.data.repository.WalletRepositoryImpl
import com.cbstudio.wearwallet.core.data.repository.validateReconciliationCandidate
import com.cbstudio.wearwallet.core.database.DatabaseDriverFactory
import com.cbstudio.wearwallet.core.database.StagingJournalQueries
import com.cbstudio.wearwallet.core.database.Staging_journal
import com.cbstudio.wearwallet.core.database.Wallet
import com.cbstudio.wearwallet.core.database.WalletQueries
import com.cbstudio.wearwallet.core.domain.model.WalletType
import com.cbstudio.wearwallet.core.network.EthereumRpcClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

/**
 * Comprehensive 5-Layer Anti-Spoofing Unit Test Suite for KeyVault Reconciliation
 *
 * Verifies:
 * - Layer 1: Rejection of non-existent DB journal rows.
 * - Layer 2: Rejection of session ID or alias mismatches.
 * - Layer 3: Rejection of invalid/corrupted provisioning states.
 * - Layer 4: Active wallet protection (healing to COMMITTED without deleting KeyVault key).
 * - Layer 5: Rejection of already finalized journals (COMMITTED / ROLLED_BACK).
 * - Safe rollback of legitimate orphan keys.
 * - End-to-End reconcileStartupState behavior.
 */
class ReconciliationAntiSpoofingTest {

    private lateinit var databaseDriverFactory: DatabaseDriverFactory
    private lateinit var mockWalletQueries: WalletQueries
    private lateinit var mockJournalQueries: StagingJournalQueries
    private lateinit var fakeSecureKeyManager: FakeSecureKeyManager
    private lateinit var cryptoProvider: CryptoProvider
    private lateinit var ethereumRpcClient: EthereumRpcClient
    private lateinit var repository: WalletRepositoryImpl

    private val testSessionId = "session_recon_101"
    private val testAlias = "ww_key_recon_101"
    private val activeWalletAlias = "ww_key_active_wallet_202"
    private val journalMap = mutableMapOf<String, Staging_journal>()

    @Before
    fun setup() {
        databaseDriverFactory = mock()
        mockWalletQueries = mock()
        mockJournalQueries = mock()
        journalMap.clear()
        whenever(mockJournalQueries.transaction(any(), any())).thenAnswer { invocation ->
            val body = invocation.getArgument<app.cash.sqldelight.TransactionWithoutReturn.() -> Unit>(1)
            val tx = mock<app.cash.sqldelight.TransactionWithoutReturn>()
            body.invoke(tx)
        }
        whenever(mockJournalQueries.insertJournal(any(), any(), any(), any(), any(), any())).thenAnswer { invocation ->
            val sessionId = invocation.getArgument<String>(0)
            val stagedKeyAlias = invocation.getArgument<String>(1)
            val backupId = invocation.getArgument<String>(2)
            val state = invocation.getArgument<String>(3)
            val createdAt = invocation.getArgument<Long>(4)
            val expiresAt = invocation.getArgument<Long>(5)
            journalMap[sessionId] = Staging_journal(
                session_id = sessionId,
                staged_alias = stagedKeyAlias,
                backup_id = backupId,
                state = state,
                created_at = createdAt,
                expires_at = expiresAt
            )
            Unit
        }
        var lastAffectedRows = 1L
        whenever(mockJournalQueries.updateJournalStateCas(any(), any(), any())).thenAnswer { invocation ->
            val newState = invocation.getArgument<String>(0)
            val sessionId = invocation.getArgument<String>(1)
            val expectedState = invocation.getArgument<String>(2)
            val entry = journalMap[sessionId]
            if (entry != null && entry.state == expectedState) {
                journalMap[sessionId] = entry.copy(state = newState)
                lastAffectedRows = 1L
            } else {
                lastAffectedRows = 0L
            }
            Unit
        }
        whenever(mockJournalQueries.changesCount()).thenAnswer {
            val q = mock<Query<Long>>()
            whenever(q.executeAsOne()).thenAnswer { lastAffectedRows }
            whenever(q.executeAsOneOrNull()).thenAnswer { lastAffectedRows }
            q
        }
        whenever(mockJournalQueries.selectBySessionId(any())).thenAnswer { invocation ->
            val sessionId = invocation.getArgument<String>(0)
            val q = mock<Query<Staging_journal>>()
            whenever(q.executeAsOneOrNull()).thenAnswer { journalMap[sessionId] }
            whenever(q.executeAsOne()).thenAnswer { journalMap[sessionId] ?: throw NoSuchElementException("No journal for session $sessionId") }
            q
        }
        fakeSecureKeyManager = FakeSecureKeyManager()
        cryptoProvider = CommonCryptoProvider()
        ethereumRpcClient = mock()

        val mockDriver = mock<app.cash.sqldelight.db.SqlDriver>()
        whenever(databaseDriverFactory.createDriver()).thenReturn(mockDriver)

        val mockDeletionQueries = mock<com.cbstudio.wearwallet.core.database.DeletionJournalQueries>()
        val emptyDeletionQuery = mock<app.cash.sqldelight.Query<com.cbstudio.wearwallet.core.database.Deletion_journal>>()
        whenever(emptyDeletionQuery.executeAsList()).thenReturn(emptyList())
        whenever(mockDeletionQueries.selectPendingDeletions()).thenReturn(emptyDeletionQuery)
        whenever(mockDeletionQueries.transaction(any(), any())).thenAnswer { invocation ->
            val body = invocation.getArgument<app.cash.sqldelight.TransactionWithoutReturn.() -> Unit>(1)
            val tx = mock<app.cash.sqldelight.TransactionWithoutReturn>()
            body.invoke(tx)
        }

        repository = WalletRepositoryImpl(
            databaseDriverFactory = databaseDriverFactory,
            cryptoProvider = cryptoProvider,
            ethereumRpcClient = ethereumRpcClient,
            secureKeyManager = fakeSecureKeyManager,
            platformDeletionCleanupHook = com.cbstudio.wearwallet.core.platform.NoOpPlatformDeletionCleanupHook(),
            customWalletQueries = mockWalletQueries,
            customStagingJournalQueries = mockJournalQueries,
            customDeletionJournalQueries = mockDeletionQueries
        )
    }

    private fun createJournal(
        sessionId: String = testSessionId,
        stagedAlias: String = testAlias,
        backupId: String = "ww_backup_default",
        state: String = ProvisioningState.KEY_STAGED.name,
        createdAt: Long = 1000L,
        expiresAt: Long = 2000L
    ): Staging_journal {
        val j = Staging_journal(
            session_id = sessionId,
            staged_alias = stagedAlias,
            backup_id = backupId,
            state = state,
            created_at = createdAt,
            expires_at = expiresAt
        )
        journalMap[sessionId] = j
        return j
    }

    private fun mockWalletRow(keyAlias: String, isDeletionPending: Long = 0L): Wallet {
        return Wallet(
            id = 1L,
            name = "Active Wallet",
            address = "0x2222222222222222222222222222222222222222",
            public_key = "0x04publickey",
            encrypted_private_key = "encrypted_key",
            encrypted_mnemonic = null,
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
            key_alias = keyAlias,
            key_backend = "KEYSTORE",
            key_format_version = 1L,
            requires_auth = 1L,
            is_deletion_pending = isDeletionPending,
            created_at = 1000L,
            updated_at = 1000L
        )
    }

    @Test
    fun test_layer1_null_journal_entry_is_rejected() {
        val verdict = validateReconciliationCandidate(
            journal = null,
            expectedSessionId = testSessionId,
            expectedKeyAlias = testAlias,
            walletQueries = mockWalletQueries
        )

        assertTrue("Layer 1: Null journal MUST be rejected", verdict is ReconciliationVerdict.Rejected)
        assertTrue(
            "Rejection reason must mention Layer 1",
            (verdict as ReconciliationVerdict.Rejected).reason.contains("Layer 1")
        )
    }

    @Test
    fun test_layer2_session_id_or_alias_mismatch_is_rejected() {
        val journal = createJournal(sessionId = "session_actual", stagedAlias = "alias_actual")

        // Mismatched session ID
        val verdictSessionMismatch = validateReconciliationCandidate(
            journal = journal,
            expectedSessionId = "session_spoofed",
            expectedKeyAlias = "alias_actual",
            walletQueries = mockWalletQueries
        )
        assertTrue("Layer 2: Session mismatch MUST be rejected", verdictSessionMismatch is ReconciliationVerdict.Rejected)

        // Mismatched alias
        val verdictAliasMismatch = validateReconciliationCandidate(
            journal = journal,
            expectedSessionId = "session_actual",
            expectedKeyAlias = "alias_spoofed",
            walletQueries = mockWalletQueries
        )
        assertTrue("Layer 2: Alias mismatch MUST be rejected", verdictAliasMismatch is ReconciliationVerdict.Rejected)
    }

    @Test
    fun test_layer3_invalid_or_corrupted_state_is_rejected() {
        val corruptedJournal = createJournal(state = "CORRUPTED_STATE_XYZ")

        val verdict = validateReconciliationCandidate(
            journal = corruptedJournal,
            expectedSessionId = testSessionId,
            expectedKeyAlias = testAlias,
            walletQueries = mockWalletQueries
        )

        assertTrue("Layer 3: Corrupted state MUST be rejected", verdict is ReconciliationVerdict.Rejected)
        assertTrue(
            "Rejection reason must mention Layer 3",
            (verdict as ReconciliationVerdict.Rejected).reason.contains("Layer 3")
        )
    }

    @Test
    fun test_layer4_active_wallet_protection_heals_to_committed() {
        val journal = createJournal(stagedAlias = activeWalletAlias, state = ProvisioningState.DB_WRITTEN.name)
        val activeWallet = mockWalletRow(keyAlias = activeWalletAlias, isDeletionPending = 0L)

        val queryMock = mock<Query<Wallet>>()
        whenever(queryMock.executeAsOneOrNull()).thenReturn(activeWallet)
        whenever(mockWalletQueries.selectByKeyAlias(activeWalletAlias)).thenReturn(queryMock)

        val verdict = validateReconciliationCandidate(
            journal = journal,
            expectedSessionId = testSessionId,
            expectedKeyAlias = activeWalletAlias,
            walletQueries = mockWalletQueries
        )

        assertTrue(
            "Layer 4: Active wallet reference MUST trigger ActiveWalletProtection",
            verdict is ReconciliationVerdict.ActiveWalletProtection
        )
    }

    @Test
    fun test_layer5_finalized_committed_or_rolled_back_is_rejected() {
        val committedJournal = createJournal(state = ProvisioningState.COMMITTED.name)
        val queryMock = mock<Query<Wallet>>()
        whenever(queryMock.executeAsOneOrNull()).thenReturn(null)
        whenever(mockWalletQueries.selectByKeyAlias(testAlias)).thenReturn(queryMock)

        val verdictCommitted = validateReconciliationCandidate(
            journal = committedJournal,
            expectedSessionId = testSessionId,
            expectedKeyAlias = testAlias,
            walletQueries = mockWalletQueries
        )
        assertTrue("Layer 5: COMMITTED journal MUST be rejected", verdictCommitted is ReconciliationVerdict.Rejected)

        val rolledBackJournal = createJournal(state = ProvisioningState.ROLLED_BACK.name)
        val verdictRolledBack = validateReconciliationCandidate(
            journal = rolledBackJournal,
            expectedSessionId = testSessionId,
            expectedKeyAlias = testAlias,
            walletQueries = mockWalletQueries
        )
        assertTrue("Layer 5: ROLLED_BACK journal MUST be rejected", verdictRolledBack is ReconciliationVerdict.Rejected)
    }

    @Test
    fun test_legitimate_orphan_staged_key_passes_all_5_layers() {
        val orphanJournal = createJournal(state = ProvisioningState.KEY_STAGED.name)
        val queryMock = mock<Query<Wallet>>()
        whenever(queryMock.executeAsOneOrNull()).thenReturn(null)
        whenever(mockWalletQueries.selectByKeyAlias(testAlias)).thenReturn(queryMock)

        val verdict = validateReconciliationCandidate(
            journal = orphanJournal,
            expectedSessionId = testSessionId,
            expectedKeyAlias = testAlias,
            walletQueries = mockWalletQueries
        )

        assertTrue("Legitimate orphan key MUST produce SafeToRollback verdict", verdict is ReconciliationVerdict.SafeToRollback)
        assertEquals(ProvisioningState.KEY_STAGED, (verdict as ReconciliationVerdict.SafeToRollback).state)
    }

    @Test
    fun test_reconcileStartupState_end_to_end_behavior() = runBlocking {
        // Setup:
        // 1. Active wallet journal (KEY_STAGED, but wallet exists in DB)
        val activeJournal = createJournal(
            sessionId = "session_active",
            stagedAlias = activeWalletAlias,
            state = ProvisioningState.KEY_STAGED.name
        )
        fakeSecureKeyManager.setKey(activeWalletAlias, "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")

        val activeWallet = mockWalletRow(keyAlias = activeWalletAlias, isDeletionPending = 0L)
        val activeWalletQuery = mock<Query<Wallet>>()
        whenever(activeWalletQuery.executeAsOneOrNull()).thenReturn(activeWallet)
        whenever(mockWalletQueries.selectByKeyAlias(activeWalletAlias)).thenReturn(activeWalletQuery)

        // 2. Orphan staged key journal (KEY_STAGED, no wallet in DB)
        val orphanAlias = "ww_key_orphan_999"
        val orphanJournal = createJournal(
            sessionId = "session_orphan",
            stagedAlias = orphanAlias,
            state = ProvisioningState.KEY_STAGED.name
        )
        fakeSecureKeyManager.setKey(orphanAlias, "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210")

        val orphanQuery = mock<Query<Wallet>>()
        whenever(orphanQuery.executeAsOneOrNull()).thenReturn(null)
        whenever(mockWalletQueries.selectByKeyAlias(orphanAlias)).thenReturn(orphanQuery)

        // Mock pending journals query
        val pendingJournalsQuery = mock<Query<Staging_journal>>()
        whenever(pendingJournalsQuery.executeAsList()).thenReturn(listOf(activeJournal, orphanJournal), emptyList())
        whenever(mockJournalQueries.selectPendingJournals()).thenReturn(pendingJournalsQuery)

        // Mock tombstoned wallets query (empty)
        val tombstoneQuery = mock<Query<Wallet>>()
        whenever(tombstoneQuery.executeAsList()).thenReturn(emptyList())
        whenever(mockWalletQueries.selectDeletionPending()).thenReturn(tombstoneQuery)

        fakeSecureKeyManager.resetDeleteCount()

        // Execute reconciliation
        val result = repository.reconcileStartupState()
        assertTrue("Reconciliation MUST succeed", result is Result.Success)

        // Verify:
        // 1. Active wallet journal was healed to COMMITTED
        verify(mockJournalQueries).updateJournalStateCas(
            newState = eq(ProvisioningState.COMMITTED.name),
            sessionId = eq("session_active"),
            expectedState = eq(ProvisioningState.KEY_STAGED.name)
        )
        // 2. Active wallet key was NOT deleted
        assertTrue("Active wallet key MUST remain in KeyVault", fakeSecureKeyManager.hasPrivateKey(activeWalletAlias))

        // 3. Orphan key journal was rolled back
        verify(mockJournalQueries).updateJournalStateCas(
            newState = eq(ProvisioningState.ROLLED_BACK.name),
            sessionId = eq("session_orphan"),
            expectedState = eq(ProvisioningState.KEY_STAGED.name)
        )
        // 4. Orphan key WAS deleted from KeyVault
        assertFalse("Orphan key MUST be deleted from KeyVault", fakeSecureKeyManager.hasPrivateKey(orphanAlias))

        // 5. Total deleted keys is exactly 1 (the orphan)
        assertEquals("Exactly 1 key should be deleted (the orphan key)", 1, fakeSecureKeyManager.deleteCount)
    }
}

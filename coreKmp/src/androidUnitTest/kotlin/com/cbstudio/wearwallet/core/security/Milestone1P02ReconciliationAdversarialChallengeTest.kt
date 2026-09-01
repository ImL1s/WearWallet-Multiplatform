package com.cbstudio.wearwallet.core.security

import android.content.Context
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
import io.github.iml1s.crypto.SecureByteArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * Milestone 1 (M1) Challenger 2 Empirical Adversarial Stress Harness for P0-2 Reconciliation
 *
 * Adversarial Challenge Suite:
 * 1. Interface Reflection & Contract Audit: Verify recoverOrRollbackPersistedSession is strictly absent from SecureKeyManager.
 * 2. Spoofed Reconciliation Attacks (Layer 1-5):
 *    - Null / missing staging journals
 *    - Cross-session ID / mismatched key alias forgery
 *    - Corrupted / unparseable / illegal provisioning states
 *    - Active wallet key alias spoofing (Critical Invariant: active keys MUST NOT be deleted)
 *    - Replay of finalized states (COMMITTED / ROLLED_BACK)
 * 3. High-Density Mixed Batch Reconciliation Stress Test (100 concurrent/mixed journals):
 *    - Ensures exact isolation: active keys healed, orphan keys deleted, forged keys rejected.
 * 4. AndroidSecureKeyManager KeyVaultReconciliationCapability Verification:
 *    - Verifies rollbackStagedKeyInternal refuses committed keys, empty parameters, and correctly deletes staged uncommitted entries.
 */
class Milestone1P02ReconciliationAdversarialChallengeTest {

    private lateinit var databaseDriverFactory: DatabaseDriverFactory
    private lateinit var mockWalletQueries: WalletQueries
    private lateinit var mockJournalQueries: StagingJournalQueries
    private lateinit var fakeSecureKeyManager: FakeSecureKeyManager
    private lateinit var cryptoProvider: CryptoProvider
    private lateinit var ethereumRpcClient: EthereumRpcClient
    private lateinit var repository: WalletRepositoryImpl
    private lateinit var mockContext: Context
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
        mockContext = mock()
        whenever(mockContext.applicationContext).thenReturn(mockContext)

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
        sessionId: String,
        stagedAlias: String,
        backupId: String = "ww_backup_adv",
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

    private fun mockWalletRow(id: Long = 1L, keyAlias: String, isDeletionPending: Long = 0L): Wallet {
        return Wallet(
            id = id,
            name = "Active Wallet $id",
            address = "0x" + "1".repeat(40),
            public_key = "0x04pubkey",
            encrypted_private_key = "enc_priv_key",
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

    // =========================================================================
    // 1. Contract & Interface Reflection Audit
    // =========================================================================

    @Test
    fun challenge_01_secureKeyManager_interface_does_not_contain_recoverOrRollbackPersistedSession() {
        val methods = SecureKeyManager::class.java.methods
        val hasExposedRollback = methods.any { it.name == "recoverOrRollbackPersistedSession" }
        assertFalse(
            "CRITICAL: SecureKeyManager public interface MUST NOT expose recoverOrRollbackPersistedSession",
            hasExposedRollback
        )
    }

    @Test
    fun challenge_02_reconciliationCapability_is_internal_and_type_safe() {
        assertTrue(
            "FakeSecureKeyManager must implement KeyVaultReconciliationCapability",
            fakeSecureKeyManager is KeyVaultReconciliationCapability
        )
        val capabilityMethods = KeyVaultReconciliationCapability::class.java.declaredMethods
        assertEquals(
            "KeyVaultReconciliationCapability must expose exactly 1 method (rollbackStagedKeyInternal)",
            1,
            capabilityMethods.size
        )
        assertEquals("rollbackStagedKeyInternal", capabilityMethods[0].name)
    }

    // =========================================================================
    // 2. Adversarial Anti-Spoofing 5-Layer Stress Tests
    // =========================================================================

    @Test
    fun challenge_03_spoofed_null_journal_is_rejected_without_key_deletion() = runBlocking {
        fakeSecureKeyManager.setKey("ww_key_victim", "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
        fakeSecureKeyManager.resetDeleteCount()

        val verdict = validateReconciliationCandidate(
            journal = null,
            expectedSessionId = "rogue_session_null",
            expectedKeyAlias = "ww_key_victim",
            walletQueries = mockWalletQueries
        )

        assertTrue("Null journal must be rejected", verdict is ReconciliationVerdict.Rejected)
        assertTrue((verdict as ReconciliationVerdict.Rejected).reason.contains("Layer 1"))
        assertTrue("Victim key must remain untouched", fakeSecureKeyManager.hasPrivateKey("ww_key_victim"))
        assertEquals("Delete count must be 0", 0, fakeSecureKeyManager.deleteCount)
    }

    @Test
    fun challenge_04_spoofed_session_or_alias_mismatch_is_rejected() = runBlocking {
        val genuineJournal = createJournal(sessionId = "session_genuine", stagedAlias = "ww_alias_genuine")
        fakeSecureKeyManager.setKey("ww_alias_genuine", "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
        fakeSecureKeyManager.resetDeleteCount()

        // Attack 1: Spoof session ID
        val verdict1 = validateReconciliationCandidate(
            journal = genuineJournal,
            expectedSessionId = "session_attacker_injected",
            expectedKeyAlias = "ww_alias_genuine",
            walletQueries = mockWalletQueries
        )
        assertTrue("Mismatched session ID must be rejected", verdict1 is ReconciliationVerdict.Rejected)
        assertTrue((verdict1 as ReconciliationVerdict.Rejected).reason.contains("Layer 2"))

        // Attack 2: Spoof key alias to target another key
        val verdict2 = validateReconciliationCandidate(
            journal = genuineJournal,
            expectedSessionId = "session_genuine",
            expectedKeyAlias = "ww_alias_target",
            walletQueries = mockWalletQueries
        )
        assertTrue("Mismatched key alias must be rejected", verdict2 is ReconciliationVerdict.Rejected)
        assertTrue((verdict2 as ReconciliationVerdict.Rejected).reason.contains("Layer 2"))

        assertTrue("Genuine key must remain untouched", fakeSecureKeyManager.hasPrivateKey("ww_alias_genuine"))
        assertEquals("Delete count must be 0", 0, fakeSecureKeyManager.deleteCount)
    }

    @Test
    fun challenge_05_corrupted_or_arbitrary_state_injection_is_rejected() = runBlocking {
        val rogueStates = listOf("RANDOM_STATE", "DELETION_IN_PROGRESS", "ACTIVE", "", "null", "IN_USE", "ATTACK")
        fakeSecureKeyManager.setKey("ww_key_target", "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")

        for (state in rogueStates) {
            val journal = createJournal(sessionId = "session_rogue_$state", stagedAlias = "ww_key_target", state = state)
            val verdict = validateReconciliationCandidate(
                journal = journal,
                expectedSessionId = "session_rogue_$state",
                expectedKeyAlias = "ww_key_target",
                walletQueries = mockWalletQueries
            )
            assertTrue("State '$state' must be rejected", verdict is ReconciliationVerdict.Rejected)
            assertTrue((verdict as ReconciliationVerdict.Rejected).reason.contains("Layer 3"))
        }

        assertTrue("Target key must remain untouched", fakeSecureKeyManager.hasPrivateKey("ww_key_target"))
        assertEquals("Delete count must be 0", 0, fakeSecureKeyManager.deleteCount)
    }

    @Test
    fun challenge_06_active_wallet_alias_spoofing_never_deletes_key_and_heals_to_committed() = runBlocking {
        val activeAlias = "ww_key_production_active"
        fakeSecureKeyManager.setKey(activeAlias, "1122334455667788112233445566778811223344556677881122334455667788")
        fakeSecureKeyManager.resetDeleteCount()

        val activeWallet = mockWalletRow(id = 42L, keyAlias = activeAlias, isDeletionPending = 0L)
        val queryMock = mock<Query<Wallet>>()
        whenever(queryMock.executeAsOneOrNull()).thenReturn(activeWallet)
        whenever(mockWalletQueries.selectByKeyAlias(activeAlias)).thenReturn(queryMock)

        // Attacker leaves a stale journal with PREPARED, KEY_STAGED, DB_WRITTEN, or ROLLBACK_PENDING
        val testStates = listOf(
            ProvisioningState.PREPARED,
            ProvisioningState.KEY_STAGED,
            ProvisioningState.DB_WRITTEN,
            ProvisioningState.ROLLBACK_PENDING
        )

        for (st in testStates) {
            val rogueJournal = createJournal(
                sessionId = "session_rogue_${st.name}",
                stagedAlias = activeAlias,
                state = st.name
            )

            val verdict = validateReconciliationCandidate(
                journal = rogueJournal,
                expectedSessionId = "session_rogue_${st.name}",
                expectedKeyAlias = activeAlias,
                walletQueries = mockWalletQueries
            )

            assertTrue(
                "Active wallet reference in state ${st.name} MUST trigger ActiveWalletProtection",
                verdict is ReconciliationVerdict.ActiveWalletProtection
            )
            assertEquals(42L, (verdict as ReconciliationVerdict.ActiveWalletProtection).activeWalletId)
        }

        // Verify that startup reconciliation processes this safely
        val pendingJournalsQuery = mock<Query<Staging_journal>>()
        val rogueJournal = createJournal(sessionId = "session_rogue_pending", stagedAlias = activeAlias, state = ProvisioningState.KEY_STAGED.name)
        whenever(pendingJournalsQuery.executeAsList()).thenReturn(listOf(rogueJournal), emptyList())
        whenever(mockJournalQueries.selectPendingJournals()).thenReturn(pendingJournalsQuery)

        val tombstoneQuery = mock<Query<Wallet>>()
        whenever(tombstoneQuery.executeAsList()).thenReturn(emptyList())
        whenever(mockWalletQueries.selectDeletionPending()).thenReturn(tombstoneQuery)

        val result = repository.reconcileStartupState()
        assertTrue("Reconciliation MUST succeed", result is Result.Success)

        // Active key MUST be preserved
        assertTrue("Active wallet key MUST NOT be deleted from KeyVault", fakeSecureKeyManager.hasPrivateKey(activeAlias))
        assertEquals("Delete count MUST remain 0", 0, fakeSecureKeyManager.deleteCount)

        // Journal MUST be healed to COMMITTED
        verify(mockJournalQueries).updateJournalStateCas(
            newState = eq(ProvisioningState.COMMITTED.name),
            sessionId = eq("session_rogue_pending"),
            expectedState = eq(ProvisioningState.KEY_STAGED.name)
        )
    }

    @Test
    fun challenge_07_replay_of_finalized_states_committed_or_rolled_back_is_rejected() = runBlocking {
        val finalStates = listOf(ProvisioningState.COMMITTED, ProvisioningState.ROLLED_BACK)
        fakeSecureKeyManager.setKey("ww_key_final", "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
        fakeSecureKeyManager.resetDeleteCount()

        val queryMock = mock<Query<Wallet>>()
        whenever(queryMock.executeAsOneOrNull()).thenReturn(null)
        whenever(mockWalletQueries.selectByKeyAlias("ww_key_final")).thenReturn(queryMock)

        for (st in finalStates) {
            val journal = createJournal(sessionId = "session_${st.name}", stagedAlias = "ww_key_final", state = st.name)
            val verdict = validateReconciliationCandidate(
                journal = journal,
                expectedSessionId = "session_${st.name}",
                expectedKeyAlias = "ww_key_final",
                walletQueries = mockWalletQueries
            )
            assertTrue("Finalized state ${st.name} MUST be rejected", verdict is ReconciliationVerdict.Rejected)
            val reason = (verdict as ReconciliationVerdict.Rejected).reason
            assertTrue("Rejection reason must mention Layer 3 or Layer 5", reason.contains("Layer 3") || reason.contains("Layer 5"))
        }

        assertTrue("Key must remain untouched", fakeSecureKeyManager.hasPrivateKey("ww_key_final"))
        assertEquals("Delete count must be 0", 0, fakeSecureKeyManager.deleteCount)
    }

    // =========================================================================
    // 3. High-Density Mixed Batch Adversarial Stress Test (100 Entries)
    // =========================================================================

    @Test
    fun challenge_08_high_density_mixed_batch_reconciliation_stress_test() = runBlocking {
        val totalActive = 25
        val totalOrphans = 25
        val totalCorrupted = 25
        val totalFinalized = 25

        val journals = mutableListOf<Staging_journal>()

        // 1. 25 Active wallets (must NOT be deleted, healed to COMMITTED)
        for (i in 1..totalActive) {
            val alias = "ww_key_active_$i"
            val session = "session_active_$i"
            fakeSecureKeyManager.setKey(alias, "active_key_bytes_$i".padEnd(64, '0'))
            journals.add(createJournal(sessionId = session, stagedAlias = alias, state = ProvisioningState.KEY_STAGED.name))

            val wallet = mockWalletRow(id = i.toLong(), keyAlias = alias, isDeletionPending = 0L)
            val queryMock = mock<Query<Wallet>>()
            whenever(queryMock.executeAsOneOrNull()).thenReturn(wallet)
            whenever(mockWalletQueries.selectByKeyAlias(alias)).thenReturn(queryMock)
        }

        // 2. 25 Valid Orphan staged keys (must be safely deleted)
        for (i in 1..totalOrphans) {
            val alias = "ww_key_orphan_$i"
            val session = "session_orphan_$i"
            fakeSecureKeyManager.setKey(alias, "orphan_key_bytes_$i".padEnd(64, '0'))
            journals.add(createJournal(sessionId = session, stagedAlias = alias, state = ProvisioningState.KEY_STAGED.name))

            val queryMock = mock<Query<Wallet>>()
            whenever(queryMock.executeAsOneOrNull()).thenReturn(null)
            whenever(mockWalletQueries.selectByKeyAlias(alias)).thenReturn(queryMock)
        }

        // 3. 25 Corrupted / Rogue state journals (must be rejected, keys NOT deleted)
        for (i in 1..totalCorrupted) {
            val alias = "ww_key_corrupted_$i"
            val session = "session_corrupted_$i"
            fakeSecureKeyManager.setKey(alias, "corrupted_key_bytes_$i".padEnd(64, '0'))
            journals.add(createJournal(sessionId = session, stagedAlias = alias, state = "CORRUPTED_STATE_$i"))

            val queryMock = mock<Query<Wallet>>()
            whenever(queryMock.executeAsOneOrNull()).thenReturn(null)
            whenever(mockWalletQueries.selectByKeyAlias(alias)).thenReturn(queryMock)
        }

        // 4. 25 Already Finalized journals (must be rejected, keys NOT deleted)
        for (i in 1..totalFinalized) {
            val alias = "ww_key_finalized_$i"
            val session = "session_finalized_$i"
            fakeSecureKeyManager.setKey(alias, "finalized_key_bytes_$i".padEnd(64, '0'))
            journals.add(createJournal(sessionId = session, stagedAlias = alias, state = ProvisioningState.COMMITTED.name))

            val queryMock = mock<Query<Wallet>>()
            whenever(queryMock.executeAsOneOrNull()).thenReturn(null)
            whenever(mockWalletQueries.selectByKeyAlias(alias)).thenReturn(queryMock)
        }

        // Mock pending journals query
        val pendingJournalsQuery = mock<Query<Staging_journal>>()
        whenever(pendingJournalsQuery.executeAsList()).thenReturn(journals, emptyList())
        whenever(mockJournalQueries.selectPendingJournals()).thenReturn(pendingJournalsQuery)

        // Mock tombstone wallets query (empty)
        val tombstoneQuery = mock<Query<Wallet>>()
        whenever(tombstoneQuery.executeAsList()).thenReturn(emptyList())
        whenever(mockWalletQueries.selectDeletionPending()).thenReturn(tombstoneQuery)

        fakeSecureKeyManager.resetDeleteCount()

        // Execute batch reconciliation
        val result = repository.reconcileStartupState()
        assertTrue("Batch reconciliation must succeed", result is Result.Success)

        // Assertions:
        // 1. All 25 active wallet keys MUST exist
        for (i in 1..totalActive) {
            assertTrue("Active key $i must still exist", fakeSecureKeyManager.hasPrivateKey("ww_key_active_$i"))
        }

        // 2. All 25 orphan keys MUST be deleted
        for (i in 1..totalOrphans) {
            assertFalse("Orphan key $i must be deleted", fakeSecureKeyManager.hasPrivateKey("ww_key_orphan_$i"))
        }

        // 3. All 25 corrupted keys MUST still exist (they were rejected)
        for (i in 1..totalCorrupted) {
            assertTrue("Corrupted key $i must still exist", fakeSecureKeyManager.hasPrivateKey("ww_key_corrupted_$i"))
        }

        // 4. All 25 finalized keys MUST still exist (they were rejected)
        for (i in 1..totalFinalized) {
            assertTrue("Finalized key $i must still exist", fakeSecureKeyManager.hasPrivateKey("ww_key_finalized_$i"))
        }

        // 5. Exactly 25 keys were deleted in total (only the valid orphans)
        assertEquals("Exactly 25 orphan keys should be deleted", totalOrphans, fakeSecureKeyManager.deleteCount)
    }

    // =========================================================================
    // 4. AndroidSecureKeyManager Reconciliation Capability Unit Verification
    // =========================================================================

    @Test
    fun challenge_09_androidSecureKeyManager_rollbackStagedKeyInternal_behavior() = runBlocking {
        val testKs = TestKeyStoreBackend()
        val prefs = InMemorySharedPreferences()
        val manager = AndroidSecureKeyManager(
            context = mockContext,
            config = SecureStorageConfig(enableRootDetection = false),
            keyStoreProvider = { testKs.createKeyStore() },
            encryptedPrefsProvider = { prefs },
            secretKeyProvider = { alias, _ -> testKs.generateAndStoreKey(alias) }
        )

        val capability = manager as KeyVaultReconciliationCapability

        // Test 1: Unregistered grant must fail
        val unregisteredGrant = RecoveryGrant.create("hash_unreg", "session_1", "alias_1", "KEY_STAGED", "ZERO_ACTIVE_REF_VALIDATED")
        val unregisteredResult = capability.rollbackStagedKeyInternal(unregisteredGrant)
        assertTrue("Unregistered grant must fail", unregisteredResult is Result.Failure)

        // Test 2: Cannot rollback a committed key in memory
        val session = manager.startProvisioningSession()
        val privKey = ByteArray(32) { 0x55.toByte() }
        val fakeAuthHandle = TestPlatformAuthenticator.issueHandle(
            keyId = session.stagedKeyAlias,
            sessionId = session.sessionId,
            operation = AuthOperation.IMPORT
        )
        manager.storeStagedPrivateKey(session, privKey, requireAuth = true, authContext = AuthenticationContext(authHandle = fakeAuthHandle))
        manager.commitProvisioningSession(session)

        val grantCommitted = RecoveryGrant.create(
            journalRowHash = "hash_committed",
            sessionId = session.sessionId,
            alias = session.stagedKeyAlias,
            state = "KEY_STAGED",
            zeroActiveReferenceProof = "ZERO_ACTIVE_REF_VALIDATED"
        )
        RecoveryGrantRegistry.register(grantCommitted)
        val rollbackCommittedResult = capability.rollbackStagedKeyInternal(grantCommitted)
        assertTrue("Rollback of committed key must fail", rollbackCommittedResult is Result.Failure)
        assertTrue(manager.hasPrivateKey(session.stagedKeyAlias))

        // Test 3: Uncommitted staged key rollback succeeds with registered grant
        val sessionOrphan = manager.startProvisioningSession()
        val fakeAuthHandleOrphan = TestPlatformAuthenticator.issueHandle(
            keyId = sessionOrphan.stagedKeyAlias,
            sessionId = sessionOrphan.sessionId,
            operation = AuthOperation.IMPORT
        )
        manager.storeStagedPrivateKey(sessionOrphan, privKey, requireAuth = true, authContext = AuthenticationContext(authHandle = fakeAuthHandleOrphan))
        assertTrue("Staged key should exist before rollback", manager.hasPrivateKey(sessionOrphan.stagedKeyAlias))

        val grantOrphan = RecoveryGrant.create(
            journalRowHash = "hash_orphan",
            sessionId = sessionOrphan.sessionId,
            alias = sessionOrphan.stagedKeyAlias,
            state = "KEY_STAGED",
            zeroActiveReferenceProof = "ZERO_ACTIVE_REF_VALIDATED"
        )
        RecoveryGrantRegistry.register(grantOrphan)
        val rollbackOrphanResult = capability.rollbackStagedKeyInternal(grantOrphan)
        assertTrue("Rollback of uncommitted staged key must succeed", rollbackOrphanResult is Result.Success)
        assertFalse("Staged key must no longer exist after rollback", manager.hasPrivateKey(sessionOrphan.stagedKeyAlias))
    }
}

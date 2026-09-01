package com.cbstudio.wearwallet.core.security

import app.cash.sqldelight.Query
import app.cash.sqldelight.TransactionWithReturn
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
 * Unit Test Suite for Milestone 3 (M3: P1-2 Persistent 5-State Deletion Machine & P1-3 Fail-Closed Staging Journal)
 */
class Milestone3DeletionAndJournalFailClosedTest {

    private lateinit var databaseDriverFactory: DatabaseDriverFactory
    private lateinit var mockWalletQueries: WalletQueries
    private lateinit var mockStagingJournalQueries: StagingJournalQueries
    private lateinit var mockDeletionJournalQueries: DeletionJournalQueries
    private lateinit var fakeSecureKeyManager: FakeSecureKeyManager
    private lateinit var cryptoProvider: CryptoProvider
    private lateinit var ethereumRpcClient: EthereumRpcClient
    private lateinit var repository: WalletRepositoryImpl

    private val testWalletId = 42L
    private val testKeyAlias = "ww_key_alias_42"
    private val testAddress = "0x4242424242424242424242424242424242424242"

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
    }

    private fun mockWalletRow(
        id: Long = testWalletId,
        keyAlias: String = testKeyAlias,
        requiresAuth: Long = 1L,
        isActive: Long = 1L
    ): Wallet {
        return Wallet(
            id = id,
            name = "Test Wallet",
            address = testAddress,
            public_key = "0x04publickey",
            encrypted_private_key = "encrypted_key",
            encrypted_mnemonic = null,
            derivation_path = "m/44'/60'/0'/0/0",
            chain_type = "ETHEREUM",
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
            is_deletion_pending = 0L,
            created_at = 1000L,
            updated_at = 1000L
        )
    }

    private fun createDeletionJournal(
        walletId: Long = testWalletId,
        keyAlias: String = testKeyAlias,
        state: String = DeletionState.DELETE_AUTHORIZED.name
    ): Deletion_journal {
        return Deletion_journal(
            wallet_id = walletId,
            key_alias = keyAlias,
            state = state,
            last_error = null,
            retry_count = 0L,
            created_at = 1000L,
            updated_at = 1000L
        )
    }

    @Test
    fun testDeleteWallet_HappyPath_CompletesAll5States() = runBlocking {
        val wallet = mockWalletRow(requiresAuth = 1L)
        val mockQuery = mock<Query<Wallet>>()
        whenever(mockQuery.executeAsOneOrNull()).thenReturn(wallet)
        whenever(mockWalletQueries.selectById(testWalletId)).thenReturn(mockQuery)

        // Store key in fake SecureKeyManager
        val importHandle = TestPlatformAuthenticator.issueHandle(
            keyId = testKeyAlias,
            operation = AuthOperation.IMPORT,
            validityDurationMs = 60_000L,
            walletId = testWalletId.toString()
        )
        val storeRes = fakeSecureKeyManager.storePrivateKey(
            keyId = testKeyAlias,
            privateKey = ByteArray(32) { 1 },
            requireAuth = true,
            authContext = AuthenticationContext(authHandle = importHandle),
            expectedWalletId = testWalletId.toString()
        )
        assertTrue("storePrivateKey must succeed: ${(storeRes as? Result.Failure)?.exception}", storeRes is Result.Success)

        // Mock deletion journal states simulating progress
        var currentState = DeletionState.DELETE_AUTHORIZED.name
        val mockDelQuery = mock<Query<Deletion_journal>>()
        whenever(mockDelQuery.executeAsOneOrNull()).thenAnswer {
            createDeletionJournal(state = currentState)
        }
        whenever(mockDeletionJournalQueries.selectByWalletId(testWalletId)).thenReturn(mockDelQuery)
        whenever(mockDeletionJournalQueries.updateDeletionStateCas(any(), anyOrNull(), any(), eq(testWalletId), any())).thenAnswer {
            currentState = it.getArgument(0)
            Unit
        }

        // Mock remaining active wallets
        val mockListQuery = mock<Query<Wallet>>()
        whenever(mockListQuery.executeAsList()).thenReturn(emptyList())
        whenever(mockWalletQueries.selectAllActiveWallets()).thenReturn(mockListQuery)

        val authHandle = TestPlatformAuthenticator.issueHandle(
            keyId = testKeyAlias,
            operation = AuthOperation.DELETE,
            validityDurationMs = 60_000L,
            walletId = testWalletId.toString()
        )
        val authContext = AuthenticationContext(authHandle = authHandle)

        val result = repository.deleteWallet(testWalletId.toString(), authContext = authContext)

        assertTrue(
            "deleteWallet should succeed, but got: ${(result as? Result.Failure)?.exception?.message ?: result}",
            result is Result.Success
        )
        verify(mockWalletQueries).markDeletionPending(testWalletId)
        verify(mockWalletQueries).delete(testWalletId)
        assertFalse("Key should be deleted from key manager", fakeSecureKeyManager.hasPrivateKey(testKeyAlias))
    }

    @Test
    fun testDeleteWallet_RequiresAuth_RejectsNullOrInvalidAuth() = runBlocking {
        val wallet = mockWalletRow(requiresAuth = 1L)
        val mockQuery = mock<Query<Wallet>>()
        whenever(mockQuery.executeAsOneOrNull()).thenReturn(wallet)
        whenever(mockWalletQueries.selectById(testWalletId)).thenReturn(mockQuery)

        // Case 1: null authContext
        val res1 = repository.deleteWallet(testWalletId.toString(), authContext = null)
        assertTrue("Null auth should fail", res1 is Result.Failure)
        assertTrue((res1 as Result.Failure).exception is AuthenticationRequiredException)

        // Case 2: Wrong operation in authHandle (e.g. EXPORT instead of DELETE)
        val wrongOpHandle = TestPlatformAuthenticator.issueHandle(
            keyId = testKeyAlias,
            operation = AuthOperation.EXPORT,
            validityDurationMs = 60_000L,
            walletId = testWalletId.toString()
        )
        val res2 = repository.deleteWallet(testWalletId.toString(), authContext = AuthenticationContext(authHandle = wrongOpHandle))
        assertTrue("Wrong operation should fail", res2 is Result.Failure)
        assertTrue((res2 as Result.Failure).exception is AuthenticationRequiredException)

        // Case 3: Wrong keyId in authHandle
        val wrongKeyHandle = TestPlatformAuthenticator.issueHandle(
            keyId = "other_key_alias",
            operation = AuthOperation.DELETE,
            validityDurationMs = 60_000L,
            walletId = testWalletId.toString()
        )
        val res3 = repository.deleteWallet(testWalletId.toString(), authContext = AuthenticationContext(authHandle = wrongKeyHandle))
        assertTrue("Wrong keyId should fail", res3 is Result.Failure)
        assertTrue((res3 as Result.Failure).exception is AuthenticationRequiredException)
    }

    @Test
    fun testReconcileStartupState_RecoversPendingDeletions() = runBlocking {
        val pendingJournalsMock = mock<Query<Staging_journal>>()
        whenever(pendingJournalsMock.executeAsList()).thenReturn(emptyList())
        whenever(mockStagingJournalQueries.selectPendingJournals()).thenReturn(pendingJournalsMock)

        var currentState = DeletionState.TOMBSTONED.name
        val pendingDelQuery = mock<Query<Deletion_journal>>()
        val tombstonedJournal = createDeletionJournal(state = DeletionState.TOMBSTONED.name)
        whenever(pendingDelQuery.executeAsList()).thenAnswer {
            if (currentState == DeletionState.COMPLETED.name) emptyList() else listOf(tombstonedJournal)
        }
        whenever(mockDeletionJournalQueries.selectPendingDeletions()).thenReturn(pendingDelQuery)

        val wallet = mockWalletRow(requiresAuth = 1L)
        val mockWalletQuery = mock<Query<Wallet>>()
        whenever(mockWalletQuery.executeAsOneOrNull()).thenReturn(wallet)
        whenever(mockWalletQueries.selectById(testWalletId)).thenReturn(mockWalletQuery)

        val mockDelQuery = mock<Query<Deletion_journal>>()
        whenever(mockDelQuery.executeAsOneOrNull()).thenAnswer {
            createDeletionJournal(state = currentState)
        }
        whenever(mockDeletionJournalQueries.selectByWalletId(testWalletId)).thenReturn(mockDelQuery)
        whenever(mockDeletionJournalQueries.updateDeletionStateCas(any(), anyOrNull(), any(), eq(testWalletId), any())).thenAnswer {
            currentState = it.getArgument(0)
            Unit
        }

        val emptyActiveListMock = mock<Query<Wallet>>()
        whenever(emptyActiveListMock.executeAsList()).thenReturn(emptyList())
        whenever(mockWalletQueries.selectAllActiveWallets()).thenReturn(emptyActiveListMock)
        whenever(mockWalletQueries.selectDeletionPending()).thenReturn(emptyActiveListMock)

        val res = repository.reconcileStartupState()

        assertTrue("reconcileStartupState should succeed", res is Result.Success)
        verify(mockWalletQueries).delete(testWalletId)
        verify(mockDeletionJournalQueries).purgeCompletedDeletions(any())
        verify(mockStagingJournalQueries).purgeExpiredJournals(any())
    }

    @Test
    fun testStagingJournal_CasMismatchThrowsTypedException() = runBlocking {
        val session = fakeSecureKeyManager.startProvisioningSession()
        
        // Mock current staging journal state as COMMITTED instead of PREPARED
        val currentJournal = Staging_journal(
            session_id = session.sessionId,
            staged_alias = session.stagedKeyAlias,
            backup_id = session.backupId,
            state = ProvisioningState.COMMITTED.name,
            created_at = 1000L,
            expires_at = 2000L
        )
        val mockQuery = mock<Query<Staging_journal>>()
        whenever(mockQuery.executeAsOneOrNull()).thenReturn(currentJournal)
        whenever(mockStagingJournalQueries.selectBySessionId(session.sessionId)).thenReturn(mockQuery)

        val authHandle = TestPlatformAuthenticator.issueHandle(
            keyId = session.stagedKeyAlias,
            sessionId = session.sessionId,
            operation = AuthOperation.IMPORT,
            validityDurationMs = 60_000L
        )

        // Attempting to create wallet when CAS encounters state mismatch should fail with typed exception
        val res = repository.createWallet(
            name = "Test Wallet",
            mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about".toCharArray(),
            password = "password123".toCharArray(),
            chainType = ChainType.ETHEREUM,
            authContext = AuthenticationContext(authHandle = authHandle)
        )

        assertTrue("Creation should fail on CAS mismatch", res is Result.Failure)
        val ex = (res as Result.Failure).exception
        assertTrue("Exception should be JournalCasMismatchException or StagingJournalException, got: ${ex::class.simpleName}", ex is StagingJournalException)
    }
}

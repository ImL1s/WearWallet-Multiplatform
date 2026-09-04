package com.cbstudio.wearwallet.core.security

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.cbstudio.wearwallet.core.cache.GlobalCacheManager
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.data.repository.WalletRepositoryImpl
import com.cbstudio.wearwallet.core.database.CoreWalletDatabase
import com.cbstudio.wearwallet.core.database.DatabaseDriverFactory
import com.cbstudio.wearwallet.core.database.DeletionStepLedgerQueries
import com.cbstudio.wearwallet.core.database.WalletQueries
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.WalletType
import com.cbstudio.wearwallet.core.network.EthereumRpcClient
import com.cbstudio.wearwallet.core.platform.PlatformDeletionCleanupHook
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

/**
 * Milestone 3 (P1) 17-Layer Deletion Ledger Fault Injection & Gate Verification Tests
 *
 * 嚴格驗證：
 * 1. initDeletionStepLedger 失敗即刻終止，0 破壞性副作用（無 Tombstone、無金鑰刪除、無 DB 刪除）。
 * 2. 17 個步驟逐項故障注入時的 Fail-Closed 行為（狀態轉為 RECOVERY_REQUIRED，金鑰或 DB 主記錄嚴密保護）。
 * 3. Step 10 (PRICE_ALERT_ROWS) 實質刪除與 count 驗證。
 * 4. Step 15 (CACHES) 實質清理 GlobalCacheManager 與驗證。
 * 5. Step 16 (ACTIVE_POINTER) 失敗時嚴格禁止刪除 DB wallet 主記錄，禁止 CAS 推進至 COMPLETED。
 * 6. 全量 17 步 PASS 斷言閘門（若有任何步驟遺漏、PENDING 或 FAILED，嚴禁進入 COMPLETED）。
 * 7. 斷點續跑與 Startup Reconciliation 增量修復直至 17 步全 PASS 推進 COMPLETED。
 */
class Milestone3FaultInjectionLedgerTest {

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

    private fun testAddress(id: Long): String {
        return "0x" + id.toString().padStart(40, '0')
    }

    private fun insertTestWallet(
        id: Long = 1L,
        address: String = testAddress(id),
        name: String = "Test Wallet $id",
        keyAlias: String = "wallet_key_$id",
        requiresAuth: Boolean = true,
        isActive: Boolean = true
    ): Long {
        database.walletQueries.insert(
            name = name,
            address = address,
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
    // 1. 初始化失敗即刻終止 (0 破壞性副作用)
    // =========================================================================

    @Test
    fun test_00_init_deletion_step_ledger_failure_aborts_immediately_zero_side_effects() = runBlocking {
        val walletId = insertTestWallet(id = 10L, keyAlias = "key_init_fail")
        val auth = issueDeleteAuth("key_init_fail", walletId = walletId.toString())

        // Mock ledger queries that throw on upsert
        val brokenLedgerQueries = mock<DeletionStepLedgerQueries>()
        whenever(brokenLedgerQueries.transaction(any(), any())).thenThrow(RuntimeException("DB disk I/O failure on ledger init"))

        val repo = createRepository(customLedgerQueries = brokenLedgerQueries)
        val result = repo.deleteWallet(walletId.toString(), auth)

        assertTrue("deleteWallet must fail when initDeletionStepLedger fails", result is Result.Failure)

        // 驗證 0 破壞性副作用：
        // 1. 錢包記錄依舊完好且未被標記為 Tombstone
        val wallet = database.walletQueries.selectById(walletId).executeAsOneOrNull()
        assertNotNull("Wallet DB record must not be deleted", wallet)
        assertEquals("Wallet must not be marked as deletion pending", 0L, wallet?.is_deletion_pending)

        // 2. KeyVault 中的私鑰完好未被刪除
        assertTrue("Key in KeyVault must remain intact", fakeKeyManager.hasPrivateKey("key_init_fail"))
        assertEquals("KeyVault deleteCount must be 0", 0, fakeKeyManager.deleteCount)
    }

    // =========================================================================
    // 2. Step 1 (WALLET_TOMBSTONE) 故障注入 -> RECOVERY_REQUIRED & Key intact
    // =========================================================================

    @Test
    fun test_01_step_1_wallet_tombstone_failure_fail_closed() = runBlocking {
        val walletId = insertTestWallet(id = 11L, keyAlias = "key_tomb_fail")
        val auth = issueDeleteAuth("key_tomb_fail")

        val spyWalletQueries = spy(database.walletQueries)
        doThrow(RuntimeException("Failed to mark tombstone")).whenever(spyWalletQueries).markDeletionPending(walletId)

        val repo = createRepository(customWalletQueries = spyWalletQueries)
        val result = repo.deleteWallet(walletId.toString(), auth)

        assertTrue(result is Result.Failure)

        // Journal 狀態轉移至 RECOVERY_REQUIRED
        val journal = database.deletionJournalQueries.selectByWalletId(walletId).executeAsOneOrNull()
        assertEquals(DeletionState.RECOVERY_REQUIRED.name, journal?.state)

        // 金鑰未被刪除
        assertTrue(fakeKeyManager.hasPrivateKey("key_tomb_fail"))
        assertEquals(0, fakeKeyManager.deleteCount)
    }

    // =========================================================================
    // 3. Step 2 (KEY_VAULT) 故障注入 -> RECOVERY_REQUIRED & DB row intact
    // =========================================================================

    @Test
    fun test_02_step_2_key_vault_failure_fail_closed() = runBlocking {
        val walletId = insertTestWallet(id = 12L, keyAlias = "key_vault_fail")
        val auth = issueDeleteAuth("key_vault_fail")

        val baseKeyManager = FakeSecureKeyManager()
        baseKeyManager.setKey("key_vault_fail", testPrivateKeyHex, requireAuth = true)

        val failingKeyManager = object : SecureKeyManager by baseKeyManager, KeyVaultDeletionCapability {
            override suspend fun deletePrivateKeyWithGrant(grant: DeletionAuthorizationGrant, expectedWalletId: String): Result<Unit> {
                return Result.Failure(KeyMaterialUnavailableException("KeyStore hardware timeout"))
            }
        }

        val repo = createRepository(keyManager = failingKeyManager)

        val result = repo.deleteWallet(walletId.toString(), auth)
        assertTrue(result is Result.Failure)

        val journal = database.deletionJournalQueries.selectByWalletId(walletId).executeAsOneOrNull()
        assertEquals(DeletionState.RECOVERY_REQUIRED.name, journal?.state)

        val stepRecord = database.deletionStepLedgerQueries.selectStep(walletId, DeletionStep.KEY_VAULT.name).executeAsOneOrNull()
        assertEquals(DeletionStepStatus.FAILED.name, stepRecord?.status)

        // DB row remains
        assertNotNull(database.walletQueries.selectById(walletId).executeAsOneOrNull())
    }

    // =========================================================================
    // 3.1 Step 3-9 子表清理故障注入與恢復測試
    // =========================================================================

    @Test
    fun test_03_step_3_nft_rows_failure_fail_closed_and_recovery() = runBlocking {
        val address = testAddress(13L)
        val walletId = insertTestWallet(id = 13L, address = address, keyAlias = "key_nft_fail")
        val auth = issueDeleteAuth("key_nft_fail")

        database.nftQueries.insert("1", "0xnft", address, ChainType.ETHEREUM.name, 1L, "NFT", null, null, null, null, null, null, null, 0L, 0L, null, null, null, null, null, null, null)

        // 手動模擬 Step 3 執行失敗時記錄 FAILED
        val repo = createRepository()
        database.deletionJournalQueries.insertDeletionJournal(walletId, "key_nft_fail", DeletionState.TOMBSTONED.name, "NFT DB lock", 0L, 1000L, 1000L)
        database.deletionStepLedgerQueries.upsertStep(walletId, DeletionStep.NFT_ROWS.name, DeletionStepStatus.FAILED.name, "NFT delete lock", 1L, 1000L)

        // 執行 Startup Reconciliation 續跑清理
        val recRes = repo.reconcileStartupState()
        assertTrue("Reconciliation must recover from NFT failure: ${(recRes as? Result.Failure)?.exception?.message}", recRes is Result.Success)

        // 驗證 NFT 已被清理且步驟為 PASS
        assertEquals(0, database.nftQueries.selectByWalletAddress(address).executeAsList().size)
        assertEquals(DeletionStepStatus.PASS.name, database.deletionStepLedgerQueries.selectStep(walletId, DeletionStep.NFT_ROWS.name).executeAsOneOrNull()?.status)
    }

    @Test
    fun test_04_step_4_push_subscriptions_failure_and_recovery() = runBlocking {
        val address = testAddress(14L)
        val walletId = insertTestWallet(id = 14L, address = address, keyAlias = "key_push_fail")
        database.pushSubscriptionQueries.upsertSubscription(address, "0xchannel", 1L, 1000L, null, 1000L)

        val repo = createRepository()
        database.deletionJournalQueries.insertDeletionJournal(walletId, "key_push_fail", DeletionState.TOMBSTONED.name, "Push sub error", 0L, 1000L, 1000L)
        database.deletionStepLedgerQueries.upsertStep(walletId, DeletionStep.PUSH_SUBSCRIPTIONS.name, DeletionStepStatus.FAILED.name, "Push error", 1L, 1000L)

        val recRes = repo.reconcileStartupState()
        assertTrue("Reconciliation must recover from Push failure", recRes is Result.Success)
        assertEquals(0, database.pushSubscriptionQueries.selectByWallet(address).executeAsList().size)
        assertEquals(DeletionStepStatus.PASS.name, database.deletionStepLedgerQueries.selectStep(walletId, DeletionStep.PUSH_SUBSCRIPTIONS.name).executeAsOneOrNull()?.status)
    }

    @Test
    fun test_05_step_5_notification_history_failure_and_recovery() = runBlocking {
        val walletId = insertTestWallet(id = 15L, keyAlias = "key_notif_hist_fail")
        database.notificationHistoryQueries.insertNotification("notif_id_15", walletId.toString(), "TX", "Title", "Body", "{}", 1000L, 0L, null)

        val repo = createRepository()
        database.deletionJournalQueries.insertDeletionJournal(walletId, "key_notif_hist_fail", DeletionState.TOMBSTONED.name, "Notif hist error", 0L, 1000L, 1000L)
        database.deletionStepLedgerQueries.upsertStep(walletId, DeletionStep.NOTIFICATION_HISTORY.name, DeletionStepStatus.FAILED.name, "History delete error", 1L, 1000L)

        val recRes = repo.reconcileStartupState()
        assertTrue("Reconciliation must recover from Notification History failure", recRes is Result.Success)
        assertEquals(0, database.notificationHistoryQueries.selectAllByWallet(walletId.toString()).executeAsList().size)
        assertEquals(DeletionStepStatus.PASS.name, database.deletionStepLedgerQueries.selectStep(walletId, DeletionStep.NOTIFICATION_HISTORY.name).executeAsOneOrNull()?.status)
    }

    @Test
    fun test_06_step_6_notification_preferences_failure_and_recovery() = runBlocking {
        val walletId = insertTestWallet(id = 16L, keyAlias = "key_notif_pref_fail")
        database.notificationPreferencesQueries.insertOrUpdate(walletId.toString(), 1L, 1L, 1L, 1L, 1L, null, null, null, 1L, 1L, 1000L)

        val repo = createRepository()
        database.deletionJournalQueries.insertDeletionJournal(walletId, "key_notif_pref_fail", DeletionState.TOMBSTONED.name, "Pref error", 0L, 1000L, 1000L)
        database.deletionStepLedgerQueries.upsertStep(walletId, DeletionStep.NOTIFICATION_PREFERENCES.name, DeletionStepStatus.FAILED.name, "Pref delete error", 1L, 1000L)

        val recRes = repo.reconcileStartupState()
        assertTrue("Reconciliation must recover from Notification Preferences failure", recRes is Result.Success)
        assertNull(database.notificationPreferencesQueries.selectByWalletId(walletId.toString()).executeAsOneOrNull())
        assertEquals(DeletionStepStatus.PASS.name, database.deletionStepLedgerQueries.selectStep(walletId, DeletionStep.NOTIFICATION_PREFERENCES.name).executeAsOneOrNull()?.status)
    }

    @Test
    fun test_07_step_7_keystone_data_failure_and_recovery() = runBlocking {
        val walletId = insertTestWallet(id = 17L, keyAlias = "key_keystone_fail")
        database.keystoneDataQueries.insertOrUpdate(walletId, "dev1", "Keystone", "1.0", null, null, null, null, null, null, null, 1L, null, null, 0L, 1000L)

        val repo = createRepository()
        database.deletionJournalQueries.insertDeletionJournal(walletId, "key_keystone_fail", DeletionState.TOMBSTONED.name, "Keystone error", 0L, 1000L, 1000L)
        database.deletionStepLedgerQueries.upsertStep(walletId, DeletionStep.KEYSTONE_DATA.name, DeletionStepStatus.FAILED.name, "Keystone delete error", 1L, 1000L)

        val recRes = repo.reconcileStartupState()
        assertTrue("Reconciliation must recover from Keystone failure", recRes is Result.Success)
        assertEquals(0, database.keystoneDataQueries.selectByWalletId(walletId).executeAsList().size)
        assertEquals(DeletionStepStatus.PASS.name, database.deletionStepLedgerQueries.selectStep(walletId, DeletionStep.KEYSTONE_DATA.name).executeAsOneOrNull()?.status)
    }

    @Test
    fun test_08_step_8_token_rows_failure_and_recovery() = runBlocking {
        val walletId = insertTestWallet(id = 18L, keyAlias = "key_token_fail")
        database.tokenQueries.insert(walletId, "0xtk", "TKN", "Token", 18L, ChainType.ETHEREUM.name, 1L, "100", 1.0, 0.0, null, 0L, 0L, "ERC20", "{}")

        val repo = createRepository()
        database.deletionJournalQueries.insertDeletionJournal(walletId, "key_token_fail", DeletionState.TOMBSTONED.name, "Token error", 0L, 1000L, 1000L)
        database.deletionStepLedgerQueries.upsertStep(walletId, DeletionStep.TOKEN_ROWS.name, DeletionStepStatus.FAILED.name, "Token delete error", 1L, 1000L)

        val recRes = repo.reconcileStartupState()
        assertTrue("Reconciliation must recover from Token failure", recRes is Result.Success)
        assertEquals(0, database.tokenQueries.selectByWalletId(walletId).executeAsList().size)
        assertEquals(DeletionStepStatus.PASS.name, database.deletionStepLedgerQueries.selectStep(walletId, DeletionStep.TOKEN_ROWS.name).executeAsOneOrNull()?.status)
    }

    @Test
    fun test_09_step_9_transaction_rows_failure_and_recovery() = runBlocking {
        val address = testAddress(19L)
        val walletId = insertTestWallet(id = 19L, address = address, keyAlias = "key_tx_fail")
        database.transactionQueries.insert(walletId, "0xtx_rec", address, "0xto", "1.0", "20", "21000", "21000", 0L, null, "SUCCESS", "TRANSFER", ChainType.ETHEREUM.name, 1L, 100L, 1000L, null, null, null, "0.01", "ETH", null, null, null, "{}")

        val repo = createRepository()
        database.deletionJournalQueries.insertDeletionJournal(walletId, "key_tx_fail", DeletionState.TOMBSTONED.name, "Tx error", 0L, 1000L, 1000L)
        database.deletionStepLedgerQueries.upsertStep(walletId, DeletionStep.TRANSACTION_ROWS.name, DeletionStepStatus.FAILED.name, "Tx delete error", 1L, 1000L)

        val recRes = repo.reconcileStartupState()
        assertTrue("Reconciliation must recover from Transaction failure", recRes is Result.Success)
        assertEquals(0, database.transactionQueries.selectByWalletId(walletId).executeAsList().size)
        assertEquals(DeletionStepStatus.PASS.name, database.deletionStepLedgerQueries.selectStep(walletId, DeletionStep.TRANSACTION_ROWS.name).executeAsOneOrNull()?.status)
    }

    // =========================================================================
    // 4. Step 10 (PRICE_ALERT_ROWS) 實質刪除與 count 驗證
    // =========================================================================

    @Test
    fun test_10_step_10_price_alert_rows_substantive_deletion_and_count_verification() = runBlocking {
        val walletId = insertTestWallet(id = 20L, keyAlias = "key_price_alert")
        val auth = issueDeleteAuth("key_price_alert")

        // 插入 Token 與關聯的 PriceAlert
        database.tokenQueries.insert(
            wallet_id = walletId,
            address = "0xtk_alert",
            symbol = "ALERT_COIN",
            name = "AlertCoin",
            decimals = 18L,
            chain_type = ChainType.ETHEREUM.name,
            chain_id = 1L,
            balance = "500",
            usd_price = 10.0,
            price_change_24h = 0.0,
            logo_url = null,
            is_native = 0L,
            is_hidden = 0L,
            contract_type = "ERC20",
            metadata = "{}"
        )

        database.priceAlertQueries.insert(
            wallet_id = walletId.toString(),
            asset_symbol = "ALERT_COIN",
            asset_name = "AlertCoin",
            contract_address = "0xtk_alert",
            chain_type = ChainType.ETHEREUM.name,
            chain_id = 1L,
            alert_type = "ABOVE",
            target_price = 15.0,
            current_price = 10.0,
            percentage_threshold = null,
            is_enabled = 1L,
            user_notes = "Sell target",
            webhook_url = null,
            repeat_interval = 0L
        )

        assertEquals(1L, database.priceAlertQueries.countByWalletId(walletId.toString()).executeAsOne())

        val repo = createRepository()
        val result = repo.deleteWallet(walletId.toString(), auth)
        assertTrue("deleteWallet must succeed", result is Result.Success)

        // 驗證 Price Alert 被實質刪除且剩餘數量 == 0
        assertEquals(0L, database.priceAlertQueries.countByWalletId(walletId.toString()).executeAsOne())

        val stepRecord = database.deletionStepLedgerQueries.selectStep(walletId, DeletionStep.PRICE_ALERT_ROWS.name).executeAsOneOrNull()
        assertEquals(DeletionStepStatus.PASS.name, stepRecord?.status)
    }

    // =========================================================================
    // 5. Step 11-14 Platform Hooks 故障注入 -> Fail-Closed & Recovery
    // =========================================================================

    @Test
    fun test_11_step_11_work_manager_jobs_hook_failure_and_recovery() = runBlocking {
        val walletId = insertTestWallet(id = 21L, keyAlias = "key_hook_wm")
        val auth = issueDeleteAuth("key_hook_wm")

        var hookShouldFail = true
        val hook = object : PlatformDeletionCleanupHook {
            override suspend fun cancelWorkManagerJobs(wId: Long): Result<Unit> {
                return if (hookShouldFail) Result.Failure(RuntimeException("WorkManager service unavailable")) else Result.Success(Unit)
            }
            override suspend fun cancelBackgroundSync(wId: Long): Result<Unit> = Result.Success(Unit)
            override suspend fun invalidateTiles(): Result<Unit> = Result.Success(Unit)
            override suspend fun invalidateComplications(): Result<Unit> = Result.Success(Unit)
        }

        val repo = createRepository(hook = hook)
        val result = repo.deleteWallet(walletId.toString(), auth)

        assertTrue(result is Result.Failure)
        assertEquals(DeletionState.RECOVERY_REQUIRED.name, database.deletionJournalQueries.selectByWalletId(walletId).executeAsOneOrNull()?.state)
        assertEquals(DeletionStepStatus.FAILED.name, database.deletionStepLedgerQueries.selectStep(walletId, DeletionStep.WORK_MANAGER_JOBS.name).executeAsOneOrNull()?.status)
        assertNotNull(database.walletQueries.selectById(walletId).executeAsOneOrNull())

        // 模擬服務恢復，執行 Startup Reconciliation 續跑清理
        hookShouldFail = false
        val recRes = repo.reconcileStartupState()
        assertTrue("Reconciliation must succeed", recRes is Result.Success)

        assertEquals(DeletionState.COMPLETED.name, database.deletionJournalQueries.selectByWalletId(walletId).executeAsOneOrNull()?.state)
        assertEquals(DeletionStepStatus.PASS.name, database.deletionStepLedgerQueries.selectStep(walletId, DeletionStep.WORK_MANAGER_JOBS.name).executeAsOneOrNull()?.status)
        assertNull(database.walletQueries.selectById(walletId).executeAsOneOrNull())
    }

    @Test
    fun test_12_step_12_background_sync_hook_failure_and_recovery() = runBlocking {
        val walletId = insertTestWallet(id = 22L, keyAlias = "key_hook_sync")
        val auth = issueDeleteAuth("key_hook_sync")

        var hookShouldFail = true
        val hook = object : PlatformDeletionCleanupHook {
            override suspend fun cancelWorkManagerJobs(wId: Long): Result<Unit> = Result.Success(Unit)
            override suspend fun cancelBackgroundSync(wId: Long): Result<Unit> {
                return if (hookShouldFail) Result.Failure(RuntimeException("Sync cancel error")) else Result.Success(Unit)
            }
            override suspend fun invalidateTiles(): Result<Unit> = Result.Success(Unit)
            override suspend fun invalidateComplications(): Result<Unit> = Result.Success(Unit)
        }

        val repo = createRepository(hook = hook)
        val result = repo.deleteWallet(walletId.toString(), auth)
        assertTrue(result is Result.Failure)

        assertEquals(DeletionState.RECOVERY_REQUIRED.name, database.deletionJournalQueries.selectByWalletId(walletId).executeAsOneOrNull()?.state)
        assertEquals(DeletionStepStatus.FAILED.name, database.deletionStepLedgerQueries.selectStep(walletId, DeletionStep.BACKGROUND_SYNC.name).executeAsOneOrNull()?.status)

        // DB row remains
        assertNotNull(database.walletQueries.selectById(walletId).executeAsOneOrNull())

        // Recovery
        hookShouldFail = false
        val recRes = repo.reconcileStartupState()
        assertTrue(recRes is Result.Success)
        assertEquals(DeletionStepStatus.PASS.name, database.deletionStepLedgerQueries.selectStep(walletId, DeletionStep.BACKGROUND_SYNC.name).executeAsOneOrNull()?.status)
    }

    @Test
    fun test_13_step_13_tiles_hook_failure_and_recovery() = runBlocking {
        val walletId = insertTestWallet(id = 23L, keyAlias = "key_hook_tiles")
        val auth = issueDeleteAuth("key_hook_tiles")

        var hookShouldFail = true
        val hook = object : PlatformDeletionCleanupHook {
            override suspend fun cancelWorkManagerJobs(wId: Long): Result<Unit> = Result.Success(Unit)
            override suspend fun cancelBackgroundSync(wId: Long): Result<Unit> = Result.Success(Unit)
            override suspend fun invalidateTiles(): Result<Unit> {
                return if (hookShouldFail) Result.Failure(RuntimeException("Tiles update error")) else Result.Success(Unit)
            }
            override suspend fun invalidateComplications(): Result<Unit> = Result.Success(Unit)
        }

        val repo = createRepository(hook = hook)
        val result = repo.deleteWallet(walletId.toString(), auth)
        assertTrue(result is Result.Failure)

        assertEquals(DeletionState.RECOVERY_REQUIRED.name, database.deletionJournalQueries.selectByWalletId(walletId).executeAsOneOrNull()?.state)
        assertEquals(DeletionStepStatus.FAILED.name, database.deletionStepLedgerQueries.selectStep(walletId, DeletionStep.TILES.name).executeAsOneOrNull()?.status)

        // Recovery
        hookShouldFail = false
        val recRes = repo.reconcileStartupState()
        assertTrue(recRes is Result.Success)
        assertEquals(DeletionStepStatus.PASS.name, database.deletionStepLedgerQueries.selectStep(walletId, DeletionStep.TILES.name).executeAsOneOrNull()?.status)
    }

    @Test
    fun test_14_step_14_complications_hook_failure_and_recovery() = runBlocking {
        val walletId = insertTestWallet(id = 24L, keyAlias = "key_hook_comp")
        val auth = issueDeleteAuth("key_hook_comp")

        var hookShouldFail = true
        val hook = object : PlatformDeletionCleanupHook {
            override suspend fun cancelWorkManagerJobs(wId: Long): Result<Unit> = Result.Success(Unit)
            override suspend fun cancelBackgroundSync(wId: Long): Result<Unit> = Result.Success(Unit)
            override suspend fun invalidateTiles(): Result<Unit> = Result.Success(Unit)
            override suspend fun invalidateComplications(): Result<Unit> {
                return if (hookShouldFail) Result.Failure(RuntimeException("Complication update error")) else Result.Success(Unit)
            }
        }

        val repo = createRepository(hook = hook)
        val result = repo.deleteWallet(walletId.toString(), auth)
        assertTrue(result is Result.Failure)

        assertEquals(DeletionState.RECOVERY_REQUIRED.name, database.deletionJournalQueries.selectByWalletId(walletId).executeAsOneOrNull()?.state)
        assertEquals(DeletionStepStatus.FAILED.name, database.deletionStepLedgerQueries.selectStep(walletId, DeletionStep.COMPLICATIONS.name).executeAsOneOrNull()?.status)

        // Recovery
        hookShouldFail = false
        val recRes = repo.reconcileStartupState()
        assertTrue(recRes is Result.Success)
        assertEquals(DeletionStepStatus.PASS.name, database.deletionStepLedgerQueries.selectStep(walletId, DeletionStep.COMPLICATIONS.name).executeAsOneOrNull()?.status)
    }

    // =========================================================================
    // 6. Step 15 (CACHES) 實質清理與驗證
    // =========================================================================

    @Test
    fun test_15_step_15_caches_substantive_cleanup_and_verification() = runBlocking {
        val walletId = insertTestWallet(id = 25L, keyAlias = "key_cache_test", address = "0xcache123")
        val auth = issueDeleteAuth("key_cache_test")

        // 預先寫入快取
        GlobalCacheManager.walletCache.put(walletId.toString(), "WalletCachedDataPayload")
        GlobalCacheManager.nftCache.put("0xcache123", "NFTCachedDataPayload")

        assertNotNull(GlobalCacheManager.walletCache.get(walletId.toString()))

        val repo = createRepository()
        val result = repo.deleteWallet(walletId.toString(), auth)

        assertTrue(result is Result.Success)

        // 驗證快取已被清除
        assertNull(GlobalCacheManager.walletCache.get(walletId.toString()))
        val stepRecord = database.deletionStepLedgerQueries.selectStep(walletId, DeletionStep.CACHES.name).executeAsOneOrNull()
        assertEquals(DeletionStepStatus.PASS.name, stepRecord?.status)
    }

    // =========================================================================
    // 7. Step 16 (ACTIVE_POINTER) 故障注入 -> 嚴格禁止刪除 DB wallet 主記錄
    // =========================================================================

    @Test
    fun test_16_step_16_active_pointer_failure_strictly_prevents_db_delete_and_completed() = runBlocking {
        // 先插入第二個備用錢包 (is_active = false)
        val otherWalletId = insertTestWallet(id = 27L, keyAlias = "key_other_27", isActive = false)
        // 再插入目標錢包並設為 active (此時 walletId.is_active == 1)
        val walletId = insertTestWallet(id = 26L, keyAlias = "key_active_fail", isActive = true)

        val auth = issueDeleteAuth("key_active_fail", walletId = walletId.toString())

        val spyWalletQueries = spy(database.walletQueries)
        doThrow(RuntimeException("Active pointer CAS lock failure")).whenever(spyWalletQueries).setActiveWallet(any())

        val repo = createRepository(customWalletQueries = spyWalletQueries)
        val result = repo.deleteWallet(walletId.toString(), auth)

        assertTrue("deleteWallet must fail when active pointer transition fails", result is Result.Failure)

        // 嚴格斷言：
        // 1. Journal 進入 RECOVERY_REQUIRED
        assertEquals(DeletionState.RECOVERY_REQUIRED.name, database.deletionJournalQueries.selectByWalletId(walletId).executeAsOneOrNull()?.state)

        // 2. ACTIVE_POINTER 記錄為 FAILED
        assertEquals(DeletionStepStatus.FAILED.name, database.deletionStepLedgerQueries.selectStep(walletId, DeletionStep.ACTIVE_POINTER.name).executeAsOneOrNull()?.status)

        // 3. DB 主記錄嚴禁被刪除！
        val walletInDb = database.walletQueries.selectById(walletId).executeAsOneOrNull()
        assertNotNull("CRITICAL: Wallet DB row MUST NOT be deleted when active pointer transition fails", walletInDb)

        // 4. WALLET_DB_ROW 步驟不能為 PASS
        val dbRowStep = database.deletionStepLedgerQueries.selectStep(walletId, DeletionStep.WALLET_DB_ROW.name).executeAsOneOrNull()
        assertNotEquals(DeletionStepStatus.PASS.name, dbRowStep?.status)
    }

    // =========================================================================
    // 8. Step 17 (WALLET_DB_ROW) 故障注入 -> 標記 FAILED & 禁止進入 COMPLETED
    // =========================================================================

    @Test
    fun test_17_step_17_wallet_db_row_failure_fails_closed() = runBlocking {
        val walletId = insertTestWallet(id = 28L, keyAlias = "key_db_fail", isActive = false)
        val auth = issueDeleteAuth("key_db_fail", walletId = walletId.toString())

        val spyWalletQueries = spy(database.walletQueries)
        doThrow(RuntimeException("Disk I/O error during row deletion")).whenever(spyWalletQueries).delete(walletId)

        val repo = createRepository(customWalletQueries = spyWalletQueries)
        val result = repo.deleteWallet(walletId.toString(), auth)

        assertTrue(result is Result.Failure)

        assertEquals(DeletionState.RECOVERY_REQUIRED.name, database.deletionJournalQueries.selectByWalletId(walletId).executeAsOneOrNull()?.state)
        assertEquals(DeletionStepStatus.FAILED.name, database.deletionStepLedgerQueries.selectStep(walletId, DeletionStep.WALLET_DB_ROW.name).executeAsOneOrNull()?.status)
        assertNotNull(database.walletQueries.selectById(walletId).executeAsOneOrNull())
    }

    // =========================================================================
    // 9. 全量 17 步 PASS 斷言閘門驗證 (16 PASS + 1 PENDING/FAILED 阻斷 COMPLETED)
    // =========================================================================

    @Test
    fun test_18_17_step_assertion_gate_blocks_incomplete_ledger() = runBlocking {
        val walletId = insertTestWallet(id = 29L, keyAlias = "key_gate_test", isActive = false)

        // 手動在 ledger 中寫入 16 個 PASS，刻意留下 1 個 PENDING (例如 TILES)
        for (step in DeletionStep.values()) {
            val status = if (step == DeletionStep.TILES) DeletionStepStatus.PENDING else DeletionStepStatus.PASS
            database.deletionStepLedgerQueries.upsertStep(
                wallet_id = walletId,
                step_name = step.name,
                status = status.name,
                error_message = null,
                retry_count = 0L,
                updated_at = System.currentTimeMillis()
            )
        }

        database.deletionJournalQueries.insertDeletionJournal(
            wallet_id = walletId,
            key_alias = "key_gate_test",
            state = DeletionState.REFERENCES_CLEARED.name,
            last_error = null,
            retry_count = 0L,
            created_at = System.currentTimeMillis(),
            updated_at = System.currentTimeMillis()
        )

        val repo = createRepository()
        // 嘗試透過 reconcileStartupState 推進
        val recRes = repo.reconcileStartupState()
        assertTrue("Reconcile must fail when TILES is still PENDING", recRes is Result.Failure)

        // 驗證狀態未被推進至 COMPLETED
        val journal = database.deletionJournalQueries.selectByWalletId(walletId).executeAsOneOrNull()
        assertNotEquals(DeletionState.COMPLETED.name, journal?.state)
    }

    // =========================================================================
    // 10. 全量 17 步 End-to-End 完美清理驗證
    // =========================================================================

    @Test
    fun test_19_full_17_step_successful_end_to_end_deletion_and_state_verification() = runBlocking {
        val address = "0x9999999999999999999999999999999999999999"
        val keyAlias = "key_full_success"
        val walletId = insertTestWallet(id = 30L, address = address, keyAlias = keyAlias, isActive = true)

        // 插入所有 17 個子系統關聯數據
        database.nftQueries.insert("1", "0xnft", address, ChainType.ETHEREUM.name, 1L, "NFT", null, null, null, null, null, null, null, 0L, 0L, null, null, null, null, null, null, null)
        database.pushSubscriptionQueries.upsertSubscription(address, "0xchannel", 1L, 1000L, null, 1000L)
        database.tokenQueries.insert(walletId, "0xtoken", "FULL_COIN", "FullCoin", 18L, ChainType.ETHEREUM.name, 1L, "100", 1.0, 0.0, null, 0L, 0L, "ERC20", "{}")
        database.transactionQueries.insert(walletId, "0xtx", address, "0xto", "1.0", "20", "21000", "21000", 0L, null, "SUCCESS", "TRANSFER", ChainType.ETHEREUM.name, 1L, 100L, 1000L, null, null, null, "0.01", "ETH", null, null, null, "{}")
        database.priceAlertQueries.insert(
            wallet_id = walletId.toString(),
            asset_symbol = "FULL_COIN",
            asset_name = "FullCoin",
            contract_address = "0xtoken",
            chain_type = ChainType.ETHEREUM.name,
            chain_id = 1L,
            alert_type = "ABOVE",
            target_price = 20.0,
            current_price = 10.0,
            percentage_threshold = null,
            is_enabled = 1L,
            user_notes = "Target",
            webhook_url = null,
            repeat_interval = 0L
        )
        GlobalCacheManager.walletCache.put(walletId.toString(), "CacheValue")

        val auth = issueDeleteAuth(keyAlias, walletId = walletId.toString())
        val repo = createRepository()

        val delRes = repo.deleteWallet(walletId.toString(), auth)
        assertTrue("deleteWallet must succeed: ${(delRes as? Result.Failure)?.exception?.message}", delRes is Result.Success)

        // 驗證 17 步全部 PASS
        val steps = database.deletionStepLedgerQueries.selectStepsByWalletId(walletId).executeAsList()
        assertEquals("Must record exactly 17 steps", 17, steps.size)
        assertTrue("All 17 steps must be PASS", steps.all { it.status == DeletionStepStatus.PASS.name })

        // 驗證所有子表資料被清理
        assertEquals(0, database.nftQueries.selectByWalletAddress(address).executeAsList().size)
        assertEquals(0, database.pushSubscriptionQueries.selectByWallet(address).executeAsList().size)
        assertEquals(0, database.tokenQueries.selectByWalletId(walletId).executeAsList().size)
        assertEquals(0, database.transactionQueries.selectByWalletId(walletId).executeAsList().size)
        assertEquals(0L, database.priceAlertQueries.countByWalletId(walletId.toString()).executeAsOne())
        assertNull(GlobalCacheManager.walletCache.get(walletId.toString()))

        // 驗證 DB Wallet 主記錄被刪除
        assertNull(database.walletQueries.selectById(walletId).executeAsOneOrNull())

        // 驗證 Key 實質刪除
        assertFalse(fakeKeyManager.hasPrivateKey(keyAlias))

        // 驗證 Journal 狀態為 COMPLETED
        val journal = database.deletionJournalQueries.selectByWalletId(walletId).executeAsOneOrNull()
        assertEquals(DeletionState.COMPLETED.name, journal?.state)
    }
}

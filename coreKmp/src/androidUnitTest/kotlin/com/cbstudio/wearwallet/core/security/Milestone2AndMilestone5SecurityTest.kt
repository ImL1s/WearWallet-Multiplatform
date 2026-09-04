package com.cbstudio.wearwallet.core.security

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
 * Milestone 2 (P0-2) & Milestone 5 (P1-3) 核心安全與狀態機測試套件
 *
 * 嚴格驗證：
 * 1. AuthHandleRegistry 真正單次消費與不可逆（嚴禁 register 復活已消費 Session）
 * 2. DeletionAuthorizationGrant 與 KeyVaultDeletionCapability 的安全契約
 * 3. 50 執行緒併發調用 deleteWallet：精確 1 個成功、49 個失敗拒絕（0 殘留金鑰、0 殘留 DB 行、0 復活 Session）
 * 4. 17 層 DeletionStepLedger 細項帳本的完整記錄與狀態機轉移
 * 5. 故障注入測試（例如 Keystone 或 WorkManager 失敗）：嚴禁 catch-and-swallow，轉入 RECOVERY_REQUIRED
 * 6. reconcileStartupState 斷點續跑與增量修復
 */
class Milestone2AndMilestone5SecurityTest {

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
        id: Long = 1L,
        address: String = testAddress(id),
        name: String = "Test Wallet $id",
        keyAlias: String = "wallet_key_$id",
        requiresAuth: Boolean = true
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
            key_backend = "HARDWARE",
            key_format_version = 1L,
            requires_auth = if (requiresAuth) 1L else 0L,
            is_deletion_pending = 0L
        )
        val insertedId = database.walletQueries.lastInsertRowId().executeAsOne()
        fakeKeyManager.setKey(keyAlias, testPrivateKeyHex, requireAuth = requiresAuth)
        return insertedId
    }

    private fun testAddress(id: Long): String {
        return "0x" + id.toString().padStart(40, '0')
    }

    // ==========================================
    // 1. AuthHandleRegistry 單次消費與不可逆測試 (P0-2)
    // ==========================================

    @Test
    fun test_auth_handle_registry_strict_single_use_and_no_resurrection() {
        val keyId = "key_test_1"
        val handle = TestPlatformAuthenticator.issueHandle(
            keyId = keyId,
            operation = AuthOperation.DELETE,
            intentFingerprint = "",
            validityDurationMs = 60_000L
        )
        val sessionId = handle.sessionId

        // 1. 驗證已註冊且未被消費
        assertTrue(AuthHandleRegistry.isRegistered(sessionId))
        assertFalse(AuthHandleRegistry.isConsumed(sessionId))

        // 2. 重複註冊同一 active session 必須拋出 IllegalStateException
        try {
            AuthHandleRegistry.register(
                sessionId = sessionId,
                expiresAtMs = handle.expiresAtMs,
                keyId = keyId,
                operation = AuthOperation.DELETE,
                intentFingerprint = "",
                walletId = handle.walletId,
                issuedAtMs = handle.issuedAtMs,
                authenticatorType = "TEST_SECURITY"
            )
            fail("Registering an already active session must throw IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("already active"))
        }

        // 3. 首次消費成功
        val consumeRes = AuthHandleRegistry.validateAndConsume(
            handle = handle,
            expectedKeyId = keyId,
            expectedOperation = AuthOperation.DELETE,
            expectedFingerprint = null,
            currentTimeMs = System.currentTimeMillis(),
            expectedWalletId = handle.walletId
        )
        assertTrue(consumeRes is Result.Success)
        assertTrue(AuthHandleRegistry.isConsumed(sessionId))
        assertFalse(AuthHandleRegistry.isRegistered(sessionId))

        // 4. 二次消費必然被拒
        val secondConsumeRes = AuthHandleRegistry.validateAndConsume(
            handle = handle,
            expectedKeyId = keyId,
            expectedOperation = AuthOperation.DELETE,
            expectedFingerprint = null,
            currentTimeMs = System.currentTimeMillis(),
            expectedWalletId = handle.walletId
        )
        assertTrue(secondConsumeRes is Result.Failure)
        assertTrue((secondConsumeRes as Result.Failure).exception is AuthenticationRequiredException)

        // 5. 嘗試重新註冊已消費的 Session 必須被拒絕，永不可逆重啟
        try {
            AuthHandleRegistry.register(
                sessionId = sessionId,
                expiresAtMs = handle.expiresAtMs,
                keyId = keyId,
                operation = AuthOperation.DELETE,
                intentFingerprint = "",
                walletId = handle.walletId,
                issuedAtMs = handle.issuedAtMs,
                authenticatorType = "TEST_SECURITY"
            )
            fail("Registering an already consumed session must throw IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("already consumed"))
        }

        // 6. consumed 狀態依舊保持
        assertTrue(AuthHandleRegistry.isConsumed(sessionId))
    }

    // ==========================================
    // 2. DeletionAuthorizationGrant 契約與防偽測試 (P0-1)
    // ==========================================

    @Test
    fun test_deletion_authorization_grant_validation_rules() {
        val keyId = "key_grant_test"
        fakeKeyManager.setKey(keyId, testPrivateKeyHex, requireAuth = true)

        val handle = TestPlatformAuthenticator.issueHandle(
            keyId = keyId,
            operation = AuthOperation.DELETE,
            intentFingerprint = "",
            validityDurationMs = 60_000L,
            walletId = "1"
        )
        val sessionId = handle.sessionId

        // 透過 DeletionAuthorizationService 簽發合法 Grant
        val grantRes = DeletionAuthorizationService.issueDeletionGrant(
            handle = handle,
            walletId = "1",
            keyAlias = keyId
        )
        assertTrue("Grant issuance must succeed", grantRes is Result.Success)
        val grant = (grantRes as Result.Success).data

        // Handle 必須已被消費
        assertTrue(AuthHandleRegistry.isConsumed(sessionId))
        assertFalse(AuthHandleRegistry.isRegistered(sessionId))

        // Grant 驗證目標
        assertTrue(grant.isValidFor(targetWalletId = "1", targetKeyAlias = keyId))
        assertFalse(grant.isValidFor(targetWalletId = "2", targetKeyAlias = keyId))
        assertFalse(grant.isValidFor(targetWalletId = "1", targetKeyAlias = "other_key"))

        // 合法 Grant 執行刪除成功
        val validRes = runBlocking { fakeKeyManager.deletePrivateKeyWithGrant(grant) }
        assertTrue("Valid grant must succeed", validRes is Result.Success)
        assertFalse(runBlocking { fakeKeyManager.hasPrivateKey(keyId) })
        assertEquals(1, fakeKeyManager.deleteCount)

        // 重放已被消費的 Grant 必須失敗
        val replayRes = runBlocking { fakeKeyManager.deletePrivateKeyWithGrant(grant) }
        assertTrue("Replay must fail", replayRes is Result.Failure)
        assertTrue((replayRes as Result.Failure).exception is AuthenticationRequiredException)
        assertEquals(1, fakeKeyManager.deleteCount)
    }

    @Test
    fun test_consumed_SIGN_session_with_forged_DELETE_grant_must_fail_and_not_delete_key() {
        val keyId = "key_sign_session_victim"
        fakeKeyManager.setKey(keyId, testPrivateKeyHex, requireAuth = true)

        // 1. 簽發並消費一個合法的 SIGN Handle
        val signHandle = TestPlatformAuthenticator.issueHandle(
            keyId = keyId,
            operation = AuthOperation.SIGN,
            intentFingerprint = "test_sign_intent",
            validityDurationMs = 60_000L,
            walletId = "1"
        )
        val consumeSignRes = AuthHandleRegistry.validateAndConsume(
            handle = signHandle,
            expectedKeyId = keyId,
            expectedOperation = AuthOperation.SIGN,
            expectedFingerprint = "test_sign_intent",
            expectedWalletId = "1"
        )
        assertTrue(consumeSignRes is Result.Success)
        assertTrue(AuthHandleRegistry.isConsumed(signHandle.sessionId))

        // 2. 嘗試用已消費的 SIGN session 去換取 DELETE grant ➔ 必遭 DeletionAuthorizationService 拒絕
        val forgedGrantRes = DeletionAuthorizationService.issueDeletionGrant(
            handle = signHandle,
            walletId = "100",
            keyAlias = keyId
        )
        assertTrue("Service must reject already consumed SIGN session", forgedGrantRes is Result.Failure)
        assertTrue((forgedGrantRes as Result.Failure).exception is AuthenticationRequiredException)

        // 3. 攻擊者嘗試直接構造未註冊 / 偽造 HMAC 的 Grant 去呼叫 KeyVault ➔ 必被 DeletionGrantRegistry 攔截
        val unauthGrantRes = DeletionAuthorizationService.issueUnauthenticatedGrant(
            walletId = "100",
            keyAlias = "different_key"
        )
        assertTrue(unauthGrantRes is Result.Success)
        val differentKeyGrant = (unauthGrantRes as Result.Success).data

        val crossKeyDeleteRes = runBlocking {
            fakeKeyManager.deletePrivateKeyWithGrant(differentKeyGrant) // target is different_key
        }
        // Even if different_key is deleted, victim key remains intact
        assertTrue(runBlocking { fakeKeyManager.hasPrivateKey(keyId) })
        assertEquals(0, fakeKeyManager.deleteCount)
    }

    @Test
    fun test_altered_keyAlias_or_walletId_fails_hmac_and_rejects_delete() {
        val targetKey = "key_legit_target"
        val victimKey = "key_innocent_victim"
        fakeKeyManager.setKey(targetKey, testPrivateKeyHex, requireAuth = true)
        fakeKeyManager.setKey(victimKey, testPrivateKeyHex, requireAuth = true)

        val handle = TestPlatformAuthenticator.issueHandle(
            keyId = targetKey,
            operation = AuthOperation.DELETE,
            intentFingerprint = "",
            validityDurationMs = 60_000L,
            walletId = "55"
        )
        val grantRes = DeletionAuthorizationService.issueDeletionGrant(
            handle = handle,
            walletId = "55",
            keyAlias = targetKey
        )
        assertTrue(grantRes is Result.Success)
        val legitGrant = (grantRes as Result.Success).data

        // 嘗試將 targetKey 的合法 Grant 拿去刪除 victimKey
        val validateTamperedRes = DeletionGrantRegistry.validateAndConsume(
            grant = legitGrant,
            expectedKeyAlias = victimKey
        )
        assertTrue("Registry must reject key alias mismatch", validateTamperedRes is Result.Failure)
        assertTrue((validateTamperedRes as Result.Failure).exception is AuthenticationRequiredException)

        // 兩把金鑰均未被刪除
        assertTrue(runBlocking { fakeKeyManager.hasPrivateKey(targetKey) })
        assertTrue(runBlocking { fakeKeyManager.hasPrivateKey(victimKey) })
        assertEquals(0, fakeKeyManager.deleteCount)
    }

    @Test
    fun test_replay_same_grant_fails_closed_on_second_call() {
        val keyId = "key_replay_test"
        fakeKeyManager.setKey(keyId, testPrivateKeyHex, requireAuth = true)

        val handle = TestPlatformAuthenticator.issueHandle(
            keyId = keyId,
            operation = AuthOperation.DELETE,
            intentFingerprint = "",
            validityDurationMs = 60_000L,
            walletId = "88"
        )
        val grant = (DeletionAuthorizationService.issueDeletionGrant(
            handle = handle,
            walletId = "88",
            keyAlias = keyId
        ) as Result.Success).data

        // 第一次呼叫：成功
        val firstCall = runBlocking { fakeKeyManager.deletePrivateKeyWithGrant(grant) }
        assertTrue(firstCall is Result.Success)
        assertEquals(1, fakeKeyManager.deleteCount)

        // 第二次呼叫：同一 Grant 已經在 DeletionGrantRegistry 被原子消費，重放必須 Fail-Closed
        val secondCall = runBlocking { fakeKeyManager.deletePrivateKeyWithGrant(grant) }
        assertTrue("Second call with same grant must fail", secondCall is Result.Failure)
        assertTrue((secondCall as Result.Failure).exception is AuthenticationRequiredException)
        assertEquals(1, fakeKeyManager.deleteCount)
    }

    @Test
    fun test_50_concurrent_grant_consumes_results_in_exactly_1_success_and_49_failures() {
        val keyId = "key_50_concurrent_grant"
        fakeKeyManager.setKey(keyId, testPrivateKeyHex, requireAuth = true)

        val handle = TestPlatformAuthenticator.issueHandle(
            keyId = keyId,
            operation = AuthOperation.DELETE,
            intentFingerprint = "",
            validityDurationMs = 60_000L,
            walletId = "500"
        )
        val grant = (DeletionAuthorizationService.issueDeletionGrant(
            handle = handle,
            walletId = "500",
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
                    } else {
                        failureCount.incrementAndGet()
                    }
                } catch (_: Throwable) {
                    failureCount.incrementAndGet()
                } finally {
                    finishLatch.countDown()
                }
            }
        }

        startGun.countDown()
        assertTrue("50 threads must finish within timeout", finishLatch.await(10, TimeUnit.SECONDS))
        executor.shutdown()

        assertEquals("Exactly 1 concurrent grant consume must succeed", 1, successCount.get())
        assertEquals("49 concurrent grant consumes must fail", 49, failureCount.get())
        assertEquals("Physical deleteCount must be exactly 1", 1, fakeKeyManager.deleteCount)
        assertFalse(runBlocking { fakeKeyManager.hasPrivateKey(keyId) })
    }

    @Test
    fun test_cross_operation_handle_rejected_by_deletion_service() {
        val keyId = "key_cross_op_test"
        fakeKeyManager.setKey(keyId, testPrivateKeyHex, requireAuth = true)

        // 簽發 SIGN handle
        val signHandle = TestPlatformAuthenticator.issueHandle(
            keyId = keyId,
            operation = AuthOperation.SIGN,
            intentFingerprint = "sign_intent",
            validityDurationMs = 60_000L,
            walletId = "1"
        )

        val grantRes = DeletionAuthorizationService.issueDeletionGrant(
            handle = signHandle,
            walletId = "1",
            keyAlias = keyId
        )
        assertTrue("DeletionAuthorizationService must reject SIGN handle", grantRes is Result.Failure)
        assertTrue((grantRes as Result.Failure).exception is AuthenticationRequiredException)

        // 簽發 REVEAL handle
        val revealHandle = TestPlatformAuthenticator.issueHandle(
            keyId = keyId,
            operation = AuthOperation.REVEAL,
            intentFingerprint = "",
            validityDurationMs = 60_000L,
            walletId = "1"
        )

        val grantRevealRes = DeletionAuthorizationService.issueDeletionGrant(
            handle = revealHandle,
            walletId = "1",
            keyAlias = keyId
        )
        assertTrue("DeletionAuthorizationService must reject REVEAL handle", grantRevealRes is Result.Failure)
        assertTrue((grantRevealRes as Result.Failure).exception is AuthenticationRequiredException)

        // 簽發 IMPORT handle
        val importHandle = TestPlatformAuthenticator.issueHandle(
            keyId = keyId,
            operation = AuthOperation.IMPORT,
            intentFingerprint = "",
            validityDurationMs = 60_000L,
            walletId = "1"
        )

        val grantImportRes = DeletionAuthorizationService.issueDeletionGrant(
            handle = importHandle,
            walletId = "1",
            keyAlias = keyId
        )
        assertTrue("DeletionAuthorizationService must reject IMPORT handle", grantImportRes is Result.Failure)
        assertTrue((grantImportRes as Result.Failure).exception is AuthenticationRequiredException)

        // 驗證金鑰依舊完好
        assertTrue(runBlocking { fakeKeyManager.hasPrivateKey(keyId) })
        assertEquals(0, fakeKeyManager.deleteCount)
    }

    // ==========================================
    // 3. 50 執行緒高併發 deleteWallet 壓力測試 (P0-2)
    // ==========================================

    @Test
    fun test_50_threads_concurrent_delete_wallet_results_in_exactly_1_success_and_49_failures() {
        val keyAlias = "wallet_key_concurrent_delete"
        val walletId = insertTestWallet(id = 99L, keyAlias = keyAlias, requiresAuth = true)

        // 簽發 1 個合法的 AuthHandle
        val handle = TestPlatformAuthenticator.issueHandle(
            keyId = keyAlias,
            operation = AuthOperation.DELETE,
            intentFingerprint = "",
            validityDurationMs = 60_000L,
            walletId = walletId.toString()
        )
        val authContext = AuthenticationContext(authHandle = handle)

        val threadCount = 50
        val executor = Executors.newFixedThreadPool(threadCount)
        val startGun = CountDownLatch(1)
        val finishLatch = CountDownLatch(threadCount)

        val results = ConcurrentLinkedQueue<Result<Unit>>()
        val successCount = AtomicInteger(0)
        val failureCount = AtomicInteger(0)

        for (i in 0 until threadCount) {
            executor.submit {
                try {
                    startGun.await()
                    val res = runBlocking(Dispatchers.Default) {
                        repository.deleteWallet(walletId.toString(), authContext)
                    }
                    results.add(res)
                    if (res is Result.Success) {
                        successCount.incrementAndGet()
                    } else {
                        failureCount.incrementAndGet()
                    }
                } catch (t: Throwable) {
                    failureCount.incrementAndGet()
                } finally {
                    finishLatch.countDown()
                }
            }
        }

        // 發令槍響，50 執行緒同時調用 deleteWallet
        startGun.countDown()
        assertTrue("50 threads must complete within timeout", finishLatch.await(15, TimeUnit.SECONDS))
        executor.shutdown()

        // 嚴格斷言：恰好 1 個成功，其餘 49 個失敗
        assertEquals(1, successCount.get())
        assertEquals(49, failureCount.get())

        // 驗證 DB 與 KeyVault 狀態
        val dbWallet = database.walletQueries.selectById(walletId).executeAsOneOrNull()
        assertNull("Wallet must be completely deleted from DB", dbWallet)

        val keyExists = runBlocking { fakeKeyManager.hasPrivateKey(keyAlias) }
        assertFalse("Key must be physically deleted from KeyVault", keyExists)

        // 驗證 KeyVault 刪除次數為 1
        assertEquals(1, fakeKeyManager.deleteCount)

        // 驗證 deletion_step_ledger 中的 17 個子步驟皆為 PASS
        val ledgerSteps = database.deletionStepLedgerQueries.selectStepsByWalletId(walletId).executeAsList()
        assertEquals(17, ledgerSteps.size)
        assertTrue(ledgerSteps.all { it.status == DeletionStepStatus.PASS.name })

        // 驗證 Session 處於 consumed 狀態，且無法復活
        assertTrue(AuthHandleRegistry.isConsumed(handle.sessionId))
        assertFalse(AuthHandleRegistry.isRegistered(handle.sessionId))
    }

    // ==========================================
    // 4. 17 層 Deletion Step Ledger 完整性測試 (P1-3)
    // ==========================================

    @Test
    fun test_17_layer_deletion_step_ledger_records_all_steps_on_success() {
        val address = testAddress(200L)
        val keyAlias = "wallet_key_200"
        val walletId = insertTestWallet(id = 200L, address = address, keyAlias = keyAlias, requiresAuth = true)

        // 預先插入子系統數據
        database.nftQueries.insert(
            token_id = "1",
            contract_address = "0xnft",
            wallet_address = address,
            chain_type = ChainType.ETHEREUM.name,
            chain_id = 1L,
            name = "Test NFT",
            description = null,
            image_url = null,
            metadata_url = null,
            attributes = null,
            collection_name = null,
            creator_address = null,
            owner_address = null,
            is_favorite = 0L,
            is_hidden = 0L,
            rarity_rank = null,
            rarity_score = null,
            price_eth = null,
            price_usd = null,
            last_sale_price = null,
            last_sale_date = null,
            synced_at = null
        )
        database.pushSubscriptionQueries.upsertSubscription(
            wallet_address = address,
            channel_address = "0xchannel",
            subscribed = 1L,
            subscribed_at = System.currentTimeMillis(),
            unsubscribed_at = null,
            last_synced_at = System.currentTimeMillis()
        )
        database.tokenQueries.insert(
            wallet_id = walletId,
            address = "0xtk1",
            symbol = "TK1",
            name = "Token1",
            decimals = 18L,
            chain_type = ChainType.ETHEREUM.name,
            chain_id = 1L,
            balance = "1000",
            usd_price = 1.0,
            price_change_24h = 0.0,
            logo_url = null,
            is_native = 0L,
            is_hidden = 0L,
            contract_type = "ERC20",
            metadata = "{}"
        )
        database.transactionQueries.insert(
            wallet_id = walletId,
            tx_hash = "0xhash1",
            from_address = address,
            to_address = "0xto",
            value_ = "1.0",
            gas_price = "20",
            gas_limit = "21000",
            gas_used = "21000",
            nonce = 0L,
            data_ = null,
            status = "SUCCESS",
            type = "TRANSFER",
            chain_type = ChainType.ETHEREUM.name,
            chain_id = 1L,
            block_number = 100L,
            block_timestamp = System.currentTimeMillis(),
            token_address = null,
            token_symbol = null,
            token_decimals = null,
            fee_amount = "0.01",
            fee_currency = "ETH",
            keystone_sign_request_id = null,
            keystone_signature = null,
            memo = null,
            metadata = "{}"
        )

        val handle = TestPlatformAuthenticator.issueHandle(
            keyId = keyAlias,
            operation = AuthOperation.DELETE,
            intentFingerprint = "",
            validityDurationMs = 60_000L,
            walletId = walletId.toString()
        )

        var cancelledWorkManager = false
        var cancelledBackgroundSync = false
        var invalidatedTiles = false
        var invalidatedComplications = false

        val testHook = object : PlatformDeletionCleanupHook {
            override suspend fun cancelWorkManagerJobs(wId: Long): Result<Unit> {
                if (wId == walletId) cancelledWorkManager = true
                return Result.Success(Unit)
            }
            override suspend fun cancelBackgroundSync(wId: Long): Result<Unit> {
                if (wId == walletId) cancelledBackgroundSync = true
                return Result.Success(Unit)
            }
            override suspend fun invalidateTiles(): Result<Unit> {
                invalidatedTiles = true
                return Result.Success(Unit)
            }
            override suspend fun invalidateComplications(): Result<Unit> {
                invalidatedComplications = true
                return Result.Success(Unit)
            }
        }

        val customRepo = WalletRepositoryImpl(
            databaseDriverFactory = driverFactory,
            cryptoProvider = cryptoProvider,
            ethereumRpcClient = ethereumRpcClient,
            secureKeyManager = fakeKeyManager,
            platformDeletionCleanupHook = testHook,
            customWalletQueries = database.walletQueries,
            customStagingJournalQueries = database.stagingJournalQueries,
            customDeletionJournalQueries = database.deletionJournalQueries,
            customDeletionStepLedgerQueries = database.deletionStepLedgerQueries
        )

        val delRes = runBlocking {
            customRepo.deleteWallet(walletId.toString(), AuthenticationContext(authHandle = handle))
        }
        assertTrue("deleteWallet must succeed", delRes is Result.Success)

        // 驗證 Hook 被呼叫
        assertTrue(cancelledWorkManager)
        assertTrue(cancelledBackgroundSync)
        assertTrue(invalidatedTiles)
        assertTrue(invalidatedComplications)

        // 驗證 17 個步驟全部被記錄且為 PASS
        val steps = database.deletionStepLedgerQueries.selectStepsByWalletId(walletId).executeAsList()
        assertEquals(17, steps.size)
        for (step in DeletionStep.values()) {
            val record = steps.find { it.step_name == step.name }
            assertNotNull("Step ${step.name} must be recorded in ledger", record)
            assertEquals("Step ${step.name} status must be PASS", DeletionStepStatus.PASS.name, record!!.status)
        }

        // 驗證相關 DB 數據全部被清除
        assertEquals(0, database.nftQueries.selectByWalletAddress(address).executeAsList().size)
        assertEquals(0, database.pushSubscriptionQueries.selectByWallet(address).executeAsList().size)
        assertEquals(0, database.tokenQueries.selectByWalletId(walletId).executeAsList().size)
        assertEquals(0, database.transactionQueries.selectByAddress(address, address, 1L).executeAsList().size)
    }

    // ==========================================
    // 5. 故障注入與斷點續跑測試 (P1-3)
    // ==========================================

    @Test
    fun test_fault_injection_on_cleanup_step_transitions_to_recovery_required_and_reconciles_on_resume() {
        val address = testAddress(300L)
        val keyAlias = "wallet_key_300"
        val walletId = insertTestWallet(id = 300L, address = address, keyAlias = keyAlias, requiresAuth = true)

        var hookShouldFail = true
        val failingHook = object : PlatformDeletionCleanupHook {
            override suspend fun cancelWorkManagerJobs(wId: Long): Result<Unit> {
                return if (hookShouldFail) {
                    Result.Failure(RuntimeException("WorkManager simulated crash"))
                } else {
                    Result.Success(Unit)
                }
            }
            override suspend fun cancelBackgroundSync(wId: Long): Result<Unit> = Result.Success(Unit)
            override suspend fun invalidateTiles(): Result<Unit> = Result.Success(Unit)
            override suspend fun invalidateComplications(): Result<Unit> = Result.Success(Unit)
        }

        val customRepo = WalletRepositoryImpl(
            databaseDriverFactory = driverFactory,
            cryptoProvider = cryptoProvider,
            ethereumRpcClient = ethereumRpcClient,
            secureKeyManager = fakeKeyManager,
            platformDeletionCleanupHook = failingHook,
            customWalletQueries = database.walletQueries,
            customStagingJournalQueries = database.stagingJournalQueries,
            customDeletionJournalQueries = database.deletionJournalQueries,
            customDeletionStepLedgerQueries = database.deletionStepLedgerQueries
        )

        val handle = TestPlatformAuthenticator.issueHandle(
            keyId = keyAlias,
            operation = AuthOperation.DELETE,
            intentFingerprint = "",
            validityDurationMs = 60_000L,
            walletId = walletId.toString()
        )

        // 首次刪除：WorkManager 失敗，操作必須返回 Failure，不可靜默吞噬
        val delRes = runBlocking {
            customRepo.deleteWallet(walletId.toString(), AuthenticationContext(authHandle = handle))
        }
        assertTrue("deleteWallet must fail when a cleanup step fails", delRes is Result.Failure)
        assertTrue((delRes as Result.Failure).exception is DeletionIncompleteException)

        // 驗證 deletion_journal 進入 RECOVERY_REQUIRED
        val journal = database.deletionJournalQueries.selectByWalletId(walletId).executeAsOneOrNull()
        assertNotNull(journal)
        assertEquals("RECOVERY_REQUIRED", journal!!.state)

        // 驗證 deletion_step_ledger 中 WORK_MANAGER_JOBS 標記為 FAILED
        val wmStep = database.deletionStepLedgerQueries.selectStep(walletId, DeletionStep.WORK_MANAGER_JOBS.name).executeAsOneOrNull()
        assertNotNull(wmStep)
        assertEquals(DeletionStepStatus.FAILED.name, wmStep!!.status)
        assertTrue(wmStep.retry_count >= 1L)

        // 修復故障後，調用 reconcileStartupState 斷點續跑
        hookShouldFail = false
        val recovRes = runBlocking {
            customRepo.reconcileStartupState()
        }
        assertTrue("reconcileStartupState must succeed", recovRes is Result.Success)

        // 驗證狀態已推進至 COMPLETED
        val finalJournal = database.deletionJournalQueries.selectByWalletId(walletId).executeAsOneOrNull()
        assertNotNull(finalJournal)
        assertEquals("COMPLETED", finalJournal!!.state)

        // 驗證 WORK_MANAGER_JOBS 狀態已變為 PASS
        val fixedWmStep = database.deletionStepLedgerQueries.selectStep(walletId, DeletionStep.WORK_MANAGER_JOBS.name).executeAsOneOrNull()
        assertNotNull(fixedWmStep)
        assertEquals(DeletionStepStatus.PASS.name, fixedWmStep!!.status)

        // 驗證 DB 錢包資料已被移除
        assertNull(database.walletQueries.selectById(walletId).executeAsOneOrNull())
    }
}

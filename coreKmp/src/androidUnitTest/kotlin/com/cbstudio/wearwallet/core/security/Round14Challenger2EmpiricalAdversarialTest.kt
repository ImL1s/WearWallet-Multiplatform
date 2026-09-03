package com.cbstudio.wearwallet.core.security

import android.content.Context
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.database.CoreWalletDatabase
import io.github.iml1s.crypto.Secp256k1Pure
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
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
 * PR #32 Round 14 Challenger 2 Empirical Adversarial Test Suite
 *
 * Verification Focus:
 * 1. P1 Cross-Wallet Authentication Attacks:
 *    - Issue Handle for Wallet A -> attempt operation on Wallet B -> strict rejection.
 *    - Tamper walletId field in Handle -> HMAC validation failure.
 *    - Attempt register/sign with blank walletId -> strict failure.
 *    - 50 concurrent threads cross-wallet attack -> strict isolation.
 * 2. P1 PriceAlert Multi-Wallet Isolation:
 *    - Identical asset symbol (ETH) for Wallet A and Wallet B.
 *    - Delete cleanup on Wallet A -> Wallet A deleted (count == 0), Wallet B untouched (count == 1).
 */
class Round14Challenger2EmpiricalAdversarialTest {

    private val testPrivKeyHexA = "e331b6d69882b4cb4ea581d88ec2693329b3b3a4711832421415ebff2d474428"
    private val testPrivKeyHexB = "4c0883a69102937d6231471b5dbb6204db7e716b78ac387728b80b7b1340a69f"
    private lateinit var mockContext: Context
    private lateinit var backend: TestKeyStoreBackend
    private lateinit var sharedPrefs: InMemorySharedPreferences
    private lateinit var keyManager: AndroidSecureKeyManager

    @Before
    fun setUp() {
        AuthHandleRegistry.clearForTesting()
        DeletionGrantRegistry.clearForTesting()
        RecoveryGrantRegistry.clearForTesting()

        mockContext = mock<Context>()
        whenever(mockContext.applicationContext).thenReturn(mockContext)

        backend = TestKeyStoreBackend()
        sharedPrefs = InMemorySharedPreferences()

        keyManager = AndroidSecureKeyManager(
            context = mockContext,
            config = SecureStorageConfig(enableRootDetection = false),
            keyStoreProvider = { backend.createKeyStore() },
            encryptedPrefsProvider = { sharedPrefs },
            secretKeyProvider = { alias, _ -> backend.generateAndStoreKey(alias) }
        )
    }

    @After
    fun tearDown() {
        AuthHandleRegistry.clearForTesting()
        DeletionGrantRegistry.clearForTesting()
        RecoveryGrantRegistry.clearForTesting()
    }

    // =========================================================================
    // 1. P1 Cross-Wallet Authentication Attacks
    // =========================================================================

    @Test
    fun test1_crossWalletAuthentication_handleForWalletA_rejectedOnWalletB() = runTest {
        val now = Clock.System.now().toEpochMilliseconds()
        val walletA = "wallet-uuid-AAA"
        val walletB = "wallet-uuid-BBB"
        val keyA = "key-alias-AAA"
        val keyB = "key-alias-BBB"

        val authA = AuthenticationContext(authHandle = TestPlatformAuthenticator.issueHandle(keyA, AuthOperation.IMPORT, walletId = walletA))
        val authB = AuthenticationContext(authHandle = TestPlatformAuthenticator.issueHandle(keyB, AuthOperation.IMPORT, walletId = walletB))

        keyManager.storePrivateKey(keyA, testPrivKeyHexA.encodeToByteArray(), requireAuth = true, authContext = authA, expectedWalletId = walletA)
        keyManager.storePrivateKey(keyB, testPrivKeyHexB.encodeToByteArray(), requireAuth = true, authContext = authB, expectedWalletId = walletB)

        val txData = "Transfer 50 USDC".encodeToByteArray()
        val txDigest = CryptoUtils.sha256(txData).toHexString()

        val handleA = TestPlatformAuthenticator.issueHandle(
            keyId = keyA,
            operation = AuthOperation.SIGN,
            intentFingerprint = txDigest,
            expiresAtMs = now + 60_000L,
            walletId = walletA
        )

        // 1. Attempt to sign Key B using Handle A
        val signCrossResult = keyManager.signWithKey(keyB, txData, authContext = AuthenticationContext(authHandle = handleA), expectedWalletId = walletB)
        assertTrue("Cross-wallet/cross-key sign MUST fail", signCrossResult is Result.Failure)
        val exSign = (signCrossResult as Result.Failure).exception
        assertTrue("Exception must be AuthenticationRequiredException, got ", exSign is AuthenticationRequiredException)

        // 2. Direct AuthHandleRegistry.validateAndConsume with expectedWalletId = walletB
        val consumeCrossResult = AuthHandleRegistry.validateAndConsume(
            handle = handleA,
            expectedKeyId = keyA,
            expectedOperation = AuthOperation.SIGN,
            expectedFingerprint = txDigest,
            currentTimeMs = handleA.issuedAtMs,
            expectedWalletId = walletB
        )
        assertTrue("validateAndConsume on cross-wallet MUST fail", consumeCrossResult is Result.Failure)
        val exConsume = (consumeCrossResult as Result.Failure).exception
        assertTrue(exConsume is AuthenticationRequiredException)
        assertTrue("Message must mention cross-wallet rejection", exConsume.message!!.contains("Cross-wallet"))

        // 3. Direct AuthHandleRegistry.validateConsumeAndIssueGrant with walletId = walletB
        val deleteHandleA = TestPlatformAuthenticator.issueHandle(
            keyId = keyA,
            operation = AuthOperation.DELETE,
            expiresAtMs = now + 60_000L,
            walletId = walletA
        )
        val grantCrossResult = AuthHandleRegistry.validateConsumeAndIssueGrant(
            handle = deleteHandleA,
            walletId = walletB,
            expectedKeyId = keyA,
            currentTimeMs = deleteHandleA.issuedAtMs
        )
        assertTrue("validateConsumeAndIssueGrant on cross-wallet MUST fail", grantCrossResult is Result.Failure)
        val exGrant = (grantCrossResult as Result.Failure).exception
        assertTrue(exGrant is AuthenticationRequiredException)
        assertTrue("Message must mention cross-wallet rejection", exGrant.message!!.contains("Cross-wallet"))
    }

    @Test
    fun test2_tamperedWalletIdInHandle_failsHmacVerification() {
        val now = Clock.System.now().toEpochMilliseconds()
        val genuineWalletId = "wallet-legitimate-001"
        val attackerWalletId = "wallet-attacker-999"
        val keyId = "key-sec-test"
        val digest = "deadbeef12345678deadbeef12345678deadbeef12345678deadbeef12345678"

        val genuineHandle = TestPlatformAuthenticator.issueHandle(
            keyId = keyId,
            operation = AuthOperation.SIGN,
            intentFingerprint = digest,
            expiresAtMs = now + 60_000L,
            walletId = genuineWalletId
        )

        // 1. Genuine handle verifies successfully
        assertTrue(
            "Genuine handle must pass isValid()",
            genuineHandle.isValid(
                expectedKeyId = keyId,
                expectedIntentFingerprint = digest,
                expectedOperation = AuthOperation.SIGN,
                currentTimeMs = now,
                expectedWalletId = genuineWalletId
            )
        )

        // 2. Tamper the walletId property of the PlatformAuthHandle
        val tamperedHandle = PlatformAuthHandle(
            keyId = genuineHandle.keyId,
            operation = genuineHandle.operation,
            intentFingerprint = genuineHandle.intentFingerprint,
            sessionId = genuineHandle.sessionId,
            nonce = genuineHandle.nonce,
            issuedAtMs = genuineHandle.issuedAtMs,
            expiresAtMs = genuineHandle.expiresAtMs,
            proofToken = genuineHandle.proofToken, // Token was HMACed with genuineWalletId
            walletId = attackerWalletId           // Tampered walletId
        )

        // 3. Verification must fail HMAC check
        assertFalse(
            "Tampered walletId MUST fail ProofTokenVerifier.verify()",
            ProofTokenVerifier.verify(
                proofToken = tamperedHandle.proofToken,
                keyId = tamperedHandle.keyId,
                operation = tamperedHandle.operation,
                intentFingerprint = tamperedHandle.intentFingerprint,
                sessionId = tamperedHandle.sessionId,
                nonce = tamperedHandle.nonce,
                issuedAtMs = tamperedHandle.issuedAtMs,
                expiresAtMs = tamperedHandle.expiresAtMs,
                walletId = tamperedHandle.walletId
            )
        )

        assertFalse(
            "Tampered handle MUST fail isValid()",
            tamperedHandle.isValid(
                expectedKeyId = keyId,
                expectedIntentFingerprint = digest,
                expectedOperation = AuthOperation.SIGN,
                currentTimeMs = now,
                expectedWalletId = attackerWalletId
            )
        )

        // 4. Registry consumption of tampered handle must fail closed
        val consumeResult = AuthHandleRegistry.validateAndConsume(
            handle = tamperedHandle,
            expectedKeyId = keyId,
            expectedOperation = AuthOperation.SIGN,
            expectedFingerprint = digest,
            currentTimeMs = now,
            expectedWalletId = attackerWalletId
        )
        assertTrue("validateAndConsume on tampered handle MUST fail", consumeResult is Result.Failure)
    }

    @Test
    fun test3_blankWalletId_strictlyRejectedAtAllLayers() {
        val now = Clock.System.now().toEpochMilliseconds()

        // 1. PlatformAuthHandle constructor must throw on blank walletId
        try {
            PlatformAuthHandle(
                keyId = "key_test",
                operation = AuthOperation.SIGN,
                intentFingerprint = "fp",
                sessionId = "s1",
                nonce = "n1",
                issuedAtMs = now,
                expiresAtMs = now + 1000,
                proofToken = "tok",
                walletId = ""
            )
            fail("PlatformAuthHandle constructor must throw IllegalArgumentException for blank walletId")
        } catch (e: IllegalArgumentException) {
            assertTrue("Message must cite blank walletId", e.message!!.contains("walletId must not be blank"))
        }

        try {
            PlatformAuthHandle(
                keyId = "key_test",
                operation = AuthOperation.SIGN,
                intentFingerprint = "fp",
                sessionId = "s1",
                nonce = "n1",
                issuedAtMs = now,
                expiresAtMs = now + 1000,
                proofToken = "tok",
                walletId = "   "
            )
            fail("PlatformAuthHandle constructor must throw IllegalArgumentException for whitespace walletId")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("walletId must not be blank"))
        }

        // 2. ProofTokenVerifier.sign with blank walletId returns 'invalid_token'
        val invalidToken = ProofTokenVerifier.sign(
            keyId = "key_test",
            operation = AuthOperation.SIGN,
            intentFingerprint = "fp",
            sessionId = "s1",
            nonce = "n1",
            issuedAtMs = now,
            expiresAtMs = now + 1000,
            walletId = ""
        )
        assertEquals("invalid_token", invalidToken)

        // 3. ProofTokenVerifier.verify with blank walletId returns false
        assertFalse(
            ProofTokenVerifier.verify(
                proofToken = "some_token",
                keyId = "key_test",
                operation = AuthOperation.SIGN,
                intentFingerprint = "fp",
                sessionId = "s1",
                nonce = "n1",
                issuedAtMs = now,
                expiresAtMs = now + 1000,
                walletId = ""
            )
        )

        // 4. validateConsumeAndIssueGrant with blank walletId returns failure
        val dummyHandle = TestPlatformAuthenticator.issueHandle("key_test", AuthOperation.DELETE, walletId = "valid_wallet")
        val blankGrantResult = AuthHandleRegistry.validateConsumeAndIssueGrant(
            handle = dummyHandle,
            walletId = "",
            expectedKeyId = "key_test",
            currentTimeMs = now
        )
        assertTrue(blankGrantResult is Result.Failure)
        assertTrue((blankGrantResult as Result.Failure).exception is IllegalArgumentException)
    }

    @Test
    fun test4_50ThreadConcurrent_crossWalletIsolationAttack() {
        val now = Clock.System.now().toEpochMilliseconds()
        val walletA = "wallet-target-A"
        val walletB = "wallet-attacker-B"
        val keyA = "key-target-A"
        val keyB = "key-attacker-B"

        val rawPrivKeyA = ByteArray(32) { i -> testPrivKeyHexA.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
        val rawPrivKeyB = ByteArray(32) { i -> testPrivKeyHexB.substring(i * 2, i * 2 + 2).toInt(16).toByte() }

        runBlocking {
            keyManager.storePrivateKey(keyA, rawPrivKeyA, requireAuth = true, authContext = AuthenticationContext(authHandle = TestPlatformAuthenticator.issueHandle(keyA, AuthOperation.IMPORT, walletId = walletA)), expectedWalletId = walletA)
            keyManager.storePrivateKey(keyB, rawPrivKeyB, requireAuth = true, authContext = AuthenticationContext(authHandle = TestPlatformAuthenticator.issueHandle(keyB, AuthOperation.IMPORT, walletId = walletB)), expectedWalletId = walletB)
        }

        val txData = CryptoUtils.sha256("Tx-Cross-Wallet-Attack".encodeToByteArray())
        val txDigest = txData.toHexString()

        // Handle legitimately issued for Wallet A
        val handleA = TestPlatformAuthenticator.issueHandle(
            keyId = keyA,
            operation = AuthOperation.SIGN,
            intentFingerprint = txDigest,
            walletId = walletA,
            validityDurationMs = 30_000L
        )

        val threadCount = 50
        val executor = Executors.newFixedThreadPool(threadCount)
        val startLatch = CountDownLatch(1)
        val finishLatch = CountDownLatch(threadCount)

        val results = ConcurrentLinkedQueue<Result<ByteArray>>()
        val successCount = AtomicInteger(0)
        val failureCount = AtomicInteger(0)

        // 50 threads attempt to use Handle A to sign Key B (Wallet B)
        for (i in 0 until threadCount) {
            executor.submit {
                try {
                    startLatch.await()
                    val res = runBlocking {
                        keyManager.signWithKey(
                            keyId = keyB,
                            data = txData,
                            authContext = AuthenticationContext(authHandle = handleA),
                            expectedWalletId = walletB
                        )
                    }
                    results.add(res)
                    when (res) {
                        is Result.Success -> successCount.incrementAndGet()
                        is Result.Failure -> failureCount.incrementAndGet()
                        else -> {}
                    }
                } finally {
                    finishLatch.countDown()
                }
            }
        }

        startLatch.countDown()
        val finished = finishLatch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        assertTrue("All threads must finish within timeout", finished)
        assertEquals("Total results collected must be 50", 50, results.size)
        assertEquals("0 threads must succeed on cross-wallet attack", 0, successCount.get())
        assertEquals("All 50 threads must be strictly rejected", 50, failureCount.get())

        // Ensure Handle A was not consumed by the failed attacks and can still be used legitimately on Wallet A
        val legitimateSign = runBlocking {
            keyManager.signWithKey(
                keyId = keyA,
                data = txData,
                authContext = AuthenticationContext(authHandle = handleA),
                expectedWalletId = walletA
            )
        }
        assertTrue("Legitimate single-use sign on Wallet A must succeed", legitimateSign is Result.Success)
        assertEquals(65, (legitimateSign as Result.Success).data.size)
    }

    // =========================================================================
    // 2. P1 PriceAlert Multi-Wallet Isolation
    // =========================================================================

    @Test
    fun test6_priceAlert_multiWalletIsolation_identicalAssetSymbol() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CoreWalletDatabase.Schema.create(driver)
        val database = CoreWalletDatabase(driver)

        val walletIdA = "wallet-alpha-111"
        val walletIdB = "wallet-beta-222"

        // Insert alert for Wallet A with symbol "ETH", target 3000.0
        database.priceAlertQueries.insert(
            wallet_id = walletIdA,
            asset_symbol = "ETH",
            asset_name = "Ethereum",
            contract_address = null,
            chain_type = "ETHEREUM",
            chain_id = 1L,
            alert_type = "ABOVE",
            target_price = 3000.0,
            current_price = 2500.0,
            percentage_threshold = null,
            is_enabled = 1L,
            user_notes = "Alpha ETH alert",
            webhook_url = null,
            repeat_interval = 0L
        )

        // Insert alert for Wallet B with identical symbol "ETH", target 3500.0
        database.priceAlertQueries.insert(
            wallet_id = walletIdB,
            asset_symbol = "ETH",
            asset_name = "Ethereum",
            contract_address = null,
            chain_type = "ETHEREUM",
            chain_id = 1L,
            alert_type = "ABOVE",
            target_price = 3500.0,
            current_price = 2500.0,
            percentage_threshold = null,
            is_enabled = 1L,
            user_notes = "Beta ETH alert",
            webhook_url = null,
            repeat_interval = 0L
        )

        // Pre-condition verification: Both wallets have exactly 1 alert
        assertEquals(1L, database.priceAlertQueries.countByWalletId(walletIdA).executeAsOne())
        assertEquals(1L, database.priceAlertQueries.countByWalletId(walletIdB).executeAsOne())
        assertEquals(2L, database.priceAlertQueries.countAll().executeAsOne())

        // Trigger deletion cleanup specifically for Wallet A
        database.priceAlertQueries.deleteByWalletId(walletIdA)

        // Post-condition verification:
        // 1. Wallet A alerts deleted (count == 0)
        assertEquals(
            "Wallet A price alerts must be completely deleted",
            0L,
            database.priceAlertQueries.countByWalletId(walletIdA).executeAsOne()
        )

        // 2. Wallet B alerts remain untouched (count == 1)
        assertEquals(
            "Wallet B price alerts must remain intact",
            1L,
            database.priceAlertQueries.countByWalletId(walletIdB).executeAsOne()
        )

        // 3. Querying Wallet B yields its genuine ETH alert
        val betaAlerts = database.priceAlertQueries.selectByWalletId(walletIdB).executeAsList()
        assertEquals(1, betaAlerts.size)
        val betaAlert = betaAlerts.first()
        assertEquals(walletIdB, betaAlert.wallet_id)
        assertEquals("ETH", betaAlert.asset_symbol)
        assertEquals(3500.0, betaAlert.target_price, 0.0001)
        assertEquals("Beta ETH alert", betaAlert.user_notes)
    }
}

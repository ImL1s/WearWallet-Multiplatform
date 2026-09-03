package com.cbstudio.wearwallet.core.security

import android.content.Context
import com.cbstudio.wearwallet.core.common.Result
import io.github.iml1s.crypto.Secp256k1Pure
import kotlinx.coroutines.runBlocking
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
 * P1-3 & P1-4: 50-Thread Concurrent Cross-Wallet Isolation & KeyPresence 4-State Consistency Stress Test
 *
 * 驗證：
 * 1. 50 執行緒併發跨錢包隔離測試：
 *    - 即使 keyAlias 與 intentFingerprint 100% 相同，只要 expectedWalletId 與 AuthHandle/Grant.walletId 不匹配，
 *      50 個執行緒 100% 遭到拒絕，0 洩漏、0 成功。
 * 2. 5-Tuple KeyStore/Prefs 完整性四態 (Present, Absent, Partial, Unavailable) 判定。
 */
class CrossWalletIsolation50ThreadConcurrentTest {

    private val testPrivateKeyHex = "e331b6d69882b4cb4ea581d88ec2693329b3b3a4711832421415ebff2d474428"
    private lateinit var mockContext: Context
    private lateinit var backend: TestKeyStoreBackend
    private lateinit var sharedPrefs: InMemorySharedPreferences
    private lateinit var keyManager: AndroidSecureKeyManager

    @Before
    fun setUp() {
        AuthHandleRegistry.clearForTesting()
        DeletionGrantRegistry.clearForTesting()
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

    @Test
    fun test_50_threads_concurrent_cross_wallet_signing_mismatch_100_percent_rejected() {
        val keyId = "key_cross_wallet_50"
        val victimWalletId = "wallet_victim_1001"
        val attackerWalletId = "wallet_attacker_9999"

        val rawPrivKey = ByteArray(32) { i ->
            testPrivateKeyHex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }

        // 1. Store key bound to victim wallet
        val importHandle = TestPlatformAuthenticator.issueHandle(
            keyId = keyId,
            operation = AuthOperation.IMPORT,
            intentFingerprint = "",
            validityDurationMs = 60_000L,
            walletId = victimWalletId
        )
        val storeResult = runBlocking {
            keyManager.storePrivateKey(
                keyId = keyId,
                privateKey = rawPrivKey,
                requireAuth = true,
                authContext = AuthenticationContext(authHandle = importHandle),
                expectedWalletId = victimWalletId
            )
        }
        assertTrue("Store key must succeed", storeResult is Result.Success)

        // 2. Prepare signing intent
        val dataToSign = CryptoUtils.sha256("Tx-Cross-Wallet-Isolation-Payload".encodeToByteArray())
        val digestHex = dataToSign.toHexString()

        // 3. Issue 50 handles for victim wallet
        val threadCount = 50
        val handles = (0 until threadCount).map {
            TestPlatformAuthenticator.issueHandle(
                keyId = keyId,
                operation = AuthOperation.SIGN,
                intentFingerprint = digestHex,
                validityDurationMs = 60_000L,
                walletId = victimWalletId
            )
        }

        val executor = Executors.newFixedThreadPool(threadCount)
        val startGun = CountDownLatch(1)
        val finishLatch = CountDownLatch(threadCount)

        val rejectedCount = AtomicInteger(0)
        val successCount = AtomicInteger(0)
        val failureExceptions = ConcurrentLinkedQueue<Throwable>()

        for (i in 0 until threadCount) {
            val handle = handles[i]
            executor.submit {
                try {
                    startGun.await()

                    // Attacker thread attempts to sign by asserting expectedWalletId = attackerWalletId
                    val result = runBlocking {
                        keyManager.signWithKey(
                            keyId = keyId,
                            data = dataToSign,
                            authContext = AuthenticationContext(authHandle = handle),
                            expectedWalletId = attackerWalletId
                        )
                    }

                    when (result) {
                        is Result.Success -> successCount.incrementAndGet()
                        is Result.Failure -> {
                            rejectedCount.incrementAndGet()
                            failureExceptions.add(result.exception)
                        }
                        is Result.Loading -> {}
                    }
                } catch (t: Throwable) {
                    rejectedCount.incrementAndGet()
                    failureExceptions.add(t)
                } finally {
                    finishLatch.countDown()
                }
            }
        }

        startGun.countDown()
        val finishedInTime = finishLatch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        assertTrue("All 50 threads must complete within timeout", finishedInTime)
        assertEquals("0 threads must succeed in cross-wallet signing", 0, successCount.get())
        assertEquals("All 50 threads must be rejected with 100% failure rate", 50, rejectedCount.get())

        // Verify all exceptions are AuthenticationRequiredException with cross-wallet message
        for (ex in failureExceptions) {
            assertTrue(
                "Exception must be AuthenticationRequiredException or SecurityException, got ${ex::class.simpleName}: ${ex.message}",
                ex is AuthenticationRequiredException || ex is SecurityException
            )
            assertTrue(
                "Message must mention cross-wallet rejection: '${ex.message}'",
                ex.message!!.contains("Cross-wallet") || ex.message!!.contains("walletId") || ex.message!!.contains("wallet")
            )
        }
    }

    @Test
    fun test_50_threads_concurrent_cross_wallet_deletion_grant_mismatch_100_percent_rejected() {
        val keyId = "key_cross_wallet_delete_50"
        val victimWalletId = "wallet_victim_2002"
        val attackerWalletId = "wallet_attacker_8888"

        val rawPrivKey = ByteArray(32) { i ->
            testPrivateKeyHex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }

        // Store key
        val importHandle = TestPlatformAuthenticator.issueHandle(
            keyId = keyId,
            operation = AuthOperation.IMPORT,
            intentFingerprint = "",
            validityDurationMs = 60_000L,
            walletId = victimWalletId
        )
        runBlocking {
            keyManager.storePrivateKey(
                keyId = keyId,
                privateKey = rawPrivKey,
                requireAuth = true,
                authContext = AuthenticationContext(authHandle = importHandle),
                expectedWalletId = victimWalletId
            )
        }

        // Issue 50 deletion grants for victim wallet
        val threadCount = 50
        val grants = (0 until threadCount).map {
            val deleteHandle = TestPlatformAuthenticator.issueHandle(
                keyId = keyId,
                operation = AuthOperation.DELETE,
                intentFingerprint = "",
                validityDurationMs = 60_000L,
                walletId = victimWalletId
            )
            val grantRes = DeletionAuthorizationService.issueDeletionGrant(
                handle = deleteHandle,
                walletId = victimWalletId,
                keyAlias = keyId,
                currentTimeMs = System.currentTimeMillis()
            )
            assertTrue("Grant issuance must succeed", grantRes is Result.Success)
            (grantRes as Result.Success).data
        }

        val executor = Executors.newFixedThreadPool(threadCount)
        val startGun = CountDownLatch(1)
        val finishLatch = CountDownLatch(threadCount)

        val rejectedCount = AtomicInteger(0)
        val successCount = AtomicInteger(0)

        for (i in 0 until threadCount) {
            val grant = grants[i]
            executor.submit {
                try {
                    startGun.await()

                    // Attempt deletion with mismatched expectedWalletId
                    val result = runBlocking {
                        keyManager.deletePrivateKeyWithGrant(
                            grant = grant,
                            expectedWalletId = attackerWalletId
                        )
                    }

                    when (result) {
                        is Result.Success -> successCount.incrementAndGet()
                        is Result.Failure -> rejectedCount.incrementAndGet()
                        is Result.Loading -> {}
                    }
                } catch (t: Throwable) {
                    rejectedCount.incrementAndGet()
                } finally {
                    finishLatch.countDown()
                }
            }
        }

        startGun.countDown()
        val finishedInTime = finishLatch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        assertTrue("All 50 threads must complete within timeout", finishedInTime)
        assertEquals("0 threads must succeed in cross-wallet deletion", 0, successCount.get())
        assertEquals("All 50 threads must be rejected", 50, rejectedCount.get())

        // Key must still be present in vault!
        val presence = runBlocking { keyManager.checkKeyPresence(keyId) }
        assertTrue("Victim key must remain Present in vault", presence is KeyPresence.Present)
    }

    @Test
    fun test_5_tuple_key_presence_consistency_all_four_states() {
        val keyId = "key_5tuple_consistency_test"
        val walletId = "wallet_test_5tuple"

        // 1. Initial State: Absent (0 of 5 tuples present)
        val initialPresence = runBlocking { keyManager.checkKeyPresence(keyId) }
        assertTrue("Initially key must be Absent", initialPresence is KeyPresence.Absent)

        // 2. Store Key: Present (All 5 of 5 tuples present)
        val rawPrivKey = ByteArray(32) { i ->
            testPrivateKeyHex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        val importHandle = TestPlatformAuthenticator.issueHandle(
            keyId = keyId,
            operation = AuthOperation.IMPORT,
            intentFingerprint = "",
            validityDurationMs = 60_000L,
            walletId = walletId
        )
        val storeRes = runBlocking {
            keyManager.storePrivateKey(
                keyId = keyId,
                privateKey = rawPrivKey,
                requireAuth = true,
                authContext = AuthenticationContext(authHandle = importHandle),
                expectedWalletId = walletId
            )
        }
        assertTrue("Store must succeed", storeRes is Result.Success)

        val fullPresence = runBlocking { keyManager.checkKeyPresence(keyId) }
        assertTrue("Stored key must be Present", fullPresence is KeyPresence.Present)

        // 3. Corrupt 1 tuple: Remove IV suffix from sharedPrefs (4 of 5 tuples present -> Partial)
        sharedPrefs.edit().remove(keyId + "_iv").commit()
        val partialPresenceIv = runBlocking { keyManager.checkKeyPresence(keyId) }
        assertTrue("Missing IV must produce KeyPresence.Partial, got $partialPresenceIv", partialPresenceIv is KeyPresence.Partial)
        val partialDetails = (partialPresenceIv as KeyPresence.Partial).details
        assertTrue("Details must explain tuple status", partialDetails.contains("hasKeyStoreKey=true") && partialDetails.contains("hasIv=false"))

        // 4. Corrupt another tuple: Remove TAG suffix (3 of 5 tuples present -> Partial)
        sharedPrefs.edit().remove(keyId + "_tag").commit()
        val partialPresenceTag = runBlocking { keyManager.checkKeyPresence(keyId) }
        assertTrue("Missing tag must produce KeyPresence.Partial", partialPresenceTag is KeyPresence.Partial)

        // 5. Corrupt ciphertext: Remove keyId (2 of 5 tuples present -> Partial)
        sharedPrefs.edit().remove(keyId).commit()
        val partialPresenceCipher = runBlocking { keyManager.checkKeyPresence(keyId) }
        assertTrue("Missing ciphertext must produce KeyPresence.Partial", partialPresenceCipher is KeyPresence.Partial)

        // 6. Corrupt requireAuth: Remove requireAuth (1 of 5 tuples present -> Partial)
        sharedPrefs.edit().remove(keyId + "_require_auth").commit()
        val partialPresenceAuth = runBlocking { keyManager.checkKeyPresence(keyId) }
        assertTrue("Only KeyStore alias present must produce KeyPresence.Partial", partialPresenceAuth is KeyPresence.Partial)

        // 7. Remove KeyStore key: (0 of 5 tuples present -> Absent)
        backend.entries.remove("wallet_key_$keyId")
        backend.entries.clear()
        val finalAbsent = runBlocking { keyManager.checkKeyPresence(keyId) }
        assertTrue("All 0 tuples present must produce KeyPresence.Absent", finalAbsent is KeyPresence.Absent)
    }

    @Test
    fun test_keystore_exception_produces_unavailable_state() {
        val brokenKeyManager = AndroidSecureKeyManager(
            context = mockContext,
            config = SecureStorageConfig(enableRootDetection = false),
            keyStoreProvider = { throw RuntimeException("Keystore daemon IPC failure / hardware deadlock") },
            encryptedPrefsProvider = { sharedPrefs },
            secretKeyProvider = { alias, _ -> backend.generateAndStoreKey(alias) }
        )

        val presence = runBlocking { brokenKeyManager.checkKeyPresence("any_key") }
        assertTrue("Keystore exception must produce KeyPresence.Unavailable, got $presence", presence is KeyPresence.Unavailable)
        val cause = (presence as KeyPresence.Unavailable).cause
        assertTrue("Cause message must contain IPC failure", cause.message!!.contains("Keystore daemon IPC failure"))
    }
}

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
 * 50 執行緒高併發原子驗證與單次消費壓力測試 (P1-1: 50-Thread Concurrent Stress Test)
 * 驗證 AuthHandleRegistry.validateAndConsume 的原子性：
 * 當 50 個執行緒同時使用同一個 PlatformAuthHandle 請求解密/簽名時，
 * 嚴格保證僅有恰好 1 個執行緒成功消費並解密/簽名，其餘 49 個執行緒必須收到 AuthenticationRequiredException 失敗拒絕。
 * 徹底杜絕 TOCTOU 多重解密與重放競態漏洞。
 */
class AuthHandle50ThreadConcurrentStressTest {

    private val testPrivateKeyHex = "e331b6d69882b4cb4ea581d88ec2693329b3b3a4711832421415ebff2d474428"
    private lateinit var mockContext: Context
    private lateinit var backend: TestKeyStoreBackend
    private lateinit var sharedPrefs: InMemorySharedPreferences
    private lateinit var keyManager: AndroidSecureKeyManager

    @Before
    fun setUp() {
        AuthHandleRegistry.clearForTesting()
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
    fun test_50_threads_concurrent_signing_with_single_handle_results_in_exactly_1_success_and_49_failures() {
        val keyId = "key_concurrent_stress_50"
        val rawPrivKey = ByteArray(32) { i ->
            testPrivateKeyHex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }

        // 1. 存儲需要生物識別認證的金鑰
        val importHandle = TestPlatformAuthenticator.issueHandle(
            keyId = keyId,
            operation = AuthOperation.IMPORT,
            intentFingerprint = "",
            validityDurationMs = 60_000L,
            walletId = keyId
        )
        val storeResult = runBlocking {
            keyManager.storePrivateKey(
                keyId = keyId,
                privateKey = rawPrivKey,
                requireAuth = true,
                authContext = AuthenticationContext(authHandle = importHandle),
                expectedWalletId = keyId
            )
        }
        assertTrue("Store key with import auth handle must succeed", storeResult is Result.Success)

        // 2. 準備待簽名數據與單一合法簽名 AuthHandle
        val dataToSign = CryptoUtils.sha256("WearWallet-Concurrent-Transaction-Payload-50".encodeToByteArray())
        val signHandle = TestPlatformAuthenticator.issueHandle(
            keyId = keyId,
            operation = AuthOperation.SIGN,
            intentFingerprint = dataToSign.toHexString(),
            validityDurationMs = 30_000L,
            walletId = keyId
        )

        // 3. 建立 50 執行緒併發環境
        val threadCount = 50
        val executor = Executors.newFixedThreadPool(threadCount)
        val startGun = CountDownLatch(1)
        val finishLatch = CountDownLatch(threadCount)

        val results = ConcurrentLinkedQueue<Result<ByteArray>>()
        val successCount = AtomicInteger(0)
        val authFailureCount = AtomicInteger(0)
        val otherFailureCount = AtomicInteger(0)

        for (i in 0 until threadCount) {
            executor.submit {
                try {
                    // 等待發令槍響，確保 50 執行緒同時出發
                    startGun.await()

                    val result = runBlocking {
                        keyManager.signWithKey(
                            keyId = keyId,
                            data = dataToSign,
                            authContext = AuthenticationContext(authHandle = signHandle),
                            expectedWalletId = keyId
                        )
                    }

                    results.add(result)
                    when (result) {
                        is Result.Success -> {
                            successCount.incrementAndGet()
                        }
                        is Result.Failure -> {
                            val ex = result.exception
                            if (ex is AuthenticationRequiredException || ex.cause is AuthenticationRequiredException) {
                                authFailureCount.incrementAndGet()
                            } else {
                                otherFailureCount.incrementAndGet()
                            }
                        }
                        else -> {}
                    }
                } finally {
                    finishLatch.countDown()
                }
            }
        }

        // 發令槍響
        startGun.countDown()

        // 等待所有執行緒執行完畢
        val finishedInTime = finishLatch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        assertTrue("All 50 threads should finish within timeout", finishedInTime)
        assertEquals("Total recorded results must be 50", 50, results.size)

        // 4. 嚴格斷言：恰好 1 次成功，恰好 49 次被拒絕 (AuthenticationRequiredException)
        assertEquals(
            "Exactly 1 thread must successfully consume handle and sign",
            1,
            successCount.get()
        )
        assertEquals(
            "Exactly 49 threads must fail with AuthenticationRequiredException",
            49,
            authFailureCount.get()
        )
        assertEquals(
            "No unexpected errors should occur",
            0,
            otherFailureCount.get()
        )

        // 5. 驗證成功產生的簽名確實是合法的 secp256k1 簽名
        val successResult = results.first { it is Result.Success } as Result.Success<ByteArray>
        val sigBytes = successResult.data
        assertEquals("Signature bytes must be 65 bytes (r:32 + s:32 + v:1)", 65, sigBytes.size)

        val r = sigBytes.copyOfRange(0, 32)
        val s = sigBytes.copyOfRange(32, 64)
        val v = sigBytes[64].toInt() and 0xFF

        val rBig = Secp256k1Pure.BigInteger.fromByteArray(r)
        val sBig = Secp256k1Pure.BigInteger.fromByteArray(s)
        val zBig = Secp256k1Pure.BigInteger.fromByteArray(dataToSign)

        val recoveredPoint = Secp256k1Pure.recoverPublicKeyPoint(zBig, rBig, sBig, v)
        assertNotNull("Recovered public key point must not be null", recoveredPoint)

        val expectedPubPoint = Secp256k1Pure.generatePublicKeyPoint(rawPrivKey)
        assertEquals(
            "Recovered public key point must match private key's public key point",
            expectedPubPoint,
            recoveredPoint
        )

        // 6. 再次嘗試使用已被消費的 handle 簽名，保證即時被拒
        val replayResult = runBlocking {
            keyManager.signWithKey(
                keyId = keyId,
                data = dataToSign,
                authContext = AuthenticationContext(authHandle = signHandle),
                expectedWalletId = keyId
            )
        }
        assertTrue("Subsequent replay must fail", replayResult is Result.Failure)
        assertTrue(
            "Replay exception must be AuthenticationRequiredException",
            (replayResult as Result.Failure).exception is AuthenticationRequiredException
        )
    }

    @Test
    fun test_50_threads_concurrent_deletion_with_single_handle_results_in_exactly_1_success_and_49_failures() {
        val keyId = "key_concurrent_delete_50"
        val rawPrivKey = ByteArray(32) { i ->
            testPrivateKeyHex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }

        // 1. 存儲金鑰
        val importHandle = TestPlatformAuthenticator.issueHandle(
            keyId = keyId,
            operation = AuthOperation.IMPORT,
            intentFingerprint = "",
            validityDurationMs = 60_000L,
            walletId = keyId
        )
        val storeResult = runBlocking {
            keyManager.storePrivateKey(
                keyId = keyId,
                privateKey = rawPrivKey,
                requireAuth = true,
                authContext = AuthenticationContext(authHandle = importHandle),
                expectedWalletId = keyId
            )
        }
        assertTrue(storeResult is Result.Success)

        // 2. 準備單一刪除 AuthHandle
        val deleteHandle = TestPlatformAuthenticator.issueHandle(
            keyId = keyId,
            operation = AuthOperation.DELETE,
            intentFingerprint = "",
            validityDurationMs = 30_000L,
            walletId = keyId
        )

        val threadCount = 50
        val executor = Executors.newFixedThreadPool(threadCount)
        val startGun = CountDownLatch(1)
        val finishLatch = CountDownLatch(threadCount)

        val successCount = AtomicInteger(0)
        val authFailureCount = AtomicInteger(0)

        for (i in 0 until threadCount) {
            executor.submit {
                try {
                    startGun.await()
                    val result = runBlocking {
                        keyManager.deletePrivateKey(
                            keyId = keyId,
                            authContext = AuthenticationContext(authHandle = deleteHandle),
                            expectedWalletId = keyId
                        )
                    }
                    when (result) {
                        is Result.Success -> successCount.incrementAndGet()
                        is Result.Failure -> {
                            val ex = result.exception
                            if (ex is AuthenticationRequiredException || ex.cause is AuthenticationRequiredException || ex is KeyNotFoundException) {
                                authFailureCount.incrementAndGet()
                            }
                        }
                        else -> {}
                    }
                } finally {
                    finishLatch.countDown()
                }
            }
        }

        startGun.countDown()
        val finished = finishLatch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        assertTrue(finished)
        assertEquals("Exactly 1 thread must succeed deleting key", 1, successCount.get())
        assertEquals("49 threads must fail deletion", 49, authFailureCount.get())
    }
}

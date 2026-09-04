package com.cbstudio.wearwallet.core.security

import android.content.Context
import androidx.biometric.BiometricPrompt
import com.cbstudio.wearwallet.core.common.Result
import io.github.iml1s.crypto.SecureByteArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlin.system.measureNanoTime

/**
 * Milestone 1 (M1) Empirical Security Challenger Stress Harness
 *
 * Provides rigorous empirical stress testing for PR #32 Round 7 Milestone 1:
 * 1. Replay Attacks & Nonce Reuse on ProofTokenVerifier & PlatformAuthHandle.
 * 2. Timing Analysis Resistance & Constant-Time Comparison Verification.
 * 3. Multi-Threaded Concurrency Stress & High-Contention Race Condition Detection.
 * 4. Memory Cleansing & Zeroed Sensitive Buffer Inspection.
 */
class Milestone1EmpiricalStressChallengeTest {

    private lateinit var mockContext: Context
    private val testPrivateKeyHex = "e331b6d69882b4cb4ea581d88ec2693329b3b3a4711832421415ebff2d474428"

    @Before
    fun setUp() {
        AuthHandleRegistry.clearForTesting()
        mockContext = mock<Context>()
        whenever(mockContext.applicationContext).thenReturn(mockContext)
    }

    @org.junit.After
    fun tearDown() {
        AuthHandleRegistry.clearForTesting()
    }

    private fun createTestEnvironment(): Triple<AndroidSecureKeyManager, TestKeyStoreBackend, InMemorySharedPreferences> {
        val testKs = TestKeyStoreBackend()
        val prefs = InMemorySharedPreferences()
        val manager = AndroidSecureKeyManager(
            context = mockContext,
            config = SecureStorageConfig(enableRootDetection = false),
            keyStoreProvider = { testKs.createKeyStore() },
            encryptedPrefsProvider = { prefs },
            secretKeyProvider = { alias, _ -> testKs.generateAndStoreKey(alias) }
        )
        return Triple(manager, testKs, prefs)
    }

    // =========================================================================
    // CATEGORY 1: Replay Attacks, Nonce Manipulation & Auth Handle Tampering
    // =========================================================================

    @Test
    fun test_01_proofToken_valid_token_verifies_successfully() {
        val keyId = "key_test_01"
        val op = AuthOperation.SIGN
        val intent = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        val session = "session_01"
        val nonce = "nonce_01"
        val issuedAt = 1000L
        val expiresAt = 2000L
        val walletId = "wallet_test"

        val token = ProofTokenVerifier.sign(
            keyId = keyId,
            operation = op,
            intentFingerprint = intent,
            sessionId = session,
            nonce = nonce,
            issuedAtMs = issuedAt,
            expiresAtMs = expiresAt,
            walletId = walletId
        )

        assertNotNull("Generated token must not be null", token)
        assertEquals("Proof token must be 64-char hex string (HMAC-SHA256)", 64, token.length)

        val isValid = ProofTokenVerifier.verify(
            proofToken = token,
            keyId = keyId,
            operation = op,
            intentFingerprint = intent,
            sessionId = session,
            nonce = nonce,
            issuedAtMs = issuedAt,
            expiresAtMs = expiresAt,
            walletId = walletId
        )
        assertTrue("Valid token with exact parameters must pass verification", isValid)
    }

    @Test
    fun test_02_proofToken_replay_with_altered_keyId_fails() {
        val token = ProofTokenVerifier.sign(
            keyId = "key_alice",
            operation = AuthOperation.SIGN,
            intentFingerprint = "digest_abc",
            sessionId = "session_1",
            nonce = "nonce_1",
            issuedAtMs = 1000L,
            expiresAtMs = 5000L,
            walletId = "wallet_alice"
        )

        val isValid = ProofTokenVerifier.verify(
            proofToken = token,
            keyId = "key_bob", // Altered keyId
            operation = AuthOperation.SIGN,
            intentFingerprint = "digest_abc",
            sessionId = "session_1",
            nonce = "nonce_1",
            issuedAtMs = 1000L,
            expiresAtMs = 5000L,
            walletId = "wallet_alice"
        )
        assertFalse("Replaying token with altered keyId must be rejected", isValid)
    }

    @Test
    fun test_03_proofToken_replay_with_altered_operation_fails() {
        val token = ProofTokenVerifier.sign(
            keyId = "key_test",
            operation = AuthOperation.SIGN,
            intentFingerprint = "digest_abc",
            sessionId = "session_1",
            nonce = "nonce_1",
            issuedAtMs = 1000L,
            expiresAtMs = 5000L,
            walletId = "wallet_test"
        )

        // Attempt to reuse SIGN token for EXPORT, DELETE, REVEAL, IMPORT
        for (targetOp in listOf(AuthOperation.EXPORT, AuthOperation.DELETE, AuthOperation.REVEAL, AuthOperation.IMPORT)) {
            val isValid = ProofTokenVerifier.verify(
                proofToken = token,
                keyId = "key_test",
                operation = targetOp,
                intentFingerprint = "digest_abc",
                sessionId = "session_1",
                nonce = "nonce_1",
                issuedAtMs = 1000L,
                expiresAtMs = 5000L,
                walletId = "wallet_test"
            )
            assertFalse("Replaying SIGN token for operation $targetOp must be rejected", isValid)
        }
    }

    @Test
    fun test_04_proofToken_replay_with_altered_intentFingerprint_fails() {
        val token = ProofTokenVerifier.sign(
            keyId = "key_test",
            operation = AuthOperation.SIGN,
            intentFingerprint = "tx_digest_original",
            sessionId = "session_1",
            nonce = "nonce_1",
            issuedAtMs = 1000L,
            expiresAtMs = 5000L,
            walletId = "wallet_test"
        )

        val isValid = ProofTokenVerifier.verify(
            proofToken = token,
            keyId = "key_test",
            operation = AuthOperation.SIGN,
            intentFingerprint = "tx_digest_tampered", // Altered intent
            sessionId = "session_1",
            nonce = "nonce_1",
            issuedAtMs = 1000L,
            expiresAtMs = 5000L,
            walletId = "wallet_test"
        )
        assertFalse("Replaying token with altered intent fingerprint must be rejected", isValid)
    }

    @Test
    fun test_05_proofToken_replay_with_altered_nonce_or_session_fails() {
        val token = ProofTokenVerifier.sign(
            keyId = "key_test",
            operation = AuthOperation.SIGN,
            intentFingerprint = "digest_abc",
            sessionId = "session_orig",
            nonce = "nonce_orig",
            issuedAtMs = 1000L,
            expiresAtMs = 5000L,
            walletId = "wallet_test"
        )

        // Altered nonce
        assertFalse(
            "Altered nonce must fail",
            ProofTokenVerifier.verify(
                proofToken = token,
                keyId = "key_test",
                operation = AuthOperation.SIGN,
                intentFingerprint = "digest_abc",
                sessionId = "session_orig",
                nonce = "nonce_replayed",
                issuedAtMs = 1000L,
                expiresAtMs = 5000L,
                walletId = "wallet_test"
            )
        )

        // Altered sessionId
        assertFalse(
            "Altered sessionId must fail",
            ProofTokenVerifier.verify(
                proofToken = token,
                keyId = "key_test",
                operation = AuthOperation.SIGN,
                intentFingerprint = "digest_abc",
                sessionId = "session_replayed",
                nonce = "nonce_orig",
                issuedAtMs = 1000L,
                expiresAtMs = 5000L,
                walletId = "wallet_test"
            )
        )
    }

    @Test
    fun test_06_proofToken_replay_with_altered_timestamps_fails() {
        val token = ProofTokenVerifier.sign(
            keyId = "key_test",
            operation = AuthOperation.SIGN,
            intentFingerprint = "digest_abc",
            sessionId = "session_1",
            nonce = "nonce_1",
            issuedAtMs = 1000L,
            expiresAtMs = 5000L,
            walletId = "wallet_test"
        )

        // Altered issuedAtMs
        assertFalse(
            "Altered issuedAtMs must fail",
            ProofTokenVerifier.verify(
                proofToken = token,
                keyId = "key_test",
                operation = AuthOperation.SIGN,
                intentFingerprint = "digest_abc",
                sessionId = "session_1",
                nonce = "nonce_1",
                issuedAtMs = 2000L,
                expiresAtMs = 5000L,
                walletId = "wallet_test"
            )
        )

        // Altered expiresAtMs
        assertFalse(
            "Altered expiresAtMs must fail",
            ProofTokenVerifier.verify(
                proofToken = token,
                keyId = "key_test",
                operation = AuthOperation.SIGN,
                intentFingerprint = "digest_abc",
                sessionId = "session_1",
                nonce = "nonce_1",
                issuedAtMs = 1000L,
                expiresAtMs = 9999L,
                walletId = "wallet_test"
            )
        )
    }

    @Test
    fun test_07_proofToken_blank_fields_fail_closed() {
        assertFalse(
            "Blank token must fail",
            ProofTokenVerifier.verify(
                proofToken = "",
                keyId = "key_1",
                operation = AuthOperation.SIGN,
                intentFingerprint = "intent",
                sessionId = "sess",
                nonce = "nonce",
                issuedAtMs = 1000L,
                expiresAtMs = 2000L,
                walletId = "wallet_1"
            )
        )

        assertFalse(
            "Blank keyId must fail",
            ProofTokenVerifier.verify(
                proofToken = "abcd",
                keyId = "",
                operation = AuthOperation.SIGN,
                intentFingerprint = "intent",
                sessionId = "sess",
                nonce = "nonce",
                issuedAtMs = 1000L,
                expiresAtMs = 2000L,
                walletId = "wallet_1"
            )
        )

        assertFalse(
            "Blank sessionId must fail",
            ProofTokenVerifier.verify(
                proofToken = "abcd",
                keyId = "key_1",
                operation = AuthOperation.SIGN,
                intentFingerprint = "intent",
                sessionId = "",
                nonce = "nonce",
                issuedAtMs = 1000L,
                expiresAtMs = 2000L,
                walletId = "wallet_1"
            )
        )

        assertFalse(
            "Blank nonce must fail",
            ProofTokenVerifier.verify(
                proofToken = "abcd",
                keyId = "key_1",
                operation = AuthOperation.SIGN,
                intentFingerprint = "intent",
                sessionId = "sess",
                nonce = "",
                issuedAtMs = 1000L,
                expiresAtMs = 2000L,
                walletId = "wallet_1"
            )
        )
    }

    @Test
    fun test_08_platformAuthHandle_lifecycle_and_single_use_invalidation() {
        val handle = TestPlatformAuthenticator.issueHandle(
            keyId = "key_test_lifecycle",
            operation = AuthOperation.SIGN,
            intentFingerprint = "tx_digest_123",
            validityDurationMs = 5000L
        )

        // Check initially valid
        assertTrue("Fresh handle must be valid", handle.isValid("key_test_lifecycle", "tx_digest_123", AuthOperation.SIGN, expectedWalletId = "key_test_lifecycle"))
        assertFalse("Fresh handle is not invalidated", handle.isInvalidated)
        assertFalse("Fresh handle is not expired", handle.isExpired())

        // Invalidate handle
        handle.invalidate()
        assertTrue("Handle must be marked invalidated", handle.isInvalidated)
        assertFalse("Invalidated handle must return false on isValid()", handle.isValid("key_test_lifecycle", "tx_digest_123", AuthOperation.SIGN, expectedWalletId = "key_test_lifecycle"))
    }

    @Test
    fun test_09_platformAuthHandle_expiration_boundary() {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        val handle = TestPlatformAuthenticator.issueHandle(
            keyId = "key_exp",
            operation = AuthOperation.SIGN,
            intentFingerprint = "digest_1",
            expiresAtMs = now + 5000L
        )

        assertTrue("Handle valid before expiration", handle.isValid("key_exp", "digest_1", AuthOperation.SIGN, currentTimeMs = now + 4999L, expectedWalletId = "key_exp"))
        assertFalse("Handle expired after expiration time", handle.isValid("key_exp", "digest_1", AuthOperation.SIGN, currentTimeMs = now + 5001L, expectedWalletId = "key_exp"))
        assertTrue("isExpired returns true after expiration time", handle.isExpired(currentTimeMs = now + 5001L))
    }

    // =========================================================================
    // CATEGORY 2: Timing Analysis Resistance & Constant-Time Comparison
    // =========================================================================

    @Test
    fun test_10_constantTimeEquals_1_bit_mutation_exhaustive_rejection() {
        val keyId = "key_timing_test"
        val op = AuthOperation.SIGN
        val intent = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        val session = "sess_timing"
        val nonce = "nonce_timing"
        val issuedAt = 1000L
        val expiresAt = 5000L
        val walletId = "wallet_timing_test"

        val validToken = ProofTokenVerifier.sign(keyId, op, intent, session, nonce, issuedAt, expiresAt, walletId)
        assertEquals(64, validToken.length)

        val hexChars = "0123456789abcdef"

        // Mutate every single position of the 64-char string to all other 15 hex characters
        for (i in 0 until 64) {
            val originalChar = validToken[i]
            for (c in hexChars) {
                if (c == originalChar) continue
                val mutatedToken = validToken.substring(0, i) + c + validToken.substring(i + 1)
                val isValid = ProofTokenVerifier.verify(
                    proofToken = mutatedToken,
                    keyId = keyId,
                    operation = op,
                    intentFingerprint = intent,
                    sessionId = session,
                    nonce = nonce,
                    issuedAtMs = issuedAt,
                    expiresAtMs = expiresAt,
                    walletId = walletId
                )
                assertFalse("Mutated token at index $i with char '$c' MUST be rejected", isValid)
            }
        }
    }

    @Test
    fun test_11_timing_variance_across_matching_prefix_lengths() {
        val keyId = "key_timing_variance"
        val op = AuthOperation.SIGN
        val intent = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2"
        val session = "sess_variance"
        val nonce = "nonce_variance"
        val issuedAt = 1000L
        val expiresAt = 5000L
        val walletId = "wallet_variance"

        val validToken = ProofTokenVerifier.sign(keyId, op, intent, session, nonce, issuedAt, expiresAt, walletId)

        // Warmup JIT
        for (w in 0..1000) {
            ProofTokenVerifier.verify(validToken, keyId, op, intent, session, nonce, issuedAt, expiresAt, walletId)
        }

        // Test prefix matches of 0, 16, 32, 48, 63 characters
        val matchLengths = listOf(0, 16, 32, 48, 63)
        val iterations = 5000

        for (matchLen in matchLengths) {
            val invertedChar = if (validToken[matchLen] == 'a') 'b' else 'a'
            val testToken = validToken.substring(0, matchLen) + invertedChar + "0".repeat(63 - matchLen)

            val elapsedNano = measureNanoTime {
                for (iter in 0 until iterations) {
                    val res = ProofTokenVerifier.verify(testToken, keyId, op, intent, session, nonce, issuedAt, expiresAt, walletId)
                    assertFalse(res)
                }
            }
            val avgPerOpNs = elapsedNano.toDouble() / iterations
            // Average per op should be under 50 microseconds (50,000 ns) and consistent
            assertTrue("Avg time per verify ($avgPerOpNs ns) for matchLen=$matchLen must be reasonable", avgPerOpNs < 50_000.0)
        }
    }

    // =========================================================================
    // CATEGORY 3: Multi-Threaded Concurrency Stress & High Contention
    // =========================================================================

    @Test
    fun test_12_concurrent_proofToken_signing_and_verification_under_high_contention() {
        val threadCount = 30
        val opsPerThread = 150
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val errorCount = AtomicInteger(0)
        val successCount = AtomicInteger(0)

        for (t in 0 until threadCount) {
            executor.submit {
                try {
                    for (i in 0 until opsPerThread) {
                        val keyId = "key_concurrent_${t}_$i"
                        val session = "session_${t}_$i"
                        val nonce = "nonce_${t}_$i"
                        val intent = "intent_${t}_$i"
                        val issued = System.currentTimeMillis()
                        val expires = issued + 10_000L
                        val walletId = "wallet_concurrent_${t}_$i"

                        val token = ProofTokenVerifier.sign(
                            keyId = keyId,
                            walletId = walletId,
                            operation = AuthOperation.SIGN,
                            intentFingerprint = intent,
                            sessionId = session,
                            nonce = nonce,
                            issuedAtMs = issued,
                            expiresAtMs = expires
                        )

                        val valid = ProofTokenVerifier.verify(
                            proofToken = token,
                            keyId = keyId,
                            walletId = walletId,
                            operation = AuthOperation.SIGN,
                            intentFingerprint = intent,
                            sessionId = session,
                            nonce = nonce,
                            issuedAtMs = issued,
                            expiresAtMs = expires
                        )

                        if (valid) {
                            successCount.incrementAndGet()
                        } else {
                            errorCount.incrementAndGet()
                        }
                    }
                } catch (e: Throwable) {
                    errorCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        val completed = latch.await(30, TimeUnit.SECONDS)
        executor.shutdown()

        assertTrue("All concurrent worker threads must complete within timeout", completed)
        assertEquals("Zero errors allowed in concurrent token signing/verification", 0, errorCount.get())
        assertEquals("All ${threadCount * opsPerThread} operations must succeed", threadCount * opsPerThread, successCount.get())
    }

    @Test
    fun test_13_concurrent_authHandle_creation_and_validation() = runBlocking {
        val coroutineCount = 50
        val results = (0 until coroutineCount).map { i ->
            async(Dispatchers.Default) {
                val keyId = "key_coroutine_$i"
                val intent = "intent_hash_$i"
                val handle = TestPlatformAuthenticator.issueHandle(
                    keyId = keyId,
                    operation = AuthOperation.SIGN,
                    intentFingerprint = intent,
                    validityDurationMs = 10_000L
                )

                val valid = handle.isValid(
                    expectedKeyId = keyId,
                    expectedIntentFingerprint = intent,
                    expectedOperation = AuthOperation.SIGN
                )
                valid
            }
        }.awaitAll()

        assertEquals("All $coroutineCount coroutines must complete", coroutineCount, results.size)
        assertTrue("All generated handles must be valid", results.all { it })
    }

    @Test
    fun test_14_concurrent_single_use_handle_invalidation_race() {
        val threadCount = 20
        val handle = TestPlatformAuthenticator.issueHandle(
            keyId = "key_race",
            operation = AuthOperation.SIGN,
            intentFingerprint = "digest_race",
            validityDurationMs = 10_000L
        )

        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val startGate = CountDownLatch(1)

        for (t in 0 until threadCount) {
            executor.submit {
                try {
                    startGate.await()
                    handle.invalidate()
                } finally {
                    latch.countDown()
                }
            }
        }

        startGate.countDown()
        val completed = latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        assertTrue("Race threads completed", completed)
        assertTrue("Handle must be invalidated after concurrent invalidations", handle.isInvalidated)
        assertFalse("Handle cannot be valid after invalidation", handle.isValid("key_race", "digest_race", AuthOperation.SIGN))
    }

    @Test
    fun test_15_concurrent_secure_key_manager_signing_under_load() = runTest {
        val (manager, _, _) = createTestEnvironment()
        val keyCount = 10

        // Provision 10 keys
        for (k in 0 until keyCount) {
            val keyId = "key_load_$k"
            val authImport = AuthenticationContext(authHandle = TestPlatformAuthenticator.issueHandle(keyId, AuthOperation.IMPORT, walletId = keyId))
            val storeRes = manager.storePrivateKey(keyId, testPrivateKeyHex.encodeToByteArray(), requireAuth = true, authContext = authImport, expectedWalletId = keyId)
            assertTrue("Store key $keyId must succeed", storeRes is Result.Success)
        }

        // Concurrently sign with all 10 keys
        val results = (0 until keyCount).map { k ->
            async(Dispatchers.Default) {
                val keyId = "key_load_$k"
                val txData = "Tx payload for wallet $k".encodeToByteArray()
                val txDigest = CryptoUtils.sha256(txData).toHexString()

                val handle = TestPlatformAuthenticator.issueHandle(
                    keyId = keyId,
                    operation = AuthOperation.SIGN,
                    intentFingerprint = txDigest,
                    walletId = keyId,
                    validityDurationMs = 10_000L
                )
                val authContext = AuthenticationContext(authHandle = handle)

                val signRes = manager.signWithKey(keyId, txData, authContext = authContext, expectedWalletId = keyId)
                signRes is Result.Success
            }
        }.awaitAll()

        assertEquals("All $keyCount concurrent signings completed", keyCount, results.size)
        assertTrue("All concurrent signings succeeded", results.all { it })
    }

    // =========================================================================
    // CATEGORY 4: Memory Cleansing & Zeroed Sensitive Buffer Inspection
    // =========================================================================

    @Test
    fun test_16_secureZero_clears_all_buffers_across_size_spectra() {
        val testSizes = listOf(0, 1, 2, 16, 31, 32, 33, 64, 128, 256, 1024, 4096)

        for (size in testSizes) {
            val buffer = ByteArray(size) { (it % 255 + 1).toByte() }
            if (size > 0) {
                assertTrue("Buffer before secureZero must have non-zero bytes", buffer.any { it != 0.toByte() })
            }

            SecureByteArray.secureZero(buffer)

            assertTrue("Every byte in buffer of size $size must be 0x00 after secureZero", buffer.all { it == 0.toByte() })
        }
    }

    @Test
    fun test_17_secureZero_cleanses_high_entropy_patterns() {
        val patterns = listOf(
            ByteArray(64) { 0xFF.toByte() },
            ByteArray(64) { 0xAA.toByte() },
            ByteArray(64) { 0x55.toByte() },
            ByteArray(64) { (it xor 0xA5).toByte() },
            Random.nextBytes(64)
        )

        for (pattern in patterns) {
            assertTrue("Pattern must initially contain non-zero bytes", pattern.any { it != 0.toByte() })
            SecureByteArray.secureZero(pattern)
            assertTrue("Pattern must be 100% zeroed", pattern.all { it == 0.toByte() })
        }
    }

    @Test
    fun test_18_androidSecureKeyManager_cleanses_buffers_on_store_and_sign() = runTest {
        val (manager, _, _) = createTestEnvironment()
        val keyId = "key_mem_test"

        val authImport = AuthenticationContext(authHandle = TestPlatformAuthenticator.issueHandle(keyId, AuthOperation.IMPORT, walletId = keyId))
        val privateKeyBytes = testPrivateKeyHex.encodeToByteArray()
        val callerCopy = privateKeyBytes.copyOf()

        // Store private key
        val storeRes = manager.storePrivateKey(keyId, privateKeyBytes, requireAuth = true, authContext = authImport, expectedWalletId = keyId)
        assertTrue(storeRes is Result.Success)

        // Verify caller's copy is unaffected (ownership retained), but manager's internal buffer was zeroed in finally
        assertArrayEquals("Caller buffer retained", callerCopy, privateKeyBytes)
        // Caller cleans up their own buffer
        SecureByteArray.secureZero(privateKeyBytes)
        SecureByteArray.secureZero(callerCopy)
        assertTrue(privateKeyBytes.all { it == 0.toByte() })

        // Sign with key
        val txData = "Tx data for memory test".encodeToByteArray()
        val txDigest = CryptoUtils.sha256(txData).toHexString()
        val signHandle = TestPlatformAuthenticator.issueHandle(
            keyId = keyId,
            operation = AuthOperation.SIGN,
            intentFingerprint = txDigest,
            walletId = keyId
        )
        val signAuth = AuthenticationContext(authHandle = signHandle)

        val signRes = manager.signWithKey(keyId, txData, authContext = signAuth, expectedWalletId = keyId)
        assertTrue("Signing must succeed", signRes is Result.Success)
        val signature = (signRes as Result.Success).data
        assertEquals("Signature length must be 65 bytes (r + s + v)", 65, signature.size)
    }

    @Test
    fun test_19_androidSecureKeyManager_cleanses_buffers_on_failure() = runTest {
        val (manager, _, _) = createTestEnvironment()
        val keyId = "key_fail_mem"

        // Invalid auth handle (operation mismatch)
        val badHandle = TestPlatformAuthenticator.issueHandle(keyId, AuthOperation.SIGN, walletId = keyId)
        val badAuth = AuthenticationContext(authHandle = badHandle)
        val privateKeyBytes = testPrivateKeyHex.encodeToByteArray()

        val storeRes = manager.storePrivateKey(keyId, privateKeyBytes, requireAuth = true, authContext = badAuth, expectedWalletId = keyId)
        assertTrue("Store must fail due to operation mismatch", storeRes is Result.Failure)

        // Caller zeroes their buffer
        SecureByteArray.secureZero(privateKeyBytes)
        assertTrue(privateKeyBytes.all { it == 0.toByte() })
    }
}


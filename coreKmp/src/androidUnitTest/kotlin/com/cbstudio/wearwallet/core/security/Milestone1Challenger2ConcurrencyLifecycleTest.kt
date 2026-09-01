package com.cbstudio.wearwallet.core.security

import android.content.Context
import com.cbstudio.wearwallet.core.common.Result
import io.github.iml1s.crypto.Secp256k1Pure
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Challenger M1-2: Concurrency, Lifecycle & Tampering Adversarial Verification Suite
 *
 * Empirical Challenge Focus:
 * 1. Concurrent handle validation and race conditions on `authHandle.invalidate()` / `AuthHandleRegistry`.
 * 2. Expiration boundary precision (exact expiration timestamp, validity window, clock skew, edge boundaries).
 * 3. Tampered intent fingerprints (case variation, format injection, length tampering, malicious payloads).
 */
class Milestone1Challenger2ConcurrencyLifecycleTest {

    private lateinit var mockContext: Context
    private val testPrivateKeyHex = "e331b6d69882b4cb4ea581d88ec2693329b3b3a4711832421415ebff2d474428"

    @Before
    fun setUp() {
        mockContext = mock<Context>()
        whenever(mockContext.applicationContext).thenReturn(mockContext)
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
    // SECTION 1: CONCURRENCY & RACE CONDITION ADVERSARIAL CHALLENGES
    // =========================================================================

    @Test
    fun challenge_concurrent_invalidation_race_preserves_single_use_invariant() {
        val threadCount = 40
        val handle = TestPlatformAuthenticator.issueHandle(
            keyId = "key_concurrent_inv",
            operation = AuthOperation.SIGN,
            intentFingerprint = "intent_race_inv",
            validityDurationMs = 15_000L
        )

        assertTrue("Handle must be initially valid", handle.isValid("key_concurrent_inv", "intent_race_inv", AuthOperation.SIGN))

        val executor = Executors.newFixedThreadPool(threadCount)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)
        val exceptions = AtomicInteger(0)

        for (i in 0 until threadCount) {
            executor.submit {
                try {
                    startLatch.await()
                    handle.invalidate()
                } catch (t: Throwable) {
                    exceptions.incrementAndGet()
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        startLatch.countDown()
        val finished = doneLatch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        assertTrue("All invalidation threads must complete within timeout", finished)
        assertEquals("Zero unhandled exceptions during concurrent invalidation", 0, exceptions.get())
        assertTrue("Handle isInvalidated must be true", handle.isInvalidated)
        assertTrue("Registry must report session consumed", AuthHandleRegistry.isConsumed(handle.sessionId))
        assertFalse("isValid() must strictly return false", handle.isValid("key_concurrent_inv", "intent_race_inv", AuthOperation.SIGN))
    }

    @Test
    fun challenge_concurrent_validation_and_invalidation_race() {
        val threadCount = 50
        val handle = TestPlatformAuthenticator.issueHandle(
            keyId = "key_val_inv_race",
            operation = AuthOperation.SIGN,
            intentFingerprint = "intent_val_inv",
            validityDurationMs = 20_000L
        )

        val executor = Executors.newFixedThreadPool(threadCount)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)
        val validCount = AtomicInteger(0)
        val invalidCount = AtomicInteger(0)

        for (i in 0 until threadCount) {
            executor.submit {
                try {
                    startLatch.await()
                    if (i == threadCount / 2) {
                        handle.invalidate()
                    }
                    val valid = handle.isValid("key_val_inv_race", "intent_val_inv", AuthOperation.SIGN)
                    if (valid) {
                        validCount.incrementAndGet()
                    } else {
                        invalidCount.incrementAndGet()
                    }
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        startLatch.countDown()
        val finished = doneLatch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        assertTrue("All threads must finish", finished)
        assertTrue("Handle must be invalidated after the race", handle.isInvalidated)
        // After invalidation, subsequent isValid calls must be false
        assertFalse("Final isValid must be false", handle.isValid("key_val_inv_race", "intent_val_inv", AuthOperation.SIGN))
    }

    @Test
    fun challenge_concurrent_registry_stress_under_high_thread_contention() {
        val threadCount = 30
        val sessionsPerThread = 100
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val errors = AtomicInteger(0)

        for (t in 0 until threadCount) {
            executor.submit {
                try {
                    for (s in 0 until sessionsPerThread) {
                        val sessionId = "session_${t}_$s"
                        val now = System.currentTimeMillis()
                        val expiresAt = now + 30_000L

                        AuthHandleRegistry.register(
                            sessionId = sessionId,
                            expiresAtMs = expiresAt,
                            keyId = "key_${t}_$s",
                            operation = AuthOperation.SIGN,
                            intentFingerprint = "",
                            walletId = "wallet_${t}_$s",
                            issuedAtMs = now,
                            authenticatorType = "TEST_CONCURRENCY"
                        )
                        if (!AuthHandleRegistry.isRegistered(sessionId)) {
                            errors.incrementAndGet()
                        }
                        if (AuthHandleRegistry.isConsumed(sessionId)) {
                            errors.incrementAndGet()
                        }

                        val consumed = AuthHandleRegistry.consume(sessionId)
                        if (!consumed) {
                            errors.incrementAndGet()
                        }
                        if (!AuthHandleRegistry.isConsumed(sessionId)) {
                            errors.incrementAndGet()
                        }
                        if (AuthHandleRegistry.isRegistered(sessionId)) {
                            errors.incrementAndGet()
                        }
                    }
                } catch (t: Throwable) {
                    errors.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        val completed = latch.await(20, TimeUnit.SECONDS)
        executor.shutdown()

        assertTrue("Registry stress test completed", completed)
        assertEquals("Zero errors in concurrent registry state machine", 0, errors.get())
    }

    @Test
    fun challenge_concurrent_signing_with_same_handle_replay_protection() = runTest {
        val (manager, _, _) = createTestEnvironment()
        val keyId = "key_single_use_race"
        val authImport = AuthenticationContext(authHandle = TestPlatformAuthenticator.issueHandle(keyId, AuthOperation.IMPORT, walletId = keyId))
        manager.storePrivateKey(keyId, testPrivateKeyHex.encodeToByteArray(), requireAuth = true, authContext = authImport, expectedWalletId = keyId)

        val txData = "Concurrent single-use test".encodeToByteArray()
        val txDigest = CryptoUtils.sha256(txData).toHexString()

        val sharedHandle = TestPlatformAuthenticator.issueHandle(
            keyId = keyId,
            operation = AuthOperation.SIGN,
            intentFingerprint = txDigest,
            walletId = keyId,
            validityDurationMs = 10_000L
        )
        val authContext = AuthenticationContext(authHandle = sharedHandle)

        // Attempt 1: First sign succeeds
        val result1 = manager.signWithKey(keyId, txData, authContext = authContext, expectedWalletId = keyId)
        assertTrue("First sign attempt must succeed", result1 is Result.Success)
        assertTrue("Handle must be invalidated after first use", sharedHandle.isInvalidated)

        // Attempt 2: Replay must fail closed
        val result2 = manager.signWithKey(keyId, txData, authContext = authContext, expectedWalletId = keyId)
        assertTrue("Second sign attempt with same handle MUST fail", result2 is Result.Failure)
        val ex2 = (result2 as Result.Failure).exception
        assertTrue("Exception must be AuthenticationRequiredException", ex2 is AuthenticationRequiredException)
    }

    // =========================================================================
    // SECTION 2: EXPIRATION BOUNDARY & TIMING PRECISION ADVERSARIAL CHALLENGES
    // =========================================================================

    @Test
    fun challenge_exact_expiration_millisecond_boundary_precision() {
        val baseTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        val validityDuration = 60_000L
        val expiresAt = baseTime + validityDuration

        val handle = PlatformAuthHandle(
            keyId = "key_exact_exp",
            operation = AuthOperation.SIGN,
            intentFingerprint = "intent_exact",
            sessionId = "sess_exact",
            nonce = "nonce_exact",
            issuedAtMs = baseTime,
            expiresAtMs = expiresAt,
            walletId = "key_exact_exp",
            proofToken = ProofTokenVerifier.sign(
                keyId = "key_exact_exp",
                operation = AuthOperation.SIGN,
                intentFingerprint = "intent_exact",
                sessionId = "sess_exact",
                nonce = "nonce_exact",
                issuedAtMs = baseTime,
                expiresAtMs = expiresAt,
                walletId = "key_exact_exp"
            )
        )

        // T - 1ms before expiration -> Valid
        assertFalse("At expiresAt - 1ms, isExpired must be false", handle.isExpired(expiresAt - 1))
        assertTrue("At expiresAt - 1ms, isValid must be true", handle.isValid("key_exact_exp", "intent_exact", AuthOperation.SIGN, currentTimeMs = expiresAt - 1, expectedWalletId = "key_exact_exp"))

        // T exact expiration millisecond -> Expired & Invalid (fails closed at exact expiration boundary)
        assertTrue("At exact expiresAt, isExpired must be true", handle.isExpired(expiresAt))
        assertFalse("At exact expiresAt, isValid must be false", handle.isValid("key_exact_exp", "intent_exact", AuthOperation.SIGN, currentTimeMs = expiresAt, expectedWalletId = "key_exact_exp"))

        // T + 1ms past expiration -> Expired & Invalid
        assertTrue("At expiresAt + 1ms, isExpired must be true", handle.isExpired(expiresAt + 1))
        assertFalse("At expiresAt + 1ms, isValid must be false", handle.isValid("key_exact_exp", "intent_exact", AuthOperation.SIGN, currentTimeMs = expiresAt + 1, expectedWalletId = "key_exact_exp"))

        // Far future -> Expired
        assertTrue("Far future must be expired", handle.isExpired(expiresAt + 1_000_000))
        assertFalse("Far future must be invalid", handle.isValid("key_exact_exp", "intent_exact", AuthOperation.SIGN, currentTimeMs = expiresAt + 1_000_000, expectedWalletId = "key_exact_exp"))
    }

    @Test
    fun challenge_clock_skew_and_future_issued_timestamp_fails_closed() {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        val futureIssued = now + 10_000L
        val futureExpires = futureIssued + 60_000L

        val handle = PlatformAuthHandle(
            keyId = "key_skew",
            operation = AuthOperation.SIGN,
            intentFingerprint = "intent_skew",
            sessionId = "sess_skew",
            nonce = "nonce_skew",
            issuedAtMs = futureIssued,
            expiresAtMs = futureExpires,
            walletId = "key_skew",
            proofToken = ProofTokenVerifier.sign(
                keyId = "key_skew",
                operation = AuthOperation.SIGN,
                intentFingerprint = "intent_skew",
                sessionId = "sess_skew",
                nonce = "nonce_skew",
                issuedAtMs = futureIssued,
                expiresAtMs = futureExpires,
                walletId = "key_skew"
            )
        )

        // Evaluated at current time (which is BEFORE issuedAtMs)
        assertFalse("Handle evaluated before issuedAtMs must fail validation (clock skew / future token)", handle.isValid("key_skew", "intent_skew", AuthOperation.SIGN, currentTimeMs = now, expectedWalletId = "key_skew"))

        // Evaluated at issuedAtMs -> Valid
        assertTrue("Handle evaluated at issuedAtMs must be valid", handle.isValid("key_skew", "intent_skew", AuthOperation.SIGN, currentTimeMs = futureIssued, expectedWalletId = "key_skew"))
    }

    @Test
    fun challenge_extreme_and_overflow_timestamp_boundaries() {
        val now = 1_700_000_000_000L

        // Zero expiration (non-expiring or disabled expiration handle)
        val zeroExpHandle = PlatformAuthHandle(
            keyId = "key_zero_exp",
            operation = AuthOperation.SIGN,
            intentFingerprint = "intent_zero",
            sessionId = "sess_zero",
            nonce = "nonce_zero",
            issuedAtMs = now,
            expiresAtMs = 0L,
            walletId = "key_zero_exp",
            proofToken = ProofTokenVerifier.sign("key_zero_exp", AuthOperation.SIGN, "intent_zero", "sess_zero", "nonce_zero", now, 0L, "key_zero_exp")
        )
        assertFalse("Zero expiration is not expired under positive timestamp check", zeroExpHandle.isExpired(now + 1_000_000L))

        // Long.MAX_VALUE expiration
        val maxExpHandle = PlatformAuthHandle(
            keyId = "key_max_exp",
            operation = AuthOperation.SIGN,
            intentFingerprint = "intent_max",
            sessionId = "sess_max",
            nonce = "nonce_max",
            issuedAtMs = now,
            expiresAtMs = Long.MAX_VALUE,
            walletId = "key_max_exp",
            proofToken = ProofTokenVerifier.sign("key_max_exp", AuthOperation.SIGN, "intent_max", "sess_max", "nonce_max", now, Long.MAX_VALUE, "key_max_exp")
        )
        assertFalse("Long.MAX_VALUE expiration is not expired at now", maxExpHandle.isExpired(now))
        assertTrue("Long.MAX_VALUE expiration isValid at now", maxExpHandle.isValid("key_max_exp", "intent_max", AuthOperation.SIGN, currentTimeMs = now, expectedWalletId = "key_max_exp"))
    }

    // =========================================================================
    // SECTION 3: TAMPERED INTENT FINGERPRINT ADVERSARIAL CHALLENGES
    // =========================================================================

    @Test
    fun challenge_intent_fingerprint_case_insensitivity_and_exact_hex_matching() {
        val keyId = "key_intent_case"
        val lowerHex = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        val upperHex = "E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855"
        val mixedHex = "E3b0C44298fc1c149AfBf4c8996fb92427Ae41e4649B934Ca495991b7852B855"

        val handle = TestPlatformAuthenticator.issueHandle(
            keyId = keyId,
            operation = AuthOperation.SIGN,
            intentFingerprint = lowerHex
        )

        // Matching with lower case
        assertTrue("Lower hex should match", handle.isValid(keyId, lowerHex, AuthOperation.SIGN))

        // Matching with upper case (case-insensitive intent check)
        assertTrue("Upper hex should match via ignoreCase", handle.isValid(keyId, upperHex, AuthOperation.SIGN))

        // Matching with mixed case
        assertTrue("Mixed hex should match via ignoreCase", handle.isValid(keyId, mixedHex, AuthOperation.SIGN))
    }

    @Test
    fun challenge_tampered_intent_single_character_mutation_rejected() {
        val keyId = "key_intent_mut"
        val originalIntent = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

        val handle = TestPlatformAuthenticator.issueHandle(
            keyId = keyId,
            operation = AuthOperation.SIGN,
            intentFingerprint = originalIntent
        )

        // Single bit/char flips at different positions (beginning, middle, end)
        val mutatedPositions = listOf(0, 15, 31, 47, 63)
        for (pos in mutatedPositions) {
            val charToReplace = originalIntent[pos]
            val replacementChar = if (charToReplace == '0') '1' else '0'
            val mutatedIntent = originalIntent.substring(0, pos) + replacementChar + originalIntent.substring(pos + 1)

            assertFalse(
                "Mutated intent at pos $pos MUST fail validation",
                handle.isValid(keyId, mutatedIntent, AuthOperation.SIGN)
            )
        }
    }

    @Test
    fun challenge_intent_length_tampering_and_truncation_rejected() {
        val keyId = "key_intent_len"
        val standardIntent = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

        val handle = TestPlatformAuthenticator.issueHandle(
            keyId = keyId,
            operation = AuthOperation.SIGN,
            intentFingerprint = standardIntent
        )

        // Truncated (63 chars)
        assertFalse("Truncated intent MUST fail", handle.isValid(keyId, standardIntent.substring(0, 63), AuthOperation.SIGN))

        // Truncated (32 chars)
        assertFalse("Half-length intent MUST fail", handle.isValid(keyId, standardIntent.substring(0, 32), AuthOperation.SIGN))

        // Extended (65 chars)
        assertFalse("Extended intent MUST fail", handle.isValid(keyId, standardIntent + "0", AuthOperation.SIGN))

        // Empty intent against non-empty expectation
        assertFalse("Empty string expected intent MUST fail against populated handle", handle.isValid(keyId, "", AuthOperation.SIGN))
    }

    @Test
    fun challenge_intent_separator_and_delimiter_injection_rejected() {
        val keyId = "key_delimiter"
        val maliciousIntent = "fake_intent:EXPORT:session_spoofed:nonce_spoofed:1000:2000"

        val handle = TestPlatformAuthenticator.issueHandle(
            keyId = keyId,
            operation = AuthOperation.SIGN,
            intentFingerprint = maliciousIntent
        )

        // Even with colons in the intent string, proof token binds the whole literal string
        assertTrue("Legitimately signed handle with colons validates against exact intent", handle.isValid(keyId, maliciousIntent, AuthOperation.SIGN))

        // An attacker trying to claim it's operation EXPORT must fail
        assertFalse("Operation mismatch cannot be spoofed via delimiter injection", handle.isValid(keyId, maliciousIntent, AuthOperation.EXPORT))
    }

    @Test
    fun challenge_intent_whitespace_and_null_byte_tampering_rejected() {
        val keyId = "key_intent_ws"
        val cleanIntent = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

        val handle = TestPlatformAuthenticator.issueHandle(
            keyId = keyId,
            operation = AuthOperation.SIGN,
            intentFingerprint = cleanIntent
        )

        // Leading/trailing whitespace
        assertFalse("Leading space in expected intent MUST fail", handle.isValid(keyId, " $cleanIntent", AuthOperation.SIGN))
        assertFalse("Trailing space in expected intent MUST fail", handle.isValid(keyId, "$cleanIntent ", AuthOperation.SIGN))
        assertFalse("Newline in expected intent MUST fail", handle.isValid(keyId, "$cleanIntent\n", AuthOperation.SIGN))

        // Null byte injection
        assertFalse("Null byte in expected intent MUST fail", handle.isValid(keyId, "$cleanIntent\u0000", AuthOperation.SIGN))
    }

    @Test
    fun challenge_end_to_end_intent_tampering_on_key_manager_sign() = runTest {
        val (manager, _, _) = createTestEnvironment()
        val keyId = "key_e2e_tamper"
        val authImport = AuthenticationContext(authHandle = TestPlatformAuthenticator.issueHandle(keyId, AuthOperation.IMPORT, walletId = keyId))
        manager.storePrivateKey(keyId, testPrivateKeyHex.encodeToByteArray(), requireAuth = true, authContext = authImport, expectedWalletId = keyId)

        val originalTx = "Transfer 100 USDT to Alice".encodeToByteArray()
        val originalDigest = CryptoUtils.sha256(originalTx).toHexString()

        val tamperedTx = "Transfer 1000000 USDT to Mallory".encodeToByteArray()

        val handle = TestPlatformAuthenticator.issueHandle(
            keyId = keyId,
            operation = AuthOperation.SIGN,
            intentFingerprint = originalDigest,
            walletId = keyId
        )

        // Attempting to sign tamperedTx with handle authorized for originalTx MUST fail
        val signResult = manager.signWithKey(keyId, tamperedTx, authContext = AuthenticationContext(authHandle = handle), expectedWalletId = keyId)

        assertTrue("Signing tampered transaction MUST fail", signResult is Result.Failure)
        val ex = (signResult as Result.Failure).exception
        assertTrue("Exception must be AuthenticationRequiredException", ex is AuthenticationRequiredException)
        assertTrue("Message must indicate fingerprint mismatch", ex.message!!.contains("Intent fingerprint mismatch"))
    }
}

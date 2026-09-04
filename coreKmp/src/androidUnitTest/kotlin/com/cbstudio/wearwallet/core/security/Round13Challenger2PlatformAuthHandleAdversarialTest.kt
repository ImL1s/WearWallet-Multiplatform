package com.cbstudio.wearwallet.core.security

import android.content.Context
import com.cbstudio.wearwallet.core.common.Result
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
 * PR #32 Round 13 Challenger 2 (teamwork_preview_challenger_r13_2)
 * Empirical Adversarial Challenge Test Suite for P1-2:
 * PlatformAuthHandle walletId Binding, HMAC Tamper Proofing, & Concurrency Replay Prevention
 */
class Round13Challenger2PlatformAuthHandleAdversarialTest {

    private val testPrivateKeyHexA = "e331b6d69882b4cb4ea581d88ec2693329b3b3a4711832421415ebff2d474428"
    private val testPrivateKeyHexB = "4c0883a69102937d6231471b5dbb6204db7e716b78ac387728b80b7b1340a69f"
    private lateinit var mockContext: Context

    @Before
    fun setUp() {
        AuthHandleRegistry.clearForTesting()
        DeletionGrantRegistry.clearForTesting()
        mockContext = mock<Context>()
        whenever(mockContext.applicationContext).thenReturn(mockContext)
    }

    @After
    fun tearDown() {
        AuthHandleRegistry.clearForTesting()
        DeletionGrantRegistry.clearForTesting()
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
    // 1. walletId Binding & HMAC Integrity Verification
    // =========================================================================

    @Test
    fun challenge_tampered_walletId_in_platform_auth_handle_fails_hmac_verification() {
        val now = Clock.System.now().toEpochMilliseconds()
        val txDigest = "abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234"
        val realWalletId = "wallet-legitimate-001"
        val tamperedWalletId = "wallet-attacker-002"

        val handle = TestPlatformAuthenticator.issueHandle(
            keyId = "key_test_wallet",
            operation = AuthOperation.SIGN,
            intentFingerprint = txDigest,
            expiresAtMs = now + 60_000L,
            walletId = realWalletId
        )

        // 1. Valid authentication with genuine walletId passes
        assertTrue(
            "Valid handle with genuine walletId must pass verification",
            handle.isValid(
                expectedKeyId = "key_test_wallet",
                expectedIntentFingerprint = txDigest,
                expectedOperation = AuthOperation.SIGN,
                currentTimeMs = now,
                expectedWalletId = realWalletId
            )
        )

        // 2. Querying with mismatched expectedWalletId fails isValid()
        assertFalse(
            "Mismatched expectedWalletId must fail isValid()",
            handle.isValid(
                expectedKeyId = "key_test_wallet",
                expectedIntentFingerprint = txDigest,
                expectedOperation = AuthOperation.SIGN,
                currentTimeMs = now,
                expectedWalletId = tamperedWalletId
            )
        )

        // 3. Forging a handle with tampered walletId property fails ProofTokenVerifier HMAC
        val forgedHandle = PlatformAuthHandle(
            keyId = handle.keyId,
            operation = handle.operation,
            intentFingerprint = handle.intentFingerprint,
            sessionId = handle.sessionId,
            nonce = handle.nonce,
            issuedAtMs = handle.issuedAtMs,
            expiresAtMs = handle.expiresAtMs,
            proofToken = handle.proofToken, // Token was signed with realWalletId
            walletId = tamperedWalletId     // Tampered field
        )

        assertFalse(
            "Tampered walletId in PlatformAuthHandle must fail ProofTokenVerifier HMAC verification",
            ProofTokenVerifier.verify(
                proofToken = forgedHandle.proofToken,
                keyId = forgedHandle.keyId,
                operation = forgedHandle.operation,
                intentFingerprint = forgedHandle.intentFingerprint,
                sessionId = forgedHandle.sessionId,
                nonce = forgedHandle.nonce,
                issuedAtMs = forgedHandle.issuedAtMs,
                expiresAtMs = forgedHandle.expiresAtMs,
                walletId = forgedHandle.walletId
            )
        )

        assertFalse(
            "Forged handle with tampered walletId must fail isValid()",
            forgedHandle.isValid(
                expectedKeyId = "key_test_wallet",
                expectedIntentFingerprint = txDigest,
                expectedOperation = AuthOperation.SIGN,
                currentTimeMs = now,
                expectedWalletId = tamperedWalletId
            )
        )
    }

    // =========================================================================
    // 2. Cross-Wallet Replay & Deletion Rejection
    // =========================================================================

    @Test
    fun challenge_valid_handle_minted_for_walletA_is_rejected_when_operating_on_walletB() = runTest {
        val now = Clock.System.now().toEpochMilliseconds()
        val walletAId = "wallet-A"
        val walletBId = "wallet-B"
        val keyIdA = "key-alias-A"
        val keyIdB = "key-alias-B"

        val handleA = TestPlatformAuthenticator.issueHandle(
            keyId = keyIdA,
            operation = AuthOperation.DELETE,
            expiresAtMs = now + 60_000L,
            walletId = walletAId
        )

        // Attempt 1: AuthHandleRegistry.validateAndConsume with expectedWalletId = walletBId
        val consumeResult = AuthHandleRegistry.validateAndConsume(
            handle = handleA,
            expectedKeyId = keyIdA,
            expectedOperation = AuthOperation.DELETE,
            currentTimeMs = now,
            expectedWalletId = walletBId
        )
        assertTrue("validateAndConsume on cross-wallet MUST fail", consumeResult is Result.Failure)
        val exConsume = (consumeResult as Result.Failure).exception
        assertTrue("Exception must be AuthenticationRequiredException", exConsume is AuthenticationRequiredException)
        assertTrue(
            "Message must cite cross-wallet rejection",
            exConsume.message!!.contains("Cross-wallet handle rejected") || exConsume.message!!.contains("Cross-wallet session rejected")
        )

        // Attempt 2: AuthHandleRegistry.validateConsumeAndIssueGrant for walletBId
        val grantResult = AuthHandleRegistry.validateConsumeAndIssueGrant(
            handle = handleA,
            walletId = walletBId,
            expectedKeyId = keyIdA,
            currentTimeMs = now
        )
        assertTrue("validateConsumeAndIssueGrant on cross-wallet MUST fail", grantResult is Result.Failure)
        val exGrant = (grantResult as Result.Failure).exception
        assertTrue("Exception must be AuthenticationRequiredException", exGrant is AuthenticationRequiredException)
        assertTrue(
            "Message must cite cross-wallet rejection",
            exGrant.message!!.contains("Cross-wallet handle rejected") || exGrant.message!!.contains("Cross-wallet session rejected")
        )

        // Attempt 3: DeletionAuthorizationService.issueDeletionGrant for walletBId
        val serviceResult = DeletionAuthorizationService.issueDeletionGrant(
            handle = handleA,
            walletId = walletBId,
            keyAlias = keyIdA,
            currentTimeMs = now
        )
        assertTrue("DeletionAuthorizationService on cross-wallet MUST fail", serviceResult is Result.Failure)
    }

    // =========================================================================
    // 3. Tampered Payload & Modified Operation Fail Closed
    // =========================================================================

    @Test
    fun challenge_tampered_payload_or_modified_operation_fails_closed() {
        val now = Clock.System.now().toEpochMilliseconds()
        val walletId = "wallet-sec-01"
        val keyId = "key-sec-01"
        val digest = "1122334455667788112233445566778811223344556677881122334455667788"

        val handle = TestPlatformAuthenticator.issueHandle(
            keyId = keyId,
            operation = AuthOperation.SIGN,
            intentFingerprint = digest,
            expiresAtMs = now + 60_000L,
            walletId = walletId
        )

        // Modifying operation from SIGN to DELETE
        val tamperedOpHandle = PlatformAuthHandle(
            keyId = handle.keyId,
            operation = AuthOperation.DELETE,
            intentFingerprint = handle.intentFingerprint,
            sessionId = handle.sessionId,
            nonce = handle.nonce,
            issuedAtMs = handle.issuedAtMs,
            expiresAtMs = handle.expiresAtMs,
            proofToken = handle.proofToken,
            walletId = handle.walletId
        )

        assertFalse(
            "Tampered operation must fail HMAC verification",
            ProofTokenVerifier.verify(
                proofToken = tamperedOpHandle.proofToken,
                keyId = tamperedOpHandle.keyId,
                operation = tamperedOpHandle.operation,
                intentFingerprint = tamperedOpHandle.intentFingerprint,
                sessionId = tamperedOpHandle.sessionId,
                nonce = tamperedOpHandle.nonce,
                issuedAtMs = tamperedOpHandle.issuedAtMs,
                expiresAtMs = tamperedOpHandle.expiresAtMs,
                walletId = tamperedOpHandle.walletId
            )
        )

        val consumeResult = AuthHandleRegistry.validateAndConsume(
            handle = tamperedOpHandle,
            expectedKeyId = keyId,
            expectedOperation = AuthOperation.DELETE,
            currentTimeMs = now,
            expectedWalletId = walletId
        )
        assertTrue("Consumption of tampered operation MUST fail", consumeResult is Result.Failure)

        // Modifying intentFingerprint
        val tamperedDigestHandle = PlatformAuthHandle(
            keyId = handle.keyId,
            operation = handle.operation,
            intentFingerprint = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef",
            sessionId = handle.sessionId,
            nonce = handle.nonce,
            issuedAtMs = handle.issuedAtMs,
            expiresAtMs = handle.expiresAtMs,
            proofToken = handle.proofToken,
            walletId = handle.walletId
        )

        assertFalse(
            "Tampered intent fingerprint must fail HMAC verification",
            ProofTokenVerifier.verify(
                proofToken = tamperedDigestHandle.proofToken,
                keyId = tamperedDigestHandle.keyId,
                operation = tamperedDigestHandle.operation,
                intentFingerprint = tamperedDigestHandle.intentFingerprint,
                sessionId = tamperedDigestHandle.sessionId,
                nonce = tamperedDigestHandle.nonce,
                issuedAtMs = tamperedDigestHandle.issuedAtMs,
                expiresAtMs = tamperedDigestHandle.expiresAtMs,
                walletId = tamperedDigestHandle.walletId
            )
        )

        // Tampering token bits directly
        val rawToken = handle.proofToken
        val flippedToken = if (rawToken[0] == '0') "1" + rawToken.substring(1) else "0" + rawToken.substring(1)
        val bitFlippedHandle = PlatformAuthHandle(
            keyId = handle.keyId,
            operation = handle.operation,
            intentFingerprint = handle.intentFingerprint,
            sessionId = handle.sessionId,
            nonce = handle.nonce,
            issuedAtMs = handle.issuedAtMs,
            expiresAtMs = handle.expiresAtMs,
            proofToken = flippedToken,
            walletId = handle.walletId
        )
        assertFalse(
            "Bit-flipped token MUST fail isValid()",
            bitFlippedHandle.isValid(
                expectedKeyId = keyId,
                expectedIntentFingerprint = digest,
                expectedOperation = AuthOperation.SIGN,
                currentTimeMs = now,
                expectedWalletId = walletId
            )
        )
    }

    // =========================================================================
    // 4. Concurrency Stress Tests: 50 Concurrent Sign Requests
    // =========================================================================

    @Test
    fun challenge_50_concurrent_sign_requests_yield_exactly_1_success_and_49_failures() {
        val (manager, _, _) = createTestEnvironment()
        val keyId = "key_concurrent_sign_p1_2"
        val walletId = "wallet_concurrent_sign_001"
        val rawPrivKey = ByteArray(32) { i ->
            testPrivateKeyHexA.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }

        // Store key requiring authentication
        val importHandle = TestPlatformAuthenticator.issueHandle(
            keyId = keyId,
            operation = AuthOperation.IMPORT,
            walletId = walletId,
            validityDurationMs = 60_000L
        )
        val storeResult = runBlocking {
            manager.storePrivateKey(
                keyId = keyId,
                privateKey = rawPrivKey,
                requireAuth = true,
                authContext = AuthenticationContext(authHandle = importHandle),
                expectedWalletId = walletId
            )
        }
        assertTrue(storeResult is Result.Success)

        val txData = CryptoUtils.sha256("PR32-Round13-Concurrency-Test-Payload".encodeToByteArray())
        val signHandle = TestPlatformAuthenticator.issueHandle(
            keyId = keyId,
            operation = AuthOperation.SIGN,
            intentFingerprint = txData.toHexString(),
            walletId = walletId,
            validityDurationMs = 30_000L
        )

        val threadCount = 50
        val executor = Executors.newFixedThreadPool(threadCount)
        val startLatch = CountDownLatch(1)
        val finishLatch = CountDownLatch(threadCount)

        val results = ConcurrentLinkedQueue<Result<ByteArray>>()
        val successCount = AtomicInteger(0)
        val authFailureCount = AtomicInteger(0)

        for (i in 0 until threadCount) {
            executor.submit {
                try {
                    startLatch.await()
                    val result = runBlocking {
                        manager.signWithKey(
                            keyId = keyId,
                            data = txData,
                            authContext = AuthenticationContext(authHandle = signHandle),
                            expectedWalletId = walletId
                        )
                    }
                    results.add(result)
                    when (result) {
                        is Result.Success -> successCount.incrementAndGet()
                        is Result.Failure -> {
                            val ex = result.exception
                            if (ex is AuthenticationRequiredException || ex.cause is AuthenticationRequiredException) {
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

        startLatch.countDown()
        val finished = finishLatch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        assertTrue("All 50 threads must complete within timeout", finished)
        assertEquals("Total results collected must be 50", 50, results.size)
        assertEquals("Exactly 1 thread must succeed", 1, successCount.get())
        assertEquals("Exactly 49 threads must fail with auth rejection", 49, authFailureCount.get())

        // Validate generated signature
        val successSig = (results.first { it is Result.Success } as Result.Success<ByteArray>).data
        assertEquals(65, successSig.size)
    }

    // =========================================================================
    // 5. Concurrency Stress Tests: 50 Concurrent Deletion Requests (Grant Issuance)
    // =========================================================================

    @Test
    fun challenge_50_concurrent_deletion_grant_issuances_yield_exactly_1_success_and_49_failures() {
        val now = Clock.System.now().toEpochMilliseconds()
        val walletId = "wallet_concurrent_delete_001"
        val keyId = "key_concurrent_delete_001"

        val deleteHandle = TestPlatformAuthenticator.issueHandle(
            keyId = keyId,
            operation = AuthOperation.DELETE,
            walletId = walletId,
            expiresAtMs = now + 30_000L
        )

        val threadCount = 50
        val executor = Executors.newFixedThreadPool(threadCount)
        val startLatch = CountDownLatch(1)
        val finishLatch = CountDownLatch(threadCount)

        val successCount = AtomicInteger(0)
        val failureCount = AtomicInteger(0)
        val grants = ConcurrentLinkedQueue<DeletionAuthorizationGrant>()

        for (i in 0 until threadCount) {
            executor.submit {
                try {
                    startLatch.await()
                    val result = DeletionAuthorizationService.issueDeletionGrant(
                        handle = deleteHandle,
                        walletId = walletId,
                        keyAlias = keyId,
                        currentTimeMs = now
                    )
                    when (result) {
                        is Result.Success -> {
                            successCount.incrementAndGet()
                            grants.add(result.data)
                        }
                        is Result.Failure -> {
                            failureCount.incrementAndGet()
                        }
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

        assertTrue("All 50 threads must complete within timeout", finished)
        assertEquals("Exactly 1 thread must successfully obtain DeletionAuthorizationGrant", 1, successCount.get())
        assertEquals("Exactly 49 threads must be rejected", 49, failureCount.get())
        assertEquals("Exactly 1 grant minted", 1, grants.size)

        // Now test 50 concurrent consumptions of the minted Grant
        val grant = grants.first()
        val grantSuccessCount = AtomicInteger(0)
        val grantFailureCount = AtomicInteger(0)

        val grantExecutor = Executors.newFixedThreadPool(threadCount)
        val grantStartLatch = CountDownLatch(1)
        val grantFinishLatch = CountDownLatch(threadCount)

        for (i in 0 until threadCount) {
            grantExecutor.submit {
                try {
                    grantStartLatch.await()
                    val result = DeletionGrantRegistry.validateAndConsume(
                        grant = grant,
                        expectedKeyAlias = keyId,
                        currentTimeMs = now
                    )
                    when (result) {
                        is Result.Success -> grantSuccessCount.incrementAndGet()
                        is Result.Failure -> grantFailureCount.incrementAndGet()
                        else -> {}
                    }
                } finally {
                    grantFinishLatch.countDown()
                }
            }
        }

        grantStartLatch.countDown()
        val grantFinished = grantFinishLatch.await(10, TimeUnit.SECONDS)
        grantExecutor.shutdown()

        assertTrue("Grant consumption threads must finish within timeout", grantFinished)
        assertEquals("Exactly 1 thread must successfully consume Grant", 1, grantSuccessCount.get())
        assertEquals("Exactly 49 threads must fail to consume Grant (replay protection)", 49, grantFailureCount.get())
    }

    // =========================================================================
    // 6. Deletion Authorization Grant HMAC Tampering & Parameter Mismatch
    // =========================================================================

    @Test
    fun challenge_tampered_grant_fields_fail_hmac_and_registry_verification() {
        val now = Clock.System.now().toEpochMilliseconds()
        val walletId = "wallet-grant-01"
        val keyAlias = "key-grant-01"

        val handle = TestPlatformAuthenticator.issueHandle(
            keyId = keyAlias,
            operation = AuthOperation.DELETE,
            walletId = walletId,
            expiresAtMs = now + 60_000L
        )

        val grantResult = DeletionAuthorizationService.issueDeletionGrant(
            handle = handle,
            walletId = walletId,
            keyAlias = keyAlias,
            currentTimeMs = handle.issuedAtMs
        )
        assertTrue(grantResult is Result.Success)
        val genuineGrant = (grantResult as Result.Success).data

        // 1. Tampering walletId in Grant
        val tamperedWalletGrant = DeletionAuthorizationGrant(
            walletId = "wallet-tampered-99",
            keyAlias = genuineGrant.keyAlias,
            operation = genuineGrant.operation,
            originalAuthSessionId = genuineGrant.originalAuthSessionId,
            issuedAtMs = genuineGrant.issuedAtMs,
            expiresAtMs = genuineGrant.expiresAtMs,
            nonce = genuineGrant.nonce,
            proofToken = genuineGrant.proofToken
        )
        assertFalse(
            "Tampered walletId in Grant must fail HMAC verification",
            DeletionGrantVerifier.verify(tamperedWalletGrant)
        )
        val consumeResult1 = DeletionGrantRegistry.validateAndConsume(
            grant = tamperedWalletGrant,
            expectedKeyAlias = keyAlias,
            currentTimeMs = now
        )
        assertTrue("Consumption of tampered walletId grant MUST fail", consumeResult1 is Result.Failure)

        // 2. Tampering keyAlias in Grant
        val tamperedKeyGrant = DeletionAuthorizationGrant(
            walletId = genuineGrant.walletId,
            keyAlias = "key-victim-99",
            operation = genuineGrant.operation,
            originalAuthSessionId = genuineGrant.originalAuthSessionId,
            issuedAtMs = genuineGrant.issuedAtMs,
            expiresAtMs = genuineGrant.expiresAtMs,
            nonce = genuineGrant.nonce,
            proofToken = genuineGrant.proofToken
        )
        assertFalse(
            "Tampered keyAlias in Grant must fail HMAC verification",
            DeletionGrantVerifier.verify(tamperedKeyGrant)
        )
        val consumeResult2 = DeletionGrantRegistry.validateAndConsume(
            grant = tamperedKeyGrant,
            expectedKeyAlias = keyAlias,
            currentTimeMs = now
        )
        assertTrue("Consumption of tampered keyAlias grant MUST fail", consumeResult2 is Result.Failure)

        // 3. Tampering proofToken (bit flip)
        val tamperedToken = if (genuineGrant.proofToken[0] == '0') "1" + genuineGrant.proofToken.substring(1) else "0" + genuineGrant.proofToken.substring(1)
        val tamperedTokenGrant = DeletionAuthorizationGrant(
            walletId = genuineGrant.walletId,
            keyAlias = genuineGrant.keyAlias,
            operation = genuineGrant.operation,
            originalAuthSessionId = genuineGrant.originalAuthSessionId,
            issuedAtMs = genuineGrant.issuedAtMs,
            expiresAtMs = genuineGrant.expiresAtMs,
            nonce = genuineGrant.nonce,
            proofToken = tamperedToken
        )
        assertFalse(
            "Bit-flipped proofToken in Grant must fail HMAC verification",
            DeletionGrantVerifier.verify(tamperedTokenGrant)
        )
        val consumeResult3 = DeletionGrantRegistry.validateAndConsume(
            grant = tamperedTokenGrant,
            expectedKeyAlias = keyAlias,
            currentTimeMs = now
        )
        assertTrue("Consumption of tampered token grant MUST fail", consumeResult3 is Result.Failure)

        // 4. Finally, authentic grant successfully consumes once
        val validConsumeResult = DeletionGrantRegistry.validateAndConsume(
            grant = genuineGrant,
            expectedKeyAlias = keyAlias,
            currentTimeMs = now
        )
        assertTrue("Authentic grant must consume successfully", validConsumeResult is Result.Success)

        // 5. Immediate replay of genuine grant MUST fail
        val replayResult = DeletionGrantRegistry.validateAndConsume(
            grant = genuineGrant,
            expectedKeyAlias = keyAlias,
            currentTimeMs = now
        )
        assertTrue("Replay of consumed grant MUST fail", replayResult is Result.Failure)
    }
}

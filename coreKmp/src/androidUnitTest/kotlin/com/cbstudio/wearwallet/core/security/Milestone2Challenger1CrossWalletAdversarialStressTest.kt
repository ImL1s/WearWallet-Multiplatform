package com.cbstudio.wearwallet.core.security

import android.content.Context
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.context.ChainExecutionContextRegistry
import com.cbstudio.wearwallet.core.domain.model.context.NetworkType
import com.cbstudio.wearwallet.core.domain.model.intent.ConfirmedEvmTransactionIntent
import com.cbstudio.wearwallet.core.domain.model.quantities.*
import com.cbstudio.wearwallet.core.domain.repository.TransactionRepository
import com.cbstudio.wearwallet.core.domain.repository.WalletRepository
import com.cbstudio.wearwallet.core.domain.usecase.transaction.SendTransactionUseCase
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.core.platform.SecureStorage
import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Milestone 2 Challenger 1: Adversarial Cross-Wallet Concurrency & Isolation Stress Test
 *
 * Empirical verification of:
 * 1. 50+ Concurrent Threads Cross-Wallet Signing & Export Isolation.
 * 2. Strict rejection when expectedWalletId != handle.walletId / grant.walletId under identical keyAlias/fingerprint.
 * 3. Deletion Grant Cross-Wallet Replay & Tampering Rejection (100% fail-closed).
 * 4. Staging Session Cross-Wallet Collision Defense.
 * 5. SendTransactionUseCase pre-signing cross-wallet gate integrity.
 * 6. 50+ Concurrent Threads KeyPresence 4-state consistency under partial corruption.
 */
class Milestone2Challenger1CrossWalletAdversarialStressTest {

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

    // =========================================================================
    // 1. 50+ Concurrent Threads Cross-Wallet Signing Isolation Stress Test
    // =========================================================================
    @Test
    fun test_60_threads_concurrent_cross_wallet_signing_identical_keyAlias_100_percent_rejected() {
        val sharedKeyAlias = "shared_key_alias_100"
        val victimWalletId = "wallet_victim_001"
        val attackerWalletId = "wallet_attacker_999"

        val rawPrivKey = ByteArray(32) { i ->
            testPrivateKeyHex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }

        // 1. Store key in vault for victim wallet
        val importHandle = TestPlatformAuthenticator.issueHandle(
            keyId = sharedKeyAlias,
            operation = AuthOperation.IMPORT,
            intentFingerprint = "",
            validityDurationMs = 60_000L,
            walletId = victimWalletId
        )
        val storeResult = runBlocking {
            keyManager.storePrivateKey(
                keyId = sharedKeyAlias,
                privateKey = rawPrivKey,
                requireAuth = true,
                authContext = AuthenticationContext(authHandle = importHandle),
                expectedWalletId = victimWalletId
            )
        }
        assertTrue("Victim store key must succeed", storeResult is Result.Success)

        // 2. Prepare signing intent
        val dataToSign = CryptoUtils.sha256("Tx-Cross-Wallet-Payload-Data".encodeToByteArray())
        val digestHex = dataToSign.toHexString()

        // 3. Issue 60 distinct handles (some issued for victim, some for attacker)
        val threadCount = 60
        val handles = (0 until threadCount).map { index ->
            val assignedWallet = if (index % 2 == 0) victimWalletId else attackerWalletId
            TestPlatformAuthenticator.issueHandle(
                keyId = sharedKeyAlias,
                operation = AuthOperation.SIGN,
                intentFingerprint = digestHex,
                validityDurationMs = 60_000L,
                walletId = assignedWallet
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

                    // Cross-wallet attack: Thread attempts to sign using mismatched expectedWalletId
                    // If handle was for victim, thread claims attackerWalletId.
                    // If handle was for attacker, thread claims victimWalletId.
                    val assertedExpectedWalletId = if (handle.walletId == victimWalletId) attackerWalletId else victimWalletId

                    val result = runBlocking {
                        keyManager.signWithKey(
                            keyId = sharedKeyAlias,
                            data = dataToSign,
                            authContext = AuthenticationContext(authHandle = handle),
                            expectedWalletId = assertedExpectedWalletId
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

        assertTrue("All 60 threads must complete within timeout", finishedInTime)
        assertEquals("Exactly 0 threads must succeed in cross-wallet signing", 0, successCount.get())
        assertEquals("All 60 threads must be rejected with 100% failure rate", threadCount, rejectedCount.get())

        for (ex in failureExceptions) {
            assertTrue(
                "Exception must be AuthenticationRequiredException or SecurityException, got ${ex::class.simpleName}: ${ex.message}",
                ex is AuthenticationRequiredException || ex is SecurityException
            )
            assertTrue(
                "Message must mention cross-wallet rejection: '${ex.message}'",
                ex.message!!.contains("Cross-wallet") || ex.message!!.contains("walletId")
            )
        }
    }

    // =========================================================================
    // 2. 50+ Concurrent Threads Cross-Wallet Encrypted Key Export Stress Test
    // =========================================================================
    @Test
    fun test_60_threads_concurrent_cross_wallet_export_100_percent_rejected() {
        val sharedKeyAlias = "shared_export_key_alias"
        val victimWalletId = "wallet_victim_export"
        val attackerWalletId = "wallet_attacker_export"
        val backupPassword = "StrongBackupPassword123!".toCharArray()

        val rawPrivKey = ByteArray(32) { i ->
            testPrivateKeyHex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }

        // 1. Store key
        val importHandle = TestPlatformAuthenticator.issueHandle(
            keyId = sharedKeyAlias,
            operation = AuthOperation.IMPORT,
            intentFingerprint = "",
            validityDurationMs = 60_000L,
            walletId = victimWalletId
        )
        runBlocking {
            keyManager.storePrivateKey(
                keyId = sharedKeyAlias,
                privateKey = rawPrivKey,
                requireAuth = true,
                authContext = AuthenticationContext(authHandle = importHandle),
                expectedWalletId = victimWalletId
            )
        }

        // 2. Issue 60 EXPORT handles for victim wallet
        val threadCount = 60
        val exportHandles = (0 until threadCount).map {
            TestPlatformAuthenticator.issueHandle(
                keyId = sharedKeyAlias,
                operation = AuthOperation.EXPORT,
                intentFingerprint = "",
                validityDurationMs = 60_000L,
                walletId = victimWalletId
            )
        }

        val executor = Executors.newFixedThreadPool(threadCount)
        val startGun = CountDownLatch(1)
        val finishLatch = CountDownLatch(threadCount)

        val rejectedCount = AtomicInteger(0)
        val successCount = AtomicInteger(0)

        for (i in 0 until threadCount) {
            val handle = exportHandles[i]
            executor.submit {
                try {
                    startGun.await()

                    // Attacker claims expectedWalletId = attackerWalletId
                    val result = runBlocking {
                        keyManager.exportEncryptedKey(
                            keyId = sharedKeyAlias,
                            backupPassword = backupPassword,
                            authContext = AuthenticationContext(authHandle = handle),
                            expectedWalletId = attackerWalletId
                        )
                    }

                    when (result) {
                        is Result.Success<*> -> successCount.incrementAndGet()
                        is Result.Failure -> rejectedCount.incrementAndGet()
                        is Result.Loading<*> -> {}
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

        assertTrue("All 60 threads must complete within timeout", finishedInTime)
        assertEquals("0 threads must succeed in cross-wallet export", 0, successCount.get())
        assertEquals("All 60 threads must be rejected", threadCount, rejectedCount.get())
    }

    // =========================================================================
    // 3. Deletion Grant Single-Use Race & Cross-Wallet Replay (60 Threads)
    // =========================================================================
    @Test
    fun test_deletion_grant_60_threads_single_use_consumption_race_and_cross_wallet_rejection() {
        val keyId = "key_delete_race_60"
        val victimWalletId = "wallet_victim_delete_race"
        val attackerWalletId = "wallet_attacker_delete_race"

        val rawPrivKey = ByteArray(32) { i ->
            testPrivateKeyHex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }

        // 1. Store key
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

        // 2. Issue a single valid DeletionAuthorizationGrant for victim wallet
        val deleteHandle = TestPlatformAuthenticator.issueHandle(
            keyId = keyId,
            operation = AuthOperation.DELETE,
            intentFingerprint = "",
            validityDurationMs = 60_000L,
            walletId = victimWalletId
        )
        val grantResult = DeletionAuthorizationService.issueDeletionGrant(
            handle = deleteHandle,
            walletId = victimWalletId,
            keyAlias = keyId,
            currentTimeMs = System.currentTimeMillis()
        )
        assertTrue("Grant issuance must succeed", grantResult is Result.Success)
        val singleSharedGrant = (grantResult as Result.Success).data

        // 3. 60 threads race to consume this single grant:
        //    30 threads attempt cross-wallet hijack (expectedWalletId = attackerWalletId)
        //    30 threads attempt valid consumption (expectedWalletId = victimWalletId)
        val threadCount = 60
        val executor = Executors.newFixedThreadPool(threadCount)
        val startGun = CountDownLatch(1)
        val finishLatch = CountDownLatch(threadCount)

        val validSuccessCount = AtomicInteger(0)
        val validReplayRejectedCount = AtomicInteger(0)
        val attackerCrossWalletRejectedCount = AtomicInteger(0)

        for (i in 0 until threadCount) {
            val isAttackerThread = (i % 2 == 0)
            executor.submit {
                try {
                    startGun.await()

                    val targetWalletId = if (isAttackerThread) attackerWalletId else victimWalletId
                    val result = runBlocking {
                        keyManager.deletePrivateKeyWithGrant(
                            grant = singleSharedGrant,
                            expectedWalletId = targetWalletId
                        )
                    }

                    if (isAttackerThread) {
                        if (result is Result.Success) {
                            fail("Attacker thread MUST NOT succeed in deleting victim key with cross-wallet grant")
                        } else {
                            attackerCrossWalletRejectedCount.incrementAndGet()
                        }
                    } else {
                        if (result is Result.Success) {
                            validSuccessCount.incrementAndGet()
                        } else {
                            validReplayRejectedCount.incrementAndGet()
                        }
                    }
                } catch (t: Throwable) {
                    if (isAttackerThread) {
                        attackerCrossWalletRejectedCount.incrementAndGet()
                    } else {
                        validReplayRejectedCount.incrementAndGet()
                    }
                } finally {
                    finishLatch.countDown()
                }
            }
        }

        startGun.countDown()
        val finishedInTime = finishLatch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        assertTrue("All 60 threads must complete within timeout", finishedInTime)
        assertEquals("Exactly 1 legitimate thread must succeed in consuming the single-use grant", 1, validSuccessCount.get())
        assertEquals("Remaining 29 legitimate threads must be rejected due to single-use replay protection", 29, validReplayRejectedCount.get())
        assertEquals("All 30 attacker threads must be rejected due to cross-wallet mismatch", 30, attackerCrossWalletRejectedCount.get())

        // Post-condition: Key presence in vault must now be strictly Absent
        val finalPresence = runBlocking { keyManager.checkKeyPresence(keyId) }
        assertTrue("Key must be strictly Absent after successful deletion", finalPresence is KeyPresence.Absent)
    }

    // =========================================================================
    // 4. Staging Session Cross-Wallet Collision & Hijacking Defense
    // =========================================================================
    @Test
    fun test_staging_session_cross_wallet_collision_and_isolation() {
        val rawPrivKey = ByteArray(32) { i ->
            testPrivateKeyHex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }

        // Wallet A starts a provisioning session
        val sessionA = runBlocking { keyManager.startProvisioningSession() }
        val sessionIdA = sessionA.sessionId
        val stagedAliasA = sessionA.stagedKeyAlias

        // Attacker Wallet B attempts to store private key into session A with handle issued for wallet_B
        val attackerHandle = TestPlatformAuthenticator.issueHandle(
            keyId = stagedAliasA,
            operation = AuthOperation.IMPORT,
            intentFingerprint = "",
            validityDurationMs = 60_000L,
            sessionId = "attacker_session_fake",
            walletId = "wallet_attacker_beta"
        )

        val storeFailResult = runBlocking {
            keyManager.storeStagedPrivateKey(
                sessionId = sessionIdA,
                stagedKeyAlias = stagedAliasA,
                privateKey = rawPrivKey,
                requireAuth = true,
                authContext = AuthenticationContext(authHandle = attackerHandle)
            )
        }
        assertTrue("Storing staged key with mismatched session/wallet handle must fail", storeFailResult is Result.Failure)

        // Storing with legitimate handle for Session A succeeds
        val validHandleA = TestPlatformAuthenticator.issueHandle(
            keyId = stagedAliasA,
            operation = AuthOperation.IMPORT,
            intentFingerprint = "",
            validityDurationMs = 60_000L,
            sessionId = sessionIdA,
            walletId = sessionIdA
        )
        val validStoreResult = runBlocking {
            keyManager.storeStagedPrivateKey(
                sessionId = sessionIdA,
                stagedKeyAlias = stagedAliasA,
                privateKey = rawPrivKey,
                requireAuth = true,
                authContext = AuthenticationContext(authHandle = validHandleA)
            )
        }
        assertTrue("Valid staged key storage must succeed", validStoreResult is Result.Success)

        // Attacker attempts to rollback Session A using an illegitimate session object with same alias but fake sessionId
        val fakeSession = ProvisioningSession.create().apply {
            // fake session
        }
        val fakeRollbackResult = runBlocking {
            keyManager.rollbackProvisioningSession(fakeSession)
        }
        assertTrue("Rollback of unknown/fake session must fail", fakeRollbackResult is Result.Failure)

        // Session A key must still be Present in staged vault
        val presenceStaged = runBlocking { keyManager.checkKeyPresence(stagedAliasA) }
        assertTrue("Staged key must remain Present", presenceStaged is KeyPresence.Present)

        // Legitimate commit marks session committed
        val commitResult = runBlocking { keyManager.commitProvisioningSession(sessionA) }
        assertTrue("Commit session must succeed", commitResult is Result.Success)

        // Subsequent rollback attempt on committed session is rejected
        val lateRollback = runBlocking { keyManager.rollbackProvisioningSession(sessionA) }
        assertTrue("Rollback on committed session must be rejected", lateRollback is Result.Failure)
    }

    // =========================================================================
    // 5. Deletion Grant Tampering & Replay Adversarial Matrix
    // =========================================================================
    @Test
    fun test_deletion_grant_tampering_matrix_all_rejected() {
        val keyId = "key_tamper_matrix"
        val walletId = "wallet_tamper_target"

        val handle = TestPlatformAuthenticator.issueHandle(
            keyId = keyId,
            operation = AuthOperation.DELETE,
            intentFingerprint = "",
            validityDurationMs = 60_000L,
            walletId = walletId
        )

        val grantRes = DeletionAuthorizationService.issueDeletionGrant(
            handle = handle,
            walletId = walletId,
            keyAlias = keyId,
            currentTimeMs = System.currentTimeMillis()
        )
        assertTrue("Grant issuance must succeed", grantRes is Result.Success)
        val originalGrant = (grantRes as Result.Success).data

        // Case A: Tampered walletId in grant
        val tamperedWalletGrant = DeletionAuthorizationGrant(
            walletId = "wallet_tampered_attacker",
            keyAlias = originalGrant.keyAlias,
            operation = originalGrant.operation,
            originalAuthSessionId = originalGrant.originalAuthSessionId,
            issuedAtMs = originalGrant.issuedAtMs,
            expiresAtMs = originalGrant.expiresAtMs,
            nonce = originalGrant.nonce,
            proofToken = originalGrant.proofToken
        )
        val verifyWalletTamper = DeletionGrantVerifier.verify(tamperedWalletGrant)
        assertFalse("Tampered walletId in grant must fail HMAC verification", verifyWalletTamper)
        val consumeWalletTamper = DeletionGrantRegistry.validateAndConsume(tamperedWalletGrant, keyId, expectedWalletId = "wallet_tampered_attacker")
        assertTrue("Registry must reject tampered wallet grant", consumeWalletTamper is Result.Failure)

        // Case B: Tampered keyAlias in grant
        val tamperedKeyGrant = DeletionAuthorizationGrant(
            walletId = originalGrant.walletId,
            keyAlias = "key_other_victim",
            operation = originalGrant.operation,
            originalAuthSessionId = originalGrant.originalAuthSessionId,
            issuedAtMs = originalGrant.issuedAtMs,
            expiresAtMs = originalGrant.expiresAtMs,
            nonce = originalGrant.nonce,
            proofToken = originalGrant.proofToken
        )
        val verifyKeyTamper = DeletionGrantVerifier.verify(tamperedKeyGrant)
        assertFalse("Tampered keyAlias in grant must fail HMAC verification", verifyKeyTamper)

        // Case C: Tampered nonce in grant
        val tamperedNonceGrant = DeletionAuthorizationGrant(
            walletId = originalGrant.walletId,
            keyAlias = originalGrant.keyAlias,
            operation = originalGrant.operation,
            originalAuthSessionId = originalGrant.originalAuthSessionId,
            issuedAtMs = originalGrant.issuedAtMs,
            expiresAtMs = originalGrant.expiresAtMs,
            nonce = "tampered_nonce_9999",
            proofToken = originalGrant.proofToken
        )
        assertFalse("Tampered nonce must fail HMAC verification", DeletionGrantVerifier.verify(tamperedNonceGrant))

        // Case D: Expired Grant
        val expiredGrant = DeletionAuthorizationGrant(
            walletId = walletId,
            keyAlias = keyId,
            operation = AuthOperation.DELETE,
            originalAuthSessionId = originalGrant.originalAuthSessionId,
            issuedAtMs = System.currentTimeMillis() - 100_000L,
            expiresAtMs = System.currentTimeMillis() - 10_000L,
            nonce = CryptoUtils.randomBytes(16).toHexString(),
            proofToken = ""
        )
        assertTrue("Grant must be expired", expiredGrant.isExpired)

        // Case E: Unauthenticated grant cross-wallet replay
        val unauthGrantRes = DeletionAuthorizationService.issueUnauthenticatedGrant(
            walletId = "wallet_unauth_alice",
            keyAlias = "key_unauth_alice"
        )
        assertTrue("Unauthenticated grant issuance must succeed", unauthGrantRes is Result.Success)
        val unauthGrant = (unauthGrantRes as Result.Success).data
        val crossUnauthConsume = DeletionGrantRegistry.validateAndConsume(
            grant = unauthGrant,
            expectedKeyAlias = "key_unauth_alice",
            expectedWalletId = "wallet_unauth_bob"
        )
        assertTrue("Unauthenticated grant cross-wallet consumption must be rejected", crossUnauthConsume is Result.Failure)
    }

    // =========================================================================
    // 6. SendTransactionUseCase Pre-Signing Cross-Wallet Gate Integrity
    // =========================================================================
    @Test
    fun test_SendTransactionUseCase_cross_wallet_handle_rejected_before_signing() {
        runBlocking {
            val mockWalletRepo = mock<WalletRepository>()
            val mockTxRepo = mock<TransactionRepository>()
            val mockCryptoProvider = mock<CryptoProvider>()
            val mockSecureStorage = mock<SecureStorage>()
            val mockCapabilityGate = mock<CapabilityGate>()
            val mockKeyManager = mock<SecureKeyManager>()

            val useCase = SendTransactionUseCase(
                walletRepository = mockWalletRepo,
                transactionRepository = mockTxRepo,
                cryptoProvider = mockCryptoProvider,
                secureStorage = mockSecureStorage,
                capabilityGate = mockCapabilityGate,
                secureKeyManager = mockKeyManager
            )

            val victimWalletId = "wallet_alice_123"
            val attackerWalletId = "wallet_bob_999"
            val keyAlias = "key_alice"
            val executionContext = ChainExecutionContextRegistry.resolve(MultiChainType.ETHEREUM, NetworkType.MAINNET)

            val senderAddr = EvmAddress.fromString("0x1111111111111111111111111111111111111111")
            val recipientAddr = EvmAddress.fromString("0x2222222222222222222222222222222222222222")
            val amount = "1.0"
            val baseUnitAmount = BaseUnitAmount.fromDecimalString(amount, 18)
            val nonce = Nonce.fromLong(0L)
            val gasPrice = Wei.fromWeiDecimal("20000000000")
            val gasLimit = GasLimit.fromDecimalString("21000")
            val fee = Wei.fromWei(gasPrice.value * BigInteger.fromLong(gasLimit.toLong()))

            val fingerprint = ConfirmedEvmTransactionIntent.createFingerprint(
                walletId = victimWalletId,
                keyAlias = keyAlias,
                sender = senderAddr,
                chain = MultiChainType.ETHEREUM,
                executionContext = executionContext,
                envelopeType = EvmEnvelope.LEGACY,
                recipient = recipientAddr,
                tokenContract = null,
                tokenSymbol = null,
                tokenDecimals = null,
                humanAmount = amount,
                baseUnitAmount = baseUnitAmount,
                nativeValue = Wei.fromWei(baseUnitAmount.value),
                calldata = Calldata.EMPTY,
                nonce = nonce,
                gasPrice = gasPrice,
                gasLimit = gasLimit,
                fee = fee
            )

            val intent = ConfirmedEvmTransactionIntent(
                walletId = victimWalletId,
                keyAlias = keyAlias,
                sender = senderAddr,
                chain = MultiChainType.ETHEREUM,
                executionContext = executionContext,
                envelopeType = EvmEnvelope.LEGACY,
                recipient = recipientAddr,
                tokenContract = null,
                tokenSymbol = null,
                tokenDecimals = null,
                humanAmount = amount,
                baseUnitAmount = baseUnitAmount,
                nativeValue = Wei.fromWei(baseUnitAmount.value),
                calldata = Calldata.EMPTY,
                nonce = nonce,
                gasPrice = gasPrice,
                gasLimit = gasLimit,
                fee = fee,
                canonicalFingerprint = fingerprint
            )

            // Issue AuthHandle bound to attacker wallet ID
            val crossWalletHandle = TestPlatformAuthenticator.issueHandle(
                keyId = keyAlias,
                operation = AuthOperation.SIGN,
                intentFingerprint = intent.signingDigestHex,
                validityDurationMs = 60_000L,
                walletId = attackerWalletId
            )

            val result = useCase(intent, AuthenticationContext(authHandle = crossWalletHandle)).first()
            assertTrue("SendTransactionUseCase must fail on cross-wallet auth handle", result is Result.Failure)
            val failure = result as Result.Failure
            assertTrue(
                "Failure must be AuthenticationRequiredException with cross-wallet message, got ${failure.exception.message}",
                failure.exception is AuthenticationRequiredException && failure.exception.message!!.contains("Cross-wallet")
            )

            // Verify that SecureKeyManager.signWithKey was NEVER called (Fail-closed pre-signing defense)
            verify(mockKeyManager, never()).signWithKey(any(), any(), any(), any())
        }
    }

    // =========================================================================
    // 7. 50+ Concurrent Threads KeyPresence Strict Consistency Under Race Conditions
    // =========================================================================
    @Test
    fun test_50_threads_concurrent_key_presence_consistency_under_sequential_corruption() {
        val keyId = "key_presence_race_50"
        val walletId = "wallet_presence_race"

        val rawPrivKey = ByteArray(32) { i ->
            testPrivateKeyHex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }

        // Store key
        val importHandle = TestPlatformAuthenticator.issueHandle(
            keyId = keyId,
            operation = AuthOperation.IMPORT,
            intentFingerprint = "",
            validityDurationMs = 60_000L,
            walletId = walletId
        )
        runBlocking {
            keyManager.storePrivateKey(
                keyId = keyId,
                privateKey = rawPrivKey,
                requireAuth = true,
                authContext = AuthenticationContext(authHandle = importHandle),
                expectedWalletId = walletId
            )
        }

        val threadCount = 50

        // Phase 1: All 50 threads query Present
        run {
            val executor = Executors.newFixedThreadPool(threadCount)
            val latch = CountDownLatch(threadCount)
            val presentCount = AtomicInteger(0)
            for (i in 0 until threadCount) {
                executor.submit {
                    try {
                        val presence = runBlocking { keyManager.checkKeyPresence(keyId) }
                        if (presence is KeyPresence.Present) presentCount.incrementAndGet()
                    } finally {
                        latch.countDown()
                    }
                }
            }
            assertTrue("Phase 1 must complete", latch.await(10, TimeUnit.SECONDS))
            executor.shutdown()
            assertEquals("All 50 threads must observe Present", threadCount, presentCount.get())
        }

        // Phase 2: Corrupt IV tuple -> All 50 threads query Partial
        sharedPrefs.edit().remove(keyId + "_iv").commit()
        run {
            val executor = Executors.newFixedThreadPool(threadCount)
            val latch = CountDownLatch(threadCount)
            val partialCount = AtomicInteger(0)
            for (i in 0 until threadCount) {
                executor.submit {
                    try {
                        val presence = runBlocking { keyManager.checkKeyPresence(keyId) }
                        if (presence is KeyPresence.Partial) partialCount.incrementAndGet()
                    } finally {
                        latch.countDown()
                    }
                }
            }
            assertTrue("Phase 2 must complete", latch.await(10, TimeUnit.SECONDS))
            executor.shutdown()
            assertEquals("All 50 threads must observe Partial when IV missing", threadCount, partialCount.get())
        }

        // Phase 3: Corrupt ciphertext and tag -> All 50 threads query Partial
        sharedPrefs.edit().remove(keyId).remove(keyId + "_tag").commit()
        run {
            val executor = Executors.newFixedThreadPool(threadCount)
            val latch = CountDownLatch(threadCount)
            val partialCount = AtomicInteger(0)
            for (i in 0 until threadCount) {
                executor.submit {
                    try {
                        val presence = runBlocking { keyManager.checkKeyPresence(keyId) }
                        if (presence is KeyPresence.Partial) partialCount.incrementAndGet()
                    } finally {
                        latch.countDown()
                    }
                }
            }
            assertTrue("Phase 3 must complete", latch.await(10, TimeUnit.SECONDS))
            executor.shutdown()
            assertEquals("All 50 threads must observe Partial when only 2 tuples present", threadCount, partialCount.get())
        }

        // Phase 4: Wipe remaining tuples -> All 50 threads query Absent
        sharedPrefs.edit().remove(keyId + "_require_auth").commit()
        backend.entries.remove("wallet_key_$keyId")
        run {
            val executor = Executors.newFixedThreadPool(threadCount)
            val latch = CountDownLatch(threadCount)
            val absentCount = AtomicInteger(0)
            for (i in 0 until threadCount) {
                executor.submit {
                    try {
                        val presence = runBlocking { keyManager.checkKeyPresence(keyId) }
                        if (presence is KeyPresence.Absent) absentCount.incrementAndGet()
                    } finally {
                        latch.countDown()
                    }
                }
            }
            assertTrue("Phase 4 must complete", latch.await(10, TimeUnit.SECONDS))
            executor.shutdown()
            assertEquals("All 50 threads must observe Absent when all tuples wiped", threadCount, absentCount.get())
        }
    }
}

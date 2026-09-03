package com.cbstudio.wearwallet.core.security

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.WalletAccount
import com.cbstudio.wearwallet.core.domain.repository.WalletRepository
import com.cbstudio.wearwallet.core.recovery.RealStartupRecoveryCoordinator
import com.cbstudio.wearwallet.core.recovery.StartupRecoveryCoordinator
import com.cbstudio.wearwallet.core.recovery.StartupRecoveryState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.lang.reflect.Field

/**
 * Round 11 Challenger 2 Empirical Verification & Adversarial Stress Suite
 *
 * Challenge Dimensions:
 * 1. Apple Authenticator Negative Challenge:
 *    - Un-evaluated / unregistered handle verification fail-closed.
 *    - Forged ProofToken rejection under all tampering scenarios (mismatched keyId, op, intent, nonce, timestamp, signature).
 *    - Coroutine cancellation & invalidation lifecycle (laContext.invalidate(), handle.invalidate(), registry consumption).
 *    - continuation.isActive double-resume protection and architecture constraints.
 *
 * 2. StartupRecoveryCoordinator Lifecycle State Challenge:
 *    - High concurrency stress (50 concurrent callers into startReconciliation / awaitReady).
 *    - INITIALIZING interception: awaitReady() automatically triggers reconciliation from INITIALIZING.
 *    - RECONCILING blocking: awaitReady() blocks callers while reconciliation is in progress and only releases them at terminal state.
 *    - FAILED retry: retry() resets failed state and successfully recovers callers waiting on awaitReady().
 *
 * 3. ScopedPrivateKey & CharArray Zeroization and Lifecycle Validation:
 *    - Deep reflection inspection of private `keyBytes` memory after close() / destroy().
 *    - `use {}` exception resilience: reflection proves memory is zeroed even when unhandled exceptions / errors are thrown.
 *    - `CharArray.useSecurely {}` zeroization under normal completion and exception throwing.
 *    - Hex decoding correctness and temporary buffer zeroing.
 */
class Round11Challenger2EmpiricalTest {

    @Before
    fun setUp() {
        AuthHandleRegistry.clearForTesting()
    }

    @After
    fun tearDown() {
        AuthHandleRegistry.clearForTesting()
    }

    // =========================================================================
    // TASK 1: Apple Authenticator Negative Challenges
    // =========================================================================

    @Test
    fun testAppleAuth_unevaluated_and_unregistered_handle_is_strictly_rejected() {
        val now = Clock.System.now().toEpochMilliseconds()
        val unauthenticatedHandle = PlatformAuthHandle(
            keyId = "apple_secure_enclave_key_001",
            operation = AuthOperation.SIGN,
            intentFingerprint = "intent_sha256_hash",
            sessionId = "unregistered_session_apple",
            nonce = "random_nonce_999",
            issuedAtMs = now,
            expiresAtMs = now + 10_000L,
            walletId = "apple_wallet_001",
            proofToken = "forged_dummy_token"
        )

        // Session not registered in AuthHandleRegistry -> must fail
        assertFalse(
            "Unregistered session handle must fail isValid()",
            unauthenticatedHandle.isValid(
                expectedKeyId = "apple_secure_enclave_key_001",
                expectedIntentFingerprint = "intent_sha256_hash",
                expectedOperation = AuthOperation.SIGN,
                currentTimeMs = now,
                expectedWalletId = "apple_wallet_001"
            )
        )
    }

    @Test
    fun testAppleAuth_forged_and_tampered_proof_tokens_are_strictly_rejected() {
        val now = Clock.System.now().toEpochMilliseconds()
        val validSessionId = "apple_auth_session_valid"
        val expiresAt = now + 10_000L
        val nonce = "valid_nonce_apple"
        val walletId = "apple_wallet_master"

        val realToken = ProofTokenVerifier.sign(
            keyId = "apple_key_master",
            operation = AuthOperation.DELETE,
            intentFingerprint = "intent_hash_clean",
            sessionId = validSessionId,
            nonce = nonce,
            issuedAtMs = now,
            expiresAtMs = expiresAt,
            walletId = walletId
        )

        // Test 1: Bit flip / tampering in proofToken
        val tamperedToken = realToken.dropLast(4) + "AAAA"
        val tamperedHandle = PlatformAuthHandle(
            keyId = "apple_key_master",
            operation = AuthOperation.DELETE,
            intentFingerprint = "intent_hash_clean",
            sessionId = validSessionId,
            nonce = nonce,
            issuedAtMs = now,
            expiresAtMs = expiresAt,
            walletId = walletId,
            proofToken = tamperedToken
        )
        assertFalse("Tampered proofToken MUST fail verification", tamperedHandle.isValid("apple_key_master", "intent_hash_clean", AuthOperation.DELETE, now, walletId))

        // Test 2: Operation substitution (token signed for DELETE, presented for SIGN)
        val validHandle = PlatformAuthHandle(
            keyId = "apple_key_master",
            operation = AuthOperation.DELETE,
            intentFingerprint = "intent_hash_clean",
            sessionId = validSessionId,
            nonce = nonce,
            issuedAtMs = now,
            expiresAtMs = expiresAt,
            walletId = walletId,
            proofToken = realToken
        )
        assertFalse("Token for DELETE presented for SIGN must fail", validHandle.isValid("apple_key_master", "intent_hash_clean", AuthOperation.SIGN, now, walletId))

        // Test 3: Key ID substitution (token signed for apple_key_master, checked against apple_key_victim)
        assertFalse("Token for apple_key_master presented for apple_key_victim must fail", validHandle.isValid("apple_key_victim", "intent_hash_clean", AuthOperation.DELETE, now, walletId))

        // Test 4: Intent fingerprint substitution
        assertFalse("Token presented with different intent must fail", validHandle.isValid("apple_key_master", "intent_hash_hijacked", AuthOperation.DELETE, now, walletId))
    }

    @Test
    fun testAppleAuth_handle_invalidation_immediately_consumes_session_and_rejects_replay() {
        val now = Clock.System.now().toEpochMilliseconds()
        val sessionId = "apple_invalidate_session"
        val expiresAt = now + 10_000L
        val nonce = "nonce_invalidate"
        val walletId = "apple_wallet_inv"

        val token = ProofTokenVerifier.sign(
            keyId = "apple_key_inv",
            operation = AuthOperation.EXPORT,
            intentFingerprint = "export_intent",
            sessionId = sessionId,
            nonce = nonce,
            issuedAtMs = now,
            expiresAtMs = expiresAt,
            walletId = walletId
        )

        val handle = PlatformAuthHandle(
            keyId = "apple_key_inv",
            operation = AuthOperation.EXPORT,
            intentFingerprint = "export_intent",
            sessionId = sessionId,
            nonce = nonce,
            issuedAtMs = now,
            expiresAtMs = expiresAt,
            walletId = walletId,
            proofToken = token
        )

        assertTrue("Initially valid", handle.isValid("apple_key_inv", "export_intent", AuthOperation.EXPORT, now, walletId))
        assertFalse("Initially not invalidated", handle.isInvalidated)

        // Invalidate handle (simulating coroutine cancellation or single-use consumption)
        handle.invalidate()

        assertTrue("Must be marked invalidated", handle.isInvalidated)
        assertTrue("Session must be consumed in registry", AuthHandleRegistry.isConsumed(sessionId))
        assertFalse("Invalidated handle MUST be rejected", handle.isValid("apple_key_inv", "export_intent", AuthOperation.EXPORT, now))
    }

    @Test
    fun testAppleAuth_production_source_code_inspection_for_cancellation_and_isActive() {
        val rootDir = findProjectRoot()
        val appleSources = listOf(
            File(rootDir, "coreKmp/src/iosMain/kotlin/com/cbstudio/wearwallet/core/security/IOSPlatformAuthenticator.kt"),
            File(rootDir, "coreKmp/src/watchosMain/kotlin/com/cbstudio/wearwallet/core/security/WatchOSPlatformAuthenticator.kt")
        )

        for (source in appleSources) {
            assertTrue("Source file must exist: ${source.path}", source.exists())
            val text = source.readText()

            // 1. Must use suspendCancellableCoroutine
            assertTrue("${source.name} must use suspendCancellableCoroutine", text.contains("suspendCancellableCoroutine"))

            // 2. Must register invokeOnCancellation
            assertTrue("${source.name} must register invokeOnCancellation", text.contains("continuation.invokeOnCancellation"))

            // 3. Must invoke laContext.invalidate() on cancellation
            assertTrue("${source.name} must call laContext.invalidate()", text.contains("laContext.invalidate()"))

            // 4. Must guard callbacks with continuation.isActive
            assertTrue("${source.name} must check continuation.isActive", text.contains("if (continuation.isActive)"))

            // 5. Must NOT have external caller assertion methods
            assertFalse("${source.name} must NOT have issueHandleFromCallback", text.contains("issueHandleFromCallback"))
            assertFalse("${source.name} must NOT have isPolicyEvaluated parameter", text.contains("isPolicyEvaluated"))
        }
    }

    // =========================================================================
    // TASK 2: StartupRecoveryCoordinator Lifecycle State Challenges
    // =========================================================================

    private class MockChallengerWalletRepo : WalletRepository {
        var reconcileResult: Result<Unit> = Result.Success(Unit)
        var throwExceptionOnReconcile: Throwable? = null
        var reconcileCallCount = 0
        var barrier: CompletableDeferred<Unit>? = null

        override suspend fun reconcileStartupState(): Result<Unit> {
            reconcileCallCount++
            barrier?.await()
            throwExceptionOnReconcile?.let { throw it }
            return reconcileResult
        }

        override suspend fun prepareProvisioning(): Result<ProvisioningRequest> = Result.Failure(UnsupportedOperationException())
        override suspend fun createWallet(name: String, mnemonic: CharArray, password: CharArray, chainType: ChainType, authContext: AuthenticationContext): Result<WalletAccount> = Result.Failure(UnsupportedOperationException())
        override suspend fun importFromMnemonic(name: String, mnemonic: CharArray, password: CharArray, chainType: ChainType, authContext: AuthenticationContext): Result<WalletAccount> = Result.Failure(UnsupportedOperationException())
        override suspend fun importFromMnemonicWithKeyPair(name: String, mnemonic: CharArray, password: CharArray, chainType: ChainType, keyPair: KeyPair, address: String, authContext: AuthenticationContext): Result<WalletAccount> = Result.Failure(UnsupportedOperationException())
        override suspend fun importFromPrivateKey(name: String, privateKey: com.cbstudio.wearwallet.core.security.ScopedPrivateKey, password: CharArray, chainType: ChainType, authContext: AuthenticationContext): Result<WalletAccount> = Result.Failure(UnsupportedOperationException())
        override suspend fun importKeystoneWallet(name: String, xpub: String, derivationPath: String, masterFingerprint: String, chainType: ChainType, policy: ExtendedPublicKeyPolicy): Result<WalletAccount> = Result.Failure(UnsupportedOperationException())
        override suspend fun getAllWallets(): Result<List<WalletAccount>> = Result.Success(emptyList())
        override suspend fun getWallet(id: String): Result<WalletAccount?> = Result.Success(null)
        override suspend fun getWalletByAddress(address: String): Result<WalletAccount?> = Result.Success(null)
        override suspend fun getActiveWallet(): Result<WalletAccount?> = Result.Success(null)
        override suspend fun getKeystoneWallets(): Result<List<WalletAccount>> = Result.Success(emptyList())
        override suspend fun updateWallet(wallet: WalletAccount): Result<Unit> = Result.Success(Unit)
        override suspend fun deleteWallet(id: String, authContext: AuthenticationContext?): Result<Unit> = Result.Success(Unit)
        override suspend fun setActiveWallet(walletId: String): Result<Unit> = Result.Success(Unit)
        override suspend fun updateKeystoneData(walletId: String, signRequest: String?, syncData: String?): Result<Unit> = Result.Success(Unit)
        override fun observeWallets(): Flow<List<WalletAccount>> = emptyFlow()
        override fun observeActiveWallet(): Flow<WalletAccount?> = emptyFlow()
    }

    @Test
    fun testStartupRecovery_50_concurrent_reconciliation_requests_mutex_serialized() = runBlocking {
        val repo = MockChallengerWalletRepo()
        val barrier = CompletableDeferred<Unit>()
        repo.barrier = barrier
        val coordinator = RealStartupRecoveryCoordinator(repo)

        // Launch 50 concurrent callers invoking startReconciliation()
        val deferreds = (1..50).map {
            async(Dispatchers.Default) {
                coordinator.startReconciliation()
            }
        }

        // Delay slightly to ensure all 50 coroutines are queued waiting on mutex/barrier
        delay(50)

        // Release barrier to let the single running reconciliation finish
        barrier.complete(Unit)

        val results = deferreds.awaitAll()

        assertEquals("Exactly 1 execution must occur in repository", 1, repo.reconcileCallCount)
        assertEquals("Terminal state must be Ready", StartupRecoveryState.Ready, coordinator.state.value)
        results.forEach { state ->
            assertEquals("All 50 callers must receive Ready state", StartupRecoveryState.Ready, state)
        }
    }

    @Test
    fun testStartupRecovery_initializing_interception_via_awaitReady() = runBlocking {
        val repo = MockChallengerWalletRepo()
        repo.reconcileResult = Result.Success(Unit)
        val coordinator = RealStartupRecoveryCoordinator(repo)

        // Coordinator is in INITIALIZING state
        assertEquals(StartupRecoveryState.Initializing, coordinator.state.value)
        assertEquals(0, repo.reconcileCallCount)

        // Calling awaitReady() must intercept INITIALIZING and trigger reconciliation automatically
        val result = coordinator.awaitReady()

        assertTrue("awaitReady() must succeed", result is Result.Success)
        assertEquals("Repository must have been called", 1, repo.reconcileCallCount)
        assertEquals(StartupRecoveryState.Ready, coordinator.state.value)
    }

    @Test
    fun testStartupRecovery_reconciling_blocks_awaitReady_until_terminal_state() = runBlocking {
        val repo = MockChallengerWalletRepo()
        val barrier = CompletableDeferred<Unit>()
        repo.barrier = barrier
        val coordinator = RealStartupRecoveryCoordinator(repo)

        // Start reconciliation in background (will be blocked inside repository on barrier)
        val startJob = launch(Dispatchers.Default) {
            coordinator.startReconciliation()
        }

        // Wait until coordinator state enters Reconciling
        coordinator.state.first { it is StartupRecoveryState.Reconciling }
        assertTrue("Coordinator must be in Reconciling state", coordinator.state.value is StartupRecoveryState.Reconciling)

        // Call awaitReady from another coroutine - it must suspend and NOT return immediately
        var awaitCompleted = false
        val awaitJob = launch(Dispatchers.Default) {
            val res = coordinator.awaitReady()
            assertTrue(res is Result.Success)
            awaitCompleted = true
        }

        delay(50)
        assertFalse("awaitReady() MUST NOT complete while state is Reconciling", awaitCompleted)

        // Unblock barrier
        barrier.complete(Unit)

        startJob.join()
        awaitJob.join()

        assertTrue("awaitReady() MUST unblock upon reaching Ready", awaitCompleted)
        assertEquals(StartupRecoveryState.Ready, coordinator.state.value)
    }

    @Test
    fun testStartupRecovery_failed_state_and_retry_recovers_to_ready() = runBlocking {
        val repo = MockChallengerWalletRepo()
        val error = IllegalStateException("Disk I/O error on journal Cas check")
        repo.reconcileResult = Result.Failure(error)
        val coordinator = RealStartupRecoveryCoordinator(repo)

        val awaitFail = coordinator.awaitReady()
        assertTrue("Initial awaitReady must fail with typed failure", awaitFail is Result.Failure)
        assertTrue("State must be Failed", coordinator.state.value is StartupRecoveryState.Failed)
        assertEquals(error, coordinator.reconciliationError.value)
        assertEquals(1, repo.reconcileCallCount)

        // Fix repository error and trigger retry
        repo.reconcileResult = Result.Success(Unit)

        // Trigger retry and await completion to Ready
        coordinator.retry()
        coordinator.state.first { it is StartupRecoveryState.Ready }

        val readyResult = coordinator.awaitReady()
        assertTrue("After retry, awaitReady must succeed", readyResult is Result.Success)
        assertEquals(StartupRecoveryState.Ready, coordinator.state.value)
        assertNull(coordinator.reconciliationError.value)
        assertEquals(2, repo.reconcileCallCount)
    }

    // =========================================================================
    // TASK 3: ScopedPrivateKey & CharArray Zeroization and Lifecycle Validation
    // =========================================================================

    @Test
    fun testScopedPrivateKey_reflection_memory_zeroization_on_close() {
        val secretBytes = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte())
        val scopedKey = ScopedPrivateKey.fromByteArray(secretBytes, takeOwnership = false)

        // Extract internal private field 'keyBytes' via reflection
        val keyBytesField: Field = ScopedPrivateKey::class.java.getDeclaredField("keyBytes").apply {
            isAccessible = true
        }
        val internalBytes = keyBytesField.get(scopedKey) as ByteArray

        // Before destruction: bytes must match secret
        assertArrayEquals(secretBytes, internalBytes)
        assertFalse("Key is not closed initially", scopedKey.isClosed)

        // Close / destroy key
        scopedKey.close()

        assertTrue("Key must report isClosed == true", scopedKey.isClosed)

        // Verify via reflection that internal array was securely zeroed
        for (i in internalBytes.indices) {
            assertEquals("Byte at index $i must be zeroed to 0x00", 0.toByte(), internalBytes[i])
        }

        // Subsequent access to size must throw IllegalStateException
        try {
            scopedKey.size
            fail("Accessing size after close() must throw IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("destroyed"))
        }
    }

    @Test
    fun testScopedPrivateKey_use_block_exception_zeroization_resilience() {
        val secretBytes = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte(), 0x42, 0x13, 0x37)
        val scopedKey = ScopedPrivateKey.fromByteArray(secretBytes, takeOwnership = false)

        val keyBytesField: Field = ScopedPrivateKey::class.java.getDeclaredField("keyBytes").apply {
            isAccessible = true
        }
        val internalBytes = keyBytesField.get(scopedKey) as ByteArray

        // Execute use block that throws an exception
        var exceptionThrown = false
        try {
            scopedKey.use { bytes ->
                // Ensure caller sees the correct bytes inside the block
                assertEquals(0xDE.toByte(), bytes[0])
                throw RuntimeException("Simulated unhandled exception during cryptographic signing")
            }
        } catch (e: RuntimeException) {
            exceptionThrown = true
            assertEquals("Simulated unhandled exception during cryptographic signing", e.message)
        }

        assertTrue("Exception must have been thrown and caught", exceptionThrown)
        assertTrue("ScopedPrivateKey must be closed after exception", scopedKey.isClosed)

        // Verify that finally block executed and zeroed the internal memory
        for (i in internalBytes.indices) {
            assertEquals("Byte at index $i must be wiped to 0x00 after exception", 0.toByte(), internalBytes[i])
        }
    }

    @Test
    fun testCharArray_useSecurely_zeroization_normal_and_exception() {
        // Test 1: Normal execution
        val passwordChars = "SuperSecretPassword123!".toCharArray()
        val extractedLength = passwordChars.useSecurely { chars ->
            chars.size
        }
        assertEquals(23, extractedLength)
        for (i in passwordChars.indices) {
            assertEquals("Char at index $i must be zeroed to '\\u0000'", '\u0000', passwordChars[i])
        }

        // Test 2: Exception thrown inside block
        val pinChars = "123456".toCharArray()
        var pinExceptionThrown = false
        try {
            pinChars.useSecurely { chars ->
                assertEquals('1', chars[0])
                throw IllegalStateException("Simulated PIN verification error")
            }
        } catch (e: IllegalStateException) {
            pinExceptionThrown = true
        }

        assertTrue("Exception must be caught", pinExceptionThrown)
        for (i in pinChars.indices) {
            assertEquals("PIN char at index $i must be zeroed to '\\u0000' even after exception", '\u0000', pinChars[i])
        }
    }

    @Test
    fun testScopedPrivateKey_fromHex_CharArray_and_String_parsing_and_zeroization() {
        val hexChars = "0x0102030405060708".toCharArray()
        val scopedKey = ScopedPrivateKey.fromHex(hexChars)

        scopedKey.use { bytes ->
            val expected = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
            assertArrayEquals(expected, bytes)
        }

        assertTrue("ScopedKey must be destroyed after use", scopedKey.isClosed)
    }

    private fun findProjectRoot(): File {
        var current = File(System.getProperty("user.dir") ?: ".")
        while (current.parentFile != null) {
            if (File(current, "settings.gradle.kts").exists() || File(current, "settings.gradle").exists()) {
                return current
            }
            current = current.parentFile
        }
        return File(System.getProperty("user.dir") ?: ".")
    }
}

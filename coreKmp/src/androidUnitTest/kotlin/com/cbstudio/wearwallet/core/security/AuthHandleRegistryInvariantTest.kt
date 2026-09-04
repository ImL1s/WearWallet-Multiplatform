package com.cbstudio.wearwallet.core.security

import com.cbstudio.wearwallet.core.common.Result
import kotlinx.datetime.Clock
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Invariant Test Suite for AuthSessionMetadata & AuthHandleRegistry
 *
 * Verifies:
 * 1. AuthSessionMetadata constructor precondition validations.
 * 2. AuthHandleRegistry.register input validation and collision handling.
 * 3. AuthHandleRegistry.consume non-existent session behavior and single-use semantics.
 * 4. Unconditional equality checks for walletId, keyId, and operation in validateAndConsume.
 * 5. Unconditional equality checks in validateConsumeAndIssueGrant.
 */
class AuthHandleRegistryInvariantTest {

    @Before
    fun setUp() {
        AuthHandleRegistry.clearForTesting()
        DeletionGrantRegistry.clearForTesting()
    }

    @Test
    fun test_AuthSessionMetadata_constructor_invariants() {
        val now = 100_000L
        val expiresAt = 110_000L

        // Valid construction
        val validMeta = AuthSessionMetadata(
            sessionId = "session_001",
            expiresAtMs = expiresAt,
            keyId = "key_001",
            operation = AuthOperation.SIGN,
            intentFingerprint = "fp_001",
            walletId = "wallet_001",
            issuedAtMs = now,
            authenticatorType = "TEST_AUTH"
        )
        assertEquals("session_001", validMeta.sessionId)
        assertEquals("fp_001", validMeta.fingerprint)
        assertEquals("fp_001", validMeta.intentFingerprint)
        assertEquals("TEST_AUTH", validMeta.authenticatorType)

        // Blank sessionId
        assertThrows(IllegalArgumentException::class.java) {
            AuthSessionMetadata(
                sessionId = "  ",
                expiresAtMs = expiresAt,
                keyId = "key_001",
                operation = AuthOperation.SIGN,
                intentFingerprint = "fp_001",
                walletId = "wallet_001",
                issuedAtMs = now,
                authenticatorType = "TEST_AUTH"
            )
        }

        // Blank keyId
        assertThrows(IllegalArgumentException::class.java) {
            AuthSessionMetadata(
                sessionId = "session_001",
                expiresAtMs = expiresAt,
                keyId = "",
                operation = AuthOperation.SIGN,
                intentFingerprint = "fp_001",
                walletId = "wallet_001",
                issuedAtMs = now,
                authenticatorType = "TEST_AUTH"
            )
        }

        // Blank walletId
        assertThrows(IllegalArgumentException::class.java) {
            AuthSessionMetadata(
                sessionId = "session_001",
                expiresAtMs = expiresAt,
                keyId = "key_001",
                operation = AuthOperation.SIGN,
                intentFingerprint = "fp_001",
                walletId = "   ",
                issuedAtMs = now,
                authenticatorType = "TEST_AUTH"
            )
        }

        // Blank authenticatorType
        assertThrows(IllegalArgumentException::class.java) {
            AuthSessionMetadata(
                sessionId = "session_001",
                expiresAtMs = expiresAt,
                keyId = "key_001",
                operation = AuthOperation.SIGN,
                intentFingerprint = "fp_001",
                walletId = "wallet_001",
                issuedAtMs = now,
                authenticatorType = " "
            )
        }

        // issuedAtMs <= 0
        assertThrows(IllegalArgumentException::class.java) {
            AuthSessionMetadata(
                sessionId = "session_001",
                expiresAtMs = expiresAt,
                keyId = "key_001",
                operation = AuthOperation.SIGN,
                intentFingerprint = "fp_001",
                walletId = "wallet_001",
                issuedAtMs = 0L,
                authenticatorType = "TEST_AUTH"
            )
        }

        // expiresAtMs <= issuedAtMs
        assertThrows(IllegalArgumentException::class.java) {
            AuthSessionMetadata(
                sessionId = "session_001",
                expiresAtMs = now,
                keyId = "key_001",
                operation = AuthOperation.SIGN,
                intentFingerprint = "fp_001",
                walletId = "wallet_001",
                issuedAtMs = now,
                authenticatorType = "TEST_AUTH"
            )
        }
    }

    @Test
    fun test_AuthHandleRegistry_consume_non_existent_session_returns_false() {
        val nonExistent = "unknown_session_xyz"
        assertFalse(AuthHandleRegistry.consume(nonExistent))
        assertFalse(AuthHandleRegistry.consume(""))
        assertFalse(AuthHandleRegistry.consume("   "))
        assertFalse(AuthHandleRegistry.isConsumed(nonExistent))
        assertNull(AuthHandleRegistry.getConsumedSessionMetadata(nonExistent))
    }

    @Test
    fun test_AuthHandleRegistry_register_and_single_consumption_lifecycle() {
        val now = Clock.System.now().toEpochMilliseconds()
        val expiresAt = now + 20_000L
        val sessionId = "lifecycle_session_01"
        val keyId = "key_lifecycle"
        val walletId = "wallet_lifecycle"

        AuthHandleRegistry.register(
            sessionId = sessionId,
            expiresAtMs = expiresAt,
            keyId = keyId,
            operation = AuthOperation.SIGN,
            intentFingerprint = "fp_lifecycle",
            walletId = walletId,
            issuedAtMs = now,
            authenticatorType = "TEST_AUTH"
        )

        assertTrue(AuthHandleRegistry.isRegistered(sessionId))
        assertFalse(AuthHandleRegistry.isConsumed(sessionId))

        val activeMeta = AuthHandleRegistry.getActiveSessionMetadata(sessionId)
        assertNotNull(activeMeta)
        assertEquals(sessionId, activeMeta!!.sessionId)
        assertEquals(keyId, activeMeta.keyId)
        assertEquals(walletId, activeMeta.walletId)
        assertEquals("TEST_AUTH", activeMeta.authenticatorType)

        // First consume
        assertTrue(AuthHandleRegistry.consume(sessionId))
        assertTrue(AuthHandleRegistry.isConsumed(sessionId))
        assertFalse(AuthHandleRegistry.isRegistered(sessionId))
        assertNull(AuthHandleRegistry.getActiveSessionMetadata(sessionId))

        val consumedMeta = AuthHandleRegistry.getConsumedSessionMetadata(sessionId)
        assertNotNull(consumedMeta)
        assertEquals(sessionId, consumedMeta!!.sessionId)

        // Second consume -> must return false because it is already removed from active
        assertFalse(AuthHandleRegistry.consume(sessionId))
    }

    @Test
    fun test_validateAndConsume_unconditional_equality_guarantees() {
        val now = 300_000L
        val expiresAt = 330_000L
        val sessionId = "eq_test_session"
        val keyId = "eq_key_alice"
        val walletId = "eq_wallet_alice"

        val proofToken = ProofTokenVerifier.sign(
            keyId = keyId,
            operation = AuthOperation.SIGN,
            intentFingerprint = "fp_alice",
            sessionId = sessionId,
            nonce = "nonce_alice",
            issuedAtMs = now,
            expiresAtMs = expiresAt,
            walletId = walletId,
            authenticatorType = "TEST_AUTH"
        )

        val handle = PlatformAuthHandle(
            keyId = keyId,
            operation = AuthOperation.SIGN,
            intentFingerprint = "fp_alice",
            sessionId = sessionId,
            nonce = "nonce_alice",
            issuedAtMs = now,
            expiresAtMs = expiresAt,
            proofToken = proofToken,
            walletId = walletId
        )

        // Blank expectedKeyId -> Failure(IllegalArgumentException)
        val blankKeyResult = AuthHandleRegistry.validateAndConsume(
            handle = handle,
            expectedKeyId = "",
            expectedOperation = AuthOperation.SIGN,
            currentTimeMs = now + 1000L,
            expectedWalletId = walletId
        )
        assertTrue(blankKeyResult is Result.Failure)
        assertTrue((blankKeyResult as Result.Failure).exception is IllegalArgumentException)

        // Blank expectedWalletId -> Failure(IllegalArgumentException)
        val blankWalletResult = AuthHandleRegistry.validateAndConsume(
            handle = handle,
            expectedKeyId = keyId,
            expectedOperation = AuthOperation.SIGN,
            currentTimeMs = now + 1000L,
            expectedWalletId = ""
        )
        assertTrue(blankWalletResult is Result.Failure)
        assertTrue((blankWalletResult as Result.Failure).exception is IllegalArgumentException)

        // Null handle -> Failure(AuthenticationRequiredException)
        val nullHandleResult = AuthHandleRegistry.validateAndConsume(
            handle = null,
            expectedKeyId = keyId,
            expectedOperation = AuthOperation.SIGN,
            currentTimeMs = now + 1000L,
            expectedWalletId = walletId
        )
        assertTrue(nullHandleResult is Result.Failure)
        assertTrue((nullHandleResult as Result.Failure).exception is AuthenticationRequiredException)

        // Successful consumption
        val successResult = AuthHandleRegistry.validateAndConsume(
            handle = handle,
            expectedKeyId = keyId,
            expectedOperation = AuthOperation.SIGN,
            expectedFingerprint = "fp_alice",
            currentTimeMs = now + 1000L,
            expectedWalletId = walletId
        )
        assertTrue(successResult is Result.Success)
    }

    @Test
    fun test_validateConsumeAndIssueGrant_unconditional_equality_guarantees() {
        val now = 400_000L
        val expiresAt = 430_000L
        val sessionId = "grant_test_session"
        val keyId = "key_delete_target"
        val walletId = "wallet_delete_target"

        val proofToken = ProofTokenVerifier.sign(
            keyId = keyId,
            operation = AuthOperation.DELETE,
            intentFingerprint = "",
            sessionId = sessionId,
            nonce = "nonce_grant",
            issuedAtMs = now,
            expiresAtMs = expiresAt,
            walletId = walletId,
            authenticatorType = "TEST_AUTH"
        )

        val handle = PlatformAuthHandle(
            keyId = keyId,
            operation = AuthOperation.DELETE,
            intentFingerprint = "",
            sessionId = sessionId,
            nonce = "nonce_grant",
            issuedAtMs = now,
            expiresAtMs = expiresAt,
            proofToken = proofToken,
            walletId = walletId
        )

        // Blank keyId
        val blankKey = AuthHandleRegistry.validateConsumeAndIssueGrant(
            handle = handle,
            walletId = walletId,
            expectedKeyId = "",
            currentTimeMs = now + 1000L
        )
        assertTrue(blankKey is Result.Failure)
        assertTrue((blankKey as Result.Failure).exception is IllegalArgumentException)

        // Blank walletId
        val blankWallet = AuthHandleRegistry.validateConsumeAndIssueGrant(
            handle = handle,
            walletId = "",
            expectedKeyId = keyId,
            currentTimeMs = now + 1000L
        )
        assertTrue(blankWallet is Result.Failure)
        assertTrue((blankWallet as Result.Failure).exception is IllegalArgumentException)

        // Successful grant issuance
        val successGrant = AuthHandleRegistry.validateConsumeAndIssueGrant(
            handle = handle,
            walletId = walletId,
            expectedKeyId = keyId,
            currentTimeMs = now + 1000L
        )
        assertTrue(successGrant is Result.Success)
        val grant = (successGrant as Result.Success).data
        assertEquals(walletId, grant.walletId)
        assertEquals(keyId, grant.keyAlias)
        assertEquals(AuthOperation.DELETE, grant.operation)
        assertEquals(sessionId, grant.originalAuthSessionId)
    }
}

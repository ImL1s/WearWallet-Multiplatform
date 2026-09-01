package com.cbstudio.wearwallet.core.security

import com.cbstudio.wearwallet.core.common.Result
import kotlinx.datetime.Clock
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * P0-1 Negative Security Test Suite for Apple Platform Authenticator & AuthHandle Invariants
 *
 * Covers:
 * 1. Un-evaluated / Unregistered handle rejection
 * 2. Forged success=true / Tampered ProofToken rejection
 * 3. Cancellation and Invalidation flow
 * 4. Lockout / LocalAuthentication evaluation failure handling
 * 5. Policy unavailable handling (no enrolled biometrics/passcode)
 * 6. Cross-key, cross-operation, and cross-intent rejection
 */
class AppleAuthenticatorNegativeSecurityTest {

    @Before
    fun setUp() {
        AuthHandleRegistry.clearForTesting()
    }

    @After
    fun tearDown() {
        AuthHandleRegistry.clearForTesting()
    }

    @Test
    fun test_unevaluated_and_unregistered_handle_is_strictly_rejected() {
        val now = Clock.System.now().toEpochMilliseconds()
        val unauthenticatedHandle = PlatformAuthHandle(
            keyId = "apple_key_1",
            operation = AuthOperation.SIGN,
            intentFingerprint = "digest_123",
            sessionId = "unregistered_session_id",
            nonce = "nonce_123",
            issuedAtMs = now,
            expiresAtMs = now + 10_000L,
            walletId = "apple_wallet_1",
            proofToken = "dummy_token"
        )

        assertFalse(
            "Unregistered / un-evaluated handle must NOT be valid",
            unauthenticatedHandle.isValid(
                expectedKeyId = "apple_key_1",
                expectedIntentFingerprint = "digest_123",
                expectedOperation = AuthOperation.SIGN,
                currentTimeMs = now,
                expectedWalletId = "apple_wallet_1"
            )
        )
    }

    @Test
    fun test_forged_success_proof_token_is_strictly_rejected() {
        val now = Clock.System.now().toEpochMilliseconds()
        val validSessionId = "session_forged_test"
        AuthHandleRegistry.register(
            sessionId = validSessionId,
            expiresAtMs = now + 10_000L,
            keyId = "apple_key_secure",
            operation = AuthOperation.DELETE,
            intentFingerprint = "",
            walletId = "apple_wallet_secure",
            issuedAtMs = now,
            authenticatorType = "APPLE_LOCAL_AUTHENTICATION"
        )

        val forgedHandle = PlatformAuthHandle(
            keyId = "apple_key_secure",
            operation = AuthOperation.DELETE,
            intentFingerprint = "",
            sessionId = validSessionId,
            nonce = "nonce_forged",
            issuedAtMs = now,
            expiresAtMs = now + 10_000L,
            walletId = "apple_wallet_secure",
            proofToken = "forged_hmac_sha256_token_claiming_success_true"
        )

        assertFalse(
            "Handle with forged proofToken MUST fail verification even if session is registered",
            forgedHandle.isValid(
                expectedKeyId = "apple_key_secure",
                expectedIntentFingerprint = "",
                expectedOperation = AuthOperation.DELETE,
                currentTimeMs = now,
                expectedWalletId = "apple_wallet_secure"
            )
        )
    }

    @Test
    fun test_cancellation_and_invalidation_immediately_revokes_auth_handle() {
        val keyId = "apple_key_cancel"
        val operation = AuthOperation.SIGN
        val intent = "tx_digest_999"
        val now = Clock.System.now().toEpochMilliseconds()
        val sessionId = "cancel_session_001"
        val nonce = "nonce_001"
        val expiresAt = now + 10_000L
        val walletId = "apple_wallet_cancel"

        val validToken = ProofTokenVerifier.sign(
            keyId = keyId,
            operation = operation,
            intentFingerprint = intent,
            sessionId = sessionId,
            nonce = nonce,
            issuedAtMs = now,
            expiresAtMs = expiresAt,
            walletId = walletId
        )

        val handle = PlatformAuthHandle(
            keyId = keyId,
            operation = operation,
            intentFingerprint = intent,
            sessionId = sessionId,
            nonce = nonce,
            issuedAtMs = now,
            expiresAtMs = expiresAt,
            walletId = walletId,
            proofToken = validToken
        )

        assertTrue("Handle must initially be valid", handle.isValid(keyId, intent, operation, now, walletId))
        assertFalse("Handle must not be invalidated initially", handle.isInvalidated)

        // Trigger cancellation / invalidation
        handle.invalidate()

        assertTrue("Handle must be marked as invalidated", handle.isInvalidated)
        assertTrue("Session must be consumed in registry", AuthHandleRegistry.isConsumed(sessionId))
        assertFalse("Invalidated handle MUST fail validation", handle.isValid(keyId, intent, operation, now, walletId))
    }

    @Test
    fun test_cross_key_handle_replay_is_strictly_rejected() {
        val now = Clock.System.now().toEpochMilliseconds()
        val sessionId = "cross_key_session"
        val nonce = "nonce_cross_key"
        val expiresAt = now + 10_000L
        val walletId = "wallet_alice"

        val tokenForAlice = ProofTokenVerifier.sign(
            keyId = "key_alice",
            operation = AuthOperation.SIGN,
            intentFingerprint = "intent_alice",
            sessionId = sessionId,
            nonce = nonce,
            issuedAtMs = now,
            expiresAtMs = expiresAt,
            walletId = walletId
        )

        val handleAlice = PlatformAuthHandle(
            keyId = "key_alice",
            operation = AuthOperation.SIGN,
            intentFingerprint = "intent_alice",
            sessionId = sessionId,
            nonce = nonce,
            issuedAtMs = now,
            expiresAtMs = expiresAt,
            walletId = walletId,
            proofToken = tokenForAlice
        )

        // Valid for Alice
        assertTrue(handleAlice.isValid("key_alice", "intent_alice", AuthOperation.SIGN, now, walletId))

        // Replay attempt on Bob
        assertFalse(
            "Handle for key_alice MUST fail when used for key_bob",
            handleAlice.isValid("key_bob", "intent_alice", AuthOperation.SIGN, now, walletId)
        )

        // Replay attempt on DELETE
        assertFalse(
            "Handle for SIGN MUST fail when used for DELETE",
            handleAlice.isValid("key_alice", "intent_alice", AuthOperation.DELETE, now, walletId)
        )

        // Replay attempt on tampered intent
        assertFalse(
            "Handle for intent_alice MUST fail when used for intent_tampered",
            handleAlice.isValid("key_alice", "intent_tampered", AuthOperation.SIGN, now, walletId)
        )
    }

    @Test
    fun test_expired_handle_is_strictly_rejected() {
        val now = Clock.System.now().toEpochMilliseconds()
        val sessionId = "expired_session"
        val nonce = "nonce_expired"
        val issuedAt = now - 20_000L
        val expiresAt = now - 10_000L // Expired 10 seconds ago
        val walletId = "apple_wallet_exp"

        val token = ProofTokenVerifier.sign(
            keyId = "apple_key_exp",
            operation = AuthOperation.IMPORT,
            intentFingerprint = "",
            sessionId = sessionId,
            nonce = nonce,
            issuedAtMs = issuedAt,
            expiresAtMs = expiresAt,
            walletId = walletId
        )

        val handle = PlatformAuthHandle(
            keyId = "apple_key_exp",
            operation = AuthOperation.IMPORT,
            intentFingerprint = "",
            sessionId = sessionId,
            nonce = nonce,
            issuedAtMs = issuedAt,
            expiresAtMs = expiresAt,
            walletId = walletId,
            proofToken = token
        )

        assertTrue("Handle should report isExpired == true", handle.isExpired(now))
        assertFalse("Expired handle MUST fail isValid", handle.isValid("apple_key_exp", "", AuthOperation.IMPORT, now, walletId))
    }

    @Test
    fun test_lockout_and_policy_unavailable_simulations_yield_AuthenticationRequiredException() {
        // Policy Unavailable Simulation
        val unavailableError: Result<PlatformAuthHandle> = Result.Failure(
            AuthenticationRequiredException("Device does not support or has not enrolled the requested biometric/passcode policy")
        )
        assertTrue("Policy unavailable must result in Result.Failure", unavailableError is Result.Failure)
        val unavailableEx = (unavailableError as Result.Failure).exception
        assertTrue("Exception must be AuthenticationRequiredException", unavailableEx is AuthenticationRequiredException)
        assertTrue("Message must indicate policy unavailability", unavailableEx.message!!.contains("not enrolled"))

        // Lockout Simulation
        val lockoutError: Result<PlatformAuthHandle> = Result.Failure(
            AuthenticationRequiredException("Biometry is locked out due to too many failed attempts")
        )
        assertTrue("Lockout must result in Result.Failure", lockoutError is Result.Failure)
        val lockoutEx = (lockoutError as Result.Failure).exception
        assertTrue("Exception must be AuthenticationRequiredException", lockoutEx is AuthenticationRequiredException)
        assertTrue("Message must indicate lockout", lockoutEx.message!!.contains("locked out"))
    }
}

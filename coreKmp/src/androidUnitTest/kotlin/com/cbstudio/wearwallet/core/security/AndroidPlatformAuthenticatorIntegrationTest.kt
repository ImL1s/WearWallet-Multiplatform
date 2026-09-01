package com.cbstudio.wearwallet.core.security

import androidx.biometric.BiometricPrompt
import com.cbstudio.wearwallet.core.common.Result
import kotlinx.datetime.Clock
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Milestone 4 Production Integration Test: AndroidPlatformAuthenticator & AuthHandleRegistry Invariants
 *
 * Exhaustively verifies:
 * 1. Authentic issuance and single-use consumption.
 * 2. Exact expiry boundary rejection (expiresAtMs <= now).
 * 3. Future issuance timestamp rejection.
 * 4. Cross-wallet rejection (fails closed).
 * 5. Cross-key rejection (fails closed).
 * 6. Cross-operation rejection across all permutations.
 * 7. Tampered proof token and invalid HMAC rejection.
 * 8. Consuming non-existent session returns false with zero side effects.
 * 9. Strict register validation rejecting blank/invalid parameters.
 * 10. Re-registering active or consumed sessions throws IllegalStateException.
 * 11. validateConsumeAndIssueGrant deletion grant lifecycle.
 * 12. AndroidPlatformAuthenticator input validation on blank arguments.
 */
class AndroidPlatformAuthenticatorIntegrationTest {

    private lateinit var mockAuthResult: BiometricPrompt.AuthenticationResult
    private lateinit var mockCryptoObject: BiometricPrompt.CryptoObject

    @Before
    fun setUp() {
        AuthHandleRegistry.clearForTesting()
        DeletionGrantRegistry.clearForTesting()
        mockAuthResult = mock()
        mockCryptoObject = mock()
        whenever(mockAuthResult.cryptoObject).thenReturn(mockCryptoObject)
    }

    @Test
    fun test_authentic_issuance_and_single_use_consumption() {
        val keyId = "key_android_01"
        val walletId = "wallet_android_01"
        val fingerprint = "fingerprint_tx_01"

        val handle = AndroidPlatformAuthenticator.issueHandle(
            keyId = keyId,
            operation = AuthOperation.SIGN,
            authenticationResult = mockAuthResult,
            walletId = walletId,
            intentFingerprint = fingerprint,
            validityDurationMs = 30_000L
        )

        assertNotNull(handle)
        assertEquals(keyId, handle.keyId)
        assertEquals(walletId, handle.walletId)
        assertEquals(AuthOperation.SIGN, handle.operation)
        assertEquals(fingerprint, handle.intentFingerprint)
        assertTrue(handle.proofToken.isNotBlank())
        assertTrue(AuthHandleRegistry.isRegistered(handle.sessionId))
        assertFalse(AuthHandleRegistry.isConsumed(handle.sessionId))

        // First consumption: Success
        val firstResult = AuthHandleRegistry.validateAndConsume(
            handle = handle,
            expectedKeyId = keyId,
            expectedOperation = AuthOperation.SIGN,
            expectedFingerprint = fingerprint,
            expectedWalletId = walletId
        )
        assertTrue("First consumption must succeed", firstResult is Result.Success)
        assertTrue("Session must be marked consumed", AuthHandleRegistry.isConsumed(handle.sessionId))
        assertFalse("Session must no longer be registered as active", AuthHandleRegistry.isRegistered(handle.sessionId))

        // Second consumption: Fails closed
        val secondResult = AuthHandleRegistry.validateAndConsume(
            handle = handle,
            expectedKeyId = keyId,
            expectedOperation = AuthOperation.SIGN,
            expectedFingerprint = fingerprint,
            expectedWalletId = walletId
        )
        assertTrue("Second consumption must fail", secondResult is Result.Failure)
        val failure = secondResult as Result.Failure
        assertTrue("Exception must be AuthenticationRequiredException", failure.exception is AuthenticationRequiredException)
        assertTrue(
            "Message must indicate already consumed or invalidated",
            failure.exception.message!!.contains("already consumed") || failure.exception.message!!.contains("invalidated")
        )
    }

    @Test
    fun test_expiry_boundary_rejection_at_exact_expiry_timestamp() {
        val keyId = "key_android_expiry"
        val walletId = "wallet_android_expiry"
        val validityDurationMs = 10_000L

        val handle = PlatformAuthHandle.createInternal(
            keyId = keyId,
            operation = AuthOperation.SIGN,
            intentFingerprint = "fp_expiry",
            validityDurationMs = validityDurationMs,
            walletId = walletId,
            cryptoObject = mockCryptoObject,
            authenticatorType = "ANDROID_BIOMETRIC"
        )

        // 1ms before expiry -> Must SUCCEED
        val beforeExpiry = AuthHandleRegistry.validateAndConsume(
            handle = handle,
            expectedKeyId = keyId,
            expectedOperation = AuthOperation.SIGN,
            expectedFingerprint = "fp_expiry",
            currentTimeMs = handle.expiresAtMs - 1L,
            expectedWalletId = walletId
        )
        assertTrue("Validation 1ms before expiry must succeed", beforeExpiry is Result.Success)

        // Reset and re-create for boundary test
        AuthHandleRegistry.clearForTesting()
        val handleBoundary = PlatformAuthHandle.createInternal(
            keyId = keyId,
            operation = AuthOperation.SIGN,
            intentFingerprint = "fp_expiry",
            validityDurationMs = validityDurationMs,
            walletId = walletId,
            cryptoObject = mockCryptoObject,
            authenticatorType = "ANDROID_BIOMETRIC"
        )

        // Exactly at expiry timestamp (now == expiresAtMs) -> Must FAIL CLOSED
        val atExpiry = AuthHandleRegistry.validateAndConsume(
            handle = handleBoundary,
            expectedKeyId = keyId,
            expectedOperation = AuthOperation.SIGN,
            expectedFingerprint = "fp_expiry",
            currentTimeMs = handleBoundary.expiresAtMs,
            expectedWalletId = walletId
        )
        assertTrue("Validation exactly at expiresAtMs boundary must fail", atExpiry is Result.Failure)
        assertTrue((atExpiry as Result.Failure).exception.message!!.contains("has expired"))

        // Reset and re-create for after expiry test
        AuthHandleRegistry.clearForTesting()
        val handleAfter = PlatformAuthHandle.createInternal(
            keyId = keyId,
            operation = AuthOperation.SIGN,
            intentFingerprint = "fp_expiry",
            validityDurationMs = validityDurationMs,
            walletId = walletId,
            cryptoObject = mockCryptoObject,
            authenticatorType = "ANDROID_BIOMETRIC"
        )

        // 1ms after expiry -> Must FAIL CLOSED
        val afterExpiry = AuthHandleRegistry.validateAndConsume(
            handle = handleAfter,
            expectedKeyId = keyId,
            expectedOperation = AuthOperation.SIGN,
            expectedFingerprint = "fp_expiry",
            currentTimeMs = handleAfter.expiresAtMs + 1L,
            expectedWalletId = walletId
        )
        assertTrue("Validation after expiry must fail", afterExpiry is Result.Failure)
        assertTrue((afterExpiry as Result.Failure).exception.message!!.contains("has expired"))
    }

    @Test
    fun test_future_issuance_timestamp_is_strictly_rejected() {
        val keyId = "key_future"
        val walletId = "wallet_future"
        val now = 1_000_000L
        val futureIssuedAt = now + 5000L
        val expiresAt = futureIssuedAt + 10_000L
        val sessionId = "future_session"
        val nonce = "future_nonce"

        val proofToken = ProofTokenVerifier.sign(
            keyId = keyId,
            operation = AuthOperation.SIGN,
            intentFingerprint = "",
            sessionId = sessionId,
            nonce = nonce,
            issuedAtMs = futureIssuedAt,
            expiresAtMs = expiresAt,
            walletId = walletId,
            authenticatorType = "ANDROID_BIOMETRIC"
        )

        val handle = PlatformAuthHandle(
            keyId = keyId,
            operation = AuthOperation.SIGN,
            intentFingerprint = "",
            sessionId = sessionId,
            nonce = nonce,
            issuedAtMs = futureIssuedAt,
            expiresAtMs = expiresAt,
            proofToken = proofToken,
            walletId = walletId
        )

        val result = AuthHandleRegistry.validateAndConsume(
            handle = handle,
            expectedKeyId = keyId,
            expectedOperation = AuthOperation.SIGN,
            currentTimeMs = now,
            expectedWalletId = walletId
        )
        assertTrue("Handle issued in the future must be rejected", result is Result.Failure)
        assertTrue((result as Result.Failure).exception.message!!.contains("not yet valid"))
    }

    @Test
    fun test_cross_wallet_rejection_fails_closed() {
        val keyId = "shared_key_id"
        val walletIdA = "wallet_alice"
        val walletIdB = "wallet_bob"

        val handleA = AndroidPlatformAuthenticator.issueHandle(
            keyId = keyId,
            operation = AuthOperation.SIGN,
            authenticationResult = mockAuthResult,
            walletId = walletIdA
        )

        val crossWalletResult = AuthHandleRegistry.validateAndConsume(
            handle = handleA,
            expectedKeyId = keyId,
            expectedOperation = AuthOperation.SIGN,
            expectedWalletId = walletIdB
        )

        assertTrue("Cross-wallet handle must fail closed", crossWalletResult is Result.Failure)
        val failure = crossWalletResult as Result.Failure
        assertTrue(
            "Message must indicate cross-wallet rejection",
            failure.exception.message!!.contains("Cross-wallet")
        )
        assertFalse("Cross-wallet session must not be consumed", AuthHandleRegistry.isConsumed(handleA.sessionId))
    }

    @Test
    fun test_cross_key_rejection_fails_closed() {
        val keyIdA = "key_alice"
        val keyIdB = "key_bob"
        val walletId = "wallet_shared"

        val handleA = AndroidPlatformAuthenticator.issueHandle(
            keyId = keyIdA,
            operation = AuthOperation.SIGN,
            authenticationResult = mockAuthResult,
            walletId = walletId
        )

        val crossKeyResult = AuthHandleRegistry.validateAndConsume(
            handle = handleA,
            expectedKeyId = keyIdB,
            expectedOperation = AuthOperation.SIGN,
            expectedWalletId = walletId
        )

        assertTrue("Cross-key handle must fail closed", crossKeyResult is Result.Failure)
        val failure = crossKeyResult as Result.Failure
        assertTrue(
            "Message must indicate cross-key rejection",
            failure.exception.message!!.contains("Cross-key")
        )
        assertFalse("Cross-key session must not be consumed", AuthHandleRegistry.isConsumed(handleA.sessionId))
    }

    @Test
    fun test_cross_operation_all_permutations_rejection() {
        val keyId = "key_op_test"
        val walletId = "wallet_op_test"
        val operations = AuthOperation.values()

        for (issuedOp in operations) {
            for (attemptedOp in operations) {
                if (issuedOp == attemptedOp) continue

                AuthHandleRegistry.clearForTesting()
                val handle = AndroidPlatformAuthenticator.issueHandle(
                    keyId = keyId,
                    operation = issuedOp,
                    authenticationResult = mockAuthResult,
                    walletId = walletId
                )

                val result = AuthHandleRegistry.validateAndConsume(
                    handle = handle,
                    expectedKeyId = keyId,
                    expectedOperation = attemptedOp,
                    expectedWalletId = walletId
                )

                assertTrue(
                    "Cross-operation from $issuedOp to $attemptedOp must be rejected",
                    result is Result.Failure
                )
                val failure = result as Result.Failure
                assertTrue(
                    "Message must mention operation mismatch",
                    failure.exception.message!!.contains("does not match expected") || failure.exception.message!!.contains("mismatch") || failure.exception.message!!.contains("is not DELETE")
                )
            }
        }
    }

    @Test
    fun test_tampered_proof_token_and_invalid_hmac_rejection() {
        val keyId = "key_tamper"
        val walletId = "wallet_tamper"

        val genuineHandle = AndroidPlatformAuthenticator.issueHandle(
            keyId = keyId,
            operation = AuthOperation.SIGN,
            authenticationResult = mockAuthResult,
            walletId = walletId
        )

        // Tamper proofToken (flip last character)
        val tamperedToken = if (genuineHandle.proofToken.endsWith("a")) {
            genuineHandle.proofToken.dropLast(1) + "b"
        } else {
            genuineHandle.proofToken.dropLast(1) + "a"
        }

        val tamperedHandle = PlatformAuthHandle(
            keyId = genuineHandle.keyId,
            operation = genuineHandle.operation,
            intentFingerprint = genuineHandle.intentFingerprint,
            sessionId = genuineHandle.sessionId,
            nonce = genuineHandle.nonce,
            issuedAtMs = genuineHandle.issuedAtMs,
            expiresAtMs = genuineHandle.expiresAtMs,
            proofToken = tamperedToken,
            walletId = genuineHandle.walletId
        )

        val result = AuthHandleRegistry.validateAndConsume(
            handle = tamperedHandle,
            expectedKeyId = keyId,
            expectedOperation = AuthOperation.SIGN,
            expectedWalletId = walletId
        )

        assertTrue("Tampered proof token must be rejected", result is Result.Failure)
        val failure = result as Result.Failure
        assertTrue(
            "Message must indicate proof token verification failure",
            failure.exception.message!!.contains("Proof token verification failed")
        )
    }

    @Test
    fun test_consuming_non_existent_session_returns_false_and_zero_side_effects() {
        val unknownSessionId = "session_never_registered_999"

        assertFalse("isRegistered must return false for unknown session", AuthHandleRegistry.isRegistered(unknownSessionId))
        assertFalse("isConsumed must return false for unknown session", AuthHandleRegistry.isConsumed(unknownSessionId))
        assertNull("getActiveSessionMetadata must return null", AuthHandleRegistry.getActiveSessionMetadata(unknownSessionId))
        assertNull("getConsumedSessionMetadata must return null", AuthHandleRegistry.getConsumedSessionMetadata(unknownSessionId))

        val consumeResult = AuthHandleRegistry.consume(unknownSessionId)
        assertFalse("Consuming non-existent session must return false", consumeResult)
        assertFalse("isConsumed must still return false", AuthHandleRegistry.isConsumed(unknownSessionId))
        assertNull("getConsumedSessionMetadata must remain null", AuthHandleRegistry.getConsumedSessionMetadata(unknownSessionId))
    }

    @Test
    fun test_register_strict_validation_rejects_blank_and_invalid_parameters() {
        val now = Clock.System.now().toEpochMilliseconds()
        val expiresAt = now + 10_000L

        // Blank sessionId
        assertThrows(IllegalArgumentException::class.java) {
            AuthHandleRegistry.register(
                sessionId = "   ",
                expiresAtMs = expiresAt,
                keyId = "key1",
                operation = AuthOperation.SIGN,
                intentFingerprint = "",
                walletId = "w1",
                issuedAtMs = now,
                authenticatorType = "ANDROID_BIOMETRIC"
            )
        }

        // Blank keyId
        assertThrows(IllegalArgumentException::class.java) {
            AuthHandleRegistry.register(
                sessionId = "s1",
                expiresAtMs = expiresAt,
                keyId = "",
                operation = AuthOperation.SIGN,
                intentFingerprint = "",
                walletId = "w1",
                issuedAtMs = now,
                authenticatorType = "ANDROID_BIOMETRIC"
            )
        }

        // Blank walletId
        assertThrows(IllegalArgumentException::class.java) {
            AuthHandleRegistry.register(
                sessionId = "s1",
                expiresAtMs = expiresAt,
                keyId = "key1",
                operation = AuthOperation.SIGN,
                intentFingerprint = "",
                walletId = "  ",
                issuedAtMs = now,
                authenticatorType = "ANDROID_BIOMETRIC"
            )
        }

        // Blank authenticatorType
        assertThrows(IllegalArgumentException::class.java) {
            AuthHandleRegistry.register(
                sessionId = "s1",
                expiresAtMs = expiresAt,
                keyId = "key1",
                operation = AuthOperation.SIGN,
                intentFingerprint = "",
                walletId = "w1",
                issuedAtMs = now,
                authenticatorType = ""
            )
        }

        // issuedAtMs <= 0
        assertThrows(IllegalArgumentException::class.java) {
            AuthHandleRegistry.register(
                sessionId = "s1",
                expiresAtMs = expiresAt,
                keyId = "key1",
                operation = AuthOperation.SIGN,
                intentFingerprint = "",
                walletId = "w1",
                issuedAtMs = 0L,
                authenticatorType = "ANDROID_BIOMETRIC"
            )
        }

        // expiresAtMs <= issuedAtMs
        assertThrows(IllegalArgumentException::class.java) {
            AuthHandleRegistry.register(
                sessionId = "s1",
                expiresAtMs = now,
                keyId = "key1",
                operation = AuthOperation.SIGN,
                intentFingerprint = "",
                walletId = "w1",
                issuedAtMs = now,
                authenticatorType = "ANDROID_BIOMETRIC"
            )
        }
    }

    @Test
    fun test_re_registering_active_or_consumed_session_throws_IllegalStateException() {
        val now = Clock.System.now().toEpochMilliseconds()
        val expiresAt = now + 10_000L
        val sessionId = "session_reregister_test"

        AuthHandleRegistry.register(
            sessionId = sessionId,
            expiresAtMs = expiresAt,
            keyId = "key1",
            operation = AuthOperation.SIGN,
            intentFingerprint = "",
            walletId = "w1",
            issuedAtMs = now,
            authenticatorType = "ANDROID_BIOMETRIC"
        )

        // Re-registering active session -> IllegalStateException
        val activeEx = assertThrows(IllegalStateException::class.java) {
            AuthHandleRegistry.register(
                sessionId = sessionId,
                expiresAtMs = expiresAt,
                keyId = "key1",
                operation = AuthOperation.SIGN,
                intentFingerprint = "",
                walletId = "w1",
                issuedAtMs = now,
                authenticatorType = "ANDROID_BIOMETRIC"
            )
        }
        assertTrue(activeEx.message!!.contains("already active"))

        // Consume the session
        assertTrue(AuthHandleRegistry.consume(sessionId))

        // Re-registering consumed session -> IllegalStateException
        val consumedEx = assertThrows(IllegalStateException::class.java) {
            AuthHandleRegistry.register(
                sessionId = sessionId,
                expiresAtMs = expiresAt,
                keyId = "key1",
                operation = AuthOperation.SIGN,
                intentFingerprint = "",
                walletId = "w1",
                issuedAtMs = now,
                authenticatorType = "ANDROID_BIOMETRIC"
            )
        }
        assertTrue(consumedEx.message!!.contains("already consumed"))
    }

    @Test
    fun test_validateConsumeAndIssueGrant_with_AndroidPlatformAuthenticator() {
        val keyId = "key_delete_01"
        val walletId = "wallet_delete_01"

        val handle = AndroidPlatformAuthenticator.issueHandle(
            keyId = keyId,
            operation = AuthOperation.DELETE,
            authenticationResult = mockAuthResult,
            walletId = walletId
        )

        val grantResult = AuthHandleRegistry.validateConsumeAndIssueGrant(
            handle = handle,
            walletId = walletId,
            expectedKeyId = keyId
        )

        assertTrue("Grant issuance must succeed", grantResult is Result.Success)
        val grant = (grantResult as Result.Success).data
        assertEquals(walletId, grant.walletId)
        assertEquals(keyId, grant.keyAlias)
        assertEquals(AuthOperation.DELETE, grant.operation)
        assertEquals(handle.sessionId, grant.originalAuthSessionId)
        assertTrue(grant.proofToken.isNotBlank())
        assertTrue("Grant must be registered in DeletionGrantRegistry", DeletionGrantRegistry.isRegistered(grant.nonce))

        // Second call with consumed handle must fail
        val secondResult = AuthHandleRegistry.validateConsumeAndIssueGrant(
            handle = handle,
            walletId = walletId,
            expectedKeyId = keyId
        )
        assertTrue("Second grant issuance with same handle must fail", secondResult is Result.Failure)
    }

    @Test
    fun test_AndroidPlatformAuthenticator_issueHandle_rejects_blank_arguments() {
        assertThrows(IllegalArgumentException::class.java) {
            AndroidPlatformAuthenticator.issueHandle(
                keyId = "",
                operation = AuthOperation.SIGN,
                authenticationResult = mockAuthResult,
                walletId = "w1"
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            AndroidPlatformAuthenticator.issueHandle(
                keyId = "key1",
                operation = AuthOperation.SIGN,
                authenticationResult = mockAuthResult,
                walletId = " "
            )
        }
    }
}

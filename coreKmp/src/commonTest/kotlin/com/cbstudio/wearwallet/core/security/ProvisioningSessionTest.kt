package com.cbstudio.wearwallet.core.security

import com.cbstudio.wearwallet.core.common.Result
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProvisioningSessionTest {

    @Test
    fun test_ProvisioningSession_alias_generation() {
        val session1 = ProvisioningSession.create()
        val session2 = ProvisioningSession.create()

        assertTrue(session1.sessionId.isNotBlank())
        assertTrue(session1.stagedKeyAlias.startsWith("ww_key_"))
        assertTrue(session1.backupId.startsWith("ww_backup_"))
        assertTrue(session1.isActive)
        assertFalse(session1.isCommitted)
        assertFalse(session1.isRolledBack)

        // Ensure distinct sessions have unique IDs and aliases
        assertTrue(session1.sessionId != session2.sessionId)
        assertTrue(session1.stagedKeyAlias != session2.stagedKeyAlias)
        assertTrue(session1.backupId != session2.backupId)
    }

    @Test
    fun test_ProvisioningSession_cannot_rollback_after_commit() = runTest {
        val fakeKeyManager = FakeSecureKeyManager()
        val session = fakeKeyManager.startProvisioningSession()

        val storeRes = fakeKeyManager.storeStagedPrivateKey(
            session = session,
            privateKey = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef".encodeToByteArray(),
            requireAuth = true,
            authContext = AuthenticationContext(
                authHandle = TestPlatformAuthenticator.issueHandle(
                    keyId = session.stagedKeyAlias,
                    sessionId = session.sessionId,
                    operation = AuthOperation.IMPORT
                )
            )
        )
        assertTrue(storeRes is Result.Success)
        assertTrue(fakeKeyManager.hasPrivateKey(session.stagedKeyAlias))

        val commitRes = fakeKeyManager.commitProvisioningSession(session)
        assertTrue(commitRes is Result.Success)
        assertTrue(session.isCommitted)
        assertFalse(session.isRolledBack)

        // Directly attempting markRolledBack() on committed session fails
        assertFailsWith<IllegalStateException> {
            session.markRolledBack()
        }

        // Attempting rollback on fakeKeyManager returns Failure
        val rollbackRes = fakeKeyManager.rollbackProvisioningSession(session)
        assertTrue(rollbackRes is Result.Failure)
        assertTrue(rollbackRes.exception is IllegalStateException)

        // Key remains safely in KeyVault
        assertTrue(fakeKeyManager.hasPrivateKey(session.stagedKeyAlias))
    }

    @Test
    fun test_ProvisioningSession_cannot_commit_after_rollback() = runTest {
        val fakeKeyManager = FakeSecureKeyManager()
        val session = fakeKeyManager.startProvisioningSession()

        fakeKeyManager.storeStagedPrivateKey(
            session = session,
            privateKey = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef".encodeToByteArray(),
            requireAuth = true,
            authContext = AuthenticationContext(
                authHandle = TestPlatformAuthenticator.issueHandle(
                    keyId = session.stagedKeyAlias,
                    sessionId = session.sessionId,
                    operation = AuthOperation.IMPORT
                )
            )
        )
        assertTrue(fakeKeyManager.hasPrivateKey(session.stagedKeyAlias))

        val rollbackRes = fakeKeyManager.rollbackProvisioningSession(session)
        assertTrue(rollbackRes is Result.Success)
        assertTrue(session.isRolledBack)
        assertFalse(session.isCommitted)
        assertFalse(fakeKeyManager.hasPrivateKey(session.stagedKeyAlias))

        // Directly attempting markCommitted() on rolled back session fails
        assertFailsWith<IllegalStateException> {
            session.markCommitted()
        }

        val commitRes = fakeKeyManager.commitProvisioningSession(session)
        assertTrue(commitRes is Result.Failure)
    }

    @Test
    fun test_ProvisioningSession_expiry_check() = runTest {
        val expiredSession = ProvisioningSession(
            sessionId = "expired_session_1",
            stagedKeyAlias = "ww_key_expired_1",
            backupId = "ww_backup_expired_1",
            createdAtMs = 1000L,
            maxValidityDurationMs = 10L // expired
        )
        assertFalse(expiredSession.isActive)

        val fakeKeyManager = FakeSecureKeyManager()
        val storeRes = fakeKeyManager.storeStagedPrivateKey(
            session = expiredSession,
            privateKey = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef".encodeToByteArray(),
            requireAuth = true,
            authContext = AuthenticationContext(
                authHandle = TestPlatformAuthenticator.issueHandle(
                    keyId = expiredSession.stagedKeyAlias,
                    sessionId = expiredSession.sessionId,
                    operation = AuthOperation.IMPORT
                )
            )
        )
        assertTrue(storeRes is Result.Failure)
        assertTrue(storeRes.exception is IllegalStateException)
    }

    @Test
    fun test_ProvisioningSession_rollback_on_unknown_session_fails_closed() = runTest {
        val fakeKeyManager = FakeSecureKeyManager()
        val untrackedKey = "ww_key_untracked_target"
        fakeKeyManager.setKey(untrackedKey, "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", requireAuth = true)

        val unknownSession = ProvisioningSession(
            sessionId = "unknown_sess_attacker",
            stagedKeyAlias = untrackedKey,
            backupId = "unknown_backup_attacker"
        )

        val rollbackRes = fakeKeyManager.rollbackProvisioningSession(unknownSession)
        // Adversarial assertion: unknown session ID attempting rollback MUST fail closed and preserve key
        assertTrue(rollbackRes is Result.Failure, "Rollback on unknown session ID must fail closed")
        assertTrue(fakeKeyManager.hasPrivateKey(untrackedKey), "Target key must not be deleted by unknown session")
    }

    @Test
    fun test_ProvisioningSession_rollback_on_expired_session_fails_closed() = runTest {
        val fakeKeyManager = FakeSecureKeyManager()
        val session = fakeKeyManager.startProvisioningSession()
        val storeRes = fakeKeyManager.storeStagedPrivateKey(
            session = session,
            privateKey = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef".encodeToByteArray(),
            requireAuth = true,
            authContext = AuthenticationContext(
                authHandle = TestPlatformAuthenticator.issueHandle(
                    keyId = session.stagedKeyAlias,
                    sessionId = session.sessionId,
                    operation = AuthOperation.IMPORT
                )
            )
        )
        assertTrue(storeRes is Result.Success)
        assertTrue(fakeKeyManager.hasPrivateKey(session.stagedKeyAlias))

        val expiredSession = ProvisioningSession(
            sessionId = session.sessionId,
            stagedKeyAlias = session.stagedKeyAlias,
            backupId = session.backupId,
            createdAtMs = 1000L,
            maxValidityDurationMs = 10L // Expired
        )
        assertFalse(expiredSession.isActive)

        val rollbackRes = fakeKeyManager.rollbackProvisioningSession(expiredSession)
        assertTrue(rollbackRes is Result.Failure, "Rollback on expired session must fail closed")
    }
}



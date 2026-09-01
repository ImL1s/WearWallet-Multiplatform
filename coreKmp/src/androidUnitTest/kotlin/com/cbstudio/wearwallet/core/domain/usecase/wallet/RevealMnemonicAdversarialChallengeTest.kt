package com.cbstudio.wearwallet.core.domain.usecase.wallet

import app.cash.sqldelight.Query
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.database.DatabaseDriverFactory
import com.cbstudio.wearwallet.core.database.Wallet
import com.cbstudio.wearwallet.core.database.WalletQueries
import com.cbstudio.wearwallet.core.domain.model.WalletType
import com.cbstudio.wearwallet.core.security.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Empirical Adversarial Challenge Test Suite for Milestone 3 (Challenger 2)
 */
class RevealMnemonicAdversarialChallengeTest {

    private lateinit var databaseDriverFactory: DatabaseDriverFactory
    private lateinit var walletQueries: WalletQueries
    private lateinit var auditLogger: SecurityAuditLogger
    private lateinit var useCase: RealRevealMnemonicUseCase

    private val testWalletId = "100"
    private val testWalletIdLong = 100L
    private val testAddress = "0x71C7656EC7ab88b098defB751B7401B5f6d8976F"
    private val testKeyAlias = "ww_key_adversarial_100"
    private val testBackupId = "ww_backup_adversarial_100"
    private val testPassword = "VeryComplexPassword!@#999"
    private val testMnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"

    private lateinit var validEnvelopeBase64: String

    @Before
    fun setup() {
        databaseDriverFactory = mock()
        walletQueries = mock()
        auditLogger = mock()

        val mnemBytes = testMnemonic.encodeToByteArray()
        val pwdBytes = testPassword.encodeToByteArray()
        val envelope = VersionedEncryptedEnvelope.encrypt(
            plaintext = mnemBytes,
            password = pwdBytes,
            keyId = testBackupId,
            aad = CanonicalAad.forWalletStorage(testBackupId, CanonicalAad.KEY_TYPE_MNEMONIC)
        )
        validEnvelopeBase64 = envelope.serializeToBase64()

        useCase = RealRevealMnemonicUseCase(
            databaseDriverFactory = databaseDriverFactory,
            securityAuditLogger = auditLogger,
            customWalletQueries = walletQueries
        )
    }

    private fun createMockWallet(
        id: Long = testWalletIdLong,
        address: String = testAddress,
        keyAlias: String? = testKeyAlias,
        encryptedMnemonic: String? = validEnvelopeBase64,
        walletType: String = "HOT_WALLET",
        isWatchOnly: Long = 0L
    ): Wallet {
        return Wallet(
            id = id,
            name = "Adversarial Test Wallet",
            address = address,
            public_key = "0x04publickey",
            encrypted_private_key = "dummy_priv",
            encrypted_mnemonic = encryptedMnemonic,
            derivation_path = "m/44'/60'/0'/0/0",
            chain_type = "ETHEREUM",
            wallet_type = walletType,
            is_active = 1L,
            is_watch_only = isWatchOnly,
            master_fingerprint = null,
            keystone_sign_request = null,
            keystone_sync_data = null,
            metadata = "{}",
            avatar_id = null,
            chain_id = 1L,
            key_alias = keyAlias,
            key_backend = "SOFTWARE",
            key_format_version = 2L,
            requires_auth = 1L,
            is_deletion_pending = 0L,
            created_at = 1000L,
            updated_at = 1000L
        )
    }

    private fun mockQueryReturning(wallet: Wallet?, walletId: Long = testWalletIdLong) {
        val query = mock<Query<Wallet>>()
        whenever(query.executeAsOneOrNull()).thenReturn(wallet)
        whenever(walletQueries.selectById(walletId)).thenReturn(query)
    }

    @Test
    fun `CHALLENGE 1 - CharArray is verified to be zeroed in memory after action returns`() = runBlocking {
        val wallet = createMockWallet()
        mockQueryReturning(wallet)

        val handle = TestPlatformAuthenticator.issueHandle(
            keyId = testKeyAlias,
            operation = AuthOperation.REVEAL,
            intentFingerprint = "reveal_zeroing_test",
            expiresAtMs = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() + 60_000L,
            walletId = testWalletId
        )
        val authContext = AuthenticationContext(authHandle = handle)

        var leakedRef: CharArray? = null
        val result = useCase.executeWithMnemonic(
            walletId = testWalletId,
            password = testPassword,
            authContext = authContext
        ) { chars ->
            leakedRef = chars
            chars.concatToString()
        }

        assertTrue(result is Result.Success)
        assertNotNull("Reference should have been captured", leakedRef)
        // Check that every character in the referenced array was zeroed out by the finally block
        for (i in leakedRef!!.indices) {
            assertEquals("Char at index $i must be zeroed", '\u0000', leakedRef!![i])
        }
        assertTrue("Auth handle must be invalidated", handle.isInvalidated)
    }

    @Test
    fun `CHALLENGE 2 - Action lambda throwing exception still triggers zeroing and invalidates handle`() = runBlocking {
        val wallet = createMockWallet()
        mockQueryReturning(wallet)

        val handle = TestPlatformAuthenticator.issueHandle(
            keyId = testKeyAlias,
            operation = AuthOperation.REVEAL,
            intentFingerprint = "reveal_exception_test",
            expiresAtMs = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() + 60_000L,
            walletId = testWalletId
        )
        val authContext = AuthenticationContext(authHandle = handle)

        var leakedRef: CharArray? = null
        val result = useCase.executeWithMnemonic(
            walletId = testWalletId,
            password = testPassword,
            authContext = authContext
        ) { chars ->
            leakedRef = chars
            throw IllegalStateException("Simulated crash inside consumer lambda")
        }

        assertTrue("Should return Failure on action crash", result is Result.Failure)
        assertTrue((result as Result.Failure).exception is IllegalStateException)
        assertNotNull(leakedRef)
        // Assert all chars are zeroed even after exception
        for (i in leakedRef!!.indices) {
            assertEquals("Char at index $i must be zeroed even after crash", '\u0000', leakedRef!![i])
        }
        assertTrue("Auth handle must be invalidated even after crash", handle.isInvalidated)
    }

    @Test
    fun `CHALLENGE 3 - Corrupted or tampered ciphertext fails cleanly and invalidates handle`() = runBlocking {
        val corruptedWallet = createMockWallet(encryptedMnemonic = "WWEN:v1:corrupted_garbage_base64")
        mockQueryReturning(corruptedWallet)

        val handle = TestPlatformAuthenticator.issueHandle(
            keyId = testKeyAlias,
            operation = AuthOperation.REVEAL,
            expiresAtMs = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() + 60_000L,
            walletId = testWalletId
        )

        val result = useCase.executeWithMnemonic(
            walletId = testWalletId,
            password = testPassword,
            authContext = AuthenticationContext(authHandle = handle)
        ) { it.concatToString() }

        assertTrue(result is Result.Failure)
        assertTrue(handle.isInvalidated)
    }

    @Test
    fun `CHALLENGE 4 - Expired auth handle at exact boundary is rejected`() = runBlocking {
        val wallet = createMockWallet()
        mockQueryReturning(wallet)

        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        val expiredHandle = TestPlatformAuthenticator.issueHandle(
            keyId = testKeyAlias,
            operation = AuthOperation.REVEAL,
            expiresAtMs = now - 10L,
            walletId = testWalletId
        )

        val result = useCase.executeWithMnemonic(
            walletId = testWalletId,
            password = testPassword,
            authContext = AuthenticationContext(authHandle = expiredHandle)
        ) { it.concatToString() }

        assertTrue(result is Result.Failure)
        val ex = (result as Result.Failure).exception
        assertTrue(ex is AuthenticationRequiredException)
        assertTrue(ex.message?.contains("expired") == true)
    }

    @Test
    fun `CHALLENGE 5 - Watch-only wallet without mnemonic fails cleanly`() = runBlocking {
        val watchOnlyWallet = createMockWallet(isWatchOnly = 1L, encryptedMnemonic = null)
        mockQueryReturning(watchOnlyWallet)

        val handle = TestPlatformAuthenticator.issueHandle(
            keyId = testKeyAlias,
            operation = AuthOperation.REVEAL,
            expiresAtMs = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() + 60_000L,
            walletId = testWalletId
        )

        val result = useCase.executeWithMnemonic(
            walletId = testWalletId,
            password = testPassword,
            authContext = AuthenticationContext(authHandle = handle)
        ) { it.concatToString() }

        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).exception is IllegalStateException)
    }

    @Test
    fun `CHALLENGE 6 - Invalid auth operations (SIGN, IMPORT, DELETE) are all rejected`() = runBlocking {
        val wallet = createMockWallet()
        mockQueryReturning(wallet)

        val invalidOps = listOf(
            AuthOperation.SIGN,
            AuthOperation.IMPORT,
            AuthOperation.DELETE
        )

        for (op in invalidOps) {
            val handle = TestPlatformAuthenticator.issueHandle(
                keyId = testKeyAlias,
                operation = op,
                expiresAtMs = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() + 60_000L,
                walletId = testWalletId
            )

            val result = useCase.executeWithMnemonic(
                walletId = testWalletId,
                password = testPassword,
                authContext = AuthenticationContext(authHandle = handle)
            ) { it.concatToString() }

            assertTrue("Operation $op should be rejected", result is Result.Failure)
            assertTrue((result as Result.Failure).exception is AuthenticationRequiredException)
        }
    }

    @Test
    fun `CHALLENGE 7 - High concurrency stress test with 50 parallel reveal requests`() = runBlocking {
        val wallet = createMockWallet()
        mockQueryReturning(wallet)

        val concurrency = 50
        val successCount = AtomicInteger(0)
        val invalidatedCount = AtomicInteger(0)
        val capturedResults = ConcurrentHashMap<Int, String>()

        coroutineScope {
            val jobs = (1..concurrency).map { index ->
                launch(Dispatchers.Default) {
                    val handle = TestPlatformAuthenticator.issueHandle(
                        keyId = testKeyAlias,
                        operation = AuthOperation.REVEAL,
                        intentFingerprint = "stress_$index",
                        expiresAtMs = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() + 60_000L,
                        walletId = testWalletId
                    )
                    val authCtx = AuthenticationContext(authHandle = handle)

                    val res = useCase.executeWithMnemonic(
                        walletId = testWalletId,
                        password = testPassword,
                        authContext = authCtx
                    ) { chars ->
                        val str = chars.concatToString()
                        capturedResults[index] = str
                        "OK_$index"
                    }

                    if (res is Result.Success && res.data == "OK_$index") {
                        successCount.incrementAndGet()
                    }
                    if (handle.isInvalidated) {
                        invalidatedCount.incrementAndGet()
                    }
                }
            }
            jobs.joinAll()
        }

        assertEquals(concurrency, successCount.get())
        assertEquals(concurrency, invalidatedCount.get())
        assertEquals(concurrency, capturedResults.size)
        capturedResults.values.forEach { mnemonic ->
            assertEquals(testMnemonic, mnemonic)
        }
    }
}


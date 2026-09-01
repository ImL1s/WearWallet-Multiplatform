package com.cbstudio.wearwallet.core.domain.usecase.wallet

import app.cash.sqldelight.Query
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.database.DatabaseDriverFactory
import com.cbstudio.wearwallet.core.database.Wallet
import com.cbstudio.wearwallet.core.database.WalletQueries
import com.cbstudio.wearwallet.core.domain.model.WalletType
import com.cbstudio.wearwallet.core.security.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

class RevealMnemonicUseCaseTest {

    private lateinit var databaseDriverFactory: DatabaseDriverFactory
    private lateinit var walletQueries: WalletQueries
    private lateinit var auditLogger: SecurityAuditLogger
    private lateinit var useCase: RealRevealMnemonicUseCase

    private val testWalletId = "42"
    private val testWalletIdLong = 42L
    private val testAddress = "0x89205A3A3b2A69De6Dbf7f01ED13B2108B2c43e7"
    private val testKeyAlias = "key_alias_uuid_42"
    private val testBackupId = "backup_id_uuid_42"
    private val testPassword = "SuperSecurePassword#2026"
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
        walletType: String = "HOT_WALLET"
    ): Wallet {
        return Wallet(
            id = id,
            name = "Test Wallet",
            address = address,
            public_key = "0x04publickey",
            encrypted_private_key = "dummy_priv",
            encrypted_mnemonic = encryptedMnemonic,
            derivation_path = "m/44'/60'/0'/0/0",
            chain_type = "ETHEREUM",
            wallet_type = walletType,
            is_active = 1L,
            is_watch_only = 0L,
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

    private fun mockQueryReturning(wallet: Wallet?) {
        val query = mock<Query<Wallet>>()
        whenever(query.executeAsOneOrNull()).thenReturn(wallet)
        whenever(walletQueries.selectById(testWalletIdLong)).thenReturn(query)
    }

    @Test
    fun `executeWithMnemonic successfully decrypts in scoped callback and invalidates handle`() = runBlocking {
        val wallet = createMockWallet()
        mockQueryReturning(wallet)

        val handle = TestPlatformAuthenticator.issueHandle(
            keyId = testKeyAlias,
            operation = AuthOperation.REVEAL,
            intentFingerprint = "reveal_test",
            expiresAtMs = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() + 60_000L,
            walletId = testWalletId
        )
        val authContext = AuthenticationContext(authHandle = handle)

        var capturedMnemonic: String? = null
        val result = useCase.executeWithMnemonic(
            walletId = testWalletId,
            password = testPassword,
            authContext = authContext
        ) { chars ->
            capturedMnemonic = chars.concatToString()
            "REVEALED_SUCCESS"
        }

        assertTrue(result is Result.Success)
        assertEquals("REVEALED_SUCCESS", (result as Result.Success).data)
        assertEquals(testMnemonic, capturedMnemonic)
        assertTrue("Auth handle must be invalidated after execution", handle.isInvalidated)

        verify(auditLogger).logEvent(check { event ->
            assertTrue(event is SecurityAuditEvent.MnemonicRevealed)
            val mEvent = event as SecurityAuditEvent.MnemonicRevealed
            assertEquals(testWalletId, mEvent.walletId)
            assertEquals(testKeyAlias, mEvent.keyAlias)
            assertTrue(mEvent.success)
        })
    }

    @Test
    fun `executeWithMnemonic successfully receives CharArray and zeros memory`() = runBlocking {
        val wallet = createMockWallet()
        mockQueryReturning(wallet)

        val handle = TestPlatformAuthenticator.issueHandle(
            keyId = testKeyAlias,
            operation = AuthOperation.REVEAL,
            intentFingerprint = "reveal_words_test",
            expiresAtMs = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() + 60_000L,
            walletId = testWalletId
        )
        val authContext = AuthenticationContext(authHandle = handle)

        var length = 0
        val result = useCase.executeWithMnemonic(
            walletId = testWalletId,
            password = testPassword,
            authContext = authContext
        ) { chars ->
            length = chars.size
            chars.size
        }

        assertTrue(result is Result.Success)
        assertTrue(length > 0)
        assertEquals(testMnemonic.length, (result as Result.Success).data)
        assertTrue(handle.isInvalidated)
    }

    @Test
    fun `executeWithMnemonic fails when auth handle is null`() = runBlocking {
        val wallet = createMockWallet()
        mockQueryReturning(wallet)

        val authContext = AuthenticationContext(authHandle = null)

        val result = useCase.executeWithMnemonic(
            walletId = testWalletId,
            password = testPassword,
            authContext = authContext
        ) { it.concatToString() }

        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).exception is AuthenticationRequiredException)
    }

    @Test
    fun `executeWithMnemonic fails when auth handle is already invalidated`() = runBlocking {
        val wallet = createMockWallet()
        mockQueryReturning(wallet)

        val handle = TestPlatformAuthenticator.issueHandle(
            keyId = testKeyAlias,
            operation = AuthOperation.REVEAL,
            intentFingerprint = "reveal_test",
            walletId = testWalletId
        )
        handle.invalidate()

        val result = useCase.executeWithMnemonic(
            walletId = testWalletId,
            password = testPassword,
            authContext = AuthenticationContext(authHandle = handle)
        ) { it.concatToString() }

        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).exception is AuthenticationRequiredException)
    }

    @Test
    fun `executeWithMnemonic fails when auth handle has wrong operation (e g SIGN)`() = runBlocking {
        val wallet = createMockWallet()
        mockQueryReturning(wallet)

        val handle = TestPlatformAuthenticator.issueHandle(
            keyId = testKeyAlias,
            operation = AuthOperation.SIGN,
            intentFingerprint = "sign_intent",
            walletId = testWalletId
        )

        val result = useCase.executeWithMnemonic(
            walletId = testWalletId,
            password = testPassword,
            authContext = AuthenticationContext(authHandle = handle)
        ) { it.concatToString() }

        assertTrue(result is Result.Failure)
        val ex = (result as Result.Failure).exception
        assertTrue(ex is AuthenticationRequiredException)
        assertTrue(ex.message?.contains("expected REVEAL") == true)
    }

    @Test
    fun `executeWithMnemonic fails when auth handle has blank keyId`() = runBlocking {
        val wallet = createMockWallet()
        mockQueryReturning(wallet)

        val handle = PlatformAuthHandle(
            keyId = "",
            operation = AuthOperation.REVEAL,
            intentFingerprint = "reveal_test",
            sessionId = "blank_key_session",
            nonce = "blank_key_nonce",
            issuedAtMs = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
            expiresAtMs = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() + 60_000L,
            proofToken = "invalid_token",
            walletId = testWalletId
        )

        val result = useCase.executeWithMnemonic(
            walletId = testWalletId,
            password = testPassword,
            authContext = AuthenticationContext(authHandle = handle)
        ) { it.concatToString() }

        assertTrue(result is Result.Failure)
        val ex = (result as Result.Failure).exception
        assertTrue(ex is AuthenticationRequiredException)
        assertTrue(ex.message?.contains("Blank keyId") == true)
    }

    @Test
    fun `executeWithMnemonic fails when auth handle has EXPORT operation (strictly rejected)`() = runBlocking {
        val wallet = createMockWallet()
        mockQueryReturning(wallet)

        val handle = TestPlatformAuthenticator.issueHandle(
            keyId = testKeyAlias,
            operation = AuthOperation.EXPORT,
            intentFingerprint = "export_intent",
            walletId = testWalletId
        )

        val result = useCase.executeWithMnemonic(
            walletId = testWalletId,
            password = testPassword,
            authContext = AuthenticationContext(authHandle = handle)
        ) { it.concatToString() }

        assertTrue(result is Result.Failure)
        val ex = (result as Result.Failure).exception
        assertTrue(ex is AuthenticationRequiredException)
        assertTrue(ex.message?.contains("expected REVEAL") == true)
    }

    @Test
    fun `executeWithMnemonic fails when auth handle keyId belongs to another wallet (cross-key rejection)`() = runBlocking {
        val wallet = createMockWallet()
        mockQueryReturning(wallet)

        val handle = TestPlatformAuthenticator.issueHandle(
            keyId = "attacker_foreign_key_id",
            operation = AuthOperation.REVEAL,
            intentFingerprint = "reveal_test",
            walletId = testWalletId
        )

        val result = useCase.executeWithMnemonic(
            walletId = testWalletId,
            password = testPassword,
            authContext = AuthenticationContext(authHandle = handle)
        ) { it.concatToString() }

        assertTrue(result is Result.Failure)
        val ex = (result as Result.Failure).exception
        assertTrue(ex is AuthenticationRequiredException)
        assertTrue(ex.message?.contains("Cross-key auth handle rejected") == true)
    }

    @Test
    fun `executeWithMnemonic fails on Keystone hardware wallet`() = runBlocking {
        val hardwareWallet = createMockWallet(walletType = WalletType.KEYSTONE.name)
        mockQueryReturning(hardwareWallet)

        val handle = TestPlatformAuthenticator.issueHandle(
            keyId = testKeyAlias,
            operation = AuthOperation.REVEAL,
            walletId = testWalletId
        )

        val result = useCase.executeWithMnemonic(
            walletId = testWalletId,
            password = testPassword,
            authContext = AuthenticationContext(authHandle = handle)
        ) { it.concatToString() }

        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).exception is UnsupportedOperationException)
    }

    @Test
    fun `executeWithMnemonic fails when wallet is not found`() = runBlocking {
        mockQueryReturning(null)

        val handle = TestPlatformAuthenticator.issueHandle(
            keyId = testKeyAlias,
            operation = AuthOperation.REVEAL,
            walletId = testWalletId
        )

        val result = useCase.executeWithMnemonic(
            walletId = testWalletId,
            password = testPassword,
            authContext = AuthenticationContext(authHandle = handle)
        ) { it.concatToString() }

        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).exception is IllegalArgumentException)
    }

    @Test
    fun `executeWithMnemonic fails when password is wrong and logs audit failure`() = runBlocking {
        val wallet = createMockWallet()
        mockQueryReturning(wallet)

        val handle = TestPlatformAuthenticator.issueHandle(
            keyId = testKeyAlias,
            operation = AuthOperation.REVEAL,
            walletId = testWalletId
        )

        val result = useCase.executeWithMnemonic(
            walletId = testWalletId,
            password = "WrongPassword#999",
            authContext = AuthenticationContext(authHandle = handle)
        ) { it.concatToString() }

        assertTrue(result is Result.Failure)
        assertTrue(handle.isInvalidated)

        verify(auditLogger).logEvent(check { event ->
            assertTrue(event is SecurityAuditEvent.MnemonicRevealed)
            val mEvent = event as SecurityAuditEvent.MnemonicRevealed
            assertFalse(mEvent.success)
        })
    }
}


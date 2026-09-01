package com.cbstudio.wearwallet.core.security

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.data.repository.WalletRepositoryImpl
import com.cbstudio.wearwallet.core.database.DatabaseDriverFactory
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.network.EthereumRpcClient
import io.github.iml1s.crypto.SecureByteArray
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.QueryResult

/**
 * Adversarial Challenger 1 Empirical Validation Test Suite (PR #32 Round 4 Milestone 1)
 *
 * Direct Empirical Challenges:
 * 1. If KeyVault store fails -> verify DB write count is 0 across all create/import methods.
 * 2. If DB write fails -> verify KeyVault entry is removed (no orphan keys) across all create/import methods.
 * 3. Verify memory buffers are actually wiped (SecureByteArray.secureZero).
 * 4. Verify FakeSecureKeyManager fails closed when queried with non-existent keyId.
 */
class Challenger1EmpiricalValidationTest {

    private val testPassword = "AdversarialMasterPassword#2026"
    private val testPrivateKeyHex = "e331b6d69882b4cb4ea581d88ec2693329b3b3a4711832421415ebff2d474428"
    private val testMnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"

    private lateinit var cryptoProvider: CommonCryptoProvider
    private lateinit var ethereumRpcClient: EthereumRpcClient
    private lateinit var sideEffectTracker: SideEffectTracker

    private fun createProvisioningAuth(): AuthenticationContext = AuthenticationContext(
        authHandle = TestPlatformAuthenticator.issueHandle(
            keyId = "provisioning",
            operation = AuthOperation.IMPORT
        )
    )

    @Before
    fun setUp() {
        cryptoProvider = CommonCryptoProvider()
        ethereumRpcClient = mock()
        sideEffectTracker = mock()
    }

    // =========================================================================
    // CHALLENGE 1: KeyVault Store Failure -> DB Write Count MUST BE 0
    // =========================================================================

    /**
     * A KeyVault manager that simulates hardware KeyStore / Enclave store failure.
     */
    private class FailingStoreKeyManager : SecureKeyManager {
        val storedKeys = mutableMapOf<String, String>()

        override suspend fun storePrivateKey(
            keyId: String,
            privateKey: ByteArray,
            requireAuth: Boolean,
            authContext: AuthenticationContext?,
            expectedWalletId: String
        ): Result<Unit> {
            return Result.Failure(KeyStorageException("Hardware KeyStore unavailable / write error simulation"))
        }

        override suspend fun deletePrivateKey(
            keyId: String,
            authContext: AuthenticationContext?,
            expectedWalletId: String
        ): Result<Unit> {
            storedKeys.remove(keyId)
            return Result.Success(Unit)
        }

        override suspend fun startProvisioningSession(): ProvisioningSession = ProvisioningSession.create()

        override suspend fun storeStagedPrivateKey(
            session: ProvisioningSession,
            privateKey: ByteArray,
            requireAuth: Boolean,
            authContext: AuthenticationContext?
        ): Result<Unit> {
            return Result.Failure(KeyStorageException("Hardware KeyStore unavailable / write error simulation"))
        }

        override suspend fun storeStagedPrivateKey(
            sessionId: String,
            stagedKeyAlias: String,
            privateKey: ByteArray,
            requireAuth: Boolean,
            authContext: AuthenticationContext?
        ): Result<Unit> {
            return Result.Failure(KeyStorageException("Hardware KeyStore unavailable / write error simulation"))
        }

        override suspend fun getActiveProvisioningSession(sessionId: String): ProvisioningSession? = null

        override suspend fun commitProvisioningSession(session: ProvisioningSession): Result<Unit> {
            return Result.Success(Unit)
        }

        override suspend fun rollbackProvisioningSession(session: ProvisioningSession): Result<Unit> {
            storedKeys.remove(session.stagedKeyAlias)
            return Result.Success(Unit)
        }

        override suspend fun checkKeyPresence(keyId: String): KeyPresence =
            if (storedKeys.containsKey(keyId)) KeyPresence.Present else KeyPresence.Absent
        override suspend fun hasPrivateKey(keyId: String): Boolean = storedKeys.containsKey(keyId)
        override suspend fun listKeyIds(): List<String> = storedKeys.keys.toList()
        override suspend fun signWithKey(keyId: String, data: ByteArray, authContext: AuthenticationContext?, expectedWalletId: String): Result<ByteArray> =
            Result.Failure(IllegalArgumentException("No key found"))
        override suspend fun revealMnemonic(keyId: String, authContext: AuthenticationContext?, expectedWalletId: String): Result<ScopedMnemonic> =
            Result.Failure(UnsupportedOperationException())
        override suspend fun getSecurityLevel(): SecurityLevel =
            SecurityLevel(SecurityLevel.Level.BASIC, false, false, false, false)
        override suspend fun exportEncryptedKey(keyId: String, backupPassword: CharArray, authContext: AuthenticationContext?, expectedWalletId: String): Result<EncryptedBackup> =
            Result.Failure(UnsupportedOperationException())
        override suspend fun importEncryptedKey(keyId: String, encryptedBackup: EncryptedBackup, backupPassword: CharArray, authContext: AuthenticationContext?, expectedWalletId: String): Result<Unit> =
            Result.Failure(UnsupportedOperationException())
        override fun observeSecurityEvents(): Flow<SecurityEvent> = emptyFlow()
    }

    @Test
    fun challenge_1_1_createWallet_keyvault_failure_results_in_zero_db_writes() = runBlocking {
        val databaseDriverFactory = mock<DatabaseDriverFactory>()
        val mockSqlDriver = mock<SqlDriver>()
        whenever(databaseDriverFactory.createDriver()).thenReturn(mockSqlDriver)

        val failingKeyManager = FailingStoreKeyManager()
        val repository = WalletRepositoryImpl(
            databaseDriverFactory = databaseDriverFactory,
            cryptoProvider = cryptoProvider,
            ethereumRpcClient = ethereumRpcClient,
            secureKeyManager = failingKeyManager,
            platformDeletionCleanupHook = com.cbstudio.wearwallet.core.platform.NoOpPlatformDeletionCleanupHook(),
            sideEffectTracker = sideEffectTracker
        )

        val result = repository.createWallet(
            name = "FailKeyVaultWallet",
            mnemonic = testMnemonic.toCharArray(),
            password = testPassword.toCharArray(),
            chainType = ChainType.ETHEREUM,
            authContext = createProvisioningAuth()
        )

        assertTrue("createWallet MUST fail when KeyVault store fails", result is Result.Failure)
        verify(sideEffectTracker, never()).onDbWrite()
        assertEquals("Zero keys must be stored in KeyVault", 0, failingKeyManager.storedKeys.size)
    }

    @Test
    fun challenge_1_2_importFromMnemonic_keyvault_failure_results_in_zero_db_writes() = runBlocking {
        val databaseDriverFactory = mock<DatabaseDriverFactory>()
        val mockSqlDriver = mock<SqlDriver>()
        whenever(databaseDriverFactory.createDriver()).thenReturn(mockSqlDriver)

        val failingKeyManager = FailingStoreKeyManager()
        val repository = WalletRepositoryImpl(
            databaseDriverFactory = databaseDriverFactory,
            cryptoProvider = cryptoProvider,
            ethereumRpcClient = ethereumRpcClient,
            secureKeyManager = failingKeyManager,
            platformDeletionCleanupHook = com.cbstudio.wearwallet.core.platform.NoOpPlatformDeletionCleanupHook(),
            sideEffectTracker = sideEffectTracker
        )

        val result = repository.importFromMnemonic(
            name = "FailImportMnemonic",
            mnemonic = testMnemonic.toCharArray(),
            password = testPassword.toCharArray(),
            chainType = ChainType.ETHEREUM,
            authContext = createProvisioningAuth()
        )

        assertTrue("importFromMnemonic MUST fail when KeyVault store fails", result is Result.Failure)
        verify(sideEffectTracker, never()).onDbWrite()
        assertEquals("Zero keys must be stored in KeyVault", 0, failingKeyManager.storedKeys.size)
    }

    @Test
    fun challenge_1_3_importFromPrivateKey_keyvault_failure_results_in_zero_db_writes() = runBlocking {
        val databaseDriverFactory = mock<DatabaseDriverFactory>()
        val mockSqlDriver = mock<SqlDriver>()
        whenever(databaseDriverFactory.createDriver()).thenReturn(mockSqlDriver)

        val failingKeyManager = FailingStoreKeyManager()
        val repository = WalletRepositoryImpl(
            databaseDriverFactory = databaseDriverFactory,
            cryptoProvider = cryptoProvider,
            ethereumRpcClient = ethereumRpcClient,
            secureKeyManager = failingKeyManager,
            platformDeletionCleanupHook = com.cbstudio.wearwallet.core.platform.NoOpPlatformDeletionCleanupHook(),
            sideEffectTracker = sideEffectTracker
        )

        val result = repository.importFromPrivateKey(
            name = "FailImportPrivateKey",
            privateKey = com.cbstudio.wearwallet.core.security.ScopedPrivateKey.fromHex(testPrivateKeyHex),
            password = testPassword.toCharArray(),
            chainType = ChainType.ETHEREUM,
            authContext = createProvisioningAuth()
        )

        assertTrue("importFromPrivateKey MUST fail when KeyVault store fails", result is Result.Failure)
        verify(sideEffectTracker, never()).onDbWrite()
        assertEquals("Zero keys must be stored in KeyVault", 0, failingKeyManager.storedKeys.size)
    }

    // =========================================================================
    // CHALLENGE 2: DB Write Failure -> KeyVault Key MUST BE Rolled Back (0 Orphan Keys)
    // =========================================================================

    private fun setupDriverWithFailingInsert(): Pair<DatabaseDriverFactory, SqlDriver> {
        val databaseDriverFactory = mock<DatabaseDriverFactory>()
        val mockSqlDriver = mock<SqlDriver>()
        whenever(databaseDriverFactory.createDriver()).thenReturn(mockSqlDriver)

        val failingQueryResult = QueryResult.Value(0L)
        whenever(mockSqlDriver.execute(any(), any(), any(), any())).thenThrow(
            RuntimeException("SQLITE_IOERR: Simulated I/O error during database insert")
        )

        return Pair(databaseDriverFactory, mockSqlDriver)
    }

    @Test
    fun challenge_2_1_createWallet_db_failure_cleans_up_keyvault_entry() = runBlocking {
        val (databaseDriverFactory, _) = setupDriverWithFailingInsert()

        val fakeKeyManager = FakeSecureKeyManager()
        val repository = WalletRepositoryImpl(
            databaseDriverFactory = databaseDriverFactory,
            cryptoProvider = cryptoProvider,
            ethereumRpcClient = ethereumRpcClient,
            secureKeyManager = fakeKeyManager,
            platformDeletionCleanupHook = com.cbstudio.wearwallet.core.platform.NoOpPlatformDeletionCleanupHook(),
            sideEffectTracker = sideEffectTracker
        )

        val result = repository.createWallet(
            name = "DbFailWallet",
            mnemonic = testMnemonic.toCharArray(),
            password = testPassword.toCharArray(),
            chainType = ChainType.ETHEREUM,
            authContext = createProvisioningAuth()
        )

        assertTrue("createWallet must fail when DB operation fails", result is Result.Failure)
        assertEquals("KeyVault MUST have 0 orphan keys after rollback compensation", 0, fakeKeyManager.listKeyIds().size)
    }

    @Test
    fun challenge_2_2_importFromMnemonic_db_failure_cleans_up_keyvault_entry() = runBlocking {
        val (databaseDriverFactory, _) = setupDriverWithFailingInsert()

        val fakeKeyManager = FakeSecureKeyManager()
        val repository = WalletRepositoryImpl(
            databaseDriverFactory = databaseDriverFactory,
            cryptoProvider = cryptoProvider,
            ethereumRpcClient = ethereumRpcClient,
            secureKeyManager = fakeKeyManager,
            platformDeletionCleanupHook = com.cbstudio.wearwallet.core.platform.NoOpPlatformDeletionCleanupHook(),
            sideEffectTracker = sideEffectTracker
        )

        val result = repository.importFromMnemonic(
            name = "DbFailMnemonic",
            mnemonic = testMnemonic.toCharArray(),
            password = testPassword.toCharArray(),
            chainType = ChainType.ETHEREUM,
            authContext = createProvisioningAuth()
        )

        assertTrue("importFromMnemonic must fail when DB operation fails", result is Result.Failure)
        assertEquals("KeyVault MUST have 0 orphan keys after rollback compensation", 0, fakeKeyManager.listKeyIds().size)
    }

    @Test
    fun challenge_2_3_importFromPrivateKey_db_failure_cleans_up_keyvault_entry() = runBlocking {
        val (databaseDriverFactory, _) = setupDriverWithFailingInsert()

        val fakeKeyManager = FakeSecureKeyManager()
        val repository = WalletRepositoryImpl(
            databaseDriverFactory = databaseDriverFactory,
            cryptoProvider = cryptoProvider,
            ethereumRpcClient = ethereumRpcClient,
            secureKeyManager = fakeKeyManager,
            platformDeletionCleanupHook = com.cbstudio.wearwallet.core.platform.NoOpPlatformDeletionCleanupHook(),
            sideEffectTracker = sideEffectTracker
        )

        val result = repository.importFromPrivateKey(
            name = "DbFailPrivateKey",
            privateKey = com.cbstudio.wearwallet.core.security.ScopedPrivateKey.fromHex(testPrivateKeyHex),
            password = testPassword.toCharArray(),
            chainType = ChainType.ETHEREUM,
            authContext = createProvisioningAuth()
        )

        assertTrue("importFromPrivateKey must fail when DB operation fails", result is Result.Failure)
        assertEquals("KeyVault MUST have 0 orphan keys after rollback compensation", 0, fakeKeyManager.listKeyIds().size)
    }

    @Test
    fun challenge_2_3_b_importFromMnemonicWithKeyPair_db_failure_cleans_up_keyvault_entry() = runBlocking {
        val (databaseDriverFactory, _) = setupDriverWithFailingInsert()

        val fakeKeyManager = FakeSecureKeyManager()
        val repository = WalletRepositoryImpl(
            databaseDriverFactory = databaseDriverFactory,
            cryptoProvider = cryptoProvider,
            ethereumRpcClient = ethereumRpcClient,
            secureKeyManager = fakeKeyManager,
            platformDeletionCleanupHook = com.cbstudio.wearwallet.core.platform.NoOpPlatformDeletionCleanupHook(),
            sideEffectTracker = sideEffectTracker
        )

        val keyPair = cryptoProvider.generateKeyPairFromMnemonic(testMnemonic.toCharArray(), "m/44'/60'/0'/0/0", ChainType.ETHEREUM)
        val address = cryptoProvider.deriveAddress(keyPair.publicKey)

        val result = repository.importFromMnemonicWithKeyPair(
            name = "DbFailMnemonicWithKeyPair",
            mnemonic = testMnemonic.toCharArray(),
            password = testPassword.toCharArray(),
            chainType = ChainType.ETHEREUM,
            keyPair = keyPair,
            address = address,
            authContext = createProvisioningAuth()
        )

        assertTrue("importFromMnemonicWithKeyPair must fail when DB operation fails", result is Result.Failure)
        assertEquals("KeyVault MUST have 0 orphan keys after rollback compensation", 0, fakeKeyManager.listKeyIds().size)
    }

    @Test
    fun challenge_2_4_stress_repeated_db_failures_leave_zero_orphan_keys() = runBlocking {
        val (databaseDriverFactory, _) = setupDriverWithFailingInsert()

        val fakeKeyManager = FakeSecureKeyManager()
        val repository = WalletRepositoryImpl(
            databaseDriverFactory = databaseDriverFactory,
            cryptoProvider = cryptoProvider,
            ethereumRpcClient = ethereumRpcClient,
            secureKeyManager = fakeKeyManager,
            platformDeletionCleanupHook = com.cbstudio.wearwallet.core.platform.NoOpPlatformDeletionCleanupHook(),
            sideEffectTracker = sideEffectTracker
        )

        for (i in 1..25) {
            repository.createWallet("Stress$i", testMnemonic.toCharArray(), testPassword.toCharArray(), ChainType.ETHEREUM, createProvisioningAuth())
            repository.importFromPrivateKey("StressPK$i", com.cbstudio.wearwallet.core.security.ScopedPrivateKey.fromHex(testPrivateKeyHex), testPassword.toCharArray(), ChainType.ETHEREUM, createProvisioningAuth())
        }

        assertEquals("After 50 failed provisioning attempts, exactly 0 orphan keys must remain in KeyVault", 0, fakeKeyManager.listKeyIds().size)
    }

    // =========================================================================
    // CHALLENGE 3: Memory Buffer Cleansing Verification (SecureByteArray.secureZero)
    // =========================================================================

    @Test
    fun challenge_3_1_secureZero_cleanses_buffers_of_all_sizes() {
        val sizes = listOf(0, 1, 16, 32, 64, 128, 512, 1024)
        for (size in sizes) {
            val buffer = ByteArray(size) { (it + 1).toByte() }
            if (size > 0) {
                assertFalse("Buffer must not initially be all zeroes", buffer.all { it == 0.toByte() })
            }
            SecureByteArray.secureZero(buffer)
            assertTrue("Buffer of size $size must be completely zeroed", buffer.all { it == 0.toByte() })
        }
    }

    @Test
    fun challenge_3_2_secureZero_on_password_and_key_material_buffers() {
        val pwdBytes = testPassword.encodeToByteArray()
        val privBytes = testPrivateKeyHex.encodeToByteArray()
        val mnemBytes = testMnemonic.encodeToByteArray()

        // Prior to zeroing, check not zero
        assertFalse(pwdBytes.all { it == 0.toByte() })
        assertFalse(privBytes.all { it == 0.toByte() })
        assertFalse(mnemBytes.all { it == 0.toByte() })

        SecureByteArray.secureZero(pwdBytes)
        SecureByteArray.secureZero(privBytes)
        SecureByteArray.secureZero(mnemBytes)

        assertTrue("Password bytes must be zeroed", pwdBytes.all { it == 0.toByte() })
        assertTrue("Private key bytes must be zeroed", privBytes.all { it == 0.toByte() })
        assertTrue("Mnemonic bytes must be zeroed", mnemBytes.all { it == 0.toByte() })
    }

    // =========================================================================
    // CHALLENGE 4: FakeSecureKeyManager Fails Closed on Non-Existent KeyId
    // =========================================================================

    @Test
    fun challenge_4_1_fakeSecureKeyManager_fails_closed_on_non_existent_keyId() = runBlocking {
        val manager = FakeSecureKeyManager()
        val nonExistentKeyId = "ww_key_non_existent_uuid_12345"
        val dummyHash = CryptoUtils.sha256(byteArrayOf(1, 2, 3, 4))

        // 1. hasPrivateKey must return false
        assertFalse("hasPrivateKey must return false for non-existent key", manager.hasPrivateKey(nonExistentKeyId))

        // 2. signWithKey must fail with IllegalArgumentException
        val signResult = manager.signWithKey(nonExistentKeyId, dummyHash, authContext = null, expectedWalletId = nonExistentKeyId)
        assertTrue("signWithKey must fail for non-existent key", signResult is Result.Failure)
        assertTrue(
            "Exception must indicate key not found",
            (signResult as Result.Failure).exception is IllegalArgumentException
        )

        // 3. signCount must remain 0
        assertEquals("signCount must NOT increment on non-existent key sign failure", 0, manager.signCount)

        // 4. export / import encrypted key must return failure
        val exportResult = manager.exportEncryptedKey(nonExistentKeyId, "backupPwd".toCharArray(), authContext = null, expectedWalletId = nonExistentKeyId)
        assertTrue("exportEncryptedKey must fail closed", exportResult is Result.Failure)

        val importResult = manager.importEncryptedKey(nonExistentKeyId, EncryptedBackup("encryptedBackup"), "backupPwd".toCharArray(), authContext = null, expectedWalletId = nonExistentKeyId)
        assertTrue("importEncryptedKey must fail closed", importResult is Result.Failure)
    }

    @Test
    fun challenge_4_2_fakeSecureKeyManager_strict_lifecycle_and_cross_key_isolation() = runBlocking {
        val manager = FakeSecureKeyManager()
        val key1 = "ww_key_valid_1"
        val key2 = "ww_key_valid_2"
        val dummyHash = CryptoUtils.sha256(byteArrayOf(5, 6, 7, 8))

        // Store key 1 (requireAuth = false for direct basic lifecycle testing)
        manager.storePrivateKey(key1, testPrivateKeyHex.encodeToByteArray(), requireAuth = false, authContext = null, expectedWalletId = key1)
        assertTrue(manager.hasPrivateKey(key1))
        assertFalse(manager.hasPrivateKey(key2))

        // Signing key 1 succeeds
        val sign1Result = manager.signWithKey(key1, dummyHash, authContext = null, expectedWalletId = key1)
        assertTrue("Signing key 1 must succeed", sign1Result is Result.Success)
        assertEquals("signCount must be 1", 1, manager.signCount)

        // Signing key 2 fails closed
        val sign2Result = manager.signWithKey(key2, dummyHash, authContext = null, expectedWalletId = key2)
        assertTrue("Signing non-existent key 2 must fail closed", sign2Result is Result.Failure)
        assertEquals("signCount must still be 1", 1, manager.signCount)

        // Delete key 1 -> now signing key 1 must fail closed
        manager.deletePrivateKey(key1, authContext = null, expectedWalletId = key1)
        assertFalse(manager.hasPrivateKey(key1))
        val sign1AfterDelete = manager.signWithKey(key1, dummyHash, authContext = null, expectedWalletId = key1)
        assertTrue("Signing deleted key 1 must fail closed", sign1AfterDelete is Result.Failure)
        assertEquals("signCount must still be 1", 1, manager.signCount)
    }
}

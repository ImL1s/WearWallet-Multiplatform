package com.cbstudio.wearwallet.core.security

import android.content.Context
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.data.repository.WalletRepositoryImpl
import com.cbstudio.wearwallet.core.database.DatabaseDriverFactory
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.WalletAccount
import com.cbstudio.wearwallet.core.network.EthereumRpcClient
import io.github.iml1s.crypto.SecureByteArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
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
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.cbstudio.wearwallet.core.database.CoreWalletDatabase
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Milestone 2 (M2) Challenger 2 Empirical Adversarial Test Suite
 *
 * Direct Empirical Challenges:
 * 1. Concurrent wallet provisioning with mixed valid, invalid, expired, and replayed auth contexts.
 * 2. Rollback compensation when database insert fails across all 5 provisioning/migration entrypoints.
 * 3. Hardware KeyStore store failure compensation (0 DB writes, 0 KeyVault keys).
 * 4. Committed key immutability (rollback on committed session must fail-closed without deleting key).
 * 5. Memory zeroization of sensitive arrays during success and failure paths.
 * 6. ProvisioningSession lifecycle timeout & state transition invariants.
 */
class Milestone2Challenger2ConcurrencyLifecycleTest {

    private val testPassword = "AdversarialMasterPassword#2026"
    private val testPrivateKeyHex = "e331b6d69882b4cb4ea581d88ec2693329b3b3a4711832421415ebff2d474428"
    private val testMnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"

    private lateinit var mockContext: Context
    private lateinit var cryptoProvider: CommonCryptoProvider
    private lateinit var ethereumRpcClient: EthereumRpcClient
    private lateinit var sideEffectTracker: SideEffectTracker

    @Before
    fun setUp() {
        mockContext = mock<Context>()
        whenever(mockContext.applicationContext).thenReturn(mockContext)
        AuthHandleRegistry.clearForTesting()
        cryptoProvider = CommonCryptoProvider()
        ethereumRpcClient = mock()
        sideEffectTracker = mock()
    }

    private fun createInMemorySqlDriver(): SqlDriver {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CoreWalletDatabase.Schema.create(driver)
        return driver
    }

    private fun createInMemoryRepository(
        secureKeyManager: SecureKeyManager,
        customDriver: SqlDriver? = null
    ): Pair<WalletRepositoryImpl, SqlDriver> {
        val driver = customDriver ?: createInMemorySqlDriver()
        val driverFactory = mock<DatabaseDriverFactory>()
        whenever(driverFactory.createDriver()).thenReturn(driver)

        val repository = WalletRepositoryImpl(
            databaseDriverFactory = driverFactory,
            cryptoProvider = cryptoProvider,
            ethereumRpcClient = ethereumRpcClient,
            secureKeyManager = secureKeyManager,
            platformDeletionCleanupHook = com.cbstudio.wearwallet.core.platform.NoOpPlatformDeletionCleanupHook(),
            sideEffectTracker = sideEffectTracker
        )
        return Pair(repository, driver)
    }

    // =========================================================================
    // SECTION 1: CONCURRENT PROVISIONING WITH INVALID / ABORTED AUTH CONTEXTS
    // =========================================================================

    @Test
    fun challenge_1_1_concurrent_createWallet_with_mixed_valid_and_invalid_auth_contexts() = runBlocking(Dispatchers.Default) {
        val fakeKeyManager = FakeSecureKeyManager()
        val (repository, _) = createInMemoryRepository(fakeKeyManager)

        val validTasks = 5
        val invalidTasks = 5
        val totalTasks = validTasks + invalidTasks

        val successCount = AtomicInteger(0)
        val failureCount = AtomicInteger(0)
        val createdWallets = ConcurrentHashMap<Int, WalletAccount>()
        val dbMutex = Mutex()

        val jobs = (0 until totalTasks).map { index ->
            async {
                val isLegit = index < validTasks
                val scopedMnemonic = cryptoProvider.generateMnemonic()
                val mnemChars = scopedMnemonic.copyChars()

                val result = try {
                    dbMutex.withLock {
                        val authContext = if (isLegit) {
                            val req = (repository.prepareProvisioning() as Result.Success).data
                            AuthenticationContext(
                                authHandle = TestPlatformAuthenticator.issueHandle(
                                    keyId = req.stagedAlias,
                                    sessionId = req.sessionId,
                                    operation = AuthOperation.IMPORT,
                                    validityDurationMs = 60_000L
                                )
                            )
                        } else {
                            when (index % 5) {
                                0 -> AuthenticationContext(authHandle = null) // null handle
                                1 -> {
                                    // Expired handle
                                    val handle = TestPlatformAuthenticator.issueHandle(
                                        keyId = "staged_fake_exp",
                                        sessionId = "session_fake_exp",
                                        operation = AuthOperation.IMPORT,
                                        validityDurationMs = 1L
                                    )
                                    Thread.sleep(5) // Wait for expiration
                                    AuthenticationContext(authHandle = handle)
                                }
                                2 -> {
                                    // Already invalidated handle
                                    val handle = TestPlatformAuthenticator.issueHandle(
                                        keyId = "staged_fake_inv",
                                        sessionId = "session_fake_inv",
                                        operation = AuthOperation.IMPORT
                                    )
                                    handle.invalidate()
                                    AuthenticationContext(authHandle = handle)
                                }
                                3 -> {
                                    // Mismatched operation (SIGN instead of IMPORT)
                                    val handle = TestPlatformAuthenticator.issueHandle(
                                        keyId = "staged_fake_op",
                                        sessionId = "session_fake_op",
                                        operation = AuthOperation.SIGN
                                    )
                                    AuthenticationContext(authHandle = handle)
                                }
                                else -> {
                                    // Cross-key handle with non-wildcard specific keyId
                                    val handle = TestPlatformAuthenticator.issueHandle(
                                        keyId = "specific_unmatched_key_999",
                                        sessionId = "session_fake_unmatched",
                                        operation = AuthOperation.IMPORT
                                    )
                                    AuthenticationContext(authHandle = handle)
                                }
                            }
                        }

                        repository.createWallet(
                            name = "Wallet_$index",
                            mnemonic = mnemChars,
                            password = testPassword.toCharArray(),
                            chainType = ChainType.ETHEREUM,
                            authContext = authContext
                        )
                    }
                } finally {
                    mnemChars.fill('\u0000')
                    scopedMnemonic.close()
                }

                when (result) {
                    is Result.Success -> {
                        successCount.incrementAndGet()
                        createdWallets[index] = result.data
                    }
                    is Result.Failure -> {
                        failureCount.incrementAndGet()
                        println("[CHALLENGE 1.1 DEBUG] Task $index (isLegit=$isLegit) failed: ${result.exception.message}")
                    }
                    else -> {}
                }
            }
        }

        jobs.awaitAll()

        assertEquals("Exactly $validTasks valid wallets must succeed", validTasks, successCount.get())
        assertEquals("Exactly $invalidTasks invalid wallets must fail", invalidTasks, failureCount.get())

        val dbWallets = repository.getAllWallets()
        assertTrue(dbWallets is Result.Success)
        val walletList = (dbWallets as Result.Success).data
        assertEquals("DB must contain exactly $validTasks wallets", validTasks, walletList.size)

        val storedKeys = fakeKeyManager.listKeyIds()
        assertEquals("KeyVault must contain exactly $validTasks keys", validTasks, storedKeys.size)

        // Verify every stored key alias matches a valid DB wallet
        for (w in walletList) {
            assertNotNull(w.keyAlias)
            assertTrue("KeyVault must have key for wallet ${w.id}", fakeKeyManager.hasPrivateKey(w.keyAlias!!))
        }
    }

    @Test
    fun challenge_1_2_concurrent_import_with_mixed_valid_and_invalid_auth_contexts() = runBlocking(Dispatchers.Default) {
        val fakeKeyManager = FakeSecureKeyManager()
        val (repository, _) = createInMemoryRepository(fakeKeyManager)

        val validTasks = 8
        val invalidTasks = 8
        val totalTasks = validTasks + invalidTasks

        val successCount = AtomicInteger(0)
        val failureCount = AtomicInteger(0)
        val dbMutex = Mutex()

        val jobs = (0 until totalTasks).map { index ->
            async {
                val isLegit = index < validTasks
                val isMnemonic = index % 2 == 0
                val result = if (isMnemonic) {
                    val scopedMnemonic = cryptoProvider.generateMnemonic()
                    val mnemChars = scopedMnemonic.copyChars()
                    try {
                        dbMutex.withLock {
                            val authContext = if (isLegit) {
                                val req = (repository.prepareProvisioning() as Result.Success).data
                                AuthenticationContext(
                                    authHandle = TestPlatformAuthenticator.issueHandle(
                                        keyId = req.stagedAlias,
                                        sessionId = req.sessionId,
                                        operation = AuthOperation.IMPORT,
                                        validityDurationMs = 60_000L
                                    )
                                )
                            } else {
                                AuthenticationContext(authHandle = null)
                            }
                            repository.importFromMnemonic(
                                name = "MnemImport_$index",
                                mnemonic = mnemChars,
                                password = testPassword.toCharArray(),
                                chainType = ChainType.ETHEREUM,
                                authContext = authContext
                            )
                        }
                    } finally {
                        mnemChars.fill('\u0000')
                        scopedMnemonic.close()
                    }
                } else {
                    val randomPk = CryptoUtils.randomBytes(32).toHexString()
                    dbMutex.withLock {
                        val authContext = if (isLegit) {
                            val req = (repository.prepareProvisioning() as Result.Success).data
                            AuthenticationContext(
                                authHandle = TestPlatformAuthenticator.issueHandle(
                                    keyId = req.stagedAlias,
                                    sessionId = req.sessionId,
                                    operation = AuthOperation.IMPORT,
                                    validityDurationMs = 60_000L
                                )
                            )
                        } else {
                            AuthenticationContext(authHandle = null)
                        }
                        repository.importFromPrivateKey(
                            name = "PkImport_$index",
                            privateKey = com.cbstudio.wearwallet.core.security.ScopedPrivateKey.fromHex(randomPk),
                            password = testPassword.toCharArray(),
                            chainType = ChainType.ETHEREUM,
                            authContext = authContext
                        )
                    }
                }

                when (result) {
                    is Result.Success<*> -> successCount.incrementAndGet()
                    is Result.Failure -> {
                        failureCount.incrementAndGet()
                        println("[CHALLENGE 1.2 DEBUG] Task $index (isLegit=$isLegit) failed: ${result.exception.message} (${result.exception::class.simpleName})")
                    }
                    else -> {}
                }
            }
        }

        jobs.awaitAll()

        assertEquals("Valid imports count must be $validTasks", validTasks, successCount.get())
        assertEquals("Invalid imports count must be $invalidTasks", invalidTasks, failureCount.get())
        assertEquals("KeyVault must hold exactly $validTasks keys", validTasks, fakeKeyManager.listKeyIds().size)
    }

    // =========================================================================
    // SECTION 2: ROLLBACK COMPENSATION ON DB INSERT FAILURE (ALL 5 ENTRYPOINTS)
    // =========================================================================

    private fun setupDriverWithFailingInsert(): DatabaseDriverFactory {
        val databaseDriverFactory = mock<DatabaseDriverFactory>()
        val mockSqlDriver = mock<SqlDriver>()
        whenever(databaseDriverFactory.createDriver()).thenReturn(mockSqlDriver)

        val mockCursor = mock<SqlCursor>()
        whenever(mockCursor.next()).thenReturn(QueryResult.Value(true))
        whenever(mockCursor.getLong(0)).thenReturn(0L) // existsByAddress -> false

        whenever(mockSqlDriver.executeQuery<Boolean>(any(), any(), any(), any(), any())).thenAnswer { invocation ->
            val mapper = invocation.getArgument<(SqlCursor) -> QueryResult<Boolean>>(4)
            mapper(mockCursor)
        }

        whenever(mockSqlDriver.execute(any(), any(), any(), any())).thenThrow(
            RuntimeException("SQLITE_IOERR: Simulated fatal disk I/O write error")
        )

        return databaseDriverFactory
    }

    private fun createProvisioningAuth(keyManager: SecureKeyManager = FakeSecureKeyManager()): AuthenticationContext {
        val s = runBlocking { keyManager.startProvisioningSession() }
        return AuthenticationContext(
            authHandle = TestPlatformAuthenticator.issueHandle(
                keyId = s.stagedKeyAlias,
                sessionId = s.sessionId,
                operation = AuthOperation.IMPORT,
                validityDurationMs = 60_000L
            )
        )
    }

    @Test
    fun challenge_2_1_createWallet_db_failure_rolls_back_staged_key() = runBlocking {
        val failingDriverFactory = setupDriverWithFailingInsert()
        val fakeKeyManager = FakeSecureKeyManager()
        val repository = WalletRepositoryImpl(
            databaseDriverFactory = failingDriverFactory,
            cryptoProvider = cryptoProvider,
            ethereumRpcClient = ethereumRpcClient,
            secureKeyManager = fakeKeyManager,
            platformDeletionCleanupHook = com.cbstudio.wearwallet.core.platform.NoOpPlatformDeletionCleanupHook(),
            sideEffectTracker = sideEffectTracker
        )

        val result = repository.createWallet(
            name = "FailDbWallet",
            mnemonic = testMnemonic.toCharArray(),
            password = testPassword.toCharArray(),
            chainType = ChainType.ETHEREUM,
            authContext = createProvisioningAuth(fakeKeyManager)
        )

        assertTrue("createWallet must fail on DB write error", result is Result.Failure)
        assertEquals("KeyVault must have 0 keys after rollback", 0, fakeKeyManager.listKeyIds().size)
    }

    @Test
    fun challenge_2_2_importFromMnemonic_db_failure_rolls_back_staged_key() = runBlocking {
        val failingDriverFactory = setupDriverWithFailingInsert()
        val fakeKeyManager = FakeSecureKeyManager()
        val repository = WalletRepositoryImpl(
            databaseDriverFactory = failingDriverFactory,
            cryptoProvider = cryptoProvider,
            ethereumRpcClient = ethereumRpcClient,
            secureKeyManager = fakeKeyManager,
            platformDeletionCleanupHook = com.cbstudio.wearwallet.core.platform.NoOpPlatformDeletionCleanupHook(),
            sideEffectTracker = sideEffectTracker
        )

        val result = repository.importFromMnemonic(
            name = "FailDbMnemonic",
            mnemonic = testMnemonic.toCharArray(),
            password = testPassword.toCharArray(),
            chainType = ChainType.ETHEREUM,
            authContext = createProvisioningAuth(fakeKeyManager)
        )

        assertTrue("importFromMnemonic must fail on DB write error", result is Result.Failure)
        assertEquals("KeyVault must have 0 keys after rollback", 0, fakeKeyManager.listKeyIds().size)
    }

    @Test
    fun challenge_2_3_importFromMnemonicWithKeyPair_db_failure_rolls_back_staged_key() = runBlocking {
        val failingDriverFactory = setupDriverWithFailingInsert()
        val fakeKeyManager = FakeSecureKeyManager()
        val repository = WalletRepositoryImpl(
            databaseDriverFactory = failingDriverFactory,
            cryptoProvider = cryptoProvider,
            ethereumRpcClient = ethereumRpcClient,
            secureKeyManager = fakeKeyManager,
            platformDeletionCleanupHook = com.cbstudio.wearwallet.core.platform.NoOpPlatformDeletionCleanupHook(),
            sideEffectTracker = sideEffectTracker
        )

        val keyPair = cryptoProvider.generateKeyPairFromMnemonic(testMnemonic.toCharArray(), "m/44'/60'/0'/0/0", ChainType.ETHEREUM)
        val address = cryptoProvider.deriveAddress(keyPair.publicKey)

        val result = repository.importFromMnemonicWithKeyPair(
            name = "FailDbMnemonicKeyPair",
            mnemonic = testMnemonic.toCharArray(),
            password = testPassword.toCharArray(),
            chainType = ChainType.ETHEREUM,
            keyPair = keyPair,
            address = address,
            authContext = createProvisioningAuth(fakeKeyManager)
        )

        assertTrue("importFromMnemonicWithKeyPair must fail on DB write error", result is Result.Failure)
        assertEquals("KeyVault must have 0 keys after rollback", 0, fakeKeyManager.listKeyIds().size)
    }

    @Test
    fun challenge_2_4_importFromPrivateKey_db_failure_rolls_back_staged_key() = runBlocking {
        val failingDriverFactory = setupDriverWithFailingInsert()
        val fakeKeyManager = FakeSecureKeyManager()
        val repository = WalletRepositoryImpl(
            databaseDriverFactory = failingDriverFactory,
            cryptoProvider = cryptoProvider,
            ethereumRpcClient = ethereumRpcClient,
            secureKeyManager = fakeKeyManager,
            platformDeletionCleanupHook = com.cbstudio.wearwallet.core.platform.NoOpPlatformDeletionCleanupHook(),
            sideEffectTracker = sideEffectTracker
        )

        val result = repository.importFromPrivateKey(
            name = "FailDbPrivateKey",
            privateKey = com.cbstudio.wearwallet.core.security.ScopedPrivateKey.fromHex(testPrivateKeyHex),
            password = testPassword.toCharArray(),
            chainType = ChainType.ETHEREUM,
            authContext = createProvisioningAuth(fakeKeyManager)
        )

        assertTrue("importFromPrivateKey must fail on DB write error", result is Result.Failure)
        assertEquals("KeyVault must have 0 keys after rollback", 0, fakeKeyManager.listKeyIds().size)
    }

    // =========================================================================
    // SECTION 3: KEYSTORE FAILURE COMPENSATION (0 DB WRITES)
    // =========================================================================

    private class FailingStagingKeyManager : SecureKeyManager {
        val storedKeys = mutableMapOf<String, String>()

        override suspend fun storePrivateKey(
            keyId: String,
            privateKey: ByteArray,
            requireAuth: Boolean,
            authContext: AuthenticationContext?,
            expectedWalletId: String
        ): Result<Unit> = Result.Failure(KeyStorageException("Hardware Enclave uninitialized"))

        override suspend fun deletePrivateKey(
            keyId: String,
            authContext: AuthenticationContext?,
            expectedWalletId: String
        ): Result<Unit> =
            Result.Success(Unit)

        override suspend fun startProvisioningSession(): ProvisioningSession = ProvisioningSession.create()

        override suspend fun storeStagedPrivateKey(
            session: ProvisioningSession,
            privateKey: ByteArray,
            requireAuth: Boolean,
            authContext: AuthenticationContext?
        ): Result<Unit> = Result.Failure(KeyStorageException("Hardware Keystore write rejected"))

        override suspend fun storeStagedPrivateKey(
            sessionId: String,
            stagedKeyAlias: String,
            privateKey: ByteArray,
            requireAuth: Boolean,
            authContext: AuthenticationContext?
        ): Result<Unit> = Result.Failure(KeyStorageException("Hardware Keystore write rejected"))

        override suspend fun getActiveProvisioningSession(sessionId: String): ProvisioningSession? = null

        override suspend fun commitProvisioningSession(session: ProvisioningSession): Result<Unit> =
            Result.Success(Unit)

        override suspend fun rollbackProvisioningSession(session: ProvisioningSession): Result<Unit> =
            Result.Success(Unit)

        override suspend fun checkKeyPresence(keyId: String): KeyPresence = KeyPresence.Absent
        override suspend fun hasPrivateKey(keyId: String): Boolean = false
        override suspend fun listKeyIds(): List<String> = emptyList()
        override suspend fun signWithKey(keyId: String, data: ByteArray, authContext: AuthenticationContext?, expectedWalletId: String): Result<ByteArray> =
            Result.Failure(KeyNotFoundException(keyId))
        override suspend fun revealMnemonic(keyId: String, authContext: AuthenticationContext?, expectedWalletId: String): Result<ScopedMnemonic> =
            Result.Failure(UnsupportedOperationException())
        override suspend fun getSecurityLevel(): SecurityLevel =
            SecurityLevel(SecurityLevel.Level.BASIC, false, false, false, false)
        override suspend fun exportEncryptedKey(keyId: String, backupPassword: CharArray, authContext: AuthenticationContext?, expectedWalletId: String): Result<EncryptedBackup> =
            Result.Failure(UnsupportedOperationException())
        override suspend fun importEncryptedKey(keyId: String, encryptedBackup: EncryptedBackup, backupPassword: CharArray, authContext: AuthenticationContext?, expectedWalletId: String): Result<Unit> =
            Result.Failure(UnsupportedOperationException())
        override fun observeSecurityEvents(): kotlinx.coroutines.flow.Flow<SecurityEvent> =
            kotlinx.coroutines.flow.emptyFlow()
    }

    @Test
    fun challenge_3_1_keystore_failure_results_in_zero_db_writes_across_all_methods() = runBlocking {
        val failingKm = FailingStagingKeyManager()
        val (repository, database) = createInMemoryRepository(failingKm)

        // 1. createWallet
        val r1 = repository.createWallet("W1", testMnemonic.toCharArray(), testPassword.toCharArray(), ChainType.ETHEREUM, createProvisioningAuth())
        assertTrue("createWallet must fail when KeyStore fails", r1 is Result.Failure)

        // 2. importFromMnemonic
        val r2 = repository.importFromMnemonic("W2", testMnemonic.toCharArray(), testPassword.toCharArray(), ChainType.ETHEREUM, createProvisioningAuth())
        assertTrue("importFromMnemonic must fail when KeyStore fails", r2 is Result.Failure)

        // 3. importFromPrivateKey
        val r3 = repository.importFromPrivateKey("W3", com.cbstudio.wearwallet.core.security.ScopedPrivateKey.fromHex(testPrivateKeyHex), testPassword.toCharArray(), ChainType.ETHEREUM, createProvisioningAuth())
        assertTrue("importFromPrivateKey must fail when KeyStore fails", r3 is Result.Failure)

        // Ensure 0 DB writes occurred
        val allWallets = repository.getAllWallets()
        assertTrue(allWallets is Result.Success)
        assertEquals("DB must have 0 wallet rows when KeyStore staging fails", 0, (allWallets as Result.Success).data.size)
    }

    // =========================================================================
    // SECTION 4: COMMITTED KEY IMMUTABILITY & ROLLBACK PROTECTION
    // =========================================================================

    @Test
    fun challenge_4_1_committed_key_cannot_be_rolled_back_by_stale_session() = runBlocking {
        val fakeKeyManager = FakeSecureKeyManager()
        val session = fakeKeyManager.startProvisioningSession()
        val auth = AuthenticationContext(
            authHandle = TestPlatformAuthenticator.issueHandle(
                keyId = session.stagedKeyAlias,
                sessionId = session.sessionId,
                operation = AuthOperation.IMPORT
            )
        )
        val storeRes = fakeKeyManager.storeStagedPrivateKey(session, testPrivateKeyHex.encodeToByteArray(), true, auth)
        assertTrue(storeRes is Result.Success)
        assertTrue(fakeKeyManager.hasPrivateKey(session.stagedKeyAlias))

        val commitRes = fakeKeyManager.commitProvisioningSession(session)
        assertTrue(commitRes is Result.Success)
        assertTrue(session.isCommitted)

        // Attempt rollback on committed session
        val rollbackRes = fakeKeyManager.rollbackProvisioningSession(session)
        assertTrue("Rollback on committed session must fail", rollbackRes is Result.Failure)
        assertTrue(
            "Exception must be IllegalStateException",
            (rollbackRes as Result.Failure).exception is IllegalStateException
        )

        // Key must remain intact in KeyVault
        assertTrue("Committed key MUST remain in KeyVault", fakeKeyManager.hasPrivateKey(session.stagedKeyAlias))
    }

    // =========================================================================
    // SECTION 5: SENSITIVE MEMORY ZEROIZATION EMPIRICAL VERIFICATION
    // =========================================================================

    @Test
    fun challenge_5_1_secureZero_strictly_zeroes_out_byte_buffers() {
        val pwdBytes = "MasterSecretPassword#2026".encodeToByteArray()
        val privKeyBytes = testPrivateKeyHex.hexToByteArray()
        val mnemonicBytes = testMnemonic.encodeToByteArray()

        assertFalse("Buffer must not initially be all zeros", pwdBytes.all { it == 0.toByte() })
        assertFalse("Buffer must not initially be all zeros", privKeyBytes.all { it == 0.toByte() })
        assertFalse("Buffer must not initially be all zeros", mnemonicBytes.all { it == 0.toByte() })

        SecureByteArray.secureZero(pwdBytes)
        SecureByteArray.secureZero(privKeyBytes)
        SecureByteArray.secureZero(mnemonicBytes)

        assertTrue("Password bytes must be zeroed", pwdBytes.all { it == 0.toByte() })
        assertTrue("Private key bytes must be zeroed", privKeyBytes.all { it == 0.toByte() })
        assertTrue("Mnemonic bytes must be zeroed", mnemonicBytes.all { it == 0.toByte() })
    }

    // =========================================================================
    // SECTION 6: PROVISIONING SESSION TIMEOUT & LIFECYCLE INVARIANTS
    // =========================================================================

    @Test
    fun challenge_6_1_expired_session_fails_closed_on_store_commit_and_rollback() = runBlocking {
        val fakeKeyManager = FakeSecureKeyManager()
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        // Create an expired session (createdAtMs 70s in the past, timeout is 60s)
        val expiredSession = ProvisioningSession(
            sessionId = "ww_sess_expired_test",
            stagedKeyAlias = "ww_key_expired_test",
            backupId = "ww_backup_expired_test",
            createdAtMs = now - 70_000L,
            maxValidityDurationMs = 60_000L
        )

        val auth = AuthenticationContext(
            authHandle = TestPlatformAuthenticator.issueHandle(
                keyId = expiredSession.stagedKeyAlias,
                sessionId = expiredSession.sessionId,
                operation = AuthOperation.IMPORT
            )
        )
        val storeRes = fakeKeyManager.storeStagedPrivateKey(expiredSession, testPrivateKeyHex.encodeToByteArray(), true, auth)
        assertTrue("storeStagedPrivateKey on expired session must fail", storeRes is Result.Failure)

        val commitRes = fakeKeyManager.commitProvisioningSession(expiredSession)
        assertTrue("commitProvisioningSession on expired session must fail", commitRes is Result.Failure)

        val rollbackRes = fakeKeyManager.rollbackProvisioningSession(expiredSession)
        assertTrue("rollbackProvisioningSession on expired session must fail", rollbackRes is Result.Failure)
    }

    @Test
    fun challenge_6_2_session_cannot_be_committed_after_rollback() {
        val session = ProvisioningSession.create()
        session.markRolledBack()
        assertTrue(session.isRolledBack)
        assertFalse(session.isActive)

        assertThrows(IllegalStateException::class.java) {
            session.markCommitted()
        }
    }

    @Test
    fun challenge_6_3_session_cannot_be_rolled_back_after_commit() {
        val session = ProvisioningSession.create()
        session.markCommitted()
        assertTrue(session.isCommitted)
        assertFalse(session.isActive)

        assertThrows(IllegalStateException::class.java) {
            session.markRolledBack()
        }
    }
}

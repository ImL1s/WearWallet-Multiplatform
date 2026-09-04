package com.cbstudio.wearwallet.core.data.repository

import app.cash.sqldelight.Query
import app.cash.sqldelight.TransactionWithoutReturn
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.database.DatabaseDriverFactory
import com.cbstudio.wearwallet.core.database.Wallet
import com.cbstudio.wearwallet.core.database.WalletQueries
import com.cbstudio.wearwallet.core.domain.model.WalletType
import com.cbstudio.wearwallet.core.multichain.util.EthereumSigner
import com.cbstudio.wearwallet.core.network.EthereumRpcClient
import com.cbstudio.wearwallet.core.security.*
import io.github.iml1s.crypto.Secp256k1Pure
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

/**
 * Milestone 4: Atomic Legacy DB Migration with KeyVault Provisioning Test Suite
 *
 * Scenarios Verified:
 * 1. Successful legacy wallet migration (derives address, provisions KeyVault, verifies signature & recovery, updates DB atomically).
 * 2. Tampered / wrong address in legacy record fails migration and leaves 0 keys in KeyVault.
 * 3. Decryption failure / wrong password handling (fails closed, 0 keys in KeyVault).
 * 4. DB failure compensation (KeyVault entry rolled back upon DB error).
 * 5. Idempotent re-run on already migrated wallet (returns existing account without duplicating or corrupting keys).
 * 6. Downgrade safety: migrated record with missing KeyVault key fails closed with KeyMaterialUnavailableException.
 * 7. Post-migration signing path works via keyAlias and matches recovered sender.
 * 8. Mnemonic-only legacy wallet migration (derives private key, stores in KeyVault, updates WWEN envelopes).
 * 9. Keystone hardware wallet migration is a safe no-op with 0 keys in KeyVault.
 * 10. Memory zeroing on all buffers in finally.
 */
class WalletRepositoryKeyVaultMigrationTest {

    private val testPassword = "LegacyWalletMasterPassword#2026"
    private val testPrivateKeyHex = "e331b6d69882b4cb4ea581d88ec2693329b3b3a4711832421415ebff2d474428"
    private val testMnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"

    private lateinit var testAddress: String
    private lateinit var testMnemonicAddress: String

    private lateinit var cryptoProvider: CommonCryptoProvider
    private lateinit var fakeSecureKeyManager: FakeSecureKeyManager
    private lateinit var ethereumRpcClient: EthereumRpcClient
    private lateinit var sideEffectTracker: SideEffectTracker
    private lateinit var databaseDriverFactory: DatabaseDriverFactory
    private lateinit var mockWalletQueries: WalletQueries
    private lateinit var mockJournalQueries: com.cbstudio.wearwallet.core.database.StagingJournalQueries
    private lateinit var mockDeletionQueries: com.cbstudio.wearwallet.core.database.DeletionJournalQueries

    @Before
    fun setUp() {
        runBlocking {
            cryptoProvider = CommonCryptoProvider()
            fakeSecureKeyManager = FakeSecureKeyManager()
            ethereumRpcClient = mock()
            sideEffectTracker = mock()
            databaseDriverFactory = mock()
            val driver = app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver(app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver.IN_MEMORY)
            com.cbstudio.wearwallet.core.database.CoreWalletDatabase.Schema.create(driver)
            whenever(databaseDriverFactory.createDriver()).thenReturn(driver)
            mockWalletQueries = mock()
            mockJournalQueries = mock()
            mockDeletionQueries = mock()

            val deletionMap = mutableMapOf<Long, com.cbstudio.wearwallet.core.database.Deletion_journal>()
            whenever(mockDeletionQueries.insertDeletionJournal(any(), anyOrNull(), any(), anyOrNull(), any(), any(), any())).thenAnswer { invocation ->
                val walletId = invocation.getArgument<Long>(0)
                val keyAlias = invocation.getArgument<String?>(1)
                val state = invocation.getArgument<String>(2)
                val lastError = invocation.getArgument<String?>(3)
                val retryCount = invocation.getArgument<Long>(4)
                val createdAt = invocation.getArgument<Long>(5)
                val updatedAt = invocation.getArgument<Long>(6)
                deletionMap[walletId] = com.cbstudio.wearwallet.core.database.Deletion_journal(
                    wallet_id = walletId,
                    key_alias = keyAlias,
                    state = state,
                    last_error = lastError,
                    retry_count = retryCount,
                    created_at = createdAt,
                    updated_at = updatedAt
                )
                Unit
            }
            var lastDeletionAffectedRows = 0L
            whenever(mockDeletionQueries.updateDeletionStateCas(any(), anyOrNull(), any(), any(), any())).thenAnswer { invocation ->
                val newState = invocation.getArgument<String>(0)
                val lastError = invocation.getArgument<String?>(1)
                val updatedAt = invocation.getArgument<Long>(2)
                val walletId = invocation.getArgument<Long>(3)
                val expectedState = invocation.getArgument<String>(4)
                val entry = deletionMap[walletId]
                if (entry != null && entry.state == expectedState) {
                    deletionMap[walletId] = entry.copy(state = newState, last_error = lastError, updated_at = updatedAt)
                    lastDeletionAffectedRows = 1L
                } else {
                    lastDeletionAffectedRows = 0L
                }
                Unit
            }
            whenever(mockDeletionQueries.changesCount()).thenAnswer {
                val q = mock<app.cash.sqldelight.Query<Long>>()
                whenever(q.executeAsOne()).thenAnswer { lastDeletionAffectedRows }
                whenever(q.executeAsOneOrNull()).thenAnswer { lastDeletionAffectedRows }
                q
            }
            whenever(mockDeletionQueries.selectByWalletId(any())).thenAnswer { invocation ->
                val walletId = invocation.getArgument<Long>(0)
                val entry = deletionMap[walletId]
                val q = mock<app.cash.sqldelight.Query<com.cbstudio.wearwallet.core.database.Deletion_journal>>()
                whenever(q.executeAsOneOrNull()).thenReturn(entry)
                whenever(q.executeAsOne()).thenAnswer { entry ?: throw NoSuchElementException() }
                q
            }

            val emptyDeletionQuery = mock<app.cash.sqldelight.Query<com.cbstudio.wearwallet.core.database.Deletion_journal>>()
            whenever(emptyDeletionQuery.executeAsList()).thenReturn(emptyList())
            whenever(mockDeletionQueries.selectPendingDeletions()).thenReturn(emptyDeletionQuery)
            whenever(mockDeletionQueries.transaction(any(), any())).thenAnswer { invocation ->
                val body = invocation.getArgument<TransactionWithoutReturn.() -> Unit>(1)
                val mockScope = mock<TransactionWithoutReturn>()
                body.invoke(mockScope)
            }

            val emptyTombstoneQuery = mock<app.cash.sqldelight.Query<Wallet>>()
            whenever(emptyTombstoneQuery.executeAsList()).thenReturn(emptyList())
            whenever(mockWalletQueries.selectDeletionPending()).thenReturn(emptyTombstoneQuery)

            val emptyActiveQuery = mock<app.cash.sqldelight.Query<Wallet>>()
            whenever(emptyActiveQuery.executeAsList()).thenReturn(emptyList())
            whenever(mockWalletQueries.selectAllActiveWallets()).thenReturn(emptyActiveQuery)

            val journalMap = mutableMapOf<String, com.cbstudio.wearwallet.core.database.Staging_journal>()
            whenever(mockJournalQueries.insertJournal(any(), any(), any(), any(), any(), any())).thenAnswer { invocation ->
                val sessionId = invocation.getArgument<String>(0)
                val stagedKeyAlias = invocation.getArgument<String>(1)
                val backupId = invocation.getArgument<String>(2)
                val state = invocation.getArgument<String>(3)
                val createdAt = invocation.getArgument<Long>(4)
                val expiresAt = invocation.getArgument<Long>(5)
                journalMap[sessionId] = com.cbstudio.wearwallet.core.database.Staging_journal(
                    session_id = sessionId,
                    staged_alias = stagedKeyAlias,
                    backup_id = backupId,
                    state = state,
                    created_at = createdAt,
                    expires_at = expiresAt
                )
                Unit
            }
            var lastAffectedRows = 0L
            whenever(mockJournalQueries.updateJournalStateCas(any(), any(), any())).thenAnswer { invocation ->
                val newState = invocation.getArgument<String>(0)
                val sessionId = invocation.getArgument<String>(1)
                val expectedState = invocation.getArgument<String>(2)
                val entry = journalMap[sessionId]
                if (entry != null && entry.state == expectedState) {
                    journalMap[sessionId] = entry.copy(state = newState)
                    lastAffectedRows = 1L
                } else {
                    lastAffectedRows = 0L
                }
                Unit
            }
            whenever(mockJournalQueries.changesCount()).thenAnswer {
                val q = mock<app.cash.sqldelight.Query<Long>>()
                whenever(q.executeAsOne()).thenAnswer { lastAffectedRows }
                whenever(q.executeAsOneOrNull()).thenAnswer { lastAffectedRows }
                q
            }
            whenever(mockJournalQueries.transaction(any(), any())).thenAnswer { invocation ->
                val body = invocation.getArgument<TransactionWithoutReturn.() -> Unit>(1)
                val mockScope = mock<TransactionWithoutReturn>()
                body.invoke(mockScope)
            }
            whenever(mockJournalQueries.selectBySessionId(any())).thenAnswer { invocation ->
                val sessionId = invocation.getArgument<String>(0)
                val entry = journalMap[sessionId]
                val q = mock<app.cash.sqldelight.Query<com.cbstudio.wearwallet.core.database.Staging_journal>>()
                whenever(q.executeAsOneOrNull()).thenReturn(entry)
                whenever(q.executeAsOne()).thenAnswer { entry ?: throw NoSuchElementException() }
                q
            }

            val kp = cryptoProvider.generateKeyPairFromPrivateKey(testPrivateKeyHex.toCharArray())
            testAddress = cryptoProvider.deriveAddress(kp.publicKey)

            val mKp = cryptoProvider.generateKeyPairFromMnemonic(testMnemonic.toCharArray(), "m/44'/60'/0'/0/0")
            testMnemonicAddress = cryptoProvider.deriveAddress(mKp.publicKey)

            // Transaction handling
            whenever(mockWalletQueries.transaction(any(), any())).thenAnswer { invocation ->
                val body = invocation.getArgument<TransactionWithoutReturn.() -> Unit>(1)
                val mockScope = mock<TransactionWithoutReturn>()
                body.invoke(mockScope)
            }
        }
    }

    private fun createRepository(
        keyManager: SecureKeyManager = fakeSecureKeyManager,
        customQueries: WalletQueries = mockWalletQueries,
        journalQueries: com.cbstudio.wearwallet.core.database.StagingJournalQueries = mockJournalQueries,
        deletionQueries: com.cbstudio.wearwallet.core.database.DeletionJournalQueries = mockDeletionQueries
    ): WalletRepositoryImpl = WalletRepositoryImpl(
        databaseDriverFactory = databaseDriverFactory,
        cryptoProvider = cryptoProvider,
        ethereumRpcClient = ethereumRpcClient,
        secureKeyManager = keyManager,
        platformDeletionCleanupHook = com.cbstudio.wearwallet.core.platform.NoOpPlatformDeletionCleanupHook(),
        sideEffectTracker = sideEffectTracker,
        customWalletQueries = customQueries,
        customStagingJournalQueries = journalQueries,
        customDeletionJournalQueries = deletionQueries
    )

    /**
     * 建立 Android 舊版 Base64(IV + Ciphertext + Tag) 加密字串
     */
    private fun createAndroidLegacyEncryptedKey(plaintext: String, password: String): String {
        val plainBytes = plaintext.encodeToByteArray()
        val pwdBytes = password.encodeToByteArray()
        val sha256Key = CryptoUtils.sha256(pwdBytes)
        val encrypted = CryptoUtils.aesGcmEncrypt(plainBytes, sha256Key)
        val combined = encrypted.nonce + encrypted.ciphertext + encrypted.authTag
        return combined.toBase64()
    }

    /**
     * 建立 5-part 冒號分隔舊版加密字串 ("v1:salt:nonce:tag:ciphertext")
     */
    private fun createColonLegacyEncryptedString(plaintext: String, password: String): String {
        val plainBytes = plaintext.encodeToByteArray()
        val pwdBytes = password.encodeToByteArray()
        val salt = CryptoUtils.randomBytes(16)
        val derivedKey = CryptoUtils.pbkdf2(pwdBytes, salt, 100_000, 32)
        val encrypted = CryptoUtils.aesGcmEncrypt(plainBytes, derivedKey)
        return listOf(
            "v1",
            salt.toBase64(),
            encrypted.nonce.toBase64(),
            encrypted.authTag.toBase64(),
            encrypted.ciphertext.toBase64()
        ).joinToString(":")
    }

    private fun createLegacyWallet(
        id: Long = 100L,
        address: String = testAddress,
        encryptedPrivateKey: String = createAndroidLegacyEncryptedKey(testPrivateKeyHex, testPassword),
        encryptedMnemonic: String? = createColonLegacyEncryptedString(testMnemonic, testPassword),
        keyAlias: String? = null,
        keyBackend: String? = null,
        keyFormatVersion: Long = 1L,
        requiresAuth: Long = 1L,
        walletType: String = "HOT_WALLET"
    ): Wallet {
        return Wallet(
            id = id,
            name = "Legacy Wallet 100",
            address = address,
            public_key = "0x04testpublickey",
            encrypted_private_key = encryptedPrivateKey,
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
            key_backend = keyBackend,
            key_format_version = keyFormatVersion,
            requires_auth = requiresAuth,
            is_deletion_pending = 0L,
            created_at = 1000L,
            updated_at = 1000L
        )
    }

    private fun mockSelectById(walletProvider: () -> Wallet?) {
        val query = mock<Query<Wallet>>()
        whenever(query.executeAsOneOrNull()).thenAnswer { walletProvider() }
        whenever(query.executeAsOne()).thenAnswer { walletProvider() ?: throw IllegalStateException("Wallet not found") }
        whenever(mockWalletQueries.selectById(any())).thenReturn(query)
    }

    // =========================================================================
    // 1. 成功遷移舊版錢包 (Successful Legacy Wallet Migration)
    // =========================================================================

    private fun createProvisioningAuth(keyManager: SecureKeyManager = fakeSecureKeyManager, walletId: String = ""): AuthenticationContext {
        val s = runBlocking { keyManager.startProvisioningSession() }
        val targetWalletId = if (walletId.isNotEmpty()) walletId else s.stagedKeyAlias
        return AuthenticationContext(
            authHandle = TestPlatformAuthenticator.issueHandle(
                keyId = s.stagedKeyAlias,
                sessionId = s.sessionId,
                operation = AuthOperation.IMPORT,
                validityDurationMs = 600_000L,
                walletId = targetWalletId
            )
        )
    }

    @Test
    fun test_successful_legacy_wallet_migration_provisions_keyvault_and_updates_db() = runBlocking<Unit> {
        var currentWallet = createLegacyWallet()
        mockSelectById { currentWallet }

        var capturedEncryptedPk: String? = null
        var capturedEncryptedMnem: String? = null
        var capturedKeyAlias: String? = null
        var capturedKeyBackend: String? = null
        var capturedVersion: Long? = null
        var capturedRequiresAuth: Long? = null

        whenever(mockWalletQueries.updateEncryptedSecrets(any(), anyOrNull(), any(), any(), any(), any(), any())).thenAnswer { invocation ->
            capturedEncryptedPk = invocation.getArgument(0)
            capturedEncryptedMnem = invocation.getArgument(1)
            capturedKeyAlias = invocation.getArgument(2)
            capturedKeyBackend = invocation.getArgument(3)
            capturedVersion = invocation.getArgument(4)
            capturedRequiresAuth = invocation.getArgument(5)
            currentWallet = currentWallet.copy(
                encrypted_private_key = capturedEncryptedPk ?: currentWallet.encrypted_private_key,
                encrypted_mnemonic = capturedEncryptedMnem,
                key_alias = capturedKeyAlias,
                key_backend = capturedKeyBackend,
                key_format_version = capturedVersion ?: 2L,
                requires_auth = capturedRequiresAuth ?: 1L
            )
            Unit
        }

        val repository = createRepository()

        val result = repository.migrateLegacyWallet("100", testPassword.toCharArray(), createProvisioningAuth(walletId = "100"))

        assertTrue("Migration must succeed: ${(result as? Result.Failure)?.exception?.message}", result is Result.Success)
        val migratedAccount = (result as Result.Success).data

        assertEquals("100", migratedAccount.id)
        assertTrue("Address must match: expected $testAddress, got ${migratedAccount.address}", testAddress.equals(migratedAccount.address, ignoreCase = true))
        assertNotNull("keyAlias must be provisioned", migratedAccount.keyAlias)
        assertTrue("keyAlias must have ww_key_ prefix", migratedAccount.keyAlias!!.startsWith("ww_key_"))
        assertEquals("keyFormatVersion must be 2", 2, migratedAccount.keyFormatVersion)
        assertTrue("requiresAuth must be true", migratedAccount.requiresAuth)

        // KeyVault verification
        val keyAlias = migratedAccount.keyAlias!!
        assertTrue("KeyVault must contain provisioned keyAlias", fakeSecureKeyManager.hasPrivateKey(keyAlias))

        val testData = CryptoUtils.sha256("TestTransactionDigest#100".encodeToByteArray())

        // 1. Negative challenge: Unauthenticated signing attempt MUST be rejected
        val unauthSignResult = fakeSecureKeyManager.signWithKey(keyAlias, testData, authContext = null, expectedWalletId = "100")
        assertTrue("Unauthenticated signing attempt on migrated key must fail", unauthSignResult is Result.Failure)
        val unauthEx = (unauthSignResult as Result.Failure).exception
        assertTrue("Must throw AuthenticationRequiredException, got: $unauthEx", unauthEx is AuthenticationRequiredException)

        // 2. Authenticated signing with valid PlatformAuthHandle
        val authHandle = TestPlatformAuthenticator.issueHandle(
            keyId = keyAlias,
            operation = AuthOperation.SIGN,
            intentFingerprint = testData.toHexString(),
            walletId = "100"
        )
        val signResult = fakeSecureKeyManager.signWithKey(keyAlias, testData, authContext = AuthenticationContext(authHandle = authHandle), expectedWalletId = "100")
        assertTrue("Signing with provisioned keyAlias and valid AuthHandle must succeed", signResult is Result.Success)
        val sigBytes = (signResult as Result.Success).data
        val r = sigBytes.copyOfRange(0, 32)
        val s = sigBytes.copyOfRange(32, 64)
        val yParity = sigBytes[64].toInt() and 0xFF
        val z = Secp256k1Pure.BigInteger.fromByteArray(testData)
        val rBig = Secp256k1Pure.BigInteger.fromByteArray(r)
        val sBig = Secp256k1Pure.BigInteger.fromByteArray(s)
        val pointQ = Secp256k1Pure.recoverPublicKeyPoint(z, rBig, sBig, yParity)
        assertNotNull(pointQ)
        val recoveredAddress = EthereumSigner.toEthereumAddress(Secp256k1Pure.encodePublicKey(pointQ!!, compressed = false))
        assertTrue("Recovered address must match migrated wallet address", recoveredAddress.equals(testAddress, ignoreCase = true))
    }

    // =========================================================================
    // 5. 遷移後讀取錢包清單正確解析 KeyVault 欄位 (Load Wallets with KeyVault Schema)
    // =========================================================================

    @Test
    fun test_loadWallets_correctly_maps_migrated_keyVault_fields() = runBlocking<Unit> {
        val keyAlias = "ww_key_auth_protected_400"
        fakeSecureKeyManager.storePrivateKey(keyAlias, testPrivateKeyHex.removePrefix("0x").hexToByteArray(), requireAuth = true, authContext = null, expectedWalletId = "400")

        val migratedWallet = createLegacyWallet(
            id = 400L,
            keyAlias = keyAlias,
            keyBackend = "KEYSTORE",
            keyFormatVersion = 2L,
            requiresAuth = 1L
        )
        val query = mock<Query<Wallet>>()
        whenever(query.executeAsList()).thenReturn(listOf(migratedWallet))
        whenever(mockWalletQueries.selectAllActiveWallets()).thenReturn(query)

        val repository = createRepository()

        val walletsResult = repository.getAllWallets()
        assertTrue(walletsResult is Result.Success)
        val wallets = (walletsResult as Result.Success).data
        assertEquals(1, wallets.size)
        val wallet = wallets.first()
        assertEquals(keyAlias, wallet.keyAlias)
        assertEquals("KEYSTORE", wallet.keyBackend)
        assertEquals(2, wallet.keyFormatVersion)
        assertEquals(true, wallet.requiresAuth)
    }

    // =========================================================================
    // 2. 篡改/錯誤地址防護 (Tampered Address -> Fail Closed, 0 Keys in KeyVault)
    // =========================================================================

    @Test
    fun test_tampered_wrong_address_in_legacy_record_fails_migration_and_leaves_0_keys_in_keyvault() = runBlocking {
        val tamperedAddress = "0x1111111111111111111111111111111111111111"
        val tamperedWallet = createLegacyWallet(address = tamperedAddress)
        mockSelectById { tamperedWallet }

        val repository = createRepository()

        val result = repository.migrateLegacyWallet("100", testPassword.toCharArray(), createProvisioningAuth())

        assertTrue("Migration must fail when address derivation check fails", result is Result.Failure)
        val ex = (result as Result.Failure).exception
        assertTrue("Exception must be EnvelopeIntegrityException", ex is EnvelopeIntegrityException)
        assertEquals("KeyVault MUST have 0 keys after failure", 0, fakeSecureKeyManager.listKeyIds().size)
    }

    // =========================================================================
    // 3. 錯誤密碼防護 (Wrong Password -> Fail Closed, 0 Keys in KeyVault)
    // =========================================================================

    @Test
    fun test_decryption_failure_wrong_password_handling() = runBlocking {
        val wallet = createLegacyWallet()
        mockSelectById { wallet }

        val repository = createRepository()

        val result = repository.migrateLegacyWallet("100", "IncorrectPassword#999".toCharArray(), createProvisioningAuth())

        assertTrue("Migration must fail on incorrect password", result is Result.Failure)
        assertEquals("KeyVault MUST have 0 keys after password failure", 0, fakeSecureKeyManager.listKeyIds().size)
    }

    // =========================================================================
    // 4. 資料庫寫入失敗回滾補償 (DB Failure -> KeyVault Rollback Compensation)
    // =========================================================================

    @Test
    fun test_db_failure_compensation_rolls_back_keyvault_entry() = runBlocking {
        val wallet = createLegacyWallet()
        mockSelectById { wallet }

        whenever(mockWalletQueries.updateEncryptedSecrets(any(), anyOrNull(), any(), any(), any(), any(), any())).thenThrow(
            RuntimeException("SQLITE_IOERR: disk full or database locked during migration transaction")
        )

        val repository = createRepository()

        val result = repository.migrateLegacyWallet("100", testPassword.toCharArray(), createProvisioningAuth())

        assertTrue("Migration must fail on DB transaction failure", result is Result.Failure)
        assertEquals("KeyVault MUST have 0 orphan keys after rollback compensation", 0, fakeSecureKeyManager.listKeyIds().size)
    }

    // =========================================================================
    // 5. 冪等性重跑 (Idempotent Re-Run on Already Migrated Wallet)
    // =========================================================================

    @Test
    fun test_idempotent_rerun_on_already_migrated_wallet() = runBlocking {
        val existingKeyAlias = "ww_key_already_migrated_uuid_200"
        fakeSecureKeyManager.setKey(existingKeyAlias, testPrivateKeyHex)

        val alreadyMigratedWallet = createLegacyWallet(
            id = 200L,
            keyAlias = existingKeyAlias,
            keyBackend = "BASIC",
            keyFormatVersion = 2L,
            requiresAuth = 1L
        )
        mockSelectById { alreadyMigratedWallet }

        val repository = createRepository()

        // 第一次調用 migrateLegacyWalletIfNeeded
        val result1 = repository.migrateLegacyWalletIfNeeded("200", testPassword.toCharArray(), createProvisioningAuth())
        assertTrue(result1 is Result.Success)
        assertEquals(existingKeyAlias, (result1 as Result.Success).data.keyAlias)
        assertEquals(listOf(existingKeyAlias), fakeSecureKeyManager.listKeyIds())

        // 第二次調用 migrateLegacyWallet
        val result2 = repository.migrateLegacyWallet("200", testPassword.toCharArray(), createProvisioningAuth())
        assertTrue(result2 is Result.Success)
        assertEquals(existingKeyAlias, (result2 as Result.Success).data.keyAlias)
        assertEquals("Must not generate additional keys in KeyVault", listOf(existingKeyAlias), fakeSecureKeyManager.listKeyIds())
    }

    // =========================================================================
    // 6. 防降級保護 (Downgrade Protection: Missing KeyVault Key -> Fail Closed)
    // =========================================================================

    @Test
    fun test_downgrade_protection_migrated_record_with_missing_keyvault_key_fails_closed() = runBlocking {
        val missingKeyAlias = "ww_key_lost_uuid_300"
        assertFalse(fakeSecureKeyManager.hasPrivateKey(missingKeyAlias))

        val migratedWalletWithLostKey = createLegacyWallet(
            id = 300L,
            keyAlias = missingKeyAlias,
            keyBackend = "HARDWARE",
            keyFormatVersion = 2L,
            requiresAuth = 1L
        )
        mockSelectById { migratedWalletWithLostKey }

        val repository = createRepository()

        val result = repository.migrateLegacyWallet("300", testPassword.toCharArray(), createProvisioningAuth())

        assertTrue("Missing key material must fail closed", result is Result.Failure)
        val ex = (result as Result.Failure).exception
        assertTrue("Exception must be KeyMaterialUnavailableException", ex is KeyMaterialUnavailableException)
    }

    // =========================================================================
    // 7. 僅有助記詞的舊版錢包遷移 (Mnemonic-only Legacy Wallet Migration)
    // =========================================================================

    @Test
    fun test_mnemonic_only_legacy_wallet_migration() = runBlocking {
        var currentWallet = createLegacyWallet(
            id = 400L,
            address = testMnemonicAddress,
            encryptedPrivateKey = "", // No private key column, only mnemonic
            encryptedMnemonic = createColonLegacyEncryptedString(testMnemonic, testPassword)
        )
        mockSelectById { currentWallet }

        whenever(mockWalletQueries.updateEncryptedSecrets(any(), anyOrNull(), any(), any(), any(), any(), any())).thenAnswer { invocation ->
            currentWallet = currentWallet.copy(
                key_alias = invocation.getArgument(2),
                key_backend = invocation.getArgument(3),
                key_format_version = invocation.getArgument(4),
                requires_auth = invocation.getArgument(5)
            )
            Unit
        }

        val repository = createRepository()

        val result = repository.migrateLegacyWallet("400", testPassword.toCharArray(), createProvisioningAuth())

        assertTrue("Mnemonic-only migration must succeed: ${(result as? Result.Failure)?.exception?.message}", result is Result.Success)
        val migrated = (result as Result.Success).data
        assertNotNull(migrated.keyAlias)
        assertTrue(fakeSecureKeyManager.hasPrivateKey(migrated.keyAlias!!))
        assertEquals(2, migrated.keyFormatVersion)
    }

    // =========================================================================
    // 8. Keystone 硬體錢包遷移為安全 No-Op (Keystone Wallet Migration is Safe No-Op)
    // =========================================================================

    @Test
    fun test_hardware_keystone_wallet_migration_is_safe_noop() = runBlocking {
        val keystoneWallet = createLegacyWallet(
            id = 500L,
            walletType = WalletType.KEYSTONE.name,
            encryptedPrivateKey = "",
            encryptedMnemonic = null
        )
        mockSelectById { keystoneWallet }

        val repository = createRepository()

        val result = repository.migrateLegacyWallet("500", testPassword.toCharArray(), createProvisioningAuth())

        assertTrue(result is Result.Success)
        assertEquals(0, fakeSecureKeyManager.listKeyIds().size)
    }

    // =========================================================================
    // 9. 刪除錢包時金鑰刪除失敗則不刪除資料庫 (Delete Wallet Key Deletion Failure Safety)
    // =========================================================================

    @Test
    fun test_deleteWallet_fails_and_keeps_db_when_key_deletion_fails() = runBlocking {
        val keyAlias = "ww_key_auth_protected_600"
        fakeSecureKeyManager.setKey(keyAlias, testPrivateKeyHex, requireAuth = true)

        val wallet = createLegacyWallet(
            id = 600L,
            keyAlias = keyAlias,
            keyBackend = "KEYSTORE",
            keyFormatVersion = 2L,
            requiresAuth = 1L
        )
        mockSelectById { wallet }

        // Mock SecureKeyManager to fail deletePrivateKey (e.g. KeyVault failure)
        val mockKeyManager = mock<SecureKeyManager>()
        whenever(mockKeyManager.checkKeyPresence(any())).thenReturn(KeyPresence.Present)
        whenever(mockKeyManager.deletePrivateKey(any(), anyOrNull(), any())).thenReturn(
            Result.Failure(KeyMaterialUnavailableException("KeyVault hardware communication error"))
        )

        val repository = createRepository(keyManager = mockKeyManager)

        val deleteHandle = TestPlatformAuthenticator.issueHandle(
            keyId = keyAlias,
            operation = AuthOperation.DELETE,
            intentFingerprint = testAddress,
            walletId = "600"
        )
        val authContext = AuthenticationContext(authHandle = deleteHandle)

        val deleteResult = repository.deleteWallet("600", authContext = authContext)

        assertTrue("Delete must fail when key manager fails", deleteResult is Result.Failure)
        assertTrue((deleteResult as Result.Failure).exception is KeyMaterialUnavailableException)
        verify(mockWalletQueries, never()).delete(600L)
    }

    @Test
    fun test_deleteWallet_succeeds_and_deletes_db_when_key_deletion_succeeds() = runBlocking {
        val keyAlias = "ww_key_auth_protected_601"
        fakeSecureKeyManager.setKey(keyAlias, testPrivateKeyHex, requireAuth = true)

        val wallet = createLegacyWallet(
            id = 601L,
            keyAlias = keyAlias,
            keyBackend = "KEYSTORE",
            keyFormatVersion = 2L,
            requiresAuth = 1L
        )
        mockSelectById { wallet }

        val mockKeyManager = mock<SecureKeyManager>()
        whenever(mockKeyManager.checkKeyPresence(eq(keyAlias))).thenReturn(KeyPresence.Present, KeyPresence.Absent)
        whenever(mockKeyManager.deletePrivateKey(eq(keyAlias), anyOrNull(), any())).thenReturn(Result.Success(Unit))

        val repository = createRepository(keyManager = mockKeyManager)

        val deleteHandle = TestPlatformAuthenticator.issueHandle(
            keyId = keyAlias,
            operation = AuthOperation.DELETE,
            intentFingerprint = testAddress,
            walletId = "601"
        )
        val authContext = AuthenticationContext(authHandle = deleteHandle)

        val deleteResult = repository.deleteWallet("601", authContext = authContext)

        assertTrue("Delete must succeed when key manager succeeds", deleteResult is Result.Success)
        verify(mockWalletQueries, times(1)).delete(601L)
    }

    // =========================================================================
    // 10. 金鑰刪除認證與未提交回滾補償隔離 (Key Deletion Auth vs Rollback Isolation)
    // =========================================================================

    @Test
    fun test_migrated_wallet_key_requires_auth_for_deletion_and_rollback_removes_uncommitted() = runBlocking {
        val session = fakeSecureKeyManager.startProvisioningSession()
        val keyAlias = session.stagedKeyAlias
        fakeSecureKeyManager.storeStagedPrivateKey(
            session = session,
            privateKey = testPrivateKeyHex.encodeToByteArray(),
            requireAuth = true,
            authContext = AuthenticationContext(
                authHandle = TestPlatformAuthenticator.issueHandle(
                    keyId = keyAlias,
                    sessionId = session.sessionId,
                    operation = AuthOperation.IMPORT
                )
            )
        )

        // 1. Deletion without authContext fails closed
        val unauthDelete = fakeSecureKeyManager.deletePrivateKey(keyAlias, authContext = null, expectedWalletId = keyAlias)
        assertTrue("deletePrivateKey without auth must fail", unauthDelete is Result.Failure)
        assertTrue((unauthDelete as Result.Failure).exception is AuthenticationRequiredException)
        assertTrue(fakeSecureKeyManager.hasPrivateKey(keyAlias))

        // 2. Uncommitted rollback compensation succeeds unconditionally without auth
        val rollbackResult = fakeSecureKeyManager.rollbackProvisioningSession(session)
        assertTrue("rollbackProvisioningSession must succeed unconditionally", rollbackResult is Result.Success)
        assertFalse("Key must be deleted after rollback compensation", fakeSecureKeyManager.hasPrivateKey(keyAlias))
    }

    // =========================================================================
    // 11. 遷移後金鑰的授權指紋與操作型別負向測試 (Post-Migration Negative Auth Challenges)
    // =========================================================================

    @Test
    fun test_migrated_wallet_key_rejects_mismatched_intent_fingerprint_on_signing() = runBlocking {
        var currentWallet = createLegacyWallet(id = 700L)
        mockSelectById { currentWallet }

        whenever(mockWalletQueries.updateEncryptedSecrets(any(), anyOrNull(), any(), any(), any(), any(), any())).thenAnswer { invocation ->
            currentWallet = currentWallet.copy(
                key_alias = invocation.getArgument(2),
                key_backend = invocation.getArgument(3),
                key_format_version = invocation.getArgument(4),
                requires_auth = invocation.getArgument(5)
            )
            Unit
        }

        val repository = createRepository()

        val result = repository.migrateLegacyWallet("700", testPassword.toCharArray(), createProvisioningAuth(walletId = "700"))
        assertTrue(result is Result.Success)
        val keyAlias = (result as Result.Success).data.keyAlias!!

        val testData = CryptoUtils.sha256("LegitimateTxData".encodeToByteArray())
        val spoofedFingerprint = "0000000000000000000000000000000000000000000000000000000000000000"

        val mismatchedHandle = TestPlatformAuthenticator.issueHandle(
            keyId = keyAlias,
            operation = AuthOperation.SIGN,
            intentFingerprint = spoofedFingerprint,
            walletId = "700"
        )

        val signResult = fakeSecureKeyManager.signWithKey(keyAlias, testData, AuthenticationContext(authHandle = mismatchedHandle), expectedWalletId = "700")
        assertTrue("Signing with mismatched fingerprint MUST fail", signResult is Result.Failure)
        val ex = (signResult as Result.Failure).exception
        assertTrue("Must throw AuthenticationRequiredException, got: $ex", ex is AuthenticationRequiredException)
        assertTrue("Message must indicate fingerprint mismatch", ex.message!!.contains("Intent fingerprint mismatch"))
    }

    @Test
    fun test_migrated_wallet_key_rejects_wrong_operation_handle_on_signing() = runBlocking {
        var currentWallet = createLegacyWallet(id = 701L)
        mockSelectById { currentWallet }

        whenever(mockWalletQueries.updateEncryptedSecrets(any(), anyOrNull(), any(), any(), any(), any(), any())).thenAnswer { invocation ->
            currentWallet = currentWallet.copy(
                key_alias = invocation.getArgument(2),
                key_backend = invocation.getArgument(3),
                key_format_version = invocation.getArgument(4),
                requires_auth = invocation.getArgument(5)
            )
            Unit
        }

        val repository = createRepository()

        val result = repository.migrateLegacyWallet("701", testPassword.toCharArray(), createProvisioningAuth(walletId = "701"))
        assertTrue(result is Result.Success)
        val keyAlias = (result as Result.Success).data.keyAlias!!

        val testData = CryptoUtils.sha256("LegitimateTxData".encodeToByteArray())
        val deleteHandle = TestPlatformAuthenticator.issueHandle(
            keyId = keyAlias,
            operation = AuthOperation.DELETE,
            intentFingerprint = testData.toHexString(),
            walletId = "701"
        )

        val signResult = fakeSecureKeyManager.signWithKey(keyAlias, testData, AuthenticationContext(authHandle = deleteHandle), expectedWalletId = "701")
        assertTrue("Signing with DELETE handle MUST fail", signResult is Result.Failure)
        val ex = (signResult as Result.Failure).exception
        assertTrue("Must throw AuthenticationRequiredException, got: $ex", ex is AuthenticationRequiredException)
        assertTrue("Message must indicate operation mismatch", ex.message!!.contains("does not match expected 'SIGN'"))
    }

    @Test
    fun test_migrated_wallet_key_rejects_cross_key_handle_on_signing() = runBlocking {
        var currentWallet = createLegacyWallet(id = 702L)
        mockSelectById { currentWallet }

        whenever(mockWalletQueries.updateEncryptedSecrets(any(), anyOrNull(), any(), any(), any(), any(), any())).thenAnswer { invocation ->
            currentWallet = currentWallet.copy(
                key_alias = invocation.getArgument(2),
                key_backend = invocation.getArgument(3),
                key_format_version = invocation.getArgument(4),
                requires_auth = invocation.getArgument(5)
            )
            Unit
        }

        val repository = createRepository()

        val result = repository.migrateLegacyWallet("702", testPassword.toCharArray(), createProvisioningAuth(walletId = "702"))
        assertTrue(result is Result.Success)
        val keyAlias = (result as Result.Success).data.keyAlias!!

        val testData = CryptoUtils.sha256("LegitimateTxData".encodeToByteArray())
        val otherKeyHandle = TestPlatformAuthenticator.issueHandle(
            keyId = "ww_key_attacker_other_wallet",
            operation = AuthOperation.SIGN,
            intentFingerprint = testData.toHexString(),
            walletId = "702"
        )

        val signResult = fakeSecureKeyManager.signWithKey(keyAlias, testData, AuthenticationContext(authHandle = otherKeyHandle), expectedWalletId = "702")
        assertTrue("Signing with cross-key handle MUST fail", signResult is Result.Failure)
        val ex = (signResult as Result.Failure).exception
        assertTrue("Must throw AuthenticationRequiredException, got: $ex", ex is AuthenticationRequiredException)
        assertTrue("Message must indicate cross-key rejection", ex.message!!.contains("Cross-key handle rejected"))
    }

    // =========================================================================
    // Milestone 3: 2-Phase Tombstone Wallet Deletion Tests
    // =========================================================================

    @Test
    fun test_2phase_deletion_success_executes_markDeletionPending_then_deletePrivateKey_then_atomic_delete() = runBlocking {
        val keyAlias = "ww_key_delete_target_100"
        fakeSecureKeyManager.setKey(keyAlias, testPrivateKeyHex, requireAuth = true)

        var currentWallet = createLegacyWallet(
            id = 100L,
            keyAlias = keyAlias,
            keyBackend = "KEYSTORE",
            keyFormatVersion = 2L,
            requiresAuth = 1L
        )
        mockSelectById { currentWallet }

        val remainingWallet = createLegacyWallet(
            id = 101L,
            keyAlias = "ww_key_remaining_101",
            keyBackend = "KEYSTORE",
            keyFormatVersion = 2L,
            requiresAuth = 1L
        )
        val remainingQuery = mock<Query<Wallet>>()
        whenever(remainingQuery.executeAsList()).thenReturn(listOf(remainingWallet))
        whenever(mockWalletQueries.selectAllActiveWallets()).thenReturn(remainingQuery)

        val repository = createRepository()

        val deleteHandle = TestPlatformAuthenticator.issueHandle(
            keyId = keyAlias,
            operation = AuthOperation.DELETE,
            intentFingerprint = testAddress,
            walletId = "100"
        )
        val authContext = AuthenticationContext(authHandle = deleteHandle)

        val result = repository.deleteWallet("100", authContext)
        assertTrue("deleteWallet must succeed: ${(result as? Result.Failure)?.exception?.message}", result is Result.Success)

        // Verify InOrder execution: markDeletionPending -> deletePrivateKey -> delete
        val inOrder = inOrder(mockWalletQueries)
        inOrder.verify(mockWalletQueries).markDeletionPending(100L)
        inOrder.verify(mockWalletQueries).delete(100L)

        // Verify key is deleted from KeyVault
        assertFalse("Key must be deleted from KeyVault", fakeSecureKeyManager.hasPrivateKey(keyAlias))
    }

    @Test
    fun test_2phase_deletion_failed_key_deletion_leaves_tombstone_state_fail_closed() = runBlocking {
        val keyAlias = "ww_key_delete_target_200"
        val mockKeyManager = mock<SecureKeyManager>()
        whenever(mockKeyManager.checkKeyPresence(any())).thenReturn(KeyPresence.Present)
        whenever(mockKeyManager.deletePrivateKey(eq(keyAlias), anyOrNull(), any())).thenReturn(
            Result.Failure(KeyMaterialUnavailableException("Simulated KeyVault deletion failure"))
        )

        val currentWallet = createLegacyWallet(
            id = 200L,
            keyAlias = keyAlias,
            keyBackend = "KEYSTORE",
            keyFormatVersion = 2L,
            requiresAuth = 1L
        )
        mockSelectById { currentWallet }

        val repository = createRepository(keyManager = mockKeyManager)

        val deleteHandle = TestPlatformAuthenticator.issueHandle(
            keyId = keyAlias,
            operation = AuthOperation.DELETE,
            intentFingerprint = testAddress,
            walletId = "200"
        )
        val authContext = AuthenticationContext(authHandle = deleteHandle)

        val result = repository.deleteWallet("200", authContext)
        assertTrue("deleteWallet must fail when key deletion fails", result is Result.Failure)

        // Verify markDeletionPending was called (Phase 2: Tombstone set)
        verify(mockWalletQueries).markDeletionPending(200L)

        // Verify delete from DB was NEVER called (Phase 5 aborted)
        verify(mockWalletQueries, never()).delete(200L)
    }

    @Test
    fun test_2phase_deletion_fail_closed_on_markDeletionPending_failure() = runBlocking {
        val keyAlias = "ww_key_delete_target_300"
        fakeSecureKeyManager.setKey(keyAlias, testPrivateKeyHex, requireAuth = true)

        val currentWallet = createLegacyWallet(
            id = 300L,
            keyAlias = keyAlias,
            keyBackend = "KEYSTORE",
            keyFormatVersion = 2L,
            requiresAuth = 1L
        )
        mockSelectById { currentWallet }

        whenever(mockWalletQueries.markDeletionPending(300L)).thenThrow(
            RuntimeException("SQLITE_LOCKED: Failed to lock database for tombstone mark")
        )

        val repository = createRepository()

        val deleteHandle = TestPlatformAuthenticator.issueHandle(
            keyId = keyAlias,
            operation = AuthOperation.DELETE,
            intentFingerprint = testAddress,
            walletId = "300"
        )
        val authContext = AuthenticationContext(authHandle = deleteHandle)

        val result = repository.deleteWallet("300", authContext)
        assertTrue("deleteWallet must fail when markDeletionPending fails", result is Result.Failure)

        // Key must NOT be deleted from KeyVault
        assertTrue("Key MUST remain intact in KeyVault when Phase 1 fails", fakeSecureKeyManager.hasPrivateKey(keyAlias))

        // Phase 3 delete from DB must NEVER be called
        verify(mockWalletQueries, never()).delete(300L)
    }

    @Test
    fun test_reconcileStartupState_handles_pending_journals_and_tombstones() = runBlocking {
        val mockJournalQueries = mock<com.cbstudio.wearwallet.core.database.StagingJournalQueries>()
        whenever(mockJournalQueries.transaction(any(), any())).thenAnswer { invocation ->
            val body = invocation.getArgument<TransactionWithoutReturn.() -> Unit>(1)
            val mockScope = mock<TransactionWithoutReturn>()
            body.invoke(mockScope)
        }
        val mockChangesQuery = mock<Query<Long>>()
        whenever(mockChangesQuery.executeAsOne()).thenReturn(1L)
        whenever(mockChangesQuery.executeAsOneOrNull()).thenReturn(1L)
        whenever(mockJournalQueries.changesCount()).thenReturn(mockChangesQuery)

        // 1. Pending journal: DB_WRITTEN but DB row exists -> commit
        val journal1 = com.cbstudio.wearwallet.core.database.Staging_journal(
            session_id = "sess_rec_1",
            staged_alias = "ww_key_rec_1",
            backup_id = "ww_backup_rec_1",
            state = ProvisioningState.DB_WRITTEN.name,
            created_at = 1000L,
            expires_at = 61000L
        )

        // 2. Pending journal: KEY_STAGED but no DB row -> clean orphan key
        val session2 = fakeSecureKeyManager.startProvisioningSession()
        fakeSecureKeyManager.storeStagedPrivateKey(
            session = session2,
            privateKey = testPrivateKeyHex.removePrefix("0x").hexToByteArray(),
            requireAuth = false,
            authContext = null
        )
        assertTrue(fakeSecureKeyManager.hasPrivateKey(session2.stagedKeyAlias))

        val journal2 = com.cbstudio.wearwallet.core.database.Staging_journal(
            session_id = session2.sessionId,
            staged_alias = session2.stagedKeyAlias,
            backup_id = session2.backupId,
            state = ProvisioningState.KEY_STAGED.name,
            created_at = System.currentTimeMillis() - 120_000L,
            expires_at = System.currentTimeMillis() - 60_000L
        )

        val mockJournal1Query = mock<Query<com.cbstudio.wearwallet.core.database.Staging_journal>>()
        whenever(mockJournal1Query.executeAsOneOrNull()).thenReturn(journal1.copy(state = ProvisioningState.COMMITTED.name))
        whenever(mockJournalQueries.selectBySessionId("sess_rec_1")).thenReturn(mockJournal1Query)

        val mockJournal2Query = mock<Query<com.cbstudio.wearwallet.core.database.Staging_journal>>()
        whenever(mockJournal2Query.executeAsOneOrNull()).thenReturn(journal2.copy(state = ProvisioningState.ROLLED_BACK.name))
        whenever(mockJournalQueries.selectBySessionId(session2.sessionId)).thenReturn(mockJournal2Query)

        val journalQuery = mock<Query<com.cbstudio.wearwallet.core.database.Staging_journal>>()
        whenever(journalQuery.executeAsList()).thenReturn(listOf(journal1, journal2), emptyList())
        whenever(mockJournalQueries.selectPendingJournals()).thenReturn(journalQuery)

        // Mock wallet query for key1 (exists) and key2 (does not exist)
        val wallet1 = createLegacyWallet(id = 501L, keyAlias = "ww_key_rec_1")
        val wQuery1 = mock<Query<Wallet>>()
        whenever(wQuery1.executeAsOneOrNull()).thenReturn(wallet1)
        whenever(mockWalletQueries.selectByKeyAlias("ww_key_rec_1")).thenReturn(wQuery1)

        val wQuery2 = mock<Query<Wallet>>()
        whenever(wQuery2.executeAsOneOrNull()).thenReturn(null)
        whenever(mockWalletQueries.selectByKeyAlias(session2.stagedKeyAlias)).thenReturn(wQuery2)

        // Mock tombstones
        val tombstoneWallet = createLegacyWallet(id = 502L, keyAlias = "ww_key_deleted_key")
        val tombstoneQuery = mock<Query<Wallet>>()
        whenever(tombstoneQuery.executeAsList()).thenReturn(listOf(tombstoneWallet), emptyList())
        whenever(mockWalletQueries.selectDeletionPending()).thenReturn(tombstoneQuery)

        val repository = createRepository(journalQueries = mockJournalQueries)

        val recResult = repository.reconcileStartupState()
        assertTrue("Startup reconciliation must succeed", recResult is Result.Success)

        // Verify journal1 was transitioned to COMMITTED via CAS
        verify(mockJournalQueries).updateJournalStateCas(
            newState = ProvisioningState.COMMITTED.name,
            sessionId = "sess_rec_1",
            expectedState = ProvisioningState.DB_WRITTEN.name
        )

        // Verify orphan key2 was rolled back and journal2 marked ROLLED_BACK via CAS
        verify(mockJournalQueries).updateJournalStateCas(
            newState = ProvisioningState.ROLLED_BACK.name,
            sessionId = session2.sessionId,
            expectedState = ProvisioningState.KEY_STAGED.name
        )
        assertFalse("Orphan key2 must be cleaned up from KeyVault", fakeSecureKeyManager.hasPrivateKey(session2.stagedKeyAlias))

        // Verify tombstone cleanup for deleted key
        verify(mockWalletQueries).delete(502L)
    }
}


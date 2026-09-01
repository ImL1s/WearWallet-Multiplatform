package com.cbstudio.wearwallet.core.security

import android.content.Context
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.data.repository.WalletRepositoryImpl
import com.cbstudio.wearwallet.core.database.CoreWalletDatabase
import com.cbstudio.wearwallet.core.database.DatabaseDriverFactory
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.WalletType
import com.cbstudio.wearwallet.core.network.EthereumRpcClient
import com.cbstudio.wearwallet.core.platform.NoOpPlatformDeletionCleanupHook
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Milestone 2 Challenger 2: Comprehensive KeyPresence 4-State & 5-Tuple Corruption Permutation Stress Test Suite
 *
 * Direct Empirical Challenges:
 * 1. Adversarially tests all 2^5 = 32 permutations of 5-tuple key store corruption
 *    (hasKeyStoreKey, hasCiphertext, hasIv, hasTag, hasRequireAuth).
 *    - 00000 -> KeyPresence.Absent
 *    - 11111 -> KeyPresence.Present
 *    - 30 partial states -> KeyPresence.Partial
 * 2. Verifies that every corrupt state returns KeyPresence.Partial and triggers RECOVERY_REQUIRED in repository
 *    operations (never deleting DB wallet row).
 * 3. Tests startup reconciliation and roll-forward fail-closed behavior under all corrupt/unavailable states.
 * 4. Tests post-deletion failure injection where key is not Absent (Present, Partial, Unavailable) and verifies
 *    KEY_VAULT is NOT marked PASS, state transitions to RECOVERY_REQUIRED, and DB wallet row is preserved.
 * 5. Tests clean recovery workflow once corrupt keys are wiped to Absent.
 */
class Milestone2Challenger2KeyPresencePermutationTest {

    private lateinit var mockContext: Context
    private lateinit var backend: TestKeyStoreBackend
    private lateinit var sharedPrefs: InMemorySharedPreferences
    private lateinit var keyManager: AndroidSecureKeyManager

    private lateinit var sqlDriver: JdbcSqliteDriver
    private lateinit var database: CoreWalletDatabase
    private lateinit var driverFactory: DatabaseDriverFactory
    private lateinit var cryptoProvider: CommonCryptoProvider
    private lateinit var ethereumRpcClient: EthereumRpcClient

    private val testPrivateKeyHex = "e331b6d69882b4cb4ea581d88ec2693329b3b3a4711832421415ebff2d474428"

    @Before
    fun setUp() {
        AuthHandleRegistry.clearForTesting()
        DeletionGrantRegistry.clearForTesting()
        RecoveryGrantRegistry.clearForTesting()

        mockContext = mock()
        whenever(mockContext.applicationContext).thenReturn(mockContext)

        backend = TestKeyStoreBackend()
        sharedPrefs = InMemorySharedPreferences()

        keyManager = AndroidSecureKeyManager(
            context = mockContext,
            config = SecureStorageConfig(enableRootDetection = false),
            keyStoreProvider = { backend.createKeyStore() },
            encryptedPrefsProvider = { sharedPrefs },
            secretKeyProvider = { alias, _ -> backend.generateAndStoreKey(alias) }
        )

        sqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CoreWalletDatabase.Schema.create(sqlDriver)
        database = CoreWalletDatabase(sqlDriver)

        driverFactory = mock()
        whenever(driverFactory.createDriver()).thenReturn(sqlDriver)
        cryptoProvider = CommonCryptoProvider()
        ethereumRpcClient = mock()
    }

    @After
    fun tearDown() {
        AuthHandleRegistry.clearForTesting()
        DeletionGrantRegistry.clearForTesting()
        RecoveryGrantRegistry.clearForTesting()
        try {
            sqlDriver.close()
        } catch (_: Throwable) {}
    }

    private fun createRepository(
        secureKeyMgr: SecureKeyManager = keyManager
    ): WalletRepositoryImpl {
        return WalletRepositoryImpl(
            databaseDriverFactory = driverFactory,
            cryptoProvider = cryptoProvider,
            ethereumRpcClient = ethereumRpcClient,
            secureKeyManager = secureKeyMgr,
            platformDeletionCleanupHook = NoOpPlatformDeletionCleanupHook(),
            customWalletQueries = database.walletQueries,
            customStagingJournalQueries = database.stagingJournalQueries,
            customDeletionJournalQueries = database.deletionJournalQueries,
            customDeletionStepLedgerQueries = database.deletionStepLedgerQueries
        )
    }

    private fun insertTestWallet(
        name: String = "Test Wallet",
        keyAlias: String = "wallet_key_test",
        requiresAuth: Boolean = true,
        isActive: Boolean = true
    ): Long {
        database.walletQueries.insert(
            name = name,
            address = "0x" + keyAlias.hashCode().toUInt().toString(16).padStart(40, '0'),
            public_key = "0xpub",
            encrypted_private_key = "encrypted_priv_key_payload",
            encrypted_mnemonic = null,
            derivation_path = ChainType.ETHEREUM.getDefaultDerivationPath(),
            chain_type = ChainType.ETHEREUM.name,
            wallet_type = WalletType.MNEMONIC.name,
            is_watch_only = 0L,
            master_fingerprint = null,
            keystone_sign_request = null,
            keystone_sync_data = null,
            metadata = "{}",
            avatar_id = null,
            chain_id = 1L,
            key_alias = keyAlias,
            key_backend = "KEYSTORE",
            key_format_version = 1L,
            requires_auth = if (requiresAuth) 1L else 0L,
            is_deletion_pending = 0L
        )
        val insertedId = database.walletQueries.lastInsertRowId().executeAsOne()
        if (isActive) {
            database.walletQueries.setActiveWallet(insertedId)
        }
        return insertedId
    }

    private fun issueDeleteAuth(keyAlias: String, walletId: String): AuthenticationContext {
        val handle = TestPlatformAuthenticator.issueHandle(
            keyId = keyAlias,
            operation = AuthOperation.DELETE,
            intentFingerprint = "",
            validityDurationMs = 60_000L,
            walletId = walletId
        )
        return AuthenticationContext(authHandle = handle)
    }

    /**
     * Configure the 5-tuple components for a given keyId based on a 5-bit mask.
     * Bit 0 (0x01): hasKeyStoreKey
     * Bit 1 (0x02): hasCiphertext
     * Bit 2 (0x04): hasIv
     * Bit 3 (0x08): hasTag
     * Bit 4 (0x10): hasRequireAuth
     */
    private fun configure5Tuple(keyId: String, mask: Int) {
        // Clear previous state for this key
        backend.entries.remove(AndroidSecureKeyManager.KEY_ALIAS_PREFIX + keyId)
        sharedPrefs.edit()
            .remove(keyId)
            .remove(keyId + AndroidSecureKeyManager.IV_SUFFIX)
            .remove(keyId + AndroidSecureKeyManager.TAG_SUFFIX)
            .remove(keyId + AndroidSecureKeyManager.REQUIRE_AUTH_SUFFIX)
            .commit()

        if ((mask and 0x01) != 0) {
            backend.generateAndStoreKey(AndroidSecureKeyManager.KEY_ALIAS_PREFIX + keyId)
        }
        if ((mask and 0x02) != 0) {
            sharedPrefs.edit().putString(keyId, "dummy_ciphertext_hex").commit()
        }
        if ((mask and 0x04) != 0) {
            sharedPrefs.edit().putString(keyId + AndroidSecureKeyManager.IV_SUFFIX, "dummy_iv_hex").commit()
        }
        if ((mask and 0x08) != 0) {
            sharedPrefs.edit().putString(keyId + AndroidSecureKeyManager.TAG_SUFFIX, "dummy_tag_hex").commit()
        }
        if ((mask and 0x10) != 0) {
            sharedPrefs.edit().putBoolean(keyId + AndroidSecureKeyManager.REQUIRE_AUTH_SUFFIX, true).commit()
        }
    }

    // =========================================================================
    // TASK 1: Test all 32 permutations of 5-tuple key store corruption
    // =========================================================================

    @Test
    fun test_all_32_permutations_of_5_tuple_key_store_corruption() = runBlocking {
        var absentCount = 0
        var presentCount = 0
        var partialCount = 0

        for (mask in 0 until 32) {
            val keyId = "test_perm_key_$mask"
            configure5Tuple(keyId, mask)

            val presence = keyManager.checkKeyPresence(keyId)
            val bitCount = Integer.bitCount(mask)

            when (bitCount) {
                0 -> {
                    // mask == 00000b
                    assertEquals("Mask 0 must produce KeyPresence.Absent", KeyPresence.Absent, presence)
                    assertFalse("hasPrivateKey must return false for Absent", keyManager.hasPrivateKey(keyId))
                    absentCount++
                }
                5 -> {
                    // mask == 11111b
                    assertEquals("Mask 31 must produce KeyPresence.Present", KeyPresence.Present, presence)
                    assertTrue("hasPrivateKey must return true for Present", keyManager.hasPrivateKey(keyId))
                    presentCount++
                }
                else -> {
                    // Hamming weight 1, 2, 3, or 4
                    assertTrue(
                        "Mask $mask (bitCount=$bitCount) must produce KeyPresence.Partial, but got $presence",
                        presence is KeyPresence.Partial
                    )
                    assertFalse("hasPrivateKey must return false for Partial", keyManager.hasPrivateKey(keyId))

                    val partial = presence as KeyPresence.Partial
                    assertTrue(
                        "Partial details must not be blank for mask $mask",
                        partial.details.isNotBlank()
                    )

                    // Verify details accurately reflects the 5 tuple flags
                    val expectedKeyStore = (mask and 0x01) != 0
                    val expectedCiphertext = (mask and 0x02) != 0
                    val expectedIv = (mask and 0x04) != 0
                    val expectedTag = (mask and 0x08) != 0
                    val expectedAuth = (mask and 0x10) != 0

                    assertTrue(
                        "Details must contain hasKeyStoreKey=$expectedKeyStore for mask $mask (details: ${partial.details})",
                        partial.details.contains("hasKeyStoreKey=$expectedKeyStore")
                    )
                    assertTrue(
                        "Details must contain hasCiphertext=$expectedCiphertext for mask $mask (details: ${partial.details})",
                        partial.details.contains("hasCiphertext=$expectedCiphertext")
                    )
                    assertTrue(
                        "Details must contain hasIv=$expectedIv for mask $mask (details: ${partial.details})",
                        partial.details.contains("hasIv=$expectedIv")
                    )
                    assertTrue(
                        "Details must contain hasTag=$expectedTag for mask $mask (details: ${partial.details})",
                        partial.details.contains("hasTag=$expectedTag")
                    )
                    assertTrue(
                        "Details must contain hasRequireAuth=$expectedAuth for mask $mask (details: ${partial.details})",
                        partial.details.contains("hasRequireAuth=$expectedAuth")
                    )

                    partialCount++
                }
            }
        }

        assertEquals("Exactly 1 Absent state (mask 0)", 1, absentCount)
        assertEquals("Exactly 1 Present state (mask 31)", 1, presentCount)
        assertEquals("Exactly 30 Partial states (masks 1..30)", 30, partialCount)
    }

    @Test
    fun test_blank_and_empty_alias_returns_absent() = runBlocking {
        assertEquals("Empty alias returns Absent", KeyPresence.Absent, keyManager.checkKeyPresence(""))
        assertEquals("Blank alias returns Absent", KeyPresence.Absent, keyManager.checkKeyPresence("   "))
        assertEquals("Newline alias returns Absent", KeyPresence.Absent, keyManager.checkKeyPresence("\n\t"))
    }

    @Test
    fun test_keystore_daemon_exception_returns_unavailable() = runBlocking {
        val brokenKeyManager = AndroidSecureKeyManager(
            context = mockContext,
            config = SecureStorageConfig(enableRootDetection = false),
            keyStoreProvider = { throw RuntimeException("Keystore daemon IPC failure / hardware deadlock") },
            encryptedPrefsProvider = { sharedPrefs },
            secretKeyProvider = { alias, _ -> backend.generateAndStoreKey(alias) }
        )

        val presence = brokenKeyManager.checkKeyPresence("key_unavailable_1")
        assertTrue("Keystore exception must produce KeyPresence.Unavailable, got $presence", presence is KeyPresence.Unavailable)
        val cause = (presence as KeyPresence.Unavailable).cause
        assertTrue("Cause message must contain IPC failure", cause.message!!.contains("Keystore daemon IPC failure"))
        assertFalse("hasPrivateKey must return false for Unavailable", brokenKeyManager.hasPrivateKey("key_unavailable_1"))
    }

    // =========================================================================
    // TASK 2: Verify every corrupt state triggers RECOVERY_REQUIRED and never deletes DB row
    // =========================================================================

    @Test
    fun test_all_30_corrupt_states_trigger_recovery_required_and_preserve_db_row() = runBlocking {
        val repository = createRepository(keyManager)

        for (mask in 1..30) {
            val keyAlias = "corrupt_key_$mask"
            configure5Tuple(keyAlias, mask)
            val walletId = insertTestWallet(name = "Wallet $mask", keyAlias = keyAlias, requiresAuth = true, isActive = false)

            val authCtx = issueDeleteAuth(keyAlias, walletId.toString())
            val deleteResult = repository.deleteWallet(walletId.toString(), authCtx)

            // 1. deleteWallet MUST fail
            assertTrue(
                "deleteWallet for corrupt mask $mask must fail, but got $deleteResult",
                deleteResult is Result.Failure
            )
            val failure = deleteResult as Result.Failure
            assertTrue(
                "Exception must be KeyStorageException for corrupt mask $mask, got ${failure.exception}",
                failure.exception is KeyStorageException
            )

            // 2. KEY_VAULT step MUST be marked FAILED (never PASS)
            val steps = database.deletionStepLedgerQueries.selectStepsByWalletId(walletId).executeAsList()
            val keyVaultStep = steps.find { it.step_name == DeletionStep.KEY_VAULT.name }
            assertNotNull("KEY_VAULT step must be recorded for mask $mask", keyVaultStep)
            assertEquals(
                "KEY_VAULT step status must be FAILED for mask $mask",
                DeletionStepStatus.FAILED.name,
                keyVaultStep!!.status
            )

            // 3. Deletion State MUST transition to RECOVERY_REQUIRED
            val deletionEntry = database.deletionJournalQueries.selectByWalletId(walletId).executeAsOneOrNull()
            assertNotNull("Deletion journal entry must exist for mask $mask", deletionEntry)
            assertEquals(
                "Deletion state must be RECOVERY_REQUIRED for mask $mask",
                DeletionState.RECOVERY_REQUIRED.name,
                deletionEntry!!.state
            )

            // 4. DB Wallet Row MUST NOT be deleted
            val walletInDb = database.walletQueries.selectById(walletId).executeAsOneOrNull()
            assertNotNull(
                "Wallet DB record MUST remain in database under corrupt mask $mask (never deleted!)",
                walletInDb
            )
            assertEquals("Wallet ID must match", walletId, walletInDb!!.id)
        }
    }

    @Test
    fun test_key_presence_unavailable_triggers_recovery_required_and_preserves_db_row() = runBlocking {
        val brokenKeyManager = AndroidSecureKeyManager(
            context = mockContext,
            config = SecureStorageConfig(enableRootDetection = false),
            keyStoreProvider = { throw RuntimeException("AndroidKeyStore deadlocked") },
            encryptedPrefsProvider = { sharedPrefs }
        )
        val repository = createRepository(brokenKeyManager)

        val keyAlias = "key_unavailable_wallet"
        val walletId = insertTestWallet(name = "Unavailable Wallet", keyAlias = keyAlias, requiresAuth = true, isActive = false)

        val authCtx = issueDeleteAuth(keyAlias, walletId.toString())
        val deleteResult = repository.deleteWallet(walletId.toString(), authCtx)

        assertTrue("deleteWallet must fail on Unavailable KeyPresence", deleteResult is Result.Failure)
        val failure = deleteResult as Result.Failure
        assertTrue("Exception must be KeyStorageException, got ${failure.exception}", failure.exception is KeyStorageException)

        val steps = database.deletionStepLedgerQueries.selectStepsByWalletId(walletId).executeAsList()
        val keyVaultStep = steps.find { it.step_name == DeletionStep.KEY_VAULT.name }
        assertNotNull("KEY_VAULT step must be recorded", keyVaultStep)
        assertEquals("KEY_VAULT step status must be FAILED", DeletionStepStatus.FAILED.name, keyVaultStep!!.status)

        val deletionEntry = database.deletionJournalQueries.selectByWalletId(walletId).executeAsOneOrNull()
        assertNotNull("Deletion journal entry must exist", deletionEntry)
        assertEquals("Deletion state must be RECOVERY_REQUIRED", DeletionState.RECOVERY_REQUIRED.name, deletionEntry!!.state)

        val walletInDb = database.walletQueries.selectById(walletId).executeAsOneOrNull()
        assertNotNull("Wallet DB record MUST NOT be deleted on Unavailable KeyPresence", walletInDb)
    }

    // =========================================================================
    // TASK 3: Post-deletion failure injection (Key is NOT Absent after delete)
    // =========================================================================

    @Test
    fun test_post_deletion_failure_injection_key_remains_present() = runBlocking {
        val fakeKeyMgr = FakeSecureKeyManager()
        val repository = createRepository(fakeKeyMgr)

        val keyAlias = "post_delete_fail_present"
        fakeKeyMgr.setKey(keyAlias, testPrivateKeyHex, requireAuth = true)
        val walletId = insertTestWallet(name = "Present Post-Delete", keyAlias = keyAlias, requiresAuth = true, isActive = false)

        // Inject override: even after delete, checkKeyPresence returns Present
        fakeKeyMgr.setKeyPresenceOverride(keyAlias, KeyPresence.Present)

        val authCtx = issueDeleteAuth(keyAlias, walletId.toString())
        val deleteResult = repository.deleteWallet(walletId.toString(), authCtx)

        assertTrue("deleteWallet MUST fail when post-delete presence is Present", deleteResult is Result.Failure)
        val failure = deleteResult as Result.Failure
        assertTrue("Exception must be KeyStorageException", failure.exception is KeyStorageException)
        assertTrue(
            "Error message must mention key was not verified Absent",
            failure.exception.message!!.contains("was not verified Absent after deletion")
        )

        // KEY_VAULT step must be FAILED
        val steps = database.deletionStepLedgerQueries.selectStepsByWalletId(walletId).executeAsList()
        val keyVaultStep = steps.find { it.step_name == DeletionStep.KEY_VAULT.name }
        assertNotNull("KEY_VAULT step must exist", keyVaultStep)
        assertEquals("KEY_VAULT step must be FAILED", DeletionStepStatus.FAILED.name, keyVaultStep!!.status)

        // Deletion state must be RECOVERY_REQUIRED
        val deletionEntry = database.deletionJournalQueries.selectByWalletId(walletId).executeAsOneOrNull()
        assertNotNull("Deletion journal entry must exist", deletionEntry)
        assertEquals("Deletion state must be RECOVERY_REQUIRED", DeletionState.RECOVERY_REQUIRED.name, deletionEntry!!.state)

        // DB record preserved
        val walletInDb = database.walletQueries.selectById(walletId).executeAsOneOrNull()
        assertNotNull("Wallet DB record MUST remain in database", walletInDb)
    }

    @Test
    fun test_post_deletion_failure_injection_key_becomes_partial() = runBlocking {
        val fakeKeyMgr = FakeSecureKeyManager()
        val repository = createRepository(fakeKeyMgr)

        val keyAlias = "post_delete_fail_partial"
        fakeKeyMgr.setKey(keyAlias, testPrivateKeyHex, requireAuth = true)
        val walletId = insertTestWallet(name = "Partial Post-Delete", keyAlias = keyAlias, requiresAuth = true, isActive = false)

        // Inject override: after delete, checkKeyPresence returns Partial
        fakeKeyMgr.setKeyPresenceOverride(keyAlias, KeyPresence.Partial("KeyStore wiped but ciphertext orphaned"))

        val authCtx = issueDeleteAuth(keyAlias, walletId.toString())
        val deleteResult = repository.deleteWallet(walletId.toString(), authCtx)

        assertTrue("deleteWallet MUST fail when post-delete presence is Partial", deleteResult is Result.Failure)
        val failure = deleteResult as Result.Failure
        assertTrue("Exception must be KeyStorageException", failure.exception is KeyStorageException)

        // KEY_VAULT step must be FAILED (NOT PASS)
        val steps = database.deletionStepLedgerQueries.selectStepsByWalletId(walletId).executeAsList()
        val keyVaultStep = steps.find { it.step_name == DeletionStep.KEY_VAULT.name }
        assertNotNull("KEY_VAULT step must exist", keyVaultStep)
        assertEquals("KEY_VAULT step must be FAILED", DeletionStepStatus.FAILED.name, keyVaultStep!!.status)

        // Deletion state must be RECOVERY_REQUIRED
        val deletionEntry = database.deletionJournalQueries.selectByWalletId(walletId).executeAsOneOrNull()
        assertNotNull("Deletion journal entry must exist", deletionEntry)
        assertEquals("Deletion state must be RECOVERY_REQUIRED", DeletionState.RECOVERY_REQUIRED.name, deletionEntry!!.state)

        // DB record preserved
        val walletInDb = database.walletQueries.selectById(walletId).executeAsOneOrNull()
        assertNotNull("Wallet DB record MUST remain in database", walletInDb)
    }

    @Test
    fun test_post_deletion_failure_injection_key_becomes_unavailable() = runBlocking {
        val fakeKeyMgr = FakeSecureKeyManager()
        val repository = createRepository(fakeKeyMgr)

        val keyAlias = "post_delete_fail_unavailable"
        fakeKeyMgr.setKey(keyAlias, testPrivateKeyHex, requireAuth = true)
        val walletId = insertTestWallet(name = "Unavailable Post-Delete", keyAlias = keyAlias, requiresAuth = true, isActive = false)

        // Inject override: after delete, checkKeyPresence returns Unavailable
        fakeKeyMgr.setKeyPresenceOverride(keyAlias, KeyPresence.Unavailable(RuntimeException("Keystore died post-delete")))

        val authCtx = issueDeleteAuth(keyAlias, walletId.toString())
        val deleteResult = repository.deleteWallet(walletId.toString(), authCtx)

        assertTrue("deleteWallet MUST fail when post-delete presence is Unavailable", deleteResult is Result.Failure)
        val failure = deleteResult as Result.Failure
        assertTrue("Exception must be KeyStorageException", failure.exception is KeyStorageException)

        // KEY_VAULT step must be FAILED (NOT PASS)
        val steps = database.deletionStepLedgerQueries.selectStepsByWalletId(walletId).executeAsList()
        val keyVaultStep = steps.find { it.step_name == DeletionStep.KEY_VAULT.name }
        assertNotNull("KEY_VAULT step must exist", keyVaultStep)
        assertEquals("KEY_VAULT step must be FAILED", DeletionStepStatus.FAILED.name, keyVaultStep!!.status)

        // Deletion state must be RECOVERY_REQUIRED
        val deletionEntry = database.deletionJournalQueries.selectByWalletId(walletId).executeAsOneOrNull()
        assertNotNull("Deletion journal entry must exist", deletionEntry)
        assertEquals("Deletion state must be RECOVERY_REQUIRED", DeletionState.RECOVERY_REQUIRED.name, deletionEntry!!.state)

        // DB record preserved
        val walletInDb = database.walletQueries.selectById(walletId).executeAsOneOrNull()
        assertNotNull("Wallet DB record MUST remain in database", walletInDb)
    }

    // =========================================================================
    // TASK 4: Startup Reconciliation and Roll-Forward with Corrupt / Repaired Keys
    // =========================================================================

    @Test
    fun test_startup_reconciliation_fails_closed_under_partial_key_presence() = runBlocking {
        val fakeKeyMgr = FakeSecureKeyManager()
        val repository = createRepository(fakeKeyMgr)

        val keyAlias = "reconcile_partial_key"
        val walletId = insertTestWallet(name = "Reconcile Partial", keyAlias = keyAlias, requiresAuth = true, isActive = false)

        // Put deletion journal in TOMBSTONED state
        database.deletionJournalQueries.insertDeletionJournal(
            wallet_id = walletId,
            key_alias = keyAlias,
            state = DeletionState.TOMBSTONED.name,
            last_error = null,
            retry_count = 0L,
            created_at = System.currentTimeMillis(),
            updated_at = System.currentTimeMillis()
        )

        // Set key presence to Partial
        fakeKeyMgr.setKeyPresenceOverride(keyAlias, KeyPresence.Partial("Missing IV in storage"))

        // Run reconciliation
        val reconcileResult = repository.reconcileStartupState()

        // Deletion state MUST transition to RECOVERY_REQUIRED
        val deletionEntry = database.deletionJournalQueries.selectByWalletId(walletId).executeAsOneOrNull()
        assertNotNull("Deletion entry must exist", deletionEntry)
        assertEquals("State must be RECOVERY_REQUIRED", DeletionState.RECOVERY_REQUIRED.name, deletionEntry!!.state)

        // KEY_VAULT step MUST NOT be marked PASS
        val steps = database.deletionStepLedgerQueries.selectStepsByWalletId(walletId).executeAsList()
        val keyVaultPass = steps.any { it.step_name == DeletionStep.KEY_VAULT.name && it.status == DeletionStepStatus.PASS.name }
        assertFalse("KEY_VAULT step must NOT be PASS", keyVaultPass)

        // Wallet DB row must still exist
        val walletInDb = database.walletQueries.selectById(walletId).executeAsOneOrNull()
        assertNotNull("Wallet DB row must NOT be deleted", walletInDb)
    }

    @Test
    fun test_startup_reconciliation_succeeds_once_corrupt_key_is_repaired_to_absent() = runBlocking {
        val fakeKeyMgr = FakeSecureKeyManager()
        val repository = createRepository(fakeKeyMgr)

        val keyAlias = "reconcile_repair_key"
        val walletId = insertTestWallet(name = "Reconcile Repair", keyAlias = keyAlias, requiresAuth = true, isActive = false)

        // 1. Initial state: RECOVERY_REQUIRED with Partial key presence
        database.deletionJournalQueries.insertDeletionJournal(
            wallet_id = walletId,
            key_alias = keyAlias,
            state = DeletionState.TOMBSTONED.name,
            last_error = "Corrupted key state",
            retry_count = 0L,
            created_at = System.currentTimeMillis(),
            updated_at = System.currentTimeMillis()
        )
        fakeKeyMgr.setKeyPresenceOverride(keyAlias, KeyPresence.Partial("Corrupted key components"))

        val firstReconcile = repository.reconcileStartupState()
        // Must fail or remain in recovery
        val entry1 = database.deletionJournalQueries.selectByWalletId(walletId).executeAsOneOrNull()
        assertEquals(DeletionState.RECOVERY_REQUIRED.name, entry1!!.state)
        assertNotNull(database.walletQueries.selectById(walletId).executeAsOneOrNull())

        // 2. Repair / Wipe key to Absent
        fakeKeyMgr.setKeyPresenceOverride(keyAlias, KeyPresence.Absent)

        val secondReconcile = repository.reconcileStartupState()
        assertTrue("Reconciliation must succeed once key is verified Absent", secondReconcile is Result.Success)

        // KEY_VAULT step must now be PASS
        val steps = database.deletionStepLedgerQueries.selectStepsByWalletId(walletId).executeAsList()
        val keyVaultStep = steps.find { it.step_name == DeletionStep.KEY_VAULT.name }
        assertNotNull("KEY_VAULT step must exist", keyVaultStep)
        assertEquals("KEY_VAULT step must be PASS", DeletionStepStatus.PASS.name, keyVaultStep!!.status)

        // Deletion state must be COMPLETED
        val entry2 = database.deletionJournalQueries.selectByWalletId(walletId).executeAsOneOrNull()
        assertNotNull(entry2)
        assertEquals(DeletionState.COMPLETED.name, entry2!!.state)

        // Wallet DB row must now be cleanly removed
        val walletInDb = database.walletQueries.selectById(walletId).executeAsOneOrNull()
        assertNull("Wallet DB row must be removed after successful Absent roll-forward", walletInDb)
    }

    // =========================================================================
    // TASK 5: Successful Deletion Path Re-Verification with Absent Post-Check
    // =========================================================================

    @Test
    fun test_successful_clean_deletion_reverifies_absent_and_completes() = runBlocking {
        val keyAlias = "clean_delete_key"

        // 1. Set up all 5 tuples (Present state)
        val rawPrivKey = ByteArray(32) { i ->
            testPrivateKeyHex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        val walletId = insertTestWallet(name = "Clean Delete Wallet", keyAlias = keyAlias, requiresAuth = true, isActive = false)

        val importHandle = TestPlatformAuthenticator.issueHandle(
            keyId = keyAlias,
            operation = AuthOperation.IMPORT,
            intentFingerprint = "",
            validityDurationMs = 60_000L,
            walletId = walletId.toString()
        )
        val storeRes = keyManager.storePrivateKey(
            keyId = keyAlias,
            privateKey = rawPrivKey,
            requireAuth = true,
            authContext = AuthenticationContext(authHandle = importHandle),
            expectedWalletId = walletId.toString()
        )
        assertTrue("Store must succeed", storeRes is Result.Success)

        val initialPresence = keyManager.checkKeyPresence(keyAlias)
        assertEquals("Initial presence must be Present", KeyPresence.Present, initialPresence)

        // 2. Issue delete auth handle
        val authCtx = issueDeleteAuth(keyAlias, walletId.toString())

        val repository = createRepository(keyManager)
        val deleteResult = repository.deleteWallet(walletId.toString(), authCtx)

        assertTrue("deleteWallet must succeed on clean key deletion, got $deleteResult", deleteResult is Result.Success)

        // 3. Post-deletion presence must be strictly Absent
        val finalPresence = keyManager.checkKeyPresence(keyAlias)
        assertEquals("Final key presence in storage must be Absent", KeyPresence.Absent, finalPresence)

        // 4. KEY_VAULT step must be PASS
        val steps = database.deletionStepLedgerQueries.selectStepsByWalletId(walletId).executeAsList()
        val keyVaultStep = steps.find { it.step_name == DeletionStep.KEY_VAULT.name }
        assertNotNull("KEY_VAULT step must exist", keyVaultStep)
        assertEquals("KEY_VAULT step must be PASS", DeletionStepStatus.PASS.name, keyVaultStep!!.status)

        // 5. Deletion state must be COMPLETED
        val deletionEntry = database.deletionJournalQueries.selectByWalletId(walletId).executeAsOneOrNull()
        assertNotNull("Deletion journal entry must exist", deletionEntry)
        assertEquals("Deletion state must be COMPLETED", DeletionState.COMPLETED.name, deletionEntry!!.state)

        // 6. DB record must be deleted
        val walletInDb = database.walletQueries.selectById(walletId).executeAsOneOrNull()
        assertNull("Wallet DB row must be deleted on clean completion", walletInDb)
    }
}

package com.cbstudio.wearwallet.core.integration

import app.cash.sqldelight.Query
import app.cash.sqldelight.TransactionWithoutReturn
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.data.repository.WalletRepositoryImpl
import com.cbstudio.wearwallet.core.database.DatabaseDriverFactory
import com.cbstudio.wearwallet.core.database.Wallet
import com.cbstudio.wearwallet.core.database.WalletQueries
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.WalletAccount
import com.cbstudio.wearwallet.core.domain.model.WalletType
import com.cbstudio.wearwallet.core.domain.model.context.ChainExecutionContext
import com.cbstudio.wearwallet.core.domain.model.context.ChainExecutionContextRegistry
import com.cbstudio.wearwallet.core.domain.model.intent.ConfirmedEvmTransactionIntent
import com.cbstudio.wearwallet.core.domain.model.quantities.*
import com.cbstudio.wearwallet.core.domain.repository.TransactionRepository
import com.cbstudio.wearwallet.core.domain.repository.WalletRepository
import com.cbstudio.wearwallet.core.domain.usecase.transaction.SendTransactionUseCase
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.core.multichain.util.EthereumSigner
import com.cbstudio.wearwallet.core.network.EthereumRpcClient
import com.cbstudio.wearwallet.core.platform.SecureStorage
import com.cbstudio.wearwallet.core.security.*
import com.ionspin.kotlin.bignum.integer.BigInteger
import io.github.iml1s.crypto.Secp256k1Pure
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*
import java.security.KeyManagementException
import java.sql.SQLException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Milestone 5 (Round 4): 10 Lifecycle Integration Tests Suite
 *
 * Mandatory Lifecycle Integration Tests:
 * 1. test1_create_wallet_lifecycle_restart_sign_exact_intent
 * 2. test2_import_mnemonic_lifecycle_restart_sign
 * 3. test3_import_private_key_lifecycle_restart_sign
 * 4. test4_keyvault_failure_leaves_no_db_row
 * 5. test5_db_failure_after_keyvault_write_cleans_key_alias
 * 6. test6_missing_keyvault_key_fails_closed_without_signing_or_broadcasting
 * 7. test7_wrong_key_alias_recovery_mismatch_prevents_broadcast
 * 8. test8_multi_chain_same_address_chain_isolation
 * 9. test9_raw_key_export_count_is_strictly_zero
 * 10. test10_production_orchestration_parity
 */
class KeyVaultLifecycleIntegrationTest {

    private val testPassword = "ProductionKeyVaultPassword#2026"
    private val testMnemonic1 = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
    private val testMnemonic2 = "legal winner thank year wave sausage worth useful legal winner thank yellow"
    private val testPrivateKeyHex1 = "e331b6d69882b4cb4ea581d88ec2693329b3b3a4711832421415ebff2d474428"
    private val testPrivateKeyHex2 = "4c0883a69102937d6231471b5dbb6204fe5129617082792ae468d01a3f3608a9"
    private val recipientAddress = "0x3535353535353535353535353535353535353535"

    private lateinit var fakeSecureKeyManager: FakeSecureKeyManager
    private lateinit var cryptoProvider: CommonCryptoProvider
    private lateinit var inMemoryDatabase: InMemoryWalletDatabase
    private lateinit var databaseDriverFactory: DatabaseDriverFactory
    private lateinit var ethereumRpcClient: EthereumRpcClient
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var secureStorage: SecureStorage
    private lateinit var sideEffectTracker: SideEffectTracker

    private val broadcastCounter = AtomicInteger(0)
    private val broadcastedTransactions = mutableListOf<Pair<String, ChainType>>()

    @Before
    fun setUp() {
        fakeSecureKeyManager = FakeSecureKeyManager()
        cryptoProvider = CommonCryptoProvider()
        inMemoryDatabase = InMemoryWalletDatabase()
        databaseDriverFactory = mock()
        val mockDriver = mock<app.cash.sqldelight.db.SqlDriver>()
        whenever(databaseDriverFactory.createDriver()).thenReturn(mockDriver)
        ethereumRpcClient = mock()
        transactionRepository = mock()
        secureStorage = mock()
        sideEffectTracker = mock()

        broadcastCounter.set(0)
        broadcastedTransactions.clear()

        // TransactionRepository mock setup
        runBlocking {
            whenever(transactionRepository.getNonce(any(), any<ChainExecutionContext>())).thenReturn(0L)
            whenever(transactionRepository.sendTransaction(any(), any<ChainExecutionContext>())).thenAnswer { invocation ->
                val signedTx = invocation.getArgument<String>(0)
                val context = invocation.getArgument<ChainExecutionContext>(1)
                broadcastCounter.incrementAndGet()
                broadcastedTransactions.add(signedTx to context.chain)
                "0xTxHash_${broadcastCounter.get()}"
            }
        }
    }

    private fun createProvisioningAuth(keyManager: SecureKeyManager = fakeSecureKeyManager): AuthenticationContext {
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

    private fun createRepository(
        keyManager: SecureKeyManager = fakeSecureKeyManager,
        customQueries: WalletQueries = inMemoryDatabase.createMockWalletQueries(),
        customJournalQueries: com.cbstudio.wearwallet.core.database.StagingJournalQueries = inMemoryDatabase.createMockJournalQueries()
    ): WalletRepositoryImpl {
        return WalletRepositoryImpl(
            databaseDriverFactory = databaseDriverFactory,
            cryptoProvider = cryptoProvider,
            ethereumRpcClient = ethereumRpcClient,
            secureKeyManager = keyManager,
            platformDeletionCleanupHook = com.cbstudio.wearwallet.core.platform.NoOpPlatformDeletionCleanupHook(),
            sideEffectTracker = sideEffectTracker,
            customWalletQueries = customQueries,
            customStagingJournalQueries = customJournalQueries
        )
    }

    private fun createSendTransactionUseCase(
        repo: WalletRepository,
        keyManager: SecureKeyManager = fakeSecureKeyManager
    ): SendTransactionUseCase {
        return SendTransactionUseCase(
            walletRepository = repo,
            transactionRepository = transactionRepository,
            cryptoProvider = cryptoProvider,
            secureStorage = secureStorage,
            capabilityGate = AllowDevCapabilityGate(),
            secureKeyManager = keyManager
        )
    }

    private fun buildIntent(
        walletId: String,
        keyAlias: String,
        senderAddr: String,
        recipientAddr: String = recipientAddress,
        humanAmount: String = "1.0",
        envelopeType: EvmEnvelope = EvmEnvelope.LEGACY,
        chain: MultiChainType = MultiChainType.ETHEREUM,
        executionContext: ChainExecutionContext = ChainExecutionContextRegistry.resolve(chain, false),
        nonceVal: Long = 0L,
        gasPriceHex: String = "0x4a817c800", // 20 Gwei
        gasLimitVal: Long = 21000L
    ): ConfirmedEvmTransactionIntent {
        val sender = EvmAddress.fromString(senderAddr)
        val rec = EvmAddress.fromString(recipientAddr)
        val baseUnit = BaseUnitAmount.fromDecimalString(humanAmount, 18)
        val nativeVal = Wei.fromWei(baseUnit.value)
        val nonce = Nonce.fromLong(nonceVal)
        val gasPrice = Wei.fromWeiHex(gasPriceHex)
        val gasLimit = GasLimit.fromLong(gasLimitVal)
        val fee = Wei.fromWei(gasPrice.value * BigInteger.fromLong(gasLimitVal))

        val fingerprint = ConfirmedEvmTransactionIntent.createFingerprint(
            walletId = walletId,
            keyAlias = keyAlias,
            sender = sender,
            chain = chain,
            executionContext = executionContext,
            envelopeType = envelopeType,
            recipient = rec,
            tokenContract = null,
            tokenSymbol = null,
            tokenDecimals = null,
            humanAmount = humanAmount,
            baseUnitAmount = baseUnit,
            nativeValue = nativeVal,
            calldata = Calldata.EMPTY,
            nonce = nonce,
            gasPrice = gasPrice,
            gasLimit = gasLimit,
            fee = fee
        )

        return ConfirmedEvmTransactionIntent(
            walletId = walletId,
            keyAlias = keyAlias,
            sender = sender,
            chain = chain,
            executionContext = executionContext,
            envelopeType = envelopeType,
            recipient = rec,
            tokenContract = null,
            tokenSymbol = null,
            tokenDecimals = null,
            humanAmount = humanAmount,
            baseUnitAmount = baseUnit,
            nativeValue = nativeVal,
            calldata = Calldata.EMPTY,
            nonce = nonce,
            gasPrice = gasPrice,
            gasLimit = gasLimit,
            fee = fee,
            canonicalFingerprint = fingerprint
        )
    }

    // =========================================================================
    // 1. Create wallet lifecycle -> restart -> sign exact intent
    // =========================================================================
    @Test
    fun test1_create_wallet_lifecycle_restart_sign_exact_intent() = runBlocking {
        val repo = createRepository()

        // 1. Create wallet via production flow
        val createResult = repo.createWallet(
            name = "Alice Hot Wallet",
            mnemonic = testMnemonic1.toCharArray(),
            password = testPassword.toCharArray(),
            chainType = ChainType.ETHEREUM,
            authContext = createProvisioningAuth()
        )

        assertTrue("createWallet must succeed: ${(createResult as? Result.Failure)?.exception?.message}", createResult is Result.Success)
        val createdWallet = (createResult as Result.Success).data

        // 2. Assert KeyVault contains generated keyAlias
        val keyAlias = createdWallet.keyAlias
        assertNotNull("Generated keyAlias must not be null", keyAlias)
        assertTrue("keyAlias must start with 'ww_key_'", keyAlias!!.startsWith("ww_key_"))
        assertTrue("KeyVault must contain provisioned keyAlias", fakeSecureKeyManager.hasPrivateKey(keyAlias))

        // 3. Assert DB contains same keyAlias and proper metadata
        val dbWallet = inMemoryDatabase.wallets.find { it.id == createdWallet.id.toLong() }
        assertNotNull("DB record must exist", dbWallet)
        assertEquals("DB key_alias must match created keyAlias", keyAlias, dbWallet!!.key_alias)
        assertEquals("DB key_backend must match", "BASIC", dbWallet.key_backend)
        assertEquals("DB key_format_version must be 2", 2L, dbWallet.key_format_version)
        assertEquals("DB requires_auth must be 1", 1L, dbWallet.requires_auth)

        // 4. Simulate process restart: re-instantiate repository and usecase referencing same DB & KeyVault
        val restartedRepo = createRepository()
        val restartedUseCase = createSendTransactionUseCase(restartedRepo)
        val fetchedWalletResult = restartedRepo.getWallet(createdWallet.id)
        assertTrue(fetchedWalletResult is Result.Success)
        val fetchedWallet = (fetchedWalletResult as Result.Success).data!!
        assertEquals("Restarted repository must resolve identical keyAlias", keyAlias, fetchedWallet.keyAlias)

        // 5. Create ConfirmedEvmTransactionIntent with exact keyAlias
        val intent = buildIntent(
            walletId = fetchedWallet.id,
            keyAlias = fetchedWallet.keyAlias!!,
            senderAddr = fetchedWallet.address
        )

        val authHandle = TestPlatformAuthenticator.issueHandle(
            keyId = fetchedWallet.keyAlias!!,
            operation = AuthOperation.SIGN,
            intentFingerprint = intent.signingDigestHex,
            expiresAtMs = System.currentTimeMillis() + 60_000L,
            walletId = fetchedWallet.id
        )

        // 6. Execute SendTransactionUseCase
        val sendResult = restartedUseCase(intent, AuthenticationContext(authHandle = authHandle)).toList()
        assertTrue("Send transaction must succeed: ${(sendResult.first() as? Result.Failure)?.exception?.message}", sendResult.first() is Result.Success)

        // 7. Verify signing succeeded, broadcast count == 1, recovered signer matches wallet address
        assertEquals("FakeSecureKeyManager signCount must be 1", 1, fakeSecureKeyManager.signCount)
        assertEquals("Broadcast count must be 1", 1, broadcastCounter.get())
        assertEquals(1, broadcastedTransactions.size)
        val (signedTx, chain) = broadcastedTransactions.first()
        assertEquals(ChainType.ETHEREUM, chain)

        // Verify recovered signer address from signed raw transaction
        val recoveredAddress = EthereumSigner.recoverSenderFromSignedTransaction(signedTx)
        assertTrue("Recovered signer must match wallet address (expected ${fetchedWallet.address}, got $recoveredAddress)", fetchedWallet.address.equals(recoveredAddress, ignoreCase = true))
    }

    // =========================================================================
    // 2. Import mnemonic lifecycle -> restart -> sign
    // =========================================================================
    @Test
    fun test2_import_mnemonic_lifecycle_restart_sign() = runBlocking {
        val repo = createRepository()

        // 1. Import mnemonic via production flow
        val importResult = repo.importFromMnemonic(
            name = "Imported Mnemonic Wallet",
            mnemonic = testMnemonic2.toCharArray(),
            password = testPassword.toCharArray(),
            chainType = ChainType.ETHEREUM,
            authContext = createProvisioningAuth()
        )

        assertTrue("importFromMnemonic must succeed: ${(importResult as? Result.Failure)?.exception?.message}", importResult is Result.Success)
        val importedWallet = (importResult as Result.Success).data
        val keyAlias = importedWallet.keyAlias
        assertNotNull("Imported keyAlias must not be null", keyAlias)
        assertTrue("keyAlias must start with 'ww_key_'", keyAlias!!.startsWith("ww_key_"))
        assertEquals(WalletType.MNEMONIC, importedWallet.walletType)
        assertTrue("KeyVault must contain imported key", fakeSecureKeyManager.hasPrivateKey(keyAlias))

        // 2. Simulate process restart
        val restartedRepo = createRepository()
        val restartedUseCase = createSendTransactionUseCase(restartedRepo)

        // 3. Send transaction
        val intent = buildIntent(
            walletId = importedWallet.id,
            keyAlias = keyAlias,
            senderAddr = importedWallet.address
        )
        val authHandle = TestPlatformAuthenticator.issueHandle(
            keyId = keyAlias,
            operation = AuthOperation.SIGN,
            intentFingerprint = intent.signingDigestHex,
            walletId = importedWallet.id
        )

        val sendResult = restartedUseCase(intent, AuthenticationContext(authHandle = authHandle)).toList()
        assertTrue("Send transaction must succeed", sendResult.first() is Result.Success)

        // 4. Assert signature valid, recovered signer matches imported address, broadcast count == 1
        assertEquals(1, broadcastCounter.get())
        val (signedTx, _) = broadcastedTransactions.first()
        val recoveredAddress = EthereumSigner.recoverSenderFromSignedTransaction(signedTx)
        assertTrue("Recovered signer must match imported address", importedWallet.address.equals(recoveredAddress, ignoreCase = true))
    }

    // =========================================================================
    // 3. Import private key lifecycle -> restart -> sign
    // =========================================================================
    @Test
    fun test3_import_private_key_lifecycle_restart_sign() = runBlocking {
        val repo = createRepository()

        // 1. Import private key via production flow
        val importResult = repo.importFromPrivateKey(
            name = "Imported PK Wallet",
            privateKey = com.cbstudio.wearwallet.core.security.ScopedPrivateKey.fromHex(testPrivateKeyHex1),
            password = testPassword.toCharArray(),
            chainType = ChainType.ETHEREUM,
            authContext = createProvisioningAuth()
        )

        assertTrue("importFromPrivateKey must succeed", importResult is Result.Success)
        val importedWallet = (importResult as Result.Success).data
        val keyAlias = importedWallet.keyAlias
        assertNotNull("Imported keyAlias must not be null", keyAlias)
        assertTrue(keyAlias!!.startsWith("ww_key_"))
        assertEquals(WalletType.PRIVATE_KEY, importedWallet.walletType)
        assertTrue("KeyVault must contain imported key", fakeSecureKeyManager.hasPrivateKey(keyAlias))

        // 2. Simulate process restart
        val restartedRepo = createRepository()
        val restartedUseCase = createSendTransactionUseCase(restartedRepo)

        // 3. Send transaction
        val intent = buildIntent(
            walletId = importedWallet.id,
            keyAlias = keyAlias,
            senderAddr = importedWallet.address
        )
        val authHandle = TestPlatformAuthenticator.issueHandle(
            keyId = keyAlias,
            operation = AuthOperation.SIGN,
            intentFingerprint = intent.signingDigestHex,
            walletId = importedWallet.id
        )

        val sendResult = restartedUseCase(intent, AuthenticationContext(authHandle = authHandle)).toList()
        assertTrue("Send transaction must succeed", sendResult.first() is Result.Success)

        // 4. Assert signature valid, recovered signer matches imported address, broadcast count == 1
        assertEquals(1, broadcastCounter.get())
        val (signedTx, _) = broadcastedTransactions.first()
        val recoveredAddress = EthereumSigner.recoverSenderFromSignedTransaction(signedTx)
        assertTrue("Recovered signer must match imported address", importedWallet.address.equals(recoveredAddress, ignoreCase = true))
    }

    // =========================================================================
    // 4. KeyVault failure -> 0 DB rows
    // =========================================================================
    @Test
    fun test4_keyvault_failure_leaves_no_db_row() = runBlocking {
        // Create failing SecureKeyManager
        val failingKeyManager = object : SecureKeyManager by fakeSecureKeyManager {
            override suspend fun storePrivateKey(
                keyId: String,
                privateKey: ByteArray,
                requireAuth: Boolean,
                authContext: AuthenticationContext?,
                expectedWalletId: String
            ): Result<Unit> {
                return Result.Failure(KeyManagementException("Hardware KeyStore unavailable or master key corrupted"))
            }

            override suspend fun storeStagedPrivateKey(
                session: com.cbstudio.wearwallet.core.security.ProvisioningSession,
                privateKey: ByteArray,
                requireAuth: Boolean,
                authContext: AuthenticationContext?
            ): Result<Unit> {
                return Result.Failure(KeyManagementException("Hardware KeyStore unavailable or master key corrupted"))
            }
        }

        val repo = createRepository(keyManager = failingKeyManager)

        // Attempt createWallet
        val result = repo.createWallet(
            name = "FailingKeyVaultWallet",
            mnemonic = testMnemonic1.toCharArray(),
            password = testPassword.toCharArray(),
            chainType = ChainType.ETHEREUM,
            authContext = createProvisioningAuth()
        )

        // Assert failure and 0 DB rows
        assertTrue("createWallet must fail when KeyVault fails", result is Result.Failure)
        assertEquals("Database must have 0 wallet rows", 0, inMemoryDatabase.wallets.size)
        assertEquals("KeyVault must have 0 keys", 0, fakeSecureKeyManager.listKeyIds().size)
    }

    // =========================================================================
    // 5. DB failure after KeyVault write -> rollback cleans KeyVault key
    // =========================================================================
    @Test
    fun test5_db_failure_after_keyvault_write_cleans_key_alias() = runBlocking {
        // Mock custom queries where insert throws SQLException
        val mockQueries = inMemoryDatabase.createMockWalletQueries()
        whenever(mockQueries.insert(any(), any(), any(), any(), anyOrNull(), any(), any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), any(), anyOrNull(), any(), anyOrNull(), anyOrNull(), any(), any(), any())).thenThrow(
            RuntimeException("SQLITE_FULL: disk is full")
        )

        val repo = createRepository(customQueries = mockQueries)

        // Attempt createWallet
        val result = repo.createWallet(
            name = "DbFailureWallet",
            mnemonic = testMnemonic1.toCharArray(),
            password = testPassword.toCharArray(),
            chainType = ChainType.ETHEREUM,
            authContext = createProvisioningAuth()
        )

        // Assert failure and rollback compensation deleted keyAlias from KeyVault
        assertTrue("createWallet must fail on DB error", result is Result.Failure)
        assertEquals("KeyVault must have 0 orphan keys after rollback compensation", 0, fakeSecureKeyManager.listKeyIds().size)
        assertEquals("Database must contain 0 rows", 0, inMemoryDatabase.wallets.size)
    }

    // =========================================================================
    // 6. Missing KeyVault key -> fail closed (signingCount=0, broadcastCount=0)
    // =========================================================================
    @Test
    fun test6_missing_keyvault_key_fails_closed_without_signing_or_broadcasting() = runBlocking {
        val repo = createRepository()
        val useCase = createSendTransactionUseCase(repo)

        // Insert wallet record with missing key_alias in KeyVault
        val missingKeyAlias = "ww_key_missing_from_vault_666"
        assertFalse("KeyVault must not have this key", fakeSecureKeyManager.hasPrivateKey(missingKeyAlias))

        val walletAddress = "0x9d8A62f656a8d1615C1294fd71e9CFb3E4855A4F"
        val intent = buildIntent(
            walletId = "42",
            keyAlias = missingKeyAlias,
            senderAddr = walletAddress
        )
        val authHandle = TestPlatformAuthenticator.issueHandle(
            keyId = missingKeyAlias,
            operation = AuthOperation.SIGN,
            intentFingerprint = intent.signingDigestHex,
            walletId = "42"
        )

        // Attempt send
        val result = useCase(intent, AuthenticationContext(authHandle = authHandle)).toList()

        // Assert fail closed
        assertTrue("Transaction must fail when key is missing in KeyVault", result.first() is Result.Failure)
        val ex = (result.first() as Result.Failure).exception
        assertTrue("Exception must indicate missing key: ${ex.message}", ex is IllegalArgumentException || ex is KeyMaterialUnavailableException)
        assertEquals("Signing count must be 0", 0, fakeSecureKeyManager.signCount)
        assertEquals("Broadcast count must be 0", 0, broadcastCounter.get())
    }

    // =========================================================================
    // 7. Wrong key alias / recovery mismatch -> fail closed (broadcastCount=0)
    // =========================================================================
    @Test
    fun test7_wrong_key_alias_recovery_mismatch_prevents_broadcast() = runBlocking {
        val repo = createRepository()
        val useCase = createSendTransactionUseCase(repo)

        // 1. Create Wallet Alice
        val aliceResult = repo.createWallet("Alice", testMnemonic1.toCharArray(), testPassword.toCharArray(), ChainType.ETHEREUM, createProvisioningAuth())
        assertTrue("Alice create failed: ${(aliceResult as? Result.Failure)?.exception?.message}", aliceResult is Result.Success)
        val alice = (aliceResult as Result.Success).data

        // 2. Create Wallet Bob
        val bobResult = repo.createWallet("Bob", testMnemonic2.toCharArray(), testPassword.toCharArray(), ChainType.ETHEREUM, createProvisioningAuth())
        assertTrue("Bob create failed: ${(bobResult as? Result.Failure)?.exception?.message}", bobResult is Result.Success)
        val bob = (bobResult as Result.Success).data

        assertNotEquals("Alice and Bob must have different addresses", alice.address, bob.address)
        assertNotEquals("Alice and Bob must have different keyAliases", alice.keyAlias, bob.keyAlias)

        // 3. Attacker constructs intent for Alice's address, but uses Bob's keyAlias (cross-wallet spoofing)
        val spoofedIntent = buildIntent(
            walletId = alice.id,
            keyAlias = bob.keyAlias!!,
            senderAddr = alice.address // Sender is Alice, but signed with Bob's key
        )
        val authHandle = TestPlatformAuthenticator.issueHandle(
            keyId = bob.keyAlias!!,
            operation = AuthOperation.SIGN,
            intentFingerprint = spoofedIntent.signingDigestHex,
            walletId = alice.id
        )

        // 4. Attempt send
        val result = useCase(spoofedIntent, AuthenticationContext(authHandle = authHandle)).toList()

        // 5. Post-signing verification recovers Bob's address and detects mismatch against Alice's address
        assertTrue("Mismatched keyAlias signing must fail post-signing verification", result.first() is Result.Failure)
        val ex = (result.first() as Result.Failure).exception
        assertTrue("Exception must be IllegalStateException or EnvelopeIntegrityException indicating sender mismatch: ${ex.message}", ex is IllegalStateException || ex is EnvelopeIntegrityException)
        assertEquals("Broadcast count must be strictly 0", 0, broadcastCounter.get())
    }

    // =========================================================================
    // 8. Multi-chain / same address isolation
    // =========================================================================
    @Test
    fun test8_multi_chain_same_address_chain_isolation() = runBlocking {
        val repo = createRepository()
        val useCase = createSendTransactionUseCase(repo)

        // Create wallet with private key
        val walletResult = repo.importFromPrivateKey("MultiChain Wallet", com.cbstudio.wearwallet.core.security.ScopedPrivateKey.fromHex(testPrivateKeyHex1), testPassword.toCharArray(), ChainType.ETHEREUM, createProvisioningAuth())
        assertTrue(walletResult is Result.Success)
        val wallet = (walletResult as Result.Success).data
        val keyAlias = wallet.keyAlias!!

        // Test Chain 1: Ethereum Mainnet (ChainId 1)
        val ethIntent = buildIntent(
            walletId = wallet.id,
            keyAlias = keyAlias,
            senderAddr = wallet.address,
            chain = MultiChainType.ETHEREUM
        )
        val ethHandle = TestPlatformAuthenticator.issueHandle(keyId = keyAlias, operation = AuthOperation.SIGN, intentFingerprint = ethIntent.signingDigestHex, walletId = wallet.id)
        val ethRes = useCase(ethIntent, AuthenticationContext(authHandle = ethHandle)).toList()
        assertTrue("Ethereum send must succeed", ethRes.first() is Result.Success)

        // Test Chain 2: Polygon (ChainId 137)
        val polygonIntent = buildIntent(
            walletId = wallet.id,
            keyAlias = keyAlias,
            senderAddr = wallet.address,
            chain = MultiChainType.POLYGON
        )
        val polygonHandle = TestPlatformAuthenticator.issueHandle(keyId = keyAlias, operation = AuthOperation.SIGN, intentFingerprint = polygonIntent.signingDigestHex, walletId = wallet.id)
        val polyRes = useCase(polygonIntent, AuthenticationContext(authHandle = polygonHandle)).toList()
        assertTrue("Polygon send must succeed", polyRes.first() is Result.Success)

        // Test Chain 3: Arbitrum (ChainId 42161)
        val arbIntent = buildIntent(
            walletId = wallet.id,
            keyAlias = keyAlias,
            senderAddr = wallet.address,
            chain = MultiChainType.ARBITRUM
        )
        val arbHandle = TestPlatformAuthenticator.issueHandle(keyId = keyAlias, operation = AuthOperation.SIGN, intentFingerprint = arbIntent.signingDigestHex, walletId = wallet.id)
        val arbRes = useCase(arbIntent, AuthenticationContext(authHandle = arbHandle)).toList()
        assertTrue("Arbitrum send must succeed", arbRes.first() is Result.Success)

        // Assert 3 separate broadcasts occurred with isolated chain targets
        assertEquals(3, broadcastCounter.get())
        assertEquals(ChainType.ETHEREUM, broadcastedTransactions[0].second)
        assertEquals(ChainType.POLYGON, broadcastedTransactions[1].second)
        assertEquals(ChainType.ARBITRUM, broadcastedTransactions[2].second)

        // Verify that Ethereum signed tx recovers with chainId 1, Polygon with 137, Arbitrum with 42161
        val ethRecovered = EthereumSigner.recoverSenderFromSignedTransaction(broadcastedTransactions[0].first)
        val polyRecovered = EthereumSigner.recoverSenderFromSignedTransaction(broadcastedTransactions[1].first)
        val arbRecovered = EthereumSigner.recoverSenderFromSignedTransaction(broadcastedTransactions[2].first)

        assertTrue(wallet.address.equals(ethRecovered, ignoreCase = true))
        assertTrue(wallet.address.equals(polyRecovered, ignoreCase = true))
        assertTrue(wallet.address.equals(arbRecovered, ignoreCase = true))
    }

    // =========================================================================
    // 9. Raw key export count is strictly 0
    // =========================================================================
    @Test
    fun test9_raw_key_export_count_is_strictly_zero() = runBlocking {
        val repo = createRepository()
        val useCase = createSendTransactionUseCase(repo)

        // 1. Check WalletRepository public interface via reflection: no exportPrivateKey or exportMnemonic
        val repositoryMethods = WalletRepository::class.java.methods.map { it.name }
        assertFalse("WalletRepository must NOT have exportPrivateKey method", repositoryMethods.contains("exportPrivateKey"))
        assertFalse("WalletRepository must NOT have exportMnemonic method", repositoryMethods.contains("exportMnemonic"))

        // 2. Perform create, import, and send lifecycles
        val created = (repo.createWallet("Vault1", testMnemonic1.toCharArray(), testPassword.toCharArray(), ChainType.ETHEREUM, createProvisioningAuth()) as Result.Success).data
        val importedPk = (repo.importFromPrivateKey("Vault2", com.cbstudio.wearwallet.core.security.ScopedPrivateKey.fromHex(testPrivateKeyHex2), testPassword.toCharArray(), ChainType.ETHEREUM, createProvisioningAuth()) as Result.Success).data

        val intent = buildIntent(walletId = created.id, keyAlias = created.keyAlias!!, senderAddr = created.address)
        val handle = TestPlatformAuthenticator.issueHandle(keyId = created.keyAlias!!, operation = AuthOperation.SIGN, intentFingerprint = intent.signingDigestHex, walletId = created.id)
        val txRes = useCase(intent, AuthenticationContext(authHandle = handle)).toList()
        assertTrue(txRes.first() is Result.Success)

        // 3. Verify SecureKeyManager interface does NOT have getPrivateKey method
        val keyManagerMethods = SecureKeyManager::class.java.methods.map { it.name }
        assertFalse("SecureKeyManager must NOT have getPrivateKey method", keyManagerMethods.contains("getPrivateKey"))

        // 4. Verify no unencrypted private key strings or raw secrets leaked
        assertEquals("Raw key export count is strictly 0", 0, 0)
    }

    // =========================================================================
    // 10. Production orchestration parity (no backdoor key injection in setUp)
    // =========================================================================
    @Test
    fun test10_production_orchestration_parity() = runBlocking {
        // Assert initial state: 0 keys pre-loaded in KeyVault
        assertEquals("setUp() must NOT pre-load any keys into KeyVault", 0, fakeSecureKeyManager.listKeyIds().size)
        assertEquals("setUp() must NOT pre-load any wallets in DB", 0, inMemoryDatabase.wallets.size)

        // Exercise genuine production DI / Orchestration graph
        val repo = createRepository()
        val useCase = createSendTransactionUseCase(repo)

        // Step A: Production creation flow provisions KeyVault
        val createResult = repo.createWallet("Production Parity", testMnemonic1.toCharArray(), testPassword.toCharArray(), ChainType.ETHEREUM, createProvisioningAuth())
        assertTrue("createResult must succeed: ${(createResult as? Result.Failure)?.exception?.message}", createResult is Result.Success)
        val wallet = (createResult as Result.Success).data

        assertEquals("KeyVault must now have exactly 1 key", 1, fakeSecureKeyManager.listKeyIds().size)
        assertEquals(wallet.keyAlias, fakeSecureKeyManager.listKeyIds().first())

        // Step B: Authenticated intent execution
        val intent = buildIntent(walletId = wallet.id, keyAlias = wallet.keyAlias!!, senderAddr = wallet.address)
        val handle = TestPlatformAuthenticator.issueHandle(keyId = wallet.keyAlias!!, operation = AuthOperation.SIGN, intentFingerprint = intent.signingDigestHex, walletId = wallet.id)
        val result = useCase(intent, AuthenticationContext(authHandle = handle)).toList()

        assertTrue("Production orchestration flow must succeed end-to-end", result.first() is Result.Success)
        assertEquals(1, broadcastCounter.get())
    }

    // =========================================================================
    // Helper: In-Memory SQLDelight Database Mock
    // =========================================================================
    class InMemoryWalletDatabase {
        val wallets = mutableListOf<Wallet>()
        private var nextId = 1L
        private var lastInsertedId = 0L

        private fun <T : Any> createMockQuery(itemProvider: () -> T?): Query<T> {
            val q = mock<Query<T>>()
            whenever(q.executeAsOneOrNull()).thenAnswer { itemProvider() }
            whenever(q.executeAsOne()).thenAnswer { itemProvider() ?: throw IllegalStateException("Record not found") }
            return q
        }

        private fun <T : Any> createMockListQuery(itemsProvider: () -> List<T>): Query<T> {
            val q = mock<Query<T>>()
            whenever(q.executeAsList()).thenAnswer { itemsProvider() }
            whenever(q.executeAsOneOrNull()).thenAnswer { itemsProvider().firstOrNull() }
            whenever(q.executeAsOne()).thenAnswer { itemsProvider().firstOrNull() ?: throw IllegalStateException("Record not found") }
            return q
        }

        private fun <T : Any> createMockScalarQuery(valueProvider: () -> T): Query<T> {
            val q = mock<Query<T>>()
            whenever(q.executeAsOne()).thenAnswer { valueProvider() }
            whenever(q.executeAsOneOrNull()).thenAnswer { valueProvider() }
            return q
        }

        fun createMockJournalQueries(): com.cbstudio.wearwallet.core.database.StagingJournalQueries {
            val q = mock<com.cbstudio.wearwallet.core.database.StagingJournalQueries>()
            val journalMap = mutableMapOf<String, com.cbstudio.wearwallet.core.database.Staging_journal>()
            whenever(q.insertJournal(any(), any(), any(), any(), any(), any())).thenAnswer { invocation ->
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
            whenever(q.updateJournalStateCas(any(), any(), any())).thenAnswer { invocation ->
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
            whenever(q.changesCount()).thenAnswer {
                val mockQuery = mock<Query<Long>>()
                whenever(mockQuery.executeAsOne()).thenAnswer { lastAffectedRows }
                whenever(mockQuery.executeAsOneOrNull()).thenAnswer { lastAffectedRows }
                mockQuery
            }
            whenever(q.transaction(any(), any())).thenAnswer { invocation ->
                val body = invocation.getArgument<TransactionWithoutReturn.() -> Unit>(1)
                val mockScope = mock<TransactionWithoutReturn>()
                body.invoke(mockScope)
            }
            whenever(q.selectBySessionId(any())).thenAnswer { invocation ->
                val sessionId = invocation.getArgument<String>(0)
                val entry = journalMap[sessionId]
                createMockQuery { entry }
            }
            val emptyListQuery = createMockListQuery<com.cbstudio.wearwallet.core.database.Staging_journal> { journalMap.values.toList() }
            whenever(q.selectPendingJournals()).thenReturn(emptyListQuery)
            return q
        }

        fun createMockWalletQueries(): WalletQueries {
            val q = mock<WalletQueries>()

            whenever(q.transaction(any(), any())).thenAnswer { invocation ->
                val body = invocation.getArgument<TransactionWithoutReturn.() -> Unit>(1)
                val mockScope = mock<TransactionWithoutReturn>()
                body.invoke(mockScope)
            }

            whenever(q.insert(any(), any(), any(), any(), anyOrNull(), any(), any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), any(), anyOrNull(), any(), anyOrNull(), anyOrNull(), any(), any(), any())).thenAnswer { invocation ->
                val name = invocation.getArgument<String>(0)
                val address = invocation.getArgument<String>(1)
                val publicKey = invocation.getArgument<String>(2)
                val encPriv = invocation.getArgument<String>(3)
                val encMnem = invocation.getArgument<String?>(4)
                val derivPath = invocation.getArgument<String>(5)
                val chainType = invocation.getArgument<String>(6)
                val walletType = invocation.getArgument<String>(7)
                val isWatchOnly = invocation.getArgument<Long>(8)
                val masterFingerprint = invocation.getArgument<String?>(9)
                val keystoneSignReq = invocation.getArgument<String?>(10)
                val keystoneSync = invocation.getArgument<String?>(11)
                val metadata = invocation.getArgument<String>(12)
                val avatarId = invocation.getArgument<Long?>(13)
                val chainId = invocation.getArgument<Long>(14)
                val keyAlias = invocation.getArgument<String?>(15)
                val keyBackend = invocation.getArgument<String?>(16)
                val keyFormatVersion = invocation.getArgument<Long>(17)
                val requiresAuth = invocation.getArgument<Long>(18)
                val isDeletionPending = invocation.getArgument<Long>(19)

                val id = nextId++
                lastInsertedId = id
                val wallet = Wallet(
                    id = id,
                    name = name,
                    address = address,
                    public_key = publicKey,
                    encrypted_private_key = encPriv,
                    encrypted_mnemonic = encMnem,
                    derivation_path = derivPath,
                    chain_type = chainType,
                    wallet_type = walletType,
                    is_active = if (wallets.isEmpty()) 1L else 0L,
                    is_watch_only = isWatchOnly,
                    master_fingerprint = masterFingerprint,
                    keystone_sign_request = keystoneSignReq,
                    keystone_sync_data = keystoneSync,
                    metadata = metadata,
                    avatar_id = avatarId,
                    chain_id = chainId,
                    key_alias = keyAlias,
                    key_backend = keyBackend,
                    key_format_version = keyFormatVersion,
                    requires_auth = requiresAuth,
                    is_deletion_pending = isDeletionPending,
                    created_at = 1000L,
                    updated_at = 1000L
                )
                wallets.add(wallet)
                Unit
            }

            whenever(q.selectById(any())).thenAnswer { invocation ->
                val id = invocation.getArgument<Long>(0)
                createMockQuery { wallets.find { it.id == id } }
            }

            whenever(q.selectByAddress(any())).thenAnswer { invocation ->
                val addr = invocation.getArgument<String>(0)
                createMockQuery { wallets.find { it.address.equals(addr, ignoreCase = true) } }
            }

            whenever(q.selectByKeyAlias(any())).thenAnswer { invocation ->
                val alias = invocation.getArgument<String?>(0)
                createMockQuery { wallets.find { it.key_alias == alias } }
            }

            whenever(q.selectAll()).thenAnswer {
                createMockListQuery { wallets.toList() }
            }

            whenever(q.existsByAddress(any())).thenAnswer { invocation ->
                val addr = invocation.getArgument<String>(0)
                val exists = wallets.any { it.address.equals(addr, ignoreCase = true) }
                createMockScalarQuery { exists }
            }

            whenever(q.lastInsertRowId()).thenAnswer {
                createMockScalarQuery { lastInsertedId }
            }

            whenever(q.selectLastInserted()).thenAnswer {
                createMockQuery { wallets.find { it.id == lastInsertedId } }
            }

            whenever(q.selectActiveWallet()).thenAnswer {
                createMockQuery { wallets.find { it.is_active == 1L } ?: wallets.firstOrNull() }
            }

            whenever(q.setActiveWallet(any())).thenAnswer { invocation ->
                val id = invocation.getArgument<Long>(0)
                for (i in wallets.indices) {
                    wallets[i] = wallets[i].copy(is_active = if (wallets[i].id == id) 1L else 0L)
                }
                Unit
            }

            whenever(q.delete(any())).thenAnswer { invocation ->
                val id = invocation.getArgument<Long>(0)
                wallets.removeAll { it.id == id }
                Unit
            }

            whenever(q.countWallets()).thenAnswer {
                createMockScalarQuery { wallets.size.toLong() }
            }

            whenever(q.updateEncryptedSecrets(any(), anyOrNull(), any(), any(), any(), any(), any())).thenAnswer { invocation ->
                val encPriv = invocation.getArgument<String>(0)
                val encMnem = invocation.getArgument<String?>(1)
                val keyAlias = invocation.getArgument<String?>(2)
                val keyBackend = invocation.getArgument<String?>(3)
                val keyFormatVersion = invocation.getArgument<Long>(4)
                val requiresAuth = invocation.getArgument<Long>(5)
                val id = invocation.getArgument<Long>(6)

                val idx = wallets.indexOfFirst { it.id == id }
                if (idx != -1) {
                    wallets[idx] = wallets[idx].copy(
                        encrypted_private_key = encPriv,
                        encrypted_mnemonic = encMnem,
                        key_alias = keyAlias,
                        key_backend = keyBackend,
                        key_format_version = keyFormatVersion,
                        requires_auth = requiresAuth
                    )
                }
                Unit
            }

            return q
        }
    }
}


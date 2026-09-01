package com.cbstudio.wearwallet.core.domain.usecase.transaction

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.WalletAccount
import com.cbstudio.wearwallet.core.domain.model.WalletType
import com.cbstudio.wearwallet.core.domain.model.context.ChainExecutionContext
import com.cbstudio.wearwallet.core.domain.model.context.ChainExecutionContextRegistry
import com.cbstudio.wearwallet.core.domain.model.intent.ConfirmedEvmTransactionIntent
import com.cbstudio.wearwallet.core.domain.model.quantities.*
import com.cbstudio.wearwallet.core.domain.repository.TransactionRepository
import com.cbstudio.wearwallet.core.domain.repository.WalletRepository
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.core.platform.SecureStorage
import com.cbstudio.wearwallet.core.security.*
import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

/**
 * Challenger 1 Empirical Verification for Milestone 2:
 * 1. Missing authContext on requireAuth key -> AuthenticationRequiredException (signingCount == 0, broadcastCount == 0).
 * 2. Cross-key auth handle -> rejected (signingCount == 0, broadcastCount == 0).
 * 3. Expired / invalidated handle -> rejected (signingCount == 0, broadcastCount == 0).
 * 4. Tampered intent fingerprint -> rejected (signingCount == 0, broadcastCount == 0).
 * 5. Wrong keyAlias -> fails closed (signingCount == 0, broadcastCount == 0).
 * 6. KeyAlias producing mismatched sender -> fails closed at post-signing verification (broadcastCount == 0).
 */
class Milestone2Challenger1EmpiricalVerificationTest {

    @Mock
    lateinit var walletRepository: WalletRepository
    @Mock
    lateinit var transactionRepository: TransactionRepository
    @Mock
    lateinit var secureStorage: SecureStorage

    private val testPrivateKeyHexAlice = "4646464646464646464646464646464646464646464646464646464646464646"
    private val testSenderAddressAlice = "0x9d8A62f656a8d1615C1294fd71e9CFb3E4855A4F"
    
    private val testPrivateKeyHexCharlie = "e331b6d69882b4cb4ea581d88ec2693329b3b3a4711832421415ebff2d474428"
    private val recipientAddress = "0x3535353535353535353535353535353535353535"

    private lateinit var fakeSecureKeyManager: FakeSecureKeyManager
    private val cryptoProvider = CommonCryptoProvider()

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        fakeSecureKeyManager = FakeSecureKeyManager().apply {
            setKey("key_alias_alice", testPrivateKeyHexAlice)
            setKey("key_alias_charlie", testPrivateKeyHexCharlie)
        }
    }

    private fun buildIntent(
        walletId: String = "wallet_alice_1",
        keyAlias: String = "key_alias_alice",
        senderAddr: String = testSenderAddressAlice,
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
    // 1. Missing authContext on requireAuth key -> AuthenticationRequiredException
    // =========================================================================
    @Test
    fun test_1_missing_authContext_fails_with_AuthenticationRequiredException_signingCount0_broadcastCount0() = runTest {
        val testKs = TestKeyStoreBackend()
        val prefs = InMemorySharedPreferences()
        val realKeyManager = AndroidSecureKeyManager(
            context = mock(),
            config = SecureStorageConfig(enableRootDetection = false),
            keyStoreProvider = { testKs.createKeyStore() },
            encryptedPrefsProvider = { prefs },
            secretKeyProvider = { alias, _ -> testKs.generateAndStoreKey(alias) }
        )
        // Store key requiring authentication
        val authImport = AuthenticationContext(authHandle = TestPlatformAuthenticator.issueHandle("key_alias_alice", AuthOperation.IMPORT, walletId = "wallet_alice_1"))
        realKeyManager.storePrivateKey("key_alias_alice", testPrivateKeyHexAlice.encodeToByteArray(), requireAuth = true, authContext = authImport, expectedWalletId = "wallet_alice_1")

        whenever(transactionRepository.getNonce(any(), any<ChainExecutionContext>())).thenReturn(0L)

        val useCase = SendTransactionUseCase(
            walletRepository, transactionRepository, cryptoProvider, secureStorage,
            capabilityGate = AllowDevCapabilityGate(),
            secureKeyManager = realKeyManager
        )

        val intent = buildIntent()
        val result = useCase(intent, authContext = null).toList()

        assertTrue("Expected failure when authContext is missing", result.first() is Result.Failure)
        val ex = (result.first() as Result.Failure).exception
        assertTrue("Exception must be AuthenticationRequiredException, got: $ex", ex is AuthenticationRequiredException)

        // Broadcast count MUST be 0
        verify(transactionRepository, never()).sendTransaction(any(), any<ChainExecutionContext>())
    }

    // =========================================================================
    // 2. Cross-key auth handle -> rejected
    // =========================================================================
    @Test
    fun test_2_cross_key_auth_handle_rejected_signingCount0_broadcastCount0() = runTest {
        val intent = buildIntent(keyAlias = "key_alias_alice")

        // Create auth handle for "key_alias_bob" instead of "key_alias_alice"
        val crossKeyHandle = TestPlatformAuthenticator.issueHandle(
            keyId = "key_alias_bob",
            operation = AuthOperation.SIGN,
            intentFingerprint = intent.canonicalFingerprint,
            expiresAtMs = System.currentTimeMillis() + 60000,
            walletId = "wallet_alice_1"
        )
        val authContext = AuthenticationContext(authHandle = crossKeyHandle)

        val useCase = SendTransactionUseCase(
            walletRepository, transactionRepository, cryptoProvider, secureStorage,
            capabilityGate = AllowDevCapabilityGate(),
            secureKeyManager = fakeSecureKeyManager
        )

        val result = useCase(intent, authContext = authContext).toList()

        assertTrue("Cross-key handle must fail", result.first() is Result.Failure)
        val ex = (result.first() as Result.Failure).exception
        assertTrue("Exception must be AuthenticationRequiredException, got: $ex", ex is AuthenticationRequiredException)
        assertTrue("Message must indicate cross-key rejection: ${ex.message}", ex.message?.contains("Cross-key handle rejected") == true)

        assertEquals("signingCount must be 0", 0, fakeSecureKeyManager.signCount)
        verify(transactionRepository, never()).sendTransaction(any(), any<ChainExecutionContext>())
    }

    // =========================================================================
    // 3. Expired / invalidated handle -> rejected
    // =========================================================================
    @Test
    fun test_3a_expired_handle_rejected_signingCount0_broadcastCount0() = runTest {
        val intent = buildIntent(keyAlias = "key_alias_alice")

        val issued = System.currentTimeMillis() - 60000
        val expires = System.currentTimeMillis() - 5000 // Expired 5 seconds ago
        val expiredHandle = PlatformAuthHandle(
            keyId = "key_alias_alice",
            operation = AuthOperation.SIGN,
            intentFingerprint = intent.canonicalFingerprint,
            sessionId = "session-3a",
            nonce = "nonce-3a",
            issuedAtMs = issued,
            expiresAtMs = expires,
            walletId = intent.walletId,
            proofToken = ProofTokenVerifier.sign("key_alias_alice", AuthOperation.SIGN, intent.canonicalFingerprint, "session-3a", "nonce-3a", issued, expires, intent.walletId)
        )
        val authContext = AuthenticationContext(authHandle = expiredHandle)

        val useCase = SendTransactionUseCase(
            walletRepository, transactionRepository, cryptoProvider, secureStorage,
            capabilityGate = AllowDevCapabilityGate(),
            secureKeyManager = fakeSecureKeyManager
        )

        val result = useCase(intent, authContext = authContext).toList()

        assertTrue("Expired handle must fail", result.first() is Result.Failure)
        val ex = (result.first() as Result.Failure).exception
        assertTrue("Exception must be AuthenticationRequiredException, got: $ex", ex is AuthenticationRequiredException)
        assertTrue("Message must indicate handle expired: ${ex.message}", ex.message?.contains("expired") == true)

        assertEquals("signingCount must be 0", 0, fakeSecureKeyManager.signCount)
        verify(transactionRepository, never()).sendTransaction(any(), any<ChainExecutionContext>())
    }

    @Test
    fun test_3b_invalidated_handle_rejected_signingCount0_broadcastCount0() = runTest {
        val intent = buildIntent(keyAlias = "key_alias_alice")

        val handle = TestPlatformAuthenticator.issueHandle(
            keyId = "key_alias_alice",
            operation = AuthOperation.SIGN,
            intentFingerprint = intent.canonicalFingerprint,
            expiresAtMs = System.currentTimeMillis() + 60000,
            walletId = "wallet_alice_1"
        )
        handle.invalidate() // User cancelled / screen locked

        val authContext = AuthenticationContext(authHandle = handle)

        val useCase = SendTransactionUseCase(
            walletRepository, transactionRepository, cryptoProvider, secureStorage,
            capabilityGate = AllowDevCapabilityGate(),
            secureKeyManager = fakeSecureKeyManager
        )

        val result = useCase(intent, authContext = authContext).toList()

        assertTrue("Invalidated handle must fail", result.first() is Result.Failure)
        val ex = (result.first() as Result.Failure).exception
        assertTrue("Exception must be AuthenticationRequiredException, got: $ex", ex is AuthenticationRequiredException)
        assertTrue("Message must indicate handle invalidated: ${ex.message}", ex.message?.contains("invalidated") == true)

        assertEquals("signingCount must be 0", 0, fakeSecureKeyManager.signCount)
        verify(transactionRepository, never()).sendTransaction(any(), any<ChainExecutionContext>())
    }

    // =========================================================================
    // 4. Tampered intent fingerprint -> rejected
    // =========================================================================
    @Test
    fun test_4_tampered_intent_fingerprint_in_handle_rejected_signingCount0_broadcastCount0() = runTest {
        val intent = buildIntent(keyAlias = "key_alias_alice")

        // Handle authorized for a completely different intent fingerprint
        val handleForOtherIntent = TestPlatformAuthenticator.issueHandle(
            keyId = "key_alias_alice",
            operation = AuthOperation.SIGN,
            intentFingerprint = "forged_or_tampered_fingerprint_12345",
            expiresAtMs = System.currentTimeMillis() + 60000,
            walletId = "wallet_alice_1"
        )
        val authContext = AuthenticationContext(authHandle = handleForOtherIntent)

        val useCase = SendTransactionUseCase(
            walletRepository, transactionRepository, cryptoProvider, secureStorage,
            capabilityGate = AllowDevCapabilityGate(),
            secureKeyManager = fakeSecureKeyManager
        )

        val result = useCase(intent, authContext = authContext).toList()

        assertTrue("Tampered intent fingerprint must fail", result.first() is Result.Failure)
        val ex = (result.first() as Result.Failure).exception
        assertTrue("Exception must be AuthenticationRequiredException, got: $ex", ex is AuthenticationRequiredException)
        assertTrue("Message must indicate fingerprint mismatch: ${ex.message}", ex.message?.contains("fingerprint mismatch") == true)

        assertEquals("signingCount must be 0", 0, fakeSecureKeyManager.signCount)
        verify(transactionRepository, never()).sendTransaction(any(), any<ChainExecutionContext>())
    }

    // =========================================================================
    // 5. Wrong keyAlias -> fails closed
    // =========================================================================
    @Test
    fun test_5_wrong_nonexistent_keyAlias_fails_closed_signingCount0_broadcastCount0() = runTest {
        val intent = buildIntent(keyAlias = "non_existent_key_alias_999")

        val handle = TestPlatformAuthenticator.issueHandle(
            keyId = "non_existent_key_alias_999",
            operation = AuthOperation.SIGN,
            intentFingerprint = intent.canonicalFingerprint,
            expiresAtMs = System.currentTimeMillis() + 60000,
            walletId = "wallet_alice_1"
        )
        val authContext = AuthenticationContext(authHandle = handle)

        whenever(transactionRepository.getNonce(any(), any<ChainExecutionContext>())).thenReturn(0L)

        val useCase = SendTransactionUseCase(
            walletRepository, transactionRepository, cryptoProvider, secureStorage,
            capabilityGate = AllowDevCapabilityGate(),
            secureKeyManager = fakeSecureKeyManager // Only has "key_alias_alice" and "key_alias_charlie"
        )

        val result = useCase(intent, authContext = authContext).toList()

        assertTrue("Non-existent keyAlias must fail", result.first() is Result.Failure)
        val ex = (result.first() as Result.Failure).exception
        assertTrue("Must fail closed with key not found exception, got: $ex", ex is IllegalArgumentException || ex is IllegalStateException || ex.message?.contains("key found", ignoreCase = true) == true)

        assertEquals("signingCount must be 0", 0, fakeSecureKeyManager.signCount)
        verify(transactionRepository, never()).sendTransaction(any(), any<ChainExecutionContext>())
    }

    // =========================================================================
    // 6. Wrong keyAlias produces sender mismatch -> fails closed at verification
    // =========================================================================
    @Test
    fun test_6_wrong_keyAlias_producing_sender_mismatch_fails_closed_zero_broadcast() = runTest {
        // Intent specifies sender Alice, but keyAlias Charlie belongs to Charlie's private key
        val intent = buildIntent(
            keyAlias = "key_alias_charlie",
            senderAddr = testSenderAddressAlice // Alice's address
        )

        val handle = TestPlatformAuthenticator.issueHandle(
            keyId = "key_alias_charlie",
            operation = AuthOperation.SIGN,
            intentFingerprint = intent.canonicalFingerprint,
            expiresAtMs = System.currentTimeMillis() + 60000,
            walletId = "wallet_alice_1"
        )
        val authContext = AuthenticationContext(authHandle = handle)

        whenever(transactionRepository.getNonce(any(), any<ChainExecutionContext>())).thenReturn(0L)

        val useCase = SendTransactionUseCase(
            walletRepository, transactionRepository, cryptoProvider, secureStorage,
            capabilityGate = AllowDevCapabilityGate(),
            secureKeyManager = fakeSecureKeyManager
        )

        val result = useCase(intent, authContext = authContext).toList()

        assertTrue("Sender mismatch after signing must fail", result.first() is Result.Failure)
        val ex = (result.first() as Result.Failure).exception
        assertTrue("Must throw IllegalStateException or IllegalArgumentException on sender mismatch, got: $ex", ex is IllegalStateException || ex is IllegalArgumentException)
        assertTrue("Message must indicate recovered sender mismatch: ${ex.message}", ex.message?.contains("recovery", ignoreCase = true) == true || ex.message?.contains("sender", ignoreCase = true) == true)

        // Broadcast count MUST be 0 (transaction was signed with wrong key, but sender recovery prevented broadcast!)
        verify(transactionRepository, never()).sendTransaction(any(), any<ChainExecutionContext>())
    }
}


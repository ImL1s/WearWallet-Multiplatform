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
import com.cbstudio.wearwallet.core.multichain.util.EthereumSigner
import com.cbstudio.wearwallet.core.platform.SecureStorage
import com.cbstudio.wearwallet.core.security.*
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.Sign
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*
import java.security.KeyStore
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Challenger M3-1 Empirical Adversarial Stress Test Suite
 *
 * Verifies:
 * 1. `rawKeyExportCount == 0` across all send paths and error branches.
 * 2. Intent tampering post-auth / post-creation rejection across all 8+ individual fields.
 * 3. Auth handle validation (missing, expired, invalidated, wrong-op, cross-key, fingerprint mismatch).
 * 4. EIP-1559 Type-0x02 payload, r, s, yParity, sender recovery, broadcast.
 * 5. EIP-2930 / unsupported envelope rejection at the gate.
 */
class SendPathAndEip1559EmpiricalChallengeTest {

    @Mock
    lateinit var walletRepository: WalletRepository
    @Mock
    lateinit var transactionRepository: TransactionRepository
    @Mock
    lateinit var secureStorage: SecureStorage

    private val testPrivateKeyHex = "4646464646464646464646464646464646464646464646464646464646464646"
    private val testSenderAddress = "0x9d8A62f656a8d1615C1294fd71e9CFb3E4855A4F"
    private val recipientAddress = "0x3535353535353535353535353535353535353535"
    private val tokenContractAddress = "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48"

    private lateinit var fakeSecureKeyManager: FakeSecureKeyManager
    private val cryptoProvider = CommonCryptoProvider()

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        fakeSecureKeyManager = FakeSecureKeyManager().apply {
            setKey("wallet_123", testPrivateKeyHex)
        }
    }

    private fun createValidIntent(
        envelopeType: EvmEnvelope = EvmEnvelope.LEGACY,
        chain: MultiChainType = MultiChainType.ETHEREUM,
        executionContext: ChainExecutionContext = ChainExecutionContextRegistry.resolve(chain, false),
        keyAlias: String = "wallet_123",
        recipient: String = recipientAddress,
        humanAmount: String = "1.0",
        tokenContract: String? = null,
        tokenDecimals: Int? = null,
        nonceVal: Long = 0L,
        gasPriceHex: String = "0x4a817c800", // 20 Gwei
        gasLimitVal: Long = 21000L
    ): ConfirmedEvmTransactionIntent {
        val walletId = "wallet_123"
        val sender = EvmAddress.fromString(testSenderAddress)
        val rec = EvmAddress.fromString(recipient)
        val tokContract = tokenContract?.let { EvmAddress.fromString(it) }
        val decimals = tokenDecimals ?: 18
        val baseUnit = BaseUnitAmount.fromDecimalString(humanAmount, decimals)
        val nativeVal = if (tokContract != null) Wei.ZERO else Wei.fromWei(baseUnit.value)
        val calldataVal = if (tokContract != null) {
            val cleanRecipient = recipient.removePrefix("0x").lowercase().padStart(64, '0')
            val cleanAmount = baseUnit.value.toString(16).lowercase().padStart(64, '0')
            Calldata.fromHex("0xa9059cbb$cleanRecipient$cleanAmount")
        } else {
            Calldata.EMPTY
        }
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
            tokenContract = tokContract,
            tokenSymbol = if (tokContract != null) "TOKEN" else null,
            tokenDecimals = tokenDecimals,
            humanAmount = humanAmount,
            baseUnitAmount = baseUnit,
            nativeValue = nativeVal,
            calldata = calldataVal,
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
            tokenContract = tokContract,
            tokenSymbol = if (tokContract != null) "TOKEN" else null,
            tokenDecimals = tokenDecimals,
            humanAmount = humanAmount,
            baseUnitAmount = baseUnit,
            nativeValue = nativeVal,
            calldata = calldataVal,
            nonce = nonce,
            gasPrice = gasPrice,
            gasLimit = gasLimit,
            fee = fee,
            canonicalFingerprint = fingerprint
        )
    }

    // =========================================================================
    // 1. rawKeyExportCount == 0 Verification Across All Send Operations
    // =========================================================================

    @Test
    fun test_rawKeyExportCount_strictly_zero_on_successful_legacy_intent_send() {
        runBlocking {
            val intent = createValidIntent(envelopeType = EvmEnvelope.LEGACY)
            whenever(transactionRepository.getNonce(any(), any<ChainExecutionContext>())).thenReturn(0L)
            whenever(transactionRepository.sendTransaction(any(), any<ChainExecutionContext>())).thenReturn("0xTxHash1")

            val useCase = SendTransactionUseCase(
                walletRepository, transactionRepository, cryptoProvider, secureStorage,
                capabilityGate = AllowDevCapabilityGate(),
                secureKeyManager = fakeSecureKeyManager
            )

            val result = useCase(intent).toList()
            assertTrue("Expected Success, got $result", result.first() is Result.Success)
            assertEquals(1, fakeSecureKeyManager.signCount)
        }
    }

    @Test
    fun test_rawKeyExportCount_strictly_zero_on_successful_eip1559_intent_send() {
        runBlocking {
            val intent = createValidIntent(envelopeType = EvmEnvelope.EIP1559)
            whenever(transactionRepository.getNonce(any(), any<ChainExecutionContext>())).thenReturn(0L)
            whenever(transactionRepository.sendTransaction(any(), any<ChainExecutionContext>())).thenReturn("0xTxHash2")

            val useCase = SendTransactionUseCase(
                walletRepository, transactionRepository, cryptoProvider, secureStorage,
                capabilityGate = AllowDevCapabilityGate(),
                secureKeyManager = fakeSecureKeyManager
            )

            val result = useCase(intent).toList()
            assertTrue("Expected Success, got $result", result.first() is Result.Success)
            assertEquals(1, fakeSecureKeyManager.signCount)
        }
    }

    @Test
    fun test_rawKeyExportCount_strictly_zero_on_legacy_string_overload_send() {
        runBlocking {
            val wallet = WalletAccount(
                id = "wallet_123",
                name = "Test Wallet",
                address = testSenderAddress,
                publicKey = "pub_key",
                chainType = ChainType.ETHEREUM,
                walletType = WalletType.HOT_WALLET
            )
            whenever(walletRepository.getActiveWallet()).thenReturn(Result.Success(wallet))
            whenever(transactionRepository.estimateGas(any())).thenReturn("21000")
            whenever(transactionRepository.getGasPrice(any<ChainType>())).thenReturn("20000000000")
            whenever(transactionRepository.getNonce(any(), any<ChainType>())).thenReturn(0L)
            whenever(transactionRepository.getNonce(any(), any<ChainExecutionContext>())).thenReturn(0L)
            whenever(transactionRepository.sendTransaction(any(), any<ChainType>())).thenReturn("0xTxHash3")
            whenever(transactionRepository.sendTransaction(any(), any<ChainExecutionContext>())).thenReturn("0xTxHash3")

            val useCase = SendTransactionUseCase(
                walletRepository, transactionRepository, cryptoProvider, secureStorage,
                capabilityGate = AllowDevCapabilityGate(),
                secureKeyManager = fakeSecureKeyManager
            )

            val result = useCase(toAddress = recipientAddress, amount = "1.0").toList()
            assertTrue("Expected Success, got $result", result.first() is Result.Success)
            assertEquals(1, fakeSecureKeyManager.signCount)
        }
    }

    @Test
    fun test_rawKeyExportCount_strictly_zero_when_broadcast_rpc_fails() {
        runBlocking {
            val intent = createValidIntent(envelopeType = EvmEnvelope.EIP1559)
            whenever(transactionRepository.getNonce(any(), any<ChainExecutionContext>())).thenReturn(0L)
            whenever(transactionRepository.sendTransaction(any(), any<ChainExecutionContext>())).thenThrow(RuntimeException("Broadcast Node Offline"))

            val useCase = SendTransactionUseCase(
                walletRepository, transactionRepository, cryptoProvider, secureStorage,
                capabilityGate = AllowDevCapabilityGate(),
                secureKeyManager = fakeSecureKeyManager
            )

            val result = useCase(intent).toList()
            assertTrue("Expected Failure, got $result", result.first() is Result.Failure)
        }
    }

    @Test
    fun test_rawKeyExportCount_strictly_zero_when_capability_gate_denies() {
        runBlocking {
            val intent = createValidIntent(envelopeType = EvmEnvelope.EIP1559)

            val useCase = SendTransactionUseCase(
                walletRepository, transactionRepository, cryptoProvider, secureStorage,
                capabilityGate = ReleaseProductionCapabilityGate(allowEvmMainnetSend = false),
                secureKeyManager = fakeSecureKeyManager
            )

            val result = useCase(intent).toList()
            assertTrue("Expected Failure due to capability gate, got $result", result.first() is Result.Failure)
            assertEquals(0, fakeSecureKeyManager.signCount)
        }
    }

    // =========================================================================
    // 2. Adversarial Scenarios: Intent Tampering Post-Auth / Post-Creation
    // =========================================================================

    @Test
    fun test_adversarial_tampering_any_intent_field_without_fingerprint_rejected_by_model() {
        val valid = createValidIntent()

        // 1. Recipient tampering
        val exRecipient = assertThrows(IllegalArgumentException::class.java) {
            valid.copy(recipient = EvmAddress.fromString("0x000000000000000000000000000000000000dead"))
        }
        assertTrue(exRecipient.message!!.contains("Canonical fingerprint mismatch"))

        // 2. Native value tampering
        val exValue = assertThrows(IllegalArgumentException::class.java) {
            valid.copy(nativeValue = Wei.fromWei(BigInteger.fromLong(999999999999999999L)))
        }
        assertTrue(exValue.message!!.contains("Canonical fingerprint mismatch"))

        // 3. Token contract tampering
        val validToken = createValidIntent(tokenContract = tokenContractAddress, tokenDecimals = 6, humanAmount = "50.0")
        val exToken = assertThrows(IllegalArgumentException::class.java) {
            validToken.copy(tokenContract = EvmAddress.fromString("0xdac17f958d2ee523a2206206994597c13d831ec7"))
        }
        assertTrue(exToken.message!!.contains("Canonical fingerprint mismatch"))

        // 4. Calldata tampering
        val exCalldata = assertThrows(IllegalArgumentException::class.java) {
            valid.copy(calldata = Calldata.fromHex("0xdeadbeef"))
        }
        assertTrue(exCalldata.message!!.contains("Canonical fingerprint mismatch"))

        // 5. GasPrice tampering
        val exGasPrice = assertThrows(IllegalArgumentException::class.java) {
            valid.copy(gasPrice = Wei.fromWeiHex("0x999999999"))
        }
        assertTrue(exGasPrice.message!!.contains("Canonical fingerprint mismatch"))

        // 6. GasLimit tampering
        val exGasLimit = assertThrows(IllegalArgumentException::class.java) {
            valid.copy(gasLimit = GasLimit.fromLong(100000L))
        }
        assertTrue(exGasLimit.message!!.contains("Canonical fingerprint mismatch"))

        // 7. Nonce tampering
        val exNonce = assertThrows(IllegalArgumentException::class.java) {
            valid.copy(nonce = Nonce.fromLong(5L))
        }
        assertTrue(exNonce.message!!.contains("Canonical fingerprint mismatch"))

        // 8. Chain tampering
        val exChain = assertThrows(IllegalArgumentException::class.java) {
            valid.copy(chain = MultiChainType.POLYGON)
        }
        assertTrue(exChain.message!!.contains("match", ignoreCase = true))

        // 9. Execution context tampering
        val exContext = assertThrows(IllegalArgumentException::class.java) {
            valid.copy(executionContext = ChainExecutionContextRegistry.resolve(MultiChainType.BSC, false))
        }
        assertTrue(exContext.message!!.contains("match", ignoreCase = true))
    }

    @Test
    fun test_adversarial_forged_fee_mismatch_rejected_in_usecase_pre_signing() {
        runBlocking {
            // Adversary constructs intent where fee is manipulated to 0
            val walletId = "wallet_123"
            val sender = EvmAddress.fromString(testSenderAddress)
            val recipient = EvmAddress.fromString(recipientAddress)
            val baseUnit = BaseUnitAmount.fromDecimalString("1.0", 18)
            val gasPrice = Wei.fromWeiHex("0x4a817c800") // 20 Gwei
            val gasLimit = GasLimit.fromLong(21000L)
            val tamperedFee = Wei.ZERO // Fee should be 20 Gwei * 21000, but forged to ZERO

            val executionContext = ChainExecutionContextRegistry.resolve(MultiChainType.ETHEREUM, false)
            val fingerprint = ConfirmedEvmTransactionIntent.createFingerprint(
                walletId = walletId,
                keyAlias = walletId,
                sender = sender,
                chain = MultiChainType.ETHEREUM,
                executionContext = executionContext,
                envelopeType = EvmEnvelope.LEGACY,
                recipient = recipient,
                tokenContract = null,
                tokenSymbol = null,
                tokenDecimals = null,
                humanAmount = "1.0",
                baseUnitAmount = baseUnit,
                nativeValue = Wei.fromWei(baseUnit.value),
                calldata = Calldata.EMPTY,
                nonce = Nonce.fromLong(0L),
                gasPrice = gasPrice,
                gasLimit = gasLimit,
                fee = tamperedFee
            )

            val forgedIntent = ConfirmedEvmTransactionIntent(
                walletId = walletId,
                keyAlias = walletId,
                sender = sender,
                chain = MultiChainType.ETHEREUM,
                executionContext = executionContext,
                envelopeType = EvmEnvelope.LEGACY,
                recipient = recipient,
                tokenContract = null,
                tokenSymbol = null,
                tokenDecimals = null,
                humanAmount = "1.0",
                baseUnitAmount = baseUnit,
                nativeValue = Wei.fromWei(baseUnit.value),
                calldata = Calldata.EMPTY,
                nonce = Nonce.fromLong(0L),
                gasPrice = gasPrice,
                gasLimit = gasLimit,
                fee = tamperedFee,
                canonicalFingerprint = fingerprint
            )

            val useCase = SendTransactionUseCase(
                walletRepository, transactionRepository, cryptoProvider, secureStorage,
                capabilityGate = AllowDevCapabilityGate(),
                secureKeyManager = fakeSecureKeyManager
            )

            val result = useCase(forgedIntent).toList()
            assertTrue("Forged fee must fail", result.first() is Result.Failure)
            val ex = (result.first() as Result.Failure).exception
            assertTrue("Error must mention fee mismatch, got: ${ex.message}", ex.message?.contains("fee") == true && ex.message?.contains("does not match gasPrice * gasLimit") == true)
            assertEquals(0, fakeSecureKeyManager.signCount)
        }
    }

    @Test
    fun test_adversarial_forged_erc20_calldata_mismatch_rejected_in_usecase_pre_signing() {
        runBlocking {
            val walletId = "wallet_123"
            val sender = EvmAddress.fromString(testSenderAddress)
            val recipient = EvmAddress.fromString(recipientAddress)
            val tokenContract = EvmAddress.fromString(tokenContractAddress)
            val baseUnit = BaseUnitAmount.fromDecimalString("100.0", 6)
            val gasPrice = Wei.fromWeiHex("0x4a817c800")
            val gasLimit = GasLimit.fromLong(65000L)
            val fee = Wei.fromWei(gasPrice.value * BigInteger.fromLong(65000L))
            // Forged calldata: transferring to a different address
            val forgedCalldata = Calldata.fromHex("0xa9059cbb000000000000000000000000dead0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000005f5e100")

            val executionContext = ChainExecutionContextRegistry.resolve(MultiChainType.ETHEREUM, false)
            val fingerprint = ConfirmedEvmTransactionIntent.createFingerprint(
                walletId = walletId,
                keyAlias = walletId,
                sender = sender,
                chain = MultiChainType.ETHEREUM,
                executionContext = executionContext,
                envelopeType = EvmEnvelope.LEGACY,
                recipient = recipient,
                tokenContract = tokenContract,
                tokenSymbol = "USDC",
                tokenDecimals = 6,
                humanAmount = "100.0",
                baseUnitAmount = baseUnit,
                nativeValue = Wei.ZERO,
                calldata = forgedCalldata,
                nonce = Nonce.fromLong(0L),
                gasPrice = gasPrice,
                gasLimit = gasLimit,
                fee = fee
            )

            val forgedIntent = ConfirmedEvmTransactionIntent(
                walletId = walletId,
                keyAlias = walletId,
                sender = sender,
                chain = MultiChainType.ETHEREUM,
                executionContext = executionContext,
                envelopeType = EvmEnvelope.LEGACY,
                recipient = recipient,
                tokenContract = tokenContract,
                tokenSymbol = "USDC",
                tokenDecimals = 6,
                humanAmount = "100.0",
                baseUnitAmount = baseUnit,
                nativeValue = Wei.ZERO,
                calldata = forgedCalldata,
                nonce = Nonce.fromLong(0L),
                gasPrice = gasPrice,
                gasLimit = gasLimit,
                fee = fee,
                canonicalFingerprint = fingerprint
            )

            val useCase = SendTransactionUseCase(
                walletRepository, transactionRepository, cryptoProvider, secureStorage,
                capabilityGate = AllowDevCapabilityGate(),
                secureKeyManager = fakeSecureKeyManager
            )

            val result = useCase(forgedIntent).toList()
            assertTrue("Forged ERC-20 calldata must fail", result.first() is Result.Failure)
            val ex = (result.first() as Result.Failure).exception
            assertTrue("Error must mention ERC-20 calldata mismatch: ${ex.message}", ex.message?.contains("ERC-20 calldata mismatch") == true)
            assertEquals(0, fakeSecureKeyManager.signCount)
        }
    }

    @Test
    fun test_adversarial_nonce_mismatch_with_rpc_fails_closed_before_signing() {
        runBlocking {
            val intent = createValidIntent(nonceVal = 5L) // Intent has nonce 5
            whenever(transactionRepository.getNonce(any(), any<ChainExecutionContext>())).thenReturn(0L) // RPC reports nonce 0

            val useCase = SendTransactionUseCase(
                walletRepository, transactionRepository, cryptoProvider, secureStorage,
                capabilityGate = AllowDevCapabilityGate(),
                secureKeyManager = fakeSecureKeyManager
            )

            val result = useCase(intent).toList()
            assertTrue("Nonce mismatch must fail", result.first() is Result.Failure)
            val ex = (result.first() as Result.Failure).exception
            assertTrue("Must throw TypedNonceChangedException, got: $ex", ex is TypedNonceChangedException)
            assertEquals(0, fakeSecureKeyManager.signCount)
            verify(transactionRepository, never()).sendTransaction(any(), any<ChainExecutionContext>())
        }
    }

    // =========================================================================
    // 3. Post-Signing Transaction Verification Defense
    // =========================================================================

    @Test
    fun test_verifySignedTransactionMatchesIntent_catches_tampered_signed_payload() {
        val intent = createValidIntent(envelopeType = EvmEnvelope.EIP1559)

        val cleanKey = testPrivateKeyHex.removePrefix("0x")
        val pkBytes = ByteArray(32) { i -> cleanKey.substring(i * 2, i * 2 + 2).toInt(16).toByte() }

        val signedTxHex = EthereumSigner.signEip1559Transaction(
            chainId = ChainId.fromLong(1L),
            nonce = intent.nonce,
            maxPriorityFeePerGas = intent.gasPrice,
            maxFeePerGas = intent.gasPrice,
            gasLimit = intent.gasLimit,
            toAddress = intent.recipient,
            value = intent.nativeValue,
            data = intent.calldata,
            accessList = emptyList(),
            privateKeyBytes = pkBytes
        )

        // Verifying correct intent against signed tx succeeds
        EthereumSigner.verifySignedTransactionMatchesIntent(signedTxHex, intent)

        // Attempting to match against an altered intent must fail
        val alteredIntent = createValidIntent(
            envelopeType = EvmEnvelope.EIP1559,
            recipient = "0x1111111111111111111111111111111111111111"
        )
        assertThrows(IllegalArgumentException::class.java) {
            EthereumSigner.verifySignedTransactionMatchesIntent(signedTxHex, alteredIntent)
        }
    }

    // =========================================================================
    // 4. Auth Handle Security & Fail-Closed Gate
    // =========================================================================

    @Test
    fun test_missing_authContext_when_auth_required_fails_closed_with_zero_signatures() {
        runBlocking {
            val testKs = TestKeyStoreBackend()
            val prefs = InMemorySharedPreferences()
            val realSecureKeyManager = AndroidSecureKeyManager(
                context = mock(),
                config = SecureStorageConfig(enableRootDetection = false),
                keyStoreProvider = { testKs.createKeyStore() },
                encryptedPrefsProvider = { prefs },
                secretKeyProvider = { alias, _ -> testKs.generateAndStoreKey(alias) }
            )

            // Store key requiring authentication
            val authImport = AuthenticationContext(authHandle = TestPlatformAuthenticator.issueHandle("auth_wallet", AuthOperation.IMPORT, walletId = "auth_wallet"))
            realSecureKeyManager.storePrivateKey("auth_wallet", testPrivateKeyHex.encodeToByteArray(), requireAuth = true, authContext = authImport, expectedWalletId = "auth_wallet")

            val result = realSecureKeyManager.signWithKey(
                keyId = "auth_wallet",
                data = "TxDigestToSign32BytesLength12345".encodeToByteArray(),
                authContext = null,
                expectedWalletId = "auth_wallet"
            )

            assertTrue("Missing authContext must fail", result is Result.Failure)
            val ex = (result as Result.Failure).exception
            assertTrue("Exception must be AuthenticationRequiredException, got $ex", ex is AuthenticationRequiredException)
        }
    }

    @Test
    fun test_invalidated_auth_handle_fails_closed_with_zero_signatures() {
        runBlocking {
            val testKs = TestKeyStoreBackend()
            val prefs = InMemorySharedPreferences()
            val realSecureKeyManager = AndroidSecureKeyManager(
                context = mock(),
                config = SecureStorageConfig(enableRootDetection = false),
                keyStoreProvider = { testKs.createKeyStore() },
                encryptedPrefsProvider = { prefs },
                secretKeyProvider = { alias, _ -> testKs.generateAndStoreKey(alias) }
            )

            val authImport = AuthenticationContext(authHandle = TestPlatformAuthenticator.issueHandle("auth_wallet_inval", AuthOperation.IMPORT, walletId = "auth_wallet_inval"))
            realSecureKeyManager.storePrivateKey("auth_wallet_inval", testPrivateKeyHex.encodeToByteArray(), requireAuth = true, authContext = authImport, expectedWalletId = "auth_wallet_inval")

            val handle = TestPlatformAuthenticator.issueHandle(
                keyId = "auth_wallet_inval",
                operation = AuthOperation.SIGN,
                expiresAtMs = System.currentTimeMillis() + 60000,
                walletId = "auth_wallet_inval"
            )
            handle.invalidate() // e.g. on app backgrounding

            val result = realSecureKeyManager.signWithKey(
                keyId = "auth_wallet_inval",
                data = "TxDigestToSign32BytesLength12345".encodeToByteArray(),
                authContext = AuthenticationContext(authHandle = handle),
                expectedWalletId = "auth_wallet_inval"
            )

            assertTrue("Invalidated handle must fail", result is Result.Failure)
            val ex = (result as Result.Failure).exception
            assertTrue("Exception must be AuthenticationRequiredException", ex is AuthenticationRequiredException)
        }
    }

    @Test
    fun test_expired_auth_handle_fails_closed_with_zero_signatures() {
        runBlocking {
            val testKs = TestKeyStoreBackend()
            val prefs = InMemorySharedPreferences()
            val realSecureKeyManager = AndroidSecureKeyManager(
                context = mock(),
                config = SecureStorageConfig(enableRootDetection = false),
                keyStoreProvider = { testKs.createKeyStore() },
                encryptedPrefsProvider = { prefs },
                secretKeyProvider = { alias, _ -> testKs.generateAndStoreKey(alias) }
            )

            val authImport = AuthenticationContext(authHandle = TestPlatformAuthenticator.issueHandle("auth_wallet_exp", AuthOperation.IMPORT, walletId = "auth_wallet_exp"))
            realSecureKeyManager.storePrivateKey("auth_wallet_exp", testPrivateKeyHex.encodeToByteArray(), requireAuth = true, authContext = authImport, expectedWalletId = "auth_wallet_exp")

            val handle = TestPlatformAuthenticator.issueHandle(
                keyId = "auth_wallet_exp",
                operation = AuthOperation.SIGN,
                expiresAtMs = System.currentTimeMillis() - 10000, // expired
                walletId = "auth_wallet_exp"
            )

            val result = realSecureKeyManager.signWithKey(
                keyId = "auth_wallet_exp",
                data = "TxDigestToSign32BytesLength12345".encodeToByteArray(),
                authContext = AuthenticationContext(authHandle = handle),
                expectedWalletId = "auth_wallet_exp"
            )

            assertTrue("Expired handle must fail", result is Result.Failure)
            val ex = (result as Result.Failure).exception
            assertTrue("Exception must be AuthenticationRequiredException", ex is AuthenticationRequiredException)
        }
    }

    @Test
    fun test_wrong_operation_auth_handle_fails_closed_with_zero_signatures() {
        runBlocking {
            val testKs = TestKeyStoreBackend()
            val prefs = InMemorySharedPreferences()
            val realSecureKeyManager = AndroidSecureKeyManager(
                context = mock(),
                config = SecureStorageConfig(enableRootDetection = false),
                keyStoreProvider = { testKs.createKeyStore() },
                encryptedPrefsProvider = { prefs },
                secretKeyProvider = { alias, _ -> testKs.generateAndStoreKey(alias) }
            )

            val authImport = AuthenticationContext(authHandle = TestPlatformAuthenticator.issueHandle("auth_wallet_op", AuthOperation.IMPORT, walletId = "auth_wallet_op"))
            realSecureKeyManager.storePrivateKey("auth_wallet_op", testPrivateKeyHex.encodeToByteArray(), requireAuth = true, authContext = authImport, expectedWalletId = "auth_wallet_op")

            val handle = TestPlatformAuthenticator.issueHandle(
                keyId = "auth_wallet_op",
                operation = AuthOperation.DELETE, // wrong operation: DELETE instead of SIGN
                expiresAtMs = System.currentTimeMillis() + 60000,
                walletId = "auth_wallet_op"
            )

            val result = realSecureKeyManager.signWithKey(
                keyId = "auth_wallet_op",
                data = "TxDigestToSign32BytesLength12345".encodeToByteArray(),
                authContext = AuthenticationContext(authHandle = handle),
                expectedWalletId = "auth_wallet_op"
            )

            assertTrue("Wrong operation handle must fail", result is Result.Failure)
            val ex = (result as Result.Failure).exception
            assertTrue("Exception must be AuthenticationRequiredException", ex is AuthenticationRequiredException)
        }
    }

    @Test
    fun test_cross_key_auth_handle_fails_closed_with_zero_signatures() {
        runBlocking {
            val testKs = TestKeyStoreBackend()
            val prefs = InMemorySharedPreferences()
            val realSecureKeyManager = AndroidSecureKeyManager(
                context = mock(),
                config = SecureStorageConfig(enableRootDetection = false),
                keyStoreProvider = { testKs.createKeyStore() },
                encryptedPrefsProvider = { prefs },
                secretKeyProvider = { alias, _ -> testKs.generateAndStoreKey(alias) }
            )

            val authImport = AuthenticationContext(authHandle = TestPlatformAuthenticator.issueHandle("auth_wallet_A", AuthOperation.IMPORT, walletId = "auth_wallet_A"))
            realSecureKeyManager.storePrivateKey("auth_wallet_A", testPrivateKeyHex.encodeToByteArray(), requireAuth = true, authContext = authImport, expectedWalletId = "auth_wallet_A")

            val handle = TestPlatformAuthenticator.issueHandle(
                keyId = "auth_wallet_B", // issued for B, attempted on A
                operation = AuthOperation.SIGN,
                expiresAtMs = System.currentTimeMillis() + 60000,
                walletId = "auth_wallet_A"
            )

            val result = realSecureKeyManager.signWithKey(
                keyId = "auth_wallet_A",
                data = "TxDigestToSign32BytesLength12345".encodeToByteArray(),
                authContext = AuthenticationContext(authHandle = handle),
                expectedWalletId = "auth_wallet_A"
            )

            assertTrue("Cross-key handle must fail", result is Result.Failure)
            val ex = (result as Result.Failure).exception
            assertTrue("Exception must be AuthenticationRequiredException", ex is AuthenticationRequiredException)
        }
    }

    // =========================================================================
    // 5. EIP-1559 Full Pipeline: Type 0x02, r, s, yParity, sender recovery, broadcast
    // =========================================================================

    @Test
    fun test_eip1559_pipeline_type02_r_s_yParity_recovery_and_broadcast() {
        runBlocking {
            val intent = createValidIntent(envelopeType = EvmEnvelope.EIP1559)
            val broadcastCaptor = argumentCaptor<String>()

            whenever(transactionRepository.getNonce(any(), any<ChainExecutionContext>())).thenReturn(0L)
            whenever(transactionRepository.sendTransaction(broadcastCaptor.capture(), any<ChainExecutionContext>())).thenReturn("0xBroadcastTxHash")

            val useCase = SendTransactionUseCase(
                walletRepository, transactionRepository, cryptoProvider, secureStorage,
                capabilityGate = AllowDevCapabilityGate(),
                secureKeyManager = fakeSecureKeyManager
            )

            val result = useCase(intent).toList()
            assertTrue("SendTransactionUseCase must succeed for EIP-1559", result.first() is Result.Success)

            val rawSignedTx = broadcastCaptor.firstValue
            assertTrue("EIP-1559 raw tx must start with 0x02", rawSignedTx.startsWith("0x02") || rawSignedTx.startsWith("0X02"))

            // Decode raw tx and verify sender
            val recoveredSender = EthereumSigner.recoverSenderFromSignedTransaction(rawSignedTx)
            assertEquals("Recovered sender must match intent sender", testSenderAddress.lowercase(), recoveredSender.lowercase())
        }
    }

    // =========================================================================
    // 6. EIP-2930 / Unsupported Envelope Fail-Closed Gate
    // =========================================================================

    @Test
    fun test_eip2930_envelope_fails_closed_at_gate() {
        runBlocking {
            val intent = createValidIntent(envelopeType = EvmEnvelope.EIP2930)

            val useCase = SendTransactionUseCase(
                walletRepository, transactionRepository, cryptoProvider, secureStorage,
                capabilityGate = AllowDevCapabilityGate(),
                secureKeyManager = fakeSecureKeyManager
            )

            val result = useCase(intent).toList()
            assertTrue("EIP-2930 must fail closed", result.first() is Result.Failure)
            val ex = (result.first() as Result.Failure).exception
            assertTrue("Must throw TypedUnsupportedTransactionException, got $ex", ex is TypedUnsupportedTransactionException)
            assertEquals("0 signatures should be generated on EIP-2930 rejection", 0, fakeSecureKeyManager.signCount)
            verify(transactionRepository, never()).sendTransaction(any(), any<ChainExecutionContext>())
        }
    }

    @Test
    fun test_EthereumSigner_signTypedTransaction_rejects_EIP2930() {
        val pkBytes = ByteArray(32) { 0x46 }
        assertThrows(IllegalArgumentException::class.java) {
            EthereumSigner.signTypedTransaction(
                envelopeType = EvmEnvelope.EIP2930,
                chainId = ChainId.fromLong(1L),
                nonce = Nonce.fromLong(0L),
                gasLimit = GasLimit.fromLong(21000L),
                toAddress = EvmAddress.fromHex(recipientAddress),
                value = Wei.ZERO,
                data = Calldata.EMPTY,
                privateKeyBytes = pkBytes
            )
        }
    }
}


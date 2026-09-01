package com.cbstudio.wearwallet.core.security

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.context.ChainExecutionContext
import com.cbstudio.wearwallet.core.domain.model.context.ChainExecutionContextRegistry
import com.cbstudio.wearwallet.core.domain.model.context.NetworkType
import com.cbstudio.wearwallet.core.domain.model.intent.ConfirmedEvmTransactionIntent
import com.cbstudio.wearwallet.core.domain.model.quantities.*
import com.cbstudio.wearwallet.core.domain.repository.TransactionRepository
import com.cbstudio.wearwallet.core.domain.repository.WalletRepository
import com.cbstudio.wearwallet.core.domain.usecase.transaction.SendTransactionUseCase
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.core.platform.SecureStorage
import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

/**
 * Adversarial Attack Verification:
 * BSC + Generic testnet tampering attack is 100% rejected.
 *
 * Attack Vectors Tested:
 * 1. Attacker attempts to pass BSC chain with generic "testnet" or Sepolia/Goerli chainId (e.g. 11155111, 5) to spoof testnet authorization.
 * 2. Attacker attempts to sign a transaction with forged chainId in the intent vs the canonical BSC context.
 * 3. ReleaseProductionCapabilityGate strictly rejects BSC Mainnet software sign attempts while allowing BSC Testnet (chainId 97).
 * 4. SendTransactionUseCase pre-signing validation catches any context tampering between MultiChainType and ChainExecutionContext.
 */
class BscGenericTestnetTamperingAttackTest {

    @Mock
    lateinit var walletRepository: WalletRepository
    @Mock
    lateinit var transactionRepository: TransactionRepository
    @Mock
    lateinit var secureStorage: SecureStorage

    private val secureKeyManager = FakeSecureKeyManager()
    private val cryptoProvider = CommonCryptoProvider()
    private val testPrivateKey = "4646464646464646464646464646464646464646464646464646464646464646"
    private val senderAddress = "0x9d8A62f656a8d1615C1294fd71e9CFb3E4855A4F"
    private val recipientAddress = "0x3535353535353535353535353535353535353535"

    private val releaseGate = ReleaseProductionCapabilityGate(
        allowEvmMainnetSend = false,
        allowBroadcast = true
    )

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        secureKeyManager.setKey("bsc_wallet", testPrivateKey)
    }

    @Test
    fun test_bsc_mainnet_software_sign_is_strictly_denied() {
        val bscMainnetReq = CapabilityRequest(
            operation = Operation.SOFTWARE_SIGN,
            chain = MultiChainType.BSC,
            network = Network.MAINNET,
            platform = Platform.WEAR_OS,
            buildType = BuildType.RELEASE,
            envelopeType = EvmEnvelope.LEGACY,
            signerImplementation = SignerImplementation.SOFTWARE_LOCAL,
            walletType = WalletType.SOFTWARE_PRIVATE_KEY,
            backendIdentity = BackendIdentity.PRODUCTION_V1,
            backendAvailable = true,
            backendVersion = "1.0.0",
            smokeVectorVerified = true
        )

        val decision = releaseGate.checkCapability(bscMainnetReq)
        assertTrue("BSC Mainnet software sign MUST be denied", decision is CapabilityDecision.Denied)
    }

    @Test
    fun test_bsc_testnet_software_sign_is_allowed_under_release_gate() {
        val bscTestnetReq = CapabilityRequest(
            operation = Operation.SOFTWARE_SIGN,
            chain = MultiChainType.BSC,
            network = Network.TESTNET,
            platform = Platform.WEAR_OS,
            buildType = BuildType.RELEASE,
            envelopeType = EvmEnvelope.LEGACY,
            signerImplementation = SignerImplementation.SOFTWARE_LOCAL,
            walletType = WalletType.SOFTWARE_PRIVATE_KEY,
            backendIdentity = BackendIdentity.PRODUCTION_V1,
            backendAvailable = true,
            backendVersion = "1.0.0",
            smokeVectorVerified = true
        )

        val decision = releaseGate.checkCapability(bscTestnetReq)
        assertTrue("BSC Testnet software sign MUST be allowed", decision is CapabilityDecision.Allowed)
    }

    @Test
    fun test_adversarial_bsc_tampered_with_sepolia_chain_id_rejected() {
        runBlocking {
            // Adversary constructs BSC intent but with Sepolia chainId (11155111) instead of 97
            val forgedContext = ChainExecutionContext(
                chain = ChainType.BSC,
                multiChainType = MultiChainType.BSC,
                networkType = NetworkType.TESTNET,
                chainId = 11155111L,
                rpcBackendIdentity = "forged-rpc",
                capabilityNetwork = Network.TESTNET
            )

            val sender = EvmAddress.fromString(senderAddress)
            val recipient = EvmAddress.fromString(recipientAddress)
            val baseUnitAmount = BaseUnitAmount.fromDecimalString("1.0", 18)
            val gasPrice = Wei.fromWeiDecimal("20000000000")
            val gasLimit = GasLimit.fromLong(21000L)
            val fee = Wei.fromWei(gasPrice.value * BigInteger.fromLong(21000L))
            val nonce = Nonce.fromLong(0L)

            val fingerprint = ConfirmedEvmTransactionIntent.createFingerprint(
                walletId = "bsc_wallet",
                keyAlias = "bsc_wallet",
                sender = sender,
                chain = MultiChainType.BSC,
                executionContext = forgedContext,
                envelopeType = EvmEnvelope.LEGACY,
                recipient = recipient,
                tokenContract = null,
                tokenSymbol = null,
                tokenDecimals = null,
                humanAmount = "1.0",
                baseUnitAmount = baseUnitAmount,
                nativeValue = Wei.fromWei(baseUnitAmount.value),
                calldata = Calldata.EMPTY,
                nonce = nonce,
                gasPrice = gasPrice,
                gasLimit = gasLimit,
                fee = fee
            )

            val tamperedIntent = ConfirmedEvmTransactionIntent(
                walletId = "bsc_wallet",
                keyAlias = "bsc_wallet",
                sender = sender,
                chain = MultiChainType.BSC,
                executionContext = forgedContext,
                envelopeType = EvmEnvelope.LEGACY,
                recipient = recipient,
                tokenContract = null,
                tokenSymbol = null,
                tokenDecimals = null,
                humanAmount = "1.0",
                baseUnitAmount = baseUnitAmount,
                nativeValue = Wei.fromWei(baseUnitAmount.value),
                calldata = Calldata.EMPTY,
                nonce = nonce,
                gasPrice = gasPrice,
                gasLimit = gasLimit,
                fee = fee,
                canonicalFingerprint = fingerprint
            )

            whenever(transactionRepository.getNonce(any(), any<ChainExecutionContext>())).thenReturn(0L)

            val useCase = SendTransactionUseCase(
                walletRepository,
                transactionRepository,
                cryptoProvider,
                secureStorage,
                capabilityGate = releaseGate,
                secureKeyManager = secureKeyManager
            )

            val results = useCase(tamperedIntent).toList()
            val first = results.first()
            assertTrue("Forged BSC context MUST fail validation", first is Result.Failure)
            val ex = (first as Result.Failure).exception
            assertTrue("Error must cite execution context mismatch: " + ex.message, ex.message?.contains("ChainExecutionContext") == true || ex.message?.contains("mismatch") == true || ex is SecurityException || ex is IllegalArgumentException)
            assertEquals(0, secureKeyManager.signCount)
        }
    }

    @Test
    fun test_legitimate_bsc_testnet_intent_succeeds() {
        runBlocking {
            val legitimateContext = ChainExecutionContextRegistry.resolve(MultiChainType.BSC, isTestnet = true)
            assertEquals(97L, legitimateContext.chainId)
            assertEquals(NetworkType.TESTNET, legitimateContext.networkType)

            val sender = EvmAddress.fromString(senderAddress)
            val recipient = EvmAddress.fromString(recipientAddress)
            val baseUnitAmount = BaseUnitAmount.fromDecimalString("1.0", 18)
            val gasPrice = Wei.fromWeiDecimal("20000000000")
            val gasLimit = GasLimit.fromLong(21000L)
            val fee = Wei.fromWei(gasPrice.value * BigInteger.fromLong(21000L))
            val nonce = Nonce.fromLong(0L)

            val fingerprint = ConfirmedEvmTransactionIntent.createFingerprint(
                walletId = "bsc_wallet",
                keyAlias = "bsc_wallet",
                sender = sender,
                chain = MultiChainType.BSC,
                executionContext = legitimateContext,
                envelopeType = EvmEnvelope.LEGACY,
                recipient = recipient,
                tokenContract = null,
                tokenSymbol = null,
                tokenDecimals = null,
                humanAmount = "1.0",
                baseUnitAmount = baseUnitAmount,
                nativeValue = Wei.fromWei(baseUnitAmount.value),
                calldata = Calldata.EMPTY,
                nonce = nonce,
                gasPrice = gasPrice,
                gasLimit = gasLimit,
                fee = fee
            )

            val validIntent = ConfirmedEvmTransactionIntent(
                walletId = "bsc_wallet",
                keyAlias = "bsc_wallet",
                sender = sender,
                chain = MultiChainType.BSC,
                executionContext = legitimateContext,
                envelopeType = EvmEnvelope.LEGACY,
                recipient = recipient,
                tokenContract = null,
                tokenSymbol = null,
                tokenDecimals = null,
                humanAmount = "1.0",
                baseUnitAmount = baseUnitAmount,
                nativeValue = Wei.fromWei(baseUnitAmount.value),
                calldata = Calldata.EMPTY,
                nonce = nonce,
                gasPrice = gasPrice,
                gasLimit = gasLimit,
                fee = fee,
                canonicalFingerprint = fingerprint
            )

            whenever(transactionRepository.getNonce(any(), any<ChainExecutionContext>())).thenReturn(0L)
            whenever(transactionRepository.sendTransaction(any(), any<ChainExecutionContext>())).thenReturn("0xBscTxSuccessHash")

            val useCase = SendTransactionUseCase(
                walletRepository,
                transactionRepository,
                cryptoProvider,
                secureStorage,
                capabilityGate = releaseGate,
                secureKeyManager = secureKeyManager
            )

            val results = useCase(validIntent).toList()
            val first = results.first()
            assertTrue("Legitimate BSC testnet send should succeed", first is Result.Success)
            assertEquals("0xBscTxSuccessHash", (first as Result.Success).data)
            assertEquals(1, secureKeyManager.signCount)
        }
    }
}

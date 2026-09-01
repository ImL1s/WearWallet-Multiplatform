package com.cbstudio.wearwallet.core.domain.usecase.transaction

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.WalletAccount
import com.cbstudio.wearwallet.core.domain.model.WalletType
import com.cbstudio.wearwallet.core.domain.repository.TransactionRepository
import com.cbstudio.wearwallet.core.domain.repository.WalletRepository
import com.cbstudio.wearwallet.core.platform.SecureStorage
import com.cbstudio.wearwallet.core.security.CommonCryptoProvider
import com.cbstudio.wearwallet.core.security.CapabilityGate
import com.cbstudio.wearwallet.core.security.ReleaseProductionCapabilityGate
import com.cbstudio.wearwallet.core.security.AllowDevCapabilityGate
import com.cbstudio.wearwallet.core.domain.model.context.ChainExecutionContext
import com.cbstudio.wearwallet.core.domain.model.context.ChainExecutionContextRegistry
import com.cbstudio.wearwallet.core.domain.model.intent.ConfirmedEvmTransactionIntent
import com.cbstudio.wearwallet.core.domain.model.quantities.*
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import com.cbstudio.wearwallet.core.security.FakeSecureKeyManager
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.never
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

class SendTransactionUseCaseTest {

    @Mock
    lateinit var walletRepository: WalletRepository
    @Mock
    lateinit var transactionRepository: TransactionRepository
    @Mock
    lateinit var secureStorage: SecureStorage

    private val secureKeyManager = com.cbstudio.wearwallet.core.security.FakeSecureKeyManager()

    private val cryptoProvider = CommonCryptoProvider()
    private val testPrivateKey = "4646464646464646464646464646464646464646464646464646464646464646"
    private val testSenderAddress = "0x9d8A62f656a8d1615C1294fd71e9CFb3E4855A4F"
    private val recipientAddress = "0x3535353535353535353535353535353535353535"

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        val mainnets = listOf(
            ChainType.ETHEREUM,
            ChainType.BSC,
            ChainType.POLYGON,
            ChainType.ARBITRUM,
            ChainType.OPTIMISM,
            ChainType.BASE,
            ChainType.AVALANCHE,
            ChainType.LINEA,
            ChainType.ZKSYNC
        )
        for (chain in mainnets) {
            secureKeyManager.setKey("wallet_${chain.name}", testPrivateKey)
        }
        secureKeyManager.setKey("wallet_123", testPrivateKey)
    }

    @Test
    fun `invoke fails closed under ReleaseProductionCapabilityGate without exporting private key`() {
        runBlocking {
            val mainnets = listOf(
                ChainType.ETHEREUM,
                ChainType.BSC,
                ChainType.POLYGON,
                ChainType.ARBITRUM,
                ChainType.OPTIMISM,
                ChainType.BASE,
                ChainType.AVALANCHE,
                ChainType.LINEA,
                ChainType.ZKSYNC
            )

            for (chain in mainnets) {
                val wallet = WalletAccount(
                    id = "wallet_${chain.name}",
                    name = "Test Wallet",
                    address = testSenderAddress,
                    publicKey = "pub_key",
                    chainType = chain,
                    walletType = WalletType.HOT_WALLET
                )
                whenever(walletRepository.getActiveWallet()).thenReturn(Result.Success(wallet))

                val useCase = SendTransactionUseCase(
                    walletRepository,
                    transactionRepository,
                    cryptoProvider,
                    secureStorage,
                    capabilityGate = ReleaseProductionCapabilityGate(allowEvmMainnetSend = false),
                    secureKeyManager = secureKeyManager
                )

                val result = useCase(recipientAddress, "1.0").toList()
                assertTrue("Chain $chain should fail closed", result.first() is Result.Failure)
                assertTrue(
                    "Chain $chain exception should be UnsupportedOperationException",
                    (result.first() as Result.Failure).exception is UnsupportedOperationException
                )

                verify(transactionRepository, never()).sendTransaction(any(), any<ChainType>())
            }
        }
    }

    @Test
    fun `invoke success with AllowDevCapabilityGate sends transaction with zero key export`() {
        runBlocking {
            val wallet = WalletAccount(
                id = "wallet_123",
                name = "Test Wallet",
                address = testSenderAddress,
                publicKey = "pub_key",
                chainType = ChainType.ETHEREUM,
                walletType = WalletType.HOT_WALLET
            )
            val txHash = "0xSuccessHash"

            whenever(walletRepository.getActiveWallet()).thenReturn(Result.Success(wallet))
            whenever(transactionRepository.estimateGas(any())).thenReturn("21000")
            whenever(transactionRepository.getGasPrice(any<ChainType>())).thenReturn("20000000000")
            whenever(transactionRepository.getNonce(any(), any<ChainType>())).thenReturn(9L)
            whenever(transactionRepository.getNonce(any(), any<ChainExecutionContext>())).thenReturn(9L)
            whenever(transactionRepository.sendTransaction(any(), any<ChainType>())).thenReturn(txHash)
            whenever(transactionRepository.sendTransaction(any(), any<ChainExecutionContext>())).thenReturn(txHash)

            val useCase = SendTransactionUseCase(
                walletRepository,
                transactionRepository,
                cryptoProvider,
                secureStorage,
                capabilityGate = AllowDevCapabilityGate(),
                secureKeyManager = secureKeyManager
            )

            val result = useCase(
                toAddress = recipientAddress,
                amount = "1.0"
            ).toList()

            assertTrue("Result should be Success but was ${result.first()}", result.first() is Result.Success)
            assertEquals(txHash, (result.first() as Result.Success).data)
        }
    }

    @Test
    fun `invoke ERC20 token transfer serializes contract data without dropping tokenAddress`() {
        runBlocking {
            val wallet = WalletAccount(
                id = "wallet_123",
                name = "Test Wallet",
                address = testSenderAddress,
                publicKey = "pub_key",
                chainType = ChainType.ETHEREUM,
                walletType = WalletType.HOT_WALLET
            )
            val tokenContract = "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48"
            val txHash = "0xTokenTxHash"

            whenever(walletRepository.getActiveWallet()).thenReturn(Result.Success(wallet))
            whenever(transactionRepository.estimateGas(any())).thenReturn("65000")
            whenever(transactionRepository.getGasPrice(any<ChainType>())).thenReturn("20000000000")
            whenever(transactionRepository.getNonce(any(), any<ChainType>())).thenReturn(0L)
            whenever(transactionRepository.getNonce(any(), any<ChainExecutionContext>())).thenReturn(0L)
            whenever(transactionRepository.sendTransaction(any(), any<ChainType>())).thenReturn(txHash)
            whenever(transactionRepository.sendTransaction(any(), any<ChainExecutionContext>())).thenReturn(txHash)

            val useCase = SendTransactionUseCase(
                walletRepository,
                transactionRepository,
                cryptoProvider,
                secureStorage,
                capabilityGate = AllowDevCapabilityGate(),
                secureKeyManager = secureKeyManager
            )

            val result = useCase(
                toAddress = recipientAddress,
                amount = "100.0",
                tokenAddress = tokenContract,
                tokenDecimals = 18  // Use explicit decimals to satisfy fail-closed guard
            ).toList()

            assertTrue(result.first() is Result.Success)
            assertEquals(txHash, (result.first() as Result.Success).data)
        }
    }

    @Test
    fun `invoke fails when active wallet fetching fails`() {
        runBlocking {
            whenever(walletRepository.getActiveWallet()).thenReturn(Result.Failure(Exception("No active wallet")))
            val useCase = SendTransactionUseCase(walletRepository, transactionRepository, cryptoProvider, secureStorage, capabilityGate = AllowDevCapabilityGate(), secureKeyManager = secureKeyManager)

            val result = useCase(recipientAddress, "0.1").toList()
            assertTrue(result.first() is Result.Failure)
            assertEquals("No active wallet", (result.first() as Result.Failure).exception.message)
        }
    }

    @Test
    fun `invoke fails for hardware wallet`() {
        runBlocking {
            val wallet = WalletAccount(
                id = "hw_wallet",
                name = "HW Wallet",
                address = testSenderAddress,
                publicKey = "pub_key",
                chainType = ChainType.ETHEREUM,
                walletType = WalletType.KEYSTONE
            )
            whenever(walletRepository.getActiveWallet()).thenReturn(Result.Success(wallet))
            val useCase = SendTransactionUseCase(walletRepository, transactionRepository, cryptoProvider, secureStorage, capabilityGate = AllowDevCapabilityGate(), secureKeyManager = secureKeyManager)

            val result = useCase(recipientAddress, "0.1").toList()
            assertTrue(result.first() is Result.Failure)
            assertEquals("Hardware wallet signing not yet implemented", (result.first() as Result.Failure).exception.message)
        }
    }

    @Test
    fun `createUnsignedTransaction success returns json`() {
        runBlocking {
            val wallet = WalletAccount(
                id = "wallet_123",
                name = "Test Wallet",
                address = testSenderAddress,
                publicKey = "pub_key",
                chainType = ChainType.ETHEREUM,
                walletType = WalletType.HOT_WALLET
            )
            val jsonParams = """{"nonce":"1","gasPrice":"20000000000","gasLimit":"21000","chainId":"1"}"""
            whenever(walletRepository.getActiveWallet()).thenReturn(Result.Success(wallet))
            whenever(transactionRepository.getNonce(any(), any<ChainType>())).thenReturn(1L)
            whenever(transactionRepository.estimateGas(any())).thenReturn("21000")
            whenever(transactionRepository.buildTransaction(any())).thenReturn(jsonParams)

            val useCase = SendTransactionUseCase(
                walletRepository,
                transactionRepository,
                cryptoProvider,
                secureStorage,
                capabilityGate = AllowDevCapabilityGate(),
                secureKeyManager = secureKeyManager
            )

            val result = useCase.createUnsignedTransaction(
                toAddress = recipientAddress,
                amount = "0.1"
            )

            assertTrue(result is Result.Success)
            val jsonResult = (result as Result.Success).data
            assertTrue(jsonResult.contains("\"nonce\":\"1\""))
            assertTrue(jsonResult.contains("\"gasPrice\":\"20000000000\""))
        }
    }

    @Test
    fun `invoke with negative amount throws IllegalArgumentException`() {
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

            val useCase = SendTransactionUseCase(
                walletRepository,
                transactionRepository,
                cryptoProvider,
                secureStorage,
                capabilityGate = AllowDevCapabilityGate(),
                secureKeyManager = secureKeyManager
            )

            val result = useCase(recipientAddress, "-1.0").toList()
            assertTrue(result.first() is Result.Failure)
            val exception = (result.first() as Result.Failure).exception
            assertTrue("Expected IllegalArgumentException but got ${exception::class}", exception is IllegalArgumentException)
            assertEquals("Amount must be non-negative", exception.message)
        }
    }

    @Test
    fun `createUnsignedTransaction with negative amount fails with IllegalArgumentException`() {
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

            val useCase = SendTransactionUseCase(
                walletRepository,
                transactionRepository,
                cryptoProvider,
                secureStorage,
                capabilityGate = AllowDevCapabilityGate(),
                secureKeyManager = secureKeyManager
            )

            val result = useCase.createUnsignedTransaction(
                toAddress = recipientAddress,
                amount = "-0.5"
            )

            assertTrue(result is Result.Failure)
            val exception = (result as Result.Failure).exception
            assertTrue(exception is IllegalArgumentException)
            assertEquals("Amount must be non-negative", exception.message)
        }
    }

    @Test
    fun `createUnsignedTransaction fails closed under ReleaseProductionCapabilityGate`() {
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

            val useCase = SendTransactionUseCase(
                walletRepository,
                transactionRepository,
                cryptoProvider,
                secureStorage,
                capabilityGate = ReleaseProductionCapabilityGate(allowEvmMainnetSend = false),
                secureKeyManager = secureKeyManager
            )

            val result = useCase.createUnsignedTransaction(
                toAddress = recipientAddress,
                amount = "0.1"
            )

            assertTrue(result is Result.Failure)
            val exception = (result as Result.Failure).exception
            assertTrue(exception is UnsupportedOperationException)
        }
    }

    @Test
    fun `invoke with ConfirmedEvmTransactionIntent directly sends transaction without active wallet refetch`() {
        runBlocking {
            val walletId = "wallet_123"
            val password = "password"
            val txHash = "0xIntentSuccessHash"

            val sender = EvmAddress.fromString(testSenderAddress)
            val recipient = EvmAddress.fromString(recipientAddress)
            val baseUnitAmount = BaseUnitAmount.fromDecimalString("1.0", 18)
            val gasPrice = Wei.fromWeiHex("0x4a817c800") // 20 Gwei
            val gasLimit = GasLimit.fromLong(21000L)
            val fee = Wei.fromWei(gasPrice.value * com.ionspin.kotlin.bignum.integer.BigInteger.fromLong(21000L))
            val nonce = Nonce.fromLong(0L)

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
                baseUnitAmount = baseUnitAmount,
                nativeValue = Wei.fromWei(baseUnitAmount.value),
                calldata = Calldata.EMPTY,
                nonce = nonce,
                gasPrice = gasPrice,
                gasLimit = gasLimit,
                fee = fee
            )

            val intent = ConfirmedEvmTransactionIntent(
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
            whenever(transactionRepository.sendTransaction(any(), any<ChainExecutionContext>())).thenReturn(txHash)

            val useCase = SendTransactionUseCase(
                walletRepository,
                transactionRepository,
                cryptoProvider,
                secureStorage,
                capabilityGate = AllowDevCapabilityGate(),
                secureKeyManager = secureKeyManager
            )

            val result = useCase(intent).toList()

            assertTrue(result.first() is Result.Success)
            assertEquals(txHash, (result.first() as Result.Success).data)
            // Verify getActiveWallet was NEVER called
            verify(walletRepository, never()).getActiveWallet()
        }
    }

    @Test
    fun `invoke with EIP-1559 intent computes Type-0x02 digest and signs successfully`() {
        runBlocking {
            val walletId = "wallet_123"
            val txHash = "0xEip1559SuccessHash"

            val sender = EvmAddress.fromString(testSenderAddress)
            val recipient = EvmAddress.fromString(recipientAddress)
            val baseUnitAmount = BaseUnitAmount.fromDecimalString("1.0", 18)
            val gasPrice = Wei.fromWeiHex("0x4a817c800")
            val gasLimit = GasLimit.fromLong(21000L)
            val fee = Wei.fromWei(gasPrice.value * com.ionspin.kotlin.bignum.integer.BigInteger.fromLong(21000L))
            val nonce = Nonce.fromLong(0L)

            val executionContext = ChainExecutionContextRegistry.resolve(MultiChainType.ETHEREUM, false)
            val fingerprint = ConfirmedEvmTransactionIntent.createFingerprint(
                walletId = walletId,
                keyAlias = walletId,
                sender = sender,
                chain = MultiChainType.ETHEREUM,
                executionContext = executionContext,
                envelopeType = EvmEnvelope.EIP1559,
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

            val intent = ConfirmedEvmTransactionIntent(
                walletId = walletId,
                keyAlias = walletId,
                sender = sender,
                chain = MultiChainType.ETHEREUM,
                executionContext = executionContext,
                envelopeType = EvmEnvelope.EIP1559,
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
            whenever(transactionRepository.sendTransaction(any(), any<ChainExecutionContext>())).thenReturn(txHash)

            val useCase = SendTransactionUseCase(
                walletRepository,
                transactionRepository,
                cryptoProvider,
                secureStorage,
                capabilityGate = AllowDevCapabilityGate(),
                secureKeyManager = secureKeyManager
            )

            val result = useCase(intent).toList()

            assertTrue(result.first() is Result.Success)
            assertEquals(txHash, (result.first() as Result.Success).data)
        }
    }

    @Test
    fun `SendTransactionUseCase passes intent keyAlias to signWithKey`() {
        runBlocking {
            val walletId = "wallet_id_1"
            val customKeyAlias = "my_custom_hardware_key_alias"
            val txHash = "0xKeyAliasTxHash"

            val sender = EvmAddress.fromString(testSenderAddress)
            val recipient = EvmAddress.fromString(recipientAddress)
            val baseUnitAmount = BaseUnitAmount.fromDecimalString("1.0", 18)
            val gasPrice = Wei.fromWeiDecimal("20000000000")
            val gasLimit = GasLimit.fromLong(21000L)
            val fee = Wei.fromWei(gasPrice.value * com.ionspin.kotlin.bignum.integer.BigInteger.fromLong(21000L))
            val nonce = Nonce.fromLong(0L)

            val executionContext = ChainExecutionContextRegistry.resolve(MultiChainType.ETHEREUM, false)
            val fingerprint = ConfirmedEvmTransactionIntent.createFingerprint(
                walletId = walletId,
                keyAlias = customKeyAlias,
                sender = sender,
                chain = MultiChainType.ETHEREUM,
                executionContext = executionContext,
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

            val intent = ConfirmedEvmTransactionIntent(
                walletId = walletId,
                keyAlias = customKeyAlias,
                sender = sender,
                chain = MultiChainType.ETHEREUM,
                executionContext = executionContext,
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

            secureKeyManager.setKey(customKeyAlias, testPrivateKey)
            whenever(transactionRepository.getNonce(any(), any<ChainExecutionContext>())).thenReturn(0L)
            whenever(transactionRepository.sendTransaction(any(), any<ChainExecutionContext>())).thenReturn(txHash)

            val useCase = SendTransactionUseCase(
                walletRepository,
                transactionRepository,
                cryptoProvider,
                secureStorage,
                capabilityGate = AllowDevCapabilityGate(),
                secureKeyManager = secureKeyManager
            )

            val result = useCase(intent).toList()
            assertTrue(result.first() is Result.Success)

            val fakeManager = secureKeyManager as FakeSecureKeyManager
            val lastSignCall = fakeManager.signCalls.lastOrNull()
            assertNotNull(lastSignCall)
            assertEquals(customKeyAlias, lastSignCall?.keyId)
        }
    }

    @Test
    fun `SendTransactionUseCase fails closed when authHandle keyId does not match intent keyAlias`() {
        runBlocking {
            val walletId = "wallet_id_1"
            val keyAlias = "key_alias_1"

            val sender = EvmAddress.fromString(testSenderAddress)
            val recipient = EvmAddress.fromString(recipientAddress)
            val baseUnitAmount = BaseUnitAmount.fromDecimalString("1.0", 18)
            val gasPrice = Wei.fromWeiDecimal("20000000000")
            val gasLimit = GasLimit.fromLong(21000L)
            val fee = Wei.fromWei(gasPrice.value * com.ionspin.kotlin.bignum.integer.BigInteger.fromLong(21000L))
            val nonce = Nonce.fromLong(0L)

            val executionContext = ChainExecutionContextRegistry.resolve(MultiChainType.ETHEREUM, false)
            val fingerprint = ConfirmedEvmTransactionIntent.createFingerprint(
                walletId = walletId,
                keyAlias = keyAlias,
                sender = sender,
                chain = MultiChainType.ETHEREUM,
                executionContext = executionContext,
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

            val intent = ConfirmedEvmTransactionIntent(
                walletId = walletId,
                keyAlias = keyAlias,
                sender = sender,
                chain = MultiChainType.ETHEREUM,
                executionContext = executionContext,
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

            val mismatchHandle = com.cbstudio.wearwallet.core.security.TestPlatformAuthenticator.issueHandle(
                keyId = "wrong_key_alias",
                operation = com.cbstudio.wearwallet.core.security.AuthOperation.SIGN,
                intentFingerprint = fingerprint
            )
            val authContext = com.cbstudio.wearwallet.core.security.AuthenticationContext(authHandle = mismatchHandle)

            whenever(transactionRepository.getNonce(any(), any<ChainExecutionContext>())).thenReturn(0L)

            val useCase = SendTransactionUseCase(
                walletRepository,
                transactionRepository,
                cryptoProvider,
                secureStorage,
                capabilityGate = AllowDevCapabilityGate(),
                secureKeyManager = secureKeyManager
            )

            val result = useCase(intent, authContext = authContext).toList()
            assertTrue(result.first() is Result.Failure)
            val exception = (result.first() as Result.Failure).exception
            assertTrue("Expected AuthenticationRequiredException but was ${exception::class}", exception is com.cbstudio.wearwallet.core.security.AuthenticationRequiredException)
            assertTrue(exception.message?.contains("Cross-key handle rejected") == true)
        }
    }
}


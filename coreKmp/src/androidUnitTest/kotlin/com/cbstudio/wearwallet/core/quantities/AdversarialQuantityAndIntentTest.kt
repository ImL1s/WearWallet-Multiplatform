package com.cbstudio.wearwallet.core.quantities

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.context.ChainExecutionContext
import com.cbstudio.wearwallet.core.domain.model.context.ChainExecutionContextRegistry
import com.cbstudio.wearwallet.core.domain.model.intent.ConfirmedEvmTransactionIntent
import com.cbstudio.wearwallet.core.domain.model.quantities.*
import com.cbstudio.wearwallet.core.domain.repository.TransactionRepository
import com.cbstudio.wearwallet.core.domain.repository.WalletRepository
import com.cbstudio.wearwallet.core.domain.usecase.transaction.SendTransactionUseCase
import com.cbstudio.wearwallet.core.domain.usecase.transaction.TypedUnsupportedTransactionException
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.core.platform.SecureStorage
import com.cbstudio.wearwallet.core.security.AllowDevCapabilityGate
import com.cbstudio.wearwallet.core.security.CommonCryptoProvider
import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

class AdversarialQuantityAndIntentTest {

    @Mock
    lateinit var walletRepository: WalletRepository
    @Mock
    lateinit var transactionRepository: TransactionRepository
    @Mock
    lateinit var secureStorage: SecureStorage

    private val secureKeyManager = com.cbstudio.wearwallet.core.security.FakeSecureKeyManager()

    private val cryptoProvider = CommonCryptoProvider()
    private val testPrivateKey = "4646464646464646464646464646464646464646464646464646464646464646"
    private val senderAddress = "0x9d8A62f656a8d1615C1294fd71e9CFb3E4855A4F"
    private val recipientAddress = "0x3535353535353535353535353535353535353535"

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        secureKeyManager.setKey("w1", testPrivateKey)
    }

    // ==========================================
    // 1. EIP-1559 Envelope & Intent Validation Tests
    // ==========================================

    @Test
    fun `EIP-1559 envelope is supported and correctly signed with zero raw key export`() {
        runBlocking {
            val sender = EvmAddress.fromString(senderAddress)
            val recipient = EvmAddress.fromString(recipientAddress)
            val baseUnitAmount = BaseUnitAmount.fromDecimalString("1.0", 18)
            val gasPrice = Wei.fromWeiDecimal("20000000000")
            val gasLimit = GasLimit.fromLong(21000L)
            val fee = Wei.fromWei(gasPrice.value * BigInteger.fromLong(21000L))
            val nonce = Nonce.fromLong(0L)

            val executionContext = ChainExecutionContextRegistry.resolve(MultiChainType.ETHEREUM, false)
            val fingerprint = ConfirmedEvmTransactionIntent.createFingerprint(
                walletId = "w1",
                keyAlias = "w1",
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
                walletId = "w1",
                keyAlias = "w1",
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
            whenever(transactionRepository.sendTransaction(any(), any<ChainExecutionContext>())).thenReturn("0xEip1559TxHash")

            val useCase = SendTransactionUseCase(
                walletRepository,
                transactionRepository,
                cryptoProvider,
                secureStorage,
                capabilityGate = AllowDevCapabilityGate(),
                secureKeyManager = secureKeyManager
            )

            val results = useCase(intent).toList()
            val first = results.first()

            assertTrue("Result should be success", first is Result.Success)
            assertEquals("0xEip1559TxHash", (first as Result.Success).data)
        }
    }

    @Test
    fun `EIP-2930 envelope is rejected BEFORE touching private key`() {
        runBlocking {
            val sender = EvmAddress.fromString(senderAddress)
            val recipient = EvmAddress.fromString(recipientAddress)
            val baseUnitAmount = BaseUnitAmount.fromDecimalString("1.0", 18)
            val gasPrice = Wei.fromWeiDecimal("20000000000")
            val gasLimit = GasLimit.fromLong(21000L)
            val fee = Wei.fromWei(gasPrice.value * BigInteger.fromLong(21000L))
            val nonce = Nonce.fromLong(0L)

            val executionContext = ChainExecutionContextRegistry.resolve(MultiChainType.ETHEREUM, false)
            val fingerprint = ConfirmedEvmTransactionIntent.createFingerprint(
                walletId = "w1",
                keyAlias = "w1",
                sender = sender,
                chain = MultiChainType.ETHEREUM,
                executionContext = executionContext,
                envelopeType = EvmEnvelope.EIP2930,
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
                walletId = "w1",
                keyAlias = "w1",
                sender = sender,
                chain = MultiChainType.ETHEREUM,
                executionContext = executionContext,
                envelopeType = EvmEnvelope.EIP2930,
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

            val useCase = SendTransactionUseCase(
                walletRepository,
                transactionRepository,
                cryptoProvider,
                secureStorage,
                capabilityGate = AllowDevCapabilityGate(),
                secureKeyManager = secureKeyManager
            )

            val results = useCase(intent).toList()
            val first = results.first()

            assertTrue(first is Result.Failure)
            val exception = (first as Result.Failure).exception
            assertTrue(exception is TypedUnsupportedTransactionException)
        }
    }

    @Test
    fun `tampered fee in intent fails pre-signing validation BEFORE private key export`() {
        runBlocking {
            val sender = EvmAddress.fromString(senderAddress)
            val recipient = EvmAddress.fromString(recipientAddress)
            val baseUnitAmount = BaseUnitAmount.fromDecimalString("1.0", 18)
            val gasPrice = Wei.fromWeiDecimal("20000000000")
            val gasLimit = GasLimit.fromLong(21000L)
            val wrongFee = Wei.fromWeiDecimal("100") // Tampered fee != gasPrice * gasLimit
            val nonce = Nonce.fromLong(0L)

            val executionContext = ChainExecutionContextRegistry.resolve(MultiChainType.ETHEREUM, false)
            val fingerprint = ConfirmedEvmTransactionIntent.createFingerprint(
                walletId = "w1",
                keyAlias = "w1",
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
                fee = wrongFee
            )

            val intent = ConfirmedEvmTransactionIntent(
                walletId = "w1",
                keyAlias = "w1",
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
                fee = wrongFee,
                canonicalFingerprint = fingerprint
            )

            val useCase = SendTransactionUseCase(
                walletRepository,
                transactionRepository,
                cryptoProvider,
                secureStorage,
                capabilityGate = AllowDevCapabilityGate(),
                secureKeyManager = secureKeyManager
            )

            val results = useCase(intent).toList()
            val first = results.first()

            assertTrue(first is Result.Failure)
            val exception = (first as Result.Failure).exception
            assertTrue(exception is IllegalArgumentException)
            assertTrue(exception.message?.contains("fee") == true)
        }
    }

    // ==========================================
    // 2. Quantity Parser Edge Cases Tests
    // ==========================================

    @Test
    fun `BaseUnitAmount fromDecimalString rejects scientific notation`() {
        val scientificInputs = listOf("1e18", "1E18", "1.5e3", "2.1E-4", "1e+18")
        for (input in scientificInputs) {
            try {
                BaseUnitAmount.fromDecimalString(input, 18)
                fail("Expected IllegalArgumentException for scientific notation '$input'")
            } catch (e: IllegalArgumentException) {
                // Expected
            }
        }
    }

    @Test
    fun `BaseUnitAmount fromDecimalString rejects negative numbers`() {
        val negativeInputs = listOf("-1.0", "-100", "-0.0001", "-0")
        for (input in negativeInputs) {
            try {
                BaseUnitAmount.fromDecimalString(input, 18)
                fail("Expected IllegalArgumentException for negative amount '$input'")
            } catch (e: IllegalArgumentException) {
                // Expected
            }
        }
    }

    @Test
    fun `BaseUnitAmount fromDecimalString rejects hex strings`() {
        val hexInputs = listOf("0x10", "0x0", "0XFF")
        for (input in hexInputs) {
            try {
                BaseUnitAmount.fromDecimalString(input, 18)
                fail("Expected IllegalArgumentException for hex input in human amount API '$input'")
            } catch (e: IllegalArgumentException) {
                // Expected
            }
        }
    }

    @Test
    fun `BaseUnitAmount fromDecimalString rejects excess fractional digits`() {
        // USDC has 6 decimals, giving 7 fractional digits must fail
        try {
            BaseUnitAmount.fromDecimalString("1.1234567", 6)
            fail("Expected IllegalArgumentException for excess fractional digits")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("exceed token decimals") == true)
        }
    }

    @Test
    fun `Wei fromWeiDecimal rejects scientific notation and negative values`() {
        assertThrows(IllegalArgumentException::class.java) {
            Wei.fromWeiDecimal("1e18")
        }
        assertThrows(IllegalArgumentException::class.java) {
            Wei.fromWeiDecimal("-1000")
        }
        assertThrows(IllegalArgumentException::class.java) {
            Wei.fromWeiDecimal("+1000")
        }
        assertThrows(IllegalArgumentException::class.java) {
            Wei.fromWeiDecimal("0x10")
        }
    }

    @Test
    fun `Wei fromWeiHex handles valid hex and rejects invalid hex`() {
        val validWei = Wei.fromWeiHex("0x3b9aca00")
        assertEquals(BigInteger.fromLong(1_000_000_000L), validWei.value)

        assertThrows(IllegalArgumentException::class.java) {
            Wei.fromWeiHex("0xGG")
        }
        assertThrows(IllegalArgumentException::class.java) {
            Wei.fromWeiHex("")
        }
        assertThrows(IllegalArgumentException::class.java) {
            Wei.fromWeiHex("0x")
        }
    }

    @Test
    fun `GasLimit rejects values less than 21000 and invalid hex`() {
        assertThrows(IllegalArgumentException::class.java) {
            GasLimit.fromLong(20999L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            GasLimit.fromLong(-1L)
        }
    }

    @Test
    fun `Nonce rejects negative values and empty hex`() {
        assertThrows(IllegalArgumentException::class.java) {
            Nonce.fromLong(-1L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Nonce.fromHex("0x")
        }
    }

    @Test
    fun `Calldata enforces even length and hex regex`() {
        assertThrows(IllegalArgumentException::class.java) {
            Calldata.fromHex("0x1") // odd length
        }
        assertThrows(IllegalArgumentException::class.java) {
            Calldata.fromHex("0xZZ") // non-hex
        }
        val emptyCalldata = Calldata.fromHex("")
        assertTrue(emptyCalldata.isEmpty())
    }

    @Test
    fun `Arbitrary large BigInteger inputs handle 256-bit overflow safely`() {
        val hugeDecimal = "115792089237316195423570985008687907853269984665640564039457584007913129639935" // 2^256 - 1
        val baseUnit = BaseUnitAmount.fromDecimalString(hugeDecimal, 0)
        assertEquals(hugeDecimal, baseUnit.value.toString(10))

        val wei = Wei.fromWeiDecimal(hugeDecimal)
        assertEquals(hugeDecimal, wei.value.toString(10))
    }
}

package com.cbstudio.wearwallet.core.domain.usecase.transaction

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.WalletAccount
import com.cbstudio.wearwallet.core.domain.model.WalletType
import com.cbstudio.wearwallet.core.domain.model.context.ChainExecutionContext
import com.cbstudio.wearwallet.core.domain.model.quantities.BaseUnitAmount
import com.cbstudio.wearwallet.core.domain.repository.TransactionRepository
import com.cbstudio.wearwallet.core.domain.repository.WalletRepository
import com.cbstudio.wearwallet.core.platform.SecureStorage
import com.cbstudio.wearwallet.core.security.AllowDevCapabilityGate
import com.cbstudio.wearwallet.core.security.CommonCryptoProvider
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.whenever
import org.mockito.kotlin.verify

class Milestone3ChallengerAdversarialTest {

    private val cryptoProvider = CommonCryptoProvider()
    private val testPrivateKey = "4646464646464646464646464646464646464646464646464646464646464646"
    private val testSenderAddress = "0x9d8A62f656a8d1615C1294fd71e9CFb3E4855A4F"
    private val recipientAddress = "0x3535353535353535353535353535353535353535"
    private val tokenContract = "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48"
    private val password = "password"

    private class TestMocks(
        val walletRepository: WalletRepository = org.mockito.Mockito.mock(WalletRepository::class.java),
        val transactionRepository: TransactionRepository = org.mockito.Mockito.mock(TransactionRepository::class.java),
        val secureStorage: SecureStorage = org.mockito.Mockito.mock(SecureStorage::class.java),
        val secureKeyManager: com.cbstudio.wearwallet.core.security.FakeSecureKeyManager = com.cbstudio.wearwallet.core.security.FakeSecureKeyManager().apply {
            setKey("wallet_123", "4646464646464646464646464646464646464646464646464646464646464646")
        }
    )

    private fun createWallet(): WalletAccount {
        return WalletAccount(
            id = "wallet_123",
            name = "Test Wallet",
            address = testSenderAddress,
            publicKey = "pub_key",
            chainType = ChainType.ETHEREUM,
            walletType = WalletType.HOT_WALLET
        )
    }

    @Test
    fun `challenge_1_ui_5_gwei_gas_price_encodes_5_gwei_wei_in_rlp_tx`() {
        runBlocking {
            val mocks = TestMocks()
            val wallet = createWallet()
            whenever(mocks.walletRepository.getActiveWallet()).thenReturn(Result.Success(wallet))
            whenever(mocks.transactionRepository.estimateGas(any())).thenReturn("21000")
            whenever(mocks.transactionRepository.getNonce(any(), any<ChainType>())).thenReturn(0L)
            whenever(mocks.transactionRepository.getNonce(any(), any<ChainExecutionContext>())).thenReturn(0L)
            whenever(mocks.transactionRepository.sendTransaction(any(), any<ChainType>())).thenReturn("0xTxHash")
            whenever(mocks.transactionRepository.sendTransaction(any(), any<ChainExecutionContext>())).thenReturn("0xTxHash")

            val useCase = SendTransactionUseCase(
                mocks.walletRepository, mocks.transactionRepository, cryptoProvider, mocks.secureStorage,
                capabilityGate = AllowDevCapabilityGate(),
                secureKeyManager = mocks.secureKeyManager
            )

            val results = useCase(
                toAddress = recipientAddress,
                amount = "1.0",
                gasPrice = "5000000000",  // 5 Gwei in Wei. No magnitude-based guessing.
                gasLimit = "21000"
            ).toList()

            assertTrue("Result should be Success", results.first() is Result.Success)

            val captor = argumentCaptor<String>()
            verify(mocks.transactionRepository).sendTransaction(captor.capture(), any<ChainExecutionContext>())

            val signedTxHex = captor.firstValue
            assertTrue(
                "Signed RLP transaction must encode 5 Gwei hex (12a05f200), got: $signedTxHex",
                signedTxHex.contains("12a05f200")
            )
        }
    }

    @Test
    fun `challenge_2_excess_fractional_digits_throw_IllegalArgumentException_for_6_8_18_decimals`() {
        runBlocking {
            val mocks = TestMocks()
            val wallet = createWallet()
            whenever(mocks.walletRepository.getActiveWallet()).thenReturn(Result.Success(wallet))
            whenever(mocks.transactionRepository.estimateGas(any())).thenReturn("65000")
            whenever(mocks.transactionRepository.getNonce(any(), any<ChainType>())).thenReturn(0L)
            whenever(mocks.transactionRepository.getNonce(any(), any<ChainExecutionContext>())).thenReturn(0L)
            whenever(mocks.transactionRepository.sendTransaction(any(), any<ChainType>())).thenReturn("0xTxHash")
            whenever(mocks.transactionRepository.sendTransaction(any(), any<ChainExecutionContext>())).thenReturn("0xTxHash")

            val useCase = SendTransactionUseCase(
                mocks.walletRepository, mocks.transactionRepository, cryptoProvider, mocks.secureStorage,
                capabilityGate = AllowDevCapabilityGate(),
                secureKeyManager = mocks.secureKeyManager
            )

            val res6 = useCase(
                toAddress = recipientAddress,
                amount = "1.1234567",
                tokenAddress = tokenContract,
                tokenDecimals = 6
            ).toList()
            assertTrue("6 decimals with 7 fractional digits must fail", res6.first() is Result.Failure)
            val err6 = (res6.first() as Result.Failure).exception
            assertTrue("Exception must be IllegalArgumentException", err6 is IllegalArgumentException)
            assertTrue("Message must mention exceeding decimals", err6.message?.contains("exceed token decimals") == true)

            val res8 = useCase(
                toAddress = recipientAddress,
                amount = "0.123456789",
                tokenAddress = tokenContract,
                tokenDecimals = 8
            ).toList()
            assertTrue("8 decimals with 9 fractional digits must fail", res8.first() is Result.Failure)
            val err8 = (res8.first() as Result.Failure).exception
            assertTrue("Exception must be IllegalArgumentException", err8 is IllegalArgumentException)
            assertTrue("Message must mention exceeding decimals", err8.message?.contains("exceed token decimals") == true)

            val res18 = useCase(
                toAddress = recipientAddress,
                amount = "1.0000000000000000001",
                tokenAddress = tokenContract,
                tokenDecimals = 18
            ).toList()
            assertTrue("18 decimals with 19 fractional digits must fail", res18.first() is Result.Failure)
            val err18 = (res18.first() as Result.Failure).exception
            assertTrue("Exception must be IllegalArgumentException", err18 is IllegalArgumentException)
            assertTrue("Message must mention exceeding decimals", err18.message?.contains("exceed token decimals") == true)
        }
    }

    @Test
    fun `challenge_2_direct_BaseUnitAmount_test_proves_no_silent_take_truncation`() {
        val ex6 = assertThrows(IllegalArgumentException::class.java) {
            BaseUnitAmount.fromDecimalString("1.1234567", 6)
        }
        assertTrue(ex6.message!!.contains("exceed token decimals"))

        val ex8 = assertThrows(IllegalArgumentException::class.java) {
            BaseUnitAmount.fromDecimalString("0.123456789", 8)
        }
        assertTrue(ex8.message!!.contains("exceed token decimals"))

        val ex18 = assertThrows(IllegalArgumentException::class.java) {
            BaseUnitAmount.fromDecimalString("1.0000000000000000001", 18)
        }
        assertTrue(ex18.message!!.contains("exceed token decimals"))
    }

    @Test
    fun `challenge_3_rpc_estimateGas_failure_fails_closed_without_fallback`() {
        runBlocking {
            val mocks = TestMocks()
            val wallet = createWallet()
            whenever(mocks.walletRepository.getActiveWallet()).thenReturn(Result.Success(wallet))
            whenever(mocks.transactionRepository.estimateGas(any())).thenThrow(RuntimeException("RPC Network Timeout"))

            val useCase = SendTransactionUseCase(
                mocks.walletRepository, mocks.transactionRepository, cryptoProvider, mocks.secureStorage,
                capabilityGate = AllowDevCapabilityGate(),
                secureKeyManager = mocks.secureKeyManager
            )

            val results = useCase(
                toAddress = recipientAddress,
                amount = "1.0",
                gasPrice = "5000000000",
                gasLimit = null
            ).toList()

            assertTrue("Result must be Failure on estimateGas RPC error", results.first() is Result.Failure)
            val ex = (results.first() as Result.Failure).exception
            assertTrue("Exception must contain estimateGas error details", ex.message?.contains("Failed to estimate gas") == true)
        }
    }

    @Test
    fun `challenge_3_rpc_getGasPrice_failure_fails_closed_without_fallback`() {
        runBlocking {
            val mocks = TestMocks()
            val wallet = createWallet()
            whenever(mocks.walletRepository.getActiveWallet()).thenReturn(Result.Success(wallet))
            whenever(mocks.transactionRepository.getGasPrice(any<ChainType>())).thenThrow(RuntimeException("RPC gasPrice Unavailable"))

            val useCase = SendTransactionUseCase(
                mocks.walletRepository, mocks.transactionRepository, cryptoProvider, mocks.secureStorage,
                capabilityGate = AllowDevCapabilityGate(),
                secureKeyManager = mocks.secureKeyManager
            )

            val results = useCase(
                toAddress = recipientAddress,
                amount = "1.0",
                gasPrice = null,
                gasLimit = "21000"
            ).toList()

            assertTrue("Result must be Failure on getGasPrice RPC error", results.first() is Result.Failure)
            val ex = (results.first() as Result.Failure).exception
            assertTrue("Exception must contain gas price error details", ex.message?.contains("Failed to retrieve gas price") == true)
        }
    }

    @Test
    fun `challenge_3_rpc_getNonce_failure_fails_closed_without_fallback`() {
        runBlocking {
            val mocks = TestMocks()
            val wallet = createWallet()
            whenever(mocks.walletRepository.getActiveWallet()).thenReturn(Result.Success(wallet))
            whenever(mocks.transactionRepository.getNonce(any(), any<ChainType>())).thenThrow(RuntimeException("RPC nonce failure"))

            val useCase = SendTransactionUseCase(
                mocks.walletRepository, mocks.transactionRepository, cryptoProvider, mocks.secureStorage,
                capabilityGate = AllowDevCapabilityGate(),
                secureKeyManager = mocks.secureKeyManager
            )

            val results = useCase(
                toAddress = recipientAddress,
                amount = "1.0",
                gasPrice = "5000000000",
                gasLimit = "21000"
            ).toList()

            assertTrue("Result must be Failure on getNonce RPC error", results.first() is Result.Failure)
            val ex = (results.first() as Result.Failure).exception
            assertTrue("Exception must contain nonce error details", ex.message?.contains("Failed to retrieve nonce") == true)
        }
    }

    @Test
    fun `challenge_4_tokenDecimals_null_with_tokenAddress_fails_closed`() {
        runBlocking {
            val mocks = TestMocks()
            val wallet = createWallet()
            whenever(mocks.walletRepository.getActiveWallet()).thenReturn(Result.Success(wallet))
            whenever(mocks.transactionRepository.estimateGas(any())).thenReturn("65000")
            whenever(mocks.transactionRepository.getGasPrice(any<ChainType>())).thenReturn("0x12a05f200")
            whenever(mocks.transactionRepository.getNonce(any(), any<ChainType>())).thenReturn(0L)
            whenever(mocks.transactionRepository.getNonce(any(), any<ChainExecutionContext>())).thenReturn(0L)
            whenever(mocks.transactionRepository.sendTransaction(any(), any<ChainType>())).thenReturn("0xTxHash")
            whenever(mocks.transactionRepository.sendTransaction(any(), any<ChainExecutionContext>())).thenReturn("0xTxHash")

            val useCase = SendTransactionUseCase(
                mocks.walletRepository, mocks.transactionRepository, cryptoProvider, mocks.secureStorage,
                capabilityGate = AllowDevCapabilityGate(),
                secureKeyManager = mocks.secureKeyManager
            )

            // tokenAddress specified but tokenDecimals omitted → must fail-closed
            val results = useCase(
                toAddress = recipientAddress,
                amount = "100",
                tokenAddress = tokenContract,
                tokenDecimals = null
            ).toList()

            assertTrue("Result must fail when tokenDecimals is null with tokenAddress", results.first() is Result.Failure)
            val ex = (results.first() as Result.Failure).exception
            assertTrue("Error must mention tokenDecimals", ex is IllegalArgumentException)
            assertTrue(ex.message?.contains("tokenDecimals is required") == true)
        }
    }

    @Test
    fun `challenge_5_BaseUnitAmount_adversarial_inputs`() {
        // scientific notation
        assertThrows(IllegalArgumentException::class.java) {
            BaseUnitAmount.fromDecimalString("1e18", 18)
        }
        // leading +
        assertThrows(IllegalArgumentException::class.java) {
            BaseUnitAmount.fromDecimalString("+100", 18)
        }
        // multiple decimal points
        assertThrows(IllegalArgumentException::class.java) {
            BaseUnitAmount.fromDecimalString("1.2.3", 18)
        }
        // bare dot
        assertThrows(IllegalArgumentException::class.java) {
            BaseUnitAmount.fromDecimalString(".", 18)
        }
        // negative
        assertThrows(IllegalArgumentException::class.java) {
            BaseUnitAmount.fromDecimalString("-100", 18)
        }
        // empty
        assertThrows(IllegalArgumentException::class.java) {
            BaseUnitAmount.fromDecimalString("", 18)
        }
        // hex prefix in human API
        assertThrows(IllegalArgumentException::class.java) {
            BaseUnitAmount.fromDecimalString("0x100", 18)
        }
    }
}

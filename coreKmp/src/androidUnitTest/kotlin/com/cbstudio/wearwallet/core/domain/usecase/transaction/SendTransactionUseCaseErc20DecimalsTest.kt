package com.cbstudio.wearwallet.core.domain.usecase.transaction

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.WalletAccount
import com.cbstudio.wearwallet.core.domain.model.WalletType
import com.cbstudio.wearwallet.core.domain.model.context.ChainExecutionContext
import com.cbstudio.wearwallet.core.domain.repository.TransactionRepository
import com.cbstudio.wearwallet.core.domain.repository.WalletRepository
import com.cbstudio.wearwallet.core.platform.SecureStorage
import com.cbstudio.wearwallet.core.security.AllowDevCapabilityGate
import com.cbstudio.wearwallet.core.security.CommonCryptoProvider
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.whenever
import org.mockito.kotlin.verify

class SendTransactionUseCaseErc20DecimalsTest {

    private val cryptoProvider = CommonCryptoProvider()
    private val testPrivateKey = "4646464646464646464646464646464646464646464646464646464646464646"
    private val testSenderAddress = "0x9d8A62f656a8d1615C1294fd71e9CFb3E4855A4F"
    private val recipientAddress = "0x3535353535353535353535353535353535353535"
    private val tokenContract = "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48"

    private class TestMocks(
        val walletRepository: WalletRepository = org.mockito.Mockito.mock(WalletRepository::class.java),
        val transactionRepository: TransactionRepository = org.mockito.Mockito.mock(TransactionRepository::class.java),
        val secureStorage: SecureStorage = org.mockito.Mockito.mock(SecureStorage::class.java),
        val secureKeyManager: com.cbstudio.wearwallet.core.security.SecureKeyManager = org.mockito.Mockito.mock(com.cbstudio.wearwallet.core.security.SecureKeyManager::class.java)
    )

    private suspend fun setupWalletMock(mocks: TestMocks): WalletAccount {
        val wallet = WalletAccount(
            id = "wallet_123",
            name = "Test Wallet",
            address = testSenderAddress,
            publicKey = "pub_key",
            chainType = ChainType.ETHEREUM,
            walletType = WalletType.HOT_WALLET
        )
        whenever(mocks.walletRepository.getActiveWallet()).thenReturn(Result.Success(wallet))
        whenever(mocks.transactionRepository.estimateGas(any())).thenReturn("65000")
        whenever(mocks.transactionRepository.getGasPrice(any<ChainType>())).thenReturn("0x12a05f200")
        whenever(mocks.transactionRepository.getNonce(any(), any<ChainType>())).thenReturn(0L)
        whenever(mocks.transactionRepository.getNonce(any(), any<ChainExecutionContext>())).thenReturn(0L)
        whenever(mocks.transactionRepository.sendTransaction(any(), any<ChainType>())).thenReturn("0xTxHash")
        whenever(mocks.transactionRepository.sendTransaction(any(), any<ChainExecutionContext>())).thenReturn("0xTxHash")
        
        val pkBytes = testPrivateKey.removePrefix("0x").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        whenever(mocks.secureKeyManager.signWithKey(any(), any(), org.mockito.kotlin.anyOrNull(), any())).thenAnswer { inv ->
            val digest = inv.getArgument<ByteArray>(1)
            val sig = io.github.iml1s.crypto.Secp256k1Pure.signWithRecovery(digest, pkBytes)
            Result.Success(sig.r + sig.s + byteArrayOf(sig.yParity.toByte()))
        }
        return wallet
    }

    @Test
    fun `6 decimals USDC transfer formatted exact 0xa9059cbb calldata`() {
        runBlocking {
            val mocks = TestMocks()
            setupWalletMock(mocks)
            val useCase = SendTransactionUseCase(
                mocks.walletRepository, mocks.transactionRepository, cryptoProvider, mocks.secureStorage,
                capabilityGate = AllowDevCapabilityGate(),
                secureKeyManager = mocks.secureKeyManager
            )

            val result = useCase(
                toAddress = recipientAddress,
                amount = "1.5",
                tokenAddress = tokenContract,
                tokenDecimals = 6
            ).toList()

            assertTrue(result.first() is Result.Success)
            val captor = argumentCaptor<String>()
            verify(mocks.transactionRepository).sendTransaction(captor.capture(), any<ChainExecutionContext>())

            val signedTxHex = captor.firstValue
            val cleanRecipient = recipientAddress.removePrefix("0x").lowercase()
            val expectedPaddedRecipient = cleanRecipient.padStart(64, '0')
            val expectedPaddedAmount = "16e360".padStart(64, '0')
            val expectedCalldata = "a9059cbb$expectedPaddedRecipient$expectedPaddedAmount"

            assertTrue("Signed tx must contain USDC calldata $expectedCalldata", signedTxHex.contains(expectedCalldata))
        }
    }

    @Test
    fun `8 decimals WBTC transfer formatted exact 0xa9059cbb calldata`() {
        runBlocking {
            val mocks = TestMocks()
            setupWalletMock(mocks)
            val useCase = SendTransactionUseCase(
                mocks.walletRepository, mocks.transactionRepository, cryptoProvider, mocks.secureStorage,
                capabilityGate = AllowDevCapabilityGate(),
                secureKeyManager = mocks.secureKeyManager
            )

            val result = useCase(
                toAddress = recipientAddress,
                amount = "1.5",
                tokenAddress = tokenContract,
                tokenDecimals = 8
            ).toList()

            assertTrue(result.first() is Result.Success)
            val captor = argumentCaptor<String>()
            verify(mocks.transactionRepository).sendTransaction(captor.capture(), any<ChainExecutionContext>())

            val signedTxHex = captor.firstValue
            val cleanRecipient = recipientAddress.removePrefix("0x").lowercase()
            val expectedPaddedRecipient = cleanRecipient.padStart(64, '0')
            val expectedPaddedAmount = "8f0d180".padStart(64, '0')
            val expectedCalldata = "a9059cbb$expectedPaddedRecipient$expectedPaddedAmount"

            assertTrue("Signed tx must contain WBTC calldata $expectedCalldata", signedTxHex.contains(expectedCalldata))
        }
    }

    @Test
    fun `18 decimals DAI transfer formatted exact 0xa9059cbb calldata`() {
        runBlocking {
            val mocks = TestMocks()
            setupWalletMock(mocks)
            val useCase = SendTransactionUseCase(
                mocks.walletRepository, mocks.transactionRepository, cryptoProvider, mocks.secureStorage,
                capabilityGate = AllowDevCapabilityGate(),
                secureKeyManager = mocks.secureKeyManager
            )

            val result = useCase(
                toAddress = recipientAddress,
                amount = "1.5",
                tokenAddress = tokenContract,
                tokenDecimals = 18
            ).toList()

            assertTrue(result.first() is Result.Success)
            val captor = argumentCaptor<String>()
            verify(mocks.transactionRepository).sendTransaction(captor.capture(), any<ChainExecutionContext>())

            val signedTxHex = captor.firstValue
            val cleanRecipient = recipientAddress.removePrefix("0x").lowercase()
            val expectedPaddedRecipient = cleanRecipient.padStart(64, '0')
            val expectedPaddedAmount = "14d1120d7b160000".padStart(64, '0')
            val expectedCalldata = "a9059cbb$expectedPaddedRecipient$expectedPaddedAmount"

            assertTrue("Signed tx must contain DAI calldata $expectedCalldata", signedTxHex.contains(expectedCalldata))
        }
    }

    @Test
    fun `6 decimals token rejects 7 decimal digits with IllegalArgumentException`() {
        runBlocking {
            val mocks = TestMocks()
            setupWalletMock(mocks)
            val useCase = SendTransactionUseCase(
                mocks.walletRepository, mocks.transactionRepository, cryptoProvider, mocks.secureStorage,
                capabilityGate = AllowDevCapabilityGate(),
                secureKeyManager = mocks.secureKeyManager
            )

            val result = useCase(
                toAddress = recipientAddress,
                amount = "1.1234567",
                tokenAddress = tokenContract,
                tokenDecimals = 6
            ).toList()

            assertTrue(result.first() is Result.Failure)
            val exception = (result.first() as Result.Failure).exception
            assertTrue(exception is IllegalArgumentException)
            assertTrue(exception.message?.contains("exceed token decimals") == true)
        }
    }

    @Test
    fun `8 decimals token rejects 9 decimal digits with IllegalArgumentException`() {
        runBlocking {
            val mocks = TestMocks()
            setupWalletMock(mocks)
            val useCase = SendTransactionUseCase(
                mocks.walletRepository, mocks.transactionRepository, cryptoProvider, mocks.secureStorage,
                capabilityGate = AllowDevCapabilityGate(),
                secureKeyManager = mocks.secureKeyManager
            )

            val result = useCase(
                toAddress = recipientAddress,
                amount = "0.000000001",
                tokenAddress = tokenContract,
                tokenDecimals = 8
            ).toList()

            assertTrue(result.first() is Result.Failure)
            val exception = (result.first() as Result.Failure).exception
            assertTrue(exception is IllegalArgumentException)
            assertTrue(exception.message?.contains("exceed token decimals") == true)
        }
    }

    @Test
    fun `18 decimals token rejects 19 decimal digits with IllegalArgumentException`() {
        runBlocking {
            val mocks = TestMocks()
            setupWalletMock(mocks)
            val useCase = SendTransactionUseCase(
                mocks.walletRepository, mocks.transactionRepository, cryptoProvider, mocks.secureStorage,
                capabilityGate = AllowDevCapabilityGate(),
                secureKeyManager = mocks.secureKeyManager
            )

            val result = useCase(
                toAddress = recipientAddress,
                amount = "1.0000000000000000001",
                tokenAddress = tokenContract,
                tokenDecimals = 18
            ).toList()

            assertTrue(result.first() is Result.Failure)
            val exception = (result.first() as Result.Failure).exception
            assertTrue(exception is IllegalArgumentException)
            assertTrue(exception.message?.contains("exceed token decimals") == true)
        }
    }
}

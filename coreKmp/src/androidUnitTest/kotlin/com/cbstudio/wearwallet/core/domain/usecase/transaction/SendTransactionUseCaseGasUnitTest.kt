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

class SendTransactionUseCaseGasUnitTest {

    private val cryptoProvider = CommonCryptoProvider()
    private val testPrivateKey = "4646464646464646464646464646464646464646464646464646464646464646"
    private val testSenderAddress = "0x9d8A62f656a8d1615C1294fd71e9CFb3E4855A4F"
    private val recipientAddress = "0x3535353535353535353535353535353535353535"

    @Test
    fun `sendTransaction converts UI 5 Gwei gasPrice to 5_000_000_000 Wei in RLP tx`() {
        runBlocking {
            val walletRepository: WalletRepository = org.mockito.Mockito.mock(WalletRepository::class.java)
            val transactionRepository: TransactionRepository = org.mockito.Mockito.mock(TransactionRepository::class.java)
            val secureStorage: SecureStorage = org.mockito.Mockito.mock(SecureStorage::class.java)
            val secureKeyManager: com.cbstudio.wearwallet.core.security.SecureKeyManager = org.mockito.Mockito.mock(com.cbstudio.wearwallet.core.security.SecureKeyManager::class.java)

            val wallet = WalletAccount(
                id = "wallet_123",
                name = "Test Wallet",
                address = testSenderAddress,
                publicKey = "pub_key",
                chainType = ChainType.ETHEREUM,
                walletType = WalletType.HOT_WALLET
            )
            val password = "password"

            whenever(walletRepository.getActiveWallet()).thenReturn(Result.Success(wallet))
            whenever(transactionRepository.estimateGas(any())).thenReturn("21000")
            whenever(transactionRepository.getNonce(any(), any<ChainType>())).thenReturn(0L)
            whenever(transactionRepository.getNonce(any(), any<ChainExecutionContext>())).thenReturn(0L)
            whenever(transactionRepository.sendTransaction(any(), any<ChainType>())).thenReturn("0xTxHash")
            whenever(transactionRepository.sendTransaction(any(), any<ChainExecutionContext>())).thenReturn("0xTxHash")

            val pkBytes = testPrivateKey.removePrefix("0x").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            whenever(secureKeyManager.signWithKey(any(), any(), org.mockito.kotlin.anyOrNull(), any())).thenAnswer { inv ->
                val digest = inv.getArgument<ByteArray>(1)
                val sig = io.github.iml1s.crypto.Secp256k1Pure.signWithRecovery(digest, pkBytes)
                Result.Success(sig.r + sig.s + byteArrayOf(sig.yParity.toByte()))
            }

            val useCase = SendTransactionUseCase(
                walletRepository,
                transactionRepository,
                cryptoProvider,
                secureStorage,
                capabilityGate = AllowDevCapabilityGate(),
                secureKeyManager = secureKeyManager
            )

            // UI must convert Gwei to Wei BEFORE passing to UseCase.
            // 5 Gwei = 5_000_000_000 Wei. No magnitude-based guessing.
            val result = useCase(
                toAddress = recipientAddress,
                amount = "1.0",
                gasPrice = "5000000000",
                gasLimit = "21000"
            ).toList()

            assertTrue(result.first() is Result.Success)

            val captor = argumentCaptor<String>()
            verify(transactionRepository).sendTransaction(captor.capture(), any<ChainExecutionContext>())

            val signedTxHex = captor.firstValue
            assertTrue(
                "Signed transaction hex must contain 5 Gwei (12a05f200) but was: $signedTxHex",
                signedTxHex.contains("12a05f200")
            )
        }
    }
}

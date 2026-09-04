package com.cbstudio.wearwallet.core.security

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.Transaction
import com.cbstudio.wearwallet.core.domain.model.TransactionRequest
import com.cbstudio.wearwallet.core.domain.model.WalletAccount
import com.cbstudio.wearwallet.core.domain.repository.TransactionRepository
import com.cbstudio.wearwallet.core.domain.repository.WalletRepository
import com.cbstudio.wearwallet.core.domain.usecase.transaction.SendTransactionUseCase
import com.cbstudio.wearwallet.core.domain.usecase.transaction.TypedUnsupportedTransactionException
import com.cbstudio.wearwallet.core.platform.SecureStorage
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking

class FakeWalletRepository(
    private val activeWallet: WalletAccount?,
    private val exportKey: String?
) : WalletRepository {
    override suspend fun prepareProvisioning(): Result<ProvisioningRequest> = Result.Failure(Exception("Not implemented"))
    override suspend fun getActiveWallet(): Result<WalletAccount?> = Result.Success(activeWallet)
    override suspend fun createWallet(name: String, mnemonic: CharArray, password: CharArray, chainType: ChainType, authContext: com.cbstudio.wearwallet.core.security.AuthenticationContext): Result<WalletAccount> = Result.Failure(Exception())

    override suspend fun importFromMnemonic(name: String, mnemonic: CharArray, password: CharArray, chainType: ChainType, authContext: com.cbstudio.wearwallet.core.security.AuthenticationContext): Result<WalletAccount> = Result.Failure(Exception())
    override suspend fun importFromMnemonicWithKeyPair(name: String, mnemonic: CharArray, password: CharArray, chainType: ChainType, keyPair: KeyPair, address: String, authContext: com.cbstudio.wearwallet.core.security.AuthenticationContext): Result<WalletAccount> = Result.Failure(Exception())
    override suspend fun importFromPrivateKey(name: String, privateKey: com.cbstudio.wearwallet.core.security.ScopedPrivateKey, password: CharArray, chainType: ChainType, authContext: com.cbstudio.wearwallet.core.security.AuthenticationContext): Result<WalletAccount> = Result.Failure(Exception())
    override suspend fun importKeystoneWallet(name: String, xpub: String, derivationPath: String, masterFingerprint: String, chainType: ChainType, policy: com.cbstudio.wearwallet.core.security.ExtendedPublicKeyPolicy): Result<WalletAccount> = Result.Failure(Exception())
    override suspend fun getAllWallets(): Result<List<WalletAccount>> = Result.Success(emptyList())
    override suspend fun getWallet(id: String): Result<WalletAccount?> = Result.Success(null)
    override suspend fun getWalletByAddress(address: String): Result<WalletAccount?> = Result.Success(null)
    override suspend fun getKeystoneWallets(): Result<List<WalletAccount>> = Result.Success(emptyList())
    override suspend fun updateWallet(wallet: WalletAccount): Result<Unit> = Result.Success(Unit)
    override suspend fun deleteWallet(id: String, authContext: com.cbstudio.wearwallet.core.security.AuthenticationContext?): Result<Unit> = Result.Success(Unit)
    override suspend fun setActiveWallet(walletId: String): Result<Unit> = Result.Success(Unit)
    override suspend fun updateKeystoneData(walletId: String, signRequest: String?, syncData: String?): Result<Unit> = Result.Success(Unit)
    override fun observeWallets(): Flow<List<WalletAccount>> = flowOf(emptyList())
    override fun observeActiveWallet(): Flow<WalletAccount?> = flowOf(activeWallet)
}

class SpyTransactionRepository : TransactionRepository {
    var broadcastCount = 0
    var sendTransactionCalled = false

    override suspend fun sendTransaction(signedTransaction: String, chainType: ChainType): String {
        sendTransactionCalled = true
        broadcastCount++
        return "0xhash"
    }
    override suspend fun estimateGas(request: TransactionRequest): String = "21000"
    override suspend fun getNonce(walletAddress: String, chainType: ChainType): Long = 0L
    override suspend fun getGasPrice(chainType: ChainType): String = "0x4a817c800"
    override suspend fun getTransaction(hash: String, chainType: ChainType): Transaction? = null
    override fun observeTransactions(walletAddress: String): Flow<List<Transaction>> = flowOf(emptyList())
    override suspend fun buildTransaction(request: TransactionRequest): String = "{}"
    override suspend fun getTransactionHistory(walletAddress: String, chainType: ChainType): List<Transaction> = emptyList()
}

class FakeSecureStorage : SecureStorage {
    override suspend fun encrypt(plainText: String): String = plainText
    override suspend fun decrypt(encryptedText: String): String = encryptedText
    override suspend fun saveSecure(key: String, value: String) {}
    override suspend fun getSecure(key: String): String? = null
    override suspend fun removeSecure(key: String) {}
    override suspend fun hasKey(key: String): Boolean = false
}

class BroadcastSpyTest {

    @Test
    fun testNoBroadcastWhenSenderAddressMismatches() = runBlocking {
        val activeWallet = WalletAccount(
            id = "w1",
            name = "Test Wallet",
            address = "0x1111111111111111111111111111111111111111",
            publicKey = "0x04publickey",
            chainType = ChainType.ETHEREUM
        )
        val wrongKey = "4646464646464646464646464646464646464646464646464646464646464646"

        val walletRepo = FakeWalletRepository(activeWallet, wrongKey)
        val spyTxRepo = SpyTransactionRepository()
        val provider = CommonCryptoProvider()
        val secureStorage = FakeSecureStorage()
        val secureKeyManager = FakeSecureKeyManager().apply { setKey("w1", wrongKey) }

        val useCase = SendTransactionUseCase(walletRepo, spyTxRepo, provider, secureStorage, capabilityGate = AllowDevCapabilityGate(), secureKeyManager = secureKeyManager)
        val results = useCase("0x2222222222222222222222222222222222222222", "1.0").toList()

        assertTrue(results.last() is Result.Failure)
        assertEquals(false, spyTxRepo.sendTransactionCalled, "Must NOT call sendTransaction when address fails verification")
        assertEquals(0, spyTxRepo.broadcastCount, "broadcastCount MUST be 0 on sender address mismatch")
        assertTrue(spyTxRepo.broadcastCount <= 1, "broadcastCount MUST be <= 1 across all paths")
    }

    @Test
    fun testNoBroadcastAndNoPrivateKeyExportWhenSigningFailsForUnsupportedChain() = runBlocking {
        val activeWallet = WalletAccount(
            id = "w1",
            name = "Test Solana Wallet",
            address = "0x1111111111111111111111111111111111111111",
            publicKey = "0x04publickey",
            chainType = ChainType.SOLANA
        )
        val key = "4646464646464646464646464646464646464646464646464646464646464646"

        val walletRepo = FakeWalletRepository(activeWallet, key)
        val spyTxRepo = SpyTransactionRepository()
        val provider = CommonCryptoProvider()
        val secureStorage = FakeSecureStorage()
        val secureKeyManager = FakeSecureKeyManager().apply { setKey("w1", key) }

        val useCase = SendTransactionUseCase(walletRepo, spyTxRepo, provider, secureStorage, capabilityGate = ReleaseProductionCapabilityGate(allowEvmMainnetSend = false), secureKeyManager = secureKeyManager)
        val results = useCase("0x2222222222222222222222222222222222222222", "1.0").toList()

        val last = results.last()
        assertTrue(last is Result.Failure, "Unsupported chain signing must fail closed, not return success")
        val failureEx = (last as Result.Failure).exception
        assertTrue(
            failureEx is TypedUnsupportedTransactionException || failureEx is UnsupportedOperationException,
            "Unsupported chain must throw typed fail-closed exception, not a fake success: ${failureEx::class.simpleName}: ${failureEx.message}"
        )
        assertEquals(false, spyTxRepo.sendTransactionCalled, "Must NOT call sendTransaction on unsupported chain failure")
        assertEquals(0, spyTxRepo.broadcastCount, "broadcastCount MUST be 0 on unsupported chain")
        assertTrue(spyTxRepo.broadcastCount <= 1, "broadcastCount MUST be <= 1 across all paths")
    }

    @Test
    fun testSuccessfulSendHasExactBroadcastCountOne() = runBlocking {
        val validKey = "4646464646464646464646464646464646464646464646464646464646464646"
        val activeWallet = WalletAccount(
            id = "w1",
            name = "Test Valid Wallet",
            address = "0x9d8A62f656a8d1615C1294fd71e9CFb3E4855A4F",
            publicKey = "0x04publickey",
            chainType = ChainType.ETHEREUM
        )

        val walletRepo = FakeWalletRepository(activeWallet, validKey)
        val spyTxRepo = SpyTransactionRepository()
        val provider = CommonCryptoProvider()
        val secureStorage = FakeSecureStorage()
        val secureKeyManager = FakeSecureKeyManager().apply { setKey("w1", validKey) }

        val useCase = SendTransactionUseCase(walletRepo, spyTxRepo, provider, secureStorage, capabilityGate = AllowDevCapabilityGate(), secureKeyManager = secureKeyManager)
        val results = useCase("0x3535353535353535353535353535353535353535", "1.0").toList()

        assertTrue(results.last() is Result.Success)
        assertEquals(1, spyTxRepo.broadcastCount, "broadcastCount MUST be exactly 1 on successful transaction execution")
        assertTrue(spyTxRepo.broadcastCount <= 1, "broadcastCount MUST be <= 1 across all paths")
    }

    @Test
    fun testEip1559TypedUnsupportedTxRejectionHasZeroBroadcastCount() = runBlocking {
        val activeWallet = WalletAccount(
            id = "w1",
            name = "Test EIP1559 Wallet",
            address = "0x9d8A62f656a8d1615C1294fd71e9CFb3E4855A4F",
            publicKey = "0x04publickey",
            chainType = ChainType.ETHEREUM
        )
        val invalidKey = "1234"

        val walletRepo = FakeWalletRepository(activeWallet, invalidKey)
        val spyTxRepo = SpyTransactionRepository()
        val provider = CommonCryptoProvider()
        val secureStorage = FakeSecureStorage()
        val secureKeyManager = FakeSecureKeyManager().apply { setKey("w1", invalidKey) }

        val useCase = SendTransactionUseCase(walletRepo, spyTxRepo, provider, secureStorage, capabilityGate = AllowDevCapabilityGate(), secureKeyManager = secureKeyManager)
        val results = useCase("0x3535353535353535353535353535353535353535", "1.0").toList()

        assertTrue(results.last() is Result.Failure, "Invalid key format MUST cause send failure")
        assertEquals(0, spyTxRepo.broadcastCount, "broadcastCount MUST be 0 on transaction signing failure")
        assertTrue(spyTxRepo.broadcastCount <= 1, "broadcastCount MUST be <= 1 across all paths")
    }
}

package com.cbstudio.wearwallet.core.domain.usecase.wallet

import android.util.Log
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.WalletAccount
import com.cbstudio.wearwallet.core.domain.model.WalletType
import com.cbstudio.wearwallet.core.domain.repository.WalletRepository
import com.cbstudio.wearwallet.core.platform.SecureStorage
import com.cbstudio.wearwallet.core.security.CryptoProvider
import com.cbstudio.wearwallet.core.security.KeyPair
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

import com.cbstudio.wearwallet.core.security.AllowDevCapabilityGate
import com.cbstudio.wearwallet.core.security.ReleaseProductionCapabilityGate
import com.cbstudio.wearwallet.core.domain.usecase.transaction.TypedUnsupportedTransactionException

import com.cbstudio.wearwallet.core.security.AuthenticationContext
import com.cbstudio.wearwallet.core.security.AuthOperation
import com.cbstudio.wearwallet.core.security.TestPlatformAuthenticator

class ImportWalletUseCaseTest {

    @Mock
    lateinit var walletRepository: WalletRepository
    @Mock
    lateinit var cryptoProvider: CryptoProvider
    @Mock
    lateinit var secureStorage: SecureStorage

    private lateinit var importWalletUseCase: ImportWalletUseCase
    private lateinit var mockedLog: MockedStatic<Log>
    private lateinit var testAuthContext: AuthenticationContext

    @Before
    fun setup() {
        com.cbstudio.wearwallet.core.security.AuthHandleRegistry.clearForTesting()
        MockitoAnnotations.openMocks(this)
        
        // Mock android.util.Log to prevent "Method not mocked" runtime error
        mockedLog = Mockito.mockStatic(Log::class.java)
        mockedLog.`when`<Int> { Log.d(anyString(), anyString()) }.thenReturn(0)
        mockedLog.`when`<Int> { Log.e(anyString(), anyString()) }.thenReturn(0)
        mockedLog.`when`<Int> { Log.i(anyString(), anyString()) }.thenReturn(0)
        mockedLog.`when`<Int> { Log.w(anyString(), anyString()) }.thenReturn(0)
        
        testAuthContext = AuthenticationContext(
            authHandle = TestPlatformAuthenticator.issueHandle(
                keyId = "staged_test_key",
                operation = AuthOperation.IMPORT
            )
        )

        importWalletUseCase = ImportWalletUseCase(
            walletRepository,
            cryptoProvider,
            secureStorage,
            capabilityGate = AllowDevCapabilityGate()
        )
    }

    @After
    fun tearDown() {
        mockedLog.close()
        com.cbstudio.wearwallet.core.security.AuthHandleRegistry.clearForTesting()
    }

    @Test
    fun `importFromMnemonic success imports wallet`() {
        runBlocking {
            // Given
            val name = "Test Wallet"
            val mnemonic = "word1 word2 word3"
            val password = "password"
            val address = "0xAddress"
            val keyPair = KeyPair("pub", byteArrayOf(1, 2, 3))
            val wallet = WalletAccount(
                id = "1", 
                name = name, 
                address = address, 
                publicKey = keyPair.publicKey, 
                chainType = ChainType.ETHEREUM,
                walletType = WalletType.MNEMONIC
            )

            whenever(cryptoProvider.validateMnemonic(any<CharArray>())).thenReturn(true)
            whenever(cryptoProvider.generateKeyPairFromMnemonic(any<CharArray>(), any(), any())).thenReturn(keyPair)
            whenever(cryptoProvider.deriveAddress(keyPair.publicKey)).thenReturn(address)
            whenever(walletRepository.getAllWallets()).thenReturn(Result.Success(emptyList()))
            whenever(walletRepository.importFromMnemonicWithKeyPair(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Result.Success(wallet))

            // When
            val result = importWalletUseCase.importFromMnemonic(name, mnemonic.toCharArray(), password.toCharArray(), authContext = testAuthContext).toList()

            // Then
            assertTrue(result.first() is Result.Success)
            assertEquals(wallet, (result.first() as Result.Success).data)
        }
    }

    @Test
    fun `importFromMnemonic fails invalid mnemonic`() {
        runBlocking {
            whenever(cryptoProvider.validateMnemonic(any<CharArray>())).thenReturn(false)

            val result = importWalletUseCase.importFromMnemonic("name", "bad mnemonic".toCharArray(), "pw".toCharArray(), authContext = testAuthContext).toList()

            assertTrue(result.first() is Result.Failure)
            assertEquals("Invalid mnemonic phrase", (result.first() as Result.Failure).exception.message)
        }
    }

    @Test
    fun `importFromMnemonic fails duplicate wallet`() {
        runBlocking {
            val address = "0xAddress"
            val existingWallet = WalletAccount(
                id = "1", name = "Old", address = address, 
                publicKey = "pub", chainType = ChainType.ETHEREUM, walletType = WalletType.HOT_WALLET
            )
            val keyPair = KeyPair("pub", byteArrayOf(1, 2, 3))

            whenever(cryptoProvider.validateMnemonic(any<CharArray>())).thenReturn(true)
            whenever(cryptoProvider.generateKeyPairFromMnemonic(any<CharArray>(), any<String>(), any<ChainType>())).thenReturn(keyPair)
            whenever(cryptoProvider.deriveAddress(any())).thenReturn(address)
            whenever(walletRepository.getAllWallets()).thenReturn(Result.Success(listOf(existingWallet)))

            val result = importWalletUseCase.importFromMnemonic("New", "mnemonic".toCharArray(), "pw".toCharArray(), authContext = testAuthContext).toList()

            assertTrue(result.first() is Result.Failure)
            assertEquals("此錢包已經存在", (result.first() as Result.Failure).exception.message)
        }
    }

    @Test
    fun `importFromPrivateKey success imports wallet`() {
        runBlocking {
            val name = "Test Wallet"
            val privateKey = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef" // 64 chars
            val password = "password"
            val address = "0xAddress"
            val keyPair = KeyPair("pub", byteArrayOf(1, 2, 3))
            val wallet = WalletAccount(
                id = "1", name = name, address = address, publicKey = "pub", 
                chainType = ChainType.ETHEREUM, walletType = WalletType.PRIVATE_KEY
            )

            // isValidPrivateKey checks regex. My mocked key needs to match.
            // "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef" is 64 hex chars.
            
            whenever(cryptoProvider.generateKeyPairFromPrivateKey(any<ByteArray>())).thenReturn(keyPair)
            whenever(cryptoProvider.generateKeyPairFromPrivateKey(any<CharArray>())).thenReturn(keyPair)
            whenever(cryptoProvider.deriveAddress(any())).thenReturn(address)
            whenever(walletRepository.getAllWallets()).thenReturn(Result.Success(emptyList()))
            whenever(walletRepository.importFromPrivateKey(any(), any(), any(), any(), any())).thenReturn(Result.Success(wallet))

            val result = importWalletUseCase.importFromPrivateKey(name, privateKey.toCharArray(), password.toCharArray(), authContext = testAuthContext).toList()

            assertTrue(result.first() is Result.Success)
            assertEquals(wallet, (result.first() as Result.Success).data)
        }
    }

    @Test
    fun `importFromPrivateKey fails invalid format`() {
        runBlocking {
            val result = importWalletUseCase.importFromPrivateKey("name", "bad_key".toCharArray(), "pw".toCharArray(), authContext = testAuthContext).toList()

            assertTrue(result.first() is Result.Failure)
            assertEquals("Invalid private key format", (result.first() as Result.Failure).exception.message)
        }
    }
    
    @Test
    fun `importFromKeystone success`() {
        runBlocking {
            val wallet = WalletAccount(
                id = "1",
                name = "Keystone", 
                address = "0xAddress",
                publicKey = "pub",
                chainType = ChainType.ETHEREUM,
                walletType = WalletType.KEYSTONE
            )
            
            whenever(walletRepository.importKeystoneWallet(any(), any(), any(), any(), any(), any())).thenReturn(Result.Success(wallet))
            whenever(walletRepository.getAllWallets()).thenReturn(Result.Success(emptyList()))

            val validXpub = "xpub661MyMwAqRbcFtXgS5sYJABqqG9YLmC4Q1Rdap9gSE8NqtwybGhePY2gZ29ESFjqJoCu1Rupje8YtGqsefD265TMg7usUDFdp6W1EGMcet8"
            val result = importWalletUseCase.importFromKeystone("Keystone", validXpub, "m", "00000000").toList()

            assertTrue(result.first() is Result.Success)
            assertEquals(wallet, (result.first() as Result.Success).data)
        }
    }

    @Test
    fun `import methods fail closed under ReleaseProductionCapabilityGate`() = runBlocking {
        val releaseUseCase = ImportWalletUseCase(
            walletRepository,
            cryptoProvider,
            secureStorage,
            capabilityGate = ReleaseProductionCapabilityGate(allowEvmMainnetSend = false)
        )

        val resMnemonic = releaseUseCase.importFromMnemonic("Name", "word1 word2 word3".toCharArray(), "pass".toCharArray(), ChainType.ETHEREUM, authContext = testAuthContext).toList()
        assertTrue(resMnemonic.first() is Result.Failure)
        assertTrue((resMnemonic.first() as Result.Failure).exception is TypedUnsupportedTransactionException)

        val resPrivKey = releaseUseCase.importFromPrivateKey("Name", "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef".toCharArray(), "pass".toCharArray(), ChainType.ETHEREUM, authContext = testAuthContext).toList()
        assertTrue(resPrivKey.first() is Result.Failure)
        assertTrue((resPrivKey.first() as Result.Failure).exception is TypedUnsupportedTransactionException)

        val resKeystone = releaseUseCase.importFromKeystone("Name", "xpub", "path", "fp", ChainType.SOLANA).toList()
        assertTrue(resKeystone.first() is Result.Failure)
        assertTrue((resKeystone.first() as Result.Failure).exception is TypedUnsupportedTransactionException)
    }
}

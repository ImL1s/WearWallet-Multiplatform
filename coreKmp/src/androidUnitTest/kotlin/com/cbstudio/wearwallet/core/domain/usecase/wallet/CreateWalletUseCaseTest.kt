package com.cbstudio.wearwallet.core.domain.usecase.wallet

import android.util.Log
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.WalletAccount
import com.cbstudio.wearwallet.core.domain.model.WalletType
import com.cbstudio.wearwallet.core.domain.repository.WalletRepository
import com.cbstudio.wearwallet.core.security.CryptoProvider
import com.cbstudio.wearwallet.core.platform.SecureStorage
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import org.mockito.ArgumentMatchers.anyString

import com.cbstudio.wearwallet.core.security.AllowDevCapabilityGate
import com.cbstudio.wearwallet.core.security.ReleaseProductionCapabilityGate
import com.cbstudio.wearwallet.core.domain.usecase.transaction.TypedUnsupportedTransactionException

import com.cbstudio.wearwallet.core.security.AuthenticationContext
import com.cbstudio.wearwallet.core.security.AuthOperation
import com.cbstudio.wearwallet.core.security.TestPlatformAuthenticator
import com.cbstudio.wearwallet.core.security.ScopedMnemonic

class CreateWalletUseCaseTest {

    @Mock
    lateinit var walletRepository: WalletRepository
    @Mock
    lateinit var cryptoProvider: CryptoProvider
    @Mock
    lateinit var secureStorage: SecureStorage

    private lateinit var createWalletUseCase: CreateWalletUseCase
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

        createWalletUseCase = CreateWalletUseCase(walletRepository, cryptoProvider, secureStorage, capabilityGate = AllowDevCapabilityGate())
    }

    @After
    fun tearDown() {
        mockedLog.close()
        com.cbstudio.wearwallet.core.security.AuthHandleRegistry.clearForTesting()
    }

    @Test
    fun `invoke with empty name returns failure`() {
        runBlocking {
            // When
            val result = createWalletUseCase("", "password".toCharArray(), authContext = testAuthContext).toList()

            // Then
            // Result flow might emit items. We check the first one that is a failure or just check structure.
            // The implementation: emits failure and returns.
            assertTrue(result.isNotEmpty())
            assertTrue(result.first() is Result.Failure)
            assertEquals("Wallet name cannot be empty", (result.first() as Result.Failure).exception.message)
        }
    }

    @Test
    fun `invoke with invalid mnemonic returns failure`() {
        runBlocking {
            // Given
            val mnemonic = "invalid mnemonic"
            whenever(cryptoProvider.validateMnemonic(any<CharArray>())).thenReturn(false)

            // When
            val result = createWalletUseCase("Test Wallet", "password".toCharArray(), mnemonic = mnemonic.toCharArray(), authContext = testAuthContext).toList()

            // Then
            assertTrue(result.isNotEmpty())
            assertTrue(result.first() is Result.Failure)
            assertEquals("Invalid mnemonic phrase", (result.first() as Result.Failure).exception.message)
        }
    }

    @Test
    fun `invoke success creates wallet and sets active if first wallet`() {
        runBlocking {
            // Given
            val mnemonic = "word1 word2 word3"
            val wallet = WalletAccount(
                id = "wallet_123",
                name = "Test Wallet",
                address = "0x123",
                publicKey = "pub_key",
                chainType = ChainType.ETHEREUM,
                walletType = WalletType.HOT_WALLET
            )
            
            whenever(cryptoProvider.generateMnemonic(any<Int>())).thenAnswer { ScopedMnemonic.fromString(mnemonic) }
            whenever(cryptoProvider.validateMnemonic(any<CharArray>())).thenReturn(true)
            whenever(cryptoProvider.validateMnemonic(any<ScopedMnemonic>())).thenReturn(true)
            whenever(walletRepository.createWallet(any(), any(), any(), any(), any())).thenReturn(Result.Success(wallet))
            // Simulate just one wallet (the new one) exists, invoking setActiveWallet
            whenever(walletRepository.getAllWallets()).thenReturn(Result.Success(listOf(wallet)))
            whenever(walletRepository.setActiveWallet(any())).thenReturn(Result.Success(Unit))

            // When
            val result = createWalletUseCase("Test Wallet", "password".toCharArray(), authContext = testAuthContext).toList()

            // Then
            // Expect Success
            val successResult = result.find { it is Result.Success }
            assertTrue(successResult != null)
            assertEquals(wallet, (successResult as Result.Success).data)
            
            Mockito.verify(walletRepository).setActiveWallet(wallet.id)
        }
    }
    
    @Test
    fun `invoke failure from repository returns failure`() {
        runBlocking {
            // Given
            val mnemonic = "word1 word2 word3"
            whenever(cryptoProvider.generateMnemonic(any<Int>())).thenAnswer { ScopedMnemonic.fromString(mnemonic) }
            whenever(cryptoProvider.validateMnemonic(any<CharArray>())).thenReturn(true)
            whenever(cryptoProvider.validateMnemonic(any<ScopedMnemonic>())).thenReturn(true)
            whenever(walletRepository.createWallet(any(), any(), any(), any(), any())).thenReturn(Result.Failure(Exception("Repo Error")))

            // When
            val result = createWalletUseCase("Test Wallet", "password".toCharArray(), authContext = testAuthContext).toList()

            // Then
            val failureResult = result.find { it is Result.Failure }
            assertTrue(failureResult != null)
            assertEquals("Repo Error", (failureResult as Result.Failure).exception.message)
        }
    }

    @Test
    fun `createWithMnemonic returns ephemeral mnemonic words and wallet on success`() = runBlocking {
        val mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        val wallet = WalletAccount(
            id = "wallet_456",
            name = "Ephemeral Test Wallet",
            address = "0x456",
            publicKey = "pub_key_456",
            chainType = ChainType.ETHEREUM,
            walletType = WalletType.HOT_WALLET
        )

        whenever(cryptoProvider.generateMnemonic(any<Int>())).thenAnswer { ScopedMnemonic.fromString(mnemonic) }
        whenever(cryptoProvider.validateMnemonic(any<CharArray>())).thenReturn(true)
        whenever(cryptoProvider.validateMnemonic(any<ScopedMnemonic>())).thenReturn(true)
        whenever(walletRepository.createWallet(any(), any(), any(), any(), any())).thenReturn(Result.Success(wallet))
        whenever(walletRepository.getAllWallets()).thenReturn(Result.Success(listOf(wallet)))
        whenever(walletRepository.setActiveWallet(any())).thenReturn(Result.Success(Unit))

        val result = createWalletUseCase.createWithMnemonic("Ephemeral Test Wallet", "password".toCharArray(), authContext = testAuthContext).toList()
        val success = result.find { it is Result.Success }
        assertTrue(success != null)
        val created = (success as Result.Success).data
        assertEquals(wallet, created.wallet)
        val words = created.mnemonicHolder.getWords()
        assertEquals(12, words.size)
        assertEquals("abandon", words.first())
        assertEquals("about", words.last())
    }

    @Test
    fun `invoke fails closed under ReleaseProductionCapabilityGate for EVM mainnets`() = runBlocking {
        val releaseUseCase = CreateWalletUseCase(
            walletRepository,
            cryptoProvider,
            secureStorage,
            capabilityGate = ReleaseProductionCapabilityGate(allowEvmMainnetSend = false)
        )

        val result = releaseUseCase("Test Wallet", "password".toCharArray(), ChainType.ETHEREUM, authContext = testAuthContext).toList()
        assertTrue(result.first() is Result.Failure)
        assertTrue((result.first() as Result.Failure).exception is TypedUnsupportedTransactionException)
    }
}

package com.cbstudio.wearwallet.core.domain.usecase.wallet

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.Transaction
import com.cbstudio.wearwallet.core.domain.repository.TransactionRepository
import com.cbstudio.wearwallet.core.security.CryptoProvider
import com.cbstudio.wearwallet.core.security.KeyPair
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.MockedStatic
import com.cbstudio.wearwallet.core.utils.Logger
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class AccountDiscoveryUseCaseTest {

    private lateinit var transactionRepository: TransactionRepository
    private lateinit var cryptoProvider: CryptoProvider
    private lateinit var accountDiscoveryUseCase: AccountDiscoveryUseCase

    @Before
    fun setup() {
        transactionRepository = mock()
        cryptoProvider = mock()
        accountDiscoveryUseCase = AccountDiscoveryUseCase(
            transactionRepository = transactionRepository,
            cryptoProvider = cryptoProvider
        )
    }

    @Test
    fun execute_discovers_accounts_until_gap_limit() = runBlocking {
        // Arrange
        val mnemonic = "test mnemonic".toCharArray()
        val chainType = ChainType.ETHEREUM
        val coinType = 60
        
        // Mock KeyPair generation and Address derivation based on path accounts
        // Account 0: has history
        // Account 1: empty
        // Account 2: has history
        // Account 3: empty
        // Account 4: empty
        // Account 5: empty (Gap Limit reached if limit is 3)
        
        // Helper to mock crypto
        val mockAddressMap = mapOf(
            0 to "0xAddr0",
            1 to "0xAddr1",
            2 to "0xAddr2",
            3 to "0xAddr3",
            4 to "0xAddr4",
            5 to "0xAddr5",
            6 to "0xAddr6"
        )
        
        // Explicitly mock calls for each index 0..3 (Gap limit 3 means 0,1,2,3, 4, 5 checked until 3 consecutive empty)
        // Indices checked:
        // 0: Active (consecutive=0)
        // 1: Empty (consecutive=1)
        // 2: Active (consecutive=0)
        // 3: Empty (consecutive=1)
        // 4: Empty (consecutive=2)
        // 5: Empty (consecutive=3) -> STOP. (Total consecutive 3 reached after index 5 check? No, 3,4,5 are 3 empty.)
        
        val indicesToCheck = 0..5
        for (i in indicesToCheck) {
            val path = "m/44'/60'/$i'/0/0"
            val pk = "pk_$i"
            val sk = "sk_$i"
            val addr = "0xAddr$i"
            
            // Mock Key Gen
            whenever(cryptoProvider.generateKeyPairFromMnemonic(
                any<CharArray>(), 
                eq(path), 
                eq(chainType)
            )).thenReturn(KeyPair(pk, sk.encodeToByteArray()))
            
            // Mock Address Derivation
            whenever(cryptoProvider.deriveAddress(eq(pk))).thenReturn(addr)
            
            // Mock History
            val history = if (i == 0 || i == 2) listOf(mock<Transaction>()) else emptyList()
            whenever(transactionRepository.getTransactionHistory(eq(addr), eq(chainType))).thenReturn(history)
        }
        
        // Act
        val results = accountDiscoveryUseCase.execute(mnemonic, chainType, gapLimit = 3).toList()
        
        // Assert
        val successResult = results.last() as Result.Success<List<DiscoveredAccount>>
        val discovered = successResult.data
        
        assertEquals(2, discovered.size)
        assertEquals(0, discovered[0].index)
        assertEquals("0xAddr0", discovered[0].address)
        assertEquals(2, discovered[1].index)
        assertEquals("0xAddr2", discovered[1].address)
    }
}

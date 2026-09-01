package com.cbstudio.wearwallet.core.domain.usecase.transaction

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.Transaction
import com.cbstudio.wearwallet.core.domain.model.TransactionStatus
import com.cbstudio.wearwallet.core.domain.model.TransactionType
import com.cbstudio.wearwallet.core.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations

class GetTransactionHistoryUseCaseTest {

    @Mock
    private lateinit var transactionRepository: TransactionRepository

    private lateinit var getTransactionHistoryUseCase: GetTransactionHistoryUseCase

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        getTransactionHistoryUseCase = GetTransactionHistoryUseCase(transactionRepository)
    }

    @Test
    fun `invoke fetches transaction history successfully`() {
        runBlocking {
            val address = "0xWallet"
            val chainType = ChainType.ETHEREUM
            val transactions = listOf(
                createMockTransaction("hash1"),
                createMockTransaction("hash2")
            )

            Mockito.`when`(transactionRepository.getTransactionHistory(address, chainType))
                .thenReturn(transactions)

            val flow = getTransactionHistoryUseCase(address, chainType)
            val results = flow.toList()

            assertTrue(results[0] is Result.Loading)
            assertTrue(results[1] is Result.Success)

            val data = (results[1] as Result.Success).data
            assertEquals(2, data.size)
            assertEquals("hash1", data[0].hash)
        }
    }

    @Test
    fun `invoke limits transaction history`() {
        runBlocking {
            val address = "0xWallet"
            val chainType = ChainType.ETHEREUM
            val transactions = List(10) { createMockTransaction("hash$it") }
            val limit = 5

            Mockito.`when`(transactionRepository.getTransactionHistory(address, chainType))
                .thenReturn(transactions)

            val flow = getTransactionHistoryUseCase(address, chainType, limit)
            val results = flow.toList()

            val data = (results[1] as Result.Success).data
            assertEquals(5, data.size)
        }
    }

    @Test
    fun `observeTransactions returns flow from repository`() {
        val address = "0xWallet"
        val transactions = listOf(createMockTransaction("hash1"))
        Mockito.`when`(transactionRepository.observeTransactions(address)).thenReturn(flowOf(transactions))

        runBlocking {
            val result = getTransactionHistoryUseCase.observeTransactions(address)
            result.collect {
                assertEquals(transactions, it)
            }
            verify(transactionRepository).observeTransactions(address)
        }
    }

    private fun createMockTransaction(hash: String): Transaction {
        return Transaction(
            hash = hash,
            from = "0xFrom",
            to = "0xTo",
            value = "1.0",
            nonce = 0L,
            status = TransactionStatus.CONFIRMED,
            type = TransactionType.TRANSFER,
            chainType = ChainType.ETHEREUM,
            networkFee = "0.01",
            timestamp = kotlinx.datetime.Clock.System.now()
        )
    }
}

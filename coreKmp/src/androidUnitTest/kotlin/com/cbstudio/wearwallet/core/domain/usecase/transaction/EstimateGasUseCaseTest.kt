package com.cbstudio.wearwallet.core.domain.usecase.transaction

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.TransactionRequest
import com.cbstudio.wearwallet.core.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations

class EstimateGasUseCaseTest {

    private fun <T> any(type: Class<T>): T = Mockito.any(type)

    @Mock
    private lateinit var transactionRepository: TransactionRepository

    private lateinit var estimateGasUseCase: EstimateGasUseCase

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        estimateGasUseCase = EstimateGasUseCase(transactionRepository)
    }

    @Test
    fun `invoke estimates gas successfully using live gasPrice`() {
        runBlocking {
            val from = "0xFrom"
            val to = "0xTo"
            val value = "1.0"
            val chainType = ChainType.ETHEREUM
            val expectedGasLimit = "21000"
            val expectedGasPriceHex = "0x12a05f200" // 5 Gwei (5,000,000,000 Wei)

            Mockito.`when`(transactionRepository.estimateGas(any(TransactionRequest::class.java)))
                .thenReturn(expectedGasLimit)
            Mockito.`when`(transactionRepository.getGasPrice(chainType))
                .thenReturn(expectedGasPriceHex)

            val flow = estimateGasUseCase(from, to, value, chainType)
            val results = flow.toList()

            assertTrue(results[0] is Result.Loading)
            assertTrue(results[1] is Result.Success)

            val data = (results[1] as Result.Success).data
            assertEquals(expectedGasLimit, data.gasLimit)
            assertEquals("5", data.gasPrice) // In Gwei
            
            // Fee calculation: 5 Gwei * 21000 = 105,000 Gwei = 0.000105 ETH
            val feeDouble = data.totalFee.toDouble()
            assertEquals(0.000105, feeDouble, 0.0000001)
        }
    }

    @Test
    fun `invoke fails closed on gas estimation error`() {
        runBlocking {
            val from = "0xFrom"
            val to = "0xTo"
            val value = "1.0"
            val chainType = ChainType.ETHEREUM

            // Simulate RPC failure
            Mockito.`when`(transactionRepository.estimateGas(any(TransactionRequest::class.java)))
                .thenThrow(RuntimeException("RPC Error"))

            val flow = estimateGasUseCase(from, to, value, chainType)
            val results = flow.toList()

            assertTrue(results[0] is Result.Loading)
            assertTrue(results[1] is Result.Failure)
        }
    }

    @Test
    fun `invoke fails closed when tokenAddress specified without tokenDecimals`() {
        runBlocking {
            val from = "0xFrom"
            val to = "0x" + "a".repeat(40)
            val value = "100.0"
            val chainType = ChainType.ETHEREUM
            val tokenAddress = "0x" + "b".repeat(40)

            val flow = estimateGasUseCase(
                from = from,
                to = to,
                value = value,
                chainType = chainType,
                tokenAddress = tokenAddress,
                tokenDecimals = null // P1-2: must fail closed
            )
            val results = flow.toList()

            assertTrue(results[0] is Result.Loading)
            assertTrue("Should fail with IllegalArgumentException when tokenDecimals is null for ERC-20",
                results[1] is Result.Failure)
            assertTrue("Should throw IllegalArgumentException",
                (results[1] as Result.Failure).exception is IllegalArgumentException)
        }
    }

    @Test
    fun `invoke builds ERC-20 calldata for token transfer gas estimation`() {
        runBlocking {
            val from = "0xFrom"
            val to = "0x" + "a".repeat(40)
            val value = "100.0"
            val chainType = ChainType.ETHEREUM
            val tokenAddress = "0x" + "b".repeat(40)
            val tokenDecimals = 6  // USDC-like
            val expectedGasLimit = "60000"
            val expectedGasPriceHex = "0x12a05f200" // 5 Gwei

            Mockito.`when`(transactionRepository.estimateGas(any(TransactionRequest::class.java)))
                .thenReturn(expectedGasLimit)
            Mockito.`when`(transactionRepository.getGasPrice(chainType))
                .thenReturn(expectedGasPriceHex)

            val flow = estimateGasUseCase(
                from = from,
                to = to,
                value = value,
                chainType = chainType,
                tokenAddress = tokenAddress,
                tokenDecimals = tokenDecimals
            )
            val results = flow.toList()

            assertTrue(results[0] is Result.Loading)
            assertTrue("ERC-20 estimation should succeed", results[1] is Result.Success)
            
            val data = (results[1] as Result.Success).data
            assertEquals(expectedGasLimit, data.gasLimit)
        }
    }
}

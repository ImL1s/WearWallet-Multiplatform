package com.cbstudio.wearwallet.core.domain.usecase.transaction

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.address.EvmRecipientAddressPolicy
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.TransactionEstimate
import com.cbstudio.wearwallet.core.domain.model.TransactionRequest
import com.cbstudio.wearwallet.core.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations

class EstimateTransactionUseCaseTest {

    private fun <T> any(type: Class<T>): T = Mockito.any(type)

    @Mock
    private lateinit var transactionRepository: TransactionRepository

    private lateinit var useCase: EstimateTransactionUseCase

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        useCase = EstimateTransactionUseCase(transactionRepository)
    }

    @Test
    fun `invoke rejects mixed-case address that fails EIP-55`() = runBlocking {
        val results = useCase(
            fromAddress = EIP55_ALL_LOWER,
            toAddress = EIP55_WRONG_MIXED,
            amount = "0.1",
            chainType = ChainType.ETHEREUM,
        ).toList()

        assertTrue(results.isNotEmpty())
        assertTrue("wrong EIP-55 checksum must be Result.Failure", results.last() is Result.Failure)
        val message = (results.last() as Result.Failure).exception.message.orEmpty()
        assertTrue(message.contains("address", ignoreCase = true))
    }

    @Test
    fun `invoke accepts known EIP-55 checksum and all-lower of same bytes`() {
        assertTrue(EvmRecipientAddressPolicy.isValid(EIP55_GOOD))
        assertTrue(EvmRecipientAddressPolicy.isValid(EIP55_ALL_LOWER))
        assertFalse(EvmRecipientAddressPolicy.isValid(EIP55_WRONG_MIXED))
    }

    @Test
    fun `invalid gas price hex is Failure never silent 20 gwei`() = runBlocking {
        Mockito.`when`(transactionRepository.estimateGas(any(TransactionRequest::class.java)))
            .thenReturn("21000")
        Mockito.`when`(transactionRepository.getGasPrice(ChainType.ETHEREUM))
            .thenReturn("not-a-hex-price")
        Mockito.`when`(transactionRepository.getNonce(EIP55_ALL_LOWER, ChainType.ETHEREUM))
            .thenReturn(1L)

        val results = useCase(
            fromAddress = EIP55_ALL_LOWER,
            toAddress = EIP55_GOOD,
            amount = "0.1",
            chainType = ChainType.ETHEREUM,
        ).toList()

        val failure = results.filterIsInstance<Result.Failure>().lastOrNull()
        assertTrue("invalid gas price must be Result.Failure, got $results", failure != null)
        assertFalse(
            "must not pretend a 20 Gwei fallback succeeded",
            results.any { it is Result.Success },
        )
    }

    @Test
    fun `missing gas limit is Failure never silent 21000`() = runBlocking {
        Mockito.`when`(transactionRepository.estimateGas(any(TransactionRequest::class.java)))
            .thenReturn("")
        Mockito.`when`(transactionRepository.getGasPrice(ChainType.ETHEREUM))
            .thenReturn("0x12a05f200")
        Mockito.`when`(transactionRepository.getNonce(EIP55_ALL_LOWER, ChainType.ETHEREUM))
            .thenReturn(1L)

        val results = useCase(
            fromAddress = EIP55_ALL_LOWER,
            toAddress = EIP55_GOOD,
            amount = "0.1",
            chainType = ChainType.ETHEREUM,
        ).toList()

        assertTrue(
            "missing gas limit must be Result.Failure, got $results",
            results.any { it is Result.Failure },
        )
        assertFalse(results.any { it is Result.Success })
    }

    @Test
    fun `unparseable gas limit is Failure never 21000 times 20`() = runBlocking {
        Mockito.`when`(transactionRepository.estimateGas(any(TransactionRequest::class.java)))
            .thenReturn("not-a-number")
        Mockito.`when`(transactionRepository.getGasPrice(ChainType.ETHEREUM))
            .thenReturn("0x12a05f200")
        Mockito.`when`(transactionRepository.getNonce(EIP55_ALL_LOWER, ChainType.ETHEREUM))
            .thenReturn(1L)

        val results = useCase(
            fromAddress = EIP55_ALL_LOWER,
            toAddress = EIP55_GOOD,
            amount = "0.1",
            chainType = ChainType.ETHEREUM,
        ).toList()

        assertTrue(results.any { it is Result.Failure })
        assertFalse(results.any { it is Result.Success })
    }

    @Test
    fun `gas price above chain max is Failure not silently capped`() = runBlocking {
        // 600 Gwei in wei = 600 * 1e9 = 0x8bb2c97000
        Mockito.`when`(transactionRepository.estimateGas(any(TransactionRequest::class.java)))
            .thenReturn("21000")
        Mockito.`when`(transactionRepository.getGasPrice(ChainType.ETHEREUM))
            .thenReturn("0x8bb2c97000")
        Mockito.`when`(transactionRepository.getNonce(EIP55_ALL_LOWER, ChainType.ETHEREUM))
            .thenReturn(1L)

        val results = useCase(
            fromAddress = EIP55_ALL_LOWER,
            toAddress = EIP55_GOOD,
            amount = "0.1",
            chainType = ChainType.ETHEREUM,
        ).toList()

        assertTrue(
            "over-max gas must fail closed, not rewrite to 500 Gwei. got $results",
            results.any { it is Result.Failure },
        )
        assertFalse(results.any { it is Result.Success })
    }

    @Test
    fun `valid live gas estimate does not use 21000 or 20 fallbacks`() = runBlocking {
        Mockito.`when`(transactionRepository.estimateGas(any(TransactionRequest::class.java)))
            .thenReturn("21000")
        Mockito.`when`(transactionRepository.getGasPrice(ChainType.ETHEREUM))
            .thenReturn("0x12a05f200") // 5 Gwei
        Mockito.`when`(transactionRepository.getNonce(EIP55_ALL_LOWER, ChainType.ETHEREUM))
            .thenReturn(7L)

        val results = useCase(
            fromAddress = EIP55_ALL_LOWER,
            toAddress = EIP55_GOOD,
            amount = "0.1",
            chainType = ChainType.ETHEREUM,
        ).toList()

        val success = results.lastOrNull { it is Result.Success<*> }
        assertTrue("valid estimate must succeed, got $results", success is Result.Success<*>)
        val estimate = (success as Result.Success<TransactionEstimate>).data
        assertEquals("21000", estimate.gasLimit)
        assertEquals("5", estimate.gasPrice)
        assertEquals(7L, estimate.nonce)
        val fee = estimate.estimatedFee.toDouble()
        assertEquals(0.000105, fee, 0.0000001)
        assertTrue(estimate.warning.isNullOrBlank())
    }

    companion object {
        const val EIP55_GOOD = "0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed"
        const val EIP55_WRONG_MIXED = "0x5aaeb6053F3E94C9b9A09f33669435E7Ef1BeAed"
        const val EIP55_ALL_LOWER = "0x5aaeb6053f3e94c9b9a09f33669435e7ef1beaed"
    }
}

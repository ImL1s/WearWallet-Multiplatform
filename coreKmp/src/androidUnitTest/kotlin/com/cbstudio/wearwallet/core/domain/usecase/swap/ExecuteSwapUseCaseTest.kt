package com.cbstudio.wearwallet.core.domain.usecase.swap

import android.util.Log
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.rango.RangoRepository
import com.cbstudio.wearwallet.core.rango.model.RangoRoute
import com.cbstudio.wearwallet.core.rango.model.RangoSwapResponse
import com.cbstudio.wearwallet.core.rango.model.RangoTokenMeta
import com.cbstudio.wearwallet.core.rango.model.RangoTransaction
import com.cbstudio.wearwallet.core.swap.SwapExecutor
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
import org.mockito.kotlin.whenever
import org.mockito.ArgumentMatchers.anyString

import com.cbstudio.wearwallet.core.security.AllowDevCapabilityGate
import com.cbstudio.wearwallet.core.security.ReleaseProductionCapabilityGate
import com.cbstudio.wearwallet.core.domain.usecase.transaction.TypedUnsupportedTransactionException

class ExecuteSwapUseCaseTest {

    @Mock
    lateinit var rangoRepository: RangoRepository
    
    @Mock
    lateinit var swapExecutor: SwapExecutor

    private lateinit var executeSwapUseCase: ExecuteSwapUseCase
    private lateinit var mockedLog: MockedStatic<Log>

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        
        mockedLog = Mockito.mockStatic(Log::class.java)
        mockedLog.`when`<Int> { Log.d(anyString(), anyString()) }.thenReturn(0)
        mockedLog.`when`<Int> { Log.e(anyString(), anyString()) }.thenReturn(0)
        
        executeSwapUseCase = ExecuteSwapUseCase(rangoRepository, swapExecutor, capabilityGate = AllowDevCapabilityGate())
    }

    @After
    fun tearDown() {
        mockedLog.close()
    }

    @Test
    fun `invoke returns success when swap execution is successful`() = runBlocking {
        // Arrange
        val params = ExecuteSwapUseCase.Params(
            fromToken = RangoTokenMeta(blockchain = "ETH", symbol = "ETH", address = null),
            toToken = RangoTokenMeta(blockchain = "BSC", symbol = "BNB", address = null),
            amountInWei = "1000000000000000000",
            walletAddress = "0x123",
            privateKey = "0xabc",
            slippage = 1.0
        )

        val mockTransaction = RangoTransaction(
            to = "0xRouter",
            data = "0xData",
            value = "1000",
            gasLimit = "21000",
            gasPrice = "100"
        )
        
        val mockSwapResponse = RangoSwapResponse(
            requestId = "req-1",
            resultType = "OK",
            transaction = mockTransaction,
            error = null,
            route = RangoRoute(outputAmount = "500")
        )

        whenever(
            rangoRepository.createSwapTransaction(
                fromChain = "ETH",
                fromTokenSymbol = null,
                toChain = "BSC",
                toTokenSymbol = null,
                amount = "1000000000000000000",
                fromAddress = "0x123",
                toAddress = "0x123",
                slippage = 1.0
            )
        ).thenReturn(kotlin.Result.success(mockSwapResponse))

        whenever(
            swapExecutor.executeEVMSwap(any(), any(), any(), any(), any())
        ).thenReturn(Result.Success("0xHash"))

        // Act
        val result = executeSwapUseCase(params)

        // Assert
        assertTrue(result is Result.Success)
        assertEquals("0xHash", (result as Result.Success).data.txHash)
    }

    @Test
    fun `invoke returns failure when rango returns failure`() = runBlocking {
        // Arrange
        val params = ExecuteSwapUseCase.Params(
            fromToken = RangoTokenMeta(blockchain = "ETH", symbol = "ETH", address = null),
            toToken = RangoTokenMeta(blockchain = "BSC", symbol = "BNB", address = null),
            amountInWei = "10",
            walletAddress = "0x123",
            privateKey = "0xabc"
        )
        val exception = Exception("Rango API Error")

        whenever(
            rangoRepository.createSwapTransaction(
                fromChain = "ETH",
                fromTokenSymbol = null,
                toChain = "BSC",
                toTokenSymbol = null,
                amount = "10",
                fromAddress = "0x123",
                toAddress = "0x123",
                slippage = 1.0
            )
        ).thenReturn(kotlin.Result.failure(exception))

        // Act
        val result = executeSwapUseCase(params)

        // Assert
        assertTrue(result is Result.Failure)
    }

    @Test
    fun `invoke fails closed under ReleaseProductionCapabilityGate for EVM mainnets`() = runBlocking {
        val releaseUseCase = ExecuteSwapUseCase(
            rangoRepository,
            swapExecutor,
            capabilityGate = ReleaseProductionCapabilityGate(allowEvmMainnetSend = false)
        )
        val params = ExecuteSwapUseCase.Params(
            fromToken = RangoTokenMeta(blockchain = "ETH", symbol = "ETH", address = null),
            toToken = RangoTokenMeta(blockchain = "BSC", symbol = "BNB", address = null),
            amountInWei = "1000000000000000000",
            walletAddress = "0x123",
            privateKey = "0xabc"
        )

        val result = releaseUseCase(params)
        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).exception is TypedUnsupportedTransactionException)
    }
}

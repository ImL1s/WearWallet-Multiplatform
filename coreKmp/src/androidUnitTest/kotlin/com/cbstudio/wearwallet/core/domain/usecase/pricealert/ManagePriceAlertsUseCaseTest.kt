package com.cbstudio.wearwallet.core.domain.usecase.pricealert

import android.util.Log
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.pricealert.AlertType
import com.cbstudio.wearwallet.core.domain.model.pricealert.PriceAlert
import com.cbstudio.wearwallet.core.domain.repository.PriceAlertRepository
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

class ManagePriceAlertsUseCaseTest {

    @Mock
    lateinit var priceAlertRepository: PriceAlertRepository

    private lateinit var managePriceAlertsUseCase: ManagePriceAlertsUseCase
    private lateinit var mockedLog: MockedStatic<Log>

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        
        // Mock android.util.Log to prevent "Method not mocked" runtime error
        mockedLog = Mockito.mockStatic(Log::class.java)
        mockedLog.`when`<Int> { Log.d(anyString(), anyString()) }.thenReturn(0)
        mockedLog.`when`<Int> { Log.e(anyString(), anyString()) }.thenReturn(0)
        mockedLog.`when`<Int> { Log.i(anyString(), anyString()) }.thenReturn(0)
        mockedLog.`when`<Int> { Log.w(anyString(), anyString()) }.thenReturn(0)
        
        managePriceAlertsUseCase = ManagePriceAlertsUseCase(priceAlertRepository)
    }

    @After
    fun tearDown() {
        mockedLog.close()
    }

    @Test
    fun `createAlert success returns success result`() {
        runBlocking {
            // Given
            val walletId = "wallet_main_001"
            val assetSymbol = "ETH"
            val assetName = "Ethereum"
            val chainType = ChainType.ETHEREUM
            val alertType = AlertType.ABOVE
            val targetPrice = 3000.0
            val currentPrice = 2800.0
            
            whenever(priceAlertRepository.isAlertExists(anyString(), anyString(), any(), any(), Mockito.anyDouble()))
                .thenReturn(Result.Success(false))
            
            whenever(priceAlertRepository.createAlert(any()))
                .thenAnswer { invocation ->
                    Result.Success(invocation.getArgument(0) as PriceAlert)
                }

            // When
            val result = managePriceAlertsUseCase.createAlert(
                walletId = walletId,
                assetSymbol = assetSymbol,
                assetName = assetName,
                chainType = chainType,
                alertType = alertType,
                targetPrice = targetPrice,
                currentPrice = currentPrice
            )

            // Then
            assertTrue(result is Result.Success)
            val alert = (result as Result.Success).data
            assertEquals(walletId, alert.walletId)
            assertEquals(assetSymbol, alert.assetSymbol)
            assertEquals(targetPrice, alert.targetPrice, 0.0)
            assertTrue(alert.isEnabled)
        }
    }

    @Test
    fun `createAlert fails if walletId is empty`() {
        runBlocking {
            // When
            val result = managePriceAlertsUseCase.createAlert(
                walletId = "",
                assetSymbol = "ETH",
                assetName = "Ethereum",
                chainType = ChainType.ETHEREUM,
                alertType = AlertType.ABOVE,
                targetPrice = 3000.0,
                currentPrice = 2800.0
            )

            // Then
            assertTrue(result is Result.Failure)
            assertEquals("錢包 ID 不能為空", (result as Result.Failure).exception.message)
        }
    }

    @Test
    fun `createAlert fails if asset symbol is empty`() {
        runBlocking {
            // When
            val result = managePriceAlertsUseCase.createAlert(
                walletId = "wallet_main_001",
                assetSymbol = "",
                assetName = "Ethereum",
                chainType = ChainType.ETHEREUM,
                alertType = AlertType.ABOVE,
                targetPrice = 3000.0,
                currentPrice = 2800.0
            )

            // Then
            assertTrue(result is Result.Failure)
            assertEquals("資產符號不能為空", (result as Result.Failure).exception.message)
        }
    }

    @Test
    fun `createAlert fails if alert already exists`() {
        runBlocking {
            // Given
            val walletId = "wallet_main_001"
            whenever(priceAlertRepository.isAlertExists(anyString(), anyString(), any(), any(), Mockito.anyDouble()))
                .thenReturn(Result.Success(true))

            // When
            val result = managePriceAlertsUseCase.createAlert(
                walletId = walletId,
                assetSymbol = "ETH",
                assetName = "Ethereum",
                chainType = ChainType.ETHEREUM,
                alertType = AlertType.ABOVE,
                targetPrice = 3000.0,
                currentPrice = 2800.0
            )

            // Then
            assertTrue(result is Result.Failure)
            assertEquals("相同配置的提醒已存在", (result as Result.Failure).exception.message)
        }
    }

    @Test
    fun `toggleAlertEnabled success toggles state`() {
        runBlocking {
            // Given
            val alertId = "alert_123"
            val alert = PriceAlert(
                id = alertId,
                walletId = "wallet_main_001",
                assetSymbol = "ETH",
                assetName = "Ethereum",
                chainType = ChainType.ETHEREUM,
                chainId = 1,
                alertType = AlertType.ABOVE,
                targetPrice = 3000.0,
                currentPrice = 2500.0,
                isEnabled = true,
                createdAt = 123L,
                updatedAt = 123L
            )
            
            whenever(priceAlertRepository.getAlert(alertId)).thenReturn(Result.Success(alert))
            whenever(priceAlertRepository.updateEnabledStatus(alertId, false)).thenReturn(Result.Success(Unit))

            // When
            val result = managePriceAlertsUseCase.toggleAlertEnabled(alertId)

            // Then
            assertTrue(result is Result.Success)
            Mockito.verify(priceAlertRepository).updateEnabledStatus(alertId, false)
        }
    }

    @Test
    fun `deleteAlert success calls repository`() {
        runBlocking {
            // Given
            val alertId = "alert_123"
            whenever(priceAlertRepository.deleteAlert(alertId)).thenReturn(Result.Success(Unit))

            // When
            val result = managePriceAlertsUseCase.deleteAlert(alertId)

            // Then
            assertTrue(result is Result.Success)
            Mockito.verify(priceAlertRepository).deleteAlert(alertId)
        }
    }
}

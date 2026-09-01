package com.cbstudio.wearwallet.core.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.common.TypedUnsupportedOperationException
import com.cbstudio.wearwallet.core.database.CoreWalletDatabase
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.pricealert.AlertType
import com.cbstudio.wearwallet.core.domain.model.pricealert.PriceAlert
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PerWalletPriceAlertIsolationTest {

    private lateinit var database: CoreWalletDatabase
    private lateinit var repository: PriceAlertRepositoryImpl

    @Before
    fun setup() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CoreWalletDatabase.Schema.create(driver)
        database = CoreWalletDatabase(driver)
        repository = PriceAlertRepositoryImpl(database)
    }

    @Test
    fun `createAlert fails if walletId is blank`() = runBlocking {
        val alert = PriceAlert(
            id = "0",
            walletId = "",
            assetSymbol = "ETH",
            chainType = ChainType.ETHEREUM,
            chainId = 1,
            alertType = AlertType.ABOVE,
            targetPrice = 3000.0,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        val result = repository.createAlert(alert)
        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).exception.message!!.contains("walletId"))
    }

    @Test
    fun `wallet A and wallet B can independently create identical alerts`() = runBlocking {
        val alertA = PriceAlert(
            id = "0",
            walletId = "wallet_A",
            assetSymbol = "ETH",
            chainType = ChainType.ETHEREUM,
            chainId = 1,
            alertType = AlertType.ABOVE,
            targetPrice = 3000.0,
            createdAt = 1000L,
            updatedAt = 1000L
        )

        val alertB = PriceAlert(
            id = "0",
            walletId = "wallet_B",
            assetSymbol = "ETH",
            chainType = ChainType.ETHEREUM,
            chainId = 1,
            alertType = AlertType.ABOVE,
            targetPrice = 3000.0,
            createdAt = 1000L,
            updatedAt = 1000L
        )

        val resultA = repository.createAlert(alertA)
        val resultB = repository.createAlert(alertB)

        assertTrue(resultA is Result.Success)
        assertTrue(resultB is Result.Success)

        val createdA = (resultA as Result.Success).data
        val createdB = (resultB as Result.Success).data

        assertEquals("wallet_A", createdA.walletId)
        assertEquals("wallet_B", createdB.walletId)
        assertEquals(createdA.targetPrice, createdB.targetPrice, 0.0)

        // Verifying duplicate for same wallet fails
        val resultADup = repository.createAlert(alertA)
        assertTrue(resultADup is Result.Failure)
        assertEquals("相同配置的提醒已存在", (resultADup as Result.Failure).exception.message)
    }

    @Test
    fun `isAlertExists properly isolates across wallets`() = runBlocking {
        val alertA = PriceAlert(
            id = "0",
            walletId = "wallet_A",
            assetSymbol = "BTC",
            chainType = ChainType.BITCOIN,
            chainId = 0,
            alertType = AlertType.BELOW,
            targetPrice = 60000.0,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        repository.createAlert(alertA)

        val existsA = repository.isAlertExists("wallet_A", "BTC", ChainType.BITCOIN, AlertType.BELOW, 60000.0)
        val existsB = repository.isAlertExists("wallet_B", "BTC", ChainType.BITCOIN, AlertType.BELOW, 60000.0)

        assertTrue(existsA is Result.Success && existsA.data)
        assertTrue(existsB is Result.Success && !existsB.data)
    }

    @Test
    fun `deletion by walletId does not affect other wallets`() = runBlocking {
        val alertA = PriceAlert(
            id = "0",
            walletId = "wallet_A",
            assetSymbol = "SOL",
            chainType = ChainType.SOLANA,
            chainId = 0,
            alertType = AlertType.ABOVE,
            targetPrice = 150.0,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        val alertB = PriceAlert(
            id = "0",
            walletId = "wallet_B",
            assetSymbol = "SOL",
            chainType = ChainType.SOLANA,
            chainId = 0,
            alertType = AlertType.ABOVE,
            targetPrice = 150.0,
            createdAt = 1000L,
            updatedAt = 1000L
        )

        repository.createAlert(alertA)
        repository.createAlert(alertB)

        // Delete wallet A alerts via SQL query
        database.priceAlertQueries.deleteByWalletId("wallet_A")

        val countA = database.priceAlertQueries.countByWalletId("wallet_A").executeAsOne()
        val countB = database.priceAlertQueries.countByWalletId("wallet_B").executeAsOne()

        assertEquals(0L, countA)
        assertEquals(1L, countB)
    }

    @Test
    fun `all repository mutations update real database state`() = runBlocking {
        val alert = PriceAlert(
            id = "0",
            walletId = "wallet_mutations",
            assetSymbol = "ETH",
            chainType = ChainType.ETHEREUM,
            chainId = 1,
            alertType = AlertType.ABOVE,
            targetPrice = 3500.0,
            currentPrice = 3000.0,
            createdAt = 1000L,
            updatedAt = 1000L
        )

        val created = (repository.createAlert(alert) as Result.Success).data
        val alertId = created.id

        // 1. updateCurrentPrice
        val updatePriceRes = repository.updateCurrentPrice(alertId, 3400.0)
        assertTrue(updatePriceRes is Result.Success)
        val fetched1 = (repository.getAlert(alertId) as Result.Success).data
        assertNotNull(fetched1)
        assertEquals(3400.0, fetched1!!.currentPrice!!, 0.0)

        // 2. updateEnabledStatus
        val updateEnabledRes = repository.updateEnabledStatus(alertId, false)
        assertTrue(updateEnabledRes is Result.Success)
        val fetched2 = (repository.getAlert(alertId) as Result.Success).data!!
        assertFalse(fetched2.isEnabled)

        // 3. triggerAlert
        val triggerRes = repository.triggerAlert(alertId)
        assertTrue(triggerRes is Result.Success)
        val fetched3 = (repository.getAlert(alertId) as Result.Success).data!!
        assertTrue(fetched3.isTriggered)
        assertEquals(1, fetched3.triggerCount)

        // 4. markNotificationSent
        val notifRes = repository.markNotificationSent(alertId)
        assertTrue(notifRes is Result.Success)
        val fetched4 = (repository.getAlert(alertId) as Result.Success).data!!
        assertTrue(fetched4.notificationSent)

        // 5. resetTriggerStatus
        val resetRes = repository.resetTriggerStatus(alertId)
        assertTrue(resetRes is Result.Success)
        val fetched5 = (repository.getAlert(alertId) as Result.Success).data!!
        assertFalse(fetched5.isTriggered)
        assertFalse(fetched5.notificationSent)
    }

    @Test
    fun `unsupported analytics operations fail with TypedUnsupportedOperationException`() = runBlocking {
        val cleanupRes = repository.cleanupExpiredTriggers(1000L)
        assertTrue(cleanupRes is Result.Failure)
        assertTrue((cleanupRes as Result.Failure).exception is TypedUnsupportedOperationException)

        val resetStaleRes = repository.resetStaleAlerts(1000L)
        assertTrue(resetStaleRes is Result.Failure)
        assertTrue((resetStaleRes as Result.Failure).exception is TypedUnsupportedOperationException)
    }
}

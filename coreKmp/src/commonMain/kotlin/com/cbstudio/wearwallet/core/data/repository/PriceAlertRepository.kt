package com.cbstudio.wearwallet.core.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.common.TypedUnsupportedOperationException
import com.cbstudio.wearwallet.core.database.CoreWalletDatabase
import com.cbstudio.wearwallet.core.database.Price_alert
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.pricealert.*
import com.cbstudio.wearwallet.core.domain.repository.PriceAlertRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock

/**
 * 使用 SQLDelight 實現的價格提醒儲存庫
 * 提供完整的價格提醒資料持久化功能
 */
class PriceAlertRepositoryImpl(
    private val database: CoreWalletDatabase
) : PriceAlertRepository {
    
    private val priceAlertQueries = database.priceAlertQueries
    
    override suspend fun createAlert(alert: PriceAlert): Result<PriceAlert> {
        return try {
            if (alert.walletId.isBlank()) {
                return Result.Failure(IllegalArgumentException("walletId 不能為空"))
            }
            if (alert.assetSymbol.isBlank()) {
                return Result.Failure(IllegalArgumentException("assetSymbol 不能為空"))
            }
            if (alert.targetPrice <= 0.0) {
                return Result.Failure(IllegalArgumentException("targetPrice 必須大於 0"))
            }
            
            // 檢查是否已存在相同配置的提醒 (Per-Wallet Isolation)
            if (priceAlertQueries.existsBySameConfig(
                wallet_id = alert.walletId,
                asset_symbol = alert.assetSymbol, 
                chain_type = alert.chainType.name,
                alert_type = alert.alertType.name,
                target_price = alert.targetPrice
            ).executeAsOne()) {
                return Result.Failure(Exception("相同配置的提醒已存在"))
            }
            
            // 插入到數據庫
            priceAlertQueries.insert(
                wallet_id = alert.walletId,
                asset_symbol = alert.assetSymbol,
                asset_name = alert.assetName,
                contract_address = alert.contractAddress,
                chain_type = alert.chainType.name,
                chain_id = alert.chainId.toLong(),
                alert_type = alert.alertType.name,
                target_price = alert.targetPrice,
                current_price = alert.currentPrice,
                percentage_threshold = alert.percentageThreshold,
                is_enabled = if (alert.isEnabled) 1L else 0L,
                user_notes = alert.userNotes,
                webhook_url = alert.webhookUrl,
                repeat_interval = alert.repeatInterval.toLong()
            )
            
            // 獲取插入的提醒 ID
            val alertId = priceAlertQueries.lastInsertRowId().executeAsOne()
            
            // 查詢並返回創建的提醒
            val createdAlert = priceAlertQueries.selectById(alertId).executeAsOne()
            Result.Success(createdAlert.toPriceAlert())
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun getAlert(id: String): Result<PriceAlert?> {
        return try {
            val alertId = id.toLongOrNull() ?: return Result.Failure(
                IllegalArgumentException("Invalid alert ID: $id")
            )
            val alert = priceAlertQueries.selectById(alertId).executeAsOneOrNull()
            Result.Success(alert?.toPriceAlert())
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun getAllAlerts(): Result<List<PriceAlert>> {
        return try {
            val alerts = priceAlertQueries.selectAll().executeAsList()
            Result.Success(alerts.map { it.toPriceAlert() })
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override fun observeAllAlerts(): Flow<List<PriceAlert>> {
        return priceAlertQueries.selectAll()
            .asFlow()
            .mapToList(kotlinx.coroutines.Dispatchers.Default)
            .map { alerts ->
                alerts.map { it.toPriceAlert() }
            }
    }
    
    override suspend fun updateAlert(alert: PriceAlert): Result<PriceAlert> {
        return try {
            val alertId = alert.id.toLongOrNull() ?: return Result.Failure(
                IllegalArgumentException("Invalid alert ID: ${alert.id}")
            )
            
            priceAlertQueries.update(
                asset_name = alert.assetName,
                contract_address = alert.contractAddress,
                alert_type = alert.alertType.name,
                target_price = alert.targetPrice,
                percentage_threshold = alert.percentageThreshold,
                is_enabled = if (alert.isEnabled) 1L else 0L,
                user_notes = alert.userNotes,
                webhook_url = alert.webhookUrl,
                repeat_interval = alert.repeatInterval.toLong(),
                id = alertId
            )
            
            val updatedAlert = priceAlertQueries.selectById(alertId).executeAsOne()
            Result.Success(updatedAlert.toPriceAlert())
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun deleteAlert(id: String): Result<Unit> {
        return try {
            val alertId = id.toLongOrNull() ?: return Result.Failure(
                IllegalArgumentException("Invalid alert ID: $id")
            )
            priceAlertQueries.deleteById(alertId)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun deleteAlerts(ids: List<String>): Result<Unit> {
        return try {
            val alertIds = ids.mapNotNull { it.toLongOrNull() }
            if (alertIds.isEmpty()) {
                return Result.Success(Unit)
            }
            database.transaction {
                alertIds.forEach { id ->
                    priceAlertQueries.deleteById(id)
                }
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun getEnabledAlerts(): Result<List<PriceAlert>> {
        return try {
            val alerts = priceAlertQueries.selectEnabled().executeAsList()
            Result.Success(alerts.map { it.toPriceAlert() })
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun getNotTriggeredAlerts(): Result<List<PriceAlert>> {
        return try {
            val alerts = priceAlertQueries.selectNotTriggered().executeAsList()
            Result.Success(alerts.map { it.toPriceAlert() })
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun getAlertsByAssetSymbol(assetSymbol: String): Result<List<PriceAlert>> {
        return try {
            val alerts = priceAlertQueries.selectByAssetSymbol(assetSymbol).executeAsList()
            Result.Success(alerts.map { it.toPriceAlert() })
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun getAlertsByChainType(chainType: ChainType): Result<List<PriceAlert>> {
        return try {
            val alerts = priceAlertQueries.selectByChainType(chainType.name).executeAsList()
            Result.Success(alerts.map { it.toPriceAlert() })
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun getAlertsByAlertType(alertType: AlertType): Result<List<PriceAlert>> {
        return try {
            val alerts = priceAlertQueries.selectByAlertType(alertType.name).executeAsList()
            Result.Success(alerts.map { it.toPriceAlert() })
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun searchAlerts(query: String): Result<List<PriceAlert>> {
        return try {
            val alerts = priceAlertQueries.searchAlerts(query, query, query).executeAsList()
            Result.Success(alerts.map { it.toPriceAlert() })
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun getAlertsWithFilter(filter: PriceAlertFilter): Result<List<PriceAlert>> {
        return try {
            val priceAlerts = when {
                filter.assetSymbol != null -> {
                    priceAlertQueries.selectByAssetSymbol(filter.assetSymbol).executeAsList()
                        .map { it.toPriceAlert() }
                }
                filter.chainType != null -> {
                    priceAlertQueries.selectByChainType(filter.chainType.name).executeAsList()
                        .map { it.toPriceAlert() }
                }
                filter.alertType != null -> {
                    priceAlertQueries.selectByAlertType(filter.alertType.name).executeAsList()
                        .map { it.toPriceAlert() }
                }
                filter.isEnabled == true -> {
                    priceAlertQueries.selectEnabled().executeAsList()
                        .map { it.toPriceAlert() }
                }
                filter.isTriggered == false -> {
                    priceAlertQueries.selectNotTriggered().executeAsList()
                        .map { it.toPriceAlert() }
                }
                !filter.searchQuery.isNullOrBlank() -> {
                    val query = filter.searchQuery
                    priceAlertQueries.searchAlerts(query, query, query).executeAsList()
                        .map { it.toPriceAlert() }
                }
                else -> {
                    priceAlertQueries.selectAll().executeAsList()
                        .map { it.toPriceAlert() }
                }
            }
            
            Result.Success(priceAlerts)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun getAlertsForMonitoring(maxAgeMs: Long): Result<List<PriceAlert>> {
        return try {
            val cutoffTime = Clock.System.now().toEpochMilliseconds() - maxAgeMs
            val alerts = priceAlertQueries.selectForMonitoring(cutoffTime).executeAsList()
            Result.Success(alerts.map { it.toPriceAlert() })
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun getNearTriggerAlerts(): Result<List<PriceAlert>> {
        return try {
            val alerts = priceAlertQueries.selectNearTrigger().executeAsList()
            Result.Success(alerts.map { it.toPriceAlert() })
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun updateCurrentPrice(id: String, price: Double): Result<Unit> {
        return try {
            val alertId = id.toLongOrNull() ?: return Result.Failure(
                IllegalArgumentException("Invalid alert ID: $id")
            )
            priceAlertQueries.updateCurrentPrice(price, alertId)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun updateCurrentPrices(priceUpdates: Map<String, Double>): Result<Unit> {
        return try {
            database.transaction {
                priceUpdates.forEach { (id, price) ->
                    val alertId = id.toLongOrNull()
                    if (alertId != null) {
                        priceAlertQueries.updateCurrentPrice(price, alertId)
                    }
                }
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun updateEnabledStatus(id: String, isEnabled: Boolean): Result<Unit> {
        return try {
            val alertId = id.toLongOrNull() ?: return Result.Failure(
                IllegalArgumentException("Invalid alert ID: $id")
            )
            priceAlertQueries.updateEnabledStatus(if (isEnabled) 1L else 0L, alertId)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun triggerAlert(id: String): Result<Unit> {
        return try {
            val alertId = id.toLongOrNull() ?: return Result.Failure(
                IllegalArgumentException("Invalid alert ID: $id")
            )
            priceAlertQueries.triggerAlert(alertId)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun markNotificationSent(id: String): Result<Unit> {
        return try {
            val alertId = id.toLongOrNull() ?: return Result.Failure(
                IllegalArgumentException("Invalid alert ID: $id")
            )
            priceAlertQueries.markNotificationSent(alertId)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun resetTriggerStatus(id: String): Result<Unit> {
        return try {
            val alertId = id.toLongOrNull() ?: return Result.Failure(
                IllegalArgumentException("Invalid alert ID: $id")
            )
            priceAlertQueries.resetTriggerStatus(alertId)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun updateLastCheckedTime(assetSymbol: String, chainType: ChainType, timestamp: Long): Result<Unit> {
        return try {
            priceAlertQueries.updateLastCheckedTime(timestamp, assetSymbol, chainType.name)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun isAlertExists(
        walletId: String,
        assetSymbol: String,
        chainType: ChainType,
        alertType: AlertType,
        targetPrice: Double
    ): Result<Boolean> {
        return try {
            val exists = priceAlertQueries.existsBySameConfig(
                wallet_id = walletId,
                asset_symbol = assetSymbol,
                chain_type = chainType.name,
                alert_type = alertType.name,
                target_price = targetPrice
            ).executeAsOne()
            Result.Success(exists)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun validateAlert(alert: PriceAlert): Result<Boolean> {
        return Result.Success(alert.isValid)
    }
    
    override suspend fun checkShouldTrigger(id: String, currentPrice: Double): Result<Boolean> {
        return try {
            val alertId = id.toLongOrNull() ?: return Result.Failure(
                IllegalArgumentException("Invalid alert ID: $id")
            )
            val alert = priceAlertQueries.selectById(alertId).executeAsOneOrNull()
                ?: return Result.Failure(NoSuchElementException("Price alert not found with ID: $id"))
            Result.Success(alert.toPriceAlert().shouldTrigger(currentPrice))
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun createAlerts(alerts: List<PriceAlert>): Result<Int> {
        return try {
            var count = 0
            database.transaction {
                alerts.forEach { alert ->
                    if (alert.walletId.isNotBlank() && alert.assetSymbol.isNotBlank() && alert.targetPrice > 0.0) {
                        priceAlertQueries.insert(
                            wallet_id = alert.walletId,
                            asset_symbol = alert.assetSymbol,
                            asset_name = alert.assetName,
                            contract_address = alert.contractAddress,
                            chain_type = alert.chainType.name,
                            chain_id = alert.chainId.toLong(),
                            alert_type = alert.alertType.name,
                            target_price = alert.targetPrice,
                            current_price = alert.currentPrice,
                            percentage_threshold = alert.percentageThreshold,
                            is_enabled = if (alert.isEnabled) 1L else 0L,
                            user_notes = alert.userNotes,
                            webhook_url = alert.webhookUrl,
                            repeat_interval = alert.repeatInterval.toLong()
                        )
                        count++
                    }
                }
            }
            Result.Success(count)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun deleteAlertsByAssetSymbol(assetSymbol: String): Result<Unit> {
        return try {
            priceAlertQueries.deleteByAssetSymbol(assetSymbol)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun deleteTriggeredAlerts(): Result<Unit> {
        return try {
            priceAlertQueries.deleteTriggered()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun deleteDisabledAlerts(): Result<Unit> {
        return try {
            priceAlertQueries.deleteDisabled()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun clearAllAlerts(): Result<Unit> {
        return try {
            priceAlertQueries.deleteAll()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun getStatistics(): Result<PriceAlertStatistics> {
        return try {
            val total = priceAlertQueries.countAll().executeAsOne().toInt()
            val enabled = priceAlertQueries.countEnabled().executeAsOne().toInt()
            val triggered = priceAlertQueries.countTriggered().executeAsOne().toInt()
            val nearTrigger = priceAlertQueries.selectNearTrigger().executeAsList().size
            
            val chainStats = priceAlertQueries.getChainStats().executeAsList().associate {
                ChainType.valueOf(it.chain_type) to it.COUNT.toInt()
            }
            
            val typeStats = AlertType.values().associateWith { type ->
                priceAlertQueries.countByAlertType(type.name).executeAsOne().toInt()
            }
            
            val assetSummaries = priceAlertQueries.getAssetStats().executeAsList().map {
                AssetAlertSummary(
                    assetSymbol = it.asset_symbol,
                    assetName = null,
                    totalAlerts = it.COUNT.toInt(),
                    enabledAlerts = (it.SUM ?: 0L).toInt(),
                    triggeredAlerts = (it.SUM_ ?: 0L).toInt(),
                    averageTargetPrice = null,
                    currentPrice = null
                )
            }
            
            val history = priceAlertQueries.getTriggerHistory(50L).executeAsList().map {
                TriggerHistory(
                    id = it.id.toString(),
                    assetSymbol = it.asset_symbol,
                    alertType = AlertType.valueOf(it.alert_type),
                    targetPrice = it.target_price,
                    triggeredPrice = it.current_price,
                    triggeredAt = it.last_triggered_at ?: 0L,
                    triggerCount = it.trigger_count.toInt(),
                    userNotes = it.user_notes
                )
            }
            
            Result.Success(
                PriceAlertStatistics(
                    totalAlerts = total,
                    enabledAlerts = enabled,
                    triggeredAlerts = triggered,
                    nearTriggerAlerts = nearTrigger,
                    alertsByChain = chainStats,
                    alertsByType = typeStats,
                    topAssets = assetSummaries,
                    recentTriggers = history,
                    averageTriggersPerAlert = if (total > 0) triggered.toDouble() / total else 0.0,
                    totalTriggerCount = triggered
                )
            )
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun getAssetStatistics(): Result<List<AssetAlertSummary>> {
        return try {
            val list = priceAlertQueries.getAssetStats().executeAsList().map {
                AssetAlertSummary(
                    assetSymbol = it.asset_symbol,
                    assetName = null,
                    totalAlerts = it.COUNT.toInt(),
                    enabledAlerts = (it.SUM ?: 0L).toInt(),
                    triggeredAlerts = (it.SUM_ ?: 0L).toInt(),
                    averageTargetPrice = null,
                    currentPrice = null
                )
            }
            Result.Success(list)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun getChainStatistics(): Result<Map<ChainType, Int>> {
        return try {
            val map = priceAlertQueries.getChainStats().executeAsList().associate {
                ChainType.valueOf(it.chain_type) to it.COUNT.toInt()
            }
            Result.Success(map)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun getTriggerHistory(limit: Int): Result<List<TriggerHistory>> {
        return try {
            val list = priceAlertQueries.getTriggerHistory(limit.toLong()).executeAsList().map {
                TriggerHistory(
                    id = it.id.toString(),
                    assetSymbol = it.asset_symbol,
                    alertType = AlertType.valueOf(it.alert_type),
                    targetPrice = it.target_price,
                    triggeredPrice = it.current_price,
                    triggeredAt = it.last_triggered_at ?: 0L,
                    triggerCount = it.trigger_count.toInt(),
                    userNotes = it.user_notes
                )
            }
            Result.Success(list)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun getAssetsToMonitor(): Result<List<String>> {
        return try {
            val list = priceAlertQueries.selectEnabled().executeAsList()
                .map { it.asset_symbol }
                .distinct()
            Result.Success(list)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun processPriceUpdate(assetSymbol: String, price: Double, chainType: ChainType): Result<List<PriceAlert>> {
        return try {
            val alerts = priceAlertQueries.selectByAssetSymbol(assetSymbol).executeAsList()
                .filter { it.chain_type == chainType.name && it.is_enabled == 1L }
                .map { it.toPriceAlert() }
            
            val triggeredList = mutableListOf<PriceAlert>()
            database.transaction {
                alerts.forEach { alert ->
                    val alertId = alert.id.toLong()
                    priceAlertQueries.updateCurrentPrice(price, alertId)
                    if (alert.shouldTrigger(price)) {
                        priceAlertQueries.triggerAlert(alertId)
                        triggeredList.add(alert.copy(isTriggered = true, currentPrice = price))
                    }
                }
            }
            Result.Success(triggeredList)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun cleanupExpiredTriggers(maxAgeMs: Long): Result<Int> {
        return Result.Failure(
            TypedUnsupportedOperationException("cleanupExpiredTriggers is unsupported without dedicated expiry index")
        )
    }
    
    override suspend fun resetStaleAlerts(maxAgeMs: Long): Result<Int> {
        return Result.Failure(
            TypedUnsupportedOperationException("resetStaleAlerts is unsupported without dedicated heartbeat tracking")
        )
    }
}

/**
 * 擴展函數：將數據庫 Price_alert 轉換為領域模型 PriceAlert
 */
private fun Price_alert.toPriceAlert(): PriceAlert {
    return PriceAlert(
        id = id.toString(),
        walletId = wallet_id,
        assetSymbol = asset_symbol,
        assetName = asset_name,
        contractAddress = contract_address,
        chainType = ChainType.valueOf(chain_type),
        chainId = chain_id.toInt(),
        alertType = AlertType.valueOf(alert_type),
        targetPrice = target_price,
        currentPrice = current_price,
        percentageThreshold = percentage_threshold,
        isEnabled = is_enabled != 0L,
        isTriggered = is_triggered != 0L,
        notificationSent = notification_sent != 0L,
        triggerCount = trigger_count.toInt(),
        lastTriggeredAt = last_triggered_at,
        lastCheckedAt = last_checked_at,
        createdAt = created_at,
        updatedAt = updated_at,
        userNotes = user_notes,
        webhookUrl = webhook_url,
        repeatInterval = repeat_interval?.toInt() ?: 0
    )
}

/**
 * 擴展函數：將數據庫 SelectNearTrigger 轉換為領域模型 PriceAlert
 */
private fun com.cbstudio.wearwallet.core.database.SelectNearTrigger.toPriceAlert(): PriceAlert {
    return PriceAlert(
        id = id.toString(),
        walletId = wallet_id,
        assetSymbol = asset_symbol,
        assetName = asset_name,
        contractAddress = contract_address,
        chainType = ChainType.valueOf(chain_type),
        chainId = chain_id.toInt(),
        alertType = AlertType.valueOf(alert_type),
        targetPrice = target_price,
        currentPrice = current_price,
        percentageThreshold = percentage_threshold,
        isEnabled = is_enabled != 0L,
        isTriggered = is_triggered != 0L,
        notificationSent = notification_sent != 0L,
        triggerCount = trigger_count.toInt(),
        lastTriggeredAt = last_triggered_at,
        lastCheckedAt = last_checked_at,
        createdAt = created_at,
        updatedAt = updated_at,
        userNotes = user_notes,
        webhookUrl = webhook_url,
        repeatInterval = repeat_interval?.toInt() ?: 0
    )
}
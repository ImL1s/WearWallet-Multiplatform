package com.cbstudio.wearwallet.core.domain.model.pricealert

import com.cbstudio.wearwallet.core.domain.model.ChainType
import kotlinx.serialization.Serializable

/**
 * 價格提醒領域模型
 */
@Serializable
data class PriceAlert(
    val id: String,
    val walletId: String,
    val assetSymbol: String,
    val assetName: String? = null,
    val contractAddress: String? = null,
    val chainType: ChainType,
    val chainId: Int,
    val alertType: AlertType,
    val targetPrice: Double,
    val currentPrice: Double? = null,
    val percentageThreshold: Double? = null,
    val isEnabled: Boolean = true,
    val isTriggered: Boolean = false,
    val notificationSent: Boolean = false,
    val triggerCount: Int = 0,
    val lastTriggeredAt: Long? = null,
    val lastCheckedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val userNotes: String? = null,
    val webhookUrl: String? = null,
    val repeatInterval: Int = 0 // 重複間隔（分鐘）
) {
    /**
     * 顯示用的資產名稱
     */
    val displayName: String
        get() = assetName?.takeIf { it.isNotBlank() } ?: assetSymbol
    
    /**
     * 是否為有效的提醒
     */
    val isValid: Boolean
        get() = walletId.isNotBlank() && assetSymbol.isNotBlank() && targetPrice > 0
    
    /**
     * 是否需要重複提醒
     */
    val shouldRepeat: Boolean
        get() = repeatInterval > 0
    
    /**
     * 是否接近觸發條件
     */
    val isNearTrigger: Boolean
        get() = currentPrice?.let { price ->
            when (alertType) {
                AlertType.ABOVE -> price > targetPrice * 0.95
                AlertType.BELOW -> price < targetPrice * 1.05
                AlertType.PERCENTAGE_CHANGE -> percentageThreshold?.let { threshold ->
                    kotlin.math.abs((price - targetPrice) / targetPrice * 100) > threshold * 0.8
                } ?: false
            }
        } ?: false
    
    /**
     * 是否應該觸發提醒
     */
    fun shouldTrigger(price: Double): Boolean {
        if (!isEnabled || (isTriggered && !shouldRepeat)) return false
        
        return when (alertType) {
            AlertType.ABOVE -> price >= targetPrice
            AlertType.BELOW -> price <= targetPrice
            AlertType.PERCENTAGE_CHANGE -> {
                percentageThreshold?.let { threshold ->
                    val changePercent = kotlin.math.abs((price - targetPrice) / targetPrice * 100)
                    changePercent >= threshold
                } ?: false
            }
        }
    }
    
    /**
     * 計算價格變化百分比
     */
    fun calculatePriceChangePercent(): Double? {
        return currentPrice?.let { price ->
            ((price - targetPrice) / targetPrice) * 100
        }
    }
    
    /**
     * 獲取提醒狀態描述
     */
    val statusDescription: String
        get() = when {
            !isEnabled -> "已停用"
            isTriggered && !shouldRepeat -> "已觸發"
            isNearTrigger -> "即將觸發"
            else -> "監控中"
        }
}

/**
 * 提醒類型
 */
enum class AlertType {
    ABOVE,              // 價格高於目標價格
    BELOW,              // 價格低於目標價格
    PERCENTAGE_CHANGE   // 價格變化百分比
}

/**
 * 價格提醒篩選條件
 */
@Serializable
data class PriceAlertFilter(
    val assetSymbol: String? = null,
    val chainType: ChainType? = null,
    val alertType: AlertType? = null,
    val isEnabled: Boolean? = null,
    val isTriggered: Boolean? = null,
    val isNearTrigger: Boolean? = null,
    val searchQuery: String? = null,
    val sortBy: AlertSortBy = AlertSortBy.CREATED_DATE,
    val sortOrder: SortOrder = SortOrder.DESC,
    val limit: Int? = null
)

/**
 * 提醒排序方式
 */
enum class AlertSortBy {
    CREATED_DATE,
    UPDATED_DATE,
    ASSET_SYMBOL,
    TARGET_PRICE,
    CURRENT_PRICE,
    LAST_TRIGGERED,
    TRIGGER_COUNT
}

/**
 * 排序順序
 */
enum class SortOrder {
    ASC,
    DESC
}

/**
 * 價格提醒統計資訊
 */
@Serializable
data class PriceAlertStatistics(
    val totalAlerts: Int,
    val enabledAlerts: Int,
    val triggeredAlerts: Int,
    val nearTriggerAlerts: Int,
    val alertsByChain: Map<ChainType, Int>,
    val alertsByType: Map<AlertType, Int>,
    val topAssets: List<AssetAlertSummary>,
    val recentTriggers: List<TriggerHistory>,
    val averageTriggersPerAlert: Double,
    val totalTriggerCount: Int
)

/**
 * 資產提醒摘要
 */
@Serializable
data class AssetAlertSummary(
    val assetSymbol: String,
    val assetName: String?,
    val totalAlerts: Int,
    val enabledAlerts: Int,
    val triggeredAlerts: Int,
    val averageTargetPrice: Double?,
    val currentPrice: Double?
)

/**
 * 觸發歷史記錄
 */
@Serializable
data class TriggerHistory(
    val id: String,
    val assetSymbol: String,
    val alertType: AlertType,
    val targetPrice: Double,
    val triggeredPrice: Double?,
    val triggeredAt: Long,
    val triggerCount: Int,
    val userNotes: String?
)

/**
 * 價格提醒創建請求
 */
@Serializable
data class CreatePriceAlertRequest(
    val walletId: String,
    val assetSymbol: String,
    val assetName: String? = null,
    val contractAddress: String? = null,
    val chainType: ChainType,
    val chainId: Int = 1,
    val alertType: AlertType,
    val targetPrice: Double,
    val percentageThreshold: Double? = null,
    val userNotes: String? = null,
    val webhookUrl: String? = null,
    val repeatInterval: Int = 0
) {
    /**
     * 轉換為 PriceAlert 領域模型
     */
    fun toPriceAlert(id: String, currentTime: Long): PriceAlert {
        return PriceAlert(
            id = id,
            walletId = walletId,
            assetSymbol = assetSymbol,
            assetName = assetName,
            contractAddress = contractAddress,
            chainType = chainType,
            chainId = chainId,
            alertType = alertType,
            targetPrice = targetPrice,
            percentageThreshold = percentageThreshold,
            userNotes = userNotes,
            webhookUrl = webhookUrl,
            repeatInterval = repeatInterval,
            createdAt = currentTime,
            updatedAt = currentTime
        )
    }
}

/**
 * 價格提醒更新請求
 */
@Serializable
data class UpdatePriceAlertRequest(
    val assetName: String? = null,
    val contractAddress: String? = null,
    val alertType: AlertType? = null,
    val targetPrice: Double? = null,
    val percentageThreshold: Double? = null,
    val isEnabled: Boolean? = null,
    val userNotes: String? = null,
    val webhookUrl: String? = null,
    val repeatInterval: Int? = null
)
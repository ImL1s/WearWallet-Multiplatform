package com.cbstudio.wearwallet.core.domain.model.notification

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 通知歷史記錄 - coreKmp 版本
 */
data class NotificationHistory(
    val id: String,
    val walletId: String,
    val notificationType: NotificationType,
    val title: String,
    val body: String,
    val data: String,
    val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
    val read: Boolean = false,
    val pushNotificationId: String? = null
) {
    /**
     * 通知類型枚舉
     */
    enum class NotificationType {
        PRICE_ALERT,      // 價格提醒
        TRANSACTION,      // 交易通知
        SECURITY,         // 安全警告
        DEFI             // DeFi 相關通知
    }
    
    /**
     * 獲取時間戳的 Instant 表示
     */
    fun getTimestampInstant(): Instant = Instant.fromEpochMilliseconds(timestamp)
    
    /**
     * 解析 JSON 數據為指定類型
     */
    inline fun <reified T> parseData(): T? {
        return try {
            Json.decodeFromString<T>(data)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 獲取通知的簡短描述
     */
    fun getShortDescription(): String {
        return when (notificationType) {
            NotificationType.PRICE_ALERT -> "Price Alert: $title"
            NotificationType.TRANSACTION -> "Transaction: $title"
            NotificationType.SECURITY -> "Security Alert: $title"
            NotificationType.DEFI -> "DeFi: $title"
        }
    }
    
    /**
     * 檢查通知是否已過期（超過指定天數）
     */
    fun isExpired(days: Int): Boolean {
        val expirationTime = Clock.System.now().toEpochMilliseconds() - (days * 24 * 60 * 60 * 1000L)
        return timestamp < expirationTime
    }
    
    companion object {
        /**
         * 創建新的通知歷史記錄
         */
        fun create(
            walletId: String,
            notificationType: NotificationType,
            title: String,
            body: String,
            data: Any? = null,
            pushNotificationId: String? = null
        ): NotificationHistory {
            val jsonData = data?.let { 
                Json.encodeToString(kotlinx.serialization.serializer(), it) 
            } ?: "{}"
            
            return NotificationHistory(
                id = "notif_${Clock.System.now().toEpochMilliseconds()}_${(0..9999).random()}",
                walletId = walletId,
                notificationType = notificationType,
                title = title,
                body = body,
                data = jsonData,
                pushNotificationId = pushNotificationId
            )
        }
        
        /**
         * 創建價格提醒通知
         */
        fun createPriceAlert(
            walletId: String,
            token: String,
            condition: String,
            currentPrice: Double
        ): NotificationHistory {
            return create(
                walletId = walletId,
                notificationType = NotificationType.PRICE_ALERT,
                title = "$token Price Alert",
                body = "$token has $condition. Current price: $${formatPrice(currentPrice)}",
                data = mapOf(
                    "token" to token,
                    "condition" to condition,
                    "price" to currentPrice
                )
            )
        }
        
        /**
         * 創建交易通知
         */
        fun createTransactionNotification(
            walletId: String,
            token: String,
            txHash: String,
            amount: String,
            isIncoming: Boolean
        ): NotificationHistory {
            val direction = if (isIncoming) "Received" else "Sent"
            return create(
                walletId = walletId,
                notificationType = NotificationType.TRANSACTION,
                title = "$direction $token",
                body = "$direction $amount $token",
                data = mapOf(
                    "txHash" to txHash,
                    "amount" to amount,
                    "token" to token,
                    "isIncoming" to isIncoming
                )
            )
        }
        
        /**
         * 創建安全警告
         */
        fun createSecurityAlert(
            walletId: String,
            alertType: String,
            details: String
        ): NotificationHistory {
            return create(
                walletId = walletId,
                notificationType = NotificationType.SECURITY,
                title = "Security Alert: $alertType",
                body = details,
                data = mapOf(
                    "alertType" to alertType,
                    "details" to details,
                    "severity" to "HIGH"
                )
            )
        }
        
        /**
         * 創建 DeFi 通知
         */
        fun createDeFiNotification(
            walletId: String,
            protocol: String,
            action: String,
            details: String
        ): NotificationHistory {
            return create(
                walletId = walletId,
                notificationType = NotificationType.DEFI,
                title = "$protocol: $action",
                body = details,
                data = mapOf(
                    "protocol" to protocol,
                    "action" to action,
                    "details" to details
                )
            )
        }
        
        private fun formatPrice(price: Double): String {
            return when {
                price >= 1000 -> price.toInt().toString()
                price >= 1 -> price.toFixed(2)
                else -> price.toFixed(6)
            }
        }
        
        private fun Double.toFixed(decimals: Int): String {
            var factor = 1.0
            repeat(decimals) { factor *= 10.0 }
            val rounded = kotlin.math.round(this * factor) / factor
            val str = rounded.toString()
            val dotIndex = str.indexOf('.')
            return if (dotIndex == -1) {
                "$str.${"0".repeat(decimals)}"
            } else {
                val currentDecimals = str.length - dotIndex - 1
                when {
                    currentDecimals < decimals -> str + "0".repeat(decimals - currentDecimals)
                    currentDecimals > decimals -> str.substring(0, dotIndex + decimals + 1)
                    else -> str
                }
            }
        }
    }
}

/**
 * 通知數據的具體類型定義
 */
@Serializable
data class PriceAlertData(
    val token: String,
    val condition: String,
    val price: Double
)

@Serializable
data class TransactionData(
    val txHash: String,
    val amount: String,
    val token: String,
    val isIncoming: Boolean
)

@Serializable
data class SecurityAlertData(
    val alertType: String,
    val details: String,
    val severity: String
)

@Serializable
data class DeFiNotificationData(
    val protocol: String,
    val action: String,
    val details: String
)
package com.cbstudio.wearwallet.core.domain.model.notification

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * 通知偏好設定 - coreKmp 版本
 */
data class NotificationPreferences(
    val walletId: String,
    val pushEnabled: Boolean = false,
    val priceAlertsEnabled: Boolean = true,
    val transactionAlertsEnabled: Boolean = true,
    val securityAlertsEnabled: Boolean = true,
    val defiAlertsEnabled: Boolean = false,
    val minimumTransactionAmount: String? = null,  // BigDecimal as String
    val quietHoursStart: Int? = null,              // 0-23
    val quietHoursEnd: Int? = null,                // 0-23
    val vibrationEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val lastUpdatedAt: Long = Clock.System.now().toEpochMilliseconds()
) {
    /**
     * 檢查特定類型的通知是否啟用
     */
    fun isAlertTypeEnabled(type: NotificationHistory.NotificationType): Boolean {
        return when (type) {
            NotificationHistory.NotificationType.PRICE_ALERT -> priceAlertsEnabled
            NotificationHistory.NotificationType.TRANSACTION -> transactionAlertsEnabled
            NotificationHistory.NotificationType.SECURITY -> securityAlertsEnabled
            NotificationHistory.NotificationType.DEFI -> defiAlertsEnabled
        }
    }
    
    /**
     * 檢查當前時間是否在靜音時段內
     */
    fun isInQuietHours(currentHour: Int): Boolean {
        if (quietHoursStart == null || quietHoursEnd == null) return false
        
        return if (quietHoursStart < quietHoursEnd) {
            // 正常時段，如 22:00 - 07:00 不跨午夜
            currentHour in quietHoursStart..quietHoursEnd
        } else {
            // 跨午夜時段，如 22:00 - 07:00
            currentHour >= quietHoursStart || currentHour <= quietHoursEnd
        }
    }
    
    /**
     * 檢查交易金額是否達到通知門檻
     */
    fun shouldNotifyForAmount(amount: String): Boolean {
        val minAmount = minimumTransactionAmount ?: return true
        return try {
            val transactionAmount = amount.toDouble()
            val threshold = minAmount.toDouble()
            transactionAmount >= threshold
        } catch (e: NumberFormatException) {
            true // 如果無法解析，預設通知
        }
    }
    
    /**
     * 獲取最後更新時間的 Instant 表示
     */
    fun getLastUpdatedInstant(): Instant = Instant.fromEpochMilliseconds(lastUpdatedAt)
    
    /**
     * 獲取靜音時段的描述文字
     */
    fun getQuietHoursDescription(): String? {
        if (quietHoursStart == null || quietHoursEnd == null) return null
        return "${formatHour(quietHoursStart)} - ${formatHour(quietHoursEnd)}"
    }
    
    /**
     * 合併另一個偏好設定（用於更新）
     */
    fun merge(update: NotificationPreferencesUpdate): NotificationPreferences {
        return copy(
            pushEnabled = update.pushEnabled ?: pushEnabled,
            priceAlertsEnabled = update.priceAlertsEnabled ?: priceAlertsEnabled,
            transactionAlertsEnabled = update.transactionAlertsEnabled ?: transactionAlertsEnabled,
            securityAlertsEnabled = update.securityAlertsEnabled ?: securityAlertsEnabled,
            defiAlertsEnabled = update.defiAlertsEnabled ?: defiAlertsEnabled,
            minimumTransactionAmount = update.minimumTransactionAmount ?: minimumTransactionAmount,
            quietHoursStart = update.quietHoursStart ?: quietHoursStart,
            quietHoursEnd = update.quietHoursEnd ?: quietHoursEnd,
            vibrationEnabled = update.vibrationEnabled ?: vibrationEnabled,
            soundEnabled = update.soundEnabled ?: soundEnabled,
            lastUpdatedAt = Clock.System.now().toEpochMilliseconds()
        )
    }
    
    companion object {
        /**
         * 創建預設的通知偏好設定
         */
        fun createDefault(walletId: String): NotificationPreferences {
            return NotificationPreferences(walletId = walletId)
        }
        
        /**
         * 創建全部啟用的通知偏好設定
         */
        fun createAllEnabled(walletId: String): NotificationPreferences {
            return NotificationPreferences(
                walletId = walletId,
                pushEnabled = true,
                priceAlertsEnabled = true,
                transactionAlertsEnabled = true,
                securityAlertsEnabled = true,
                defiAlertsEnabled = true,
                vibrationEnabled = true,
                soundEnabled = true
            )
        }
        
        /**
         * 創建全部停用的通知偏好設定
         */
        fun createAllDisabled(walletId: String): NotificationPreferences {
            return NotificationPreferences(
                walletId = walletId,
                pushEnabled = false,
                priceAlertsEnabled = false,
                transactionAlertsEnabled = false,
                securityAlertsEnabled = false,
                defiAlertsEnabled = false,
                vibrationEnabled = false,
                soundEnabled = false
            )
        }
        
        private fun formatHour(hour: Int): String {
            return "${hour.toString().padStart(2, '0')}:00"
        }
    }
}

/**
 * 部分更新通知偏好設定的資料類
 */
data class NotificationPreferencesUpdate(
    val pushEnabled: Boolean? = null,
    val priceAlertsEnabled: Boolean? = null,
    val transactionAlertsEnabled: Boolean? = null,
    val securityAlertsEnabled: Boolean? = null,
    val defiAlertsEnabled: Boolean? = null,
    val minimumTransactionAmount: String? = null,
    val quietHoursStart: Int? = null,
    val quietHoursEnd: Int? = null,
    val vibrationEnabled: Boolean? = null,
    val soundEnabled: Boolean? = null
)

/**
 * 通知頻道類型
 */
enum class NotificationChannel {
    PRICE_ALERTS,
    TRANSACTIONS,
    SECURITY,
    DEFI
}

/**
 * 通知優先級
 */
enum class NotificationPriority {
    LOW,
    NORMAL,
    HIGH,
    URGENT
}
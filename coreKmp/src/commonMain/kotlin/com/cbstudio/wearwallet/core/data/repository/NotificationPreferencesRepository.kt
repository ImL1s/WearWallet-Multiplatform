package com.cbstudio.wearwallet.core.data.repository

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.common.asResult
import com.cbstudio.wearwallet.core.database.CoreWalletDatabase
import com.cbstudio.wearwallet.core.domain.model.notification.NotificationHistory
import com.cbstudio.wearwallet.core.domain.model.notification.NotificationPreferences
import com.cbstudio.wearwallet.core.domain.model.notification.NotificationPreferencesUpdate
import kotlinx.datetime.Clock

/**
 * 通知偏好設定 Repository - coreKmp 實現
 */
class NotificationPreferencesRepository(
    private val database: CoreWalletDatabase
) {
    private val queries = database.notificationPreferencesQueries
    
    suspend fun getPreferences(walletId: String): Result<NotificationPreferences?> = asResult {
        queries.selectByWalletId(walletId)
            .executeAsOneOrNull()
            ?.toNotificationPreferences()
    }
    
    suspend fun hasPreferences(walletId: String): Result<Boolean> = asResult {
        queries.hasPreferences(walletId).executeAsOne()
    }
    
    suspend fun getWalletsWithPushEnabled(): Result<List<String>> = asResult {
        queries.selectWalletsWithPushEnabled().executeAsList()
    }
    
    suspend fun getWalletsWithAlertTypeEnabled(alertType: String): Result<List<String>> = asResult {
        queries.selectWalletsWithAlertType(alertType, alertType, alertType, alertType).executeAsList()
    }
    
    suspend fun isInQuietHours(walletId: String, currentHour: Int): Result<Boolean> = asResult {
        val result = queries.checkQuietHours(walletId).executeAsOneOrNull()
        result?.let {
            val startHour = it.quiet_hours_start?.toInt()
            val endHour = it.quiet_hours_end?.toInt()
            
            if (startHour == null || endHour == null) return@let false
            
            if (startHour < endHour) {
                // 正常時段，如 08:00 - 22:00
                currentHour in startHour..endHour
            } else {
                // 跨午夜時段，如 22:00 - 07:00
                currentHour >= startHour || currentHour <= endHour
            }
        } ?: false
    }
    
    suspend fun insertOrUpdatePreferences(preferences: NotificationPreferences): Result<Unit> = asResult {
        queries.insertOrUpdate(
            wallet_id = preferences.walletId,
            push_enabled = if (preferences.pushEnabled) 1L else 0L,
            price_alerts_enabled = if (preferences.priceAlertsEnabled) 1L else 0L,
            transaction_alerts_enabled = if (preferences.transactionAlertsEnabled) 1L else 0L,
            security_alerts_enabled = if (preferences.securityAlertsEnabled) 1L else 0L,
            defi_alerts_enabled = if (preferences.defiAlertsEnabled) 1L else 0L,
            minimum_transaction_amount = preferences.minimumTransactionAmount,
            quiet_hours_start = preferences.quietHoursStart?.toLong(),
            quiet_hours_end = preferences.quietHoursEnd?.toLong(),
            vibration_enabled = if (preferences.vibrationEnabled) 1L else 0L,
            sound_enabled = if (preferences.soundEnabled) 1L else 0L,
            last_updated_at = preferences.lastUpdatedAt
        )
    }
    
    suspend fun updatePushEnabled(walletId: String, enabled: Boolean): Result<Unit> = asResult {
        val now = Clock.System.now().toEpochMilliseconds()
        queries.updatePushEnabled(
            push_enabled = if (enabled) 1L else 0L,
            last_updated_at = now,
            wallet_id = walletId
        )
    }
    
    suspend fun updateAlertTypeEnabled(
        walletId: String, 
        alertType: String, 
        enabled: Boolean
    ): Result<Unit> = asResult {
        val now = Clock.System.now().toEpochMilliseconds()
        val enabledValue = if (enabled) 1L else 0L
        
        when (alertType) {
            "PRICE_ALERT" -> queries.updatePriceAlertsEnabled(enabledValue, now, walletId)
            "TRANSACTION" -> queries.updateTransactionAlertsEnabled(enabledValue, now, walletId)
            "SECURITY" -> queries.updateSecurityAlertsEnabled(enabledValue, now, walletId)
            "DEFI" -> queries.updateDefiAlertsEnabled(enabledValue, now, walletId)
        }
    }
    
    suspend fun updateMinimumTransactionAmount(
        walletId: String, 
        amount: String?
    ): Result<Unit> = asResult {
        val now = Clock.System.now().toEpochMilliseconds()
        queries.updateMinimumTransactionAmount(amount, now, walletId)
    }
    
    suspend fun updateQuietHours(
        walletId: String, 
        startHour: Int?, 
        endHour: Int?
    ): Result<Unit> = asResult {
        val now = Clock.System.now().toEpochMilliseconds()
        queries.updateQuietHours(startHour?.toLong(), endHour?.toLong(), now, walletId)
    }
    
    suspend fun updateVibrationEnabled(walletId: String, enabled: Boolean): Result<Unit> = asResult {
        val now = Clock.System.now().toEpochMilliseconds()
        queries.updateVibrationEnabled(if (enabled) 1L else 0L, now, walletId)
    }
    
    suspend fun updateSoundEnabled(walletId: String, enabled: Boolean): Result<Unit> = asResult {
        val now = Clock.System.now().toEpochMilliseconds()
        queries.updateSoundEnabled(if (enabled) 1L else 0L, now, walletId)
    }
    
    suspend fun resetToDefaults(walletId: String): Result<Unit> = asResult {
        val now = Clock.System.now().toEpochMilliseconds()
        queries.resetToDefaults(now, walletId)
    }
    
    suspend fun updatePreferences(preferences: NotificationPreferences): Result<NotificationPreferences> = asResult {
        insertOrUpdatePreferences(preferences)
        preferences
    }
    
    suspend fun deletePreferences(walletId: String): Result<Unit> = asResult {
        queries.deleteByWalletId(walletId)
    }
    
    // 擴展函數：將 SQLDelight 生成的類型轉換為 domain model
    private fun com.cbstudio.wearwallet.core.database.Notification_preferences.toNotificationPreferences(): NotificationPreferences {
        return NotificationPreferences(
            walletId = wallet_id,
            pushEnabled = push_enabled == 1L,
            priceAlertsEnabled = price_alerts_enabled == 1L,
            transactionAlertsEnabled = transaction_alerts_enabled == 1L,
            securityAlertsEnabled = security_alerts_enabled == 1L,
            defiAlertsEnabled = defi_alerts_enabled == 1L,
            minimumTransactionAmount = minimum_transaction_amount,
            quietHoursStart = quiet_hours_start?.toInt(),
            quietHoursEnd = quiet_hours_end?.toInt(),
            vibrationEnabled = vibration_enabled == 1L,
            soundEnabled = sound_enabled == 1L,
            lastUpdatedAt = last_updated_at
        )
    }
}

/**
 * Repository 介面定義
 */
interface INotificationPreferencesRepository {
    suspend fun getPreferences(walletId: String): Result<NotificationPreferences?>
    suspend fun hasPreferences(walletId: String): Result<Boolean>
    suspend fun getWalletsWithPushEnabled(): Result<List<String>>
    suspend fun getWalletsWithAlertTypeEnabled(alertType: String): Result<List<String>>
    suspend fun isInQuietHours(walletId: String, currentHour: Int): Result<Boolean>
    suspend fun insertOrUpdatePreferences(preferences: NotificationPreferences): Result<Unit>
    suspend fun updatePushEnabled(walletId: String, enabled: Boolean): Result<Unit>
    suspend fun updateAlertTypeEnabled(walletId: String, alertType: String, enabled: Boolean): Result<Unit>
    suspend fun updateMinimumTransactionAmount(walletId: String, amount: String?): Result<Unit>
    suspend fun updateQuietHours(walletId: String, startHour: Int?, endHour: Int?): Result<Unit>
    suspend fun updateVibrationEnabled(walletId: String, enabled: Boolean): Result<Unit>
    suspend fun updateSoundEnabled(walletId: String, enabled: Boolean): Result<Unit>
    suspend fun resetToDefaults(walletId: String): Result<Unit>
    suspend fun updatePreferences(walletId: String, update: NotificationPreferencesUpdate): Result<Unit>
    suspend fun deletePreferences(walletId: String): Result<Unit>
}
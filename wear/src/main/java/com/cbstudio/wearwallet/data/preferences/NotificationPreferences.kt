package com.cbstudio.wearwallet.data.preferences

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 通知偏好設定資料類別
 */
data class NotificationPreferences(
    val priceAlertsEnabled: Boolean = true,
    val transactionNotificationsEnabled: Boolean = true,
    val securityAlertsEnabled: Boolean = true,
    val defiNotificationsEnabled: Boolean = false,
    val announcementsEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val soundEnabled: Boolean = false // Wear OS 通常使用振動而非聲音
)

/**
 * 通知偏好設定管理擴展
 * 用於 UserPreferencesManager
 */
class NotificationPreferencesManager(
    private val prefs: android.content.SharedPreferences
) {
    companion object {
        private const val KEY_PRICE_ALERTS_ENABLED = "price_alerts_enabled"
        private const val KEY_TRANSACTION_NOTIFICATIONS_ENABLED = "transaction_notifications_enabled"
        private const val KEY_SECURITY_ALERTS_ENABLED = "security_alerts_enabled"
        private const val KEY_DEFI_NOTIFICATIONS_ENABLED = "defi_notifications_enabled"
        private const val KEY_ANNOUNCEMENTS_ENABLED = "announcements_enabled"
        private const val KEY_VIBRATION_ENABLED = "vibration_enabled"
        private const val KEY_SOUND_ENABLED = "sound_enabled"
    }
    
    private val _notificationPreferences = MutableStateFlow(loadPreferences())
    val notificationPreferences: StateFlow<NotificationPreferences> = _notificationPreferences.asStateFlow()
    
    /**
     * 載入偏好設定
     */
    private fun loadPreferences(): NotificationPreferences {
        return NotificationPreferences(
            priceAlertsEnabled = prefs.getBoolean(KEY_PRICE_ALERTS_ENABLED, true),
            transactionNotificationsEnabled = prefs.getBoolean(KEY_TRANSACTION_NOTIFICATIONS_ENABLED, true),
            securityAlertsEnabled = prefs.getBoolean(KEY_SECURITY_ALERTS_ENABLED, true),
            defiNotificationsEnabled = prefs.getBoolean(KEY_DEFI_NOTIFICATIONS_ENABLED, false),
            announcementsEnabled = prefs.getBoolean(KEY_ANNOUNCEMENTS_ENABLED, true),
            vibrationEnabled = prefs.getBoolean(KEY_VIBRATION_ENABLED, true),
            soundEnabled = prefs.getBoolean(KEY_SOUND_ENABLED, false)
        )
    }
    
    /**
     * 更新價格提醒設定
     */
    fun updatePriceAlertsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PRICE_ALERTS_ENABLED, enabled).apply()
        _notificationPreferences.value = _notificationPreferences.value.copy(priceAlertsEnabled = enabled)
    }
    
    /**
     * 更新交易通知設定
     */
    fun updateTransactionNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TRANSACTION_NOTIFICATIONS_ENABLED, enabled).apply()
        _notificationPreferences.value = _notificationPreferences.value.copy(transactionNotificationsEnabled = enabled)
    }
    
    /**
     * 更新安全警報設定
     */
    fun updateSecurityAlertsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SECURITY_ALERTS_ENABLED, enabled).apply()
        _notificationPreferences.value = _notificationPreferences.value.copy(securityAlertsEnabled = enabled)
    }
    
    /**
     * 更新 DeFi 通知設定
     */
    fun updateDeFiNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DEFI_NOTIFICATIONS_ENABLED, enabled).apply()
        _notificationPreferences.value = _notificationPreferences.value.copy(defiNotificationsEnabled = enabled)
    }
    
    /**
     * 更新公告通知設定
     */
    fun updateAnnouncementsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ANNOUNCEMENTS_ENABLED, enabled).apply()
        _notificationPreferences.value = _notificationPreferences.value.copy(announcementsEnabled = enabled)
    }
    
    /**
     * 更新振動設定
     */
    fun updateVibrationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VIBRATION_ENABLED, enabled).apply()
        _notificationPreferences.value = _notificationPreferences.value.copy(vibrationEnabled = enabled)
    }
    
    /**
     * 更新聲音設定
     */
    fun updateSoundEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SOUND_ENABLED, enabled).apply()
        _notificationPreferences.value = _notificationPreferences.value.copy(soundEnabled = enabled)
    }
    
    /**
     * 重置為預設值
     */
    fun resetToDefaults() {
        val defaults = NotificationPreferences()
        prefs.edit().apply {
            putBoolean(KEY_PRICE_ALERTS_ENABLED, defaults.priceAlertsEnabled)
            putBoolean(KEY_TRANSACTION_NOTIFICATIONS_ENABLED, defaults.transactionNotificationsEnabled)
            putBoolean(KEY_SECURITY_ALERTS_ENABLED, defaults.securityAlertsEnabled)
            putBoolean(KEY_DEFI_NOTIFICATIONS_ENABLED, defaults.defiNotificationsEnabled)
            putBoolean(KEY_ANNOUNCEMENTS_ENABLED, defaults.announcementsEnabled)
            putBoolean(KEY_VIBRATION_ENABLED, defaults.vibrationEnabled)
            putBoolean(KEY_SOUND_ENABLED, defaults.soundEnabled)
            apply()
        }
        _notificationPreferences.value = defaults
    }
}

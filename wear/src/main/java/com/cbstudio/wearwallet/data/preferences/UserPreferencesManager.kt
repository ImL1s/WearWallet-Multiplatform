package com.cbstudio.wearwallet.data.preferences

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.StateFlow
import java.util.*

// Use a local placeholder enum to avoid unresolved import during stubbing phase
enum class Chain { BITCOIN, ETHEREUM }
// Hilt annotations removed in pure Koin setup

/**
 * 管理用戶偏好設定
 */

class UserPreferencesManager(
    private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )
    
    // 通知偏好設定管理器
    private val notificationPrefsManager = NotificationPreferencesManager(prefs)
    
    // 公開通知偏好設定的 Flow
    val notificationPreferences: StateFlow<NotificationPreferences> = notificationPrefsManager.notificationPreferences
    
    companion object {
        private const val PREFS_NAME = "wear_wallet_prefs"
        private const val KEY_USER_DISPLAY_NAME = "user_display_name"
        private const val KEY_JOIN_DATE = "join_date"
        private const val KEY_DEFAULT_CHAIN = "default_chain"
        private const val KEY_PREFERRED_CURRENCY = "preferred_currency"
        private const val KEY_THEME = "theme"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        private const val KEY_HIDE_SMALL_BALANCES = "hide_small_balances"
        private const val KEY_SMALL_BALANCE_THRESHOLD = "small_balance_threshold"
    }
    
    // 用戶顯示名稱
    fun getUserDisplayName(): String? = prefs.getString(KEY_USER_DISPLAY_NAME, null)
    
    fun setUserDisplayName(name: String) {
        prefs.edit().putString(KEY_USER_DISPLAY_NAME, name).apply()
    }
    
    // 加入日期
    fun getJoinDate(): Date? {
        val timestamp = prefs.getLong(KEY_JOIN_DATE, 0)
        return if (timestamp > 0) Date(timestamp) else null
    }
    
    fun setJoinDate(date: Date) {
        prefs.edit().putLong(KEY_JOIN_DATE, date.time).apply()
    }
    
    // 預設鏈
    fun getDefaultChain(): Chain? {
        val chainName = prefs.getString(KEY_DEFAULT_CHAIN, null) ?: return null
        return try {
            // 使用 valueOf 來根據名稱查找 enum
            Chain.valueOf(chainName)
        } catch (e: Exception) {
            // 如果名稱查找失敗，使用默認的 Ethereum
            Chain.ETHEREUM
        }
    }
    
    fun setDefaultChain(chain: Chain) {
        prefs.edit().putString(KEY_DEFAULT_CHAIN, chain.name).apply()
    }
    
    // 偏好貨幣
    fun getPreferredCurrency(): String = prefs.getString(KEY_PREFERRED_CURRENCY, "USD") ?: "USD"
    
    fun setPreferredCurrency(currency: String) {
        prefs.edit().putString(KEY_PREFERRED_CURRENCY, currency).apply()
    }
    
    // 主題
    fun getTheme(): String = prefs.getString(KEY_THEME, "dark") ?: "dark"
    
    fun setTheme(theme: String) {
        prefs.edit().putString(KEY_THEME, theme).apply()
    }
    
    // 生物識別
    fun isBiometricEnabled(): Boolean = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
    
    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }
    
    // 通知
    fun isNotificationsEnabled(): Boolean = prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true)
    
    fun setNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled).apply()
    }
    
    // 隱藏小額餘額
    fun isHideSmallBalances(): Boolean = prefs.getBoolean(KEY_HIDE_SMALL_BALANCES, false)
    
    fun setHideSmallBalances(hide: Boolean) {
        prefs.edit().putBoolean(KEY_HIDE_SMALL_BALANCES, hide).apply()
    }
    
    // 小額餘額閾值
    fun getSmallBalanceThreshold(): Float = prefs.getFloat(KEY_SMALL_BALANCE_THRESHOLD, 1.0f)
    
    fun setSmallBalanceThreshold(threshold: Float) {
        prefs.edit().putFloat(KEY_SMALL_BALANCE_THRESHOLD, threshold).apply()
    }
    
    // 清除所有偏好設定
    fun clearAll() {
        prefs.edit().clear().apply()
    }
    
    // 初始化新用戶
    fun initializeNewUser() {
        if (getJoinDate() == null) {
            setJoinDate(Date())
        }
        if (getUserDisplayName() == null) {
            setUserDisplayName("Wallet User")
        }
    }
    
    // 通知偏好設定委派方法
    fun updatePriceAlertsEnabled(enabled: Boolean) = notificationPrefsManager.updatePriceAlertsEnabled(enabled)
    fun updateTransactionNotificationsEnabled(enabled: Boolean) = notificationPrefsManager.updateTransactionNotificationsEnabled(enabled)
    fun updateSecurityAlertsEnabled(enabled: Boolean) = notificationPrefsManager.updateSecurityAlertsEnabled(enabled)
    fun updateDeFiNotificationsEnabled(enabled: Boolean) = notificationPrefsManager.updateDeFiNotificationsEnabled(enabled)
    fun updateAnnouncementsEnabled(enabled: Boolean) = notificationPrefsManager.updateAnnouncementsEnabled(enabled)
    fun updateVibrationEnabled(enabled: Boolean) = notificationPrefsManager.updateVibrationEnabled(enabled)
    fun updateSoundEnabled(enabled: Boolean) = notificationPrefsManager.updateSoundEnabled(enabled)
    fun resetNotificationPreferences() = notificationPrefsManager.resetToDefaults()
}

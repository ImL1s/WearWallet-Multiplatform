package com.cbstudio.wearwallet.config

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.cbstudio.wearwallet.BuildConfig
import com.cbstudio.wearwallet.R
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Push Protocol 配置管理器
 * 
 * 管理 Push Protocol 通知系統的配置和密鑰
 * 使用加密存儲保護敏感資訊
 * 
 * 功能：
 * 1. Push Protocol 頻道密鑰管理
 * 2. 功能開關控制
 * 3. 通知類型配置
 * 4. 頻道狀態追蹤
 */

class PushProtocolConfig : KoinComponent {
    
    private val context: Context by inject<Context>()
    
    companion object {
        private const val PREFS_NAME = "push_protocol_config"
        
        // 配置鍵值
        private const val KEY_ENABLED = "push_protocol_enabled"
        private const val KEY_CHANNEL_CREATED = "channel_created"
        private const val KEY_CHANNEL_ADDRESS = "channel_address"
        private const val KEY_CHANNEL_PRIVATE_KEY = "channel_private_key"
        private const val KEY_SUBSCRIBER_COUNT = "subscriber_count"
        private const val KEY_LAST_SYNC_TIME = "last_sync_time"
        
        // 通知類型開關
        private const val KEY_PRICE_ALERTS_ENABLED = "price_alerts_enabled"
        private const val KEY_TRANSACTION_ALERTS_ENABLED = "transaction_alerts_enabled"
        private const val KEY_SECURITY_ALERTS_ENABLED = "security_alerts_enabled"
        private const val KEY_DEFI_ALERTS_ENABLED = "defi_alerts_enabled"
        private const val KEY_ANNOUNCEMENT_ALERTS_ENABLED = "announcement_alerts_enabled"
        
        // 預設值
        const val DEFAULT_CHANNEL_ADDRESS = "0x0000000000000000000000000000000000000000"
        const val PUSH_TOKEN_REQUIREMENT = 50 // 創建頻道需要的 PUSH tokens
        
        // Push Protocol 網絡配置
        const val PUSH_MAINNET_URL = "https://backend.epns.io/apis"
        const val PUSH_STAGING_URL = "https://backend-staging.epns.io/apis"
        const val PUSH_DEV_URL = "http://localhost:3000/apis"
    }
    
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    private val encryptedPrefs = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    /**
     * 檢查 Push Protocol 是否啟用
     */
    fun isPushProtocolEnabled(): Boolean {
        // 在 DEBUG 模式下預設啟用
        return if (BuildConfig.DEBUG) {
            encryptedPrefs.getBoolean(KEY_ENABLED, true)
        } else {
            encryptedPrefs.getBoolean(KEY_ENABLED, false)
        }
    }
    
    /**
     * 啟用/禁用 Push Protocol
     */
    fun setPushProtocolEnabled(enabled: Boolean) {
        encryptedPrefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }
    
    /**
     * 檢查頻道是否已創建
     */
    fun isChannelCreated(): Boolean {
        return encryptedPrefs.getBoolean(KEY_CHANNEL_CREATED, false)
    }
    
    /**
     * 標記頻道已創建
     */
    fun markChannelCreated(channelAddress: String, privateKey: String) {
        encryptedPrefs.edit().apply {
            putBoolean(KEY_CHANNEL_CREATED, true)
            putString(KEY_CHANNEL_ADDRESS, channelAddress)
            putString(KEY_CHANNEL_PRIVATE_KEY, privateKey)
            putLong(KEY_LAST_SYNC_TIME, System.currentTimeMillis())
            apply()
        }
    }
    
    /**
     * 獲取頻道地址
     */
    fun getChannelAddress(): String? {
        return encryptedPrefs.getString(KEY_CHANNEL_ADDRESS, null)
    }
    
    /**
     * 獲取頻道私鑰（敏感資訊）
     */
    fun getChannelPrivateKey(): String? {
        return encryptedPrefs.getString(KEY_CHANNEL_PRIVATE_KEY, null)
    }
    
    /**
     * 更新訂閱者數量
     */
    fun updateSubscriberCount(count: Int) {
        encryptedPrefs.edit().putInt(KEY_SUBSCRIBER_COUNT, count).apply()
    }
    
    /**
     * 獲取訂閱者數量
     */
    fun getSubscriberCount(): Int {
        return encryptedPrefs.getInt(KEY_SUBSCRIBER_COUNT, 0)
    }
    
    /**
     * 檢查價格提醒是否啟用
     */
    fun isPriceAlertsEnabled(): Boolean {
        return encryptedPrefs.getBoolean(KEY_PRICE_ALERTS_ENABLED, true)
    }
    
    /**
     * 設置價格提醒開關
     */
    fun setPriceAlertsEnabled(enabled: Boolean) {
        encryptedPrefs.edit().putBoolean(KEY_PRICE_ALERTS_ENABLED, enabled).apply()
    }
    
    /**
     * 檢查交易提醒是否啟用
     */
    fun isTransactionAlertsEnabled(): Boolean {
        return encryptedPrefs.getBoolean(KEY_TRANSACTION_ALERTS_ENABLED, true)
    }
    
    /**
     * 設置交易提醒開關
     */
    fun setTransactionAlertsEnabled(enabled: Boolean) {
        encryptedPrefs.edit().putBoolean(KEY_TRANSACTION_ALERTS_ENABLED, enabled).apply()
    }
    
    /**
     * 檢查安全提醒是否啟用
     */
    fun isSecurityAlertsEnabled(): Boolean {
        return encryptedPrefs.getBoolean(KEY_SECURITY_ALERTS_ENABLED, true)
    }
    
    /**
     * 設置安全提醒開關
     */
    fun setSecurityAlertsEnabled(enabled: Boolean) {
        encryptedPrefs.edit().putBoolean(KEY_SECURITY_ALERTS_ENABLED, enabled).apply()
    }
    
    /**
     * 檢查 DeFi 提醒是否啟用
     */
    fun isDefiAlertsEnabled(): Boolean {
        return encryptedPrefs.getBoolean(KEY_DEFI_ALERTS_ENABLED, true)
    }
    
    /**
     * 設置 DeFi 提醒開關
     */
    fun setDefiAlertsEnabled(enabled: Boolean) {
        encryptedPrefs.edit().putBoolean(KEY_DEFI_ALERTS_ENABLED, enabled).apply()
    }
    
    /**
     * 檢查公告提醒是否啟用
     */
    fun isAnnouncementAlertsEnabled(): Boolean {
        return encryptedPrefs.getBoolean(KEY_ANNOUNCEMENT_ALERTS_ENABLED, true)
    }
    
    /**
     * 設置公告提醒開關
     */
    fun setAnnouncementAlertsEnabled(enabled: Boolean) {
        encryptedPrefs.edit().putBoolean(KEY_ANNOUNCEMENT_ALERTS_ENABLED, enabled).apply()
    }
    
    /**
     * 獲取最後同步時間
     */
    fun getLastSyncTime(): Long {
        return encryptedPrefs.getLong(KEY_LAST_SYNC_TIME, 0)
    }
    
    /**
     * 更新最後同步時間
     */
    fun updateLastSyncTime() {
        encryptedPrefs.edit().putLong(KEY_LAST_SYNC_TIME, System.currentTimeMillis()).apply()
    }
    
    /**
     * 獲取 Push Protocol API URL
     */
    fun getPushProtocolApiUrl(): String {
        return when {
            BuildConfig.DEBUG -> PUSH_STAGING_URL
            BuildConfig.BUILD_TYPE == "staging" -> PUSH_STAGING_URL
            else -> PUSH_MAINNET_URL
        }
    }
    
    /**
     * 驗證配置狀態
     */
    fun validateConfiguration(): PushConfigStatus {
        return when {
            !isPushProtocolEnabled() -> {
                PushConfigStatus.Disabled(context.getString(R.string.push_status_disabled))
            }
            !isChannelCreated() -> {
                PushConfigStatus.NotConfigured(context.getString(R.string.push_status_not_configured))
            }
            getChannelAddress().isNullOrBlank() -> {
                PushConfigStatus.Invalid(context.getString(R.string.push_status_invalid_address))
            }
            getChannelPrivateKey().isNullOrBlank() -> {
                PushConfigStatus.Invalid(context.getString(R.string.push_status_missing_key))
            }
            else -> {
                PushConfigStatus.Ready(
                    channelAddress = getChannelAddress()!!,
                    subscriberCount = getSubscriberCount()
                )
            }
        }
    }
    
    /**
     * 導出配置（不包含敏感資訊）
     */
    fun exportConfiguration(): Map<String, Any> {
        return mapOf(
            "enabled" to isPushProtocolEnabled(),
            "channel_created" to isChannelCreated(),
            "channel_address" to (getChannelAddress() ?: ""),
            "subscriber_count" to getSubscriberCount(),
            "price_alerts" to isPriceAlertsEnabled(),
            "transaction_alerts" to isTransactionAlertsEnabled(),
            "security_alerts" to isSecurityAlertsEnabled(),
            "defi_alerts" to isDefiAlertsEnabled(),
            "announcement_alerts" to isAnnouncementAlertsEnabled(),
            "last_sync_time" to getLastSyncTime()
        )
    }
    
    /**
     * 重置配置
     */
    fun resetConfiguration() {
        encryptedPrefs.edit().clear().apply()
    }
    
    /**
     * 檢查是否需要同步
     */
    fun needsSync(): Boolean {
        val lastSync = getLastSyncTime()
        val now = System.currentTimeMillis()
        val hoursSinceLastSync = (now - lastSync) / (1000 * 60 * 60)
        return hoursSinceLastSync > 24 // 每24小時同步一次
    }
}

/**
 * Push Protocol 配置狀態
 */
sealed class PushConfigStatus {
    data class Ready(
        val channelAddress: String,
        val subscriberCount: Int
    ) : PushConfigStatus()
    
    data class NotConfigured(val message: String) : PushConfigStatus()
    data class Invalid(val reason: String) : PushConfigStatus()
    data class Disabled(val reason: String) : PushConfigStatus()
}

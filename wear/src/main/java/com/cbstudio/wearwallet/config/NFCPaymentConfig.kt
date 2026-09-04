package com.cbstudio.wearwallet.config

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.cbstudio.wearwallet.BuildConfig
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * NFC 支付配置管理器
 * 
 * 管理 Flexa API 密鑰和其他 NFC 支付相關配置
 * 使用 EncryptedSharedPreferences 確保敏感資訊安全
 * 
 * 功能：
 * 1. API 密鑰的安全存儲和讀取
 * 2. 環境切換（生產/沙箱）
 * 3. 支付限額配置
 * 4. 功能開關管理
 */

class NFCPaymentConfig(
    private val context: Context
) {
    
    companion object {
        private const val PREFS_NAME = "nfc_payment_config"
        private const val KEY_FLEXA_API_KEY = "flexa_api_key"
        private const val KEY_FLEXA_ENVIRONMENT = "flexa_environment"
        private const val KEY_NFC_ENABLED = "nfc_enabled"
        private const val KEY_PAYMENT_MIN_AMOUNT = "payment_min_amount"
        private const val KEY_PAYMENT_MAX_AMOUNT = "payment_max_amount"
        private const val KEY_DAILY_LIMIT = "daily_limit"
        private const val KEY_REQUIRE_BIOMETRIC = "require_biometric"
        private const val KEY_AUTO_CONFIRM_THRESHOLD = "auto_confirm_threshold"
        
        // 預設值
        const val DEFAULT_MIN_AMOUNT = "0.01"
        const val DEFAULT_MAX_AMOUNT = "1000.00"
        const val DEFAULT_DAILY_LIMIT = "5000.00"
        const val DEFAULT_AUTO_CONFIRM_THRESHOLD = "10.00"
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
     * 獲取 Flexa API 密鑰
     */
    fun getFlexaApiKey(): String? {
        return if (BuildConfig.DEBUG) {
            // 開發環境：從加密存儲讀取（未來可從 BuildConfig 讀取）
            encryptedPrefs.getString(KEY_FLEXA_API_KEY, "test_flexa_api_key")
        } else {
            // 生產環境：從加密存儲讀取
            encryptedPrefs.getString(KEY_FLEXA_API_KEY, null)
        }
    }
    
    /**
     * 設置 Flexa API 密鑰
     * 注意：應該通過安全的方式設置，例如從服務器獲取或通過安全配置
     */
    fun setFlexaApiKey(apiKey: String) {
        encryptedPrefs.edit().putString(KEY_FLEXA_API_KEY, apiKey).apply()
    }
    
    /**
     * 獲取 Flexa 環境
     */
    fun getFlexaEnvironment(): FlexaEnvironment {
        val envString = encryptedPrefs.getString(KEY_FLEXA_ENVIRONMENT, null)
        return when (envString) {
            "PRODUCTION" -> FlexaEnvironment.PRODUCTION
            "SANDBOX" -> FlexaEnvironment.SANDBOX
            else -> if (BuildConfig.DEBUG) FlexaEnvironment.SANDBOX else FlexaEnvironment.PRODUCTION
        }
    }
    
    /**
     * 設置 Flexa 環境
     */
    fun setFlexaEnvironment(environment: FlexaEnvironment) {
        encryptedPrefs.edit().putString(KEY_FLEXA_ENVIRONMENT, environment.name).apply()
    }
    
    /**
     * 檢查 NFC 支付是否啟用
     */
    fun isNFCPaymentEnabled(): Boolean {
        return encryptedPrefs.getBoolean(KEY_NFC_ENABLED, true)
    }
    
    /**
     * 設置 NFC 支付開關
     */
    fun setNFCPaymentEnabled(enabled: Boolean) {
        encryptedPrefs.edit().putBoolean(KEY_NFC_ENABLED, enabled).apply()
    }
    
    /**
     * 獲取最小支付金額
     */
    fun getMinPaymentAmount(): String {
        return encryptedPrefs.getString(KEY_PAYMENT_MIN_AMOUNT, DEFAULT_MIN_AMOUNT) 
            ?: DEFAULT_MIN_AMOUNT
    }
    
    /**
     * 獲取最大支付金額
     */
    fun getMaxPaymentAmount(): String {
        return encryptedPrefs.getString(KEY_PAYMENT_MAX_AMOUNT, DEFAULT_MAX_AMOUNT) 
            ?: DEFAULT_MAX_AMOUNT
    }
    
    /**
     * 獲取每日限額
     */
    fun getDailyLimit(): String {
        return encryptedPrefs.getString(KEY_DAILY_LIMIT, DEFAULT_DAILY_LIMIT) 
            ?: DEFAULT_DAILY_LIMIT
    }
    
    /**
     * 設置支付限額
     */
    fun setPaymentLimits(min: String, max: String, daily: String) {
        encryptedPrefs.edit().apply {
            putString(KEY_PAYMENT_MIN_AMOUNT, min)
            putString(KEY_PAYMENT_MAX_AMOUNT, max)
            putString(KEY_DAILY_LIMIT, daily)
            apply()
        }
    }
    
    /**
     * 檢查是否需要生物識別驗證
     */
    fun requireBiometricForPayment(): Boolean {
        return encryptedPrefs.getBoolean(KEY_REQUIRE_BIOMETRIC, true)
    }
    
    /**
     * 設置生物識別驗證要求
     */
    fun setRequireBiometric(require: Boolean) {
        encryptedPrefs.edit().putBoolean(KEY_REQUIRE_BIOMETRIC, require).apply()
    }
    
    /**
     * 獲取自動確認閾值
     * 低於此金額的交易可以自動確認，無需用戶手動批准
     */
    fun getAutoConfirmThreshold(): String {
        return encryptedPrefs.getString(KEY_AUTO_CONFIRM_THRESHOLD, DEFAULT_AUTO_CONFIRM_THRESHOLD) 
            ?: DEFAULT_AUTO_CONFIRM_THRESHOLD
    }
    
    /**
     * 設置自動確認閾值
     */
    fun setAutoConfirmThreshold(threshold: String) {
        encryptedPrefs.edit().putString(KEY_AUTO_CONFIRM_THRESHOLD, threshold).apply()
    }
    
    /**
     * 驗證配置完整性
     */
    fun validateConfiguration(): ConfigValidationResult {
        val apiKey = getFlexaApiKey()
        
        return when {
            apiKey.isNullOrBlank() -> {
                ConfigValidationResult.Invalid("Flexa API 密鑰未配置")
            }
            apiKey.startsWith("YOUR_") || apiKey == "test_flexa_api_key" -> {
                ConfigValidationResult.Invalid("請配置有效的 Flexa API 密鑰")
            }
            !isNFCPaymentEnabled() -> {
                ConfigValidationResult.Disabled("NFC 支付功能已禁用")
            }
            else -> {
                ConfigValidationResult.Valid
            }
        }
    }
    
    /**
     * 重置所有配置到預設值
     */
    fun resetToDefaults() {
        encryptedPrefs.edit().clear().apply()
    }
    
    /**
     * 導出配置（用於備份，不包含敏感資訊）
     */
    fun exportConfiguration(): Map<String, Any> {
        return mapOf(
            "environment" to getFlexaEnvironment().name,
            "nfc_enabled" to isNFCPaymentEnabled(),
            "min_amount" to getMinPaymentAmount(),
            "max_amount" to getMaxPaymentAmount(),
            "daily_limit" to getDailyLimit(),
            "require_biometric" to requireBiometricForPayment(),
            "auto_confirm_threshold" to getAutoConfirmThreshold()
        )
    }
    
    /**
     * 導入配置（從備份恢復）
     */
    fun importConfiguration(config: Map<String, Any>) {
        config["environment"]?.let { env ->
            setFlexaEnvironment(
                FlexaEnvironment.valueOf(env.toString())
            )
        }
        config["nfc_enabled"]?.let { enabled ->
            setNFCPaymentEnabled(enabled as Boolean)
        }
        config["min_amount"]?.let { min ->
            encryptedPrefs.edit().putString(KEY_PAYMENT_MIN_AMOUNT, min.toString()).apply()
        }
        config["max_amount"]?.let { max ->
            encryptedPrefs.edit().putString(KEY_PAYMENT_MAX_AMOUNT, max.toString()).apply()
        }
        config["daily_limit"]?.let { daily ->
            encryptedPrefs.edit().putString(KEY_DAILY_LIMIT, daily.toString()).apply()
        }
        config["require_biometric"]?.let { require ->
            setRequireBiometric(require as Boolean)
        }
        config["auto_confirm_threshold"]?.let { threshold ->
            setAutoConfirmThreshold(threshold.toString())
        }
    }
}

/**
 * Flexa 環境枚舉
 */
enum class FlexaEnvironment {
    PRODUCTION,
    SANDBOX
}

/**
 * 配置驗證結果
 */
sealed class ConfigValidationResult {
    object Valid : ConfigValidationResult()
    data class Invalid(val reason: String) : ConfigValidationResult()
    data class Disabled(val reason: String) : ConfigValidationResult()
}

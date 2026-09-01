package com.cbstudio.wearwallet.services

import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.analytics.ktx.logEvent
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.google.firebase.perf.ktx.performance
import com.google.firebase.perf.metrics.Trace
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import com.google.firebase.remoteconfig.ConfigUpdate
import com.google.firebase.remoteconfig.ConfigUpdateListener
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException
import com.cbstudio.wearwallet.BuildConfig
import com.cbstudio.wearwallet.data.repository.RemoteConfigDefaults
import com.cbstudio.wearwallet.core.domain.repository.WalletRepository
import timber.log.Timber
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Firebase 服務整合類
 * 統一管理所有 Firebase 服務
 */
// @Singleton // 暫時移除 Hilt 註解
class FirebaseService(
    private val context: Context,
    private val walletRepository: WalletRepository?
) {
    private val analytics: FirebaseAnalytics = Firebase.analytics
    private val crashlytics = Firebase.crashlytics
    private val performance = Firebase.performance
    private val remoteConfig = Firebase.remoteConfig
    
    // 用於非同步操作的協程範圍
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        // 初始化 Remote Config
        setupRemoteConfig()
        
        // 設定 Crashlytics 用戶識別
        setupCrashlyticsUserId()
        
        // 自定義 Crashlytics 鍵值
        crashlytics.setCustomKey("app_type", "wear_os")
        crashlytics.setCustomKey("wallet_type", "multi_chain")
    }
    
    /**
     * 設定 Crashlytics 用戶識別
     * 使用當前選中錢包的ID作為用戶識別
     */
    private fun setupCrashlyticsUserId() {
        serviceScope.launch {
            try {
                // 暫時使用設備標識，直到 walletRepository 可用
                val userId = if (walletRepository != null) {
                    // TODO: 實現錢包檢索邏輯
                    "device_${context.packageName.hashCode().toString().take(8)}"
                } else {
                    // 如果沒有錢包，使用設備標識
                    "device_${context.packageName.hashCode().toString().take(8)}"
                }
                
                crashlytics.setUserId(userId)
                Timber.d("FirebaseService: Set Crashlytics user ID: $userId")
                
            } catch (e: Exception) {
                // 如果獲取錢包失敗，使用應用包名哈希作為後備方案
                val fallbackUserId = "fallback_${context.packageName.hashCode().toString().take(8)}"
                crashlytics.setUserId(fallbackUserId)
                Timber.w("FirebaseService: Failed to get wallet ID, using fallback: $fallbackUserId", e)
            }
        }
    }
    
    /**
     * 設定 Remote Config
     */
    private fun setupRemoteConfig() {
        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = if (BuildConfig.DEBUG) 0 else 3600
        }
        remoteConfig.setConfigSettingsAsync(configSettings)
        
        // 設定預設值
        val defaults = getDefaultRemoteConfigValues()
        remoteConfig.setDefaultsAsync(defaults)
        
        // 獲取並啟用遠端配置
        remoteConfig.fetchAndActivate()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val updated = task.result
                    Timber.d("Config params updated: $updated")
                } else {
                    Timber.e("Failed to fetch remote config")
                }
            }
        
        // 監聽即時配置更新
        remoteConfig.addOnConfigUpdateListener(object : ConfigUpdateListener {
            override fun onUpdate(configUpdate: ConfigUpdate) {
                Timber.d("Updated keys: ${configUpdate.updatedKeys}")
                
                // 檢查是否有訂閱相關的配置更新
                if (configUpdate.updatedKeys.contains("free_wallet_limit") ||
                    configUpdate.updatedKeys.contains("subscription_price_monthly") ||
                    configUpdate.updatedKeys.contains("subscription_price_yearly")) {
                    remoteConfig.activate()
                        .addOnCompleteListener {
                            Timber.d("Remote config activated with new values")
                        }
                }
            }
            
            override fun onError(error: FirebaseRemoteConfigException) {
                Timber.w(error, "Config update error")
            }
        })
    }
    
    /**
     * 獲取預設的 Remote Config 值
     * TODO: 從 sharedKmp 獲取默認值
     */
    private fun getDefaultRemoteConfigValues(): Map<String, Any> {
        // 臨時返回空映射，等待 KMP 整合
        return emptyMap()
    }

    /**
     * 記錄分析事件
     */
    fun logEvent(eventName: String, params: Map<String, Any>? = null) {
        analytics.logEvent(eventName) {
            params?.forEach { (key, value) ->
                when (value) {
                    is String -> param(key, value)
                    is Long -> param(key, value)
                    is Double -> param(key, value)
                    is Int -> param(key, value.toLong())
                    is Boolean -> param(key, if (value) 1L else 0L)
                    else -> param(key, value.toString())
                }
            }
        }
    }

    /**
     * 記錄錢包相關事件
     */
    fun logWalletEvent(action: WalletAction, chain: String? = null, extras: Map<String, Any>? = null) {
        val params = mutableMapOf<String, Any>(
            "action" to action.name.lowercase()
        )
        chain?.let { params["chain"] = it }
        extras?.let { params.putAll(it) }
        
        logEvent("wallet_action", params)
    }

    /**
     * 記錄交易事件
     */
    fun logTransactionEvent(action: TransactionAction, chain: String, amount: String? = null) {
        val params = mutableMapOf<String, Any>(
            "action" to action.name.lowercase(),
            "chain" to chain
        )
        amount?.let { params["amount"] = it }
        
        logEvent("transaction_action", params)
    }

    /**
     * 記錄訂閱事件
     */
    fun logSubscriptionEvent(action: SubscriptionAction, productId: String? = null) {
        val params = mutableMapOf<String, Any>(
            "action" to action.name.lowercase()
        )
        productId?.let { params["product_id"] = it }
        
        logEvent("subscription_action", params)
    }

    /**
     * 記錄錯誤
     */
    fun logError(message: String, exception: Throwable? = null) {
        crashlytics.log(message)
        exception?.let { crashlytics.recordException(it) }
    }

    /**
     * 開始性能追蹤
     */
    fun startTrace(traceName: String): Trace {
        return performance.newTrace(traceName).apply {
            start()
        }
    }

    /**
     * 設定用戶屬性
     */
    fun setUserProperty(name: String, value: String) {
        analytics.setUserProperty(name, value)
    }

    /**
     * 獲取遠端配置值
     */
    fun getRemoteConfigString(key: String): String = remoteConfig.getString(key)
    fun getRemoteConfigLong(key: String): Long = remoteConfig.getLong(key)
    fun getRemoteConfigBoolean(key: String): Boolean = remoteConfig.getBoolean(key)
    fun getRemoteConfigDouble(key: String): Double = remoteConfig.getDouble(key)
    
    /**
     * 獲取並激活 Remote Config
     */
    suspend fun fetchAndActivateRemoteConfig() {
        try {
            remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Timber.d("Remote Config 更新成功")
                } else {
                    Timber.e("Remote Config 更新失敗")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "獲取 Remote Config 時發生錯誤")
        }
    }
    
    /**
     * 報告錯誤到 Crashlytics
     */
    fun reportCrashlytics(throwable: Throwable, message: String? = null) {
        message?.let { crashlytics.log(it) }
        crashlytics.recordException(throwable)
    }

    /**
     * 刷新遠端配置
     */
    suspend fun refreshRemoteConfig() {
        try {
            remoteConfig.fetchAndActivate()
        } catch (e: Exception) {
            logError("Failed to refresh remote config", e)
        }
    }
}

/**
 * 錢包操作類型
 */
enum class WalletAction {
    CREATE,
    IMPORT,
    DELETE,
    SWITCH,
    BACKUP_VIEWED,
    CONNECT_KEYSTONE
}

/**
 * 交易操作類型
 */
enum class TransactionAction {
    SEND_INITIATED,
    SEND_COMPLETED,
    SEND_FAILED,
    RECEIVE_VIEWED,
    HISTORY_VIEWED
}

/**
 * 訂閱操作類型
 */
enum class SubscriptionAction {
    SCREEN_VIEWED,
    PURCHASE_INITIATED,
    PURCHASE_COMPLETED,
    PURCHASE_FAILED,
    PURCHASE_CANCELLED,
    CANCEL_INITIATED,
    RESTORE_INITIATED,
    RESTORE_COMPLETED
}

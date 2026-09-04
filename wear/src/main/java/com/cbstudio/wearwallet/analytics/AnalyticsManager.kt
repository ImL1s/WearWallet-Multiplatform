package com.cbstudio.wearwallet.analytics

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.analytics.ktx.logEvent
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.google.firebase.perf.ktx.performance
import com.google.firebase.perf.metrics.Trace
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import javax.inject.Singleton

/**
 * 統一的分析管理器，處理應用程式的所有分析需求
 */
// @Singleton
class AnalyticsManager constructor() {
    
    private val analytics = Firebase.analytics
    private val crashlytics = Firebase.crashlytics
    private val performance = Firebase.performance
    
    // 分析事件常量
    object Events {
        // 錢包操作
        const val WALLET_CREATED = "wallet_created"
        const val WALLET_IMPORTED = "wallet_imported"
        const val WALLET_DELETED = "wallet_deleted"
        const val WALLET_BACKED_UP = "wallet_backed_up"
        
        // 交易操作
        const val TRANSACTION_SENT = "transaction_sent"
        const val TRANSACTION_RECEIVED = "transaction_received"
        const val TRANSACTION_FAILED = "transaction_failed"
        const val QR_CODE_SCANNED = "qr_code_scanned"
        const val QR_CODE_GENERATED = "qr_code_generated"
        
        // 訂閱相關
        const val SUBSCRIPTION_STARTED = "subscription_started"
        const val SUBSCRIPTION_CANCELLED = "subscription_cancelled"
        const val SUBSCRIPTION_SCREEN_VIEWED = "subscription_screen_viewed"
        const val WALLET_LIMIT_REACHED = "wallet_limit_reached"
        
        // AI 功能
        const val AI_COMMAND_USED = "ai_command_used"
        const val AI_RISK_ANALYSIS = "ai_risk_analysis"
        const val AI_PORTFOLIO_ANALYSIS = "ai_portfolio_analysis"
        
        // UI 互動
        const val SCREEN_VIEW = "screen_view"
        const val COMPLICATION_UPDATED = "complication_updated"
        const val ADDRESS_BOOK_USED = "address_book_used"
        const val SETTINGS_CHANGED = "settings_changed"
        
        // 錯誤追蹤
        const val API_ERROR = "api_error"
        const val NETWORK_ERROR = "network_error"
        const val KEYSTONE_ERROR = "keystone_error"
    }
    
    // 參數常量
    object Params {
        const val WALLET_TYPE = "wallet_type"
        const val CHAIN_ID = "chain_id"
        const val TOKEN_SYMBOL = "token_symbol"
        const val AMOUNT = "amount"
        const val ERROR_CODE = "error_code"
        const val ERROR_MESSAGE = "error_message"
        const val SCREEN_NAME = "screen_name"
        const val COMMAND_TYPE = "command_type"
        const val RISK_LEVEL = "risk_level"
        const val SUBSCRIPTION_TIER = "subscription_tier"
        const val WALLET_COUNT = "wallet_count"
    }
    
    // 用戶屬性
    object UserProperties {
        const val WALLET_COUNT = "wallet_count"
        const val SUBSCRIPTION_STATUS = "subscription_status"
        const val PREFERRED_CHAIN = "preferred_chain"
        const val AI_USAGE_LEVEL = "ai_usage_level"
        const val COMPLICATION_ENABLED = "complication_enabled"
        const val LANGUAGE = "language"
    }
    
    /**
     * 記錄錢包創建事件
     */
    fun logWalletCreated(walletType: String, chainId: String) {
        analytics.logEvent(Events.WALLET_CREATED) {
            param(Params.WALLET_TYPE, walletType)
            param(Params.CHAIN_ID, chainId)
        }
    }
    
    /**
     * 記錄交易發送事件
     */
    fun logTransactionSent(chainId: String, tokenSymbol: String, amount: String) {
        analytics.logEvent(Events.TRANSACTION_SENT) {
            param(Params.CHAIN_ID, chainId)
            param(Params.TOKEN_SYMBOL, tokenSymbol)
            param(Params.AMOUNT, amount)
        }
    }
    
    /**
     * 記錄訂閱相關事件
     */
    fun logSubscriptionEvent(event: String, tier: String, walletCount: Int) {
        analytics.logEvent(event) {
            param(Params.SUBSCRIPTION_TIER, tier)
            param(Params.WALLET_COUNT, walletCount.toLong())
        }
    }
    
    /**
     * 記錄 AI 功能使用
     */
    fun logAICommandUsed(commandType: String, success: Boolean) {
        analytics.logEvent(Events.AI_COMMAND_USED) {
            param(Params.COMMAND_TYPE, commandType)
            param(FirebaseAnalytics.Param.SUCCESS, if (success) 1 else 0)
        }
    }
    
    /**
     * 記錄螢幕瀏覽
     */
    fun logScreenView(screenName: String) {
        analytics.logEvent(Events.SCREEN_VIEW) {
            param(Params.SCREEN_NAME, screenName)
            param(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
            param(FirebaseAnalytics.Param.SCREEN_CLASS, screenName)
        }
    }
    
    /**
     * 記錄錯誤事件
     */
    fun logError(errorType: String, errorCode: String, errorMessage: String) {
        analytics.logEvent(errorType) {
            param(Params.ERROR_CODE, errorCode)
            param(Params.ERROR_MESSAGE, errorMessage)
        }
        
        // 同時記錄到 Crashlytics
        crashlytics.recordException(
            AnalyticsException(errorType, errorCode, errorMessage)
        )
    }
    
    /**
     * 設置用戶屬性
     */
    fun setUserProperty(property: String, value: String) {
        analytics.setUserProperty(property, value)
        crashlytics.setCustomKey(property, value)
    }
    
    /**
     * 更新用戶錢包數量
     */
    fun updateWalletCount(count: Int) {
        setUserProperty(UserProperties.WALLET_COUNT, count.toString())
    }
    
    /**
     * 更新訂閱狀態
     */
    fun updateSubscriptionStatus(status: String) {
        setUserProperty(UserProperties.SUBSCRIPTION_STATUS, status)
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
     * 記錄自定義性能指標
     */
    fun recordMetric(trace: Trace, metricName: String, value: Long) {
        trace.putMetric(metricName, value)
    }
    
    /**
     * 設置崩潰報告用戶 ID
     */
    fun setUserId(userId: String) {
        crashlytics.setUserId(userId)
        analytics.setUserId(userId)
    }
    
    /**
     * 記錄崩潰前的自定義日誌
     */
    fun log(message: String) {
        crashlytics.log(message)
    }
    
    /**
     * 記錄非致命錯誤
     */
    fun recordException(exception: Throwable) {
        crashlytics.recordException(exception)
    }
}

/**
 * 自定義分析異常類
 */
class AnalyticsException(
    private val errorType: String,
    private val errorCode: String,
    message: String
) : Exception("[$errorType] $errorCode: $message")

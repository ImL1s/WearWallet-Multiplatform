package com.cbstudio.wearwallet.analytics

import com.google.firebase.perf.metrics.Trace
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.system.measureTimeMillis

/**
 * 性能監控工具類
 */
class PerformanceMonitor constructor(
    private val analyticsManager: AnalyticsManager
) {
    
    /**
     * 監控交易發送性能
     */
    fun <T> measureTransactionSend(
        chainId: String,
        action: () -> T
    ): T {
        val trace = analyticsManager.startTrace("transaction_send")
        trace.putAttribute("chain_id", chainId)
        
        var result: T
        val duration = measureTimeMillis {
            result = action()
        }
        
        trace.putMetric("duration_ms", duration)
        trace.stop()
        
        return result
    }
    
    /**
     * 監控錢包創建性能
     */
    fun <T> measureWalletCreation(
        walletType: String,
        action: () -> T
    ): T {
        val trace = analyticsManager.startTrace("wallet_creation")
        trace.putAttribute("wallet_type", walletType)
        
        var result: T
        val duration = measureTimeMillis {
            result = action()
        }
        
        trace.putMetric("duration_ms", duration)
        trace.stop()
        
        return result
    }
    
    /**
     * 監控 AI 請求性能
     */
    fun <T> measureAIRequest(
        requestType: String,
        action: () -> T
    ): T {
        val trace = analyticsManager.startTrace("ai_request")
        trace.putAttribute("request_type", requestType)
        
        var result: T
        val duration = measureTimeMillis {
            result = action()
        }
        
        trace.putMetric("duration_ms", duration)
        trace.stop()
        
        return result
    }
    
    /**
     * 監控資料庫查詢性能
     */
    fun <T> measureDatabaseQuery(
        queryType: String,
        action: () -> T
    ): T {
        val trace = analyticsManager.startTrace("database_query")
        trace.putAttribute("query_type", queryType)
        
        var result: T
        val duration = measureTimeMillis {
            result = action()
        }
        
        trace.putMetric("duration_ms", duration)
        trace.stop()
        
        return result
    }
    
    /**
     * 監控網路請求性能
     */
    fun createNetworkTrace(url: String): NetworkTrace {
        return NetworkTrace(analyticsManager, url)
    }
    
    /**
     * 監控啟動時間
     */
    fun recordAppStartTime(startTimeMs: Long) {
        val currentTime = System.currentTimeMillis()
        val startupTime = currentTime - startTimeMs
        
        val trace = analyticsManager.startTrace("app_startup")
        trace.putMetric("startup_time_ms", startupTime)
        trace.stop()
    }
}

/**
 * 網路請求追蹤包裝類
 */
class NetworkTrace(
    private val analyticsManager: AnalyticsManager,
    private val url: String
) {
    private val trace: Trace = analyticsManager.startTrace("network_request")
    
    init {
        trace.putAttribute("url", url)
    }
    
    fun setRequestPayloadSize(bytes: Long) {
        trace.putMetric("request_payload_bytes", bytes)
    }
    
    fun setResponsePayloadSize(bytes: Long) {
        trace.putMetric("response_payload_bytes", bytes)
    }
    
    fun setHttpResponseCode(code: Int) {
        trace.putAttribute("http_response_code", code.toString())
    }
    
    fun recordSuccess() {
        trace.putAttribute("success", "true")
        trace.stop()
    }
    
    fun recordFailure(error: String) {
        trace.putAttribute("success", "false")
        trace.putAttribute("error", error)
        trace.stop()
    }
}

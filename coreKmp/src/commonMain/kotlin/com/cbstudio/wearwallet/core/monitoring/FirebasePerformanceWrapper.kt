package com.cbstudio.wearwallet.core.monitoring

import com.cbstudio.wearwallet.core.utils.Logger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.datetime.Clock

/**
 * Firebase Performance Monitoring 跨平台包裝器
 * 
 * 提供統一的效能監控介面，在不同平台上實作具體的 Firebase 整合
 * 
 * Created: 2025-01-17
 */
interface FirebasePerformanceWrapper {
    /**
     * 開始一個自定義追蹤
     */
    fun startTrace(name: String): PerformanceTrace
    
    /**
     * 記錄網路請求
     */
    fun logNetworkRequest(
        url: String,
        httpMethod: String,
        responseCode: Int,
        requestPayloadSize: Long?,
        responsePayloadSize: Long?,
        duration: Duration
    )
    
    /**
     * 設定自定義屬性
     */
    fun setAttribute(key: String, value: String)
    
    /**
     * 記錄自定義指標
     */
    fun putMetric(name: String, value: Long)
    
    /**
     * 設定使用者 ID（匿名化）
     */
    fun setUserId(userId: String?)
    
    /**
     * 啟用/禁用效能監控
     */
    fun setEnabled(enabled: Boolean)
}

/**
 * 效能追蹤介面
 */
interface PerformanceTrace {
    /**
     * 設定追蹤屬性
     */
    fun setAttribute(key: String, value: String)
    
    /**
     * 增加計數器
     */
    fun incrementMetric(name: String, value: Long = 1)
    
    /**
     * 設定指標值
     */
    fun putMetric(name: String, value: Long)
    
    /**
     * 停止追蹤
     */
    fun stop()
}

/**
 * 平台特定的 Firebase Performance 實作
 */
expect class PlatformFirebasePerformance() {
    fun startTrace(name: String): PerformanceTrace
    fun logNetworkRequest(
        url: String,
        httpMethod: String,
        responseCode: Int,
        requestPayloadSize: Long?,
        responsePayloadSize: Long?,
        duration: Duration
    )
    fun setAttribute(key: String, value: String)
    fun putMetric(name: String, value: Long)
    fun setUserId(userId: String?)
    fun setEnabled(enabled: Boolean)
}

/**
 * Firebase Performance 預設實作（用於測試和非 Firebase 環境）
 */
class DefaultFirebasePerformance : FirebasePerformanceWrapper {
    
    private var isEnabled = true
    private val traces = mutableMapOf<String, DefaultTrace>()
    
    override fun startTrace(name: String): PerformanceTrace {
        if (!isEnabled) return NoOpTrace()
        
        val trace = DefaultTrace(name)
        traces[name] = trace
        Logger.d("FirebasePerf", "Started trace: $name")
        return trace
    }
    
    override fun logNetworkRequest(
        url: String,
        httpMethod: String,
        responseCode: Int,
        requestPayloadSize: Long?,
        responsePayloadSize: Long?,
        duration: Duration
    ) {
        if (!isEnabled) return
        
        Logger.d("FirebasePerf", """
            Network Request:
            URL: $url
            Method: $httpMethod
            Response Code: $responseCode
            Request Size: ${requestPayloadSize ?: 0} bytes
            Response Size: ${responsePayloadSize ?: 0} bytes
            Duration: ${duration.inWholeMilliseconds}ms
        """.trimIndent())
    }
    
    override fun setAttribute(key: String, value: String) {
        if (!isEnabled) return
        Logger.d("FirebasePerf", "Set attribute: $key = $value")
    }
    
    override fun putMetric(name: String, value: Long) {
        if (!isEnabled) return
        Logger.d("FirebasePerf", "Put metric: $name = $value")
    }
    
    override fun setUserId(userId: String?) {
        if (!isEnabled) return
        Logger.d("FirebasePerf", "Set user ID: ${userId?.take(8)}...")
    }
    
    override fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        Logger.d("FirebasePerf", "Performance monitoring ${if (enabled) "enabled" else "disabled"}")
    }
    
    /**
     * 獲取追蹤統計（測試用）
     */
    fun getTraceStats(): Map<String, TraceStats> {
        return traces.mapValues { (_, trace) ->
            TraceStats(
                name = trace.name,
                duration = trace.getDuration(),
                attributes = trace.attributes.toMap(),
                metrics = trace.metrics.toMap()
            )
        }
    }
}

/**
 * 預設追蹤實作
 */
private class DefaultTrace(
    val name: String
) : PerformanceTrace {
    
    private val startTime = Clock.System.now()
    private var endTime: kotlinx.datetime.Instant? = null
    
    val attributes = mutableMapOf<String, String>()
    val metrics = mutableMapOf<String, Long>()
    
    override fun setAttribute(key: String, value: String) {
        attributes[key] = value
    }
    
    override fun incrementMetric(name: String, value: Long) {
        metrics[name] = (metrics[name] ?: 0) + value
    }
    
    override fun putMetric(name: String, value: Long) {
        metrics[name] = value
    }
    
    override fun stop() {
        if (endTime == null) {
            endTime = Clock.System.now()
            val duration = endTime!! - startTime
            Logger.d("FirebasePerf", "Stopped trace: $name (${duration.inWholeMilliseconds}ms)")
        }
    }
    
    fun getDuration(): Duration {
        val end = endTime ?: Clock.System.now()
        return end - startTime
    }
}

/**
 * 無操作追蹤（用於禁用狀態）
 */
private class NoOpTrace : PerformanceTrace {
    override fun setAttribute(key: String, value: String) {}
    override fun incrementMetric(name: String, value: Long) {}
    override fun putMetric(name: String, value: Long) {}
    override fun stop() {}
}

/**
 * 追蹤統計資料
 */
data class TraceStats(
    val name: String,
    val duration: Duration,
    val attributes: Map<String, String>,
    val metrics: Map<String, Long>
)

/**
 * 全局 Firebase Performance 實例
 */
object FirebasePerformance {
    
    private var implementation: FirebasePerformanceWrapper = DefaultFirebasePerformance()
    
    /**
     * 初始化 Firebase Performance
     */
    fun initialize(impl: FirebasePerformanceWrapper) {
        implementation = impl
        Logger.d("FirebasePerf", "Initialized with ${impl::class.simpleName}")
    }
    
    /**
     * 開始追蹤
     */
    fun startTrace(name: String): PerformanceTrace {
        return implementation.startTrace(name)
    }
    
    /**
     * 使用 DSL 方式追蹤
     */
    inline fun <T> trace(
        name: String,
        block: PerformanceTrace.() -> T
    ): T {
        val trace = startTrace(name)
        return try {
            trace.block()
        } finally {
            trace.stop()
        }
    }
    
    /**
     * 記錄網路請求
     */
    fun logNetworkRequest(
        url: String,
        httpMethod: String,
        responseCode: Int,
        requestPayloadSize: Long? = null,
        responsePayloadSize: Long? = null,
        duration: Duration
    ) {
        implementation.logNetworkRequest(
            url, httpMethod, responseCode,
            requestPayloadSize, responsePayloadSize, duration
        )
    }
    
    /**
     * 設定全局屬性
     */
    fun setAttribute(key: String, value: String) {
        implementation.setAttribute(key, value)
    }
    
    /**
     * 記錄全局指標
     */
    fun putMetric(name: String, value: Long) {
        implementation.putMetric(name, value)
    }
    
    /**
     * 設定使用者 ID
     */
    fun setUserId(userId: String?) {
        implementation.setUserId(userId)
    }
    
    /**
     * 啟用/禁用
     */
    fun setEnabled(enabled: Boolean) {
        implementation.setEnabled(enabled)
    }
}

/**
 * 效能監控註解
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class TrackPerformance(
    val name: String = "",
    val category: String = ""
)

/**
 * 網路效能監控
 */
class NetworkPerformanceMonitor {
    
    data class RequestInfo(
        val url: String,
        val method: String,
        val startTime: kotlinx.datetime.Instant,
        var requestSize: Long? = null,
        var responseSize: Long? = null,
        var responseCode: Int? = null
    )
    
    private val activeRequests = mutableMapOf<String, RequestInfo>()
    
    /**
     * 開始監控請求
     */
    fun startRequest(
        requestId: String,
        url: String,
        method: String,
        requestSize: Long? = null
    ) {
        activeRequests[requestId] = RequestInfo(
            url = url,
            method = method,
            startTime = Clock.System.now(),
            requestSize = requestSize
        )
    }
    
    /**
     * 結束監控請求
     */
    fun endRequest(
        requestId: String,
        responseCode: Int,
        responseSize: Long? = null
    ) {
        activeRequests.remove(requestId)?.let { info ->
            val duration = Clock.System.now() - info.startTime
            
            FirebasePerformance.logNetworkRequest(
                url = info.url,
                httpMethod = info.method,
                responseCode = responseCode,
                requestPayloadSize = info.requestSize,
                responsePayloadSize = responseSize,
                duration = duration
            )
        }
    }
    
    /**
     * 請求失敗
     */
    fun failRequest(requestId: String, error: Throwable) {
        activeRequests.remove(requestId)?.let { info ->
            val duration = Clock.System.now() - info.startTime
            
            FirebasePerformance.logNetworkRequest(
                url = info.url,
                httpMethod = info.method,
                responseCode = -1, // 表示失敗
                requestPayloadSize = info.requestSize,
                responsePayloadSize = null,
                duration = duration
            )
        }
    }
}

/**
 * 自定義指標收集器
 */
class MetricsCollector {
    
    private val counters = mutableMapOf<String, Long>()
    private val gauges = mutableMapOf<String, Long>()
    private val histograms = mutableMapOf<String, MutableList<Long>>()
    
    /**
     * 增加計數器
     */
    fun incrementCounter(name: String, value: Long = 1) {
        counters[name] = (counters[name] ?: 0) + value
        FirebasePerformance.putMetric(name, counters[name]!!)
    }
    
    /**
     * 設定量測值
     */
    fun setGauge(name: String, value: Long) {
        gauges[name] = value
        FirebasePerformance.putMetric(name, value)
    }
    
    /**
     * 記錄直方圖值
     */
    fun recordHistogram(name: String, value: Long) {
        histograms.getOrPut(name) { mutableListOf() }.add(value)
        
        // 計算並記錄百分位數
        val values = histograms[name]!!.sorted()
        if (values.size >= 10) {
            val p50 = values[values.size / 2]
            val p90 = values[(values.size * 0.9).toInt()]
            val p99 = values[(values.size * 0.99).toInt().coerceAtMost(values.size - 1)]
            
            FirebasePerformance.putMetric("${name}_p50", p50)
            FirebasePerformance.putMetric("${name}_p90", p90)
            FirebasePerformance.putMetric("${name}_p99", p99)
        }
    }
    
    /**
     * 獲取統計報告
     */
    fun getReport(): MetricsReport {
        return MetricsReport(
            counters = counters.toMap(),
            gauges = gauges.toMap(),
            histograms = histograms.mapValues { (_, values) ->
                HistogramStats(
                    count = values.size,
                    min = values.minOrNull() ?: 0,
                    max = values.maxOrNull() ?: 0,
                    avg = if (values.isNotEmpty()) values.average().toLong() else 0,
                    p50 = if (values.size >= 2) values.sorted()[values.size / 2] else 0,
                    p90 = if (values.size >= 10) values.sorted()[(values.size * 0.9).toInt()] else 0,
                    p99 = if (values.size >= 100) values.sorted()[(values.size * 0.99).toInt()] else 0
                )
            }
        )
    }
}

/**
 * 指標報告
 */
data class MetricsReport(
    val counters: Map<String, Long>,
    val gauges: Map<String, Long>,
    val histograms: Map<String, HistogramStats>
) {
    fun printReport() {
        println("""
            ╔════════════════════════════════════════╗
            ║         Metrics Report                 ║
            ╚════════════════════════════════════════╝
            
            📊 Counters:
            ${counters.entries.joinToString("\n") { "  • ${it.key}: ${it.value}" }}
            
            📏 Gauges:
            ${gauges.entries.joinToString("\n") { "  • ${it.key}: ${it.value}" }}
            
            📈 Histograms:
            ${histograms.entries.joinToString("\n") { (name, stats) ->
                """  • $name:
                    Count: ${stats.count}
                    Min: ${stats.min}, Max: ${stats.max}, Avg: ${stats.avg}
                    P50: ${stats.p50}, P90: ${stats.p90}, P99: ${stats.p99}"""
            }}
        """.trimIndent())
    }
}

/**
 * 直方圖統計
 */
data class HistogramStats(
    val count: Int,
    val min: Long,
    val max: Long,
    val avg: Long,
    val p50: Long,
    val p90: Long,
    val p99: Long
)

/**
 * 全局指標收集器
 */
object GlobalMetrics {
    val collector = MetricsCollector()
    val networkMonitor = NetworkPerformanceMonitor()
    
    /**
     * 開始應用效能監控
     */
    fun startMonitoring() {
        // 設定應用屬性
        FirebasePerformance.setAttribute("platform", getPlatformName())
        FirebasePerformance.setAttribute("app_version", "1.0.0")
        FirebasePerformance.setAttribute("build_type", getBuildType())
        
        Logger.d("GlobalMetrics", "Started performance monitoring")
    }
    
    private fun getPlatformName(): String {
        // 這將在各平台實作中覆寫
        return "KMP"
    }
    
    private fun getBuildType(): String {
        // 這將根據編譯配置決定
        return "Debug"
    }
}
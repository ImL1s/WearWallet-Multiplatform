package com.cbstudio.wearwallet.core.monitoring

import com.cbstudio.wearwallet.core.utils.Logger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.*
import platform.WatchKit.*

/**
 * watchOS 平台的 Firebase Performance 實作
 * 
 * 注意：watchOS 可能有限制的 Firebase 支援
 * 可能需要透過 iPhone 伴侶應用來傳送效能資料
 * 
 * Created: 2025-01-17
 */
actual class PlatformFirebasePerformance {
    
    private var isEnabled = true
    private val pendingMetrics = mutableListOf<PendingMetric>()
    
    actual fun startTrace(name: String): PerformanceTrace {
        if (!isEnabled) {
            return WatchOSNoOpTrace()
        }
        
        Logger.d("WatchOSFirebasePerf", "Started trace: $name")
        return WatchOSPerformanceTrace(name, this as PlatformFirebasePerformance)
    }
    
    actual fun logNetworkRequest(
        url: String,
        httpMethod: String,
        responseCode: Int,
        requestPayloadSize: Long?,
        responsePayloadSize: Long?,
        duration: Duration
    ) {
        if (!isEnabled) return
        
        // 在 watchOS 上，可能需要批量傳送到 iPhone
        val metric = PendingMetric.NetworkRequest(
            url = url,
            httpMethod = httpMethod,
            responseCode = responseCode,
            requestPayloadSize = requestPayloadSize,
            responsePayloadSize = responsePayloadSize,
            duration = duration
        )
        
        queueMetric(metric)
        
        Logger.d("WatchOSFirebasePerf", """
            Network Request queued:
            URL: $url
            Method: $httpMethod
            Response: $responseCode
            Duration: ${duration.inWholeMilliseconds}ms
        """.trimIndent())
    }
    
    actual fun setAttribute(key: String, value: String) {
        if (!isEnabled) return
        
        queueMetric(PendingMetric.Attribute(key, value))
        Logger.d("WatchOSFirebasePerf", "Set attribute: $key = $value")
    }
    
    actual fun putMetric(name: String, value: Long) {
        if (!isEnabled) return
        
        queueMetric(PendingMetric.CustomMetric(name, value))
        Logger.d("WatchOSFirebasePerf", "Put metric: $name = $value")
    }
    
    actual fun setUserId(userId: String?) {
        if (!isEnabled) return
        
        userId?.let {
            queueMetric(PendingMetric.UserId(it))
        }
        Logger.d("WatchOSFirebasePerf", "Set user ID")
    }
    
    actual fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        
        if (enabled) {
            startBatchUpload()
        } else {
            stopBatchUpload()
        }
        
        Logger.d("WatchOSFirebasePerf", "Performance monitoring ${if (enabled) "enabled" else "disabled"}")
    }
    
    /**
     * 將指標加入佇列
     */
    private fun queueMetric(metric: PendingMetric) {
        pendingMetrics.add(metric)
        
        // 如果累積太多，觸發上傳
        if (pendingMetrics.size >= 50) {
            uploadMetrics()
        }
    }
    
    /**
     * 上傳指標到 iPhone
     */
    private fun uploadMetrics() {
        if (pendingMetrics.isEmpty()) return
        
        // 在實際實作中，這裡會使用 WatchConnectivity 傳送到 iPhone
        // let message = ["metrics": pendingMetrics.map { $0.toDictionary() }]
        // WCSession.default.sendMessage(message, replyHandler: nil)
        
        Logger.d("WatchOSFirebasePerf", "Uploading ${pendingMetrics.size} metrics to iPhone")
        pendingMetrics.clear()
    }
    
    /**
     * 開始批次上傳計時器
     */
    private fun startBatchUpload() {
        // 每 30 秒上傳一次
        // Timer.scheduledTimer(withTimeInterval: 30.0, repeats: true) { _ in
        //     self.uploadMetrics()
        // }
    }
    
    /**
     * 停止批次上傳
     */
    private fun stopBatchUpload() {
        uploadMetrics() // 上傳剩餘的
        // timer?.invalidate()
    }
    
    /**
     * 記錄追蹤完成
     */
    internal fun logTraceCompletion(
        name: String,
        duration: Duration,
        attributes: Map<String, String>,
        metrics: Map<String, Long>
    ) {
        queueMetric(
            PendingMetric.TraceCompletion(
                name = name,
                duration = duration,
                attributes = attributes,
                metrics = metrics
            )
        )
    }
}

/**
 * 待上傳的指標
 */
private sealed class PendingMetric {
    data class NetworkRequest(
        val url: String,
        val httpMethod: String,
        val responseCode: Int,
        val requestPayloadSize: Long?,
        val responsePayloadSize: Long?,
        val duration: Duration
    ) : PendingMetric()
    
    data class Attribute(
        val key: String,
        val value: String
    ) : PendingMetric()
    
    data class CustomMetric(
        val name: String,
        val value: Long
    ) : PendingMetric()
    
    data class UserId(
        val id: String
    ) : PendingMetric()
    
    data class TraceCompletion(
        val name: String,
        val duration: Duration,
        val attributes: Map<String, String>,
        val metrics: Map<String, Long>
    ) : PendingMetric()
}

/**
 * watchOS 效能追蹤實作
 */
private class WatchOSPerformanceTrace(
    private val name: String,
    private val performance: PlatformFirebasePerformance
) : PerformanceTrace {
    
    private val startTime = NSDate.date()
    private var endTime: NSDate? = null
    private val attributes = mutableMapOf<String, String>()
    private val metrics = mutableMapOf<String, Long>()
    
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
            endTime = NSDate.date()
            val durationMs = ((endTime!!.timeIntervalSince1970 - startTime.timeIntervalSince1970) * 1000).toLong()
            val duration = durationMs.milliseconds
            
            performance.logTraceCompletion(
                name = name,
                duration = duration,
                attributes = attributes,
                metrics = metrics
            )
            
            Logger.d("WatchOSFirebasePerf", "Stopped trace: $name (${duration.inWholeMilliseconds}ms)")
        }
    }
}

/**
 * watchOS 無操作追蹤
 */
private class WatchOSNoOpTrace : PerformanceTrace {
    override fun setAttribute(key: String, value: String) {}
    override fun incrementMetric(name: String, value: Long) {}
    override fun putMetric(name: String, value: Long) {}
    override fun stop() {}
}

/**
 * watchOS 平台初始化器
 */
@OptIn(ExperimentalForeignApi::class)
object WatchOSFirebaseInitializer {
    
    /**
     * 初始化 Firebase Performance（從 watchOS 應用調用）
     */
    fun initialize() {
        val watchOSImpl = PlatformFirebasePerformance()
        
        // 創建一個 wrapper 來橋接
        val wrapper = object : FirebasePerformanceWrapper {
            override fun startTrace(name: String) = watchOSImpl.startTrace(name)
            override fun logNetworkRequest(
                url: String,
                httpMethod: String,
                responseCode: Int,
                requestPayloadSize: Long?,
                responsePayloadSize: Long?,
                duration: Duration
            ) = watchOSImpl.logNetworkRequest(url, httpMethod, responseCode, requestPayloadSize, responsePayloadSize, duration)
            override fun setAttribute(key: String, value: String) = watchOSImpl.setAttribute(key, value)
            override fun putMetric(name: String, value: Long) = watchOSImpl.putMetric(name, value)
            override fun setUserId(userId: String?) = watchOSImpl.setUserId(userId)
            override fun setEnabled(enabled: Boolean) = watchOSImpl.setEnabled(enabled)
        }
        
        FirebasePerformance.initialize(wrapper)
        
        // 設定平台特定屬性
        FirebasePerformance.setAttribute("platform", "watchOS")
        
        val device = WKInterfaceDevice.currentDevice()
        FirebasePerformance.setAttribute("os_version", device.systemVersion.toString())
        FirebasePerformance.setAttribute("device_model", device.model)
        FirebasePerformance.setAttribute("watch_name", device.name)
        
        // watchOS 特定屬性 - 簡化版本，避免複雜的 cinterop
        // 實際的螢幕尺寸會在 Swift 層設定
        
        Logger.d("WatchOSFirebaseInit", "Firebase Performance initialized for watchOS")
    }
}
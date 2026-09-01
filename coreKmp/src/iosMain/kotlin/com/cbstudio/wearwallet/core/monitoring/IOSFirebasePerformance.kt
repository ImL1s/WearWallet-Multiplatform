package com.cbstudio.wearwallet.core.monitoring

import com.cbstudio.wearwallet.core.utils.Logger
import kotlin.time.Duration
import platform.Foundation.*

/**
 * iOS 平台的 Firebase Performance 實作
 * 
 * 注意：實際使用時需要在 iOS 專案中整合 Firebase SDK
 * 這裡提供介面實作，實際的 Firebase SDK 調用在 Swift 中完成
 * 
 * Created: 2025-01-17
 */
actual class PlatformFirebasePerformance {
    
    private var isEnabled = true
    
    actual fun startTrace(name: String): PerformanceTrace {
        if (!isEnabled) {
            return IOSNoOpTrace()
        }
        
        // 在實際實作中，這裡會透過 Swift 橋接調用 Firebase SDK
        // let trace = Performance.startTrace(name: name)
        
        Logger.d("IOSFirebasePerf", "Started trace: $name")
        return IOSPerformanceTrace(name)
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
        
        // 在實際實作中，這裡會透過 Swift 橋接調用 Firebase SDK
        // let metric = HTTPMetric(url: URL(string: url)!, httpMethod: HTTPMethod(rawValue: httpMethod)!)
        // metric.responseCode = responseCode
        // if let requestSize = requestPayloadSize {
        //     metric.requestPayloadSize = requestSize
        // }
        // if let responseSize = responsePayloadSize {
        //     metric.responsePayloadSize = responseSize
        // }
        // metric.stop()
        
        Logger.d("IOSFirebasePerf", """
            Network Request logged:
            URL: $url
            Method: $httpMethod
            Response: $responseCode
            Duration: ${duration.inWholeMilliseconds}ms
        """.trimIndent())
    }
    
    actual fun setAttribute(key: String, value: String) {
        if (!isEnabled) return
        
        // Performance.sharedInstance().setValue(value, forAttribute: key)
        Logger.d("IOSFirebasePerf", "Set attribute: $key = $value")
    }
    
    actual fun putMetric(name: String, value: Long) {
        if (!isEnabled) return
        
        Logger.d("IOSFirebasePerf", "Put metric: $name = $value")
    }
    
    actual fun setUserId(userId: String?) {
        if (!isEnabled) return
        
        // Analytics.setUserID(userId)
        Logger.d("IOSFirebasePerf", "Set user ID")
    }
    
    actual fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        // Performance.sharedInstance().isDataCollectionEnabled = enabled
        Logger.d("IOSFirebasePerf", "Performance monitoring ${if (enabled) "enabled" else "disabled"}")
    }
}

/**
 * iOS 效能追蹤實作
 */
private class IOSPerformanceTrace(
    private val name: String
) : PerformanceTrace {
    
    private val attributes = mutableMapOf<String, String>()
    private val metrics = mutableMapOf<String, Long>()
    
    override fun setAttribute(key: String, value: String) {
        attributes[key] = value
        // trace?.setValue(value, forAttribute: key)
    }
    
    override fun incrementMetric(name: String, value: Long) {
        metrics[name] = (metrics[name] ?: 0) + value
        // trace?.incrementMetric(name, by: value)
    }
    
    override fun putMetric(name: String, value: Long) {
        metrics[name] = value
        // trace?.setValue(value, forMetric: name)
    }
    
    override fun stop() {
        // trace?.stop()
        Logger.d("IOSFirebasePerf", "Stopped trace: $name")
    }
}

/**
 * iOS 無操作追蹤
 */
private class IOSNoOpTrace : PerformanceTrace {
    override fun setAttribute(key: String, value: String) {}
    override fun incrementMetric(name: String, value: Long) {}
    override fun putMetric(name: String, value: Long) {}
    override fun stop() {}
}

/**
 * iOS 平台初始化器
 */
object IOSFirebaseInitializer {
    
    /**
     * 初始化 Firebase Performance（從 iOS 應用調用）
     */
    fun initialize() {
        val iosImpl = PlatformFirebasePerformance()
        
        // 創建一個 wrapper 來橋接
        val wrapper = object : FirebasePerformanceWrapper {
            override fun startTrace(name: String) = iosImpl.startTrace(name)
            override fun logNetworkRequest(
                url: String,
                httpMethod: String,
                responseCode: Int,
                requestPayloadSize: Long?,
                responsePayloadSize: Long?,
                duration: Duration
            ) = iosImpl.logNetworkRequest(url, httpMethod, responseCode, requestPayloadSize, responsePayloadSize, duration)
            override fun setAttribute(key: String, value: String) = iosImpl.setAttribute(key, value)
            override fun putMetric(name: String, value: Long) = iosImpl.putMetric(name, value)
            override fun setUserId(userId: String?) = iosImpl.setUserId(userId)
            override fun setEnabled(enabled: Boolean) = iosImpl.setEnabled(enabled)
        }
        
        FirebasePerformance.initialize(wrapper)
        
        // 設定平台特定屬性
        FirebasePerformance.setAttribute("platform", "iOS")
        
        val device = NSProcessInfo.processInfo.operatingSystemVersionString
        FirebasePerformance.setAttribute("os_version", device)
        
        Logger.d("IOSFirebaseInit", "Firebase Performance initialized for iOS")
    }
}
package com.cbstudio.wearwallet.core.monitoring

import com.cbstudio.wearwallet.core.utils.Logger
import kotlin.time.Duration

/**
 * Android 平台的 Firebase Performance 實作
 * 
 * 注意：實際使用時需要在 Android 模組中整合 Firebase SDK
 * 這裡提供介面實作，實際的 Firebase SDK 調用在 wear/mobile 模組中完成
 * 
 * Created: 2025-01-17
 */
actual class PlatformFirebasePerformance {
    
    // 這個會在 Android 模組中被真實的 Firebase Performance 實例替換
    private var firebaseImpl: Any? = null
    private var isEnabled = true
    
    /**
     * 設定實際的 Firebase Performance 實例
     */
    fun setFirebaseImplementation(impl: Any) {
        firebaseImpl = impl
        Logger.d("AndroidFirebasePerf", "Firebase implementation set")
    }
    
    actual fun startTrace(name: String): PerformanceTrace {
        if (!isEnabled || firebaseImpl == null) {
            return AndroidNoOpTrace()
        }
        
        // 在實際實作中，這裡會調用 Firebase SDK
        // val firebaseTrace = FirebasePerformance.getInstance().newTrace(name)
        // firebaseTrace.start()
        
        Logger.d("AndroidFirebasePerf", "Started trace: $name")
        return AndroidPerformanceTrace(name)
    }
    
    actual fun logNetworkRequest(
        url: String,
        httpMethod: String,
        responseCode: Int,
        requestPayloadSize: Long?,
        responsePayloadSize: Long?,
        duration: Duration
    ) {
        if (!isEnabled || firebaseImpl == null) return
        
        // 在實際實作中，這裡會調用 Firebase SDK
        // val metric = FirebasePerformance.getInstance()
        //     .newHttpMetric(url, httpMethod)
        // metric.setResponseCode(responseCode)
        // requestPayloadSize?.let { metric.setRequestPayloadSize(it) }
        // responsePayloadSize?.let { metric.setResponsePayloadSize(it) }
        // metric.putAttribute("duration_ms", duration.inWholeMilliseconds.toString())
        // metric.stop()
        
        Logger.d("AndroidFirebasePerf", """
            Network Request logged:
            URL: $url
            Method: $httpMethod
            Response: $responseCode
            Duration: ${duration.inWholeMilliseconds}ms
        """.trimIndent())
    }
    
    actual fun setAttribute(key: String, value: String) {
        if (!isEnabled || firebaseImpl == null) return
        
        // FirebasePerformance.getInstance().putAttribute(key, value)
        Logger.d("AndroidFirebasePerf", "Set attribute: $key = $value")
    }
    
    actual fun putMetric(name: String, value: Long) {
        if (!isEnabled || firebaseImpl == null) return
        
        // 自定義指標需要透過 trace 設定
        Logger.d("AndroidFirebasePerf", "Put metric: $name = $value")
    }
    
    actual fun setUserId(userId: String?) {
        if (!isEnabled || firebaseImpl == null) return
        
        // FirebaseAnalytics.getInstance(context).setUserId(userId)
        Logger.d("AndroidFirebasePerf", "Set user ID")
    }
    
    actual fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        // FirebasePerformance.getInstance().isPerformanceCollectionEnabled = enabled
        Logger.d("AndroidFirebasePerf", "Performance monitoring ${if (enabled) "enabled" else "disabled"}")
    }
}

/**
 * Android 效能追蹤實作
 */
private class AndroidPerformanceTrace(
    private val name: String
) : PerformanceTrace {
    
    // 在實際實作中，這會是 Firebase Trace 物件
    private var firebaseTrace: Any? = null
    private val attributes = mutableMapOf<String, String>()
    private val metrics = mutableMapOf<String, Long>()
    
    override fun setAttribute(key: String, value: String) {
        attributes[key] = value
        // firebaseTrace?.putAttribute(key, value)
    }
    
    override fun incrementMetric(name: String, value: Long) {
        metrics[name] = (metrics[name] ?: 0) + value
        // firebaseTrace?.incrementMetric(name, value)
    }
    
    override fun putMetric(name: String, value: Long) {
        metrics[name] = value
        // firebaseTrace?.putMetric(name, value)
    }
    
    override fun stop() {
        // firebaseTrace?.stop()
        Logger.d("AndroidFirebasePerf", "Stopped trace: $name")
    }
}

/**
 * Android 無操作追蹤
 */
private class AndroidNoOpTrace : PerformanceTrace {
    override fun setAttribute(key: String, value: String) {}
    override fun incrementMetric(name: String, value: Long) {}
    override fun putMetric(name: String, value: Long) {}
    override fun stop() {}
}

/**
 * Android 平台初始化器
 */
object AndroidFirebaseInitializer {
    
    /**
     * 初始化 Firebase Performance（從 Android 模組調用）
     */
    fun initialize(context: Any) {
        val androidImpl = PlatformFirebasePerformance()
        
        // 這裡會從 Android 模組傳入實際的 Firebase 實例
        // androidImpl.setFirebaseImplementation(FirebasePerformance.getInstance())
        
        // 創建一個 wrapper 來橋接
        val wrapper = object : FirebasePerformanceWrapper {
            override fun startTrace(name: String) = androidImpl.startTrace(name)
            override fun logNetworkRequest(
                url: String,
                httpMethod: String,
                responseCode: Int,
                requestPayloadSize: Long?,
                responsePayloadSize: Long?,
                duration: Duration
            ) = androidImpl.logNetworkRequest(url, httpMethod, responseCode, requestPayloadSize, responsePayloadSize, duration)
            override fun setAttribute(key: String, value: String) = androidImpl.setAttribute(key, value)
            override fun putMetric(name: String, value: Long) = androidImpl.putMetric(name, value)
            override fun setUserId(userId: String?) = androidImpl.setUserId(userId)
            override fun setEnabled(enabled: Boolean) = androidImpl.setEnabled(enabled)
        }
        
        FirebasePerformance.initialize(wrapper)
        
        // 設定平台特定屬性
        FirebasePerformance.setAttribute("platform", "Android")
        FirebasePerformance.setAttribute("os_version", android.os.Build.VERSION.RELEASE)
        FirebasePerformance.setAttribute("device_model", android.os.Build.MODEL)
        
        Logger.d("AndroidFirebaseInit", "Firebase Performance initialized for Android")
    }
}
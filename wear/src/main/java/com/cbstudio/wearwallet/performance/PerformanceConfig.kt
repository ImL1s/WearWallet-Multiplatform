package com.cbstudio.wearwallet.performance

import androidx.compose.runtime.Stable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * 性能優化配置
 * 集中管理應用的性能相關設定
 */
@Stable
object PerformanceConfig {
    
    /**
     * 列表項目延遲載入配置
     */
    object LazyLoading {
        const val INITIAL_LOAD_SIZE = 20
        const val PAGE_SIZE = 10
        const val PREFETCH_DISTANCE = 5
        const val PLACEHOLDER_COUNT = 3
        const val CACHE_SIZE = 100
    }
    
    /**
     * 圖片載入配置
     */
    object ImageLoading {
        const val MEMORY_CACHE_SIZE = 20 * 1024 * 1024L // 20MB
        const val DISK_CACHE_SIZE = 50 * 1024 * 1024L // 50MB
        const val MAX_IMAGE_SIZE = 1024 // pixels
        const val THUMBNAIL_SIZE = 256 // pixels
        const val CACHE_DURATION = 7 * 24 * 60 * 60 * 1000L // 7 days
    }
    
    /**
     * 網路請求配置
     */
    object Network {
        const val CONNECTION_TIMEOUT = 30L // seconds
        const val READ_TIMEOUT = 30L // seconds
        const val WRITE_TIMEOUT = 30L // seconds
        const val MAX_RETRIES = 3
        const val RETRY_DELAY = 1000L // milliseconds
        const val CACHE_SIZE = 10 * 1024 * 1024L // 10MB
    }
    
    /**
     * 動畫配置
     */
    object Animation {
        const val SHORT_DURATION = 150
        const val MEDIUM_DURATION = 300
        const val LONG_DURATION = 500
        const val DISABLED_ALPHA = 0.38f
        const val PRESSED_SCALE = 0.95f
        const val MAX_FPS = 60
    }
    
    /**
     * 資料庫配置
     */
    object Database {
        const val QUERY_TIMEOUT = 5000L // milliseconds
        const val MAX_CONNECTIONS = 5
        const val ENABLE_WAL = true // Write-Ahead Logging
        const val CACHE_SIZE = 2000 // pages
    }
    
    /**
     * 記憶體管理
     */
    object Memory {
        const val LOW_MEMORY_THRESHOLD = 0.15f // 15% available
        const val CRITICAL_MEMORY_THRESHOLD = 0.05f // 5% available
        const val GC_INTERVAL = 60000L // milliseconds
        const val CACHE_TRIM_RATIO = 0.5f
    }
    
    /**
     * Coroutine Dispatchers
     */
    object Dispatchers {
        val IO: CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO.limitedParallelism(4)
        val Default: CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Default
        val Main: CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Main
        val Unconfined: CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Unconfined
    }
    
    /**
     * 電池優化
     */
    object Battery {
        const val LOW_BATTERY_THRESHOLD = 15 // percentage
        const val CRITICAL_BATTERY_THRESHOLD = 5 // percentage
        const val REDUCE_ANIMATIONS = true
        const val REDUCE_NETWORK_CALLS = true
        const val DISABLE_BACKGROUND_SYNC = true
    }
    
    /**
     * Wear OS 特定優化
     */
    object WearOS {
        const val MAX_ITEMS_PER_SCREEN = 5
        const val ENABLE_ROTARY_INPUT = true
        const val AOD_UPDATE_INTERVAL = 60000L // milliseconds
        const val HAPTIC_FEEDBACK = true
        const val COMPLICATION_UPDATE_INTERVAL = 900000L // 15 minutes
    }
    
    /**
     * 調試配置
     */
    object Debug {
        const val ENABLE_LOGGING = true
        const val ENABLE_STRICT_MODE = false
        const val ENABLE_LEAK_CANARY = false
        const val ENABLE_PERFORMANCE_MONITORING = true
        const val LOG_NETWORK_CALLS = true
    }
}

/**
 * 幀率監控器
 */
class FrameRateMonitor {
    private var frameStartTime = 0L
    private var frameCount = 0
    private var totalFrameTime = 0L
    
    fun startFrame() {
        frameStartTime = System.nanoTime()
    }
    
    fun endFrame() {
        if (frameStartTime > 0) {
            val frameTime = System.nanoTime() - frameStartTime
            totalFrameTime += frameTime
            frameCount++
            
            // 檢查是否掉幀 (16ms = 60fps)
            if (frameTime > 16_666_667) {
                logSlowFrame(frameTime)
            }
        }
    }
    
    fun getAverageFPS(): Float {
        return if (frameCount > 0) {
            1_000_000_000f / (totalFrameTime.toFloat() / frameCount)
        } else {
            0f
        }
    }
    
    fun reset() {
        frameCount = 0
        totalFrameTime = 0L
        frameStartTime = 0L
    }
    
    private fun logSlowFrame(frameTimeNanos: Long) {
        val frameTimeMs = frameTimeNanos / 1_000_000
        println("⚠️ Slow frame detected: ${frameTimeMs}ms")
    }
}

/**
 * 記憶體監控器
 */
object MemoryMonitor {
    fun getMemoryUsage(): MemoryInfo {
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        
        return MemoryInfo(
            used = usedMemory,
            total = runtime.totalMemory(),
            max = maxMemory,
            percentage = (usedMemory.toFloat() / maxMemory * 100).toInt()
        )
    }
    
    fun isLowMemory(): Boolean {
        val info = getMemoryUsage()
        return info.percentage > 85
    }
    
    fun isCriticalMemory(): Boolean {
        val info = getMemoryUsage()
        return info.percentage > 95
    }
    
    data class MemoryInfo(
        val used: Long,
        val total: Long,
        val max: Long,
        val percentage: Int
    )
}
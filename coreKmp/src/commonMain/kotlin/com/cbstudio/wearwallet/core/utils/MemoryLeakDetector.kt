package com.cbstudio.wearwallet.core.utils

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * 記憶體洩漏檢測器
 * 
 * 追蹤 Flow 收集器和協程，確保正確清理
 * 
 * Created: 2025-01-17
 */
object MemoryLeakDetector {
    
    private val activeFlows = mutableMapOf<String, FlowTracker>()
    private val activeCoroutines = mutableMapOf<String, CoroutineTracker>()
    private var isEnabled = true
    private val checkInterval = 30.seconds
    private val warningThreshold = 5.minutes
    
    /**
     * 獲取活躍 Flow 數量
     */
    fun getActiveFlowCount(): Int = activeFlows.size
    
    /**
     * 獲取活躍協程數量
     */
    fun getActiveCoroutineCount(): Int = activeCoroutines.size
    
    init {
        // Start monitoring job
        GlobalScope.launch {
            while (isActive) {
                delay(checkInterval)
                checkForLeaks()
            }
        }
    }
    
    /**
     * 追蹤 Flow 收集
     */
    fun <T> Flow<T>.trackCollection(
        identifier: String,
        warningTimeout: Duration = warningThreshold
    ): Flow<T> {
        if (!isEnabled) return this
        
        val trackerId = "${identifier}_${Clock.System.now().toEpochMilliseconds()}"
        
        return this
            .onStart {
                activeFlows[trackerId] = FlowTracker(
                    id = trackerId,
                    name = identifier,
                    startTime = Clock.System.now(),
                    warningTimeout = warningTimeout
                )
                Logger.d("MemoryLeakDetector", "🔍 Tracking Flow: $identifier")
            }
            .onCompletion {
                activeFlows.remove(trackerId)
                Logger.d("MemoryLeakDetector", "✅ Flow completed: $identifier")
            }
            .catch { exception ->
                activeFlows.remove(trackerId)
                Logger.e("MemoryLeakDetector", "❌ Flow error: $identifier", exception)
                throw exception
            }
    }
    
    /**
     * 追蹤協程
     */
    fun CoroutineScope.trackCoroutine(
        identifier: String,
        warningTimeout: Duration = warningThreshold,
        block: suspend CoroutineScope.() -> Unit
    ): Job {
        if (!isEnabled) return launch { block() }
        
        val trackerId = "${identifier}_${Clock.System.now().toEpochMilliseconds()}"
        
        activeCoroutines[trackerId] = CoroutineTracker(
            id = trackerId,
            name = identifier,
            startTime = Clock.System.now(),
            warningTimeout = warningTimeout
        )
        
        return launch {
            try {
                Logger.d("MemoryLeakDetector", "🔍 Tracking Coroutine: $identifier")
                block()
            } finally {
                activeCoroutines.remove(trackerId)
                Logger.d("MemoryLeakDetector", "✅ Coroutine completed: $identifier")
            }
        }
    }
    
    /**
     * 檢查潛在的洩漏
     */
    private fun checkForLeaks() {
        val now = Clock.System.now()
        val leakedFlows = mutableListOf<FlowTracker>()
        val leakedCoroutines = mutableListOf<CoroutineTracker>()
        
        // Check flows
        activeFlows.values.forEach { tracker ->
            val duration = now - tracker.startTime
            if (duration > tracker.warningTimeout) {
                leakedFlows.add(tracker)
            }
        }
        
        // Check coroutines
        activeCoroutines.values.forEach { tracker ->
            val duration = now - tracker.startTime
            if (duration > tracker.warningTimeout) {
                leakedCoroutines.add(tracker)
            }
        }
        
        // Report leaks
        if (leakedFlows.isNotEmpty() || leakedCoroutines.isNotEmpty()) {
            reportLeaks(leakedFlows, leakedCoroutines)
        }
    }
    
    /**
     * 報告洩漏
     */
    private fun reportLeaks(
        flows: List<FlowTracker>,
        coroutines: List<CoroutineTracker>
    ) {
        val report = buildString {
            appendLine("⚠️ POTENTIAL MEMORY LEAKS DETECTED ⚠️")
            appendLine("=====================================")
            
            if (flows.isNotEmpty()) {
                appendLine("\n📊 Long-running Flows (${flows.size}):")
                flows.forEach { tracker ->
                    val duration = Clock.System.now() - tracker.startTime
                    appendLine("  • ${tracker.name}: running for ${duration.inWholeMinutes} minutes")
                }
            }
            
            if (coroutines.isNotEmpty()) {
                appendLine("\n🔄 Long-running Coroutines (${coroutines.size}):")
                coroutines.forEach { tracker ->
                    val duration = Clock.System.now() - tracker.startTime
                    appendLine("  • ${tracker.name}: running for ${duration.inWholeMinutes} minutes")
                }
            }
            
            appendLine("\n💡 Suggestions:")
            appendLine("  • Ensure proper cancellation in onCleared()")
            appendLine("  • Use viewModelScope or lifecycleScope")
            appendLine("  • Cancel flows when not needed")
            appendLine("  • Check for infinite loops")
        }
        
        Logger.w("MemoryLeakDetector", report)
    }
    
    /**
     * 獲取當前狀態報告
     */
    fun getStatusReport(): LeakDetectorReport {
        return LeakDetectorReport(
            activeFlowCount = activeFlows.size,
            activeCoroutineCount = activeCoroutines.size,
            activeFlows = activeFlows.values.map { it.toInfo() },
            activeCoroutines = activeCoroutines.values.map { it.toInfo() }
        )
    }
    
    /**
     * 清理所有追蹤
     */
    fun clearAll() {
        activeFlows.clear()
        activeCoroutines.clear()
        Logger.d("MemoryLeakDetector", "All trackers cleared")
    }
    
    /**
     * 啟用/禁用檢測
     */
    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        Logger.d("MemoryLeakDetector", "Leak detection ${if (enabled) "enabled" else "disabled"}")
    }
    
    /**
     * Flow 追蹤器
     */
    private data class FlowTracker(
        val id: String,
        val name: String,
        val startTime: kotlinx.datetime.Instant,
        val warningTimeout: Duration
    ) {
        fun toInfo() = TrackerInfo(
            name = name,
            duration = Clock.System.now() - startTime,
            isWarning = (Clock.System.now() - startTime) > warningTimeout
        )
    }
    
    /**
     * 協程追蹤器
     */
    private data class CoroutineTracker(
        val id: String,
        val name: String,
        val startTime: kotlinx.datetime.Instant,
        val warningTimeout: Duration
    ) {
        fun toInfo() = TrackerInfo(
            name = name,
            duration = Clock.System.now() - startTime,
            isWarning = (Clock.System.now() - startTime) > warningTimeout
        )
    }
}

/**
 * 追蹤器資訊
 */
data class TrackerInfo(
    val name: String,
    val duration: Duration,
    val isWarning: Boolean
)

/**
 * 洩漏檢測報告
 */
data class LeakDetectorReport(
    val activeFlowCount: Int,
    val activeCoroutineCount: Int,
    val activeFlows: List<TrackerInfo>,
    val activeCoroutines: List<TrackerInfo>
) {
    val hasWarnings: Boolean
        get() = activeFlows.any { it.isWarning } || activeCoroutines.any { it.isWarning }
    
    fun printSummary() {
        println("""
            ╔════════════════════════════════════════╗
            ║      Memory Leak Detector Report       ║
            ╚════════════════════════════════════════╝
            
            📊 Active Flows: $activeFlowCount
            🔄 Active Coroutines: $activeCoroutineCount
            ⚠️ Warnings: ${if (hasWarnings) "YES" else "NO"}
            
            ${if (activeFlows.isNotEmpty()) {
                "Active Flows:\n" + activeFlows.joinToString("\n") { tracker ->
                    val emoji = if (tracker.isWarning) "⚠️" else "✅"
                    "  $emoji ${tracker.name}: ${tracker.duration.inWholeSeconds}s"
                }
            } else ""}
            
            ${if (activeCoroutines.isNotEmpty()) {
                "Active Coroutines:\n" + activeCoroutines.joinToString("\n") { tracker ->
                    val emoji = if (tracker.isWarning) "⚠️" else "✅"
                    "  $emoji ${tracker.name}: ${tracker.duration.inWholeSeconds}s"
                }
            } else ""}
        """.trimIndent())
    }
}

/**
 * Extension functions for ViewModels
 */
fun CoroutineScope.launchTracked(
    name: String,
    block: suspend CoroutineScope.() -> Unit
): Job = MemoryLeakDetector.run { 
    this@launchTracked.trackCoroutine(name, block = block)
}

fun <T> Flow<T>.collectTracked(
    scope: CoroutineScope,
    name: String,
    collector: suspend (T) -> Unit
): Job = scope.launch {
    MemoryLeakDetector.run {
        this@collectTracked
            .trackCollection(name)
            .collect(collector)
    }
}
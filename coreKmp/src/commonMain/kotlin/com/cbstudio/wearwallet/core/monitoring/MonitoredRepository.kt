package com.cbstudio.wearwallet.core.monitoring

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.utils.Logger
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.milliseconds

/**
 * 監控 Repository 的網路請求
 * 
 * 自動追蹤 API 呼叫效能並上報到 Firebase
 * 
 * Created: 2025-01-17
 */

/**
 * 監控網路請求的擴展函數
 */
suspend inline fun <T> monitorNetworkRequest(
    url: String,
    method: String = "GET",
    requestSize: Long? = null,
    crossinline block: suspend () -> Result<T>
): Result<T> {
    val requestId = "${method}_${url}_${Clock.System.now().toEpochMilliseconds()}"
    val startTime = Clock.System.now()
    
    // 開始監控
    GlobalMetrics.networkMonitor.startRequest(
        requestId = requestId,
        url = url,
        method = method,
        requestSize = requestSize
    )
    
    return try {
        val result = block()
        val duration = Clock.System.now() - startTime
        
        when (result) {
            is Result.Success -> {
                // 成功請求
                GlobalMetrics.networkMonitor.endRequest(
                    requestId = requestId,
                    responseCode = 200,
                    responseSize = estimateResponseSize(result.data)
                )
                
                GlobalMetrics.collector.recordHistogram(
                    "network_latency_${method.lowercase()}", 
                    duration.inWholeMilliseconds
                )
                
                Logger.d("MonitoredRequest", 
                    "✅ $method $url completed in ${duration.inWholeMilliseconds}ms")
            }
            
            is Result.Failure -> {
                // 失敗請求
                GlobalMetrics.networkMonitor.failRequest(requestId, result.error)
                
                GlobalMetrics.collector.incrementCounter("network_errors")
                
                Logger.e("MonitoredRequest", 
                    "❌ $method $url failed: ${result.error.message}")
            }
            
            is Result.Loading -> {
                // Loading 狀態不記錄
            }
        }
        
        result
    } catch (e: Exception) {
        GlobalMetrics.networkMonitor.failRequest(requestId, e)
        GlobalMetrics.collector.incrementCounter("network_exceptions")
        
        Logger.e("MonitoredRequest", 
            "💥 $method $url exception: ${e.message}", e)
        
        Result.Failure(e)
    }
}

/**
 * 估算響應大小
 */
fun <T> estimateResponseSize(data: T): Long {
    return when (data) {
        is String -> data.length.toLong()
        is ByteArray -> data.size.toLong()
        is List<*> -> (data.size * 100).toLong() // 假設每項 100 bytes
        is Map<*, *> -> (data.size * 50).toLong() // 假設每個鍵值對 50 bytes
        else -> 100L // 預設 100 bytes
    }
}

/**
 * 監控批量請求
 */
suspend inline fun <T> monitorBatchRequests(
    requests: List<Pair<String, String>>, // (url, method) pairs
    crossinline block: suspend (String, String) -> Result<T>
): List<Result<T>> {
    val trace = FirebasePerformance.startTrace("BatchRequests")
    trace.putMetric("batch_size", requests.size.toLong())
    
    val startTime = Clock.System.now()
    val results = mutableListOf<Result<T>>()
    var successCount = 0
    var failureCount = 0
    
    try {
        requests.forEach { (url, method) ->
            val result = monitorNetworkRequest(url, method) {
                block(url, method)
            }
            
            when (result) {
                is Result.Success -> successCount++
                is Result.Failure -> failureCount++
                else -> {}
            }
            
            results.add(result)
        }
        
        val duration = Clock.System.now() - startTime
        
        trace.putMetric("success_count", successCount.toLong())
        trace.putMetric("failure_count", failureCount.toLong())
        trace.putMetric("total_duration_ms", duration.inWholeMilliseconds)
        trace.putMetric("avg_duration_ms", duration.inWholeMilliseconds / requests.size)
        trace.setAttribute("success_rate", "${(successCount * 100) / requests.size}%")
        
        Logger.d("BatchRequests", 
            "Completed ${requests.size} requests in ${duration.inWholeMilliseconds}ms " +
            "(Success: $successCount, Failure: $failureCount)")
        
    } finally {
        trace.stop()
    }
    
    return results
}

/**
 * 監控快取的 Repository 請求
 */
suspend inline fun <T> monitorCachedRequest(
    cacheKey: String,
    url: String,
    method: String = "GET",
    crossinline cacheProvider: suspend () -> T?,
    crossinline networkProvider: suspend () -> Result<T>
): Result<T> {
    val trace = FirebasePerformance.startTrace("CachedRequest")
    trace.setAttribute("cache_key", cacheKey)
    trace.setAttribute("url", url)
    
    try {
        // 先檢查快取
        val cached = cacheProvider()
        if (cached != null) {
            trace.setAttribute("cache_hit", "true")
            trace.incrementMetric("cache_hits")
            
            GlobalMetrics.collector.incrementCounter("cache_hits")
            
            Logger.d("CachedRequest", "💾 Cache hit for $cacheKey")
            return Result.Success(cached)
        }
        
        // 快取未命中，發起網路請求
        trace.setAttribute("cache_hit", "false")
        trace.incrementMetric("cache_misses")
        
        GlobalMetrics.collector.incrementCounter("cache_misses")
        
        return monitorNetworkRequest(url, method) {
            networkProvider()
        }
        
    } finally {
        trace.stop()
    }
}

/**
 * 重試策略監控
 */
suspend inline fun <T> monitorRetryableRequest(
    url: String,
    method: String = "GET",
    maxRetries: Int = 3,
    crossinline block: suspend () -> Result<T>
): Result<T> {
    val trace = FirebasePerformance.startTrace("RetryableRequest")
    trace.setAttribute("url", url)
    trace.setAttribute("method", method)
    trace.putMetric("max_retries", maxRetries.toLong())
    
    var attempt = 0
    var lastError: Exception? = null
    
    try {
        while (attempt < maxRetries) {
            attempt++
            trace.incrementMetric("attempt_count")
            
            val result = monitorNetworkRequest(url, method) {
                block()
            }
            
            when (result) {
                is Result.Success -> {
                    trace.setAttribute("success_attempt", attempt.toString())
                    trace.setAttribute("retried", (attempt > 1).toString())
                    
                    if (attempt > 1) {
                        GlobalMetrics.collector.incrementCounter("retry_success")
                        Logger.d("RetryableRequest", 
                            "✅ Succeeded after $attempt attempts: $url")
                    }
                    
                    return result
                }
                
                is Result.Failure -> {
                    lastError = result.error
                    trace.incrementMetric("failure_count")
                    
                    if (attempt < maxRetries) {
                        val delay = calculateRetryDelay(attempt)
                        Logger.d("RetryableRequest", 
                            "⏳ Retry $attempt/$maxRetries after ${delay}ms: $url")
                        
                        kotlinx.coroutines.delay(delay)
                    }
                }
                
                else -> return result
            }
        }
        
        trace.setAttribute("exhausted_retries", "true")
        GlobalMetrics.collector.incrementCounter("retry_exhausted")
        
        Logger.e("RetryableRequest", 
            "❌ All $maxRetries attempts failed for $url")
        
        return Result.Failure(
            lastError ?: Exception("All retry attempts failed")
        )
        
    } finally {
        trace.stop()
    }
}

/**
 * 計算重試延遲（指數退避）
 */
fun calculateRetryDelay(attempt: Int): Long {
    val baseDelay = 1000L // 1 second
    val maxDelay = 30000L // 30 seconds
    val delay = baseDelay * (1 shl (attempt - 1)) // 2^(attempt-1)
    return delay.coerceAtMost(maxDelay)
}

/**
 * 監控分頁請求
 */
suspend inline fun <T> monitorPaginatedRequest(
    baseUrl: String,
    pageSize: Int,
    crossinline fetcher: suspend (page: Int) -> Result<List<T>>
): Result<List<T>> {
    val trace = FirebasePerformance.startTrace("PaginatedRequest")
    trace.setAttribute("base_url", baseUrl)
    trace.putMetric("page_size", pageSize.toLong())
    
    val allItems = mutableListOf<T>()
    var currentPage = 0
    var hasMore = true
    var totalFetched = 0
    
    try {
        while (hasMore) {
            currentPage++
            trace.incrementMetric("page_count")
            
            val pageUrl = "$baseUrl?page=$currentPage&size=$pageSize"
            val result = monitorNetworkRequest(pageUrl, "GET") {
                fetcher(currentPage)
            }
            
            when (result) {
                is Result.Success -> {
                    val items = result.data
                    allItems.addAll(items)
                    totalFetched += items.size
                    
                    trace.putMetric("total_items", totalFetched.toLong())
                    
                    hasMore = items.size == pageSize
                    
                    Logger.d("PaginatedRequest", 
                        "📄 Page $currentPage: ${items.size} items (Total: $totalFetched)")
                }
                
                is Result.Failure -> {
                    trace.setAttribute("failed_at_page", currentPage.toString())
                    Logger.e("PaginatedRequest", 
                        "❌ Failed at page $currentPage: ${result.error.message}")
                    
                    return if (allItems.isNotEmpty()) {
                        // 返回部分結果
                        Result.Success(allItems)
                    } else {
                        result
                    }
                }
                
                else -> return result
            }
        }
        
        trace.setAttribute("complete", "true")
        return Result.Success(allItems)
        
    } finally {
        trace.stop()
    }
}

/**
 * 效能警報檢查器
 */
object PerformanceAlerts {
    
    private const val SLOW_REQUEST_THRESHOLD = 3000L // 3 seconds
    private const val HIGH_ERROR_RATE_THRESHOLD = 0.1 // 10%
    private const val LOW_CACHE_HIT_THRESHOLD = 0.5 // 50%
    
    /**
     * 檢查效能警報
     */
    fun checkAlerts() {
        val report = GlobalMetrics.collector.getReport()
        
        // 檢查慢請求
        report.histograms.forEach { (name, stats) ->
            if (name.startsWith("network_latency_") && stats.p90 > SLOW_REQUEST_THRESHOLD) {
                logAlert(
                    "SLOW_REQUESTS",
                    "$name P90 latency is ${stats.p90}ms (threshold: ${SLOW_REQUEST_THRESHOLD}ms)"
                )
            }
        }
        
        // 檢查錯誤率
        val totalRequests = report.counters["network_requests"] ?: 0
        val totalErrors = report.counters["network_errors"] ?: 0
        if (totalRequests > 0) {
            val errorRate = totalErrors.toDouble() / totalRequests
            if (errorRate > HIGH_ERROR_RATE_THRESHOLD) {
                logAlert(
                    "HIGH_ERROR_RATE",
                    "Network error rate is ${(errorRate * 100).toInt()}% (threshold: ${(HIGH_ERROR_RATE_THRESHOLD * 100).toInt()}%)"
                )
            }
        }
        
        // 檢查快取命中率
        val cacheHits = report.counters["cache_hits"] ?: 0
        val cacheMisses = report.counters["cache_misses"] ?: 0
        val totalCacheAccess = cacheHits + cacheMisses
        if (totalCacheAccess > 0) {
            val hitRate = cacheHits.toDouble() / totalCacheAccess
            if (hitRate < LOW_CACHE_HIT_THRESHOLD) {
                logAlert(
                    "LOW_CACHE_HIT_RATE",
                    "Cache hit rate is ${(hitRate * 100).toInt()}% (threshold: ${(LOW_CACHE_HIT_THRESHOLD * 100).toInt()}%)"
                )
            }
        }
    }
    
    private fun logAlert(type: String, message: String) {
        FirebasePerformance.setAttribute("alert_type", type)
        FirebasePerformance.putMetric("alert_${type.lowercase()}", 1)
        
        Logger.w("PerformanceAlert", "⚠️ [$type] $message")
    }
}
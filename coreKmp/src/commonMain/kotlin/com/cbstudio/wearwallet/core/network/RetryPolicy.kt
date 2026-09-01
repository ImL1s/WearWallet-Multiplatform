package com.cbstudio.wearwallet.core.network

import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random
import kotlin.reflect.KClass

/**
 * 重試策略配置
 */
data class RetryPolicy(
    val maxAttempts: Int = 3,
    val initialDelayMillis: Long = 1000,
    val maxDelayMillis: Long = 30000,
    val backoffMultiplier: Double = 2.0,
    val jitterEnabled: Boolean = true,
    val retryableExceptions: Set<KClass<out Exception>> = setOf(
        // 這些異常類型將在平台特定代碼中定義
    ),
    val retryableStatusCodes: Set<Int> = setOf(
        408, // Request Timeout
        429, // Too Many Requests
        500, // Internal Server Error
        502, // Bad Gateway
        503, // Service Unavailable
        504  // Gateway Timeout
    )
)

/**
 * 重試執行器
 * 提供指數退避和抖動的重試機制
 */
class RetryExecutor(
    private val policy: RetryPolicy = RetryPolicy()
) {
    /**
     * 執行帶重試的操作
     */
    suspend fun <T> execute(
        operation: suspend () -> T
    ): T {
        var lastException: Exception? = null
        var attempt = 0
        
        while (attempt < policy.maxAttempts) {
            try {
                // 執行操作
                return operation()
            } catch (e: Exception) {
                lastException = e
                attempt++
                
                // 檢查是否應該重試
                if (!shouldRetry(e, attempt)) {
                    throw e
                }
                
                // 計算延遲時間
                val delay = calculateDelay(attempt)
                
                println("⚠️ 操作失敗 (嘗試 $attempt/${policy.maxAttempts}): ${e.message}")
                println("⏱️ ${delay}ms 後重試...")
                
                // 等待後重試
                delay(delay)
            }
        }
        
        throw RetryExhaustedException(
            "重試 ${policy.maxAttempts} 次後仍然失敗",
            lastException
        )
    }
    
    /**
     * 執行帶重試的 HTTP 操作
     */
    suspend fun <T> executeHttp(
        operation: suspend () -> HttpResponse<T>
    ): HttpResponse<T> {
        var lastException: Exception? = null
        var lastResponse: HttpResponse<T>? = null
        var attempt = 0
        
        while (attempt < policy.maxAttempts) {
            try {
                // 執行 HTTP 操作
                val response = operation()
                
                // 檢查 HTTP 狀態碼
                if (response.statusCode in policy.retryableStatusCodes) {
                    lastResponse = response
                    attempt++
                    
                    if (attempt >= policy.maxAttempts) {
                        throw HttpRetryException(
                            "HTTP ${response.statusCode}: ${response.statusMessage}",
                            response.statusCode
                        )
                    }
                    
                    val delay = calculateDelay(attempt)
                    
                    // 處理 Rate Limit
                    val retryAfter = response.headers["Retry-After"]?.toLongOrNull()
                    val actualDelay = if (retryAfter != null) {
                        min(retryAfter * 1000, policy.maxDelayMillis)
                    } else {
                        delay
                    }
                    
                    println("⚠️ HTTP ${response.statusCode} (嘗試 $attempt/${policy.maxAttempts})")
                    println("⏱️ ${actualDelay}ms 後重試...")
                    
                    delay(actualDelay)
                } else if (response.isSuccess) {
                    return response
                } else {
                    throw HttpException(
                        "HTTP ${response.statusCode}: ${response.statusMessage}",
                        response.statusCode
                    )
                }
            } catch (e: Exception) {
                when (e) {
                    is HttpException, is HttpRetryException -> throw e
                    else -> {
                        lastException = e
                        attempt++
                        
                        if (!shouldRetry(e, attempt)) {
                            throw e
                        }
                        
                        val delay = calculateDelay(attempt)
                        
                        println("⚠️ 網路錯誤 (嘗試 $attempt/${policy.maxAttempts}): ${e.message}")
                        println("⏱️ ${delay}ms 後重試...")
                        
                        delay(delay)
                    }
                }
            }
        }
        
        throw RetryExhaustedException(
            "重試 ${policy.maxAttempts} 次後仍然失敗",
            lastException
        )
    }
    
    /**
     * 判斷是否應該重試
     */
    private fun shouldRetry(exception: Exception, attempt: Int): Boolean {
        if (attempt >= policy.maxAttempts) {
            return false
        }
        
        // 檢查異常類型
        return policy.retryableExceptions.any { 
            it.isInstance(exception) 
        }
    }
    
    /**
     * 計算重試延遲時間（指數退避 + 抖動）
     */
    private fun calculateDelay(attempt: Int): Long {
        // 指數退避
        val exponentialDelay = policy.initialDelayMillis * 
            policy.backoffMultiplier.pow(attempt - 1).toLong()
        
        // 限制最大延遲
        val cappedDelay = min(exponentialDelay, policy.maxDelayMillis)
        
        // 添加抖動
        return if (policy.jitterEnabled) {
            val jitter = Random.nextLong(0, cappedDelay / 2)
            cappedDelay + jitter
        } else {
            cappedDelay
        }
    }
}

/**
 * HTTP 響應包裝
 */
data class HttpResponse<T>(
    val statusCode: Int,
    val statusMessage: String,
    val headers: Map<String, String>,
    val body: T?
) {
    val isSuccess: Boolean
        get() = statusCode in 200..299
}

/**
 * 重試耗盡異常
 */
class RetryExhaustedException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * HTTP 異常
 */
open class HttpException(
    message: String,
    val statusCode: Int
) : Exception(message)

/**
 * HTTP 重試異常
 */
class HttpRetryException(
    message: String,
    statusCode: Int
) : HttpException(message, statusCode)

/**
 * 電路斷路器
 * 防止對故障服務的持續請求
 */
class CircuitBreaker(
    private val failureThreshold: Int = 5,
    private val resetTimeoutMillis: Long = 60000,
    private val halfOpenMaxAttempts: Int = 3
) {
    enum class State {
        CLOSED,     // 正常狀態
        OPEN,       // 斷路狀態
        HALF_OPEN   // 半開狀態
    }
    
    private var state = State.CLOSED
    private var failureCount = 0
    private var lastFailureTime = 0L
    private var halfOpenAttempts = 0
    
    /**
     * 執行受保護的操作
     */
    suspend fun <T> execute(
        operation: suspend () -> T
    ): T {
        val currentTimeMillis = Clock.System.now().toEpochMilliseconds()
        
        when (state) {
            State.OPEN -> {
                // 檢查是否可以嘗試恢復
                if (currentTimeMillis - lastFailureTime > resetTimeoutMillis) {
                    state = State.HALF_OPEN
                    halfOpenAttempts = 0
                    println("🔄 電路斷路器進入半開狀態")
                } else {
                    throw CircuitBreakerOpenException("電路斷路器已打開")
                }
            }
            State.HALF_OPEN -> {
                if (halfOpenAttempts >= halfOpenMaxAttempts) {
                    state = State.OPEN
                    lastFailureTime = currentTimeMillis
                    println("❌ 電路斷路器重新打開")
                    throw CircuitBreakerOpenException("電路斷路器已打開")
                }
                halfOpenAttempts++
            }
            State.CLOSED -> {
                // 正常執行
            }
        }
        
        return try {
            val result = operation()
            onSuccess()
            result
        } catch (e: Exception) {
            onFailure()
            throw e
        }
    }
    
    private fun onSuccess() {
        when (state) {
            State.HALF_OPEN -> {
                state = State.CLOSED
                failureCount = 0
                println("✅ 電路斷路器恢復關閉狀態")
            }
            State.CLOSED -> {
                failureCount = 0
            }
            State.OPEN -> {
                // 不應該發生
            }
        }
    }
    
    private fun onFailure() {
        val currentTimeMillis = Clock.System.now().toEpochMilliseconds()
        
        when (state) {
            State.CLOSED -> {
                failureCount++
                if (failureCount >= failureThreshold) {
                    state = State.OPEN
                    lastFailureTime = currentTimeMillis
                    println("⚠️ 電路斷路器打開（失敗 $failureCount 次）")
                }
            }
            State.HALF_OPEN -> {
                state = State.OPEN
                lastFailureTime = currentTimeMillis
                println("❌ 電路斷路器重新打開")
            }
            State.OPEN -> {
                // 保持打開狀態
            }
        }
    }
    
    fun getState(): State = state
    
    fun reset() {
        state = State.CLOSED
        failureCount = 0
        halfOpenAttempts = 0
    }
}

/**
 * 電路斷路器打開異常
 */
class CircuitBreakerOpenException(
    message: String
) : Exception(message)
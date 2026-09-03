package com.cbstudio.wearwallet.core.utils

import com.cbstudio.wearwallet.core.common.Result
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * 重試策略
 * 
 * 提供不同的重試策略和錯誤處理機制
 * 
 * Created: 2025-01-17
 */
sealed class RetryPolicy {
    
    /**
     * 固定延遲重試
     */
    data class Fixed(
        val maxAttempts: Int = 3,
        val delay: Duration = 1.seconds
    ) : RetryPolicy()
    
    /**
     * 指數退避重試
     */
    data class Exponential(
        val maxAttempts: Int = 3,
        val initialDelay: Duration = 1.seconds,
        val maxDelay: Duration = 30.seconds,
        val factor: Double = 2.0
    ) : RetryPolicy()
    
    /**
     * 線性退避重試
     */
    data class Linear(
        val maxAttempts: Int = 3,
        val initialDelay: Duration = 1.seconds,
        val increment: Duration = 1.seconds
    ) : RetryPolicy()
    
    /**
     * 自定義重試
     */
    data class Custom(
        val shouldRetry: (attempt: Int, error: Throwable) -> Boolean,
        val delayCalculator: (attempt: Int) -> Duration
    ) : RetryPolicy()
    
    /**
     * 不重試
     */
    object None : RetryPolicy()
}

/**
 * Flow 重試擴展
 */
fun <T> Flow<Result<T>>.withRetryPolicy(
    policy: RetryPolicy,
    onRetry: ((attempt: Int, error: Throwable) -> Unit)? = null
): Flow<Result<T>> = when (policy) {
    is RetryPolicy.None -> this.catch { emit(Result.Failure(Exception(it.message, it))) }
    
    is RetryPolicy.Fixed -> retryWhen { cause, attempt ->
        val shouldRetry = attempt < policy.maxAttempts - 1 && isRetriableError(cause)
        if (shouldRetry) {
            onRetry?.invoke(attempt.toInt() + 1, cause)
            delay(policy.delay)
        }
        shouldRetry
    }.catch { emit(Result.Failure(Exception(it.message, it))) }
    
    is RetryPolicy.Exponential -> retryWhen { cause, attempt ->
        val shouldRetry = attempt < policy.maxAttempts - 1 && isRetriableError(cause)
        if (shouldRetry) {
            onRetry?.invoke(attempt.toInt() + 1, cause)
            val delayTime = kotlin.math.min(
                (policy.initialDelay.inWholeMilliseconds * policy.factor.pow(attempt.toInt())).toLong(),
                policy.maxDelay.inWholeMilliseconds
            )
            delay(delayTime.milliseconds)
        }
        shouldRetry
    }.catch { emit(Result.Failure(Exception(it.message, it))) }
    
    is RetryPolicy.Linear -> retryWhen { cause, attempt ->
        val shouldRetry = attempt < policy.maxAttempts - 1 && isRetriableError(cause)
        if (shouldRetry) {
            onRetry?.invoke(attempt.toInt() + 1, cause)
            val delayTime = policy.initialDelay + (policy.increment * attempt.toInt())
            delay(delayTime)
        }
        shouldRetry
    }.catch { emit(Result.Failure(Exception(it.message, it))) }
    
    is RetryPolicy.Custom -> retryWhen { cause, attempt ->
        val shouldRetry = policy.shouldRetry(attempt.toInt(), cause)
        if (shouldRetry) {
            onRetry?.invoke(attempt.toInt() + 1, cause)
            delay(policy.delayCalculator(attempt.toInt()))
        }
        shouldRetry
    }.catch { emit(Result.Failure(Exception(it.message, it))) }
}

/**
 * 判斷是否為可重試的錯誤
 */
private fun isRetriableError(error: Throwable): Boolean {
    return when (error) {
        is NetworkException -> true
        is TimeoutException -> true
        is ServiceUnavailableException -> true
        is RateLimitException -> true
        else -> false
    }
}

/**
 * 錯誤處理器
 */
object ErrorHandler {
    
    /**
     * 處理錯誤並返回友好訊息
     */
    fun handleError(error: Throwable): ErrorInfo {
        return when (error) {
            is NetworkException -> ErrorInfo(
                type = ErrorType.NETWORK,
                message = "網路連接失敗，請檢查網路設定",
                userAction = "請檢查網路連接後重試",
                isRetriable = true
            )
            
            is AuthenticationException -> ErrorInfo(
                type = ErrorType.AUTHENTICATION,
                message = "身份驗證失敗",
                userAction = "請重新登入",
                isRetriable = false
            )
            
            is InsufficientBalanceException -> ErrorInfo(
                type = ErrorType.INSUFFICIENT_BALANCE,
                message = "餘額不足",
                userAction = "請確保帳戶有足夠餘額",
                isRetriable = false
            )
            
            is InvalidAddressException -> ErrorInfo(
                type = ErrorType.VALIDATION,
                message = "無效的錢包地址",
                userAction = "請檢查地址格式",
                isRetriable = false
            )
            
            is TimeoutException -> ErrorInfo(
                type = ErrorType.TIMEOUT,
                message = "操作超時",
                userAction = "請稍後再試",
                isRetriable = true
            )
            
            is RateLimitException -> ErrorInfo(
                type = ErrorType.RATE_LIMIT,
                message = "請求過於頻繁",
                userAction = "請稍等 ${error.retryAfter} 秒後再試",
                isRetriable = true,
                retryAfter = error.retryAfter
            )
            
            is ServiceUnavailableException -> ErrorInfo(
                type = ErrorType.SERVICE_UNAVAILABLE,
                message = "服務暫時不可用",
                userAction = "請稍後再試",
                isRetriable = true
            )
            
            else -> ErrorInfo(
                type = ErrorType.UNKNOWN,
                message = error.message ?: "未知錯誤",
                userAction = "請聯繫客服",
                isRetriable = false
            )
        }
    }
    
    /**
     * 記錄錯誤
     */
    fun logError(error: Throwable, context: String) {
        val errorInfo = handleError(error)
        Logger.e(
            "ErrorHandler",
            """
            Context: $context
            Type: ${errorInfo.type}
            Message: ${errorInfo.message}
            Retriable: ${errorInfo.isRetriable}
            """.trimIndent(),
            error
        )
    }
}

/**
 * 錯誤資訊
 */
data class ErrorInfo(
    val type: ErrorType,
    val message: String,
    val userAction: String,
    val isRetriable: Boolean,
    val retryAfter: Int? = null
)

/**
 * 錯誤類型
 */
enum class ErrorType {
    NETWORK,
    AUTHENTICATION,
    INSUFFICIENT_BALANCE,
    VALIDATION,
    TIMEOUT,
    RATE_LIMIT,
    SERVICE_UNAVAILABLE,
    UNKNOWN
}

/**
 * 自定義異常
 */
class NetworkException(message: String? = null, cause: Throwable? = null) : 
    Exception(message, cause)

class AuthenticationException(message: String? = null) : 
    Exception(message)

class InsufficientBalanceException(
    val required: String,
    val available: String
) : Exception("Insufficient balance: required $required, available $available")

class InvalidAddressException(val address: String) : 
    Exception("Invalid address: $address")

class TimeoutException(message: String? = null) : 
    Exception(message)

class RateLimitException(val retryAfter: Int) : 
    Exception("Rate limited. Retry after $retryAfter seconds")

class ServiceUnavailableException(message: String? = null) : 
    Exception(message)

/**
 * 使用範例
 */
suspend fun exampleUsage() {
    // 固定延遲重試
    val fixedRetryFlow = flow<Result<String>> {
        emit(Result.Failure(NetworkException()))
    }.withRetryPolicy(
        policy = RetryPolicy.Fixed(maxAttempts = 3, delay = 2.seconds),
        onRetry = { attempt, error ->
            Logger.d("Retry", "Attempt $attempt after error: ${error.message}")
        }
    )
    
    // 指數退避重試
    val exponentialRetryFlow = flow<Result<String>> {
        emit(Result.Failure(TimeoutException()))
    }.withRetryPolicy(
        policy = RetryPolicy.Exponential(
            maxAttempts = 5,
            initialDelay = 1.seconds,
            maxDelay = 30.seconds
        )
    )
    
    // 自定義重試策略
    val customRetryFlow = flow<Result<String>> {
        emit(Result.Failure(ServiceUnavailableException()))
    }.withRetryPolicy(
        policy = RetryPolicy.Custom(
            shouldRetry = { attempt, error ->
                attempt < 3 && error is ServiceUnavailableException
            },
            delayCalculator = { attempt ->
                (attempt * 2).seconds
            }
        )
    )
}
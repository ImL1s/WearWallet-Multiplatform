package com.cbstudio.wearwallet.core.utils

import com.cbstudio.wearwallet.core.common.Result as CoreResult
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * RetryPolicy 單元測試
 * 
 * Created: 2025-01-17
 */
class RetryPolicyTest {
    
    private var attemptCount = 0
    private val retryAttempts = mutableListOf<Int>()
    
    @BeforeTest
    fun setup() {
        attemptCount = 0
        retryAttempts.clear()
    }
    
    @Test
    fun testFixedRetryPolicy_Success() = runTest {
        // Given - 第二次嘗試成功的 Flow
        val flow = flow<CoreResult<String>> {
            attemptCount++
            if (attemptCount == 1) {
                throw NetworkException("Network error")
            }
            emit(CoreResult.Success("Success"))
        }
        
        // When - 應用固定延遲重試策略
        val result = flow.withRetryPolicy(
            policy = RetryPolicy.Fixed(maxAttempts = 3, delay = 10.milliseconds),
            onRetry = { attempt, _ -> retryAttempts.add(attempt) }
        ).first()
        
        // Then
        assertTrue(result is CoreResult.Success)
        assertEquals("Success", (result as CoreResult.Success).data)
        assertEquals(2, attemptCount)
        assertEquals(listOf(1), retryAttempts)
    }
    
    @Test
    fun testFixedRetryPolicy_MaxAttemptsReached() = runTest {
        // Given - 總是失敗的 Flow
        val flow = flow<CoreResult<String>> {
            attemptCount++
            throw NetworkException("Network error")
        }
        
        // When - 應用固定延遲重試策略
        val result = flow.withRetryPolicy(
            policy = RetryPolicy.Fixed(maxAttempts = 3, delay = 10.milliseconds),
            onRetry = { attempt, _ -> retryAttempts.add(attempt) }
        ).first()
        
        // Then
        assertTrue(result is CoreResult.Failure)
        assertEquals(3, attemptCount) // 原始嘗試 + 2次重試
        assertEquals(listOf(1, 2), retryAttempts)
    }
    
    @Test
    fun testExponentialRetryPolicy() = runTest {
        // Given - 第三次嘗試成功的 Flow
        val flow = flow<CoreResult<String>> {
            attemptCount++
            when (attemptCount) {
                1, 2 -> throw TimeoutException("Timeout")
                else -> emit(CoreResult.Success("Success"))
            }
        }
        
        // When - 應用指數退避重試策略
        val startTime = TimeSource.Monotonic.markNow()
        val result = flow.withRetryPolicy(
            policy = RetryPolicy.Exponential(
                maxAttempts = 4,
                initialDelay = 10.milliseconds,
                maxDelay = 100.milliseconds,
                factor = 2.0
            ),
            onRetry = { attempt, _ -> retryAttempts.add(attempt) }
        ).first()
        val duration = startTime.elapsedNow()
        
        // Then
        assertTrue(result is CoreResult.Success)
        assertEquals(3, attemptCount)
        assertEquals(listOf(1, 2), retryAttempts)
        // 注意：在 runTest 協程測試環境中，delay 是虛擬的（不會實際等待）
        // 因此不驗證實際經過的時間，只驗證重試邏輯正確性
        // 真實環境中的延遲會按照指數退避策略執行
        println("Test duration: $duration (virtual time in test environment)")
    }
    
    @Test
    fun testLinearRetryPolicy() = runTest {
        // Given - 第二次嘗試成功的 Flow
        val flow = flow<CoreResult<String>> {
            attemptCount++
            if (attemptCount == 1) {
                throw ServiceUnavailableException("Service unavailable")
            }
            emit(CoreResult.Success("Success"))
        }
        
        // When - 應用線性退避重試策略
        val result = flow.withRetryPolicy(
            policy = RetryPolicy.Linear(
                maxAttempts = 3,
                initialDelay = 10.milliseconds,
                increment = 10.milliseconds
            ),
            onRetry = { attempt, _ -> retryAttempts.add(attempt) }
        ).first()
        
        // Then
        assertTrue(result is CoreResult.Success)
        assertEquals(2, attemptCount)
        assertEquals(listOf(1), retryAttempts)
    }
    
    @Test
    fun testCustomRetryPolicy() = runTest {
        // Given - 自定義重試邏輯
        val flow = flow<CoreResult<String>> {
            attemptCount++
            if (attemptCount <= 2) {
                throw ServiceUnavailableException("Service unavailable")
            }
            emit(CoreResult.Success("Success"))
        }
        
        // When - 應用自定義重試策略
        val result = flow.withRetryPolicy(
            policy = RetryPolicy.Custom(
                shouldRetry = { attempt, error ->
                    attempt < 3 && error is ServiceUnavailableException
                },
                delayCalculator = { attempt ->
                    (attempt * 5).milliseconds
                }
            ),
            onRetry = { attempt, _ -> retryAttempts.add(attempt) }
        ).first()
        
        // Then
        assertTrue(result is CoreResult.Success)
        assertEquals(3, attemptCount)
        assertEquals(listOf(1, 2), retryAttempts)
    }
    
    @Test
    fun testNoneRetryPolicy() = runTest {
        // Given - 失敗的 Flow
        val flow = flow<CoreResult<String>> {
            attemptCount++
            throw NetworkException("Network error")
        }
        
        // When - 應用無重試策略
        val result = flow.withRetryPolicy(
            policy = RetryPolicy.None,
            onRetry = { attempt, _ -> retryAttempts.add(attempt) }
        ).first()
        
        // Then
        assertTrue(result is CoreResult.Failure)
        assertEquals(1, attemptCount) // 只有原始嘗試，沒有重試
        assertTrue(retryAttempts.isEmpty())
    }
    
    @Test
    fun testNonRetriableError() = runTest {
        // Given - 拋出不可重試的錯誤
        val flow = flow<CoreResult<String>> {
            attemptCount++
            throw AuthenticationException("Auth failed")
        }
        
        // When - 應用重試策略
        val result = flow.withRetryPolicy(
            policy = RetryPolicy.Fixed(maxAttempts = 3, delay = 10.milliseconds),
            onRetry = { attempt, _ -> retryAttempts.add(attempt) }
        ).first()
        
        // Then - 不應該重試
        assertTrue(result is CoreResult.Failure)
        assertEquals(1, attemptCount)
        assertTrue(retryAttempts.isEmpty())
    }
    
    @Test
    fun testErrorHandler_NetworkException() {
        // Given
        val error = NetworkException("Connection failed")
        
        // When
        val errorInfo = ErrorHandler.handleError(error)
        
        // Then
        assertEquals(ErrorType.NETWORK, errorInfo.type)
        assertEquals("網路連接失敗，請檢查網路設定", errorInfo.message)
        assertEquals("請檢查網路連接後重試", errorInfo.userAction)
        assertTrue(errorInfo.isRetriable)
    }
    
    @Test
    fun testErrorHandler_InsufficientBalanceException() {
        // Given
        val error = InsufficientBalanceException(
            required = "100 ETH",
            available = "50 ETH"
        )
        
        // When
        val errorInfo = ErrorHandler.handleError(error)
        
        // Then
        assertEquals(ErrorType.INSUFFICIENT_BALANCE, errorInfo.type)
        assertEquals("餘額不足", errorInfo.message)
        assertEquals("請確保帳戶有足夠餘額", errorInfo.userAction)
        assertFalse(errorInfo.isRetriable)
    }
    
    @Test
    fun testErrorHandler_RateLimitException() {
        // Given
        val error = RateLimitException(retryAfter = 30)
        
        // When
        val errorInfo = ErrorHandler.handleError(error)
        
        // Then
        assertEquals(ErrorType.RATE_LIMIT, errorInfo.type)
        assertEquals("請求過於頻繁", errorInfo.message)
        assertEquals("請稍等 30 秒後再試", errorInfo.userAction)
        assertTrue(errorInfo.isRetriable)
        assertEquals(30, errorInfo.retryAfter)
    }
    
    @Test
    fun testErrorHandler_UnknownError() {
        // Given
        val error = Exception("Something went wrong")
        
        // When
        val errorInfo = ErrorHandler.handleError(error)
        
        // Then
        assertEquals(ErrorType.UNKNOWN, errorInfo.type)
        assertEquals("Something went wrong", errorInfo.message)
        assertEquals("請聯繫客服", errorInfo.userAction)
        assertFalse(errorInfo.isRetriable)
    }
    
    @Test
    fun testRetryWithDifferentErrors() = runTest {
        // Given - Flow that throws different errors
        val errors = listOf(
            NetworkException("Network error"),
            TimeoutException("Timeout"),
            ServiceUnavailableException("Service unavailable")
        )
        var errorIndex = 0
        
        val flow = flow<CoreResult<String>> {
            attemptCount++
            if (errorIndex < errors.size) {
                throw errors[errorIndex++]
            }
            emit(CoreResult.Success("Success"))
        }
        
        // When - Apply retry policy
        val result = flow.withRetryPolicy(
            policy = RetryPolicy.Exponential(
                maxAttempts = 5,
                initialDelay = 10.milliseconds
            ),
            onRetry = { attempt, error ->
                retryAttempts.add(attempt)
                println("Retry $attempt: ${error::class.simpleName}")
            }
        ).first()
        
        // Then
        assertTrue(result is CoreResult.Success)
        assertEquals(4, attemptCount)
        assertEquals(listOf(1, 2, 3), retryAttempts)
    }
}
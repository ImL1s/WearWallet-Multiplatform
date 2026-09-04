package com.cbstudio.wearwallet.core.common

/**
 * 通用的結果封裝類型
 */
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Failure(val exception: Exception) : Result<Nothing>() {
        @Deprecated("Use 'exception' instead", ReplaceWith("exception"))
        val error: Exception get() = exception
    }
    class Loading<T> : Result<T>()
    
    inline fun <R> map(transform: (T) -> R): Result<R> = when (this) {
        is Success -> Success(transform(data))
        is Failure -> this
        is Loading -> Loading()
    }
    
    inline fun <R> flatMap(transform: (T) -> Result<R>): Result<R> = when (this) {
        is Success -> transform(data)
        is Failure -> this
        is Loading -> Loading()
    }
    
    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Failure -> null
        is Loading -> null
    }
    
    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Failure -> throw exception
        is Loading -> throw Exception("Still loading")
    }
    
    fun isSuccess(): Boolean = this is Success
    fun isFailure(): Boolean = this is Failure
}

/**
 * 便利函數用於創建成功結果
 */
fun <T> Result.Companion.success(value: T): Result<T> = Result.Success(value)

/**
 * 便利函數用於創建失敗結果
 */
fun Result.Companion.failure(error: Exception): Result<Nothing> = Result.Failure(error)

/**
 * 執行 suspend 函數並包裝為 Result
 */
suspend inline fun <T> asResult(block: suspend () -> T): Result<T> {
    return try {
        Result.Success(block())
    } catch (e: Exception) {
        Result.Failure(e)
    }
}

/**
 * 獲取成功值或執行替代方案
 * 與 Kotlin 標準庫 Result.getOrElse 保持一致
 */
inline fun <T> Result<T>.getOrElse(onFailure: (Exception) -> T): T = when (this) {
    is Result.Success -> data
    is Result.Failure -> onFailure(exception)
    is Result.Loading -> throw IllegalStateException("Cannot get value from Loading state")
}

/**
 * 獲取成功值或返回默認值
 */
fun <T> Result<T>.getOrDefault(defaultValue: T): T = when (this) {
    is Result.Success -> data
    is Result.Failure -> defaultValue
    is Result.Loading -> defaultValue
}
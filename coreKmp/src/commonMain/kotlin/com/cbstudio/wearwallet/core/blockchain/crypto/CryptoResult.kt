package com.cbstudio.wearwallet.core.blockchain.crypto

/**
 * 加密操作的統一結果類型
 *
 * 使用 sealed class 提供類型安全的錯誤處理，避免：
 * - 使用 null 表示錯誤（信息量不足）
 * - 使用異常進行流程控制（性能問題）
 * - 混亂的返回值類型（String/ByteArray/null）
 *
 * 優勢：
 * - 類型安全：編譯器強制處理所有情況
 * - 明確的錯誤信息：包含錯誤碼和可選消息
 * - 鏈式調用：支持 onSuccess/onFailure 等函數式操作
 * - 易於測試：清晰的成功/失敗狀態
 *
 * 使用示例：
 * ```kotlin
 * val result = signWithEd25519Result(message, privateKey)
 * result
 *     .onSuccess { signature -> println("簽名: $signature") }
 *     .onFailure { code, msg -> println("錯誤 $code: $msg") }
 *
 * // 或使用 when 表達式
 * when (result) {
 *     is CryptoResult.Success -> handleSuccess(result.data)
 *     is CryptoResult.Failure -> handleError(result.errorCode)
 * }
 * ```
 */
sealed class CryptoResult<out T> {
    /**
     * 成功結果
     * @property data 操作返回的數據
     */
    data class Success<T>(val data: T) : CryptoResult<T>()

    /**
     * 失敗結果
     * @property errorCode 錯誤碼
     * @property message 可選的錯誤消息（開發環境使用）
     */
    data class Failure(
        val errorCode: CryptoErrorCode,
        val message: String? = null
    ) : CryptoResult<Nothing>()

    /**
     * 判斷是否成功
     */
    fun isSuccess(): Boolean = this is Success

    /**
     * 判斷是否失敗
     */
    fun isFailure(): Boolean = this is Failure

    /**
     * 獲取數據（失敗返回 null）
     */
    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Failure -> null
    }

    /**
     * 獲取數據（失敗拋出異常）
     * @throws CryptoException 如果操作失敗
     */
    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Failure -> throw CryptoException(errorCode, message)
    }

    /**
     * 獲取數據或提供默認值
     * @param defaultValue 失敗時返回的默認值
     */
    fun getOrDefault(defaultValue: @UnsafeVariance T): T = when (this) {
        is Success -> data
        is Failure -> defaultValue
    }

    /**
     * 成功時執行操作
     * @param action 要執行的操作
     * @return 原始結果（支持鏈式調用）
     */
    inline fun onSuccess(action: (T) -> Unit): CryptoResult<T> {
        if (this is Success) action(data)
        return this
    }

    /**
     * 失敗時執行操作
     * @param action 要執行的操作
     * @return 原始結果（支持鏈式調用）
     */
    inline fun onFailure(action: (CryptoErrorCode, String?) -> Unit): CryptoResult<T> {
        if (this is Failure) action(errorCode, message)
        return this
    }

    /**
     * 映射成功結果
     * @param transform 轉換函數
     * @return 轉換後的結果
     */
    inline fun <R> map(transform: (T) -> R): CryptoResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Failure -> Failure(errorCode, message)
    }

    /**
     * 映射成功結果（可能失敗）
     * @param transform 轉換函數（返回新的 CryptoResult）
     * @return 轉換後的結果
     */
    inline fun <R> flatMap(transform: (T) -> CryptoResult<R>): CryptoResult<R> = when (this) {
        is Success -> transform(data)
        is Failure -> Failure(errorCode, message)
    }
}

/**
 * 加密操作異常
 *
 * 僅在使用 getOrThrow() 時拋出，不應該用於正常的流程控制。
 * 推薦使用 CryptoResult 的其他方法（如 getOrNull, onSuccess, onFailure）。
 *
 * @property errorCode 錯誤碼
 * @property message 錯誤消息
 */
class CryptoException(
    val errorCode: CryptoErrorCode,
    message: String? = null
) : Exception(message ?: errorCode.description)

/**
 * 輔助函數：將可能拋出異常的代碼塊轉換為 CryptoResult
 *
 * 使用示例：
 * ```kotlin
 * val result = runCatching(CryptoErrorCode.ED25519_SIGN_FAILED) {
 *     performEd25519Signature(message, privateKey)
 * }
 * ```
 *
 * @param errorCode 發生異常時使用的錯誤碼
 * @param block 要執行的代碼塊
 * @return CryptoResult
 */
inline fun <T> runCatching(
    errorCode: CryptoErrorCode,
    block: () -> T
): CryptoResult<T> {
    return try {
        CryptoResult.Success(block())
    } catch (e: CryptoException) {
        CryptoResult.Failure(e.errorCode, e.message)
    } catch (e: Exception) {
        CryptoResult.Failure(errorCode, e.message)
    }
}
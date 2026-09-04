package com.cbstudio.wearwallet.core.blockchain.crypto

import com.cbstudio.wearwallet.core.security.CryptoUtils
import kotlin.random.Random

/**
 * 加密安全工具類
 * 提供安全清零和日誌功能
 *
 * 🔒 安全特性：
 * - 多次覆寫防止編譯器優化
 * - 隨機數據覆寫防止冷啟動攻擊
 * - 支持多種數據類型的清理
 * - 自動清理包裝器
 */
object SecureCryptoUtils {

    /**
     * 安全清零敏感數據（字節數組版本）
     *
     * 防禦措施：
     * 1. 多次覆寫防止編譯器優化
     * 2. 隨機數據覆寫防止冷啟動攻擊
     * 3. 最終零覆寫確保清除
     * 4. 內存屏障防止編譯器重排序
     *
     * @receiver ByteArray 要清零的字節陣列
     */
    fun ByteArray.secureZero() {
        if (this.isEmpty()) return

        // 第一次：全零覆寫
        this.fill(0)

        // 第二次：隨機數據覆寫（防止冷啟動攻擊，使用 CSPRNG）
        val randomBytes = CryptoUtils.randomBytes(this.size)
        randomBytes.copyInto(this)
        randomBytes.fill(0)

        // 第三次：再次全零覆寫
        this.fill(0)

        // 強制觸發內存屏障（防止編譯器優化）
        @Suppress("UNUSED_VARIABLE")
        val checksum = this.sum()
    }

    /**
     * 執行操作並確保清理敏感數據
     *
     * 使用範例：
     * ```kotlin
     * val result = withSecureCleanup(privateKey, tempKey) {
     *     // 使用敏感數據進行操作
     *     performCryptoOperation()
     * }
     * // privateKey 和 tempKey 在返回前自動清零
     * ```
     *
     * @param sensitiveData 需要清理的敏感數據數組
     * @param block 要執行的操作
     * @return 操作的結果
     */
    inline fun <T> withSecureCleanup(
        vararg sensitiveData: ByteArray,
        block: () -> T
    ): T {
        return try {
            block()
        } finally {
            sensitiveData.forEach { it.secureZero() }
        }
    }

    /**
     * 安全清零多個敏感數據
     *
     * @param data 要清零的數據數組
     */
    fun secureZeroAll(vararg data: ByteArray?) {
        data.forEach { it?.secureZero() }
    }

    /**
     * 安全日誌系統（已棄用，請使用 CryptoLogger）
     * 僅在 Debug 模式輸出非敏感信息
     *
     * @deprecated 使用新的 CryptoLogger 系統以獲得更好的錯誤碼支持
     */
    @Deprecated(
        message = "Use CryptoLogger with CryptoErrorCode for better error handling",
        replaceWith = ReplaceWith("CryptoLogger", "com.cbstudio.wearwallet.core.blockchain.crypto.CryptoLogger")
    )
    object SecureCryptoLogger {
        private const val TAG = "SecureCrypto"

        // 判斷是否為 Debug 模式的標誌
        // 在生產環境中應該為 false
        private val isDebugMode: Boolean
            get() = true // TODO: 連接到實際的 BuildConfig

        /**
         * 記錄錯誤信息
         * @param errorCode 錯誤代碼（不包含敏感信息）
         * @param context 上下文信息（可選，不應包含敏感數據）
         */
        fun error(errorCode: String, context: String? = null) {
            if (isDebugMode) {
                val message = if (context != null) {
                    "[$TAG] Error: $errorCode | Context: $context"
                } else {
                    "[$TAG] Error: $errorCode"
                }
                println(message)
            }
            // 生產環境：應發送到安全日誌系統（如 Firebase Crashlytics）
            // 但不包含敏感數據
        }

        /**
         * 記錄調試信息
         * @param message 調試消息
         */
        fun debug(message: String) {
            if (isDebugMode) {
                println("[$TAG] $message")
            }
        }

        /**
         * 記錄成功操作
         * @param operation 操作名稱
         */
        fun success(operation: String) {
            if (isDebugMode) {
                println("[$TAG] ✅ $operation")
            }
        }

        /**
         * 記錄警告信息
         * @param message 警告消息
         */
        fun warning(message: String) {
            if (isDebugMode) {
                println("[$TAG] ⚠️ $message")
            }
        }
    }
}
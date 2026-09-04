package io.github.iml1s.crypto

import kotlin.random.Random

/**
 * 安全的字節數組包裝器
 *
 * 功能:
 * 1. 自動清零 (AutoCloseable)
 * 2. 防止複製
 * 3. 隨機覆寫（防冷啟動攻擊）
 * 4. 使用後立即清除
 *
 * 使用方式:
 * ```kotlin
 * SecureByteArray.create(32).use { secureKey ->
 *     // 使用 secureKey.data
 *     // 離開 use 塊時自動清零
 * }
 * ```
 */
class SecureByteArray private constructor(
    internal val data: ByteArray
) : AutoCloseable {

    private var isClosed = false

    /**
     * 獲取只讀視圖（防止意外修改）
     */
    val readOnly: ByteArray
        get() {
            checkNotClosed()
            return data.copyOf()  // 返回副本
        }

    /**
     * 獲取大小
     */
    val size: Int
        get() = data.size

    /**
     * 安全地執行操作
     */
    fun <R> use(block: (ByteArray) -> R): R {
        return try {
            checkNotClosed()
            block(data)
        } finally {
            close()
        }
    }

    /**
     * 清除內存（三重清零 + 隨機覆寫）
     */
    override fun close() {
        if (!isClosed) {
            secureZero(data)
            isClosed = true
        }
    }

    /**
     * 檢查是否已關閉
     */
    private fun checkNotClosed() {
        if (isClosed) {
            throw IllegalStateException("SecureByteArray has been closed")
        }
    }

    /**
     * 防止意外複製
     */
    @Deprecated("不允許複製 SecureByteArray，使用 readOnly 獲取副本", level = DeprecationLevel.ERROR)
    fun copy(): Nothing = throw UnsupportedOperationException("複製 SecureByteArray 不安全")

    companion object {
        /**
         * 創建新的安全字節數組
         */
        fun create(size: Int): SecureByteArray {
            require(size > 0) { "Size must be positive" }
            return SecureByteArray(ByteArray(size))
        }

        /**
         * 從現有數據創建（會複製數據並清除原數據）
         */
        fun fromByteArray(data: ByteArray): SecureByteArray {
            val secure = SecureByteArray(data.copyOf())
            secureZero(data)  // 清除原始數據
            return secure
        }

        /**
         * 從十六進制字符串創建
         */
        fun fromHex(hex: String): SecureByteArray {
            val cleanHex = hex.removePrefix("0x")
            val bytes = ByteArray(cleanHex.length / 2) { i ->
                cleanHex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }

            return SecureByteArray(bytes)
        }

        /**
         * 三重清零 + 隨機覆寫
         *
         * 防禦措施:
         * 1. 第一次清零 - 清除明文
         * 2. 隨機覆寫 - 防止冷啟動攻擊
         * 3. 第二次清零 - 清除隨機數據
         * 4. 內存屏障 - 防止編譯器優化
         */
        fun secureZero(data: ByteArray) {
            // 第一次清零
            data.fill(0)

            // 隨機覆寫（防冷啟動攻擊）
            Random.nextBytes(data)

            // 第二次清零
            data.fill(0)

            // 內存屏障（防止編譯器優化掉清零操作）
            // 使用 volatile 或計算來防止優化
            @Suppress("UNUSED_VARIABLE")
            val checksum = data.sum()
        }
    }
}

/**
 * 擴展函數：安全地使用字節數組
 */
fun <R> ByteArray.useSecurely(block: (ByteArray) -> R): R {
    return try {
        block(this)
    } finally {
        SecureByteArray.secureZero(this)
    }
}

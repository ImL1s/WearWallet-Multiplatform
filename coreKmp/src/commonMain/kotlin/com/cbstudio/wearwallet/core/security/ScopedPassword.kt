package com.cbstudio.wearwallet.core.security

/**
 * 專用 Scoped Password
 * 封裝 CharArray 密碼，提供 AutoCloseable 與 use 模式，
 * 確保在 finally 區塊中以 chars.fill('\u0000') 徹底抹除。
 */
class ScopedPassword private constructor(
    @PublishedApi internal val chars: CharArray
) : AutoCloseable {
    private var isDestroyed = false

    val length: Int
        get() {
            checkNotDestroyed()
            return chars.size
        }

    val isClosed: Boolean
        get() = isDestroyed

    /**
     * 安全使用底層密碼字符陣列，執行完畢後立即清零銷毀
     */
    inline fun <R> use(block: (CharArray) -> R): R {
        checkNotDestroyed()
        return try {
            block(chars)
        } finally {
            destroy()
        }
    }

    /**
     * 複製一份 CharArray (呼叫者需自行負責清零)
     */
    fun copyChars(): CharArray {
        checkNotDestroyed()
        return chars.copyOf()
    }

    /**
     * 手動銷毀並清零內存
     */
    fun destroy() {
        if (!isDestroyed) {
            isDestroyed = true
            chars.fill('\u0000')
        }
    }

    override fun close() = destroy()

    @PublishedApi
    internal fun checkNotDestroyed() {
        if (isDestroyed) {
            throw IllegalStateException("ScopedPassword has already been destroyed/wiped")
        }
    }

    companion object {
        fun fromCharArray(chars: CharArray, takeOwnership: Boolean = false): ScopedPassword {
            val copy = if (takeOwnership) chars else chars.copyOf()
            return ScopedPassword(copy)
        }

        fun fromString(str: String): ScopedPassword {
            val chars = str.toCharArray()
            return ScopedPassword(chars)
        }
    }
}

/**
 * CharArray 轉換為 ScopedPassword
 */
fun CharArray.toScopedPassword(takeOwnership: Boolean = false): ScopedPassword {
    return ScopedPassword.fromCharArray(this, takeOwnership = takeOwnership)
}

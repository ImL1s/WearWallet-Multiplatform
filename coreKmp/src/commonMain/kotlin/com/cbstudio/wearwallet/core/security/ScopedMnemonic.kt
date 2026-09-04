package com.cbstudio.wearwallet.core.security

/**
 * 專用 Scoped Mnemonic
 * 封裝 CharArray 助記詞，提供 AutoCloseable 與 use 模式，
 * 確保在 finally 區塊中以 chars.fill('\u0000') 徹底抹除。
 */
class ScopedMnemonic private constructor(
    @PublishedApi internal val chars: CharArray
) : AutoCloseable {
    private var isDestroyed = false

    val wordCount: Int
        get() {
            checkNotDestroyed()
            var count = 0
            var inWord = false
            for (c in chars) {
                if (c.isWhitespace()) {
                    inWord = false
                } else if (!inWord) {
                    inWord = true
                    count++
                }
            }
            return count
        }

    val length: Int
        get() {
            checkNotDestroyed()
            return chars.size
        }

    val isClosed: Boolean
        get() = isDestroyed

    /**
     * 安全使用底層助記詞字符陣列，執行完畢後立即清零銷毀
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
            throw IllegalStateException("ScopedMnemonic has already been destroyed/wiped")
        }
    }

    companion object {
        fun fromCharArray(chars: CharArray, takeOwnership: Boolean = false): ScopedMnemonic {
            val copy = if (takeOwnership) chars else chars.copyOf()
            return ScopedMnemonic(copy)
        }

        fun fromString(str: String): ScopedMnemonic {
            val chars = str.toCharArray()
            return ScopedMnemonic(chars)
        }

        fun fromWords(words: List<String>): ScopedMnemonic {
            val totalChars = words.sumOf { it.length } + maxOf(0, words.size - 1)
            val chars = CharArray(totalChars)
            var pos = 0
            for (i in words.indices) {
                val w = words[i]
                for (j in w.indices) {
                    chars[pos++] = w[j]
                }
                if (i < words.size - 1) {
                    chars[pos++] = ' '
                }
            }
            return ScopedMnemonic(chars)
        }
    }
}

/**
 * CharArray 轉換為 ScopedMnemonic
 */
fun CharArray.toScopedMnemonic(takeOwnership: Boolean = false): ScopedMnemonic {
    return ScopedMnemonic.fromCharArray(this, takeOwnership = takeOwnership)
}

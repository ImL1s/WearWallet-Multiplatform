package com.cbstudio.wearwallet.core.security

import io.github.iml1s.crypto.SecureByteArray

/**
 * 專用 Scoped Private Key
 * 封裝原生 ByteArray，提供 AutoCloseable 與 use 模式，
 * 確保在 finally 區塊中以 SecureByteArray.secureZero 徹底抹除。
 */
class ScopedPrivateKey private constructor(
    @PublishedApi internal val keyBytes: ByteArray
) : AutoCloseable {
    private var isDestroyed = false

    val size: Int
        get() {
            checkNotDestroyed()
            return keyBytes.size
        }

    val isClosed: Boolean
        get() = isDestroyed

    /**
     * 安全使用底層密鑰字節，執行完畢後立即清零銷毀
     */
    inline fun <R> use(block: (ByteArray) -> R): R {
        checkNotDestroyed()
        return try {
            block(keyBytes)
        } finally {
            destroy()
        }
    }

    /**
     * 手動銷毀並清零內存
     */
    fun destroy() {
        if (!isDestroyed) {
            isDestroyed = true
            SecureByteArray.secureZero(keyBytes)
        }
    }

    override fun close() = destroy()

    @PublishedApi
    internal fun checkNotDestroyed() {
        if (isDestroyed) {
            throw IllegalStateException("ScopedPrivateKey has already been destroyed/wiped")
        }
    }

    companion object {
        fun fromByteArray(bytes: ByteArray, takeOwnership: Boolean = false): ScopedPrivateKey {
            val copy = if (takeOwnership) {
                bytes
            } else {
                bytes.copyOf()
            }
            return ScopedPrivateKey(copy)
        }

        fun fromHex(hex: CharArray): ScopedPrivateKey {
            val cleanLength = if (hex.size >= 2 && hex[0] == '0' && (hex[1] == 'x' || hex[1] == 'X')) hex.size - 2 else hex.size
            val offset = hex.size - cleanLength
            val byteCount = cleanLength / 2
            val bytes = ByteArray(byteCount)
            for (i in 0 until byteCount) {
                val hi = hexNibble(hex[offset + i * 2])
                val lo = hexNibble(hex[offset + i * 2 + 1])
                bytes[i] = ((hi shl 4) or lo).toByte()
            }
            return ScopedPrivateKey(bytes)
        }

        fun fromHex(hexStr: String): ScopedPrivateKey {
            val chars = hexStr.toCharArray()
            try {
                return fromHex(chars)
            } finally {
                chars.fill('\u0000')
            }
        }

        private fun hexNibble(c: Char): Int = when (c) {
            in '0'..'9' -> c - '0'
            in 'a'..'f' -> c - 'a' + 10
            in 'A'..'F' -> c - 'A' + 10
            else -> throw IllegalArgumentException("Invalid hex char: $c")
        }
    }
}

/**
 * CharArray 安全使用擴展函數：在 finally 中自動清零
 */
inline fun <R> CharArray.useSecurely(block: (CharArray) -> R): R {
    return try {
        block(this)
    } finally {
        this.fill('\u0000')
    }
}

/**
 * 將 CharArray 編碼為 UTF-8 ByteArray，避免在 JVM 堆上創建不可抹除的 String
 */
fun CharArray.encodeToUtf8Bytes(): ByteArray {
    var byteCount = 0
    var i = 0
    while (i < this.size) {
        val c = this[i]
        val code = c.code
        when {
            code < 0x80 -> {
                byteCount += 1
                i += 1
            }
            code < 0x800 -> {
                byteCount += 2
                i += 1
            }
            code in 0xD800..0xDBFF && i + 1 < this.size && this[i + 1].code in 0xDC00..0xDFFF -> {
                byteCount += 4
                i += 2
            }
            else -> {
                byteCount += 3
                i += 1
            }
        }
    }
    val bytes = ByteArray(byteCount)
    var idx = 0
    i = 0
    while (i < this.size) {
        val c = this[i]
        val code = c.code
        when {
            code < 0x80 -> {
                bytes[idx++] = code.toByte()
                i += 1
            }
            code < 0x800 -> {
                bytes[idx++] = (0xC0 or (code shr 6)).toByte()
                bytes[idx++] = (0x80 or (code and 0x3F)).toByte()
                i += 1
            }
            code in 0xD800..0xDBFF && i + 1 < this.size && this[i + 1].code in 0xDC00..0xDFFF -> {
                val high = code - 0xD800
                val low = this[i + 1].code - 0xDC00
                val cp = 0x10000 + ((high shl 10) or low)
                bytes[idx++] = (0xF0 or (cp shr 18)).toByte()
                bytes[idx++] = (0x80 or ((cp shr 12) and 0x3F)).toByte()
                bytes[idx++] = (0x80 or ((cp shr 6) and 0x3F)).toByte()
                bytes[idx++] = (0x80 or (cp and 0x3F)).toByte()
                i += 2
            }
            else -> {
                bytes[idx++] = (0xE0 or (code shr 12)).toByte()
                bytes[idx++] = (0x80 or ((code shr 6) and 0x3F)).toByte()
                bytes[idx++] = (0x80 or (code and 0x3F)).toByte()
                i += 1
            }
        }
    }
    return bytes
}

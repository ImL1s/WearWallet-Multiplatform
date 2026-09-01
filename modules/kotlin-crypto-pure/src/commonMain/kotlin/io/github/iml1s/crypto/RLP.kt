package io.github.iml1s.crypto

// toHexString is not strictly needed for RLP encoding; removed.

/**
 * Recursive Length Prefix (RLP) Encoding for Ethereum
 */
object RLP {
    
    fun encode(input: Any): ByteArray {
        return when (input) {
            is ByteArray -> encodeByteArray(input)
            is String -> encodeString(input)
            is Long -> encodeByteArray(toMinByteArray(input))
            is Int -> encodeByteArray(toMinByteArray(input.toLong()))
            is List<*> -> encodeList(input)
            else -> throw IllegalArgumentException("Unsupported type for RLP encoding: ${input::class.simpleName}")
        }
    }

    private fun encodeByteArray(bytes: ByteArray): ByteArray {
        return if (bytes.size == 1 && (bytes[0].toInt() and 0xFF) < 0x80) {
            bytes
        } else if (bytes.size <= 55) {
            val length = bytes.size
            byteArrayOf((0x80 + length).toByte()) + bytes
        } else {
            val lengthBytes = toMinByteArray(bytes.size.toLong())
            byteArrayOf((0xB7 + lengthBytes.size).toByte()) + lengthBytes + bytes
        }
    }

    private fun encodeString(s: String): ByteArray {
        return if (s.startsWith("0x")) {
            // Hex string
            encodeByteArray(s.hexToByteArray())
        } else {
            // Normal string
            encodeByteArray(s.encodeToByteArray())
        }
    }

    public fun encodeList(list: List<*>): ByteArray {
        val encodedItems = list.map { encode(it!!) } // Assume no nulls for now
        val totalLength = encodedItems.sumOf { it.size }
        val combined = encodedItems.fold(ByteArray(0)) { acc, bytes -> acc + bytes }

        return if (totalLength <= 55) {
            byteArrayOf((0xC0 + totalLength).toByte()) + combined
        } else {
            val lengthBytes = toMinByteArray(totalLength.toLong())
            byteArrayOf((0xF7 + lengthBytes.size).toByte()) + lengthBytes + combined
        }
    }

    private fun toMinByteArray(value: Long): ByteArray {
        if (value == 0L) return ByteArray(0)
        
        var temp = value
        val bytes = mutableListOf<Byte>()
        while (temp > 0) {
            bytes.add(0, (temp and 0xFF).toByte())
            temp = temp shr 8
        }
        return bytes.toByteArray()
    }

    private fun String.hexToByteArray(): ByteArray {
        val s = this.removePrefix("0x")
        val len = s.length
        val data = ByteArray(len / 2)
        for (i in 0 until len step 2) {
            data[i / 2] = ((s[i].digitToInt(16) shl 4) + s[i + 1].digitToInt(16)).toByte()
        }
        return data
    }
}

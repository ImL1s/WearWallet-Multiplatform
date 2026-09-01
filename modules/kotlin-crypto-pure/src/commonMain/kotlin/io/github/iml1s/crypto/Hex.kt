package io.github.iml1s.crypto

public object Hex {
    private val hexArray = "0123456789abcdef".toCharArray()

    public fun encode(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        for (j in bytes.indices) {
            val v = bytes[j].toInt() and 0xFF
            hexChars[j * 2] = hexArray[v ushr 4]
            hexChars[j * 2 + 1] = hexArray[v and 0x0F]
        }
        return hexChars.concatToString()
    }

    public fun decode(s: String): ByteArray {
        val len = s.length
        require(len % 2 == 0) { "Hex string must have an even length" }
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((digitToInt(s[i]) shl 4) + digitToInt(s[i + 1])).toByte()
            i += 2
        }
        return data
    }
    
    private fun digitToInt(c: Char): Int {
        return when (c) {
            in '0'..'9' -> c - '0'
            in 'a'..'f' -> c - 'a' + 10
            in 'A'..'F' -> c - 'A' + 10
            else -> throw IllegalArgumentException("Invalid hex character: $c")
        }
    }
}

package io.github.iml1s.tx.utils

object Hex {
    fun decode(hex: String): ByteArray {
        val clean = if (hex.length % 2 != 0) "0$hex" else hex
        return clean.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
    
    fun encode(bytes: ByteArray): String {
        return bytes.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    }
}

fun ByteArray.toHex(): String = Hex.encode(this)

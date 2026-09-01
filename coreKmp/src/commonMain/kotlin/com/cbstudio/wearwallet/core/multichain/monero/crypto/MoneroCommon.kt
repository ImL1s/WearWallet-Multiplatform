package com.cbstudio.wearwallet.core.multichain.monero.crypto

/**
 * Monero 通用定義和擴展函數
 */

/**
 * Monero 網路類型
 */
enum class MoneroNetwork {
    MAINNET,
    STAGENET,
    TESTNET
}

/**
 * String 擴展：格式化為指定格式
 */
fun String.format(vararg args: Any): String {
    var result = this
    args.forEachIndexed { index, arg ->
        result = result.replace("%${index + 1}\$s", arg.toString())
                      .replace("%s", arg.toString())
                      .replace("%d", arg.toString())
                      .replace("%02x", if (arg is Int) {
                          arg.toString(16).padStart(2, '0')
                      } else {
                          arg.toString()
                      })
    }
    return result
}

/**
 * Double 擴展：格式化為指定小數位數
 */
fun Double.format(digits: Int): String {
    return this.toString() // 簡化實現，可以根據需要改進
}

/**
 * ByteArray 到十六進制字符串
 */
fun ByteArray.toHex(): String {
    return joinToString("") { byte ->
        val value = byte.toInt() and 0xFF
        value.toString(16).padStart(2, '0')
    }
}

/**
 * 十六進制字符串到 ByteArray
 */
fun String.hexToByteArray(): ByteArray {
    require(length % 2 == 0) { "Hex string must have even length" }
    return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

/**
 * 將整數轉換為 ByteArray (小端序)
 */
fun Int.toByteArray(): ByteArray {
    return byteArrayOf(
        (this and 0xFF).toByte(),
        ((this shr 8) and 0xFF).toByte(),
        ((this shr 16) and 0xFF).toByte(),
        ((this shr 24) and 0xFF).toByte()
    )
}

/**
 * 將長整數轉換為 ByteArray (小端序)
 */
fun Long.toByteArray(): ByteArray {
    return byteArrayOf(
        (this and 0xFF).toByte(),
        ((this shr 8) and 0xFF).toByte(),
        ((this shr 16) and 0xFF).toByte(),
        ((this shr 24) and 0xFF).toByte(),
        ((this shr 32) and 0xFF).toByte(),
        ((this shr 40) and 0xFF).toByte(),
        ((this shr 48) and 0xFF).toByte(),
        ((this shr 56) and 0xFF).toByte()
    )
}

/**
 * ByteArray 轉換為整數 (小端序)
 */
fun ByteArray.toInt(): Int {
    require(size >= 4) { "ByteArray must have at least 4 bytes" }
    return ((this[3].toInt() and 0xFF) shl 24) or
           ((this[2].toInt() and 0xFF) shl 16) or
           ((this[1].toInt() and 0xFF) shl 8) or
           (this[0].toInt() and 0xFF)
}

/**
 * ByteArray 轉換為長整數 (小端序)
 */
fun ByteArray.toLong(): Long {
    require(size >= 8) { "ByteArray must have at least 8 bytes" }
    return ((this[7].toLong() and 0xFF) shl 56) or
           ((this[6].toLong() and 0xFF) shl 48) or
           ((this[5].toLong() and 0xFF) shl 40) or
           ((this[4].toLong() and 0xFF) shl 32) or
           ((this[3].toLong() and 0xFF) shl 24) or
           ((this[2].toLong() and 0xFF) shl 16) or
           ((this[1].toLong() and 0xFF) shl 8) or
           (this[0].toLong() and 0xFF)
}
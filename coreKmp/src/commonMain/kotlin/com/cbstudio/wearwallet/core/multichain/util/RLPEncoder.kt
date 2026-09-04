package com.cbstudio.wearwallet.core.multichain.util

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.Sign

/**
 * RLP (Recursive Length Prefix) 編碼與解碼工具
 *
 * 用於 Ethereum 交易的序列化與反序列化
 * 基於 Ethereum 官方規範實現
 *
 * 參考：https://ethereum.org/developers/docs/data-structures-and-encoding/rlp/
 */
object RLPEncoder {

    /**
     * 編碼單個項目
     */
    fun encode(input: Any?): ByteArray {
        return when (input) {
            null -> encodeLength(0, 0x80)
            is ByteArray -> encodeBytes(input)
            is String -> {
                if (input.startsWith("0x")) {
                    encodeBytes(hexToByteArray(input))
                } else {
                    encodeBytes(input.encodeToByteArray())
                }
            }
            is Int -> encodeInt(input.toLong())
            is Long -> encodeInt(input)
            is BigInteger -> encodeBigInteger(input)
            is List<*> -> encodeList(input)
            else -> throw IllegalArgumentException("不支援的類型: ${input::class}")
        }
    }

    /**
     * 解碼 RLP 位元組陣列 (支援單一 item 與 List)
     */
    fun decode(input: ByteArray): Any {
        val (item, _) = decodeItem(input, 0)
        return item
    }

    private fun decodeItem(input: ByteArray, offset: Int): Pair<Any, Int> {
        require(offset < input.size) { "RLP decode out of bounds" }
        val prefix = input[offset].toInt() and 0xFF

        return when {
            prefix < 0x80 -> {
                Pair(byteArrayOf(input[offset]), offset + 1)
            }
            prefix <= 0xb7 -> {
                val len = prefix - 0x80
                val data = if (len == 0) byteArrayOf() else input.copyOfRange(offset + 1, offset + 1 + len)
                Pair(data, offset + 1 + len)
            }
            prefix <= 0xbf -> {
                val lenOfLen = prefix - 0xb7
                var len = 0
                for (i in 0 until lenOfLen) {
                    len = (len shl 8) or (input[offset + 1 + i].toInt() and 0xFF)
                }
                val start = offset + 1 + lenOfLen
                val data = input.copyOfRange(start, start + len)
                Pair(data, start + len)
            }
            prefix <= 0xf7 -> {
                val listLen = prefix - 0xc0
                var current = offset + 1
                val end = current + listLen
                val list = mutableListOf<Any>()
                while (current < end) {
                    val (item, next) = decodeItem(input, current)
                    list.add(item)
                    current = next
                }
                Pair(list, end)
            }
            else -> {
                val lenOfLen = prefix - 0xf7
                var listLen = 0
                for (i in 0 until lenOfLen) {
                    listLen = (listLen shl 8) or (input[offset + 1 + i].toInt() and 0xFF)
                }
                var current = offset + 1 + lenOfLen
                val end = current + listLen
                val list = mutableListOf<Any>()
                while (current < end) {
                    val (item, next) = decodeItem(input, current)
                    list.add(item)
                    current = next
                }
                Pair(list, end)
            }
        }
    }

    /**
     * 編碼位元組陣列
     */
    private fun encodeBytes(input: ByteArray): ByteArray {
        if (input.isEmpty()) {
            return byteArrayOf(0x80.toByte())
        }

        if (input.size == 1 && input[0].toInt() and 0xFF < 0x80) {
            return input
        }

        return encodeLength(input.size, 0x80) + input
    }

    /**
     * 編碼整數
     */
    private fun encodeInt(value: Long): ByteArray {
        if (value == 0L) {
            return byteArrayOf(0x80.toByte())
        }

        val bytes = toByteArray(value)
        return encodeBytes(bytes)
    }

    /**
     * 編碼大整數
     */
    private fun encodeBigInteger(value: BigInteger): ByteArray {
        if (value == BigInteger.ZERO) {
            return byteArrayOf(0x80.toByte())
        }

        val bytes = value.toByteArray()
        return encodeBytes(bytes)
    }

    /**
     * 編碼列表
     */
    private fun encodeList(input: List<*>): ByteArray {
        if (input.isEmpty()) {
            return encodeLength(0, 0xc0)
        }
        val output = input.map { encode(it) }.fold(byteArrayOf()) { acc, bytes -> acc + bytes }
        return encodeLength(output.size, 0xc0) + output
    }

    /**
     * 編碼長度前綴
     */
    private fun encodeLength(length: Int, offset: Int): ByteArray {
        if (length < 56) {
            return byteArrayOf((offset + length).toByte())
        }

        val lengthBytes = toByteArray(length.toLong())
        return byteArrayOf((offset + 55 + lengthBytes.size).toByte()) + lengthBytes
    }

    /**
     * 將長整數轉換為位元組陣列（Big-endian）
     */
    private fun toByteArray(value: Long): ByteArray {
        if (value == 0L) return byteArrayOf()

        val result = mutableListOf<Byte>()
        var v = value

        while (v > 0) {
            result.add(0, (v and 0xFF).toByte())
            v = v shr 8
        }

        return result.toByteArray()
    }

    /**
     * 十六進制字串轉位元組陣列
     */
    private fun hexToByteArray(hex: String): ByteArray {
        val cleanHex = hex.removePrefix("0x")
        val len = cleanHex.length
        val data = ByteArray(len / 2)

        for (i in data.indices) {
            val highNibble = cleanHex[i * 2].toString().toInt(16)
            val lowNibble = cleanHex[i * 2 + 1].toString().toInt(16)
            data[i] = ((highNibble shl 4) + lowNibble).toByte()
        }

        return data
    }

    /**
     * 位元組陣列轉十六進制字串
     */
    fun toHexString(bytes: ByteArray): String {
        return "0x" + bytes.joinToString("") { byte ->
            val hex = (byte.toInt() and 0xFF).toString(16)
            if (hex.length == 1) "0$hex" else hex
        }
    }
}

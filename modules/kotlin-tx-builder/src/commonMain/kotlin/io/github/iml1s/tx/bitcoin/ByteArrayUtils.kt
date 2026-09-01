package io.github.iml1s.tx.bitcoin

/**
 * ByteArray 構建器（用於交易序列化）
 *
 * 提供 little-endian 和 varint 寫入方法
 */
class ByteArrayBuilder {
    private val buffer = mutableListOf<Byte>()

    fun writeByte(value: Int) {
        buffer.add(value.toByte())
    }

    fun writeBytes(bytes: ByteArray) {
        buffer.addAll(bytes.toList())
    }

    /**
     * 寫入 32-bit 整數（little-endian）
     */
    fun writeInt32LE(value: Int) {
        buffer.add((value and 0xFF).toByte())
        buffer.add(((value shr 8) and 0xFF).toByte())
        buffer.add(((value shr 16) and 0xFF).toByte())
        buffer.add(((value shr 24) and 0xFF).toByte())
    }

    /**
     * 寫入 64-bit 整數（little-endian）
     */
    fun writeInt64LE(value: Long) {
        buffer.add((value and 0xFF).toByte())
        buffer.add(((value shr 8) and 0xFF).toByte())
        buffer.add(((value shr 16) and 0xFF).toByte())
        buffer.add(((value shr 24) and 0xFF).toByte())
        buffer.add(((value shr 32) and 0xFF).toByte())
        buffer.add(((value shr 40) and 0xFF).toByte())
        buffer.add(((value shr 48) and 0xFF).toByte())
        buffer.add(((value shr 56) and 0xFF).toByte())
    }

    /**
     * 寫入可變長度整數（Bitcoin VarInt 格式）
     *
     * 格式:
     * - 0-0xFC: 1 byte
     * - 0xFD-0xFFFF: 0xFD + 2 bytes (little-endian)
     * - 0x10000-0xFFFFFFFF: 0xFE + 4 bytes (little-endian)
     * - 0x100000000-0xFFFFFFFFFFFFFFFF: 0xFF + 8 bytes (little-endian)
     */
    fun writeVarInt(value: Long) {
        when {
            value < 0xFD -> {
                buffer.add(value.toByte())
            }
            value <= 0xFFFF -> {
                buffer.add(0xFD.toByte())
                buffer.add((value and 0xFF).toByte())
                buffer.add(((value shr 8) and 0xFF).toByte())
            }
            value <= 0xFFFFFFFFL -> {
                buffer.add(0xFE.toByte())
                writeInt32LE(value.toInt())
            }
            else -> {
                buffer.add(0xFF.toByte())
                writeInt64LE(value)
            }
        }
    }

    fun toByteArray(): ByteArray = buffer.toByteArray()

    fun size(): Int = buffer.size
}

/**
 * ByteArray 讀取器（用於交易解析）
 */
class ByteArrayReader(private val data: ByteArray) {
    private var offset = 0

    fun readByte(): Int {
        require(offset < data.size) { "End of data" }
        return data[offset++].toInt() and 0xFF
    }

    fun readBytes(count: Int): ByteArray {
        require(offset + count <= data.size) { "Not enough data" }
        val result = data.sliceArray(offset until offset + count)
        offset += count
        return result
    }

    /**
     * 讀取 32-bit 整數（little-endian）
     */
    fun readInt32LE(): Int {
        val b0 = readByte()
        val b1 = readByte()
        val b2 = readByte()
        val b3 = readByte()
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }

    /**
     * 讀取 64-bit 整數（little-endian）
     */
    fun readInt64LE(): Long {
        val b0 = readByte().toLong()
        val b1 = readByte().toLong()
        val b2 = readByte().toLong()
        val b3 = readByte().toLong()
        val b4 = readByte().toLong()
        val b5 = readByte().toLong()
        val b6 = readByte().toLong()
        val b7 = readByte().toLong()
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24) or
                (b4 shl 32) or (b5 shl 40) or (b6 shl 48) or (b7 shl 56)
    }

    /**
     * 讀取可變長度整數
     */
    fun readVarInt(): Long {
        val first = readByte()
        return when {
            first < 0xFD -> first.toLong()
            first == 0xFD -> {
                val b0 = readByte().toLong()
                val b1 = readByte().toLong()
                b0 or (b1 shl 8)
            }
            first == 0xFE -> readInt32LE().toLong() and 0xFFFFFFFFL
            else -> readInt64LE()
        }
    }

    fun remaining(): Int = data.size - offset

    fun hasRemaining(): Boolean = offset < data.size

    fun getOffset(): Int = offset

    fun peekByte(): Int {
        require(offset < data.size) { "End of data" }
        return data[offset].toInt() and 0xFF
    }

    /**
     * 讀取可變長度整數 (當已經讀取了第一個字節時使用)
     */
    fun readVarInt(first: Int): Long {
        return when {
            first < 0xFD -> first.toLong()
            first == 0xFD -> {
                val b0 = readByte().toLong()
                val b1 = readByte().toLong()
                b0 or (b1 shl 8)
            }
            first == 0xFE -> readInt32LE().toLong() and 0xFFFFFFFFL
            else -> readInt64LE()
        }
    }

    /**
     * 讀取 Script (VarInt length + bytes)
     */
    fun readScript(): ByteArray {
        val len = readVarInt().toInt()
        return readBytes(len)
    }
}

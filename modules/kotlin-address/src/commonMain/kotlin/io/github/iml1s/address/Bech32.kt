package io.github.iml1s.address

/**
 * Bech32/Bech32m 編碼實現
 *
 * 符合以下標準：
 * - BIP173: Bech32 (SegWit v0)
 * - BIP350: Bech32m (SegWit v1+, Taproot)
 *
 * @see <a href="https://github.com/bitcoin/bips/blob/master/bip-0173.mediawiki">BIP173</a>
 * @see <a href="https://github.com/bitcoin/bips/blob/master/bip-0350.mediawiki">BIP350</a>
 */
object Bech32 {

    private const val CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
    private const val BECH32_CONST = 1
    private const val BECH32M_CONST = 0x2bc830a3

    /**
     * Bech32 編碼結果
     */
    data class Bech32Data(
        val hrp: String,
        val data: ByteArray,
        val encoding: Encoding
    ) {
        enum class Encoding { BECH32, BECH32M }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false
            other as Bech32Data
            return hrp == other.hrp && data.contentEquals(other.data) && encoding == other.encoding
        }

        override fun hashCode(): Int {
            var result = hrp.hashCode()
            result = 31 * result + data.contentHashCode()
            result = 31 * result + encoding.hashCode()
            return result
        }
    }

    /**
     * Bech32 編碼
     *
     * @param hrp Human-readable part (例如 "bc" 或 "tb")
     * @param data 5-bit 資料陣列
     * @param encoding BECH32 或 BECH32M
     * @return 編碼後的字串
     */
    fun encode(hrp: String, data: ByteArray, encoding: Bech32Data.Encoding = Bech32Data.Encoding.BECH32): String {
        val checksum = createChecksum(hrp, data, encoding)
        val combined = data + checksum

        val result = StringBuilder(hrp.length + 1 + combined.size)
        result.append(hrp)
        result.append('1')

        for (b in combined) {
            result.append(CHARSET[b.toInt() and 0x1f])
        }

        return result.toString()
    }

    /**
     * Bech32 解碼
     *
     * @param bech32 Bech32 編碼的字串
     * @return 解碼結果或 null（如果無效）
     */
    fun decode(bech32: String): Bech32Data? {
        val lower = bech32.lowercase()
        val pos = lower.lastIndexOf('1')

        if (pos < 1 || pos + 7 > lower.length || lower.length > 90) {
            return null
        }

        val hrp = lower.substring(0, pos)
        val dataPartStr = lower.substring(pos + 1)

        val data = ByteArray(dataPartStr.length)
        for (i in dataPartStr.indices) {
            val c = CHARSET.indexOf(dataPartStr[i])
            if (c == -1) return null
            data[i] = c.toByte()
        }

        // 驗證 checksum
        val encoding = verifyChecksum(hrp, data)
        if (encoding == null) return null

        // 移除 checksum（最後 6 bytes）
        val payload = data.sliceArray(0 until data.size - 6)

        return Bech32Data(hrp, payload, encoding)
    }

    /**
     * 將 8-bit 資料轉換為 5-bit
     */
    fun convertBits(data: ByteArray, fromBits: Int, toBits: Int, pad: Boolean): ByteArray? {
        var acc = 0
        var bits = 0
        val result = mutableListOf<Byte>()
        val maxv = (1 shl toBits) - 1

        for (b in data) {
            val value = b.toInt() and 0xFF
            if (value ushr fromBits != 0) return null

            acc = (acc shl fromBits) or value
            bits += fromBits

            while (bits >= toBits) {
                bits -= toBits
                result.add(((acc ushr bits) and maxv).toByte())
            }
        }

        if (pad) {
            if (bits > 0) {
                result.add(((acc shl (toBits - bits)) and maxv).toByte())
            }
        } else if (bits >= fromBits || ((acc shl (toBits - bits)) and maxv) != 0) {
            return null
        }

        return result.toByteArray()
    }

    /**
     * 編碼 SegWit 地址
     *
     * @param hrp Human-readable part
     * @param witnessVersion 見證版本 (0-16)
     * @param witnessProgram 見證程序
     * @return 編碼的地址
     */
    fun encodeSegwitAddress(hrp: String, witnessVersion: Int, witnessProgram: ByteArray): String? {
        if (witnessVersion < 0 || witnessVersion > 16) return null

        val converted = convertBits(witnessProgram, 8, 5, true) ?: return null
        val data = byteArrayOf(witnessVersion.toByte()) + converted

        val encoding = if (witnessVersion == 0) {
            Bech32Data.Encoding.BECH32
        } else {
            Bech32Data.Encoding.BECH32M
        }

        return encode(hrp, data, encoding)
    }

    /**
     * 解碼 SegWit 地址
     *
     * @param address Bech32 編碼的地址
     * @return Pair(witnessVersion, witnessProgram) 或 null
     */
    fun decodeSegwitAddress(address: String): Pair<Int, ByteArray>? {
        val decoded = decode(address) ?: return null
        if (decoded.data.isEmpty()) return null

        val witnessVersion = decoded.data[0].toInt()
        if (witnessVersion < 0 || witnessVersion > 16) return null

        // 驗證編碼類型
        val expectedEncoding = if (witnessVersion == 0) {
            Bech32Data.Encoding.BECH32
        } else {
            Bech32Data.Encoding.BECH32M
        }

        if (decoded.encoding != expectedEncoding) return null

        val payload = decoded.data.sliceArray(1 until decoded.data.size)
        val witnessProgram = convertBits(payload, 5, 8, false) ?: return null

        // 驗證長度
        if (witnessProgram.size < 2 || witnessProgram.size > 40) return null
        if (witnessVersion == 0 && witnessProgram.size != 20 && witnessProgram.size != 32) return null

        return Pair(witnessVersion, witnessProgram)
    }

    private fun polymod(values: ByteArray): Int {
        val generator = intArrayOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)
        var chk = 1

        for (v in values) {
            val top = chk ushr 25
            chk = ((chk and 0x1ffffff) shl 5) xor (v.toInt() and 0xff)
            for (i in 0 until 5) {
                if ((top ushr i) and 1 == 1) {
                    chk = chk xor generator[i]
                }
            }
        }

        return chk
    }

    private fun hrpExpand(hrp: String): ByteArray {
        val result = ByteArray(hrp.length * 2 + 1)
        for (i in hrp.indices) {
            result[i] = (hrp[i].code ushr 5).toByte()
        }
        result[hrp.length] = 0
        for (i in hrp.indices) {
            result[hrp.length + 1 + i] = (hrp[i].code and 31).toByte()
        }
        return result
    }

    private fun createChecksum(hrp: String, data: ByteArray, encoding: Bech32Data.Encoding): ByteArray {
        val values = hrpExpand(hrp) + data + ByteArray(6)
        val const = if (encoding == Bech32Data.Encoding.BECH32) BECH32_CONST else BECH32M_CONST
        val polymod = polymod(values) xor const

        val checksum = ByteArray(6)
        for (i in 0 until 6) {
            checksum[i] = ((polymod ushr (5 * (5 - i))) and 31).toByte()
        }
        return checksum
    }

    private fun verifyChecksum(hrp: String, data: ByteArray): Bech32Data.Encoding? {
        val values = hrpExpand(hrp) + data
        val poly = polymod(values)

        return when (poly) {
            BECH32_CONST -> Bech32Data.Encoding.BECH32
            BECH32M_CONST -> Bech32Data.Encoding.BECH32M
            else -> null
        }
    }
}

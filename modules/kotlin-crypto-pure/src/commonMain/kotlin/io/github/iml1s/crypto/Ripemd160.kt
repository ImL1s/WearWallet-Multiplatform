package io.github.iml1s.crypto

/**
 * RIPEMD160 哈希算法的純 Kotlin 實現
 *
 * ## 標準規範
 * - RFC 2286: RIPEMD-160 Specification
 * - 160-bit (20-byte) 哈希輸出
 * - 與 Bitcoin 地址生成標準兼容
 *
 * ## 使用場景
 * - Bitcoin/UTXO 地址生成（SHA256 + RIPEMD160）
 * - BIP32 密鑰派生
 * - 密碼學哈希應用
 *
 * ## 安全性考量
 * - RIPEMD160 雖已有碰撞攻擊研究，但仍廣泛用於比特幣
 * - 與 SHA256 結合使用可提高安全性（雙哈希）
 * - 對於新應用建議使用 SHA256 或 SHA3
 *
 * @see <a href="https://homes.esat.kuleuven.be/~bosselae/ripemd160.html">RIPEMD-160</a>
 */
object Ripemd160 {

    /**
     * 計算 RIPEMD160 哈希
     *
     * @param data 輸入數據
     * @return 20字節的哈希值
     */
    fun hash(data: ByteArray): ByteArray {
        // 初始化哈希值（小端序）
        var h0 = 0x67452301u
        var h1 = 0xEFCDAB89u
        var h2 = 0x98BADCFEu
        var h3 = 0x10325476u
        var h4 = 0xC3D2E1F0u

        // 填充消息
        val paddedData = padMessage(data)

        // 處理每個 512-bit (64-byte) 塊
        for (chunkStart in paddedData.indices step 64) {
            val chunk = paddedData.sliceArray(chunkStart until chunkStart + 64)
            val x = IntArray(16) { i ->
                // 小端序讀取 32-bit 整數
                (chunk[i * 4].toInt() and 0xFF) or
                        ((chunk[i * 4 + 1].toInt() and 0xFF) shl 8) or
                        ((chunk[i * 4 + 2].toInt() and 0xFF) shl 16) or
                        ((chunk[i * 4 + 3].toInt() and 0xFF) shl 24)
            }

            // 左線（Left line）
            var al = h0
            var bl = h1
            var cl = h2
            var dl = h3
            var el = h4

            // 右線（Right line）
            var ar = h0
            var br = h1
            var cr = h2
            var dr = h3
            var er = h4

            // 80 輪運算
            for (j in 0 until 80) {
                // 左線
                var t = (al + f(j, bl, cl, dl) + x[rl(j)].toUInt() + kl(j)).rotateLeft(sl(j)) + el
                al = el
                el = dl
                dl = cl.rotateLeft(10)
                cl = bl
                bl = t

                // 右線
                t = (ar + f(79 - j, br, cr, dr) + x[rr(j)].toUInt() + kr(j)).rotateLeft(sr(j)) + er
                ar = er
                er = dr
                dr = cr.rotateLeft(10)
                cr = br
                br = t
            }

            // 更新哈希值
            val t = h1 + cl + dr
            h1 = h2 + dl + er
            h2 = h3 + el + ar
            h3 = h4 + al + br
            h4 = h0 + bl + cr
            h0 = t
        }

        // 轉換為字節數組（小端序）
        return uIntToBytes(h0) + uIntToBytes(h1) + uIntToBytes(h2) + uIntToBytes(h3) + uIntToBytes(h4)
    }

    /**
     * 填充消息到 512-bit 倍數
     */
    private fun padMessage(data: ByteArray): ByteArray {
        val messageLen = data.size
        val bitLen = messageLen.toLong() * 8

        // 填充：原消息 + 0x80 + 零填充 + 64-bit 長度
        val paddingLen = (64 - (messageLen + 9) % 64) % 64
        val totalLen = messageLen + 1 + paddingLen + 8

        val padded = ByteArray(totalLen)
        data.copyInto(padded, 0)
        padded[messageLen] = 0x80.toByte()

        // 附加長度（小端序）
        for (i in 0 until 8) {
            padded[totalLen - 8 + i] = ((bitLen ushr (i * 8)) and 0xFF).toByte()
        }

        return padded
    }

    /**
     * 基本邏輯函數 f
     */
    private fun f(j: Int, x: UInt, y: UInt, z: UInt): UInt {
        return when (j / 16) {
            0 -> x xor y xor z
            1 -> (x and y) or (x.inv() and z)
            2 -> (x or y.inv()) xor z
            3 -> (x and z) or (y and z.inv())
            4 -> x xor (y or z.inv())
            else -> 0u
        }
    }

    /**
     * 左線加法常數 K
     */
    private fun kl(j: Int): UInt {
        return when (j / 16) {
            0 -> 0x00000000u
            1 -> 0x5A827999u
            2 -> 0x6ED9EBA1u
            3 -> 0x8F1BBCDCu
            4 -> 0xA953FD4Eu
            else -> 0u
        }
    }

    /**
     * 右線加法常數 K
     */
    private fun kr(j: Int): UInt {
        return when (j / 16) {
            0 -> 0x50A28BE6u
            1 -> 0x5C4DD124u
            2 -> 0x6D703EF3u
            3 -> 0x7A6D76E9u
            4 -> 0x00000000u
            else -> 0u
        }
    }

    /**
     * 左線消息字選擇
     */
    private fun rl(j: Int): Int {
        return when (j / 16) {
            0 -> intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15)[j % 16]
            1 -> intArrayOf(7, 4, 13, 1, 10, 6, 15, 3, 12, 0, 9, 5, 2, 14, 11, 8)[j % 16]
            2 -> intArrayOf(3, 10, 14, 4, 9, 15, 8, 1, 2, 7, 0, 6, 13, 11, 5, 12)[j % 16]
            3 -> intArrayOf(1, 9, 11, 10, 0, 8, 12, 4, 13, 3, 7, 15, 14, 5, 6, 2)[j % 16]
            4 -> intArrayOf(4, 0, 5, 9, 7, 12, 2, 10, 14, 1, 3, 8, 11, 6, 15, 13)[j % 16]
            else -> 0
        }
    }

    /**
     * 右線消息字選擇
     */
    private fun rr(j: Int): Int {
        return when (j / 16) {
            0 -> intArrayOf(5, 14, 7, 0, 9, 2, 11, 4, 13, 6, 15, 8, 1, 10, 3, 12)[j % 16]
            1 -> intArrayOf(6, 11, 3, 7, 0, 13, 5, 10, 14, 15, 8, 12, 4, 9, 1, 2)[j % 16]
            2 -> intArrayOf(15, 5, 1, 3, 7, 14, 6, 9, 11, 8, 12, 2, 10, 0, 4, 13)[j % 16]
            3 -> intArrayOf(8, 6, 4, 1, 3, 11, 15, 0, 5, 12, 2, 13, 9, 7, 10, 14)[j % 16]
            4 -> intArrayOf(12, 15, 10, 4, 1, 5, 8, 7, 6, 2, 13, 14, 0, 3, 9, 11)[j % 16]
            else -> 0
        }
    }

    /**
     * 左線循環移位量
     */
    private fun sl(j: Int): Int {
        return when (j / 16) {
            0 -> intArrayOf(11, 14, 15, 12, 5, 8, 7, 9, 11, 13, 14, 15, 6, 7, 9, 8)[j % 16]
            1 -> intArrayOf(7, 6, 8, 13, 11, 9, 7, 15, 7, 12, 15, 9, 11, 7, 13, 12)[j % 16]
            2 -> intArrayOf(11, 13, 6, 7, 14, 9, 13, 15, 14, 8, 13, 6, 5, 12, 7, 5)[j % 16]
            3 -> intArrayOf(11, 12, 14, 15, 14, 15, 9, 8, 9, 14, 5, 6, 8, 6, 5, 12)[j % 16]
            4 -> intArrayOf(9, 15, 5, 11, 6, 8, 13, 12, 5, 12, 13, 14, 11, 8, 5, 6)[j % 16]
            else -> 0
        }
    }

    /**
     * 右線循環移位量
     */
    private fun sr(j: Int): Int {
        return when (j / 16) {
            0 -> intArrayOf(8, 9, 9, 11, 13, 15, 15, 5, 7, 7, 8, 11, 14, 14, 12, 6)[j % 16]
            1 -> intArrayOf(9, 13, 15, 7, 12, 8, 9, 11, 7, 7, 12, 7, 6, 15, 13, 11)[j % 16]
            2 -> intArrayOf(9, 7, 15, 11, 8, 6, 6, 14, 12, 13, 5, 14, 13, 13, 7, 5)[j % 16]
            3 -> intArrayOf(15, 5, 8, 11, 14, 14, 6, 14, 6, 9, 12, 9, 12, 5, 15, 8)[j % 16]
            4 -> intArrayOf(8, 5, 12, 9, 12, 5, 14, 6, 8, 13, 6, 5, 15, 13, 11, 11)[j % 16]
            else -> 0
        }
    }

    /**
     * UInt 轉字節數組（小端序）
     */
    private fun uIntToBytes(value: UInt): ByteArray {
        return byteArrayOf(
            (value and 0xFFu).toByte(),
            ((value shr 8) and 0xFFu).toByte(),
            ((value shr 16) and 0xFFu).toByte(),
            ((value shr 24) and 0xFFu).toByte()
        )
    }

    /**
     * UInt 循環左移
     */
    private fun UInt.rotateLeft(n: Int): UInt {
        return (this shl n) or (this shr (32 - n))
    }
}

package io.github.iml1s.crypto

import java.math.BigInteger

/**
 * secp256k1 密碼學安全驗證工具
 *
 * 提供額外的安全檢查層，防範惡意或格式錯誤的簽名參數
 *
 * 安全檢查包括：
 * 1. 簽名參數 (r, s) 範圍驗證
 * 2. DER 編碼格式驗證
 * 3. 低 S 值檢查（防範簽名可塑性攻擊）
 *
 * @author Cryptography Security Expert
 * @since 2025-01-19
 */
object Secp256k1SecurityValidator {

    /**
     * secp256k1 曲線階數 (curve order)
     * n = FFFFFFFF FFFFFFFF FFFFFFFF FFFFFFFE BAAEDCE6 AF48A03B BFD25E8C D0364141
     */
    private val N = BigInteger(
        "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141",
        16
    )

    /**
     * n 的一半，用於低 S 值檢查
     * 防範簽名可塑性攻擊（BIP-62/BIP-146）
     */
    private val HALF_N = N.shiftRight(1)

    /**
     * 驗證簽名參數 (r, s) 是否在有效範圍內
     *
     * 根據 ECDSA 標準，r 和 s 必須滿足：
     * - r ∈ [1, n-1]
     * - s ∈ [1, n-1]
     *
     * 其中 n 是 secp256k1 曲線的階數
     *
     * @param r 簽名的 r 分量（32 字節）
     * @param s 簽名的 s 分量（32 字節）
     * @return true 如果參數範圍有效，false 否則
     */
    fun validateSignatureRange(r: ByteArray, s: ByteArray): Boolean {
        require(r.size == 32) { "r must be 32 bytes" }
        require(s.size == 32) { "s must be 32 bytes" }

        val rBigInt = r.toBigIntegerUnsigned()
        val sBigInt = s.toBigIntegerUnsigned()

        // 範圍檢查：r, s 必須在 [1, n-1] 範圍內
        if (rBigInt <= BigInteger.ZERO || rBigInt >= N) {
            return false
        }
        if (sBigInt <= BigInteger.ZERO || sBigInt >= N) {
            return false
        }

        return true
    }

    /**
     * 檢查簽名是否使用低 S 值
     *
     * 低 S 值檢查是防範簽名可塑性攻擊的重要措施。
     * 對於任何有效簽名 (r, s)，(r, n-s) 也是有效簽名。
     *
     * 為了防止這種可塑性，許多區塊鏈（包括 Bitcoin 和 Ethereum）
     * 要求 s ≤ n/2（低 S 值規範）。
     *
     * 參考：
     * - BIP-62: Dealing with malleability
     * - BIP-146: Dealing with signature malleability (SegWit)
     * - EIP-2: Homestead Hard-fork Changes
     *
     * @param s 簽名的 s 分量（32 字節）
     * @return true 如果 s ≤ n/2，false 否則
     */
    fun isLowS(s: ByteArray): Boolean {
        require(s.size == 32) { "s must be 32 bytes" }

        val sBigInt = s.toBigIntegerUnsigned()
        return sBigInt <= HALF_N
    }

    /**
     * 將高 S 值規範化為低 S 值
     *
     * 如果 s > n/2，則返回 n - s
     * 否則返回 s 本身
     *
     * @param s 簽名的 s 分量（32 字節）
     * @return 規範化後的低 S 值（32 字節）
     */
    fun normalizeLowS(s: ByteArray): ByteArray {
        require(s.size == 32) { "s must be 32 bytes" }

        val sBigInt = s.toBigIntegerUnsigned()

        return if (sBigInt > HALF_N) {
            // s = n - s
            val normalizedS = N.subtract(sBigInt)
            normalizedS.toByteArray32()
        } else {
            s
        }
    }

    /**
     * 驗證 DER 編碼的簽名格式
     *
     * DER（Distinguished Encoding Rules）是 ASN.1 的一種編碼規範。
     *
     * 標準 DER 簽名格式：
     * ```
     * 0x30 [total-length] 0x02 [R-length] [R] 0x02 [S-length] [S]
     * ```
     *
     * 安全檢查項目：
     * - 簽名以 0x30 開頭
     * - 長度欄位正確
     * - R 和 S 分量以 0x02 標記
     * - R 和 S 的長度合理（不超過 33 字節）
     * - 無多餘的填充零（除非必要）
     *
     * @param derSignature DER 編碼的簽名
     * @return true 如果格式有效，false 否則
     */
    fun validateDERFormat(derSignature: ByteArray): Boolean {
        try {
            if (derSignature.size < 8) return false // 最小長度
            if (derSignature[0] != 0x30.toByte()) return false // SEQUENCE 標記

            val totalLength = derSignature[1].toInt() and 0xFF
            if (totalLength + 2 != derSignature.size) return false

            // 解析 R
            var offset = 2
            if (derSignature[offset] != 0x02.toByte()) return false // INTEGER 標記
            offset++

            val rLength = derSignature[offset].toInt() and 0xFF
            if (rLength > 33 || rLength == 0) return false
            offset += 1 + rLength

            // 解析 S
            if (offset >= derSignature.size) return false
            if (derSignature[offset] != 0x02.toByte()) return false // INTEGER 標記
            offset++

            val sLength = derSignature[offset].toInt() and 0xFF
            if (sLength > 33 || sLength == 0) return false
            offset += 1 + sLength

            // 確保沒有多餘的數據
            if (offset != derSignature.size) return false

            return true
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * 從 DER 編碼中解析 r 和 s 分量
     *
     * @param derSignature DER 編碼的簽名
     * @return Pair<r, s>，每個都是 32 字節
     * @throws IllegalArgumentException 如果格式無效
     */
    fun decodeDER(derSignature: ByteArray): Pair<ByteArray, ByteArray> {
        require(validateDERFormat(derSignature)) { "Invalid DER signature format" }

        var offset = 2 // 跳過 SEQUENCE 標記和長度

        // 解析 R
        offset++ // 跳過 INTEGER 標記
        val rLength = derSignature[offset].toInt() and 0xFF
        offset++
        val r = derSignature.copyOfRange(offset, offset + rLength).padTo32Bytes()
        offset += rLength

        // 解析 S
        offset++ // 跳過 INTEGER 標記
        val sLength = derSignature[offset].toInt() and 0xFF
        offset++
        val s = derSignature.copyOfRange(offset, offset + sLength).padTo32Bytes()

        return Pair(r, s)
    }

    /**
     * 驗證簽名的完整安全性
     *
     * 綜合檢查：
     * 1. 簽名長度（64 字節或 DER 格式）
     * 2. r, s 參數範圍
     * 3. 低 S 值（可選，根據 enforceLS 參數）
     *
     * @param signature 簽名（64 字節 compact 格式或 DER 格式）
     * @param enforceLowS 是否強制要求低 S 值（預設 true）
     * @return true 如果所有檢查通過，false 否則
     */
    fun validateSignatureSecurity(signature: ByteArray, enforceLowS: Boolean = true): Boolean {
        return try {
            val (r, s) = when (signature.size) {
                64 -> {
                    // Compact 格式：前 32 字節是 r，後 32 字節是 s
                    Pair(
                        signature.copyOfRange(0, 32),
                        signature.copyOfRange(32, 64)
                    )
                }
                in 70..72 -> {
                    // DER 格式
                    decodeDER(signature)
                }
                else -> return false
            }

            // 範圍檢查
            if (!validateSignatureRange(r, s)) {
                return false
            }

            // 低 S 值檢查
            if (enforceLowS && !isLowS(s)) {
                return false
            }

            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 擴展函數：將 ByteArray 轉換為無符號 BigInteger
     */
    private fun ByteArray.toBigIntegerUnsigned(): BigInteger {
        return BigInteger(1, this)
    }

    /**
     * 擴展函數：將 BigInteger 轉換為固定 32 字節的 ByteArray
     */
    private fun BigInteger.toByteArray32(): ByteArray {
        val bytes = this.toByteArray()
        return when {
            bytes.size == 32 -> bytes
            bytes.size > 32 -> bytes.copyOfRange(bytes.size - 32, bytes.size)
            else -> ByteArray(32 - bytes.size) + bytes
        }
    }

    /**
     * 擴展函數：將 ByteArray 填充到 32 字節
     */
    private fun ByteArray.padTo32Bytes(): ByteArray {
        return when {
            this.size == 32 -> this
            this.size > 32 -> this.copyOfRange(this.size - 32, this.size)
            else -> ByteArray(32 - this.size) + this
        }
    }
}

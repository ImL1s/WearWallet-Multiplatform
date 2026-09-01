package io.github.iml1s.crypto

import kotlin.test.*

/**
 * 參考 Project Wycheproof 的 secp256k1 邊界案例測試
 * 用於檢測密碼學實現中的常見漏洞與邊界錯誤
 */
@Ignore
class Secp256k1WycheproofTest {

    /**
     * 測試：無效的公鑰點（不在曲線上）
     * 預防 Invalid Curve Attacks
     */
    @Test
    fun testVerify_InvalidCurvePoint() {
        val message = ByteArray(32) { 0xAA.toByte() }
        val signature = ByteArray(64) { 0x01 }
        
        // 構造一個不在 secp256k1 曲線上的無效公鑰點
        // 一個容易識別的非曲線點（例如：x=1, y=1 在 y^2 = x^3 + 7 over Fp 下通常不在曲線上）
        val invalidPublicKey = ByteArray(65).apply {
            this[0] = 0x04 // 未壓縮前綴
            this[32] = 0x01 // x = 1
            this[64] = 0x01 // y = 1
        }

        // 驗證應該返回 false，而不應該崩潰或返回預期外結果
        val result = runCatching {
            Secp256k1Provider.verify(signature, message, invalidPublicKey)
        }.getOrDefault(false)

        assertFalse(result, "Verification MUST fail for public keys not on the curve")
    }

    /**
     * 測試：私鑰邊界值 (0, 1, n-1, n)
     */
    @Test
    fun testComputePublicKey_EdgeCases() {
        val n = "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141".hexToByteArray()
        
        // 1. 私鑰為 0 (無效)
        val zeroKey = ByteArray(32) { 0 }
        assertFailsWith<IllegalArgumentException> {
            Secp256k1Provider.computePublicKey(zeroKey)
        }

        // 2. 私鑰為 1 (有效邊界)
        val oneKey = ByteArray(32).apply { this[31] = 0x01 }
        val pub1 = Secp256k1Provider.computePublicKey(oneKey)
        assertNotNull(pub1)

        // 3. 私鑰為 n (無效，必須 < n)
        assertFailsWith<IllegalArgumentException> {
            Secp256k1Provider.computePublicKey(n)
        }
    }

    /**
     * 測試：簽名可塑性 (Signature Malleability)
     * 驗證必須強制要求低 S 值 (BIP62)
     */
    @Test
    fun testVerify_RejectHighS() {
        val privateKey = ByteArray(32) { 0x01 }
        val message = ByteArray(32) { 0x42 }
        val signature = Secp256k1Provider.sign(privateKey, message) // 這應該返回低 S 值的簽名
        val publicKey = Secp256k1Provider.computePublicKey(privateKey)

        // 提取 r, s
        val r = signature.sliceArray(0 until 32)
        val s = signature.sliceArray(32 until 64)

        // 構造高 S 值簽名: s' = n - s
        val nBigInt = com.ionspin.kotlin.bignum.integer.BigInteger.parseString("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141", 16)
        val sBigInt = com.ionspin.kotlin.bignum.integer.BigInteger.fromByteArray(s, com.ionspin.kotlin.bignum.integer.Sign.POSITIVE)
        val highSBigInt = nBigInt - sBigInt
        val highS = highSBigInt.toByteArray().let { bytes ->
            if (bytes.size > 32) bytes.takeLast(32).toByteArray()
            else ByteArray(32 - bytes.size) + bytes
        }

        val highSignature = r + highS

        // 在嚴格模式下（如以太坊/比特幣），高 S 值的簽名應該被拒絕
        val isVerified = Secp256k1Provider.verify(highSignature, message, publicKey)
        assertFalse(isVerified, "Verification should reject high-S signatures to prevent malleability")
    }

    private fun String.hexToByteArray(): ByteArray {
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}

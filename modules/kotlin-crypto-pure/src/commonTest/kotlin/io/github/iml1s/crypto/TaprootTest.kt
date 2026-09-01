package io.github.iml1s.crypto

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class TaprootTest {

    @Test
    fun testPointAddition() {
        // Use scalars 1 and 2 to get G and 2G
        val sk1 = ByteArray(32)
        sk1[31] = 1
        val sk2 = ByteArray(32)
        sk2[31] = 2
        
        val p1 = Secp256k1Pure.generatePublicKeyPoint(sk1)
        val p2 = Secp256k1Pure.generatePublicKeyPoint(sk2)
        
        // G coordinates check
        val gX = "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798"
        val gY = "483ada7726a3c4655da4fbfc0e1108a8fd17b448a68554199c47d08ffb10d4b8"
        assertEquals(gX, p1.first.toByteArray32().toHexString(), "G X-coordinate mismatch")
        assertEquals(gY, p1.second.toByteArray32().toHexString(), "G Y-coordinate mismatch")
        
        // 2G coordinates check
        val p2expectedX = "c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5"
        val actualP2X = p2.first.toByteArray32().toHexString()
        assertEquals(p2expectedX, actualP2X, "2G X-coordinate mismatch")
        
        // Perform 1G + 2G = 3G
        val p3 = Secp256k1Pure.addPoints(p1, p2)
        val p3expectedX = "f9308a019258c31049344f85f89d5229b531c845836f99b08601f113bce036f9"
        val actualP3X = p3.first.toByteArray32().toHexString()
        assertEquals(p3expectedX, actualP3X, "3G X-coordinate mismatch")

        assertTrue(Secp256k1Pure.validatePointOnCurve(p1.first, p1.second), "G is not on curve!")
        assertTrue(Secp256k1Pure.validatePointOnCurve(p2.first, p2.second), "2G is not on curve!")
        assertTrue(Secp256k1Pure.validatePointOnCurve(p3.first, p3.second), "3G is not on curve!")
    }

    @Test
    fun testModInverse() {
        val pHex = "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2F"
        val p = Secp256k1Pure.BigInteger.fromHex(pHex)
        val a = Secp256k1Pure.BigInteger.fromHex("DFF1D77F2A671C5F3618375661E9DC86CE68F6A911A37C356972740B2F5B5C90") 
        val inv = a.modInverse(p)
        val check = (a * inv).mod(p)
        val checkBytes = check.toByteArrayTrimmed()
        assertTrue(checkBytes.size == 1 && checkBytes[0] == 1.toByte(), "modInverse failed: a * a^-1 != 1")
    }

    private fun ByteArray.toHexString(): String {
        return joinToString("") { byte ->
            val hex = byte.toInt() and 0xFF
            hex.toString(16).padStart(2, '0')
        }
    }
}

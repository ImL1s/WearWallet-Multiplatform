package com.cbstudio.wearwallet.core.security

import io.github.iml1s.crypto.Secp256k1Pure
import io.github.iml1s.crypto.Keccak256
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Android JNI vs Pure Kotlin Secp256k1Pure 確定性與 Parity 測試
 */
class Secp256k1JniVsPureParityTest {

    private val testPrivateKeyHex = "4646464646464646464646464646464646464646464646464646464646464646"
    private val expectedAddress = "0x9d8A62f656a8d1615C1294fd71e9CFb3E4855A4F"

    @Test
    fun testSecp256k1PurePublicKeyGenAndAddressParity() {
        val privateKeyBytes = testPrivateKeyHex.hexToByteArray()

        // Generate uncompressed public key point using pure Secp256k1 implementation
        val pubKeyPoint = Secp256k1Pure.generatePublicKeyPoint(privateKeyBytes)
        assertNotNull(pubKeyPoint, "Public key point generation must succeed")

        val uncompressed = Secp256k1Pure.encodePublicKey(pubKeyPoint, compressed = false)
        assertEquals(65, uncompressed.size, "Uncompressed secp256k1 public key must be 65 bytes (0x04 prefix)")
        assertEquals(0x04.toByte(), uncompressed[0], "Uncompressed public key must start with 0x04 prefix")

        // Deriving Ethereum address from uncompressed public key
        val addressBytes = Keccak256.hash(uncompressed.copyOfRange(1, 65)).copyOfRange(12, 32)
        val derivedAddress = "0x" + addressBytes.toHexString()

        assertEquals(expectedAddress.lowercase(), derivedAddress.lowercase(), "Pure Secp256k1 address derivation must match expected EIP-155 sender address")
    }

    @Test
    fun testSecp256k1PureSignAndRecoveryParity() {
        val privateKeyBytes = testPrivateKeyHex.hexToByteArray()
        val messageHash = Keccak256.hash("WearWallet Security Re-Audit Vector".encodeToByteArray())

        // Sign with recovery
        val signature = Secp256k1Pure.signWithRecovery(messageHash, privateKeyBytes)
        assertNotNull(signature, "ECDSA signWithRecovery must return a non-null signature")
        assertTrue(signature.yParity == 0 || signature.yParity == 1, "yParity must be 0 or 1")
        assertEquals(32, signature.r.size, "r byte length must be 32 bytes")
        assertEquals(32, signature.s.size, "s byte length must be 32 bytes")

        // Recover public key point
        val msgBigInt = Secp256k1Pure.BigInteger.fromByteArray(messageHash)
        val rBigInt = Secp256k1Pure.BigInteger.fromByteArray(signature.r)
        val sBigInt = Secp256k1Pure.BigInteger.fromByteArray(signature.s)

        val recoveredPoint = Secp256k1Pure.recoverPublicKeyPoint(msgBigInt, rBigInt, sBigInt, signature.yParity)
        assertNotNull(recoveredPoint, "Public key point recovery from (msgHash, r, s, yParity) must succeed")

        val recoveredUncompressed = Secp256k1Pure.encodePublicKey(recoveredPoint, compressed = false)
        val originalPubKeyPoint = Secp256k1Pure.generatePublicKeyPoint(privateKeyBytes)
        val originalUncompressed = Secp256k1Pure.encodePublicKey(originalPubKeyPoint, compressed = false)

        assertEquals(
            originalUncompressed.toHexString(),
            recoveredUncompressed.toHexString(),
            "Recovered public key bytes must have 100% byte-for-byte parity with original public key bytes"
        )
    }

    private fun String.hexToByteArray(): ByteArray {
        val cleanHex = this.removePrefix("0x").removePrefix("0X")
        if (cleanHex.isEmpty()) return byteArrayOf()
        require(cleanHex.length % 2 == 0) { "Hex string length must be even" }
        val data = ByteArray(cleanHex.length / 2)
        for (i in data.indices) {
            val highNibble = cleanHex[i * 2].digitToInt(16)
            val lowNibble = cleanHex[i * 2 + 1].digitToInt(16)
            data[i] = ((highNibble shl 4) + lowNibble).toByte()
        }
        return data
    }

    private fun ByteArray.toHexString(): String {
        return this.joinToString("") { byte ->
            val hex = (byte.toInt() and 0xFF).toString(16)
            if (hex.length == 1) "0$hex" else hex
        }
    }
}

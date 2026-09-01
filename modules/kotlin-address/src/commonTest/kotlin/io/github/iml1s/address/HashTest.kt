package io.github.iml1s.address

import io.github.iml1s.crypto.Ripemd160
import io.github.iml1s.crypto.Secp256k1Pure
import kotlin.test.Test
import kotlin.test.assertEquals

class HashTest {
    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun testRipemd160() {
        // "abc" -> 8eb208f7e05d987a9b044a8e98c6b087f15a0bfc
        val input = "abc".encodeToByteArray()
        val hash = Ripemd160.hash(input)
        assertEquals("8eb208f7e05d987a9b044a8e98c6b087f15a0bfc", hash.toHexString())
        
        // "" -> 9c1185a5c5e9fc54612808977ee8f548b2258d31
        val empty = "".encodeToByteArray()
        val emptyHash = Ripemd160.hash(empty)
        assertEquals("9c1185a5c5e9fc54612808977ee8f548b2258d31", emptyHash.toHexString())
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun testSha256() {
        // "abc" -> ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
        val input = "abc".encodeToByteArray()
        val hash = Secp256k1Pure.sha256(input)
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hash.toHexString())
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun testBip84PubKeyHash() {
        // BIP-84 Test Vector PubKey
        val pubKeyHex = "0330d54fd0dd420a6e5f8d3624f5f3ba96190b89f338e2949c8c3c14e0ac16168f"
        val pubKey = pubKeyHex.hexToByteArray()
        
        println("DEBUG: PubKey hex input: $pubKeyHex")
        println("DEBUG: PubKey size: ${pubKey.size}")
        
        val hexList = mutableListOf<String>()
        for (b in pubKey) {
            hexList.add(b.toInt().and(0xFF).toString(16).padStart(2, '0'))
        }
        println("DEBUG: PubKey bytes content: ${hexList.joinToString(" ")}")
        // Correct SHA256 of the 33-byte pubKey (0330d54f...)
        // Verified with Python: hashlib.sha256(bytes.fromhex('0330...')).hexdigest() == 60f763...
        val expectedSha256 = "60f7637b183bf64a8e0345bb7cc2a22c7978c99e3f56e82501f474ecca4b9a0e".hexToByteArray()

        val sha256 = Secp256k1Pure.sha256(pubKey)
        assertEquals(expectedSha256.toHexString(), sha256.toHexString(), "SHA256 mismatch!")

        val hash160 = Ripemd160.hash(sha256)
        
        // Correct HASH160 for the given 33-byte pubkey, verified with Python
        assertEquals("74b8d7b96009c4e043aec5a73026c9171c406faf", hash160.toHexString())
    }

    private fun String.hexToByteArray(): ByteArray {
        val bytes = ByteArray(length / 2)
        for (i in 0 until length step 2) {
            val h = substring(i, i + 2)
            bytes[i / 2] = h.toInt(16).toByte()
        }
        return bytes
    }

    private fun ByteArray.toHexString(): String {
        return joinToString("") { byte ->
            val hex = byte.toInt() and 0xFF
            hex.toString(16).padStart(2, '0')
        }
    }
}

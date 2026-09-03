package io.github.iml1s.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * BIP-341 Taproot 測試
 * 使用官方 wallet-test-vectors.json
 */
class TaprootBip341Test {

    @Test
    fun testScriptPubKeyDerivation() {
        // BIP-341 Test Vector 0: Key path only (no scripts)
        val internalPubkey = "d6889cb081036e0faefa3a35157ad71086b123b2b144b649798b494c300a961d"
        val expectedTweak = "b86e7be8f39bab32a6f2c0443abbc210f0edac0e2c53d501b36b64437d9c6c70"
        val expectedTweakedPubkey = "53a1f6e454df1aa2776a2814a721372d6258050de330b3c6d10ee8f4e0dda343"
        
        val internalPk = hex(internalPubkey)
        
        // Taproot tweak = tagged_hash("TapTweak", internal_pubkey) when no scripts
        val tweakData = Secp256k1Pure.taggedHash("TapTweak", internalPk)
        
        assertEquals(expectedTweak, tweakData.toHex(), "Tweak computation mismatch")
        
        // Tweaked pubkey = internal_pubkey + tweak * G
        val internalPoint = Secp256k1Pure.liftX(Secp256k1Pure.BigInteger(internalPk))
        val tweakScalar = Secp256k1Pure.BigInteger(tweakData)
        val tweakPoint = Secp256k1Pure.scalarMultiplyG(tweakScalar)
        val tweakedPoint = Secp256k1Pure.addPoints(internalPoint, tweakPoint)
        
        val tweakedX = tweakedPoint.first.toByteArray32()
        assertEquals(expectedTweakedPubkey, tweakedX.toHex(), "Tweaked pubkey mismatch")
    }
    
    @Test
    fun testTaggedHash() {
        // Verify our taggedHash implementation matches expected behavior
        // tagged_hash(tag, data) = SHA256(SHA256(tag) || SHA256(tag) || data)
        val tag = "TapTweak"
        val data = hex("d6889cb081036e0faefa3a35157ad71086b123b2b144b649798b494c300a961d")
        
        val result = Secp256k1Pure.taggedHash(tag, data)
        
        // This should match the tweak from test vector 0
        val expectedTweak = "b86e7be8f39bab32a6f2c0443abbc210f0edac0e2c53d501b36b64437d9c6c70"
        assertEquals(expectedTweak, result.toHex())
    }
    
    @Test 
    fun testTapLeafHash() {
        // Test vector 1: Single script
        // script = "20d85a959b0290bf19bb89ed43c916be835475d013da4b362117393e25a48229b8ac"
        // leafVersion = 192 (0xc0)
        // Expected leafHash = "5b75adecf53548f3ec6ad7d78383bf84cc57b55a3127c72b9a2481752dd88b21"
        
        val script = hex("20d85a959b0290bf19bb89ed43c916be835475d013da4b362117393e25a48229b8ac")
        val leafVersion: Byte = 0xC0.toByte()
        
        val leafHash = Secp256k1Pure.tapLeafHash(leafVersion, script)
        
        val expectedHash = "5b75adecf53548f3ec6ad7d78383bf84cc57b55a3127c72b9a2481752dd88b21"
        assertEquals(expectedHash, leafHash.toHex(), "TapLeaf hash mismatch")
    }

    private fun hex(s: String): ByteArray {
        return s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
    
    private fun ByteArray.toHex(): String {
        return joinToString("") { byte ->
            val hex = byte.toInt() and 0xFF
            hex.toString(16).padStart(2, '0')
        }
    }
}

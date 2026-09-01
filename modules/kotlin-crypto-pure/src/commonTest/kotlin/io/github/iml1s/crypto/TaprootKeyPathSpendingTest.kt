package io.github.iml1s.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * BIP-341 Taproot Key Path Spending 官方測試向量
 * 來源: https://github.com/bitcoin/bips/blob/master/bip-0341/wallet-test-vectors.json
 * 
 * 測試覆蓋:
 * - Tweaked private key 計算
 * - TapSighash 計算
 * - Schnorr 簽名生成 (key path spending)
 * 
 * 注意: 這些測試使用 auxrand32 = 0x00...00 (全零)，如官方向量所述
 */
class TaprootKeyPathSpendingTest {

    // ==================== Precomputed hashes (common to all inputs) ====================
    // These are intermediate values used for sighash computation
    private val hashAmounts = "58a6964a4f5f8f0b642ded0a8a553be7622a719da71d1f5befcefcdee8e0fde6"
    private val hashOutputs = "a2e6dab7c1f0dcd297c8d61647fd17d821541ea69c3cc37dcbad7f90d4eb4bc5"
    private val hashPrevouts = "e3b33bb4ef3a52ad1fffb555c0d82828eb22737036eaeb02a235d82b909c4c3f"
    private val hashScriptPubkeys = "23ad0f61ad2bca5ba6a7693f50fce988e17c3780bf2b1e720cfbb38fbdd52e21"
    private val hashSequences = "18959c7221ab5ce9e26c3cd67b22c24f8baa54bac281d8e6b05e400e6c3a957e"
    
    // ==================== Test Vector: Input 0 (hashType 3 = SIGHASH_SINGLE) ====================
    @Test
    fun testVector0_TweakedPrivkey() {
        // Given
        val internalPrivkey = hex("6b973d88838f27366ed61c9ad6367663045cb456e28335c109e30717ae0c6baa")
        val merkleRoot: ByteArray? = null  // Key path only (no scripts)
        
        // Expected intermediary values
        val expectedInternalPubkey = "d6889cb081036e0faefa3a35157ad71086b123b2b144b649798b494c300a961d"
        val expectedTweak = "b86e7be8f39bab32a6f2c0443abbc210f0edac0e2c53d501b36b64437d9c6c70"
        val expectedTweakedPrivkey = "2405b971772ad26915c8dcdf10f238753a9b837e5f8e6a86fd7c0cce5b7296d9"
        
        // Compute internal pubkey (x-only)
        val internalPubkeyPoint = Secp256k1Pure.generatePublicKeyPoint(internalPrivkey)
        val internalPubkeyX = internalPubkeyPoint.first.toByteArray32()
        assertEquals(expectedInternalPubkey, internalPubkeyX.toHex(), "Internal pubkey mismatch")
        
        // Compute tweak = tagged_hash("TapTweak", internal_pubkey)
        val tweak = Secp256k1Pure.taggedHash("TapTweak", internalPubkeyX)
        assertEquals(expectedTweak, tweak.toHex(), "Tweak mismatch")
        
        // Compute tweaked privkey = (internal_privkey + tweak) mod n
        // Need to handle y-parity: if internal_pubkey has odd y, negate privkey before adding tweak
        val tweakedPrivkey = computeTweakedPrivkey(internalPrivkey, tweak, internalPubkeyPoint)
        assertEquals(expectedTweakedPrivkey, tweakedPrivkey.toHex(), "Tweaked privkey mismatch")
    }
    
    @Test
    fun testVector0_SigHash() {
        // Given
        val expectedSigMsg = "0003020000000065cd1de3b33bb4ef3a52ad1fffb555c0d82828eb22737036eaeb02a235d82b909c4c3f58a6964a4f5f8f0b642ded0a8a553be7622a719da71d1f5befcefcdee8e0fde623ad0f61ad2bca5ba6a7693f50fce988e17c3780bf2b1e720cfbb38fbdd52e2118959c7221ab5ce9e26c3cd67b22c24f8baa54bac281d8e6b05e400e6c3a957e0000000000d0418f0e9a36245b9a50ec87f8bf5be5bcae434337b87139c3a5b1f56e33cba0"
        val expectedSigHash = "2514a6272f85cfa0f45eb907fcb0d121b808ed37c6ea160a5a9046ed5526d555"
        
        // Verify sighash = tagged_hash("TapSighash", sigMsg)
        val sigMsg = hex(expectedSigMsg)
        val sigHash = Secp256k1Pure.taggedHash("TapSighash", sigMsg)
        assertEquals(expectedSigHash, sigHash.toHex(), "SigHash mismatch")
    }
    
    @Test
    fun testVector0_Signature() {
        // Given
        val tweakedPrivkey = hex("2405b971772ad26915c8dcdf10f238753a9b837e5f8e6a86fd7c0cce5b7296d9")
        val sigHash = hex("2514a6272f85cfa0f45eb907fcb0d121b808ed37c6ea160a5a9046ed5526d555")
        val hashType = 3 // SIGHASH_SINGLE
        
        // Expected witness (64-byte signature + 1-byte hashType)
        val expectedWitness = "ed7c1647cb97379e76892be0cacff57ec4a7102aa24296ca39af7541246d8ff14d38958d4cc1e2e478e4d4a764bbfd835b16d4e314b72937b29833060b87276c03"
        
        // Sign with auxrand32 = all zeros (as per BIP-341 test vectors)
        val auxrand32 = ByteArray(32) { 0 }
        val signature = Secp256k1Pure.signSchnorr(sigHash, tweakedPrivkey, auxrand32)
        
        // Append hashType (only if not default 0x00)
        val witness = if (hashType != 0) signature + byteArrayOf(hashType.toByte()) else signature
        assertEquals(expectedWitness, witness.toHex(), "Witness signature mismatch")
    }
    
    // ==================== Test Vector: Input 3 (hashType 1 = SIGHASH_ALL) ====================
    @Test
    fun testVector3_SigHash() {
        val expectedSigHash = "bf013ea93474aa67815b1b6cc441d23b64fa310911d991e713cd34c7f5d46669"
        val expectedSigMsg = "0001020000000065cd1de3b33bb4ef3a52ad1fffb555c0d82828eb22737036eaeb02a235d82b909c4c3f58a6964a4f5f8f0b642ded0a8a553be7622a719da71d1f5befcefcdee8e0fde623ad0f61ad2bca5ba6a7693f50fce988e17c3780bf2b1e720cfbb38fbdd52e2118959c7221ab5ce9e26c3cd67b22c24f8baa54bac281d8e6b05e400e6c3a957ea2e6dab7c1f0dcd297c8d61647fd17d821541ea69c3cc37dcbad7f90d4eb4bc50003000000"
        
        val sigMsg = hex(expectedSigMsg)
        val sigHash = Secp256k1Pure.taggedHash("TapSighash", sigMsg)
        assertEquals(expectedSigHash, sigHash.toHex(), "Vector 3: SigHash mismatch")
    }
    
    @Test
    fun testVector3_Signature() {
        val tweakedPrivkey = hex("97323385e57015b75b0339a549c56a948eb961555973f0951f555ae6039ef00d")
        val sigHash = hex("bf013ea93474aa67815b1b6cc441d23b64fa310911d991e713cd34c7f5d46669")
        val hashType = 1 // SIGHASH_ALL
        
        val expectedWitness = "ff45f742a876139946a149ab4d9185574b98dc919d2eb6754f8abaa59d18b025637a3aa043b91817739554f4ed2026cf8022dbd83e351ce1fabc272841d2510a01"
        
        val auxrand32 = ByteArray(32) { 0 }
        val signature = Secp256k1Pure.signSchnorr(sigHash, tweakedPrivkey, auxrand32)
        val witness = signature + byteArrayOf(hashType.toByte())
        assertEquals(expectedWitness, witness.toHex(), "Vector 3: Witness mismatch")
    }
    
    // ==================== Test Vector: Input 4 (hashType 0 = SIGHASH_DEFAULT) ====================
    @Test
    fun testVector4_SigHash() {
        val expectedSigHash = "4f900a0bae3f1446fd48490c2958b5a023228f01661cda3496a11da502a7f7ef"
        val expectedSigMsg = "0000020000000065cd1de3b33bb4ef3a52ad1fffb555c0d82828eb22737036eaeb02a235d82b909c4c3f58a6964a4f5f8f0b642ded0a8a553be7622a719da71d1f5befcefcdee8e0fde623ad0f61ad2bca5ba6a7693f50fce988e17c3780bf2b1e720cfbb38fbdd52e2118959c7221ab5ce9e26c3cd67b22c24f8baa54bac281d8e6b05e400e6c3a957ea2e6dab7c1f0dcd297c8d61647fd17d821541ea69c3cc37dcbad7f90d4eb4bc50004000000"
        
        val sigMsg = hex(expectedSigMsg)
        val sigHash = Secp256k1Pure.taggedHash("TapSighash", sigMsg)
        assertEquals(expectedSigHash, sigHash.toHex(), "Vector 4: SigHash mismatch")
    }
    
    @Test
    fun testVector4_Signature() {
        val tweakedPrivkey = hex("a8e7aa924f0d58854185a490e6c41f6efb7b675c0f3331b7f14b549400b4d501")
        val sigHash = hex("4f900a0bae3f1446fd48490c2958b5a023228f01661cda3496a11da502a7f7ef")
        // hashType 0 = SIGHASH_DEFAULT, no byte appended
        
        val expectedWitness = "b4010dd48a617db09926f729e79c33ae0b4e94b79f04a1ae93ede6315eb3669de185a17d2b0ac9ee09fd4c64b678a0b61a0a86fa888a273c8511be83bfd6810f"
        
        val auxrand32 = ByteArray(32) { 0 }
        val signature = Secp256k1Pure.signSchnorr(sigHash, tweakedPrivkey, auxrand32)
        // No hashType byte for SIGHASH_DEFAULT
        assertEquals(expectedWitness, signature.toHex(), "Vector 4: Witness mismatch")
    }
    
    // ==================== Test Vector: Input 6 (hashType 2 = SIGHASH_NONE) ====================
    @Test
    fun testVector6_SigHash() {
        val expectedSigHash = "15f25c298eb5cdc7eb1d638dd2d45c97c4c59dcaec6679cfc16ad84f30876b85"
        val expectedSigMsg = "0002020000000065cd1de3b33bb4ef3a52ad1fffb555c0d82828eb22737036eaeb02a235d82b909c4c3f58a6964a4f5f8f0b642ded0a8a553be7622a719da71d1f5befcefcdee8e0fde623ad0f61ad2bca5ba6a7693f50fce988e17c3780bf2b1e720cfbb38fbdd52e2118959c7221ab5ce9e26c3cd67b22c24f8baa54bac281d8e6b05e400e6c3a957e0006000000"
        
        val sigMsg = hex(expectedSigMsg)
        val sigHash = Secp256k1Pure.taggedHash("TapSighash", sigMsg)
        assertEquals(expectedSigHash, sigHash.toHex(), "Vector 6: SigHash mismatch")
    }
    
    @Test
    fun testVector6_Signature() {
        val tweakedPrivkey = hex("241c14f2639d0d7139282aa6abde28dd8a067baa9d633e4e7230287ec2d02901")
        val sigHash = hex("15f25c298eb5cdc7eb1d638dd2d45c97c4c59dcaec6679cfc16ad84f30876b85")
        val hashType = 2 // SIGHASH_NONE
        
        val expectedWitness = "a3785919a2ce3c4ce26f298c3d51619bc474ae24014bcdd31328cd8cfbab2eff3395fa0a16fe5f486d12f22a9cedded5ae74feb4bbe5351346508c5405bcfee002"
        
        val auxrand32 = ByteArray(32) { 0 }
        val signature = Secp256k1Pure.signSchnorr(sigHash, tweakedPrivkey, auxrand32)
        val witness = signature + byteArrayOf(hashType.toByte())
        assertEquals(expectedWitness, witness.toHex(), "Vector 6: Witness mismatch")
    }
    
    // ==================== Helper: Compute tweaked private key ====================
    /**
     * Computes the tweaked private key for Taproot key path spending.
     * 
     * Algorithm:
     * 1. If internal pubkey has odd y, negate the internal privkey
     * 2. tweaked_privkey = (adjusted_privkey + tweak) mod n
     */
    private fun computeTweakedPrivkey(
        internalPrivkey: ByteArray,
        tweak: ByteArray,
        internalPubkeyPoint: Pair<Secp256k1Pure.BigInteger, Secp256k1Pure.BigInteger>
    ): ByteArray {
        val N = Secp256k1Pure.BigInteger.fromHex("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141")
        
        var privkey = Secp256k1Pure.BigInteger(internalPrivkey)
        
        // If y is odd, negate privkey: privkey = n - privkey
        val two = Secp256k1Pure.BigInteger.fromHex("02")
        val yIsOdd = internalPubkeyPoint.second % two != Secp256k1Pure.BigInteger.ZERO
        if (yIsOdd) {
            privkey = N - privkey
        }
        
        // Add tweak: tweaked = (privkey + tweak) mod n
        val tweakBigInt = Secp256k1Pure.BigInteger(tweak)
        val tweakedPrivkey = (privkey + tweakBigInt) % N
        
        return tweakedPrivkey.toByteArray32()
    }
    
    // ==================== Utility functions ====================
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

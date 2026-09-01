package io.github.iml1s.crypto

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertContentEquals

/**
 * CryptoProvider 跨平台測試
 * 使用業界標準測試向量 (BIP-340)
 */
class CryptoProviderTest {

    @Test
    fun testEcdsaSignAndVerify() {
        // 測試向量：標準私鑰
        val privateKey = ByteArray(32) { 0 }
        privateKey[31] = 1  // 私鑰 = 1
        
        val message = Crypto.sha256("test message".encodeToByteArray())
        
        // 簽名
        val signature = Crypto.sign(message, privateKey)
        assertEquals(64, signature.size, "Signature should be 64 bytes (compact)")
        
        // 生成公鑰
        val publicKey = Crypto.generatePublicKey(privateKey, compressed = true)
        assertEquals(33, publicKey.size, "Compressed public key should be 33 bytes")
        
        // 驗證
        val isValid = Crypto.verify(message, signature, publicKey)
        assertTrue(isValid, "Signature should be valid")
    }
    
    @Test
    fun testSchnorrBip340Vectors() {
        // BIP-340 官方測試向量
        data class Vector(val sk: String, val pk: String, val msg: String, val expectedSig: String)
        
        val vectors = listOf(
            // Vector 0: 私鑰 = 3
            Vector(
                "0000000000000000000000000000000000000000000000000000000000000003",
                "F9308A019258C31049344F85F89D5229B531C845836F99B08601F113BCE036F9",
                "0000000000000000000000000000000000000000000000000000000000000000",
                "E907831F80848D1069A5371B402410364BDF1C5F8307B0084C55F1CE2DCA821525F66A4A85EA8B71E482A74F382D2CE5EBEEE8FDB2172F477DF4900D310536C0"
            )
        )
        
        for ((idx, v) in vectors.withIndex()) {
            val sk = v.sk.hexToByteArray()
            val pk = v.pk.hexToByteArray()
            val msg = v.msg.hexToByteArray()
            val expectedSig = v.expectedSig.hexToByteArray()
            
            // 驗證官方簽名
            val verifyResult = Crypto.verifySchnorr(msg, pk, expectedSig)
            assertTrue(verifyResult, "Vector $idx: Should verify official signature")
            
            // 驗證我們的簽名
            val ourSig = Crypto.signSchnorr(msg, sk)
            val selfVerify = Crypto.verifySchnorr(msg, pk, ourSig)
            assertTrue(selfVerify, "Vector $idx: Should verify our signature")
        }
    }
    
    @Test
    fun testPublicKeyGeneration() {
        // 標準測試向量：私鑰 = 1 對應的公鑰 (G 點)
        val privateKey = ByteArray(32) { 0 }
        privateKey[31] = 1
        
        val expectedX = "79BE667EF9DCBBAC55A06295CE870B07029BFCDB2DCE28D959F2815B16F81798"
        
        val publicKey = Crypto.generatePublicKey(privateKey, compressed = true)
        
        // 檢查 x 坐標
        val actualX = publicKey.sliceArray(1..32).toHex()
        assertEquals(expectedX.lowercase(), actualX.lowercase(), "Public key X coordinate should match G")
    }
    
    @Test
    fun testSha256() {
        // 標準測試向量
        val input = "hello".encodeToByteArray()
        val expected = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
        
        val result = Crypto.sha256(input)
        assertEquals(expected, result.toHex())
    }
    
    // Helper functions
    private fun String.hexToByteArray(): ByteArray {
        require(length % 2 == 0) { "Hex string must have even length" }
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
    
    private fun ByteArray.toHex(): String {
        return joinToString("") { byte ->
            (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
        }
    }
}

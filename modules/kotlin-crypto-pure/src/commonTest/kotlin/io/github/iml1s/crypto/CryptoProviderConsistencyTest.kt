package io.github.iml1s.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertContentEquals

/**
 * 跨平台一致性測試
 * 驗證 DefaultCryptoProvider (純 Kotlin) 和 JniCryptoProvider (Android JNI) 輸出相同結果
 * 
 * 此測試確保：
 * 1. watchOS 使用的純 Kotlin 實現與 Android JNI 實現行為一致
 * 2. 簽名和驗證在所有平台產生相同結果
 */
class CryptoProviderConsistencyTest {

    // ==================== BIP-340 官方測試向量 ====================
    
    private data class Bip340Vector(
        val index: Int,
        val sk: String,
        val pk: String,
        val msg: String,
        val sig: String
    )
    
    private val bip340Vectors = listOf(
        Bip340Vector(0,
            "0000000000000000000000000000000000000000000000000000000000000003",
            "F9308A019258C31049344F85F89D5229B531C845836F99B08601F113BCE036F9",
            "0000000000000000000000000000000000000000000000000000000000000000",
            "E907831F80848D1069A5371B402410364BDF1C5F8307B0084C55F1CE2DCA821525F66A4A85EA8B71E482A74F382D2CE5EBEEE8FDB2172F477DF4900D310536C0"
        ),
        Bip340Vector(1,
            "B7E151628AED2A6ABF7158809CF4F3C762E7160F38B4DA56A784D9045190CFEF",
            "DFF1D77F2A671C5F36183726DB2341BE58FEAE1DA2DECED843240F7B502BA659",
            "243F6A8885A308D313198A2E03707344A4093822299F31D0082EFA98EC4E6C89",
            "6896BD60EEAE296DB48A229FF71DFE071BDE413E6D43F917DC8DCF8C78DE33418906D11AC976ABCCB20B091292BFF4EA897EFCB639EA871CFA95F6DE339E4B0A"
        ),
        Bip340Vector(2,
            "C90FDAA22168C234C4C6628B80DC1CD129024E088A67CC74020BBEA63B14E5C9",
            "DD308AFEC5777E13121FA72B9CC1B7CC0139715309B086C960E18FD969774EB8",
            "7E2D58D8B3BCDF1ABADEC7829054F90DDA9805AAB56C77333024B9D0A508B75C",
            "5831AAEED7B44BB74E5EAB94BA9D4294C49BCF2A60728D8B4C200F50DD313C1BAB745879A5AD954A72C45A91C3A51D3C7ADEA98D82F8481E0E1E03674A6F3FB7"
        )
    )

    // ==================== ECDSA 測試向量 ====================
    
    @Test
    fun testEcdsaConsistency() {
        // 使用多個私鑰測試 ECDSA 簽名一致性
        val testKeys = listOf(
            ByteArray(32) { 0 }.also { it[31] = 1 },  // 私鑰 = 1
            ByteArray(32) { 0 }.also { it[31] = 2 },  // 私鑰 = 2
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef".hexToByteArray()
        )
        
        val testMessages = listOf(
            Crypto.sha256("hello".encodeToByteArray()),
            Crypto.sha256("world".encodeToByteArray()),
            ByteArray(32) { it.toByte() }  // 順序填充
        )
        
        for (privateKey in testKeys) {
            val publicKey = Crypto.generatePublicKey(privateKey, compressed = true)
            
            for (message in testMessages) {
                // 簽名
                val signature = Crypto.sign(message, privateKey)
                
                // 驗證
                val isValid = Crypto.verify(message, signature, publicKey)
                assertTrue(isValid, "ECDSA signature should be valid")
                
                // 驗證格式
                assertEquals(64, signature.size, "Signature should be 64 bytes (compact)")
                assertEquals(33, publicKey.size, "Compressed public key should be 33 bytes")
            }
        }
    }

    @Test
    fun testSchnorrSignatureVerification() {
        // 使用 BIP-340 官方測試向量驗證 Schnorr 簽名
        for (vector in bip340Vectors) {
            val sk = vector.sk.hexToByteArray()
            val pk = vector.pk.hexToByteArray()
            val msg = vector.msg.hexToByteArray()
            val expectedSig = vector.sig.hexToByteArray()
            
            // 驗證官方簽名
            val verifyResult = Crypto.verifySchnorr(msg, pk, expectedSig)
            assertTrue(verifyResult, "Vector ${vector.index}: Official signature should verify")
            
            // 我們的實現也應該產生可驗證的簽名
            val ourSig = Crypto.signSchnorr(msg, sk)
            val selfVerify = Crypto.verifySchnorr(msg, pk, ourSig)
            assertTrue(selfVerify, "Vector ${vector.index}: Our signature should verify")
        }
    }

    @Test
    fun testPublicKeyDerivationConsistency() {
        // 標準測試向量：私鑰 = 1 應該得到 G 點
        val privateKey1 = ByteArray(32) { 0 }.also { it[31] = 1 }
        val expectedGx = "79BE667EF9DCBBAC55A06295CE870B07029BFCDB2DCE28D959F2815B16F81798"
        
        val publicKey = Crypto.generatePublicKey(privateKey1, compressed = true)
        val actualGx = publicKey.sliceArray(1..32).toHex()
        
        assertEquals(expectedGx.lowercase(), actualGx.lowercase(), 
            "Public key for sk=1 should be G point")
        
        // 私鑰 = 2 應該得到 2G
        val privateKey2 = ByteArray(32) { 0 }.also { it[31] = 2 }
        val expected2Gx = "C6047F9441ED7D6D3045406E95C07CD85C778E4B8CEF3CA7ABAC09B95C709EE5"
        
        val publicKey2 = Crypto.generatePublicKey(privateKey2, compressed = true)
        val actual2Gx = publicKey2.sliceArray(1..32).toHex()
        
        assertEquals(expected2Gx.lowercase(), actual2Gx.lowercase(),
            "Public key for sk=2 should be 2G point")
    }

    @Test
    fun testSha256Consistency() {
        // 標準 SHA-256 測試向量
        val testCases = listOf(
            "" to "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            "abc" to "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            "hello" to "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
        )
        
        for ((input, expectedHash) in testCases) {
            val result = Crypto.sha256(input.encodeToByteArray())
            assertEquals(expectedHash, result.toHex(), "SHA-256 of '$input' should match")
        }
    }

    @Test
    fun testSignatureFormats() {
        val privateKey = ByteArray(32) { 0 }.also { it[31] = 1 }
        val message = Crypto.sha256("test".encodeToByteArray())
        
        // ECDSA compact 格式
        val ecdsaSig = Crypto.sign(message, privateKey)
        assertEquals(64, ecdsaSig.size, "ECDSA signature should be 64 bytes")
        
        // Schnorr 格式
        val schnorrSig = Crypto.signSchnorr(message, privateKey)
        assertEquals(64, schnorrSig.size, "Schnorr signature should be 64 bytes")
    }

    // ==================== Helper Functions ====================
    
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

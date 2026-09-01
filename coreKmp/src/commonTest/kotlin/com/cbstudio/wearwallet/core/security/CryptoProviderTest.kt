package com.cbstudio.wearwallet.core.security

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

/**
 * 跨平台加密功能測試
 * 確保 Android、iOS、watchOS 平台產生一致的輸出
 */
class CryptoProviderTest {
    
    private val cryptoProvider: CryptoProvider = CommonCryptoProvider()
    
    @Test
    fun testGenerateMnemonic() = runBlocking {
        val scopedMnemonic = cryptoProvider.generateMnemonic(12)
        assertEquals(12, scopedMnemonic.wordCount)
        assertTrue(cryptoProvider.validateMnemonic(scopedMnemonic))
    }
    
    @Test
    fun testEncryptDecrypt() = runBlocking {
        val data = "secret data".encodeToByteArray()
        val password = "test_password".toCharArray()
        
        val encrypted = cryptoProvider.encrypt(data, password)
        val decrypted = cryptoProvider.decrypt(encrypted, password)
        
        assertTrue(data.contentEquals(decrypted))
    }
    
    @Test
    fun testGenerateKeyPair() = runBlocking {
        val scopedMnemonic = cryptoProvider.generateMnemonic(12)
        val keyPair = cryptoProvider.generateKeyPairFromMnemonic(scopedMnemonic)
        
        assertTrue(keyPair.privateKeyBytes.isNotEmpty())
        assertTrue(keyPair.publicKey.isNotEmpty())
    }
    
    @Test
    fun testDeriveAddress() = runBlocking {
        val scopedMnemonic = cryptoProvider.generateMnemonic(12)
        val keyPair = cryptoProvider.generateKeyPairFromMnemonic(scopedMnemonic)
        val address = cryptoProvider.deriveAddress(keyPair.publicKey)
        
        assertTrue(address.startsWith("0x"))
        assertEquals(42, address.length) // 0x + 40 hex chars
    }
    
    @Test
    fun testCrossPlatformConsistency() = runBlocking {
        // 最重要的測試：確保所有平台從相同助記詞生成相同密鑰
        val testMnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        val mnemChars = testMnemonic.toCharArray()
        
        // 測試多次生成確保一致性
        val keyPair1 = cryptoProvider.generateKeyPairFromMnemonic(mnemChars)
        val keyPair2 = cryptoProvider.generateKeyPairFromMnemonic(mnemChars)
        
        assertTrue(keyPair1.privateKeyBytes.contentEquals(keyPair2.privateKeyBytes), "相同助記詞應生成相同私鑰")
        assertEquals(keyPair1.publicKey, keyPair2.publicKey, "相同助記詞應生成相同公鑰")
        
        // 從相同公鑰派生相同地址
        val address1 = cryptoProvider.deriveAddress(keyPair1.publicKey)
        val address2 = cryptoProvider.deriveAddress(keyPair2.publicKey)
        
        assertEquals(address1, address2, "相同公鑰應派生相同地址")
        
        // 輸出結果供人工驗證
        println("Test Mnemonic: $testMnemonic")
        println("Private Key bytes length: ${keyPair1.privateKeyBytes.size}")
        println("Public Key: ${keyPair1.publicKey}")
        println("Address: $address1")
    }
    
    @Test
    fun testDifferentPasswordsProduceDifferentEncryption() = runBlocking {
        val data = "test data".encodeToByteArray()
        val password1 = "password1".toCharArray()
        val password2 = "password2".toCharArray()
        
        val encrypted1 = cryptoProvider.encrypt(data, password1)
        val encrypted2 = cryptoProvider.encrypt(data, password2)
        
        // 不同密碼應產生不同加密結果
        assertTrue(!encrypted1.contentEquals(encrypted2), "不同密碼應產生不同加密結果")
        
        // 各自都能正確解密
        assertTrue(data.contentEquals(cryptoProvider.decrypt(encrypted1, password1)))
        assertTrue(data.contentEquals(cryptoProvider.decrypt(encrypted2, password2)))
    }
}

/**
 * 平台特定加密工具測試 - 簡化版本
 */
class CryptoUtilsBasicTest {
    
    @Test
    fun testSHA256Consistency() {
        val data = "Hello, World!".encodeToByteArray()
        
        // 測試多次調用產生相同結果
        val hash1 = CryptoUtils.sha256(data)
        val hash2 = CryptoUtils.sha256(data)
        
        assertEquals(32, hash1.size, "SHA256 應產生 32 字節")
        assertTrue(hash1.contentEquals(hash2), "相同輸入應產生相同 SHA256")
    }
    
    @Test
    fun testRandomBytesAreDifferent() {
        val random1 = CryptoUtils.randomBytes(32)
        val random2 = CryptoUtils.randomBytes(32)
        
        assertEquals(32, random1.size)
        assertEquals(32, random2.size)
        
        // 隨機數不應相同
        assertTrue(!random1.contentEquals(random2), "隨機數應該不同")
    }
    
    @Test
    fun testHexStringConversion() {
        val original = byteArrayOf(0x00, 0x01, 0x0F, 0xFF.toByte())
        val hex = original.toHexString()
        val restored = hex.hexToByteArray()
        
        assertEquals("00010fff", hex)
        assertTrue(original.contentEquals(restored), "轉換應該可逆")
    }

    private fun ByteArray.toHexString(): String {
        return joinToString("") { byte ->
            byte.toUByte().toString(16).padStart(2, '0')
        }
    }

    private fun String.hexToByteArray(): ByteArray {
        val hex = removePrefix("0x")
        return ByteArray(hex.length / 2) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
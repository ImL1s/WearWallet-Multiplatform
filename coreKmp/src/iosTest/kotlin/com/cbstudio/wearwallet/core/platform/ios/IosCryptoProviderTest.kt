package com.cbstudio.wearwallet.core.platform.ios

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class IosCryptoProviderTest {
    
    private val cryptoProvider = IosCryptoProvider()
    
    @Test
    fun testGenerateMnemonic() = runBlocking {
        val mnemonic = cryptoProvider.generateMnemonic(12)
        val words = mnemonic.split(" ")
        assertEquals(12, words.size, "應該生成12個助記詞")
    }
    
    @Test
    fun testValidateMnemonic() = runBlocking {
        val validMnemonic = cryptoProvider.generateMnemonic(12)
        assertTrue(cryptoProvider.validateMnemonic(validMnemonic), "應該驗證有效的助記詞")
    }
    
    @Test
    fun testEncryptDecrypt() = runBlocking {
        val originalData = "test data"
        val password = "test password"
        
        val encrypted = cryptoProvider.encrypt(originalData, password)
        val decrypted = cryptoProvider.decrypt(encrypted, password)
        
        assertEquals(originalData, decrypted, "解密後的數據應該與原始數據相同")
    }
    
    @Test
    fun testGenerateKeyPair() = runBlocking {
        val mnemonic = cryptoProvider.generateMnemonic(12)
        val keyPair = cryptoProvider.generateKeyPairFromMnemonic(mnemonic)
        
        assertTrue(keyPair.publicKey.isNotEmpty(), "公鑰不應為空")
        assertTrue(keyPair.privateKey.isNotEmpty(), "私鑰不應為空")
    }
    
    @Test
    fun testDeriveAddress() = runBlocking {
        val mnemonic = cryptoProvider.generateMnemonic(12)
        val keyPair = cryptoProvider.generateKeyPairFromMnemonic(mnemonic)
        val address = cryptoProvider.deriveAddress(keyPair.publicKey)
        
        assertTrue(address.startsWith("0x"), "地址應該以0x開頭")
        assertEquals(42, address.length, "地址長度應該是42個字符（包括0x）")
    }
}
package com.cbstudio.wearwallet.core.security

import com.cbstudio.wearwallet.core.domain.model.ChainType
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NativeCryptoTest {

    @AfterTest
    fun tearDown() {
    }

    @Test
    fun testSingletonInitialization() {
        val mockDelegate = object : NativeCryptoDelegate {
            override fun deriveAddressFromXpub(xpub: String, derivationPath: String, isTestnet: Boolean, policy: ExtendedPublicKeyPolicy?): String = "mock_address"
            override fun generateKeyPair(mnemonic: String, derivationPath: String, chainType: ChainType): KeyPair = 
                KeyPair("pub", byteArrayOf(1, 2, 3))
            override fun signTransaction(data: ByteArray, privateKey: String): ByteArray = byteArrayOf(1, 2, 3)
            override fun encodeUR(data: ByteArray, type: String, maxFragmentSize: Int): List<String> = listOf("ur:mock")
            override fun decodeUR(urString: String): ByteArray = byteArrayOf(4, 5, 6)
            override fun combineUR(parts: List<String>): ByteArray = byteArrayOf(7, 8, 9)
        }

        NativeCrypto.setDelegate(mockDelegate)
        
        assertTrue(NativeCrypto.isAvailable())
        assertNotNull(NativeCrypto.delegate)
        assertEquals(mockDelegate, NativeCrypto.delegate)
    }

    @Test
    fun testDelegateUsage() {
        val mockDelegate = object : NativeCryptoDelegate {
            override fun deriveAddressFromXpub(xpub: String, derivationPath: String, isTestnet: Boolean, policy: ExtendedPublicKeyPolicy?): String = "derived_$xpub"
            override fun generateKeyPair(mnemonic: String, derivationPath: String, chainType: ChainType): KeyPair = KeyPair("pub", byteArrayOf(1, 2, 3))
            override fun signTransaction(data: ByteArray, privateKey: String): ByteArray = ByteArray(0)
            override fun encodeUR(data: ByteArray, type: String, maxFragmentSize: Int): List<String> = listOf()
            override fun decodeUR(urString: String): ByteArray = ByteArray(0)
            override fun combineUR(parts: List<String>): ByteArray = ByteArray(0)
        }
        
        NativeCrypto.setDelegate(mockDelegate)
        
        val result = NativeCrypto.delegate.deriveAddressFromXpub("xpub123", "m/44/60")
        assertEquals("derived_xpub123", result)
    }
}

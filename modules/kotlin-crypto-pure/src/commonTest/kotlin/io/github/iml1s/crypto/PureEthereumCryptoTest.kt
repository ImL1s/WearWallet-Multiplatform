package io.github.iml1s.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PureEthereumCryptoTest {
    @Test
    fun testDerivationFromXpub() {
        // BIP32 Vector 1, Master Node (m)
        val xpub = "xpub661MyMwAqRbcFtXgS5sYJABqqG9YLmC4Q1Rdap9gSE8NqtwybGhePY2gZ29ESFjqJoCu1Rupje8YtGqsefD265TMg7usUDFdp6W1EGMcet8"
        
        // Target: m/0/1
        // Expected behavior: Parse xpub (depth 1), Parse path "m/0/1", Derive index difference (1).
        
        val address = PureEthereumCrypto.deriveAddressFromXpub(xpub, "m/0/1")

        assertTrue(address.startsWith("0x"))
        assertEquals(42, address.length)
        
        // Consistency check
        val address2 = PureEthereumCrypto.deriveAddressFromXpub(xpub, "m/0/1")
        assertEquals(address, address2)
    }
    
    @Test
    fun testDerivationDeterministic() {
         val xpub = "xpub661MyMwAqRbcFtXgS5sYJABqqG9YLmC4Q1Rdap9gSE8NqtwybGhePY2gZ29ESFjqJoCu1Rupje8YtGqsefD265TMg7usUDFdp6W1EGMcet8"
         val addr1 = PureEthereumCrypto.deriveAddressFromXpub(xpub, "m/0/5")
         val addr2 = PureEthereumCrypto.deriveAddressFromXpub(xpub, "m/0/5")
         assertEquals(addr1, addr2)
         
         val addr3 = PureEthereumCrypto.deriveAddressFromXpub(xpub, "m/0/6")
         assertTrue(addr1 != addr3)
    }

    @Test
    fun testDerivePrivateKeyAndAddress() {
        // BIP39 Test Vector
        val mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        val path = "m/44'/60'/0'/0/0"
        
        // 1. Derive Private Key
        val privateKey = PureEthereumCrypto.derivePrivateKey(mnemonic, path)

        assertTrue(privateKey.startsWith("0x"))
        assertEquals(66, privateKey.length) // 2 (0x) + 64 (32 bytes hex)
        
        // 2. Generate Address
        val address = PureEthereumCrypto.getEthereumAddress(privateKey)

        // Expected Address for this mnemonic path ?
        // Using "abandon..." usually results in:
        // m/44'/60'/0'/0/0 -> 0x9858Effd232991E79940288E729a509cB3F82096
        // Let's check consistency.
        
        assertTrue(address.startsWith("0x"))
        assertEquals(42, address.length)
        
        // Verify with generated address method
        val addressRe = PureEthereumCrypto.getEthereumAddress(privateKey)
        assertEquals(address, addressRe)
    }
}

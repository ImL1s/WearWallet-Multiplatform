package com.cbstudio.wearwallet.core.security

import io.github.iml1s.crypto.PureEthereumCrypto
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class Bip39TestVectorsTest {

    private val EXPECTED_KEY_12 = "0x1ab42cc412b618bdea3a599e3c9bae199ebf030895b039e9db1e30dafb12b727"
    private val EXPECTED_ADDR_12 = "0x9858EfFD232B4033E47d90003D41EC34EcaEda94"

    private val EXPECTED_KEY_15 = "0x89fe6cf31a686f718017d664b08f75a51b706edc85edd6c67b1d2b56da628964"

    private val EXPECTED_KEY_18 = "0x7baa95e968e65395b2b4cc341885bcfbc0d820571af180c65cc9c5019551c669"

    private val EXPECTED_KEY_21 = "0x01a17b608fed85bf7a3ce6ffc0f8f9edd862bda09d1395eb0335edb336d9acae"

    private val EXPECTED_KEY_24 = "0xff25e57518abf6647749e5ebffbd8ab4382519f5f7a7d82db5365b18e464f4df"
    private val EXPECTED_ADDR_24 = "0x2f826cb22E80a2c40f149Ecb92b2Fa5ecBf67170"

    @Test
    fun testBip39OfficialVectors12WordsEntropyToMnemonicToKey() {
        runBlocking {
            val provider: CryptoProvider = CommonCryptoProvider()
            val mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
            val isValid = provider.validateMnemonic(mnemonic.toCharArray())
            assertTrue(isValid, "Official 12-word BIP39 vector should validate")

            val derivedKey = PureEthereumCrypto.derivePrivateKey(mnemonic, "m/44'/60'/0'/0/0")
            val derivedAddress = PureEthereumCrypto.getEthereumAddress(derivedKey)
            assertTrue(derivedKey.startsWith("0x"), "Derived private key must start with 0x")
            assertEquals(66, derivedKey.length, "Derived private key hex length must be 66")
            assertEquals(EXPECTED_KEY_12.lowercase(), derivedKey.lowercase())

            assertTrue(derivedAddress.startsWith("0x"), "Derived address must start with 0x")
            assertEquals(42, derivedAddress.length, "Ethereum address hex length must be 42")
            assertEquals(EXPECTED_ADDR_12.lowercase(), derivedAddress.lowercase())
        }
    }

    @Test
    fun testBip39OfficialVectors15WordsEntropyToMnemonicToKey() {
        runBlocking {
            val provider: CryptoProvider = CommonCryptoProvider()
            val mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon address"
            val isValid = provider.validateMnemonic(mnemonic.toCharArray())
            assertTrue(isValid, "Official 15-word BIP39 vector should validate")

            val derivedKey = PureEthereumCrypto.derivePrivateKey(mnemonic, "m/44'/60'/0'/0/0")
            val derivedAddress = PureEthereumCrypto.getEthereumAddress(derivedKey)
            assertTrue(derivedKey.startsWith("0x"))
            assertEquals(66, derivedKey.length)
            assertEquals(EXPECTED_KEY_15.lowercase(), derivedKey.lowercase())

            assertTrue(derivedAddress.startsWith("0x"))
            assertEquals(42, derivedAddress.length)
        }
    }

    @Test
    fun testBip39OfficialVectors18WordsEntropyToMnemonicToKey() {
        runBlocking {
            val provider: CryptoProvider = CommonCryptoProvider()
            val mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon agent"
            val isValid = provider.validateMnemonic(mnemonic.toCharArray())
            assertTrue(isValid, "Official 18-word BIP39 vector should validate")

            val derivedKey = PureEthereumCrypto.derivePrivateKey(mnemonic, "m/44'/60'/0'/0/0")
            val derivedAddress = PureEthereumCrypto.getEthereumAddress(derivedKey)
            assertTrue(derivedKey.startsWith("0x"))
            assertEquals(66, derivedKey.length)
            assertEquals(EXPECTED_KEY_18.lowercase(), derivedKey.lowercase())

            assertTrue(derivedAddress.startsWith("0x"))
            assertEquals(42, derivedAddress.length)
        }
    }

    @Test
    fun testBip39OfficialVectors21WordsEntropyToMnemonicToKey() {
        runBlocking {
            val provider: CryptoProvider = CommonCryptoProvider()
            val mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon admit"
            val isValid = provider.validateMnemonic(mnemonic.toCharArray())
            assertTrue(isValid, "Official 21-word BIP39 vector should validate")

            val derivedKey = PureEthereumCrypto.derivePrivateKey(mnemonic, "m/44'/60'/0'/0/0")
            val derivedAddress = PureEthereumCrypto.getEthereumAddress(derivedKey)
            assertTrue(derivedKey.startsWith("0x"))
            assertEquals(66, derivedKey.length)
            assertEquals(EXPECTED_KEY_21.lowercase(), derivedKey.lowercase())

            assertTrue(derivedAddress.startsWith("0x"))
            assertEquals(42, derivedAddress.length)
        }
    }

    @Test
    fun testBip39OfficialVectors24WordsEntropyToMnemonicToKey() {
        runBlocking {
            val provider: CryptoProvider = CommonCryptoProvider()
            val mnemonic = "legal winner thank year wave sausage worth useful legal winner thank year wave sausage worth useful legal winner thank year wave sausage worth title"
            val isValid = provider.validateMnemonic(mnemonic.toCharArray())
            assertTrue(isValid, "Official 24-word BIP39 vector should validate")

            val derivedKey = PureEthereumCrypto.derivePrivateKey(mnemonic, "m/44'/60'/0'/0/0")
            val derivedAddress = PureEthereumCrypto.getEthereumAddress(derivedKey)
            assertTrue(derivedKey.startsWith("0x"))
            assertEquals(66, derivedKey.length)
            assertEquals(EXPECTED_KEY_24.lowercase(), derivedKey.lowercase())

            assertTrue(derivedAddress.startsWith("0x"))
            assertEquals(42, derivedAddress.length)
            assertEquals(EXPECTED_ADDR_24.lowercase(), derivedAddress.lowercase())
        }
    }

    @Test
    fun testGenerateMnemonicSupportedWordCounts12To24() {
        runBlocking {
            val provider: CryptoProvider = CommonCryptoProvider()
            val wordCounts = listOf(12, 15, 18, 21, 24)
            for (count in wordCounts) {
                val scopedMnemonic = provider.generateMnemonic(count)
                assertEquals(count, scopedMnemonic.wordCount, "Generated mnemonic should have exactly $count words")

                val chars = scopedMnemonic.copyChars()
                try {
                    assertTrue(provider.validateMnemonic(chars), "Generated $count-word mnemonic should validate")
                    val key = PureEthereumCrypto.derivePrivateKey(String(chars), "m/44'/60'/0'/0/0")
                    assertTrue(key.startsWith("0x"))
                } finally {
                    chars.fill('\u0000')
                    scopedMnemonic.close()
                }
            }
        }
    }

    @Test
    fun testGenerateMnemonicUnsupportedWordCountFailsClosed() {
        runBlocking {
            val provider: CryptoProvider = CommonCryptoProvider()
            assertFailsWith<IllegalArgumentException> {
                provider.generateMnemonic(13)
            }
        }
    }
}

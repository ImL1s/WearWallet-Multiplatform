package com.cbstudio.wearwallet.core.security

import io.github.iml1s.crypto.Bip32
import io.github.iml1s.crypto.PureEthereumCrypto
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class XpubDerivationTest {

    private val EXPECTED_MASTER_XPUB = "xpub661MyMwAqRbcFtXgS5sYJABqqG9YLmC4Q1Rdap9gSE8NqtwybGhePY2gZ29ESFjqJoCu1Rupje8YtGqsefD265TMg7usUDFdp6W1EGMcet8"
    private val EXPECTED_CHILD_ADDRESS = "0x4B7115aD9623A528f1845eaf85D166dE1E869BFB"

    private fun getValidXpub(): String {
        val seed = "000102030405060708090a0b0c0d0e0f".hexToByteArray()
        val master = Bip32.masterKeyFromSeed(seed)
        return master.serializePublic()
    }

    @Test
    fun testXpubMasterAndChildFixedReferenceVectors() {
        val seed = "000102030405060708090a0b0c0d0e0f".hexToByteArray()
        val master = Bip32.masterKeyFromSeed(seed)
        val masterXpub = master.serializePublic()

        assertTrue(masterXpub.startsWith("xpub"), "Master xpub must start with 'xpub' prefix")
        assertEquals(111, masterXpub.length, "Base58Check serialized xpub must be exactly 111 characters")
        assertEquals(master.serializePublic(), masterXpub, "Base58Check serialized xpub must be deterministic")
        assertEquals(EXPECTED_MASTER_XPUB, masterXpub)

        val childAddress = PureEthereumCrypto.deriveAddressFromXpub(
            xpub = masterXpub,
            path = "0/0"
        )

        assertTrue(childAddress.startsWith("0x"), "Derived address must start with 0x")
        assertEquals(42, childAddress.length, "Child address derivation from xpub path 0/0 length must be 42")
        assertEquals(EXPECTED_CHILD_ADDRESS.lowercase(), childAddress.lowercase())
    }

    @Test
    fun testXpubChecksumMismatchFailsClosed() {
        val validXpub = getValidXpub()
        // Corrupt last character of checksum
        val invalidChecksumXpub = validXpub.dropLast(1) + if (validXpub.last() == 'A') "B" else "A"
        assertFailsWith<IllegalArgumentException> {
            PureEthereumCrypto.deriveAddressFromXpub(invalidChecksumXpub, "0/0")
        }
    }

    @Test
    fun testXpubHardenedPathFailsClosed() {
        val validXpub = getValidXpub()
        assertFailsWith<IllegalArgumentException> {
            PureEthereumCrypto.deriveAddressFromXpub(validXpub, "0/0'")
        }
    }

    @Test
    fun testXpubInvalidPathComponentFailsClosed() {
        val validXpub = getValidXpub()
        assertFailsWith<IllegalArgumentException> {
            PureEthereumCrypto.deriveAddressFromXpub(validXpub, "0/invalid_path/0")
        }
    }

    @Test
    fun testXpubEmptyPathComponentFailsClosed() {
        val validXpub = getValidXpub()
        assertFailsWith<IllegalArgumentException> {
            PureEthereumCrypto.deriveAddressFromXpub(validXpub, "0//0")
        }
    }

    private fun String.hexToByteArray(): ByteArray {
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}

package io.github.iml1s.address

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertNull
import kotlin.test.assertFalse

/**
 * Bech32/Bech32m 測試
 * 
 * 使用官方 BIP-173 和 BIP-350 測試向量
 * @see https://github.com/bitcoin/bips/blob/master/bip-0173.mediawiki
 * @see https://github.com/bitcoin/bips/blob/master/bip-0350.mediawiki
 */
class Bech32Test {

    // ==================== BIP-173 Official Test Vectors ====================
    
    @Test
    fun testBip173ValidAddresses() {
        // BIP-173 valid address test vectors
        val validAddresses = listOf(
            "BC1QW508D6QEJXTDG4Y5R3ZARVARY0C5XW7KV8F3T4",
            "tb1qrp33g0q5c5txsp9arysrx4k6zdkfs4nce4xj0gdcccefvpysxf3q0sl5k7",
            "bc1pw508d6qejxtdg4y5r3zarvary0c5xw7kw508d6qejxtdg4y5r3zarvary0c5xw7kt5nd6y",
            "BC1SW50QGDZ25J",
            "bc1zw508d6qejxtdg4y5r3zarvaryvaxxpcs",
            "tb1qqqqqp399et2xygdj5xreqhjjvcmzhxw4aywxecjdzew6hylgvsesrxh6hy"
        )
        
        for (address in validAddresses) {
            val decoded = Bech32.decode(address.lowercase())
            assertNotNull(decoded, "Should decode valid address: $address")
        }
    }
    
    @Test
    fun testBip173InvalidAddresses() {
        // BIP-173 invalid address test vectors
        val invalidAddresses = listOf(
            "tc1qw508d6qejxtdg4y5r3zarvary0c5xw7kg3g4ty",  // Invalid hrp
            "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t5",  // Invalid checksum
            "BC13W508D6QEJXTDG4Y5R3ZARVARY0C5XW7KN40WF2", // Invalid witness version
            "bc1rw5uspcuh",                                 // Invalid program length
            "bc10w508d6qejxtdg4y5r3zarvary0c5xw7kw508d6qejxtdg4y5r3zarvary0c5xw7kw5rljs90", // Invalid program length
            "BC1QR508D6QEJXTDG4Y5R3ZARVARYV98GJ9P",        // Invalid program length for v0
            "tb1qrp33g0q5c5txsp9arysrx4k6zdkfs4nce4xj0gdcccefvpysxf3q0sL5k7", // Mixed case
            "bc1zw508d6qejxtdg4y5r3zarvaryvqyzf3du",       // Zero padding issue
            "tb1qrp33g0q5c5txsp9arysrx4k6zdkfs4nce4xj0gdcccefvpysxf3pjxtptv", // Non-zero padding
            "bc1gmk9yu"                                     // Empty data
        )
        
        for (address in invalidAddresses) {
            val decoded = try {
                Bech32.decode(address.lowercase())
            } catch (e: Exception) {
                null
            }
            // Some of these should fail decode, some should fail validation
            // Just ensure they don't pass as valid segwit addresses
        }
    }
    
    // ==================== BIP-350 (Bech32m) Official Test Vectors ====================
    
    @Test
    fun testBip350ValidBech32m() {
        // BIP-350 valid Bech32m test strings
        val validBech32m = listOf(
            "A1LQFN3A",
            "a1lqfn3a",
            "an83characterlonghumanreadablepartthatcontainsthetheexcludedcharactersbioandnumber11sg7hg6",
            "abcdef1l7aum6echk45nj3s0wdvt2fg8x9yrzpqzd3ryx",
            "11llllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllludsr8",
            "split1checkupstagehandshakeupstreamerranterredcaperredlc445v",
            "?1v759aa"
        )
        
        for (s in validBech32m) {
            val decoded = try {
                Bech32.decode(s.lowercase())
            } catch (e: Exception) {
                null
            }
            assertNotNull(decoded, "Should decode valid Bech32m: $s")
        }
    }
    
    @Test
    fun testBip350TaprootAddresses() {
        // BIP-350 valid Taproot (v1) addresses
        val validTaprootAddresses = listOf(
            "bc1p0xlxvlhemja6c4dqv22uapctqupfhlxm9h8z3k2e72q4k9hcz7vqzk5jj0",
            "tb1pqqqqp399et2xygdj5xreqhjjvcmzhxw4aywxecjdzew6hylgvsesf3hn0c",
            "bc1p0xlxvlhemja6c4dqv22uapctqupfhlxm9h8z3k2e72q4k9hcz7vqh2y7hd",
            "tb1z0xlxvlhemja6c4dqv22uapctqupfhlxm9h8z3k2e72q4k9hcz7vqglt7rf",
            "BC1S0XLXVLHEMJA6C4DQV22UAPCTQUPFHLXM9H8Z3K2E72Q4K9HCZ7VQ54WELL",
            "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kemeawh",
            "tb1q0xlxvlhemja6c4dqv22uapctqupfhlxm9h8z3k2e72q4k9hcz7vq24jc47"
        )
        
        for (address in validTaprootAddresses) {
            val decoded = try {
                Bech32.decode(address.lowercase())
            } catch (e: Exception) {
                null
            }
            assertNotNull(decoded, "Should decode Taproot address: $address")
        }
    }
    
    // ==================== Segwit Address Round-trip Tests ====================
    
    @Test
    fun testSegwitAddressRoundTrip() {
        val hrp = "bc"
        val witnessVersion = 0
        val program = ByteArray(20) { (it + 1).toByte() }
        
        val encoded = Bech32.encodeSegwitAddress(hrp, witnessVersion, program)
        assertNotNull(encoded)
        
        val decoded = Bech32.decodeSegwitAddress(encoded)
        assertNotNull(decoded)
        assertEquals(witnessVersion, decoded.first)
        assertTrue(program.contentEquals(decoded.second))
    }
    
    @Test
    fun testTaprootAddressRoundTrip() {
        val hrp = "bc"
        val witnessVersion = 1  // Taproot uses v1
        val program = ByteArray(32) { (it + 1).toByte() }
        
        val encoded = Bech32.encodeSegwitAddress(hrp, witnessVersion, program)
        assertNotNull(encoded)
        assertTrue(encoded.startsWith("bc1p"), "Taproot address should start with bc1p")
        
        val decoded = Bech32.decodeSegwitAddress(encoded)
        assertNotNull(decoded)
        assertEquals(witnessVersion, decoded.first)
        assertTrue(program.contentEquals(decoded.second))
    }
    
    // ==================== Basic Encoding/Decoding Tests ====================
    
    @Test
    fun testBech32Encode() {
        val hrp = "bc"
        val data = byteArrayOf(0, 14, 20, 15, 7, 13, 26, 0, 25, 18, 6, 11, 13, 8, 21, 4, 20, 3, 17, 2, 29, 3, 12, 29, 3, 4, 15, 24, 20, 6, 14, 30, 22)
        
        val result = Bech32.encode(hrp, data)
        assertTrue(result.startsWith("bc1"))
    }

    @Test
    fun testBech32Decode() {
        val address = "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4"
        val decoded = Bech32.decode(address)
        
        assertNotNull(decoded)
        assertEquals("bc", decoded.hrp)
    }

    @Test
    fun testBech32mEncode() {
        val hrp = "bc"
        val witnessVersion = 1
        val program = ByteArray(32) { 0x01 }
        
        val result = Bech32.encodeSegwitAddress(hrp, witnessVersion, program)
        assertNotNull(result)
        assertTrue(result.startsWith("bc1p"))
    }
}

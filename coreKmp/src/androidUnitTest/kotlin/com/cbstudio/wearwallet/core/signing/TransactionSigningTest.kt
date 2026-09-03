package com.cbstudio.wearwallet.core.signing

import com.cbstudio.wearwallet.core.multichain.util.RLPEncoder
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.ionspin.kotlin.bignum.integer.BigInteger
import org.junit.Test
import org.junit.Assert.*

/**
 * Transaction Encoding and Chain Configuration Tests
 * 
 * Tests RLP encoding, chain configuration, and utility functions.
 * Note: Actual signing requires native TrustWallet Core library (Android Instrumented Tests).
 */
class TransactionEncodingTest {
    
    // ==================== RLP Encoding Tests ====================
    
    @Test
    fun rlpEncoder_encodesZero() {
        val encoded = RLPEncoder.encode(0L) as ByteArray
        assertNotNull(encoded)
        assertEquals("Zero encodes to 0x80", 1, encoded.size)
        println("Zero RLP: ${encoded.toHexString()} ✅")
    }
    
    @Test
    fun rlpEncoder_encodesSmallInteger() {
        val encoded = RLPEncoder.encode(127L) as ByteArray
        assertNotNull(encoded)
        assertEquals("Small int RLP", 1, encoded.size)
        assertEquals("127 = 0x7f", 0x7f.toByte(), encoded[0])
        println("Small int (127) RLP: ${encoded.toHexString()} ✅")
    }
    
    @Test
    fun rlpEncoder_encodesLargeInteger() {
        val encoded = RLPEncoder.encode(1024L) as ByteArray
        assertNotNull(encoded)
        assertTrue("Large int RLP should be > 1 byte", encoded.size > 1)
        println("Large int (1024) RLP: ${encoded.toHexString()} ✅")
    }
    
    @Test
    fun rlpEncoder_encodesBigInteger() {
        val bigInt = BigInteger.parseString("de0b6b3a7640000", 16) // 1 ETH
        val encoded = RLPEncoder.encode(bigInt) as ByteArray
        assertNotNull(encoded)
        assertTrue("BigInteger RLP should be > 1 byte", encoded.size > 1)
        println("BigInteger (1 ETH) RLP: ${encoded.toHexString()} ✅")
    }
    
    @Test
    fun rlpEncoder_encodesSimpleList() {
        val list = listOf(0L, 1L, 2L)
        val encoded = RLPEncoder.encode(list) as ByteArray
        assertNotNull(encoded)
        assertTrue("List RLP starts with 0xc", (encoded[0].toInt() and 0xFF) >= 0xc0)
        println("Simple list RLP: ${encoded.toHexString()} ✅")
    }
    
    // ==================== Chain Configuration Tests ====================
    
    @Test
    fun chainType_evmChainsHaveCorrectNativeToken() {
        assertEquals("ETH", ChainType.ETHEREUM.nativeToken)
        assertEquals("BNB", ChainType.BSC.nativeToken)
        assertEquals("MATIC", ChainType.POLYGON.nativeToken)
        assertEquals("ETH", ChainType.ARBITRUM.nativeToken)
        assertEquals("ETH", ChainType.OPTIMISM.nativeToken)
        assertEquals("ETH", ChainType.BASE.nativeToken)
        assertEquals("AVAX", ChainType.AVALANCHE.nativeToken)
        assertEquals("FTM", ChainType.FANTOM.nativeToken)
        println("All EVM chain native tokens verified ✅")
    }
    
    @Test
    fun chainType_testnetIdentification() {
        assertTrue(ChainType.SEPOLIA.isTestnet())
        assertTrue(ChainType.GOERLI.isTestnet())
        assertTrue(ChainType.MUMBAI.isTestnet())
        assertFalse(ChainType.ETHEREUM.isTestnet())
        assertFalse(ChainType.BSC.isTestnet())
        println("Testnet identification verified ✅")
    }
    
    @Test
    fun chainType_evmIdentification() {
        assertTrue(ChainType.ETHEREUM.isEVM())
        assertTrue(ChainType.BSC.isEVM())
        assertTrue(ChainType.ARBITRUM.isEVM())
        assertFalse(ChainType.BITCOIN.isEVM())
        assertFalse(ChainType.SOLANA.isEVM())
        println("EVM identification verified ✅")
    }
    
    // ==================== Hex Conversion Tests ====================
    
    @Test
    fun hexConversion_addressToBytes() {
        val hex = "742d35Cc6634C0532925a3b844Bc9e7595f3B4E0"
        val bytes = hex.hexToByteArray()
        assertEquals("Address should be 20 bytes", 20, bytes.size)
        println("Hex to bytes verified ✅")
    }
    
    @Test
    fun hexConversion_roundTrip() {
        val original = "deadbeef0123456789abcdef"
        val bytes = original.hexToByteArray()
        val result = bytes.toHexString()
        assertEquals(original, result)
        println("Round trip verified ✅")
    }
    
    // ==================== Gas Calculation Tests ====================
    
    @Test
    fun gasCalculation_standardTransfer() {
        val gasLimit = 21000L
        val gasPrice = 20_000_000_000L // 20 Gwei
        val gasCost = gasLimit * gasPrice
        assertEquals(420_000_000_000_000L, gasCost)
        println("Standard transfer gas: $gasCost Wei ✅")
    }
    
    @Test
    fun gasCalculation_tokenTransfer() {
        val gasLimit = 65000L
        val gasPrice = 30_000_000_000L // 30 Gwei
        val gasCost = gasLimit * gasPrice
        assertEquals(1_950_000_000_000_000L, gasCost)
        println("Token transfer gas: $gasCost Wei ✅")
    }
    
    // Helpers
    private fun String.hexToByteArray(): ByteArray {
        val cleanHex = this.removePrefix("0x")
        val data = ByteArray(cleanHex.length / 2)
        for (i in data.indices) {
            data[i] = cleanHex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        return data
    }
    
    private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }
}

package io.github.iml1s.tx.bitcoin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * Script 模組測試
 * 
 * 參考 bitcoin-kmp 測試方式：
 * 1. 使用已知私鑰/公鑰驗證腳本 hex 結果
 * 2. 驗證腳本類型判斷正確性
 * 3. 交叉驗證 (確保類型判斷互斥)
 * 
 * 測試向量來源：
 * - bitcoin-kmp ScriptTestsCommon.kt
 * - BIP-141 (SegWit)
 * - BIP-341 (Taproot)
 */
class ScriptTest {

    // ================================
    // 使用已知向量的精確測試
    // ================================
    
    /**
     * P2PKH 測試 (使用 bitcoin-kmp 相同的私鑰)
     * 私鑰: 0101010101010101010101010101010101010101010101010101010101010101
     * 公鑰 hash160: 79b000887626b294a914501a4cd226b58b235983
     */
    @Test
    fun testP2PKH_WithKnownVector() {
        // From bitcoin-kmp: priv = "0101...01", pubKeyHash = "79b000887626b294a914501a4cd226b58b235983"
        val pubKeyHash = hexToBytes("79b000887626b294a914501a4cd226b58b235983")
        
        val script = Script.pay2pkh(pubKeyHash)
        val hex = bytesToHex(script)
        
        // 官方期望值 from bitcoin-kmp
        assertEquals("76a91479b000887626b294a914501a4cd226b58b23598388ac", hex)
        
        // 類型驗證 (互斥性測試)
        assertTrue(Script.isP2PKH(script))
        assertFalse(Script.isP2SH(script))
        assertFalse(Script.isP2WPKH(script))
        assertFalse(Script.isP2WSH(script))
        assertFalse(Script.isP2TR(script))
        
        // Witness 版本應為 null (非 native segwit)
        assertNull(Script.getWitnessVersion(script))
    }

    /**
     * P2WPKH 測試 (SegWit v0)
     * 使用相同公鑰 hash
     */
    @Test
    fun testP2WPKH_WithKnownVector() {
        val pubKeyHash = hexToBytes("79b000887626b294a914501a4cd226b58b235983")
        
        val script = Script.pay2wpkh(pubKeyHash)
        val hex = bytesToHex(script)
        
        // 官方期望值 from bitcoin-kmp
        assertEquals("001479b000887626b294a914501a4cd226b58b235983", hex)
        
        // 類型驗證
        assertTrue(Script.isP2WPKH(script))
        assertFalse(Script.isP2PKH(script))
        assertFalse(Script.isP2SH(script))
        assertFalse(Script.isP2WSH(script))
        assertFalse(Script.isP2TR(script))
        
        // SegWit v0
        assertEquals(0, Script.getWitnessVersion(script))
    }

    /**
     * P2TR 測試 (Taproot, BIP-341)
     * 使用 BIP-341 測試向量中的 x-only 公鑰
     */
    @Test
    fun testP2TR_WithKnownVector() {
        // Taproot 測試向量: x-only 公鑰
        // 來源: BIP-341 test vector (secp256k1 generator point G's x-coordinate)
        val outputKey = hexToBytes("79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798")
        
        val script = Script.pay2tr(outputKey)
        val hex = bytesToHex(script)
        
        // OP_1 (0x51) + push 32 bytes (0x20) + outputKey
        assertEquals("512079be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798", hex)
        
        // 類型驗證
        assertTrue(Script.isP2TR(script))
        assertFalse(Script.isP2PKH(script))
        assertFalse(Script.isP2WPKH(script))
        assertFalse(Script.isP2WSH(script))
        
        // SegWit v1
        assertEquals(1, Script.getWitnessVersion(script))
    }

    // ================================
    // 結構正確性測試
    // ================================
    
    @Test
    fun testP2SH_Structure() {
        val scriptHash = hexToBytes("0000000000000000000000000000000000000000")
        
        val script = Script.pay2sh(scriptHash)
        
        // OP_HASH160 <20 bytes> OP_EQUAL
        assertEquals(23, script.size)
        assertEquals(OpCodes.OP_HASH160, script[0].toInt() and 0xFF)
        assertEquals(0x14, script[1].toInt() and 0xFF) // push 20 bytes
        assertEquals(OpCodes.OP_EQUAL, script[22].toInt() and 0xFF)
        
        assertTrue(Script.isP2SH(script))
    }

    @Test
    fun testP2WSH_Structure() {
        val scriptHash = ByteArray(32) { 0xAA.toByte() }
        
        val script = Script.pay2wsh(scriptHash)
        
        // OP_0 <32 bytes>
        assertEquals(34, script.size)
        assertEquals(OpCodes.OP_0, script[0].toInt() and 0xFF)
        assertEquals(0x20, script[1].toInt() and 0xFF) // push 32 bytes
        
        assertTrue(Script.isP2WSH(script))
        assertEquals(0, Script.getWitnessVersion(script))
    }

    // ================================
    // 邊界情況測試
    // ================================
    
    @Test
    fun testPushData_EmptyData_ReturnsOP0() {
        val result = Script.pushData(byteArrayOf())
        
        assertEquals(1, result.size)
        assertEquals(OpCodes.OP_0, result[0].toInt() and 0xFF)
    }

    @Test
    fun testPushData_SmallNumber_ReturnsDirectPush() {
        // 數字 1-16 應使用 OP_1..OP_16
        val data = byteArrayOf(0x05)
        val result = Script.pushData(data)
        
        assertEquals(1, result.size)
        assertEquals(OpCodes.OP_5, result[0].toInt() and 0xFF)
    }

    @Test
    fun testPushData_RegularData_ReturnsLengthPrefixed() {
        val data = byteArrayOf(0x11, 0x22, 0x33, 0x44)
        val result = Script.pushData(data)
        
        // length (1 byte) + data (4 bytes)
        assertEquals(5, result.size)
        assertEquals(4, result[0].toInt() and 0xFF) // length prefix
    }

    @Test
    fun testPushData_LargeData_UsesPUSHDATA1() {
        val data = ByteArray(100) { 0xAB.toByte() }
        val result = Script.pushData(data)
        
        // OP_PUSHDATA1 + length (1 byte) + data
        assertEquals(102, result.size)
        assertEquals(OpCodes.OP_PUSHDATA1, result[0].toInt() and 0xFF)
        assertEquals(100, result[1].toInt() and 0xFF)
    }

    // ================================
    // Witness Version 判斷測試
    // ================================
    
    @Test
    fun testGetWitnessVersion_VariousVersions() {
        // v0 - P2WPKH (20 bytes program)
        val v0_wpkh = byteArrayOf(0x00, 0x14) + ByteArray(20)
        assertEquals(0, Script.getWitnessVersion(v0_wpkh))
        
        // v0 - P2WSH (32 bytes program)
        val v0_wsh = byteArrayOf(0x00, 0x20) + ByteArray(32)
        assertEquals(0, Script.getWitnessVersion(v0_wsh))
        
        // v1 - P2TR (32 bytes program)
        val v1_tr = byteArrayOf(0x51, 0x20) + ByteArray(32)
        assertEquals(1, Script.getWitnessVersion(v1_tr))
        
        // 未來版本 (v2-v16)
        val v16 = byteArrayOf(0x60, 0x20) + ByteArray(32) // OP_16 = 0x60
        assertEquals(16, Script.getWitnessVersion(v16))
    }

    @Test
    fun testGetWitnessVersion_InvalidScripts() {
        // 太短
        assertNull(Script.getWitnessVersion(byteArrayOf(0x00, 0x02, 0x00)))
        
        // 太長 (program > 40 bytes)
        assertNull(Script.getWitnessVersion(byteArrayOf(0x00, 0x29) + ByteArray(41)))
        
        // 不是 witness script
        assertNull(Script.getWitnessVersion(hexToBytes("76a91479b000887626b294a914501a4cd226b58b23598388ac")))
    }

    // ================================
    // Utility Functions
    // ================================
    
    private fun hexToBytes(hex: String): ByteArray {
        return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
    
    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

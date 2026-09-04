package com.cbstudio.wearwallet.core.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import com.cbstudio.wearwallet.core.security.toHexString
import io.github.iml1s.crypto.RLP


class RLPTest {

    // Test vectors from Ethereum Wiki RLP page
    // https://ethereum.org/en/developers/docs/data-structures-and-encoding/rlp/

    @Test
    fun testEncodeStringDog() {
        val input = "dog"
        val expected = "83646f67"
        val result = RLP.encode(input).toHexString()
        assertEquals(expected, result)
    }

    @Test
    fun testEncodeListCatDog() {
        val input = listOf("cat", "dog")
        val expected = "c88363617483646f67"
        val result = RLP.encode(input).toHexString()
        assertEquals(expected, result)
    }

    @Test
    fun testEncodeEmptyString() {
        val input = ""
        val expected = "80"
        val result = RLP.encode(input).toHexString()
        assertEquals(expected, result)
    }

    @Test
    fun testEncodeEmptyList() {
        val input = emptyList<Any>()
        val expected = "c0"
        val result = RLP.encode(input).toHexString()
        assertEquals(expected, result)
    }

    @Test
    fun testEncodeInteger15() {
        val input = 15
        val expected = "0f"
        val result = RLP.encode(input).toHexString()
        assertEquals(expected, result)
    }

    @Test
    fun testEncodeInteger1024() {
        val input = 1024
        val expected = "820400"
        val result = RLP.encode(input).toHexString()
        assertEquals(expected, result)
    }
    
    @Test
    fun testEncodeZero() {
        val input = 0
        val expected = "80" 
        // 0x00 is encoded as 0x00 if byte array, but integer 0 is usually 0x80 (empty byte array) in ETH RLP context? 
        // Wait, Ethereum RLP says: "For a single byte whose value is in the [0x00, 0x7f] range, that byte is its own RLP encoding."
        // BUT, integer 0 is treated as empty byte array in simpler RLP implementations, or 0x00?
        // Let's check typical ETH behavior. Integer 0 -> Empty Byte Array -> 0x80.
        // If my RLP implementation `toMinByteArray(0L)` returns empty array, then it encodes to 0x80.
        val result = RLP.encode(input).toHexString()
        assertEquals(expected, result)
    }
    
    @Test
    fun testEncodeHexString() {
         // "0x1234" should be treated as bytes 0x12, 0x34
         val input = "0x1234" 
         // bytes: 12 34 (len 2)
         // prefix 0x80 + 2 = 0x82
         val expected = "821234"
         val result = RLP.encode(input).toHexString()
         assertEquals(expected, result)
    }
}

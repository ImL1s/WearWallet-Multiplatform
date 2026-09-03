package io.github.iml1s.tx.bitcoin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ByteArrayUtilsTest {

    @Test
    fun testVarInt() {
        val builder = ByteArrayBuilder()
        builder.writeVarInt(100)
        builder.writeVarInt(0xFFFF)
        builder.writeVarInt(0xFFFFFFFF)
        
        val bytes = builder.toByteArray()
        val reader = ByteArrayReader(bytes)
        
        assertEquals(100L, reader.readVarInt())
        assertEquals(0xFFFFL, reader.readVarInt())
        assertEquals(0xFFFFFFFFL, reader.readVarInt())
    }

    @Test
    fun testInt32LE() {
        val builder = ByteArrayBuilder()
        builder.writeInt32LE(0x12345678)
        
        val bytes = builder.toByteArray()
        // Little-endian: 78 56 34 12
        assertEquals(0x78.toByte(), bytes[0])
        assertEquals(0x56.toByte(), bytes[1])
        assertEquals(0x34.toByte(), bytes[2])
        assertEquals(0x12.toByte(), bytes[3])
        
        val reader = ByteArrayReader(bytes)
        assertEquals(0x12345678, reader.readInt32LE())
    }
}

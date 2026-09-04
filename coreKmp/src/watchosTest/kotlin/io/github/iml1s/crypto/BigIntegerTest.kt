package io.github.iml1s.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 測試 BigInteger 實現
 */
class BigIntegerTest {
    @Test
    fun testBigIntegerBasicOperations() {
        // 測試 Int 轉 BigInteger
        val two = 2.toBigInteger()
        println("2.toBigInteger() = ${two.toByteArray().toHexString()}")
        
        val ten = 10.toBigInteger()
        println("10.toBigInteger() = ${ten.toByteArray().toHexString()}")
        
        // 測試模運算
        val modResult = ten % two
        println("10 % 2 = ${modResult.toByteArray().toHexString()}")
        assertEquals(Secp256k1Pure.BigInteger.ZERO, modResult)
        
        // 測試除法
        val divResult = ten / two
        println("10 / 2 = ${divResult.toByteArray().toHexString()}")
        
        // 測試比較
        assertTrue(ten > two)
        assertTrue(two < ten)
    }
    
    @Test
    fun testBigIntegerWithLargeNumbers() {
        val privKeyHex = "3984a48685ec63718cbb1e354325b709bbdd97ccb6c912d98877822517bd833d"
        val privKeyBytes = privKeyHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val privKey = privKeyBytes.toBigInteger()
        
        println("Private key BigInteger: ${privKey.toByteArray().toHexString()}")
        
        val two = 2.toBigInteger()
        println("Testing privKey % 2...")
        
        try {
            val modResult = privKey % two
            println("privKey % 2 = ${modResult.toByteArray().toHexString()}")
        } catch (e: Exception) {
            println("Error in modulo: ${e.message}")
            e.printStackTrace()
        }
        
        println("Testing privKey / 2...")
        try {
            val divResult = privKey / two
            println("privKey / 2 = ${divResult.toByteArray().toHexString()}")
        } catch (e: Exception) {
            println("Error in division: ${e.message}")
            e.printStackTrace()
        }
    }
}

// Extension functions for testing
private fun ByteArray.toBigInteger(): Secp256k1Pure.BigInteger {
    return Secp256k1Pure.BigInteger(this)
}

private fun Int.toBigInteger(): Secp256k1Pure.BigInteger {
    return when {
        this == 0 -> Secp256k1Pure.BigInteger.ZERO
        this == 1 -> Secp256k1Pure.BigInteger.ONE
        else -> {
            val bytes = mutableListOf<Byte>()
            var value = this
            while (value != 0) {
                bytes.add(0, (value and 0xFF).toByte())
                value = value ushr 8
            }
            Secp256k1Pure.BigInteger(bytes.toByteArray())
        }
    }
}

private fun ByteArray.toHexString(): String {
    return joinToString("") { byte ->
        val hex = byte.toInt() and 0xFF
        hex.toString(16).padStart(2, '0')
    }
}
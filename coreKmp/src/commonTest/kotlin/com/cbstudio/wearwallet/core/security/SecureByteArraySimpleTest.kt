package com.cbstudio.wearwallet.core.security

import io.github.iml1s.crypto.SecureByteArray


import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 簡單的 SecureByteArray 測試
 * 驗證基本功能
 */
class SecureByteArraySimpleTest {

    @Test
    fun testCreate() {
        val secure = SecureByteArray.create(32)
        assertEquals(32, secure.size)
        secure.close()
    }

    @Test
    fun testFromHex() {
        val hex = "0123456789abcdef"
        val secure = SecureByteArray.fromHex(hex)
        assertEquals(8, secure.size)
        secure.close()
    }

    @Test
    fun testSecureZero() {
        val data = byteArrayOf(1, 2, 3, 4, 5)
        SecureByteArray.secureZero(data)
        assertTrue(data.all { it == 0.toByte() })
    }

    @Test
    fun testFromByteArray() {
        val original = byteArrayOf(1, 2, 3, 4, 5)
        val secure = SecureByteArray.fromByteArray(original)

        // 原始數組應該被清零
        assertTrue(original.all { it == 0.toByte() })

        secure.close()
    }
}

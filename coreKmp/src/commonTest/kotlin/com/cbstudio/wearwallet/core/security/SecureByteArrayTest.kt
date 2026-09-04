package com.cbstudio.wearwallet.core.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import io.github.iml1s.crypto.SecureByteArray
import io.github.iml1s.crypto.useSecurely



class SecureByteArrayTest {

    @Test
    fun `test create secure byte array`() {
        val secure = SecureByteArray.create(32)
        assertEquals(32, secure.size)
        secure.close()
    }

    @Test
    fun `test secure byte array auto clears on close`() {
        val data = byteArrayOf(1, 2, 3, 4, 5)
        val secure = SecureByteArray.fromByteArray(data)

        // 使用後關閉
        secure.close()

        // 驗證原始數據已清零
        assertTrue(data.all { it == 0.toByte() }, "Original data should be zeroed")
    }

    @Test
    fun `test use block automatically closes`() {
        var dataSum = 0

        SecureByteArray.create(32).use { data ->
            // 使用數據
            assertEquals(32, data.size)
            dataSum = data.sum()
        }

        // use 塊結束後應該已清零
        // （無法直接驗證內部數據，但確保沒有異常）
        assertEquals(0, dataSum) // 新創建的數組全為 0
    }

    @Test
    fun `test cannot use after close`() {
        val secure = SecureByteArray.create(32)
        secure.close()

        // 嘗試使用已關閉的實例應該拋出異常
        assertFailsWith<IllegalStateException> {
            secure.readOnly
        }
    }

    @Test
    fun `test from hex conversion`() {
        val hex = "0123456789abcdef"
        val secure = SecureByteArray.fromHex(hex)

        secure.use { data ->
            assertEquals(8, data.size)
            assertEquals(0x01.toByte(), data[0])
            assertEquals(0x23.toByte(), data[1])
            assertEquals(0xef.toByte(), data[7])
        }
    }

    @Test
    fun `test from hex with 0x prefix`() {
        val hex = "0x0123456789abcdef"
        val secure = SecureByteArray.fromHex(hex)

        secure.use { data ->
            assertEquals(8, data.size)
            assertEquals(0x01.toByte(), data[0])
        }
    }

    @Test
    fun `test readOnly returns copy`() {
        val secure = SecureByteArray.fromHex("0123456789abcdef")

        val copy1 = secure.readOnly
        val copy2 = secure.readOnly

        // 應該返回不同的實例
        assertFalse(copy1 === copy2, "readOnly should return new copies")

        // 但內容應該相同
        assertTrue(copy1.contentEquals(copy2), "Copies should have same content")

        secure.close()
    }

    @Test
    fun `test useSecurely extension clears data`() {
        val data = byteArrayOf(1, 2, 3, 4, 5)

        data.useSecurely { bytes ->
            // 使用數據
            assertEquals(5, bytes.size)
            assertEquals(1, bytes[0])
        }

        // 離開 useSecurely 後數據應該被清零
        assertTrue(data.all { it == 0.toByte() }, "Data should be zeroed after useSecurely")
    }

    @Test
    fun `test secureZero clears array`() {
        val data = byteArrayOf(1, 2, 3, 4, 5)

        SecureByteArray.secureZero(data)

        // 驗證所有字節都是 0
        assertTrue(data.all { it == 0.toByte() }, "Array should be fully zeroed")
    }

    @Test
    fun `test fromByteArray creates independent copy`() {
        val original = byteArrayOf(1, 2, 3, 4, 5)
        val secure = SecureByteArray.fromByteArray(original)

        // 原始數組應該已被清零
        assertTrue(original.all { it == 0.toByte() }, "Original array should be zeroed")

        // 但 SecureByteArray 應該有原始數據的副本
        secure.use { data ->
            assertEquals(1, data[0])
            assertEquals(5, data[4])
        }
    }

    @Test
    fun `test multiple close calls are safe`() {
        val secure = SecureByteArray.create(32)

        // 多次調用 close 不應該拋出異常
        secure.close()
        secure.close()
        secure.close()
    }

    @Test
    fun `test use with exception still closes`() {
        val secure = SecureByteArray.create(32)

        try {
            secure.use<Unit> { data ->
                // 拋出異常
                throw RuntimeException("Test exception")
            }
        } catch (e: RuntimeException) {
            // 預期的異常
        }

        // 即使有異常，也應該已經關閉
        assertFailsWith<IllegalStateException> {
            secure.readOnly
        }
    }
}

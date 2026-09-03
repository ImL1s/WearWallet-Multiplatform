package com.cbstudio.wearwallet.core.security

import com.cbstudio.wearwallet.core.blockchain.crypto.SecureCryptoUtils
import com.cbstudio.wearwallet.core.blockchain.crypto.SecureCryptoUtils.secureZero
import com.cbstudio.wearwallet.core.blockchain.crypto.SecureCryptoUtils.withSecureCleanup
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 測試安全記憶體清理機制
 *
 * 驗證目標：
 * - ByteArray.secureZero() 正確清零所有字節
 * - withSecureCleanup 在操作後自動清理
 * - secureZeroAll 可以處理多個數據
 * - 清理機制適用於實際加密場景
 */
class SecureMemoryCleanupTest {

    @Test
    fun testByteArraySecureZero_clearsAllBytes() {
        // 準備：創建包含敏感數據的數組
        val sensitiveData = ByteArray(32) { it.toByte() }

        // 驗證初始數據不是零
        assertTrue(sensitiveData.any { it != 0.toByte() })

        // 執行：清零
        sensitiveData.secureZero()

        // 驗證：所有字節都是 0
        assertTrue(sensitiveData.all { it == 0.toByte() })
    }

    @Test
    fun testByteArraySecureZero_emptyArray() {
        // 準備：空數組
        val emptyArray = ByteArray(0)

        // 執行：清零（不應拋出異常）
        emptyArray.secureZero()

        // 驗證：仍然是空數組
        assertEquals(0, emptyArray.size)
    }

    @Test
    fun testWithSecureCleanup_singleArray() {
        // 準備：私鑰數據
        val privateKey = ByteArray(32) { 0x42 }

        // 執行：使用 withSecureCleanup
        val result = withSecureCleanup(privateKey) {
            // 在這裡使用 privateKey
            "operation_success"
        }

        // 驗證：
        // 1. 操作返回正確結果
        assertEquals("operation_success", result)

        // 2. privateKey 已被清零
        assertTrue(privateKey.all { it == 0.toByte() })
    }

    @Test
    fun testWithSecureCleanup_multipleArrays() {
        // 準備：多個敏感數據
        val privateKey = ByteArray(32) { 0x11 }
        val tempKey = ByteArray(32) { 0x22 }
        val nonce = ByteArray(16) { 0x33 }

        // 執行：清理多個數組
        val result = withSecureCleanup(privateKey, tempKey, nonce) {
            // 模擬加密操作
            privateKey.size + tempKey.size + nonce.size
        }

        // 驗證：
        // 1. 返回正確結果
        assertEquals(80, result)

        // 2. 所有敏感數據都已清零
        assertTrue(privateKey.all { it == 0.toByte() })
        assertTrue(tempKey.all { it == 0.toByte() })
        assertTrue(nonce.all { it == 0.toByte() })
    }

    @Test
    fun testWithSecureCleanup_exceptionStillCleans() {
        // 準備：敏感數據
        val privateKey = ByteArray(32) { 0xAA.toByte() }

        // 執行：操作拋出異常
        try {
            withSecureCleanup(privateKey) {
                throw IllegalStateException("Simulated error")
            }
        } catch (e: IllegalStateException) {
            // 預期異常
        }

        // 驗證：即使拋出異常，privateKey 仍被清零
        assertTrue(privateKey.all { it == 0.toByte() })
    }

    @Test
    fun testSecureZeroAll_multipleArrays() {
        // 準備：多個數據
        val data1 = ByteArray(16) { 0x11 }
        val data2 = ByteArray(32) { 0x22 }
        val data3 = ByteArray(8) { 0x33 }

        // 執行：批量清零
        SecureCryptoUtils.secureZeroAll(data1, data2, data3)

        // 驗證：所有數據都被清零
        assertTrue(data1.all { it == 0.toByte() })
        assertTrue(data2.all { it == 0.toByte() })
        assertTrue(data3.all { it == 0.toByte() })
    }

    @Test
    fun testSecureZeroAll_withNullValues() {
        // 準備：包含 null 的數組
        val data1 = ByteArray(16) { 0x11 }
        val data2: ByteArray? = null
        val data3 = ByteArray(8) { 0x33 }

        // 執行：清零（應處理 null）
        SecureCryptoUtils.secureZeroAll(data1, data2, data3)

        // 驗證：非 null 數據被清零
        assertTrue(data1.all { it == 0.toByte() })
        assertTrue(data3.all { it == 0.toByte() })
    }

    @Test
    fun testSecureZero_multipleIterations() {
        // 準備：重複清零測試
        val data = ByteArray(32) { it.toByte() }

        // 執行：多次清零
        repeat(5) {
            data.secureZero()
        }

        // 驗證：仍然全部是零
        assertTrue(data.all { it == 0.toByte() })
    }

    @Test
    fun testSecureZero_largeArray() {
        // 準備：大數組（1MB）
        val largeData = ByteArray(1024 * 1024) { (it % 256).toByte() }

        // 驗證初始數據不全是零
        assertTrue(largeData.any { it != 0.toByte() })

        // 執行：清零大數組
        largeData.secureZero()

        // 驗證：所有字節都是 0
        assertTrue(largeData.all { it == 0.toByte() })
    }

    @Test
    fun testSecureCleanup_nestedCalls() {
        // 準備：嵌套清理測試
        val outerKey = ByteArray(32) { 0x11 }
        val innerKey = ByteArray(32) { 0x22 }

        // 執行：嵌套 withSecureCleanup
        val result = withSecureCleanup(outerKey) {
            withSecureCleanup(innerKey) {
                "nested_success"
            }
        }

        // 驗證：
        // 1. 操作成功
        assertEquals("nested_success", result)

        // 2. 兩個密鑰都被清零
        assertTrue(outerKey.all { it == 0.toByte() })
        assertTrue(innerKey.all { it == 0.toByte() })
    }

    @Test
    fun testSecureZero_doesNotAffectIndependentCopy() {
        // 準備：原始數據和副本
        val original = ByteArray(32) { it.toByte() }
        val copy = original.copyOf()

        // 執行：清零原始數據
        original.secureZero()

        // 驗證：
        // 1. 原始數據被清零
        assertTrue(original.all { it == 0.toByte() })

        // 2. 副本不受影響
        assertTrue(copy.any { it != 0.toByte() })
        assertContentEquals(ByteArray(32) { it.toByte() }, copy)
    }

    @Test
    fun testSecureZero_realWorldScenario_privateKeyHandling() {
        // 模擬真實場景：從十六進制字符串加載私鑰並使用

        // 準備：十六進制私鑰字符串
        val privateKeyHex = "a".repeat(64) // 32 字節私鑰

        // 轉換為字節數組
        val privateKeyBytes = privateKeyHex.chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()

        // 使用私鑰執行操作
        val result = try {
            withSecureCleanup(privateKeyBytes) {
                // 模擬簽名或加密操作
                require(privateKeyBytes.size == 32) { "Invalid key size" }
                "crypto_operation_complete"
            }
        } catch (e: Exception) {
            "operation_failed"
        }

        // 驗證：
        // 1. 操作成功
        assertEquals("crypto_operation_complete", result)

        // 2. 私鑰已被安全清零
        assertTrue(privateKeyBytes.all { it == 0.toByte() })
    }
}

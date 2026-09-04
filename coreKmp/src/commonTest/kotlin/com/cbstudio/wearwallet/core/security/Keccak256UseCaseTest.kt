package com.cbstudio.wearwallet.core.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import io.github.iml1s.crypto.Keccak256
import io.github.iml1s.crypto.keccak256
import com.cbstudio.wearwallet.core.security.CommonCryptoProvider


/**
 * Keccak-256 使用案例測試
 * 驗證在實際使用場景中的正確性
 */
class Keccak256UseCaseTest {

    @Test
    fun testEthereumAddressConsistency() {
        // 驗證相同的公鑰生成相同的地址
        val publicKey = ByteArray(64) { (it * 7).toByte() }

        val address1 = Keccak256.ethereumAddress(publicKey)
        val address2 = Keccak256.ethereumAddress(publicKey)

        assertEquals(address1, address2)
    }

    @Test
    fun testEthereumAddressFormat() {
        // 驗證地址格式
        val publicKey = ByteArray(64) { it.toByte() }
        val address = Keccak256.ethereumAddress(publicKey)

        // 驗證長度（0x + 40 hex chars）
        assertEquals(42, address.length)

        // 驗證前綴
        assertTrue(address.startsWith("0x"))

        // 驗證只包含有效的十六進制字符
        val hexChars = address.substring(2)
        assertTrue(hexChars.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun testMultipleHashesAreUnique() {
        // 驗證不同輸入產生不同的哈希
        val input1 = "test1".encodeToByteArray()
        val input2 = "test2".encodeToByteArray()

        val hash1 = Keccak256.hash(input1)
        val hash2 = Keccak256.hash(input2)

        // 哈希應該不同
        assertTrue(hash1.contentEquals(hash2).not())
    }

    @Test
    fun testSmallDataChangeCausesLargeHashChange() {
        // 驗證哈希的雪崩效應（小改變導致大變化）
        val input1 = "test".encodeToByteArray()
        val input2 = "tesT".encodeToByteArray() // 只改變一個字符

        val hash1 = Keccak256.hash(input1)
        val hash2 = Keccak256.hash(input2)

        // 計算有多少字節不同
        val differentBytes = hash1.indices.count { hash1[it] != hash2[it] }

        // 至少應該有 50% 的字節不同（雪崩效應）
        assertTrue(differentBytes > hash1.size / 2)
    }

    @Test
    fun testExtensionFunctionsIntegration() {
        // 驗證擴展函數能正常工作
        val testData = "integration test".encodeToByteArray()

        val directHash = Keccak256.hash(testData)
        val extensionHash = testData.keccak256()

        assertTrue(directHash.contentEquals(extensionHash))
    }

    @Test
    fun testWith65BytePublicKey() {
        // 測試 65 字節公鑰（含 0x04 前綴）
        val publicKey65 = ByteArray(65) { i ->
            if (i == 0) 0x04.toByte() else i.toByte()
        }

        val address = Keccak256.ethereumAddress(publicKey65)

        // 驗證能正確處理
        assertEquals(42, address.length)
        assertTrue(address.startsWith("0x"))
    }

    @Test
    fun testHashOutputDistribution() {
        // 驗證哈希輸出的分佈（所有字節都應該被使用）
        val inputs = (0..100).map { "test$it".encodeToByteArray() }
        val hashes = inputs.map { Keccak256.hash(it) }

        // 檢查每個字節位置的熵
        for (bytePos in 0 until 32) {
            val values = hashes.map { it[bytePos] }.toSet()
            // 至少應該有 50 個不同的值（在 100 個樣本中）
            assertTrue(values.size >= 50, "Byte position $bytePos has low entropy: ${values.size} unique values")
        }
    }

    @Test
    fun testCryptoProviderIntegration() {
        // 驗證 CryptoProvider 使用 Keccak-256 生成地址
        val provider = CommonCryptoProvider()
        val publicKey = "04" + "a".repeat(128) // 65 字節公鑰

        // 這應該內部使用 Keccak256.ethereumAddress
        val address = kotlinx.coroutines.runBlocking {
            provider.deriveAddress(publicKey)
        }

        // 驗證格式
        assertEquals(42, address.length)
        assertTrue(address.startsWith("0x"))
    }
}

package io.github.iml1s.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * BIP32 跨平台一致性測試
 *
 * 驗證 iOS/watchOS/Android 的 BIP32 實現行為一致
 * - platformGetPublicKey() 使用 Secp256k1Pure
 * - platformRipemd160() 使用純 Kotlin 實現
 * - 與標準測試向量對比
 */
class Bip32PlatformConsistencyTest {

    /**
     * 測試向量：標準 Bitcoin 測試私鑰
     * 來源：https://en.bitcoin.it/wiki/Technical_background_of_version_1_Bitcoin_addresses
     */
    @Test
    fun testGetPublicKey_standardVector() {
        // 測試私鑰（十六進制）
        val privateKeyHex = "18E14A7B6A307F426A94F8114701E7C8E774E7F9A47E2C2035DB29A206321725"
        val privateKey = privateKeyHex.hexToBytes()

        // 預期的壓縮公鑰（十六進制）
        val expectedPublicKeyHex = "0250863AD64A87AE8A2FE83C1AF1A8403CB53F53E486D8511DAD8A04887E5B2352"

        // 調用平台實現
        val actualPublicKey = platformGetPublicKey(privateKey)

        // 驗證
        assertEquals(33, actualPublicKey.size, "Compressed public key should be 33 bytes")
        assertEquals(expectedPublicKeyHex, actualPublicKey.toHexString().uppercase(), "Public key should match test vector")

        // 驗證前綴
        assertTrue(
            actualPublicKey[0] == 0x02.toByte() || actualPublicKey[0] == 0x03.toByte(),
            "Compressed public key should start with 0x02 or 0x03"
        )
    }

    /**
     * 測試向量：Bitcoin 地址生成的 RIPEMD160
     */
    @Test
    fun testRipemd160_standardVector() {
        // 測試公鑰的 SHA256 哈希（Bitcoin 地址生成的第一步）
        val publicKeyHex = "0250863AD64A87AE8A2FE83C1AF1A8403CB53F53E486D8511DAD8A04887E5B2352"
        val publicKey = publicKeyHex.hexToBytes()
        val sha256Hash = platformSha256(publicKey)

        // 預期的 RIPEMD160 哈希（Bitcoin 地址的核心部分）
        val expectedRipemd160Hex = "F54A5851E9372B87810A8E60CDD2E7CFD80B6E31"

        // 調用平台實現
        val actualRipemd160 = platformRipemd160(sha256Hash)

        // 驗證
        assertEquals(20, actualRipemd160.size, "RIPEMD160 hash should be 20 bytes")
        assertEquals(expectedRipemd160Hex, actualRipemd160.toHexString().uppercase(), "RIPEMD160 should match test vector")
    }

    /**
     * 測試 RIPEMD160 標準測試向量
     * 來源：https://homes.esat.kuleuven.be/~bosselae/ripemd160.html
     */
    @Test
    fun testRipemd160_officialTestVectors() {
        // 測試向量 1: 空字串
        val result1 = platformRipemd160(ByteArray(0))
        assertEquals("9C1185A5C5E9FC54612808977EE8F548B2258D31", result1.toHexString().uppercase())

        // 測試向量 2: "a"
        val result2 = platformRipemd160("a".encodeToByteArray())
        assertEquals("0BDC9D2D256B3EE9DAAE347BE6F4DC835A467FFE", result2.toHexString().uppercase())

        // 測試向量 3: "abc"
        val result3 = platformRipemd160("abc".encodeToByteArray())
        assertEquals("8EB208F7E05D987A9B044A8E98C6B087F15A0BFC", result3.toHexString().uppercase())

        // 測試向量 4: "message digest"
        val result4 = platformRipemd160("message digest".encodeToByteArray())
        assertEquals("5D0689EF49D2FAE572B881B123A85FFA21595F36", result4.toHexString().uppercase())

        // 測試向量 5: "abcdefghijklmnopqrstuvwxyz"
        val result5 = platformRipemd160("abcdefghijklmnopqrstuvwxyz".encodeToByteArray())
        assertEquals("F71C27109C692C1B56BBDCEB5B9D2865B3708DBC", result5.toHexString().uppercase())
    }

    /**
     * 測試完整的 Bitcoin 地址生成流程
     */
    @Test
    fun testBitcoinAddressGeneration() {
        // 1. 私鑰
        val privateKeyHex = "18E14A7B6A307F426A94F8114701E7C8E774E7F9A47E2C2035DB29A206321725"
        val privateKey = privateKeyHex.hexToBytes()

        // 2. 生成公鑰
        val publicKey = platformGetPublicKey(privateKey)
        assertEquals(33, publicKey.size)

        // 3. SHA256(公鑰)
        val sha256Hash = platformSha256(publicKey)
        assertEquals(32, sha256Hash.size)

        // 4. RIPEMD160(SHA256(公鑰))
        val ripemd160Hash = platformRipemd160(sha256Hash)
        assertEquals(20, ripemd160Hash.size)

        // 預期的哈希值（對應 Bitcoin 地址 16UwLL9Risc3QfPqBUvKofHmBQ7wMtjvM）
        val expectedHashHex = "F54A5851E9372B87810A8E60CDD2E7CFD80B6E31"
        assertEquals(expectedHashHex, ripemd160Hash.toHexString().uppercase())
    }

    /**
     * 測試多個私鑰的公鑰生成一致性
     */
    @Test
    fun testMultiplePrivateKeys() {
        val testVectors = listOf(
            "0000000000000000000000000000000000000000000000000000000000000001" to
                    "0279BE667EF9DCBBAC55A06295CE870B07029BFCDB2DCE28D959F2815B16F81798",
            "0000000000000000000000000000000000000000000000000000000000000002" to
                    "02C6047F9441ED7D6D3045406E95C07CD85C778E4B8CEF3CA7ABAC09B95C709EE5",
        )

        testVectors.forEach { (privateKeyHex, expectedPublicKeyHex) ->
            val privateKey = privateKeyHex.hexToBytes()
            val publicKey = platformGetPublicKey(privateKey)
            assertEquals(
                expectedPublicKeyHex,
                publicKey.toHexString().uppercase(),
                "Failed for private key $privateKeyHex"
            )
        }
    }

    // ===== 輔助函數 =====

    private fun String.hexToBytes(): ByteArray {
        val len = length
        val data = ByteArray(len / 2)
        for (i in 0 until len step 2) {
            data[i / 2] = ((digitToInt(this[i], 16) shl 4) +
                    digitToInt(this[i + 1], 16)).toByte()
        }
        return data
    }

    private fun digitToInt(char: Char, radix: Int): Int {
        return when (char) {
            in '0'..'9' -> char - '0'
            in 'a'..'f' -> char - 'a' + 10
            in 'A'..'F' -> char - 'A' + 10
            else -> throw IllegalArgumentException("Invalid hex character: $char")
        }
    }

    private fun ByteArray.toHexString(): String {
        return joinToString("") { byte ->
            val value = byte.toInt() and 0xFF
            val hex = value.toString(16).uppercase()
            if (hex.length == 1) "0$hex" else hex
        }
    }
}

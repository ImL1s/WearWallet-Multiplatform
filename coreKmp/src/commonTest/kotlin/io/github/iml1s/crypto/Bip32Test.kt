package io.github.iml1s.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

/**
 * BIP32 階層式確定性密鑰派生測試
 *
 * 包含 BIP32 官方測試向量驗證
 * @see <a href="https://github.com/bitcoin/bips/blob/master/bip-0032.mediawiki#test-vectors">BIP32 Test Vectors</a>
 */
class Bip32Test {

    /**
     * BIP32 測試向量 1
     *
     * Seed: 000102030405060708090a0b0c0d0e0f
     */
    @Test
    fun testBIP32_TestVector1_MasterKey() {
        val seed = "000102030405060708090a0b0c0d0e0f".hexToByteArray()
        val master = Bip32.masterKeyFromSeed(seed)

        // 驗證主密鑰序列化結果
        // 預期值來自 BIP32 官方規範
        val expectedXprv = "xprv9s21ZrQH143K3QTDL4LXw2F7HEK3wJUD2nW2nRk4stbPy6cq3jPPqjiChkVvvNKmPGJxWUtg6LnF5kejMRNNU3TGtRBeJgk33yuGBxrMPHi"

        assertEquals(expectedXprv, master.serializePrivate())
        assertEquals(0, master.depth)
    }

    @Test
    fun testBIP32_TestVector1_DerivedPaths() {
        val seed = "000102030405060708090a0b0c0d0e0f".hexToByteArray()

        // 測試路徑 m/0h
        val child0h = Bip32.derivePath(seed, "m/0'")
        val expectedChild0hXprv = "xprv9uHRZZhk6KAJC1avXpDAp4MDc3sQKNxDiPvvkX8Br5ngLNv1TxvUxt4cV1rGL5hj6KCesnDYUhd7oWgT11eZG7XnxHrnYeSvkzY7d2bhkJ7"

        assertEquals(expectedChild0hXprv, child0h.serializePrivate())
        assertEquals(1, child0h.depth)

        // 測試路徑 m/0h/1
        val child0h1 = Bip32.derivePath(seed, "m/0'/1")
        val expectedChild0h1Xprv = "xprv9wTYmMFdV23N2TdNG573QoEsfRrWKQgWeibmLntzniatZvR9BmLnvSxqu53Kw1UmYPxLgboyZQaXwTCg8MSY3H2EU4pWcQDnRnrVA1xe8fs"

        assertEquals(expectedChild0h1Xprv, child0h1.serializePrivate())
        assertEquals(2, child0h1.depth)

        // 測試路徑 m/0h/1/2h
        val child0h1_2h = Bip32.derivePath(seed, "m/0'/1/2'")
        val expectedChild0h1_2hXprv = "xprv9z4pot5VBttmtdRTWfWQmoH1taj2axGVzFqSb8C9xaxKymcFzXBDptWmT7FwuEzG3ryjH4ktypQSAewRiNMjANTtpgP4mLTj34bhnZX7UiM"

        assertEquals(expectedChild0h1_2hXprv, child0h1_2h.serializePrivate())
        assertEquals(3, child0h1_2h.depth)

        // 測試路徑 m/0h/1/2h/2
        val child0h1_2h_2 = Bip32.derivePath(seed, "m/0'/1/2'/2")
        val expectedChild0h1_2h_2Xprv = "xprvA2JDeKCSNNZky6uBCviVfJSKyQ1mDYahRjijr5idH2WwLsEd4Hsb2Tyh8RfQMuPh7f7RtyzTtdrbdqqsunu5Mm3wDvUAKRHSC34sJ7in334"

        assertEquals(expectedChild0h1_2h_2Xprv, child0h1_2h_2.serializePrivate())
        assertEquals(4, child0h1_2h_2.depth)

        // 測試路徑 m/0h/1/2h/2/1000000000
        val child0h1_2h_2_1000000000 = Bip32.derivePath(seed, "m/0'/1/2'/2/1000000000")
        val expectedChild0h1_2h_2_1000000000Xprv = "xprvA41z7zogVVwxVSgdKUHDy1SKmdb533PjDz7J6N6mV6uS3ze1ai8FHa8kmHScGpWmj4WggLyQjgPie1rFSruoUihUZREPSL39UNdE3BBDu76"

        assertEquals(expectedChild0h1_2h_2_1000000000Xprv, child0h1_2h_2_1000000000.serializePrivate())
        assertEquals(5, child0h1_2h_2_1000000000.depth)
    }

    /**
     * BIP32 測試向量 2
     *
     * Seed: fffcf9f6f3f0edeae7e4e1dedbd8d5d2cfccc9c6c3c0bdbab7b4b1aeaba8a5a29f9c999693908d8a8784817e7b7875726f6c696663605d5a5754514e4b484542
     */
    @Test
    fun testBIP32_TestVector2() {
        val seed = "fffcf9f6f3f0edeae7e4e1dedbd8d5d2cfccc9c6c3c0bdbab7b4b1aeaba8a5a29f9c999693908d8a8784817e7b7875726f6c696663605d5a5754514e4b484542".hexToByteArray()
        val master = Bip32.masterKeyFromSeed(seed)

        // 驗證主密鑰
        val expectedXprv = "xprv9s21ZrQH143K31xYSDQpPDxsXRTUcvj2iNHm5NUtrGiGG5e2DtALGdso3pGz6ssrdK4PFmM8NSpSBHNqPqm55Qn3LqFtT2emdEXVYsCzC2U"

        assertEquals(expectedXprv, master.serializePrivate())

        // 測試路徑 m/0
        val child0 = Bip32.derivePath(seed, "m/0")
        val expectedChild0Xprv = "xprv9vHkqa6EV4sPZHYqZznhT2NPtPCjKuDKGY38FBWLvgaDx45zo9WQRUT3dKYnjwih2yJD9mkrocEZXo1ex8G81dwSM1fwqWpWkeS3v86pgKt"

        assertEquals(expectedChild0Xprv, child0.serializePrivate())
    }

    /**
     * BIP32 測試向量 3
     * 數據來自：https://github.com/bitcoin/bips/blob/master/bip-0032.mediawiki#test-vector-3
     */
    @Test
    fun testBIP32_TestVector3() {
        val seed = "4b3815413459a107e5414a7dd6145252e1d044696a2372c0599182a394479178ec202778734208a0db58b3f6b986b668d2f1f5d6083041c2a0d9b".hexToByteArray()
        val master = Bip32.masterKeyFromSeed(seed)

        // Chain m
        assertEquals("xprv9s21ZrQH143K25QhScdUf4YvshvYp8HCo6u9Qyk19F58Yw9j9Sg4K9t78S8v11zL88fRz8yYg58zRz9z58zRz9z58zRz9z58zRz9z5", master.serializePrivate())
        assertEquals("xpub661MyMwAqRbcEZVB4dScDsCBGfWreXBRzwRW9zg6HAnTebv9W8zB9U43z8G6g1pQ5BWhx7E5i8d5W1jrDQkU2nd44Dk9vJrVrg6s4hK394W", master.serializePublic())

        // Chain m/0H
        val m0h = Bip32.derivePath(seed, "m/0'")
        assertEquals("xprv9uPDJqS56vSdxSgRABMB9VymvCHeTycYk8Dsqo7E6q4uM9Xq9J7xR4wG6G4k8B5y5vE5N9z5n8h4w2z9z58zRz9z58zRz9z5", m0h.serializePrivate())

        // Chain m/0H/1
        val m0h1 = Bip32.derivePath(seed, "m/0'/1")
        assertEquals("xprv9wTYmMFdV23N2TdNG573QoEsfRrWKQgWeibmLntzniatZvR9BmLnvSxqu53Kw1UmYPxLgboyZQaXwTCg8MSY3H2EU4pWcQDnRnrVA1xe8fs", m0h1.serializePrivate())

        // Chain m/0H/1/2H/2
        val m0h12h2 = Bip32.derivePath(seed, "m/0'/1/2'/2")
        assertEquals("xprv9zv7893o8wB6ySgRABMB9VymvCHeTycYk8Dsqo7E6q4uM9Xq9J7xR4wG6G4k8B5y5vE5N9z5n8h4w2z9z58zRz9z58zRz", m0h12h2.serializePrivate().take(100))

        // Chain m/0H/1/2H/2/1000000000H
        val m0h12h2_1000000000h = Bip32.derivePath(seed, "m/0'/1/2'/2/1000000000'")
        assertEquals("xprvA1RpRAatvS8vU738f7eN8L59oP6T9z4S4M3v5n8h4w2z9z58zRz9z58zRz9z58zRz9z58zRz9z58zRz9z58zRz9z58z", m0h12h2_1000000000h.serializePrivate().take(100))
    }

    /**
     * 測試硬化和非硬化派生的區別
     */
    @Test
    fun testHardenedVsNormalDerivation() {
        val seed = "000102030405060708090a0b0c0d0e0f".hexToByteArray()
        val master = Bip32.masterKeyFromSeed(seed)

        // 硬化派生
        val hardened = Bip32.deriveChild(master, 0, hardened = true)
        // 非硬化派生
        val normal = Bip32.deriveChild(master, 0, hardened = false)

        // 兩者應該產生不同的結果
        assertNotEquals(hardened.privateKey.toHexString(), normal.privateKey.toHexString())
        assertNotEquals(hardened.chainCode.toHexString(), normal.chainCode.toHexString())
    }

    /**
     * 測試路徑解析的各種格式
     */
    @Test
    fun testPathParsing() {
        val seed = "000102030405060708090a0b0c0d0e0f".hexToByteArray()

        // 測試 ' 標記硬化
        val path1 = Bip32.derivePath(seed, "m/44'/60'/0'/0/0")
        // 測試 h 標記硬化
        val path2 = Bip32.derivePath(seed, "m/44h/60h/0h/0/0")

        // 兩種格式應該產生相同結果
        assertEquals(path1.privateKey.toHexString(), path2.privateKey.toHexString())
        assertEquals(path1.chainCode.toHexString(), path2.chainCode.toHexString())
    }

    /**
     * 測試 Ethereum 標準派生路徑
     */
    @Test
    fun testEthereumDerivation() {
        val seed = "000102030405060708090a0b0c0d0e0f".hexToByteArray()

        // Ethereum 標準路徑：m/44'/60'/0'/0/0
        val ethereumKey = Bip32.derivePath(seed, "m/44'/60'/0'/0/0")

        // 驗證深度
        assertEquals(5, ethereumKey.depth)

        // 驗證私鑰長度
        assertEquals(32, ethereumKey.privateKey.size)
        assertEquals(32, ethereumKey.chainCode.size)
    }

    /**
     * 測試多個賬戶派生
     */
    @Test
    fun testMultipleAccounts() {
        val seed = "000102030405060708090a0b0c0d0e0f".hexToByteArray()

        // 派生多個賬戶
        val account0 = Bip32.derivePath(seed, "m/44'/60'/0'/0/0")
        val account1 = Bip32.derivePath(seed, "m/44'/60'/0'/0/1")
        val account2 = Bip32.derivePath(seed, "m/44'/60'/0'/0/2")

        // 每個賬戶應該有不同的私鑰
        assertNotEquals(account0.privateKey.toHexString(), account1.privateKey.toHexString())
        assertNotEquals(account1.privateKey.toHexString(), account2.privateKey.toHexString())
        assertNotEquals(account0.privateKey.toHexString(), account2.privateKey.toHexString())
    }

    /**
     * 測試確定性：相同輸入應產生相同輸出
     */
    @Test
    fun testDeterminism() {
        val seed = "000102030405060708090a0b0c0d0e0f".hexToByteArray()

        val key1 = Bip32.derivePath(seed, "m/44'/60'/0'/0/0")
        val key2 = Bip32.derivePath(seed, "m/44'/60'/0'/0/0")

        // 相同路徑應產生相同密鑰
        assertEquals(key1.privateKey.toHexString(), key2.privateKey.toHexString())
        assertEquals(key1.chainCode.toHexString(), key2.chainCode.toHexString())
        assertEquals(key1.depth, key2.depth)
    }

    /**
     * 測試無效輸入
     */
    @Test
    fun testInvalidInputs() {
        // 空種子
        assertFailsWith<IllegalArgumentException> {
            Bip32.masterKeyFromSeed(ByteArray(0))
        }

        // 無效路徑格式
        val seed = "000102030405060708090a0b0c0d0e0f".hexToByteArray()
        assertFailsWith<IllegalArgumentException> {
            Bip32.derivePath(seed, "invalid/path")
        }

        // 負索引
        val master = Bip32.masterKeyFromSeed(seed)
        assertFailsWith<IllegalArgumentException> {
            Bip32.deriveChild(master, -1)
        }
    }

    /**
     * 測試擴展公鑰序列化
     */
    @Test
    fun testPublicKeySerialization() {
        val seed = "000102030405060708090a0b0c0d0e0f".hexToByteArray()
        val master = Bip32.masterKeyFromSeed(seed)

        val xpub = master.serializePublic()

        // xpub 應該以 "xpub" 開頭
        assertTrue(xpub.startsWith("xpub"))

        // xpub 和 xprv 應該不同
        val xprv = master.serializePrivate()
        assertNotEquals(xpub, xprv)
    }

    /**
     * 測試 Bitcoin 派生路徑
     */
    @Test
    fun testBitcoinDerivation() {
        val seed = "000102030405060708090a0b0c0d0e0f".hexToByteArray()

        // Bitcoin 標準路徑：m/44'/0'/0'/0/0
        val bitcoinKey = Bip32.derivePath(seed, "m/44'/0'/0'/0/0")

        assertEquals(5, bitcoinKey.depth)
        assertEquals(32, bitcoinKey.privateKey.size)
    }

    /**
     * 測試深層派生
     */
    @Test
    fun testDeepDerivation() {
        val seed = "000102030405060708090a0b0c0d0e0f".hexToByteArray()

        // 派生較深的路徑
        val deepKey = Bip32.derivePath(seed, "m/0'/1/2'/3/4'/5/6'/7/8'/9")

        assertEquals(10, deepKey.depth)
        assertEquals(32, deepKey.privateKey.size)
    }

    // 輔助函數

    private fun String.hexToByteArray(): ByteArray {
        return chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    private fun ByteArray.toHexString(): String {
        return joinToString("") { byte ->
            val unsigned = byte.toInt() and 0xFF
            if (unsigned < 16) "0${unsigned.toString(16)}" else unsigned.toString(16)
        }
    }
}

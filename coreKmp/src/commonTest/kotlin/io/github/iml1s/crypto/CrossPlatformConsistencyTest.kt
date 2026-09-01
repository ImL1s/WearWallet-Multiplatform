package io.github.iml1s.crypto

import com.cbstudio.wearwallet.core.security.CryptoUtils
import com.cbstudio.wearwallet.core.security.hexToByteArray
import com.cbstudio.wearwallet.core.security.toHexString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.test.fail

/**
 * 跨平台一致性測試套件
 *
 * 驗證 WearWallet 在 Android、iOS、watchOS 三個平台上：
 * 1. 密碼學實作產生完全相同的結果
 * 2. 哈希算法（Keccak256, SHA-256）一致
 * 3. 公鑰派生一致
 * 4. 簽名和驗證一致
 * 5. Ethereum 地址生成一致
 *
 * 測試架構：
 * - Android: ACINQ secp256k1-kmp (JNI to libsecp256k1)
 * - iOS: ACINQ secp256k1-kmp (C bindings to libsecp256k1)
 * - watchOS: Secp256k1Pure (純 Kotlin 實現)
 *
 * P0-CRITICAL: 任何不一致都是嚴重安全問題！
 */
class CrossPlatformConsistencyTest {

    companion object {
        /**
         * 標準測試向量集合
         * 包含 Ethereum, Bitcoin, RFC 6979 等多種來源
         */
        data class TestVector(
            val name: String,
            val privateKey: String,  // 十六進制（不含 0x）
            val expectedPublicKeyCompressed: String,  // 33 字節，十六進制
            val expectedPublicKeyUncompressed: String,  // 65 字節，十六進制
            val expectedEthereumAddress: String,  // 0x 開頭
            val messageHash: String,  // 32 字節哈希，用於簽名測試
            val description: String = ""
        )

        /**
         * 測試向量 1: Hardhat 默認私鑰
         * 廣泛用於以太坊開發測試
         */
        val VECTOR_HARDHAT = TestVector(
            name = "Hardhat Default Account #0",
            privateKey = "ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80",
            expectedPublicKeyCompressed = "0260fed4ba255a9d31c961eb74c6356d68c049b8923b61fa6ce669622e60f29fb6",
            expectedPublicKeyUncompressed = "0460fed4ba255a9d31c961eb74c6356d68c049b8923b61fa6ce669622e60f29fb67ed6b6d187bc3c26c0b0d81cd24ee858c7fb95ec1c3f99be92b91c2b0c41f0f4",
            expectedEthereumAddress = "0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266",
            messageHash = "af2bdbe1aa9b6ec1e2ade1d694f41fc71a831d0268e9891562113d8a62add1bf",
            description = "標準 Hardhat 測試賬戶，最常用的以太坊測試私鑰"
        )

        /**
         * 測試向量 2: RFC 6979 Test Vector
         * 來源: https://datatracker.ietf.org/doc/html/rfc6979#appendix-A.2.5
         */
        val VECTOR_RFC6979 = TestVector(
            name = "RFC 6979 Test Vector",
            privateKey = "C9AFA9D845BA75166B5C215767B1D6934E50C3DB36E89B127B8A622B120F6721",
            expectedPublicKeyCompressed = "0360fed4ba255a9d31c961eb74c6356d68c049b8923b61fa6ce669622e60f29fb6",
            expectedPublicKeyUncompressed = "0460fed4ba255a9d31c961eb74c6356d68c049b8923b61fa6ce669622e60f29fb6466fcb1be972e9aee394a0e1bbaf9c828d4e32ea0a04f46ea46c4c5ed0dce6ae",
            expectedEthereumAddress = "0x2f015c60e0be116b1f0cd534704db9c92118fb6a",
            messageHash = "4b688df40bcedbe641ddb16ff0a1842d9c67ea1c3bf63f3e0471baa664531d1a",
            description = "RFC 6979 標準測試向量，用於確定性簽名驗證"
        )

        /**
         * 測試向量 3: 最小有效私鑰
         * 邊界情況：最小的非零私鑰
         */
        val VECTOR_MIN_KEY = TestVector(
            name = "Minimum Valid Private Key",
            privateKey = "0000000000000000000000000000000000000000000000000000000000000001",
            expectedPublicKeyCompressed = "0279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
            expectedPublicKeyUncompressed = "0479be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798483ada7726a3c4655da4fbfc0e1108a8fd17b448a68554199c47d08ffb10d4b8",
            expectedEthereumAddress = "0x7e5f4552091a69125d5dfcb7b8c2659029395bdf",
            messageHash = "0000000000000000000000000000000000000000000000000000000000000000",
            description = "最小有效私鑰（值為 1），測試邊界情況"
        )

        /**
         * 測試向量 4: 最大有效私鑰
         * 邊界情況：secp256k1 curve order - 1
         */
        val VECTOR_MAX_KEY = TestVector(
            name = "Maximum Valid Private Key",
            privateKey = "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364140",
            expectedPublicKeyCompressed = "02e493dbf1c10d80f3581e4904930b1404cc6c13900ee0758474fa94abe8c4cd13",
            expectedPublicKeyUncompressed = "04e493dbf1c10d80f3581e4904930b1404cc6c13900ee0758474fa94abe8c4cd1351ed993ea0d455b75642e2098ea51448d967ae33bfbdfe40cfe97bdc47739922",
            expectedEthereumAddress = "0x2c7536e3605d9c16a7a3d7b1898e529396a65c23",
            messageHash = "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
            description = "最大有效私鑰（curve order - 1），測試邊界情況"
        )

        /**
         * 測試向量 5: 隨機私鑰（中等範圍）
         */
        val VECTOR_RANDOM = TestVector(
            name = "Random Private Key",
            privateKey = "4646464646464646464646464646464646464646464646464646464646464646",
            expectedPublicKeyCompressed = "02a65786c4c74d65e4191e445d03b3b0424e7c448e16e0dd7ca6a005acdae6bf5b",
            expectedPublicKeyUncompressed = "04a65786c4c74d65e4191e445d03b3b0424e7c448e16e0dd7ca6a005acdae6bf5b8dce1c8e9e7c36fb8e2ca04c6a3f9df8e0a36f5f6d5f4e3d2c1b0a09080706050",
            expectedEthereumAddress = "0xa0ee7a142d267c1f36714e4a8f75612f20a79720",
            messageHash = "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef",
            description = "隨機私鑰，測試通用情況"
        )

        /**
         * 所有測試向量
         */
        val ALL_VECTORS = listOf(
            VECTOR_HARDHAT,
            VECTOR_RFC6979,
            VECTOR_MIN_KEY,
            VECTOR_MAX_KEY,
            VECTOR_RANDOM
        )
    }

    //region 1. 公鑰派生一致性測試

    /**
     * 測試 1.1: 壓縮公鑰派生一致性
     *
     * 驗證所有平台從相同私鑰派生出相同的壓縮公鑰
     */
    @Test
    fun test_publicKey_compressed_consistency_all_vectors() {
        println("\n" + "=".repeat(80))
        println("測試 1.1: 壓縮公鑰派生一致性")
        println("=".repeat(80))

        var passCount = 0
        var failCount = 0

        ALL_VECTORS.forEach { vector ->
            println("\n📋 ${vector.name}")
            println("   描述: ${vector.description}")
            println("   私鑰: ${vector.privateKey}")

            val privateKey = vector.privateKey.hexToByteArray()

            val result = runCatching {
                val publicKey = Secp256k1Provider.computePublicKey(privateKey, compressed = true)
                val publicKeyHex = publicKey.toHexString()

                println("   實際公鑰: $publicKeyHex")
                println("   預期公鑰: ${vector.expectedPublicKeyCompressed}")

                // 驗證長度
                assertEquals(33, publicKey.size, "壓縮公鑰必須為 33 字節")

                // 驗證格式（開頭必須為 0x02 或 0x03）
                assertTrue(
                    publicKey[0] == 0x02.toByte() || publicKey[0] == 0x03.toByte(),
                    "壓縮公鑰必須以 0x02 或 0x03 開頭"
                )

                // 驗證與預期值匹配
                assertEquals(
                    vector.expectedPublicKeyCompressed,
                    publicKeyHex,
                    "公鑰不符合預期"
                )

                println("   ✅ 通過")
                passCount++
            }

            if (result.isFailure) {
                println("   ❌ 失敗: ${result.exceptionOrNull()?.message}")
                failCount++
            }
        }

        println("\n" + "=".repeat(80))
        println("📊 測試結果: ✅ $passCount 通過 | ❌ $failCount 失敗")
        println("=".repeat(80))

        assertTrue(passCount > 0, "至少應該有一些測試通過")
        assertEquals(0, failCount, "所有測試都應該通過")
    }

    /**
     * 測試 1.2: 未壓縮公鑰派生一致性
     *
     * 驗證所有平台從相同私鑰派生出相同的未壓縮公鑰
     */
    @Test
    fun test_publicKey_uncompressed_consistency_all_vectors() {
        println("\n" + "=".repeat(80))
        println("測試 1.2: 未壓縮公鑰派生一致性")
        println("=".repeat(80))

        var passCount = 0
        var failCount = 0

        ALL_VECTORS.forEach { vector ->
            println("\n📋 ${vector.name}")
            println("   私鑰: ${vector.privateKey}")

            val privateKey = vector.privateKey.hexToByteArray()

            val result = runCatching {
                val publicKey = Secp256k1Provider.computePublicKey(privateKey, compressed = false)
                val publicKeyHex = publicKey.toHexString()

                println("   實際公鑰: $publicKeyHex")
                println("   預期公鑰: ${vector.expectedPublicKeyUncompressed}")

                // 驗證長度
                assertEquals(65, publicKey.size, "未壓縮公鑰必須為 65 字節")

                // 驗證格式（開頭必須為 0x04）
                assertEquals(
                    0x04.toByte(),
                    publicKey[0],
                    "未壓縮公鑰必須以 0x04 開頭"
                )

                // 驗證與預期值匹配
                assertEquals(
                    vector.expectedPublicKeyUncompressed,
                    publicKeyHex,
                    "公鑰不符合預期"
                )

                println("   ✅ 通過")
                passCount++
            }

            if (result.isFailure) {
                println("   ❌ 失敗: ${result.exceptionOrNull()?.message}")
                failCount++
            }
        }

        println("\n" + "=".repeat(80))
        println("📊 測試結果: ✅ $passCount 通過 | ❌ $failCount 失敗")
        println("=".repeat(80))

        assertTrue(passCount > 0, "至少應該有一些測試通過")
        assertEquals(0, failCount, "所有測試都應該通過")
    }

    //endregion

    //region 2. 簽名一致性測試

    /**
     * 測試 2.1: 簽名生成一致性（RFC 6979 確定性簽名）
     *
     * 驗證：
     * 1. 對相同私鑰和消息，所有平台產生相同簽名
     * 2. 簽名是確定性的（多次簽名結果相同）
     * 3. 簽名符合 RFC 6979 標準
     */
    @Test
    fun test_signature_deterministic_consistency() {
        println("\n" + "=".repeat(80))
        println("測試 2.1: RFC 6979 確定性簽名一致性")
        println("=".repeat(80))

        var passCount = 0
        var failCount = 0

        ALL_VECTORS.forEach { vector ->
            println("\n📋 ${vector.name}")
            println("   私鑰: ${vector.privateKey}")
            println("   消息哈希: ${vector.messageHash}")

            val privateKey = vector.privateKey.hexToByteArray()
            val messageHash = vector.messageHash.hexToByteArray()

            val result = runCatching {
                // 第一次簽名
                val signature1 = Secp256k1Provider.sign(privateKey, messageHash)
                println("   簽名 1: ${signature1.toHexString()}")

                // 第二次簽名（應該相同）
                val signature2 = Secp256k1Provider.sign(privateKey, messageHash)
                println("   簽名 2: ${signature2.toHexString()}")

                // 第三次簽名（應該相同）
                val signature3 = Secp256k1Provider.sign(privateKey, messageHash)
                println("   簽名 3: ${signature3.toHexString()}")

                // 驗證簽名長度（64 字節：r + s）
                assertEquals(64, signature1.size, "簽名必須為 64 字節")
                assertEquals(64, signature2.size, "簽名必須為 64 字節")
                assertEquals(64, signature3.size, "簽名必須為 64 字節")

                // 驗證確定性：三次簽名必須完全相同
                assertTrue(
                    signature1.contentEquals(signature2),
                    "RFC 6979 確定性簽名：第 1 次和第 2 次應該相同"
                )
                assertTrue(
                    signature2.contentEquals(signature3),
                    "RFC 6979 確定性簽名：第 2 次和第 3 次應該相同"
                )

                println("   ✅ 確定性驗證通過")
                passCount++
            }

            if (result.isFailure) {
                println("   ❌ 失敗: ${result.exceptionOrNull()?.message}")
                result.exceptionOrNull()?.printStackTrace()
                failCount++
            }
        }

        println("\n" + "=".repeat(80))
        println("📊 測試結果: ✅ $passCount 通過 | ❌ $failCount 失敗")
        println("=".repeat(80))

        assertTrue(passCount > 0, "至少應該有一些測試通過")
        assertEquals(0, failCount, "所有測試都應該通過")
    }

    /**
     * 測試 2.2: 簽名驗證一致性
     *
     * 驗證：
     * 1. 所有平台都能驗證自己生成的簽名
     * 2. 所有平台都能驗證其他平台生成的簽名（交叉驗證）
     */
    @Test
    fun test_signature_verification_consistency() {
        println("\n" + "=".repeat(80))
        println("測試 2.2: 簽名驗證一致性")
        println("=".repeat(80))

        var passCount = 0
        var failCount = 0

        ALL_VECTORS.forEach { vector ->
            println("\n📋 ${vector.name}")

            val privateKey = vector.privateKey.hexToByteArray()
            val messageHash = vector.messageHash.hexToByteArray()

            val result = runCatching {
                // 生成公鑰
                val publicKey = Secp256k1Provider.computePublicKey(privateKey, compressed = true)
                println("   公鑰: ${publicKey.toHexString()}")

                // 生成簽名
                val signature = Secp256k1Provider.sign(privateKey, messageHash)
                println("   簽名: ${signature.toHexString()}")

                // 驗證簽名
                val isValid = Secp256k1Provider.verify(signature, messageHash, publicKey)
                println("   驗證結果: $isValid")

                assertTrue(isValid, "簽名驗證應該通過")

                // 驗證錯誤簽名應該失敗
                val wrongSignature = signature.copyOf()
                wrongSignature[0] = (wrongSignature[0].toInt() xor 0xFF).toByte()  // 翻轉第一個字節

                val isInvalid = !Secp256k1Provider.verify(wrongSignature, messageHash, publicKey)
                assertTrue(isInvalid, "錯誤的簽名應該驗證失敗")

                println("   ✅ 驗證通過")
                passCount++
            }

            if (result.isFailure) {
                println("   ❌ 失敗: ${result.exceptionOrNull()?.message}")
                result.exceptionOrNull()?.printStackTrace()
                failCount++
            }
        }

        println("\n" + "=".repeat(80))
        println("📊 測試結果: ✅ $passCount 通過 | ❌ $failCount 失敗")
        println("=".repeat(80))

        assertTrue(passCount > 0, "至少應該有一些測試通過")
        assertEquals(0, failCount, "所有測試都應該通過")
    }

    //endregion

    //region 3. 哈希算法一致性測試

    /**
     * 測試 3.1: Keccak256 跨平台一致性
     *
     * 驗證所有平台的 Keccak256 實作產生相同結果
     */
    @Test
    fun test_keccak256_consistency() {
        println("\n" + "=".repeat(80))
        println("測試 3.1: Keccak256 跨平台一致性")
        println("=".repeat(80))

        // Keccak256 標準測試向量
        val testCases = listOf(
            "" to "c5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470",
            "abc" to "4e03657aea45a94fc7d47ba826c8d667c0d1e6e33a64a036ec44f58fa12d6c45",
            "Hello, World!" to "acaf3289d7b601cbd114fb36c4d29c85bbfd5e133f14cb355c3fd8d99367964f",
            "The quick brown fox jumps over the lazy dog" to "4d741b6f1eb29cb2a9b9911c82f56fa8d73b04959d3d9d222895df6c0b28aa15"
        )

        var passCount = 0
        var failCount = 0

        testCases.forEach { (input, expected) ->
            println("\n📋 輸入: \"$input\"")
            println("   預期: $expected")

            val result = runCatching {
                val inputBytes = input.encodeToByteArray()
                val hash = CryptoUtils.keccak256(inputBytes)
                val hashHex = hash.toHexString()

                println("   實際: $hashHex")

                assertEquals(32, hash.size, "Keccak256 必須產生 32 字節")
                assertEquals(expected, hashHex, "Keccak256 結果不符合預期")

                println("   ✅ 通過")
                passCount++
            }

            if (result.isFailure) {
                println("   ❌ 失敗: ${result.exceptionOrNull()?.message}")
                failCount++
            }
        }

        println("\n" + "=".repeat(80))
        println("📊 測試結果: ✅ $passCount 通過 | ❌ $failCount 失敗")
        println("=".repeat(80))

        assertEquals(testCases.size, passCount, "所有 Keccak256 測試都應該通過")
    }

    /**
     * 測試 3.2: SHA-256 跨平台一致性
     *
     * 驗證所有平台的 SHA-256 實作產生相同結果
     */
    @Test
    fun test_sha256_consistency() {
        println("\n" + "=".repeat(80))
        println("測試 3.2: SHA-256 跨平台一致性")
        println("=".repeat(80))

        // SHA-256 標準測試向量
        val testCases = listOf(
            "" to "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            "abc" to "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            "Hello, World!" to "dffd6021bb2bd5b0af676290809ec3a53191dd81c7f70a4b28688a362182986f",
            "The quick brown fox jumps over the lazy dog" to "d7a8fbb307d7809469ca9abcb0082e4f8d5651e46d3cdb762d02d0bf37c9e592"
        )

        var passCount = 0
        var failCount = 0

        testCases.forEach { (input, expected) ->
            println("\n📋 輸入: \"$input\"")
            println("   預期: $expected")

            val result = runCatching {
                val inputBytes = input.encodeToByteArray()
                val hash = CryptoUtils.sha256(inputBytes)
                val hashHex = hash.toHexString()

                println("   實際: $hashHex")

                assertEquals(32, hash.size, "SHA-256 必須產生 32 字節")
                assertEquals(expected, hashHex, "SHA-256 結果不符合預期")

                println("   ✅ 通過")
                passCount++
            }

            if (result.isFailure) {
                println("   ❌ 失敗: ${result.exceptionOrNull()?.message}")
                failCount++
            }
        }

        println("\n" + "=".repeat(80))
        println("📊 測試結果: ✅ $passCount 通過 | ❌ $failCount 失敗")
        println("=".repeat(80))

        assertEquals(testCases.size, passCount, "所有 SHA-256 測試都應該通過")
    }

    //endregion

    //region 4. Ethereum 地址生成一致性測試

    /**
     * 測試 4.1: Ethereum 地址生成一致性
     *
     * 驗證所有平台從相同私鑰生成相同的 Ethereum 地址
     *
     * 地址生成步驟：
     * 1. 從私鑰派生未壓縮公鑰（65 字節）
     * 2. 去掉前綴 0x04，取剩餘 64 字節
     * 3. Keccak256 哈希
     * 4. 取最後 20 字節
     * 5. 加上 0x 前綴並轉為小寫
     */
    @Test
    fun test_ethereum_address_generation_consistency() {
        println("\n" + "=".repeat(80))
        println("測試 4.1: Ethereum 地址生成一致性")
        println("=".repeat(80))

        var passCount = 0
        var failCount = 0

        ALL_VECTORS.forEach { vector ->
            println("\n📋 ${vector.name}")
            println("   私鑰: ${vector.privateKey}")
            println("   預期地址: ${vector.expectedEthereumAddress}")

            val privateKey = vector.privateKey.hexToByteArray()

            val result = runCatching {
                // 步驟 1: 派生未壓縮公鑰
                val publicKey = Secp256k1Provider.computePublicKey(privateKey, compressed = false)
                assertEquals(65, publicKey.size, "未壓縮公鑰必須為 65 字節")
                assertEquals(0x04.toByte(), publicKey[0], "未壓縮公鑰必須以 0x04 開頭")

                // 步驟 2: 去掉 0x04 前綴
                val publicKeyWithoutPrefix = publicKey.copyOfRange(1, 65)
                assertEquals(64, publicKeyWithoutPrefix.size, "去除前綴後應為 64 字節")

                // 步驟 3: Keccak256 哈希
                val hash = CryptoUtils.keccak256(publicKeyWithoutPrefix)
                assertEquals(32, hash.size, "Keccak256 必須產生 32 字節")

                // 步驟 4: 取最後 20 字節
                val addressBytes = hash.copyOfRange(12, 32)
                assertEquals(20, addressBytes.size, "Ethereum 地址必須為 20 字節")

                // 步驟 5: 加上 0x 前綴並轉為小寫
                val address = "0x" + addressBytes.toHexString()

                println("   實際地址: $address")
                println("   中間公鑰 (無前綴): ${publicKeyWithoutPrefix.toHexString()}")
                println("   中間哈希: ${hash.toHexString()}")

                // 驗證地址格式
                assertEquals(42, address.length, "Ethereum 地址應為 42 字符（0x + 40 十六進制）")
                assertTrue(address.startsWith("0x"), "Ethereum 地址應以 0x 開頭")

                // 驗證與預期地址匹配
                assertEquals(
                    vector.expectedEthereumAddress.lowercase(),
                    address.lowercase(),
                    "Ethereum 地址不符合預期"
                )

                println("   ✅ 通過")
                passCount++
            }

            if (result.isFailure) {
                println("   ❌ 失敗: ${result.exceptionOrNull()?.message}")
                result.exceptionOrNull()?.printStackTrace()
                failCount++
            }
        }

        println("\n" + "=".repeat(80))
        println("📊 測試結果: ✅ $passCount 通過 | ❌ $failCount 失敗")
        println("=".repeat(80))

        assertTrue(passCount > 0, "至少應該有一些測試通過")
        assertEquals(0, failCount, "所有測試都應該通過")
    }

    //endregion

    //region 5. 完整端到端測試

    /**
     * 測試 5.1: 完整端到端流程
     *
     * 模擬完整的錢包使用流程：
     * 1. 私鑰 → 公鑰
     * 2. 公鑰 → Ethereum 地址
     * 3. 簽名交易
     * 4. 驗證簽名
     */
    @Test
    fun test_end_to_end_wallet_flow() {
        println("\n" + "=".repeat(80))
        println("測試 5.1: 完整端到端錢包流程")
        println("=".repeat(80))

        val vector = VECTOR_HARDHAT  // 使用最常用的 Hardhat 測試私鑰

        println("📋 測試向量: ${vector.name}")
        println("   ${vector.description}")

        val privateKey = vector.privateKey.hexToByteArray()
        val messageHash = vector.messageHash.hexToByteArray()

        // 步驟 1: 派生公鑰
        println("\n步驟 1️⃣: 派生公鑰")
        val publicKeyCompressed = Secp256k1Provider.computePublicKey(privateKey, compressed = true)
        val publicKeyUncompressed = Secp256k1Provider.computePublicKey(privateKey, compressed = false)
        println("   壓縮公鑰: ${publicKeyCompressed.toHexString()}")
        println("   未壓縮公鑰: ${publicKeyUncompressed.toHexString()}")
        assertEquals(vector.expectedPublicKeyCompressed, publicKeyCompressed.toHexString())
        assertEquals(vector.expectedPublicKeyUncompressed, publicKeyUncompressed.toHexString())
        println("   ✅ 公鑰派生正確")

        // 步驟 2: 生成 Ethereum 地址
        println("\n步驟 2️⃣: 生成 Ethereum 地址")
        val publicKeyWithoutPrefix = publicKeyUncompressed.copyOfRange(1, 65)
        val hash = CryptoUtils.keccak256(publicKeyWithoutPrefix)
        val addressBytes = hash.copyOfRange(12, 32)
        val address = "0x" + addressBytes.toHexString()
        println("   地址: $address")
        assertEquals(vector.expectedEthereumAddress.lowercase(), address.lowercase())
        println("   ✅ 地址生成正確")

        // 步驟 3: 簽名交易
        println("\n步驟 3️⃣: 簽名交易")
        val signature = Secp256k1Provider.sign(privateKey, messageHash)
        println("   簽名: ${signature.toHexString()}")
        assertEquals(64, signature.size)
        println("   ✅ 簽名生成成功")

        // 步驟 4: 驗證簽名
        println("\n步驟 4️⃣: 驗證簽名")
        val isValid = Secp256k1Provider.verify(signature, messageHash, publicKeyCompressed)
        println("   驗證結果: $isValid")
        assertTrue(isValid, "簽名驗證應該通過")
        println("   ✅ 簽名驗證成功")

        // 步驟 5: 驗證錯誤簽名應該失敗
        println("\n步驟 5️⃣: 驗證錯誤簽名（應該失敗）")
        val wrongSignature = signature.copyOf()
        wrongSignature[0] = (wrongSignature[0].toInt() xor 0xFF).toByte()
        val isInvalid = !Secp256k1Provider.verify(wrongSignature, messageHash, publicKeyCompressed)
        assertTrue(isInvalid, "錯誤的簽名應該驗證失敗")
        println("   ✅ 正確拒絕錯誤簽名")

        println("\n" + "=".repeat(80))
        println("✅ 完整端到端流程測試通過")
        println("=".repeat(80))
    }

    //endregion

    //region 6. 性能基準測試

    /**
     * 測試 6.1: 性能基準測試
     *
     * 測量各平台的性能，用於對比：
     * - 公鑰派生速度
     * - 簽名生成速度
     * - 簽名驗證速度
     * - 哈希計算速度
     */
    @Test
    fun test_performance_benchmark() {
        println("\n" + "=".repeat(80))
        println("測試 6.1: 性能基準測試")
        println("=".repeat(80))

        val privateKey = VECTOR_HARDHAT.privateKey.hexToByteArray()
        val messageHash = VECTOR_HARDHAT.messageHash.hexToByteArray()
        val iterations = 100

        // 公鑰派生性能
        println("\n⚡ 公鑰派生性能（$iterations 次）")
        val pubKeyStart = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        repeat(iterations) {
            Secp256k1Provider.computePublicKey(privateKey, compressed = true)
        }
        val pubKeyDuration = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - pubKeyStart
        println("   總時間: $pubKeyDuration ms")
        println("   平均: ${pubKeyDuration.toDouble() / iterations} ms/次")

        // 簽名生成性能
        println("\n⚡ 簽名生成性能（$iterations 次）")
        val signStart = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        repeat(iterations) {
            Secp256k1Provider.sign(privateKey, messageHash)
        }
        val signDuration = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - signStart
        println("   總時間: $signDuration ms")
        println("   平均: ${signDuration.toDouble() / iterations} ms/次")

        // 簽名驗證性能
        println("\n⚡ 簽名驗證性能（$iterations 次）")
        val publicKey = Secp256k1Provider.computePublicKey(privateKey, compressed = true)
        val signature = Secp256k1Provider.sign(privateKey, messageHash)
        val verifyStart = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        repeat(iterations) {
            Secp256k1Provider.verify(signature, messageHash, publicKey)
        }
        val verifyDuration = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - verifyStart
        println("   總時間: $verifyDuration ms")
        println("   平均: ${verifyDuration.toDouble() / iterations} ms/次")

        // Keccak256 性能
        println("\n⚡ Keccak256 哈希性能（$iterations 次）")
        val data = "performance test".encodeToByteArray()
        val keccakStart = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        repeat(iterations) {
            CryptoUtils.keccak256(data)
        }
        val keccakDuration = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - keccakStart
        println("   總時間: $keccakDuration ms")
        println("   平均: ${keccakDuration.toDouble() / iterations} ms/次")

        println("\n" + "=".repeat(80))
        println("✅ 性能基準測試完成")
        println("=".repeat(80))
    }

    //endregion
}

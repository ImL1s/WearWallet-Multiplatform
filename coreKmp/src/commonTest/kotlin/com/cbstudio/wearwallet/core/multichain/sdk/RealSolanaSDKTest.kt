package com.cbstudio.wearwallet.core.multichain.sdk

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import io.github.iml1s.crypto.Base58

import com.cbstudio.wearwallet.core.multichain.solana.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * RealSolanaSDK 測試
 */
class RealSolanaSDKTest {

    private lateinit var sdk: RealSolanaSDK

    @BeforeTest
    fun setup() {
        sdk = RealSolanaSDK()
    }

    @Test
    fun testSDKInitialization() = runTest {
        assertFalse(sdk.isInitialized(), "SDK 應該尚未初始化")

        val config = SDKConfig(
            rpcUrl = "https://api.devnet.solana.com",
            network = "devnet",
            apiKey = null,
            timeout = 30000
        )

        val result = sdk.initialize(config)
        assertTrue(result is Result.Success, "SDK 初始化應該成功")
        assertTrue(sdk.isInitialized(), "SDK 應該已初始化")

        assertEquals(MultiChainType.SOLANA, sdk.chainType)
    }

    @Test
    fun testAddressValidation() {
        // 有效的 Solana 地址
        val validAddress = "9VHphpWFmUxVHxzWyeYJYYbQADWZ7X6PLzyWER8Lc3k2"
        val validResult = sdk.validateAddress(validAddress)

        assertTrue(validResult is Result.Success)
        assertTrue((validResult as Result.Success).data.isValid)

        // 無效的地址
        val invalidAddresses = listOf(
            "",  // 空字串
            "invalid",  // 太短
            "11111111111111111111111111111111111111111111111",  // 太長
            "9VHphpWFmUxVHxzWyeYJYYbQADWZ7X6PLzyWER8Lc3k@",  // 包含無效字符
        )

        invalidAddresses.forEach { addr ->
            val result = sdk.validateAddress(addr)
            assertTrue(result is Result.Success)
            assertFalse((result as Result.Success).data.isValid, "地址 '$addr' 應該無效")
        }
    }

    @Test
    fun testBase58Encoding() {
        val testData = "Hello Solana!".encodeToByteArray()
        val encoded = Base58.encode(testData)

        assertNotNull(encoded)
        assertTrue(encoded.isNotEmpty())

        // 測試解碼
        val decoded = Base58.decode(encoded)
        assertContentEquals(testData, decoded)
    }

    @Test
    fun testBase58KnownValues() {
        // 測試已知的 Base58 編碼值
        val testCases = mapOf(
            "" to "",
            "61" to "2g",  // 'a' in hex
            "626262" to "a3gV",  // 'bbb' in hex
            "636363" to "aPEr",  // 'ccc' in hex
            "00eb15231dfceb60925886b67d065299925915aeb172c06647" to "1NS17iag9jJgTHD1VXjvLCEnZuQ3rJDE9L"
        )

        testCases.forEach { (hex, expected) ->
            if (hex.isNotEmpty()) {
                val bytes = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                val encoded = Base58.encode(bytes)
                assertEquals(expected, encoded, "編碼 $hex 應該得到 $expected")

                val decoded = Base58.decode(encoded)
                assertContentEquals(bytes, decoded, "解碼 $expected 應該得到原始數據")
            }
        }
    }

    @Test
    fun testCompactU16Encoding() {
        val testCases = mapOf(
            0 to listOf<Byte>(0x00),
            127 to listOf<Byte>(0x7f),
            128 to listOf<Byte>(0x80.toByte(), 0x01),
            255 to listOf<Byte>(0xff.toByte(), 0x01),
            256 to listOf<Byte>(0x80.toByte(), 0x02),
            16384 to listOf<Byte>(0x80.toByte(), 0x80.toByte(), 0x01)
        )

        testCases.forEach { (value, expected) ->
            val encoded = encodeCompactU16(value)
            assertEquals(expected, encoded, "編碼 $value 應該得到 $expected")
        }
    }

    @Test
    fun testCompactU16Decoding() {
        val testCases = mapOf(
            byteArrayOf(0x00) to 0,
            byteArrayOf(0x7f) to 127,
            byteArrayOf(0x80.toByte(), 0x01) to 128,
            byteArrayOf(0xff.toByte(), 0x01) to 255,
            byteArrayOf(0x80.toByte(), 0x02) to 256,
            byteArrayOf(0x80.toByte(), 0x80.toByte(), 0x01) to 16384
        )

        testCases.forEach { (bytes, expected) ->
            val (decoded, offset) = decodeCompactU16(bytes, 0)
            assertEquals(expected, decoded, "解碼 ${bytes.contentToString()} 應該得到 $expected")
            assertEquals(bytes.size, offset, "偏移量應該等於 bytes 長度")
        }
    }

    @Test
    fun testTransactionSerialization() {
        // 創建簡單的交易
        val signer = "9VHphpWFmUxVHxzWyeYJYYbQADWZ7X6PLzyWER8Lc3k2"
        val recipient = "BRLsMczKuaR5w9vSubF4j8HwEGGprVAyyVgS4EX7DKEg"
        val recentBlockhash = "GH7Tvz8cr3mvQC1ZZzNdKHBhMEJW7RzDEUJJgZCk6zj"

        val transaction = SolanaTransactionBuilder()
            .addSigner(signer)
            .addInstruction(
                programId = "11111111111111111111111111111111",
                accounts = listOf(
                    AccountMeta(signer, isSigner = true, isWritable = true),
                    AccountMeta(recipient, isSigner = false, isWritable = true)
                ),
                data = ByteArray(12) { 0 } // 簡化的 transfer 數據
            )
            .setRecentBlockhash(recentBlockhash)
            .build()

        // 序列化
        val serialized = transaction.serialize()
        assertNotNull(serialized)
        assertTrue(serialized.isNotEmpty())

        // 反序列化驗證
        val deserialized = com.cbstudio.wearwallet.core.multichain.solana.SolanaTransaction.deserialize(serialized)
        assertNotNull(deserialized)

        // 驗證數據結構
        assertEquals(transaction.message.accountKeys.size, deserialized.message.accountKeys.size)
        assertEquals(transaction.message.recentBlockhash, deserialized.message.recentBlockhash)
        assertEquals(transaction.message.instructions.size, deserialized.message.instructions.size)
    }

    @Test
    fun testMessageSerialization() {
        val header = MessageHeader(
            numRequiredSignatures = 1,
            numReadonlySignedAccounts = 0,
            numReadonlyUnsignedAccounts = 1
        )

        val accountKeys = listOf(
            "9VHphpWFmUxVHxzWyeYJYYbQADWZ7X6PLzyWER8Lc3k2",
            "BRLsMczKuaR5w9vSubF4j8HwEGGprVAyyVgS4EX7DKEg",
            "11111111111111111111111111111111"
        )

        val recentBlockhash = "GH7Tvz8cr3mvQC1ZZzNdKHBhMEJW7RzDEUJJgZCk6zj"

        val instructions = listOf(
            CompiledInstruction(
                programIdIndex = 2,
                accountIndexes = listOf(0, 1),
                data = ByteArray(12) { 0 }
            )
        )

        val message = SolanaMessage(header, accountKeys, recentBlockhash, instructions)

        // 序列化
        val serialized = message.serialize()
        assertNotNull(serialized)

        // 反序列化
        val deserialized = SolanaMessage.deserialize(serialized, 0)
        assertNotNull(deserialized)

        // 驗證
        assertEquals(message.header.numRequiredSignatures, deserialized.header.numRequiredSignatures)
        assertEquals(message.accountKeys.size, deserialized.accountKeys.size)
        assertEquals(message.recentBlockhash, deserialized.recentBlockhash)
        assertEquals(message.instructions.size, deserialized.instructions.size)
    }

    @Test
    fun testTransactionBuilder() {
        val signer = "9VHphpWFmUxVHxzWyeYJYYbQADWZ7X6PLzyWER8Lc3k2"
        val recipient = "BRLsMczKuaR5w9vSubF4j8HwEGGprVAyyVgS4EX7DKEg"
        val systemProgram = "11111111111111111111111111111111"
        val recentBlockhash = "GH7Tvz8cr3mvQC1ZZzNdKHBhMEJW7RzDEUJJgZCk6zj"

        val transaction = SolanaTransactionBuilder()
            .addSigner(signer)
            .addInstruction(
                programId = systemProgram,
                accounts = listOf(
                    AccountMeta(signer, isSigner = true, isWritable = true),
                    AccountMeta(recipient, isSigner = false, isWritable = true)
                ),
                data = ByteArray(12)
            )
            .setRecentBlockhash(recentBlockhash)
            .build()

        // 驗證 transaction 結構
        assertNotNull(transaction)
        assertEquals(1, transaction.signatures.size)
        assertEquals(1, transaction.message.header.numRequiredSignatures)

        // 驗證 accountKeys 順序: signers first
        assertEquals(signer, transaction.message.accountKeys[0])
        assertTrue(transaction.message.accountKeys.contains(recipient))
        assertTrue(transaction.message.accountKeys.contains(systemProgram))
    }

    @Test
    fun testEstimateTransactionFee() = runTest {
        val request = TransactionRequest(
            fromAddress = "9VHphpWFmUxVHxzWyeYJYYbQADWZ7X6PLzyWER8Lc3k2",
            toAddress = "BRLsMczKuaR5w9vSubF4j8HwEGGprVAyyVgS4EX7DKEg",
            amount = "1.0",
            priority = TransactionPriority.NORMAL,
            memo = null
        )

        val result = sdk.estimateTransactionFee(request)

        assertTrue(result is Result.Success)
        val fee = (result as Result.Success).data
        assertEquals("5000", fee.gasLimit)
        assertEquals("0.000005", fee.estimatedCost)
    }

    @Test
    fun testCapabilities() {
        val capabilities = sdk.capabilities

        assertTrue(capabilities.contains(SDKCapability.BALANCE_QUERY))
        assertTrue(capabilities.contains(SDKCapability.TRANSACTION_CREATION))
        assertTrue(capabilities.contains(SDKCapability.TRANSACTION_SIGNING))
        assertTrue(capabilities.contains(SDKCapability.TRANSACTION_BROADCAST))
        assertTrue(capabilities.contains(SDKCapability.ADDRESS_VALIDATION))
        assertTrue(capabilities.contains(SDKCapability.TRANSACTION_HISTORY))
        assertTrue(capabilities.contains(SDKCapability.NFT_OPERATIONS))
        assertTrue(capabilities.contains(SDKCapability.DEFI_OPERATIONS))
        assertTrue(capabilities.contains(SDKCapability.STAKING_OPERATIONS))
    }

    @AfterTest
    fun cleanup() = runTest {
        sdk.cleanup()
        assertFalse(sdk.isInitialized(), "清理後 SDK 應該未初始化")
    }
}

/**
 * Base58 編碼測試
 */
class Base58Test {

    @Test
    fun testEncodeEmptyArray() {
        val encoded = Base58.encode(ByteArray(0))
        assertEquals("", encoded)
    }

    @Test
    fun testDecodeEmptyString() {
        val decoded = Base58.decode("")
        assertEquals(0, decoded?.size ?: -1)
    }

    @Test
    fun testEncodeDecode() {
        val testStrings = listOf(
            "Hello, World!",
            "Solana",
            "12345",
            "Test123",
            "🚀"
        )

        testStrings.forEach { str ->
            val bytes = str.encodeToByteArray()
            val encoded = Base58.encode(bytes)
            val decoded = Base58.decode(encoded)
            assertNotNull(decoded)

            assertContentEquals(bytes, decoded, "編碼解碼 '$str' 應該得到相同結果")
        }
    }

    @Test
    fun testLeadingZeros() {
        // 測試前導零
        val bytes = byteArrayOf(0, 0, 0, 1, 2, 3)
        val encoded = Base58.encode(bytes)

        assertTrue(encoded.startsWith("111"), "Base58 編碼的前導零應該是 '1'")

        val decoded = Base58.decode(encoded)
        assertNotNull(decoded)
        assertContentEquals(bytes, decoded)
    }

    @Test
    fun testInvalidBase58Character() {
        assertFails {
            Base58.decode("Invalid0Character")  // '0' 不在 Base58 字母表中
        }
    }
}

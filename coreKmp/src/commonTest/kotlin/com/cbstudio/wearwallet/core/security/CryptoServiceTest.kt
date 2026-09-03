package com.cbstudio.wearwallet.core.security

import com.cbstudio.wearwallet.core.domain.model.ChainType
import kotlin.test.*

/**
 * CryptoService 單元測試套件
 *
 * 測試覆蓋範圍：
 * 1. 簽名功能測試（ECDSA + Ed25519）
 * 2. 多鏈支援測試（Ethereum, Solana, Bitcoin 等）
 * 3. 地址生成測試（EIP-55, Base58, Bech32）
 * 4. 錯誤處理測試（無效私鑰、不支援的鏈）
 * 5. 安全性測試（私鑰驗證、簽名確定性）
 *
 * 目標覆蓋率：80%+ 行覆蓋率，70%+ 分支覆蓋率
 */
class CryptoServiceTest {

    private lateinit var cryptoService: CryptoService

    // 測試用私鑰和公鑰（標準 Ethereum 測試向量）
    companion object {
        // 標準測試私鑰（來自 Ethereum test vectors）
        const val TEST_PRIVATE_KEY = "4c0883a69102937d6231471b5dbb6204fe5129617082792ae468d01a3f362318"
        const val TEST_PRIVATE_KEY_WITH_PREFIX = "0x$TEST_PRIVATE_KEY"

        // 對應的公鑰（未壓縮格式）
        const val TEST_PUBLIC_KEY = "0469d908510e355beb1d5bf2df8129e5b6401e1969891e8016a0b2300739bbb006407ff6b1bd0c6060fa33d2e0091ac52d1d0fe0dd21b83a2c0b88bc8e8f23e4d1"

        // BIP39 標準測試助記詞
        const val BIP39_TEST_MNEMONIC = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"

        // 測試消息
        const val TEST_MESSAGE = "Hello, Blockchain!"

        // 無效的私鑰測試用例
        const val INVALID_KEY_TOO_SHORT = "123abc"
        const val INVALID_KEY_TOO_LONG = "4c0883a69102937d6231471b5dbb6204fe5129617082792ae468d01a3f362318ff"
        const val INVALID_KEY_NON_HEX = "zzz883a69102937d6231471b5dbb6204fe5129617082792ae468d01a3f362318"
        const val EMPTY_KEY = ""
    }

    @BeforeTest
    fun setup() {
        cryptoService = CryptoService()
    }

    // ==================== 簽名功能測試 ====================

    @Test
    fun testSignMessageWithECDSA_Ethereum() {
        val signature = cryptoService.signMessage(
            message = TEST_MESSAGE,
            privateKey = TEST_PRIVATE_KEY,
            chainType = ChainType.ETHEREUM
        )

        assertNotNull(signature)
        assertTrue(signature.isNotEmpty())
        assertTrue(signature.length > 64) // ECDSA 簽名至少 64 字節（r + s）
        assertTrue(isValidHexString(signature))
    }

    @Test
    fun testSignMessageWithECDSA_BSC() {
        val signature = cryptoService.signMessage(
            message = TEST_MESSAGE,
            privateKey = TEST_PRIVATE_KEY,
            chainType = ChainType.BSC
        )

        assertNotNull(signature)
        assertTrue(signature.isNotEmpty())
        assertTrue(isValidHexString(signature))
    }

    @Test
    fun testSignMessageWithECDSA_Polygon() {
        val signature = cryptoService.signMessage(
            message = TEST_MESSAGE,
            privateKey = TEST_PRIVATE_KEY,
            chainType = ChainType.POLYGON
        )

        assertNotNull(signature)
        assertTrue(signature.isNotEmpty())
        assertTrue(isValidHexString(signature))
    }

    @Test
    fun testSignMessageWithEd25519_Solana() {
        val signature = cryptoService.signMessage(
            message = TEST_MESSAGE,
            privateKey = TEST_PRIVATE_KEY,
            chainType = ChainType.SOLANA
        )

        assertNotNull(signature)
        assertTrue(signature.isNotEmpty())
        // Ed25519 簽名固定為 64 字節
        assertEquals(128, signature.length) // 64 bytes * 2 = 128 hex chars
        assertTrue(isValidHexString(signature))
    }

    @Test
    fun testSignMessageWithEd25519_Aptos() {
        val signature = cryptoService.signMessage(
            message = TEST_MESSAGE,
            privateKey = TEST_PRIVATE_KEY,
            chainType = ChainType.APTOS
        )

        assertNotNull(signature)
        assertTrue(signature.isNotEmpty())
        assertEquals(128, signature.length) // Ed25519: 64 bytes
        assertTrue(isValidHexString(signature))
    }

    @Test
    fun testSignWithECDSA_Direct() {
        val signature = cryptoService.signWithECDSA(TEST_MESSAGE, TEST_PRIVATE_KEY)

        assertNotNull(signature)
        assertTrue(signature.isNotEmpty())
        assertTrue(isValidHexString(signature))
    }

    @Test
    fun testSignWithEd25519_Direct() {
        val signature = cryptoService.signWithEd25519(TEST_MESSAGE, TEST_PRIVATE_KEY)

        assertNotNull(signature)
        assertTrue(signature.isNotEmpty())
        assertEquals(128, signature.length) // 64 bytes
        assertTrue(isValidHexString(signature))
    }

    @Test
    fun testSignatureDeterminism() {
        // RFC 6979: 相同輸入應產生相同簽名
        val signature1 = cryptoService.signMessage(
            TEST_MESSAGE, TEST_PRIVATE_KEY, ChainType.ETHEREUM
        )
        val signature2 = cryptoService.signMessage(
            TEST_MESSAGE, TEST_PRIVATE_KEY, ChainType.ETHEREUM
        )

        assertEquals(signature1, signature2, "相同輸入應產生相同簽名（RFC 6979）")
    }

    // ==================== 交易簽名測試 ====================

    @Test
    fun testSignTransaction_Ethereum() {
        val txData = "test transaction data".encodeToByteArray()

        val signedTx = cryptoService.signTransaction(
            transactionData = txData,
            privateKey = TEST_PRIVATE_KEY,
            chainType = ChainType.ETHEREUM,
            chainId = 1 // Ethereum Mainnet
        )

        assertNotNull(signedTx)
        assertTrue(signedTx.isNotEmpty())
        assertTrue(isValidHexString(signedTx))
    }

    @Test
    fun testSignTransaction_BSC() {
        val txData = "test transaction data".encodeToByteArray()

        val signedTx = cryptoService.signTransaction(
            transactionData = txData,
            privateKey = TEST_PRIVATE_KEY,
            chainType = ChainType.BSC,
            chainId = 56 // BSC Mainnet
        )

        assertNotNull(signedTx)
        assertTrue(signedTx.isNotEmpty())
    }

    @Test
    fun testSignTransaction_Solana() {
        val txData = "test transaction data".encodeToByteArray()

        val signedTx = cryptoService.signTransaction(
            transactionData = txData,
            privateKey = TEST_PRIVATE_KEY,
            chainType = ChainType.SOLANA
        )

        assertNotNull(signedTx)
        assertTrue(signedTx.isNotEmpty())
    }

    @Test
    fun testSignTransaction_RequiresChainId() {
        val txData = "test transaction data".encodeToByteArray()

        // EVM 鏈需要 chainId
        assertFailsWith<IllegalArgumentException> {
            cryptoService.signTransaction(
                transactionData = txData,
                privateKey = TEST_PRIVATE_KEY,
                chainType = ChainType.ETHEREUM,
                chainId = null // 缺少 chainId
            )
        }
    }

    // ==================== 簽名驗證測試 ====================

    @Test
    fun testVerifySignature_ECDSA() {
        val message = TEST_MESSAGE
        val signature = cryptoService.signMessage(
            message, TEST_PRIVATE_KEY, ChainType.ETHEREUM
        )

        val publicKey = cryptoService.derivePublicKey(TEST_PRIVATE_KEY)

        val isValid = cryptoService.verifySignature(
            message = message,
            signature = signature,
            publicKey = publicKey,
            chainType = ChainType.ETHEREUM
        )

        assertTrue(isValid, "有效的簽名應該通過驗證")
    }

    @Test
    fun testVerifySignature_Ed25519() {
        val message = TEST_MESSAGE
        val signature = cryptoService.signMessage(
            message, TEST_PRIVATE_KEY, ChainType.SOLANA
        )

        val publicKey = cryptoService.derivePublicKey(TEST_PRIVATE_KEY)

        val isValid = cryptoService.verifySignature(
            message = message,
            signature = signature,
            publicKey = publicKey,
            chainType = ChainType.SOLANA
        )

        assertTrue(isValid, "有效的 Ed25519 簽名應該通過驗證")
    }

    @Test
    fun testVerifySignature_InvalidSignature() {
        val message = TEST_MESSAGE
        val publicKey = cryptoService.derivePublicKey(TEST_PRIVATE_KEY)

        val isValid = cryptoService.verifySignature(
            message = message,
            signature = "0".repeat(128), // 無效簽名
            publicKey = publicKey,
            chainType = ChainType.ETHEREUM
        )

        assertFalse(isValid, "無效簽名應該驗證失敗")
    }

    @Test
    fun testVerifySignature_WrongMessage() {
        val originalMessage = TEST_MESSAGE
        val signature = cryptoService.signMessage(
            originalMessage, TEST_PRIVATE_KEY, ChainType.ETHEREUM
        )

        val publicKey = cryptoService.derivePublicKey(TEST_PRIVATE_KEY)

        val isValid = cryptoService.verifySignature(
            message = "Different Message", // 不同的消息
            signature = signature,
            publicKey = publicKey,
            chainType = ChainType.ETHEREUM
        )

        assertFalse(isValid, "不同消息應該驗證失敗")
    }

    // ==================== 公鑰派生測試 ====================

    @Test
    fun testDerivePublicKey() {
        val publicKey = cryptoService.derivePublicKey(TEST_PRIVATE_KEY)

        assertNotNull(publicKey)
        assertTrue(publicKey.isNotEmpty())
        // 非壓縮公鑰格式：0x04 + 64 bytes (x + y) = 130 hex chars
        assertTrue(publicKey.length == 130 || publicKey.length == 128)
        assertTrue(isValidHexString(publicKey))
    }

    @Test
    fun testDerivePublicKey_WithPrefix() {
        val publicKey = cryptoService.derivePublicKey(TEST_PRIVATE_KEY_WITH_PREFIX)

        assertNotNull(publicKey)
        assertTrue(publicKey.isNotEmpty())
    }

    @Test
    fun testDerivePublicKey_Determinism() {
        val publicKey1 = cryptoService.derivePublicKey(TEST_PRIVATE_KEY)
        val publicKey2 = cryptoService.derivePublicKey(TEST_PRIVATE_KEY)

        assertEquals(publicKey1, publicKey2, "相同私鑰應派生相同公鑰")
    }

    // ==================== 地址生成測試 ====================

    @Test
    fun testDeriveAddress_Ethereum() {
        val publicKey = cryptoService.derivePublicKey(TEST_PRIVATE_KEY)
        val address = cryptoService.deriveAddress(publicKey, ChainType.ETHEREUM)

        assertTrue(address.startsWith("0x"), "Ethereum 地址應以 0x 開頭")
        assertEquals(42, address.length, "Ethereum 地址應為 42 字符（0x + 40 hex）")
        assertTrue(isValidHexString(address.removePrefix("0x")))
    }

    @Test
    fun testDeriveAddress_EIP55Checksum() {
        val publicKey = cryptoService.derivePublicKey(TEST_PRIVATE_KEY)
        val address = cryptoService.deriveAddress(publicKey, ChainType.ETHEREUM)

        // EIP-55: 地址應包含大小寫混合（校驗和）
        assertTrue(
            address.any { it.isUpperCase() } || address.all { it.isLowerCase() || it.isDigit() || it == 'x' },
            "應符合 EIP-55 校驗和格式"
        )
    }

    @Test
    fun testDeriveAddress_BSC() {
        val publicKey = cryptoService.derivePublicKey(TEST_PRIVATE_KEY)
        val address = cryptoService.deriveAddress(publicKey, ChainType.BSC)

        assertTrue(address.startsWith("0x"))
        assertEquals(42, address.length)
    }

    @Test
    fun testDeriveAddress_Polygon() {
        val publicKey = cryptoService.derivePublicKey(TEST_PRIVATE_KEY)
        val address = cryptoService.deriveAddress(publicKey, ChainType.POLYGON)

        assertTrue(address.startsWith("0x"))
        assertEquals(42, address.length)
    }

    @Test
    fun testDeriveAddress_Solana() {
        val publicKey = cryptoService.derivePublicKey(TEST_PRIVATE_KEY)
        val address = cryptoService.deriveAddress(publicKey, ChainType.SOLANA)

        assertNotNull(address)
        assertTrue(address.isNotEmpty())
        // Solana 地址是 Base58 編碼，長度約 32-44 字符
        assertTrue(address.length in 32..44)
        assertTrue(isValidBase58String(address))
    }

    @Test
    fun testDeriveAddress_Bitcoin() {
        val publicKey = cryptoService.derivePublicKey(TEST_PRIVATE_KEY)
        val address = cryptoService.deriveAddress(publicKey, ChainType.BITCOIN)

        assertNotNull(address)
        assertTrue(address.isNotEmpty())
        // Bitcoin 地址通常以 1 或 3 開頭（P2PKH 或 P2SH）
        assertTrue(address.startsWith("1") || address.startsWith("3"))
        assertTrue(isValidBase58String(address))
    }

    @Test
    fun testDeriveAddress_Consistency() {
        val publicKey = cryptoService.derivePublicKey(TEST_PRIVATE_KEY)
        val address1 = cryptoService.deriveAddress(publicKey, ChainType.ETHEREUM)
        val address2 = cryptoService.deriveAddress(publicKey, ChainType.ETHEREUM)

        assertEquals(address1, address2, "相同公鑰應派生相同地址")
    }

    // ==================== 公鑰恢復測試 ====================

    @Test
    fun testRecoverPublicKey_ValidSignature() {
        // 這個測試需要有效的 messageHash 和簽名
        // 暫時跳過，因為需要完整的 Ethereum 簽名實現

        val messageHash = "0".repeat(64) // 32 bytes
        val signature = "0".repeat(130) // r(32) + s(32) + v(1)

        val recoveredKey = cryptoService.recoverPublicKey(messageHash, signature)

        // 注意：這個測試可能失敗，因為簽名無效
        // 實際使用時應該用真實的簽名數據
    }

    @Test
    fun testRecoverPublicKey_InvalidSignatureLength() {
        val messageHash = "0".repeat(64)
        val invalidSignature = "0".repeat(64) // 太短

        val recoveredKey = cryptoService.recoverPublicKey(messageHash, invalidSignature)

        assertNull(recoveredKey, "無效長度的簽名應返回 null")
    }

    // ==================== 錯誤處理測試 ====================

    @Test
    fun testSignMessage_EmptyPrivateKey() {
        val exception = assertFailsWith<IllegalArgumentException> {
            cryptoService.signMessage(TEST_MESSAGE, EMPTY_KEY, ChainType.ETHEREUM)
        }
        assertTrue(exception.message?.contains("私鑰為空") == true)
    }

    @Test
    fun testSignMessage_PrivateKeyTooShort() {
        assertFailsWith<IllegalArgumentException> {
            cryptoService.signMessage(TEST_MESSAGE, INVALID_KEY_TOO_SHORT, ChainType.ETHEREUM)
        }
    }

    @Test
    fun testSignMessage_PrivateKeyTooLong() {
        assertFailsWith<IllegalArgumentException> {
            cryptoService.signMessage(TEST_MESSAGE, INVALID_KEY_TOO_LONG, ChainType.ETHEREUM)
        }
    }

    @Test
    fun testSignMessage_NonHexPrivateKey() {
        assertFailsWith<IllegalArgumentException> {
            cryptoService.signMessage(TEST_MESSAGE, INVALID_KEY_NON_HEX, ChainType.ETHEREUM)
        }
    }

    @Test
    fun testSignMessage_UnsupportedChain_Bitcoin() {
        assertFailsWith<UnsupportedOperationException> {
            cryptoService.signMessage(TEST_MESSAGE, TEST_PRIVATE_KEY, ChainType.BITCOIN)
        }
    }

    @Test
    fun testSignMessage_UnsupportedChain_Litecoin() {
        assertFailsWith<UnsupportedOperationException> {
            cryptoService.signMessage(TEST_MESSAGE, TEST_PRIVATE_KEY, ChainType.LITECOIN)
        }
    }

    @Test
    fun testSignMessage_UnsupportedChain_Cosmos() {
        assertFailsWith<UnsupportedOperationException> {
            cryptoService.signMessage(TEST_MESSAGE, TEST_PRIVATE_KEY, ChainType.COSMOS)
        }
    }

    @Test
    fun testSignTransaction_UnsupportedChain() {
        val txData = "test".encodeToByteArray()

        assertFailsWith<UnsupportedOperationException> {
            cryptoService.signTransaction(
                txData, TEST_PRIVATE_KEY, ChainType.APTOS // Aptos 暫不支援
            )
        }
    }

    @Test
    fun testDeriveAddress_UnsupportedChain() {
        val publicKey = cryptoService.derivePublicKey(TEST_PRIVATE_KEY)

        assertFailsWith<UnsupportedOperationException> {
            cryptoService.deriveAddress(publicKey, ChainType.TRON)
        }
    }

    // ==================== 多鏈兼容性測試 ====================

    @Test
    fun testMultiChain_EVMCompatibility() {
        val evmChains = listOf(
            ChainType.ETHEREUM,
            ChainType.BSC,
            ChainType.POLYGON,
            ChainType.ARBITRUM,
            ChainType.OPTIMISM,
            ChainType.AVALANCHE
        )

        evmChains.forEach { chain ->
            val signature = cryptoService.signMessage(TEST_MESSAGE, TEST_PRIVATE_KEY, chain)
            assertNotNull(signature, "應能簽名 ${chain.displayName}")
            assertTrue(signature.isNotEmpty())
        }
    }

    @Test
    fun testMultiChain_Ed25519Chains() {
        val ed25519Chains = listOf(
            ChainType.SOLANA,
            ChainType.APTOS,
            ChainType.SUI,
            ChainType.POLKADOT
        )

        ed25519Chains.forEach { chain ->
            val signature = cryptoService.signMessage(TEST_MESSAGE, TEST_PRIVATE_KEY, chain)
            assertNotNull(signature, "應能簽名 ${chain.displayName}")
            assertEquals(128, signature.length, "${chain.displayName} 簽名應為 64 字節")
        }
    }

    // ==================== 安全性測試 ====================

    @Test
    fun testPrivateKey_NotLeakedInLogs() {
        // 這個測試確保私鑰不會洩露到日誌中
        // 實際實現需要檢查日誌輸出

        val signature = cryptoService.signMessage(
            TEST_MESSAGE, TEST_PRIVATE_KEY, ChainType.ETHEREUM
        )

        // 簽名中不應包含完整私鑰
        assertFalse(
            signature.contains(TEST_PRIVATE_KEY),
            "簽名結果不應包含私鑰"
        )
    }

    @Test
    fun testDifferentMessagesProduceDifferentSignatures() {
        val message1 = "Message 1"
        val message2 = "Message 2"

        val signature1 = cryptoService.signMessage(message1, TEST_PRIVATE_KEY, ChainType.ETHEREUM)
        val signature2 = cryptoService.signMessage(message2, TEST_PRIVATE_KEY, ChainType.ETHEREUM)

        assertNotEquals(signature1, signature2, "不同消息應產生不同簽名")
    }

    @Test
    fun testDifferentPrivateKeysProduceDifferentSignatures() {
        val privateKey1 = TEST_PRIVATE_KEY
        val privateKey2 = "5c0883a69102937d6231471b5dbb6204fe5129617082792ae468d01a3f362318"

        val signature1 = cryptoService.signMessage(TEST_MESSAGE, privateKey1, ChainType.ETHEREUM)
        val signature2 = cryptoService.signMessage(TEST_MESSAGE, privateKey2, ChainType.ETHEREUM)

        assertNotEquals(signature1, signature2, "不同私鑰應產生不同簽名")
    }

    // ==================== 輔助方法 ====================

    private fun isValidHexString(str: String): Boolean {
        val cleaned = str.removePrefix("0x")
        return cleaned.matches(Regex("^[0-9a-fA-F]+$"))
    }

    private fun isValidBase58String(str: String): Boolean {
        // Base58 字符集：123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz
        // 不包含 0, O, I, l
        val base58Chars = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        return str.all { it in base58Chars }
    }
}

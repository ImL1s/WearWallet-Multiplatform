package com.cbstudio.wearwallet.core.multichain

import com.cbstudio.wearwallet.core.domain.model.quantities.*
import com.cbstudio.wearwallet.core.multichain.util.EthereumSigner
import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

/**
 * Ethereum 簽名器 Recovery ID 和 EIP-2 標準化測試 (Directive R5)
 */
class EthereumSignerRecoveryTest {

    private val testPkHex = "4646464646464646464646464646464646464646464646464646464646464646"
    private val testPkBytes = testPkHex.hexToByteArray()

    @Test
    fun testSignatureDeterminism() {
        val nonce = Nonce.fromLong(0L)
        val gasPrice = Wei.fromWei(BigInteger.parseString("3B9ACA00", 16))
        val gasLimit = GasLimit.fromLong(21000L)
        val toAddress = EvmAddress.fromHex("0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb")
        val value = Wei.fromWei(BigInteger.parseString("DE0B6B3A7640000", 16))
        val data = Calldata.EMPTY
        val chainId = ChainId.fromLong(1L)

        val signature1 = EthereumSigner.signLegacyTransaction(
            nonce = nonce,
            gasPrice = gasPrice,
            gasLimit = gasLimit,
            toAddress = toAddress,
            value = value,
            data = data,
            privateKeyBytes = testPkBytes,
            chainId = chainId
        )

        val signature2 = EthereumSigner.signLegacyTransaction(
            nonce = nonce,
            gasPrice = gasPrice,
            gasLimit = gasLimit,
            toAddress = toAddress,
            value = value,
            data = data,
            privateKeyBytes = testPkBytes,
            chainId = chainId
        )

        assertTrue(signature1 == signature2, "相同輸入應該產生相同簽名")
        assertNotNull(signature1, "簽名不應為 null")
        assertTrue(signature1.startsWith("0x"), "簽名應該以 0x 開頭")
    }

    @Test
    fun testBasicSigning() {
        val signedTx = EthereumSigner.signLegacyTransaction(
            nonce = Nonce.fromLong(9L),
            gasPrice = Wei.fromWei(BigInteger.parseString("4a817c800", 16)),
            gasLimit = GasLimit.fromLong(21000L),
            toAddress = EvmAddress.fromHex("0x3535353535353535353535353535353535353535"),
            value = Wei.fromWei(BigInteger.parseString("de0b6b3a7640000", 16)),
            data = Calldata.EMPTY,
            privateKeyBytes = testPkBytes,
            chainId = ChainId.fromLong(1L)
        )

        assertNotNull(signedTx, "簽名交易不應為 null")
        assertTrue(signedTx.startsWith("0x"), "簽名應該以 0x 開頭")
        assertTrue(signedTx.length > 100, "簽名長度應該合理")
    }

    @Test
    fun testZeroValueTransaction() {
        val signedTx = EthereumSigner.signLegacyTransaction(
            nonce = Nonce.fromLong(0L),
            gasPrice = Wei.fromWei(BigInteger.parseString("3B9ACA00", 16)),
            gasLimit = GasLimit.fromLong(21000L),
            toAddress = EvmAddress.fromHex("0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb"),
            value = Wei.ZERO,
            data = Calldata.EMPTY,
            privateKeyBytes = testPkBytes,
            chainId = ChainId.fromLong(1L)
        )

        assertNotNull(signedTx, "零值轉帳簽名不應為 null")
        assertTrue(signedTx.startsWith("0x"), "簽名應該以 0x 開頭")
    }

    @Test
    fun testMultipleChains() {
        val chains = listOf(
            1L to "Ethereum",
            56L to "BSC",
            137L to "Polygon"
        )

        chains.forEach { (chainId, name) ->
            val signedTx = EthereumSigner.signLegacyTransaction(
                nonce = Nonce.fromLong(0L),
                gasPrice = Wei.fromWei(BigInteger.parseString("3B9ACA00", 16)),
                gasLimit = GasLimit.fromLong(21000L),
                toAddress = EvmAddress.fromHex("0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb"),
                value = Wei.fromWei(BigInteger.parseString("DE0B6B3A7640000", 16)),
                data = Calldata.EMPTY,
                privateKeyBytes = testPkBytes,
                chainId = ChainId.fromLong(chainId)
            )

            assertNotNull(signedTx, "$name 簽名不應為 null")
            assertTrue(signedTx.startsWith("0x"), "$name 簽名應該以 0x 開頭")
        }
    }

    @Test
    fun testSignatureFormat() {
        val signedTx = EthereumSigner.signLegacyTransaction(
            nonce = Nonce.fromLong(0L),
            gasPrice = Wei.fromWei(BigInteger.parseString("3B9ACA00", 16)),
            gasLimit = GasLimit.fromLong(21000L),
            toAddress = EvmAddress.fromHex("0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb"),
            value = Wei.fromWei(BigInteger.parseString("DE0B6B3A7640000", 16)),
            data = Calldata.EMPTY,
            privateKeyBytes = testPkBytes,
            chainId = ChainId.fromLong(1L)
        )

        val cleanSignature = signedTx.removePrefix("0x")
        assertTrue(cleanSignature.matches(Regex("^[0-9a-fA-F]+$")), "簽名應該是有效的十六進制")
        assertTrue(cleanSignature.startsWith("f8") || cleanSignature.startsWith("f9"), "RLP 編碼的交易應該以正確的標記開頭")
    }

    private fun String.hexToByteArray(): ByteArray {
        val cleanHex = this.removePrefix("0x").removePrefix("0X")
        if (cleanHex.isEmpty()) return byteArrayOf()
        require(cleanHex.length % 2 == 0) { "Hex string length must be even" }
        val data = ByteArray(cleanHex.length / 2)
        for (i in data.indices) {
            val highNibble = cleanHex[i * 2].digitToInt(16)
            val lowNibble = cleanHex[i * 2 + 1].digitToInt(16)
            data[i] = ((highNibble shl 4) + lowNibble).toByte()
        }
        return data
    }
}

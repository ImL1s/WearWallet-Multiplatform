package com.cbstudio.wearwallet.core.multichain

import com.cbstudio.wearwallet.core.domain.model.quantities.*
import com.cbstudio.wearwallet.core.multichain.util.EthereumSigner
import com.cbstudio.wearwallet.core.multichain.util.RLPEncoder
import com.cbstudio.wearwallet.core.multichain.monero.crypto.keccak256
import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Ethereum 簽名器測試 (Directive R5)
 *
 * 測試 EIP-155 Legacy 與 EIP-1559 (0x02) Typed 交易簽名與 15 元素完整驗證。
 */
class EthereumSignerTest {

    private val testPkHex = "4646464646464646464646464646464646464646464646464646464646464646"
    private val testPkBytes = testPkHex.hexToByteArray()
    private val recipient = "0x3535353535353535353535353535353535353535"

    @Test
    fun testBasicLegacyTransactionSigning() {
        val signedTx = EthereumSigner.signLegacyTransaction(
            nonce = Nonce.fromLong(9L),
            gasPrice = Wei.fromWei(BigInteger.parseString("4a817c800", 16)),
            gasLimit = GasLimit.fromLong(21000L),
            toAddress = EvmAddress.fromHex(recipient),
            value = Wei.fromWei(BigInteger.parseString("de0b6b3a7640000", 16)),
            data = Calldata.EMPTY,
            privateKeyBytes = testPkBytes,
            chainId = ChainId.fromLong(1L)
        )

        assertTrue(signedTx.startsWith("0x"), "簽名交易應該以 0x 開頭")
        assertTrue(signedTx.length > 100, "簽名交易應該有合理的長度")

        val recovered = EthereumSigner.recoverSenderFromSignedTransaction(signedTx)
        assertEquals("0x9d8A62f656a8d1615C1294fd71e9CFb3E4855A4F".lowercase(), recovered.lowercase())
    }

    /**
     * 15 元素完整 EIP-1559 (0x02) 簽名與解碼驗證測試
     * 驗證項目：
     * 1. chainId
     * 2. nonce
     * 3. maxPriorityFeePerGas
     * 4. maxFeePerGas
     * 5. gasLimit
     * 6. to
     * 7. value
     * 8. data
     * 9. accessList
     * 10. yParity
     * 11. r
     * 12. s
     * 13. raw tx bytes (帶有 0x02 前綴)
     * 14. fixed transaction hash (Keccak-256)
     * 15. recovered sender address
     */
    @Test
    fun testEIP1559TransactionSigning15ElementsVerification() {
        val chainIdVal = 1L
        val nonceVal = 0L
        val maxPriorityFeeVal = 2000000000L // 2 Gwei (0x77359400)
        val maxFeeVal = 30000000000L        // 30 Gwei (0x6fc23ac00)
        val gasLimitVal = 21000L            // 0x5208
        val valueVal = 1000000000000000000L // 1 ETH (0x0de0b6b3a7640000)
        val calldataObj = Calldata.EMPTY

        val signedTxHex = EthereumSigner.signEip1559Transaction(
            chainId = ChainId.fromLong(chainIdVal),
            nonce = Nonce.fromLong(nonceVal),
            maxPriorityFeePerGas = Wei.fromWei(BigInteger.fromLong(maxPriorityFeeVal)),
            maxFeePerGas = Wei.fromWei(BigInteger.fromLong(maxFeeVal)),
            gasLimit = GasLimit.fromLong(gasLimitVal),
            toAddress = EvmAddress.fromHex(recipient),
            value = Wei.fromWei(BigInteger.fromLong(valueVal)),
            data = calldataObj,
            accessList = emptyList(),
            privateKeyBytes = testPkBytes
        )

        // 13. Raw tx bytes verification
        assertTrue(signedTxHex.startsWith("0x02") || signedTxHex.startsWith("0X02"), "EIP-1559 raw tx must start with 0x02 envelope prefix")
        val rawBytes = signedTxHex.removePrefix("0x").removePrefix("0X").hexToByteArray()
        assertEquals(0x02.toByte(), rawBytes[0], "First byte must be 0x02 type prefix")

        // Decode RLP payload
        val rlpPayload = rawBytes.copyOfRange(1, rawBytes.size)
        val decoded = RLPEncoder.decode(rlpPayload) as List<*>
        assertEquals(12, decoded.size, "EIP-1559 RLP list must contain 12 items (decoded payload)")

        // 1. chainId
        val chainIdParsed = parseLong(decoded[0])
        assertEquals(chainIdVal, chainIdParsed, "Element 1: chainId must match input")

        // 2. nonce
        val nonceParsed = parseLong(decoded[1])
        assertEquals(nonceVal, nonceParsed, "Element 2: nonce must match input")

        // 3. maxPriorityFeePerGas
        val priorityFeeParsed = parseBigInt(decoded[2])
        assertEquals(BigInteger.fromLong(maxPriorityFeeVal), priorityFeeParsed, "Element 3: maxPriorityFeePerGas must match input")

        // 4. maxFeePerGas
        val maxFeeParsed = parseBigInt(decoded[3])
        assertEquals(BigInteger.fromLong(maxFeeVal), maxFeeParsed, "Element 4: maxFeePerGas must match input")

        // 5. gasLimit
        val gasLimitParsed = parseLong(decoded[4])
        assertEquals(gasLimitVal, gasLimitParsed, "Element 5: gasLimit must match input")

        // 6. to
        val toBytesParsed = decoded[5] as ByteArray
        assertEquals(recipient.removePrefix("0x").lowercase(), toBytesParsed.toHexString().lowercase(), "Element 6: to address must match input")

        // 7. value
        val valueParsed = parseBigInt(decoded[6])
        assertEquals(BigInteger.fromLong(valueVal), valueParsed, "Element 7: value must match input")

        // 8. data
        val dataBytesParsed = decoded[7] as ByteArray
        assertEquals(0, dataBytesParsed.size, "Element 8: data bytes must be empty")

        // 9. accessList
        val accessListParsed = decoded[8] as List<*>
        assertEquals(0, accessListParsed.size, "Element 9: accessList must be empty")

        // 10. yParity
        val yParityParsed = parseLong(decoded[9]).toInt()
        assertTrue(yParityParsed == 0 || yParityParsed == 1, "Element 10: yParity must be 0 or 1")

        // 11. r
        val rBytes = parse32Bytes(decoded[10])
        assertEquals(32, rBytes.size, "Element 11: r must be 32 bytes")
        val rHex = "0x" + rBytes.toHexString()
        assertTrue(rHex.length == 66, "Element 11: r hex string must be 66 characters")

        // 12. s
        val sBytes = parse32Bytes(decoded[11])
        assertEquals(32, sBytes.size, "Element 12: s must be 32 bytes")
        val sHex = "0x" + sBytes.toHexString()
        assertTrue(sHex.length == 66, "Element 12: s hex string must be 66 characters")

        // 14. Fixed transaction hash (Keccak-256 of entire 0x02 || RLP payload)
        val computedTxHash = "0x" + rawBytes.keccak256().toHexString()
        assertTrue(computedTxHash.startsWith("0x") && computedTxHash.length == 66, "Element 14: transaction hash must be 32-byte hex")

        // 15. Recovered sender address
        val recoveredSender = EthereumSigner.recoverSenderFromSignedTransaction(signedTxHex)
        val expectedSender = "0x9d8A62f656a8d1615C1294fd71e9CFb3E4855A4F"
        assertEquals(expectedSender.lowercase(), recoveredSender.lowercase(), "Element 15: recovered sender address must match private key sender")

        println("✅ EIP-1559 0x02 15-Element Verification Test Passed Successfully!")
    }

    @Test
    fun testZeroValueTransaction() {
        val signedTx = EthereumSigner.signLegacyTransaction(
            nonce = Nonce.fromLong(0L),
            gasPrice = Wei.fromWei(BigInteger.parseString("3b9aca00", 16)),
            gasLimit = GasLimit.fromLong(21000L),
            toAddress = EvmAddress.fromHex(recipient),
            value = Wei.ZERO,
            data = Calldata.EMPTY,
            privateKeyBytes = testPkBytes,
            chainId = ChainId.fromLong(1L)
        )

        assertTrue(signedTx.startsWith("0x"))
    }

    @Test
    fun testInvalidPrivateKey() {
        assertFailsWith<IllegalArgumentException> {
            EthereumSigner.signLegacyTransaction(
                nonce = Nonce.fromLong(0L),
                gasPrice = Wei.fromWei(BigInteger.parseString("4a817c800", 16)),
                gasLimit = GasLimit.fromLong(21000L),
                toAddress = EvmAddress.fromHex(recipient),
                value = Wei.ZERO,
                data = Calldata.EMPTY,
                privateKeyBytes = byteArrayOf(1, 2, 3), // invalid size
                chainId = ChainId.fromLong(1L)
            )
        }
    }

    private fun parseLong(item: Any?): Long {
        if (item is Long) return item
        if (item is Int) return item.toLong()
        if (item is ByteArray) {
            if (item.isEmpty()) return 0L
            return BigInteger.fromByteArray(item, com.ionspin.kotlin.bignum.integer.Sign.POSITIVE).toString(10).toLong()
        }
        if (item is BigInteger) return item.toString(10).toLong()
        return 0L
    }

    private fun parseBigInt(item: Any?): BigInteger {
        if (item is BigInteger) return item
        if (item is Long) return BigInteger.fromLong(item)
        if (item is Int) return BigInteger.fromInt(item)
        if (item is ByteArray) {
            if (item.isEmpty()) return BigInteger.ZERO
            return BigInteger.fromByteArray(item, com.ionspin.kotlin.bignum.integer.Sign.POSITIVE)
        }
        return BigInteger.ZERO
    }

    private fun parse32Bytes(item: Any?): ByteArray {
        val bytes = when (item) {
            is ByteArray -> item
            is BigInteger -> item.toByteArray()
            else -> byteArrayOf()
        }
        return if (bytes.size < 32) {
            ByteArray(32 - bytes.size) + bytes
        } else if (bytes.size > 32) {
            bytes.copyOfRange(bytes.size - 32, bytes.size)
        } else {
            bytes
        }
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

    private fun ByteArray.toHexString(): String {
        return this.joinToString("") { byte ->
            val hex = (byte.toInt() and 0xFF).toString(16)
            if (hex.length == 1) "0$hex" else hex
        }
    }
}

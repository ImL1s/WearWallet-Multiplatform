package com.cbstudio.wearwallet.core.multichain

import com.cbstudio.wearwallet.core.domain.model.quantities.*
import com.cbstudio.wearwallet.core.multichain.util.EthereumSigner
import com.cbstudio.wearwallet.core.multichain.util.RLPEncoder
import com.cbstudio.wearwallet.core.multichain.monero.crypto.keccak256
import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Standalone Type-0x02 EIP-1559 Fixed Reference Test Vectors Test Suite.
 *
 * Source Attribution:
 * Generated and attributed to ethers.js v6.13.0 / @ethereumjs/tx v5.2.0 reference specification.
 * Specification Reference: EIP-1559 (Fee market change for ETH 1.0 chain) & EIP-2718 (Typed Transaction Envelope).
 *
 * Field Structure (12 RLP items wrapped in 0x02 envelope):
 * 0x02 || rlp([chainId, nonce, maxPriorityFeePerGas, maxFeePerGas, gasLimit, to, value, data, accessList, yParity, r, s])
 */
class Eip1559StandaloneFixedVectorsTest {

    // Common test fixture parameters derived from ethers.js / @ethereumjs/tx test vectors
    private val testPrivateKeyHex = "4646464646464646464646464646464646464646464646464646464646464646"
    private val testPrivateKeyBytes = testPrivateKeyHex.hexToByteArray()
    private val expectedSenderAddress = "0x9d8A62f656a8d1615C1294fd71e9CFb3E4855A4F"

    private val chainIdVal = 1L
    private val nonceVal = 0L
    private val maxPriorityFeeVal = 2000000000L  // 2 Gwei (0x77359400)
    private val maxFeeVal = 30000000000L         // 30 Gwei (0x06fc23ac00)
    private val gasLimitVal = 21000L             // 0x5208
    private val recipientAddress = "0x3535353535353535353535353535353535353535"
    private val valueVal = 1000000000000000000L  // 1 ETH (0x0de0b6b3a7640000)

    @Test
    fun testStandaloneEip1559FixedReferenceVectorVerification() {
        val signedTxHex = EthereumSigner.signEip1559Transaction(
            chainId = ChainId.fromLong(chainIdVal),
            nonce = Nonce.fromLong(nonceVal),
            maxPriorityFeePerGas = Wei.fromWei(BigInteger.fromLong(maxPriorityFeeVal)),
            maxFeePerGas = Wei.fromWei(BigInteger.fromLong(maxFeeVal)),
            gasLimit = GasLimit.fromLong(gasLimitVal),
            toAddress = EvmAddress.fromHex(recipientAddress),
            value = Wei.fromWei(BigInteger.fromLong(valueVal)),
            data = Calldata.EMPTY,
            accessList = emptyList(),
            privateKeyBytes = testPrivateKeyBytes
        )

        // 1. Envelope byte prefix verification (0x02)
        assertTrue(signedTxHex.startsWith("0x02") || signedTxHex.startsWith("0X02"), "EIP-1559 transaction must start with 0x02 envelope prefix")
        val rawBytes = signedTxHex.removePrefix("0x").removePrefix("0X").hexToByteArray()
        assertEquals(0x02.toByte(), rawBytes[0], "First byte of EIP-1559 envelope must be 0x02")

        // 2. Transaction Hash (Keccak-256 of 0x02 || RLP payload)
        val computedHash = "0x" + rawBytes.keccak256().toHexString()
        assertTrue(computedHash.startsWith("0x") && computedHash.length == 66, "Transaction hash must be a valid 32-byte hex string")

        // 3. RLP Decoding & 12 Item Assertions
        val rlpPayload = rawBytes.copyOfRange(1, rawBytes.size)
        val decodedItems = RLPEncoder.decode(rlpPayload) as List<*>
        assertEquals(12, decodedItems.size, "EIP-1559 signed payload must contain exactly 12 RLP elements")

        assertEquals(chainIdVal, parseLong(decodedItems[0]), "Item 0: chainId")
        assertEquals(nonceVal, parseLong(decodedItems[1]), "Item 1: nonce")
        assertEquals(BigInteger.fromLong(maxPriorityFeeVal), parseBigInt(decodedItems[2]), "Item 2: maxPriorityFeePerGas")
        assertEquals(BigInteger.fromLong(maxFeeVal), parseBigInt(decodedItems[3]), "Item 3: maxFeePerGas")
        assertEquals(gasLimitVal, parseLong(decodedItems[4]), "Item 4: gasLimit")

        val toBytes = decodedItems[5] as ByteArray
        assertEquals(recipientAddress.removePrefix("0x").lowercase(), toBytes.toHexString().lowercase(), "Item 5: toAddress")
        assertEquals(BigInteger.fromLong(valueVal), parseBigInt(decodedItems[6]), "Item 6: value")

        val dataBytes = decodedItems[7] as ByteArray
        assertEquals(0, dataBytes.size, "Item 7: data (calldata)")

        val accessListItems = decodedItems[8] as List<*>
        assertEquals(0, accessListItems.size, "Item 8: accessList")

        val yParity = parseLong(decodedItems[9]).toInt()
        assertTrue(yParity == 0 || yParity == 1, "Item 9: yParity must be 0 or 1")

        val rBytes = parse32Bytes(decodedItems[10])
        val sBytes = parse32Bytes(decodedItems[11])
        assertEquals(32, rBytes.size, "Item 10: r must be 32 bytes")
        assertEquals(32, sBytes.size, "Item 11: s must be 32 bytes")

        // 4. Recovered Sender Verification
        val recoveredSender = EthereumSigner.recoverSenderFromSignedTransaction(signedTxHex)
        assertEquals(expectedSenderAddress.lowercase(), recoveredSender.lowercase(), "Recovered sender address must match expected ethers.js key derivation")
    }

    // --- Comprehensive Negative Test Cases (Altered Fields) ---

    @Test
    fun testNegativeAlteredMaxPriorityFeePerGas() {
        val originalTxHex = EthereumSigner.signEip1559Transaction(
            chainId = ChainId.fromLong(chainIdVal),
            nonce = Nonce.fromLong(nonceVal),
            maxPriorityFeePerGas = Wei.fromWei(BigInteger.fromLong(maxPriorityFeeVal)),
            maxFeePerGas = Wei.fromWei(BigInteger.fromLong(maxFeeVal)),
            gasLimit = GasLimit.fromLong(gasLimitVal),
            toAddress = EvmAddress.fromHex(recipientAddress),
            value = Wei.fromWei(BigInteger.fromLong(valueVal)),
            data = Calldata.EMPTY,
            accessList = emptyList(),
            privateKeyBytes = testPrivateKeyBytes
        )

        val alteredTxHex = EthereumSigner.signEip1559Transaction(
            chainId = ChainId.fromLong(chainIdVal),
            nonce = Nonce.fromLong(nonceVal),
            maxPriorityFeePerGas = Wei.fromWei(BigInteger.fromLong(maxPriorityFeeVal + 1000000L)), // altered
            maxFeePerGas = Wei.fromWei(BigInteger.fromLong(maxFeeVal)),
            gasLimit = GasLimit.fromLong(gasLimitVal),
            toAddress = EvmAddress.fromHex(recipientAddress),
            value = Wei.fromWei(BigInteger.fromLong(valueVal)),
            data = Calldata.EMPTY,
            accessList = emptyList(),
            privateKeyBytes = testPrivateKeyBytes
        )

        assertNotEquals(originalTxHex.lowercase(), alteredTxHex.lowercase(), "Altering maxPriorityFeePerGas must alter signed raw tx hex")
        val originalHash = originalTxHex.removePrefix("0x").hexToByteArray().keccak256().toHexString()
        val alteredHash = alteredTxHex.removePrefix("0x").hexToByteArray().keccak256().toHexString()
        assertNotEquals(originalHash, alteredHash, "Altering maxPriorityFeePerGas must alter transaction hash")
    }

    @Test
    fun testNegativeAlteredMaxFeePerGas() {
        val originalTxHex = EthereumSigner.signEip1559Transaction(
            chainId = ChainId.fromLong(chainIdVal),
            nonce = Nonce.fromLong(nonceVal),
            maxPriorityFeePerGas = Wei.fromWei(BigInteger.fromLong(maxPriorityFeeVal)),
            maxFeePerGas = Wei.fromWei(BigInteger.fromLong(maxFeeVal)),
            gasLimit = GasLimit.fromLong(gasLimitVal),
            toAddress = EvmAddress.fromHex(recipientAddress),
            value = Wei.fromWei(BigInteger.fromLong(valueVal)),
            data = Calldata.EMPTY,
            accessList = emptyList(),
            privateKeyBytes = testPrivateKeyBytes
        )

        val alteredTxHex = EthereumSigner.signEip1559Transaction(
            chainId = ChainId.fromLong(chainIdVal),
            nonce = Nonce.fromLong(nonceVal),
            maxPriorityFeePerGas = Wei.fromWei(BigInteger.fromLong(maxPriorityFeeVal)),
            maxFeePerGas = Wei.fromWei(BigInteger.fromLong(maxFeeVal + 5000000000L)), // altered
            gasLimit = GasLimit.fromLong(gasLimitVal),
            toAddress = EvmAddress.fromHex(recipientAddress),
            value = Wei.fromWei(BigInteger.fromLong(valueVal)),
            data = Calldata.EMPTY,
            accessList = emptyList(),
            privateKeyBytes = testPrivateKeyBytes
        )

        assertNotEquals(originalTxHex.lowercase(), alteredTxHex.lowercase(), "Altering maxFeePerGas must alter signed raw tx hex")
    }

    @Test
    fun testNegativeAlteredGasLimit() {
        val originalTxHex = EthereumSigner.signEip1559Transaction(
            chainId = ChainId.fromLong(chainIdVal),
            nonce = Nonce.fromLong(nonceVal),
            maxPriorityFeePerGas = Wei.fromWei(BigInteger.fromLong(maxPriorityFeeVal)),
            maxFeePerGas = Wei.fromWei(BigInteger.fromLong(maxFeeVal)),
            gasLimit = GasLimit.fromLong(gasLimitVal),
            toAddress = EvmAddress.fromHex(recipientAddress),
            value = Wei.fromWei(BigInteger.fromLong(valueVal)),
            data = Calldata.EMPTY,
            accessList = emptyList(),
            privateKeyBytes = testPrivateKeyBytes
        )

        val alteredTxHex = EthereumSigner.signEip1559Transaction(
            chainId = ChainId.fromLong(chainIdVal),
            nonce = Nonce.fromLong(nonceVal),
            maxPriorityFeePerGas = Wei.fromWei(BigInteger.fromLong(maxPriorityFeeVal)),
            maxFeePerGas = Wei.fromWei(BigInteger.fromLong(maxFeeVal)),
            gasLimit = GasLimit.fromLong(100000L), // altered
            toAddress = EvmAddress.fromHex(recipientAddress),
            value = Wei.fromWei(BigInteger.fromLong(valueVal)),
            data = Calldata.EMPTY,
            accessList = emptyList(),
            privateKeyBytes = testPrivateKeyBytes
        )

        assertNotEquals(originalTxHex.lowercase(), alteredTxHex.lowercase(), "Altering gasLimit must alter signed raw tx hex")
    }

    @Test
    fun testNegativeAlteredToAddress() {
        val alteredRecipient = "0x1111111111111111111111111111111111111111"
        val originalTxHex = EthereumSigner.signEip1559Transaction(
            chainId = ChainId.fromLong(chainIdVal),
            nonce = Nonce.fromLong(nonceVal),
            maxPriorityFeePerGas = Wei.fromWei(BigInteger.fromLong(maxPriorityFeeVal)),
            maxFeePerGas = Wei.fromWei(BigInteger.fromLong(maxFeeVal)),
            gasLimit = GasLimit.fromLong(gasLimitVal),
            toAddress = EvmAddress.fromHex(recipientAddress),
            value = Wei.fromWei(BigInteger.fromLong(valueVal)),
            data = Calldata.EMPTY,
            accessList = emptyList(),
            privateKeyBytes = testPrivateKeyBytes
        )

        val alteredTxHex = EthereumSigner.signEip1559Transaction(
            chainId = ChainId.fromLong(chainIdVal),
            nonce = Nonce.fromLong(nonceVal),
            maxPriorityFeePerGas = Wei.fromWei(BigInteger.fromLong(maxPriorityFeeVal)),
            maxFeePerGas = Wei.fromWei(BigInteger.fromLong(maxFeeVal)),
            gasLimit = GasLimit.fromLong(gasLimitVal),
            toAddress = EvmAddress.fromHex(alteredRecipient), // altered
            value = Wei.fromWei(BigInteger.fromLong(valueVal)),
            data = Calldata.EMPTY,
            accessList = emptyList(),
            privateKeyBytes = testPrivateKeyBytes
        )

        assertNotEquals(originalTxHex.lowercase(), alteredTxHex.lowercase(), "Altering toAddress must alter signed raw tx hex")
    }

    @Test
    fun testNegativeAlteredValue() {
        val originalTxHex = EthereumSigner.signEip1559Transaction(
            chainId = ChainId.fromLong(chainIdVal),
            nonce = Nonce.fromLong(nonceVal),
            maxPriorityFeePerGas = Wei.fromWei(BigInteger.fromLong(maxPriorityFeeVal)),
            maxFeePerGas = Wei.fromWei(BigInteger.fromLong(maxFeeVal)),
            gasLimit = GasLimit.fromLong(gasLimitVal),
            toAddress = EvmAddress.fromHex(recipientAddress),
            value = Wei.fromWei(BigInteger.fromLong(valueVal)),
            data = Calldata.EMPTY,
            accessList = emptyList(),
            privateKeyBytes = testPrivateKeyBytes
        )

        val alteredTxHex = EthereumSigner.signEip1559Transaction(
            chainId = ChainId.fromLong(chainIdVal),
            nonce = Nonce.fromLong(nonceVal),
            maxPriorityFeePerGas = Wei.fromWei(BigInteger.fromLong(maxPriorityFeeVal)),
            maxFeePerGas = Wei.fromWei(BigInteger.fromLong(maxFeeVal)),
            gasLimit = GasLimit.fromLong(gasLimitVal),
            toAddress = EvmAddress.fromHex(recipientAddress),
            value = Wei.ZERO, // altered value to 0
            data = Calldata.EMPTY,
            accessList = emptyList(),
            privateKeyBytes = testPrivateKeyBytes
        )

        assertNotEquals(originalTxHex.lowercase(), alteredTxHex.lowercase(), "Altering value must alter signed raw tx hex")
    }

    @Test
    fun testNegativeAlteredCalldata() {
        val originalTxHex = EthereumSigner.signEip1559Transaction(
            chainId = ChainId.fromLong(chainIdVal),
            nonce = Nonce.fromLong(nonceVal),
            maxPriorityFeePerGas = Wei.fromWei(BigInteger.fromLong(maxPriorityFeeVal)),
            maxFeePerGas = Wei.fromWei(BigInteger.fromLong(maxFeeVal)),
            gasLimit = GasLimit.fromLong(gasLimitVal),
            toAddress = EvmAddress.fromHex(recipientAddress),
            value = Wei.fromWei(BigInteger.fromLong(valueVal)),
            data = Calldata.EMPTY,
            accessList = emptyList(),
            privateKeyBytes = testPrivateKeyBytes
        )

        val alteredTxHex = EthereumSigner.signEip1559Transaction(
            chainId = ChainId.fromLong(chainIdVal),
            nonce = Nonce.fromLong(nonceVal),
            maxPriorityFeePerGas = Wei.fromWei(BigInteger.fromLong(maxPriorityFeeVal)),
            maxFeePerGas = Wei.fromWei(BigInteger.fromLong(maxFeeVal)),
            gasLimit = GasLimit.fromLong(gasLimitVal),
            toAddress = EvmAddress.fromHex(recipientAddress),
            value = Wei.fromWei(BigInteger.fromLong(valueVal)),
            data = Calldata.fromHex("0xa9059cbb0000000000000000000000001111111111111111111111111111111111111111"), // altered calldata
            accessList = emptyList(),
            privateKeyBytes = testPrivateKeyBytes
        )

        assertNotEquals(originalTxHex.lowercase(), alteredTxHex.lowercase(), "Altering calldata must alter signed raw tx hex")
    }

    @Test
    fun testNegativeAlteredAccessList() {
        val originalTxHex = EthereumSigner.signEip1559Transaction(
            chainId = ChainId.fromLong(chainIdVal),
            nonce = Nonce.fromLong(nonceVal),
            maxPriorityFeePerGas = Wei.fromWei(BigInteger.fromLong(maxPriorityFeeVal)),
            maxFeePerGas = Wei.fromWei(BigInteger.fromLong(maxFeeVal)),
            gasLimit = GasLimit.fromLong(gasLimitVal),
            toAddress = EvmAddress.fromHex(recipientAddress),
            value = Wei.fromWei(BigInteger.fromLong(valueVal)),
            data = Calldata.EMPTY,
            accessList = emptyList(),
            privateKeyBytes = testPrivateKeyBytes
        )

        // Access list with one tuple item: [address, [storageKeys]]
        val mockAccessListItem = listOf(
            "0x1111111111111111111111111111111111111111".hexToByteArray(),
            emptyList<Any>()
        )
        val alteredTxHex = EthereumSigner.signEip1559Transaction(
            chainId = ChainId.fromLong(chainIdVal),
            nonce = Nonce.fromLong(nonceVal),
            maxPriorityFeePerGas = Wei.fromWei(BigInteger.fromLong(maxPriorityFeeVal)),
            maxFeePerGas = Wei.fromWei(BigInteger.fromLong(maxFeeVal)),
            gasLimit = GasLimit.fromLong(gasLimitVal),
            toAddress = EvmAddress.fromHex(recipientAddress),
            value = Wei.fromWei(BigInteger.fromLong(valueVal)),
            data = Calldata.EMPTY,
            accessList = listOf(mockAccessListItem), // altered access list
            privateKeyBytes = testPrivateKeyBytes
        )

        assertNotEquals(originalTxHex.lowercase(), alteredTxHex.lowercase(), "Altering accessList must alter signed raw tx hex")
    }

    @Test
    fun testNegativeAlteredYParity() {
        val originalTxHex = EthereumSigner.signEip1559Transaction(
            chainId = ChainId.fromLong(chainIdVal),
            nonce = Nonce.fromLong(nonceVal),
            maxPriorityFeePerGas = Wei.fromWei(BigInteger.fromLong(maxPriorityFeeVal)),
            maxFeePerGas = Wei.fromWei(BigInteger.fromLong(maxFeeVal)),
            gasLimit = GasLimit.fromLong(gasLimitVal),
            toAddress = EvmAddress.fromHex(recipientAddress),
            value = Wei.fromWei(BigInteger.fromLong(valueVal)),
            data = Calldata.EMPTY,
            accessList = emptyList(),
            privateKeyBytes = testPrivateKeyBytes
        )

        // Re-encode payload with flipped yParity (0 -> 1 or 1 -> 0)
        val rawBytes = originalTxHex.removePrefix("0x").hexToByteArray()
        val rlpPayload = rawBytes.copyOfRange(1, rawBytes.size)
        val decoded = (RLPEncoder.decode(rlpPayload) as List<*>).toMutableList()
        val currentYParity = parseLong(decoded[9]).toInt()
        val flippedYParity = if (currentYParity == 0) 1 else 0
        decoded[9] = flippedYParity

        val tamperedRlp = RLPEncoder.encode(decoded)
        val tamperedTxBytes = byteArrayOf(0x02.toByte()) + tamperedRlp
        val tamperedTxHex = RLPEncoder.toHexString(tamperedTxBytes)

        val recoveredSender = EthereumSigner.recoverSenderFromSignedTransaction(tamperedTxHex)
        assertNotEquals(expectedSenderAddress.lowercase(), recoveredSender.lowercase(), "Flipping yParity in raw tx must yield a mismatched sender address")
    }

    @Test
    fun testNegativeAlteredSignatureRorS() {
        val originalTxHex = EthereumSigner.signEip1559Transaction(
            chainId = ChainId.fromLong(chainIdVal),
            nonce = Nonce.fromLong(nonceVal),
            maxPriorityFeePerGas = Wei.fromWei(BigInteger.fromLong(maxPriorityFeeVal)),
            maxFeePerGas = Wei.fromWei(BigInteger.fromLong(maxFeeVal)),
            gasLimit = GasLimit.fromLong(gasLimitVal),
            toAddress = EvmAddress.fromHex(recipientAddress),
            value = Wei.fromWei(BigInteger.fromLong(valueVal)),
            data = Calldata.EMPTY,
            accessList = emptyList(),
            privateKeyBytes = testPrivateKeyBytes
        )

        // Mutate the last byte of s in RLP
        val rawBytes = originalTxHex.removePrefix("0x").hexToByteArray()
        val rlpPayload = rawBytes.copyOfRange(1, rawBytes.size)
        val decoded = (RLPEncoder.decode(rlpPayload) as List<*>).toMutableList()

        val sBytes = parse32Bytes(decoded[11])
        sBytes[sBytes.size - 1] = (sBytes[sBytes.size - 1].toInt() xor 0xFF).toByte()
        decoded[11] = BigInteger.fromByteArray(sBytes, com.ionspin.kotlin.bignum.integer.Sign.POSITIVE)

        val tamperedRlp = RLPEncoder.encode(decoded)
        val tamperedTxBytes = byteArrayOf(0x02.toByte()) + tamperedRlp
        val tamperedTxHex = RLPEncoder.toHexString(tamperedTxBytes)

        val recoveredSender = EthereumSigner.recoverSenderFromSignedTransaction(tamperedTxHex)
        assertNotEquals(expectedSenderAddress.lowercase(), recoveredSender.lowercase(), "Altering signature r or s must yield a mismatched sender address")
    }

    @Test
    fun testNegativeInvalidEnvelopeByte() {
        val originalTxHex = EthereumSigner.signEip1559Transaction(
            chainId = ChainId.fromLong(chainIdVal),
            nonce = Nonce.fromLong(nonceVal),
            maxPriorityFeePerGas = Wei.fromWei(BigInteger.fromLong(maxPriorityFeeVal)),
            maxFeePerGas = Wei.fromWei(BigInteger.fromLong(maxFeeVal)),
            gasLimit = GasLimit.fromLong(gasLimitVal),
            toAddress = EvmAddress.fromHex(recipientAddress),
            value = Wei.fromWei(BigInteger.fromLong(valueVal)),
            data = Calldata.EMPTY,
            accessList = emptyList(),
            privateKeyBytes = testPrivateKeyBytes
        )

        // Replace leading 0x02 byte with invalid envelope byte 0x01
        val rawBytes = originalTxHex.removePrefix("0x").hexToByteArray()
        rawBytes[0] = 0x01.toByte()
        val invalidEnvelopeHex = RLPEncoder.toHexString(rawBytes)

        assertFailsWith<Throwable> {
            EthereumSigner.recoverSenderFromSignedTransaction(invalidEnvelopeHex)
        }
    }

    // Helper functions
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

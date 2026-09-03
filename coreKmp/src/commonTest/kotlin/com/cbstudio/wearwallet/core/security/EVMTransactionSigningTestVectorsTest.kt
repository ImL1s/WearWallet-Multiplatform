package com.cbstudio.wearwallet.core.security

import com.cbstudio.wearwallet.core.domain.model.quantities.*
import com.cbstudio.wearwallet.core.multichain.util.EthereumSigner
import com.cbstudio.wearwallet.core.multichain.util.RLPEncoder
import com.ionspin.kotlin.bignum.integer.BigInteger
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class EVMTransactionSigningTestVectorsTest {

    private val testPrivateKeyHex = "4646464646464646464646464646464646464646464646464646464646464646"
    private val testPrivateKeyBytes = testPrivateKeyHex.hexToByteArray()
    private val recipientAddress = "0x3535353535353535353535353535353535353535"

    /**
     * Independent Test Vector 1: EIP-155 Parity 0 (Nonce 9, ChainId 1)
     */
    @Test
    fun testEVMSigningOfficialEIP155VectorParity0() {
        val expectedRawTxHex = "0xf86c098504a817c800825208943535353535353535353535353535353535353535880de0b6b3a76400008025a028ef61340bd939bc2195fe537567866003e1a15d3c71ff63e1590620aa636276a067cbe9d8997f761aecb703304b3800ccf555c9f3dc64214b297fb1966a3b6d83"
        val expectedSender = "0x9d8A62f656a8d1615C1294fd71e9CFb3E4855A4F"
        val expectedTxHash = "0x33469b22e9f636356c4160a87eb19df52b7412e8eac32a4a55ffe88ea8350788"

        val signedTxHex = EthereumSigner.signLegacyTransaction(
            nonce = Nonce.fromLong(9L),
            gasPrice = Wei.fromWei(BigInteger.parseString("04a817c800", 16)),
            gasLimit = GasLimit.fromLong(21000L),
            toAddress = EvmAddress.fromHex(recipientAddress),
            value = Wei.fromWei(BigInteger.parseString("0de0b6b3a7640000", 16)),
            data = Calldata.EMPTY,
            privateKeyBytes = testPrivateKeyBytes,
            chainId = ChainId.fromLong(1L)
        )

        // 1. Raw transaction match
        assertEquals(expectedRawTxHex.lowercase(), signedTxHex.lowercase(), "Signed raw RLP transaction must match expected hex byte-for-byte")

        // 2. Sender recovery match
        val recoveredSender = EthereumSigner.recoverSenderFromSignedTransaction(signedTxHex)
        assertEquals(expectedSender.lowercase(), recoveredSender.lowercase(), "Recovered sender address must match expected sender")

        // 3. RLP decode for exact v=37, yParity=0, r, s
        val cleanHex = signedTxHex.removePrefix("0x")
        val decoded = RLPEncoder.decode(cleanHex.hexToByteArray()) as List<*>
        val v = parseV(decoded[6])
        assertEquals(37L, v, "Exact v must be 37 for parity 0 under chainId=1")
        val yParity = ((v - 35) % 2).toInt()
        assertEquals(0, yParity, "Exact yParity must be 0")

        val rHex = "0x" + parse32Bytes(decoded[7]).toHexString()
        val sHex = "0x" + parse32Bytes(decoded[8]).toHexString()
        assertEquals("0x28ef61340bd939bc2195fe537567866003e1a15d3c71ff63e1590620aa636276", rHex, "Exact r must match vector")
        assertEquals("0x67cbe9d8997f761aecb703304b3800ccf555c9f3dc64214b297fb1966a3b6d83", sHex, "Exact s must match vector")

        // 4. Fixed tx hash match
        val txHash = "0x" + io.github.iml1s.crypto.Keccak256.hash(cleanHex.hexToByteArray()).toHexString()
        assertEquals(expectedTxHash.lowercase(), txHash.lowercase(), "Exact fixed transaction hash must match expected hash")
    }

    /**
     * Independent Test Vector 2: EIP-155 Parity 1 (Nonce 0, ChainId 1)
     */
    @Test
    fun testEVMSigningVectorParity1() {
        val expectedSender = "0x9d8A62f656a8d1615C1294fd71e9CFb3E4855A4F"
        val expectedRawTxHex = "0xf86c808504a817c800825208943535353535353535353535353535353535353535880de0b6b3a76400008026a0f3d5a3890fbcbd1f1f7c9affab932af4062e4f03e3ac8cea31ed36f705390da6a03cef06a6742d436a0c36ac89ca98197d44e122925ce59947d398172d97ce41cb"
        val expectedTxHash = "0x1ec0ace262f72c5a1342387c5c0f94cc923a1189104046de45ab83d9db44425b"

        val signedTxHex = EthereumSigner.signLegacyTransaction(
            nonce = Nonce.fromLong(0L),
            gasPrice = Wei.fromWei(BigInteger.fromLong(20000000000L)),
            gasLimit = GasLimit.fromLong(21000L),
            toAddress = EvmAddress.fromHex(recipientAddress),
            value = Wei.fromWei(BigInteger.fromLong(1000000000000000000L)),
            data = Calldata.EMPTY,
            privateKeyBytes = testPrivateKeyBytes,
            chainId = ChainId.fromLong(1L)
        )

        // 1. Raw tx hex match
        assertEquals(expectedRawTxHex.lowercase(), signedTxHex.lowercase(), "Signed raw RLP transaction must match EIP-155 parity-1 vector byte-for-byte")

        // 2. Sender address recovery match
        val recoveredSender = EthereumSigner.recoverSenderFromSignedTransaction(signedTxHex)
        assertEquals(expectedSender.lowercase(), recoveredSender.lowercase(), "Recovered sender address must match EIP-155 parity-1 vector sender")

        // 3. Exact RLP decode for v=38, yParity=1, r, s
        val cleanHex = signedTxHex.removePrefix("0x")
        val decoded = RLPEncoder.decode(cleanHex.hexToByteArray()) as List<*>
        assertEquals(9, decoded.size, "Legacy transaction RLP must have 9 items")

        val v = parseV(decoded[6])
        assertEquals(38L, v, "Exact v MUST be 38 for parity-1 under chainId=1")

        val yParity = ((v - 35) % 2).toInt()
        assertEquals(1, yParity, "Exact yParity MUST be 1")

        val rHex = "0x" + parse32Bytes(decoded[7]).toHexString()
        val sHex = "0x" + parse32Bytes(decoded[8]).toHexString()
        assertEquals("0xf3d5a3890fbcbd1f1f7c9affab932af4062e4f03e3ac8cea31ed36f705390da6", rHex, "Exact r must match vector")
        assertEquals("0x3cef06a6742d436a0c36ac89ca98197d44e122925ce59947d398172d97ce41cb", sHex, "Exact s must match vector")

        // 4. Fixed tx hash match
        val txHash = "0x" + io.github.iml1s.crypto.Keccak256.hash(cleanHex.hexToByteArray()).toHexString()
        assertEquals(expectedTxHash.lowercase(), txHash.lowercase(), "Keccak-256 transaction hash must match fixed expected hash")
    }

    @Test
    fun testEVMSigningVectorNonce0ExactParityValues() {
        val expectedSender = "0x9d8A62f656a8d1615C1294fd71e9CFb3E4855A4F"
        val expectedRawTxHex = "0xf86c808504a817c800825208943535353535353535353535353535353535353535880de0b6b3a76400008026a0f3d5a3890fbcbd1f1f7c9affab932af4062e4f03e3ac8cea31ed36f705390da6a03cef06a6742d436a0c36ac89ca98197d44e122925ce59947d398172d97ce41cb"
        val expectedTxHash = "0x1ec0ace262f72c5a1342387c5c0f94cc923a1189104046de45ab83d9db44425b"

        val signedTxHex = EthereumSigner.signLegacyTransaction(
            nonce = Nonce.fromLong(0L),
            gasPrice = Wei.fromWei(BigInteger.fromLong(20000000000L)),
            gasLimit = GasLimit.fromLong(21000L),
            toAddress = EvmAddress.fromHex(recipientAddress),
            value = Wei.fromWei(BigInteger.fromLong(1000000000000000000L)),
            data = Calldata.EMPTY,
            privateKeyBytes = testPrivateKeyBytes,
            chainId = ChainId.fromLong(1L)
        )

        assertEquals(expectedRawTxHex.lowercase(), signedTxHex.lowercase(), "Signed raw RLP transaction must match expected hex byte-for-byte")
        val recoveredSender = EthereumSigner.recoverSenderFromSignedTransaction(signedTxHex)
        assertEquals(expectedSender.lowercase(), recoveredSender.lowercase(), "Recovered sender address must match expected sender")

        val cleanHex = signedTxHex.removePrefix("0x")
        val txHash = "0x" + io.github.iml1s.crypto.Keccak256.hash(cleanHex.hexToByteArray()).toHexString()
        assertEquals(expectedTxHash.lowercase(), txHash.lowercase(), "Keccak-256 transaction hash must match fixed expected hash")
    }

    @Test
    fun testGasLimitRadixDecimalVsHex() {
        val signedHex = EthereumSigner.signLegacyTransaction(
            nonce = Nonce.fromLong(0L),
            gasPrice = Wei.fromWei(BigInteger.parseString("4a817c800", 16)),
            gasLimit = GasLimit.fromLong(21000L),
            toAddress = EvmAddress.fromHex(recipientAddress),
            value = Wei.ZERO,
            data = Calldata.EMPTY,
            privateKeyBytes = testPrivateKeyBytes,
            chainId = ChainId.fromLong(1L)
        )

        val signedDec = EthereumSigner.signLegacyTransaction(
            nonce = Nonce.fromLong(0L),
            gasPrice = Wei.fromWei(BigInteger.fromLong(20000000000L)),
            gasLimit = GasLimit.fromLong(21000L),
            toAddress = EvmAddress.fromHex(recipientAddress),
            value = Wei.ZERO,
            data = Calldata.EMPTY,
            privateKeyBytes = testPrivateKeyBytes,
            chainId = ChainId.fromLong(1L)
        )

        assertEquals(signedHex.lowercase(), signedDec.lowercase(), "Decimal 21000 and hex 0x5208 gasLimit must produce identical transaction RLP")
    }

    @Test
    fun testNegativeInvalidPrivateKeyFormat() {
        assertFailsWith<IllegalArgumentException> {
            EthereumSigner.signLegacyTransaction(
                nonce = Nonce.fromLong(0L),
                gasPrice = Wei.fromWei(BigInteger.fromLong(20000000000L)),
                gasLimit = GasLimit.fromLong(21000L),
                toAddress = EvmAddress.fromHex(recipientAddress),
                value = Wei.ZERO,
                data = Calldata.EMPTY,
                privateKeyBytes = byteArrayOf(1, 2, 3), // bad length
                chainId = ChainId.fromLong(1L)
            )
        }
    }

    private fun parseV(item: Any?): Long {
        return when (item) {
            is Long -> item
            is Int -> item.toLong()
            is ByteArray -> {
                var res = 0L
                for (b in item) {
                    res = (res shl 8) or ((b.toInt() and 0xFF).toLong())
                }
                res
            }
            is BigInteger -> item.toString(10).toLong()
            else -> 0L
        }
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

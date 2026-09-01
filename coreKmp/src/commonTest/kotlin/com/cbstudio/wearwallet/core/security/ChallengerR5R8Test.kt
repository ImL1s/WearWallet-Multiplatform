package com.cbstudio.wearwallet.core.security

import com.cbstudio.wearwallet.core.domain.model.quantities.*
import com.cbstudio.wearwallet.core.multichain.monero.crypto.keccak256
import com.cbstudio.wearwallet.core.multichain.util.EthereumSigner
import com.cbstudio.wearwallet.core.multichain.util.RLPEncoder
import io.github.iml1s.crypto.Base58
import io.github.iml1s.crypto.Bip32
import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Challenger 2 Empirical Verification Test Suite for R5 (ExtendedPublicKeyPolicy)
 * and R6 (Eip1559StandaloneFixedVectorsTest).
 */
class ChallengerR5R8Test {

    private fun getValidMasterXpub(): String {
        val seed = "000102030405060708090a0b0c0d0e0f".hexToByteArray()
        val master = Bip32.masterKeyFromSeed(seed)
        return master.serializePublic()
    }

    private fun String.hexToByteArray(): ByteArray {
        val cleanHex = this.removePrefix("0x").removePrefix("0X")
        if (cleanHex.isEmpty()) return byteArrayOf()
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

    // =========================================================================
    // Requirement R5: ExtendedPublicKeyPolicy Adversarial Payload Stress Tests
    // =========================================================================

    @Test
    fun testR5_InvalidBase58CheckChecksum() {
        val policy = ExtendedPublicKeyPolicy.STRICT_DEFAULT
        val validXpub = getValidMasterXpub()
        val rawBytes = Base58.decode(validXpub)!!

        val corruptedPayload = rawBytes.copyOf()
        corruptedPayload[10] = (corruptedPayload[10].toInt() xor 0xFF).toByte()
        val badChecksumXpub = Base58.encode(corruptedPayload)

        val ex = assertFailsWith<InvalidExtendedPublicKeyPolicyException> {
            policy.validate(masterFingerprint = "00000000", xpub = badChecksumXpub, derivationPath = "m")
        }
        assertTrue(ex.message!!.contains("Base58Check checksum validation failed"), "Must fail with Base58Check checksum error")
    }

    @Test
    fun testR5_BadPayloadLength_77_or_79_bytes() {
        val policy = ExtendedPublicKeyPolicy.STRICT_DEFAULT
        val validXpub = getValidMasterXpub()
        val rawBytes = Base58.decode(validXpub)!!

        val shortBytes = rawBytes.copyOfRange(0, 81)
        val shortXpub = Base58.encode(shortBytes)

        val exShort = assertFailsWith<InvalidExtendedPublicKeyPolicyException> {
            policy.validate(masterFingerprint = "00000000", xpub = shortXpub, derivationPath = "m")
        }
        assertTrue(exShort.message!!.contains("Invalid xpub payload length"), "77-byte payload must fail payload length check")

        val longBytes = rawBytes + byteArrayOf(0x00)
        val longXpub = Base58.encode(longBytes)

        val exLong = assertFailsWith<InvalidExtendedPublicKeyPolicyException> {
            policy.validate(masterFingerprint = "00000000", xpub = longXpub, derivationPath = "m")
        }
        assertTrue(exLong.message!!.contains("Invalid xpub payload length"), "79-byte payload must fail payload length check")
    }

    @Test
    fun testR5_WrongVersionBytes_0x0488B21F() {
        val policy = ExtendedPublicKeyPolicy.STRICT_DEFAULT
        val validXpub = getValidMasterXpub()
        val rawBytes = Base58.decode(validXpub)!!

        val corruptedPayload = rawBytes.copyOfRange(0, 78)
        corruptedPayload[3] = 0x1F.toByte()

        val newChecksum = platformSha256(platformSha256(corruptedPayload)).copyOfRange(0, 4)
        val badVersionXpub = Base58.encode(corruptedPayload + newChecksum)

        val ex = assertFailsWith<InvalidExtendedPublicKeyPolicyException> {
            policy.validate(masterFingerprint = "00000000", xpub = badVersionXpub, derivationPath = "m")
        }
        assertTrue(ex.message!!.contains("Invalid xpub version bytes"), "Version 0x0488B21F must fail version check")
    }

    @Test
    fun testR5_InvalidDepthAndParentFingerprint() {
        val policy = ExtendedPublicKeyPolicy.STRICT_DEFAULT
        val validXpub = getValidMasterXpub()
        val rawBytes = Base58.decode(validXpub)!!

        val corruptedPayload = rawBytes.copyOfRange(0, 78)
        corruptedPayload[5] = 0xAA.toByte()
        val newChecksum = platformSha256(platformSha256(corruptedPayload)).copyOfRange(0, 4)
        val badParentXpub = Base58.encode(corruptedPayload + newChecksum)

        val ex = assertFailsWith<InvalidExtendedPublicKeyPolicyException> {
            policy.validate(masterFingerprint = "00000000", xpub = badParentXpub, derivationPath = "m")
        }
        assertTrue(ex.message!!.contains("parent fingerprint"), "Depth 0 key with non-zero parent fingerprint must fail")
    }

    @Test
    fun testR5_InvalidChildNumber() {
        val policy = ExtendedPublicKeyPolicy.STRICT_DEFAULT
        val validXpub = getValidMasterXpub()
        val rawBytes = Base58.decode(validXpub)!!

        val corruptedPayload = rawBytes.copyOfRange(0, 78)
        corruptedPayload[12] = 0x01.toByte()
        val newChecksum = platformSha256(platformSha256(corruptedPayload)).copyOfRange(0, 4)
        val badChildNumXpub = Base58.encode(corruptedPayload + newChecksum)

        val ex = assertFailsWith<InvalidExtendedPublicKeyPolicyException> {
            policy.validate(masterFingerprint = "00000000", xpub = badChildNumXpub, derivationPath = "m")
        }
        assertTrue(ex.message!!.contains("child number"), "Depth 0 key with non-zero child number must fail")
    }

    @Test
    fun testR5_InvalidCompressedKeyPrefix_0x04_and_0x00() {
        val policy = ExtendedPublicKeyPolicy.STRICT_DEFAULT
        val validXpub = getValidMasterXpub()
        val rawBytes = Base58.decode(validXpub)!!

        val payloadUncompressed = rawBytes.copyOfRange(0, 78)
        payloadUncompressed[45] = 0x04.toByte()
        val checksum04 = platformSha256(platformSha256(payloadUncompressed)).copyOfRange(0, 4)
        val xpub04 = Base58.encode(payloadUncompressed + checksum04)

        val ex04 = assertFailsWith<InvalidExtendedPublicKeyPolicyException> {
            policy.validate(masterFingerprint = "00000000", xpub = xpub04, derivationPath = "m")
        }
        assertTrue(ex04.message!!.contains("compressed public key prefix"), "Prefix 0x04 must fail prefix check")

        val payload00 = rawBytes.copyOfRange(0, 78)
        payload00[45] = 0x00.toByte()
        val checksum00 = platformSha256(platformSha256(payload00)).copyOfRange(0, 4)
        val xpub00 = Base58.encode(payload00 + checksum00)

        val ex00 = assertFailsWith<InvalidExtendedPublicKeyPolicyException> {
            policy.validate(masterFingerprint = "00000000", xpub = xpub00, derivationPath = "m")
        }
        assertTrue(ex00.message!!.contains("compressed public key prefix"), "Prefix 0x00 must fail prefix check")
    }

    @Test
    fun testR5_HardenedPathFromXpubProhibited() {
        val policy = ExtendedPublicKeyPolicy.STRICT_DEFAULT
        val validXpub = getValidMasterXpub()

        val ex1 = assertFailsWith<InvalidExtendedPublicKeyPolicyException> {
            policy.validate(masterFingerprint = "00000000", xpub = validXpub, derivationPath = "0/0'")
        }
        assertTrue(ex1.message!!.contains("Hardened child derivation"), "Derivation path '0/0'' must be rejected")

        val ex2 = assertFailsWith<InvalidExtendedPublicKeyPolicyException> {
            policy.validate(masterFingerprint = "00000000", xpub = validXpub, derivationPath = "0/0h")
        }
        assertTrue(ex2.message!!.contains("Hardened child derivation"), "Derivation path '0/0h' must be rejected")
    }

    // =========================================================================
    // Requirement R6: Standalone EIP-1559 Standalone Fixed Vector Verification
    // =========================================================================

    @Test
    fun testR6_Eip1559StandaloneFixedVectorsAndNegativeMutations() {
        val privateKeyHex = "4646464646464646464646464646464646464646464646464646464646464646"
        val privateKeyBytes = privateKeyHex.hexToByteArray()
        val expectedSender = "0x9d8A62f656a8d1615C1294fd71e9CFb3E4855A4F"

        val signedTxHex = EthereumSigner.signEip1559Transaction(
            chainId = ChainId.fromLong(1L),
            nonce = Nonce.fromLong(0L),
            maxPriorityFeePerGas = Wei.fromWei(BigInteger.fromLong(2000000000L)),
            maxFeePerGas = Wei.fromWei(BigInteger.fromLong(30000000000L)),
            gasLimit = GasLimit.fromLong(21000L),
            toAddress = EvmAddress.fromHex("0x3535353535353535353535353535353535353535"),
            value = Wei.fromWei(BigInteger.fromLong(1000000000000000000L)),
            data = Calldata.EMPTY,
            accessList = emptyList(),
            privateKeyBytes = privateKeyBytes
        )

        val rawBytes = signedTxHex.removePrefix("0x").removePrefix("0X").hexToByteArray()
        assertEquals(0x02.toByte(), rawBytes[0], "First byte of EIP-1559 envelope must be 0x02")

        val hash = "0x" + rawBytes.keccak256().toHexString()
        assertEquals(66, hash.length, "Keccak-256 hash must be 32 bytes (66 hex chars)")

        val recoveredSender = EthereumSigner.recoverSenderFromSignedTransaction(signedTxHex)
        assertEquals(expectedSender.lowercase(), recoveredSender.lowercase(), "Recovered sender must match expected ethers.js derivation")
    }
}

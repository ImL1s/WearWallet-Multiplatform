package com.cbstudio.wearwallet.core.security

import io.github.iml1s.crypto.Base58
import io.github.iml1s.crypto.Bip32
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Unit tests for [ExtendedPublicKeyPolicy] asserting all 8 structural validation checkpoints (Requirement R5).
 */
class ExtendedPublicKeyPolicyTest {

    private fun getValidMasterXpub(): String {
        val seed = "000102030405060708090a0b0c0d0e0f".hexToByteArray()
        val master = Bip32.masterKeyFromSeed(seed)
        return master.serializePublic()
    }

    private fun createTestnetTpub(xpub: String): String {
        val rawBytes = Base58.decode(xpub)!!
        val payload = rawBytes.copyOfRange(0, 78)
        payload[0] = 0x04.toByte()
        payload[1] = 0x35.toByte()
        payload[2] = 0x87.toByte()
        payload[3] = 0xCF.toByte()
        val checksum = platformSha256(platformSha256(payload)).copyOfRange(0, 4)
        return Base58.encode(payload + checksum)
    }

    private fun String.hexToByteArray(): ByteArray {
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    // ==========================================
    // Checkpoint 1: Version bytes validation
    // ==========================================

    @Test
    fun test_checkpoint_1_version_bytes_mainnet_xpub_and_testnet_tpub() {
        val policy = ExtendedPublicKeyPolicy.STRICT_DEFAULT
        val masterXpub = getValidMasterXpub()
        val testnetTpub = createTestnetTpub(masterXpub)

        // Mainnet policy accepts mainnet version bytes (0x0488B21E)
        policy.validate(masterFingerprint = "00000000", xpub = masterXpub, derivationPath = "m", isTestnet = false)

        // Testnet policy accepts testnet version bytes (0x043587CF)
        policy.validate(masterFingerprint = "00000000", xpub = testnetTpub, derivationPath = "m", isTestnet = true)

        // Mainnet xpub on testnet MUST fail version byte check
        assertFailsWith<InvalidExtendedPublicKeyPolicyException> {
            policy.validate(masterFingerprint = "00000000", xpub = masterXpub, derivationPath = "m", isTestnet = true)
        }

        // Testnet tpub on mainnet MUST fail version byte check
        assertFailsWith<InvalidExtendedPublicKeyPolicyException> {
            policy.validate(masterFingerprint = "00000000", xpub = testnetTpub, derivationPath = "m", isTestnet = false)
        }
    }

    // ==========================================
    // Checkpoint 2: Depth byte validation
    // ==========================================

    @Test
    fun test_checkpoint_2_depth_byte_validation() {
        val policy = ExtendedPublicKeyPolicy.STRICT_DEFAULT
        val masterXpub = getValidMasterXpub()

        // Master xpub has depth 0 -> valid
        policy.validate(masterFingerprint = "00000000", xpub = masterXpub, derivationPath = "m", isTestnet = false)

        // Corrupt depth byte to invalid value
        val rawBytes = Base58.decode(masterXpub)!!
        val corruptedPayload = rawBytes.copyOfRange(0, 78)
        corruptedPayload[4] = 1.toByte() // change depth to 1 without matching parent fingerprint
        val newChecksum = platformSha256(platformSha256(corruptedPayload)).copyOfRange(0, 4)
        val corruptedXpub = Base58.encode(corruptedPayload + newChecksum)

        assertFailsWith<InvalidExtendedPublicKeyPolicyException> {
            policy.validate(masterFingerprint = "12345678", xpub = corruptedXpub, derivationPath = "m", isTestnet = false)
        }
    }

    // ==========================================
    // Checkpoint 3: Parent fingerprint validation
    // ==========================================

    @Test
    fun test_checkpoint_3_parent_fingerprint_validation() {
        val policy = ExtendedPublicKeyPolicy.STRICT_DEFAULT
        val masterXpub = getValidMasterXpub()

        // Depth 0 xpub MUST have parent fingerprint 00000000
        policy.validate(masterFingerprint = "00000000", xpub = masterXpub, derivationPath = "m", isTestnet = false)

        // Corrupt parent fingerprint on depth 0 key -> MUST fail
        val rawBytes = Base58.decode(masterXpub)!!
        val corruptedPayload = rawBytes.copyOfRange(0, 78)
        corruptedPayload[5] = 0x12.toByte() // non-zero parent fingerprint byte
        val newChecksum = platformSha256(platformSha256(corruptedPayload)).copyOfRange(0, 4)
        val corruptedXpub = Base58.encode(corruptedPayload + newChecksum)

        val ex = assertFailsWith<InvalidExtendedPublicKeyPolicyException> {
            policy.validate(masterFingerprint = "12345678", xpub = corruptedXpub, derivationPath = "m", isTestnet = false)
        }
        assertTrue(ex.message!!.contains("parent fingerprint"))
    }

    // ==========================================
    // Checkpoint 4: Child number validation
    // ==========================================

    @Test
    fun test_checkpoint_4_child_number_validation() {
        val policy = ExtendedPublicKeyPolicy.STRICT_DEFAULT
        val masterXpub = getValidMasterXpub()

        // Depth 0 key must have child number 0
        policy.validate(masterFingerprint = "00000000", xpub = masterXpub, derivationPath = "m", isTestnet = false)

        // Corrupt child number on depth 0 key -> MUST fail
        val rawBytes = Base58.decode(masterXpub)!!
        val corruptedPayload = rawBytes.copyOfRange(0, 78)
        corruptedPayload[12] = 1.toByte() // child number = 1
        val newChecksum = platformSha256(platformSha256(corruptedPayload)).copyOfRange(0, 4)
        val corruptedXpub = Base58.encode(corruptedPayload + newChecksum)

        val ex = assertFailsWith<InvalidExtendedPublicKeyPolicyException> {
            policy.validate(masterFingerprint = "00000000", xpub = corruptedXpub, derivationPath = "m", isTestnet = false)
        }
        assertTrue(ex.message!!.contains("child number"))
    }

    // ==========================================
    // Checkpoint 5: Account index check
    // ==========================================

    @Test
    fun test_checkpoint_5_account_index_check() {
        val policy = ExtendedPublicKeyPolicy.STRICT_DEFAULT
        val seed = "000102030405060708090a0b0c0d0e0f".hexToByteArray()
        val accountKey = Bip32.derivePath(seed, "m/44'/60'/0'")
        val accountXpub = accountKey.serializePublic()

        // Account xpub (depth 3, child number 0x80000000) with derivation path "m/44'/60'/0'" -> valid
        policy.validate(masterFingerprint = "3442193e", xpub = accountXpub, derivationPath = "m/44'/60'/0'", isTestnet = false)

        // Account index mismatch: path specifies account 1 ("m/44'/60'/1'") but xpub has account 0 -> MUST fail
        assertFailsWith<InvalidExtendedPublicKeyPolicyException> {
            policy.validate(masterFingerprint = "3442193e", xpub = accountXpub, derivationPath = "m/44'/60'/1'", isTestnet = false)
        }
    }

    // ==========================================
    // Checkpoint 6: Compressed public key prefix & SECP256k1 validity
    // ==========================================

    @Test
    fun test_checkpoint_6_compressed_public_key_prefix_and_secp256k1_validity() {
        val policy = ExtendedPublicKeyPolicy.STRICT_DEFAULT
        val masterXpub = getValidMasterXpub()

        // Valid compressed public key prefix (0x02 or 0x03) and valid secp256k1 curve point -> passes
        policy.validate(masterFingerprint = "00000000", xpub = masterXpub, derivationPath = "m", isTestnet = false)

        // Corrupt public key prefix to 0x04 (uncompressed) or invalid byte -> MUST fail
        val rawBytes = Base58.decode(masterXpub)!!
        val corruptedPayload = rawBytes.copyOfRange(0, 78)
        corruptedPayload[45] = 0x04.toByte() // invalid prefix
        val newChecksum = platformSha256(platformSha256(corruptedPayload)).copyOfRange(0, 4)
        val corruptedXpub = Base58.encode(corruptedPayload + newChecksum)

        val ex = assertFailsWith<InvalidExtendedPublicKeyPolicyException> {
            policy.validate(masterFingerprint = "00000000", xpub = corruptedXpub, derivationPath = "m", isTestnet = false)
        }
        assertTrue(ex.message!!.contains("compressed public key prefix"))
    }

    // ==========================================
    // Checkpoint 7: Derivation path structure check (disallows hardened child paths)
    // ==========================================

    @Test
    fun test_checkpoint_7_derivation_path_structure_check_disallows_hardened_child_paths() {
        val policy = ExtendedPublicKeyPolicy.STRICT_DEFAULT
        val masterXpub = getValidMasterXpub()

        // Relative unhardened child derivation -> valid
        policy.validate(masterFingerprint = "00000000", xpub = masterXpub, derivationPath = "0/0", isTestnet = false)

        // Relative hardened child derivation ("0/0'") -> MUST fail
        val ex1 = assertFailsWith<InvalidExtendedPublicKeyPolicyException> {
            policy.validate(masterFingerprint = "00000000", xpub = masterXpub, derivationPath = "0/0'", isTestnet = false)
        }
        assertTrue(ex1.message!!.contains("Hardened child derivation"))

        // Hardened child derivation using 'h' ("0/0h") -> MUST fail
        val ex2 = assertFailsWith<InvalidExtendedPublicKeyPolicyException> {
            policy.validate(masterFingerprint = "00000000", xpub = masterXpub, derivationPath = "0/0h", isTestnet = false)
        }
        assertTrue(ex2.message!!.contains("Hardened child derivation"))
    }

    // ==========================================
    // Checkpoint 8: Master fingerprint binding and format validation
    // ==========================================

    @Test
    fun test_checkpoint_8_master_fingerprint_binding_and_format_validation() {
        val policy = ExtendedPublicKeyPolicy.STRICT_DEFAULT
        val masterXpub = getValidMasterXpub()

        // Valid 8-char hex master fingerprint -> valid
        policy.validate(masterFingerprint = "12345678", xpub = masterXpub, derivationPath = "0/0", isTestnet = false)

        // Invalid format (not 8 hex chars) -> MUST fail
        assertFailsWith<InvalidExtendedPublicKeyPolicyException> {
            policy.validate(masterFingerprint = "12345", xpub = masterXpub, derivationPath = "0/0", isTestnet = false)
        }

        // Empty master fingerprint when allowEmptyMasterFingerprint=false -> MUST fail
        assertFailsWith<InvalidExtendedPublicKeyPolicyException> {
            policy.validate(masterFingerprint = "", xpub = masterXpub, derivationPath = "0/0", isTestnet = false)
        }

        // Master fingerprint mismatch against policy.expectedMasterFingerprint -> MUST fail
        val expectedPolicy = ExtendedPublicKeyPolicy(expectedMasterFingerprint = "12345678")
        assertFailsWith<InvalidExtendedPublicKeyPolicyException> {
            expectedPolicy.validate(masterFingerprint = "A1B2C3D4", xpub = masterXpub, derivationPath = "0/0", isTestnet = false)
        }
        expectedPolicy.validate(masterFingerprint = "12345678", xpub = masterXpub, derivationPath = "0/0", isTestnet = false)
    }
}

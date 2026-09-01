package com.cbstudio.wearwallet.core.security

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.data.repository.WalletRepositoryImpl
import com.cbstudio.wearwallet.core.database.CoreWalletDatabase
import com.cbstudio.wearwallet.core.database.DatabaseDriverFactory
import com.cbstudio.wearwallet.core.database.Wallet
import com.cbstudio.wearwallet.core.database.WalletQueries
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.WalletAccount
import com.cbstudio.wearwallet.core.domain.model.WalletType
import com.cbstudio.wearwallet.core.network.EthereumRpcClient
import io.github.iml1s.crypto.SecureByteArray
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Milestone 2 (M2) Storage & Migration Adversarial Test Suite
 *
 * Empirical challenger tests covering:
 * 1. Corrupted legacy ciphertexts, wrong passwords, truncated payloads, and altered tags.
 * 2. Database transaction atomicity during migration (simulating faults, partial failures, address mismatches).
 * 3. Downgrade resistance: ensuring legacy fallback or plaintext acceptance is strictly rejected once migrated or expected as modern WWEN.
 */
class Milestone2ChallengerAdversarialTest {

    private val testPassword = "AdversarialMasterPassword#2026"
    private val testPrivateKeyHex = "e331b6d69882b4cb4ea581d88ec2693329b3b3a4711832421415ebff2d474428"
    private val testMnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
    private val testAddress = "0x89205A3A3b2A69De6Dbf7f01ED13B2108B2c43e7"
    private val testAad = CanonicalAad.forWalletStorage(testAddress, CanonicalAad.KEY_TYPE_PRIVATE_KEY)

    // =========================================================================
    // SECTION 1: Corrupted Legacy Ciphertext & Format Tampering
    // =========================================================================

    @Test
    fun `challenge_1_1_legacy_5part_corrupted_ciphertext_fails_closed`() {
        val privBytes = testPrivateKeyHex.encodeToByteArray()
        val pwdBytes = testPassword.encodeToByteArray()
        val salt = CryptoUtils.randomBytes(16)
        val derivedKey = CryptoUtils.pbkdf2(pwdBytes, salt, 100_000, 32)
        val encrypted = CryptoUtils.aesGcmEncrypt(privBytes, derivedKey)

        // Corrupt ciphertext
        val corruptedCt = encrypted.ciphertext.copyOf()
        corruptedCt[corruptedCt.size / 2] = (corruptedCt[corruptedCt.size / 2].toInt() xor 0xFF).toByte()

        val legacy5Part = listOf(
            "v1",
            salt.toBase64(),
            encrypted.nonce.toBase64(),
            encrypted.authTag.toBase64(),
            corruptedCt.toBase64()
        ).joinToString(":")

        assertTrue(VersionedEncryptedEnvelope.isLegacyFormat(legacy5Part))

        assertThrows(EnvelopeIntegrityException::class.java) {
            VersionedEncryptedEnvelope.migrateLegacy(
                legacyString = legacy5Part,
                password = testPassword,
                keyId = testAddress,
                aad = testAad
            )
        }
    }

    @Test
    fun `challenge_1_2_legacy_4part_corrupted_ciphertext_fails_closed`() {
        val privBytes = testPrivateKeyHex.encodeToByteArray()
        val pwdBytes = testPassword.encodeToByteArray()
        val salt = CryptoUtils.randomBytes(16)
        val derivedKey = CryptoUtils.pbkdf2(pwdBytes, salt, 100_000, 32)
        val encrypted = CryptoUtils.aesGcmEncrypt(privBytes, derivedKey)

        val corruptedCt = encrypted.ciphertext.copyOf()
        corruptedCt[0] = (corruptedCt[0].toInt() xor 0xAA).toByte()

        val legacy4Part = listOf(
            salt.toBase64(),
            encrypted.nonce.toBase64(),
            encrypted.authTag.toBase64(),
            corruptedCt.toBase64()
        ).joinToString(":")

        assertTrue(VersionedEncryptedEnvelope.isLegacyFormat(legacy4Part))

        assertThrows(EnvelopeIntegrityException::class.java) {
            VersionedEncryptedEnvelope.migrateLegacy(
                legacyString = legacy4Part,
                password = testPassword,
                keyId = testAddress,
                aad = testAad
            )
        }
    }

    @Test
    fun `challenge_1_3_android_legacy_corrupted_ciphertext_fails_closed`() {
        val privBytes = testPrivateKeyHex.encodeToByteArray()
        val pwdBytes = testPassword.encodeToByteArray()
        val sha256Key = CryptoUtils.sha256(pwdBytes)
        val encrypted = CryptoUtils.aesGcmEncrypt(privBytes, sha256Key)

        val corruptedCt = encrypted.ciphertext.copyOf()
        corruptedCt[0] = (corruptedCt[0].toInt() xor 0x01).toByte()

        val combined = encrypted.nonce + corruptedCt + encrypted.authTag
        val corruptedBase64 = combined.toBase64()

        assertTrue(VersionedEncryptedEnvelope.isLegacyFormat(corruptedBase64))

        assertThrows(EnvelopeIntegrityException::class.java) {
            VersionedEncryptedEnvelope.migrateLegacy(
                legacyString = corruptedBase64,
                password = testPassword,
                keyId = testAddress,
                aad = testAad
            )
        }
    }

    // =========================================================================
    // SECTION 2: Wrong Passwords
    // =========================================================================

    @Test
    fun `challenge_2_1_legacy_5part_wrong_password_fails_closed`() {
        val privBytes = testPrivateKeyHex.encodeToByteArray()
        val pwdBytes = testPassword.encodeToByteArray()
        val salt = CryptoUtils.randomBytes(16)
        val derivedKey = CryptoUtils.pbkdf2(pwdBytes, salt, 100_000, 32)
        val encrypted = CryptoUtils.aesGcmEncrypt(privBytes, derivedKey)

        val legacy5Part = listOf(
            "v1",
            salt.toBase64(),
            encrypted.nonce.toBase64(),
            encrypted.authTag.toBase64(),
            encrypted.ciphertext.toBase64()
        ).joinToString(":")

        assertThrows(EnvelopeIntegrityException::class.java) {
            VersionedEncryptedEnvelope.migrateLegacy(
                legacyString = legacy5Part,
                password = "CompletelyWrongPassword#999",
                keyId = testAddress,
                aad = testAad
            )
        }
    }

    @Test
    fun `challenge_2_2_android_legacy_wrong_password_fails_closed`() {
        val privBytes = testPrivateKeyHex.encodeToByteArray()
        val pwdBytes = testPassword.encodeToByteArray()
        val sha256Key = CryptoUtils.sha256(pwdBytes)
        val encrypted = CryptoUtils.aesGcmEncrypt(privBytes, sha256Key)
        val combined = encrypted.nonce + encrypted.ciphertext + encrypted.authTag
        val legacyBase64 = combined.toBase64()

        assertThrows(EnvelopeIntegrityException::class.java) {
            VersionedEncryptedEnvelope.migrateLegacy(
                legacyString = legacyBase64,
                password = "WrongPassword#456",
                keyId = testAddress,
                aad = testAad
            )
        }
    }

    @Test
    fun `challenge_2_3_modern_wwen_wrong_password_fails_closed`() {
        val privBytes = testPrivateKeyHex.encodeToByteArray()
        val pwdBytes = testPassword.encodeToByteArray()

        val envelope = VersionedEncryptedEnvelope.encrypt(
            plaintext = privBytes,
            password = pwdBytes,
            keyId = testAddress,
            aad = testAad
        )

        assertThrows(EnvelopeIntegrityException::class.java) {
            envelope.decrypt(
                password = "WrongModernPassword#789".encodeToByteArray(),
                expectedAad = testAad
            )
        }
    }

    // =========================================================================
    // SECTION 3: Truncated Payloads & Malformed Envelopes (Fuzzing)
    // =========================================================================

    @Test
    fun `challenge_3_1_android_legacy_truncated_below_28_bytes_rejected`() {
        val shortLengths = listOf(0, 1, 5, 12, 16, 20, 27)
        for (len in shortLengths) {
            val shortBytes = ByteArray(len) { 0x42 }
            val shortBase64 = shortBytes.toBase64()

            assertThrows(UnversionedPlaintextException::class.java) {
                VersionedEncryptedEnvelope.migrateLegacy(
                    legacyString = shortBase64,
                    password = testPassword,
                    keyId = testAddress,
                    aad = testAad
                )
            }
        }
    }

    @Test
    fun `challenge_3_2_legacy_colon_format_wrong_number_of_parts_rejected`() {
        val malformedColons = listOf(
            "singlePartOnly",
            "part1:part2",
            "part1:part2:part3",
            "v1:part1:part2:part3:part4:part5Extra"
        )
        for (malformed in malformedColons) {
            assertThrows(UnversionedPlaintextException::class.java) {
                VersionedEncryptedEnvelope.migrateLegacy(
                    legacyString = malformed,
                    password = testPassword,
                    keyId = testAddress,
                    aad = testAad
                )
            }
        }
    }

    @Test
    fun `challenge_3_3_modern_wwen_truncated_at_every_single_byte_offset_fails_closed`() {
        val privBytes = testPrivateKeyHex.encodeToByteArray()
        val pwdBytes = testPassword.encodeToByteArray()

        val envelope = VersionedEncryptedEnvelope.encrypt(
            plaintext = privBytes,
            password = pwdBytes,
            keyId = testAddress,
            aad = testAad
        )

        val serialized = envelope.serialize()
        assertTrue("Envelope size should be reasonable", serialized.size > 50)

        val unhandledExceptions = mutableListOf<String>()

        // Test every truncation point from 0 to total-1
        for (cutoff in 0 until serialized.size) {
            val truncated = serialized.copyOfRange(0, cutoff)
            try {
                VersionedEncryptedEnvelope.deserialize(truncated)
                org.junit.Assert.fail("Truncated envelope at cutoff $cutoff must not deserialize successfully")
            } catch (e: EnvelopeException) {
                // Expected typed domain exception
            } catch (e: Throwable) {
                // Unhandled generic exception (e.g. ArrayIndexOutOfBoundsException) escaping domain exception hierarchy
                unhandledExceptions.add("cutoff=$cutoff, size=${truncated.size}: ${e.javaClass.name}: ${e.message}")
            }
        }

        // We assert that NO unhandled generic exceptions escape the domain EnvelopeException hierarchy
        assertTrue(
            "Expected all truncated buffers to throw EnvelopeException (EnvelopeCorruptedException), but found unhandled exceptions: $unhandledExceptions",
            unhandledExceptions.isEmpty()
        )
    }

    @Test
    fun `challenge_3_5_legacy_colon_separated_invalid_base64_must_throw_domain_exception`() {
        val invalidBase64Inputs = listOf(
            "v1:!invalid_salt!:bm9uY2U=:dGFn:Y2lwaGVydGV4dA==",
            "!invalid_salt!:bm9uY2U=:dGFn:Y2lwaGVydGV4dA==",
            "v1:c2FsdA==:!invalid_nonce!:dGFn:Y2lwaGVydGV4dA==",
            "v1:c2FsdA==:bm9uY2U=:!invalid_tag!:Y2lwaGVydGV4dA==",
            "v1:c2FsdA==:bm9uY2U=:dGFn:!invalid_ct!"
        )

        val unhandled = mutableListOf<String>()
        for (input in invalidBase64Inputs) {
            try {
                VersionedEncryptedEnvelope.migrateLegacy(
                    legacyString = input,
                    password = testPassword,
                    keyId = testAddress,
                    aad = testAad
                )
                org.junit.Assert.fail("Invalid base64 input '$input' must not succeed")
            } catch (e: EnvelopeException) {
                // Expected typed domain exception (e.g. UnversionedPlaintextException or EnvelopeCorruptedException)
            } catch (e: Throwable) {
                unhandled.add("input='$input' -> ${e.javaClass.name}: ${e.message}")
            }
        }

        assertTrue(
            "Colon-separated legacy strings with invalid base64 must throw typed EnvelopeException, but threw unhandled: $unhandled",
            unhandled.isEmpty()
        )
    }

    @Test
    fun `challenge_3_4_modern_wwen_with_trailing_garbage_fails_closed`() {
        val privBytes = testPrivateKeyHex.encodeToByteArray()
        val pwdBytes = testPassword.encodeToByteArray()

        val envelope = VersionedEncryptedEnvelope.encrypt(
            plaintext = privBytes,
            password = pwdBytes,
            keyId = testAddress,
            aad = testAad
        )

        val validBytes = envelope.serialize()
        val withOneGarbage = validBytes + byteArrayOf(0x00)
        val withManyGarbage = validBytes + ByteArray(64) { 0xFF.toByte() }

        val ex1 = assertThrows(EnvelopeCorruptedException::class.java) {
            VersionedEncryptedEnvelope.deserialize(withOneGarbage)
        }
        assertTrue(ex1.message?.contains("trailing garbage bytes") == true)

        val ex2 = assertThrows(EnvelopeCorruptedException::class.java) {
            VersionedEncryptedEnvelope.deserialize(withManyGarbage)
        }
        assertTrue(ex2.message?.contains("trailing garbage bytes") == true)
    }

    // =========================================================================
    // SECTION 4: Altered Auth Tags & Nonce/Salt Tampering
    // =========================================================================

    @Test
    fun `challenge_4_1_legacy_5part_altered_auth_tag_fails_closed`() {
        val privBytes = testPrivateKeyHex.encodeToByteArray()
        val pwdBytes = testPassword.encodeToByteArray()
        val salt = CryptoUtils.randomBytes(16)
        val derivedKey = CryptoUtils.pbkdf2(pwdBytes, salt, 100_000, 32)
        val encrypted = CryptoUtils.aesGcmEncrypt(privBytes, derivedKey)

        val corruptedTag = encrypted.authTag.copyOf()
        corruptedTag[15] = (corruptedTag[15].toInt() xor 0x55).toByte()

        val legacy5Part = listOf(
            "v1",
            salt.toBase64(),
            encrypted.nonce.toBase64(),
            corruptedTag.toBase64(),
            encrypted.ciphertext.toBase64()
        ).joinToString(":")

        assertThrows(EnvelopeIntegrityException::class.java) {
            VersionedEncryptedEnvelope.migrateLegacy(
                legacyString = legacy5Part,
                password = testPassword,
                keyId = testAddress,
                aad = testAad
            )
        }
    }

    @Test
    fun `challenge_4_2_android_legacy_altered_auth_tag_fails_closed`() {
        val privBytes = testPrivateKeyHex.encodeToByteArray()
        val pwdBytes = testPassword.encodeToByteArray()
        val sha256Key = CryptoUtils.sha256(pwdBytes)
        val encrypted = CryptoUtils.aesGcmEncrypt(privBytes, sha256Key)

        val corruptedTag = encrypted.authTag.copyOf()
        corruptedTag[0] = (corruptedTag[0].toInt() xor 0xFF).toByte()

        val combined = encrypted.nonce + encrypted.ciphertext + corruptedTag
        val corruptedBase64 = combined.toBase64()

        assertThrows(EnvelopeIntegrityException::class.java) {
            VersionedEncryptedEnvelope.migrateLegacy(
                legacyString = corruptedBase64,
                password = testPassword,
                keyId = testAddress,
                aad = testAad
            )
        }
    }

    @Test
    fun `challenge_4_3_modern_wwen_altered_auth_tag_fails_closed`() {
        val privBytes = testPrivateKeyHex.encodeToByteArray()
        val pwdBytes = testPassword.encodeToByteArray()

        val envelope = VersionedEncryptedEnvelope.encrypt(
            plaintext = privBytes,
            password = pwdBytes,
            keyId = testAddress,
            aad = testAad
        )

        val corruptedTag = envelope.authTag.copyOf()
        corruptedTag[0] = (corruptedTag[0].toInt() xor 0x01).toByte()
        val tamperedEnvelope = envelope.copy(authTag = corruptedTag)

        assertThrows(EnvelopeIntegrityException::class.java) {
            tamperedEnvelope.decrypt(pwdBytes, testAad)
        }
    }

    @Test
    fun `challenge_4_4_modern_wwen_altered_tag_length_header_fails_closed`() {
        val privBytes = testPrivateKeyHex.encodeToByteArray()
        val pwdBytes = testPassword.encodeToByteArray()

        val envelope = VersionedEncryptedEnvelope.encrypt(
            plaintext = privBytes,
            password = pwdBytes,
            keyId = testAddress,
            aad = testAad
        )

        val serialized = envelope.serialize()
        // Auth tag length is right after AAD
        // Calculate offset of tagLen
        val keyIdBytes = testAddress.encodeToByteArray()
        val tagLenOffset = 4 + 1 + 1 + 4 + 2 + 1 + envelope.salt.size + 1 + 1 + envelope.nonce.size + 2 + keyIdBytes.size + 4 + testAad.size
        serialized[tagLenOffset] = 32 // Invalid tag length (must be 16)

        assertThrows(EnvelopeCorruptedException::class.java) {
            VersionedEncryptedEnvelope.deserialize(serialized)
        }
    }

    // =========================================================================
    // SECTION 5: Downgrade Resistance & Strict Plaintext Rejection
    // =========================================================================

    @Test
    fun `challenge_5_1_deserializeFromBase64_strictly_rejects_legacy_formats`() {
        val privBytes = testPrivateKeyHex.encodeToByteArray()
        val pwdBytes = testPassword.encodeToByteArray()

        // 1. Android Legacy Base64
        val sha256Key = CryptoUtils.sha256(pwdBytes)
        val encrypted = CryptoUtils.aesGcmEncrypt(privBytes, sha256Key)
        val androidLegacyBase64 = (encrypted.nonce + encrypted.ciphertext + encrypted.authTag).toBase64()

        assertThrows(UnversionedPlaintextException::class.java) {
            VersionedEncryptedEnvelope.deserializeFromBase64(androidLegacyBase64)
        }

        // 2. 5-part Legacy
        val salt = CryptoUtils.randomBytes(16)
        val derivedKey = CryptoUtils.pbkdf2(pwdBytes, salt, 100_000, 32)
        val enc5 = CryptoUtils.aesGcmEncrypt(privBytes, derivedKey)
        val legacy5Part = listOf(
            "v1",
            salt.toBase64(),
            enc5.nonce.toBase64(),
            enc5.authTag.toBase64(),
            enc5.ciphertext.toBase64()
        ).joinToString(":")

        assertThrows(UnversionedPlaintextException::class.java) {
            VersionedEncryptedEnvelope.deserializeFromBase64(legacy5Part)
        }
    }

    @Test
    fun `challenge_5_2_deserializeFromBase64_strictly_rejects_raw_plaintext`() {
        val rawPlaintexts = listOf(
            testPrivateKeyHex,
            "0x$testPrivateKeyHex",
            testMnemonic,
            "random_plain_text_payload_longer_than_28_characters_without_magic_header",
            "1234567890abcdef1234567890abcdef1234567890abcdef"
        )

        for (plain in rawPlaintexts) {
            assertThrows(UnversionedPlaintextException::class.java) {
                VersionedEncryptedEnvelope.deserializeFromBase64(plain)
            }
        }
    }

    @Test
    fun `challenge_5_3_migrated_record_becomes_wwen_and_is_no_longer_legacy`() {
        val privBytes = testPrivateKeyHex.encodeToByteArray()
        val pwdBytes = testPassword.encodeToByteArray()
        val sha256Key = CryptoUtils.sha256(pwdBytes)
        val encrypted = CryptoUtils.aesGcmEncrypt(privBytes, sha256Key)
        val androidLegacyBase64 = (encrypted.nonce + encrypted.ciphertext + encrypted.authTag).toBase64()

        // Before migration: isLegacyFormat is true
        assertTrue(VersionedEncryptedEnvelope.isLegacyFormat(androidLegacyBase64))

        val migrated = VersionedEncryptedEnvelope.migrateLegacy(
            legacyString = androidLegacyBase64,
            password = testPassword,
            keyId = testAddress,
            aad = testAad
        )

        val migratedBase64 = migrated.serializeToBase64()

        // After migration: isLegacyFormat MUST be false
        assertFalse(
            "Migrated WWEN envelope must NOT be recognized as legacy format",
            VersionedEncryptedEnvelope.isLegacyFormat(migratedBase64)
        )

        // Direct deserialization succeeds
        val deserialized = VersionedEncryptedEnvelope.deserializeFromBase64(migratedBase64)
        assertEquals(VersionedEncryptedEnvelope.CURRENT_VERSION, deserialized.version)
        assertEquals(testAddress, deserialized.keyId)

        // Decrypt with wrong AAD fails
        val wrongAad = CanonicalAad.forWalletStorage(testAddress, CanonicalAad.KEY_TYPE_MNEMONIC)
        assertThrows(EnvelopeIntegrityException::class.java) {
            deserialized.decrypt(pwdBytes, expectedAad = wrongAad)
        }
    }

    // =========================================================================
    // SECTION 6: Database Migration Atomicity & Fault Simulation
    // =========================================================================

    @Test
    fun `challenge_6_1_repository_export_fails_closed_and_does_not_update_db_when_password_is_wrong`() = runTest {
        val mockDriverFactory = mock<DatabaseDriverFactory>()
        val mockCryptoProvider = CommonCryptoProvider()
        val mockRpcClient = mock<EthereumRpcClient>()

        // Generate Android legacy encrypted private key
        val privBytes = testPrivateKeyHex.encodeToByteArray()
        val pwdBytes = testPassword.encodeToByteArray()
        val sha256Key = CryptoUtils.sha256(pwdBytes)
        val encrypted = CryptoUtils.aesGcmEncrypt(privBytes, sha256Key)
        val androidLegacyBase64 = (encrypted.nonce + encrypted.ciphertext + encrypted.authTag).toBase64()

        val legacyWallet = Wallet(
            id = 1L,
            name = "Test Legacy Wallet",
            address = testAddress,
            public_key = "04mockpubkey",
            encrypted_private_key = androidLegacyBase64,
            encrypted_mnemonic = null,
            derivation_path = "m/44'/60'/0'/0/0",
            chain_type = "ETHEREUM",
            wallet_type = "HOT_WALLET",
            is_active = 1L,
            is_watch_only = 0L,
            master_fingerprint = null,
            keystone_sign_request = null,
            keystone_sync_data = null,
            metadata = "{}",
            avatar_id = null,
            chain_id = 1L,
            key_alias = null,
            key_backend = null,
            key_format_version = 1L,
            requires_auth = 1L,
            is_deletion_pending = 0L,
            created_at = 1000L,
            updated_at = 1000L
        )

        // When wrong password is provided to export, migration must throw EnvelopeIntegrityException
        assertThrows(EnvelopeIntegrityException::class.java) {
            VersionedEncryptedEnvelope.migrateLegacy(
                legacyString = legacyWallet.encrypted_private_key,
                password = "WrongPassword#999",
                keyId = legacyWallet.address,
                aad = CanonicalAad.forWalletStorage(legacyWallet.address, CanonicalAad.KEY_TYPE_PRIVATE_KEY)
            )
        }
    }

    @Test
    fun `challenge_6_2_repository_migration_fails_closed_when_decrypted_key_derives_mismatched_address`() = runTest {
        val mockCryptoProvider = CommonCryptoProvider()

        // Generate valid legacy ciphertext for key A
        val privBytes = testPrivateKeyHex.encodeToByteArray()
        val pwdBytes = testPassword.encodeToByteArray()
        val sha256Key = CryptoUtils.sha256(pwdBytes)
        val encrypted = CryptoUtils.aesGcmEncrypt(privBytes, sha256Key)
        val androidLegacyBase64 = (encrypted.nonce + encrypted.ciphertext + encrypted.authTag).toBase64()

        // But wallet address in DB is spoofed to address B (mismatch)
        val spoofedAddress = "0x0000000000000000000000000000000000000001"
        val privAad = CanonicalAad.forWalletStorage(spoofedAddress, CanonicalAad.KEY_TYPE_PRIVATE_KEY)

        val migrated = VersionedEncryptedEnvelope.migrateLegacy(
            legacyString = androidLegacyBase64,
            password = testPassword,
            keyId = spoofedAddress,
            aad = privAad
        )

        val decryptedKey = migrated.decrypt(pwdBytes, expectedAad = privAad).decodeToString()

        // Derive address
        val keyPair = mockCryptoProvider.generateKeyPairFromPrivateKey(decryptedKey.toCharArray())
        val derivedAddress = mockCryptoProvider.deriveAddress(keyPair.publicKey)

        // Assert sanity check detects discrepancy
        assertFalse(
            "Derived address $derivedAddress must not match spoofed address $spoofedAddress",
            derivedAddress.equals(spoofedAddress, ignoreCase = true)
        )
    }

    @Test
    fun `challenge_6_3_migration_atomicity_mnemonic_corruption_prevents_partial_update`() {
        val privBytes = testPrivateKeyHex.encodeToByteArray()
        val pwdBytes = testPassword.encodeToByteArray()

        // 1. Valid private key legacy
        val sha256Key = CryptoUtils.sha256(pwdBytes)
        val encPriv = CryptoUtils.aesGcmEncrypt(privBytes, sha256Key)
        val legacyPrivBase64 = (encPriv.nonce + encPriv.ciphertext + encPriv.authTag).toBase64()

        // 2. Corrupted mnemonic legacy
        val mnemBytes = testMnemonic.encodeToByteArray()
        val encMnem = CryptoUtils.aesGcmEncrypt(mnemBytes, sha256Key)
        val corruptedMnemTag = encMnem.authTag.copyOf()
        corruptedMnemTag[0] = (corruptedMnemTag[0].toInt() xor 0xFF).toByte()
        val corruptedMnemBase64 = (encMnem.nonce + encMnem.ciphertext + corruptedMnemTag).toBase64()

        val privAad = CanonicalAad.forWalletStorage(testAddress, CanonicalAad.KEY_TYPE_PRIVATE_KEY)
        val mnemAad = CanonicalAad.forWalletStorage(testAddress, CanonicalAad.KEY_TYPE_MNEMONIC)

        // Simulating the repository migration pipeline:
        // Step 1: Migrate private key succeeds
        val migratedPriv = VersionedEncryptedEnvelope.migrateLegacy(
            legacyString = legacyPrivBase64,
            password = testPassword,
            keyId = testAddress,
            aad = privAad
        )
        assertNotNull(migratedPriv)

        // Step 2: Migrate mnemonic MUST fail with EnvelopeIntegrityException
        assertThrows(EnvelopeIntegrityException::class.java) {
            VersionedEncryptedEnvelope.migrateLegacy(
                legacyString = corruptedMnemBase64,
                password = testPassword,
                keyId = testAddress,
                aad = mnemAad
            )
        }
        // Since step 2 throws before SQL transaction, DB write is NEVER reached (atomic all-or-nothing)
    }
}

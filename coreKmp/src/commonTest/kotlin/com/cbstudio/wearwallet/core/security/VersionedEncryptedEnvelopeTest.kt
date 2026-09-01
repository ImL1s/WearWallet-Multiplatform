package com.cbstudio.wearwallet.core.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class VersionedEncryptedEnvelopeTest {

    private val testPassword = "SuperSecurePassword#2026".encodeToByteArray()
    private val testPayload = "0x4c0883a69102937d6231471b5dbb6204fe5129617082792ae468d01a3f360873".encodeToByteArray()
    private val testKeyId = "wallet-account-test-001"
    private val testAad = "context:account_id=001,chain=ETH".encodeToByteArray()

    @Test
    fun testEncryptAndDecryptSuccess() {
        val envelope = VersionedEncryptedEnvelope.encrypt(
            plaintext = testPayload,
            password = testPassword,
            keyId = testKeyId,
            aad = testAad
        )

        assertEquals(VersionedEncryptedEnvelope.CURRENT_VERSION, envelope.version)
        assertEquals(KdfAlgorithm.PBKDF2_HMAC_SHA256, envelope.kdfAlgorithm)
        assertEquals(CipherAlgorithm.AES_256_GCM, envelope.cipherAlgorithm)
        assertEquals(testKeyId, envelope.keyId)
        assertTrue(envelope.salt.size >= VersionedEncryptedEnvelope.MIN_SALT_LENGTH)
        assertTrue(envelope.nonce.size >= VersionedEncryptedEnvelope.MIN_NONCE_LENGTH)
        assertTrue(envelope.authTag.size >= VersionedEncryptedEnvelope.AUTH_TAG_LENGTH)
        assertTrue(envelope.ciphertext.isNotEmpty())

        val decrypted = envelope.decrypt(
            password = testPassword,
            expectedAad = testAad
        )

        assertTrue(decrypted.contentEquals(testPayload), "Decrypted content must match original payload")
    }

    @Test
    fun testTamperedAuthTagFailsClosed() {
        val envelope = VersionedEncryptedEnvelope.encrypt(
            plaintext = testPayload,
            password = testPassword,
            keyId = testKeyId,
            aad = testAad
        )

        val tamperedTag = envelope.authTag.copyOf()
        tamperedTag[0] = (tamperedTag[0].toInt() xor 0xFF).toByte()
        val tamperedEnvelope = envelope.copy(authTag = tamperedTag)

        assertFailsWith<EnvelopeIntegrityException> {
            tamperedEnvelope.decrypt(testPassword, testAad)
        }
    }

    @Test
    fun testTamperedCiphertextFailsClosed() {
        val envelope = VersionedEncryptedEnvelope.encrypt(
            plaintext = testPayload,
            password = testPassword,
            keyId = testKeyId,
            aad = testAad
        )

        val tamperedCt = envelope.ciphertext.copyOf()
        tamperedCt[0] = (tamperedCt[0].toInt() xor 0x01).toByte()
        val tamperedEnvelope = envelope.copy(ciphertext = tamperedCt)

        assertFailsWith<EnvelopeIntegrityException> {
            tamperedEnvelope.decrypt(testPassword, testAad)
        }
    }

    @Test
    fun testTamperedAadFailsClosed() {
        val envelope = VersionedEncryptedEnvelope.encrypt(
            plaintext = testPayload,
            password = testPassword,
            keyId = testKeyId,
            aad = testAad
        )

        val wrongAad = "context:account_id=002,chain=ETH".encodeToByteArray()

        // 1. Decrypt with wrong expected AAD
        assertFailsWith<EnvelopeIntegrityException> {
            envelope.decrypt(testPassword, expectedAad = wrongAad)
        }

        // 2. Tampered AAD inside envelope
        val tamperedEnvelope = envelope.copy(aad = wrongAad)
        assertFailsWith<EnvelopeIntegrityException> {
            tamperedEnvelope.decrypt(testPassword, expectedAad = wrongAad)
        }
    }

    @Test
    fun testTamperedNonceFailsClosed() {
        val envelope = VersionedEncryptedEnvelope.encrypt(
            plaintext = testPayload,
            password = testPassword,
            keyId = testKeyId,
            aad = testAad
        )

        val tamperedNonce = envelope.nonce.copyOf()
        tamperedNonce[0] = (tamperedNonce[0].toInt() xor 0x55).toByte()
        val tamperedEnvelope = envelope.copy(nonce = tamperedNonce)

        assertFailsWith<EnvelopeIntegrityException> {
            tamperedEnvelope.decrypt(testPassword, testAad)
        }
    }

    @Test
    fun testInvalidMagicHeaderFailsClosed() {
        val envelope = VersionedEncryptedEnvelope.encrypt(
            plaintext = testPayload,
            password = testPassword,
            keyId = testKeyId,
            aad = testAad
        )

        val serialized = envelope.serialize()
        serialized[0] = 0x00 // Corrupt magic

        assertFailsWith<InvalidEnvelopeHeaderException> {
            VersionedEncryptedEnvelope.deserialize(serialized)
        }
    }

    @Test
    fun testUnsupportedVersionFailsClosed() {
        val envelope = VersionedEncryptedEnvelope.encrypt(
            plaintext = testPayload,
            password = testPassword,
            keyId = testKeyId,
            aad = testAad
        )

        val serialized = envelope.serialize()
        serialized[4] = 99 // Corrupt version to 99

        assertFailsWith<UnsupportedEnvelopeVersionException> {
            VersionedEncryptedEnvelope.deserialize(serialized)
        }
    }

    @Test
    fun testTruncatedBufferFailsClosed() {
        val envelope = VersionedEncryptedEnvelope.encrypt(
            plaintext = testPayload,
            password = testPassword,
            keyId = testKeyId,
            aad = testAad
        )

        val serialized = envelope.serialize()
        val truncated = serialized.copyOfRange(0, serialized.size / 2)

        assertFailsWith<EnvelopeCorruptedException> {
            VersionedEncryptedEnvelope.deserialize(truncated)
        }
    }

    @Test
    fun testBinaryAndBase64RoundTrip() {
        val envelope = VersionedEncryptedEnvelope.encrypt(
            plaintext = testPayload,
            password = testPassword,
            keyId = testKeyId,
            aad = testAad
        )

        // 1. Binary round trip
        val serializedBinary = envelope.serialize()
        val deserializedBinary = VersionedEncryptedEnvelope.deserialize(serializedBinary)
        assertEquals(envelope, deserializedBinary)

        // 2. Base64 round trip
        val base64 = envelope.serializeToBase64()
        val deserializedBase64 = VersionedEncryptedEnvelope.deserializeFromBase64(base64)
        assertEquals(envelope, deserializedBase64)

        // Decrypt from deserialized
        val decrypted = deserializedBase64.decrypt(testPassword, testAad)
        assertTrue(decrypted.contentEquals(testPayload))
    }

    @Test
    fun testLegacyFormatDetectionAndMigration() {
        val salt = CryptoUtils.randomBytes(16)
        val derivedKey = CryptoUtils.pbkdf2(
            password = testPassword,
            salt = salt,
            iterations = 100_000,
            keyLength = 32
        )
        val encrypted = CryptoUtils.aesGcmEncrypt(testPayload, derivedKey)

        val legacyString = listOf(
            "v1",
            salt.toBase64(),
            encrypted.nonce.toBase64(),
            encrypted.authTag.toBase64(),
            encrypted.ciphertext.toBase64()
        ).joinToString(":")

        assertTrue(VersionedEncryptedEnvelope.isLegacyFormat(legacyString))

        // Direct deserializeFromBase64 on legacy string should fail closed
        assertFailsWith<UnversionedPlaintextException> {
            VersionedEncryptedEnvelope.deserializeFromBase64(legacyString)
        }

        // Migration to new VersionedEncryptedEnvelope
        val migratedEnvelope = VersionedEncryptedEnvelope.migrateLegacy(
            legacyString = legacyString,
            password = testPassword.decodeToString(),
            keyId = "migrated-key-001",
            aad = testAad
        )

        assertEquals("migrated-key-001", migratedEnvelope.keyId)
        val decrypted = migratedEnvelope.decrypt(testPassword, testAad)
        assertTrue(decrypted.contentEquals(testPayload))
    }

    @Test
    fun testLegacy4PartFormatDetectionAndMigration() {
        val salt = CryptoUtils.randomBytes(16)
        val derivedKey = CryptoUtils.pbkdf2(
            password = testPassword,
            salt = salt,
            iterations = 100_000,
            keyLength = 32
        )
        val encrypted = CryptoUtils.aesGcmEncrypt(testPayload, derivedKey)

        val legacy4Part = listOf(
            salt.toBase64(),
            encrypted.nonce.toBase64(),
            encrypted.authTag.toBase64(),
            encrypted.ciphertext.toBase64()
        ).joinToString(":")

        assertTrue(VersionedEncryptedEnvelope.isLegacyFormat(legacy4Part))

        assertFailsWith<UnversionedPlaintextException> {
            VersionedEncryptedEnvelope.deserializeFromBase64(legacy4Part)
        }

        val migrated = VersionedEncryptedEnvelope.migrateLegacy(
            legacyString = legacy4Part,
            password = testPassword.decodeToString(),
            keyId = "migrated-4part-001",
            aad = testAad
        )
        assertEquals("migrated-4part-001", migrated.keyId)
        val decrypted = migrated.decrypt(testPassword, testAad)
        assertTrue(decrypted.contentEquals(testPayload))
    }

    @Test
    fun testAndroidLegacyFormatDetectionAndMigration() {
        // Android legacy: Base64(12-byte IV + GCM ciphertext + tag), key = SHA-256(password)
        val sha256Key = CryptoUtils.sha256(testPassword)
        val encrypted = CryptoUtils.aesGcmEncrypt(testPayload, sha256Key)
        val combined = encrypted.nonce + encrypted.ciphertext + encrypted.authTag
        val androidLegacyBase64 = combined.toBase64()

        assertTrue(VersionedEncryptedEnvelope.isLegacyFormat(androidLegacyBase64))

        // Direct deserialize must reject legacy Base64 (downgrade prevention)
        assertFailsWith<UnversionedPlaintextException> {
            VersionedEncryptedEnvelope.deserializeFromBase64(androidLegacyBase64)
        }

        val migrated = VersionedEncryptedEnvelope.migrateLegacy(
            legacyString = androidLegacyBase64,
            password = testPassword.decodeToString(),
            keyId = "migrated-android-001",
            aad = testAad
        )
        assertEquals("migrated-android-001", migrated.keyId)
        val decrypted = migrated.decrypt(testPassword, testAad)
        assertTrue(decrypted.contentEquals(testPayload))
    }

    @Test
    fun testLegacyMigrationFailsClosedOnWrongPassword() {
        val sha256Key = CryptoUtils.sha256(testPassword)
        val encrypted = CryptoUtils.aesGcmEncrypt(testPayload, sha256Key)
        val combined = encrypted.nonce + encrypted.ciphertext + encrypted.authTag
        val androidLegacyBase64 = combined.toBase64()

        assertFailsWith<EnvelopeIntegrityException> {
            VersionedEncryptedEnvelope.migrateLegacy(
                legacyString = androidLegacyBase64,
                password = "WrongPassword#123",
                keyId = "test-key",
                aad = testAad
            )
        }
    }

    @Test
    fun testLegacyMigrationFailsClosedOnCorruptedTag() {
        val sha256Key = CryptoUtils.sha256(testPassword)
        val encrypted = CryptoUtils.aesGcmEncrypt(testPayload, sha256Key)
        val corruptedTag = encrypted.authTag.copyOf()
        corruptedTag[0] = (corruptedTag[0].toInt() xor 0xFF).toByte()
        val combined = encrypted.nonce + encrypted.ciphertext + corruptedTag
        val corruptedAndroidLegacyBase64 = combined.toBase64()

        assertFailsWith<EnvelopeIntegrityException> {
            VersionedEncryptedEnvelope.migrateLegacy(
                legacyString = corruptedAndroidLegacyBase64,
                password = testPassword.decodeToString(),
                keyId = "test-key",
                aad = testAad
            )
        }
    }

    @Test
    fun testUnversionedPlaintextRejected() {
        val plaintexts = listOf(
            "raw_private_key_without_envelope",
            "1234567890abcdef1234567890abcdef",
            "",
            "   "
        )

        for (plain in plaintexts) {
            assertFailsWith<UnversionedPlaintextException> {
                VersionedEncryptedEnvelope.deserializeFromBase64(plain)
            }
        }
    }

    @Test
    fun testSecureZeroZeroizesMemory() {
        val envelope = VersionedEncryptedEnvelope.encrypt(
            plaintext = testPayload,
            password = testPassword,
            keyId = testKeyId,
            aad = testAad
        )

        val saltCopy = envelope.salt.copyOf()
        val nonceCopy = envelope.nonce.copyOf()
        val ctCopy = envelope.ciphertext.copyOf()

        envelope.secureZero()

        assertTrue(envelope.salt.all { it == 0.toByte() })
        assertTrue(envelope.nonce.all { it == 0.toByte() })
        assertTrue(envelope.ciphertext.all { it == 0.toByte() })
        assertTrue(envelope.authTag.all { it == 0.toByte() })
    }

    // =========================================================================
    // P1-1: 演算法混淆防護測試 (Algorithm Confusion Gate)
    // =========================================================================
    @Test
    fun test_unimplemented_kdf_argon2id_in_encrypt_fails_closed() {
        assertFailsWith<UnsupportedEnvelopeVersionException> {
            VersionedEncryptedEnvelope.encrypt(
                plaintext = testPayload,
                password = testPassword,
                kdfAlgorithm = KdfAlgorithm.ARGON2ID
            )
        }
    }

    @Test
    fun test_unimplemented_cipher_chacha20_in_encrypt_fails_closed() {
        assertFailsWith<UnsupportedEnvelopeVersionException> {
            VersionedEncryptedEnvelope.encrypt(
                plaintext = testPayload,
                password = testPassword,
                cipherAlgorithm = CipherAlgorithm.CHACHA20_POLY1305
            )
        }
    }

    @Test
    fun test_deserialize_unsupported_kdf_id_fails_closed() {
        val envelope = VersionedEncryptedEnvelope.encrypt(
            plaintext = testPayload,
            password = testPassword,
            keyId = testKeyId
        )
        val serialized = envelope.serialize()
        serialized[5] = KdfAlgorithm.ARGON2ID.id // 修改 KDF id

        assertFailsWith<UnsupportedEnvelopeVersionException> {
            VersionedEncryptedEnvelope.deserialize(serialized)
        }
    }

    @Test
    fun test_deserialize_unsupported_cipher_id_fails_closed() {
        val envelope = VersionedEncryptedEnvelope.encrypt(
            plaintext = testPayload,
            password = testPassword,
            keyId = testKeyId
        )
        val serialized = envelope.serialize()
        // Cipher ID 位於 offset: 4(magic) + 1(ver) + 1(kdfId) + 4(kdfIter) + 2(kdfKeyLen) + 1(saltLen) + salt.size
        val cipherIdOffset = 4 + 1 + 1 + 4 + 2 + 1 + envelope.salt.size
        serialized[cipherIdOffset] = CipherAlgorithm.CHACHA20_POLY1305.id

        assertFailsWith<UnsupportedEnvelopeVersionException> {
            VersionedEncryptedEnvelope.deserialize(serialized)
        }
    }

    // =========================================================================
    // P1-2: 參數邊界與拒絕尾隨垃圾測試 (Parameter Bounds & Trailing Bytes Gate)
    // =========================================================================
    @Test
    fun test_deserialize_strictly_rejects_trailing_garbage_bytes() {
        val envelope = VersionedEncryptedEnvelope.encrypt(
            plaintext = testPayload,
            password = testPassword,
            keyId = testKeyId,
            aad = testAad
        )
        val validBytes = envelope.serialize()
        val corruptedWithGarbage = validBytes + byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())

        val exception = assertFailsWith<EnvelopeCorruptedException> {
            VersionedEncryptedEnvelope.deserialize(corruptedWithGarbage)
        }
        assertTrue(exception.message?.contains("trailing garbage bytes") == true)
    }

    @Test
    fun test_kdf_iterations_lower_bound_enforced() {
        assertFailsWith<IllegalArgumentException> {
            VersionedEncryptedEnvelope.encrypt(
                plaintext = testPayload,
                password = testPassword,
                kdfIterations = 9_999 // 低於 10,000
            )
        }
    }

    @Test
    fun test_kdf_iterations_upper_bound_enforced() {
        assertFailsWith<IllegalArgumentException> {
            VersionedEncryptedEnvelope.encrypt(
                plaintext = testPayload,
                password = testPassword,
                kdfIterations = 1_000_001 // 超過 1,000,000
            )
        }
    }

    @Test
    fun test_deserialize_rejects_oversized_total_envelope() {
        val fakeOversizedHeader = ByteArray(100)
        VersionedEncryptedEnvelope.MAGIC.copyInto(fakeOversizedHeader, 0, 0, 4)
        fakeOversizedHeader[4] = 1 // version
        val buffer = ByteArray(1_050_001) // 超出 1,050,000 bytes 上限
        fakeOversizedHeader.copyInto(buffer, 0, 0, 100)

        assertFailsWith<EnvelopeCorruptedException> {
            VersionedEncryptedEnvelope.deserialize(buffer)
        }
    }
}

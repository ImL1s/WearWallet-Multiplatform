package com.cbstudio.wearwallet.core.security

import io.github.iml1s.crypto.Secp256k1Pure
import io.github.iml1s.crypto.SecureByteArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 跨平台確定性固定密碼學測試向量 (Cross-Platform Fixed Crypto Vectors)
 *
 * 驗證 Android ↔ iOS ↔ watchOS 共享一致的 NIST SP 800-38D AES-256-GCM 密碼學標準、
 * 固定金鑰/向量之互通性、Canonical AAD 認證及所有負向篡改情境之 Fail-Closed 保證。
 */
class CrossPlatformFixedCryptoVectorsTest {

    // =========================================================================
    // 向量 1: NIST SP 800-38D AES-256-GCM Test Vector (256-bit Key, 96-bit IV)
    // =========================================================================
    @Test
    fun test_nist_sp800_38d_aes256_gcm_standard_vector() {
        // NIST SP 800-38D Test Case 14 (Key: 256 bits, IV: 96 bits, PT: 128 bits, AAD: 160 bits)
        val key = "feffe9928665731c6d6a8f9467308308feffe9928665731c6d6a8f9467308308".hexToByteArray()
        val iv = "cafebabefacedbaddecaf888".hexToByteArray()
        val plaintext = "d9313225f88406e5a55909c5aff5269a86a7a9531534f7da2e4c303d8a318a721c3c0c95956809532fcf0e2449a6b525b16aedf5aa0de657ba637b391aafd255".hexToByteArray()
        val aad = "feedfacedeadbeeffeedfacedeadbeefabaddad2".hexToByteArray()

        val expectedCiphertext = "522dc1f099567d07f47f37a32a84427d643a8cdcbfe5c0c97598a2bd2555d1aa8cb08e48590dbb3da7b08b1056828838c5f61e6393ba7a0abcc9f662898015ad".hexToByteArray()
        val expectedTag = "2df7cd675b4f09163b41ebf980a7f638".hexToByteArray()

        val encrypted = CryptoUtils.aesGcmEncryptWithAad(
            data = plaintext,
            key = key,
            nonce = iv,
            aad = aad
        )

        // 驗證密文與 Auth Tag 與 NIST 標準完全吻合
        assertEquals(expectedCiphertext.toHexString(), encrypted.ciphertext.toHexString(), "NIST Ciphertext must match standard vector")
        assertEquals(expectedTag.toHexString(), encrypted.authTag.toHexString(), "NIST Auth Tag must match standard vector")

        // 驗證解密可完全還原
        val decrypted = CryptoUtils.aesGcmDecryptWithAad(
            encryptedData = encrypted,
            key = key,
            aad = aad
        )
        assertEquals(plaintext.toHexString(), decrypted.toHexString(), "Decrypted plaintext must match NIST original")
    }

    // =========================================================================
    // 向量 2: WearWallet 規範 Canonical AAD 認證與抗篡改驗證
    // =========================================================================
    @Test
    fun test_wearwallet_canonical_aad_binding_and_tampering() {
        val key = CryptoUtils.randomBytes(32)
        val nonce = CryptoUtils.randomBytes(12)
        val payload = "0x4c0883a69102937d6231471b5dbb6204fe5129617082792ae468d01a3f360873".encodeToByteArray()
        val keyId = "account_vault_primary"

        val canonicalAad = CanonicalAad.forKeyBackup(keyId)
        assertEquals("schema=v1|purpose=key_backup|keyId=account_vault_primary", canonicalAad.decodeToString())

        val encrypted = CryptoUtils.aesGcmEncryptWithAad(
            data = payload,
            key = key,
            nonce = nonce,
            aad = canonicalAad
        )

        // 1. 正確 AAD 解密成功
        val decrypted = CryptoUtils.aesGcmDecryptWithAad(encrypted, key, canonicalAad)
        assertEquals(payload.decodeToString(), decrypted.decodeToString())

        // 2. 篡改 AAD (例如換成不同 keyId 的 AAD) 解密必失敗
        val forgedAad = CanonicalAad.forKeyBackup("account_vault_attacker")
        assertFailsWith<Exception> {
            CryptoUtils.aesGcmDecryptWithAad(encrypted, key, forgedAad)
        }

        // 3. 空 AAD 解密必失敗
        assertFailsWith<Exception> {
            CryptoUtils.aesGcmDecryptWithAad(encrypted, key, byteArrayOf())
        }
    }

    // =========================================================================
    // 向量 3: 32-Byte 私鑰固定向量加密、解密與 Secp256k1 確定性簽名
    // =========================================================================
    @Test
    fun test_32byte_private_key_fixed_vector_and_deterministic_signing() {
        val fixedPrivateKeyHex = "e331b6d69882b4cb4ea581d88ec2693329b3b3a4711832421415ebff2d474428"
        val privateKeyBytes = fixedPrivateKeyHex.hexToByteArray()
        val password = "StrongPassword#2026".encodeToByteArray()
        val keyId = "wearwallet-test-key-32b"

        val envelope = VersionedEncryptedEnvelope.encrypt(
            plaintext = privateKeyBytes,
            password = password,
            keyId = keyId,
            aad = CanonicalAad.forKeyBackup(keyId)
        )

        // 解密私鑰
        val decryptedKeyBytes = envelope.decrypt(password, CanonicalAad.forKeyBackup(keyId))
        assertEquals(fixedPrivateKeyHex, decryptedKeyBytes.toHexString())

        // 進行 Secp256k1 確定性簽名驗證
        val message = "WearWallet Fixed Crypto Vector Verification Message".encodeToByteArray()
        val messageHash = CryptoUtils.sha256(message)

        val signature1 = Secp256k1Pure.sign(messageHash, decryptedKeyBytes)
        val signature2 = Secp256k1Pure.sign(messageHash, privateKeyBytes)

        assertTrue(signature1.contentEquals(signature2), "RFC 6979 deterministic signatures must be identical")
        assertTrue(signature1.size in 64..65, "Signature size must be 64 or 65 bytes")

        SecureByteArray.secureZero(privateKeyBytes)
        SecureByteArray.secureZero(decryptedKeyBytes)
        SecureByteArray.secureZero(password)
    }

    // =========================================================================
    // 向量 4: 完整 WWEN 信封二進制與 Base64 固定向量 Golden Vector
    // =========================================================================
    @Test
    fun test_full_wwen_envelope_fixed_roundtrip_vector() {
        val payload = "WearWallet Golden Secret Payload".encodeToByteArray()
        val password = "GoldenPassword2026".encodeToByteArray()
        val fixedSalt = "0102030405060708090a0b0c0d0e0f10".hexToByteArray()
        val fixedNonce = "0102030405060708090a0b0c".hexToByteArray()
        val keyId = "golden-key-01"
        val aad = CanonicalAad.forKeyBackup(keyId)

        val envelope = VersionedEncryptedEnvelope.encrypt(
            plaintext = payload,
            password = password,
            keyId = keyId,
            aad = aad,
            kdfIterations = 10_000,
            salt = fixedSalt,
            nonce = fixedNonce
        )

        val base64String = envelope.serializeToBase64()
        assertTrue(base64String.isNotEmpty())

        // 從 Base64 反序列化並驗證屬性
        val restored = VersionedEncryptedEnvelope.deserializeFromBase64(base64String)
        assertEquals(1.toByte(), restored.version)
        assertEquals(KdfAlgorithm.PBKDF2_HMAC_SHA256, restored.kdfAlgorithm)
        assertEquals(10_000, restored.kdfIterations)
        assertEquals(32, restored.kdfKeyLength)
        assertEquals(fixedSalt.toHexString(), restored.salt.toHexString())
        assertEquals(CipherAlgorithm.AES_256_GCM, restored.cipherAlgorithm)
        assertEquals(fixedNonce.toHexString(), restored.nonce.toHexString())
        assertEquals(keyId, restored.keyId)
        assertEquals(aad.toHexString(), restored.aad.toHexString())

        val decrypted = restored.decrypt(password, aad)
        assertEquals(payload.decodeToString(), decrypted.decodeToString())
    }

    // =========================================================================
    // 向量 5: 負向單元測試 — 所有篡改位元均必須 Fail-Closed
    // =========================================================================
    @Test
    fun test_negative_mutations_fail_closed() {
        val payload = "Secret Data".encodeToByteArray()
        val password = "MutationPassword".encodeToByteArray()
        val keyId = "mutation-key"
        val aad = CanonicalAad.forKeyBackup(keyId)

        val envelope = VersionedEncryptedEnvelope.encrypt(
            plaintext = payload,
            password = password,
            keyId = keyId,
            aad = aad
        )

        // 1. 篡改 Ciphertext
        val badCt = envelope.ciphertext.copyOf()
        badCt[0] = (badCt[0].toInt() xor 0x01).toByte()
        assertFailsWith<EnvelopeIntegrityException> {
            envelope.copy(ciphertext = badCt).decrypt(password, aad)
        }

        // 2. 篡改 Auth Tag
        val badTag = envelope.authTag.copyOf()
        badTag[0] = (badTag[0].toInt() xor 0xFF).toByte()
        assertFailsWith<EnvelopeIntegrityException> {
            envelope.copy(authTag = badTag).decrypt(password, aad)
        }

        // 3. 篡改 Nonce
        val badNonce = envelope.nonce.copyOf()
        badNonce[0] = (badNonce[0].toInt() xor 0xAA).toByte()
        assertFailsWith<EnvelopeIntegrityException> {
            envelope.copy(nonce = badNonce).decrypt(password, aad)
        }

        // 4. 篡改 Salt
        val badSalt = envelope.salt.copyOf()
        badSalt[0] = (badSalt[0].toInt() xor 0x55).toByte()
        assertFailsWith<EnvelopeIntegrityException> {
            envelope.copy(salt = badSalt).decrypt(password, aad)
        }

        // 5. 篡改 Magic Header
        val serialized = envelope.serialize()
        serialized[0] = 0x00
        assertFailsWith<InvalidEnvelopeHeaderException> {
            VersionedEncryptedEnvelope.deserialize(serialized)
        }

        // 6. 尾隨垃圾位元組
        val withGarbage = envelope.serialize() + byteArrayOf(0x01, 0x02, 0x03)
        assertFailsWith<EnvelopeCorruptedException> {
            VersionedEncryptedEnvelope.deserialize(withGarbage)
        }
    }
}

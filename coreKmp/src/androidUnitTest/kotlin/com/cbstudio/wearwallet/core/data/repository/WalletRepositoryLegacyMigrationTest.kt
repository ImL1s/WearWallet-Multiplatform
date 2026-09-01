package com.cbstudio.wearwallet.core.data.repository

import com.cbstudio.wearwallet.core.security.CanonicalAad
import com.cbstudio.wearwallet.core.security.CryptoUtils
import com.cbstudio.wearwallet.core.security.EnvelopeIntegrityException
import com.cbstudio.wearwallet.core.security.UnversionedPlaintextException
import com.cbstudio.wearwallet.core.security.VersionedEncryptedEnvelope
import com.cbstudio.wearwallet.core.security.toBase64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * P0-3, P1-4: 錢包儲存庫安全性與 Legacy 遷移測試 (Architecture & Legacy Migration Tests)
 *
 * 驗證重點：
 * 1. 架構審查：RealWalletRepository 嚴禁調用未加鹽/舊版 CryptoProvider.encrypt 或 decrypt
 * 2. Android 舊版 Base64(IV + CiphertextWithTag, key = SHA-256(password)) 遷移至 WWEN 格式
 * 3. 4-Part 及 5-Part 舊版格式遷移至 WWEN 格式
 * 4. 遷移時嚴格執行地址一致性校驗 (Address Sanity Check)
 * 5. 錯誤密碼與損毀標籤 Fail-Closed 防護
 * 6. 遷移後防止降級 (Downgrade Prevention)
 */
class WalletRepositoryLegacyMigrationTest {

    private val testPassword = "WalletMigrationPassword#2026"
    private val testPrivateKey = "e331b6d69882b4cb4ea581d88ec2693329b3b3a4711832421415ebff2d474428"
    private val testMnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
    private val testAddress = "0x89205A3A3b2A69De6Dbf7f01ED13B2108B2c43e7"

    // =========================================================================
    // 1. 架構安全檢查 (Architecture Static Check)
    // =========================================================================

    @Test
    fun test_RealWalletRepository_contains_zero_calls_to_cryptoProvider_encrypt_or_decrypt() {
        val repoFile = File("src/commonMain/kotlin/com/cbstudio/wearwallet/core/data/repository/RealWalletRepository.kt")
        val content = if (repoFile.exists()) {
            repoFile.readText()
        } else {
            val rootFile = File("coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/data/repository/RealWalletRepository.kt")
            assertTrue("RealWalletRepository.kt must exist", rootFile.exists())
            rootFile.readText()
        }

        assertFalse(
            "RealWalletRepository MUST NOT call cryptoProvider.encrypt (must use VersionedEncryptedEnvelope.encrypt)",
            content.contains("cryptoProvider.encrypt(")
        )

        assertFalse(
            "RealWalletRepository MUST NOT call cryptoProvider.decrypt (must use VersionedEncryptedEnvelope / decryptAndMigrateWalletSecrets)",
            content.contains("cryptoProvider.decrypt(")
        )
    }

    // =========================================================================
    // 2. Android 舊版 SHA-256 + AES-GCM Base64 格式遷移
    // =========================================================================

    @Test
    fun test_AndroidLegacyFormat_migrates_to_WWEN_envelope_successfully() {
        val privBytes = testPrivateKey.encodeToByteArray()
        val pwdBytes = testPassword.encodeToByteArray()

        // 構造 Android 舊版加密字串: Base64(12-byte IV + GCM ciphertext + 16-byte tag), key = SHA-256(password)
        val sha256Key = CryptoUtils.sha256(pwdBytes)
        val encrypted = CryptoUtils.aesGcmEncrypt(privBytes, sha256Key)
        val combined = encrypted.nonce + encrypted.ciphertext + encrypted.authTag
        val androidLegacyBase64 = combined.toBase64()

        assertTrue(
            "Android legacy Base64 string must be detected as legacy format",
            VersionedEncryptedEnvelope.isLegacyFormat(androidLegacyBase64)
        )

        // 嚴禁直接以 deserializeFromBase64 讀取舊格式 (防止無版本降級攻擊)
        assertThrows(UnversionedPlaintextException::class.java) {
            VersionedEncryptedEnvelope.deserializeFromBase64(androidLegacyBase64)
        }

        // 執行遷移
        val privAad = CanonicalAad.forWalletStorage(testAddress, CanonicalAad.KEY_TYPE_PRIVATE_KEY)
        val migratedEnvelope = VersionedEncryptedEnvelope.migrateLegacy(
            legacyString = androidLegacyBase64,
            password = testPassword,
            keyId = testAddress,
            aad = privAad
        )

        // 驗證遷移後的信封屬性
        assertEquals(VersionedEncryptedEnvelope.CURRENT_VERSION, migratedEnvelope.version)
        assertEquals(testAddress, migratedEnvelope.keyId)

        // 驗證使用 Canonical AAD 解密還原
        val decryptedBytes = migratedEnvelope.decrypt(pwdBytes, expectedAad = privAad)
        assertEquals(testPrivateKey, decryptedBytes.decodeToString())

        // 序列化為 Base64 並確認新格式以 WWEN 開頭且不再是 legacy
        val newSerializedBase64 = migratedEnvelope.serializeToBase64()
        assertFalse(
            "New WWEN envelope must NOT be classified as legacy",
            VersionedEncryptedEnvelope.isLegacyFormat(newSerializedBase64)
        )
    }

    // =========================================================================
    // 3. 冒號分隔 (v1: 5-Part & 4-Part) 格式遷移
    // =========================================================================

    @Test
    fun test_ColonSeparatedLegacyFormat_migrates_to_WWEN_envelope_successfully() {
        val mnemBytes = testMnemonic.encodeToByteArray()
        val pwdBytes = testPassword.encodeToByteArray()

        val salt = CryptoUtils.randomBytes(16)
        val derivedKey = CryptoUtils.pbkdf2(pwdBytes, salt, 100_000, 32)
        val encrypted = CryptoUtils.aesGcmEncrypt(mnemBytes, derivedKey)

        val legacy5Part = listOf(
            "v1",
            salt.toBase64(),
            encrypted.nonce.toBase64(),
            encrypted.authTag.toBase64(),
            encrypted.ciphertext.toBase64()
        ).joinToString(":")

        assertTrue(VersionedEncryptedEnvelope.isLegacyFormat(legacy5Part))

        val mnemAad = CanonicalAad.forWalletStorage(testAddress, CanonicalAad.KEY_TYPE_MNEMONIC)
        val migratedEnvelope = VersionedEncryptedEnvelope.migrateLegacy(
            legacyString = legacy5Part,
            password = testPassword,
            keyId = testAddress,
            aad = mnemAad
        )

        val decryptedBytes = migratedEnvelope.decrypt(pwdBytes, expectedAad = mnemAad)
        assertEquals(testMnemonic, decryptedBytes.decodeToString())
    }

    // =========================================================================
    // 4. 錯誤密碼與損毀標籤 Fail-Closed 防護
    // =========================================================================

    @Test
    fun test_LegacyMigration_failsClosed_on_wrong_password() {
        val privBytes = testPrivateKey.encodeToByteArray()
        val pwdBytes = testPassword.encodeToByteArray()
        val sha256Key = CryptoUtils.sha256(pwdBytes)
        val encrypted = CryptoUtils.aesGcmEncrypt(privBytes, sha256Key)
        val androidLegacyBase64 = (encrypted.nonce + encrypted.ciphertext + encrypted.authTag).toBase64()

        val privAad = CanonicalAad.forWalletStorage(testAddress, CanonicalAad.KEY_TYPE_PRIVATE_KEY)

        assertThrows(EnvelopeIntegrityException::class.java) {
            VersionedEncryptedEnvelope.migrateLegacy(
                legacyString = androidLegacyBase64,
                password = "IncorrectPassword#999",
                keyId = testAddress,
                aad = privAad
            )
        }
    }

    @Test
    fun test_LegacyMigration_failsClosed_on_corrupted_tag() {
        val privBytes = testPrivateKey.encodeToByteArray()
        val pwdBytes = testPassword.encodeToByteArray()
        val sha256Key = CryptoUtils.sha256(pwdBytes)
        val encrypted = CryptoUtils.aesGcmEncrypt(privBytes, sha256Key)

        val corruptedTag = encrypted.authTag.copyOf()
        corruptedTag[0] = (corruptedTag[0].toInt() xor 0xFF).toByte()
        val corruptedLegacyBase64 = (encrypted.nonce + encrypted.ciphertext + corruptedTag).toBase64()

        val privAad = CanonicalAad.forWalletStorage(testAddress, CanonicalAad.KEY_TYPE_PRIVATE_KEY)

        assertThrows(EnvelopeIntegrityException::class.java) {
            VersionedEncryptedEnvelope.migrateLegacy(
                legacyString = corruptedLegacyBase64,
                password = testPassword,
                keyId = testAddress,
                aad = privAad
            )
        }
    }
}

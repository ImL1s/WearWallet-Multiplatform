package com.cbstudio.wearwallet.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 資料庫密鑰管理器
 * 使用 Android Keystore 安全存儲資料庫加密密鑰
 *
 * 安全特性:
 * - 使用 Android Keystore 硬體級加密
 * - AES-256-GCM 加密算法
 * - 密鑰不可導出
 * - 支援密鑰生成、加密、解密、輪換
 *
 * @param context Android Context
 */
class DatabaseKeyManager(private val context: Context) {

    private val keyStore: KeyStore = KeyStore.getInstance(Constants.ANDROID_KEYSTORE).apply {
        load(null)
    }

    private val sharedPreferences = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

    private object Constants {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val MASTER_KEY_ALIAS = "wearwallet_db_master_key"
        const val PREFS_NAME = "wearwallet_secure_prefs"
        const val ENCRYPTED_KEY_PREF = "encrypted_db_key"
        const val IV_PREF = "encryption_iv"
        const val GCM_TAG_LENGTH = 128
        const val AES_KEY_SIZE = 256
        const val DB_KEY_SIZE = 32 // 256 bits
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }

    /**
     * 獲取或創建資料庫加密密鑰
     *
     * @return ByteArray 256位資料庫加密密鑰
     */
    fun getDatabaseKey(): ByteArray {
        val encryptedKey = sharedPreferences.getString(Constants.ENCRYPTED_KEY_PREF, null)

        return if (encryptedKey != null) {
            // 解密已存在的密鑰
            decryptDatabaseKey(encryptedKey)
        } else {
            // 生成新密鑰並加密存儲
            val newKey = generateRandomKey()
            val encrypted = encryptDatabaseKey(newKey)

            sharedPreferences.edit()
                .putString(Constants.ENCRYPTED_KEY_PREF, encrypted.encryptedKey)
                .putString(Constants.IV_PREF, encrypted.iv)
                .apply()

            newKey
        }
    }

    /**
     * 生成隨機的 256 位密鑰
     */
    private fun generateRandomKey(): ByteArray {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES)
        keyGenerator.init(Constants.AES_KEY_SIZE)
        return keyGenerator.generateKey().encoded
    }

    /**
     * 使用 Android Keystore 加密密鑰
     */
    private fun encryptDatabaseKey(key: ByteArray): EncryptedData {
        val masterKey = getOrCreateMasterKey()
        val cipher = Cipher.getInstance(Constants.TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, masterKey)

        val encrypted = cipher.doFinal(key)
        val iv = cipher.iv

        return EncryptedData(
            encryptedKey = Base64.encodeToString(encrypted, Base64.NO_WRAP),
            iv = Base64.encodeToString(iv, Base64.NO_WRAP)
        )
    }

    /**
     * 使用 Android Keystore 解密密鑰
     */
    private fun decryptDatabaseKey(encryptedKey: String): ByteArray {
        val masterKey = getOrCreateMasterKey()
        val iv = sharedPreferences.getString(Constants.IV_PREF, null)
            ?: throw IllegalStateException("IV not found")

        val cipher = Cipher.getInstance(Constants.TRANSFORMATION)
        val gcmSpec = GCMParameterSpec(Constants.GCM_TAG_LENGTH, Base64.decode(iv, Base64.NO_WRAP))
        cipher.init(Cipher.DECRYPT_MODE, masterKey, gcmSpec)

        val encryptedBytes = Base64.decode(encryptedKey, Base64.NO_WRAP)
        return cipher.doFinal(encryptedBytes)
    }

    /**
     * 獲取或創建 Keystore 主密鑰
     */
    private fun getOrCreateMasterKey(): SecretKey {
        return if (keyStore.containsAlias(Constants.MASTER_KEY_ALIAS)) {
            (keyStore.getEntry(Constants.MASTER_KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
        } else {
            createMasterKey()
        }
    }

    /**
     * 在 Android Keystore 中創建主密鑰
     *
     * 安全配置:
     * - AES-256-GCM 加密
     * - 密鑰存儲在硬體支援的 Keystore 中
     * - 不需要用戶認證(避免影響後台操作)
     * - 密鑰不可導出
     */
    private fun createMasterKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            Constants.ANDROID_KEYSTORE
        )

        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            Constants.MASTER_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(Constants.AES_KEY_SIZE)
            .setUserAuthenticationRequired(false) // 不需要用戶驗證
            .build()

        keyGenerator.init(keyGenParameterSpec)
        return keyGenerator.generateKey()
    }

    /**
     * 清除所有密鑰(用於重置或卸載)
     *
     * 警告: 這將使加密的資料庫無法訪問!
     */
    fun clearKeys() {
        try {
            if (keyStore.containsAlias(Constants.MASTER_KEY_ALIAS)) {
                keyStore.deleteEntry(Constants.MASTER_KEY_ALIAS)
            }

            sharedPreferences.edit()
                .remove(Constants.ENCRYPTED_KEY_PREF)
                .remove(Constants.IV_PREF)
                .apply()
        } catch (e: Exception) {
            throw SecurityException("Failed to clear keys: ${e.message}", e)
        }
    }

    /**
     * 密鑰輪換 - 生成新密鑰並重新加密
     *
     * 注意: 實際使用時需要配合資料庫遷移
     */
    fun rotateKey(): ByteArray {
        clearKeys()
        return getDatabaseKey()
    }

    /**
     * 檢查是否已有加密密鑰
     */
    fun hasEncryptionKey(): Boolean {
        return sharedPreferences.contains(Constants.ENCRYPTED_KEY_PREF)
    }

    /**
     * 加密數據容器
     */
    private data class EncryptedData(
        val encryptedKey: String,
        val iv: String
    )
}

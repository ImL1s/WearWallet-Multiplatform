package com.cbstudio.wearwallet.core.multichain.storage

import android.content.Context
import com.cbstudio.wearwallet.core.multichain.monero.crypto.MoneroWalletCache
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import android.util.Base64

/**
 * Android 平台快取儲存實現
 * 
 * 儲存位置：
 * - 快取檔案：Internal Storage /data/data/[package]/files/monero_cache/
 * - 設定：SharedPreferences
 * - 加密：使用 Android Keystore 或 AES-256
 */
actual class PlatformCacheStorage {
    
    companion object {
        @android.annotation.SuppressLint("StaticFieldLeak")
        private var applicationContext: Context? = null
        private const val CACHE_DIR = "monero_cache"
        private const val PREFS_NAME = "monero_wallet_prefs"
        private const val ENCRYPTION_KEY = "WearWalletMoneroCacheKey" // 應該從 Keystore 獲取
        
        fun init(context: Context) {
            applicationContext = context.applicationContext
        }
        
        fun getContext(): Context {
            return applicationContext ?: throw IllegalStateException(
                "PlatformCacheStorage not initialized. Call PlatformCacheStorage.init(context) first."
            )
        }
    }
    
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }
    
    private fun getCacheDir(): File {
        val context = getContext()
        val dir = File(context.filesDir, CACHE_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
    
    private fun getCacheFile(walletId: String): File {
        return File(getCacheDir(), "$walletId.cache")
    }
    
    actual fun saveMoneroCache(walletId: String, cache: MoneroWalletCache) {
        try {
            val jsonStr = json.encodeToString(cache)
            val encrypted = encrypt(jsonStr)
            val file = getCacheFile(walletId)
            file.writeText(encrypted)
            
            // 同時保存元數據到 SharedPreferences
            val prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().apply {
                putLong("${walletId}_last_sync", cache.lastSyncTimestamp)
                putLong("${walletId}_last_height", cache.lastScannedHeight)
                putString("${walletId}_address", cache.primaryAddress)
                apply()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    actual fun loadMoneroCache(walletId: String): MoneroWalletCache? {
        return try {
            val file = getCacheFile(walletId)
            if (!file.exists()) return null
            
            val encrypted = file.readText()
            val decrypted = decrypt(encrypted)
            json.decodeFromString<MoneroWalletCache>(decrypted)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    actual fun deleteMoneroCache(walletId: String) {
        val file = getCacheFile(walletId)
        if (file.exists()) {
            file.delete()
        }
        
        // 清除 SharedPreferences
        val prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            remove("${walletId}_last_sync")
            remove("${walletId}_last_height")
            remove("${walletId}_address")
            apply()
        }
    }
    
    actual fun existsMoneroCache(walletId: String): Boolean {
        return getCacheFile(walletId).exists()
    }
    
    actual fun listWalletIds(): List<String> {
        return getCacheDir().listFiles()
            ?.filter { it.extension == "cache" }
            ?.map { it.nameWithoutExtension }
            ?: emptyList()
    }
    
    actual fun clearAllCaches() {
        getCacheDir().listFiles()?.forEach { it.delete() }
        
        val prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }
    
    // 簡單的 AES 加密（實際應該使用 Android Keystore）
    private fun encrypt(data: String): String {
        val key = deriveKey(ENCRYPTION_KEY)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val keySpec = SecretKeySpec(key, "AES")
        val iv = ByteArray(16) // 應該使用隨機 IV
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, IvParameterSpec(iv))
        val encrypted = cipher.doFinal(data.toByteArray())
        return Base64.encodeToString(encrypted, Base64.DEFAULT)
    }
    
    private fun decrypt(data: String): String {
        val key = deriveKey(ENCRYPTION_KEY)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val keySpec = SecretKeySpec(key, "AES")
        val iv = ByteArray(16)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, IvParameterSpec(iv))
        val decrypted = cipher.doFinal(Base64.decode(data, Base64.DEFAULT))
        return String(decrypted)
    }
    
    private fun deriveKey(password: String): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(password.toByteArray()).take(32).toByteArray()
    }
}

/**
 * Android 平台鍵值儲存實現
 */
actual class PlatformKeyValueStorage {
    
    companion object {
        private const val PREFS_NAME = "monero_wallet_kv"
    }
    
    private val prefs by lazy {
        PlatformCacheStorage.getContext()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    actual fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }
    
    actual fun getString(key: String): String? {
        return prefs.getString(key, null)
    }
    
    actual fun putLong(key: String, value: Long) {
        prefs.edit().putLong(key, value).apply()
    }
    
    actual fun getLong(key: String, defaultValue: Long): Long {
        return prefs.getLong(key, defaultValue)
    }
    
    actual fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }
    
    actual fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return prefs.getBoolean(key, defaultValue)
    }
    
    actual fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }
    
    actual fun clear() {
        prefs.edit().clear().apply()
    }
    
    actual fun contains(key: String): Boolean {
        return prefs.contains(key)
    }
    
    actual fun getAllKeys(): Set<String> {
        return prefs.all.keys
    }
}
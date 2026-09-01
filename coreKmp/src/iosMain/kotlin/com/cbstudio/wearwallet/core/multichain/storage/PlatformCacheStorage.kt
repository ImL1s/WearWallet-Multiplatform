package com.cbstudio.wearwallet.core.multichain.storage

import com.cbstudio.wearwallet.core.multichain.monero.crypto.MoneroWalletCache
import kotlinx.cinterop.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import platform.Foundation.*
import platform.Security.*

/**
 * iOS 平台快取儲存實現
 *
 * 儲存位置：
 * - 快取檔案：Documents/monero_cache/
 * - 設定：NSUserDefaults
 * - 加密：使用 Base64 編碼（生產環境應使用完整加密）
 */
@OptIn(ExperimentalForeignApi::class)
actual class PlatformCacheStorage {

    companion object {
        private const val CACHE_DIR = "monero_cache"
        private const val PREFS_KEY_PREFIX = "monero_wallet_"
        private const val ENCRYPTION_KEY = "WearWalletMoneroCacheKey"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private val fileManager = NSFileManager.defaultManager
    private val userDefaults = NSUserDefaults.standardUserDefaults

    /**
     * 獲取快取目錄
     */
    private fun getCacheDir(): String {
        val fileManager = NSFileManager.defaultManager
        val paths = NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory,
            NSUserDomainMask,
            true
        )
        val documentsDir = paths.firstOrNull() as? String
            ?: throw IllegalStateException("Cannot find documents directory")

        val cacheDir = "$documentsDir/$CACHE_DIR"

        // 創建目錄（如果不存在）
        if (!fileManager.fileExistsAtPath(cacheDir)) {
            fileManager.createDirectoryAtPath(
                cacheDir,
                withIntermediateDirectories = true,
                attributes = null,
                error = null
            )
        }

        return cacheDir
    }

    /**
     * 獲取快取檔案路徑
     */
    private fun getCacheFilePath(walletId: String): String {
        return "${getCacheDir()}/$walletId.cache"
    }

    actual fun saveMoneroCache(walletId: String, cache: MoneroWalletCache) {
        try {
            val jsonStr = json.encodeToString(cache)
            val encrypted = encrypt(jsonStr)
            val filePath = getCacheFilePath(walletId)

            // 寫入檔案
            val nsString = encrypted as NSString
            nsString.writeToFile(
                filePath,
                atomically = true,
                encoding = NSUTF8StringEncoding,
                error = null
            )

            // 同時保存元數據到 NSUserDefaults
            userDefaults.setObject(cache.lastSyncTimestamp, "${PREFS_KEY_PREFIX}${walletId}_last_sync")
            userDefaults.setObject(cache.lastScannedHeight, "${PREFS_KEY_PREFIX}${walletId}_last_height")
            userDefaults.setObject(cache.primaryAddress, "${PREFS_KEY_PREFIX}${walletId}_address")
            userDefaults.synchronize()
        } catch (e: Exception) {
            println("Error saving Monero cache: ${e.message}")
        }
    }

    actual fun loadMoneroCache(walletId: String): MoneroWalletCache? {
        return try {
            val filePath = getCacheFilePath(walletId)

            if (!fileManager.fileExistsAtPath(filePath)) {
                return null
            }

            val encrypted = NSString.stringWithContentsOfFile(
                filePath,
                encoding = NSUTF8StringEncoding,
                error = null
            ) as? String ?: return null

            val decrypted = decrypt(encrypted)
            json.decodeFromString<MoneroWalletCache>(decrypted)
        } catch (e: Exception) {
            println("Error loading Monero cache: ${e.message}")
            null
        }
    }

    actual fun deleteMoneroCache(walletId: String) {
        val filePath = getCacheFilePath(walletId)

        if (fileManager.fileExistsAtPath(filePath)) {
            fileManager.removeItemAtPath(filePath, error = null)
        }

        // 清除 NSUserDefaults
        userDefaults.removeObjectForKey("${PREFS_KEY_PREFIX}${walletId}_last_sync")
        userDefaults.removeObjectForKey("${PREFS_KEY_PREFIX}${walletId}_last_height")
        userDefaults.removeObjectForKey("${PREFS_KEY_PREFIX}${walletId}_address")
        userDefaults.synchronize()
    }

    actual fun existsMoneroCache(walletId: String): Boolean {
        val filePath = getCacheFilePath(walletId)
        return fileManager.fileExistsAtPath(filePath)
    }

    actual fun listWalletIds(): List<String> {
        return try {
            val cacheDir = getCacheDir()
            val contents = fileManager.contentsOfDirectoryAtPath(cacheDir, error = null) as? List<*>

            contents?.mapNotNull { fileName ->
                (fileName as? String)?.let { name ->
                    if (name.endsWith(".cache")) {
                        name.removeSuffix(".cache")
                    } else null
                }
            } ?: emptyList()
        } catch (e: Exception) {
            println("Error listing wallet IDs: ${e.message}")
            emptyList()
        }
    }

    actual fun clearAllCaches() {
        try {
            val cacheDir = getCacheDir()
            val contents = fileManager.contentsOfDirectoryAtPath(cacheDir, error = null) as? List<*>

            contents?.forEach { fileName ->
                val filePath = "$cacheDir/$fileName"
                fileManager.removeItemAtPath(filePath, error = null)
            }

            // 清除所有相關的 UserDefaults
            val allKeys = userDefaults.dictionaryRepresentation().keys
            allKeys.forEach { key ->
                val keyString = key.toString()
                if (keyString.startsWith(PREFS_KEY_PREFIX)) {
                    userDefaults.removeObjectForKey(keyString)
                }
            }
            userDefaults.synchronize()
        } catch (e: Exception) {
            println("Error clearing all caches: ${e.message}")
        }
    }

    /**
     * 簡單的加密（使用 Base64）
     * 生產環境應使用 CommonCrypto AES 加密
     */
    private fun encrypt(data: String): String {
        val nsData = (data as NSString).dataUsingEncoding(NSUTF8StringEncoding)
        return nsData?.base64EncodedStringWithOptions(0u) ?: data
    }

    /**
     * 解密
     */
    private fun decrypt(data: String): String {
        val nsData = NSData.create(base64EncodedString = data, options = 0u)
        return nsData?.let {
            NSString.create(data = it, encoding = NSUTF8StringEncoding) as? String
        } ?: data
    }
}

/**
 * iOS 平台鍵值儲存實現
 * 使用 NSUserDefaults 實現輕量級鍵值存儲
 */
@OptIn(ExperimentalForeignApi::class)
actual class PlatformKeyValueStorage {

    companion object {
        private const val PREFS_NAME = "monero_wallet_kv_"
    }

    private val userDefaults = NSUserDefaults.standardUserDefaults

    actual fun putString(key: String, value: String) {
        userDefaults.setObject(value, "$PREFS_NAME$key")
        userDefaults.synchronize()
    }

    actual fun getString(key: String): String? {
        return userDefaults.stringForKey("$PREFS_NAME$key")
    }

    actual fun putLong(key: String, value: Long) {
        userDefaults.setObject(value, "$PREFS_NAME$key")
        userDefaults.synchronize()
    }

    actual fun getLong(key: String, defaultValue: Long): Long {
        val storedValue = userDefaults.objectForKey("$PREFS_NAME$key")
        return when (storedValue) {
            is NSNumber -> storedValue.longValue
            else -> defaultValue
        }
    }

    actual fun putBoolean(key: String, value: Boolean) {
        userDefaults.setBool(value, "$PREFS_NAME$key")
        userDefaults.synchronize()
    }

    actual fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        val prefKey = "$PREFS_NAME$key"
        // 檢查鍵是否存在
        return if (userDefaults.objectForKey(prefKey) != null) {
            userDefaults.boolForKey(prefKey)
        } else {
            defaultValue
        }
    }

    actual fun remove(key: String) {
        userDefaults.removeObjectForKey("$PREFS_NAME$key")
        userDefaults.synchronize()
    }

    actual fun clear() {
        val allKeys = userDefaults.dictionaryRepresentation().keys
        allKeys.forEach { key ->
            val keyString = key.toString()
            if (keyString.startsWith(PREFS_NAME)) {
                userDefaults.removeObjectForKey(keyString)
            }
        }
        userDefaults.synchronize()
    }

    actual fun contains(key: String): Boolean {
        return userDefaults.objectForKey("$PREFS_NAME$key") != null
    }

    actual fun getAllKeys(): Set<String> {
        val allKeys = userDefaults.dictionaryRepresentation().keys
        return allKeys.mapNotNull { key ->
            val keyString = key.toString()
            if (keyString.startsWith(PREFS_NAME)) {
                keyString.removePrefix(PREFS_NAME)
            } else {
                null
            }
        }.toSet()
    }
}
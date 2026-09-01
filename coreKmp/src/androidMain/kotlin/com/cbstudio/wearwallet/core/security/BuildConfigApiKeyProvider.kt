package com.cbstudio.wearwallet.core.security

import android.content.Context

/**
 * Android 平台的環境變量 API Key 提供者實現
 */
actual class EnvironmentApiKeyProvider : ApiKeyProvider {
    actual override suspend fun getKey(keyName: String): String? {
        return System.getenv(keyName)
    }
    
    actual override suspend fun getAllKeys(): Map<String, String> {
        return ApiKeyManager.run {
            listOf(
                KEY_INFURA, KEY_ALCHEMY, KEY_MORALIS,
                KEY_ETHERSCAN, KEY_BSCSCAN, KEY_POLYGONSCAN,
                KEY_BLOCKSTREAM, KEY_BLOCKCYPHER,
                KEY_SOLANA_RPC, KEY_TRON_GRID
            ).mapNotNull { key ->
                System.getenv(key)?.let { value ->
                    key to value
                }
            }.toMap()
        }
    }
}

/**
 * Android BuildConfig API Key 提供者
 * 從 ApiConfig 讀取已初始化的 API Keys
 *
 * 注意：ApiConfig 在 WalletApplication 中從 BuildConfig 初始化
 */
actual class BuildConfigApiKeyProvider : ApiKeyProvider {

    actual override suspend fun getKey(keyName: String): String? {
        return try {
            when (keyName) {
                ApiKeyManager.KEY_INFURA -> getApiConfigValue { com.cbstudio.wearwallet.core.network.ApiConfig.infuraApiKey }
                ApiKeyManager.KEY_ETHERSCAN -> getApiConfigValue { com.cbstudio.wearwallet.core.network.ApiConfig.etherscanApiKey }
                ApiKeyManager.KEY_BSCSCAN -> getApiConfigValue { com.cbstudio.wearwallet.core.network.ApiConfig.etherscanApiKey } // BSCScan 使用相同格式
                ApiKeyManager.KEY_POLYGONSCAN -> getApiConfigValue { com.cbstudio.wearwallet.core.network.ApiConfig.etherscanApiKey } // PolygonScan 使用相同格式
                ApiKeyManager.KEY_MORALIS -> getApiConfigValue { com.cbstudio.wearwallet.core.network.ApiConfig.moralisApiKey }
                else -> null
            }
        } catch (e: Exception) {
            println("⚠️ 無法從 ApiConfig 獲取 $keyName: ${e.message}")
            null
        }
    }

    actual override suspend fun getAllKeys(): Map<String, String> {
        val keys = mutableMapOf<String, String>()

        // 從 ApiConfig 獲取所有已初始化的 keys
        getApiConfigValue { com.cbstudio.wearwallet.core.network.ApiConfig.infuraApiKey }?.let {
            keys[ApiKeyManager.KEY_INFURA] = it
        }

        getApiConfigValue { com.cbstudio.wearwallet.core.network.ApiConfig.etherscanApiKey }?.let {
            keys[ApiKeyManager.KEY_ETHERSCAN] = it
            keys[ApiKeyManager.KEY_BSCSCAN] = it  // 相同 API key 格式
            keys[ApiKeyManager.KEY_POLYGONSCAN] = it // 相同 API key 格式
        }

        getApiConfigValue { com.cbstudio.wearwallet.core.network.ApiConfig.moralisApiKey }?.let {
            keys[ApiKeyManager.KEY_MORALIS] = it
        }

        return keys
    }

    /**
     * 從 ApiConfig 獲取值，過濾預設佔位符
     */
    private fun getApiConfigValue(getter: () -> String): String? {
        return try {
            val value = getter()
            // 過濾掉預設佔位符值
            if (value.isNotBlank() &&
                !value.startsWith("YOUR_") &&
                value != "null") {
                value
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Android 安全存儲 API Key 提供者
 * 使用 Android Keystore 和 EncryptedSharedPreferences
 */
actual class SecureStorageApiKeyProvider : ApiKeyProvider {
    
    private var context: Context? = null
    private val prefsName = "secure_api_keys"
    
    /**
     * 初始化，需要 Android Context
     */
    fun initialize(context: Context) {
        this.context = context
    }
    
    actual override suspend fun getKey(keyName: String): String? {
        val ctx = context ?: return null
        
        return try {
            val encryptedPrefs = getEncryptedSharedPreferences(ctx)
            encryptedPrefs.getString(keyName, null)
        } catch (e: Exception) {
            println("⚠️ 無法從安全存儲獲取 $keyName: ${e.message}")
            null
        }
    }
    
    actual override suspend fun getAllKeys(): Map<String, String> {
        val ctx = context ?: return emptyMap()
        val keys = mutableMapOf<String, String>()
        
        try {
            val encryptedPrefs = getEncryptedSharedPreferences(ctx)
            val allKeys = listOf(
                ApiKeyManager.KEY_INFURA,
                ApiKeyManager.KEY_ALCHEMY,
                ApiKeyManager.KEY_MORALIS,
                ApiKeyManager.KEY_ETHERSCAN,
                ApiKeyManager.KEY_BSCSCAN,
                ApiKeyManager.KEY_POLYGONSCAN,
                ApiKeyManager.KEY_BLOCKSTREAM,
                ApiKeyManager.KEY_BLOCKCYPHER,
                ApiKeyManager.KEY_SOLANA_RPC,
                ApiKeyManager.KEY_TRON_GRID
            )
            
            allKeys.forEach { key ->
                encryptedPrefs.getString(key, null)?.let { value ->
                    if (value.isNotBlank()) {
                        keys[key] = value
                    }
                }
            }
        } catch (e: Exception) {
            println("⚠️ 無法從安全存儲讀取 API Keys: ${e.message}")
        }
        
        return keys
    }
    
    /**
     * 保存 API Key 到安全存儲
     */
    suspend fun saveKey(keyName: String, value: String) {
        val ctx = context ?: return
        
        try {
            val encryptedPrefs = getEncryptedSharedPreferences(ctx)
            encryptedPrefs.edit().putString(keyName, value).apply()
        } catch (e: Exception) {
            println("❌ 無法保存 API Key 到安全存儲: ${e.message}")
        }
    }
    
    /**
     * 獲取加密的 SharedPreferences
     */
    private fun getEncryptedSharedPreferences(context: Context): android.content.SharedPreferences {
        val masterKey = androidx.security.crypto.MasterKey.Builder(context)
            .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
            .build()
        
        return androidx.security.crypto.EncryptedSharedPreferences.create(
            context,
            prefsName,
            masterKey,
            androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    
    /**
     * 清除所有存儲的 API Keys
     */
    suspend fun clearAll() {
        val ctx = context ?: return
        
        try {
            val encryptedPrefs = getEncryptedSharedPreferences(ctx)
            encryptedPrefs.edit().clear().apply()
        } catch (e: Exception) {
            println("❌ 無法清除安全存儲: ${e.message}")
        }
    }
}
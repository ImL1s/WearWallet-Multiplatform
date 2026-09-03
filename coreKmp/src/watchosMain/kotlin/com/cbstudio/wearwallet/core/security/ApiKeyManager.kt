package com.cbstudio.wearwallet.core.security

/**
 * watchOS 平台的 API Key 管理實現
 */

/**
 * watchOS 平台的環境變量 API Key 提供者實現（簡化）
 */
actual class EnvironmentApiKeyProvider : ApiKeyProvider {
    actual override suspend fun getKey(keyName: String): String? {
        return null
    }
    
    actual override suspend fun getAllKeys(): Map<String, String> {
        return emptyMap()
    }
}

/**
 * watchOS 平台的 BuildConfig API Key 提供者實現（簡化）
 */
actual class BuildConfigApiKeyProvider : ApiKeyProvider {
    actual override suspend fun getKey(keyName: String): String? {
        return null
    }
    
    actual override suspend fun getAllKeys(): Map<String, String> {
        return emptyMap()
    }
}

/**
 * watchOS 平台的安全存儲 API Key 提供者實現（簡化）
 */
actual class SecureStorageApiKeyProvider : ApiKeyProvider {
    actual override suspend fun getKey(keyName: String): String? {
        return null
    }
    
    actual override suspend fun getAllKeys(): Map<String, String> {
        return emptyMap()
    }
}

/**
 * watchOS 平台的 SecureKeyManagerFactory 實現 (P0-2: 使用 Keychain 支援的 WatchOSSecureKeyManager)
 */
actual class SecureKeyManagerFactory {
    actual companion object {
        actual fun create(config: SecureStorageConfig): SecureKeyManager {
            return WatchOSSecureKeyManager(config)
        }
    }
}
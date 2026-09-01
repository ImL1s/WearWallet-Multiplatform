package com.cbstudio.wearwallet.core.security

import platform.Foundation.NSProcessInfo

/**
 * iOS 平台的環境變量 API Key 提供者實現
 */
actual class EnvironmentApiKeyProvider : ApiKeyProvider {
    actual override suspend fun getKey(keyName: String): String? {
        return NSProcessInfo.processInfo.environment[keyName] as? String
    }
    
    actual override suspend fun getAllKeys(): Map<String, String> {
        val environment = NSProcessInfo.processInfo.environment
        return ApiKeyManager.run {
            listOf(
                KEY_INFURA, KEY_ALCHEMY, KEY_MORALIS,
                KEY_ETHERSCAN, KEY_BSCSCAN, KEY_POLYGONSCAN,
                KEY_BLOCKSTREAM, KEY_BLOCKCYPHER,
                KEY_SOLANA_RPC, KEY_TRON_GRID
            ).mapNotNull { key ->
                (environment[key] as? String)?.let { value ->
                    key to value
                }
            }.toMap()
        }
    }
}

/**
 * iOS 平台的 BuildConfig API Key 提供者實現
 */
actual class BuildConfigApiKeyProvider : ApiKeyProvider {
    actual override suspend fun getKey(keyName: String): String? {
        // iOS 沒有 BuildConfig，可以從 Info.plist 或其他配置讀取
        return null
    }
    
    actual override suspend fun getAllKeys(): Map<String, String> {
        return emptyMap()
    }
}

/**
 * iOS 平台的安全存儲 API Key 提供者實現
 */
actual class SecureStorageApiKeyProvider : ApiKeyProvider {
    actual override suspend fun getKey(keyName: String): String? {
        // iOS Keychain 實現
        return null
    }
    
    actual override suspend fun getAllKeys(): Map<String, String> {
        return emptyMap()
    }
}
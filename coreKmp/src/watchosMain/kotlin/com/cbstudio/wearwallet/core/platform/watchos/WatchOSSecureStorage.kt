package com.cbstudio.wearwallet.core.platform.watchos

import com.cbstudio.wearwallet.core.platform.SecureStorage
import com.cbstudio.wearwallet.core.security.WatchOSHotWalletUnsupportedException

/**
 * watchOS SecureStorage 實現
 * 
 * 依據 P0-2 規範，禁止在生產環境使用記憶體 Map 作為安全存儲。
 * watchOS 金鑰存儲請使用 WatchOSSecureKeyManager (Keychain)。
 */
class WatchOSSecureStorage : SecureStorage {
    
    override suspend fun encrypt(plainText: String): String {
        throw WatchOSHotWalletUnsupportedException("WatchOSSecureStorage plain storage is disabled. Use WatchOSSecureKeyManager.")
    }
    
    override suspend fun decrypt(encryptedText: String): String {
        throw WatchOSHotWalletUnsupportedException("WatchOSSecureStorage plain storage is disabled. Use WatchOSSecureKeyManager.")
    }
    
    override suspend fun saveSecure(key: String, value: String) {
        throw WatchOSHotWalletUnsupportedException("WatchOSSecureStorage plain storage is disabled. Use WatchOSSecureKeyManager.")
    }
    
    override suspend fun getSecure(key: String): String? {
        throw WatchOSHotWalletUnsupportedException("WatchOSSecureStorage plain storage is disabled. Use WatchOSSecureKeyManager.")
    }
    
    override suspend fun removeSecure(key: String) {
        throw WatchOSHotWalletUnsupportedException("WatchOSSecureStorage plain storage is disabled. Use WatchOSSecureKeyManager.")
    }
    
    override suspend fun hasKey(key: String): Boolean {
        return false
    }
}
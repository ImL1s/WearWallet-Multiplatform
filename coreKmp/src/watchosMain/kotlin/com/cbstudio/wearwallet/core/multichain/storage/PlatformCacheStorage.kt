package com.cbstudio.wearwallet.core.multichain.storage

import com.cbstudio.wearwallet.core.multichain.monero.crypto.MoneroWalletCache

/**
 * watchOS 平台的緩存存儲實現（簡化版本）
 */
actual class PlatformCacheStorage actual constructor() {
    private val cache = mutableMapOf<String, MoneroWalletCache>()
    
    actual fun saveMoneroCache(walletId: String, cache: MoneroWalletCache) {
        this.cache[walletId] = cache
    }
    
    actual fun loadMoneroCache(walletId: String): MoneroWalletCache? {
        return cache[walletId]
    }
    
    actual fun deleteMoneroCache(walletId: String) {
        cache.remove(walletId)
    }
    
    actual fun existsMoneroCache(walletId: String): Boolean {
        return cache.containsKey(walletId)
    }
    
    actual fun listWalletIds(): List<String> {
        return cache.keys.toList()
    }
    
    actual fun clearAllCaches() {
        cache.clear()
    }
}

/**
 * watchOS 平台的鍵值存儲實現（簡化版本）
 */
actual class PlatformKeyValueStorage actual constructor() {
    private val storage = mutableMapOf<String, String>()
    
    actual fun putString(key: String, value: String) {
        storage[key] = value
    }
    
    actual fun getString(key: String): String? {
        return storage[key]
    }
    
    actual fun putLong(key: String, value: Long) {
        storage[key] = value.toString()
    }
    
    actual fun getLong(key: String, defaultValue: Long): Long {
        return storage[key]?.toLongOrNull() ?: defaultValue
    }
    
    actual fun putBoolean(key: String, value: Boolean) {
        storage[key] = value.toString()
    }
    
    actual fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return storage[key]?.toBooleanStrictOrNull() ?: defaultValue
    }
    
    actual fun remove(key: String) {
        storage.remove(key)
    }
    
    actual fun clear() {
        storage.clear()
    }
    
    actual fun contains(key: String): Boolean {
        return storage.containsKey(key)
    }
    
    actual fun getAllKeys(): Set<String> {
        return storage.keys.toSet()
    }
}
package com.cbstudio.wearwallet.core.multichain.storage

import com.cbstudio.wearwallet.core.multichain.monero.crypto.MoneroWalletCache

/**
 * KMP 跨平台快取儲存介面
 * 使用 expect/actual 模式實現不同平台
 */
expect class PlatformCacheStorage() {
    /**
     * 保存 Monero 錢包快取
     */
    fun saveMoneroCache(walletId: String, cache: MoneroWalletCache)
    
    /**
     * 載入 Monero 錢包快取
     */
    fun loadMoneroCache(walletId: String): MoneroWalletCache?
    
    /**
     * 刪除快取
     */
    fun deleteMoneroCache(walletId: String)
    
    /**
     * 檢查快取是否存在
     */
    fun existsMoneroCache(walletId: String): Boolean
    
    /**
     * 列出所有錢包ID
     */
    fun listWalletIds(): List<String>
    
    /**
     * 清除所有快取
     */
    fun clearAllCaches()
}

/**
 * 通用的跨平台鍵值儲存
 */
expect class PlatformKeyValueStorage() {
    fun putString(key: String, value: String)
    fun getString(key: String): String?
    fun putLong(key: String, value: Long)
    fun getLong(key: String, defaultValue: Long): Long
    fun putBoolean(key: String, value: Boolean)
    fun getBoolean(key: String, defaultValue: Boolean): Boolean
    fun remove(key: String)
    fun clear()
    fun contains(key: String): Boolean
    fun getAllKeys(): Set<String>
}
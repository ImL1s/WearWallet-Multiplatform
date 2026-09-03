package com.cbstudio.wearwallet.core.multichain.storage

import com.cbstudio.wearwallet.core.multichain.monero.crypto.CacheStorage
import com.cbstudio.wearwallet.core.multichain.monero.crypto.MoneroWalletCache

/**
 * 適配器類，將 PlatformCacheStorage 轉換為 CacheStorage 接口
 */
class CacheStorageAdapter(
    private val platformStorage: PlatformCacheStorage
) : CacheStorage {

    override fun save(walletId: String, cache: MoneroWalletCache) {
        platformStorage.saveMoneroCache(walletId, cache)
    }

    override fun load(walletId: String): MoneroWalletCache? {
        return platformStorage.loadMoneroCache(walletId)
    }

    override fun delete(walletId: String) {
        platformStorage.deleteMoneroCache(walletId)
    }

    override fun exists(walletId: String): Boolean {
        return platformStorage.existsMoneroCache(walletId)
    }
}
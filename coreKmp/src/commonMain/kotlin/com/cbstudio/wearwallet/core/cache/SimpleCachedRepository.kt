package com.cbstudio.wearwallet.core.cache

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.*
import com.cbstudio.wearwallet.core.domain.repository.*
import com.cbstudio.wearwallet.core.utils.Logger
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * 簡化版快取 Repository 實現
 * 
 * 使用委託模式，只覆寫需要快取的方法
 * 
 * Created: 2025-01-17
 */

/**
 * 錢包快取 Repository
 */
class SimpleCachedWalletRepository(
    private val delegate: WalletRepository,
    private val cache: CacheManager<String, Any> = GlobalCacheManager.walletCache
) : WalletRepository by delegate {
    
    override suspend fun getAllWallets(): Result<List<WalletAccount>> {
        val cacheKey = "all_wallets"
        
        @Suppress("UNCHECKED_CAST")
        val cached = cache.get(cacheKey) as? List<WalletAccount>
        if (cached != null) {
            Logger.d("CachedWallet", "Cache hit: getAllWallets")
            return Result.Success(cached)
        }
        
        val result = delegate.getAllWallets()
        if (result is Result.Success) {
            cache.put(cacheKey, result.data, ttl = 5.minutes)
        }
        
        return result
    }
    
    override suspend fun getWallet(id: String): Result<WalletAccount?> {
        val cacheKey = "wallet_$id"
        
        @Suppress("UNCHECKED_CAST")
        val cached = cache.get(cacheKey) as? WalletAccount
        if (cached != null) {
            return Result.Success(cached)
        }
        
        val result = delegate.getWallet(id)
        if (result is Result.Success && result.data != null) {
            cache.put(cacheKey, result.data, ttl = 5.minutes)
        }
        
        return result
    }
    
    override suspend fun getActiveWallet(): Result<WalletAccount?> {
        val cacheKey = "active_wallet"
        
        @Suppress("UNCHECKED_CAST")
        val cached = cache.get(cacheKey) as? WalletAccount
        if (cached != null) {
            return Result.Success(cached)
        }
        
        val result = delegate.getActiveWallet()
        if (result is Result.Success && result.data != null) {
            cache.put(cacheKey, result.data, ttl = 1.minutes)
        }
        
        return result
    }
    
    // 寫入操作清除快取
    override suspend fun createWallet(
        name: String,
        mnemonic: CharArray,
        password: CharArray,
        chainType: com.cbstudio.wearwallet.core.domain.model.ChainType,
        authContext: com.cbstudio.wearwallet.core.security.AuthenticationContext
    ): Result<WalletAccount> {
        val result = delegate.createWallet(name, mnemonic, password, chainType, authContext)
        if (result is Result.Success) {
            clearWalletCache()
        }
        return result
    }
    
    override suspend fun deleteWallet(id: String, authContext: com.cbstudio.wearwallet.core.security.AuthenticationContext?): Result<Unit> {
        val result = delegate.deleteWallet(id, authContext)
        if (result is Result.Success) {
            clearWalletCache()
        }
        return result
    }
    
    override suspend fun updateWallet(wallet: WalletAccount): Result<Unit> {
        val result = delegate.updateWallet(wallet)
        if (result is Result.Success) {
            clearWalletCache()
        }
        return result
    }
    
    override suspend fun setActiveWallet(walletId: String): Result<Unit> {
        val result = delegate.setActiveWallet(walletId)
        if (result is Result.Success) {
            cache.remove("active_wallet")
        }
        return result
    }
    
    private suspend fun clearWalletCache() {
        cache.remove("all_wallets")
        cache.remove("active_wallet")
        Logger.d("CachedWallet", "Cache cleared")
    }
}

/**
 * 價格快取 Repository
 */
class SimpleCachedPriceRepository(
    private val delegate: PriceRepository,
    private val cache: CacheManager<String, Double> = GlobalCacheManager.priceCache
) : PriceRepository by delegate {
    
    override suspend fun getTokenPrice(
        symbol: String,
        currency: String
    ): Result<Double> {
        val cacheKey = "${symbol}_$currency"
        
        cache.get(cacheKey)?.let { cached ->
            Logger.d("CachedPrice", "Cache hit: $symbol/$currency = $cached")
            return Result.Success(cached)
        }
        
        val result = delegate.getTokenPrice(symbol, currency)
        if (result is Result.Success) {
            cache.put(cacheKey, result.data, ttl = 30.seconds)
        }
        
        return result
    }
    
    override suspend fun getBatchPrices(
        symbols: List<String>,
        currency: String
    ): Result<Map<String, Double>> {
        val cachedPrices = mutableMapOf<String, Double>()
        val missingSymbols = mutableListOf<String>()
        
        // 檢查快取
        symbols.forEach { symbol ->
            val cached = cache.get("${symbol}_$currency")
            if (cached != null) {
                cachedPrices[symbol] = cached
            } else {
                missingSymbols.add(symbol)
            }
        }
        
        // 全部在快取中
        if (missingSymbols.isEmpty()) {
            return Result.Success(cachedPrices)
        }
        
        // 獲取缺失的
        val result = delegate.getBatchPrices(missingSymbols, currency)
        
        return when (result) {
            is Result.Success -> {
                result.data.forEach { (symbol, price) ->
                    cache.put("${symbol}_$currency", price, ttl = 30.seconds)
                    cachedPrices[symbol] = price
                }
                Result.Success(cachedPrices)
            }
            is Result.Failure -> result
            is Result.Loading -> result
        }
    }
}

/**
 * 簡化版快取工廠
 */
object SimpleCacheFactory {
    
    fun wrapWithCache(repository: WalletRepository): WalletRepository {
        return SimpleCachedWalletRepository(repository)
    }
    
    fun wrapWithCache(repository: PriceRepository): PriceRepository {
        return SimpleCachedPriceRepository(repository)
    }
}
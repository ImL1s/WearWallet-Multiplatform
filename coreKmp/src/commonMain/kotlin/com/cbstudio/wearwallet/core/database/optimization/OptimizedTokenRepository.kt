package com.cbstudio.wearwallet.core.database.optimization

import com.cbstudio.wearwallet.core.database.CoreWalletDatabase
import com.cbstudio.wearwallet.core.domain.model.Token
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.utils.Logger
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private typealias TokenEntity = com.cbstudio.wearwallet.core.database.Token

/**
 * 優化的 Token Repository 實作
 * 
 * 整合查詢優化器、快取和批量操作
 * 
 * Created: 2025-01-17
 */
class OptimizedTokenRepository(
    private val database: CoreWalletDatabase,
    private val queryExecutor: CachedQueryExecutor = GlobalQueryCache.getExecutor("token"),
    private val optimizer: QueryOptimizer = GlobalQueryOptimizer.optimizer
) {
    
    private val tokenQueries = database.tokenQueries
    
    /**
     * 獲取錢包的所有代幣（優化版）
     */
    suspend fun getTokensByWallet(walletId: Long): List<Token> {
        return optimizer.analyzeQuery("getTokensByWallet") {
            queryExecutor.executeCachedList<Token>(
                queryId = "tokens_wallet_$walletId",
                cacheDuration = 1.minutes
            ) {
                tokenQueries.selectByWalletId(walletId)
                    .executeAsList()
                    .map { it.toDomainModel() }
            }
        }
    }
    
    /**
     * 獲取可見代幣（使用部分索引）
     */
    suspend fun getVisibleTokens(walletId: Long): List<Token> {
        return optimizer.analyzeQuery("getVisibleTokens") {
            queryExecutor.executeCachedList<Token>(
                queryId = "visible_tokens_$walletId",
                cacheDuration = 30.seconds
            ) {
                tokenQueries.selectVisibleTokens(walletId)
                    .executeAsList()
                    .map { it.toDomainModel() }
            }
        }
    }
    
    /**
     * 批量更新代幣餘額
     */
    suspend fun updateTokenBalances(updates: List<TokenBalanceUpdate>) {
        if (updates.isEmpty()) return
        
        optimizer.optimizeBatchQuery(
            queryName = "updateTokenBalances",
            items = updates,
            batchSize = 50
        ) { batch ->
            tokenQueries.transaction {
                batch.forEach { update ->
                    tokenQueries.updateBalance(
                        balance = update.balance,
                        id = update.tokenId
                    )
                }
            }
        }
        
        // 清除相關快取
        updates.map { it.walletId }.distinct().forEach { walletId ->
            queryExecutor.notifyTableUpdate("token_wallet_$walletId")
        }
    }
    
    /**
     * 批量更新代幣價格
     */
    suspend fun updateTokenPrices(updates: List<TokenPriceUpdate>) {
        if (updates.isEmpty()) return
        
        optimizer.optimizeBatchQuery(
            queryName = "updateTokenPrices",
            items = updates,
            batchSize = 100
        ) { batch ->
            tokenQueries.transaction {
                batch.forEach { update ->
                    tokenQueries.updatePrice(
                        usd_price = update.price,
                        price_change_24h = update.priceChange24h,
                        id = update.tokenId
                    )
                }
            }
        }
        
        // 價格更新不需要清除快取（價格有自己的 TTL）
    }
    
    /**
     * 計算錢包總價值（使用快取）
     */
    suspend fun calculateWalletValue(walletId: Long): Double {
        return optimizer.analyzeQuery("calculateWalletValue") {
            queryExecutor.executeCached<Double>(
                queryId = "wallet_value_$walletId",
                cacheDuration = 10.seconds
            ) {
                tokenQueries.calculateWalletValue(walletId)
                    .executeAsOneOrNull()?.total_value ?: 0.0
            }
        }
    }
    
    /**
     * 搜尋代幣（使用索引）
     */
    suspend fun searchTokens(query: String, walletId: Long): List<Token> {
        return optimizer.analyzeQuery("searchTokens") {
            queryExecutor.executeCachedList<Token>(
                queryId = "search_tokens_$query",
                cacheDuration = 30.seconds
            ) {
                // 使用索引搜尋 - 只搜尋指定錢包的代幣
                tokenQueries.selectByWalletId(walletId)
                    .executeAsList()
                    .filter { token ->
                        token.symbol.contains(query, ignoreCase = true) ||
                        token.name.contains(query, ignoreCase = true)
                    }
                    .map { token -> token.toDomainModel() }
            }
        }
    }
    
    /**
     * 獲取需要更新的代幣（批量）
     */
    suspend fun getTokensNeedingUpdate(maxAge: Long = 300): List<TokenUpdateInfo> {
        return optimizer.analyzeQuery("getTokensNeedingUpdate") {
            tokenQueries.selectTokensNeedingPriceUpdate()
                .executeAsList()
                .map { 
                    TokenUpdateInfo(
                        symbol = it.symbol,
                        chainId = it.chain_id
                    )
                }
        }
    }
    
    /**
     * 觀察錢包代幣變化（使用 Flow）
     */
    fun observeWalletTokens(walletId: Long): Flow<List<Token>> {
        return tokenQueries.selectByWalletId(walletId)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { tokens ->
                tokens.map { it.toDomainModel() }
            }
    }
}

/**
 * 代幣餘額更新
 */
data class TokenBalanceUpdate(
    val tokenId: Long,
    val walletId: Long,
    val balance: String
)

/**
 * 代幣價格更新
 */
data class TokenPriceUpdate(
    val tokenId: Long,
    val price: Double,
    val priceChange24h: Double?
)

/**
 * 代幣更新資訊
 */
data class TokenUpdateInfo(
    val symbol: String,
    val chainId: Long
)

/**
 * 擴展函數：轉換為領域模型
 */
private fun TokenEntity.toDomainModel(): Token {
    return Token(
        id = id.toString(),
        address = address,
        symbol = symbol,
        name = name,
        decimals = decimals.toInt(),
        chainType = runCatching { ChainType.valueOf(chain_type) }.getOrDefault(ChainType.ETHEREUM),
        balance = balance,
        logoUrl = logo_url,
        usdPrice = usd_price,
        isNative = is_native == 1L
    )
}
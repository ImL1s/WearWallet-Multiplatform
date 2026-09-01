package com.cbstudio.wearwallet.core.rango

import com.cbstudio.wearwallet.core.rango.model.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

/**
 * Rango Metadata Repository
 * 
 * Provides cached access to Rango metadata (blockchains, tokens, swappers).
 * Caches data to avoid repeated API calls.
 */
class RangoMetadataRepository(
    private val client: RangoClient
) {
    private var cachedMetadata: RangoMetaResponse? = null
    private var cacheTimestamp: Long = 0
    private val cacheMutex = Mutex()
    
    companion object {
        // Cache duration: 1 hour
        private const val CACHE_DURATION_MS = 60 * 60 * 1000L
        
        // Default EVM chains we support
        val SUPPORTED_CHAINS = listOf("BSC", "POLYGON", "ETH", "ARBITRUM", "OPTIMISM", "BASE")
    }
    
    // ============================================
    // Public API
    // ============================================
    
    /**
     * Get all metadata (with caching)
     */
    suspend fun getMetadata(forceRefresh: Boolean = false): Result<RangoMetaResponse> {
        return cacheMutex.withLock {
            val now = Clock.System.now().toEpochMilliseconds()
            
            // Return cached data if still valid
            if (!forceRefresh && cachedMetadata != null && (now - cacheTimestamp) < CACHE_DURATION_MS) {
                return@withLock Result.success(cachedMetadata!!)
            }
            
            // Fetch fresh data
            try {
                println("[RangoMetadataRepository] getMetadata: Fetching from API...")
                val response = client.getMetadata(
                    blockchains = SUPPORTED_CHAINS,
                    excludeNonPopulars = true
                )
                println("[RangoMetadataRepository] getMetadata: API response received. Tokens=${response.tokens.size}")
                
                // Validate response
                if (response.blockchains.isEmpty() && response.tokens.isEmpty()) {
                    println("[RangoMetadataRepository] getMetadata: API returned empty data!")
                    return@withLock Result.failure(Exception("Empty metadata response"))
                }
                cachedMetadata = response
                cacheTimestamp = now
                Result.success(response)
            } catch (e: Exception) {
                // Return stale cache if available
                cachedMetadata?.let { 
                    Result.success(it) 
                } ?: Result.failure(e)
            }
        }
    }
    
    /**
     * Get enabled blockchains
     */
    suspend fun getEnabledBlockchains(): Result<List<RangoBlockchain>> {
        return getMetadata().map { meta ->
            meta.blockchains.enabledOnly()
        }
    }
    
    /**
     * Get EVM blockchains only
     */
    suspend fun getEvmBlockchains(): Result<List<RangoBlockchain>> {
        return getMetadata().map { meta ->
            meta.blockchains.evmChains().enabledOnly()
        }
    }
    
    /**
     * Get blockchain by name
     */
    suspend fun getBlockchain(name: String): Result<RangoBlockchain?> {
        return getMetadata().map { meta ->
            meta.blockchains.findByName(name)
        }
    }
    
    /**
     * Get blockchain by chainId
     */
    suspend fun getBlockchainByChainId(chainId: String): Result<RangoBlockchain?> {
        return getMetadata().map { meta ->
            meta.blockchains.findByChainId(chainId)
        }
    }
    
    /**
     * Get tokens for a specific blockchain
     */
    suspend fun getTokensForBlockchain(blockchain: String): Result<List<RangoTokenMeta>> {
        return getMetadata().map { meta ->
            meta.tokens.forBlockchain(blockchain)
        }
    }
    
    /**
     * Get popular tokens for a blockchain
     */
    suspend fun getPopularTokens(blockchain: String): Result<List<RangoTokenMeta>> {
        return getMetadata().map { meta ->
            meta.tokens.forBlockchain(blockchain).popularOnly()
        }
    }
    
    /**
     * Get native token for a blockchain
     */
    suspend fun getNativeToken(blockchain: String): Result<RangoTokenMeta?> {
        return getMetadata().map { meta ->
            meta.tokens.findNativeToken(blockchain)
        }
    }
    
    /**
     * Find token by address
     */
    suspend fun findToken(blockchain: String, address: String?): Result<RangoTokenMeta?> {
        return getMetadata().map { meta ->
            meta.tokens.findByAddress(blockchain, address)
        }
    }
    
    /**
     * Get all swappers
     */
    suspend fun getSwappers(): Result<List<RangoSwapperMeta>> {
        return getMetadata().map { meta ->
            meta.swappers.filter { it.enabled }
        }
    }
    
    /**
     * Get DEX swappers only
     */
    suspend fun getDexSwappers(): Result<List<RangoSwapperMeta>> {
        return getMetadata().map { meta ->
            meta.swappers.filter { it.enabled && it.isDex }
        }
    }
    
    /**
     * Get bridge swappers only
     */
    suspend fun getBridgeSwappers(): Result<List<RangoSwapperMeta>> {
        return getMetadata().map { meta ->
            meta.swappers.filter { it.enabled && it.isBridge }
        }
    }
    
    // ============================================
    // Utility Methods
    // ============================================
    
    /**
     * Check if a blockchain is supported and enabled
     */
    suspend fun isBlockchainSupported(name: String): Boolean {
        return getBlockchain(name).getOrNull()?.enabled == true
    }
    
    /**
     * Check if a token is supported on a blockchain
     */
    suspend fun isTokenSupported(blockchain: String, address: String?): Boolean {
        return findToken(blockchain, address).getOrNull() != null
    }
    
    /**
     * Get Rango blockchain name from chainId
     * Useful for converting from Web3 chainId to Rango name
     */
    suspend fun getRangoChainName(chainId: Int): String? {
        return getBlockchainByChainId(chainId.toString()).getOrNull()?.name
    }
    
    /**
     * Format asset string for Rango API
     */
    suspend fun formatAssetString(blockchain: String, address: String?): String {
        val token = findToken(blockchain, address).getOrNull()
        return token?.toAssetString() ?: run {
            // Fallback formatting
            if (address.isNullOrEmpty()) {
                "$blockchain.${getNativeSymbol(blockchain)}"
            } else {
                "$blockchain--$address"
            }
        }
    }
    
    /**
     * Get block explorer URL for a transaction
     */
    suspend fun getTransactionExplorerUrl(blockchain: String, txHash: String): String? {
        val chain = getBlockchain(blockchain).getOrNull()
        return chain?.info?.transactionUrl?.replace("{txHash}", txHash)
    }
    
    /**
     * Get block explorer URL for an address
     */
    suspend fun getAddressExplorerUrl(blockchain: String, address: String): String? {
        val chain = getBlockchain(blockchain).getOrNull()
        return chain?.info?.addressUrl?.replace("{wallet}", address)
    }
    
    /**
     * Clear cached metadata
     */
    fun clearCache() {
        cachedMetadata = null
        cacheTimestamp = 0
    }
    
    // ============================================
    // Private Helpers
    // ============================================
    
    private fun getNativeSymbol(chain: String): String {
        return when (chain.uppercase()) {
            "BSC" -> "BNB"
            "POLYGON" -> "MATIC"
            "ETH", "ETHEREUM" -> "ETH"
            "ARBITRUM", "OPTIMISM", "BASE", "LINEA", "ZKSYNC" -> "ETH"
            "AVAX_CCHAIN", "AVALANCHE" -> "AVAX"
            "FANTOM" -> "FTM"
            else -> chain
        }
    }
}

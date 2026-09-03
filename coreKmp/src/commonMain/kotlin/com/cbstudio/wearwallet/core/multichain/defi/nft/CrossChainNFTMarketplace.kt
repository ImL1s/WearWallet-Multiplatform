package com.cbstudio.wearwallet.core.multichain.defi.nft

import kotlinx.datetime.Clock
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.core.multichain.model.BlockchainException
import co.touchlab.kermit.Logger

/**
 * 跨鏈 NFT 市場整合
 * 支援多個區塊鏈上的 NFT 交易平台整合
 */
interface CrossChainNFTMarketplace {
    
    /**
     * 市場平台名稱
     */
    val marketplaceName: String
    
    /**
     * 支援的區塊鏈列表
     */
    val supportedChains: List<MultiChainType>
    
    /**
     * 搜尋 NFT
     */
    suspend fun searchNFTs(
        query: NFTSearchQuery
    ): NFTSearchResult
    
    /**
     * 取得 NFT 詳細資訊
     */
    suspend fun getNFTDetails(
        contractAddress: String,
        tokenId: String,
        chainType: MultiChainType
    ): NFTDetails
    
    /**
     * 取得 NFT 市場價格
     */
    suspend fun getNFTPrice(
        contractAddress: String,
        tokenId: String,
        chainType: MultiChainType
    ): NFTPrice
    
    /**
     * 取得用戶 NFT 收藏
     */
    suspend fun getUserNFTs(
        walletAddress: String,
        chainType: MultiChainType,
        pagination: Pagination = Pagination()
    ): NFTCollection
    
    /**
     * 創建 NFT 購買交易
     */
    suspend fun createBuyTransaction(
        contractAddress: String,
        tokenId: String,
        buyerAddress: String,
        chainType: MultiChainType
    ): String
    
    /**
     * 創建 NFT 列售交易
     */
    suspend fun createListTransaction(
        contractAddress: String,
        tokenId: String,
        price: String,
        sellerAddress: String,
        chainType: MultiChainType
    ): String
    
    /**
     * 取得熱門 NFT 收藏
     */
    suspend fun getTrendingCollections(
        chainType: MultiChainType,
        timeframe: NFTTimeframe = NFTTimeframe.DAY_1,
        limit: Int = 20
    ): List<NFTCollection>
    
    /**
     * 取得 NFT 交易歷史
     */
    suspend fun getNFTTransactionHistory(
        contractAddress: String,
        tokenId: String? = null,
        chainType: MultiChainType,
        pagination: Pagination = Pagination()
    ): List<NFTTransaction>
}

/**
 * NFT 搜尋查詢
 */
data class NFTSearchQuery(
    val keyword: String? = null,
    val contractAddress: String? = null,
    val collectionName: String? = null,
    val chainType: MultiChainType? = null,
    val minPrice: String? = null,
    val maxPrice: String? = null,
    val category: NFTCategory? = null,
    val sortBy: NFTSortBy = NFTSortBy.RELEVANCE,
    val pagination: Pagination = Pagination()
)

/**
 * NFT 搜尋結果
 */
data class NFTSearchResult(
    val items: List<NFTItem>,
    val totalCount: Int,
    val pagination: Pagination
)

/**
 * NFT 項目
 */
data class NFTItem(
    val contractAddress: String,
    val tokenId: String,
    val name: String,
    val description: String?,
    val imageUrl: String?,
    val animationUrl: String?,
    val chainType: MultiChainType,
    val collectionName: String?,
    val owner: String,
    val price: NFTPrice?,
    val rarity: NFTRarity?
)

/**
 * NFT 詳細資訊
 */
data class NFTDetails(
    val item: NFTItem,
    val metadata: Map<String, Any>,
    val attributes: List<NFTAttribute>,
    val history: List<NFTTransaction>,
    val marketData: NFTMarketData
)

/**
 * NFT 價格
 */
data class NFTPrice(
    val amount: String,
    val currency: String,
    val usdValue: String?,
    val marketplaceName: String,
    val lastUpdated: Long
)

/**
 * NFT 收藏
 */
data class NFTCollection(
    val name: String,
    val contractAddress: String,
    val chainType: MultiChainType,
    val description: String?,
    val imageUrl: String?,
    val bannerUrl: String?,
    val totalSupply: String,
    val ownerCount: String,
    val floorPrice: NFTPrice?,
    val volume24h: String?,
    val items: List<NFTItem> = emptyList(),
    val stats: NFTCollectionStats?
)

/**
 * NFT 稀有度
 */
data class NFTRarity(
    val rank: Int?,
    val score: Double?,
    val level: NFTRarityLevel
)

/**
 * NFT 屬性
 */
data class NFTAttribute(
    val traitType: String,
    val value: String,
    val rarity: Double?
)

/**
 * NFT 交易記錄
 */
data class NFTTransaction(
    val hash: String,
    val type: NFTTransactionType,
    val from: String,
    val to: String,
    val price: NFTPrice?,
    val timestamp: Long,
    val chainType: MultiChainType
)

/**
 * NFT 市場數據
 */
data class NFTMarketData(
    val floorPrice: NFTPrice?,
    val lastSale: NFTPrice?,
    val averagePrice: NFTPrice?,
    val volume24h: String?,
    val priceChange24h: Double?
)

/**
 * NFT 收藏統計
 */
data class NFTCollectionStats(
    val floorPrice: NFTPrice,
    val volume24h: String,
    val volume7d: String,
    val volume30d: String,
    val volumeTotal: String,
    val sales24h: Int,
    val averagePrice: String,
    val marketCap: String?
)

/**
 * 分頁資訊
 */
data class Pagination(
    val page: Int = 1,
    val limit: Int = 20,
    val offset: Int = (page - 1) * limit
)

/**
 * NFT 類別
 */
enum class NFTCategory {
    ART,
    COLLECTIBLES,
    GAMING,
    MUSIC,
    PHOTOGRAPHY,
    SPORTS,
    TRADING_CARDS,
    UTILITY,
    VIRTUAL_WORLDS,
    OTHER
}

/**
 * NFT 排序方式
 */
enum class NFTSortBy {
    RELEVANCE,
    PRICE_LOW_TO_HIGH,
    PRICE_HIGH_TO_LOW,
    RECENTLY_LISTED,
    OLDEST_LISTED,
    RECENTLY_SOLD,
    ENDING_SOON
}

/**
 * NFT 時間範圍
 */
enum class NFTTimeframe {
    HOUR_1,
    HOUR_6,
    HOUR_12,
    DAY_1,
    DAY_7,
    DAY_30,
    ALL_TIME
}

/**
 * NFT 稀有度等級
 */
enum class NFTRarityLevel {
    COMMON,
    UNCOMMON,
    RARE,
    EPIC,
    LEGENDARY,
    MYTHIC
}

/**
 * NFT 交易類型
 */
enum class NFTTransactionType {
    MINT,
    TRANSFER,
    SALE,
    LIST,
    CANCEL_LIST,
    OFFER,
    CANCEL_OFFER,
    BURN
}

/**
 * OpenSea 市場整合實現
 */
class OpenSeaMarketplace(
    private val logger: Logger = Logger.withTag("OpenSeaMarketplace")
) : CrossChainNFTMarketplace {
    
    override val marketplaceName = "OpenSea"
    
    override val supportedChains = listOf(
        MultiChainType.ETHEREUM,
        MultiChainType.SOLANA
    )
    
    override suspend fun searchNFTs(query: NFTSearchQuery): NFTSearchResult {
        logger.d("Searching NFTs on OpenSea: ${query.keyword}")
        
        return try {
            // TODO: 實際的 OpenSea API 調用
            // const opensea = new OpenSeaSDK()
            // const result = await opensea.searchAssets(query)
            
            // 暫時的模擬資料
            NFTSearchResult(
                items = listOf(
                    NFTItem(
                        contractAddress = "0xBC4CA0EdA7647A8aB7C2061c2E118A18a936f13D",
                        tokenId = "1234",
                        name = "Bored Ape #1234",
                        description = "A unique Bored Ape Yacht Club NFT",
                        imageUrl = "https://example.com/ape1234.png",
                        animationUrl = null,
                        chainType = MultiChainType.ETHEREUM,
                        collectionName = "Bored Ape Yacht Club",
                        owner = "0x1234...5678",
                        price = NFTPrice(
                            amount = "50.0",
                            currency = "ETH",
                            usdValue = "90000.0",
                            marketplaceName = marketplaceName,
                            lastUpdated = Clock.System.now().toEpochMilliseconds()
                        ),
                        rarity = NFTRarity(
                            rank = 1234,
                            score = 0.85,
                            level = NFTRarityLevel.RARE
                        )
                    )
                ),
                totalCount = 1,
                pagination = query.pagination
            )
        } catch (e: Exception) {
            logger.e("Failed to search NFTs on OpenSea", e)
            throw BlockchainException.ApiException(
                query.chainType ?: MultiChainType.ETHEREUM,
                "opensea nft search",
                null,
                "Failed to search NFTs: ${e.message}",
                e
            )
        }
    }
    
    override suspend fun getNFTDetails(
        contractAddress: String,
        tokenId: String,
        chainType: MultiChainType
    ): NFTDetails {
        logger.d("Getting NFT details: $contractAddress/$tokenId on ${chainType.symbol}")
        
        // TODO: 實際的 OpenSea API 調用
        throw BlockchainException.UnsupportedOperationException(
            chainType,
            "OpenSea NFT details - implementation pending"
        )
    }
    
    override suspend fun getNFTPrice(
        contractAddress: String,
        tokenId: String,
        chainType: MultiChainType
    ): NFTPrice {
        // TODO: 實際的價格查詢
        throw BlockchainException.UnsupportedOperationException(
            chainType,
            "OpenSea NFT pricing - implementation pending"
        )
    }
    
    override suspend fun getUserNFTs(
        walletAddress: String,
        chainType: MultiChainType,
        pagination: Pagination
    ): NFTCollection {
        // TODO: 實際的用戶 NFT 查詢
        throw BlockchainException.UnsupportedOperationException(
            chainType,
            "OpenSea user NFTs - implementation pending"
        )
    }
    
    override suspend fun createBuyTransaction(
        contractAddress: String,
        tokenId: String,
        buyerAddress: String,
        chainType: MultiChainType
    ): String {
        // TODO: 實際的購買交易建構
        throw BlockchainException.UnsupportedOperationException(
            chainType,
            "OpenSea buy transaction - implementation pending"
        )
    }
    
    override suspend fun createListTransaction(
        contractAddress: String,
        tokenId: String,
        price: String,
        sellerAddress: String,
        chainType: MultiChainType
    ): String {
        // TODO: 實際的列售交易建構
        throw BlockchainException.UnsupportedOperationException(
            chainType,
            "OpenSea list transaction - implementation pending"
        )
    }
    
    override suspend fun getTrendingCollections(
        chainType: MultiChainType,
        timeframe: NFTTimeframe,
        limit: Int
    ): List<NFTCollection> {
        // TODO: 實際的熱門收藏查詢
        return emptyList()
    }
    
    override suspend fun getNFTTransactionHistory(
        contractAddress: String,
        tokenId: String?,
        chainType: MultiChainType,
        pagination: Pagination
    ): List<NFTTransaction> {
        // TODO: 實際的交易歷史查詢
        return emptyList()
    }
}

/**
 * Magic Eden 市場整合實現 (Solana)
 */
class MagicEdenMarketplace(
    private val logger: Logger = Logger.withTag("MagicEdenMarketplace")
) : CrossChainNFTMarketplace {
    
    override val marketplaceName = "Magic Eden"
    
    override val supportedChains = listOf(MultiChainType.SOLANA)
    
    override suspend fun searchNFTs(query: NFTSearchQuery): NFTSearchResult {
        logger.d("Searching NFTs on Magic Eden: ${query.keyword}")
        
        if (query.chainType != null && query.chainType !in supportedChains) {
            throw BlockchainException.UnsupportedOperationException(
                query.chainType,
                "$marketplaceName only supports Solana network"
            )
        }
        
        // TODO: 實際的 Magic Eden API 調用
        return NFTSearchResult(
            items = emptyList(),
            totalCount = 0,
            pagination = query.pagination
        )
    }
    
    override suspend fun getNFTDetails(
        contractAddress: String,
        tokenId: String,
        chainType: MultiChainType
    ): NFTDetails {
        validateChainSupport(chainType)
        
        // TODO: 實際的 Magic Eden API 調用
        throw BlockchainException.UnsupportedOperationException(
            chainType,
            "Magic Eden NFT details - implementation pending"
        )
    }
    
    override suspend fun getNFTPrice(
        contractAddress: String,
        tokenId: String,
        chainType: MultiChainType
    ): NFTPrice {
        validateChainSupport(chainType)
        
        // TODO: 實際的價格查詢
        throw BlockchainException.UnsupportedOperationException(
            chainType,
            "Magic Eden NFT pricing - implementation pending"
        )
    }
    
    override suspend fun getUserNFTs(
        walletAddress: String,
        chainType: MultiChainType,
        pagination: Pagination
    ): NFTCollection {
        validateChainSupport(chainType)
        
        // TODO: 實際的用戶 NFT 查詢
        throw BlockchainException.UnsupportedOperationException(
            chainType,
            "Magic Eden user NFTs - implementation pending"
        )
    }
    
    override suspend fun createBuyTransaction(
        contractAddress: String,
        tokenId: String,
        buyerAddress: String,
        chainType: MultiChainType
    ): String {
        validateChainSupport(chainType)
        
        // TODO: 實際的購買交易建構
        throw BlockchainException.UnsupportedOperationException(
            chainType,
            "Magic Eden buy transaction - implementation pending"
        )
    }
    
    override suspend fun createListTransaction(
        contractAddress: String,
        tokenId: String,
        price: String,
        sellerAddress: String,
        chainType: MultiChainType
    ): String {
        validateChainSupport(chainType)
        
        // TODO: 實際的列售交易建構
        throw BlockchainException.UnsupportedOperationException(
            chainType,
            "Magic Eden list transaction - implementation pending"
        )
    }
    
    override suspend fun getTrendingCollections(
        chainType: MultiChainType,
        timeframe: NFTTimeframe,
        limit: Int
    ): List<NFTCollection> {
        validateChainSupport(chainType)
        
        // TODO: 實際的熱門收藏查詢
        return emptyList()
    }
    
    override suspend fun getNFTTransactionHistory(
        contractAddress: String,
        tokenId: String?,
        chainType: MultiChainType,
        pagination: Pagination
    ): List<NFTTransaction> {
        validateChainSupport(chainType)
        
        // TODO: 實際的交易歷史查詢
        return emptyList()
    }
    
    private fun validateChainSupport(chainType: MultiChainType) {
        if (chainType !in supportedChains) {
            throw BlockchainException.UnsupportedOperationException(
                chainType,
                "$marketplaceName only supports Solana network"
            )
        }
    }
}

/**
 * NFT 市場聚合器
 * 整合多個 NFT 市場，提供統一的查詢和交易介面
 */
class NFTMarketplaceAggregator(
    private val marketplaces: List<CrossChainNFTMarketplace>,
    private val logger: Logger = Logger.withTag("NFTMarketplaceAggregator")
) {
    
    /**
     * 跨市場搜尋 NFT
     */
    suspend fun searchNFTs(query: NFTSearchQuery): AggregatedNFTSearchResult {
        logger.i("Searching NFTs across ${marketplaces.size} marketplaces")
        
        val supportedMarketplaces = marketplaces.filter { marketplace ->
            query.chainType == null || query.chainType in marketplace.supportedChains
        }
        
        if (supportedMarketplaces.isEmpty()) {
            throw BlockchainException.UnsupportedOperationException(
                query.chainType ?: MultiChainType.ETHEREUM,
                "No marketplace supports the specified chain"
            )
        }
        
        val marketplaceResults = mutableMapOf<String, NFTSearchResult>()
        var totalItems = 0
        
        // 並行查詢所有市場
        supportedMarketplaces.forEach { marketplace ->
            try {
                val result = marketplace.searchNFTs(query)
                marketplaceResults[marketplace.marketplaceName] = result
                totalItems += result.items.size
                
                logger.d("${marketplace.marketplaceName}: ${result.items.size} items found")
            } catch (e: Exception) {
                logger.w("Failed to search ${marketplace.marketplaceName}", e)
            }
        }
        
        // 合併和排序結果
        val allItems = marketplaceResults.values.flatMap { it.items }
        val sortedItems = sortNFTItems(allItems, query.sortBy)
        
        return AggregatedNFTSearchResult(
            items = sortedItems,
            totalCount = totalItems,
            marketplaceResults = marketplaceResults,
            pagination = query.pagination
        )
    }
    
    /**
     * 取得 NFT 跨市場價格比較
     */
    suspend fun getNFTPriceComparison(
        contractAddress: String,
        tokenId: String,
        chainType: MultiChainType
    ): NFTPriceComparison {
        logger.d("Getting price comparison for $contractAddress/$tokenId")
        
        val supportedMarketplaces = marketplaces.filter { 
            chainType in it.supportedChains 
        }
        
        val prices = mutableMapOf<String, NFTPrice>()
        
        supportedMarketplaces.forEach { marketplace ->
            try {
                val price = marketplace.getNFTPrice(contractAddress, tokenId, chainType)
                prices[marketplace.marketplaceName] = price
            } catch (e: Exception) {
                logger.w("Failed to get price from ${marketplace.marketplaceName}", e)
            }
        }
        
        return NFTPriceComparison(
            contractAddress = contractAddress,
            tokenId = tokenId,
            chainType = chainType,
            prices = prices,
            bestPrice = findBestPrice(prices),
            priceSpread = calculatePriceSpread(prices)
        )
    }
    
    /**
     * 取得用戶的全部 NFT 收藏
     */
    suspend fun getUserAllNFTs(
        walletAddress: String,
        chainType: MultiChainType
    ): AggregatedNFTCollection {
        logger.d("Getting user NFTs from all marketplaces")
        
        val supportedMarketplaces = marketplaces.filter { 
            chainType in it.supportedChains 
        }
        
        val collections = mutableMapOf<String, NFTCollection>()
        val allItems = mutableListOf<NFTItem>()
        
        supportedMarketplaces.forEach { marketplace ->
            try {
                val collection = marketplace.getUserNFTs(walletAddress, chainType)
                collections[marketplace.marketplaceName] = collection
                allItems.addAll(collection.items)
            } catch (e: Exception) {
                logger.w("Failed to get NFTs from ${marketplace.marketplaceName}", e)
            }
        }
        
        return AggregatedNFTCollection(
            walletAddress = walletAddress,
            chainType = chainType,
            totalItems = allItems.size,
            items = allItems.distinctBy { "${it.contractAddress}:${it.tokenId}" },
            marketplaceCollections = collections
        )
    }
    
    // 私有輔助方法
    
    private fun sortNFTItems(items: List<NFTItem>, sortBy: NFTSortBy): List<NFTItem> {
        return when (sortBy) {
            NFTSortBy.PRICE_LOW_TO_HIGH -> items.sortedBy { 
                it.price?.usdValue?.toDoubleOrNull() ?: Double.MAX_VALUE 
            }
            NFTSortBy.PRICE_HIGH_TO_LOW -> items.sortedByDescending { 
                it.price?.usdValue?.toDoubleOrNull() ?: 0.0 
            }
            NFTSortBy.RELEVANCE -> items // 保持原有順序
            else -> items
        }
    }
    
    private fun findBestPrice(prices: Map<String, NFTPrice>): Pair<String, NFTPrice>? {
        return prices.minByOrNull { 
            it.value.usdValue?.toDoubleOrNull() ?: Double.MAX_VALUE 
        }?.let { it.key to it.value }
    }
    
    private fun calculatePriceSpread(prices: Map<String, NFTPrice>): Double {
        if (prices.size < 2) return 0.0
        
        val usdPrices = prices.values.mapNotNull { 
            it.usdValue?.toDoubleOrNull() 
        }
        
        if (usdPrices.isEmpty()) return 0.0
        
        val min = usdPrices.minOrNull() ?: 0.0
        val max = usdPrices.maxOrNull() ?: 0.0
        
        return if (min > 0) (max - min) / min else 0.0
    }
}

/**
 * 聚合 NFT 搜尋結果
 */
data class AggregatedNFTSearchResult(
    val items: List<NFTItem>,
    val totalCount: Int,
    val marketplaceResults: Map<String, NFTSearchResult>,
    val pagination: Pagination
) {
    /**
     * 取得最佳價格的 NFT
     */
    fun getBestDeals(): List<NFTItem> {
        return items.filter { it.price != null }
            .sortedBy { it.price?.usdValue?.toDoubleOrNull() ?: Double.MAX_VALUE }
    }
    
    /**
     * 按市場分組
     */
    fun groupByMarketplace(): Map<String, List<NFTItem>> {
        return marketplaceResults.mapValues { it.value.items }
    }
}

/**
 * NFT 價格比較結果
 */
data class NFTPriceComparison(
    val contractAddress: String,
    val tokenId: String,
    val chainType: MultiChainType,
    val prices: Map<String, NFTPrice>, // 市場名稱 -> 價格
    val bestPrice: Pair<String, NFTPrice>?, // 最佳價格的市場和價格
    val priceSpread: Double // 價格差距百分比
) {
    /**
     * 取得格式化的價格差距
     */
    fun getFormattedPriceSpread(): String {
        return "${(priceSpread * 100).toInt()}%"
    }
    
    /**
     * 取得潛在節省金額
     */
    fun getPotentialSavings(): String? {
        if (prices.size < 2) return null
        
        val usdPrices = prices.values.mapNotNull { it.usdValue?.toDoubleOrNull() }
        if (usdPrices.isEmpty()) return null
        
        val min = usdPrices.minOrNull() ?: 0.0
        val max = usdPrices.maxOrNull() ?: 0.0
        
        return (max - min).toString()
    }
}

/**
 * 聚合 NFT 收藏
 */
data class AggregatedNFTCollection(
    val walletAddress: String,
    val chainType: MultiChainType,
    val totalItems: Int,
    val items: List<NFTItem>,
    val marketplaceCollections: Map<String, NFTCollection>
) {
    /**
     * 取得總價值估算
     */
    fun getTotalValue(): String {
        val totalUsdValue = items.mapNotNull { 
            it.price?.usdValue?.toDoubleOrNull() 
        }.sum()
        
        return totalUsdValue.toString()
    }
    
    /**
     * 按收藏分組
     */
    fun groupByCollection(): Map<String, List<NFTItem>> {
        return items.groupBy { it.collectionName ?: "Unknown" }
    }
}

/**
 * NFT 市場聚合器工廠
 */
object NFTMarketplaceAggregatorFactory {
    
    /**
     * 創建預設的 NFT 市場聚合器
     */
    fun createDefaultAggregator(): NFTMarketplaceAggregator {
        val marketplaces = listOf(
            OpenSeaMarketplace(),
            MagicEdenMarketplace()
            // 可以添加更多市場：
            // LooksRare(),
            // X2Y2(),
            // Foundation(),
            // SuperRare()
        )
        
        return NFTMarketplaceAggregator(marketplaces)
    }
    
    /**
     * 創建特定鏈的 NFT 市場聚合器
     */
    fun createChainSpecificAggregator(chainType: MultiChainType): NFTMarketplaceAggregator {
        val marketplaces = when (chainType) {
            MultiChainType.ETHEREUM -> listOf(
                OpenSeaMarketplace()
                // LooksRare(),
                // X2Y2()
            )
            MultiChainType.SOLANA -> listOf(
                MagicEdenMarketplace()
                // Solanart(),
                // DigitalEyes()
            )
            else -> emptyList()
        }
        
        return NFTMarketplaceAggregator(marketplaces)
    }
}
package com.cbstudio.wearwallet.core.domain.model.nft

import com.cbstudio.wearwallet.core.domain.model.ChainType
import kotlinx.serialization.Serializable

/**
 * NFT 代幣領域模型
 */
@Serializable
data class NftToken(
    val id: String,
    val tokenId: String,
    val contractAddress: String,
    val walletAddress: String,
    val chainType: ChainType,
    val chainId: Int,
    val name: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
    val metadataUrl: String? = null,
    val attributes: List<NftAttribute> = emptyList(),
    val collectionName: String = "",
    val creatorAddress: String? = null,
    val ownerAddress: String = "",
    val isFavorite: Boolean = false,
    val isHidden: Boolean = false,
    val isWatchFace: Boolean = false,
    val rarity: NftRarity? = null,
    val pricing: NftPricing? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val syncedAt: Long? = null
) {
    /**
     * 是否為有效的 NFT（有基本資訊）
     */
    val isValid: Boolean
        get() = tokenId.isNotBlank() && contractAddress.isNotBlank()
    
    /**
     * 是否有圖片
     */
    val hasImage: Boolean
        get() = !imageUrl.isNullOrBlank()
    
    /**
     * 是否有元數據
     */
    val hasMetadata: Boolean
        get() = !metadataUrl.isNullOrBlank()
    
    /**
     * 是否有稀有度資訊
     */
    val hasRarity: Boolean
        get() = rarity != null && rarity.rank != null
    
    /**
     * 是否有價格資訊
     */
    val hasPricing: Boolean
        get() = pricing != null && (pricing.ethPrice != null || pricing.usdPrice != null)
    
    /**
     * 顯示用的名稱（優先使用 name，否則使用 tokenId）
     */
    val displayName: String
        get() = name?.takeIf { it.isNotBlank() } ?: "NFT #$tokenId"
    
    /**
     * 顯示用的收藏集名稱
     */
    val displayCollectionName: String
        get() = collectionName?.takeIf { it.isNotBlank() } ?: "Unknown Collection"
}

/**
 * NFT 屬性
 */
/**
 * NFT 稀有度資訊
 */
@Serializable
data class NftRarity(
    val rank: Int? = null,
    val score: Double? = null,
    val total: Int? = null
) {
    /**
     * 稀有度百分比
     */
    val percentage: Double?
        get() = if (rank != null && total != null && total > 0) {
            ((total - rank + 1).toDouble() / total) * 100
        } else null
    
    /**
     * 稀有度等級
     */
    val level: RarityLevel
        get() = when {
            rank == null || total == null -> RarityLevel.UNKNOWN
            else -> {
                val pct = percentage ?: return RarityLevel.UNKNOWN
                when {
                    pct >= 95.0 -> RarityLevel.LEGENDARY
                    pct >= 85.0 -> RarityLevel.EPIC
                    pct >= 70.0 -> RarityLevel.RARE
                    pct >= 50.0 -> RarityLevel.UNCOMMON
                    else -> RarityLevel.COMMON
                }
            }
        }
}

/**
 * NFT 價格資訊
 */
@Serializable
data class NftPricing(
    val ethPrice: Double? = null,
    val usdPrice: Double? = null,
    val lastSalePrice: Double? = null,
    val lastSaleDate: Long? = null,
    val floorPrice: Double? = null,
    val currency: String = "ETH"
)

/**
 * 稀有度等級
 */
enum class RarityLevel {
    UNKNOWN,
    COMMON,
    UNCOMMON,
    RARE,
    EPIC,
    LEGENDARY
}

/**
 * NFT 篩選條件
 */
@Serializable
data class NftFilter(
    val walletAddress: String? = null,
    val chainType: ChainType? = null,
    val contractAddress: String? = null,
    val collectionName: String? = null,
    val isFavorite: Boolean? = null,
    val isHidden: Boolean? = null,
    val hasPrice: Boolean? = null,
    val hasRarity: Boolean? = null,
    val minPrice: Double? = null,
    val maxPrice: Double? = null,
    val searchQuery: String? = null,
    val sortBy: NftSortBy = NftSortBy.CREATED_DATE,
    val sortOrder: SortOrder = SortOrder.DESC,
    val limit: Int? = null
)

/**
 * NFT 排序方式
 */
enum class NftSortBy {
    CREATED_DATE,
    UPDATED_DATE,
    NAME,
    COLLECTION,
    PRICE_USD,
    PRICE_ETH,
    RARITY_RANK,
    RARITY_SCORE
}

/**
 * 排序順序
 */
enum class SortOrder {
    ASC,
    DESC
}

/**
 * NFT 統計資訊
 */
@Serializable
data class NftStatistics(
    val totalNfts: Int,
    val favoriteNfts: Int,
    val hiddenNfts: Int,
    val nftsByChain: Map<ChainType, Int>,
    val nftsByCollection: Map<String, Int>,
    val totalValueUsd: Double,
    val averageValueUsd: Double,
    val mostValuableNft: NftToken?,
    val topCollections: List<CollectionSummary>,
    val rarityDistribution: Map<RarityLevel, Int>
)

/**
 * 收藏集摘要
 */
@Serializable
data class CollectionSummary(
    val name: String,
    val nftCount: Int,
    val totalValue: Double,
    val averagePrice: Double,
    val floorPrice: Double?,
    val topNft: NftToken?
)
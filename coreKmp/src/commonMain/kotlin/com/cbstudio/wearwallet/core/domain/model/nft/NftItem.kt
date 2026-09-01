package com.cbstudio.wearwallet.core.domain.model.nft

import com.cbstudio.wearwallet.core.domain.model.ChainType
import kotlinx.serialization.Serializable

/**
 * NFT 項目資料模型
 */
@Serializable
data class NftItem(
    /**
     * 合約地址
     */
    val contractAddress: String,
    
    /**
     * Token ID
     */
    val tokenId: String,
    
    /**
     * NFT 名稱
     */
    val name: String,
    
    /**
     * NFT 描述
     */
    val description: String? = null,
    
    /**
     * 圖像 URL
     */
    val imageUrl: String,
    
    /**
     * 縮略圖 URL（優化後的小尺寸版本）
     */
    val thumbnailUrl: String? = null,
    
    /**
     * 收藏品系列名稱
     */
    val collectionName: String,
    
    /**
     * 區塊鏈網路
     */
    val blockchain: ChainType = ChainType.ETHEREUM,
    
    /**
     * 最後更新時間戳
     */
    val lastUpdated: Long = 0L,
    
    /**
     * 元數據 URI
     */
    val metadataUri: String? = null,
    
    /**
     * 當前估值（USD）
     */
    val estimatedValueUsd: Double? = null,
    
    /**
     * 是否為動畫 NFT
     */
    val isAnimated: Boolean = false,
    
    /**
     * 動畫 URL（如果適用）
     */
    val animationUrl: String? = null,
    
    /**
     * 擁有者地址
     */
    val ownerAddress: String? = null,
    
    /**
     * 是否為收藏
     */
    val isFavorite: Boolean = false,
    
    /**
     * NFT 標準（ERC-721, ERC-1155 等）
     */
    val standard: String? = null,
    
    /**
     * 屬性列表
     */
    val attributes: List<NftAttribute> = emptyList()
) {
    /**
     * 獲取最適合錶盤顯示的圖像 URL
     */
    fun getWatchFaceImageUrl(): String {
        return thumbnailUrl?.takeIf { it.isNotEmpty() } ?: imageUrl
    }
    
    /**
     * 獲取唯一標識符
     */
    fun getUniqueId(): String {
        return "${contractAddress}_${tokenId}"
    }
    
    /**
     * 格式化顯示名稱
     */
    fun getDisplayName(): String {
        return if (name.isNotEmpty()) name else "#$tokenId"
    }
    
    /**
     * 檢查是否有動畫內容
     */
    fun hasAnimation(): Boolean {
        return isAnimated && !animationUrl.isNullOrEmpty()
    }
    
    /**
     * 獲取預覽圖 URL（優先縮略圖）
     */
    fun getPreviewUrl(): String {
        return thumbnailUrl ?: imageUrl
    }
}

/**
 * NFT 屬性
 */
@Serializable
data class NftAttribute(
    val traitType: String,
    val value: String,
    val displayType: String? = null,
    val maxValue: Double? = null
)

/**
 * NFT 收藏品系列資訊
 */
@Serializable
data class NftCollection(
    val contractAddress: String,
    val name: String,
    val symbol: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
    val blockchain: ChainType = ChainType.ETHEREUM,
    val totalSupply: Long? = null,
    val floorPrice: Double? = null,
    val verified: Boolean = false,
    val ownerCount: Int? = null,
    val volumeTotal: Double? = null,
    val volume24h: Double? = null,
    val marketCap: Double? = null
)

/**
 * NFT Metadata
 */
@Serializable
data class NftMetadata(
    val name: String,
    val description: String? = null,
    val image: String,
    val externalUrl: String? = null,
    val backgroundColor: String? = null,
    val animationUrl: String? = null,
    val attributes: List<NftAttribute> = emptyList()
)
package com.cbstudio.wearwallet.core.domain.model.nft

import kotlinx.serialization.Serializable

/**
 * NFT Complication 設定資料模型
 */
@Serializable
data class NftComplicationSettings(
    /**
     * 是否啟用 NFT Complication
     */
    val isEnabled: Boolean = false,
    
    /**
     * 選中的 NFT 合約地址
     */
    val selectedNftContract: String = "",
    
    /**
     * 選中的 NFT Token ID
     */
    val selectedNftTokenId: String = "",
    
    /**
     * NFT 顯示模式
     */
    val displayMode: NftDisplayMode = NftDisplayMode.IMAGE_ONLY,
    
    /**
     * 圖像更新間隔（秒）
     */
    val updateIntervalSeconds: Int = 3600, // 1 hour
    
    /**
     * 是否允許自動輪換收藏的 NFT
     */
    val autoRotateEnabled: Boolean = false,
    
    /**
     * 自動輪換間隔（秒）
     */
    val rotateIntervalSeconds: Int = 86400, // 24 hours
    
    /**
     * 收藏的 NFT 列表（用於輪換）
     */
    val favoriteNfts: List<NftItem> = emptyList(),
    
    /**
     * 是否顯示 NFT 價值
     */
    val showValue: Boolean = false,
    
    /**
     * 是否顯示稀有度
     */
    val showRarity: Boolean = false,
    
    /**
     * 是否啟用動畫（如果 NFT 支援）
     */
    val enableAnimation: Boolean = true
)
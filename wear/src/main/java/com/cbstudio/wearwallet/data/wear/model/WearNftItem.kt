package com.cbstudio.wearwallet.data.wear.model

/**
 * Wear OS NFT 項目模型
 * 
 * 簡化的 NFT 數據模型，專為手錶端優化
 */
data class WearNftItem(
    val contractAddress: String,
    val tokenId: String,
    val name: String,
    val description: String = "",
    val imageUrl: String,
    val thumbnailUrl: String,
    val collectionName: String,
    val blockchain: String,
    val lastUpdated: Long,
    val isAnimated: Boolean = false,
    val animationUrl: String = ""
) {
    fun getUniqueId(): String = "${contractAddress.lowercase()}:$tokenId"
}

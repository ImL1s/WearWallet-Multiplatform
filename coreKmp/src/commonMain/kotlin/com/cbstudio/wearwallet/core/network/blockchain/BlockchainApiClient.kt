package com.cbstudio.wearwallet.core.network.blockchain

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.nft.NftToken
import com.cbstudio.wearwallet.core.domain.model.nft.NftPricing

/**
 * 區塊鏈 API 客戶端介面
 * ULTRATHINK Phase 12 - 完整實現區塊鏈網路查詢
 * 使用 Bridge Data 模式與多個區塊鏈網路互動
 */
interface BlockchainApiClient {
    
    /**
     * 從區塊鏈查詢錢包的所有 NFT
     * @param walletAddress 錢包地址
     * @param chainType 區塊鏈類型
     * @param includeMetadata 是否包含元數據
     * @param includePricing 是否包含價格信息
     * @return NFT 列表
     */
    suspend fun fetchNftsByWallet(
        walletAddress: String,
        chainType: ChainType,
        includeMetadata: Boolean = true,
        includePricing: Boolean = false
    ): Result<List<NftToken>>
    
    /**
     * 獲取 NFT 元數據
     * @param contractAddress 合約地址
     * @param tokenId Token ID
     * @param chainType 區塊鏈類型
     * @return NFT 元數據
     */
    suspend fun fetchNftMetadata(
        contractAddress: String,
        tokenId: String,
        chainType: ChainType
    ): Result<NftMetadata>
    
    /**
     * 批量獲取 NFT 價格信息
     * @param nftIds NFT ID 列表
     * @param currency 貨幣類型 (USD, ETH, etc.)
     * @return NFT ID 到價格的映射
     */
    suspend fun fetchNftPricing(
        nftIds: List<String>,
        currency: String = "USD"
    ): Result<Map<String, NftPricing>>
    
    /**
     * 驗證 NFT 所有權
     * @param contractAddress 合約地址
     * @param tokenId Token ID
     * @param walletAddress 錢包地址
     * @param chainType 區塊鏈類型
     * @return 是否擁有該 NFT
     */
    suspend fun verifyNftOwnership(
        contractAddress: String,
        tokenId: String,
        walletAddress: String,
        chainType: ChainType
    ): Result<Boolean>
    
    /**
     * 快取 NFT 圖片到本地
     * @param imageUrl 圖片 URL
     * @param nftId NFT ID
     * @return 本地快取路徑
     */
    suspend fun cacheNftImage(
        imageUrl: String,
        nftId: String
    ): Result<String>
    
    /**
     * 清理快取的 NFT 圖片
     * @param nftId NFT ID
     * @return 是否成功
     */
    suspend fun clearCachedImage(
        nftId: String
    ): Result<Unit>
    
    /**
     * 同步用戶偏好設置到雲端
     * @param walletAddress 錢包地址
     * @param nftId NFT ID
     * @param preference 偏好設置
     * @return 是否成功
     */
    suspend fun syncUserPreference(
        walletAddress: String,
        nftId: String,
        preference: Map<String, Any>
    ): Result<Unit>
    
    /**
     * 獲取支援的區塊鏈列表
     * @return 支援的區塊鏈類型列表
     */
    fun getSupportedChains(): List<ChainType>
    
    /**
     * 檢查 API 連接狀態
     * @return 是否連接成功
     */
    suspend fun checkConnection(): Result<Boolean>
}

/**
 * NFT 元數據擴展資訊
 */
data class NftMetadata(
    val name: String,
    val description: String?,
    val imageUrl: String,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val animationUrl: String? = null,
    val externalUrl: String? = null,
    val backgroundColor: String? = null,
    val attributes: List<NftAttribute> = emptyList(),
    val traits: Map<String, Any> = emptyMap()
)

/**
 * NFT 屬性
 */
data class NftAttribute(
    val traitType: String,
    val value: Any,
    val displayType: String? = null,
    val maxValue: Number? = null
)
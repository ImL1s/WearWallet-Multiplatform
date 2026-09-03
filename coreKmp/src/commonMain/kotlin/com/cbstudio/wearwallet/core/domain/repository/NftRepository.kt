package com.cbstudio.wearwallet.core.domain.repository

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.nft.*
import kotlinx.coroutines.flow.Flow

/**
 * NFT 資料庫操作介面
 * 提供 NFT 的增刪改查功能
 */
interface NftRepository {
    
    // ========== 基本 CRUD 操作 ==========
    
    /**
     * 創建 NFT 記錄
     */
    suspend fun createNft(nft: NftToken): Result<NftToken>
    
    /**
     * 根據 ID 獲取 NFT
     */
    suspend fun getNft(id: String): Result<NftToken?>
    
    /**
     * 獲取所有 NFT
     */
    suspend fun getAllNfts(): Result<List<NftToken>>
    
    /**
     * 觀察所有 NFT 變化
     */
    fun observeAllNfts(): Flow<List<NftToken>>
    
    /**
     * 更新 NFT 資料
     */
    suspend fun updateNft(nft: NftToken): Result<NftToken>
    
    /**
     * 根據 ID 刪除 NFT
     */
    suspend fun deleteNft(id: String): Result<Unit>
    
    /**
     * 批量刪除 NFT
     */
    suspend fun deleteNfts(ids: List<String>): Result<Unit>
    
    // ========== 查詢操作 ==========
    
    /**
     * 根據錢包地址獲取 NFT
     */
    suspend fun getNftsByWalletAddress(walletAddress: String): Result<List<NftToken>>
    
    /**
     * 根據合約地址獲取 NFT
     */
    suspend fun getNftsByContractAddress(contractAddress: String): Result<List<NftToken>>
    
    /**
     * 根據鏈類型獲取 NFT
     */
    suspend fun getNftsByChainType(chainType: ChainType): Result<List<NftToken>>
    
    /**
     * 根據收藏集名稱獲取 NFT
     */
    suspend fun getNftsByCollection(collectionName: String): Result<List<NftToken>>
    
    /**
     * 獲取收藏的 NFT
     */
    suspend fun getFavoriteNfts(): Result<List<NftToken>>
    
    /**
     * 獲取可見的 NFT（未隱藏）
     */
    suspend fun getVisibleNfts(): Result<List<NftToken>>
    
    /**
     * 搜尋 NFT
     */
    suspend fun searchNfts(query: String): Result<List<NftToken>>
    
    /**
     * 根據篩選條件獲取 NFT
     */
    suspend fun getNftsWithFilter(filter: NftFilter): Result<List<NftToken>>
    
    /**
     * 根據稀有度排序獲取 NFT
     */
    suspend fun getNftsByRarity(limit: Int = 20): Result<List<NftToken>>
    
    /**
     * 根據價值排序獲取 NFT
     */
    suspend fun getNftsByValue(limit: Int = 20): Result<List<NftToken>>
    
    // ========== 狀態更新 ==========
    
    /**
     * 更新收藏狀態
     */
    suspend fun updateFavoriteStatus(id: String, isFavorite: Boolean): Result<Unit>
    
    /**
     * 更新隱藏狀態
     */
    suspend fun updateHiddenStatus(id: String, isHidden: Boolean): Result<Unit>
    
    /**
     * 更新價格資訊
     */
    suspend fun updatePricing(id: String, pricing: NftPricing): Result<Unit>
    
    /**
     * 批量更新同步時間
     */
    suspend fun updateSyncTime(walletAddress: String, chainType: ChainType, syncTime: Long): Result<Unit>
    
    // ========== 驗證與檢查 ==========
    
    /**
     * 檢查 NFT 是否存在
     */
    suspend fun isNftExists(tokenId: String, contractAddress: String, chainType: ChainType): Result<Boolean>
    
    /**
     * 驗證 NFT 資料完整性
     */
    suspend fun validateNft(nft: NftToken): Result<Boolean>
    
    // ========== Watch Face 相關 ==========
    
    /**
     * 獲取設置為 Watch Face 的 NFT
     */
    suspend fun getWatchFaceNfts(): Result<List<NftToken>>
    
    /**
     * 設置 Watch Face 狀態
     */
    suspend fun setWatchFaceStatus(id: String, isWatchFace: Boolean): Result<Unit>
    
    /**
     * 清除所有 Watch Face 狀態
     */
    suspend fun clearAllWatchFaceStatus(): Result<Unit>
    
    /**
     * 更新最後訪問時間
     */
    suspend fun updateLastAccessTime(nftId: String, timestamp: Long): Result<Unit>
    
    // ========== 組合查詢 ==========
    
    /**
     * 根據錢包地址和鏈類型獲取 NFT
     */
    suspend fun getNftsByWalletAndChain(walletAddress: String, chainType: ChainType): Result<List<NftToken>>
    
    /**
     * 根據錢包地址和收藏集獲取 NFT
     */
    suspend fun getNftsByWalletAndCollection(walletAddress: String, collectionName: String): Result<List<NftToken>>
    
    /**
     * 在特定錢包中搜尋 NFT
     */
    suspend fun searchNftsByWallet(walletAddress: String, query: String): Result<List<NftToken>>
    
    /**
     * 根據合約地址和 Token ID 獲取 NFT
     */
    suspend fun getNftByContractAndTokenId(contractAddress: String, tokenId: String): Result<NftToken?>
    
    // ========== 批量操作 ==========
    
    /**
     * 批量導入 NFT
     */
    suspend fun importNfts(nfts: List<NftToken>): Result<Int>
    
    /**
     * 匯出 NFT 為 JSON
     */
    suspend fun exportNftsAsJson(): Result<String>
    
    /**
     * 根據錢包地址清理 NFT
     */
    suspend fun clearNftsByWalletAddress(walletAddress: String): Result<Unit>
    
    /**
     * 根據合約地址清理 NFT
     */
    suspend fun clearNftsByContractAddress(contractAddress: String): Result<Unit>
    
    /**
     * 清空所有 NFT
     */
    suspend fun clearAllNfts(): Result<Unit>
    
    // ========== 統計與分析 ==========
    
    /**
     * 獲取 NFT 統計資訊
     */
    suspend fun getStatistics(): Result<NftStatistics>
    
    /**
     * 獲取收藏集統計
     */
    suspend fun getCollectionStatistics(): Result<List<CollectionSummary>>
    
    /**
     * 獲取鏈統計
     */
    suspend fun getChainStatistics(): Result<Map<ChainType, Int>>
    
    // ========== 同步與更新 ==========
    
    /**
     * 檢查需要同步的 NFT（超過指定時間未同步）
     */
    suspend fun getNftsNeedingSync(maxAgeMs: Long): Result<List<NftToken>>
    
    /**
     * 標記 NFT 為已同步
     */
    suspend fun markAsSynced(ids: List<String>): Result<Unit>
    
    // ========== 額外搜索方法 (For NftItem) ==========
    
    /**
     * 搜尋 NFT 並返回 NftItem 列表
     */
    suspend fun searchNftsAsItems(query: String): List<NftItem>
    
    /**
     * 根據鏈類型搜尋 NFT
     */
    suspend fun searchNftsByChain(query: String, chainType: ChainType): List<NftItem>
    
    /**
     * 根據地址獲取 NFT
     */
    suspend fun getNftsByAddress(address: String): List<NftItem>
    
    /**
     * 根據地址和鏈類型獲取 NFT
     */
    suspend fun getNftsByAddressAndChain(address: String, chainType: ChainType): List<NftItem>
    
    /**
     * 根據收藏集地址獲取 NFT (返回 NftItem)
     */
    suspend fun getNftsByCollectionAsItems(collectionAddress: String): List<NftItem>
    
    // ========== Watch Face Complication 設定 ==========
    
    /**
     * 獲取 NFT Complication 設定
     */
    suspend fun getNftComplicationSettings(): NftComplicationSettings
    
    /**
     * 保存 NFT Complication 設定
     */
    suspend fun saveNftComplicationSettings(settings: NftComplicationSettings): Result<Unit>
    
    /**
     * 獲取用戶的 NFT 收藏列表
     */
    suspend fun getUserNftCollection(): List<NftItem>
}
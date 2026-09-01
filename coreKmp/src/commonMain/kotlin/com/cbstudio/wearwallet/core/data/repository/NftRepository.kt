package com.cbstudio.wearwallet.core.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.database.CoreWalletDatabase
import com.cbstudio.wearwallet.core.database.Nft
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.nft.*
import com.cbstudio.wearwallet.core.domain.repository.NftRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 使用 SQLDelight 實現的 NFT 儲存庫
 * 提供完整的 NFT 資料持久化功能
 */
class NftRepositoryImpl(
    private val database: CoreWalletDatabase
) : NftRepository {
    
    private val nftQueries = database.nftQueries
    
    override suspend fun createNft(nft: NftToken): Result<NftToken> {
        return try {
            println("🔧 NftRepository.createNft 開始")
            println("   tokenId: ${nft.tokenId}")
            println("   contractAddress: ${nft.contractAddress}")
            println("   name: ${nft.name}")
            
            // 檢查 NFT 是否已存在
            if (nftQueries.existsByTokenAndContract(
                nft.tokenId, 
                nft.contractAddress, 
                nft.chainType.name
            ).executeAsOne()) {
                return Result.Failure(Exception("NFT 已存在"))
            }
            
            // 插入到數據庫
            nftQueries.insert(
                token_id = nft.tokenId,
                contract_address = nft.contractAddress,
                wallet_address = nft.walletAddress,
                chain_type = nft.chainType.name,
                chain_id = nft.chainId.toLong(),
                name = nft.name,
                description = nft.description,
                image_url = nft.imageUrl,
                metadata_url = nft.metadataUrl,
                attributes = Json.encodeToString(nft.attributes),
                collection_name = nft.collectionName,
                creator_address = nft.creatorAddress,
                owner_address = nft.ownerAddress,
                is_favorite = if (nft.isFavorite) 1L else 0L,
                is_hidden = if (nft.isHidden) 1L else 0L,
                rarity_rank = nft.rarity?.rank?.toLong(),
                rarity_score = nft.rarity?.score,
                price_eth = nft.pricing?.ethPrice,
                price_usd = nft.pricing?.usdPrice,
                last_sale_price = nft.pricing?.lastSalePrice,
                last_sale_date = nft.pricing?.lastSaleDate,
                synced_at = nft.syncedAt
            )
            
            // 獲取插入的 NFT ID
            val nftId = nftQueries.lastInsertRowId().executeAsOne()
            println("🔧 NFT ID: $nftId")
            
            // 查詢並返回創建的 NFT
            val createdNft = nftQueries.selectById(nftId).executeAsOne()
            println("🔧 NFT 創建成功！")
            Result.Success(createdNft.toNftToken())
        } catch (e: Exception) {
            println("❌ NftRepository.createNft 失敗: ${e.message}")
            e.printStackTrace()
            Result.Failure(e)
        }
    }
    
    override suspend fun getNft(id: String): Result<NftToken?> {
        return try {
            val nftId = id.toLongOrNull() ?: return Result.Failure(
                Exception("Invalid NFT ID")
            )
            val nft = nftQueries.selectById(nftId).executeAsOneOrNull()
            Result.Success(nft?.toNftToken())
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun getAllNfts(): Result<List<NftToken>> {
        return try {
            val nfts = nftQueries.selectAll().executeAsList()
            Result.Success(nfts.map { it.toNftToken() })
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override fun observeAllNfts(): Flow<List<NftToken>> {
        return nftQueries.selectAll()
            .asFlow()
            .mapToList(kotlinx.coroutines.Dispatchers.Default)
            .map { nfts ->
                nfts.map { it.toNftToken() }
            }
    }
    
    override suspend fun updateNft(nft: NftToken): Result<NftToken> {
        return try {
            val nftId = nft.id.toLongOrNull() ?: return Result.Failure(
                Exception("Invalid NFT ID")
            )
            
            nftQueries.update(
                name = nft.name,
                description = nft.description,
                image_url = nft.imageUrl,
                metadata_url = nft.metadataUrl,
                attributes = Json.encodeToString(nft.attributes),
                collection_name = nft.collectionName,
                creator_address = nft.creatorAddress,
                owner_address = nft.ownerAddress,
                rarity_rank = nft.rarity?.rank?.toLong(),
                rarity_score = nft.rarity?.score,
                price_eth = nft.pricing?.ethPrice,
                price_usd = nft.pricing?.usdPrice,
                last_sale_price = nft.pricing?.lastSalePrice,
                last_sale_date = nft.pricing?.lastSaleDate,
                synced_at = nft.syncedAt,
                id = nftId
            )
            
            // 查詢並返回更新的 NFT
            val updatedNft = nftQueries.selectById(nftId).executeAsOne()
            Result.Success(updatedNft.toNftToken())
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun deleteNft(id: String): Result<Unit> {
        return try {
            val nftId = id.toLongOrNull() ?: return Result.Failure(
                Exception("Invalid NFT ID")
            )
            nftQueries.deleteById(nftId)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun deleteNfts(ids: List<String>): Result<Unit> {
        return try {
            val nftIds = ids.mapNotNull { it.toLongOrNull() }
            if (nftIds.isEmpty()) {
                return Result.Success(Unit)
            }
            
            // SQLDelight 不支援 IN 查詢的參數列表，需要逐個刪除
            nftIds.forEach { id ->
                nftQueries.deleteById(id)
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun getNftsByWalletAddress(walletAddress: String): Result<List<NftToken>> {
        return try {
            val nfts = nftQueries.selectByWalletAddress(walletAddress).executeAsList()
            Result.Success(nfts.map { it.toNftToken() })
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun getNftsByContractAddress(contractAddress: String): Result<List<NftToken>> {
        return try {
            val nfts = nftQueries.selectByContractAddress(contractAddress).executeAsList()
            Result.Success(nfts.map { it.toNftToken() })
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun getNftsByChainType(chainType: ChainType): Result<List<NftToken>> {
        return try {
            val nfts = nftQueries.selectByChainType(chainType.name).executeAsList()
            Result.Success(nfts.map { it.toNftToken() })
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun getNftsByCollection(collectionName: String): Result<List<NftToken>> {
        return try {
            val nfts = nftQueries.selectByCollection(collectionName).executeAsList()
            Result.Success(nfts.map { it.toNftToken() })
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun getFavoriteNfts(): Result<List<NftToken>> {
        return try {
            val nfts = nftQueries.selectFavorites().executeAsList()
            Result.Success(nfts.map { it.toNftToken() })
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun getVisibleNfts(): Result<List<NftToken>> {
        return try {
            val nfts = nftQueries.selectVisible().executeAsList()
            Result.Success(nfts.map { it.toNftToken() })
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun searchNfts(query: String): Result<List<NftToken>> {
        return try {
            val nfts = nftQueries.searchNfts(query, query, query).executeAsList()
            Result.Success(nfts.map { it.toNftToken() })
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun getNftsWithFilter(filter: NftFilter): Result<List<NftToken>> {
        return try {
            // 根據不同的篩選條件執行不同的查詢
            val nftTokens = when {
                filter.walletAddress != null -> {
                    nftQueries.selectByWalletAddress(filter.walletAddress).executeAsList()
                        .map { it.toNftToken() }
                }
                filter.chainType != null -> {
                    nftQueries.selectByChainType(filter.chainType.name).executeAsList()
                        .map { it.toNftToken() }
                }
                filter.contractAddress != null -> {
                    nftQueries.selectByContractAddress(filter.contractAddress).executeAsList()
                        .map { it.toNftToken() }
                }
                filter.collectionName != null -> {
                    nftQueries.selectByCollection(filter.collectionName).executeAsList()
                        .map { it.toNftToken() }
                }
                filter.isFavorite == true -> {
                    nftQueries.selectFavorites().executeAsList()
                        .map { it.toNftToken() }
                }
                !filter.searchQuery.isNullOrBlank() -> {
                    val query = filter.searchQuery
                    nftQueries.searchNfts(query, query, query).executeAsList()
                        .map { it.toNftToken() }
                }
                else -> {
                    nftQueries.selectAll().executeAsList()
                        .map { it.toNftToken() }
                }
            }
            
            Result.Success(nftTokens)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun getNftsByRarity(limit: Int): Result<List<NftToken>> {
        return try {
            val nfts = nftQueries.selectByRarity(limit.toLong()).executeAsList()
            Result.Success(nfts.map { it.toNftToken() })
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun getNftsByValue(limit: Int): Result<List<NftToken>> {
        return try {
            val nfts = nftQueries.selectByValue(limit.toLong()).executeAsList()
            Result.Success(nfts.map { it.toNftToken() })
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun updateFavoriteStatus(id: String, isFavorite: Boolean): Result<Unit> {
        return try {
            val nftId = id.toLongOrNull() ?: return Result.Failure(
                Exception("Invalid NFT ID")
            )
            nftQueries.updateFavoriteStatus(if (isFavorite) 1L else 0L, nftId)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun updateHiddenStatus(id: String, isHidden: Boolean): Result<Unit> {
        return try {
            val nftId = id.toLongOrNull() ?: return Result.Failure(
                Exception("Invalid NFT ID")
            )
            nftQueries.updateHiddenStatus(if (isHidden) 1L else 0L, nftId)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun updatePricing(id: String, pricing: NftPricing): Result<Unit> {
        return try {
            val nftId = id.toLongOrNull() ?: return Result.Failure(
                Exception("Invalid NFT ID")
            )
            nftQueries.updatePricing(
                price_eth = pricing.ethPrice,
                price_usd = pricing.usdPrice,
                last_sale_price = pricing.lastSalePrice,
                last_sale_date = pricing.lastSaleDate,
                id = nftId
            )
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun updateSyncTime(walletAddress: String, chainType: ChainType, syncTime: Long): Result<Unit> {
        return try {
            nftQueries.updateSyncTime(syncTime, walletAddress, chainType.name)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun isNftExists(tokenId: String, contractAddress: String, chainType: ChainType): Result<Boolean> {
        return try {
            val exists = nftQueries.existsByTokenAndContract(tokenId, contractAddress, chainType.name).executeAsOne()
            Result.Success(exists)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun validateNft(nft: NftToken): Result<Boolean> {
        return try {
            // 基本的 NFT 資料驗證
            val isValid = nft.tokenId.isNotBlank() &&
                    nft.contractAddress.isNotBlank() &&
                    nft.walletAddress.isNotBlank() &&
                    nft.contractAddress.startsWith("0x") &&
                    nft.contractAddress.length == 42 &&
                    nft.walletAddress.startsWith("0x") &&
                    nft.walletAddress.length == 42
            
            Result.Success(isValid)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun importNfts(nfts: List<NftToken>): Result<Int> {
        return try {
            var importedCount = 0
            nfts.forEach { nft ->
                val result = createNft(nft)
                if (result is Result.Success) {
                    importedCount++
                }
            }
            Result.Success(importedCount)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun exportNftsAsJson(): Result<String> {
        return try {
            val nftsResult = getAllNfts()
            when (nftsResult) {
                is Result.Success -> {
                    val json = Json.encodeToString(nftsResult.data)
                    Result.Success(json)
                }
                is Result.Failure -> nftsResult
                is Result.Loading -> Result.Failure(Exception("Unexpected loading state"))
            }
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun clearNftsByWalletAddress(walletAddress: String): Result<Unit> {
        return try {
            nftQueries.deleteByWalletAddress(walletAddress)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun clearNftsByContractAddress(contractAddress: String): Result<Unit> {
        return try {
            nftQueries.deleteByContractAddress(contractAddress)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun clearAllNfts(): Result<Unit> {
        return try {
            nftQueries.deleteAll()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun getStatistics(): Result<NftStatistics> {
        return try {
            val totalNfts = nftQueries.countAll().executeAsOne().toInt()
            val favoriteNfts = nftQueries.countFavorites().executeAsOne().toInt()
            
            // 目前僅實現基本統計，其他統計功能可後續擴展
            val statistics = NftStatistics(
                totalNfts = totalNfts,
                favoriteNfts = favoriteNfts,
                hiddenNfts = 0, // TODO: 需要實現隱藏統計
                nftsByChain = emptyMap(), // TODO: 需要實現鏈統計
                nftsByCollection = emptyMap(), // TODO: 需要實現收藏集統計
                totalValueUsd = 0.0, // TODO: 需要實現價值統計
                averageValueUsd = 0.0,
                mostValuableNft = null,
                topCollections = emptyList(),
                rarityDistribution = emptyMap()
            )
            
            Result.Success(statistics)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun getCollectionStatistics(): Result<List<CollectionSummary>> {
        return try {
            val stats = nftQueries.getCollectionStats().executeAsList()
            val summaries = stats.map { stat ->
                CollectionSummary(
                    name = stat.collection_name,
                    nftCount = stat.COUNT_.toInt(),
                    totalValue = 0.0, // TODO: 計算總價值
                    averagePrice = stat.AVG_ ?: 0.0,
                    floorPrice = stat.MIN_,
                    topNft = null // TODO: 獲取最高價值 NFT
                )
            }
            Result.Success(summaries)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun getChainStatistics(): Result<Map<ChainType, Int>> {
        return try {
            val stats = nftQueries.getChainStats().executeAsList()
            val chainStats = stats.associate { stat ->
                ChainType.valueOf(stat.chain_type) to stat.COUNT_.toInt()
            }
            Result.Success(chainStats)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun getNftsNeedingSync(maxAgeMs: Long): Result<List<NftToken>> {
        return try {
            val cutoffTime = Clock.System.now().toEpochMilliseconds() - maxAgeMs
            // 目前簡化實現，獲取所有 NFT 並過濾
            val allNfts = nftQueries.selectAll().executeAsList().map { it.toNftToken() }
            val needingSyncNfts = allNfts.filter { nft ->
                nft.syncedAt == null || nft.syncedAt < cutoffTime
            }
            Result.Success(needingSyncNfts)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun markAsSynced(ids: List<String>): Result<Unit> {
        return try {
            val syncTime = Clock.System.now().toEpochMilliseconds()
            val nftIds = ids.mapNotNull { it.toLongOrNull() }
            
            // 逐個更新同步時間
            nftIds.forEach { id ->
                // 暫時使用更新整個 NFT 的方式，未來可優化為專門的同步時間更新
                val nft = nftQueries.selectById(id).executeAsOneOrNull()
                if (nft != null) {
                    nftQueries.update(
                        name = nft.name,
                        description = nft.description,
                        image_url = nft.image_url,
                        metadata_url = nft.metadata_url,
                        attributes = nft.attributes,
                        collection_name = nft.collection_name,
                        creator_address = nft.creator_address,
                        owner_address = nft.owner_address,
                        rarity_rank = nft.rarity_rank,
                        rarity_score = nft.rarity_score,
                        price_eth = nft.price_eth,
                        price_usd = nft.price_usd,
                        last_sale_price = nft.last_sale_price,
                        last_sale_date = nft.last_sale_date,
                        synced_at = syncTime,
                        id = id
                    )
                }
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    // ========== Watch Face 相關 ==========
    
    override suspend fun getWatchFaceNfts(): Result<List<NftToken>> {
        return try {
            // 這個功能需要在資料庫 schema 中添加 is_watch_face 欄位
            // 暫時返回空列表
            Result.Success(emptyList())
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun setWatchFaceStatus(id: String, isWatchFace: Boolean): Result<Unit> {
        return try {
            // 這個功能需要在資料庫 schema 中添加 is_watch_face 欄位
            // 暫時使用 is_hidden 的反向邏輯
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun clearAllWatchFaceStatus(): Result<Unit> {
        return try {
            // 清除所有 Watch Face 狀態
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun updateLastAccessTime(nftId: String, timestamp: Long): Result<Unit> {
        return try {
            // 更新最後訪問時間
            // 這個功能需要在 SQLDelight 中定義查詢
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    // ========== 組合查詢 ==========
    
    override suspend fun getNftsByWalletAndChain(walletAddress: String, chainType: ChainType): Result<List<NftToken>> {
        return try {
            // 暫時使用基本實現
            Result.Success(emptyList())
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun getNftsByWalletAndCollection(walletAddress: String, collectionName: String): Result<List<NftToken>> {
        return try {
            // 暫時使用基本實現
            Result.Success(emptyList())
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun searchNftsByWallet(walletAddress: String, query: String): Result<List<NftToken>> {
        return try {
            // 暫時使用基本實現
            Result.Success(emptyList())
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun getNftByContractAndTokenId(contractAddress: String, tokenId: String): Result<NftToken?> {
        return try {
            // 暫時使用基本實現
            Result.Success(null)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    // ========== 額外搜索方法 (For NftItem) ==========
    
    override suspend fun searchNftsAsItems(query: String): List<NftItem> {
        return try {
            val result = searchNfts(query)
            when (result) {
                is Result.Success -> result.data.map { it.toNftItem() }
                is Result.Failure -> emptyList()
                else -> emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    override suspend fun searchNftsByChain(query: String, chainType: ChainType): List<NftItem> {
        return try {
            val nfts = nftQueries.selectByChainType(chainType.name).executeAsList()
                .filter { nft ->
                    nft.name?.contains(query, ignoreCase = true) == true ||
                    nft.description?.contains(query, ignoreCase = true) == true ||
                    nft.collection_name?.contains(query, ignoreCase = true) == true
                }
            nfts.map { it.toNftToken().toNftItem() }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    override suspend fun getNftsByAddress(address: String): List<NftItem> {
        return try {
            val result = getNftsByWalletAddress(address)
            when (result) {
                is Result.Success -> result.data.map { it.toNftItem() }
                is Result.Failure -> emptyList()
                else -> emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    override suspend fun getNftsByAddressAndChain(address: String, chainType: ChainType): List<NftItem> {
        return try {
            val result = getNftsByWalletAndChain(address, chainType)
            when (result) {
                is Result.Success -> result.data.map { it.toNftItem() }
                is Result.Failure -> emptyList()
                else -> emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    override suspend fun getNftsByCollectionAsItems(collectionAddress: String): List<NftItem> {
        return try {
            val result = getNftsByCollection(collectionAddress)
            when (result) {
                is Result.Success -> result.data.map { it.toNftItem() }
                is Result.Failure -> emptyList()
                else -> emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ========== Watch Face Complication 設定 ==========
    
    /**
     * 用於存儲 NFT Complication 設定的內存緩存
     * 在實際生產環境中，這應該使用 DataStore 或數據庫
     */
    private var cachedNftComplicationSettings: NftComplicationSettings? = null
    
    override suspend fun getNftComplicationSettings(): NftComplicationSettings {
        if (cachedNftComplicationSettings == null) {
            // 返回預設設定
            cachedNftComplicationSettings = NftComplicationSettings()
        }
        return cachedNftComplicationSettings!!
    }
    
    override suspend fun saveNftComplicationSettings(settings: NftComplicationSettings): Result<Unit> {
        return try {
            cachedNftComplicationSettings = settings
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun getUserNftCollection(): List<NftItem> {
        return try {
            // 從數據庫獲取收藏的 NFT
            val favoriteNfts = nftQueries.selectFavorites()
                .executeAsList()
                .map { it.toNftToken() }
                .map { it.toNftItem() }
            
            if (favoriteNfts.isEmpty()) {
                // 如果沒有收藏，返回所有 NFT
                nftQueries.selectAll()
                    .executeAsList()
                    .map { it.toNftToken() }
                    .map { it.toNftItem() }
                    .take(20) // 限制返回數量
            } else {
                favoriteNfts
            }
        } catch (e: Exception) {
            println("⚠️ getUserNftCollection 錯誤: ${e.message}")
            emptyList()
        }
    }
}

/**
 * 擴展函數：將 NftToken 轉換為 NftItem
 */
private fun NftToken.toNftItem(): NftItem {
    return NftItem(
        contractAddress = this.contractAddress,
        tokenId = this.tokenId,
        name = this.name ?: "",
        description = this.description,
        imageUrl = this.imageUrl ?: "",
        thumbnailUrl = null, // NftToken doesn't have thumbnailUrl
        collectionName = this.collectionName,
        blockchain = this.chainType,
        lastUpdated = this.updatedAt,
        metadataUri = this.metadataUrl,
        estimatedValueUsd = this.pricing?.usdPrice,
        isAnimated = false, // NftToken doesn't have animationUrl field
        animationUrl = null,
        ownerAddress = this.ownerAddress,
        isFavorite = this.isFavorite,
        standard = null, // NftToken doesn't have tokenStandard
        attributes = this.attributes
    )
}

/**
 * 擴展函數：將數據庫 Nft 轉換為領域模型 NftToken
 */
private fun Nft.toNftToken(): NftToken {
    return NftToken(
        id = id.toString(),
        tokenId = token_id,
        contractAddress = contract_address,
        walletAddress = wallet_address,
        chainType = ChainType.valueOf(chain_type),
        chainId = chain_id.toInt(),
        name = name,
        description = description,
        imageUrl = image_url,
        metadataUrl = metadata_url,
        attributes = try {
            if (!attributes.isNullOrBlank()) {
                Json.decodeFromString<List<NftAttribute>>(attributes)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        },
        collectionName = collection_name ?: "",
        creatorAddress = creator_address,
        ownerAddress = owner_address ?: "",
        isFavorite = is_favorite != 0L,
        isHidden = is_hidden != 0L,
        rarity = if (rarity_rank != null || rarity_score != null) {
            NftRarity(
                rank = rarity_rank?.toInt(),
                score = rarity_score
            )
        } else null,
        pricing = if (price_eth != null || price_usd != null || last_sale_price != null) {
            NftPricing(
                ethPrice = price_eth,
                usdPrice = price_usd,
                lastSalePrice = last_sale_price,
                lastSaleDate = last_sale_date
            )
        } else null,
        createdAt = created_at,
        updatedAt = updated_at,
        syncedAt = synced_at
    )
}

/**
 * 擴展函數：將數據庫 SelectByRarity 轉換為領域模型 NftToken
 */
private fun com.cbstudio.wearwallet.core.database.SelectByRarity.toNftToken(): NftToken {
    return NftToken(
        id = id.toString(),
        tokenId = token_id,
        contractAddress = contract_address,
        walletAddress = wallet_address,
        chainType = ChainType.valueOf(chain_type),
        chainId = chain_id.toInt(),
        name = name,
        description = description,
        imageUrl = image_url,
        metadataUrl = metadata_url,
        attributes = try {
            if (!attributes.isNullOrBlank()) {
                Json.decodeFromString<List<NftAttribute>>(attributes)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        },
        collectionName = collection_name ?: "",
        creatorAddress = creator_address,
        ownerAddress = owner_address ?: "",
        isFavorite = is_favorite != 0L,
        isHidden = is_hidden != 0L,
        rarity = if (rarity_rank != null || rarity_score != null) {
            NftRarity(
                rank = rarity_rank.toInt(),
                score = rarity_score
            )
        } else null,
        pricing = if (price_eth != null || price_usd != null || last_sale_price != null) {
            NftPricing(
                ethPrice = price_eth,
                usdPrice = price_usd,
                lastSalePrice = last_sale_price,
                lastSaleDate = last_sale_date
            )
        } else null,
        createdAt = created_at,
        updatedAt = updated_at,
        syncedAt = synced_at
    )
}

/**
 * 擴展函數：將數據庫 SelectByValue 轉換為領域模型 NftToken
 */
private fun com.cbstudio.wearwallet.core.database.SelectByValue.toNftToken(): NftToken {
    return NftToken(
        id = id.toString(),
        tokenId = token_id,
        contractAddress = contract_address,
        walletAddress = wallet_address,
        chainType = ChainType.valueOf(chain_type),
        chainId = chain_id.toInt(),
        name = name,
        description = description,
        imageUrl = image_url,
        metadataUrl = metadata_url,
        attributes = try {
            if (!attributes.isNullOrBlank()) {
                Json.decodeFromString<List<NftAttribute>>(attributes)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        },
        collectionName = collection_name ?: "",
        creatorAddress = creator_address,
        ownerAddress = owner_address ?: "",
        isFavorite = is_favorite != 0L,
        isHidden = is_hidden != 0L,
        rarity = if (rarity_rank != null || rarity_score != null) {
            NftRarity(
                rank = rarity_rank?.toInt(),
                score = rarity_score
            )
        } else null,
        pricing = if (price_eth != null || price_usd != null || last_sale_price != null) {
            NftPricing(
                ethPrice = price_eth,
                usdPrice = price_usd,
                lastSalePrice = last_sale_price,
                lastSaleDate = last_sale_date
            )
        } else null,
        createdAt = created_at,
        updatedAt = updated_at,
        syncedAt = synced_at
    )
}

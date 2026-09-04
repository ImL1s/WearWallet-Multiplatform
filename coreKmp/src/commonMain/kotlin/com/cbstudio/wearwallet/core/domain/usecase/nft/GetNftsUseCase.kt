package com.cbstudio.wearwallet.core.domain.usecase.nft

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.nft.NftToken
import com.cbstudio.wearwallet.core.domain.model.nft.NftFilter
import com.cbstudio.wearwallet.core.domain.repository.NftRepository
import kotlinx.coroutines.flow.Flow

/**
 * 獲取 NFT 業務邏輯
 */
class GetNftsUseCase(
    private val nftRepository: NftRepository
) {
    /**
     * 獲取所有 NFT
     */
    suspend fun getAllNfts(): Result<List<NftToken>> {
        return nftRepository.getAllNfts()
    }
    
    /**
     * 觀察所有 NFT 變化
     */
    fun observeAllNfts(): Flow<List<NftToken>> {
        return nftRepository.observeAllNfts()
    }
    
    /**
     * 根據錢包地址獲取 NFT
     */
    suspend fun getNftsByWallet(walletAddress: String): Result<List<NftToken>> {
        if (walletAddress.isBlank()) {
            return Result.Failure(Exception("錢包地址不能為空"))
        }
        return nftRepository.getNftsByWalletAddress(walletAddress)
    }
    
    /**
     * 根據合約地址獲取 NFT
     */
    suspend fun getNftsByContract(contractAddress: String): Result<List<NftToken>> {
        if (contractAddress.isBlank()) {
            return Result.Failure(Exception("合約地址不能為空"))
        }
        return nftRepository.getNftsByContractAddress(contractAddress)
    }
    
    /**
     * 根據區塊鏈類型獲取 NFT
     */
    suspend fun getNftsByChain(chainType: ChainType): Result<List<NftToken>> {
        return nftRepository.getNftsByChainType(chainType)
    }
    
    /**
     * 根據收藏集獲取 NFT
     */
    suspend fun getNftsByCollection(collectionName: String): Result<List<NftToken>> {
        if (collectionName.isBlank()) {
            return Result.Failure(Exception("收藏集名稱不能為空"))
        }
        return nftRepository.getNftsByCollection(collectionName)
    }
    
    /**
     * 獲取收藏的 NFT
     */
    suspend fun getFavoriteNfts(): Result<List<NftToken>> {
        return nftRepository.getFavoriteNfts()
    }
    
    /**
     * 獲取可見的 NFT（未隱藏）
     */
    suspend fun getVisibleNfts(): Result<List<NftToken>> {
        return nftRepository.getVisibleNfts()
    }
    
    /**
     * 搜索 NFT
     */
    suspend fun searchNfts(query: String): Result<List<NftToken>> {
        if (query.isBlank()) {
            return getAllNfts()
        }
        return nftRepository.searchNfts(query)
    }
    
    /**
     * 根據過濾條件獲取 NFT
     */
    suspend fun getNftsWithFilter(filter: NftFilter): Result<List<NftToken>> {
        return nftRepository.getNftsWithFilter(filter)
    }
    
    /**
     * 根據稀有度排序獲取 NFT
     */
    suspend fun getNftsByRarity(limit: Int = 20): Result<List<NftToken>> {
        return nftRepository.getNftsByRarity(limit)
    }
    
    /**
     * 根據價值排序獲取 NFT
     */
    suspend fun getNftsByValue(limit: Int = 20): Result<List<NftToken>> {
        return nftRepository.getNftsByValue(limit)
    }
    
    /**
     * 根據ID獲取特定 NFT
     */
    suspend fun getNftById(id: String): Result<NftToken?> {
        if (id.isBlank()) {
            return Result.Failure(Exception("NFT ID 不能為空"))
        }
        return nftRepository.getNft(id)
    }
    
    /**
     * 檢查 NFT 是否存在
     */
    suspend fun isNftExists(tokenId: String, contractAddress: String, chainType: ChainType): Result<Boolean> {
        return nftRepository.isNftExists(tokenId, contractAddress, chainType)
    }
    
    /**
     * 獲取設置為 Watch Face 的 NFT
     * ULTRATHINK Phase 12 - 完整實現
     */
    suspend fun getWatchFaceNfts(): Result<List<NftToken>> {
        return nftRepository.getWatchFaceNfts()
    }
    
    /**
     * 根據錢包地址和區塊鏈類型獲取 NFT
     */
    suspend fun getNftsByChain(walletAddress: String, chainType: ChainType): Result<List<NftToken>> {
        if (walletAddress.isBlank()) {
            return Result.Failure(Exception("錢包地址不能為空"))
        }
        return nftRepository.getNftsByWalletAndChain(walletAddress, chainType)
    }
    
    /**
     * 根據錢包地址和收藏集名稱獲取 NFT
     */
    suspend fun getNftsByCollection(walletAddress: String, collectionName: String): Result<List<NftToken>> {
        if (walletAddress.isBlank()) {
            return Result.Failure(Exception("錢包地址不能為空"))
        }
        if (collectionName.isBlank()) {
            return Result.Failure(Exception("收藏集名稱不能為空"))
        }
        return nftRepository.getNftsByWalletAndCollection(walletAddress, collectionName)
    }
    
    /**
     * 在特定錢包中搜索 NFT
     */
    suspend fun searchNfts(walletAddress: String, query: String): Result<List<NftToken>> {
        if (walletAddress.isBlank()) {
            return Result.Failure(Exception("錢包地址不能為空"))
        }
        if (query.isBlank()) {
            return getNftsByWallet(walletAddress)
        }
        return nftRepository.searchNftsByWallet(walletAddress, query)
    }
}
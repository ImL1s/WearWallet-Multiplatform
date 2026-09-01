package com.cbstudio.wearwallet.core.domain.model.addressbook

import com.cbstudio.wearwallet.core.domain.model.ChainType
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

/**
 * 地址簿聯絡人領域模型
 */
@Serializable
data class AddressContact(
    val id: String,
    val name: String,
    val address: String,
    val chainType: ChainType,
    val chainId: Int,
    val category: ContactCategory = ContactCategory.OTHER,
    val tags: List<String> = emptyList(),
    val notes: String = "",
    val isFavorite: Boolean = false,
    val isVerified: Boolean = false,
    val usageCount: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val lastUsedAt: Long? = null
) {
    /**
     * 獲取顯示名稱（如果沒有名稱則顯示地址的簡寫）
     */
    val displayName: String
        get() = name.ifBlank { shortAddress }
    
    /**
     * 獲取簡短地址
     */
    val shortAddress: String
        get() = "${address.take(6)}...${address.takeLast(4)}"
    
    /**
     * 是否為常用聯絡人（使用次數 > 5）
     */
    val isFrequent: Boolean
        get() = usageCount > 5
    
    /**
     * 是否為最近使用（7天內）
     */
    val isRecent: Boolean
        get() = lastUsedAt?.let { 
            Clock.System.now().toEpochMilliseconds() - it < 7 * 24 * 60 * 60 * 1000 
        } ?: false
    
    /**
     * 獲取標籤字符串（用於數據庫存儲）
     */
    val tagsString: String
        get() = tags.joinToString(",")
    
    /**
     * 創建新的聯絡人，增加使用次數
     */
    fun incrementUsage(): AddressContact {
        val now = Clock.System.now().toEpochMilliseconds()
        return copy(
            usageCount = usageCount + 1,
            lastUsedAt = now,
            updatedAt = now
        )
    }
    
    /**
     * 切換收藏狀態
     */
    fun toggleFavorite(): AddressContact {
        return copy(
            isFavorite = !isFavorite,
            updatedAt = Clock.System.now().toEpochMilliseconds()
        )
    }
    
    companion object {
        /**
         * 從標籤字符串解析為標籤列表
         */
        fun parseTagsFromString(tagsString: String): List<String> {
            return if (tagsString.isBlank()) {
                emptyList()
            } else {
                tagsString.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            }
        }
        
        /**
         * 創建新的聯絡人
         */
        fun create(
            name: String,
            address: String,
            chainType: ChainType,
            chainId: Int = when(chainType) {
                ChainType.ETHEREUM -> 1
                ChainType.BSC -> 56
                ChainType.POLYGON -> 137
                ChainType.AVALANCHE -> 43114
                ChainType.ARBITRUM -> 42161
                ChainType.OPTIMISM -> 10
                ChainType.FANTOM -> 250
                ChainType.CRONOS -> 25
                ChainType.CRONOSZVM -> 390
                ChainType.BASE -> 8453
                ChainType.ZKSYNC -> 324
                ChainType.MOONBEAM -> 1284
                ChainType.GNOSIS -> 100
                ChainType.CELO -> 42220
                ChainType.LINEA -> 59144
                ChainType.SEPOLIA -> 11155111
                ChainType.GOERLI -> 5
                ChainType.MUMBAI -> 80001
                else -> 1
            },
            category: ContactCategory = ContactCategory.OTHER,
            tags: List<String> = emptyList(),
            notes: String = "",
            isFavorite: Boolean = false,
            isVerified: Boolean = false
        ): AddressContact {
            val now = Clock.System.now().toEpochMilliseconds()
            return AddressContact(
                id = "", // 將由數據庫生成
                name = name,
                address = address,
                chainType = chainType,
                chainId = chainId,
                category = category,
                tags = tags,
                notes = notes,
                isFavorite = isFavorite,
                isVerified = isVerified,
                usageCount = 0,
                createdAt = now,
                updatedAt = now,
                lastUsedAt = null
            )
        }
    }
}
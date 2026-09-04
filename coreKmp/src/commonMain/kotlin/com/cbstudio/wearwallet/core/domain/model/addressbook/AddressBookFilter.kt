package com.cbstudio.wearwallet.core.domain.model.addressbook

import com.cbstudio.wearwallet.core.domain.model.ChainType
import kotlinx.serialization.Serializable

/**
 * 地址簿篩選條件
 */
@Serializable
data class AddressBookFilter(
    val chainType: ChainType? = null,
    val category: ContactCategory? = null,
    val tag: String? = null,
    val isFavorite: Boolean? = null,
    val isVerified: Boolean? = null,
    val isFrequent: Boolean? = null, // 使用次數 > 5
    val isRecent: Boolean? = null,   // 7天內使用
    val searchQuery: String? = null,
    val sortBy: SortBy = SortBy.NAME_ASC,
    val limit: Int? = null
) {
    /**
     * 是否有任何篩選條件
     */
    val hasFilters: Boolean
        get() = chainType != null || 
                category != null || 
                tag != null || 
                isFavorite != null || 
                isVerified != null || 
                isFrequent != null || 
                isRecent != null || 
                !searchQuery.isNullOrBlank()
    
    /**
     * 清除所有篩選條件
     */
    fun clearAll(): AddressBookFilter {
        return AddressBookFilter(sortBy = sortBy)
    }
    
    companion object {
        /**
         * 建立收藏聯絡人篩選器
         */
        fun favorites(): AddressBookFilter {
            return AddressBookFilter(
                isFavorite = true,
                sortBy = SortBy.USAGE_COUNT_DESC
            )
        }
        
        /**
         * 建立最近使用篩選器
         */
        fun recent(limit: Int = 10): AddressBookFilter {
            return AddressBookFilter(
                isRecent = true,
                sortBy = SortBy.LAST_USED_DESC,
                limit = limit
            )
        }
        
        /**
         * 建立常用聯絡人篩選器
         */
        fun frequent(limit: Int = 10): AddressBookFilter {
            return AddressBookFilter(
                isFrequent = true,
                sortBy = SortBy.USAGE_COUNT_DESC,
                limit = limit
            )
        }
        
        /**
         * 建立特定鏈類型篩選器
         */
        fun byChain(chainType: ChainType): AddressBookFilter {
            return AddressBookFilter(
                chainType = chainType,
                sortBy = SortBy.NAME_ASC
            )
        }
        
        /**
         * 建立搜索篩選器
         */
        fun search(query: String): AddressBookFilter {
            return AddressBookFilter(
                searchQuery = query,
                sortBy = SortBy.RELEVANCE
            )
        }
    }
}

/**
 * 排序方式枚舉
 */
@Serializable
enum class SortBy(val displayName: String) {
    NAME_ASC("名稱 A-Z"),
    NAME_DESC("名稱 Z-A"),
    CREATED_ASC("創建時間↑"),
    CREATED_DESC("創建時間↓"),
    LAST_USED_ASC("使用時間↑"),
    LAST_USED_DESC("使用時間↓"),
    USAGE_COUNT_ASC("使用次數↑"),
    USAGE_COUNT_DESC("使用次數↓"),
    RELEVANCE("相關性"),
    FAVORITE_FIRST("收藏優先")
}
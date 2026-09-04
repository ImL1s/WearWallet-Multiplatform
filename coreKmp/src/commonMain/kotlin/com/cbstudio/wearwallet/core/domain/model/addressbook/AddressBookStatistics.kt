package com.cbstudio.wearwallet.core.domain.model.addressbook

import com.cbstudio.wearwallet.core.domain.model.ChainType
import kotlinx.serialization.Serializable

/**
 * 地址簿統計資訊
 */
@Serializable
data class AddressBookStatistics(
    val totalContacts: Int,
    val favoriteContacts: Int,
    val verifiedContacts: Int,
    val contactsByChain: Map<ChainType, Int>,
    val contactsByCategory: Map<ContactCategory, Int>,
    val totalUsageCount: Int,
    val averageUsageCount: Double,
    val mostUsedContact: AddressContact? = null,
    val recentlyAddedCount: Int, // 7天內新增
    val recentlyUsedCount: Int,  // 7天內使用
    val allTags: List<String>,
    val mostPopularTags: List<TagStatistic>
) {
    /**
     * 收藏比率
     */
    val favoriteRatio: Double
        get() = if (totalContacts > 0) favoriteContacts.toDouble() / totalContacts else 0.0
    
    /**
     * 驗證比率
     */
    val verificationRatio: Double
        get() = if (totalContacts > 0) verifiedContacts.toDouble() / totalContacts else 0.0
    
    /**
     * 最活躍的鏈類型
     */
    val mostActiveChain: ChainType?
        get() = contactsByChain.maxByOrNull { it.value }?.key
    
    /**
     * 最大的分類
     */
    val largestCategory: ContactCategory?
        get() = contactsByCategory.maxByOrNull { it.value }?.key
    
    companion object {
        /**
         * 空統計
         */
        fun empty(): AddressBookStatistics {
            return AddressBookStatistics(
                totalContacts = 0,
                favoriteContacts = 0,
                verifiedContacts = 0,
                contactsByChain = emptyMap(),
                contactsByCategory = emptyMap(),
                totalUsageCount = 0,
                averageUsageCount = 0.0,
                mostUsedContact = null,
                recentlyAddedCount = 0,
                recentlyUsedCount = 0,
                allTags = emptyList(),
                mostPopularTags = emptyList()
            )
        }
    }
}

/**
 * 標籤統計
 */
@Serializable
data class TagStatistic(
    val tag: String,
    val count: Int,
    val percentage: Double
) {
    companion object {
        /**
         * 從標籤使用情況創建統計
         */
        fun fromTagUsage(tagUsage: Map<String, Int>, totalContacts: Int): List<TagStatistic> {
            return tagUsage.map { (tag, count) ->
                TagStatistic(
                    tag = tag,
                    count = count,
                    percentage = if (totalContacts > 0) count.toDouble() / totalContacts * 100 else 0.0
                )
            }.sortedByDescending { it.count }
        }
    }
}
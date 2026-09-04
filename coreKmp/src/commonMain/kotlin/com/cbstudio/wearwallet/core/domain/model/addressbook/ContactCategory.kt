package com.cbstudio.wearwallet.core.domain.model.addressbook

import kotlinx.serialization.Serializable

/**
 * 聯絡人分類枚舉
 */
@Serializable
enum class ContactCategory(val displayName: String, val icon: String) {
    EXCHANGE("交易所", "🏦"),
    FRIEND("朋友", "👥"),
    FAMILY("家人", "👨‍👩‍👧‍👦"),
    DEFI("DeFi協議", "🔗"),
    DAO("DAO組織", "🏛️"),
    BUSINESS("商業", "💼"),
    CHARITY("慈善", "❤️"),
    NFT("NFT項目", "🎨"),
    GAMING("遊戲", "🎮"),
    OTHER("其他", "📁");
    
    companion object {
        /**
         * 從字符串獲取分類，如果不存在則返回 OTHER
         */
        fun fromString(value: String): ContactCategory {
            return values().find { it.name == value } ?: OTHER
        }
        
        /**
         * 獲取所有分類選項
         */
        fun getAllOptions(): List<ContactCategory> {
            return values().toList()
        }
    }
}
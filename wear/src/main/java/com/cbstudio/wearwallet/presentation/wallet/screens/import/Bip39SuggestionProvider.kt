package com.cbstudio.wearwallet.presentation.wallet.screens.import

import io.github.iml1s.crypto.BIP39_ENGLISH_WORDLIST

/**
 * BIP39 單詞自動完成建議提供者
 * 
 * 用於在助記詞輸入時提供單詞建議，提升用戶體驗
 */
object Bip39SuggestionProvider {
    
    /**
     * 獲取匹配前綴的 BIP39 單詞建議
     * 
     * @param prefix 用戶輸入的前綴
     * @param limit 最大返回數量，預設 5 個
     * @return 匹配的單詞列表
     */
    fun getSuggestions(prefix: String, limit: Int = 5): List<String> {
        if (prefix.length < 2) return emptyList()
        
        val lowerPrefix = prefix.lowercase().trim()
        return BIP39_ENGLISH_WORDLIST
            .filter { it.startsWith(lowerPrefix) }
            .take(limit)
    }
    
    /**
     * 檢查單詞是否為有效的 BIP39 單詞
     * 
     * @param word 要檢查的單詞
     * @return 是否有效
     */
    fun isValidWord(word: String): Boolean {
        return BIP39_ENGLISH_WORDLIST.contains(word.lowercase().trim())
    }
    
    /**
     * 獲取所有 BIP39 單詞數量
     */
    val wordCount: Int get() = BIP39_ENGLISH_WORDLIST.size
}

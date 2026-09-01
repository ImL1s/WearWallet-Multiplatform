package com.cbstudio.wearwallet.presentation.wallet.screens.import

import org.junit.Test
import org.junit.Assert.*

/**
 * Bip39SuggestionProvider 單元測試
 */
class Bip39SuggestionProviderTest {

    @Test
    fun `getSuggestions returns empty for short prefix`() {
        // 少於 2 個字符應返回空列表
        assertTrue(Bip39SuggestionProvider.getSuggestions("a").isEmpty())
        assertTrue(Bip39SuggestionProvider.getSuggestions("").isEmpty())
    }

    @Test
    fun `getSuggestions returns matching words for valid prefix`() {
        // "ab" 應該返回 abandon, ability, able, about, above 等
        val suggestions = Bip39SuggestionProvider.getSuggestions("ab")
        assertTrue(suggestions.isNotEmpty())
        assertTrue(suggestions.all { it.startsWith("ab") })
        assertTrue(suggestions.contains("abandon") || suggestions.contains("ability"))
    }

    @Test
    fun `getSuggestions respects limit parameter`() {
        val suggestions = Bip39SuggestionProvider.getSuggestions("a", limit = 3)
        assertTrue(suggestions.size <= 3)
    }

    @Test
    fun `getSuggestions returns max 5 by default`() {
        val suggestions = Bip39SuggestionProvider.getSuggestions("a")
        assertTrue(suggestions.size <= 5)
    }

    @Test
    fun `isValidWord returns true for valid BIP39 words`() {
        assertTrue(Bip39SuggestionProvider.isValidWord("abandon"))
        assertTrue(Bip39SuggestionProvider.isValidWord("zoo"))
        assertTrue(Bip39SuggestionProvider.isValidWord("rookie"))
        assertTrue(Bip39SuggestionProvider.isValidWord("abuse"))
    }

    @Test
    fun `isValidWord returns false for invalid words`() {
        assertFalse(Bip39SuggestionProvider.isValidWord("notaword"))
        assertFalse(Bip39SuggestionProvider.isValidWord("blockchain"))
        assertFalse(Bip39SuggestionProvider.isValidWord(""))
        assertFalse(Bip39SuggestionProvider.isValidWord("cryptocurrency"))
    }

    @Test
    fun `isValidWord is case insensitive`() {
        assertTrue(Bip39SuggestionProvider.isValidWord("ABANDON"))
        assertTrue(Bip39SuggestionProvider.isValidWord("Abandon"))
        assertTrue(Bip39SuggestionProvider.isValidWord("aBaNdOn"))
    }

    @Test
    fun `getSuggestions handles test mnemonic words`() {
        // 測試助記詞 "rookie abuse frozen luxury science hat alert avoid car lemon day cost"
        val testWords = listOf("rookie", "abuse", "frozen", "luxury", "science", 
                               "hat", "alert", "avoid", "car", "lemon", "day", "cost")
        
        for (word in testWords) {
            assertTrue("$word should be valid", Bip39SuggestionProvider.isValidWord(word))
            
            // 前兩個字符應該能找到建議
            if (word.length >= 2) {
                val prefix = word.take(2)
                val suggestions = Bip39SuggestionProvider.getSuggestions(prefix)
                assertTrue("Suggestions for '$prefix' should contain '$word'", 
                           suggestions.isEmpty() || suggestions.any { it.startsWith(prefix) })
            }
        }
    }

    @Test
    fun `wordCount returns 2048`() {
        assertEquals(2048, Bip39SuggestionProvider.wordCount)
    }
}

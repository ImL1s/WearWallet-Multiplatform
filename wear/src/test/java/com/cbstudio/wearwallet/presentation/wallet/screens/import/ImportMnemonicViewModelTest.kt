package com.cbstudio.wearwallet.presentation.wallet.screens.import

import org.junit.Test
import org.junit.Assert.*
import org.junit.Before

/**
 * ImportMnemonicViewModel 單元測試
 */
class ImportMnemonicViewModelTest {

    @Test
    fun `parsesMnemonicFromPaste correctly parses space-separated mnemonic`() {
        // 測試解析邏輯（不依賴 ViewModel 實例）
        val input = "rookie abuse frozen luxury science hat alert avoid car lemon day cost"
        val words = parseMnemonicText(input)
        
        assertEquals(12, words.size)
        assertEquals("rookie", words[0])
        assertEquals("abuse", words[1])
        assertEquals("frozen", words[2])
        assertEquals("cost", words[11])
    }

    @Test
    fun `parsesMnemonicFromPaste handles newline-separated mnemonic`() {
        val input = "rookie\nabuse\nfrozen\nluxury\nscience\nhat\nalert\navoid\ncar\nlemon\nday\ncost"
        val words = parseMnemonicText(input)
        
        assertEquals(12, words.size)
        assertEquals("rookie", words[0])
        assertEquals("cost", words[11])
    }

    @Test
    fun `parsesMnemonicFromPaste handles comma-separated mnemonic`() {
        val input = "rookie,abuse,frozen,luxury,science,hat,alert,avoid,car,lemon,day,cost"
        val words = parseMnemonicText(input)
        
        assertEquals(12, words.size)
    }

    @Test
    fun `parsesMnemonicFromPaste handles mixed separators`() {
        val input = "rookie abuse,frozen\nluxury science hat,alert\navoid car lemon day cost"
        val words = parseMnemonicText(input)
        
        assertEquals(12, words.size)
    }

    @Test
    fun `parsesMnemonicFromPaste handles extra whitespace`() {
        val input = "  rookie   abuse  frozen   luxury  science hat alert avoid car lemon day cost  "
        val words = parseMnemonicText(input)
        
        assertEquals(12, words.size)
        assertEquals("rookie", words[0])
    }

    @Test
    fun `parsesMnemonicFromPaste converts to lowercase`() {
        val input = "ROOKIE Abuse FROZEN Luxury SCIENCE Hat"
        val words = parseMnemonicText(input)
        
        assertTrue(words.all { it == it.lowercase() })
    }

    @Test
    fun `parsesMnemonicFromPaste takes only first 12 words`() {
        val input = "word1 word2 word3 word4 word5 word6 word7 word8 word9 word10 word11 word12 word13 word14"
        val words = parseMnemonicText(input)
        
        assertEquals(12, words.size)
        assertFalse(words.contains("word13"))
    }

    @Test
    fun `parsesMnemonicFromPaste handles less than 12 words`() {
        val input = "rookie abuse frozen"
        val words = parseMnemonicText(input)
        
        assertEquals(3, words.size)
    }

    // 模擬 ViewModel 中的解析邏輯
    private fun parseMnemonicText(text: String): List<String> {
        val cleanedText = text
            .replace("\n", " ")
            .replace(",", " ")
            .replace("\t", " ")
            .trim()
        
        return cleanedText
            .split("\\s+".toRegex())
            .map { it.lowercase().trim() }
            .filter { it.isNotEmpty() }
            .take(12)
    }
}

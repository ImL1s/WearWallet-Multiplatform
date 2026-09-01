package com.cbstudio.wearwallet.core.multichain.crypto

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class MoneroSimpleTest {
    
    @Test
    fun testMoneroAddressFormat() {
        // Test that Stagenet addresses start with '5'
        val stageNetAddress = "5BXyLwTmWfyuQQX7SjRVMeNHpchYgnTaxdRQNvuNfkKRc2pWUhP9NU8deLafqQJMsnBG3MKjJAhBVNpGCTG3JknMEgv7KJb"
        assertTrue(stageNetAddress.startsWith("5"), "Stagenet address should start with '5'")
        assertEquals(95, stageNetAddress.length, "Monero address should be 95 characters")
    }
    
    @Test
    fun testMnemonicWordCount() {
        val bip39 = "rookie abuse frozen luxury science hat alert avoid car lemon day cost"
        val xmr25 = "emotion adopt stockpile tumbling myth software talent python coal much lion nobody tomorrow goblet habitat items tyrant pairing roster itches giddy ledge gigantic gleeful lion"
        
        assertEquals(12, bip39.split(" ").size, "BIP39 should have 12 words")
        assertEquals(25, xmr25.split(" ").size, "XMR25 should have 25 words")
    }
}
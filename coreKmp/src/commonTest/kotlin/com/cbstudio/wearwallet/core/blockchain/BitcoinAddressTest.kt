package com.cbstudio.wearwallet.core.blockchain

import com.cbstudio.wearwallet.core.blockchain.adapter.BitcoinPlatformAdapter
import com.cbstudio.wearwallet.core.domain.model.Network
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Bitcoin 地址相關測試
 */
class BitcoinAddressTest {
    
    @Test
    fun testAddressValidation() {
        val adapter = BitcoinPlatformAdapter(Network.BITCOIN_TESTNET)
        
        // 測試有效的測試網地址
        assertTrue(adapter.validateAddress("mipcBbFg9gMiCh81Kj8tqqdgoZub1ZJRfn"))
        assertTrue(adapter.validateAddress("2MzQwSSnBHWHqSAqtTVQ6v47XtaisrJa1Vc"))
        assertTrue(adapter.validateAddress("tb1qw508d6qejxtdg4y5r3zarvary0c5xw7kxpjzsx"))
        
        // 測試無效地址
        assertTrue(!adapter.validateAddress("invalid_address"))
        assertTrue(!adapter.validateAddress(""))
        
        // 測試主網地址在測試網應該無效
        assertTrue(!adapter.validateAddress("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa"))
        assertTrue(!adapter.validateAddress("bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4"))
    }
    
    @Test
    fun testNetworkSpecificValidation() {
        // 測試網適配器
        val testnetAdapter = BitcoinPlatformAdapter(Network.BITCOIN_TESTNET)
        
        // 測試網地址應該有效
        assertTrue(testnetAdapter.validateAddress("mipcBbFg9gMiCh81Kj8tqqdgoZub1ZJRfn"))
        assertTrue(testnetAdapter.validateAddress("tb1qw508d6qejxtdg4y5r3zarvary0c5xw7kxpjzsx"))
        
        // 主網適配器
        val mainnetAdapter = BitcoinPlatformAdapter(Network.BITCOIN_MAINNET)
        
        // 主網地址應該有效
        assertTrue(mainnetAdapter.validateAddress("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa"))
        assertTrue(mainnetAdapter.validateAddress("bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4"))
        
        // 測試網地址在主網應該無效
        assertTrue(!mainnetAdapter.validateAddress("mipcBbFg9gMiCh81Kj8tqqdgoZub1ZJRfn"))
        assertTrue(!mainnetAdapter.validateAddress("tb1qw508d6qejxtdg4y5r3zarvary0c5xw7kxpjzsx"))
    }
}
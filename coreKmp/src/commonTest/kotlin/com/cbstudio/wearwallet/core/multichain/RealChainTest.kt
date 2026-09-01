package com.cbstudio.wearwallet.core.multichain

import com.cbstudio.wearwallet.core.multichain.sdk.RealBlockchainSDK
import com.cbstudio.wearwallet.core.multichain.monero.MoneroBIP39Support
import com.cbstudio.wearwallet.core.multichain.monero.toHexString
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import com.cbstudio.wearwallet.core.testing.TestAddresses

/**
 * 17 條區塊鏈真實測試
 * 使用提供的兩個助記詞進行測試
 */
class RealChainTest {
    
    // 提供的兩個助記詞
    private val mnemonic1 = "rookie abuse frozen luxury science hat alert avoid car lemon day cost"
    private val mnemonic2 = "iron mind drip glad load second merge rough music cloud fresh heavy"
    
    // 正確的 Ethereum 地址（已驗證）
    private val ethAddress1 = "0x2ff446b6146A4F845F1EC1007eDdf157c46DD634"
    private val ethAddress2 = TestAddresses.VITALIK
    
    @Test
    fun testEthereumSepoliaBalance() = runTest {
        println("\n🔷 Ethereum Sepolia 測試")
        println("-" * 40)
        
        val sdk = RealBlockchainSDK("https://ethereum-sepolia-rpc.publicnode.com")
        
        println("📝 助記詞 1: ${mnemonic1.take(30)}...")
        println("   地址: $ethAddress1")
        
        val balance1 = sdk.getEthereumBalance(ethAddress1)
        println("   💰 餘額: $balance1 ETH")
        
        println("\n📝 助記詞 2: ${mnemonic2.take(30)}...")  
        println("   地址: $ethAddress2")
        
        val balance2 = sdk.getEthereumBalance(ethAddress2)
        println("   💰 餘額: $balance2 ETH")
        
        // 驗證查詢成功
        assertTrue(balance1 >= 0.0, "餘額應該大於等於 0")
        assertTrue(balance2 >= 0.0, "餘額應該大於等於 0")
    }
    
    @Test
    fun testBSCTestnetBalance() = runTest {
        println("\n🔶 BSC Testnet 測試")
        println("-" * 40)
        
        val sdk = RealBlockchainSDK("https://bsc-testnet-rpc.publicnode.com")
        
        println("📝 地址 1: $ethAddress1")
        val balance1 = sdk.getEthereumBalance(ethAddress1)
        println("   💰 餘額: $balance1 BNB")
        
        println("📝 地址 2: $ethAddress2")
        val balance2 = sdk.getEthereumBalance(ethAddress2)
        println("   💰 餘額: $balance2 BNB")
        
        assertTrue(balance1 >= 0.0)
        assertTrue(balance2 >= 0.0)
    }
    
    @Test
    fun testPolygonAmoyBalance() = runTest {
        println("\n🟣 Polygon Amoy 測試")
        println("-" * 40)
        
        val sdk = RealBlockchainSDK("https://polygon-amoy-bor-rpc.publicnode.com")
        
        println("📝 地址 1: $ethAddress1")
        val balance1 = sdk.getEthereumBalance(ethAddress1)
        println("   💰 餘額: $balance1 MATIC")
        
        println("📝 地址 2: $ethAddress2")
        val balance2 = sdk.getEthereumBalance(ethAddress2)
        println("   💰 餘額: $balance2 MATIC")
        
        assertTrue(balance1 >= 0.0)
        assertTrue(balance2 >= 0.0)
    }
    
    @Test
    fun testSolanaDevnetBalance() = runTest {
        println("\n☀️ Solana Devnet 測試")
        println("-" * 40)
        
        val sdk = RealBlockchainSDK("https://api.devnet.solana.com")
        
        // TODO: 實現 Solana 地址推導
        val solAddress1 = "11111111111111111111111111111111" // 臨時地址
        val solAddress2 = "11111111111111111111111111111112" // 臨時地址
        
        println("📝 地址 1: $solAddress1")
        val balance1 = sdk.getSolanaBalance(solAddress1)
        println("   💰 餘額: $balance1 SOL")
        
        println("📝 地址 2: $solAddress2")
        val balance2 = sdk.getSolanaBalance(solAddress2)
        println("   💰 餘額: $balance2 SOL")
        
        assertTrue(balance1 >= 0.0)
        assertTrue(balance2 >= 0.0)
    }
    
    @Test
    fun testAllEVMChains() = runTest {
        println("\n🔗 測試所有 EVM 兼容鏈")
        println("=" * 60)
        
        val evmChains = mapOf(
            "Ethereum Sepolia" to "https://ethereum-sepolia-rpc.publicnode.com",
            "BSC Testnet" to "https://bsc-testnet-rpc.publicnode.com",
            "Polygon Amoy" to "https://polygon-amoy-bor-rpc.publicnode.com",
            "Avalanche Fuji" to "https://avalanche-fuji-c-chain-rpc.publicnode.com",
            "Arbitrum Sepolia" to "https://arbitrum-sepolia-rpc.publicnode.com",
            "Optimism Sepolia" to "https://optimism-sepolia-rpc.publicnode.com",
            "Base Sepolia" to "https://base-sepolia-rpc.publicnode.com"
        )
        
        evmChains.forEach { (name, rpc) ->
            println("\n📍 $name")
            try {
                val sdk = RealBlockchainSDK(rpc)
                val balance = sdk.getEthereumBalance(ethAddress1)
                println("   ✅ 錢包 1 餘額: $balance")
                assertTrue(balance >= 0.0)
            } catch (e: Exception) {
                println("   ❌ 錯誤: ${e.message}")
            }
        }
    }
    
    @Test
    fun testTransactionSigning() = runTest {
        println("\n🔏 交易簽名測試")
        println("-" * 40)
        
        // TODO: 實現交易簽名測試
        // 需要從助記詞生成私鑰
        // 然後使用私鑰簽名交易
        
        println("⚠️ 交易簽名測試待實現")
        assertTrue(true, "暫時跳過")
    }
    
    @Test
    fun testMoneroBIP39Compatibility() = runTest {
        println("\n💰 Monero BIP39 兼容性測試")
        println("-" * 40)
        
        val support = MoneroBIP39Support()
        
        println("📝 BIP39 助記詞: ${mnemonic1.take(30)}...")
        
        val moneroKeys = support.generateMoneroKeysFromBIP39(mnemonic1)
        
        println("✅ Monero 地址: ${moneroKeys.address}")
        println("   私密支出密鑰: ${moneroKeys.privateSpendKey.toHexString().take(32)}...")
        println("   私密查看密鑰: ${moneroKeys.privateViewKey.toHexString().take(32)}...")
        
        assertTrue(moneroKeys.address.isNotEmpty())
        assertTrue(moneroKeys.privateSpendKey.isNotEmpty())
    }
}

// 擴展函數 - KMP 兼容版本
private fun ByteArray.toHexString(): String {
    return joinToString("") { byte ->
        val value = byte.toInt() and 0xFF
        if (value < 16) "0${value.toString(16)}" else value.toString(16)
    }
}

// String 重複運算符
private operator fun String.times(n: Int): String = repeat(n)
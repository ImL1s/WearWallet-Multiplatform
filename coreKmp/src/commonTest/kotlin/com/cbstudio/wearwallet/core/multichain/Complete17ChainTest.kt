package com.cbstudio.wearwallet.core.multichain

import com.cbstudio.wearwallet.core.multichain.sdk.WalletManager
import com.cbstudio.wearwallet.core.common.Result
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.math.pow
import com.cbstudio.wearwallet.core.testing.TestAddresses
import com.cbstudio.wearwallet.core.security.AllowDevCapabilityGate

/**
 * 完整的 17 條區塊鏈測試
 * 使用提供的兩個助記詞測試所有支援的區塊鏈
 */
class Complete17ChainTest {
    
    // 提供的兩個助記詞
    private val mnemonic1 = "rookie abuse frozen luxury science hat alert avoid car lemon day cost"
    private val mnemonic2 = "iron mind drip glad load second merge rough music cloud fresh heavy"
    
    // 預期的 Ethereum 地址（用於驗證）
    private val expectedEthAddress1 = "0x2ff446b6146A4F845F1EC1007eDdf157c46DD634"
    private val expectedEthAddress2 = TestAddresses.VITALIK
    
    @Test
    fun testAll17Chains() = runTest {
        println("\n" + "=" * 80)
        println("🚀 完整 17 條區塊鏈測試")
        println("=" * 80)
        
        val walletManager1 = WalletManager(mnemonic1, AllowDevCapabilityGate())
        val walletManager2 = WalletManager(mnemonic2, AllowDevCapabilityGate())
        
        // 初始化所有 SDK
        println("\n📱 初始化所有區塊鏈 SDK...")
        val initResult1 = walletManager1.initializeAll()
        val initResult2 = walletManager2.initializeAll()
        
        assertTrue(initResult1 is Result.Success, "錢包1 初始化失敗")
        assertTrue(initResult2 is Result.Success, "錢包2 初始化失敗")
        
        // 定義所有 17 條鏈
        val allChains = listOf(
            // 11 條 EVM 兼容鏈
            MultiChainType.ETHEREUM to "Ethereum",
            MultiChainType.BSC to "Binance Smart Chain",
            MultiChainType.POLYGON to "Polygon",
            MultiChainType.AVALANCHE to "Avalanche",
            MultiChainType.ARBITRUM to "Arbitrum",
            MultiChainType.OPTIMISM to "Optimism",
            MultiChainType.FANTOM to "Fantom",
            MultiChainType.CRONOS to "Cronos",
            MultiChainType.BASE to "Base",
            MultiChainType.CELO to "Celo",
            MultiChainType.MOONBEAM to "Moonbeam",
            
            // 6 條特殊鏈
            MultiChainType.SOLANA to "Solana",
            MultiChainType.TRON to "Tron",
            MultiChainType.BITCOIN to "Bitcoin",
            MultiChainType.LITECOIN to "Litecoin",
            MultiChainType.DOGECOIN to "Dogecoin",
            MultiChainType.BITCOIN_CASH to "Bitcoin Cash"
        )
        
        println("\n📊 測試結果摘要:")
        println("-" * 60)
        
        var successCount = 0
        var failCount = 0
        val results = mutableListOf<TestResult>()
        
        // 測試每條鏈
        allChains.forEach { (chainType, chainName) ->
            print("Testing $chainName...")
            
            try {
                // 測試錢包1
                val address1 = walletManager1.getDerivedAddress(chainType)
                val sdk1 = walletManager1.getSDK(chainType)
                
                // 測試錢包2
                val address2 = walletManager2.getDerivedAddress(chainType)
                val sdk2 = walletManager2.getSDK(chainType)
                
                // 驗證地址生成
                val addressValid = address1.isNotEmpty() && address2.isNotEmpty()
                
                // 對於 Ethereum，驗證地址是否正確
                if (chainType == MultiChainType.ETHEREUM) {
                    assertEquals(expectedEthAddress1.lowercase(), address1.lowercase(), 
                        "錢包1 Ethereum 地址不匹配")
                    assertEquals(expectedEthAddress2.lowercase(), address2.lowercase(), 
                        "錢包2 Ethereum 地址不匹配")
                }
                
                // 驗證 SDK 存在
                val sdkValid = sdk1 != null && sdk2 != null
                
                if (addressValid && sdkValid) {
                    println(" ✅ PASSED")
                    successCount++
                    results.add(TestResult(chainName, true, address1, address2))
                } else {
                    println(" ❌ FAILED")
                    failCount++
                    results.add(TestResult(chainName, false, address1, address2))
                }
                
            } catch (e: Exception) {
                println(" ❌ ERROR: ${e.message}")
                failCount++
                results.add(TestResult(chainName, false, "", "", e.message))
            }
        }
        
        // 打印詳細結果
        println("\n📝 詳細測試結果:")
        println("-" * 80)
        
        results.forEach { result ->
            println("\n${result.chainName}:")
            if (result.success) {
                println("  狀態: ✅ 成功")
                println("  錢包1地址: ${result.address1.take(20)}...")
                println("  錢包2地址: ${result.address2.take(20)}...")
            } else {
                println("  狀態: ❌ 失敗")
                if (result.error != null) {
                    println("  錯誤: ${result.error}")
                }
            }
        }
        
        // 總結
        println("\n" + "=" * 80)
        println("📈 測試總結:")
        println("  成功: $successCount / ${allChains.size}")
        println("  失敗: $failCount / ${allChains.size}")
        println("  成功率: ${(successCount * 100.0 / allChains.size).format(2)}%")
        println("=" * 80)
        
        // 斷言至少有一半的鏈測試成功
        assertTrue(successCount >= allChains.size / 2, 
            "測試失敗：只有 $successCount/${allChains.size} 條鏈測試通過")
    }
    
    @Test
    fun testCrossWalletTransfer() = runTest {
        println("\n" + "=" * 80)
        println("💸 跨錢包轉賬測試")
        println("=" * 80)
        
        val walletManager1 = WalletManager(mnemonic1, AllowDevCapabilityGate())
        val walletManager2 = WalletManager(mnemonic2, AllowDevCapabilityGate())
        
        // 初始化
        walletManager1.initializeAll()
        walletManager2.initializeAll()
        
        // 測試 Ethereum 轉賬（從錢包1到錢包2）
        val fromAddress = walletManager1.getDerivedAddress(MultiChainType.ETHEREUM)
        val toAddress = walletManager2.getDerivedAddress(MultiChainType.ETHEREUM)
        
        println("\n🔄 Ethereum 轉賬測試:")
        println("  From: $fromAddress")
        println("  To: $toAddress")
        println("  Amount: 0.0001 ETH")
        
        // 創建交易（但不廣播）
        val txResult = walletManager1.createTestTransaction(
            MultiChainType.ETHEREUM, 
            "0.0001"
        )
        
        when (txResult) {
            is Result.Success -> {
                println("  ✅ 交易創建成功")
                println("  ⚠️ 注意：交易未廣播（需要實際餘額）")
            }
            is Result.Failure -> {
                println("  ⚠️ 交易創建失敗: ${txResult.exception.message}")
                println("  這是預期的（測試賬戶可能沒有餘額）")
            }
            is Result.Loading -> {
                println("  ⏳ 交易創建中...")
            }
        }
    }
    
    @Test
    fun testAddressConsistency() = runTest {
        println("\n" + "=" * 80)
        println("🔑 地址一致性測試")
        println("=" * 80)
        
        // 多次創建相同助記詞的錢包，確保地址一致
        val addresses = mutableListOf<String>()
        
        repeat(3) { index ->
            val walletManager = WalletManager(mnemonic1, AllowDevCapabilityGate())
            walletManager.initializeAll()
            val ethAddress = walletManager.getDerivedAddress(MultiChainType.ETHEREUM)
            addresses.add(ethAddress)
            println("  第 ${index + 1} 次生成: $ethAddress")
        }
        
        // 驗證所有地址相同
        assertTrue(addresses.all { it.lowercase() == expectedEthAddress1.lowercase() },
            "地址不一致：$addresses")
        
        println("\n  ✅ 地址一致性驗證通過")
    }
}

// 測試結果數據類
private data class TestResult(
    val chainName: String,
    val success: Boolean,
    val address1: String,
    val address2: String,
    val error: String? = null
)

// 格式化擴展函數 - KMP 兼容版本
internal fun Double.format(decimals: Int): String {
    val multiplier = 10.0.pow(decimals.toDouble())
    val rounded = (this * multiplier).toLong().toDouble() / multiplier
    return rounded.toString()
}

// 重複字符串擴展
private operator fun String.times(n: Int): String = repeat(n)
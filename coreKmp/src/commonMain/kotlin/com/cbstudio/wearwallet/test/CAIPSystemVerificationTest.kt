package com.cbstudio.wearwallet.test

import kotlinx.datetime.Clock
import com.cbstudio.wearwallet.core.caip.*
import com.cbstudio.wearwallet.core.caip.adapters.SolanaCAIPAdapter
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.core.multichain.sdk.SDKConfig
import com.cbstudio.wearwallet.core.common.Result
import kotlinx.coroutines.runBlocking
import co.touchlab.kermit.Logger

/**
 * CAIP 系統完整驗證測試
 * 
 * 執行完整的系統驗證，確保所有 CAIP 功能正常運作
 */
class CAIPSystemVerificationTest {
    
    private val logger = Logger.withTag("CAIPVerificationTest")
    
    /**
     * 執行完整系統驗證
     */
    fun runCompleteVerification(): CAIPSystemVerificationResult {
        logger.i("開始執行 CAIP 系統完整驗證")
        
        val results = mutableListOf<CAIPVerificationStep>()
        var allPassed = true
        
        // 步驟 1: 驗證 CAIP-2 鏈 ID 標準
        val step1 = verifyCAIP2ChainIDs()
        results.add(step1)
        if (!step1.passed) allPassed = false
        
        // 步驟 2: 驗證 CAIP-10 地址標準  
        val step2 = verifyCAIP10Addresses()
        results.add(step2)
        if (!step2.passed) allPassed = false
        
        // 步驟 3: 驗證 CAIP-19 資產標準
        val step3 = verifyCAIP19Assets()
        results.add(step3)
        if (!step3.passed) allPassed = false
        
        // 步驟 4: 驗證 CAIP 服務整合
        val step4 = verifyCAIPServices()
        results.add(step4)
        if (!step4.passed) allPassed = false
        
        // 步驟 5: 驗證 Solana CAIP 適配器
        val step5 = verifySolanaCAIPAdapter()
        results.add(step5)
        if (!step5.passed) allPassed = false
        
        // 步驟 6: 驗證完整測試套件
        val step6 = verifyTestSuite()
        results.add(step6)
        if (!step6.passed) allPassed = false
        
        val summary = generateVerificationSummary(results, allPassed)
        
        logger.i("CAIP 系統驗證完成: ${if (allPassed) "全部通過" else "存在問題"}")
        
        return CAIPSystemVerificationResult(
            overallPassed = allPassed,
            steps = results,
            summary = summary,
            timestamp = Clock.System.now().toEpochMilliseconds()
        )
    }
    
    /**
     * 驗證 CAIP-2 鏈 ID 標準
     */
    private fun verifyCAIP2ChainIDs(): CAIPVerificationStep {
        return try {
            logger.d("驗證 CAIP-2 鏈 ID 標準")
            
            val testCases = listOf(
                "eip155:1" to ("eip155" to "1"),
                "solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp" to ("solana" to "5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp"),
                "bip122:000000000019d6689c085ae165831e93" to ("bip122" to "000000000019d6689c085ae165831e93")
            )
            
            val results = mutableListOf<String>()
            var allPassed = true
            
            for ((input, expected) in testCases) {
                val parseResult = CAIPChainID.parse(input)
                when (parseResult) {
                    is Result.Success -> {
                        val chainId = parseResult.data
                        if (chainId.namespace == expected.first && chainId.reference == expected.second) {
                            results.add("✓ $input 解析正確")
                        } else {
                            results.add("✗ $input 解析錯誤: 期望 $expected，實際 ${chainId.namespace}:${chainId.reference}")
                            allPassed = false
                        }
                    }
                    is Result.Failure -> {
                        results.add("✗ $input 解析失敗: ${parseResult.exception.message}")
                        allPassed = false
                    }
                    is Result.Loading -> {
                        results.add("⏳ $input 仍在載入中")
                        allPassed = false
                    }
                }
            }
            
            // 測試 MultiChainType 轉換
            val conversionTests = listOf(
                MultiChainType.ETHEREUM to "eip155:1",
                MultiChainType.SOLANA to "solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp",
                MultiChainType.BITCOIN to "bip122:000000000019d6689c085ae165831e93"
            )
            
            for ((chainType, expectedCAIP) in conversionTests) {
                val chainId = CAIPChainID.fromMultiChainType(chainType)
                val caipString = chainId.toCAIPString()
                if (caipString == expectedCAIP) {
                    results.add("✓ $chainType 轉換正確: $caipString")
                } else {
                    results.add("✗ $chainType 轉換錯誤: 期望 $expectedCAIP，實際 $caipString")
                    allPassed = false
                }
            }
            
            CAIPVerificationStep(
                stepName = "CAIP-2 鏈 ID 標準驗證",
                passed = allPassed,
                details = results,
                category = "CAIP標準"
            )
        } catch (e: Exception) {
            CAIPVerificationStep(
                stepName = "CAIP-2 鏈 ID 標準驗證",
                passed = false,
                details = listOf("驗證過程發生異常: ${e.message}"),
                category = "CAIP標準"
            )
        }
    }
    
    /**
     * 驗證 CAIP-10 地址標準
     */
    private fun verifyCAIP10Addresses(): CAIPVerificationStep {
        return try {
            logger.d("驗證 CAIP-10 地址標準")
            
            val testAddresses = listOf(
                "eip155:1:0xab16a96d359ec26a11e2c2b3d8f8b8942d5bfcdb",
                "solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp:4Qkev8aNZcqFNSRhQzwyLMFSsi94jHqE8WNVTJzTP99F",
                "bip122:000000000019d6689c085ae165831e93:1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa"
            )
            
            val results = mutableListOf<String>()
            var allPassed = true
            
            for (testAddress in testAddresses) {
                // 測試解析
                val parseResult = CAIPAddress.parse(testAddress)
                when (parseResult) {
                    is Result.Success -> {
                        val address = parseResult.data
                        val reconstructed = address.toCAIPString()
                        if (reconstructed == testAddress) {
                            results.add("✓ $testAddress 解析和重構正確")
                        } else {
                            results.add("✗ $testAddress 重構錯誤: $reconstructed")
                            allPassed = false
                        }
                        
                        // 測試驗證
                        val validationResult = address.validate()
                        when (validationResult) {
                            is Result.Success -> {
                                if (validationResult.data) {
                                    results.add("  ✓ 地址驗證通過")
                                } else {
                                    results.add("  ✗ 地址驗證失敗")
                                    allPassed = false
                                }
                            }
                            is Result.Failure -> {
                                results.add("  ✗ 地址驗證異常: ${validationResult.exception.message}")
                                allPassed = false
                            }
                            is Result.Loading -> {
                                results.add("  ⏳ 地址驗證載入中")
                                allPassed = false
                            }
                        }
                        
                        // 測試顯示格式
                        val displayAddress = address.getDisplayAddress()
                        if (displayAddress.isNotEmpty()) {
                            results.add("  ✓ 顯示格式: $displayAddress")
                        } else {
                            results.add("  ✗ 顯示格式為空")
                            allPassed = false
                        }
                        
                    }
                    is Result.Failure -> {
                        results.add("✗ $testAddress 解析失敗: ${parseResult.exception.message}")
                        allPassed = false
                    }
                    is Result.Loading -> {
                        results.add("⏳ $testAddress 載入中")
                        allPassed = false
                    }
                }
            }
            
            // 測試舊版地址轉換
            val legacyTests = listOf(
                "0xab16a96d359ec26a11e2c2b3d8f8b8942d5bfcdb" to MultiChainType.ETHEREUM,
                "4Qkev8aNZcqFNSRhQzwyLMFSsi94jHqE8WNVTJzTP99F" to MultiChainType.SOLANA
            )
            
            for ((legacyAddress, chainType) in legacyTests) {
                val caipAddress = CAIPAddress.fromLegacyAddress(legacyAddress, chainType)
                if (caipAddress.address == legacyAddress) {
                    results.add("✓ 舊版地址轉換成功: $chainType")
                } else {
                    results.add("✗ 舊版地址轉換失敗: $legacyAddress -> ${caipAddress.address}")
                    allPassed = false
                }
            }
            
            CAIPVerificationStep(
                stepName = "CAIP-10 地址標準驗證",
                passed = allPassed,
                details = results,
                category = "CAIP標準"
            )
        } catch (e: Exception) {
            CAIPVerificationStep(
                stepName = "CAIP-10 地址標準驗證",
                passed = false,
                details = listOf("驗證過程發生異常: ${e.message}"),
                category = "CAIP標準"
            )
        }
    }
    
    /**
     * 驗證 CAIP-19 資產標準
     */
    private fun verifyCAIP19Assets(): CAIPVerificationStep {
        return try {
            logger.d("驗證 CAIP-19 資產標準")
            
            val results = mutableListOf<String>()
            var allPassed = true
            
            // 測試各種資產類型
            val assetTests = listOf(
                "eip155:1/slip44:60" to "原生 ETH",
                "eip155:1/erc20:0xa0b86a33e6776bb5b4e8a8e7b4a9b23ef4b50c6b" to "ERC20 代幣",
                "eip155:1/erc721:0x06012c8cf97bead5deae237070f9587f8e7a266d/771769" to "ERC721 NFT",
                "solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp/slip44:501" to "原生 SOL",
                "solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp/spl-token:EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v" to "SPL USDC"
            )
            
            for ((caipString, description) in assetTests) {
                val parseResult = CAIPAsset.parse(caipString)
                when (parseResult) {
                    is Result.Success -> {
                        val asset = parseResult.data
                        val reconstructed = asset.toCAIPString()
                        
                        if (reconstructed == caipString) {
                            results.add("✓ $description 解析正確: $caipString")
                            
                            // 測試資產類型檢測
                            val symbol = asset.getSymbol()
                            val isNative = asset.isNativeToken()
                            val isERC20 = asset.isERC20Token()
                            val isNFT = asset.isNFT()
                            
                            results.add("  符號: $symbol, 原生: $isNative, ERC20: $isERC20, NFT: $isNFT")
                            
                        } else {
                            results.add("✗ $description 重構錯誤: $reconstructed")
                            allPassed = false
                        }
                    }
                    is Result.Failure -> {
                        results.add("✗ $description 解析失敗: ${parseResult.exception.message}")
                        allPassed = false
                    }
                    is Result.Loading -> {
                        results.add("⏳ $description 載入中")
                        allPassed = false
                    }
                }
            }
            
            // 測試資產創建方法
            val creationTests = listOf(
                MultiChainType.ETHEREUM to "ETH",
                MultiChainType.SOLANA to "SOL", 
                MultiChainType.BITCOIN to "BTC"
            )
            
            for ((chainType, expectedSymbol) in creationTests) {
                val nativeAsset = CAIPAsset.createNativeAsset(chainType)
                val actualSymbol = nativeAsset.getSymbol()
                
                if (actualSymbol == expectedSymbol && nativeAsset.isNativeToken()) {
                    results.add("✓ $chainType 原生資產創建成功: ${nativeAsset.toCAIPString()}")
                } else {
                    results.add("✗ $chainType 原生資產創建失敗: 期望 $expectedSymbol，實際 $actualSymbol")
                    allPassed = false
                }
            }
            
            // 測試 ERC20 創建
            val erc20Asset = CAIPAsset.createERC20Asset(
                "0xa0b86a33e6776bb5b4e8a8e7b4a9b23ef4b50c6b", 
                MultiChainType.ETHEREUM
            )
            if (erc20Asset.isERC20Token() && !erc20Asset.isNativeToken()) {
                results.add("✓ ERC20 資產創建成功: ${erc20Asset.toCAIPString()}")
            } else {
                results.add("✗ ERC20 資產創建失敗")
                allPassed = false
            }
            
            CAIPVerificationStep(
                stepName = "CAIP-19 資產標準驗證",
                passed = allPassed,
                details = results,
                category = "CAIP標準"
            )
        } catch (e: Exception) {
            CAIPVerificationStep(
                stepName = "CAIP-19 資產標準驗證",
                passed = false,
                details = listOf("驗證過程發生異常: ${e.message}"),
                category = "CAIP標準"
            )
        }
    }
    
    /**
     * 驗證 CAIP 服務整合
     */
    private fun verifyCAIPServices(): CAIPVerificationStep {
        return try {
            logger.d("驗證 CAIP 服務整合")
            
            val caipService = CAIPService()
            val results = mutableListOf<String>()
            var allPassed = true
            
            // 測試通用 CAIP 字串解析
            val testStrings = listOf(
                "eip155:1",
                "eip155:1:0xab16a96d359ec26a11e2c2b3d8f8b8942d5bfcdb",
                "eip155:1/slip44:60",
                "eip155:1/erc20:0xa0b86a33e6776bb5b4e8a8e7b4a9b23ef4b50c6b"
            )
            
            for (testString in testStrings) {
                val parseResult = caipService.parseCAIPString(testString)
                val validateResult = caipService.validateCAIPString(testString)
                
                when {
                    parseResult.isSuccess() && validateResult.isSuccess() -> {
                        results.add("✓ '$testString' 解析和驗證成功")
                    }
                    parseResult.isFailure() -> {
                        results.add("✗ '$testString' 解析失敗")
                        allPassed = false
                    }
                    validateResult.isFailure() -> {
                        results.add("✗ '$testString' 驗證失敗")
                        allPassed = false
                    }
                    else -> {
                        results.add("✗ '$testString' 處理失敗")
                        allPassed = false
                    }
                }
            }
            
            // 測試區塊瀏覽器 URL 生成
            val chainTests = listOf(
                CAIPChainID("eip155", "1") to "以太坊主網",
                CAIPChainID("solana", "5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp") to "Solana 主網"
            )
            
            for ((chainId, description) in chainTests) {
                val explorerUrl = caipService.getExplorerUrl(chainId, "test_hash")
                if (explorerUrl?.isNotEmpty() == true && explorerUrl.contains("test_hash")) {
                    results.add("✓ $description 區塊瀏覽器 URL: $explorerUrl")
                } else {
                    results.add("✗ $description 區塊瀏覽器 URL 生成失敗")
                    allPassed = false
                }
            }
            
            // 測試 CAIPUtils
            val addresses = listOf(
                "0xab16a96d359ec26a11e2c2b3d8f8b8942d5bfcdb",
                "0x742d35Cc6634C0532925a3b8d6f4f3e71b1B8A57"
            )
            
            val caipAddresses = CAIPUtils.convertAddressesToCAIP(addresses, MultiChainType.ETHEREUM)
            if (caipAddresses.size == addresses.size) {
                results.add("✓ 批次地址轉換成功: ${caipAddresses.size} 個地址")
            } else {
                results.add("✗ 批次地址轉換失敗: 期望 ${addresses.size}，實際 ${caipAddresses.size}")
                allPassed = false
            }
            
            CAIPVerificationStep(
                stepName = "CAIP 服務整合驗證",
                passed = allPassed,
                details = results,
                category = "服務整合"
            )
        } catch (e: Exception) {
            CAIPVerificationStep(
                stepName = "CAIP 服務整合驗證",
                passed = false,
                details = listOf("驗證過程發生異常: ${e.message}"),
                category = "服務整合"
            )
        }
    }
    
    /**
     * 驗證 Solana CAIP 適配器
     */
    private fun verifySolanaCAIPAdapter(): CAIPVerificationStep {
        return try {
            logger.d("驗證 Solana CAIP 適配器")
            
            val adapter = SolanaCAIPAdapter("devnet")
            val results = mutableListOf<String>()
            var allPassed = true
            
            // 檢查基本屬性
            if (adapter.chainType == MultiChainType.SOLANA) {
                results.add("✓ 鏈類型正確: ${adapter.chainType}")
            } else {
                results.add("✗ 鏈類型錯誤: 期望 SOLANA，實際 ${adapter.chainType}")
                allPassed = false
            }
            
            if (adapter.supportedNamespaces.contains("solana")) {
                results.add("✓ 支援 Solana 命名空間")
            } else {
                results.add("✗ 不支援 Solana 命名空間")
                allPassed = false
            }
            
            val expectedAssetNamespaces = setOf("slip44", "spl-token", "spl-nft")
            val actualAssetNamespaces = adapter.supportedAssetNamespaces.toSet()
            if (actualAssetNamespaces.containsAll(expectedAssetNamespaces)) {
                results.add("✓ 資產命名空間支援完整: $actualAssetNamespaces")
            } else {
                results.add("✗ 資產命名空間支援不完整: 期望 $expectedAssetNamespaces，實際 $actualAssetNamespaces")
                allPassed = false
            }
            
            // 檢查支援的鏈 ID
            val supportedChains = adapter.getSupportedChainIDs()
            val hasSolanaChains = supportedChains.any { it.namespace == "solana" }
            if (hasSolanaChains) {
                results.add("✓ 支援的 Solana 鏈: ${supportedChains.size} 個")
                supportedChains.forEach { chainId ->
                    results.add("  - ${chainId.toCAIPString()}")
                }
            } else {
                results.add("✗ 未找到支援的 Solana 鏈")
                allPassed = false
            }
            
            // 測試地址驗證
            try {
                val validAddress = CAIPAddress.parse("solana:EtWTRABZaYq6iMfeYKouRu166VU2xqa1:4Qkev8aNZcqFNSRhQzwyLMFSsi94jHqE8WNVTJzTP99F").getOrThrow()
                val validationResult = adapter.validateAddressCAIP(validAddress)
                
                when (validationResult) {
                    is Result.Success -> {
                        if (validationResult.data.isValid) {
                            results.add("✓ Solana 地址驗證成功")
                        } else {
                            results.add("✗ Solana 地址驗證失敗: ${validationResult.data.message}")
                            allPassed = false
                        }
                    }
                    is Result.Failure -> {
                        results.add("✗ 地址驗證異常: ${validationResult.exception.message}")
                        allPassed = false
                    }
                    is Result.Loading -> {
                        results.add("⏳ 地址驗證載入中")
                        allPassed = false
                    }
                }
            } catch (e: Exception) {
                results.add("✗ 地址驗證測試失敗: ${e.message}")
                allPassed = false
            }
            
            CAIPVerificationStep(
                stepName = "Solana CAIP 適配器驗證",
                passed = allPassed,
                details = results,
                category = "適配器"
            )
        } catch (e: Exception) {
            CAIPVerificationStep(
                stepName = "Solana CAIP 適配器驗證",
                passed = false,
                details = listOf("驗證過程發生異常: ${e.message}"),
                category = "適配器"
            )
        }
    }
    
    /**
     * 驗證完整測試套件
     */
    private fun verifyTestSuite(): CAIPVerificationStep {
        return try {
            logger.d("驗證完整測試套件")
            
            val testSuite = CAIPTestSuite()
            val results = mutableListOf<String>()
            var allPassed = true
            
            runBlocking {
                val testResults = testSuite.runFullTestSuite()
                
                results.add("測試套件執行完成:")
                results.add("  總測試數: ${testResults.totalTests}")
                results.add("  通過測試: ${testResults.passedTests}")
                results.add("  失敗測試: ${testResults.failedTests}")
                
                val successRate = (testResults.passedTests * 100.0 / testResults.totalTests).toInt()
                results.add("  成功率: $successRate%")
                
                if (testResults.failedTests == 0) {
                    results.add("✓ 所有測試通過")
                } else {
                    results.add("✗ 存在 ${testResults.failedTests} 個失敗測試")
                    allPassed = false
                    
                    // 添加失敗測試詳情
                    val failedTests = testResults.testResults.filter { !it.passed }
                    failedTests.forEach { test ->
                        results.add("  失敗: ${test.testName}")
                        if (test.details.isNotEmpty()) {
                            results.add("    詳情: ${test.details}")
                        }
                    }
                }
            }
            
            CAIPVerificationStep(
                stepName = "完整測試套件驗證",
                passed = allPassed,
                details = results,
                category = "測試套件"
            )
        } catch (e: Exception) {
            CAIPVerificationStep(
                stepName = "完整測試套件驗證",
                passed = false,
                details = listOf("驗證過程發生異常: ${e.message}"),
                category = "測試套件"
            )
        }
    }
    
    private fun generateVerificationSummary(
        steps: List<CAIPVerificationStep>, 
        overallPassed: Boolean
    ): String {
        return buildString {
            appendLine("CAIP 系統完整驗證報告")
            appendLine("=".repeat(40))
            appendLine()
            
            appendLine("整體結果: ${if (overallPassed) "✅ 全部通過" else "❌ 存在問題"}")
            appendLine("驗證步驟: ${steps.size} 個")
            appendLine("通過步驟: ${steps.count { it.passed }} 個") 
            appendLine("失敗步驟: ${steps.count { !it.passed }} 個")
            appendLine()
            
            // 按類別分組顯示
            val categorizedSteps = steps.groupBy { it.category }
            categorizedSteps.forEach { (category, categorySteps) ->
                appendLine("【$category】")
                categorySteps.forEach { step ->
                    val status = if (step.passed) "✅" else "❌"
                    appendLine("  $status ${step.stepName}")
                }
                appendLine()
            }
            
            if (steps.any { !it.passed }) {
                appendLine("失敗步驟詳情:")
                appendLine("-".repeat(20))
                
                steps.filter { !it.passed }.forEach { step ->
                    appendLine("❌ ${step.stepName}")
                    step.details.forEach { detail ->
                        appendLine("   $detail")
                    }
                    appendLine()
                }
            }
            
            appendLine("驗證建議:")
            when {
                overallPassed -> {
                    appendLine("🎉 CAIP 標準化系統實作完整且功能正常")
                    appendLine("📋 系統已準備好進入生產環境")
                    appendLine("🚀 可以開始 Phase 3 其他鏈的適配器開發")
                }
                else -> {
                    appendLine("⚠️  請修復失敗的驗證步驟")
                    appendLine("🔧 建議逐步解決每個類別的問題")
                    appendLine("🧪 修復後重新執行驗證測試")
                }
            }
            
            appendLine()
            appendLine("驗證時間: ${Clock.System.now()}")
        }
    }
    
    private fun <T> Result<T>.isSuccess(): Boolean = this is Result.Success
    private fun <T> Result<T>.isFailure(): Boolean = this is Result.Failure
    
    private fun <T> Result<T>.getOrThrow(): T {
        return when (this) {
            is Result.Success -> this.data
            is Result.Failure -> throw this.exception
            is Result.Loading -> throw Exception("Still loading")
        }
    }
}

/**
 * 驗證步驟結果
 */
data class CAIPVerificationStep(
    val stepName: String,
    val passed: Boolean,
    val details: List<String>,
    val category: String
)

/**
 * 系統驗證結果
 */
data class CAIPSystemVerificationResult(
    val overallPassed: Boolean,
    val steps: List<CAIPVerificationStep>,
    val summary: String,
    val timestamp: Long
)
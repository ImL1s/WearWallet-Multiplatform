package com.cbstudio.wearwallet.core.caip

import com.cbstudio.wearwallet.core.caip.adapters.SolanaCAIPAdapter
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.core.common.Result
import co.touchlab.kermit.Logger

/**
 * CAIP 標準化測試套件
 * 
 * 驗證 CAIP 標準實作的正確性和相容性
 */
class CAIPTestSuite {
    
    private val logger = Logger.withTag("CAIPTestSuite")
    private val caipService = CAIPService()
    
    /**
     * 執行完整的 CAIP 測試套件
     */
    suspend fun runFullTestSuite(): CAIPTestResults {
        logger.i("Starting CAIP test suite")
        
        val results = mutableListOf<CAIPTestResult>()
        
        // CAIP-2 鏈 ID 測試
        results.addAll(testCAIP2ChainIDs())
        
        // CAIP-10 地址測試
        results.addAll(testCAIP10Addresses())
        
        // CAIP-19 資產測試
        results.addAll(testCAIP19Assets())
        
        // SDK 整合測試
        results.addAll(testSDKIntegration())
        
        // Solana 適配器測試
        results.addAll(testSolanaCAIPAdapter())
        
        val totalTests = results.size
        val passedTests = results.count { it.passed }
        val failedTests = totalTests - passedTests
        
        val overallResult = CAIPTestResults(
            totalTests = totalTests,
            passedTests = passedTests,
            failedTests = failedTests,
            testResults = results,
            summary = generateTestSummary(results)
        )
        
        logger.i("CAIP test suite completed: $passedTests/$totalTests tests passed")
        
        return overallResult
    }
    
    /**
     * 測試 CAIP-2 鏈 ID 標準
     */
    private fun testCAIP2ChainIDs(): List<CAIPTestResult> {
        val results = mutableListOf<CAIPTestResult>()
        
        // 測試基本解析
        results.add(testCAIP2Parsing())
        
        // 測試 MultiChainType 轉換
        results.add(testCAIP2MultiChainConversion())
        
        // 測試無效格式處理
        results.add(testCAIP2InvalidFormats())
        
        return results
    }
    
    private fun testCAIP2Parsing(): CAIPTestResult {
        return try {
            // 測試有效的 CAIP-2 格式
            val testCases = listOf(
                "eip155:1" to ("eip155" to "1"),
                "cosmos:cosmoshub-4" to ("cosmos" to "cosmoshub-4"),
                "solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp" to ("solana" to "5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp")
            )
            
            var allPassed = true
            val details = mutableListOf<String>()
            
            for ((input, expected) in testCases) {
                val result = CAIPChainID.parse(input)
                when (result) {
                    is Result.Success -> {
                        val chainId = result.data
                        if (chainId.namespace == expected.first && chainId.reference == expected.second) {
                            details.add("✓ $input parsed correctly")
                        } else {
                            details.add("✗ $input parsed incorrectly: expected $expected, got ${chainId.namespace}:${chainId.reference}")
                            allPassed = false
                        }
                    }
                    is Result.Failure -> {
                        details.add("✗ $input failed to parse: ${result.exception.message}")
                        allPassed = false
                    }
                    is Result.Loading -> {
                        details.add("⏳ $input still loading")
                        allPassed = false
                    }
                }
            }
            
            CAIPTestResult(
                testName = "CAIP-2 Parsing",
                passed = allPassed,
                details = details.joinToString("\n"),
                duration = 0
            )
        } catch (e: Exception) {
            CAIPTestResult(
                testName = "CAIP-2 Parsing",
                passed = false,
                details = "Test failed with exception: ${e.message}",
                duration = 0
            )
        }
    }
    
    private fun testCAIP2MultiChainConversion(): CAIPTestResult {
        return try {
            val testCases = listOf(
                MultiChainType.ETHEREUM to "eip155:1", 
                MultiChainType.SOLANA to "solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp",
                MultiChainType.BITCOIN to "bip122:000000000019d6689c085ae165831e93",
                MultiChainType.POLKADOT to "polkadot:91b171bb158e2d3848fa23a9f1c25182"
            )
            
            var allPassed = true
            val details = mutableListOf<String>()
            
            for ((chainType, expected) in testCases) {
                val chainId = CAIPChainID.fromMultiChainType(chainType)
                val caipString = chainId.toCAIPString()
                
                if (caipString == expected) {
                    details.add("✓ $chainType -> $caipString")
                } else {
                    details.add("✗ $chainType -> $caipString (expected $expected)")
                    allPassed = false
                }
            }
            
            CAIPTestResult(
                testName = "CAIP-2 MultiChain Conversion",
                passed = allPassed,
                details = details.joinToString("\n"),
                duration = 0
            )
        } catch (e: Exception) {
            CAIPTestResult(
                testName = "CAIP-2 MultiChain Conversion",
                passed = false,
                details = "Test failed with exception: ${e.message}",
                duration = 0
            )
        }
    }
    
    private fun testCAIP2InvalidFormats(): CAIPTestResult {
        return try {
            val invalidFormats = listOf(
                "eip155",              // 缺少參考
                "eip155:1:extra",      // 多餘部分
                ":1",                  // 缺少命名空間
                "eip155:",             // 缺少參考
                ""                     // 空字符串
            )
            
            var allPassed = true
            val details = mutableListOf<String>()
            
            for (invalidFormat in invalidFormats) {
                val result = CAIPChainID.parse(invalidFormat)
                when (result) {
                    is Result.Success -> {
                        details.add("✗ '$invalidFormat' should have failed but passed")
                        allPassed = false
                    }
                    is Result.Failure -> {
                        details.add("✓ '$invalidFormat' correctly rejected")
                    }
                    is Result.Loading -> {
                        details.add("⏳ '$invalidFormat' still loading")
                        allPassed = false
                    }
                }
            }
            
            CAIPTestResult(
                testName = "CAIP-2 Invalid Formats",
                passed = allPassed,
                details = details.joinToString("\n"),
                duration = 0
            )
        } catch (e: Exception) {
            CAIPTestResult(
                testName = "CAIP-2 Invalid Formats",
                passed = false,
                details = "Test failed with exception: ${e.message}",
                duration = 0
            )
        }
    }
    
    /**
     * 測試 CAIP-10 地址標準
     */
    private fun testCAIP10Addresses(): List<CAIPTestResult> {
        val results = mutableListOf<CAIPTestResult>()
        
        results.add(testCAIP10Parsing())
        results.add(testCAIP10Validation())
        results.add(testCAIP10LegacyConversion())
        
        return results
    }
    
    private fun testCAIP10Parsing(): CAIPTestResult {
        return try {
            val testCases = listOf(
                "eip155:1:0xab16a96d359ec26a11e2c2b3d8f8b8942d5bfcdb",
                "cosmos:cosmoshub-4:cosmos1t2uflqwqe0fsj0shcfkrvpukewcw40yjj6hdc0",
                "solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp:4Qkev8aNZcqFNSRhQzwyLMFSsi94jHqE8WNVTJzTP99F"
            )
            
            var allPassed = true
            val details = mutableListOf<String>()
            
            for (testCase in testCases) {
                val result = CAIPAddress.parse(testCase)
                when (result) {
                    is Result.Success -> {
                        val address = result.data
                        val reconstructed = address.toCAIPString()
                        if (reconstructed == testCase) {
                            details.add("✓ $testCase parsed and reconstructed correctly")
                        } else {
                            details.add("✗ $testCase reconstructed as $reconstructed")
                            allPassed = false
                        }
                    }
                    is Result.Failure -> {
                        details.add("✗ $testCase failed to parse: ${result.exception.message}")
                        allPassed = false
                    }
                    is Result.Loading -> {
                        details.add("⏳ $testCase still loading")
                        allPassed = false
                    }
                }
            }
            
            CAIPTestResult(
                testName = "CAIP-10 Parsing",
                passed = allPassed,
                details = details.joinToString("\n"),
                duration = 0
            )
        } catch (e: Exception) {
            CAIPTestResult(
                testName = "CAIP-10 Parsing",
                passed = false,
                details = "Test failed with exception: ${e.message}",
                duration = 0
            )
        }
    }
    
    private fun testCAIP10Validation(): CAIPTestResult {
        return try {
            val validAddresses = listOf(
                CAIPAddress.parse("eip155:1:0xab16a96d359ec26a11e2c2b3d8f8b8942d5bfcdb").getOrThrow(),
                CAIPAddress.parse("solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp:4Qkev8aNZcqFNSRhQzwyLMFSsi94jHqE8WNVTJzTP99F").getOrThrow()
            )
            
            var allPassed = true
            val details = mutableListOf<String>()
            
            for (address in validAddresses) {
                val validation = address.validate()
                when (validation) {
                    is Result.Success -> {
                        if (validation.data) {
                            details.add("✓ ${address.toCAIPString()} validated successfully")
                        } else {
                            details.add("✗ ${address.toCAIPString()} validation failed")
                            allPassed = false
                        }
                    }
                    is Result.Failure -> {
                        details.add("✗ ${address.toCAIPString()} validation threw exception: ${validation.exception.message}")
                        allPassed = false
                    }
                    is Result.Loading -> {
                        details.add("⏳ ${address.toCAIPString()} validation still loading")
                        allPassed = false
                    }
                }
            }
            
            CAIPTestResult(
                testName = "CAIP-10 Validation",
                passed = allPassed,
                details = details.joinToString("\n"),
                duration = 0
            )
        } catch (e: Exception) {
            CAIPTestResult(
                testName = "CAIP-10 Validation",
                passed = false,
                details = "Test failed with exception: ${e.message}",
                duration = 0
            )
        }
    }
    
    private fun testCAIP10LegacyConversion(): CAIPTestResult {
        return try {
            val legacyAddresses = listOf(
                "0xab16a96d359ec26a11e2c2b3d8f8b8942d5bfcdb" to MultiChainType.ETHEREUM,
                "4Qkev8aNZcqFNSRhQzwyLMFSsi94jHqE8WNVTJzTP99F" to MultiChainType.SOLANA
            )
            
            var allPassed = true
            val details = mutableListOf<String>()
            
            for ((address, chainType) in legacyAddresses) {
                val caipAddress = CAIPAddress.fromLegacyAddress(address, chainType)
                val extractedAddress = caipAddress.address
                
                if (extractedAddress == address) {
                    details.add("✓ Legacy $chainType address converted correctly")
                } else {
                    details.add("✗ Legacy $chainType address conversion failed: $address -> $extractedAddress")
                    allPassed = false
                }
            }
            
            CAIPTestResult(
                testName = "CAIP-10 Legacy Conversion",
                passed = allPassed,
                details = details.joinToString("\n"),
                duration = 0
            )
        } catch (e: Exception) {
            CAIPTestResult(
                testName = "CAIP-10 Legacy Conversion",
                passed = false,
                details = "Test failed with exception: ${e.message}",
                duration = 0
            )
        }
    }
    
    /**
     * 測試 CAIP-19 資產標準
     */
    private fun testCAIP19Assets(): List<CAIPTestResult> {
        val results = mutableListOf<CAIPTestResult>()
        
        results.add(testCAIP19Parsing())
        results.add(testCAIP19AssetCreation())
        results.add(testCAIP19AssetTypes())
        
        return results
    }
    
    private fun testCAIP19Parsing(): CAIPTestResult {
        return try {
            val testCases = listOf(
                "eip155:1/slip44:60", // ETH
                "eip155:1/erc20:0xa0b86a33e6776bb5b4e8a8e7b4a9b23ef4b50c6b", // ERC20
                "solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp/slip44:501" // SOL
            )
            
            var allPassed = true
            val details = mutableListOf<String>()
            
            for (testCase in testCases) {
                val result = CAIPAsset.parse(testCase)
                when (result) {
                    is Result.Success -> {
                        val asset = result.data
                        val reconstructed = asset.toCAIPString()
                        if (reconstructed == testCase) {
                            details.add("✓ $testCase parsed correctly")
                        } else {
                            details.add("✗ $testCase reconstructed as $reconstructed")
                            allPassed = false
                        }
                    }
                    is Result.Failure -> {
                        details.add("✗ $testCase failed to parse: ${result.exception.message}")
                        allPassed = false
                    }
                    is Result.Loading -> {
                        details.add("⏳ $testCase still loading")
                        allPassed = false
                    }
                }
            }
            
            CAIPTestResult(
                testName = "CAIP-19 Parsing",
                passed = allPassed,
                details = details.joinToString("\n"),
                duration = 0
            )
        } catch (e: Exception) {
            CAIPTestResult(
                testName = "CAIP-19 Parsing",
                passed = false,
                details = "Test failed with exception: ${e.message}",
                duration = 0
            )
        }
    }
    
    private fun testCAIP19AssetCreation(): CAIPTestResult {
        return try {
            val chainTypes = listOf(
                MultiChainType.ETHEREUM,
                MultiChainType.SOLANA,
                MultiChainType.BITCOIN
            )
            
            var allPassed = true
            val details = mutableListOf<String>()
            
            for (chainType in chainTypes) {
                val nativeAsset = CAIPAsset.createNativeAsset(chainType)
                if (nativeAsset.isNativeToken()) {
                    details.add("✓ Native asset created for $chainType: ${nativeAsset.toCAIPString()}")
                } else {
                    details.add("✗ Native asset creation failed for $chainType")
                    allPassed = false
                }
            }
            
            // 測試 ERC20 創建
            val erc20Asset = CAIPAsset.createERC20Asset(
                "0xa0b86a33e6776bb5b4e8a8e7b4a9b23ef4b50c6b",
                MultiChainType.ETHEREUM
            )
            if (erc20Asset.isERC20Token()) {
                details.add("✓ ERC20 asset created: ${erc20Asset.toCAIPString()}")
            } else {
                details.add("✗ ERC20 asset creation failed")
                allPassed = false
            }
            
            CAIPTestResult(
                testName = "CAIP-19 Asset Creation",
                passed = allPassed,
                details = details.joinToString("\n"),
                duration = 0
            )
        } catch (e: Exception) {
            CAIPTestResult(
                testName = "CAIP-19 Asset Creation",
                passed = false,
                details = "Test failed with exception: ${e.message}",
                duration = 0
            )
        }
    }
    
    private fun testCAIP19AssetTypes(): CAIPTestResult {
        return try {
            val nativeAsset = CAIPAsset.createNativeAsset(MultiChainType.ETHEREUM)
            val erc20Asset = CAIPAsset.createERC20Asset("0xA0b86a33E6776BB5b4E8A8E7B4A9b23eF4b50c6B", MultiChainType.ETHEREUM)
            val nftAsset = CAIPAsset.createNFTAsset("0x06012c8cf97bead5deae237070f9587f8e7a266d", "771769", MultiChainType.ETHEREUM)
            
            var allPassed = true
            val details = mutableListOf<String>()
            
            // 測試資產類型檢查
            if (nativeAsset.isNativeToken() && !nativeAsset.isERC20Token() && !nativeAsset.isNFT()) {
                details.add("✓ Native asset type detection correct")
            } else {
                details.add("✗ Native asset type detection failed")
                allPassed = false
            }
            
            if (!erc20Asset.isNativeToken() && erc20Asset.isERC20Token() && !erc20Asset.isNFT()) {
                details.add("✓ ERC20 asset type detection correct")
            } else {
                details.add("✗ ERC20 asset type detection failed")
                allPassed = false
            }
            
            if (!nftAsset.isNativeToken() && !nftAsset.isERC20Token() && nftAsset.isNFT()) {
                details.add("✓ NFT asset type detection correct")
            } else {
                details.add("✗ NFT asset type detection failed")
                allPassed = false
            }
            
            CAIPTestResult(
                testName = "CAIP-19 Asset Types",
                passed = allPassed,
                details = details.joinToString("\n"),
                duration = 0
            )
        } catch (e: Exception) {
            CAIPTestResult(
                testName = "CAIP-19 Asset Types",
                passed = false,
                details = "Test failed with exception: ${e.message}",
                duration = 0
            )
        }
    }
    
    /**
     * 測試 SDK 整合
     */
    private fun testSDKIntegration(): List<CAIPTestResult> {
        val results = mutableListOf<CAIPTestResult>()
        
        results.add(testCAIPService())
        results.add(testCAIPUtils())
        
        return results
    }
    
    private fun testCAIPService(): CAIPTestResult {
        return try {
            val testStrings = listOf(
                "eip155:1",
                "eip155:1:0xab16a96d359ec26a11e2c2b3d8f8b8942d5bfcdb",
                "eip155:1/slip44:60"
            )
            
            var allPassed = true
            val details = mutableListOf<String>()
            
            for (testString in testStrings) {
                val parseResult = caipService.parseCAIPString(testString)
                val validateResult = caipService.validateCAIPString(testString)
                
                when {
                    parseResult.isSuccess() && validateResult.isSuccess() -> {
                        details.add("✓ '$testString' parsed and validated successfully")
                    }
                    else -> {
                        details.add("✗ '$testString' failed validation")
                        allPassed = false
                    }
                }
            }
            
            CAIPTestResult(
                testName = "CAIP Service",
                passed = allPassed,
                details = details.joinToString("\n"),
                duration = 0
            )
        } catch (e: Exception) {
            CAIPTestResult(
                testName = "CAIP Service",
                passed = false,
                details = "Test failed with exception: ${e.message}",
                duration = 0
            )
        }
    }
    
    private fun testCAIPUtils(): CAIPTestResult {
        return try {
            val addresses = listOf(
                "0xab16a96d359ec26a11e2c2b3d8f8b8942d5bfcdb",
                "0x742d35Cc6634C0532925a3b8d6f4f3e71b1B8A57"
            )
            
            val caipAddresses = CAIPUtils.convertAddressesToCAIP(addresses, MultiChainType.ETHEREUM)
            
            var allPassed = true
            val details = mutableListOf<String>()
            
            if (caipAddresses.size == addresses.size) {
                details.add("✓ Address batch conversion successful: ${caipAddresses.size} addresses")
                
                // 驗證轉換後的地址
                caipAddresses.forEachIndexed { index, caipAddress ->
                    if (caipAddress.address == addresses[index]) {
                        details.add("  ✓ ${addresses[index]} -> ${caipAddress.toCAIPString()}")
                    } else {
                        details.add("  ✗ Address conversion mismatch")
                        allPassed = false
                    }
                }
            } else {
                details.add("✗ Address batch conversion failed: expected ${addresses.size}, got ${caipAddresses.size}")
                allPassed = false
            }
            
            CAIPTestResult(
                testName = "CAIP Utils",
                passed = allPassed,
                details = details.joinToString("\n"),
                duration = 0
            )
        } catch (e: Exception) {
            CAIPTestResult(
                testName = "CAIP Utils",
                passed = false,
                details = "Test failed with exception: ${e.message}",
                duration = 0
            )
        }
    }
    
    /**
     * 測試 Solana CAIP 適配器
     */
    private fun testSolanaCAIPAdapter(): List<CAIPTestResult> {
        val results = mutableListOf<CAIPTestResult>()
        
        results.add(testSolanaAdapterInitialization())
        results.add(testSolanaAddressValidation())
        results.add(testSolanaCAIPSupport())
        
        return results
    }
    
    private fun testSolanaAdapterInitialization(): CAIPTestResult {
        return try {
            val adapter = SolanaCAIPAdapter("devnet")
            
            var allPassed = true
            val details = mutableListOf<String>()
            
            // 檢查基本屬性
            if (adapter.chainType == MultiChainType.SOLANA) {
                details.add("✓ Chain type correct: ${adapter.chainType}")
            } else {
                details.add("✗ Chain type incorrect: expected SOLANA, got ${adapter.chainType}")
                allPassed = false
            }
            
            if (adapter.supportedNamespaces.contains("solana")) {
                details.add("✓ Solana namespace supported")
            } else {
                details.add("✗ Solana namespace not supported")
                allPassed = false
            }
            
            if (adapter.supportedAssetNamespaces.contains("slip44") && 
                adapter.supportedAssetNamespaces.contains("spl-token")) {
                details.add("✓ Asset namespaces supported: ${adapter.supportedAssetNamespaces}")
            } else {
                details.add("✗ Required asset namespaces missing")
                allPassed = false
            }
            
            CAIPTestResult(
                testName = "Solana Adapter Initialization",
                passed = allPassed,
                details = details.joinToString("\n"),
                duration = 0
            )
        } catch (e: Exception) {
            CAIPTestResult(
                testName = "Solana Adapter Initialization",
                passed = false,
                details = "Test failed with exception: ${e.message}",
                duration = 0
            )
        }
    }
    
    private fun testSolanaAddressValidation(): CAIPTestResult {
        return try {
            val adapter = SolanaCAIPAdapter("devnet")
            
            val validAddress = CAIPAddress.parse("solana:EtWTRABZaYq6iMfeYKouRu166VU2xqa1:4Qkev8aNZcqFNSRhQzwyLMFSsi94jHqE8WNVTJzTP99F").getOrThrow()
            val invalidAddress = CAIPAddress.parse("eip155:1:0xab16a96d359ec26a11e2c2b3d8f8b8942d5bfcdb").getOrThrow()
            
            var allPassed = true
            val details = mutableListOf<String>()
            
            // 測試有效地址
            val validResult = adapter.validateAddressCAIP(validAddress)
            when (validResult) {
                is Result.Success -> {
                    if (validResult.data.isValid) {
                        details.add("✓ Valid Solana address accepted")
                    } else {
                        details.add("✗ Valid Solana address rejected: ${validResult.data.message}")
                        allPassed = false
                    }
                }
                is Result.Failure -> {
                    details.add("✗ Valid address validation failed: ${validResult.exception.message}")
                    allPassed = false
                }
                is Result.Loading -> {
                    details.add("⏳ Valid address validation still loading")
                    allPassed = false
                }
            }
            
            // 測試無效地址
            val invalidResult = adapter.validateAddressCAIP(invalidAddress)
            when (invalidResult) {
                is Result.Success -> {
                    if (!invalidResult.data.isValid) {
                        details.add("✓ Invalid address correctly rejected")
                    } else {
                        details.add("✗ Invalid address incorrectly accepted")
                        allPassed = false
                    }
                }
                is Result.Failure -> {
                    // 拋出異常也算是正確處理無效地址
                    details.add("✓ Invalid address correctly failed validation")
                }
                is Result.Loading -> {
                    details.add("⏳ Invalid address validation still loading")
                    allPassed = false
                }
            }
            
            CAIPTestResult(
                testName = "Solana Address Validation",
                passed = allPassed,
                details = details.joinToString("\n"),
                duration = 0
            )
        } catch (e: Exception) {
            CAIPTestResult(
                testName = "Solana Address Validation",
                passed = false,
                details = "Test failed with exception: ${e.message}",
                duration = 0
            )
        }
    }
    
    private fun testSolanaCAIPSupport(): CAIPTestResult {
        return try {
            val adapter = SolanaCAIPAdapter("devnet")
            
            var allPassed = true
            val details = mutableListOf<String>()
            
            // 測試支援的鏈 ID
            val supportedChains = adapter.getSupportedChainIDs()
            val hasSolanaChains = supportedChains.any { it.namespace == "solana" }
            
            if (hasSolanaChains) {
                details.add("✓ Solana chain IDs supported: ${supportedChains.size} chains")
                supportedChains.forEach { chainId ->
                    details.add("  - ${chainId.toCAIPString()}")
                }
            } else {
                details.add("✗ No Solana chain IDs found")
                allPassed = false
            }
            
            CAIPTestResult(
                testName = "Solana CAIP Support",
                passed = allPassed,
                details = details.joinToString("\n"),
                duration = 0
            )
        } catch (e: Exception) {
            CAIPTestResult(
                testName = "Solana CAIP Support",
                passed = false,
                details = "Test failed with exception: ${e.message}",
                duration = 0
            )
        }
    }
    
    private fun generateTestSummary(results: List<CAIPTestResult>): String {
        val passedTests = results.filter { it.passed }
        val failedTests = results.filter { !it.passed }
        
        val summary = buildString {
            appendLine("CAIP 測試套件總結")
            appendLine("================")
            appendLine()
            appendLine("總測試數: ${results.size}")
            appendLine("通過測試: ${passedTests.size}")
            appendLine("失敗測試: ${failedTests.size}")
            appendLine("成功率: ${(passedTests.size * 100.0 / results.size).toInt()}%")
            appendLine()
            
            if (failedTests.isNotEmpty()) {
                appendLine("失敗測試詳情:")
                failedTests.forEach { test ->
                    appendLine("  ✗ ${test.testName}")
                    if (test.details.isNotEmpty()) {
                        appendLine("    ${test.details}")
                    }
                }
                appendLine()
            }
            
            appendLine("測試類別分析:")
            val categories = mapOf(
                "CAIP-2" to results.filter { it.testName.contains("CAIP-2") },
                "CAIP-10" to results.filter { it.testName.contains("CAIP-10") },
                "CAIP-19" to results.filter { it.testName.contains("CAIP-19") },
                "SDK 整合" to results.filter { it.testName.contains("Service") || it.testName.contains("Utils") },
                "Solana 適配器" to results.filter { it.testName.contains("Solana") }
            )
            
            categories.forEach { (category, tests) ->
                if (tests.isNotEmpty()) {
                    val passed = tests.count { it.passed }
                    val total = tests.size
                    appendLine("  $category: $passed/$total 通過")
                }
            }
        }
        
        return summary
    }
}

/**
 * CAIP 測試結果
 */
data class CAIPTestResult(
    val testName: String,
    val passed: Boolean,
    val details: String = "",
    val duration: Long = 0
)

/**
 * CAIP 測試套件結果
 */
data class CAIPTestResults(
    val totalTests: Int,
    val passedTests: Int,
    val failedTests: Int,
    val testResults: List<CAIPTestResult>,
    val summary: String
)

/**
 * Result 擴展函數以支援測試
 */
fun <T> Result<T>.getOrThrow(): T {
    return when (this) {
        is Result.Success -> this.data
        is Result.Failure -> throw this.exception
        is Result.Loading -> throw Exception("Still loading")
    }
}

fun <T> Result<T>.isSuccess(): Boolean {
    return this is Result.Success
}

fun <T> Result<T>.isFailure(): Boolean {
    return this is Result.Failure
}
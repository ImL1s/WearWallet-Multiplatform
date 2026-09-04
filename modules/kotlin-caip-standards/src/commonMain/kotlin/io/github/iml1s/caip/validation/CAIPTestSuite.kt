package io.github.iml1s.caip.validation

import io.github.iml1s.caip.core.*
import io.github.iml1s.caip.model.*
import io.github.iml1s.caip.adapter.SolanaCAIPAdapter

/**
 * CAIP Standardization Test Suite
 *
 * Validates the correctness and compatibility of CAIP standard implementations.
 * This test suite can be run to verify that all CAIP parsing, conversion,
 * and validation functions work correctly.
 */
class CAIPTestSuite {

    private val caipService = CAIPService()

    /**
     * Run the complete CAIP test suite
     */
    suspend fun runFullTestSuite(): CAIPTestResults {
        val results = mutableListOf<CAIPTestResult>()

        // CAIP-2 Chain ID tests
        results.addAll(testCAIP2ChainIDs())

        // CAIP-10 Address tests
        results.addAll(testCAIP10Addresses())

        // CAIP-19 Asset tests
        results.addAll(testCAIP19Assets())

        // SDK integration tests
        results.addAll(testSDKIntegration())

        // Solana adapter tests
        results.addAll(testSolanaCAIPAdapter())

        val totalTests = results.size
        val passedTests = results.count { it.passed }
        val failedTests = totalTests - passedTests

        return CAIPTestResults(
            totalTests = totalTests,
            passedTests = passedTests,
            failedTests = failedTests,
            testResults = results,
            summary = generateTestSummary(results)
        )
    }

    /**
     * Test CAIP-2 Chain ID standards
     */
    private fun testCAIP2ChainIDs(): List<CAIPTestResult> {
        val results = mutableListOf<CAIPTestResult>()

        results.add(testCAIP2Parsing())
        results.add(testCAIP2ChainTypeConversion())
        results.add(testCAIP2InvalidFormats())

        return results
    }

    private fun testCAIP2Parsing(): CAIPTestResult {
        return try {
            val testCases = listOf(
                "eip155:1" to ("eip155" to "1"),
                "cosmos:cosmoshub-4" to ("cosmos" to "cosmoshub-4"),
                "solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp" to ("solana" to "5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp"),
                "bip122:000000000019d6689c085ae165831e93" to ("bip122" to "000000000019d6689c085ae165831e93")
            )

            var allPassed = true
            val details = mutableListOf<String>()

            for ((input, expected) in testCases) {
                val result = CAIPChainID.parse(input)
                when (result) {
                    is CAIPResult.Success -> {
                        val chainId = result.data
                        if (chainId.namespace == expected.first && chainId.reference == expected.second) {
                            details.add("[PASS] $input parsed correctly")
                        } else {
                            details.add("[FAIL] $input parsed incorrectly: expected $expected, got ${chainId.namespace}:${chainId.reference}")
                            allPassed = false
                        }
                    }
                    is CAIPResult.Failure -> {
                        details.add("[FAIL] $input failed to parse: ${result.exception.message}")
                        allPassed = false
                    }
                    is CAIPResult.Loading -> {
                        details.add("[WAIT] $input still loading")
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

    private fun testCAIP2ChainTypeConversion(): CAIPTestResult {
        return try {
            val testCases = listOf(
                CAIPChainType.ETHEREUM to "eip155:1",
                CAIPChainType.SOLANA to "solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp",
                CAIPChainType.BITCOIN to "bip122:000000000019d6689c085ae165831e93",
                CAIPChainType.POLKADOT to "polkadot:91b171bb158e2d3848fa23a9f1c25182"
            )

            var allPassed = true
            val details = mutableListOf<String>()

            for ((chainType, expected) in testCases) {
                val chainId = CAIPChainID.fromChainType(chainType)
                val caipString = chainId.toCAIPString()

                if (caipString == expected) {
                    details.add("[PASS] $chainType -> $caipString")
                } else {
                    details.add("[FAIL] $chainType -> $caipString (expected $expected)")
                    allPassed = false
                }
            }

            CAIPTestResult(
                testName = "CAIP-2 ChainType Conversion",
                passed = allPassed,
                details = details.joinToString("\n"),
                duration = 0
            )
        } catch (e: Exception) {
            CAIPTestResult(
                testName = "CAIP-2 ChainType Conversion",
                passed = false,
                details = "Test failed with exception: ${e.message}",
                duration = 0
            )
        }
    }

    private fun testCAIP2InvalidFormats(): CAIPTestResult {
        return try {
            val invalidFormats = listOf(
                "eip155",              // Missing reference
                "eip155:1:extra",      // Extra parts
                ":1",                  // Missing namespace
                "eip155:",             // Missing reference
                ""                     // Empty string
            )

            var allPassed = true
            val details = mutableListOf<String>()

            for (invalidFormat in invalidFormats) {
                val result = CAIPChainID.parse(invalidFormat)
                when (result) {
                    is CAIPResult.Success -> {
                        details.add("[FAIL] '$invalidFormat' should have failed but passed")
                        allPassed = false
                    }
                    is CAIPResult.Failure -> {
                        details.add("[PASS] '$invalidFormat' correctly rejected")
                    }
                    is CAIPResult.Loading -> {
                        details.add("[WAIT] '$invalidFormat' still loading")
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
     * Test CAIP-10 Address standards
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
                    is CAIPResult.Success -> {
                        val address = result.data
                        val reconstructed = address.toCAIPString()
                        if (reconstructed == testCase) {
                            details.add("[PASS] $testCase parsed and reconstructed correctly")
                        } else {
                            details.add("[FAIL] $testCase reconstructed as $reconstructed")
                            allPassed = false
                        }
                    }
                    is CAIPResult.Failure -> {
                        details.add("[FAIL] $testCase failed to parse: ${result.exception.message}")
                        allPassed = false
                    }
                    is CAIPResult.Loading -> {
                        details.add("[WAIT] $testCase still loading")
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
                    is CAIPResult.Success -> {
                        if (validation.data) {
                            details.add("[PASS] ${address.toCAIPString()} validated successfully")
                        } else {
                            details.add("[FAIL] ${address.toCAIPString()} validation failed")
                            allPassed = false
                        }
                    }
                    is CAIPResult.Failure -> {
                        details.add("[FAIL] ${address.toCAIPString()} validation threw exception: ${validation.exception.message}")
                        allPassed = false
                    }
                    is CAIPResult.Loading -> {
                        details.add("[WAIT] ${address.toCAIPString()} validation still loading")
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
                "0xab16a96d359ec26a11e2c2b3d8f8b8942d5bfcdb" to CAIPChainType.ETHEREUM,
                "4Qkev8aNZcqFNSRhQzwyLMFSsi94jHqE8WNVTJzTP99F" to CAIPChainType.SOLANA
            )

            var allPassed = true
            val details = mutableListOf<String>()

            for ((address, chainType) in legacyAddresses) {
                val caipAddress = CAIPAddress.fromAddress(address, chainType)
                val extractedAddress = caipAddress.address

                if (extractedAddress == address) {
                    details.add("[PASS] Legacy $chainType address converted correctly")
                } else {
                    details.add("[FAIL] Legacy $chainType address conversion failed: $address -> $extractedAddress")
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
     * Test CAIP-19 Asset standards
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
                    is CAIPResult.Success -> {
                        val asset = result.data
                        val reconstructed = asset.toCAIPString()
                        if (reconstructed == testCase) {
                            details.add("[PASS] $testCase parsed correctly")
                        } else {
                            details.add("[FAIL] $testCase reconstructed as $reconstructed")
                            allPassed = false
                        }
                    }
                    is CAIPResult.Failure -> {
                        details.add("[FAIL] $testCase failed to parse: ${result.exception.message}")
                        allPassed = false
                    }
                    is CAIPResult.Loading -> {
                        details.add("[WAIT] $testCase still loading")
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
                CAIPChainType.ETHEREUM,
                CAIPChainType.SOLANA,
                CAIPChainType.BITCOIN
            )

            var allPassed = true
            val details = mutableListOf<String>()

            for (chainType in chainTypes) {
                val nativeAsset = CAIPAsset.createNativeAsset(chainType)
                if (nativeAsset.isNativeToken()) {
                    details.add("[PASS] Native asset created for $chainType: ${nativeAsset.toCAIPString()}")
                } else {
                    details.add("[FAIL] Native asset creation failed for $chainType")
                    allPassed = false
                }
            }

            // Test ERC20 creation
            val erc20Asset = CAIPAsset.createERC20Asset(
                "0xa0b86a33e6776bb5b4e8a8e7b4a9b23ef4b50c6b",
                CAIPChainType.ETHEREUM
            )
            if (erc20Asset.isERC20Token()) {
                details.add("[PASS] ERC20 asset created: ${erc20Asset.toCAIPString()}")
            } else {
                details.add("[FAIL] ERC20 asset creation failed")
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
            val nativeAsset = CAIPAsset.createNativeAsset(CAIPChainType.ETHEREUM)
            val erc20Asset = CAIPAsset.createERC20Asset("0xA0b86a33E6776BB5b4E8A8E7B4A9b23eF4b50c6B", CAIPChainType.ETHEREUM)
            val nftAsset = CAIPAsset.createNFTAsset("0x06012c8cf97bead5deae237070f9587f8e7a266d", "771769", CAIPChainType.ETHEREUM)

            var allPassed = true
            val details = mutableListOf<String>()

            // Test asset type detection
            if (nativeAsset.isNativeToken() && !nativeAsset.isERC20Token() && !nativeAsset.isNFT()) {
                details.add("[PASS] Native asset type detection correct")
            } else {
                details.add("[FAIL] Native asset type detection failed")
                allPassed = false
            }

            if (!erc20Asset.isNativeToken() && erc20Asset.isERC20Token() && !erc20Asset.isNFT()) {
                details.add("[PASS] ERC20 asset type detection correct")
            } else {
                details.add("[FAIL] ERC20 asset type detection failed")
                allPassed = false
            }

            if (!nftAsset.isNativeToken() && !nftAsset.isERC20Token() && nftAsset.isNFT()) {
                details.add("[PASS] NFT asset type detection correct")
            } else {
                details.add("[FAIL] NFT asset type detection failed")
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
     * Test SDK integration
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
                        details.add("[PASS] '$testString' parsed and validated successfully")
                    }
                    else -> {
                        details.add("[FAIL] '$testString' failed validation")
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

            val caipAddresses = CAIPUtils.convertAddressesToCAIP(addresses, CAIPChainType.ETHEREUM)

            var allPassed = true
            val details = mutableListOf<String>()

            if (caipAddresses.size == addresses.size) {
                details.add("[PASS] Address batch conversion successful: ${caipAddresses.size} addresses")

                caipAddresses.forEachIndexed { index, caipAddress ->
                    if (caipAddress.address == addresses[index]) {
                        details.add("  [PASS] ${addresses[index]} -> ${caipAddress.toCAIPString()}")
                    } else {
                        details.add("  [FAIL] Address conversion mismatch")
                        allPassed = false
                    }
                }
            } else {
                details.add("[FAIL] Address batch conversion failed: expected ${addresses.size}, got ${caipAddresses.size}")
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
     * Test Solana CAIP adapter
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

            if (adapter.chainType == CAIPChainType.SOLANA) {
                details.add("[PASS] Chain type correct: ${adapter.chainType}")
            } else {
                details.add("[FAIL] Chain type incorrect: expected SOLANA, got ${adapter.chainType}")
                allPassed = false
            }

            if (adapter.supportedNamespaces.contains("solana")) {
                details.add("[PASS] Solana namespace supported")
            } else {
                details.add("[FAIL] Solana namespace not supported")
                allPassed = false
            }

            if (adapter.supportedAssetNamespaces.contains("slip44") &&
                adapter.supportedAssetNamespaces.contains("spl-token")) {
                details.add("[PASS] Asset namespaces supported: ${adapter.supportedAssetNamespaces}")
            } else {
                details.add("[FAIL] Required asset namespaces missing")
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

            // Test valid address
            val validResult = adapter.validateAddressCAIP(validAddress)
            when (validResult) {
                is CAIPResult.Success -> {
                    if (validResult.data.isValid) {
                        details.add("[PASS] Valid Solana address accepted")
                    } else {
                        details.add("[FAIL] Valid Solana address rejected: ${validResult.data.message}")
                        allPassed = false
                    }
                }
                is CAIPResult.Failure -> {
                    details.add("[FAIL] Valid address validation failed: ${validResult.exception.message}")
                    allPassed = false
                }
                is CAIPResult.Loading -> {
                    details.add("[WAIT] Valid address validation still loading")
                    allPassed = false
                }
            }

            // Test invalid address (wrong chain)
            val invalidResult = adapter.validateAddressCAIP(invalidAddress)
            when (invalidResult) {
                is CAIPResult.Success -> {
                    if (!invalidResult.data.isValid) {
                        details.add("[PASS] Invalid address correctly rejected")
                    } else {
                        details.add("[FAIL] Invalid address incorrectly accepted")
                        allPassed = false
                    }
                }
                is CAIPResult.Failure -> {
                    // Exception also means address was rejected
                    details.add("[PASS] Invalid address correctly failed validation")
                }
                is CAIPResult.Loading -> {
                    details.add("[WAIT] Invalid address validation still loading")
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

            val supportedChains = adapter.getSupportedChainIDs()
            val hasSolanaChains = supportedChains.any { it.namespace == "solana" }

            if (hasSolanaChains) {
                details.add("[PASS] Solana chain IDs supported: ${supportedChains.size} chains")
                supportedChains.forEach { chainId ->
                    details.add("  - ${chainId.toCAIPString()}")
                }
            } else {
                details.add("[FAIL] No Solana chain IDs found")
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

        return buildString {
            appendLine("CAIP Test Suite Summary")
            appendLine("=======================")
            appendLine()
            appendLine("Total Tests: ${results.size}")
            appendLine("Passed: ${passedTests.size}")
            appendLine("Failed: ${failedTests.size}")
            appendLine("Success Rate: ${(passedTests.size * 100.0 / results.size).toInt()}%")
            appendLine()

            if (failedTests.isNotEmpty()) {
                appendLine("Failed Tests:")
                failedTests.forEach { test ->
                    appendLine("  [FAIL] ${test.testName}")
                    if (test.details.isNotEmpty()) {
                        appendLine("    ${test.details}")
                    }
                }
                appendLine()
            }

            appendLine("Category Analysis:")
            val categories = mapOf(
                "CAIP-2" to results.filter { it.testName.contains("CAIP-2") },
                "CAIP-10" to results.filter { it.testName.contains("CAIP-10") },
                "CAIP-19" to results.filter { it.testName.contains("CAIP-19") },
                "SDK Integration" to results.filter { it.testName.contains("Service") || it.testName.contains("Utils") },
                "Solana Adapter" to results.filter { it.testName.contains("Solana") }
            )

            categories.forEach { (category, tests) ->
                if (tests.isNotEmpty()) {
                    val passed = tests.count { it.passed }
                    val total = tests.size
                    appendLine("  $category: $passed/$total passed")
                }
            }
        }
    }
}

/**
 * Individual test result
 */
data class CAIPTestResult(
    val testName: String,
    val passed: Boolean,
    val details: String = "",
    val duration: Long = 0
)

/**
 * Complete test suite results
 */
data class CAIPTestResults(
    val totalTests: Int,
    val passedTests: Int,
    val failedTests: Int,
    val testResults: List<CAIPTestResult>,
    val summary: String
) {
    val successRate: Double
        get() = if (totalTests > 0) passedTests.toDouble() / totalTests else 0.0

    val allPassed: Boolean
        get() = failedTests == 0
}

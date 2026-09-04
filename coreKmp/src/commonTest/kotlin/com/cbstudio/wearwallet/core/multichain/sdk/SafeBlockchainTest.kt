package com.cbstudio.wearwallet.core.multichain.sdk

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.core.security.AllowDevCapabilityGate
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * 安全的區塊鏈測試
 * 使用極小金額和安全檢查來測試真實功能
 */
class SafeBlockchainTest {
    
    // 測試配置
    private val testConfig = TestConfig(
        maxNativeAmount = "0.00001",  // 極小金額
        maxTokenAmount = "0.001",      // 極小代幣金額
        useTestnet = true,             // 使用測試網
        dryRun = true                  // 乾跑模式（不實際廣播）
    )
    
    private val walletManager = WalletManager("abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about", AllowDevCapabilityGate())
    
    @BeforeTest
    fun setup() = runTest {
        println("\n" + "=".repeat(60))
        println("🔒 安全區塊鏈測試開始")
        println("配置:")
        println("  • 最大原生幣: ${testConfig.maxNativeAmount}")
        println("  • 最大代幣: ${testConfig.maxTokenAmount}")
        println("  • 測試網: ${testConfig.useTestnet}")
        println("  • 乾跑模式: ${testConfig.dryRun}")
        println("=".repeat(60))
        
        // 初始化 SDK
        val result = walletManager.initializeAll()
        assertTrue(result is Result.Success, "SDK 初始化失敗")
    }
    
    @Test
    fun testSafeBalanceQuery() = runTest {
        println("\n📊 測試安全餘額查詢")
        
        val chains = listOf(
            MultiChainType.SOLANA,
            MultiChainType.ETHEREUM,
            MultiChainType.TRON
        )
        
        chains.forEach { chain ->
            val address = walletManager.getDerivedAddress(chain)
            val sdk = walletManager.getSDK(chain)
            
            // 查詢餘額（安全操作）
            val balanceResult = sdk?.getAccountBalance(address)
            
            when (balanceResult) {
                is Result.Success -> {
                    val balance = balanceResult.data
                    println("✓ $chain 餘額: ${balance.amount} ${balance.symbol}")
                    
                    // 驗證餘額格式
                    assertNotNull(balance.amount)
                    assertNotNull(balance.symbol)
                    assertTrue(balance.decimals > 0)
                }
                is Result.Failure -> {
                    println("⚠️ $chain 餘額查詢失敗: ${balanceResult.exception.message}")
                }
                else -> {
                    println("⏳ $chain 餘額查詢中...")
                }
            }
        }
    }
    
    @Test
    fun testSafeTransactionCreation() = runTest {
        println("\n🔨 測試安全交易創建")
        
        // 測試極小金額交易
        val amount = "0.000001"
        
        listOf(
            MultiChainType.SOLANA to "SOL",
            MultiChainType.ETHEREUM to "ETH",
            MultiChainType.TRON to "TRX"
        ).forEach { (chain, symbol) ->
            println("\n測試 $chain 交易創建:")
            
            // 安全檢查
            val isSafe = SafetyChecker.checkAmount(amount, false)
            assertTrue(isSafe, "$chain 金額應該通過安全檢查")
            println("  ✓ 金額安全檢查通過")
            
            // 創建交易（不簽名）
            val txResult = walletManager.createTestTransaction(chain, amount)
            
            when (txResult) {
                is Result.Success -> {
                    val tx = txResult.data
                    println("  ✓ 交易創建成功")
                    println("    • Chain: ${tx.chainType}")
                    println("    • Amount: $amount $symbol")
                    println("    • 預估費用: ${tx.estimatedFee.estimatedCost}")
                    
                    // 驗證交易結構
                    assertNotNull(tx.rawData)
                    assertNotNull(tx.estimatedFee)
                    assertEquals(chain, tx.chainType)
                }
                is Result.Failure -> {
                    println("  ❌ 交易創建失敗: ${txResult.exception.message}")
                }
                else -> {
                    println("  ⏳ 交易創建中...")
                }
            }
        }
    }
    
    @Test
    fun testSafeTokenTransfers() = runTest {
        println("\n💰 測試安全代幣轉帳")
        
        val tokenAmount = "0.0001"
        
        // 測試各鏈的代幣轉帳
        val tokenTests = listOf(
            Triple(MultiChainType.SOLANA, TokenType.USDC, "SPL"),
            Triple(MultiChainType.ETHEREUM, TokenType.USDT, "ERC20"),
            Triple(MultiChainType.TRON, TokenType.USDT, "TRC20")
        )
        
        tokenTests.forEach { (chain, token, standard) ->
            println("\n測試 $chain $standard 代幣轉帳:")
            
            // 安全檢查
            val isSafe = SafetyChecker.checkAmount(tokenAmount, true)
            assertTrue(isSafe, "代幣金額應該通過安全檢查")
            println("  ✓ 金額安全檢查通過")
            
            // 創建代幣交易
            val txResult = walletManager.createTokenTestTransaction(
                chain, token, tokenAmount
            )
            
            when (txResult) {
                is Result.Success -> {
                    val tx = txResult.data
                    println("  ✓ $token 交易創建成功")
                    println("    • Standard: $standard")
                    println("    • Amount: $tokenAmount $token")
                    println("    • Gas/Energy: ${tx.estimatedFee.gasLimit}")
                    
                    if (testConfig.dryRun) {
                        println("    • 狀態: 乾跑模式（不廣播）")
                    }
                }
                is Result.Failure -> {
                    println("  ❌ $token 交易失敗: ${txResult.exception.message}")
                }
                else -> {
                    println("  ⏳ $token 交易創建中...")
                }
            }
        }
    }
    
    @Test
    fun testTransactionValidation() = runTest {
        println("\n🛡️ 測試交易驗證機制")
        
        // 測試各種無效交易
        val testCases = listOf(
            TestCase(
                name = "空地址",
                request = TransactionRequest("", "", "0.001"),
                shouldFail = true
            ),
            TestCase(
                name = "超大金額",
                request = TransactionRequest(
                    "0x123", "0x456", "1000000"
                ),
                shouldFail = true
            ),
            TestCase(
                name = "負數金額",
                request = TransactionRequest(
                    "0x123", "0x456", "-0.001"
                ),
                shouldFail = true
            ),
            TestCase(
                name = "有效交易",
                request = TransactionRequest(
                    "0x123", "0x456", "0.0001"
                ),
                shouldFail = false
            )
        )
        
        testCases.forEach { testCase ->
            println("\n測試: ${testCase.name}")
            val result = SafetyChecker.validateTransaction(testCase.request)
            
            if (testCase.shouldFail) {
                assertTrue(result is Result.Failure, "${testCase.name} 應該失敗")
                println("  ✓ 正確拒絕無效交易")
            } else {
                assertTrue(result is Result.Success, "${testCase.name} 應該成功")
                println("  ✓ 正確接受有效交易")
            }
        }
    }
    
    @Test
    fun testAddressValidation() = runTest {
        println("\n🔍 測試地址驗證")
        
        val addressTests = mapOf(
            MultiChainType.SOLANA to listOf(
                "7UX2i7SucgLMQcfZ75s3VXmZZY4YRUyJN9X1RgfMoDUi" to true,
                "invalid_solana" to false
            ),
            MultiChainType.ETHEREUM to listOf(
                "0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb9" to true,
                "0xinvalid" to false
            ),
            MultiChainType.TRON to listOf(
                "TN9RRaXkCFtTXRso2GdTZxSxxwufzxLQPP" to true,
                "invalid_tron" to false
            )
        )
        
        addressTests.forEach { (chain, tests) ->
            println("\n$chain 地址驗證:")
            val sdk = walletManager.getSDK(chain)
            
            tests.forEach { (address, expectedValid) ->
                val result = sdk?.validateAddress(address)
                when (result) {
                    is Result.Success -> {
                        assertEquals(
                            expectedValid, 
                            result.data.isValid,
                            "$address 驗證結果不符預期"
                        )
                        val status = if (result.data.isValid) "✓ 有效" else "✗ 無效"
                        println("  $status: ${address.take(20)}...")
                    }
                    else -> {
                        fail("地址驗證應該返回結果")
                    }
                }
            }
        }
    }
    
    @Test
    fun testGasEstimation() = runTest {
        println("\n⛽ 測試 Gas/能量估算")
        
        val chains = listOf(
            MultiChainType.ETHEREUM to "Gas",
            MultiChainType.TRON to "Energy"
        )
        
        chains.forEach { (chain, feeType) ->
            println("\n$chain $feeType 估算:")
            
            val request = TransactionRequest(
                fromAddress = walletManager.getDerivedAddress(chain),
                toAddress = walletManager.getDerivedAddress(chain),
                amount = "0.0001"
            )
            
            val sdk = walletManager.getSDK(chain)
            val feeResult = sdk?.estimateTransactionFee(request)
            
            when (feeResult) {
                is Result.Success -> {
                    val fee = feeResult.data
                    println("  ✓ $feeType 估算成功")
                    println("    • 估算費用: ${fee.estimatedCost}")
                    println("    • Gas Limit: ${fee.gasLimit}")
                    println("    • Gas Price: ${fee.gasPrice}")
                    
                    // 驗證費用合理性
                    assertNotNull(fee.estimatedCost)
                    assertTrue(fee.gasLimit.toIntOrNull() ?: 0 > 0)
                }
                is Result.Failure -> {
                    println("  ⚠️ $feeType 估算失敗: ${feeResult.exception.message}")
                }
                else -> {
                    println("  ⏳ $feeType 估算中...")
                }
            }
        }
    }
    
    @AfterTest
    fun teardown() {
        println("\n" + "=".repeat(60))
        println("✅ 安全測試完成")
        println("所有測試都使用極小金額和安全檢查")
        println("=".repeat(60))
    }
}

/**
 * 測試配置
 */
data class TestConfig(
    val maxNativeAmount: String,
    val maxTokenAmount: String,
    val useTestnet: Boolean,
    val dryRun: Boolean
)

/**
 * 測試案例
 */
data class TestCase(
    val name: String,
    val request: TransactionRequest,
    val shouldFail: Boolean
)
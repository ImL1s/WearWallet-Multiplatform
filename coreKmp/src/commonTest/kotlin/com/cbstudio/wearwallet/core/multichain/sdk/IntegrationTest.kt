package com.cbstudio.wearwallet.core.multichain.sdk

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import kotlinx.coroutines.test.runTest
import kotlin.test.*

import com.cbstudio.wearwallet.core.security.AllowDevCapabilityGate

/**
 * 整合測試 - 測試完整的使用流程
 */
class IntegrationTest {
    
    private val walletManager = WalletManager("abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about", AllowDevCapabilityGate())
    
    @Test
    fun testCompleteFlow() = runTest {
        println("\n" + "=".repeat(60))
        println("🚀 WearWallet 區塊鏈 SDK 整合測試")
        println("=".repeat(60))
        
        // Step 1: 初始化所有 SDK
        println("\n📌 Step 1: 初始化所有 SDK")
        val initResult = walletManager.initializeAll()
        assertTrue(initResult is Result.Success, "初始化應該成功")
        println("✅ 所有 SDK 初始化成功")
        
        // Step 2: 檢查所有鏈的餘額
        println("\n📌 Step 2: 檢查所有鏈的餘額")
        val balances = walletManager.checkAllBalances()
        balances.forEach { (chain, balance) ->
            println("  $chain: $balance")
        }
        assertTrue(balances.isNotEmpty(), "應該有餘額資訊")
        
        // Step 3: 測試原生幣轉帳
        println("\n📌 Step 3: 測試原生幣轉帳交易創建")
        testNativeTransfers()
        
        // Step 4: 測試代幣轉帳
        println("\n📌 Step 4: 測試代幣轉帳交易創建")
        testTokenTransfers()
        
        // Step 5: 測試交易建構器
        println("\n📌 Step 5: 測試交易建構器")
        testTransactionBuilder()
        
        // Step 6: 測試安全檢查
        println("\n📌 Step 6: 測試安全檢查機制")
        testSafetyChecker()
        
        println("\n" + "=".repeat(60))
        println("✅ 整合測試全部通過！")
        println("=".repeat(60))
    }
    
    private suspend fun testNativeTransfers() {
        val chains = listOf(
            MultiChainType.SOLANA,
            MultiChainType.ETHEREUM,
            MultiChainType.TRON
        )
        
        chains.forEach { chain ->
            val tx = walletManager.createTestTransaction(chain, "0.0001")
            assertTrue(tx is Result.Success, "$chain 原生幣交易應該創建成功")
            val unsignedTx = (tx as Result.Success).data
            assertNotNull(unsignedTx.estimatedFee, "應該有手續費估算")
            println("  ✓ $chain 原生幣交易創建成功")
        }
    }
    
    private suspend fun testTokenTransfers() {
        // Solana SPL Token
        val solanaUSDC = walletManager.createTokenTestTransaction(
            MultiChainType.SOLANA,
            TokenType.USDC,
            "0.001"
        )
        assertTrue(solanaUSDC is Result.Success, "Solana USDC 交易應該創建成功")
        println("  ✓ Solana SPL Token (USDC) 交易創建成功")
        
        // Ethereum ERC20
        val ethUSDT = walletManager.createTokenTestTransaction(
            MultiChainType.ETHEREUM,
            TokenType.USDT,
            "0.001"
        )
        assertTrue(ethUSDT is Result.Success, "Ethereum USDT 交易應該創建成功")
        println("  ✓ Ethereum ERC20 (USDT) 交易創建成功")
        
        // TRON TRC20
        val tronUSDT = walletManager.createTokenTestTransaction(
            MultiChainType.TRON,
            TokenType.USDT,
            "0.001"
        )
        assertTrue(tronUSDT is Result.Success, "TRON USDT 交易應該創建成功")
        println("  ✓ TRON TRC20 (USDT) 交易創建成功")
    }
    
    private suspend fun testTransactionBuilder() {
        val builder = TransactionBuilder(walletManager)
        
        // 測試鏈式調用
        val tx = builder
            .chain(MultiChainType.ETHEREUM)
            .amount("0.0005")
            .token(TokenType.USDC)
            .build()
        
        assertTrue(tx is Result.Success, "建構器應該成功創建交易")
        println("  ✓ TransactionBuilder 鏈式調用成功")
    }
    
    private fun testSafetyChecker() {
        // 測試安全金額
        assertTrue(SafetyChecker.checkAmount("0.001", false), "小額應該通過")
        assertTrue(SafetyChecker.checkAmount("0.01", false), "最大額應該通過")
        assertFalse(SafetyChecker.checkAmount("1.0", false), "大額不應該通過")
        println("  ✓ 金額安全檢查正常")
        
        // 測試交易驗證
        val validRequest = TransactionRequest(
            fromAddress = "0x123",
            toAddress = "0x456",
            amount = "0.001"
        )
        assertTrue(
            SafetyChecker.validateTransaction(validRequest) is Result.Success,
            "有效交易應該通過驗證"
        )
        
        val invalidRequest = TransactionRequest(
            fromAddress = "0x123",
            toAddress = "0x456",
            amount = "100.0" // 太大
        )
        assertTrue(
            SafetyChecker.validateTransaction(invalidRequest) is Result.Failure,
            "無效交易不應該通過驗證"
        )
        println("  ✓ 交易驗證機制正常")
    }
    
    @Test
    fun testRealScenario() = runTest {
        println("\n📱 模擬真實使用場景")
        println("-".repeat(40))
        
        // 場景 1: 用戶查看餘額
        println("\n場景 1: 用戶查看餘額")
        val balances = walletManager.checkAllBalances()
        println("您的資產:")
        balances.forEach { (chain, balance) ->
            println("  • $chain: $balance")
        }
        
        // 場景 2: 用戶想轉帳 USDC
        println("\n場景 2: 轉帳 0.5 USDC (Ethereum)")
        val builder = TransactionBuilder(walletManager)
        val usdcTx = builder
            .chain(MultiChainType.ETHEREUM)
            .token(TokenType.USDC)
            .amount("0.5")
            .build()
        
        when (usdcTx) {
            is Result.Success -> {
                val tx = usdcTx.data
                println("  交易已準備:")
                println("  • 預估手續費: ${tx.estimatedFee.estimatedCost} ETH")
                println("  • Gas Limit: ${tx.estimatedFee.gasLimit}")
                println("  • 狀態: 待簽名")
            }
            is Result.Failure -> {
                println("  ❌ 交易創建失敗: ${usdcTx.exception.message}")
            }
            is Result.Loading -> {
                println("  ⏳ 交易創建中...")
            }
        }
        
        // 場景 3: 安全警告
        println("\n場景 3: 安全警告測試")
        val largeTx = builder
            .chain(MultiChainType.SOLANA)
            .amount("10.0") // 太大！
            .build()
        
        // 在實際使用中，這應該被 SafetyChecker 攔截
        if (!SafetyChecker.checkAmount("10.0", false)) {
            println("  ⚠️ 警告: 金額過大，已自動阻止")
            println("  最大允許金額: 0.01 SOL")
        }
        
        println("\n✅ 真實場景測試完成")
    }
    
    @Test
    fun testErrorRecovery() = runTest {
        println("\n🔧 測試錯誤恢復機制")
        
        // 測試無效地址
        val sdk = walletManager.getSDK(MultiChainType.ETHEREUM) as? RealEthereumSDK
        assertNotNull(sdk)
        
        val invalidAddressResult = sdk.validateAddress("not_an_address")
        assertTrue(invalidAddressResult is Result.Success)
        assertFalse((invalidAddressResult as Result.Success).data.isValid)
        println("✓ 無效地址檢測正常")
        
        // 測試空交易
        val emptyTx = TransactionRequest(
            fromAddress = "",
            toAddress = "",
            amount = ""
        )
        val validationResult = SafetyChecker.validateTransaction(emptyTx)
        assertTrue(validationResult is Result.Failure)
        println("✓ 空交易拒絕正常")
        
        // 測試極大金額
        val hugeTx = TransactionRequest(
            fromAddress = "0x123",
            toAddress = "0x456",
            amount = "999999999"
        )
        val hugeValidation = SafetyChecker.validateTransaction(hugeTx)
        assertTrue(hugeValidation is Result.Failure)
        println("✓ 大額交易阻止正常")
        
        println("\n✅ 錯誤恢復機制測試通過")
    }
    
    @Test
    fun testPerformance() = runTest {
        println("\n⚡ 效能測試")
        
        val startTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        
        // 測試 100 次地址驗證
        val sdk = walletManager.getSDK(MultiChainType.SOLANA)
        repeat(100) {
            sdk?.validateAddress("11111111111111111111111111111111")
        }
        
        val validationTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - startTime
        println("100 次地址驗證耗時: ${validationTime}ms")
        assertTrue(validationTime < 1000, "驗證應該在 1 秒內完成")
        
        // 測試並行餘額查詢
        val balanceStartTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        walletManager.checkAllBalances()
        val balanceTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - balanceStartTime
        println("3 鏈餘額查詢耗時: ${balanceTime}ms")
        
        println("\n✅ 效能測試通過")
    }
}
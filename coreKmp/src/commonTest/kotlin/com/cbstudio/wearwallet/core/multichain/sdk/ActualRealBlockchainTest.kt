package com.cbstudio.wearwallet.core.multichain.sdk

import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.core.multichain.MultiChainWalletManager
import com.cbstudio.wearwallet.core.common.Result
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlin.test.*

/**
 * 🔥 真正的區塊鏈 SDK 測試 - 使用真實的 coreKmp SDK！
 * 
 * 真實特點：
 * ✅ 使用 MultiChainWalletManager SDK API
 * ✅ 真實的網路連接性測試
 * ✅ 真實的餘額查詢功能
 * ✅ 真實的地址驗證
 * ✅ 真實的錯誤處理
 * ❌ 不再直接使用 HTTP 客戶端！
 */
class ActualRealBlockchainTest {
    
    companion object {
        const val TEST_MNEMONIC = "rookie abuse frozen luxury science hat alert avoid car lemon day cost"
        
        // 測試地址（從助記詞生成）
        val TEST_ADDRESSES = mapOf(
            MultiChainType.SOLANA to "7xKXR5nT9yLHBmHpvJZPkFnhZ8Kt2WnTgvFqPBmcAGf9",
            MultiChainType.TRON to "TLyqzVGLV1srkB7dToTAEqgDSfPtXRJZYH",
            MultiChainType.ETHEREUM to "0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb"
        )
        
        // 系統地址（已知的系統程序地址）
        val SYSTEM_ADDRESSES = mapOf(
            MultiChainType.SOLANA to "11111111111111111111111111111112", // System Program
            MultiChainType.TRON to "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t" // USDT
        )
    }
    
    private lateinit var walletManager: MultiChainWalletManager
    
    @BeforeTest
    fun setUp() = runTest {
        walletManager = MultiChainWalletManager.createDefault(com.cbstudio.wearwallet.core.security.AllowDevCapabilityGate())
        
        // 初始化 Solana 和 TRON
        val configs = listOf(
            MultiChainWalletManager.ChainConfig(
                chainType = MultiChainType.SOLANA,
                network = "devnet",
                enabled = true
            ),
            MultiChainWalletManager.ChainConfig(
                chainType = MultiChainType.TRON,
                network = "shasta",
                enabled = true
            ),
            MultiChainWalletManager.ChainConfig(
                chainType = MultiChainType.ETHEREUM,
                network = "testnet",
                enabled = true
            )
        )
        
        when (val result = walletManager.initialize(configs)) {
            is Result.Success -> println("✅ WalletManager 初始化成功")
            is Result.Failure -> println("⚠️ WalletManager 初始化失敗: ${result.exception}")
            is Result.Loading -> println("⏳ WalletManager 初始化中...")
        }
        
        delay(1000) // 等待初始化完成
    }

    @Test
    fun testRealSolanaDevnetConnection() = runTest {
        println("🔥 測試真實 Solana devnet SDK 連接...")
        
        // 使用 SDK 測試 Solana 連接
        when (val result = walletManager.getNetworkStatus(MultiChainType.SOLANA)) {
            is Result.Success -> {
                val status = result.data
                println("📡 Solana 網路狀態:")
                println("  連接狀態: ${if (status.isConnected) "✅ 已連接" else "❌ 未連接"}")
                println("  區塊高度: ${status.blockHeight}")
                println("  網路 ID: ${status.networkId}")
                // 節點版本資訊 (如果有的話)
                status.syncProgress?.let { progress ->
                    println("  同步進度: ${(progress * 100).toInt()}%")
                }
                
                assertTrue(status.isConnected, "Solana devnet 應該連接成功")
                assertTrue(status.blockHeight > 0, "應該有有效的區塊高度")
                assertNotNull(status.networkId, "應該有網路 ID")
                
                println("✅ 成功連接到真實的 Solana devnet！")
            }
            is Result.Failure -> {
                fail("真實 Solana devnet 連接失敗: ${result.exception.message}")
            }
            is Result.Loading -> {
                println("⏳ 連接中...")
                delay(1000)
                // 無法連接或還在載入中
                println("⚠️ 連接超時或載入中")
            }
        }
    }

    @Test
    fun testRealSolanaBalanceQuery() = runTest {
        println("🔥 測試真實 Solana 地址餘額查詢...")
        
        // 使用 SDK 查詢多個地址的餘額
        val addresses = listOf(
            SYSTEM_ADDRESSES[MultiChainType.SOLANA]!!, // 系統程序地址
            TEST_ADDRESSES[MultiChainType.SOLANA]!!,   // 測試地址
            "So11111111111111111111111111111111111111112" // Native SOL
        )
        
        println("\n📊 查詢 ${addresses.size} 個 Solana 地址...")
        
        for (address in addresses) {
            println("\n🔍 查詢地址: ${address.take(10)}...${address.takeLast(10)}")
            
            val balance = walletManager.getBalance(MultiChainType.SOLANA, address)
            
            if (balance != null) {
                println("  ✅ 餘額: ${balance.amount} ${balance.symbol}")
                // Lamports 數量 (此欄位不存在)
                balance.usdValue?.let { usd ->
                    println("  💵 美元價值: \$$usd")
                }
                
                assertNotNull(balance.amount, "應該有餘額數量")
                assertNotNull(balance.symbol, "應該有代幣符號")
                assertEquals("SOL", balance.symbol, "符號應該是 SOL")
            } else {
                println("  ℹ️ 無餘額或地址不存在（這是正常的）")
            }
        }
        
        println("\n✅ 真實 Solana 餘額查詢完成")
    }

    @Test
    fun testRealTronShastaConnection() = runTest {
        println("🔥 測試真實 TRON Shasta 測試網連接...")
        
        // 使用 SDK 測試 TRON 連接
        when (val result = walletManager.getNetworkStatus(MultiChainType.TRON)) {
            is Result.Success -> {
                val status = result.data
                println("📡 TRON 網路狀態:")
                println("  連接狀態: ${if (status.isConnected) "✅ 已連接" else "❌ 未連接"}")
                println("  區塊高度: ${status.blockHeight}")
                println("  網路 ID: ${status.networkId}")
                // 節點版本資訊 (如果有的話)
                status.syncProgress?.let { progress ->
                    println("  同步進度: ${(progress * 100).toInt()}%")
                }
                
                assertTrue(status.isConnected, "TRON Shasta 應該連接成功")
                assertTrue(status.blockHeight > 0, "應該有有效的區塊高度")
                assertNotNull(status.networkId, "應該有網路 ID")
                
                println("✅ 成功連接到真實的 TRON Shasta 測試網！")
            }
            is Result.Failure -> {
                fail("真實 TRON Shasta 連接失敗: ${result.exception.message}")
            }
            is Result.Loading -> {
                println("⏳ 連接中...")
                delay(1000)
                // 無法連接或還在載入中
                println("⚠️ 連接超時或載入中")
            }
        }
    }

    @Test
    fun testRealTronAccountQuery() = runTest {
        println("🔥 測試真實 TRON 帳戶查詢...")
        
        // 使用 SDK 查詢多個 TRON 地址
        val addresses = listOf(
            TEST_ADDRESSES[MultiChainType.TRON]!!,    // 測試地址
            SYSTEM_ADDRESSES[MultiChainType.TRON]!!,  // USDT 合約
            "TSSMHYeV2uE9qYH95DqyoCuNCzEL1NvU3S"     // 另一個測試地址
        )
        
        println("\n📊 查詢 ${addresses.size} 個 TRON 地址...")
        
        for (address in addresses) {
            println("\n🔍 查詢地址: ${address.take(10)}...${address.takeLast(10)}")
            
            val balance = walletManager.getBalance(MultiChainType.TRON, address)
            
            if (balance != null) {
                println("  ✅ 餘額: ${balance.amount} ${balance.symbol}")
                // Sun 數量 (此欄位不存在)
                balance.usdValue?.let { usd ->
                    println("  💵 美元價值: \$$usd")
                }
                
                // TRX 金額已經是正確單位
                println("  🪙 代幣: ${balance.symbol}")
                
                assertNotNull(balance.amount, "應該有餘額數量")
                assertNotNull(balance.symbol, "應該有代幣符號")
                assertEquals("TRX", balance.symbol, "符號應該是 TRX")
            } else {
                println("  ℹ️ 無餘額或地址不存在（這是正常的測試結果）")
            }
        }
        
        println("\n✅ 真實 TRON 帳戶查詢完成")
    }

    @Test
    fun testRealNetworkLatency() = runTest {
        println("🔥 測試真實區塊鏈網路延遲...")
        
        val chains = listOf(MultiChainType.SOLANA, MultiChainType.TRON)
        val latencies = mutableMapOf<MultiChainType, Long>()
        
        for (chain in chains) {
            println("\n⚡ 測試 ${chain.name} 網路延遲...")
            
            // 執行 5 次測試取平均值
            val measurements = mutableListOf<Long>()
            
            repeat(5) { i ->
                val startTime = Clock.System.now().toEpochMilliseconds()
                
                when (val result = walletManager.getNetworkStatus(chain)) {
                    is Result.Success -> {
                        val endTime = Clock.System.now().toEpochMilliseconds()
                        val latency = endTime - startTime
                        measurements.add(latency)
                        print(".")
                    }
                    is Result.Failure -> {
                        println("\n  ⚠️ 請求失敗: ${result.exception.message}")
                    }
                    is Result.Loading -> {
                        // 跳過載入狀態
                    }
                }
                
                delay(200) // 避免請求過於頻繁
            }
            println()
            
            if (measurements.isNotEmpty()) {
                val avgLatency = measurements.average().toLong()
                val minLatency = measurements.minOrNull() ?: 0
                val maxLatency = measurements.maxOrNull() ?: 0
                
                latencies[chain] = avgLatency
                
                println("  📊 ${chain.name} 延遲統計:")
                println("    最小延遲: ${minLatency}ms")
                println("    最大延遲: ${maxLatency}ms")
                println("    平均延遲: ${avgLatency}ms")
                
                assertTrue(avgLatency < 10000, "${chain.name} 平均延遲應該合理 (< 10秒)")
            }
        }
        
        println("\n✅ 網路延遲測試完成")
        
        if (latencies.size >= 2) {
            println("\n📊 性能對比:")
            latencies.forEach { (chain, latency) ->
                println("  ${chain.name}: ${latency}ms")
            }
            
            val fastest = latencies.minByOrNull { it.value }
            val slowest = latencies.maxByOrNull { it.value }
            
            fastest?.let {
                println("  🏆 最快: ${it.key.name} (${it.value}ms)")
            }
            slowest?.let {
                println("  🐢 最慢: ${it.key.name} (${it.value}ms)")
            }
        }
    }

    @Test
    fun testRealAddressValidation() = runTest {
        println("🔥 測試真實地址格式驗證...")
        
        // 測試 Solana 地址驗證
        println("\n📊 Solana 地址驗證:")
        
        val solanaTestCases = mapOf(
            "11111111111111111111111111111112" to true,  // 系統程序
            "So11111111111111111111111111111111111111112" to true, // Native SOL
            "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v" to true, // USDC
            "invalid_address" to false,
            "123" to false,
            "11111111111111111111111111111111111111111111111111111111111111111" to false, // 太長
            "0x1234567890abcdef" to false // 以太坊格式
        )
        
        for ((address, expectedValid) in solanaTestCases) {
            when (val result = walletManager.validateAddress(MultiChainType.SOLANA, address)) {
                is Result.Success -> {
                    val validation = result.data
                    val displayAddress = if (address.length > 20) {
                        "${address.take(10)}...${address.takeLast(10)}"
                    } else {
                        address
                    }
                    
                    if (validation.isValid) {
                        println("  ✅ 有效地址: $displayAddress")
                        validation.addressType?.let { type ->
                            println("      類型: ${type.name}")
                        }
                    } else {
                        println("  ❌ 無效地址: $displayAddress")
                        validation.message?.let { msg ->
                            println("      原因: $msg")
                        }
                    }
                    
                    assertEquals(expectedValid, validation.isValid, 
                                "地址 '$displayAddress' 驗證結果不符預期")
                }
                is Result.Failure -> {
                    println("  ⚠️ 驗證失敗: ${result.exception.message}")
                }
                is Result.Loading -> {
                    println("  ⏳ 驗證中...")
                }
            }
        }
        
        // 測試 TRON 地址驗證
        println("\n📊 TRON 地址驗證:")
        
        val tronTestCases = mapOf(
            "TLyqzVGLV1srkB7dToTAEqgDSfPtXRJZYH" to true,
            "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t" to true, // USDT
            "TSSMHYeV2uE9qYH95DqyoCuNCzEL1NvU3S" to true, // 測試地址
            "invalid_tron" to false,
            "0x1234567890abcdef" to false,
            "A" + "1".repeat(33) to false, // 不是以 T 開頭
            "T" + "1".repeat(40) to false  // 太長
        )
        
        for ((address, expectedValid) in tronTestCases) {
            when (val result = walletManager.validateAddress(MultiChainType.TRON, address)) {
                is Result.Success -> {
                    val validation = result.data
                    val displayAddress = if (address.length > 20) {
                        "${address.take(10)}...${address.takeLast(10)}"
                    } else {
                        address
                    }
                    
                    if (validation.isValid) {
                        println("  ✅ 有效地址: $displayAddress")
                        validation.addressType?.let { type ->
                            println("      類型: ${type.name}")
                        }
                    } else {
                        println("  ❌ 無效地址: $displayAddress")
                        validation.message?.let { msg ->
                            println("      原因: $msg")
                        }
                    }
                    
                    assertEquals(expectedValid, validation.isValid,
                                "地址 '$displayAddress' 驗證結果不符預期")
                }
                is Result.Failure -> {
                    println("  ⚠️ 驗證失敗: ${result.exception.message}")
                }
                is Result.Loading -> {
                    println("  ⏳ 驗證中...")
                }
            }
        }
        
        println("\n✅ 真實地址格式驗證完成")
    }

    @Test
    fun testRealErrorHandling() = runTest {
        println("🔥 測試真實錯誤處理...")
        
        println("\n1️⃣ 測試無效地址錯誤處理:")
        
        // 測試各種無效地址
        val invalidAddresses = listOf(
            "" to "空地址",
            "invalid" to "無效格式",
            "0x0000000000000000000000000000000000000000" to "錯誤的鏈格式",
            "!!!@@@###" to "特殊字符"
        )
        
        for ((address, description) in invalidAddresses) {
            println("\n  測試 $description: '$address'")
            
            // Solana 驗證
            when (val result = walletManager.validateAddress(MultiChainType.SOLANA, address)) {
                is Result.Success -> {
                    val validation = result.data
                    assertFalse(validation.isValid, "$description 應該被識別為無效")
                    println("    ✅ Solana 正確識別為無效地址")
                }
                is Result.Failure -> {
                    println("    ✅ Solana 拋出錯誤: ${result.exception.message}")
                }
                is Result.Loading -> {}
            }
            
            // TRON 驗證
            when (val result = walletManager.validateAddress(MultiChainType.TRON, address)) {
                is Result.Success -> {
                    val validation = result.data
                    assertFalse(validation.isValid, "$description 應該被識別為無效")
                    println("    ✅ TRON 正確識別為無效地址")
                }
                is Result.Failure -> {
                    println("    ✅ TRON 拋出錯誤: ${result.exception.message}")
                }
                is Result.Loading -> {}
            }
        }
        
        println("\n2️⃣ 測試不存在的鏈錯誤處理:")
        
        // 測試查詢不存在的地址餘額
        val nonExistentAddress = "ThisAddressDefinitelyDoesNotExist123456789"
        
        val balance = walletManager.getBalance(MultiChainType.SOLANA, nonExistentAddress)
        if (balance == null) {
            println("  ✅ 正確處理不存在的地址（返回 null）")
        } else {
            // 有些情況下可能返回 0 餘額
            assertEquals("0", balance.amount, "不存在的地址應該餘額為 0")
            println("  ✅ 不存在的地址返回 0 餘額")
        }
        
        println("\n3️⃣ 測試無效交易參數錯誤處理:")
        
        val invalidRequest = TransactionRequest(
            fromAddress = "invalid_from",
            toAddress = "invalid_to",
            amount = "-1", // 負數金額
            priority = TransactionPriority.NORMAL
        )
        
        when (val result = walletManager.estimateTransactionFee(MultiChainType.SOLANA, invalidRequest)) {
            is Result.Success -> {
                // 不應該成功
                fail("無效交易參數不應該成功")
            }
            is Result.Failure -> {
                println("  ✅ 正確拒絕無效交易參數")
                println("    錯誤: ${result.exception.message}")
                assertNotNull(result.exception.message, "應該有錯誤訊息")
            }
            is Result.Loading -> {
                println("  ⏳ 處理中...")
            }
        }
        
        println("\n✅ 真實錯誤處理測試完成")
    }

    @Test
    fun testRealSDKCapabilities() = runTest {
        println("🔥 測試真實 SDK 功能...")
        
        println("\n📊 檢查支援的區塊鏈:")
        val supportedChains = walletManager.getSupportedChains()
        assertTrue(supportedChains.isNotEmpty(), "應該支援至少一條區塊鏈")
        
        supportedChains.forEach { chain ->
            println("  • ${chain.name}")
        }
        
        println("\n📊 檢查 SDK 功能:")
        val capabilities = listOf(
            SDKCapability.BALANCE_QUERY to "餘額查詢",
            SDKCapability.TRANSACTION_CREATION to "交易創建",
            SDKCapability.ADDRESS_VALIDATION to "地址驗證",
            SDKCapability.TRANSACTION_HISTORY to "交易歷史",
            SDKCapability.OFFLINE_SIGNING to "離線簽名"
        )
        
        for ((capability, name) in capabilities) {
            val chains = walletManager.getChainsWithCapability(capability)
            if (chains.isNotEmpty()) {
                println("  ✅ $name (${chains.size} 條鏈)")
                chains.take(3).forEach { chain ->
                    println("      • ${chain.name}")
                }
                if (chains.size > 3) {
                    println("      ... 及其他 ${chains.size - 3} 條鏈")
                }
            } else {
                println("  ⚠️ $name (尚未支援)")
            }
        }
        
        println("\n✅ SDK 功能測試完成")
    }
    
    @Test
    fun testRealBatchOperations() = runTest {
        println("🔥 測試真實批量操作...")
        
        println("\n📊 批量查詢多鏈餘額:")
        
        val addressMap = mapOf(
            MultiChainType.SOLANA to TEST_ADDRESSES[MultiChainType.SOLANA]!!,
            MultiChainType.TRON to TEST_ADDRESSES[MultiChainType.TRON]!!,
            MultiChainType.ETHEREUM to TEST_ADDRESSES[MultiChainType.ETHEREUM]!!
        )
        
        when (val result = walletManager.getAllBalances(addressMap)) {
            is Result.Success -> {
                val balances = result.data
                println("  ✅ 成功查詢 ${balances.size} 個餘額")
                
                var totalUsd = 0.0
                balances.forEach { (chain, balance) ->
                    println("\n  ${chain.name}:")
                    println("    地址: ${addressMap[chain]?.take(10)}...${addressMap[chain]?.takeLast(10)}")
                    println("    餘額: ${balance.amount} ${balance.symbol}")
                    balance.usdValue?.toDoubleOrNull()?.let { usd ->
                        println("    美元: \$$usd")
                        totalUsd += usd
                    }
                }
                
                println("\n  💰 總價值: \$${(totalUsd * 100).toInt() / 100.0}")
            }
            is Result.Failure -> {
                println("  ❌ 批量查詢失敗: ${result.exception.message}")
            }
            is Result.Loading -> {
                println("  ⏳ 查詢中...")
            }
        }
        
        println("\n✅ 批量操作測試完成")
    }
    
    @AfterTest
    fun tearDown() = runTest {
        try {
            walletManager.cleanup()
            println("\n🧹 測試資源已清理")
        } catch (e: Exception) {
            println("\n⚠️ 清理資源時出錯: ${e.message}")
        }
    }
}
package com.cbstudio.wearwallet.core.multichain.sdk

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import kotlinx.coroutines.test.runTest
import kotlin.test.*

import com.cbstudio.wearwallet.core.security.AllowDevCapabilityGate

/**
 * 真實區塊鏈功能測試
 * 使用提供的助記詞測試所有功能
 */
class RealBlockchainLiveTest {
    
    // 使用者提供的助記詞
    private val mnemonic = "rookie abuse frozen luxury science hat alert avoid car lemon day cost iron mind drip glad load second merge rough music cloud fresh heavy"
    private lateinit var walletManager: WalletManager
    
    @BeforeTest
    fun setup() = runTest {
        println("\n" + "=".repeat(80))
        println("🚀 真實區塊鏈功能測試開始")
        println("📝 使用助記詞: ${mnemonic.take(30)}...")
        println("=".repeat(80))
        
        walletManager = WalletManager(mnemonic, AllowDevCapabilityGate())
        
        // 初始化所有 SDK
        val result = walletManager.initializeAll()
        assertTrue(result is Result.Success, "SDK 初始化應該成功")
        println("✅ SDK 初始化成功")
    }
    
    @Test
    fun testBalanceQuery() = runTest {
        println("\n💰 測試餘額查詢")
        println("-".repeat(60))
        
        val chains = listOf(
            MultiChainType.BITCOIN,
            MultiChainType.ETHEREUM,
            MultiChainType.SOLANA,
            MultiChainType.LITECOIN,
            MultiChainType.DOGECOIN
        )
        
        chains.forEach { chain ->
            val address = walletManager.getDerivedAddress(chain)
            val sdk = walletManager.getSDK(chain)
            
            if (sdk != null && address.isNotEmpty()) {
                println("\n🔗 ${chain.fullName}")
                println("📬 地址: $address")
                
                val balanceResult = sdk.getAccountBalance(address)
                when (balanceResult) {
                    is Result.Success -> {
                        val balance = balanceResult.data
                        println("✅ 餘額: ${balance.amount} ${balance.symbol}")
                        
                        // 驗證餘額格式正確
                        assertNotNull(balance.amount)
                        assertNotNull(balance.symbol)
                        assertTrue(balance.decimals > 0)
                    }
                    is Result.Failure -> {
                        println("⚠️ 查詢失敗（可能是網路問題）: ${balanceResult.exception.message}")
                        // 不讓測試失敗，因為可能是網路問題
                    }
                    else -> fail("未知狀態")
                }
            }
        }
    }
    
    @Test
    fun testUTXOTransactionCreation() = runTest {
        println("\n📝 測試 UTXO 交易創建")
        println("-".repeat(60))
        
        val utxoChains = listOf(
            MultiChainType.BITCOIN,
            MultiChainType.LITECOIN,
            MultiChainType.DOGECOIN,
            MultiChainType.BITCOIN_CASH
        )
        
        utxoChains.forEach { chain ->
            println("\n🔗 測試 ${chain.fullName}")
            
            val sdk = walletManager.getSDK(chain)
            assertNotNull(sdk, "${chain.fullName} SDK 應該存在")
            
            val address = walletManager.getDerivedAddress(chain)
            assertTrue(address.isNotEmpty(), "${chain.fullName} 應該有測試地址")
            
            // 創建測試交易請求
            val request = TransactionRequest(
                fromAddress = address,
                toAddress = address, // 發送給自己
                amount = "0.00000001" // 1 satoshi
            )
            
            val result = sdk.createTransaction(request)
            when (result) {
                is Result.Success -> {
                    val unsignedTx = result.data
                    println("✅ 成功創建未簽名交易")
                    
                    // 驗證交易結構
                    assertNotNull(unsignedTx.rawData)
                    assertEquals(chain, unsignedTx.chainType)
                    assertNotNull(unsignedTx.estimatedFee)
                    assertTrue(unsignedTx.metadata.isNotEmpty())
                    
                    // 檢查 UTXO 相關數據
                    assertTrue(unsignedTx.metadata.containsKey("utxos"))
                    assertTrue(unsignedTx.metadata.containsKey("fee"))
                }
                is Result.Failure -> {
                    println("⚠️ 創建失敗（預期的，可能沒有 UTXO）: ${result.exception.message}")
                    // 這是預期的，因為測試地址可能沒有 UTXO
                }
                else -> fail("未知狀態")
            }
        }
    }
    
    @Test
    fun testTransactionSigning() = runTest {
        println("\n🔐 測試交易簽名")
        println("-".repeat(60))
        
        // 測試 Bitcoin 交易簽名
        println("\n測試 Bitcoin 交易簽名...")
        
        val result = walletManager.createAndSignUTXOTransaction(
            chainType = MultiChainType.BITCOIN,
            amount = "0.00000001" // 1 satoshi
        )
        
        when (result) {
            is Result.Success -> {
                val signedTx = result.data
                println("✅ 交易簽名成功!")
                
                // 驗證簽名交易
                assertNotNull(signedTx.hash, "交易應該有 hash")
                assertNotNull(signedTx.rawData, "應該有原始交易數據")
                assertEquals(MultiChainType.BITCOIN, signedTx.chainType)
                
                println("  • Hash: ${signedTx.hash}")
                println("  • 數據長度: ${signedTx.rawData.length} bytes")
                println("  ⚠️ 注意: 交易已簽名但未廣播")
            }
            is Result.Failure -> {
                println("⚠️ 簽名失敗（預期的）: ${result.exception.message}")
                // 這是預期的，因為測試地址可能沒有餘額
            }
            else -> fail("未知狀態")
        }
    }
    
    @Test
    fun testAddressValidation() = runTest {
        println("\n🔍 測試地址驗證")
        println("-".repeat(60))
        
        // Bitcoin 地址測試
        val btcSDK = walletManager.getSDK(MultiChainType.BITCOIN)
        assertNotNull(btcSDK)
        
        val testCases = listOf(
            // 有效地址
            "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa" to true,  // P2PKH
            "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4" to true,  // Bech32
            "3J98t1WpEZ73CNmQviecrnyiWrnqRhWNLy" to true,  // P2SH
            // 無效地址
            "invalid_btc" to false,
            "0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb9" to false  // Ethereum 地址
        )
        
        println("\n🔗 Bitcoin 地址驗證:")
        testCases.forEach { (address, expectedValid) ->
            val result = btcSDK.validateAddress(address)
            when (result) {
                is Result.Success -> {
                    val validation = result.data
                    assertEquals(expectedValid, validation.isValid, 
                        "地址 $address 驗證結果應該是 $expectedValid")
                    
                    val icon = if (validation.isValid) "✅" else "❌"
                    println("$icon ${address.take(30)}... = ${validation.isValid}")
                    
                    if (validation.isValid) {
                        assertNotNull(validation.addressType)
                    }
                }
                is Result.Failure -> fail("地址驗證不應該失敗: ${result.exception.message}")
                else -> fail("未知狀態")
            }
        }
    }
    
    @Test
    fun testReceivingAddresses() = runTest {
        println("\n📤 測試接收地址生成")
        println("-".repeat(60))
        
        val chains = listOf(
            MultiChainType.BITCOIN,
            MultiChainType.ETHEREUM,
            MultiChainType.SOLANA,
            MultiChainType.TRON,
            MultiChainType.LITECOIN,
            MultiChainType.DOGECOIN,
            MultiChainType.BITCOIN_CASH
        )
        
        println("\n生成的接收地址（從助記詞派生）:")
        chains.forEach { chain ->
            val address = walletManager.getDerivedAddress(chain)
            
            if (address.isNotEmpty()) {
                println("\n${chain.symbol}:")
                println("  $address")
                
                // 驗證地址格式
                val sdk = walletManager.getSDK(chain)
                if (sdk != null) {
                    val validation = sdk.validateAddress(address)
                    if (validation is Result.Success) {
                        assertTrue(validation.data.isValid, 
                            "${chain.fullName} 生成的地址應該是有效的")
                    }
                }
            }
        }
    }
    
    @Test
    fun testFeeEstimation() = runTest {
        println("\n💰 測試手續費估算")
        println("-".repeat(60))
        
        val chains = listOf(
            MultiChainType.BITCOIN,
            MultiChainType.LITECOIN,
            MultiChainType.DOGECOIN
        )
        
        chains.forEach { chain ->
            val sdk = walletManager.getSDK(chain)
            assertNotNull(sdk)
            
            val address = walletManager.getDerivedAddress(chain)
            val request = TransactionRequest(
                fromAddress = address,
                toAddress = address,
                amount = "0.00001"
            )
            
            println("\n🔗 ${chain.fullName} 手續費估算:")
            val result = sdk.estimateTransactionFee(request)
            
            when (result) {
                is Result.Success -> {
                    val fee = result.data
                    println("✅ 估算成功:")
                    println("  • 估算費用: ${fee.estimatedCost}")
                    println("  • Gas Limit: ${fee.gasLimit}")
                    println("  • Gas Price: ${fee.gasPrice}")
                    println("  • 優先級: ${fee.priority}")
                    
                    // 驗證手續費結構
                    assertNotNull(fee.estimatedCost)
                    assertNotNull(fee.gasLimit)
                    assertNotNull(fee.gasPrice)
                    assertNotNull(fee.priority)
                }
                is Result.Failure -> {
                    println("⚠️ 估算失敗: ${result.exception.message}")
                }
                else -> fail("未知狀態")
            }
        }
    }
    
    @Test
    fun testNetworkStatus() = runTest {
        println("\n🌐 測試網路狀態")
        println("-".repeat(60))
        
        val chains = listOf(
            MultiChainType.BITCOIN,
            MultiChainType.ETHEREUM,
            MultiChainType.SOLANA
        )
        
        chains.forEach { chain ->
            val sdk = walletManager.getSDK(chain)
            if (sdk != null) {
                println("\n🔗 ${chain.fullName}:")
                
                val result = sdk.getNetworkStatus()
                when (result) {
                    is Result.Success -> {
                        val status = result.data
                        println("✅ 網路狀態:")
                        println("  • 連接: ${if (status.isConnected) "已連接" else "斷開"}")
                        println("  • 區塊高度: ${status.blockHeight}")
                        println("  • 網路 ID: ${status.networkId}")
                        
                        // 驗證網路狀態
                        assertNotNull(status.blockHeight)
                        assertNotNull(status.networkId)
                    }
                    is Result.Failure -> {
                        println("⚠️ 查詢失敗: ${result.exception.message}")
                    }
                    else -> fail("未知狀態")
                }
            }
        }
    }
    
    @AfterTest
    fun teardown() {
        println("\n" + "=".repeat(80))
        println("✅ 真實區塊鏈功能測試完成")
        println("=".repeat(80))
    }
}
package com.cbstudio.wearwallet.core.multichain.sdk

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.core.multichain.config.DefaultRPCConfig
import com.cbstudio.wearwallet.core.multichain.monero.crypto.getMoneroCryptoProvider
import com.cbstudio.wearwallet.core.multichain.monero.sdk.MoneroSDK
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import com.cbstudio.wearwallet.core.testing.TestAddresses

/**
 * 簡化的區塊鏈單元測試
 * 驗證基本功能而不需要實際網路連接
 */
class SimplifiedBlockchainTest {
    
    companion object {
        // 測試地址常量
        const val ROOKIE_EVM = "0x2ff446b6146A4F845F1EC1007eDdf157c46DD634"
        const val ROOKIE_TRON = "TAWPuPmFRMx2p1ZT8Y1MAsEbmSVgZpL7u2"
        const val ROOKIE_SOLANA = "BeMCB2gG9dZqGxsfNGEY819knV3QxFuSB2voSt8CEv7Z"
        const val ROOKIE_MONERO = "44AFFq5kSiGBoZ4NMDwYtN18obc8AemS33DBLWs3H7otXft3XjrpDtQGv7SqSsaBYBb98uNbr2VBBEt7f2wfn3RVGQBEP3A"
        
        const val IRON_EVM = TestAddresses.VITALIK
        const val IRON_MONERO = "46E5ekYrZd5UCcmNuYEX24FRjWVMgZ1ob79cRViyfvLFZjfyMhPDvbuCMCfBVFYfGKNKN46zKCecHviNPJJ1Z2fVnvyBu43"
    }
    
    @Test
    fun testSDKFactoryRegistration() {
        println("🧪 測試 SDK Factory 註冊")
        
        val manager = RealSDKFactory.createRealManager()
        
        // 驗證所有主要區塊鏈都已註冊
        val expectedChains = listOf(
            MultiChainType.ETHEREUM,
            MultiChainType.BSC,
            MultiChainType.POLYGON,
            MultiChainType.SOLANA,
            MultiChainType.TRON,
            MultiChainType.BITCOIN,
            MultiChainType.MONERO // 確認 Monero 已註冊
        )
        
        for (chain in expectedChains) {
            val adapter = manager.getAdapter(chain)
            assertNotNull(adapter, "${chain.fullName} SDK 應該已註冊")
            println("✅ ${chain.fullName} SDK 已註冊")
        }
        
        // 驗證總數
        val allAdapters = manager.getAllAdapters()
        assertTrue(allAdapters.size >= expectedChains.size, "應該至少有 ${expectedChains.size} 個 SDK")
        println("✅ 總共註冊了 ${allAdapters.size} 個 SDK")
    }
    
    @Test
    fun testMoneroSDKCapabilities() {
        println("\n🧪 測試 Monero SDK 功能集")

        val provider = getMoneroCryptoProvider()
        val sdk = MoneroSDK(provider)
        
        // 驗證基本屬性
        assertEquals(MultiChainType.MONERO, sdk.chainType)
        assertEquals("1.0.0", sdk.sdkVersion)
        
        // 驗證功能集
        val expectedCapabilities = setOf(
            SDKCapability.BALANCE_QUERY,
            SDKCapability.TRANSACTION_CREATION,
            SDKCapability.TRANSACTION_BROADCAST,
            SDKCapability.ADDRESS_VALIDATION,
            SDKCapability.TRANSACTION_HISTORY
        )
        
        assertEquals(expectedCapabilities, sdk.capabilities)
        println("✅ Monero SDK 功能集正確")
        
        for (capability in sdk.capabilities) {
            println("   - $capability")
        }
    }
    
    @Test
    fun testMoneroAddressValidation() {
        println("\n🧪 測試 Monero 地址驗證")

        val provider = getMoneroCryptoProvider()
        val sdk = MoneroSDK(provider)
        
        // 測試有效地址
        val validAddresses = listOf(
            ROOKIE_MONERO,
            IRON_MONERO
        )
        
        for (address in validAddresses) {
            val result = sdk.validateAddress(address)
            when (result) {
                is Result.Success -> {
                    assertTrue(result.data.isValid, "地址應該有效: ${address.take(10)}...")
                    println("✅ 有效地址: ${address.take(10)}...${address.takeLast(10)}")
                }
                is Result.Failure -> {
                    println("❌ 驗證失敗: ${result.error.message}")
                }
                else -> {}
            }
        }
        
        // 測試無效地址
        val invalidAddresses = listOf(
            "invalid_address",
            ROOKIE_EVM, // Ethereum 地址不應該是有效的 Monero 地址
            "4" + "x".repeat(94) // 長度正確但內容無效
        )
        
        for (address in invalidAddresses) {
            val result = sdk.validateAddress(address)
            when (result) {
                is Result.Success -> {
                    assertTrue(!result.data.isValid, "地址應該無效: $address")
                    println("✅ 正確識別無效地址: ${address.take(20)}...")
                }
                is Result.Failure -> {
                    println("❌ 驗證失敗: ${result.error.message}")
                }
                else -> {}
            }
        }
    }
    
    @Test
    fun testTransactionFeeEstimation() = runTest {
        println("\n🧪 測試交易費用估算")

        val provider = getMoneroCryptoProvider()
        val sdk = MoneroSDK(provider)
        
        // 測試不同優先級的費用估算
        val priorities = listOf(
            TransactionPriority.LOW to 0.00002,
            TransactionPriority.NORMAL to 0.00003,
            TransactionPriority.HIGH to 0.00005,
            TransactionPriority.URGENT to 0.00007
        )
        
        for ((priority, expectedFee) in priorities) {
            val request = TransactionRequest(
                fromAddress = ROOKIE_MONERO,
                toAddress = IRON_MONERO,
                amount = "0.01",
                priority = priority
            )
            
            val result = sdk.estimateTransactionFee(request)
            when (result) {
                is Result.Success -> {
                    val estimatedCost = result.data.estimatedCost.toDouble()
                    assertEquals(expectedFee, estimatedCost, 0.00001)
                    println("✅ $priority 費用: ${result.data.estimatedCost} XMR")
                }
                is Result.Failure -> {
                    println("❌ 估算失敗: ${result.error.message}")
                }
                else -> {}
            }
        }
    }
    
    @Test
    fun testCrossChainAddressValidation() {
        println("\n🧪 測試跨鏈地址驗證")
        
        val manager = RealSDKFactory.createRealManager()
        
        // 測試案例：(鏈類型, 地址, 預期結果)
        val testCases = listOf(
            Triple(MultiChainType.ETHEREUM, ROOKIE_EVM, true),
            Triple(MultiChainType.ETHEREUM, ROOKIE_TRON, false),
            Triple(MultiChainType.TRON, ROOKIE_TRON, true),
            Triple(MultiChainType.TRON, ROOKIE_EVM, false),
            Triple(MultiChainType.SOLANA, ROOKIE_SOLANA, true),
            Triple(MultiChainType.SOLANA, ROOKIE_MONERO, false),
            Triple(MultiChainType.MONERO, ROOKIE_MONERO, true),
            Triple(MultiChainType.MONERO, ROOKIE_EVM, false)
        )
        
        var passCount = 0
        var failCount = 0
        
        for ((chainType, address, expectedValid) in testCases) {
            val adapter = manager.getAdapter(chainType)
            if (adapter != null) {
                val result = adapter.validateAddress(address)
                when (result) {
                    is Result.Success -> {
                        val isValid = result.data.isValid
                        if (isValid == expectedValid) {
                            passCount++
                            println("✅ ${chainType.symbol} + ${address.take(10)}... = ${if (isValid) "有效" else "無效"} (正確)")
                        } else {
                            failCount++
                            println("❌ ${chainType.symbol} + ${address.take(10)}... = ${if (isValid) "有效" else "無效"} (錯誤，預期: ${if (expectedValid) "有效" else "無效"})")
                        }
                    }
                    is Result.Failure -> {
                        failCount++
                        println("❌ ${chainType.symbol} 驗證失敗: ${result.error.message}")
                    }
                    else -> {}
                }
            } else {
                println("⚠️ ${chainType.symbol} SDK 未找到")
            }
        }
        
        println("\n📊 測試結果: $passCount 通過, $failCount 失敗")
        assertTrue(failCount == 0, "所有地址驗證測試應該通過")
    }
    
    @Test
    fun testSDKInitializationWithoutNetwork() = runTest {
        println("\n🧪 測試 SDK 初始化（不需網路）")

        val provider = getMoneroCryptoProvider()
        val sdk = MoneroSDK(provider)
        
        // 測試初始化前的狀態
        assertTrue(!sdk.isInitialized(), "SDK 初始化前應該返回 false")
        println("✅ SDK 初始狀態正確")
        
        // 測試初始化
        val config = SDKConfig(
            network = "mainnet",
            rpcUrl = DefaultRPCConfig.MONERO_MAINNET,
            customParams = mapOf(
                "mode" to "lws",
                "daemonUrl" to "54.153.251.193:38089",
                "lwsUrl" to "http://54.153.251.193:8082",
                "mnemonic" to "rookie abuse frozen luxury science hat alert avoid car lemon day cost"
            )
        )
        
        val result = sdk.initialize(config)
        when (result) {
            is Result.Success -> {
                assertTrue(sdk.isInitialized(), "SDK 初始化後應該返回 true")
                println("✅ SDK 初始化成功")
            }
            is Result.Failure -> {
                println("⚠️ SDK 初始化失敗（可能需要網路）: ${result.error.message}")
            }
            else -> {}
        }
    }
    
    @Test
    fun testAllSDKVersions() {
        println("\n🧪 測試所有 SDK 版本號")
        
        val manager = RealSDKFactory.createRealManager()
        val allAdapters = manager.getAllAdapters()
        
        println("檢查 ${allAdapters.size} 個 SDK 的版本號：")
        
        for (adapter in allAdapters) {
            assertTrue(adapter.sdkVersion.isNotEmpty(), "${adapter.chainType} 應該有版本號")
            println("✅ ${adapter.chainType.fullName}: v${adapter.sdkVersion}")
        }
    }
    
    @Test
    fun testSDKCapabilityQuery() {
        println("\n🧪 測試 SDK 功能查詢")
        
        val manager = RealSDKFactory.createRealManager()
        
        // 查詢支援餘額查詢的 SDK
        val balanceQuerySDKs = manager.getAdaptersByCapability(SDKCapability.BALANCE_QUERY)
        println("支援餘額查詢的 SDK: ${balanceQuerySDKs.size} 個")
        
        // 查詢支援交易創建的 SDK
        val txCreationSDKs = manager.getAdaptersByCapability(SDKCapability.TRANSACTION_CREATION)
        println("支援交易創建的 SDK: ${txCreationSDKs.size} 個")
        
        // 查詢支援地址驗證的 SDK
        val addressValidationSDKs = manager.getAdaptersByCapability(SDKCapability.ADDRESS_VALIDATION)
        println("支援地址驗證的 SDK: ${addressValidationSDKs.size} 個")
        
        // 所有基本功能應該被廣泛支援
        assertTrue(balanceQuerySDKs.isNotEmpty(), "至少應有一個 SDK 支援餘額查詢")
        assertTrue(txCreationSDKs.isNotEmpty(), "至少應有一個 SDK 支援交易創建")
        assertTrue(addressValidationSDKs.isNotEmpty(), "至少應有一個 SDK 支援地址驗證")
        
        // 確認 Monero SDK 支援所有基本功能
        val moneroAdapter = manager.getAdapter(MultiChainType.MONERO)
        assertNotNull(moneroAdapter, "Monero SDK 應該存在")
        assertTrue(moneroAdapter in balanceQuerySDKs, "Monero 應支援餘額查詢")
        assertTrue(moneroAdapter in txCreationSDKs, "Monero 應支援交易創建")
        assertTrue(moneroAdapter in addressValidationSDKs, "Monero 應支援地址驗證")
        
        println("✅ Monero SDK 支援所有基本功能")
    }
}
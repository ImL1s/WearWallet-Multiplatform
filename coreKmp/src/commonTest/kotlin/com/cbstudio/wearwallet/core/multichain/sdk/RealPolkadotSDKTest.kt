package com.cbstudio.wearwallet.core.multichain.sdk

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.common.Result.Success
import com.cbstudio.wearwallet.core.common.Result.Failure
import com.cbstudio.wearwallet.core.common.Result.Loading
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.core.multichain.config.DefaultRPCConfig
import kotlin.test.*
import kotlinx.coroutines.runBlocking

/**
 * Polkadot Real SDK 測試套件
 *
 * 測試範圍:
 * ✅ SDK 初始化
 * ✅ 地址驗證
 * ✅ 餘額查詢 (需要真實網路連接)
 * ✅ 交易創建
 * ✅ 網路狀態查詢
 *
 * 測試網路: Westend Testnet
 */
class RealPolkadotSDKTest {

    private lateinit var sdk: RealPolkadotSDK

    @BeforeTest
    fun setup() {
        sdk = RealPolkadotSDK()
    }

    @AfterTest
    fun teardown() {
        runBlocking {
            sdk.cleanup()
        }
    }

    @Test
    fun testChainType() {
        assertEquals(MultiChainType.POLKADOT, sdk.chainType)
    }

    @Test
    fun testSdkVersion() {
        assertEquals("1.0.0", sdk.sdkVersion)
    }

    @Test
    fun testCapabilities() {
        assertTrue(sdk.capabilities.contains(SDKCapability.BALANCE_QUERY))
        assertTrue(sdk.capabilities.contains(SDKCapability.TRANSACTION_CREATION))
        assertTrue(sdk.capabilities.contains(SDKCapability.ADDRESS_VALIDATION))
        assertTrue(sdk.capabilities.contains(SDKCapability.TRANSACTION_BROADCAST))
        assertTrue(sdk.capabilities.contains(SDKCapability.STAKING_OPERATIONS))
    }

    @Test
    fun testInitialization_WithWestendNetwork() = runBlocking {
        // 測試 Westend 測試網初始化
        val config = SDKConfig(
            network = "westend",
            rpcUrl = DefaultRPCConfig.POLKADOT_WESTEND,
            apiKey = "",
            timeout = 30000
        )

        val result = sdk.initialize(config)

        assertTrue(result is Result.Success, "Westend 初始化應該成功")
        assertTrue(sdk.isInitialized(), "SDK 應該處於已初始化狀態")
    }

    @Test
    fun testInitialization_WithPolkadotMainnet() = runBlocking {
        val config = SDKConfig(
            network = "polkadot",
            rpcUrl = DefaultRPCConfig.POLKADOT_MAINNET,
            apiKey = "",
            timeout = 30000
        )

        val result = sdk.initialize(config)

        assertTrue(result is Result.Success)
        assertTrue(sdk.isInitialized())
    }

    @Test
    fun testInitialization_WithInvalidNetwork() = runBlocking {
        val config = SDKConfig(
            network = "invalid_network",
            rpcUrl = DefaultRPCConfig.getDefaultRpcUrl(MultiChainType.POLKADOT, "invalid_network"),
            apiKey = "",
            timeout = 30000
        )

        val result = sdk.initialize(config)

        assertTrue(result is Result.Failure)
        val error = (result as Result.Failure).exception
        assertTrue(error is SDKException.ConfigurationException)
    }

    @Test
    fun testAddressValidation_ValidPolkadotAddress() {
        // Polkadot 主網有效地址示例 (前綴 1)
        val validAddress = "15oF4uVJwmo4TdGW7VfQxNLavjCXviqxT9S1MgbjMNHr6Sp5"

        val result = sdk.validateAddress(validAddress)

        assertTrue(result is Result.Success)
        val validation = (result as Result.Success).data
        assertTrue(validation.isValid, "應該是有效的 Polkadot 地址")
        assertEquals("有效的 Polkadot 地址", validation.message)
    }

    @Test
    fun testAddressValidation_ValidWestendAddress() {
        // Westend 測試網有效地址示例 (前綴 5)
        val validAddress = "5GrwvaEF5zXb26Fz9rcQpDWS57CtERHpNehXCPcNoHGKutQY"

        val result = sdk.validateAddress(validAddress)

        assertTrue(result is Result.Success)
        val validation = (result as Result.Success).data
        assertTrue(validation.isValid)
    }

    @Test
    fun testAddressValidation_ValidKusamaAddress() {
        // Kusama 地址示例 (前綴 C/D/F/G/H/J)
        val validAddress = "CpjsLDC1JFyrhm3ftC9Gs4QoyrkHKhZKtK7YqGTRFtTafgp"

        val result = sdk.validateAddress(validAddress)

        assertTrue(result is Result.Success)
        val validation = (result as Result.Success).data
        assertTrue(validation.isValid)
    }

    @Test
    fun testAddressValidation_InvalidAddress_TooShort() {
        val invalidAddress = "123"

        val result = sdk.validateAddress(invalidAddress)

        assertTrue(result is Result.Success)
        val validation = (result as Result.Success).data
        assertFalse(validation.isValid, "過短的地址應該無效")
        assertEquals("無效的地址格式", validation.message)
    }

    @Test
    fun testAddressValidation_InvalidAddress_EthereumFormat() {
        val invalidAddress = "0x1234567890abcdef1234567890abcdef12345678" // 以太坊地址格式

        val result = sdk.validateAddress(invalidAddress)

        assertTrue(result is Result.Success)
        val validation = (result as Result.Success).data
        assertFalse(validation.isValid, "以太坊地址格式應該無效")
    }

    @Test
    fun testAddressValidation_InvalidCharacters() {
        // 包含無效字符 (0, O, I, l 不在 Base58 中)
        val invalidAddress = "10OlI4uVJwmo4TdGW7VfQxNLavjCXviqxT9S1MgbjMNHr6Sp5"

        val result = sdk.validateAddress(invalidAddress)

        assertTrue(result is Result.Success)
        val validation = (result as Result.Success).data
        assertFalse(validation.isValid)
    }

    @Test
    @Ignore
    fun testGetAccountBalance_RealNetwork() = runBlocking {
        // 這個測試需要真實的網路連接
        val config = SDKConfig(
            network = "westend",
            rpcUrl = DefaultRPCConfig.POLKADOT_WESTEND,
            apiKey = "",
            timeout = 30000
        )

        val initResult = sdk.initialize(config)
        assertTrue(initResult is Result.Success)

        // Alice 測試地址
        val address = "5GrwvaEF5zXb26Fz9rcQpDWS57CtERHpNehXCPcNoHGKutQY"
        val balanceResult = sdk.getAccountBalance(address)

        when (balanceResult) {
            is Result.Success -> {
                val balance = balanceResult.data
                assertEquals("WND", balance.symbol)
                assertEquals(12, balance.decimals)
                println("餘額: ${balance.amount} ${balance.symbol}")
            }
            is Result.Failure -> {
                println("餘額查詢失敗: ${balanceResult.exception.message}")
            }
            is Result.Loading -> {
                println("載入中...")
            }
        }
    }

    @Test
    fun testCreateTransaction_BeforeInitialization() = runBlocking {
        val request = TransactionRequest(
            fromAddress = "5GrwvaEF5zXb26Fz9rcQpDWS57CtERHpNehXCPcNoHGKutQY",
            toAddress = "5FHneW46xGXgs5mUiveU4sbTyGBzmstUspZC92UhjJM694ty",
            amount = "1.0",
            priority = TransactionPriority.NORMAL
        )

        val result = sdk.createTransaction(request)
        assertTrue(result is Result.Failure)
        val error = (result as Result.Failure).exception
        assertTrue(error is SDKException.InitializationException)
    }

    @Test
    fun testCreateTransaction_WithInitialization() = runBlocking {
        val config = SDKConfig(network = "westend", rpcUrl = DefaultRPCConfig.POLKADOT_WESTEND, apiKey = "", timeout = 30000)
        sdk.initialize(config)

        val request = TransactionRequest(
            fromAddress = "5GrwvaEF5zXb26Fz9rcQpDWS57CtERHpNehXCPcNoHGKutQY",
            toAddress = "5FHneW46xGXgs5mUiveU4sbTyGBzmstUspZC92UhjJM694ty",
            amount = "1.0",
            priority = TransactionPriority.NORMAL
        )

        val txResult = sdk.createTransaction(request)

        when (txResult) {
            is Result.Success -> {
                val unsignedTx = txResult.data
                assertNotNull(unsignedTx.metadata["nonce"])
                assertNotNull(unsignedTx.metadata["blockHash"])
                assertNotNull(unsignedTx.metadata["genesisHash"])
                assertNotNull(unsignedTx.metadata["amountInPlanck"])
                val txFrom = unsignedTx.metadata["fromAddress"] as? String ?: ""
                val txTo = unsignedTx.metadata["toAddress"] as? String ?: ""
                assertEquals(request.fromAddress, txFrom)
                assertEquals(request.toAddress, txTo)
                println("交易創建成功")
                println("Nonce: ${unsignedTx.metadata["nonce"]}")
                println("Amount in Planck: ${unsignedTx.metadata["amountInPlanck"]}")
            }
            is Result.Failure -> {
                fail("交易創建不應失敗: ${txResult.exception.message}")
            }
            is Result.Loading -> {
                fail("不應該處於載入狀態")
            }
        }
    }

    @Test
    fun testCreateTransaction_WithInvalidFromAddress() = runBlocking {
        val config = SDKConfig(network = "westend", rpcUrl = DefaultRPCConfig.POLKADOT_WESTEND, apiKey = "", timeout = 30000)
        sdk.initialize(config)

        val request = TransactionRequest(
            fromAddress = "invalid_address",
            toAddress = "5FHneW46xGXgs5mUiveU4sbTyGBzmstUspZC92UhjJM694ty",
            amount = "1.0",
            priority = TransactionPriority.NORMAL
        )

        val result = sdk.createTransaction(request)
        assertTrue(result is Result.Failure)
        val error = (result as Result.Failure).exception
        assertTrue(error is SDKException.ConfigurationException || error.message?.contains("address") == true)
    }

    @Test
    fun testEstimateTransactionFee_LowPriority() = runBlocking {
        val config = SDKConfig(network = "westend", rpcUrl = DefaultRPCConfig.POLKADOT_WESTEND, apiKey = "", timeout = 30000)
        sdk.initialize(config)

        val request = TransactionRequest(
            fromAddress = "5GrwvaEF5zXb26Fz9rcQpDWS57CtERHpNehXCPcNoHGKutQY",
            toAddress = "5FHneW46xGXgs5mUiveU4sbTyGBzmstUspZC92UhjJM694ty",
            amount = "1.0",
            priority = TransactionPriority.LOW
        )

        val feeResult = sdk.estimateTransactionFee(request)

        assertTrue(feeResult is Result.Success)
        val fee = (feeResult as Result.Success).data
        assertEquals(TransactionPriority.LOW, fee.priority)
        assertEquals("0.01", fee.estimatedCost)
    }

    @Test
    fun testEstimateTransactionFee_NormalPriority() = runBlocking {
        val config = SDKConfig(network = "westend", rpcUrl = DefaultRPCConfig.POLKADOT_WESTEND, apiKey = "", timeout = 30000)
        sdk.initialize(config)

        val request = TransactionRequest(
            fromAddress = "5GrwvaEF5zXb26Fz9rcQpDWS57CtERHpNehXCPcNoHGKutQY",
            toAddress = "5FHneW46xGXgs5mUiveU4sbTyGBzmstUspZC92UhjJM694ty",
            amount = "1.0",
            priority = TransactionPriority.NORMAL
        )

        val feeResult = sdk.estimateTransactionFee(request)

        assertTrue(feeResult is Result.Success)
        val fee = (feeResult as Result.Success).data
        assertEquals(TransactionPriority.NORMAL, fee.priority)
        assertEquals("0.015", fee.estimatedCost)
    }

    @Test
    fun testEstimateTransactionFee_HighPriority() = runBlocking {
        val config = SDKConfig(network = "westend", rpcUrl = DefaultRPCConfig.POLKADOT_WESTEND, apiKey = "", timeout = 30000)
        sdk.initialize(config)

        val request = TransactionRequest(
            fromAddress = "5GrwvaEF5zXb26Fz9rcQpDWS57CtERHpNehXCPcNoHGKutQY",
            toAddress = "5FHneW46xGXgs5mUiveU4sbTyGBzmstUspZC92UhjJM694ty",
            amount = "1.0",
            priority = TransactionPriority.HIGH
        )

        val feeResult = sdk.estimateTransactionFee(request)

        assertTrue(feeResult is Result.Success)
        val fee = (feeResult as Result.Success).data
        assertEquals(TransactionPriority.HIGH, fee.priority)
        assertEquals("0.02", fee.estimatedCost)
    }

    @Test
    @Ignore
    fun testGetNetworkStatus_RealNetwork() = runBlocking {
        val config = SDKConfig(
            network = "westend",
            rpcUrl = DefaultRPCConfig.POLKADOT_WESTEND,
            apiKey = "",
            timeout = 30000
        )

        sdk.initialize(config)

        val statusResult = sdk.getNetworkStatus()

        when (statusResult) {
            is Result.Success -> {
                val status = statusResult.data
                assertTrue(status.isConnected)
                assertTrue(status.blockHeight > 0)
                assertEquals("westend", status.networkId)
                assertEquals(6000L, status.averageBlockTime)
                println("網路狀態: 區塊高度 ${status.blockHeight}")
            }
            is Result.Failure -> {
                println("網路狀態查詢失敗: ${statusResult.exception.message}")
            }
            is Result.Loading -> {
                println("載入中...")
            }
        }
    }

    @Test
    fun testGetNetworkStatus_WithoutInitialization() = runBlocking {
        val result = sdk.getNetworkStatus()
        assertTrue(result is Result.Failure)
        val error = (result as Result.Failure).exception
        assertTrue(error is SDKException.InitializationException)
    }

    @Test
    @Ignore
    fun testGetTransactionHistory() = runBlocking {
        val config = SDKConfig(network = "westend", rpcUrl = DefaultRPCConfig.POLKADOT_WESTEND, apiKey = "", timeout = 30000)
        sdk.initialize(config)

        val address = "5GrwvaEF5zXb26Fz9rcQpDWS57CtERHpNehXCPcNoHGKutQY"
        val historyResult = sdk.getTransactionHistory(address, limit = 10, offset = 0)

        when (historyResult) {
            is Result.Success -> {
                val transactions = historyResult.data
                println("獲取到 ${transactions.size} 筆交易記錄")
                transactions.forEach { tx ->
                    println("Hash: ${tx.hash}, Amount: ${tx.amount}, Status: ${tx.status}")
                }
            }
            is Result.Failure -> {
                println("查詢交易歷史失敗: ${historyResult.exception.message}")
            }
            is Result.Loading -> {
                println("載入中...")
            }
        }
    }

    @Test
    fun testCleanup() = runBlocking {
        val config = SDKConfig(network = "westend", rpcUrl = DefaultRPCConfig.POLKADOT_WESTEND, apiKey = "", timeout = 30000)

        sdk.initialize(config)
        assertTrue(sdk.isInitialized())

        sdk.cleanup()
        assertFalse(sdk.isInitialized())
    }

    @Test
    fun testMultipleInitializations() = runBlocking {
        val config = SDKConfig(network = "westend", rpcUrl = DefaultRPCConfig.POLKADOT_WESTEND, apiKey = "", timeout = 30000)

        // 第一次初始化
        val result1 = sdk.initialize(config)
        assertTrue(result1 is Result.Success)
        assertTrue(sdk.isInitialized())

        // 第二次初始化 (應該覆蓋)
        val result2 = sdk.initialize(config)
        assertTrue(result2 is Result.Success)
        assertTrue(sdk.isInitialized())

        sdk.cleanup()
    }

    @Test
    fun testNetworkSwitch() = runBlocking {
        // 初始化為 Westend
        val westendConfig = SDKConfig(network = "westend", rpcUrl = DefaultRPCConfig.POLKADOT_WESTEND, apiKey = "", timeout = 30000)
        val result1 = sdk.initialize(westendConfig)
        assertTrue(result1 is Result.Success)

        // 切換到 Polkadot Mainnet
        val polkadotConfig = SDKConfig(network = "polkadot", rpcUrl = DefaultRPCConfig.POLKADOT_MAINNET, apiKey = "", timeout = 30000)
        val result2 = sdk.initialize(polkadotConfig)
        assertTrue(result2 is Result.Success)

        sdk.cleanup()
    }
}

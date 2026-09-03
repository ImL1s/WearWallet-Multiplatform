package com.cbstudio.wearwallet.core.multichain.sdk

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * RealTronSDK 測試
 *
 * 測試策略:
 * 1. 使用 Shasta Testnet 進行測試
 * 2. 測試地址驗證功能
 * 3. 測試交易創建和簽名（不廣播）
 * 4. 測試 TRC20 編碼器
 */
class RealTronSDKTest {

    private lateinit var sdk: RealTronSDK

    @BeforeTest
    fun setup() = runTest {
        sdk = RealTronSDK()

        // 初始化 SDK 連接到 Shasta Testnet
        val config = SDKConfig(
            rpcUrl = "https://api.shasta.trongrid.io",
            network = "shasta",
            apiKey = null // Shasta testnet 不需要 API key
        )

        val result = sdk.initialize(config)
        assertTrue(result is Result.Success, "SDK 初始化應該成功")
    }

    @AfterTest
    fun tearDown() = runTest {
        sdk.cleanup()
    }

    @Test
    fun testAddressValidation() {
        // 測試有效的 TRON 地址
        val validAddress = "TJRabPrwbZy45sbavfcjinPJC18kjpRTv8" // Shasta testnet 地址示例
        val result = sdk.validateAddress(validAddress)

        assertTrue(result is Result.Success, "有效地址驗證應該成功")
        val validation = (result as Result.Success).data
        assertTrue(validation.isValid, "地址應該被判定為有效")
    }

    @Test
    fun testInvalidAddressValidation() {
        // 測試無效的地址
        val invalidAddresses = listOf(
            "0x1234567890", // Ethereum 格式
            "abc123", // 太短
            "R1234567890123456789012345678901234", // 錯誤的前綴
            "" // 空字符串
        )

        invalidAddresses.forEach { address ->
            val result = sdk.validateAddress(address)
            assertTrue(result is Result.Success, "驗證應該成功執行")
            val validation = (result as Result.Success).data
            assertFalse(validation.isValid, "地址 '$address' 應該被判定為無效")
        }
    }

    @Test
    fun testTRC20EncoderTransfer() {
        // 測試 TRC20 transfer 編碼
        val toAddress = "TJRabPrwbZy45sbavfcjinPJC18kjpRTv8"
        val amount = "1000000" // 1 USDT (假設 6 位小數)

        val encoded = TRC20Encoder.encodeTransfer(toAddress, amount)

        // 驗證編碼結果
        assertTrue(encoded.startsWith("a9059cbb"), "應該以 transfer 方法 ID 開頭")
        assertEquals(136, encoded.length, "編碼長度應該是 136 字符 (4 + 64 + 64 + 4)")
    }

    @Test
    fun testTRC20EncoderBalanceOf() {
        // 測試 TRC20 balanceOf 編碼
        val address = "TJRabPrwbZy45sbavfcjinPJC18kjpRTv8"

        val encoded = TRC20Encoder.encodeBalanceOf(address)

        // 驗證編碼結果
        assertTrue(encoded.startsWith("70a08231"), "應該以 balanceOf 方法 ID 開頭")
        assertEquals(72, encoded.length, "編碼長度應該是 72 字符 (8 + 64)")
    }

    @Test
    fun testTronAddressValidation() {
        // 測試 Base58Check 格式地址
        assertTrue(TronAddress.isValidAddress("TJRabPrwbZy45sbavfcjinPJC18kjpRTv8"))

        // 測試 Hex 格式地址
        assertTrue(TronAddress.isValidAddress("414d1ef8673f916debb7e2515a8f3ecaf2611034aa"))
        assertTrue(TronAddress.isValidAddress("0x414d1ef8673f916debb7e2515a8f3ecaf2611034aa"))

        // 測試無效地址
        assertFalse(TronAddress.isValidAddress(""))
        assertFalse(TronAddress.isValidAddress("abc"))
        assertFalse(TronAddress.isValidAddress("0x1234"))
    }

    @Test
    fun testEstimateTransactionFee() = runTest {
        // 測試 TRX 轉帳費用估算
        val trxRequest = TransactionRequest(
            fromAddress = "TJRabPrwbZy45sbavfcjinPJC18kjpRTv8",
            toAddress = "TGjAHQzKTv8J74oLjBKbYP6icqFxNfSCmt",
            amount = "10",
            priority = TransactionPriority.NORMAL
        )

        val result = sdk.estimateTransactionFee(trxRequest)

        assertTrue(result is Result.Success, "TRX 轉帳費用估算應該成功")
        val fee = (result as Result.Success).data
        assertEquals("268", fee.gasLimit, "TRX 轉帳應該消耗 268 bandwidth")
        assertEquals("0", fee.gasPrice.toDoubleOrNull()?.let { if (it > 0) "positive" else "0" } ?: "0",
            "TRX 轉帳能量費用應該為 0")
    }

    @Test
    fun testEstimateTRC20Fee() = runTest {
        // 測試 TRC20 轉帳費用估算
        val trc20Request = TransactionRequest(
            fromAddress = "TJRabPrwbZy45sbavfcjinPJC18kjpRTv8",
            toAddress = "TGjAHQzKTv8J74oLjBKbYP6icqFxNfSCmt",
            amount = "10",
            tokenAddress = "TG3XXyExBkPp9nzdajDZsozEu4BkaSJozs", // USDT-Shasta
            priority = TransactionPriority.NORMAL
        )

        val result = sdk.estimateTransactionFee(trc20Request)

        assertTrue(result is Result.Success, "TRC20 轉帳費用估算應該成功")
        val fee = (result as Result.Success).data
        assertEquals("345", fee.gasLimit, "TRC20 轉帳應該消耗 345 bandwidth")
    }

    @Test
    fun testUint256Decoding() {
        // 測試 uint256 解碼
        val hexValue = "0000000000000000000000000000000000000000000000000000000000989680" // 10000000
        val decoded = TRC20Encoder.decodeUint256(hexValue)

        assertEquals("10000000", decoded, "應該正確解碼 uint256 值")
    }

    @Test
    fun testSDKInitialization() = runTest {
        val newSdk = RealTronSDK()

        // 測試未初始化的 SDK
        assertFalse(newSdk.isInitialized(), "新建的 SDK 應該未初始化")

        // 測試初始化
        val config = SDKConfig(
            rpcUrl = "https://api.shasta.trongrid.io",
            network = "shasta"
        )

        val result = newSdk.initialize(config)
        assertTrue(result is Result.Success, "初始化應該成功")
        assertTrue(newSdk.isInitialized(), "初始化後的 SDK 應該標記為已初始化")

        // 清理
        newSdk.cleanup()
        assertFalse(newSdk.isInitialized(), "清理後的 SDK 應該標記為未初始化")
    }

    @Test
    fun testNetworkStatus() = runTest {
        val result = sdk.getNetworkStatus()

        assertTrue(result is Result.Success, "獲取網絡狀態應該成功")
        val status = (result as Result.Success).data
        assertTrue(status.isConnected, "應該連接到網絡")
        assertEquals("shasta", status.networkId, "網絡 ID 應該是 shasta")
    }

    @Test
    fun testSDKCapabilities() {
        // 驗證 SDK 支援的功能
        val capabilities = sdk.capabilities

        assertTrue(capabilities.contains(SDKCapability.BALANCE_QUERY), "應該支援餘額查詢")
        assertTrue(capabilities.contains(SDKCapability.TRANSACTION_CREATION), "應該支援交易創建")
        assertTrue(capabilities.contains(SDKCapability.TRANSACTION_SIGNING), "應該支援交易簽名")
        assertTrue(capabilities.contains(SDKCapability.TRANSACTION_BROADCAST), "應該支援交易廣播")
        assertTrue(capabilities.contains(SDKCapability.ADDRESS_VALIDATION), "應該支援地址驗證")
        assertTrue(capabilities.contains(SDKCapability.TRANSACTION_HISTORY), "應該支援交易歷史")
        assertTrue(capabilities.contains(SDKCapability.SMART_CONTRACT_INTERACTION), "應該支援智能合約交互")
    }

    @Test
    fun testChainType() {
        assertEquals(MultiChainType.TRON, sdk.chainType, "Chain type 應該是 TRON")
    }
}

/**
 * TRON 整合測試（需要實際網絡連接）
 *
 * 注意: 這些測試需要連接到 Shasta Testnet
 * 某些測試可能因為網絡狀況而失敗
 */
class RealTronSDKIntegrationTest {

    private lateinit var sdk: RealTronSDK

    // Shasta Testnet 測試地址（水龍頭獲取的測試 TRX）
    private val testAddress = "TJRabPrwbZy45sbavfcjinPJC18kjpRTv8" // 示例地址，實際使用時需要替換

    @BeforeTest
    fun setup() = runTest {
        sdk = RealTronSDK()

        val config = SDKConfig(
            rpcUrl = "https://api.shasta.trongrid.io",
            network = "shasta"
        )

        sdk.initialize(config)
    }

    @AfterTest
    fun tearDown() = runTest {
        sdk.cleanup()
    }

    @Test
    @Ignore
    fun testGetBalance() = runTest {
        val result = sdk.getAccountBalance(testAddress)

        assertTrue(result is Result.Success, "查詢餘額應該成功")
        val balance = (result as Result.Success).data

        assertNotNull(balance.amount, "餘額應該不為 null")
        assertEquals("TRX", balance.symbol, "符號應該是 TRX")
        assertEquals(6, balance.decimals, "小數位應該是 6")
    }

    @Test
    @Ignore
    fun testGetTransactionHistory() = runTest {
        val result = sdk.getTransactionHistory(
            address = testAddress,
            limit = 10,
            offset = 0
        )

        assertTrue(result is Result.Success, "查詢交易歷史應該成功")
        val transactions = (result as Result.Success).data

        // 注意：新地址可能沒有交易歷史
        assertNotNull(transactions, "交易列表不應該為 null")
    }

    @Test
    @Ignore
    fun testGetTRC20Balance() = runTest {
        // USDT on Shasta Testnet (需要實際的合約地址)
        val usdtContract = "TG3XXyExBkPp9nzdajDZsozEu4BkaSJozs"

        val result = sdk.getTRC20Balance(testAddress, usdtContract)

        assertTrue(result is Result.Success, "查詢 TRC20 餘額應該成功")
    }
}

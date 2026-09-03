package com.cbstudio.wearwallet.core.multichain.sdk

import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.core.multichain.sdk.impl.CardanoRealSDK
import com.cbstudio.wearwallet.core.common.Result
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.*

/**
 * Cardano Real SDK 測試
 *
 * 測試重點:
 * - SDK 初始化和配置驗證
 * - 地址生成和驗證 (Bech32 格式)
 * - 餘額查詢 (Lovelace to ADA 轉換)
 * - UTXO 交易構建
 * - Ed25519 簽名整合
 * - Blockfrost API 模擬
 * - Staking 功能
 *
 * 注意: 測試使用 Cardano Preprod Testnet
 */
class CardanoRealSDKTest {

    private lateinit var sdk: CardanoRealSDK

    @BeforeTest
    fun setup() {
        sdk = CardanoRealSDK()
    }

    @AfterTest
    fun tearDown() = runTest {
        sdk.cleanup()
    }

    // === 基本功能測試 ===

    @Test
    fun test_01_SDKMetadata() {
        assertEquals(MultiChainType.CARDANO, sdk.chainType)
        assertEquals("1.0.0-real", sdk.sdkVersion)
        assertTrue(sdk.capabilities.isNotEmpty())
        assertTrue(sdk.capabilities.contains(SDKCapability.BALANCE_QUERY))
        assertTrue(sdk.capabilities.contains(SDKCapability.STAKING_OPERATIONS))
    }

    @Test
    fun test_02_InitializeWithValidConfig() = runTest {
        val config = SDKConfig(
            network = "preprod",
            rpcUrl = "https://cardano-preprod.blockfrost.io/api/v0/",
            apiKey = "preprodXXXXXXXXXXXXXXXXXXXXXXXXXXXX", // 測試用 API Key
            timeout = 30000,
            retryCount = 3
        )

        val result = sdk.initialize(config)

        assertTrue(result is Result.Success)
        assertTrue(sdk.isInitialized())
    }

    @Test
    fun test_03_InitializeWithInvalidNetwork() = runTest {
        val config = SDKConfig(
            network = "invalid_network",
            rpcUrl = "https://invalid.endpoint.com/api/v0/",
            apiKey = "test_key",
            timeout = 30000,
            retryCount = 3
        )

        val result = sdk.initialize(config)

        assertTrue(result is Result.Failure)
        assertFalse(sdk.isInitialized())
    }

    @Test
    fun test_04_InitializeWithoutAPIKey() = runTest {
        val config = SDKConfig(
            network = "preprod",
            rpcUrl = "https://cardano-preprod.blockfrost.io/api/v0/",
            apiKey = null,
            timeout = 30000,
            retryCount = 3
        )

        val result = sdk.initialize(config)

        assertTrue(result is Result.Failure)
        val message = (result as Result.Failure).exception.message
        assertNotNull(message)
        assertTrue(message.contains("API Key"))
    }

    // === 地址生成和驗證測試 ===

    @Test
    fun test_05_GenerateAccount() = runTest {
        initializeSDK()

        val result = sdk.generateAccount()

        assertTrue(result is Result.Success)
        val account = (result as Result.Success).data

        // 驗證地址格式 (Shelley Bech32)
        assertTrue(account.address.startsWith("addr_test") || account.address.startsWith("addr"))
        assertTrue(account.address.length in 58..103)

        // 驗證密鑰長度
        assertEquals(128, account.privateKey.length) // 64 bytes = 128 hex chars
        assertEquals(64, account.publicKey.length)   // 32 bytes = 64 hex chars

        // 驗證網路
        assertEquals("preprod", account.network)
        assertEquals("shelley", account.addressType)
    }

    @Test
    fun test_06_ValidateShelleyAddress_Mainnet() {
        val validMainnetAddr = "addr1qxyz..."
        val result = sdk.validateAddress(validMainnetAddr)

        assertTrue(result is Result.Success)
        val validation = (result as Result.Success).data
        assertTrue(validation.isValid)
    }

    @Test
    fun test_07_ValidateShelleyAddress_Testnet() {
        val validTestnetAddr = "addr_test1qxyz..."
        val result = sdk.validateAddress(validTestnetAddr)

        assertTrue(result is Result.Success)
        val validation = (result as Result.Success).data
        assertTrue(validation.isValid)
    }

    @Test
    fun test_08_ValidateByronAddress() {
        val byronAddr = "DdzFFzCqrhsxyz..."
        val result = sdk.validateAddress(byronAddr)

        assertTrue(result is Result.Success)
        val validation = (result as Result.Success).data
        assertTrue(validation.isValid)
        assertEquals(AddressType.LEGACY, validation.addressType)
    }

    @Test
    fun test_09_ValidateInvalidAddress() {
        val invalidAddr = "invalid_cardano_address"
        val result = sdk.validateAddress(invalidAddr)

        assertTrue(result is Result.Success)
        val validation = (result as Result.Success).data
        assertFalse(validation.isValid)
    }

    // === 餘額查詢測試 ===

    @Test
    fun test_10_GetAccountBalance() = runTest {
        initializeSDK()

        val testAddress = "addr_test1qzf0hdwgj5d6j7h6g46c0w2z0v4r6v8r6v8r6v8r6v8r6v8r6v8r6v8r6v8r6"
        val result = sdk.getAccountBalance(testAddress)

        assertTrue(result is Result.Success)
        val balance = (result as Result.Success).data

        assertEquals("ADA", balance.symbol)
        assertEquals(6, balance.decimals)
        assertTrue(balance.amount.toDouble() >= 0)

        // 驗證 Lovelace to ADA 轉換
        val lovelace = balance.amount.toDouble() * 1_000_000
        assertTrue(lovelace >= 1_000_000) // 至少 1 ADA
    }

    @Test
    fun test_11_GetNativeTokenBalance() = runTest {
        initializeSDK()

        val testAddress = "addr_test1qzf0hdwgj5d6j7h6g46c0w2z0v4r6v8r6v8r6v8r6v8r6v8r6v8r6v8r6v8r6"
        val policyId = "abc123policyid"
        val assetName = "MyToken"

        val result = sdk.getNativeTokenBalance(testAddress, policyId, assetName)

        assertTrue(result is Result.Success)
        val balance = (result as Result.Success).data

        assertEquals(6, balance.decimals)
        assertTrue(balance.amount.toDouble() >= 0)
    }

    @Test
    fun test_12_GetBalanceWithoutInitialization() = runTest {
        val testAddress = "addr_test1qzf0hdwgj5d6j7h6g46c0w2z0v4r6v8r6v8r6v8r6v8r6v8r6v8r6v8r6v8r6"
        val result = sdk.getAccountBalance(testAddress)

        assertTrue(result is Result.Failure)
        val message = (result as Result.Failure).exception.message
        assertNotNull(message)
        assertTrue(message.contains("未初始化"))
    }

    // === 交易創建測試 ===

    @Test
    fun test_13_CreateADATransaction() = runTest {
        initializeSDK()

        val request = TransactionRequest(
            fromAddress = "addr_test1qzf0hdwgj5d6j7h6g46c0w2z0v4r6v8r6v8r6v8r6v8r6v8r6v8r6v8r6v8r6",
            toAddress = "addr_test1qrzqjhtd5yh0gj5d6j7h6g46c0w2z0v4r6v8r6v8r6v8r6v8r6v8r6v8r6v8r6",
            amount = "10.5", // 10.5 ADA
            tokenAddress = null,
            priority = TransactionPriority.NORMAL
        )

        val result = sdk.createTransaction(request)

        assertTrue(result is Result.Success)
        val tx = (result as Result.Success).data

        assertEquals(MultiChainType.CARDANO, tx.chainType)
        assertTrue(tx.rawData.isNotEmpty())
        assertTrue(tx.estimatedFee.estimatedCost.toDouble() > 0)
        assertEquals("0", tx.estimatedFee.gasLimit) // Cardano 不使用 gas
        assertTrue(tx.metadata.containsKey("network"))
        assertTrue(tx.metadata.containsKey("fee"))
    }

    @Test
    fun test_14_CreateNativeTokenTransaction() = runTest {
        initializeSDK()

        val request = TransactionRequest(
            fromAddress = "addr_test1qzf0hdwgj5d6j7h6g46c0w2z0v4r6v8r6v8r6v8r6v8r6v8r6v8r6v8r6v8r6",
            toAddress = "addr_test1qrzqjhtd5yh0gj5d6j7h6g46c0w2z0v4r6v8r6v8r6v8r6v8r6v8r6v8r6v8r6",
            amount = "100",
            tokenAddress = "abc123policyidMyToken",
            priority = TransactionPriority.NORMAL
        )

        val result = sdk.createTransaction(request)

        assertTrue(result is Result.Success)
        val tx = (result as Result.Success).data

        assertEquals(MultiChainType.CARDANO, tx.chainType)
        assertEquals("true", tx.metadata["isToken"])
    }

    @Test
    fun test_15_EstimateTransactionFee() = runTest {
        val request = TransactionRequest(
            fromAddress = "addr_test1qzf0hdwgj5d6j7h6g46c0w2z0v4r6v8r6v8r6v8r6v8r6v8r6v8r6v8r6v8r6",
            toAddress = "addr_test1qrzqjhtd5yh0gj5d6j7h6g46c0w2z0v4r6v8r6v8r6v8r6v8r6v8r6v8r6v8r6",
            amount = "10",
            tokenAddress = null,
            priority = TransactionPriority.NORMAL
        )

        val result = sdk.estimateTransactionFee(request)

        assertTrue(result is Result.Success)
        val fee = (result as Result.Success).data

        assertTrue(fee.estimatedCost.toDouble() >= 0.15) // 至少 0.15 ADA
        assertEquals("0", fee.gasLimit)
        assertEquals(TransactionPriority.NORMAL, fee.priority)
    }

    @Test
    fun test_16_EstimateFeeWithPriority() = runTest {
        val lowPriorityRequest = TransactionRequest(
            fromAddress = "addr_test1qzf0",
            toAddress = "addr_test1qrzq",
            amount = "10",
            tokenAddress = null,
            priority = TransactionPriority.LOW
        )

        val highPriorityRequest = lowPriorityRequest.copy(priority = TransactionPriority.HIGH)

        val lowFeeResult = sdk.estimateTransactionFee(lowPriorityRequest)
        val highFeeResult = sdk.estimateTransactionFee(highPriorityRequest)

        assertTrue(lowFeeResult is Result.Success)
        assertTrue(highFeeResult is Result.Success)

        val lowFee = (lowFeeResult as Result.Success).data.estimatedCost.toDouble()
        val highFee = (highFeeResult as Result.Success).data.estimatedCost.toDouble()

        assertTrue(highFee > lowFee)
    }

    // === 交易簽名測試 ===

    @Test
    fun test_17_SignTransaction() = runTest {
        initializeSDK()

        // 創建未簽名交易
        val unsignedTx = UnsignedTransaction(
            rawData = "84a400818258201234567890abcdef...",
            chainType = MultiChainType.CARDANO,
            estimatedFee = TransactionFee(
                gasLimit = "0",
                gasPrice = "0",
                estimatedCost = "0.17",
                usdValue = null,
                priority = TransactionPriority.NORMAL
            ),
            expirationTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() + 7200000,
            metadata = mapOf("network" to "preprod")
        )

        // 生成測試密鑰對
        val accountResult = sdk.generateAccount()
        assertTrue(accountResult is Result.Success)
        val account = (accountResult as Result.Success).data

        // 簽名交易
        val result = sdk.signTransaction(unsignedTx, account.privateKey)

        assertTrue(result is Result.Success)
        val signedTx = (result as Result.Success).data

        assertTrue(signedTx.signature.isNotEmpty())
        assertTrue(signedTx.hash?.isNotEmpty() == true)
        assertTrue(signedTx.rawData.contains("signed_"))
        assertEquals(MultiChainType.CARDANO, signedTx.chainType)
    }

    @Test
    fun test_18_SignTransactionWithInvalidPrivateKey() = runTest {
        val unsignedTx = UnsignedTransaction(
            rawData = "84a400818258201234567890abcdef...",
            chainType = MultiChainType.CARDANO,
            estimatedFee = TransactionFee(
                gasLimit = "0",
                gasPrice = "0",
                estimatedCost = "0.17",
                usdValue = null,
                priority = TransactionPriority.NORMAL
            ),
            expirationTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() + 7200000,
            metadata = mapOf()
        )

        val result = sdk.signTransaction(unsignedTx, "invalid_key")

        assertTrue(result is Result.Failure)
    }

    // === 交易廣播測試 ===

    @Test
    fun test_19_BroadcastSignedTransaction() = runTest {
        initializeSDK()

        val signedTx = SignedTransaction(
            rawData = "signed_84a400818258201234567890abcdef...",
            signature = "abcdef1234567890...",
            chainType = MultiChainType.CARDANO,
            hash = "tx_hash_123456"
        )

        val result = sdk.broadcastTransaction(signedTx)

        assertTrue(result is Result.Success)
        val txHash = (result as Result.Success).data
        assertEquals("tx_hash_123456", txHash.hash)
    }

    @Test
    fun test_20_BroadcastInvalidTransaction() = runTest {
        initializeSDK()

        val invalidTx = SignedTransaction(
            rawData = "invalid_transaction_data",
            signature = "abcdef",
            chainType = MultiChainType.CARDANO,
            hash = "invalid_hash"
        )

        val result = sdk.broadcastTransaction(invalidTx)

        assertTrue(result is Result.Failure)
    }

    // === 交易歷史測試 ===

    @Test
    fun test_21_GetTransactionHistory() = runTest {
        initializeSDK()

        val testAddress = "addr_test1qzf0hdwgj5d6j7h6g46c0w2z0v4r6v8r6v8r6v8r6v8r6v8r6v8r6v8r6v8r6"
        val result = sdk.getTransactionHistory(testAddress, limit = 10, offset = 0)

        assertTrue(result is Result.Success)
        val transactions = (result as Result.Success).data

        assertTrue(transactions.isNotEmpty())
        transactions.forEach { tx ->
            assertTrue(tx.hash.isNotEmpty())
            assertTrue(tx.timestamp > 0)
            assertTrue((tx.blockNumber ?: 0L) > 0)
            assertTrue((tx.fee.toDoubleOrNull() ?: 0.0) > 0)
        }
    }

    // === 網路狀態測試 ===

    @Test
    fun test_22_GetNetworkStatus() = runTest {
        initializeSDK()

        val result = sdk.getNetworkStatus()

        assertTrue(result is Result.Success)
        val status = (result as Result.Success).data

        assertTrue(status.isConnected)
        assertTrue(status.blockHeight > 8_000_000)
        assertEquals("preprod", status.networkId)
        assertEquals(1.0, status.syncProgress)
        assertEquals(20000L, status.averageBlockTime) // Cardano ~20 秒
    }

    // === Staking 功能測試 ===

    @Test
    fun test_23_DelegateToStakePool() = runTest {
        initializeSDK()

        val stakeAddress = "stake_test1uqzf0hdwgj5d6j7h6g46c0w2z0v4r6v8r6v8r6v8r6v8r6v8r6v8r6"
        val poolId = "pool1abc123xyz..."

        val result = sdk.delegateToPool(stakeAddress, poolId)

        assertTrue(result is Result.Success)
        val txHash = (result as Result.Success).data
        assertTrue(txHash.contains("delegation_tx"))
    }

    @Test
    fun test_24_GetStakePoolInfo() = runTest {
        initializeSDK()

        val poolId = "pool1abc123xyz..."
        val result = sdk.getStakePoolInfo(poolId)

        assertTrue(result is Result.Success)
        val poolInfo = (result as Result.Success).data

        assertEquals(poolId, poolInfo["pool_id"])
        assertTrue(poolInfo.containsKey("live_stake"))
        assertTrue(poolInfo.containsKey("margin_cost"))
        assertTrue(poolInfo.containsKey("fixed_cost"))
    }

    // === 輔助函數 ===

    private suspend fun initializeSDK() {
        val config = SDKConfig(
            network = "preprod",
            rpcUrl = "https://cardano-preprod.blockfrost.io/api/v0/",
            apiKey = "preprodXXXXXXXXXXXXXXXXXXXXXXXXXXXX",
            timeout = 30000,
            retryCount = 3
        )
        val result = sdk.initialize(config)
        assertTrue(result is Result.Success)
    }
}

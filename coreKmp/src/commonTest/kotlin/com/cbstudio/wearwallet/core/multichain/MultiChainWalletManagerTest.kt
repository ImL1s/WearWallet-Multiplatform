package com.cbstudio.wearwallet.core.multichain

import com.cbstudio.wearwallet.core.multichain.sdk.*
import com.cbstudio.wearwallet.core.common.Result
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import com.cbstudio.wearwallet.core.security.AllowDevCapabilityGate
import kotlin.test.*

/**
 * 多鏈錢包管理器整合測試
 * 
 * 驗證 MultiChainWalletManager 的完整功能
 */
class MultiChainWalletManagerTest {
    
    private lateinit var walletManager: MultiChainWalletManager
    
    @BeforeTest
    fun setup() {
        walletManager = MultiChainWalletManager.createDefault(AllowDevCapabilityGate())
    }
    
    @AfterTest
    fun teardown() = runTest {
        walletManager.cleanup()
    }
    
    /**
     * 測試錢包管理器初始化
     */
    @Test
    fun testWalletManagerInitialization() = runTest {
        // 驗證初始狀態
        val initialState = walletManager.walletState.first()
        assertFalse(initialState.isInitialized)
        assertEquals(0, initialState.activeChains.size)
        
        // 初始化錢包管理器
        val configs = listOf(
            MultiChainWalletManager.ChainConfig(
                chainType = MultiChainType.SOLANA,
                network = "testnet",
                enabled = true
            ),
            MultiChainWalletManager.ChainConfig(
                chainType = MultiChainType.POLKADOT,
                network = "testnet",
                enabled = true
            ),
            MultiChainWalletManager.ChainConfig(
                chainType = MultiChainType.TRON,
                network = "testnet",
                enabled = false // 測試禁用的鏈
            )
        )
        
        val result = walletManager.initialize(configs)
        assertTrue(result is Result.Success)
        
        // 驗證初始化後狀態
        val newState = walletManager.walletState.first()
        assertTrue(newState.isInitialized)
        assertEquals(2, newState.activeChains.size) // 只有啟用的鏈
        assertTrue(MultiChainType.SOLANA in newState.activeChains)
        assertTrue(MultiChainType.POLKADOT in newState.activeChains)
        assertFalse(MultiChainType.TRON in newState.activeChains)
    }
    
    /**
     * 測試餘額查詢功能
     */
    @Test
    fun testBalanceQuery() = runTest {
        // 初始化
        val result = walletManager.initialize()
        assertTrue(result is Result.Success)
        
        // 準備測試地址
        val addresses = mapOf(
            MultiChainType.SOLANA to "7VfVJLHKdyfEHnNNDpFNAgY3QZJcnPuJWJdMFHX5Nppq",
            MultiChainType.POLKADOT to "15oF4uVJwmo4TdGW7VfQxNLavjCXviqxT9S1MgbjMNHr6Sp5",
            MultiChainType.CARDANO to "addr1qxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
        )
        
        // 查詢餘額
        val balances = walletManager.getAllBalances(addresses)
        assertTrue(balances is Result.Success)
        
        val balanceMap = balances.getOrNull()
        assertNotNull(balanceMap)
        
        // 驗證 Solana 餘額
        val solanaBalance = balanceMap[MultiChainType.SOLANA]
        assertNotNull(solanaBalance)
        assertEquals("SOL", solanaBalance.symbol)
        assertEquals(9, solanaBalance.decimals)
        
        // 驗證狀態更新
        val state = walletManager.walletState.first()
        assertEquals(balanceMap, state.balances)
        assertNotNull(state.portfolioValue)
    }
    
    /**
     * 測試單個鏈餘額查詢
     */
    @Test
    fun testSingleChainBalance() = runTest {
        // 初始化
        walletManager.initialize()
        
        // 查詢 Solana 餘額
        val balance = walletManager.getBalance(
            MultiChainType.SOLANA,
            "7VfVJLHKdyfEHnNNDpFNAgY3QZJcnPuJWJdMFHX5Nppq"
        )
        
        assertNotNull(balance)
        assertEquals("SOL", balance.symbol)
        assertEquals("0", balance.amount) // 測試地址餘額為 0
    }
    
    /**
     * 測試地址驗證
     */
    @Test
    fun testAddressValidation() {
        // Solana 地址驗證
        val solanaValid = walletManager.validateAddress(
            MultiChainType.SOLANA,
            "7VfVJLHKdyfEHnNNDpFNAgY3QZJcnPuJWJdMFHX5Nppq"
        )
        assertTrue(solanaValid is Result.Success)
        assertTrue(solanaValid.getOrNull()?.isValid == true)
        
        val solanaInvalid = walletManager.validateAddress(
            MultiChainType.SOLANA,
            "invalid_address"
        )
        assertTrue(solanaInvalid is Result.Success)
        assertFalse(solanaInvalid.getOrNull()?.isValid == true)
        
        // Polkadot 地址驗證
        val polkadotValid = walletManager.validateAddress(
            MultiChainType.POLKADOT,
            "15oF4uVJwmo4TdGW7VfQxNLavjCXviqxT9S1MgbjMNHr6Sp5"
        )
        assertTrue(polkadotValid is Result.Success)
        assertTrue(polkadotValid.getOrNull()?.isValid == true)
        
        // Monero 地址驗證
        val moneroValid = walletManager.validateAddress(
            MultiChainType.MONERO,
            "44AFFq5kSiGBoZ4NMDwYtN18obc8AemS33DBLWs3H7otXft3XjrpDtQGv7SqSsaBYBb98uNbr2VBBEt7f2wfn3RVGQBEP3A"
        )
        assertTrue(moneroValid is Result.Success)
        assertTrue(moneroValid.getOrNull()?.isValid == true)
    }
    
    /**
     * 測試交易創建和廣播
     */
    @Test
    fun testTransactionFlow() = runTest {
        // 初始化
        walletManager.initialize()
        
        // 創建交易請求
        val request = TransactionRequest(
            fromAddress = "7VfVJLHKdyfEHnNNDpFNAgY3QZJcnPuJWJdMFHX5Nppq",
            toAddress = "8VfVJLHKdyfEHnNNDpFNAgY3QZJcnPuJWJdMFHX5Nppq",
            amount = "0.01",
            priority = TransactionPriority.NORMAL
        )
        
        // 估算手續費
        val feeResult = walletManager.estimateTransactionFee(
            MultiChainType.SOLANA,
            request
        )
        assertTrue(feeResult is Result.Success)
        val fee = feeResult.getOrNull()
        assertNotNull(fee)
        assertEquals(TransactionPriority.NORMAL, fee.priority)
        
        // 創建交易
        val txResult = walletManager.createTransaction(
            MultiChainType.SOLANA,
            request
        )
        assertTrue(txResult is Result.Success)
        val unsignedTx = txResult.getOrNull()
        assertNotNull(unsignedTx)
        assertEquals(MultiChainType.SOLANA, unsignedTx.chainType)
        
        // 模擬簽名（實際應用中需要私鑰）
        val signedTx = SignedTransaction(
            chainType = unsignedTx.chainType,
            rawData = unsignedTx.rawData,
            signature = "mock_signature_" + kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        )
        
        // 廣播交易（在測試環境會失敗，但可以驗證流程）
        val broadcastResult = walletManager.broadcastTransaction(
            MultiChainType.SOLANA,
            signedTx
        )
        // 預期在測試環境失敗
        assertTrue(broadcastResult is Result.Failure)
    }
    
    /**
     * 測試交易歷史查詢
     */
    @Test
    fun testTransactionHistory() = runTest {
        // 初始化
        walletManager.initialize()
        
        // 查詢交易歷史
        val historyResult = walletManager.getTransactionHistory(
            MultiChainType.SOLANA,
            "7VfVJLHKdyfEHnNNDpFNAgY3QZJcnPuJWJdMFHX5Nppq",
            limit = 10
        )
        
        assertTrue(historyResult is Result.Success)
        val transactions = historyResult.getOrNull()
        assertNotNull(transactions)
        assertTrue(transactions.isEmpty()) // 測試地址應該沒有交易
    }
    
    /**
     * 測試網路狀態監控
     */
    @Test
    fun testNetworkStatus() = runTest {
        // 初始化
        walletManager.initialize()
        
        // 查詢單個鏈的網路狀態
        val solanaStatus = walletManager.getNetworkStatus(MultiChainType.SOLANA)
        assertTrue(solanaStatus is Result.Success)
        val status = solanaStatus.getOrNull()
        assertNotNull(status)
        assertTrue(status.isConnected)
        assertEquals(400L, status.averageBlockTime) // Solana 400ms
        
        // 查詢所有鏈的網路狀態
        val allStatuses = walletManager.getAllNetworkStatus()
        assertTrue(allStatuses.isNotEmpty())
        assertTrue(MultiChainType.SOLANA in allStatuses)
        
        // 驗證每個鏈的狀態
        allStatuses.forEach { (chainType, networkStatus) ->
            assertTrue(networkStatus.isConnected)
            assertTrue(networkStatus.averageBlockTime?.let { it > 0 } ?: true)
        }
    }
    
    /**
     * 測試投資組合價值計算
     */
    @Test
    fun testPortfolioValue() = runTest {
        // 初始化
        walletManager.initialize()
        
        // 準備多個地址的餘額
        val addresses = mapOf(
            MultiChainType.SOLANA to "7VfVJLHKdyfEHnNNDpFNAgY3QZJcnPuJWJdMFHX5Nppq",
            MultiChainType.POLKADOT to "15oF4uVJwmo4TdGW7VfQxNLavjCXviqxT9S1MgbjMNHr6Sp5",
            MultiChainType.CARDANO to "addr1qxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
        )
        
        // 查詢餘額（會自動計算投資組合價值）
        walletManager.getAllBalances(addresses)
        
        // 等待狀態更新
        delay(100)
        
        // 驗證投資組合價值
        val state = walletManager.walletState.first()
        val portfolio = state.portfolioValue
        assertNotNull(portfolio)
        assertEquals(0.0, portfolio.totalUsdValue) // 測試地址都是空的
        assertTrue(portfolio.chainBreakdown.isNotEmpty())
    }
    
    /**
     * 測試支援的鏈查詢
     */
    @Test
    fun testSupportedChains() {
        val supportedChains = walletManager.getSupportedChains()
        assertTrue(supportedChains.isNotEmpty())
        assertTrue(MultiChainType.SOLANA in supportedChains)
        assertTrue(MultiChainType.POLKADOT in supportedChains)
        assertTrue(MultiChainType.MONERO in supportedChains)
        assertTrue(MultiChainType.TRON in supportedChains)
        assertTrue(MultiChainType.CARDANO in supportedChains)
    }
    
    /**
     * 測試按功能查詢鏈
     */
    @Test
    fun testChainsWithCapability() {
        // 查詢支援 NFT 的鏈
        val nftChains = walletManager.getChainsWithCapability(SDKCapability.NFT_OPERATIONS)
        assertTrue(MultiChainType.SOLANA in nftChains)
        
        // 查詢支援多重簽名的鏈
        val multiSigChains = walletManager.getChainsWithCapability(SDKCapability.MULTI_SIG_SUPPORT)
        assertTrue(multiSigChains.isNotEmpty())
        
        // 查詢支援 Staking 的鏈
        val stakingChains = walletManager.getChainsWithCapability(SDKCapability.STAKING_OPERATIONS)
        assertTrue(MultiChainType.POLKADOT in stakingChains)
        assertTrue(MultiChainType.TRON in stakingChains)
        assertTrue(MultiChainType.CARDANO in stakingChains)
    }
    
    /**
     * 測試資源清理
     */
    @Test
    fun testCleanup() = runTest {
        // 初始化
        walletManager.initialize()
        
        // 驗證已初始化
        var state = walletManager.walletState.first()
        assertTrue(state.isInitialized)
        assertTrue(state.activeChains.isNotEmpty())
        
        // 清理資源
        walletManager.cleanup()
        
        // 驗證清理後狀態
        state = walletManager.walletState.first()
        assertFalse(state.isInitialized)
        assertTrue(state.activeChains.isEmpty())
        assertTrue(state.balances.isEmpty())
        assertNull(state.portfolioValue)
    }
    
    /**
     * 測試預設 RPC URL
     */
    @Test
    fun testDefaultRpcUrls() = runTest {
        // 使用預設配置初始化
        val result = walletManager.initialize()
        assertTrue(result is Result.Success)
        
        // 驗證所有預設鏈都已啟用
        val state = walletManager.walletState.first()
        assertEquals(5, state.activeChains.size)
        assertTrue(MultiChainType.SOLANA in state.activeChains)
        assertTrue(MultiChainType.POLKADOT in state.activeChains)
        assertTrue(MultiChainType.TRON in state.activeChains)
        assertTrue(MultiChainType.CARDANO in state.activeChains)
        assertTrue(MultiChainType.MONERO in state.activeChains)
    }
    
    /**
     * 測試錯誤處理
     */
    @Test
    fun testErrorHandling() = runTest {
        // 未初始化時查詢餘額應該失敗
        val balanceResult = walletManager.getAllBalances(emptyMap())
        assertTrue(balanceResult is Result.Failure)
        
        // 不支援的鏈應該返回錯誤
        val invalidChain = MultiChainType.ETHEREUM // 假設這個還沒實作
        val txResult = walletManager.createTransaction(
            invalidChain,
            TransactionRequest(
                fromAddress = "test",
                toAddress = "test",
                amount = "1.0"
            )
        )
        assertTrue(txResult is Result.Failure)
    }
}
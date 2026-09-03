package com.cbstudio.wearwallet.core.multichain

import com.cbstudio.wearwallet.core.multichain.service.DefaultBlockchainServiceFactory
import com.cbstudio.wearwallet.core.multichain.service.MultiChainServiceManager
import com.cbstudio.wearwallet.core.multichain.service.solana.SolanaService
import com.cbstudio.wearwallet.core.multichain.service.tron.TronService
import com.cbstudio.wearwallet.core.multichain.service.polkadot.PolkadotService
import com.cbstudio.wearwallet.core.multichain.service.cardano.CardanoService
import com.cbstudio.wearwallet.core.multichain.service.monero.MoneroService
import com.cbstudio.wearwallet.core.multichain.model.TransferRequest
import com.cbstudio.wearwallet.core.multichain.model.ValidationResult
import co.touchlab.kermit.Logger
import co.touchlab.kermit.StaticConfig
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/**
 * 多鏈服務整合測試
 * 測試多鏈架構的核心功能
 */
class MultiChainServiceTest {
    
    private val logger = Logger(
        config = StaticConfig(),
        tag = "MultiChainServiceTest"
    )
    
    private fun createTestServiceManager(): MultiChainServiceManager {
        val factory = DefaultBlockchainServiceFactory()
        return MultiChainServiceManager(factory, logger)
    }
    
    @Test
    fun testServiceFactory() = runTest {
        val factory = DefaultBlockchainServiceFactory()
        
        // 註冊測試服務
        factory.registerService(SolanaService())
        factory.registerService(TronService())
        factory.registerService(PolkadotService())
        factory.registerService(CardanoService())
        factory.registerService(MoneroService())
        
        // 測試服務註冊
        assertTrue(factory.isSupported(MultiChainType.SOLANA))
        assertTrue(factory.isSupported(MultiChainType.TRON))
        assertTrue(factory.isSupported(MultiChainType.POLKADOT))
        assertTrue(factory.isSupported(MultiChainType.CARDANO))
        assertTrue(factory.isSupported(MultiChainType.MONERO))
        
        // 測試獲取服務
        val solanaService = factory.getService(MultiChainType.SOLANA)
        assertNotNull(solanaService)
        assertEquals(MultiChainType.SOLANA, solanaService.supportedChainType)
        
        // 測試支援的鏈列表
        val supportedChains = factory.getSupportedChains()
        assertTrue(supportedChains.contains(MultiChainType.SOLANA))
        assertTrue(supportedChains.contains(MultiChainType.TRON))
        assertTrue(supportedChains.contains(MultiChainType.POLKADOT))
        assertTrue(supportedChains.contains(MultiChainType.CARDANO))
        assertTrue(supportedChains.contains(MultiChainType.MONERO))
    }
    
    @Test
    fun testSolanaService() = runTest {
        val service = SolanaService()
        
        // 測試基本屬性
        assertEquals(MultiChainType.SOLANA, service.supportedChainType)
        
        // 測試地址驗證
        val validAddress = service.validateAddress("11111111111111111111111111111112") // 有效格式示例
        assertTrue(validAddress.isValid)
        
        val invalidAddress = service.validateAddress("invalid_address")
        assertFalse(invalidAddress.isValid)
        assertTrue((invalidAddress as ValidationResult.Invalid).message.contains("Invalid"))
        
        // 測試服務可用性（目前回傳 true，因為是模擬實現）
        assertTrue(service.isServiceAvailable())
    }
    
    @Test
    fun testTronService() = runTest {
        val service = TronService()
        
        // 測試基本屬性
        assertEquals(MultiChainType.TRON, service.supportedChainType)
        
        // 測試地址驗證
        val validAddress = service.validateAddress("TRX9a5u28v32eNHqRNz3WuZ9jz4W6K2abc") // 34字符
        assertTrue(validAddress.isValid)
        
        val invalidAddress = service.validateAddress("invalid_tron_address")
        assertFalse(invalidAddress.isValid)
        
        // 測試手續費估算
        val request = TransferRequest(
            chainType = MultiChainType.TRON,
            fromAddress = "TRX9a5u28v32eNHqRNz3WuZ9jz4W6K2abc",
            toAddress = "TRX9a5u28v32eNHqRNz3WuZ9jz4W6K3def",
            amount = "100"
        )
        val fee = service.estimateFee(request)
        assertTrue(fee.toDoubleOrNull() != null && fee.toDouble() > 0)
    }
    
    @Test
    fun testPolkadotService() = runTest {
        val service = PolkadotService()
        
        // 測試基本屬性
        assertEquals(MultiChainType.POLKADOT, service.supportedChainType)
        
        // 測試地址驗證（SS58 格式）
        val validAddress = service.validateAddress("15oF4uVJwmo4TdGW7VfQxNLavjCXviqxT9S1MgbjMNHr6Sp5")
        assertTrue(validAddress.isValid)
        
        val invalidAddress = service.validateAddress("invalid_dot_address")
        assertFalse(invalidAddress.isValid)
    }
    
    @Test
    fun testCardanoService() = runTest {
        val service = CardanoService()
        
        // 測試基本屬性
        assertEquals(MultiChainType.CARDANO, service.supportedChainType)
        
        // 測試地址驗證（Bech32 格式）
        val validAddress = service.validateAddress("addr1qx2fxv2umyhttkxyxp8x0dlpdt3k6cwng5pxj3jhsydzer3jcu5d8ps7zex2k2xt3uqxgjqnnj83ws8lhrn648jjxtwq2ytjqp")
        assertTrue(validAddress.isValid)
        
        val invalidAddress = service.validateAddress("invalid_ada_address")
        assertFalse(invalidAddress.isValid)
    }
    
    @Test
    fun testMoneroService() = runTest {
        val service = MoneroService()
        
        // 測試基本屬性
        assertEquals(MultiChainType.MONERO, service.supportedChainType)
        
        // 測試地址驗證
        val validAddress = service.validateAddress("42ey1afDFnn4886T7196doS9GPMzexD9gXpsZJDwVjeRVdFCSoHnv7KPbBeGpzJBzHRCAs9UxqeoyFQMYbqSWYTfJJQAWDm")
        assertTrue(validAddress.isValid)
        
        val invalidAddress = service.validateAddress("invalid_xmr_address")
        assertFalse(invalidAddress.isValid)
    }
    
    @Test
    fun testTransferRequestValidation() = runTest {
        // 測試有效的轉帳請求
        val validRequest = TransferRequest(
            chainType = MultiChainType.SOLANA,
            fromAddress = "11111111111111111111111111111112",
            toAddress = "11111111111111111111111111111113",
            amount = "1.5"
        )
        assertTrue(validRequest.validate().isValid)
        
        // 測試無效金額
        val invalidAmountRequest = TransferRequest(
            chainType = MultiChainType.SOLANA,
            fromAddress = "11111111111111111111111111111112",
            toAddress = "11111111111111111111111111111113",
            amount = "-1"
        )
        val amountResult = invalidAmountRequest.validate()
        assertFalse(amountResult.isValid)
        assertTrue((amountResult as ValidationResult.Invalid).message.contains("Invalid amount"))
        
        // 測試空地址
        val emptyAddressRequest = TransferRequest(
            chainType = MultiChainType.SOLANA,
            fromAddress = "",
            toAddress = "11111111111111111111111111111113",
            amount = "1.0"
        )
        val addressResult = emptyAddressRequest.validate()
        assertFalse(addressResult.isValid)
        assertTrue((addressResult as ValidationResult.Invalid).message.contains("cannot be empty"))
        
        // 測試自轉
        val selfTransferRequest = TransferRequest(
            chainType = MultiChainType.SOLANA,
            fromAddress = "11111111111111111111111111111112",
            toAddress = "11111111111111111111111111111112",
            amount = "1.0"
        )
        val selfResult = selfTransferRequest.validate()
        assertFalse(selfResult.isValid)
        assertTrue((selfResult as ValidationResult.Invalid).message.contains("same address"))
    }
    
    @Test
    fun testMultiChainTypes() = runTest {
        // 測試所有鏈類型
        val allChains = MultiChainType.getAllChains()
        assertTrue(allChains.contains(MultiChainType.BITCOIN))
        assertTrue(allChains.contains(MultiChainType.ETHEREUM))
        assertTrue(allChains.contains(MultiChainType.SOLANA))
        assertTrue(allChains.contains(MultiChainType.TRON))
        assertTrue(allChains.contains(MultiChainType.POLKADOT))
        assertTrue(allChains.contains(MultiChainType.CARDANO))
        assertTrue(allChains.contains(MultiChainType.MONERO))
        
        // 測試新增的鏈
        val newChains = MultiChainType.getNewChains()
        assertEquals(5, newChains.size)
        assertTrue(newChains.contains(MultiChainType.SOLANA))
        assertTrue(newChains.contains(MultiChainType.TRON))
        assertTrue(newChains.contains(MultiChainType.POLKADOT))
        assertTrue(newChains.contains(MultiChainType.CARDANO))
        assertTrue(newChains.contains(MultiChainType.MONERO))
        
        // 測試符號查找
        assertEquals(MultiChainType.SOLANA, MultiChainType.fromSymbol("SOL"))
        assertEquals(MultiChainType.TRON, MultiChainType.fromSymbol("trx")) // 不區分大小寫
        assertEquals(null, MultiChainType.fromSymbol("UNKNOWN"))
        
        // 測試鏈類型分類
        assertTrue(MultiChainType.isUtxoChain(MultiChainType.BITCOIN))
        assertFalse(MultiChainType.isUtxoChain(MultiChainType.ETHEREUM))
        
        assertTrue(MultiChainType.isAccountChain(MultiChainType.ETHEREUM))
        assertTrue(MultiChainType.isAccountChain(MultiChainType.SOLANA))
        assertFalse(MultiChainType.isAccountChain(MultiChainType.BITCOIN))
        
        assertTrue(MultiChainType.isPrivacyChain(MultiChainType.MONERO))
        assertFalse(MultiChainType.isPrivacyChain(MultiChainType.BITCOIN))
    }
    
    @Test
    fun testServiceManager() = runTest {
        val manager = createTestServiceManager()
        manager.initializeServices()
        
        // 測試多鏈餘額查詢（模擬）
        val chains = listOf(
            MultiChainType.SOLANA,
            MultiChainType.TRON,
            MultiChainType.POLKADOT
        )
        
        val balances = manager.getMultiChainBalances(
            address = "test_address",
            chains = chains
        )
        
        // 檢查是否回傳了所有請求的鏈
        assertEquals(chains.size, balances.size)
        assertTrue(balances.containsKey(MultiChainType.SOLANA))
        assertTrue(balances.containsKey(MultiChainType.TRON))
        assertTrue(balances.containsKey(MultiChainType.POLKADOT))
    }
    
    @Test
    fun testExplorerUrls() = runTest {
        val btcTx = com.cbstudio.wearwallet.core.multichain.model.MultiChainTransaction(
            hash = "test_hash",
            chainType = MultiChainType.BITCOIN,
            fromAddress = "from",
            toAddress = "to",
            amount = "1.0",
            fee = "0.001",
            timestamp = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
            status = com.cbstudio.wearwallet.core.multichain.model.TransactionStatus.CONFIRMED
        )
        assertTrue(btcTx.getExplorerUrl().contains("blockstream.info"))
        
        val solTx = btcTx.copy(
            chainType = MultiChainType.SOLANA,
            hash = "sol_hash"
        )
        assertTrue(solTx.getExplorerUrl().contains("solscan.io"))
    }
}
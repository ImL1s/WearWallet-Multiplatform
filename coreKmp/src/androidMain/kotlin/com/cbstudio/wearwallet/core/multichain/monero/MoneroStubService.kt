package com.cbstudio.wearwallet.core.multichain.monero

import com.cbstudio.wearwallet.core.common.Result
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * Monero Stub 服務 - 用於測試的完整模擬實現
 * 模擬真實的錢包同步、轉帳和交易紀錄
 */
class MoneroStubService {
    
    // 模擬的錢包數據
    private val wallets = mutableMapOf<String, WalletData>()
    private var currentWallet: WalletData? = null
    
    companion object {
        // 測試錢包資料
        private val TEST_WALLETS = mapOf(
            // Stub 錢包 A (XMR25 - 25字, fake mnemonic for testing only)
            "abbey abbey abbey abbey abbey abbey abbey abbey abbey abbey abbey abbey abbey abbey abbey abbey abbey abbey abbey abbey abbey abbey abbey abbey abbey" to WalletData(
                address = "55jWjdFJ92uDpAdP5oqdcoC2JF3xoDjc4XUjyVzr5Hg7cQXxqn1bkdoZg81dsMWAgJ9a6GqNBdna7c7S7JKaHKmnMbyZUdT",
                viewKey = "4c9170e5fe44e8a4bb29fe1e9507efb917c0d9e54e2000ccc0cb628f41fce60f",
                spendKey = "7f0b57e4b7e8b8a9d0e9a8b7c6d5e4f3a2b1c0d9e8f7a6b5c4d3e2f1a0b9c8d7",
                balance = BigDecimal.parseString("10.5"),
                unlockedBalance = BigDecimal.parseString("8.5"),
                transactions = mutableListOf(
                    TransactionData(
                        hash = "a1b2c3d4e5f6789012345678901234567890123456789012345678901234567890",
                        amount = BigDecimal.parseString("5.0"),
                        isIncoming = true,
                        confirmations = 100,
                        timestamp = System.currentTimeMillis() - 86400000 * 7 // 7天前
                    ),
                    TransactionData(
                        hash = "b2c3d4e5f67890123456789012345678901234567890123456789012345678901a",
                        amount = BigDecimal.parseString("3.5"),
                        isIncoming = true,
                        confirmations = 50,
                        timestamp = System.currentTimeMillis() - 86400000 * 3 // 3天前
                    ),
                    TransactionData(
                        hash = "c3d4e5f678901234567890123456789012345678901234567890123456789012ab",
                        amount = BigDecimal.parseString("1.0"),
                        isIncoming = false,
                        confirmations = 20,
                        timestamp = System.currentTimeMillis() - 86400000 // 1天前
                    )
                )
            ),
            
            // Rookie 錢包 (BIP39 - 12字)
            "rookie abuse frozen luxury science hat alert avoid car lemon day cost" to WalletData(
                address = "59VLav8QsYdGgYVdKUzRRaKAPMFPvXmLCyCW8wNBwB27ezFwXGSBMYpGLpFPAqbCdYdc9HvKs86Eo7kPKqX3gQCx6rKC6rZ",
                viewKey = "5e8170e5fe44e8a4bb29fe1e9507efb917c0d9e54e2000ccc0cb628f41fce60f",
                spendKey = "8f0b57e4b7e8b8a9d0e9a8b7c6d5e4f3a2b1c0d9e8f7a6b5c4d3e2f1a0b9c8d7",
                balance = BigDecimal.parseString("2.0"),
                unlockedBalance = BigDecimal.parseString("2.0"),
                transactions = mutableListOf(
                    TransactionData(
                        hash = "d4e5f678901234567890123456789012345678901234567890123456789012bcd",
                        amount = BigDecimal.parseString("2.0"),
                        isIncoming = true,
                        confirmations = 200,
                        timestamp = System.currentTimeMillis() - 86400000 * 10 // 10天前
                    )
                )
            ),
            
            // Iron 錢包 (新的，用於接收) — invalid BIP39 placeholder (not a real seed)
            "zzzz notaword stubfixture monero receive only never use onchain funds xx yy zz aa bb" to WalletData(
                address = "5ARKsdfJLu2UDXL5XsVpKqZ7h9iec7A3f4j2MBg4GCLm9YqoK5BoGzKaHje3TJ9nFbR8fPyH7VPzBp6xvqhVKPgp67Nw7Kj",
                viewKey = "6f8170e5fe44e8a4bb29fe1e9507efb917c0d9e54e2000ccc0cb628f41fce60f",
                spendKey = "9f0b57e4b7e8b8a9d0e9a8b7c6d5e4f3a2b1c0d9e8f7a6b5c4d3e2f1a0b9c8d7",
                balance = BigDecimal.parseString("0.0"),
                unlockedBalance = BigDecimal.parseString("0.0"),
                transactions = mutableListOf()
            )
        )
        
        const val STAGENET_NODE = "http://54.153.251.193:38081"
        const val MAINNET_NODE = "http://opennode.xmr-tw.org:18089"
        
        // 1 XMR = 10^12 atomic units
        val ATOMIC_UNITS = BigDecimal.fromLong(1000000000000L)
    }
    
    init {
        // 初始化測試錢包
        TEST_WALLETS.forEach { (mnemonic, data) ->
            wallets[mnemonic] = data.copy()
        }
    }
    
    /**
     * 從助記詞創建錢包並連接到節點
     */
    suspend fun createWalletFromMnemonic(
        mnemonic: String,
        network: String = "stagenet",
        nodeUrl: String? = null
    ): Result<WalletInfo> = withContext(Dispatchers.IO) {
        try {
            println("\n════════════════════════════════════════")
            println("🎭 STUB MODE - 模擬 Monero 錢包")
            println("════════════════════════════════════════")
            
            // 檢查是否為已知錢包
            val walletData = wallets[mnemonic] ?: {
                // 創建新錢包
                val wordCount = mnemonic.split(" ").size
                val address = when (wordCount) {
                    25 -> "55XMR25Generated${Random.nextInt(1000, 9999)}" + "x".repeat(60)
                    12 -> "59BIP39Generated${Random.nextInt(1000, 9999)}" + "x".repeat(60)
                    else -> "5XUnknownGenerated${Random.nextInt(1000, 9999)}" + "x".repeat(57)
                }
                
                WalletData(
                    address = address,
                    viewKey = "generated_view_key_${Random.nextInt(1000000, 9999999)}",
                    spendKey = "generated_spend_key_${Random.nextInt(1000000, 9999999)}",
                    balance = BigDecimal.ZERO,
                    unlockedBalance = BigDecimal.ZERO,
                    transactions = mutableListOf()
                ).also {
                    wallets[mnemonic] = it
                }
            }()
            
            currentWallet = walletData
            
            // 模擬連接節點
            val rpcUrl = nodeUrl ?: when (network) {
                "mainnet" -> MAINNET_NODE
                else -> STAGENET_NODE
            }
            
            println("📡 模擬連接到節點: $rpcUrl")
            delay(500) // 模擬網路延遲
            
            val wordCount = mnemonic.split(" ").size
            println("🔑 錢包類型: ${if (wordCount == 25) "XMR25 (Monero原生)" else if (wordCount == 12) "BIP39" else "未知"}")
            println("📍 地址: ${walletData.address.take(30)}...")
            println("💰 餘額: ${walletData.balance} XMR")
            println("✅ 模擬錢包創建成功")
            
            Result.Success(
                WalletInfo(
                    address = walletData.address,
                    viewKey = walletData.viewKey,
                    spendKey = walletData.spendKey,
                    network = network,
                    nodeUrl = rpcUrl,
                    currentHeight = 2800000 + Random.nextLong(1000)
                )
            )
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    /**
     * 查詢餘額（模擬同步）
     */
    suspend fun getBalance(): Result<BalanceInfo> = withContext(Dispatchers.IO) {
        try {
            val wallet = currentWallet ?: return@withContext Result.Failure(Exception("錢包未初始化"))
            
            println("\n🔄 開始模擬同步錢包...")
            
            // 模擬同步過程
            val totalSteps = 10
            for (i in 1..totalSteps) {
                val progress = (i * 100.0 / totalSteps)
                println("同步進度: ${progress.toInt()}% [${"█".repeat(i)}${"░".repeat(totalSteps - i)}]")
                delay(200)
            }
            
            println("✅ 錢包同步完成!")
            println("\n💰 餘額資訊:")
            println("  總額: ${wallet.balance} XMR")
            println("  可用: ${wallet.unlockedBalance} XMR")
            println("  鎖定: ${wallet.balance - wallet.unlockedBalance} XMR")
            
            Result.Success(
                BalanceInfo(
                    totalBalance = wallet.balance,
                    unlockedBalance = wallet.unlockedBalance,
                    lockedBalance = wallet.balance - wallet.unlockedBalance,
                    walletHeight = 2800000,
                    daemonHeight = 2800100,
                    isSynced = true
                )
            )
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    /**
     * 獲取交易歷史
     */
    suspend fun getTransactions(): Result<List<TransactionInfo>> = withContext(Dispatchers.IO) {
        try {
            val wallet = currentWallet ?: return@withContext Result.Failure(Exception("錢包未初始化"))
            
            println("\n📜 獲取交易歷史...")
            delay(500)
            
            val transactions = wallet.transactions.map { tx ->
                TransactionInfo(
                    hash = tx.hash,
                    height = 2800000L - tx.confirmations.toLong(),
                    timestamp = tx.timestamp,
                    amount = tx.amount,
                    fee = BigDecimal.parseString("0.0001"),
                    isIncoming = tx.isIncoming,
                    isConfirmed = tx.confirmations > 10,
                    confirmations = tx.confirmations
                )
            }
            
            println("找到 ${transactions.size} 筆交易:")
            transactions.forEach { tx ->
                val type = if (tx.isIncoming) "📥 接收" else "📤 發送"
                println("  $type ${tx.amount} XMR (${tx.confirmations} 確認)")
            }
            
            Result.Success(transactions)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    /**
     * 創建並發送交易（模擬）
     */
    suspend fun sendTransaction(
        toAddress: String,
        amount: BigDecimal,
        priority: Int = 1
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val wallet = currentWallet ?: return@withContext Result.Failure(Exception("錢包未初始化"))
            
            println("\n📤 創建交易...")
            println("  接收地址: ${toAddress.take(30)}...")
            println("  金額: $amount XMR")
            println("  手續費: 0.0001 XMR")
            
            // 檢查餘額
            if (wallet.unlockedBalance < amount + BigDecimal.parseString("0.0001")) {
                return@withContext Result.Failure(Exception("餘額不足"))
            }
            
            // 模擬交易創建
            delay(1000)
            println("⏳ 簽名交易...")
            delay(500)
            println("📡 廣播交易...")
            delay(500)
            
            // 生成交易 hash
            val txHash = "tx_${System.currentTimeMillis()}_${Random.nextInt(1000000, 9999999)}"
            
            // 更新發送方餘額
            wallet.unlockedBalance -= (amount + BigDecimal.parseString("0.0001"))
            wallet.balance -= (amount + BigDecimal.parseString("0.0001"))
            
            // 添加交易記錄
            wallet.transactions.add(
                TransactionData(
                    hash = txHash,
                    amount = amount,
                    isIncoming = false,
                    confirmations = 0,
                    timestamp = System.currentTimeMillis()
                )
            )
            
            // 更新接收方餘額（如果是測試錢包）
            TEST_WALLETS.entries.find { it.value.address == toAddress }?.let { (mnemonic, _) ->
                wallets[mnemonic]?.let { receiverWallet ->
                    receiverWallet.balance += amount
                    receiverWallet.transactions.add(
                        TransactionData(
                            hash = txHash,
                            amount = amount,
                            isIncoming = true,
                            confirmations = 0,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                    println("✅ 接收方錢包已更新")
                }
            }
            
            println("✅ 交易已發送!")
            println("📝 交易 Hash: $txHash")
            
            Result.Success(txHash)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    /**
     * 關閉錢包
     */
    fun close() {
        currentWallet = null
        println("📪 錢包已關閉")
    }
    
    // 數據類
    data class WalletInfo(
        val address: String,
        val viewKey: String,
        val spendKey: String,
        val network: String,
        val nodeUrl: String,
        val currentHeight: Long
    )
    
    data class BalanceInfo(
        val totalBalance: BigDecimal,
        val unlockedBalance: BigDecimal,
        val lockedBalance: BigDecimal,
        val walletHeight: Long,
        val daemonHeight: Long,
        val isSynced: Boolean
    )
    
    data class TransactionInfo(
        val hash: String,
        val height: Long,
        val timestamp: Long,
        val amount: BigDecimal,
        val fee: BigDecimal,
        val isIncoming: Boolean,
        val isConfirmed: Boolean,
        val confirmations: Int
    )
    
    // 內部數據類
    private data class WalletData(
        val address: String,
        val viewKey: String,
        val spendKey: String,
        var balance: BigDecimal,
        var unlockedBalance: BigDecimal,
        val transactions: MutableList<TransactionData>
    )
    
    private data class TransactionData(
        val hash: String,
        val amount: BigDecimal,
        val isIncoming: Boolean,
        val confirmations: Int,
        val timestamp: Long
    )
}
package com.cbstudio.wearwallet.core.multichain.monero

import android.content.Context
import com.cbstudio.wearwallet.core.common.Result
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import monero.common.MoneroRpcConnection
import monero.daemon.MoneroDaemonRpc
import monero.wallet.MoneroWalletFull
import monero.wallet.model.MoneroWalletConfig
import monero.daemon.model.MoneroNetworkType

/**
 * Monero 錢包服務
 * 優先使用 Monerujo native library，失敗時回退到 monero-java
 */
class MoneroWalletService(private val context: Context? = null) {
    
    // Monerujo 實現（優先）
    private var monerujoService: MonerujoWalletService? = null
    
    // Stub 實現（測試和備用）
    private var stubService: MoneroStubService? = null
    
    // monero-java 實現（備用）
    private var daemon: MoneroDaemonRpc? = null
    private var wallet: MoneroWalletFull? = null
    
    // 標記使用哪個實現
    private enum class ServiceType {
        MONERUJO, STUB, MONERO_JAVA
    }
    private var serviceType = ServiceType.STUB
    
    companion object {
        const val STAGENET_NODE = "http://54.153.251.193:38081"
        const val MAINNET_NODE = "http://opennode.xmr-tw.org:18089"
        
        private var librariesLoaded = false
        
        fun ensureLibrariesLoaded(context: Context?): Boolean {
            // 優先嘗試載入 Monerujo
            if (context != null && MonerujoJNIWrapper.isLibraryLoaded()) {
                println("✅ 使用 Monerujo native library")
                return true
            }
            
            if (librariesLoaded) {
                return true
            }
            
            // 嘗試直接載入 monero-java
            if (MoneroNativeLoader.tryDirectLoad()) {
                librariesLoaded = true
                println("✅ 使用 monero-java native library")
                return true
            }
            
            // 如果有 context，嘗試從 JAR 提取並載入
            if (context != null) {
                if (MoneroNativeLoader.loadLibraries(context)) {
                    librariesLoaded = true
                    println("✅ 使用提取的 monero-java native library")
                    return true
                }
            }
            
            println("⚠️ 無法載入任何 Monero native libraries，使用 Stub 模式")
            return false
        }
    }
    
    init {
        // 檢測是否在測試環境中
        val isInstrumentationTest = try {
            Class.forName("androidx.test.InstrumentationRegistry")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
        
        // 檢查是否強制使用真實實現
        val forceRealImplementation = System.getProperty("force.real.monero", "false") == "true"
        
        // 初始化時決定使用哪個實現
        when {
            // 優先使用 Monerujo 如果已載入（即使在測試環境）
            context != null && MonerujoJNIWrapper.isLibraryLoaded() -> {
                monerujoService = MonerujoWalletService(context)
                serviceType = ServiceType.MONERUJO
                println("🚀 使用 Monerujo 實現（高效能本地同步）")
            }
            // 強制使用真實實現或在測試環境中需要真實功能
            forceRealImplementation || (isInstrumentationTest && MonerujoJNIWrapper.isLibraryLoaded()) -> {
                if (context != null) {
                    monerujoService = MonerujoWalletService(context)
                    serviceType = ServiceType.MONERUJO
                    println("🚀 使用 Monerujo 實現（測試模式 - 真實功能）")
                } else {
                    serviceType = ServiceType.MONERO_JAVA
                    println("📦 使用 monero-java 實現（真實網路測試）")
                }
            }
            // 強制使用 monero-java 進行真實測試
            System.getProperty("force.monero.java", "false") == "true" -> {
                serviceType = ServiceType.MONERO_JAVA
                println("📦 強制使用 monero-java 實現（真實網路測試）")
            }
            ensureLibrariesLoaded(context) -> {
                serviceType = ServiceType.MONERO_JAVA
                println("📦 使用 monero-java 實現")
            }
            // 只在完全沒有真實實現可用時才使用 Stub
            else -> {
                stubService = MoneroStubService()
                serviceType = ServiceType.STUB
                println("⚠️ 使用 Stub 實現（無真實庫可用）")
            }
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
        when (serviceType) {
            ServiceType.MONERUJO -> {
                monerujoService?.let { service ->
                    val result = service.createWalletFromMnemonic(mnemonic, network, nodeUrl)
                    if (result is Result.Success) {
                        return@withContext Result.Success(
                            WalletInfo(
                                address = result.data.address,
                                viewKey = result.data.viewKey,
                                spendKey = result.data.spendKey,
                                network = result.data.network,
                                nodeUrl = result.data.nodeUrl,
                                currentHeight = result.data.currentHeight
                            )
                        )
                    }
                }
                println("⚠️ Monerujo 失敗，嘗試備用方案...")
            }
            
            ServiceType.STUB -> {
                stubService?.let { service ->
                    val result = service.createWalletFromMnemonic(mnemonic, network, nodeUrl)
                    if (result is Result.Success) {
                        return@withContext Result.Success(
                            WalletInfo(
                                address = result.data.address,
                                viewKey = result.data.viewKey,
                                spendKey = result.data.spendKey,
                                network = result.data.network,
                                nodeUrl = result.data.nodeUrl,
                                currentHeight = result.data.currentHeight
                            )
                        )
                    }
                }
            }
            
            ServiceType.MONERO_JAVA -> {
                // monero-java 實現
            }
        }
        
        // 使用 monero-java 實現作為最後備用
        try {
            // 選擇節點
            val rpcUrl = nodeUrl ?: when (network) {
                "mainnet" -> MAINNET_NODE
                else -> STAGENET_NODE
            }
            
            // 連接到 daemon
            daemon = MoneroDaemonRpc(MoneroRpcConnection(rpcUrl))
            
            // 驗證連接
            val height = daemon?.getHeight() ?: 0
            println("連接到節點成功，當前區塊高度: $height")
            
            // 選擇網路類型
            val networkType = when (network) {
                "mainnet" -> MoneroNetworkType.MAINNET
                "testnet" -> MoneroNetworkType.TESTNET  
                else -> MoneroNetworkType.STAGENET
            }
            
            // 創建錢包配置
            val walletConfig = MoneroWalletConfig()
                .setPath("") // 內存錢包
                .setPassword("")
                .setNetworkType(networkType)
                .setSeed(mnemonic)
                .setRestoreHeight(height - 1000) // 從最近1000個區塊開始同步
                .setServerUri(rpcUrl)
            
            // 創建錢包
            wallet = MoneroWalletFull.createWallet(walletConfig)
            
            val address = wallet?.getPrimaryAddress() ?: ""
            val viewKey = wallet?.getPrivateViewKey() ?: ""
            val spendKey = wallet?.getPrivateSpendKey() ?: ""
            
            Result.Success(
                WalletInfo(
                    address = address,
                    viewKey = viewKey,
                    spendKey = spendKey,
                    network = network,
                    nodeUrl = rpcUrl,
                    currentHeight = height
                )
            )
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    /**
     * 查詢餘額（自動同步）
     */
    suspend fun getBalance(): Result<BalanceInfo> = withContext(Dispatchers.IO) {
        // 使用對應的服務實現
        when (serviceType) {
            ServiceType.MONERUJO -> {
                monerujoService?.let { service ->
                    val result = service.getBalance()
                    if (result is Result.Success) {
                        return@withContext Result.Success(
                            BalanceInfo(
                                totalBalance = result.data.totalBalance,
                                unlockedBalance = result.data.unlockedBalance,
                                lockedBalance = result.data.lockedBalance,
                                walletHeight = result.data.walletHeight,
                                daemonHeight = result.data.daemonHeight,
                                isSynced = result.data.isSynced
                            )
                        )
                    }
                }
            }
            
            ServiceType.STUB -> {
                stubService?.let { service ->
                    val result = service.getBalance()
                    if (result is Result.Success) {
                        return@withContext Result.Success(
                            BalanceInfo(
                                totalBalance = result.data.totalBalance,
                                unlockedBalance = result.data.unlockedBalance,
                                lockedBalance = result.data.lockedBalance,
                                walletHeight = result.data.walletHeight,
                                daemonHeight = result.data.daemonHeight,
                                isSynced = result.data.isSynced
                            )
                        )
                    }
                }
            }
            
            ServiceType.MONERO_JAVA -> {
                // monero-java 實現
            }
        }
        
        // monero-java 備用實現
        try {
            if (wallet == null) {
                return@withContext Result.Failure(Exception("錢包未初始化"))
            }
            
            println("開始同步錢包...")
            
            // 同步錢包（掃描區塊鏈）
            wallet?.sync()
            
            // 獲取餘額
            val balance = wallet?.getBalance() ?: java.math.BigInteger.ZERO
            val unlockedBalance = wallet?.getUnlockedBalance() ?: java.math.BigInteger.ZERO
            val lockedBalance = balance - unlockedBalance
            
            // 獲取當前高度
            val height = wallet?.getHeight() ?: 0L
            val daemonHeight = daemon?.getHeight() ?: 0L
            
            // 轉換為 XMR (1 XMR = 10^12 atomic units)
            val divisor = BigDecimal.fromLong(1000000000000L)
            val balanceXMR = BigDecimal.fromLong(balance.toLong()).divide(divisor)
            val unlockedXMR = BigDecimal.fromLong(unlockedBalance.toLong()).divide(divisor)
            val lockedXMR = BigDecimal.fromLong(lockedBalance.toLong()).divide(divisor)
            
            println("同步完成！")
            println("餘額: $balanceXMR XMR")
            println("可用餘額: $unlockedXMR XMR")
            println("鎖定餘額: $lockedXMR XMR")
            println("錢包高度: $height / $daemonHeight")
            
            Result.Success(
                BalanceInfo(
                    totalBalance = balanceXMR,
                    unlockedBalance = unlockedXMR,
                    lockedBalance = lockedXMR,
                    walletHeight = height,
                    daemonHeight = daemonHeight,
                    isSynced = height >= daemonHeight - 1L
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
        // 使用對應的服務實現
        when (serviceType) {
            ServiceType.MONERUJO -> {
                monerujoService?.let { service ->
                    val result = service.getTransactions()
                    if (result is Result.Success) {
                        return@withContext Result.Success(
                            result.data.map { tx ->
                                TransactionInfo(
                                    hash = tx.hash,
                                    height = tx.height,
                                    timestamp = tx.timestamp,
                                    amount = tx.amount,
                                    fee = tx.fee,
                                    isIncoming = tx.isIncoming,
                                    isConfirmed = tx.isConfirmed,
                                    confirmations = tx.confirmations
                                )
                            }
                        )
                    }
                }
            }
            
            ServiceType.STUB -> {
                stubService?.let { service ->
                    val result = service.getTransactions()
                    if (result is Result.Success) {
                        return@withContext Result.Success(
                            result.data.map { tx ->
                                TransactionInfo(
                                    hash = tx.hash,
                                    height = tx.height,
                                    timestamp = tx.timestamp,
                                    amount = tx.amount,
                                    fee = tx.fee,
                                    isIncoming = tx.isIncoming,
                                    isConfirmed = tx.isConfirmed,
                                    confirmations = tx.confirmations
                                )
                            }
                        )
                    }
                }
            }
            
            ServiceType.MONERO_JAVA -> {
                // monero-java 實現
            }
        }
        
        // monero-java 備用實現
        try {
            if (wallet == null) {
                return@withContext Result.Failure(Exception("錢包未初始化"))
            }
            
            // 同步錢包
            wallet?.sync()
            
            // 獲取交易
            val txs = wallet?.getTxs() ?: emptyList()
            
            val transactions = txs.map { tx ->
                TransactionInfo(
                    hash = tx.hash ?: "",
                    height = tx.height?.toLong() ?: 0L,
                    timestamp = tx.unlockTime?.toLong() ?: 0L,
                    amount = BigDecimal.fromLong((tx.outgoingAmount ?: java.math.BigInteger.ZERO).toLong()).divide(BigDecimal.fromLong(1000000000000L)),
                    fee = BigDecimal.fromLong((tx.fee ?: java.math.BigInteger.ZERO).toLong()).divide(BigDecimal.fromLong(1000000000000L)),
                    isIncoming = tx.isIncoming ?: false,
                    isConfirmed = tx.isConfirmed ?: false,
                    confirmations = tx.numConfirmations?.toInt() ?: 0
                )
            }
            
            Result.Success(transactions)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    /**
     * 創建並發送交易
     */
    suspend fun sendTransaction(
        toAddress: String,
        amount: BigDecimal,
        priority: Int = 1
    ): Result<String> = withContext(Dispatchers.IO) {
        // 使用對應的服務實現
        when (serviceType) {
            ServiceType.MONERUJO -> {
                monerujoService?.let { service ->
                    return@withContext service.sendTransaction(toAddress, amount, priority)
                }
            }
            
            ServiceType.STUB -> {
                stubService?.let { service ->
                    return@withContext service.sendTransaction(toAddress, amount, priority)
                }
            }
            
            ServiceType.MONERO_JAVA -> {
                // monero-java 實現
            }
        }
        
        // monero-java 備用實現
        try {
            if (wallet == null) {
                return@withContext Result.Failure(Exception("錢包未初始化"))
            }
            
            // 同步錢包
            wallet?.sync()
            
            // 轉換金額為 atomic units (1 XMR = 10^12 atomic units)
            val atomicMultiplier = BigDecimal.fromLong(1000000000000L)
            val amountAtomic = amount.multiply(atomicMultiplier).toString().toLong()
            
            // 創建交易配置
            val txConfig = monero.wallet.model.MoneroTxConfig()
                .setAccountIndex(0)
                .setAddress(toAddress)
                .setAmount(java.math.BigInteger.valueOf(amountAtomic))
                .setRelay(true) // 自動廣播
            
            // 創建並發送交易
            val tx = wallet?.createTx(txConfig)
            val hash = tx?.hash ?: ""
            
            println("交易已發送!")
            println("交易 Hash: $hash")
            println("手續費: ${tx?.fee} atomic units")
            
            Result.Success(hash)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    /**
     * 關閉錢包
     */
    fun close() {
        when (serviceType) {
            ServiceType.MONERUJO -> monerujoService?.close()
            ServiceType.STUB -> stubService?.close()
            ServiceType.MONERO_JAVA -> {
                wallet?.close()
                wallet = null
                daemon = null
            }
        }
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
}
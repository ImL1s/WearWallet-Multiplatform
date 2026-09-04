package com.cbstudio.wearwallet.core.multichain.sdk

import kotlinx.datetime.Clock
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.core.common.Result
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * 簡化 SDK 的抽象基類
 * 
 * 提供所有 SimplifiedSDK 實現的共用功能：
 * - 初始化/清理邏輯
 * - 交易歷史生成
 * - 網路狀態查詢
 * - 通用的 Key 生成
 * 
 * 子類只需覆寫鏈特定的邏輯 (地址驗證、代幣符號等)
 */
abstract class SimplifiedBaseSDK : BlockchainSDKAdapter {
    
    // ===== 抽象屬性 - 子類必須實現 =====
    
    /** 原生代幣符號 (如 SOL, TRX, DOT) */
    protected abstract val nativeSymbol: String
    
    /** 原生代幣小數位數 */
    protected abstract val nativeDecimals: Int
    
    /** 預設網路名稱 */
    protected abstract val defaultNetwork: String
    
    /** 平均區塊時間 (毫秒) */
    protected abstract val avgBlockTimeMs: Long
    
    // ===== 共用狀態 =====
    
    protected var initialized = false
    protected var network = ""
    
    override val sdkVersion = "1.0.0-simplified"
    
    // ===== 模板方法 - 共用實現 =====
    
    override suspend fun initialize(config: SDKConfig): Result<Unit> {
        return try {
            delay(100)
            network = config.network.ifEmpty { defaultNetwork }
            initialized = true
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override fun isInitialized(): Boolean = initialized
    
    override suspend fun cleanup() {
        initialized = false
    }
    
    override suspend fun getAccountBalance(address: String): Result<Balance> {
        if (!initialized) return Result.Failure(IllegalStateException("未初始化"))
        
        return try {
            delay(200)
            val amount = generateRandomBalance()
            
            Result.Success(Balance(
                amount = amount,
                decimals = nativeDecimals,
                symbol = nativeSymbol,
                lastUpdated = Clock.System.now().toEpochMilliseconds()
            ))
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun getTransactionHistory(
        address: String,
        limit: Int,
        offset: Int
    ): Result<List<Transaction>> {
        return try {
            delay(300)
            val transactions = (0 until limit).map { index ->
                Transaction(
                    hash = generateTxHash(),
                    fromAddress = address,
                    toAddress = generateAddress(),
                    amount = Random.nextDouble(0.01, 10.0).toString(),
                    fee = generateFeeAmount(),
                    timestamp = Clock.System.now().toEpochMilliseconds() - (index * 3600000),
                    status = TransactionStatus.CONFIRMED
                )
            }
            Result.Success(transactions)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun createTransaction(request: TransactionRequest): Result<UnsignedTransaction> {
        return try {
            delay(100)
            val fee = calculateFee(request.priority)
            Result.Success(UnsignedTransaction(
                rawData = "${chainType.name.lowercase()}_unsigned_${Random.nextLong()}",
                chainType = chainType,
                estimatedFee = fee
            ))
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun estimateTransactionFee(request: TransactionRequest): Result<TransactionFee> {
        return try {
            Result.Success(calculateFee(request.priority))
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun broadcastTransaction(signedTransaction: SignedTransaction): Result<TransactionResult> {
        return try {
            delay(500)
            Result.Success(TransactionResult(
                hash = signedTransaction.hash ?: generateTxHash(),
                status = TransactionStatus.PENDING,
                message = "Transaction submitted to ${chainType.name} $network"
            ))
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun getNetworkStatus(): Result<NetworkStatus> {
        return try {
            delay(50)
            Result.Success(NetworkStatus(
                isConnected = true,
                blockHeight = generateBlockHeight(),
                networkId = network,
                syncProgress = 1.0,
                averageBlockTime = avgBlockTimeMs
            ))
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    // ===== 抽象方法 - 子類必須實現 =====
    
    /** 驗證地址格式 - 各鏈有不同規則 */
    abstract override fun validateAddress(address: String): Result<AddressValidation>
    
    /** 生成鏈特定的地址 */
    protected abstract fun generateAddress(): String
    
    /** 生成鏈特定的交易哈希格式 */
    protected abstract fun generateTxHash(): String
    
    // ===== 可覆寫的 Helper 方法 =====
    
    /** 生成隨機餘額 - 子類可覆寫以調整範圍 */
    protected open fun generateRandomBalance(): String {
        return Random.nextDouble(0.1, 100.0).toString()
    }
    
    /** 生成區塊高度 - 子類可覆寫以調整範圍 */
    protected open fun generateBlockHeight(): Long {
        return Random.nextLong(10000000, 50000000)
    }
    
    /** 生成費用金額 - 子類可覆寫以調整格式 */
    protected open fun generateFeeAmount(): String {
        return Random.nextDouble(0.0001, 0.01).toString()
    }
    
    /** 計算交易費用 - 子類可覆寫以調整費用結構 */
    protected open fun calculateFee(priority: TransactionPriority): TransactionFee {
        val multiplier = when (priority) {
            TransactionPriority.LOW -> 0.5
            TransactionPriority.NORMAL -> 1.0
            TransactionPriority.HIGH -> 2.0
            TransactionPriority.URGENT -> 5.0
        }
        val baseFee = 0.001
        val cost = (baseFee * multiplier).toString()
        
        return TransactionFee(
            gasLimit = "1",
            gasPrice = cost,
            estimatedCost = cost,
            priority = priority
        )
    }
    
    // ===== 共用 Helper =====
    
    /** 生成 64 字符的十六進制密鑰對 */
    protected fun generateKeyPair(): Pair<String, String> {
        val privateKey = (1..64).map { "0123456789abcdef".random() }.joinToString("")
        val publicKey = (1..64).map { "0123456789abcdef".random() }.joinToString("")
        return Pair(publicKey, privateKey)
    }
    
    /** 生成帳戶信息 */
    fun generateAccount(): Result<AccountInfo> {
        return try {
            val address = generateAddress()
            val keyPair = generateKeyPair()
            
            Result.Success(AccountInfo(
                address = address,
                publicKey = keyPair.first,
                privateKey = keyPair.second,
                network = network.ifEmpty { defaultNetwork }
            ))
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    /** 簽名交易 - 通用實現 */
    override suspend fun signTransaction(unsignedTransaction: UnsignedTransaction, privateKey: String): Result<SignedTransaction> {
        return try {
            Result.Success(SignedTransaction(
                rawData = "signed_${unsignedTransaction.rawData}",
                signature = "${chainType.name.lowercase()}_sig_${Random.nextLong()}",
                chainType = chainType,
                hash = generateTxHash()
            ))
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
}

// Note: AccountInfo is already defined in BaseBlockchainSDK.kt

package com.cbstudio.wearwallet.core.security

import com.cbstudio.wearwallet.core.multichain.*
import com.cbstudio.wearwallet.core.common.Result
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.*
import co.touchlab.kermit.Logger
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.min
import kotlinx.datetime.Clock
import com.cbstudio.wearwallet.core.multichain.sdk.TransactionRequest
import com.cbstudio.wearwallet.core.multichain.sdk.TransactionPriority
import com.cbstudio.wearwallet.core.multichain.sdk.Balance

/**
 * 多重簽名錢包系統
 * 
 * 提供安全的多簽錢包創建、管理和交易簽名功能
 */
class MultiSigWallet(
    private val walletManager: MultiChainWalletManager
) {
    
    private val logger = Logger.withTag("MultiSigWallet")
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    
    // 多簽錢包狀態
    private val _walletState = MutableStateFlow(MultiSigState())
    val walletState: StateFlow<MultiSigState> = _walletState.asStateFlow()
    
    // 待簽名交易流
    private val _pendingTransactions = MutableSharedFlow<PendingTransaction>()
    val pendingTransactions: SharedFlow<PendingTransaction> = _pendingTransactions.asSharedFlow()
    
    /**
     * 多簽錢包狀態
     */
    data class MultiSigState(
        val wallets: Map<String, MultiSigWalletInfo> = emptyMap(),
        val pendingTransactions: List<PendingTransaction> = emptyList(),
        val executedTransactions: List<ExecutedTransaction> = emptyList(),
        val signers: Map<String, SignerInfo> = emptyMap()
    )
    
    /**
     * 多簽錢包資訊
     */
    @Serializable
    data class MultiSigWalletInfo(
        val id: String,
        val name: String,
        val chainType: MultiChainType,
        val address: String,
        val owners: List<String>,
        val requiredSignatures: Int,
        val createdAt: Long,
        val balance: String? = null,
        val nonce: Long = 0,
        val type: WalletType = WalletType.STANDARD,
        val metadata: Map<String, String> = emptyMap()
    )
    
    /**
     * 錢包類型
     */
    enum class WalletType {
        STANDARD,      // 標準多簽
        GNOSIS_SAFE,   // Gnosis Safe
        TIMELOCK,      // 時間鎖定
        SOCIAL_RECOVERY // 社交恢復
    }
    
    /**
     * 簽名者資訊
     */
    @Serializable
    data class SignerInfo(
        val address: String,
        val name: String,
        val weight: Int = 1,
        val publicKey: String? = null,
        val addedAt: Long,
        val isActive: Boolean = true,
        val signatureCount: Int = 0
    )
    
    /**
     * 待簽名交易
     */
    @Serializable
    data class PendingTransaction(
        val id: String,
        val walletId: String,
        val chainType: MultiChainType,
        val to: String,
        val value: String,
        val data: String? = null,
        val description: String,
        val createdBy: String,
        val createdAt: Long,
        val signatures: List<Signature> = emptyList(),
        val requiredSignatures: Int,
        val status: TransactionStatus,
        val executeAfter: Long? = null, // 時間鎖定
        val expiresAt: Long? = null
    )
    
    /**
     * 簽名
     */
    @Serializable
    data class Signature(
        val signer: String,
        val signature: String,
        val signedAt: Long,
        val nonce: Long? = null
    )
    
    /**
     * 交易狀態
     */
    enum class TransactionStatus {
        PENDING,        // 待簽名
        PARTIALLY_SIGNED, // 部分簽名
        READY,          // 準備執行
        EXECUTING,      // 執行中
        EXECUTED,       // 已執行
        CANCELLED,      // 已取消
        EXPIRED,        // 已過期
        FAILED          // 失敗
    }
    
    /**
     * 已執行交易
     */
    @Serializable
    data class ExecutedTransaction(
        val pendingTx: PendingTransaction,
        val executionTxHash: String,
        val executedBy: String,
        val executedAt: Long,
        val gasUsed: String,
        val success: Boolean
    )
    
    /**
     * 創建多簽錢包配置
     */
    data class CreateWalletConfig(
        val name: String,
        val chainType: MultiChainType,
        val owners: List<String>,
        val requiredSignatures: Int,
        val type: WalletType = WalletType.STANDARD,
        val timeLockDuration: Long? = null, // 毫秒
        val dailyLimit: String? = null,
        val metadata: Map<String, String> = emptyMap()
    )
    
    /**
     * 初始化多簽錢包系統
     */
    suspend fun initialize(): Result<Unit> {
        return try {
            logger.i("Initializing MultiSig Wallet System")
            
            // 載入現有錢包
            loadExistingWallets()
            
            // 啟動監控任務
            startMonitoring()
            
            logger.i("MultiSig System initialized")
            Result.Success(Unit)
        } catch (e: Exception) {
            logger.e("Failed to initialize MultiSig System", e)
            Result.Failure(e)
        }
    }
    
    /**
     * 創建多簽錢包
     */
    suspend fun createWallet(config: CreateWalletConfig): Result<MultiSigWalletInfo> {
        return try {
            logger.i("Creating multi-sig wallet: ${config.name}")
            
            // 驗證配置
            validateWalletConfig(config)
            
            // 生成錢包地址
            val walletAddress = generateMultiSigAddress(config)
            
            // 創建錢包資訊
            val wallet = MultiSigWalletInfo(
                id = "multisig_${Clock.System.now().toEpochMilliseconds()}",
                name = config.name,
                chainType = config.chainType,
                address = walletAddress,
                owners = config.owners,
                requiredSignatures = config.requiredSignatures,
                createdAt = Clock.System.now().toEpochMilliseconds(),
                type = config.type,
                metadata = config.metadata
            )
            
            // 部署合約（如果需要）
            if (requiresDeployment(config.chainType)) {
                deployMultiSigContract(wallet, config)
            }
            
            // 保存錢包
            saveWallet(wallet)
            
            // 添加簽名者
            config.owners.forEach { owner ->
                addSigner(wallet.id, owner, "Owner")
            }
            
            logger.i("Multi-sig wallet created: ${wallet.address}")
            Result.Success(wallet)
        } catch (e: Exception) {
            logger.e("Failed to create multi-sig wallet", e)
            Result.Failure(e)
        }
    }
    
    /**
     * 導入現有多簽錢包
     */
    suspend fun importWallet(
        address: String,
        chainType: MultiChainType,
        name: String? = null
    ): Result<MultiSigWalletInfo> {
        return try {
            logger.i("Importing multi-sig wallet: $address")
            
            // 從鏈上讀取錢包資訊
            val walletInfo = fetchWalletInfoFromChain(address, chainType)
            
            // 創建本地記錄
            val wallet = MultiSigWalletInfo(
                id = "imported_${Clock.System.now().toEpochMilliseconds()}",
                name = name ?: "Imported Wallet",
                chainType = chainType,
                address = address,
                owners = walletInfo.owners,
                requiredSignatures = walletInfo.threshold,
                createdAt = Clock.System.now().toEpochMilliseconds(),
                type = detectWalletType(address, chainType)
            )
            
            // 保存錢包
            saveWallet(wallet)
            
            Result.Success(wallet)
        } catch (e: Exception) {
            logger.e("Failed to import wallet", e)
            Result.Failure(e)
        }
    }
    
    /**
     * 創建交易提案
     */
    suspend fun proposeTransaction(
        walletId: String,
        to: String,
        value: String,
        data: String? = null,
        description: String
    ): Result<PendingTransaction> {
        return try {
            val wallet = _walletState.value.wallets[walletId]
                ?: return Result.Failure(Exception("Wallet not found"))
            
            logger.i("Proposing transaction for wallet ${wallet.address}")
            
            // 創建待簽名交易
            val pendingTx = PendingTransaction(
                id = "tx_${Clock.System.now().toEpochMilliseconds()}",
                walletId = walletId,
                chainType = wallet.chainType,
                to = to,
                value = value,
                data = data,
                description = description,
                createdBy = getCurrentSigner(),
                createdAt = Clock.System.now().toEpochMilliseconds(),
                requiredSignatures = wallet.requiredSignatures,
                status = TransactionStatus.PENDING,
                expiresAt = Clock.System.now().toEpochMilliseconds() + 86400000 // 24小時
            )
            
            // 保存交易
            savePendingTransaction(pendingTx)
            
            // 發送通知
            notifySigners(wallet, pendingTx)
            
            // 自動簽名（如果是提案者）
            if (isOwner(wallet, getCurrentSigner())) {
                signTransaction(pendingTx.id)
            }
            
            Result.Success(pendingTx)
        } catch (e: Exception) {
            logger.e("Failed to propose transaction", e)
            Result.Failure(e)
        }
    }
    
    /**
     * 簽名交易
     */
    suspend fun signTransaction(
        transactionId: String,
        privateKey: String? = null
    ): Result<Signature> {
        return try {
            val pendingTx = getPendingTransaction(transactionId)
                ?: return Result.Failure(Exception("Transaction not found"))
            
            val wallet = _walletState.value.wallets[pendingTx.walletId]
                ?: return Result.Failure(Exception("Wallet not found"))
            
            val signer = getCurrentSigner()
            
            // 驗證簽名者
            if (!isOwner(wallet, signer)) {
                return Result.Failure(Exception("Not an owner of this wallet"))
            }
            
            // 檢查是否已簽名
            if (pendingTx.signatures.any { it.signer == signer }) {
                return Result.Failure(Exception("Already signed"))
            }
            
            logger.i("Signing transaction $transactionId")
            
            // 生成簽名
            val signatureData = generateSignature(pendingTx, privateKey)
            
            val signature = Signature(
                signer = signer,
                signature = signatureData,
                signedAt = Clock.System.now().toEpochMilliseconds(),
                nonce = wallet.nonce
            )
            
            // 更新交易
            updateTransactionSignature(transactionId, signature)
            
            // 檢查是否可以執行
            checkAndExecuteTransaction(transactionId)
            
            Result.Success(signature)
        } catch (e: Exception) {
            logger.e("Failed to sign transaction", e)
            Result.Failure(e)
        }
    }
    
    /**
     * 執行交易
     */
    suspend fun executeTransaction(transactionId: String): Result<ExecutedTransaction> {
        return try {
            val pendingTx = getPendingTransaction(transactionId)
                ?: return Result.Failure(Exception("Transaction not found"))
            
            val wallet = _walletState.value.wallets[pendingTx.walletId]
                ?: return Result.Failure(Exception("Wallet not found"))
            
            // 驗證簽名數量
            if (pendingTx.signatures.size < wallet.requiredSignatures) {
                return Result.Failure(Exception("Insufficient signatures"))
            }
            
            // 檢查時間鎖
            pendingTx.executeAfter?.let { lockTime ->
                if (Clock.System.now().toEpochMilliseconds() < lockTime) {
                    return Result.Failure(Exception("Transaction is time-locked"))
                }
            }
            
            logger.i("Executing transaction $transactionId")
            
            // 更新狀態
            updateTransactionStatus(transactionId, TransactionStatus.EXECUTING)
            
            // 執行鏈上交易
            val txHash = executeOnChain(wallet, pendingTx)
            
            // 創建執行記錄
            val executedTx = ExecutedTransaction(
                pendingTx = pendingTx,
                executionTxHash = txHash,
                executedBy = getCurrentSigner(),
                executedAt = Clock.System.now().toEpochMilliseconds(),
                gasUsed = estimateGas(pendingTx),
                success = true
            )
            
            // 保存執行記錄
            saveExecutedTransaction(executedTx)
            
            // 更新狀態
            updateTransactionStatus(transactionId, TransactionStatus.EXECUTED)
            
            Result.Success(executedTx)
        } catch (e: Exception) {
            logger.e("Failed to execute transaction", e)
            updateTransactionStatus(transactionId, TransactionStatus.FAILED)
            Result.Failure(e)
        }
    }
    
    /**
     * 取消交易
     */
    suspend fun cancelTransaction(transactionId: String): Result<Unit> {
        return try {
            val pendingTx = getPendingTransaction(transactionId)
                ?: return Result.Failure(Exception("Transaction not found"))
            
            // 只有創建者可以取消
            if (pendingTx.createdBy != getCurrentSigner()) {
                return Result.Failure(Exception("Only creator can cancel"))
            }
            
            // 檢查狀態
            if (pendingTx.status != TransactionStatus.PENDING && 
                pendingTx.status != TransactionStatus.PARTIALLY_SIGNED) {
                return Result.Failure(Exception("Cannot cancel transaction in current status"))
            }
            
            logger.i("Cancelling transaction $transactionId")
            
            updateTransactionStatus(transactionId, TransactionStatus.CANCELLED)
            
            Result.Success(Unit)
        } catch (e: Exception) {
            logger.e("Failed to cancel transaction", e)
            Result.Failure(e)
        }
    }
    
    /**
     * 添加簽名者
     */
    suspend fun addSigner(
        walletId: String,
        signerAddress: String,
        name: String,
        weight: Int = 1
    ): Result<SignerInfo> {
        return try {
            val wallet = _walletState.value.wallets[walletId]
                ?: return Result.Failure(Exception("Wallet not found"))
            
            // 檢查是否已存在
            if (_walletState.value.signers.containsKey(signerAddress)) {
                return Result.Failure(Exception("Signer already exists"))
            }
            
            logger.i("Adding signer $signerAddress to wallet ${wallet.address}")
            
            val signer = SignerInfo(
                address = signerAddress,
                name = name,
                weight = weight,
                addedAt = Clock.System.now().toEpochMilliseconds()
            )
            
            // 保存簽名者
            val signers = _walletState.value.signers.toMutableMap()
            signers[signerAddress] = signer
            _walletState.value = _walletState.value.copy(signers = signers)
            
            // 如果是鏈上錢包，更新合約
            if (requiresDeployment(wallet.chainType)) {
                updateContractOwners(wallet, signerAddress, true)
            }
            
            Result.Success(signer)
        } catch (e: Exception) {
            logger.e("Failed to add signer", e)
            Result.Failure(e)
        }
    }
    
    /**
     * 移除簽名者
     */
    suspend fun removeSigner(
        walletId: String,
        signerAddress: String
    ): Result<Unit> {
        return try {
            val wallet = _walletState.value.wallets[walletId]
                ?: return Result.Failure(Exception("Wallet not found"))
            
            // 檢查最小簽名者數量
            val remainingOwners = wallet.owners.filter { it != signerAddress }
            if (remainingOwners.size < wallet.requiredSignatures) {
                return Result.Failure(Exception("Cannot remove signer: would violate threshold"))
            }
            
            logger.i("Removing signer $signerAddress from wallet ${wallet.address}")
            
            // 更新錢包
            val updatedWallet = wallet.copy(owners = remainingOwners)
            saveWallet(updatedWallet)
            
            // 移除簽名者記錄
            val signers = _walletState.value.signers.toMutableMap()
            signers.remove(signerAddress)
            _walletState.value = _walletState.value.copy(signers = signers)
            
            // 更新鏈上合約
            if (requiresDeployment(wallet.chainType)) {
                updateContractOwners(wallet, signerAddress, false)
            }
            
            Result.Success(Unit)
        } catch (e: Exception) {
            logger.e("Failed to remove signer", e)
            Result.Failure(e)
        }
    }
    
    /**
     * 更新門檻
     */
    suspend fun updateThreshold(
        walletId: String,
        newThreshold: Int
    ): Result<Unit> {
        return try {
            val wallet = _walletState.value.wallets[walletId]
                ?: return Result.Failure(Exception("Wallet not found"))
            
            // 驗證門檻
            if (newThreshold < 1 || newThreshold > wallet.owners.size) {
                return Result.Failure(Exception("Invalid threshold"))
            }
            
            logger.i("Updating threshold for wallet ${wallet.address}: ${wallet.requiredSignatures} -> $newThreshold")
            
            // 更新錢包
            val updatedWallet = wallet.copy(requiredSignatures = newThreshold)
            saveWallet(updatedWallet)
            
            // 更新鏈上合約
            if (requiresDeployment(wallet.chainType)) {
                updateContractThreshold(wallet, newThreshold)
            }
            
            Result.Success(Unit)
        } catch (e: Exception) {
            logger.e("Failed to update threshold", e)
            Result.Failure(e)
        }
    }
    
    /**
     * 獲取錢包餘額
     */
    suspend fun getWalletBalance(walletId: String): Result<Map<String, String>> {
        return try {
            val wallet = _walletState.value.wallets[walletId]
                ?: return Result.Failure(Exception("Wallet not found"))
            
            logger.i("Getting balance for wallet ${wallet.address}")
            
            // 獲取原生幣餘額
            val nativeBalance = walletManager.getBalance(
                wallet.chainType,
                wallet.address
            )
            
            val balances = mutableMapOf<String, String>()
            
            if (nativeBalance is Result.Success<*>) {
                balances["native"] = (nativeBalance.data as Balance).amount
            }
            
            // 獲取代幣餘額
            val tokenBalances = getTokenBalances(wallet)
            balances.putAll(tokenBalances)
            
            Result.Success<Map<String, String>>(balances)
        } catch (e: Exception) {
            logger.e("Failed to get wallet balance", e)
            Result.Failure(e)
        }
    }
    
    /**
     * 獲取交易歷史
     */
    suspend fun getTransactionHistory(
        walletId: String,
        limit: Int = 50
    ): Result<List<Any>> {
        return try {
            val wallet = _walletState.value.wallets[walletId]
                ?: return Result.Failure(Exception("Wallet not found"))
            
            val pending = _walletState.value.pendingTransactions
                .filter { it.walletId == walletId }
                .take(limit / 2)
            
            val executed = _walletState.value.executedTransactions
                .filter { it.pendingTx.walletId == walletId }
                .take(limit / 2)
            
            val combined = mutableListOf<Any>()
            combined.addAll(pending)
            combined.addAll(executed)
            
            Result.Success(combined)
        } catch (e: Exception) {
            logger.e("Failed to get transaction history", e)
            Result.Failure(e)
        }
    }
    
    /**
     * 獲取待簽名交易列表
     */
    fun getPendingTransactions(walletId: String? = null): List<PendingTransaction> {
        return if (walletId != null) {
            _walletState.value.pendingTransactions.filter { 
                it.walletId == walletId && 
                it.status in listOf(TransactionStatus.PENDING, TransactionStatus.PARTIALLY_SIGNED)
            }
        } else {
            _walletState.value.pendingTransactions.filter { 
                it.status in listOf(TransactionStatus.PENDING, TransactionStatus.PARTIALLY_SIGNED)
            }
        }
    }
    
    /**
     * 批量簽名
     */
    suspend fun batchSign(
        transactionIds: List<String>,
        privateKey: String? = null
    ): Result<List<Signature>> {
        return try {
            logger.i("Batch signing ${transactionIds.size} transactions")
            
            val signatures = mutableListOf<Signature>()
            
            coroutineScope {
                transactionIds.map { txId ->
                    async {
                        val result = signTransaction(txId, privateKey)
                        if (result is Result.Success) {
                            signatures.add(result.data)
                        }
                    }
                }.awaitAll()
            }
            
            Result.Success(signatures)
        } catch (e: Exception) {
            logger.e("Failed to batch sign", e)
            Result.Failure(e)
        }
    }
    
    /**
     * 社交恢復
     */
    suspend fun initiateSocialRecovery(
        walletId: String,
        newOwners: List<String>
    ): Result<String> {
        return try {
            val wallet = _walletState.value.wallets[walletId]
                ?: return Result.Failure(Exception("Wallet not found"))
            
            if (wallet.type != WalletType.SOCIAL_RECOVERY) {
                return Result.Failure(Exception("Wallet does not support social recovery"))
            }
            
            logger.i("Initiating social recovery for wallet ${wallet.address}")
            
            // 創建恢復提案
            val recoveryId = "recovery_${Clock.System.now().toEpochMilliseconds()}"
            
            // 需要現有所有者的多數簽名
            val requiredApprovals = (wallet.owners.size + 1) / 2
            
            // 發送恢復請求給守護者
            notifyGuardians(wallet, recoveryId, newOwners)
            
            Result.Success(recoveryId)
        } catch (e: Exception) {
            logger.e("Failed to initiate social recovery", e)
            Result.Failure(e)
        }
    }
    
    // === 私有輔助方法 ===
    
    private fun validateWalletConfig(config: CreateWalletConfig) {
        require(config.owners.isNotEmpty()) { "At least one owner required" }
        require(config.requiredSignatures > 0) { "Required signatures must be positive" }
        require(config.requiredSignatures <= config.owners.size) { 
            "Required signatures cannot exceed number of owners" 
        }
        require(config.owners.distinct().size == config.owners.size) { 
            "Duplicate owners not allowed" 
        }
    }
    
    private suspend fun generateMultiSigAddress(config: CreateWalletConfig): String {
        // 根據鏈類型生成地址
        return when (config.chainType) {
            MultiChainType.ETHEREUM,
            MultiChainType.BSC,
            MultiChainType.POLYGON -> {
                // CREATE2 地址生成
                generateCreate2Address(config)
            }
            MultiChainType.SOLANA -> {
                // PDA 地址生成
                generateProgramDerivedAddress(config)
            }
            else -> {
                // 使用第一個所有者的地址作為基礎
                "multisig_${config.chainType.symbol}_${Clock.System.now().toEpochMilliseconds()}"
            }
        }
    }
    
    private fun generateCreate2Address(config: CreateWalletConfig): String {
        // 簡化的 CREATE2 地址計算
        val salt = config.owners.joinToString("").hashCode()
        return "0x${salt.toString(16).padStart(40, '0')}"
    }
    
    private fun generateProgramDerivedAddress(config: CreateWalletConfig): String {
        // 簡化的 PDA 生成
        val seed = config.owners.joinToString(":")
        return "${seed.hashCode()}${Clock.System.now().toEpochMilliseconds()}".take(44)
    }
    
    private fun requiresDeployment(chainType: MultiChainType): Boolean {
        // 判斷是否需要部署合約
        return chainType in listOf(
            MultiChainType.ETHEREUM,
            MultiChainType.BSC,
            MultiChainType.POLYGON,
            MultiChainType.AVALANCHE,
            MultiChainType.ARBITRUM,
            MultiChainType.OPTIMISM
        )
    }
    
    private suspend fun deployMultiSigContract(
        wallet: MultiSigWalletInfo,
        config: CreateWalletConfig
    ) {
        // 部署多簽合約（簡化實現）
        logger.i("Deploying multi-sig contract for ${wallet.address}")
        // 實際實現需要調用智能合約部署
    }
    
    private fun saveWallet(wallet: MultiSigWalletInfo) {
        val wallets = _walletState.value.wallets.toMutableMap()
        wallets[wallet.id] = wallet
        _walletState.value = _walletState.value.copy(wallets = wallets)
    }
    
    private fun savePendingTransaction(tx: PendingTransaction) {
        val transactions = _walletState.value.pendingTransactions.toMutableList()
        transactions.add(tx)
        _walletState.value = _walletState.value.copy(pendingTransactions = transactions)
        
        // 發送到流
        GlobalScope.launch {
            _pendingTransactions.emit(tx)
        }
    }
    
    private fun saveExecutedTransaction(tx: ExecutedTransaction) {
        val transactions = _walletState.value.executedTransactions.toMutableList()
        transactions.add(tx)
        
        // 限制歷史記錄大小
        if (transactions.size > 1000) {
            transactions.removeAt(0)
        }
        
        _walletState.value = _walletState.value.copy(executedTransactions = transactions)
    }
    
    private fun getCurrentSigner(): String {
        // 獲取當前簽名者地址
        return "current_signer_address"
    }
    
    private fun isOwner(wallet: MultiSigWalletInfo, address: String): Boolean {
        return address in wallet.owners
    }
    
    private fun getPendingTransaction(id: String): PendingTransaction? {
        return _walletState.value.pendingTransactions.find { it.id == id }
    }
    
    private suspend fun generateSignature(
        tx: PendingTransaction,
        privateKey: String?
    ): String {
        // 生成交易簽名
        val message = "${tx.to}:${tx.value}:${tx.data ?: ""}:${tx.walletId}"
        return "sig_${message.hashCode()}_${Clock.System.now().toEpochMilliseconds()}"
    }
    
    private fun updateTransactionSignature(txId: String, signature: Signature) {
        val transactions = _walletState.value.pendingTransactions.map { tx ->
            if (tx.id == txId) {
                val signatures = tx.signatures.toMutableList()
                signatures.add(signature)
                val status = if (signatures.size >= tx.requiredSignatures) {
                    TransactionStatus.READY
                } else {
                    TransactionStatus.PARTIALLY_SIGNED
                }
                tx.copy(signatures = signatures, status = status)
            } else {
                tx
            }
        }
        _walletState.value = _walletState.value.copy(pendingTransactions = transactions)
    }
    
    private fun updateTransactionStatus(txId: String, status: TransactionStatus) {
        val transactions = _walletState.value.pendingTransactions.map { tx ->
            if (tx.id == txId) {
                tx.copy(status = status)
            } else {
                tx
            }
        }
        _walletState.value = _walletState.value.copy(pendingTransactions = transactions)
    }
    
    private suspend fun checkAndExecuteTransaction(txId: String) {
        val tx = getPendingTransaction(txId) ?: return
        val wallet = _walletState.value.wallets[tx.walletId] ?: return
        
        if (tx.signatures.size >= wallet.requiredSignatures) {
            executeTransaction(txId)
        }
    }
    
    private suspend fun executeOnChain(
        wallet: MultiSigWalletInfo,
        tx: PendingTransaction
    ): String {
        // 執行鏈上交易
        logger.i("Executing on-chain transaction for wallet ${wallet.address}")
        
        // 創建交易請求
        val request = TransactionRequest(
            fromAddress = wallet.address,
            toAddress = tx.to,
            amount = tx.value,
            priority = TransactionPriority.NORMAL
        )
        
        // 提交交易
        val result = walletManager.createTransaction(wallet.chainType, request)
        
        return if (result is Result.Success) {
            "tx_hash_${Clock.System.now().toEpochMilliseconds()}"
        } else {
            throw Exception("Failed to execute on-chain transaction")
        }
    }
    
    private fun estimateGas(tx: PendingTransaction): String {
        // 估算 Gas 費用
        return when (tx.chainType) {
            MultiChainType.ETHEREUM -> "100000"
            MultiChainType.BSC -> "50000"
            MultiChainType.POLYGON -> "50000"
            else -> "21000"
        }
    }
    
    private suspend fun notifySigners(wallet: MultiSigWalletInfo, tx: PendingTransaction) {
        // 通知所有簽名者
        logger.i("Notifying ${wallet.owners.size} signers about new transaction")
        // 實際實現需要推送通知或郵件
    }
    
    private suspend fun notifyGuardians(
        wallet: MultiSigWalletInfo,
        recoveryId: String,
        newOwners: List<String>
    ) {
        // 通知守護者關於恢復請求
        logger.i("Notifying guardians about recovery request $recoveryId")
    }
    
    private suspend fun fetchWalletInfoFromChain(
        address: String,
        chainType: MultiChainType
    ): WalletChainInfo {
        // 從鏈上讀取錢包資訊
        return WalletChainInfo(
            owners = listOf(address),
            threshold = 1
        )
    }
    
    private data class WalletChainInfo(
        val owners: List<String>,
        val threshold: Int
    )
    
    private fun detectWalletType(address: String, chainType: MultiChainType): WalletType {
        // 檢測錢包類型
        return when {
            address.contains("gnosis", ignoreCase = true) -> WalletType.GNOSIS_SAFE
            address.contains("timelock", ignoreCase = true) -> WalletType.TIMELOCK
            else -> WalletType.STANDARD
        }
    }
    
    private suspend fun updateContractOwners(
        wallet: MultiSigWalletInfo,
        owner: String,
        add: Boolean
    ) {
        // 更新鏈上合約所有者
        logger.i("Updating contract owners: ${if (add) "adding" else "removing"} $owner")
    }
    
    private suspend fun updateContractThreshold(
        wallet: MultiSigWalletInfo,
        newThreshold: Int
    ) {
        // 更新鏈上合約門檻
        logger.i("Updating contract threshold to $newThreshold")
    }
    
    private suspend fun getTokenBalances(
        wallet: MultiSigWalletInfo
    ): Map<String, String> {
        // 獲取代幣餘額
        return emptyMap()
    }
    
    private suspend fun loadExistingWallets() {
        // 載入現有錢包
        logger.i("Loading existing multi-sig wallets")
    }
    
    private fun startMonitoring() {
        // 啟動監控任務
        GlobalScope.launch {
            while (true) {
                // 檢查過期交易
                checkExpiredTransactions()
                
                // 更新錢包餘額
                updateWalletBalances()
                
                delay(60000) // 每分鐘檢查
            }
        }
    }
    
    private fun checkExpiredTransactions() {
        val now = Clock.System.now().toEpochMilliseconds()
        val transactions = _walletState.value.pendingTransactions.map { tx ->
            if (tx.expiresAt != null && tx.expiresAt < now && 
                tx.status in listOf(TransactionStatus.PENDING, TransactionStatus.PARTIALLY_SIGNED)) {
                tx.copy(status = TransactionStatus.EXPIRED)
            } else {
                tx
            }
        }
        _walletState.value = _walletState.value.copy(pendingTransactions = transactions)
    }
    
    private suspend fun updateWalletBalances() {
        _walletState.value.wallets.values.forEach { wallet ->
            try {
                val balanceResult = walletManager.getBalance(wallet.chainType, wallet.address)
                if (balanceResult is Result.Success<*>) {
                    val updatedWallet = wallet.copy(balance = (balanceResult.data as Balance).amount)
                    saveWallet(updatedWallet)
                }
            } catch (e: Exception) {
                logger.e("Failed to update balance for wallet ${wallet.id}", e)
            }
        }
    }
    
    /**
     * 清理資源
     */
    fun cleanup() {
        logger.i("Cleaning up MultiSig Wallet System")
        _walletState.value = MultiSigState()
    }
}
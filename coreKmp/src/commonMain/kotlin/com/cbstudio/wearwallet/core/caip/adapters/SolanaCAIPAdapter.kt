package com.cbstudio.wearwallet.core.caip.adapters

import kotlinx.datetime.Clock
import com.cbstudio.wearwallet.core.caip.*
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.core.multichain.sdk.*
import com.cbstudio.wearwallet.core.common.Result
import co.touchlab.kermit.Logger

/**
 * Solana CAIP 標準 SDK 適配器
 * 
 * 基於 Metaplex KMP SDK 的 CAIP 標準實作
 * 支援 Solana 主網、測試網和開發網
 * 
 * 支援的 CAIP 標準:
 * - CAIP-2: solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp (mainnet)
 * - CAIP-10: solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp:4Qkev8aNZcqFNSRhQzwyLMFSsi94jHqE8WNVTJzTP99F
 * - CAIP-19: solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp/slip44:501 (SOL)
 * - CAIP-19: solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp/spl-token:EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v (USDC)
 */
class SolanaCAIPAdapter(
    private val network: String = "mainnet"
) : AbstractCAIPSDKAdapter() {
    
    override val chainType = MultiChainType.SOLANA
    override val sdkVersion = "2.0.0-caip"
    
    override val capabilities = setOf(
        SDKCapability.BALANCE_QUERY,
        SDKCapability.TRANSACTION_CREATION,
        SDKCapability.TRANSACTION_BROADCAST,
        SDKCapability.ADDRESS_VALIDATION,
        SDKCapability.TRANSACTION_HISTORY,
        SDKCapability.NFT_OPERATIONS,
        SDKCapability.DEFI_OPERATIONS
    )
    
    override val supportedNamespaces = listOf("solana")
    
    override val supportedAssetNamespaces = listOf(
        "slip44",      // SOL 原生代幣
        "spl-token",   // SPL 代幣標準
        "spl-nft"      // SPL NFT
    )
    
    private var initialized = false
    private var rpcEndpoint: String = getDefaultRpcEndpoint()
    private var httpClient: SolanaHttpClient? = null
    
    companion object {
        private const val LAMPORTS_PER_SOL = 1_000_000_000L
        private const val MAX_RETRIES = 3
        private const val DEFAULT_COMMITMENT = "confirmed"
        
        // Solana 網路 Genesis Hash (用於 CAIP 鏈 ID)
        private const val MAINNET_GENESIS = "5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp"
        private const val TESTNET_GENESIS = "4uhcVJyU9pJkvQyS88uRDiswHXSCkY3z"
        private const val DEVNET_GENESIS = "EtWTRABZaYq6iMfeYKouRu166VU2xqa1"
        
        // 知名 SPL 代幣
        private val WELL_KNOWN_TOKENS = mapOf(
            "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v" to TokenInfo("USDC", "USD Coin", 6),
            "Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB" to TokenInfo("USDT", "Tether USD", 6),
            "mSoLzYCxHdYgdzU16g5QSh3i5K3z3KZK7ytfqcJm7So" to TokenInfo("mSOL", "Marinade Staked SOL", 9),
            "7dHbWXmci3dT8UFYWYZweBLXgycu7Y3iL6trKn1Y7ARj" to TokenInfo("stSOL", "Lido Staked SOL", 9)
        )
    }
    
    data class TokenInfo(
        val symbol: String,
        val name: String,
        val decimals: Int
    )
    
    override fun getDefaultNetwork(): String = network
    
    private fun getDefaultRpcEndpoint(): String {
        return when (network) {
            "mainnet" -> "https://api.mainnet-beta.solana.com"
            "testnet" -> "https://api.testnet.solana.com"
            "devnet" -> "https://api.devnet.solana.com"
            else -> "https://api.mainnet-beta.solana.com"
        }
    }
    
    private fun getGenesisHash(): String {
        return when (network) {
            "mainnet" -> MAINNET_GENESIS
            "testnet" -> TESTNET_GENESIS
            "devnet" -> DEVNET_GENESIS
            else -> MAINNET_GENESIS
        }
    }
    
    // CAIP 標準實作
    
    override suspend fun getAccountBalanceCAIP(caipAddress: CAIPAddress): Result<CAIPBalance> {
        if (!initialized) {
            return Result.Failure(SDKException.InitializationException(chainType, "SDK not initialized"))
        }
        
        logger.d("Getting CAIP balance for ${caipAddress.toCAIPString()}")
        
        return try {
            // 驗證地址是否為 Solana 地址
            if (caipAddress.chainId.namespace != "solana") {
                return Result.Failure(SDKException.ConfigurationException(
                    chainType, 
                    "Invalid chain namespace: expected 'solana', got '${caipAddress.chainId.namespace}'"
                ))
            }
            
            // 驗證地址格式
            if (!isValidSolanaAddress(caipAddress.address)) {
                return Result.Failure(SDKException.ConfigurationException(
                    chainType,
                    "Invalid Solana address format: ${caipAddress.address}"
                ))
            }
            
            // 查詢帳戶餘額
            val balanceResponse = queryAccountBalance(caipAddress.address)
            val lamports = balanceResponse.value
            val solAmount = lamports.toDouble() / LAMPORTS_PER_SOL
            
            // 創建 SOL 原生資產
            val solAsset = CAIPAsset.createNativeAsset(chainType, network)
            
            Result.Success(CAIPBalance(
                asset = solAsset,
                amount = solAmount.toString(),
                decimals = 9,
                symbol = "SOL",
                usdValue = null, // 需要價格 API
                lastUpdated = Clock.System.now().toEpochMilliseconds(),
                metadata = mapOf(
                    "lamports" to lamports,
                    "network" to network,
                    "commitment" to DEFAULT_COMMITMENT
                )
            ))
        } catch (e: Exception) {
            Result.Failure(SDKException.NetworkException(
                chainType,
                "Failed to fetch CAIP balance for ${caipAddress.address}: ${e.message}",
                e
            ))
        }
    }
    
    override suspend fun createTransactionCAIP(request: CAIPTransactionRequest): Result<CAIPUnsignedTransaction> {
        if (!initialized) {
            return Result.Failure(SDKException.InitializationException(chainType, "SDK not initialized"))
        }
        
        logger.d("Creating CAIP transaction: ${request.fromAddress.address} -> ${request.toAddress.address}")
        
        return try {
            // 驗證交易請求
            val validation = request.validate()
            if (validation.isFailure()) {
                return validation as Result.Failure
            }
            
            // 驗證是否為 Solana 交易
            if (request.fromAddress.chainId.namespace != "solana" || 
                request.toAddress.chainId.namespace != "solana") {
                return Result.Failure(SDKException.ConfigurationException(
                    chainType,
                    "Non-Solana addresses not supported"
                ))
            }
            
            // 獲取最新的區塊雜湊
            val recentBlockhash = getRecentBlockhash()
            
            // 決定轉帳類型
            val transaction = when {
                request.asset.isNativeToken() -> {
                    // SOL 原生轉帳
                    createNativeTransfer(request, recentBlockhash)
                }
                request.asset.assetNamespace == "spl-token" -> {
                    // SPL 代幣轉帳
                    createSPLTokenTransfer(request, recentBlockhash)
                }
                request.asset.isNFT() -> {
                    // NFT 轉移
                    createNFTTransfer(request, recentBlockhash)
                }
                else -> {
                    throw IllegalArgumentException("Unsupported asset type: ${request.asset.assetNamespace}")
                }
            }
            
            // 估算手續費
            val fee = estimateTransactionFeeCAIP(request).getOrNull() ?:
                createDefaultFee(request.asset)
            
            Result.Success(CAIPUnsignedTransaction(
                rawData = transaction.serialize(),
                chainId = request.fromAddress.chainId,
                estimatedFee = fee,
                expirationTime = null, // Solana 交易不會過期，但區塊雜湊會過期
                metadata = mapOf(
                    "recentBlockhash" to recentBlockhash,
                    "feePayer" to request.fromAddress.address,
                    "transactionType" to getTransactionType(request.asset),
                    "network" to network
                )
            ))
        } catch (e: Exception) {
            Result.Failure(SDKException.TransactionException(
                chainType,
                "Failed to create CAIP transaction: ${e.message}",
                e
            ))
        }
    }
    
    override fun validateAddressCAIP(caipAddress: CAIPAddress): Result<CAIPAddressValidation> {
        logger.d("Validating CAIP address: ${caipAddress.toCAIPString()}")
        
        return try {
            // 檢查命名空間
            if (caipAddress.chainId.namespace != "solana") {
                return Result.Success(CAIPAddressValidation(
                    isValid = false,
                    message = "Invalid namespace: expected 'solana', got '${caipAddress.chainId.namespace}'"
                ))
            }
            
            // 檢查網路
            val expectedGenesis = getGenesisHash()
            if (caipAddress.chainId.reference != expectedGenesis) {
                return Result.Success(CAIPAddressValidation(
                    isValid = false,
                    networkMatches = false,
                    message = "Network mismatch: expected $expectedGenesis, got ${caipAddress.chainId.reference}"
                ))
            }
            
            // 檢查地址格式
            val isValidFormat = isValidSolanaAddress(caipAddress.address)
            if (!isValidFormat) {
                return Result.Success(CAIPAddressValidation(
                    isValid = false,
                    message = "Invalid Solana address format"
                ))
            }
            
            // 確定地址類型 (需要查詢鏈上資料)
            val addressType = determineSolanaAddressType(caipAddress.address)
            
            Result.Success(CAIPAddressValidation(
                isValid = true,
                addressType = addressType,
                networkMatches = true,
                message = "Valid Solana address",
                supportedOperations = getSupportedOperations(addressType)
            ))
        } catch (e: Exception) {
            Result.Failure(SDKException.ConfigurationException(
                chainType,
                "Address validation failed: ${e.message}"
            ))
        }
    }
    
    override suspend fun broadcastTransactionCAIP(signedTransaction: CAIPSignedTransaction): Result<CAIPTransactionResult> {
        if (!initialized) {
            return Result.Failure(SDKException.InitializationException(chainType, "SDK not initialized"))
        }
        
        logger.d("Broadcasting CAIP transaction")
        
        return try {
            val transactionHash = submitTransaction(signedTransaction.rawData)
            val explorerUrl = CAIPService().getExplorerUrl(
                signedTransaction.chainId,
                transactionHash
            )
            
            Result.Success(CAIPTransactionResult(
                transactionHash = transactionHash,
                status = CAIPTransactionStatus.PENDING,
                chainId = signedTransaction.chainId,
                blockNumber = null,
                gasUsed = null,
                fee = null,
                timestamp = Clock.System.now().toEpochMilliseconds(),
                explorerUrl = explorerUrl
            ))
        } catch (e: Exception) {
            Result.Failure(SDKException.TransactionException(
                chainType,
                "Failed to broadcast CAIP transaction: ${e.message}",
                e
            ))
        }
    }
    
    override fun getSupportedChainIDs(): List<CAIPChainID> {
        return listOf(
            CAIPChainID("solana", MAINNET_GENESIS),
            CAIPChainID("solana", TESTNET_GENESIS), 
            CAIPChainID("solana", DEVNET_GENESIS)
        )
    }
    
    override suspend fun getSupportedAssets(chainId: CAIPChainID): Result<List<CAIPAsset>> {
        if (chainId.namespace != "solana") {
            return Result.Failure(IllegalArgumentException("Unsupported chain: ${chainId.toCAIPString()}"))
        }
        
        val assets = mutableListOf<CAIPAsset>()
        
        // 添加 SOL 原生代幣
        assets.add(CAIPAsset.createNativeAsset(chainType, network))
        
        // 添加知名 SPL 代幣
        WELL_KNOWN_TOKENS.forEach { (address, tokenInfo) ->
            assets.add(CAIPAsset(
                chainId = chainId,
                assetNamespace = "spl-token",
                assetReference = address
            ))
        }
        
        return Result.Success(assets)
    }
    
    // SDK 基礎方法實作
    
    override suspend fun initialize(config: SDKConfig): Result<Unit> {
        return try {
            rpcEndpoint = config.rpcUrl.takeIf { it.isNotEmpty() } ?: rpcEndpoint
            httpClient = createSolanaHttpClient(config)
            
            // 驗證連線
            val health = checkRpcHealth()
            if (health) {
                initialized = true
                logger.i("Solana CAIP SDK initialized for $network network")
                Result.Success(Unit)
            } else {
                Result.Failure(SDKException.InitializationException(
                    chainType,
                    "Failed to connect to Solana RPC endpoint: $rpcEndpoint"
                ))
            }
        } catch (e: Exception) {
            Result.Failure(SDKException.InitializationException(
                chainType,
                "SDK initialization failed: ${e.message}",
                e
            ))
        }
    }
    
    override fun isInitialized(): Boolean = initialized
    
    override suspend fun getTransactionHistory(
        address: String,
        limit: Int,
        offset: Int
    ): Result<List<Transaction>> {
        // 使用 CAIP 方法實作
        val caipAddress = CAIPAddress.fromLegacyAddress(address, chainType, network)
        return getTransactionHistoryCAIP(caipAddress, limit, offset)
    }
    
    override suspend fun estimateTransactionFee(request: TransactionRequest): Result<TransactionFee> {
        // 轉換為 CAIP 格式並估算
        val fromCAIP = CAIPAddress.fromLegacyAddress(request.fromAddress, chainType, network)
        val toCAIP = CAIPAddress.fromLegacyAddress(request.toAddress, chainType, network)
        
        val asset = if (request.tokenAddress != null) {
            CAIPAsset(
                chainId = CAIPChainID.fromMultiChainType(chainType, network),
                assetNamespace = "spl-token",
                assetReference = request.tokenAddress
            )
        } else {
            CAIPAsset.createNativeAsset(chainType, network)
        }
        
        val caipRequest = CAIPTransactionRequest(
            fromAddress = fromCAIP,
            toAddress = toCAIP,
            asset = asset,
            amount = request.amount,
            memo = request.memo
        )
        
        val caipFeeResult = estimateTransactionFeeCAIP(caipRequest)
        return when (caipFeeResult) {
            is Result.Success -> Result.Success(caipFeeResult.data.toLegacyTransactionFee())
            is Result.Failure -> Result.Failure(caipFeeResult.exception)
            is Result.Loading -> Result.Loading()
        }
    }
    
    override suspend fun getNetworkStatus(): Result<NetworkStatus> {
        if (!initialized) {
            return Result.Failure(SDKException.InitializationException(chainType, "SDK not initialized"))
        }
        
        return try {
            val health = checkRpcHealth()
            val slot = getCurrentSlot()
            val epoch = getCurrentEpoch()
            
            Result.Success(NetworkStatus(
                isConnected = health,
                blockHeight = slot,
                networkId = network,
                peersCount = null,
                syncProgress = 1.0,
                averageBlockTime = 400 // Solana 平均出塊時間約 400ms
            ))
        } catch (e: Exception) {
            Result.Failure(SDKException.NetworkException(
                chainType,
                "Failed to get network status: ${e.message}",
                e
            ))
        }
    }
    
    override suspend fun cleanup() {
        httpClient = null
        initialized = false
        logger.i("Solana CAIP SDK cleaned up")
    }
    
    override suspend fun signTransaction(unsignedTransaction: UnsignedTransaction, privateKey: String): Result<SignedTransaction> {
        if (!initialized) {
            return Result.Failure(SDKException.InitializationException(chainType, "SDK not initialized"))
        }
        
        return try {
            // TODO: 使用 Metaplex Solana KMP SDK 進行交易簽名
            // 暫時返回模擬的已簽名交易
            Result.Success(SignedTransaction(
                rawData = unsignedTransaction.rawData,
                signature = "solana_caip_sig_${Clock.System.now().toEpochMilliseconds()}",
                chainType = chainType,
                hash = "solana_caip_tx_${Clock.System.now().toEpochMilliseconds()}"
            ))
        } catch (e: Exception) {
            Result.Failure(SDKException.TransactionException(
                chainType,
                "Failed to sign transaction: ${e.message}",
                e
            ))
        }
    }
    
    // CAIP 特定輔助方法
    
    /**
     * 取得交易歷史 (CAIP 格式)
     */
    private suspend fun getTransactionHistoryCAIP(
        caipAddress: CAIPAddress,
        limit: Int,
        offset: Int
    ): Result<List<Transaction>> {
        if (!initialized) {
            return Result.Failure(SDKException.InitializationException(chainType, "SDK not initialized"))
        }
        
        return try {
            val signatures = getConfirmedSignaturesForAddress(caipAddress.address, limit, offset)
            val transactions = signatures.map { signature ->
                getTransactionDetails(signature.signature)
            }
            
            Result.Success(transactions)
        } catch (e: Exception) {
            Result.Failure(SDKException.NetworkException(
                chainType,
                "Failed to fetch CAIP transaction history: ${e.message}",
                e
            ))
        }
    }
    
    /**
     * 估算交易手續費 (CAIP 格式)
     */
    private suspend fun estimateTransactionFeeCAIP(request: CAIPTransactionRequest): Result<CAIPTransactionFee> {
        return try {
            val baseFee = when {
                request.asset.isNativeToken() -> 5000L // SOL 轉帳基本費用
                request.asset.assetNamespace == "spl-token" -> 10000L // SPL 代幣轉帳
                request.asset.isNFT() -> 15000L // NFT 轉移
                else -> 5000L
            }
            
            val priorityMultiplier = 1.5 // 預設優先級係數
            val totalFee = (baseFee * priorityMultiplier).toLong()
            val solFee = totalFee.toDouble() / LAMPORTS_PER_SOL
            
            // 手續費資產 (SOL)
            val feeAsset = CAIPAsset.createNativeAsset(chainType, network)
            
            Result.Success(CAIPTransactionFee(
                gasLimit = "1", // Solana 不使用 gas limit 概念
                gasPrice = totalFee.toString(),
                estimatedCost = solFee.toString(),
                asset = feeAsset,
                usdValue = null,
                priority = TransactionPriority.NORMAL
            ))
        } catch (e: Exception) {
            Result.Failure(SDKException.NetworkException(
                chainType,
                "Failed to estimate CAIP transaction fee: ${e.message}",
                e
            ))
        }
    }
    
    // 私有輔助方法
    
    private fun isValidSolanaAddress(address: String): Boolean {
        // Solana 地址是 44 個字符的 Base58 編碼
        return address.length == 44 && address.matches(Regex("[1-9A-HJ-NP-Za-km-z]+"))
    }
    
    private fun determineSolanaAddressType(address: String): CAIPAddressType {
        // 這裡需要查詢鏈上資料來確定地址類型
        // 暫時返回 EOA，實際實作需要呼叫 getAccountInfo
        return CAIPAddressType.EOA
    }
    
    private fun getSupportedOperations(addressType: CAIPAddressType): Set<String> {
        return when (addressType) {
            CAIPAddressType.EOA -> setOf("transfer", "receive", "sign")
            CAIPAddressType.CONTRACT -> setOf("call", "receive")
            CAIPAddressType.MULTISIG -> setOf("multisig_transfer", "sign")
            else -> emptySet()
        }
    }
    
    private fun getTransactionType(asset: CAIPAsset): String {
        return when {
            asset.isNativeToken() -> "native_transfer"
            asset.assetNamespace == "spl-token" -> "spl_token_transfer"
            asset.isNFT() -> "nft_transfer"
            else -> "unknown"
        }
    }
    
    private fun createDefaultFee(asset: CAIPAsset): CAIPTransactionFee {
        val baseFee = when {
            asset.isNativeToken() -> 5000L
            asset.assetNamespace == "spl-token" -> 10000L
            else -> 5000L
        }
        
        val feeAsset = CAIPAsset.createNativeAsset(chainType, network)
        
        return CAIPTransactionFee(
            gasLimit = "1",
            gasPrice = baseFee.toString(),
            estimatedCost = (baseFee.toDouble() / LAMPORTS_PER_SOL).toString(),
            asset = feeAsset,
            priority = TransactionPriority.NORMAL
        )
    }
    
    private fun createNativeTransfer(
        request: CAIPTransactionRequest,
        recentBlockhash: String
    ): SolanaTransaction {
        val lamports = (request.amount.toDouble() * LAMPORTS_PER_SOL).toLong()
        val transferInstruction = createTransferInstruction(
            fromAddress = request.fromAddress.address,
            toAddress = request.toAddress.address,
            amount = lamports
        )
        
        return buildTransaction(
            instructions = listOf(transferInstruction),
            recentBlockhash = recentBlockhash,
            feePayer = request.fromAddress.address
        )
    }
    
    private fun createSPLTokenTransfer(
        request: CAIPTransactionRequest,
        recentBlockhash: String
    ): SolanaTransaction {
        val tokenMint = request.asset.assetReference
        val tokenInfo = WELL_KNOWN_TOKENS[tokenMint]
        val decimals = tokenInfo?.decimals ?: 6
        
        val amount = (request.amount.toDouble() * 10.0.pow(decimals)).toLong()
        
        // 這裡需要實作 SPL 代幣轉帳指令
        // 暫時使用基本轉帳作為佔位符
        val transferInstruction = createTransferInstruction(
            fromAddress = request.fromAddress.address,
            toAddress = request.toAddress.address,
            amount = amount
        )
        
        return buildTransaction(
            instructions = listOf(transferInstruction),
            recentBlockhash = recentBlockhash,
            feePayer = request.fromAddress.address
        )
    }
    
    private fun createNFTTransfer(
        request: CAIPTransactionRequest,
        recentBlockhash: String
    ): SolanaTransaction {
        // NFT 轉移邏輯
        // 這裡需要實作 Metaplex NFT 轉移指令
        // 暫時使用基本轉帳作為佔位符
        val transferInstruction = createTransferInstruction(
            fromAddress = request.fromAddress.address,
            toAddress = request.toAddress.address,
            amount = 1L // NFT 數量通常為 1
        )
        
        return buildTransaction(
            instructions = listOf(transferInstruction),
            recentBlockhash = recentBlockhash,
            feePayer = request.fromAddress.address
        )
    }
    
    // 重用原有的 Solana SDK 方法
    
    private fun createSolanaHttpClient(config: SDKConfig): SolanaHttpClient {
        return SolanaHttpClient(
            endpoint = rpcEndpoint,
            timeout = config.timeout,
            retries = config.retryCount,
            apiKey = config.apiKey
        )
    }
    
    private suspend fun checkRpcHealth(): Boolean {
        return try {
            httpClient?.getHealth() ?: false
        } catch (e: Exception) {
            false
        }
    }
    
    private suspend fun queryAccountBalance(address: String): BalanceResponse {
        return httpClient?.getBalance(address, DEFAULT_COMMITMENT)
            ?: throw Exception("HTTP client not initialized")
    }
    
    private suspend fun getRecentBlockhash(): String {
        return httpClient?.getRecentBlockhash()?.value?.blockhash
            ?: throw Exception("Failed to get recent blockhash")
    }
    
    private suspend fun getCurrentSlot(): Long {
        return httpClient?.getSlot() ?: 0L
    }
    
    private suspend fun getCurrentEpoch(): Long {
        return httpClient?.getEpochInfo()?.epoch ?: 0L
    }
    
    private suspend fun getConfirmedSignaturesForAddress(
        address: String,
        limit: Int,
        offset: Int
    ): List<SignatureStatus> {
        // TODO: 使用真實的 Solana KMP SDK 實作
        return emptyList()
    }
    
    private suspend fun getTransactionDetails(signature: String): Transaction {
        // TODO: 使用真實的 Solana KMP SDK 實作
        return Transaction(
            hash = signature,
            fromAddress = "",
            toAddress = "",
            amount = "0",
            fee = "0.000005",
            timestamp = Clock.System.now().toEpochMilliseconds(),
            blockNumber = null,
            status = TransactionStatus.CONFIRMED,
            memo = null
        )
    }
    
    private fun createTransferInstruction(
        fromAddress: String,
        toAddress: String,
        amount: Long
    ): TransferInstruction {
        // TODO: 使用真實的 Solana KMP SDK 實作
        return TransferInstruction(fromAddress, toAddress, amount)
    }
    
    private fun buildTransaction(
        instructions: List<Any>,
        recentBlockhash: String,
        feePayer: String
    ): SolanaTransaction {
        // TODO: 使用真實的 Solana KMP SDK 實作
        return SolanaTransaction()
    }
    
    private suspend fun submitTransaction(serializedTransaction: String): String {
        // TODO: 使用真實的 Solana KMP SDK 實作
        return "caip_transaction_hash_${Clock.System.now().toEpochMilliseconds()}"
    }
}

// 重用原有的資料結構
data class BalanceResponse(val value: Long)
data class BlockhashResponse(val value: BlockhashValue)
data class BlockhashValue(val blockhash: String)
data class EpochInfo(val epoch: Long)
data class SignatureStatus(val signature: String)
data class TransferInstruction(val from: String, val to: String, val amount: Long)

class SolanaTransaction {
    fun serialize(): String = "caip_serialized_transaction"
}

data class SolanaHttpClient(
    val endpoint: String,
    val timeout: Long,
    val retries: Int,
    val apiKey: String?
) {
    suspend fun getHealth(): Boolean = true
    suspend fun getBalance(address: String, commitment: String): BalanceResponse = 
        BalanceResponse(1000000000L)
    suspend fun getRecentBlockhash(): BlockhashResponse = 
        BlockhashResponse(BlockhashValue("caip_blockhash"))
    suspend fun getSlot(): Long = 100000L
    suspend fun getEpochInfo(): EpochInfo = EpochInfo(100L)
}

// 數學工具函數
private fun Double.pow(n: Int): Double {
    var result = 1.0
    repeat(n) { result *= this }
    return result
}
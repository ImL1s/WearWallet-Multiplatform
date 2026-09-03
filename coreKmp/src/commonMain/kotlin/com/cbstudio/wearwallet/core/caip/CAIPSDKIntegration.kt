package com.cbstudio.wearwallet.core.caip

import kotlinx.datetime.Clock
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.core.multichain.sdk.*
import com.cbstudio.wearwallet.core.common.Result
import co.touchlab.kermit.Logger

/**
 * CAIP 標準的 SDK 適配器介面
 * 
 * 擴展原有的 BlockchainSDKAdapter，添加 CAIP 標準支援
 */
interface CAIPBlockchainSDKAdapter : BlockchainSDKAdapter {
    
    /**
     * 支援的 CAIP 命名空間
     */
    val supportedNamespaces: List<String>
    
    /**
     * 支援的資產命名空間
     */
    val supportedAssetNamespaces: List<String>
    
    /**
     * 使用 CAIP 地址查詢餘額
     */
    suspend fun getAccountBalanceCAIP(caipAddress: CAIPAddress): Result<CAIPBalance>
    
    /**
     * 使用 CAIP 創建交易
     */
    suspend fun createTransactionCAIP(request: CAIPTransactionRequest): Result<CAIPUnsignedTransaction>
    
    /**
     * 驗證 CAIP 地址
     */
    fun validateAddressCAIP(caipAddress: CAIPAddress): Result<CAIPAddressValidation>
    
    /**
     * 廣播 CAIP 簽名交易
     */
    suspend fun broadcastTransactionCAIP(signedTransaction: CAIPSignedTransaction): Result<CAIPTransactionResult>
    
    /**
     * 取得支援的鏈 ID 列表
     */
    fun getSupportedChainIDs(): List<CAIPChainID>
    
    /**
     * 取得支援的資產列表
     */
    suspend fun getSupportedAssets(chainId: CAIPChainID): Result<List<CAIPAsset>>
}

/**
 * CAIP 餘額資訊
 */
data class CAIPBalance(
    val asset: CAIPAsset,              // 資產資訊
    val amount: String,                // 餘額數量
    val decimals: Int,                 // 小數位數
    val symbol: String,                // 代幣符號  
    val usdValue: String? = null,      // 美元價值
    val lastUpdated: Long = Clock.System.now().toEpochMilliseconds(),
    val metadata: Map<String, Any> = emptyMap()  // 附加元數據
) {
    /**
     * 轉換為舊版餘額格式（向後兼容）
     */
    fun toLegacyBalance(): Balance {
        return Balance(
            amount = amount,
            decimals = decimals,
            symbol = symbol,
            usdValue = usdValue,
            lastUpdated = lastUpdated
        )
    }
}

/**
 * CAIP 未簽名交易
 */
data class CAIPUnsignedTransaction(
    val rawData: String,               // 原始交易數據
    val chainId: CAIPChainID,         // 區塊鏈 ID
    val estimatedFee: CAIPTransactionFee, // 預估手續費
    val expirationTime: Long? = null,  // 過期時間
    val metadata: Map<String, Any> = emptyMap()  // 附加元數據
) {
    /**
     * 轉換為舊版未簽名交易格式
     */
    fun toLegacyUnsignedTransaction(): UnsignedTransaction {
        return UnsignedTransaction(
            rawData = rawData,
            chainType = chainId.toMultiChainType(),
            estimatedFee = estimatedFee.toLegacyTransactionFee(),
            expirationTime = expirationTime,
            metadata = metadata
        )
    }
}

/**
 * CAIP 已簽名交易
 */
data class CAIPSignedTransaction(
    val rawData: String,               // 簽名後的交易數據
    val signature: String,             // 交易簽名
    val chainId: CAIPChainID,         // 區塊鏈 ID
    val hash: String? = null          // 交易雜湊（可選）
) {
    /**
     * 轉換為舊版已簽名交易格式
     */
    fun toLegacySignedTransaction(): SignedTransaction {
        return SignedTransaction(
            rawData = rawData,
            signature = signature,
            chainType = chainId.toMultiChainType(),
            hash = hash
        )
    }
}

/**
 * CAIP 交易手續費
 */
data class CAIPTransactionFee(
    val gasLimit: String,              // Gas 限制
    val gasPrice: String,              // Gas 價格
    val estimatedCost: String,         // 預估成本
    val asset: CAIPAsset,             // 手續費支付資產
    val usdValue: String? = null,      // 美元價值
    val priority: TransactionPriority  // 優先級
) {
    /**
     * 轉換為舊版交易手續費格式
     */
    fun toLegacyTransactionFee(): TransactionFee {
        return TransactionFee(
            gasLimit = gasLimit,
            gasPrice = gasPrice,
            estimatedCost = estimatedCost,
            usdValue = usdValue,
            priority = priority
        )
    }
}

/**
 * CAIP 地址驗證結果
 */
data class CAIPAddressValidation(
    val isValid: Boolean,              // 是否有效
    val addressType: CAIPAddressType? = null,  // 地址類型
    val networkMatches: Boolean = true, // 是否匹配當前網路
    val message: String? = null,       // 驗證訊息
    val supportedOperations: Set<String> = emptySet()  // 支援的操作
) {
    /**
     * 轉換為舊版地址驗證格式
     */
    fun toLegacyAddressValidation(): AddressValidation {
        return AddressValidation(
            isValid = isValid,
            addressType = addressType?.toLegacyAddressType(),
            networkMatches = networkMatches,
            message = message
        )
    }
}

/**
 * CAIP 地址類型
 */
enum class CAIPAddressType {
    EOA,                    // 外部擁有帳戶
    CONTRACT,               // 智能合約
    MULTISIG,              // 多重簽名
    VALIDATOR,              // 驗證器
    SYSTEM,                // 系統帳戶
    UNKNOWN;               // 未知類型
    
    fun toLegacyAddressType(): AddressType {
        return when (this) {
            EOA -> AddressType.LEGACY
            CONTRACT -> AddressType.SMART_CONTRACT
            MULTISIG -> AddressType.MULTI_SIG
            else -> AddressType.UNKNOWN
        }
    }
}

/**
 * CAIP 標準 SDK 適配器的抽象實作
 * 
 * 提供 CAIP 標準和舊版介面之間的橋接
 */
abstract class AbstractCAIPSDKAdapter : CAIPBlockchainSDKAdapter {
    
    protected val logger = Logger.withTag("CAIPSDKAdapter")
    protected val caipService = CAIPService()
    
    // 舊版介面的橋接實作
    
    override suspend fun getAccountBalance(address: String): Result<Balance> {
        logger.d("Converting legacy balance query to CAIP format")
        
        val caipAddress = CAIPAddress.fromLegacyAddress(address, chainType)
        val caipBalanceResult = getAccountBalanceCAIP(caipAddress)
        
        return when (caipBalanceResult) {
            is Result.Success -> Result.Success(caipBalanceResult.data.toLegacyBalance())
            is Result.Failure -> Result.Failure(caipBalanceResult.exception)
            is Result.Loading -> Result.Loading()
        }
    }
    
    override suspend fun createTransaction(request: TransactionRequest): Result<UnsignedTransaction> {
        logger.d("Converting legacy transaction request to CAIP format")
        
        val fromCAIP = CAIPAddress.fromLegacyAddress(request.fromAddress, chainType)
        val toCAIP = CAIPAddress.fromLegacyAddress(request.toAddress, chainType)
        
        // 決定資產類型
        val asset = if (request.tokenAddress != null) {
            CAIPAsset.createERC20Asset(request.tokenAddress, chainType)
        } else {
            CAIPAsset.createNativeAsset(chainType)
        }
        
        val caipRequest = CAIPTransactionRequest(
            fromAddress = fromCAIP,
            toAddress = toCAIP,
            asset = asset,
            amount = request.amount,
            memo = request.memo,
            gasLimit = request.customGasLimit,
            gasPrice = request.customGasPrice
        )
        
        val caipTransactionResult = createTransactionCAIP(caipRequest)
        
        return when (caipTransactionResult) {
            is Result.Success -> Result.Success(caipTransactionResult.data.toLegacyUnsignedTransaction())
            is Result.Failure -> Result.Failure(caipTransactionResult.exception)
            is Result.Loading -> Result.Loading()
        }
    }
    
    override fun validateAddress(address: String): Result<AddressValidation> {
        logger.d("Converting legacy address validation to CAIP format")
        
        val caipAddress = CAIPAddress.fromLegacyAddress(address, chainType)
        val caipValidationResult = validateAddressCAIP(caipAddress)
        
        return when (caipValidationResult) {
            is Result.Success -> Result.Success(caipValidationResult.data.toLegacyAddressValidation())
            is Result.Failure -> Result.Failure(caipValidationResult.exception)
            is Result.Loading -> Result.Loading()
        }
    }
    
    override suspend fun broadcastTransaction(signedTransaction: SignedTransaction): Result<TransactionResult> {
        logger.d("Converting legacy broadcast to CAIP format")
        
        val chainId = CAIPChainID.fromMultiChainType(signedTransaction.chainType)
        val caipSigned = CAIPSignedTransaction(
            rawData = signedTransaction.rawData,
            signature = signedTransaction.signature,
            chainId = chainId,
            hash = signedTransaction.hash
        )
        
        val caipResult = broadcastTransactionCAIP(caipSigned)
        
        return when (caipResult) {
            is Result.Success -> {
                Result.Success(TransactionResult(
                    hash = caipResult.data.transactionHash,
                    status = caipResult.data.status.toLegacyTransactionStatus(),
                    blockNumber = caipResult.data.blockNumber,
                    gasUsed = caipResult.data.gasUsed,
                    message = "Transaction broadcast successfully"
                ))
            }
            is Result.Failure -> Result.Failure(caipResult.exception)
            is Result.Loading -> Result.Loading()
        }
    }
    
    // 抽象方法 - 子類別實作具體的 CAIP 邏輯
    
    /**
     * 取得鏈的預設網路名稱
     */
    protected open fun getDefaultNetwork(): String = "mainnet"
    
    /**
     * 將 MultiChainType 轉換為支援的 CAIP 命名空間
     */
    protected fun chainTypeToNamespace(chainType: MultiChainType): String {
        return when (chainType) {
            MultiChainType.ETHEREUM -> "eip155"
            MultiChainType.BSC -> "eip155"
            MultiChainType.POLYGON -> "eip155"
            MultiChainType.AVALANCHE -> "eip155"
            MultiChainType.ARBITRUM -> "eip155"
            MultiChainType.OPTIMISM -> "eip155"
            MultiChainType.CRONOS -> "eip155"
            MultiChainType.BASE -> "eip155"
            MultiChainType.FANTOM -> "eip155"
            MultiChainType.CELO -> "eip155"
            MultiChainType.MOONBEAM -> "eip155"
            MultiChainType.LINEA -> "eip155"
            MultiChainType.ZKSYNC -> "eip155"
            MultiChainType.SOLANA -> "solana"
            MultiChainType.BITCOIN -> "bip122"
            MultiChainType.POLKADOT -> "polkadot"
            MultiChainType.CARDANO -> "cardano"
            MultiChainType.TRON -> "tron"
            MultiChainType.MONERO -> "monero"
            MultiChainType.BITCOIN_CASH -> "bip122"
            MultiChainType.LITECOIN -> "bip122"
            MultiChainType.DOGECOIN -> "bip122"
        }
    }
    
    // 預設的 CAIP 實作（可被子類別覆寫）
    
    override val supportedNamespaces: List<String>
        get() = listOf(chainTypeToNamespace(chainType))
    
    override val supportedAssetNamespaces: List<String>
        get() = listOf("slip44", "erc20", "erc721", "erc1155")
    
    override fun getSupportedChainIDs(): List<CAIPChainID> {
        return listOf(CAIPChainID.fromMultiChainType(chainType, getDefaultNetwork()))
    }
    
    override suspend fun getSupportedAssets(chainId: CAIPChainID): Result<List<CAIPAsset>> {
        val nativeAsset = CAIPAsset.createNativeAsset(chainType, getDefaultNetwork())
        return Result.Success(listOf(nativeAsset))
    }
}

/**
 * CAIP 工具類別
 */
object CAIPUtils {
    
    /**
     * 批次轉換地址為 CAIP 格式
     */
    fun convertAddressesToCAIP(
        addresses: List<String>,
        chainType: MultiChainType,
        network: String = "mainnet"
    ): List<CAIPAddress> {
        return addresses.map { address ->
            CAIPAddress.fromLegacyAddress(address, chainType, network)
        }
    }
    
    /**
     * 批次驗證 CAIP 地址
     */
    suspend fun validateCAIPAddresses(addresses: List<CAIPAddress>): Map<CAIPAddress, Result<Boolean>> {
        return addresses.associateWith { address ->
            address.validate()
        }
    }
    
    /**
     * 解析混合格式地址（支援 CAIP 和舊版格式）
     */
    fun parseFlexibleAddress(
        addressString: String,
        defaultChainType: MultiChainType? = null
    ): Result<CAIPAddress> {
        return if (addressString.contains(':')) {
            // 嘗試解析為 CAIP 格式
            CAIPAddress.parse(addressString)
        } else if (defaultChainType != null) {
            // 當作舊版地址處理
            Result.Success(CAIPAddress.fromLegacyAddress(addressString, defaultChainType))
        } else {
            Result.Failure(IllegalArgumentException("Cannot parse address without chain context: $addressString"))
        }
    }
    
    /**
     * 為交易生成區塊瀏覽器連結
     */
    fun generateExplorerLink(result: CAIPTransactionResult): String? {
        return CAIPService().getExplorerUrl(result.chainId, result.transactionHash)
    }
}

// 擴展函數

/**
 * CAIPChainID 擴展函數 - 轉換為 MultiChainType
 */
fun CAIPChainID.toMultiChainType(): MultiChainType {
    return when (namespace) {
        "eip155" -> MultiChainType.ETHEREUM // 所有 EVM 兼容鏈都對應到 ETHEREUM
        "solana" -> MultiChainType.SOLANA
        "bip122" -> when {
            reference.contains("000000000019d6689c085ae165831e93") -> MultiChainType.BITCOIN
            else -> MultiChainType.BITCOIN // 預設為 Bitcoin
        }
        "polkadot" -> MultiChainType.POLKADOT
        "cardano" -> MultiChainType.CARDANO
        "tron" -> MultiChainType.TRON
        "monero" -> MultiChainType.MONERO
        else -> MultiChainType.ETHEREUM // 預設值
    }
}

/**
 * CAIPTransactionStatus 擴展函數 - 轉換為舊版交易狀態
 */
fun CAIPTransactionStatus.toLegacyTransactionStatus(): TransactionStatus {
    return when (this) {
        CAIPTransactionStatus.PENDING -> TransactionStatus.PENDING
        CAIPTransactionStatus.CONFIRMED -> TransactionStatus.CONFIRMED
        CAIPTransactionStatus.FAILED -> TransactionStatus.FAILED
        CAIPTransactionStatus.CANCELLED -> TransactionStatus.CANCELLED
    }
}
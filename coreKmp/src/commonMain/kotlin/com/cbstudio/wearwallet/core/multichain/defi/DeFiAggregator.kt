package com.cbstudio.wearwallet.core.multichain.defi

import com.cbstudio.wearwallet.core.multichain.*
import com.cbstudio.wearwallet.core.multichain.sdk.*
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.rango.RangoRepository
import com.cbstudio.wearwallet.core.zerox.ZeroXRepository
import com.cbstudio.wearwallet.core.zerox.model.ZeroXQuoteResponse
import com.cbstudio.wearwallet.core.rango.model.RangoQuoteResponse
import com.cbstudio.wearwallet.core.multichain.defi.models.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import co.touchlab.kermit.Logger
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

/**
 * DeFi 聚合器服務
 * 
 * 整合多個 DeFi 協議，提供最佳收益和流動性
 */
class DeFiAggregator(
    private val walletManager: MultiChainWalletManager,
    private val rangoRepository: RangoRepository,
    private val zeroXRepository: ZeroXRepository
) {
    
    private val logger = Logger.withTag("DeFiAggregator")
    private val json = Json { ignoreUnknownKeys = true }
    
    // DeFi 狀態
    private val _defiState = MutableStateFlow(DeFiState())
    val defiState: StateFlow<DeFiState> = _defiState.asStateFlow()
    
    /**
     * DeFi 狀態
     */
    data class DeFiState(
        val isInitialized: Boolean = false,
        val supportedProtocols: List<DeFiProtocol> = emptyList(),
        val liquidityPools: List<LiquidityPool> = emptyList(),
        val yieldFarms: List<YieldFarm> = emptyList(),
        val lendingMarkets: List<LendingMarket> = emptyList(),
        val userPositions: List<DeFiPosition> = emptyList()
    )
    
    /**
     * DeFi 協議
     */
    data class DeFiProtocol(
        val id: String,
        val name: String,
        val chainType: MultiChainType,
        val category: DeFiCategory,
        val tvl: String, // Total Value Locked
        val apr: Double, // Annual Percentage Rate
        val risk: RiskLevel,
        val isActive: Boolean = true
    )
    
    /**
     * DeFi 類別
     */
    enum class DeFiCategory {
        DEX,           // 去中心化交易所
        LENDING,       // 借貸協議
        YIELD_FARMING, // 收益農場
        STAKING,       // 質押
        LIQUIDITY,     // 流動性提供
        DERIVATIVES,   // 衍生品
        INSURANCE,     // 保險
        AGGREGATOR     // 聚合器
    }
    

    
    /**
     * 流動性池
     */
    data class LiquidityPool(
        val id: String,
        val protocol: String,
        val chainType: MultiChainType,
        val tokenA: String,
        val tokenB: String,
        val tvl: String,
        val volume24h: String,
        val apr: Double,
        val fees24h: String,
        val impermanentLoss: Double? = null
    )
    
    /**
     * 收益農場
     */
    data class YieldFarm(
        val id: String,
        val protocol: String,
        val chainType: MultiChainType,
        val stakingToken: String,
        val rewardToken: String,
        val apr: Double,
        val tvl: String,
        val duration: Long?, // 鎖定期（毫秒）
        val minStake: String,
        val maxStake: String?
    )
    
    /**
     * 借貸市場
     */
    data class LendingMarket(
        val id: String,
        val protocol: String,
        val chainType: MultiChainType,
        val asset: String,
        val supplyApr: Double,
        val borrowApr: Double,
        val totalSupply: String,
        val totalBorrow: String,
        val utilizationRate: Double,
        val collateralFactor: Double
    )
    
    /**
     * DeFi 倉位
     */
    data class DeFiPosition(
        val id: String,
        val protocol: String,
        val chainType: MultiChainType,
        val category: DeFiCategory,
        val asset: String,
        val amount: String,
        val value: String,
        val pnl: String, // Profit and Loss
        val apy: Double,
        val startTime: Long,
        val status: PositionStatus
    )
    
    /**
     * 倉位狀態
     */
    enum class PositionStatus {
        ACTIVE,
        PENDING,
        CLOSED,
        LIQUIDATED
    }
    
    /**
     * 交換參數
     */
    data class SwapParams(
        val fromToken: String,
        val toToken: String,
        val amount: String,
        val slippage: Double = 0.5, // 0.5%
        val deadline: Long? = null,
        val fromChain: MultiChainType? = null,
        val toChain: MultiChainType? = null,
        val userAddress: String? = null // For 0x/Rango quotes
    )
    
    /**
     * 交換報價
     */
    data class SwapQuote(
        val protocol: String,
        val chainType: MultiChainType,
        val fromToken: String,
        val toToken: String,
        val fromAmount: String,
        val toAmount: String,
        val price: Double,
        val priceImpact: Double,
        val fee: String,
        val estimatedGas: String,
        val route: List<String>,
        // 內部使用，用於存儲原始數據以供 executeSwap 使用
        val provider: String = "", // "0x" or "Rango"
        val rawData: String = "" // JSON encoded raw response
    )
    
    /**
     * 初始化 DeFi 聚合器
     */
    suspend fun initialize(): Result<Unit> {
        return try {
            logger.i("Initializing DeFi Aggregator")
            
            // 載入支援的協議
            val protocols = loadSupportedProtocols()
            
            // 載入流動性池
            val pools = loadLiquidityPools()
            
            // 載入收益農場
            val farms = loadYieldFarms()
            
            // 載入借貸市場
            val markets = loadLendingMarkets()
            
            _defiState.value = _defiState.value.copy(
                isInitialized = true,
                supportedProtocols = protocols,
                liquidityPools = pools,
                yieldFarms = farms,
                lendingMarkets = markets
            )
            
            logger.i("DeFi Aggregator initialized with ${protocols.size} protocols")
            Result.Success(Unit)
        } catch (e: Exception) {
            logger.e("Failed to initialize DeFi Aggregator", e)
            Result.Failure(e)
        }
    }
    
    /**
     * 獲取最佳交換路徑
     * 
     * 策略：
     * 1. 如果是同鏈 EVM 交易 -> 優先使用 0x
     * 2. 如果是跨鏈或非 EVM 交易 -> 使用 Rango
     */
    suspend fun getBestSwapRoute(
        chainType: MultiChainType,
        params: SwapParams
    ): Result<SwapQuote> {
        return try {
            logger.i("Finding best swap route: ${params.fromToken} -> ${params.toToken} on $chainType")
            
            val targetChain = params.toChain ?: chainType
            val isSameChain = chainType == targetChain
            val isEVM = MultiChainType.isAccountChain(chainType) // Simplified check, assumes Account based ~ EVM usually
            
            // 1. 嘗試使用 0x (僅限同鏈 EVM)
            if (isSameChain && isEVM) {
                val chainId = mapTo0xChainId(chainType)
                if (chainId != null && params.userAddress != null) {
                    logger.i("Fetching 0x quote for chainId: $chainId")
                    val zeroXResult = zeroXRepository.getSwapQuote(
                        chainId = chainId,
                        sellTokenAddress = params.fromToken,
                        buyTokenAddress = params.toToken,
                        sellAmount = params.amount,
                        takerAddress = params.userAddress
                    )
                    
                    if (zeroXResult.isSuccess) {
                        val quote = zeroXResult.getOrThrow()
                        return Result.Success(mapZeroXQuote(quote, chainType, params))
                    } else {
                        logger.w("0x quote failed, falling back to Rango: ${zeroXResult.exceptionOrNull()?.message}")
                    }
                }
            }
            
            // 2. 使用 Rango (跨鏈或非 EVM，或 0x 失敗)
            logger.i("Fetching Rango quote")
            val fromChainName = mapToRangoChain(chainType)
            val toChainName = mapToRangoChain(targetChain)
            
            if (fromChainName != null && toChainName != null) {
                val rangoResult = rangoRepository.getSwapQuote(
                    fromChain = fromChainName,
                    fromTokenSymbol = getTokenSymbol(params.fromToken), // Rango uses symbols often, or address? RangoClient impl check needed. Assumes symbol/address handling inside repo.
                    toChain = toChainName,
                    toTokenSymbol = getTokenSymbol(params.toToken),
                    amount = params.amount,
                    slippage = params.slippage
                )
                
                if (rangoResult.isSuccess) {
                    val quote = rangoResult.getOrThrow()
                    if (quote.resultType == "OK" && quote.route != null) {
                        return Result.Success(mapRangoQuote(quote, chainType, params))
                    } else {
                         return Result.Failure(Exception("Rango quote error: ${quote.error ?: quote.resultType}"))
                    }
                } else {
                    val exception = (rangoResult as? Result.Failure)?.exception ?: Exception("Unknown error")
                    return Result.Failure(exception)
                }
            }
            
            Result.Failure(Exception("No supported route found for ${chainType.name}"))
            
        } catch (e: Exception) {
            logger.e("Failed to get best swap route", e)
            Result.Failure(e)
        }
    }
    
    /**
     * 執行交換
     */
    suspend fun executeSwap(
        quote: SwapQuote,
        userAddress: String,
        privateKey: String
    ): Result<TransactionResult> {
        return try {
            logger.i("Executing swap on ${quote.protocol} via ${quote.provider}")
            
            val unsignedTxResult = when (quote.provider) {
                "0x" -> {
                    // Parsing rawData (Assuming it's a JSON string)
                    val txTo = getValueFromJson(quote.rawData, "to")
                    val txData = getValueFromJson(quote.rawData, "data")
                    val txValue = getValueFromJson(quote.rawData, "value")
                    
                    if (txTo == null || txData == null || txValue == null) {
                         Result.Failure(Exception("Invalid 0x transaction data"))
                    } else {
                        val request = TransactionRequest(
                            fromAddress = userAddress,
                            toAddress = txTo,
                            amount = "0", // Amount is inside the call data (or verified via value for ETH)
                            tokenAddress = quote.fromToken, // 只是參考用
                            priority = TransactionPriority.NORMAL,
                            memo = "Swap via 0x",
                            data = txData,
                            value = txValue
                        )
                        
                        logger.i("Created 0x TransactionRequest: to=$txTo, value=$txValue")
                        walletManager.createTransaction(quote.chainType, request)
                    }
                }
                "Rango" -> {
                    // Rango 需要兩步：1. Quote (Done) 2. Create Swap Tx
                    val fromChain = mapToRangoChain(quote.chainType) ?: return Result.Failure(Exception("Invalid chain"))
                    val toChain = mapToRangoChain(quote.chainType) ?: return Result.Failure(Exception("Invalid chain"))
                    
                    val createTxResult = rangoRepository.createSwapTransaction(
                        fromChain = fromChain,
                        fromTokenSymbol = getTokenSymbol(quote.fromToken),
                        toChain = toChain,
                        toTokenSymbol = getTokenSymbol(quote.toToken),
                        amount = quote.fromAmount,
                        fromAddress = userAddress,
                        toAddress = userAddress, // 假設發送給自己
                        slippage = 1.0 // Default
                    )

                    if (createTxResult.isSuccess) {
                        val swapResponse = createTxResult.getOrNull()
                        val tx = swapResponse?.transaction
                        
                        if (tx != null) {
                            val request = TransactionRequest(
                                fromAddress = userAddress,
                                toAddress = tx.to ?: "",
                                amount = "0",
                                tokenAddress = quote.fromToken,
                                priority = TransactionPriority.NORMAL,
                                memo = "Swap via Rango",
                                data = tx.data ?: "",
                                value = tx.value ?: "0"
                            )
                            logger.i("Created Rango TransactionRequest: to=${tx.to}")
                            walletManager.createTransaction(quote.chainType, request)
                        } else {
                            Result.Failure(Exception("Rango transaction generation failed: No tx data"))
                        }
                    } else {
                        val exception = (createTxResult as? Result.Failure)?.exception ?: Exception("Unknown error")
                        Result.Failure(exception)
                    }
                }
                else -> Result.Failure(Exception("Unknown provider: ${quote.provider}"))
            }

            when (unsignedTxResult) {
                is Result.Success -> {
                    // 執行 簽名 -> 廣播
                    val unsignedTx = unsignedTxResult.data
                    signAndBroadcast(quote.chainType, unsignedTx, privateKey)
                }
                is Result.Failure -> Result.Failure(unsignedTxResult.exception)
                is Result.Loading -> Result.Failure(Exception("Transaction creation timed out"))
            }
        } catch (e: Exception) {
            logger.e("Swap execution failed", e)
            Result.Failure(e)
        }
    }

    /**
     * 輔助方法：簽名並廣播交易
     */
    private suspend fun signAndBroadcast(
        chainType: MultiChainType,
        unsignedTransaction: UnsignedTransaction,
        privateKey: String
    ): Result<TransactionResult> {
        return try {
            // 1. 簽名
            logger.i("Signing transaction for $chainType")
            val signedTxResult = walletManager.signTransaction(chainType, unsignedTransaction, privateKey)
            
            if (signedTxResult is Result.Failure) {
                logger.e("Transaction signing failed: ${signedTxResult.exception.message}")
                return Result.Failure(signedTxResult.exception)
            }
            
            val signedTx = (signedTxResult as Result.Success).data
            
            // 2. 廣播
            logger.i("Broadcasting transaction for $chainType")
            val broadcastResult = walletManager.broadcastTransaction(chainType, signedTx)
            
            if (broadcastResult is Result.Failure) {
                logger.e("Transaction broadcast failed: ${broadcastResult.exception.message}")
            } else if (broadcastResult is Result.Success) {
                val txResult = broadcastResult.data
                logger.i("Transaction broadcast successful! Hash: ${txResult.hash}")
            }
            
            broadcastResult
        } catch (e: Exception) {
            logger.e("Sign and broadcast flow failed", e)
            Result.Failure(e)
        }
    }

    // === Mappers ===
    
    private fun mapZeroXQuote(response: ZeroXQuoteResponse, chainType: MultiChainType, params: SwapParams): SwapQuote {
        // Serializing the full response to rawData to be used in executeSwap
        // Note: In a real app, uses specific DTOs or shared State.
        // We do a manual JSON construction for simplicity if serializer not avail, 
        // OR rely on the fact that we can just recreate the params.
        // But for 0x, valid quote IS the tx data.
        
        // Manual JSON construction to avoid heavy dependency issues inside standard lib if not setup
        val rawJson = """
            {
                "to": "${response.to}",
                "data": "${response.data}",
                "value": "${response.value}",
                "gasPrice": "${response.gasPrice}",
                "buyAmount": "${response.buyAmount}"
            }
        """.trimIndent()

        return SwapQuote(
            protocol = "0x Protocol",
            chainType = chainType,
            fromToken = params.fromToken,
            toToken = params.toToken,
            fromAmount = params.amount,
            toAmount = response.buyAmount, // Need decimal conversion? 0x returns base units
            price = response.price.toDoubleOrNull() ?: 0.0,
            priceImpact = 0.0, // 0x API specific param?
            fee = response.estimatedGas ?: "0",
            estimatedGas = response.estimatedGas ?: "0",
            route = response.sources?.map { it.name } ?: emptyList(),
            provider = "0x",
            rawData = rawJson
        )
    }
    
    private fun mapRangoQuote(response: RangoQuoteResponse, chainType: MultiChainType, params: SwapParams): SwapQuote {
        val route = response.route
        return SwapQuote(
            protocol = "Rango Exchange",
            chainType = chainType,
            fromToken = params.fromToken,
            toToken = params.toToken,
            fromAmount = params.amount,
            toAmount = route?.outputAmount ?: "0",
            price = route?.outputAmountUsd ?: 0.0, // Approx
            priceImpact = 0.0,
            fee = route?.feeUsd?.toString() ?: "0",
            estimatedGas = "0",
            route = route?.path?.map { it.swapperId ?: "Unknown" } ?: emptyList(),
            provider = "Rango",
            rawData = "" // Rango requires fresh 'create' call
        )
    }
    
    // === Helpers ===
    
    private fun mapTo0xChainId(type: MultiChainType): Int? {
        // Mapping MultiChainType (com.cbstudio.wearwallet.core.multichain) to ChainID
        return when (type) {
            MultiChainType.ETHEREUM -> 1
            MultiChainType.BSC -> 56
            MultiChainType.POLYGON -> 137
            MultiChainType.ARBITRUM -> 42161
            MultiChainType.OPTIMISM -> 10
            MultiChainType.AVALANCHE -> 43114
            MultiChainType.FANTOM -> 250
            MultiChainType.CRONOS -> 25
            MultiChainType.BASE -> 8453

            MultiChainType.MOONBEAM -> 1284

            MultiChainType.CELO -> 42220

            else -> null
        }
    }
    
    private fun mapToRangoChain(type: MultiChainType): String? {
        return when (type) {
            MultiChainType.ETHEREUM -> "ETH"
            MultiChainType.BSC -> "BSC"
            MultiChainType.POLYGON -> "POLYGON"
            MultiChainType.ARBITRUM -> "ARBITRUM"
            MultiChainType.OPTIMISM -> "OPTIMISM"
            MultiChainType.AVALANCHE -> "AVAX_CCHAIN"
            MultiChainType.FANTOM -> "FANTOM"
            MultiChainType.CRONOS -> "CRONOS"
            MultiChainType.SOLANA -> "SOLANA"
            MultiChainType.TRON -> "TRON"

            MultiChainType.BITCOIN -> "BTC"
            MultiChainType.LITECOIN -> "LTC"
            MultiChainType.DOGECOIN -> "DOGE"
            MultiChainType.BITCOIN_CASH -> "BCH"
            else -> null
        }
    }
    
    private fun getTokenSymbol(tokenAddressOrSymbol: String): String {
        // Simplistic extraction. 
        // In reality, should look up token metadataRepo by address.
        // For now, assume param passes symbol if address not available or logic handles it.
        // Or if it's 0xeeee..., return native.
        return if (tokenAddressOrSymbol.startsWith("0x") && tokenAddressOrSymbol.length > 10) {
            // It's an address, Rango might need address or symbol. 
            // Rango works best with "ETH", "USDT" etc.
            // Would need TokenRepository lookup here. 
            // Returning null/error or just the string for now.
            tokenAddressOrSymbol 
        } else {
            tokenAddressOrSymbol
        }
    }

    // Simplistic JSON value extractor to avoid heavy deps
    private fun getValueFromJson(json: String, key: String): String? {
        val regex = "\"$key\"\\s*:\\s*\"([^\"]+)\"".toRegex()
        return regex.find(json)?.groupValues?.get(1)
    }

    // === Existing Dummy Loaders (Kept for State Initialization compatibility) ===
    
    private fun loadSupportedProtocols(): List<DeFiProtocol> = emptyList()
    private fun loadLiquidityPools(): List<LiquidityPool> = emptyList()
    private fun loadYieldFarms(): List<YieldFarm> = emptyList()
    private fun loadLendingMarkets(): List<LendingMarket> = emptyList()

    /**
     * 清理資源
     */
    fun cleanup() {
        logger.i("Cleaning up DeFi Aggregator")
        _defiState.value = DeFiState()
    }
    
    // Stub other methods if they are called by ViewModels to avoid breaking changes
    suspend fun getBestYieldOpportunities(
        chainType: MultiChainType? = null,
        minApr: Double = 0.0,
        maxRisk: RiskLevel = RiskLevel.HIGH
    ): List<YieldOpportunity> = emptyList()
    
    suspend fun addLiquidity(poolId: String, tA: String, tB: String, mA: String, mB: String, uA: String) = Result.Success(UnsignedTransaction("", MultiChainType.ETHEREUM, TransactionFee("0", "0", "0", priority = TransactionPriority.NORMAL)))
    suspend fun lend(mId: String, asset: String, amt: String, uA: String) = Result.Success(UnsignedTransaction("", MultiChainType.ETHEREUM, TransactionFee("0", "0", "0", priority = TransactionPriority.NORMAL)))
    fun getUserPositions(uA: String): List<DeFiPosition> = emptyList()
    
}

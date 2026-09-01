package com.cbstudio.wearwallet.core.multichain.defi.lending

import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.core.multichain.model.BlockchainException
import com.cbstudio.wearwallet.core.domain.model.RiskLevel
import co.touchlab.kermit.Logger

/**
 * 借貸協定介面
 * 支援多個區塊鏈上的借貸平台整合
 */
interface LendingProtocol {
    
    /**
     * 協定名稱
     */
    val protocolName: String
    
    /**
     * 支援的區塊鏈列表
     */
    val supportedChains: List<MultiChainType>
    
    /**
     * 取得可用的借貸市場
     */
    suspend fun getAvailableMarkets(
        chainType: MultiChainType,
        category: MarketCategory = MarketCategory.ALL
    ): List<LendingMarket>
    
    /**
     * 取得市場詳細資訊
     */
    suspend fun getMarketDetails(
        marketAddress: String,
        chainType: MultiChainType
    ): LendingMarketDetails
    
    /**
     * 取得用戶借貸持倉
     */
    suspend fun getUserLendingPosition(
        userAddress: String,
        chainType: MultiChainType
    ): UserLendingPosition
    
    /**
     * 創建存款交易
     */
    suspend fun createSupplyTransaction(
        request: SupplyRequest
    ): String
    
    /**
     * 創建提款交易
     */
    suspend fun createWithdrawTransaction(
        request: WithdrawRequest
    ): String
    
    /**
     * 創建借款交易
     */
    suspend fun createBorrowTransaction(
        request: BorrowRequest
    ): String
    
    /**
     * 創建還款交易
     */
    suspend fun createRepayTransaction(
        request: RepayRequest
    ): String
    
    /**
     * 計算健康係數
     */
    suspend fun calculateHealthFactor(
        userAddress: String,
        chainType: MultiChainType
    ): HealthFactor
    
    /**
     * 計算借貸能力
     */
    suspend fun calculateBorrowCapacity(
        userAddress: String,
        chainType: MultiChainType
    ): BorrowCapacity
    
    /**
     * 取得清算資訊
     */
    suspend fun getLiquidationInfo(
        userAddress: String,
        chainType: MultiChainType
    ): LiquidationInfo
}

/**
 * 借貸市場
 */
data class LendingMarket(
    val address: String,
    val token: LendingToken,
    val chainType: MultiChainType,
    val protocolName: String,
    val supplyApr: Double,          // 存款年利率
    val borrowApr: Double,          // 借款年利率
    val totalSupply: String,        // 總存款
    val totalBorrow: String,        // 總借款
    val utilizationRate: Double,    // 使用率
    val liquidationThreshold: Double, // 清算閾值
    val collateralFactor: Double,   // 抵押係數
    val reserveFactor: Double,      // 儲備係數
    val isCollateralEnabled: Boolean, // 是否可作為抵押品
    val isBorrowEnabled: Boolean,   // 是否可借款
    val marketCap: String,          // 市值
    val riskRating: RiskRating      // 風險評級
)

/**
 * 借貸代幣資訊
 */
data class LendingToken(
    val contractAddress: String?,
    val symbol: String,
    val name: String,
    val decimals: Int,
    val logoUrl: String?,
    val price: String?,             // USD 價格
    val priceChange24h: Double,     // 24小時價格變化
    val isNative: Boolean = contractAddress == null
)

/**
 * 借貸市場詳細資訊
 */
data class LendingMarketDetails(
    val market: LendingMarket,
    val interestRateModel: InterestRateModel,
    val marketMetrics: MarketMetrics,
    val riskParameters: RiskParameters,
    val historicalData: MarketHistoricalData,
    val governanceInfo: GovernanceInfo?
)

/**
 * 利率模型
 */
data class InterestRateModel(
    val modelType: InterestRateModelType,
    val baseRate: Double,
    val multiplier: Double,
    val jumpMultiplier: Double?,
    val kink: Double?,              // 利率跳躍點
    val reserveFactor: Double
)

/**
 * 利率模型類型
 */
enum class InterestRateModelType {
    LINEAR,                         // 線性模型
    KINKED,                        // 折線模型
    JUMP_RATE                      // 跳躍利率模型
}

/**
 * 市場指標
 */
data class MarketMetrics(
    val totalLiquidity: String,     // 總流動性
    val availableLiquidity: String, // 可用流動性
    val borrowCapUsed: String,      // 已使用借款上限
    val borrowCapTotal: String,     // 總借款上限
    val supplyCap: String?,         // 存款上限
    val dailyVolume: String,        // 日交易量
    val activeUsers: Int,           // 活躍用戶數
    val avgLoanSize: String         // 平均借款規模
)

/**
 * 風險參數
 */
data class RiskParameters(
    val liquidationIncentive: Double, // 清算激勵
    val closeFactor: Double,         // 清算因子
    val collateralFactor: Double,    // 抵押因子
    val liquidationThreshold: Double, // 清算閾值
    val debtCeiling: String?,        // 債務上限
    val priceOracle: String          // 價格預言機
)

/**
 * 市場歷史數據
 */
data class MarketHistoricalData(
    val aprHistory: List<AprHistoryPoint>,
    val utilizationHistory: List<UtilizationPoint>,
    val liquidationEvents: List<LiquidationEvent>,
    val totalValueHistory: List<TvlPoint>
)

/**
 * APR 歷史點
 */
data class AprHistoryPoint(
    val timestamp: Long,
    val supplyApr: Double,
    val borrowApr: Double
)

/**
 * 使用率歷史點
 */
data class UtilizationPoint(
    val timestamp: Long,
    val utilizationRate: Double
)

/**
 * 清算事件
 */
data class LiquidationEvent(
    val timestamp: Long,
    val liquidatorAddress: String,
    val borrowerAddress: String,
    val repayAmount: String,
    val collateralSeized: String,
    val liquidationIncentive: String
)

/**
 * TVL 歷史點
 */
data class TvlPoint(
    val timestamp: Long,
    val totalValue: String
)

/**
 * 治理資訊
 */
data class GovernanceInfo(
    val governanceToken: LendingToken?,
    val votingPower: String?,
    val proposals: List<GovernanceProposal>
)

/**
 * 治理提案
 */
data class GovernanceProposal(
    val id: String,
    val title: String,
    val description: String,
    val status: ProposalStatus,
    val votesFor: String,
    val votesAgainst: String,
    val endTime: Long
)

/**
 * 提案狀態
 */
enum class ProposalStatus {
    PENDING,
    ACTIVE,
    SUCCEEDED,
    DEFEATED,
    QUEUED,
    EXECUTED,
    CANCELLED
}

/**
 * 用戶借貸持倉
 */
data class UserLendingPosition(
    val userAddress: String,
    val chainType: MultiChainType,
    val protocolName: String,
    val suppliedAssets: List<SuppliedAsset>,
    val borrowedAssets: List<BorrowedAsset>,
    val totalSuppliedValue: String,   // 總存款價值 USD
    val totalBorrowedValue: String,   // 總借款價值 USD
    val netWorth: String,             // 淨值 USD
    val healthFactor: HealthFactor,
    val borrowCapacity: BorrowCapacity,
    val liquidationRisk: LiquidationRisk,
    val earnedInterest: String,       // 累積利息收入
    val paidInterest: String          // 累積利息支出
)

/**
 * 供應的資產
 */
data class SuppliedAsset(
    val market: LendingMarket,
    val suppliedAmount: String,       // 存款數量
    val suppliedValue: String,        // 存款價值 USD
    val earnedInterest: String,       // 累積利息
    val aTokenBalance: String,        // aToken 餘額（如 Aave）
    val isCollateral: Boolean,        // 是否用作抵押品
    val canBeWithdrawn: String        // 可提取數量
)

/**
 * 借款的資產
 */
data class BorrowedAsset(
    val market: LendingMarket,
    val borrowedAmount: String,       // 借款數量
    val borrowedValue: String,        // 借款價值 USD
    val accruedInterest: String,      // 累積利息
    val interestRate: Double,         // 當前利率
    val rateMode: InterestRateMode,   // 利率模式
    val healthContribution: Double    // 對健康係數的影響
)

/**
 * 利率模式
 */
enum class InterestRateMode {
    STABLE,                          // 固定利率
    VARIABLE                         // 浮動利率
}

/**
 * 健康係數
 */
data class HealthFactor(
    val value: Double,               // 健康係數值
    val status: HealthStatus,        // 健康狀態
    val liquidationThreshold: Double, // 清算閾值
    val safetyMargin: String,        // 安全邊際 USD
    val riskLevel: RiskLevel         // 風險等級
)

/**
 * 健康狀態
 */
enum class HealthStatus {
    HEALTHY,                         // 健康
    WARNING,                         // 警告
    DANGER,                          // 危險
    LIQUIDATABLE                     // 可清算
}

/**
 * 借貸能力
 */
data class BorrowCapacity(
    val maxBorrowValue: String,      // 最大借款價值 USD
    val currentBorrowValue: String,  // 當前借款價值 USD
    val availableBorrowValue: String, // 可用借款價值 USD
    val utilizationRate: Double,     // 借貸使用率
    val borrowPowerUsed: Double      // 借款能力使用率
)

/**
 * 清算資訊
 */
data class LiquidationInfo(
    val isAtRisk: Boolean,           // 是否有清算風險
    val liquidationPrice: Map<String, String>, // 各資產清算價格
    val timeToLiquidation: Long?,    // 預估清算時間
    val protectionStrategies: List<ProtectionStrategy>
)

/**
 * 清算風險等級
 */
enum class LiquidationRisk {
    NONE,                            // 無風險
    LOW,                             // 低風險
    MEDIUM,                          // 中風險
    HIGH,                            // 高風險
    CRITICAL                         // 危急風險
}

/**
 * 保護策略
 */
data class ProtectionStrategy(
    val type: ProtectionType,
    val description: String,
    val estimatedCost: String?,      // 預估成本
    val effectivenessRating: Double  // 有效性評級
)

/**
 * 保護策略類型
 */
enum class ProtectionType {
    ADD_COLLATERAL,                  // 增加抵押品
    REPAY_DEBT,                      // 償還債務
    SWITCH_RATE_MODE,                // 切換利率模式
    LIQUIDATE_PARTIALLY,             // 部分清算
    HEDGE_POSITION                   // 對沖倉位
}

/**
 * 存款請求
 */
data class SupplyRequest(
    val marketAddress: String,
    val amount: String,
    val userAddress: String,
    val enableAsCollateral: Boolean = true,
    val chainType: MultiChainType
)

/**
 * 提款請求
 */
data class WithdrawRequest(
    val marketAddress: String,
    val amount: String,               // 提款數量，"max" 表示全部
    val userAddress: String,
    val chainType: MultiChainType
)

/**
 * 借款請求
 */
data class BorrowRequest(
    val marketAddress: String,
    val amount: String,
    val userAddress: String,
    val interestRateMode: InterestRateMode = InterestRateMode.VARIABLE,
    val chainType: MultiChainType
)

/**
 * 還款請求
 */
data class RepayRequest(
    val marketAddress: String,
    val amount: String,               // 還款數量，"max" 表示全部
    val userAddress: String,
    val onBehalfOf: String? = null,  // 代償地址
    val rateMode: InterestRateMode = InterestRateMode.VARIABLE,
    val chainType: MultiChainType
)

/**
 * 市場分類
 */
enum class MarketCategory {
    ALL,                             // 全部
    STABLE_COINS,                    // 穩定幣
    MAJOR_ASSETS,                    // 主要資產
    ALTCOINS,                        // 山寨幣
    HIGH_YIELD,                      // 高收益
    LOW_RISK,                        // 低風險
    NEW_MARKETS                      // 新市場
}

/**
 * 風險評級
 */
enum class RiskRating {
    AAA, AA, A, BBB, BB, B, CCC, CC, C, D
}

/**
 * Compound 協定實現
 */
class CompoundProtocol(
    private val logger: Logger = Logger.withTag("CompoundProtocol")
) : LendingProtocol {
    
    override val protocolName = "Compound"
    
    override val supportedChains = listOf(
        MultiChainType.ETHEREUM
    )
    
    override suspend fun getAvailableMarkets(
        chainType: MultiChainType,
        category: MarketCategory
    ): List<LendingMarket> {
        validateChainSupport(chainType)
        
        logger.d("Getting Compound markets for ${chainType.symbol}")
        
        return try {
            // TODO: 實際的 Compound API 調用
            // const compound = new CompoundAPI()
            // const markets = await compound.getMarkets(chainType, category)
            
            // 暫時的模擬市場列表
            listOf(
                LendingMarket(
                    address = "0x70e36f6BF80a52b3B46b3aF8e106CC0ed743E8e4", // cLEND
                    token = LendingToken(
                        contractAddress = null,
                        symbol = "ETH",
                        name = "Ethereum",
                        decimals = 18,
                        logoUrl = "https://tokens.1inch.io/0xeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee.png",
                        price = "1800.50",
                        priceChange24h = 2.5
                    ),
                    chainType = chainType,
                    protocolName = protocolName,
                    supplyApr = 3.2,
                    borrowApr = 5.8,
                    totalSupply = "1000000.0",
                    totalBorrow = "750000.0",
                    utilizationRate = 0.75,
                    liquidationThreshold = 0.825,
                    collateralFactor = 0.75,
                    reserveFactor = 0.15,
                    isCollateralEnabled = true,
                    isBorrowEnabled = true,
                    marketCap = "1800500000.0",
                    riskRating = RiskRating.AA
                ),
                LendingMarket(
                    address = "0x39AA39c021dfbaE8faC545936693aC917d5E7563", // cUSDC
                    token = LendingToken(
                        contractAddress = "0xA0b86a33E6441e5e4b6f4cc12D5d91E2C5E8B6F9",
                        symbol = "USDC",
                        name = "USD Coin",
                        decimals = 6,
                        logoUrl = "https://tokens.1inch.io/0xa0b86a33e6441e5e4b6f4cc12d5d91e2c5e8b6f9.png",
                        price = "1.00",
                        priceChange24h = 0.1,
                        isNative = false
                    ),
                    chainType = chainType,
                    protocolName = protocolName,
                    supplyApr = 4.1,
                    borrowApr = 6.3,
                    totalSupply = "500000000.0",
                    totalBorrow = "350000000.0",
                    utilizationRate = 0.70,
                    liquidationThreshold = 0.85,
                    collateralFactor = 0.80,
                    reserveFactor = 0.10,
                    isCollateralEnabled = true,
                    isBorrowEnabled = true,
                    marketCap = "500000000.0",
                    riskRating = RiskRating.AAA
                )
            )
        } catch (e: Exception) {
            logger.e("Failed to get Compound markets", e)
            throw BlockchainException.ApiException(
                chainType,
                "compound markets",
                null,
                "Failed to get markets: ${e.message}",
                e
            )
        }
    }
    
    override suspend fun getMarketDetails(
        marketAddress: String,
        chainType: MultiChainType
    ): LendingMarketDetails {
        validateChainSupport(chainType)
        
        // TODO: 實際的市場詳細資訊查詢
        throw BlockchainException.UnsupportedOperationException(
            chainType,
            "Compound market details - implementation pending"
        )
    }
    
    override suspend fun getUserLendingPosition(
        userAddress: String,
        chainType: MultiChainType
    ): UserLendingPosition {
        validateChainSupport(chainType)
        
        // TODO: 實際的用戶借貸持倉查詢
        throw BlockchainException.UnsupportedOperationException(
            chainType,
            "Compound user position - implementation pending"
        )
    }
    
    override suspend fun createSupplyTransaction(request: SupplyRequest): String {
        validateChainSupport(request.chainType)
        
        // TODO: 實際的存款交易建構
        throw BlockchainException.UnsupportedOperationException(
            request.chainType,
            "Compound supply transaction - implementation pending"
        )
    }
    
    override suspend fun createWithdrawTransaction(request: WithdrawRequest): String {
        validateChainSupport(request.chainType)
        
        // TODO: 實際的提款交易建構
        throw BlockchainException.UnsupportedOperationException(
            request.chainType,
            "Compound withdraw transaction - implementation pending"
        )
    }
    
    override suspend fun createBorrowTransaction(request: BorrowRequest): String {
        validateChainSupport(request.chainType)
        
        // TODO: 實際的借款交易建構
        throw BlockchainException.UnsupportedOperationException(
            request.chainType,
            "Compound borrow transaction - implementation pending"
        )
    }
    
    override suspend fun createRepayTransaction(request: RepayRequest): String {
        validateChainSupport(request.chainType)
        
        // TODO: 實際的還款交易建構
        throw BlockchainException.UnsupportedOperationException(
            request.chainType,
            "Compound repay transaction - implementation pending"
        )
    }
    
    override suspend fun calculateHealthFactor(
        userAddress: String,
        chainType: MultiChainType
    ): HealthFactor {
        validateChainSupport(chainType)
        
        // TODO: 實際的健康係數計算
        throw BlockchainException.UnsupportedOperationException(
            chainType,
            "Compound health factor calculation - implementation pending"
        )
    }
    
    override suspend fun calculateBorrowCapacity(
        userAddress: String,
        chainType: MultiChainType
    ): BorrowCapacity {
        validateChainSupport(chainType)
        
        // TODO: 實際的借貸能力計算
        throw BlockchainException.UnsupportedOperationException(
            chainType,
            "Compound borrow capacity calculation - implementation pending"
        )
    }
    
    override suspend fun getLiquidationInfo(
        userAddress: String,
        chainType: MultiChainType
    ): LiquidationInfo {
        validateChainSupport(chainType)
        
        // TODO: 實際的清算資訊查詢
        throw BlockchainException.UnsupportedOperationException(
            chainType,
            "Compound liquidation info - implementation pending"
        )
    }
    
    private fun validateChainSupport(chainType: MultiChainType) {
        if (chainType !in supportedChains) {
            throw BlockchainException.UnsupportedOperationException(
                chainType,
                "$protocolName does not support ${chainType.fullName}"
            )
        }
    }
}

/**
 * Aave 協定實現
 */
class AaveProtocol(
    private val logger: Logger = Logger.withTag("AaveProtocol")
) : LendingProtocol {
    
    override val protocolName = "Aave"
    
    override val supportedChains = listOf(
        MultiChainType.ETHEREUM
        // TODO: 添加 Polygon, Avalanche 等
    )
    
    override suspend fun getAvailableMarkets(
        chainType: MultiChainType,
        category: MarketCategory
    ): List<LendingMarket> {
        validateChainSupport(chainType)
        
        logger.d("Getting Aave markets for ${chainType.symbol}")
        
        return try {
            // TODO: 實際的 Aave API 調用
            // const aave = new AaveProtocolDataProvider()
            // const markets = await aave.getAllReservesTokens()
            
            // 暫時的模擬市場列表
            emptyList()
        } catch (e: Exception) {
            logger.e("Failed to get Aave markets", e)
            throw BlockchainException.ApiException(
                chainType,
                "aave markets",
                null,
                "Failed to get markets: ${e.message}",
                e
            )
        }
    }
    
    override suspend fun getMarketDetails(
        marketAddress: String,
        chainType: MultiChainType
    ): LendingMarketDetails {
        validateChainSupport(chainType)
        
        // TODO: 實際的市場詳細資訊查詢
        throw BlockchainException.UnsupportedOperationException(
            chainType,
            "Aave market details - implementation pending"
        )
    }
    
    override suspend fun getUserLendingPosition(
        userAddress: String,
        chainType: MultiChainType
    ): UserLendingPosition {
        validateChainSupport(chainType)
        
        // TODO: 實際的用戶借貸持倉查詢
        throw BlockchainException.UnsupportedOperationException(
            chainType,
            "Aave user position - implementation pending"
        )
    }
    
    override suspend fun createSupplyTransaction(request: SupplyRequest): String {
        validateChainSupport(request.chainType)
        
        // TODO: 實際的存款交易建構
        throw BlockchainException.UnsupportedOperationException(
            request.chainType,
            "Aave supply transaction - implementation pending"
        )
    }
    
    override suspend fun createWithdrawTransaction(request: WithdrawRequest): String {
        validateChainSupport(request.chainType)
        
        // TODO: 實際的提款交易建構
        throw BlockchainException.UnsupportedOperationException(
            request.chainType,
            "Aave withdraw transaction - implementation pending"
        )
    }
    
    override suspend fun createBorrowTransaction(request: BorrowRequest): String {
        validateChainSupport(request.chainType)
        
        // TODO: 實際的借款交易建構
        throw BlockchainException.UnsupportedOperationException(
            request.chainType,
            "Aave borrow transaction - implementation pending"
        )
    }
    
    override suspend fun createRepayTransaction(request: RepayRequest): String {
        validateChainSupport(request.chainType)
        
        // TODO: 實際的還款交易建構
        throw BlockchainException.UnsupportedOperationException(
            request.chainType,
            "Aave repay transaction - implementation pending"
        )
    }
    
    override suspend fun calculateHealthFactor(
        userAddress: String,
        chainType: MultiChainType
    ): HealthFactor {
        validateChainSupport(chainType)
        
        // TODO: 實際的健康係數計算
        throw BlockchainException.UnsupportedOperationException(
            chainType,
            "Aave health factor calculation - implementation pending"
        )
    }
    
    override suspend fun calculateBorrowCapacity(
        userAddress: String,
        chainType: MultiChainType
    ): BorrowCapacity {
        validateChainSupport(chainType)
        
        // TODO: 實際的借貸能力計算
        throw BlockchainException.UnsupportedOperationException(
            chainType,
            "Aave borrow capacity calculation - implementation pending"
        )
    }
    
    override suspend fun getLiquidationInfo(
        userAddress: String,
        chainType: MultiChainType
    ): LiquidationInfo {
        validateChainSupport(chainType)
        
        // TODO: 實際的清算資訊查詢
        throw BlockchainException.UnsupportedOperationException(
            chainType,
            "Aave liquidation info - implementation pending"
        )
    }
    
    private fun validateChainSupport(chainType: MultiChainType) {
        if (chainType !in supportedChains) {
            throw BlockchainException.UnsupportedOperationException(
                chainType,
                "$protocolName does not support ${chainType.fullName}"
            )
        }
    }
}

/**
 * 借貸協定聚合器
 * 整合多個借貸協定，提供最佳利率比較和風險管理
 */
class LendingProtocolAggregator(
    private val protocols: List<LendingProtocol>,
    private val logger: Logger = Logger.withTag("LendingProtocolAggregator")
) {
    
    /**
     * 取得最佳存款利率
     */
    suspend fun getBestSupplyRates(
        chainType: MultiChainType,
        tokenSymbols: List<String> = emptyList()
    ): List<MarketRateComparison> {
        logger.i("Getting best supply rates across ${protocols.size} protocols")
        
        val supportedProtocols = protocols.filter { 
            chainType in it.supportedChains 
        }
        
        val rateComparisons = mutableListOf<MarketRateComparison>()
        
        supportedProtocols.forEach { protocol ->
            try {
                val markets = protocol.getAvailableMarkets(chainType)
                val filteredMarkets = if (tokenSymbols.isNotEmpty()) {
                    markets.filter { it.token.symbol in tokenSymbols }
                } else markets
                
                filteredMarkets.forEach { market ->
                    rateComparisons.add(
                        MarketRateComparison(
                            protocolName = protocol.protocolName,
                            market = market,
                            rateAdvantage = 0.0 // 將在後續計算
                        )
                    )
                }
            } catch (e: Exception) {
                logger.w("Failed to get markets from ${protocol.protocolName}", e)
            }
        }
        
        // 按代幣分組並計算利率優勢
        return rateComparisons
            .groupBy { it.market.token.symbol }
            .flatMap { (_, markets) ->
                val avgSupplyRate = markets.map { it.market.supplyApr }.average()
                markets.map { comparison ->
                    comparison.copy(rateAdvantage = comparison.market.supplyApr - avgSupplyRate)
                }
            }
            .sortedByDescending { it.market.supplyApr }
    }
    
    /**
     * 取得最佳借款利率
     */
    suspend fun getBestBorrowRates(
        chainType: MultiChainType,
        tokenSymbols: List<String> = emptyList()
    ): List<MarketRateComparison> {
        logger.i("Getting best borrow rates")
        
        val supportedProtocols = protocols.filter { 
            chainType in it.supportedChains 
        }
        
        val rateComparisons = mutableListOf<MarketRateComparison>()
        
        supportedProtocols.forEach { protocol ->
            try {
                val markets = protocol.getAvailableMarkets(chainType)
                val filteredMarkets = if (tokenSymbols.isNotEmpty()) {
                    markets.filter { it.token.symbol in tokenSymbols }
                } else markets
                
                filteredMarkets.filter { it.isBorrowEnabled }.forEach { market ->
                    rateComparisons.add(
                        MarketRateComparison(
                            protocolName = protocol.protocolName,
                            market = market,
                            rateAdvantage = 0.0
                        )
                    )
                }
            } catch (e: Exception) {
                logger.w("Failed to get markets from ${protocol.protocolName}", e)
            }
        }
        
        // 按代幣分組並計算利率優勢（借款利率越低越好）
        return rateComparisons
            .groupBy { it.market.token.symbol }
            .flatMap { (_, markets) ->
                val avgBorrowRate = markets.map { it.market.borrowApr }.average()
                markets.map { comparison ->
                    comparison.copy(rateAdvantage = avgBorrowRate - comparison.market.borrowApr)
                }
            }
            .sortedBy { it.market.borrowApr }
    }
    
    /**
     * 取得用戶跨協定持倉總覽
     */
    suspend fun getUserAggregatedPosition(
        userAddress: String,
        chainType: MultiChainType
    ): AggregatedLendingPosition {
        logger.d("Getting aggregated lending position for user $userAddress")
        
        val supportedProtocols = protocols.filter { 
            chainType in it.supportedChains 
        }
        
        val protocolPositions = mutableMapOf<String, UserLendingPosition>()
        val allSupplied = mutableListOf<SuppliedAsset>()
        val allBorrowed = mutableListOf<BorrowedAsset>()
        var totalSuppliedValue = 0.0
        var totalBorrowedValue = 0.0
        var totalEarnedInterest = 0.0
        var totalPaidInterest = 0.0
        
        supportedProtocols.forEach { protocol ->
            try {
                val position = protocol.getUserLendingPosition(userAddress, chainType)
                protocolPositions[protocol.protocolName] = position
                
                allSupplied.addAll(position.suppliedAssets)
                allBorrowed.addAll(position.borrowedAssets)
                
                totalSuppliedValue += position.totalSuppliedValue.toDoubleOrNull() ?: 0.0
                totalBorrowedValue += position.totalBorrowedValue.toDoubleOrNull() ?: 0.0
                totalEarnedInterest += position.earnedInterest.toDoubleOrNull() ?: 0.0
                totalPaidInterest += position.paidInterest.toDoubleOrNull() ?: 0.0
            } catch (e: Exception) {
                logger.w("Failed to get user position from ${protocol.protocolName}", e)
            }
        }
        
        // 計算總體健康係數
        val overallHealthFactor = calculateOverallHealthFactor(protocolPositions.values.toList())
        
        return AggregatedLendingPosition(
            userAddress = userAddress,
            chainType = chainType,
            totalSuppliedValue = totalSuppliedValue.toString(),
            totalBorrowedValue = totalBorrowedValue.toString(),
            netWorth = (totalSuppliedValue - totalBorrowedValue).toString(),
            overallHealthFactor = overallHealthFactor,
            protocolPositions = protocolPositions,
            allSuppliedAssets = allSupplied,
            allBorrowedAssets = allBorrowed,
            totalEarnedInterest = totalEarnedInterest.toString(),
            totalPaidInterest = totalPaidInterest.toString(),
            summary = LendingPositionSummary(
                activeProtocols = protocolPositions.size,
                totalMarkets = allSupplied.size + allBorrowed.size,
                averageSupplyApr = if (allSupplied.isNotEmpty()) {
                    allSupplied.map { it.market.supplyApr }.average()
                } else 0.0,
                averageBorrowApr = if (allBorrowed.isNotEmpty()) {
                    allBorrowed.map { it.market.borrowApr }.average()
                } else 0.0,
                liquidationRisk = calculateOverallLiquidationRisk(protocolPositions.values.toList())
            )
        )
    }
    
    // 私有輔助方法
    
    private fun calculateOverallHealthFactor(positions: List<UserLendingPosition>): HealthFactor {
        if (positions.isEmpty()) {
            return HealthFactor(
                value = Double.POSITIVE_INFINITY,
                status = HealthStatus.HEALTHY,
                liquidationThreshold = 0.0,
                safetyMargin = "0",
                riskLevel = RiskLevel.LOW
            )
        }
        
        val healthFactors = positions.map { it.healthFactor.value }
        val minHealthFactor = healthFactors.minOrNull() ?: Double.POSITIVE_INFINITY
        
        val status = when {
            minHealthFactor >= 2.0 -> HealthStatus.HEALTHY
            minHealthFactor >= 1.5 -> HealthStatus.WARNING
            minHealthFactor >= 1.1 -> HealthStatus.DANGER
            else -> HealthStatus.LIQUIDATABLE
        }
        
        return HealthFactor(
            value = minHealthFactor,
            status = status,
            liquidationThreshold = positions.map { it.healthFactor.liquidationThreshold }.average(),
            safetyMargin = "0", // 需要詳細計算
            riskLevel = when (status) {
                HealthStatus.HEALTHY -> RiskLevel.LOW
                HealthStatus.WARNING -> RiskLevel.MEDIUM
                HealthStatus.DANGER -> RiskLevel.HIGH
                HealthStatus.LIQUIDATABLE -> RiskLevel.CRITICAL
            }
        )
    }
    
    private fun calculateOverallLiquidationRisk(positions: List<UserLendingPosition>): LiquidationRisk {
        val riskLevels = positions.map { it.liquidationRisk }
        return riskLevels.maxByOrNull { it.ordinal } ?: LiquidationRisk.NONE
    }
}

/**
 * 市場利率比較
 */
data class MarketRateComparison(
    val protocolName: String,
    val market: LendingMarket,
    val rateAdvantage: Double // 相對於平均利率的優勢
)

/**
 * 聚合借貸持倉
 */
data class AggregatedLendingPosition(
    val userAddress: String,
    val chainType: MultiChainType,
    val totalSuppliedValue: String,
    val totalBorrowedValue: String,
    val netWorth: String,
    val overallHealthFactor: HealthFactor,
    val protocolPositions: Map<String, UserLendingPosition>,
    val allSuppliedAssets: List<SuppliedAsset>,
    val allBorrowedAssets: List<BorrowedAsset>,
    val totalEarnedInterest: String,
    val totalPaidInterest: String,
    val summary: LendingPositionSummary
)

/**
 * 借貸持倉摘要
 */
data class LendingPositionSummary(
    val activeProtocols: Int,
    val totalMarkets: Int,
    val averageSupplyApr: Double,
    val averageBorrowApr: Double,
    val liquidationRisk: LiquidationRisk
)

/**
 * 借貸協定聚合器工廠
 */
object LendingProtocolAggregatorFactory {
    
    /**
     * 創建預設的借貸協定聚合器
     */
    fun createDefaultAggregator(): LendingProtocolAggregator {
        val protocols = listOf(
            CompoundProtocol(),
            AaveProtocol()
            // 未來可添加更多協定：
            // MakerDAOProtocol(),
            // VenusProtocol(),
            // JustLendProtocol() (for TRON)
        )
        
        return LendingProtocolAggregator(protocols)
    }
    
    /**
     * 創建特定鏈的借貸協定聚合器
     */
    fun createChainSpecificAggregator(chainType: MultiChainType): LendingProtocolAggregator {
        val protocols = when (chainType) {
            MultiChainType.ETHEREUM -> listOf(
                CompoundProtocol(),
                AaveProtocol()
            )
            MultiChainType.TRON -> listOf(
                // JustLendProtocol()
            )
            else -> emptyList()
        }
        
        return LendingProtocolAggregator(protocols)
    }
}
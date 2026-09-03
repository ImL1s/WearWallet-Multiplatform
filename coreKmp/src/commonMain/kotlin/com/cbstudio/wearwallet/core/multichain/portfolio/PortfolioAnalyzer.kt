package com.cbstudio.wearwallet.core.multichain.portfolio

import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.core.multichain.model.BlockchainException
import co.touchlab.kermit.Logger

/**
 * 投資組合分析器
 * 提供跨鏈投資組合的深度分析和風險管理功能
 */
interface PortfolioAnalyzer {
    
    /**
     * 分析器名稱
     */
    val analyzerName: String
    
    /**
     * 支援的區塊鏈列表
     */
    val supportedChains: List<MultiChainType>
    
    /**
     * 分析投資組合整體表現
     */
    suspend fun analyzePortfolioPerformance(
        portfolioData: PortfolioData
    ): PortfolioPerformanceAnalysis
    
    /**
     * 計算風險指標
     */
    suspend fun calculateRiskMetrics(
        portfolioData: PortfolioData,
        timeframe: AnalysisTimeframe = AnalysisTimeframe.DAYS_30
    ): RiskMetrics
    
    /**
     * 生成資產配置建議
     */
    suspend fun generateAllocationSuggestions(
        portfolioData: PortfolioData,
        investmentProfile: InvestmentProfile
    ): AllocationSuggestions
    
    /**
     * 執行壓力測試
     */
    suspend fun performStressTest(
        portfolioData: PortfolioData,
        scenarios: List<StressTestScenario>
    ): StressTestResults
    
    /**
     * 計算多元化指數
     */
    suspend fun calculateDiversificationIndex(
        portfolioData: PortfolioData
    ): DiversificationAnalysis
    
    /**
     * 生成再平衡建議
     */
    suspend fun generateRebalancingAdvice(
        portfolioData: PortfolioData,
        targetAllocation: Map<String, Double>
    ): RebalancingAdvice
}

/**
 * 投資組合數據
 */
data class PortfolioData(
    val walletAddress: String,
    val totalValue: String,                    // 總價值 USD
    val assets: List<PortfolioAsset>,
    val defiPositions: List<DeFiPosition>,
    val nftCollections: List<NFTCollection>,
    val lastUpdated: Long,
    val historicalData: PortfolioHistoricalData?
)

/**
 * 投資組合資產
 */
data class PortfolioAsset(
    val chainType: MultiChainType,
    val contractAddress: String?,
    val symbol: String,
    val name: String,
    val balance: String,
    val usdValue: String,
    val price: String,
    val priceChange24h: Double,
    val priceChange7d: Double,
    val priceChange30d: Double,
    val weight: Double,                        // 在投資組合中的權重
    val averageCost: String?,                  // 平均成本
    val unrealizedPnL: String?,                // 未實現損益
    val unrealizedPnLPercentage: Double?,      // 未實現損益百分比
    val volatility30d: Double,                 // 30天波動率
    val liquidityScore: Double,                // 流動性評分 (0-100)
    val marketCap: String?,                    // 市值
    val volume24h: String?                     // 24小時交易量
)

/**
 * DeFi 持倉
 */
data class DeFiPosition(
    val protocol: String,
    val chainType: MultiChainType,
    val positionType: DeFiPositionType,
    val assets: List<PortfolioAsset>,
    val totalValue: String,
    val apr: Double?,                          // 年化收益率
    val healthFactor: Double?,                 // 健康係數（借貸）
    val impermanentLoss: String?,              // 無常損失（LP）
    val claimableRewards: String?              // 可領取獎勵
)

/**
 * DeFi 持倉類型
 */
enum class DeFiPositionType {
    LENDING,                                   // 借貸
    LIQUIDITY_PROVIDING,                       // 流動性提供
    STAKING,                                   // 質押
    FARMING,                                   // 收益農場
    BORROWING                                  // 借款
}

/**
 * NFT 收藏
 */
data class NFTCollection(
    val chainType: MultiChainType,
    val contractAddress: String,
    val collectionName: String,
    val itemCount: Int,
    val floorPrice: String?,
    val totalValue: String,
    val averageCost: String?,
    val unrealizedPnL: String?
)

/**
 * 投資組合歷史數據
 */
data class PortfolioHistoricalData(
    val dailyValues: List<PortfolioValuePoint>,
    val returns: PortfolioReturns,
    val maxDrawdown: Double,                   // 最大回撤
    val sharpeRatio: Double,                   // 夏普比率
    val sortinoRatio: Double,                  // 索提諾比率
    val beta: Double?                          // 相對於市場的貝塔值
)

/**
 * 投資組合價值點
 */
data class PortfolioValuePoint(
    val timestamp: Long,
    val totalValue: String,
    val dailyReturn: Double
)

/**
 * 投資組合回報
 */
data class PortfolioReturns(
    val daily: Double,
    val weekly: Double,
    val monthly: Double,
    val quarterly: Double,
    val yearly: Double,
    val allTime: Double,
    val annualized: Double                     // 年化回報率
)

/**
 * 投資組合表現分析
 */
data class PortfolioPerformanceAnalysis(
    val portfolioData: PortfolioData,
    val performance: PerformanceMetrics,
    val riskAdjustedReturns: RiskAdjustedMetrics,
    val assetPerformance: List<AssetPerformance>,
    val sectorAnalysis: SectorAnalysis,
    val benchmarkComparison: BenchmarkComparison,
    val strengths: List<String>,
    val weaknesses: List<String>,
    val recommendations: List<String>
)

/**
 * 表現指標
 */
data class PerformanceMetrics(
    val totalReturn: Double,                   // 總回報率
    val annualizedReturn: Double,              // 年化回報率
    val volatility: Double,                    // 波動率
    val maxDrawdown: Double,                   // 最大回撤
    val winRate: Double,                       // 勝率
    val profitFactor: Double,                  // 盈利因子
    val calmarRatio: Double,                   // 卡瑪比率
    val informationRatio: Double               // 資訊比率
)

/**
 * 風險調整指標
 */
data class RiskAdjustedMetrics(
    val sharpeRatio: Double,                   // 夏普比率
    val sortinoRatio: Double,                  // 索提諾比率
    val treynorRatio: Double,                  // 崔納比率
    val jensenAlpha: Double,                   // 詹森阿爾法
    val beta: Double,                          // 貝塔值
    val rSquared: Double,                      // R 平方
    val trackingError: Double                  // 追蹤誤差
)

/**
 * 資產表現
 */
data class AssetPerformance(
    val asset: PortfolioAsset,
    val returns: PortfolioReturns,
    val volatility: Double,
    val sharpeRatio: Double,
    val maxDrawdown: Double,
    val contribution: Double,                  // 對投資組合回報的貢獻
    val correlationWithPortfolio: Double,      // 與投資組合的相關性
    val recommendation: AssetRecommendation
)

/**
 * 資產建議
 */
enum class AssetRecommendation {
    BUY,                                       // 買入
    HOLD,                                      // 持有
    REDUCE,                                    // 減倉
    SELL                                       // 賣出
}

/**
 * 板塊分析
 */
data class SectorAnalysis(
    val sectors: List<SectorAllocation>,
    val sectorPerformance: List<SectorPerformance>,
    val overweightSectors: List<String>,
    val underweightSectors: List<String>,
    val concentrationRisk: ConcentrationRisk
)

/**
 * 板塊配置
 */
data class SectorAllocation(
    val sector: String,
    val allocation: Double,                    // 配置比例
    val value: String,                         // 配置價值
    val targetAllocation: Double?              // 目標配置
)

/**
 * 板塊表現
 */
data class SectorPerformance(
    val sector: String,
    val returns: PortfolioReturns,
    val volatility: Double,
    val sharpeRatio: Double
)

/**
 * 集中度風險
 */
data class ConcentrationRisk(
    val herfindahlIndex: Double,               // 赫芬達爾指數
    val top3Concentration: Double,             // 前三大持倉集中度
    val top5Concentration: Double,             // 前五大持倉集中度
    val riskLevel: ConcentrationRiskLevel
)

/**
 * 集中度風險等級
 */
enum class ConcentrationRiskLevel {
    LOW,                                       // 低集中度
    MODERATE,                                  // 中等集中度
    HIGH,                                      // 高集中度
    EXTREME                                    // 極高集中度
}

/**
 * 基準比較
 */
data class BenchmarkComparison(
    val benchmarks: List<BenchmarkPerformance>,
    val outperformance: Double,                // 超額回報
    val informationRatio: Double,              // 資訊比率
    val trackingError: Double,                 // 追蹤誤差
    val upMarketCapture: Double,               // 上行市場捕獲率
    val downMarketCapture: Double              // 下行市場捕獲率
)

/**
 * 基準表現
 */
data class BenchmarkPerformance(
    val name: String,
    val returns: PortfolioReturns,
    val volatility: Double,
    val sharpeRatio: Double,
    val correlation: Double                    // 與投資組合的相關性
)

/**
 * 風險指標
 */
data class RiskMetrics(
    val portfolioVaR: ValueAtRisk,             // 投資組合 VaR
    val componentVaR: List<ComponentVaR>,      // 成分 VaR
    val stressTestResults: List<StressTestResult>,
    val correlationMatrix: CorrelationMatrix,
    val riskDecomposition: RiskDecomposition,
    val liquidityRisk: LiquidityRisk,
    val counterpartyRisk: CounterpartyRisk
)

/**
 * 風險價值 (VaR)
 */
data class ValueAtRisk(
    val confidenceLevel: Double,               // 置信水準
    val timeHorizon: Int,                      // 時間範圍（天）
    val varAmount: String,                     // VaR 金額
    val varPercentage: Double,                 // VaR 百分比
    val expectedShortfall: String              // 預期短缺
)

/**
 * 成分 VaR
 */
data class ComponentVaR(
    val asset: PortfolioAsset,
    val marginalVaR: String,                   // 邊際 VaR
    val componentVaR: String,                  // 成分 VaR
    val riskContribution: Double               // 風險貢獻度
)

/**
 * 壓力測試結果
 */
data class StressTestResult(
    val scenario: StressTestScenario,
    val portfolioImpact: String,               // 投資組合影響
    val impactPercentage: Double,              // 影響百分比
    val worstAssets: List<AssetImpact>,        // 受影響最大的資產
    val recoveryTime: Int?                     // 預估恢復時間（天）
)

/**
 * 壓力測試情境
 */
data class StressTestScenario(
    val name: String,
    val description: String,
    val priceShocks: Map<String, Double>,      // 價格衝擊
    val marketConditions: MarketConditions
)

/**
 * 市場條件
 */
data class MarketConditions(
    val volatilityMultiplier: Double,          // 波動率乘數
    val correlationShift: Double,              // 相關性變化
    val liquidityImpact: Double                // 流動性影響
)

/**
 * 資產影響
 */
data class AssetImpact(
    val asset: PortfolioAsset,
    val priceImpact: Double,                   // 價格影響
    val valueImpact: String                    // 價值影響
)

/**
 * 相關性矩陣
 */
data class CorrelationMatrix(
    val assets: List<String>,
    val correlations: Array<DoubleArray>,      // N x N 相關性矩陣
    val averageCorrelation: Double,
    val maxCorrelation: Double,
    val minCorrelation: Double
) {
    /**
     * 取得兩個資產之間的相關性
     */
    fun getCorrelation(asset1: String, asset2: String): Double? {
        val index1 = assets.indexOf(asset1)
        val index2 = assets.indexOf(asset2)
        
        return if (index1 != -1 && index2 != -1) {
            correlations[index1][index2]
        } else null
    }
}

/**
 * 風險分解
 */
data class RiskDecomposition(
    val totalRisk: Double,
    val systematicRisk: Double,                // 系統性風險
    val idiosyncraticRisk: Double,             // 特有風險
    val diversificationRatio: Double,          // 多元化比率
    val concentrationRisk: Double,             // 集中度風險
    val riskSources: List<RiskSource>
)

/**
 * 風險來源
 */
data class RiskSource(
    val source: String,
    val contribution: Double,                  // 風險貢獻
    val description: String
)

/**
 * 流動性風險
 */
data class LiquidityRisk(
    val portfolioLiquidityScore: Double,       // 投資組合流動性評分
    val liquidityBuckets: List<LiquidityBucket>,
    val illiquidAssets: List<PortfolioAsset>,
    val liquidationImpact: String              // 清算影響
)

/**
 * 流動性分桶
 */
data class LiquidityBucket(
    val bucket: LiquidityLevel,
    val allocation: Double,                    // 配置比例
    val value: String,                         // 配置價值
    val assets: List<PortfolioAsset>
)

/**
 * 流動性等級
 */
enum class LiquidityLevel {
    HIGH,                                      // 高流動性
    MEDIUM,                                    // 中流動性
    LOW,                                       // 低流動性
    ILLIQUID                                   // 非流動性
}

/**
 * 交易對手風險
 */
data class CounterpartyRisk(
    val exposures: List<CounterpartyExposure>,
    val concentrationRisk: Double,
    val creditRisk: Double,
    val operationalRisk: Double
)

/**
 * 交易對手敞口
 */
data class CounterpartyExposure(
    val counterparty: String,
    val exposure: String,                      // 敞口金額
    val riskRating: String,                    // 風險評級
    val products: List<String>                 // 產品類型
)

/**
 * 投資者風險偏好檔案
 */
data class InvestmentProfile(
    val riskTolerance: RiskTolerance,
    val investmentHorizon: InvestmentHorizon,
    val liquidityNeeds: LiquidityNeeds,
    val investmentObjectives: List<InvestmentObjective>,
    val constraints: List<InvestmentConstraint>
)

/**
 * 風險承受度
 */
enum class RiskTolerance {
    CONSERVATIVE,                              // 保守型
    MODERATE,                                  // 穩健型
    AGGRESSIVE,                                // 進取型
    SPECULATIVE                                // 投機型
}

/**
 * 投資期限
 */
enum class InvestmentHorizon {
    SHORT_TERM,                                // 短期 (<1年)
    MEDIUM_TERM,                               // 中期 (1-5年)
    LONG_TERM                                  // 長期 (>5年)
}

/**
 * 流動性需求
 */
enum class LiquidityNeeds {
    HIGH,                                      // 高流動性需求
    MODERATE,                                  // 中等流動性需求
    LOW                                        // 低流動性需求
}

/**
 * 投資目標
 */
enum class InvestmentObjective {
    CAPITAL_PRESERVATION,                      // 資本保值
    INCOME_GENERATION,                         // 收入產生
    CAPITAL_GROWTH,                            // 資本增值
    SPECULATION                                // 投機
}

/**
 * 投資約束
 */
data class InvestmentConstraint(
    val type: ConstraintType,
    val description: String,
    val value: String?
)

/**
 * 約束類型
 */
enum class ConstraintType {
    SECTOR_LIMIT,                              // 板塊限制
    SINGLE_ASSET_LIMIT,                        // 單一資產限制
    GEOGRAPHIC_LIMIT,                          // 地理限制
    ESG_REQUIREMENT,                           // ESG 要求
    LIQUIDITY_REQUIREMENT                      // 流動性要求
}

/**
 * 配置建議
 */
data class AllocationSuggestions(
    val currentAllocation: Map<String, Double>,
    val recommendedAllocation: Map<String, Double>,
    val rebalancingActions: List<RebalancingAction>,
    val expectedImprovement: ExpectedImprovement,
    val risks: List<String>,
    val rationale: String
)

/**
 * 再平衡操作
 */
data class RebalancingAction(
    val action: ActionType,
    val asset: String,
    val currentWeight: Double,
    val targetWeight: Double,
    val amount: String,
    val priority: Priority
)

/**
 * 操作類型
 */
enum class ActionType {
    BUY,                                       // 買入
    SELL,                                      // 賣出
    HOLD                                       // 持有
}

/**
 * 優先級
 */
enum class Priority {
    HIGH,                                      // 高優先級
    MEDIUM,                                    // 中優先級
    LOW                                        // 低優先級
}

/**
 * 預期改善
 */
data class ExpectedImprovement(
    val returnImprovement: Double,             // 回報改善
    val riskReduction: Double,                 // 風險降低
    val sharpeImprovement: Double,             // 夏普比率改善
    val diversificationImprovement: Double      // 多元化改善
)

/**
 * 再平衡建議
 */
data class RebalancingAdvice(
    val currentAllocation: Map<String, Double>,
    val targetAllocation: Map<String, Double>,
    val deviations: Map<String, Double>,       // 偏離度
    val rebalancingActions: List<RebalancingAction>,
    val estimatedCosts: RebalancingCosts,
    val optimalTiming: RebalancingTiming,
    val taxImplications: TaxImplications?
)

/**
 * 再平衡成本
 */
data class RebalancingCosts(
    val tradingFees: String,
    val gassfees: String,
    val slippageCosts: String,
    val totalCosts: String,
    val costBenefitRatio: Double
)

/**
 * 再平衡時機
 */
data class RebalancingTiming(
    val urgency: RebalancingUrgency,
    val optimalTimeframe: String,
    val marketConditions: String,
    val recommendations: List<String>
)

/**
 * 再平衡緊急程度
 */
enum class RebalancingUrgency {
    IMMEDIATE,                                 // 立即
    SOON,                                      // 盡快
    MODERATE,                                  // 中等
    LOW                                        // 低
}

/**
 * 稅務影響
 */
data class TaxImplications(
    val shortTermGains: String,
    val longTermGains: String,
    val taxLiability: String,
    val taxOptimizedActions: List<RebalancingAction>
)

/**
 * 多元化分析
 */
data class DiversificationAnalysis(
    val overallScore: Double,                  // 總體多元化評分
    val assetDiversification: Double,          // 資產多元化
    val geographicDiversification: Double,     // 地理多元化
    val sectorDiversification: Double,         // 板塊多元化
    val correlationDiversification: Double,    // 相關性多元化
    val recommendations: List<DiversificationRecommendation>
)

/**
 * 多元化建議
 */
data class DiversificationRecommendation(
    val category: DiversificationCategory,
    val currentScore: Double,
    val targetScore: Double,
    val suggestedActions: List<String>
)

/**
 * 多元化類別
 */
enum class DiversificationCategory {
    ASSET_CLASS,                               // 資產類別
    GEOGRAPHIC,                                // 地理
    SECTOR,                                    // 板塊
    MARKET_CAP,                                // 市值
    CORRELATION                                // 相關性
}

/**
 * 分析時間範圍
 */
enum class AnalysisTimeframe {
    DAYS_7,                                    // 7天
    DAYS_30,                                   // 30天
    DAYS_90,                                   // 90天
    DAYS_180,                                  // 180天
    YEAR_1,                                    // 1年
    YEAR_2,                                    // 2年
    ALL_TIME                                   // 全時間
}

/**
 * 壓力測試結果集合
 */
data class StressTestResults(
    val scenarios: List<StressTestResult>,
    val worstCaseScenario: StressTestResult,
    val averageImpact: Double,
    val resilenceScore: Double,                // 韌性評分
    val recommendations: List<String>
)

/**
 * 進階投資組合分析器實現
 */
class AdvancedPortfolioAnalyzer(
    private val logger: Logger = Logger.withTag("AdvancedPortfolioAnalyzer")
) : PortfolioAnalyzer {
    
    override val analyzerName = "Advanced Portfolio Analyzer"
    
    override val supportedChains = MultiChainType.getAllChains()
    
    override suspend fun analyzePortfolioPerformance(
        portfolioData: PortfolioData
    ): PortfolioPerformanceAnalysis {
        logger.i("Analyzing portfolio performance for ${portfolioData.walletAddress}")
        
        return try {
            // TODO: 實際的投資組合表現分析
            // 1. 計算各種表現指標
            // 2. 分析風險調整回報
            // 3. 評估資產表現
            // 4. 執行板塊分析
            // 5. 與基準比較
            
            val performanceMetrics = calculatePerformanceMetrics(portfolioData)
            val riskAdjustedMetrics = calculateRiskAdjustedMetrics(portfolioData)
            val assetPerformance = analyzeAssetPerformance(portfolioData)
            val sectorAnalysis = analyzeSectors(portfolioData)
            val benchmarkComparison = compareToBenchmarks(portfolioData)
            
            PortfolioPerformanceAnalysis(
                portfolioData = portfolioData,
                performance = performanceMetrics,
                riskAdjustedReturns = riskAdjustedMetrics,
                assetPerformance = assetPerformance,
                sectorAnalysis = sectorAnalysis,
                benchmarkComparison = benchmarkComparison,
                strengths = generateStrengths(portfolioData),
                weaknesses = generateWeaknesses(portfolioData),
                recommendations = generateRecommendations(portfolioData)
            )
        } catch (e: Exception) {
            logger.e("Failed to analyze portfolio performance", e)
            throw BlockchainException.GenericException(
                MultiChainType.ETHEREUM, // 預設
                "Failed to analyze portfolio: ${e.message}",
                e
            )
        }
    }
    
    override suspend fun calculateRiskMetrics(
        portfolioData: PortfolioData,
        timeframe: AnalysisTimeframe
    ): RiskMetrics {
        logger.d("Calculating risk metrics for timeframe: $timeframe")
        
        // TODO: 實際的風險指標計算
        throw BlockchainException.UnsupportedOperationException(
            MultiChainType.ETHEREUM,
            "Risk metrics calculation - implementation pending"
        )
    }
    
    override suspend fun generateAllocationSuggestions(
        portfolioData: PortfolioData,
        investmentProfile: InvestmentProfile
    ): AllocationSuggestions {
        logger.d("Generating allocation suggestions for ${investmentProfile.riskTolerance} investor")
        
        // TODO: 實際的配置建議生成
        throw BlockchainException.UnsupportedOperationException(
            MultiChainType.ETHEREUM,
            "Allocation suggestions - implementation pending"
        )
    }
    
    override suspend fun performStressTest(
        portfolioData: PortfolioData,
        scenarios: List<StressTestScenario>
    ): StressTestResults {
        logger.d("Performing stress test with ${scenarios.size} scenarios")
        
        // TODO: 實際的壓力測試
        throw BlockchainException.UnsupportedOperationException(
            MultiChainType.ETHEREUM,
            "Stress testing - implementation pending"
        )
    }
    
    override suspend fun calculateDiversificationIndex(
        portfolioData: PortfolioData
    ): DiversificationAnalysis {
        logger.d("Calculating diversification index")
        
        // TODO: 實際的多元化分析
        throw BlockchainException.UnsupportedOperationException(
            MultiChainType.ETHEREUM,
            "Diversification analysis - implementation pending"
        )
    }
    
    override suspend fun generateRebalancingAdvice(
        portfolioData: PortfolioData,
        targetAllocation: Map<String, Double>
    ): RebalancingAdvice {
        logger.d("Generating rebalancing advice")
        
        // TODO: 實際的再平衡建議
        throw BlockchainException.UnsupportedOperationException(
            MultiChainType.ETHEREUM,
            "Rebalancing advice - implementation pending"
        )
    }
    
    // 私有輔助方法 - 暫時的模擬實現
    
    private fun calculatePerformanceMetrics(portfolioData: PortfolioData): PerformanceMetrics {
        // 暫時的模擬數據
        return PerformanceMetrics(
            totalReturn = 15.2,
            annualizedReturn = 12.8,
            volatility = 18.5,
            maxDrawdown = -8.3,
            winRate = 0.65,
            profitFactor = 1.8,
            calmarRatio = 1.54,
            informationRatio = 0.75
        )
    }
    
    private fun calculateRiskAdjustedMetrics(portfolioData: PortfolioData): RiskAdjustedMetrics {
        // 暫時的模擬數據
        return RiskAdjustedMetrics(
            sharpeRatio = 0.69,
            sortinoRatio = 0.92,
            treynorRatio = 0.128,
            jensenAlpha = 0.032,
            beta = 1.15,
            rSquared = 0.78,
            trackingError = 0.045
        )
    }
    
    private fun analyzeAssetPerformance(portfolioData: PortfolioData): List<AssetPerformance> {
        // 暫時回傳空列表
        return emptyList()
    }
    
    private fun analyzeSectors(portfolioData: PortfolioData): SectorAnalysis {
        // 暫時的模擬數據
        return SectorAnalysis(
            sectors = emptyList(),
            sectorPerformance = emptyList(),
            overweightSectors = emptyList(),
            underweightSectors = emptyList(),
            concentrationRisk = ConcentrationRisk(
                herfindahlIndex = 0.25,
                top3Concentration = 0.45,
                top5Concentration = 0.67,
                riskLevel = ConcentrationRiskLevel.MODERATE
            )
        )
    }
    
    private fun compareToBenchmarks(portfolioData: PortfolioData): BenchmarkComparison {
        // 暫時的模擬數據
        return BenchmarkComparison(
            benchmarks = emptyList(),
            outperformance = 3.2,
            informationRatio = 0.75,
            trackingError = 0.045,
            upMarketCapture = 1.12,
            downMarketCapture = 0.88
        )
    }
    
    private fun generateStrengths(portfolioData: PortfolioData): List<String> {
        return listOf(
            "良好的多鏈分散",
            "優秀的風險調整回報",
            "適度的流動性配置"
        )
    }
    
    private fun generateWeaknesses(portfolioData: PortfolioData): List<String> {
        return listOf(
            "部分資產集中度偏高",
            "DeFi 持倉風險需要關注"
        )
    }
    
    private fun generateRecommendations(portfolioData: PortfolioData): List<String> {
        return listOf(
            "考慮增加穩定幣配置以降低波動性",
            "定期檢查 DeFi 持倉的健康係數",
            "建議設置止損策略"
        )
    }
}

/**
 * 投資組合分析器工廠
 */
object PortfolioAnalyzerFactory {
    
    /**
     * 創建進階投資組合分析器
     */
    fun createAdvancedAnalyzer(): PortfolioAnalyzer {
        return AdvancedPortfolioAnalyzer()
    }
    
    /**
     * 創建預設的壓力測試情境
     */
    fun createDefaultStressTestScenarios(): List<StressTestScenario> {
        return listOf(
            StressTestScenario(
                name = "市場崩盤",
                description = "類似 2008 年或 2020 年的市場恐慌",
                priceShocks = mapOf(
                    "BTC" to -0.5,
                    "ETH" to -0.6,
                    "STOCKS" to -0.4
                ),
                marketConditions = MarketConditions(
                    volatilityMultiplier = 3.0,
                    correlationShift = 0.3,
                    liquidityImpact = 0.5
                )
            ),
            StressTestScenario(
                name = "加密貨幣冬天",
                description = "長期的加密貨幣熊市",
                priceShocks = mapOf(
                    "BTC" to -0.8,
                    "ETH" to -0.85,
                    "ALTCOINS" to -0.9
                ),
                marketConditions = MarketConditions(
                    volatilityMultiplier = 2.0,
                    correlationShift = 0.2,
                    liquidityImpact = 0.7
                )
            ),
            StressTestScenario(
                name = "監管打擊",
                description = "嚴厲的全球監管措施",
                priceShocks = mapOf(
                    "CRYPTO" to -0.4,
                    "DEFI" to -0.6,
                    "NFT" to -0.7
                ),
                marketConditions = MarketConditions(
                    volatilityMultiplier = 2.5,
                    correlationShift = 0.4,
                    liquidityImpact = 0.8
                )
            )
        )
    }
    
    /**
     * 創建預設的投資者風險檔案
     */
    fun createDefaultInvestmentProfiles(): Map<String, InvestmentProfile> {
        return mapOf(
            "conservative" to InvestmentProfile(
                riskTolerance = RiskTolerance.CONSERVATIVE,
                investmentHorizon = InvestmentHorizon.LONG_TERM,
                liquidityNeeds = LiquidityNeeds.HIGH,
                investmentObjectives = listOf(
                    InvestmentObjective.CAPITAL_PRESERVATION,
                    InvestmentObjective.INCOME_GENERATION
                ),
                constraints = listOf(
                    InvestmentConstraint(
                        type = ConstraintType.SINGLE_ASSET_LIMIT,
                        description = "單一資產不得超過 10%",
                        value = "0.10"
                    )
                )
            ),
            "aggressive" to InvestmentProfile(
                riskTolerance = RiskTolerance.AGGRESSIVE,
                investmentHorizon = InvestmentHorizon.LONG_TERM,
                liquidityNeeds = LiquidityNeeds.LOW,
                investmentObjectives = listOf(
                    InvestmentObjective.CAPITAL_GROWTH
                ),
                constraints = listOf(
                    InvestmentConstraint(
                        type = ConstraintType.SINGLE_ASSET_LIMIT,
                        description = "單一資產不得超過 25%",
                        value = "0.25"
                    )
                )
            )
        )
    }
}

/**
 * 相關性分析
 */
data class CorrelationAnalysis(
    val averageCorrelation: Double,
    val maxCorrelation: Double,
    val minCorrelation: Double,
    val correlationMatrix: CorrelationMatrix,
    val significantCorrelations: List<CorrelationPair>
)

/**
 * 相關性配對
 */
data class CorrelationPair(
    val asset1: String,
    val asset2: String,
    val correlation: Double,
    val significance: CorrelationSignificance
)

/**
 * 相關性顯著程度
 */
enum class CorrelationSignificance {
    HIGH,      // |r| > 0.8
    MODERATE,  // 0.5 < |r| <= 0.8  
    LOW,       // 0.3 < |r| <= 0.5
    NEGLIGIBLE // |r| <= 0.3
}
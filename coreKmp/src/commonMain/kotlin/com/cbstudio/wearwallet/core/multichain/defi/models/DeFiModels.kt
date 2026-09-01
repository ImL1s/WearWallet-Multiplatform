package com.cbstudio.wearwallet.core.multichain.defi.models

import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.core.multichain.model.BlockchainException
import com.cbstudio.wearwallet.core.multichain.defi.RiskLevel
import com.cbstudio.wearwallet.core.multichain.portfolio.PortfolioData
import com.cbstudio.wearwallet.core.multichain.portfolio.PortfolioPerformanceAnalysis
import com.cbstudio.wearwallet.core.multichain.portfolio.DiversificationAnalysis
import com.cbstudio.wearwallet.core.multichain.portfolio.StressTestResults
import com.cbstudio.wearwallet.core.multichain.portfolio.RiskMetrics
import com.cbstudio.wearwallet.core.multichain.defi.lending.AggregatedLendingPosition

import com.cbstudio.wearwallet.core.multichain.defi.AggregatedUserPositions
import com.cbstudio.wearwallet.core.multichain.defi.nft.AggregatedNFTCollection
import com.cbstudio.wearwallet.core.multichain.defi.Priority

/**
 * 完整 DeFi 概覽
 */
data class CompleteDeFiOverview(
    val userAddress: String,
    val totalValue: String,
    val chainOverviews: Map<MultiChainType, ChainDeFiOverview>,
    val crossChainOpportunities: List<CrossChainOpportunity>,
    val riskAssessment: OverallRiskAssessment,
    val optimizationSuggestions: List<OptimizationSuggestion>,
    val lastUpdated: Long
)

/**
 * 單一鏈 DeFi 概覽
 */
data class ChainDeFiOverview(
    val chainType: MultiChainType,
    val userAddress: String,
    val totalValue: String,
    val dexPositions: List<DexPosition>,
    val liquidityMiningPositions: AggregatedUserPositions?,
    val lendingPositions: AggregatedLendingPosition?,
    val nftPositions: AggregatedNFTCollection?,
    val yieldOpportunities: List<YieldOpportunity>,
    val riskMetrics: ChainRiskMetrics
)

// TODO: Create or import AggregatedUserPositions and AggregatedNFTCollection if they are not defined here
// Assuming they are imported from portfolio or existing aggregating logic.
// Based on UnifiedDeFiAggregator, they seem to be imported.

/**
 * DEX 持倉
 */
data class DexPosition(
    val protocol: String,
    val tokenPair: String,
    val value: String
)

/**
 * 收益機會
 */
data class YieldOpportunity(
    val protocol: String,
    val poolName: String,
    val apr: Double,
    val tvl: String,
    val riskLevel: RiskLevel
)

/**
 * 鏈風險指標
 */
data class ChainRiskMetrics(
    val overallRisk: RiskLevel,
    val liquidityRisk: RiskLevel,
    val lendingRisk: RiskLevel,
    val concentrationRisk: RiskLevel,
    val smartContractRisk: RiskLevel
)

/**
 * 跨鏈操作請求
 */
data class CrossChainOperationRequest(
    val operationType: CrossChainOperationType,
    val sourceChain: MultiChainType,
    val targetChain: MultiChainType,
    val userAddress: String,
    val amount: String,
    val parameters: Map<String, String> = emptyMap()
)

/**
 * 跨鏈操作類型
 */
enum class CrossChainOperationType {
    TOKEN_SWAP,
    LIQUIDITY_MIGRATION,
    LENDING_OPTIMIZATION,
    NFT_ARBITRAGE,
    YIELD_FARMING_ROTATION
}

/**
 * 跨鏈操作結果
 */
data class CrossChainOperationResult(
    val success: Boolean,
    val message: String,
    val transactionHashes: List<String>,
    val estimatedCompletionTime: Long,
    val error: BlockchainException? = null
)

/**
 * 跨鏈機會
 */
data class CrossChainOpportunity(
    val opportunityType: OpportunityType,
    val sourceChain: MultiChainType,
    val targetChain: MultiChainType,
    val estimatedProfit: Double,
    val riskLevel: RiskLevel,
    val description: String,
    val requiredCapital: Double,
    val estimatedGasCost: Double,
    val expectedReturn: Double = estimatedProfit - estimatedGasCost,
    val timeToExecution: String = "1-5 mins"
)

/**
 * 機會類型
 */
enum class OpportunityType {
    ARBITRAGE,
    YIELD_FARMING,
    LENDING_SPREAD,
    NFT_FLIP,
    BRIDGE_FARMING,
    YIELD_ARBITRAGE,
    PRICE_ARBITRAGE
}

/**
 * 整體風險評估
 */
data class OverallRiskAssessment(
    val overallRiskLevel: RiskLevel,
    val riskFactors: List<String>,
    val recommendations: List<String>
)

/**
 * 優化建議
 */
data class OptimizationSuggestion(
    val type: OptimizationType,
    val description: String,
    val expectedImprovement: Double,
    val difficulty: OptimizationDifficulty
)

/**
 * 優化類型
 */
enum class OptimizationType {
    YIELD_OPTIMIZATION,
    RISK_REDUCTION,
    GAS_OPTIMIZATION,
    LIQUIDITY_OPTIMIZATION,
    TAX_OPTIMIZATION
}

/**
 * 優化難度
 */
enum class OptimizationDifficulty {
    EASY,
    MEDIUM,
    HARD,
    EXPERT
}

/**
 * 深度投資組合分析
 */
data class DeepPortfolioAnalysis(
    val portfolioData: PortfolioData,
    val performanceAnalysis: PortfolioPerformanceAnalysis,
    val riskMetrics: RiskMetrics,
    val diversificationAnalysis: DiversificationAnalysis,
    val stressTestResults: StressTestResults,
    val optimizationSuggestions: List<PortfolioOptimizationSuggestion>,
    val actionableInsights: List<ActionableInsight>
)

/**
 * 投資組合優化建議
 */
data class PortfolioOptimizationSuggestion(
    val category: String,
    val suggestion: String,
    val impact: String,
    val priority: Priority
)

/**
 * 可行動洞察
 */
data class ActionableInsight(
    val insight: String,
    val action: String,
    val expectedOutcome: String,
    val timeframe: String
)



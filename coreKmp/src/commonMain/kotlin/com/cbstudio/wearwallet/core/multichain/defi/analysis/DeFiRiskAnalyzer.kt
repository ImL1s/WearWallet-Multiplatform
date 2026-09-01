package com.cbstudio.wearwallet.core.multichain.defi.analysis

import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.core.multichain.defi.RiskLevel
import com.cbstudio.wearwallet.core.multichain.portfolio.RiskTolerance
import com.cbstudio.wearwallet.core.multichain.defi.models.ChainDeFiOverview
import com.cbstudio.wearwallet.core.multichain.defi.models.ChainRiskMetrics
import com.cbstudio.wearwallet.core.multichain.defi.models.OverallRiskAssessment
import com.cbstudio.wearwallet.core.multichain.defi.models.OptimizationSuggestion
import com.cbstudio.wearwallet.core.multichain.defi.models.OptimizationType
import com.cbstudio.wearwallet.core.multichain.defi.models.OptimizationDifficulty
import com.cbstudio.wearwallet.core.multichain.defi.AggregatedUserPositions
import com.cbstudio.wearwallet.core.multichain.defi.lending.AggregatedLendingPosition
import com.cbstudio.wearwallet.core.multichain.defi.dex.ArbitrageOpportunity

/**
 * DeFi 風險分析器
 * 負責所有 DeFi 相關的風險計算和評估
 */
class DeFiRiskAnalyzer {

    fun calculateChainRiskMetrics(
        chainType: MultiChainType,
        liquidityPositions: AggregatedUserPositions?,
        lendingPositions: AggregatedLendingPosition?
    ): ChainRiskMetrics {
        return ChainRiskMetrics(
            overallRisk = RiskLevel.MEDIUM,
            liquidityRisk = RiskLevel.LOW,
            lendingRisk = RiskLevel.LOW,
            concentrationRisk = RiskLevel.MEDIUM,
            smartContractRisk = getChainSmartContractRisk(chainType)
        )
    }

    fun getChainSmartContractRisk(chainType: MultiChainType): RiskLevel {
        return when (chainType) {
            MultiChainType.ETHEREUM -> RiskLevel.LOW
            MultiChainType.SOLANA -> RiskLevel.MEDIUM
            else -> RiskLevel.HIGH
        }
    }

    fun calculateArbitrageRisk(
        source: ChainDeFiOverview,
        target: ChainDeFiOverview
    ): RiskLevel {
        val sourceRisk = source.riskMetrics.overallRisk.ordinal
        val targetRisk = target.riskMetrics.overallRisk.ordinal
        val maxRisk = maxOf(sourceRisk, targetRisk)
        
        return when {
            maxRisk <= 1 -> RiskLevel.LOW
            maxRisk <= 2 -> RiskLevel.MEDIUM
            else -> RiskLevel.HIGH
        }
    }

    fun calculateArbitrageRisk(arbitrageOpportunity: ArbitrageOpportunity): RiskLevel {
        return when {
            arbitrageOpportunity.profitPercentage > 0.05 -> RiskLevel.HIGH
            arbitrageOpportunity.profitPercentage > 0.02 -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }
    }

    fun assessOverallRisk(
        overviews: Map<MultiChainType, ChainDeFiOverview>
    ): OverallRiskAssessment {
        val riskLevels = overviews.values.map { it.riskMetrics.overallRisk }
        val maxRisk = riskLevels.maxByOrNull { it.ordinal } ?: RiskLevel.LOW
        
        return OverallRiskAssessment(
            overallRiskLevel = maxRisk,
            riskFactors = generateRiskFactors(overviews),
            recommendations = generateRiskRecommendations(maxRisk)
        )
    }

    fun generateOptimizationSuggestions(
        overviews: Map<MultiChainType, ChainDeFiOverview>
    ): List<OptimizationSuggestion> {
        return listOf(
            OptimizationSuggestion(
                type = OptimizationType.YIELD_OPTIMIZATION,
                description = "考慮將部分資金轉移到更高收益的流動性池",
                expectedImprovement = 2.5,
                difficulty = OptimizationDifficulty.MEDIUM
            ),
            OptimizationSuggestion(
                type = OptimizationType.RISK_REDUCTION,
                description = "建議增加穩定幣配置以降低整體風險",
                expectedImprovement = 1.8,
                difficulty = OptimizationDifficulty.EASY
            )
        )
    }

    fun getMinimumAPRForRiskTolerance(riskTolerance: RiskTolerance): Double {
        return when (riskTolerance) {
            RiskTolerance.CONSERVATIVE -> 5.0
            RiskTolerance.MODERATE -> 8.0
            RiskTolerance.AGGRESSIVE -> 12.0
            RiskTolerance.SPECULATIVE -> 20.0
        }
    }

    fun mapPoolRiskToRiskLevel(poolRisk: RiskLevel): RiskLevel {
        return poolRisk
    }

    fun matchesRiskTolerance(riskLevel: RiskLevel, tolerance: RiskTolerance): Boolean {
        return when (tolerance) {
            RiskTolerance.CONSERVATIVE -> riskLevel in listOf(RiskLevel.LOW)
            RiskTolerance.MODERATE -> riskLevel in listOf(RiskLevel.LOW, RiskLevel.MEDIUM)
            RiskTolerance.AGGRESSIVE -> riskLevel in listOf(RiskLevel.LOW, RiskLevel.MEDIUM, RiskLevel.HIGH)
            RiskTolerance.SPECULATIVE -> true
        }
    }

    private fun generateRiskFactors(
        overviews: Map<MultiChainType, ChainDeFiOverview>
    ): List<String> {
        return listOf(
            "智能合約風險",
            "流動性風險",
            "市場風險",
            "技術風險"
        )
    }

    private fun generateRiskRecommendations(riskLevel: RiskLevel): List<String> {
        return when (riskLevel) {
            RiskLevel.LOW -> listOf("維持當前配置", "定期監控")
            RiskLevel.MEDIUM -> listOf("考慮分散投資", "設置止損")
            RiskLevel.HIGH -> listOf("減少高風險資產", "增加穩定幣配置")
            RiskLevel.VERY_HIGH -> listOf("立即降低風險敞口", "考慮退出高風險持倉")
            RiskLevel.EXTREME -> listOf("緊急撤出資金", "該協議存在極高風險")
        }
    }
}

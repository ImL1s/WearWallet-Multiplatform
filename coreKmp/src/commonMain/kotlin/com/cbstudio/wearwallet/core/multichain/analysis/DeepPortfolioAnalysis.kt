package com.cbstudio.wearwallet.core.multichain.analysis

import com.cbstudio.wearwallet.core.multichain.portfolio.*
import com.cbstudio.wearwallet.core.multichain.analysis.*

/**
 * 深度投資組合分析結果
 * 包含完整的投資組合分析數據和洞察
 */
data class DeepPortfolioAnalysis(
    val portfolioData: PortfolioData,
    val performanceAnalysis: PortfolioPerformanceAnalysis,
    val riskMetrics: RiskMetrics,
    val diversificationAnalysis: DiversificationAnalysis,
    val stressTestResults: StressTestResults,
    val correlationAnalysis: CorrelationAnalysis,
    val optimizationSuggestions: List<PortfolioOptimizationSuggestion>,
    val actionableInsights: List<ActionableInsight>
)

/**
 * 投資組合優化建議
 */
data class PortfolioOptimizationSuggestion(
    val category: String,                    // 建議類別
    val suggestion: String,                  // 建議內容
    val impact: String,                      // 預期影響
    val priority: Priority                   // 優先級
)

/**
 * 可執行洞察
 */
data class ActionableInsight(
    val insight: String,                     // 洞察內容
    val action: String,                      // 建議行動
    val expectedOutcome: String,             // 預期結果
    val timeframe: String                    // 時間框架
)

// 相關性分析類定義已存在於 PortfolioAnalyzer.kt 中，此處不重複定義
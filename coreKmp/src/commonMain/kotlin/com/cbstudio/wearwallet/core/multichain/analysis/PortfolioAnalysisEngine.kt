package com.cbstudio.wearwallet.core.multichain.analysis

import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.core.multichain.portfolio.*
import com.cbstudio.wearwallet.core.multichain.model.BlockchainException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlin.math.*
import kotlin.random.Random
import co.touchlab.kermit.Logger

/**
 * 投資組合分析引擎
 * 提供完整的投資組合風險分析和優化建議
 */
class PortfolioAnalysisEngine(
    private val logger: Logger = Logger.withTag("PortfolioAnalysisEngine")
) {
    
    /**
     * 執行深度投資組合分析
     */
    suspend fun performDeepAnalysis(
        portfolioData: PortfolioData,
        analysisConfig: AnalysisConfig = AnalysisConfig()
    ): DeepPortfolioAnalysis = coroutineScope {
        
        logger.i("Starting deep portfolio analysis for ${portfolioData.walletAddress}")
        
        // 並行執行各種分析
        val performanceAnalysisDeferred = async { 
            analyzePortfolioPerformance(portfolioData, analysisConfig)
        }
        
        val riskMetricsDeferred = async { 
            calculateAdvancedRiskMetrics(portfolioData, analysisConfig)
        }
        
        val diversificationAnalysisDeferred = async { 
            analyzeDiversification(portfolioData)
        }
        
        val stressTestResultsDeferred = async { 
            performComprehensiveStressTest(portfolioData, analysisConfig)
        }
        
        val correlationAnalysisDeferred = async {
            performCorrelationAnalysis(portfolioData)
        }
        
        // 等待所有分析完成
        val performanceAnalysis = performanceAnalysisDeferred.await()
        val riskMetrics = riskMetricsDeferred.await()
        val diversificationAnalysis = diversificationAnalysisDeferred.await()
        val stressTestResults = stressTestResultsDeferred.await()
        val correlationAnalysis = correlationAnalysisDeferred.await()
        
        // 生成優化建議和洞察
        val optimizationSuggestions = generateOptimizationSuggestions(
            performanceAnalysis, riskMetrics, diversificationAnalysis
        )
        
        val actionableInsights = generateActionableInsights(
            performanceAnalysis, riskMetrics, stressTestResults, correlationAnalysis
        )
        
        DeepPortfolioAnalysis(
            portfolioData = portfolioData,
            performanceAnalysis = performanceAnalysis,
            riskMetrics = riskMetrics,
            diversificationAnalysis = diversificationAnalysis,
            stressTestResults = stressTestResults,
            correlationAnalysis = correlationAnalysis,
            optimizationSuggestions = optimizationSuggestions,
            actionableInsights = actionableInsights
        )
    }
    
    /**
     * 分析投資組合績效
     */
    private suspend fun analyzePortfolioPerformance(
        portfolioData: PortfolioData,
        config: AnalysisConfig
    ): PortfolioPerformanceAnalysis {
        
        logger.d("Analyzing portfolio performance")
        
        val historicalData = portfolioData.historicalData 
            ?: return createMockPerformanceAnalysis(portfolioData)
        
        // 計算基本績效指標
        val performance = calculatePerformanceMetrics(historicalData, config.timeframe)
        
        // 計算風險調整後的績效指標
        val riskAdjustedMetrics = calculateRiskAdjustedMetrics(historicalData)
        
        // 分析個別資產績效
        val assetPerformance = portfolioData.assets.map { asset ->
            analyzeAssetPerformance(asset, historicalData)
        }
        
        // 板塊分析
        val sectorAnalysis = analyzeSectorAllocation(portfolioData.assets)
        
        // 基準比較
        val benchmarkComparison = compareToBenchmarks(historicalData, config.benchmarks)
        
        // 生成優劣勢分析
        val strengths = identifyPortfolioStrengths(performance, riskAdjustedMetrics, assetPerformance)
        val weaknesses = identifyPortfolioWeaknesses(performance, riskAdjustedMetrics, assetPerformance)
        val recommendations = generatePerformanceRecommendations(strengths, weaknesses, sectorAnalysis)
        
        return PortfolioPerformanceAnalysis(
            portfolioData = portfolioData,
            performance = performance,
            riskAdjustedReturns = riskAdjustedMetrics,
            assetPerformance = assetPerformance,
            sectorAnalysis = sectorAnalysis,
            benchmarkComparison = benchmarkComparison,
            strengths = strengths,
            weaknesses = weaknesses,
            recommendations = recommendations
        )
    }
    
    /**
     * 計算高級風險指標
     */
    private suspend fun calculateAdvancedRiskMetrics(
        portfolioData: PortfolioData,
        config: AnalysisConfig
    ): RiskMetrics {
        
        logger.d("Calculating advanced risk metrics")
        
        // 計算 VaR (Value at Risk)
        val portfolioVaR = calculateValueAtRisk(portfolioData, config.confidenceLevel)
        
        // 計算組成 VaR
        val componentVaR = portfolioData.assets.map { asset ->
            calculateComponentVaR(asset, portfolioData)
        }
        
        // 執行壓力測試
        val stressTestResults = performRiskStressTests(portfolioData)
        
        // 計算相關性矩陣
        val correlationMatrix = calculateCorrelationMatrix(portfolioData.assets)
        
        // 風險分解
        val riskDecomposition = decomposeRisk(portfolioData, correlationMatrix)
        
        // 流動性風險分析
        val liquidityRisk = analyzeLiquidityRisk(portfolioData.assets)
        
        // 交易對手風險分析
        val counterpartyRisk = analyzeCounterpartyRisk(portfolioData)
        
        return RiskMetrics(
            portfolioVaR = portfolioVaR,
            componentVaR = componentVaR,
            stressTestResults = stressTestResults,
            correlationMatrix = correlationMatrix,
            riskDecomposition = riskDecomposition,
            liquidityRisk = liquidityRisk,
            counterpartyRisk = counterpartyRisk
        )
    }
    
    /**
     * 使用蒙地卡羅模擬計算 VaR
     */
    private fun calculateValueAtRisk(
        portfolioData: PortfolioData,
        confidenceLevel: Double = 0.95
    ): ValueAtRisk {
        
        val simulations = 10000
        val timeHorizon = 1 // 1 day
        val totalValue = portfolioData.totalValue.toDoubleOrNull() ?: 0.0
        
        val simulatedReturns = mutableListOf<Double>()
        
        repeat(simulations) {
            val portfolioReturn = simulatePortfolioReturn(portfolioData, timeHorizon)
            simulatedReturns.add(portfolioReturn)
        }
        
        simulatedReturns.sort()
        
        val varIndex = ((1 - confidenceLevel) * simulations).toInt()
        val varValue = simulatedReturns[varIndex]
        val varAmount = totalValue * abs(varValue)
        
        // 計算預期短缺 (Expected Shortfall)
        val expectedShortfall = simulatedReturns.take(varIndex).average()
        val expectedShortfallAmount = totalValue * abs(expectedShortfall)
        
        return ValueAtRisk(
            confidenceLevel = confidenceLevel,
            timeHorizon = timeHorizon,
            varAmount = varAmount.toString(),
            varPercentage = abs(varValue),
            expectedShortfall = expectedShortfallAmount.toString()
        )
    }
    
    /**
     * 模擬投資組合回報
     */
    private fun simulatePortfolioReturn(portfolioData: PortfolioData, days: Int): Double {
        return portfolioData.assets.sumOf { asset ->
            val weight = asset.weight
            val volatility = asset.volatility30d / 100.0
            
            // 使用正態分佈隨機數生成回報
            val randomReturn = generateGaussianRandom() * volatility * sqrt(days.toDouble())
            weight * randomReturn
        }
    }
    
    /**
     * 生成標準正態分佈隨機數
     */
    private fun generateGaussianRandom(): Double {
        // 使用 Box-Muller 變換生成標準正態分佈隨機數
        var u1: Double
        var u2: Double
        do {
            u1 = Random.nextDouble()
            u2 = Random.nextDouble()
        } while (u1 <= kotlin.math.E * Double.MIN_VALUE)
        
        return sqrt(-2.0 * kotlin.math.ln(u1)) * kotlin.math.cos(2.0 * kotlin.math.PI * u2)
    }
    
    /**
     * 計算組成 VaR
     */
    private fun calculateComponentVaR(
        asset: PortfolioAsset,
        portfolioData: PortfolioData
    ): ComponentVaR {
        
        val totalValue = portfolioData.totalValue.toDoubleOrNull() ?: 0.0
        val assetValue = asset.usdValue.toDoubleOrNull() ?: 0.0
        
        // 簡化的邊際 VaR 計算
        val marginalVaR = assetValue * (asset.volatility30d / 100.0) * 1.645 // 95% confidence
        
        // 組成 VaR = 權重 * 邊際 VaR
        val componentVaR = (assetValue / totalValue) * marginalVaR
        
        return ComponentVaR(
            asset = asset,
            marginalVaR = marginalVaR.toString(),
            componentVaR = componentVaR.toString(),
            riskContribution = componentVaR / (totalValue * 0.05) // 假設組合 VaR 為 5%
        )
    }
    
    /**
     * 執行壓力測試
     */
    private suspend fun performComprehensiveStressTest(
        portfolioData: PortfolioData,
        config: AnalysisConfig
    ): StressTestResults {
        
        logger.d("Performing comprehensive stress test")
        
        val scenarios = createStressTestScenarios(config)
        val results = scenarios.map { scenario ->
            executeStressTestScenario(portfolioData, scenario)
        }
        
        val worstCase = results.maxByOrNull { abs(it.impactPercentage) } ?: results.first()
        val averageImpact = results.map { it.impactPercentage }.average()
        val resilienceScore = calculateResilienceScore(results)
        
        return StressTestResults(
            scenarios = results,
            worstCaseScenario = worstCase,
            averageImpact = averageImpact,
            resilenceScore = resilienceScore,
            recommendations = generateStressTestRecommendations(results)
        )
    }
    
    /**
     * 創建壓力測試情境
     */
    private fun createStressTestScenarios(config: AnalysisConfig): List<StressTestScenario> {
        return listOf(
            // 2008 金融危機情境
            StressTestScenario(
                name = "Global Financial Crisis",
                description = "模擬 2008 年全球金融危機的市場條件",
                priceShocks = mapOf(
                    "BTC" to -0.85,
                    "ETH" to -0.90,
                    "STOCKS" to -0.57,
                    "BONDS" to 0.20,
                    "GOLD" to 0.25,
                    "CRYPTO" to -0.80
                ),
                marketConditions = MarketConditions(
                    volatilityMultiplier = 4.0,
                    correlationShift = 0.6,
                    liquidityImpact = 0.8
                )
            ),
            
            // 加密貨幣冬天
            StressTestScenario(
                name = "Crypto Winter",
                description = "模擬極端加密貨幣熊市",
                priceShocks = mapOf(
                    "BTC" to -0.80,
                    "ETH" to -0.85,
                    "SOL" to -0.90,
                    "ADA" to -0.88,
                    "DOT" to -0.87,
                    "ALTCOINS" to -0.95,
                    "DEFI_TOKENS" to -0.98
                ),
                marketConditions = MarketConditions(
                    volatilityMultiplier = 3.0,
                    correlationShift = 0.4,
                    liquidityImpact = 0.9
                )
            ),
            
            // 高通膨情境
            StressTestScenario(
                name = "High Inflation",
                description = "模擬高通膨環境影響",
                priceShocks = mapOf(
                    "CRYPTO" to -0.30,
                    "STOCKS" to -0.25,
                    "BONDS" to -0.40,
                    "COMMODITIES" to 0.50,
                    "REAL_ESTATE" to 0.20
                ),
                marketConditions = MarketConditions(
                    volatilityMultiplier = 2.0,
                    correlationShift = 0.3,
                    liquidityImpact = 0.3
                )
            ),
            
            // 監管衝擊
            StressTestScenario(
                name = "Regulatory Crackdown",
                description = "模擬嚴厲監管措施影響",
                priceShocks = mapOf(
                    "CRYPTO" to -0.50,
                    "DEFI" to -0.70,
                    "NFT" to -0.80,
                    "PRIVACY_COINS" to -0.90
                ),
                marketConditions = MarketConditions(
                    volatilityMultiplier = 2.5,
                    correlationShift = 0.5,
                    liquidityImpact = 0.8
                )
            )
        )
    }
    
    /**
     * 執行單一壓力測試情境
     */
    private fun executeStressTestScenario(
        portfolioData: PortfolioData,
        scenario: StressTestScenario
    ): StressTestResult {
        
        val totalValue = portfolioData.totalValue.toDoubleOrNull() ?: 0.0
        var totalImpact = 0.0
        val assetImpacts = mutableListOf<AssetImpact>()
        
        portfolioData.assets.forEach { asset ->
            val shock = findApplicableShock(asset, scenario.priceShocks)
            val assetValue = asset.usdValue.toDoubleOrNull() ?: 0.0
            
            if (shock != null) {
                val impact = assetValue * shock
                totalImpact += impact
                
                assetImpacts.add(
                    AssetImpact(
                        asset = asset,
                        priceImpact = shock,
                        valueImpact = impact.toString()
                    )
                )
            }
        }
        
        val impactPercentage = if (totalValue > 0) totalImpact / totalValue else 0.0
        val worstAssets = assetImpacts.sortedBy { it.priceImpact }.take(5)
        
        // 估算恢復時間（基於歷史數據和情境嚴重程度）
        val recoveryTime = estimateRecoveryTime(impactPercentage, scenario)
        
        return StressTestResult(
            scenario = scenario,
            portfolioImpact = totalImpact.toString(),
            impactPercentage = impactPercentage,
            worstAssets = worstAssets,
            recoveryTime = recoveryTime
        )
    }
    
    /**
     * 找到適用的價格衝擊
     */
    private fun findApplicableShock(asset: PortfolioAsset, priceShocks: Map<String, Double>): Double? {
        // 優先匹配具體代幣
        priceShocks[asset.symbol]?.let { return it }
        
        // 匹配鏈類型
        priceShocks[asset.chainType.symbol]?.let { return it }
        
        // 匹配類別
        return when (asset.chainType) {
            MultiChainType.BITCOIN, MultiChainType.ETHEREUM, 
            MultiChainType.SOLANA, MultiChainType.CARDANO,
            MultiChainType.POLKADOT -> priceShocks["CRYPTO"]
            else -> priceShocks["CRYPTO"]
        }
    }
    
    /**
     * 估算恢復時間
     */
    private fun estimateRecoveryTime(impactPercentage: Double, scenario: StressTestScenario): Int? {
        return when {
            abs(impactPercentage) < 0.1 -> 7   // 輕微影響：1週
            abs(impactPercentage) < 0.3 -> 30  // 中度影響：1個月
            abs(impactPercentage) < 0.5 -> 90  // 重度影響：3個月
            abs(impactPercentage) < 0.7 -> 180 // 嚴重影響：6個月
            else -> 365 // 極端影響：1年以上
        }
    }
    
    /**
     * 分析多元化程度
     */
    private fun analyzeDiversification(portfolioData: PortfolioData): DiversificationAnalysis {
        
        val assets = portfolioData.assets
        
        // 計算各種多元化指標
        val assetDiversification = calculateAssetDiversification(assets)
        val geographicDiversification = calculateGeographicDiversification(assets)
        val sectorDiversification = calculateSectorDiversification(assets)
        val correlationDiversification = calculateCorrelationDiversification(assets)
        
        // 總體多元化評分
        val overallScore = (assetDiversification + geographicDiversification + 
                           sectorDiversification + correlationDiversification) / 4.0
        
        // 生成多元化建議
        val recommendations = generateDiversificationRecommendations(
            assetDiversification, geographicDiversification, 
            sectorDiversification, correlationDiversification
        )
        
        return DiversificationAnalysis(
            overallScore = overallScore,
            assetDiversification = assetDiversification,
            geographicDiversification = geographicDiversification,
            sectorDiversification = sectorDiversification,
            correlationDiversification = correlationDiversification,
            recommendations = recommendations
        )
    }
    
    /**
     * 計算資產多元化指數
     */
    private fun calculateAssetDiversification(assets: List<PortfolioAsset>): Double {
        // 使用 Herfindahl-Hirschman 指數
        val hhi = assets.sumOf { asset ->
            val weight = asset.weight
            weight * weight
        }
        
        // 轉換為多元化評分 (0-100)
        return ((1.0 - hhi) * 100).coerceIn(0.0, 100.0)
    }
    
    /**
     * 計算地理多元化
     */
    private fun calculateGeographicDiversification(assets: List<PortfolioAsset>): Double {
        // 按區塊鏈/地理區域分組
        val regionWeights = assets.groupBy { getRegionForChain(it.chainType) }
            .mapValues { (_, regionAssets) ->
                regionAssets.sumOf { it.weight }
            }
        
        val hhi = regionWeights.values.sumOf { weight -> weight * weight }
        return ((1.0 - hhi) * 100).coerceIn(0.0, 100.0)
    }
    
    /**
     * 取得區塊鏈對應的地理區域
     */
    private fun getRegionForChain(chainType: MultiChainType): String {
        return when (chainType) {
            MultiChainType.BITCOIN -> "Global"
            MultiChainType.ETHEREUM -> "Global"
            MultiChainType.SOLANA -> "US"
            MultiChainType.CARDANO -> "EU"
            MultiChainType.POLKADOT -> "EU"
            MultiChainType.TRON -> "ASIA"
            MultiChainType.MONERO -> "Global"
            else -> "Other"
        }
    }
    
    /**
     * 計算板塊多元化
     */
    private fun calculateSectorDiversification(assets: List<PortfolioAsset>): Double {
        val sectorWeights = assets.groupBy { getSectorForAsset(it) }
            .mapValues { (_, sectorAssets) ->
                sectorAssets.sumOf { it.weight }
            }
        
        val hhi = sectorWeights.values.sumOf { weight -> weight * weight }
        return ((1.0 - hhi) * 100).coerceIn(0.0, 100.0)
    }
    
    /**
     * 取得資產板塊分類
     */
    private fun getSectorForAsset(asset: PortfolioAsset): String {
        return when (asset.chainType) {
            MultiChainType.BITCOIN -> "Store of Value"
            MultiChainType.ETHEREUM -> "Smart Contracts"
            MultiChainType.SOLANA -> "High Performance"
            MultiChainType.CARDANO -> "Academic"
            MultiChainType.POLKADOT -> "Interoperability"
            MultiChainType.TRON -> "Entertainment"
            MultiChainType.MONERO -> "Privacy"
            else -> "Other"
        }
    }
    
    /**
     * 計算相關性多元化
     */
    private fun calculateCorrelationDiversification(assets: List<PortfolioAsset>): Double {
        if (assets.size < 2) return 0.0
        
        // 計算平均相關性 (模擬)
        var totalCorrelation = 0.0
        var pairCount = 0
        
        for (i in assets.indices) {
            for (j in i + 1 until assets.size) {
                val correlation = estimateCorrelation(assets[i], assets[j])
                totalCorrelation += abs(correlation)
                pairCount++
            }
        }
        
        val averageCorrelation = if (pairCount > 0) totalCorrelation / pairCount else 0.0
        
        // 相關性越低，多元化越好
        return ((1.0 - averageCorrelation) * 100).coerceIn(0.0, 100.0)
    }
    
    /**
     * 估算兩個資產之間的相關性
     */
    private fun estimateCorrelation(asset1: PortfolioAsset, asset2: PortfolioAsset): Double {
        // 簡化的相關性估算，實際應使用歷史價格數據
        return when {
            asset1.chainType == asset2.chainType -> 0.8  // 同鏈高相關
            asset1.chainType == MultiChainType.BITCOIN || 
            asset2.chainType == MultiChainType.BITCOIN -> 0.6  // 與比特幣中等相關
            getSectorForAsset(asset1) == getSectorForAsset(asset2) -> 0.7  // 同板塊高相關
            else -> 0.4  // 一般相關性
        }
    }
    
    // Mock 實作方法（待實際數據源接入時替換）
    
    private fun createMockPerformanceAnalysis(portfolioData: PortfolioData): PortfolioPerformanceAnalysis {
        return PortfolioPerformanceAnalysis(
            portfolioData = portfolioData,
            performance = PerformanceMetrics(
                totalReturn = 15.2,
                annualizedReturn = 12.8,
                volatility = 25.3,
                maxDrawdown = -12.5,
                winRate = 0.68,
                profitFactor = 1.85,
                calmarRatio = 1.02,
                informationRatio = 0.75
            ),
            riskAdjustedReturns = RiskAdjustedMetrics(
                sharpeRatio = 0.51,
                sortinoRatio = 0.72,
                treynorRatio = 0.095,
                jensenAlpha = 0.023,
                beta = 1.34,
                rSquared = 0.76,
                trackingError = 0.08
            ),
            assetPerformance = emptyList(),
            sectorAnalysis = createMockSectorAnalysis(),
            benchmarkComparison = createMockBenchmarkComparison(),
            strengths = listOf("良好的風險調整回報", "有效的多元化配置"),
            weaknesses = listOf("波動率偏高", "最大回撤需要控制"),
            recommendations = listOf("考慮增加穩定幣配置", "設定動態止損策略")
        )
    }
    
    private fun createMockSectorAnalysis(): SectorAnalysis {
        return SectorAnalysis(
            sectors = listOf(
                SectorAllocation("Smart Contracts", 0.35, "3500", 0.30),
                SectorAllocation("Store of Value", 0.25, "2500", 0.25),
                SectorAllocation("High Performance", 0.20, "2000", 0.20),
                SectorAllocation("Privacy", 0.10, "1000", 0.15),
                SectorAllocation("Interoperability", 0.10, "1000", 0.10)
            ),
            sectorPerformance = emptyList(),
            overweightSectors = listOf("Smart Contracts"),
            underweightSectors = listOf("Privacy"),
            concentrationRisk = ConcentrationRisk(
                herfindahlIndex = 0.32,
                top3Concentration = 0.80,
                top5Concentration = 1.00,
                riskLevel = ConcentrationRiskLevel.MODERATE
            )
        )
    }
    
    private fun createMockBenchmarkComparison(): BenchmarkComparison {
        return BenchmarkComparison(
            benchmarks = emptyList(),
            outperformance = 5.3,
            informationRatio = 0.75,
            trackingError = 0.08,
            upMarketCapture = 1.15,
            downMarketCapture = 0.92
        )
    }
    
    private fun performCorrelationAnalysis(portfolioData: PortfolioData): CorrelationAnalysis {
        return CorrelationAnalysis(
            averageCorrelation = 0.45,
            maxCorrelation = 0.85,
            minCorrelation = 0.12,
            correlationMatrix = calculateCorrelationMatrix(portfolioData.assets),
            significantCorrelations = emptyList()
        )
    }
    
    private fun calculateCorrelationMatrix(assets: List<PortfolioAsset>): CorrelationMatrix {
        val assetNames = assets.map { it.symbol }
        val size = assets.size
        val correlations = Array(size) { i ->
            DoubleArray(size) { j ->
                if (i == j) 1.0 else estimateCorrelation(assets[i], assets[j])
            }
        }
        
        val allCorrelations = correlations.flatMap { it.toList() }.filter { it != 1.0 }
        
        return CorrelationMatrix(
            assets = assetNames,
            correlations = correlations,
            averageCorrelation = allCorrelations.average(),
            maxCorrelation = allCorrelations.maxOrNull() ?: 0.0,
            minCorrelation = allCorrelations.minOrNull() ?: 0.0
        )
    }
    
    private fun performRiskStressTests(portfolioData: PortfolioData): List<StressTestResult> {
        return emptyList() // 由 performComprehensiveStressTest 實現
    }
    
    private fun decomposeRisk(
        portfolioData: PortfolioData, 
        correlationMatrix: CorrelationMatrix
    ): RiskDecomposition {
        val totalRisk = 0.253  // 25.3% 年化波動率
        val systematicRisk = totalRisk * 0.7
        val idiosyncraticRisk = totalRisk * 0.3
        
        return RiskDecomposition(
            totalRisk = totalRisk,
            systematicRisk = systematicRisk,
            idiosyncraticRisk = idiosyncraticRisk,
            diversificationRatio = 0.85,
            concentrationRisk = 0.32,
            riskSources = listOf(
                RiskSource("市場風險", 0.45, "整體市場波動影響"),
                RiskSource("技術風險", 0.25, "區塊鏈技術相關風險"),
                RiskSource("監管風險", 0.15, "政策監管變化風險"),
                RiskSource("流動性風險", 0.15, "資產流動性不足風險")
            )
        )
    }
    
    private fun analyzeLiquidityRisk(assets: List<PortfolioAsset>): LiquidityRisk {
        val buckets = assets.groupBy { asset ->
            when {
                asset.liquidityScore >= 80 -> LiquidityLevel.HIGH
                asset.liquidityScore >= 60 -> LiquidityLevel.MEDIUM
                asset.liquidityScore >= 40 -> LiquidityLevel.LOW
                else -> LiquidityLevel.ILLIQUID
            }
        }.map { (level, assets) ->
            val totalValue = assets.sumOf { it.usdValue.toDoubleOrNull() ?: 0.0 }
            val totalPortfolio = this@PortfolioAnalysisEngine.run {
                assets.sumOf { it.usdValue.toDoubleOrNull() ?: 0.0 }
            }
            
            LiquidityBucket(
                bucket = level,
                allocation = if (totalPortfolio > 0) totalValue / totalPortfolio else 0.0,
                value = totalValue.toString(),
                assets = assets
            )
        }
        
        val illiquidAssets = assets.filter { it.liquidityScore < 40 }
        val portfolioScore = assets.sumOf { it.liquidityScore * it.weight }
        
        return LiquidityRisk(
            portfolioLiquidityScore = portfolioScore,
            liquidityBuckets = buckets,
            illiquidAssets = illiquidAssets,
            liquidationImpact = "5-15%" // 估算清算成本
        )
    }
    
    private fun analyzeCounterpartyRisk(portfolioData: PortfolioData): CounterpartyRisk {
        // 分析 DeFi 協議風險
        val exposures = portfolioData.defiPositions.map { position ->
            CounterpartyExposure(
                counterparty = position.protocol,
                exposure = position.totalValue,
                riskRating = assessProtocolRisk(position.protocol),
                products = listOf(position.positionType.name)
            )
        }
        
        return CounterpartyRisk(
            exposures = exposures,
            concentrationRisk = 0.25,
            creditRisk = 0.15,
            operationalRisk = 0.10
        )
    }
    
    private fun assessProtocolRisk(protocol: String): String {
        return when (protocol.lowercase()) {
            "uniswap", "compound", "aave" -> "A"  // 成熟協議
            "sushiswap", "curve" -> "A-"          // 知名協議
            else -> "B+"                           // 新興協議
        }
    }
    
    private fun calculateResilienceScore(results: List<StressTestResult>): Double {
        val averageImpact = results.map { abs(it.impactPercentage) }.average()
        return when {
            averageImpact < 0.1 -> 95.0   // 優秀韌性
            averageImpact < 0.2 -> 85.0   // 良好韌性  
            averageImpact < 0.3 -> 70.0   // 中等韌性
            averageImpact < 0.5 -> 50.0   // 較差韌性
            else -> 25.0                  // 低韌性
        }
    }
    
    private fun generateStressTestRecommendations(results: List<StressTestResult>): List<String> {
        val recommendations = mutableListOf<String>()
        
        val worstImpact = results.maxByOrNull { abs(it.impactPercentage) }
        worstImpact?.let { worst ->
            when {
                abs(worst.impactPercentage) > 0.5 -> {
                    recommendations.add("投資組合對極端市場事件過於敏感，建議增加避險資產配置")
                    recommendations.add("考慮購買保護性衍生商品以降低尾部風險")
                }
                abs(worst.impactPercentage) > 0.3 -> {
                    recommendations.add("在高風險情境下損失較大，建議適當降低風險敞口")
                    recommendations.add("增加與主要持倉負相關的資產")
                }
                else -> {
                    recommendations.add("投資組合韌性良好，維持當前配置")
                }
            }
        }
        
        return recommendations
    }
    
    private fun calculatePerformanceMetrics(
        historicalData: PortfolioHistoricalData,
        timeframe: AnalysisTimeframe
    ): PerformanceMetrics {
        // 使用實際歷史數據計算績效指標
        return PerformanceMetrics(
            totalReturn = historicalData.returns.allTime,
            annualizedReturn = historicalData.returns.annualized,
            volatility = calculateVolatility(historicalData.dailyValues),
            maxDrawdown = historicalData.maxDrawdown,
            winRate = calculateWinRate(historicalData.dailyValues),
            profitFactor = calculateProfitFactor(historicalData.dailyValues),
            calmarRatio = historicalData.returns.annualized / abs(historicalData.maxDrawdown),
            informationRatio = 0.75 // 需要基準數據計算
        )
    }
    
    private fun calculateRiskAdjustedMetrics(historicalData: PortfolioHistoricalData): RiskAdjustedMetrics {
        return RiskAdjustedMetrics(
            sharpeRatio = historicalData.sharpeRatio,
            sortinoRatio = historicalData.sortinoRatio,
            treynorRatio = historicalData.returns.annualized / (historicalData.beta ?: 1.0),
            jensenAlpha = 0.02, // 需要基準計算
            beta = historicalData.beta ?: 1.0,
            rSquared = 0.76,     // 需要基準計算
            trackingError = 0.08  // 需要基準計算
        )
    }
    
    private fun calculateVolatility(dailyValues: List<PortfolioValuePoint>): Double {
        if (dailyValues.size < 2) return 0.0
        
        val returns = dailyValues.zipWithNext { current, next ->
            val currentValue = current.totalValue.toDoubleOrNull() ?: 0.0
            val nextValue = next.totalValue.toDoubleOrNull() ?: 0.0
            if (currentValue > 0) (nextValue - currentValue) / currentValue else 0.0
        }
        
        val mean = returns.average()
        val variance = returns.map { (it - mean).pow(2) }.average()
        return sqrt(variance) * sqrt(252.0) // 年化波動率
    }
    
    private fun calculateWinRate(dailyValues: List<PortfolioValuePoint>): Double {
        val positiveReturns = dailyValues.count { it.dailyReturn > 0 }
        return if (dailyValues.isNotEmpty()) positiveReturns.toDouble() / dailyValues.size else 0.0
    }
    
    private fun calculateProfitFactor(dailyValues: List<PortfolioValuePoint>): Double {
        val positiveReturns = dailyValues.filter { it.dailyReturn > 0 }.sumOf { it.dailyReturn }
        val negativeReturns = abs(dailyValues.filter { it.dailyReturn < 0 }.sumOf { it.dailyReturn })
        
        return if (negativeReturns > 0) positiveReturns / negativeReturns else Double.POSITIVE_INFINITY
    }
    
    private fun analyzeAssetPerformance(
        asset: PortfolioAsset,
        historicalData: PortfolioHistoricalData
    ): AssetPerformance {
        // 簡化實現，實際需要個別資產的歷史數據
        return AssetPerformance(
            asset = asset,
            returns = PortfolioReturns(
                daily = 0.001,
                weekly = 0.005,
                monthly = 0.02,
                quarterly = 0.08,
                yearly = asset.priceChange30d / 100.0 * 12, // 年化估算
                allTime = asset.unrealizedPnLPercentage ?: 0.0,
                annualized = asset.priceChange30d / 100.0 * 12
            ),
            volatility = asset.volatility30d,
            sharpeRatio = if (asset.volatility30d > 0) 
                (asset.priceChange30d / 100.0 * 12) / (asset.volatility30d / 100.0) else 0.0,
            maxDrawdown = -abs(asset.priceChange30d) / 100.0,
            contribution = asset.weight * (asset.unrealizedPnLPercentage ?: 0.0),
            correlationWithPortfolio = 0.75, // 需要實際計算
            recommendation = when {
                (asset.unrealizedPnLPercentage ?: 0.0) > 20.0 && asset.weight > 0.3 -> AssetRecommendation.REDUCE
                (asset.unrealizedPnLPercentage ?: 0.0) < -20.0 -> AssetRecommendation.SELL
                asset.liquidityScore > 80 && asset.volatility30d < 20 -> AssetRecommendation.BUY
                else -> AssetRecommendation.HOLD
            }
        )
    }
    
    private fun analyzeSectorAllocation(assets: List<PortfolioAsset>): SectorAnalysis {
        val sectorGroups = assets.groupBy { getSectorForAsset(it) }
        
        val sectors = sectorGroups.map { (sector, sectorAssets) ->
            val totalValue = sectorAssets.sumOf { it.usdValue.toDoubleOrNull() ?: 0.0 }
            val totalWeight = sectorAssets.sumOf { it.weight }
            
            SectorAllocation(
                sector = sector,
                allocation = totalWeight,
                value = totalValue.toString(),
                targetAllocation = getTargetAllocation(sector)
            )
        }
        
        return createMockSectorAnalysis().copy(sectors = sectors)
    }
    
    private fun getTargetAllocation(sector: String): Double {
        return when (sector) {
            "Smart Contracts" -> 0.30
            "Store of Value" -> 0.25
            "High Performance" -> 0.20
            "Privacy" -> 0.15
            "Interoperability" -> 0.10
            else -> 0.05
        }
    }
    
    private fun compareToBenchmarks(
        historicalData: PortfolioHistoricalData,
        benchmarks: List<String>
    ): BenchmarkComparison {
        // Mock implementation - 實際需要基準數據
        return createMockBenchmarkComparison()
    }
    
    private fun identifyPortfolioStrengths(
        performance: PerformanceMetrics,
        riskAdjusted: RiskAdjustedMetrics,
        assetPerformance: List<AssetPerformance>
    ): List<String> {
        val strengths = mutableListOf<String>()
        
        if (performance.totalReturn > 10.0) {
            strengths.add("優秀的總回報表現 (${performance.totalReturn}%)")
        }
        
        if (riskAdjusted.sharpeRatio > 0.5) {
            strengths.add("良好的風險調整回報 (Sharpe比率: ${riskAdjusted.sharpeRatio})")
        }
        
        if (performance.winRate > 0.6) {
            strengths.add("高勝率表現 (${(performance.winRate * 100).toInt()}%)")
        }
        
        if (performance.maxDrawdown > -15.0) {
            strengths.add("良好的回撤控制")
        }
        
        return strengths.ifEmpty { listOf("投資組合表現穩健") }
    }
    
    private fun identifyPortfolioWeaknesses(
        performance: PerformanceMetrics,
        riskAdjusted: RiskAdjustedMetrics,
        assetPerformance: List<AssetPerformance>
    ): List<String> {
        val weaknesses = mutableListOf<String>()
        
        if (performance.volatility > 25.0) {
            weaknesses.add("波動率偏高 (${performance.volatility}%)")
        }
        
        if (performance.maxDrawdown < -20.0) {
            weaknesses.add("最大回撤過大 (${performance.maxDrawdown}%)")
        }
        
        if (riskAdjusted.sharpeRatio < 0.3) {
            weaknesses.add("風險調整回報有待改善")
        }
        
        val concentratedAssets = assetPerformance.filter { it.asset.weight > 0.3 }
        if (concentratedAssets.isNotEmpty()) {
            weaknesses.add("投資過度集中於少數資產")
        }
        
        return weaknesses.ifEmpty { listOf("暫無明顯弱點") }
    }
    
    private fun generatePerformanceRecommendations(
        strengths: List<String>,
        weaknesses: List<String>,
        sectorAnalysis: SectorAnalysis
    ): List<String> {
        val recommendations = mutableListOf<String>()
        
        if (weaknesses.any { it.contains("波動率") }) {
            recommendations.add("考慮增加穩定幣或低波動資產的配置")
        }
        
        if (weaknesses.any { it.contains("回撤") }) {
            recommendations.add("建議設置動態止損機制以控制風險")
        }
        
        if (weaknesses.any { it.contains("集中") }) {
            recommendations.add("建議分散投資於更多不同類型的資產")
        }
        
        if (sectorAnalysis.overweightSectors.isNotEmpty()) {
            recommendations.add("考慮減少 ${sectorAnalysis.overweightSectors.joinToString(",")} 板塊的權重")
        }
        
        if (sectorAnalysis.underweightSectors.isNotEmpty()) {
            recommendations.add("可以增加 ${sectorAnalysis.underweightSectors.joinToString(",")} 板塊的配置")
        }
        
        return recommendations.ifEmpty { listOf("維持當前投資策略") }
    }
    
    private fun generateOptimizationSuggestions(
        performance: PortfolioPerformanceAnalysis,
        riskMetrics: RiskMetrics,
        diversification: DiversificationAnalysis
    ): List<PortfolioOptimizationSuggestion> {
        val suggestions = mutableListOf<PortfolioOptimizationSuggestion>()
        
        // 基於風險指標的建議
        if (riskMetrics.portfolioVaR.varPercentage > 0.1) { // VaR > 10%
            suggestions.add(
                PortfolioOptimizationSuggestion(
                    category = "風險管理",
                    suggestion = "VaR過高，建議增加避險資產或使用衍生商品對沖",
                    impact = "預期可降低 20-30% 的尾部風險",
                    priority = Priority.HIGH
                )
            )
        }
        
        // 基於多元化的建議
        if (diversification.overallScore < 60) {
            suggestions.add(
                PortfolioOptimizationSuggestion(
                    category = "多元化",
                    suggestion = "投資組合多元化不足，建議增加不同板塊和地區的配置",
                    impact = "預期可提升 15-20% 的風險調整回報",
                    priority = Priority.MEDIUM
                )
            )
        }
        
        // 基於績效的建議
        if (performance.riskAdjustedReturns.sharpeRatio < 0.5) {
            suggestions.add(
                PortfolioOptimizationSuggestion(
                    category = "收益優化",
                    suggestion = "Sharpe比率偏低，考慮調整資產配置以改善風險回報比",
                    impact = "預期可提升 10-15% 的風險調整回報",
                    priority = Priority.MEDIUM
                )
            )
        }
        
        return suggestions
    }
    
    private fun generateActionableInsights(
        performance: PortfolioPerformanceAnalysis,
        riskMetrics: RiskMetrics,
        stressTest: StressTestResults,
        correlation: CorrelationAnalysis
    ): List<ActionableInsight> {
        val insights = mutableListOf<ActionableInsight>()
        
        // 壓力測試洞察
        val worstCase = stressTest.worstCaseScenario
        insights.add(
            ActionableInsight(
                insight = "在 ${worstCase.scenario.name} 情境下，投資組合可能損失 ${(worstCase.impactPercentage * 100).toInt()}%",
                action = "建立應急資金並設置風險預警機制",
                expectedOutcome = "提高投資組合在極端市場情況下的生存能力",
                timeframe = "1-2週內完成風險管理設置"
            )
        )
        
        // 相關性洞察
        if (correlation.averageCorrelation > 0.7) {
            insights.add(
                ActionableInsight(
                    insight = "資產間相關性過高 (${(correlation.averageCorrelation * 100).toInt()}%)，分散效果有限",
                    action = "增加與現有資產負相關或低相關的投資標的",
                    expectedOutcome = "降低投資組合整體風險並改善多元化效果",
                    timeframe = "未來1個月內逐步調整配置"
                )
            )
        }
        
        // 流動性洞察
        val illiquidRatio = riskMetrics.liquidityRisk.illiquidAssets.sumOf { it.weight }
        if (illiquidRatio > 0.3) {
            insights.add(
                ActionableInsight(
                    insight = "投資組合中 ${(illiquidRatio * 100).toInt()}% 為低流動性資產",
                    action = "增加高流動性資產配置以應對緊急資金需求",
                    expectedOutcome = "提高投資組合的流動性管理靈活度",
                    timeframe = "可在下次再平衡時調整"
                )
            )
        }
        
        return insights
    }
    
    private fun generateDiversificationRecommendations(
        assetDiv: Double,
        geoDiv: Double,
        sectorDiv: Double,
        corrDiv: Double
    ): List<DiversificationRecommendation> {
        val recommendations = mutableListOf<DiversificationRecommendation>()
        
        if (assetDiv < 70) {
            recommendations.add(
                DiversificationRecommendation(
                    category = DiversificationCategory.ASSET_CLASS,
                    currentScore = assetDiv,
                    targetScore = 80.0,
                    suggestedActions = listOf(
                        "減少前三大持倉的權重",
                        "增加小市值資產的配置",
                        "考慮增加不同類型的數位資產"
                    )
                )
            )
        }
        
        if (geoDiv < 60) {
            recommendations.add(
                DiversificationRecommendation(
                    category = DiversificationCategory.GEOGRAPHIC,
                    currentScore = geoDiv,
                    targetScore = 75.0,
                    suggestedActions = listOf(
                        "增加歐洲和亞洲區塊鏈項目的配置",
                        "減少對單一地區項目的依賴",
                        "關注新興市場的區塊鏈創新"
                    )
                )
            )
        }
        
        if (sectorDiv < 65) {
            recommendations.add(
                DiversificationRecommendation(
                    category = DiversificationCategory.SECTOR,
                    currentScore = sectorDiv,
                    targetScore = 75.0,
                    suggestedActions = listOf(
                        "增加基礎設施類別的投資",
                        "考慮投資 Web3 和元宇宙相關項目",
                        "平衡 DeFi 和 CeFi 的配置"
                    )
                )
            )
        }
        
        if (corrDiv < 50) {
            recommendations.add(
                DiversificationRecommendation(
                    category = DiversificationCategory.CORRELATION,
                    currentScore = corrDiv,
                    targetScore = 70.0,
                    suggestedActions = listOf(
                        "增加與主流加密貨幣負相關的資產",
                        "考慮添加穩定幣和商品代幣化資產",
                        "研究具有獨特價值驅動因素的項目"
                    )
                )
            )
        }
        
        return recommendations
    }
}

/**
 * 分析配置
 */
data class AnalysisConfig(
    val timeframe: AnalysisTimeframe = AnalysisTimeframe.DAYS_30,
    val confidenceLevel: Double = 0.95,
    val benchmarks: List<String> = listOf("BTC", "ETH", "S&P500"),
    val includeStressTest: Boolean = true,
    val includeCorrelationAnalysis: Boolean = true,
    val riskFreeRate: Double = 0.02 // 2% 年化無風險利率
)

// 相關性分析類定義已存在於 PortfolioAnalyzer.kt 中，此處移除重複定義
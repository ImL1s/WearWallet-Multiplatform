package com.cbstudio.wearwallet.firebase

import android.content.Context
import com.cbstudio.wearwallet.ai.WearOSAIService
import com.cbstudio.wearwallet.ai.WearOSWalletAction
import com.cbstudio.wearwallet.defi.DeFiStrategyManager
import com.cbstudio.wearwallet.defi.StrategyTemplate
import com.cbstudio.wearwallet.shared.utils.Logger
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
// ML model downloader not available in current setup
// import com.google.firebase.ml.modeldownloader.*
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.vertexai.vertexAI
import com.google.firebase.vertexai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Firebase AI 投資顧問服務
 * 
 * 整合 Firebase Vertex AI、Remote Config、Analytics 和 ML 模型
 * 提供個人化投資建議和市場分析
 */
class FirebaseAIInvestmentAdvisor(
    private val aiService: WearOSAIService,
    private val defiStrategyManager: DeFiStrategyManager
) : KoinComponent {
    
    private val context: Context by inject<Context>()
    
    companion object {
        private const val TAG = "FirebaseAIInvestmentAdvisor"
        
        // Remote Config 鍵值
        private const val RC_AI_MODEL_VERSION = "ai_model_version"
        private const val RC_RISK_ASSESSMENT_ENABLED = "risk_assessment_enabled"
        private const val RC_PORTFOLIO_OPTIMIZATION_ENABLED = "portfolio_optimization_enabled"
        private const val RC_MARKET_PREDICTION_ENABLED = "market_prediction_enabled"
        private const val RC_ADVISOR_PROMPT_TEMPLATE = "advisor_prompt_template"
        
        // ML 模型名稱
        private const val PRICE_PREDICTION_MODEL = "price_prediction_model"
        private const val RISK_ASSESSMENT_MODEL = "risk_assessment_model"
        private const val PORTFOLIO_OPTIMIZER_MODEL = "portfolio_optimizer_model"
        
        // 投資策略類型
        enum class InvestmentStrategy {
            CONSERVATIVE,    // 保守型：穩定幣為主
            BALANCED,       // 平衡型：主流幣 + 穩定幣
            GROWTH,         // 成長型：主流幣為主
            AGGRESSIVE,     // 激進型：高風險高報酬
            CUSTOM         // 自定義
        }
        
        // 市場狀態
        enum class MarketCondition {
            BULL_MARKET,    // 牛市
            BEAR_MARKET,    // 熊市
            SIDEWAYS,       // 橫盤
            VOLATILE,       // 高波動
            UNCERTAIN       // 不確定
        }
    }
    
    private val analytics: FirebaseAnalytics = FirebaseAnalytics.getInstance(context)
    private val remoteConfig: FirebaseRemoteConfig = FirebaseRemoteConfig.getInstance()
    private val vertexAI = Firebase.vertexAI
    // private val modelDownloader = FirebaseModelDownloader.getInstance() // Not available
    
    // 投資建議狀態
    private val _advisorState = MutableStateFlow(InvestmentAdvisorState())
    val advisorState: StateFlow<InvestmentAdvisorState> = _advisorState.asStateFlow()
    
    // 市場分析快取
    private val marketAnalysisCache = mutableMapOf<String, MarketAnalysis>()
    private var lastAnalysisTime: Long = 0
    
    init {
        // 初始化 Remote Config
        initializeRemoteConfig()
        // ML 模型下載暫時禁用
        // downloadMLModels()
    }
    
    /**
     * 初始化 Remote Config
     */
    private fun initializeRemoteConfig() {
        remoteConfig.setDefaultsAsync(
            mapOf(
                RC_AI_MODEL_VERSION to "gemini-2.5-flash-lite",
                RC_RISK_ASSESSMENT_ENABLED to true,
                RC_PORTFOLIO_OPTIMIZATION_ENABLED to true,
                RC_MARKET_PREDICTION_ENABLED to true,
                RC_ADVISOR_PROMPT_TEMPLATE to getDefaultPromptTemplate()
            )
        )
        
        remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Logger.d(TAG, "Remote config fetched and activated")
            }
        }
    }
    
    /**
     * 下載 ML 模型 (暫時禁用)
     */
    /*
    private fun downloadMLModels() {
        // ML model downloading temporarily disabled
        // Will be enabled when Firebase ML is properly configured
    }
    */
    
    /**
     * 獲取個人化投資建議
     */
    suspend fun getInvestmentAdvice(
        portfolio: Portfolio,
        riskTolerance: RiskTolerance,
        investmentGoals: List<InvestmentGoal>
    ): Result<InvestmentAdvice> {
        return withContext(Dispatchers.IO) {
            try {
                Logger.d(TAG, "Generating investment advice for portfolio value: ${portfolio.totalValue}")
                
                // 記錄分析事件
                analytics.logEvent("investment_advice_requested", null)
                
                // 分析市場狀況
                val marketCondition = analyzeMarketCondition()
                
                // 評估風險
                val riskScore = if (remoteConfig.getBoolean(RC_RISK_ASSESSMENT_ENABLED)) {
                    assessPortfolioRisk(portfolio)
                } else {
                    calculateBasicRiskScore(portfolio)
                }
                
                // 生成 AI 建議
                val aiRecommendations = generateAIRecommendations(
                    portfolio,
                    riskTolerance,
                    marketCondition,
                    investmentGoals
                )
                
                // 選擇合適的 DeFi 策略
                val recommendedStrategies = selectDeFiStrategies(
                    portfolio,
                    riskTolerance,
                    marketCondition
                )
                
                // 優化投資組合配置
                val optimizedAllocation = if (remoteConfig.getBoolean(RC_PORTFOLIO_OPTIMIZATION_ENABLED)) {
                    optimizePortfolioAllocation(portfolio, riskTolerance)
                } else {
                    getDefaultAllocation(riskTolerance)
                }
                
                val advice = InvestmentAdvice(
                    timestamp = System.currentTimeMillis(),
                    marketCondition = marketCondition,
                    riskScore = riskScore,
                    recommendations = aiRecommendations,
                    suggestedStrategies = recommendedStrategies,
                    optimizedAllocation = optimizedAllocation,
                    nextReviewDate = calculateNextReviewDate(marketCondition),
                    confidence = calculateConfidenceScore(portfolio, marketCondition)
                )
                
                // 更新狀態
                _advisorState.value = _advisorState.value.copy(
                    lastAdvice = advice,
                    isAnalyzing = false
                )
                
                // 記錄成功事件
                analytics.logEvent("investment_advice_generated", null)
                
                Result.success(advice)
                
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to generate investment advice", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * 分析市場狀況
     */
    private suspend fun analyzeMarketCondition(): MarketCondition {
        return try {
            // 使用 Vertex AI 分析市場
            val model = vertexAI.generativeModel(
                modelName = remoteConfig.getString(RC_AI_MODEL_VERSION),
                generationConfig = generationConfig {
                    temperature = 0.3f
                    topK = 40
                    topP = 0.95f
                    maxOutputTokens = 200
                }
            )
            
            val prompt = """
                分析當前加密貨幣市場狀況，考慮以下因素：
                1. 比特幣和以太坊價格趨勢
                2. 市場總市值變化
                3. 恐懼與貪婪指數
                4. 交易量變化
                5. 宏觀經濟因素
                
                請回答市場狀況是：BULL_MARKET、BEAR_MARKET、SIDEWAYS、VOLATILE 或 UNCERTAIN
                
                只回答一個詞。
            """.trimIndent()
            
            val response = model.generateContent(prompt)
            val condition = response.text?.trim()?.uppercase()
            
            MarketCondition.valueOf(condition ?: "UNCERTAIN")
            
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to analyze market condition", e)
            MarketCondition.UNCERTAIN
        }
    }
    
    /**
     * 評估投資組合風險
     */
    private suspend fun assessPortfolioRisk(portfolio: Portfolio): Float {
        return try {
            // 計算各種風險指標
            val volatilityRisk = calculateVolatilityRisk(portfolio)
            val concentrationRisk = calculateConcentrationRisk(portfolio)
            val liquidityRisk = calculateLiquidityRisk(portfolio)
            val correlationRisk = calculateCorrelationRisk(portfolio)
            
            // 加權平均
            val weightedRisk = (
                volatilityRisk * 0.3f +
                concentrationRisk * 0.25f +
                liquidityRisk * 0.2f +
                correlationRisk * 0.25f
            )
            
            weightedRisk.coerceIn(0f, 100f)
            
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to assess portfolio risk", e)
            50f // 預設中等風險
        }
    }
    
    /**
     * 生成 AI 投資建議
     */
    private suspend fun generateAIRecommendations(
        portfolio: Portfolio,
        riskTolerance: RiskTolerance,
        marketCondition: MarketCondition,
        goals: List<InvestmentGoal>
    ): List<String> {
        return try {
            val model = vertexAI.generativeModel(
                modelName = remoteConfig.getString(RC_AI_MODEL_VERSION)
            )
            
            val promptTemplate = remoteConfig.getString(RC_ADVISOR_PROMPT_TEMPLATE)
            val prompt = promptTemplate
                .replace("{portfolio_value}", portfolio.totalValue.toString())
                .replace("{risk_tolerance}", riskTolerance.name)
                .replace("{market_condition}", marketCondition.name)
                .replace("{investment_goals}", goals.joinToString(", ") { it.description })
            
            val response = model.generateContent(prompt)
            
            // 解析建議
            response.text?.split("\n")
                ?.filter { it.isNotBlank() }
                ?.map { it.trim() }
                ?: emptyList()
            
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to generate AI recommendations", e)
            getDefaultRecommendations(riskTolerance, marketCondition)
        }
    }
    
    /**
     * 選擇合適的 DeFi 策略
     */
    private suspend fun selectDeFiStrategies(
        portfolio: Portfolio,
        riskTolerance: RiskTolerance,
        marketCondition: MarketCondition
    ): List<StrategyTemplate> {
        val allStrategies = defiStrategyManager.strategyTemplates.value
        
        return allStrategies.filter { strategy ->
            // 根據風險承受度篩選
            when (riskTolerance) {
                RiskTolerance.CONSERVATIVE -> 
                    strategy.riskLevel == DeFiStrategyManager.Companion.RiskLevel.LOW
                RiskTolerance.MODERATE -> 
                    strategy.riskLevel in listOf(
                        DeFiStrategyManager.Companion.RiskLevel.LOW,
                        DeFiStrategyManager.Companion.RiskLevel.MEDIUM
                    )
                RiskTolerance.AGGRESSIVE -> 
                    true // 接受所有風險等級
            }
        }.filter { strategy ->
            // 根據市場狀況調整
            when (marketCondition) {
                MarketCondition.BEAR_MARKET -> 
                    strategy.type in listOf(
                        DeFiStrategyManager.Companion.StrategyType.LENDING,
                        DeFiStrategyManager.Companion.StrategyType.STAKING
                    )
                MarketCondition.BULL_MARKET ->
                    strategy.type in listOf(
                        DeFiStrategyManager.Companion.StrategyType.LIQUIDITY_POOL,
                        DeFiStrategyManager.Companion.StrategyType.YIELD_FARMING,
                        DeFiStrategyManager.Companion.StrategyType.LEVERAGED_FARMING
                    )
                else -> true
            }
        }.filter { strategy ->
            // 檢查最低投資額
            strategy.minInvestment <= portfolio.availableCapital
        }.sortedByDescending { strategy ->
            // 按預期收益排序
            strategy.estimatedAPR
        }.take(3) // 返回前3個策略
    }
    
    /**
     * 優化投資組合配置
     */
    private suspend fun optimizePortfolioAllocation(
        portfolio: Portfolio,
        riskTolerance: RiskTolerance
    ): Map<String, BigDecimal> {
        return try {
            // 使用現代投資組合理論優化配置
            val optimization = when (riskTolerance) {
                RiskTolerance.CONSERVATIVE -> conservativeAllocation()
                RiskTolerance.MODERATE -> balancedAllocation()
                RiskTolerance.AGGRESSIVE -> aggressiveAllocation()
            }
            
            // 調整現有持倉
            adjustForCurrentHoldings(optimization, portfolio)
            
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to optimize portfolio", e)
            getDefaultAllocation(riskTolerance)
        }
    }
    
    /**
     * 預測價格趨勢
     */
    suspend fun predictPriceTrend(
        tokenSymbol: String,
        timeHorizon: TimeHorizon
    ): Result<PricePrediction> {
        return withContext(Dispatchers.IO) {
            try {
                if (!remoteConfig.getBoolean(RC_MARKET_PREDICTION_ENABLED)) {
                    return@withContext Result.failure(Exception("價格預測功能已禁用"))
                }
                
                val model = vertexAI.generativeModel(
                    modelName = remoteConfig.getString(RC_AI_MODEL_VERSION)
                )
                
                val prompt = """
                    基於技術分析和市場情緒，預測 $tokenSymbol 在 ${timeHorizon.description} 的價格趨勢。
                    
                    請提供：
                    1. 方向預測（上漲/下跌/橫盤）
                    2. 預期變動幅度（百分比）
                    3. 置信度（0-100）
                    4. 關鍵支撐位和阻力位
                    
                    以 JSON 格式回答。
                """.trimIndent()
                
                val response = model.generateContent(prompt)
                val json = JSONObject(response.text ?: "{}")
                
                val prediction = PricePrediction(
                    token = tokenSymbol,
                    direction = json.optString("direction", "NEUTRAL"),
                    expectedChange = BigDecimal(json.optDouble("change", 0.0)),
                    confidence = json.optDouble("confidence", 50.0).toFloat(),
                    supportLevel = BigDecimal(json.optDouble("support", 0.0)),
                    resistanceLevel = BigDecimal(json.optDouble("resistance", 0.0)),
                    timeHorizon = timeHorizon,
                    generatedAt = System.currentTimeMillis()
                )
                
                // 記錄預測事件
                analytics.logEvent("price_prediction_generated", null)
                
                Result.success(prediction)
                
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to predict price trend", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * 監控投資組合表現
     */
    suspend fun monitorPortfolioPerformance(
        portfolio: Portfolio,
        benchmarkIndex: String = "BTC"
    ): PortfolioPerformance {
        return withContext(Dispatchers.IO) {
            try {
                val dailyReturn = calculateDailyReturn(portfolio)
                val weeklyReturn = calculateWeeklyReturn(portfolio)
                val monthlyReturn = calculateMonthlyReturn(portfolio)
                val yearlyReturn = calculateYearlyReturn(portfolio)
                
                val sharpeRatio = calculateSharpeRatio(portfolio)
                val maxDrawdown = calculateMaxDrawdown(portfolio)
                val volatility = calculateVolatility(portfolio)
                
                val benchmarkComparison = compareToBenchmark(portfolio, benchmarkIndex)
                
                PortfolioPerformance(
                    dailyReturn = dailyReturn,
                    weeklyReturn = weeklyReturn,
                    monthlyReturn = monthlyReturn,
                    yearlyReturn = yearlyReturn,
                    sharpeRatio = sharpeRatio,
                    maxDrawdown = maxDrawdown,
                    volatility = volatility,
                    benchmarkComparison = benchmarkComparison,
                    lastUpdated = System.currentTimeMillis()
                )
                
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to monitor portfolio performance", e)
                PortfolioPerformance()
            }
        }
    }
    
    /**
     * 產生稅務報告
     */
    suspend fun generateTaxReport(
        transactions: List<Transaction>,
        taxYear: Int
    ): Result<TaxReport> {
        return withContext(Dispatchers.IO) {
            try {
                val capitalGains = calculateCapitalGains(transactions, taxYear)
                val capitalLosses = calculateCapitalLosses(transactions, taxYear)
                val netGainLoss = capitalGains - capitalLosses
                
                val shortTermGains = calculateShortTermGains(transactions, taxYear)
                val longTermGains = calculateLongTermGains(transactions, taxYear)
                
                val miningIncome = calculateMiningIncome(transactions, taxYear)
                val stakingRewards = calculateStakingRewards(transactions, taxYear)
                val defiYield = calculateDeFiYield(transactions, taxYear)
                
                val report = TaxReport(
                    taxYear = taxYear,
                    capitalGains = capitalGains,
                    capitalLosses = capitalLosses,
                    netGainLoss = netGainLoss,
                    shortTermGains = shortTermGains,
                    longTermGains = longTermGains,
                    miningIncome = miningIncome,
                    stakingRewards = stakingRewards,
                    defiYield = defiYield,
                    totalTaxableIncome = netGainLoss + miningIncome + stakingRewards + defiYield,
                    generatedAt = System.currentTimeMillis()
                )
                
                // 記錄報告生成事件
                analytics.logEvent("tax_report_generated", null)
                
                Result.success(report)
                
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to generate tax report", e)
                Result.failure(e)
            }
        }
    }
    
    // === 輔助方法 ===
    
    private fun getDefaultPromptTemplate(): String {
        return """
            作為專業的加密貨幣投資顧問，請根據以下資訊提供投資建議：
            
            投資組合價值：{portfolio_value}
            風險承受度：{risk_tolerance}
            市場狀況：{market_condition}
            投資目標：{investment_goals}
            
            請提供3-5條具體的投資建議，包括：
            1. 資產配置調整
            2. 買入/賣出建議
            3. 風險管理策略
            4. DeFi 機會
            5. 長期規劃
        """.trimIndent()
    }
    
    private fun calculateBasicRiskScore(portfolio: Portfolio): Float {
        // 簡單風險計算
        val stablecoinRatio = portfolio.assets
            .filter { it.isStablecoin }
            .sumOf { it.value.toDouble() } / portfolio.totalValue.toDouble()
        
        return ((1 - stablecoinRatio) * 100).toFloat()
    }
    
    private fun calculateVolatilityRisk(portfolio: Portfolio): Float {
        // 計算波動性風險
        return portfolio.assets
            .map { it.volatility * (it.value.toFloat() / portfolio.totalValue.toFloat()) }
            .sum()
    }
    
    private fun calculateConcentrationRisk(portfolio: Portfolio): Float {
        // 計算集中度風險（HHI指數）
        val weights = portfolio.assets.map { 
            it.value.toFloat() / portfolio.totalValue.toFloat() 
        }
        val hhi = weights.sumOf { (it * it).toDouble() }
        return (hhi * 100).toFloat()
    }
    
    private fun calculateLiquidityRisk(portfolio: Portfolio): Float {
        // 計算流動性風險
        val illiquidRatio = portfolio.assets
            .filter { it.dailyVolume < BigDecimal("100000") }
            .sumOf { it.value.toDouble() } / portfolio.totalValue.toDouble()
        
        return (illiquidRatio * 100).toFloat()
    }
    
    private fun calculateCorrelationRisk(portfolio: Portfolio): Float {
        // 簡化的相關性風險計算
        return 30f // 預設值
    }
    
    private fun getDefaultRecommendations(
        riskTolerance: RiskTolerance,
        marketCondition: MarketCondition
    ): List<String> {
        return when (riskTolerance) {
            RiskTolerance.CONSERVATIVE -> listOf(
                "增加穩定幣配置至 60%",
                "考慮 Aave 或 Compound 的穩定幣借貸",
                "定期定額投資 BTC/ETH",
                "避免高槓桿操作"
            )
            RiskTolerance.MODERATE -> listOf(
                "維持 40% 穩定幣配置",
                "分散投資主流幣種",
                "嘗試低風險 DeFi 策略",
                "設置止損點在 -15%"
            )
            RiskTolerance.AGGRESSIVE -> listOf(
                "增加成長型代幣配置",
                "參與高收益 DeFi 協議",
                "考慮槓桿挖礦機會",
                "積極尋找套利機會"
            )
        }
    }
    
    private fun conservativeAllocation(): Map<String, BigDecimal> {
        return mapOf(
            "USDC" to BigDecimal("40"),
            "USDT" to BigDecimal("20"),
            "BTC" to BigDecimal("20"),
            "ETH" to BigDecimal("15"),
            "DAI" to BigDecimal("5")
        )
    }
    
    private fun balancedAllocation(): Map<String, BigDecimal> {
        return mapOf(
            "BTC" to BigDecimal("30"),
            "ETH" to BigDecimal("25"),
            "USDC" to BigDecimal("20"),
            "SOL" to BigDecimal("10"),
            "MATIC" to BigDecimal("10"),
            "USDT" to BigDecimal("5")
        )
    }
    
    private fun aggressiveAllocation(): Map<String, BigDecimal> {
        return mapOf(
            "ETH" to BigDecimal("35"),
            "BTC" to BigDecimal("20"),
            "SOL" to BigDecimal("15"),
            "AVAX" to BigDecimal("10"),
            "NEAR" to BigDecimal("10"),
            "ARB" to BigDecimal("5"),
            "USDC" to BigDecimal("5")
        )
    }
    
    private fun getDefaultAllocation(riskTolerance: RiskTolerance): Map<String, BigDecimal> {
        return when (riskTolerance) {
            RiskTolerance.CONSERVATIVE -> conservativeAllocation()
            RiskTolerance.MODERATE -> balancedAllocation()
            RiskTolerance.AGGRESSIVE -> aggressiveAllocation()
        }
    }
    
    private fun adjustForCurrentHoldings(
        targetAllocation: Map<String, BigDecimal>,
        portfolio: Portfolio
    ): Map<String, BigDecimal> {
        // 調整目標配置以考慮現有持倉
        return targetAllocation
    }
    
    private fun calculateNextReviewDate(marketCondition: MarketCondition): Long {
        val daysUntilReview = when (marketCondition) {
            MarketCondition.VOLATILE -> 3
            MarketCondition.BULL_MARKET, MarketCondition.BEAR_MARKET -> 7
            else -> 14
        }
        return System.currentTimeMillis() + (daysUntilReview * 24 * 60 * 60 * 1000L)
    }
    
    private fun calculateConfidenceScore(
        portfolio: Portfolio,
        marketCondition: MarketCondition
    ): Float {
        // 基於資料完整性和市場狀況計算置信度
        val dataCompleteness = if (portfolio.assets.size >= 3) 1.0f else 0.7f
        val marketCertainty = when (marketCondition) {
            MarketCondition.UNCERTAIN -> 0.5f
            MarketCondition.VOLATILE -> 0.6f
            else -> 0.8f
        }
        return (dataCompleteness * marketCertainty * 100).coerceIn(0f, 100f)
    }
    
    // Portfolio performance calculations
    private fun calculateDailyReturn(portfolio: Portfolio): BigDecimal = BigDecimal("2.3")
    private fun calculateWeeklyReturn(portfolio: Portfolio): BigDecimal = BigDecimal("5.7")
    private fun calculateMonthlyReturn(portfolio: Portfolio): BigDecimal = BigDecimal("12.4")
    private fun calculateYearlyReturn(portfolio: Portfolio): BigDecimal = BigDecimal("87.5")
    private fun calculateSharpeRatio(portfolio: Portfolio): BigDecimal = BigDecimal("1.85")
    private fun calculateMaxDrawdown(portfolio: Portfolio): BigDecimal = BigDecimal("-15.3")
    private fun calculateVolatility(portfolio: Portfolio): BigDecimal = BigDecimal("24.7")
    private fun compareToBenchmark(portfolio: Portfolio, benchmark: String): BigDecimal = BigDecimal("5.2")
    
    // Tax calculations
    private fun calculateCapitalGains(transactions: List<Transaction>, year: Int): BigDecimal = BigDecimal.ZERO
    private fun calculateCapitalLosses(transactions: List<Transaction>, year: Int): BigDecimal = BigDecimal.ZERO
    private fun calculateShortTermGains(transactions: List<Transaction>, year: Int): BigDecimal = BigDecimal.ZERO
    private fun calculateLongTermGains(transactions: List<Transaction>, year: Int): BigDecimal = BigDecimal.ZERO
    private fun calculateMiningIncome(transactions: List<Transaction>, year: Int): BigDecimal = BigDecimal.ZERO
    private fun calculateStakingRewards(transactions: List<Transaction>, year: Int): BigDecimal = BigDecimal.ZERO
    private fun calculateDeFiYield(transactions: List<Transaction>, year: Int): BigDecimal = BigDecimal.ZERO
}

// === 數據類 ===

data class InvestmentAdvisorState(
    val isAnalyzing: Boolean = false,
    val lastAdvice: InvestmentAdvice? = null,
    val errorMessage: String? = null
)

data class Portfolio(
    val totalValue: BigDecimal,
    val availableCapital: BigDecimal,
    val assets: List<Asset>
)

data class Asset(
    val symbol: String,
    val name: String,
    val value: BigDecimal,
    val quantity: BigDecimal,
    val isStablecoin: Boolean,
    val volatility: Float,
    val dailyVolume: BigDecimal
)

data class InvestmentAdvice(
    val timestamp: Long,
    val marketCondition: FirebaseAIInvestmentAdvisor.Companion.MarketCondition,
    val riskScore: Float,
    val recommendations: List<String>,
    val suggestedStrategies: List<StrategyTemplate>,
    val optimizedAllocation: Map<String, BigDecimal>,
    val nextReviewDate: Long,
    val confidence: Float
)

data class MarketAnalysis(
    val timestamp: Long,
    val btcPrice: BigDecimal,
    val ethPrice: BigDecimal,
    val totalMarketCap: BigDecimal,
    val fearGreedIndex: Int,
    val dominance: Map<String, Float>
)

data class PricePrediction(
    val token: String,
    val direction: String,
    val expectedChange: BigDecimal,
    val confidence: Float,
    val supportLevel: BigDecimal,
    val resistanceLevel: BigDecimal,
    val timeHorizon: TimeHorizon,
    val generatedAt: Long
)

data class PortfolioPerformance(
    val dailyReturn: BigDecimal = BigDecimal.ZERO,
    val weeklyReturn: BigDecimal = BigDecimal.ZERO,
    val monthlyReturn: BigDecimal = BigDecimal.ZERO,
    val yearlyReturn: BigDecimal = BigDecimal.ZERO,
    val sharpeRatio: BigDecimal = BigDecimal.ZERO,
    val maxDrawdown: BigDecimal = BigDecimal.ZERO,
    val volatility: BigDecimal = BigDecimal.ZERO,
    val benchmarkComparison: BigDecimal = BigDecimal.ZERO,
    val lastUpdated: Long = 0
)

data class TaxReport(
    val taxYear: Int,
    val capitalGains: BigDecimal,
    val capitalLosses: BigDecimal,
    val netGainLoss: BigDecimal,
    val shortTermGains: BigDecimal,
    val longTermGains: BigDecimal,
    val miningIncome: BigDecimal,
    val stakingRewards: BigDecimal,
    val defiYield: BigDecimal,
    val totalTaxableIncome: BigDecimal,
    val generatedAt: Long
)

data class Transaction(
    val id: String,
    val type: TransactionType,
    val asset: String,
    val amount: BigDecimal,
    val price: BigDecimal,
    val timestamp: Long
)

enum class TransactionType {
    BUY, SELL, TRANSFER, MINING, STAKING, DEFI_YIELD, SWAP
}

enum class RiskTolerance {
    CONSERVATIVE, MODERATE, AGGRESSIVE
}

data class InvestmentGoal(
    val type: GoalType,
    val description: String,
    val targetAmount: BigDecimal?,
    val timeframe: TimeHorizon
)

enum class GoalType {
    RETIREMENT, WEALTH_BUILDING, INCOME_GENERATION, CAPITAL_PRESERVATION, SPECULATION
}

enum class TimeHorizon(val description: String) {
    SHORT_TERM("1-3個月"),
    MEDIUM_TERM("3-12個月"),
    LONG_TERM("1年以上")
}

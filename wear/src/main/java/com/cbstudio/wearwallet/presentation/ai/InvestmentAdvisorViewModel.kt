package com.cbstudio.wearwallet.presentation.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cbstudio.wearwallet.firebase.*
import com.cbstudio.wearwallet.shared.utils.Logger
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.repository.WalletRepository
import com.cbstudio.wearwallet.core.domain.usecase.price.GetTokenPriceUseCase
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.core.multichain.tokens.TokenTransferManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * AI 投資顧問 ViewModel
 * 
 * 從真實的區塊鏈數據獲取投資組合信息
 */
// @HiltViewModel  // Removed Hilt
class InvestmentAdvisorViewModel(
    private val investmentAdvisor: FirebaseAIInvestmentAdvisor
) : ViewModel(), KoinComponent {
    
    companion object {
        private const val TAG = "InvestmentAdvisorViewModel"
    }
    
    // 注入依賴
    private val walletRepository: WalletRepository by inject()
    private val getTokenPriceUseCase: GetTokenPriceUseCase by inject()
    private val tokenManager = TokenTransferManager()
    
    private val _uiState = MutableStateFlow(InvestmentAdvisorUiState())
    val uiState: StateFlow<InvestmentAdvisorUiState> = _uiState.asStateFlow()
    
    init {
        loadPortfolioData()
        observeAdvisorState()
        checkMarketCondition()
    }
    
    /**
     * 載入投資組合數據
     * 從區塊鏈獲取真實的資產數據
     */
    private fun loadPortfolioData() {
        viewModelScope.launch {
            try {
                // 獲取活躍錢包
                val walletResult = walletRepository.getActiveWallet()
                val wallet = when (walletResult) {
                    is Result.Success -> walletResult.data
                    else -> null
                }
                
                if (wallet == null) {
                    Logger.w(TAG, "No active wallet found, showing empty portfolio")
                    _uiState.update { state ->
                        state.copy(
                            portfolioValue = BigDecimal.ZERO,
                            dailyChange = BigDecimal.ZERO,
                            portfolio = Portfolio(
                                totalValue = BigDecimal.ZERO,
                                availableCapital = BigDecimal.ZERO,
                                assets = emptyList()
                            ),
                            errorMessage = "請先創建或導入錢包"
                        )
                    }
                    return@launch
                }
                
                // 從區塊鏈獲取真實資產數據
                val portfolio = buildRealPortfolio(wallet.address)
                
                _uiState.update { state ->
                    state.copy(
                        portfolioValue = portfolio.totalValue,
                        dailyChange = calculateDailyChange(portfolio),
                        portfolio = portfolio
                    )
                }
                
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to load portfolio", e)
                _uiState.update { state ->
                    state.copy(
                        errorMessage = "載入投資組合失敗: ${e.message}",
                        portfolio = Portfolio(
                            totalValue = BigDecimal.ZERO,
                            availableCapital = BigDecimal.ZERO,
                            assets = emptyList()
                        )
                    )
                }
            }
        }
    }
    
    /**
     * 從區塊鏈構建真實的投資組合數據
     */
    private suspend fun buildRealPortfolio(walletAddress: String): Portfolio {
        val assets = mutableListOf<Asset>()
        var totalValue = BigDecimal.ZERO
        
        // 支援的主要鏈
        val supportedChains = listOf(
            Triple(MultiChainType.ETHEREUM, "ETH", "Ethereum"),
            Triple(MultiChainType.BSC, "BNB", "BNB"),
            Triple(MultiChainType.POLYGON, "MATIC", "Polygon")
        )
        
        for ((chainType, symbol, name) in supportedChains) {
            try {
                // 獲取原生代幣價格
                val priceResult = getTokenPriceUseCase.getPrice(symbol)
                val price = when (priceResult) {
                    is Result.Success -> BigDecimal(priceResult.data.toString())
                    else -> BigDecimal.ZERO
                }
                
                // 查詢原生代幣餘額 (ETH/BNB/MATIC)
                val domainChainType = multiChainTypeToDomainChainType(chainType)
                if (domainChainType != null) {
                    val nativeBalance = walletRepository.getNativeBalance(walletAddress, domainChainType)
                    if (nativeBalance > 0) {
                        val nativeValue = BigDecimal(nativeBalance.toString()) * price
                        totalValue += nativeValue
                        
                        assets.add(Asset(
                            symbol = symbol,
                            name = name,
                            value = nativeValue,
                            quantity = BigDecimal(nativeBalance.toString()),
                            isStablecoin = false,
                            volatility = 60f,
                            dailyVolume = BigDecimal("10000000000") // 原生代幣流動性高
                        ))
                        Logger.d(TAG, "$symbol balance: $nativeBalance, price: $price, value: $nativeValue")
                    }
                }
                
                // 查詢熱門 ERC20 代幣餘額
                val popularTokens = tokenManager.getPopularTokens(chainType)
                for (token in popularTokens.take(3)) { // 限制每鏈最多 3 個代幣
                    val balanceResult = tokenManager.getTokenBalance(
                        chainType = chainType,
                        tokenAddress = token.contractAddress,
                        walletAddress = walletAddress
                    )
                    
                    when (balanceResult) {
                        is Result.Success -> {
                            val balance = balanceResult.data
                            if (balance.balance > 0) {
                                val tokenPriceResult = getTokenPriceUseCase.getPrice(token.symbol)
                                val tokenPrice = when (tokenPriceResult) {
                                    is Result.Success -> BigDecimal(tokenPriceResult.data.toString())
                                    else -> BigDecimal.ZERO
                                }
                                
                                val value = BigDecimal(balance.balance.toString()) * tokenPrice
                                totalValue += value
                                
                                assets.add(Asset(
                                    symbol = token.symbol,
                                    name = token.name,
                                    value = value,
                                    quantity = BigDecimal(balance.balance.toString()),
                                    isStablecoin = token.symbol in listOf("USDT", "USDC", "DAI", "BUSD"),
                                    volatility = if (token.symbol in listOf("USDT", "USDC", "DAI", "BUSD")) 1f else 50f,
                                    dailyVolume = BigDecimal("1000000000") // 簡化處理
                                ))
                            }
                        }
                        else -> { /* 跳過失敗的查詢 */ }
                    }
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to load assets for $chainType", e)
            }
        }
        
        return Portfolio(
            totalValue = totalValue,
            availableCapital = totalValue * BigDecimal("0.1"), // 假設 10% 為可用資金
            assets = assets.sortedByDescending { it.value }
        )
    }
    
    /**
     * 觀察顧問狀態
     */
    private fun observeAdvisorState() {
        viewModelScope.launch {
            investmentAdvisor.advisorState.collect { advisorState ->
                _uiState.update { state ->
                    state.copy(
                        isAnalyzing = advisorState.isAnalyzing,
                        latestAdvice = advisorState.lastAdvice,
                        riskScore = advisorState.lastAdvice?.riskScore ?: state.riskScore
                    )
                }
            }
        }
    }
    
    /**
     * 檢查市場狀況
     */
    private fun checkMarketCondition() {
        viewModelScope.launch {
            try {
                // 這會觸發內部的市場分析
                _uiState.update { it.copy(lastMarketUpdate = System.currentTimeMillis()) }
                
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to check market condition", e)
            }
        }
    }
    
    /**
     * 生成新的投資建議
     */
    fun generateNewAdvice() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isAnalyzing = true, errorMessage = null) }
                
                val portfolio = _uiState.value.portfolio ?: getEmptyPortfolio()
                val riskTolerance = _uiState.value.riskTolerance
                val goals = _uiState.value.investmentGoals
                
                val result = investmentAdvisor.getInvestmentAdvice(
                    portfolio = portfolio,
                    riskTolerance = riskTolerance,
                    investmentGoals = goals
                )
                
                if (result.isSuccess) {
                    val advice = result.getOrNull()!!
                    _uiState.update { state ->
                        state.copy(
                            latestAdvice = advice,
                            currentMarketCondition = advice.marketCondition,
                            riskScore = advice.riskScore,
                            isAnalyzing = false
                        )
                    }
                    
                    Logger.d(TAG, "New advice generated with ${advice.recommendations.size} recommendations")
                    
                } else {
                    result.exceptionOrNull()?.let { exception ->
                        _uiState.update { state ->
                            state.copy(
                                isAnalyzing = false,
                                errorMessage = "生成建議失敗: ${exception.message}"
                            )
                        }
                    }
                }
                
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to generate advice", e)
                _uiState.update { state ->
                    state.copy(
                        isAnalyzing = false,
                        errorMessage = "生成建議失敗: ${e.message}"
                    )
                }
            }
        }
    }
    
    /**
     * 優化投資組合
     */
    fun optimizePortfolio() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isOptimizing = true, errorMessage = null) }
                
                val portfolio = _uiState.value.portfolio ?: getEmptyPortfolio()
                val riskTolerance = _uiState.value.riskTolerance
                
                // 生成包含優化建議的完整分析
                val result = investmentAdvisor.getInvestmentAdvice(
                    portfolio = portfolio,
                    riskTolerance = riskTolerance,
                    investmentGoals = listOf(
                        InvestmentGoal(
                            type = GoalType.WEALTH_BUILDING,
                            description = "優化資產配置",
                            targetAmount = null,
                            timeframe = TimeHorizon.MEDIUM_TERM
                        )
                    )
                )
                
                if (result.isSuccess) {
                    val advice = result.getOrNull()!!
                    _uiState.update { state ->
                        state.copy(
                            latestAdvice = advice,
                            isOptimizing = false,
                            showOptimizationResult = true
                        )
                    }
                    
                    Logger.d(TAG, "Portfolio optimization completed")
                    
                } else {
                    result.exceptionOrNull()?.let { exception ->
                        _uiState.update { state ->
                            state.copy(
                                isOptimizing = false,
                                errorMessage = "優化失敗: ${exception.message}"
                            )
                        }
                    }
                }
                
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to optimize portfolio", e)
                _uiState.update { state ->
                    state.copy(
                        isOptimizing = false,
                        errorMessage = "優化失敗: ${e.message}"
                    )
                }
            }
        }
    }
    
    /**
     * 顯示風險評估
     */
    fun showRiskAssessment() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(showRiskAssessment = true) }
                
                // 如果沒有最新建議，先生成
                if (_uiState.value.latestAdvice == null) {
                    generateNewAdvice()
                }
                
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to show risk assessment", e)
            }
        }
    }
    
    /**
     * 顯示績效報告
     */
    fun showPerformanceReport() {
        viewModelScope.launch {
            try {
                val portfolio = _uiState.value.portfolio ?: getEmptyPortfolio()
                
                val performance = investmentAdvisor.monitorPortfolioPerformance(
                    portfolio = portfolio,
                    benchmarkIndex = "BTC"
                )
                
                _uiState.update { state ->
                    state.copy(
                        portfolioPerformance = performance,
                        showPerformanceReport = true
                    )
                }
                
                Logger.d(TAG, "Performance report generated")
                
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to generate performance report", e)
                _uiState.update { state ->
                    state.copy(errorMessage = "生成報告失敗: ${e.message}")
                }
            }
        }
    }
    
    /**
     * 更新風險承受度
     */
    fun updateRiskTolerance(tolerance: RiskTolerance) {
        _uiState.update { state ->
            state.copy(riskTolerance = tolerance)
        }
        
        // 重新生成建議
        generateNewAdvice()
    }
    
    /**
     * 添加投資目標
     */
    fun addInvestmentGoal(goal: InvestmentGoal) {
        _uiState.update { state ->
            state.copy(
                investmentGoals = state.investmentGoals + goal
            )
        }
    }
    
    /**
     * 移除投資目標
     */
    fun removeInvestmentGoal(goal: InvestmentGoal) {
        _uiState.update { state ->
            state.copy(
                investmentGoals = state.investmentGoals - goal
            )
        }
    }
    
    /**
     * 預測價格趨勢
     */
    fun predictPrice(tokenSymbol: String, timeHorizon: TimeHorizon) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isPredicting = true) }
                
                val result = investmentAdvisor.predictPriceTrend(tokenSymbol, timeHorizon)
                
                if (result.isSuccess) {
                    val prediction = result.getOrNull()!!
                    _uiState.update { state ->
                        state.copy(
                            pricePredictions = state.pricePredictions + (tokenSymbol to prediction),
                            isPredicting = false
                        )
                    }
                    
                    Logger.d(TAG, "Price prediction generated for $tokenSymbol")
                    
                } else {
                    result.exceptionOrNull()?.let { exception ->
                        _uiState.update { state ->
                            state.copy(
                                isPredicting = false,
                                errorMessage = "預測失敗: ${exception.message}"
                            )
                        }
                    }
                }
                
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to predict price", e)
                _uiState.update { state ->
                    state.copy(
                        isPredicting = false,
                        errorMessage = "預測失敗: ${e.message}"
                    )
                }
            }
        }
    }
    
    /**
     * 生成稅務報告
     */
    fun generateTaxReport(year: Int) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isGeneratingReport = true) }
                
                // TODO: 從資料庫載入交易記錄
                val transactions = emptyList<Transaction>()
                
                val result = investmentAdvisor.generateTaxReport(transactions, year)
                
                if (result.isSuccess) {
                    val report = result.getOrNull()!!
                    _uiState.update { state ->
                        state.copy(
                            taxReport = report,
                            isGeneratingReport = false,
                            showTaxReport = true
                        )
                    }
                    
                    Logger.d(TAG, "Tax report generated for year $year")
                    
                } else {
                    result.exceptionOrNull()?.let { exception ->
                        _uiState.update { state ->
                            state.copy(
                                isGeneratingReport = false,
                                errorMessage = "生成報告失敗: ${exception.message}"
                            )
                        }
                    }
                }
                
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to generate tax report", e)
                _uiState.update { state ->
                    state.copy(
                        isGeneratingReport = false,
                        errorMessage = "生成報告失敗: ${e.message}"
                    )
                }
            }
        }
    }
    
    /**
     * 清除錯誤訊息
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
    
    /**
     * 關閉對話框
     */
    fun dismissDialogs() {
        _uiState.update { state ->
            state.copy(
                showRiskAssessment = false,
                showPerformanceReport = false,
                showOptimizationResult = false,
                showTaxReport = false
            )
        }
    }
    
    // === 輔助方法 ===
    
    /**
     * 返回空的投資組合（當沒有真實數據時使用）
     */
    private fun getEmptyPortfolio(): Portfolio {
        return Portfolio(
            totalValue = BigDecimal.ZERO,
            availableCapital = BigDecimal.ZERO,
            assets = emptyList()
        )
    }
    
    /**
     * 將 MultiChainType 轉換為 domain ChainType
     */
    private fun multiChainTypeToDomainChainType(multiChainType: MultiChainType): ChainType? {
        return when (multiChainType) {
            MultiChainType.ETHEREUM -> ChainType.ETHEREUM
            MultiChainType.BSC -> ChainType.BSC
            MultiChainType.POLYGON -> ChainType.POLYGON
            MultiChainType.ARBITRUM -> ChainType.ARBITRUM
            MultiChainType.OPTIMISM -> ChainType.OPTIMISM
            MultiChainType.AVALANCHE -> ChainType.AVALANCHE
            MultiChainType.FANTOM -> ChainType.FANTOM
            MultiChainType.CRONOS -> ChainType.CRONOS
            MultiChainType.BASE -> ChainType.BASE
            MultiChainType.CELO -> ChainType.CELO
            else -> null
        }
    }
    
    /**
     * 計算投資組合的日變化
     * 基於資產的價格變化計算
     */
    private fun calculateDailyChange(portfolio: Portfolio): BigDecimal {
        if (portfolio.assets.isEmpty()) {
            return BigDecimal.ZERO
        }
        
        // 基於價格的預估變化（真實數據需要歷史價格）
        // 目前返回 0，待實現完整的價格歷史查詢
        return BigDecimal.ZERO
    }
}

/**
 * 投資顧問 UI 狀態
 */
data class InvestmentAdvisorUiState(
    val isAnalyzing: Boolean = false,
    val isOptimizing: Boolean = false,
    val isPredicting: Boolean = false,
    val isGeneratingReport: Boolean = false,
    val portfolioValue: BigDecimal = BigDecimal.ZERO,
    val dailyChange: BigDecimal = BigDecimal.ZERO,
    val riskScore: Float = 50f,
    val currentMarketCondition: FirebaseAIInvestmentAdvisor.Companion.MarketCondition = 
        FirebaseAIInvestmentAdvisor.Companion.MarketCondition.UNCERTAIN,
    val lastMarketUpdate: Long = 0,
    val portfolio: Portfolio? = null,
    val latestAdvice: InvestmentAdvice? = null,
    val riskTolerance: RiskTolerance = RiskTolerance.MODERATE,
    val investmentGoals: List<InvestmentGoal> = listOf(
        InvestmentGoal(
            type = GoalType.WEALTH_BUILDING,
            description = "長期財富增長",
            targetAmount = BigDecimal("100000"),
            timeframe = TimeHorizon.LONG_TERM
        )
    ),
    val portfolioPerformance: PortfolioPerformance? = null,
    val pricePredictions: Map<String, PricePrediction> = emptyMap(),
    val taxReport: TaxReport? = null,
    val showRiskAssessment: Boolean = false,
    val showPerformanceReport: Boolean = false,
    val showOptimizationResult: Boolean = false,
    val showTaxReport: Boolean = false,
    val errorMessage: String? = null
)

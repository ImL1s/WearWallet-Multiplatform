package com.cbstudio.wearwallet.presentation.ai

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.WalletAccount
import com.cbstudio.wearwallet.core.domain.model.WalletType
import com.cbstudio.wearwallet.core.domain.repository.WalletRepository
import com.cbstudio.wearwallet.core.domain.usecase.price.GetTokenPriceUseCase
import com.cbstudio.wearwallet.firebase.*
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import java.math.BigDecimal
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class InvestmentAdvisorViewModelTest : KoinTest {

    private lateinit var viewModel: InvestmentAdvisorViewModel
    private lateinit var investmentAdvisor: FirebaseAIInvestmentAdvisor
    private lateinit var walletRepository: WalletRepository
    private lateinit var getTokenPriceUseCase: GetTokenPriceUseCase
    
    private val testDispatcher = StandardTestDispatcher()

    private val mockWallet = WalletAccount(
        id = "wallet-1",
        name = "Test Wallet",
        address = "0x71C7656EC7ab88b098defB751B7401B5f6d8976F",
        publicKey = "0xpubkey",
        chainType = ChainType.ETHEREUM,
        walletType = WalletType.HOT_WALLET
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        investmentAdvisor = mockk(relaxed = true)
        walletRepository = mockk(relaxed = true)
        getTokenPriceUseCase = mockk(relaxed = true)
        
        // Mock the advisorState flow
        every { investmentAdvisor.advisorState } returns MutableStateFlow(InvestmentAdvisorState())
        
        startKoin {
            modules(module {
                single { walletRepository }
                single { getTokenPriceUseCase }
            })
        }
        
        coEvery { walletRepository.getActiveWallet() } returns Result.Success(mockWallet)
        coEvery { getTokenPriceUseCase.getPrice(any()) } returns Result.Success(2500.0)
        coEvery { walletRepository.getNativeBalance(any(), any()) } returns 1.5
        
        viewModel = InvestmentAdvisorViewModel(investmentAdvisor)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        stopKoin()
    }

    @Test
    fun `init should load portfolio data`() = runTest {
        testScheduler.advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertNotNull(state.portfolio)
        assertTrue(state.portfolioValue > BigDecimal.ZERO)
        assertNull(state.errorMessage)
    }

    @Test
    fun `loadPortfolioData no wallet should set error`() = runTest {
        coEvery { walletRepository.getActiveWallet() } returns Result.Success(null)
        
        viewModel = InvestmentAdvisorViewModel(investmentAdvisor)
        testScheduler.advanceUntilIdle()
        
        assertEquals("請先創建或導入錢包", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `generateNewAdvice success should update latestAdvice`() = runTest {
        val mockAdvice = InvestmentAdvice(
            timestamp = 12345L,
            marketCondition = FirebaseAIInvestmentAdvisor.Companion.MarketCondition.BULL_MARKET,
            riskScore = 30f,
            recommendations = listOf("Buy low", "Sell high"),
            suggestedStrategies = emptyList(),
            optimizedAllocation = emptyMap(),
            nextReviewDate = 67890L,
            confidence = 85f
        )
        
        coEvery { investmentAdvisor.getInvestmentAdvice(any(), any(), any()) } returns kotlin.Result.success(mockAdvice)
        
        // When
        viewModel.generateNewAdvice()
        testScheduler.advanceUntilIdle()
        
        // Then
        assertEquals(mockAdvice, viewModel.uiState.value.latestAdvice)
        assertFalse(viewModel.uiState.value.isAnalyzing)
    }

    @Test
    fun `optimizePortfolio success should show optimization result`() = runTest {
        val mockAdvice = mockk<InvestmentAdvice>(relaxed = true)
        coEvery { investmentAdvisor.getInvestmentAdvice(any(), any(), any()) } returns kotlin.Result.success(mockAdvice)
        
        // When
        viewModel.optimizePortfolio()
        testScheduler.advanceUntilIdle()
        
        // Then
        assertTrue(viewModel.uiState.value.showOptimizationResult)
        assertFalse(viewModel.uiState.value.isOptimizing)
    }

    @Test
    fun `showPerformanceReport should update portfolioPerformance`() = runTest {
        val mockPerformance = PortfolioPerformance(
            dailyReturn = BigDecimal("2.5"),
            lastUpdated = System.currentTimeMillis()
        )
        coEvery { investmentAdvisor.monitorPortfolioPerformance(any(), any()) } returns mockPerformance
        
        // When
        viewModel.showPerformanceReport()
        testScheduler.advanceUntilIdle()
        
        // Then
        assertEquals(mockPerformance, viewModel.uiState.value.portfolioPerformance)
        assertTrue(viewModel.uiState.value.showPerformanceReport)
    }

    @Test
    fun `predictPrice success should add prediction to map`() = runTest {
        val mockPrediction = PricePrediction(
            token = "ETH",
            direction = "UP",
            expectedChange = BigDecimal("5.0"),
            confidence = 80f,
            supportLevel = BigDecimal("2400"),
            resistanceLevel = BigDecimal("2600"),
            timeHorizon = TimeHorizon.SHORT_TERM,
            generatedAt = System.currentTimeMillis()
        )
        coEvery { investmentAdvisor.predictPriceTrend("ETH", any()) } returns kotlin.Result.success(mockPrediction)
        
        // When
        viewModel.predictPrice("ETH", TimeHorizon.SHORT_TERM)
        testScheduler.advanceUntilIdle()
        
        // Then
        assertTrue(viewModel.uiState.value.pricePredictions.containsKey("ETH"))
        assertEquals(mockPrediction, viewModel.uiState.value.pricePredictions["ETH"])
    }

    @Test
    fun `generateTaxReport success should show tax report`() = runTest {
        val mockReport = TaxReport(
            taxYear = 2024,
            capitalGains = BigDecimal("1000"),
            capitalLosses = BigDecimal("200"),
            netGainLoss = BigDecimal("800"),
            shortTermGains = BigDecimal("800"),
            longTermGains = BigDecimal.ZERO,
            miningIncome = BigDecimal.ZERO,
            stakingRewards = BigDecimal.ZERO,
            defiYield = BigDecimal.ZERO,
            totalTaxableIncome = BigDecimal("800"),
            generatedAt = System.currentTimeMillis()
        )
        coEvery { investmentAdvisor.generateTaxReport(any(), any()) } returns kotlin.Result.success(mockReport)
        
        // When
        viewModel.generateTaxReport(2024)
        testScheduler.advanceUntilIdle()
        
        // Then
        assertEquals(mockReport, viewModel.uiState.value.taxReport)
        assertTrue(viewModel.uiState.value.showTaxReport)
    }
}

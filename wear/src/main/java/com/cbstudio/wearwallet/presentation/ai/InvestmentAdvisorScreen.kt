package com.cbstudio.wearwallet.presentation.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import androidx.wear.compose.material.*
import com.cbstudio.wearwallet.firebase.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

/**
 * AI 投資顧問畫面
 * 
 * 提供個人化投資建議、市場分析和投資組合優化
 */
@Composable
fun InvestmentAdvisorScreen(
    onNavigateBack: () -> Unit,
    onNavigateToStrategy: (String) -> Unit,
    onNavigateToPortfolio: () -> Unit,
    viewModel: InvestmentAdvisorViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colors.surface,
                        MaterialTheme.colors.surface.copy(alpha = 0.9f)
                    )
                )
            )
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 32.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 標題
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colors.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "AI 投資顧問",
                        style = MaterialTheme.typography.title2,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            // 市場狀況卡片
            item {
                MarketConditionCard(
                    condition = uiState.currentMarketCondition,
                    lastUpdate = uiState.lastMarketUpdate
                )
            }
            
            // 投資組合摘要
            item {
                PortfolioSummaryCard(
                    totalValue = uiState.portfolioValue,
                    dailyChange = uiState.dailyChange,
                    riskScore = uiState.riskScore,
                    onClick = onNavigateToPortfolio
                )
            }
            
            // AI 建議區塊
            val advice = uiState.latestAdvice
            if (advice != null) {
                item {
                    Text(
                        text = "投資建議",
                        style = MaterialTheme.typography.caption1,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                
                items(advice.recommendations.size) { index ->
                    RecommendationCard(
                        recommendation = advice.recommendations[index],
                        index = index + 1
                    )
                }
                
                // 推薦策略
                if (advice.suggestedStrategies.isNotEmpty()) {
                    item {
                        Text(
                            text = "推薦 DeFi 策略",
                            style = MaterialTheme.typography.caption1,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                    
                    items(advice.suggestedStrategies.size) { index ->
                        val strategy = advice.suggestedStrategies[index]
                        SuggestedStrategyCard(
                            strategy = strategy,
                            onClick = { onNavigateToStrategy(strategy.id) }
                        )
                    }
                }
            }
            
            // 功能按鈕
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            item {
                Chip(
                    onClick = {
                        coroutineScope.launch {
                            viewModel.generateNewAdvice()
                        }
                    },
                    label = {
                        if (uiState.isAnalyzing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("獲取新建議")
                        }
                    },
                    icon = {
                        if (!uiState.isAnalyzing) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null)
                        }
                    },
                    enabled = !uiState.isAnalyzing,
                    colors = ChipDefaults.primaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            item {
                Chip(
                    onClick = {
                        coroutineScope.launch {
                            viewModel.optimizePortfolio()
                        }
                    },
                    label = { Text("優化投資組合") },
                    icon = { Icon(Icons.Default.TrendingUp, contentDescription = null) },
                    enabled = !uiState.isOptimizing,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            item {
                Chip(
                    onClick = { viewModel.showRiskAssessment() },
                    label = { Text("風險評估") },
                    icon = { Icon(Icons.Default.Shield, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            item {
                Chip(
                    onClick = { viewModel.showPerformanceReport() },
                    label = { Text("績效報告") },
                    icon = { Icon(Icons.Default.Assessment, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            item {
                Chip(
                    onClick = onNavigateBack,
                    label = { Text("返回") },
                    icon = { Icon(Icons.Default.ArrowBack, contentDescription = null) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        
        // 載入動畫
        AnimatedVisibility(
            visible = uiState.isAnalyzing || uiState.isOptimizing,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colors.surface.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (uiState.isAnalyzing) "分析中..." else "優化中...",
                        style = MaterialTheme.typography.body2
                    )
                }
            }
        }
        
        // 錯誤提示 (使用簡單的錯誤顯示)
        uiState.errorMessage?.let { error ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .background(
                        MaterialTheme.colors.error,
                        MaterialTheme.shapes.medium
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onError
                )
            }
        }
    }
}

/**
 * 市場狀況卡片
 */
@Composable
private fun MarketConditionCard(
    condition: FirebaseAIInvestmentAdvisor.Companion.MarketCondition,
    lastUpdate: Long
) {
    Card(
        onClick = {},
        enabled = false,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "市場狀況",
                    style = MaterialTheme.typography.caption2,
                    color = MaterialTheme.colors.onSurfaceVariant
                )
                
                val (emoji, color) = when (condition) {
                    FirebaseAIInvestmentAdvisor.Companion.MarketCondition.BULL_MARKET -> 
                        "🐂" to Color.Green
                    FirebaseAIInvestmentAdvisor.Companion.MarketCondition.BEAR_MARKET -> 
                        "🐻" to Color.Red
                    FirebaseAIInvestmentAdvisor.Companion.MarketCondition.SIDEWAYS -> 
                        "➡️" to Color.Yellow
                    FirebaseAIInvestmentAdvisor.Companion.MarketCondition.VOLATILE -> 
                        "📊" to Color(0xFFFF9800)
                    FirebaseAIInvestmentAdvisor.Companion.MarketCondition.UNCERTAIN -> 
                        "❓" to Color.Gray
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(emoji)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = getMarketConditionText(condition),
                        style = MaterialTheme.typography.body2,
                        color = color,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            if (lastUpdate > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "更新於 ${formatTime(lastUpdate)}",
                    style = MaterialTheme.typography.caption3,
                    color = MaterialTheme.colors.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 投資組合摘要卡片
 */
@Composable
private fun PortfolioSummaryCard(
    totalValue: BigDecimal,
    dailyChange: BigDecimal,
    riskScore: Float,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "投資組合",
                style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = formatCurrency(totalValue),
                style = MaterialTheme.typography.title3,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 日變化
                Text(
                    text = "${if (dailyChange >= BigDecimal.ZERO) "+" else ""}${formatCurrency(dailyChange)}",
                    style = MaterialTheme.typography.body2,
                    color = if (dailyChange >= BigDecimal.ZERO) Color.Green else Color.Red
                )
                
                // 風險評分
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = getRiskColor(riskScore)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${riskScore.toInt()}/100",
                        style = MaterialTheme.typography.caption3,
                        color = getRiskColor(riskScore)
                    )
                }
            }
        }
    }
}

/**
 * 建議卡片
 */
@Composable
private fun RecommendationCard(
    recommendation: String,
    index: Int
) {
    Card(
        onClick = {},
        enabled = false,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(
                        MaterialTheme.colors.primary,
                        MaterialTheme.shapes.small
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = index.toString(),
                    style = MaterialTheme.typography.caption3,
                    color = MaterialTheme.colors.onPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Text(
                text = recommendation,
                style = MaterialTheme.typography.body2,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * 推薦策略卡片
 */
@Composable
private fun SuggestedStrategyCard(
    strategy: com.cbstudio.wearwallet.defi.StrategyTemplate,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strategy.name,
                        style = MaterialTheme.typography.body2,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = strategy.protocol.name,
                        style = MaterialTheme.typography.caption3,
                        color = MaterialTheme.colors.onSurfaceVariant
                    )
                }
                
                Text(
                    text = "${strategy.estimatedAPR}% APR",
                    style = MaterialTheme.typography.body2,
                    color = Color.Green,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "最低 ${formatCurrency(strategy.minInvestment)}",
                    style = MaterialTheme.typography.caption3,
                    color = MaterialTheme.colors.onSurfaceVariant
                )
                
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colors.primary
                )
            }
        }
    }
}

// 輔助函數

private fun getMarketConditionText(condition: FirebaseAIInvestmentAdvisor.Companion.MarketCondition): String {
    return when (condition) {
        FirebaseAIInvestmentAdvisor.Companion.MarketCondition.BULL_MARKET -> "牛市"
        FirebaseAIInvestmentAdvisor.Companion.MarketCondition.BEAR_MARKET -> "熊市"
        FirebaseAIInvestmentAdvisor.Companion.MarketCondition.SIDEWAYS -> "橫盤"
        FirebaseAIInvestmentAdvisor.Companion.MarketCondition.VOLATILE -> "高波動"
        FirebaseAIInvestmentAdvisor.Companion.MarketCondition.UNCERTAIN -> "不確定"
    }
}

private fun formatCurrency(amount: BigDecimal): String {
    val formatter = DecimalFormat("$#,##0.00")
    return formatter.format(amount)
}

private fun formatTime(timestamp: Long): String {
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    return formatter.format(Date(timestamp))
}

private fun getRiskColor(riskScore: Float): Color {
    return when {
        riskScore < 30 -> Color.Green
        riskScore < 60 -> Color.Yellow
        riskScore < 80 -> Color(0xFFFF9800)
        else -> Color.Red
    }
}

package com.cbstudio.wearwallet.presentation.wallet.screens.main.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.TrendingUp

/**
 * AI 投資顧問入口卡片
 * 
 * 顯示在錢包主畫面的 AI 投資顧問快速入口
 */
@Composable
fun AIInvestmentAdvisorCard(
    onNavigateToAIAdvisor: () -> Unit,
    enabled: Boolean = true,
    portfolioScore: Int? = null,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ai_pulse")
    
    // 脈動效果
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    // 光暈效果
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )
    
    Card(
        onClick = onNavigateToAIAdvisor,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .scale(scale),
        colors = CardDefaults.cardColors(
            containerColor = Color.DarkGray
        )
    ) {
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            // 背景漸變效果
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .alpha(glowAlpha)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Blue.copy(alpha = 0.2f),
                                Color.Blue.copy(alpha = 0f)
                            )
                        )
                    )
            )
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // AI 圖標
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = Color.Blue.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = Color.Blue
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = "AI 投資顧問",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "智能投資策略分析",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray,
                    textAlign = TextAlign.Center
                )
                
                // 投資組合健康度指標
                if (portfolioScore != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.TrendingUp,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = when {
                                portfolioScore >= 80 -> Color.Green
                                portfolioScore >= 60 -> Color.Yellow
                                else -> Color.Red
                            }
                        )
                        
                        Spacer(modifier = Modifier.width(4.dp))
                        
                        Text(
                            text = "健康度: $portfolioScore%",
                            style = MaterialTheme.typography.labelSmall,
                            color = when {
                                portfolioScore >= 80 -> Color.Green
                                portfolioScore >= 60 -> Color.Yellow
                                else -> Color.Red
                            }
                        )
                    }
                }
            }
            
            // 點擊提示
            if (enabled) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(8.dp)
                        .background(
                            color = Color.Blue,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .alpha(glowAlpha)
                )
            }
        }
    }
}

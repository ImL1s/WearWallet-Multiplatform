package com.cbstudio.wearwallet.presentation.wallet.screens.main.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.*

/**
 * WearFi 健康挖礦入口卡片
 * Material 3 Expressive 設計
 */
@Composable
fun WearFiEntryCard(
    onNavigateToWearFi: () -> Unit,
    enabled: Boolean = true,
    pendingRewards: String? = null
) {
    // 脈動動畫
    val infiniteTransition = rememberInfiniteTransition()
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    Card(
        onClick = onNavigateToWearFi,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        backgroundPainter = CardDefaults.cardBackgroundPainter(
            startBackgroundColor = MaterialTheme.colors.surface,
            endBackgroundColor = MaterialTheme.colors.surface.copy(alpha = 0.8f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF4CAF50).copy(alpha = 0.1f),
                            Color(0xFF81C784).copy(alpha = 0.1f)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    // 圖標
                    Text(
                        text = "🏃",
                        style = MaterialTheme.typography.title1.copy(
                            fontSize = 32.sp
                        ),
                        modifier = Modifier.scale(pulseScale)
                    )
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    // 標題
                    Column {
                        Text(
                            text = "WearFi 健康挖礦",
                            style = MaterialTheme.typography.title3.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colors.onSurface
                        )
                        
                        Text(
                            text = "將健康數據轉換為代幣",
                            style = MaterialTheme.typography.caption2,
                            color = MaterialTheme.colors.onSurfaceVariant
                        )
                    }
                }
                
                // 待領取獎勵提示
                if (pendingRewards != null && pendingRewards != "0") {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colors.secondary.copy(alpha = 0.2f),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "💎",
                            style = MaterialTheme.typography.caption1
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$pendingRewards HEALTH 待領取",
                            style = MaterialTheme.typography.caption1.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colors.secondary
                        )
                    }
                }
            }
            
            // 右側箭頭提示
            if (enabled) {
                Text(
                    text = "→",
                    style = MaterialTheme.typography.title2,
                    color = MaterialTheme.colors.primary,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 16.dp)
                )
            }
        }
    }
}

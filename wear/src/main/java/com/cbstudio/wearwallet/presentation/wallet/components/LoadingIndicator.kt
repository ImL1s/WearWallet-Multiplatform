package com.cbstudio.wearwallet.presentation.wallet.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * 統一的載入指示器組件
 * 支援多種樣式和自定義訊息
 */
@Composable
fun LoadingIndicator(
    message: String? = null,
    modifier: Modifier = Modifier,
    style: LoadingStyle = LoadingStyle.CIRCULAR
) {
    when (style) {
        LoadingStyle.CIRCULAR -> CircularLoadingIndicator(message, modifier)
        LoadingStyle.DOTS -> DotsLoadingIndicator(message, modifier)
        LoadingStyle.PULSE -> PulseLoadingIndicator(message, modifier)
    }
}

/**
 * 圓形載入指示器
 */
@Composable
private fun CircularLoadingIndicator(
    message: String?,
    modifier: Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF58A6FF).copy(alpha = 0.1f),
                            Color.Transparent
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = Color(0xFF58A6FF),
                strokeWidth = 3.dp
            )
        }
        
        if (message != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * 點點載入指示器
 */
@Composable
private fun DotsLoadingIndicator(
    message: String?,
    modifier: Modifier
) {
    val infiniteTransition = rememberInfiniteTransition()
    
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(3) { index ->
                val delay = index * 100
                val scale by infiniteTransition.animateFloat(
                    initialValue = 0.8f,
                    targetValue = 1.2f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600, delayMillis = delay),
                        repeatMode = RepeatMode.Reverse
                    )
                )
                
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .scale(scale)
                        .clip(CircleShape)
                        .background(
                            Color(0xFF58A6FF).copy(
                                alpha = 0.3f + (scale - 0.8f)
                            )
                        )
                )
            }
        }
        
        if (message != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * 脈動載入指示器
 */
@Composable
private fun PulseLoadingIndicator(
    message: String?,
    modifier: Modifier
) {
    val infiniteTransition = rememberInfiniteTransition()
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF58A6FF).copy(alpha = alpha),
                            Color(0xFF58A6FF).copy(alpha = alpha * 0.3f),
                            Color.Transparent
                        )
                    )
                )
        )
        
        if (message != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * 載入樣式枚舉
 */
enum class LoadingStyle {
    CIRCULAR,  // 圓形進度條
    DOTS,      // 三個點動畫
    PULSE      // 脈動效果
}

/**
 * 骨架屏載入效果
 */
@Composable
fun SkeletonLoader(
    modifier: Modifier = Modifier,
    count: Int = 3
) {
    val infiniteTransition = rememberInfiniteTransition()
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(count) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = alpha)
                )
            ) {
                // 空的骨架
            }
        }
    }
}
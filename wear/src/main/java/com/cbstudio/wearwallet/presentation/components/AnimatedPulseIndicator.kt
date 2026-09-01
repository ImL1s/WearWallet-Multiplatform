package com.cbstudio.wearwallet.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 動畫脈動指示器
 * 用於顯示 AI 處理中或其他活動狀態
 */
@Composable
fun AnimatedPulseIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    color: Color = Color.White,
    duration: Int = 1500
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    
    // 脈動縮放動畫
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(duration / 2, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    // 透明度動畫
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(duration / 2, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // 外圈脈動效果
        Box(
            modifier = Modifier
                .size(size)
                .scale(scale)
                .alpha(alpha * 0.5f)
                .background(
                    color = color.copy(alpha = 0.3f),
                    shape = CircleShape
                )
        )
        
        // 內圈脈動效果
        Box(
            modifier = Modifier
                .size(size * 0.7f)
                .scale(scale * 0.9f)
                .alpha(alpha)
                .background(
                    color = color.copy(alpha = 0.6f),
                    shape = CircleShape
                )
        )
        
        // 中心點
        Box(
            modifier = Modifier
                .size(size * 0.4f)
                .background(
                    color = color,
                    shape = CircleShape
                )
        )
    }
}

/**
 * 多重脈動指示器
 * 顯示多個同步脈動的點
 */
@Composable
fun MultiPulseIndicator(
    modifier: Modifier = Modifier,
    count: Int = 3,
    size: Dp = 8.dp,
    spacing: Dp = 4.dp,
    color: Color = Color.White,
    duration: Int = 1200
) {
    val infiniteTransition = rememberInfiniteTransition(label = "multi_pulse")
    
    androidx.compose.foundation.layout.Row(
        modifier = modifier,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(spacing)
    ) {
        repeat(count) { index ->
            val delay = (duration / count) * index
            
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.5f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = duration,
                        delayMillis = delay,
                        easing = FastOutSlowInEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "scale_$index"
            )
            
            Box(
                modifier = Modifier
                    .size(size)
                    .scale(scale)
                    .background(
                        color = color,
                        shape = CircleShape
                    )
            )
        }
    }
}

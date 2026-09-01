package com.cbstudio.wearwallet.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text

/**
 * 動畫健康指標組件
 * Material 3 Expressive 風格
 */
@Composable
fun AnimatedHealthMetric(
    label: String,
    value: String,
    progress: Float,
    icon: String,
    modifier: Modifier = Modifier
) {
    // 動畫進度
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                drawProgressBackground()
                drawProgress(animatedProgress)
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 圖標
        Text(
            text = icon,
            style = MaterialTheme.typography.body1.copy(
                fontSize = 20.sp
            )
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // 標籤和數值
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.body1.copy(
                    fontWeight = FontWeight.Bold
                )
            )
        }
        
        // 進度百分比
        Text(
            text = "${(animatedProgress * 100).toInt()}%",
            style = MaterialTheme.typography.caption1,
            color = MaterialTheme.colors.secondary
        )
    }
}

/**
 * 繪製進度條背景
 */
private fun DrawScope.drawProgressBackground() {
    val strokeWidth = 2.dp.toPx()
    val y = size.height - strokeWidth / 2
    
    drawLine(
        color = Color.White.copy(alpha = 0.1f),
        start = Offset(0f, y),
        end = Offset(size.width, y),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
}

/**
 * 繪製進度條
 */
private fun DrawScope.drawProgress(progress: Float) {
    val strokeWidth = 2.dp.toPx()
    val y = size.height - strokeWidth / 2
    val progressWidth = size.width * progress
    
    if (progressWidth > 0) {
        // 主進度條
        drawLine(
            color = Color(0xFF4CAF50),
            start = Offset(0f, y),
            end = Offset(progressWidth, y),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        
        // 發光效果
        drawLine(
            color = Color(0xFF4CAF50).copy(alpha = 0.3f),
            start = Offset(0f, y),
            end = Offset(progressWidth, y),
            strokeWidth = strokeWidth * 2,
            cap = StrokeCap.Round
        )
    }
}

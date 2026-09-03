package com.cbstudio.wearwallet.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp

/**
 * 成功動畫組件
 * 顯示一個打勾的動畫效果
 */
@Composable
fun SuccessAnimation(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    size: androidx.compose.ui.unit.Dp = 80.dp
) {
    val transition = rememberInfiniteTransition(label = "success")
    
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "progress"
    )
    
    Canvas(
        modifier = modifier.size(size)
    ) {
        val strokeWidth = size.toPx() * 0.08f
        val radius = (size.toPx() - strokeWidth) / 2
        val center = Offset(size.toPx() / 2, size.toPx() / 2)
        
        // 畫圓圈
        drawCircle(
            color = color.copy(alpha = 0.3f),
            radius = radius,
            center = center,
            style = Stroke(width = strokeWidth)
        )
        
        // 畫動態圓弧
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 360f * progress,
            useCenter = false,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
            size = androidx.compose.ui.geometry.Size(
                size.toPx() - strokeWidth,
                size.toPx() - strokeWidth
            )
        )
        
        // 畫打勾
        if (progress > 0.5f) {
            val checkProgress = ((progress - 0.5f) * 2).coerceIn(0f, 1f)
            val checkStartX = center.x - radius * 0.3f
            val checkStartY = center.y
            val checkMidX = center.x - radius * 0.1f
            val checkMidY = center.y + radius * 0.3f
            val checkEndX = center.x + radius * 0.4f
            val checkEndY = center.y - radius * 0.2f
            
            // 第一段線
            if (checkProgress > 0f) {
                val firstProgress = (checkProgress * 2).coerceIn(0f, 1f)
                drawLine(
                    color = color,
                    start = Offset(checkStartX, checkStartY),
                    end = Offset(
                        checkStartX + (checkMidX - checkStartX) * firstProgress,
                        checkStartY + (checkMidY - checkStartY) * firstProgress
                    ),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }
            
            // 第二段線
            if (checkProgress > 0.5f) {
                val secondProgress = ((checkProgress - 0.5f) * 2).coerceIn(0f, 1f)
                drawLine(
                    color = color,
                    start = Offset(checkMidX, checkMidY),
                    end = Offset(
                        checkMidX + (checkEndX - checkMidX) * secondProgress,
                        checkMidY + (checkEndY - checkMidY) * secondProgress
                    ),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

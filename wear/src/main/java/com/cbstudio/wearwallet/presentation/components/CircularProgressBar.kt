package com.cbstudio.wearwallet.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 圓形進度條組件
 */
@Composable
fun CircularProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 4.dp,
    backgroundColor: Color = Color.Gray.copy(alpha = 0.3f),
    progressColor: Color = Color.Blue,
    startAngle: Float = -90f
) {
    Canvas(modifier = modifier) {
        val stroke = Stroke(
            width = strokeWidth.toPx(),
            cap = StrokeCap.Round
        )
        
        val diameter = size.minDimension
        val radius = diameter / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        
        // 繪製背景圓弧
        drawCircle(
            color = backgroundColor,
            radius = radius - stroke.width / 2f,
            center = center,
            style = stroke
        )
        
        // 繪製進度圓弧
        val sweepAngle = progress * 360f
        drawArc(
            color = progressColor,
            startAngle = startAngle,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = Offset(
                center.x - radius + stroke.width / 2f,
                center.y - radius + stroke.width / 2f
            ),
            size = Size(
                diameter - stroke.width,
                diameter - stroke.width
            ),
            style = stroke
        )
    }
}

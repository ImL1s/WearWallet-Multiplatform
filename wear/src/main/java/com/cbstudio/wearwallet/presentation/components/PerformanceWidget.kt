package com.cbstudio.wearwallet.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.*
import kotlin.random.Random

/**
 * 迷你效能小工具
 */
@Composable
fun MiniPerformanceWidget(
    cpuUsage: Float,
    memoryUsage: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cpuColor by animateColorAsState(
        targetValue = getMetricColor(cpuUsage),
        animationSpec = tween(500)
    )
    
    val memoryColor by animateColorAsState(
        targetValue = getMetricColor(memoryUsage),
        animationSpec = tween(500)
    )
    
    Box(
        modifier = modifier
            .size(60.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        MaterialTheme.colors.surface.copy(alpha = 0.8f),
                        MaterialTheme.colors.surface.copy(alpha = 0.4f)
                    )
                )
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            // CPU 圓弧
            drawArc(
                color = cpuColor,
                startAngle = -90f,
                sweepAngle = 180f * cpuUsage,
                useCenter = false,
                topLeft = Offset.Zero,
                size = size,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )
            
            // 記憶體圓弧
            drawArc(
                color = memoryColor,
                startAngle = 90f,
                sweepAngle = 180f * memoryUsage,
                useCenter = false,
                topLeft = Offset.Zero,
                size = size,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "${(cpuUsage * 100).toInt()}%",
                style = MaterialTheme.typography.caption3,
                fontSize = 10.sp
            )
            Text(
                text = "${(memoryUsage * 100).toInt()}%",
                style = MaterialTheme.typography.caption3,
                fontSize = 10.sp
            )
        }
    }
}

/**
 * 即時效能圖表
 */
@Composable
fun RealtimePerformanceChart(
    modifier: Modifier = Modifier,
    maxDataPoints: Int = 30
) {
    var dataPoints by remember { mutableStateOf(listOf<Float>()) }
    
    // 模擬即時數據更新
    LaunchedEffect(Unit) {
        while (isActive) {
            val newValue = Random.nextFloat() * 0.3f + 0.3f // 30% - 60%
            dataPoints = (dataPoints + newValue).takeLast(maxDataPoints)
            delay(1000)
        }
    }
    
    val infiniteTransition = rememberInfiniteTransition()
    val animatedAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    Box(
        modifier = modifier
            .height(80.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colors.surface.copy(alpha = 0.3f))
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
        ) {
            if (dataPoints.size > 1) {
                drawPerformanceLine(dataPoints, animatedAlpha)
            }
        }
        
        // 標籤
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "0%",
                style = MaterialTheme.typography.caption3,
                fontSize = 8.sp
            )
            Text(
                text = "100%",
                style = MaterialTheme.typography.caption3,
                fontSize = 8.sp
            )
        }
    }
}

/**
 * 繪製效能折線圖
 */
fun DrawScope.drawPerformanceLine(
    dataPoints: List<Float>,
    alpha: Float
) {
    if (dataPoints.isEmpty()) return
    
    val path = Path()
    val width = size.width
    val height = size.height
    val pointSpacing = width / (dataPoints.size - 1)
    
    // 創建路徑
    dataPoints.forEachIndexed { index, value ->
        val x = index * pointSpacing
        val y = height - (value * height)
        
        if (index == 0) {
            path.moveTo(x, y)
        } else {
            // 平滑曲線
            val prevX = (index - 1) * pointSpacing
            val prevY = height - (dataPoints[index - 1] * height)
            val controlX = (prevX + x) / 2
            
            path.cubicTo(
                controlX, prevY,
                controlX, y,
                x, y
            )
        }
    }
    
    // 繪製漸變填充
    val fillPath = Path().apply {
        addPath(path)
        lineTo(width, height)
        lineTo(0f, height)
        close()
    }
    
    drawPath(
        path = fillPath,
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.Green.copy(alpha = 0.3f * alpha),
                Color.Transparent
            )
        )
    )
    
    // 繪製線條
    drawPath(
        path = path,
        color = Color.Green.copy(alpha = alpha),
        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
    )
    
    // 繪製數據點
    dataPoints.forEachIndexed { index, value ->
        val x = index * pointSpacing
        val y = height - (value * height)
        
        drawCircle(
            color = Color.Green,
            radius = 2.dp.toPx(),
            center = Offset(x, y)
        )
    }
}

/**
 * 效能狀態指示器
 */
@Composable
fun PerformanceStatusIndicator(
    status: PerformanceStatus,
    modifier: Modifier = Modifier
) {
    val color = when (status) {
        PerformanceStatus.EXCELLENT -> Color(0xFF4CAF50)
        PerformanceStatus.GOOD -> Color(0xFF8BC34A)
        PerformanceStatus.FAIR -> Color(0xFFFFC107)
        PerformanceStatus.POOR -> Color(0xFFFF9800)
        PerformanceStatus.CRITICAL -> Color(0xFFF44336)
    }
    
    val pulseAnimation = rememberInfiniteTransition()
    val scale by pulseAnimation.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    Box(
        modifier = modifier.size(12.dp),
        contentAlignment = Alignment.Center
    ) {
        // 脈動效果
        if (status == PerformanceStatus.CRITICAL) {
            Canvas(
                modifier = Modifier
                    .size(12.dp * scale)
            ) {
                drawCircle(
                    color = color.copy(alpha = 0.3f),
                    radius = size.minDimension / 2
                )
            }
        }
        
        // 狀態點
        Canvas(
            modifier = Modifier.size(8.dp)
        ) {
            drawCircle(
                color = color,
                radius = size.minDimension / 2
            )
        }
    }
}

/**
 * 快取命中率視覺化
 */
@Composable
fun CacheHitRateVisualizer(
    hitRate: Float,
    modifier: Modifier = Modifier
) {
    val animatedHitRate by animateFloatAsState(
        targetValue = hitRate,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )
    
    Box(
        modifier = modifier
            .height(40.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colors.surface.copy(alpha = 0.3f))
    ) {
        // 命中率條
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(animatedHitRate)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF4CAF50),
                            Color(0xFF8BC34A)
                        )
                    )
                )
        )
        
        // 文字標籤
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "快取",
                style = MaterialTheme.typography.caption2,
                fontSize = 10.sp
            )
            Text(
                text = "${(hitRate * 100).toInt()}%",
                style = MaterialTheme.typography.caption1,
                fontSize = 12.sp
            )
        }
    }
}

/**
 * 優化建議卡片
 */
@Composable
fun OptimizationSuggestionCard(
    suggestion: String,
    priority: SuggestionPriority,
    onApply: () -> Unit,
    modifier: Modifier = Modifier
) {
    val priorityColor = when (priority) {
        SuggestionPriority.HIGH -> Color(0xFFF44336)
        SuggestionPriority.MEDIUM -> Color(0xFFFFC107)
        SuggestionPriority.LOW -> Color(0xFF4CAF50)
    }
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        priorityColor.copy(alpha = 0.2f),
                        priorityColor.copy(alpha = 0.05f)
                    )
                )
            )
            .clickable { onApply() }
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 優先級指示器
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(priorityColor, RoundedCornerShape(4.dp))
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // 建議文字
            Text(
                text = suggestion,
                style = MaterialTheme.typography.caption1,
                modifier = Modifier.weight(1f)
            )
            
            // 應用按鈕
            Text(
                text = "套用",
                style = MaterialTheme.typography.caption2,
                color = priorityColor,
                modifier = Modifier.clickable { onApply() }
            )
        }
    }
}

// Helper functions and enums
private fun getMetricColor(value: Float): Color {
    return when {
        value < 0.5f -> Color(0xFF4CAF50)
        value < 0.7f -> Color(0xFFFFC107)
        value < 0.85f -> Color(0xFFFF9800)
        else -> Color(0xFFF44336)
    }
}

enum class PerformanceStatus {
    EXCELLENT, GOOD, FAIR, POOR, CRITICAL
}

enum class SuggestionPriority {
    HIGH, MEDIUM, LOW
}
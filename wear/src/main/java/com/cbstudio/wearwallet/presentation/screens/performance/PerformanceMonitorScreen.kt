package com.cbstudio.wearwallet.presentation.screens.performance

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.cbstudio.wearwallet.R
import androidx.wear.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.cos
import kotlin.math.sin

/**
 * Wear OS 效能監控畫面
 */
@Composable
fun PerformanceMonitorScreen(
    viewModel: PerformanceViewModel,
    onBack: () -> Unit
) {
    val scrollState = rememberScrollState()
    val metrics by viewModel.metrics.collectAsState()
    val alerts by viewModel.activeAlerts.collectAsState(initial = emptyList())
    val strategy by viewModel.optimizationStrategy.collectAsState()
    
    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scrollState = scrollState) }
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = 24.dp,
                bottom = 40.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 標題
            item {
                Text(
                    text = stringResource(R.string.performance_monitor),
                    style = MaterialTheme.typography.title2,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            
            // 健康度儀表板
            item {
                HealthGauge(
                    health = metrics.overallHealth,
                    modifier = Modifier
                        .size(140.dp)
                        .padding(8.dp)
                )
            }
            
            // 關鍵指標卡片
            item {
                MetricsCard(
                    title = "CPU",
                    value = "${metrics.cpuUsage.toInt()}%",
                    status = metrics.cpuStatus,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            item {
                MetricsCard(
                    title = stringResource(R.string.memory),
                    value = "${metrics.memoryUsage.toInt()}%",
                    status = metrics.memoryStatus,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            item {
                MetricsCard(
                    title = stringResource(R.string.response_time),
                    value = "${metrics.responseTime}ms",
                    status = metrics.responseStatus,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            item {
                MetricsCard(
                    title = stringResource(R.string.cache_hit_rate),
                    value = "${(metrics.cacheHitRate * 100).toInt()}%",
                    status = if (metrics.cacheHitRate > 0.7) MetricStatus.GOOD else MetricStatus.WARNING,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            // 優化策略
            item {
                StrategyIndicator(
                    strategy = strategy,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            // 活躍警報
            if (alerts.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.active_alerts),
                        style = MaterialTheme.typography.caption1,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                
                items(alerts.take(3)) { alert ->
                    AlertItem(
                        alert = alert,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            
            // 操作按鈕
            item {
                Chip(
                    onClick = { viewModel.forceOptimization() },
                    label = { Text(stringResource(R.string.force_optimize)) },
                    modifier = Modifier.fillMaxWidth(0.9f)
                )
            }
            
            item {
                Chip(
                    onClick = { viewModel.clearCache() },
                    label = { Text(stringResource(R.string.clear_cache)) },
                    modifier = Modifier.fillMaxWidth(0.9f)
                )
            }
            
            item {
                Chip(
                    onClick = onBack,
                    label = { Text(stringResource(R.string.back)) },
                    modifier = Modifier.fillMaxWidth(0.9f)
                )
            }
        }
    }
}

/**
 * 健康度儀表
 */
@Composable
fun HealthGauge(
    health: HealthStatus,
    modifier: Modifier = Modifier
) {
    val healthValue = when (health) {
        HealthStatus.EXCELLENT -> 1.0f
        HealthStatus.GOOD -> 0.75f
        HealthStatus.FAIR -> 0.5f
        HealthStatus.POOR -> 0.25f
    }
    
    val healthColor by animateColorAsState(
        targetValue = when (health) {
            HealthStatus.EXCELLENT -> Color.Green
            HealthStatus.GOOD -> Color(0xFF8BC34A)
            HealthStatus.FAIR -> Color(0xFFFFC107)
            HealthStatus.POOR -> Color(0xFFFF5722)
        },
        animationSpec = tween(500)
    )
    
    val animatedValue by animateFloatAsState(
        targetValue = healthValue,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )
    
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawHealthGauge(
                value = animatedValue,
                color = healthColor
            )
        }
        
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = health.name,
                style = MaterialTheme.typography.caption1,
                color = healthColor
            )
            Text(
                text = "${(healthValue * 100).toInt()}%",
                style = MaterialTheme.typography.title3
            )
        }
    }
}

/**
 * 繪製健康度儀表
 */
fun DrawScope.drawHealthGauge(
    value: Float,
    color: Color
) {
    val strokeWidth = 8.dp.toPx()
    val radius = (size.minDimension - strokeWidth) / 2
    val center = Offset(size.width / 2, size.height / 2)
    
    // 背景圓弧
    drawArc(
        color = color.copy(alpha = 0.2f),
        startAngle = 135f,
        sweepAngle = 270f,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2, radius * 2),
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )
    
    // 進度圓弧
    drawArc(
        color = color,
        startAngle = 135f,
        sweepAngle = 270f * value,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2, radius * 2),
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )
    
    // 刻度標記
    for (i in 0..10) {
        val angle = 135f + (270f / 10) * i
        val startRadius = radius - strokeWidth
        val endRadius = radius - strokeWidth * 2
        
        val startX = center.x + startRadius * cos(Math.toRadians(angle.toDouble())).toFloat()
        val startY = center.y + startRadius * sin(Math.toRadians(angle.toDouble())).toFloat()
        val endX = center.x + endRadius * cos(Math.toRadians(angle.toDouble())).toFloat()
        val endY = center.y + endRadius * sin(Math.toRadians(angle.toDouble())).toFloat()
        
        drawLine(
            color = color.copy(alpha = 0.5f),
            start = Offset(startX, startY),
            end = Offset(endX, endY),
            strokeWidth = 1.dp.toPx()
        )
    }
}

/**
 * 指標卡片
 */
@Composable
fun MetricsCard(
    title: String,
    value: String,
    status: MetricStatus,
    modifier: Modifier = Modifier
) {
    val statusColor = when (status) {
        MetricStatus.GOOD -> Color(0xFF4CAF50)
        MetricStatus.WARNING -> Color(0xFFFFC107)
        MetricStatus.ERROR -> Color(0xFFF44336)
        MetricStatus.CRITICAL -> Color(0xFF9C27B0)
    }
    
    Card(
        onClick = { },
        modifier = modifier.padding(horizontal = 16.dp),
        backgroundPainter = CardDefaults.cardBackgroundPainter(
            startBackgroundColor = statusColor.copy(alpha = 0.1f),
            endBackgroundColor = statusColor.copy(alpha = 0.05f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.body2
            )
            Text(
                text = value,
                style = MaterialTheme.typography.title3,
                color = statusColor
            )
        }
    }
}

/**
 * 策略指示器
 */
@Composable
fun StrategyIndicator(
    strategy: OptimizationStrategy,
    modifier: Modifier = Modifier
) {
    val icon = when (strategy) {
        OptimizationStrategy.AGGRESSIVE -> Icons.Default.Warning
        OptimizationStrategy.BALANCED -> Icons.Default.CheckCircle
        OptimizationStrategy.PERFORMANCE_FOCUS -> Icons.Default.Info
        OptimizationStrategy.SAFE_MODE -> Icons.Default.Error
    }
    
    val color = when (strategy) {
        OptimizationStrategy.AGGRESSIVE -> Color(0xFFFF9800)
        OptimizationStrategy.BALANCED -> Color(0xFF4CAF50)
        OptimizationStrategy.PERFORMANCE_FOCUS -> Color(0xFF2196F3)
        OptimizationStrategy.SAFE_MODE -> Color(0xFFF44336)
    }
    
    val description = when (strategy) {
        OptimizationStrategy.AGGRESSIVE -> stringResource(R.string.strategy_aggressive)
        OptimizationStrategy.BALANCED -> stringResource(R.string.strategy_balanced)
        OptimizationStrategy.PERFORMANCE_FOCUS -> stringResource(R.string.strategy_performance)
        OptimizationStrategy.SAFE_MODE -> stringResource(R.string.strategy_safe)
    }
    
    Card(
        onClick = { },
        modifier = modifier.padding(horizontal = 16.dp),
        backgroundPainter = CardDefaults.cardBackgroundPainter(
            startBackgroundColor = color.copy(alpha = 0.2f),
            endBackgroundColor = color.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = stringResource(R.string.optimization_strategy),
                    style = MaterialTheme.typography.caption2
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.body1,
                    color = color
                )
            }
        }
    }
}

/**
 * 警報項目
 */
@Composable
fun AlertItem(
    alert: PerformanceAlert,
    modifier: Modifier = Modifier
) {
    val alertColor = when (alert.severity) {
        AlertSeverity.INFO -> Color(0xFF2196F3)
        AlertSeverity.WARNING -> Color(0xFFFFC107)
        AlertSeverity.ERROR -> Color(0xFFFF5722)
        AlertSeverity.CRITICAL -> Color(0xFFF44336)
    }
    
    val alertIcon = when (alert.severity) {
        AlertSeverity.INFO -> Icons.Default.Info
        AlertSeverity.WARNING -> Icons.Default.Warning
        AlertSeverity.ERROR -> Icons.Default.Error
        AlertSeverity.CRITICAL -> Icons.Default.Error
    }
    
    Card(
        onClick = { },
        modifier = modifier.padding(horizontal = 16.dp),
        backgroundPainter = CardDefaults.cardBackgroundPainter(
            startBackgroundColor = alertColor.copy(alpha = 0.15f),
            endBackgroundColor = alertColor.copy(alpha = 0.05f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = alertIcon,
                contentDescription = null,
                tint = alertColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = alert.message ?: alert.messageRes?.let { stringResource(it, *alert.args) } ?: "",
                style = MaterialTheme.typography.caption1,
                color = alertColor,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// Data classes for UI
data class PerformanceMetrics(
    val overallHealth: HealthStatus = HealthStatus.GOOD,
    val cpuUsage: Double = 0.0,
    val cpuStatus: MetricStatus = MetricStatus.GOOD,
    val memoryUsage: Double = 0.0,
    val memoryStatus: MetricStatus = MetricStatus.GOOD,
    val responseTime: Long = 0,
    val responseStatus: MetricStatus = MetricStatus.GOOD,
    val cacheHitRate: Double = 0.0
)

enum class HealthStatus {
    EXCELLENT, GOOD, FAIR, POOR
}

enum class MetricStatus {
    GOOD, WARNING, ERROR, CRITICAL
}

enum class OptimizationStrategy {
    AGGRESSIVE, BALANCED, PERFORMANCE_FOCUS, SAFE_MODE
}

enum class AlertSeverity {
    INFO, WARNING, ERROR, CRITICAL
}

data class PerformanceAlert(
    val message: String? = null,
    val messageRes: Int? = null,
    val args: Array<Any> = emptyArray(),
    val severity: AlertSeverity,
    val timestamp: Long = System.currentTimeMillis()
)
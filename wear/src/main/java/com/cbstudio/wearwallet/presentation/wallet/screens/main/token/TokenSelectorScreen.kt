package com.cbstudio.wearwallet.presentation.wallet.screens.main.token

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListAnchorType
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import com.cbstudio.wearwallet.core.domain.model.Token
import com.cbstudio.wearwallet.presentation.wallet.screens.token.TokenSelectorViewModel
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import java.text.DecimalFormat

/**
 * 重新設計的代幣選擇器畫面
 * 現代化設計，動畫效果，優化的視覺層次
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun TokenSelectorScreen(
    onTokenSelected: (Token) -> Unit = {},
    onBackClick: () -> Unit = {},
    onNavigateToChainSelector: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: TokenSelectorViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val filteredTokens by viewModel.filteredTokens.collectAsStateWithLifecycle(emptyList())
    val scrollState = rememberScalingLazyListState()
    
    // 動畫狀態
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(100)
        isVisible = true
    }
    
    // 掃描動畫
    val infiniteTransition = rememberInfiniteTransition()
    val scanRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0A0E27),
                        Color(0xFF1A1F3A)
                    )
                )
            )
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn() + slideInVertically()
        ) {
            ScalingLazyColumn(
                state = scrollState,
                modifier = Modifier.fillMaxSize(),
                anchorType = ScalingLazyListAnchorType.ItemStart,
                contentPadding = PaddingValues(
                    top = 20.dp,
                    bottom = 32.dp
                ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 優化的標題列
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        // 返回按鈕
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.1f))
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        
                        // 標題
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "選擇代幣",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                ),
                                color = Color.White
                            )
                            Text(
                                text = "${filteredTokens.size} 個代幣",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                        
                        // 掃描按鈕
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xFF4A90E2),
                                            Color(0xFF7B68EE)
                                        )
                                    )
                                )
                        ) {
                            IconButton(
                                onClick = { viewModel.scanTokens() },
                                modifier = Modifier.fillMaxSize(),
                                enabled = !uiState.isScanning
                            ) {
                                if (uiState.isScanning) {
                                    Icon(
                                        imageVector = Icons.Default.Sync,
                                        contentDescription = "掃描中",
                                        tint = Color.White,
                                        modifier = Modifier
                                            .size(20.dp)
                                            .graphicsLayer {
                                                rotationZ = scanRotation
                                            }
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "掃描代幣",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                
                // 鏈選擇卡片（優化設計）
                item {
                    Card(
                        onClick = onNavigateToChainSelector,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White.copy(alpha = 0.08f)
                        ),
                        border = BorderStroke(
                            width = 1.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF4A90E2).copy(alpha = 0.5f),
                                    Color(0xFF7B68EE).copy(alpha = 0.5f)
                                )
                            )
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(
                                            brush = Brush.radialGradient(
                                                colors = listOf(
                                                    Color(0xFF4A90E2),
                                                    Color(0xFF7B68EE)
                                                )
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Language,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = uiState.currentChain.displayName,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = Color.White
                                    )
                                    Text(
                                        text = "當前網路",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.5f)
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                
                // 搜尋框（現代化設計）
                item {
                    Card(
                        onClick = { /* TODO: 實現搜尋輸入 */ },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White.copy(alpha = 0.05f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (uiState.searchQuery.isEmpty()) 
                                    "搜尋代幣名稱或符號" 
                                else 
                                    uiState.searchQuery,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (uiState.searchQuery.isEmpty())
                                    Color.White.copy(alpha = 0.4f)
                                else
                                    Color.White
                            )
                        }
                    }
                }
                
                // 載入狀態
                if (uiState.isLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = Color(0xFF4A90E2),
                                strokeWidth = 3.dp
                            )
                        }
                    }
                }
                
                // 錯誤提示（優化樣式）
                uiState.error?.let { error ->
                    item {
                        Card(
                            onClick = { viewModel.clearError() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFFF6B6B).copy(alpha = 0.15f)
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = Color(0xFFFF6B6B).copy(alpha = 0.3f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = Color(0xFFFF6B6B),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = error,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFFF6B6B),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
                
                // 代幣列表
                if (filteredTokens.isEmpty() && !uiState.isLoading) {
                    item {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AccountBalanceWallet,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.3f),
                                modifier = Modifier
                                    .size(48.dp)
                                    .padding(bottom = 16.dp)
                            )
                            Text(
                                text = "沒有找到代幣",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.5f),
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "點擊上方掃描按鈕刷新",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.3f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                } else {
                    items(
                        filteredTokens,
                        key = { it.address }
                    ) { token ->
                        ModernTokenItem(
                            token = token,
                            isSelected = token == uiState.selectedToken,
                            onClick = {
                                viewModel.selectToken(token)
                                onTokenSelected(token)
                            }
                        )
                    }
                }
                
                // 底部間距
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun ModernTokenItem(
    token: Token,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        )
    )
    
    Card(
        onClick = {
            isPressed = true
            onClick()
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .scale(scale),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                Color(0xFF4A90E2).copy(alpha = 0.15f)
            } else {
                Color.White.copy(alpha = 0.05f)
            }
        ),
        border = if (isSelected) {
            BorderStroke(
                width = 1.5.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF4A90E2),
                        Color(0xFF7B68EE)
                    )
                )
            )
        } else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 代幣圖標
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.linearGradient(
                            colors = generateTokenColors(token.symbol)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = token.symbol.take(2).uppercase(),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    ),
                    color = Color.White
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // 代幣信息
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = token.symbol,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White
                )
                Text(
                    text = token.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            // 餘額和價值
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = formatBalance(token.balance),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = Color.White
                )
                val price = token.usdPrice
                if (price != null && price > 0) {
                    val balanceValue = token.balance.toDoubleOrNull() ?: 0.0
                    val usdValue = balanceValue * price
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF4ADE80),
                            fontSize = 10.sp
                        )
                        Text(
                            text = formatUsdValue(usdValue),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF4ADE80),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
    
    LaunchedEffect(isPressed) {
        if (isPressed) {
            delay(100)
            isPressed = false
        }
    }
}

/**
 * 根據代幣符號生成漸變顏色
 */
private fun generateTokenColors(symbol: String): List<Color> {
    val hash = symbol.hashCode()
    return when (Math.abs(hash) % 6) {
        0 -> listOf(Color(0xFF667EEA), Color(0xFF764BA2))
        1 -> listOf(Color(0xFFF093FB), Color(0xFFF5576C))
        2 -> listOf(Color(0xFF4FACFE), Color(0xFF00F2FE))
        3 -> listOf(Color(0xFF43E97B), Color(0xFF38F9D7))
        4 -> listOf(Color(0xFFFA709A), Color(0xFFFEE140))
        else -> listOf(Color(0xFF30CFD0), Color(0xFF330867))
    }
}

/**
 * 格式化餘額顯示
 */
private fun formatBalance(balance: String): String {
    return try {
        val value = balance.toDoubleOrNull() ?: 0.0
        when {
            value >= 1000000 -> "${DecimalFormat("#,##0.00").format(value / 1000000)}M"
            value >= 1000 -> "${DecimalFormat("#,##0.00").format(value / 1000)}K"
            value > 0.01 -> DecimalFormat("#,##0.00").format(value)
            value > 0 -> DecimalFormat("0.######").format(value)
            else -> "0"
        }
    } catch (e: Exception) {
        balance
    }
}

/**
 * 格式化 USD 價值
 */
private fun formatUsdValue(value: Double): String {
    return when {
        value >= 1000000 -> "${DecimalFormat("#,##0.00").format(value / 1000000)}M"
        value >= 1000 -> "${DecimalFormat("#,##0.00").format(value / 1000)}K"
        value >= 1 -> DecimalFormat("#,##0.00").format(value)
        value > 0 -> DecimalFormat("0.####").format(value)
        else -> "0.00"
    }
}
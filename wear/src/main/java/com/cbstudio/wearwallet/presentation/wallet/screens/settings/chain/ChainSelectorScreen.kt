package com.cbstudio.wearwallet.presentation.wallet.screens.settings.chain

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import com.cbstudio.wearwallet.core.domain.model.ChainType
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

/**
 * 鏈選擇器畫面 - 現代化設計
 * 使用 coreKmp 的 ChainType
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun ChainSelectorScreen(
    onBackClick: () -> Unit = {},
    onChainSelected: (ChainType) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ChainSelectorViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScalingLazyListState()
    
    // 動畫狀態
    var isAnimating by remember { mutableStateOf(false) }
    val infiniteTransition = rememberInfiniteTransition(label = "chain_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    
    // 載入動畫
    LaunchedEffect(Unit) {
        isAnimating = true
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A1A2E),
                        Color(0xFF0F0F1E)
                    )
                )
            )
    ) {
        // 背景裝飾效果
        Box(
            modifier = Modifier
                .size(200.dp)
                .offset(x = (-100).dp, y = (-50).dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF6366F1).copy(alpha = 0.1f),
                            Color.Transparent
                        )
                    )
                )
        )
        
        ScalingLazyColumn(
            state = scrollState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 優雅的標題區域
            item {
                AnimatedVisibility(
                    visible = isAnimating,
                    enter = fadeIn() + slideInVertically()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 返回按鈕和標題
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF6366F1).copy(alpha = 0.2f))
                                    .clickable(onClick = onBackClick),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "返回",
                                    tint = Color(0xFF818CF8),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            
                            Text(
                                text = "區塊鏈網路",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                ),
                                color = Color.White
                            )
                            
                            Spacer(modifier = Modifier.width(40.dp))
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // 副標題
                        Text(
                            text = "選擇您的網路",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }
            }
            
            // 當前選擇的鏈 - 突出顯示
            item {
                AnimatedVisibility(
                    visible = isAnimating,
                    enter = fadeIn(animationSpec = tween(500, 200)) + 
                           slideInHorizontally(animationSpec = tween(500, 200))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .scale(pulseScale)
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.Transparent
                            ),
                            onClick = {}
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(
                                                Color(0xFF6366F1),
                                                Color(0xFF8B5CF6)
                                            )
                                        )
                                    )
                                    .padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(Color.White.copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = getChainIcon(uiState.currentChain),
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                        
                                        Spacer(modifier = Modifier.width(12.dp))
                                        
                                        Column {
                                            Text(
                                                text = uiState.currentChain.displayName,
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                color = Color.White
                                            )
                                            Text(
                                                text = "當前網路",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.White.copy(alpha = 0.8f)
                                            )
                                        }
                                    }
                                    
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            // 主網列表標題
            item {
                AnimatedVisibility(
                    visible = isAnimating,
                    enter = fadeIn(animationSpec = tween(500, 400))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "主網路",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = Color(0xFF10B981)
                        )
                    }
                }
            }
            
            // 主網列表
            uiState.mainnetChains.forEachIndexed { index, chain ->
                item {
                    AnimatedVisibility(
                        visible = isAnimating,
                        enter = fadeIn(animationSpec = tween(500, 400 + index * 50)) + 
                               slideInHorizontally(animationSpec = tween(500, 400 + index * 50))
                    ) {
                        ModernChainItem(
                            chain = chain,
                            isSelected = chain == uiState.currentChain,
                            onClick = {
                                viewModel.selectChain(chain)
                                onChainSelected(chain)
                                onBackClick()
                            }
                        )
                    }
                }
            }
            
            // 測試網列表
            if (uiState.showTestnets && uiState.testnetChains.isNotEmpty()) {
                item {
                    AnimatedVisibility(
                        visible = isAnimating,
                        enter = fadeIn(animationSpec = tween(500, 800))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFF59E0B))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "測試網路",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = Color(0xFFF59E0B)
                            )
                        }
                    }
                }
                
                uiState.testnetChains.forEachIndexed { index, chain ->
                    item {
                        AnimatedVisibility(
                            visible = isAnimating,
                            enter = fadeIn(animationSpec = tween(500, 900 + index * 50)) + 
                                   slideInHorizontally(animationSpec = tween(500, 900 + index * 50))
                        ) {
                            ModernChainItem(
                                chain = chain,
                                isSelected = chain == uiState.currentChain,
                                isTestnet = true,
                                onClick = {
                                    viewModel.selectChain(chain)
                                    onChainSelected(chain)
                                    onBackClick()
                                }
                            )
                        }
                    }
                }
            }
            
            // 顯示/隱藏測試網按鈕
            item {
                AnimatedVisibility(
                    visible = isAnimating,
                    enter = fadeIn(animationSpec = tween(500, 1000))
                ) {
                    Box(
                        modifier = Modifier.padding(top = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            onClick = { viewModel.toggleTestnets() },
                            modifier = Modifier,
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF6366F1).copy(alpha = 0.1f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(
                                    horizontal = 16.dp,
                                    vertical = 8.dp
                                ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (uiState.showTestnets) 
                                        Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = Color(0xFF818CF8),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (uiState.showTestnets) "隱藏測試網" else "顯示測試網",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFF818CF8)
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // 載入指示器
        AnimatedVisibility(
            visible = uiState.isLoading,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Color(0xFF6366F1),
                    modifier = Modifier.size(40.dp)
                )
            }
        }
    }
}

/**
 * 現代化的鏈項目組件
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModernChainItem(
    chain: ChainType,
    isSelected: Boolean,
    isTestnet: Boolean = false,
    onClick: () -> Unit
) {
    val animatedScale = remember { Animatable(1f) }
    
    LaunchedEffect(key1 = isSelected) {
        if (isSelected) {
            animatedScale.animateTo(
                targetValue = 1.02f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        } else {
            animatedScale.animateTo(1f)
        }
    }
    
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .scale(animatedScale.value)
            .then(
                if (isSelected) {
                    Modifier.shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(16.dp),
                        ambientColor = Color(0xFF6366F1).copy(alpha = 0.3f),
                        spotColor = Color(0xFF6366F1).copy(alpha = 0.3f)
                    )
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                Color(0xFF2D2D44)
            } else {
                Color(0xFF1E1E2E)
            }
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isSelected) {
                        Modifier.border(
                            width = 1.dp,
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF6366F1),
                                    Color(0xFF8B5CF6)
                                )
                            ),
                            shape = RoundedCornerShape(16.dp)
                        )
                    } else {
                        Modifier
                    }
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // 鏈圖標背景
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (isTestnet) {
                                    Color(0xFFF59E0B).copy(alpha = 0.1f)
                                } else {
                                    getChainColor(chain).copy(alpha = 0.1f)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = getChainIcon(chain),
                            contentDescription = null,
                            tint = if (isTestnet) {
                                Color(0xFFF59E0B)
                            } else {
                                getChainColor(chain)
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Column {
                        Text(
                            text = chain.displayName,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (isSelected) {
                                Color.White
                            } else {
                                Color.White.copy(alpha = 0.9f)
                            }
                        )
                        
                        if (isTestnet) {
                            Text(
                                text = "測試網路",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFF59E0B).copy(alpha = 0.8f)
                            )
                        } else {
                            Text(
                                text = getChainDescription(chain),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                
                // 選中指示器
                AnimatedVisibility(
                    visible = isSelected,
                    enter = scaleIn() + fadeIn(),
                    exit = scaleOut() + fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFF6366F1),
                                        Color(0xFF8B5CF6)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "已選擇",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 獲取鏈的圖標
 */
private fun getChainIcon(chain: ChainType): ImageVector {
    return when (chain) {
        ChainType.ETHEREUM -> Icons.Default.CloudQueue
        ChainType.BSC -> Icons.Default.SwapHoriz
        ChainType.POLYGON -> Icons.Default.Category  // Changed from Hexagon
        ChainType.ARBITRUM -> Icons.Default.Speed
        ChainType.OPTIMISM -> Icons.Default.TrendingUp
        ChainType.AVALANCHE -> Icons.Default.AcUnit
        ChainType.BASE -> Icons.Default.Foundation
        ChainType.CRONOS -> Icons.Default.CurrencyExchange
        ChainType.SEPOLIA,
        ChainType.GOERLI,
        ChainType.MUMBAI -> Icons.Default.Science
        // UTXO 鏈圖標
        ChainType.BITCOIN -> Icons.Default.CurrencyBitcoin
        ChainType.LITECOIN -> Icons.Default.MonetizationOn
        ChainType.DOGECOIN -> Icons.Default.Pets
        ChainType.BITCOIN_CASH -> Icons.Default.Money
        // 其他非 EVM 鏈
        ChainType.SOLANA -> Icons.Default.WbSunny
        ChainType.APTOS -> Icons.Default.Rocket
        ChainType.SUI -> Icons.Default.Water
        ChainType.COSMOS -> Icons.Default.Hub
        ChainType.POLKADOT -> Icons.Default.GridOn
        ChainType.CARDANO -> Icons.Default.AccountTree
        ChainType.NEAR -> Icons.Default.NearMe
        else -> Icons.Default.Link
    }
}

/**
 * 獲取鏈的顏色
 */
private fun getChainColor(chain: ChainType): Color {
    return when (chain) {
        ChainType.ETHEREUM -> Color(0xFF627EEA)
        ChainType.BSC -> Color(0xFFF3BA2F)
        ChainType.POLYGON -> Color(0xFF8247E5)
        ChainType.ARBITRUM -> Color(0xFF28A0F0)
        ChainType.OPTIMISM -> Color(0xFFFF0420)
        ChainType.AVALANCHE -> Color(0xFFE84142)
        ChainType.BASE -> Color(0xFF0052FF)
        ChainType.CRONOS -> Color(0xFF002D74)
        // UTXO 鏈顏色
        ChainType.BITCOIN -> Color(0xFFF7931A)  // Bitcoin 橘色
        ChainType.LITECOIN -> Color(0xFF345D9D)  // Litecoin 藍色
        ChainType.DOGECOIN -> Color(0xFFC3A634)  // Dogecoin 黃色
        ChainType.BITCOIN_CASH -> Color(0xFF0AC18E)  // Bitcoin Cash 綠色
        // 其他非 EVM 鏈
        ChainType.SOLANA -> Color(0xFF14F195)
        ChainType.APTOS -> Color(0xFF00A9A7)
        ChainType.SUI -> Color(0xFF6FBCE3)
        ChainType.COSMOS -> Color(0xFF2E3148)
        ChainType.POLKADOT -> Color(0xFFE6007A)
        ChainType.CARDANO -> Color(0xFF0033AD)
        ChainType.NEAR -> Color(0xFF00C1DE)
        else -> Color(0xFF6366F1)
    }
}

/**
 * 獲取鏈的描述
 */
private fun getChainDescription(chain: ChainType): String {
    return when (chain) {
        ChainType.ETHEREUM -> "去中心化世界電腦"
        ChainType.BSC -> "幣安智能鏈"
        ChainType.POLYGON -> "以太坊側鏈解決方案"
        ChainType.ARBITRUM -> "Layer 2 擴展方案"
        ChainType.OPTIMISM -> "樂觀擴展網路"
        ChainType.AVALANCHE -> "高速區塊鏈平台"
        ChainType.BASE -> "Coinbase Layer 2"
        ChainType.CRONOS -> "Crypto.com 鏈"
        // UTXO 鏈描述
        ChainType.BITCOIN -> "數位黃金，第一個加密貨幣"
        ChainType.LITECOIN -> "比特幣的輕量版本"
        ChainType.DOGECOIN -> "迷因幣，社群驅動"
        ChainType.BITCOIN_CASH -> "比特幣分叉，大區塊"
        // 其他非 EVM 鏈
        ChainType.SOLANA -> "高性能區塊鏈"
        ChainType.APTOS -> "Move 語言智能合約"
        ChainType.SUI -> "並行交易處理"
        ChainType.COSMOS -> "區塊鏈互聯網"
        ChainType.POLKADOT -> "跨鏈協議"
        ChainType.CARDANO -> "學術驅動區塊鏈"
        ChainType.NEAR -> "開發者友好平台"
        else -> "區塊鏈網路"
    }
}
package com.cbstudio.wearwallet.presentation.wallet.screens.settings.chain

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.*
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.presentation.theme.MetallicBlue
import org.koin.androidx.compose.koinViewModel

// 狀態語義色：綠色代表主網上線、琥珀色代表測試網
private val MainnetGreen = Color(0xFF10B981)
private val TestnetAmber = Color(0xFFF59E0B)

/**
 * 鏈選擇器畫面 - Wear OS Material 3
 * 使用 coreKmp 的 ChainType
 */
@Composable
fun ChainSelectorScreen(
    onBackClick: () -> Unit = {},
    onChainSelected: (ChainType) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ChainSelectorViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScalingLazyListState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ScalingLazyColumn(
            state = scrollState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 標題區域
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Text(
                            text = "區塊鏈網路",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        // 平衡返回按鈕寬度，讓標題置中
                        Spacer(modifier = Modifier.width(48.dp))
                    }

                    Text(
                        text = "選擇您的網路",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 當前選擇的鏈 - 以 primary container 色調突出顯示
            item {
                Card(
                    onClick = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .heightIn(min = 48.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = getChainIcon(uiState.currentChain),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Text(
                                    text = uiState.currentChain.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "當前網路",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // 主網列表標題
            item {
                SectionLabel(text = "主網路", color = MainnetGreen)
            }

            // 主網列表
            uiState.mainnetChains.forEach { chain ->
                item {
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

            // 測試網列表
            if (uiState.showTestnets && uiState.testnetChains.isNotEmpty()) {
                item {
                    SectionLabel(
                        text = "測試網路",
                        color = TestnetAmber,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                uiState.testnetChains.forEach { chain ->
                    item {
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

            // 顯示/隱藏測試網按鈕
            item {
                FilledTonalButton(
                    onClick = { viewModel.toggleTestnets() },
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .heightIn(min = 48.dp)
                ) {
                    Icon(
                        imageVector = if (uiState.showTestnets)
                            Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (uiState.showTestnets) "隱藏測試網" else "顯示測試網",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        // 載入指示器
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(40.dp)
                )
            }
        }
    }
}

/**
 * 區段標題（主網路 / 測試網路）
 */
@Composable
private fun SectionLabel(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = color
        )
    }
}

/**
 * 鏈項目組件
 */
@Composable
private fun ModernChainItem(
    chain: ChainType,
    isSelected: Boolean,
    isTestnet: Boolean = false,
    onClick: () -> Unit
) {
    val accentColor = if (isTestnet) TestnetAmber else getChainColor(chain)

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .heightIn(min = 48.dp),
        colors = if (isSelected) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
                        .background(accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getChainIcon(chain),
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = chain.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isTestnet) "測試網路" else getChainDescription(chain),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 選中指示器
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "已選擇",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
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
 * 獲取鏈的顏色（各鏈品牌色）
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
        else -> MetallicBlue // 主題主色作為通用後備
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

package com.cbstudio.wearwallet.presentation.ui.token

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.androidx.compose.koinViewModel
import androidx.wear.compose.material.*
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.core.multichain.tokens.TokenTransferManager
import com.cbstudio.wearwallet.presentation.theme.WearWalletTheme
import java.math.BigDecimal
import java.text.DecimalFormat

/**
 * Token list screen for Wear OS
 * Displays user's token balances and allows transfers
 */
@Composable
fun TokenListScreen(
    walletAddress: String,
    chainType: MultiChainType,
    onTokenClick: (TokenTransferManager.TokenInfo) -> Unit,
    onBackClick: () -> Unit,
    onViewHistory: (TokenTransferManager.TokenInfo) -> Unit = {},
    viewModel: TokenListViewModel = koinViewModel()
) {
    val tokens by viewModel.tokens.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val totalValue by viewModel.totalValue.collectAsState()
    
    LaunchedEffect(walletAddress, chainType) {
        viewModel.loadTokens(walletAddress, chainType)
    }
    
    val listState = rememberScalingLazyListState()
    
    Scaffold(
        timeText = {
            TimeText()
        },
        vignette = {
            Vignette(vignettePosition = VignettePosition.TopAndBottom)
        },
        positionIndicator = {
            PositionIndicator(scalingLazyListState = listState)
        }
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(
                top = 32.dp,
                bottom = 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            item {
                Text(
                    text = "代幣",
                    style = MaterialTheme.typography.title2,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            
            // Chain info
            item {
                Chip(
                    label = {
                        Text(
                            text = chainType.name,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    },
                    secondaryLabel = {
                        Text(
                            text = "總值: $${"%.2f".format(totalValue)}",
                            fontSize = 10.sp
                        )
                    },
                    onClick = { },
                    colors = ChipDefaults.chipColors(
                        backgroundColor = MaterialTheme.colors.surface
                    ),
                    modifier = Modifier.fillMaxWidth(0.9f)
                )
            }
            
            // Loading indicator
            if (isLoading) {
                item {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(16.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
            
            // Token list
            items(tokens) { tokenBalance ->
                TokenCard(
                    tokenBalance = tokenBalance,
                    onClick = { onTokenClick(tokenBalance.token) },
                    onHistoryClick = { onViewHistory(tokenBalance.token) }
                )
            }
            
            // Add token button
            item {
                Chip(
                    label = {
                        Text(
                            text = "添加代幣",
                            fontSize = 12.sp
                        )
                    },
                    icon = {
                        Text("+", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    },
                    onClick = { /* TODO: Implement add token */ },
                    colors = ChipDefaults.chipColors(
                        backgroundColor = MaterialTheme.colors.primaryVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .padding(top = 8.dp)
                )
            }
            
            // Back button
            item {
                Chip(
                    label = {
                        Text(
                            text = "返回",
                            fontSize = 12.sp
                        )
                    },
                    onClick = onBackClick,
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .padding(top = 4.dp)
                )
            }
        }
    }
}

/**
 * Individual token card component
 */
@Composable
fun TokenCard(
    tokenBalance: TokenTransferManager.TokenBalance,
    onClick: () -> Unit,
    onHistoryClick: () -> Unit = {}
) {
    val decimalFormat = DecimalFormat("#,##0.######")
    
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .padding(vertical = 2.dp),
        backgroundPainter = CardDefaults.cardBackgroundPainter(
            startBackgroundColor = MaterialTheme.colors.surface.copy(alpha = 0.3f),
            endBackgroundColor = MaterialTheme.colors.surface.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Token symbol and name
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = tokenBalance.token.symbol,
                        style = MaterialTheme.typography.body1,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = tokenBalance.token.name,
                        style = MaterialTheme.typography.caption3,
                        fontSize = 10.sp,
                        color = MaterialTheme.colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                // Balance and value
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = decimalFormat.format(tokenBalance.balance),
                        style = MaterialTheme.typography.body2,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    tokenBalance.usdValue?.let { value ->
                        Text(
                            text = "$${"%.2f".format(value)}",
                            style = MaterialTheme.typography.caption3,
                            fontSize = 10.sp,
                            color = Color.Green.copy(alpha = 0.8f),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/**
 * Token transfer dialog
 * Note: Wear OS doesn't have Dialog/Alert components, use a separate screen instead
 */
@Composable
fun TokenTransferDialog(
    token: TokenTransferManager.TokenInfo,
    onDismiss: () -> Unit,
    onConfirm: (recipient: String, amount: BigDecimal) -> Unit
) {
    var recipient by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    
    // For Wear OS, we'll use a Card with overlay effect instead
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            onClick = { },
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Title
                Text(
                    text = "轉帳 ${token.symbol}",
                    style = MaterialTheme.typography.title3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // Recipient address
                Text(
                    text = "接收地址",
                    fontSize = 10.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = if (recipient.isEmpty()) "點擊輸入" else recipient,
                    fontSize = 10.sp,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Amount
                Text(
                    text = "數量",
                    fontSize = 10.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = if (amount.isEmpty()) "0.00" else amount,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                
                // Token info
                Text(
                    text = token.name,
                    fontSize = 8.sp,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    CompactButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.secondaryButtonColors()
                    ) {
                        Text("取消", fontSize = 12.sp)
                    }
                    CompactButton(
                        onClick = {
                            val amountDecimal = amount.toBigDecimalOrNull()
                            if (recipient.isNotEmpty() && amountDecimal != null && amountDecimal > BigDecimal.ZERO) {
                                onConfirm(recipient, amountDecimal)
                            }
                        },
                        colors = ButtonDefaults.primaryButtonColors()
                    ) {
                        Text("確認", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
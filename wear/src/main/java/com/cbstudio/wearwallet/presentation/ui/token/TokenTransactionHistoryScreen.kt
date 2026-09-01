package com.cbstudio.wearwallet.presentation.ui.token

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
import androidx.wear.compose.material.*
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import com.cbstudio.wearwallet.core.multichain.tokens.TokenTransferManager
import com.cbstudio.wearwallet.core.domain.model.Transaction
import com.cbstudio.wearwallet.core.domain.model.TransactionType
import com.cbstudio.wearwallet.core.domain.model.TransactionStatus
import com.cbstudio.wearwallet.core.domain.model.TransactionDirection
import java.text.SimpleDateFormat
import java.util.*
import org.koin.androidx.compose.koinViewModel

/**
 * Token transaction history screen
 * Displays transaction history for a specific token
 */
@Composable
fun TokenTransactionHistoryScreen(
    token: TokenTransferManager.TokenInfo,
    walletAddress: String,
    onBackClick: () -> Unit,
    viewModel: TokenTransactionHistoryViewModel = koinViewModel()
) {
    val transactions by viewModel.transactions.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    
    LaunchedEffect(token, walletAddress) {
        viewModel.loadTransactionHistory(token, walletAddress)
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
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Text(
                        text = "${token.symbol} 交易歷史",
                        style = MaterialTheme.typography.title2,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = token.name,
                        style = MaterialTheme.typography.caption3,
                        color = MaterialTheme.colors.onSurfaceVariant
                    )
                }
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
            
            // Error message
            error?.let { errorMsg ->
                item {
                    Card(
                        onClick = { },
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .padding(vertical = 8.dp),
                        backgroundPainter = CardDefaults.cardBackgroundPainter(
                            startBackgroundColor = MaterialTheme.colors.error.copy(alpha = 0.2f),
                            endBackgroundColor = MaterialTheme.colors.error.copy(alpha = 0.1f)
                        )
                    ) {
                        Text(
                            text = errorMsg,
                            color = MaterialTheme.colors.error,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
            
            // Transaction list
            if (transactions.isEmpty() && !isLoading) {
                item {
                    Card(
                        onClick = { },
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = "暫無交易記錄",
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(transactions) { transaction ->
                    TransactionCard(
                        transaction = transaction,
                        tokenSymbol = token.symbol,
                        walletAddress = walletAddress
                    )
                }
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
                        .padding(top = 8.dp)
                )
            }
        }
    }
}

/**
 * Individual transaction card
 */
@Composable
fun TransactionCard(
    transaction: Transaction,
    tokenSymbol: String,
    walletAddress: String
) {
    val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
    val isReceived = transaction.direction == TransactionDirection.INCOMING
    
    Card(
        onClick = { },
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .padding(vertical = 2.dp),
        backgroundPainter = CardDefaults.cardBackgroundPainter(
            startBackgroundColor = MaterialTheme.colors.surface.copy(alpha = 0.3f),
            endBackgroundColor = MaterialTheme.colors.surface.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Transaction type icon and info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isReceived) "↓" else "↑",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isReceived) Color.Green else Color.Red,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Text(
                        text = if (isReceived) "接收" else "發送",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                // Address (truncated)
                val address = if (isReceived) transaction.from else transaction.to
                Text(
                    text = "${address.take(6)}...${address.takeLast(4)}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                // Timestamp
                transaction.timestamp?.let { timestamp ->
                    Text(
                        text = dateFormat.format(Date(timestamp.toEpochMilliseconds())),
                        fontSize = 10.sp,
                        color = MaterialTheme.colors.onSurfaceVariant
                    )
                }
            }
            
            // Amount and status
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "${if (isReceived) "+" else "-"}${transaction.getFormattedAmount()}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isReceived) Color.Green else Color.Red
                )
                Text(
                    text = tokenSymbol,
                    fontSize = 10.sp,
                    color = MaterialTheme.colors.onSurfaceVariant
                )
                
                // Transaction status
                val statusColor = when (transaction.status) {
                    TransactionStatus.CONFIRMED -> Color.Green
                    TransactionStatus.PENDING -> Color.Yellow
                    TransactionStatus.FAILED -> Color.Red
                    else -> MaterialTheme.colors.onSurfaceVariant
                }
                Text(
                    text = when (transaction.status) {
                        TransactionStatus.CONFIRMED -> "✓"
                        TransactionStatus.PENDING -> "⏳"
                        TransactionStatus.FAILED -> "✗"
                        else -> "?"
                    },
                    fontSize = 10.sp,
                    color = statusColor
                )
            }
        }
    }
}
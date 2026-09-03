package com.cbstudio.wearwallet.presentation.wallet.screens.transaction

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.CircularProgressIndicator
import com.cbstudio.wearwallet.core.domain.model.Transaction
import com.cbstudio.wearwallet.core.domain.model.TransactionStatus
import kotlinx.datetime.Instant
import java.text.SimpleDateFormat
import java.util.*

/**
 * 交易歷史畫面 - 完整實現
 * 
 * 功能：
 * 1. 顯示交易列表
 * 2. 按日期分組
 * 3. 滑動刷新（使用按鈕替代）
 * 4. 交易詳情對話框
 * 5. 交易狀態顯示
 * 
 * 使用 coreKmp 的 TransactionRepository
 */
@Composable
fun TransactionHistoryScreen(
    onBackClick: () -> Unit = {},
    onTransactionClick: (Transaction) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: TransactionHistoryViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScalingLazyListState()
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when {
            uiState.isLoading && uiState.transactions.isEmpty() -> {
                // 初次載入
                LoadingScreen()
            }
            
            uiState.error != null && uiState.transactions.isEmpty() -> {
                // 錯誤狀態
                ErrorScreen(
                    error = uiState.error ?: "未知錯誤",
                    onRetry = { viewModel.refreshTransactions() }
                )
            }
            
            uiState.transactions.isEmpty() -> {
                // 空狀態
                EmptyScreen()
            }
            
            else -> {
                // 交易列表
                ScalingLazyColumn(
                    state = scrollState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 標題和刷新按鈕
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "交易歷史",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            
                            // 刷新按鈕
                            if (uiState.isRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                IconButton(
                                    onClick = { viewModel.refreshTransactions() },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = "刷新",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                    
                    // 分組顯示交易
                    uiState.groupedTransactions.forEach { (date, transactions) ->
                        item {
                            DateHeader(date = date)
                        }
                        
                        items(transactions) { transaction ->
                            TransactionItem(
                                transaction = transaction,
                                currentAddress = uiState.activeWallet?.address ?: "",
                                onClick = { viewModel.showTransactionDetail(transaction) },
                                formatAmount = viewModel::formatAmount,
                                formatAddress = viewModel::formatAddress,
                                getStatusColor = viewModel::getStatusColor,
                                getStatusText = viewModel::getStatusText
                            )
                        }
                    }
                    
                    // 底部間距
                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
        
        // 返回按鈕
        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .size(32.dp)
        ) {
            Icon(
                Icons.Default.ArrowBack,
                contentDescription = "返回",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        
        // 交易詳情對話框
        if (uiState.showTransactionDetail) {
            uiState.selectedTransaction?.let { transaction ->
                TransactionDetailDialog(
                    transaction = transaction,
                    currentAddress = uiState.activeWallet?.address ?: "",
                    onDismiss = { viewModel.closeTransactionDetail() },
                    formatAmount = viewModel::formatAmount,
                    formatAddress = viewModel::formatAddress,
                    getStatusColor = viewModel::getStatusColor,
                    getStatusText = viewModel::getStatusText
                )
            }
        }
    }
}

/**
 * 日期標題
 */
@Composable
private fun DateHeader(date: String) {
    Text(
        text = date,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
    )
}

/**
 * 交易項目
 */
@Composable
private fun TransactionItem(
    transaction: Transaction,
    currentAddress: String,
    onClick: () -> Unit,
    formatAmount: (String, String) -> String,
    formatAddress: (String) -> String,
    getStatusColor: (TransactionStatus) -> Color,
    getStatusText: (TransactionStatus) -> String
) {
    val isSent = transaction.from.equals(currentAddress, ignoreCase = true)
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左側：類型圖標和地址
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // 交易類型圖標
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSent) Color.Red.copy(alpha = 0.2f)
                            else Color.Green.copy(alpha = 0.2f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isSent) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                        contentDescription = null,
                        tint = if (isSent) Color.Red else Color.Green,
                        modifier = Modifier.size(18.dp)
                    )
                }
                
                // 地址和時間
                Column {
                    Text(
                        text = if (isSent) "發送到" else "接收自",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatAddress(if (isSent) transaction.to else transaction.from),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1
                    )
                }
            }
            
            // 右側：金額和狀態
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = (if (isSent) "-" else "+") + formatAmount(
                        transaction.value,
                        transaction.tokenSymbol ?: "ETH"
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isSent) Color.Red else Color.Green
                )
                
                // 交易狀態
                Text(
                    text = getStatusText(transaction.status),
                    style = MaterialTheme.typography.labelSmall,
                    color = getStatusColor(transaction.status)
                )
            }
        }
    }
}

/**
 * 交易詳情對話框
 */
@Composable
private fun TransactionDetailDialog(
    transaction: Transaction,
    currentAddress: String,
    onDismiss: () -> Unit,
    formatAmount: (String, String) -> String,
    formatAddress: (String) -> String,
    getStatusColor: (TransactionStatus) -> Color,
    getStatusText: (TransactionStatus) -> String
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "交易詳情",
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 交易狀態
                DetailRow(
                    label = "狀態",
                    value = getStatusText(transaction.status),
                    valueColor = getStatusColor(transaction.status)
                )
                
                // 交易哈希
                DetailRow(
                    label = "交易哈希",
                    value = formatAddress(transaction.hash),
                    isMonospace = true
                )
                
                // 發送方
                DetailRow(
                    label = "發送方",
                    value = formatAddress(transaction.from),
                    isMonospace = true
                )
                
                // 接收方
                DetailRow(
                    label = "接收方",
                    value = formatAddress(transaction.to),
                    isMonospace = true
                )
                
                // 金額
                DetailRow(
                    label = "金額",
                    value = formatAmount(transaction.value, transaction.tokenSymbol ?: "ETH")
                )
                
                // Gas 費用
                transaction.gasPrice?.let { gasPrice ->
                    transaction.gasLimit?.let { gasLimit ->
                        val gasFee = (gasPrice.toLongOrNull() ?: 0L) * (gasLimit.toLongOrNull() ?: 0L)
                        DetailRow(
                            label = "Gas 費用",
                            value = formatAmount(
                                (gasFee / 1_000_000_000_000_000_000.0).toString(),
                                "ETH"
                            )
                        )
                    }
                }
                
                // 區塊高度
                transaction.blockNumber?.let {
                    DetailRow(
                        label = "區塊高度",
                        value = it.toString()
                    )
                }
                
                // 時間
                transaction.timestamp?.let {
                    val date = Date(it.toEpochMilliseconds())
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    DetailRow(
                        label = "時間",
                        value = dateFormat.format(date)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("關閉")
            }
        }
    )
}

/**
 * 詳情行
 */
@Composable
private fun DetailRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    isMonospace: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = valueColor,
            fontFamily = if (isMonospace) FontFamily.Monospace else FontFamily.Default,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f, false)
        )
    }
}

/**
 * 載入中畫面
 */
@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = "載入交易歷史...",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/**
 * 空狀態畫面
 */
@Composable
private fun EmptyScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Default.Receipt,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Text(
                text = "暫無交易記錄",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "您的交易將顯示在這裡",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * 錯誤畫面
 */
@Composable
private fun ErrorScreen(
    error: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                Icons.Default.Error,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                text = "載入失敗",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("重試")
            }
        }
    }
}
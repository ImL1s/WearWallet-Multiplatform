package com.cbstudio.wearwallet.presentation.wallet.screens.main.tx

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.cbstudio.wearwallet.core.domain.model.Transaction
import com.cbstudio.wearwallet.presentation.wallet.screens.history.TransactionHistoryViewModel
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.pow

/**
 * 交易篩選器枚舉
 */
enum class TransactionFilter {
    ALL,
    SENT,
    RECEIVED
}

/**
 * 交易歷史畫面 - 手錶優化版
 * 精簡設計，專注於核心資訊
 */
@Composable
fun TransactionHistoryScreen(
    onBackClick: () -> Unit = {},
    onTransactionClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: TransactionHistoryViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScalingLazyListState()
    
    // 淡入動畫
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(50)
        isVisible = true
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(animationSpec = tween(300))
        ) {
            ScalingLazyColumn(
                state = scrollState,
                modifier = Modifier.fillMaxSize(),
                anchorType = ScalingLazyListAnchorType.ItemCenter,
                contentPadding = PaddingValues(
                    top = 32.dp,
                    bottom = 48.dp
                ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 標題 - 只顯示數量
                item {
                    Row(
                        modifier = Modifier.padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (uiState.isLoading || uiState.isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = "${uiState.transactions.size} 筆交易",
                            style = MaterialTheme.typography.title3,
                            color = Color.White
                        )
                    }
                }
                
                // 篩選器 - 緊湊 chips
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)
                    ) {
                        CompactFilterChip(
                            text = "全部",
                            isSelected = uiState.filter == TransactionFilter.ALL,
                            onClick = { viewModel.setFilter(TransactionFilter.ALL) }
                        )
                        CompactFilterChip(
                            text = "發送",
                            isSelected = uiState.filter == TransactionFilter.SENT,
                            onClick = { viewModel.setFilter(TransactionFilter.SENT) }
                        )
                        CompactFilterChip(
                            text = "接收",
                            isSelected = uiState.filter == TransactionFilter.RECEIVED,
                            onClick = { viewModel.setFilter(TransactionFilter.RECEIVED) }
                        )
                    }
                }
                
                // 交易列表
                val filteredTransactions = when (uiState.filter) {
                    TransactionFilter.ALL -> uiState.transactions
                    TransactionFilter.SENT -> uiState.transactions.filter { 
                        it.from.lowercase() == uiState.activeWallet?.address?.lowercase()
                    }
                    TransactionFilter.RECEIVED -> uiState.transactions.filter { 
                        it.to.lowercase() == uiState.activeWallet?.address?.lowercase()
                    }
                }
                
                if (filteredTransactions.isEmpty() && !uiState.isLoading) {
                    // 空狀態
                    item {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Receipt,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "暫無交易",
                                style = MaterialTheme.typography.body2,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                        }
                    }
                } else {
                    // 按日期分組
                    val groupedTransactions = filteredTransactions.groupBy { tx ->
                        tx.timestamp?.let {
                            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                .format(Date(it.toEpochMilliseconds()))
                        } ?: "未知"
                    }
                    
                    groupedTransactions.forEach { (date, transactions) ->
                        // 日期標題
                        item {
                            Text(
                                text = formatDateHeader(date),
                                style = MaterialTheme.typography.caption2,
                                color = Color.White.copy(alpha = 0.4f),
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                            )
                        }
                        
                        // 交易項目
                        items(
                            transactions,
                            key = { it.hash }
                        ) { transaction ->
                            CompactTransactionItem(
                                transaction = transaction,
                                userAddress = uiState.activeWallet?.address,
                                onClick = { onTransactionClick(transaction.hash) }
                            )
                        }
                    }
                }
                
                // 刷新按鈕 (底部)
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Chip(
                        onClick = { viewModel.refreshTransactions() },
                        label = { Text("刷新") },
                        icon = {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.height(36.dp)
                    )
                }
            }
        }
    }
}

/**
 * 緊湊篩選 Chip
 */
@Composable
private fun CompactFilterChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) {
        Color(0xFF3B82F6).copy(alpha = 0.3f)
    } else {
        Color.White.copy(alpha = 0.08f)
    }
    
    val textColor = if (isSelected) {
        Color(0xFF60A5FA)
    } else {
        Color.White.copy(alpha = 0.6f)
    }
    
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.caption2.copy(
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            ),
            color = textColor
        )
    }
}

/**
 * 緊湊交易項目
 */
@Composable
private fun CompactTransactionItem(
    transaction: Transaction,
    userAddress: String?,
    onClick: () -> Unit
) {
    val isSend = userAddress?.let { 
        transaction.from.lowercase() == it.lowercase() 
    } ?: false
    
    val amountColor = if (isSend) Color(0xFFEF4444) else Color(0xFF22C55E)
    val amountPrefix = if (isSend) "-" else "+"
    
    Chip(
        onClick = onClick,
        label = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左側: 類型圖標 + 地址
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // 交易方向圖標
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(amountColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isSend) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                            contentDescription = null,
                            tint = amountColor,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // 地址
                    Text(
                        text = formatAddress(if (isSend) transaction.to else transaction.from),
                        style = MaterialTheme.typography.caption2,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                // 右側: 金額
                Text(
                    text = "$amountPrefix${formatAmountForTransaction(transaction)}",
                    style = MaterialTheme.typography.caption1.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = amountColor
                )
            }
        },
        secondaryLabel = {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = transaction.timestamp?.let { 
                        formatTime(it.toEpochMilliseconds()) 
                    } ?: "--:--",
                    style = MaterialTheme.typography.caption2,
                    color = Color.White.copy(alpha = 0.4f)
                )
                Text(
                    text = transaction.tokenSymbol ?: "ETH",
                    style = MaterialTheme.typography.caption2,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
        },
        colors = ChipDefaults.chipColors(
            backgroundColor = Color.White.copy(alpha = 0.05f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

// === 輔助函數 ===

private fun formatDateHeader(date: String): String {
    val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    val yesterday = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        .format(Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000))
    
    return when (date) {
        today -> "今天"
        yesterday -> "昨天"
        else -> {
            try {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val parsedDate = dateFormat.parse(date)
                SimpleDateFormat("MM/dd", Locale.getDefault()).format(parsedDate!!)
            } catch (e: Exception) {
                date
            }
        }
    }
}

private fun formatAddress(address: String): String {
    return if (address.length > 10) {
        "${address.take(6)}...${address.takeLast(4)}"
    } else {
        address
    }
}

private fun formatTime(timestamp: Long): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
}


/**
 * 根據代幣類型格式化金額
 * 優先使用 tokenDecimals 進行轉換
 * 如果沒有 decimals，則保留原有邏輯（ETH 預設 18，其他視為已轉換）
 */
private fun formatAmountForTransaction(transaction: Transaction): String {
    return try {
        val amount = transaction.value
        val decimals = transaction.tokenDecimals
        val symbol = transaction.tokenSymbol
        
        val value = if (decimals != null) {
            // 如果有明確的小數位數，進行轉換
            val valDouble = amount.toDoubleOrNull() ?: 0.0
            valDouble / 10.0.pow(decimals)
        } else {
            // 舊有邏輯 fallback
            val isTokenTransfer = symbol != null && symbol != "ETH"
            
            if (isTokenTransfer) {
                // ERC-20 代幣: 假設已轉換 (或是原有邏輯如此)
                amount.toDoubleOrNull() ?: 0.0
            } else {
                // ETH: 需要從 Wei 轉換
                val weiValue = amount.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO
                weiValue.divide(java.math.BigDecimal("1000000000000000000"), 8, java.math.RoundingMode.HALF_UP).toDouble()
            }
        }
        
        formatDisplayAmount(value)
    } catch (e: Exception) {
        "0"
    }
}

/**
 * 格式化顯示金額
 */
private fun formatDisplayAmount(value: Double): String {
    return when {
        value == 0.0 -> "0"
        value >= 1000 -> "${DecimalFormat("#.##").format(value / 1000)}K"
        value >= 1 -> DecimalFormat("#.####").format(value)
        value >= 0.0001 -> DecimalFormat("0.####").format(value)
        value > 0 -> "<0.0001"
        else -> "0"
    }
}
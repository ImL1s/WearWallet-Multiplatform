package com.cbstudio.wearwallet.presentation.wallet.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * 統一的空狀態顯示組件
 * 用於列表為空時的友好提示
 */
@Composable
fun EmptyState(
    title: String,
    description: String? = null,
    icon: ImageVector = Icons.Outlined.Inbox,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var isVisible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        delay(200)
        isVisible = true
    }
    
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + scaleIn(initialScale = 0.9f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 動畫圖標
            AnimatedIcon(icon = icon)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 標題
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
            
            // 描述
            if (description != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )
            }
            
            // 操作按鈕
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onAction,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF58A6FF),
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = actionLabel,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * 動畫圖標組件
 */
@Composable
private fun AnimatedIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition()
    
    // 浮動動畫
    val offsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -10f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    // 透明度動畫
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    Box(
        modifier = modifier.offset(y = offsetY.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = alpha),
            modifier = Modifier.size(64.dp)
        )
    }
}

/**
 * 預設空狀態配置
 */
object EmptyStateDefaults {
    
    @Composable
    fun NoTransactions(
        onRefresh: (() -> Unit)? = null
    ) = EmptyState(
        title = "沒有交易記錄",
        description = "您的交易將顯示在這裡",
        icon = Icons.Outlined.Receipt,
        actionLabel = if (onRefresh != null) "刷新" else null,
        onAction = onRefresh
    )
    
    @Composable
    fun NoTokens(
        onScan: (() -> Unit)? = null
    ) = EmptyState(
        title = "沒有代幣",
        description = "點擊掃描按鈕查找您的代幣",
        icon = Icons.Outlined.AccountBalanceWallet,
        actionLabel = if (onScan != null) "掃描代幣" else null,
        onAction = onScan
    )
    
    @Composable
    fun NoWallets(
        onCreate: (() -> Unit)? = null
    ) = EmptyState(
        title = "沒有錢包",
        description = "創建或匯入您的第一個錢包",
        icon = Icons.Outlined.Wallet,
        actionLabel = if (onCreate != null) "創建錢包" else null,
        onAction = onCreate
    )
    
    @Composable
    fun NoContacts(
        onAdd: (() -> Unit)? = null
    ) = EmptyState(
        title = "沒有聯絡人",
        description = "添加常用地址以便快速轉帳",
        icon = Icons.Outlined.Contacts,
        actionLabel = if (onAdd != null) "添加聯絡人" else null,
        onAction = onAdd
    )
    
    @Composable
    fun NoNotifications() = EmptyState(
        title = "沒有通知",
        description = "新的活動將顯示在這裡",
        icon = Icons.Outlined.NotificationsNone
    )
    
    @Composable
    fun SearchNoResults(
        query: String
    ) = EmptyState(
        title = "找不到結果",
        description = "嘗試使用其他關鍵字搜尋",
        icon = Icons.Outlined.SearchOff
    )
}
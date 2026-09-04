package com.cbstudio.wearwallet.presentation.subscription

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.*
import com.cbstudio.wearwallet.R
import com.cbstudio.wearwallet.domain.service.SubscriptionStatusInfo
import com.cbstudio.wearwallet.domain.service.SubscriptionTier

/**
 * 訂閱狀態卡片組件
 * 在設定頁面顯示當前訂閱狀態
 */
@Composable
fun SubscriptionStatusCard(
    subscriptionStatus: SubscriptionStatusInfo?,
    onUpgradeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when (subscriptionStatus?.tier) {
        SubscriptionTier.PREMIUM -> Color(0xFF1A1A2E) // 深色高級背景
        else -> Color.White.copy(alpha = 0.05f)
    }
    
    val borderColor = when (subscriptionStatus?.tier) {
        SubscriptionTier.PREMIUM -> Color(0xFFFFD700) // 金色邊框
        else -> Color.White.copy(alpha = 0.2f)
    }
    
    Card(
        onClick = onUpgradeClick, // 所有用戶都可以點擊進入訂閱管理頁面
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        backgroundPainter = CardDefaults.cardBackgroundPainter(
            startBackgroundColor = backgroundColor,
            endBackgroundColor = backgroundColor
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(
                    color = backgroundColor,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(1.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(
                    color = backgroundColor,
                    shape = RoundedCornerShape(7.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 圖標和標題
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = when (subscriptionStatus?.tier) {
                            SubscriptionTier.PREMIUM -> Icons.Default.Star
                            else -> Icons.Default.Lock
                        },
                        contentDescription = null,
                        tint = when (subscriptionStatus?.tier) {
                            SubscriptionTier.PREMIUM -> Color(0xFFFFD700)
                            else -> MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                        },
                        modifier = Modifier.size(20.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Text(
                        text = when (subscriptionStatus?.tier) {
                            SubscriptionTier.PREMIUM -> stringResource(R.string.premium_user)
                            else -> stringResource(R.string.free_user)
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (subscriptionStatus?.tier) {
                            SubscriptionTier.PREMIUM -> Color(0xFFFFD700)
                            else -> MaterialTheme.colors.onSurface
                        }
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 狀態描述
                Text(
                    text = when {
                        subscriptionStatus == null -> stringResource(R.string.checking_subscription)
                        subscriptionStatus.isPremiumUser -> stringResource(R.string.premium_user) + " - " + stringResource(R.string.unlimited_wallets)
                        subscriptionStatus.isActive -> stringResource(R.string.free_user) + " - " + stringResource(R.string.subscription_active)
                        else -> stringResource(R.string.subscription_expired)
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
                
                // 錢包限制信息
                if (subscriptionStatus != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    val walletLimitText = when (subscriptionStatus.tier) {
                        SubscriptionTier.PREMIUM -> stringResource(R.string.unlimited_wallets)
                        SubscriptionTier.FREE -> stringResource(R.string.wallet_limit_free, 2)
                    }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(12.dp)
                        )
                        
                        Spacer(modifier = Modifier.width(4.dp))
                        
                        Text(
                            text = walletLimitText,
                            fontSize = 10.sp,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
                
                // 到期提醒
                if (subscriptionStatus?.isExpiringSoon == true && subscriptionStatus.daysRemaining != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFFF6B35).copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.subscription_expiring_soon, subscriptionStatus.daysRemaining),
                            fontSize = 10.sp,
                            color = Color(0xFFFF6B35),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                
                // 升級按鈕（僅限免費用戶）
                if (subscriptionStatus?.tier == SubscriptionTier.FREE) {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Chip(
                        onClick = onUpgradeClick,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ChipDefaults.chipColors(
                            backgroundColor = Color(0xFFFFD700),
                            contentColor = Color.Black
                        ),
                        label = {
                            Text(
                                text = stringResource(R.string.upgrade_to_premium),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    )
                }
            }
        }
    }
}

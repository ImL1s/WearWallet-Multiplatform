package com.cbstudio.wearwallet.presentation.subscription

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState

/**
 * 訂閱管理面板 — Dark Glassmorphism
 * 
 * 顯示當前訂閱狀態、用量統計和管理動作
 */
@Composable
fun SubscriptionManagementScreen(
    onBackClick: () -> Unit = {},
    onChangePlan: () -> Unit = {},
    onCancelSubscription: () -> Unit = {},
    onRestorePurchases: () -> Unit = {},
    modifier: Modifier = Modifier,
    // 訂閱狀態（由 ViewModel 注入）
    currentPlan: String = "FREE",
    expiryDate: String = "",
    isAutoRenew: Boolean = false,
    walletsUsed: Int = 1,
    walletsLimit: Int = 3
) {
    val scrollState = rememberScalingLazyListState()
    
    val surfaceColor = Color(0xFF1E1B2E)
    val cardColor = Color(0xFF2A2640)
    val primaryColor = Color(0xFF6366F1)
    val accentColor = Color(0xFF10B981)
    val warningColor = Color(0xFFEAB308)
    
    val isPremium = currentPlan != "FREE"
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(surfaceColor)
    ) {
        ScalingLazyColumn(
            state = scrollState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ═══ Current Plan Card ═══
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = cardColor),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Plan badge
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isPremium) primaryColor.copy(alpha = 0.2f)
                                    else Color(0xFF3A3550)
                                )
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (isPremium) "✦ PREMIUM" else "FREE",
                                color = if (isPremium) primaryColor else Color(0xFFADA8C3),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "當前方案",
                            color = Color(0xFFADA8C3),
                            fontSize = 10.sp
                        )
                        
                        if (isPremium && expiryDate.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    if (isAutoRenew) Icons.Filled.Refresh else Icons.Filled.DateRange,
                                    contentDescription = null,
                                    tint = if (isAutoRenew) accentColor else warningColor,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isAutoRenew) "自動續訂 · $expiryDate" else "到期日 · $expiryDate",
                                    color = if (isAutoRenew) accentColor else warningColor,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
            
            // ═══ Usage Stats ═══
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = cardColor),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "使用量",
                            color = Color(0xFFADA8C3),
                            fontSize = 10.sp
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Wallet usage
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Filled.AccountBalanceWallet,
                                contentDescription = null,
                                tint = primaryColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "錢包",
                                color = Color.White,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = if (isPremium) "$walletsUsed / ∞" else "$walletsUsed / $walletsLimit",
                                color = if (!isPremium && walletsUsed >= walletsLimit) warningColor else accentColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                        
                        // Progress bar
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { if (isPremium) 0.1f else walletsUsed.toFloat() / walletsLimit },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = if (!isPremium && walletsUsed >= walletsLimit) warningColor else accentColor,
                            trackColor = Color(0xFF3A3550)
                        )
                        
                        if (!isPremium && walletsUsed >= walletsLimit) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "⚠️ 已達上限，升級 Premium 解鎖無限錢包",
                                color = warningColor,
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }
            
            // ═══ Quick Actions ═══
            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (!isPremium) {
                        // Upgrade button
                        Button(
                            onClick = onChangePlan,
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                        ) {
                            Icon(Icons.Filled.Star, null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("升級 Premium", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        // Change plan
                        ActionRow(
                            icon = Icons.Filled.SwapHoriz,
                            label = "變更方案",
                            onClick = onChangePlan,
                            cardColor = cardColor
                        )
                        
                        // Cancel
                        ActionRow(
                            icon = Icons.Filled.Cancel,
                            label = "取消訂閱",
                            onClick = onCancelSubscription,
                            tintColor = Color(0xFFEF4444),
                            cardColor = cardColor
                        )
                    }
                    
                    // Restore purchases
                    ActionRow(
                        icon = Icons.Filled.Restore,
                        label = "恢復購買",
                        onClick = onRestorePurchases,
                        cardColor = cardColor
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tintColor: Color = Color(0xFFADA8C3),
    cardColor: Color
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = tintColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = Color(0xFF4B4560),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
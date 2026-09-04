package com.cbstudio.wearwallet.presentation.subscription

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState

/**
 * Premium 訂閱付費牆 — Dark Glassmorphism + Crypto Neon 設計
 * 
 * Features:
 * - 動態漸層 Premium 冠冕圖標
 * - FREE vs PREMIUM 功能比較
 * - 月/年計劃選擇器（帶 BEST VALUE 徽章）
 * - 漸層 CTA 按鈕
 * - 恢復購買 / 條款連結
 */
@Composable
fun SubscriptionScreen(
    onBackClick: () -> Unit = {},
    onSubscribe: (planId: String) -> Unit = {},
    onRestorePurchases: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScalingLazyListState()
    var selectedPlan by remember { mutableStateOf("yearly") } // "monthly" or "yearly"
    
    // 漸層色彩
    val primaryGradient = Brush.linearGradient(
        colors = listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))
    )
    val accentColor = Color(0xFF10B981) // Emerald
    val surfaceColor = Color(0xFF1E1B2E)
    val cardColor = Color(0xFF2A2640)
    
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
            // ═══ Hero Section ═══
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    // Premium Crown Icon with glow
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(56.dp)
                            .shadow(12.dp, CircleShape, ambientColor = Color(0xFF6366F1))
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(Color(0xFF8B5CF6), Color(0xFF6366F1))
                                )
                            )
                    ) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = "Premium",
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "WearWallet Premium",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 16.sp
                    )
                    
                    Text(
                        text = "解鎖完整功能",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFADA8C3),
                        fontSize = 11.sp
                    )
                }
            }
            
            // ═══ Feature Comparison ═══
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = cardColor),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "功能比較",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFFADA8C3),
                            fontSize = 10.sp
                        )
                        
                        FeatureRow("基本錢包管理", true, true)
                        FeatureRow("3 個錢包", true, false, "FREE")
                        FeatureRow("無限錢包", false, true)
                        FeatureRow("AI 投資顧問", false, true)
                        FeatureRow("進階分析", false, true)
                        FeatureRow("優先客戶支援", false, true)
                    }
                }
            }
            
            // ═══ Plan Selector ═══
            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Monthly Plan
                    PlanCard(
                        title = "月訂閱",
                        price = "$2.99",
                        period = "/月",
                        isSelected = selectedPlan == "monthly",
                        onClick = { selectedPlan = "monthly" },
                        cardColor = cardColor,
                        primaryColor = Color(0xFF6366F1)
                    )
                    
                    // Yearly Plan with BEST VALUE badge
                    PlanCard(
                        title = "年訂閱",
                        price = "$29.99",
                        period = "/年",
                        isSelected = selectedPlan == "yearly",
                        onClick = { selectedPlan = "yearly" },
                        badge = "省 17%",
                        cardColor = cardColor,
                        primaryColor = Color(0xFF6366F1),
                        accentColor = accentColor
                    )
                }
            }
            
            // ═══ CTA Button ═══
            item {
                Button(
                    onClick = {
                        val planId = if (selectedPlan == "monthly") "premium_monthly" else "premium_yearly"
                        onSubscribe(planId)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF6366F1)
                    ),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Text(
                        text = "立即訂閱",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                }
            }
            
            // ═══ Restore + Terms ═══
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "恢復購買",
                        color = Color(0xFF8B5CF6),
                        fontSize = 11.sp,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable { onRestorePurchases() }
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = "訂閱可隨時取消 · 條款與隱私政策",
                        color = Color(0xFF6B6580),
                        fontSize = 9.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun FeatureRow(
    feature: String,
    freeHas: Boolean,
    premiumHas: Boolean,
    freeLabel: String? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = feature,
            color = Color.White,
            fontSize = 11.sp,
            modifier = Modifier.weight(1f)
        )
        
        // Free column
        if (freeLabel != null) {
            Text(
                text = freeLabel,
                color = Color(0xFF6B6580),
                fontSize = 9.sp
            )
        } else {
            Icon(
                imageVector = if (freeHas) Icons.Filled.Check else Icons.Filled.Close,
                contentDescription = null,
                tint = if (freeHas) Color(0xFF10B981) else Color(0xFF4B4560),
                modifier = Modifier.size(14.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Premium column
        Icon(
            imageVector = if (premiumHas) Icons.Filled.Check else Icons.Filled.Close,
            contentDescription = null,
            tint = if (premiumHas) Color(0xFF10B981) else Color(0xFF4B4560),
            modifier = Modifier.size(14.dp)
        )
    }
}

@Composable
private fun PlanCard(
    title: String,
    price: String,
    period: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    badge: String? = null,
    cardColor: Color,
    primaryColor: Color,
    accentColor: Color = Color(0xFF10B981)
) {
    val borderColor = if (isSelected) primaryColor else Color(0xFF3A3550)
    
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) cardColor.copy(alpha = 0.9f) else cardColor.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            // Radio indicator
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .border(2.dp, if (isSelected) primaryColor else Color(0xFF4B4560), CircleShape)
            ) {
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(primaryColor)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(10.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    if (badge != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = badge,
                            color = accentColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(
                                    accentColor.copy(alpha = 0.15f),
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }
            
            Text(
                text = price,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Text(
                text = period,
                color = Color(0xFF6B6580),
                fontSize = 10.sp
            )
        }
    }
}
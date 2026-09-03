package com.cbstudio.wearwallet.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Payment
import com.cbstudio.wearwallet.R

/**
 * ULTRATHINK - NFC 支付按鈕組件
 * 
 * 主畫面的 NFC Tap-to-Pay 入口按鈕
 * 特色：
 * 1. 吸引人的漸變背景
 * 2. 脈動動畫提示功能可用
 * 3. 符合 Wear OS Material 3 設計
 */
@Composable
fun NFCPaymentButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    // 脈動動畫
    val infiniteTransition = rememberInfiniteTransition()
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 2000,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    val shimmerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1500,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .scale(if (enabled) scale else 1f),
        enabled = enabled,
        backgroundPainter = CardDefaults.cardBackgroundPainter(
            startBackgroundColor = Color.Transparent,
            endBackgroundColor = Color.Transparent
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colors.primary.copy(alpha = shimmerAlpha),
                            MaterialTheme.colors.primaryVariant.copy(alpha = shimmerAlpha * 0.8f)
                        )
                    ),
                    shape = RoundedCornerShape(20.dp)
                )
                .clip(RoundedCornerShape(20.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // NFC 圖標
                Icon(
                    imageVector = Icons.Default.Payment,
                    contentDescription = "NFC",
                    modifier = Modifier.size(36.dp),
                    tint = Color.White
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                // 文字
                Column(
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.tap_to_pay),
                        style = MaterialTheme.typography.title3,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = stringResource(R.string.nfc_payment),
                        style = MaterialTheme.typography.caption2,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
            
            // "NEW" 標籤
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(
                        color = MaterialTheme.colors.secondary,
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "NEW",
                    style = MaterialTheme.typography.caption3,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

/**
 * 精簡版 NFC 支付按鈕（用於空間有限的場景）
 */
@Composable
fun CompactNFCPaymentButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition()
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    CompactButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            backgroundColor = MaterialTheme.colors.primary.copy(alpha = glowAlpha)
        )
    ) {
        Icon(
            imageVector = Icons.Default.Payment,
            contentDescription = stringResource(R.string.nfc_payment),
            modifier = Modifier.size(24.dp)
        )
    }
}

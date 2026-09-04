package com.cbstudio.wearwallet.presentation.subscription

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
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
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TextButton
import com.cbstudio.wearwallet.R

/**
 * 訂閱升級對話框
 * 用於在需要 Premium 功能時提示用戶升級
 */
@Composable
fun SubscriptionUpgradeDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    onUpgradeClick: () -> Unit,
    title: String = stringResource(R.string.upgrade_required),
    message: String = stringResource(R.string.upgrade_message_default),
    feature: String? = null
) {
    AlertDialog(
        visible = visible,
        onDismissRequest = onDismissRequest,
        icon = {
            androidx.wear.compose.material3.Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = Color(0xFFFFD700),
                modifier = Modifier.size(24.dp)
            )
        },
        title = {
            androidx.wear.compose.material3.Text(
                text = title,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                androidx.wear.compose.material.Text(
                    text = message,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.body2
                )
                
                feature?.let {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF2196F3).copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        androidx.wear.compose.material.Text(
                            text = it,
                            fontSize = 11.sp,
                            color = Color(0xFF2196F3),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                
                // Premium 功能列表
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        R.string.premium_feature_unlimited_wallets,
                        R.string.premium_feature_ai_assistant,
                        R.string.premium_feature_advanced_analytics,
                        R.string.premium_feature_priority_support
                    ).forEach { featureRes ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            androidx.wear.compose.material.Text(
                                text = "✓ ${stringResource(featureRes)}",
                                fontSize = 10.sp,
                                color = Color(0xFF4CAF50)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            androidx.wear.compose.material3.Button(
                onClick = {
                    onDismissRequest()
                    onUpgradeClick()
                },
                colors = androidx.wear.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFD700),
                    contentColor = Color.Black
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                androidx.wear.compose.material3.Text(
                    text = stringResource(R.string.upgrade_now),
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            androidx.wear.compose.material3.TextButton(
                onClick = onDismissRequest,
                modifier = Modifier.fillMaxWidth()
            ) {
                androidx.wear.compose.material3.Text(
                    text = stringResource(R.string.maybe_later),
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    )
}

/**
 * 錢包限制對話框
 * 當用戶達到免費版錢包限制時顯示
 */
@Composable
fun WalletLimitDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    onUpgradeClick: () -> Unit,
    currentWallets: Int,
    maxWallets: Int
) {
    SubscriptionUpgradeDialog(
        visible = visible,
        onDismissRequest = onDismissRequest,
        onUpgradeClick = onUpgradeClick,
        title = stringResource(R.string.wallet_limit_reached),
        message = stringResource(
            R.string.wallet_limit_message,
            currentWallets,
            maxWallets
        ),
        feature = stringResource(R.string.unlimited_wallets)
    )
}

/**
 * AI 功能升級對話框
 * 當用戶嘗試使用 AI 功能時顯示
 */
@Composable
fun AIFeatureUpgradeDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    onUpgradeClick: () -> Unit,
    aiFeature: String
) {
    SubscriptionUpgradeDialog(
        visible = visible,
        onDismissRequest = onDismissRequest,
        onUpgradeClick = onUpgradeClick,
        title = stringResource(R.string.ai_feature_premium_only),
        message = stringResource(R.string.ai_feature_upgrade_message),
        feature = aiFeature
    )
}

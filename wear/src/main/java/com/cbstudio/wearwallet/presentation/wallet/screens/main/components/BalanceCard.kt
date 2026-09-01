package com.cbstudio.wearwallet.presentation.wallet.screens.main.components

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.*
import androidx.compose.ui.platform.testTag
import com.cbstudio.wearwallet.R
import com.cbstudio.wearwallet.presentation.TestTags
import com.cbstudio.wearwallet.core.domain.model.WalletAccount
import com.cbstudio.wearwallet.core.domain.model.WalletType
import com.cbstudio.wearwallet.core.domain.model.ChainType



/**
 * 餘額卡片組件 - 連接到 coreKmp
 */
@Composable
fun BalanceCard(
    currentWallet: WalletAccount?,
    chainType: ChainType,
    balance: Double,
    balanceUsd: String,
    tokenPrice: Double?,
    isLoading: Boolean = false,
    isScanningTokens: Boolean = false,
    onRefresh: () -> Unit = {},
    onSelectToken: () -> Unit = {},
    onScanTokens: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onSelectToken,
        modifier = modifier
            .fillMaxWidth()
            .testTag(TestTags.BALANCE_CARD),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 頂部：錢包資訊和刷新按鈕
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 錢包資訊
                if (currentWallet != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = when (currentWallet.walletType) {
                                WalletType.HOT_WALLET -> Icons.Outlined.LocalFireDepartment
                                WalletType.KEYSTONE_COLD -> Icons.Outlined.AcUnit
                                else -> Icons.Outlined.LocalFireDepartment
                            },
                            contentDescription = null,
                            tint = when (currentWallet.walletType) {
                                WalletType.HOT_WALLET -> MaterialTheme.colorScheme.tertiary
                                WalletType.KEYSTONE_COLD -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = currentWallet.name,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    // 沒有錢包時顯示
                    Text(
                        text = stringResource(R.string.not_selected_wallet),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 刷新按鈕
                IconButton(
                    onClick = onRefresh,
                    enabled = !isLoading && !isScanningTokens,
                    modifier = Modifier.size(28.dp)
                ) {
                    if (isLoading || isScanningTokens) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.refresh),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // 中間：餘額顯示
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 代幣符號
                AnimatedContent(
                    targetState = chainType,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(300)) togetherWith
                                fadeOut(animationSpec = tween(300))
                    },
                    label = "token_symbol"
                ) { chain ->
                    Text(
                        text = when (chain) {
                            ChainType.ETHEREUM -> "ETH"
                            ChainType.BSC -> "BNB"
                            ChainType.POLYGON -> "MATIC"
                            ChainType.BITCOIN -> "BTC"
                            ChainType.LITECOIN -> "LTC"
                            ChainType.DOGECOIN -> "DOGE"
                            ChainType.BITCOIN_CASH -> "BCH"
                            ChainType.ARBITRUM -> "ETH"
                            ChainType.OPTIMISM -> "ETH"
                            ChainType.AVALANCHE -> "AVAX"
                            ChainType.BASE -> "ETH"
                            ChainType.CRONOS -> "CRO"
                            ChainType.SOLANA -> "SOL"
                            ChainType.SEPOLIA -> "ETH"
                            ChainType.GOERLI -> "ETH"
                            ChainType.MUMBAI -> "MATIC"
                            else -> chain.name.take(4).uppercase()
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag(TestTags.CHAIN_SYMBOL)
                    )
                }

                // 餘額數字
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 3.dp
                    )
                } else {
                    AnimatedContent(
                        targetState = balance,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(300)) togetherWith
                                fadeOut(animationSpec = tween(300))
                        },
                        label = "balance_amount"
                    ) { amount ->
                        Text(
                            text = formatAdaptiveBalance(amount),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Visible
                        )
                    }
                }

                // USD 價值
                AnimatedContent(
                    targetState = balanceUsd,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(300)) togetherWith
                                fadeOut(animationSpec = tween(300))
                    },
                    label = "balance_usd"
                ) { usdValue ->
                    Text(
                        text = "≈ $$usdValue",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 底部提示 - 點擊切換代幣
            Text(
                text = stringResource(R.string.tap_to_switch_token),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
package com.cbstudio.wearwallet.presentation.wallet.screens.main.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.*
import androidx.compose.ui.platform.testTag
import com.cbstudio.wearwallet.R
import com.cbstudio.wearwallet.presentation.TestTags
import com.cbstudio.wearwallet.core.domain.model.WalletAccount
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
        // 錢包名稱已在 WalletSwitcher，這裡只放餘額 + 角落刷新，讓首屏塞得下四顆操作鈕
        Box(modifier = Modifier.fillMaxWidth()) {
            IconButton(
                onClick = onRefresh,
                enabled = !isLoading && !isScanningTokens,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(40.dp)
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

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
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
                        text = "≈ $usdValue",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
package com.cbstudio.wearwallet.presentation.ui.token

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.*
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.core.multichain.tokens.TokenTransferManager
import com.cbstudio.wearwallet.presentation.theme.SuccessGreen
import org.koin.androidx.compose.koinViewModel
import com.cbstudio.wearwallet.presentation.TestTags
import com.cbstudio.wearwallet.presentation.qa.WearQaFixtureBanner
import java.math.BigDecimal
import java.text.DecimalFormat

/**
 * Token list screen for Wear OS
 * Displays user's token balances and allows transfers
 */
@Composable
fun TokenListScreen(
    walletAddress: String,
    chainType: MultiChainType,
    onTokenClick: (TokenTransferManager.TokenInfo) -> Unit,
    onBackClick: () -> Unit,
    onViewHistory: (TokenTransferManager.TokenInfo) -> Unit = {},
    onAddToken: () -> Unit = {},
    viewModel: TokenListViewModel = koinViewModel()
) {
    val tokens by viewModel.tokens.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val totalValue by viewModel.totalValue.collectAsState()
    val error by viewModel.error.collectAsState()

    LaunchedEffect(walletAddress, chainType) {
        viewModel.loadTokens(walletAddress, chainType)
    }

    val listState = rememberScalingLazyListState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(
                top = 32.dp,
                bottom = 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            item {
                Text(
                    text = "代幣",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            item {
                WearQaFixtureBanner()
            }

            if (!error.isNullOrBlank()) {
                item {
                    Text(
                        text = error.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .testTag(TestTags.ERROR_MESSAGE)
                    )
                }
            }

            // Chain info
            item {
                Card(
                    onClick = { },
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .heightIn(min = 48.dp)
                ) {
                    Column {
                        Text(
                            text = chainType.name,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "總值: $${"%.2f".format(totalValue)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Loading indicator
            if (isLoading) {
                item {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            // Token list
            items(tokens) { tokenBalance ->
                TokenCard(
                    tokenBalance = tokenBalance,
                    onClick = { onTokenClick(tokenBalance.token) },
                    onHistoryClick = { onViewHistory(tokenBalance.token) }
                )
            }

            // Add token button
            item {
                Button(
                    onClick = onAddToken,
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .padding(top = 8.dp)
                        .heightIn(min = 48.dp)
                        .testTag(TestTags.TOKEN_LIST_ADD_BUTTON)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "添加代幣",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            // Back button
            item {
                FilledTonalButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .padding(top = 4.dp)
                        .heightIn(min = 48.dp)
                ) {
                    Text(
                        text = "返回",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

/**
 * Individual token card component
 */
@Composable
fun TokenCard(
    tokenBalance: TokenTransferManager.TokenBalance,
    onClick: () -> Unit,
    onHistoryClick: () -> Unit = {}
) {
    val decimalFormat = DecimalFormat("#,##0.######")

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .padding(vertical = 2.dp)
            .heightIn(min = 48.dp)
            .testTag(TestTags.tokenRow(tokenBalance.token.symbol))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Token symbol and name
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = tokenBalance.token.symbol,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = tokenBalance.token.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Balance and value
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = decimalFormat.format(tokenBalance.balance),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                tokenBalance.usdValue?.let { value ->
                    Text(
                        text = "$${"%.2f".format(value)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = SuccessGreen,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * Token transfer dialog
 * Note: Wear OS doesn't have Dialog/Alert components, use a separate screen instead
 */
@Composable
fun TokenTransferDialog(
    token: TokenTransferManager.TokenInfo,
    onDismiss: () -> Unit,
    onConfirm: (recipient: String, amount: BigDecimal) -> Unit
) {
    var recipient by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }

    // For Wear OS, we'll use a Card with overlay effect instead
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            onClick = { },
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Title
                Text(
                    text = "轉帳 ${token.symbol}",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Recipient address
                Text(
                    text = "接收地址",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = if (recipient.isEmpty()) "點擊輸入" else recipient,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Amount
                Text(
                    text = "數量",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = if (amount.isEmpty()) "0.00" else amount,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                // Token info
                Text(
                    text = token.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Text(
                            text = "取消",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    Button(
                        onClick = {
                            val amountDecimal = amount.toBigDecimalOrNull()
                            if (recipient.isNotEmpty() && amountDecimal != null && amountDecimal > BigDecimal.ZERO) {
                                onConfirm(recipient, amountDecimal)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Text(
                            text = "確認",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}

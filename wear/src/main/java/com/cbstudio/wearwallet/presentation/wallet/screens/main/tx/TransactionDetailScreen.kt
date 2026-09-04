package com.cbstudio.wearwallet.presentation.wallet.screens.main.tx

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.cbstudio.wearwallet.core.domain.model.Transaction
import com.cbstudio.wearwallet.core.domain.model.TransactionDirection
import com.cbstudio.wearwallet.core.domain.model.TransactionStatus
import com.cbstudio.wearwallet.presentation.qa.WearQaFixtureBanner
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * 交易詳情畫面 — 顯示單筆交易的完整資訊（狀態、金額、地址、時間、Hash）
 */
@Composable
fun TransactionDetailScreen(
    transactionId: String,
    onBackClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: TransactionDetailViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScalingLazyListState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    LaunchedEffect(transactionId) {
        viewModel.load(transactionId)
    }

    ScalingLazyColumn(
        state = scrollState,
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when {
            uiState.isLoading -> {
                item {
                    CircularProgressIndicator(modifier = Modifier.size(40.dp))
                }
                item {
                    Text(
                        text = "載入交易詳情…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            uiState.transaction != null -> {
                item {
                    WearQaFixtureBanner()
                }
                if (!uiState.error.isNullOrBlank()) {
                    item {
                        Text(
                            text = uiState.error.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
                uiState.transaction?.let { tx ->
                    transactionDetailContent(
                        tx = tx,
                        onCopyHash = {
                            clipboardManager.setText(AnnotatedString(tx.hash))
                            android.widget.Toast
                                .makeText(context, "已複製交易 Hash", android.widget.Toast.LENGTH_SHORT)
                                .show()
                        },
                        onBackClick = onBackClick
                    )
                }
            }

            uiState.error != null -> {
                item {
                    WearQaFixtureBanner()
                }
                item {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(32.dp)
                    )
                }
                item {
                    Text(
                        text = uiState.error ?: "發生錯誤",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                item {
                    Button(
                        onClick = onBackClick,
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(48.dp)
                    ) {
                        Text("返回")
                    }
                }
            }

            else -> {
                item {
                    WearQaFixtureBanner()
                }
            }
        }
    }
}

private fun androidx.wear.compose.foundation.lazy.ScalingLazyListScope.transactionDetailContent(
    tx: Transaction,
    onCopyHash: () -> Unit,
    onBackClick: () -> Unit
) {
    val (statusIcon, statusLabel) = tx.status.displayInfo()
    val isIncoming = tx.direction == TransactionDirection.INCOMING
    val sign = if (isIncoming) "+" else "-"

    item {
        Icon(
            imageVector = statusIcon,
            contentDescription = statusLabel,
            tint = tx.status.tintColor(),
            modifier = Modifier
                .size(32.dp)
                .padding(top = 4.dp)
        )
    }

    item {
        Text(
            text = "$sign${tx.getFormattedAmount()} ${tx.getDisplaySymbol()}",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }

    item {
        Text(
            text = statusLabel,
            style = MaterialTheme.typography.labelMedium,
            color = tx.status.tintColor()
        )
    }

    item {
        DetailCard(label = if (isIncoming) "來自" else "發送至") {
            Text(
                text = (if (isIncoming) tx.from else tx.to).shortenAddress(),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace
            )
        }
    }

    tx.timestamp?.let { instant ->
        item {
            DetailCard(label = "時間") {
                val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
                Text(
                    text = "%04d-%02d-%02d %02d:%02d".format(
                        local.year, local.monthNumber, local.dayOfMonth,
                        local.hour, local.minute
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }

    item {
        DetailCard(label = "交易 Hash") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = tx.getShortHash(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace
                )
                Button(
                    onClick = onCopyHash,
                    modifier = Modifier.size(48.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "複製交易 Hash",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

    if (tx.confirmations > 0) {
        item {
            DetailCard(label = "確認數") {
                Text(
                    text = "${tx.confirmations}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }

    item {
        Spacer(modifier = Modifier.height(4.dp))
    }

    item {
        Button(
            onClick = onBackClick,
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(48.dp)
        ) {
            Text("返回")
        }
    }
}

@Composable
private fun DetailCard(
    label: String,
    content: @Composable () -> Unit
) {
    Card(
        onClick = {},
        enabled = false,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) {
        Column(modifier = Modifier.padding(4.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            content()
        }
    }
}

private fun TransactionStatus.displayInfo(): Pair<ImageVector, String> = when (this) {
    TransactionStatus.CONFIRMED -> Icons.Default.CheckCircle to "已確認"
    TransactionStatus.PENDING -> Icons.Default.Schedule to "處理中"
    TransactionStatus.FAILED -> Icons.Default.Error to "失敗"
    TransactionStatus.DROPPED -> Icons.Default.Error to "已丟棄"
    TransactionStatus.REPLACED -> Icons.Default.Schedule to "已替換"
    TransactionStatus.CANCELLED -> Icons.Default.Error to "已取消"
    TransactionStatus.SEND -> Icons.Default.Schedule to "已送出"
}

@Composable
private fun TransactionStatus.tintColor() = when (this) {
    TransactionStatus.CONFIRMED -> MaterialTheme.colorScheme.primary
    TransactionStatus.FAILED,
    TransactionStatus.DROPPED,
    TransactionStatus.CANCELLED -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun String.shortenAddress(): String =
    if (length > 14) "${take(8)}…${takeLast(6)}" else this

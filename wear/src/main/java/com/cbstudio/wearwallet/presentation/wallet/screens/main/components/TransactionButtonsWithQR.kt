package com.cbstudio.wearwallet.presentation.wallet.screens.main.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import com.cbstudio.wearwallet.R
import com.cbstudio.wearwallet.presentation.TestTags

/**
 * Transaction Buttons with Swap - 4 button layout
 *
 * [Send] [Swap] [QR] [Receive]
 *
 * Send/Receive use the primary (filled) emphasis; Swap/QR are tonal
 * secondary actions. All targets are >= 48dp per Wear touch guidelines.
 */
@Composable
fun TransactionButtonsWithQR(
    onSendClick: () -> Unit,
    onReceiveClick: () -> Unit,
    onScanQrClick: () -> Unit,  // QR Scanner
    onSwapClick: () -> Unit = {},  // Swap
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        // 發送按鈕（主要動作 - primary）
        FilledIconButton(
            onClick = onSendClick,
            enabled = enabled,
            modifier = Modifier
                .size(48.dp)
                .testTag(TestTags.SEND_BUTTON)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.CallMade,
                contentDescription = stringResource(R.string.send),
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }

        // Swap 按鈕（次要動作 - tonal）
        FilledTonalIconButton(
            onClick = onSwapClick,
            enabled = enabled,
            modifier = Modifier
                .size(48.dp)
                .testTag(TestTags.SWAP_BUTTON)
        ) {
            Icon(
                imageVector = Icons.Filled.SwapHoriz,
                contentDescription = "Swap"
            )
        }

        // QR 掃描按鈕（次要動作 - tonal）
        FilledTonalIconButton(
            onClick = onScanQrClick,
            enabled = enabled,
            modifier = Modifier
                .size(48.dp)
                .testTag(TestTags.QR_SCAN_BUTTON)
        ) {
            Icon(
                imageVector = Icons.Default.QrCodeScanner,
                contentDescription = stringResource(R.string.scan_qr_code)
            )
        }

        // 接收按鈕（主要動作 - primary）
        FilledIconButton(
            onClick = onReceiveClick,
            enabled = enabled,
            modifier = Modifier
                .size(48.dp)
                .testTag(TestTags.RECEIVE_BUTTON)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.CallReceived,
                contentDescription = stringResource(R.string.receive),
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

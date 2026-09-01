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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import com.cbstudio.wearwallet.R

/**
 * Transaction Buttons with Swap - 4 button layout
 * 
 * [Send] [Swap] [QR] [Receive]
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
        // 發送按鈕
        FilledIconButton(
            onClick = onSendClick,
            enabled = enabled,
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.CallMade,
                contentDescription = stringResource(R.string.send),
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }

        // Swap 按鈕
        FilledIconButton(
            onClick = onSwapClick,
            enabled = enabled,
            modifier = Modifier
                .size(44.dp)
                .testTag(com.cbstudio.wearwallet.presentation.TestTags.SWAP_BUTTON),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = Color(0xFF9C27B0) // Purple for Swap
            )
        ) {
            Icon(
                imageVector = Icons.Filled.SwapHoriz,
                contentDescription = "Swap",
                tint = Color.White
            )
        }

        // QR 掃描按鈕
        FilledIconButton(
            onClick = onScanQrClick,
            enabled = enabled,
            modifier = Modifier.size(44.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = Color(0xFF4285F4) // Scanner Blue
            )
        ) {
            Icon(
                imageVector = Icons.Default.QrCodeScanner,
                contentDescription = stringResource(R.string.scan_qr_code),
                tint = Color.White
            )
        }

        // 接收按鈕
        FilledIconButton(
            onClick = onReceiveClick,
            enabled = enabled,
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.CallReceived,
                contentDescription = stringResource(R.string.receive),
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

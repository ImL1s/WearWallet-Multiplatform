package com.cbstudio.wearwallet.presentation.qrscanner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.cbstudio.wearwallet.R
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import com.cbstudio.wearwallet.data.repository.QRScanType
import com.cbstudio.wearwallet.presentation.TestTags
import com.cbstudio.wearwallet.presentation.qa.WearQaFixtureBanner
import com.cbstudio.wearwallet.presentation.qa.WearQaFixtures
import com.cbstudio.wearwallet.presentation.qa.WearQaHarness

/**
 * QR 掃描器畫面 - 透過手機掃描
 */
@Composable
fun QrScannerScreen(
    onBackClick: () -> Unit = {},
    onQrCodeScanned: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: QrScannerViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScalingLazyListState()
    
    // 當收到掃描結果時回傳
    LaunchedEffect(uiState.scanResult) {
        uiState.scanResult?.let {
            onQrCodeScanned(it)
            viewModel.clearScanResult()
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag(TestTags.QR_SCANNER_SCREEN)
    ) {
        ScalingLazyColumn(
            state = scrollState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 標題
            item {
                Text(
                    text = stringResource(R.string.scan_qr_code),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            item {
                WearQaFixtureBanner()
            }

            if (WearQaHarness.isActive()) {
                item {
                    OutlinedButton(
                        onClick = { viewModel.simulateScan(WearQaFixtures.QR_EIP681) },
                        enabled = !uiState.isRequestingPhone && !uiState.isWaitingForResult,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(TestTags.QR_SIMULATE_SCAN_BUTTON)
                    ) {
                        Text("模擬掃描")
                    }
                }
            }

            // 掃描類型選擇
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF2C2C2C)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.qr_scan_type),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        // 第一行：地址和交易
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            QRTypeChip(
                                type = QRScanType.ADDRESS,
                                selected = uiState.scanType == QRScanType.ADDRESS,
                                onClick = { viewModel.setScanType(QRScanType.ADDRESS) }
                            )
                            QRTypeChip(
                                type = QRScanType.TRANSACTION,
                                selected = uiState.scanType == QRScanType.TRANSACTION,
                                onClick = { viewModel.setScanType(QRScanType.TRANSACTION) }
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        // 第二行：Keystone 連接和簽名
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            QRTypeChip(
                                type = QRScanType.KEYSTONE_CONNECT,
                                selected = uiState.scanType == QRScanType.KEYSTONE_CONNECT,
                                onClick = { viewModel.setScanType(QRScanType.KEYSTONE_CONNECT) }
                            )
                            QRTypeChip(
                                type = QRScanType.KEYSTONE_SIGN,
                                selected = uiState.scanType == QRScanType.KEYSTONE_SIGN,
                                onClick = { viewModel.setScanType(QRScanType.KEYSTONE_SIGN) }
                            )
                        }
                    }
                }
            }
            
            // 狀態顯示
            item {
                when {
                    uiState.isRequestingPhone -> {
                        StatusCard(
                            icon = Icons.Default.PhoneAndroid,
                            title = stringResource(R.string.qr_connecting_phone),
                            message = stringResource(R.string.qr_requesting_phone_scanner),
                            showProgress = true
                        )
                    }
                    uiState.isWaitingForResult -> {
                        StatusCard(
                            icon = Icons.Default.QrCodeScanner,
                            title = stringResource(R.string.qr_waiting_for_scan),
                            message = stringResource(R.string.qr_align_phone_hint),
                            showProgress = true
                        )
                    }
                    uiState.errorMessage != null -> {
                        StatusCard(
                            icon = Icons.Default.Error,
                            title = stringResource(R.string.scan_failed),
                            message = uiState.errorMessage ?: stringResource(R.string.unknown),
                            showProgress = false,
                            isError = true
                        )
                    }
                    else -> {
                        StatusCard(
                            icon = Icons.Default.CameraAlt,
                            title = stringResource(R.string.preparing_scanner),
                            message = stringResource(R.string.qr_tap_to_start),
                            showProgress = false
                        )
                    }
                }
            }
            
            // 操作按鈕
            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.requestPhoneScan(uiState.scanType) },
                        enabled = !uiState.isRequestingPhone && !uiState.isWaitingForResult,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(TestTags.QR_START_SCAN_BUTTON),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (uiState.errorMessage != null) stringResource(R.string.retry) else stringResource(R.string.start_scan),
                            color = Color.White
                        )
                    }
                    
                    OutlinedButton(
                        onClick = onBackClick,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.back))
                    }
                }
            }
            
            // 提示訊息
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1E1E1E)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.qr_phone_app_required),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QRTypeChip(
    type: QRScanType,
    selected: Boolean,
    onClick: () -> Unit
) {
    Chip(
        onClick = onClick,
        colors = ChipDefaults.chipColors(
            backgroundColor = if (selected) 
                MaterialTheme.colorScheme.primary 
            else 
                Color(0xFF3C3C3C)
        ),
        label = {
            Text(
                text = when (type) {
                    QRScanType.ADDRESS -> stringResource(R.string.address)
                    QRScanType.TRANSACTION -> stringResource(R.string.transaction)
                    QRScanType.KEYSTONE_CONNECT -> stringResource(R.string.keystone)
                    QRScanType.KEYSTONE_SIGN -> stringResource(R.string.signature)
                },
                style = MaterialTheme.typography.labelSmall
            )
        },
        modifier = Modifier
    )
}

@Composable
private fun StatusCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String,
    showProgress: Boolean = false,
    isError: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) 
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
            else 
                Color(0xFF2C2C2C)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = if (isError) 
                    MaterialTheme.colorScheme.error 
                else 
                    MaterialTheme.colorScheme.primary
            )
            
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            if (showProgress) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 3.dp
                )
            }
        }
    }
}
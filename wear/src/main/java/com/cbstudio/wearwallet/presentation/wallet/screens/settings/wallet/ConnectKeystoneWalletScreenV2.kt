package com.cbstudio.wearwallet.presentation.wallet.screens.settings.wallet

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.*
import com.cbstudio.wearwallet.presentation.common.components.LoadingIndicator
import com.cbstudio.wearwallet.presentation.common.components.QRCodeDisplay
import org.koin.androidx.compose.koinViewModel

/**
 * Connect Keystone Wallet Screen V2 - 整合 coreKmp
 * 使用真正的 KeystoneService 實現硬體錢包連接
 */
@Composable
fun ConnectKeystoneWalletScreenV2(
    onNavigateBack: () -> Unit,
    onConnectSuccess: (walletAddress: String) -> Unit,
    onScanQr: () -> Unit,
    viewModel: ConnectKeystoneWalletViewModelV2 = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    // 監聽連接事件
    LaunchedEffect(Unit) {
        viewModel.connectionEvent.collect { event ->
            when (event) {
                is ConnectionEvent.Success -> {
                    // 連接成功，返回第一個地址
                    event.wallet.addresses.firstOrNull()?.let { address ->
                        onConnectSuccess(address.address)
                    }
                }
                is ConnectionEvent.SignatureReceived -> {
                    // 簽名接收處理（如果需要）
                }
                is ConnectionEvent.Error -> {
                    // 錯誤處理
                }
            }
        }
    }
    
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when {
            uiState.isLoading -> {
                LoadingIndicator(
                    message = uiState.statusMessage ?: "處理中..."
                )
            }
            
            uiState.errorMessage != null -> {
                val errorMsg = uiState.errorMessage!! // 使用 !! 確保非 null
                ErrorContent(
                    errorMessage = errorMsg,
                    onRetry = viewModel::retry,
                    onBack = onNavigateBack
                )
            }
            
            uiState.connectedWallet != null -> {
                val wallet = uiState.connectedWallet!! // 使用 !! 確保非 null
                ConnectedContent(
                    wallet = wallet,
                    onDisconnect = viewModel::disconnect,
                    onBack = onNavigateBack
                )
            }
            
            uiState.currentSignRequest != null -> {
                val signRequest = uiState.currentSignRequest!! // 使用 !! 確保非 null
                SignRequestContent(
                    signRequest = signRequest,
                    onCancel = { viewModel.disconnect() }
                )
            }
            
            else -> {
                ConnectContent(
                    isInitialized = uiState.isInitialized,
                    onScanQr = onScanQr,
                    onBack = onNavigateBack
                )
            }
        }
    }
}

@Composable
private fun ConnectContent(
    isInitialized: Boolean,
    onScanQr: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Hardware,
            contentDescription = "Keystone Hardware Wallet",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp)
        )
        
        Text(
            text = "連接 Keystone 錢包",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        Text(
            text = if (isInitialized) {
                "請在 Keystone 設備上顯示 QR Code"
            } else {
                "正在初始化服務..."
            },
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onScanQr,
                enabled = isInitialized,
                colors = ButtonDefaults.buttonColors()
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = "Scan QR",
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Button(
                onClick = onBack,
                colors = ButtonDefaults.filledTonalButtonColors()
            ) {
                Text("返回", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ConnectedContent(
    wallet: com.cbstudio.wearwallet.core.domain.model.keystone.KeystoneWallet,
    onDisconnect: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = "Connected",
            tint = Color.Green,
            modifier = Modifier.size(48.dp)
        )
        
        Text(
            text = "已連接",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Green
        )
        
        Text(
            text = wallet.name,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        wallet.addresses.firstOrNull()?.let { address ->
            Text(
                text = "${address.address.take(6)}...${address.address.takeLast(4)}",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onDisconnect,
                colors = ButtonDefaults.buttonColors()
            ) {
                Text("斷開", fontSize = 12.sp)
            }
            
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors()
            ) {
                Text("完成", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SignRequestContent(
    signRequest: com.cbstudio.wearwallet.core.domain.model.keystone.KeystoneSignRequest,
    onCancel: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "簽名請求",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        // 顯示 QR Code（如果有多個片段，需要動畫顯示）
        if (signRequest.qrCodeData.isNotEmpty()) {
            // 這裡應該顯示動畫 QR Code
            // 暫時顯示第一個片段
            QRCodeDisplay(
                data = signRequest.qrCodeData.first(),
                modifier = Modifier.size(120.dp)
            )
        }
        
        Text(
            text = "請在 Keystone 設備上掃描並簽名",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
        
        Button(
            onClick = onCancel,
            colors = ButtonDefaults.filledTonalButtonColors()
        ) {
            Text("取消", fontSize = 12.sp)
        }
    }
}

@Composable
private fun ErrorContent(
    errorMessage: String,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = "Error",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp)
        )
        
        Text(
            text = "連接失敗",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error
        )
        
        Text(
            text = errorMessage,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors()
            ) {
                Text("重試", fontSize = 12.sp)
            }
            
            Button(
                onClick = onBack,
                colors = ButtonDefaults.filledTonalButtonColors()
            ) {
                Text("返回", fontSize = 12.sp)
            }
        }
    }
}
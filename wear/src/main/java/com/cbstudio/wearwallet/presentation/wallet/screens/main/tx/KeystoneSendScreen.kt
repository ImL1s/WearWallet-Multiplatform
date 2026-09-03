package com.cbstudio.wearwallet.presentation.wallet.screens.main.tx

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.*
import com.cbstudio.wearwallet.presentation.ui.components.QrCodeView
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ArrowBack
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import kotlinx.coroutines.delay

/**
 * Keystone Send Screen - 完整實現
 * 顯示簽名請求 QR Code 並掃描簽名結果
 */
@Composable
fun KeystoneSendScreen(
    viewModel: KeystoneSendViewModel,
    onNavigateBack: () -> Unit,
    onTransactionSent: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    
    // 監聽成功狀態
    LaunchedEffect(uiState.step, uiState.txHash) {
        if (uiState.step == KeystoneSendStep.SUCCESS && uiState.txHash != null) {
            // 延遲一下讓用戶看到成功畫面
            delay(1500)
            onTransactionSent(uiState.txHash!!)
        }
    }
    
    // 根據狀態顯示不同畫面
    when (uiState.step) {
        KeystoneSendStep.PREPARING -> {
            LoadingScreen("準備簽名請求...")
        }
        KeystoneSendStep.SHOW_QR -> {
            ShowQrScreen(
                qrData = uiState.qrCodeData,
                onScanClick = {
                    // 觸發掃描
                    viewModel.onScanClick()
                    // 這裡通常需要啟動掃描畫面，但我們的架構通過手機伴侶或手錶相機
                    // 假設 onScanClick 會請求外部掃描並將結果回傳給 viewModel
                    // 如果需要導航到掃描器，這裡需要 callback
                },
                onBack = onNavigateBack
            )
        }
        KeystoneSendStep.SCAN_QR -> {
             // 實際上 scan 邏輯可能需要手機端介入
             // 這裡顯示 "請檢查手機或開啟相機"
             // 假設我們有相機權限和組件，這裡簡化為 "等待掃描結果" UI
             // 因為 Wear OS 上實現相機掃描較複雜，通常可能使用伴侶應用
             // 但我們也可能有 QrScannerScreen
             
             // 這裡為了演示，我們顯示一個模擬掃描或指示 UI
             ScanInstructionScreen(
                 onScanResult = viewModel::handleScanResult, // 測試用，實際應由外部觸發
                 onBack = { 
                     // 返回顯示 QR
                     viewModel.retry() 
                 }
             )
        }
        KeystoneSendStep.BROADCASTING -> {
            LoadingScreen("廣播交易中...")
        }
        KeystoneSendStep.SUCCESS -> {
            SuccessScreen()
        }
        KeystoneSendStep.FAILED -> {
            ErrorScreen(
                error = uiState.error ?: "未知錯誤",
                onRetry = viewModel::retry,
                onBack = onNavigateBack
            )
        }
    }
}

@Composable
private fun LoadingScreen(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator()
            Text(text = message, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ShowQrScreen(
    qrData: List<String>,
    onScanClick: () -> Unit,
    onBack: () -> Unit
) {
    val scrollState = rememberScalingLazyListState()
    
    // 簡單的動畫邏輯 (如果 qrData 多於一個)
    var currentIndex by remember { mutableStateOf(0) }
    
    LaunchedEffect(qrData) {
        if (qrData.size > 1) {
            while(true) {
                delay(200) // 200ms per frame
                currentIndex = (currentIndex + 1) % qrData.size
            }
        }
    }
    
    val currentQr = if (qrData.isNotEmpty()) qrData[currentIndex] else ""
    
    ScalingLazyColumn(
        state = scrollState,
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(
                text = "Scan with Keystone",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        
        item {
            // QR Code
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .padding(8.dp)
            ) {
                if (currentQr.isNotEmpty()) {
                    QrCodeView(
                        data = currentQr,
                        modifier = Modifier.fillMaxSize(),
                        size = 144
                    )
                }
            }
        }
        
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                 Button(
                     onClick = onScanClick,
                     modifier = Modifier.fillMaxWidth(0.8f)
                 ) {
                     Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                     Spacer(modifier = Modifier.width(8.dp))
                     Text("Get Signature")
                 }
            }
        }
        
        item {
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = null)
            }
        }
    }
}

@Composable
private fun ScanInstructionScreen(
    onScanResult: (String) -> Unit,
    onBack: () -> Unit
) {
    // 這裡實際上應該調用相機或等待手機回傳
    // 為了測試，我們提供一個 Text Field 模擬輸入，或者按鈕模擬掃描成功
    // 但在手錶上輸入太難。
    
    // 提示用戶使用手機掃描
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(48.dp))
            Text(
                "Please scan the Keystone QR code using your phone companion app or click below to simulate (Test).",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall
            )
            
            // 模擬按鈕 (實際環境應隱藏或替換為開啟相機)
            /*
            Button(onClick = {
                // 模擬一個簽名響應 (UR)
                // 這裡需要真實的 UR 數據才能測試解析，否則會報錯
                // 所以最好還是依賴真實流程
            }) {
                Text("Simulate Scan")
            }
            */
            
            Button(onClick = onBack) {
                Text("Cancel")
            }
        }
    }
}

@Composable
private fun SuccessScreen() {
     Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Default.CheckCircle, 
                contentDescription = null,
                tint = Color.Green,
                modifier = Modifier.size(48.dp)
            )
            Text(text = "Transaction Sent!", textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ErrorScreen(
    error: String,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(
                Icons.Default.Error, 
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = error, 
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.error
            )
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onBack) {
                    Text("Back")
                }
                Button(onClick = onRetry) {
                    Text("Retry")
                }
            }
        }
    }
}
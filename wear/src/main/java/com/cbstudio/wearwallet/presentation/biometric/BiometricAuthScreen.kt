package com.cbstudio.wearwallet.presentation.biometric

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.cbstudio.wearwallet.presentation.components.CircularProgressBar
import com.cbstudio.wearwallet.presentation.theme.WearWalletTheme
import com.cbstudio.wearwallet.core.domain.model.RiskLevel
import kotlinx.coroutines.delay

/**
 * 生物識別認證畫面
 */
@Composable
fun BiometricAuthScreen(
    onAuthSuccess: () -> Unit,
    onAuthFailure: () -> Unit,
    onDismiss: () -> Unit,
    viewModel: BiometricAuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    // 自動開始認證
    LaunchedEffect(Unit) {
        viewModel.startAuthentication()
    }
    
    // 處理認證結果
    LaunchedEffect(uiState.authResult) {
        uiState.authResult?.let { result ->
            delay(1000) // 顯示結果 1 秒
            if (result.isAuthenticated) {
                onAuthSuccess()
            } else {
                onAuthFailure()
            }
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.DarkGray,
                        Color.Black
                    )
                )
            )
    ) {
        ScalingLazyColumn(
            state = rememberScalingLazyListState(),
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            item {
                BiometricAuthContent(
                    uiState = uiState,
                    onRetry = { viewModel.retryAuthentication() },
                    onCancel = onDismiss
                )
            }
        }
    }
}

@Composable
private fun BiometricAuthContent(
    uiState: BiometricAuthUiState,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 標題
        Text(
            text = when (uiState.authState) {
                AuthState.IDLE -> "行為生物識別"
                AuthState.COLLECTING -> "收集數據中"
                AuthState.ANALYZING -> "分析行為模式"
                AuthState.COMPLETED -> if (uiState.authResult?.isAuthenticated == true) 
                    "認證成功" else "認證失敗"
            },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        // 進度指示器或結果圖標
        when (uiState.authState) {
            AuthState.IDLE, AuthState.COLLECTING, AuthState.ANALYZING -> {
                Box(
                    modifier = Modifier.size(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressBar(
                        progress = uiState.progress,
                        modifier = Modifier.fillMaxSize(),
                        strokeWidth = 8.dp,
                        backgroundColor = Color.Gray,
                        progressColor = when (uiState.riskLevel) {
                            RiskLevel.LOW -> Color.Green
                            RiskLevel.MEDIUM -> Color(0xFFFFA726)
                            RiskLevel.HIGH -> Color(0xFFFF6F00)
                            RiskLevel.CRITICAL -> Color.Red
                        }
                    )
                    
                    // 中心圖標
                    Icon(
                        imageVector = when (uiState.authState) {
                            AuthState.COLLECTING -> Icons.Default.MotionPhotosOn
                            AuthState.ANALYZING -> Icons.Default.Analytics
                            else -> Icons.Default.Security
                        },
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            AuthState.COMPLETED -> {
                Icon(
                    imageVector = if (uiState.authResult?.isAuthenticated == true)
                        androidx.compose.material.icons.Icons.Default.CheckCircle
                    else
                        androidx.compose.material.icons.Icons.Default.Cancel,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = if (uiState.authResult?.isAuthenticated == true)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.error
                )
            }
        }
        
        // 狀態信息
        Text(
            text = uiState.statusMessage,
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        
        // 感測器狀態
        if (uiState.sensorStatus.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                uiState.sensorStatus.forEach { (sensor, available) ->
                    if (available) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Circle,
                            contentDescription = sensor,
                            modifier = Modifier.size(8.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                }
            }
        }
        
        // 信心分數（如果有）
        uiState.authResult?.let { result ->
            LinearProgressIndicator(
                progress = { result.confidence },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
            )
            
            Text(
                text = "信心分數: ${(result.confidence * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
        }
        
        // 操作按鈕
        when (uiState.authState) {
            AuthState.IDLE -> {
                Button(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Gray
                    )
                ) {
                    Text("取消")
                }
            }
            AuthState.COMPLETED -> {
                if (uiState.authResult?.isAuthenticated == false) {
                    Button(
                        onClick = onRetry,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("重試")
                    }
                    
                    TextButton(
                        onClick = onCancel,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("使用其他方式")
                    }
                }
            }
            else -> {
                // 認證進行中，不顯示按鈕
            }
        }
        
        // 建議（如果有）
        uiState.authResult?.recommendations?.forEach { recommendation ->
            Card(
                onClick = { /* 無操作 */ },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors()
            ) {
                Text(
                    text = recommendation,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(8.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * 認證狀態
 */
enum class AuthState {
    IDLE,       // 閒置
    COLLECTING, // 收集數據
    ANALYZING,  // 分析中
    COMPLETED   // 完成
}

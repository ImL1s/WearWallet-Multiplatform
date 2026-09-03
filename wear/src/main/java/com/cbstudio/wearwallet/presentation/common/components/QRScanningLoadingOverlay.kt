package com.cbstudio.wearwallet.presentation.common.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * 通用的 QR 掃描載入覆蓋層組件
 * 可被 Send 頁面和 Keystone 連接頁面共用
 * 
 * @param modifier 修飾符
 * @param primaryText 主要提示文字
 * @param secondaryText 次要提示文字（可選）
 * @param backgroundColor 背景顏色透明度
 */
@Composable
fun QRScanningLoadingOverlay(
    modifier: Modifier = Modifier,
    primaryText: String = "正在掃描 QR Code...",
    secondaryText: String? = "請對準 QR Code",
    backgroundColor: Color = Color.Black
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = primaryText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            
            secondaryText?.let { text ->
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * 用於在 LazyColumn 中顯示的掃描載入項目
 * 適用於不需要全螢幕覆蓋的場景
 */
@Composable
fun QRScanningLoadingItem(
    primaryText: String = "正在掃描 QR 碼...",
    secondaryText: String? = "請將 QR 碼對準相機"
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(40.dp),
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = primaryText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        
        secondaryText?.let { text ->
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
        }
    }
}

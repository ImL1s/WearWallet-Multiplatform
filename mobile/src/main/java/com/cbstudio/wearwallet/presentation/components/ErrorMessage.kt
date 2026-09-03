package com.cbstudio.wearwallet.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cbstudio.mobile.R

/**
 * 統一的錯誤訊息組件
 * 
 * 基於 Material 3 設計系統的錯誤狀態顯示組件：
 * - 可自定義錯誤圖標和訊息
 * - 可選擇的重試按鈕
 * - 一致的錯誤狀態視覺風格
 */
@Composable
fun ErrorMessage(
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.Error,
    title: String = stringResource(R.string.error_occurred),
    message: String,
    retryText: String? = stringResource(R.string.retry),
    onRetryClick: (() -> Unit)? = null,
    iconTint: Color = MaterialTheme.colorScheme.error
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 錯誤圖標
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = iconTint
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // 錯誤標題
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // 錯誤訊息
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
        )
        
        // 重試按鈕（可選）
        if (retryText != null && onRetryClick != null) {
            Spacer(modifier = Modifier.height(24.dp))
            
            FilledTonalButton(
                onClick = onRetryClick,
                modifier = Modifier.height(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = retryText,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

/**
 * 緊湊版錯誤訊息組件（用於小空間）
 */
@Composable
fun CompactErrorMessage(
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.Error,
    message: String,
    onRetryClick: (() -> Unit)? = null,
    iconTint: Color = MaterialTheme.colorScheme.error
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = iconTint
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.weight(1f)
        )
        
        if (onRetryClick != null) {
            Spacer(modifier = Modifier.width(8.dp))
            
            IconButton(
                onClick = onRetryClick,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.retry),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
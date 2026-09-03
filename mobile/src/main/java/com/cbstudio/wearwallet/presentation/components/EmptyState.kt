package com.cbstudio.wearwallet.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * 統一的空狀態組件
 * 
 * 基於 Material 3 設計系統的空狀態顯示組件：
 * - 可自定義圖標和文字
 * - 可選擇的操作按鈕
 * - 一致的視覺層次
 */
@Composable
fun EmptyState(
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.ImageNotSupported,
    message: String,
    description: String? = null,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 圖標
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = iconTint
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // 標題
        Text(
            text = message,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        
        // 描述（可選）
        description?.let { desc ->
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = desc,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
            )
        }
        
        // 操作按鈕（可選）
        if (actionText != null && onActionClick != null) {
            Spacer(modifier = Modifier.height(24.dp))
            
            FilledTonalButton(
                onClick = onActionClick,
                modifier = Modifier.height(40.dp)
            ) {
                Text(
                    text = actionText,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

/**
 * 緊湊版空狀態組件（用於小空間）
 */
@Composable
fun CompactEmptyState(
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.ImageNotSupported,
    text: String,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = iconTint
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}
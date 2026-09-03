package com.cbstudio.wearwallet.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Error
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.*

/**
 * 統一的錯誤提示卡片組件
 *
 * 特點：
 * - 使用 Material 3 錯誤色彩系統
 * - 包含錯誤圖標和訊息文字
 * - 完整的無障礙支援
 * - 符合 Wear OS 設計規範
 *
 * @param message 錯誤訊息文字
 * @param modifier Modifier
 */
@Composable
fun ErrorCard(
    message: String,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = { /* 不可點擊 */ },
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "錯誤訊息: $message"
                role = Role.Image
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        enabled = false
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Error,
                contentDescription = null, // 已在 Card 層級提供
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )

            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

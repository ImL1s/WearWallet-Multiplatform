package com.cbstudio.wearwallet.presentation.wallet.screens.main.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.*

/**
 * 空錢包引導卡片 - 首頁空狀態引導
 *
 * 功能：
 * - 顯示歡迎訊息和錢包圖標
 * - 提供「創建錢包」和「導入錢包」兩個操作入口
 * - 遵循 Wear OS Material 3 設計規範
 *
 * @param onNavigateToCreate 創建錢包點擊事件
 * @param onNavigateToImport 導入錢包點擊事件
 * @param modifier Modifier
 */
@Composable
fun EmptyWalletGuideCard(
    onNavigateToCreate: () -> Unit,
    onNavigateToImport: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = { /* 卡片本身不可點擊 */ },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        enabled = false // 卡片本身不響應點擊
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 錢包圖標
            Icon(
                imageVector = Icons.Outlined.AccountBalanceWallet,
                contentDescription = "錢包圖標",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )

            // 標題
            Text(
                text = "歡迎使用 WearWallet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            // 說明文字
            Text(
                text = "請創建或導入錢包開始使用",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 創建錢包按鈕（主按鈕）
            Button(
                onClick = onNavigateToCreate,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .semantics {
                        contentDescription = "創建新錢包"
                        role = Role.Button
                    },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = "創建錢包",
                    style = MaterialTheme.typography.labelLarge
                )
            }

            // 導入錢包按鈕（次按鈕）
            OutlinedButton(
                onClick = onNavigateToImport,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .semantics {
                        contentDescription = "導入現有錢包"
                        role = Role.Button
                    },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = "導入錢包",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

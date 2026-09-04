package com.cbstudio.wearwallet.presentation.wallet.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListAnchorType
import androidx.wear.compose.material3.*
import com.cbstudio.wearwallet.feature.ReleaseFeatureGate
import com.cbstudio.wearwallet.presentation.navigation.WalletRoute

/**
 * Wallet Settings Screen - 完整設定畫面恢復
 * ULTRATHINK Phase 22+ - 恢復原始設定介面
 */
@Composable
fun WalletSettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToCreateMnemonic: () -> Unit = {},
    onNavigateToChainSelector: () -> Unit = {},
    onNavigateToMnemonic: () -> Unit = {},
    onNavigateToWalletManagement: () -> Unit = {},
    onNavigateToTokenManagement: () -> Unit = {},
    onNavigateToNotificationList: () -> Unit = {},
    onNavigateToAddressBook: () -> Unit = {},
    onNavigateToSubscription: () -> Unit = {},
    onNavigateToDatabaseDebug: () -> Unit = {},
    onNavigateToKmpTest: () -> Unit = {},
    onNavigateToAIAssistant: () -> Unit = {},
    onNavigateToPushProtocolSettings: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            anchorType = ScalingLazyListAnchorType.ItemStart,
            contentPadding = PaddingValues(
                top = 24.dp,
                bottom = 24.dp
            )
        ) {
            // 標題
            item {
                Text(
                    text = "設定",
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
            }
            
            // 錢包管理區塊
            item {
                SectionHeader("錢包管理")
            }
            
            item {
                SettingsItem(
                    icon = Icons.Outlined.AccountBalanceWallet,
                    title = "錢包管理",
                    subtitle = "管理多個錢包",
                    onClick = onNavigateToWalletManagement
                )
            }
            
            item {
                SettingsItem(
                    icon = Icons.Outlined.Key,
                    title = "助記詞",
                    subtitle = "查看恢復短語",
                    onClick = onNavigateToMnemonic
                )
            }
            
            item {
                SettingsItem(
                    icon = Icons.Outlined.Add,
                    title = "創建新錢包",
                    subtitle = "生成新的助記詞",
                    onClick = onNavigateToCreateMnemonic
                )
            }
            
            // 網路與代幣
            item {
                SectionHeader("網路與代幣")
            }
            
            item {
                SettingsItem(
                    icon = Icons.Outlined.Language,
                    title = "選擇區塊鏈",
                    subtitle = "切換網路",
                    onClick = onNavigateToChainSelector
                )
            }
            
            item {
                SettingsItem(
                    icon = Icons.Outlined.Token,
                    title = "代幣管理",
                    subtitle = "添加自定義代幣",
                    onClick = onNavigateToTokenManagement
                )
            }
            
            // 社交功能
            item {
                SectionHeader("社交功能")
            }
            
            item {
                SettingsItem(
                    icon = Icons.Outlined.Contacts,
                    title = "通訊錄",
                    subtitle = "管理聯絡人地址",
                    onClick = onNavigateToAddressBook
                )
            }
            
            // 通知設定：NotificationListScreen 尚未實現、Push Protocol 依賴未註冊，
            // 先隱藏入口避免點擊無反應（實現後再恢復）。
            /*
            item {
                SettingsItem(
                    icon = Icons.Outlined.Notifications,
                    title = "通知設定",
                    subtitle = "推送通知偏好",
                    onClick = onNavigateToNotificationList
                )
            }
            */
            
            // TODO: Push Protocol 功能暫時停用
            /*
            item {
                SettingsItem(
                    icon = Icons.Outlined.Message,
                    title = "Push Protocol",
                    subtitle = "Web3 通訊設定",
                    onClick = onNavigateToPushProtocolSettings
                )
            }
            */
            
            // 進階功能 — AI assistant is MAINTENANCE and omitted from release
            if (ReleaseFeatureGate.allowsRoute(
                    WalletRoute.AI_ASSISTANT,
                    ReleaseFeatureGate.isReleaseBuild(),
                )
            ) {
            item {
                SectionHeader("進階功能")
            }
            
            item {
                SettingsItem(
                    icon = Icons.Outlined.Psychology,
                    title = "AI 助手",
                    subtitle = "智能交易建議",
                    onClick = onNavigateToAIAssistant
                )
            }
            }
            
            // TODO: 訂閱管理功能暫時停用
            /*
            item {
                SettingsItem(
                    icon = Icons.Outlined.Diamond,
                    title = "訂閱管理",
                    subtitle = "Pro 功能",
                    onClick = onNavigateToSubscription
                )
            }
            */

            // TODO: 開發者選項暫時停用
            /*
            // 開發者選項
            item {
                SectionHeader("開發者選項")
            }

            item {
                SettingsItem(
                    icon = Icons.Outlined.BugReport,
                    title = "資料庫除錯",
                    subtitle = "檢視本地資料",
                    onClick = onNavigateToDatabaseDebug
                )
            }

            item {
                SettingsItem(
                    icon = Icons.Outlined.Science,
                    title = "KMP 測試",
                    subtitle = "跨平台功能測試",
                    onClick = onNavigateToKmpTest
                )
            }
            */
            
            // 底部間距
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .heightIn(min = 48.dp)
            .testTag("settings_$title")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
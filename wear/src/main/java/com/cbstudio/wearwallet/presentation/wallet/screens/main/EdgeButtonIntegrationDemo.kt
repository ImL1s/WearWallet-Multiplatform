package com.cbstudio.wearwallet.presentation.wallet.screens.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.material3.Text
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import com.cbstudio.wearwallet.presentation.theme.WearWalletTheme

/**
 * ULTRATHINK Phase 8.2 更新：語音助手已整合到主介面交易按鈕中
 * 
 * 新的整合方案：
 * - 語音助手取代了中間的 QR 掃描按鈕
 * - 採用 WearOS RemoteInput API 最佳實踐
 * - 完全避免干擾水平滑動手勢
 * - 符合 WearOS 小螢幕設計原則
 */
@Preview(
    name = "Voice Assistant Integration - Updated",
    group = "Voice Assistant"
)
@WearPreviewDevices
@Composable
fun VoiceAssistantIntegrationPreview() {
    WearWalletTheme {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "語音助手已整合到\n主介面交易按鈕中")
        }
    }
}

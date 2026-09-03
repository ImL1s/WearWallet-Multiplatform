package com.cbstudio.wearwallet.presentation.wallet.screens.debitcard

import androidx.compose.runtime.Composable

/**
 * 借記卡詳情畫面
 * 顯示特定卡片的詳細資訊
 */
@Composable
fun DebitCardDetailsScreen(
    cardId: String,
    onNavigateBack: () -> Unit
) {
    // 此功能已整合到 CryptoDebitCardScreen 的 DETAILS 視圖中
    // 這個獨立畫面保留給未來擴展使用
    onNavigateBack()
}

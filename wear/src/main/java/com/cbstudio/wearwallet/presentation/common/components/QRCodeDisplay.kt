package com.cbstudio.wearwallet.presentation.common.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.cbstudio.wearwallet.presentation.wallet.screens.main.components.QRCodeImage

/**
 * QR Code 顯示組件
 * 用於顯示 Keystone 硬體錢包的 QR Code
 */
@Composable
fun QRCodeDisplay(
    data: String,
    modifier: Modifier = Modifier
) {
    // 直接使用現有的 QRCodeImage 組件
    QRCodeImage(
        data = data,
        modifier = modifier
    )
}
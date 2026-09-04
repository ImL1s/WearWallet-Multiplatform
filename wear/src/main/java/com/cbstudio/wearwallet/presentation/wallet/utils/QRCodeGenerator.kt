package com.cbstudio.wearwallet.presentation.wallet.utils

import androidx.compose.ui.graphics.ImageBitmap

interface QRCodeGenerator {
    suspend fun generateQrCode(content: String): ImageBitmap?
}

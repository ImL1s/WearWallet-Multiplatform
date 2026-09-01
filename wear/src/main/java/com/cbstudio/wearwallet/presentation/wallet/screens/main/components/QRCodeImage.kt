package com.cbstudio.wearwallet.presentation.wallet.screens.main.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.cbstudio.wearwallet.R
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.journeyapps.barcodescanner.BarcodeEncoder

@Composable
fun QRCodeImage(data: String, modifier: Modifier = Modifier) {
    val bitmap = remember(data) {
        generateQRCode(data)
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = stringResource(R.string.qr_code),
            modifier = modifier
        )
    } else {
        // 如果 QR 碼生成失敗，顯示一個錯誤提示
        Text(
            text = stringResource(R.string.qr_code_generation_failed),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            color = androidx.compose.ui.graphics.Color.Red,
        )
    }
}

private fun generateQRCode(data: String): Bitmap? {
    return try {
        // 針對 WearOS 小螢幕優化的設定
        val hints = hashMapOf<EncodeHintType, Any>().apply {
            // 使用最低的錯誤修正等級 (7%) 以產生最小的 QR Code
            put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L)
            // 設定邊距為 2（預設為 4），在小螢幕上可以更充分利用空間
            put(EncodeHintType.MARGIN, 2)
            // 使用 UTF-8 編碼
            put(EncodeHintType.CHARACTER_SET, "UTF-8")
        }
        
        val multiFormatWriter = MultiFormatWriter()
        val bitMatrix: BitMatrix = multiFormatWriter.encode(
            data,
            BarcodeFormat.QR_CODE,
            200, // 降低到 200x200 適合圓形螢幕
            200,
            hints
        )
        val barcodeEncoder = BarcodeEncoder()
        barcodeEncoder.createBitmap(bitMatrix)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

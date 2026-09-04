package com.cbstudio.wearwallet.presentation.wallet.screens.main.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.journeyapps.barcodescanner.BarcodeEncoder
import kotlinx.coroutines.delay

/**
 * 支持 Keystone 3 Pro 的動畫 QR 碼組件
 * 能夠循環顯示多個 QR 碼片段
 */
@Composable
fun AnimatedQRCodeImage(
    qrDataList: List<String>,
    modifier: Modifier = Modifier,
    size: Int = 200,
    animationIntervalMs: Long = 1000L, // 增加到 1 秒，給掃描器更多時間
    slowMode: Boolean = false // 慢速模式，適用於 Keystone 設備
) {
    var currentIndex by remember { mutableIntStateOf(0) }
    
    // 如果只有一個 QR 碼，直接顯示
    if (qrDataList.size == 1) {
        val bitmap = remember(qrDataList.first()) {
            generateQRCode(qrDataList.first(), size)
        }
        
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Keystone QR Code",
                modifier = modifier
                    .size(size.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .padding(4.dp) // 最小內邊距，保持 QR 碼可讀性
            )
        } else {
            ErrorQRCode(size)
        }
        return
    }
    
    // 多個 QR 碼的動畫顯示
    LaunchedEffect(qrDataList, slowMode) {
        if (qrDataList.isNotEmpty()) {
            while (true) {
                val interval = if (slowMode) animationIntervalMs * 2 else animationIntervalMs
                delay(interval)
                currentIndex = (currentIndex + 1) % qrDataList.size
            }
        }
    }
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        if (qrDataList.isNotEmpty()) {
            val currentData = qrDataList[currentIndex]
            val bitmap = remember(currentData) {
                generateQRCode(currentData, size)
            }
            
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Keystone QR Code",
                    modifier = Modifier
                        .size(size.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White)
                        .padding(4.dp) // 最小內邊距，保持 QR 碼可讀性
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 顯示當前片段信息
                Text(
                    text = "第 ${currentIndex + 1} / ${qrDataList.size} 段",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                // 進度指示器
                Text(
                    text = "●".repeat(currentIndex + 1) + "○".repeat(qrDataList.size - currentIndex - 1),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary
                )
                
                // 掃描提示
                if (qrDataList.size > 1) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "請保持對準直到掃描完成",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            } else {
                ErrorQRCode(size)
            }
        } else {
            ErrorQRCode(size)
        }
    }
}

/**
 * Keystone QR 碼顯示組件
 * 包含標題和說明
 */
@Composable
fun KeystoneQRCodeDisplay(
    qrDataList: List<String>,
    title: String = "掃描以發送至 Keystone",
    instruction: String = "請使用 Keystone 3 Pro 掃描此 QR 碼",
    modifier: Modifier = Modifier,
    slowMode: Boolean = true // Keystone 設備預設使用慢速模式
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = instruction,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        AnimatedQRCodeImage(
            qrDataList = qrDataList,
            size = 180,
            slowMode = slowMode,
            animationIntervalMs = if (slowMode) 1500L else 1000L // 慢速模式使用 1.5 秒間隔
        )
    }
}

@Composable
private fun ErrorQRCode(size: Int = 200) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.LightGray),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "無法生成 QR 碼",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            color = Color.Red
        )
    }
}

private fun generateQRCode(data: String, size: Int): Bitmap? {
    return try {
        val multiFormatWriter = MultiFormatWriter()
        // 使用更高的像素密度生成更清晰的 QR 碼
        val pixelSize = maxOf(size * 2, 512) // 至少 512 像素，或者是顯示大小的 2 倍
        val bitMatrix: BitMatrix = multiFormatWriter.encode(
            data,
            BarcodeFormat.QR_CODE,
            pixelSize,
            pixelSize
        )
        val barcodeEncoder = BarcodeEncoder()
        barcodeEncoder.createBitmap(bitMatrix)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
} 

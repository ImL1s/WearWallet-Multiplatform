package com.cbstudio.wearwallet.presentation.complication

import androidx.wear.watchface.complications.data.*
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService

/**
 * Complication 擴展函數
 * 提供統一的錯誤處理和共用功能
 */

/**
 * 創建錯誤 Complication 數據
 */
fun SuspendingComplicationDataSourceService.createErrorComplication(
    type: ComplicationType, 
    error: String = "錯誤"
): ComplicationData {
    return when (type) {
        ComplicationType.SHORT_TEXT -> {
            ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder(error).build(),
                contentDescription = PlainComplicationText.Builder("Error: $error").build()
            ).build()
        }
        ComplicationType.LONG_TEXT -> {
            LongTextComplicationData.Builder(
                text = PlainComplicationText.Builder(error).build(),
                contentDescription = PlainComplicationText.Builder("Error: $error").build()
            ).build()
        }
        ComplicationType.RANGED_VALUE -> {
            RangedValueComplicationData.Builder(
                value = 0f,
                min = 0f,
                max = 100f,
                contentDescription = PlainComplicationText.Builder("Error: $error").build()
            ).setText(PlainComplicationText.Builder(error).build())
            .build()
        }
        ComplicationType.SMALL_IMAGE, ComplicationType.PHOTO_IMAGE -> {
            // 暫時返回文字錯誤，避免 Context 依賴問題
            ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder(error).build(),
                contentDescription = PlainComplicationText.Builder("Error: $error").build()
            ).build()
        }
        else -> {
            ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder(error).build(),
                contentDescription = PlainComplicationText.Builder("Error: $error").build()
            ).build()
        }
    }
}

/**
 * 創建載入中 Complication 數據
 */
fun SuspendingComplicationDataSourceService.createLoadingComplication(
    type: ComplicationType
): ComplicationData {
    return when (type) {
        ComplicationType.SHORT_TEXT -> {
            ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder("載入中...").build(),
                contentDescription = PlainComplicationText.Builder("Loading data").build()
            ).build()
        }
        ComplicationType.LONG_TEXT -> {
            LongTextComplicationData.Builder(
                text = PlainComplicationText.Builder("載入中...").build(),
                contentDescription = PlainComplicationText.Builder("Loading data").build()
            ).build()
        }
        ComplicationType.RANGED_VALUE -> {
            RangedValueComplicationData.Builder(
                value = 50f,
                min = 0f,
                max = 100f,
                contentDescription = PlainComplicationText.Builder("Loading data").build()
            ).setText(PlainComplicationText.Builder("載入中").build())
            .build()
        }
        else -> {
            ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder("載入中").build(),
                contentDescription = PlainComplicationText.Builder("Loading data").build()
            ).build()
        }
    }
}

/**
 * 創建錯誤圖標 (預設) - 需要 Context 參數
 */
private fun createErrorIcon(context: android.content.Context): androidx.wear.watchface.complications.data.SmallImage {
    // 創建一個簡單的錯誤圖標
    val icon = android.graphics.drawable.Icon.createWithResource(
        context,
        android.R.drawable.ic_dialog_alert
    )
    
    return androidx.wear.watchface.complications.data.SmallImage.Builder(
        icon,
        androidx.wear.watchface.complications.data.SmallImageType.ICON
    ).build()
}
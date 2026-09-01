package com.cbstudio.wearwallet.presentation.complication.service

import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService

/**
 * 簡化 Complication 數據服務 - MAINTENANCE MODE
 * ULTRATHINK Phase 18 - 最終語法修復
 */
class SimpleComplicationDataService : SuspendingComplicationDataSourceService() {
    
    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        return NoDataComplicationData()
    }
    
    override fun getPreviewData(type: androidx.wear.watchface.complications.data.ComplicationType): ComplicationData {
        return NoDataComplicationData()
    }
}
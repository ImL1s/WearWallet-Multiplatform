package com.cbstudio.wearwallet.presentation.complication

import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService

/**
 * Token Change Complication 服務 - MAINTENANCE MODE
 * ULTRATHINK Phase 18 - 維護模式修復
 */
class TokenChangeComplicationService : SuspendingComplicationDataSourceService() {
    
    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        // MAINTENANCE MODE: Return no data
        return NoDataComplicationData()
    }
    
    override fun getPreviewData(type: androidx.wear.watchface.complications.data.ComplicationType): ComplicationData {
        return NoDataComplicationData()
    }
}
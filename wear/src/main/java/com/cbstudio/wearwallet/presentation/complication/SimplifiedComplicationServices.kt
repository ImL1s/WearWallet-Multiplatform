package com.cbstudio.wearwallet.presentation.complication

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.*
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.cbstudio.wearwallet.R
import com.cbstudio.wearwallet.presentation.MainActivity
import timber.log.Timber

/**
 * 簡化的錢包餘額 Complication 服務
 */
class SimplifiedWalletBalanceComplicationService : SuspendingComplicationDataSourceService() {
    
    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        return try {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            
            val pendingIntent = PendingIntent.getActivity(
                this,
                request.complicationInstanceId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            when (request.complicationType) {
                ComplicationType.SHORT_TEXT -> {
                    ShortTextComplicationData.Builder(
                        text = PlainComplicationText.Builder("$0.00").build(),
                        contentDescription = PlainComplicationText.Builder("錢包餘額").build()
                    )
                        .setTapAction(pendingIntent)
                        .setMonochromaticImage(
                            MonochromaticImage.Builder(
                                Icon.createWithResource(this, R.drawable.ic_complication_wallet_balance)
                            ).build()
                        )
                        .build()
                }
                else -> NoDataComplicationData()
            }
        } catch (e: Exception) {
            Timber.e(e, "建立 Complication 失敗")
            NoDataComplicationData()
        }
    }
    
    override fun getPreviewData(type: ComplicationType): ComplicationData {
        return NoDataComplicationData()
    }
}

/**
 * 簡化的代幣價格 Complication 服務
 */
class SimplifiedTokenPriceComplicationService : SuspendingComplicationDataSourceService() {
    
    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        return try {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            
            val pendingIntent = PendingIntent.getActivity(
                this,
                request.complicationInstanceId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            when (request.complicationType) {
                ComplicationType.SHORT_TEXT -> {
                    ShortTextComplicationData.Builder(
                        text = PlainComplicationText.Builder("ETH").build(),
                        contentDescription = PlainComplicationText.Builder("ETH 價格").build()
                    )
                        .setTapAction(pendingIntent)
                        .setMonochromaticImage(
                            MonochromaticImage.Builder(
                                Icon.createWithResource(this, R.drawable.ic_complication_token_price)
                            ).build()
                        )
                        .build()
                }
                else -> NoDataComplicationData()
            }
        } catch (e: Exception) {
            Timber.e(e, "建立 Complication 失敗")
            NoDataComplicationData()
        }
    }
    
    override fun getPreviewData(type: ComplicationType): ComplicationData {
        return NoDataComplicationData()
    }
}

/**
 * 簡化的 Gas Fee Complication 服務
 */
class SimplifiedGasFeeComplicationService : SuspendingComplicationDataSourceService() {
    
    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        return try {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            
            val pendingIntent = PendingIntent.getActivity(
                this,
                request.complicationInstanceId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            when (request.complicationType) {
                ComplicationType.SHORT_TEXT -> {
                    ShortTextComplicationData.Builder(
                        text = PlainComplicationText.Builder("--").build(),
                        contentDescription = PlainComplicationText.Builder("Gas 價格").build()
                    )
                        .setTitle(
                            PlainComplicationText.Builder("Gas").build()
                        )
                        .setTapAction(pendingIntent)
                        .setMonochromaticImage(
                            MonochromaticImage.Builder(
                                Icon.createWithResource(this, R.drawable.ic_complication_gas_fee)
                            ).build()
                        )
                        .build()
                }
                else -> NoDataComplicationData()
            }
        } catch (e: Exception) {
            Timber.e(e, "建立 Complication 失敗")
            NoDataComplicationData()
        }
    }
    
    override fun getPreviewData(type: ComplicationType): ComplicationData {
        return NoDataComplicationData()
    }
}
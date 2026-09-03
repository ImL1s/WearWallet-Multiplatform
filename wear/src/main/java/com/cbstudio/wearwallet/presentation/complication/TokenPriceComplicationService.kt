package com.cbstudio.wearwallet.presentation.complication

import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.*
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.cbstudio.wearwallet.R
import com.cbstudio.wearwallet.core.domain.repository.TokenRepository
import com.cbstudio.wearwallet.presentation.MainActivity
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.text.NumberFormat
import java.util.Locale

/**
 * 代幣價格 Complication 服務
 * 顯示特定代幣的價格和漲幅
 */
class TokenPriceComplicationService : SuspendingComplicationDataSourceService(), KoinComponent {
    
    private val tokenRepository: TokenRepository by inject()
    private val prefs: SharedPreferences by lazy {
        getSharedPreferences("complication_prefs", MODE_PRIVATE)
    }
    private val numberFormat = NumberFormat.getCurrencyInstance(Locale.US)
    
    companion object {
        private const val PREF_TOKEN_SYMBOL = "token_symbol"
        private const val DEFAULT_TOKEN = "ETH"
        private var lastPriceCache: Pair<String, Double>? = null
    }
    
    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        return try {
            // 獲取設定的代幣符號
            val tokenSymbol = prefs.getString(PREF_TOKEN_SYMBOL, DEFAULT_TOKEN) ?: DEFAULT_TOKEN
            
            // 獲取代幣價格
            val price = fetchTokenPrice(tokenSymbol)
            val priceText = if (price > 0) {
                numberFormat.format(price)
            } else {
                "--"
            }
            
            // 計算漲幅 (簡化版，與快取值比較)
            val changePercent = calculatePriceChange(tokenSymbol, price)
            val changeText = if (changePercent != null) {
                String.format("%+.2f%%", changePercent)
            } else {
                ""
            }
            
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("show_token", tokenSymbol)
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
                        text = PlainComplicationText.Builder(priceText).build(),
                        contentDescription = PlainComplicationText.Builder("$tokenSymbol 價格 $priceText").build()
                    )
                        .setTitle(
                            PlainComplicationText.Builder(tokenSymbol).build()
                        )
                        .setTapAction(pendingIntent)
                        .build()
                }
                
                ComplicationType.LONG_TEXT -> {
                    val fullText = if (changeText.isNotEmpty()) {
                        "$priceText $changeText"
                    } else {
                        priceText
                    }
                    
                    LongTextComplicationData.Builder(
                        text = PlainComplicationText.Builder(fullText).build(),
                        contentDescription = PlainComplicationText.Builder("$tokenSymbol 價格 $fullText").build()
                    )
                        .setTitle(
                            PlainComplicationText.Builder(tokenSymbol).build()
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
            Timber.e(e, "建立 Token Price Complication 失敗")
            NoDataComplicationData()
        }
    }
    
    /**
     * 獲取代幣價格
     */
    private suspend fun fetchTokenPrice(symbol: String): Double {
        return try {
            tokenRepository.getTokenPrice(symbol) ?: 0.0
        } catch (e: Exception) {
            Timber.e(e, "獲取 $symbol 價格失敗")
            0.0
        }
    }
    
    /**
     * 計算價格變化百分比
     */
    private fun calculatePriceChange(symbol: String, currentPrice: Double): Double? {
        // 簡化實現：與快取值比較
        val lastPrice = lastPriceCache
        return if (lastPrice != null && lastPrice.first == symbol && lastPrice.second > 0) {
            ((currentPrice - lastPrice.second) / lastPrice.second) * 100
        } else {
            // 更新快取
            lastPriceCache = symbol to currentPrice
            null
        }
    }
    
    /**
     * 設定要顯示的代幣
     */
    fun setTokenSymbol(symbol: String) {
        prefs.edit().putString(PREF_TOKEN_SYMBOL, symbol).apply()
    }
    
    override fun getPreviewData(type: ComplicationType): ComplicationData {
        return when (type) {
            ComplicationType.SHORT_TEXT -> {
                ShortTextComplicationData.Builder(
                    text = PlainComplicationText.Builder("$2,456.78").build(),
                    contentDescription = PlainComplicationText.Builder("ETH 價格").build()
                )
                    .setTitle(
                        PlainComplicationText.Builder("ETH").build()
                    )
                    .build()
            }
            ComplicationType.LONG_TEXT -> {
                LongTextComplicationData.Builder(
                    text = PlainComplicationText.Builder("$2,456.78 +3.45%").build(),
                    contentDescription = PlainComplicationText.Builder("ETH 價格").build()
                )
                    .setTitle(
                        PlainComplicationText.Builder("ETH").build()
                    )
                    .setMonochromaticImage(
                        MonochromaticImage.Builder(
                            Icon.createWithResource(this, R.drawable.ic_complication_token_price)
                        ).build()
                    )
                    .build()
            }
            else -> NoDataComplicationData()
        }
    }
}
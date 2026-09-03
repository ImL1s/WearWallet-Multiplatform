package com.cbstudio.wearwallet.presentation.complication

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.*
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.cbstudio.wearwallet.R
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.repository.TokenRepository
import com.cbstudio.wearwallet.core.domain.repository.WalletRepository
import com.cbstudio.wearwallet.presentation.MainActivity
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.text.NumberFormat
import java.util.Locale

/**
 * 錢包餘額 Complication 服務
 * 顯示當前錢包的總餘額
 */
class WalletBalanceComplicationService : SuspendingComplicationDataSourceService(), KoinComponent {
    
    private val walletRepository: WalletRepository by inject()
    private val tokenRepository: TokenRepository by inject()
    private val numberFormat = NumberFormat.getCurrencyInstance(Locale.US)
    
    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        return try {
            // 計算總餘額
            val totalBalance = calculateTotalBalance()
            val balanceText = if (totalBalance >= 0) {
                numberFormat.format(totalBalance)
            } else {
                "--"
            }
            
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("show_wallet", true)
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
                        text = PlainComplicationText.Builder(balanceText).build(),
                        contentDescription = PlainComplicationText.Builder("錢包餘額 $balanceText").build()
                    )
                        .setTapAction(pendingIntent)
                        .setMonochromaticImage(
                            MonochromaticImage.Builder(
                                Icon.createWithResource(this, R.drawable.ic_complication_wallet_balance)
                            ).build()
                        )
                        .build()
                }
                
                ComplicationType.LONG_TEXT -> {
                    LongTextComplicationData.Builder(
                        text = PlainComplicationText.Builder(balanceText).build(),
                        contentDescription = PlainComplicationText.Builder("錢包餘額 $balanceText").build()
                    )
                        .setTitle(
                            PlainComplicationText.Builder("錢包").build()
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
            Timber.e(e, "建立 Complication 數據失敗")
            NoDataComplicationData()
        }
    }
    
    /**
     * 計算錢包總餘額
     */
    private suspend fun calculateTotalBalance(): Double {
        return try {
            // 獲取當前活動錢包
            val activeWalletResult = walletRepository.getActiveWallet()
            val activeWallet = if (activeWalletResult is com.cbstudio.wearwallet.core.common.Result.Success) {
                activeWalletResult.data
            } else {
                null
            }
            val walletAddress = activeWallet?.address ?: return -1.0
            
            // 支援的鏈
            val chains = listOf(
                ChainType.ETHEREUM,
                ChainType.BSC,
                ChainType.POLYGON,
                ChainType.CRONOS
            )
            
            // 並行獲取各鏈的餘額
            val balances = coroutineScope {
                chains.map { chain ->
                    async {
                        try {
                            // 獲取原生代幣餘額
                            val nativeBalance = tokenRepository.getNativeBalance(walletAddress, chain)
                            val nativeValue = nativeBalance.toDoubleOrNull() ?: 0.0
                            
                            // 獲取原生代幣價格
                            val nativeSymbol = when (chain) {
                                ChainType.ETHEREUM -> "ETH"
                                ChainType.BSC -> "BNB"
                                ChainType.POLYGON -> "MATIC"
                                ChainType.CRONOS -> "CRO"
                                else -> null
                            }
                            
                            val nativePrice = nativeSymbol?.let {
                                tokenRepository.getTokenPrice(it) ?: 0.0
                            } ?: 0.0
                            
                            val nativeUsdValue = nativeValue * nativePrice
                            
                            // 獲取代幣餘額 (暫時只計算原生代幣)
                            // TODO: 加入 ERC20 代幣餘額
                            
                            nativeUsdValue
                        } catch (e: Exception) {
                            Timber.e(e, "獲取 $chain 餘額失敗")
                            0.0
                        }
                    }
                }.awaitAll()
            }
            
            balances.sum()
        } catch (e: Exception) {
            Timber.e(e, "計算總餘額失敗")
            -1.0
        }
    }
    
    override fun getPreviewData(type: ComplicationType): ComplicationData {
        return when (type) {
            ComplicationType.SHORT_TEXT -> {
                ShortTextComplicationData.Builder(
                    text = PlainComplicationText.Builder("$1,234.56").build(),
                    contentDescription = PlainComplicationText.Builder("錢包餘額").build()
                )
                    .setMonochromaticImage(
                        MonochromaticImage.Builder(
                            Icon.createWithResource(this, R.drawable.ic_complication_wallet_balance)
                        ).build()
                    )
                    .build()
            }
            else -> NoDataComplicationData()
        }
    }
}
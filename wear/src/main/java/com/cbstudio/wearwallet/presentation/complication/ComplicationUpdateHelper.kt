package com.cbstudio.wearwallet.presentation.complication

import android.content.ComponentName
import android.content.Context
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.cbstudio.wearwallet.shared.utils.Logger

/**
 * 協助管理 Complication 更新的工具類
 * 
 * WearOS Complications 有以下更新限制：
 * - UPDATE_PERIOD_SECONDS 最小值為 300 秒（5 分鐘）
 * - 系統會根據電池優化等因素調整實際更新頻率
 * - 在 Ambient Mode 或未佩戴時更新頻率更低
 * 
 * 此類提供手動更新 Complications 的方法
 * 
 * 移除 Hilt 依賴，改用 Koin 管理
 */
class ComplicationUpdateHelper(
    private val context: Context
) {
    
    /**
     * 更新所有 WearWallet Complications
     * 
     * 注意：為了保護電池壽命，不應過於頻繁調用此方法
     * 建議平均不超過每 5 分鐘一次
     */
    fun updateAllComplications() {
        Logger.d("ComplicationUpdateHelper", "請求更新所有 Complications")
        
        // 更新代幣價格 Complication
        updateComplication(TokenPriceComplicationService::class.java)
        
        // 更新代幣變化率 Complication
        updateComplication(TokenChangeComplicationService::class.java)
        
        // 更新錢包餘額 Complication
        updateComplication(WalletBalanceComplicationService::class.java)
        
        // 更新 Gas 費用 Complication
        updateComplication(GasFeeComplicationService::class.java)
        
        // 更新主要 Complication 服務
        updateComplication(WearWalletComplicationService::class.java)
    }
    
    /**
     * 更新特定的 Complication 服務
     */
    fun updateComplication(complicationClass: Class<*>) {
        try {
            val componentName = ComponentName(context, complicationClass)
            val updateRequester = ComplicationDataSourceUpdateRequester.create(
                context,
                componentName
            )
            
            updateRequester.requestUpdateAll()
            Logger.d("ComplicationUpdateHelper", "已請求更新: ${complicationClass.simpleName}")
            
        } catch (e: Exception) {
            Logger.e("ComplicationUpdateHelper", "更新 Complication 失敗: ${complicationClass.simpleName}", e)
        }
    }
    
    /**
     * 更新 Gas 費用 Complication
     * 
     * Gas 價格變化較快，可能需要更頻繁的更新
     */
    fun updateGasComplication() {
        Logger.d("ComplicationUpdateHelper", "請求更新 Gas Complication")
        updateComplication(GasFeeComplicationService::class.java)
    }
    
    /**
     * 更新價格相關的 Complications（代幣價格和變化率）
     */
    fun updatePriceComplications() {
        Logger.d("ComplicationUpdateHelper", "請求更新價格相關 Complications")
        updateComplication(TokenPriceComplicationService::class.java)
        updateComplication(TokenChangeComplicationService::class.java)
    }
}

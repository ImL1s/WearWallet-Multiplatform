package com.cbstudio.wearwallet.core.domain.repository

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.pricealert.*
import kotlinx.coroutines.flow.Flow

/**
 * 價格提醒資料庫操作介面
 * 提供價格提醒的增刪改查功能
 */
interface PriceAlertRepository {
    
    // ========== 基本 CRUD 操作 ==========
    
    /**
     * 創建價格提醒
     */
    suspend fun createAlert(alert: PriceAlert): Result<PriceAlert>
    
    /**
     * 根據 ID 獲取提醒
     */
    suspend fun getAlert(id: String): Result<PriceAlert?>
    
    /**
     * 獲取所有提醒
     */
    suspend fun getAllAlerts(): Result<List<PriceAlert>>
    
    /**
     * 觀察所有提醒變化
     */
    fun observeAllAlerts(): Flow<List<PriceAlert>>
    
    /**
     * 更新提醒
     */
    suspend fun updateAlert(alert: PriceAlert): Result<PriceAlert>
    
    /**
     * 根據 ID 刪除提醒
     */
    suspend fun deleteAlert(id: String): Result<Unit>
    
    /**
     * 批量刪除提醒
     */
    suspend fun deleteAlerts(ids: List<String>): Result<Unit>
    
    // ========== 查詢操作 ==========
    
    /**
     * 獲取啟用的提醒
     */
    suspend fun getEnabledAlerts(): Result<List<PriceAlert>>
    
    /**
     * 獲取未觸發的提醒
     */
    suspend fun getNotTriggeredAlerts(): Result<List<PriceAlert>>
    
    /**
     * 根據資產符號獲取提醒
     */
    suspend fun getAlertsByAssetSymbol(assetSymbol: String): Result<List<PriceAlert>>
    
    /**
     * 根據鏈類型獲取提醒
     */
    suspend fun getAlertsByChainType(chainType: ChainType): Result<List<PriceAlert>>
    
    /**
     * 根據提醒類型獲取提醒
     */
    suspend fun getAlertsByAlertType(alertType: AlertType): Result<List<PriceAlert>>
    
    /**
     * 搜尋提醒
     */
    suspend fun searchAlerts(query: String): Result<List<PriceAlert>>
    
    /**
     * 根據篩選條件獲取提醒
     */
    suspend fun getAlertsWithFilter(filter: PriceAlertFilter): Result<List<PriceAlert>>
    
    /**
     * 獲取需要監控的提醒（用於價格檢查服務）
     */
    suspend fun getAlertsForMonitoring(maxAgeMs: Long): Result<List<PriceAlert>>
    
    /**
     * 獲取即將觸發的提醒
     */
    suspend fun getNearTriggerAlerts(): Result<List<PriceAlert>>
    
    // ========== 狀態更新 ==========
    
    /**
     * 更新當前價格
     */
    suspend fun updateCurrentPrice(id: String, price: Double): Result<Unit>
    
    /**
     * 批量更新當前價格
     */
    suspend fun updateCurrentPrices(priceUpdates: Map<String, Double>): Result<Unit>
    
    /**
     * 更新啟用狀態
     */
    suspend fun updateEnabledStatus(id: String, isEnabled: Boolean): Result<Unit>
    
    /**
     * 觸發提醒
     */
    suspend fun triggerAlert(id: String): Result<Unit>
    
    /**
     * 標記通知已發送
     */
    suspend fun markNotificationSent(id: String): Result<Unit>
    
    /**
     * 重置觸發狀態（用於重複提醒）
     */
    suspend fun resetTriggerStatus(id: String): Result<Unit>
    
    /**
     * 批量更新檢查時間
     */
    suspend fun updateLastCheckedTime(assetSymbol: String, chainType: ChainType, timestamp: Long): Result<Unit>
    
    // ========== 驗證與檢查 ==========
    
    /**
     * 檢查是否存在相同配置的提醒
     */
    suspend fun isAlertExists(
        walletId: String,
        assetSymbol: String, 
        chainType: ChainType, 
        alertType: AlertType, 
        targetPrice: Double
    ): Result<Boolean>
    
    /**
     * 驗證提醒配置是否有效
     */
    suspend fun validateAlert(alert: PriceAlert): Result<Boolean>
    
    /**
     * 檢查提醒是否應該觸發
     */
    suspend fun checkShouldTrigger(id: String, currentPrice: Double): Result<Boolean>
    
    // ========== 批量操作 ==========
    
    /**
     * 批量創建提醒
     */
    suspend fun createAlerts(alerts: List<PriceAlert>): Result<Int>
    
    /**
     * 根據資產符號刪除提醒
     */
    suspend fun deleteAlertsByAssetSymbol(assetSymbol: String): Result<Unit>
    
    /**
     * 刪除已觸發的提醒（不重複的）
     */
    suspend fun deleteTriggeredAlerts(): Result<Unit>
    
    /**
     * 刪除禁用的提醒
     */
    suspend fun deleteDisabledAlerts(): Result<Unit>
    
    /**
     * 清空所有提醒
     */
    suspend fun clearAllAlerts(): Result<Unit>
    
    // ========== 統計與分析 ==========
    
    /**
     * 獲取提醒統計資訊
     */
    suspend fun getStatistics(): Result<PriceAlertStatistics>
    
    /**
     * 獲取資產統計
     */
    suspend fun getAssetStatistics(): Result<List<AssetAlertSummary>>
    
    /**
     * 獲取鏈統計
     */
    suspend fun getChainStatistics(): Result<Map<ChainType, Int>>
    
    /**
     * 獲取觸發歷史
     */
    suspend fun getTriggerHistory(limit: Int = 50): Result<List<TriggerHistory>>
    
    // ========== 監控與維護 ==========
    
    /**
     * 獲取需要檢查價格的資產列表
     */
    suspend fun getAssetsToMonitor(): Result<List<String>>
    
    /**
     * 處理價格更新（檢查並觸發符合條件的提醒）
     */
    suspend fun processPriceUpdate(assetSymbol: String, price: Double, chainType: ChainType): Result<List<PriceAlert>>
    
    /**
     * 清理過期的觸發記錄
     */
    suspend fun cleanupExpiredTriggers(maxAgeMs: Long): Result<Int>
    
    /**
     * 重置長時間未檢查的提醒狀態
     */
    suspend fun resetStaleAlerts(maxAgeMs: Long): Result<Int>
}
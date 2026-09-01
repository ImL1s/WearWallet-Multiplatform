package com.cbstudio.wearwallet.presentation.complication

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.cbstudio.wearwallet.shared.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Complication 同步管理器
 * 
 * 負責管理 Complication 數據的定期更新和即時更新：
 * - 定期背景同步（每 15 分鐘）
 * - 即時更新請求
 * - 電池和網路狀況優化
 * - 同步策略管理
 */
class ComplicationSyncManager constructor(
    private val workManager: WorkManager
) {
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    companion object {
        private const val PERIODIC_SYNC_WORK_NAME = "complication_periodic_sync"
        private const val IMMEDIATE_SYNC_WORK_NAME = "complication_immediate_sync"
        
        // 更新間隔（分鐘）
        private const val DEFAULT_UPDATE_INTERVAL_MINUTES = 15L
        private const val MIN_UPDATE_INTERVAL_MINUTES = 5L
        private const val MAX_UPDATE_INTERVAL_MINUTES = 60L
        
        // 重試設定
        private const val RETRY_BACKOFF_DELAY_MINUTES = 5L
        private const val MAX_RETRY_ATTEMPTS = 3
    }
    
    /**
     * 啟動定期 Complication 更新
     * 
     * @param intervalMinutes 更新間隔（分鐘），預設 15 分鐘
     */
    fun startPeriodicUpdates(intervalMinutes: Long = DEFAULT_UPDATE_INTERVAL_MINUTES) {
        val validInterval = intervalMinutes.coerceIn(
            MIN_UPDATE_INTERVAL_MINUTES,
            MAX_UPDATE_INTERVAL_MINUTES
        )
        
        Logger.d("ComplicationSyncManager", "Starting periodic updates with interval: ${validInterval}m")
        
        val constraints = buildOptimalConstraints()
        
        val periodicWork = PeriodicWorkRequestBuilder<ComplicationUpdateWorker>(
            validInterval, TimeUnit.MINUTES
        )
        .setConstraints(constraints)
        .setBackoffCriteria(
            BackoffPolicy.EXPONENTIAL,
            RETRY_BACKOFF_DELAY_MINUTES, TimeUnit.MINUTES
        )
        .addTag("complication_sync")
        .addTag("periodic_sync")
        .build()
        
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicWork
        )
        
        Logger.d("ComplicationSyncManager", "Periodic sync work enqueued successfully")
    }
    
    /**
     * 停止定期更新
     */
    fun stopPeriodicUpdates() {
        Logger.d("ComplicationSyncManager", "Stopping periodic updates")
        workManager.cancelUniqueWork(PERIODIC_SYNC_WORK_NAME)
    }
    
    /**
     * 立即更新 Complication 數據
     * 
     * @param forceUpdate 是否強制更新（忽略電池和網路限制）
     */
    fun updateImmediately(forceUpdate: Boolean = false) {
        Logger.d("ComplicationSyncManager", "Requesting immediate update (force: $forceUpdate)")
        
        val constraints = if (forceUpdate) {
            // 強制更新時放寬限制
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        } else {
            buildOptimalConstraints()
        }
        
        val immediateWork = OneTimeWorkRequestBuilder<ComplicationUpdateWorker>()
            .setConstraints(constraints)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag("complication_sync")
            .addTag("immediate_sync")
            .setInputData(workDataOf("force_update" to forceUpdate))
            .build()
        
        workManager.enqueue(immediateWork)
        
        Logger.d("ComplicationSyncManager", "Immediate sync work enqueued")
    }
    
    /**
     * 更新特定 Complication 類型的數據
     * 
     * @param complicationType 要更新的 Complication 類型
     */
    fun updateSpecificComplication(complicationType: ComplicationType) {
        Logger.d("ComplicationSyncManager", "Updating specific complication: $complicationType")
        
        val specificWork = OneTimeWorkRequestBuilder<ComplicationUpdateWorker>()
            .setConstraints(buildOptimalConstraints())
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag("complication_sync")
            .addTag("specific_sync")
            .setInputData(workDataOf("complication_type" to complicationType.name))
            .build()
        
        workManager.enqueue(specificWork)
    }
    
    /**
     * 檢查同步狀態
     * 
     * @return 是否有進行中的同步工作
     */
    fun isSyncing(): Boolean {
        return try {
            val workInfos = workManager.getWorkInfosByTag("complication_sync").get()
            workInfos.any { !it.state.isFinished }
        } catch (e: Exception) {
            Logger.e("ComplicationSyncManager", "Error checking sync status", e)
            false
        }
    }
    
    /**
     * 取消所有 Complication 同步工作
     */
    fun cancelAllSync() {
        Logger.d("ComplicationSyncManager", "Cancelling all sync work")
        workManager.cancelAllWorkByTag("complication_sync")
    }
    
    /**
     * 重新啟動同步服務（重置配置）
     * 
     * @param intervalMinutes 新的更新間隔
     */
    suspend fun restartSync(intervalMinutes: Long = DEFAULT_UPDATE_INTERVAL_MINUTES) {
        Logger.d("ComplicationSyncManager", "Restarting sync with new interval: ${intervalMinutes}m")
        
        stopPeriodicUpdates()
        // 稍微延遲確保舊工作完全取消
        kotlinx.coroutines.delay(100)
        startPeriodicUpdates(intervalMinutes)
    }
    
    /**
     * 建立最佳化的工作約束條件
     * 
     * 根據 Wear OS 特性優化：
     * - 需要網路連接
     * - 電池電量不低
     * - 充電時執行（可選）
     * - 儲存空間充足
     */
    private fun buildOptimalConstraints(): Constraints {
        return Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .setRequiresCharging(false) // Wear OS 設備充電頻率較高，不強制要求充電
            .setRequiresStorageNotLow(true)
            .build()
    }
    
    /**
     * 建立省電模式約束條件
     * 
     * 電池電量低時使用的嚴格約束條件
     */
    private fun buildBatterySavingConstraints(): Constraints {
        return Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED) // 只在 WiFi 下執行
            .setRequiresBatteryNotLow(true)
            .setRequiresCharging(true) // 要求充電
            .setRequiresStorageNotLow(true)
            .build()
    }
    
    /**
     * 根據設備狀態調整同步策略
     * 
     * @param batteryLevel 電池電量 (0-100)
     * @param isCharging 是否正在充電
     */
    fun adjustSyncStrategy(batteryLevel: Int, isCharging: Boolean) {
        Logger.d("ComplicationSyncManager", "Adjusting sync strategy - Battery: $batteryLevel%, Charging: $isCharging")
        
        when {
            batteryLevel < 20 && !isCharging -> {
                // 低電量且未充電：降低更新頻率
                Logger.d("ComplicationSyncManager", "Low battery mode: reducing update frequency")
                scope.launch { restartSync(MAX_UPDATE_INTERVAL_MINUTES) }
            }
            
            batteryLevel < 50 && !isCharging -> {
                // 中等電量且未充電：適中更新頻率
                Logger.d("ComplicationSyncManager", "Medium battery mode: standard update frequency")
                scope.launch { restartSync(DEFAULT_UPDATE_INTERVAL_MINUTES * 2) }
            }
            
            isCharging || batteryLevel > 80 -> {
                // 充電中或高電量：正常更新頻率
                Logger.d("ComplicationSyncManager", "Optimal conditions: normal update frequency")
                scope.launch { restartSync(DEFAULT_UPDATE_INTERVAL_MINUTES) }
            }
            
            else -> {
                // 其他情況：預設頻率
                Logger.d("ComplicationSyncManager", "Default conditions: standard update frequency")
                scope.launch { restartSync(DEFAULT_UPDATE_INTERVAL_MINUTES) }
            }
        }
    }
    
    /**
     * Complication 類型枚舉
     */
    enum class ComplicationType {
        TOKEN_PRICE,
        WALLET_BALANCE,
        GAS_FEE,
        PORTFOLIO,
        QR_RECEIVE
    }
}

package com.cbstudio.wearwallet.core.domain.usecase.notification

import kotlinx.datetime.Clock
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.notification.NotificationHistory
import com.cbstudio.wearwallet.core.domain.model.notification.NotificationHistoryModel
import com.cbstudio.wearwallet.core.domain.model.notification.NotificationPreferences
import com.cbstudio.wearwallet.core.domain.model.notification.PushSubscription
import com.cbstudio.wearwallet.core.data.repository.NotificationHistoryRepository
import com.cbstudio.wearwallet.core.data.repository.NotificationPreferencesRepository
import com.cbstudio.wearwallet.core.data.repository.PushSubscriptionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 通知管理業務邏輯
 */
class ManageNotificationsUseCase(
    private val notificationHistoryRepository: NotificationHistoryRepository,
    private val notificationPreferencesRepository: NotificationPreferencesRepository,
    private val pushSubscriptionRepository: PushSubscriptionRepository
) {
    
    // === 通知歷史管理 ===
    
    /**
     * 獲取所有通知歷史
     */
    suspend fun getAllNotifications(walletId: String): Result<List<NotificationHistoryModel>> {
        return when (val result = notificationHistoryRepository.getAllNotifications(walletId)) {
            is Result.Success -> Result.Success(result.data.map { NotificationHistoryModel.fromOriginal(it) })
            is Result.Failure -> Result.Failure(result.exception)
            is Result.Loading -> Result.Loading()
        }
    }
    
    /**
     * 觀察通知歷史變化
     */
    fun observeNotifications(walletId: String): Flow<List<NotificationHistoryModel>> {
        return notificationHistoryRepository.observeAllNotifications(walletId)
            .map { list -> list.map { NotificationHistoryModel.fromOriginal(it) } }
    }
    
    /**
     * 添加通知記錄
     */
    suspend fun addNotification(notification: NotificationHistoryModel): Result<NotificationHistoryModel> {
        return try {
            // 驗證輸入
            if (notification.walletId.isBlank()) {
                return Result.Failure(Exception("錢包 ID 不能為空"))
            }
            
            if (notification.title.isBlank()) {
                return Result.Failure(Exception("通知標題不能為空"))
            }
            
            // 轉換為原始模型並保存
            val originalNotification = NotificationHistoryModel.toOriginal(notification)
            when (val result = notificationHistoryRepository.addNotification(originalNotification)) {
                is Result.Success -> Result.Success(notification)
                is Result.Failure -> Result.Failure(result.exception)
                is Result.Loading -> Result.Loading()
            }
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    /**
     * 標記通知為已讀
     */
    suspend fun markAsRead(id: String): Result<Unit> {
        return notificationHistoryRepository.markAsRead(id)
    }
    
    /**
     * 批量標記為已讀
     */
    suspend fun markMultipleAsRead(ids: List<String>): Result<Unit> {
        return notificationHistoryRepository.markMultipleAsRead(ids)
    }
    
    /**
     * 標記所有通知為已讀
     */
    suspend fun markAllAsRead(walletId: String): Result<Unit> {
        return notificationHistoryRepository.markAllAsRead(walletId)
    }
    
    /**
     * 獲取未讀通知數量
     */
    suspend fun getUnreadCount(walletId: String): Result<Int> {
        return when (val result = notificationHistoryRepository.getUnreadCount(walletId)) {
            is Result.Success -> Result.Success(result.data.toInt())
            is Result.Failure -> Result.Failure(result.exception)
            is Result.Loading -> Result.Loading()
        }
    }
    
    /**
     * 刪除通知
     */
    suspend fun deleteNotification(id: String): Result<Unit> {
        return notificationHistoryRepository.deleteNotification(id)
    }
    
    /**
     * 清除舊通知
     */
    suspend fun clearOldNotifications(walletId: String, daysOld: Int): Result<Unit> {
        return notificationHistoryRepository.clearOldNotifications(walletId, daysOld)
    }
    
    // === 通知偏好設置 ===
    
    /**
     * 獲取通知偏好設置
     */
    suspend fun getNotificationPreferences(walletId: String): Result<NotificationPreferences?> {
        return notificationPreferencesRepository.getPreferences(walletId)
    }
    
    /**
     * 更新通知偏好設置
     */
    suspend fun updateNotificationPreferences(preferences: NotificationPreferences): Result<NotificationPreferences> {
        return try {
            // 驗證輸入
            if (preferences.walletId.isBlank()) {
                return Result.Failure(Exception("錢包 ID 不能為空"))
            }
            
            notificationPreferencesRepository.updatePreferences(preferences)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    /**
     * 切換價格提醒通知
     */
    suspend fun togglePriceAlerts(walletId: String): Result<Unit> {
        return try {
            val preferencesResult = notificationPreferencesRepository.getPreferences(walletId)
            when (preferencesResult) {
                is Result.Success -> {
                    val preferences = preferencesResult.data
                    if (preferences != null) {
                        val updated = preferences.copy(
                            priceAlertsEnabled = !preferences.priceAlertsEnabled,
                            lastUpdatedAt = Clock.System.now().toEpochMilliseconds()
                        )
                        notificationPreferencesRepository.updatePreferences(updated)
                        Result.Success(Unit)
                    } else {
                        Result.Failure(Exception("通知偏好設置不存在"))
                    }
                }
                is Result.Failure -> Result.Failure(preferencesResult.exception)
                is Result.Loading -> Result.Failure(Exception("查詢狀態異常"))
            }
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    /**
     * 切換交易通知
     */
    suspend fun toggleTransactionNotifications(walletId: String): Result<Unit> {
        return try {
            val preferencesResult = notificationPreferencesRepository.getPreferences(walletId)
            when (preferencesResult) {
                is Result.Success -> {
                    val preferences = preferencesResult.data
                    if (preferences != null) {
                        val updated = preferences.copy(
                            transactionAlertsEnabled = !preferences.transactionAlertsEnabled,
                            lastUpdatedAt = Clock.System.now().toEpochMilliseconds()
                        )
                        notificationPreferencesRepository.updatePreferences(updated)
                        Result.Success(Unit)
                    } else {
                        Result.Failure(Exception("通知偏好設置不存在"))
                    }
                }
                is Result.Failure -> Result.Failure(preferencesResult.exception)
                is Result.Loading -> Result.Failure(Exception("查詢狀態異常"))
            }
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    // === Push 訂閱管理 ===
    
    /**
     * 獲取最近訂閱
     */
    suspend fun getRecentSubscriptions(walletAddress: String, limit: Int = 10): Result<List<PushSubscription>> {
        return pushSubscriptionRepository.getRecentlySubscribed(walletAddress, limit)
    }
    
    /**
     * 觀察訂閱變化
     */
    fun observeSubscriptions(walletAddress: String): Flow<List<PushSubscription>> {
        return pushSubscriptionRepository.observeSubscriptionsByWallet(walletAddress)
    }
    
    /**
     * 添加 Push 訂閱
     */
    suspend fun addSubscription(walletAddress: String, channelAddress: String): Result<Unit> {
        return try {
            // 驗證輸入
            if (walletAddress.isBlank()) {
                return Result.Failure(Exception("錢包地址不能為空"))
            }
            
            if (channelAddress.isBlank()) {
                return Result.Failure(Exception("頻道地址不能為空"))
            }
            
            pushSubscriptionRepository.subscribe(walletAddress, channelAddress)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    /**
     * 取消訂閱
     */
    suspend fun unsubscribe(walletAddress: String, channelAddress: String): Result<Unit> {
        return pushSubscriptionRepository.unsubscribe(walletAddress, channelAddress)
    }
    
    /**
     * 刪除訂閱
     */
    suspend fun deleteSubscription(walletAddress: String, channelAddress: String): Result<Unit> {
        return pushSubscriptionRepository.deleteSubscription(walletAddress, channelAddress)
    }
    
    /**
     * 清理舊的未訂閱記錄
     */
    suspend fun cleanupOldUnsubscriptions(daysOld: Int = 30): Result<Unit> {
        val cutoffTime = Clock.System.now().toEpochMilliseconds() - (daysOld * 24 * 60 * 60 * 1000L)
        return pushSubscriptionRepository.cleanupOldUnsubscriptions(cutoffTime)
    }
}
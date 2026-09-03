package com.cbstudio.wearwallet.core.data.repository

import kotlinx.datetime.Clock
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.common.asResult
import com.cbstudio.wearwallet.core.database.CoreWalletDatabase
import com.cbstudio.wearwallet.core.domain.model.notification.NotificationHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 通知歷史記錄 Repository - coreKmp 實現
 */
class NotificationHistoryRepository(
    private val database: CoreWalletDatabase
) {
    private val queries = database.notificationHistoryQueries
    
    // 查詢操作
    fun observeAllNotifications(walletId: String): Flow<List<NotificationHistory>> {
        return queries.selectAllByWallet(walletId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { list ->
                list.map { it.toNotificationHistory() }
            }
    }
    
    fun observeUnreadNotifications(walletId: String): Flow<List<NotificationHistory>> {
        return queries.selectUnreadByWallet(walletId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { list ->
                list.map { it.toNotificationHistory() }
            }
    }
    
    fun observeNotificationsByType(
        walletId: String, 
        type: NotificationHistory.NotificationType
    ): Flow<List<NotificationHistory>> {
        return queries.selectByType(walletId, type.name)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { list ->
                list.map { it.toNotificationHistory() }
            }
    }
    
    suspend fun getNotificationById(id: String): Result<NotificationHistory?> = asResult {
        queries.selectById(id)
            .executeAsOneOrNull()
            ?.toNotificationHistory()
    }
    
    suspend fun getRecentNotifications(
        walletId: String, 
        limit: Int, 
        offset: Int
    ): Result<List<NotificationHistory>> = asResult {
        queries.selectRecent(walletId, limit.toLong(), offset.toLong())
            .executeAsList()
            .map { it.toNotificationHistory() }
    }
    
    suspend fun getNotificationsByTimeRange(
        walletId: String,
        startTime: Long,
        endTime: Long
    ): Result<List<NotificationHistory>> = asResult {
        queries.selectByTimeRange(walletId, startTime, endTime)
            .executeAsList()
            .map { it.toNotificationHistory() }
    }
    
    suspend fun getUnreadCount(walletId: String): Result<Long> = asResult {
        queries.countUnread(walletId).executeAsOne()
    }
    
    suspend fun getAllNotifications(walletId: String): Result<List<NotificationHistory>> = asResult {
        queries.selectAllByWallet(walletId)
            .executeAsList()
            .map { it.toNotificationHistory() }
    }
    
    suspend fun addNotification(notification: NotificationHistory): Result<NotificationHistory> = asResult {
        queries.insertNotification(
            id = notification.id,
            wallet_id = notification.walletId,
            notification_type = notification.notificationType.name,
            title = notification.title,
            body = notification.body,
            data_ = notification.data,
            timestamp = notification.timestamp,
            read = if (notification.read) 1L else 0L,
            push_notification_id = notification.pushNotificationId
        )
        notification
    }
    
    suspend fun markMultipleAsRead(ids: List<String>): Result<Unit> = asResult {
        ids.forEach { id ->
            queries.markAsRead(id)
        }
    }
    
    suspend fun clearOldNotifications(walletId: String, daysOld: Int): Result<Unit> = asResult {
        val cutoffTime = Clock.System.now().toEpochMilliseconds() - (daysOld * 24 * 60 * 60 * 1000L)
        queries.cleanupOldNotifications(cutoffTime)
    }
    
    suspend fun searchNotifications(
        walletId: String, 
        query: String
    ): Result<List<NotificationHistory>> = asResult {
        queries.searchNotifications(walletId, query, query)
            .executeAsList()
            .map { it.toNotificationHistory() }
    }
    
    // 寫入操作
    suspend fun insertNotification(notification: NotificationHistory): Result<Unit> = asResult {
        queries.insertNotification(
            id = notification.id,
            wallet_id = notification.walletId,
            notification_type = notification.notificationType.name,
            title = notification.title,
            body = notification.body,
            data_ = notification.data,
            timestamp = notification.timestamp,
            read = if (notification.read) 1L else 0L,
            push_notification_id = notification.pushNotificationId
        )
    }
    
    suspend fun markAsRead(id: String): Result<Unit> = asResult {
        queries.markAsRead(id)
    }
    
    suspend fun markAllAsRead(walletId: String): Result<Unit> = asResult {
        queries.markAllAsReadForWallet(walletId)
    }
    
    suspend fun markTypeAsRead(
        walletId: String, 
        type: NotificationHistory.NotificationType
    ): Result<Unit> = asResult {
        queries.markTypeAsReadForWallet(walletId, type.name)
    }
    
    // 刪除操作
    suspend fun deleteNotification(id: String): Result<Unit> = asResult {
        queries.deleteById(id)
    }
    
    suspend fun deleteAllNotifications(walletId: String): Result<Unit> = asResult {
        queries.deleteAllByWallet(walletId)
    }
    
    suspend fun cleanupOldNotifications(olderThanTimestamp: Long): Result<Unit> = asResult {
        queries.cleanupOldNotifications(olderThanTimestamp)
    }
    
    // 擴展函數：將 SQLDelight 生成的類型轉換為 domain model
    private fun com.cbstudio.wearwallet.core.database.Notification_history.toNotificationHistory(): NotificationHistory {
        return NotificationHistory(
            id = id,
            walletId = wallet_id,
            notificationType = NotificationHistory.NotificationType.valueOf(notification_type),
            title = title,
            body = body,
            data = data_,
            timestamp = timestamp,
            read = read == 1L,
            pushNotificationId = push_notification_id
        )
    }
}

/**
 * Repository 介面定義
 */
interface INotificationHistoryRepository {
    // 查詢操作
    fun observeAllNotifications(walletId: String): Flow<List<NotificationHistory>>
    fun observeUnreadNotifications(walletId: String): Flow<List<NotificationHistory>>
    fun observeNotificationsByType(walletId: String, type: NotificationHistory.NotificationType): Flow<List<NotificationHistory>>
    suspend fun getNotificationById(id: String): Result<NotificationHistory?>
    suspend fun getRecentNotifications(walletId: String, limit: Int, offset: Int): Result<List<NotificationHistory>>
    suspend fun getNotificationsByTimeRange(walletId: String, startTime: Long, endTime: Long): Result<List<NotificationHistory>>
    suspend fun getUnreadCount(walletId: String): Result<Long>
    suspend fun searchNotifications(walletId: String, query: String): Result<List<NotificationHistory>>
    
    // 寫入操作
    suspend fun insertNotification(notification: NotificationHistory): Result<Unit>
    suspend fun markAsRead(id: String): Result<Unit>
    suspend fun markAllAsRead(walletId: String): Result<Unit>
    suspend fun markTypeAsRead(walletId: String, type: NotificationHistory.NotificationType): Result<Unit>
    
    // 刪除操作
    suspend fun deleteNotification(id: String): Result<Unit>
    suspend fun deleteAllNotifications(walletId: String): Result<Unit>
    suspend fun cleanupOldNotifications(olderThanTimestamp: Long): Result<Unit>
}
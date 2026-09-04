package com.cbstudio.wearwallet.core.domain.model.notification

/**
 * 統一的通知類型定義
 */
enum class NotificationType {
    PRICE_ALERT,      // 價格提醒
    TRANSACTION,      // 交易通知
    SECURITY,         // 安全警告
    DEFI,             // DeFi 相關通知
    SYSTEM,           // 系統通知
    MARKETING         // 行銷通知
}

/**
 * 統一的通知歷史模型 - 與 UseCase 兼容
 */
data class NotificationHistoryModel(
    val id: String,
    val walletId: String,
    val type: NotificationType,
    val title: String,
    val content: String,
    val data: String? = null,
    val createdAt: Long,
    val isRead: Boolean = false,
    val updatedAt: Long = createdAt
) {
    companion object {
        /**
         * 從原始 NotificationHistory 轉換
         */
        fun fromOriginal(original: NotificationHistory): NotificationHistoryModel {
            return NotificationHistoryModel(
                id = original.id,
                walletId = original.walletId,
                type = when (original.notificationType) {
                    NotificationHistory.NotificationType.PRICE_ALERT -> NotificationType.PRICE_ALERT
                    NotificationHistory.NotificationType.TRANSACTION -> NotificationType.TRANSACTION
                    NotificationHistory.NotificationType.SECURITY -> NotificationType.SECURITY
                    NotificationHistory.NotificationType.DEFI -> NotificationType.DEFI
                },
                title = original.title,
                content = original.body,
                data = original.data,
                createdAt = original.timestamp,
                isRead = original.read
            )
        }
        
        /**
         * 轉換為原始 NotificationHistory
         */
        fun toOriginal(model: NotificationHistoryModel): NotificationHistory {
            return NotificationHistory(
                id = model.id,
                walletId = model.walletId,
                notificationType = when (model.type) {
                    NotificationType.PRICE_ALERT -> NotificationHistory.NotificationType.PRICE_ALERT
                    NotificationType.TRANSACTION -> NotificationHistory.NotificationType.TRANSACTION
                    NotificationType.SECURITY -> NotificationHistory.NotificationType.SECURITY
                    NotificationType.DEFI -> NotificationHistory.NotificationType.DEFI
                    else -> NotificationHistory.NotificationType.DEFI
                },
                title = model.title,
                body = model.content,
                data = model.data ?: "{}",
                timestamp = model.createdAt,
                read = model.isRead
            )
        }
    }
}
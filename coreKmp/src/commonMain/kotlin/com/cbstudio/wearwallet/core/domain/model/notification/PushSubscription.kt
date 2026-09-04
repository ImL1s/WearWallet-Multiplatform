package com.cbstudio.wearwallet.core.domain.model.notification

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

/**
 * Push Protocol 訂閱記錄 - coreKmp 版本
 */
data class PushSubscription(
    val walletAddress: String,
    val channelAddress: String,
    val subscribed: Boolean,
    val subscribedAt: Long? = null,
    val unsubscribedAt: Long? = null,
    val lastSyncedAt: Long = Clock.System.now().toEpochMilliseconds()
) {
    /**
     * 獲取訂閱時間的 Instant 表示
     */
    fun getSubscribedInstant(): Instant? = subscribedAt?.let { Instant.fromEpochMilliseconds(it) }
    
    /**
     * 獲取取消訂閱時間的 Instant 表示
     */
    fun getUnsubscribedInstant(): Instant? = unsubscribedAt?.let { Instant.fromEpochMilliseconds(it) }
    
    /**
     * 獲取最後同步時間的 Instant 表示
     */
    fun getLastSyncedInstant(): Instant = Instant.fromEpochMilliseconds(lastSyncedAt)
    
    /**
     * 檢查是否需要同步（超過指定分鐘數）
     */
    fun needsSync(minutesSinceLastSync: Int = 60): Boolean {
        val currentTime = Clock.System.now().toEpochMilliseconds()
        val timeSinceSync = currentTime - lastSyncedAt
        return timeSinceSync > (minutesSinceLastSync * 60 * 1000L)
    }
    
    /**
     * 獲取訂閱狀態的描述文字
     */
    fun getStatusDescription(): String {
        return if (subscribed) {
            subscribedAt?.let { 
                "Subscribed since ${formatTimestamp(it)}" 
            } ?: "Subscribed"
        } else {
            unsubscribedAt?.let { 
                "Unsubscribed since ${formatTimestamp(it)}" 
            } ?: "Not subscribed"
        }
    }
    
    /**
     * 檢查訂閱是否已過期（長時間未同步）
     */
    fun isStale(days: Int = 30): Boolean {
        val staleTime = Clock.System.now().toEpochMilliseconds() - (days * 24 * 60 * 60 * 1000L)
        return lastSyncedAt < staleTime
    }
    
    /**
     * 切換訂閱狀態
     */
    fun toggleSubscription(): PushSubscription {
        val now = Clock.System.now().toEpochMilliseconds()
        return if (subscribed) {
            // 取消訂閱
            copy(
                subscribed = false,
                unsubscribedAt = now,
                lastSyncedAt = now
            )
        } else {
            // 重新訂閱
            copy(
                subscribed = true,
                subscribedAt = now,
                unsubscribedAt = null,
                lastSyncedAt = now
            )
        }
    }
    
    /**
     * 更新同步時間
     */
    fun updateSyncTime(): PushSubscription {
        return copy(lastSyncedAt = Clock.System.now().toEpochMilliseconds())
    }
    
    companion object {
        /**
         * 創建新的訂閱
         */
        fun createSubscription(
            walletAddress: String,
            channelAddress: String
        ): PushSubscription {
            val now = Clock.System.now().toEpochMilliseconds()
            return PushSubscription(
                walletAddress = walletAddress,
                channelAddress = channelAddress,
                subscribed = true,
                subscribedAt = now,
                unsubscribedAt = null,
                lastSyncedAt = now
            )
        }
        
        /**
         * 創建取消訂閱的記錄
         */
        fun createUnsubscription(
            walletAddress: String,
            channelAddress: String,
            previousSubscribedAt: Long? = null
        ): PushSubscription {
            val now = Clock.System.now().toEpochMilliseconds()
            return PushSubscription(
                walletAddress = walletAddress,
                channelAddress = channelAddress,
                subscribed = false,
                subscribedAt = previousSubscribedAt,
                unsubscribedAt = now,
                lastSyncedAt = now
            )
        }
        
        private fun formatTimestamp(timestamp: Long): String {
            val instant = Instant.fromEpochMilliseconds(timestamp)
            return instant.toString()
        }
    }
}

/**
 * Push Protocol 頻道資訊
 */
@Serializable
data class PushChannel(
    val address: String,
    val name: String,
    val description: String,
    val icon: String?,
    val verified: Boolean = false,
    val subscriberCount: Int = 0
)

/**
 * 批量訂閱操作結果
 */
@Serializable
data class BatchSubscriptionResult(
    val successful: List<String>,
    val failed: List<SubscriptionError>
)

/**
 * 訂閱錯誤資訊
 */
@Serializable
data class SubscriptionError(
    val channelAddress: String,
    val error: String,
    val errorCode: ErrorCode
) {
    enum class ErrorCode {
        NETWORK_ERROR,
        INVALID_CHANNEL,
        ALREADY_SUBSCRIBED,
        NOT_SUBSCRIBED,
        PERMISSION_DENIED,
        UNKNOWN
    }
}

/**
 * 訂閱統計資訊
 */
data class SubscriptionStats(
    val totalSubscriptions: Int,
    val activeSubscriptions: Int,
    val recentlySubscribed: List<PushSubscription>,
    val needsSyncCount: Int
)
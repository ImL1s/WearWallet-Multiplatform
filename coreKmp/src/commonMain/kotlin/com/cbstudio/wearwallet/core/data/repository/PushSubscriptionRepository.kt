package com.cbstudio.wearwallet.core.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.common.asResult
import com.cbstudio.wearwallet.core.database.CoreWalletDatabase
import com.cbstudio.wearwallet.core.domain.model.notification.PushSubscription
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock

/**
 * Push Protocol 訂閱 Repository - coreKmp 實現
 */
class PushSubscriptionRepository(
    private val database: CoreWalletDatabase
) : IPushSubscriptionRepository {
    private val queries = database.pushSubscriptionQueries
    
    override fun observeSubscriptionsByWallet(walletAddress: String): Flow<List<PushSubscription>> {
        return queries.selectByWallet(walletAddress)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { list ->
                list.map { it.toPushSubscription() }
            }
    }
    
    override fun observeActiveSubscriptionsByWallet(walletAddress: String): Flow<List<PushSubscription>> {
        return queries.selectActiveByWallet(walletAddress)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { list ->
                list.map { it.toPushSubscription() }
            }
    }
    
    override suspend fun getSubscriptionStatus(
        walletAddress: String, 
        channelAddress: String
    ): Result<PushSubscription?> = asResult {
        queries.selectSubscriptionStatus(walletAddress, channelAddress)
            .executeAsOneOrNull()
            ?.toPushSubscription()
    }
    
    override suspend fun isSubscribed(
        walletAddress: String, 
        channelAddress: String
    ): Result<Boolean> = asResult {
        queries.checkSubscribed(walletAddress, channelAddress)
            .executeAsOneOrNull() == 1L
    }
    
    override suspend fun getChannelSubscriberCount(channelAddress: String): Result<Long> = asResult {
        queries.countChannelSubscribers(channelAddress).executeAsOne()
    }
    
    override suspend fun getSubscriptionsNeedingSync(
        olderThanTimestamp: Long, 
        limit: Int
    ): Result<List<PushSubscription>> = asResult {
        queries.selectNeedingSync(olderThanTimestamp, limit.toLong())
            .executeAsList()
            .map { it.toPushSubscription() }
    }
    
    override suspend fun getSubscriptionCount(walletAddress: String): Result<Long> = asResult {
        queries.countSubscriptions(walletAddress).executeAsOne()
    }
    
    override suspend fun getRecentlySubscribed(
        walletAddress: String, 
        limit: Int
    ): Result<List<PushSubscription>> = asResult {
        queries.selectRecentlySubscribed(walletAddress, limit.toLong())
            .executeAsList()
            .map { it.toPushSubscription() }
    }
    
    override suspend fun upsertSubscription(subscription: PushSubscription): Result<Unit> = asResult {
        queries.upsertSubscription(
            wallet_address = subscription.walletAddress,
            channel_address = subscription.channelAddress,
            subscribed = if (subscription.subscribed) 1L else 0L,
            subscribed_at = subscription.subscribedAt,
            unsubscribed_at = subscription.unsubscribedAt,
            last_synced_at = subscription.lastSyncedAt
        )
    }
    
    override suspend fun subscribe(walletAddress: String, channelAddress: String): Result<Unit> = asResult {
        val now = Clock.System.now().toEpochMilliseconds()
        queries.subscribe(
            wallet_address = walletAddress,
            channel_address = channelAddress,
            subscribed_at = now,
            last_synced_at = now
        )
    }
    
    override suspend fun unsubscribe(walletAddress: String, channelAddress: String): Result<Unit> = asResult {
        val now = Clock.System.now().toEpochMilliseconds()
        queries.unsubscribe(
            unsubscribed_at = now,
            last_synced_at = now,
            wallet_address = walletAddress,
            channel_address = channelAddress
        )
    }
    
    override suspend fun updateSyncTime(walletAddress: String, channelAddress: String): Result<Unit> = asResult {
        val now = Clock.System.now().toEpochMilliseconds()
        queries.updateSyncTime(now, walletAddress, channelAddress)
    }
    
    override suspend fun batchUpdateSyncTime(walletAddress: String): Result<Unit> = asResult {
        val now = Clock.System.now().toEpochMilliseconds()
        queries.batchUpdateSyncTime(now, walletAddress)
    }
    
    override suspend fun deleteSubscription(walletAddress: String, channelAddress: String): Result<Unit> = asResult {
        queries.deleteSubscription(walletAddress, channelAddress)
    }
    
    override suspend fun deleteAllSubscriptionsByWallet(walletAddress: String): Result<Unit> = asResult {
        queries.deleteAllByWallet(walletAddress)
    }
    
    override suspend fun cleanupOldUnsubscriptions(olderThanTimestamp: Long): Result<Unit> = asResult {
        queries.cleanupOldUnsubscriptions(olderThanTimestamp)
    }
    
    // 擴展函數：將 SQLDelight 生成的類型轉換為 domain model
    private fun com.cbstudio.wearwallet.core.database.Push_subscription.toPushSubscription(): PushSubscription {
        return PushSubscription(
            walletAddress = wallet_address,
            channelAddress = channel_address,
            subscribed = subscribed == 1L,
            subscribedAt = subscribed_at,
            unsubscribedAt = unsubscribed_at,
            lastSyncedAt = last_synced_at
        )
    }
}

/**
 * Repository 介面定義
 */
interface IPushSubscriptionRepository {
    fun observeSubscriptionsByWallet(walletAddress: String): Flow<List<PushSubscription>>
    fun observeActiveSubscriptionsByWallet(walletAddress: String): Flow<List<PushSubscription>>
    suspend fun getSubscriptionStatus(walletAddress: String, channelAddress: String): Result<PushSubscription?>
    suspend fun isSubscribed(walletAddress: String, channelAddress: String): Result<Boolean>
    suspend fun getChannelSubscriberCount(channelAddress: String): Result<Long>
    suspend fun getSubscriptionsNeedingSync(olderThanTimestamp: Long, limit: Int): Result<List<PushSubscription>>
    suspend fun getSubscriptionCount(walletAddress: String): Result<Long>
    suspend fun getRecentlySubscribed(walletAddress: String, limit: Int): Result<List<PushSubscription>>
    suspend fun upsertSubscription(subscription: PushSubscription): Result<Unit>
    suspend fun subscribe(walletAddress: String, channelAddress: String): Result<Unit>
    suspend fun unsubscribe(walletAddress: String, channelAddress: String): Result<Unit>
    suspend fun updateSyncTime(walletAddress: String, channelAddress: String): Result<Unit>
    suspend fun batchUpdateSyncTime(walletAddress: String): Result<Unit>
    suspend fun deleteSubscription(walletAddress: String, channelAddress: String): Result<Unit>
    suspend fun deleteAllSubscriptionsByWallet(walletAddress: String): Result<Unit>
    suspend fun cleanupOldUnsubscriptions(olderThanTimestamp: Long): Result<Unit>
}
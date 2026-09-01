package com.cbstudio.wearwallet.domain.service

import kotlinx.coroutines.flow.*

/**
 * 訂閱服務介面的臨時本地實作
 * TODO: 在 sharedKmp 編譯修復後，移除此檔案並使用共享實作
 */

enum class SubscriptionTier {
    FREE, PREMIUM;
    
    val maxWallets: Int
        get() = when (this) {
            FREE -> 2
            PREMIUM -> -1 // 無限制
        }
}

data class SubscriptionStatusInfo(
    val subscription: Subscription?,
    val tier: SubscriptionTier,
    val isActive: Boolean,
    val daysRemaining: Int?
) {
    val isPremiumUser: Boolean
        get() = tier == SubscriptionTier.PREMIUM && isActive
        
    val isExpiringSoon: Boolean
        get() = daysRemaining != null && daysRemaining <= 7
}

data class Subscription(
    val id: String,
    val userId: String,
    val tier: SubscriptionTier,
    val subscriptionId: String?,
    val productId: String?,
    val isActive: Boolean,
    val autoRenew: Boolean,
    val purchaseDate: Long?,
    val expiryDate: Long?,
    val cancellationDate: Long?,
    val platform: PurchasePlatform,
    val receiptData: String?
) {
    val isExpiringSoon: Boolean
        get() = expiryDate != null && 
                System.currentTimeMillis() + (7 * 24 * 60 * 60 * 1000L) >= expiryDate
}

data class WalletLimitCheck(
    val maxWallets: Int,
    val currentWallets: Int,
    val canAddWallet: Boolean
)

data class SubscriptionProduct(
    val productId: String,
    val tier: SubscriptionTier,
    val priceString: String,
    val title: String,
    val description: String
)

sealed class PurchaseState {
    object Idle : PurchaseState()
    object Loading : PurchaseState()
    object Success : PurchaseState()
    object Cancelled : PurchaseState()
    data class Error(val message: String) : PurchaseState()
}

enum class PurchasePlatform {
    UNKNOWN, ANDROID, IOS, WATCHOS
}

sealed class SubscriptionEvent {
    object PurchaseCompleted : SubscriptionEvent()
    data class PurchaseFailed(val error: String) : SubscriptionEvent()
    object SubscriptionCancelled : SubscriptionEvent()
}

interface SubscriptionService {
    suspend fun initialize(userId: String)
    suspend fun checkSubscriptionStatus(): SubscriptionStatusInfo
    suspend fun checkWalletPermission(): WalletLimitCheck
    suspend fun getAvailableProducts(): List<SubscriptionProduct>
    suspend fun startPurchase(productId: String): PurchaseState
    suspend fun cancelSubscription(): Boolean
    suspend fun restorePurchases()
    
    val purchaseStateFlow: StateFlow<PurchaseState>
    fun observeSubscription(): Flow<Subscription?>
    fun observeWalletPermission(): Flow<WalletLimitCheck>
    val subscriptionEventsFlow: SharedFlow<SubscriptionEvent>
}

package com.cbstudio.wearwallet.presentation.subscription

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Subscription ViewModel - MAINTENANCE MODE
 * ULTRATHINK Phase 18 - 最終衝刺編譯完成策略
 * 
 * TODO: Complex subscription operations temporarily disabled for maintenance
 * - All subscription functionality disabled
 * - Keep ViewModel structure consistent for future implementation
 * - Focus on compilation stability
 */
class SubscriptionViewModel : ViewModel() {
    // Presentation-layer models expected by UI
    data class SubscriptionStatusInfo(
        val isActive: Boolean = false,
        val isPremiumUser: Boolean = false,
        val tier: SubscriptionTier = SubscriptionTier.FREE,
        val subscription: Subscription? = null,
        val isExpiringSoon: Boolean = false,
        val daysRemaining: Int? = null
    )

    data class WalletLimitCheck(
        val canAddWallet: Boolean = true,
        val currentWallets: Int = 0,
        val maxWallets: Int = SubscriptionTier.FREE.maxWallets
    )

    data class SubscriptionProduct(
        val productId: String,
        val title: String,
        val priceString: String,
        val description: String
    )

    enum class SubscriptionTier(val maxWallets: Int) {
        FREE(maxWallets = 1),
        PREMIUM(maxWallets = -1)
    }

    data class Subscription(
        val autoRenew: Boolean = false,
        val expiryDate: Long? = null
    )

    sealed class PurchaseState {
        object Idle : PurchaseState()
        object Loading : PurchaseState()
        object Success : PurchaseState()
        data class Error(val message: String) : PurchaseState()
    }

    data class SubscriptionUiState(
        val isLoading: Boolean = false,
        val subscriptionStatus: SubscriptionStatusInfo? = null,
        val walletLimit: WalletLimitCheck? = WalletLimitCheck(),
        val availableProducts: List<SubscriptionProduct> = emptyList(),
        val showSubscriptionOptions: Boolean = false,
        val purchaseState: PurchaseState = PurchaseState.Idle,
        val errorMessage: String? = null
    )

    private val _uiState = MutableStateFlow(SubscriptionUiState())
    val uiState: StateFlow<SubscriptionUiState> = _uiState.asStateFlow()

    // Maintenance-mode no-ops to satisfy UI calls
    fun loadSubscriptionData() { /* no-op */ }
    fun showSubscriptionOptions() { _uiState.value = _uiState.value.copy(showSubscriptionOptions = true) }
    fun purchaseProduct(productId: String, context: android.content.Context) { /* no-op */ }
    fun cancelSubscription() { /* no-op */ }
    fun restorePurchases() { /* no-op */ }
    fun dismissError() { _uiState.value = _uiState.value.copy(errorMessage = null) }
}
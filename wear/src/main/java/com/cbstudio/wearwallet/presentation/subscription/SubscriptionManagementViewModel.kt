package com.cbstudio.wearwallet.presentation.subscription

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.app.Activity
import com.cbstudio.wearwallet.data.service.BillingServiceImpl
import com.cbstudio.wearwallet.domain.service.*
import com.cbstudio.wearwallet.services.FirebaseService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * SubscriptionManagementScreen 的 ViewModel
 * 管理訂閱狀態、購買流程、取消訂閱等操作
 */
// @HiltViewModel  // Removed Hilt
class SubscriptionManagementViewModel(
    private val subscriptionService: SubscriptionService,
    private val billingService: BillingServiceImpl,
    private val subscriptionPurchaseHelper: SubscriptionPurchaseHelper
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SubscriptionManagementUiState())
    val uiState: StateFlow<SubscriptionManagementUiState> = _uiState.asStateFlow()
    
    val purchaseState: StateFlow<PurchaseState> = billingService.purchaseState
    
    init {
        loadSubscriptionData()
        observeSubscriptionEvents()
    }
    
    private fun loadSubscriptionData() {
        viewModelScope.launch {
            // 初始化 Billing Service
            billingService.initialize()
            subscriptionService.initialize("user_001") // TODO: Use real user ID
            
            // 載入初始狀態
            val initialStatus = subscriptionService.checkSubscriptionStatus()
            val initialWalletLimit = subscriptionService.checkWalletPermission()
            val initialProducts = subscriptionService.getAvailableProducts()
            
            _uiState.update {
                it.copy(
                    subscriptionStatus = initialStatus,
                    walletLimitCheck = initialWalletLimit,
                    availableProducts = initialProducts,
                    isLoading = false
                )
            }
            
            // 觀察訂閱狀態變化
            launch {
                subscriptionService.observeSubscription().collect { subscription ->
                    val status = subscriptionService.checkSubscriptionStatus()
                    _uiState.update { it.copy(subscriptionStatus = status) }
                }
            }
            
            // 觀察錢包限制變化
            launch {
                subscriptionService.observeWalletPermission().collect { walletLimit ->
                    _uiState.update { it.copy(walletLimitCheck = walletLimit) }
                }
            }
            
            // 觀察可用產品
            launch {
                billingService.availableProducts.collect { products ->
                    val subscriptionProducts = products.map { 
                        billingService.toSubscriptionProduct(it) 
                    }
                    _uiState.update { 
                        it.copy(
                            availableProducts = subscriptionProducts
                        ) 
                    }
                }
            }
        }
    }
    
    private fun observeSubscriptionEvents() {
        // Observe subscription events if available
    }
    
    /**
     * 開始購買流程
     */
    fun startPurchase(productId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(pendingPurchaseProductId = productId) }
            // 注意：實際的購買流程需要在 Activity 中調用，
            // 因為需要 Activity 引用來啟動 Google Play 購買界面
        }
    }
    
    /**
     * 執行購買（由 Activity 調用）
     */
    suspend fun executePurchase(activity: Activity, productId: String): Boolean {
        return try {
            subscriptionPurchaseHelper.setActivity(activity)
            val result = subscriptionPurchaseHelper.startPurchase(productId)
            
            when (result) {
                is SubscriptionPurchaseResult.Success -> {
                    onPurchaseComplete()
                    true
                }
                is SubscriptionPurchaseResult.Error -> {
                    _uiState.update { 
                        it.copy(
                            showError = true,
                            errorMessage = result.message
                        ) 
                    }
                    false
                }
                is SubscriptionPurchaseResult.MaintenanceMode -> {
                    _uiState.update { 
                        it.copy(
                            showError = true,
                            errorMessage = result.message
                        ) 
                    }
                    false
                }
            }
        } catch (e: Exception) {
            _uiState.update { 
                it.copy(
                    showError = true,
                    errorMessage = "購買失敗：${e.message}"
                ) 
            }
            false
        } finally {
            subscriptionPurchaseHelper.setActivity(null)
            _uiState.update { it.copy(pendingPurchaseProductId = null) }
        }
    }
    
    /**
     * 取消訂閱
     */
    fun cancelSubscription() {
        viewModelScope.launch {
            _uiState.update { it.copy(showCancelConfirmation = true) }
        }
    }
    
    /**
     * 確認取消訂閱
     */
    fun confirmCancelSubscription() {
        viewModelScope.launch {
            _uiState.update { it.copy(showCancelConfirmation = false, isLoading = true) }
            
            val success = subscriptionService.cancelSubscription()
            
            if (success) {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        showSuccess = true,
                        successMessage = "訂閱已取消，將在到期後停止服務"
                    ) 
                }
            } else {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        showError = true,
                        errorMessage = "取消訂閱失敗，請稍後再試"
                    ) 
                }
            }
        }
    }
    
    /**
     * 恢復購買
     */
    fun restorePurchases() {
        viewModelScope.launch {
            billingService.restorePurchases()
        }
    }
    
    /**
     * 購買完成處理
     */
    fun onPurchaseComplete() {
        viewModelScope.launch {
            _uiState.update { 
                it.copy(
                    showSuccess = true,
                    successMessage = "升級成功！歡迎成為 Premium 會員"
                ) 
            }
        }
    }
    
    /**
     * 關閉購買錯誤
     */
    fun dismissPurchaseError() {
        // 購買狀態由 subscriptionService 管理，這裡只需要更新 UI 狀態
        _uiState.update { 
            it.copy(showError = false, errorMessage = null) 
        }
    }
    
    /**
     * 關閉取消確認對話框
     */
    fun dismissCancelConfirmation() {
        _uiState.update { it.copy(showCancelConfirmation = false) }
    }
    
    /**
     * 關閉成功訊息
     */
    fun dismissSuccess() {
        _uiState.update { 
            it.copy(showSuccess = false, successMessage = null) 
        }
    }
    
}

/**
 * 訂閱管理 UI 狀態
 */
data class SubscriptionManagementUiState(
    val subscriptionStatus: SubscriptionStatusInfo? = null,
    val walletLimitCheck: WalletLimitCheck? = null,
    val availableProducts: List<SubscriptionProduct> = emptyList(),
    val isLoading: Boolean = true,
    val showCancelConfirmation: Boolean = false,
    val showError: Boolean = false,
    val errorMessage: String? = null,
    val showSuccess: Boolean = false,
    val successMessage: String? = null,
    val pendingPurchaseProductId: String? = null
)

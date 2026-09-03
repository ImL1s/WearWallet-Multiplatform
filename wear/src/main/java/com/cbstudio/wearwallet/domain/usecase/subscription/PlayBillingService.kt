package com.cbstudio.wearwallet.domain.usecase.subscription

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import com.cbstudio.wearwallet.domain.service.*
import com.cbstudio.wearwallet.domain.model.PlatformPurchaseResult
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Google Play Billing Service
 * 處理 Android 端的訂閱購買和管理功能
 */
@Singleton
class PlayBillingService constructor(
    private val context: Context
) : PurchasesUpdatedListener, BillingClientStateListener {
    
    private lateinit var billingClient: BillingClient
    
    // 連接狀態
    private val _connectionState = MutableStateFlow(BillingConnectionState.DISCONNECTED)
    val connectionState: StateFlow<BillingConnectionState> = _connectionState.asStateFlow()
    
    // 購買狀態
    private val _purchaseState = MutableStateFlow<PurchaseState>(PurchaseState.Idle)
    val purchaseState: StateFlow<PurchaseState> = _purchaseState.asStateFlow()
    
    // 可用產品
    private val _availableProducts = MutableStateFlow<List<ProductDetails>>(emptyList())
    val availableProducts: StateFlow<List<ProductDetails>> = _availableProducts.asStateFlow()
    
    // 購買回調
    private var purchaseCallback: ((PlatformPurchaseResult) -> Unit)? = null
    
    /**
     * 初始化 Billing Client
     */
    fun initialize() {
        billingClient = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases()
            .build()
        
        connectToBillingService()
    }
    
    /**
     * 連接到 Billing 服務
     */
    private fun connectToBillingService() {
        if (!billingClient.isReady) {
            _connectionState.value = BillingConnectionState.CONNECTING
            billingClient.startConnection(this)
        }
    }
    
    /**
     * 查詢可用的訂閱產品
     */
    suspend fun querySubscriptionProducts(): List<ProductDetails> = suspendCancellableCoroutine { continuation ->
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId("premium_monthly")
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )
        
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()
        
        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && productDetailsList != null) {
                _availableProducts.value = productDetailsList
                continuation.resume(productDetailsList)
            } else {
                continuation.resume(emptyList())
            }
        }
    }
    
    /**
     * 開始購買訂閱
     */
    suspend fun purchaseSubscription(
        activity: Activity,
        productId: String
    ): PlatformPurchaseResult = suspendCancellableCoroutine { continuation ->
        
        purchaseCallback = { result ->
            continuation.resume(result)
        }
        
        val productDetails = _availableProducts.value.find { it.productId == productId }
        if (productDetails == null) {
            continuation.resume(PlatformPurchaseResult.Error("找不到產品: $productId"))
            return@suspendCancellableCoroutine
        }
        
        val subscriptionOfferDetails = productDetails.subscriptionOfferDetails?.firstOrNull()
        if (subscriptionOfferDetails == null) {
            continuation.resume(PlatformPurchaseResult.Error("找不到訂閱優惠詳情"))
            return@suspendCancellableCoroutine
        }
        
        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .setOfferToken(subscriptionOfferDetails.offerToken)
                .build()
        )
        
        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()
        
        _purchaseState.value = PurchaseState.Loading
        
        val billingResult = billingClient.launchBillingFlow(activity, billingFlowParams)
        if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            val errorMessage = when (billingResult.responseCode) {
                BillingClient.BillingResponseCode.USER_CANCELED -> "用戶取消購買"
                BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE -> "Billing 服務不可用"
                BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> "Billing 功能不可用"
                BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> "商品不可用"
                BillingClient.BillingResponseCode.DEVELOPER_ERROR -> "開發者錯誤"
                BillingClient.BillingResponseCode.ERROR -> "一般錯誤"
                else -> "未知錯誤: ${billingResult.responseCode}"
            }
            _purchaseState.value = PurchaseState.Error(errorMessage)
            continuation.resume(PlatformPurchaseResult.Error(errorMessage))
        }
    }
    
    /**
     * 查詢已有的購買記錄
     */
    suspend fun queryPurchases(): List<Purchase> = suspendCancellableCoroutine { continuation ->
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        
        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                continuation.resume(purchases)
            } else {
                continuation.resume(emptyList())
            }
        }
    }
    
    /**
     * 確認購買
     */
    suspend fun acknowledgePurchase(purchase: Purchase): Boolean = suspendCancellableCoroutine { continuation ->
        if (purchase.isAcknowledged) {
            continuation.resume(true)
            return@suspendCancellableCoroutine
        }
        
        val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        
        billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
            continuation.resume(billingResult.responseCode == BillingClient.BillingResponseCode.OK)
        }
    }
    
    /**
     * 恢復購買
     */
    suspend fun restorePurchases(): List<Subscription> {
        val purchases = queryPurchases()
        return purchases.mapNotNull { purchase ->
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                convertPurchaseToSubscription(purchase)
            } else {
                null
            }
        }
    }
    
    /**
     * 將 Google Play Purchase 轉換為 Subscription 模型
     */
    private fun convertPurchaseToSubscription(purchase: Purchase): Subscription? {
        val productId = purchase.products.firstOrNull() ?: return null
        val tier = when (productId) {
            "premium_monthly",
            "premium_yearly" -> SubscriptionTier.PREMIUM
            else -> SubscriptionTier.FREE
        }
        
        return Subscription(
            id = purchase.orderId ?: "",
            userId = "", // 需要從外部提供
            tier = tier,
            subscriptionId = purchase.orderId ?: "",
            productId = productId,
            isActive = purchase.purchaseState == Purchase.PurchaseState.PURCHASED,
            autoRenew = purchase.isAutoRenewing,
            purchaseDate = purchase.purchaseTime,
            expiryDate = null,
            cancellationDate = null,
            platform = PurchasePlatform.ANDROID,
            receiptData = purchase.originalJson
        )
    }
    
    // BillingClientStateListener 實作
    override fun onBillingSetupFinished(billingResult: BillingResult) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            _connectionState.value = BillingConnectionState.CONNECTED
        } else {
            _connectionState.value = BillingConnectionState.ERROR
        }
    }
    
    override fun onBillingServiceDisconnected() {
        _connectionState.value = BillingConnectionState.DISCONNECTED
    }
    
    // PurchasesUpdatedListener 實作
    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.let { purchaseList ->
                    for (purchase in purchaseList) {
                        handlePurchase(purchase)
                    }
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                _purchaseState.value = PurchaseState.Cancelled
                purchaseCallback?.invoke(PlatformPurchaseResult.Cancelled)
                purchaseCallback = null
            }
            else -> {
                val errorMessage = "購買失敗: ${billingResult.responseCode}"
                _purchaseState.value = PurchaseState.Error(errorMessage)
                purchaseCallback?.invoke(PlatformPurchaseResult.Error(errorMessage))
                purchaseCallback = null
            }
        }
    }
    
    /**
     * 處理購買結果
     */
    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            // 購買成功，提取收據資料
            val receiptData = purchase.originalJson
            purchaseCallback?.invoke(PlatformPurchaseResult.Success)
            purchaseCallback = null
            
            // 確認購買（如果需要）
            if (!purchase.isAcknowledged) {
                val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                
                billingClient.acknowledgePurchase(acknowledgePurchaseParams) { _ ->
                    // 確認完成
                }
            }
        } else if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
            // 購買待處理
            _purchaseState.value = PurchaseState.Loading
        }
    }
    
    /**
     * 釋放資源
     */
    fun destroy() {
        if (this::billingClient.isInitialized) {
            billingClient.endConnection()
        }
    }
}

/**
 * Billing 連接狀態
 */
enum class BillingConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

package com.cbstudio.wearwallet.data.service

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import com.cbstudio.wearwallet.domain.service.*
import com.cbstudio.wearwallet.services.FirebaseService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Google Play Billing 服務實作
 * 處理訂閱購買、恢復和驗證
 */

@Singleton
class BillingServiceImpl @Inject constructor(
    private val context: Context,
    private val firebaseService: FirebaseService
) : PurchasesUpdatedListener, BillingClientStateListener {
    
    private var billingClient: BillingClient? = null
    
    // 購買狀態流
    private val _purchaseState = MutableStateFlow<PurchaseState>(PurchaseState.Idle)
    val purchaseState: StateFlow<PurchaseState> = _purchaseState.asStateFlow()
    
    // 訂閱狀態流
    private val _subscriptions = MutableStateFlow<List<Purchase>>(emptyList())
    val subscriptions: StateFlow<List<Purchase>> = _subscriptions.asStateFlow()
    
    // 可用產品流
    private val _availableProducts = MutableStateFlow<List<ProductDetails>>(emptyList())
    val availableProducts: StateFlow<List<ProductDetails>> = _availableProducts.asStateFlow()
    
    // 連接狀態
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    // 產品 ID 映射
    companion object {
        const val PREMIUM_MONTHLY = "wearwallet_premium_monthly"
        const val PREMIUM_YEARLY = "wearwallet_premium_yearly"
        
        val SUBSCRIPTION_SKUS = listOf(PREMIUM_MONTHLY, PREMIUM_YEARLY)
    }
    
    /**
     * 初始化 Billing Client
     */
    suspend fun initialize() {
        if (billingClient == null) {
            billingClient = BillingClient.newBuilder(context)
                .setListener(this)
                .enablePendingPurchases()
                .build()
        }
        
        connectToPlayStore()
    }
    
    /**
     * 連接到 Google Play Store
     */
    private suspend fun connectToPlayStore() = suspendCancellableCoroutine<Boolean> { continuation ->
        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    _isConnected.value = true
                    continuation.resume(true)
                    
                    // 連接成功後，查詢可用產品和現有購買
                    queryAvailableProducts()
                    queryPurchases()
                } else {
                    _isConnected.value = false
                    continuation.resume(false)
                    firebaseService.logError(
                        "Billing setup failed",
                        Exception("Response code: ${billingResult.responseCode}")
                    )
                }
            }
            
            override fun onBillingServiceDisconnected() {
                _isConnected.value = false
                // 嘗試重新連接
                retryConnection()
            }
        })
    }
    
    /**
     * 重試連接
     */
    private fun retryConnection() {
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(5000) // 等待 5 秒
            connectToPlayStore()
        }
    }
    
    /**
     * 查詢可用產品
     */
    private fun queryAvailableProducts() {
        val productList = SUBSCRIPTION_SKUS.map { sku ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(sku)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }
        
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()
        
        billingClient?.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                _availableProducts.value = productDetailsList
                
                firebaseService.logEvent("billing_products_loaded", mapOf<String, Any>(
                    "product_count" to productDetailsList.size
                ))
            } else {
                firebaseService.logError(
                    "Failed to query products",
                    Exception("Response code: ${billingResult.responseCode}")
                )
            }
        }
    }
    
    /**
     * 查詢現有購買
     */
    private fun queryPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        
        billingClient?.queryPurchasesAsync(params) { billingResult, purchasesList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                _subscriptions.value = purchasesList
                
                // 處理未確認的購買
                purchasesList.forEach { purchase ->
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && 
                        !purchase.isAcknowledged) {
                        acknowledgePurchase(purchase)
                    }
                }
            }
        }
    }
    
    /**
     * 開始購買流程
     */
    suspend fun launchBillingFlow(
        activity: Activity,
        productId: String
    ): BillingResult = withContext(Dispatchers.Main) {
        val productDetails = _availableProducts.value.find { 
            it.productId == productId 
        } ?: return@withContext BillingResult.newBuilder()
            .setResponseCode(BillingClient.BillingResponseCode.ITEM_NOT_OWNED)
            .build()
        
        val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken
            ?: return@withContext BillingResult.newBuilder()
                .setResponseCode(BillingClient.BillingResponseCode.DEVELOPER_ERROR)
                .build()
        
        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .setOfferToken(offerToken)
                .build()
        )
        
        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()
        
        _purchaseState.value = PurchaseState.Loading
        
        billingClient?.launchBillingFlow(activity, billingFlowParams)
            ?: BillingResult.newBuilder()
                .setResponseCode(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED)
                .build()
    }
    
    /**
     * 處理購買更新
     */
    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: List<Purchase>?
    ) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { purchase ->
                    handlePurchase(purchase)
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                _purchaseState.value = PurchaseState.Cancelled
                firebaseService.logEvent("purchase_cancelled", null)
            }
            else -> {
                _purchaseState.value = PurchaseState.Error(
                    "Purchase failed: ${billingResult.debugMessage}"
                )
                firebaseService.logError(
                    "Purchase failed",
                    Exception("Response code: ${billingResult.responseCode}")
                )
            }
        }
    }
    
    /**
     * 處理購買
     */
    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            // 驗證購買
            if (isSignatureValid(purchase)) {
                // 確認購買
                acknowledgePurchase(purchase)
                
                // 更新訂閱列表
                val currentSubs = _subscriptions.value.toMutableList()
                currentSubs.removeAll { it.orderId == purchase.orderId }
                currentSubs.add(purchase)
                _subscriptions.value = currentSubs
                
                _purchaseState.value = PurchaseState.Success
                
                firebaseService.logEvent("purchase_successful", mapOf<String, Any>(
                    "product_id" to (purchase.products.firstOrNull() ?: ""),
                    "order_id" to (purchase.orderId ?: "")
                ))
            } else {
                _purchaseState.value = PurchaseState.Error("Invalid purchase signature")
                firebaseService.logError(
                    "Invalid purchase signature",
                    Exception("Purchase: ${purchase.orderId}")
                )
            }
        }
    }
    
    /**
     * 驗證購買簽名
     */
    private fun isSignatureValid(purchase: Purchase): Boolean {
        // TODO: 實作伺服器端簽名驗證
        // 目前僅做基本檢查
        return purchase.signature.isNotEmpty()
    }
    
    /**
     * 確認購買
     */
    private fun acknowledgePurchase(purchase: Purchase) {
        if (!purchase.isAcknowledged) {
            val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            
            billingClient?.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    firebaseService.logEvent("purchase_acknowledged", mapOf<String, Any>(
                        "order_id" to (purchase.orderId ?: "")
                    ))
                }
            }
        }
    }
    
    /**
     * 恢復購買
     */
    suspend fun restorePurchases() {
        _purchaseState.value = PurchaseState.Loading
        
        queryPurchases()
        
        // 等待查詢完成
        kotlinx.coroutines.delay(1000)
        
        if (_subscriptions.value.isNotEmpty()) {
            _purchaseState.value = PurchaseState.Success
            firebaseService.logEvent("purchases_restored", mapOf<String, Any>(
                "count" to _subscriptions.value.size
            ))
        } else {
            _purchaseState.value = PurchaseState.Idle
        }
    }
    
    /**
     * 獲取當前有效的訂閱
     */
    fun getActiveSubscription(): Purchase? {
        return _subscriptions.value.firstOrNull { purchase ->
            purchase.purchaseState == Purchase.PurchaseState.PURCHASED
        }
    }
    
    /**
     * 將 ProductDetails 轉換為 SubscriptionProduct
     */
    fun toSubscriptionProduct(productDetails: ProductDetails): SubscriptionProduct {
        val offerDetails = productDetails.subscriptionOfferDetails?.firstOrNull()
        val pricingPhase = offerDetails?.pricingPhases?.pricingPhaseList?.firstOrNull()
        
        return SubscriptionProduct(
            productId = productDetails.productId,
            tier = if (productDetails.productId.contains("premium")) {
                SubscriptionTier.PREMIUM
            } else {
                SubscriptionTier.FREE
            },
            priceString = pricingPhase?.formattedPrice ?: "N/A",
            title = productDetails.title,
            description = productDetails.description
        )
    }
    
    /**
     * Billing 客戶端狀態監聽
     */
    override fun onBillingSetupFinished(billingResult: BillingResult) {
        // 由 connectToPlayStore 處理
    }
    
    override fun onBillingServiceDisconnected() {
        _isConnected.value = false
        retryConnection()
    }
    
    /**
     * 清理資源
     */
    fun dispose() {
        billingClient?.endConnection()
        billingClient = null
        _isConnected.value = false
    }
}

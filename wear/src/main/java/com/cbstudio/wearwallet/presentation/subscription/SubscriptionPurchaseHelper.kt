package com.cbstudio.wearwallet.presentation.subscription

import android.app.Activity
import kotlinx.coroutines.delay
import javax.inject.Singleton

/**
 * 訂閱購買輔助類 - 簡化版本
 * ULTRATHINK Phase 13 - 激進清理後的最小化實現
 */
@Singleton
class SubscriptionPurchaseHelper {
    
    /**
     * 設置當前活動 - 簡化實現
     */
    fun setActivity(activity: Activity?) {
        // TODO: 使用 sharedKmp 的訂閱服務
    }
    
    /**
     * 開始購買流程 - 簡化實現
     */
    suspend fun startPurchase(productId: String): SubscriptionPurchaseResult {
        delay(1000) // 模擬延遲
        return SubscriptionPurchaseResult.MaintenanceMode("訂閱功能遷移到 KMP 架構中，即將可用")
    }
    
    /**
     * 恢復購買 - 簡化實現
     */
    suspend fun restorePurchases(): SubscriptionPurchaseResult {
        delay(1000) // 模擬延遲
        return SubscriptionPurchaseResult.MaintenanceMode("訂閱恢復功能遷移到 KMP 架構中")
    }
    
    /**
     * 初始化 - 簡化實現
     */
    suspend fun initialize(): Boolean {
        delay(500) // 模擬延遲
        return true
    }
    
    /**
     * 清理 - 簡化實現
     */
    fun dispose() {
        // TODO: 使用 sharedKmp 的訂閱服務清理
    }
}

/**
 * 簡化版訂閱購買結果
 */
sealed class SubscriptionPurchaseResult {
    object Success : SubscriptionPurchaseResult()
    data class Error(val message: String) : SubscriptionPurchaseResult()
    data class MaintenanceMode(val message: String) : SubscriptionPurchaseResult()
}
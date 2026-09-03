package com.cbstudio.wearwallet.analytics

import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 訂閱功能專用的分析追蹤
 */
class SubscriptionAnalytics constructor(
    private val analyticsManager: AnalyticsManager
) {
    
    /**
     * 追蹤訂閱轉換漏斗
     */
    fun trackSubscriptionFunnel(step: FunnelStep) {
        when (step) {
            FunnelStep.VIEWED_PRICING -> {
                analyticsManager.logScreenView("subscription_pricing")
            }
            FunnelStep.STARTED_PURCHASE -> {
                analyticsManager.logSubscriptionEvent(
                    "subscription_purchase_started",
                    "premium",
                    0
                )
            }
            FunnelStep.COMPLETED_PURCHASE -> {
                analyticsManager.logSubscriptionEvent(
                    AnalyticsManager.Events.SUBSCRIPTION_STARTED,
                    "premium",
                    0
                )
            }
            FunnelStep.CANCELLED_PURCHASE -> {
                analyticsManager.logSubscriptionEvent(
                    "subscription_purchase_cancelled",
                    "premium",
                    0
                )
            }
        }
    }
    
    /**
     * 追蹤錢包限制觸發
     */
    fun trackWalletLimitReached(currentWalletCount: Int) {
        analyticsManager.logSubscriptionEvent(
            AnalyticsManager.Events.WALLET_LIMIT_REACHED,
            "free",
            currentWalletCount
        )
    }
    
    /**
     * 追蹤訂閱取消
     */
    fun trackSubscriptionCancelled(reason: String) {
        analyticsManager.logSubscriptionEvent(
            AnalyticsManager.Events.SUBSCRIPTION_CANCELLED,
            "premium",
            0
        )
        analyticsManager.log("Subscription cancelled: $reason")
    }
    
    /**
     * 追蹤升級提示展示
     */
    fun trackUpgradePromptShown(context: String) {
        analyticsManager.logScreenView("upgrade_prompt")
        analyticsManager.log("Upgrade prompt shown in context: $context")
    }
    
    enum class FunnelStep {
        VIEWED_PRICING,
        STARTED_PURCHASE,
        COMPLETED_PURCHASE,
        CANCELLED_PURCHASE
    }
}

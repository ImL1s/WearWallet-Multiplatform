package com.cbstudio.wearwallet.domain.usecase.wallet

import com.cbstudio.wearwallet.domain.service.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import javax.inject.Singleton

/**
 * 錢包新增權限檢查 Use Case
 * 整合訂閱系統與錢包管理功能
 */
@Singleton
class CheckWalletLimitUseCase(
    private val subscriptionService: SubscriptionService
) {
    
    /**
     * 檢查用戶是否可以新增錢包
     * @param userId 用戶 ID
     * @return WalletPermission 包含權限檢查結果和相關資訊
     */
    suspend operator fun invoke(userId: String): WalletPermission {
        return try {
            // 初始化訂閱服務（如果尚未初始化）
            subscriptionService.initialize(userId)
            
            // 檢查錢包限制
            val walletLimit = subscriptionService.checkWalletPermission()
            
            // 檢查訂閱狀態
            val subscriptionStatus = subscriptionService.checkSubscriptionStatus()
            
            WalletPermission(
                canAddWallet = walletLimit.canAddWallet,
                currentWallets = walletLimit.currentWallets,
                maxWallets = walletLimit.maxWallets,
                remainingWallets = if (walletLimit.maxWallets == -1) Int.MAX_VALUE else (walletLimit.maxWallets - walletLimit.currentWallets).coerceAtLeast(0),
                isAtLimit = !walletLimit.canAddWallet,
                subscriptionTier = subscriptionStatus.tier,
                isSubscriptionActive = subscriptionStatus.isActive,
                subscriptionStatus = subscriptionStatus,
                limitReason = when {
                    walletLimit.canAddWallet -> null
                    !subscriptionStatus.isActive && !walletLimit.canAddWallet -> 
                        WalletLimitReason.FREE_USER_LIMIT_REACHED
                    !subscriptionStatus.isActive -> 
                        WalletLimitReason.SUBSCRIPTION_EXPIRED
                    else -> WalletLimitReason.UNKNOWN_LIMIT
                }
            )
        } catch (e: Exception) {
            // 發生錯誤時，返回最嚴格的限制
            WalletPermission(
                canAddWallet = false,
                currentWallets = 0,
                maxWallets = 2, // 免費用戶限制
                remainingWallets = 0,
                isAtLimit = true,
                subscriptionTier = SubscriptionTier.FREE,
                isSubscriptionActive = false,
                subscriptionStatus = null,
                limitReason = WalletLimitReason.ERROR_OCCURRED,
                error = e.message
            )
        }
    }
    
    /**
     * 取得升級提示訊息
     */
    fun getUpgradeMessage(permission: WalletPermission): String? {
        return when (permission.limitReason) {
            WalletLimitReason.FREE_USER_LIMIT_REACHED -> 
                "免費用戶最多可創建 ${permission.maxWallets} 個錢包。升級到高級版享受無限錢包管理。"
            WalletLimitReason.SUBSCRIPTION_EXPIRED -> 
                "您的訂閱已過期，已恢復到免費用戶限制。請續訂以繼續享受無限錢包功能。"
            WalletLimitReason.ERROR_OCCURRED -> 
                "檢查錢包限制時發生錯誤，請稍後再試。"
            else -> null
        }
    }
}

/**
 * 錢包權限檢查結果
 */
data class WalletPermission(
    val canAddWallet: Boolean,
    val currentWallets: Int,
    val maxWallets: Int,
    val remainingWallets: Int,
    val isAtLimit: Boolean,
    val subscriptionTier: SubscriptionTier,
    val isSubscriptionActive: Boolean,
    val subscriptionStatus: SubscriptionStatusInfo?,
    val limitReason: WalletLimitReason?,
    val error: String? = null
) {
    /**
     * 是否為付費用戶
     */
    val isPremiumUser: Boolean
        get() = subscriptionTier == SubscriptionTier.PREMIUM && isSubscriptionActive
    
    /**
     * 是否需要顯示升級提示
     */
    val shouldShowUpgradePrompt: Boolean
        get() = !canAddWallet && limitReason != null
}

/**
 * 錢包限制原因
 */
enum class WalletLimitReason {
    FREE_USER_LIMIT_REACHED,    // 免費用戶達到上限
    SUBSCRIPTION_EXPIRED,       // 訂閱已過期
    ERROR_OCCURRED,            // 發生錯誤
    UNKNOWN_LIMIT              // 未知限制
}

package com.cbstudio.wearwallet.domain.model

/**
 * 用戶個人資料模型
 */
data class UserProfile(
    val displayName: String = "Wallet User",
    val walletCount: Int = 0,
    val subscriptionStatus: String = "Free",
    val joinDate: String = ""
)

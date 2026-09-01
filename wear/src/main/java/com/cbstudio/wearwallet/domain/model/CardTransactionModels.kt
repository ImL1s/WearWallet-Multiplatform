package com.cbstudio.wearwallet.domain.model

import java.math.BigDecimal
import java.time.Instant

/**
 * 卡片交易記錄
 */
data class CardTransaction(
    val id: String,
    val cardId: String,
    val type: CardTransactionType,
    val amount: BigDecimal,
    val currency: String,
    val cryptoAmount: BigDecimal? = null,
    val cryptoCurrency: String? = null,
    val merchant: MerchantInfo,
    val merchantCategory: String? = null,
    val status: CardTransactionStatus,
    val timestamp: Instant,
    val exchangeRate: BigDecimal? = null,
    val fees: BigDecimal = BigDecimal.ZERO,
    val description: String? = null,
    val location: TransactionLocation? = null,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * 交易類型
 */
enum class CardTransactionType {
    PURCHASE,           // 購買
    WITHDRAWAL,         // 提款
    REFUND,            // 退款
    FEE,               // 手續費
    CASHBACK,          // 返現
    DEPOSIT,           // 存款
    TRANSFER,          // 轉賬
    REVERSAL,          // 撤銷
    AUTHORIZATION,     // 授權
    SETTLEMENT,        // 結算
    LOAD               // 充值
}

/**
 * 交易狀態
 */
enum class TransactionStatus {
    PENDING,           // 待處理
    AUTHORIZED,        // 已授權
    COMPLETED,         // 已完成
    FAILED,            // 失敗
    REVERSED,          // 已撤銷
    CANCELLED,         // 已取消
    EXPIRED            // 已過期
}

/**
 * 交易位置
 */
data class TransactionLocation(
    val country: String? = null,
    val city: String? = null,
    val merchantAddress: String? = null,
    val isOnline: Boolean = true
)

/**
 * 創建卡片請求
 */
data class CreateCardRequest(
    val walletAddress: String,
    val cardType: CardType,
    val provider: CardProvider,
    val currency: String = "USD",
    val initialDeposit: BigDecimal? = null,
    val spendingLimits: SpendingLimits? = null,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * 消費限額
 */
data class SpendingLimits(
    val daily: BigDecimal,
    val monthly: BigDecimal,
    val perTransaction: BigDecimal,
    val atmDaily: BigDecimal? = null,
    val atmMonthly: BigDecimal? = null
)

/**
 * 卡片餘額
 */
data class CardBalance(
    val available: BigDecimal,
    val pending: BigDecimal,
    val currency: String,
    val lastUpdated: Instant = Instant.now()
)

/**
 * 卡片元數據
 */
data class CardMetadata(
    val issuedAt: Instant,
    val activatedAt: Instant? = null,
    val lastUsedAt: Instant? = null,
    val totalTransactions: Int = 0,
    val totalSpent: BigDecimal = BigDecimal.ZERO,
    val totalCashback: BigDecimal = BigDecimal.ZERO,
    val rewardsRate: BigDecimal = BigDecimal.ZERO
)

/**
 * 卡片限額更新請求
 */
data class UpdateLimitsRequest(
    val cardId: String,
    val newLimits: SpendingLimits
)

/**
 * 卡片狀態更新請求
 */
data class UpdateCardStatusRequest(
    val cardId: String,
    val newStatus: CardStatus,
    val reason: String? = null
)

/**
 * 卡片充值請求
 */
data class CardTopUpRequest(
    val cardId: String,
    val amount: BigDecimal,
    val cryptoCurrency: String,
    val cryptoAmount: BigDecimal,
    val walletAddress: String
)

/**
 * 交易篩選器
 */
data class TransactionFilter(
    val startDate: Instant? = null,
    val endDate: Instant? = null,
    val types: List<CardTransactionType>? = null,
    val statuses: List<CardTransactionStatus>? = null,
    val minAmount: BigDecimal? = null,
    val maxAmount: BigDecimal? = null,
    val merchant: String? = null
)

/**
 * 商家資訊
 */
data class MerchantInfo(
    val name: String,
    val category: String,
    val country: String,
    val mcc: String? = null,    // Merchant Category Code
    val address: String? = null,
    val logo: String? = null
)

/**
 * 卡片交易狀態
 */
enum class CardTransactionStatus {
    PENDING,           // 待處理
    AUTHORIZED,        // 已授權
    COMPLETED,         // 已完成
    FAILED,           // 失敗
    REVERSED,         // 已撤銷
    CANCELLED,        // 已取消
    EXPIRED           // 已過期
}

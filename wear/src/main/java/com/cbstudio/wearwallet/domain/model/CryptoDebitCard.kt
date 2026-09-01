package com.cbstudio.wearwallet.domain.model

import java.math.BigDecimal
import java.time.Instant
import java.time.YearMonth

/**
 * 加密貨幣借記卡
 * 
 * 注意：BigDecimal 在序列化時需要特殊處理
 * 如果需要序列化，請使用 String 類型或自定義序列化器
 */
data class CryptoDebitCard(
    val id: String = "",
    val cardNumber: String = "",
    val cardholderName: String = "",
    val expiryDate: YearMonth = YearMonth.now().plusYears(3),
    val cvv: String = "",
    val type: CardType = CardType.VIRTUAL,
    val status: CardStatus = CardStatus.PENDING,
    val provider: CardProvider = CardProvider.CRYPTO_COM,
    val linkedWalletAddress: String = "",
    val defaultCurrency: String = "USD",
    val spendingLimit: SpendingLimits? = null,
    val balance: CardBalance? = null,
    val metadata: CardMetadata? = null,
    val lastFourDigits: String = cardNumber.takeLast(4),
    val isNfcEnabled: Boolean = false,
    val rewards: CardRewards? = null
) {
    /**
     * 獲取遮罩後的卡號
     */
    fun getMaskedCardNumber(): String {
        return if (cardNumber.length >= 16) {
            "**** **** **** ${cardNumber.takeLast(4)}"
        } else {
            cardNumber
        }
    }
    
    /**
     * 檢查卡片是否可用
     */
    fun isUsable(): Boolean {
        return status == CardStatus.ACTIVE && 
               expiryDate.isAfter(YearMonth.now())
    }
    
    /**
     * 檢查是否接近過期（3個月內）
     */
    fun isExpiringSoon(): Boolean {
        val threeMonthsFromNow = YearMonth.now().plusMonths(3)
        return expiryDate.isBefore(threeMonthsFromNow) || 
               expiryDate.equals(threeMonthsFromNow)
    }
}

/**
 * 卡片狀態
 */
enum class CardStatus {
    ACTIVE,         // 啟用
    FROZEN,         // 凍結（可恢復）
    BLOCKED,        // 封鎖（不可恢復）
    EXPIRED,        // 過期
    PENDING,        // 待啟用
    CANCELLED       // 已取消
}

/**
 * 卡片類型
 */
enum class CardType {
    VIRTUAL,        // 虛擬卡
    PHYSICAL,       // 實體卡
    NFC_ENABLED     // NFC 卡片
}

/**
 * 卡片提供商
 */
enum class CardProvider {
    CRYPTO_COM,     // Crypto.com Visa
    BINANCE,        // Binance Card
    COINBASE,       // Coinbase Card
    WIREX,          // Wirex Card
    NEXO,           // Nexo Card
    BYBIT,          // Bybit Card
    REVOLUT         // Revolut Crypto
}

/**
 * 卡片獎勵計劃
 */
data class CardRewards(
    val type: RewardType,
    val rate: BigDecimal,
    val cap: BigDecimal? = null,
    val currency: String = "USD"
)

/**
 * 獎勵類型
 */
enum class RewardType {
    CASHBACK,       // 現金返還
    CRYPTO_BACK,    // 加密貨幣返還
    POINTS,         // 積分
    MILES           // 里程
}

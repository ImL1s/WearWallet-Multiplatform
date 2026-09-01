package com.cbstudio.wearwallet.domain.utils

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

/**
 * ULTRATHINK Agent 4: 數值類型轉換和 BigDecimal 處理工具
 * 
 * 提供安全、精確的數值轉換，用於修復所有編譯錯誤
 * 
 * @author ULTRATHINK Agent 4
 * @since 2025-08-09
 */

/**
 * Double → BigDecimal 安全轉換
 */
fun Double.toBigDecimalSafe(): BigDecimal = 
    if (this.isNaN() || this.isInfinite()) {
        BigDecimal.ZERO
    } else {
        BigDecimal.valueOf(this)
    }

/**
 * Float → BigDecimal 安全轉換
 */
fun Float.toBigDecimalSafe(): BigDecimal = 
    if (this.isNaN() || this.isInfinite()) {
        BigDecimal.ZERO
    } else {
        BigDecimal.valueOf(this.toDouble())
    }

/**
 * String → BigDecimal 安全轉換
 */
fun String.toBigDecimalSafe(): BigDecimal = 
    try {
        if (this.isBlank()) BigDecimal.ZERO else BigDecimal(this)
    } catch (e: NumberFormatException) {
        BigDecimal.ZERO
    }

/**
 * Number → BigDecimal 通用轉換
 */
fun Number.toBigDecimalSafe(): BigDecimal = when(this) {
    is BigDecimal -> this
    is Double -> this.toBigDecimalSafe()
    is Float -> this.toBigDecimalSafe()
    is Long -> BigDecimal.valueOf(this)
    is Int -> BigDecimal.valueOf(this.toLong())
    else -> BigDecimal.valueOf(this.toDouble())
}

/**
 * 數值驗證器
 */
object NumericValidator {
    
    /**
     * 驗證金額是否有效
     */
    fun validateAmount(amount: Any): BigDecimal {
        return when {
            amount is BigDecimal -> amount
            amount is String -> amount.toBigDecimalSafe()
            amount is Number -> amount.toBigDecimalSafe()
            else -> throw IllegalArgumentException("Invalid amount type: ${amount::class}")
        }
    }
    
    /**
     * 驗證價格是否有效
     */
    fun isValidPrice(price: Double): Boolean {
        return price >= 0.0 && !price.isNaN() && !price.isInfinite()
    }
    
    /**
     * 標準化價格
     */
    fun normalizePrice(price: Double): BigDecimal {
        return if (isValidPrice(price)) {
            price.toBigDecimalSafe()
        } else {
            BigDecimal.ZERO
        }
    }
    
    /**
     * 驗證百分比是否有效
     */
    fun isValidPercentage(percentage: Float): Boolean {
        return !percentage.isNaN() && !percentage.isInfinite()
    }
    
    /**
     * 標準化百分比
     */
    fun normalizePercentage(percentage: Float): Float {
        return if (isValidPercentage(percentage)) percentage else 0f
    }
}

/**
 * 金融計算工具
 */
object FinancialCalculator {
    
    /**
     * 計算投資組合價值
     */
    fun calculatePortfolioValue(
        balance: Double,
        price: Double
    ): BigDecimal {
        return balance.toBigDecimalSafe()
            .multiply(price.toBigDecimalSafe())
            .setScale(8, RoundingMode.HALF_UP)
    }
    
    /**
     * 匯率轉換
     */
    fun convertCurrency(
        amount: BigDecimal,
        rate: Double
    ): BigDecimal {
        return amount.multiply(rate.toBigDecimalSafe())
            .setScale(2, RoundingMode.HALF_UP)
    }
    
    /**
     * 計算價格變化百分比
     */
    fun calculatePriceChange(
        oldPrice: Double,
        newPrice: Double
    ): BigDecimal {
        if (oldPrice == 0.0) return BigDecimal.ZERO
        
        val change = (newPrice - oldPrice) / oldPrice * 100
        return change.toBigDecimalSafe()
            .setScale(2, RoundingMode.HALF_UP)
    }
    
    /**
     * 計算總變化百分比（加權平均）
     */
    fun calculateWeightedAverageChange(
        values: List<Double>,
        changes: List<Float>
    ): Float {
        if (values.isEmpty() || changes.isEmpty() || values.size != changes.size) {
            return 0f
        }
        
        val totalValue = values.sum()
        if (totalValue == 0.0) return 0f
        
        var weightedSum = 0.0
        for (i in values.indices) {
            val weight = values[i] / totalValue
            weightedSum += weight * changes[i]
        }
        
        return weightedSum.toFloat()
    }
}
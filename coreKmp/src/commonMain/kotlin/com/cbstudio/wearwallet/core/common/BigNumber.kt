package com.cbstudio.wearwallet.core.common

import com.ionspin.kotlin.bignum.decimal.BigDecimal as KmpBigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger as KmpBigInteger
import com.ionspin.kotlin.bignum.decimal.DecimalMode
import com.ionspin.kotlin.bignum.decimal.RoundingMode

/**
 * KMP 相容的大數類型別名和擴展函數
 * 使用 ionspin kotlin-bignum 庫實現跨平台支援
 */

// 類型別名，讓代碼更簡潔
typealias BigDecimal = KmpBigDecimal
typealias BigInteger = KmpBigInteger

// 常用常量
object BigNumber {
    val ZERO_DECIMAL = BigDecimal.ZERO
    val ONE_DECIMAL = BigDecimal.ONE
    val TEN_DECIMAL = BigDecimal.TEN
    
    val ZERO_INTEGER = BigInteger.ZERO
    val ONE_INTEGER = BigInteger.ONE
    val TEN_INTEGER = BigInteger.TEN
    
    // 預設的小數模式
    val DEFAULT_DECIMAL_MODE = DecimalMode(
        decimalPrecision = 18,
        roundingMode = RoundingMode.CEILING,
        scale = 8
    )
}

// 擴展函數，提供便利的操作

/**
 * 從字符串創建 BigDecimal
 */
fun String.toBigDecimalOrNull(): BigDecimal? {
    return try {
        BigDecimal.parseString(this)
    } catch (e: Exception) {
        null
    }
}

/**
 * 從字符串創建 BigDecimal，失敗時返回 0
 */
fun String.toBigDecimalOrZero(): BigDecimal {
    return toBigDecimalOrNull() ?: BigNumber.ZERO_DECIMAL
}

/**
 * 從字符串創建 BigInteger
 */
fun String.toBigIntegerOrNull(): BigInteger? {
    return try {
        BigInteger.parseString(this)
    } catch (e: Exception) {
        null
    }
}

/**
 * 從字符串創建 BigInteger，失敗時返回 0
 */
fun String.toBigIntegerOrZero(): BigInteger {
    return toBigIntegerOrNull() ?: BigNumber.ZERO_INTEGER
}

/**
 * 從 Double 創建 BigDecimal
 */
fun Double.toBigDecimal(): BigDecimal {
    return BigDecimal.fromDouble(this, BigNumber.DEFAULT_DECIMAL_MODE)
}

/**
 * 從 Long 創建 BigInteger
 */
fun Long.toBigInteger(): BigInteger {
    return BigInteger.fromLong(this)
}

/**
 * 從 Int 創建 BigInteger
 */
fun Int.toBigInteger(): BigInteger {
    return BigInteger.fromInt(this)
}

/**
 * BigDecimal 轉換為 Double
 */
fun BigDecimal.toDoubleOrZero(): Double {
    return try {
        this.doubleValue(false)
    } catch (e: Exception) {
        0.0
    }
}

/**
 * BigInteger 轉換為 Long
 */
fun BigInteger.toLongOrZero(): Long {
    return try {
        this.longValue(false)
    } catch (e: Exception) {
        0L
    }
}

/**
 * 計算 10 的 n 次方（用於小數位轉換）
 */
fun tenPower(n: Int): BigDecimal {
    return BigNumber.TEN_DECIMAL.pow(n.toLong())
}

/**
 * 計算 10 的 n 次方（整數版本）
 */
fun tenPowerInt(n: Int): BigInteger {
    return BigNumber.TEN_INTEGER.pow(n.toLong())
}

/**
 * 將金額轉換為最小單位（例如 ETH -> Wei）
 */
fun BigDecimal.toSmallestUnit(decimals: Int): BigInteger {
    val factor = tenPower(decimals)
    return (this * factor).toBigInteger()
}

/**
 * 從最小單位轉換為標準單位（例如 Wei -> ETH）
 */
fun BigInteger.fromSmallestUnit(decimals: Int): BigDecimal {
    val factor = tenPower(decimals)
    return BigDecimal.fromBigInteger(this) / factor
}

/**
 * 格式化為字符串，移除尾部的零
 */
fun BigDecimal.toPlainString(): String {
    val str = this.toStringExpanded()
    
    // 如果包含小數點，移除尾部的零
    if ("." in str) {
        return str.trimEnd('0').trimEnd('.')
    }
    return str
}

/**
 * 格式化為固定小數位的字符串
 */
fun BigDecimal.toFixedString(scale: Int): String {
    val mode = DecimalMode(
        decimalPrecision = 18,
        roundingMode = RoundingMode.CEILING,
        scale = scale.toLong()
    )
    return this.roundToDigitPositionAfterDecimalPoint(scale.toLong(), RoundingMode.CEILING).toStringExpanded()
}

/**
 * 比較運算符擴展
 */
operator fun BigDecimal.compareTo(other: Double): Int {
    return this.compareTo(other.toBigDecimal())
}

operator fun BigInteger.compareTo(other: Long): Int {
    return this.compareTo(other.toBigInteger())
}

operator fun BigInteger.compareTo(other: Int): Int {
    return this.compareTo(other.toBigInteger())
}

/**
 * 安全的除法運算，避免除零錯誤
 */
fun BigDecimal.safeDivide(divisor: BigDecimal): BigDecimal {
    return if (divisor == BigNumber.ZERO_DECIMAL) {
        BigNumber.ZERO_DECIMAL
    } else {
        this / divisor
    }
}

/**
 * 百分比計算
 */
fun BigDecimal.percentageOf(total: BigDecimal): BigDecimal {
    return if (total == BigNumber.ZERO_DECIMAL) {
        BigNumber.ZERO_DECIMAL
    } else {
        (this / total) * BigDecimal.fromInt(100)
    }
}
package com.cbstudio.wearwallet.domain.utils

import org.junit.Test
import org.junit.Assert.*
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * ULTRATHINK Agent 4: NumericUtils 單元測試
 * 
 * 驗證所有數值類型轉換的正確性和安全性
 */
class NumericUtilsTest {

    @Test
    fun `toBigDecimalSafe handles valid Double values correctly`() {
        assertEquals(BigDecimal("123.45"), 123.45.toBigDecimalSafe())
        assertEquals(BigDecimal("0.0"), 0.0.toBigDecimalSafe())
        assertEquals(BigDecimal("-42.5"), (-42.5).toBigDecimalSafe())
    }

    @Test
    fun `toBigDecimalSafe handles special Double values safely`() {
        assertEquals(BigDecimal.ZERO, Double.NaN.toBigDecimalSafe())
        assertEquals(BigDecimal.ZERO, Double.POSITIVE_INFINITY.toBigDecimalSafe())
        assertEquals(BigDecimal.ZERO, Double.NEGATIVE_INFINITY.toBigDecimalSafe())
    }

    @Test
    fun `toBigDecimalSafe handles valid Float values correctly`() {
        // Float 轉換為 Double 時會有精度差異，這是預期行為
        // 123.45f.toDouble() approx 123.44999694824219
        assertEquals(BigDecimal.valueOf(123.45f.toDouble()), 123.45f.toBigDecimalSafe())
        assertEquals(BigDecimal("0.0"), 0.0f.toBigDecimalSafe())
        assertEquals(BigDecimal.valueOf((-42.5f).toDouble()), (-42.5f).toBigDecimalSafe())
    }

// ... (skip unchanged) ...

    @Test
    fun `Number toBigDecimalSafe handles various number types`() {
        assertEquals(BigDecimal("123"), 123.toBigDecimalSafe())
        assertEquals(BigDecimal("123"), 123L.toBigDecimalSafe())
        assertEquals(BigDecimal("123.45"), 123.45.toBigDecimalSafe())
        // Float 會有精度差異
        assertEquals(BigDecimal.valueOf(123.45f.toDouble()), 123.45f.toBigDecimalSafe())
        
        val existingBigDecimal = BigDecimal("999.99")
        assertEquals(existingBigDecimal, existingBigDecimal.toBigDecimalSafe())
    }

// ... (skip unchanged) ...

    @Test
    fun `rounding behavior is consistent`() {
        val amount = BigDecimal("100.555")
        val rate = 1.999
        val result = FinancialCalculator.convertCurrency(amount, rate)
        
        // 100.555 * 1.999 = 201.009445
        // HALF_UP rounding to 2 decimals -> 201.01
        assertEquals("201.01", result.toPlainString())
    }
}
package io.github.iml1s.utxo

import kotlin.test.Test
import kotlin.test.assertEquals

class CpfpTest {

    @Test
    fun testCalculateChildFee() {
        // Parent: 200 vbytes, paid 200 sats (1 sat/vB) - Stuck
        // Child: 200 vbytes
        // Target: 10 sat/vB for package
        // Total weight = 400
        // Required Fee = 400 * 10 = 4000
        // Child Fee = 4000 - 200 = 3800
        
        val childFee = CpfpUtils.calculateChildFee(
            parentFee = 200,
            parentSize = 200,
            childSize = 200,
            targetFeeRate = 10
        )
        
        assertEquals(3800L, childFee)
    }

    @Test
    fun testPackageFeeRate() {
        val rate = CpfpUtils.calculatePackageFeeRate(
            parentFee = 200,
            parentSize = 200,
            childFee = 3800,
            childSize = 200
        )
        
        assertEquals(10.0, rate, 0.001)
    }
    
    @Test
    fun testChildFeeZeroIfParentPaysEnough() {
        // Parent already pays 20 sat/vB
        val childFee = CpfpUtils.calculateChildFee(
            parentFee = 4000,
            parentSize = 200,
            childSize = 200,
            targetFeeRate = 10
        )
        // Algorithm returns 0 (technically negative, but capped at 0)
        assertEquals(0L, childFee)
    }
}

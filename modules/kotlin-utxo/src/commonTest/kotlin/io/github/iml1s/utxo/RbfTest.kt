package io.github.iml1s.utxo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class RbfTest {

    @Test
    fun testMinReplacementFee() {
        val originalFee = 1000L
        val newTxSize = 250 // vbytes
        // Rule 4: Pay for own bandwidth at min relay fee (default 1 sat/vB)
        // Delta = 250 * 1 = 250 sats
        // Min Fee = 1000 + 250 = 1250
        
        val minFee = RbfUtils.calculateMinReplacementFee(originalFee, newTxSize)
        assertEquals(1250L, minFee)
    }

    @Test
    fun testIsFeeSufficient() {
        val originalFee = 1000L
        val newTxSize = 250
        
        // 1249 is not enough (need 1250)
        assertFalse(RbfUtils.isFeeSufficientForReplacement(1249L, originalFee, newTxSize))
        
        // 1250 is enough
        assertTrue(RbfUtils.isFeeSufficientForReplacement(1250L, originalFee, newTxSize))
        
        // 1500 is enough
        assertTrue(RbfUtils.isFeeSufficientForReplacement(1500L, originalFee, newTxSize))
    }
}

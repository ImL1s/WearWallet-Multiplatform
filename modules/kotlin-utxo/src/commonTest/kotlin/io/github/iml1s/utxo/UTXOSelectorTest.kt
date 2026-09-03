package io.github.iml1s.utxo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class UTXOSelectorTest {

    private val selector = UTXOSelector()

    @Test
    fun testSelectLargestFirst() {
        val utxos = listOf(
            UTXO(txid = "tx1", vout = 0, value = 10_000, confirmed = true),
            UTXO(txid = "tx2", vout = 0, value = 50_000, confirmed = true),
            UTXO(txid = "tx3", vout = 0, value = 30_000, confirmed = true)
        )

        val selection = selector.select(
            utxos = utxos,
            targetAmount = 40_000,
            feeRate = 10,
            strategy = UTXOSelectionStrategy.LARGEST_FIRST
        )

        // Should select the 50,000 UTXO first
        assertTrue(selection.selectedUTXOs.any { it.txid == "tx2" })
        assertTrue(selection.totalValue >= 40_000 + selection.estimatedFee)
    }

    @Test
    fun testSelectSmallestFirst() {
        val utxos = listOf(
            UTXO(txid = "tx1", vout = 0, value = 10_000, confirmed = true),
            UTXO(txid = "tx2", vout = 0, value = 50_000, confirmed = true),
            UTXO(txid = "tx3", vout = 0, value = 30_000, confirmed = true)
        )

        val selection = selector.select(
            utxos = utxos,
            targetAmount = 35_000,
            feeRate = 10,
            strategy = UTXOSelectionStrategy.SMALLEST_FIRST
        )

        // Should include the smallest UTXO (10,000)
        assertTrue(selection.selectedUTXOs.any { it.txid == "tx1" })
    }

    @Test
    fun testInsufficientFunds() {
        val utxos = listOf(
            UTXO(txid = "tx1", vout = 0, value = 10_000, confirmed = true)
        )

        assertFailsWith<InsufficientFundsException> {
            selector.select(
                utxos = utxos,
                targetAmount = 100_000,
                feeRate = 10
            )
        }
    }

    @Test
    fun testNoEligibleUTXOs() {
        val utxos = listOf(
            UTXO(txid = "tx1", vout = 0, value = 10_000, confirmed = false) // Unconfirmed
        )

        assertFailsWith<InsufficientFundsException> {
            selector.select(
                utxos = utxos,
                targetAmount = 5_000,
                feeRate = 10
            )
        }
    }

    @Test
    fun testDustUTXOsFiltered() {
        val utxos = listOf(
            UTXO(txid = "tx1", vout = 0, value = 100, confirmed = true), // Dust
            UTXO(txid = "tx2", vout = 0, value = 50_000, confirmed = true)
        )

        val selection = selector.select(
            utxos = utxos,
            targetAmount = 10_000,
            feeRate = 10
        )

        // Should not include the dust UTXO
        assertFalse(selection.selectedUTXOs.any { it.txid == "tx1" })
    }

    @Test
    fun testChangeCalculation() {
        val utxos = listOf(
            UTXO(txid = "tx1", vout = 0, value = 100_000, confirmed = true)
        )

        val targetAmount = 50_000L
        val feeRate = 10L

        val selection = selector.select(
            utxos = utxos,
            targetAmount = targetAmount,
            feeRate = feeRate
        )

        // Verify change calculation is correct
        assertEquals(
            selection.totalValue,
            targetAmount + selection.change + selection.estimatedFee
        )
    }

    @Test
    fun testBranchAndBound() {
        val utxos = listOf(
            UTXO(txid = "tx1", vout = 0, value = 10_000, confirmed = true),
            UTXO(txid = "tx2", vout = 0, value = 20_000, confirmed = true),
            UTXO(txid = "tx3", vout = 0, value = 30_000, confirmed = true),
            UTXO(txid = "tx4", vout = 0, value = 40_000, confirmed = true)
        )

        val selection = selector.select(
            utxos = utxos,
            targetAmount = 25_000,
            feeRate = 5,
            strategy = UTXOSelectionStrategy.BRANCH_AND_BOUND
        )

        // Should find a valid selection
        assertTrue(selection.totalValue >= 25_000 + selection.estimatedFee)
        assertTrue(selection.isValid())
    }

    @Test
    fun testSelectOptimal() {
        val utxos = listOf(
            UTXO(txid = "tx1", vout = 0, value = 10_000, confirmed = true),
            UTXO(txid = "tx2", vout = 0, value = 25_000, confirmed = true),
            UTXO(txid = "tx3", vout = 0, value = 30_000, confirmed = true)
        )

        val selection = selector.selectOptimal(
            utxos = utxos,
            targetAmount = 20_000,
            feeRate = 10
        )

        // Should return a valid selection with the lowest fee
        assertTrue(selection.totalValue >= 20_000 + selection.estimatedFee)
    }

    @Test
    fun testFeeEstimation() {
        val fee = selector.estimateFee(
            inputCount = 2,
            outputCount = 2,
            feeRate = 10,
            scriptType = ScriptType.P2WPKH
        )

        // Expected: 10 + (2 * 68) + (2 * 31) = 208 bytes * 10 = 2080 satoshis
        assertTrue(fee > 0)
    }

    @Test
    fun testValidateSelection() {
        val utxos = listOf(
            UTXO(txid = "tx1", vout = 0, value = 100_000, confirmed = true)
        )

        val selection = selector.select(
            utxos = utxos,
            targetAmount = 50_000,
            feeRate = 10
        )

        assertTrue(selector.validateSelection(selection, 50_000, 10))
    }
}

class UTXOTest {

    @Test
    fun testOutpoint() {
        val utxo = UTXO(txid = "abc123", vout = 1, value = 10_000, confirmed = true)
        assertEquals("abc123:1", utxo.outpoint)
    }

    @Test
    fun testIsDust() {
        val dustUTXO = UTXO(txid = "tx1", vout = 0, value = 100, confirmed = true)
        val normalUTXO = UTXO(txid = "tx2", vout = 0, value = 10_000, confirmed = true)

        assertTrue(dustUTXO.isDust())
        assertFalse(normalUTXO.isDust())
    }

    @Test
    fun testIsSpendable() {
        val spendable = UTXO(txid = "tx1", vout = 0, value = 10_000, confirmed = true)
        val unconfirmed = UTXO(txid = "tx2", vout = 0, value = 10_000, confirmed = false)
        val dust = UTXO(txid = "tx3", vout = 0, value = 100, confirmed = true)

        assertTrue(spendable.isSpendable())
        assertFalse(unconfirmed.isSpendable())
        assertFalse(dust.isSpendable())
    }
}

class UTXOSelectionTest {

    @Test
    fun testInputCount() {
        val selection = UTXOSelection(
            selectedUTXOs = listOf(
                UTXO(txid = "tx1", vout = 0, value = 10_000, confirmed = true),
                UTXO(txid = "tx2", vout = 0, value = 20_000, confirmed = true)
            ),
            totalValue = 30_000,
            change = 5_000,
            estimatedFee = 1_000
        )

        assertEquals(2, selection.inputCount)
    }

    @Test
    fun testHasChange() {
        val withChange = UTXOSelection(
            selectedUTXOs = emptyList(),
            totalValue = 30_000,
            change = 5_000,
            estimatedFee = 1_000
        )

        val withoutChange = UTXOSelection(
            selectedUTXOs = emptyList(),
            totalValue = 30_000,
            change = 0,
            estimatedFee = 1_000
        )

        assertTrue(withChange.hasChange)
        assertFalse(withoutChange.hasChange)
    }

    @Test
    fun testIsValid() {
        val validSelection = UTXOSelection(
            selectedUTXOs = emptyList(),
            totalValue = 30_000,
            change = 5_000,
            estimatedFee = 1_000
        )

        assertTrue(validSelection.isValid())
        assertEquals(24_000, validSelection.sendAmount)
    }

    @Test
    fun testEmptySelection() {
        val empty = UTXOSelection.empty()
        assertEquals(0, empty.inputCount)
        assertEquals(0, empty.totalValue)
    }
}

class FeeEstimatorTest {

    @Test
    fun testEstimateVirtualSize() {
        val inputs = listOf(ScriptType.P2WPKH, ScriptType.P2WPKH)
        val outputs = listOf(ScriptType.P2WPKH, ScriptType.P2WPKH)

        val vsize = FeeEstimator.estimateVirtualSize(inputs, outputs, isSegWit = true)

        // Should return a positive value
        assertTrue(vsize > 0)
    }

    @Test
    fun testEstimateFee() {
        val inputs = listOf(ScriptType.P2WPKH)
        val outputs = listOf(ScriptType.P2WPKH, ScriptType.P2WPKH)

        val fee = FeeEstimator.estimateFee(inputs, outputs, feeRate = 10)

        assertTrue(fee > 0)
    }

    @Test
    fun testEstimateFeeSimple() {
        val fee = FeeEstimator.estimateFeeSimple(
            inputCount = 2,
            outputCount = 2,
            feeRate = 10
        )

        assertTrue(fee > 0)
    }
}

class FeePriorityTest {

    @Test
    fun testTargetBlocks() {
        assertEquals(1, FeePriority.URGENT.targetBlocks)
        assertEquals(6, FeePriority.MEDIUM.targetBlocks)
        assertEquals(144, FeePriority.ECONOMY.targetBlocks)
    }

    @Test
    fun testSuggestPriority() {
        assertEquals(FeePriority.URGENT, FeePriority.suggest(1_000_000, isUrgent = true))
        assertEquals(FeePriority.HIGH, FeePriority.suggest(50_000_000)) // > 0.1 BTC
        assertEquals(FeePriority.MEDIUM, FeePriority.suggest(5_000_000)) // > 0.01 BTC
        assertEquals(FeePriority.LOW, FeePriority.suggest(100_000)) // Small amount
    }
}

class FeeRatesTest {

    @Test
    fun testForPriority() {
        val rates = FeeRates(
            economy = 1,
            low = 2,
            medium = 5,
            high = 10,
            urgent = 20
        )

        assertEquals(1L, rates.forPriority(FeePriority.ECONOMY))
        assertEquals(20L, rates.forPriority(FeePriority.URGENT))
    }

    @Test
    fun testDefaultRates() {
        val defaultRates = FeeRates.DEFAULT
        assertTrue(defaultRates.economy > 0)
        assertTrue(defaultRates.urgent >= defaultRates.economy)
    }
}

package io.github.iml1s.utxo

/**
 * Represents the result of a UTXO selection algorithm.
 *
 * When constructing a Bitcoin transaction, you need to select which UTXOs to use as inputs.
 * This class contains the selected UTXOs and calculated values like change and estimated fees.
 *
 * @property selectedUTXOs The list of UTXOs selected for the transaction
 * @property totalValue The total value of all selected UTXOs in satoshis
 * @property change The change amount to be returned (totalValue - targetAmount - fee)
 * @property estimatedFee The estimated transaction fee in satoshis
 */
data class UTXOSelection(
    val selectedUTXOs: List<UTXO>,
    val totalValue: Long,
    val change: Long,
    val estimatedFee: Long
) {
    /**
     * The number of selected UTXOs (transaction inputs)
     */
    val inputCount: Int
        get() = selectedUTXOs.size

    /**
     * Whether this selection includes change output
     */
    val hasChange: Boolean
        get() = change > 0

    /**
     * The effective value after subtracting fees
     * This is the maximum amount that can be sent using this selection
     */
    val effectiveValue: Long
        get() = totalValue - estimatedFee

    /**
     * The amount being sent (totalValue - change - fee)
     */
    val sendAmount: Long
        get() = totalValue - change - estimatedFee

    /**
     * Validates that the selection is mathematically correct
     *
     * @return true if totalValue equals sendAmount + change + estimatedFee
     */
    fun isValid(): Boolean {
        return totalValue == sendAmount + change + estimatedFee
    }

    /**
     * Gets the fee rate based on estimated transaction size
     *
     * @param scriptType The script type for input size estimation
     * @return Fee rate in satoshis per virtual byte
     */
    fun effectiveFeeRate(scriptType: ScriptType = ScriptType.P2WPKH): Double {
        val estimatedSize = estimateTransactionSize(scriptType)
        return if (estimatedSize > 0) {
            estimatedFee.toDouble() / estimatedSize
        } else {
            0.0
        }
    }

    /**
     * Estimates the transaction size based on the number of inputs and outputs
     *
     * @param scriptType The script type for input size estimation
     * @return Estimated size in virtual bytes
     */
    fun estimateTransactionSize(scriptType: ScriptType = ScriptType.P2WPKH): Int {
        val outputCount = if (hasChange) 2 else 1
        val inputSize = when (scriptType) {
            ScriptType.P2PKH -> 148
            ScriptType.P2SH -> 91
            ScriptType.P2WPKH -> 68
            ScriptType.P2WSH -> 104
            ScriptType.P2TR -> 57
            ScriptType.UNKNOWN -> 148
        }
        val outputSize = 34 // Standard output size

        // Base transaction overhead (version, locktime, etc.)
        val overhead = 10

        return overhead + (inputCount * inputSize) + (outputCount * outputSize)
    }

    companion object {
        /**
         * Creates an empty selection (no UTXOs selected)
         */
        fun empty(): UTXOSelection = UTXOSelection(
            selectedUTXOs = emptyList(),
            totalValue = 0,
            change = 0,
            estimatedFee = 0
        )
    }
}

/**
 * Represents detailed information about a UTXO selection with additional metadata
 */
data class UTXOSelectionResult(
    val selection: UTXOSelection,
    val strategy: UTXOSelectionStrategy,
    val unusedUTXOs: List<UTXO>,
    val processingTimeMs: Long = 0
) {
    /**
     * The efficiency of the selection (1.0 means no waste, lower means more waste)
     */
    val efficiency: Double
        get() {
            val targetPlusFee = selection.sendAmount + selection.estimatedFee
            return if (selection.totalValue > 0) {
                targetPlusFee.toDouble() / selection.totalValue
            } else {
                0.0
            }
        }
}

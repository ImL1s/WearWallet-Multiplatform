package io.github.iml1s.utxo

/**
 * Validates RBF (Replace-By-Fee) replacements according to BIP125 rules.
 */
object RbfUtils {

    /**
     * Minimum Relay Fee (1 sat/vbyte). In practice, nodes may have higher limits (e.g. 5 sat/vB).
     */
    const val DEFAULT_MIN_RELAY_FEE = 1L

    /**
     * Calculates the minimum fee required for a replacement transaction.
     * 
     * BIP125 Rule 3: The replacement transaction must pay an absolute fee of at least the sum paid by the original transactions.
     * BIP125 Rule 4: The replacement transaction must also pay for its own bandwidth at or above the rate set by the node's minimum relay fee setting.
     * 
     * @param originalFee The fee paid by the original transaction(s) being replaced (in satoshis).
     * @param newTxSize The virtual size of the new (replacement) transaction (in vbytes).
     * @param minRelayFeeRate The minimum relay fee rate in sat/vbyte (default 1).
     * @return The absolute minimum fee (in satoshis) the new transaction must pay.
     */
    fun calculateMinReplacementFee(
        originalFee: Long,
        newTxSize: Int,
        minRelayFeeRate: Long = DEFAULT_MIN_RELAY_FEE
    ): Long {
        val bandwidthFee = newTxSize * minRelayFeeRate
        // Must pay at least original fee + bandwidth cost for new tx
        return originalFee + bandwidthFee
    }

    /**
     * Checks if a user-proposed fee is sufficient for RBF replacement.
     */
    fun isFeeSufficientForReplacement(
        newFee: Long,
        originalFee: Long,
        newTxSize: Int,
        minRelayFeeRate: Long = DEFAULT_MIN_RELAY_FEE
    ): Boolean {
        val minRequired = calculateMinReplacementFee(originalFee, newTxSize, minRelayFeeRate)
        return newFee >= minRequired
    }
}

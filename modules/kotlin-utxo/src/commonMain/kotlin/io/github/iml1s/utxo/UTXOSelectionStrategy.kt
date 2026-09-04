package io.github.iml1s.utxo

/**
 * UTXO selection strategies determine how UTXOs are selected for a transaction.
 *
 * Different strategies optimize for different goals:
 * - Minimizing fees
 * - Minimizing the number of inputs
 * - Maximizing privacy
 * - Consolidating dust UTXOs
 */
enum class UTXOSelectionStrategy {
    /**
     * Selects the largest UTXOs first to minimize the number of inputs.
     *
     * Pros:
     * - Minimizes transaction size (fewer inputs = lower fees)
     * - Simple and fast algorithm
     *
     * Cons:
     * - May create large change outputs
     * - Can lead to UTXO set fragmentation over time
     *
     * Best used when: Fee minimization is the priority
     */
    LARGEST_FIRST,

    /**
     * Selects the smallest UTXOs first.
     *
     * Pros:
     * - Consolidates small UTXOs (dust collection)
     * - Reduces UTXO set size over time
     *
     * Cons:
     * - Higher transaction fees (more inputs)
     * - Slower for large UTXO sets
     *
     * Best used when: Consolidating many small UTXOs or when fees are low
     */
    SMALLEST_FIRST,

    /**
     * Uses Branch and Bound algorithm to find an exact match or near-optimal selection.
     *
     * Pros:
     * - Can find exact matches (no change output needed)
     * - Minimizes waste (unused value)
     *
     * Cons:
     * - More computationally intensive
     * - May take longer for large UTXO sets
     *
     * Best used when: Looking for optimal selection and have time for computation
     */
    BRANCH_AND_BOUND,

    /**
     * First In, First Out - selects oldest UTXOs first.
     *
     * Pros:
     * - Good for coin age/staking scenarios
     * - Predictable behavior
     *
     * Cons:
     * - May not optimize for fees
     *
     * Best used when: Coin age matters or for predictable behavior
     */
    FIFO,

    /**
     * Randomly selects UTXOs until the target is reached.
     *
     * Pros:
     * - Better privacy (less predictable)
     * - Good entropy
     *
     * Cons:
     * - Non-deterministic results
     * - May not optimize for fees
     *
     * Best used when: Privacy is a priority
     */
    RANDOM,

    /**
     * Automatically selects the best strategy based on the situation.
     *
     * The optimal strategy considers:
     * - Number of available UTXOs
     * - Target amount relative to total available
     * - Fee rate
     * - UTXO value distribution
     *
     * This is the recommended default strategy.
     */
    OPTIMAL;

    companion object {
        /**
         * Returns the default recommended strategy
         */
        fun default(): UTXOSelectionStrategy = OPTIMAL

        /**
         * Suggests a strategy based on the given parameters
         *
         * @param utxoCount Number of available UTXOs
         * @param targetAmount Target amount to send
         * @param totalAvailable Total available value
         * @param feeRate Current fee rate in sat/vB
         * @return Recommended strategy
         */
        fun suggest(
            utxoCount: Int,
            targetAmount: Long,
            totalAvailable: Long,
            feeRate: Long
        ): UTXOSelectionStrategy {
            // If there are very few UTXOs, just use largest first
            if (utxoCount <= 3) {
                return LARGEST_FIRST
            }

            // If target is close to total available, use largest first to minimize inputs
            val ratio = targetAmount.toDouble() / totalAvailable
            if (ratio > 0.8) {
                return LARGEST_FIRST
            }

            // If fees are high, try branch and bound for optimal selection
            if (feeRate > 50) {
                return BRANCH_AND_BOUND
            }

            // If fees are low, might be a good time to consolidate
            if (feeRate < 5 && utxoCount > 20) {
                return SMALLEST_FIRST
            }

            // For moderate cases, use branch and bound
            return BRANCH_AND_BOUND
        }
    }
}

/**
 * Configuration options for UTXO selection
 */
data class UTXOSelectionConfig(
    /**
     * The selection strategy to use
     */
    val strategy: UTXOSelectionStrategy = UTXOSelectionStrategy.OPTIMAL,

    /**
     * Minimum dust threshold in satoshis
     */
    val dustThreshold: Long = UTXO.DEFAULT_DUST_THRESHOLD,

    /**
     * Whether to include unconfirmed UTXOs
     */
    val includeUnconfirmed: Boolean = false,

    /**
     * Maximum number of inputs to use in a single transaction
     */
    val maxInputs: Int = 100,

    /**
     * Target number of confirmations for UTXOs to be considered safe
     */
    val minConfirmations: Int = 1,

    /**
     * Whether to add extra change output for privacy
     * (splitting change into multiple outputs)
     */
    val enablePrivacyMode: Boolean = false,

    /**
     * Maximum tries for branch and bound algorithm
     */
    val maxBranchAndBoundTries: Int = 100_000
) {
    companion object {
        /**
         * Default configuration
         */
        fun default(): UTXOSelectionConfig = UTXOSelectionConfig()

        /**
         * Configuration optimized for low fees
         */
        fun lowFee(): UTXOSelectionConfig = UTXOSelectionConfig(
            strategy = UTXOSelectionStrategy.LARGEST_FIRST,
            maxInputs = 10
        )

        /**
         * Configuration for consolidating small UTXOs
         */
        fun consolidate(): UTXOSelectionConfig = UTXOSelectionConfig(
            strategy = UTXOSelectionStrategy.SMALLEST_FIRST,
            maxInputs = 500,
            includeUnconfirmed = false
        )

        /**
         * Configuration optimized for privacy
         */
        fun privacy(): UTXOSelectionConfig = UTXOSelectionConfig(
            strategy = UTXOSelectionStrategy.RANDOM,
            enablePrivacyMode = true
        )
    }
}

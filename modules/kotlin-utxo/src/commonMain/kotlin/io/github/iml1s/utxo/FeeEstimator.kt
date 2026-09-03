package io.github.iml1s.utxo

/**
 * Fee estimation utilities for UTXO-based transactions.
 *
 * This class provides methods to estimate transaction fees based on
 * transaction structure (inputs, outputs) and current fee rates.
 */
object FeeEstimator {

    // Transaction structure sizes in virtual bytes
    private const val VERSION_SIZE = 4
    private const val LOCKTIME_SIZE = 4
    private const val INPUT_COUNT_SIZE = 1 // VarInt, typically 1 byte
    private const val OUTPUT_COUNT_SIZE = 1 // VarInt, typically 1 byte
    private const val SEGWIT_MARKER_FLAG = 2

    /**
     * Estimates the virtual size of a transaction.
     *
     * For SegWit transactions, the virtual size is calculated as:
     * vsize = (weight + 3) / 4
     *
     * @param inputs List of inputs with their script types
     * @param outputs List of outputs with their script types
     * @param isSegWit Whether the transaction uses SegWit
     * @return Virtual size in vbytes
     */
    fun estimateVirtualSize(
        inputs: List<ScriptType>,
        outputs: List<ScriptType>,
        isSegWit: Boolean = true
    ): Int {
        val baseSize = calculateBaseSize(inputs, outputs)
        val witnessSize = if (isSegWit) calculateWitnessSize(inputs) else 0

        return if (isSegWit) {
            // Weight = base size * 4 + witness size
            // Virtual size = (weight + 3) / 4
            val weight = baseSize * 4 + witnessSize
            (weight + 3) / 4
        } else {
            baseSize
        }
    }

    /**
     * Calculates the base size of a transaction (non-witness data).
     */
    private fun calculateBaseSize(
        inputs: List<ScriptType>,
        outputs: List<ScriptType>
    ): Int {
        val overhead = VERSION_SIZE + LOCKTIME_SIZE + INPUT_COUNT_SIZE + OUTPUT_COUNT_SIZE
        val inputSize = inputs.sumOf { getInputBaseSize(it) }
        val outputSize = outputs.sumOf { getOutputSize(it) }
        return overhead + inputSize + outputSize
    }

    /**
     * Calculates the witness size for SegWit inputs.
     */
    private fun calculateWitnessSize(inputs: List<ScriptType>): Int {
        val markerFlag = SEGWIT_MARKER_FLAG
        val witnessData = inputs.sumOf { getInputWitnessSize(it) }
        return markerFlag + witnessData
    }

    /**
     * Gets the base size of an input (without witness data).
     */
    private fun getInputBaseSize(scriptType: ScriptType): Int {
        return when (scriptType) {
            ScriptType.P2PKH -> 148 // txid(32) + vout(4) + scriptSig(~107) + sequence(4)
            ScriptType.P2SH -> 91   // For P2SH-P2WPKH
            ScriptType.P2WPKH -> 41 // txid(32) + vout(4) + scriptSig(1 empty) + sequence(4)
            ScriptType.P2WSH -> 41
            ScriptType.P2TR -> 41
            ScriptType.UNKNOWN -> 148
        }
    }

    /**
     * Gets the witness size for a SegWit input.
     */
    private fun getInputWitnessSize(scriptType: ScriptType): Int {
        return when (scriptType) {
            ScriptType.P2WPKH -> 107 // signature(72) + pubkey(33) + stack items
            ScriptType.P2WSH -> 63   // Varies by script
            ScriptType.P2TR -> 65    // Schnorr signature
            ScriptType.P2SH -> 107   // P2SH-P2WPKH witness
            else -> 0
        }
    }

    /**
     * Gets the size of an output.
     */
    private fun getOutputSize(scriptType: ScriptType): Int {
        return when (scriptType) {
            ScriptType.P2PKH -> 34  // value(8) + scriptPubKey(26)
            ScriptType.P2SH -> 32   // value(8) + scriptPubKey(24)
            ScriptType.P2WPKH -> 31 // value(8) + scriptPubKey(23)
            ScriptType.P2WSH -> 43  // value(8) + scriptPubKey(35)
            ScriptType.P2TR -> 43   // value(8) + scriptPubKey(35)
            ScriptType.UNKNOWN -> 34
        }
    }

    /**
     * Estimates the fee for a transaction.
     *
     * @param inputs List of input script types
     * @param outputs List of output script types
     * @param feeRate Fee rate in satoshis per virtual byte
     * @param isSegWit Whether the transaction uses SegWit
     * @return Estimated fee in satoshis
     */
    fun estimateFee(
        inputs: List<ScriptType>,
        outputs: List<ScriptType>,
        feeRate: Long,
        isSegWit: Boolean = true
    ): Long {
        val vsize = estimateVirtualSize(inputs, outputs, isSegWit)
        return vsize.toLong() * feeRate
    }

    /**
     * Estimates the fee for a simple transaction with uniform input/output types.
     *
     * @param inputCount Number of inputs
     * @param outputCount Number of outputs
     * @param feeRate Fee rate in satoshis per virtual byte
     * @param scriptType Script type for all inputs and outputs
     * @return Estimated fee in satoshis
     */
    fun estimateFeeSimple(
        inputCount: Int,
        outputCount: Int,
        feeRate: Long,
        scriptType: ScriptType = ScriptType.P2WPKH
    ): Long {
        val inputs = List(inputCount) { scriptType }
        val outputs = List(outputCount) { scriptType }
        return estimateFee(inputs, outputs, feeRate)
    }
}

/**
 * Fee priority levels with typical confirmation targets.
 */
enum class FeePriority(
    val targetBlocks: Int,
    val description: String
) {
    /**
     * Economy - ~24 hours (144 blocks)
     */
    ECONOMY(144, "Economy (~24 hours)"),

    /**
     * Low - ~6 hours (36 blocks)
     */
    LOW(36, "Low (~6 hours)"),

    /**
     * Medium - ~1 hour (6 blocks)
     */
    MEDIUM(6, "Medium (~1 hour)"),

    /**
     * High - ~30 minutes (3 blocks)
     */
    HIGH(3, "High (~30 minutes)"),

    /**
     * Urgent - Next block
     */
    URGENT(1, "Urgent (next block)");

    companion object {
        /**
         * Suggests a fee priority based on transaction value and urgency.
         *
         * @param valueInSatoshis Transaction value
         * @param isUrgent Whether the transaction is time-sensitive
         * @return Recommended fee priority
         */
        fun suggest(valueInSatoshis: Long, isUrgent: Boolean = false): FeePriority {
            return when {
                isUrgent -> URGENT
                valueInSatoshis > 10_000_000 -> HIGH // > 0.1 BTC
                valueInSatoshis > 1_000_000 -> MEDIUM // > 0.01 BTC
                else -> LOW
            }
        }
    }
}

/**
 * Data class representing current fee rates for different priorities.
 */
data class FeeRates(
    val economy: Long,
    val low: Long,
    val medium: Long,
    val high: Long,
    val urgent: Long
) {
    /**
     * Gets the fee rate for a given priority.
     */
    fun forPriority(priority: FeePriority): Long {
        return when (priority) {
            FeePriority.ECONOMY -> economy
            FeePriority.LOW -> low
            FeePriority.MEDIUM -> medium
            FeePriority.HIGH -> high
            FeePriority.URGENT -> urgent
        }
    }

    companion object {
        /**
         * Default fee rates (fallback values in sat/vB).
         * These should be replaced with real-time data from a fee estimation API.
         */
        val DEFAULT = FeeRates(
            economy = 1,
            low = 2,
            medium = 5,
            high = 10,
            urgent = 20
        )
    }
}

package io.github.iml1s.utxo

/**
 * Utilities for Child-Pays-For-Parent (CPFP) fee estimation.
 */
object CpfpUtils {

    /**
     * Calculates the required fee for a child transaction to boost a parent transaction's effective fee rate.
     *
     * Formula:
     * Package Fee Rate = (Parent Fee + Child Fee) / (Parent Size + Child Size)
     * => Child Fee = (Target Rate * (Parent Size + Child Size)) - Parent Fee
     *
     * @param parentFee Fee paid by the parent transaction (satoshis)
     * @param parentSize Virtual size of the parent transaction (vbytes)
     * @param childSize Estimated virtual size of the child transaction (vbytes)
     * @param targetFeeRate User's desired effective fee rate for the package (sat/vbyte)
     * @return The fee the child transaction should pay (satoshis). Max ensures it's at least 0.
     */
    fun calculateChildFee(
        parentFee: Long,
        parentSize: Int,
        childSize: Int,
        targetFeeRate: Long
    ): Long {
        val totalWeight = parentSize + childSize
        val requiredTotalFee = totalWeight * targetFeeRate
        val childFee = requiredTotalFee - parentFee
        
        return if (childFee > 0) childFee else 0L
    }

    /**
     * Calculates the effective fee rate of a CPFP package.
     */
    fun calculatePackageFeeRate(
        parentFee: Long,
        parentSize: Int,
        childFee: Long,
        childSize: Int
    ): Double {
        val totalFee = parentFee + childFee
        val totalSize = parentSize + childSize
        if (totalSize == 0) return 0.0
        return totalFee.toDouble() / totalSize
    }
}

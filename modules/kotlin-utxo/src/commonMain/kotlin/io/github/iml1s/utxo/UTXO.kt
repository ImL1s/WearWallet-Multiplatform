package io.github.iml1s.utxo

/**
 * UTXO (Unspent Transaction Output) represents an unspent output from a previous transaction
 * that can be used as an input in a new transaction.
 *
 * In the Bitcoin and UTXO-based blockchain model, each transaction output that hasn't been
 * spent yet can be referenced by its transaction ID (txid) and output index (vout).
 *
 * @property txid The transaction ID (hash) of the transaction that created this output
 * @property vout The output index within the transaction (0-based)
 * @property value The amount in the smallest unit (satoshis for Bitcoin)
 * @property confirmed Whether this UTXO has been confirmed in a block
 * @property blockHeight The block height where this UTXO was confirmed (0 if unconfirmed)
 * @property scriptPubKey The locking script (scriptPubKey) in hex format
 * @property address The address that owns this UTXO
 */
data class UTXO(
    val txid: String,
    val vout: Int,
    val value: Long,
    val confirmed: Boolean,
    val blockHeight: Long = 0,
    val scriptPubKey: String? = null,
    val address: String? = null,
    
    // PSBT / RBF Metadata
    val redeemScript: String? = null,   // Hex string
    val witnessScript: String? = null,  // Hex string
    val derivationPath: String? = null, // e.g. m/84'/0'/0'/0/0
    val publicKey: String? = null,      // Hex string (needed for some PSBT flows)
    val isRbf: Boolean = false          // Whether the transaction creating this output signals RBF
) {
    /**
     * Returns the unique identifier for this UTXO (txid:vout format)
     */
    val outpoint: String
        get() = "$txid:$vout"

    /**
     * Checks if this UTXO is dust (below the minimum economically viable threshold)
     *
     * @param dustThreshold The minimum value to not be considered dust (default: 546 satoshis)
     * @return true if this UTXO is dust
     */
    fun isDust(dustThreshold: Long = DEFAULT_DUST_THRESHOLD): Boolean {
        return value < dustThreshold
    }

    /**
     * Checks if this UTXO can be safely spent in a transaction
     * A UTXO should be confirmed and not be dust
     *
     * @param dustThreshold The minimum value to not be considered dust
     * @return true if this UTXO is spendable
     */
    fun isSpendable(dustThreshold: Long = DEFAULT_DUST_THRESHOLD): Boolean {
        return confirmed && !isDust(dustThreshold)
    }

    companion object {
        /**
         * Default dust threshold in satoshis (546 satoshis for Bitcoin)
         */
        const val DEFAULT_DUST_THRESHOLD = 546L
    }
}

/**
 * Represents the script type of a UTXO
 */
enum class ScriptType {
    /**
     * Pay to Public Key Hash - Legacy addresses starting with 1 (Bitcoin)
     */
    P2PKH,

    /**
     * Pay to Script Hash - Addresses starting with 3 (Bitcoin)
     */
    P2SH,

    /**
     * Pay to Witness Public Key Hash - Native SegWit addresses starting with bc1q (Bitcoin)
     */
    P2WPKH,

    /**
     * Pay to Witness Script Hash - Native SegWit script addresses
     */
    P2WSH,

    /**
     * Pay to Taproot - Taproot addresses starting with bc1p (Bitcoin)
     */
    P2TR,

    /**
     * Unknown script type
     */
    UNKNOWN
}

/**
 * Extension function to get the estimated input size for a UTXO based on script type
 *
 * @param scriptType The type of script
 * @return Estimated size in bytes
 */
fun UTXO.estimatedInputSize(scriptType: ScriptType = ScriptType.P2WPKH): Int {
    return when (scriptType) {
        ScriptType.P2PKH -> 148  // Legacy input size
        ScriptType.P2SH -> 91   // P2SH-P2WPKH (wrapped SegWit)
        ScriptType.P2WPKH -> 68 // Native SegWit input size
        ScriptType.P2WSH -> 104 // Witness script hash
        ScriptType.P2TR -> 57   // Taproot input size
        ScriptType.UNKNOWN -> 148 // Default to legacy
    }
}

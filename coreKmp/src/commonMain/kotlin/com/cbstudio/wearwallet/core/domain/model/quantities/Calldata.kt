package com.cbstudio.wearwallet.core.domain.model.quantities

/**
 * Typed Calldata wrapper for EVM transaction payload.
 */
data class Calldata(val hexString: String) {
    init {
        val clean = if (hexString.startsWith("0x") || hexString.startsWith("0X")) hexString.substring(2) else hexString
        require(clean.length % 2 == 0) {
            "Calldata hex string length must be even, got ${clean.length} in '$hexString'"
        }
        require(clean.isEmpty() || clean.matches(Regex("^[0-9a-fA-F]*$"))) {
            "Invalid hex characters in Calldata: '$hexString'"
        }
    }

    fun toHex(): String {
        val clean = if (hexString.startsWith("0x") || hexString.startsWith("0X")) hexString.substring(2) else hexString
        return if (clean.isEmpty()) "" else "0x$clean"
    }

    fun toCleanHex(): String {
        return if (hexString.startsWith("0x") || hexString.startsWith("0X")) hexString.substring(2) else hexString
    }

    fun isEmpty(): Boolean = toCleanHex().isEmpty()

    companion object {
        val EMPTY = Calldata("")

        fun fromHex(hex: String): Calldata {
            val clean = hex.trim()
            return Calldata(clean)
        }
    }
}

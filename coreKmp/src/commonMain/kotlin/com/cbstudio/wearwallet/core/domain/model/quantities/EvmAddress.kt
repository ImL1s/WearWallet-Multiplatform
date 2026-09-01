package com.cbstudio.wearwallet.core.domain.model.quantities

/**
 * Typed EVM address wrapper (0x-prefixed 40 hex chars).
 */
data class EvmAddress(val value: String) {
    init {
        val clean = value.trim()
        require(clean.startsWith("0x") || clean.startsWith("0X")) {
            "EvmAddress must start with '0x', got '$value'"
        }
        require(clean.length == 42) {
            "EvmAddress length must be 42 characters including 0x prefix, got ${clean.length} in '$value'"
        }
        val hexPart = clean.substring(2)
        require(hexPart.matches(Regex("^[0-9a-fA-F]{40}$"))) {
            "Invalid hex characters in EvmAddress: '$value'"
        }
    }

    fun toLowercase(): String = value.lowercase()

    companion object {
        fun fromString(address: String): EvmAddress {
            val clean = address.trim()
            val formatted = if (clean.startsWith("0x") || clean.startsWith("0X")) clean else "0x$clean"
            return EvmAddress(formatted)
        }

        fun fromHex(hex: String): EvmAddress = fromString(hex)
    }
}

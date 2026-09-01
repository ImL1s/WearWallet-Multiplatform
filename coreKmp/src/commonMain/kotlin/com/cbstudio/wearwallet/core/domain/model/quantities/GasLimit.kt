package com.cbstudio.wearwallet.core.domain.model.quantities

data class GasLimit(val value: Long) {
    init {
        require(value >= 21000L) { "GasLimit must be at least 21000 (got $value)" }
    }

    fun toHex(): String = "0x" + value.toString(16)
    fun toLong(): Long = value

    companion object {
        fun fromLong(value: Long): GasLimit = GasLimit(value)

        fun fromHex(hex: String): GasLimit {
            val clean = hex.trim().removePrefix("0x").removePrefix("0X")
            val longVal = clean.toLong(16)
            return GasLimit(longVal)
        }

        fun fromDecimalString(str: String): GasLimit {
            val clean = str.trim()
            val longVal = if (clean.startsWith("0x") || clean.startsWith("0X")) {
                clean.substring(2).toLong(16)
            } else {
                clean.toLong(10)
            }
            return GasLimit(longVal)
        }
    }
}

package com.cbstudio.wearwallet.core.domain.model.quantities

data class ChainId(val value: Long) {
    init {
        require(value > 0L) { "ChainId must be positive (got $value)" }
    }

    fun toHex(): String = "0x" + value.toString(16)
    fun toLong(): Long = value

    companion object {
        fun fromLong(value: Long): ChainId = ChainId(value)
        fun fromDecimalString(str: String): ChainId = ChainId(str.trim().toLong(10))
    }
}

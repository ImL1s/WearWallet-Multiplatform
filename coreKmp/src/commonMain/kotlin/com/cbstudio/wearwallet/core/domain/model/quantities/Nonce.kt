package com.cbstudio.wearwallet.core.domain.model.quantities

data class Nonce(val value: Long) {
    init {
        require(value >= 0L) { "Nonce must be non-negative (got $value)" }
    }

    fun toHex(): String = "0x" + value.toString(16)
    fun toLong(): Long = value

    companion object {
        fun fromLong(value: Long): Nonce = Nonce(value)

        fun fromHex(hex: String): Nonce {
            val clean = hex.trim().removePrefix("0x").removePrefix("0X")
            require(clean.isNotEmpty()) { "Nonce hex string must not be empty" }
            return Nonce(clean.toLong(16))
        }
    }
}

package com.cbstudio.wearwallet.core.domain.model.quantities

import com.ionspin.kotlin.bignum.integer.BigInteger

/**
 * Typed Wei value for gas prices and native transfer amounts.
 *
 * Security constraints:
 * - Non-negative
 * - Explicit factory methods: fromWeiHex, fromWeiDecimal, fromGwei, fromGweiString
 * - No unit guessing based on magnitude
 * - Empty strings are rejected (not treated as zero)
 */
data class Wei(val value: BigInteger) {
    init {
        require(value >= BigInteger.ZERO) { "Wei value must be non-negative" }
    }

    fun toHex(): String {
        return "0x" + value.toString(16)
    }

    fun toBigInteger(): BigInteger = value

    fun toGweiString(): String {
        val gwei = value / BigInteger.fromLong(1_000_000_000L)
        return gwei.toString(10)
    }

    fun toEthString(): String {
        val ethDouble = value.toString(10).toDouble() / 1e18
        val rounded = kotlin.math.round(ethDouble * 1000000.0) / 1000000.0
        return rounded.toString()
    }

    companion object {
        val ZERO = Wei(BigInteger.ZERO)

        private val STRICT_DECIMAL_REGEX = Regex("^[0-9]+$")
        private val STRICT_HEX_REGEX = Regex("^[0-9a-fA-F]+$")
        private val STRICT_GWEI_DECIMAL_REGEX = Regex("^[0-9]+(\\.[0-9]+)?$")

        fun fromWei(value: BigInteger): Wei = Wei(value)

        /**
         * Parse Wei from a hex string (e.g., RPC response "0x3b9aca00").
         * Rejects empty strings and non-hex characters.
         */
        fun fromWeiHex(hex: String): Wei {
            val clean = hex.trim().removePrefix("0x").removePrefix("0X")
            require(clean.isNotEmpty()) { "Wei hex string must not be empty" }
            require(STRICT_HEX_REGEX.matches(clean)) { "Invalid hex format: '$clean'" }
            val bigInt = BigInteger.parseString(clean, 16)
            return Wei(bigInt)
        }

        /**
         * Parse Wei from a decimal string (e.g., "1000000000").
         * Rejects empty, hex prefix, scientific notation, leading +, negative.
         */
        fun fromWeiDecimal(decimalStr: String): Wei {
            val clean = decimalStr.trim()
            require(clean.isNotEmpty()) { "Wei decimal string must not be empty" }
            require(!clean.startsWith("0x") && !clean.startsWith("0X")) {
                "Hex prefix not accepted in Wei decimal API. Use fromWeiHex()."
            }
            require(!clean.startsWith("-")) { "Wei must be non-negative" }
            require(!clean.startsWith("+")) { "Leading '+' not accepted" }
            require(!clean.contains('e', ignoreCase = true)) { "Scientific notation not accepted" }
            require(STRICT_DECIMAL_REGEX.matches(clean)) {
                "Invalid Wei decimal format: '$clean'. Only digits accepted."
            }
            val bigInt = BigInteger.parseString(clean, 10)
            return Wei(bigInt)
        }

        fun fromGwei(gwei: Long): Wei {
            require(gwei >= 0) { "Gwei must be non-negative" }
            val weiBigInt = BigInteger.fromLong(gwei) * BigInteger.fromLong(1_000_000_000L)
            return Wei(weiBigInt)
        }

        /**
         * Parse Gwei from a decimal string (e.g., "5", "0.1", "20.5").
         * Rejects hex prefix (use fromWeiHex for hex values).
         * Fractional Gwei is supported up to 9 decimal places (nano-Wei precision).
         */
        fun fromGweiString(gweiStr: String): Wei {
            val clean = gweiStr.trim()
            require(clean.isNotEmpty()) { "Gwei string must not be empty" }
            require(!clean.startsWith("0x") && !clean.startsWith("0X")) {
                "Hex prefix not accepted in Gwei API. Use fromWeiHex() for hex Wei values."
            }
            require(!clean.startsWith("-")) { "Gwei must be non-negative" }
            require(!clean.startsWith("+")) { "Leading '+' not accepted" }
            require(!clean.contains('e', ignoreCase = true)) { "Scientific notation not accepted" }
            require(STRICT_GWEI_DECIMAL_REGEX.matches(clean)) {
                "Invalid Gwei format: '$clean'. Only digits and single decimal point accepted."
            }

            val parts = clean.split(".")
            val wholeGwei = BigInteger.parseString(parts[0].ifEmpty { "0" }, 10)
            var weiFromWhole = wholeGwei * BigInteger.fromLong(1_000_000_000L)
            if (parts.size > 1) {
                val fracPart = parts[1]
                require(fracPart.length <= 9) {
                    "Gwei fractional digits (${fracPart.length}) exceed 9 (nano-Wei precision)"
                }
                val frac = fracPart.padEnd(9, '0')
                val fracWei = BigInteger.parseString(frac, 10)
                weiFromWhole += fracWei
            }
            return Wei(weiFromWhole)
        }
    }
}

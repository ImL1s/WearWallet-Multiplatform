package com.cbstudio.wearwallet.core.domain.model.quantities

import com.ionspin.kotlin.bignum.integer.BigInteger

/**
 * Typed base-unit amount for token transfers.
 *
 * Security constraints:
 * - value must be non-negative
 * - tokenDecimals must be 0..77 (uint8 max=255, but ERC-20 practical limit ≤ 77)
 * - Human decimal amounts and raw base-unit amounts use different constructors
 */
data class BaseUnitAmount(
    val value: BigInteger,
    val tokenDecimals: Int
) {
    init {
        require(value >= BigInteger.ZERO) { "BaseUnitAmount must be non-negative" }
        require(tokenDecimals in 0..77) { "tokenDecimals must be in 0..77, got $tokenDecimals" }
    }

    fun toHex(): String = "0x" + value.toString(16)
    fun toBigInteger(): BigInteger = value

    companion object {
        // Strict decimal pattern: optional leading digits, optional single decimal point with digits
        // No leading +, no scientific notation, no hex, no non-ASCII
        private val STRICT_DECIMAL_REGEX = Regex("^[0-9]+(\\.[0-9]+)?$")
        private val STRICT_INTEGER_REGEX = Regex("^[0-9]+$")

        /**
         * Parse a human-readable decimal amount string into base units.
         *
         * This constructor is for HUMAN AMOUNTS ONLY (e.g., "100.5" USDC).
         * Hex strings (0x...) are NOT accepted — use [fromRawBaseUnits] for raw values.
         *
         * @param amountStr Human-readable amount like "100", "1.234567"
         * @param tokenDecimals Token decimals (e.g., 6 for USDC, 18 for ETH)
         * @throws IllegalArgumentException for invalid/malicious input
         */
        fun fromDecimalString(amountStr: String, tokenDecimals: Int): BaseUnitAmount {
            require(tokenDecimals in 0..77) { "Token decimals must be in 0..77, got $tokenDecimals" }

            val clean = amountStr.trim()

            // Reject empty
            require(clean.isNotEmpty()) { "Amount string must not be empty" }

            // Reject hex prefix in human amount API
            require(!clean.startsWith("0x") && !clean.startsWith("0X")) {
                "Hex strings not accepted in human amount API. Use fromRawBaseUnits() for raw values."
            }

            // Reject negative
            require(!clean.startsWith("-")) { "Amount must be non-negative" }

            // Reject leading +
            require(!clean.startsWith("+")) { "Leading '+' not accepted" }

            // Reject scientific notation
            require(!clean.contains('e', ignoreCase = true)) { "Scientific notation not accepted" }

            // Reject bare dot
            require(clean != ".") { "Bare '.' is not a valid amount" }

            // Validate strict decimal format
            require(STRICT_DECIMAL_REGEX.matches(clean)) {
                "Invalid amount format: '$clean'. Only digits and single decimal point accepted."
            }

            val parts = clean.split(".")
            // At this point regex guarantees at most 2 parts
            val wholeStr = parts[0].ifEmpty { "0" }
            val fractionStr = if (parts.size > 1) parts[1] else ""

            // Reject excess fractional digits
            require(fractionStr.length <= tokenDecimals) {
                "Amount fractional digits (${fractionStr.length}) exceed token decimals ($tokenDecimals)"
            }

            val paddedFraction = fractionStr.padEnd(tokenDecimals, '0')
            val combinedStr = (wholeStr + paddedFraction).replaceFirst(Regex("^0+"), "").ifEmpty { "0" }
            val baseUnits = BigInteger.parseString(combinedStr, 10)
            return BaseUnitAmount(baseUnits, tokenDecimals)
        }

        /**
         * Create from raw base-unit BigInteger (already in smallest unit).
         * This is for values that are already in base units (e.g., from RPC responses).
         */
        fun fromRawBaseUnits(value: BigInteger, tokenDecimals: Int): BaseUnitAmount {
            require(value >= BigInteger.ZERO) { "Base unit amount must be non-negative" }
            return BaseUnitAmount(value, tokenDecimals)
        }

        /**
         * Parse a raw base-unit hex string (e.g., from RPC or calldata).
         */
        fun fromRawHex(hex: String, tokenDecimals: Int): BaseUnitAmount {
            val clean = hex.trim().removePrefix("0x").removePrefix("0X")
            require(clean.isNotEmpty()) { "Hex string must not be empty" }
            require(STRICT_INTEGER_REGEX.matches(clean) || clean.matches(Regex("^[0-9a-fA-F]+$"))) {
                "Invalid hex format: '$clean'"
            }
            val value = BigInteger.parseString(clean, 16)
            return BaseUnitAmount(value, tokenDecimals)
        }
    }
}

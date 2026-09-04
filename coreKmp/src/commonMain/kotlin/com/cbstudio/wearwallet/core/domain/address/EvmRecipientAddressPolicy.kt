package com.cbstudio.wearwallet.core.domain.address

import io.github.iml1s.address.EthereumAddress

/**
 * Shared EVM recipient policy for Wear send and [com.cbstudio.wearwallet.core.domain.usecase.transaction.EstimateTransactionUseCase].
 *
 * - `0x` + 40 hex, all-lower or all-upper: accepted (no checksum provided).
 * - Mixed case: must match EIP-55. Length-42 hex is not sufficient if the checksum is wrong.
 */
object EvmRecipientAddressPolicy {

    fun isValid(address: String): Boolean {
        if (!EthereumAddress.isValidAddress(address)) return false
        val hex = address.substring(2)
        val letters = hex.filter { it.isLetter() }
        if (letters.isEmpty()) return true
        val allLower = letters.all { it.isLowerCase() }
        val allUpper = letters.all { it.isUpperCase() }
        if (allLower || allUpper) return true
        return EthereumAddress.isValidChecksumAddress(address)
    }
}

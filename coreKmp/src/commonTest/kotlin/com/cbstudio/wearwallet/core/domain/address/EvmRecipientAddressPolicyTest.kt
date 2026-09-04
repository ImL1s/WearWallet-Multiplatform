package com.cbstudio.wearwallet.core.domain.address

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * EIP-55 recipient policy: mixed-case must checksum; all-lower / all-upper
 * (no checksum provided) is accepted. Length-42 hex is not enough.
 *
 * Vector: EIP-55 spec example `0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed`.
 */
class EvmRecipientAddressPolicyTest {

    @Test
    fun acceptsKnownEip55ChecksumAddress() {
        assertTrue(EvmRecipientAddressPolicy.isValid(EIP55_GOOD))
    }

    @Test
    fun rejectsSameBytesWithWrongMixedCase() {
        assertFalse(
            EvmRecipientAddressPolicy.isValid(EIP55_WRONG_MIXED),
            "mixed-case that fails EIP-55 must be rejected even though it is 0x + 40 hex",
        )
        assertTrue(
            EIP55_WRONG_MIXED.length == 42 &&
                EIP55_WRONG_MIXED.startsWith("0x") &&
                EIP55_WRONG_MIXED.drop(2).equals(EIP55_GOOD.drop(2), ignoreCase = true),
            "fixture must be same bytes as the good checksum, wrong case only",
        )
    }

    @Test
    fun acceptsAllLowerAndAllUpperOfSameBytes() {
        assertTrue(EvmRecipientAddressPolicy.isValid(EIP55_ALL_LOWER))
        assertTrue(EvmRecipientAddressPolicy.isValid(EIP55_ALL_UPPER))
    }

    @Test
    fun rejectsMalformedHex() {
        assertFalse(EvmRecipientAddressPolicy.isValid(""))
        assertFalse(EvmRecipientAddressPolicy.isValid("0x1234"))
        assertFalse(EvmRecipientAddressPolicy.isValid("71C7656EC7ab88b098defB751B7401B5f6d8976F"))
        assertFalse(EvmRecipientAddressPolicy.isValid("0xZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZ"))
    }

    companion object {
        const val EIP55_GOOD = "0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed"
        const val EIP55_WRONG_MIXED = "0x5aaeb6053F3E94C9b9A09f33669435E7Ef1BeAed"
        const val EIP55_ALL_LOWER = "0x5aaeb6053f3e94c9b9a09f33669435e7ef1beaed"
        const val EIP55_ALL_UPPER = "0x5AAEB6053F3E94C9B9A09F33669435E7EF1BEAED"
    }
}

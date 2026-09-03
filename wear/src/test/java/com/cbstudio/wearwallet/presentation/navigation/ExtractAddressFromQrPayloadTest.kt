package com.cbstudio.wearwallet.presentation.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * QR payloads must yield a sendable address, including EIP-681 URIs from a phone scan.
 */
class ExtractAddressFromQrPayloadTest {

    @Test
    fun `plain 0x address is unchanged`() {
        assertEquals(
            "0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045",
            extractAddressFromQrPayload("0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045")
        )
    }

    @Test
    fun `eip681 uri drops scheme chain id and query`() {
        assertEquals(
            "0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045",
            extractAddressFromQrPayload(
                "ethereum:0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045@1?value=1e18"
            )
        )
    }

    @Test
    fun `whitespace is trimmed`() {
        assertEquals(
            "0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045",
            extractAddressFromQrPayload("  0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045  ")
        )
    }

    @Test
    fun `blank payload stays blank`() {
        assertEquals("", extractAddressFromQrPayload("   "))
    }
}

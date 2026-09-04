package io.github.iml1s.tx.bitcoin

import io.github.iml1s.address.AddressGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TxBuilderTest {

    @Test
    fun testBasicDSL() {
        val transaction = tx(AddressGenerator.Network.MAINNET) {
            version = 2
            input(
                txid = "0000000000000000000000000000000000000000000000000000000000000000",
                vout = 0
            )
            // P2WPKH
            output(
                address = "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4", // Valid BIP-173 address
                amount = 100_000_000
            )
            // P2PKH (Legacy)
            output(
                address = "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa", // Genesis address
                amount = 50_000_000
            )
        }

        assertEquals(2, transaction.version)
        assertEquals(1, transaction.inputs.size)
        assertEquals(2, transaction.outputs.size)
        assertEquals(100_000_000L, transaction.outputs[0].value)
        assertEquals(50_000_000L, transaction.outputs[1].value)

        // Verify scriptPubKey types
        assertTrue(Script.isP2WPKH(transaction.outputs[0].scriptPubKey))
        assertTrue(Script.isP2PKH(transaction.outputs[1].scriptPubKey))
    }
}

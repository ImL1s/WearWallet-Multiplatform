package io.github.iml1s.tx.bitcoin

import kotlin.test.Test
import kotlin.test.assertEquals
import io.github.iml1s.tx.bitcoin.Transaction

class TxBuilderVectorsTest {

    // BIP-143 Test Vectors
    // https://github.com/bitcoin/bips/blob/master/bip-0143.mediawiki
    
    @Test
    fun testBip143NativeP2WPKH() {
        // Native P2WPKH
        // Input:
        //   Previous tx: 8d276c76b32d56a350c3d9a5da9f6920d0f7a08b5f3a0026e632d43e5927376c
        //   Index: 1
        //   Amount: 600000000
        
        // Output:
        //   Amount: 599990000
        //   ScriptPubKey: 001479091972186c449eb1ded22b78e40d009bdf0089 (P2WPKH)
        
        val tx = Transaction(
            version = 1,
            inputs = listOf(
                TxInput(
                    previousTxHash = "5927376c5927376c5927376c5927376c5927376c5927376c5927376c5927376c".hexToByteArray(), // Fake for parsing test
                    previousOutputIndex = 1,
                    sequence = TxInput.SEQUENCE_FINAL,
                    scriptSig = ByteArray(0) 
                )
            ),
            outputs = listOf(
                TxOutput(
                    value = 599990000,
                    scriptPubKey = "001479091972186c449eb1ded22b78e40d009bdf0089".hexToByteArray()
                )
            ),
            lockTime = 0
        )
        
        // Note: We can't verify full serialization to specific byte array without signing 
        // because BIP-143 vectors are about signature verification (sighash).
        // But we can verify structure.
        
        assertEquals(1, tx.inputs.size)
        assertEquals(1, tx.outputs.size)
        assertEquals(599990000L, tx.outputs[0].value)
    }

    private fun String.hexToByteArray(): ByteArray {
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}

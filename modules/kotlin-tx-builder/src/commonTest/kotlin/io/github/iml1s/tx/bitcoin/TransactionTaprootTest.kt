package io.github.iml1s.tx.bitcoin

import io.github.iml1s.tx.crypto.Crypto
import io.github.iml1s.tx.utils.ByteVector32
import io.github.iml1s.tx.utils.ByteVector64
import io.github.iml1s.tx.utils.Hex
import io.github.iml1s.crypto.Secp256k1Pure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TransactionTaprootTest {
    
    // Test vector from bitcoin-kmp TaprootTestsCommon.kt: "send to and spend from taproot addresses"
    @Test
    fun testTaprootKeyPathSigning() {
        val privKeyBytes = Hex.decode("0101010101010101010101010101010101010101010101010101010101010101")
        
        // Previous transaction (funding tx)
        val prevTxHex = "02000000000101bf77ef36f2c0f32e0822cef0514948254997495a34bfba7dd4a73aabfcbb87900000000000fdffffff02c2c2000000000000160014b5c3dbfeb8e7d0c809c3ba3f815fd430777ef4be50c30000000000002251208c5db7f797196d6edc4dd7df6048f4ea6b883a6af6af032342088f436543790f0140583f758bea307216e03c1f54c3c6088e8923c8e1c89d96679fb00de9e808a79d0fba1cc3f9521cb686e8f43fb37cc6429f2e1480c70cc25ecb4ac0dde8921a01f1f70000"
        val prevTx = Transaction.read(prevTxHex)
        
        // Verification of parsing
        assertEquals(2, prevTx.version)
        assertEquals(1, prevTx.inputs.size)
        assertEquals(2, prevTx.outputs.size)
        
        // The UTXO we want to spend is index 1
        val utxoIndex = 1
        val utxo = prevTx.outputs[utxoIndex]
        
        val tx1Input = TxInput(
            previousTxHash = prevTx.getTxId(),
            previousOutputIndex = utxoIndex.toLong(),
            scriptSig = ByteArray(0), // Empty for Taproot
            sequence = TxInput.SEQUENCE_FINAL
        )
        
        val tx1Output = TxOutput(value = 49258, scriptPubKey = Hex.decode("51020000")) // Dummy
        
        val tx1 = Transaction(
            version = 2,
            inputs = listOf(tx1Input),
            outputs = listOf(tx1Output),
            lockTime = 0
        )
        
        // Calculate Sighash
        val sigHashType = Transaction.SIGHASH_DEFAULT // 0x00
        val utxos = listOf(utxo) 
        
        val hash = tx1.hashForSigningTaprootKeyPath(
            inputIndex = 0,
            prevOutputs = utxos,
            sighashType = sigHashType
        )
        
        println("Taproot Hash: ${Hex.encode(hash)}")
        
        // Sign using Schnorr
        val sig = Crypto.signSchnorr(hash, privKeyBytes)
        
        // Verify
        val pubKeyBytes = Secp256k1Pure.pubKeyOf(privKeyBytes)
        val xOnlyPubKey = pubKeyBytes.sliceArray(1 until 33)
        // println("Pub Key (xOnly): ${Hex.encode(xOnlyPubKey)}")
        
        val verified = Crypto.verifySignatureSchnorr(hash, sig, xOnlyPubKey)
        
        assertTrue(verified, "Schnorr signature verification failed")
    }
}

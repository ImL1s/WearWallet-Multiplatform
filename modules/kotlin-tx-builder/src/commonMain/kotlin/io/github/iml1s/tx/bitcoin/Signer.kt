package io.github.iml1s.tx.bitcoin

import io.github.iml1s.crypto.Secp256k1Pure

import io.github.iml1s.tx.crypto.Crypto

object Signer {

    /**
     * Sign a P2WPKH input
     * 
     * @param tx The transaction to sign
     * @param inputIndex Index of the input to sign
     * @param privateKey Private key (32 bytes)
     * @param publicKey Public key (33 bytes) used in P2WPKH script
     * @param amount Amount of the UTXO being spent (required for SegWit)
     * @return The witness stack (signature + public key)
     */
    fun signP2WPKH(
        tx: Transaction,
        inputIndex: Int,
        privateKey: ByteArray,
        publicKey: ByteArray,
        amount: Long
    ): TxWitness {
        // P2WPKH scriptCode is: 0x19 0x76 0xa9 0x14 {20-byte-pubkey-hash} 0x88 0xac
        // 0x19 = 25 (length)
        // OP_DUP OP_HASH160 20-bytes OP_EQUALVERIFY OP_CHECKSIG
        
        val pubKeyHash = Crypto.hash160(publicKey)
        val scriptCode = ByteArray(26)
        scriptCode[0] = 0x19.toByte() // Size 25
        scriptCode[1] = 0x76.toByte() // OP_DUP
        scriptCode[2] = 0xA9.toByte() // OP_HASH160
        scriptCode[3] = 0x14.toByte() // Push 20 bytes
        pubKeyHash.copyInto(scriptCode, destinationOffset = 4)
        scriptCode[24] = 0x88.toByte() // OP_EQUALVERIFY
        scriptCode[25] = 0xAC.toByte() // OP_CHECKSIG

        val hashType = Transaction.SIGHASH_ALL
        
        // Calculate Sighash
        val sighash = tx.hashForSignature(
            inputIndex = inputIndex,
            scriptCode = scriptCode, // For P2WPKH, use the subscript (dup hash160 ...)
            hashType = hashType,
            amount = amount,
            isSegWit = true
        )

        // Sign
        val signature = Secp256k1Pure.sign(sighash, privateKey)
        
        // Append HashType to signature (DER encoded + 1 byte hashType)
        // Secp256k1Pure.sign returns 64-byte compact signature by default?
        // Wait, Transaction signing in Bitcoin usually expects DER encoded signature + hashType byte.
        // Secp256k1Pure.sign returns compact (64 bytes).
        // I need to encode it to DER.
        // I need to check if Secp256k1Pure exposes encodeDER (it was private in my view).
        // If not, I need to add it or implement it here.
        // Checking Secp256k1Pure: encodeDER was private.
        // I should probably expose it or implement usage here.
        // Let's assume for now I need to implement DER encoding here or change Secp256k1Pure to public.
        
        // For now, I will add a TO-DO or minimal DER encoder here if needed.
        // Actually, Secp256k1Pure had `encodeDER` as private. 
        // I'll update Secp256k1Pure to publicize it in next step if test fails or before.
        // Or I can just write a DER encoder here. It's standard.
        // Minimal DER encoder: 0x30 [len] 0x02 [len_r] r 0x02 [len_s] s
        
        val r = signature.sliceArray(0 until 32)
        val s = signature.sliceArray(32 until 64)
        
        val derSig = encodeDer(r, s) + hashType.toByte()
        
        return TxWitness(listOf(derSig, publicKey))
    }
    
    private fun encodeDer(r: ByteArray, s: ByteArray): ByteArray {
        // Simplistic DER encoding (assuming positive integers, might need 0x00 prefix if MSB is 1)
        fun toDerInt(bytes: ByteArray): ByteArray {
            var b = bytes
            // Remove leading zeros
            while(b.size > 1 && b[0] == 0.toByte()) {
                b = b.sliceArray(1 until b.size)
            }
            // Add 0x00 if MSB is 1 (to make it positive)
            if ((b[0].toInt() and 0x80) != 0) {
                return byteArrayOf(0) + b
            }
            return b
        }
        
        val rDer = toDerInt(r)
        val sDer = toDerInt(s)
        
        val len = 2 + rDer.size + 2 + sDer.size
        
        val out = ByteArray(2 + len)
        var idx = 0
        out[idx++] = 0x30.toByte() // SEQUENCE
        out[idx++] = len.toByte()
        
        out[idx++] = 0x02.toByte() // INT
        out[idx++] = rDer.size.toByte()
        rDer.copyInto(out, destinationOffset = idx)
        idx += rDer.size
        
        out[idx++] = 0x02.toByte() // INT
        out[idx++] = sDer.size.toByte()
        sDer.copyInto(out, destinationOffset = idx)
        // idx += sDer.size
        
        return out
    }
}

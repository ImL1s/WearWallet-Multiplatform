package com.cbstudio.wearwallet.core.multichain.monero.crypto

/**
 * Keccak-256 implementation for Monero
 * 
 * Note: Monero uses the original Keccak-256, not SHA3-256
 * They differ in the padding scheme.
 * 
 * This implementation uses the kotlincrypto library which provides
 * the correct Keccak-256 (not SHA3-256) that Monero requires.
 */
object MoneroKeccak {
    
    /**
     * Compute Keccak-256 hash
     * 
     * @param input Input data
     * @return 32-byte hash
     */
    fun keccak256(input: ByteArray): ByteArray {
        // Use kotlincrypto's Keccak256 implementation
        // This is the correct Keccak (not SHA3) that Monero uses
        val keccak = org.kotlincrypto.hash.sha3.Keccak256()
        keccak.update(input)
        return keccak.digest()
    }
}

/**
 * Extension function for convenience
 */
fun ByteArray.keccak256(): ByteArray = MoneroKeccak.keccak256(this)
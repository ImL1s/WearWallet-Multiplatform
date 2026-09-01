package com.cbstudio.wearwallet.core.security

import com.cbstudio.wearwallet.core.domain.model.ChainType

/**
 * Native Crypto Delegate Interface
 * 
 * Defines cryptographic operations that should be delegated to the native platform
 * (e.g. Swift/iOS App) which hosts the actual TrustWallet Core and Keystone SDKs.
 * 
 * This avoids complex cinterop configurations in KMP and allows the host app
 * to inject fully configured native implementations.
 */
interface NativeCryptoDelegate {
    /**
     * Derive address from Extended Public Key (xpub)
     */
    fun deriveAddressFromXpub(
        xpub: String,
        derivationPath: String,
        isTestnet: Boolean = false,
        policy: ExtendedPublicKeyPolicy? = null
    ): String
    
    /**
     * Generate KeyPair from Mnemonic
     */
    fun generateKeyPair(mnemonic: String, derivationPath: String, chainType: ChainType): KeyPair
    
    /**
     * Sign Transaction
     */
    fun signTransaction(data: ByteArray, privateKey: String): ByteArray
    
    /**
     * Keystone/UR Encode
     * Wraps KeystoneSDK/URKit encode logic.
     * Returns a list of UR parts (strings). Even for single part, return list of size 1.
     */
    fun encodeUR(data: ByteArray, type: String, maxFragmentSize: Int): List<String>
    
    /**
     * Keystone/UR Decode
     * Wraps KeystoneSDK/URKit decode logic.
     */
    fun decodeUR(urString: String): ByteArray
    
    /**
     * Keystone/UR Combine
     * Combines multiple UR parts into the decoded data.
     */
    fun combineUR(parts: List<String>): ByteArray
}

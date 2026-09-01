package com.cbstudio.wearwallet.core.security

/**
 * Native Crypto Singleton
 * 
 * Holds the reference to the [NativeCryptoDelegate] injected by the host application.
 * Platform-specific CryptoProviders (iOS/WatchOS) should check [isAvailable]
 * and use [delegate] if possible.
 */
object NativeCrypto {
    
    private var _delegate: NativeCryptoDelegate? = null
    
    /**
     * The delegate instance. 
     * Throws IllegalStateException if accessed before initialization via setDelegate.
     * Note: Use [delegateOrNull] for safe access.
     */
    val delegate: NativeCryptoDelegate
        get() = _delegate ?: throw IllegalStateException("NativeCryptoDelegate not initialized. Call NativeCrypto.setDelegate() from iOS/WatchOS App.")

    /**
     * Safe access to delegate
     */
    val delegateOrNull: NativeCryptoDelegate?
        get() = _delegate
        
    /**
     * Check if a delegate is available
     */
    fun isAvailable(): Boolean = _delegate != null
    
    /**
     * Set the delegate implementation.
     * Should be called by the iOS/WatchOS Application on launch.
     */
    fun setDelegate(delegate: NativeCryptoDelegate) {
        _delegate = delegate
        println("NativeCrypto: Delegate initialized")
    }
}

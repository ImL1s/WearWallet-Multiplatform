package com.m2049r.xmrwallet.model

/**
 * Monerujo Bridge Class
 * 
 * This class exists to satisfy libmonerujo.so's JNI registration requirements.
 * The native library expects Java classes in the com.m2049r.xmrwallet package,
 * but our project uses com.cbstudio.wearwallet.
 * 
 * This bridge class forwards calls to our actual implementation.
 */
class CoinsInfo {
    // Basic fields that Monerujo native code might expect
    var blockHeight: Long = 0
    var hash: String? = null
    var pubKey: String? = null
    var address: String? = null
    var globalIndex: Long = 0
    var spent: Boolean = false
    var keyImage: String? = null
    var amount: Long = 0
    var frozen: Boolean = false
    var spentHeight: Long = 0
    var unlocked: Boolean = false
    
    // Constructor for JNI
    constructor()
    
    // Additional constructor with parameters
    constructor(amount: Long, unlocked: Boolean) {
        this.amount = amount
        this.unlocked = unlocked
    }
}
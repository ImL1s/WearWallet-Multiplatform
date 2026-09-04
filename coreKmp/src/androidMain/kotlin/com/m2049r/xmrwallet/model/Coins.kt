package com.m2049r.xmrwallet.model

/**
 * Monerujo Bridge Class - Coins
 * 
 * Manages coin outputs for a wallet.
 */
class Coins {
    // Native handle
    var handle: Long = 0
    
    // Constructor for JNI
    constructor()
    
    // Native methods
    external fun getCount(): Int
    external fun getCoin(index: Int): CoinsInfo?
    external fun getAll(): List<CoinsInfo>?
    external fun refresh()
    external fun setFrozen(index: Int, frozen: Boolean)
    external fun thaw(keyImage: String?)
    external fun freeze(keyImage: String?)
    external fun isTransferUnlocked(unlockTime: Long, height: Long): Boolean
}
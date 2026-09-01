package com.m2049r.xmrwallet.model

/**
 * Monerujo Bridge Class - TransactionHistory
 * 
 * Manages transaction history for a wallet.
 */
class TransactionHistory {
    // Native handle
    var handle: Long = 0
    
    // Constructor for JNI
    constructor()
    
    // Native methods
    external fun getCount(): Int
    external fun getTransaction(i: Int): TransactionInfo?
    external fun getTransaction(id: String?): TransactionInfo?
    external fun getAll(): List<TransactionInfo>?
    external fun refresh()
}
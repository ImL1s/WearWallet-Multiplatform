package com.m2049r.xmrwallet.model

/**
 * Monerujo Bridge Class - Transfer
 * 
 * Represents a transfer within a transaction.
 */
class Transfer {
    var amount: Long = 0
    var address: String? = null
    
    // Constructor for JNI
    constructor()
    
    constructor(amount: Long, address: String?) {
        this.amount = amount
        this.address = address
    }
}
package com.m2049r.xmrwallet.model

/**
 * Monerujo Bridge Class - SubaddressRow
 * 
 * Represents a subaddress within an account.
 */
class SubaddressRow {
    var rowId: Int = 0
    var address: String? = null
    var label: String? = null
    
    // Constructor for JNI
    constructor()
    
    constructor(rowId: Int, address: String?, label: String?) {
        this.rowId = rowId
        this.address = address
        this.label = label
    }
}
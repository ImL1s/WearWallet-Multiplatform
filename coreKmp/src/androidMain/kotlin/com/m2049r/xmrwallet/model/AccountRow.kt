package com.m2049r.xmrwallet.model

/**
 * Monerujo Bridge Class - AccountRow
 * 
 * Represents an account within a wallet.
 */
class AccountRow {
    var rowId: Int = 0
    var balance: Long = 0
    var unlockedBalance: Long = 0
    var label: String? = null
    
    // Constructor for JNI
    constructor()
    
    constructor(rowId: Int, label: String?) {
        this.rowId = rowId
        this.label = label
    }
    
    fun getBalance(): String = balance.toString()
    fun getUnlockedBalance(): String = unlockedBalance.toString()
}
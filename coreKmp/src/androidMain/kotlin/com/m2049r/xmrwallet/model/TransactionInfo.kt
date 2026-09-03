package com.m2049r.xmrwallet.model

/**
 * Monerujo Bridge Class - TransactionInfo
 * 
 * Represents transaction information that the native library expects.
 */
class TransactionInfo {
    // Transaction state enum
    enum class Direction {
        Direction_In,
        Direction_Out
    }
    
    // Basic transaction fields
    var direction: Direction = Direction.Direction_In
    var isPending: Boolean = false
    var isFailed: Boolean = false
    var blockheight: Long = 0
    var amount: Long = 0
    var fee: Long = 0
    var confirmations: Long = 0
    var hash: String? = null
    var timestamp: Long = 0
    var paymentId: String? = null
    var transfers: Long = 0  // Handle to native transfers
    var label: String? = null
    var unlockTime: Long = 0
    var subaddressAccount: Long = 0
    var address: String? = null
    var addressLabel: String? = null
    
    // Constructor for JNI
    constructor()
    
    // Native handle
    var handle: Long = 0
    
    // Transfer list (simplified)
    fun getTransfers(): List<Transfer> = emptyList()
}
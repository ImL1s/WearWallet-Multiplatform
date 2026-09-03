package com.m2049r.xmrwallet.model

/**
 * Monerujo Bridge Class - PendingTransaction
 * 
 * Represents a transaction that is pending to be sent.
 */
class PendingTransaction {
    
    enum class Status {
        Status_Ok,
        Status_Error,
        Status_Critical
    }
    
    enum class Priority {
        Priority_Default,
        Priority_Low,
        Priority_Medium,
        Priority_High
    }
    
    // Native handle
    var handle: Long = 0
    
    // Constructor for JNI
    constructor()
    
    // Native methods
    external fun getStatus(): Status
    external fun getErrorString(): String?
    external fun commit(filename: String?, overwrite: Boolean): Boolean
    external fun getAmount(): Long
    external fun getDust(): Long
    external fun getFee(): Long
    external fun getFirstTxId(): String?
    external fun getTxCount(): Long
}
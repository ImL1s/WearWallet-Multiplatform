package com.m2049r.xmrwallet.model

/**
 * Monerujo Bridge Interface - WalletListener
 * 
 * Listener interface for wallet events that the native library expects.
 */
interface WalletListener {
    
    /**
     * moneySpent - called when money was spent
     */
    fun moneySpent(txId: String?, amount: Long, fee: Long)
    
    /**
     * moneyReceived - called when money was received
     */
    fun moneyReceived(txId: String?, amount: Long)
    
    /**
     * unconfirmedMoneyReceived - called when unconfirmed money was received
     */
    fun unconfirmedMoneyReceived(txId: String?, amount: Long)
    
    /**
     * newBlock - called when a new block is processed
     */
    fun newBlock(height: Long)
    
    /**
     * updated - called when wallet is updated  
     */
    fun updated()
    
    /**
     * refreshed - called when wallet refresh is complete
     */
    fun refreshed()
}
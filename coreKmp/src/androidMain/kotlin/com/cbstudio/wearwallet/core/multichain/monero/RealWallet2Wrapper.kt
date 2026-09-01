package com.cbstudio.wearwallet.core.multichain.monero

import android.util.Log
import com.cbstudio.wearwallet.core.domain.usecase.transaction.TypedUnsupportedTransactionException

/**
 * REAL Wallet2 Wrapper - Disabled / short-circuited in production release
 */
object RealWallet2Wrapper {
    private const val TAG = "RealWallet2"

    /**
     * 載入真實的 wallet2 library (short-circuited in release)
     */
    fun loadRealWallet2Library(): Boolean {
        Log.w(TAG, "RealWallet2 library loading is disabled in production release")
        return false
    }

    fun createRealWalletFromMnemonic(
        mnemonic: String,
        networkType: Int,
        path: String = ""
    ): Long = 0L

    fun getRealWalletAddress(handle: Long): String = ""

    fun setRealDaemonAddress(handle: Long, daemonUrl: String): Boolean = false

    fun startRealRefresh(handle: Long): Boolean = false

    fun refreshRealWallet(handle: Long): Boolean = false

    fun setRealRefreshFromBlockHeight(handle: Long, height: Long): Boolean = false

    fun getRealSyncHeight(handle: Long): Long = 0L

    fun getRealDaemonHeight(handle: Long): Long = 0L

    fun isRealWalletSynced(handle: Long): Boolean = false

    fun getRealBalance(handle: Long, accountIndex: Int = 0): Long = 0L

    fun getRealUnlockedBalance(handle: Long, accountIndex: Int = 0): Long = 0L

    fun getRealTransactionHistory(handle: Long): List<TransactionInfo> = emptyList()

    fun createRealTransaction(
        walletHandle: Long,
        address: String,
        amount: Long,
        mixinCount: Int = 10,
        priority: Int = 0
    ): Long = 0L

    fun getRealTransactionFee(txHandle: Long): Long = 0L

    @JvmStatic
    fun getRealTransactionHash(pendingTxHandle: Long): String = ""

    fun commitRealTransaction(walletHandle: Long, txHandle: Long): Boolean = false

    fun closeRealWallet(handle: Long) {
        // No-op in release
    }

    fun createAndSyncEmotionWallet(
        mnemonic: String,
        daemonUrl: String
    ): WalletSyncResult {
        Log.w(TAG, "createAndSyncEmotionWallet is disabled in production release")
        return WalletSyncResult(
            success = false,
            error = "RealWallet2 library loading is disabled in production release"
        )
    }

    data class WalletSyncResult(
        val success: Boolean,
        val error: String? = null,
        val handle: Long = 0,
        val address: String = "",
        val balance: Long = 0,
        val unlockedBalance: Long = 0,
        val transactions: List<TransactionInfo> = emptyList(),
        val syncHeight: Long = 0,
        val daemonHeight: Long = 0
    ) {
        val balanceXmr: Double get() = balance / 1e12
        val unlockedXmr: Double get() = unlockedBalance / 1e12
    }
}
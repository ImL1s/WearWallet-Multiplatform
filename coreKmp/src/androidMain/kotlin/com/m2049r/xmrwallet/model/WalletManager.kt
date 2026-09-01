package com.m2049r.xmrwallet.model

/**
 * Monerujo Bridge Class - WalletManager
 * 
 * Manages wallet instances and provides factory methods.
 */
class WalletManager {
    
    // Constructor for JNI
    private constructor()
    
    companion object {
        @JvmStatic
        private var instance: WalletManager? = null
        
        @JvmStatic
        fun getInstance(): WalletManager {
            if (instance == null) {
                instance = WalletManager()
            }
            return instance!!
        }
        
        init {
            // Load native library if not already loaded
            try {
                System.loadLibrary("monerujo")
            } catch (e: UnsatisfiedLinkError) {
                // Already loaded or will be loaded elsewhere
            }
        }
    }
    
    // Native methods
    external fun createWallet(path: String?, password: String?, language: String?, nettype: Wallet.NetworkType): Wallet?
    external fun openWallet(path: String?, password: String?, nettype: Wallet.NetworkType): Wallet?
    external fun recoveryWallet(path: String?, password: String?, mnemonic: String?, nettype: Wallet.NetworkType, restoreHeight: Long): Wallet?
    external fun recoveryWalletFromKeys(path: String?, password: String?, language: String?, nettype: Wallet.NetworkType, 
                                       restoreHeight: Long, addressString: String?, viewKeyString: String?, spendKeyString: String?): Wallet?
    external fun walletExists(path: String?): Boolean
    external fun verifyWalletPassword(keyPath: String?, password: String?, watch: Boolean): Boolean
    external fun verifyWalletPasswordOnly(keyPath: String?, password: String?): Boolean
    external fun findWallets(path: String?): List<String>?
    external fun getErrorString(): String?
    external fun setDaemonAddress(address: String?)
    external fun getDaemonAddress(): String?
    external fun setDaemonUsername(username: String?)
    external fun setDaemonPassword(password: String?)
    external fun getNetworkDifficulty(): Long
    external fun getBlockTarget(): Long
    external fun getBlockchainHeight(): Long
    external fun getBlockchainTargetHeight(): Long
    external fun getNetworkHashRate(): Double
    external fun isMining(): Boolean
    external fun startMining(address: String?, background: Boolean, threads: Int): Boolean
    external fun stopMining(): Boolean
    external fun pauseRefreshJ()
    external fun onRefreshProgressJ(walletHandle: Long, height: Long, startHeight: Long, endHeight: Long): Int
    external fun setLogLevel(level: Int)
    external fun logDebug(category: String?, message: String?)
    external fun logInfo(category: String?, message: String?)
    external fun logWarning(category: String?, message: String?)
    external fun logError(category: String?, message: String?)
}
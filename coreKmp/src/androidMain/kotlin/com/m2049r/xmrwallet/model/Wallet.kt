package com.m2049r.xmrwallet.model

/**
 * Monerujo Bridge Class - Wallet
 * 
 * Main wallet interface that the native library expects.
 */
class Wallet {
    // Network types
    enum class NetworkType {
        NetworkType_Mainnet,
        NetworkType_Testnet,
        NetworkType_Stagenet
    }
    
    // Connection status
    enum class ConnectionStatus {
        ConnectionStatus_Disconnected,
        ConnectionStatus_Connected,
        ConnectionStatus_WrongVersion
    }
    
    // Status codes
    class Status {
        var status: Int = 0
        var errorString: String? = null
        
        fun isOk(): Boolean = status == 0
    }
    
    // Native handle
    var handle: Long = 0
    
    // Basic wallet properties (using private to avoid JVM signature clash)
    private var _path: String? = null
    private var _networkType: NetworkType = NetworkType.NetworkType_Mainnet
    
    // Constructor for JNI
    constructor()
    
    // Native methods (will be implemented by JNI)
    external fun getAddress(accountIndex: Int, addressIndex: Int): String?
    external fun getBalance(accountIndex: Int): Long
    external fun getUnlockedBalance(accountIndex: Int): Long
    external fun getSeed(): String?
    external fun getSeedLanguage(): String?
    external fun getSecretViewKey(): String?
    external fun getPublicViewKey(): String?
    external fun getSecretSpendKey(): String?
    external fun getPublicSpendKey(): String?
    external fun store(path: String?): Boolean
    external fun close(): Boolean
    external fun getFilename(): String?
    external fun getPassword(): String?
    external fun setPassword(password: String?): Boolean
    external fun getIntegratedAddress(payment_id: String?): String?
    external fun getPath(): String?
    external fun getNetworkType(): NetworkType
    external fun getBlockChainHeight(): Long
    external fun getDaemonBlockChainHeight(): Long
    external fun getDaemonBlockChainTargetHeight(): Long
    external fun isSynchronized(): Boolean
    external fun getDisplayAmount(amount: Long): String?
    external fun getAmountFromString(amount: String?): Long
    external fun getAmountFromDouble(amount: Double): Long
    external fun startRefresh(): Boolean
    external fun pauseRefresh()
    external fun refresh(): Boolean
    external fun refreshAsync()
    external fun setAutoRefreshInterval(seconds: Int)
    external fun getAutoRefreshInterval(): Int
    external fun rescanBlockchain()
    external fun rescanBlockchainAsync()
    external fun getConnectionStatus(): ConnectionStatus
    external fun setTrustedDaemon(trusted: Boolean)
    external fun isTrustedDaemon(): Boolean
    external fun getBalance(): Long
    external fun getUnlockedBalance(): Long
    external fun isWatchOnly(): Boolean
    external fun getBlockChainHeightJ(): Long
    external fun getApproximateBlockChainHeight(): Long
    external fun getDaemonBlockChainHeightJ(): Long
    
    companion object {
        const val LOGLEVEL_SILENT = 0
        const val LOGLEVEL_WARN = 1
        const val LOGLEVEL_INFO = 2
        const val LOGLEVEL_DEBUG = 3
        const val LOGLEVEL_TRACE = 4
        const val LOGLEVEL_MAX = 5
    }
}
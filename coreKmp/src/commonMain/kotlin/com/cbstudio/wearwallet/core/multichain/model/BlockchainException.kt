package com.cbstudio.wearwallet.core.multichain.model

import com.cbstudio.wearwallet.core.multichain.MultiChainType

/**
 * 區塊鏈相關的例外類別
 */
sealed class BlockchainException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {
    
    /**
     * 網路連線錯誤
     */
    class NetworkException(
        val chainType: MultiChainType,
        message: String,
        cause: Throwable? = null
    ) : BlockchainException("Network error for ${chainType.symbol}: $message", cause)
    
    /**
     * 餘額不足
     */
    class InsufficientBalanceException(
        val chainType: MultiChainType,
        val required: String,
        val available: String
    ) : BlockchainException(
        "Insufficient balance for ${chainType.symbol}: required $required, available $available"
    )
    
    /**
     * 無效地址
     */
    class InvalidAddressException(
        val chainType: MultiChainType,
        val address: String
    ) : BlockchainException("Invalid address for ${chainType.symbol}: $address")
    
    /**
     * 交易建立失敗
     */
    class TransactionBuildException(
        val chainType: MultiChainType,
        message: String,
        cause: Throwable? = null
    ) : BlockchainException("Failed to build transaction for ${chainType.symbol}: $message", cause)
    
    /**
     * 交易簽名失敗
     */
    class TransactionSignException(
        val chainType: MultiChainType,
        message: String,
        cause: Throwable? = null
    ) : BlockchainException("Failed to sign transaction for ${chainType.symbol}: $message", cause)
    
    /**
     * 交易廣播失敗
     */
    class TransactionBroadcastException(
        val chainType: MultiChainType,
        message: String,
        cause: Throwable? = null
    ) : BlockchainException("Failed to broadcast transaction for ${chainType.symbol}: $message", cause)
    
    /**
     * 手續費估算失敗
     */
    class FeeEstimationException(
        val chainType: MultiChainType,
        message: String,
        cause: Throwable? = null
    ) : BlockchainException("Failed to estimate fee for ${chainType.symbol}: $message", cause)
    
    /**
     * API 呼叫失敗
     */
    class ApiException(
        val chainType: MultiChainType,
        val apiEndpoint: String,
        val statusCode: Int? = null,
        message: String,
        cause: Throwable? = null
    ) : BlockchainException(
        "API error for ${chainType.symbol} at $apiEndpoint${statusCode?.let { " (HTTP $it)" } ?: ""}: $message",
        cause
    )
    
    /**
     * 私鑰相關錯誤
     */
    class PrivateKeyException(
        val chainType: MultiChainType,
        message: String,
        cause: Throwable? = null
    ) : BlockchainException("Private key error for ${chainType.symbol}: $message", cause)
    
    /**
     * 不支援的操作
     */
    class UnsupportedOperationException(
        val chainType: MultiChainType,
        val operation: String
    ) : BlockchainException("Unsupported operation '$operation' for ${chainType.symbol}")
    
    /**
     * 配置錯誤
     */
    class ConfigurationException(
        val chainType: MultiChainType,
        message: String
    ) : BlockchainException("Configuration error for ${chainType.symbol}: $message")
    
    /**
     * 通用區塊鏈錯誤
     */
    class GenericException(
        val chainType: MultiChainType,
        message: String,
        cause: Throwable? = null
    ) : BlockchainException("Error for ${chainType.symbol}: $message", cause)
}

/**
 * 建立區塊鏈例外的便利函數
 */
object BlockchainExceptionFactory {
    
    fun networkError(
        chainType: MultiChainType,
        message: String,
        cause: Throwable? = null
    ): BlockchainException.NetworkException {
        return BlockchainException.NetworkException(chainType, message, cause)
    }
    
    fun insufficientBalance(
        chainType: MultiChainType,
        required: String,
        available: String
    ): BlockchainException.InsufficientBalanceException {
        return BlockchainException.InsufficientBalanceException(chainType, required, available)
    }
    
    fun invalidAddress(
        chainType: MultiChainType,
        address: String
    ): BlockchainException.InvalidAddressException {
        return BlockchainException.InvalidAddressException(chainType, address)
    }
    
    fun transactionBuildError(
        chainType: MultiChainType,
        message: String,
        cause: Throwable? = null
    ): BlockchainException.TransactionBuildException {
        return BlockchainException.TransactionBuildException(chainType, message, cause)
    }
    
    fun transactionSignError(
        chainType: MultiChainType,
        message: String,
        cause: Throwable? = null
    ): BlockchainException.TransactionSignException {
        return BlockchainException.TransactionSignException(chainType, message, cause)
    }
    
    fun unsupportedOperation(
        chainType: MultiChainType,
        operation: String
    ): BlockchainException.UnsupportedOperationException {
        return BlockchainException.UnsupportedOperationException(chainType, operation)
    }
}
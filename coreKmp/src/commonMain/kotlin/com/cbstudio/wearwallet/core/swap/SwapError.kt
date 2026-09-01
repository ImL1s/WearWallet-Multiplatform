package com.cbstudio.wearwallet.core.swap

/**
 * Swap 功能統一錯誤類型
 * 
 * 用於在 ViewModel 和 UI 層提供一致的錯誤處理和顯示
 */
sealed class SwapError(
    open val message: String,
    open val cause: Throwable? = null
) {
    /**
     * 餘額不足
     */
    data class InsufficientBalance(
        val required: String,
        val available: String,
        val tokenSymbol: String
    ) : SwapError("$tokenSymbol 餘額不足：需要 $required，可用 $available")
    
    /**
     * Gas 費用不足
     */
    data class InsufficientGas(
        val required: String,
        val available: String,
        val nativeToken: String
    ) : SwapError("$nativeToken 餘額不足以支付 Gas 費用")
    
    /**
     * 滑點過高
     */
    data class SlippageTooHigh(
        val expectedSlippage: Double,
        val actualSlippage: Double
    ) : SwapError("滑點過大：預期 ${expectedSlippage}%，實際 ${actualSlippage}%")
    
    /**
     * 找不到交易路徑
     */
    data class NoRouteFound(
        val fromToken: String,
        val toToken: String
    ) : SwapError("找不到從 $fromToken 到 $toToken 的交易路徑")
    
    /**
     * 代幣授權失敗
     */
    data class ApprovalFailed(
        val tokenSymbol: String,
        override val cause: Throwable? = null
    ) : SwapError("$tokenSymbol 授權失敗", cause)
    
    /**
     * 交易被拒絕
     */
    data class TransactionRejected(
        val reason: String? = null
    ) : SwapError(reason ?: "交易被拒絕")
    
    /**
     * 網路錯誤
     */
    data class NetworkError(
        override val cause: Throwable? = null
    ) : SwapError("網路連線錯誤，請稍後再試", cause)
    
    /**
     * 報價已過期
     */
    data object QuoteExpired : SwapError("報價已過期，請重新獲取")
    
    /**
     * 密碼錯誤
     */
    data object InvalidPassword : SwapError("密碼錯誤")
    
    /**
     * 未知錯誤
     */
    data class Unknown(
        override val message: String,
        override val cause: Throwable? = null
    ) : SwapError(message, cause)
    
    companion object {
        /**
         * 從 Exception 解析 SwapError
         */
        fun fromException(e: Exception): SwapError {
            val message = e.message ?: ""
            return when {
                message.contains("insufficient funds", ignoreCase = true) ||
                message.contains("餘額不足", ignoreCase = true) -> 
                    InsufficientBalance("", "", "")
                    
                message.contains("gas", ignoreCase = true) && 
                message.contains("low", ignoreCase = true) -> 
                    InsufficientGas("", "", "")
                    
                message.contains("slippage", ignoreCase = true) -> 
                    SlippageTooHigh(0.0, 0.0)
                    
                message.contains("no route", ignoreCase = true) ||
                message.contains("route not found", ignoreCase = true) -> 
                    NoRouteFound("", "")
                    
                message.contains("allowance", ignoreCase = true) ||
                message.contains("approve", ignoreCase = true) -> 
                    ApprovalFailed("", e)
                    
                message.contains("rejected", ignoreCase = true) ||
                message.contains("denied", ignoreCase = true) -> 
                    TransactionRejected(message)
                    
                message.contains("timeout", ignoreCase = true) ||
                message.contains("network", ignoreCase = true) ||
                message.contains("connection", ignoreCase = true) -> 
                    NetworkError(e)
                    
                message.contains("expired", ignoreCase = true) -> 
                    QuoteExpired
                    
                message.contains("password", ignoreCase = true) ||
                message.contains("密碼", ignoreCase = true) -> 
                    InvalidPassword
                    
                else -> Unknown(message, e)
            }
        }
    }
}

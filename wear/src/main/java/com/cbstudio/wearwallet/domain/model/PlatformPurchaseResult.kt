package com.cbstudio.wearwallet.domain.model

/**
 * 平台購買結果的臨時本地實作
 * TODO: 在 sharedKmp 編譯修復後，移除此檔案並使用共享實作
 */
sealed class PlatformPurchaseResult {
    object Success : PlatformPurchaseResult()
    object Cancelled : PlatformPurchaseResult()
    data class Error(val message: String) : PlatformPurchaseResult()
}

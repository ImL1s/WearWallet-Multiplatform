package com.cbstudio.wearwallet.core.multichain.model

import com.cbstudio.wearwallet.core.multichain.MultiChainType
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * 統一的轉帳請求模型
 * 適用於所有支援的區塊鏈
 */
@Serializable
data class TransferRequest(
    val chainType: MultiChainType,
    val fromAddress: String,
    val toAddress: String,
    val amount: String, // 使用字串避免精度問題
    val memo: String? = null,
    val feeRate: String? = null, // 手續費率（可選）
    val chainSpecific: Map<String, String> = emptyMap() // 鏈特定參數
) {
    /**
     * 驗證轉帳請求的基本有效性
     */
    fun validate(): ValidationResult {
        // 檢查金額
        val amountValue = amount.toDoubleOrNull()
        if (amountValue == null || amountValue <= 0) {
            return ValidationResult.Invalid("Invalid amount: $amount")
        }
        
        // 檢查地址
        if (fromAddress.isBlank()) {
            return ValidationResult.Invalid("From address cannot be empty")
        }
        
        if (toAddress.isBlank()) {
            return ValidationResult.Invalid("To address cannot be empty")
        }
        
        // 檢查是否為自轉
        if (fromAddress == toAddress) {
            return ValidationResult.Invalid("Cannot transfer to the same address")
        }
        
        return ValidationResult.Valid
    }
    
    /**
     * 取得鏈特定參數
     */
    fun getChainSpecific(key: String): String? = chainSpecific[key]
    
    /**
     * 添加鏈特定參數
     */
    fun withChainSpecific(key: String, value: String): TransferRequest {
        return this.copy(chainSpecific = chainSpecific + (key to value))
    }
}

/**
 * 驗證結果
 */
sealed class ValidationResult {
    object Valid : ValidationResult()
    
    data class Invalid(val message: String) : ValidationResult()
    
    val isValid: Boolean
        get() = this is Valid
}
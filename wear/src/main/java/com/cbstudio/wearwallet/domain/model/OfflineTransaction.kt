package com.cbstudio.wearwallet.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.util.UUID

/**
 * 離線交易資料模型
 * 用於手腕到手腕的 NFC 轉帳
 */
@Serializable
data class OfflineTransaction(
    val id: String = UUID.randomUUID().toString(),
    val type: OfflineTransactionType,
    val payload: TransactionPayload,
    val metadata: TransactionMetadata,
    val signature: String? = null
) {
    fun toNfcMessage(): ByteArray {
        val json = Json { 
            ignoreUnknownKeys = true
            isLenient = true
        }
        return json.encodeToString(this).toByteArray()
    }
    
    companion object {
        fun fromNfcMessage(data: ByteArray): OfflineTransaction? {
            return try {
                val json = Json { 
                    ignoreUnknownKeys = true
                    isLenient = true
                }
                json.decodeFromString<OfflineTransaction>(String(data))
            } catch (e: Exception) {
                null
            }
        }
    }
}

@Serializable
enum class OfflineTransactionType {
    PAYMENT_REQUEST,    // 請求付款
    PAYMENT_SEND,      // 發送付款
    ADDRESS_EXCHANGE,  // 交換地址
    SIGNATURE_REQUEST, // 請求簽名
    SIGNED_TX         // 已簽名交易
}

@Serializable
data class TransactionPayload(
    val fromAddress: String? = null,
    val toAddress: String? = null,
    val amount: String? = null,
    val token: String? = null,
    val chainId: String? = null,
    val nonce: Long? = null,
    val gasPrice: String? = null,
    val gasLimit: String? = null,
    val data: String? = null,
    val rawTransaction: String? = null
)

@Serializable
data class TransactionMetadata(
    val timestamp: Long = System.currentTimeMillis(),
    val deviceId: String? = null,
    val appVersion: String? = null,
    val expiresAt: Long? = null,
    val note: String? = null
)

/**
 * NFC 通訊狀態
 */
sealed class NfcTransferState {
    object Idle : NfcTransferState()
    object Scanning : NfcTransferState()
    object Connected : NfcTransferState()
    data class Sending(val progress: Float) : NfcTransferState()
    data class Receiving(val progress: Float) : NfcTransferState()
    data class Success(val transaction: OfflineTransaction) : NfcTransferState()
    data class Error(val message: String) : NfcTransferState()
}

/**
 * 離線交易驗證結果
 */
sealed class ValidationResult {
    object Valid : ValidationResult()
    data class Invalid(val reason: String) : ValidationResult()
}

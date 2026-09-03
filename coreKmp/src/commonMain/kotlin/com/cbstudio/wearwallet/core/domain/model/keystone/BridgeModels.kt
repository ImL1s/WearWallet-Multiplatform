package com.cbstudio.wearwallet.core.domain.model.keystone

import kotlinx.serialization.Serializable

/**
 * Keystone 橋接請求
 * 用於 watchOS 和 iOS 之間的通訊
 */
@Serializable
data class KeystoneBridgeRequest(
    val requestId: String,
    val requestType: BridgeRequestType,
    val payload: String,
    val nonce: String,
    val timestamp: Double,
    val timeout: Long = 60000,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Keystone 橋接響應
 */
@Serializable
data class KeystoneBridgeResponse(
    val requestId: String,
    val success: Boolean,
    val result: String? = null,
    val error: KeystoneBridgeError? = null,
    val timestamp: Double
)

/**
 * 橋接請求類型
 */
@Serializable
enum class BridgeRequestType {
    SIGN_TRANSACTION,
    GET_ACCOUNTS,
    VERIFY_ADDRESS,
    GET_EXTENDED_PUBKEY
}

/**
 * 橋接錯誤
 */
@Serializable
data class KeystoneBridgeError(
    val code: String,
    val message: String,
    val details: String? = null
)

/**
 * UR 編碼器接口（簡化版）
 */
interface UREncoder {
    fun isComplete(): Boolean
    fun nextPart(): String
}

/**
 * UR 解碼器接口（簡化版）
 */
interface URDecoder {
    fun addPart(part: String): Boolean
    fun isComplete(): Boolean
    fun getResult(): ByteArray?
    fun reset()
}

/**
 * UR 類型定義
 */
enum class URType {
    CRYPTO_PSBT,
    CRYPTO_HDKEY,
    ETH_SIGN_REQUEST,
    ETH_SIGNATURE
}
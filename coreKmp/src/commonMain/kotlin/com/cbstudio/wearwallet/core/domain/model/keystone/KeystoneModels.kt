package com.cbstudio.wearwallet.core.domain.model.keystone

import kotlinx.serialization.Serializable

/**
 * Keystone 操作結果封裝
 */
sealed class KeystoneResult<out T> {
    data class Success<T>(val data: T) : KeystoneResult<T>()
    data class Error(val error: KeystoneError) : KeystoneResult<Nothing>()
    
    inline fun <R> map(transform: (T) -> R): KeystoneResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> this
    }
    
    inline fun onSuccess(action: (T) -> Unit): KeystoneResult<T> {
        if (this is Success) action(data)
        return this
    }
    
    inline fun onError(action: (KeystoneError) -> Unit): KeystoneResult<T> {
        if (this is Error) action(error)
        return this
    }
}

/**
 * Keystone 錯誤類型
 */
sealed class KeystoneError(val message: String, val cause: Throwable? = null) {
    class InvalidQRCode(message: String) : KeystoneError("Invalid QR Code: $message")
    class UnsupportedURType(type: String) : KeystoneError("Unsupported UR type: $type")
    class ValidationFailed(message: String) : KeystoneError("Validation failed: $message")
    class SigningFailed(message: String) : KeystoneError("Signing failed: $message")
    class NetworkError(message: String, cause: Throwable? = null) : KeystoneError("Network error: $message", cause)
    class UnknownError(message: String, cause: Throwable? = null) : KeystoneError("Unknown error: $message", cause)
    
    // Additional error types for UR protocol
    class EncodingError(message: String) : KeystoneError("Encoding failed: $message")
    class DecodingError(message: String) : KeystoneError("Decoding failed: $message") 
    class InvalidInput(message: String) : KeystoneError("Invalid input: $message")
    class CombiningError(message: String) : KeystoneError("Combining failed: $message")
}

/**
 * Keystone 簽名請求數據類
 * 與 shared 模組保持完全一致的結構
 */
@Serializable
data class KeystoneSignRequest(
    val requestId: String,
    val qrCodeData: List<String>
)

/**
 * Keystone 簽名結果
 */
sealed class KeystoneSignatureResult {
    data class Success(
        val signature: String,
        val requestId: String
    ) : KeystoneSignatureResult()
    
    data class Incomplete(val message: String) : KeystoneSignatureResult()
    data class Error(val message: String) : KeystoneSignatureResult()
}

/**
 * Keystone 簽名響應
 */
@Serializable
data class KeystoneSignResponse(
    val requestId: String,
    val signature: String,
    val v: String = "",
    val r: String = "",
    val s: String = ""
)

/**
 * Keystone HD Key 結果
 */
sealed class KeystoneHDKeyResult {
    data class Success(
        val publicKey: String,
        val extendedPublicKey: String,
        val masterFingerprint: String,
        val path: String,
        val chainCode: String
    ) : KeystoneHDKeyResult()
    
    data class Incomplete(val message: String) : KeystoneHDKeyResult()
    data class Error(val message: String) : KeystoneHDKeyResult()
}

/**
 * Keystone HD Key 數據
 */
@Serializable
data class KeystoneHDKey(
    val name: String,
    val masterFingerprint: String,
    val xpub: String,
    val accounts: List<KeystoneAccount>
)

/**
 * Keystone 帳戶數據
 */
@Serializable
data class KeystoneAccount(
    val path: String,
    val xpub: String,
    val address: String,
    val chainId: String? = null
)

/**
 * Keystone 錢包資訊
 */
@Serializable
data class KeystoneWallet(
    val id: String,
    val name: String,
    val masterFingerprint: String,
    val addresses: List<KeystoneAddress>,
    val supportedChains: List<String>,
    val deviceInfo: KeystoneDeviceInfo? = null
) {
    fun getAddressForChain(chainId: String): KeystoneAddress? = addresses.find { it.chainId == chainId }
    fun supportsChain(chainId: String): Boolean = supportedChains.contains(chainId)
}

/**
 * Keystone 地址資訊
 */
@Serializable
data class KeystoneAddress(
    val address: String,
    val chainId: String,
    val derivationPath: String,
    val publicKey: String? = null,
    val addressType: AddressType = AddressType.LEGACY
)

/**
 * 地址類型
 */
@Serializable
enum class AddressType {
    LEGACY,        // 傳統地址
    SEGWIT,        // SegWit 地址  
    NATIVE_SEGWIT, // Native SegWit 地址
    TAPROOT        // Taproot 地址（Bitcoin）
}

/**
 * Keystone 設備資訊
 */
@Serializable
data class KeystoneDeviceInfo(
    val deviceType: String = "Keystone 3 Pro",
    val firmwareVersion: String? = null,
    val batteryLevel: Int? = null,
    val serialNumber: String? = null
)

/**
 * Keystone 交易資訊
 */
@Serializable
data class KeystoneTransaction(
    val to: String,
    val value: String,
    val data: String? = null,
    val gasPrice: String? = null,
    val gasLimit: String? = null,
    val nonce: String? = null,
    val chainId: String
)

/**
 * 簽名請求類型
 */
@Serializable
enum class SignRequestType {
    TRANSACTION,
    MESSAGE,
    TYPED_DATA
}

/**
 * Keystone 異常類
 */
class KeystoneException(message: String, cause: Throwable? = null) : Exception(message, cause)
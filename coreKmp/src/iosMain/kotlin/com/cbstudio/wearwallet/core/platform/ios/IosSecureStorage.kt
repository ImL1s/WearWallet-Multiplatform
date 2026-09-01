package com.cbstudio.wearwallet.core.platform.ios

import com.cbstudio.wearwallet.core.platform.SecureStorage
import kotlinx.cinterop.*
import platform.CoreFoundation.*
import platform.Foundation.*
import platform.Security.*

/**
 * iOS SecureStorage 實現
 * 使用 iOS Keychain 進行安全儲存
 */
@OptIn(ExperimentalForeignApi::class, UnsafeNumber::class)
class IosSecureStorage : SecureStorage {
    
    private val service = "com.cbstudio.wearwallet"
    
    override suspend fun encrypt(plainText: String): String {
        // iOS Keychain 本身就是加密儲存，直接返回原文
        return plainText
    }
    
    override suspend fun decrypt(encryptedText: String): String {
        // iOS Keychain 本身就是加密儲存，直接返回原文
        return encryptedText
    }
    
    override suspend fun saveSecure(key: String, value: String) {
        val valueData = value.encodeToByteArray().toNSData()
        
        // 先嘗試刪除舊值  
        withKeychainQuery({
            set(kSecClass, kSecClassGenericPassword)
            set(kSecAttrService, service)
            set(kSecAttrAccount, key)
        }) { query ->
            SecItemDelete(query)
        }
        
        // 添加新值
        val status = withKeychainQuery({
            set(kSecClass, kSecClassGenericPassword)
            set(kSecAttrService, service)
            set(kSecAttrAccount, key)
            set(kSecValueData, valueData)
            set(kSecAttrAccessible, kSecAttrAccessibleWhenUnlockedThisDeviceOnly)
        }) { query ->
            SecItemAdd(query, null)
        }
        
        if (status != errSecSuccess && status != errSecDuplicateItem) {
            throw Exception("Failed to save to keychain: $status")
        }
    }
    
    override suspend fun getSecure(key: String): String? {
        return memScoped {
            val result = alloc<COpaquePointerVar>()
            val status = withKeychainQuery({
                set(kSecClass, kSecClassGenericPassword)
                set(kSecAttrService, service)
                set(kSecAttrAccount, key)
                set(kSecReturnData, kCFBooleanTrue)
                set(kSecMatchLimit, kSecMatchLimitOne)
            }) { query ->
                SecItemCopyMatching(query, result.ptr)
            }
            
            when (status) {
                errSecSuccess -> {
                    if (result.value != null) {
                        val data = CFBridgingRelease(result.value) as? NSData
                        data?.toByteArray()?.decodeToString()
                    } else null
                }
                errSecItemNotFound -> null
                else -> throw Exception("Failed to read from keychain: $status")
            }
        }
    }
    
    override suspend fun removeSecure(key: String) {
        val status = withKeychainQuery({
            set(kSecClass, kSecClassGenericPassword)
            set(kSecAttrService, service)
            set(kSecAttrAccount, key)
        }) { query ->
            SecItemDelete(query)
        }
        
        if (status != errSecSuccess && status != errSecItemNotFound) {
            throw Exception("Failed to delete from keychain: $status")
        }
    }
    
    override suspend fun hasKey(key: String): Boolean {
        val status = withKeychainQuery({
            set(kSecClass, kSecClassGenericPassword)
            set(kSecAttrService, service)
            set(kSecAttrAccount, key)
            set(kSecReturnData, kCFBooleanFalse)
            set(kSecMatchLimit, kSecMatchLimitOne)
        }) { query ->
            SecItemCopyMatching(query, null)
        }
        return status == errSecSuccess
    }

    private class KeychainQueryBuilder {
        private val dict = NSMutableDictionary()

        fun set(key: CFStringRef?, value: Any?) {
            if (key == null || value == null) return
            val bridgedKey = CFBridgingRelease(CFRetain(key)) ?: return
            val bridgedValue = when (value) {
                is String -> value as NSString
                is Boolean -> if (value) CFBridgingRelease(CFRetain(kCFBooleanTrue)) else CFBridgingRelease(CFRetain(kCFBooleanFalse))
                is CPointer<*> -> CFBridgingRelease(CFRetain(value)) ?: return
                else -> value
            } ?: return
            dict.setObject(bridgedValue, forKeyedSubscript = bridgedKey as platform.Foundation.NSCopyingProtocol)
        }

        fun build(): CFDictionaryRef? {
            return CFBridgingRetain(dict)?.reinterpret()
        }
    }

    private inline fun <R> withKeychainQuery(
        block: KeychainQueryBuilder.() -> Unit,
        action: (CFDictionaryRef?) -> R
    ): R {
        val builder = KeychainQueryBuilder().apply(block)
        val cfDict = builder.build()
        return try {
            action(cfDict)
        } finally {
            if (cfDict != null) {
                CFBridgingRelease(cfDict)
            }
        }
    }
}

// Extension functions for conversion
@OptIn(ExperimentalForeignApi::class, UnsafeNumber::class)
private fun ByteArray.toNSData(): NSData {
    return memScoped {
        NSData.create(bytes = allocArrayOf(this@toNSData), length = this@toNSData.size.convert())
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val length = this.length
    return ByteArray(length.toInt()).apply {
        usePinned {
            platform.posix.memcpy(it.addressOf(0), this@toByteArray.bytes, length)
        }
    }
}
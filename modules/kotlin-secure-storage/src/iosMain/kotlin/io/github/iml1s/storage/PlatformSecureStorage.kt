package io.github.iml1s.storage

import kotlinx.cinterop.*
import platform.Foundation.*
import platform.Security.*
import platform.darwin.NSObject

/**
 * iOS implementation of PlatformContext (placeholder)
 */
actual class PlatformContext

/**
 * iOS implementation of SecureStorage using Keychain Services.
 */
actual class PlatformSecureStorage actual constructor(platformContext: PlatformContext) : SecureStorage {

    override suspend fun put(key: String, value: String) {
        val query = createQuery(key)
        
        // Convert string to NSData
        val data = (value as NSString).dataUsingEncoding(NSUTF8StringEncoding)
        
        // Add data to query attributes
        query[kSecValueData as String] = data!!

        // Attempt to add item
        val status = SecItemAdd(query as CFDictionaryRef, null)
        
        if (status == errSecDuplicateItem) {
            // Item already exists, update it
            val updateQuery = createQuery(key)
            val attributesToUpdate = mutableMapOf<Any?, Any?>()
            attributesToUpdate[kSecValueData as String] = data
            
            SecItemUpdate(updateQuery as CFDictionaryRef, attributesToUpdate as CFDictionaryRef)
        }
    }

    override suspend fun get(key: String): String? {
        val query = createQuery(key)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne

        val result = memScoped {
            val resultRef = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query as CFDictionaryRef, resultRef.ptr)
            if (status == errSecSuccess) {
                val data = resultRef.value?.let { ObjCObjectVar<NSData>(it.reinterpret()).value }
                // Convert NSData to String
                val string = NSString.create(data!!, NSUTF8StringEncoding) as String?
                return@memScoped string
            }
            return@memScoped null
        }
        return result
    }

    override suspend fun delete(key: String) {
        val query = createQuery(key)
        SecItemDelete(query as CFDictionaryRef)
    }

    override suspend fun clear() {
        // Clear all items for this app class
        val query = mutableMapOf<Any?, Any?>()
        query[kSecClass as String] = kSecClassGenericPassword
        SecItemDelete(query as CFDictionaryRef)
    }

    private fun createQuery(key: String): MutableMap<Any?, Any?> {
        val query = mutableMapOf<Any?, Any?>()
        query[kSecClass as String] = kSecClassGenericPassword
        query[kSecAttrAccount as String] = key
        // Ensure we only access items we created or have access to
        query[kSecAttrService as String] = "io.github.iml1s.wallet" 
        return query
    }
}

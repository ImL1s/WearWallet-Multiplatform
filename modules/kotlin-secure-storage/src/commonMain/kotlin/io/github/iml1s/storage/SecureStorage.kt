package io.github.iml1s.storage

// Common
expect class PlatformContext

interface SecureStorage {
    suspend fun put(key: String, value: String)
    suspend fun get(key: String): String?
    suspend fun delete(key: String)
    suspend fun clear()
}

expect class PlatformSecureStorage(platformContext: PlatformContext) : SecureStorage

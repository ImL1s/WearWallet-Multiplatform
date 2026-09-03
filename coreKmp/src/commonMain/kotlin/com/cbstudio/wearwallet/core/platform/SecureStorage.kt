package com.cbstudio.wearwallet.core.platform

/**
 * 安全儲存初始化異常 (Fail-Closed)
 */
open class SecureStorageInitializationException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

/**
 * 安全儲存介面
 */
interface SecureStorage {
    /**
     * 加密字串
     */
    suspend fun encrypt(plainText: String): String
    
    /**
     * 解密字串
     */
    suspend fun decrypt(encryptedText: String): String
    
    /**
     * 儲存加密資料
     */
    suspend fun saveSecure(key: String, value: String)
    
    /**
     * 讀取加密資料
     */
    suspend fun getSecure(key: String): String?
    
    /**
     * 刪除加密資料
     */
    suspend fun removeSecure(key: String)
    
    /**
     * 檢查是否存在
     */
    suspend fun hasKey(key: String): Boolean
}
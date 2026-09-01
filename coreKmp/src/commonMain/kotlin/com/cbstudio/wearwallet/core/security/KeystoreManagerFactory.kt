package com.cbstudio.wearwallet.core.security

/**
 * KeystoreManager 工廠介面
 * 用於解決 expect class 無法直接在 Koin 中實例化的問題
 */
expect object KeystoreManagerFactory {
    fun create(): KeystoreManager
}
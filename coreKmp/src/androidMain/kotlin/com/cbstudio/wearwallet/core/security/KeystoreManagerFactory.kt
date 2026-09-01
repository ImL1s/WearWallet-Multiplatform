package com.cbstudio.wearwallet.core.security

/**
 * Android 平台的 KeystoreManager 工廠實現
 */
actual object KeystoreManagerFactory {
    actual fun create(): KeystoreManager = KeystoreManager()
}
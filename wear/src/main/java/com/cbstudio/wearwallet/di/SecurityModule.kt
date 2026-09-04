package com.cbstudio.wearwallet.di

import org.koin.dsl.module
import com.cbstudio.wearwallet.core.security.CryptoProvider
import com.cbstudio.wearwallet.core.platform.android.AndroidCryptoProvider
import com.cbstudio.wearwallet.core.platform.SecureStorage
import com.cbstudio.wearwallet.core.platform.android.AndroidSecureStorage

/**
 * Security DI Module
 * Restored for Real Module Usage
 */
val securityModule = module {
    single<CryptoProvider> { AndroidCryptoProvider(get()) }
    single<SecureStorage> { AndroidSecureStorage(get()) }
}
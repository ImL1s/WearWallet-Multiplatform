package com.cbstudio.wearwallet.core.di

import com.cbstudio.wearwallet.core.database.DatabaseDriverFactory
import com.cbstudio.wearwallet.core.database.CoreWalletDatabase
import com.cbstudio.wearwallet.core.domain.repository.*
import com.cbstudio.wearwallet.core.data.repository.*
import com.cbstudio.wearwallet.core.platform.SecureStorage
import com.cbstudio.wearwallet.core.platform.android.*
import com.cbstudio.wearwallet.core.security.CryptoProvider
import com.cbstudio.wearwallet.core.security.SecureKeyManager
import com.cbstudio.wearwallet.core.security.AndroidSecureKeyManager
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Android Repository 實現
 */
actual val platformRepositoryModule: Module = module {
    // Database
    single { DatabaseDriverFactory(androidContext()) }
    single { CoreWalletDatabase(get<DatabaseDriverFactory>().createDriver()) }
    
    // Core repositories - 使用真實實現與 SQLDelight
    single<WalletRepository> { 
        WalletRepositoryImpl(
            databaseDriverFactory = get(),
            cryptoProvider = get(),
            ethereumRpcClient = get(),
            secureKeyManager = get(),
            platformDeletionCleanupHook = get()
        )
    }
    single<TokenRepository> { TokenRepositoryImpl(get(), get()) }
    single<TransactionRepository> { 
        TransactionRepositoryImpl(
            cryptoProvider = get(),
            rpcClient = get(),
            httpClient = get(),
            utxoApiClient = get()
        )
    }
    
    // 新增的 CRUD repositories (使用共享的 CoreWalletDatabase)
    single<AddressBookRepository> { 
        AddressBookRepositoryImpl(get<CoreWalletDatabase>())
    }
    single<NftRepository> { 
        NftRepositoryImpl(get<CoreWalletDatabase>())
    }
    single<PriceAlertRepository> { 
        PriceAlertRepositoryImpl(get<CoreWalletDatabase>())
    }
    
    // 通知系列 repositories (使用 CoreWalletDatabase)
    single { NotificationHistoryRepository(get<CoreWalletDatabase>()) }
    single { NotificationPreferencesRepository(get<CoreWalletDatabase>()) }
    single { PushSubscriptionRepository(get<CoreWalletDatabase>()) }
}

/**
 * Android Platform Provider 實現
 */
actual val platformProviderModule: Module = module {
    includes(androidNetworkModule)
    single<CryptoProvider> { AndroidCryptoProvider() }
    single<SecureStorage> { AndroidSecureStorage(get()) }
    single<SecureKeyManager> { AndroidSecureKeyManager(androidContext()) }
    single<com.cbstudio.wearwallet.core.security.PlatformProvider> { com.cbstudio.wearwallet.core.security.AndroidPlatformProvider(androidContext()) }
    single<com.cbstudio.wearwallet.core.security.BuildTypeProvider> { com.cbstudio.wearwallet.core.security.AndroidBuildTypeProvider() }
}

/**
 * Android 區塊鏈模組實現
 */
actual val blockchainModule: Module = androidBlockchainModule

/**
 * Android 網路模組
 * 提供配置了證書固定的 HTTP 客戶端
 */
val androidNetworkModuleProvider: Module
    get() = androidNetworkModule
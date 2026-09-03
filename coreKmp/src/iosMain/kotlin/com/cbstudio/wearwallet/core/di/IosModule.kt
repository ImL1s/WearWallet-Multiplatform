package com.cbstudio.wearwallet.core.di

import com.cbstudio.wearwallet.core.database.CoreWalletDatabase
import com.cbstudio.wearwallet.core.database.DatabaseDriverFactory
import com.cbstudio.wearwallet.core.domain.repository.*
import com.cbstudio.wearwallet.core.data.repository.*
import com.cbstudio.wearwallet.core.platform.SecureStorage
import com.cbstudio.wearwallet.core.platform.ios.IosSecureStorage
import com.cbstudio.wearwallet.core.platform.ios.IosCryptoProvider
import com.cbstudio.wearwallet.core.security.CryptoProvider
import com.cbstudio.wearwallet.core.security.SecureKeyManager
import com.cbstudio.wearwallet.core.security.IOSSecureKeyManager
import com.cbstudio.wearwallet.core.network.EthereumRpcClient
import io.ktor.serialization.kotlinx.json.json
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * iOS 平台特定的 Repository 模組
 */
actual val platformRepositoryModule: Module = module {
    // Database Driver and Instance
    single { DatabaseDriverFactory() }
    single { CoreWalletDatabase(get<DatabaseDriverFactory>().createDriver()) }

    // ✅ 真實的 SQLDelight Repository 實現
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
    
    // Core Transaction Repository (Moved from Android to Common)
    single<TransactionRepository> { 
        TransactionRepositoryImpl(
            cryptoProvider = get(),
            rpcClient = get(),
            httpClient = get(),
            utxoApiClient = get()
        )
    }
    
    // CRUD Repositories
    single<AddressBookRepository> { AddressBookRepositoryImpl(get()) }
    single<NftRepository> { NftRepositoryImpl(get()) }
    single<PriceAlertRepository> { PriceAlertRepositoryImpl(get()) }
    
    // Notification Repositories
    single { NotificationHistoryRepository(get()) }
    single { NotificationPreferencesRepository(get()) }
    single { PushSubscriptionRepository(get()) }
}

/**
 * iOS 平台特定的 Provider 模組
 */
actual val platformProviderModule: Module = module {
    single<CryptoProvider> { IosCryptoProvider() }
    single<SecureStorage> { IosSecureStorage() }
    single<SecureKeyManager> { IOSSecureKeyManager() }
    single<com.cbstudio.wearwallet.core.security.PlatformProvider> { com.cbstudio.wearwallet.core.security.IOSPlatformProvider() }
    single<com.cbstudio.wearwallet.core.security.BuildTypeProvider> { com.cbstudio.wearwallet.core.security.IOSBuildTypeProvider() }
    single<com.cbstudio.wearwallet.core.platform.PlatformDeletionCleanupHook> { 
        com.cbstudio.wearwallet.core.platform.ios.IosPlatformDeletionCleanupHook() 
    }
    
    // HTTP Client
    single {
        io.ktor.client.HttpClient(io.ktor.client.engine.darwin.Darwin) {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                val jsonConfig = kotlinx.serialization.json.Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    prettyPrint = false
                }
                json(jsonConfig)
            }
            install(io.ktor.client.plugins.logging.Logging) {
                level = io.ktor.client.plugins.logging.LogLevel.INFO
            }
        }
    }
    
    // RPC Client
    single { EthereumRpcClient(get()) }
}

/**
 * iOS 區塊鏈模組實現 - 暫時空實現
 */
actual val blockchainModule: Module = module {
    // iOS 平台的區塊鏈適配器將在後續實現
}
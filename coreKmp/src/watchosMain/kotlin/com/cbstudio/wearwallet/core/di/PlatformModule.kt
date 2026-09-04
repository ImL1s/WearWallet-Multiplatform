package com.cbstudio.wearwallet.core.di

import com.cbstudio.wearwallet.core.data.repository.*

import com.cbstudio.wearwallet.core.database.CoreWalletDatabase
import com.cbstudio.wearwallet.core.database.DatabaseDriverFactory
import com.cbstudio.wearwallet.core.domain.repository.*
import com.cbstudio.wearwallet.core.platform.watchos.WatchOSCryptoProvider
import com.cbstudio.wearwallet.core.security.CryptoProvider
import com.cbstudio.wearwallet.core.security.SecureKeyManager
import com.cbstudio.wearwallet.core.security.WatchOSSecureKeyManager
import org.koin.core.module.Module
import org.koin.dsl.module
import io.ktor.serialization.kotlinx.json.json

/**
 * watchOS 平台專用的 Repository 模組
 */
actual val platformRepositoryModule: Module = module {
    // Database Driver and Instance
    single { DatabaseDriverFactory() }
    single {
        CoreWalletDatabase(get<DatabaseDriverFactory>().createDriver())
    }

    single<WalletRepository> { 
        WalletRepositoryImpl(
            databaseDriverFactory = get<DatabaseDriverFactory>(), 
            cryptoProvider = get<CryptoProvider>(), 
            ethereumRpcClient = get<com.cbstudio.wearwallet.core.network.EthereumRpcClient>(),
            secureKeyManager = get<SecureKeyManager>(),
            platformDeletionCleanupHook = get()
        ) 
    }
    single<TokenRepository> { TokenRepositoryImpl(get(), get(), get()) }
    single<TransactionRepository> {
        TransactionRepositoryImpl(
            cryptoProvider = get(),
            rpcClient = get(),
            httpClient = get(),
            utxoApiClient = get()
        )
    }

    // 其他 CRUD Repositories
    single<AddressBookRepository> { AddressBookRepositoryImpl(get()) }
    single<NftRepository> { NftRepositoryImpl(get()) }
    single<PriceAlertRepository> { PriceAlertRepositoryImpl(get()) }

    // Notification repositories
    single { NotificationHistoryRepository(get()) }
    single { NotificationPreferencesRepository(get()) }
    single { PushSubscriptionRepository(get()) }
}

/**
 * watchOS 平台專用的 Provider 模組
 */
actual val platformProviderModule: Module = module {
    // Crypto Provider - watchOS 專用實現
    single<CryptoProvider> { WatchOSCryptoProvider() }
    single<SecureKeyManager> { WatchOSSecureKeyManager() }
    single<com.cbstudio.wearwallet.core.platform.PlatformDeletionCleanupHook> {
        com.cbstudio.wearwallet.core.platform.watchos.WatchOSPlatformDeletionCleanupHook()
    }
    
    single {
        io.ktor.client.HttpClient(io.ktor.client.engine.darwin.Darwin) {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json(kotlinx.serialization.json.Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    prettyPrint = false
                })
            }
            install(io.ktor.client.plugins.logging.Logging) {
                level = io.ktor.client.plugins.logging.LogLevel.INFO
            }
        }
    }

    single { com.cbstudio.wearwallet.core.network.EthereumRpcClient(get()) }
}

/**
 * watchOS 區塊鏈模組實現 - 暫時空實現
 */
actual val blockchainModule: Module = module {
    // watchOS 平台的區塊鏈適配器將在後續實現
}
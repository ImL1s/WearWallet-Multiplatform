package com.cbstudio.wearwallet.core.di

import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.dsl.module
import com.cbstudio.wearwallet.core.domain.repository.*
import com.cbstudio.wearwallet.core.domain.usecase.wallet.*
import com.cbstudio.wearwallet.core.domain.usecase.transaction.*
import com.cbstudio.wearwallet.core.domain.usecase.token.*
import com.cbstudio.wearwallet.core.domain.usecase.price.*
import com.cbstudio.wearwallet.core.domain.usecase.addressbook.*
import com.cbstudio.wearwallet.core.domain.usecase.nft.*
import com.cbstudio.wearwallet.core.domain.usecase.pricealert.*
import com.cbstudio.wearwallet.core.domain.usecase.notification.*
import com.cbstudio.wearwallet.core.domain.usecase.contact.*
import com.cbstudio.wearwallet.core.domain.usecase.bitcoin.*
import com.cbstudio.wearwallet.core.domain.usecase.utxo.*
import com.cbstudio.wearwallet.core.security.CryptoProvider
import com.cbstudio.wearwallet.core.security.KeystoreManager

/**
 * watchOS 平台的 Koin 初始化 (Duplicate of KoinIos.kt for watchOS target)
 * 
 * 提供 KoinApplication 給 Swift 使用
 * 確保所有 UseCase 都能被 Swift 存取
 */
fun initKoinIos(
    appModule: org.koin.core.module.Module? = null
): KoinApplication {
    return startKoin {
        // 載入所有核心模組
        val allModules = listOfNotNull(
            // Core modules with UseCases
            coreModule,
            
            // Platform specific modules
            platformRepositoryModule,
            platformProviderModule,
            blockchainModule,
            
            // App specific module if provided
            appModule
        )
        
        modules(allModules)
    }.also {
        // 驗證 UseCase 註冊 (Simplified verifying for watchOS)
    }
}

/**
 * Wrapper for initialization to avoid file facade issues in Swift
 */
object KoinInitializer {
    fun start(appModule: Any? = null): Any {
        return initKoinIos(appModule as? org.koin.core.module.Module)
    }
}

/**
 * Helper class to expose Koin to Swift (Naming matches iOS for compatibility)
 */
class KoinIosHelper(koinAppAny: Any) {
    val koin = (koinAppAny as KoinApplication).koin
    
    // Repositories
    fun getWalletRepository() = koin.get<WalletRepository>()
    fun getTokenRepository() = koin.get<TokenRepository>()
    fun getNftRepository() = koin.get<NftRepository>()
    fun getPriceAlertRepository() = koin.get<PriceAlertRepository>()
    
    // Providers
    fun getCryptoProvider() = koin.get<CryptoProvider>()
    fun getKeystoreManager() = koin.get<KeystoreManager>()
    
    // Wallet UseCases
    fun getCreateWalletUseCase() = koin.get<CreateWalletUseCase>()
    fun getImportWalletUseCase() = koin.get<ImportWalletUseCase>()
    
    // Transaction UseCases
    fun getSendTransactionUseCase() = koin.get<SendTransactionUseCase>()
    fun getEstimateGasUseCase() = koin.get<EstimateGasUseCase>()
    fun getGetTransactionHistoryUseCase() = koin.get<GetTransactionHistoryUseCase>()
    fun getEstimateTransactionUseCase() = koin.get<EstimateTransactionUseCase>()
    
    // Token UseCases
    fun getScanTokensUseCase() = koin.get<ScanTokensUseCase>()
    fun getGetUserTokensUseCase() = koin.get<GetUserTokensUseCase>()
    
    // Price UseCases
    fun getGetTokenPriceUseCase() = koin.get<GetTokenPriceUseCase>()
    
    // AddressBook UseCases
    fun getAddAddressContactUseCase() = koin.get<AddAddressContactUseCase>()
    fun getSearchAddressBookUseCase() = koin.get<SearchAddressBookUseCase>()
    fun getGetAddressContactsUseCase() = koin.get<GetAddressContactsUseCase>()
    fun getUpdateAddressContactUseCase() = koin.get<UpdateAddressContactUseCase>()
    
    // PriceAlert UseCases
    fun getManagePriceAlertsUseCase() = koin.get<ManagePriceAlertsUseCase>()
    
    // Notification UseCases
    fun getManageNotificationsUseCase() = koin.get<ManageNotificationsUseCase>()
    
    // Contact UseCases
    fun getAddContactUseCase() = koin.get<AddContactUseCase>()
    fun getGetAllContactsUseCase() = koin.get<GetAllContactsUseCase>()
    fun getGetContactByIdUseCase() = koin.get<GetContactByIdUseCase>()
    fun getUpdateContactUseCase() = koin.get<UpdateContactUseCase>()
    fun getDeleteContactUseCase() = koin.get<DeleteContactUseCase>()
    
    // NFT UseCases
    fun getGetNftsUseCase() = koin.get<GetNftsUseCase>()
    fun getManageNftsUseCase() = koin.get<ManageNftsUseCase>()

    // Bitcoin UseCases
    fun getSendBitcoinTransactionUseCase() = koin.get<SendBitcoinTransactionUseCase>()

    // UTXO UseCases
    fun getSendUTXOTransactionUseCase() = koin.get<SendUTXOTransactionUseCase>()
    fun getGetUTXOTransactionHistoryUseCase() = koin.get<GetUTXOTransactionHistoryUseCase>()
    
    // UTXO ApiClient
    fun getUTXOApiClient() = koin.get<com.cbstudio.wearwallet.core.blockchain.api.UTXOApiClient>()
    
    // DeFi Aggregator
    fun getDeFiAggregator() = koin.get<com.cbstudio.wearwallet.core.multichain.defi.DeFiAggregator>()
    
    // Rango Metadata
    fun getRangoMetadataRepository() = koin.get<com.cbstudio.wearwallet.core.rango.RangoMetadataRepository>()
}

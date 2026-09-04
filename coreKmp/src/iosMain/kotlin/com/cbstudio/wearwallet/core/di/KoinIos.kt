package com.cbstudio.wearwallet.core.di

import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.dsl.module

/**
 * iOS/watchOS 平台的 Koin 初始化
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
            
            // App specific module if provided
            appModule
        )
        
        modules(allModules)
    }.also {
        // 驗證 UseCase 註冊
        verifyUseCaseRegistration(it)
    }
}

/**
 * 驗證所有 UseCase 是否正確註冊
 */
private fun verifyUseCaseRegistration(koinApp: KoinApplication) {
    val koin = koinApp.koin
    
    try {
        // Wallet UseCases
        koin.get<com.cbstudio.wearwallet.core.domain.usecase.wallet.CreateWalletUseCase>()
        koin.get<com.cbstudio.wearwallet.core.domain.usecase.wallet.ImportWalletUseCase>()
        
        // Transaction UseCases
        koin.get<com.cbstudio.wearwallet.core.domain.usecase.transaction.SendTransactionUseCase>()
        koin.get<com.cbstudio.wearwallet.core.domain.usecase.transaction.EstimateGasUseCase>()
        
        // Token UseCases
        koin.get<com.cbstudio.wearwallet.core.domain.usecase.token.ScanTokensUseCase>()
        
        // Price UseCases
        koin.get<com.cbstudio.wearwallet.core.domain.usecase.price.GetTokenPriceUseCase>()
        
        println("[KoinIos] ✅ All UseCases are registered successfully")
    } catch (e: Exception) {
        println("[KoinIos] ❌ UseCase registration failed: ${e.message}")
    }
}

/**
 * Helper class to expose Koin to Swift
 */
class KoinIosHelper(private val koinApp: KoinApplication) {
    val koin = koinApp.koin
    
    // Wallet UseCases
    fun getCreateWalletUseCase() = koin.get<com.cbstudio.wearwallet.core.domain.usecase.wallet.CreateWalletUseCase>()
    fun getImportWalletUseCase() = koin.get<com.cbstudio.wearwallet.core.domain.usecase.wallet.ImportWalletUseCase>()
    
    // Transaction UseCases
    fun getSendTransactionUseCase() = koin.get<com.cbstudio.wearwallet.core.domain.usecase.transaction.SendTransactionUseCase>()
    fun getEstimateGasUseCase() = koin.get<com.cbstudio.wearwallet.core.domain.usecase.transaction.EstimateGasUseCase>()
    
    // Token UseCases
    fun getScanTokensUseCase() = koin.get<com.cbstudio.wearwallet.core.domain.usecase.token.ScanTokensUseCase>()
    
    // Price UseCases
    fun getGetTokenPriceUseCase() = koin.get<com.cbstudio.wearwallet.core.domain.usecase.price.GetTokenPriceUseCase>()
    
    // Contact UseCases (if needed)
    fun getAddContactUseCase() = koin.get<com.cbstudio.wearwallet.core.domain.usecase.contact.AddContactUseCase>()
    fun getGetAllContactsUseCase() = koin.get<com.cbstudio.wearwallet.core.domain.usecase.contact.GetAllContactsUseCase>()
    fun getGetContactByIdUseCase() = koin.get<com.cbstudio.wearwallet.core.domain.usecase.contact.GetContactByIdUseCase>()
    fun getUpdateContactUseCase() = koin.get<com.cbstudio.wearwallet.core.domain.usecase.contact.UpdateContactUseCase>()
    fun getDeleteContactUseCase() = koin.get<com.cbstudio.wearwallet.core.domain.usecase.contact.DeleteContactUseCase>()
    
    // NFT UseCases (if needed)
    fun getGetNftsUseCase() = koin.get<com.cbstudio.wearwallet.core.domain.usecase.nft.GetNftsUseCase>()
    fun getManageNftsUseCase() = koin.get<com.cbstudio.wearwallet.core.domain.usecase.nft.ManageNftsUseCase>()

    // UTXO UseCases
    fun getSendUTXOTransactionUseCase() = koin.get<com.cbstudio.wearwallet.core.domain.usecase.utxo.SendUTXOTransactionUseCase>()
    fun getGetUTXOTransactionHistoryUseCase() = koin.get<com.cbstudio.wearwallet.core.domain.usecase.utxo.GetUTXOTransactionHistoryUseCase>()
    
    // UTXO ApiClient (for balance)
    fun getUTXOApiClient() = koin.get<com.cbstudio.wearwallet.core.blockchain.api.UTXOApiClient>()
    
    // DeFi Aggregator
    fun getDeFiAggregator() = koin.get<com.cbstudio.wearwallet.core.multichain.defi.DeFiAggregator>()
    
    // Rango Metadata
    fun getRangoMetadataRepository() = koin.get<com.cbstudio.wearwallet.core.rango.RangoMetadataRepository>()

    // Direct Repository Access (for Lists)
    fun getPriceAlertRepository() = koin.get<com.cbstudio.wearwallet.core.domain.repository.PriceAlertRepository>()
    fun getNftRepository() = koin.get<com.cbstudio.wearwallet.core.domain.repository.NftRepository>()
}
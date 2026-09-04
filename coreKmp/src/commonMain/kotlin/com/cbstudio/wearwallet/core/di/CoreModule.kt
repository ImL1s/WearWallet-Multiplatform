package com.cbstudio.wearwallet.core.di

import com.cbstudio.wearwallet.core.data.repository.WalletRepositoryImpl
import com.cbstudio.wearwallet.core.data.repository.TokenRepositoryImpl
import com.cbstudio.wearwallet.core.data.repository.AddressBookRepositoryImpl
import com.cbstudio.wearwallet.core.data.repository.NftRepositoryImpl
import com.cbstudio.wearwallet.core.data.repository.PriceAlertRepositoryImpl
import com.cbstudio.wearwallet.core.data.repository.NotificationHistoryRepository
import com.cbstudio.wearwallet.core.data.repository.NotificationPreferencesRepository
import com.cbstudio.wearwallet.core.data.repository.PushSubscriptionRepository
import com.cbstudio.wearwallet.core.data.repository.ContactRepositoryImpl
import com.cbstudio.wearwallet.core.domain.repository.WalletRepository
import com.cbstudio.wearwallet.core.domain.repository.TransactionRepository
import com.cbstudio.wearwallet.core.domain.repository.TokenRepository
import com.cbstudio.wearwallet.core.platform.SecureStorage
import com.cbstudio.wearwallet.core.security.CapabilityGate
import com.cbstudio.wearwallet.core.security.SecureKeyManager
import com.cbstudio.wearwallet.core.domain.repository.AddressBookRepository
import com.cbstudio.wearwallet.core.domain.repository.NftRepository
import com.cbstudio.wearwallet.core.domain.repository.PriceAlertRepository
import com.cbstudio.wearwallet.core.domain.repository.ContactRepository
import com.cbstudio.wearwallet.core.domain.usecase.wallet.*
import com.cbstudio.wearwallet.core.domain.usecase.transaction.*
import com.cbstudio.wearwallet.core.domain.usecase.price.*
import com.cbstudio.wearwallet.core.domain.usecase.addressbook.*
import com.cbstudio.wearwallet.core.domain.usecase.nft.*
import com.cbstudio.wearwallet.core.domain.usecase.pricealert.*
import com.cbstudio.wearwallet.core.domain.usecase.notification.*
import com.cbstudio.wearwallet.core.domain.usecase.contact.*
import com.cbstudio.wearwallet.core.recovery.StartupRecoveryCoordinator
import com.cbstudio.wearwallet.core.recovery.RealStartupRecoveryCoordinator
import com.cbstudio.wearwallet.core.domain.usecase.bitcoin.*
import com.cbstudio.wearwallet.core.domain.usecase.utxo.*
import com.cbstudio.wearwallet.core.blockchain.adapter.BitcoinPlatformAdapter
import com.cbstudio.wearwallet.core.blockchain.api.BlockstreamApiClient
import com.cbstudio.wearwallet.core.blockchain.api.UTXOApiClient
import com.cbstudio.wearwallet.core.blockchain.signer.BitcoinSigner
import com.cbstudio.wearwallet.core.blockchain.signer.LitecoinSigner
import com.cbstudio.wearwallet.core.blockchain.signer.DogecoinSigner
import com.cbstudio.wearwallet.core.blockchain.signer.BitcoinCashSigner
import com.cbstudio.wearwallet.core.blockchain.utxo.AddressDerivation
import com.cbstudio.wearwallet.core.blockchain.utxo.UTXOAddressManager
import com.cbstudio.wearwallet.core.domain.model.Network
import com.cbstudio.wearwallet.core.keystone.KeystoneManager
import com.cbstudio.wearwallet.core.domain.service.KeystoneService
import com.cbstudio.wearwallet.core.network.*
import com.cbstudio.wearwallet.core.security.CryptoProvider
import com.cbstudio.wearwallet.core.security.PrivateKeyManager
import com.cbstudio.wearwallet.core.security.KeystoreManager
import com.cbstudio.wearwallet.core.security.KeystoreManagerFactory
import com.cbstudio.wearwallet.core.security.SideEffectTracker
import com.cbstudio.wearwallet.core.security.NoOpSideEffectTracker
import com.cbstudio.wearwallet.core.security.GlobalSideEffectTracker
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Core DI Module
 */
fun provideKeystoneService(): com.cbstudio.wearwallet.core.domain.service.KeystoneService = com.cbstudio.wearwallet.core.domain.service.KeystoneService()

val coreModule: Module = module {
    // Network Clients
    // Network Clients
    // HttpClient instance is provided by platform modules (AndroidModule, IosModule, PlatformModule)
    // to ensure correct engine (OkHttp, Darwin) usage.
    
    single { PriceApiClient(get()) }
    single { SmartContractClient(get()) }
    single { EthereumRpcClient(get(), get()) }
    
    // Crypto Provider and Security
    // CryptoProvider is bound only by platformProviderModule (Android/iOS/watchOS).
    // Do not bind CommonCryptoProvider here — it is a helper for tests/mnemonic utilities,
    // not a production signing implementation.
    single<SideEffectTracker> { GlobalSideEffectTracker.instance }
    single<com.cbstudio.wearwallet.core.security.SecurityAuditLogger> { com.cbstudio.wearwallet.core.security.GlobalSecurityAuditLogger.instance }
    single<com.cbstudio.wearwallet.core.security.CapabilityGate> { com.cbstudio.wearwallet.core.security.ReleaseProductionCapabilityGate() }
    single<com.cbstudio.wearwallet.core.security.BackendAttestationProvider> { com.cbstudio.wearwallet.core.security.DefaultBackendAttestationProvider() }
    // 使用工廠來創建 KeystoreManager
    single { KeystoreManagerFactory.create() }
    single { PrivateKeyManager(get()) }
    
    // Core Repositories (defined in platformRepositoryModule for platform-specific implementation)
    // single<WalletRepository> { WalletRepositoryImpl(get(), get(), get()) }  // Moved to AndroidModule
    single<TokenRepository> { TokenRepositoryImpl(get(), get(), get()) }
    single<ContactRepository> { ContactRepositoryImpl() }
    
    // Startup Recovery Coordinator (Milestone 4 / P1-2)
    single<StartupRecoveryCoordinator> { RealStartupRecoveryCoordinator(get()) }
    single { RealStartupRecoveryCoordinator(get()) }
    
    // Keystone Hardware Wallet
    single { provideKeystoneService() }
    single { KeystoneManager(get(), get()) }
    
    // Wallet UseCases — CapabilityGate injected as mandatory singleton dependency
    single { CreateWalletUseCase(get(), get(), get(), get(), get(), get(), get()) }
    single { ImportWalletUseCase(get(), get(), get(), get(), get(), get(), get()) }
    single<RevealMnemonicUseCase> { RealRevealMnemonicUseCase(get(), get()) }
    
    // Transaction UseCases
    single { SendTransactionUseCase(get<WalletRepository>(), get<TransactionRepository>(), get<CryptoProvider>(), get<SecureStorage>(), get<CapabilityGate>(), get<SecureKeyManager>(), get(), get(), get()) }
    single { EstimateGasUseCase(get()) }
    single { GetTransactionHistoryUseCase(get()) }
    
    // Price UseCases
    single { GetTokenPriceUseCase(get(), get()) }
    
    // AddressBook UseCases
    single { AddAddressContactUseCase(get()) }
    single { SearchAddressBookUseCase(get()) }
    single { GetAddressContactsUseCase(get()) }
    single { UpdateAddressContactUseCase(get()) }
    
    // NFT UseCases
    single { GetNftsUseCase(get()) }
    single { ManageNftsUseCase(get()) }
    
    // PriceAlert UseCases
    single { ManagePriceAlertsUseCase(get()) }
    
    // Notification UseCases
    single { ManageNotificationsUseCase(get(), get(), get()) }
    
    // Contact UseCases
    single { AddContactUseCase(get()) }
    single { GetAllContactsUseCase(get()) }
    single { GetContactByIdUseCase(get()) }
    single { UpdateContactUseCase(get()) }
    single { DeleteContactUseCase(get()) }
    
    // UTXO Chain Components
    single { UTXOApiClient() }
    single { BitcoinPlatformAdapter(Network.BITCOIN_MAINNET) }
    single { BlockstreamApiClient(Network.BITCOIN_MAINNET) }
    
    // UTXO Chain Signers
    single { BitcoinSigner() }
    single { LitecoinSigner() }
    single { DogecoinSigner() }
    single { BitcoinCashSigner() }
    
    // UTXO Address Management
    single { AddressDerivation(get()) }
    single { UTXOAddressManager(get(), get()) }
    
    // Bitcoin UseCases
    single { SendBitcoinTransactionUseCase(get(), get(), get(), get(), get(), get(), get()) }
    
    // UTXO UseCases
    single { SendUTXOTransactionUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    single { GetUTXOTransactionHistoryUseCase(get()) }
    
    // Multichain Components
    single { com.cbstudio.wearwallet.core.multichain.tokens.ERC20TokenHandler(get()) }

    // Rango Swap
    single { com.cbstudio.wearwallet.core.rango.RangoClient(get()) }
    single { com.cbstudio.wearwallet.core.rango.RangoRepository(get()) }
    single { com.cbstudio.wearwallet.core.rango.RangoMetadataRepository(get()) }
    
    // 0x Swap
    single { com.cbstudio.wearwallet.core.zerox.ZeroXClient() }
    single { com.cbstudio.wearwallet.core.zerox.ZeroXRepository(get()) }
    
    // Swap UseCases (Read-Only Quotes in Release)
    single { com.cbstudio.wearwallet.core.domain.usecase.swap.GetSwapQuoteUseCase(get()) }
    
    // DeFi Aggregator
    single { com.cbstudio.wearwallet.core.multichain.defi.DeFiAggregator(get(), get(), get()) }
}

/**
 * Repository Module - 需要在平台層實現
 */
expect val platformRepositoryModule: Module

/**
 * Platform Module - 需要在平台層實現
 */
expect val platformProviderModule: Module

/**
 * 區塊鏈模組 - 在平台層實現
 */
expect val blockchainModule: Module

/**
 * 獲取所有模組
 */
fun getAllCoreModules(): List<Module> = listOf(
    coreModule,
    platformRepositoryModule,
    platformProviderModule,
    blockchainModule
)

/**
 * 獲取核心模組 - 為了向後相容
 */
fun getCoreModules(): List<Module> = getAllCoreModules()

/**
 * 獲取平台模組 - 為了向後相容
 */
fun getPlatformModule(): Module = platformProviderModule
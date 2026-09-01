//
//  DIContainer.swift
//  WatchWallet Watch App
//
//  依賴注入容器 - 簡化版本（暫時跳過 Koin 初始化）
//

import Foundation
import coreKmp

/// CoreKmp 依賴注入容器
class DIContainer {
    static let shared = DIContainer()
    
    // Mock flag - 設定為 false 以使用真實 KMP
    private let useMock = false
    
    // KMP Helper
    private var kmpHelper: KoinIosHelper?
    
    private init() {
        print("[DIContainer] 🚀 Initializing Real KMP Integration...")
        setupKoin()
    }
    
    private func setupKoin() {
        do {
            // 初始化 KMP Koin
            let koinApp = KoinInitializer().start(appModule: nil)
            self.kmpHelper = KoinIosHelper(koinAppAny: koinApp)
            print("[DIContainer] ✅ KMP Koin initialized successfully")
        } catch {
            print("[DIContainer] ❌ KMP Koin initialization failed: \(error)")
        }
    }
    
    // MARK: - Providers
    
    func getCryptoProvider() -> (any CryptoProvider)? {
        if useMock { return nil }
        return kmpHelper?.getCryptoProvider()
    }
    
    func getKeystoreManager() -> KeystoreManager? {
        if useMock { return nil }
        return kmpHelper?.getKeystoreManager()
    }
    
    // MARK: - Repositories
    
    func getWalletRepository() -> (any WalletRepository)? {
        if useMock { return nil }
        return kmpHelper?.getWalletRepository()
    }
    
    func getTokenRepository() -> (any TokenRepository)? {
        if useMock { return nil }
        return kmpHelper?.getTokenRepository()
    }

    func getNftRepository() -> (any NftRepository)? {
        if useMock { return nil }
        return kmpHelper?.getNftRepository()
    }

    func getPriceAlertRepository() -> (any PriceAlertRepository)? {
        if useMock { return nil }
        return kmpHelper?.getPriceAlertRepository()
    }
    
    // MARK: - Wallet UseCases
    
    func getCreateWalletUseCase() -> CreateWalletUseCase? {
        if useMock { return nil }
        return kmpHelper?.getCreateWalletUseCase()
    }
    
    func getImportWalletUseCase() -> ImportWalletUseCase? {
        if useMock { return nil }
        return kmpHelper?.getImportWalletUseCase()
    }
    
    // MARK: - Transaction UseCases
    
    func getSendTransactionUseCase() -> SendTransactionUseCase? {
        if useMock { return nil }
        return kmpHelper?.getSendTransactionUseCase()
    }
    
    func getEstimateGasUseCase() -> EstimateGasUseCase? {
        if useMock { return nil }
        return kmpHelper?.getEstimateGasUseCase()
    }
    
    func getGetTransactionHistoryUseCase() -> GetTransactionHistoryUseCase? {
        if useMock { return nil }
        return kmpHelper?.getGetTransactionHistoryUseCase()
    }
    
    func getEstimateTransactionUseCase() -> EstimateTransactionUseCase? {
        if useMock { return nil }
        return kmpHelper?.getEstimateTransactionUseCase()
    }
    
    // MARK: - Token UseCases
    
    func getScanTokensUseCase() -> ScanTokensUseCase? {
        if useMock { return nil }
        return kmpHelper?.getScanTokensUseCase()
    }
    
    func getGetUserTokensUseCase() -> GetUserTokensUseCase? {
        if useMock { return nil }
        return kmpHelper?.getGetUserTokensUseCase()
    }
    
    // MARK: - Price UseCases
    
    func getGetTokenPriceUseCase() -> GetTokenPriceUseCase? {
        if useMock { return nil }
        return kmpHelper?.getGetTokenPriceUseCase()
    }
    
    // MARK: - AddressBook UseCases
    
    func getAddAddressContactUseCase() -> AddAddressContactUseCase? {
        if useMock { return nil }
        return kmpHelper?.getAddAddressContactUseCase()
    }
    
    func getSearchAddressBookUseCase() -> SearchAddressBookUseCase? {
        if useMock { return nil }
        return kmpHelper?.getSearchAddressBookUseCase()
    }
    
    func getGetAddressContactsUseCase() -> GetAddressContactsUseCase? {
        if useMock { return nil }
        return kmpHelper?.getGetAddressContactsUseCase()
    }
    
    func getUpdateAddressContactUseCase() -> UpdateAddressContactUseCase? {
        if useMock { return nil }
        return kmpHelper?.getUpdateAddressContactUseCase()
    }
    
    // MARK: - PriceAlert UseCases
    
    func getManagePriceAlertsUseCase() -> ManagePriceAlertsUseCase? {
        if useMock { return nil }
        return kmpHelper?.getManagePriceAlertsUseCase()
    }
    
    // MARK: - Notification UseCases
    
    func getManageNotificationsUseCase() -> ManageNotificationsUseCase? {
        if useMock { return nil }
        return kmpHelper?.getManageNotificationsUseCase()
    }
    
    // MARK: - Contact UseCases
    
    func getAddContactUseCase() -> AddContactUseCase? {
        if useMock { return nil }
        return kmpHelper?.getAddContactUseCase()
    }
    
    func getGetContactByIdUseCase() -> GetContactByIdUseCase? {
        if useMock { return nil }
        return kmpHelper?.getGetContactByIdUseCase()
    }
    
    func getUpdateContactUseCase() -> UpdateContactUseCase? {
        if useMock { return nil }
        return kmpHelper?.getUpdateContactUseCase()
    }
    
    func getDeleteContactUseCase() -> DeleteContactUseCase? {
        if useMock { return nil }
        return kmpHelper?.getDeleteContactUseCase()
    }
    
    func getGetAllContactsUseCase() -> GetAllContactsUseCase? {
        if useMock { return nil }
        return kmpHelper?.getGetAllContactsUseCase()
    }
    
    // MARK: - Bitcoin UseCases
    
    func getSendBitcoinTransactionUseCase() -> SendBitcoinTransactionUseCase? {
        if useMock { return nil }
        return kmpHelper?.getSendBitcoinTransactionUseCase()
    }
    
    // MARK: - UTXO UseCases
    
    func getSendUTXOTransactionUseCase() -> SendUTXOTransactionUseCase? {
        if useMock { return nil }
        return kmpHelper?.getSendUTXOTransactionUseCase()
    }
    
    func getGetUTXOTransactionHistoryUseCase() -> GetUTXOTransactionHistoryUseCase? {
        if useMock { return nil }
        return kmpHelper?.getGetUTXOTransactionHistoryUseCase()
    }
    
    func getUTXOApiClient() -> UTXOApiClient? {
        if useMock { return nil }
        return kmpHelper?.getUTXOApiClient()
    }
    
    // MARK: - NFT UseCases
    
    func getGetNftsUseCase() -> GetNftsUseCase? {
        if useMock { return nil }
        return kmpHelper?.getGetNftsUseCase()
    }
    
    func getManageNftsUseCase() -> ManageNftsUseCase? {
        if useMock { return nil }
        return kmpHelper?.getManageNftsUseCase()
    }
    
    // MARK: - DeFi UseCases
    
    func getDeFiAggregator() -> DeFiAggregator? {
        if useMock { return nil }
        return kmpHelper?.getDeFiAggregator()
    }
    
    // MARK: - Rango UseCases
    
    func getRangoRepository() -> RangoMetadataRepository? {
        if useMock { return nil }
        return kmpHelper?.getRangoMetadataRepository()
    }
}

// MARK: - Extensions

extension DIContainer {
    /// 測試配置
    func verifyConfiguration() -> Bool {
        return true // Always true in mock mode
    }
}

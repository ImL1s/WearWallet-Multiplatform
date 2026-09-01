//
//  KoinHelper.swift
//  WatchWallet Watch App
//
//  Koin Helper - 已棄用，使用 DIContainer 代替
//

import Foundation
import coreKmp

/**
 * Koin Helper for watchOS
 * 
 * 注意：此類已棄用，請使用 DIContainer.shared 來獲取依賴
 * 保留此類是為了向後兼容
 */
class KoinHelper {
    
    // MARK: - Singleton
    static let shared = KoinHelper()
    
    // MARK: - Initialization
    private init() {
        print("[KoinHelper] ⚠️ KoinHelper is deprecated, use DIContainer.shared instead")
    }
    
    // MARK: - Public Methods (Delegating to DIContainer)
    
    /// Get CreateWalletUseCase
    var createWalletUseCase: CreateWalletUseCase? {
        return DIContainer.shared.getCreateWalletUseCase()
    }
    
    /// Get ImportWalletUseCase
    var importWalletUseCase: ImportWalletUseCase? {
        return DIContainer.shared.getImportWalletUseCase()
    }
    
    /// Get SendTransactionUseCase
    var sendTransactionUseCase: SendTransactionUseCase? {
        return DIContainer.shared.getSendTransactionUseCase()
    }
    
    /// Get EstimateGasUseCase
    var estimateGasUseCase: EstimateGasUseCase? {
        return DIContainer.shared.getEstimateGasUseCase()
    }
    
    /// Get GetTokenPriceUseCase
    var getTokenPriceUseCase: GetTokenPriceUseCase? {
        return DIContainer.shared.getGetTokenPriceUseCase()
    }
}
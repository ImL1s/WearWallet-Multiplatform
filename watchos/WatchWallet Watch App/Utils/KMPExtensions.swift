//
//  KMPExtensions.swift
//  WatchWallet Watch App
//
//  Swift extensions for KMP async/await bridging - 簡化版本
//  使用 KMPTypeAliases.swift 中定義的類型別名 (KMPWallet = SwiftWalletAccount)
//

import Foundation
import coreKmp

// MARK: - WalletRepositoryImpl Extension (Mock Delegated)

extension WalletRepositoryImpl {
    
    /// 創建錢包 (Mock)
    func createWalletAsync(name: String, mnemonic: String, password: String) async -> Swift.Result<KMPWallet, Error> {
        print("[KMPExtensions] createWalletAsync (delegating to KMPUseCaseDirect Mock)")
        do {
            // 注意: createWallet 在 KMPUseCaseDirect 中不需要 mnemonic (生成新的)
            // 但這裡傳入了 mnemonic，如果是 import 請用 importWallet
            // 為了兼容接口，我們忽略 mnemonic 或者如果是創建則生成新的
            // 這裡假設是 "Create New Wallet" 場景，mnemonic 用於生成私鑰 (?)
            // 但 Mock 只是返回 fake data
            
            // 確保調用的是 KMPUseCaseDirect
            let wallet = try await KMPUseCaseDirect.shared.createWallet(name: name, password: password)
            return .success(wallet)
        } catch {
            return .failure(error)
        }
    }
    
    /// 導入錢包 (Mock)
    func importWalletAsync(mnemonic: String, name: String, password: String) async -> Swift.Result<KMPWallet, Error> {
        print("[KMPExtensions] importWalletAsync (delegating to KMPUseCaseDirect Mock)")
        do {
            let wallet = try await KMPUseCaseDirect.shared.importWallet(mnemonic: mnemonic, name: name, password: password)
            return .success(wallet)
        } catch {
            return .failure(error)
        }
    }
    
    /// 獲取所有錢包 (Mock)
    func getAllWalletsAsync() async throws -> [KMPWallet] {
        print("[KMPExtensions] getAllWalletsAsync (delegating to KMPUseCaseDirect Mock)")
        // KMPUseCaseDirect 是 Mock，可能返回空。
        // 如果需要假數據，可以在 KMPUseCaseDirect 中添加
        return try await KMPUseCaseDirect.shared.getAllWallets()
    }
    
    /// 獲取錢包 (Mock)
    func getWalletAsync(id: String) async throws -> KMPWallet? {
        print("[KMPExtensions] getWalletAsync: \(id)")
        // Mock lookup
        // 在真实 App 中，应该从 getAllWallets 筛选
        // 为了 Mock，直接返回一个假的
        return SwiftWalletAccount(
            id: id,
            name: "Mock Wallet",
            address: "0xMockAddress...",
            chainTypeRaw: "ethereum",
            isHardwareWallet: false,
            createdAt: Date()
        )
    }
    
    /// 驗證助記詞 (模擬)
    func validateMnemonicAsync(mnemonic: String) async -> Bool {
        print("[KMPExtensions] validateMnemonicAsync")
        let words = mnemonic.split(separator: " ")
        return words.count == 12 || words.count == 24
    }
}

// MARK: - UseCase Convenience Methods

extension DIContainer {
    // Methods removed as they are no longer duplicated here (see DIContainer.swift)
    // If needed, they should be in ONE place.
    // Given DIContainer.swift now has 'not implemented' for everything or mocks
    // We can put working mocks here IF DIContainer doesn't have them
    // But DIContainer.swift removed them.
    
    // We can define global helpers or extend DIContainer if we really want
    // But the ViewModel likely calls these.
    
    // Let's add them back here as extensions to DIContainer, providing working MOCK logic
    // This allows ViewModels to call DIContainer.shared.fetchBalanceAsync(...)
    
    /// 簡化的餘額獲取
    func fetchBalanceAsync(address: String, chainType: coreKmp.ChainType) async throws -> String {
         // 使用 KMPUseCaseDirect
         let balance = try await KMPUseCaseDirect.shared.getTokenBalance(
            walletAddress: address,
            tokenAddress: nil,
            chainType: chainType
         )
         return "\(balance.balance) \(balance.symbol)"
    }
    
    /// 簡化的代幣價格獲取
    func fetchTokenPriceAsync(symbol: String) async throws -> Double {
        let mockPrices: [String: Double] = [
            "ETH": 2345.67,
            "BTC": 43210.12,
            "BNB": 312.45
        ]
        return mockPrices[symbol.uppercased()] ?? 0.0
    }
}
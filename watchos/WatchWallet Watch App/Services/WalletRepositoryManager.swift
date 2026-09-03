//
//  WalletRepositoryManager.swift
//  WatchWallet Watch App
//
//  Wallet repository manager with secure Keychain integration
//

import Foundation
import coreKmp

class WalletRepositoryManager: ObservableObject {
    static let shared = WalletRepositoryManager()
    
    // MARK: - Properties
    private let secureWalletManager = SecureWalletManager.shared
    private let keychainManager = KeychainManager.shared
    private var kmpWalletRepository: WalletRepository?
    
    #if DEBUG
    // E2E Test Mock Storage (UserDefaults Persistence for robustness)
    private var mockWallets: [WalletData] {
        get {
            if let data = UserDefaults.standard.data(forKey: "mockWallets"),
               let decoded = try? JSONDecoder().decode([WalletData].self, from: data) {
                return decoded
            }
            return []
        }
        set {
            if let encoded = try? JSONEncoder().encode(newValue) {
                UserDefaults.standard.set(encoded, forKey: "mockWallets")
            }
        }
    }
    #endif
    
    // MARK: - Initialization
    private init() {
        setupKMPWalletRepository()
    }
    
    // MARK: - Setup
    
    private func setupKMPWalletRepository() {
        self.kmpWalletRepository = DIContainer.shared.getWalletRepository()
        if self.kmpWalletRepository == nil {
            print("[WalletRepositoryManager] ⚠️ KMP WalletRepository initialization failed")
        }
    }
    
    // MARK: - Wallet Management
    
    /// Create a new wallet with mnemonic
    func createWallet(
        name: String,
        mnemonic: String? = nil,
        networkType: SecureWalletData.NetworkType = .ethereum
    ) async -> Swift.Result<WalletData, WalletError> {

        // Get CoreKmp CryptoProvider
        let cryptoProvider = DIContainer.shared.getCryptoProvider()
        
        // Return failure if provider not available (should only happen if Koin failed)
        guard let cryptoProvider = cryptoProvider else {
             return .failure(.creationFailed("CryptoProvider unavailable"))
        }

        #if DEBUG
        // E2E Test Mock: In-memory mock
        let mockId = UUID().uuidString
        let mockAddress = "0x" + String(UUID().uuidString.prefix(40))
        let walletData = WalletData(
            id: mockId,
            name: name,
            address: mockAddress,
            networkType: networkType.rawValue,
            createdAt: Date(),
            isHardwareWallet: false,
            chainId: "1"
        )
        var wallets = mockWallets
        wallets.append(walletData)
        mockWallets = wallets
        print("[WalletRepositoryManager] ✅ Mock Wallet Created: \(name)")
        return .success(walletData)
        #endif

        // Generate mnemonic if not provided using CoreKmp
        let finalMnemonic: String
        if let providedMnemonic = mnemonic {
            finalMnemonic = providedMnemonic
        } else {
            do {
                finalMnemonic = try await cryptoProvider.generateMnemonic(wordCount: 12)
            } catch {
                print("[WalletRepositoryManager] Failed to generate mnemonic: \(error)")
                return .failure(.mnemonicGenerationFailed)
            }
        }

        // Create secure wallet in Keychain
        let secureResult = await secureWalletManager.createWallet(
            name: name,
            mnemonic: finalMnemonic,
            networkType: networkType
        )

        switch secureResult {
        case .success(let secureWalletData):
            // Generate address using CoreKmp
            do {
                let keyPair = try await cryptoProvider.generateKeyPairFromMnemonic(
                    mnemonic: finalMnemonic,
                    derivationPath: "m/44'/60'/0'/0/0",
                    chainType: .ethereum
                )
                let addr = try await cryptoProvider.deriveAddress(publicKey: keyPair.publicKey)
                
                // Create wallet data object
                let walletData = WalletData(
                    id: secureWalletData.walletId,
                    name: secureWalletData.name,
                    address: addr,
                    
                    // Fields from SecureWalletData
                    networkType: networkType.rawValue,
                    createdAt: secureWalletData.createdAt,
                    isHardwareWallet: false, // Default or mock
                    chainId: "1" // Default for Ethereum
                )
                
                print("[WalletRepositoryManager] Created wallet successfully: \(walletData.id)")
                return .success(walletData)
            } catch {
                print("[WalletRepositoryManager] Failed to derive key or address: \(error)")
                return .failure(.addressGenerationFailed)
            }
            
        case .failure(let error):
            print("[WalletRepositoryManager] Failed to create wallet: \(error)")
            return .failure(.creationFailed(error.localizedDescription))
        }
    }
    
    /// Import wallet from mnemonic
    func importWallet(
        name: String,
        mnemonic: String,
        networkType: SecureWalletData.NetworkType = .ethereum
    ) async -> Swift.Result<WalletData, WalletError> {
        
        // Validate mnemonic length
        let words = mnemonic.split(separator: " ")
        guard [12, 15, 18, 21, 24].contains(words.count) else {
            return .failure(.invalidMnemonic)
        }
        
        // Reuse createWallet logic which handles passed mnemonic
        return await createWallet(name: name, mnemonic: mnemonic, networkType: networkType)
    }
    
    /// Get all wallets
    func getAllWallets() async -> Swift.Result<[WalletData], WalletError> {
        #if DEBUG
        return .success(mockWallets)
        #endif
        
        guard let cryptoProvider = DIContainer.shared.getCryptoProvider() else {
             return .failure(.retrievalFailed("CryptoProvider unavailable"))
        }

        let secureResult = secureWalletManager.getAllWallets()

        switch secureResult {
        case .success(let secureWallets):
            var wallets: [WalletData] = []

            for secureWallet in secureWallets {
                // Get mnemonic to regenerate address
                let mnemonicResult = secureWalletManager.getWalletMnemonic(walletId: secureWallet.walletId)

                if case .success(let mnemonic) = mnemonicResult {
                    do {
                        let keyPair = try await cryptoProvider.generateKeyPairFromMnemonic(
                            mnemonic: mnemonic,
                            derivationPath: "m/44'/60'/0'/0/0",
                            chainType: .ethereum
                        )
                        let address = try await cryptoProvider.deriveAddress(publicKey: keyPair.publicKey)
                        
                         let walletData = WalletData(
                            id: secureWallet.walletId,
                            name: secureWallet.name,
                            address: address,
                            networkType: secureWallet.networkType.rawValue,
                            createdAt: secureWallet.createdAt,
                            isHardwareWallet: false,
                            chainId: "1"
                        )
                        wallets.append(walletData)
                    } catch {
                        print("[WalletRepositoryManager] Failed to generate address for wallet \(secureWallet.walletId): \(error)")
                    }
                }
            }

            return .success(wallets)

        case .failure(let error):
            print("[WalletRepositoryManager] Failed to get all wallets: \(error)")
            return .failure(.retrievalFailed(error.localizedDescription))
        }
    }
    
    /// Get wallet by ID
    func getWallet(walletId: String) async -> Swift.Result<WalletData, WalletError> {
        guard let cryptoProvider = DIContainer.shared.getCryptoProvider() else {
             return .failure(.retrievalFailed("CryptoProvider unavailable"))
        }

        let secureResult = secureWalletManager.getWallet(walletId: walletId)

        switch secureResult {
        case .success(let secureWallet):
            // Get mnemonic for address generation
            let mnemonicResult = secureWalletManager.getWalletMnemonic(walletId: walletId)

            switch mnemonicResult {
            case .success(let mnemonic):
                do {
                    let keyPair = try await cryptoProvider.generateKeyPairFromMnemonic(
                        mnemonic: mnemonic,
                        derivationPath: "m/44'/60'/0'/0/0",
                        chainType: .ethereum
                    )
                    let addr = try await cryptoProvider.deriveAddress(publicKey: keyPair.publicKey)
                    
                    let walletData = WalletData(
                        id: secureWallet.walletId,
                        name: secureWallet.name,
                        address: addr,
                        networkType: secureWallet.networkType.rawValue,
                        createdAt: secureWallet.createdAt,
                        isHardwareWallet: false,
                        chainId: "1"
                    )

                    return .success(walletData)
                } catch {
                    print("[WalletRepositoryManager] Failed to generate address: \(error)")
                    return .failure(.addressGenerationFailed)
                }

            case .failure(let error):
                print("[WalletRepositoryManager] Failed to get mnemonic for wallet \(walletId): \(error)")
                return .failure(.retrievalFailed(error.localizedDescription))
            }

        case .failure(let error):
            print("[WalletRepositoryManager] Failed to get wallet \(walletId): \(error)")
            return .failure(.walletNotFound)
        }
    }
    
    // MARK: - Pass-through Methods
    
    func getWalletMnemonic(walletId: String) -> Swift.Result<String, WalletError> {
        let result = secureWalletManager.getWalletMnemonic(walletId: walletId)
        switch result {
        case .success(let mnemonic): return .success(mnemonic)
        case .failure(let error): return .failure(.retrievalFailed(error.localizedDescription))
        }
    }
    
    func deleteWallet(walletId: String) -> Swift.Result<Void, WalletError> {
        let result = secureWalletManager.deleteWallet(walletId: walletId)
        switch result {
        case .success: return .success(())
        case .failure(let error): return .failure(.deletionFailed(error.localizedDescription))
        }
    }
    
    func deleteAllWallets() -> Swift.Result<Void, WalletError> {
        
        #if DEBUG
        mockWallets = []
        print("[WalletRepositoryManager] ✅ Mock Wallets Cleared")
        return .success(())
        #endif
        
        let result = secureWalletManager.deleteAllWallets()
        switch result {
        case .success: return .success(())
        case .failure(let error): return .failure(.deletionFailed(error.localizedDescription))
        }
    }
    
    // MARK: - Async Wrapper Methods
    
    /// Async wrapper for getAllWallets - KMP integrated
    func getAllWalletsAsync() async throws -> [WalletData] {
        let result = await getAllWallets()
        switch result {
        case .success(let wallets):
            return wallets
        case .failure(let error):
            throw error
        }
    }
    
    /// Async wrapper for getWallet
    func getActiveWalletAsync() async throws -> WalletData? {
        let wallets = try await getAllWalletsAsync()
        
        // 從 UserDefaults 讀取保存的活動錢包 ID
        if let savedWalletId = UserDefaults.standard.string(forKey: "activeWalletId"),
           let activeWallet = wallets.first(where: { $0.id == savedWalletId }) {
            return activeWallet
        }
        
        // 如果沒有保存的選擇，返回第一個錢包
        return wallets.first
    }
    
    /// Async wrapper for getWallet
    func getWalletAsync(id: String) async throws -> WalletData? {
        let result = await getWallet(walletId: id)
        switch result {
        case .success(let wallet): return wallet
        case .failure: return nil
        }
    }
    
    /// Async wrapper for creating wallets
    func createWalletAsync(name: String) async -> Swift.Result<WalletData, WalletError> {
        return await createWallet(name: name, mnemonic: nil)
    }
    
    /// Async wrapper for importing wallets
    func importWalletAsync(mnemonic: String, name: String) async -> Swift.Result<WalletData, WalletError> {
        return await importWallet(name: name, mnemonic: mnemonic)
    }
    
    /// Async wrapper for deleting wallets
    func deleteWalletAsync(id: String) async throws -> Bool {
        return try await withCheckedThrowingContinuation { continuation in
            let result = deleteWallet(walletId: id)
            switch result {
            case .success: continuation.resume(returning: true)
            case .failure(let error): continuation.resume(throwing: error)
            }
        }
    }
    
    // MARK: - Helpers
    
    // Helper to handle KMP Wallet Result
    private func handleKMPWalletResult(
        _ result: Result<WalletAccount>,
        transform: (WalletAccount) -> WalletData
    ) async -> Swift.Result<WalletData, WalletError> {
        if let success = result as? ResultSuccess<WalletAccount> {
            if let data = success.data {
                return .success(transform(data))
            } else {
                return .failure(.creationFailed("KMP result data is nil"))
            }
        } else if let failure = result as? ResultFailure {
            return .failure(.creationFailed(failure.exception.message ?? "Unknown error"))
        }
        return .failure(.creationFailed("Unknown KMP result type"))
    }
    
    // Helper to handle KMP Wallet List Result
    private func handleKMPWalletListResult(
        _ result: Result<NSArray>, // Assuming KMP returns NSArray for List
        transform: (WalletAccount) -> WalletData
    ) async throws -> [WalletData] {
        if let success = result as? ResultSuccess<NSArray> {
             if let accounts = success.data as? [WalletAccount] {
                 return accounts.map(transform)
             }
             return []
        } else if let failure = result as? ResultFailure {
            throw WalletError.retrievalFailed(failure.exception.message ?? "Unknown error")
        }
        throw WalletError.retrievalFailed("Unknown KMP result type")
    }
    
    // Helper accessors
    private func getChainSymbol(chainId: String) -> String {
        switch chainId {
        case "1": return "ETH"
        case "56": return "BNB"
        default: return "ETH"
        }
    }
    
    private func getChainName(chainId: String) -> String {
        switch chainId {
        case "1": return "Ethereum"
        case "56": return "BNB Smart Chain"
        default: return "Unknown Chain"
        }
    }
}
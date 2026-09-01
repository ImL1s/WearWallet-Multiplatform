import Foundation
import coreKmp

// MARK: - Secure Wallet Data Models

/// Secure wallet metadata stored in Keychain
struct SecureWalletData: Codable {
    let walletId: String
    let name: String
    let createdAt: Date
    let accountIndex: Int
    let derivationPath: String
    let networkType: NetworkType
    let addressCount: Int
    var isBackedUp: Bool
    var lastUsedAt: Date
    
    enum NetworkType: String, Codable, CaseIterable {
        case mainnet = "mainnet"
        case testnet = "testnet"
        case ethereum = "ethereum"
        case bsc = "bsc"
        case polygon = "polygon"
        case cronos = "cronos"
        
        var chainId: Int {
            switch self {
            case .mainnet, .ethereum:
                return 1
            case .testnet:
                return 5 // Goerli
            case .bsc:
                return 56
            case .polygon:
                return 137
            case .cronos:
                return 25
            }
        }
        
        var name: String {
            switch self {
            case .mainnet, .ethereum:
                return "Ethereum"
            case .testnet:
                return "Ethereum Testnet"
            case .bsc:
                return "Binance Smart Chain"
            case .polygon:
                return "Polygon"
            case .cronos:
                return "Cronos"
            }
        }
    }
    
    init(walletId: String, name: String, accountIndex: Int = 0, derivationPath: String = "m/44'/60'/0'/0/0", networkType: NetworkType = .ethereum) {
        self.walletId = walletId
        self.name = name
        self.createdAt = Date()
        self.accountIndex = accountIndex
        self.derivationPath = derivationPath
        self.networkType = networkType
        self.addressCount = 1
        self.isBackedUp = false
        self.lastUsedAt = Date()
    }
    
    /// Update the last used timestamp
    mutating func updateLastUsed() {
        self.lastUsedAt = Date()
    }
    
    /// Mark as backed up
    mutating func markAsBackedUp() {
        self.isBackedUp = true
    }
}

/// Secure wallet address information
struct SecureWalletAddress: Codable {
    let walletId: String
    let address: String
    let derivationPath: String
    let accountIndex: Int
    let addressIndex: Int
    let createdAt: Date
    let label: String?
    
    init(walletId: String, address: String, derivationPath: String, accountIndex: Int, addressIndex: Int, label: String? = nil) {
        self.walletId = walletId
        self.address = address
        self.derivationPath = derivationPath
        self.accountIndex = accountIndex
        self.addressIndex = addressIndex
        self.createdAt = Date()
        self.label = label
    }
}

/// Secure wallet settings
struct SecureWalletSettings: Codable {
    var requireAuthenticationForTransactions: Bool
    var requireAuthenticationForSensitiveData: Bool
    var autoLockTimeoutMinutes: Int
    var defaultNetworkType: SecureWalletData.NetworkType
    var showTestNetworks: Bool
    var biometricAuthenticationEnabled: Bool
    
    static let `default` = SecureWalletSettings(
        requireAuthenticationForTransactions: true,
        requireAuthenticationForSensitiveData: true,
        autoLockTimeoutMinutes: 5,
        defaultNetworkType: .ethereum,
        showTestNetworks: false,
        biometricAuthenticationEnabled: true
    )
}

/// Data cleanup verification status
struct DataCleanupStatus {
    var mnemonicCleared: Bool = true
    var privateKeysCleared: Bool = true
    var walletDataCleared: Bool = true
    var settingsCleared: Bool = true
    var isCompletelyCleared: Bool = false
    var remainingItems: [String] = []
    
    init() {
        self.mnemonicCleared = true
        self.privateKeysCleared = true
        self.walletDataCleared = true
        self.settingsCleared = true
        self.isCompletelyCleared = false
        self.remainingItems = []
    }
}

// MARK: - Secure Wallet Manager

/// Manager for secure wallet operations using Keychain
class SecureWalletManager {
    
    // MARK: - Singleton
    static let shared = SecureWalletManager()
    
    // MARK: - Properties
    private let keychainManager = KeychainManager.shared
    private let walletListKey = "walletList"
    private let settingsKey = "settings"
    
    private init() {}
    
    // MARK: - Wallet Creation
    
    /// Create a new secure wallet
    /// - Parameters:
    ///   - name: Display name for the wallet
    ///   - mnemonic: BIP39 mnemonic phrase
    ///   - networkType: Network type for the wallet
    /// - Returns: Result containing the wallet data or error
    func createWallet(name: String, mnemonic: String, networkType: SecureWalletData.NetworkType = .ethereum) async -> Swift.Result<SecureWalletData, SecureWalletError> {
        let walletId = UUID().uuidString
        let accountIndex = getNextAccountIndex()
        let derivationPath = "m/44'/60'/0'/0/0"
        
        // Create wallet data
        let walletData = SecureWalletData(
            walletId: walletId,
            name: name,
            accountIndex: accountIndex,
            derivationPath: derivationPath,
            networkType: networkType
        )
        
        // Store mnemonic in Keychain
        let mnemonicResult = keychainManager.storeMnemonic(mnemonic)
        if case .failure(let error) = mnemonicResult {
            return .failure(.keychainError(error))
        }

        // Derive and store private key using CoreKmp CryptoProvider
        guard let cryptoProvider = DIContainer.shared.getCryptoProvider() else {
            return .failure(.keyDerivationFailed)
        }

        do {
            let keyPair = try await cryptoProvider.generateKeyPairFromMnemonic(
                mnemonic: mnemonic,
                derivationPath: "m/44'/60'/0'/0/0",
                chainType: .ethereum
            )
            let privateKey = keyPair.privateKey

            let privateKeyResult = keychainManager.storePrivateKey(privateKey, accountIndex: accountIndex)
            if case .failure(let error) = privateKeyResult {
                return .failure(.keychainError(error))
            }
            
            // Store wallet data
            let walletDataResult = keychainManager.storeWalletData(walletData, walletId: walletId)
            if case .failure(let error) = walletDataResult {
                return .failure(.keychainError(error))
            }
            
            // Update wallet list
            let updateResult = addToWalletList(walletId: walletId)
            if case .failure(let error) = updateResult {
                return .failure(error)
            }
            
            print("[SecureWalletManager] Created wallet successfully: \(walletId)")
            return .success(walletData)
        } catch {
            print("[SecureWalletManager] Failed to derive private key: \(error)")
            return .failure(.keyDerivationFailed)
        }
    }
    
    /// Import an existing wallet from mnemonic
    /// - Parameters:
    ///   - name: Display name for the wallet
    ///   - mnemonic: BIP39 mnemonic phrase
    ///   - networkType: Network type for the wallet
    /// - Returns: Result containing the wallet data or error
    func importWallet(name: String, mnemonic: String, networkType: SecureWalletData.NetworkType = .ethereum) async -> Swift.Result<SecureWalletData, SecureWalletError> {
        // Validate mnemonic
        let words = mnemonic.split(separator: " ")
        guard [12, 15, 18, 21, 24].contains(words.count) else {
            return .failure(.invalidMnemonic)
        }
        
        // Create wallet using the same process as createWallet
        return await createWallet(name: name, mnemonic: mnemonic, networkType: networkType)
    }
    
    // MARK: - Wallet Retrieval
    
    /// Get all wallets
    /// - Returns: Result containing array of wallet data or error
    func getAllWallets() -> Swift.Result<[SecureWalletData], SecureWalletError> {
        let walletListResult = getWalletList()
        if case .failure(let error) = walletListResult {
            return .failure(error)
        }
        
        guard case .success(let walletIds) = walletListResult else {
            return .failure(.walletNotFound)
        }
        var wallets: [SecureWalletData] = []
        
        for walletId in walletIds {
            let walletResult = keychainManager.retrieveWalletData(SecureWalletData.self, walletId: walletId)
            if case .success(let wallet) = walletResult {
                wallets.append(wallet)
            } else if case .failure(let error) = walletResult {
                 print("[SecureWalletManager] Error retrieving wallet \(walletId): \(error)")
            }
        }
        
        return .success(wallets)
    }
    
    /// Get wallet by ID
    /// - Parameter walletId: Unique identifier for the wallet
    /// - Returns: Result containing wallet data or error
    func getWallet(walletId: String) -> Swift.Result<SecureWalletData, SecureWalletError> {
        let result = keychainManager.retrieveWalletData(SecureWalletData.self, walletId: walletId)
        switch result {
        case .success(let wallet):
            return .success(wallet)
        case .failure(let error):
            return .failure(.keychainError(error))
        }
    }
    
    /// Get wallet's mnemonic
    /// - Parameter walletId: Unique identifier for the wallet
    /// - Returns: Result containing mnemonic or error
    func getWalletMnemonic(walletId: String) -> Swift.Result<String, SecureWalletError> {
        let result = keychainManager.retrieveMnemonic()
        switch result {
        case .success(let mnemonic):
            return .success(mnemonic)
        case .failure(let error):
            return .failure(.keychainError(error))
        }
    }
    
    /// Get wallet's private key
    /// - Parameter walletId: Unique identifier for the wallet
    /// - Returns: Result containing private key or error
    func getWalletPrivateKey(walletId: String) -> Swift.Result<String, SecureWalletError> {
        let walletResult = getWallet(walletId: walletId)
        
        let wallet: SecureWalletData
        if case .success(let data) = walletResult {
            wallet = data
        } else if case .failure(let error) = walletResult {
            return .failure(error)
        } else {
            return .failure(.walletNotFound)
        }
        let result = keychainManager.retrievePrivateKey(accountIndex: wallet.accountIndex)
        switch result {
        case .success(let privateKey):
            return .success(privateKey)
        case .failure(let error):
            return .failure(.keychainError(error))
        }
    }
    
    // MARK: - Wallet Management
    
    /// Delete a wallet
    /// - Parameter walletId: Unique identifier for the wallet
    /// - Returns: Result indicating success or error
    func deleteWallet(walletId: String) -> Swift.Result<Void, SecureWalletError> {
        // Get wallet data first
        let walletResult = getWallet(walletId: walletId)
        
        let wallet: SecureWalletData
        if case .success(let data) = walletResult {
            wallet = data
        } else if case .failure(let error) = walletResult {
            return .failure(error)
        } else {
            return .failure(.walletNotFound)
        }
        
        // Delete private key
        let privateKeyResult = keychainManager.deletePrivateKey(accountIndex: wallet.accountIndex)
        if case .failure(let error) = privateKeyResult {
            return .failure(.keychainError(error))
        }
        
        // Delete wallet data
        let walletDataResult = keychainManager.deleteWalletData(walletId: walletId)
        if case .failure(let error) = walletDataResult {
            return .failure(.keychainError(error))
        }
        
        // Remove from wallet list
        let removeResult = removeFromWalletList(walletId: walletId)
        if case .failure(let error) = removeResult {
            return .failure(error)
        }
        
        print("[SecureWalletManager] Deleted wallet successfully: \(walletId)")
        return .success(())
    }
    
    /// Delete all wallets
    /// - Returns: Result indicating success or error
    func deleteAllWallets() -> Swift.Result<Void, SecureWalletError> {
        let result = keychainManager.deleteAllWalletData()
        switch result {
        case .success:
            print("[SecureWalletManager] Deleted all wallets successfully")
            return .success(())
        case .failure(let error):
            return .failure(.keychainError(error))
        }
    }
    
    // MARK: - Secure Cleanup Methods
    
    /// Securely wipe all sensitive data from the device
    /// This method performs a complete cleanup of all wallet-related data
    /// - Returns: Result indicating success or error
    func secureWipeAllData() -> Swift.Result<Void, SecureWalletError> {
        print("[SecureWalletManager] Starting secure data wipe...")
        
        // Step 1: Delete all wallets individually for better error handling
        let walletsResult = getAllWallets()
        switch walletsResult {
        case .success(let wallets):
            for wallet in wallets {
                let deleteResult = deleteWallet(walletId: wallet.walletId)
                if case .failure(let error) = deleteResult {
                    print("[SecureWalletManager] Warning: Failed to delete wallet \(wallet.walletId): \(error)")
                }
            }
        case .failure(let error):
            print("[SecureWalletManager] Warning: Failed to get wallets for cleanup: \(error)")
        }
        
        // Step 2: Delete all remaining keychain data
        let keychainResult = keychainManager.deleteAllWalletData()
        switch keychainResult {
        case .success:
            print("[SecureWalletManager] All keychain data deleted")
        case .failure(let error):
            print("[SecureWalletManager] Warning: Failed to delete keychain data: \(error)")
        }
        
        // Step 3: Overwrite memory (best effort)
        overwriteMemoryData()
        
        print("[SecureWalletManager] Secure data wipe completed")
        return .success(())
    }
    
    /// Securely delete a specific wallet with memory overwrite
    /// - Parameter walletId: Unique identifier for the wallet
    /// - Returns: Result indicating success or error
    func secureDeleteWallet(walletId: String) -> Swift.Result<Void, SecureWalletError> {
        print("[SecureWalletManager] Starting secure deletion of wallet: \(walletId)")
        
        // Get wallet data before deletion for cleanup
        let _ = getWallet(walletId: walletId)
        
        // Delete the wallet normally
        let deleteResult = deleteWallet(walletId: walletId)
        
        // Overwrite memory regardless of delete result
        overwriteMemoryData()
        
        switch deleteResult {
        case .success:
            print("[SecureWalletManager] Wallet \(walletId) securely deleted")
            return .success(())
        case .failure(let error):
            return .failure(error)
        }
    }
    
    /// Verify that all sensitive data has been properly cleared
    /// - Returns: Result containing verification status
    func verifyDataCleanup() -> Swift.Result<DataCleanupStatus, SecureWalletError> {
        var status = DataCleanupStatus()
        
        // Check if mnemonic exists
        let mnemonicResult = keychainManager.retrieveMnemonic()
        switch mnemonicResult {
        case .success:
            status.mnemonicCleared = false
            status.remainingItems.append("mnemonic")
        case .failure(.itemNotFound):
            status.mnemonicCleared = true
        case .failure(let error):
            return .failure(.keychainError(error))
        }
        
        // Check if any private keys exist
        for i in 0..<10 { // Check first 10 account indices
            if keychainManager.privateKeyExists(accountIndex: i) {
                status.privateKeysCleared = false
                status.remainingItems.append("privateKey_\(i)")
            }
        }
        
        // Check if any wallet data exists
        let walletsResult = getAllWallets()
        switch walletsResult {
        case .success(let wallets):
            if !wallets.isEmpty {
                status.walletDataCleared = false
                status.remainingItems.append(contentsOf: wallets.map { "wallet_\($0.walletId)" })
            }
        case .failure(.keychainError(.itemNotFound)):
            status.walletDataCleared = true
        case .failure(let error):
            return .failure(error)
        }
        
        // Check if settings exist
        let settingsResult = getSettings()
        switch settingsResult {
        case .success:
            status.settingsCleared = false
            status.remainingItems.append("settings")
        case .failure(.keychainError(.itemNotFound)):
            status.settingsCleared = true
        case .failure(let error):
            return .failure(error)
        }
        
        status.isCompletelyCleared = status.mnemonicCleared && 
                                   status.privateKeysCleared && 
                                   status.walletDataCleared && 
                                   status.settingsCleared
        
        print("[SecureWalletManager] Data cleanup verification: \(status.isCompletelyCleared ? "CLEAN" : "PARTIAL")")
        if !status.remainingItems.isEmpty {
            print("[SecureWalletManager] Remaining items: \(status.remainingItems.joined(separator: ", "))")
        }
        
        return .success(status)
    }
    
    /// Force cleanup of any remaining data (last resort)
    /// - Returns: Result indicating success or error
    func forceCleanupRemainingData() -> Swift.Result<Void, SecureWalletError> {
        print("[SecureWalletManager] Force cleaning remaining data...")
        
        // Delete mnemonic specifically
        let _ = keychainManager.deleteMnemonic()
        
        // Delete private keys for multiple account indices
        for i in 0..<50 { // Check more account indices
            let _ = keychainManager.deletePrivateKey(accountIndex: i)
        }
        
        // Delete all keychain data
        let result = keychainManager.deleteAllWalletData()
        
        // Overwrite memory multiple times
        for _ in 0..<3 {
            overwriteMemoryData()
        }
        
        switch result {
        case .success:
            print("[SecureWalletManager] Force cleanup completed")
            return .success(())
        case .failure(let error):
            return .failure(.keychainError(error))
        }
    }
    
    // MARK: - Private Cleanup Helpers
    
    /// Overwrite memory data (best effort)
    /// This is a security measure to minimize data recovery from memory
    private func overwriteMemoryData() {
        // Create large data structures to overwrite memory
        let overwriteData = Data(repeating: 0, count: 1024 * 1024) // 1MB of zeros
        let randomData = Data((0..<1024*1024).map { _ in UInt8.random(in: 0...255) })
        
        // Force memory allocation and deallocation
        var memoryOverwrite: [Data] = []
        for _ in 0..<10 {
            memoryOverwrite.append(overwriteData)
            memoryOverwrite.append(randomData)
        }
        
        // Clear the array to deallocate memory
        memoryOverwrite.removeAll()
        
        // Force garbage collection (in Swift, this is automatic)
        print("[SecureWalletManager] Memory overwrite completed")
    }
    
    // MARK: - Settings Management
    
    /// Get wallet settings
    /// - Returns: Result containing settings or error
    func getSettings() -> Swift.Result<SecureWalletSettings, SecureWalletError> {
        let result = keychainManager.retrieveWalletData(SecureWalletSettings.self, walletId: settingsKey)
        switch result {
        case .success(let settings):
            return .success(settings)
        case .failure(.itemNotFound):
            return .success(.default)
        case .failure(let error):
            return .failure(.keychainError(error))
        }
    }
    
    /// Update wallet settings
    /// - Parameter settings: New settings to store
    /// - Returns: Result indicating success or error
    func updateSettings(_ settings: SecureWalletSettings) -> Swift.Result<Void, SecureWalletError> {
        let result = keychainManager.storeWalletData(settings, walletId: settingsKey)
        switch result {
        case .success:
            return .success(())
        case .failure(let error):
            return .failure(.keychainError(error))
        }
    }
    
    // MARK: - Private Helper Methods
    
    private func getNextAccountIndex() -> Int {
        let walletsResult = getAllWallets()
        if case .success(let wallets) = walletsResult {
            return wallets.map { $0.accountIndex }.max() ?? 0 + 1
        }
        return 0
    }
    
    private func getWalletList() -> Swift.Result<[String], SecureWalletError> {
        let result = keychainManager.retrieveWalletData([String].self, walletId: walletListKey)
        switch result {
        case .success(let walletIds):
            return .success(walletIds)
        case .failure(.itemNotFound):
            return .success([])
        case .failure(let error):
            return .failure(.keychainError(error))
        }
    }
    
    private func addToWalletList(walletId: String) -> Swift.Result<Void, SecureWalletError> {
        let listResult = getWalletList()
        if case .failure(let error) = listResult {
            return .failure(error)
        }
        
        var walletIds: [String] = []
        if case .success(let ids) = listResult {
            walletIds = ids
        }
        if !walletIds.contains(walletId) {
            walletIds.append(walletId)
        }
        
        let result = keychainManager.storeWalletData(walletIds, walletId: walletListKey)
        switch result {
        case .success:
            return .success(())
        case .failure(let error):
            return .failure(.keychainError(error))
        }
    }
    
    private func removeFromWalletList(walletId: String) -> Swift.Result<Void, SecureWalletError> {
        let listResult = getWalletList()
        if case .failure(let error) = listResult {
            return .failure(error)
        }
        
        var walletIds: [String] = []
        if case .success(let ids) = listResult {
            walletIds = ids
        }
        walletIds.removeAll { $0 == walletId }
        
        let result = keychainManager.storeWalletData(walletIds, walletId: walletListKey)
        switch result {
        case .success:
            return .success(())
        case .failure(let error):
            return .failure(.keychainError(error))
        }
    }
}

// MARK: - Secure Wallet Error Types

enum SecureWalletError: Error, LocalizedError {
    case keychainError(KeychainError)
    case keyDerivationFailed
    case invalidMnemonic
    case walletNotFound
    case duplicateWallet
    
    var errorDescription: String? {
        switch self {
        case .keychainError(let error):
            return error.localizedDescription
        case .keyDerivationFailed:
            return "Failed to derive private key from mnemonic"
        case .invalidMnemonic:
            return "Invalid mnemonic phrase"
        case .walletNotFound:
            return "Wallet not found"
        case .duplicateWallet:
            return "Wallet already exists"
        }
    }
}
// Note: Result extension removed to avoid conflict with coreKmp.Result
// Use pattern matching instead: if case .success(let value) = result { ... }
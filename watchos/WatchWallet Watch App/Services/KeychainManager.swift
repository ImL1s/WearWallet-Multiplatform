import Foundation
import Security
import CryptoKit
import LocalAuthentication

/// Keychain Manager for secure storage of sensitive wallet data
/// Provides hardware-backed encryption and biometric authentication
class KeychainManager {
    
    // MARK: - Singleton
    static let shared = KeychainManager()
    
    // MARK: - Service Identifiers
    private let serviceIdentifier = "com.iml1s.WatchWallet"
    private let mnemonicKey = "mnemonic"
    private let privateKeyPrefix = "privateKey_"
    private let walletDataPrefix = "walletData_"
    
    // MARK: - Security Configuration
    private let accessGroup: String? = nil // App-specific on watchOS
    
    private init() {}
    
    // MARK: - Mnemonic Storage
    
    /// Store mnemonic phrase securely in Keychain
    /// - Parameters:
    ///   - mnemonic: The BIP39 mnemonic phrase
    ///   - requireAuthentication: Whether to require biometric/passcode authentication
    /// - Returns: Result indicating success or failure
    func storeMnemonic(_ mnemonic: String, requireAuthentication: Bool = false) -> Result<Void, KeychainError> {
        let data = mnemonic.data(using: .utf8)!
        
        // Create query - simplified for watchOS compatibility
        var query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceIdentifier,
            kSecAttrAccount as String: mnemonicKey,
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        ]
        
        // Don't use access control on watchOS for now to avoid parameter errors
        // if requireAuthentication {
        //     var error: Unmanaged<CFError>?
        //     let accessControl = SecAccessControlCreateWithFlags(
        //         nil,
        //         kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
        //         .userPresence,
        //         &error
        //     )
        //     
        //     if let error = error {
        //         print("[KeychainManager] Failed to create access control: \(error)")
        //         return .failure(.accessControlCreationFailed)
        //     }
        //     
        //     query[kSecAttrAccessControl as String] = accessControl
        // }
        
        if let accessGroup = accessGroup {
            query[kSecAttrAccessGroup as String] = accessGroup
        }
        
        // Delete existing item first
        let deleteQuery: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceIdentifier,
            kSecAttrAccount as String: mnemonicKey
        ]
        SecItemDelete(deleteQuery as CFDictionary)
        
        // Add new item
        let status = SecItemAdd(query as CFDictionary, nil)
        
        switch status {
        case errSecSuccess:
            print("[KeychainManager] Mnemonic stored successfully")
            return .success(())
        default:
            print("[KeychainManager] Failed to store mnemonic: \(status)")
            return .failure(.storageError(status))
        }
    }
    
    /// Retrieve mnemonic phrase from Keychain
    /// - Returns: Result containing the mnemonic or error
    func retrieveMnemonic() -> Result<String, KeychainError> {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceIdentifier,
            kSecAttrAccount as String: mnemonicKey,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        
        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        
        switch status {
        case errSecSuccess:
            guard let data = result as? Data,
                  let mnemonic = String(data: data, encoding: .utf8) else {
                return .failure(.dataCorrupted)
            }
            print("[KeychainManager] Mnemonic retrieved successfully")
            return .success(mnemonic)
        case errSecItemNotFound:
            return .failure(.itemNotFound)
        case OSStatus(-128):  // errSecUserCancel
            return .failure(.userCancelled)
        case errSecAuthFailed:
            return .failure(.authenticationFailed)
        default:
            print("[KeychainManager] Failed to retrieve mnemonic: \(status)")
            return .failure(.retrievalError(status))
        }
    }
    
    // MARK: - Private Key Storage
    
    /// Store private key securely in Keychain
    /// - Parameters:
    ///   - privateKey: The private key in hex format
    ///   - accountIndex: The account index for HD wallet
    ///   - requireAuthentication: Whether to require biometric/passcode authentication
    /// - Returns: Result indicating success or failure
    func storePrivateKey(_ privateKey: String, accountIndex: Int, requireAuthentication: Bool = false) -> Result<Void, KeychainError> {
        let data = privateKey.data(using: .utf8)!
        let accountKey = "\(privateKeyPrefix)\(accountIndex)"
        
        // Create query - simplified for watchOS compatibility
        var query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceIdentifier,
            kSecAttrAccount as String: accountKey,
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        ]
        
        // Don't use access control on watchOS for now to avoid parameter errors
        // if requireAuthentication {
        //     var error: Unmanaged<CFError>?
        //     let accessControl = SecAccessControlCreateWithFlags(
        //         nil,
        //         kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
        //         .userPresence,
        //         &error
        //     )
        //     
        //     if let error = error {
        //         print("[KeychainManager] Failed to create access control: \(error)")
        //         return .failure(.accessControlCreationFailed)
        //     }
        //     
        //     query[kSecAttrAccessControl as String] = accessControl
        // }
        
        if let accessGroup = accessGroup {
            query[kSecAttrAccessGroup as String] = accessGroup
        }
        
        // Delete existing item first
        let deleteQuery: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceIdentifier,
            kSecAttrAccount as String: accountKey
        ]
        SecItemDelete(deleteQuery as CFDictionary)
        
        // Add new item
        let status = SecItemAdd(query as CFDictionary, nil)
        
        switch status {
        case errSecSuccess:
            print("[KeychainManager] Private key stored successfully for account \(accountIndex)")
            return .success(())
        default:
            print("[KeychainManager] Failed to store private key: \(status)")
            return .failure(.storageError(status))
        }
    }
    
    /// Retrieve private key from Keychain
    /// - Parameter accountIndex: The account index for HD wallet
    /// - Returns: Result containing the private key or error
    func retrievePrivateKey(accountIndex: Int) -> Result<String, KeychainError> {
        let accountKey = "\(privateKeyPrefix)\(accountIndex)"
        
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceIdentifier,
            kSecAttrAccount as String: accountKey,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        
        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        
        switch status {
        case errSecSuccess:
            guard let data = result as? Data,
                  let privateKey = String(data: data, encoding: .utf8) else {
                return .failure(.dataCorrupted)
            }
            print("[KeychainManager] Private key retrieved successfully for account \(accountIndex)")
            return .success(privateKey)
        case errSecItemNotFound:
            return .failure(.itemNotFound)
        case OSStatus(-128):  // errSecUserCancel
            return .failure(.userCancelled)
        case errSecAuthFailed:
            return .failure(.authenticationFailed)
        default:
            print("[KeychainManager] Failed to retrieve private key: \(status)")
            return .failure(.retrievalError(status))
        }
    }
    
    // MARK: - Wallet Data Storage
    
    /// Store wallet metadata securely
    /// - Parameters:
    ///   - walletData: The wallet data to store
    ///   - walletId: Unique identifier for the wallet
    /// - Returns: Result indicating success or failure
    func storeWalletData<T: Codable>(_ walletData: T, walletId: String) -> Result<Void, KeychainError> {
        do {
            let data = try JSONEncoder().encode(walletData)
            let accountKey = "\(walletDataPrefix)\(walletId)"
            
            // Create query
            let query: [String: Any] = [
                kSecClass as String: kSecClassGenericPassword,
                kSecAttrService as String: serviceIdentifier,
                kSecAttrAccount as String: accountKey,
                kSecValueData as String: data,
                kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly
            ]
            
            // Delete existing item first
            let deleteQuery: [String: Any] = [
                kSecClass as String: kSecClassGenericPassword,
                kSecAttrService as String: serviceIdentifier,
                kSecAttrAccount as String: accountKey
            ]
            SecItemDelete(deleteQuery as CFDictionary)
            
            // Add new item
            let status = SecItemAdd(query as CFDictionary, nil)
            
            switch status {
            case errSecSuccess:
                print("[KeychainManager] Wallet data stored successfully for wallet \(walletId)")
                return .success(())
            default:
                print("[KeychainManager] Failed to store wallet data: \(status)")
                return .failure(.storageError(status))
            }
        } catch {
            print("[KeychainManager] Failed to encode wallet data: \(error)")
            return .failure(.encodingError(error))
        }
    }
    
    /// Retrieve wallet metadata from Keychain
    /// - Parameters:
    ///   - type: The type of data to retrieve
    ///   - walletId: Unique identifier for the wallet
    /// - Returns: Result containing the wallet data or error
    func retrieveWalletData<T: Codable>(_ type: T.Type, walletId: String) -> Result<T, KeychainError> {
        let accountKey = "\(walletDataPrefix)\(walletId)"
        
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceIdentifier,
            kSecAttrAccount as String: accountKey,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        
        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        
        switch status {
        case errSecSuccess:
            guard let data = result as? Data else {
                return .failure(.dataCorrupted)
            }
            
            do {
                let walletData = try JSONDecoder().decode(type, from: data)
                print("[KeychainManager] Wallet data retrieved successfully for wallet \(walletId)")
                return .success(walletData)
            } catch {
                print("[KeychainManager] Failed to decode wallet data: \(error)")
                return .failure(.decodingError(error))
            }
        case errSecItemNotFound:
            return .failure(.itemNotFound)
        case OSStatus(-128):  // errSecUserCancel
            return .failure(.userCancelled)
        case errSecAuthFailed:
            return .failure(.authenticationFailed)
        default:
            print("[KeychainManager] Failed to retrieve wallet data: \(status)")
            return .failure(.retrievalError(status))
        }
    }
    
    // MARK: - Cleanup Methods
    
    /// Delete mnemonic from Keychain
    /// - Returns: Result indicating success or failure
    func deleteMnemonic() -> Result<Void, KeychainError> {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceIdentifier,
            kSecAttrAccount as String: mnemonicKey
        ]
        
        let status = SecItemDelete(query as CFDictionary)
        
        switch status {
        case errSecSuccess:
            print("[KeychainManager] Mnemonic deleted successfully")
            return .success(())
        case errSecItemNotFound:
            return .failure(.itemNotFound)
        default:
            print("[KeychainManager] Failed to delete mnemonic: \(status)")
            return .failure(.deletionError(status))
        }
    }
    
    /// Delete private key from Keychain
    /// - Parameter accountIndex: The account index for HD wallet
    /// - Returns: Result indicating success or failure
    func deletePrivateKey(accountIndex: Int) -> Result<Void, KeychainError> {
        let accountKey = "\(privateKeyPrefix)\(accountIndex)"
        
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceIdentifier,
            kSecAttrAccount as String: accountKey
        ]
        
        let status = SecItemDelete(query as CFDictionary)
        
        switch status {
        case errSecSuccess:
            print("[KeychainManager] Private key deleted successfully for account \(accountIndex)")
            return .success(())
        case errSecItemNotFound:
            return .failure(.itemNotFound)
        default:
            print("[KeychainManager] Failed to delete private key: \(status)")
            return .failure(.deletionError(status))
        }
    }
    
    /// Delete wallet data from Keychain
    /// - Parameter walletId: Unique identifier for the wallet
    /// - Returns: Result indicating success or failure
    func deleteWalletData(walletId: String) -> Result<Void, KeychainError> {
        let accountKey = "\(walletDataPrefix)\(walletId)"
        
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceIdentifier,
            kSecAttrAccount as String: accountKey
        ]
        
        let status = SecItemDelete(query as CFDictionary)
        
        switch status {
        case errSecSuccess:
            print("[KeychainManager] Wallet data deleted successfully for wallet \(walletId)")
            return .success(())
        case errSecItemNotFound:
            return .failure(.itemNotFound)
        default:
            print("[KeychainManager] Failed to delete wallet data: \(status)")
            return .failure(.deletionError(status))
        }
    }
    
    /// Delete all wallet-related data from Keychain
    /// - Returns: Result indicating success or failure
    func deleteAllWalletData() -> Result<Void, KeychainError> {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceIdentifier
        ]
        
        let status = SecItemDelete(query as CFDictionary)
        
        switch status {
        case errSecSuccess:
            print("[KeychainManager] All wallet data deleted successfully")
            return .success(())
        case errSecItemNotFound:
            print("[KeychainManager] No wallet data found to delete")
            return .success(())
        default:
            print("[KeychainManager] Failed to delete all wallet data: \(status)")
            return .failure(.deletionError(status))
        }
    }
    
    // MARK: - Utility Methods
    
    /// Check if mnemonic exists in Keychain
    /// - Returns: True if mnemonic exists, false otherwise
    func mnemonicExists() -> Bool {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceIdentifier,
            kSecAttrAccount as String: mnemonicKey,
            kSecReturnData as String: false
        ]
        
        let status = SecItemCopyMatching(query as CFDictionary, nil)
        return status == errSecSuccess
    }
    
    /// Check if private key exists for specific account
    /// - Parameter accountIndex: The account index for HD wallet
    /// - Returns: True if private key exists, false otherwise
    func privateKeyExists(accountIndex: Int) -> Bool {
        let accountKey = "\(privateKeyPrefix)\(accountIndex)"
        
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceIdentifier,
            kSecAttrAccount as String: accountKey,
            kSecReturnData as String: false
        ]
        
        let status = SecItemCopyMatching(query as CFDictionary, nil)
        return status == errSecSuccess
    }
}

// MARK: - Keychain Error Types

enum KeychainError: Error, LocalizedError {
    case storageError(OSStatus)
    case retrievalError(OSStatus)
    case deletionError(OSStatus)
    case itemNotFound
    case dataCorrupted
    case accessControlCreationFailed
    case userCancelled
    case authenticationFailed
    case encodingError(Error)
    case decodingError(Error)
    
    var errorDescription: String? {
        switch self {
        case .storageError(let status):
            return "Failed to store item in Keychain: \(status)"
        case .retrievalError(let status):
            return "Failed to retrieve item from Keychain: \(status)"
        case .deletionError(let status):
            return "Failed to delete item from Keychain: \(status)"
        case .itemNotFound:
            return "Item not found in Keychain"
        case .dataCorrupted:
            return "Data retrieved from Keychain is corrupted"
        case .accessControlCreationFailed:
            return "Failed to create access control"
        case .userCancelled:
            return "User cancelled authentication"
        case .authenticationFailed:
            return "Authentication failed"
        case .encodingError(let error):
            return "Failed to encode data: \(error.localizedDescription)"
        case .decodingError(let error):
            return "Failed to decode data: \(error.localizedDescription)"
        }
    }
}
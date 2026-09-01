//
//  WalletTypes.swift
//  WatchWallet Watch App
//
//  Local type definitions for wallet management
//

import Foundation

/// Wallet data model for local Swift usage
struct WalletData: Identifiable, Codable {
    let id: String
    let name: String
    let address: String
    let mnemonic: String?
    let networkType: String
    let createdAt: Date
    let isHardwareWallet: Bool
    let chainId: String
    
    init(id: String = UUID().uuidString, name: String, address: String, mnemonic: String? = nil, networkType: String = "ethereum", createdAt: Date = Date(), isHardwareWallet: Bool = false, chainId: String = "1") {
        self.id = id
        self.name = name
        self.address = address
        self.mnemonic = mnemonic
        self.networkType = networkType
        self.createdAt = createdAt
        self.isHardwareWallet = isHardwareWallet
        self.chainId = chainId
    }
}

/// Wallet error types
enum WalletError: LocalizedError {
    case creationFailed(String)
    case importFailed(String)
    case notFound
    case invalidMnemonic
    case cryptoError(String)
    case storageError(String)
    case unknown(String)
    
    // Additional cases needed by WalletRepositoryManager
    case mnemonicGenerationFailed
    case addressGenerationFailed
    case retrievalFailed(String)
    case walletNotFound
    case deletionFailed(String)
    
    var errorDescription: String? {
        switch self {
        case .creationFailed(let message): return "Wallet creation failed: \(message)"
        case .importFailed(let message): return "Wallet import failed: \(message)"
        case .notFound, .walletNotFound: return "Wallet not found"
        case .invalidMnemonic: return "Invalid mnemonic phrase"
        case .cryptoError(let message): return "Crypto error: \(message)"
        case .storageError(let message): return "Storage error: \(message)"
        case .unknown(let message): return "Unknown error: \(message)"
        case .mnemonicGenerationFailed: return "Failed to generate mnemonic"
        case .addressGenerationFailed: return "Failed to generate address"
        case .retrievalFailed(let message): return "Retrieval failed: \(message)"
        case .deletionFailed(let message): return "Deletion failed: \(message)"
        }
    }
}

// Note: SwiftGasEstimation, SwiftToken, and SwiftTransaction are defined in KMPUseCaseDirect.swift

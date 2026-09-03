//
//  Web3RepositoryHelper.swift
//  WatchWallet Watch App
//
//  Helper to access real KMP Web3Repository instance with Web3Service
//

import Foundation
import coreKmp

/// Helper class for accessing KMP repository instances
class Web3RepositoryHelper {
    
    // Web3Bridge doesn't exist in coreKmp, using TokenRepository instead
    private static var tokenRepository: coreKmp.TokenRepository?
    private static var walletRepository: coreKmp.WalletRepository?
    
    /// Get the shared TokenRepository instance
    static func getTokenRepository() -> Swift.Result<coreKmp.TokenRepository, Error> {
        // Return cached instance if available
        if let cached = tokenRepository {
            print("[Web3RepositoryHelper] Returning cached TokenRepository instance")
            return .success(cached)
        }
        
        // Get repository from DIContainer
        if let repository = DIContainer.shared.getTokenRepository() {
            // Cache the instance
            tokenRepository = repository
            
            print("[Web3RepositoryHelper] Successfully obtained TokenRepository")
            return .success(repository)
        } else {
            let error = NSError(
                domain: "Web3RepositoryHelper",
                code: -1,
                userInfo: [NSLocalizedDescriptionKey: "Failed to get TokenRepository from Koin"]
            )
            print("[Web3RepositoryHelper] Failed to get TokenRepository: \(error)")
            return .failure(error)
        }
    }
    
    /// Get the shared WalletRepository instance
    static func getWalletRepository() -> Swift.Result<coreKmp.WalletRepository, Error> {
        // Return cached instance if available
        if let cached = walletRepository {
            print("[Web3RepositoryHelper] Returning cached WalletRepository instance")
            return .success(cached)
        }
        
        // Get repository from DIContainer
        if let repository = DIContainer.shared.getWalletRepository() {
            // Cache the instance
            walletRepository = repository
            
            print("[Web3RepositoryHelper] Successfully obtained WalletRepository")
            return .success(repository)
        } else {
            let error = NSError(
                domain: "Web3RepositoryHelper",
                code: -1,
                userInfo: [NSLocalizedDescriptionKey: "Failed to get WalletRepository from Koin"]
            )
            print("[Web3RepositoryHelper] Failed to get WalletRepository: \(error)")
            return .failure(error)
        }
    }
    
    /// Clear cached repositories (useful for testing or reinitialization)
    static func clearCache() {
        print("[Web3RepositoryHelper] Clearing cached repositories")
        tokenRepository = nil
        walletRepository = nil
    }
    
    /// Get supported chain IDs
    static func getSupportedChains() -> [String: String] {
        return [
            "1": "Ethereum Mainnet",
            "11155111": "Ethereum Sepolia", 
            "56": "BNB Smart Chain",
            "137": "Polygon",
            "25": "Cronos"
        ]
    }
    
    /// Check if a chain ID is supported
    static func isChainSupported(chainId: String) -> Bool {
        return getSupportedChains().keys.contains(chainId)
    }
    
    /// Get chain information
    static func getChainInfo(chainId: String) -> (symbol: String, name: String)? {
        switch chainId {
        case "1":
            return (symbol: "ETH", name: "Ethereum")
        case "11155111":
            return (symbol: "ETH", name: "Ethereum Sepolia")
        case "56":
            return (symbol: "BNB", name: "BNB Smart Chain")
        case "137":
            return (symbol: "MATIC", name: "Polygon")
        case "25":
            return (symbol: "CRO", name: "Cronos")
        default:
            return nil
        }
    }
}
//
//  TokenManagementViewModel.swift
//  WatchWallet Watch App
//
//  ViewModel for token management
//

import Foundation
import SwiftUI
import coreKmp

@MainActor
class TokenManagementViewModel: ObservableObject {
    @Published var allTokens: [TokenInfo] = []
    @Published var enabledTokens: Set<String> = []
    @Published var isLoading = false
    @Published var error: String?
    
    // MARK: - Dependencies
    // Note: TokenRepository implementation not available yet, using local storage
    
    // Shared default tokens list for access by other ViewModels across the app
    static let defaultTokens: [TokenInfo] = [
        TokenInfo(
            id: "eth",
            contractAddress: "0x0000000000000000000000000000000000000000",
            symbol: "ETH",
            name: "Ethereum",
            decimals: 18,
            chainId: "1",
            isCustom: false
        ),
        TokenInfo(
            id: "usdt",
            contractAddress: "0xdAC17F958D2ee523a2206206994597C13D831ec7",
            symbol: "USDT",
            name: "Tether USD",
            decimals: 6,
            chainId: "1",
            isCustom: false
        ),
        TokenInfo(
            id: "usdc",
            contractAddress: "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48",
            symbol: "USDC",
            name: "USD Coin",
            decimals: 6,
            chainId: "1",
            isCustom: false
        ),
        TokenInfo(
            id: "dai",
            contractAddress: "0x6B175474E89094C44Da98b954EedeAC495271d0F",
            symbol: "DAI",
            name: "Dai Stablecoin",
            decimals: 18,
            chainId: "1",
            isCustom: false
        ),
        TokenInfo(
            id: "wbtc",
            contractAddress: "0x2260FAC5E5542a773Aa44fBCfeDf7C193bc2C599",
            symbol: "WBTC",
            name: "Wrapped Bitcoin",
            decimals: 8,
            chainId: "1",
            isCustom: false
        ),
        TokenInfo(
            id: "link",
            contractAddress: "0x514910771AF9Ca656af840dff83E8264EcF986CA",
            symbol: "LINK",
            name: "Chainlink",
            decimals: 18,
            chainId: "1",
            isCustom: false
        ),
        TokenInfo(
            id: "uni",
            contractAddress: "0x1f9840a85d5aF5bf1D1762F925BDADdC4201F984",
            symbol: "UNI",
            name: "Uniswap",
            decimals: 18,
            chainId: "1",
            isCustom: false
        )
    ]
    
    init() {
        loadTokens()
        loadEnabledTokens()
    }
    
    func loadTokens() {
        // Default popular tokens
        allTokens = Self.defaultTokens
        
        // Load custom tokens
        loadCustomTokens()
    }
    
    func loadEnabledTokens() {
        // Load from UserDefaults
        if let savedTokens = UserDefaults.standard.array(forKey: "enabledTokens") as? [String] {
            enabledTokens = Set(savedTokens)
        } else {
            // Default enabled tokens
            enabledTokens = ["eth", "usdt", "usdc"]
        }
    }
    
    func isTokenEnabled(_ token: TokenInfo) -> Bool {
        enabledTokens.contains(token.id)
    }
    
    func toggleToken(_ token: TokenInfo, enabled: Bool) {
        if enabled {
            enabledTokens.insert(token.id)
        } else {
            enabledTokens.remove(token.id)
        }
        saveEnabledTokens()
        
        // Notify other parts of the app
        NotificationCenter.default.post(
            name: .tokensUpdated,
            object: nil,
            userInfo: ["enabledTokens": Array(enabledTokens)]
        )
    }
    
    func addCustomToken(_ token: TokenInfo) {
        allTokens.append(token)
        enabledTokens.insert(token.id)
        saveCustomTokens()
        saveEnabledTokens()
    }
    
    func removeCustomToken(_ token: TokenInfo) {
        guard token.isCustom else { return }
        allTokens.removeAll { $0.id == token.id }
        enabledTokens.remove(token.id)
        saveCustomTokens()
        saveEnabledTokens()
    }
    
    private func loadCustomTokens() {
        // Load from UserDefaults or local storage
        // This is a simplified implementation
        _ = allTokens // Suppress unused warning
    }
    
    private func saveCustomTokens() {
        // Save custom tokens to UserDefaults or local storage
        let customTokens = allTokens.filter { $0.isCustom }
        // Encode and save...
    }
    
    private func saveEnabledTokens() {
        UserDefaults.standard.set(Array(enabledTokens), forKey: "enabledTokens")
    }
}

extension Notification.Name {
    static let tokensUpdated = Notification.Name("tokensUpdated")
}
//
//  NetworkSettingsViewModel.swift
//  WatchWallet Watch App
//
//  ViewModel for network settings
//

import Foundation
import SwiftUI
import coreKmp

@MainActor
class NetworkSettingsViewModel: ObservableObject {
    @Published var networks: [NetworkModel] = []
    @Published var selectedNetwork: NetworkModel?
    @Published var isLoading = false
    @Published var error: String?
    
    init() {
        loadNetworks()
    }
    
    func loadNetworks() {
        // Try to load from KMP CommonChains first
        if let commonChains = loadKMPChains() {
            networks = commonChains
        } else {
            // Fallback to default networks
            networks = [
                NetworkModel(
                    id: "1",
                    name: "Ethereum",
                    chainId: "1",
                    rpcUrl: "https://eth.llamarpc.com",
                    symbol: "ETH",
                    explorerUrl: "https://etherscan.io",
                    isTestnet: false,
                    isCustom: false
                ),
            NetworkModel(
                id: "11155111",
                name: "Ethereum Sepolia",
                chainId: "11155111",
                rpcUrl: "https://rpc.sepolia.org",
                symbol: "ETH",
                explorerUrl: "https://sepolia.etherscan.io",
                isTestnet: true,
                isCustom: false
            ),
            NetworkModel(
                id: "137",
                name: "Polygon",
                chainId: "137",
                rpcUrl: "https://polygon-rpc.com",
                symbol: "MATIC",
                explorerUrl: "https://polygonscan.com",
                isTestnet: false,
                isCustom: false
            ),
            NetworkModel(
                id: "56",
                name: "BNB Smart Chain",
                chainId: "56",
                rpcUrl: "https://bsc-dataseed.binance.org",
                symbol: "BNB",
                explorerUrl: "https://bscscan.com",
                isTestnet: false,
                isCustom: false
            ),
            NetworkModel(
                id: "42161",
                name: "Arbitrum One",
                chainId: "42161",
                rpcUrl: "https://arb1.arbitrum.io/rpc",
                symbol: "ETH",
                explorerUrl: "https://arbiscan.io",
                isTestnet: false,
                isCustom: false
            ),
            NetworkModel(
                id: "10",
                name: "Optimism",
                chainId: "10",
                rpcUrl: "https://mainnet.optimism.io",
                symbol: "ETH",
                explorerUrl: "https://optimistic.etherscan.io",
                isTestnet: false,
                isCustom: false
            )
            ]
        }
        
        // Load saved network preference
        if let savedNetworkId = UserDefaults.standard.string(forKey: "selectedNetworkId"),
           let network = networks.first(where: { $0.id == savedNetworkId }) {
            selectedNetwork = network
        } else {
            selectedNetwork = networks.first
        }
        
        // Load custom networks
        loadCustomNetworks()
    }
    
    func selectNetwork(_ network: NetworkModel) {
        selectedNetwork = network
        UserDefaults.standard.set(network.id, forKey: "selectedNetworkId")
        
        // Notify other parts of the app about network change
        NotificationCenter.default.post(
            name: .networkChanged,
            object: nil,
            userInfo: ["network": network]
        )
    }
    
    func addCustomNetwork(_ network: NetworkModel) {
        networks.append(network)
        saveCustomNetworks()
    }
    
    func removeCustomNetwork(_ network: NetworkModel) {
        guard network.isCustom else { return }
        networks.removeAll { $0.id == network.id }
        
        // If removed network was selected, switch to mainnet
        if selectedNetwork?.id == network.id {
            selectNetwork(networks.first ?? networks[0])
        }
        
        saveCustomNetworks()
    }
    
    private func loadCustomNetworks() {
        // Load from UserDefaults or local storage
        // This is a simplified implementation
    }
    
    private func saveCustomNetworks() {
        // Save custom networks to UserDefaults or local storage
        // This is a simplified implementation
    }
    
    private func loadKMPChains() -> [NetworkModel]? {
        // Try to load chains from coreKmp CommonChains
        // KMP integration is simplified, returning nil to use hardcoded defaults
        print("[NetworkSettingsViewModel] Using hardcoded network list (KMP bridge simplified)")
        return nil
    }
    
    private func getRPCUrl(for chainId: String) -> String {
        // Get RPC URL from environment or use defaults
        switch chainId {
        case "1": return "https://eth.llamarpc.com"
        case "11155111": return "https://rpc.sepolia.org"
        case "137": return "https://polygon-rpc.com"
        case "56": return "https://bsc-dataseed.binance.org"
        case "42161": return "https://arb1.arbitrum.io/rpc"
        case "10": return "https://mainnet.optimism.io"
        default: return ""
        }
    }
    
    private func getExplorerUrl(for chainId: String) -> String {
        switch chainId {
        case "1": return "https://etherscan.io"
        case "11155111": return "https://sepolia.etherscan.io"
        case "137": return "https://polygonscan.com"
        case "56": return "https://bscscan.com"
        case "42161": return "https://arbiscan.io"
        case "10": return "https://optimistic.etherscan.io"
        default: return ""
        }
    }
}

extension Notification.Name {
    static let networkChanged = Notification.Name("networkChanged")
}
//
//  WalletMainViewModel.swift
//  WatchWallet Watch App
//
//  ViewModel for the main wallet view
//

import Foundation
import SwiftUI
import Combine
import coreKmp

@MainActor
class WalletMainViewModel: ObservableObject {
    // MARK: - Published Properties
    @Published var currentWallet: WalletModel?
    @Published var wallets: [WalletModel] = []
    @Published var balance: String = "0.00"
    @Published var selectedToken: TokenModel?
    @Published var availableTokens: [TokenModel] = []
    @Published var recentTransactions: [TransactionModel] = []
    @Published var isLoading = false
    @Published var error: String?
    
    // MARK: - Dependencies
    private let walletRepository = WalletRepositoryManager.shared
    
    // MARK: - Navigation
    var onNavigateToSend: (() -> Void)?
    var onNavigateToReceive: (() -> Void)?
    var onNavigateToHistory: (() -> Void)?
    
    // MARK: - Initialization
    init() {
        setupDefaultTokens()
        loadWallets()
    }
    
    // MARK: - Public Methods
    func onAppear() {
        refreshBalance()
        loadRecentTransactions()
    }
    
    func refreshBalance() {
        guard let wallet = currentWallet else { 
            balance = "0.00"
            error = nil
            return 
        }
        
        isLoading = true
        error = nil
        
        Task {
            do {
                let chainType = getKMPChainType(wallet.chainId)
                var updatedTokens = self.availableTokens
                
                // Use TaskGroup for concurrent fetching
                try await withThrowingTaskGroup(of: (String, SwiftToken).self) { group in
                    // 1. Fetch Native Token Balance
                    group.addTask {
                        let token = try await KMPUseCaseDirect.shared.getTokenBalance(
                            walletAddress: wallet.address,
                            tokenAddress: nil,
                            chainType: chainType
                        )
                        return ("native", token)
                    }
                    
                    // 2. Fetch ERC20 Token Balances
                    for token in self.availableTokens where !token.isNative {
                        guard let contractAddr = token.contractAddress else { continue }
                        group.addTask {
                            let t = try await KMPUseCaseDirect.shared.getTokenBalance(
                                walletAddress: wallet.address,
                                tokenAddress: contractAddr,
                                chainType: chainType
                            )
                            return (token.id, t)
                        }
                    }
                    
                    // 3. Collect Results
                    for try await (id, tokenData) in group {
                        if id == "native" {
                            await MainActor.run {
                                self.balance = "\(tokenData.balance) \(tokenData.symbol)"
                            }
                        }
                        
                        if let index = updatedTokens.firstIndex(where: { $0.id == id }) {
                            updatedTokens[index].balance = tokenData.balance
                        }
                    }
                }
                
                await MainActor.run {
                    self.isLoading = false
                    self.availableTokens = updatedTokens
                }
            } catch {
                await MainActor.run {
                    self.isLoading = false
                    print("[WalletMainViewModel] Balance refresh failed: \(error.localizedDescription)")
                }
            }
        }
    }
    
    func switchWallet(to wallet: WalletModel) {
        currentWallet = wallet
        // 保存活動錢包 ID 到 UserDefaults
        UserDefaults.standard.set(wallet.id, forKey: "activeWalletId")
        loadBalanceForWallet(wallet) // Load tokens and then refresh
        loadRecentTransactions()
    }
    
    func selectToken(_ token: TokenModel) {
        selectedToken = token
        // Use the token's balance for the main display if available
        if let bal = token.balance {
            balance = "\(bal) \(token.symbol)"
        }
    }
    
    func navigateToSend() {
        onNavigateToSend?()
    }
    
    func navigateToReceive() {
        onNavigateToReceive?()
    }
    
    func navigateToHistory() {
        onNavigateToHistory?()
    }
    
    // MARK: - Private Methods
    private func getChainName(chainId: String) -> String {
        switch chainId {
        case "1", "11155111", "17000":
            return "Ethereum"
        case "56", "97":
            return "BNB Smart Chain"
        case "137", "80001":
            return "Polygon"
        case "25", "338":
            return "Cronos"
        case "42161", "421614":
            return "Arbitrum"
        case "8453", "84532":
            return "Base"
        case "10", "11155420":
            return "Optimism"
        case "bitcoin":
            return "Bitcoin"
        case "litecoin":
            return "Litecoin"
        case "dogecoin":
            return "Dogecoin"
        default:
            return "Unknown Network"
        }
    }
    
    private func setupDefaultTokens() {
        // Initial setup handled in loadBalanceForWallet now
        availableTokens = []
    }
    
    private func loadWallets() {
        Task {
            do {
                let kmpWallets = try await walletRepository.getAllWalletsAsync()
                await MainActor.run {
                    self.wallets = kmpWallets.map { wallet in
                        WalletModel(
                            id: wallet.id,
                            name: wallet.name,
                            address: wallet.address,
                            type: wallet.isHardwareWallet ? .hardware : .hot,
                            chainId: wallet.chainId
                        )
                    }
                    
                    self.error = nil
                    
                    if self.wallets.isEmpty {
                        self.currentWallet = nil
                        self.balance = "0.00"
                    } else {
                        // 從 UserDefaults 讀取保存的活動錢包 ID
                        let savedWalletId = UserDefaults.standard.string(forKey: "activeWalletId")
                        
                        if let savedId = savedWalletId,
                           let savedWallet = self.wallets.first(where: { $0.id == savedId }) {
                            // 恢復之前選擇的錢包
                            self.currentWallet = savedWallet
                        } else if self.currentWallet == nil {
                            // 沒有保存的選擇，使用第一個錢包
                            self.currentWallet = self.wallets.first
                        }
                        
                        if let currentWallet = self.currentWallet {
                            self.loadBalanceForWallet(currentWallet)
                        }
                    }
                }
            } catch {
                await MainActor.run {
                    self.error = "無法載入錢包: \(error.localizedDescription)"
                }
            }
        }
    }
    
    private func loadBalanceForWallet(_ wallet: WalletModel) {
        // 1. Native Token
        let nativeToken = TokenModel(
            id: "native",
            symbol: wallet.chainId == "bitcoin" ? "BTC" : (wallet.chainId == "litecoin" ? "LTC" : (wallet.chainId == "dogecoin" ? "DOGE" : "ETH")),
            name: self.getChainName(chainId: wallet.chainId),
            chainId: wallet.chainId,
            contractAddress: nil, // Native token has no contract address
            decimals: 18,
            balance: "0.00",
            usdValue: "0.00",
            isNative: true
        )
        
        // 2. Load Enabled Tokens from UserDefaults & TokenManagementViewModel
        var walletTokens = [nativeToken]
        
        let enabledIds = UserDefaults.standard.array(forKey: "enabledTokens") as? [String] ?? ["eth", "usdt", "usdc"] // Default fallback
        let allTokens = TokenManagementViewModel.defaultTokens
        
        for tokenId in enabledIds {
            // Check if token exists and matches current wallet's chain
            if let info = allTokens.first(where: { $0.id == tokenId && $0.chainId == wallet.chainId }) {
                // Determine if we should fix the contract address (Mock fix)
                // In KMP/Simulated env, KMP might expect specific mock addresses unless we are on Mainnet
                // For now, pass the address from TokenInfo
                
                let tokenModel = TokenModel(
                    id: info.id,
                    symbol: info.symbol,
                    name: info.name,
                    chainId: info.chainId,
                    contractAddress: info.contractAddress,
                    decimals: info.decimals,
                    balance: "0.00", // Start with 0, fetch later
                    isNative: false,
                    isCustom: info.isCustom
                )
                walletTokens.append(tokenModel)
            }
        }
        
        self.availableTokens = walletTokens
        self.selectedToken = nativeToken // Default to native
        
        // 3. Fetch Real Balances
        refreshBalance()
    }
    
    private func loadRecentTransactions() {
        guard let wallet = currentWallet else { return }
        
        Task {
            do {
                let chainType = getKMPChainType(wallet.chainId)
                let kmpTransactions = try await KMPUseCaseDirect.shared.getTransactionHistory(
                    address: wallet.address,
                    chainType: chainType,
                    limit: 5
                )
                
                await MainActor.run {
                    self.recentTransactions = kmpTransactions.map { tx in
                        TransactionModel(
                            id: tx.hash,
                            hash: tx.hash,
                            from: tx.from,
                            to: tx.to,
                            value: tx.value,
                            symbol: self.selectedToken?.symbol ?? "ETH",
                            timestamp: tx.timestamp,
                            status: self.mapTransactionStatus(tx.status),
                            type: tx.to.lowercased() == wallet.address.lowercased() ? .received : .sent
                        )
                    }
                }
            } catch {
                print("[WalletMainViewModel] Error loading recent transactions: \(error)")
            }
        }
    }
    
    private func getKMPChainType(_ chainId: String) -> coreKmp.ChainType {
        switch chainId {
        case "1": return .ethereum
        case "56": return .bsc
        case "137": return .polygon
        case "bitcoin": return .bitcoin
        case "litecoin": return .litecoin
        case "dogecoin": return .dogecoin
        default: return .ethereum
        }
    }

    // Helper functions for transaction mapping
    private func mapTransactionStatus(_ status: String) -> TransactionStatus {
        switch status.lowercased() {
        case "pending":
            return .pending
        case "completed", "success":
            return .completed
        case "failed":
            return .failed
        default:
            return .pending
        }
    }
}



//
//  TransactionHistoryViewModel.swift
//  WatchWallet Watch App
//
//  ViewModel for transaction history
//

import Foundation
import SwiftUI
import coreKmp

@MainActor
class TransactionHistoryViewModel: ObservableObject {
    // MARK: - Published Properties
    @Published var transactions: [TransactionModel] = []
    @Published var filteredTransactions: [TransactionModel] = []
    @Published var availableNetworks: [String] = []
    @Published var isLoading = false
    @Published var isLoadingMore = false
    @Published var hasMoreTransactions = true
    @Published var error: String?
    @Published var selectedTransaction: TransactionModel?
    
    // MARK: - Private Properties
    private var allTransactions: [TransactionModel] = []
    private var currentSearchQuery = ""
    private var currentFilter: TransactionFilter = .all
    private var currentNetworkFilter = "all"
    private var currentPage = 0
    private let pageSize = 20
    
    // MARK: - Dependencies
    private let walletRepository = WalletRepositoryManager.shared
    
    // MARK: - Public Methods
    func loadTransactions() {
        Task {
            isLoading = true
            error = nil
            currentPage = 0
            hasMoreTransactions = true
            
            await loadTransactionsPage()
        }
    }
    
    func refreshTransactions() async {
        currentPage = 0
        hasMoreTransactions = true
        allTransactions.removeAll()
        transactions.removeAll()
        
        await loadTransactionsPage()
    }
    
    func loadMoreTransactions() async {
        guard !isLoadingMore && hasMoreTransactions else { return }
        
        currentPage += 1
        await loadTransactionsPage()
    }
    
    private func loadTransactionsPage() async {
        let loading = currentPage == 0
        if loading {
            isLoading = true
        } else {
            isLoadingMore = true
        }
        
        do {
            // Get current wallet
            let wallets = try await walletRepository.getAllWalletsAsync()
            
            // 使用保存的 activeWalletId 選擇正確的錢包
            let savedWalletId = UserDefaults.standard.string(forKey: "activeWalletId")
            let currentWallet = wallets.first(where: { $0.id == savedWalletId }) ?? wallets.first
            guard let currentWallet = currentWallet else {
                self.error = "未找到錢包"
                isLoading = false
                isLoadingMore = false
                return
            }
            
            let chainType = getKMPChainType(chainId: currentWallet.chainId)
            
            // Use real transaction history API
            let kmpTransactions = try await KMPUseCaseDirect.shared.getTransactionHistory(
                address: currentWallet.address,
                chainType: chainType,
                page: currentPage + 1,
                limit: pageSize
            )
            
            let newTransactions = kmpTransactions.map { tx in
                TransactionModel(
                    id: tx.hash,
                    hash: tx.hash,
                    from: tx.from,
                    to: tx.to,
                    value: tx.value,
                    symbol: "ETH", // Default symbol, might need refinement
                    timestamp: tx.timestamp,
                    status: self.mapTransactionStatus(tx.status),
                    type: tx.to.lowercased() == currentWallet.address.lowercased() ? .received : .sent,
                    chainName: self.getChainName(chainId: currentWallet.chainId)
                )
            }
            
            // Update transactions
            if currentPage == 0 {
                allTransactions = newTransactions
                transactions = newTransactions
            } else {
                allTransactions.append(contentsOf: newTransactions)
                transactions.append(contentsOf: newTransactions)
            }
            
            // Update available networks
            updateAvailableNetworks()
            
            // Check if there are more transactions
            hasMoreTransactions = kmpTransactions.count == pageSize
            
            // Apply current filters
            applyAllFilters()
            
        } catch {
            self.error = "無法載入交易紀錄: \(error.localizedDescription)"
        }
        
        isLoading = false
        isLoadingMore = false
    }
    
    // Mapping from chainId to ChainType
    private func getKMPChainType(chainId: String) -> coreKmp.ChainType {
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
    
    func filterTransactions(by filter: TransactionFilter) {
        currentFilter = filter
        applyAllFilters()
    }
    
    func searchTransactions(query: String) {
        currentSearchQuery = query.lowercased()
        applyAllFilters()
    }
    
    func filterByNetwork(_ network: String) {
        currentNetworkFilter = network
        applyAllFilters()
    }
    
    func resetFilters() {
        currentFilter = .all
        currentSearchQuery = ""
        currentNetworkFilter = "all"
        applyAllFilters()
    }
    
    func selectTransaction(_ transaction: TransactionModel) {
        selectedTransaction = transaction
    }
    
    func showTransactionDetail(_ transaction: TransactionModel) {
        selectedTransaction = transaction
        // TODO: Navigate to detail view
    }
    
    func shareTransaction(_ transaction: TransactionModel) {
        // TODO: Implement share functionality
        // Share implementation would go here
        print("Sharing transaction: \(transaction.hash) - \(transaction.amountText)")
    }
    
    private func applyAllFilters() {
        var filtered = allTransactions
        
        // Apply search filter
        if !currentSearchQuery.isEmpty {
            filtered = filtered.filter { transaction in
                transaction.hash.lowercased().contains(currentSearchQuery) ||
                transaction.from.lowercased().contains(currentSearchQuery) ||
                transaction.to.lowercased().contains(currentSearchQuery) ||
                transaction.symbol.lowercased().contains(currentSearchQuery) ||
                (transaction.chainName?.lowercased().contains(currentSearchQuery) ?? false)
            }
        }
        
        // Apply type filter
        switch currentFilter {
        case .all:
            break
        case .sent:
            filtered = filtered.filter { $0.type == .sent }
        case .received:
            filtered = filtered.filter { $0.type == .received }
        }
        
        // Apply network filter
        if currentNetworkFilter != "all" {
            filtered = filtered.filter { $0.chainName == currentNetworkFilter }
        }
        
        filteredTransactions = filtered
    }
    
    private func updateAvailableNetworks() {
        let networks = Set(allTransactions.compactMap { $0.chainName })
        availableNetworks = Array(networks).sorted()
    }
    
    // MARK: - Private Methods
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
    
    private func mapTransactionType(_ type: String, currentAddress: String) -> TransactionType {
        // Determine type based on transaction type and addresses
        switch type.lowercased() {
        case "sent":
            return .sent
        case "received":
            return .received
        case "contract_interaction", "token_approval", "token_transfer":
            // For other types, determine based on from/to addresses
            // This is simplified logic - in production would need more checks
            return .sent
        default:
            return .sent
        }
    }
    
    /// Get chain name for a given chain ID
    private func getChainName(chainId: String) -> String {
        switch chainId {
        case "1": return "Ethereum"
        case "11155111": return "Ethereum Sepolia"
        case "56": return "BNB Smart Chain"
        case "137": return "Polygon"
        case "25": return "Cronos"
        default: return "Ethereum"
        }
    }
}
//
//  ReceiveViewModel.swift
//  WatchWallet Watch App
//
//  ViewModel for receiving transactions
//

import Foundation
import SwiftUI
import coreKmp

@MainActor
class ReceiveViewModel: ObservableObject {
    // MARK: - Published Properties
    @Published var walletName: String = ""
    @Published var walletAddress: String = ""
    @Published var selectedToken: TokenModel?
    @Published var isLoading = false
    @Published var error: String?
    
    // MARK: - Dependencies
    private let walletRepository = WalletRepositoryManager.shared
    
    // MARK: - Initialization
    init() {
        loadCurrentWallet()
    }
    
    // MARK: - Public Methods
    func generatePaymentRequest(amount: String) -> String {
        // Generate payment request URI
        // Format: ethereum:address?amount=value
        var uri = "ethereum:\(walletAddress)"
        if !amount.isEmpty, let amountValue = Double(amount) {
            uri += "?amount=\(amountValue)"
        }
        return uri
    }
    
    // MARK: - Private Methods
    private func loadCurrentWallet() {
        Task {
            do {
                let wallets = try await walletRepository.getAllWalletsAsync()
                print("[ReceiveViewModel] getAllWalletsAsync returned \(wallets.count) wallets")
                
                // 使用保存的 activeWalletId 選擇正確的錢包
                let savedWalletId = UserDefaults.standard.string(forKey: "activeWalletId")
                let activeWallet = wallets.first(where: { $0.id == savedWalletId }) ?? wallets.first
                
                if let wallet = activeWallet {
                    walletName = wallet.name
                    walletAddress = wallet.address
                    
                    // Set default token
                    selectedToken = TokenModel(
                        id: "1",
                        symbol: "ETH",
                        name: "Ethereum",
                        chainId: wallet.chainId
                    )
                    
                    print("[ReceiveViewModel] Loaded wallet: \(walletName), address: \(walletAddress)")
                } else {
                    print("[ReceiveViewModel] No wallets found in the list")
                    self.error = "未找到錢包"
                    // Don't use demo data - leave empty to show the error
                    walletName = "無錢包"
                    walletAddress = ""
                }
            } catch {
                print("[ReceiveViewModel] Error loading wallet: \(error)")
                self.error = "無法載入錢包資訊：\(error.localizedDescription)"
                // Don't use demo data - leave empty to show the error
                walletName = "載入錯誤"
                walletAddress = ""
            }
        }
    }
}
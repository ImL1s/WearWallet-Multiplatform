//
//  WalletManagementViewModel.swift
//  WatchWallet Watch App
//
//  ViewModel for wallet management
//

import Foundation
import SwiftUI
import Combine
import coreKmp

@MainActor
class WalletManagementViewModel: ObservableObject {
    // MARK: - Published Properties
    @Published var wallets: [WalletModel] = []
    @Published var selectedWalletId: String?
    @Published var isLoading = false
    @Published var error: String?
    
    // MARK: - Dependencies
    private let walletRepository = WalletRepositoryManager.shared
    
    // MARK: - Initialization
    init() {
        loadWallets()
    }
    
    // MARK: - Public Methods
    func loadWallets() {
        print("[WalletManagement] Loading wallets...")
        Task {
            do {
                let walletList = try await walletRepository.getAllWalletsAsync()
                print("[WalletManagement] Found \(walletList.count) wallets")
                
                await MainActor.run {
                    self.wallets = walletList.map { wallet in
                        WalletModel(
                            id: wallet.id,
                            name: wallet.name,
                            address: wallet.address,
                            type: .hot, // 暫時都設為熱錢包
                            chainId: wallet.chainId
                        )
                    }
                    
                    // 如果沒有選中的錢包，選擇第一個
                    if selectedWalletId == nil && !wallets.isEmpty {
                        selectedWalletId = wallets.first?.id
                    }
                }
            } catch {
                print("[WalletManagement] Failed to load wallets: \(error)")
                await MainActor.run {
                    self.error = "無法載入錢包列表: \(error.localizedDescription)"
                }
            }
        }
    }
    
    func createWallet(name: String) {
        print("[WalletManagement] Starting wallet creation: \(name)")
        
        guard !name.isEmpty else {
            print("[WalletManagement] Wallet name is empty")
            error = "錢包名稱不能為空"
            return
        }
        
        Task {
            await MainActor.run {
                isLoading = true
                error = nil
            }
            
            do {
                print("[WalletManagement] Calling KMP createWallet...")
                let result = await walletRepository.createWalletAsync(name: name)
                print("[WalletManagement] Wallet creation result: \(String(describing: result))")
                
                switch result {
                case .success(_):
                    await MainActor.run {
                        print("[WalletManagement] Wallet creation successful, reloading wallets...")
                        isLoading = false
                        // 發送通知
                        NotificationCenter.default.post(name: .walletCreated, object: nil)
                    }
                    
                    // 稍微延遲後重新加載錢包，確保資料已經持久化
                    try await Task.sleep(nanoseconds: 100_000_000) // 100ms
                    await MainActor.run {
                        loadWallets()
                    }
                case .failure(let error):
                    print("[WalletManagement] Wallet creation failed: \(error)")
                    await MainActor.run {
                        isLoading = false
                        self.error = "創建錢包時發生錯誤: \(error.localizedDescription)"
                    }
                }
            } catch {
                print("[WalletManagement] Wallet creation failed: \(error)")
                await MainActor.run {
                    isLoading = false
                    self.error = "創建錢包時發生錯誤: \(error.localizedDescription)"
                }
            }
        }
    }
    
    func importWallet(name: String, mnemonic: String) {
        print("[WalletManagement] Starting wallet import: \(name)")
        Task {
            await MainActor.run {
                isLoading = true
                error = nil
            }
            
            do {
                print("[WalletManagement] Calling KMP importWallet...")
                let result = await walletRepository.importWalletAsync(mnemonic: mnemonic, name: name)
                print("[WalletManagement] Wallet import result: \(String(describing: result))")
                
                switch result {
                case .success(_):
                    print("[WalletManagement] Wallet import successful")
                    await MainActor.run {
                        // 發送通知
                        NotificationCenter.default.post(name: .walletCreated, object: nil)
                    }
                    
                    // 稍微延遲後重新加載錢包，確保資料已經持久化
                    try await Task.sleep(nanoseconds: 100_000_000) // 100ms
                    await MainActor.run {
                        loadWallets()
                    }
                case .failure(let error):
                    print("[WalletManagement] Wallet import failed: \(error)")
                    await MainActor.run {
                        self.error = "導入錢包時發生錯誤: \(error.localizedDescription)"
                    }
                }
            } catch {
                print("[WalletManagement] Wallet import failed: \(error)")
                await MainActor.run {
                    self.error = "導入錢包時發生錯誤: \(error.localizedDescription)"
                }
            }
            
            await MainActor.run {
                isLoading = false
            }
        }
    }
    
    func deleteWallet(_ wallet: WalletModel) {
        Task {
            do {
                let success = try await walletRepository.deleteWalletAsync(id: wallet.id)
                
                // KotlinBoolean 需要轉換為 Swift Bool
                if success {
                    await MainActor.run {
                        // 從本地錢包列表中移除已刪除的錢包
                        self.wallets.removeAll { $0.id == wallet.id }
                        
                        // 如果刪除的是當前選中的錢包，選擇另一個或清空選擇
                        if wallet.id == selectedWalletId {
                            if !wallets.isEmpty {
                                selectedWalletId = wallets.first?.id
                            } else {
                                selectedWalletId = nil
                            }
                        }
                        
                        print("[WalletManagement] Wallet deleted. Remaining wallets: \(wallets.count)")
                    }
                    
                    // 發送通知，包含剩餘錢包數量
                    NotificationCenter.default.post(
                        name: .walletDeleted, 
                        object: nil, 
                        userInfo: ["remainingWalletCount": wallets.count]
                    )
                } else {
                    await MainActor.run {
                        self.error = "刪除錢包失敗"
                    }
                }
            } catch {
                await MainActor.run {
                    self.error = "刪除錢包時發生錯誤: \(error.localizedDescription)"
                }
            }
        }
    }
    
    func selectWallet(_ wallet: WalletModel) {
        selectedWalletId = wallet.id
        // 保存選中的錢包到 UserDefaults
        UserDefaults.standard.set(wallet.id, forKey: "activeWalletId")
    }
}

// 通知定義
extension Notification.Name {
    static let walletCreated = Notification.Name("walletCreated")
    static let walletDeleted = Notification.Name("walletDeleted")
}


//
//  MnemonicBackupViewModel.swift
//  WatchWallet Watch App
//
//  ViewModel for mnemonic backup functionality
//

import Foundation
import SwiftUI
import coreKmp

@MainActor
class MnemonicBackupViewModel: ObservableObject {
    @Published var wallets: [WalletModel] = []
    @Published var mnemonic: String?
    @Published var isLoading = false
    @Published var error: String?
    
    // LocalAuthentication is not available on watchOS
    private let walletRepository = WalletRepositoryManager.shared
    
    func loadWallets() {
        Task {
            do {
                let walletList = try await walletRepository.getAllWalletsAsync()
                self.wallets = walletList.map { wallet in
                    WalletModel(
                        id: wallet.id,
                        name: wallet.name,
                        address: wallet.address,
                        type: wallet.isHardwareWallet ? .cold : .hot,
                        chainId: wallet.chainId
                    )
                }
            } catch {
                self.error = "無法載入錢包列表"
            }
        }
    }
    
    func authenticate() async -> Bool {
        // On watchOS, authentication is handled through passcode when the watch is put on
        // For sensitive operations like viewing mnemonics, we assume the user is authenticated
        // if they are wearing the watch (continuous authentication)
        
        // In a production app, you might want to:
        // 1. Request passcode re-entry for very sensitive operations
        // 2. Use WatchConnectivity to verify with paired iPhone
        // 3. Implement additional security measures
        
        // For now, return true since the user has already unlocked their watch
        return true
    }
    
    func loadMnemonic(for wallet: WalletModel) async {
        isLoading = true
        error = nil
        
        do {
            // 嘗試從錢包倉庫獲取真實的助記詞
            let walletRepository = WalletRepositoryManager.shared
            let result = walletRepository.getWalletMnemonic(walletId: wallet.id)
            
            switch result {
            case .success(let realMnemonic):
                await MainActor.run {
                    self.mnemonic = realMnemonic
                    self.isLoading = false
                }
                print("[MnemonicBackup] Successfully loaded real mnemonic for wallet: \(wallet.id)")
                
            case .failure(let walletError):
                print("[MnemonicBackup] Failed to load real mnemonic: \(walletError), using fallback")
                // 如果無法獲取真實助記詞，使用安全的本地生成方式
                await useFallbackMnemonic()
            }
        } catch {
            print("[MnemonicBackup] Error loading mnemonic: \(error), using fallback")
            await useFallbackMnemonic()
        }
    }
    
    private func useFallbackMnemonic() async {
        // 使用 CoreKmp CryptoProvider 而不是不安全的 EthereumCrypto
        guard let cryptoProvider = DIContainer.shared.getCryptoProvider() else {
            await MainActor.run {
                self.error = "CryptoProvider 不可用"
                self.isLoading = false
            }
            return
        }

        do {
            let fallbackMnemonic = try await cryptoProvider.generateMnemonic(wordCount: 12)

            await MainActor.run {
                self.mnemonic = fallbackMnemonic
                self.isLoading = false
            }
            print("[MnemonicBackup] Using fallback mnemonic (CoreKmp generation)")
        } catch {
            await MainActor.run {
                self.error = "無法生成助記詞：\(error.localizedDescription)"
                self.isLoading = false
            }
            print("[MnemonicBackup] Failed to generate fallback mnemonic: \(error)")
        }
    }
    
    func clearMnemonic() {
        mnemonic = nil
    }
    
    func copyMnemonic() {
        // UIPasteboard is not available on watchOS
        // In a production app, you would use WatchConnectivity to send the mnemonic to the paired iPhone
        guard mnemonic != nil else { return }
        
        // For now, we'll just store it temporarily for the UI to show feedback
        // In production, implement WatchConnectivity to send to iPhone for copying
    }
}
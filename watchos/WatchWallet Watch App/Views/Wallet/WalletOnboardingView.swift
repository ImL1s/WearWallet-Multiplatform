//
//  WalletOnboardingView.swift
//  WatchWallet Watch App
//
//  Initial wallet setup view for first-time users
//

import SwiftUI
import coreKmp

struct WalletOnboardingView: View {
    @StateObject private var viewModel = WalletManagementViewModel()
    @State private var showCreateWallet = false
    @State private var showImportWallet = false
    
    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                // 歡迎標題 - 符合 watchOS 設計原則
                VStack(spacing: 12) {
                    Image(systemName: "wallet.pass.fill")
                        .font(.system(size: 50))
                        .foregroundColor(.blue)
                    
                    Text("WatchWallet")
                        .font(.system(size: 18, weight: .bold))
                    
                    Text("創建或導入錢包開始使用")
                        .font(.system(size: 13))
                        .foregroundColor(.secondary)
                        .multilineTextAlignment(.center)
                }
                .padding(.vertical, 12)
                
                // 創建錢包按鈕
                Button(action: { showCreateWallet = true }) {
                    HStack {
                        Image(systemName: "plus.circle.fill")
                            .font(.system(size: 18))
                        Text("創建新錢包")
                            .font(.system(size: 14, weight: .medium))
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(Color.blue)
                    .foregroundColor(.white)
                    .cornerRadius(10)
                }
                .accessibilityIdentifier("CreateWalletButton")
                .disabled(viewModel.isLoading)
                .padding(.horizontal)
                
                // 導入錢包按鈕
                Button(action: { showImportWallet = true }) {
                    HStack {
                        Image(systemName: "square.and.arrow.down.fill")
                            .font(.system(size: 18))
                        Text("導入現有錢包")
                            .font(.system(size: 14, weight: .medium))
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(Color.green)
                    .foregroundColor(.white)
                    .cornerRadius(10)
                }
                .accessibilityIdentifier("ImportWalletButton")
                .disabled(viewModel.isLoading)
                .padding(.horizontal)
                
                // 安全提示
                VStack(alignment: .leading, spacing: 6) {
                    Label("安全提示", systemImage: "lock.shield")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundColor(.orange)
                    
                    VStack(alignment: .leading, spacing: 4) {
                        Text("• 請妥善保管助記詞")
                        Text("• 不要將助記詞分享給他人")
                        Text("• 丟失助記詞將無法恢復錢包")
                    }
                    .font(.system(size: 10))
                    .foregroundColor(.secondary)
                }
                .padding(12)
                .background(Color.orange.opacity(0.1))
                .cornerRadius(8)
                .padding(.horizontal)
                
                // 錯誤提示
                if let error = viewModel.error {
                    HStack {
                        Image(systemName: "exclamationmark.circle")
                            .font(.system(size: 12))
                        Text(error)
                            .font(.system(size: 12))
                    }
                    .foregroundColor(.red)
                    .padding(.horizontal)
                }
                
                // 加載指示器
                if viewModel.isLoading {
                    ProgressView("處理中...")
                        .font(.system(size: 12))
                        .padding()
                }
            }
            .padding(.vertical)
        }
        .sheet(isPresented: $showCreateWallet) {
            CreateWalletView { name in
                print("[WalletOnboarding] Create wallet flow completed: \(name)")
                // 不需要再次調用 viewModel.createWallet，因為 CreateWalletView 已經創建了
                // 發送通知或重新加載
                viewModel.loadWallets()
            }
        }
        .sheet(isPresented: $showImportWallet) {
            ImportWalletView { name, mnemonic in
                print("[WalletOnboarding] Import wallet flow completed: \(name)")
                // 不需要再次調用 viewModel.importWallet
                viewModel.loadWallets()
            }
        }
    }
}

#Preview {
    NavigationStack {
        WalletOnboardingView()
    }
}
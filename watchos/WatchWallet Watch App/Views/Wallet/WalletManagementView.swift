//
//  WalletManagementView.swift
//  WatchWallet Watch App
//
//  Wallet management view for creating, importing and managing wallets
//

import SwiftUI
import coreKmp

struct WalletManagementView: View {
    @StateObject private var viewModel = WalletManagementViewModel()
    @State private var showCreateWallet = false
    @State private var showImportWallet = false
    @State private var showDeleteConfirmation = false
    @State private var walletToDelete: WalletModel?
    @State private var showError = false
    
    var body: some View {
        ZStack {
            ScrollView {
                VStack(spacing: 8) {
                    // 添加錢包按鈕
                    if viewModel.wallets.count < 5 { // 限制最多 5 個錢包
                        AddWalletButtons(
                            onCreateTapped: { showCreateWallet = true },
                            onImportTapped: { showImportWallet = true }
                        )
                        .padding(.horizontal, 8)
                    }
                    
                    // 錢包列表
                    if viewModel.wallets.isEmpty && !viewModel.isLoading {
                        EmptyWalletView()
                    } else {
                        ForEach(viewModel.wallets) { wallet in
                            WalletCard(
                                wallet: wallet,
                                isSelected: wallet.id == viewModel.selectedWalletId,
                                onSelect: {
                                    viewModel.selectWallet(wallet)
                                },
                                onDelete: {
                                    walletToDelete = wallet
                                    showDeleteConfirmation = true
                                }
                            )
                            .padding(.horizontal, 8)
                        }
                    }
                }
                .padding(.vertical)
            }
            .navigationTitle("錢包管理")
            .navigationBarTitleDisplayMode(.inline)
            
            // 全域載入動畫
            if viewModel.isLoading {
                LoadingView(message: "正在處理錢包...")
            }
        }
        .sheet(isPresented: $showCreateWallet) {
            CreateWalletView { name in
                print("[WalletManagement] Create wallet callback: \(name)")
                viewModel.createWallet(name: name)
                // 不立即關閉，讓 CreateWalletView 自己處理
            }
        }
        .sheet(isPresented: $showImportWallet) {
            ImportWalletView { name, mnemonic in
                print("[WalletManagement] Import wallet callback: \(name)")
                viewModel.importWallet(name: name, mnemonic: mnemonic)
                // 不立即關閉，讓 ImportWalletView 自己處理
            }
        }
        .alert("刪除錢包", isPresented: $showDeleteConfirmation) {
            Button("取消", role: .cancel) {}
            Button("刪除", role: .destructive) {
                if let wallet = walletToDelete {
                    viewModel.deleteWallet(wallet)
                }
            }
        } message: {
            Text("確定要刪除這個錢包嗎？此操作無法撤銷。")
        }
        .toast(isPresented: $showError, message: viewModel.error ?? "", type: .error)
        .onChange(of: viewModel.error) { oldValue, newValue in
            if newValue != nil {
                showError = true
            }
        }
        .onAppear {
            // 只在沒有錢包時加載，避免重複加載
            if viewModel.wallets.isEmpty {
                viewModel.loadWallets()
            }
        }
    }
}

// 添加錢包按鈕
struct AddWalletButtons: View {
    let onCreateTapped: () -> Void
    let onImportTapped: () -> Void
    
    var body: some View {
        HStack(spacing: 10) {
            Button(action: onCreateTapped) {
                VStack(spacing: 4) {
                    Image(systemName: "plus.circle.fill")
                        .font(.system(size: 20))
                    Text("創建錢包")
                        .font(.system(size: 11))
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 10)
                .background(Color.blue.opacity(0.2))
                .cornerRadius(8)
            }
            .buttonStyle(.plain)
            
            Button(action: onImportTapped) {
                VStack(spacing: 4) {
                    Image(systemName: "square.and.arrow.down.fill")
                        .font(.system(size: 20))
                    Text("導入錢包")
                        .font(.system(size: 11))
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 10)
                .background(Color.green.opacity(0.2))
                .cornerRadius(8)
            }
            .buttonStyle(.plain)
        }
    }
}

// 空錢包視圖
struct EmptyWalletView: View {
    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: "wallet.pass")
                .font(.system(size: 40))
                .foregroundColor(.secondary)
            
            Text("還沒有錢包")
                .font(.system(size: 14))
                .foregroundColor(.secondary)
            
            Text("創建或導入錢包開始使用")
                .font(.system(size: 12))
                .foregroundColor(.secondary)
        }
        .padding(.vertical, 30)
    }
}

// 錢包卡片
struct WalletCard: View {
    let wallet: WalletModel
    let isSelected: Bool
    let onSelect: () -> Void
    let onDelete: () -> Void
    
    var body: some View {
        Button(action: onSelect) {
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Image(systemName: wallet.type.icon)
                        .font(.system(size: 16))
                        .foregroundColor(wallet.type.color)
                    
                    Text(wallet.name)
                        .font(.system(size: 14, weight: .medium))
                    
                    Spacer()
                    
                    if isSelected {
                        Image(systemName: "checkmark.circle.fill")
                            .font(.system(size: 14))
                            .foregroundColor(.green)
                    }
                }
                
                Text(formatAddress(wallet.address))
                    .font(.system(size: 11, design: .monospaced))
                    .foregroundColor(.secondary)
                
                HStack {
                    Text("創建於 \(formatDate(wallet.createdAt))")
                        .font(.system(size: 10))
                        .foregroundColor(.secondary)
                    
                    Spacer()
                    
                    Button(action: onDelete) {
                        Image(systemName: "trash")
                            .font(.system(size: 12))
                            .foregroundColor(.red)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(12)
            .background(isSelected ? Color.blue.opacity(0.1) : Color.white.opacity(0.05))
            .cornerRadius(10)
            .overlay(
                RoundedRectangle(cornerRadius: 10)
                    .stroke(isSelected ? Color.blue.opacity(0.5) : Color.clear, lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
    }
    
    private func formatAddress(_ address: String) -> String {
        guard address.count > 10 else { return address }
        let prefix = address.prefix(6)
        let suffix = address.suffix(4)
        return "\(prefix)...\(suffix)"
    }
    
    private func formatDate(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy/MM/dd"
        return formatter.string(from: date)
    }
}

#Preview {
    NavigationStack {
        WalletManagementView()
    }
}
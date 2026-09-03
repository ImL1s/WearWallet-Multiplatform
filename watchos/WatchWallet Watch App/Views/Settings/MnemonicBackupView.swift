//
//  MnemonicBackupView.swift
//  WatchWallet Watch App
//
//  Mnemonic phrase backup and recovery
//

import SwiftUI
import LocalAuthentication

struct MnemonicBackupView: View {
    @StateObject private var viewModel = MnemonicBackupViewModel()
    @State private var showMnemonicView = false
    @State private var showAuthenticationError = false
    @State private var selectedWallet: WalletModel?
    
    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                // Warning card
                WarningCard()
                
                // Wallet selection
                if viewModel.wallets.count > 1 {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("選擇錢包")
                            .font(.system(size: 13, weight: .medium))
                            .foregroundColor(.secondary)
                        
                        ForEach(viewModel.wallets) { wallet in
                            WalletSelectionRow(
                                wallet: wallet,
                                isSelected: selectedWallet?.id == wallet.id,
                                onSelect: {
                                    selectedWallet = wallet
                                }
                            )
                        }
                    }
                    .padding()
                    .background(Color.white.opacity(0.05))
                    .cornerRadius(8)
                }
                
                // Show mnemonic button - now opens new page
                Button(action: authenticateAndShowMnemonic) {
                    HStack {
                        Image(systemName: "eye.fill")
                            .font(.system(size: 14))
                        Text("查看助記詞")
                            .font(.system(size: 14, weight: .medium))
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10)
                }
                .buttonStyle(.borderedProminent)
                .disabled(selectedWallet == nil && viewModel.wallets.count > 1)
                
                // Backup tips
                BackupTips()
            }
            .padding()
        }
        .navigationTitle("助記詞備份")
        .navigationBarTitleDisplayMode(.inline)
        .alert("認證失敗", isPresented: $showAuthenticationError) {
            Button("確定", role: .cancel) {}
        } message: {
            Text("無法驗證您的身份。請確保您已設定 Face ID 或密碼。")
        }
        .sheet(isPresented: $showMnemonicView) {
            if let wallet = selectedWallet ?? viewModel.wallets.first {
                MnemonicDisplayView(
                    walletId: wallet.id,
                    walletName: wallet.name
                )
            }
        }
        .onAppear {
            viewModel.loadWallets()
            if viewModel.wallets.count == 1 {
                selectedWallet = viewModel.wallets.first
            }
        }
    }
    
    private func authenticateAndShowMnemonic() {
        // 新的 MnemonicDisplayView 會自行處理驗證
        showMnemonicView = true
    }
}

struct WarningCard: View {
    var body: some View {
        VStack(spacing: 8) {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.system(size: 24))
                .foregroundColor(.orange)
            
            Text("重要安全提示")
                .font(.system(size: 14, weight: .semibold))
            
            VStack(alignment: .leading, spacing: 6) {
                SecurityWarning(text: "請在安全的環境下查看助記詞")
                SecurityWarning(text: "不要截圖或拍照保存助記詞")
                SecurityWarning(text: "不要將助記詞告訴任何人")
                SecurityWarning(text: "妥善保管助記詞，遺失無法找回")
            }
        }
        .padding()
        .background(Color.orange.opacity(0.1))
        .cornerRadius(8)
    }
}

struct SecurityWarning: View {
    let text: String
    
    var body: some View {
        HStack(alignment: .top, spacing: 6) {
            Text("•")
                .font(.system(size: 12))
                .foregroundColor(.orange)
            Text(text)
                .font(.system(size: 11))
                .foregroundColor(.secondary)
        }
    }
}

struct WalletSelectionRow: View {
    let wallet: WalletModel
    let isSelected: Bool
    let onSelect: () -> Void
    
    var body: some View {
        Button(action: onSelect) {
            HStack {
                Image(systemName: wallet.type.icon)
                    .font(.system(size: 14))
                    .foregroundColor(wallet.type.color)
                
                VStack(alignment: .leading, spacing: 2) {
                    Text(wallet.name)
                        .font(.system(size: 13))
                        .foregroundColor(isSelected ? .white : .primary)
                    Text(formatAddress(wallet.address))
                        .font(.system(size: 10))
                        .foregroundColor(isSelected ? .white.opacity(0.7) : .secondary)
                }
                
                Spacer()
                
                if isSelected {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 14))
                        .foregroundColor(.white)
                }
            }
            .padding(10)
            .background(isSelected ? Color.blue : Color.clear)
            .cornerRadius(6)
        }
        .buttonStyle(.plain)
    }
    
    private func formatAddress(_ address: String) -> String {
        guard address.count > 10 else { return address }
        let prefix = address.prefix(6)
        let suffix = address.suffix(4)
        return "\(prefix)...\(suffix)"
    }
}


struct BackupTips: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("備份建議")
                .font(.system(size: 13, weight: .medium))
            
            VStack(alignment: .leading, spacing: 6) {
                BackupTip(icon: "pencil", text: "將助記詞寫在紙上")
                BackupTip(icon: "lock.fill", text: "存放在安全的地方")
                BackupTip(icon: "xmark.shield", text: "不要存儲在電子設備中")
                BackupTip(icon: "person.2.slash", text: "不要與他人分享")
            }
        }
        .padding()
        .background(Color.blue.opacity(0.1))
        .cornerRadius(8)
    }
}

struct BackupTip: View {
    let icon: String
    let text: String
    
    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: icon)
                .font(.system(size: 12))
                .foregroundColor(.blue)
                .frame(width: 16)
            
            Text(text)
                .font(.system(size: 11))
                .foregroundColor(.secondary)
        }
    }
}

#Preview {
    NavigationStack {
        MnemonicBackupView()
    }
}
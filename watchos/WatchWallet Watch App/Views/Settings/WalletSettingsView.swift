//
//  WalletSettingsView.swift
//  WatchWallet Watch App
//
//  Settings view for wallet configuration
//

import SwiftUI
import coreKmp

struct WalletSettingsView: View {
    @StateObject private var chainStateManager = ChainStateManager.shared
    @State private var showingUTXOWallet = false
    
    private var isUTXOChain: Bool {
        switch chainStateManager.currentChain {
        case .bitcoin, .litecoin, .dogecoin, .bitcoinCash:
            return true
        default:
            return false
        }
    }
    
    var body: some View {
        ScrollView {
            VStack(spacing: 8) {
                // UTXO 錢包入口（當選擇 UTXO 鏈時顯示）
                if isUTXOChain {
                    Button(action: {
                        showingUTXOWallet = true
                    }) {
                        HStack(spacing: 10) {
                            Image(systemName: "bitcoinsign.circle.fill")
                                .font(.system(size: 20))
                                .foregroundColor(.orange)
                                .frame(width: 28, height: 28)
                            
                            VStack(alignment: .leading, spacing: 2) {
                                Text("UTXO 錢包")
                                    .font(.system(size: 14))
                                    .foregroundColor(.primary)
                                Text("管理 Bitcoin 類型錢包")
                                    .font(.system(size: 11))
                                    .foregroundColor(.secondary)
                            }
                            
                            Spacer()
                            
                            Image(systemName: "chevron.right")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                        .padding(.vertical, 4)
                    }
                    .buttonStyle(.plain)
                }
                
                NavigationLink(destination: WalletManagementView()) {
                    SettingsRow(
                        icon: "wallet.pass",
                        title: "錢包管理",
                        subtitle: "創建、導入或管理錢包"
                    )
                }
                
                NavigationLink(destination: NetworkSettingsView()) {
                    SettingsRow(
                        icon: "network",
                        title: "網路設定",
                        subtitle: "選擇區塊鏈網路"
                    )
                }
                
                NavigationLink(destination: TokenManagementView()) {
                    SettingsRow(
                        icon: "bitcoinsign.circle",
                        title: "代幣管理",
                        subtitle: "添加自定義代幣"
                    )
                }
                
                NavigationLink(destination: TransactionHistoryView()) {
                    SettingsRow(
                        icon: "list.bullet.rectangle",
                        title: "交易歷史",
                        subtitle: "查看所有交易紀錄"
                    )
                }
                
                NavigationLink(destination: KeystoneView()) {
                    SettingsRow(
                        icon: "lock.shield.fill",
                        title: "Keystone 硬體錢包",
                        subtitle: "連接硬體錢包"
                    )
                }
                
                NavigationLink(destination: SecuritySettingsView()) {
                    SettingsRow(
                        icon: "lock.shield",
                        title: "安全設定",
                        subtitle: "生物識別、密碼"
                    )
                }
                
                NavigationLink(destination: MnemonicBackupView()) {
                    SettingsRow(
                        icon: "key",
                        title: "助記詞備份",
                        subtitle: "查看和備份助記詞"
                    )
                }
                
                NavigationLink(destination: AboutView()) {
                    SettingsRow(
                        icon: "info.circle",
                        title: "關於",
                        subtitle: "版本資訊"
                    )
                }
            }
            .padding()
        }
        .sheet(isPresented: $showingUTXOWallet) {
            NavigationStack {
                UTXOWalletView()
            }
        }
    }
}

struct SettingsRow: View {
    let icon: String
    let title: String
    let subtitle: String
    
    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: icon)
                .font(.system(size: 20))
                .foregroundColor(.blue)
                .frame(width: 28, height: 28)
            
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 14))
                Text(subtitle)
                    .font(.system(size: 11))
                    .foregroundColor(.secondary)
            }
            
            Spacer()
        }
        .padding(.vertical, 4)
    }
}


// NetworkSettingsView is now in its own file

// TokenManagementView is now in its own file

// SecuritySettingsView is now in its own file

// MnemonicBackupView is now in its own file

struct AboutView: View {
    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "wallet.pass.fill")
                .font(.system(size: 50))
                .foregroundColor(.blue)
            
            Text("WatchWallet")
                .font(.title2)
            
            Text("版本 1.0.0")
                .font(.caption)
                .foregroundColor(.secondary)
            
            Text("基於 KMP 的跨平台錢包")
                .font(.caption)
                .foregroundColor(.secondary)
            
            Text("Running on watchOS")
                .font(.caption2)
                .foregroundColor(.secondary)
        }
        .padding()
        .navigationTitle("關於")
    }
}

#Preview {
    NavigationStack {
        WalletSettingsView()
    }
}
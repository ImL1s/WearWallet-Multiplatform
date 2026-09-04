//
//  WalletSwitcher.swift
//  WatchWallet Watch App
//
//  Wallet switcher component
//

import SwiftUI

struct WalletSwitcher: View {
    let currentWallet: WalletModel?
    let wallets: [WalletModel]
    @Binding var isShowing: Bool
    let onWalletSelected: (WalletModel) -> Void
    
    var body: some View {
        if wallets.count > 1 {
            Button(action: { isShowing.toggle() }) {
                HStack {
                    Image(systemName: currentWallet?.type.icon ?? "wallet.pass")
                        .font(.system(size: 12))
                        .foregroundColor(currentWallet?.type.color ?? .blue)
                    
                    Text(currentWallet?.name ?? "選擇錢包")
                        .font(.system(size: 12))
                        .lineLimit(1)
                    
                    Image(systemName: "chevron.down")
                        .font(.system(size: 10))
                        .foregroundColor(.secondary)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(Color.white.opacity(0.1))
                .cornerRadius(8)
            }
            .buttonStyle(.plain)
            .sheet(isPresented: $isShowing) {
                WalletListView(
                    wallets: wallets,
                    currentWallet: currentWallet,
                    onWalletSelected: { wallet in
                        onWalletSelected(wallet)
                        isShowing = false
                    }
                )
            }
        }
    }
}

struct WalletListView: View {
    let wallets: [WalletModel]
    let currentWallet: WalletModel?
    let onWalletSelected: (WalletModel) -> Void
    @Environment(\.dismiss) var dismiss
    
    var body: some View {
        NavigationStack {
            List(wallets) { wallet in
                Button(action: { onWalletSelected(wallet) }) {
                    HStack {
                        Image(systemName: wallet.type.icon)
                            .foregroundColor(wallet.type.color)
                            .font(.system(size: 16))
                        
                        VStack(alignment: .leading, spacing: 2) {
                            Text(wallet.name)
                                .font(.system(size: 14))
                            Text(wallet.address)
                                .font(.system(size: 10))
                                .foregroundColor(.secondary)
                        }
                        
                        Spacer()
                        
                        if wallet.id == currentWallet?.id {
                            Image(systemName: "checkmark")
                                .foregroundColor(.blue)
                                .font(.system(size: 12))
                        }
                    }
                    .padding(.vertical, 4)
                }
                .buttonStyle(.plain)
            }
            .navigationTitle("選擇錢包")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("關閉") { dismiss() }
                }
            }
        }
    }
}

#Preview {
    VStack {
        WalletSwitcher(
            currentWallet: WalletModel(
                id: "1",
                name: "主錢包",
                address: "0x1234...5678",
                type: .hot,
                chainId: "1"
            ),
            wallets: [
                WalletModel(
                    id: "1",
                    name: "主錢包",
                    address: "0x1234...5678",
                    type: .hot,
                    chainId: "1"
                ),
                WalletModel(
                    id: "2",
                    name: "冷錢包",
                    address: "0x8765...4321",
                    type: .cold,
                    chainId: "1"
                )
            ],
            isShowing: .constant(false),
            onWalletSelected: { _ in }
        )
    }
    .padding()
}
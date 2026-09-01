//
//  BalanceCard.swift
//  WatchWallet Watch App
//
//  Balance display card component
//

import SwiftUI

struct BalanceCard: View {
    let balance: String
    let tokenSymbol: String
    let walletName: String
    let walletType: WalletType
    let isLoading: Bool
    let onRefresh: () -> Void
    let onTap: () -> Void
    
    var body: some View {
        Button(action: onTap) {
            VStack(spacing: 8) {
                // 錢包資訊
                HStack {
                    Image(systemName: walletType.icon)
                        .font(.system(size: 12))
                        .foregroundColor(walletType.color)
                    Text(walletName)
                        .font(.system(size: 12))
                        .foregroundColor(.secondary)
                        .lineLimit(1)
                    Spacer()
                }
                .padding(.horizontal, 12)
                
                // 餘額
                if isLoading {
                    ProgressView()
                        .scaleEffect(0.8)
                        .frame(height: 40)
                } else {
                    VStack(spacing: 4) {
                        Text(balance)
                            .font(.system(size: 24, weight: .semibold, design: .rounded))
                            .foregroundColor(.white)
                            .minimumScaleFactor(0.7)
                            .lineLimit(1)
                        
                        HStack(spacing: 4) {
                            Text(tokenSymbol)
                                .font(.system(size: 14))
                                .foregroundColor(.secondary)
                            Image(systemName: "chevron.down")
                                .font(.system(size: 10))
                                .foregroundColor(.secondary)
                        }
                    }
                }
                
                // 刷新按鈕
                Button(action: onRefresh) {
                    HStack(spacing: 4) {
                        Image(systemName: "arrow.clockwise")
                            .font(.system(size: 12))
                        Text("刷新")
                            .font(.system(size: 12))
                    }
                    .foregroundColor(.blue.opacity(0.8))
                }
                .buttonStyle(.plain)
                .disabled(isLoading)
                .padding(.horizontal, 12)
            }
            .padding(.vertical, 14)
            .frame(maxWidth: .infinity)
            .background(.ultraThinMaterial) // Solarium: Glassmorphism
            .cornerRadius(16)
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .stroke(
                        LinearGradient(
                            colors: [.white.opacity(0.15), .clear],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        ),
                        lineWidth: 1
                    )
            )
            .shadow(color: .black.opacity(0.2), radius: 4, x: 0, y: 2)
        }
        .buttonStyle(.plain)
    }
}

#Preview {
    BalanceCard(
        balance: "1,234.56",
        tokenSymbol: "ETH",
        walletName: "主錢包",
        walletType: .hot,
        isLoading: false,
        onRefresh: {},
        onTap: {}
    )
    .padding()
}
//
//  TokenSelectorView.swift
//  WatchWallet Watch App
//
//  Token selection view
//

import SwiftUI

struct TokenSelectorView: View {
    let selectedToken: TokenModel?
    let tokens: [TokenModel]
    let onTokenSelected: (TokenModel) -> Void
    @Environment(\.dismiss) var dismiss
    
    var body: some View {
        NavigationStack {
            List(tokens) { token in
                Button(action: { onTokenSelected(token) }) {
                    HStack {
                        // 代幣圖標 (暫時使用系統圖標)
                        Image(systemName: "bitcoinsign.circle.fill")
                            .font(.system(size: 24))
                            .foregroundColor(tokenColor(for: token.symbol))
                        
                        VStack(alignment: .leading, spacing: 2) {
                            Text(token.symbol)
                                .font(.system(size: 14, weight: .medium))
                            Text(token.name)
                                .font(.system(size: 12))
                                .foregroundColor(.secondary)
                        }
                        
                        Spacer()
                        
                        if token.id == selectedToken?.id {
                            Image(systemName: "checkmark")
                                .foregroundColor(.blue)
                                .font(.system(size: 12))
                        }
                    }
                    .padding(.vertical, 4)
                }
                .buttonStyle(.plain)
            }
            .navigationTitle("選擇代幣")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("關閉") { dismiss() }
                }
            }
        }
    }
    
    private func tokenColor(for symbol: String) -> Color {
        switch symbol {
        case "ETH": return Color(red: 0.39, green: 0.42, blue: 0.68)
        case "BNB": return Color(red: 0.95, green: 0.73, blue: 0.16)
        case "MATIC": return Color(red: 0.51, green: 0.31, blue: 0.88)
        case "CRO": return Color(red: 0.0, green: 0.21, blue: 0.51)
        default: return .blue
        }
    }
}

#Preview {
    TokenSelectorView(
        selectedToken: TokenModel(id: "1", symbol: "ETH", name: "Ethereum", chainId: "1"),
        tokens: [
            TokenModel(id: "1", symbol: "ETH", name: "Ethereum", chainId: "1"),
            TokenModel(id: "2", symbol: "BNB", name: "Binance Coin", chainId: "56"),
            TokenModel(id: "3", symbol: "MATIC", name: "Polygon", chainId: "137"),
            TokenModel(id: "4", symbol: "CRO", name: "Cronos", chainId: "25")
        ],
        onTokenSelected: { _ in }
    )
}
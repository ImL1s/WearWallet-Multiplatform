//
//  UTXOChainSelectorView.swift
//  WatchWallet Watch App
//
//  UTXO 鏈選擇器視圖
//

import SwiftUI

struct UTXOChainSelectorView: View {
    let currentChain: UTXOChainType
    let onSelectChain: (UTXOChainType) -> Void
    @Environment(\.dismiss) private var dismiss
    
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 8) {
                    ForEach(UTXOChainType.allCases, id: \.self) { chain in
                        ChainOptionRow(
                            chain: chain,
                            isSelected: chain == currentChain,
                            onSelect: {
                                onSelectChain(chain)
                            }
                        )
                    }
                }
                .padding(.vertical)
            }
            .navigationTitle("選擇區塊鏈")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") {
                        dismiss()
                    }
                }
            }
        }
    }
}

struct ChainOptionRow: View {
    let chain: UTXOChainType
    let isSelected: Bool
    let onSelect: () -> Void
    
    var body: some View {
        Button(action: onSelect) {
            HStack(spacing: 12) {
                // 鏈圖標
                Image(systemName: chain.icon)
                    .font(.title2)
                    .foregroundColor(chain.color)
                    .frame(width: 32, height: 32)
                    .background(chain.color.opacity(0.1))
                    .clipShape(Circle())
                
                // 鏈資訊
                VStack(alignment: .leading, spacing: 2) {
                    Text(chain.displayName)
                        .font(.body)
                        .foregroundColor(.primary)
                    
                    Text(chain.symbol)
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
                
                Spacer()
                
                // 選中標記
                if isSelected {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundColor(.green)
                        .font(.title3)
                }
            }
            .padding(.horizontal)
            .padding(.vertical, 8)
            .background(
                RoundedRectangle(cornerRadius: 10)
                    .fill(isSelected ? Color.blue.opacity(0.1) : Color.gray.opacity(0.05))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 10)
                    .stroke(isSelected ? Color.blue : Color.clear, lineWidth: 1)
            )
            .padding(.horizontal)
        }
        .buttonStyle(.plain)
    }
}

#Preview {
    UTXOChainSelectorView(
        currentChain: .bitcoin,
        onSelectChain: { _ in }
    )
}
//
//  UTXOListView.swift
//  WatchWallet Watch App
//
//  UTXO 列表視圖 - 顯示所有未花費的交易輸出
//

import SwiftUI

struct UTXOListView: View {
    let utxos: [UTXO]
    let chain: UTXOChainType
    @Environment(\.dismiss) private var dismiss
    @State private var selectedUTXO: UTXO?
    @State private var sortOrder: UTXOSortOrder = .valueDescending
    
    enum UTXOSortOrder: String, CaseIterable {
        case valueDescending = "金額從高到低"
        case valueAscending = "金額從低到高"
        case confirmationsDescending = "確認數從高到低"
        case confirmationsAscending = "確認數從低到高"
    }
    
    private var sortedUTXOs: [UTXO] {
        switch sortOrder {
        case .valueDescending:
            return utxos.sorted { $0.value > $1.value }
        case .valueAscending:
            return utxos.sorted { $0.value < $1.value }
        case .confirmationsDescending:
            return utxos.sorted { $0.confirmations > $1.confirmations }
        case .confirmationsAscending:
            return utxos.sorted { $0.confirmations < $1.confirmations }
        }
    }
    
    private var totalValue: Int64 {
        utxos.reduce(0) { $0 + $1.value }
    }
    
    private var formattedTotalValue: String {
        let divisor = pow(10.0, 8.0)
        let value = Double(totalValue) / divisor
        return String(format: "%.8f", value)
    }
    
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 12) {
                    // 統計資訊
                    VStack(spacing: 8) {
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("UTXO 總數")
                                    .font(.caption2)
                                    .foregroundColor(.secondary)
                                Text("\(utxos.count)")
                                    .font(.title3)
                                    .fontWeight(.bold)
                            }
                            
                            Spacer()
                            
                            VStack(alignment: .trailing, spacing: 2) {
                                Text("總價值")
                                    .font(.caption2)
                                    .foregroundColor(.secondary)
                                Text("\(formattedTotalValue)")
                                    .font(.caption)
                                    .fontWeight(.medium)
                                Text(chain.symbol)
                                    .font(.caption2)
                                    .foregroundColor(.secondary)
                            }
                        }
                        .padding()
                        .background(Color.gray.opacity(0.1))
                        .cornerRadius(10)
                    }
                    .padding(.horizontal)
                    
                    // 排序選項
                    Picker("排序", selection: $sortOrder) {
                        ForEach(UTXOSortOrder.allCases, id: \.self) { order in
                            Text(order.rawValue)
                                .tag(order)
                        }
                    }
                    .pickerStyle(.navigationLink)
                    .padding(.horizontal)
                    
                    // UTXO 列表
                    ForEach(sortedUTXOs) { utxo in
                        UTXORowView(
                            utxo: utxo,
                            chain: chain,
                            onTap: {
                                selectedUTXO = utxo
                            }
                        )
                    }
                }
                .padding(.vertical)
            }
            .navigationTitle("UTXO 列表")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("完成") {
                        dismiss()
                    }
                }
            }
            .sheet(item: $selectedUTXO) { utxo in
                UTXODetailView(utxo: utxo, chain: chain)
            }
        }
    }
}

struct UTXORowView: View {
    let utxo: UTXO
    let chain: UTXOChainType
    let onTap: () -> Void
    
    var body: some View {
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: 8) {
                // 交易ID和輸出索引
                HStack {
                    Text("\(utxo.shortTxid):\(utxo.vout)")
                        .font(.system(size: 11, design: .monospaced))
                        .foregroundColor(.primary)
                    
                    Spacer()
                    
                    if utxo.spendable {
                        Image(systemName: "checkmark.circle.fill")
                            .font(.caption)
                            .foregroundColor(.green)
                    } else {
                        Image(systemName: "lock.circle.fill")
                            .font(.caption)
                            .foregroundColor(.orange)
                    }
                }
                
                // 金額和確認數
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("金額")
                            .font(.system(size: 9))
                            .foregroundColor(.secondary)
                        Text("\(utxo.formattedValue) \(chain.symbol)")
                            .font(.caption2)
                            .fontWeight(.medium)
                    }
                    
                    Spacer()
                    
                    VStack(alignment: .trailing, spacing: 2) {
                        Text("確認數")
                            .font(.system(size: 9))
                            .foregroundColor(.secondary)
                        Text("\(utxo.confirmations)")
                            .font(.caption2)
                            .fontWeight(.medium)
                            .foregroundColor(utxo.confirmations >= 6 ? .green : .orange)
                    }
                }
            }
            .padding()
            .background(Color.gray.opacity(0.05))
            .cornerRadius(8)
            .padding(.horizontal)
        }
        .buttonStyle(.plain)
    }
}

// MARK: - UTXO Detail View

struct UTXODetailView: View {
    let utxo: UTXO
    let chain: UTXOChainType
    @Environment(\.dismiss) private var dismiss
    @State private var isCopied = false
    
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    // 基本資訊
                    DetailSection(title: "基本資訊") {
                        DetailRow(label: "交易ID", value: utxo.txid, isMonospaced: true)
                        DetailRow(label: "輸出索引", value: "\(utxo.vout)")
                        DetailRow(label: "金額", value: "\(utxo.formattedValue) \(chain.symbol)")
                        DetailRow(label: "確認數", value: "\(utxo.confirmations)")
                        DetailRow(label: "可花費", value: utxo.spendable ? "是" : "否")
                    }
                    
                    // 地址資訊
                    DetailSection(title: "地址") {
                        Text(utxo.address)
                            .font(.system(size: 10, design: .monospaced))
                            .foregroundColor(.primary)
                            .padding(8)
                            .background(Color.gray.opacity(0.1))
                            .cornerRadius(6)
                        
                        Button(action: copyAddress) {
                            HStack {
                                Image(systemName: isCopied ? "checkmark" : "doc.on.doc")
                                Text(isCopied ? "已複製" : "複製地址")
                            }
                            .font(.caption2)
                            .foregroundColor(isCopied ? .green : .blue)
                        }
                        .buttonStyle(.bordered)
                        .controlSize(.small)
                    }
                    
                    // Script 資訊
                    DetailSection(title: "Script") {
                        Text(utxo.scriptPubKey)
                            .font(.system(size: 9, design: .monospaced))
                            .foregroundColor(.secondary)
                            .lineLimit(3)
                            .padding(8)
                            .background(Color.gray.opacity(0.1))
                            .cornerRadius(6)
                    }
                    
                    // 查看區塊瀏覽器
                    Button(action: openInExplorer) {
                        HStack {
                            Image(systemName: "safari")
                            Text("在區塊瀏覽器中查看")
                        }
                        .font(.caption)
                        .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .padding(.horizontal)
                }
                .padding(.vertical)
            }
            .navigationTitle("UTXO 詳情")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("完成") {
                        dismiss()
                    }
                }
            }
        }
    }
    
    private func copyAddress() {
        // UIPasteboard.general.string = utxo.address
        isCopied = true
        DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
            isCopied = false
        }
    }
    
    private func openInExplorer() {
        let urlString = "\(chain.explorerUrl)/transaction/\(utxo.txid)"
        if let url = URL(string: urlString) {
            // 在 watchOS 上，這將在配對的 iPhone 上打開
            // WKExtension.shared().openSystemURL(url)
        }
    }
}

struct DetailSection<Content: View>: View {
    let title: String
    let content: Content
    
    init(title: String, @ViewBuilder content: () -> Content) {
        self.title = title
        self.content = content()
    }
    
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.caption)
                .fontWeight(.medium)
                .foregroundColor(.secondary)
            
            content
        }
        .padding(.horizontal)
    }
}

struct DetailRow: View {
    let label: String
    let value: String
    var isMonospaced: Bool = false
    
    var body: some View {
        HStack {
            Text(label)
                .font(.caption2)
                .foregroundColor(.secondary)
            
            Spacer()
            
            Text(value)
                .font(isMonospaced ? .system(size: 10, design: .monospaced) : .caption2)
                .foregroundColor(.primary)
                .lineLimit(1)
        }
    }
}

#Preview {
    UTXOListView(
        utxos: [
            UTXO(
                id: "1",
                txid: "abc123def456789",
                vout: 0,
                value: 100000000,
                scriptPubKey: "76a914abc88b835c78e5b892756e0f8e7c3b3f6771829088ac",
                address: "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa",
                confirmations: 6,
                spendable: true
            )
        ],
        chain: .bitcoin
    )
}
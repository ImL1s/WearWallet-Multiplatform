//
//  UTXOTransactionDetailView.swift
//  WatchWallet Watch App
//
//  UTXO 交易詳情視圖
//

import SwiftUI

struct UTXOTransactionDetailView: View {
    let transaction: UTXOTransaction
    let chain: UTXOChainType
    @Environment(\.dismiss) private var dismiss
    @State private var isTxidCopied = false
    
    private var confirmationStatus: String {
        if transaction.confirmations == 0 {
            return "未確認"
        } else if transaction.confirmations < 6 {
            return "\(transaction.confirmations) 確認 (進行中)"
        } else {
            return "\(transaction.confirmations) 確認 (已完成)"
        }
    }
    
    private var confirmationColor: Color {
        if transaction.confirmations == 0 {
            return .orange
        } else if transaction.confirmations < 6 {
            return .yellow
        } else {
            return .green
        }
    }
    
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    // 交易狀態
                    HStack {
                        Image(systemName: "checkmark.circle.fill")
                            .foregroundColor(confirmationColor)
                        Text(confirmationStatus)
                            .font(.caption)
                            .foregroundColor(confirmationColor)
                        Spacer()
                    }
                    .padding(.horizontal)
                    
                    // 交易ID
                    DetailSection(title: "交易ID") {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(transaction.txid)
                                .font(.system(size: 9, design: .monospaced))
                                .foregroundColor(.primary)
                                .lineLimit(2)
                                .padding(8)
                                .background(Color.gray.opacity(0.1))
                                .cornerRadius(6)
                            
                            Button(action: copyTxid) {
                                HStack {
                                    Image(systemName: isTxidCopied ? "checkmark" : "doc.on.doc")
                                    Text(isTxidCopied ? "已複製" : "複製交易ID")
                                }
                                .font(.caption2)
                                .foregroundColor(isTxidCopied ? .green : .blue)
                            }
                            .buttonStyle(.bordered)
                            .controlSize(.small)
                        }
                    }
                    
                    // 交易資訊
                    DetailSection(title: "交易資訊") {
                        DetailRow(label: "時間", value: formatDate(transaction.timestamp))
                        if let blockHeight = transaction.blockHeight {
                            DetailRow(label: "區塊高度", value: "\(blockHeight)")
                        }
                        DetailRow(label: "交易大小", value: "\(transaction.size) bytes")
                        DetailRow(label: "手續費", value: "\(transaction.formattedFee) \(chain.symbol)")
                        DetailRow(label: "費率", value: String(format: "%.2f sat/vB", transaction.feeRate))
                    }
                    
                    // 輸入
                    DetailSection(title: "輸入 (\(transaction.inputs.count))") {
                        ForEach(transaction.inputs) { input in
                            InputOutputRow(
                                address: input.address ?? "未知地址",
                                value: formatValue(input.value),
                                symbol: chain.symbol,
                                isInput: true
                            )
                        }
                    }
                    
                    // 輸出
                    DetailSection(title: "輸出 (\(transaction.outputs.count))") {
                        ForEach(transaction.outputs) { output in
                            InputOutputRow(
                                address: output.address ?? "未知地址",
                                value: formatValue(output.value),
                                symbol: chain.symbol,
                                isInput: false,
                                isSpent: output.spent
                            )
                        }
                    }
                    
                    // 總計
                    VStack(spacing: 8) {
                        HStack {
                            Text("總輸入")
                                .font(.caption2)
                                .foregroundColor(.secondary)
                            Spacer()
                            Text("\(formatValue(transaction.totalInput)) \(chain.symbol)")
                                .font(.caption2)
                                .fontWeight(.medium)
                        }
                        
                        HStack {
                            Text("總輸出")
                                .font(.caption2)
                                .foregroundColor(.secondary)
                            Spacer()
                            Text("\(formatValue(transaction.totalOutput)) \(chain.symbol)")
                                .font(.caption2)
                                .fontWeight(.medium)
                        }
                        
                        Divider()
                        
                        HStack {
                            Text("手續費")
                                .font(.caption2)
                                .foregroundColor(.secondary)
                            Spacer()
                            Text("\(transaction.formattedFee) \(chain.symbol)")
                                .font(.caption2)
                                .fontWeight(.bold)
                                .foregroundColor(.orange)
                        }
                    }
                    .padding()
                    .background(Color.gray.opacity(0.1))
                    .cornerRadius(10)
                    .padding(.horizontal)
                    
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
            .navigationTitle("交易詳情")
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
    
    private func formatDate(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateStyle = .short
        formatter.timeStyle = .short
        return formatter.string(from: date)
    }
    
    private func formatValue(_ satoshis: Int64) -> String {
        let divisor = pow(10.0, 8.0)
        let value = Double(satoshis) / divisor
        return String(format: "%.8f", value)
    }
    
    private func copyTxid() {
        // UIPasteboard.general.string = transaction.txid
        isTxidCopied = true
        DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
            isTxidCopied = false
        }
    }
    
    private func openInExplorer() {
        let urlString = "\(chain.explorerUrl)/transaction/\(transaction.txid)"
        if let url = URL(string: urlString) {
            // 在 watchOS 上，這將在配對的 iPhone 上打開
            // WKExtension.shared().openSystemURL(url)
        }
    }
}

struct InputOutputRow: View {
    let address: String
    let value: String
    let symbol: String
    let isInput: Bool
    var isSpent: Bool = false
    
    private var shortAddress: String {
        guard address.count > 10 else { return address }
        return "\(address.prefix(8))...\(address.suffix(6))"
    }
    
    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(shortAddress)
                    .font(.system(size: 10, design: .monospaced))
                    .foregroundColor(.primary)
                
                if !isInput && isSpent {
                    Text("已花費")
                        .font(.system(size: 8))
                        .foregroundColor(.orange)
                }
            }
            
            Spacer()
            
            VStack(alignment: .trailing, spacing: 2) {
                Text(value)
                    .font(.caption2)
                    .fontWeight(.medium)
                    .foregroundColor(isInput ? .red : .green)
                
                Text(symbol)
                    .font(.system(size: 9))
                    .foregroundColor(.secondary)
            }
        }
        .padding(.vertical, 4)
        .padding(.horizontal, 8)
        .background(
            RoundedRectangle(cornerRadius: 6)
                .fill(Color.gray.opacity(0.05))
        )
    }
}

#Preview {
    UTXOTransactionDetailView(
        transaction: UTXOTransaction(
            id: "1",
            txid: "abc123def456789abc123def456789abc123def456789abc123def456789abc1",
            inputs: [
                UTXOInput(
                    id: "1",
                    prevTxid: "prev1",
                    prevVout: 0,
                    scriptSig: "",
                    sequence: 0xFFFFFFFF,
                    address: "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa",
                    value: 100000000
                )
            ],
            outputs: [
                UTXOOutput(
                    id: "1",
                    value: 50000000,
                    scriptPubKey: "",
                    address: "3J98t1WpEZ73CNmQviecrnyiWrnqRhWNLy",
                    spent: false
                ),
                UTXOOutput(
                    id: "2",
                    value: 49900000,
                    scriptPubKey: "",
                    address: "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa",
                    spent: false
                )
            ],
            fee: 100000,
            size: 250,
            confirmations: 6,
            timestamp: Date(),
            blockHeight: 800000
        ),
        chain: .bitcoin
    )
}
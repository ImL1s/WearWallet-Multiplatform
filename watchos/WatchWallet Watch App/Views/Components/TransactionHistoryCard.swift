//
//  TransactionHistoryCard.swift
//  WatchWallet Watch App
//
//  Transaction history preview card
//

import SwiftUI

struct TransactionHistoryCard: View {
    let transactions: [TransactionModel]
    let onShowAll: () -> Void
    
    var body: some View {
        VStack(spacing: 8) {
            // 標題
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("最近交易")
                        .font(.system(size: 14, weight: .medium))
                }
                Spacer()
                Button(action: onShowAll) {
                    Text("查看全部")
                        .font(.system(size: 12))
                        .foregroundColor(.blue)
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 12)
            
            // 交易列表
            VStack(spacing: 4) {
                ForEach(transactions.prefix(3)) { transaction in
                    CompactTransactionRow(transaction: transaction)
                }
            }
            
            if transactions.isEmpty {
                Text("暫無交易記錄")
                    .font(.system(size: 12))
                    .foregroundColor(.secondary)
                    .padding(.vertical, 20)
            }
        }
        .padding(.vertical, 8)
        .background(Color.white.opacity(0.05))
        .cornerRadius(10)
    }
}

struct CompactTransactionRow: View {
    let transaction: TransactionModel
    
    var body: some View {
        HStack {
            // 圖標
            Image(systemName: transaction.type.icon)
                .foregroundColor(transaction.type.color)
                .font(.system(size: 16))
            
            // 交易資訊
            VStack(alignment: .leading, spacing: 2) {
                Text(transaction.type == .sent ? "發送" : "接收")
                    .font(.system(size: 12))
                Text(timeAgo(from: transaction.timestamp))
                    .font(.system(size: 10))
                    .foregroundColor(.secondary)
            }
            
            Spacer()
            
            // 金額
            Text("\(transaction.value) \(transaction.symbol)")
                .font(.system(size: 12, weight: .medium))
                .foregroundColor(transaction.type == .sent ? .primary : .green)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 6)
    }
    
    private func timeAgo(from date: Date) -> String {
        let interval = Date().timeIntervalSince(date)
        let hours = Int(interval / 3600)
        let minutes = Int((interval.truncatingRemainder(dividingBy: 3600)) / 60)
        
        if hours > 0 {
            return "\(hours) 小時前"
        } else if minutes > 0 {
            return "\(minutes) 分鐘前"
        } else {
            return "剛剛"
        }
    }
}

#Preview {
    TransactionHistoryCard(
        transactions: [
            TransactionModel(
                id: "1",
                hash: "0xabc...123",
                from: "0x1234...5678",
                to: "0x8765...4321",
                value: "0.1",
                symbol: "ETH",
                timestamp: Date().addingTimeInterval(-3600),
                status: .completed,
                type: .received
            ),
            TransactionModel(
                id: "2",
                hash: "0xdef...456",
                from: "0x1234...5678",
                to: "0x9999...1111",
                value: "0.5",
                symbol: "ETH",
                timestamp: Date().addingTimeInterval(-7200),
                status: .completed,
                type: .sent
            )
        ],
        onShowAll: {}
    )
    .padding()
}
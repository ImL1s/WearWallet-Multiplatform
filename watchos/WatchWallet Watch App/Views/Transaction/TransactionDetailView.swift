//
//  TransactionDetailView.swift
//  WatchWallet Watch App
//
//  Detailed transaction view with watchOS 2024 design patterns
//

import SwiftUI
import UIKit

struct TransactionDetailView: View {
    let transaction: TransactionModel
    @Environment(\.dismiss) var dismiss
    @State private var showingShareSheet = false
    @State private var scrollPosition: CGFloat = 0
    
    var body: some View {
        NavigationView {
            ScrollViewReader { proxy in
                List {
                    // MARK: - Header Section
                    headerSection
                    
                    // MARK: - Amount Section
                    amountSection
                    
                    // MARK: - Status Section
                    statusSection
                    
                    // MARK: - Transaction Details
                    transactionDetailsSection
                    
                    // MARK: - Network Information
                    networkInfoSection
                    
                    // MARK: - Addresses Section
                    addressesSection
                    
                    // MARK: - Timing Information
                    timingSection
                    
                    // MARK: - Actions Section
                    actionsSection
                }
                .listStyle(.plain)
                .digitalCrownRotation($scrollPosition)
                .focusable(true)
                .onChange(of: scrollPosition) { _, newValue in
                    // Smooth scrolling with Digital Crown
                    let targetIndex = Int(newValue * 8) % 8 // 8 sections
                    withAnimation(.easeInOut(duration: 0.3)) {
                        proxy.scrollTo("section-\(targetIndex)", anchor: .top)
                    }
                }
            }
            .navigationTitle("交易詳情")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("返回") {
                        dismiss()
                    }
                    .font(.system(size: 14, weight: .medium))
                }
                
                ToolbarItem(placement: .topBarTrailing) {
                    shareButton
                }
            }
        }
        .sheet(isPresented: $showingShareSheet) {
            ShareSheet(transaction: transaction)
        }
    }
    
    // MARK: - Header Section
    private var headerSection: some View {
        VStack(spacing: 12) {
            // Transaction Icon
            ZStack {
                Circle()
                    .fill(transaction.type.color.opacity(0.15))
                    .frame(width: 60, height: 60)
                
                Image(systemName: transaction.type.icon)
                    .font(.system(size: 28, weight: .medium))
                    .foregroundColor(transaction.type.color)
            }
            
            // Transaction Type
            Text(transactionTypeText)
                .font(.system(size: 16, weight: .semibold))
                .foregroundColor(.primary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 16)
        .listRowBackground(Color.clear)
        .id("section-0")
    }
    
    // MARK: - Amount Section
    private var amountSection: some View {
        VStack(spacing: 8) {
            // Primary Amount
            Text(transaction.amountText)
                .font(.system(size: 24, weight: .bold, design: .rounded))
                .foregroundColor(transaction.type.color)
            
            // USD Value
            if let usdValue = transaction.usdValue {
                Text("≈ $\(usdValue) USD")
                    .font(.system(size: 14))
                    .foregroundColor(.secondary)
            }
            
            // Gas Fee (if available)
            if let gasFee = transaction.gasFee {
                HStack(spacing: 4) {
                    Image(systemName: "fuelpump")
                        .font(.system(size: 10))
                        .foregroundColor(.orange)
                    
                    Text("網路費用: \(gasFee) \(transaction.symbol)")
                        .font(.system(size: 11))
                        .foregroundColor(.secondary)
                }
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 16)
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(Color.white.opacity(0.05))
        )
        .listRowBackground(Color.clear)
        .id("section-1")
    }
    
    // MARK: - Status Section
    private var statusSection: some View {
        HStack {
            Text("狀態")
                .font(.system(size: 13, weight: .medium))
                .foregroundColor(.secondary)
            
            Spacer()
            
            HStack(spacing: 6) {
                statusIcon
                    .font(.system(size: 12))
                    .foregroundColor(statusColor)
                
                Text(statusText)
                    .font(.system(size: 13, weight: .medium))
                    .foregroundColor(statusColor)
            }
        }
        .padding(.vertical, 8)
        .listRowBackground(Color.clear)
        .id("section-2")
    }
    
    // MARK: - Transaction Details
    private var transactionDetailsSection: some View {
        Section("交易資訊") {
            // Transaction Hash
            TransactionDetailRow(
                label: "交易雜湊",
                value: transaction.shortHash,
                fullValue: transaction.hash,
                copyable: true
            )
            
            // Block Number
            if let blockNumber = transaction.blockNumber {
                TransactionDetailRow(
                    label: "區塊高度",
                    value: blockNumber
                )
            }
            
            // Confirmations
            if let confirmations = transaction.confirmations {
                TransactionDetailRow(
                    label: "確認數",
                    value: "\(confirmations)"
                )
            }
        }
        .listRowBackground(Color.clear)
        .id("section-3")
    }
    
    // MARK: - Network Information
    private var networkInfoSection: some View {
        Section("網路資訊") {
            TransactionDetailRow(
                label: "區塊鏈",
                value: transaction.networkDisplayName
            )
            
            if let chainId = transaction.chainId {
                TransactionDetailRow(
                    label: "鏈 ID",
                    value: chainId
                )
            }
            
            // Gas Information
            if let gasPrice = transaction.gasPrice {
                TransactionDetailRow(
                    label: "Gas 價格",
                    value: "\(gasPrice) Gwei"
                )
            }
            
            if let gasUsed = transaction.gasUsed {
                TransactionDetailRow(
                    label: "Gas 使用量",
                    value: gasUsed
                )
            }
        }
        .listRowBackground(Color.clear)
        .id("section-4")
    }
    
    // MARK: - Addresses Section
    private var addressesSection: some View {
        Section("地址資訊") {
            TransactionDetailRow(
                label: transaction.type == .sent ? "發送至" : "接收自",
                value: formatAddress(transaction.type == .sent ? transaction.to : transaction.from),
                fullValue: transaction.type == .sent ? transaction.to : transaction.from,
                copyable: true
            )
            
            TransactionDetailRow(
                label: transaction.type == .sent ? "發送自" : "接收至",
                value: formatAddress(transaction.type == .sent ? transaction.from : transaction.to),
                fullValue: transaction.type == .sent ? transaction.from : transaction.to,
                copyable: true
            )
        }
        .listRowBackground(Color.clear)
        .id("section-5")
    }
    
    // MARK: - Timing Section
    private var timingSection: some View {
        Section("時間資訊") {
            TransactionDetailRow(
                label: "交易時間",
                value: formatFullDate(transaction.timestamp)
            )
            
            TransactionDetailRow(
                label: "相對時間",
                value: formatRelativeTime(transaction.timestamp)
            )
        }
        .listRowBackground(Color.clear)
        .id("section-6")
    }
    
    // MARK: - Actions Section
    private var actionsSection: some View {
        VStack(spacing: 8) {
            // View in Explorer Button
            if transaction.status == .completed {
                Button(action: {
                    // TODO: Open in blockchain explorer
                }) {
                    HStack {
                        Image(systemName: "safari")
                            .font(.system(size: 14))
                        
                        Text("在區塊鏈瀏覽器中查看")
                            .font(.system(size: 13, weight: .medium))
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10)
                    .background(Color.blue.opacity(0.15))
                    .foregroundColor(.blue)
                    .cornerRadius(8)
                }
                .buttonStyle(.plain)
            }
            
            // Copy Transaction Hash Button
            Button(action: {
                // Note: Pasteboard functionality is limited on watchOS
                // TODO: Implement watchOS-appropriate copy functionality
                print("Copy transaction hash: \(transaction.hash)")
            }) {
                HStack {
                    Image(systemName: "doc.on.doc")
                        .font(.system(size: 14))
                    
                    Text("複製交易雜湊")
                        .font(.system(size: 13, weight: .medium))
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 10)
                .background(Color.secondary.opacity(0.15))
                .foregroundColor(.secondary)
                .cornerRadius(8)
            }
            .buttonStyle(.plain)
        }
        .padding(.vertical, 8)
        .listRowBackground(Color.clear)
                .id("section-7")
    }
    
    // MARK: - Share Button
    private var shareButton: some View {
        Button(action: {
            showingShareSheet = true
        }) {
            Image(systemName: "square.and.arrow.up")
                .font(.system(size: 16))
        }
    }
    
    // MARK: - Computed Properties
    private var transactionTypeText: String {
        switch transaction.type {
        case .sent:
            return "發送交易"
        case .received:
            return "接收交易"
        }
    }
    
    private var statusColor: Color {
        switch transaction.status {
        case .pending:
            return .orange
        case .completed:
            return .green
        case .failed:
            return .red
        @unknown default:
            return .gray
        }
    }
    
    private var statusText: String {
        switch transaction.status {
        case .pending:
            return "處理中"
        case .completed:
            return "已完成"
        case .failed:
            return "失敗"
        @unknown default:
            return "未知狀態"
        }
    }
    
    private var statusIcon: some View {
        Group {
            switch transaction.status {
            case .pending:
                Image(systemName: "clock")
            case .completed:
                Image(systemName: "checkmark.circle.fill")
            case .failed:
                Image(systemName: "exclamationmark.triangle.fill")
            @unknown default:
                Image(systemName: "questionmark.circle")
            }
        }
    }
    
    // MARK: - Helper Methods
    private func formatAddress(_ address: String) -> String {
        guard address.count > 10 else { return address }
        let prefix = address.prefix(8)
        let suffix = address.suffix(6)
        return "\(prefix)...\(suffix)"
    }
    
    private func formatFullDate(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateStyle = .medium
        formatter.timeStyle = .medium
        formatter.locale = Locale(identifier: "zh_TW")
        return formatter.string(from: date)
    }
    
    private func formatRelativeTime(_ date: Date) -> String {
        let formatter = RelativeDateTimeFormatter()
        formatter.unitsStyle = .full
        formatter.locale = Locale(identifier: "zh_TW")
        return formatter.localizedString(for: date, relativeTo: Date())
    }
}

// MARK: - Detail Row Component
struct TransactionDetailRow: View {
    let label: String
    let value: String
    var fullValue: String?
    var copyable: Bool = false
    
    @State private var showingFullValue = false
    
    var body: some View {
        HStack(alignment: .top) {
            Text(label)
                .font(.system(size: 12, weight: .medium))
                .foregroundColor(.secondary)
                .frame(width: 80, alignment: .leading)
            
            Spacer()
            
            VStack(alignment: .trailing, spacing: 2) {
                Text(showingFullValue ? (fullValue ?? value) : value)
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(.primary)
                    .multilineTextAlignment(.trailing)
                    .lineLimit(showingFullValue ? nil : 2)
                
                if copyable {
                    HStack(spacing: 8) {
                        if fullValue != nil {
                            Button(showingFullValue ? "收起" : "展開") {
                                withAnimation(.easeInOut(duration: 0.2)) {
                                    showingFullValue.toggle()
                                }
                            }
                            .font(.system(size: 10))
                            .foregroundColor(.blue)
                        }
                        
                        Button("複製") {
                            // Note: Pasteboard functionality is limited on watchOS
                            // TODO: Implement watchOS-appropriate copy functionality
                            print("Copy value: \(fullValue ?? value)")
                        }
                        .font(.system(size: 10))
                        .foregroundColor(.blue)
                    }
                }
            }
        }
        .padding(.vertical, 4)
    }
}

// MARK: - Share Sheet
struct ShareSheet: View {
    let transaction: TransactionModel
    @Environment(\.dismiss) var dismiss
    
    var body: some View {
        NavigationView {
            VStack(spacing: 20) {
                Text("分享交易")
                    .font(.system(size: 16, weight: .semibold))
                
                Text("分享此交易的詳細資訊")
                    .font(.system(size: 12))
                    .foregroundColor(.secondary)
                
                VStack(alignment: .leading, spacing: 8) {
                    Text("交易雜湊:")
                        .font(.system(size: 11, weight: .medium))
                        .foregroundColor(.secondary)
                    
                    Text(transaction.hash)
                        .font(.system(size: 10, design: .monospaced))
                        .foregroundColor(.primary)
                }
                .padding()
                .background(Color.white.opacity(0.05))
                .cornerRadius(8)
                
                Button("複製並關閉") {
                    // Note: Pasteboard functionality is limited on watchOS
                    // TODO: Implement watchOS-appropriate copy functionality
                    print("Copy share text: \(shareText)")
                    dismiss()
                }
                .font(.system(size: 13, weight: .medium))
                .foregroundColor(.blue)
                .padding(.horizontal, 20)
                .padding(.vertical, 8)
                .background(Color.blue.opacity(0.15))
                .cornerRadius(8)
            }
            .padding()
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("完成") {
                        dismiss()
                    }
                    .font(.system(size: 14))
                }
            }
        }
    }
    
    private var shareText: String {
        return """
        交易詳情
        類型: \(transaction.type == .sent ? "發送" : "接收")
        金額: \(transaction.amountText)
        狀態: \(transaction.status == .completed ? "已完成" : transaction.status == .pending ? "處理中" : "失敗")
        雜湊: \(transaction.hash)
        """
    }
}

#Preview {
    TransactionDetailView(
        transaction: TransactionModel(
            id: "1",
            hash: "0x1234567890abcdef1234567890abcdef12345678",
            from: "0xabcdef1234567890abcdef1234567890abcdef12",
            to: "0x1234567890abcdef1234567890abcdef12345678",
            value: "0.5",
            symbol: "ETH",
            timestamp: Date(),
            status: .completed,
            type: .sent,
            usdValue: "1250.00",
            chainId: "1",
            chainName: "Ethereum"
        )
    )
}
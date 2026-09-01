//
//  TransactionHistoryView.swift
//  WatchWallet Watch App
//
//  Enhanced transaction history view with search and filtering
//  Based on watchOS 2024 design guidelines
//

import SwiftUI
import UIKit

struct TransactionHistoryView: View {
    @StateObject private var viewModel = TransactionHistoryViewModel()
    @State private var selectedFilter: TransactionFilter = .all
    @State private var searchText = ""
    @State private var selectedNetwork: String = "all"
    @State private var scrollPosition: CGFloat = 0
    @State private var showError = false
    @Environment(\.dismiss) var dismiss
    
    var body: some View {
        ZStack {
            NavigationStack {
                Group {
                    if viewModel.isLoading && viewModel.transactions.isEmpty {
                        Color.clear // Loading handled by ZStack overlay
                    } else if viewModel.filteredTransactions.isEmpty {
                        emptyStateView
                    } else {
                        transactionListView
                    }
                }
                .navigationTitle("交易紀錄")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button("完成") { dismiss() }
                            .font(.system(size: 14, weight: .medium))
                    }
                }
                .onAppear {
                    viewModel.loadTransactions()
                }
                .onChange(of: searchText) { _, newValue in
                    viewModel.searchTransactions(query: newValue)
                }
                .refreshable {
                    await viewModel.refreshTransactions()
                }
            }
            
            // 全域載入動畫
            if viewModel.isLoading && viewModel.transactions.isEmpty {
                LoadingView(message: "正在載入交易紀錄...")
            }
        }
        .toast(isPresented: $showError, message: viewModel.error ?? "", type: .error)
        .onChange(of: viewModel.error) { oldValue, newValue in
            if newValue != nil {
                showError = true
            }
        }
    }
    
    // MARK: - Loading View (Removed in favor of unified LoadingView)
    /*
    private var loadingView: some View {
        VStack(spacing: 16) {
            ProgressView()
                .scaleEffect(0.8)
            
            Text("載入交易紀錄...")
                .font(.system(size: 12))
                .foregroundColor(.secondary)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
    */
    
    // MARK: - Search and Network Section
    private var searchAndNetworkSection: some View {
        VStack(spacing: 8) {
            // Search Bar
            HStack {
                Image(systemName: "magnifyingglass")
                    .font(.system(size: 14))
                    .foregroundColor(.secondary)
                
                TextField("搜尋交易...", text: $searchText)
                    .font(.system(size: 14))
                    .textFieldStyle(.plain)
                
                if !searchText.isEmpty {
                    Button(action: {
                        searchText = ""
                    }) {
                        Image(systemName: "xmark.circle.fill")
                            .font(.system(size: 14))
                            .foregroundColor(.secondary)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(Color.white.opacity(0.1))
            .cornerRadius(8)
            
            // Network Filter Row
            if !viewModel.availableNetworks.isEmpty {
                HStack {
                    Text("網路")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundColor(.secondary)
                    
                    Spacer()
                    
                    networkFilterButton
                }
            }
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 8)
        .listRowBackground(Color.clear)
    }
    
    // MARK: - Network Filter Button
    private var networkFilterButton: some View {
        Button(action: {
            // Cycle through available networks on tap
            if let currentIndex = viewModel.availableNetworks.firstIndex(of: selectedNetwork) {
                let nextIndex = (currentIndex + 1) % (viewModel.availableNetworks.count + 1)
                if nextIndex == 0 {
                    selectedNetwork = "all"
                    viewModel.filterByNetwork("all")
                } else {
                    let nextNetwork = viewModel.availableNetworks[nextIndex - 1]
                    selectedNetwork = nextNetwork
                    viewModel.filterByNetwork(nextNetwork)
                }
            } else {
                // If current selection is "all", move to first network
                if !viewModel.availableNetworks.isEmpty {
                    let firstNetwork = viewModel.availableNetworks[0]
                    selectedNetwork = firstNetwork
                    viewModel.filterByNetwork(firstNetwork)
                }
            }
        }) {
            HStack(spacing: 4) {
                Text(selectedNetwork == "all" ? "全部" : selectedNetwork)
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(.blue)
                
                Image(systemName: "chevron.down")
                    .font(.system(size: 10))
                    .foregroundColor(.blue)
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(Color.blue.opacity(0.1))
            .cornerRadius(6)
        }
        .buttonStyle(.plain)
    }
    
    // MARK: - Transaction List View
    private var transactionListView: some View {
        ScrollViewReader { proxy in
            List {
                // Search and Network Filter Section
                searchAndNetworkSection
                
                // Filter Section
                filterSection
                
                // Transaction Items
                ForEach(viewModel.filteredTransactions) { transaction in
                    NavigationLink(destination: TransactionDetailView(transaction: transaction)) {
                        TransactionRow(transaction: transaction)
                    }
                    .listRowBackground(Color.clear)
                                            .swipeActions(edge: .trailing) {
                            Button("詳情") {
                                viewModel.showTransactionDetail(transaction)
                            }
                            .tint(.blue)
                            
                            if transaction.status == .completed {
                                Button("分享") {
                                    viewModel.shareTransaction(transaction)
                                }
                                .tint(.green)
                            }
                        }
                }
                
                // Load More Footer
                if viewModel.hasMoreTransactions {
                    loadMoreFooter
                }
            }
            .listStyle(.plain)
            .accessibilityIdentifier("TransactionList")
            .digitalCrownRotation($scrollPosition)
            .focusable(true)
            .onChange(of: scrollPosition) { _, newValue in
                withAnimation(.easeInOut(duration: 0.3)) {
                    proxy.scrollTo("transaction-\(Int(newValue * 10) % viewModel.filteredTransactions.count)", anchor: .top)
                }
            }
        }
    }
    
    // MARK: - Filter Section
    private var filterSection: some View {
        VStack(spacing: 8) {
            // Transaction Type Filters
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(TransactionFilter.allCases, id: \.self) { filter in
                        FilterChip(
                            title: filter.title,
                            isSelected: selectedFilter == filter
                        ) {
                            selectedFilter = filter
                            viewModel.filterTransactions(by: filter)
                        }
                        .accessibilityIdentifier("FilterChip_\(filter.title)")
                    }
                }
                .padding(.horizontal, 4)
            }
            
            // Network Status
            if selectedNetwork != "all" {
                HStack {
                    Image(systemName: "network")
                        .font(.system(size: 10))
                        .foregroundColor(.blue)
                    
                    Text(selectedNetwork)
                        .font(.system(size: 10, weight: .medium))
                        .foregroundColor(.blue)
                    
                    Spacer()
                    
                    Button("清除") {
                        selectedNetwork = "all"
                        viewModel.filterByNetwork("all")
                    }
                    .font(.system(size: 10))
                    .foregroundColor(.secondary)
                }
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(Color.blue.opacity(0.1))
                .cornerRadius(6)
            }
        }
        .listRowBackground(Color.clear)
            }
    
    // MARK: - Load More Footer
    private var loadMoreFooter: some View {
        HStack {
            if viewModel.isLoadingMore {
                ProgressView()
                    .scaleEffect(0.6)
                
                Text("載入更多...")
                    .font(.system(size: 10))
                    .foregroundColor(.secondary)
            } else {
                Button("載入更多交易") {
                    Task {
                        await viewModel.loadMoreTransactions()
                    }
                }
                .font(.system(size: 11))
                .foregroundColor(.blue)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 8)
        .listRowBackground(Color.clear)
                .onAppear {
            if !viewModel.isLoadingMore {
                Task {
                    await viewModel.loadMoreTransactions()
                }
            }
        }
    }
    
    // MARK: - Empty State View
    private var emptyStateView: some View {
        VStack(spacing: 16) {
            // Icon
            ZStack {
                Circle()
                    .fill(Color.blue.opacity(0.1))
                    .frame(width: 60, height: 60)
                
                Image(systemName: searchText.isEmpty ? "doc.text" : "magnifyingglass")
                    .font(.system(size: 24))
                    .foregroundColor(.blue)
            }
            
            // Title and Description
            VStack(spacing: 6) {
                Text(emptyStateTitle)
                    .font(.system(size: 14, weight: .medium))
                    .foregroundColor(.primary)
                
                Text(emptyStateDescription)
                    .font(.system(size: 11))
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
            }
            
            // Action Buttons
            VStack(spacing: 8) {
                if !searchText.isEmpty {
                    Button("清除搜尋") {
                        searchText = ""
                    }
                    .font(.system(size: 11, weight: .medium))
                    .foregroundColor(.blue)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 6)
                    .background(Color.blue.opacity(0.1))
                    .cornerRadius(12)
                }
                
                if selectedFilter != .all || selectedNetwork != "all" {
                    Button("重置篩選") {
                        selectedFilter = .all
                        selectedNetwork = "all"
                        viewModel.resetFilters()
                    }
                    .font(.system(size: 11, weight: .medium))
                    .foregroundColor(.secondary)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 6)
                    .background(Color.secondary.opacity(0.1))
                    .cornerRadius(12)
                }
                
                if viewModel.transactions.isEmpty && !viewModel.isLoading {
                    Button("重新整理") {
                        Task {
                            await viewModel.refreshTransactions()
                        }
                    }
                    .font(.system(size: 11, weight: .medium))
                    .foregroundColor(.green)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 6)
                    .background(Color.green.opacity(0.1))
                    .cornerRadius(12)
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(.horizontal, 20)
    }
    
    // MARK: - Computed Properties
    private var emptyStateTitle: String {
        if !searchText.isEmpty {
            return "找不到相關交易"
        } else if selectedFilter != .all || selectedNetwork != "all" {
            return "沒有符合篩選條件的交易"
        } else if viewModel.transactions.isEmpty {
            return "暫無交易紀錄"
        } else {
            return "沒有結果"
        }
    }
    
    private var emptyStateDescription: String {
        if !searchText.isEmpty {
            return "嘗試調整搜尋條件\n或清除搜尋重新開始"
        } else if selectedFilter != .all || selectedNetwork != "all" {
            return "嘗試重置篩選條件\n查看所有交易紀錄"
        } else if viewModel.transactions.isEmpty {
            return "完成您的第一筆交易\n即可在此查看紀錄"
        } else {
            return "請檢查篩選條件"
        }
    }
}

// MARK: - FilterChip Component
struct FilterChip: View {
    let title: String
    let isSelected: Bool
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 10, weight: .medium))
                .foregroundColor(isSelected ? .white : .secondary)
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(
                    RoundedRectangle(cornerRadius: 12)
                        .fill(isSelected ? Color.blue : Color.white.opacity(0.1))
                )
        }
        .buttonStyle(.plain)
        .animation(.easeInOut(duration: 0.2), value: isSelected)
    }
}

struct TransactionRow: View {
    let transaction: TransactionModel
    @State private var isPressed = false
    
    var body: some View {
        HStack(spacing: 12) {
            // Transaction Icon with Status Overlay
            ZStack {
                Circle()
                    .fill(transaction.type.color.opacity(0.15))
                    .frame(width: 36, height: 36)
                
                Image(systemName: transaction.type.icon)
                    .font(.system(size: 16, weight: .medium))
                    .foregroundColor(transaction.type.color)
                
                // Status overlay
                if transaction.status != .completed {
                    Circle()
                        .fill(statusColor.opacity(0.9))
                        .frame(width: 12, height: 12)
                        .overlay(
                            statusIcon
                                .font(.system(size: 6, weight: .bold))
                                .foregroundColor(.white)
                        )
                        .offset(x: 14, y: -14)
                }
            }
            
            // Transaction Details
            VStack(alignment: .leading, spacing: 3) {
                // Main row: Type and Amount
                HStack {
                    Text(transactionTypeText)
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundColor(.primary)
                    
                    Spacer()
                    
                    VStack(alignment: .trailing, spacing: 1) {
                        Text(amountText)
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(transaction.type.color)
                        
                        if let usdValue = transaction.usdValue {
                            Text("$\(usdValue)")
                                .font(.system(size: 9))
                                .foregroundColor(.secondary)
                        }
                    }
                }
                
                // Secondary row: Address and Time
                HStack {
                    HStack(spacing: 4) {
                        // Network indicator
                        if let chainName = transaction.chainName {
                            Text(getNetworkShortName(chainName))
                                .font(.system(size: 8, weight: .medium))
                                .foregroundColor(.blue)
                                .padding(.horizontal, 4)
                                .padding(.vertical, 1)
                                .background(Color.blue.opacity(0.1))
                                .cornerRadius(4)
                        }
                        
                        Text(formatAddress(transaction.type == .sent ? transaction.to : transaction.from))
                            .font(.system(size: 10))
                            .foregroundColor(.secondary)
                    }
                    
                    Spacer()
                    
                    Text(formatTime(transaction.timestamp))
                        .font(.system(size: 10))
                        .foregroundColor(.secondary)
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(Color.white.opacity(isPressed ? 0.1 : 0.05))
        )
        .scaleEffect(isPressed ? 0.98 : 1.0)
        .animation(.easeInOut(duration: 0.1), value: isPressed)
        .onTapGesture {
            // Haptic feedback not available on watchOS
            // TODO: Consider WKInterfaceDevice.current().play(.click) for watchOS haptics
        }
        .onLongPressGesture(
            minimumDuration: 0,
            maximumDistance: .infinity,
            pressing: { pressing in
                isPressed = pressing
            },
            perform: {}
        )
    }
    
    // MARK: - Computed Properties
    private var transactionTypeText: String {
        switch transaction.type {
        case .sent:
            return "發送"
        case .received:
            return "收款"
        }
    }
    
    private var amountText: String {
        let prefix = transaction.type == .sent ? "-" : "+"
        return "\(prefix)\(transaction.value) \(transaction.symbol)"
    }
    
    private var statusColor: Color {
        switch transaction.status {
        case .pending:
            return .orange
        case .failed:
            return .red
        case .completed:
            return .green
        @unknown default:
            return .gray
        }
    }
    
    private var statusIcon: some View {
        Group {
            switch transaction.status {
            case .pending:
                Image(systemName: "clock")
            case .failed:
                Image(systemName: "xmark")
            case .completed:
                Image(systemName: "checkmark")
            @unknown default:
                Image(systemName: "questionmark")
            }
        }
    }
    
    // MARK: - Helper Methods
    private func getNetworkShortName(_ networkName: String) -> String {
        switch networkName.lowercased() {
        case let name where name.contains("ethereum") || name.contains("eth"):
            return "ETH"
        case let name where name.contains("polygon"):
            return "MATIC"
        case let name where name.contains("binance") || name.contains("bsc"):
            return "BSC"
        case let name where name.contains("bitcoin") || name.contains("btc"):
            return "BTC"
        default:
            return String(networkName.prefix(3).uppercased())
        }
    }
    
    private func formatAddress(_ address: String) -> String {
        guard address.count > 10 else { return address }
        let prefix = address.prefix(6)
        let suffix = address.suffix(4)
        return "\(prefix)...\(suffix)"
    }
    
    private func formatTime(_ date: Date) -> String {
        let formatter = RelativeDateTimeFormatter()
        formatter.unitsStyle = .abbreviated
        return formatter.localizedString(for: date, relativeTo: Date())
    }
}

enum TransactionFilter: CaseIterable {
    case all
    case sent
    case received
    
    var title: String {
        switch self {
        case .all: return "全部"
        case .sent: return "發送"
        case .received: return "收款"
        }
    }
}

#Preview {
    TransactionHistoryView()
}
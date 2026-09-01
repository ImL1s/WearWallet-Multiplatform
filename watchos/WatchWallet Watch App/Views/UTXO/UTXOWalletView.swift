//
//  UTXOWalletView.swift
//  WatchWallet Watch App
//
//  UTXO 鏈錢包主視圖
//

import SwiftUI
import coreKmp

struct UTXOWalletView: View {
    @StateObject private var utxoService = UTXOService.shared
    @State private var showingChainSelector = false
    @State private var showingSendView = false
    @State private var showingReceiveView = false
    @State private var showingUTXOList = false
    @State private var selectedTransaction: UTXOTransaction?
    
    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                // 鏈選擇器
                ChainSelectorCard(
                    currentChain: utxoService.currentChain,
                    onTap: {
                        showingChainSelector = true
                    }
                )
                
                // 餘額卡片
                if let balance = utxoService.balance {
                    UTXOBalanceCard(balance: balance)
                        .onTapGesture {
                            utxoService.refreshData()
                        }
                }
                
                // 操作按鈕
                HStack(spacing: 8) {
                    UTXOActionButton(
                        title: "發送",
                        icon: "arrow.up.circle.fill",
                        color: .orange
                    ) {
                        showingSendView = true
                    }
                    
                    UTXOActionButton(
                        title: "接收",
                        icon: "arrow.down.circle.fill",
                        color: .green
                    ) {
                        showingReceiveView = true
                    }
                    
                    UTXOActionButton(
                        title: "UTXO",
                        icon: "square.stack.3d.up.fill",
                        color: .blue
                    ) {
                        showingUTXOList = true
                    }
                }
                .padding(.horizontal)
                
                // 手續費估算
                if let feeEstimate = utxoService.feeEstimate {
                    FeeEstimateCard(estimate: feeEstimate, chain: utxoService.currentChain)
                }
                
                // 最近交易
                if !utxoService.recentTransactions.isEmpty {
                    UTXOTransactionList(
                        transactions: utxoService.recentTransactions,
                        chain: utxoService.currentChain,
                        onSelectTransaction: { transaction in
                            selectedTransaction = transaction
                        }
                    )
                }
                
                // 載入指示器
                if utxoService.isLoading {
                    ProgressView("載入中...")
                        .padding()
                }
            }
        }
        .navigationTitle(utxoService.currentChain.displayName)
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $showingChainSelector) {
            UTXOChainSelectorView(
                currentChain: utxoService.currentChain,
                onSelectChain: { chain in
                    utxoService.switchChain(chain)
                    showingChainSelector = false
                }
            )
        }
        .sheet(isPresented: $showingSendView) {
            UTXOSendView(chain: utxoService.currentChain)
        }
        .sheet(isPresented: $showingReceiveView) {
            UTXOReceiveView(chain: utxoService.currentChain)
        }
        .sheet(isPresented: $showingUTXOList) {
            UTXOListView(utxos: utxoService.utxos, chain: utxoService.currentChain)
        }
        .sheet(item: $selectedTransaction) { transaction in
            UTXOTransactionDetailView(transaction: transaction, chain: utxoService.currentChain)
        }
        .alert("錯誤", isPresented: .constant(utxoService.error != nil)) {
            Button("確定") {
                utxoService.error = nil
            }
        } message: {
            Text(utxoService.error ?? "")
        }
        .onAppear {
            utxoService.refreshData()
        }
    }
}

// MARK: - Chain Selector Card

struct ChainSelectorCard: View {
    let currentChain: UTXOChainType
    let onTap: () -> Void
    
    var body: some View {
        Button(action: onTap) {
            HStack {
                Image(systemName: currentChain.icon)
                    .font(.title3)
                    .foregroundColor(currentChain.color)
                
                VStack(alignment: .leading, spacing: 2) {
                    Text(currentChain.displayName)
                        .font(.caption)
                        .foregroundColor(.primary)
                    
                    Text(currentChain.symbol)
                        .font(.caption2)
                        .foregroundColor(.secondary)
                }
                
                Spacer()
                
                Image(systemName: "chevron.right")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            .padding(.horizontal)
            .padding(.vertical, 8)
            .background(Color.gray.opacity(0.1))
            .cornerRadius(10)
        }
        .buttonStyle(.plain)
        .padding(.horizontal)
    }
}

// MARK: - Balance Card

struct UTXOBalanceCard: View {
    let balance: UTXOBalance
    
    var body: some View {
        VStack(spacing: 8) {
            Text("總餘額")
                .font(.caption2)
                .foregroundColor(.secondary)
            
            Text("\(balance.formattedTotal) \(balance.chain.symbol)")
                .font(.title3)
                .fontWeight(.bold)
                .foregroundColor(.primary)
            
            HStack(spacing: 12) {
                VStack(spacing: 2) {
                    Text("已確認")
                        .font(.system(size: 10))
                        .foregroundColor(.secondary)
                    Text(balance.formattedConfirmed)
                        .font(.caption2)
                        .foregroundColor(.green)
                }
                
                if balance.unconfirmed > 0 {
                    VStack(spacing: 2) {
                        Text("未確認")
                            .font(.system(size: 10))
                            .foregroundColor(.secondary)
                        Text(balance.formattedUnconfirmed)
                            .font(.caption2)
                            .foregroundColor(.orange)
                    }
                }
                
                VStack(spacing: 2) {
                    Text("UTXO")
                        .font(.system(size: 10))
                        .foregroundColor(.secondary)
                    Text("\(balance.utxoCount)")
                        .font(.caption2)
                        .foregroundColor(.blue)
                }
            }
        }
        .padding()
        .frame(maxWidth: .infinity)
        .background(Color.gray.opacity(0.1))
        .cornerRadius(12)
        .padding(.horizontal)
    }
}

// MARK: - Action Buttons

struct UTXOActionButton: View {
    let title: String
    let icon: String
    let color: Color
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            VStack(spacing: 4) {
                Image(systemName: icon)
                    .font(.system(size: 20))
                    .foregroundColor(color)
                
                Text(title)
                    .font(.system(size: 10))
                    .foregroundColor(.primary)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 6)
            .background(Color.gray.opacity(0.1))
            .cornerRadius(8)
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Fee Estimate Card

struct FeeEstimateCard: View {
    let estimate: UTXOFeeEstimate
    let chain: UTXOChainType
    
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("手續費估算 (sat/vB)")
                .font(.caption2)
                .foregroundColor(.secondary)
            
            HStack(spacing: 8) {
                FeeOption(label: "最快", value: estimate.fastestFee, color: .red)
                FeeOption(label: "快速", value: estimate.halfHourFee, color: .orange)
                FeeOption(label: "一般", value: estimate.hourFee, color: .yellow)
                FeeOption(label: "經濟", value: estimate.economyFee, color: .green)
            }
        }
        .padding()
        .background(Color.gray.opacity(0.1))
        .cornerRadius(10)
        .padding(.horizontal)
    }
}

struct FeeOption: View {
    let label: String
    let value: Int64
    let color: Color
    
    var body: some View {
        VStack(spacing: 2) {
            Text(label)
                .font(.system(size: 9))
                .foregroundColor(.secondary)
            
            Text("\(value)")
                .font(.system(size: 11))
                .fontWeight(.medium)
                .foregroundColor(color)
        }
        .frame(maxWidth: .infinity)
    }
}

// MARK: - Transaction List

struct UTXOTransactionList: View {
    let transactions: [UTXOTransaction]
    let chain: UTXOChainType
    let onSelectTransaction: (UTXOTransaction) -> Void
    
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("最近交易")
                .font(.caption)
                .foregroundColor(.secondary)
                .padding(.horizontal)
            
            ForEach(transactions.prefix(5)) { transaction in
                UTXOTransactionRow(
                    transaction: transaction,
                    chain: chain,
                    onSelect: {
                        onSelectTransaction(transaction)
                    }
                )
            }
        }
    }
}

struct UTXOTransactionRow: View {
    let transaction: UTXOTransaction
    let chain: UTXOChainType
    let onSelect: () -> Void
    
    private var shortTxid: String {
        let txid = transaction.txid
        guard txid.count > 10 else { return txid }
        return "\(txid.prefix(6))...\(txid.suffix(4))"
    }
    
    var body: some View {
        Button(action: onSelect) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(shortTxid)
                        .font(.caption2)
                        .foregroundColor(.primary)
                    
                    Text("\(transaction.confirmations) 確認")
                        .font(.system(size: 10))
                        .foregroundColor(transaction.confirmations > 0 ? .green : .orange)
                }
                
                Spacer()
                
                VStack(alignment: .trailing, spacing: 2) {
                    Text(transaction.formattedFee)
                        .font(.caption2)
                        .foregroundColor(.primary)
                    
                    Text("\(chain.symbol)")
                        .font(.system(size: 10))
                        .foregroundColor(.secondary)
                }
            }
            .padding(.horizontal)
            .padding(.vertical, 6)
            .background(Color.gray.opacity(0.05))
        }
        .buttonStyle(.plain)
    }
}

#Preview {
    NavigationStack {
        UTXOWalletView()
    }
}
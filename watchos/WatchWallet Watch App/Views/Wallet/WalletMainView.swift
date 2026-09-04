//
//  WalletMainView.swift
//  WatchWallet Watch App
//
//  Main wallet view showing balance and transaction options
//

import SwiftUI
import coreKmp

struct WalletMainView: View {
    @StateObject private var viewModel = WalletMainViewModel()
    @State private var showTokenSelector = false
    @State private var showWalletSwitcher = false
    @State private var showSendView = false
    @State private var showReceiveView = false
    @State private var showSwapView = false
    @State private var showHistoryView = false
    @State private var showError = false
    
    var body: some View {
        ZStack {
            List {
                // 錢包切換器
                if viewModel.wallets.count > 1 {
                    WalletSwitcher(
                        currentWallet: viewModel.currentWallet,
                        wallets: viewModel.wallets,
                        isShowing: $showWalletSwitcher,
                        onWalletSelected: { wallet in
                            viewModel.switchWallet(to: wallet)
                        }
                    )
                    .listRowBackground(Color.clear)
                    .listRowInsets(EdgeInsets())
                }
                
                // 餘額卡片
                if let currentWallet = viewModel.currentWallet {
                    BalanceCard(
                        balance: viewModel.balance,
                        tokenSymbol: viewModel.selectedToken?.symbol ?? "ETH",
                        walletName: currentWallet.name,
                        walletType: currentWallet.type,
                        isLoading: viewModel.isLoading,
                        onRefresh: { viewModel.refreshBalance() },
                        onTap: { showTokenSelector = true }
                    )
                    .listRowBackground(Color.clear)
                    .listRowInsets(EdgeInsets())
                } else {
                    // 空錢包狀態卡片
                    VStack(spacing: 8) {
                        HStack {
                            Image(systemName: "wallet.pass")
                                .font(.system(size: 12))
                                .foregroundColor(.gray)
                            Text("無錢包")
                                .font(.system(size: 12))
                                .foregroundColor(.secondary)
                            Spacer()
                        }
                        .padding(.horizontal, 12)
                        
                        VStack(spacing: 4) {
                            Text("--")
                                .font(.system(size: 24, weight: .semibold, design: .rounded))
                                .foregroundColor(.gray)
                            
                            Text("請創建錢包")
                                .font(.system(size: 14))
                                .foregroundColor(.secondary)
                        }
                        .padding(.vertical, 12)
                    }
                    .padding(.bottom, 8)
                    .frame(maxWidth: .infinity)
                    .background(.ultraThinMaterial)
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
                    .listRowBackground(Color.clear)
                    .listRowInsets(EdgeInsets())
                }
                
                // 交易按鈕
                TransactionButtons(
                    onSendClick: { showSendView = true },
                    onReceiveClick: { showReceiveView = true },
                    onSwapClick: { showSwapView = true },
                    enabled: viewModel.currentWallet != nil && !viewModel.isLoading
                )
                .listRowBackground(Color.clear)
                .listRowInsets(EdgeInsets())
                
                // 交易歷史 - 始終顯示，無論是否有交易紀錄
                if viewModel.currentWallet != nil {
                    TransactionHistoryCard(
                        transactions: viewModel.recentTransactions,
                        onShowAll: { showHistoryView = true }
                    )
                    .listRowBackground(Color.clear)
                    .listRowInsets(EdgeInsets())
                }
                
                // 創建錢包提示（如果沒有錢包）
                if viewModel.wallets.isEmpty && !viewModel.isLoading {
                    VStack(spacing: 12) {
                        Image(systemName: "wallet.pass")
                            .font(.system(size: 32))
                            .foregroundColor(.gray)
                        
                        Text("尚未創建錢包")
                            .font(.system(size: 16, weight: .medium))
                            .foregroundColor(.primary)
                        
                        Text("請先創建或導入錢包以使用發送和接收功能")
                            .font(.system(size: 12))
                            .foregroundColor(.secondary)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 8)
                        
                        NavigationLink(destination: WalletManagementView()) {
                            Text("創建錢包")
                                .font(.system(size: 14, weight: .medium))
                                .foregroundColor(.white)
                                .padding(.horizontal, 20)
                                .padding(.vertical, 8)
                                .background(Color.blue)
                                .cornerRadius(20)
                        }
                        .buttonStyle(.plain)
                    }
                    .padding(.vertical, 20)
                    .background(Color.white.opacity(0.05))
                    .cornerRadius(12)
                    .listRowBackground(Color.clear)
                    .listRowInsets(EdgeInsets())
                }
                
                // 功能選單 (NFT & Alerts)
                if viewModel.currentWallet != nil {
                    Section {
                        NavigationLink(destination: NFTListView()) {
                            Label {
                                Text("NFTs")
                            } icon: {
                                Image(systemName: "photo.stack")
                                    .foregroundColor(.purple)
                            }
                        }
                        
                        NavigationLink(destination: PriceAlertsView()) {
                            Label {
                                Text("Price Alerts")
                            } icon: {
                                Image(systemName: "bell.fill")
                                    .foregroundColor(.orange)
                            }
                        }
                    }
                    .listRowBackground(Color.white.opacity(0.1))
                }
                
                // 錯誤訊息 (如果不是用 Toast 顯示，可以在這裡保留或移除)
                if let error = viewModel.error {
                    ErrorMessage(message: error)
                        .listRowBackground(Color.clear)
                        .listRowInsets(EdgeInsets())
                }
            }
            .listStyle(.plain)
            
            // 全域載入動畫
            if viewModel.isLoading {
                LoadingView(message: "正在載入...")
            }
        }
        .toast(isPresented: $showError, message: viewModel.error ?? "", type: .error)
        .onChange(of: viewModel.error) { oldValue, newValue in
            if newValue != nil {
                showError = true
            }
        }
        .sheet(isPresented: $showTokenSelector) {
            TokenSelectorView(
                selectedToken: viewModel.selectedToken,
                tokens: viewModel.availableTokens,
                onTokenSelected: { token in
                    viewModel.selectToken(token)
                    showTokenSelector = false
                }
            )
        }
        .onAppear {
            viewModel.onAppear()
        }
        .sheet(isPresented: $showSendView) {
            SendView()
        }
        .sheet(isPresented: $showReceiveView) {
            ReceiveView()
        }
        .sheet(isPresented: $showHistoryView) {
            TransactionHistoryView()
        }
        .sheet(isPresented: $showSwapView) {
            SwapView(isPresented: $showSwapView)
        }
    }
}


#Preview {
    NavigationStack {
        WalletMainView()
    }
}
//
//  TokenManagementView.swift
//  WatchWallet Watch App
//
//  Token management and custom token addition
//

import SwiftUI

struct TokenManagementView: View {
    @StateObject private var viewModel = TokenManagementViewModel()
    @State private var showAddToken = false
    @State private var searchText = ""
    
    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                // Search bar
                HStack {
                    Image(systemName: "magnifyingglass")
                        .font(.system(size: 12))
                        .foregroundColor(.secondary)
                    TextField("搜尋代幣", text: $searchText)
                        .font(.system(size: 13))
                        .textFieldStyle(.plain)
                }
                .padding(8)
                .background(Color.white.opacity(0.1))
                .cornerRadius(8)
                .padding(.horizontal)
                
                // Token list
                ForEach(filteredTokens) { token in
                    TokenRow(
                        token: token,
                        isEnabled: viewModel.isTokenEnabled(token),
                        onToggle: { enabled in
                            viewModel.toggleToken(token, enabled: enabled)
                        }
                    )
                }
                
                // Add custom token button
                Button(action: { showAddToken = true }) {
                    HStack {
                        Image(systemName: "plus.circle")
                            .font(.system(size: 14))
                        Text("添加自定義代幣")
                            .font(.system(size: 13))
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10)
                }
                .buttonStyle(.bordered)
                .padding(.horizontal)
                .padding(.top, 8)
            }
            .padding(.vertical)
        }
        .navigationTitle("代幣管理")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $showAddToken) {
            AddCustomTokenView { token in
                viewModel.addCustomToken(token)
                showAddToken = false
            }
        }
    }
    
    private var filteredTokens: [TokenInfo] {
        if searchText.isEmpty {
            return viewModel.allTokens
        } else {
            return viewModel.allTokens.filter {
                $0.symbol.localizedCaseInsensitiveContains(searchText) ||
                $0.name.localizedCaseInsensitiveContains(searchText)
            }
        }
    }
}

struct TokenRow: View {
    let token: TokenInfo
    let isEnabled: Bool
    let onToggle: (Bool) -> Void
    
    var body: some View {
        HStack {
            // Token icon
            ZStack {
                Circle()
                    .fill(Color.blue.opacity(0.2))
                    .frame(width: 32, height: 32)
                
                Text(String(token.symbol.prefix(1)))
                    .font(.system(size: 14, weight: .medium))
                    .foregroundColor(.blue)
            }
            
            // Token info
            VStack(alignment: .leading, spacing: 2) {
                Text(token.symbol)
                    .font(.system(size: 14, weight: .medium))
                Text(token.name)
                    .font(.system(size: 11))
                    .foregroundColor(.secondary)
            }
            
            Spacer()
            
            // Toggle
            Toggle("", isOn: .init(
                get: { isEnabled },
                set: { onToggle($0) }
            ))
            .labelsHidden()
            .scaleEffect(0.8)
        }
        .padding(.horizontal)
        .padding(.vertical, 6)
    }
}

struct AddCustomTokenView: View {
    @State private var contractAddress = ""
    @State private var tokenSymbol = ""
    @State private var tokenName = ""
    @State private var decimals = "18"
    @State private var isLoading = false
    @State private var showError = false
    @State private var errorMessage = ""
    @Environment(\.dismiss) var dismiss
    
    let onAdd: (TokenInfo) -> Void
    
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    // Contract Address
                    VStack(alignment: .leading, spacing: 6) {
                        Text("合約地址")
                            .font(.system(size: 12))
                            .foregroundColor(.secondary)
                        TextField("0x...", text: $contractAddress)
                            .font(.system(size: 13))
                            .textFieldStyle(.plain)
                            .padding(8)
                            .background(Color.white.opacity(0.1))
                            .cornerRadius(6)
                    }
                    
                    // Auto-detect button
                    if !contractAddress.isEmpty {
                        Button(action: autoDetectToken) {
                            HStack {
                                if isLoading {
                                    ProgressView()
                                        .scaleEffect(0.8)
                                } else {
                                    Image(systemName: "wand.and.rays")
                                        .font(.system(size: 12))
                                }
                                Text("自動檢測代幣資訊")
                                    .font(.system(size: 12))
                            }
                        }
                        .buttonStyle(.bordered)
                        .controlSize(.small)
                        .disabled(isLoading)
                    }
                    
                    // Token Symbol
                    VStack(alignment: .leading, spacing: 6) {
                        Text("代幣符號")
                            .font(.system(size: 12))
                            .foregroundColor(.secondary)
                        TextField("例：USDT", text: $tokenSymbol)
                            .font(.system(size: 13))
                            .textFieldStyle(.plain)
                            .padding(8)
                            .background(Color.white.opacity(0.1))
                            .cornerRadius(6)
                    }
                    
                    // Token Name
                    VStack(alignment: .leading, spacing: 6) {
                        Text("代幣名稱")
                            .font(.system(size: 12))
                            .foregroundColor(.secondary)
                        TextField("例：Tether USD", text: $tokenName)
                            .font(.system(size: 13))
                            .textFieldStyle(.plain)
                            .padding(8)
                            .background(Color.white.opacity(0.1))
                            .cornerRadius(6)
                    }
                    
                    // Decimals
                    VStack(alignment: .leading, spacing: 6) {
                        Text("小數位數")
                            .font(.system(size: 12))
                            .foregroundColor(.secondary)
                        TextField("18", text: $decimals)
                            .font(.system(size: 13))
                            .textFieldStyle(.plain)
                            .padding(8)
                            .background(Color.white.opacity(0.1))
                            .cornerRadius(6)
                    }
                    
                    if showError {
                        Text(errorMessage)
                            .font(.system(size: 11))
                            .foregroundColor(.red)
                            .multilineTextAlignment(.center)
                    }
                    
                    Button(action: addToken) {
                        Text("添加代幣")
                            .font(.system(size: 14, weight: .medium))
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10)
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(!canAdd)
                    .padding(.top, 8)
                }
                .padding()
            }
            .navigationTitle("添加自定義代幣")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { dismiss() }
                }
            }
        }
    }
    
    private var canAdd: Bool {
        !contractAddress.isEmpty && !tokenSymbol.isEmpty && 
        !tokenName.isEmpty && !decimals.isEmpty
    }
    
    private func autoDetectToken() {
        isLoading = true
        showError = false
        
        // Simulate token detection
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
            isLoading = false
            
            // In real implementation, fetch token info from blockchain
            if contractAddress.lowercased() == "0xdac17f958d2ee523a2206206994597c13d831ec7" {
                tokenSymbol = "USDT"
                tokenName = "Tether USD"
                decimals = "6"
            } else {
                showError = true
                errorMessage = "無法獲取代幣資訊，請手動輸入"
            }
        }
    }
    
    private func addToken() {
        guard let decimalsInt = Int(decimals) else { return }
        
        let token = TokenInfo(
            id: UUID().uuidString,
            contractAddress: contractAddress,
            symbol: tokenSymbol,
            name: tokenName,
            decimals: decimalsInt,
            chainId: "1", // Current network chain ID
            isCustom: true
        )
        
        onAdd(token)
    }
}


#Preview {
    NavigationStack {
        TokenManagementView()
    }
}
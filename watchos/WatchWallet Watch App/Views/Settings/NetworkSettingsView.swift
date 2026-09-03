//
//  NetworkSettingsView.swift
//  WatchWallet Watch App
//
//  Network settings and chain selection
//

import SwiftUI

struct NetworkSettingsView: View {
    @StateObject private var viewModel = NetworkSettingsViewModel()
    @State private var showCustomNetwork = false
    
    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                // Network list
                ForEach(viewModel.networks) { network in
                    NetworkRow(
                        network: network,
                        isSelected: network.id == viewModel.selectedNetwork?.id,
                        onSelect: {
                            viewModel.selectNetwork(network)
                        }
                    )
                }
                
                // Add custom network button
                Button(action: { showCustomNetwork = true }) {
                    HStack {
                        Image(systemName: "plus.circle")
                            .font(.system(size: 14))
                        Text("添加自定義網路")
                            .font(.system(size: 13))
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10)
                }
                .buttonStyle(.bordered)
                .padding(.top, 8)
            }
            .padding()
        }
        .navigationTitle("網路設定")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $showCustomNetwork) {
            AddCustomNetworkView { network in
                viewModel.addCustomNetwork(network)
                showCustomNetwork = false
            }
        }
    }
}

struct NetworkRow: View {
    let network: NetworkModel
    let isSelected: Bool
    let onSelect: () -> Void
    
    var body: some View {
        Button(action: onSelect) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    HStack {
                        Text(network.name)
                            .font(.system(size: 14, weight: .medium))
                            .foregroundColor(isSelected ? .white : .primary)
                        
                        if network.isTestnet {
                            Text("測試網")
                                .font(.system(size: 10))
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(Color.orange.opacity(0.3))
                                .foregroundColor(.orange)
                                .cornerRadius(4)
                        }
                    }
                    
                    Text("Chain ID: \(network.chainId)")
                        .font(.system(size: 11))
                        .foregroundColor(isSelected ? .white.opacity(0.7) : .secondary)
                }
                
                Spacer()
                
                if isSelected {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 16))
                        .foregroundColor(.white)
                }
            }
            .padding(12)
            .background(isSelected ? Color.blue : Color.white.opacity(0.05))
            .cornerRadius(8)
        }
        .buttonStyle(.plain)
    }
}

struct AddCustomNetworkView: View {
    @State private var networkName = ""
    @State private var chainId = ""
    @State private var rpcUrl = ""
    @State private var symbol = ""
    @State private var explorerUrl = ""
    @Environment(\.dismiss) var dismiss
    
    let onAdd: (NetworkModel) -> Void
    
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    // Network Name
                    VStack(alignment: .leading, spacing: 6) {
                        Text("網路名稱")
                            .font(.system(size: 12))
                            .foregroundColor(.secondary)
                        TextField("例：Polygon", text: $networkName)
                            .font(.system(size: 13))
                            .textFieldStyle(.plain)
                            .padding(8)
                            .background(Color.white.opacity(0.1))
                            .cornerRadius(6)
                    }
                    
                    // Chain ID
                    VStack(alignment: .leading, spacing: 6) {
                        Text("Chain ID")
                            .font(.system(size: 12))
                            .foregroundColor(.secondary)
                        TextField("例：137", text: $chainId)
                            .font(.system(size: 13))
                            .textFieldStyle(.plain)
                            .padding(8)
                            .background(Color.white.opacity(0.1))
                            .cornerRadius(6)
                    }
                    
                    // RPC URL
                    VStack(alignment: .leading, spacing: 6) {
                        Text("RPC URL")
                            .font(.system(size: 12))
                            .foregroundColor(.secondary)
                        TextField("https://...", text: $rpcUrl)
                            .font(.system(size: 13))
                            .textFieldStyle(.plain)
                            .padding(8)
                            .background(Color.white.opacity(0.1))
                            .cornerRadius(6)
                    }
                    
                    // Symbol
                    VStack(alignment: .leading, spacing: 6) {
                        Text("代幣符號")
                            .font(.system(size: 12))
                            .foregroundColor(.secondary)
                        TextField("例：MATIC", text: $symbol)
                            .font(.system(size: 13))
                            .textFieldStyle(.plain)
                            .padding(8)
                            .background(Color.white.opacity(0.1))
                            .cornerRadius(6)
                    }
                    
                    // Explorer URL (Optional)
                    VStack(alignment: .leading, spacing: 6) {
                        Text("區塊瀏覽器 (可選)")
                            .font(.system(size: 12))
                            .foregroundColor(.secondary)
                        TextField("https://...", text: $explorerUrl)
                            .font(.system(size: 13))
                            .textFieldStyle(.plain)
                            .padding(8)
                            .background(Color.white.opacity(0.1))
                            .cornerRadius(6)
                    }
                    
                    Button(action: addNetwork) {
                        Text("添加網路")
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
            .navigationTitle("添加自定義網路")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { dismiss() }
                }
            }
        }
    }
    
    private var canAdd: Bool {
        !networkName.isEmpty && !chainId.isEmpty && !rpcUrl.isEmpty && !symbol.isEmpty
    }
    
    private func addNetwork() {
        let network = NetworkModel(
            id: UUID().uuidString,
            name: networkName,
            chainId: chainId,
            rpcUrl: rpcUrl,
            symbol: symbol,
            explorerUrl: explorerUrl.isEmpty ? nil : explorerUrl,
            isTestnet: false,
            isCustom: true
        )
        onAdd(network)
    }
}


#Preview {
    NavigationStack {
        NetworkSettingsView()
    }
}
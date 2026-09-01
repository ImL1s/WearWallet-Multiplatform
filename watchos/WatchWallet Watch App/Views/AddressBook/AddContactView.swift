//
//  AddContactView.swift
//  WatchWallet Watch App
//
//  添加聯絡人視圖 - 用於創建新的地址簿聯絡人
//  Created: 2025-08-07
//

import SwiftUI

/**
 * 添加聯絡人視圖
 * 
 * 提供表單界面用於創建新的聯絡人
 */
struct AddContactView: View {
    @ObservedObject var viewModel: AddressBookViewModel
    @Environment(\.dismiss) private var dismiss
    
    // Form State
    @State private var name: String = ""
    @State private var address: String = ""
    @State private var chainType: String = "Ethereum"
    @State private var notes: String = ""
    @State private var tags: String = ""
    @State private var isFavorite: Bool = false
    @State private var showingScanner: Bool = false
    @State private var showingChainPicker: Bool = false
    @State private var isValidating: Bool = false
    
    // Validation State
    @State private var nameError: String?
    @State private var addressError: String?
    
    private let supportedChains = [
        "Ethereum", "BSC", "Polygon", "Arbitrum", "Optimism",
        "Avalanche", "Fantom", "Cronos", "Bitcoin", "Solana"
    ]
    
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    // 名稱輸入
                    nameField
                    
                    // 地址輸入
                    addressField
                    
                    // 區塊鏈選擇
                    chainSelector
                    
                    // 標籤輸入
                    tagsField
                    
                    // 備註輸入
                    notesField
                    
                    // 收藏開關
                    favoriteToggle
                    
                    // 操作按鈕
                    actionButtons
                }
                .padding()
            }
            .navigationTitle("添加聯絡人")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") {
                        dismiss()
                    }
                }
            }
            .sheet(isPresented: $showingScanner) {
                QRCodeScannerView { scannedAddress in
                    self.address = scannedAddress
                    showingScanner = false
                }
            }
            .sheet(isPresented: $showingChainPicker) {
                ChainPickerView(
                    selectedChain: $chainType,
                    chains: supportedChains
                )
            }
        }
    }
    
    // MARK: - Form Fields
    
    private var nameField: some View {
        VStack(alignment: .leading, spacing: 6) {
            Label("聯絡人名稱", systemImage: "person")
                .font(.system(size: 12, weight: .medium))
                .foregroundColor(.secondary)
            
            TextField("輸入名稱", text: $name)
                .font(.system(size: 14))
                .padding(8)
                .background(Color.gray.opacity(0.1))
                .cornerRadius(8)
                .onChange(of: name) { _ in
                    validateName()
                }
            
            if let error = nameError {
                Text(error)
                    .font(.system(size: 10))
                    .foregroundColor(.red)
            }
        }
    }
    
    private var addressField: some View {
        VStack(alignment: .leading, spacing: 6) {
            Label("錢包地址", systemImage: "wallet.pass")
                .font(.system(size: 12, weight: .medium))
                .foregroundColor(.secondary)
            
            HStack(spacing: 8) {
                TextField("輸入或掃描地址", text: $address)
                    .font(.system(size: 14))
                    .padding(8)
                    .background(Color.gray.opacity(0.1))
                    .cornerRadius(8)
                    .onChange(of: address) { _ in
                        validateAddress()
                    }
                
                Button(action: { showingScanner = true }) {
                    Image(systemName: "qrcode.viewfinder")
                        .font(.system(size: 16))
                }
                .buttonStyle(.bordered)
                .controlSize(.small)
                
                Button(action: pasteAddress) {
                    Image(systemName: "doc.on.clipboard")
                        .font(.system(size: 16))
                }
                .buttonStyle(.bordered)
                .controlSize(.small)
            }
            
            if let error = addressError {
                Text(error)
                    .font(.system(size: 10))
                    .foregroundColor(.red)
            }
        }
    }
    
    private var chainSelector: some View {
        VStack(alignment: .leading, spacing: 6) {
            Label("區塊鏈網路", systemImage: "link")
                .font(.system(size: 12, weight: .medium))
                .foregroundColor(.secondary)
            
            Button(action: { showingChainPicker = true }) {
                HStack {
                    Text(chainType)
                        .font(.system(size: 14))
                    Spacer()
                    Image(systemName: "chevron.down")
                        .font(.system(size: 12))
                }
                .padding(8)
                .background(Color.gray.opacity(0.1))
                .cornerRadius(8)
            }
            .buttonStyle(.plain)
        }
    }
    
    private var tagsField: some View {
        VStack(alignment: .leading, spacing: 6) {
            Label("標籤（用逗號分隔）", systemImage: "tag")
                .font(.system(size: 12, weight: .medium))
                .foregroundColor(.secondary)
            
            TextField("例如：朋友, DeFi", text: $tags)
                .font(.system(size: 14))
                .padding(8)
                .background(Color.gray.opacity(0.1))
                .cornerRadius(8)
        }
    }
    
    private var notesField: some View {
        VStack(alignment: .leading, spacing: 6) {
            Label("備註", systemImage: "note.text")
                .font(.system(size: 12, weight: .medium))
                .foregroundColor(.secondary)
            
            TextField("添加備註...", text: $notes, axis: .vertical)
                .font(.system(size: 14))
                .lineLimit(3...6)
                .padding(8)
                .background(Color.gray.opacity(0.1))
                .cornerRadius(8)
        }
    }
    
    private var favoriteToggle: some View {
        HStack {
            Label("設為收藏", systemImage: "star")
                .font(.system(size: 14, weight: .medium))
            
            Spacer()
            
            Toggle("", isOn: $isFavorite)
                .labelsHidden()
        }
        .padding(.vertical, 4)
    }
    
    private var actionButtons: some View {
        VStack(spacing: 12) {
            Button(action: saveContact) {
                HStack {
                    if isValidating {
                        ProgressView()
                            .scaleEffect(0.8)
                    } else {
                        Image(systemName: "checkmark.circle.fill")
                    }
                    Text("保存聯絡人")
                }
                .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .disabled(!isFormValid || isValidating)
            
            Button("取消") {
                dismiss()
            }
            .buttonStyle(.bordered)
        }
        .padding(.top, 8)
    }
    
    // MARK: - Validation
    
    private var isFormValid: Bool {
        !name.isEmpty && !address.isEmpty && nameError == nil && addressError == nil
    }
    
    private func validateName() {
        if name.isEmpty {
            nameError = "請輸入聯絡人名稱"
        } else if name.count < 2 {
            nameError = "名稱至少需要 2 個字符"
        } else {
            nameError = nil
        }
    }
    
    private func validateAddress() {
        if address.isEmpty {
            addressError = "請輸入錢包地址"
        } else if !isValidAddressFormat(address) {
            addressError = "無效的地址格式"
        } else {
            addressError = nil
        }
    }
    
    private func isValidAddressFormat(_ address: String) -> Bool {
        // 簡單的格式檢查
        if chainType == "Ethereum" || chainType == "BSC" || chainType == "Polygon" {
            return address.starts(with: "0x") && address.count == 42
        } else if chainType == "Bitcoin" {
            return address.count >= 26 && address.count <= 62
        } else if chainType == "Solana" {
            return address.count >= 32 && address.count <= 44
        }
        return !address.isEmpty
    }
    
    // MARK: - Actions
    
    private func pasteAddress() {
        // UIPasteboard not available on watchOS
        // if let pastedString = UIPasteboard.general.string {
        if let pastedString: String? = nil {
            address = pastedString ?? ""
        }
    }
    
    private func saveContact() {
        guard isFormValid else { return }
        
        isValidating = true
        
        // 解析標籤
        let tagList = tags.split(separator: ",").map { 
            $0.trimmingCharacters(in: .whitespaces) 
        }
        
        Task {
            await viewModel.createContact(
                name: name,
                address: address,
                chainType: chainType,
                tags: tagList,
                notes: notes,
                isFavorite: isFavorite
            )
            
            isValidating = false
            
            if viewModel.errorMessage == nil {
                dismiss()
            }
        }
    }
}

// MARK: - Chain Picker View

struct ChainPickerView: View {
    @Binding var selectedChain: String
    let chains: [String]
    @Environment(\.dismiss) private var dismiss
    
    var body: some View {
        NavigationStack {
            List(chains, id: \.self) { chain in
                Button(action: {
                    selectedChain = chain
                    dismiss()
                }) {
                    HStack {
                        Text(chain)
                            .font(.system(size: 14))
                        Spacer()
                        if chain == selectedChain {
                            Image(systemName: "checkmark")
                                .foregroundColor(.blue)
                        }
                    }
                }
                .buttonStyle(.plain)
            }
            .navigationTitle("選擇區塊鏈")
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
}

// MARK: - QR Code Scanner View (Placeholder)

struct QRCodeScannerView: View {
    let onScan: (String) -> Void
    @Environment(\.dismiss) private var dismiss
    
    var body: some View {
        NavigationStack {
            VStack(spacing: 20) {
                Image(systemName: "qrcode.viewfinder")
                    .font(.system(size: 60))
                    .foregroundColor(.blue)
                
                Text("QR Code 掃描器")
                    .font(.headline)
                
                Text("請使用配對的 iPhone 掃描")
                    .font(.caption)
                    .foregroundColor(.secondary)
                
                // 測試用：模擬掃描結果
                Button("模擬掃描") {
                    onScan("0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb2")
                }
                .buttonStyle(.borderedProminent)
            }
            .padding()
            .navigationTitle("掃描地址")
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

// MARK: - Preview

struct AddContactView_Previews: PreviewProvider {
    static var previews: some View {
        AddContactView(viewModel: AddressBookViewModel())
    }
}
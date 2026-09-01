//
//  EditContactView.swift
//  WatchWallet Watch App
//
//  編輯聯絡人視圖 - 用於編輯現有聯絡人資訊
//  Created: 2025-08-07
//

import SwiftUI

/**
 * 編輯聯絡人視圖
 * 
 * 提供編輯現有聯絡人資訊的界面
 */
struct EditContactView: View {
    let contact: AddressContact
    @ObservedObject var viewModel: AddressBookViewModel
    @Environment(\.dismiss) private var dismiss
    
    // Form State - 初始化為聯絡人當前值
    @State private var name: String
    @State private var primaryAddress: String
    @State private var chainType: String
    @State private var notes: String
    @State private var tags: String
    @State private var isFavorite: Bool
    @State private var additionalAddresses: [ChainAddress]
    
    @State private var showingAddAddress = false
    @State private var showingChainPicker = false
    @State private var isValidating = false
    @State private var showingDeleteConfirm = false
    
    // Validation State
    @State private var nameError: String?
    @State private var addressError: String?
    
    private let supportedChains = [
        "Ethereum", "BSC", "Polygon", "Arbitrum", "Optimism",
        "Avalanche", "Fantom", "Cronos", "Bitcoin", "Solana"
    ]
    
    init(contact: AddressContact, viewModel: AddressBookViewModel) {
        self.contact = contact
        self.viewModel = viewModel
        
        // 初始化狀態
        _name = State(initialValue: contact.name)
        _primaryAddress = State(initialValue: contact.primaryAddress)
        _chainType = State(initialValue: contact.chainType)
        _notes = State(initialValue: contact.notes)
        _tags = State(initialValue: contact.tags.joined(separator: ", "))
        _isFavorite = State(initialValue: contact.isFavorite)
        _additionalAddresses = State(initialValue: contact.additionalAddresses)
    }
    
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    // 名稱輸入
                    nameField
                    
                    // 主地址輸入
                    primaryAddressField
                    
                    // 區塊鏈選擇
                    chainSelector
                    
                    // 其他地址管理
                    additionalAddressesSection
                    
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
            .navigationTitle("編輯聯絡人")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") {
                        dismiss()
                    }
                }
            }
            .sheet(isPresented: $showingChainPicker) {
                ChainPickerView(
                    selectedChain: $chainType,
                    chains: supportedChains
                )
            }
            .sheet(isPresented: $showingAddAddress) {
                AddChainAddressView { chainAddress in
                    additionalAddresses.append(chainAddress)
                }
            }
            .alert("刪除地址", isPresented: $showingDeleteConfirm) {
                Button("取消", role: .cancel) { }
                Button("刪除", role: .destructive) { }
            } message: {
                Text("確定要刪除這個地址嗎？")
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
    
    private var primaryAddressField: some View {
        VStack(alignment: .leading, spacing: 6) {
            Label("主要錢包地址", systemImage: "wallet.pass")
                .font(.system(size: 12, weight: .medium))
                .foregroundColor(.secondary)
            
            TextField("輸入地址", text: $primaryAddress)
                .font(.system(size: 14))
                .padding(8)
                .background(Color.gray.opacity(0.1))
                .cornerRadius(8)
                .onChange(of: primaryAddress) { _ in
                    validateAddress()
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
    
    private var additionalAddressesSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Label("其他地址", systemImage: "wallet.pass.fill")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(.secondary)
                
                Spacer()
                
                Button(action: { showingAddAddress = true }) {
                    Image(systemName: "plus.circle")
                        .font(.system(size: 14))
                }
            }
            
            if !additionalAddresses.isEmpty {
                ForEach(additionalAddresses) { chainAddress in
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(chainAddress.chainType)
                                .font(.system(size: 11, weight: .medium))
                                .foregroundColor(.blue)
                            
                            Text(formatAddress(chainAddress.address))
                                .font(.system(size: 10, design: .monospaced))
                                .foregroundColor(.secondary)
                        }
                        
                        Spacer()
                        
                        Button(action: {
                            additionalAddresses.removeAll { $0.id == chainAddress.id }
                        }) {
                            Image(systemName: "minus.circle.fill")
                                .font(.system(size: 14))
                                .foregroundColor(.red)
                        }
                    }
                    .padding(8)
                    .background(Color.gray.opacity(0.1))
                    .cornerRadius(6)
                }
            } else {
                Text("暫無其他地址")
                    .font(.system(size: 11))
                    .foregroundColor(.secondary)
                    .padding(.vertical, 4)
            }
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
            Button(action: saveChanges) {
                HStack {
                    if isValidating {
                        ProgressView()
                            .scaleEffect(0.8)
                    } else {
                        Image(systemName: "checkmark.circle.fill")
                    }
                    Text("保存更改")
                }
                .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .disabled(!isFormValid || isValidating || !hasChanges)
            
            Button("取消") {
                dismiss()
            }
            .buttonStyle(.bordered)
        }
        .padding(.top, 8)
    }
    
    // MARK: - Validation
    
    private var isFormValid: Bool {
        !name.isEmpty && !primaryAddress.isEmpty && nameError == nil && addressError == nil
    }
    
    private var hasChanges: Bool {
        name != contact.name ||
        primaryAddress != contact.primaryAddress ||
        chainType != contact.chainType ||
        notes != contact.notes ||
        tags != contact.tags.joined(separator: ", ") ||
        isFavorite != contact.isFavorite ||
        additionalAddresses != contact.additionalAddresses
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
        if primaryAddress.isEmpty {
            addressError = "請輸入錢包地址"
        } else if !isValidAddressFormat(primaryAddress) {
            addressError = "無效的地址格式"
        } else {
            addressError = nil
        }
    }
    
    private func isValidAddressFormat(_ address: String) -> Bool {
        if chainType == "Ethereum" || chainType == "BSC" || chainType == "Polygon" {
            return address.starts(with: "0x") && address.count == 42
        } else if chainType == "Bitcoin" {
            return address.count >= 26 && address.count <= 62
        } else if chainType == "Solana" {
            return address.count >= 32 && address.count <= 44
        }
        return !address.isEmpty
    }
    
    private func formatAddress(_ address: String) -> String {
        guard address.count > 10 else { return address }
        let start = address.prefix(6)
        let end = address.suffix(4)
        return "\(start)...\(end)"
    }
    
    // MARK: - Actions
    
    private func saveChanges() {
        guard isFormValid && hasChanges else { return }
        
        isValidating = true
        
        // 更新聯絡人資料
        contact.name = name
        contact.primaryAddress = primaryAddress
        contact.chainType = chainType
        contact.notes = notes
        contact.isFavorite = isFavorite
        contact.additionalAddresses = additionalAddresses
        
        // 解析並更新標籤
        let tagList = tags.split(separator: ",").map {
            $0.trimmingCharacters(in: .whitespaces)
        }
        contact.tags = tagList
        
        Task {
            await viewModel.updateContact(contact)
            isValidating = false
            
            if viewModel.errorMessage == nil {
                dismiss()
            }
        }
    }
}

// MARK: - Add Chain Address View

struct AddChainAddressView: View {
    let onAdd: (ChainAddress) -> Void
    @Environment(\.dismiss) private var dismiss
    
    @State private var chainType = "Ethereum"
    @State private var address = ""
    @State private var showingChainPicker = false
    
    private let supportedChains = [
        "Ethereum", "BSC", "Polygon", "Arbitrum", "Optimism",
        "Avalanche", "Fantom", "Cronos", "Bitcoin", "Solana"
    ]
    
    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                // 區塊鏈選擇
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
                
                // 地址輸入
                VStack(alignment: .leading, spacing: 6) {
                    Label("錢包地址", systemImage: "wallet.pass")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundColor(.secondary)
                    
                    TextField("輸入地址", text: $address)
                        .font(.system(size: 14))
                        .padding(8)
                        .background(Color.gray.opacity(0.1))
                        .cornerRadius(8)
                }
                
                Spacer()
                
                // 操作按鈕
                VStack(spacing: 12) {
                    Button(action: {
                        let chainAddress = ChainAddress(
                            chainType: chainType,
                            address: address
                        )
                        onAdd(chainAddress)
                        dismiss()
                    }) {
                        Text("添加地址")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(address.isEmpty)
                    
                    Button("取消") {
                        dismiss()
                    }
                    .buttonStyle(.bordered)
                }
            }
            .padding()
            .navigationTitle("添加區塊鏈地址")
            .navigationBarTitleDisplayMode(.inline)
            .sheet(isPresented: $showingChainPicker) {
                ChainPickerView(
                    selectedChain: $chainType,
                    chains: supportedChains
                )
            }
        }
    }
}

// MARK: - Preview

struct EditContactView_Previews: PreviewProvider {
    static var previews: some View {
        let contact = AddressContact(
            name: "測試聯絡人",
            primaryAddress: "0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb2",
            chainType: "Ethereum",
            tags: ["朋友", "DeFi"],
            notes: "這是一個測試聯絡人",
            isFavorite: true
        )
        
        EditContactView(
            contact: contact,
            viewModel: AddressBookViewModel()
        )
    }
}
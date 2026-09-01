//
//  ContactDetailView.swift
//  WatchWallet Watch App
//
//  聯絡人詳情視圖 - 顯示和編輯聯絡人詳細資訊
//  Created: 2025-08-07
//

import SwiftUI

/**
 * 聯絡人詳情視圖
 * 
 * 顯示聯絡人的完整資訊，並提供編輯和操作選項
 */
struct ContactDetailView: View {
    let contact: AddressContact
    @ObservedObject var viewModel: AddressBookViewModel
    @Environment(\.dismiss) private var dismiss
    
    @State private var showingEditView = false
    @State private var showingDeleteConfirm = false
    @State private var showingQRCode = false
    @State private var copiedAddress = false
    @State private var selectedAddress: String?
    
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    // 頭像和基本資訊
                    contactHeader
                    
                    // 主要地址卡片
                    primaryAddressCard
                    
                    // 其他區塊鏈地址
                    if !contact.additionalAddresses.isEmpty {
                        additionalAddressesSection
                    }
                    
                    // 標籤
                    if !contact.tags.isEmpty {
                        tagsSection
                    }
                    
                    // 備註
                    if !contact.notes.isEmpty {
                        notesSection
                    }
                    
                    // 統計資訊
                    statisticsSection
                    
                    // 操作按鈕
                    actionButtons
                }
                .padding()
            }
            .navigationTitle("聯絡人詳情")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button(action: { showingEditView = true }) {
                        Image(systemName: "pencil")
                    }
                }
            }
            .sheet(isPresented: $showingEditView) {
                EditContactView(contact: contact, viewModel: viewModel)
            }
            .sheet(isPresented: $showingQRCode) {
                if let address = selectedAddress {
                    ContactQRCodeView(
                        title: contact.name,
                        content: address,
                        chainType: contact.chainType
                    )
                }
            }
            .alert("刪除聯絡人", isPresented: $showingDeleteConfirm) {
                Button("取消", role: .cancel) { }
                Button("刪除", role: .destructive) {
                    Task {
                        await viewModel.deleteContact(contact)
                        dismiss()
                    }
                }
            } message: {
                Text("確定要刪除 \(contact.name) 嗎？此操作無法復原。")
            }
            .alert("已複製", isPresented: $copiedAddress) {
                Button("確定") { }
            } message: {
                Text("地址已複製到剪貼板")
            }
        }
    }
    
    // MARK: - Header Section
    
    private var contactHeader: some View {
        VStack(spacing: 12) {
            // 頭像
            Circle()
                .fill(Color(hex: contact.avatarColor) ?? .blue)
                .frame(width: 60, height: 60)
                .overlay(
                    Text(contact.name.prefix(1).uppercased())
                        .font(.system(size: 24, weight: .bold))
                        .foregroundColor(.white)
                )
            
            // 名稱和狀態
            VStack(spacing: 4) {
                Text(contact.name)
                    .font(.system(size: 18, weight: .semibold))
                
                HStack(spacing: 8) {
                    if contact.isFavorite {
                        Label("收藏", systemImage: "star.fill")
                            .font(.system(size: 11))
                            .foregroundColor(.yellow)
                    }
                    
                    if contact.isVerified {
                        Label("已驗證", systemImage: "checkmark.seal.fill")
                            .font(.system(size: 11))
                            .foregroundColor(.green)
                    }
                    
                    Label(contact.source.rawValue, systemImage: contact.source.icon)
                        .font(.system(size: 11))
                        .foregroundColor(.secondary)
                }
            }
        }
    }
    
    // MARK: - Primary Address Card
    
    private var primaryAddressCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Label(contact.chainType, systemImage: "link.circle.fill")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(.blue)
                
                Spacer()
                
                Text("主地址")
                    .font(.system(size: 10))
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(Color.blue.opacity(0.2))
                    .cornerRadius(4)
            }
            
            Text(contact.primaryAddress)
                .font(.system(size: 11, design: .monospaced))
                .lineLimit(nil)
                .fixedSize(horizontal: false, vertical: true)
            
            HStack(spacing: 8) {
                Button(action: {
                    viewModel.copyAddress(contact.primaryAddress)
                    copiedAddress = true
                }) {
                    Label("複製", systemImage: "doc.on.doc")
                        .font(.system(size: 10))
                }
                .buttonStyle(.bordered)
                .controlSize(.mini)
                
                Button(action: {
                    selectedAddress = contact.primaryAddress
                    showingQRCode = true
                }) {
                    Label("QR", systemImage: "qrcode")
                        .font(.system(size: 10))
                }
                .buttonStyle(.bordered)
                .controlSize(.mini)
                
                Button(action: {
                    Task {
                        await viewModel.recordUsage(contact)
                    }
                }) {
                    Label("使用", systemImage: "arrow.right.circle")
                        .font(.system(size: 10))
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.mini)
            }
        }
        .padding()
        .background(Color.gray.opacity(0.1))
        .cornerRadius(10)
    }
    
    // MARK: - Additional Addresses Section
    
    private var additionalAddressesSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Label("其他地址", systemImage: "wallet.pass")
                .font(.system(size: 12, weight: .medium))
                .foregroundColor(.secondary)
            
            ForEach(contact.additionalAddresses) { chainAddress in
                VStack(alignment: .leading, spacing: 4) {
                    Text(chainAddress.chainType)
                        .font(.system(size: 11, weight: .medium))
                        .foregroundColor(.blue)
                    
                    Text(formatAddress(chainAddress.address))
                        .font(.system(size: 10, design: .monospaced))
                        .foregroundColor(.secondary)
                    
                    HStack(spacing: 8) {
                        Button(action: {
                            viewModel.copyAddress(chainAddress.address)
                            copiedAddress = true
                        }) {
                            Image(systemName: "doc.on.doc")
                                .font(.system(size: 10))
                        }
                        .buttonStyle(.bordered)
                        .controlSize(.mini)
                        
                        Button(action: {
                            selectedAddress = chainAddress.address
                            showingQRCode = true
                        }) {
                            Image(systemName: "qrcode")
                                .font(.system(size: 10))
                        }
                        .buttonStyle(.bordered)
                        .controlSize(.mini)
                    }
                }
                .padding(8)
                .background(Color.gray.opacity(0.1))
                .cornerRadius(8)
            }
        }
    }
    
    // MARK: - Tags Section
    
    private var tagsSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Label("標籤", systemImage: "tag")
                .font(.system(size: 12, weight: .medium))
                .foregroundColor(.secondary)
            
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(contact.tags, id: \.self) { tag in
                        Text(tag)
                            .font(.system(size: 11))
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(Color.blue.opacity(0.2))
                            .cornerRadius(6)
                    }
                }
            }
        }
    }
    
    // MARK: - Notes Section
    
    private var notesSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Label("備註", systemImage: "note.text")
                .font(.system(size: 12, weight: .medium))
                .foregroundColor(.secondary)
            
            Text(contact.notes)
                .font(.system(size: 12))
                .padding(8)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color.gray.opacity(0.1))
                .cornerRadius(8)
        }
    }
    
    // MARK: - Statistics Section
    
    private var statisticsSection: some View {
        VStack(spacing: 8) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("使用次數")
                        .font(.system(size: 10))
                        .foregroundColor(.secondary)
                    Text("\(contact.useCount)")
                        .font(.system(size: 14, weight: .semibold))
                }
                
                Spacer()
                
                VStack(alignment: .trailing, spacing: 2) {
                    Text("最後使用")
                        .font(.system(size: 10))
                        .foregroundColor(.secondary)
                    Text(lastUsedText)
                        .font(.system(size: 14, weight: .semibold))
                }
            }
            
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("創建時間")
                        .font(.system(size: 10))
                        .foregroundColor(.secondary)
                    Text(formatDate(contact.createdDate))
                        .font(.system(size: 11))
                }
                
                Spacer()
                
                VStack(alignment: .trailing, spacing: 2) {
                    Text("更新時間")
                        .font(.system(size: 10))
                        .foregroundColor(.secondary)
                    Text(formatDate(contact.lastModifiedDate))
                        .font(.system(size: 11))
                }
            }
        }
        .padding()
        .background(Color.gray.opacity(0.1))
        .cornerRadius(10)
    }
    
    // MARK: - Action Buttons
    
    private var actionButtons: some View {
        VStack(spacing: 12) {
            Button(action: {
                Task {
                    await viewModel.toggleFavorite(contact)
                }
            }) {
                HStack {
                    Image(systemName: contact.isFavorite ? "star.fill" : "star")
                    Text(contact.isFavorite ? "取消收藏" : "加入收藏")
                }
                .frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)
            .tint(.yellow)
            
            if !contact.isVerified {
                Button(action: {
                    Task {
                        await viewModel.verifyContact(contact)
                    }
                }) {
                    HStack {
                        Image(systemName: "checkmark.seal")
                        Text("驗證地址")
                    }
                    .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                .tint(.green)
            }
            
            Button(action: { showingDeleteConfirm = true }) {
                HStack {
                    Image(systemName: "trash")
                    Text("刪除聯絡人")
                }
                .frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)
            .tint(.red)
        }
        .padding(.top, 8)
    }
    
    // MARK: - Helper Methods
    
    private func formatAddress(_ address: String) -> String {
        guard address.count > 10 else { return address }
        let start = address.prefix(6)
        let end = address.suffix(4)
        return "\(start)...\(end)"
    }
    
    private func formatDate(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateStyle = .short
        formatter.timeStyle = .short
        return formatter.string(from: date)
    }
    
    private var lastUsedText: String {
        if let date = contact.lastUsedDate {
            return formatDate(date)
        } else {
            return "從未使用"
        }
    }
}

// MARK: - QR Code Display View

struct ContactQRCodeView: View {
    let title: String
    let content: String
    let chainType: String
    @Environment(\.dismiss) private var dismiss
    
    var body: some View {
        NavigationStack {
            VStack(spacing: 20) {
                Text(title)
                    .font(.system(size: 16, weight: .semibold))
                
                // QR Code 占位符
                RoundedRectangle(cornerRadius: 12)
                    .fill(Color.white)
                    .frame(width: 150, height: 150)
                    .overlay(
                        VStack {
                            Image(systemName: "qrcode")
                                .font(.system(size: 50))
                                .foregroundColor(.black)
                            Text("QR Code")
                                .font(.system(size: 12))
                                .foregroundColor(.black)
                        }
                    )
                
                VStack(spacing: 4) {
                    Text(chainType)
                        .font(.system(size: 12))
                        .foregroundColor(.secondary)
                    
                    Text(content)
                        .font(.system(size: 10, design: .monospaced))
                        .lineLimit(3)
                        .multilineTextAlignment(.center)
                }
                
                Button("關閉") {
                    dismiss()
                }
                .buttonStyle(.borderedProminent)
            }
            .padding()
            .navigationTitle("QR Code")
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}

// MARK: - Preview

struct ContactDetailView_Previews: PreviewProvider {
    static var previews: some View {
        let contact = AddressContact(
            name: "測試聯絡人",
            primaryAddress: "0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb2",
            chainType: "Ethereum",
            tags: ["朋友", "DeFi"],
            notes: "這是一個測試聯絡人",
            isFavorite: true
        )
        
        ContactDetailView(
            contact: contact,
            viewModel: AddressBookViewModel()
        )
    }
}
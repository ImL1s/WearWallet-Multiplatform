//
//  AddressBookView.swift
//  WatchWallet Watch App
//
//  地址簿主視圖 - 顯示和管理聯絡人列表
//  Created: 2025-08-07
//

import SwiftUI
import SwiftData

/**
 * 地址簿主視圖
 * 
 * 提供完整的地址簿功能，包括：
 * - 聯絡人列表顯示
 * - 搜索和篩選
 * - 添加、編輯、刪除操作
 * - 收藏和分組顯示
 */
struct AddressBookView: View {
    @StateObject private var viewModel = AddressBookViewModel()
    @State private var selectedTab: AddressBookTab = .all
    @State private var showingAddContact = false
    @State private var selectedContact: AddressContact?
    @State private var showingContactDetail = false
    @State private var showingSettings = false
    
    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // 標籤選擇器
                tabSelector
                
                // 搜索欄
                if selectedTab == .all || selectedTab == .search {
                    searchBar
                }
                
                // 聯絡人列表
                contactsList
            }
            .navigationTitle("地址簿")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(action: { showingAddContact = true }) {
                        Image(systemName: "plus")
                    }
                }
                
                ToolbarItem(placement: .cancellationAction) {
                    Button(action: { showingSettings = true }) {
                        Image(systemName: "ellipsis.circle")
                    }
                }
            }
            .sheet(isPresented: $showingAddContact) {
                AddContactView(viewModel: viewModel)
            }
            .sheet(item: $selectedContact) { contact in
                ContactDetailView(contact: contact, viewModel: viewModel)
            }
            .sheet(isPresented: $showingSettings) {
                AddressBookSettingsView(viewModel: viewModel)
            }
            .alert("錯誤", isPresented: .constant(viewModel.errorMessage != nil)) {
                Button("確定") {
                    viewModel.errorMessage = nil
                }
            } message: {
                if let error = viewModel.errorMessage {
                    Text(error)
                }
            }
            .alert("成功", isPresented: .constant(viewModel.successMessage != nil)) {
                Button("確定") {
                    viewModel.successMessage = nil
                }
            } message: {
                if let success = viewModel.successMessage {
                    Text(success)
                }
            }
        }
    }
    
    // MARK: - Tab Selector
    
    private var tabSelector: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(AddressBookTab.allCases, id: \.self) { tab in
                    TabButton(
                        title: tab.title,
                        icon: tab.icon,
                        isSelected: selectedTab == tab,
                        count: getCountForTab(tab)
                    ) {
                        withAnimation(.easeInOut(duration: 0.2)) {
                            selectedTab = tab
                        }
                    }
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
        }
    }
    
    // MARK: - Search Bar
    
    private var searchBar: some View {
        HStack {
            Image(systemName: "magnifyingglass")
                .foregroundColor(.secondary)
                .font(.system(size: 14))
            
            TextField("搜索聯絡人...", text: $viewModel.searchText)
                .font(.system(size: 14))
                .textFieldStyle(.plain)
            
            if !viewModel.searchText.isEmpty {
                Button(action: { viewModel.searchText = "" }) {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundColor(.secondary)
                        .font(.system(size: 12))
                }
            }
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .background(Color.gray.opacity(0.1))
        .cornerRadius(8)
        .padding(.horizontal, 12)
        .padding(.bottom, 8)
    }
    
    // MARK: - Contacts List
    
    private var contactsList: some View {
        ScrollView {
            if viewModel.isLoading {
                ProgressView()
                    .padding()
            } else {
                LazyVStack(spacing: 8) {
                    ForEach(getContactsForTab()) { contact in
                        ContactRow(contact: contact) {
                            selectedContact = contact
                        }
                        .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                            Button(role: .destructive) {
                                Task {
                                    await viewModel.deleteContact(contact)
                                }
                            } label: {
                                Label("刪除", systemImage: "trash")
                            }
                            
                            Button {
                                Task {
                                    await viewModel.toggleFavorite(contact)
                                }
                            } label: {
                                Label(
                                    contact.isFavorite ? "取消收藏" : "收藏",
                                    systemImage: contact.isFavorite ? "star.fill" : "star"
                                )
                            }
                            .tint(.yellow)
                        }
                    }
                    
                    if getContactsForTab().isEmpty {
                        emptyStateView
                    }
                }
                .padding(.horizontal, 12)
            }
        }
    }
    
    // MARK: - Empty State
    
    private var emptyStateView: some View {
        VStack(spacing: 12) {
            Image(systemName: selectedTab.emptyIcon)
                .font(.system(size: 40))
                .foregroundColor(.secondary)
            
            Text(selectedTab.emptyMessage)
                .font(.system(size: 14))
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
            
            if selectedTab == .all {
                Button("添加第一個聯絡人") {
                    showingAddContact = true
                }
                .font(.system(size: 12))
                .buttonStyle(.borderedProminent)
            }
        }
        .padding(.vertical, 40)
    }
    
    // MARK: - Helper Methods
    
    private func getContactsForTab() -> [AddressContact] {
        switch selectedTab {
        case .all:
            return viewModel.displayedContacts
        case .favorites:
            return viewModel.favoriteContacts
        case .recent:
            return viewModel.recentContacts
        case .frequent:
            return viewModel.frequentContacts
        case .search:
            return viewModel.displayedContacts
        }
    }
    
    private func getCountForTab(_ tab: AddressBookTab) -> Int? {
        switch tab {
        case .all:
            return viewModel.contacts.count
        case .favorites:
            return viewModel.favoriteContacts.count
        case .recent:
            return viewModel.recentContacts.count
        case .frequent:
            return viewModel.frequentContacts.count
        case .search:
            return nil
        }
    }
}

// MARK: - Tab Button Component

struct TabButton: View {
    let title: String
    let icon: String
    let isSelected: Bool
    let count: Int?
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            HStack(spacing: 4) {
                Image(systemName: icon)
                    .font(.system(size: 12))
                
                Text(title)
                    .font(.system(size: 12, weight: .medium))
                
                if let count = count {
                    Text("\(count)")
                        .font(.system(size: 10))
                        .padding(.horizontal, 4)
                        .padding(.vertical, 1)
                        .background(
                            isSelected ? Color.white.opacity(0.2) : Color.secondary.opacity(0.2)
                        )
                        .cornerRadius(4)
                }
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(
                isSelected ? Color.blue : Color.clear
            )
            .foregroundColor(isSelected ? .white : .primary)
            .cornerRadius(8)
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Contact Row Component

struct ContactRow: View {
    let contact: AddressContact
    let onTap: () -> Void
    
    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 10) {
                // 頭像
                Circle()
                    .fill(Color(hex: contact.avatarColor) ?? .blue)
                    .frame(width: 36, height: 36)
                    .overlay(
                        Text(contact.name.prefix(1).uppercased())
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundColor(.white)
                    )
                
                // 聯絡人資訊
                VStack(alignment: .leading, spacing: 2) {
                    HStack {
                        Text(contact.name)
                            .font(.system(size: 14, weight: .medium))
                            .lineLimit(1)
                        
                        if contact.isFavorite {
                            Image(systemName: "star.fill")
                                .font(.system(size: 10))
                                .foregroundColor(.yellow)
                        }
                        
                        if contact.isVerified {
                            Image(systemName: "checkmark.seal.fill")
                                .font(.system(size: 10))
                                .foregroundColor(.green)
                        }
                    }
                    
                    Text(formatAddress(contact.primaryAddress))
                        .font(.system(size: 11))
                        .foregroundColor(.secondary)
                        .lineLimit(1)
                    
                    HStack(spacing: 4) {
                        // 區塊鏈類型
                        Label(contact.chainType, systemImage: "link.circle.fill")
                            .font(.system(size: 9))
                            .foregroundColor(.blue)
                        
                        // 使用次數
                        if contact.useCount > 0 {
                            Label("\(contact.useCount)", systemImage: "arrow.left.arrow.right")
                                .font(.system(size: 9))
                                .foregroundColor(.secondary)
                        }
                    }
                }
                
                Spacer()
                
                // 箭頭
                Image(systemName: "chevron.right")
                    .font(.system(size: 12))
                    .foregroundColor(.secondary)
            }
            .padding(.vertical, 8)
            .padding(.horizontal, 12)
            .background(Color.gray.opacity(0.1))
            .cornerRadius(10)
        }
        .buttonStyle(.plain)
    }
    
    private func formatAddress(_ address: String) -> String {
        guard address.count > 10 else { return address }
        let start = address.prefix(6)
        let end = address.suffix(4)
        return "\(start)...\(end)"
    }
}

// MARK: - Supporting Types

enum AddressBookTab: String, CaseIterable {
    case all = "全部"
    case favorites = "收藏"
    case recent = "最近"
    case frequent = "常用"
    case search = "搜索"
    
    var title: String {
        return self.rawValue
    }
    
    var icon: String {
        switch self {
        case .all: return "person.2"
        case .favorites: return "star"
        case .recent: return "clock"
        case .frequent: return "arrow.up.arrow.down"
        case .search: return "magnifyingglass"
        }
    }
    
    var emptyIcon: String {
        switch self {
        case .all: return "person.crop.circle.badge.plus"
        case .favorites: return "star.slash"
        case .recent: return "clock.badge.xmark"
        case .frequent: return "arrow.up.arrow.down.circle"
        case .search: return "magnifyingglass"
        }
    }
    
    var emptyMessage: String {
        switch self {
        case .all: return "還沒有聯絡人\n點擊添加第一個"
        case .favorites: return "沒有收藏的聯絡人"
        case .recent: return "沒有最近使用的聯絡人"
        case .frequent: return "沒有常用聯絡人"
        case .search: return "沒有找到匹配的聯絡人"
        }
    }
}

// MARK: - Color Extension

extension Color {
    init?(hex: String) {
        var hexSanitized = hex.trimmingCharacters(in: .whitespacesAndNewlines)
        hexSanitized = hexSanitized.replacingOccurrences(of: "#", with: "")
        
        var rgb: UInt64 = 0
        
        guard Scanner(string: hexSanitized).scanHexInt64(&rgb) else { return nil }
        
        let r = Double((rgb & 0xFF0000) >> 16) / 255.0
        let g = Double((rgb & 0x00FF00) >> 8) / 255.0
        let b = Double(rgb & 0x0000FF) / 255.0
        
        self.init(red: r, green: g, blue: b)
    }
}

// MARK: - Preview

struct AddressBookView_Previews: PreviewProvider {
    static var previews: some View {
        NavigationStack {
            AddressBookView()
        }
    }
}
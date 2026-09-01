//
//  AddressBookViewModel2.swift
//  WatchWallet Watch App
//
//  地址簿視圖模型 - 使用 KMP Bridge 版本
//  Created: 2025-08-07
//

import Foundation
import SwiftUI
import Combine

/**
 * 地址簿視圖模型（KMP 版本）
 * 
 * 使用 KMP AddressBook UseCase 來管理地址簿業務邏輯
 */
@MainActor
class AddressBookViewModel2: ObservableObject {
    
    // MARK: - Published Properties
    
    /// 所有聯絡人
    @Published private(set) var contacts: [SwiftAddressContact] = []
    
    /// 顯示的聯絡人（經過搜索和篩選）
    @Published private(set) var displayedContacts: [SwiftAddressContact] = []
    
    /// 收藏聯絡人
    @Published private(set) var favoriteContacts: [SwiftAddressContact] = []
    
    /// 最近使用的聯絡人
    @Published private(set) var recentContacts: [SwiftAddressContact] = []
    
    /// 常用聯絡人
    @Published private(set) var frequentContacts: [SwiftAddressContact] = []
    
    /// 搜索文字
    @Published var searchText: String = "" {
        didSet {
            Task {
                await performSearch()
            }
        }
    }
    
    /// 選中的區塊鏈類型篩選
    @Published var selectedChainFilter: String? = nil {
        didSet {
            Task {
                await applyFilters()
            }
        }
    }
    
    /// 選中的標籤篩選
    @Published var selectedTagFilter: String? = nil {
        didSet {
            Task {
                await applyFilters()
            }
        }
    }
    
    /// 排序方式
    @Published var sortOption: SortOption = .name {
        didSet {
            applySorting()
        }
    }
    
    /// 是否正在加載
    @Published private(set) var isLoading: Bool = false
    
    /// 錯誤訊息
    @Published var errorMessage: String?
    
    /// 成功訊息
    @Published var successMessage: String?
    
    /// 是否顯示添加聯絡人表單
    @Published var showingAddContact: Bool = false
    
    /// 是否顯示編輯聯絡人表單
    @Published var showingEditContact: Bool = false
    
    /// 當前編輯的聯絡人
    @Published var editingContact: SwiftAddressContact?
    
    /// 是否顯示導入視圖
    @Published var showingImport: Bool = false
    
    /// 是否顯示導出視圖
    @Published var showingExport: Bool = false
    
    // MARK: - Properties
    
    /// KMP 橋接器
    private let kmpBridge = KMPAddressBookBridge.shared
    
    /// 取消訂閱集合
    private var cancellables = Set<AnyCancellable>()
    
    /// 所有可用的標籤
    var allTags: [String] {
        let tags = contacts.flatMap { $0.tags }
        return Array(Set(tags)).sorted()
    }
    
    /// 所有可用的區塊鏈類型
    var allChainTypes: [String] {
        let chains = Set(contacts.map { $0.chainTypeRaw })
        return Array(chains).sorted()
    }
    
    // MARK: - Initialization
    
    init() {
        // 觀察 KMP Bridge 的變化
        kmpBridge.$contacts
            .sink { [weak self] (contacts: [SwiftAddressContact]) in
                self?.contacts = contacts
                self?.applyLocalFilters()
            }
            .store(in: &cancellables)
        
        kmpBridge.$isLoading
            .assign(to: &$isLoading)
        
        kmpBridge.$error
            .compactMap { $0 }
            .assign(to: &$errorMessage)
        
        Task {
            await loadContacts()
        }
    }
    
    // MARK: - CRUD Operations
    
    /// 載入所有聯絡人
    func loadContacts() async {
        await kmpBridge.loadContacts()
        applyLocalFilters()
    }
    
    /// 創建新聯絡人
    func createContact(
        name: String,
        address: String,
        chainType: String,
        tags: [String] = [],
        notes: String = "",
        isFavorite: Bool = false
    ) async {
        // 驗證輸入
        guard !name.isEmpty else {
            errorMessage = "請輸入聯絡人名稱"
            return
        }
        
        guard !address.isEmpty else {
            errorMessage = "請輸入錢包地址"
            return
        }
        
        // 創建聯絡人
        if let newContact = await kmpBridge.createContact(
            name: name,
            address: address,
            chainType: chainType,
            tags: tags,
            notes: notes,
            isFavorite: isFavorite
        ) {
            successMessage = "成功添加聯絡人"
            showingAddContact = false
        }
    }
    
    /// 更新聯絡人
    func updateContact(_ contact: SwiftAddressContact) async {
        if await kmpBridge.updateContact(contact) {
            successMessage = "成功更新聯絡人"
            showingEditContact = false
            editingContact = nil
        }
    }
    
    /// 刪除聯絡人
    func deleteContact(_ contact: SwiftAddressContact) async {
        if await kmpBridge.deleteContact(contact.id) {
            successMessage = "成功刪除聯絡人"
        }
    }
    
    /// 批量刪除聯絡人
    func deleteContacts(_ contacts: [SwiftAddressContact]) async {
        var deletedCount = 0
        for contact in contacts {
            if await kmpBridge.deleteContact(contact.id) {
                deletedCount += 1
            }
        }
        successMessage = "成功刪除 \(deletedCount) 個聯絡人"
    }
    
    // MARK: - Contact Actions
    
    /// 切換收藏狀態
    func toggleFavorite(_ contact: SwiftAddressContact) async {
        if await kmpBridge.toggleFavorite(contact.id) {
            // 狀態會自動更新
        }
    }
    
    /// 記錄使用
    func recordUsage(_ contact: SwiftAddressContact) async {
        _ = await kmpBridge.recordUsage(contact.id)
    }
    
    /// 驗證聯絡人地址
    func verifyContact(_ contact: SwiftAddressContact) async {
        if await kmpBridge.verifyAddress(contact.primaryAddress, chainType: contact.chainType) {
            // 更新聯絡人的驗證狀態
            var updatedContact = contact
            updatedContact.isVerified = true
            await updateContact(updatedContact)
            successMessage = "地址驗證成功"
        } else {
            errorMessage = "地址驗證失敗"
        }
    }
    
    // MARK: - Search and Filter
    
    /// 執行搜索
    private func performSearch() async {
        if searchText.isEmpty {
            await applyFilters()
        } else {
            let searchResults = await kmpBridge.searchContacts(searchText)
            displayedContacts = searchResults
            applySorting()
        }
    }
    
    /// 應用篩選
    private func applyFilters() async {
        applyLocalFilters()
    }
    
    /// 本地篩選邏輯
    private func applyLocalFilters() {
        var filteredContacts = contacts
        
        // 應用區塊鏈類型篩選
        if let chainFilter = selectedChainFilter {
            filteredContacts = filteredContacts.filter { $0.chainTypeRaw == chainFilter }
        }
        
        // 應用標籤篩選
        if let tagFilter = selectedTagFilter {
            filteredContacts = filteredContacts.filter { $0.tags.contains(tagFilter) }
        }
        
        // 應用搜索
        if !searchText.isEmpty {
            filteredContacts = filteredContacts.filter { contact in
                contact.name.localizedCaseInsensitiveContains(searchText) ||
                contact.primaryAddress.localizedCaseInsensitiveContains(searchText) ||
                contact.chainTypeRaw.localizedCaseInsensitiveContains(searchText) ||
                contact.tags.contains { $0.localizedCaseInsensitiveContains(searchText) }
            }
        }
        
        displayedContacts = filteredContacts
        
        // 更新分類列表
        favoriteContacts = contacts.filter { $0.isFavorite }
        recentContacts = contacts
            .filter { $0.lastUsed != nil }
            .sorted { $0.lastUsed! > $1.lastUsed! }
            .prefix(5)
            .map { $0 }
        frequentContacts = contacts
            .filter { $0.useCount > 0 }
            .sorted { $0.useCount > $1.useCount }
            .prefix(5)
            .map { $0 }
        
        applySorting()
    }
    
    /// 應用排序
    private func applySorting() {
        switch sortOption {
        case .name:
            displayedContacts.sort { $0.name < $1.name }
        case .recent:
            displayedContacts.sort {
                guard let date1 = $0.lastUsed else { return false }
                guard let date2 = $1.lastUsed else { return true }
                return date1 > date2
            }
        case .frequent:
            displayedContacts.sort { $0.useCount > $1.useCount }
        case .favorite:
            displayedContacts.sort { lhs, rhs in
                if lhs.isFavorite != rhs.isFavorite {
                    return lhs.isFavorite
                }
                return lhs.name < rhs.name
            }
        }
    }
    
    /// 清除篩選
    func clearFilters() {
        searchText = ""
        selectedChainFilter = nil
        selectedTagFilter = nil
        sortOption = .name
    }
    
    // MARK: - Import/Export
    
    /// 導入聯絡人
    func importContacts(from data: Data) async {
        guard let jsonString = String(data: data, encoding: .utf8) else {
            errorMessage = "無法讀取導入檔案"
            return
        }
        
        if let count = await kmpBridge.importContacts(jsonString) {
            successMessage = "成功導入 \(count) 個聯絡人"
            showingImport = false
        }
    }
    
    /// 導出聯絡人
    func exportContacts() async -> Data? {
        if let jsonString = await kmpBridge.exportContacts() {
            successMessage = "成功導出 \(contacts.count) 個聯絡人"
            return jsonString.data(using: String.Encoding.utf8)
        }
        return nil
    }
    
    /// 清除所有聯絡人
    func clearAllContacts() async {
        // 這個功能需要在 KMP Bridge 中實現
        errorMessage = "清除功能尚未實現"
    }
    
    // MARK: - Helper Methods
    
    /// 複製地址到剪貼板
    func copyAddress(_ address: String) {
        // UIPasteboard.general.string = address
        print("Copy to clipboard not supported on watchOS directly: \(address)")
        successMessage = "地址已複製 (Mock)"
    }
    
    /// 從剪貼板貼上地址
    func pasteAddress() -> String? {
        // return UIPasteboard.general.string
        return nil
    }
    
    /// 生成測試資料
    func generateMockData() async {
        // 這個功能需要在 KMP 層實現
        errorMessage = "測試資料功能尚未實現"
    }
    
    /// 獲取統計資訊
    func getStatistics() async -> SwiftAddressBookStatistics? {
        return await kmpBridge.getStatistics()
    }
}
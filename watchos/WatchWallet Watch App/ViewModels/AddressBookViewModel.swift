//
//  AddressBookViewModel.swift
//  WatchWallet Watch App
//
//  地址簿視圖模型 - 管理地址簿的業務邏輯和狀態
//  Created: 2025-08-07
//

import Foundation
import SwiftUI
import Combine

/**
 * 地址簿視圖模型
 * 
 * 負責管理地址簿的所有業務邏輯，包括：
 * - CRUD 操作
 * - 搜索和篩選
 * - 狀態管理
 * - 資料驗證
 */
@MainActor
class AddressBookViewModel: ObservableObject {
    
    // MARK: - Published Properties
    
    /// 所有聯絡人
    @Published private(set) var contacts: [AddressContact] = []
    
    /// 顯示的聯絡人（經過搜索和篩選）
    @Published private(set) var displayedContacts: [AddressContact] = []
    
    /// 收藏聯絡人
    @Published private(set) var favoriteContacts: [AddressContact] = []
    
    /// 最近使用的聯絡人
    @Published private(set) var recentContacts: [AddressContact] = []
    
    /// 常用聯絡人
    @Published private(set) var frequentContacts: [AddressContact] = []
    
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
            Task {
                await applySorting()
            }
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
    @Published var editingContact: AddressContact?
    
    /// 是否顯示導入視圖
    @Published var showingImport: Bool = false
    
    /// 是否顯示導出視圖
    @Published var showingExport: Bool = false
    
    // MARK: - Properties
    
    /// 地址簿儲存庫
    private let repository: AddressBookRepositoryProtocol
    
    /// 取消訂閱集合
    private var cancellables = Set<AnyCancellable>()
    
    /// 所有可用的標籤
    var allTags: [String] {
        let tags = contacts.flatMap { $0.tags }
        return Array(Set(tags)).sorted()
    }
    
    /// 所有可用的區塊鏈類型
    var allChainTypes: [String] {
        let chains = Set(contacts.map { $0.chainType })
        return Array(chains).sorted()
    }
    
    // MARK: - Initialization
    
    init(repository: AddressBookRepositoryProtocol = AddressBookRepository.shared) {
        self.repository = repository
        
        Task {
            await loadContacts()
        }
    }
    
    // MARK: - CRUD Operations
    
    /// 載入所有聯絡人
    func loadContacts() async {
        isLoading = true
        errorMessage = nil
        
        do {
            contacts = try await repository.getAllContacts()
            favoriteContacts = try await repository.getFavoriteContacts()
            recentContacts = try await repository.getRecentContacts(limit: 5)
            frequentContacts = try await repository.getFrequentContacts(limit: 5)
            
            await applyFilters()
            
        } catch {
            errorMessage = "載入聯絡人失敗: \(error.localizedDescription)"
            print("[AddressBookViewModel] Failed to load contacts: \(error)")
        }
        
        isLoading = false
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
        
        // 驗證地址格式
        do {
            let isValid = try await repository.validateAddress(address, chainType: chainType)
            guard isValid else {
                errorMessage = "無效的錢包地址格式"
                return
            }
        } catch {
            errorMessage = "地址驗證失敗: \(error.localizedDescription)"
            return
        }
        
        // 檢查地址是否已存在
        if contacts.contains(where: { 
            $0.primaryAddress.lowercased() == address.lowercased() && 
            $0.chainType == chainType 
        }) {
            errorMessage = "該地址已存在於聯絡人中"
            return
        }
        
        // 創建新聯絡人
        let newContact = AddressContact(
            name: name,
            primaryAddress: address,
            chainType: chainType,
            tags: tags,
            notes: notes,
            isFavorite: isFavorite
        )
        
        do {
            try await repository.createContact(newContact)
            await loadContacts()
            
            successMessage = "成功添加聯絡人"
            showingAddContact = false
            
        } catch {
            errorMessage = "添加聯絡人失敗: \(error.localizedDescription)"
        }
    }
    
    /// 更新聯絡人
    func updateContact(_ contact: AddressContact) async {
        do {
            try await repository.updateContact(contact)
            await loadContacts()
            
            successMessage = "成功更新聯絡人"
            showingEditContact = false
            editingContact = nil
            
        } catch {
            errorMessage = "更新聯絡人失敗: \(error.localizedDescription)"
        }
    }
    
    /// 刪除聯絡人
    func deleteContact(_ contact: AddressContact) async {
        do {
            try await repository.deleteContact(contact)
            await loadContacts()
            
            successMessage = "成功刪除聯絡人"
            
        } catch {
            errorMessage = "刪除聯絡人失敗: \(error.localizedDescription)"
        }
    }
    
    /// 批量刪除聯絡人
    func deleteContacts(_ contacts: [AddressContact]) async {
        isLoading = true
        
        var deletedCount = 0
        for contact in contacts {
            do {
                try await repository.deleteContact(contact)
                deletedCount += 1
            } catch {
                print("[AddressBookViewModel] Failed to delete contact: \(error)")
            }
        }
        
        await loadContacts()
        successMessage = "成功刪除 \(deletedCount) 個聯絡人"
        
        isLoading = false
    }
    
    // MARK: - Contact Actions
    
    /// 切換收藏狀態
    func toggleFavorite(_ contact: AddressContact) async {
        contact.toggleFavorite()
        await updateContact(contact)
    }
    
    /// 記錄使用
    func recordUsage(_ contact: AddressContact) async {
        contact.recordUsage()
        await updateContact(contact)
    }
    
    /// 添加標籤
    func addTag(_ tag: String, to contact: AddressContact) async {
        contact.addTag(tag)
        await updateContact(contact)
    }
    
    /// 移除標籤
    func removeTag(_ tag: String, from contact: AddressContact) async {
        contact.removeTag(tag)
        await updateContact(contact)
    }
    
    /// 添加額外的區塊鏈地址
    func addChainAddress(_ address: ChainAddress, to contact: AddressContact) async {
        contact.addChainAddress(address)
        await updateContact(contact)
    }
    
    /// 驗證聯絡人地址
    func verifyContact(_ contact: AddressContact) async {
        do {
            let isValid = try await repository.validateAddress(
                contact.primaryAddress,
                chainType: contact.chainType
            )
            
            if isValid {
                contact.verifyAddress()
                await updateContact(contact)
                successMessage = "地址驗證成功"
            } else {
                errorMessage = "地址驗證失敗"
            }
        } catch {
            errorMessage = "驗證失敗: \(error.localizedDescription)"
        }
    }
    
    // MARK: - Search and Filter
    
    /// 執行搜索
    private func performSearch() async {
        if searchText.isEmpty {
            await applyFilters()
        } else {
            do {
                let searchResults = try await repository.searchContacts(query: searchText)
                displayedContacts = searchResults
                await applySorting()
            } catch {
                print("[AddressBookViewModel] Search failed: \(error)")
            }
        }
    }
    
    /// 應用篩選
    private func applyFilters() async {
        var filteredContacts = contacts
        
        // 應用區塊鏈類型篩選
        if let chainFilter = selectedChainFilter {
            filteredContacts = filteredContacts.filter { $0.chainType == chainFilter }
        }
        
        // 應用標籤篩選
        if let tagFilter = selectedTagFilter {
            filteredContacts = filteredContacts.filter { $0.tags.contains(tagFilter) }
        }
        
        // 應用搜索
        if !searchText.isEmpty {
            filteredContacts = filteredContacts.filter { $0.matches(searchText: searchText) }
        }
        
        displayedContacts = filteredContacts
        await applySorting()
    }
    
    /// 應用排序
    private func applySorting() async {
        switch sortOption {
        case .name:
            displayedContacts.sort { $0.name < $1.name }
        case .recent:
            displayedContacts.sort {
                guard let date1 = $0.lastUsedDate else { return false }
                guard let date2 = $1.lastUsedDate else { return true }
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
        do {
            let decoder = JSONDecoder()
            let exportableContacts = try decoder.decode([ExportableContact].self, from: data)
            
            // 轉換為 AddressContact
            let contacts = exportableContacts.map { exportable in
                AddressContact(
                    name: exportable.name,
                    primaryAddress: exportable.primaryAddress,
                    chainType: exportable.chainType,
                    tags: exportable.tags,
                    notes: exportable.notes,
                    isFavorite: exportable.isFavorite,
                    additionalAddresses: exportable.additionalAddresses,
                    source: .imported
                )
            }
            
            try await repository.importContacts(contacts)
            await loadContacts()
            
            successMessage = "成功導入 \(contacts.count) 個聯絡人"
            showingImport = false
            
        } catch {
            errorMessage = "導入失敗: \(error.localizedDescription)"
        }
    }
    
    /// 導出聯絡人
    func exportContacts() async -> Data? {
        do {
            let data = try await repository.exportContacts()
            successMessage = "成功導出 \(contacts.count) 個聯絡人"
            return data
        } catch {
            errorMessage = "導出失敗: \(error.localizedDescription)"
            return nil
        }
    }
    
    /// 清除所有聯絡人
    func clearAllContacts() async {
        do {
            try await repository.clearAllContacts()
            await loadContacts()
            
            successMessage = "已清除所有聯絡人"
            
        } catch {
            errorMessage = "清除失敗: \(error.localizedDescription)"
        }
    }
    
    // MARK: - Helper Methods
    
    /// 複製地址到剪貼板
    func copyAddress(_ address: String) {
        // UIPasteboard.general.string = address
        print("[Watch] Copy address: \(address)")
        successMessage = "地址已複製"
    }
    
    /// 從剪貼板貼上地址
    func pasteAddress() -> String? {
        // return UIPasteboard.general.string
        return nil
    }
    
    /// 生成測試資料
    func generateMockData() async {
        if let repo = repository as? AddressBookRepository {
            await repo.createMockData()
            await loadContacts()
            successMessage = "已生成測試資料"
        }
    }
}

// MARK: - Supporting Types

/**
 * 排序選項
 */
enum SortOption: String, CaseIterable {
    case name = "名稱"
    case recent = "最近使用"
    case frequent = "使用頻率"
    case favorite = "收藏優先"
    
    var icon: String {
        switch self {
        case .name: return "textformat"
        case .recent: return "clock"
        case .frequent: return "arrow.up.arrow.down"
        case .favorite: return "star"
        }
    }
}
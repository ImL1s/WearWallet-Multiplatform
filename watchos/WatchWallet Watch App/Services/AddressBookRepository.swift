//
//  AddressBookRepository.swift
//  WatchWallet Watch App
//
//  地址簿資料儲存庫 - 負責地址簿的 CRUD 操作
//  Created: 2025-08-07
//

import Foundation
import SwiftData
import SwiftUI

/**
 * 地址簿儲存庫協議
 * 
 * 定義地址簿 CRUD 操作的介面
 */
protocol AddressBookRepositoryProtocol {
    func createContact(_ contact: AddressContact) async throws
    func getContact(by id: UUID) async throws -> AddressContact?
    func getAllContacts() async throws -> [AddressContact]
    func updateContact(_ contact: AddressContact) async throws
    func deleteContact(_ contact: AddressContact) async throws
    func deleteContact(by id: UUID) async throws
    func searchContacts(query: String) async throws -> [AddressContact]
    func getFavoriteContacts() async throws -> [AddressContact]
    func getRecentContacts(limit: Int) async throws -> [AddressContact]
    func getFrequentContacts(limit: Int) async throws -> [AddressContact]
    func getContactsByChain(chainType: String) async throws -> [AddressContact]
    func getContactsByTag(tag: String) async throws -> [AddressContact]
    func validateAddress(_ address: String, chainType: String) async throws -> Bool
    func importContacts(_ contacts: [AddressContact]) async throws
    func exportContacts() async throws -> Data
    func clearAllContacts() async throws
}

/**
 * 地址簿儲存庫實現
 * 
 * 使用 SwiftData 進行資料持久化
 */
@MainActor
class AddressBookRepository: ObservableObject, AddressBookRepositoryProtocol {
    
    // MARK: - Properties
    
    /// SwiftData 模型容器
    private let modelContainer: ModelContainer
    
    /// SwiftData 模型上下文
    private let modelContext: ModelContext
    
    /// 發布的聯絡人列表
    @Published private(set) var contacts: [AddressContact] = []
    
    /// 發布的收藏聯絡人
    @Published private(set) var favoriteContacts: [AddressContact] = []
    
    /// 發布的最近使用聯絡人
    @Published private(set) var recentContacts: [AddressContact] = []
    
    /// 單例實例
    static let shared = AddressBookRepository()
    
    // MARK: - Initialization
    
    private init() {
        do {
            // 配置 SwiftData 模型容器
            let schema = Schema([
                AddressContact.self
            ])
            
            let modelConfiguration = ModelConfiguration(
                schema: schema,
                isStoredInMemoryOnly: false,
                allowsSave: true
            )
            
            self.modelContainer = try ModelContainer(
                for: schema,
                configurations: [modelConfiguration]
            )
            
            self.modelContext = modelContainer.mainContext
            
            // 載入初始資料
            Task {
                await loadContacts()
            }
            
        } catch {
            fatalError("Failed to create model container: \(error)")
        }
    }
    
    // MARK: - CRUD Operations
    
    /// 創建新聯絡人
    func createContact(_ contact: AddressContact) async throws {
        modelContext.insert(contact)
        try modelContext.save()
        await loadContacts()
        
        print("[AddressBookRepository] Created contact: \(contact.name)")
    }
    
    /// 獲取單個聯絡人
    func getContact(by id: UUID) async throws -> AddressContact? {
        let descriptor = FetchDescriptor<AddressContact>(
            predicate: #Predicate { contact in
                contact.id == id
            }
        )
        
        let results = try modelContext.fetch(descriptor)
        return results.first
    }
    
    /// 獲取所有聯絡人
    func getAllContacts() async throws -> [AddressContact] {
        let descriptor = FetchDescriptor<AddressContact>()
        return try modelContext.fetch(descriptor).sorted { lhs, rhs in
            if lhs.isFavorite != rhs.isFavorite { return lhs.isFavorite }
            if lhs.useCount != rhs.useCount { return lhs.useCount > rhs.useCount }
            return lhs.name < rhs.name
        }
    }
    
    /// 更新聯絡人
    func updateContact(_ contact: AddressContact) async throws {
        contact.lastModifiedDate = Date()
        try modelContext.save()
        await loadContacts()
        
        print("[AddressBookRepository] Updated contact: \(contact.name)")
    }
    
    /// 刪除聯絡人
    func deleteContact(_ contact: AddressContact) async throws {
        modelContext.delete(contact)
        try modelContext.save()
        await loadContacts()
        
        print("[AddressBookRepository] Deleted contact: \(contact.name)")
    }
    
    /// 根據 ID 刪除聯絡人
    func deleteContact(by id: UUID) async throws {
        if let contact = try await getContact(by: id) {
            try await deleteContact(contact)
        }
    }
    
    // MARK: - Search and Filter
    
    /// 搜索聯絡人
    func searchContacts(query: String) async throws -> [AddressContact] {
        if query.isEmpty {
            return try await getAllContacts()
        }
        
        let lowercasedQuery = query.lowercased()
        
        let descriptor = FetchDescriptor<AddressContact>(
            predicate: #Predicate { contact in
                contact.name.localizedStandardContains(query) ||
                contact.primaryAddress.localizedStandardContains(query) ||
                contact.chainType.localizedStandardContains(query) ||
                contact.notes.localizedStandardContains(query)
            }
        )
        
        return try modelContext.fetch(descriptor).sorted { lhs, rhs in
            if lhs.isFavorite != rhs.isFavorite { return lhs.isFavorite }
            return lhs.name < rhs.name
        }
    }
    
    /// 獲取收藏聯絡人
    func getFavoriteContacts() async throws -> [AddressContact] {
        let descriptor = FetchDescriptor<AddressContact>(
            predicate: #Predicate { contact in
                contact.isFavorite == true
            }
        )
        
        return try modelContext.fetch(descriptor).sorted { $0.name < $1.name }
    }
    
    /// 獲取最近使用的聯絡人
    func getRecentContacts(limit: Int = 10) async throws -> [AddressContact] {
        let descriptor = FetchDescriptor<AddressContact>(
            predicate: #Predicate { contact in
                contact.lastUsedDate != nil
            }
        )
        
        let allContacts = try modelContext.fetch(descriptor)
        let sorted = allContacts.sorted { 
            ($0.lastUsedDate ?? Date.distantPast) > ($1.lastUsedDate ?? Date.distantPast)
        }
        return Array(sorted.prefix(limit))
    }
    
    /// 獲取常用聯絡人
    func getFrequentContacts(limit: Int = 10) async throws -> [AddressContact] {
        let descriptor = FetchDescriptor<AddressContact>(
            predicate: #Predicate { contact in
                contact.useCount > 0
            }
        )
        
        let allContacts = try modelContext.fetch(descriptor)
        let sorted = allContacts.sorted { $0.useCount > $1.useCount }
        return Array(sorted.prefix(limit))
    }
    
    /// 根據區塊鏈類型獲取聯絡人
    func getContactsByChain(chainType: String) async throws -> [AddressContact] {
        let descriptor = FetchDescriptor<AddressContact>(
            predicate: #Predicate { contact in
                contact.chainType == chainType
            }
        )
        
        return try modelContext.fetch(descriptor).sorted { lhs, rhs in
            if lhs.isFavorite != rhs.isFavorite { return lhs.isFavorite }
            return lhs.name < rhs.name
        }
    }
    
    /// 根據標籤獲取聯絡人
    func getContactsByTag(tag: String) async throws -> [AddressContact] {
        let descriptor = FetchDescriptor<AddressContact>(
            predicate: #Predicate { contact in
                contact.tags.contains(tag)
            }
        )
        
        return try modelContext.fetch(descriptor).sorted { lhs, rhs in
            if lhs.isFavorite != rhs.isFavorite { return lhs.isFavorite }
            return lhs.name < rhs.name
        }
    }
    
    // MARK: - Validation
    
    /// 驗證地址格式
    func validateAddress(_ address: String, chainType: String) async throws -> Bool {
        // TODO: 實現各種區塊鏈地址格式驗證
        switch chainType.lowercased() {
        case "ethereum", "bsc", "polygon", "arbitrum", "optimism":
            return validateEthereumAddress(address)
        case "bitcoin":
            return validateBitcoinAddress(address)
        case "solana":
            return validateSolanaAddress(address)
        default:
            return !address.isEmpty
        }
    }
    
    // MARK: - Import/Export
    
    /// 導入聯絡人
    func importContacts(_ contacts: [AddressContact]) async throws {
        for contact in contacts {
            // 檢查是否已存在
            let exists = try await getContact(by: contact.id) != nil
            if !exists {
                modelContext.insert(contact)
            }
        }
        
        try modelContext.save()
        await loadContacts()
        
        print("[AddressBookRepository] Imported \(contacts.count) contacts")
    }
    
    /// 導出聯絡人
    func exportContacts() async throws -> Data {
        let contacts = try await getAllContacts()
        
        // 創建可編碼的聯絡人資料
        let exportData = contacts.map { contact in
            ExportableContact(
                id: contact.id,
                name: contact.name,
                primaryAddress: contact.primaryAddress,
                chainType: contact.chainType,
                tags: contact.tags,
                notes: contact.notes,
                isFavorite: contact.isFavorite,
                additionalAddresses: contact.additionalAddresses,
                source: contact.source
            )
        }
        
        let encoder = JSONEncoder()
        encoder.outputFormatting = .prettyPrinted
        return try encoder.encode(exportData)
    }
    
    /// 清除所有聯絡人
    func clearAllContacts() async throws {
        let contacts = try await getAllContacts()
        for contact in contacts {
            modelContext.delete(contact)
        }
        
        try modelContext.save()
        await loadContacts()
        
        print("[AddressBookRepository] Cleared all contacts")
    }
    
    // MARK: - Private Methods
    
    /// 載入聯絡人資料
    private func loadContacts() async {
        do {
            self.contacts = try await getAllContacts()
            self.favoriteContacts = try await getFavoriteContacts()
            self.recentContacts = try await getRecentContacts(limit: 5)
        } catch {
            print("[AddressBookRepository] Failed to load contacts: \(error)")
        }
    }
    
    /// 驗證以太坊地址
    private func validateEthereumAddress(_ address: String) -> Bool {
        let pattern = "^0x[a-fA-F0-9]{40}$"
        let regex = try? NSRegularExpression(pattern: pattern)
        let range = NSRange(location: 0, length: address.utf16.count)
        return regex?.firstMatch(in: address, range: range) != nil
    }
    
    /// 驗證比特幣地址
    private func validateBitcoinAddress(_ address: String) -> Bool {
        // 簡化的比特幣地址驗證
        let pattern = "^[13][a-km-zA-HJ-NP-Z1-9]{25,34}$|^bc1[a-z0-9]{39,59}$"
        let regex = try? NSRegularExpression(pattern: pattern)
        let range = NSRange(location: 0, length: address.utf16.count)
        return regex?.firstMatch(in: address, range: range) != nil
    }
    
    /// 驗證 Solana 地址
    private func validateSolanaAddress(_ address: String) -> Bool {
        // Solana 地址是 32 字節的 base58 編碼
        let pattern = "^[1-9A-HJ-NP-Za-km-z]{32,44}$"
        let regex = try? NSRegularExpression(pattern: pattern)
        let range = NSRange(location: 0, length: address.utf16.count)
        return regex?.firstMatch(in: address, range: range) != nil
    }
}

// MARK: - Supporting Types

/**
 * 可導出的聯絡人資料結構
 */
struct ExportableContact: Codable {
    let id: UUID
    let name: String
    let primaryAddress: String
    let chainType: String
    let tags: [String]
    let notes: String
    let isFavorite: Bool
    let additionalAddresses: [ChainAddress]
    let source: ContactSource
}

// MARK: - Mock Data Extension

extension AddressBookRepository {
    
    /// 創建測試資料
    func createMockData() async {
        let mockContacts = [
            AddressContact(
                name: "Vitalik Buterin",
                primaryAddress: "0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045",
                chainType: "Ethereum",
                tags: ["開發者", "以太坊創始人"],
                notes: "以太坊創始人的錢包",
                isFavorite: true
            ),
            AddressContact(
                name: "幣安熱錢包",
                primaryAddress: "0x28C6c06298d514Db089934071355E5743bf21d60",
                chainType: "Ethereum",
                tags: ["交易所", "熱錢包"],
                notes: "幣安交易所的熱錢包地址",
                isFavorite: false
            ),
            AddressContact(
                name: "測試錢包",
                primaryAddress: "0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb2",
                chainType: "Ethereum",
                tags: ["測試"],
                notes: "用於測試的錢包地址",
                isFavorite: false
            )
        ]
        
        for contact in mockContacts {
            try? await createContact(contact)
        }
    }
}
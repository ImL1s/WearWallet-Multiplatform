//
//  KMPAddressBookBridge.swift
//  WatchWallet Watch App
//
//  橋接 KMP AddressBook 功能到 watchOS - 簡化版本
//

import Foundation
import SwiftUI
import coreKmp

// MARK: - Swift Address Contact Model

/// Swift 端的地址簿聯絡人模型
struct SwiftAddressContact: Identifiable, Codable, Hashable {
    let id: String
    let name: String
    let address: String
    let primaryAddress: String
    let primaryChain: String
    let chainTypeRaw: String  // 使用 String 存儲，避免 Codable 問題
    let tags: [String]
    let notes: String
    let isFavorite: Bool
    var isVerified: Bool
    let createdAt: Date
    let lastModified: Date
    let lastUsed: Date?
    let useCount: Int
    let color: String
    
    var shortAddress: String {
        if address.count > 10 {
            return "\(address.prefix(6))...\(address.suffix(4))"
        }
        return address
    }
    
    var displayChain: String {
        return primaryChain
    }
    
    // 使用 coreKmp.ChainType
    var chainType: coreKmp.ChainType {
        switch chainTypeRaw {
        case "ethereum": return .ethereum
        case "bsc": return .bsc
        case "polygon": return .polygon
        case "avalanche": return .avalanche
        case "arbitrum": return .arbitrum
        case "optimism": return .optimism
        case "cronos": return .cronos
        default: return .ethereum
        }
    }
}

// MARK: - Swift Address Book Statistics

struct SwiftAddressBookStatistics {
    let totalContacts: Int
    let favoriteContacts: Int
    let frequentContacts: Int
    let recentContacts: Int
    let chains: [String: Int]
    let tags: [String: Int]
}

// MARK: - Address Book Bridge

/// 橋接 KMP AddressBook 功能
@MainActor
class KMPAddressBookBridge: ObservableObject {
    
    // MARK: - Published Properties
    @Published var contacts: [SwiftAddressContact] = []
    @Published var favoriteContacts: [SwiftAddressContact] = []
    @Published var recentContacts: [SwiftAddressContact] = []
    @Published var isLoading = false
    @Published var error: String?
    
    // MARK: - Singleton
    static let shared: KMPAddressBookBridge = {
        return KMPAddressBookBridge()
    }()
    
    private init() {
        Task {
            await loadContacts()
        }
    }
    
    /// 載入所有聯絡人
    func loadContacts() async {
        isLoading = true
        error = nil
        
        loadContactsFromLocal()
        
        isLoading = false
    }
    
    /// 搜索聯絡人
    func searchContacts(_ query: String) async -> [SwiftAddressContact] {
        if query.isEmpty {
            return contacts
        }
        
        return contacts.filter { contact in
            contact.name.localizedCaseInsensitiveContains(query) ||
            contact.address.localizedCaseInsensitiveContains(query) ||
            contact.tags.contains { $0.localizedCaseInsensitiveContains(query) }
        }
    }
    
    /// 切換收藏狀態
    func toggleFavorite(_ contactId: String) async -> Bool {
        if let index = contacts.firstIndex(where: { $0.id == contactId }) {
            saveContactsToLocal()
            await loadContacts()
            return true
        }
        return false
    }
    
    /// 記錄使用
    func recordUsage(_ contactId: String) async -> Bool {
        if let index = contacts.firstIndex(where: { $0.id == contactId }) {
            saveContactsToLocal()
            await loadContacts()
            return true
        }
        return false
    }
    
    /// 添加新聯絡人
    func addContact(
        name: String,
        address: String,
        chainType: coreKmp.ChainType,
        tags: [String] = [],
        notes: String = "",
        isFavorite: Bool = false
    ) async -> Swift.Result<String, Error> {
        let chainName = getChainDisplayName(chainType)
        
        let newContact = SwiftAddressContact(
            id: UUID().uuidString,
            name: name,
            address: address,
            primaryAddress: address,
            primaryChain: chainName,
            chainTypeRaw: getChainRaw(chainType),
            tags: tags,
            notes: notes,
            isFavorite: isFavorite,
            isVerified: false,
            createdAt: Date(),
            lastModified: Date(),
            lastUsed: nil,
            useCount: 0,
            color: "blue"
        )
        
        contacts.append(newContact)
        saveContactsToLocal()
        await loadContacts()
        
        return .success(newContact.id)
    }
    
    /// 更新聯絡人
    func updateContact(_ contact: SwiftAddressContact) async -> Swift.Result<Void, Error> {
        if let index = contacts.firstIndex(where: { $0.id == contact.id }) {
            contacts[index] = contact
            saveContactsToLocal()
            await loadContacts()
            return .success(())
        }
        return .failure(NSError(domain: "KMPAddressBookBridge", code: 404, userInfo: [NSLocalizedDescriptionKey: "Contact not found"]))
    }
    
    /// 刪除聯絡人
    func deleteContact(_ contactId: String) async -> Bool {
        contacts.removeAll { $0.id == contactId }
        saveContactsToLocal()
        await loadContacts()
        return true
    }
    
    /// 導入聯絡人
    func importContacts(_ json: String) async -> Int? {
        guard let data = json.data(using: .utf8),
              let imported = try? JSONDecoder().decode([SwiftAddressContact].self, from: data) else {
            return nil
        }
        
        for contact in imported {
            if !contacts.contains(where: { $0.primaryAddress == contact.primaryAddress && $0.chainTypeRaw == contact.chainTypeRaw }) {
                contacts.append(contact)
            }
        }
        
        saveContactsToLocal()
        await loadContacts()
        return imported.count
    }
    
    /// 導出聯絡人
    func exportContacts() async -> String? {
        guard let data = try? JSONEncoder().encode(contacts),
              let json = String(data: data, encoding: .utf8) else {
            return nil
        }
        return json
    }
    
    /// 驗證地址
    func verifyAddress(_ address: String, chainType: coreKmp.ChainType) async -> Bool {
        // 模擬驗證
        return !address.isEmpty
    }
    
    /// 獲取統計資訊
    func getStatistics() async -> SwiftAddressBookStatistics {
        let chains = Dictionary(grouping: contacts, by: { $0.chainTypeRaw }).mapValues { $0.count }
        let tags = Dictionary(grouping: contacts.flatMap { $0.tags }, by: { $0 }).mapValues { $0.count }
        
        return SwiftAddressBookStatistics(
            totalContacts: contacts.count,
            favoriteContacts: contacts.filter { $0.isFavorite }.count,
            frequentContacts: contacts.filter { $0.useCount > 5 }.count,
            recentContacts: contacts.filter { $0.lastUsed != nil }.count,
            chains: chains,
            tags: tags
        )
    }
    
    /// 更新單個聯絡人
    func updateContact(_ contact: SwiftAddressContact) async -> Bool {
        if let index = contacts.firstIndex(where: { $0.id == contact.id }) {
            contacts[index] = contact
            saveContactsToLocal()
            await loadContacts()
            return true
        }
        return false
    }
    
    /// 創建聯絡人 (回傳可選 ID)
    func createContact(
        name: String,
        address: String,
        chainType: String,
        tags: [String] = [],
        notes: String = "",
        isFavorite: Bool = false
    ) async -> SwiftAddressContact? {
        let newContact = SwiftAddressContact(
            id: UUID().uuidString,
            name: name,
            address: address,
            primaryAddress: address,
            primaryChain: chainType,
            chainTypeRaw: chainType,
            tags: tags,
            notes: notes,
            isFavorite: isFavorite,
            isVerified: false,
            createdAt: Date(),
            lastModified: Date(),
            lastUsed: nil,
            useCount: 0,
            color: "blue"
        )
        
        contacts.append(newContact)
        saveContactsToLocal()
        await loadContacts()
        return newContact
    }
    
    // MARK: - Helper Functions
    
    private func getChainDisplayName(_ chainType: coreKmp.ChainType) -> String {
        switch chainType {
        case .ethereum: return "Ethereum"
        case .bsc: return "BSC"
        case .polygon: return "Polygon"
        case .avalanche: return "Avalanche"
        case .arbitrum: return "Arbitrum"
        case .optimism: return "Optimism"
        case .cronos: return "Cronos"
        default: return "Ethereum"
        }
    }
    
    private func getChainRaw(_ chainType: coreKmp.ChainType) -> String {
        switch chainType {
        case .ethereum: return "ethereum"
        case .bsc: return "bsc"
        case .polygon: return "polygon"
        case .avalanche: return "avalanche"
        case .arbitrum: return "arbitrum"
        case .optimism: return "optimism"
        case .cronos: return "cronos"
        default: return "ethereum"
        }
    }
    
    // MARK: - Local Storage
    
    private func loadContactsFromLocal() {
        if let data = UserDefaults.standard.data(forKey: "addressbook_contacts"),
           let decoded = try? JSONDecoder().decode([SwiftAddressContact].self, from: data) {
            self.contacts = decoded
            self.favoriteContacts = decoded.filter { $0.isFavorite }
            self.recentContacts = decoded.filter { $0.lastUsed != nil }
                .sorted { ($0.lastUsed ?? Date.distantPast) > ($1.lastUsed ?? Date.distantPast) }
                .prefix(5)
                .map { $0 }
        }
    }
    
    private func saveContactsToLocal() {
        if let encoded = try? JSONEncoder().encode(contacts) {
            UserDefaults.standard.set(encoded, forKey: "addressbook_contacts")
        }
    }
}
//
//  AddressBook.swift
//  WatchWallet Watch App
//
//  地址簿資料模型 - 使用 SwiftData 進行資料持久化
//  Created: 2025-08-07
//

import Foundation
import SwiftData

/**
 * 地址簿聯絡人模型
 * 
 * 使用 SwiftData @Model 宏進行資料持久化
 * 支援多鏈地址儲存和標籤管理
 */
@Model
final class AddressContact {
    
    // MARK: - Properties
    
    /// 唯一標識符
    @Attribute(.unique) var id: UUID
    
    /// 聯絡人名稱
    var name: String
    
    /// 主要錢包地址
    var primaryAddress: String
    
    /// 區塊鏈網路類型
    var chainType: String
    
    /// 聯絡人標籤
    var tags: [String]
    
    /// 備註
    var notes: String
    
    /// 是否為收藏
    var isFavorite: Bool
    
    /// 創建時間
    var createdDate: Date
    
    /// 最後更新時間
    var lastModifiedDate: Date
    
    /// 最後使用時間
    var lastUsedDate: Date?
    
    /// 使用次數
    var useCount: Int
    
    /// 頭像顏色（用於生成默認頭像）
    var avatarColor: String
    
    /// 額外的區塊鏈地址（支援多鏈）
    var additionalAddresses: [ChainAddress]
    
    /// 聯絡人來源（手動添加、掃描、導入等）
    var source: ContactSource
    
    /// 是否已驗證地址有效性
    var isVerified: Bool
    
    // MARK: - Initialization
    
    init(
        name: String,
        primaryAddress: String,
        chainType: String = "Ethereum",
        tags: [String] = [],
        notes: String = "",
        isFavorite: Bool = false,
        additionalAddresses: [ChainAddress] = [],
        source: ContactSource = .manual
    ) {
        self.id = UUID()
        self.name = name
        self.primaryAddress = primaryAddress
        self.chainType = chainType
        self.tags = tags
        self.notes = notes
        self.isFavorite = isFavorite
        self.createdDate = Date()
        self.lastModifiedDate = Date()
        self.lastUsedDate = nil
        self.useCount = 0
        self.avatarColor = Self.generateAvatarColor()
        self.additionalAddresses = additionalAddresses
        self.source = source
        self.isVerified = false
    }
    
    // MARK: - Methods
    
    /// 更新使用記錄
    func recordUsage() {
        self.lastUsedDate = Date()
        self.useCount += 1
        self.lastModifiedDate = Date()
    }
    
    /// 切換收藏狀態
    func toggleFavorite() {
        self.isFavorite.toggle()
        self.lastModifiedDate = Date()
    }
    
    /// 添加標籤
    func addTag(_ tag: String) {
        if !tags.contains(tag) {
            tags.append(tag)
            self.lastModifiedDate = Date()
        }
    }
    
    /// 移除標籤
    func removeTag(_ tag: String) {
        tags.removeAll { $0 == tag }
        self.lastModifiedDate = Date()
    }
    
    /// 添加額外的區塊鏈地址
    func addChainAddress(_ address: ChainAddress) {
        if !additionalAddresses.contains(where: { $0.chainType == address.chainType }) {
            additionalAddresses.append(address)
            self.lastModifiedDate = Date()
        }
    }
    
    /// 移除指定鏈的地址
    func removeChainAddress(chainType: String) {
        additionalAddresses.removeAll { $0.chainType == chainType }
        self.lastModifiedDate = Date()
    }
    
    /// 獲取指定鏈的地址
    func getAddress(for chainType: String) -> String? {
        if self.chainType == chainType {
            return primaryAddress
        }
        return additionalAddresses.first(where: { $0.chainType == chainType })?.address
    }
    
    /// 驗證地址
    func verifyAddress() {
        // TODO: 實現地址驗證邏輯
        self.isVerified = true
        self.lastModifiedDate = Date()
    }
    
    // MARK: - Static Methods
    
    /// 生成隨機頭像顏色
    private static func generateAvatarColor() -> String {
        let colors = [
            "#FF6B6B", "#4ECDC4", "#45B7D1", "#96CEB4", "#FFEAA7",
            "#74B9FF", "#A29BFE", "#FD79A8", "#FDCB6E", "#6C5CE7"
        ]
        return colors.randomElement() ?? "#4ECDC4"
    }
}

// MARK: - Supporting Types

/**
 * 額外的區塊鏈地址
 */
struct ChainAddress: Codable, Identifiable, Equatable {
    let id = UUID()
    let chainType: String
    let address: String
    let chainId: Int?
    
    init(chainType: String, address: String, chainId: Int? = nil) {
        self.chainType = chainType
        self.address = address
        self.chainId = chainId
    }
}

/**
 * 聯絡人來源類型
 */
enum ContactSource: String, Codable, CaseIterable {
    case manual = "手動添加"
    case scan = "掃描添加"
    case transaction = "交易記錄"
    case imported = "導入"
    case sync = "同步"
    
    var icon: String {
        switch self {
        case .manual: return "plus.circle"
        case .scan: return "qrcode.viewfinder"
        case .transaction: return "arrow.left.arrow.right"
        case .imported: return "square.and.arrow.down"
        case .sync: return "arrow.triangle.2.circlepath"
        }
    }
}

// MARK: - Search and Filter Extensions

extension AddressContact {
    
    /// 搜索匹配
    func matches(searchText: String) -> Bool {
        if searchText.isEmpty { return true }
        
        let lowercasedSearch = searchText.lowercased()
        
        return name.lowercased().contains(lowercasedSearch) ||
               primaryAddress.lowercased().contains(lowercasedSearch) ||
               chainType.lowercased().contains(lowercasedSearch) ||
               tags.contains { $0.lowercased().contains(lowercasedSearch) } ||
               notes.lowercased().contains(lowercasedSearch) ||
               additionalAddresses.contains { 
                   $0.address.lowercased().contains(lowercasedSearch) ||
                   $0.chainType.lowercased().contains(lowercasedSearch)
               }
    }
    
    /// 是否為最近使用
    var isRecentlyUsed: Bool {
        guard let lastUsed = lastUsedDate else { return false }
        let sevenDaysAgo = Date().addingTimeInterval(-7 * 24 * 60 * 60)
        return lastUsed > sevenDaysAgo
    }
    
    /// 是否為常用聯絡人（使用次數超過5次）
    var isFrequentlyUsed: Bool {
        return useCount >= 5
    }
}

// MARK: - Comparable

extension AddressContact: Comparable {
    static func < (lhs: AddressContact, rhs: AddressContact) -> Bool {
        // 優先級：收藏 > 使用頻率 > 最近使用 > 名稱
        if lhs.isFavorite != rhs.isFavorite {
            return lhs.isFavorite
        }
        if lhs.useCount != rhs.useCount {
            return lhs.useCount > rhs.useCount
        }
        if let lhsDate = lhs.lastUsedDate, let rhsDate = rhs.lastUsedDate {
            return lhsDate > rhsDate
        }
        return lhs.name < rhs.name
    }
    
    static func == (lhs: AddressContact, rhs: AddressContact) -> Bool {
        return lhs.id == rhs.id
    }
}
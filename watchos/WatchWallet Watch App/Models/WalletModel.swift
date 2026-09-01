//
//  WalletModel.swift
//  WatchWallet Watch App
//
//  Unified wallet models to avoid type conflicts
//

import Foundation
import SwiftUI

// MARK: - Wallet Type
enum WalletType {
    case hot
    case cold
    case hardware
    
    var icon: String {
        switch self {
        case .hot:
            return "flame.fill"
        case .cold:
            return "snow"
        case .hardware:
            return "cpu"
        }
    }
    
    var color: Color {
        switch self {
        case .hot:
            return .orange
        case .cold:
            return .blue
        case .hardware:
            return .green
        }
    }
    
    var displayName: String {
        switch self {
        case .hot:
            return "熱錢包"
        case .cold:
            return "冷錢包"
        case .hardware:
            return "硬體錢包"
        }
    }
}

// MARK: - Wallet Model
struct WalletModel: Identifiable {
    let id: String
    let name: String
    let address: String
    let type: WalletType
    let chainId: String
    
    // Optional properties
    var balance: String?
    var usdValue: String?
    var isSelected: Bool = false
    var isBackedUp: Bool = false
    var createdAt: Date = Date()
    
    // Computed properties
    var shortAddress: String {
        guard address.count > 10 else { return address }
        let prefix = address.prefix(6)
        let suffix = address.suffix(4)
        return "\(prefix)...\(suffix)"
    }
    
    var chainName: String {
        switch chainId {
        case "1":
            return "Ethereum Mainnet"
        case "11155111":
            return "Ethereum Sepolia"
        case "56":
            return "BNB Smart Chain"
        case "137":
            return "Polygon"
        case "25":
            return "Cronos"
        case "42161":
            return "Arbitrum"
        case "8453":
            return "Base"
        case "10":
            return "Optimism"
        default:
            return "Unknown Chain"
        }
    }
}
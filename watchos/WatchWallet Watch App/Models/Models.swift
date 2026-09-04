//
//  Models.swift
//  WatchWallet Watch App
//
//  Data models for the watchOS app
//

import Foundation
import SwiftUI

// MARK: - Token Model
struct TokenModel: Identifiable {
    let id: String
    let symbol: String
    let name: String
    let chainId: String
    
    // Optional properties
    var contractAddress: String?
    var decimals: Int?
    var logoUrl: String?
    var balance: String?
    var usdValue: String?
    var priceUsd: Double?
    var priceChange24h: Double?
    var isNative: Bool = false
    var isCustom: Bool = false
}

// MARK: - Transaction Model
enum TransactionType {
    case sent
    case received
    
    var icon: String {
        switch self {
        case .sent:
            return "arrow.up.circle.fill"
        case .received:
            return "arrow.down.circle.fill"
        }
    }
    
    var color: Color {
        switch self {
        case .sent:
            return .orange
        case .received:
            return .green
        }
    }
}

enum TransactionStatus {
    case pending
    case completed
    case failed
    case signed
}

struct TransactionModel: Identifiable {
    let id: String
    let hash: String
    let from: String
    let to: String
    let value: String
    let symbol: String
    let timestamp: Date
    let status: TransactionStatus
    let type: TransactionType
    
    // Gas information
    var gasPrice: String?
    var gasUsed: String?
    var gasFee: String?
    
    // USD value at time of transaction
    var usdValue: String?
    
    // Network information
    var chainId: String?
    var chainName: String?
    
    // Block information
    var blockNumber: String?
    var confirmations: Int?
    
    // Computed properties
    var amountText: String {
        let prefix = type == .sent ? "-" : "+"
        return "\(prefix)\(value) \(symbol)"
    }
    
    var shortHash: String {
        guard hash.count > 10 else { return hash }
        let prefix = hash.prefix(6)
        let suffix = hash.suffix(4)
        return "\(prefix)...\(suffix)"
    }
    
    var networkDisplayName: String {
        return chainName ?? "Unknown Network"
    }
}

// MARK: - Network Model
struct NetworkModel: Identifiable {
    let id: String
    let name: String
    let chainId: String
    let rpcUrl: String
    let symbol: String
    let explorerUrl: String?
    let isTestnet: Bool
    let isCustom: Bool
}

// MARK: - Token Info (for Token Management)
struct TokenInfo: Identifiable {
    let id: String
    let contractAddress: String
    let symbol: String
    let name: String
    let decimals: Int
    let chainId: String
    let isCustom: Bool
}
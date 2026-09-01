//
//  UTXOModels.swift
//  WatchWallet Watch App
//
//  UTXO 鏈專用資料模型
//

import Foundation
import SwiftUI

// MARK: - UTXO Chain Types

enum UTXOChainType: String, CaseIterable {
    case bitcoin = "BITCOIN"
    case litecoin = "LITECOIN"
    case dogecoin = "DOGECOIN"
    case bitcoinCash = "BITCOIN_CASH"
    
    var name: String { rawValue }
    
    var displayName: String {
        switch self {
        case .bitcoin:
            return "Bitcoin"
        case .litecoin:
            return "Litecoin"
        case .dogecoin:
            return "Dogecoin"
        case .bitcoinCash:
            return "Bitcoin Cash"
        }
    }
    
    var symbol: String {
        switch self {
        case .bitcoin:
            return "BTC"
        case .litecoin:
            return "LTC"
        case .dogecoin:
            return "DOGE"
        case .bitcoinCash:
            return "BCH"
        }
    }
    
    var icon: String {
        switch self {
        case .bitcoin:
            return "bitcoinsign.circle.fill"
        case .litecoin:
            return "l.circle.fill"
        case .dogecoin:
            return "d.circle.fill"
        case .bitcoinCash:
            return "b.circle.fill"
        }
    }
    
    var color: Color {
        switch self {
        case .bitcoin:
            return .orange
        case .litecoin:
            return .gray
        case .dogecoin:
            return .yellow
        case .bitcoinCash:
            return .green
        }
    }
    
    var addressPrefix: String {
        switch self {
        case .bitcoin:
            return "bc1" // Bech32 format
        case .litecoin:
            return "ltc1" // Bech32 format
        case .dogecoin:
            return "D" // Legacy format
        case .bitcoinCash:
            return "bitcoincash:" // CashAddr format
        }
    }
    
    var explorerUrl: String {
        switch self {
        case .bitcoin:
            return "https://blockchair.com/bitcoin"
        case .litecoin:
            return "https://blockchair.com/litecoin"
        case .dogecoin:
            return "https://blockchair.com/dogecoin"
        case .bitcoinCash:
            return "https://blockchair.com/bitcoin-cash"
        }
    }
    
    var decimals: Int {
        return 8 // All UTXO chains use 8 decimals
    }
    
    var minDustAmount: Int64 {
        switch self {
        case .bitcoin:
            return 546 // satoshis
        case .litecoin:
            return 5460 // litoshis
        case .dogecoin:
            return 1_000_000 // koinus (0.01 DOGE)
        case .bitcoinCash:
            return 546 // satoshis
        }
    }
}

// MARK: - UTXO Model

struct UTXO: Identifiable, Codable {
    let id: String
    let txid: String
    let vout: Int
    let value: Int64 // In satoshis/litoshis/koinus
    let scriptPubKey: String
    let address: String
    let confirmations: Int
    let spendable: Bool
    
    var formattedValue: String {
        let divisor = pow(10.0, 8.0)
        let value = Double(self.value) / divisor
        return String(format: "%.8f", value)
    }
    
    var shortTxid: String {
        guard txid.count > 10 else { return txid }
        return "\(txid.prefix(6))...\(txid.suffix(4))"
    }
}

// MARK: - UTXO Transaction

struct UTXOTransaction: Identifiable {
    let id: String
    let txid: String
    let inputs: [UTXOInput]
    let outputs: [UTXOOutput]
    let fee: Int64
    let size: Int
    let confirmations: Int
    let timestamp: Date
    let blockHeight: Int?
    
    var totalInput: Int64 {
        inputs.reduce(0) { $0 + $1.value }
    }
    
    var totalOutput: Int64 {
        outputs.reduce(0) { $0 + $1.value }
    }
    
    var formattedFee: String {
        let divisor = pow(10.0, 8.0)
        let fee = Double(self.fee) / divisor
        return String(format: "%.8f", fee)
    }
    
    var feeRate: Double {
        Double(fee) / Double(size)
    }
}

struct UTXOInput: Identifiable {
    let id: String
    let prevTxid: String
    let prevVout: Int
    let scriptSig: String
    let sequence: UInt32
    let address: String?
    let value: Int64
}

struct UTXOOutput: Identifiable {
    let id: String
    let value: Int64
    let scriptPubKey: String
    let address: String?
    let spent: Bool
}

// MARK: - UTXO Balance

struct UTXOBalance {
    let chain: UTXOChainType
    let address: String
    let confirmed: Int64
    let unconfirmed: Int64
    let utxoCount: Int
    let lastUpdated: Date
    
    var total: Int64 {
        confirmed + unconfirmed
    }
    
    var formattedConfirmed: String {
        formatSatoshis(confirmed)
    }
    
    var formattedUnconfirmed: String {
        formatSatoshis(unconfirmed)
    }
    
    var formattedTotal: String {
        formatSatoshis(total)
    }
    
    private func formatSatoshis(_ satoshis: Int64) -> String {
        let divisor = pow(10.0, 8.0)
        let value = Double(satoshis) / divisor
        return String(format: "%.8f", value)
    }
}

// MARK: - UTXO Fee Estimation

struct UTXOFeeEstimate {
    let chain: UTXOChainType
    let fastestFee: Int64   // 1-2 blocks
    let halfHourFee: Int64  // 3-6 blocks
    let hourFee: Int64      // 6-12 blocks
    let economyFee: Int64   // 12+ blocks
    
    func getFeeForPriority(_ priority: TransactionPriority) -> Int64 {
        switch priority {
        case .fastest:
            return fastestFee
        case .fast:
            return halfHourFee
        case .normal:
            return hourFee
        case .economy:
            return economyFee
        }
    }
}

enum TransactionPriority: String, CaseIterable {
    case fastest = "最快"
    case fast = "快速"
    case normal = "一般"
    case economy = "經濟"
    
    var confirmationBlocks: String {
        switch self {
        case .fastest:
            return "1-2 區塊"
        case .fast:
            return "3-6 區塊"
        case .normal:
            return "6-12 區塊"
        case .economy:
            return "12+ 區塊"
        }
    }
}

// MARK: - UTXO Address Type

enum UTXOAddressType: String, CaseIterable {
    case legacy = "LEGACY"       // P2PKH
    case segwit = "SEGWIT"       // P2WPKH (Bech32)
    case compatSegwit = "COMPAT" // P2SH-P2WPKH
    case taproot = "TAPROOT"     // P2TR (Bech32m)
    
    var displayName: String {
        switch self {
        case .legacy:
            return "傳統地址"
        case .segwit:
            return "原生隔離見證"
        case .compatSegwit:
            return "兼容隔離見證"
        case .taproot:
            return "Taproot"
        }
    }
    
    func isSupported(by chain: UTXOChainType) -> Bool {
        switch chain {
        case .bitcoin:
            return true // Bitcoin supports all types
        case .litecoin:
            return self != .taproot // Litecoin doesn't support Taproot yet
        case .dogecoin:
            return self == .legacy // Dogecoin only supports legacy
        case .bitcoinCash:
            return self == .legacy // BCH uses CashAddr format for legacy
        }
    }
}

// MARK: - UTXO Network Status

struct UTXONetworkStatus {
    let chain: UTXOChainType
    let blockHeight: Int
    let difficulty: Double
    let hashRate: String
    let mempoolSize: Int
    let mempoolBytes: Int64
    let averageFee: Int64
    let lastBlockTime: Date
    let isConnected: Bool
}
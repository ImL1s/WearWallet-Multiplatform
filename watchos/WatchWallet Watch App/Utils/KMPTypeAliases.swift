//
//  KMPTypeAliases.swift
//  WatchWallet Watch App
//
//  Global type aliases for KMP types to avoid naming conflicts
//

import coreKmp

// Transaction types - Updated to use correct class names
typealias KMPTransaction = coreKmp.Transaction
typealias KMPTransactionStatus = coreKmp.TransactionStatus
typealias KMPTransactionRequest = coreKmp.TransactionRequest

// Balance types - Note: Balance class doesn't exist in coreKmp
// Using a struct as placeholder
struct KMPBalance {
    let amount: String
    let symbol: String
    let usdValue: Double?
}

// Wallet types
// Wallet types
typealias KMPWallet = SwiftWalletAccount

// Token types
typealias KMPToken = coreKmp.Token

// Chain types
typealias KMPChain = coreKmp.ChainType
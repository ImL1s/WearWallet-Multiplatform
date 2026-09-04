#!/usr/bin/env swift

import Foundation

// Debug wallet persistence by checking NSUserDefaults
let userDefaults = UserDefaults.standard

print("=== Debugging Wallet Persistence ===")

// List all keys in UserDefaults
print("\nAll UserDefaults keys:")
let allKeys = userDefaults.dictionaryRepresentation().keys
for key in allKeys.sorted() {
    print("  - \(key)")
}

// Check for wallet-related keys
print("\nWallet-related keys:")
let walletKeys = allKeys.filter { $0.contains("wallet") || $0.contains("data_") }
for key in walletKeys.sorted() {
    let value = userDefaults.object(forKey: key)
    print("  - \(key): \(value ?? "nil")")
}

// Check the specific keys that WalletRepositoryImpl uses
print("\nSpecific wallet keys:")
let walletListKey = "data_wallet_list"
let walletList = userDefaults.string(forKey: walletListKey)
print("  - \(walletListKey): \(walletList ?? "nil")")

if let walletList = walletList, !walletList.isEmpty {
    let walletIds = walletList.split(separator: ",")
    print("  - Found \(walletIds.count) wallet IDs: \(walletIds)")
    
    for walletId in walletIds {
        let walletKey = "data_wallet_\(walletId)"
        let walletData = userDefaults.string(forKey: walletKey)
        print("  - \(walletKey): \(walletData ?? "nil")")
    }
}

print("\n=== End Debug ===")
//
//  APIKeyConfig.swift
//  WatchWallet Watch App
//
//  API Key configuration for blockchain explorers
//

import Foundation

struct APIKeyConfig {
    
    /// 區塊鏈瀏覽器 API Keys 配置
    /// 請從以下網站獲取免費 API keys：
    /// - Etherscan: https://etherscan.io/apis
    /// - Polygonscan: https://polygonscan.com/apis  
    /// - BscScan: https://bscscan.com/apis
    /// - Cronoscan: https://cronoscan.com/apis
    static let blockchainExplorerAPIKeys: [String: String] = [
        // TODO: 請將這些示例值替換為你的真實 API keys
        
        // Ethereum (Etherscan)
        "1": "PASTE_YOUR_ETHERSCAN_API_KEY_HERE",           // Ethereum Mainnet
        "11155111": "PASTE_YOUR_ETHERSCAN_API_KEY_HERE",   // Ethereum Sepolia Testnet
        
        // Polygon (Polygonscan)
        "137": "PASTE_YOUR_POLYGONSCAN_API_KEY_HERE",      // Polygon Mainnet
        "80001": "PASTE_YOUR_POLYGONSCAN_API_KEY_HERE",    // Polygon Mumbai Testnet
        
        // BSC (BscScan)
        "56": "PASTE_YOUR_BSCSCAN_API_KEY_HERE",           // BSC Mainnet
        "97": "PASTE_YOUR_BSCSCAN_API_KEY_HERE",           // BSC Testnet
        
        // Cronos (Cronoscan)
        "25": "PASTE_YOUR_CRONOSCAN_API_KEY_HERE",         // Cronos Mainnet
        "338": "PASTE_YOUR_CRONOSCAN_API_KEY_HERE"         // Cronos Testnet
    ]
    
    /// 檢查 API keys 是否已配置
    static var areAPIKeysConfigured: Bool {
        return !blockchainExplorerAPIKeys.values.contains { key in
            key.hasPrefix("PASTE_YOUR_") || key.isEmpty
        }
    }
    
    /// 獲取已配置的網路數量
    static var configuredNetworksCount: Int {
        return blockchainExplorerAPIKeys.filter { !$0.value.hasPrefix("PASTE_YOUR_") && !$0.value.isEmpty }.count
    }
    
    /// 獲取配置狀態報告
    static var configurationStatus: String {
        if areAPIKeysConfigured {
            return "✅ 所有 \(blockchainExplorerAPIKeys.count) 個網路的 API keys 已配置"
        } else {
            let configuredCount = configuredNetworksCount
            let totalCount = blockchainExplorerAPIKeys.count
            return "⚠️ \(totalCount) 個網路中只有 \(configuredCount) 個已配置 API keys"
        }
    }
    
    /// 網路名稱映射
    static let chainNames: [String: String] = [
        "1": "Ethereum Mainnet",
        "11155111": "Ethereum Sepolia",
        "137": "Polygon Mainnet", 
        "80001": "Polygon Mumbai",
        "56": "BSC Mainnet",
        "97": "BSC Testnet",
        "25": "Cronos Mainnet",
        "338": "Cronos Testnet"
    ]
    
    /// 瀏覽器 URL 映射
    static let explorerURLs: [String: String] = [
        "1": "https://etherscan.io/apis",
        "11155111": "https://etherscan.io/apis",
        "137": "https://polygonscan.com/apis",
        "80001": "https://polygonscan.com/apis", 
        "56": "https://bscscan.com/apis",
        "97": "https://bscscan.com/apis",
        "25": "https://cronoscan.com/apis",
        "338": "https://cronoscan.com/apis"
    ]
    
    /// 打印配置指導
    static func printConfigurationGuide() {
        print("""
        
        🔑 API Key 配置指南
        ==================
        
        為了獲取完整的交易歷史，你需要配置區塊鏈瀏覽器的 API keys：
        
        1. 前往以下網站註冊免費帳戶並獲取 API key：
           • Etherscan (Ethereum): https://etherscan.io/apis
           • Polygonscan (Polygon): https://polygonscan.com/apis  
           • BscScan (BSC): https://bscscan.com/apis
           • Cronoscan (Cronos): https://cronoscan.com/apis
        
        2. 編輯檔案: WatchWallet Watch App/Config/APIKeyConfig.swift
        
        3. 將 "PASTE_YOUR_XXX_API_KEY_HERE" 替換為你的真實 API keys
        
        4. 重新建置應用程式
        
        目前狀態: \(configurationStatus)
        
        """)
    }
}
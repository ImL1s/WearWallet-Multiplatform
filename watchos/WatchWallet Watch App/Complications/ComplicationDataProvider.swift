//
//  ComplicationDataProvider.swift
//  WatchWallet Watch App
//
//  提供 Complication 數據的服務類
//  整合 PriceService 和其他數據源
//

import Foundation
import coreKmp

actor ComplicationDataProvider {
    
    // MARK: - Properties
    private let priceService = PriceService.shared
    private let walletRepositoryManager = WalletRepositoryManager.shared
    
    // 快取數據以避免重複請求
    private var cachedTokenData: ComplicationTokenData?
    private var cachedWalletData: ComplicationWalletData?
    private var lastUpdateTime: Date?
    private let cacheInterval: TimeInterval = 5 * 60 // 5分鐘快取
    
    // 預設代幣設定
    private let defaultTokens = ["ETH", "BTC", "BNB", "MATIC", "CRO"]
    
    // MARK: - Public Methods
    
    /**
     * 獲取主要代幣數據（用於 Circular Small 和 Modular Small）
     */
    func getPrimaryTokenData() async throws -> ComplicationTokenData {
        if let cached = cachedTokenData, shouldUseCache() {
            return cached
        }
        
        let tokenData = try await fetchPrimaryTokenData()
        cachedTokenData = tokenData
        lastUpdateTime = Date()
        return tokenData
    }
    
    /**
     * 獲取錢包餘額數據（用於 Utilitarian Small）
     */
    func getWalletBalanceData() async throws -> ComplicationWalletData {
        if let cached = cachedWalletData, shouldUseCache() {
            return cached
        }
        
        let walletData = try await fetchWalletBalanceData()
        cachedWalletData = walletData
        lastUpdateTime = Date()
        return walletData
    }
    
    /**
     * 強制刷新所有數據
     */
    func refreshAllData() async throws {
        cachedTokenData = nil
        cachedWalletData = nil
        lastUpdateTime = nil
        
        // 並行獲取數據
        async let tokenData = fetchPrimaryTokenData()
        async let walletData = fetchWalletBalanceData()
        
        cachedTokenData = try await tokenData
        cachedWalletData = try await walletData
        lastUpdateTime = Date()
    }
    
    // MARK: - Private Methods
    
    private func shouldUseCache() -> Bool {
        guard let lastUpdate = lastUpdateTime else { return false }
        return Date().timeIntervalSince(lastUpdate) < cacheInterval
    }
    
    private func fetchPrimaryTokenData() async throws -> ComplicationTokenData {
        // 獲取用戶偏好的主要代幣，預設為 ETH
        let primaryTokenSymbol = UserDefaults.standard.string(forKey: "primary_token_symbol") ?? "ETH"
        
        // 從 PriceService 獲取價格數據
        let price = await priceService.getTokenPrice(symbol: primaryTokenSymbol)
        let priceChange24h = await priceService.getPriceChange24h(symbol: primaryTokenSymbol)
        
        // 如果沒有數據，等待 PriceService 刷新
        let shouldUpdate = await priceService.shouldUpdatePrices()
        if price == nil && shouldUpdate {
            print("[ComplicationDataProvider] 價格數據不可用，嘗試刷新...")
            await priceService.refreshPrices()
            
            // 刷新後重新獲取
            let refreshedPrice = await priceService.getTokenPrice(symbol: primaryTokenSymbol)
            let refreshedChange = await priceService.getPriceChange24h(symbol: primaryTokenSymbol)
            
            return ComplicationTokenData(
                symbol: primaryTokenSymbol,
                price: refreshedPrice,
                priceChange24h: refreshedChange,
                lastUpdated: Date()
            )
        }
        
        return ComplicationTokenData(
            symbol: primaryTokenSymbol,
            price: price,
            priceChange24h: priceChange24h,
            lastUpdated: await priceService.getLastUpdated() ?? Date()
        )
    }
    
    private func fetchWalletBalanceData() async throws -> ComplicationWalletData {
        // 獲取當前活躍錢包
        guard let activeWallet = try await getActiveWallet() else {
            return ComplicationWalletData(
                totalUsdValue: nil,
                nativeBalance: nil,
                nativeSymbol: "ETH",
                lastUpdated: Date()
            )
        }
        
        // 計算總 USD 價值
        let totalUsdValue = try await calculateTotalUsdValue(for: activeWallet)
        
        return ComplicationWalletData(
            totalUsdValue: totalUsdValue,
            nativeBalance: activeWallet.balance,
            nativeSymbol: activeWallet.symbol,
            lastUpdated: Date()
        )
    }
    
    private func getActiveWallet() async throws -> ActiveWalletInfo? {
        // 從 UserDefaults 獲取錢包地址和鏈信息
        guard let walletAddress = UserDefaults.standard.string(forKey: "selected_wallet_address"),
              !walletAddress.isEmpty else {
            print("[ComplicationDataProvider] 沒有找到活躍錢包地址")
            return nil
        }
        
        let chainId = UserDefaults.standard.string(forKey: "selected_chain_id") ?? "1" // 預設 Ethereum
        
        // 模擬獲取餘額（實際實現需要調用 KMP 服務）
        // 這裡先使用靜態數據，後續需要整合真實的餘額查詢
        let balance = "1.5" // 示例餘額
        let symbol = getChainNativeSymbol(chainId: chainId)
        
        return ActiveWalletInfo(
            address: walletAddress,
            balance: balance,
            symbol: symbol,
            chainId: chainId
        )
    }
    
    private func calculateTotalUsdValue(for wallet: ActiveWalletInfo) async throws -> Double? {
        guard let balanceValue = Double(wallet.balance) else { return nil }
        
        // 獲取原生代幣的 USD 價格
        let price = await priceService.getTokenPrice(symbol: wallet.symbol)
        
        if let usdPrice = price {
            return balanceValue * usdPrice
        }
        
        return nil
    }
    
    private func getChainNativeSymbol(chainId: String) -> String {
        switch chainId {
        case "1": return "ETH"      // Ethereum
        case "56": return "BNB"     // BSC
        case "137": return "MATIC"  // Polygon
        case "25": return "CRO"     // Cronos
        default: return "ETH"
        }
    }
}

// MARK: - Data Models

struct ComplicationTokenData {
    let symbol: String
    let price: Double?
    let priceChange24h: Double?
    let lastUpdated: Date
    
    var isDataValid: Bool {
        return price != nil
    }
    
    var formattedPrice: String {
        guard let price = price else { return "N/A" }
        return formatPrice(price)
    }
    
    var formattedChange: String {
        guard let change = priceChange24h else { return "N/A" }
        let prefix = change >= 0 ? "+" : ""
        return "\(prefix)\(String(format: "%.1f", change))%"
    }
    
    private func formatPrice(_ price: Double) -> String {
        if price >= 10000 {
            return String(format: "%.1fK", price / 1000)
        } else if price >= 1 {
            return String(format: "%.0f", price)
        } else {
            return String(format: "%.4f", price)
        }
    }
}

struct ComplicationWalletData {
    let totalUsdValue: Double?
    let nativeBalance: String?
    let nativeSymbol: String
    let lastUpdated: Date
    
    var isDataValid: Bool {
        return totalUsdValue != nil || nativeBalance != nil
    }
    
    var formattedUsdValue: String {
        guard let usdValue = totalUsdValue else { return "N/A" }
        return formatUsdValue(usdValue)
    }
    
    var formattedBalance: String {
        guard let balance = nativeBalance else { return "N/A" }
        return "\(balance) \(nativeSymbol)"
    }
    
    private func formatUsdValue(_ value: Double) -> String {
        if value >= 1000000 {
            return String(format: "%.1fM", value / 1000000)
        } else if value >= 1000 {
            return String(format: "%.1fK", value / 1000)
        } else {
            return String(format: "%.0f", value)
        }
    }
}

struct ActiveWalletInfo {
    let address: String
    let balance: String
    let symbol: String
    let chainId: String
}

// MARK: - Extensions

extension PriceService {
    func getLastUpdated() async -> Date? {
        return lastUpdated
    }
}

// MARK: - Error Types

enum ComplicationDataError: LocalizedError {
    case noActiveWallet
    case priceDataUnavailable
    case balanceDataUnavailable
    case networkError(String)
    
    var errorDescription: String? {
        switch self {
        case .noActiveWallet:
            return "沒有活躍的錢包"
        case .priceDataUnavailable:
            return "價格數據不可用"
        case .balanceDataUnavailable:
            return "餘額數據不可用"
        case .networkError(let message):
            return "網路錯誤: \(message)"
        }
    }
}
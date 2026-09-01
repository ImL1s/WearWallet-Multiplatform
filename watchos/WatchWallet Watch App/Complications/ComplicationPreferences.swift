//
//  ComplicationPreferences.swift
//  WatchWallet Watch App
//
//  Complication 偏好設定管理
//  處理用戶對 Watch Face Complications 的自定義設定
//

import Foundation

@MainActor
class ComplicationPreferences: ObservableObject {
    
    // MARK: - Singleton
    static let shared = ComplicationPreferences()
    
    // MARK: - Published Properties
    @Published var primaryTokenSymbol: String {
        didSet {
            UserDefaults.standard.set(primaryTokenSymbol, forKey: Keys.primaryTokenSymbol)
            notifyPreferencesChanged()
        }
    }
    
    @Published var showPriceChange: Bool {
        didSet {
            UserDefaults.standard.set(showPriceChange, forKey: Keys.showPriceChange)
            notifyPreferencesChanged()
        }
    }
    
    @Published var updateInterval: ComplicationUpdateInterval {
        didSet {
            UserDefaults.standard.set(updateInterval.rawValue, forKey: Keys.updateInterval)
            notifyPreferencesChanged()
        }
    }
    
    @Published var displayCurrency: DisplayCurrency {
        didSet {
            UserDefaults.standard.set(displayCurrency.rawValue, forKey: Keys.displayCurrency)
            notifyPreferencesChanged()
        }
    }
    
    @Published var enableBackgroundUpdates: Bool {
        didSet {
            UserDefaults.standard.set(enableBackgroundUpdates, forKey: Keys.enableBackgroundUpdates)
            notifyPreferencesChanged()
        }
    }
    
    // MARK: - Private Keys
    private enum Keys {
        static let primaryTokenSymbol = "complication_primary_token_symbol"
        static let showPriceChange = "complication_show_price_change"
        static let updateInterval = "complication_update_interval"
        static let displayCurrency = "complication_display_currency"
        static let enableBackgroundUpdates = "complication_enable_background_updates"
    }
    
    // MARK: - Initialization
    private init() {
        // 載入保存的設定或使用預設值
        self.primaryTokenSymbol = UserDefaults.standard.string(forKey: Keys.primaryTokenSymbol) ?? "ETH"
        self.showPriceChange = UserDefaults.standard.bool(forKey: Keys.showPriceChange)
        
        let intervalRaw = UserDefaults.standard.string(forKey: Keys.updateInterval) ?? ComplicationUpdateInterval.standard.rawValue
        self.updateInterval = ComplicationUpdateInterval(rawValue: intervalRaw) ?? .standard
        
        let currencyRaw = UserDefaults.standard.string(forKey: Keys.displayCurrency) ?? DisplayCurrency.usd.rawValue
        self.displayCurrency = DisplayCurrency(rawValue: currencyRaw) ?? .usd
        
        // 預設啟用背景更新
        self.enableBackgroundUpdates = UserDefaults.standard.object(forKey: Keys.enableBackgroundUpdates) as? Bool ?? true
        
        print("[ComplicationPreferences] 載入設定完成")
        print("  主要代幣: \(primaryTokenSymbol)")
        print("  顯示變化率: \(showPriceChange)")
        print("  更新間隔: \(updateInterval.displayName)")
        print("  顯示貨幣: \(displayCurrency.symbol)")
        print("  背景更新: \(enableBackgroundUpdates)")
    }
    
    // MARK: - Public Methods
    
    /**
     * 重置為預設設定
     */
    func resetToDefaults() {
        primaryTokenSymbol = "ETH"
        showPriceChange = false
        updateInterval = .standard
        displayCurrency = .usd
        enableBackgroundUpdates = true
        
        print("[ComplicationPreferences] ♻️ 已重置為預設設定")
    }
    
    /**
     * 獲取支援的代幣清單
     */
    func getSupportedTokens() -> [TokenOption] {
        return [
            TokenOption(symbol: "BTC", name: "Bitcoin", isDefault: false),
            TokenOption(symbol: "ETH", name: "Ethereum", isDefault: true),
            TokenOption(symbol: "BNB", name: "BNB", isDefault: false),
            TokenOption(symbol: "MATIC", name: "Polygon", isDefault: false),
            TokenOption(symbol: "CRO", name: "Cronos", isDefault: false),
            TokenOption(symbol: "USDT", name: "Tether", isDefault: false),
            TokenOption(symbol: "USDC", name: "USD Coin", isDefault: false)
        ]
    }
    
    /**
     * 檢查當前設定是否有效
     */
    func validateSettings() -> Bool {
        let supportedTokens = getSupportedTokens().map { $0.symbol }
        return supportedTokens.contains(primaryTokenSymbol)
    }
    
    /**
     * 獲取當前設定的摘要
     */
    func getSettingsSummary() -> [String: Any] {
        return [
            "primaryToken": primaryTokenSymbol,
            "showPriceChange": showPriceChange,
            "updateInterval": updateInterval.rawValue,
            "displayCurrency": displayCurrency.rawValue,
            "backgroundUpdates": enableBackgroundUpdates,
            "isValid": validateSettings()
        ]
    }
    
    // MARK: - Private Methods
    
    private func notifyPreferencesChanged() {
        // 通知 ComplicationUpdateService 設定已變更
        NotificationCenter.default.post(
            name: .complicationPreferencesDidChange,
            object: nil,
            userInfo: getSettingsSummary()
        )
        
        print("[ComplicationPreferences] 📢 設定已變更並通知相關服務")
    }
}

// MARK: - Supporting Types

enum ComplicationUpdateInterval: String, CaseIterable {
    case frequent = "frequent"     // 5分鐘
    case standard = "standard"     // 15分鐘
    case conservative = "conservative" // 30分鐘
    
    var timeInterval: TimeInterval {
        switch self {
        case .frequent: return 5 * 60
        case .standard: return 15 * 60
        case .conservative: return 30 * 60
        }
    }
    
    var displayName: String {
        switch self {
        case .frequent: return "頻繁 (5分鐘)"
        case .standard: return "標準 (15分鐘)"
        case .conservative: return "節能 (30分鐘)"
        }
    }
}

enum DisplayCurrency: String, CaseIterable {
    case usd = "USD"
    case eur = "EUR"
    case jpy = "JPY"
    case cny = "CNY"
    case twd = "TWD"
    
    var symbol: String {
        switch self {
        case .usd: return "$"
        case .eur: return "€"
        case .jpy: return "¥"
        case .cny: return "¥"
        case .twd: return "NT$"
        }
    }
    
    var displayName: String {
        switch self {
        case .usd: return "美元 (USD)"
        case .eur: return "歐元 (EUR)"
        case .jpy: return "日圓 (JPY)"
        case .cny: return "人民幣 (CNY)"
        case .twd: return "新台幣 (TWD)"
        }
    }
}

struct TokenOption: Identifiable {
    let id = UUID()
    let symbol: String
    let name: String
    let isDefault: Bool
    
    var displayName: String {
        return "\(symbol) - \(name)"
    }
}

// MARK: - Notification Names

extension Notification.Name {
    static let complicationPreferencesDidChange = Notification.Name("ComplicationPreferencesDidChange")
}

// MARK: - UserDefaults Extension

extension UserDefaults {
    
    /**
     * 批次設定 Complication 相關的偏好
     */
    func setComplicationPreferences(_ preferences: [String: Any]) {
        for (key, value) in preferences {
            set(value, forKey: "complication_\(key)")
        }
        synchronize()
    }
    
    /**
     * 獲取所有 Complication 相關偏好
     */
    func getComplicationPreferences() -> [String: Any] {
        let keys = [
            "primary_token_symbol",
            "show_price_change",
            "update_interval",
            "display_currency",
            "enable_background_updates"
        ]
        
        var preferences: [String: Any] = [:]
        for key in keys {
            if let value = object(forKey: "complication_\(key)") {
                preferences[key] = value
            }
        }
        
        return preferences
    }
}
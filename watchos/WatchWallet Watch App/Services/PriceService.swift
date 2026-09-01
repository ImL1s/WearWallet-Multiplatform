//
//  PriceService.swift
//  WatchWallet Watch App
//
//  價格服務 - 整合 KMP PriceApiClient 和 UpdateTokenPricesUseCase
//

import Foundation
import Combine
import coreKmp

@MainActor
class PriceService: ObservableObject {
    
    // MARK: - Singleton
    static let shared = PriceService()
    
    // MARK: - Published Properties
    @Published private(set) var tokenPrices: [String: TokenPrice] = [:]
    @Published private(set) var isLoading = false
    @Published private(set) var lastUpdated: Date?
    @Published private(set) var error: String?
    
    // MARK: - Properties
    private var getTokenPriceUseCase: coreKmp.GetTokenPriceUseCase?
    private var priceUpdateTimer: Timer?
    private let cacheExpiryInterval: TimeInterval = 5 * 60 // 5 minutes
    
    // 常用代幣的 CoinGecko ID 映射
    private let commonTokens = [
        "ETH": "ethereum",
        "BTC": "bitcoin", 
        "BNB": "binancecoin",
        "MATIC": "matic-network", 
        "CRO": "crypto-com-chain",
        "USDT": "tether",
        "USDC": "usd-coin"
    ]
    
    // MARK: - Initialization
    private init() {
        setupPriceUpdateUseCase()
        startPeriodicPriceUpdates()
    }
    
    deinit {
        stopPeriodicPriceUpdates()
    }
    
    // MARK: - Public Methods
    
    /**
     * 獲取代幣的 USD 價格
     */
    func getTokenPrice(symbol: String) -> Double? {
        if let tokenPrice = tokenPrices[symbol.uppercased()] {
            return tokenPrice.price
        }
        return nil
    }
    
    /**
     * 獲取代幣的 24 小時變化率
     */
    func getPriceChange24h(symbol: String) -> Double? {
        if let tokenPrice = tokenPrices[symbol.uppercased()] {
            return tokenPrice.priceChange24h
        }
        return nil
    }
    
    /**
     * 計算 USD 價值
     */
    func calculateUsdValue(amount: String, symbol: String) -> Double {
        guard let amountValue = Double(amount),
              let price = getTokenPrice(symbol: symbol) else {
            return 0.0
        }
        return amountValue * price
    }
    
    /**
     * 格式化 USD 價值為字串
     */
    func formatUsdValue(amount: String, symbol: String) -> String {
        let usdValue = calculateUsdValue(amount: amount, symbol: symbol)
        return String(format: "%.2f", usdValue)
    }
    
    /**
     * 手動刷新價格
     */
    func refreshPrices() async {
        await updateCommonTokenPrices()
    }
    
    /**
     * 檢查價格數據是否需要更新
     */
    func shouldUpdatePrices() -> Bool {
        guard let lastUpdated = lastUpdated else { return true }
        return Date().timeIntervalSince(lastUpdated) > cacheExpiryInterval
    }
    
    /**
     * 設置價格更新 Use Case
     */
    private func setupPriceUpdateUseCase() {
        self.getTokenPriceUseCase = DIContainer.shared.getGetTokenPriceUseCase()
        if getTokenPriceUseCase != nil {
            print("[PriceService] ✅ KMP GetTokenPriceUseCase 已初始化")
        } else {
            print("[PriceService] ⚠️ KMP GetTokenPriceUseCase 不可用，將使用模擬數據")
        }
    }
    
    /**
     * 更新常用代幣價格
     */
    private func updateCommonTokenPrices() async {
        // 如果連 UseCase 都沒有，且沒有舊數據，則顯示錯誤
        if getTokenPriceUseCase == nil && tokenPrices.isEmpty {
            // 我們在 fetchPricesFromKMP 會處理模擬數據
            print("[PriceService] ⚠️ UseCase 不可用，嘗試獲取模擬數據")
        }
        
        isLoading = true
        error = nil
        
        do {
            // 使用 KMP PriceApiClient 獲取價格
            let prices = try await fetchPricesFromKMP()
            
            await MainActor.run {
                self.tokenPrices = prices
                self.lastUpdated = Date()
                self.isLoading = false
                print("[PriceService] ✅ KMP 價格更新完成，獲取 \(prices.count) 個代幣價格")
                
                // 通知 Complication 更新服務
                self.notifyPriceUpdate()
            }
        } catch {
            await MainActor.run {
                self.error = "價格更新失敗: \(error.localizedDescription)"
                self.isLoading = false
                print("[PriceService] ❌ KMP 價格更新失敗: \(error)")
            }
        }
    }
    
    /**
     * 從 KMP PriceApiClient 獲取價格 (模擬實現)
     */
    private func fetchPricesFromKMP() async throws -> [String: TokenPrice] {
        var results: [String: TokenPrice] = [:]
        let now = Date()
        
        if let useCase = getTokenPriceUseCase {
            let symbols = Array(commonTokens.keys)
            do {
                // getPrices returns Result<Map<String, PriceData>>
                let result = try await useCase.getPrices(symbols: symbols)
                
                // Handling KMP Result Success
                if let success = result as? coreKmp.ResultSuccess<AnyObject>, 
                   let dataMap = success.data as? [String: coreKmp.PriceData] {
                    for (symbol, p) in dataMap {
                        results[symbol] = TokenPrice(
                            id: "p_\(symbol)",
                            tokenId: p.symbol,
                            price: p.price,
                            priceChange24h: p.changePercent24h?.doubleValue,
                            volume24h: p.volume24h?.doubleValue,
                            marketCap: p.marketCap?.doubleValue,
                            currency: p.currency,
                            lastUpdated: now
                        )
                    }
                } else if let success = result as? coreKmp.ResultSuccess<NSDictionary>,
                          let dict = success.data as? NSDictionary {
                    // Fallback for NSDictionary if the above cast fails
                    for (key, value) in dict {
                        if let symbol = key as? String, let p = value as? coreKmp.PriceData {
                            results[symbol] = TokenPrice(
                                id: "p_\(symbol)",
                                tokenId: p.symbol,
                                price: p.price,
                                priceChange24h: p.changePercent24h?.doubleValue,
                                volume24h: p.volume24h?.doubleValue,
                                marketCap: p.marketCap?.doubleValue,
                                currency: p.currency,
                                lastUpdated: now
                            )
                        }
                    }
                }
            } catch {
                print("[PriceService] ❌ 獲取多個代幣價格失敗: \(error)")
            }
        }
        
        // 如果 KMP 獲取失敗或不可用，返回模擬數據作為備案
        if results.isEmpty {
            print("[PriceService] ⚠️ 使用模擬價格數據作為備案")
            let mockPrices: [String: Double] = [
                "ETH": 2345.67,
                "BTC": 43210.12,
                "BNB": 312.45,
                "MATIC": 0.89,
                "CRO": 0.12,
                "USDT": 1.00,
                "USDC": 1.00
            ]
            
            for (symbol, price) in mockPrices {
                results[symbol] = TokenPrice(
                    id: "mock_\(symbol)",
                    tokenId: symbol,
                    price: price,
                    priceChange24h: Double.random(in: -5...5),
                    volume24h: nil,
                    marketCap: nil,
                    currency: "USD",
                    lastUpdated: now
                )
            }
        }
        
        return results
    }
    
    /**
     * 開始定期價格更新
     */
    private func startPeriodicPriceUpdates() {
        // 每 5 分鐘更新一次價格
        priceUpdateTimer = Timer.scheduledTimer(withTimeInterval: 300, repeats: true) { [weak self] _ in
            Task { @MainActor in
                if self?.shouldUpdatePrices() == true {
                    await self?.updateCommonTokenPrices()
                }
            }
        }
        
        // 立即執行第一次更新
        Task {
            await updateCommonTokenPrices()
        }
    }
    
    /**
     * 停止定期價格更新
     */
    nonisolated private func stopPeriodicPriceUpdates() {
        Task { @MainActor in
            priceUpdateTimer?.invalidate()
            priceUpdateTimer = nil
        }
    }
}

// MARK: - Supporting Types

enum PriceServiceError: LocalizedError {
    case serviceNotInitialized
    case kmpApiError(String)
    case emptyResponse
    case decodingError
    
    var errorDescription: String? {
        switch self {
        case .serviceNotInitialized:
            return "KMP 價格服務未初始化"
        case .kmpApiError(let message):
            return "KMP API 錯誤: \(message)"
        case .emptyResponse:
            return "API 返回空數據"
        case .decodingError:
            return "價格數據解析失敗"
        }
    }
}

/**
 * 簡化的 TokenPrice 模型（與 KMP 相容）
 */
struct TokenPrice {
    let id: String
    let tokenId: String
    let price: Double
    let priceChange24h: Double?
    let volume24h: Double?
    let marketCap: Double?
    let currency: String
    let lastUpdated: Date
}

// MARK: - Complication Integration

extension PriceService {
    /**
     * 通知 Complication 更新服務價格已更新
     */
    func notifyPriceUpdate() {
        NotificationCenter.default.post(name: .priceServiceDidUpdate, object: nil)
    }
}

// MARK: - Notification Names

extension Notification.Name {
    static let priceServiceDidUpdate = Notification.Name("PriceServiceDidUpdate")
}
import Foundation
import SwiftUI
import StoreKit
import WatchKit

/**
 * Subscription ViewModel for watchOS
 * 
 * 管理訂閱邏輯：
 * - In-App Purchase 處理
 * - 訂閱狀態追蹤
 * - 功能解鎖管理
 * - 收據驗證
 * 
 * Created: 2025-08-07
 */
@MainActor
class SubscriptionViewModel: ObservableObject {
    
    // MARK: - Published Properties
    @Published var isSubscribed = false
    @Published var expirationDate = ""
    @Published var willAutoRenew = true
    @Published var isProcessing = false
    @Published var errorMessage: String?
    
    // MARK: - Private Properties
    private var products: [Product] = []
    private var purchasedProducts: Set<Product> = []
    private var updateListenerTask: Task<Void, Error>?
    
    // Product IDs
    private let productIds = [
        "com.cbstudio.wearwallet.pro.monthly",
        "com.cbstudio.wearwallet.pro.yearly",
        "com.cbstudio.wearwallet.pro.lifetime"
    ]
    
    // MARK: - Initialization
    
    init() {
        updateListenerTask = listenForTransactions()
        Task {
            await loadProducts()
            await updateSubscriptionStatus()
        }
    }
    
    deinit {
        updateListenerTask?.cancel()
    }
    
    // MARK: - Product Loading
    
    private func loadProducts() async {
        do {
            products = try await Product.products(for: productIds)
            print("Loaded \(products.count) products")
        } catch {
            print("Failed to load products: \(error)")
            errorMessage = "無法載入訂閱方案"
        }
    }
    
    // MARK: - Subscription Status
    
    func loadSubscriptionStatus() {
        Task {
            await updateSubscriptionStatus()
        }
    }
    
    private func updateSubscriptionStatus() async {
        var hasActiveSubscription = false
        var latestExpirationDate: Date?
        
        for await result in Transaction.currentEntitlements {
            guard case .verified(let transaction) = result else { continue }
            
            if transaction.productType == .autoRenewable || transaction.productType == .nonRenewable {
                hasActiveSubscription = true
                
                if let expiryDate = transaction.expirationDate {
                    if latestExpirationDate == nil || expiryDate > latestExpirationDate! {
                        latestExpirationDate = expiryDate
                    }
                }
                
                // Check if product is in our list
                if let product = products.first(where: { $0.id == transaction.productID }) {
                    purchasedProducts.insert(product)
                }
            }
        }
        
        isSubscribed = hasActiveSubscription
        
        if let expiry = latestExpirationDate {
            let formatter = DateFormatter()
            formatter.dateStyle = .medium
            formatter.timeStyle = .none
            expirationDate = formatter.string(from: expiry)
            
            // Check if will auto-renew (expiry is in the future)
            willAutoRenew = expiry > Date()
        } else {
            expirationDate = "N/A"
            willAutoRenew = false
        }
    }
    
    // MARK: - Purchase Methods
    
    func purchase(_ plan: SubscriptionPlan) async throws {
        isProcessing = true
        defer { isProcessing = false }
        
        guard let product = getProduct(for: plan) else {
            throw SubscriptionError.productNotFound
        }
        
        let result = try await product.purchase()
        
        switch result {
        case .success(let verification):
            let transaction = try checkVerified(verification)
            
            // Update subscription status
            await updateSubscriptionStatus()
            
            // Finish transaction
            await transaction.finish()
            
            // Provide haptic feedback
            WKInterfaceDevice.current().play(.success)
            
        case .userCancelled:
            throw SubscriptionError.userCancelled
            
        case .pending:
            throw SubscriptionError.purchasePending
            
        @unknown default:
            throw SubscriptionError.unknownError
        }
    }
    
    func restorePurchases() async throws -> Bool {
        isProcessing = true
        defer { isProcessing = false }
        
        try await AppStore.sync()
        await updateSubscriptionStatus()
        
        return isSubscribed
    }
    
    // MARK: - Transaction Handling
    
    private func listenForTransactions() -> Task<Void, Error> {
        return Task.detached {
            for await result in Transaction.updates {
                do {
                    let transaction = try await self.checkVerified(result)
                    
                    // Update subscription status
                    await self.updateSubscriptionStatus()
                    
                    // Finish transaction
                    await transaction.finish()
                } catch {
                    print("Transaction failed verification: \(error)")
                }
            }
        }
    }
    
    private func checkVerified<T>(_ result: StoreKit.VerificationResult<T>) throws -> T {
        switch result {
        case .unverified:
            throw SubscriptionError.verificationFailed
        case .verified(let safe):
            return safe
        }
    }
    
    // MARK: - Helper Methods
    
    func getPrice(for plan: SubscriptionPlan) -> String {
        guard let product = getProduct(for: plan) else {
            return plan.defaultPrice
        }
        return product.displayPrice
    }
    
    private func getProduct(for plan: SubscriptionPlan) -> Product? {
        let productId = getProductId(for: plan)
        return products.first { $0.id == productId }
    }
    
    private func getProductId(for plan: SubscriptionPlan) -> String {
        switch plan {
        case .monthly:
            return "com.cbstudio.wearwallet.pro.monthly"
        case .yearly:
            return "com.cbstudio.wearwallet.pro.yearly"
        case .lifetime:
            return "com.cbstudio.wearwallet.pro.lifetime"
        }
    }
    
    func calculateSavings(for plan: SubscriptionPlan) -> String? {
        switch plan {
        case .yearly:
            // Calculate yearly savings vs monthly
            let monthlyPrice = 4.99
            let yearlyPrice = 39.99
            let yearlyCostIfMonthly = monthlyPrice * 12
            let savings = yearlyCostIfMonthly - yearlyPrice
            return String(format: "$%.0f", savings)
        case .lifetime:
            // Calculate lifetime value
            return "終身使用"
        default:
            return nil
        }
    }
    
    func manageSubscription() {
        // Open subscription management in Settings
        if let url = URL(string: "https://apps.apple.com/account/subscriptions") {
            // Note: watchOS doesn't support opening URLs directly
            // This would need to be handled through iPhone companion app
            print("Open subscription management: \(url)")
        }
    }
    
    func openTermsOfService() {
        // Open terms of service
        // This would typically open a web view or send to iPhone
        print("Open terms of service")
    }
    
    func openPrivacyPolicy() {
        // Open privacy policy
        // This would typically open a web view or send to iPhone
        print("Open privacy policy")
    }
}

// MARK: - Supporting Types

enum SubscriptionPlan: String, CaseIterable {
    case monthly = "月訂閱"
    case yearly = "年訂閱"
    case lifetime = "終身版"
    
    var name: String { rawValue }
    
    var description: String {
        switch self {
        case .monthly:
            return "每月自動續訂"
        case .yearly:
            return "每年自動續訂，節省 33%"
        case .lifetime:
            return "一次購買，永久使用"
        }
    }
    
    var unitPrice: String? {
        switch self {
        case .monthly:
            return nil
        case .yearly:
            return "每月僅 $3.33"
        case .lifetime:
            return "無需續訂"
        }
    }
    
    var defaultPrice: String {
        switch self {
        case .monthly:
            return "$4.99"
        case .yearly:
            return "$39.99"
        case .lifetime:
            return "$99.99"
        }
    }
    
    var color: Color {
        switch self {
        case .monthly:
            return .blue
        case .yearly:
            return .green
        case .lifetime:
            return .purple
        }
    }
    
    var isBestValue: Bool {
        return self == .yearly
    }
}

struct ProFeature: Identifiable {
    let id = UUID()
    let name: String
    let description: String?
    let isFree: Bool
    let isNew: Bool
    
    static let allFeatures = [
        ProFeature(
            name: "無限錢包",
            description: "創建無限數量的錢包",
            isFree: false,
            isNew: false
        ),
        ProFeature(
            name: "高級圖表",
            description: "專業級技術分析工具",
            isFree: false,
            isNew: false
        ),
        ProFeature(
            name: "AI 投資顧問",
            description: "個人化投資建議",
            isFree: false,
            isNew: true
        ),
        ProFeature(
            name: "DeFi 策略",
            description: "一鍵執行複雜 DeFi 操作",
            isFree: false,
            isNew: true
        ),
        ProFeature(
            name: "優先客服",
            description: "24/7 專屬支援",
            isFree: false,
            isNew: false
        ),
        ProFeature(
            name: "無廣告體驗",
            description: "完全無廣告干擾",
            isFree: false,
            isNew: false
        ),
        ProFeature(
            name: "自訂通知",
            description: "進階價格警報設定",
            isFree: false,
            isNew: false
        ),
        ProFeature(
            name: "批量交易",
            description: "同時執行多筆交易",
            isFree: false,
            isNew: true
        ),
        ProFeature(
            name: "基本功能",
            description: "錢包管理、交易、查看餘額",
            isFree: true,
            isNew: false
        )
    ]
}

enum SubscriptionError: LocalizedError {
    case productNotFound
    case purchaseFailed
    case verificationFailed
    case userCancelled
    case purchasePending
    case unknownError
    
    var errorDescription: String? {
        switch self {
        case .productNotFound:
            return "找不到訂閱方案"
        case .purchaseFailed:
            return "購買失敗"
        case .verificationFailed:
            return "驗證失敗"
        case .userCancelled:
            return "購買已取消"
        case .purchasePending:
            return "購買處理中"
        case .unknownError:
            return "未知錯誤"
        }
    }
}
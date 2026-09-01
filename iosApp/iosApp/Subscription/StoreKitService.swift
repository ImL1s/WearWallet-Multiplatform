import Foundation
import StoreKit

/**
 * StoreKit 2 訂閱服務
 * 處理 iOS/watchOS 端的 App Store 訂閱功能
 */
@MainActor
class StoreKitService: ObservableObject {
    
    // MARK: - Properties
    
    /// 可用產品列表
    @Published var availableProducts: [Product] = []
    
    /// 購買狀態
    @Published var purchaseState: PurchaseState = .idle
    
    /// 當前訂閱狀態
    @Published var subscriptionStatus: SubscriptionStatus = .notSubscribed
    
    /// 產品 ID 列表
    private let productIds: Set<String> = [
        "premium_monthly",
        "premium_yearly"
    ]
    
    /// 交易監聽任務
    private var transactionListener: Task<Void, Error>?
    
    // MARK: - Initialization
    
    init() {
        // 啟動交易監聽器
        startTransactionListener()
        
        // 載入產品和訂閱狀態  
        Task {
            await loadProducts()
            await updateSubscriptionStatus()
        }
    }
    
    deinit {
        transactionListener?.cancel()
    }
    
    // MARK: - Public Methods
    
    /// 載入可用產品
    func loadProducts() async {
        do {
            let products = try await Product.products(for: productIds)
            availableProducts = products.sorted { first, second in
                first.displayName < second.displayName
            }
        } catch {
            print(String(format: NSLocalizedString("storekit_failed_load", comment: ""), error.localizedDescription))
        }
    }
    
    /// 購買產品
    func purchase(_ product: Product) async -> PurchaseResult {
        purchaseState = .purchasing
        
        do {
            let result = try await product.purchase()
            
            switch result {
            case .success(let verification):
                // 驗證交易
                let transaction = try checkVerified(verification)
                
                // 完成交易
                await transaction.finish()
                
                // 更新訂閱狀態
                await updateSubscriptionStatus()
                
                purchaseState = .purchased
                return .success(transaction.originalID)
                
            case .userCancelled:
                purchaseState = .idle
                return .userCancelled
                
            case .pending:
                purchaseState = .pending
                return .pending
                
            @unknown default:
                purchaseState = .failed(NSLocalizedString("storekit_unknown_result", comment: ""))
                return .failed(NSLocalizedString("storekit_unknown_result", comment: ""))
            }
        } catch {
            purchaseState = .failed(error.localizedDescription)
            return .failed(error.localizedDescription)
        }
    }
    
    /// 恢復購買
    func restorePurchases() async {
        do {
            try await AppStore.sync()
            await updateSubscriptionStatus()
        } catch {
            purchaseState = .failed(String(format: NSLocalizedString("storekit_restore_failed", comment: ""), error.localizedDescription))
        }
    }
    
    /// 更新訂閱狀態
    func updateSubscriptionStatus() async {
        var highestStatus: Product.SubscriptionInfo.Status?
        var highestProduct: Product?
        
        // 檢查每個產品的訂閱狀態
        for product in availableProducts {
            guard let subscription = product.subscription else { continue }
            
            let statuses = try? await subscription.status
            
            for status in statuses ?? [] {
                switch status.state {
                case .subscribed:
                    if highestStatus == nil {
                        highestStatus = status
                        highestProduct = product
                    }
                case .expired, .revoked, .inGracePeriod, .inBillingRetryPeriod:
                    // 處理過期、撤銷等狀態
                    continue
                default:
                    continue
                }
            }
        }
        
        if let status = highestStatus, let product = highestProduct {
            // Get renewal date from transaction expiration
            let renewalDate: Date? = nil // StoreKit2 doesn't expose autoRenewOn directly
            subscriptionStatus = .subscribed(
                product: product,
                renewalDate: renewalDate
            )
        } else {
            subscriptionStatus = .notSubscribed
        }
    }
    
    /// 取消訂閱
    func cancelSubscription() async {
        // StoreKit 2 不直接支援取消訂閱
        // 需要引導用戶到 App Store 設定頁面
        if let url = URL(string: "https://apps.apple.com/account/subscriptions") {
            await MainActor.run {
                #if os(iOS)
                UIApplication.shared.open(url)
                #endif
            }
        }
    }
    
    // MARK: - Private Methods
    
    /// 啟動交易監聽器
    private func startTransactionListener() {
        transactionListener = Task.detached { [weak self] in
            for await result in Transaction.updates {
                do {
                    guard let self = self else { return }
                    let transaction = try await MainActor.run { try self.checkVerified(result) }
                    
                    // 處理新交易
                    await self.handleTransaction(transaction)
                    
                    // 完成交易
                    await transaction.finish()
                } catch {
                    print(String(format: NSLocalizedString("storekit_transaction_failed", comment: ""), error.localizedDescription))
                }
            }
        }
    }
    
    /// 處理交易
    private func handleTransaction(_ transaction: Transaction) async {
        // 根據交易類型更新本地狀態
        switch transaction.productType {
        case .autoRenewable:
            await updateSubscriptionStatus()
        default:
            break
        }
    }
    
    /// 驗證交易
    private func checkVerified<T>(_ result: VerificationResult<T>) throws -> T {
        switch result {
        case .unverified:
            throw StoreError.failedVerification
        case .verified(let safe):
            return safe
        }
    }
}

// MARK: - Supporting Types

/// 購買結果
enum PurchaseResult {
    case success(UInt64) // 交易 ID
    case userCancelled
    case pending
    case failed(String)
}

/// 購買狀態
enum PurchaseState {
    case idle
    case purchasing
    case purchased
    case pending
    case failed(String)
}

/// 訂閱狀態
enum SubscriptionStatus {
    case notSubscribed
    case subscribed(product: Product, renewalDate: Date?)
}

/// Store 錯誤
enum StoreError: Error {
    case failedVerification
}

// MARK: - Extensions

extension Product {
    /// 格式化價格顯示
    var displayPrice: String {
        return displayName + " - " + displayPrice
    }
}

extension Transaction {
    /// 將交易轉換為收據資料字串
    var receiptData: String {
        // 簡化的收據資料，實際應用中需要更完整的資訊
        let receiptInfo: [String: Any] = [
            "transaction_id": String(id),
            "original_transaction_id": String(originalID),
            "product_id": productID,
            "purchase_date": purchaseDate.timeIntervalSince1970,
            "expires_date": expirationDate?.timeIntervalSince1970 ?? 0
        ]
        
        if let jsonData = try? JSONSerialization.data(withJSONObject: receiptInfo),
           let jsonString = String(data: jsonData, encoding: .utf8) {
            return jsonString
        }
        
        return ""
    }
}
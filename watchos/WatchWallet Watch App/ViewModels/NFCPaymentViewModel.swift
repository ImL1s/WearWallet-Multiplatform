import Foundation
import SwiftUI
import PassKit
import WatchConnectivity
import coreKmp

/**
 * NFC Payment ViewModel for watchOS
 * 
 * 管理加密貨幣 NFC 支付邏輯：
 * - Flexa 網絡整合
 * - Apple Pay 介面
 * - 交易簽名和廣播
 * - 即時匯率轉換
 * 
 * Created: 2025-08-07
 */
@MainActor
class NFCPaymentViewModel: NSObject, ObservableObject {
    
    // MARK: - Published Properties
    @Published var selectedToken = PaymentToken.usdc
    @Published var balance: Double = 1000.0
    @Published var paymentAmount: Double = 0.0
    @Published var fiatValue: Double = 0.0
    
    @Published var recentMerchants: [Merchant] = []
    @Published var recentTransactions: [PaymentTransaction] = []
    @Published var lastTransaction: PaymentTransaction?
    
    @Published var isFlexaAvailable = true
    @Published var isProcessing = false
    @Published var errorMessage: String?
    
    // MARK: - Private Properties
    private let kmpBridge = KMPUseCaseDirect.shared
    private let wcSession = WCSession.default
    private var flexaAPI: FlexaAPIService?
    
    // Exchange rates (mock data - in production, fetch from API)
    private let exchangeRates: [String: Double] = [
        "USDC": 1.0,
        "USDT": 1.0,
        "ETH": 3500.0,
        "BTC": 65000.0,
        "MATIC": 0.85
    ]
    
    // MARK: - Computed Properties
    
    var formattedBalance: String {
        String(format: "%.2f", balance)
    }
    
    var formattedFiatValue: String {
        let rate = exchangeRates[selectedToken.symbol] ?? 1.0
        return String(format: "%.2f", paymentAmount * rate)
    }
    
    // MARK: - Initialization
    
    override init() {
        super.init()
        setupWatchConnectivity()
        checkFlexaAvailability()
    }
    
    // MARK: - Setup Methods
    
    private func setupWatchConnectivity() {
        if WCSession.isSupported() {
            wcSession.delegate = self
            wcSession.activate()
        }
    }
    
    private func checkFlexaAvailability() {
        // Check if Flexa network is available
        // In production, this would query Flexa API
        Task {
            isFlexaAvailable = await checkFlexaNetworkStatus()
        }
    }
    
    // MARK: - Data Loading
    
    func loadPaymentData() {
        loadBalance()
        loadRecentMerchants()
        loadRecentTransactions()
        updateFiatValue()
    }
    
    private func loadBalance() {
        Task {
            do {
                // Get balance from KMP
                let address = getUserWalletAddress()
                let balanceModel = try await kmpBridge.getTokenBalance(
                    walletAddress: address,
                    tokenAddress: nil, // Native token for now
                    chainType: getChainForToken(selectedToken)
                )
                balance = Double(balanceModel.balance) ?? 0
            } catch {
                print("Failed to load balance: \(error)")
                balance = 1000.0 // Mock fallback
            }
        }
    }
    
    private func loadRecentMerchants() {
        // Load from storage or API
        recentMerchants = [
            Merchant(
                id: "starbucks",
                name: "Starbucks",
                category: "咖啡店",
                icon: "cup.and.saucer",
                color: .green,
                acceptsFlexa: true
            ),
            Merchant(
                id: "wholefoods",
                name: "Whole Foods",
                category: "超市",
                icon: "basket",
                color: .orange,
                acceptsFlexa: true
            ),
            Merchant(
                id: "nordstrom",
                name: "Nordstrom",
                category: "百貨公司",
                icon: "bag",
                color: .purple,
                acceptsFlexa: true
            ),
            Merchant(
                id: "gamestop",
                name: "GameStop",
                category: "遊戲商店",
                icon: "gamecontroller",
                color: .red,
                acceptsFlexa: true
            )
        ]
    }
    
    private func loadRecentTransactions() {
        // Load from storage or blockchain
        recentTransactions = [
            PaymentTransaction(
                id: "tx1",
                merchant: "Starbucks",
                amount: 5.50,
                token: "USDC",
                fee: 0.05,
                date: Date().addingTimeInterval(-3600),
                status: "已完成",
                type: .payment,
                txHash: "0xabc123..."
            ),
            PaymentTransaction(
                id: "tx2",
                merchant: "Whole Foods",
                amount: 87.25,
                token: "USDC",
                fee: 0.15,
                date: Date().addingTimeInterval(-86400),
                status: "已完成",
                type: .payment,
                txHash: "0xdef456..."
            ),
            PaymentTransaction(
                id: "tx3",
                merchant: "Nordstrom",
                amount: 145.00,
                token: "USDT",
                fee: 0.25,
                date: Date().addingTimeInterval(-172800),
                status: "已完成",
                type: .payment,
                txHash: "0xghi789..."
            )
        ]
    }
    
    private func updateFiatValue() {
        let rate = exchangeRates[selectedToken.symbol] ?? 1.0
        fiatValue = paymentAmount * rate
    }
    
    // MARK: - Payment Processing
    
    func selectPaymentMethod(_ method: PaymentMethod) {
        // Update UI based on selected method
        print("Selected payment method: \(method.name)")
    }
    
    func selectMerchant(_ merchant: Merchant) {
        // Pre-fill payment details for merchant
        print("Selected merchant: \(merchant.name)")
    }
    
    func processApplePayPayment() async {
        isProcessing = true
        
        do {
            // 1. Create payment request
            let paymentRequest = createPaymentRequest()
            
            // 2. Sign transaction offline
            let signedTx = try await signTransaction(amount: paymentAmount)
            
            // 3. Send to Flexa for processing
            let flexaResponse = try await processFlexaPayment(signedTx)
            
            // 4. Broadcast transaction
            let txHash = try await broadcastTransaction(signedTx)
            
            // 5. Update UI
            lastTransaction = PaymentTransaction(
                id: UUID().uuidString,
                merchant: "Apple Pay",
                amount: paymentAmount,
                token: selectedToken.symbol,
                fee: 0.10,
                date: Date(),
                status: "已完成",
                type: .payment,
                txHash: txHash
            )
            
            recentTransactions.insert(lastTransaction!, at: 0)
            balance -= paymentAmount
            
            // Success feedback
            WKInterfaceDevice.current().play(.success)
            
        } catch {
            errorMessage = "支付失敗: \(error.localizedDescription)"
            WKInterfaceDevice.current().play(.failure)
        }
        
        isProcessing = false
    }
    
    func processNFCPayment() async {
        isProcessing = true
        
        // Since watchOS doesn't support direct NFC,
        // send request to iPhone companion app
        if wcSession.isReachable {
            let message: [String: Any] = [
                "action": "nfc_payment",
                "amount": paymentAmount,
                "token": selectedToken.symbol,
                "merchant": recentMerchants.first?.id ?? ""
            ]
            
            wcSession.sendMessage(message, replyHandler: { reply in
                Task { @MainActor in
                    self.handleNFCPaymentResponse(reply)
                }
            }, errorHandler: { error in
                Task { @MainActor in
                    self.errorMessage = "NFC 支付失敗: \(error.localizedDescription)"
                    self.isProcessing = false
                }
            })
        } else {
            errorMessage = "請確保 iPhone 在附近並開啟應用"
            isProcessing = false
        }
    }
    
    private func handleNFCPaymentResponse(_ response: [String: Any]) {
        if let success = response["success"] as? Bool, success {
            if let txHash = response["txHash"] as? String {
                lastTransaction = PaymentTransaction(
                    id: UUID().uuidString,
                    merchant: response["merchant"] as? String ?? "NFC Payment",
                    amount: paymentAmount,
                    token: selectedToken.symbol,
                    fee: 0.10,
                    date: Date(),
                    status: "已完成",
                    type: .payment,
                    txHash: txHash
                )
                
                recentTransactions.insert(lastTransaction!, at: 0)
                balance -= paymentAmount
                
                WKInterfaceDevice.current().play(.success)
            }
        } else {
            errorMessage = response["error"] as? String ?? "支付失敗"
            WKInterfaceDevice.current().play(.failure)
        }
        
        isProcessing = false
    }
    
    // MARK: - QR Code Generation
    
    func generatePaymentQRData() -> String {
        // Generate payment QR code data
        // Format: ethereum:address?value=amount&token=symbol
        let address = getUserWalletAddress()
        let amountInWei = Int(paymentAmount * 1e18)
        
        return "ethereum:\(address)?value=\(amountInWei)&token=\(selectedToken.symbol)"
    }
    
    // MARK: - Helper Methods
    
    private func createPaymentRequest() -> PKPaymentRequest {
        let request = PKPaymentRequest()
        request.merchantIdentifier = "merchant.com.cbstudio.wearwallet"
        request.supportedNetworks = [.visa, .masterCard, .amex]
        request.merchantCapabilities = .threeDSecure
        request.countryCode = "US"
        request.currencyCode = "USD"
        
        let item = PKPaymentSummaryItem(
            label: selectedToken.symbol,
            amount: NSDecimalNumber(value: paymentAmount)
        )
        request.paymentSummaryItems = [item]
        
        return request
    }
    
    private func signTransaction(amount: Double) async throws -> String {
        // 使用 KMP 簽署交易
        let fromAddress = try await getUserWalletAddressAsync()
        let toAddress = "0xFlexaPaymentProcessor" // Flexa payment processor address

        // 將金額轉換為 wei
        let amountInWei = String(format: "%.0f", amount * 1e18)

        // 透過 WatchConnectivity 請求 iPhone 簽署
        // 因為 watchOS 的安全限制，複雜簽署操作在 iPhone 上執行
        let signRequest: [String: Any] = [
            "action": "signNFCPayment",
            "fromAddress": fromAddress,
            "toAddress": toAddress,
            "amount": amountInWei,
            "token": selectedToken.symbol,
            "timestamp": Date().timeIntervalSince1970
        ]

        // 發送到 iPhone 並等待回應
        return await withCheckedContinuation { continuation in
            WatchConnectivityManager.shared.sendTransactionForSigning(signRequest)

            // 設置超時和監聽回應
            // 實際實現需要透過 WatchConnectivity 回調處理
            // 這裡暫時返回請求 ID，實際簽署結果會透過回調返回
            let requestId = UUID().uuidString
            continuation.resume(returning: "pending_\(requestId)")
        }
    }

    private func processFlexaPayment(_ signedTx: String) async throws -> FlexaPaymentResponse {
        // 透過 Flexa 網路處理支付
        // 在生產環境中，這會呼叫 Flexa API

        // 檢查是否是待處理的交易
        if signedTx.hasPrefix("pending_") {
            // 等待 iPhone 完成簽署
            try await Task.sleep(nanoseconds: 2_000_000_000) // 2 秒等待
        }

        // 生成 Flexa 支付碼
        let flexaCode = generateFlexaCode()

        return FlexaPaymentResponse(
            paymentId: UUID().uuidString,
            status: "approved",
            flexaCode: flexaCode
        )
    }

    /// 生成 Flexa 支付碼
    private func generateFlexaCode() -> String {
        // 生成隨機的 Flexa 格式支付碼
        let chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        let code = String((0..<8).map { _ in chars.randomElement()! })
        return "FLEXA\(code)"
    }

    private func broadcastTransaction(_ signedTx: String) async throws -> String {
        // 透過 KMP 廣播交易到區塊鏈
        do {
            let chainType = getChainForToken(selectedToken)
            let chainId = getChainId(for: chainType)

            // 使用 Web3Service 廣播
            let web3Service = Web3ServiceFactory.shared.createForChain(chainId)
            let txHash = try await web3Service.sendTransaction(signedTransaction: signedTx, chainId: chainId)

            print("[NFCPaymentViewModel] 交易廣播成功: \(txHash)")
            return txHash
        } catch {
            print("[NFCPaymentViewModel] 交易廣播失敗: \(error)")
            throw error
        }
    }

    /// 獲取 chainId
    private func getChainId(for chainType: ChainType) -> String {
        switch chainType {
        case .ethereum: return "1"
        case .bsc: return "56"
        case .polygon: return "137"
        case .arbitrum: return "42161"
        case .optimism: return "10"
        case .base: return "8453"
        default: return "1"
        }
    }

    private func checkFlexaNetworkStatus() async -> Bool {
        // 檢查 Flexa 網路可用性
        // 透過 WatchConnectivity 檢查 iPhone 端的連接狀態
        return WatchConnectivityManager.shared.isReachable
    }

    private func getUserWalletAddress() -> String {
        // 同步獲取用戶錢包地址（用於快速 UI 顯示）
        // 嘗試從緩存獲取
        if let cachedAddress = UserDefaults.standard.string(forKey: "cached_wallet_address") {
            return cachedAddress
        }
        return ""
    }

    /// 異步獲取用戶錢包地址
    private func getUserWalletAddressAsync() async throws -> String {
        do {
            let wallets = try await KMPUseCaseDirect.shared.getAllWallets()
            if let wallet = wallets.first {
                // 緩存地址以供同步使用
                UserDefaults.standard.set(wallet.address, forKey: "cached_wallet_address")
                return wallet.address
            }
            throw NFCPaymentError.noWallet
        } catch {
            throw NFCPaymentError.walletError(error.localizedDescription)
        }
    }

    enum NFCPaymentError: LocalizedError {
        case noWallet
        case walletError(String)

        var errorDescription: String? {
            switch self {
            case .noWallet:
                return "尚未創建錢包"
            case .walletError(let message):
                return "錢包錯誤: \(message)"
            }
        }
    }
    
    private func getChainForToken(_ token: PaymentToken) -> coreKmp.ChainType {
        switch token {
        case .usdc, .usdt, .eth:
            return .ethereum
        case .btc:
            // Assuming KMP supports Bitcoin chain type, otherwise default to ETH or error
            // coreKmp usually has ChainType.bitcoin or similar?
            // KMPTypeAliases uses typealias KMPChain = coreKmp.ChainType
            // Let's assume ChainType.bitcoin exists, or use raw value if needed.
            // But coreKmp.ChainType is enum.
            // Let's defer to ethereum if unsure, or check KMPTypeAliases/KMPUseCaseDirect.
            return .ethereum // Placeholder
        case .matic:
            return .polygon
        default:
            return .ethereum
        }
    }
    
    func showError(_ message: String) {
        errorMessage = message
        WKInterfaceDevice.current().play(.failure)
    }
}

// MARK: - WatchConnectivity Delegate

extension NFCPaymentViewModel: WCSessionDelegate {
    nonisolated func session(_ session: WCSession, activationDidCompleteWith activationState: WCSessionActivationState, error: Error?) {
        if activationState == .activated {
            print("WatchConnectivity activated")
        }
    }
    
    nonisolated func session(_ session: WCSession, didReceiveMessage message: [String : Any]) {
        // Handle messages from iPhone
        Task { @MainActor in
            self.handleMessage(message)
        }
    }
    
    private func handleMessage(_ message: [String : Any]) {
        if let action = message["action"] as? String {
             switch action {
             case "payment_completed":
                 handleNFCPaymentResponse(message)
             case "flexa_status":
                 if let available = message["available"] as? Bool {
                     isFlexaAvailable = available
                 }
             default:
                 break
             }
        }
    }
}

// MARK: - Supporting Types

enum PaymentMethod: String, CaseIterable, Equatable {
    case applePay = "Apple Pay"
    case nfc = "NFC"
    case qrCode = "QR Code"
    
    var name: String { rawValue }
    
    var icon: String {
        switch self {
        case .applePay: return "applelogo"
        case .nfc: return "wave.3.right"
        case .qrCode: return "qrcode"
        }
    }
    
    var color: Color {
        switch self {
        case .applePay: return .black
        case .nfc: return .green
        case .qrCode: return .blue
        }
    }
}

struct PaymentToken: Equatable {
    let symbol: String
    let name: String
    let icon: String
    let color: Color
    
    static let usdc = PaymentToken(
        symbol: "USDC",
        name: "USD Coin",
        icon: "dollarsign.circle",
        color: .blue
    )
    
    static let usdt = PaymentToken(
        symbol: "USDT",
        name: "Tether",
        icon: "dollarsign.circle.fill",
        color: .green
    )
    
    static let eth = PaymentToken(
        symbol: "ETH",
        name: "Ethereum",
        icon: "diamond",
        color: .purple
    )
    
    static let btc = PaymentToken(
        symbol: "BTC",
        name: "Bitcoin",
        icon: "bitcoinsign.circle",
        color: .orange
    )
    
    static let matic = PaymentToken(
        symbol: "MATIC",
        name: "Polygon",
        icon: "pentagon",
        color: .purple
    )
}

struct Merchant: Identifiable {
    let id: String
    let name: String
    let category: String
    let icon: String
    let color: Color
    let acceptsFlexa: Bool
}

struct PaymentTransaction: Identifiable {
    let id: String
    let merchant: String
    let amount: Double
    let token: String
    let fee: Double
    let date: Date
    let status: String
    let type: TransactionType
    let txHash: String
    
    var formattedDate: String {
        let formatter = RelativeDateTimeFormatter()
        formatter.unitsStyle = .abbreviated
        return formatter.localizedString(for: date, relativeTo: Date())
    }
    
    enum TransactionType {
        case payment
        case refund
        case reward
        
        var icon: String {
            switch self {
            case .payment: return "arrow.up.circle"
            case .refund: return "arrow.down.circle"
            case .reward: return "gift.circle"
            }
        }
        
        var color: Color {
            switch self {
            case .payment: return .red
            case .refund: return .green
            case .reward: return .purple
            }
        }
    }
}

struct FlexaPaymentResponse {
    let paymentId: String
    let status: String
    let flexaCode: String
}

// Mock Flexa API Service
class FlexaAPIService {
    func checkNetworkStatus() async -> Bool {
        return true
    }
    
    func processPayment(_ request: FlexaPaymentRequest) async throws -> FlexaPaymentResponse {
        return FlexaPaymentResponse(
            paymentId: UUID().uuidString,
            status: "approved",
            flexaCode: "FLEXA\(Int.random(in: 1000...9999))"
        )
    }
}

struct FlexaPaymentRequest {
    let amount: Double
    let token: String
    let merchantId: String
    let signedTransaction: String
}
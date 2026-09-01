//
//  AIAssistantViewModel.swift
//  WatchWallet Watch App
//
//  AI 助手 ViewModel - 管理 AI 交互邏輯
//  整合 KMP AI 服務和 watchOS UI 狀態管理
//

import SwiftUI
import coreKmp
import Combine

@MainActor
class AIAssistantViewModel: ObservableObject {

    // MARK: - Published Properties
    @Published var conversationHistory: [ConversationMessage] = []
    @Published var isProcessing = false
    @Published var usageStats = AIUsageStats(
        todayRequests: 0,
        remainingRequests: 20,
        monthlyUsage: 0,
        estimatedCost: 0.0
    )
    @Published var errorMessage: String?

    // MARK: - Navigation Properties (供 View 觀察)
    @Published var navigateToTransactionHistory = false
    @Published var navigateToPortfolio = false
    @Published var navigateToReceive = false
    @Published var showQRCode = false
    @Published var qrCodeAddress: String = ""

    // MARK: - Properties
    private let aiService: WatchOSGeminiBridge
    private var cancellables = Set<AnyCancellable>()

    // MARK: - Storage Keys
    private let conversationHistoryKey = "ai_conversation_history"
    
    // MARK: - Initialization
    init() {
        // 初始化 watchOS AI 服務
        self.aiService = WatchOSGeminiBridge()
        
        setupInitialState()
        loadConversationHistory()
    }
    
    // MARK: - Public Methods
    
    /**
     * 處理用戶命令
     */
    func processCommand(_ command: String) async {
        guard !command.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
        guard !isProcessing else { return }
        
        isProcessing = true
        errorMessage = nil
        
        // 添加用戶消息到對話歷史
        let userMessage = ConversationMessage(
            id: UUID().uuidString,
            content: command,
            isUser: true,
            source: .user,
            timestamp: Date()
        )
        conversationHistory.append(userMessage)
        
        do {
            // 調用 watchOS AI 服務
            let response = await aiService.processCommand(command)
            
            // 轉換為 AICommandResult
            let result = AICommandResult(
                action: parseWalletAction(from: response.action),
                response: response.response,
                confidence: response.confidence,
                source: .geminiApi
            )
            
            // 處理 AI 回應
            await handleAIResult(result)
            
            // 更新使用統計
            loadUsageStats()
            
        } catch {
            let errorMessage = ConversationMessage(
                id: UUID().uuidString,
                content: "處理指令時出現錯誤：\(error.localizedDescription)",
                isUser: false,
                source: .error,
                timestamp: Date()
            )
            conversationHistory.append(errorMessage)
            
            self.errorMessage = error.localizedDescription
            print("[AIAssistantViewModel] 錯誤：\(error)")
        }
        
        isProcessing = false
        saveConversationHistory()
    }
    
    /**
     * 執行特定的錢包操作
     */
    func executeWalletAction(_ action: WalletAction) async {
        switch action {
        case let .checkBalance(token):
            await handleBalanceCheck(token: token)
            
        case .showTransactionHistory:
            await handleTransactionHistory()
            
        case .showPortfolio:
            await handlePortfolio()
            
        case let .generateQRCode(address):
            await handleQRCodeGeneration(address: address)
            
        case let .sendTransaction(toAddress, amount, token):
            await handleSendTransaction(toAddress: toAddress, amount: amount, token: token)
            
        case let .setGasPrice(level):
            await handleGasPriceSettings(level: level)
            
        case .unknown:
            let message = ConversationMessage(
                id: UUID().uuidString,
                content: "抱歉，我無法理解這個指令。請嘗試更具體的描述。",
                isUser: false,
                source: .assistant,
                timestamp: Date()
            )
            conversationHistory.append(message)
            
        default:
            let message = ConversationMessage(
                id: UUID().uuidString,
                content: "抱歉，暫時不支援此操作。",
                isUser: false,
                source: .assistant,
                timestamp: Date()
            )
            conversationHistory.append(message)
        }
        
        saveConversationHistory()
    }
    
    /**
     * 解析錢包操作
     */
    private func parseWalletAction(from action: String) -> WalletAction {
        switch action {
        case "checkBalance":
            return .checkBalance(token: nil)
        case "showTransactionHistory":
            return .showTransactionHistory
        case "showPortfolio":
            return .showPortfolio
        default:
            return .unknown
        }
    }
    
    /**
     * 清除對話歷史
     */
    func clearHistory() {
        conversationHistory.removeAll()
        saveConversationHistory()
    }
    
    /**
     * 載入使用統計
     */
    func loadUsageStats() {
        // watchOS AI 服務暫時沒有使用統計
        self.usageStats = AIUsageStats(
            todayRequests: 0,
            remainingRequests: 20,
            monthlyUsage: 0,
            estimatedCost: 0.0
        )
    }
    
    /**
     * 分析交易風險
     */
    func analyzeTransactionRisk(toAddress: String, amount: String, chainId: String) async -> TransactionRiskAnalysis? {
        // watchOS AI 服務暫時沒有風險分析功能
        print("[AIAssistantViewModel] 風險分析暫時不可用")
        return nil
    }
    
    // MARK: - Private Methods
    
    private func setupInitialState() {
        // 設置初始歡迎消息
        let welcomeMessage = ConversationMessage(
            id: "welcome",
            content: "您好！我是 WearWallet AI 助手。您可以問我關於錢包餘額、交易記錄或發送代幣的問題。",
            isUser: false,
            source: .assistant,
            timestamp: Date()
        )
        conversationHistory.append(welcomeMessage)
    }
    
    private func handleAIResult(_ result: AICommandResult) async {
        // 添加 AI 回應到對話歷史
        let aiMessage = ConversationMessage(
            id: UUID().uuidString,
            content: result.response,
            isUser: false,
            source: MessageSource.from(aiSource: result.source),
            timestamp: Date()
        )
        conversationHistory.append(aiMessage)
        
        // 如果有具體的操作，執行它
        if let action = result.action {
            await executeWalletAction(action)
        }
    }
    
    // MARK: - 錢包操作處理方法
    
    private func handleBalanceCheck(token: String?) async {
        let message = if let token = token {
            "正在查詢 \(token) 餘額..."
        } else {
            "正在查詢總餘額..."
        }

        let responseMessage = ConversationMessage(
            id: UUID().uuidString,
            content: message,
            isUser: false,
            source: .system,
            timestamp: Date()
        )
        conversationHistory.append(responseMessage)

        // 透過 KMP 查詢實際餘額
        do {
            let wallets = try await KMPUseCaseDirect.shared.getAllWallets()

            guard let firstWallet = wallets.first else {
                let noWalletMessage = ConversationMessage(
                    id: UUID().uuidString,
                    content: "尚未創建錢包，請先創建或導入錢包。",
                    isUser: false,
                    source: .system,
                    timestamp: Date()
                )
                conversationHistory.append(noWalletMessage)
                return
            }

            // 獲取餘額
            let balanceToken = try await KMPUseCaseDirect.shared.getTokenBalance(
                walletAddress: firstWallet.address,
                tokenAddress: nil, // 原生代幣
                chainType: .ethereum
            )

            let formattedBalance = formatBalance(balanceToken.balance, decimals: balanceToken.decimals)
            let resultContent: String

            if let token = token {
                resultContent = "\(token) 餘額：\(formattedBalance) \(balanceToken.symbol)"
            } else {
                // 獲取所有代幣計算總值
                let tokens = try await KMPUseCaseDirect.shared.getUserTokens(
                    address: firstWallet.address,
                    chainType: .ethereum
                )

                if tokens.isEmpty {
                    resultContent = "總餘額：\(formattedBalance) \(balanceToken.symbol)"
                } else {
                    let tokenList = tokens.prefix(3).map { "\($0.symbol): \(formatBalance($0.balance, decimals: $0.decimals))" }.joined(separator: ", ")
                    resultContent = "資產概覽：\(tokenList)"
                }
            }

            let resultMessage = ConversationMessage(
                id: UUID().uuidString,
                content: resultContent,
                isUser: false,
                source: .system,
                timestamp: Date()
            )
            conversationHistory.append(resultMessage)

        } catch {
            let errorMsg = ConversationMessage(
                id: UUID().uuidString,
                content: "無法獲取餘額：\(error.localizedDescription)",
                isUser: false,
                source: .error,
                timestamp: Date()
            )
            conversationHistory.append(errorMsg)
        }
    }

    /// 格式化餘額顯示
    private func formatBalance(_ balance: String, decimals: Int) -> String {
        guard let value = Double(balance) else { return balance }
        let divisor = pow(10.0, Double(decimals))
        let formatted = value / divisor
        return String(format: "%.4f", formatted)
    }
    
    private func handleTransactionHistory() async {
        let message = ConversationMessage(
            id: UUID().uuidString,
            content: "正在載入交易歷史...",
            isUser: false,
            source: .system,
            timestamp: Date()
        )
        conversationHistory.append(message)

        // 獲取實際交易歷史
        do {
            let wallets = try await KMPUseCaseDirect.shared.getAllWallets()
            guard let wallet = wallets.first else {
                navigateToTransactionHistory = true
                return
            }

            let transactions = try await KMPUseCaseDirect.shared.getTransactionHistory(
                address: wallet.address,
                chainType: .ethereum,
                page: 1,
                limit: 3
            )

            if transactions.isEmpty {
                let noTxMessage = ConversationMessage(
                    id: UUID().uuidString,
                    content: "暫無交易記錄。",
                    isUser: false,
                    source: .system,
                    timestamp: Date()
                )
                conversationHistory.append(noTxMessage)
            } else {
                let txSummary = transactions.prefix(3).map { tx in
                    let direction = tx.from.lowercased() == wallet.address.lowercased() ? "發送" : "接收"
                    return "\(direction) \(tx.value) - \(tx.status)"
                }.joined(separator: "\n")

                let txMessage = ConversationMessage(
                    id: UUID().uuidString,
                    content: "最近交易：\n\(txSummary)",
                    isUser: false,
                    source: .system,
                    timestamp: Date()
                )
                conversationHistory.append(txMessage)
            }

            // 觸發導航
            navigateToTransactionHistory = true

        } catch {
            navigateToTransactionHistory = true
        }
    }

    private func handlePortfolio() async {
        let message = ConversationMessage(
            id: UUID().uuidString,
            content: "正在載入投資組合...",
            isUser: false,
            source: .system,
            timestamp: Date()
        )
        conversationHistory.append(message)

        // 獲取實際投資組合
        do {
            let wallets = try await KMPUseCaseDirect.shared.getAllWallets()
            guard let wallet = wallets.first else {
                navigateToPortfolio = true
                return
            }

            let tokens = try await KMPUseCaseDirect.shared.getUserTokens(
                address: wallet.address,
                chainType: .ethereum
            )

            let portfolioSummary: String
            if tokens.isEmpty {
                portfolioSummary = "投資組合：暫無代幣資產"
            } else {
                let tokenList = tokens.prefix(5).map { "\($0.symbol): \(formatBalance($0.balance, decimals: $0.decimals))" }.joined(separator: ", ")
                portfolioSummary = "投資組合（\(tokens.count) 種代幣）：\(tokenList)"
            }

            let portfolioMessage = ConversationMessage(
                id: UUID().uuidString,
                content: portfolioSummary,
                isUser: false,
                source: .system,
                timestamp: Date()
            )
            conversationHistory.append(portfolioMessage)

            // 觸發導航
            navigateToPortfolio = true

        } catch {
            navigateToPortfolio = true
        }
    }

    private func handleQRCodeGeneration(address: String) async {
        let message = ConversationMessage(
            id: UUID().uuidString,
            content: "正在生成收款 QR 碼...",
            isUser: false,
            source: .system,
            timestamp: Date()
        )
        conversationHistory.append(message)

        // 設置 QR 碼地址並觸發顯示
        if !address.isEmpty {
            qrCodeAddress = address
        } else {
            // 如果沒有提供地址，使用錢包地址
            do {
                let wallets = try await KMPUseCaseDirect.shared.getAllWallets()
                if let wallet = wallets.first {
                    qrCodeAddress = wallet.address
                }
            } catch {
                qrCodeAddress = ""
            }
        }

        if !qrCodeAddress.isEmpty {
            showQRCode = true
            navigateToReceive = true

            let successMessage = ConversationMessage(
                id: UUID().uuidString,
                content: "QR 碼已生成，地址：\(qrCodeAddress.prefix(10))...\(qrCodeAddress.suffix(6))",
                isUser: false,
                source: .system,
                timestamp: Date()
            )
            conversationHistory.append(successMessage)
        }
    }

    private func handleSendTransaction(toAddress: String, amount: String, token: String) async {
        let message = ConversationMessage(
            id: UUID().uuidString,
            content: "發送交易需要確認，正在準備...",
            isUser: false,
            source: .assistant,
            timestamp: Date()
        )
        conversationHistory.append(message)

        // 透過 WatchConnectivity 通知 iPhone 處理
        // 因為複雜交易在 iPhone 上更安全
        let transactionRequest: [String: Any] = [
            "action": "prepareTransaction",
            "toAddress": toAddress,
            "amount": amount,
            "token": token,
            "timestamp": Date().timeIntervalSince1970
        ]

        WatchConnectivityManager.shared.sendTransactionForSigning(transactionRequest)

        let handoffMessage = ConversationMessage(
            id: UUID().uuidString,
            content: "已通知 iPhone 準備交易。請在 iPhone 上確認並簽名。",
            isUser: false,
            source: .system,
            timestamp: Date()
        )
        conversationHistory.append(handoffMessage)

        // 設置 Handoff Activity
        setupHandoffActivity(for: toAddress, amount: amount, token: token)
    }

    /// 設置 Handoff Activity
    private func setupHandoffActivity(for toAddress: String, amount: String, token: String) {
        let activity = NSUserActivity(activityType: "com.wearwallet.sendTransaction")
        activity.title = "發送 \(amount) \(token)"
        activity.userInfo = [
            "toAddress": toAddress,
            "amount": amount,
            "token": token
        ]
        activity.isEligibleForHandoff = true
        activity.becomeCurrent()
    }
    
    private func handleGasPriceSettings(level: String) async {
        let message = ConversationMessage(
            id: UUID().uuidString,
            content: "Gas 費用已設置為 \(level) 模式。",
            isUser: false,
            source: .system,
            timestamp: Date()
        )
        conversationHistory.append(message)
    }
    
    // MARK: - 數據持久化
    
    private func saveConversationHistory() {
        // 為了 watchOS 記憶體限制，只保存最近 20 條消息
        let maxMessages = 20
        if conversationHistory.count > maxMessages {
            conversationHistory = Array(conversationHistory.suffix(maxMessages))
        }

        // 使用 UserDefaults 存儲對話歷史
        do {
            let encoder = JSONEncoder()
            encoder.dateEncodingStrategy = .iso8601
            let data = try encoder.encode(conversationHistory)
            UserDefaults.standard.set(data, forKey: conversationHistoryKey)
            print("[AIAssistantViewModel] 對話歷史已保存 (\(conversationHistory.count) 條)")
        } catch {
            print("[AIAssistantViewModel] 保存對話歷史失敗：\(error)")
        }
    }

    private func loadConversationHistory() {
        // 從 UserDefaults 載入對話歷史
        guard let data = UserDefaults.standard.data(forKey: conversationHistoryKey) else {
            print("[AIAssistantViewModel] 無已保存的對話歷史")
            return
        }

        do {
            let decoder = JSONDecoder()
            decoder.dateDecodingStrategy = .iso8601
            let history = try decoder.decode([ConversationMessage].self, from: data)
            conversationHistory = history
            print("[AIAssistantViewModel] 對話歷史已載入 (\(history.count) 條)")
        } catch {
            print("[AIAssistantViewModel] 載入對話歷史失敗：\(error)")
        }
    }
}

// MARK: - Supporting Types

/**
 * 對話消息結構
 */
struct ConversationMessage: Identifiable, Codable {
    let id: String
    let content: String
    let isUser: Bool
    let source: MessageSource
    let timestamp: Date
}

/**
 * 消息來源
 */
enum MessageSource: String, Codable, CaseIterable {
    case user = "user"
    case assistant = "assistant"
    case system = "system"
    case local = "local"
    case cached = "cached"
    case error = "error"
    
    var displayName: String {
        switch self {
        case .user: return "您"
        case .assistant: return "AI助手"
        case .system: return "系統"
        case .local: return "本地"
        case .cached: return "快取"
        case .error: return "錯誤"
        }
    }
    
    var icon: String {
        switch self {
        case .user: return "person.circle"
        case .assistant: return "brain.head.profile"
        case .system: return "gear.circle"
        case .local: return "internaldrive"
        case .cached: return "memorychip"
        case .error: return "exclamationmark.triangle"
        }
    }
    
    var color: Color {
        switch self {
        case .user: return .blue
        case .assistant: return .green
        case .system: return .orange
        case .local: return .purple
        case .cached: return .gray
        case .error: return .red
        }
    }
    
    static func from(aiSource: AISource) -> MessageSource {
        switch aiSource {
        case .localRules: return .local
        case .geminiApi: return .assistant
        case .cached: return .cached
        case .fallback: return .system
        case .watchOS: return .assistant
        case .relay: return .system
        default: return .system
        }
    }
}

// MARK: - Extensions

extension AIUsageStats {
    var usagePercentage: Double {
        let total = todayRequests + remainingRequests
        guard total > 0 else { return 0 }
        return Double(todayRequests) / Double(total)
    }
    
    var isNearLimit: Bool {
        return remainingRequests <= 3
    }
    
    var statusColor: Color {
        if isNearLimit {
            return .red
        } else if usagePercentage > 0.7 {
            return .orange
        } else {
            return .green
        }
    }
}

// MARK: - Additional Types

struct TransactionRiskAnalysis {
    let riskLevel: String
    let warnings: [String]
    let suggestions: [String]
}
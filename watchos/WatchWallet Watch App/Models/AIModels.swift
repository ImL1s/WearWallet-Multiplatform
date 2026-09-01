//
//  AIModels.swift
//  WatchWallet Watch App
//
//  AI 服務相關的資料模型
//  Updated for compatibility with AIAssistantViewModel
//

import Foundation

// MARK: - AI 使用統計
struct AIUsageStats {
    let todayRequests: Int
    let remainingRequests: Int
    let monthlyUsage: Int
    let estimatedCost: Double
}

// MARK: - AI 命令結果
struct AICommandResult {
    // Compatibility fields
    let action: WalletAction?  // Changed to optional to match old model or use .unknown
    let response: String       // Renamed from message
    let confidence: Double
    let source: AISource
    
    // Original fields maintained for compatibility or unused?
    // AIAssistantViewModel expects: action, response, confidence, source
    // WatchOSAIServiceHelper creates: success, message, action, data.
    // We will align to AIAssistantViewModel.
    
    var success: Bool {
        return confidence > 0.5
    }
    
    var data: [String: Any]? // Optional extra data
}

// MARK: - 錢包操作
enum WalletAction {
    // Standard actions
    case checkBalance(token: String?)
    case showTransactionHistory // Renamed from viewTransactionHistory
    case showPortfolio
    case generateQRCode(address: String)
    case sendTransaction(toAddress: String, amount: String, token: String)
    case setGasPrice(level: String)
    
    // Additional actions from old AIModels
    case connectWallet
    case disconnectWallet
    case showNFTs
    case bridgeAsset(from: String, to: String, amount: String)
    case stakeTokens(amount: String, validator: String?)
    case swapTokens(from: String, to: String, amount: String)
    
    case unknown
    
    // Deprecated/Aliased cases for compatibility if needed?
    // Swift enums don't support aliases well. We should update usage or map.
    // usage in WatchOSAIServiceAdapter needs update.
}


// MARK: - AI 來源
enum AISource: String {
    case geminiApi = "Gemini" // Renamed from gemini
    case openai = "OpenAI"
    case claude = "Claude"
    case localRules = "Local" // Renamed from local
    case cached = "Cached"
    case fallback = "Fallback"
    case watchOS = "WatchOS"
    case relay = "iPhone Relay"
}

// MARK: - AI 服務協議
protocol WearWalletAIService {
    func processCommand(_ command: String) async throws -> AICommandResult
    func getUsageStats() async throws -> AIUsageStats
}

// MARK: - AI 服務工廠
class WearWalletAIServiceFactory {
    static let shared = WearWalletAIServiceFactory()
    
    init() {}
    
    func create() -> WearWalletAIService {
        // 使用 Firebase AI 服務（透過 WatchConnectivity 中繼）
        return WatchOSAIServiceAdapter()
    }
}

// MARK: - WatchOS AI 服務適配器
class WatchOSAIServiceAdapter: WearWalletAIService {
    private let firebaseService = FirebaseAIService()
    
    func processCommand(_ command: String) async throws -> AICommandResult {
        let analysis = await firebaseService.analyzeVoiceCommand(command)
        
        // 將分析結果轉換為錢包操作
        let action: WalletAction = parseWalletAction(from: analysis.intent, entities: analysis.entities)
        
        return AICommandResult(
            action: action,
            response: "已處理指令: \(command)", // Use analysis response if available?
            confidence: analysis.confidence,
            source: .watchOS,
            data: analysis.entities
        )
    }
    
    
    func getUsageStats() async throws -> AIUsageStats {
        // 預設統計資料
        return AIUsageStats(
            todayRequests: 5,
            remainingRequests: 15,
            monthlyUsage: 150,
            estimatedCost: 0.05
        )
    }
    
    private func parseWalletAction(from intent: String, entities: [String: Any]) -> WalletAction {
        switch intent.uppercased() {
        case "SEND_TRANSACTION":
            let to = entities["recipient"] as? String ?? ""
            let amount = entities["amount"] as? String ?? "0"
            let token = entities["token"] as? String ?? "ETH"
            return .sendTransaction(toAddress: to, amount: amount, token: token)
            
        case "CHECK_BALANCE":
            let token = entities["token"] as? String
            return .checkBalance(token: token)
            
        case "VIEW_HISTORY", "SHOW_HISTORY":
            return .showTransactionHistory
            
        case "SHOW_PORTFOLIO":
             return .showPortfolio
            
        case "CONNECT_WALLET":
            return .connectWallet
            
        case "DISCONNECT_WALLET":
            return .disconnectWallet
            
        case "SHOW_NFTS":
            return .showNFTs
            
        case "BRIDGE_ASSET":
            let from = entities["fromChain"] as? String ?? ""
            let to = entities["toChain"] as? String ?? ""
            let amount = entities["amount"] as? String ?? "0"
            return .bridgeAsset(from: from, to: to, amount: amount)
            
        case "STAKE_TOKENS":
            let amount = entities["amount"] as? String ?? "0"
            let validator = entities["validator"] as? String
            return .stakeTokens(amount: amount, validator: validator)
            
        case "SWAP_TOKENS":
            let from = entities["fromToken"] as? String ?? ""
            let to = entities["toToken"] as? String ?? ""
            let amount = entities["amount"] as? String ?? "0"
            return .swapTokens(from: from, to: to, amount: amount)
            
        default:
            return .unknown
        }
    }
}
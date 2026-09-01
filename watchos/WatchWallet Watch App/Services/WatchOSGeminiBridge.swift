//
//  WatchOSGeminiBridge.swift
//  WatchWallet Watch App
//
//  Kotlin-Swift 互操作橋接服務
//  連接 KMP AI 服務與 Firebase AI 實現
//
//  更新 (2025-08-07): watchOS 版本修正
//  - 移除直接 Firebase 導入（watchOS 不支援）
//  - 透過 WatchConnectivity 中繼至 iPhone
//  - 實現錯誤處理和降級機制
//  - watchOS 優化的記憶體管理
//

import Foundation

/**
 * 全域 Firebase AI 服務實例
 */
private var firebaseAIService: FirebaseAIService?

// MARK: - Data Types (本地定義，因為 watchOS 無法直接使用 Firebase)

struct FirebaseAIResponse {
    let action: String
    let response: String
    let confidence: Double
    let source: String
}

struct FirebaseRiskAnalysis {
    let level: String
    let score: Float
    let warning: String?
    
    static func defaultSafe() -> FirebaseRiskAnalysis {
        return FirebaseRiskAnalysis(level: "low", score: 10, warning: nil)
    }
}

/**
 * WatchOSGeminiBridge 類別
 * 提供 Swift 端的 AI 服務橋接
 */
class WatchOSGeminiBridge {
    
    init() {
        initializeFirebaseAI()
    }
    
    func processCommand(_ command: String) async -> FirebaseAIResponse {
        // 使用 FirebaseAIService 處理指令
        if let service = firebaseAIService {
            let analysis = await service.analyzeVoiceCommand(command)
            return FirebaseAIResponse(
                action: analysis.intent,
                response: "指令已處理",
                confidence: analysis.confidence,
                source: "watchOS"
            )
        }
        
        return FirebaseAIResponse(
            action: "unknown",
            response: "Service temporarily unavailable",
            confidence: 0.0,
            source: "watchOS"
        )
    }
    
    // MARK: - New AI Methods for Voice Assistant
    
    func analyzeVoiceCommand(_ command: String) async -> CommandAnalysis {
        if let service = firebaseAIService {
            return await service.analyzeVoiceCommand(command)
        }
        return CommandAnalysis(intent: "UNKNOWN", entities: [:], confidence: 0.0, riskLevel: "LOW")
    }
    
    func verifyTransactionVisually(txHash: String) async -> VerificationResult {
        if let service = firebaseAIService {
            return await service.verifyTransactionVisually(txHash: txHash)
        }
        return VerificationResult(status: "ERROR", riskScore: 0.0, warnings: ["服務未初始化"])
    }
    
    func auditSmartContract(address: String) async -> AuditResult {
        if let service = firebaseAIService {
            return await service.auditSmartContract(address: address)
        }
        return AuditResult(securityScore: 0, issues: ["服務未初始化"], recommendations: [])
    }
    
    func analyzeNFT(tokenId: String) async -> NFTAnalysis {
        if let service = firebaseAIService {
            return await service.analyzeNFT(tokenId: tokenId)
        }
        return NFTAnalysis(rarity: 0.0, estimatedValue: 0.0, attributes: [])
    }
}

/**
 * 初始化 Firebase AI 服務
 * 在應用啟動時調用
 */
@_cdecl("swift_initialize_firebase_ai")
public func initializeFirebaseAI() {
    // watchOS 使用 WatchConnectivity 中繼
    // 不需要直接配置 Firebase
    
    // 初始化 Firebase AI 服務（透過 WatchConnectivity）
    firebaseAIService = FirebaseAIService()
    
    print("[WatchOSGeminiBridge] Firebase AI 服務已初始化（透過 iPhone 中繼）")
}

/**
 * 處理錢包指令的 C 接口函數
 * 供 Kotlin/Native 調用
 */
@_cdecl("swift_process_wallet_command")
public func processWalletCommand(command: UnsafePointer<CChar>) -> UnsafePointer<CChar>? {
    guard let commandString = String(cString: command, encoding: .utf8) else {
        return createCStringResponse(json: createErrorResponse(message: "指令解析失敗"))
    }
    
    guard let aiService = firebaseAIService else {
        return createCStringResponse(json: createErrorResponse(message: "AI 服務未初始化"))
    }
    
    // 使用同步調用避免複雜的異步處理
    let semaphore = DispatchSemaphore(value: 0)
    var result: String?
    
    Task {
        // 分析命令
        let analysis = await aiService.analyzeVoiceCommand(commandString)
        
        // 將 CommandAnalysis 轉換為 FirebaseAIResponse
        let response = FirebaseAIResponse(
            action: analysis.intent,
            response: "指令已處理",
            confidence: analysis.confidence,
            source: "watchOS"
        )
        
        result = convertToKotlinFormat(response)
        semaphore.signal()
    }
    
    // 10 秒超時
    let timeoutResult = semaphore.wait(timeout: .now() + 10.0)
    
    if timeoutResult == .timedOut {
        result = createTimeoutResponse()
    }
    
    return createCStringResponse(json: result ?? createErrorResponse(message: "未知錯誤"))
}

/**
 * 分析交易風險的 C 接口函數
 */
@_cdecl("swift_analyze_transaction_risk")
public func analyzeTransactionRisk(toAddress: UnsafePointer<CChar>, amount: UnsafePointer<CChar>) -> UnsafePointer<CChar>? {
    guard let addressString = String(cString: toAddress, encoding: .utf8),
          let amountString = String(cString: amount, encoding: .utf8) else {
        return createCStringResponse(json: createRiskAnalysisResponse(level: "high", score: 90, warning: "參數解析失敗"))
    }
    
    guard let aiService = firebaseAIService else {
        return createCStringResponse(json: createRiskAnalysisResponse(level: "medium", score: 50, warning: "AI 服務未初始化"))
    }
    
    let semaphore = DispatchSemaphore(value: 0)
    var result: String?
    
    Task {
        // 使用視覺交易驗證來分析風險
        let verification = await aiService.verifyTransactionVisually(txHash: addressString)
        
        // 將 VerificationResult 轉換為 FirebaseRiskAnalysis
        let riskLevel: String
        if verification.riskScore < 30 {
            riskLevel = "low"
        } else if verification.riskScore < 70 {
            riskLevel = "medium"
        } else {
            riskLevel = "high"
        }
        
        let analysis = FirebaseRiskAnalysis(
            level: riskLevel,
            score: verification.riskScore,
            warning: verification.warnings.first
        )
        
        result = convertRiskAnalysisToKotlinFormat(analysis)
        semaphore.signal()
    }
    
    let timeoutResult = semaphore.wait(timeout: .now() + 8.0)
    
    if timeoutResult == .timedOut {
        result = createRiskAnalysisResponse(level: "medium", score: 60, warning: "分析超時")
    }
    
    return createCStringResponse(json: result ?? createRiskAnalysisResponse(level: "medium", score: 50, warning: "未知錯誤"))
}

/**
 * 清理 C 字符串記憶體的輔助函數
 * 在 Kotlin 端調用完成後需要調用此函數釋放記憶體
 */
@_cdecl("swift_free_cstring")
public func freeCString(ptr: UnsafePointer<CChar>) {
    free(UnsafeMutableRawPointer(mutating: ptr))
}

// MARK: - Private Helper Functions

/**
 * 將 Firebase AI 回應轉換為 Kotlin 期望的格式
 */
private func convertToKotlinFormat(_ response: FirebaseAIResponse) -> String {
    return """
    {
      "action": "\(response.action)",
      "response": "\(response.response)",
      "confidence": \(response.confidence),
      "source": "\(response.source)"
    }
    """
}

/**
 * 將 Firebase 風險分析轉換為 Kotlin 格式
 */
private func convertRiskAnalysisToKotlinFormat(_ analysis: FirebaseRiskAnalysis) -> String {
    if let warning = analysis.warning {
        return """
        {
          "level": "\(analysis.level)",
          "score": \(Int(analysis.score)),
          "warning": "\(warning)"
        }
        """
    } else {
        return """
        {
          "level": "\(analysis.level)",
          "score": \(Int(analysis.score))
        }
        """
    }
}

/**
 * 創建錯誤回應
 */
private func createErrorResponse(message: String) -> String {
    return """
    {
      "action": "unknown",
      "response": "\(message)",
      "confidence": 0.1,
      "source": "error"
    }
    """
}

/**
 * 創建超時回應
 */
private func createTimeoutResponse() -> String {
    return """
    {
      "action": "unknown",
      "response": "請求超時",
      "confidence": 0.2,
      "source": "timeout"
    }
    """
}

/**
 * 創建風險分析回應 JSON
 */
private func createRiskAnalysisResponse(level: String, score: Int, warning: String?) -> String {
    if let warning = warning {
        return """
        {
          "level": "\(level)",
          "score": \(score),
          "warning": "\(warning)"
        }
        """
    } else {
        return """
        {
          "level": "\(level)",
          "score": \(score)
        }
        """
    }
}

/**
 * 創建 C 字符串回應
 */
private func createCStringResponse(json: String) -> UnsafePointer<CChar>? {
    // Convert mutable pointer to immutable pointer
    guard let mutablePointer = strdup(json) else { return nil }
    return UnsafePointer(mutablePointer)
}

// MARK: - Global Service Access

/**
 * 獲取全域 Firebase AI 服務實例
 * 用於直接 Swift 調用 (非 Kotlin 互操作)
 */
func getFirebaseAIService() -> FirebaseAIService? {
    return firebaseAIService
}

/**
 * 直接的 Swift 介面 (用於 SwiftUI)
 */
@available(watchOS 6.0, *)
class WatchOSGeminiServiceWrapper: ObservableObject {
    
    private let firebaseService: FirebaseAIService?
    
    init() {
        self.firebaseService = firebaseAIService
    }
    
    func processCommand(_ command: String) async -> FirebaseAIResponse {
        guard let service = firebaseService else {
            return FirebaseAIResponse(
                action: "unknown",
                response: "服務未初始化",
                confidence: 0.1,
                source: "error"
            )
        }
        
        // 使用 analyzeVoiceCommand 分析命令
        let analysis = await service.analyzeVoiceCommand(command)
        
        return FirebaseAIResponse(
            action: analysis.intent,
            response: "指令已處理",
            confidence: analysis.confidence,
            source: "watchOS"
        )
    }
    
    func analyzeRisk(toAddress: String, amount: String) async -> FirebaseRiskAnalysis {
        guard let service = firebaseService else {
            return FirebaseRiskAnalysis.defaultSafe()
        }
        
        // 使用視覺交易驗證來分析風險
        let verification = await service.verifyTransactionVisually(txHash: toAddress)
        
        let riskLevel: String
        if verification.riskScore < 30 {
            riskLevel = "low"
        } else if verification.riskScore < 70 {
            riskLevel = "medium"
        } else {
            riskLevel = "high"
        }
        
        return FirebaseRiskAnalysis(
            level: riskLevel,
            score: verification.riskScore,
            warning: verification.warnings.first
        )
    }
}
//
//  WatchConnectivityManager.swift
//  WatchWallet Watch App
//
//  跨設備通信管理器 - 處理 watchOS 與 iPhone 之間的通信
//  包含 Keystone 3 Pro 硬體錢包整合支援
//

import Foundation
import WatchConnectivity
import Combine

class WatchConnectivityManager: NSObject, ObservableObject {
    static let shared = WatchConnectivityManager()
    
    // MARK: - Published Properties
    @Published var isReachable = false
    @Published var lastReceivedMessage: [String: Any] = [:]
    @Published var scannedQRCode: String?
    @Published var receivedClipboardContent: String?
    @Published var connectionError: String?
    
    // MARK: - Combine Publishers
    let isConnected = CurrentValueSubject<Bool, Never>(false)
    let keystoneConnectResults = PassthroughSubject<String, Never>()
    let keystoneSignResults = PassthroughSubject<String, Never>()
    
    private let session: WCSession
    private var cancellables = Set<AnyCancellable>()
    
    // MARK: - Message Types
    private enum MessageType {
        static let keystoneConnectRequest = "keystone_connect_request"
        static let keystoneConnectResult = "keystone_connect_result"
        static let keystoneSignRequest = "keystone_sign_request"
        static let keystoneSignResult = "keystone_sign_result"
        static let qrCodeScan = "scanQRCode"
        static let signTransaction = "signTransaction"
        static let syncWallets = "syncWallets"
    }
    
    private override init() {
        self.session = WCSession.default
        super.init()
        
        if WCSession.isSupported() {
            session.delegate = self
            session.activate()
        }
        
        setupConnectionMonitoring()
    }
    
    // MARK: - Keystone Integration Methods
    
    /**
     * 請求 iPhone 掃描 Keystone 連接 QR 碼
     */
    func requestKeystoneConnectScan() async -> Bool {
        guard session.isReachable else {
            DispatchQueue.main.async {
                self.connectionError = "iPhone 不可達，請確保 iPhone 端應用已開啟"
            }
            print("❌ iPhone 不可達，無法發送 Keystone 連接請求")
            return false
        }
        
        let message: [String: Any] = [
            "type": MessageType.keystoneConnectRequest,
            "action": MessageType.keystoneConnectRequest,
            "timestamp": Date().timeIntervalSince1970,
            "data": [
                "requestType": "connect_keystone"
            ]
        ]
        
        return await withCheckedContinuation { continuation in
            session.sendMessage(message) { response in
                print("✅ Keystone 連接請求已發送，iPhone 回應: \(response)")
                continuation.resume(returning: true)
            } errorHandler: { error in
                print("❌ 發送 Keystone 連接請求失敗: \(error.localizedDescription)")
                DispatchQueue.main.async {
                    self.connectionError = "無法連接到 iPhone: \(error.localizedDescription)"
                }
                continuation.resume(returning: false)
            }
        }
    }
    
    /**
     * 請求 iPhone 掃描 Keystone 簽名 QR 碼
     */
    func requestKeystoneSignScan(signRequest: [String: Any]) async -> Bool {
        guard session.isReachable else {
            DispatchQueue.main.async {
                self.connectionError = "iPhone 不可達，請確保 iPhone 端應用已開啟"
            }
            print("❌ iPhone 不可達，無法發送 Keystone 簽名請求")
            return false
        }
        
        let message: [String: Any] = [
            "type": MessageType.keystoneSignRequest,
            "action": MessageType.keystoneSignRequest,
            "timestamp": Date().timeIntervalSince1970,
            "data": signRequest
        ]
        
        return await withCheckedContinuation { continuation in
            session.sendMessage(message) { response in
                print("✅ Keystone 簽名請求已發送，iPhone 回應: \(response)")
                continuation.resume(returning: true)
            } errorHandler: { error in
                print("❌ 發送 Keystone 簽名請求失敗: \(error.localizedDescription)")
                DispatchQueue.main.async {
                    self.connectionError = "無法連接到 iPhone: \(error.localizedDescription)"
                }
                continuation.resume(returning: false)
            }
        }
    }
    
    // MARK: - Original Public Methods
    
    // Request QR code scanning from iPhone
    func requestQRCodeScan() {
        guard session.isReachable else {
            print("iPhone is not reachable")
            return
        }
        
        let message = ["action": "scanQRCode"]
        session.sendMessage(message, replyHandler: { reply in
            if let qrCode = reply["qrCode"] as? String {
                DispatchQueue.main.async {
                    self.scannedQRCode = qrCode
                }
            }
        }, errorHandler: { error in
            print("Error sending message: \(error.localizedDescription)")
        })
    }
    
    // Send transaction to iPhone for signing
    func sendTransactionForSigning(_ transaction: [String: Any]) {
        guard session.isReachable else {
            print("iPhone is not reachable")
            return
        }
        
        var message = transaction
        message["action"] = "signTransaction"
        
        session.sendMessage(message, replyHandler: { reply in
            print("Transaction signing response: \(reply)")
        }, errorHandler: { error in
            print("Error sending transaction: \(error.localizedDescription)")
        })
    }
    
    // Request wallet sync from iPhone
    func requestWalletSync() {
        guard session.isReachable else {
            print("iPhone is not reachable")
            return
        }
        
        let message = ["action": "syncWallets"]
        session.sendMessage(message, replyHandler: { reply in
            print("Wallet sync response: \(reply)")
        }, errorHandler: { error in
            print("Error syncing wallets: \(error.localizedDescription)")
        })
    }
    
    // Copy text to iPhone clipboard
    func copyToClipboard(_ text: String) {
        guard session.isReachable else {
            print("iPhone is not reachable for clipboard copy")
            return
        }
        
        let message = ["action": "copyToClipboard", "text": text]
        session.sendMessage(message, replyHandler: nil) { error in
            print("Error sending clipboard text: \(error.localizedDescription)")
        }
    }
    
    // MARK: - Connection Monitoring
    
    /**
     * 設置連接狀態監控
     */
    private func setupConnectionMonitoring() {
        // 更新連接狀態
        updateConnectionStatus()
    }
    
    /**
     * 更新連接狀態
     */
    private func updateConnectionStatus() {
        let connected = session.activationState == .activated && session.isReachable
        
        isConnected.send(connected)
        isReachable = connected
        
        print("📱 連接狀態更新: \(connected ? "已連接" : "未連接")")
        print("   - 激活狀態: \(session.activationState.rawValue)")
        print("   - 是否可達: \(session.isReachable)")
        if session.activationState == .activated {
            // Note: isPaired and isWatchAppInstalled are iOS-only APIs
            print("   - 手錶端已激活")
        }
    }
    
    /**
     * 處理收到的 Keystone 消息
     */
    private func handleKeystoneMessage(_ message: [String: Any]) {
        guard let messageType = message["type"] as? String else {
            print("❌ 收到無效的 Keystone 消息：缺少 type 字段")
            return
        }
        
        print("📨 收到 Keystone 消息類型: \(messageType)")
        
        switch messageType {
        case MessageType.keystoneConnectResult:
            handleKeystoneConnectResult(message)
            
        case MessageType.keystoneSignResult:
            handleKeystoneSignResult(message)
            
        default:
            print("⚠️ 未知的 Keystone 消息類型: \(messageType)")
        }
    }
    
    /**
     * 處理 Keystone 連接結果
     */
    private func handleKeystoneConnectResult(_ message: [String: Any]) {
        guard let data = message["data"] as? [String: Any],
              let urData = data["urData"] as? String else {
            print("❌ Keystone 連接結果格式無效")
            return
        }
        
        print("✅ 收到 Keystone 連接結果: \(urData.prefix(50))...")
        keystoneConnectResults.send(urData)
    }
    
    /**
     * 處理 Keystone 簽名結果
     */
    private func handleKeystoneSignResult(_ message: [String: Any]) {
        guard let data = message["data"] as? [String: Any],
              let urData = data["urData"] as? String else {
            print("❌ Keystone 簽名結果格式無效")
            return
        }
        
        print("✅ 收到 Keystone 簽名結果: \(urData.prefix(50))...")
        keystoneSignResults.send(urData)
    }
}

// MARK: - WCSessionDelegate
extension WatchConnectivityManager: WCSessionDelegate {
    func session(_ session: WCSession, activationDidCompleteWith activationState: WCSessionActivationState, error: Error?) {
        DispatchQueue.main.async {
            if let error = error {
                print("❌ WatchConnectivity 激活失敗: \(error.localizedDescription)")
                self.connectionError = "連接失敗: \(error.localizedDescription)"
            } else {
                print("✅ WatchConnectivity 激活成功，狀態: \(activationState.rawValue)")
                self.connectionError = nil
            }
            
            self.updateConnectionStatus()
        }
    }
    
    func sessionReachabilityDidChange(_ session: WCSession) {
        DispatchQueue.main.async {
            print("📡 可達性狀態變化: \(session.isReachable)")
            self.updateConnectionStatus()
        }
    }
    
    func session(_ session: WCSession, didReceiveMessage message: [String : Any]) {
        DispatchQueue.main.async {
            self.lastReceivedMessage = message
            
            // 首先檢查是否為 Keystone 相關消息
            if let messageType = message["type"] as? String,
               messageType.contains("keystone") {
                self.handleKeystoneMessage(message)
                return
            }
            
            // 處理其他消息類型
            if let action = message["action"] as? String {
                switch action {
                case "qrCodeScanned":
                    if let qrCode = message["data"] as? String {
                        self.scannedQRCode = qrCode
                    }
                case "clipboardUpdate":
                    if let content = message["content"] as? String {
                        self.receivedClipboardContent = content
                    }
                case "walletUpdated":
                    // Handle wallet updates
                    NotificationCenter.default.post(name: .walletUpdated, object: nil, userInfo: message)
                case "transactionUpdate":
                    // Handle transaction updates
                    NotificationCenter.default.post(name: .transactionUpdate, object: nil, userInfo: message)
                case MessageType.keystoneConnectResult:
                    // 處理 Keystone 連接結果（舊格式兼容）
                    self.handleKeystoneMessage(message)
                case MessageType.keystoneSignResult:
                    // 處理 Keystone 簽名結果（舊格式兼容）
                    self.handleKeystoneMessage(message)
                default:
                    print("📨 收到未知動作: \(action)")
                    break
                }
            }
        }
    }
    
    // Send QR Code data to iPhone to display
    func sendQRToPhone(_ qrData: String) {
        guard session.isReachable else {
            print("iPhone is not reachable for QR send")
            return
        }
        
        let message = ["action": "displayQRCode", "qrData": qrData]
        session.sendMessage(message, replyHandler: nil) { error in
            print("Error sending QR code to phone: \(error.localizedDescription)")
        }
    }
    
    func session(_ session: WCSession, didReceiveMessage message: [String : Any], replyHandler: @escaping ([String : Any]) -> Void) {
        DispatchQueue.main.async {
            // 首先處理普通消息邏輯
            self.lastReceivedMessage = message
            
            // 檢查是否為 Keystone 相關消息
            if let messageType = message["type"] as? String,
               messageType.contains("keystone") {
                self.handleKeystoneMessage(message)
            }
            
            // 處理需要回復的消息
            if let action = message["action"] as? String {
                switch action {
                case "getWatchStatus":
                    replyHandler([
                        "status": "active", 
                        "version": "1.0.0",
                        "keystoneSupported": true,
                        "timestamp": Date().timeIntervalSince1970
                    ])
                case MessageType.keystoneConnectRequest, MessageType.keystoneSignRequest:
                    // Keystone 請求的確認回復
                    replyHandler([
                        "status": "received",
                        "timestamp": Date().timeIntervalSince1970
                    ])
                default:
                    replyHandler([
                        "status": "unknown action",
                        "action": action
                    ])
                }
            } else {
                replyHandler([
                    "status": "no action specified"
                ])
            }
        }
    }
    
    func session(_ session: WCSession, didReceiveApplicationContext applicationContext: [String : Any]) {
        // Handle application context updates (for background sync)
        DispatchQueue.main.async {
            self.lastReceivedMessage = applicationContext
            
            // 檢查是否包含 Keystone 相關的背景同步數據
            if let keystoneData = applicationContext["keystoneUpdate"] as? [String: Any] {
                print("📱 收到 Keystone 背景同步數據")
                // 可以在這裡處理 Keystone 狀態的背景同步
            }
        }
    }
}

// MARK: - Notification Names
extension Notification.Name {
    static let walletUpdated = Notification.Name("walletUpdated")
    static let transactionUpdate = Notification.Name("transactionUpdate")
    static let keystoneConnected = Notification.Name("keystoneConnected")
    static let keystoneSignatureReceived = Notification.Name("keystoneSignatureReceived")
}

// MARK: - WCSessionActivationState Extensions
extension WCSessionActivationState {
    var description: String {
        switch self {
        case .notActivated:
            return "未激活"
        case .inactive:
            return "非活動"
        case .activated:
            return "已激活"
        @unknown default:
            return "未知狀態"
        }
    }
}
import Foundation
import WatchConnectivity
import Combine
import SwiftUI

/**
 * iPhone 端的手錶連接管理器
 * 負責處理來自 Apple Watch 的請求，特別是 Keystone 相關的操作
 */
class WatchConnectivityManager: NSObject, ObservableObject {
    static let shared = WatchConnectivityManager()
    
    // MARK: - Published Properties
    @Published var isReachable = false
    @Published var lastReceivedMessage: [String: Any] = [:]
    
    // 用於導航的狀態
    @Published var showKeystoneScanner = false
    @Published var currentRequestId: String?
    @Published var scannerExpectedType: URType = .CRYPTO_SIGNATURE
    
    private let session: WCSession
    
    // MARK: - Message Types (需與 Watch 端保持一致)
    private enum MessageType {
        static let keystoneConnectRequest = "keystone_connect_request"
        static let keystoneConnectResult = "keystone_connect_result"
        static let keystoneSignRequest = "keystone_sign_request"
        static let keystoneSignResult = "keystone_sign_result"
    }
    
    private override init() {
        self.session = WCSession.default
        super.init()
        
        if WCSession.isSupported() {
            session.delegate = self
            session.activate()
        }
    }
    
    // MARK: - Public Methods
    
    /**
     * 發送 Keystone 連接結果回手錶
     */
    func sendKeystoneConnectResult(urData: String) {
        guard session.isReachable else {
            print("❌ 手錶不可達，無法發送連接結果")
            return
        }
        
        let message: [String: Any] = [
            "type": MessageType.keystoneConnectResult,
            "timestamp": Date().timeIntervalSince1970,
            "data": [
                "urData": urData
            ]
        ]
        
        sendMessage(message)
    }
    
    /**
     * 發送 Keystone 簽名結果回手錶
     */
    func sendKeystoneSignResult(urData: String) {
        guard session.isReachable else {
            print("❌ 手錶不可達，無法發送簽名結果")
            return
        }
        
        let message: [String: Any] = [
            "type": MessageType.keystoneSignResult,
            "timestamp": Date().timeIntervalSince1970,
            "data": [
                "urData": urData
            ]
        ]
        
        sendMessage(message)
    }
    
    func sendMessage(_ message: [String: Any]) {
        session.sendMessage(message, replyHandler: { reply in
            print("✅ 消息發送成功，手錶回應: \(reply)")
        }, errorHandler: { error in
            print("❌ 發送消息失敗: \(error.localizedDescription)")
        })
    }
    
    // MARK: - Private Methods
    
    private func handleKeystoneMessage(_ message: [String: Any]) {
        guard let messageType = message["type"] as? String else { return }
        
        DispatchQueue.main.async {
            switch messageType {
            case MessageType.keystoneConnectRequest:
                print("📱 收到 Keystone 連接請求")
                self.currentRequestId = UUID().uuidString
                self.scannerExpectedType = .CRYPTO_ACCOUNT // 連接通常是掃描帳戶
                self.showKeystoneScanner = true
                
            case MessageType.keystoneSignRequest:
                print("📱 收到 Keystone 簽名請求")
                self.currentRequestId = UUID().uuidString
                self.scannerExpectedType = .CRYPTO_SIGNATURE // 簽名請求
                self.showKeystoneScanner = true
                
                // 如果有交易數據需要顯示，可以在這裡處理
                if let data = message["data"] as? [String: Any] {
                    print("   - 交易數據: \(data)")
                }
                
            default:
                break
            }
        }
    }
}

// MARK: - WCSessionDelegate
extension WatchConnectivityManager: WCSessionDelegate {
    
    func session(_ session: WCSession, activationDidCompleteWith activationState: WCSessionActivationState, error: Error?) {
        DispatchQueue.main.async {
            if let error = error {
                print("❌ WCSession 激活失敗: \(error.localizedDescription)")
            } else {
                print("✅ WCSession 激活成功: \(activationState.rawValue)")
                self.isReachable = session.isReachable
            }
        }
    }
    
    func sessionDidBecomeInactive(_ session: WCSession) {}
    
    func sessionDidDeactivate(_ session: WCSession) {
        // 重新激活以支援切換手錶
        session.activate()
    }
    
    func session(_ session: WCSession, didReceiveMessage message: [String : Any]) {
        handleMessage(message)
    }
    
    func session(_ session: WCSession, didReceiveMessage message: [String : Any], replyHandler: @escaping ([String : Any]) -> Void) {
        handleMessage(message)
        
        // 簡單回復確認收到
        replyHandler(["status": "received", "timestamp": Date().timeIntervalSince1970])
    }
    
    private func handleMessage(_ message: [String: Any]) {
        // 處理 Keystone 消息
        if let type = message["type"] as? String, type.contains("keystone") {
            handleKeystoneMessage(message)
        }
    }
    
    func sessionReachabilityDidChange(_ session: WCSession) {
        DispatchQueue.main.async {
            self.isReachable = session.isReachable
        }
    }
}

// 用於定義掃描類型
enum URType {
    case CRYPTO_ACCOUNT
    case CRYPTO_SIGNATURE
}

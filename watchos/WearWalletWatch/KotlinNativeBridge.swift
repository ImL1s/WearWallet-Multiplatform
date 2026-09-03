import Foundation
import coreKmp

/**
 * Kotlin/Native 橋接配置
 * 
 * 這個文件負責初始化 coreKmp 框架並設置與 Swift 的互操作
 */
class KotlinNativeBridge {
    
    static let shared = KotlinNativeBridge()
    
    private init() {
        // 私有初始化器確保單例
    }
    
    /**
     * 初始化 Kotlin/Native 橋接
     */
    func initialize() {
        print("KotlinNativeBridge: Initializing Kotlin/Native bridge")
        
        // 初始化 Keystone Swift 橋接
        let success = KeystoneSwiftBridge.setup()
        print("KotlinNativeBridge: Keystone bridge initialized: \(success)")
        
        // 註冊 Swift 實現到 Kotlin
        registerSwiftImplementations()
        
        print("KotlinNativeBridge: Bridge initialization complete")
    }
    
    /**
     * 註冊 Swift 實現供 Kotlin 調用
     */
    private func registerSwiftImplementations() {
        // 這裡可以設置任何需要從 Kotlin 調用的 Swift 回調
        // 例如：註冊 QR 掃描處理器、UI 更新回調等
    }
    
    /**
     * 從 Kotlin 調用 Swift 方法的示例
     */
    func handleKeystoneRequest(_ request: Any) {
        // 將 Kotlin 對象轉換為 Swift 可用的格式
        // 調用相應的 KeystoneSwiftBridge 方法
    }
    
    /**
     * 生成以太坊簽名請求（供 Kotlin 調用）
     */
    @objc public func generateEthSignRequest(
        unsignedTxHex: String,
        derivationPath: String,
        masterFingerprint: String,
        chainId: Int64,
        requestId: String,
        fromAddress: String?
    ) -> [String]? {
        
        let result = KeystoneSwiftBridge.generateEthSignRequest(
            unsignedTxHex: unsignedTxHex,
            derivationPath: derivationPath,
            masterFingerprint: masterFingerprint,
            chainId: chainId,
            requestId: requestId,
            fromAddress: fromAddress
        )
        
        return result?.qrCodeData
    }
    
    /**
     * 解析簽名（供 Kotlin 調用）
     */
    @objc public func parseSignature(_ urString: String) -> [String: String]? {
        guard let result = KeystoneSwiftBridge.parseSignature(urString) else {
            return nil
        }
        
        return [
            "signature": result.signature,
            "requestId": result.requestId
        ]
    }
    
    /**
     * 導入錢包（供 Kotlin 調用）
     */
    @objc public func importWallet(_ qrData: String) -> [String: Any]? {
        guard let wallet = KeystoneSwiftBridge.importWallet(qrData) else {
            return nil
        }
        
        // 轉換為 Dictionary 供 Kotlin 使用
        var addressesArray: [[String: String]] = []
        for address in wallet.addresses {
            addressesArray.append([
                "address": address.address,
                "chainId": address.chainId,
                "derivationPath": address.derivationPath,
                "publicKey": address.publicKey,
                "addressType": address.addressType
            ])
        }
        
        return [
            "id": wallet.id,
            "name": wallet.name,
            "masterFingerprint": wallet.masterFingerprint,
            "addresses": addressesArray,
            "supportedChains": wallet.supportedChains
        ]
    }
}

// MARK: - WatchOS 特定功能

#if os(watchOS)
import WatchConnectivity

extension KotlinNativeBridge {
    
    /**
     * 設置 WatchConnectivity
     */
    func setupWatchConnectivity() {
        if WCSession.isSupported() {
            let session = WCSession.default
            session.delegate = self
            session.activate()
            print("KotlinNativeBridge: WatchConnectivity setup complete")
        } else {
            print("KotlinNativeBridge: WatchConnectivity not supported")
        }
    }
    
    /**
     * 請求 iPhone 掃描 QR Code
     */
    func requestQRScanFromPhone(completion: @escaping (String?) -> Void) {
        KeystoneSwiftBridge.requestQRScan(completion: completion)
    }
    
    /**
     * 發送 QR Code 到 iPhone 顯示
     */
    func sendQRToPhone(_ qrData: String, completion: @escaping (Bool) -> Void) {
        KeystoneSwiftBridge.sendQRToPhone(qrData: qrData, completion: completion)
    }
}

// WatchConnectivity Delegate
extension KotlinNativeBridge: WCSessionDelegate {
    
    func session(_ session: WCSession, activationDidCompleteWith activationState: WCSessionActivationState, error: Error?) {
        if let error = error {
            print("KotlinNativeBridge: WCSession activation error: \(error)")
        } else {
            print("KotlinNativeBridge: WCSession activated with state: \(activationState)")
        }
    }
    
    func session(_ session: WCSession, didReceiveMessage message: [String : Any]) {
        print("KotlinNativeBridge: Received message from iPhone: \(message)")
        
        // 處理從 iPhone 接收的消息
        if let action = message["action"] as? String {
            switch action {
            case "qrScanned":
                if let qrData = message["qrData"] as? String {
                    handleScannedQR(qrData)
                }
            case "signatureReady":
                if let signature = message["signature"] as? String {
                    handleSignature(signature)
                }
            default:
                print("KotlinNativeBridge: Unknown action: \(action)")
            }
        }
    }
    
    private func handleScannedQR(_ qrData: String) {
        // 處理掃描到的 QR Code
        print("KotlinNativeBridge: Handling scanned QR: \(qrData.prefix(50))...")
        
        // 通知 Kotlin 層
        // NotificationCenter 或其他機制
    }
    
    private func handleSignature(_ signature: String) {
        // 處理簽名
        print("KotlinNativeBridge: Handling signature: \(signature.prefix(50))...")
        
        // 通知 Kotlin 層
        // NotificationCenter 或其他機制
    }
}
#endif
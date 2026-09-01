import Foundation
import coreKmp
import WatchConnectivity

/**
 * Kotlin/Native 橋接配置
 * 
 * 這個文件負責初始化 coreKmp 框架並設置與 Swift 的互操作
 */
class KotlinNativeBridge: NSObject {
    
    static let shared = KotlinNativeBridge()
    
    private override init() {
        super.init()
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
        
        // 註冊 Crypto 橋接 (Fix for AES-GCM)
        CryptoSwiftBridge.shared.register()
        
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
     * 請求 iPhone 掃描 QR Code
     */
    func requestQRScanFromPhone(completion: @escaping (String?) -> Void) {
        // Redirect to WatchConnectivityManager
        WatchConnectivityManager.shared.requestQRCodeScan()
        // Note: completion handling would need refactoring as Manager uses published property
        print("[KotlinNativeBridge] requestQRScanFromPhone - delegated to Manager")
        completion(nil)
    }
    
    /**
     * 發送 QR Code 到 iPhone 顯示
     * 透過 WatchConnectivityManager 實現
     */
    func sendQRToPhone(_ qrData: String, completion: @escaping (Bool) -> Void) {
        guard WCSession.default.isReachable else {
            print("[KotlinNativeBridge] sendQRToPhone - iPhone 不可達")
            completion(false)
            return
        }

        // 使用 WatchConnectivityManager 發送 QR 碼到 iPhone
        WatchConnectivityManager.shared.sendQRToPhone(qrData)
        print("[KotlinNativeBridge] sendQRToPhone - 已發送到 iPhone")
        completion(true)
    }

    /**
     * 異步版本：發送 QR Code 到 iPhone 顯示
     */
    func sendQRToPhoneAsync(_ qrData: String) async -> Bool {
        guard WCSession.default.isReachable else {
            print("[KotlinNativeBridge] sendQRToPhoneAsync - iPhone 不可達")
            return false
        }

        return await withCheckedContinuation { continuation in
            let message: [String: Any] = [
                "action": "displayQRCode",
                "qrData": qrData,
                "timestamp": Date().timeIntervalSince1970
            ]

            WCSession.default.sendMessage(message) { response in
                print("[KotlinNativeBridge] QR 碼已發送，回應: \(response)")
                continuation.resume(returning: true)
            } errorHandler: { error in
                print("[KotlinNativeBridge] 發送 QR 碼失敗: \(error.localizedDescription)")
                continuation.resume(returning: false)
            }
        }
    }
}
#endif
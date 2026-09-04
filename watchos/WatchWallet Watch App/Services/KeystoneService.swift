//
//  KeystoneService.swift
//  WatchWallet Watch App
//
//  Created by IML1S
//  整合 coreKmp KeystoneService 和 watchOS UI
//

import Foundation
import Combine
import SwiftUI
import coreKmp

/**
 * Keystone 硬體錢包整合服務
 * 使用 coreKmp 提供的 KeystoneService
 */
class KeystoneService: ObservableObject {
    
    // MARK: - Singleton
    static let shared = KeystoneService()
    
    // MARK: - Published Properties
    @Published private(set) var isInitialized = false
    @Published private(set) var connectedWallets: [ConnectedWallet] = []
    @Published private(set) var serviceStatus: KeystoneServiceStatusInfo?
    @Published private(set) var isLoading = false
    @Published private(set) var error: String?
    
    // MARK: - Properties
    private var cancellables = Set<AnyCancellable>()
    // 注意: 如果 Kotlin 輸出類名有變，需調整這裡
    private let kmpService = coreKmp.KeystoneService()
    
    // MARK: - Initialization
    private init() {
        setupService()
    }
    
    // MARK: - Public Methods
    
    /**
     * 初始化 Keystone 服務
     */
    func initialize() async {
        isLoading = true
        error = nil
        
        do {
            // 注意: Kotlin 的 suspend 函數在 Swift 中是 async throws
            let result = try await kmpService.initialize()
            
            // 使用反射處理 Result
            let mirror = Mirror(reflecting: result)
            let typeName = String(describing: type(of: result))
            
            if typeName.contains("Success") {
                isInitialized = true
                print("[KeystoneService] ✅ Initialized successfully")
            } else {
                // Failure
                isInitialized = false
                print("[KeystoneService] ❌ Initialization failed: \(result)")
                if let errorField = mirror.children.first(where: { $0.label == "error" })?.value {
                    error = String(describing: errorField)
                }
            }
        } catch {
            self.error = error.localizedDescription
            isInitialized = false
            print("[KeystoneService] ❌ Exception: \(error)")
        }
        
        isLoading = false
    }
    
    /**
     * 從 iPhone 同步錢包資料
     */
    func syncWalletFromiPhone(_ walletData: String) async {
        isLoading = true
        error = nil
        
        do {
            let result = try await kmpService.importWalletFromQR(qrData: walletData)

            let mirror = Mirror(reflecting: result)
            let typeName = String(describing: type(of: result))

            if typeName.contains("Success") {
                if let wallet = mirror.children.first(where: { $0.label == "data" })?.value {
                    // 使用反射提取 KMP Wallet 對象屬性
                    let walletMirror = Mirror(reflecting: wallet)

                    // 提取錢包屬性
                    let address = extractStringProperty(from: walletMirror, name: "address") ?? ""
                    let name = extractStringProperty(from: walletMirror, name: "name") ?? "Keystone Wallet"
                    let chainId = extractStringProperty(from: walletMirror, name: "chainId") ?? "1"

                    print("[KeystoneService] ✅ Wallet synced via KMP - Address: \(address.prefix(10))...")

                    let connectedWallet = ConnectedWallet(
                        address: address,
                        name: name,
                        type: .keystone,
                        chainId: chainId,
                        balance: "0",
                        metadata: [:]
                    )

                    connectedWallets.append(connectedWallet)
                }
            } else {
                if let errorField = mirror.children.first(where: { $0.label == "error" })?.value {
                    error = String(describing: errorField)
                }
                print("[KeystoneService] ❌ Sync failed")
            }
        } catch {
            self.error = error.localizedDescription
            print("[KeystoneService] ❌ Sync exception: \(error)")
        }
        
        isLoading = false
    }
    
    /**
     * 連接 Keystone 錢包
     */
    func connectWallet(qrData: String) async -> Bool {
        isLoading = true
        error = nil
        
        do {
            let result = try await kmpService.importWalletFromQR(qrData: qrData)
            
            let typeName = String(describing: type(of: result))
            if typeName.contains("Success") {
                print("[KeystoneService] ✅ Wallet connected")
                await syncWalletFromiPhone(qrData)
                return true
            } else {
                print("[KeystoneService] ❌ Connection failed")
                return false
            }
        } catch {
            self.error = error.localizedDescription
            print("[KeystoneService] ❌ Connection exception: \(error)")
        }
        
        isLoading = false
        return false
    }
    
    /**
     * 斷開錢包連接
     */
    func disconnectWallet(_ address: String) async {
        connectedWallets.removeAll { $0.address == address }
        print("[KeystoneService] Wallet disconnected: \(address)")
    }
    
    /**
     * 檢查是否為有效的 Keystone QR Code
     */
    func isValidKeystoneQR(_ qrData: String) -> Bool {
        return kmpService.isValidKeystoneQR(qrData: qrData)
    }
    
    /**
     * 生成簽名請求
     */
    /**
     * 生成簽名請求
     */
    func generateSignRequest(transaction: UnsignedTransaction) async -> String? {
        isLoading = true
        error = nil
        
        do {
            // 構建交易十六進制數據
            let txHex = buildTransactionHex(transaction)
            
            // 使用默認值，因為靜態屬性可能無法訪問
            let signRequest = try await kmpService.generateEthSignRequest(
                unsignedTxHex: txHex,
                derivationPath: "m/44'/60'/0'/0/0",
                masterFingerprint: "00000000",
                chainId: Int64(transaction.chainId) ?? 1,
                requestId: kmpService.generateRequestId(),
                fromAddress: transaction.from
            )
            
            // 生成 QR 碼數據
            let qrResult = try await kmpService.generateSignRequestQR(request: signRequest)
            
            let mirror = Mirror(reflecting: qrResult)
            let typeName = String(describing: type(of: qrResult))
            
            if typeName.contains("Success") {
                isLoading = false
                if let qrData = mirror.children.first(where: { $0.label == "data" })?.value as? String {
                    return qrData
                }
            } else {
                if let errorField = mirror.children.first(where: { $0.label == "error" })?.value {
                    error = String(describing: errorField)
                }
                print("[KeystoneService] ❌ QR generation failed")
            }
        } catch {
            self.error = error.localizedDescription
            print("[KeystoneService] ❌ Sign request exception: \(error)")
        }
        
        isLoading = false
        return nil
    }
    
    /**
     * 解析簽名響應
     */
    func parseSignResponse(_ responseData: String) async -> SignedTransaction? {
        isLoading = true
        error = nil

        do {
            let result = try await kmpService.parseSignResponse(responseData: responseData)

            let mirror = Mirror(reflecting: result)
            let typeName = String(describing: type(of: result))

            if typeName.contains("Success") {
                if let signatureData = mirror.children.first(where: { $0.label == "data" })?.value {
                    // 使用反射提取簽名結果
                    let sigMirror = Mirror(reflecting: signatureData)

                    // 提取簽名屬性
                    let signature = extractStringProperty(from: sigMirror, name: "signature")
                        ?? extractStringProperty(from: sigMirror, name: "signedTx")
                        ?? ""
                    let requestId = extractStringProperty(from: sigMirror, name: "requestId") ?? ""
                    let txHash = extractStringProperty(from: sigMirror, name: "txHash")

                    print("[KeystoneService] ✅ Signature parsed - RequestId: \(requestId)")

                    isLoading = false
                    return SignedTransaction(
                        signedTx: signature,
                        txHash: txHash,
                        from: "",
                        to: "",
                        value: "",
                        status: .signed
                    )
                }
            } else {
                if let errorField = mirror.children.first(where: { $0.label == "error" })?.value {
                    error = String(describing: errorField)
                }
                print("[KeystoneService] ❌ Parse response failed")
            }
        } catch {
            self.error = error.localizedDescription
            print("[KeystoneService] ❌ Parse response exception: \(error)")
        }

        isLoading = false
        return nil
    }

    // MARK: - Reflection Helpers

    /// 從 Mirror 中提取字符串屬性
    private func extractStringProperty(from mirror: Mirror, name: String) -> String? {
        // 直接查找屬性名
        if let value = mirror.children.first(where: { $0.label == name })?.value {
            if let stringValue = value as? String {
                return stringValue
            }
            // 嘗試轉換為字符串描述
            let description = String(describing: value)
            if description != "nil" && !description.isEmpty {
                return description
            }
        }

        // 遍歷所有子屬性尋找匹配
        for child in mirror.children {
            if let childMirror = child.value as? Mirror {
                if let found = extractStringProperty(from: childMirror, name: name) {
                    return found
                }
            }
        }

        return nil
    }

    /// 從 Mirror 中提取整數屬性
    private func extractIntProperty(from mirror: Mirror, name: String) -> Int? {
        if let value = mirror.children.first(where: { $0.label == name })?.value {
            if let intValue = value as? Int {
                return intValue
            }
            if let int32Value = value as? Int32 {
                return Int(int32Value)
            }
            if let int64Value = value as? Int64 {
                return Int(int64Value)
            }
        }
        return nil
    }
    
    // MARK: - Private Methods
    
    private func setupService() {
        Task {
            await initialize()
        }
    }
    
    private func buildTransactionHex(_ transaction: UnsignedTransaction) -> String {
        // 簡化版交易序列化
        // 實際應該使用完整的 RLP 編碼
        let components = [
            transaction.nonce ?? "0x0",
            transaction.gasPrice ?? "0x0",
            transaction.gasLimit ?? "0x5208",
            transaction.to,
            transaction.value,
            transaction.data ?? "0x"
        ]
        
        return components.joined(separator: "")
    }
}

// MARK: - Supporting Types

struct ConnectedWallet: Identifiable {
    let id = UUID()
    let address: String
    let name: String
    let type: KeystoneWalletType
    let chainId: String
    let balance: String
    let metadata: [String: String]
}

enum KeystoneWalletType {
    case keystone
    case watchOnly
    case hotWallet
}


struct KeystoneServiceStatusInfo {
    let initialized: Bool
    let connectedDevices: Int
    let lastError: String?
}

struct UnsignedTransaction {
    let from: String
    let to: String
    let value: String
    let data: String?
    let gasPrice: String?
    let gasLimit: String?
    let nonce: String?
    let chainId: String
}

struct SignedTransaction {
    let signedTx: String
    let txHash: String?
    let from: String
    let to: String
    let value: String
    let status: TransactionStatus
}


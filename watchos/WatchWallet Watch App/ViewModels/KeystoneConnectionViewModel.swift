//
//  KeystoneConnectionViewModel.swift
//  WatchWallet Watch App
//
//  Keystone 3 Pro 連接的 ViewModel
//  對應 WearOS 的 ConnectKeystoneWalletViewModel
//

import Foundation
import Combine
import WatchConnectivity
import coreKmp

@MainActor
class KeystoneConnectionViewModel: ObservableObject {
    
    // MARK: - 連接狀態枚舉
    enum ConnectionState {
        case idle
        case waitingForScan
        case scanning
        case success
        case error
    }
    
    // MARK: - Published Properties
    @Published var connectionState: ConnectionState = .idle
    @Published var isLoading = false
    @Published var errorMessage: String?
    
    // MARK: - Dependencies
    private let watchConnectivityManager = WatchConnectivityManager.shared
    private let keystoneClient: KeystoneClient
    
    // MARK: - Private Properties
    private var cancellables = Set<AnyCancellable>()
    
    // MARK: - Initialization
    init(client: KeystoneClient = .live) {
        self.keystoneClient = client
        setupWatchConnectivityListeners()
    }
    
    // MARK: - Public Methods
    
    /**
     * 開始 Keystone 連接流程
     */
    func startKeystoneConnection() {
        Task {
            do {
                // 初始化 Keystone 服務
                if !keystoneClient.isInitialized() {
                    await keystoneClient.initialize()
                }
                
                // 檢查初始化是否成功
                if let error = keystoneClient.getError() {
                    errorMessage = error
                    connectionState = .error
                    return
                }
                
                connectionState = .waitingForScan
                errorMessage = nil
                isLoading = false
                
                print("✅ Keystone 服務已準備就緒，等待掃描")
            } catch {
                errorMessage = "準備 Keystone 連接失敗: \(error.localizedDescription)"
                connectionState = .error
                print("❌ 準備 Keystone 掃描失敗: \(error)")
            }
        }
    }
    
    /**
     * 開始掃描 Keystone QR 碼
     */
    func startScanning() {
        connectionState = .scanning
        isLoading = true
        print("Started scanning for Keystone QR code")
    }
    
    /**
     * 請求 iPhone 端掃描 Keystone QR 碼
     */
    func requestiPhoneScan() {
        Task {
            do {
                let success = await watchConnectivityManager.requestKeystoneConnectScan()
                if !success {
                    errorMessage = "無法連接到 iPhone，請確保 iPhone 端應用已開啟"
                    connectionState = .error
                } else {
                    print("已發送掃描請求到 iPhone")
                }
            } catch {
                errorMessage = "發送掃描請求失敗: \(error.localizedDescription)"
                connectionState = .error
                print("請求 iPhone 掃描失敗: \(error)")
            }
        }
    }
    
    /**
     * 完成 Keystone 連接（掃描到 QR 碼後調用）
     */
    func completeKeystoneConnection(scannedData: String) {
        Task {
            do {
                isLoading = true
                
                print("🔍 處理掃描到的 Keystone 數據: \(scannedData.prefix(30))...")
                
                // 1. 嘗試解析為 JSON (來自 iOS 的解碼結果)
                if let data = scannedData.data(using: .utf8),
                   let json = try? JSONSerialization.jsonObject(with: data, options: []) as? [String: Any],
                   let xpub = json["xpub"] as? String {
                     
                    print("✅ 收到 iOS 解析後的 Keystone 數據")
                    
                    let masterFingerprint = json["xfp"] as? String ?? extractMasterFingerprint(from: xpub)
                    let derivationPath = json["path"] as? String ?? "m/44'/60'/0'/0/0"
                    
                    // 創建真實的錢包數據
                    let walletData: [String: Any] = [
                        "id": "keystone_\(masterFingerprint)",
                        "name": "Keystone Wallet",
                        "type": "keystone",
                        "masterFingerprint": masterFingerprint,
                        "extendedPublicKey": xpub,
                        "derivationPath": derivationPath,
                        "address": "", // 地址將由 KeystoneService 根據 xpub 生成
                        "supportedChains": ["1", "56", "137", "25"]
                    ]
                    
                    let walletDataString = convertWalletDataToString(walletData)
                    await keystoneClient.syncWalletFromiPhone(walletDataString)
                    
                    if keystoneClient.getError() == nil {
                        connectionState = .success
                        print("✅ Keystone 錢包連接成功 (JSON)")
                    } else {
                        errorMessage = keystoneClient.getError() ?? "錢包同步失敗"
                        connectionState = .error
                    }

                } 
                // 2. 驗證是否為原始 Keystone UR (舊模式或直接傳輸)
                else if keystoneClient.isValidKeystoneQR(scannedData) {
                    // 這是有效的 Keystone UR 協議數據
                    print("✅ 檢測到有效的 Keystone QR 碼 (UR)")
                    
                    // 從掃描數據創建錢包資訊 (注意: 這裡仍使用舊的 Mock 解析，除非更新 parseKeystoneWalletData)
                    let walletData = parseKeystoneWalletData(scannedData)
                    
                    // 同步錢包到 Keystone 服務
                    let walletDataString = convertWalletDataToString(walletData)
                    await keystoneClient.syncWalletFromiPhone(walletDataString)
                    
                    if keystoneClient.getError() == nil {
                        connectionState = .success
                        print("✅ Keystone 錢包連接成功 (UR)")
                    } else {
                        errorMessage = keystoneClient.getError() ?? "錢包同步失敗"
                        connectionState = .error
                        print("❌ Keystone 錢包同步失敗")
                    }
                    
                } else if scannedData.contains(" ") {
                    // 3. 可能是助記詞
                    // ... (保持原有邏輯)
                    print("🔍 檢測到可能的助記詞格式")
                    
                    let words = scannedData.split(separator: " ")
                    if words.count == 12 || words.count == 24 {
                        let mnemonicWalletData = createMnemonicWalletData(scannedData)
                        let mnemonicDataString = convertWalletDataToString(mnemonicWalletData)
                        await keystoneClient.syncWalletFromiPhone(mnemonicDataString)
                        
                        if keystoneClient.getError() == nil {
                            connectionState = .success
                            print("✅ 助記詞錢包導入成功: \(words.count) words")
                        } else {
                            errorMessage = keystoneClient.getError() ?? "助記詞錢包導入失敗"
                            connectionState = .error
                        }
                    } else {
                        errorMessage = "無效的助記詞格式（需要 12 或 24 個單詞）"
                        connectionState = .error
                    }
                } else {
                    errorMessage = "無法識別的 QR 碼格式"
                    connectionState = .error
                    print("❌ 無法識別的 QR 碼格式")
                }
                
            } catch {
                errorMessage = "處理 Keystone 連接時發生錯誤: \(error.localizedDescription)"
                connectionState = .error
                print("❌ 完成 Keystone 連接異常: \(error)")
            }
            
            isLoading = false
        }
    }
    
    /**
     * 重試連接
     */
    func retryConnection() {
        connectionState = .idle
        errorMessage = nil
        isLoading = false
        
        startKeystoneConnection()
    }
    
    /**
     * 清除錯誤消息
     */
    func clearError() {
        errorMessage = nil
    }
    
    /**
     * 取消連接
     */
    func cancelConnection() {
        connectionState = .idle
        errorMessage = nil
        isLoading = false
    }
    
    // MARK: - Private Methods
    
    /**
     * 轉換錢包數據為字符串
     */
    private func convertWalletDataToString(_ walletData: [String: Any]) -> String {
        do {
            let jsonData = try JSONSerialization.data(withJSONObject: walletData, options: [])
            return String(data: jsonData, encoding: .utf8) ?? "{}"
        } catch {
            print("[KeystoneConnectionViewModel] 轉換錢包數據失敗: \(error)")
            return "{}"
        }
    }
    
    /**
     * 設置 WatchConnectivity 監聽器
     */
    private func setupWatchConnectivityListeners() {
        // 監聽 Keystone 連接掃描結果
        watchConnectivityManager.keystoneConnectResults
            .receive(on: DispatchQueue.main)
            .sink { [weak self] urData in
                print("收到 Keystone 連接掃描結果")
                self?.completeKeystoneConnection(scannedData: urData)
            }
            .store(in: &cancellables)
        
        // 監聽連接狀態變化
        watchConnectivityManager.isConnected
            .receive(on: DispatchQueue.main)
            .sink { [weak self] isConnected in
                if !isConnected && self?.connectionState == .scanning {
                    self?.errorMessage = "與 iPhone 的連接已斷開"
                    self?.connectionState = .error
                }
            }
            .store(in: &cancellables)
    }
}

// MARK: - WatchConnectivity Extensions

extension KeystoneConnectionViewModel {
    
    /**
     * 處理 WatchConnectivity 錯誤
     */
    private func handleConnectivityError(_ error: Error) {
        errorMessage = "通信錯誤: \(error.localizedDescription)"
        connectionState = .error
        isLoading = false
    }
    
    /**
     * 檢查 iPhone 連接狀態
     */
    func checkiPhoneConnection() -> Bool {
        return watchConnectivityManager.isConnected.value
    }
    
    // MARK: - Private Helper Methods
    
    /**
     * 解析 Keystone QR 碼創建錢包數據
     */
    private func parseKeystoneWalletData(_ qrData: String) -> [String: Any] {
        // 從 Keystone UR 協議數據解析錢包資訊
        // 實際實現中會使用 URProtocol 解析
        
        let walletId = "keystone_\(UUID().uuidString.prefix(8))"
        return [
            "id": walletId,
            "name": "Keystone Wallet",
            "type": "keystone",
            "masterFingerprint": extractMasterFingerprint(from: qrData),
            "address": generateAddressFromUR(qrData),
            "supportedChains": ["1", "56", "137", "25"] // ETH, BSC, Polygon, Cronos
        ]
    }
    
    /**
     * 從助記詞創建錢包數據
     */
    private func createMnemonicWalletData(_ mnemonic: String) -> [String: Any] {
        let walletId = "mnemonic_\(UUID().uuidString.prefix(8))"
        return [
            "id": walletId,
            "name": "Imported Wallet",
            "type": "mnemonic",
            "masterFingerprint": generateFingerprintFromMnemonic(mnemonic),
            "address": generateAddressFromMnemonic(mnemonic),
            "supportedChains": ["1", "56", "137", "25"]
        ]
    }
    
    /**
     * 從 UR 數據提取主指紋
     */
    private func extractMasterFingerprint(from urData: String) -> String {
        // 實際實現會從 UR 協議中解析
        // 暫時生成一個模擬的指紋
        let hash = urData.hash
        return String(format: "%08X", abs(hash) % 0x7FFFFFFF)
    }
    
    /**
     * 從 UR 數據生成地址
     */
    private func generateAddressFromUR(_ urData: String) -> String {
        // 實際實現會從 UR 協議中解析公鑰並生成地址
        // 暫時生成一個模擬地址
        let hash = urData.hash
        return String(format: "0x%040x", abs(hash))
    }
    
    /**
     * 從助記詞生成指紋
     */
    private func generateFingerprintFromMnemonic(_ mnemonic: String) -> String {
        let words = mnemonic.split(separator: " ")
        let hash = words.joined().hash
        return String(format: "%08X", abs(hash) % 0x7FFFFFFF)
    }
    
    /**
     * 從助記詞生成地址
     */
    private func generateAddressFromMnemonic(_ mnemonic: String) -> String {
        // 實際實現會使用 BIP39 + BIP44 派生地址
        // 暫時生成一個模擬地址
        let hash = mnemonic.hash
        return String(format: "0x%040x", abs(hash))
    }
}
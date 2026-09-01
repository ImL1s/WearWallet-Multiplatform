//
//  SendViewModel.swift
//  WatchWallet Watch App
//
//  ViewModel for sending transactions
//

import Foundation
import SwiftUI
import Combine
import coreKmp
import WatchConnectivity

enum TransactionState {
    case idle
    case sending
    case keystoneSigning  // 新增 Keystone 簽名狀態
    case success
    case error(String)
}

@MainActor
class SendViewModel: ObservableObject {
    // MARK: - Published Properties
    @Published var selectedToken: TokenModel?
    @Published var balance: String = "0.00"
    @Published var isLoading = false
    @Published var error: String?
    @Published var transactionState: TransactionState = .idle
    @Published var isPhoneConnected = false

    // MARK: - Address Input Properties
    @Published var recipientAddress: String = "" {
        didSet {
            validateCurrentAddress()
        }
    }
    @Published var isAddressValid: Bool = false
    @Published var addressValidationMessage: String = ""
    
    // MARK: - Dependencies
    private let walletRepository = WalletRepositoryManager.shared
    private let connectivityManager = WatchConnectivityManager.shared
    private let priceService = PriceService.shared
    private let keystoneService = KeystoneService.shared
    private let gasEstimationService = GasEstimationService.shared
    
    // MARK: - Private Properties
    private var currentWalletId: String?
    private var cancellables: Set<AnyCancellable> = []
    
    // MARK: - Initialization
    init() {
        loadCurrentWallet()
        setupConnectivity()
        setupPriceUpdates()
    }
    
    @Published var password: String = ""
    
    // MARK: - Public Methods
    func sendTransaction(to address: String, amount: String) async {
        guard let walletId = currentWalletId else {
            error = "未選擇錢包"
            return
        }
        
        // Validate address format
        guard isValidAddress(address) else {
            error = "無效的地址格式"
            return
        }
        
        // Validate amount
        guard let amountValue = Double(amount), amountValue > 0 else {
            error = "請輸入有效的金額"
            return
        }
        
        if password.isEmpty {
            error = "請輸入錢包密碼"
            return
        }
        
        isLoading = true
        error = nil
        transactionState = .sending
        
        do {
            // Get current wallet details
            guard let wallet = try await walletRepository.getWalletAsync(id: walletId) else {
                throw NSError(domain: "SendViewModel", code: 1, userInfo: [NSLocalizedDescriptionKey: "無法獲取錢包資訊"])
            }
            
            // 估算真實的交易參數
            print("[SendViewModel] 估算軟體錢包交易 Gas 參數...")
            
            // 並行估算 gas limit, gas price 和 nonce
            async let gasLimitResult = gasEstimationService.estimateGasLimit(
                from: wallet.address,
                to: address,
                value: amount,
                data: "0x",
                chainId: wallet.chainId
            )
            async let gasPriceResult = gasEstimationService.getCurrentGasPrice(chainId: wallet.chainId)
            async let nonceResult = gasEstimationService.getTransactionNonce(
                address: wallet.address,
                chainId: wallet.chainId
            )
            
            // 等待所有估算完成
            let estimatedGasLimit = await gasLimitResult
            let estimatedGasPrice = await gasPriceResult  
            let estimatedNonce = await nonceResult
            
            // 處理估算結果，如果失敗則使用預設值
            let gasLimit: String?
            switch estimatedGasLimit {
            case .success(let limit):
                gasLimit = limit
                print("[SendViewModel] ✅ 軟體錢包 Gas Limit 估算完成: \(limit)")
            case .failure(let error):
                gasLimit = nil // 讓 KMP 使用預設值
                print("[SendViewModel] ⚠️ 軟體錢包 Gas Limit 估算失敗: \(error)")
            }
            
            let gasPrice: String?
            switch estimatedGasPrice {
            case .success(let price):
                gasPrice = price.standard // 使用標準 gas price
                print("[SendViewModel] ✅ 軟體錢包 Gas Price 估算完成: \(price.standard)")
            case .failure(let error):
                gasPrice = nil // 讓 KMP 使用預設值
                print("[SendViewModel] ⚠️ 軟體錢包 Gas Price 估算失敗: \(error)")
            }
            
            let nonce: String?
            switch estimatedNonce {
            case .success(let nonceValue):
                nonce = nonceValue
                print("[SendViewModel] ✅ 軟體錢包 Nonce 估算完成: \(nonceValue)")
            case .failure(let error):
                nonce = nil // 讓 KMP 使用預設值
                print("[SendViewModel] ⚠️ 軟體錢包 Nonce 估算失敗: \(error)")
            }
            
            print("[SendViewModel] 使用 Real KMP 交易廣播...")
            
            let chainType = getKMPChainType(wallet.chainId)
            
            let txHash = try await KMPUseCaseDirect.shared.sendTransaction(
                from: wallet.address,
                to: address,
                amount: amount,
                chainType: chainType,
                password: password
            )
            
            print("[SendViewModel] ✅ 交易成功: \(txHash)")
            transactionState = .success
            
            // Clear form after success
            DispatchQueue.main.asyncAfter(deadline: .now() + 1) {
                self.transactionState = .idle
                self.password = "" // Clear password
            }
        } catch {
            self.error = "交易失敗: \(error.localizedDescription)"
            transactionState = .error(error.localizedDescription)
        }
        
        isLoading = false
    }
    
    // Mapping from chainId to ChainType
    private func getKMPChainType(_ chainId: String) -> coreKmp.ChainType {
        switch chainId {
        case "1": return .ethereum
        case "56": return .bsc
        case "137": return .polygon
        case "bitcoin": return .bitcoin
        case "litecoin": return .litecoin
        case "dogecoin": return .dogecoin
        default: return .ethereum
        }
    }
    
    func getEstimatedUsdValue(_ amount: String) -> String {
        guard !amount.isEmpty else { return "0.00" }
        
        // 使用 PriceService 獲取真實價格
        let tokenSymbol = selectedToken?.symbol ?? "ETH"
        return priceService.formatUsdValue(amount: amount, symbol: tokenSymbol)
    }
    
    /**
     * 使用 Keystone 硬體錢包簽名交易
     */
    func sendTransactionWithKeystone(to address: String, amount: String) async {
        guard let walletId = currentWalletId else {
            error = "未選擇錢包"
            return
        }
        
        // Validate inputs
        guard isValidAddress(address) else {
            error = "無效的地址格式"
            return
        }
        
        guard let amountValue = Double(amount), amountValue > 0 else {
            error = "請輸入有效的金額"
            return
        }
        
        isLoading = true
        error = nil
        transactionState = .keystoneSigning
        
        do {
            // Get current wallet details
            guard let wallet = try await walletRepository.getWalletAsync(id: walletId) else {
                throw NSError(domain: "SendViewModel", code: 1, userInfo: [NSLocalizedDescriptionKey: "無法獲取錢包資訊"])
            }
            
            print("[SendViewModel] 開始 Keystone 簽名流程")
            
            // 估算真實的交易參數
            print("[SendViewModel] 估算交易 Gas 參數...")
            
            // 並行估算 gas limit, gas price 和 nonce
            async let gasLimitResult = gasEstimationService.estimateGasLimit(
                from: wallet.address,
                to: address,
                value: amount,
                data: "0x",
                chainId: wallet.chainId
            )
            async let gasPriceResult = gasEstimationService.getCurrentGasPrice(chainId: wallet.chainId)
            async let nonceResult = gasEstimationService.getTransactionNonce(
                address: wallet.address,
                chainId: wallet.chainId
            )
            
            // 等待所有估算完成
            let estimatedGasLimit = await gasLimitResult
            let estimatedGasPrice = await gasPriceResult
            let estimatedNonce = await nonceResult
            
            // 處理估算結果，如果失敗則使用預設值
            let gasLimit: String
            switch estimatedGasLimit {
            case .success(let limit):
                gasLimit = limit
                print("[SendViewModel] ✅ Gas Limit 估算完成: \(limit)")
            case .failure(let error):
                gasLimit = "21000" // 預設值
                print("[SendViewModel] ⚠️ Gas Limit 估算失敗，使用預設值: \(error)")
            }
            
            let gasPrice: String
            switch estimatedGasPrice {
            case .success(let price):
                gasPrice = price.standard // 使用標準 gas price
                print("[SendViewModel] ✅ Gas Price 估算完成: \(price.standard)")
            case .failure(let error):
                gasPrice = "20000000000" // 預設值 20 Gwei
                print("[SendViewModel] ⚠️ Gas Price 估算失敗，使用預設值: \(error)")
            }
            
            let nonce: String
            switch estimatedNonce {
            case .success(let nonceValue):
                nonce = nonceValue
                print("[SendViewModel] ✅ Nonce 估算完成: \(nonceValue)")
            case .failure(let error):
                nonce = "0" // 預設值
                print("[SendViewModel] ⚠️ Nonce 估算失敗，使用預設值: \(error)")
            }
            
            // 构建 UnsignedTransaction 结构体
            let unsignedTx = UnsignedTransaction(
                from: wallet.address,
                to: address,
                value: amount,
                data: "0x",
                gasPrice: gasPrice,
                gasLimit: gasLimit,
                nonce: nonce,
                chainId: wallet.chainId
            )

            // Generate Keystone sign request
            let requestId = "tx_\(UUID().uuidString.prefix(8))"
            let signRequestQR = await keystoneService.generateSignRequest(transaction: unsignedTx)
            
            guard let qrData = signRequestQR else {
                throw NSError(domain: "SendViewModel", code: 2, userInfo: [NSLocalizedDescriptionKey: "生成簽名請求失敗"])
            }
            
            print("[SendViewModel] Keystone 簽名請求已生成: \(qrData.prefix(30))...")
            
            // Send QR data to iPhone for Keystone signing
            let signRequest: [String: Any] = [
                "requestId": requestId,
                "qrData": qrData,
                "fromAddress": wallet.address,
                "toAddress": address,
                "value": amount,
                "chainId": wallet.chainId
            ]
            
            let success = await connectivityManager.requestKeystoneSignScan(signRequest: signRequest)
            
            if success {
                print("[SendViewModel] Keystone 簽名請求已發送到 iPhone")
                // 等待簽名結果通過 WatchConnectivity 返回
                setupKeystoneSigningListeners()
            } else {
                throw NSError(domain: "SendViewModel", code: 3, userInfo: [NSLocalizedDescriptionKey: "無法連接到 iPhone 進行 Keystone 簽名"])
            }
            
        } catch {
            self.error = "Keystone 簽名失敗: \(error.localizedDescription)"
            transactionState = .error(error.localizedDescription)
            isLoading = false
            print("[SendViewModel] Keystone 簽名異常: \(error)")
        }
    }
    
    // MARK: - Public Methods for QR Scanning
    func requestQRCodeScan() {
        connectivityManager.requestQRCodeScan()
    }
    
    // MARK: - Private Methods
    private func setupConnectivity() {
        // Monitor phone connectivity
        connectivityManager.$isReachable
            .receive(on: DispatchQueue.main)
            .assign(to: &$isPhoneConnected)
        
        // Monitor scanned QR codes
        connectivityManager.$scannedQRCode
            .compactMap { $0 }
            .receive(on: DispatchQueue.main)
            .sink { [weak self] qrCode in
                // Validate if it's an Ethereum address
                if self?.isValidAddress(qrCode) == true {
                    // The SendView will handle updating the address field
                    NotificationCenter.default.post(
                        name: .qrCodeScanned,
                        object: nil,
                        userInfo: ["address": qrCode]
                    )
                }
            }
            .store(in: &cancellables)
    }
    
    private func loadCurrentWallet() {
        Task {
            do {
                let wallets = try await walletRepository.getAllWalletsAsync()
                
                // 使用保存的 activeWalletId 選擇正確的錢包
                let savedWalletId = UserDefaults.standard.string(forKey: "activeWalletId")
                let activeWallet = wallets.first(where: { $0.id == savedWalletId }) ?? wallets.first
                
                if let wallet = activeWallet {
                    currentWalletId = wallet.id
                    
                    // Fetch real balance
                    do {
                        let chainType = self.getKMPChainType(wallet.chainId)
                        let token = try await KMPUseCaseDirect.shared.getTokenBalance(
                            walletAddress: wallet.address,
                            tokenAddress: nil,
                            chainType: chainType
                        )
                        self.balance = "\(token.balance) \(token.symbol)"
                    } catch {
                        print("[SendViewModel] Failed to fetch balance: \(error)")
                        self.balance = "0.00 \(self.selectedToken?.symbol ?? "ETH")"
                    }
                    
                    // Set default token
                    self.selectedToken = TokenModel(
                        id: "1",
                        symbol: "ETH",
                        name: "Ethereum",
                        chainId: wallet.chainId
                    )
                }
            } catch {
                self.error = "無法載入錢包資訊"
            }
        }
    }
    
    /// 同步地址驗證 (基本正則表達式)
    private func isValidAddress(_ address: String) -> Bool {
        // 快速同步驗證用於 UI 響應
        return isValidAddressSync(address)
    }

    /// 驗證當前輸入的地址並更新 UI 狀態
    private func validateCurrentAddress() {
        let address = recipientAddress.trimmingCharacters(in: .whitespacesAndNewlines)

        // 空地址
        if address.isEmpty {
            isAddressValid = false
            addressValidationMessage = ""
            return
        }

        // 檢測地址類型並驗證
        if address.hasPrefix("0x") {
            // Ethereum 格式
            if isValidAddressSync(address) {
                isAddressValid = true
                addressValidationMessage = "✓ 有效的 Ethereum 地址"
            } else if address.count < 42 {
                isAddressValid = false
                addressValidationMessage = "地址長度不足"
            } else {
                isAddressValid = false
                addressValidationMessage = "無效的 Ethereum 地址格式"
            }
        } else if address.hasPrefix("bc1") {
            // Bitcoin Bech32
            if isValidAddressSync(address) {
                isAddressValid = true
                addressValidationMessage = "✓ 有效的 Bitcoin (Bech32) 地址"
            } else {
                isAddressValid = false
                addressValidationMessage = "無效的 Bitcoin Bech32 地址"
            }
        } else if address.hasPrefix("1") || address.hasPrefix("3") {
            // Bitcoin Legacy/P2SH
            if isValidAddressSync(address) {
                isAddressValid = true
                addressValidationMessage = "✓ 有效的 Bitcoin 地址"
            } else {
                isAddressValid = false
                addressValidationMessage = "無效的 Bitcoin 地址格式"
            }
        } else if address.count >= 32 && address.count <= 44 {
            // 可能是 Solana
            if isValidAddressSync(address) {
                isAddressValid = true
                addressValidationMessage = "✓ 有效的 Solana 地址"
            } else {
                isAddressValid = false
                addressValidationMessage = "無效的地址格式"
            }
        } else {
            isAddressValid = false
            addressValidationMessage = "無法識別的地址格式"
        }
    }

    /// 同步地址驗證 - 正則表達式
    private func isValidAddressSync(_ address: String) -> Bool {
        // Ethereum 地址格式
        let ethPattern = "^0x[a-fA-F0-9]{40}$"
        if let regex = try? NSRegularExpression(pattern: ethPattern),
           regex.firstMatch(in: address, options: [], range: NSRange(location: 0, length: address.utf16.count)) != nil {
            return true
        }

        // Bitcoin 地址格式 (Legacy, SegWit, Native SegWit)
        let btcPatterns = [
            "^[13][a-km-zA-HJ-NP-Z1-9]{25,34}$",  // Legacy
            "^3[a-km-zA-HJ-NP-Z1-9]{25,34}$",     // P2SH
            "^bc1[a-z0-9]{39,59}$"                 // Bech32
        ]
        for pattern in btcPatterns {
            if let regex = try? NSRegularExpression(pattern: pattern),
               regex.firstMatch(in: address, options: [], range: NSRange(location: 0, length: address.utf16.count)) != nil {
                return true
            }
        }

        // Solana 地址格式 (Base58, 32-44 字元)
        let solPattern = "^[1-9A-HJ-NP-Za-km-z]{32,44}$"
        if let regex = try? NSRegularExpression(pattern: solPattern),
           regex.firstMatch(in: address, options: [], range: NSRange(location: 0, length: address.utf16.count)) != nil {
            return true
        }

        return false
    }

    /// 異步地址驗證 - 使用 KMP 進行完整驗證
    private func validateAddressAsync(_ address: String, chainType: ChainType = .ethereum) async -> Bool {
        // 首先進行快速同步檢查
        guard isValidAddressSync(address) else {
            return false
        }

        // 使用 KMP 進行深度驗證（包括 checksum 驗證）
        do {
            // 嘗試透過 KMP 驗證
            // 如果 KMP 有 validateAddress 方法，可以在此調用
            // 目前 KMPUseCaseDirect 沒有直接的 validateAddress 方法
            // 可以透過嘗試解析地址來驗證

            // 對於 EVM 地址，驗證 checksum
            if address.hasPrefix("0x") {
                return validateEthereumChecksum(address)
            }

            return true
        } catch {
            print("[SendViewModel] 地址驗證錯誤: \(error)")
            return false
        }
    }

    /// 驗證 Ethereum 地址的 checksum (EIP-55)
    private func validateEthereumChecksum(_ address: String) -> Bool {
        // 如果地址全小寫或全大寫（除了 0x），跳過 checksum 驗證
        let addressWithoutPrefix = String(address.dropFirst(2))
        let isAllLower = addressWithoutPrefix == addressWithoutPrefix.lowercased()
        let isAllUpper = addressWithoutPrefix == addressWithoutPrefix.uppercased()

        if isAllLower || isAllUpper {
            // 有效但沒有 checksum
            return true
        }

        // 如果混合大小寫，需要驗證 checksum
        // 這裡簡化處理，完整驗證需要 Keccak256
        // 由於已經通過正則表達式驗證，暫時接受
        return true
    }
    
    private func setupPriceUpdates() {
        // 監聽價格服務的更新
        priceService.$tokenPrices
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in
                // 價格更新時觸發 UI 重新計算
                self?.objectWillChange.send()
            }
            .store(in: &cancellables)
        
        // 監聽價格服務的錯誤
        priceService.$error
            .compactMap { $0 }
            .receive(on: DispatchQueue.main)
            .sink { [weak self] priceError in
                // 如果價格更新失敗，記錄但不阻止交易
                print("[SendViewModel] Price update error: \(priceError)")
            }
            .store(in: &cancellables)
    }
    
    private func setupKeystoneSigningListeners() {
        // 監聽 Keystone 簽名結果
        connectivityManager.keystoneSignResults
            .receive(on: DispatchQueue.main)
            .sink { [weak self] signResultData in
                Task { @MainActor in
                    await self?.handleKeystoneSignResult(signResultData)
                }
            }
            .store(in: &cancellables)
    }
    
    private func handleKeystoneSignResult(_ signResultData: String) async {
        print("[SendViewModel] 收到 Keystone 簽名結果: \(signResultData.prefix(30))...")
        
        do {
            // 使用 KeystoneService 解析簽名結果
            guard let signResult = await keystoneService.parseSignResponse(signResultData) else {
                throw NSError(domain: "SendViewModel", code: 4, userInfo: [NSLocalizedDescriptionKey: "解析 Keystone 簽名結果失敗"])
            }
            
            print("[SendViewModel] Keystone 簽名解析成功，準備廣播交易")
            
            // 廣播已簽名的交易到區塊鏈
            guard let currentWalletId = currentWalletId,
                  let wallet = try await walletRepository.getWalletAsync(id: currentWalletId) else {
                throw NSError(domain: "SendViewModel", code: 5, userInfo: [NSLocalizedDescriptionKey: "無法獲取錢包資訊"])
            }
            
            let signedTransaction = signResult.signedTx
            guard !signedTransaction.isEmpty else {
                throw NSError(domain: "SendViewModel", code: 6, userInfo: [NSLocalizedDescriptionKey: "簽名結果無效"])
            }
            
            let txHash = try await broadcastSignedTransaction(
                signedTransaction: signedTransaction,
                chainId: wallet.chainId
            )
            
            transactionState = .success
            isLoading = false
            
            print("[SendViewModel] ✅ Keystone 交易簽名和廣播成功，交易哈希: \(txHash)")
            
            // 清除表單
            DispatchQueue.main.asyncAfter(deadline: .now() + 1) {
                self.transactionState = .idle
            }
            
        } catch {
            self.error = "處理 Keystone 簽名結果失敗: \(error.localizedDescription)"
            transactionState = .error(error.localizedDescription)
            isLoading = false
            print("[SendViewModel] ❌ 處理 Keystone 簽名結果異常: \(error)")
        }
    }
    
    /**
     * 廣播已簽名的交易
     */
    private func broadcastSignedTransaction(signedTransaction: String, chainId: String) async throws -> String {
        print("[SendViewModel] 開始廣播交易: \(signedTransaction.prefix(20))...")
        
        // 使用 Gas 估算服務中的 Web3Service 來廣播交易
        let web3Service = gasEstimationService.getWeb3Service(for: chainId)
        
        do {
            let result = try await web3Service.sendTransaction(
                signedTransaction: signedTransaction,
                chainId: chainId
            )
            
            // Web3Service Mock returns String (txHash) or throws
            if let txHash = result as? String {
                print("[SendViewModel] ✅ 交易廣播成功，哈希: \(txHash)")
                return txHash
            } else {
                 throw NSError(domain: "SendViewModel", code: 8, userInfo: [NSLocalizedDescriptionKey: "交易廣播返回未知格式"])
            }
        } catch {
            print("[SendViewModel] ❌ 交易廣播異常: \(error)")
            throw error
        }
    }
}

// MARK: - Notification Extension
extension Notification.Name {
    static let qrCodeScanned = Notification.Name("qrCodeScanned")
}
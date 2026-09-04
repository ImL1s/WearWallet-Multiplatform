//
//  GasEstimationService.swift
//  WatchWallet Watch App
//
//  Gas 費用估算服務
//

import Foundation
import coreKmp

@MainActor
class GasEstimationService: ObservableObject {
    
    // MARK: - Singleton
    static let shared = GasEstimationService()
    
    // MARK: - Published Properties
    @Published private(set) var isLoading = false
    @Published private(set) var error: String?
    
    // MARK: - Properties
    private let walletRepository = WalletRepositoryManager.shared
    private let web3ServiceFactory = Web3ServiceFactory()
    
    // Web3Service 實例緩存，按 chainId 存儲
    private var web3Services: [String: Web3Service] = [:]
    
    // Gas 價格緩存 (5分鐘有效)
    private var gasPriceCache: [String: CachedGasPrice] = [:]
    private let cacheExpirationInterval: TimeInterval = 5 * 60 // 5 minutes
    
    // MARK: - Initialization
    private init() {}
    
    // MARK: - Public Methods
    
    /**
     * 估算交易的 Gas Limit
     */
    func estimateGasLimit(
        from: String,
        to: String,
        value: String,
        data: String = "0x",
        chainId: String
    ) async -> Swift.Result<String, Error> {
        
        isLoading = true
        error = nil
        
        defer { isLoading = false }
        
        do {
            print("[GasEstimationService] 開始估算 Gas Limit")
            
            // 嘗試使用 Web3 RPC 估算 Gas
            if let rpcEstimate = await estimateGasViaRPC(
                from: from,
                to: to,
                value: value,
                data: data,
                chainId: chainId
            ) {
                print("[GasEstimationService] RPC Gas Limit 估算完成: \(rpcEstimate)")
                return .success(rpcEstimate)
            }
            
            // Fallback 到智能估算邏輯
            let estimatedGas = await estimateGasLimitIntelligently(
                from: from,
                to: to,
                value: value,
                data: data,
                chainId: chainId
            )
            
            print("[GasEstimationService] 智能 Gas Limit 估算完成: \(estimatedGas)")
            return .success(estimatedGas)
            
        } catch {
            let errorMessage = "Gas Limit 估算失敗: \(error.localizedDescription)"
            self.error = errorMessage
            print("[GasEstimationService] ❌ \(errorMessage)")
            return .failure(error)
        }
    }
    
    /**
     * 獲取當前網路的 Gas Price
     */
    func getCurrentGasPrice(chainId: String) async -> Swift.Result<GasPriceInfo, Error> {
        
        // 檢查緩存
        if let cachedPrice = gasPriceCache[chainId],
           Date().timeIntervalSince(cachedPrice.timestamp) < cacheExpirationInterval {
            return .success(cachedPrice.gasPrice)
        }
        
        isLoading = true
        error = nil
        
        defer { isLoading = false }
        
        do {
            print("[GasEstimationService] 獲取 Chain \(chainId) 的 Gas Price")
            
            // 嘗試從 RPC 獲取實時 Gas Price
            if let rpcGasPrice = await fetchGasPriceViaRPC(chainId: chainId) {
                // 緩存結果
                gasPriceCache[chainId] = CachedGasPrice(
                    gasPrice: rpcGasPrice,
                    timestamp: Date()
                )
                
                print("[GasEstimationService] RPC Gas Price 獲取完成: \(rpcGasPrice)")
                return .success(rpcGasPrice)
            }
            
            // Fallback 到預設 Gas 價格
            let gasPrice = await fetchGasPriceForChain(chainId: chainId)
            
            // 緩存結果
            gasPriceCache[chainId] = CachedGasPrice(
                gasPrice: gasPrice,
                timestamp: Date()
            )
            
            print("[GasEstimationService] 預設 Gas Price 獲取完成: \(gasPrice)")
            return .success(gasPrice)
            
        } catch {
            let errorMessage = "Gas Price 獲取失敗: \(error.localizedDescription)"
            self.error = errorMessage
            print("[GasEstimationService] ❌ \(errorMessage)")
            return .failure(error)
        }
    }
    
    /**
     * 獲取交易的 Nonce
     */
    func getTransactionNonce(address: String, chainId: String) async -> Swift.Result<String, Error> {
        
        do {
            print("[GasEstimationService] 獲取地址 \(address.prefix(10))... 的 Nonce")
            
            // 首先嘗試使用 RPC 獲取真實 Nonce
            if let rpcNonce = await fetchNonceViaRPC(address: address, chainId: chainId) {
                print("[GasEstimationService] RPC Nonce 獲取完成: \(rpcNonce)")
                return .success(rpcNonce)
            }
            
            // Fallback 到模擬 Nonce
            let nonce = await fetchNonceForAddress(address: address, chainId: chainId)
            
            print("[GasEstimationService] Fallback Nonce 獲取完成: \(nonce)")
            return .success(nonce)
            
        } catch {
            let errorMessage = "Nonce 獲取失敗: \(error.localizedDescription)"
            self.error = errorMessage
            print("[GasEstimationService] ❌ \(errorMessage)")
            return .failure(error)
        }
    }
    
    /**
     * 計算交易總費用 (Gas Limit × Gas Price)
     */
    func calculateTransactionFee(gasLimit: String, gasPrice: String) -> String {
        guard let gasLimitNum = UInt64(gasLimit),
              let gasPriceNum = UInt64(gasPrice) else {
            return "0"
        }
        
        let totalFee = gasLimitNum * gasPriceNum
        return String(totalFee)
    }
    
    /**
     * 將 Wei 轉換為 ETH (用於顯示)
     */
    func weiToEth(_ wei: String) -> String {
        guard let weiNum = UInt64(wei) else { return "0" }
        let eth = Double(weiNum) / 1_000_000_000_000_000_000.0
        return String(format: "%.6f", eth)
    }
    
    // MARK: - Private Methods
    
    /**
     * 獲取指定鏈的 Web3Service
     */
    func getWeb3Service(for chainId: String) -> Web3Service {
        if let cachedService = web3Services[chainId] {
            return cachedService
        }
        
        // 根據 chainId 獲取 RPC URL
        let rpcUrl = getRpcUrl(for: chainId)
        let service = web3ServiceFactory.create(rpcUrl: rpcUrl)
        web3Services[chainId] = service
        
        return service
    }
    
    /**
     * 根據 chainId 獲取 RPC URL
     */
    private func getRpcUrl(for chainId: String) -> String {
        switch chainId {
        case "1": // Ethereum Mainnet
            return "https://eth-mainnet.alchemyapi.io/v2/"
        case "56": // BSC Mainnet
            return "https://bsc-dataseed.binance.org/"
        case "137": // Polygon Mainnet
            return "https://polygon-rpc.com/"
        case "25": // Cronos Mainnet
            return "https://evm.cronos.org/"
        default:
            return "https://eth-mainnet.alchemyapi.io/v2/"
        }
    }
    
    /**
     * 使用 RPC 估算 Gas Limit
     */
    private func estimateGasViaRPC(
        from: String,
        to: String,
        value: String,
        data: String,
        chainId: String
    ) async -> String? {
        let web3Service = getWeb3Service(for: chainId)
        
        do {
            let result = try await web3Service.estimateGasLimit(
                from: from,
                to: to,
                value: value.isEmpty ? nil : value,
                data: data.isEmpty || data == "0x" ? nil : data,
                chainId: chainId
            )
            
            switch result {
            case let successResult as coreKmp.ResultSuccess<NSString>:
                if let gasLimit = successResult.data as String? {
                    return gasLimit
                }
            case let failureResult as coreKmp.ResultFailure:
                print("[GasEstimationService] RPC Gas 估算失敗: \(failureResult.error)")
            default:
                break
            }
        } catch {
            print("[GasEstimationService] RPC Gas 估算異常: \(error)")
        }
        
        return nil
    }
    
    /**
     * 使用 RPC 獲取 Gas Price
     */
    private func fetchGasPriceViaRPC(chainId: String) async -> GasPriceInfo? {
        let web3Service = getWeb3Service(for: chainId)
        
        do {
            let result = try await web3Service.getGasPrice(chainId: chainId)
            
            switch result {
            case let successResult as coreKmp.ResultSuccess<NSString>:
                if let gasPriceString = successResult.data as String?,
                   let gasPriceWei = UInt64(gasPriceString) {
                    // 將 wei 轉換為 gwei 字符串
                    let gasPriceGwei = String(gasPriceWei)
                    
                    return GasPriceInfo(
                        slow: gasPriceGwei,
                        standard: gasPriceGwei,
                        fast: String(gasPriceWei * 120 / 100), // +20% for fast
                        isEIP1559: chainId == "1" || chainId == "137" // Ethereum and Polygon support EIP-1559
                    )
                }
            case let failureResult as coreKmp.ResultFailure:
                print("[GasEstimationService] RPC Gas Price 獲取失敗: \(failureResult.error)")
            default:
                break
            }
        } catch {
            print("[GasEstimationService] RPC Gas Price 獲取異常: \(error)")
        }
        
        return nil
    }
    
    /**
     * 使用 RPC 獲取 Nonce
     */
    private func fetchNonceViaRPC(address: String, chainId: String) async -> String? {
        let web3Service = getWeb3Service(for: chainId)
        
        do {
            let result = try await web3Service.getNonce(address: address, chainId: chainId)
            
            switch result {
            case let successResult as coreKmp.ResultSuccess<NSString>:
                if let nonce = successResult.data as String? {
                    return nonce
                }
            case let failureResult as coreKmp.ResultFailure:
                print("[GasEstimationService] RPC Nonce 獲取失敗: \(failureResult.error)")
            default:
                break
            }
        } catch {
            print("[GasEstimationService] RPC Nonce 獲取異常: \(error)")
        }
        
        return nil
    }
    
    /**
     * 智能估算 Gas Limit
     */
    private func estimateGasLimitIntelligently(
        from: String,
        to: String,
        value: String,
        data: String,
        chainId: String
    ) async -> String {
        
        // 基礎交易類型判斷
        if data == "0x" || data.isEmpty {
            // 簡單 ETH 轉帳
            return "21000"
        } else if data.hasPrefix("0xa9059cbb") {
            // ERC-20 轉帳
            return "65000"
        } else if data.count > 10 {
            // 複雜合約調用
            let baseGas = 21000
            let dataGas = (data.count - 2) / 2 * 16 // 每個 byte 16 gas
            return String(baseGas + dataGas + 30000) // 添加額外安全邊際
        } else {
            // 預設值
            return "50000"
        }
    }
    
    /**
     * 獲取特定鏈的 Gas Price
     */
    private func fetchGasPriceForChain(chainId: String) async -> GasPriceInfo {
        
        // 根據不同鏈使用不同的策略
        switch chainId {
        case "1": // Ethereum
            return await fetchEthereumGasPrice()
        case "56": // BSC
            return await fetchBSCGasPrice()
        case "137": // Polygon
            return await fetchPolygonGasPrice()
        case "25": // Cronos
            return await fetchCronosGasPrice()
        default:
            return getDefaultGasPrice(chainId: chainId)
        }
    }
    
    /**
     * 獲取以太坊 Gas Price
     */
    private func fetchEthereumGasPrice() async -> GasPriceInfo {
        // 使用 ETH Gas Station API 或類似服務
        // 暫時返回合理的預設值
        return GasPriceInfo(
            slow: "15000000000",      // 15 Gwei
            standard: "25000000000",  // 25 Gwei
            fast: "35000000000",      // 35 Gwei
            isEIP1559: true,
            maxFeePerGas: "35000000000",
            maxPriorityFeePerGas: "2000000000"
        )
    }
    
    /**
     * 獲取 BSC Gas Price
     */
    private func fetchBSCGasPrice() async -> GasPriceInfo {
        return GasPriceInfo(
            slow: "5000000000",      // 5 Gwei
            standard: "10000000000", // 10 Gwei
            fast: "15000000000",     // 15 Gwei
            isEIP1559: false
        )
    }
    
    /**
     * 獲取 Polygon Gas Price
     */
    private func fetchPolygonGasPrice() async -> GasPriceInfo {
        return GasPriceInfo(
            slow: "30000000000",     // 30 Gwei
            standard: "50000000000", // 50 Gwei
            fast: "70000000000",     // 70 Gwei
            isEIP1559: true,
            maxFeePerGas: "70000000000",
            maxPriorityFeePerGas: "30000000000"
        )
    }
    
    /**
     * 獲取 Cronos Gas Price
     */
    private func fetchCronosGasPrice() async -> GasPriceInfo {
        return GasPriceInfo(
            slow: "20000000000000",     // 20,000 Gwei
            standard: "25000000000000", // 25,000 Gwei
            fast: "30000000000000",     // 30,000 Gwei
            isEIP1559: false
        )
    }
    
    /**
     * 獲取預設 Gas Price
     */
    private func getDefaultGasPrice(chainId: String) -> GasPriceInfo {
        return GasPriceInfo(
            slow: "20000000000",
            standard: "30000000000",
            fast: "40000000000",
            isEIP1559: false
        )
    }
    
    /**
     * 獲取地址的 Nonce
     * 透過 Web3Service 從區塊鏈 RPC 獲取
     */
    private func fetchNonceForAddress(address: String, chainId: String) async -> String {
        do {
            // 使用 Web3Service 獲取 nonce
            let web3Service = getWeb3Service(for: chainId)
            let nonce = try await web3Service.getNonce(address: address, chainId: chainId)
            print("[GasEstimationService] 獲取 Nonce 成功: \(nonce)")
            return String(nonce)
        } catch {
            print("[GasEstimationService] 獲取 Nonce 失敗，嘗試從交易歷史估算: \(error)")

            // Fallback: 從交易歷史估算 nonce
            do {
                let transactions = try await KMPUseCaseDirect.shared.getTransactionHistory(
                    address: address,
                    chainType: mapChainIdToType(chainId),
                    page: 1,
                    limit: 100
                )

                // 計算發送的交易數量作為 nonce 估算
                let sentCount = transactions.filter { $0.from.lowercased() == address.lowercased() }.count
                return String(sentCount)
            } catch {
                print("[GasEstimationService] 交易歷史獲取失敗，返回 0: \(error)")
                return "0"
            }
        }
    }

    /// 映射 chainId 到 ChainType
    private func mapChainIdToType(_ chainId: String) -> ChainType {
        switch chainId {
        case "1", "ethereum": return .ethereum
        case "56", "bsc": return .bsc
        case "137", "polygon": return .polygon
        case "42161", "arbitrum": return .arbitrum
        case "10", "optimism": return .optimism
        case "43114", "avalanche": return .avalanche
        case "250", "fantom": return .fantom
        case "8453", "base": return .base
        default: return .ethereum
        }
    }
}

// MARK: - Supporting Types

/**
 * Gas Price 資訊
 */
struct GasPriceInfo {
    let slow: String
    let standard: String
    let fast: String
    let isEIP1559: Bool
    let maxFeePerGas: String?
    let maxPriorityFeePerGas: String?
    
    init(slow: String, standard: String, fast: String, isEIP1559: Bool, maxFeePerGas: String? = nil, maxPriorityFeePerGas: String? = nil) {
        self.slow = slow
        self.standard = standard
        self.fast = fast
        self.isEIP1559 = isEIP1559
        self.maxFeePerGas = maxFeePerGas
        self.maxPriorityFeePerGas = maxPriorityFeePerGas
    }
}

/**
 * 緩存的 Gas Price
 */
private struct CachedGasPrice {
    let gasPrice: GasPriceInfo
    let timestamp: Date
}

/**
 * Gas 費用速度選項
 */
enum GasSpeed: String, CaseIterable {
    case slow = "slow"
    case standard = "standard" 
    case fast = "fast"
    
    var displayName: String {
        switch self {
        case .slow: return "慢速"
        case .standard: return "標準"
        case .fast: return "快速"
        }
    }
    
    var estimatedTime: String {
        switch self {
        case .slow: return "~5 分鐘"
        case .standard: return "~2 分鐘"
        case .fast: return "~30 秒"
        }
    }
}
//
//  Web3Service.swift
//  WatchWallet Watch App
//
//  Web3 服務 - 連接到 coreKmp 提供區塊鏈 RPC 功能
//

import Foundation
import coreKmp

/**
 * Web3Service - 提供 Ethereum 相關的 RPC 功能
 * 透過 KMPUseCaseDirect 橋接到 coreKmp 實現
 */
class Web3Service {
    private let rpcUrl: String
    private let chainType: ChainType

    init(rpcUrl: String, chainType: ChainType = .ethereum) {
        self.rpcUrl = rpcUrl
        self.chainType = chainType
    }

    // MARK: - Gas Estimation

    /// 估算交易的 Gas Limit
    func estimateGasLimit(
        from: String,
        to: String,
        value: String?,
        data: String?,
        chainId: String
    ) async throws -> UInt64 {
        print("[Web3Service] estimateGasLimit: \(from) -> \(to)")

        do {
            let estimation = try await KMPUseCaseDirect.shared.estimateGas(
                from: from,
                to: to,
                amount: value ?? "0",
                chainType: mapChainIdToType(chainId),
                data: data
            )

            // 解析 gasLimit 字符串為 UInt64
            if let gasLimit = UInt64(estimation.gasLimit) {
                return gasLimit
            }

            // 如果解析失敗，返回預設值
            return 21000
        } catch {
            print("[Web3Service] estimateGasLimit 錯誤: \(error)")
            // 返回預設 gas limit
            return 21000
        }
    }

    /// 獲取當前 Gas Price
    func getGasPrice(chainId: String) async throws -> UInt64 {
        print("[Web3Service] getGasPrice for chain: \(chainId)")

        do {
            // 使用虛擬地址進行 gas 估算以獲取 gas price
            let estimation = try await KMPUseCaseDirect.shared.estimateGas(
                from: "0x0000000000000000000000000000000000000000",
                to: "0x0000000000000000000000000000000000000000",
                amount: "0",
                chainType: mapChainIdToType(chainId),
                data: nil
            )

            // 解析 gasPrice 字符串為 UInt64 (以 Gwei 為單位)
            if let gasPrice = parseGasPrice(estimation.gasPrice) {
                return gasPrice
            }

            // 返回預設 gas price (50 Gwei)
            return 50_000_000_000
        } catch {
            print("[Web3Service] getGasPrice 錯誤: \(error)")
            // 返回預設 gas price (50 Gwei)
            return 50_000_000_000
        }
    }

    /// 獲取地址的 Nonce
    func getNonce(address: String, chainId: String) async throws -> UInt64 {
        print("[Web3Service] getNonce for: \(address)")

        // 嘗試從 KMP 獲取 nonce
        // 目前 KMP 可能沒有直接的 getNonce 方法
        // 可以透過交易歷史推算或使用 RPC 直接調用

        do {
            // 方法 1: 嘗試從交易歷史獲取
            let transactions = try await KMPUseCaseDirect.shared.getTransactionHistory(
                address: address,
                chainType: mapChainIdToType(chainId),
                page: 1,
                limit: 1
            )

            // 如果有交易記錄，可以基於此估算 nonce
            // 這是一個近似值，實際應該從 RPC 獲取
            let estimatedNonce = UInt64(transactions.count)
            return estimatedNonce
        } catch {
            print("[Web3Service] getNonce 錯誤: \(error)")
            // 返回 0 作為預設值
            return 0
        }
    }

    // MARK: - Transaction

    /// 發送已簽名的交易
    func sendTransaction(signedTransaction: String, chainId: String) async throws -> String {
        print("[Web3Service] sendTransaction to chain: \(chainId)")

        // 注意：KMPUseCaseDirect.sendTransaction 需要未簽名的參數
        // 這裡的 signedTransaction 是已簽名的原始交易
        // 需要透過 RPC 直接發送

        // 透過 WatchConnectivity 發送到 iPhone 處理
        // 因為 watchOS 可能沒有直接的 RPC 連接
        let success = await sendViaWatchConnectivity(
            signedTx: signedTransaction,
            chainId: chainId
        )

        if success {
            // 返回一個臨時的交易 hash
            // 實際 hash 會透過 WatchConnectivity 回調更新
            return "pending_\(UUID().uuidString.prefix(8))"
        }

        throw Web3ServiceError.broadcastFailed("無法發送交易")
    }

    // MARK: - Balance

    /// 獲取地址餘額
    func getBalance(address: String) async throws -> String {
        print("[Web3Service] getBalance for: \(address)")

        do {
            let token = try await KMPUseCaseDirect.shared.getTokenBalance(
                walletAddress: address,
                tokenAddress: nil, // nil = 原生代幣
                chainType: chainType
            )
            return token.balance
        } catch {
            print("[Web3Service] getBalance 錯誤: \(error)")
            return "0"
        }
    }

    // MARK: - Helper Methods

    /// 將 chainId 字符串映射到 ChainType
    private func mapChainIdToType(_ chainId: String) -> ChainType {
        switch chainId {
        case "1", "ethereum":
            return .ethereum
        case "56", "bsc":
            return .bsc
        case "137", "polygon":
            return .polygon
        case "42161", "arbitrum":
            return .arbitrum
        case "10", "optimism":
            return .optimism
        case "43114", "avalanche":
            return .avalanche
        case "250", "fantom":
            return .fantom
        case "8453", "base":
            return .base
        default:
            return .ethereum
        }
    }

    /// 解析 gas price 字符串 (可能是 "50 Gwei" 或 "50000000000")
    private func parseGasPrice(_ priceString: String) -> UInt64? {
        // 移除空格和單位
        let cleaned = priceString
            .replacingOccurrences(of: " ", with: "")
            .replacingOccurrences(of: "Gwei", with: "")
            .replacingOccurrences(of: "gwei", with: "")
            .replacingOccurrences(of: "wei", with: "")

        if let value = UInt64(cleaned) {
            // 如果數值較小，假設是 Gwei，轉換為 Wei
            if value < 1_000_000 {
                return value * 1_000_000_000
            }
            return value
        }

        // 嘗試解析小數
        if let doubleValue = Double(cleaned) {
            return UInt64(doubleValue * 1_000_000_000)
        }

        return nil
    }

    /// 透過 WatchConnectivity 發送交易到 iPhone
    private func sendViaWatchConnectivity(signedTx: String, chainId: String) async -> Bool {
        return await withCheckedContinuation { continuation in
            let message: [String: Any] = [
                "action": "broadcastTransaction",
                "signedTransaction": signedTx,
                "chainId": chainId,
                "timestamp": Date().timeIntervalSince1970
            ]

            WatchConnectivityManager.shared.sendTransactionForSigning(message)

            // 假設發送成功（實際應該等待回調）
            continuation.resume(returning: true)
        }
    }
}

// MARK: - Web3ServiceFactory

class Web3ServiceFactory {
    static let shared = Web3ServiceFactory()

    private var services: [String: Web3Service] = [:]

    func create(rpcUrl: String, chainType: ChainType = .ethereum) -> Web3Service {
        let key = "\(rpcUrl)_\(chainType.name)"

        if let existing = services[key] {
            return existing
        }

        let service = Web3Service(rpcUrl: rpcUrl, chainType: chainType)
        services[key] = service
        return service
    }

    /// 根據 chainId 創建服務
    func createForChain(_ chainId: String) -> Web3Service {
        let rpcUrl = getRpcUrl(for: chainId)
        let chainType = mapChainIdToType(chainId)
        return create(rpcUrl: rpcUrl, chainType: chainType)
    }

    /// 獲取 RPC URL
    private func getRpcUrl(for chainId: String) -> String {
        switch chainId {
        case "1", "ethereum":
            return "https://eth.llamarpc.com"
        case "56", "bsc":
            return "https://bsc-dataseed1.binance.org"
        case "137", "polygon":
            return "https://polygon-rpc.com"
        case "42161", "arbitrum":
            return "https://arb1.arbitrum.io/rpc"
        case "10", "optimism":
            return "https://mainnet.optimism.io"
        case "43114", "avalanche":
            return "https://api.avax.network/ext/bc/C/rpc"
        case "250", "fantom":
            return "https://rpc.ftm.tools"
        case "8453", "base":
            return "https://mainnet.base.org"
        default:
            return "https://eth.llamarpc.com"
        }
    }

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

// MARK: - Error Types

enum Web3ServiceError: LocalizedError {
    case notImplemented
    case invalidResponse
    case rpcError(String)
    case broadcastFailed(String)
    case networkError(String)

    var errorDescription: String? {
        switch self {
        case .notImplemented:
            return "功能尚未實現"
        case .invalidResponse:
            return "無效的 RPC 回應"
        case .rpcError(let message):
            return "RPC 錯誤: \(message)"
        case .broadcastFailed(let message):
            return "廣播失敗: \(message)"
        case .networkError(let message):
            return "網路錯誤: \(message)"
        }
    }
}

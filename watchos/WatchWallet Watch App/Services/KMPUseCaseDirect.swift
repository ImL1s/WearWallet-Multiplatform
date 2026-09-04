//
//  KMPUseCaseDirect.swift
//  WatchWallet Watch App
//
//  KMP UseCase Direct Bridge - Simplified Mock for Compilation
//

import Foundation
import coreKmp

/**
 * KMP UseCase Direct Bridge for watchOS
 * Bridges Swift calls to real KMP UseCases via DIContainer
 */
class KMPUseCaseDirect {
    
    // MARK: - Singleton
    static let shared = KMPUseCaseDirect()
    
    // MARK: - Initialization
    private init() {
        print("[KMPUseCaseDirect] ✅ Initialized (Real KMP Mode)")
    }
    
    // MARK: - Wallet UseCases
    
    /// Create a new wallet
    func createWallet(name: String, password: String) async throws -> SwiftWalletAccount {
        print("[KMPUseCaseDirect] createWallet - Real KMP")
        
        guard let useCase = DIContainer.shared.getCreateWalletUseCase() else {
            throw KMPError.internalError("CreateWalletUseCase not available")
        }
        
        let result = try await useCase.invoke(name: name, password: password, chainType: .ethereum, mnemonic: nil)
        
        if let success = result as? ResultSuccess<WalletAccount> {
            if let wallet = success.data {
                return SwiftWalletAccount(
                    id: wallet.id,
                    name: wallet.name,
                    address: wallet.address,
                    chainTypeRaw: wallet.chainType.name, // Use chainType.name as raw type
                    isHardwareWallet: false,
                    createdAt: Date()
                )
            }
        } else if let failure = result as? ResultFailure {
            throw KMPError.transactionFailed(failure.exception.message ?? "Creation failed")
        }
        
        throw KMPError.internalError("Unknown creation error")
    }
    
    /// Import wallet from mnemonic
    func importWallet(mnemonic: String, name: String, password: String, chainType: coreKmp.ChainType = .ethereum) async throws -> SwiftWalletAccount {
        print("[KMPUseCaseDirect] importWallet - Real KMP")
        
        guard let useCase = DIContainer.shared.getImportWalletUseCase() else {
            throw KMPError.internalError("ImportWalletUseCase not available")
        }
        
        let result = try await useCase.importFromMnemonic(name: name, mnemonic: mnemonic, password: password, chainType: chainType)
        
        if let success = result as? ResultSuccess<WalletAccount> {
            if let wallet = success.data {
                return SwiftWalletAccount(
                    id: wallet.id,
                    name: wallet.name,
                    address: wallet.address,
                    chainTypeRaw: wallet.chainType.name,
                    isHardwareWallet: false,
                    createdAt: Date()
                )
            }
        } else if let failure = result as? ResultFailure {
            throw KMPError.transactionFailed(failure.exception.message ?? "Import failed")
        }
        
        throw KMPError.internalError("Unknown import error")
    }
    
    /// Import wallet from private key (Not yet implemented in KMP for watchOS)
    func importPrivateKey(privateKey: String, name: String, password: String, chainType: coreKmp.ChainType) async throws -> SwiftWalletAccount {
        print("[KMPUseCaseDirect] importPrivateKey - Real KMP (Not Implemented)")
        throw KMPError.internalError("Import by private key not yet supported via KMP bridge")
    }
    
    // MARK: - Transaction UseCases
    
    /// Send transaction
    func sendTransaction(
        from: String,
        to: String,
        amount: String,
        chainType: coreKmp.ChainType,
        password: String
    ) async throws -> String {
        print("[KMPUseCaseDirect] sendTransaction - Real KMP")
        
        guard let useCase = DIContainer.shared.getSendTransactionUseCase() else {
            throw KMPError.internalError("SendTransactionUseCase not available")
        }
        
        // Construct request - Note: API might vary, assuming basic parameters for now
        // Based on TransactionHistoryViewModel usage or KMP source
        let result = try await useCase.invoke(
            toAddress: to,
            amount: amount,
            tokenAddress: nil,
            gasPrice: nil,
            gasLimit: nil,
            walletPassword: password
        )
        
        if let success = result as? ResultSuccess<NSString> {
            return success.data as String? ?? ""
        } else if let failure = result as? ResultFailure {
            throw KMPError.transactionFailed(failure.exception.message ?? "Transaction failed")
        }
        
        throw KMPError.internalError("Unknown transaction error")
    }
    
    /// Estimate gas
    func estimateGas(
        from: String,
        to: String,
        amount: String,
        chainType: coreKmp.ChainType,
        data: String? = nil
    ) async throws -> SwiftGasEstimation {
        print("[KMPUseCaseDirect] estimateGas - Real KMP")
        
        guard let useCase = DIContainer.shared.getEstimateGasUseCase() else {
            throw KMPError.internalError("EstimateGasUseCase not available")
        }
        
        let result = try await useCase.invoke(
            from: from,
            to: to,
            value: amount,
            chainType: chainType,
            tokenAddress: nil
        )
        
        if let success = result as? ResultSuccess<EstimateGasUseCase.GasEstimation> {
            if let est = success.data {
                return SwiftGasEstimation(
                    gasPrice: est.gasPrice,
                    gasLimit: est.gasLimit,
                    totalFeeInWei: "", // TotalFee is in ETH string from KMP
                    totalFeeInEth: est.totalFee,
                    totalFeeInUsd: ""
                )
            }
        } else if let failure = result as? ResultFailure {
             throw KMPError.internalError(failure.exception.message ?? "Estimation failed")
        }
        
        throw KMPError.internalError("Unknown estimation error")
    }
    
    // MARK: - Token UseCases
    
    /// Get user tokens
    func getUserTokens(
        address: String,
        chainType: coreKmp.ChainType
    ) async throws -> [SwiftToken] {
        print("[KMPUseCaseDirect] getUserTokens - Real KMP")
        
        guard let useCase = DIContainer.shared.getGetUserTokensUseCase() else {
             return []
        }
        
        let result = try await useCase.invoke(walletAddress: address, chainType: chainType)
        
        if let success = result as? ResultSuccess<NSArray> {
            if let tokens = success.data as? [coreKmp.Token] {
                return tokens.map { t in
                    SwiftToken(
                        address: t.address,
                        symbol: t.symbol,
                        name: t.name,
                        decimals: Int(t.decimals),
                        balance: t.balance,
                        chainTypeRaw: t.chainType.name.lowercased()
                    )
                }
            }
        }
        
        return []
    }
    
    // MARK: - Wallet Listing
    
    /// Get all wallets
    func getAllWallets() async throws -> [SwiftWalletAccount] {
        print("[KMPUseCaseDirect] getAllWallets - Real KMP")
        
        guard let repo = DIContainer.shared.getWalletRepository() else {
             return []
        }
        
        let result = try await repo.getAllWallets()
        
        if let success = result as? ResultSuccess<NSArray> {
            if let accounts = success.data as? [coreKmp.WalletAccount] {
                return accounts.map { w in
                    SwiftWalletAccount(
                        id: w.id,
                        name: w.name,
                        address: w.address,
                        chainTypeRaw: w.chainType.name,
                        isHardwareWallet: false,
                        createdAt: Date()
                    )
                }
            }
        }
        
        return []
    }
    
    /// Get token balance
    func getTokenBalance(
        walletAddress: String,
        tokenAddress: String?,
        chainType: coreKmp.ChainType
    ) async throws -> SwiftToken {
        print("[KMPUseCaseDirect] getTokenBalance - Real KMP")
        
        guard let repo = DIContainer.shared.getTokenRepository() else {
             throw KMPError.internalError("TokenRepository not available")
        }
        
        let result = try await repo.getTokenBalance(
            walletAddress: walletAddress,
            tokenAddress: tokenAddress ?? "",
            chainType: chainType
        )
        
        if let success = result as? ResultSuccess<coreKmp.Token> {
            if let t = success.data {
                return SwiftToken(
                    address: t.address,
                    symbol: t.symbol,
                    name: t.name,
                    decimals: Int(t.decimals),
                    balance: t.balance,
                    chainTypeRaw: t.chainType.name.lowercased()
                )
            }
        }
        
        throw KMPError.internalError("Failed to get balance")
    }

    /// Get transaction history
    func getTransactionHistory(
        address: String,
        chainType: coreKmp.ChainType,
        page: Int = 1,
        limit: Int = 20
    ) async throws -> [SwiftTransaction] {
        print("[KMPUseCaseDirect] getTransactionHistory - Real KMP")
        
        guard let useCase = DIContainer.shared.getGetTransactionHistoryUseCase() else {
            return []
        }
        
        let result = try await useCase.invoke(
            walletAddress: address,
            chainType: chainType,
            limit: Int32(limit)
        )
        
        if let success = result as? ResultSuccess<NSArray> {
            if let kmpTransactions = success.data as? [coreKmp.Transaction] {
                return kmpTransactions.map { t in
                    SwiftTransaction(
                        hash: t.hash,
                        from: t.from,
                        to: t.to,
                        value: t.value,
                        status: t.status.name.lowercased(),
                        timestamp: Date(timeIntervalSince1970: Double(t.timestamp?.epochSeconds ?? 0))
                    )
                }
            }
        }
        
        return []
    }
    
    // MARK: - DeFi UseCases
    
    /// Get swap quote
    func getSwapQuote(
        fromAddress: String,
        fromToken: String,
        toToken: String,
        amount: String,
        chainType: coreKmp.ChainType
    ) async throws -> SwiftSwapQuote {
        print("[KMPUseCaseDirect] getSwapQuote - Real KMP")
        
        guard let aggregator = DIContainer.shared.getDeFiAggregator() else {
            throw KMPError.internalError("DeFiAggregator not available")
        }
        
        // Create SwapParams
        let multiChainType = mapToMultiChainType(chainType)
        let params = DeFiAggregator.SwapParams(
            fromToken: fromToken,
            toToken: toToken,
            amount: amount,
            slippage: 0.5,
            deadline: nil,
            fromChain: multiChainType,
            toChain: multiChainType,
            userAddress: fromAddress
        )
        
        let result = try await aggregator.getBestSwapRoute(
            chainType: multiChainType,
            params: params
        )
        
        if let success = result as? ResultSuccess<DeFiAggregator.SwapQuote> {
            if let quote = success.data {
                return SwiftSwapQuote(
                    protocol: quote.`protocol`, // Kotlin 'protocol' property
                    fromAmount: quote.fromAmount,
                    toAmount: quote.toAmount,
                    fee: quote.fee,
                    estimatedGas: quote.estimatedGas,
                    priceImpact: quote.priceImpact,
                    provider: quote.provider,
                    rawData: quote.rawData
                )
            }
        } else if let failure = result as? ResultFailure {
            throw KMPError.internalError(failure.exception.message ?? "Failed to get quote")
        }
        
        throw KMPError.internalError("Unknown quote error")
    }
    
    /// Execute swap
    func executeSwap(
        quote: SwiftSwapQuote,
        fromAddress: String
    ) async throws -> String {
        print("[KMPUseCaseDirect] executeSwap - Real KMP")
        
        guard let aggregator = DIContainer.shared.getDeFiAggregator() else {
            throw KMPError.internalError("DeFiAggregator not available")
        }
        
        // 1. Retrieve the private key from SecureWalletManager
        // We look up the private key using the fromAddress
        let walletsResult = SecureWalletManager.shared.getAllWallets()
        var targetWalletId: String? = nil
        
        if case .success(let wallets) = walletsResult {
            // Note: SecureWalletData stores metadata, but we might need to find which one matches the address.
            // Since SecureWalletData doesn't store address directly, we might need a mapping or check KMP repo.
            // For now, let's assume we can find it via KMP repo first.
            if let repo = DIContainer.shared.getWalletRepository() {
                let kmpWalletResult = try await repo.getWalletByAddress(address: fromAddress)
                if let success = kmpWalletResult as? ResultSuccess<coreKmp.WalletAccount>, let wallet = success.data {
                    targetWalletId = wallet.id
                }
            }
        }
        
        guard let walletId = targetWalletId else {
            throw KMPError.walletNotFound
        }
        
        // Retrieve key from Keychain
        let keyResult = SecureWalletManager.shared.getWalletPrivateKey(walletId: walletId)
        
        let privateKey: String
        switch keyResult {
        case .success(let key):
            privateKey = key
        case .failure(let error):
            throw KMPError.internalError("Failed to retrieve private key: \(error.localizedDescription)")
        }
        
        // 2. Map SwiftSwapQuote back to KMP SwapQuote
        let kmpQuote = DeFiAggregator.SwapQuote(
            protocol: quote.protocol,
            chainType: .ethereum, // Default or pass from quote
            fromToken: "", 
            toToken: "",
            fromAmount: quote.fromAmount,
            toAmount: quote.toAmount,
            price: 0,
            priceImpact: quote.priceImpact,
            fee: quote.fee,
            estimatedGas: quote.estimatedGas,
            route: [],
            provider: quote.provider,
            rawData: quote.rawData
        )
        
        // 3. Execute
        let result = try await aggregator.executeSwap(
            quote: kmpQuote,
            userAddress: fromAddress,
            privateKey: privateKey
        )
        
        if let success = result as? ResultSuccess<TransactionResult> {
            if let tx = success.data {
                // Try 'hash' first, then 'transactionHash' if 'hash' is hidden
                return tx.hash // or tx.transactionHash
            }
            return "Success"
        } else if let failure = result as? ResultFailure {
            throw KMPError.transactionFailed(failure.exception.message ?? "Swap failed")
        }
        
        throw KMPError.internalError("Unknown swap error")
    }
    
    private func mapToMultiChainType(_ type: coreKmp.ChainType) -> coreKmp.MultiChainType {
        switch type {
        case .ethereum: return .ethereum
        case .bsc: return .bsc
        case .polygon: return .polygon
        case .arbitrum: return .arbitrum
        case .optimism: return .optimism
        case .avalanche: return .avalanche
        case .fantom: return .fantom
        case .cronos: return .cronos
        case .base: return .base
        case .moonbeam: return .moonbeam
        case .celo: return .celo
        case .bitcoin: return .bitcoin
        case .litecoin: return .litecoin
        case .dogecoin: return .dogecoin
        case .bitcoinCash: return .bitcoinCash
        case .solana: return .solana
        case .tron: return .tron
        case .polkadot: return .polkadot
        case .cardano: return .cardano
        case .monero: return .monero
        default: return .ethereum // Fallback
        }
    }
    
    private func mapToRangoChainName(_ type: coreKmp.ChainType) -> String {
        switch type {
        case .ethereum: return "ETH"
        case .bsc: return "BSC"
        case .polygon: return "POLYGON"
        case .arbitrum: return "ARBITRUM"
        case .optimism: return "OPTIMISM"
        case .base: return "BASE"
        case .avalanche: return "AVAX_CCHAIN"
        default: return "ETH"
        }
    }
    
    /// Get supported tokens for swap
    func getSupportedTokens(chainType: coreKmp.ChainType) async throws -> [SwiftSwapToken] {
        print("[KMPUseCaseDirect] getSupportedTokens - Real KMP")
        
        guard let repo = DIContainer.shared.getRangoRepository() else {
             print("[KMPUseCaseDirect] RangoRepository not available")
             return []
        }
        
        let rangoChainName = mapToRangoChainName(chainType)
        
        // Fetch tokens from Rango Meta
        let result = try await repo.getPopularTokens(blockchain: rangoChainName)
        
        if let success = result as? ResultSuccess<NSArray> {
            if let tokens = success.data as? [coreKmp.RangoTokenMeta] {
                return tokens.map { t in
                     SwiftSwapToken(
                        symbol: t.symbol,
                        name: t.name ?? t.symbol,
                        address: t.address ?? "",
                        logoUrl: t.image,
                        price: t.usdPrice?.doubleValue ?? 0.0
                     )
                }
            }
        }
        
        return []
    }
}

// MARK: - Flow Collector Helper

class FlowCollector<T>: Kotlinx_coroutines_coreFlowCollector {
    let callback: (Any?) -> Void

    init(callback: @escaping (Any?) -> Void) {
        self.callback = callback
    }

    func emit(value: Any?, completionHandler: @escaping (Error?) -> Void) {
        callback(value)
        completionHandler(nil)
    }
}

// MARK: - Swift Types

struct SwiftWalletAccount: Identifiable {
    let id: String
    let name: String
    let address: String
    let chainTypeRaw: String
    let isHardwareWallet: Bool
    let createdAt: Date
}

struct SwiftGasEstimation {
    let gasPrice: String
    let gasLimit: String
    let totalFeeInWei: String
    let totalFeeInEth: String
    let totalFeeInUsd: String
}

struct SwiftToken: Identifiable {
    var id: String { address }
    let address: String
    let symbol: String
    let name: String
    let decimals: Int
    let balance: String
    let chainTypeRaw: String
}

struct SwiftTransaction: Identifiable {
    var id: String { hash }
    let hash: String
    let from: String
    let to: String
    let value: String
    let status: String
    let timestamp: Date
}

struct SwiftSwapQuote {
    let `protocol`: String
    let fromAmount: String
    let toAmount: String
    let fee: String
    let estimatedGas: String
    let priceImpact: Double
    let provider: String
    let rawData: String
}

struct SwiftSwapToken: Identifiable, Hashable {
    let id = UUID()
    let symbol: String
    let name: String
    let address: String
    let logoUrl: String?
    let price: Double
}

// MARK: - Error Types

enum KMPError: LocalizedError {
    case parseError(String)
    case transactionFailed(String)
    case walletNotFound
    case internalError(String)
    
    var errorDescription: String? {
        switch self {
        case .parseError(let message):
            return "解析錯誤: \(message)"
        case .transactionFailed(let message):
            return "交易失敗: \(message)"
        case .walletNotFound:
            return "找不到錢包"
        case .internalError(let message):
            return "內部錯誤: \(message)"
        }
    }
}

// MARK: - KMP Wallet typealias
// typealias KMPWallet is defined in KMPTypeAliases.swift
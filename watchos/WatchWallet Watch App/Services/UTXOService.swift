//
//  UTXOService.swift
//  WatchWallet Watch App
//
//  UTXO 區塊鏈服務層 - 模擬實現
//

import Foundation
import coreKmp

// MARK: - Chain State Manager (Mock)
/// 簡化的鏈狀態管理器
class ChainStateManager: ObservableObject {
    static let shared = ChainStateManager()
    
    @Published var currentChain: UTXOChainType = .bitcoin
    
    private init() {}
    
    func updateCurrentChain(_ chain: UTXOChainType) {
        currentChain = chain
    }
}

@MainActor
class UTXOService: ObservableObject {
    static let shared = UTXOService()
    
    @Published var currentChain: UTXOChainType = .bitcoin
    @Published var balance: UTXOBalance?
    @Published var utxos: [UTXO] = []
    @Published var recentTransactions: [UTXOTransaction] = []
    @Published var feeEstimate: UTXOFeeEstimate?
    @Published var isLoading = false
    @Published var error: String?
    
    private let chainStateManager = ChainStateManager.shared
    private let walletRepository = WalletRepositoryManager.shared
    private let utxoClient = DIContainer.shared.getUTXOApiClient()
    
    private init() {}
    
    // MARK: - Chain Management
    
    func switchChain(_ chain: UTXOChainType) {
        currentChain = chain
        chainStateManager.updateCurrentChain(chain)
        refreshData()
    }
    
    // MARK: - Data Loading
    
    func refreshData() {
        Task {
            await loadBalance()
            await loadUTXOs()
            await estimateFees()
        }
    }
    
    @MainActor
    func getAddress(for chain: UTXOChainType) async -> String? {
        print("[UTXOService] getAddress for \(chain.name)")
        
        guard let cryptoProvider = DIContainer.shared.getCryptoProvider(),
              let activeWallet = try? await walletRepository.getActiveWalletAsync() else {
            return nil
        }
        
        // Get mnemonic for current wallet
        let mnemonicResult = walletRepository.getWalletMnemonic(walletId: activeWallet.id)
        guard case .success(let mnemonic) = mnemonicResult else {
            return nil
        }
        
        do {
            let kmpChain = getKMPChainType(chain)
            let path = getDerivationPath(for: chain)
            
            let keyPair = try await cryptoProvider.generateKeyPairFromMnemonic(
                mnemonic: mnemonic,
                derivationPath: path,
                chainType: kmpChain
            )
            
            return try await cryptoProvider.deriveAddress(publicKey: keyPair.publicKey)
        } catch {
            print("[UTXOService] Failed to derive address: \(error)")
            return nil
        }
    }
    
    @MainActor
    private func loadBalance() async {
        guard let address = await getAddress(for: currentChain),
              let client = utxoClient else {
            return
        }
        
        isLoading = true
        error = nil
        
        do {
            let kmpChain = getKMPChainType(currentChain)
            // getBalance returns Long directly
            let result = try await client.getBalance(address: address, chainType: kmpChain)
            
            self.balance = UTXOBalance(
                chain: currentChain,
                address: address,
                confirmed: result.int64Value,
                unconfirmed: 0,
                utxoCount: 0,
                lastUpdated: Date()
            )
        } catch {
            self.error = "載入餘額出錯: \(error.localizedDescription)"
        }
        
        isLoading = false
    }
    
    @MainActor
    private func loadUTXOs() async {
        guard let address = await getAddress(for: currentChain),
              let client = utxoClient else {
            return
        }
        
        do {
            let kmpChain = getKMPChainType(currentChain)
            // getUTXOs returns List<UTXO> directly
            let result = try await client.getUTXOs(address: address, chainType: kmpChain)
            
            self.utxos = result.map { u in
                UTXO(
                    id: u.txid + "-\(u.vout)",
                    txid: u.txid,
                    vout: Int(u.vout),
                    value: u.value,
                    scriptPubKey: u.scriptPubKey ?? "",
                    address: address,
                    confirmations: 0, // UTXO model in KMP might not have confirmations
                    spendable: true   // Default to true
                )
            }
        } catch {
            print("[UTXOService] Failed to load UTXOs: \(error)")
        }
    }
    
    @MainActor
    private func estimateFees() async {
        guard let client = utxoClient else { return }
        
        do {
            let kmpChain = getKMPChainType(currentChain)
            // getFeeEstimate returns Long directly
            // We need to call it for different priorities or mock them based on one call
            let normalFee = try await client.getFeeEstimate(chainType: kmpChain, priority: .normal)
            
            self.feeEstimate = UTXOFeeEstimate(
                chain: currentChain,
                fastestFee: normalFee.int64Value * 2,
                halfHourFee: Int64(Double(normalFee.int64Value) * 1.5),
                hourFee: normalFee.int64Value,
                economyFee: normalFee.int64Value / 2
            )
        } catch {
            print("[UTXOService] Failed to estimate fees: \(error)")
        }
    }
    
    // MARK: - Transaction Sending
    
    // 發送交易
    func sendTransaction(to: String, amount: Int64, feeRate: Int64, password: String) async throws -> String {
        print("[UTXOService] sendTransaction Real KMP")
        
        guard let useCase = DIContainer.shared.getSendUTXOTransactionUseCase() else {
            throw UTXOServiceError.noActiveWallet
        }
        
        let result = try await useCase.invoke(
            toAddress: to,
            amount: amount,
            chainType: getKMPChainType(currentChain),
            feeRate: feeRate,
            password: password
        )
        
        // Result is Flow<Result<String>>
        return try await withCheckedThrowingContinuation { continuation in
            var completed = false
            result.collect(collector: FlowCollector<coreKmp.Result<NSString>> { value in
                if completed { return }
                
                if let success = value as? ResultSuccess<NSString> {
                    completed = true
                    continuation.resume(returning: (success.data as String?) ?? "")
                    Task { @MainActor in self.refreshData() }
                } else if let failure = value as? ResultFailure {
                    completed = true
                    continuation.resume(throwing: UTXOServiceError.transactionFailed(failure.exception.message ?? "發送失敗"))
                }
            }) { error in
                if !completed {
                    completed = true
                    if let error = error {
                        continuation.resume(throwing: error)
                    } else {
                        continuation.resume(throwing: UTXOServiceError.transactionFailed("Unknown error"))
                    }
                }
            }
        }
    }
    
    // MARK: - Helper Functions
    
    private func getKMPChainType(_ chain: UTXOChainType) -> coreKmp.ChainType {
        switch chain {
        case .bitcoin: return .bitcoin
        case .litecoin: return .litecoin
        case .dogecoin: return .dogecoin
        case .bitcoinCash: return .bitcoinCash
        }
    }
    
    private func getDerivationPath(for chain: UTXOChainType) -> String {
        switch chain {
        case .bitcoin:
            return "m/84'/0'/0'/0/0"  // BIP84 for SegWit
        case .litecoin:
            return "m/84'/2'/0'/0/0"
        case .dogecoin:
            return "m/44'/3'/0'/0/0"
        case .bitcoinCash:
            return "m/44'/145'/0'/0/0"
        }
    }
}

// MARK: - Error Types (UTXOService specific)
enum UTXOServiceError: LocalizedError {
    case noActiveWallet
    case insufficientFunds
    case invalidAddress
    case transactionFailed(String)
    
    var errorDescription: String? {
        switch self {
        case .noActiveWallet:
            return "沒有找到活躍的錢包"
        case .insufficientFunds:
            return "餘額不足"
        case .invalidAddress:
            return "無效的地址"
        case .transactionFailed(let reason):
            return "交易失敗: \(reason)"
        }
    }
}
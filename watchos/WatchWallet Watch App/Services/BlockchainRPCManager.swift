//
//  BlockchainRPCManager.swift
//  WatchWallet Watch App
//
//  Blockchain RPC endpoint management for watchOS
//

import Foundation
import Combine

// MARK: - RPC Response Models

struct RPCResponse<T: Codable>: Codable {
    let jsonrpc: String
    let id: Int
    let result: T?
    let error: RPCError?
}

struct RPCError: Codable, Error {
    let code: Int
    let message: String
    let data: String?
}

struct BalanceResponse: Codable {
    let balance: String
    
    var balanceInEther: Double {
        guard let balanceWei = UInt64(balance.replacingOccurrences(of: "0x", with: ""), radix: 16) else {
            return 0.0
        }
        return Double(balanceWei) / 1e18 // Convert from Wei to Ether
    }
}

struct TransactionResponse: Codable {
    let blockHash: String?
    let blockNumber: String?
    let from: String
    let gas: String
    let gasPrice: String
    let hash: String
    let input: String
    let nonce: String
    let to: String?
    let transactionIndex: String?
    let value: String
    let type: String?
    
    var valueInEther: Double {
        guard let valueWei = UInt64(value.replacingOccurrences(of: "0x", with: ""), radix: 16) else {
            return 0.0
        }
        return Double(valueWei) / 1e18
    }
}

struct TransactionReceipt: Codable {
    let blockHash: String
    let blockNumber: String
    let contractAddress: String?
    let cumulativeGasUsed: String
    let from: String
    let gasUsed: String
    let logs: [TransactionLog]
    let status: String
    let to: String?
    let transactionHash: String
    let transactionIndex: String
    
    var isSuccessful: Bool {
        return status == "0x1"
    }
}

struct TransactionLog: Codable {
    let address: String
    let topics: [String]
    let data: String
    let blockNumber: String
    let transactionHash: String
    let transactionIndex: String
    let blockHash: String
    let logIndex: String
    let removed: Bool
}

// MARK: - Network Configuration

struct NetworkConfig {
    let name: String
    let rpcURL: String
    let chainId: Int
    let symbol: String
    let blockExplorerURL: String
    let isTestnet: Bool
    
    static let ethereum = NetworkConfig(
        name: "Ethereum",
        rpcURL: "https://eth-mainnet.g.alchemy.com/v2/demo",
        chainId: 1,
        symbol: "ETH",
        blockExplorerURL: "https://etherscan.io",
        isTestnet: false
    )
    
    static let sepolia = NetworkConfig(
        name: "Sepolia Testnet",
        rpcURL: "https://eth-sepolia.g.alchemy.com/v2/demo",
        chainId: 11155111,
        symbol: "ETH",
        blockExplorerURL: "https://sepolia.etherscan.io",
        isTestnet: true
    )
    
    static let bsc = NetworkConfig(
        name: "Binance Smart Chain",
        rpcURL: "https://bsc-dataseed.binance.org",
        chainId: 56,
        symbol: "BNB",
        blockExplorerURL: "https://bscscan.com",
        isTestnet: false
    )
    
    static let polygon = NetworkConfig(
        name: "Polygon",
        rpcURL: "https://polygon-rpc.com",
        chainId: 137,
        symbol: "MATIC",
        blockExplorerURL: "https://polygonscan.com",
        isTestnet: false
    )
    
    static let cronos = NetworkConfig(
        name: "Cronos",
        rpcURL: "https://evm.cronos.org",
        chainId: 25,
        symbol: "CRO",
        blockExplorerURL: "https://cronoscan.com",
        isTestnet: false
    )
    
    static let allNetworks: [NetworkConfig] = [
        .ethereum, .sepolia, .bsc, .polygon, .cronos
    ]
}

// MARK: - RPC Manager

@MainActor
class BlockchainRPCManager: ObservableObject {
    static let shared = BlockchainRPCManager()
    
    // MARK: - Properties
    @Published var currentNetwork: NetworkConfig = .ethereum
    @Published var isLoading = false
    @Published var lastError: RPCError?
    
    private let session = URLSession.shared
    private let timeout: TimeInterval = 30.0
    
    // MARK: - Initialization
    private init() {}
    
    // MARK: - Network Management
    
    func setCurrentNetwork(_ network: NetworkConfig) {
        currentNetwork = network
        print("[BlockchainRPCManager] Switched to network: \(network.name)")
    }
    
    func getNetworkByChainId(_ chainId: Int) -> NetworkConfig? {
        return NetworkConfig.allNetworks.first { $0.chainId == chainId }
    }
    
    // MARK: - RPC Methods
    
    /// Get account balance
    /// - Parameters:
    ///   - address: Ethereum address
    ///   - network: Network configuration (optional, uses current if nil)
    /// - Returns: Balance in wei as hex string
    func getBalance(for address: String, network: NetworkConfig? = nil) async -> Result<String, RPCError> {
        let targetNetwork = network ?? currentNetwork
        
        let params = [
            address,
            "latest"
        ]
        
        return await makeRPCCall(
            method: "eth_getBalance",
            params: params,
            network: targetNetwork
        )
    }
    
    /// Get transaction count (nonce)
    /// - Parameters:
    ///   - address: Ethereum address
    ///   - network: Network configuration (optional, uses current if nil)
    /// - Returns: Transaction count as hex string
    func getTransactionCount(for address: String, network: NetworkConfig? = nil) async -> Result<String, RPCError> {
        let targetNetwork = network ?? currentNetwork
        
        let params = [
            address,
            "latest"
        ]
        
        return await makeRPCCall(
            method: "eth_getTransactionCount",
            params: params,
            network: targetNetwork
        )
    }
    
    /// Get gas price
    /// - Parameter network: Network configuration (optional, uses current if nil)
    /// - Returns: Gas price as hex string
    func getGasPrice(network: NetworkConfig? = nil) async -> Result<String, RPCError> {
        let targetNetwork = network ?? currentNetwork
        
        return await makeRPCCall(
            method: "eth_gasPrice",
            params: [],
            network: targetNetwork
        )
    }
    
    /// Estimate gas for transaction
    /// - Parameters:
    ///   - transaction: Transaction parameters
    ///   - network: Network configuration (optional, uses current if nil)
    /// - Returns: Estimated gas as hex string
    func estimateGas(for transaction: [String: Any], network: NetworkConfig? = nil) async -> Result<String, RPCError> {
        let targetNetwork = network ?? currentNetwork
        
        let params = [transaction]
        
        return await makeRPCCall(
            method: "eth_estimateGas",
            params: params,
            network: targetNetwork
        )
    }
    
    /// Send raw transaction
    /// - Parameters:
    ///   - signedTransaction: Signed transaction data as hex string
    ///   - network: Network configuration (optional, uses current if nil)
    /// - Returns: Transaction hash
    func sendRawTransaction(_ signedTransaction: String, network: NetworkConfig? = nil) async -> Result<String, RPCError> {
        let targetNetwork = network ?? currentNetwork
        
        let params = [signedTransaction]
        
        return await makeRPCCall(
            method: "eth_sendRawTransaction",
            params: params,
            network: targetNetwork
        )
    }
    
    /// Get transaction by hash
    /// - Parameters:
    ///   - hash: Transaction hash
    ///   - network: Network configuration (optional, uses current if nil)
    /// - Returns: Transaction details
    func getTransaction(by hash: String, network: NetworkConfig? = nil) async -> Result<TransactionResponse, RPCError> {
        let targetNetwork = network ?? currentNetwork
        
        let params = [hash]
        
        return await makeRPCCall(
            method: "eth_getTransactionByHash",
            params: params,
            network: targetNetwork,
            responseType: TransactionResponse.self
        )
    }
    
    /// Get transaction receipt
    /// - Parameters:
    ///   - hash: Transaction hash
    ///   - network: Network configuration (optional, uses current if nil)
    /// - Returns: Transaction receipt
    func getTransactionReceipt(by hash: String, network: NetworkConfig? = nil) async -> Result<TransactionReceipt, RPCError> {
        let targetNetwork = network ?? currentNetwork
        
        let params = [hash]
        
        return await makeRPCCall(
            method: "eth_getTransactionReceipt",
            params: params,
            network: targetNetwork,
            responseType: TransactionReceipt.self
        )
    }
    
    /// Get current block number
    /// - Parameter network: Network configuration (optional, uses current if nil)
    /// - Returns: Block number as hex string
    func getBlockNumber(network: NetworkConfig? = nil) async -> Result<String, RPCError> {
        let targetNetwork = network ?? currentNetwork
        
        return await makeRPCCall(
            method: "eth_blockNumber",
            params: [],
            network: targetNetwork
        )
    }
    
    // MARK: - Private Methods
    
    private func makeRPCCall<T: Codable>(
        method: String,
        params: [Any],
        network: NetworkConfig,
        responseType: T.Type = String.self
    ) async -> Result<T, RPCError> {
        
        isLoading = true
        defer { isLoading = false }
        
        // Construct RPC request
        let rpcRequest = [
            "jsonrpc": "2.0",
            "method": method,
            "params": params,
            "id": 1
        ] as [String: Any]
        
        guard let url = URL(string: network.rpcURL) else {
            let error = RPCError(code: -1, message: "Invalid RPC URL", data: nil)
            lastError = error
            return .failure(error)
        }
        
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.timeoutInterval = timeout
        
        do {
            let requestData = try JSONSerialization.data(withJSONObject: rpcRequest)
            request.httpBody = requestData
            
            print("[BlockchainRPCManager] Making RPC call: \(method) to \(network.name)")
            
            let (data, response) = try await session.data(for: request)
            
            // Check HTTP response
            if let httpResponse = response as? HTTPURLResponse {
                guard httpResponse.statusCode == 200 else {
                    let error = RPCError(
                        code: httpResponse.statusCode,
                        message: "HTTP Error: \(httpResponse.statusCode)",
                        data: nil
                    )
                    lastError = error
                    return .failure(error)
                }
            }
            
            // Parse JSON response
            let rpcResponse = try JSONDecoder().decode(RPCResponse<T>.self, from: data)
            
            if let error = rpcResponse.error {
                lastError = error
                return .failure(error)
            }
            
            guard let result = rpcResponse.result else {
                let error = RPCError(code: -2, message: "No result in response", data: nil)
                lastError = error
                return .failure(error)
            }
            
            print("[BlockchainRPCManager] RPC call successful: \(method)")
            lastError = nil
            return .success(result)
            
        } catch {
            let rpcError = RPCError(
                code: -3,
                message: "Network error: \(error.localizedDescription)",
                data: nil
            )
            lastError = rpcError
            return .failure(rpcError)
        }
    }
    
    // MARK: - Utility Methods
    
    /// Check if network is available
    /// - Parameter network: Network configuration
    /// - Returns: True if network is accessible
    func isNetworkAvailable(_ network: NetworkConfig) async -> Bool {
        let result = await getBlockNumber(network: network)
        return result.isSuccess
    }
    
    /// Get formatted balance
    /// - Parameters:
    ///   - address: Ethereum address
    ///   - network: Network configuration (optional, uses current if nil)
    /// - Returns: Balance formatted as double in native currency
    func getFormattedBalance(for address: String, network: NetworkConfig? = nil) async -> Result<Double, RPCError> {
        let balanceResult = await getBalance(for: address, network: network)
        
        switch balanceResult {
        case .success(let balanceHex):
            guard let balanceWei = UInt64(balanceHex.replacingOccurrences(of: "0x", with: ""), radix: 16) else {
                return .failure(RPCError(code: -4, message: "Invalid balance format", data: nil))
            }
            let balanceEther = Double(balanceWei) / 1e18
            return .success(balanceEther)
            
        case .failure(let error):
            return .failure(error)
        }
    }
    
    /// Create transaction parameters
    /// - Parameters:
    ///   - from: Sender address
    ///   - to: Recipient address
    ///   - value: Value in wei (hex string)
    ///   - gasLimit: Gas limit (hex string)
    ///   - gasPrice: Gas price (hex string)
    ///   - nonce: Transaction nonce (hex string)
    ///   - data: Transaction data (optional)
    /// - Returns: Transaction parameters dictionary
    func createTransactionParams(
        from: String,
        to: String,
        value: String,
        gasLimit: String,
        gasPrice: String,
        nonce: String,
        data: String? = nil
    ) -> [String: Any] {
        var params: [String: Any] = [
            "from": from,
            "to": to,
            "value": value,
            "gas": gasLimit,
            "gasPrice": gasPrice,
            "nonce": nonce
        ]
        
        if let data = data {
            params["data"] = data
        }
        
        return params
    }
}

// MARK: - Result Extension

extension Result {
    var isSuccess: Bool {
        switch self {
        case .success:
            return true
        case .failure:
            return false
        }
    }
}

// MARK: - Utility Functions

/// Convert Wei to Ether
/// - Parameter wei: Wei amount as string
/// - Returns: Ether amount as double
func weiToEther(_ wei: String) -> Double {
    let cleanWei = wei.replacingOccurrences(of: "0x", with: "")
    guard let weiValue = UInt64(cleanWei, radix: 16) else {
        return 0.0
    }
    return Double(weiValue) / 1e18
}

/// Convert Ether to Wei
/// - Parameter ether: Ether amount as double
/// - Returns: Wei amount as hex string
func etherToWei(_ ether: Double) -> String {
    let weiValue = ether * 1e18
    return String(format: "0x%llx", UInt64(weiValue))
}

/// Convert decimal to hex
/// - Parameter decimal: Decimal number
/// - Returns: Hex string with 0x prefix
func decimalToHex(_ decimal: Int) -> String {
    return String(format: "0x%x", decimal)
}
//
//  KMPResultParser.swift
//  WatchWallet Watch App
//
//  Helper to parse KMP Result types in Swift
//

import Foundation
import coreKmp

/// Helper class to parse KMP Result types
class KMPResultParser {
    
    /// Parse a Swift.Result<String> from KMP
    static func parseStringResult(_ result: Any) -> String? {
        let resultString = String(describing: result)
        
        // Check if it's a Success result
        if resultString.contains("Success(") {
            // Extract the value from Success(value)
            if let startIndex = resultString.range(of: "Success(")?.upperBound,
               let endIndex = resultString.lastIndex(of: ")") {
                let value = String(resultString[startIndex..<endIndex])
                // Remove quotes if present
                return value.trimmingCharacters(in: CharacterSet(charactersIn: "\""))
            }
        }
        
        return nil
    }
    
    /// Parse a Swift.Result<List<TokenBalance>> from KMP
    static func parseTokenBalancesResult(_ result: Any) -> [coreKmp.TokenBalance]? {
        let resultString = String(describing: result)
        
        // Check if it's a Success result
        if resultString.contains("Success(") {
            // For now, return empty array as complex parsing requires more work
            // In production, you would implement proper parsing or use SKIE
            print("[KMPResultParser] TokenBalance parsing not yet implemented - returning empty array")
            return []
        }
        
        return nil
    }
    
    /// Parse a Swift.Result<List<Transaction>> from KMP
    static func parseTransactionsResult(_ result: Any) -> [Transaction]? {
        let resultString = String(describing: result)
        
        // Check if it's a Success result
        if resultString.contains("Success(") {
            // For now, return empty array as JSON-RPC doesn't support tx history
            return []
        }
        
        return nil
    }
    
    /// Parse a Swift.Result<List<Transaction>> from KMP (for transaction history)
    static func parseTransactionHistoryResult(_ result: Any) -> [coreKmp.Transaction]? {
        let resultString = String(describing: result)
        
        // Check if it's a Success result
        if resultString.contains("Success(") {
            // The KMP GetTransactionHistoryUseCase will return actual transaction data
            // when blockchain explorer API keys are configured (via 1Password)
            
            print("[KMPResultParser] Parsing transaction history result: \(resultString)")
            
            // For now, return empty array but in production this would parse the actual
            // transaction objects from the blockchain explorer API response
            // The KMP layer handles the API integration with Etherscan, BSCScan, PolygonScan etc.
            
            if resultString.contains("Success([])") {
                // Empty result - either no transactions or API keys not configured
                print("[KMPResultParser] Empty transaction history - check if API keys are configured")
                return []
            } else {
                // Non-empty result - would parse actual transaction objects here
                print("[KMPResultParser] Transaction history available but parsing not yet implemented")
                return []
            }
        }
        
        return nil
    }
    
    /// Check if a Result is a failure
    static func isFailure(_ result: Any) -> Bool {
        let resultString = String(describing: result)
        return resultString.contains("Failure(")
    }
    
    /// Extract error message from a failed Result
    static func getErrorMessage(_ result: Any) -> String? {
        let resultString = String(describing: result)
        
        if resultString.contains("Failure(") {
            // Extract the error message from Failure(ErrorType(...))
            if let startIndex = resultString.range(of: "Failure(")?.upperBound,
               let endIndex = resultString.lastIndex(of: ")") {
                let errorPart = String(resultString[startIndex..<endIndex])
                
                // Try to extract the message from NetworkError("message")
                if let msgStart = errorPart.range(of: "NetworkError(\"")?.upperBound,
                   let msgEnd = errorPart.range(of: "\")", range: msgStart..<errorPart.endIndex)?.lowerBound {
                    return String(errorPart[msgStart..<msgEnd])
                }
                
                return errorPart
            }
        }
        
        return nil
    }
    
    /// Convert wei string to ETH with proper decimal formatting
    static func weiToEth(_ weiString: String) -> String {
        guard let weiValue = Double(weiString) else { return "0" }
        
        // 1 ETH = 10^18 wei
        let ethValue = weiValue / 1_000_000_000_000_000_000.0
        
        // Format with appropriate decimal places
        let formatter = NumberFormatter()
        formatter.numberStyle = .decimal
        formatter.minimumFractionDigits = 2
        formatter.maximumFractionDigits = 6
        formatter.groupingSeparator = ","
        
        return formatter.string(from: NSNumber(value: ethValue)) ?? "0"
    }
}

// MARK: - Extension for better Result handling
extension KMPResultParser {
    
    /// Generic result handler that returns a Swift Result type
    static func handleKMPResult<T>(_ kmpResult: Any, 
                                   parser: (Any) -> T?) -> Swift.Result<T, Error> {
        if isFailure(kmpResult) {
            let errorMessage = getErrorMessage(kmpResult) ?? "Unknown error"
            return .failure(NSError(domain: "KMPError", 
                                  code: -1, 
                                  userInfo: [NSLocalizedDescriptionKey: errorMessage]))
        }
        
        if let parsedValue = parser(kmpResult) {
            return .success(parsedValue)
        }
        
        return .failure(NSError(domain: "KMPError", 
                              code: -2, 
                              userInfo: [NSLocalizedDescriptionKey: "Failed to parse KMP result"]))
    }
}
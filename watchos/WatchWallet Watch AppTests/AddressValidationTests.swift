import XCTest
@testable import WatchWallet_Watch_App

/// Comprehensive tests for multi-chain address validation
final class AddressValidationTests: XCTestCase {

    // MARK: - Ethereum Address Validation

    func testValidEthereumAddresses() {
        let validAddresses = [
            "0x742d35Cc6634C0532925a3b844Bc9e7595f4E5b2",
            "0xAb5801a7D398351b8bE11C439e05C5B3259aeC9B",
            "0x0000000000000000000000000000000000000000",
            "0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF",
            "0x1234567890abcdef1234567890abcdef12345678"
        ]

        for address in validAddresses {
            XCTAssertTrue(isValidEthereumAddress(address), "Should be valid: \(address)")
        }
    }

    func testInvalidEthereumAddresses() {
        let invalidAddresses = [
            "0x742d35Cc6634C0532925a3b844Bc9e7595f4E5b", // Too short
            "0x742d35Cc6634C0532925a3b844Bc9e7595f4E5b2a", // Too long
            "742d35Cc6634C0532925a3b844Bc9e7595f4E5b2", // Missing 0x prefix
            "0xGGGG35Cc6634C0532925a3b844Bc9e7595f4E5b2", // Invalid hex chars
            "", // Empty
            "0x" // Just prefix
        ]

        for address in invalidAddresses {
            XCTAssertFalse(isValidEthereumAddress(address), "Should be invalid: \(address)")
        }
    }

    // MARK: - Bitcoin Address Validation

    func testValidBitcoinLegacyAddresses() {
        let validAddresses = [
            "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa", // Genesis block address
            "1BvBMSEYstWetqTFn5Au4m4GFg7xJaNVN2",
            "3J98t1WpEZ73CNmQviecrnyiWrnqRhWNLy" // P2SH address
        ]

        for address in validAddresses {
            XCTAssertTrue(isValidBitcoinAddress(address), "Should be valid: \(address)")
        }
    }

    func testValidBitcoinBech32Addresses() {
        let validAddresses = [
            "bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq",
            "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4",
            "bc1q34aq5drpuwy3wgl9lhup9892qp6svr8ldzyy7c"
        ]

        for address in validAddresses {
            XCTAssertTrue(isValidBitcoinAddress(address), "Should be valid: \(address)")
        }
    }

    func testInvalidBitcoinAddresses() {
        let invalidAddresses = [
            "0x742d35Cc6634C0532925a3b844Bc9e7595f4E5b2", // Ethereum address
            "1", // Too short
            "1BvBMSEYstWetqTFn5Au4m4GFg7xJaNVN2XXXXXX", // Too long
            "", // Empty
            "OBvBMSEYstWetqTFn5Au4m4GFg7xJaNVN2" // Invalid chars (O instead of 1)
        ]

        for address in invalidAddresses {
            XCTAssertFalse(isValidBitcoinAddress(address), "Should be invalid: \(address)")
        }
    }

    // MARK: - Solana Address Validation

    func testValidSolanaAddresses() {
        let validAddresses = [
            "4Nd1mBQtrMJVYVfKf2PJy9NZUZdTAsp7D4xWLs4gDB4T",
            "DRpbCBMxVnDK7maPM5tGv6MvB3v1HA2qZPz3kWRZK9Nc",
            "11111111111111111111111111111111" // System program
        ]

        for address in validAddresses {
            XCTAssertTrue(isValidSolanaAddress(address), "Should be valid: \(address)")
        }
    }

    func testInvalidSolanaAddresses() {
        let invalidAddresses = [
            "0x742d35Cc6634C0532925a3b844Bc9e7595f4E5b2", // Ethereum address
            "short", // Too short
            "0invalid", // Invalid char (0 is not in base58)
            "", // Empty
            "Iinvalid" // Invalid char (I is not in base58)
        ]

        for address in invalidAddresses {
            XCTAssertFalse(isValidSolanaAddress(address), "Should be invalid: \(address)")
        }
    }

    // MARK: - Multi-Chain Detection

    func testDetectAddressType() {
        // Ethereum
        XCTAssertEqual(detectAddressType("0x742d35Cc6634C0532925a3b844Bc9e7595f4E5b2"), .ethereum)

        // Bitcoin Legacy
        XCTAssertEqual(detectAddressType("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa"), .bitcoin)

        // Bitcoin P2SH
        XCTAssertEqual(detectAddressType("3J98t1WpEZ73CNmQviecrnyiWrnqRhWNLy"), .bitcoin)

        // Bitcoin Bech32
        XCTAssertEqual(detectAddressType("bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq"), .bitcoin)

        // Solana
        XCTAssertEqual(detectAddressType("4Nd1mBQtrMJVYVfKf2PJy9NZUZdTAsp7D4xWLs4gDB4T"), .solana)

        // Unknown
        XCTAssertEqual(detectAddressType("invalid"), .unknown)
    }

    // MARK: - Helper Functions (matching SendViewModel implementation)

    private func isValidEthereumAddress(_ address: String) -> Bool {
        let pattern = "^0x[a-fA-F0-9]{40}$"
        return address.range(of: pattern, options: .regularExpression) != nil
    }

    private func isValidBitcoinAddress(_ address: String) -> Bool {
        // Legacy addresses (P2PKH): start with 1
        let legacyPattern = "^[1][a-km-zA-HJ-NP-Z1-9]{25,34}$"
        // P2SH addresses: start with 3
        let p2shPattern = "^3[a-km-zA-HJ-NP-Z1-9]{25,34}$"
        // Bech32 addresses: start with bc1
        let bech32Pattern = "^bc1[a-z0-9]{39,59}$"

        return address.range(of: legacyPattern, options: .regularExpression) != nil ||
               address.range(of: p2shPattern, options: .regularExpression) != nil ||
               address.range(of: bech32Pattern, options: .regularExpression) != nil
    }

    private func isValidSolanaAddress(_ address: String) -> Bool {
        // Base58 encoded, typically 32-44 characters
        let pattern = "^[1-9A-HJ-NP-Za-km-z]{32,44}$"
        return address.range(of: pattern, options: .regularExpression) != nil
    }

    private enum AddressType {
        case ethereum
        case bitcoin
        case solana
        case unknown
    }

    private func detectAddressType(_ address: String) -> AddressType {
        if isValidEthereumAddress(address) {
            return .ethereum
        }
        if isValidBitcoinAddress(address) {
            return .bitcoin
        }
        if isValidSolanaAddress(address) {
            return .solana
        }
        return .unknown
    }
}

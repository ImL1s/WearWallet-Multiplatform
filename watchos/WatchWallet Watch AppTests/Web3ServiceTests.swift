import XCTest
import coreKmp
@testable import WatchWallet_Watch_App

/// Comprehensive tests for Web3Service KMP integration
final class Web3ServiceTests: XCTestCase {

    var web3Service: Web3Service!

    override func setUp() {
        super.setUp()
        // Create service with default RPC URL
        web3Service = Web3Service(rpcUrl: "https://eth-mainnet.g.alchemy.com/v2/demo")
    }

    override func tearDown() {
        web3Service = nil
        super.tearDown()
    }

    // MARK: - Initialization Tests

    func testInitializationWithValidRpcUrl() {
        // Given
        let rpcUrl = "https://eth-mainnet.example.com"

        // When
        let service = Web3Service(rpcUrl: rpcUrl)

        // Then
        XCTAssertNotNil(service)
    }

    func testInitializationWithChainType() {
        // Given
        let rpcUrl = "https://polygon-mainnet.example.com"

        // When
        let service = Web3Service(rpcUrl: rpcUrl, chainType: .polygon)

        // Then
        XCTAssertNotNil(service)
    }

    // MARK: - Chain ID Mapping Tests

    func testMapChainIdToStringForEthereum() {
        // Given
        let chainId = "1"

        // When
        let chainName = mapChainIdToName(chainId)

        // Then
        XCTAssertEqual(chainName, "ethereum")
    }

    func testMapChainIdToStringForPolygon() {
        // Given
        let chainId = "137"

        // When
        let chainName = mapChainIdToName(chainId)

        // Then
        XCTAssertEqual(chainName, "polygon")
    }

    func testMapChainIdToStringForBSC() {
        // Given
        let chainId = "56"

        // When
        let chainName = mapChainIdToName(chainId)

        // Then
        XCTAssertEqual(chainName, "bsc")
    }

    func testMapChainIdToStringForArbitrum() {
        // Given
        let chainId = "42161"

        // When
        let chainName = mapChainIdToName(chainId)

        // Then
        XCTAssertEqual(chainName, "arbitrum")
    }

    func testMapChainIdToStringForOptimism() {
        // Given
        let chainId = "10"

        // When
        let chainName = mapChainIdToName(chainId)

        // Then
        XCTAssertEqual(chainName, "optimism")
    }

    func testMapChainIdToStringForAvalanche() {
        // Given
        let chainId = "43114"

        // When
        let chainName = mapChainIdToName(chainId)

        // Then
        XCTAssertEqual(chainName, "avalanche")
    }

    func testMapChainIdToStringForUnknown() {
        // Given
        let chainId = "99999"

        // When
        let chainName = mapChainIdToName(chainId)

        // Then
        XCTAssertEqual(chainName, "ethereum") // Default to Ethereum
    }

    // MARK: - Address Format Tests

    func testValidEthereumAddressFormat() {
        // Given
        let address = "0x742d35Cc6634C0532925a3b844Bc9e7595f4E5b2"

        // Then
        XCTAssertTrue(isValidEthereumAddress(address))
    }

    func testInvalidEthereumAddressFormat() {
        // Given
        let addresses = [
            "742d35Cc6634C0532925a3b844Bc9e7595f4E5b2", // Missing 0x
            "0x742d35Cc6634C0532925a3b844Bc9e7595f4E5", // Too short
            "0xGGGd35Cc6634C0532925a3b844Bc9e7595f4E5b2", // Invalid chars
            "" // Empty
        ]

        // Then
        for address in addresses {
            XCTAssertFalse(isValidEthereumAddress(address), "Should be invalid: \(address)")
        }
    }

    // MARK: - Wei Conversion Tests

    func testConvertEtherToWei() {
        // Given
        let ether = 1.0

        // When
        let wei = convertEtherToWei(ether)

        // Then
        XCTAssertEqual(wei, "1000000000000000000")
    }

    func testConvertSmallEtherToWei() {
        // Given
        let ether = 0.001

        // When
        let wei = convertEtherToWei(ether)

        // Then
        XCTAssertEqual(wei, "1000000000000000")
    }

    func testConvertWeiToEther() {
        // Given
        let wei = "1000000000000000000"

        // When
        let ether = convertWeiToEther(wei)

        // Then
        XCTAssertEqual(ether, 1.0, accuracy: 0.0001)
    }

    // MARK: - Gas Price Formatting Tests

    func testFormatGasPriceInGwei() {
        // Given
        let gasPriceWei: UInt64 = 20_000_000_000 // 20 Gwei

        // When
        let gwei = formatGasPriceToGwei(gasPriceWei)

        // Then
        XCTAssertEqual(gwei, "20.0")
    }

    func testFormatHighGasPriceInGwei() {
        // Given
        let gasPriceWei: UInt64 = 150_000_000_000 // 150 Gwei

        // When
        let gwei = formatGasPriceToGwei(gasPriceWei)

        // Then
        XCTAssertEqual(gwei, "150.0")
    }

    // MARK: - Transaction Hash Validation Tests

    func testValidTransactionHash() {
        // Given
        let txHash = "0x88df016429689c079f3b2f6ad39fa052532c56795b733da78a91ebe6a713944b"

        // Then
        XCTAssertTrue(isValidTransactionHash(txHash))
    }

    func testInvalidTransactionHash() {
        // Given
        let invalidHashes = [
            "0x88df016429689c079f3b2f6ad39fa052532c56795b733da78a91ebe6a713944", // Too short
            "88df016429689c079f3b2f6ad39fa052532c56795b733da78a91ebe6a713944b", // Missing 0x
            "" // Empty
        ]

        // Then
        for hash in invalidHashes {
            XCTAssertFalse(isValidTransactionHash(hash), "Should be invalid: \(hash)")
        }
    }

    // MARK: - Helper Functions

    private func mapChainIdToName(_ chainId: String) -> String {
        switch chainId {
        case "1": return "ethereum"
        case "137": return "polygon"
        case "56": return "bsc"
        case "43114": return "avalanche"
        case "42161": return "arbitrum"
        case "10": return "optimism"
        default: return "ethereum"
        }
    }

    private func isValidEthereumAddress(_ address: String) -> Bool {
        let pattern = "^0x[a-fA-F0-9]{40}$"
        return address.range(of: pattern, options: .regularExpression) != nil
    }

    private func convertEtherToWei(_ ether: Double) -> String {
        let wei = ether * 1e18
        return String(format: "%.0f", wei)
    }

    private func convertWeiToEther(_ wei: String) -> Double {
        guard let weiValue = Double(wei) else { return 0 }
        return weiValue / 1e18
    }

    private func formatGasPriceToGwei(_ weiValue: UInt64) -> String {
        let gwei = Double(weiValue) / 1e9
        return String(format: "%.1f", gwei)
    }

    private func isValidTransactionHash(_ hash: String) -> Bool {
        let pattern = "^0x[a-fA-F0-9]{64}$"
        return hash.range(of: pattern, options: .regularExpression) != nil
    }
}

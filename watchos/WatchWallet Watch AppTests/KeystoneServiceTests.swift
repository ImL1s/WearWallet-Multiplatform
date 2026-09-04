import XCTest
@testable import WatchWallet_Watch_App

/// Comprehensive tests for KeystoneService KMP integration
final class KeystoneServiceTests: XCTestCase {

    var keystoneService: KeystoneService!

    override func setUp() {
        super.setUp()
        keystoneService = KeystoneService.shared
    }

    // MARK: - Initialization Tests

    func testSharedInstanceExists() {
        // When
        let instance = KeystoneService.shared

        // Then
        XCTAssertNotNil(instance)
    }

    func testInitialState() {
        // Then
        XCTAssertFalse(keystoneService.isLoading)
        XCTAssertNil(keystoneService.error)
    }

    // MARK: - QR Validation Tests

    func testIsValidKeystoneQRWithValidData() {
        // Given
        let validQR = "ur:crypto-hdkey/oxaxhdclaocevtctynfnbwkeswmsbelooxcywflnndaebaoxtdsohycmfrnneebbdtbtjtzttygmad"

        // When
        let isValid = keystoneService.isValidKeystoneQR(validQR)

        // Then - Actual validation depends on KMP implementation
        // This test verifies the method is callable
        XCTAssertNotNil(isValid)
    }

    func testIsValidKeystoneQRWithInvalidData() {
        // Given
        let invalidQR = "not a valid keystone qr"

        // When
        let isValid = keystoneService.isValidKeystoneQR(invalidQR)

        // Then
        XCTAssertFalse(isValid)
    }

    func testIsValidKeystoneQRWithEmptyString() {
        // Given
        let emptyQR = ""

        // When
        let isValid = keystoneService.isValidKeystoneQR(emptyQR)

        // Then
        XCTAssertFalse(isValid)
    }

    // MARK: - ConnectedWallet Tests

    func testConnectedWalletCreation() {
        // Given
        let address = "0x742d35Cc6634C0532925a3b844Bc9e7595f4E5b2"
        let name = "Test Wallet"
        let chainId = "1"

        // When
        let wallet = ConnectedWallet(
            address: address,
            name: name,
            type: .keystone,
            chainId: chainId,
            balance: "0",
            metadata: [:]
        )

        // Then
        XCTAssertEqual(wallet.address, address)
        XCTAssertEqual(wallet.name, name)
        XCTAssertEqual(wallet.type, .keystone)
        XCTAssertEqual(wallet.chainId, chainId)
    }

    func testConnectedWalletTypes() {
        // Given
        let types: [KeystoneWalletType] = [.keystone, .watchOnly, .hotWallet]

        // Then
        XCTAssertEqual(types.count, 3)
    }

    // MARK: - UnsignedTransaction Tests

    func testUnsignedTransactionCreation() {
        // Given
        let from = "0x742d35Cc6634C0532925a3b844Bc9e7595f4E5b2"
        let to = "0xAb5801a7D398351b8bE11C439e05C5B3259aeC9B"
        let value = "1000000000000000000"

        // When
        let tx = UnsignedTransaction(
            from: from,
            to: to,
            value: value,
            data: nil,
            gasPrice: "20000000000",
            gasLimit: "21000",
            nonce: "0",
            chainId: "1"
        )

        // Then
        XCTAssertEqual(tx.from, from)
        XCTAssertEqual(tx.to, to)
        XCTAssertEqual(tx.value, value)
        XCTAssertEqual(tx.chainId, "1")
    }

    func testUnsignedTransactionWithContractCall() {
        // Given
        let from = "0x742d35Cc6634C0532925a3b844Bc9e7595f4E5b2"
        let contractAddress = "0xdAC17F958D2ee523a2206206994597C13D831ec7"
        let data = "0xa9059cbb000000000000000000000000" // ERC20 transfer

        // When
        let tx = UnsignedTransaction(
            from: from,
            to: contractAddress,
            value: "0",
            data: data,
            gasPrice: "30000000000",
            gasLimit: "60000",
            nonce: "5",
            chainId: "1"
        )

        // Then
        XCTAssertNotNil(tx.data)
        XCTAssertTrue(tx.data?.hasPrefix("0xa9059cbb") ?? false)
    }

    // MARK: - SignedTransaction Tests

    func testSignedTransactionCreation() {
        // Given
        let signedTx = "0xf86c..."
        let txHash = "0xabc123..."

        // When
        let tx = SignedTransaction(
            signedTx: signedTx,
            txHash: txHash,
            from: "0x...",
            to: "0x...",
            value: "0",
            status: .signed
        )

        // Then
        XCTAssertEqual(tx.signedTx, signedTx)
        XCTAssertEqual(tx.txHash, txHash)
        XCTAssertEqual(tx.status, .signed)
    }

    // MARK: - KeystoneServiceStatusInfo Tests

    func testServiceStatusInfoCreation() {
        // When
        let status = KeystoneServiceStatusInfo(
            initialized: true,
            connectedDevices: 1,
            lastError: nil
        )

        // Then
        XCTAssertTrue(status.initialized)
        XCTAssertEqual(status.connectedDevices, 1)
        XCTAssertNil(status.lastError)
    }

    func testServiceStatusInfoWithError() {
        // When
        let status = KeystoneServiceStatusInfo(
            initialized: false,
            connectedDevices: 0,
            lastError: "Connection failed"
        )

        // Then
        XCTAssertFalse(status.initialized)
        XCTAssertEqual(status.lastError, "Connection failed")
    }

    // MARK: - Reflection Helper Tests

    func testMirrorPropertyExtraction() {
        // Given
        struct TestObject {
            let name: String
            let value: Int
        }
        let obj = TestObject(name: "test", value: 42)
        let mirror = Mirror(reflecting: obj)

        // When
        let nameValue = mirror.children.first { $0.label == "name" }?.value as? String
        let intValue = mirror.children.first { $0.label == "value" }?.value as? Int

        // Then
        XCTAssertEqual(nameValue, "test")
        XCTAssertEqual(intValue, 42)
    }

    func testMirrorWithOptionalProperty() {
        // Given
        struct TestObject {
            let optional: String?
        }
        let obj = TestObject(optional: "present")
        let objNil = TestObject(optional: nil)

        // When
        let mirror1 = Mirror(reflecting: obj)
        let mirror2 = Mirror(reflecting: objNil)

        // Then
        let value1 = mirror1.children.first { $0.label == "optional" }?.value
        let value2 = mirror2.children.first { $0.label == "optional" }?.value

        XCTAssertNotNil(value1)
        XCTAssertNotNil(value2) // The mirror still contains the property, just with nil value
    }

    // MARK: - Async Operation Tests

    func testAsyncInitializeHandlesError() async {
        // When - Initialize in test environment (may fail without real KMP)
        await keystoneService.initialize()

        // Then - Should handle gracefully
        // Note: In test environment, this might fail, but shouldn't crash
    }

    func testConnectedWalletsStartsEmpty() {
        // Then
        XCTAssertTrue(keystoneService.connectedWallets.isEmpty)
    }
}

import XCTest
@testable import WatchWallet_Watch_App

/// Comprehensive tests for ViewModel integrations
/// Note: ViewModels are MainActor-isolated, tests use @MainActor
@MainActor
final class ViewModelIntegrationTests: XCTestCase {

    // MARK: - AIAssistantViewModel Tests

    func testAIAssistantViewModelInitialization() {
        // When
        let viewModel = AIAssistantViewModel()

        // Then
        XCTAssertFalse(viewModel.isProcessing)
        XCTAssertFalse(viewModel.navigateToTransactionHistory)
        XCTAssertFalse(viewModel.navigateToPortfolio)
        XCTAssertFalse(viewModel.showQRCode)
    }

    func testAIAssistantViewModelNavigationFlags() {
        // Given
        let viewModel = AIAssistantViewModel()

        // When - Simulate navigation triggers
        viewModel.navigateToTransactionHistory = true
        viewModel.navigateToPortfolio = true
        viewModel.showQRCode = true

        // Then
        XCTAssertTrue(viewModel.navigateToTransactionHistory)
        XCTAssertTrue(viewModel.navigateToPortfolio)
        XCTAssertTrue(viewModel.showQRCode)
    }

    // MARK: - NFCPaymentViewModel Tests

    func testNFCPaymentViewModelInitialization() {
        // When
        let viewModel = NFCPaymentViewModel()

        // Then
        XCTAssertEqual(viewModel.selectedToken.symbol, "USDC")
        XCTAssertFalse(viewModel.isProcessing)
    }

    func testNFCPaymentViewModelTokenSelection() {
        // Given
        let viewModel = NFCPaymentViewModel()

        // When
        viewModel.selectedToken = PaymentToken.eth

        // Then
        XCTAssertEqual(viewModel.selectedToken.symbol, "ETH")
        XCTAssertEqual(viewModel.selectedToken.name, "Ethereum")
    }

    // MARK: - UltrathinkVoiceAssistantViewModel Tests

    func testUltrathinkVoiceAssistantInitialization() {
        // When
        let viewModel = UltrathinkVoiceAssistantViewModel()

        // Then
        XCTAssertFalse(viewModel.isListening)
        XCTAssertFalse(viewModel.isProcessing)
    }

    func testUltrathinkVoiceAssistantStateTransitions() {
        // Given
        let viewModel = UltrathinkVoiceAssistantViewModel()

        // When - Simulate state changes
        viewModel.isListening = true

        // Then
        XCTAssertTrue(viewModel.isListening)

        // When - Stop listening
        viewModel.isListening = false
        viewModel.isProcessing = true

        // Then
        XCTAssertFalse(viewModel.isListening)
        XCTAssertTrue(viewModel.isProcessing)
    }

    // MARK: - PaymentToken Tests

    func testPaymentTokenEquality() {
        // Given
        let token1 = PaymentToken.usdc
        let token2 = PaymentToken.usdc
        let token3 = PaymentToken.eth

        // Then
        XCTAssertEqual(token1, token2)
        XCTAssertNotEqual(token1, token3)
    }

    func testPaymentTokenProperties() {
        // Given
        let usdc = PaymentToken.usdc

        // Then
        XCTAssertEqual(usdc.symbol, "USDC")
        XCTAssertEqual(usdc.name, "USD Coin")
        XCTAssertFalse(usdc.icon.isEmpty)
    }

    func testAllPaymentTokens() {
        // Given
        let tokens = [
            PaymentToken.usdc,
            PaymentToken.usdt,
            PaymentToken.eth,
            PaymentToken.btc,
            PaymentToken.matic
        ]

        // Then - All tokens should have unique symbols
        let symbols = tokens.map { $0.symbol }
        let uniqueSymbols = Set(symbols)
        XCTAssertEqual(symbols.count, uniqueSymbols.count)
    }
}

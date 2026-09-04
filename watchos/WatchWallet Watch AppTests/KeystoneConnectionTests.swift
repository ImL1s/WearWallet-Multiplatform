//
//  KeystoneConnectionTests.swift
//  WatchWallet Watch App Tests
//
//  Keystone 3 Pro 連接功能測試
//

import XCTest
import Combine
@testable import WatchWallet_Watch_App

@MainActor
final class KeystoneConnectionTests: XCTestCase {
    
    var viewModel: KeystoneConnectionViewModel!
    var cancellables: Set<AnyCancellable>!
    
    override func setUp() {
        super.setUp()
        // 使用 Mock Client 進行測試，隔離硬體依賴
        viewModel = KeystoneConnectionViewModel(client: .mock)
        cancellables = Set<AnyCancellable>()
    }
    
    override func tearDown() {
        cancellables = nil
        viewModel = nil
        super.tearDown()
    }
    
    // MARK: - 初始狀態測試
    
    func testInitialState() {
        // 驗證初始狀態
        XCTAssertEqual(viewModel.connectionState, .idle)
        XCTAssertFalse(viewModel.isLoading)
        XCTAssertNil(viewModel.errorMessage)
    }
    
    // MARK: - 連接流程測試
    
    func testStartKeystoneConnection() async throws {
        // 測試開始連接
        viewModel.startKeystoneConnection()
        
        // 等待異步初始化完成
        try await Task.sleep(nanoseconds: 500_000_000)
        
        // 驗證狀態變化
        XCTAssertEqual(viewModel.connectionState, .waitingForScan)
        XCTAssertFalse(viewModel.isLoading)
        XCTAssertNil(viewModel.errorMessage)
    }
    
    func testStartScanning() {
        // 測試開始掃描
        viewModel.startScanning()
        
        // 驗證狀態變化
        XCTAssertEqual(viewModel.connectionState, .scanning)
        XCTAssertTrue(viewModel.isLoading)
    }
    
    // MARK: - QR 碼處理測試
    
    func testCompleteKeystoneConnectionWithURData() async {
        let expectation = expectation(description: "UR data connection")
        
        // 模擬 UR 協議數據
        let urData = "ur:crypto-hdkey/1-2/lpadbbcsiecsfnwkahtsalrsgsbehkytbdpkhhfethvydnssamvedyuydrmh"
        
        // 監聽狀態變化
        viewModel.$connectionState
            .dropFirst() // 跳過初始值
            .sink { state in
                if state == .success {
                    expectation.fulfill()
                }
            }
            .store(in: &cancellables)
        
        // 執行連接完成
        viewModel.completeKeystoneConnection(scannedData: urData)
        
        // 等待異步操作完成
        await fulfillment(of: [expectation], timeout: 3.0)
        
        // 驗證最終狀態
        XCTAssertEqual(viewModel.connectionState, .success)
        XCTAssertFalse(viewModel.isLoading)
        XCTAssertNil(viewModel.errorMessage)
    }
    
    func testCompleteKeystoneConnectionWithValidMnemonic() async {
        let expectation = expectation(description: "Valid mnemonic connection")
        
        // 模擬有效的12詞助記詞
        let mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        
        // 監聽狀態變化
        viewModel.$connectionState
            .dropFirst()
            .sink { state in
                if state == .success {
                    expectation.fulfill()
                }
            }
            .store(in: &cancellables)
        
        // 執行連接完成
        viewModel.completeKeystoneConnection(scannedData: mnemonic)
        
        // 等待異步操作完成
        await fulfillment(of: [expectation], timeout: 3.0)
        
        // 驗證最終狀態
        XCTAssertEqual(viewModel.connectionState, .success)
        XCTAssertFalse(viewModel.isLoading)
        XCTAssertNil(viewModel.errorMessage)
    }
    
    func testCompleteKeystoneConnectionWithInvalidMnemonic() async {
        let expectation = expectation(description: "Invalid mnemonic connection")
        
        // 模擬無效的助記詞（只有5個詞）
        let invalidMnemonic = "invalid short phrase test data"
        
        // 監聽狀態變化
        viewModel.$connectionState
            .dropFirst()
            .sink { state in
                if state == .error {
                    expectation.fulfill()
                }
            }
            .store(in: &cancellables)
        
        // 執行連接完成
        viewModel.completeKeystoneConnection(scannedData: invalidMnemonic)
        
        // 等待異步操作完成
        await fulfillment(of: [expectation], timeout: 3.0)
        
        // 驗證最終狀態
        XCTAssertEqual(viewModel.connectionState, .error)
        XCTAssertFalse(viewModel.isLoading)
        XCTAssertNotNil(viewModel.errorMessage)
        XCTAssertEqual(viewModel.errorMessage, "無效的助記詞格式（需要 12 或 24 個單詞）")
    }
    
    // MARK: - 重試機制測試
    
    func testRetryConnection() async throws {
        // 設置錯誤狀態
        viewModel.completeKeystoneConnection(scannedData: "invalid")
        
        // 等待錯誤狀態設置
        try await Task.sleep(nanoseconds: 500_000_000)
        
        // 執行重試
        viewModel.retryConnection()
        
        // 等待異步重新初始化
        try await Task.sleep(nanoseconds: 500_000_000)
        
        // 驗證重置狀態
        XCTAssertEqual(viewModel.connectionState, .waitingForScan)
        XCTAssertFalse(viewModel.isLoading)
        XCTAssertNil(viewModel.errorMessage)
    }
    
    // MARK: - 取消連接測試
    
    func testCancelConnection() {
        // 設置掃描狀態
        viewModel.startScanning()
        
        // 執行取消
        viewModel.cancelConnection()
        
        // 驗證重置狀態
        XCTAssertEqual(viewModel.connectionState, .idle)
        XCTAssertFalse(viewModel.isLoading)
        XCTAssertNil(viewModel.errorMessage)
    }
    
    // MARK: - 錯誤清除測試
    
    func testClearError() {
        // 設置錯誤訊息
        viewModel.completeKeystoneConnection(scannedData: "invalid")
        
        // 等待錯誤狀態設置
        let expectation = expectation(description: "Error message set")
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
            expectation.fulfill()
        }
        wait(for: [expectation], timeout: 1.0)
        
        // 清除錯誤
        viewModel.clearError()
        
        // 驗證錯誤已清除
        XCTAssertNil(viewModel.errorMessage)
    }
}

// MARK: - WatchConnectivity 測試

final class WatchConnectivityManagerTests: XCTestCase {
    
    var manager: WatchConnectivityManager!
    var cancellables: Set<AnyCancellable>!
    
    override func setUp() {
        super.setUp()
        manager = WatchConnectivityManager.shared
        cancellables = Set<AnyCancellable>()
    }
    
    override func tearDown() {
        cancellables = nil
        super.tearDown()
    }
    
    func testManagerInitialization() {
        // 驗證管理器初始化
        XCTAssertNotNil(manager)
        XCTAssertFalse(manager.isReachable)
        XCTAssertNil(manager.connectionError)
    }
    
    func testKeystoneConnectResultsStream() {
        let expectation = expectation(description: "Keystone connect result received")
        
        // 訂閱 Keystone 連接結果流
        manager.keystoneConnectResults
            .sink { urData in
                XCTAssertFalse(urData.isEmpty)
                expectation.fulfill()
            }
            .store(in: &cancellables)
        
        // 模擬接收到 Keystone 連接結果
        manager.keystoneConnectResults.send("ur:crypto-hdkey/test-data")
        
        wait(for: [expectation], timeout: 1.0)
    }
    
    func testKeystoneSignResultsStream() {
        let expectation = expectation(description: "Keystone sign result received")
        
        // 訂閱 Keystone 簽名結果流
        manager.keystoneSignResults
            .sink { urData in
                XCTAssertFalse(urData.isEmpty)
                expectation.fulfill()
            }
            .store(in: &cancellables)
        
        // 模擬接收到 Keystone 簽名結果
        manager.keystoneSignResults.send("ur:eth-signature/test-signature")
        
        wait(for: [expectation], timeout: 1.0)
    }
    
    func testConnectionStatusStream() {
        let expectation = expectation(description: "Connection status received")
        
        // 訂閱連接狀態流
        manager.isConnected
            .sink { isConnected in
                // 初始狀態應該是 false
                XCTAssertFalse(isConnected)
                expectation.fulfill()
            }
            .store(in: &cancellables)
        
        wait(for: [expectation], timeout: 1.0)
    }
}

// MARK: - 整合測試

@MainActor
final class KeystoneIntegrationTests: XCTestCase {
    
    var viewModel: KeystoneConnectionViewModel!
    var watchConnectivity: WatchConnectivityManager!
    var cancellables: Set<AnyCancellable>!
    
    override func setUp() {
        super.setUp()
        // 使用 Mock Client 進行整合測試，解決模擬器上的超時問題
        viewModel = KeystoneConnectionViewModel(client: .mock)
        watchConnectivity = WatchConnectivityManager.shared
        cancellables = Set<AnyCancellable>()
    }
    
    override func tearDown() {
        cancellables = nil
        watchConnectivity = nil
        viewModel = nil
        super.tearDown()
    }
    
    func testFullKeystoneConnectionFlow() async {
        let expectation = expectation(description: "Full connection flow")
        
        // 1. 開始連接
        viewModel.startKeystoneConnection()
        
        // 等待異步初始化
        try? await Task.sleep(nanoseconds: 500_000_000)
        XCTAssertEqual(viewModel.connectionState, .waitingForScan)
        
        // 2. 開始掃描
        viewModel.startScanning()
        XCTAssertEqual(viewModel.connectionState, .scanning)
        XCTAssertTrue(viewModel.isLoading)
        
        // 3. 監聽連接完成
        // 3. 監聽連接完成
        viewModel.$connectionState
            .filter { $0 == .success }
            .first()
            .sink { _ in
                expectation.fulfill()
            }
            .store(in: &cancellables)
        
        // 4. 模擬掃描完成
        let urData = "ur:crypto-hdkey/1-1/lpadbbcsiecsfnwkahtsalrsgsbehkytbdpkhhfethvydnssamvedyuydrmh"
        viewModel.completeKeystoneConnection(scannedData: urData)
        
        // 5. 等待完成
        await fulfillment(of: [expectation], timeout: 10.0)
        
        // 6. 驗證最終狀態
        XCTAssertEqual(viewModel.connectionState, .success)
        XCTAssertFalse(viewModel.isLoading)
        XCTAssertNil(viewModel.errorMessage)
    }
    
    func testErrorRecoveryFlow() async {
        let expectation = expectation(description: "Error recovery flow")
        
        // 1. 觸發錯誤
        viewModel.completeKeystoneConnection(scannedData: "invalid data")
        
        // 等待錯誤狀態
        try? await Task.sleep(nanoseconds: 1_500_000_000)
        XCTAssertEqual(viewModel.connectionState, .error)
        XCTAssertNotNil(viewModel.errorMessage)
        
        // 2. 監聽重試後的成功
        // 2. 監聽重試後的成功
        viewModel.$connectionState
            .filter { $0 == .success }
            .first()
            .sink { _ in
                expectation.fulfill()
            }
            .store(in: &cancellables)
        
        // 3. 重試並提供正確數據
        viewModel.retryConnection()
        
        // 等待異步重新初始化
        try? await Task.sleep(nanoseconds: 500_000_000)
        XCTAssertEqual(viewModel.connectionState, .waitingForScan)
        
        // 4. 提供正確的助記詞
        let validMnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        viewModel.completeKeystoneConnection(scannedData: validMnemonic)
        
        // 5. 等待恢復成功
        await fulfillment(of: [expectation], timeout: 10.0)
        
        // 6. 驗證恢復狀態
        XCTAssertEqual(viewModel.connectionState, .success)
        XCTAssertNil(viewModel.errorMessage)
    }
}
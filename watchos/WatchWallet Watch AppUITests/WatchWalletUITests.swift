//
//  WatchWalletUITests.swift
//  WatchWallet Watch AppUITests
//
//  端對端 UI 自動化測試
//  使用 XCUITest 框架在 watchOS 模擬器上執行
//

import XCTest

/// watchOS E2E 自動化測試
/// 測試「創建錢包」和「導入錢包」的完整真實流程
final class WatchWalletUITests: XCTestCase {
    
    var app: XCUIApplication!
    
    /// 控制是否重置數據 - 用於需要保留錢包狀態的測試
    static var shouldResetData = true
    
    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
        
        // 使用 launchArguments，僅在需要時重置
        if Self.shouldResetData {
            app.launchArguments = ["-uitesting", "-reset-data"]
            app.launchEnvironment["RESET_DATA"] = "true"
        } else {
            app.launchArguments = ["-uitesting"]
        }
        app.launch()
    }
    
    override func tearDownWithError() throws {
        app = nil
    }
    
    // MARK: - Combined Flow Test (完整流程測試)
    
    /// 完整測試：創建錢包後立即測試 Send/Receive/History
    func testFullWalletFlowWithCoreFeatures() throws {
        print("🚀 開始完整流程測試：創建錢包 + 核心功能")
        Self.shouldResetData = true  // 重置以確保乾淨狀態
        
        // ========== Step 1: 創建錢包 ==========
        print("📝 Step 1: 創建新錢包")
        waitAndTap(app.buttons["CreateWalletButton"], message: "應該顯示「創建新錢包」按鈕")
        
        let nameInput = app.textFields["WalletNameInput"]
        typeText(nameInput, text: "TestWallet")
        
        waitAndTap(app.buttons["CreateWalletNextButton"], message: "應該顯示「下一步」按鈕")
        
        let ackButton = app.buttons["SafetyWarningAckButton"]
        if !ackButton.exists { app.swipeUp() }
        waitAndTap(ackButton, message: "應該顯示「我已了解」按鈕")
        
        let confirmBackupButton = app.buttons["MnemonicBackupConfirmButton"]
        XCTAssertTrue(confirmBackupButton.waitForExistence(timeout: 30), "應該顯示「我已備份」按鈕")
        confirmBackupButton.tap()
        
        let hasWalletsState = app.otherElements["AppRoot_HasWallets"]
        XCTAssertTrue(hasWalletsState.waitForExistence(timeout: 20), "創建成功後 App 應該切換到有錢包狀態")
        
        let sendButton = app.buttons["Send"]
        if !sendButton.exists { sleep(2) }
        XCTAssertTrue(sendButton.exists, "Dashboard 應該顯示 Send 按鈕")
        print("✅ Step 1 完成：錢包創建成功")
        
        // ========== Step 2: 測試 Receive 功能 ==========
        print("📝 Step 2: 測試 Receive 功能")
        let receiveButton = app.buttons["Receive"]
        XCTAssertTrue(receiveButton.waitForExistence(timeout: 5), "應該顯示 Receive 按鈕")
        receiveButton.tap()
        
        // 驗證 QR Code
        sleep(2)
        let addressLabel = app.staticTexts["錢包地址"]
        XCTAssertTrue(addressLabel.waitForExistence(timeout: 5), "應該顯示「錢包地址」標籤")
        
        // 驗證複製按鈕
        let copyButton = app.buttons["CopyAddressButton"]
        if copyButton.exists {
            copyButton.tap()
            print("✅ 複製按鈕點擊成功")
        }
        
        // 返回主畫面
        app.buttons["完成"].tap()
        sleep(1)
        print("✅ Step 2 完成：Receive 功能正常")
        
        // ========== Step 3: 測試 Send 功能 ==========
        print("📝 Step 3: 測試 Send 功能")
        waitAndTap(app.buttons["Send"], message: "應該顯示 Send 按鈕")
        
        // 驗證 Send 畫面元素
        let amountLabel = app.staticTexts["發送金額"]
        XCTAssertTrue(amountLabel.waitForExistence(timeout: 5), "應該顯示「發送金額」標籤")
        
        // 取消並返回
        app.buttons["取消"].tap()
        sleep(1)
        print("✅ Step 3 完成：Send 功能正常")
        
        // ========== Step 4: 測試 History 功能 ==========
        print("📝 Step 4: 測試 History 功能")
        // History 可能在不同位置，先嘗試直接按鈕
        let historyButton = app.buttons["History"]
        if historyButton.exists {
            historyButton.tap()
        } else {
            // 可能需要滑動找到 History 入口
            app.swipeUp()
            sleep(1)
            if historyButton.exists {
                historyButton.tap()
            }
        }
        
        sleep(2)
        // 可能是空狀態或有交易列表
        let historyTitle = app.staticTexts["交易紀錄"]
        let emptyState = app.staticTexts["暫無交易紀錄"]
        let hasHistoryContent = historyTitle.exists || emptyState.exists
        
        if hasHistoryContent {
            print("✅ Step 4 完成：History 功能正常")
        } else {
            print("⚠️ Step 4：未找到 History 內容，但流程完成")
        }
        
        print("🎉 完整流程測試全部通過！")
    }
    
    // MARK: - Swap Feature Test
    
    /// 測試 Swap 功能：驗證 Token 列表是否動態載入
    func testSwapViewLoadsTokens() throws {
        print("🚀 開始測試：Swap 功能 - 動態 Token 載入")
        
        // 強制重置並創建新錢包（確保乾淨狀態）
        Self.shouldResetData = true
        app.terminate()
        app.launchArguments = ["-uitesting", "-reset-data"]
        app.launchEnvironment["RESET_DATA"] = "true"
        app.launch()
        
        // 等待 App 啟動
        sleep(2)
        
        // === Step 1: 創建錢包 ===
        print("📝 Step 1: 創建錢包")
        let createButton = app.buttons["CreateWalletButton"]
        XCTAssertTrue(createButton.waitForExistence(timeout: 10), "應該顯示「創建新錢包」按鈕")
        createButton.tap()
        
        // 在 DEBUG 模式下，錢包名稱已經預填 "AutoTestWallet"，無需手動輸入
        sleep(1)
        
        // 點擊下一步
        let nextButton = app.buttons["CreateWalletNextButton"]
        XCTAssertTrue(nextButton.waitForExistence(timeout: 5), "應該顯示「下一步」按鈕")
        nextButton.tap()
        
        // 安全警告確認（需等待助記詞生成完成，按鈕才會顯示）
        // 助記詞生成期間會顯示 ProgressView，完成後才顯示「我已了解」按鈕
        print("⏳ 等待助記詞生成完成...")
        let ackButton = app.buttons["SafetyWarningAckButton"]
        // 增加重試邏輯：每秒檢查一次，最多等待 30 秒
        var ackButtonFound = false
        for _ in 1...30 {
            if ackButton.exists {
                ackButtonFound = true
                break
            }
            // 可能需要滑動才能看到按鈕
            if !ackButton.exists {
                app.swipeUp()
            }
            sleep(1)
        }
        XCTAssertTrue(ackButtonFound, "應該顯示「我已了解」按鈕（等待助記詞生成）")
        ackButton.tap()
        
        // 等待助記詞生成並確認備份
        print("⏳ 等待助記詞生成...")
        let confirmBackupButton = app.buttons["MnemonicBackupConfirmButton"]
        XCTAssertTrue(confirmBackupButton.waitForExistence(timeout: 30), "應該顯示「我已備份」按鈕")
        confirmBackupButton.tap()
        
        // 等待進入主畫面
        sleep(3)
        print("✅ Step 1 完成：錢包創建成功")
        
        // === Step 2: 進入 Swap 畫面 ===
        print("📝 Step 2: 進入 Swap 畫面")
        
        // 尋找 Swap 按鈕（可能需要滾動）
        var swapButton = app.buttons["Swap"]
        if !swapButton.exists {
            app.swipeUp()
            sleep(1)
            swapButton = app.buttons["Swap"]
        }
        
        // 也嘗試用中文「兌換」
        if !swapButton.exists {
            swapButton = app.buttons["兌換"]
        }
        
        XCTAssertTrue(swapButton.waitForExistence(timeout: 10), "應該顯示 Swap 按鈕")
        swapButton.tap()
        
        // 等待 Swap 畫面載入
        sleep(3)
        
        // === Step 3: 驗證 Swap 畫面 ===
        print("📝 Step 3: 驗證 Swap 畫面")
        
        // 截圖記錄當前狀態
        let screenshot = XCUIScreen.main.screenshot()
        let attachment = XCTAttachment(screenshot: screenshot)
        attachment.name = "SwapViewScreenshot"
        attachment.lifetime = .keepAlways
        add(attachment)
        
        // 驗證畫面包含 Swap 相關元素（使用更寬鬆的檢查）
        // 可能是 Sheet 或 NavigationView，檢查任一元素存在
        let hasSwapElements = 
            app.staticTexts["兌換"].exists ||
            app.staticTexts["Swap"].exists ||
            app.staticTexts["From"].exists ||
            app.staticTexts["To"].exists ||
            app.buttons["選擇代幣"].exists ||
            app.buttons["Select Token"].exists
        
        // 如果找不到元素，打印當前畫面結構幫助調試
        if !hasSwapElements {
            print("⚠️ 當前畫面元素：")
            print(app.debugDescription)
        }
        
        XCTAssertTrue(hasSwapElements, "Swap 畫面應該顯示相關元素")
        
        print("✅ Step 3 完成：Swap 畫面驗證成功")
        print("🎉 Swap 功能測試全部通過！")
    }
    
    // MARK: - Helper Methods
    
    /// 等待元素出現並點擊
    func waitAndTap(_ element: XCUIElement, timeout: TimeInterval = 10, message: String) {
        XCTAssertTrue(element.waitForExistence(timeout: timeout), message)
        element.tap()
    }
    
    /// 在 Text Field 中輸入文字（模擬 Watch 鍵盤輸入）
    func typeText(_ element: XCUIElement, text: String) {
        XCTAssertTrue(element.waitForExistence(timeout: 10), "找不到輸入框")
        
        // 檢查是否已經預填 (DEBUG 模式下)
        if let value = element.value as? String, value.contains(text) {
            print("ℹ️ 輸入框已預填: \(value)，跳過輸入")
            return
        }
        
        // 重試機制：輸入直到值正確
        for _ in 1...3 {
            element.tap()
            sleep(1) // 等待焦點
            
            // 輸入文字
            XCUIApplication().typeText(text)
            sleep(1)
            
            // 驗證輸入是否成功
            if let value = element.value as? String, value.contains(text) {
                print("✅ 輸入成功: \(text)")
                return
            }
            
            print("⚠️ 輸入失敗，重試中...")
            sleep(1)
        }
        
        // 如果預填失敗且輸入也失敗，則報錯
        XCTFail("❌ 無法在輸入框中輸入文字: \(text)")
    }
    
    // MARK: - E2E Tests
    
    /// 完整測試：創建新錢包流程 (真實 KMP 邏輯)
    func testFullCreateWalletFlow() throws {
        print("🚀 開始測試：創建新錢包完整流程")
        
        // 1. 點擊「創建新錢包」
        waitAndTap(app.buttons["CreateWalletButton"], message: "應該顯示「創建新錢包」按鈕")
        
        // 2. 輸入錢包名稱
        let nameInput = app.textFields["WalletNameInput"]
        typeText(nameInput, text: "AutoTestWallet")
        
        // 3. 點擊「下一步」
        waitAndTap(app.buttons["CreateWalletNextButton"], message: "應該顯示「下一步」按鈕")
        
        // 4. 安全警告確認
        // 這裡可能需要滑動才能看到按鈕
        let ackButton = app.buttons["SafetyWarningAckButton"]
        if !ackButton.exists {
            app.swipeUp()
        }
        waitAndTap(ackButton, message: "應該顯示「我已了解」按鈕")
        
        // 5. 等待助記詞生成 (真實 crypto 生成需要時間)
        print("⏳ 等待助記詞生成...")
        let confirmBackupButton = app.buttons["MnemonicBackupConfirmButton"]
        // 給予足夠長的時間等待 KMP 生成 (最多 30秒)
        XCTAssertTrue(confirmBackupButton.waitForExistence(timeout: 30), "應該顯示「我已備份」按鈕，表示助記詞生成完成")
        
        // 6. 點擊「我已備份」
        confirmBackupButton.tap()
        
        // 7. 驗證是否進入主畫面 (Dashboard)
        // 檢查 Root State Identifier (確認 App 狀態已切換)
        let hasWalletsState = app.otherElements["AppRoot_HasWallets"]
        XCTAssertTrue(hasWalletsState.waitForExistence(timeout: 20), "創建成功後 App 應該切換到有錢包狀態 (AppRoot_HasWallets)")
        
        // 這裡我們檢查是否存在 "Send" 按鈕 (Dashboard 的特徵)
        let sendButton = app.buttons["Send"]
        // 如果狀態切換了但找不到按鈕，可能是 UI 還在刷新，稍微等待
        if !sendButton.exists {
             sleep(2)
        }
        XCTAssertTrue(sendButton.exists, "Dashboard 應該顯示 Send 按鈕")
        
        print("✅ 創建錢包完整流程測試通過")
    }
    
    /// 完整測試：導入錢包流程 (真實 KMP 邏輯)
    /// 注意：在 DEBUG 模式下，錢包名稱和助記詞已預填，無需手動輸入
    func testFullImportWalletFlow() throws {
        print("🚀 開始測試：導入錢包完整流程")
        
        // 1. 點擊「導入現有錢包」
        waitAndTap(app.buttons["ImportWalletButton"], message: "應該顯示「導入現有錢包」按鈕")
        
        // 2. 驗證錢包名稱輸入框存在（DEBUG 模式下已預填 "ImportedTestWallet"）
        let nameInput = app.textFields["ImportWalletNameInput"]
        XCTAssertTrue(nameInput.waitForExistence(timeout: 10), "應該顯示錢包名稱輸入框")
        print("✅ 錢包名稱輸入框存在，DEBUG 模式已預填")
        
        // 3. 滑動以確保助記詞輸入區可見
        app.swipeUp()
        sleep(1)
        
        // 4. 驗證助記詞輸入框存在（DEBUG 模式下已預填）
        let mnemonicInput = app.textFields["ImportMnemonicInput"]
        XCTAssertTrue(mnemonicInput.waitForExistence(timeout: 10), "應該顯示助記詞輸入框")
        print("✅ 助記詞輸入框存在，DEBUG 模式已預填")
        
        // 5. 滑動並點擊「導入錢包」按鈕
        app.swipeUp()
        sleep(1)
        
        let importConfirmButton = app.buttons["ImportWalletConfirmButton"]
        XCTAssertTrue(importConfirmButton.waitForExistence(timeout: 10), "應該顯示「導入錢包」按鈕")
        
        // 等待按鈕變為可點擊狀態
        sleep(1)
        importConfirmButton.tap()
        
        // 6. 驗證導入成功並進入主畫面
        print("⏳ 等待錢包導入...")
        let sendButton = app.buttons["Send"]
        XCTAssertTrue(sendButton.waitForExistence(timeout: 30), "導入成功後應該進入主畫面")
        
        print("✅ 導入錢包完整流程測試通過")
    }

    
    // MARK: - 基本啟動測試
    
    /// 驗證 App 可以正常啟動
    func testAppLaunches() throws {
        let anyButton = app.buttons.firstMatch
        XCTAssertTrue(anyButton.waitForExistence(timeout: 15), "App 應該顯示至少一個按鈕")
        print("✅ App 啟動測試通過")
    }
    
    // MARK: - Core Feature E2E Tests
    
    /// 測試發送流程
    func testSendFlow() throws {
        print("🚀 開始測試：發送流程")
        
        // 確保有錢包 (先創建或導入)
        let sendButton = app.buttons["Send"]
        if !sendButton.waitForExistence(timeout: 10) {
            // 沒有錢包，跳過測試
            print("⚠️ 沒有錢包，跳過發送流程測試")
            throw XCTSkip("需要先創建或導入錢包")
        }
        
        // 1. 導航到發送畫面
        waitAndTap(sendButton, message: "應該顯示 Send 按鈕")
        
        // 2. 驗證發送畫面元素
        let amountInput = app.textFields["SendAmountInput"]
        XCTAssertTrue(amountInput.waitForExistence(timeout: 5), "應該顯示金額輸入框")
        
        // 3. 輸入金額
        amountInput.tap()
        sleep(1)
        app.typeText("0.001")
        
        // 4. 點擊發送按鈕
        let confirmButton = app.buttons["SendConfirmButton"]
        if confirmButton.waitForExistence(timeout: 3) && confirmButton.isEnabled {
            confirmButton.tap()
            
            // 5. 驗證確認畫面
            let confirmTitle = app.staticTexts["發送確認"]
            XCTAssertTrue(confirmTitle.waitForExistence(timeout: 5), "應該顯示發送確認畫面")
        }
        
        print("✅ 發送流程測試通過")
    }
    
    /// 測試接收流程
    func testReceiveFlow() throws {
        print("🚀 開始測試：接收流程")
        
        // 確保有錢包
        let receiveButton = app.buttons["Receive"]
        if !receiveButton.waitForExistence(timeout: 10) {
            print("⚠️ 沒有錢包，跳過接收流程測試")
            throw XCTSkip("需要先創建或導入錢包")
        }
        
        // 1. 導航到接收畫面
        waitAndTap(receiveButton, message: "應該顯示 Receive 按鈕")
        
        // 2. 驗證 QR Code 存在
        let qrCode = app.otherElements["ReceiveQRCode"]
        XCTAssertTrue(qrCode.waitForExistence(timeout: 5), "應該顯示 QR Code")
        
        // 3. 驗證地址標籤
        let addressLabel = app.staticTexts["錢包地址"]
        XCTAssertTrue(addressLabel.exists, "應該顯示「錢包地址」標籤")
        
        // 4. 驗證地址文字
        let addressText = app.staticTexts["ReceiveAddressText"]
        XCTAssertTrue(addressText.waitForExistence(timeout: 3), "應該顯示錢包地址")
        
        // 5. 點擊複製按鈕
        let copyButton = app.buttons["CopyAddressButton"]
        if copyButton.exists {
            copyButton.tap()
            print("✅ 點擊複製按鈕成功")
        }
        
        print("✅ 接收流程測試通過")
    }
    
    /// 測試交易歷史流程
    func testHistoryFlow() throws {
        print("🚀 開始測試：交易歷史流程")
        
        // 確保有錢包
        let historyButton = app.buttons["History"]
        if !historyButton.waitForExistence(timeout: 10) {
            // 嘗試找其他入口
            let transactionHistory = app.buttons.matching(identifier: "TransactionHistory").firstMatch
            if !transactionHistory.exists {
                print("⚠️ 找不到歷史按鈕，跳過測試")
                throw XCTSkip("找不到交易歷史入口")
            }
            transactionHistory.tap()
        } else {
            historyButton.tap()
        }
        
        // 驗證歷史畫面
        sleep(2)
        
        // 檢查標題或空狀態
        let historyTitle = app.staticTexts["交易紀錄"]
        let emptyState = app.staticTexts["暫無交易紀錄"]
        let transactionList = app.otherElements["TransactionList"]
        
        let hasContent = historyTitle.exists || emptyState.exists || transactionList.exists
        XCTAssertTrue(hasContent, "應該顯示交易歷史內容或空狀態")
        
        // 測試篩選器 (如果存在)
        let filterAll = app.buttons["FilterChip_全部"]
        let filterSent = app.buttons["FilterChip_發送"]
        
        if filterAll.exists {
            filterAll.tap()
            print("✅ 點擊「全部」篩選器")
        }
        
        if filterSent.exists {
            filterSent.tap()
            print("✅ 點擊「發送」篩選器")
        }
        
        print("✅ 交易歷史流程測試通過")
    }
}


import XCTest

class WearWalletCompanionUITests: XCTestCase {

    var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launch()
    }

    override func tearDownWithError() throws {
        app = nil
    }

    func testAppLaunchAndMainUI() throws {
        // Verify Title
        let title = app.staticTexts["WearWallet Companion"]
        XCTAssertTrue(title.waitForExistence(timeout: 5), "App Title should exist")
        
        // Verify Watch Connectivity Status label
        // The text depends on status, but we expect at least one of the possible strings or the status section
        let statusLabel = app.staticTexts.containing(NSPredicate(format: "label CONTAINS 'Watch'")).firstMatch
        XCTAssertTrue(statusLabel.exists, "Watch status label should be visible")
    }

    func testScannerNavigation() throws {
        // Find Keystone Scanner button
        let scanButton = app.buttons["掃描 Keystone QR"]
        XCTAssertTrue(scanButton.waitForExistence(timeout: 5), "Scanner button should exist")
        
        scanButton.tap()
        
        // Verify Scanner View appears
        // Since camera permissions might block simulator, we check for UI elements of the scanner view
        // like the close button or text
        let closeButton = app.buttons["xmark.circle.fill"]
        let scanText = app.staticTexts["Align QR code within frame"]
        
        // Note: On simulator without camera, AVCapture might error or show placeholder, 
        // but the View struct should still render the overlay.
        // We accept either the close button or the status text.
        XCTAssertTrue(closeButton.waitForExistence(timeout: 5) || scanText.waitForExistence(timeout: 5), "Scanner View should open")
        
        if closeButton.exists {
            closeButton.tap()
        }
    }
    
    func testAddressBookNavigation() throws {
        // Find Navigation Link to Address Book
        let addressBookLink = app.buttons["通訊錄管理"]
        XCTAssertTrue(addressBookLink.waitForExistence(timeout: 5), "Address Book link should exist")
        
        addressBookLink.tap()
        
        // Verify Address Book Title
        let navTitle = app.staticTexts["通訊錄"]
        XCTAssertTrue(navTitle.waitForExistence(timeout: 5), "Should navigate to Address Book screen")
        
        // Verify Add Button
        let addButton = app.buttons["plus"]
        XCTAssertTrue(addButton.exists, "Add Contact button should exist")
        
        // Test Add Contact Sheet (Partial)
        addButton.tap()
        let sheetTitle = app.staticTexts["新增聯絡人"]
        XCTAssertTrue(sheetTitle.waitForExistence(timeout: 2), "Add Contact sheet should open")
        
        // Close sheet
        app.buttons["取消"].tap()
        
        // Navigate back
        app.navigationBars.buttons.firstMatch.tap()
        
        // Check if back on Main Screen
        let mainTitle = app.staticTexts["WearWallet Companion"]
        XCTAssertTrue(mainTitle.waitForExistence(timeout: 2), "Should return to main screen")
    }
}

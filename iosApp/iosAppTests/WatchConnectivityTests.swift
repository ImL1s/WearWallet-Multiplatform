
import XCTest
@testable import WearWalletCompanion

class WatchConnectivityTests: XCTestCase {
    
    var manager: WatchConnectivityManager!
    
    override func setUp() {
        super.setUp()
        manager = WatchConnectivityManager.shared
    }
    
    override func tearDown() {
        manager = nil
        super.tearDown()
    }
    
    func testHandleKeystoneConnectRequest() {
        let message: [String: Any] = [
            "type": "keystone_connect_request",
            "timestamp": Date().timeIntervalSince1970
        ]
        
        // Simulating receiving message
        // Since we can't easily trigger the private handleMessage, we test the logic via public delegate if possible
        // or just verify the internal switching logic
        
        // In this case, we'll use a trick or expose for testing if needed, 
        // but let's see if we can trigger it via the delegate method
        
        manager.session(WCSession.default, didReceiveMessage: message)
        
        // Wait for main queue async
        let expectation = XCTestExpectation(description: "Show scanner")
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
            if self.manager.showKeystoneScanner {
                expectation.fulfill()
            }
        }
        
        wait(for: [expectation], timeout: 1.0)
        
        XCTAssertTrue(manager.showKeystoneScanner)
        XCTAssertEqual(manager.scannerExpectedType, .CRYPTO_ACCOUNT)
    }
    
    func testHandleKeystoneSignRequest() {
        let message: [String: Any] = [
            "type": "keystone_sign_request",
            "timestamp": Date().timeIntervalSince1970,
            "data": ["tx": "0x1234"]
        ]
        
        manager.session(WCSession.default, didReceiveMessage: message)
        
        let expectation = XCTestExpectation(description: "Show scanner for sign")
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
            if self.manager.showKeystoneScanner {
                expectation.fulfill()
            }
        }
        
        wait(for: [expectation], timeout: 1.0)
        
        XCTAssertTrue(manager.showKeystoneScanner)
        XCTAssertEqual(manager.scannerExpectedType, .CRYPTO_SIGNATURE)
        XCTAssertNotNil(manager.currentRequestId)
    }
}

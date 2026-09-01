//
//  TrustWalletBridge.h
//  coreKmp Objective-C 橋接頭文件
//
//  用途：允許 Kotlin/Native 調用 Swift TrustWalletSwiftBridge 類
//  創建日期：2025-10-10
//

#import <Foundation/Foundation.h>

// Forward declaration of the Swift class
// 注意：這個類在 Swift 中定義，編譯時會自動生成 Objective-C 接口
@class TrustWalletSwiftBridge;

// 如果需要顯式聲明方法，可以在這裡添加
// 但由於 Swift 類已經用 @objc 標記，通常不需要
// Swift 編譯器會自動生成對應的 Objective-C 頭文件

// Objective-C 橋接函數（可選，如果直接調用 Swift 類不行的話）
NS_ASSUME_NONNULL_BEGIN

// 便利函數：創建 TrustWalletSwiftBridge 實例並調用 Ed25519 簽名
// 這個函數用純 Objective-C 實現，方便 Kotlin 調用
NSString* _Nullable trustWalletSignEd25519(NSString *messageHex, NSString *privateKeyHex);

// 便利函數：Ed25519 簽名驗證
BOOL trustWalletVerifyEd25519(NSString *messageHex, NSString *signatureHex, NSString *publicKeyHex);

NS_ASSUME_NONNULL_END

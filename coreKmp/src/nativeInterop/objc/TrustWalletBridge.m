//
//  TrustWalletBridge.m
//  coreKmp Objective-C 橋接實現
//
//  用途：提供 Kotlin/Native 可調用的 Objective-C 函數
//  創建日期：2025-10-10
//

#import "TrustWalletBridge.h"

// Import Swift-generated Objective-C header
// 注意：這個頭文件由 Swift 編譯器自動生成
// 格式：<ModuleName>-Swift.h
#import "coreKmp-Swift.h"

NS_ASSUME_NONNULL_BEGIN

// 實現便利函數：Ed25519 簽名
// ✅ P0-3 記憶體管理：使用 @autoreleasepool 確保及時釋放
NSString* _Nullable trustWalletSignEd25519(NSString *messageHex, NSString *privateKeyHex) {
    @autoreleasepool {
        // 創建 Swift 橋接實例
        TrustWalletSwiftBridge *bridge = [[TrustWalletSwiftBridge alloc] init];

        // 調用 Swift 方法
        NSString *signature = [bridge signWithEd25519:messageHex privateKeyHex:privateKeyHex];

        // ✅ 複製字符串以防被 autoreleasepool 釋放
        return [signature copy];
    }
}

// 實現便利函數：Ed25519 驗證
// ✅ P0-3 記憶體管理：使用 @autoreleasepool 確保及時釋放
BOOL trustWalletVerifyEd25519(NSString *messageHex, NSString *signatureHex, NSString *publicKeyHex) {
    @autoreleasepool {
        TrustWalletSwiftBridge *bridge = [[TrustWalletSwiftBridge alloc] init];

        BOOL isValid = [bridge verifyEd25519SignatureWithMessageHex:messageHex
                                                        signatureHex:signatureHex
                                                        publicKeyHex:publicKeyHex];

        return isValid;
    }
}

NS_ASSUME_NONNULL_END

//
//  TrustWalletBridge.m
//  TrustWalletBridge Pod
//
//  ✅ Objective-C 實現文件
//  用途：實現公開的 C 函數，內部調用 Swift 橋接類
//  創建日期：2025-10-10
//

#import "TrustWalletBridge.h"

// ✅ 關鍵：這裡 import Swift 生成的頭文件
// 但這個 import 只在 Pod 編譯時發生，不會影響 cinterop
// cinterop 只看到上面的 TrustWalletBridge.h，不會看到這個文件
#if __has_include("TrustWalletBridge-Swift.h")
#import "TrustWalletBridge-Swift.h"
#else
// Fallback：如果沒有找到 Swift 頭文件，提供前向聲明
@class TrustWalletSwiftBridge;
@interface TrustWalletSwiftBridge : NSObject
- (NSString * _Nullable)signWithEd25519:(NSString *)messageHex privateKeyHex:(NSString *)privateKeyHex;
- (BOOL)verifyEd25519SignatureWithMessageHex:(NSString *)messageHex signatureHex:(NSString *)signatureHex publicKeyHex:(NSString *)publicKeyHex;
- (NSString *)generateMnemonic:(NSInteger)wordCount;
- (BOOL)validateMnemonic:(NSString *)mnemonic;
- (NSString * _Nullable)hashKeccak256:(NSString *)data;
- (NSString * _Nullable)hashSHA256:(NSString *)data;
- (NSString * _Nullable)base58Encode:(NSString *)dataHex;
- (NSString * _Nullable)base58Decode:(NSString *)base58String;
@end
#endif

/// ✅ 實現：Ed25519 簽名
NSString * _Nullable trustWalletSignEd25519(NSString *messageHex, NSString *privateKeyHex) {
    @autoreleasepool {
        TrustWalletSwiftBridge *bridge = [[TrustWalletSwiftBridge alloc] init];
        NSString *signature = [bridge signWithEd25519:messageHex privateKeyHex:privateKeyHex];
        return [signature copy];
    }
}

/// ✅ 實現：Ed25519 簽名驗證
BOOL trustWalletVerifyEd25519(NSString *messageHex, NSString *signatureHex, NSString *publicKeyHex) {
    @autoreleasepool {
        TrustWalletSwiftBridge *bridge = [[TrustWalletSwiftBridge alloc] init];
        return [bridge verifyEd25519SignatureWithMessageHex:messageHex
                                                signatureHex:signatureHex
                                                publicKeyHex:publicKeyHex];
    }
}

/// ✅ 實現：生成助記詞
NSString * _Nullable TWBGenerateMnemonic(int wordCount) {
    @autoreleasepool {
        TrustWalletSwiftBridge *bridge = [[TrustWalletSwiftBridge alloc] init];
        return [[bridge generateMnemonic:wordCount] copy];
    }
}

/// ✅ 實現：驗證助記詞
BOOL TWBValidateMnemonic(NSString *mnemonic) {
    @autoreleasepool {
        TrustWalletSwiftBridge *bridge = [[TrustWalletSwiftBridge alloc] init];
        return [bridge validateMnemonic:mnemonic];
    }
}

/// ✅ 實現：Keccak-256 哈希
NSString * _Nullable TWBHashKeccak256(NSString *dataHex) {
    @autoreleasepool {
        TrustWalletSwiftBridge *bridge = [[TrustWalletSwiftBridge alloc] init];
        return [[bridge hashKeccak256:dataHex] copy];
    }
}

/// ✅ 實現：SHA-256 哈希
NSString * _Nullable TWBHashSHA256(NSString *dataHex) {
    @autoreleasepool {
        TrustWalletSwiftBridge *bridge = [[TrustWalletSwiftBridge alloc] init];
        return [[bridge hashSHA256:dataHex] copy];
    }
}

/// ✅ 實現：Base58 編碼
NSString * _Nullable TWBBase58Encode(NSString *dataHex) {
    @autoreleasepool {
        TrustWalletSwiftBridge *bridge = [[TrustWalletSwiftBridge alloc] init];
        return [[bridge base58Encode:dataHex] copy];
    }
}

/// ✅ 實現：Base58 解碼
NSString * _Nullable TWBBase58Decode(NSString *base58String) {
    @autoreleasepool {
        TrustWalletSwiftBridge *bridge = [[TrustWalletSwiftBridge alloc] init];
        return [[bridge base58Decode:base58String] copy];
    }
}

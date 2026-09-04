#import "AesGcmBridge.h"
#import <CommonCrypto/CommonCrypto.h>
#import <CommonCrypto/CommonKeyDerivation.h>

// iOS 13+ 需要 CryptoKit
#if __has_include(<CryptoKit/CryptoKit.h>)
#import <CryptoKit/CryptoKit.h>
#define HAS_CRYPTOKIT 1
#else
#define HAS_CRYPTOKIT 0
#endif

@implementation AesGcmBridge

+ (NSData *)encryptPlaintext:(NSData *)plaintext
                         key:(NSData *)key
                       nonce:(NSData *)nonce {
    if (key.length != 32) {
        NSLog(@"❌ AES-GCM: Key must be 32 bytes, got %lu", (unsigned long)key.length);
        return nil;
    }
    if (nonce.length != 12) {
        NSLog(@"❌ AES-GCM: Nonce must be 12 bytes, got %lu", (unsigned long)nonce.length);
        return nil;
    }

#if HAS_CRYPTOKIT && __IPHONE_OS_VERSION_MIN_REQUIRED >= __IPHONE_13_0
    // iOS 13+ 使用 CryptoKit (需要 Swift 橋接)
    // 暫時不支援，返回錯誤
    NSLog(@"❌ AES-GCM: CryptoKit not available in Objective-C bridge");
    return nil;
#else
    // 使用 CommonCrypto 的 CCCrypt
    // 注意：CommonCrypto 不直接支持 GCM 模式
    // 需要使用 CCCryptorGCM (iOS 13+) 或回退到 CTR 模式
    NSLog(@"❌ AES-GCM: CommonCrypto GCM mode requires iOS 13+");
    return nil;
#endif
}

+ (NSData *)decryptCombined:(NSData *)combined
                        key:(NSData *)key {
    if (key.length != 32) {
        NSLog(@"❌ AES-GCM: Key must be 32 bytes");
        return nil;
    }

    // 同樣問題：需要 iOS 13+ 或 Swift CryptoKit
    NSLog(@"❌ AES-GCM: Decryption not implemented");
    return nil;
}

+ (NSData *)deriveKeyFromPassword:(NSString *)password
                             salt:(NSData *)salt
                       iterations:(NSInteger)iterations
                        keyLength:(NSInteger)keyLength {
    NSData *passwordData = [password dataUsingEncoding:NSUTF8StringEncoding];
    if (!passwordData) {
        NSLog(@"❌ PBKDF2: Failed to encode password");
        return nil;
    }

    NSMutableData *derivedKey = [NSMutableData dataWithLength:keyLength];

    CCCryptorStatus status = CCKeyDerivationPBKDF(
        kCCPBKDF2,
        passwordData.bytes,
        passwordData.length,
        salt.bytes,
        salt.length,
        kCCPRFHmacAlgSHA256,
        (uint)iterations,
        derivedKey.mutableBytes,
        derivedKey.length
    );

    if (status != kCCSuccess) {
        NSLog(@"❌ PBKDF2 failed with status: %d", status);
        return nil;
    }

    return derivedKey;
}

@end

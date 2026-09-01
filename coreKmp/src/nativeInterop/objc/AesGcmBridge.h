#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

@interface AesGcmBridge : NSObject

/// AES-256-GCM 加密
/// @param plaintext 明文數據
/// @param key 32字節密鑰
/// @param nonce 12字節nonce
/// @return combined格式數據 (nonce + ciphertext + tag)，失敗返回nil
+ (NSData * _Nullable)encryptPlaintext:(NSData *)plaintext
                                   key:(NSData *)key
                                 nonce:(NSData *)nonce NS_SWIFT_NAME(encrypt(plaintext:key:nonce:));

/// AES-256-GCM 解密
/// @param combined combined格式數據 (nonce + ciphertext + tag)
/// @param key 32字節密鑰
/// @return 解密後的明文，失敗返回nil
+ (NSData * _Nullable)decryptCombined:(NSData *)combined
                                  key:(NSData *)key NS_SWIFT_NAME(decrypt(combined:key:));

/// PBKDF2-HMAC-SHA256 密鑰派生
/// @param password 密碼字符串
/// @param salt 鹽值
/// @param iterations 迭代次數
/// @param keyLength 密鑰長度
/// @return 派生的密鑰，失敗返回nil
+ (NSData * _Nullable)deriveKeyFromPassword:(NSString *)password
                                       salt:(NSData *)salt
                                 iterations:(NSInteger)iterations
                                  keyLength:(NSInteger)keyLength NS_SWIFT_NAME(deriveKey(password:salt:iterations:keyLength:));

@end

NS_ASSUME_NONNULL_END

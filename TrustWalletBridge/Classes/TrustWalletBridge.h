//
//  TrustWalletBridge.h
//  TrustWalletBridge Pod
//
//  ✅ 公開的 Objective-C 頭文件
//  用途：供 Kotlin/Native cinterop 使用，不依賴 Swift 自動生成
//  創建日期：2025-10-10
//

#import <Foundation/Foundation.h>

//! Project version number for TrustWalletBridge.
FOUNDATION_EXPORT double TrustWalletBridgeVersionNumber;

//! Project version string for TrustWalletBridge.
FOUNDATION_EXPORT const unsigned char TrustWalletBridgeVersionString[];

NS_ASSUME_NONNULL_BEGIN

/// ✅ C 函數接口：Ed25519 簽名
/// 這些函數在 .m 文件中實現，內部調用 Swift 橋接類
/// Kotlin cinterop 可以直接綁定這些 C 函數，無需等待 Swift 頭文件生成
/// @param messageHex 消息的十六進制字符串
/// @param privateKeyHex 私鑰的十六進制字符串
/// @return 簽名的十六進制字符串（64 bytes），失敗返回 NULL
NSString * _Nullable trustWalletSignEd25519(NSString *messageHex, NSString *privateKeyHex);

/// ✅ C 函數接口：Ed25519 簽名驗證
/// @param messageHex 消息的十六進制字符串
/// @param signatureHex 簽名的十六進制字符串
/// @param publicKeyHex 公鑰的十六進制字符串
/// @return YES 如果簽名有效，否則 NO
BOOL trustWalletVerifyEd25519(NSString *messageHex, NSString *signatureHex, NSString *publicKeyHex);

/// ✅ C 函數接口：生成助記詞
/// @param wordCount 助記詞數量 (12, 15, 18, 21, 24)
/// @return 助記詞字符串，失敗返回 NULL
NSString * _Nullable TWBGenerateMnemonic(int wordCount);

/// ✅ C 函數接口：驗證助記詞
/// @param mnemonic 助記詞字符串
/// @return YES 如果助記詞有效，否則 NO
BOOL TWBValidateMnemonic(NSString *mnemonic);

/// ✅ C 函數接口：Keccak-256 哈希
/// @param dataHex 數據的十六進制字符串
/// @return 哈希值的十六進制字符串，失敗返回 NULL
NSString * _Nullable TWBHashKeccak256(NSString *dataHex);

/// ✅ C 函數接口：SHA-256 哈希
/// @param dataHex 數據的十六進制字符串
/// @return 哈希值的十六進制字符串，失敗返回 NULL
NSString * _Nullable TWBHashSHA256(NSString *dataHex);

/// ✅ C 函數接口：Base58 編碼
/// @param dataHex 數據的十六進制字符串
/// @return Base58 編碼的字符串，失敗返回 NULL
NSString * _Nullable TWBBase58Encode(NSString *dataHex);

/// ✅ C 函數接口：Base58 解碼
/// @param base58String Base58 編碼的字符串
/// @return 解碼後的十六進制字符串，失敗返回 NULL
NSString * _Nullable TWBBase58Decode(NSString *base58String);

NS_ASSUME_NONNULL_END

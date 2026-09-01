//
//  TrustWalletSwiftBridge.h
//  coreKmp
//
//  Objective-C header for Swift bridge to TrustWallet Core
//  This header is used by Kotlin/Native cinterop to generate Kotlin bindings
//

#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

/**
 * 密鑰對數據結構
 */
@interface KeyPair : NSObject

@property (nonatomic, strong, readonly) NSString *publicKey;
@property (nonatomic, strong, readonly) NSString *privateKey;

- (instancetype)initWithPublicKey:(NSString *)publicKey
                       privateKey:(NSString *)privateKey;

@end

/**
 * TrustWallet Swift 橋接類
 * 提供與 TrustWallet Core 的 Objective-C 互操作接口
 */
@interface TrustWalletSwiftBridge : NSObject

/**
 * 初始化方法
 */
- (instancetype)init;

/**
 * 從助記詞生成密鑰對
 * @param mnemonic BIP39 助記詞
 * @return KeyPair 包含公鑰和私鑰的對象
 */
- (KeyPair *)generateKeyPairFromMnemonic:(NSString *)mnemonic;

/**
 * 從私鑰生成密鑰對
 * @param privateKeyHex 十六進制格式的私鑰
 * @return KeyPair 包含公鑰和私鑰的對象
 */
- (KeyPair *)generateKeyPairFromPrivateKey:(NSString *)privateKeyHex;

/**
 * 從公鑰導出地址
 * @param publicKeyHex 十六進制格式的公鑰
 * @return NSString 區塊鏈地址
 */
- (NSString *)deriveAddress:(NSString *)publicKeyHex;

/**
 * 從擴展公鑰導出地址
 * @param xpub 擴展公鑰 (xpub)
 * @param derivationPath 派生路徑
 * @return NSString 區塊鏈地址
 */
- (NSString *)deriveAddressFromXpub:(NSString *)xpub
                     derivationPath:(NSString *)derivationPath;

/**
 * 生成助記詞
 * @param wordCount 助記詞單詞數量 (12, 15, 18, 21, 24)
 * @return NSString BIP39 助記詞
 */
- (NSString *)generateMnemonic:(NSInteger)wordCount;

/**
 * 驗證助記詞
 * @param mnemonic BIP39 助記詞
 * @return BOOL 助記詞是否有效
 */
- (BOOL)validateMnemonic:(NSString *)mnemonic;

/**
 * 簽名交易
 * @param transaction 交易數據
 * @param privateKeyHex 十六進制格式的私鑰
 * @return NSString 十六進制格式的簽名
 */
- (NSString *)signTransaction:(NSData *)transaction
                privateKeyHex:(NSString *)privateKeyHex;

@end

NS_ASSUME_NONNULL_END
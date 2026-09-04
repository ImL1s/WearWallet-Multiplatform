package com.cbstudio.wearwallet.core.trustwallet

/**
 * TrustWalletSwiftBridge 使用範例
 *
 * 此文件展示如何在 Kotlin/Native (iosMain) 中使用透過 cinterop 生成的 Swift 橋接綁定
 *
 * 前提條件：
 * 1. 已在 build.gradle.kts 中配置 cinterop
 * 2. 已執行 Gradle sync
 * 3. TrustWalletSwiftBridge.swift 已正確實現並標註 @objc
 *
 * 使用方式：
 * - 在配置完成後，取消下面的註釋即可使用
 * - import 路徑會根據 .def 文件中的 package 配置而定
 */

// 取消註釋以啟用（配置完成後）:
/*
import com.cbstudio.wearwallet.core.trustwallet.bridge.*

/**
 * iOS 平台的錢包管理器實現
 * 使用 TrustWallet Core 透過 Swift 橋接
 */
class IOSWalletManager {

    private val bridge = TrustWalletSwiftBridge()

    /**
     * 生成新錢包
     * @param wordCount 助記詞單詞數量 (12, 15, 18, 21, 24)
     * @return Triple<助記詞, 公鑰, 地址>
     */
    fun generateWallet(wordCount: Int = 12): Triple<String, String, String> {
        // 生成助記詞
        val mnemonic = bridge.generateMnemonic(wordCount.toLong()) ?: ""

        // 從助記詞生成密鑰對
        val keyPair = bridge.generateKeyPairFromMnemonic(mnemonic)

        // 從公鑰導出地址
        val publicKey = keyPair?.publicKey() ?: ""
        val address = bridge.deriveAddress(publicKey) ?: ""

        return Triple(mnemonic, publicKey, address)
    }

    /**
     * 驗證助記詞是否有效
     * @param mnemonic BIP39 助記詞
     * @return 助記詞是否有效
     */
    fun validateMnemonic(mnemonic: String): Boolean {
        return bridge.validateMnemonic(mnemonic)
    }

    /**
     * 從助記詞恢復錢包
     * @param mnemonic BIP39 助記詞
     * @return Pair<公鑰, 地址>
     */
    fun recoverWallet(mnemonic: String): Pair<String, String> {
        // 驗證助記詞
        if (!validateMnemonic(mnemonic)) {
            throw IllegalArgumentException("Invalid mnemonic")
        }

        // 從助記詞生成密鑰對
        val keyPair = bridge.generateKeyPairFromMnemonic(mnemonic)

        // 導出地址
        val publicKey = keyPair?.publicKey() ?: ""
        val address = bridge.deriveAddress(publicKey) ?: ""

        return publicKey to address
    }

    /**
     * 從私鑰導入錢包
     * @param privateKeyHex 十六進制格式的私鑰
     * @return Pair<公鑰, 地址>
     */
    fun importFromPrivateKey(privateKeyHex: String): Pair<String, String> {
        // 從私鑰生成密鑰對
        val keyPair = bridge.generateKeyPairFromPrivateKey(privateKeyHex)

        // 導出地址
        val publicKey = keyPair?.publicKey() ?: ""
        val address = bridge.deriveAddress(publicKey) ?: ""

        return publicKey to address
    }

    /**
     * 從擴展公鑰派生地址（用於 Watch-Only 錢包）
     * @param xpub 擴展公鑰
     * @param derivationPath 派生路徑 (例如: "m/44'/60'/0'/0/0")
     * @return 派生的地址
     */
    fun deriveAddressFromXpub(xpub: String, derivationPath: String): String {
        return bridge.deriveAddressFromXpub(xpub, derivationPath = derivationPath) ?: ""
    }

    /**
     * 簽名交易
     * @param transactionData 交易數據
     * @param privateKeyHex 私鑰（十六進制）
     * @return 簽名（十六進制）
     */
    fun signTransaction(transactionData: ByteArray, privateKeyHex: String): String {
        // 注意：在 Kotlin/Native 中，ByteArray 會自動橋接為 NSData
        return bridge.signTransaction(transactionData, privateKeyHex = privateKeyHex) ?: ""
    }
}

/**
 * 使用範例
 */
fun trustWalletBridgeUsageExample() {
    val walletManager = IOSWalletManager()

    // 範例 1: 生成新錢包
    println("=== 生成新錢包 ===")
    val (mnemonic, publicKey, address) = walletManager.generateWallet(12)
    println("助記詞: $mnemonic")
    println("公鑰: $publicKey")
    println("地址: $address")

    // 範例 2: 驗證助記詞
    println("\n=== 驗證助記詞 ===")
    val isValid = walletManager.validateMnemonic(mnemonic)
    println("助記詞有效: $isValid")

    // 範例 3: 從助記詞恢復錢包
    println("\n=== 恢復錢包 ===")
    val (recoveredPubKey, recoveredAddress) = walletManager.recoverWallet(mnemonic)
    println("恢復的公鑰: $recoveredPubKey")
    println("恢復的地址: $recoveredAddress")

    // 範例 4: 從私鑰導入
    println("\n=== 從私鑰導入 ===")
    // 注意：這裡使用示例私鑰，實際使用時應該是真實私鑰
    val testPrivateKey = "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef"
    try {
        val (importedPubKey, importedAddress) = walletManager.importFromPrivateKey(testPrivateKey)
        println("導入的公鑰: $importedPubKey")
        println("導入的地址: $importedAddress")
    } catch (e: Exception) {
        println("導入失敗: ${e.message}")
    }

    // 範例 5: Watch-Only 錢包 (擴展公鑰)
    println("\n=== Watch-Only 錢包 ===")
    val xpub = "xpub6D4BDPcP2GT577Vvch3R8wDkScZWzQzMMUm3PWbmWvVJrZwQY4VUNgqFJPMM3No2dFDFGTsxxpG5uJh7n7epu4trkrX7x7DogT5Uv6fcLW5"
    val derivedAddress = walletManager.deriveAddressFromXpub(xpub, "m/44'/60'/0'/0/0")
    println("派生地址: $derivedAddress")
}

/**
 * expect/actual 模式範例
 * 如果你想在 commonMain 中定義接口，可以這樣做：
 */

// commonMain:
/*
expect class WalletProvider {
    fun generateMnemonic(wordCount: Int): String
    fun validateMnemonic(mnemonic: String): Boolean
    fun deriveAddress(publicKey: String): String
}
*/

// iosMain:
/*
actual class WalletProvider {
    private val bridge = TrustWalletSwiftBridge()

    actual fun generateMnemonic(wordCount: Int): String {
        return bridge.generateMnemonic(wordCount.toLong()) ?: ""
    }

    actual fun validateMnemonic(mnemonic: String): Boolean {
        return bridge.validateMnemonic(mnemonic)
    }

    actual fun deriveAddress(publicKey: String): String {
        return bridge.deriveAddress(publicKey) ?: ""
    }
}
*/
*/

/**
 * 注意事項：
 *
 * 1. Objective-C 屬性在 Kotlin 中通常顯示為方法
 *    Swift: let publicKey: String
 *    Kotlin: keyPair.publicKey()  // 注意括號
 *
 * 2. 可選類型映射
 *    Swift: String?
 *    Kotlin: String?
 *
 * 3. 數據類型橋接
 *    - String ↔ NSString
 *    - Int ↔ NSInteger
 *    - Bool ↔ BOOL
 *    - ByteArray ↔ NSData
 *
 * 4. 錯誤處理
 *    Swift 中拋出的異常在 Kotlin 中會變成 null 或崩潰
 *    建議在 Swift 端使用 @objc 方法返回可選值而不是拋出異常
 *
 * 5. 內存管理
 *    Objective-C 對象的內存管理由 ARC 處理
 *    Kotlin 不需要手動管理這些對象的生命週期
 */
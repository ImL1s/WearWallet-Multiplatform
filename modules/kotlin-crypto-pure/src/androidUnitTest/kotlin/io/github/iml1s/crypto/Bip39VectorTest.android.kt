package io.github.iml1s.crypto

import java.text.Normalizer
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Android 平台的 BIP39 Seed 派生實現
 * 
 * 根據 BIP39 規範：
 * - Password = 助記詞 (NFKD 正規化後的字串)
 * - Salt = "mnemonic" + passphrase (NFKD 正規化)
 * - 算法 = PBKDF2-HMAC-SHA512
 * - 迭代 = 2048
 * - 輸出 = 64 bytes (512 bits)
 */
internal actual fun platformDeriveSeed(mnemonic: String, passphrase: String): ByteArray {
    // Step 1: NFKD 正規化
    val normalizedMnemonic = Normalizer.normalize(mnemonic, Normalizer.Form.NFKD)
    val normalizedPassphrase = Normalizer.normalize(passphrase, Normalizer.Form.NFKD)
    
    // Step 2: 構建 salt (作為 UTF-8 bytes)
    val salt = ("mnemonic$normalizedPassphrase").toByteArray(Charsets.UTF_8)
    
    // Step 3: 使用 PBEKeySpec 正確處理 password
    // BIP39 規範：密碼是正規化後的助記詞「字符串」
    val passwordChars = normalizedMnemonic.toCharArray()
    
    // Step 4: 調用 PBKDF2
    val spec = PBEKeySpec(passwordChars, salt, 2048, 512)
    return try {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
        factory.generateSecret(spec).encoded
    } finally {
        spec.clearPassword()
    }
}

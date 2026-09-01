package io.github.iml1s.crypto

/**
 * iOS/watchOS 平台的 BIP39 Seed 派生實現 (僅用於測試)
 */
internal actual fun platformDeriveSeed(mnemonic: String, passphrase: String): ByteArray {
    // BIP39 標準：Salt 是 "mnemonic" + passphrase (NFKD 正規化)
    val normalizedPassphrase = normalizeNfkdPlatform(passphrase)
    val salt = ("mnemonic" + normalizedPassphrase).encodeToByteArray()
    val password = normalizeNfkdPlatform(mnemonic).encodeToByteArray()
    
    // BIP39 標準：HMAC-SHA512, 2048 次迭代, 512 位 (64 字節)
    return pbkdf2HmacSha512(password, salt, 2048, 64)
}

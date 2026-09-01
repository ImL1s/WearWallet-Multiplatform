package io.github.iml1s.crypto

import java.text.Normalizer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Android 平台的 PBKDF2-HMAC-SHA512 實現
 *
 * 使用純 Java 實現符合 RFC 2898 標準。
 *
 * ## 為什麼使用自定義實現？
 * JCA 的 `PBEKeySpec` 和 BouncyCastle 在某些情況下產生不一致的結果。
 * 這個實現直接按照 RFC 2898 規範實現，確保與 BIP39 官方測試向量完全相容。
 */
internal actual fun pbkdf2HmacSha512(
    password: ByteArray,
    salt: ByteArray,
    iterations: Int,
    keyLength: Int
): ByteArray {
    val mac = Mac.getInstance("HmacSHA512")
    mac.init(SecretKeySpec(password, "HmacSHA512"))
    
    val hLen = 64 // SHA512 輸出長度
    val dkLen = keyLength
    
    // RFC 2898: l = CEIL(dkLen / hLen)
    val l = (dkLen + hLen - 1) / hLen
    
    val dk = ByteArray(l * hLen)
    
    for (i in 1..l) {
        // F(Password, Salt, c, i) = U_1 ^ U_2 ^ ... ^ U_c
        // U_1 = PRF(Password, Salt || INT(i))
        val block = ByteArray(salt.size + 4)
        System.arraycopy(salt, 0, block, 0, salt.size)
        block[salt.size] = ((i shr 24) and 0xFF).toByte()
        block[salt.size + 1] = ((i shr 16) and 0xFF).toByte()
        block[salt.size + 2] = ((i shr 8) and 0xFF).toByte()
        block[salt.size + 3] = (i and 0xFF).toByte()
        
        var u = mac.doFinal(block)
        val f = u.copyOf()
        
        // U_2 ... U_c
        for (j in 2..iterations) {
            u = mac.doFinal(u)
            for (k in f.indices) {
                f[k] = (f[k].toInt() xor u[k].toInt()).toByte()
            }
        }
        
        // 複製 F 到結果
        System.arraycopy(f, 0, dk, (i - 1) * hLen, hLen)
    }
    
    // 返回前 dkLen 字節
    return dk.copyOf(dkLen)
}

/**
 * Android 平台的 NFKD 正規化實現
 */
internal actual fun normalizeNfkdPlatform(text: String): String {
    return Normalizer.normalize(text, Normalizer.Form.NFKD)
}

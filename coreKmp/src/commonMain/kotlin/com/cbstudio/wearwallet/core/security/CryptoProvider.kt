package com.cbstudio.wearwallet.core.security

import com.cbstudio.wearwallet.core.domain.model.ChainType

/**
 * 跨平台加密提供者介面
 * 各平台需要實現自己的加密邏輯 (P1-6: 物理零化與契約更新，徹底消除 String 密鑰)
 */
interface CryptoProvider {
    /**
     * 從助記詞生成密鑰對
     */
    suspend fun generateKeyPairFromMnemonic(
        mnemonic: CharArray,
        derivationPath: String = "m/44'/60'/0'/0/0",
        chainType: ChainType = ChainType.ETHEREUM
    ): KeyPair

    /**
     * 從 ScopedMnemonic 生成密鑰對
     */
    suspend fun generateKeyPairFromMnemonic(
        scopedMnemonic: ScopedMnemonic,
        derivationPath: String = "m/44'/60'/0'/0/0",
        chainType: ChainType = ChainType.ETHEREUM
    ): KeyPair = scopedMnemonic.use { generateKeyPairFromMnemonic(it, derivationPath, chainType) }

    /**
     * 從十六進制私鑰字符陣列生成密鑰對
     */
    suspend fun generateKeyPairFromPrivateKey(privateKey: CharArray): KeyPair {
        val scopedKey = ScopedPrivateKey.fromHex(privateKey)
        return scopedKey.use { generateKeyPairFromPrivateKey(it) }
    }

    /**
     * 從私鑰字節數組生成密鑰對 (純字節操作，禁止轉 hex 字串)
     */
    suspend fun generateKeyPairFromPrivateKey(privateKeyBytes: ByteArray): KeyPair

    /**
     * 從 ScopedPrivateKey 生成密鑰對
     */
    suspend fun generateKeyPairFromPrivateKey(scopedKey: ScopedPrivateKey): KeyPair =
        scopedKey.use { generateKeyPairFromPrivateKey(it) }

    /**
     * 從公鑰導出地址
     */
    suspend fun deriveAddress(publicKey: String): String

    /**
     * 從擴展公鑰導出地址
     */
    suspend fun deriveAddressFromXpub(
        xpub: String,
        derivationPath: String,
        isTestnet: Boolean = false,
        policy: ExtendedPublicKeyPolicy? = null
    ): String

    /**
     * 加密數據
     */
    suspend fun encrypt(data: ByteArray, password: CharArray): ByteArray

    /**
     * 加密數據 (ScopedPassword)
     */
    suspend fun encrypt(data: ByteArray, password: ScopedPassword): ByteArray =
        password.use { encrypt(data, it) }

    /**
     * 解密數據
     */
    suspend fun decrypt(encryptedData: ByteArray, password: CharArray): ByteArray

    /**
     * 解密數據 (ScopedPassword)
     */
    suspend fun decrypt(encryptedData: ByteArray, password: ScopedPassword): ByteArray =
        password.use { decrypt(encryptedData, it) }

    /**
     * 生成助記詞 (ScopedMnemonic)
     */
    suspend fun generateMnemonic(wordCount: Int = 12): ScopedMnemonic

    /**
     * 驗證助記詞
     */
    suspend fun validateMnemonic(mnemonic: CharArray): Boolean

    /**
     * 驗證助記詞 (ScopedMnemonic)
     */
    suspend fun validateMnemonic(scopedMnemonic: ScopedMnemonic): Boolean =
        scopedMnemonic.use { validateMnemonic(it) }
}

/**
 * 密鑰對 (P1-4: 安全表示，移除 String 私鑰)
 */
data class KeyPair(
    val publicKey: String,
    val privateKeyBytes: ByteArray
) {
    /**
     * 支援從 ScopedPrivateKey 構建
     */
    constructor(publicKey: String, scopedKey: ScopedPrivateKey) : this(
        publicKey = publicKey,
        privateKeyBytes = scopedKey.use { it.copyOf() }
    )

    /**
     * 轉換為 ScopedPrivateKey
     */
    fun toScopedPrivateKey(): ScopedPrivateKey {
        return ScopedPrivateKey.fromByteArray(privateKeyBytes.copyOf())
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KeyPair) return false
        if (publicKey != other.publicKey) return false
        return privateKeyBytes.contentEquals(other.privateKeyBytes)
    }

    override fun hashCode(): Int {
        var result = publicKey.hashCode()
        result = 31 * result + privateKeyBytes.contentHashCode()
        return result
    }
}
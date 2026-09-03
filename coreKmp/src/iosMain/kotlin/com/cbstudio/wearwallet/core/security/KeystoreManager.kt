package com.cbstudio.wearwallet.core.security

import io.github.iml1s.crypto.Bip39
import io.github.iml1s.crypto.HDWallet
import io.github.iml1s.crypto.Secp256k1Pure
import io.github.iml1s.crypto.Hex
import io.github.iml1s.crypto.Keccak256
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * iOS 平台的 Keystore 管理器實現，完全採用純 Kotlin 加密模組 (:modules:kotlin-crypto-pure)
 */
actual class KeystoreManager {
    
    /**
     * 從助記詞推導私鑰
     */
    actual suspend fun derivePrivateKey(
        mnemonic: String,
        derivationPath: String
    ): String = withContext(Dispatchers.Default) {
        if (!validateMnemonic(mnemonic)) {
            throw IllegalArgumentException("Invalid mnemonic: $mnemonic")
        }
        val privateKeyBytes = HDWallet.derivePrivateKey(mnemonic, derivationPath)
        Hex.encode(privateKeyBytes)
    }
    
    /**
     * 生成新的助記詞
     */
    actual suspend fun generateMnemonic(strength: Int): String = withContext(Dispatchers.Default) {
        Bip39.generateMnemonic(strength)
    }
    
    /**
     * 驗證助記詞（包含 2048 詞表與 SHA256 checksum 校驗）
     */
    actual suspend fun validateMnemonic(mnemonic: String): Boolean = withContext(Dispatchers.Default) {
        try {
            Bip39.validate(mnemonic)
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 從私鑰獲取公鑰
     */
    actual suspend fun getPublicKey(privateKey: String): String = withContext(Dispatchers.Default) {
        val cleanHex = privateKey.removePrefix("0x")
        val privateKeyBytes = Hex.decode(cleanHex)
        val pubKeyBytes = Secp256k1Pure.getPublicKey(privateKeyBytes)
        Hex.encode(pubKeyBytes)
    }
    
    /**
     * 從公鑰獲取地址
     */
    actual suspend fun getAddress(
        publicKey: String,
        coinType: Int
    ): String = withContext(Dispatchers.Default) {
        // Only allow EVM coin types (60 = ETH, 966 = MATIC, 60-compatible EVMs)
        val isEvm = coinType in listOf(60, 966, 714, 1001, 8217, 43114)
        if (!isEvm) {
            throw com.cbstudio.wearwallet.core.domain.usecase.transaction.TypedUnsupportedTransactionException("Coin type $coinType is not supported on iOS")
        }
        val cleanHex = publicKey.removePrefix("0x")
        val pubKeyBytes = Hex.decode(cleanHex)
        val pubKeyWithoutPrefix = if (pubKeyBytes.size == 65) pubKeyBytes.sliceArray(1 until 65) else pubKeyBytes
        val keccak = Keccak256.hash(pubKeyWithoutPrefix)
        val addressBytes = keccak.sliceArray(12 until 32)
        "0x" + Hex.encode(addressBytes)
    }
}
package com.cbstudio.wearwallet.core.security

import wallet.core.jni.HDWallet
import wallet.core.jni.CoinType
import wallet.core.jni.PrivateKey
import wallet.core.jni.PublicKey
import wallet.core.jni.PublicKeyType
import wallet.core.jni.AnyAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android 平台的 Keystore 管理器實現
 * 使用 TrustWallet Core SDK
 */
actual class KeystoreManager {
    
    /**
     * 從助記詞推導私鑰
     */
    actual suspend fun derivePrivateKey(
        mnemonic: String,
        derivationPath: String
    ): String = withContext(Dispatchers.Default) {
        try {
            val hdWallet = HDWallet(mnemonic, "")
            
            // 解析推導路徑來確定幣種
            val coinType = parseCoinTypeFromPath(derivationPath)
            
            // 獲取私鑰
            val privateKey = hdWallet.getKey(coinType, derivationPath)
            
            // 轉換為 hex 字符串
            privateKey.data().toHexString()
        } catch (e: Exception) {
            throw Exception("Failed to derive private key: ${e.message}")
        }
    }
    
    /**
     * 生成新的助記詞
     */
    actual suspend fun generateMnemonic(strength: Int): String = withContext(Dispatchers.Default) {
        try {
            // strength 代表單詞數量，轉換為位數
            val bits = when (strength) {
                12 -> 128
                15 -> 160
                18 -> 192
                21 -> 224
                24 -> 256
                else -> 128 // 默認 12 個單詞
            }
            
            val hdWallet = HDWallet(bits, "")
            hdWallet.mnemonic()
        } catch (e: Exception) {
            throw Exception("Failed to generate mnemonic: ${e.message}")
        }
    }
    
    /**
     * 驗證助記詞
     */
    actual suspend fun validateMnemonic(mnemonic: String): Boolean = withContext(Dispatchers.Default) {
        try {
            io.github.iml1s.crypto.Bip39.validate(mnemonic)
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 從私鑰獲取公鑰
     */
    actual suspend fun getPublicKey(privateKey: String): String = withContext(Dispatchers.Default) {
        try {
            val privateKeyData = privateKey.hexToByteArray()
            val key = PrivateKey(privateKeyData)
            key.getPublicKey(CoinType.BITCOIN).data().toHexString()
        } catch (e: Exception) {
            throw Exception("Failed to get public key: ${e.message}")
        }
    }
    
    /**
     * 從公鑰獲取地址
     */
    actual suspend fun getAddress(
        publicKey: String,
        coinType: Int
    ): String = withContext(Dispatchers.Default) {
        try {
            val publicKeyData = publicKey.hexToByteArray()
            val pubKey = PublicKey(publicKeyData, PublicKeyType.SECP256K1)
            val coin = CoinType.values().find { it.value() == coinType } ?: CoinType.BITCOIN
            
            AnyAddress(pubKey, coin).description()
        } catch (e: Exception) {
            throw Exception("Failed to get address: ${e.message}")
        }
    }
    
    /**
     * 從推導路徑解析幣種類型
     */
    private fun parseCoinTypeFromPath(path: String): CoinType {
        // 解析 BIP44 路徑 m/44'/coin_type'/...
        val parts = path.split("/")
        if (parts.size < 3) return CoinType.BITCOIN
        
        val coinTypeStr = parts[2].replace("'", "")
        return when (coinTypeStr.toIntOrNull()) {
            0 -> CoinType.BITCOIN
            2 -> CoinType.LITECOIN
            3 -> CoinType.DOGECOIN
            60 -> CoinType.ETHEREUM
            145 -> CoinType.BITCOINCASH
            else -> CoinType.BITCOIN
        }
    }
    
    /**
     * ByteArray 轉 Hex 字符串
     */
    private fun ByteArray.toHexString(): String {
        return joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Hex 字符串轉 ByteArray
     */
    private fun String.hexToByteArray(): ByteArray {
        return chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }
}
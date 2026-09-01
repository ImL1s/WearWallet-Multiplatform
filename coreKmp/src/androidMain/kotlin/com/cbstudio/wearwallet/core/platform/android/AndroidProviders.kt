package com.cbstudio.wearwallet.core.platform.android

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.security.CryptoProvider
import com.cbstudio.wearwallet.core.security.KeyPair
import com.cbstudio.wearwallet.core.platform.SecureStorage
import wallet.core.jni.HDWallet
import wallet.core.jni.CoinType
import wallet.core.jni.PrivateKey
import wallet.core.jni.PublicKey
import wallet.core.jni.PublicKeyType
import wallet.core.jni.Curve
import wallet.core.jni.Hash
import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest

import io.github.iml1s.crypto.AesGcm
import io.github.iml1s.crypto.encryptString
import io.github.iml1s.crypto.decryptString

import com.cbstudio.wearwallet.core.security.SideEffectTracker
import com.cbstudio.wearwallet.core.security.GlobalSideEffectTracker

import com.cbstudio.wearwallet.core.security.ScopedMnemonic
import com.cbstudio.wearwallet.core.security.ScopedPassword
import com.cbstudio.wearwallet.core.security.CryptoUtils
import com.cbstudio.wearwallet.core.security.EncryptedData
import com.cbstudio.wearwallet.core.security.encodeToUtf8Bytes

/**
 * Android CryptoProvider 實現 - 使用新的 suspend 介面 (P1-6: 物理零化與契約更新)
 */
class AndroidCryptoProvider(
    private val sideEffectTracker: SideEffectTracker = GlobalSideEffectTracker.instance
) : CryptoProvider {
    
    init {
        // 初始化 TrustWallet Core
        try {
            android.util.Log.d("AndroidCryptoProvider", "🔧 初始化 TrustWallet Core...")
            // TrustWallet Core 通常在加載時自動初始化，但我們可以確保它已加載
            val testWallet = HDWallet(128, "")
            android.util.Log.d("AndroidCryptoProvider", "🔧 TrustWallet Core 初始化成功")
        } catch (e: Throwable) {
            android.util.Log.e("AndroidCryptoProvider", "❌ TrustWallet Core 初始化失敗: ${e.message}", e)
        }
    }
    
    override suspend fun generateKeyPairFromMnemonic(
        mnemonic: CharArray,
        derivationPath: String,
        chainType: ChainType
    ): KeyPair {
        sideEffectTracker.onSign()
        val mnemStr = String(mnemonic)
        try {
            println("🔧 AndroidCryptoProvider.generateKeyPairFromMnemonic 開始")
            println("   mnemonic: ${mnemonic.count { it.isWhitespace() } + 1} 個詞")
            println("   chainType: ${chainType.name}")
            println("   derivationPath: $derivationPath")
            
            val coinType = when (chainType) {
                ChainType.BITCOIN -> CoinType.BITCOIN
                ChainType.LITECOIN -> CoinType.LITECOIN
                ChainType.DOGECOIN -> CoinType.DOGECOIN
                ChainType.BITCOIN_CASH -> CoinType.BITCOINCASH
                ChainType.SOLANA -> CoinType.SOLANA
                else -> CoinType.ETHEREUM // Default for all EVM chains
            }
            
            val wallet = HDWallet(mnemStr, "")
            val privateKey = wallet.getKey(coinType, derivationPath)
            
            // 使用非壓縮公鑰格式 (65 bytes) 確保兼容性，除非是特定鏈
            val publicKey = privateKey.getPublicKeySecp256k1(false)
            
            val publicKeyHex = publicKey.data().toHexString()
            val privateKeyBytes = privateKey.data()
            
            println("🔧 密鑰對生成成功")
            println("   publicKey: $publicKeyHex")
            
            return KeyPair(
                publicKey = publicKeyHex,
                privateKeyBytes = privateKeyBytes
            )
        } catch (e: Exception) {
            println("❌ AndroidCryptoProvider.generateKeyPairFromMnemonic 失敗: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }
    
    override suspend fun generateKeyPairFromPrivateKey(privateKeyBytes: ByteArray): KeyPair {
        sideEffectTracker.onSign()
        val keyBytesCopy = privateKeyBytes.copyOf()
        try {
            val key = PrivateKey(keyBytesCopy)
            val publicKey = key.getPublicKeySecp256k1(false)
            
            return KeyPair(
                publicKey = publicKey.data().toHexString(),
                privateKeyBytes = keyBytesCopy.copyOf()
            )
        } finally {
            keyBytesCopy.fill(0)
        }
    }
    
    override suspend fun deriveAddress(publicKey: String): String {
        try {
            println("🔧 AndroidCryptoProvider.deriveAddress 開始")
            println("   輸入的公鑰: $publicKey")
            
            // 從公鑰導出地址
            val publicKeyHex = publicKey.removePrefix("0x")
            println("   處理後的公鑰: $publicKeyHex")
            println("   公鑰長度: ${publicKeyHex.length}")
            
            val publicKeyBytes = publicKeyHex.hexToByteArray()
            println("   公鑰字節數組長度: ${publicKeyBytes.size}")
            
            // 嘗試不同的方法來創建 PublicKey 對象
            val pubKey = when (publicKeyBytes.size) {
                33 -> {
                    // 壓縮公鑰 (33 bytes)
                    println("   使用壓縮公鑰格式")
                    PublicKey(publicKeyBytes, PublicKeyType.SECP256K1)
                }
                65 -> {
                    // 非壓縮公鑰 (65 bytes) - 嘗試移除前綴
                    println("   使用非壓縮公鑰格式，嘗試移除 0x04 前綴")
                    if (publicKeyBytes[0] == 0x04.toByte()) {
                        // 移除 0x04 前綴，只使用後 64 字節
                        val keyWithoutPrefix = publicKeyBytes.sliceArray(1..64)
                        println("   移除前綴後長度: ${keyWithoutPrefix.size}")
                        
                        // 嘗試多種方法
                        try {
                            println("   嘗試方法 1: 使用去除前綴的 64 字節")
                            PublicKey(keyWithoutPrefix, PublicKeyType.SECP256K1)
                        } catch (e1: Exception) {
                            println("   方法 1 失敗: ${e1.message}")
                            try {
                                println("   嘗試方法 2: 使用完整的 65 字節但不同類型")
                                PublicKey(publicKeyBytes, PublicKeyType.SECP256K1)
                            } catch (e2: Exception) {
                                println("   方法 2 失敗: ${e2.message}")
                                println("   嘗試方法 3: 手動計算地址")
                                throw e2
                            }
                        }
                    } else {
                        throw IllegalArgumentException("非壓縮公鑰必須以 0x04 開頭")
                    }
                }
                64 -> {
                    // 去掉 0x04 前綴的非壓縮公鑰 (64 bytes)
                    println("   64 字節公鑰，嘗試直接使用")
                    try {
                        PublicKey(publicKeyBytes, PublicKeyType.SECP256K1)
                    } catch (e1: Exception) {
                        println("   直接使用失敗: ${e1.message}")
                        println("   嘗試補充 0x04 前綴")
                        val fullPublicKey = byteArrayOf(0x04.toByte()) + publicKeyBytes
                        PublicKey(fullPublicKey, PublicKeyType.SECP256K1)
                    }
                }
                else -> {
                    throw IllegalArgumentException("無效的公鑰長度: ${publicKeyBytes.size}")
                }
            }
            
            // 使用 CoinType.ETHEREUM 來導出地址
            val address = CoinType.ETHEREUM.deriveAddressFromPublicKey(pubKey)
            println("🔧 地址導出成功: $address")
            
            return address
        } catch (e: Exception) {
            println("❌ AndroidCryptoProvider.deriveAddress 失敗: ${e.message}")
            println("❌ 嘗試備用方法：直接使用 Keccak256 計算地址")
            
            // 備用方法：使用 TrustWallet Core 的 Hash.keccak256 手動計算以太坊地址
            try {
                val publicKeyHex = publicKey.removePrefix("0x")
                val publicKeyBytes = publicKeyHex.hexToByteArray()
                
                // 對於 65 字節的非壓縮公鑰，移除前綴 0x04
                val keyForHashing = if (publicKeyBytes.size == 65 && publicKeyBytes[0] == 0x04.toByte()) {
                    publicKeyBytes.sliceArray(1..64)
                } else if (publicKeyBytes.size == 64) {
                    publicKeyBytes
                } else {
                    throw IllegalArgumentException("無法處理的公鑰格式")
                }
                
                println("🔧 使用 Keccak256 計算地址，公鑰長度: ${keyForHashing.size}")
                
                // 使用 TrustWallet Core 的 Keccak256 哈希
                val hash = Hash.keccak256(keyForHashing)
                
                // 取後 20 字節作為地址
                val addressBytes = hash.takeLast(20).toByteArray()
                val address = "0x" + addressBytes.toHexString()
                
                println("🔧 Keccak256 方法計算地址成功: $address")
                return address
            } catch (backupException: Exception) {
                println("❌ Keccak256 備用方法也失敗: ${backupException.message}")
                backupException.printStackTrace()
                e.printStackTrace()
                throw e
            }
        }
    }
    
    override suspend fun deriveAddressFromXpub(
        xpub: String,
        derivationPath: String,
        isTestnet: Boolean,
        policy: com.cbstudio.wearwallet.core.security.ExtendedPublicKeyPolicy?
    ): String {
        if (xpub.isBlank()) {
            throw IllegalArgumentException("xpub must not be empty")
        }
        policy?.validate(masterFingerprint = "", xpub = xpub, derivationPath = derivationPath, isTestnet = isTestnet)
        return io.github.iml1s.crypto.PureEthereumCrypto.deriveAddressFromXpub(
            xpub = xpub,
            path = derivationPath,
            isTestnet = isTestnet
        )
    }

    override suspend fun encrypt(data: ByteArray, password: CharArray): ByteArray {
        val passwordBytes = password.encodeToUtf8Bytes()
        val salt = "wearwallet_salt_v2".encodeToByteArray()
        val key = CryptoUtils.pbkdf2(passwordBytes, salt, 100_000, 32)
        try {
            val enc = CryptoUtils.aesGcmEncrypt(data, key)
            return enc.nonce + enc.authTag + enc.ciphertext
        } finally {
            passwordBytes.fill(0)
            key.fill(0)
        }
    }
    
    override suspend fun decrypt(encryptedData: ByteArray, password: CharArray): ByteArray {
        require(encryptedData.size >= 28) { "Invalid encrypted data length: ${encryptedData.size} (need >= 28)" }
        val nonce = encryptedData.sliceArray(0 until 12)
        val authTag = encryptedData.sliceArray(12 until 28)
        val ciphertext = encryptedData.sliceArray(28 until encryptedData.size)
        val passwordBytes = password.encodeToUtf8Bytes()
        val salt = "wearwallet_salt_v2".encodeToByteArray()
        val key = CryptoUtils.pbkdf2(passwordBytes, salt, 100_000, 32)
        try {
            return CryptoUtils.aesGcmDecrypt(EncryptedData(ciphertext, nonce, authTag), key)
        } finally {
            passwordBytes.fill(0)
            key.fill(0)
        }
    }
    
    override suspend fun generateMnemonic(wordCount: Int): ScopedMnemonic {
        println("🔧 AndroidCryptoProvider.generateMnemonic 開始")
        println("   wordCount: $wordCount")
        
        val strength = when (wordCount) {
            12 -> 128
            15 -> 160
            18 -> 192
            21 -> 224
            24 -> 256
            else -> throw IllegalArgumentException(
                "Unsupported word count: $wordCount. Allowed word counts are 12, 15, 18, 21, and 24."
            )
        }
        println("   strength: $strength")
        
        val wallet = HDWallet(strength, "")
        val mnemonic = wallet.mnemonic()
        if (mnemonic.isNullOrBlank()) {
            throw IllegalStateException("Trust Wallet Core returned null/empty mnemonic")
        }
        val chars = mnemonic.toCharArray()
        println("🔧 助記詞生成成功: ${chars.count { it.isWhitespace() } + 1} 個詞")
        return ScopedMnemonic.fromCharArray(chars, takeOwnership = true)
    }
    
    override suspend fun validateMnemonic(mnemonic: CharArray): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val mnemStr = String(mnemonic)
        val isValid = wallet.core.jni.Mnemonic.isValid(mnemStr)
        android.util.Log.d("AndroidCryptoProvider", "validateMnemonic result: $isValid")
        isValid
    }
    
    private fun ByteArray.toHexString(): String {
        return joinToString("") { "%02x".format(it) }
    }
    
    private fun String.hexToByteArray(): ByteArray {
        val hex = removePrefix("0x")
        return ByteArray(hex.length / 2) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}

/**
 * Android SecureStorage 實現
 */
class AndroidSecureStorage(private val context: Context) : SecureStorage {
    
    private val sharedPreferences: android.content.SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            
            EncryptedSharedPreferences.create(
                context,
                "secure_wallet_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            throw com.cbstudio.wearwallet.core.platform.SecureStorageInitializationException(
                "Failed to initialize EncryptedSharedPreferences (fail-closed): ${e.message}",
                e
            )
        }
    }
    
    override suspend fun encrypt(plainText: String): String {
        // 使用 EncryptedSharedPreferences 內建的加密
        // 返回原文，實際加密在儲存時進行
        return plainText
    }
    
    override suspend fun decrypt(encryptedText: String): String {
        // 使用 EncryptedSharedPreferences 內建的解密
        // 返回原文，實際解密在讀取時進行
        return encryptedText
    }
    
    override suspend fun saveSecure(key: String, value: String) {
        sharedPreferences.edit().putString(key, value).apply()
    }
    
    override suspend fun getSecure(key: String): String? {
        return sharedPreferences.getString(key, null)
    }
    
    override suspend fun removeSecure(key: String) {
        sharedPreferences.edit().remove(key).apply()
    }
    
    override suspend fun hasKey(key: String): Boolean {
        return sharedPreferences.contains(key)
    }
}
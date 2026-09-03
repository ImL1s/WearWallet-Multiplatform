package com.cbstudio.wearwallet.core.multichain.sdk

import io.github.iml1s.crypto.HmacSha512 as HmacSha512Impl
import io.github.iml1s.crypto.Secp256k1Pure
import io.github.iml1s.crypto.platformGetPublicKey
import io.github.iml1s.crypto.platformRipemd160
import io.github.iml1s.crypto.platformSha256
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.core.security.CryptoUtils
import io.github.iml1s.crypto.Keccak256

/**
 * iOS 實現 - 使用純 Kotlin 加密實現進行地址派生
 * 支援 BIP32/BIP44 標準
 * 
 * 使用：
 * - PBKDF2-HMAC-SHA512 從助記詞派生種子
 * - HMAC-SHA512 進行 HD 錢包派生
 * - Secp256k1Pure 進行公鑰生成
 * - RIPEMD160 + SHA256 進行 UTXO 地址生成
 * - Keccak256 進行 EVM 地址生成
 */
actual class AddressDerivation actual constructor() {
    
    companion object {
        private const val HARDENED_OFFSET = 0x80000000.toInt()
        private val HMAC_KEY_SEED = "Bitcoin seed".encodeToByteArray()
    }
    
    /**
     * 從助記詞派生指定鏈的地址
     */
    actual fun deriveAddress(mnemonic: String, chainType: MultiChainType): String {
        return try {
            // 1. 從助記詞生成種子
            val seed = generateSeedFromMnemonic(mnemonic)
            
            // 2. 獲取派生路徑
            val derivationPath = getDerivationPath(chainType)
            
            // 3. 派生私鑰和公鑰
            val (privateKey, _) = deriveKey(seed, derivationPath)
            val publicKey = platformGetPublicKey(privateKey)
            
            // 4. 根據鏈類型生成地址
            generateAddress(publicKey, chainType)
        } catch (e: Exception) {
            println("⚠️ iOS deriveAddress failed: ${e.message}")
            e.printStackTrace()
            ""
        }
    }
    
    /**
     * 從助記詞派生指定鏈的私鑰
     */
    actual fun derivePrivateKey(mnemonic: String, chainType: MultiChainType): ByteArray {
        return try {
            val seed = generateSeedFromMnemonic(mnemonic)
            val derivationPath = getDerivationPath(chainType)
            val (privateKey, _) = deriveKey(seed, derivationPath)
            privateKey
        } catch (e: Exception) {
            println("⚠️ iOS derivePrivateKey failed: ${e.message}")
            ByteArray(0)
        }
    }
    
    /**
     * 從助記詞生成種子（BIP39）
     */
    private fun generateSeedFromMnemonic(mnemonic: String, passphrase: String = ""): ByteArray {
        val salt = "mnemonic$passphrase".encodeToByteArray()
        return CryptoUtils.pbkdf2(
            password = mnemonic.encodeToByteArray(),
            salt = salt,
            iterations = 2048,
            keyLength = 64
        )
    }
    
    /**
     * BIP32 HD 錢包派生
     */
    private fun deriveKey(seed: ByteArray, path: String): Pair<ByteArray, ByteArray> {
        // 生成主密鑰
        val masterHmac = HmacSha512Impl.hmac(HMAC_KEY_SEED, seed)
        var privateKey = masterHmac.sliceArray(0..31)
        var chainCode = masterHmac.sliceArray(32..63)
        
        // 解析派生路徑並進行派生
        val indices = parsePath(path)
        for (index in indices) {
            val (newPrivateKey, newChainCode) = deriveChildKey(privateKey, chainCode, index)
            privateKey = newPrivateKey
            chainCode = newChainCode
        }
        
        return Pair(privateKey, chainCode)
    }
    
    /**
     * 派生子密鑰（BIP32）
     */
    private fun deriveChildKey(
        privateKey: ByteArray,
        chainCode: ByteArray,
        index: Int
    ): Pair<ByteArray, ByteArray> {
        val isHardened = index >= HARDENED_OFFSET
        
        val data = ByteArray(37)
        if (isHardened) {
            // 硬化派生：0x00 || 私鑰 || 索引
            data[0] = 0x00
            privateKey.copyInto(data, 1)
        } else {
            // 非硬化派生：公鑰 || 索引
            val publicKey = platformGetPublicKey(privateKey)
            publicKey.copyInto(data, 0)
        }
        
        // 添加索引（大端）
        data[33] = (index shr 24).toByte()
        data[34] = (index shr 16).toByte()
        data[35] = (index shr 8).toByte()
        data[36] = index.toByte()
        
        val hmac = HmacSha512Impl.hmac(chainCode, data)
        val childPrivateKeyPart = hmac.sliceArray(0..31)
        val childChainCode = hmac.sliceArray(32..63)
        
        // 計算子私鑰（模 N 加法）
        val childPrivateKey = addPrivateKeys(privateKey, childPrivateKeyPart)
        
        return Pair(childPrivateKey, childChainCode)
    }
    
    /**
     * 解析派生路徑
     */
    private fun parsePath(path: String): List<Int> {
        require(path.startsWith("m/")) { "Path must start with 'm/'" }
        
        return path.substring(2).split("/").filter { it.isNotEmpty() }.map { component ->
            if (component.endsWith("'") || component.endsWith("h")) {
                val index = component.dropLast(1).toInt()
                index or HARDENED_OFFSET
            } else {
                component.toInt()
            }
        }
    }
    
    /**
     * 根據鏈類型生成地址
     */
    private fun generateAddress(publicKey: ByteArray, chainType: MultiChainType): String {
        return when (chainType) {
            MultiChainType.BITCOIN -> generateBitcoinAddress(publicKey, 0x00)
            MultiChainType.LITECOIN -> generateBitcoinAddress(publicKey, 0x30)
            MultiChainType.DOGECOIN -> generateBitcoinAddress(publicKey, 0x1E)
            MultiChainType.BITCOIN_CASH -> generateBitcoinAddress(publicKey, 0x00) // 傳統格式
            
            MultiChainType.ETHEREUM,
            MultiChainType.BSC,
            MultiChainType.POLYGON,
            MultiChainType.AVALANCHE,
            MultiChainType.ARBITRUM,
            MultiChainType.OPTIMISM,
            MultiChainType.FANTOM,
            MultiChainType.CRONOS,
            MultiChainType.BASE,
            MultiChainType.CELO,
            MultiChainType.MOONBEAM -> generateEthereumAddress(publicKey)
            
            MultiChainType.TRON -> generateTronAddress(publicKey)
            MultiChainType.SOLANA -> base58Encode(publicKey) // 簡化實現
            
            else -> throw IllegalArgumentException("Unsupported chain: $chainType")
        }
    }
    
    /**
     * 生成 Bitcoin 風格地址（P2PKH）
     */
    private fun generateBitcoinAddress(publicKey: ByteArray, version: Int): String {
        val sha256 = platformSha256(publicKey)
        val ripemd160 = platformRipemd160(sha256)
        val versionedHash = byteArrayOf(version.toByte()) + ripemd160
        val checksum = platformSha256(platformSha256(versionedHash)).take(4).toByteArray()
        return base58Encode(versionedHash + checksum)
    }
    
    /**
     * 生成 Ethereum 地址
     */
    private fun generateEthereumAddress(compressedPublicKey: ByteArray): String {
        // 解壓縮公鑰
        val uncompressedKey = Secp256k1Pure.let { secp ->
            val point = secp.decodePublicKey(compressedPublicKey)
            secp.encodePublicKey(point, compressed = false)
        }
        // 移除前綴 0x04
        val keyWithoutPrefix = uncompressedKey.drop(1).toByteArray()
        // Keccak256 哈希
        val hash = Keccak256.hash(keyWithoutPrefix)
        // 取最後 20 字節
        return "0x${hash.takeLast(20).toByteArray().toHexString()}"
    }
    
    /**
     * 生成 TRON 地址
     */
    private fun generateTronAddress(compressedPublicKey: ByteArray): String {
        val uncompressedKey = Secp256k1Pure.let { secp ->
            val point = secp.decodePublicKey(compressedPublicKey)
            secp.encodePublicKey(point, compressed = false)
        }
        val keyWithoutPrefix = uncompressedKey.drop(1).toByteArray()
        val hash = Keccak256.hash(keyWithoutPrefix)
        val addressBytes = byteArrayOf(0x41) + hash.takeLast(20).toByteArray()
        val checksum = platformSha256(platformSha256(addressBytes)).take(4).toByteArray()
        return base58Encode(addressBytes + checksum)
    }
    
    /**
     * 獲取派生路徑
     */
    private fun getDerivationPath(chainType: MultiChainType): String {
        return when (chainType) {
            MultiChainType.BITCOIN -> "m/84'/0'/0'/0/0"
            MultiChainType.LITECOIN -> "m/84'/2'/0'/0/0"
            MultiChainType.DOGECOIN -> "m/44'/3'/0'/0/0"
            MultiChainType.BITCOIN_CASH -> "m/44'/145'/0'/0/0"
            MultiChainType.ETHEREUM,
            MultiChainType.BSC,
            MultiChainType.POLYGON,
            MultiChainType.AVALANCHE,
            MultiChainType.ARBITRUM,
            MultiChainType.OPTIMISM,
            MultiChainType.FANTOM,
            MultiChainType.CRONOS,
            MultiChainType.BASE,
            MultiChainType.CELO,
            MultiChainType.MOONBEAM -> "m/44'/60'/0'/0/0"
            MultiChainType.SOLANA -> "m/44'/501'/0'/0'"
            MultiChainType.TRON -> "m/44'/195'/0'/0/0"
            else -> throw IllegalArgumentException("Unsupported chain: $chainType")
        }
    }
    
    /**
     * 私鑰模 N 加法
     */
    private fun addPrivateKeys(key1: ByteArray, key2: ByteArray): ByteArray {
        val n = Secp256k1Pure.BigInteger(
            byteArrayOf(
                0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
                0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
                0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
                0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFE.toByte(),
                0xBA.toByte(), 0xAE.toByte(), 0xDC.toByte(), 0xE6.toByte(),
                0xAF.toByte(), 0x48.toByte(), 0xA0.toByte(), 0x3B.toByte(),
                0xBF.toByte(), 0xD2.toByte(), 0x5E.toByte(), 0x8C.toByte(),
                0xD0.toByte(), 0x36.toByte(), 0x41.toByte(), 0x41.toByte()
            )
        )
        
        val k1 = Secp256k1Pure.BigInteger(key1)
        val k2 = Secp256k1Pure.BigInteger(key2)
        val result = (k1 + k2).mod(n)
        
        return result.toByteArray().let { bytes ->
            if (bytes.size < 32) {
                ByteArray(32 - bytes.size) + bytes
            } else {
                bytes.takeLast(32).toByteArray()
            }
        }
    }
    
    /**
     * Base58 編碼
     */
    private fun base58Encode(data: ByteArray): String {
        val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        var num = Secp256k1Pure.BigInteger(data)
        val base = Secp256k1Pure.BigInteger(byteArrayOf(58))
        val zero = Secp256k1Pure.BigInteger.ZERO
        
        val encoded = StringBuilder()
        while (num > zero) {
            val remainder = (num % base).toByteArray().lastOrNull()?.toInt()?.and(0xFF) ?: 0
            encoded.append(alphabet[remainder % 58])
            num = num / base
        }
        
        for (byte in data) {
            if (byte == 0.toByte()) {
                encoded.append(alphabet[0])
            } else {
                break
            }
        }
        
        return encoded.reverse().toString()
    }
    
    /**
     * ByteArray 轉十六進制字符串
     */
    private fun ByteArray.toHexString(): String {
        return joinToString("") { byte ->
            (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
        }
    }
}
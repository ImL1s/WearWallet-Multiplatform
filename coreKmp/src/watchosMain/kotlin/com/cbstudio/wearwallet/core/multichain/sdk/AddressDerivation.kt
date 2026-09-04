package com.cbstudio.wearwallet.core.multichain.sdk

import io.github.iml1s.crypto.HDWallet
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.core.security.CryptoUtils

/**
 * watchOS 實現 - 使用純 Kotlin 的 HDWallet 實現
 * 支援 BIP32/BIP44 標準的地址派生
 */
actual class AddressDerivation actual constructor() {
    
    private val hdWallet = HDWallet()
    
    /**
     * 從助記詞派生指定鏈的地址
     * 使用純 Kotlin 的 BIP32/BIP44 HD 錢包實現
     */
    actual fun deriveAddress(mnemonic: String, chainType: MultiChainType): String {
        return try {
            // 1. 從助記詞生成種子
            val seed = generateSeedFromMnemonic(mnemonic)
            
            // 2. 從種子生成主密鑰
            val masterKey = hdWallet.generateMasterKey(seed)
            
            // 3. 根據鏈類型選擇派生路徑和 coin type
            val (derivationPath, network) = getDerivationConfig(chainType)
            
            // 4. 派生子密鑰
            val derivedKey = hdWallet.deriveFromPath(masterKey, derivationPath)
            
            // 5. 根據鏈類型生成地址
            when (chainType) {
                MultiChainType.BITCOIN,
                MultiChainType.LITECOIN,
                MultiChainType.DOGECOIN,
                MultiChainType.BITCOIN_CASH -> {
                    // UTXO 鏈使用 Base58Check 編碼
                    hdWallet.getAddress(derivedKey, network)
                }
                MultiChainType.ETHEREUM,
                MultiChainType.BSC,
                MultiChainType.POLYGON,
                MultiChainType.ARBITRUM,
                MultiChainType.OPTIMISM,
                MultiChainType.AVALANCHE,
                MultiChainType.FANTOM,
                MultiChainType.CRONOS,
                MultiChainType.BASE,
                MultiChainType.CELO,
                MultiChainType.MOONBEAM -> {
                    // EVM 鏈使用 Keccak256 生成地址
                    generateEthereumAddress(derivedKey.publicKey)
                }
                MultiChainType.SOLANA -> {
                    // Solana 使用 Ed25519，需要不同的派生
                    generateSolanaAddress(derivedKey.publicKey)
                }
                MultiChainType.TRON -> {
                    // TRON 類似 Ethereum 但使用不同前綴
                    generateTronAddress(derivedKey.publicKey)
                }
                else -> throw IllegalArgumentException("Unsupported chain type: $chainType")
            }
        } catch (e: Exception) {
            println("⚠️ watchOS deriveAddress failed: ${e.message}")
            // 返回空字符串而不是假地址
            ""
        }
    }
    
    /**
     * 從助記詞派生指定鏈的私鑰
     */
    actual fun derivePrivateKey(mnemonic: String, chainType: MultiChainType): ByteArray {
        return try {
            // 1. 從助記詞生成種子
            val seed = generateSeedFromMnemonic(mnemonic)
            
            // 2. 從種子生成主密鑰
            val masterKey = hdWallet.generateMasterKey(seed)
            
            // 3. 獲取派生路徑
            val (derivationPath, _) = getDerivationConfig(chainType)
            
            // 4. 派生子密鑰
            val derivedKey = hdWallet.deriveFromPath(masterKey, derivationPath)
            
            derivedKey.privateKey ?: throw Exception("Failed to derive private key")
        } catch (e: Exception) {
            println("⚠️ watchOS derivePrivateKey failed: ${e.message}")
            ByteArray(0)
        }
    }
    
    /**
     * 從助記詞生成種子（BIP39 標準）
     */
    private fun generateSeedFromMnemonic(mnemonic: String, passphrase: String = ""): ByteArray {
        val salt = "mnemonic$passphrase".encodeToByteArray()
        return CryptoUtils.pbkdf2(
            password = mnemonic.encodeToByteArray(),
            salt = salt,
            iterations = 2048,
            keyLength = 64  // 512 bits
        )
    }
    
    /**
     * 獲取派生配置（路徑和網路類型）
     */
    private fun getDerivationConfig(chainType: MultiChainType): Pair<String, String> {
        return when (chainType) {
            // UTXO 鏈
            MultiChainType.BITCOIN -> Pair("m/84'/0'/0'/0/0", "bitcoin")      // BIP84 Native SegWit
            MultiChainType.LITECOIN -> Pair("m/84'/2'/0'/0/0", "litecoin")    // BIP84 for Litecoin
            MultiChainType.DOGECOIN -> Pair("m/44'/3'/0'/0/0", "dogecoin")    // BIP44 for Dogecoin
            MultiChainType.BITCOIN_CASH -> Pair("m/44'/145'/0'/0/0", "bitcoincash") // BIP44 for BCH
            
            // EVM 兼容鏈
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
            MultiChainType.MOONBEAM -> Pair("m/44'/60'/0'/0/0", "ethereum")
            
            // 其他鏈
            MultiChainType.SOLANA -> Pair("m/44'/501'/0'/0'", "solana")
            MultiChainType.TRON -> Pair("m/44'/195'/0'/0/0", "tron")
            
            else -> throw IllegalArgumentException("Unsupported chain type: $chainType")
        }
    }
    
    /**
     * 生成 Ethereum 地址
     */
    private fun generateEthereumAddress(compressedPublicKey: ByteArray): String {
        val uncompressedKey = decompressPublicKey(compressedPublicKey)
        // 移除前綴 0x04
        val keyWithoutPrefix = uncompressedKey.drop(1).toByteArray()
        // Keccak256 哈希
        val hash = io.github.iml1s.crypto.Keccak256.hash(keyWithoutPrefix)
        // 取最後 20 字節
        return "0x${hash.takeLast(20).toByteArray().toHexString()}"
    }
    
    /**
     * 生成 Solana 地址（簡化實現）
     */
    private fun generateSolanaAddress(publicKey: ByteArray): String {
        // Solana 使用 Base58 編碼的公鑰
        // 這是簡化實現，完整的 Solana 需要 Ed25519
        return base58Encode(publicKey)
    }
    
    /**
     * 生成 TRON 地址
     */
    private fun generateTronAddress(compressedPublicKey: ByteArray): String {
        val uncompressedKey = decompressPublicKey(compressedPublicKey)
        // 移除前綴 0x04
        val keyWithoutPrefix = uncompressedKey.drop(1).toByteArray()
        // Keccak256 哈希
        val hash = io.github.iml1s.crypto.Keccak256.hash(keyWithoutPrefix)
        // 取最後 20 字節並添加 TRON 前綴 0x41
        val addressBytes = byteArrayOf(0x41) + hash.takeLast(20).toByteArray()
        // Base58Check 編碼
        return base58CheckEncode(addressBytes)
    }
    
    /**
     * 解壓縮公鑰
     */
    private fun decompressPublicKey(compressed: ByteArray): ByteArray {
        return io.github.iml1s.crypto.Secp256k1Pure.let { secp ->
            val point = secp.decodePublicKey(compressed)
            secp.encodePublicKey(point, compressed = false)
        }
    }
    
    /**
     * Base58 編碼
     */
    private fun base58Encode(data: ByteArray): String {
        val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        var num = io.github.iml1s.crypto.Secp256k1Pure.BigInteger(data)
        val base = io.github.iml1s.crypto.Secp256k1Pure.BigInteger(byteArrayOf(58))
        val zero = io.github.iml1s.crypto.Secp256k1Pure.BigInteger.ZERO
        
        val encoded = StringBuilder()
        while (num > zero) {
            val remainder = (num % base).toByteArray().lastOrNull()?.toInt()?.and(0xFF) ?: 0
            encoded.append(alphabet[remainder % 58])
            num = num / base
        }
        
        // 添加前導零
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
     * Base58Check 編碼
     */
    private fun base58CheckEncode(data: ByteArray): String {
        val secp = io.github.iml1s.crypto.Secp256k1Pure
        val checksum = secp.sha256(secp.sha256(data)).take(4).toByteArray()
        return base58Encode(data + checksum)
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
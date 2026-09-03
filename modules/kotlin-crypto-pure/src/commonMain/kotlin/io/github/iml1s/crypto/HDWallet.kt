package io.github.iml1s.crypto

import org.kotlincrypto.hash.sha2.SHA512
import kotlin.experimental.and
import kotlin.experimental.xor

/**
 * HD Wallet implementation for BIP32/BIP39
 * This provides hierarchical deterministic key derivation
 */
class HDWallet {
    
    companion object {
        const val HARDENED_OFFSET = 0x80000000.toInt()
        const val SEED_LENGTH = 64
        
        // BIP44 constants
        const val PURPOSE = 44
        const val COINTYPE_BTC = 0
        const val COINTYPE_LTC = 2
        const val COINTYPE_DOGE = 3
        const val COINTYPE_BCH = 145
        
        // Network versions for extended keys
        const val XPRV_VERSION = 0x0488ADE4
        const val XPUB_VERSION = 0x0488B21E
        
        private val HMAC_KEY_SEED = "Bitcoin seed".encodeToByteArray()
    }
    
    /**
     * Extended Key structure for BIP32
     */
    data class ExtendedKey(
        val privateKey: ByteArray?,
        val publicKey: ByteArray,
        val chainCode: ByteArray,
        val depth: Int,
        val parentFingerprint: ByteArray,
        val childNumber: Int
    ) {
        fun isPrivate(): Boolean = privateKey != null
        
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ExtendedKey) return false
            
            if (!privateKey.contentEquals(other.privateKey)) return false
            if (!publicKey.contentEquals(other.publicKey)) return false
            if (!chainCode.contentEquals(other.chainCode)) return false
            if (depth != other.depth) return false
            if (!parentFingerprint.contentEquals(other.parentFingerprint)) return false
            if (childNumber != other.childNumber) return false
            
            return true
        }
        
        override fun hashCode(): Int {
            var result = privateKey?.contentHashCode() ?: 0
            result = 31 * result + publicKey.contentHashCode()
            result = 31 * result + chainCode.contentHashCode()
            result = 31 * result + depth
            result = 31 * result + parentFingerprint.contentHashCode()
            result = 31 * result + childNumber
            return result
        }
    }
    
    /**
     * Generate master key from seed
     */
    fun generateMasterKey(seed: ByteArray): ExtendedKey {
        require(seed.size >= 16) { "Seed must be at least 128 bits, got ${seed.size}" }
        require(seed.size <= 64) { "Seed must be at most 512 bits, got ${seed.size}" }
        
        val hmac = try {
            hmacSha512(HMAC_KEY_SEED, seed)
        } catch (e: Exception) {
            throw Exception("Failed to compute HMAC-SHA512: ${e.message}", e)
        }
        
        val privateKey = hmac.sliceArray(0..31)
        val chainCode = hmac.sliceArray(32..63)
        
        // Generate public key from private key
        val publicKey = try {
            Secp256k1Pure.generatePublicKey(privateKey, compressed = true)
        } catch (e: Exception) {
            throw Exception("Failed to generate public key from private key: ${e.message}", e)
        }
        
        return ExtendedKey(
            privateKey = privateKey,
            publicKey = publicKey,
            chainCode = chainCode,
            depth = 0,
            parentFingerprint = byteArrayOf(0, 0, 0, 0),
            childNumber = 0
        )
    }
    
    /**
     * Derive child key from parent
     */
    fun deriveChildKey(parent: ExtendedKey, index: Int): ExtendedKey {
        val isHardened = index >= HARDENED_OFFSET
        
        // Prepare data for HMAC
        val data = ByteArray(37)
        if (isHardened) {
            // Hardened derivation: 0x00 || private key || index
            require(parent.privateKey != null) { "Hardened derivation requires private key" }
            data[0] = 0x00
            parent.privateKey.copyInto(data, 1)
        } else {
            // Non-hardened derivation: public key || index
            parent.publicKey.copyInto(data, 0)
        }
        
        // Add index (big-endian)
        data[33] = (index shr 24).toByte()
        data[34] = (index shr 16).toByte()
        data[35] = (index shr 8).toByte()
        data[36] = index.toByte()
        
        val hmac = hmacSha512(parent.chainCode, data)
        val childPrivateKeyPart = hmac.sliceArray(0..31)
        val childChainCode = hmac.sliceArray(32..63)
        
        // Calculate child private key
        val childPrivateKey = if (parent.privateKey != null) {
            addPrivateKeys(parent.privateKey, childPrivateKeyPart)
        } else null
        
        // Generate child public key
        val childPublicKey = if (childPrivateKey != null) {
            Secp256k1Pure.generatePublicKey(childPrivateKey, compressed = true)
        } else {
            // For public key derivation (watching wallets)
            addPublicKeys(parent.publicKey, childPrivateKeyPart)
        }
        
        // Calculate parent fingerprint (first 4 bytes of HASH160 of parent public key)
        val parentFingerprint = hash160(parent.publicKey).sliceArray(0..3)
        
        return ExtendedKey(
            privateKey = childPrivateKey,
            publicKey = childPublicKey,
            chainCode = childChainCode,
            depth = parent.depth + 1,
            parentFingerprint = parentFingerprint,
            childNumber = index
        )
    }
    
    /**
     * Derive key using BIP44 path
     * m/44'/coin'/account'/change/address_index
     */
    fun deriveBIP44Key(
        masterKey: ExtendedKey,
        coinType: Int,
        account: Int = 0,
        change: Int = 0,
        addressIndex: Int = 0
    ): ExtendedKey {
        var key = masterKey
        
        // m/44'
        key = deriveChildKey(key, PURPOSE or HARDENED_OFFSET)
        
        // m/44'/coin'
        key = deriveChildKey(key, coinType or HARDENED_OFFSET)
        
        // m/44'/coin'/account'
        key = deriveChildKey(key, account or HARDENED_OFFSET)
        
        // m/44'/coin'/account'/change
        key = deriveChildKey(key, change)
        
        // m/44'/coin'/account'/change/address_index
        key = deriveChildKey(key, addressIndex)
        
        return key
    }
    
    /**
     * Parse derivation path string (e.g., "m/44'/0'/0'/0/0")
     */
    fun parsePath(path: String): List<Int> {
        require(path.startsWith("m/")) { "Path must start with 'm/'" }
        
        return path.substring(2).split("/").map { component ->
            if (component.endsWith("'") || component.endsWith("h")) {
                // Hardened key
                val index = component.dropLast(1).toInt()
                index or HARDENED_OFFSET
            } else {
                component.toInt()
            }
        }
    }
    
    /**
     * Derive key from path
     */
    fun deriveFromPath(masterKey: ExtendedKey, path: String): ExtendedKey {
        val indices = parsePath(path)
        return indices.fold(masterKey) { key, index ->
            deriveChildKey(key, index)
        }
    }
    
    /**
     * Generate address from extended key
     */
    fun getAddress(key: ExtendedKey, network: String): String {
        val publicKeyHash = hash160(key.publicKey)
        
        val version = when (network.lowercase()) {
            "bitcoin" -> 0x00
            "litecoin" -> 0x30
            "dogecoin" -> 0x1E
            "bitcoincash" -> 0x00
            else -> throw IllegalArgumentException("Unsupported network: $network")
        }
        
        return Base58Check.encode(byteArrayOf(version.toByte()) + publicKeyHash)
    }
    
    // Cryptographic helper functions
    
    private fun hmacSha512(key: ByteArray, data: ByteArray): ByteArray {
        val blockSize = 128
        val opadByte = 0x5C.toByte()
        val ipadByte = 0x36.toByte()
        
        // Prepare key
        val processedKey = when {
            key.size > blockSize -> sha512(key)
            key.size < blockSize -> key + ByteArray(blockSize - key.size)
            else -> key
        }
        
        // Calculate inner hash
        val innerPad = ByteArray(blockSize) { i -> processedKey[i] xor ipadByte }
        val innerHash = sha512(innerPad + data)
        
        // Calculate outer hash
        val outerPad = ByteArray(blockSize) { i -> processedKey[i] xor opadByte }
        return sha512(outerPad + innerHash)
    }
    
    private fun sha512(data: ByteArray): ByteArray {
        // SHA-512 implementation using kotlincrypto
        return SHA512().digest(data)
    }
    
    private fun hash160(data: ByteArray): ByteArray {
        // RIPEMD160(SHA256(data))
        val sha256 = sha256(data)
        return ripemd160(sha256)
    }
    
    private fun sha256(data: ByteArray): ByteArray {
        return Secp256k1Pure.sha256(data)
    }
    
    private fun ripemd160(data: ByteArray): ByteArray {
        // RIPEMD-160 implementation
        return Ripemd160.hash(data)
    }
    
    private fun addPrivateKeys(key1: ByteArray, key2: ByteArray): ByteArray {
        // Add two private keys modulo secp256k1 order
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
    
    private fun addPublicKeys(pubKey: ByteArray, privateKey: ByteArray): ByteArray {
        // Add public key point with point derived from private key
        val point1 = Secp256k1Pure.decodePublicKey(pubKey)
        val point2 = Secp256k1Pure.generatePublicKeyPoint(privateKey)
        val result = Secp256k1Pure.addPoints(point1, point2)
        return Secp256k1Pure.encodePublicKey(result, compressed = true)
    }
}

/**
 * Base58Check encoding/decoding
 */
object Base58Check {
    private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    
    fun encode(data: ByteArray): String {
        val checksum = Secp256k1Pure.sha256(Secp256k1Pure.sha256(data)).sliceArray(0..3)
        val dataWithChecksum = data + checksum
        
        // Convert to base58
        var num = Secp256k1Pure.BigInteger(dataWithChecksum)
        val base = Secp256k1Pure.BigInteger(byteArrayOf(58))
        val zero = Secp256k1Pure.BigInteger.ZERO
        
        val encoded = StringBuilder()
        while (num > zero) {
            val remainder = (num % base).toByteArray().last().toInt() and 0xFF
            encoded.append(ALPHABET[remainder % 58])
            num = num / base
        }
        
        // Add leading zeros
        for (byte in dataWithChecksum) {
            if (byte.toInt() == 0) {
                encoded.append(ALPHABET[0])
            } else {
                break
            }
        }
        
        return encoded.reverse().toString()
    }
    
    fun decode(encoded: String): ByteArray {
        // Decode from base58
        var num = Secp256k1Pure.BigInteger.ZERO
        val base = Secp256k1Pure.BigInteger(byteArrayOf(58))
        
        for (char in encoded) {
            val digit = ALPHABET.indexOf(char)
            if (digit == -1) throw IllegalArgumentException("Invalid base58 character: $char")
            num = num * base + Secp256k1Pure.BigInteger(byteArrayOf(digit.toByte()))
        }
        
        var bytes = num.toByteArray()
        
        // Add leading zeros
        val leadingZeros = encoded.takeWhile { it == ALPHABET[0] }.length
        bytes = ByteArray(leadingZeros) + bytes
        
        // Verify checksum
        val data = bytes.dropLast(4).toByteArray()
        val checksum = bytes.takeLast(4).toByteArray()
        val calculatedChecksum = Secp256k1Pure.sha256(Secp256k1Pure.sha256(data)).sliceArray(0..3)
        
        require(checksum.contentEquals(calculatedChecksum)) { "Invalid checksum" }
        
        return data
    }
}
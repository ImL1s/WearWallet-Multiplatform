@file:OptIn(ExperimentalForeignApi::class, UnsafeNumber::class)
package com.cbstudio.wearwallet.core.platform.watchos

import com.cbstudio.wearwallet.core.security.CryptoProvider
import com.cbstudio.wearwallet.core.security.KeyPair
import com.cbstudio.wearwallet.core.security.ScopedMnemonic
import com.cbstudio.wearwallet.core.security.ScopedPassword
import kotlinx.cinterop.*
import platform.Foundation.*
import platform.Security.*
import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class, ExperimentalForeignApi::class)
class WatchOSCryptoProvider : CryptoProvider {
    
    private val BIP39_ENGLISH_WORDLIST = io.github.iml1s.crypto.BIP39_ENGLISH_WORDLIST

    override suspend fun generateMnemonic(wordCount: Int): ScopedMnemonic {
        val entropyBits = when (wordCount) {
            12 -> 128
            15 -> 160
            18 -> 192
            21 -> 224
            24 -> 256
            else -> throw IllegalArgumentException("Invalid word count: $wordCount")
        }
        
        val entropyBytes = ByteArray(entropyBits / 8)
        memScoped {
            @Suppress("EXPERIMENTAL_API_USAGE")
            val result = SecRandomCopyBytes(
                kSecRandomDefault,
                entropyBytes.size.convert(),
                entropyBytes.refTo(0)
            )
            if (result != errSecSuccess) {
                throw RuntimeException("Failed to generate secure random bytes")
            }
        }
        
        val chars = entropyToMnemonicChars(entropyBytes)
        entropyBytes.fill(0)
        return ScopedMnemonic.fromCharArray(chars, takeOwnership = true)
    }
    
    override suspend fun validateMnemonic(mnemonic: CharArray): Boolean {
        val words = mutableListOf<String>()
        val currentWord = StringBuilder()
        for (c in mnemonic) {
            if (c.isWhitespace()) {
                if (currentWord.isNotEmpty()) {
                    words.add(currentWord.toString().lowercase())
                    currentWord.clear()
                }
            } else {
                currentWord.append(c)
            }
        }
        if (currentWord.isNotEmpty()) {
            words.add(currentWord.toString().lowercase())
            currentWord.clear()
        }
        
        if (words.size !in listOf(12, 15, 18, 21, 24)) {
            return false
        }
        
        if (!words.all { BIP39_ENGLISH_WORDLIST.contains(it) }) {
            return false
        }
        
        return try {
            val entropy = wordsToEntropy(words)
            entropy.fill(0)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun generateKeyPairFromMnemonic(
        mnemonic: CharArray,
        derivationPath: String,
        chainType: com.cbstudio.wearwallet.core.domain.model.ChainType
    ): KeyPair {
        val delegate = com.cbstudio.wearwallet.core.security.NativeCrypto.delegateOrNull
        if (delegate != null) {
            val mnemStr = String(mnemonic)
            val result = delegate.generateKeyPair(mnemStr, derivationPath, chainType)
            require(result.publicKey.isNotBlank()) { "Delegate returned empty public key" }
            require(result.privateKeyBytes.isNotEmpty()) { "Delegate returned empty private key" }
            return result
        }

        throw UnsupportedOperationException(
            "watchOS key pair generation requires native crypto delegate. No fallback to placeholder implementations."
        )
    }
    
    override suspend fun generateKeyPairFromPrivateKey(privateKeyBytes: ByteArray): KeyPair {
        throw UnsupportedOperationException(
            "watchOS generateKeyPairFromPrivateKey requires native crypto delegate. No fallback to placeholder implementations."
        )
    }
    
    override suspend fun deriveAddress(publicKey: String): String {
        throw UnsupportedOperationException(
            "watchOS deriveAddress requires native Keccak-256 implementation."
        )
    }
    
    override suspend fun deriveAddressFromXpub(
        xpub: String,
        derivationPath: String,
        isTestnet: Boolean,
        policy: com.cbstudio.wearwallet.core.security.ExtendedPublicKeyPolicy?
    ): String {
        policy?.validate(masterFingerprint = "", xpub = xpub, derivationPath = derivationPath, isTestnet = isTestnet)
        if (!isTestnet && xpub.startsWith("tpub", ignoreCase = true)) {
            throw IllegalArgumentException("Testnet xpub (tpub) is not allowed on mainnet context")
        }
        if (isTestnet && xpub.startsWith("xpub", ignoreCase = true)) {
            throw IllegalArgumentException("Mainnet xpub (xpub) is not allowed on testnet context")
        }
        val delegate = com.cbstudio.wearwallet.core.security.NativeCrypto.delegateOrNull
        if (delegate != null) {
            val address = delegate.deriveAddressFromXpub(xpub, derivationPath, isTestnet = isTestnet, policy = policy)
            require(address.isNotBlank()) { "Delegate returned empty address for xpub" }
            return address
        }

        throw UnsupportedOperationException(
            "watchOS xpub address derivation requires native crypto delegate. No fallback to placeholder implementations."
        )
    }
    
    override suspend fun encrypt(data: ByteArray, password: CharArray): ByteArray {
        throw UnsupportedOperationException(
            "watchOS encryption is fail-closed and disabled pending native CryptoKit/Keychain integration."
        )
    }
    
    override suspend fun decrypt(encryptedData: ByteArray, password: CharArray): ByteArray {
        throw UnsupportedOperationException(
            "watchOS decryption is fail-closed and disabled pending native CryptoKit/Keychain integration."
        )
    }
    
    private fun entropyToMnemonicChars(entropy: ByteArray): CharArray {
        val hash = sha256(entropy)
        val checksumBits = entropy.size / 4
        
        val bits = entropy.toBitString() + hash.toBitString().take(checksumBits)
        
        val words = mutableListOf<String>()
        var totalChars = 0
        for (i in bits.indices step 11) {
            if (i + 11 <= bits.length) {
                val index = bits.substring(i, i + 11).toInt(2)
                val word = BIP39_ENGLISH_WORDLIST[index]
                words.add(word)
                totalChars += word.length
            }
        }
        totalChars += maxOf(0, words.size - 1)
        val result = CharArray(totalChars)
        var pos = 0
        for (idx in words.indices) {
            val word = words[idx]
            for (c in word) {
                result[pos++] = c
            }
            if (idx < words.size - 1) {
                result[pos++] = ' '
            }
        }
        return result
    }
    
    private fun wordsToEntropy(words: List<String>): ByteArray {
        val bits = words.joinToString("") { word ->
            val index = BIP39_ENGLISH_WORDLIST.indexOf(word)
            if (index == -1) throw IllegalArgumentException("Invalid mnemonic word: $word")
            index.toString(2).padStart(11, '0')
        }
        
        val entropyBits = (words.size * 11 * 32) / 33
        val entropyString = bits.take(entropyBits)
        val checksumString = bits.drop(entropyBits)
        
        val entropy = entropyString.chunked(8).map { it.toInt(2).toByte() }.toByteArray()
        
        val hash = sha256(entropy)
        val expectedChecksum = hash.toBitString().take(checksumString.length)
        if (checksumString != expectedChecksum) {
            throw IllegalArgumentException("Invalid mnemonic checksum")
        }
        
        return entropy
    }
    
    private fun sha256(data: ByteArray): ByteArray {
        return WatchOSCryptoKitSimple.sha256(data)
    }
    
    private fun ByteArray.toBitString(): String =
        joinToString("") { it.toUByte().toString(2).padStart(8, '0') }
}

object WatchOSCryptoUtils {
    fun isValidAddress(address: String, chainType: String): Boolean {
        return when (chainType) {
            "EVM" -> address.startsWith("0x") && address.length == 42
            "Bitcoin" -> address.length in 26..35
            else -> false
        }
    }
}
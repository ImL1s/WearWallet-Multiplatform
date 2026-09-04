package com.cbstudio.wearwallet.core.platform.ios

import com.cbstudio.wearwallet.core.security.CryptoProvider
import com.cbstudio.wearwallet.core.security.KeyPair
import com.cbstudio.wearwallet.core.security.ScopedMnemonic
import com.cbstudio.wearwallet.core.security.ScopedPassword
import kotlinx.cinterop.*
import platform.Foundation.*
import platform.Security.*
import kotlin.experimental.ExperimentalNativeApi

/**
 * iOS CryptoProvider 實現 (P1-6: 物理零化與契約更新)
 * 
 * All crypto operations require either a native delegate (injected from Swift layer)
 * or fail closed. No placeholder fallbacks to TrustWalletSwiftBridge mock.
 */
@OptIn(ExperimentalNativeApi::class, ExperimentalForeignApi::class)
class IosCryptoProvider : CryptoProvider {
    
    override suspend fun generateKeyPairFromMnemonic(
        mnemonic: CharArray,
        derivationPath: String,
        chainType: com.cbstudio.wearwallet.core.domain.model.ChainType
    ): KeyPair {
        val delegate = com.cbstudio.wearwallet.core.security.NativeCrypto.delegateOrNull
        if (delegate != null) {
            val mnemStr = String(mnemonic)
            val result = delegate.generateKeyPair(mnemStr, derivationPath, chainType)
            require(result.publicKey.isNotBlank()) { "Native delegate returned empty public key" }
            require(result.privateKeyBytes.isNotEmpty()) { "Native delegate returned empty private key" }
            return result
        }
        throw UnsupportedOperationException(
            "iOS key pair generation requires native crypto delegate. " +
            "TrustWalletSwiftBridge mock has been removed for security."
        )
    }
    
    override suspend fun generateKeyPairFromPrivateKey(privateKeyBytes: ByteArray): KeyPair {
        throw UnsupportedOperationException(
            "iOS generateKeyPairFromPrivateKey requires native crypto delegate. " +
            "No fallback to mock implementation."
        )
    }
    
    override suspend fun deriveAddress(publicKey: String): String {
        throw UnsupportedOperationException(
            "iOS deriveAddress requires native Keccak-256 implementation. " +
            "No fallback to mock implementation."
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
            require(address.isNotBlank()) { "Native delegate returned empty address for xpub" }
            return address
        }
        throw UnsupportedOperationException(
            "iOS xpub address derivation requires native crypto delegate. " +
            "No fallback to mock implementation."
        )
    }
    
    override suspend fun encrypt(data: ByteArray, password: CharArray): ByteArray {
        // Fail-closed: Single-byte XOR "encryption" has been removed.
        // iOS production encryption must use CryptoKit AES-GCM or Keychain.
        throw UnsupportedOperationException(
            "iOS encryption requires CryptoKit AES-GCM implementation. " +
            "Single-byte XOR placeholder has been removed for security."
        )
    }
    
    override suspend fun decrypt(encryptedData: ByteArray, password: CharArray): ByteArray {
        throw UnsupportedOperationException(
            "iOS decryption requires CryptoKit AES-GCM implementation. " +
            "Single-byte XOR placeholder has been removed for security."
        )
    }
    
    override suspend fun generateMnemonic(wordCount: Int): ScopedMnemonic {
        // Validate word count before any operation
        if (wordCount !in listOf(12, 15, 18, 21, 24)) {
            throw IllegalArgumentException(
                "Unsupported word count: $wordCount. Allowed: 12, 15, 18, 21, 24."
            )
        }
        throw UnsupportedOperationException(
            "iOS mnemonic generation requires native crypto delegate. " +
            "TrustWalletSwiftBridge mock mnemonic generator has been removed."
        )
    }
    
    override suspend fun validateMnemonic(mnemonic: CharArray): Boolean {
        // Basic BIP39 word list validation can be done locally via CommonCryptoProvider.
        // For now, fail-closed since TrustWalletSwiftBridge validation was a mock.
        throw UnsupportedOperationException(
            "iOS mnemonic validation requires native crypto delegate. " +
            "Mock validation has been removed."
        )
    }
}

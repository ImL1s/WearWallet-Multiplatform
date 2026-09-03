package com.cbstudio.wearwallet.core.security

import com.cbstudio.wearwallet.core.blockchain.crypto.CryptoSignature
import com.cbstudio.wearwallet.core.domain.model.ChainType
import io.github.iml1s.crypto.Keccak256
import io.github.iml1s.address.Base58

/**
 * 跨平台加密服務 (Test Fixture - Purged from production)
 * 處理多鏈交易簽名、地址生成和驗證
 */
class CryptoService {

    companion object {
        private const val TAG = "CryptoService"
        private const val PRIVATE_KEY_HEX_LENGTH = 64
    }

    fun signMessage(message: String, privateKey: String, chainType: ChainType): String {
        val cleanedKey = validateAndCleanPrivateKey(privateKey)

        return when (chainType) {
            ChainType.ETHEREUM, ChainType.BSC, ChainType.POLYGON,
            ChainType.ARBITRUM, ChainType.OPTIMISM, ChainType.AVALANCHE,
            ChainType.FANTOM, ChainType.CRONOS, ChainType.CRONOSZVM,
            ChainType.BASE, ChainType.ZKSYNC, ChainType.MOONBEAM,
            ChainType.GNOSIS, ChainType.CELO, ChainType.LINEA,
            ChainType.SEPOLIA, ChainType.GOERLI, ChainType.MUMBAI -> {
                CryptoSignature.signWithECDSA(message, cleanedKey)
            }
            ChainType.SOLANA, ChainType.APTOS, ChainType.SUI,
            ChainType.POLKADOT, ChainType.CARDANO, ChainType.NEAR -> {
                CryptoSignature.signWithEd25519(message, cleanedKey)
            }
            ChainType.TRON -> {
                CryptoSignature.signWithECDSA(message, cleanedKey)
            }
            ChainType.BITCOIN, ChainType.LITECOIN, ChainType.DOGECOIN,
            ChainType.BITCOIN_CASH -> {
                throw UnsupportedOperationException(
                    "UTXO 鏈 (${chainType.displayName}) 需要使用專用 UTXO Signer"
                )
            }
            ChainType.COSMOS, ChainType.TEZOS, ChainType.MONERO -> {
                throw UnsupportedOperationException(
                    "暫不支援的鏈類型: ${chainType.displayName}"
                )
            }
        }
    }

    fun signTransaction(
        transactionData: ByteArray,
        privateKey: String,
        chainType: ChainType,
        chainId: Int? = null
    ): String {
        val cleanedKey = validateAndCleanPrivateKey(privateKey)

        return when (chainType) {
            ChainType.ETHEREUM, ChainType.BSC, ChainType.POLYGON,
            ChainType.ARBITRUM, ChainType.OPTIMISM, ChainType.AVALANCHE,
            ChainType.FANTOM, ChainType.CRONOS, ChainType.CRONOSZVM,
            ChainType.BASE, ChainType.ZKSYNC, ChainType.MOONBEAM,
            ChainType.GNOSIS, ChainType.CELO, ChainType.LINEA,
            ChainType.SEPOLIA, ChainType.GOERLI, ChainType.MUMBAI -> {
                requireNotNull(chainId) { "EVM 鏈需要提供 chainId" }
                val signedTx = CryptoSignature.signEthereumTransaction(
                    transactionData, cleanedKey, chainId
                )
                signedTx.toHexString()
            }
            ChainType.SOLANA -> {
                val signedTx = CryptoSignature.signSolanaTransaction(
                    transactionData, cleanedKey
                )
                signedTx.toHexString()
            }
            else -> {
                throw UnsupportedOperationException(
                    "暫不支援 ${chainType.displayName} 的交易簽名"
                )
            }
        }
    }

    fun signWithEd25519(message: String, privateKey: String): String {
        val cleanedKey = validateAndCleanPrivateKey(privateKey)
        return CryptoSignature.signWithEd25519(message, cleanedKey)
    }

    fun signWithECDSA(message: String, privateKey: String): String {
        val cleanedKey = validateAndCleanPrivateKey(privateKey)
        return CryptoSignature.signWithECDSA(message, cleanedKey)
    }

    fun verifySignature(
        message: String,
        signature: String,
        publicKey: String,
        chainType: ChainType
    ): Boolean {
        val curveType = when (chainType) {
            ChainType.SOLANA, ChainType.APTOS, ChainType.SUI,
            ChainType.POLKADOT, ChainType.CARDANO, ChainType.NEAR -> "ED25519"
            ChainType.ETHEREUM, ChainType.BSC, ChainType.POLYGON,
            ChainType.ARBITRUM, ChainType.OPTIMISM, ChainType.AVALANCHE,
            ChainType.FANTOM, ChainType.CRONOS, ChainType.CRONOSZVM,
            ChainType.BASE, ChainType.ZKSYNC, ChainType.MOONBEAM,
            ChainType.GNOSIS, ChainType.CELO, ChainType.LINEA,
            ChainType.SEPOLIA, ChainType.GOERLI, ChainType.MUMBAI,
            ChainType.TRON -> "SECP256K1"
            else -> return false
        }

        return CryptoSignature.verifySignature(message, signature, publicKey, curveType)
    }

    fun derivePublicKey(privateKey: String): String {
        val cleanedKey = validateAndCleanPrivateKey(privateKey)
        return CryptoSignature.derivePublicKeyFromPrivateKey(cleanedKey)
    }

    fun deriveAddress(publicKey: String, chainType: ChainType): String {
        return when (chainType) {
            ChainType.ETHEREUM, ChainType.BSC, ChainType.POLYGON,
            ChainType.ARBITRUM, ChainType.OPTIMISM, ChainType.AVALANCHE,
            ChainType.FANTOM, ChainType.CRONOS, ChainType.CRONOSZVM,
            ChainType.BASE, ChainType.ZKSYNC, ChainType.MOONBEAM,
            ChainType.GNOSIS, ChainType.CELO, ChainType.LINEA,
            ChainType.SEPOLIA, ChainType.GOERLI, ChainType.MUMBAI -> {
                deriveEthereumAddress(publicKey)
            }
            ChainType.SOLANA -> {
                deriveSolanaAddress(publicKey)
            }
            ChainType.BITCOIN -> {
                deriveBitcoinAddress(publicKey)
            }
            else -> {
                throw UnsupportedOperationException(
                    "暫不支援 ${chainType.displayName} 的地址生成"
                )
            }
        }
    }

    fun recoverPublicKey(messageHash: String, signature: String): String? {
        if (signature.length != 130) {
            return null
        }

        val r = signature.substring(0, 64)
        val s = signature.substring(64, 128)
        val v = signature.substring(128, 130).toInt(16)

        val recoveryId = when {
            v >= 35 -> (v - 35) % 2
            v >= 27 -> v - 27
            else -> v
        }

        return CryptoSignature.recoverPublicKey(messageHash, r, s, recoveryId)
    }

    private fun validateAndCleanPrivateKey(privateKeyHex: String): String {
        require(privateKeyHex.isNotBlank()) { "私鑰為空" }

        val cleaned = privateKeyHex.removePrefix("0x").trim()

        require(cleaned.length == PRIVATE_KEY_HEX_LENGTH) {
            "私鑰長度無效: ${cleaned.length}，預期為 $PRIVATE_KEY_HEX_LENGTH 個字元"
        }

        require(cleaned.matches(Regex("^[0-9a-fA-F]+$"))) {
            "私鑰包含無效的十六進制字元"
        }

        return cleaned
    }

    private fun deriveEthereumAddress(publicKey: String): String {
        val pubKeyBytes = publicKey.hexToByteArray()

        val uncompressed = if (pubKeyBytes[0] == 0x04.toByte()) {
            pubKeyBytes.sliceArray(1 until pubKeyBytes.size)
        } else {
            pubKeyBytes
        }

        val hash = Keccak256.hash(uncompressed)
        val address = hash.sliceArray(12 until 32)

        return applyEIP55Checksum("0x" + address.toHexString())
    }

    private fun deriveSolanaAddress(publicKey: String): String {
        val pubKeyBytes = publicKey.hexToByteArray()
        return Base58.encode(pubKeyBytes)
    }

    private fun deriveBitcoinAddress(publicKey: String): String {
        val pubKeyBytes = publicKey.hexToByteArray()

        val sha256 = CryptoUtils.sha256(pubKeyBytes)
        val hash160 = CryptoUtils.sha256(sha256).sliceArray(0 until 20)
        val versionedHash = byteArrayOf(0x00) + hash160
        val checksum = CryptoUtils.sha256(CryptoUtils.sha256(versionedHash)).sliceArray(0 until 4)
        val addressBytes = versionedHash + checksum

        return Base58.encode(addressBytes)
    }

    private fun applyEIP55Checksum(address: String): String {
        val lowercaseAddress = address.lowercase().removePrefix("0x")
        val hash = Keccak256.hash(lowercaseAddress.encodeToByteArray()).toHexString()

        val checksumAddress = buildString {
            append("0x")
            lowercaseAddress.forEachIndexed { i, char ->
                if (char in '0'..'9') {
                    append(char)
                } else {
                    val hashChar = hash[i].toString().toInt(16)
                    append(if (hashChar >= 8) char.uppercase() else char)
                }
            }
        }

        return checksumAddress
    }

    private fun ByteArray.toHexString(): String {
        return joinToString("") { byte ->
            byte.toUByte().toString(16).padStart(2, '0')
        }
    }

    private fun String.hexToByteArray(): ByteArray {
        val hex = removePrefix("0x")
        return ByteArray(hex.length / 2) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}

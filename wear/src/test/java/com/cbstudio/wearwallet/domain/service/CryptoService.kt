package com.cbstudio.wearwallet.domain.service

import com.cbstudio.wearwallet.core.blockchain.crypto.CryptoSignature
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.domain.model.OfflineTransaction
import com.cbstudio.wearwallet.shared.utils.Logger
import java.security.MessageDigest
import org.koin.core.component.KoinComponent
import javax.inject.Singleton
import kotlin.experimental.and

import com.cbstudio.wearwallet.core.domain.model.context.ChainExecutionContextRegistry
import com.cbstudio.wearwallet.core.security.CapabilityGate
import com.cbstudio.wearwallet.core.security.CapabilityRequest
import com.cbstudio.wearwallet.core.security.Operation
import com.cbstudio.wearwallet.core.security.Platform
import com.cbstudio.wearwallet.core.security.BuildType
import com.cbstudio.wearwallet.core.domain.model.quantities.EvmEnvelope
import com.cbstudio.wearwallet.core.security.SignerImplementation
import com.cbstudio.wearwallet.core.security.WalletType
import com.cbstudio.wearwallet.core.security.BackendIdentity
import com.cbstudio.wearwallet.core.security.ReleaseProductionCapabilityGate
import com.cbstudio.wearwallet.core.domain.usecase.transaction.TypedUnsupportedTransactionException

/**
 * 加密服務 (Test fixture - purged from production release)
 * 處理離線交易的簽名和驗證
 */
class CryptoService(
    private val capabilityGate: CapabilityGate? = null
) : KoinComponent {

    companion object {
        private const val TAG = "CryptoService"
    }

    fun signTransaction(transaction: OfflineTransaction, privateKey: String): String {
        return try {
            val chainId = transaction.payload.chainId
                ?: throw TypedUnsupportedTransactionException("Transaction chainId is null. Fail-closed: refusing to default to Ethereum.")
            val chainType = parseChainType(chainId)

            if (capabilityGate != null) {
                val ctx = ChainExecutionContextRegistry.resolve(chainType)
                val req = CapabilityRequest.createForTesting(
                    operation = Operation.SOFTWARE_SIGN,
                    chain = ctx.multiChainType,
                    network = ctx.capabilityNetwork,
                    platform = Platform.WEAR_OS,
                    buildType = BuildType.RELEASE,
                    envelopeType = EvmEnvelope.LEGACY,
                    signerImplementation = SignerImplementation.SOFTWARE_LOCAL,
                    walletType = WalletType.SOFTWARE_MNEMONIC,
                    backendIdentity = BackendIdentity.PRODUCTION_V1,
                    backendAvailable = true,
                    backendVersion = "1.0.0",
                    smokeVectorVerified = true
                )
                if (!capabilityGate.verifyCapability(req)) {
                    throw TypedUnsupportedTransactionException("Production capability gate fail-closed: ${chainType.displayName} signing is disabled")
                }
            }

            val cleanedKey = validateAndCleanPrivateKey(privateKey)
            val message = buildSignatureData(transaction)

            val signature = when (chainType) {
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
                        "暫不支援的鏈類型: ${chainType.displayName} (${chainType.name})"
                    )
                }
            }

            Logger.d(TAG, "Transaction signed successfully for chain: ${chainType.displayName}")
            signature
        } catch (e: IllegalArgumentException) {
            Logger.e(TAG, "Invalid private key format", e)
            throw e
        } catch (e: UnsupportedOperationException) {
            Logger.e(TAG, "Unsupported chain type", e)
            throw e
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to sign transaction", e)
            throw IllegalStateException("簽名失敗: ${e.message}", e)
        }
    }

    private fun parseChainType(chainId: String): ChainType {
        val normalized = chainId.uppercase().trim()
            .replace("-", "")
            .replace("_", "")
            .replace(" ", "")

        return when (normalized) {
            "ETHEREUM", "ETH" -> ChainType.ETHEREUM
            "SEPOLIA" -> ChainType.SEPOLIA
            "GOERLI" -> ChainType.GOERLI
            "BSC", "BNB", "BINANCE", "BINANCESMARTCHAIN" -> ChainType.BSC
            "POLYGON", "MATIC" -> ChainType.POLYGON
            "MUMBAI" -> ChainType.MUMBAI
            "ARBITRUM", "ARB" -> ChainType.ARBITRUM
            "OPTIMISM", "OP" -> ChainType.OPTIMISM
            "AVALANCHE", "AVAX" -> ChainType.AVALANCHE
            "FANTOM", "FTM" -> ChainType.FANTOM
            "CRONOS", "CRO" -> ChainType.CRONOS
            "CRONOSZKEVM", "CRONOSZVM" -> ChainType.CRONOSZVM
            "BASE" -> ChainType.BASE
            "ZKSYNC", "ZK" -> ChainType.ZKSYNC
            "MOONBEAM", "GLMR" -> ChainType.MOONBEAM
            "GNOSIS", "XDAI" -> ChainType.GNOSIS
            "CELO" -> ChainType.CELO
            "LINEA" -> ChainType.LINEA
            "BITCOIN", "BTC" -> ChainType.BITCOIN
            "LITECOIN", "LTC" -> ChainType.LITECOIN
            "DOGECOIN", "DOGE" -> ChainType.DOGECOIN
            "BITCOINCASH", "BCH" -> ChainType.BITCOIN_CASH
            "SOLANA", "SOL" -> ChainType.SOLANA
            "APTOS", "APT" -> ChainType.APTOS
            "SUI" -> ChainType.SUI
            "COSMOS", "ATOM" -> ChainType.COSMOS
            "POLKADOT", "DOT" -> ChainType.POLKADOT
            "CARDANO", "ADA" -> ChainType.CARDANO
            "NEAR" -> ChainType.NEAR
            "TRON", "TRX" -> ChainType.TRON
            "TEZOS", "XTZ" -> ChainType.TEZOS
            "MONERO", "XMR" -> ChainType.MONERO
            else -> throw IllegalArgumentException("無法識別的鏈類型: $chainId")
        }
    }

    private fun validateAndCleanPrivateKey(privateKeyHex: String): String {
        if (privateKeyHex.isBlank()) {
            throw IllegalArgumentException("私鑰為空")
        }
        val cleaned = privateKeyHex.removePrefix("0x").trim()
        if (cleaned.length != 64) {
            throw IllegalArgumentException("私鑰長度無效: ${cleaned.length}，預期為 64 個字元")
        }
        if (!cleaned.matches(Regex("^[0-9a-fA-F]+$"))) {
            throw IllegalArgumentException("私鑰包含無效的十六進制字元")
        }
        return cleaned
    }

    fun verifySignature(
        transaction: OfflineTransaction,
        signature: String,
        publicKey: String
    ): Boolean {
        return try {
            val message = buildSignatureData(transaction)
            val chainId = transaction.payload.chainId
                ?: throw TypedUnsupportedTransactionException("Transaction chainId is null. Fail-closed: refusing to default to Ethereum.")
            val chainType = parseChainType(chainId)

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
                else -> {
                    Logger.w(TAG, "無法驗證不支援的鏈類型: ${chainType.displayName}")
                    return false
                }
            }

            val isValid = CryptoSignature.verifySignature(
                message = message,
                signature = signature,
                publicKey = publicKey,
                curveType = curveType
            )

            if (isValid) {
                Logger.d(TAG, "簽名驗證成功 for chain: ${chainType.displayName}")
            } else {
                Logger.w(TAG, "簽名驗證失敗 for chain: ${chainType.displayName}")
            }
            isValid
        } catch (e: Exception) {
            Logger.e(TAG, "簽名驗證過程發生錯誤", e)
            false
        }
    }

    private fun buildSignatureData(transaction: OfflineTransaction): String {
        return buildString {
            append(transaction.id)
            append("|")
            append(transaction.type.name)
            append("|")
            append(transaction.payload.fromAddress ?: "")
            append("|")
            append(transaction.payload.toAddress ?: "")
            append("|")
            append(transaction.payload.amount ?: "")
            append("|")
            append(transaction.payload.token ?: "")
            append("|")
            append(transaction.payload.chainId ?: "")
            append("|")
            append(transaction.payload.nonce ?: 0)
            append("|")
            append(transaction.payload.gasPrice ?: "")
            append("|")
            append(transaction.payload.gasLimit ?: "")
            append("|")
            append(transaction.payload.data ?: "")
            append("|")
            append(transaction.metadata.timestamp)
        }
    }

    private fun hashData(data: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(data.toByteArray())
            hash.joinToString("") { byte ->
                "%02x".format(byte and 0xFF.toByte())
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to hash data", e)
            ""
        }
    }

    fun generateTransactionId(transaction: OfflineTransaction): String {
        val data = buildString {
            append(transaction.payload.fromAddress ?: "")
            append(transaction.payload.toAddress ?: "")
            append(transaction.payload.amount ?: "")
            append(transaction.metadata.timestamp)
            append(System.nanoTime())
        }
        return hashData(data).take(16)
    }
}

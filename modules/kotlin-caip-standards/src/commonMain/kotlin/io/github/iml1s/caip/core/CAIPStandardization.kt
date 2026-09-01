package io.github.iml1s.caip.core

import io.github.iml1s.caip.model.*

/**
 * CAIP (Chain Agnostic Improvement Proposals) Standardization Implementation
 *
 * Implements industry-standard cross-chain identifiers providing unified
 * representation for addresses and assets.
 *
 * Standards Reference:
 * - CAIP-2: Blockchain ID Specification
 * - CAIP-10: Account ID Specification
 * - CAIP-19: Asset Type and Asset ID Specification
 * - CAIP-196: Chain Agnostic Asset ID Specification
 *
 * @see <a href="https://github.com/ChainAgnostic/CAIPs">CAIP Specifications</a>
 */

/**
 * CAIP Transaction Request
 *
 * Standardized transaction request using CAIP addresses and assets.
 */
data class CAIPTransactionRequest(
    val fromAddress: CAIPAddress,
    val toAddress: CAIPAddress,
    val asset: CAIPAsset,
    val amount: String,
    val memo: String? = null,
    val gasLimit: String? = null,
    val gasPrice: String? = null,
    val metadata: Map<String, Any> = emptyMap()
) {
    /**
     * Validate the transaction request
     */
    fun validate(): CAIPResult<Boolean> {
        return try {
            // Validate addresses
            val fromValidation = fromAddress.validate()
            if (fromValidation.isFailure()) {
                return fromValidation
            }

            val toValidation = toAddress.validate()
            if (toValidation.isFailure()) {
                return toValidation
            }

            // Validate same-chain transaction
            if (fromAddress.chainId != toAddress.chainId) {
                return CAIPResult.Failure(
                    IllegalArgumentException("Cross-chain transfers not supported in single transaction")
                )
            }

            // Validate asset belongs to the same chain
            if (asset.chainId != fromAddress.chainId) {
                return CAIPResult.Failure(
                    IllegalArgumentException("Asset chain does not match transaction chain")
                )
            }

            // Validate amount format
            val amountValue = amount.toDoubleOrNull()
            if (amountValue == null || amountValue <= 0) {
                return CAIPResult.Failure(
                    IllegalArgumentException("Invalid amount: $amount")
                )
            }

            CAIPResult.Success(true)
        } catch (e: Exception) {
            CAIPResult.Failure(e)
        }
    }

    /**
     * Get a human-readable summary of the transaction
     */
    fun getSummary(): String {
        return buildString {
            append("Transfer ")
            append(amount)
            append(" ")
            append(asset.getSymbol())
            append(" from ")
            append(fromAddress.getDisplayAddress())
            append(" to ")
            append(toAddress.getDisplayAddress())
            if (memo != null) {
                append(" (memo: ")
                append(memo)
                append(")")
            }
        }
    }
}

/**
 * CAIP Transaction Result
 */
data class CAIPTransactionResult(
    val transactionHash: String,
    val status: CAIPTransactionStatus,
    val chainId: CAIPChainID,
    val blockNumber: Long? = null,
    val gasUsed: String? = null,
    val fee: String? = null,
    val timestamp: Long = currentTimeMillis(),
    val explorerUrl: String? = null
)

/**
 * CAIP Service - Provides standardized operations interface
 */
class CAIPService {

    /**
     * Parse any CAIP string format
     *
     * Automatically detects the format (CAIP-2, CAIP-10, or CAIP-19)
     * and returns the appropriate parsed object.
     */
    fun parseCAIPString(caipString: String): CAIPResult<Any> {
        return when {
            caipString.count { it == ':' } == 1 && !caipString.contains('/') -> {
                // CAIP-2 format (namespace:reference)
                CAIPChainID.parse(caipString)
            }
            caipString.count { it == ':' } == 2 && !caipString.contains('/') -> {
                // CAIP-10 format (namespace:chain_id:account_address)
                CAIPAddress.parse(caipString)
            }
            caipString.contains('/') -> {
                // CAIP-19 format (namespace:chain_id/asset_namespace:asset_reference)
                CAIPAsset.parse(caipString)
            }
            else -> {
                CAIPResult.Failure(IllegalArgumentException("Unknown CAIP format: $caipString"))
            }
        }
    }

    /**
     * Validate a CAIP format string
     */
    fun validateCAIPString(caipString: String): CAIPResult<Boolean> {
        return when (val result = parseCAIPString(caipString)) {
            is CAIPResult.Success -> CAIPResult.Success(true)
            is CAIPResult.Failure -> CAIPResult.Success(false)
            is CAIPResult.Loading -> CAIPResult.Success(false)
        }
    }

    /**
     * Convert a legacy address to CAIP format
     */
    fun convertLegacyAddress(
        address: String,
        chainType: CAIPChainType,
        network: String = "mainnet"
    ): CAIPAddress {
        return CAIPAddress.fromAddress(address, chainType, network)
    }

    /**
     * Get the list of supported namespaces
     */
    fun getSupportedNamespaces(): List<String> {
        return listOf(
            "eip155",    // Ethereum compatible chains
            "cosmos",    // Cosmos ecosystem
            "solana",    // Solana
            "bip122",    // Bitcoin compatible chains
            "polkadot",  // Polkadot
            "cardano",   // Cardano
            "tron",      // TRON
            "monero"     // Monero
        )
    }

    /**
     * Get the list of supported asset namespaces
     */
    fun getSupportedAssetNamespaces(): List<String> {
        return listOf(
            "slip44",    // Native tokens (SLIP-44 coin types)
            "erc20",     // ERC-20 tokens
            "erc721",    // ERC-721 NFTs
            "erc1155",   // ERC-1155 multi-tokens
            "spl-token", // Solana SPL tokens
            "spl-nft",   // Solana SPL NFTs
            "cw20",      // CosmWasm CW-20 tokens
            "native"     // Chain native assets
        )
    }

    /**
     * Get the block explorer URL for a transaction
     */
    fun getExplorerUrl(chainId: CAIPChainID, transactionHash: String): String? {
        return when (chainId.namespace) {
            "eip155" -> getEVMExplorerUrl(chainId.reference, transactionHash)
            "solana" -> getSolanaExplorerUrl(chainId.reference, transactionHash)
            "cosmos" -> "https://www.mintscan.io/cosmos/txs/$transactionHash"
            "bip122" -> "https://blockstream.info/tx/$transactionHash"
            "polkadot" -> "https://polkadot.subscan.io/extrinsic/$transactionHash"
            "tron" -> "https://tronscan.org/#/transaction/$transactionHash"
            "cardano" -> "https://cardanoscan.io/transaction/$transactionHash"
            else -> null
        }
    }

    /**
     * Get the block explorer URL for an address
     */
    fun getAddressExplorerUrl(caipAddress: CAIPAddress): String? {
        val chainId = caipAddress.chainId
        val address = caipAddress.address

        return when (chainId.namespace) {
            "eip155" -> getEVMAddressExplorerUrl(chainId.reference, address)
            "solana" -> getSolanaAddressExplorerUrl(chainId.reference, address)
            "cosmos" -> "https://www.mintscan.io/cosmos/account/$address"
            "bip122" -> "https://blockstream.info/address/$address"
            "polkadot" -> "https://polkadot.subscan.io/account/$address"
            "tron" -> "https://tronscan.org/#/address/$address"
            "cardano" -> "https://cardanoscan.io/address/$address"
            else -> null
        }
    }

    private fun getEVMExplorerUrl(chainReference: String, transactionHash: String): String? {
        val baseUrl = when (chainReference) {
            "1" -> "https://etherscan.io"
            "137" -> "https://polygonscan.com"
            "56" -> "https://bscscan.com"
            "25" -> "https://cronoscan.com"
            "42161" -> "https://arbiscan.io"
            "10" -> "https://optimistic.etherscan.io"
            "43114" -> "https://snowtrace.io"
            "8453" -> "https://basescan.org"
            "250" -> "https://ftmscan.com"
            "42220" -> "https://celoscan.io"
            "1284" -> "https://moonscan.io"
            else -> return null
        }
        return "$baseUrl/tx/$transactionHash"
    }

    private fun getEVMAddressExplorerUrl(chainReference: String, address: String): String? {
        val baseUrl = when (chainReference) {
            "1" -> "https://etherscan.io"
            "137" -> "https://polygonscan.com"
            "56" -> "https://bscscan.com"
            "25" -> "https://cronoscan.com"
            "42161" -> "https://arbiscan.io"
            "10" -> "https://optimistic.etherscan.io"
            "43114" -> "https://snowtrace.io"
            "8453" -> "https://basescan.org"
            "250" -> "https://ftmscan.com"
            "42220" -> "https://celoscan.io"
            "1284" -> "https://moonscan.io"
            else -> return null
        }
        return "$baseUrl/address/$address"
    }

    private fun getSolanaExplorerUrl(chainReference: String, transactionHash: String): String {
        val cluster = when (chainReference) {
            "5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp" -> "" // mainnet
            "4uhcVJyU9pJkvQyS88uRDiswHXSCkY3z" -> "?cluster=testnet"
            "EtWTRABZaYq6iMfeYKouRu166VU2xqa1" -> "?cluster=devnet"
            else -> ""
        }
        return "https://solscan.io/tx/$transactionHash$cluster"
    }

    private fun getSolanaAddressExplorerUrl(chainReference: String, address: String): String {
        val cluster = when (chainReference) {
            "5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp" -> "" // mainnet
            "4uhcVJyU9pJkvQyS88uRDiswHXSCkY3z" -> "?cluster=testnet"
            "EtWTRABZaYq6iMfeYKouRu166VU2xqa1" -> "?cluster=devnet"
            else -> ""
        }
        return "https://solscan.io/account/$address$cluster"
    }
}

/**
 * CAIP Utilities
 */
object CAIPUtils {

    /**
     * Batch convert addresses to CAIP format
     */
    fun convertAddressesToCAIP(
        addresses: List<String>,
        chainType: CAIPChainType,
        network: String = "mainnet"
    ): List<CAIPAddress> {
        return addresses.map { address ->
            CAIPAddress.fromAddress(address, chainType, network)
        }
    }

    /**
     * Batch validate CAIP addresses
     */
    fun validateCAIPAddresses(addresses: List<CAIPAddress>): Map<CAIPAddress, CAIPResult<Boolean>> {
        return addresses.associateWith { address ->
            address.validate()
        }
    }

    /**
     * Parse flexible address format (supports both CAIP and legacy formats)
     */
    fun parseFlexibleAddress(
        addressString: String,
        defaultChainType: CAIPChainType? = null
    ): CAIPResult<CAIPAddress> {
        return if (addressString.contains(':') && addressString.count { it == ':' } >= 2) {
            // Try to parse as CAIP format
            CAIPAddress.parse(addressString)
        } else if (defaultChainType != null) {
            // Treat as legacy address
            CAIPResult.Success(CAIPAddress.fromAddress(addressString, defaultChainType))
        } else {
            CAIPResult.Failure(
                IllegalArgumentException("Cannot parse address without chain context: $addressString")
            )
        }
    }

    /**
     * Generate explorer link for a transaction result
     */
    fun generateExplorerLink(result: CAIPTransactionResult): String? {
        return CAIPService().getExplorerUrl(result.chainId, result.transactionHash)
    }

    /**
     * Check if two CAIP addresses are on the same chain
     */
    fun areSameChain(address1: CAIPAddress, address2: CAIPAddress): Boolean {
        return address1.chainId == address2.chainId
    }

    /**
     * Check if a CAIP address matches a chain type
     */
    fun matchesChainType(address: CAIPAddress, chainType: CAIPChainType): Boolean {
        return address.chainId.namespace == chainType.namespace
    }
}

// currentTimeMillis() is defined in Platform.kt

package io.github.iml1s.caip.model

/**
 * CAIP-2: Blockchain ID Specification
 *
 * Format: namespace:reference
 *
 * Examples:
 * - eip155:1 (Ethereum mainnet)
 * - cosmos:cosmoshub-4 (Cosmos Hub)
 * - bip122:000000000019d6689c085ae165831e93 (Bitcoin mainnet)
 * - solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp (Solana mainnet)
 *
 * @see <a href="https://github.com/ChainAgnostic/CAIPs/blob/main/CAIPs/caip-2.md">CAIP-2 Specification</a>
 */
data class CAIPChainID(
    val namespace: String,
    val reference: String
) {
    /**
     * Convert to CAIP-2 string format
     */
    fun toCAIPString(): String = "$namespace:$reference"

    /**
     * Get a human-readable description of the chain
     */
    fun getDescription(): String {
        return when (namespace) {
            "eip155" -> when (reference) {
                "1" -> "Ethereum Mainnet"
                "137" -> "Polygon Mainnet"
                "56" -> "BSC Mainnet"
                "25" -> "Cronos Mainnet"
                "42161" -> "Arbitrum One"
                "10" -> "Optimism"
                "43114" -> "Avalanche C-Chain"
                "8453" -> "Base"
                "250" -> "Fantom Opera"
                else -> "EVM Chain $reference"
            }
            "cosmos" -> "Cosmos Chain $reference"
            "bip122" -> when {
                reference.startsWith("000000000019d6689c") -> "Bitcoin Mainnet"
                reference.startsWith("0f9188f13cb7b2c71f") -> "Bitcoin Testnet"
                else -> "Bitcoin Chain $reference"
            }
            "solana" -> when (reference) {
                "5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp" -> "Solana Mainnet"
                "4uhcVJyU9pJkvQyS88uRDiswHXSCkY3z" -> "Solana Testnet"
                "EtWTRABZaYq6iMfeYKouRu166VU2xqa1" -> "Solana Devnet"
                else -> "Solana $reference"
            }
            "polkadot" -> "Polkadot $reference"
            "cardano" -> "Cardano $reference"
            "tron" -> "TRON $reference"
            "monero" -> "Monero $reference"
            else -> "Chain $namespace:$reference"
        }
    }

    companion object {
        /**
         * Parse from CAIP-2 string format
         */
        fun parse(caipString: String): CAIPResult<CAIPChainID> {
            val parts = caipString.split(":")
            return if (parts.size == 2 && parts[0].isNotEmpty() && parts[1].isNotEmpty()) {
                CAIPResult.Success(CAIPChainID(parts[0], parts[1]))
            } else {
                CAIPResult.Failure(IllegalArgumentException("Invalid CAIP-2 format: $caipString"))
            }
        }

        /**
         * Create from CAIPChainType
         */
        fun fromChainType(chainType: CAIPChainType, network: String = "mainnet"): CAIPChainID {
            return when (chainType) {
                CAIPChainType.ETHEREUM -> CAIPChainID("eip155", "1")
                CAIPChainType.BSC -> CAIPChainID("eip155", "56")
                CAIPChainType.POLYGON -> CAIPChainID("eip155", "137")
                CAIPChainType.AVALANCHE -> CAIPChainID("eip155", "43114")
                CAIPChainType.ARBITRUM -> CAIPChainID("eip155", "42161")
                CAIPChainType.OPTIMISM -> CAIPChainID("eip155", "10")
                CAIPChainType.CRONOS -> CAIPChainID("eip155", "25")
                CAIPChainType.BASE -> CAIPChainID("eip155", "8453")
                CAIPChainType.FANTOM -> CAIPChainID("eip155", "250")
                CAIPChainType.CELO -> CAIPChainID("eip155", "42220")
                CAIPChainType.MOONBEAM -> CAIPChainID("eip155", "1284")
                CAIPChainType.SOLANA -> CAIPChainID("solana", when (network) {
                    "mainnet" -> "5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp"
                    "testnet" -> "4uhcVJyU9pJkvQyS88uRDiswHXSCkY3z"
                    "devnet" -> "EtWTRABZaYq6iMfeYKouRu166VU2xqa1"
                    else -> "5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp"
                })
                CAIPChainType.BITCOIN -> CAIPChainID("bip122", "000000000019d6689c085ae165831e93")
                CAIPChainType.BITCOIN_CASH -> CAIPChainID("bip122", "000000000019d6689c085ae165831e93")
                CAIPChainType.LITECOIN -> CAIPChainID("bip122", "12a765e31ffd4059bada1e25190f6e98")
                CAIPChainType.DOGECOIN -> CAIPChainID("bip122", "1a91e3dace36e2be3bf030a65679fe821aa1d6ef92e7c9902eb318182c355691")
                CAIPChainType.POLKADOT -> CAIPChainID("polkadot", "91b171bb158e2d3848fa23a9f1c25182")
                CAIPChainType.CARDANO -> CAIPChainID("cardano", "764824073")
                CAIPChainType.TRON -> CAIPChainID("tron", "0x2b6653dc")
                CAIPChainType.MONERO -> CAIPChainID("monero", "mainnet")
            }
        }

        // Well-known chain IDs
        val ETHEREUM_MAINNET = CAIPChainID("eip155", "1")
        val POLYGON_MAINNET = CAIPChainID("eip155", "137")
        val BSC_MAINNET = CAIPChainID("eip155", "56")
        val SOLANA_MAINNET = CAIPChainID("solana", "5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp")
        val SOLANA_DEVNET = CAIPChainID("solana", "EtWTRABZaYq6iMfeYKouRu166VU2xqa1")
        val BITCOIN_MAINNET = CAIPChainID("bip122", "000000000019d6689c085ae165831e93")
    }
}

/**
 * CAIP-10: Account ID Specification
 *
 * Format: namespace:chain_id:account_address
 *
 * Examples:
 * - eip155:1:0xab16a96d359ec26a11e2c2b3d8f8b8942d5bfcdb (Ethereum)
 * - cosmos:cosmoshub-4:cosmos1t2uflqwqe0fsj0shcfkrvpukewcw40yjj6hdc0 (Cosmos)
 * - solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp:4Qkev8aNZcqFNSRhQzwyLMFSsi94jHqE8WNVTJzTP99F (Solana)
 *
 * @see <a href="https://github.com/ChainAgnostic/CAIPs/blob/main/CAIPs/caip-10.md">CAIP-10 Specification</a>
 */
data class CAIPAddress(
    val chainId: CAIPChainID,
    val address: String
) {
    /**
     * Convert to CAIP-10 string format
     */
    fun toCAIPString(): String = "${chainId.toCAIPString()}:$address"

    /**
     * Get a shortened display version of the address
     */
    fun getDisplayAddress(prefixLength: Int = 6, suffixLength: Int = 4): String {
        return if (address.length > prefixLength + suffixLength + 3) {
            "${address.take(prefixLength)}...${address.takeLast(suffixLength)}"
        } else {
            address
        }
    }

    /**
     * Validate the address format based on the chain namespace
     */
    fun validate(): CAIPResult<Boolean> {
        return try {
            when (chainId.namespace) {
                "eip155" -> validateEthereumAddress(address)
                "cosmos" -> validateCosmosAddress(address)
                "solana" -> validateSolanaAddress(address)
                "bip122" -> validateBitcoinAddress(address)
                "polkadot" -> validatePolkadotAddress(address)
                else -> CAIPResult.Success(true) // Unknown chains pass by default
            }
        } catch (e: Exception) {
            CAIPResult.Failure(e)
        }
    }

    companion object {
        /**
         * Parse from CAIP-10 string format
         */
        fun parse(caipString: String): CAIPResult<CAIPAddress> {
            val parts = caipString.split(":")
            return if (parts.size == 3) {
                val chainId = CAIPChainID(parts[0], parts[1])
                CAIPResult.Success(CAIPAddress(chainId, parts[2]))
            } else {
                CAIPResult.Failure(IllegalArgumentException("Invalid CAIP-10 format: $caipString"))
            }
        }

        /**
         * Create from a plain address and chain type
         */
        fun fromAddress(
            address: String,
            chainType: CAIPChainType,
            network: String = "mainnet"
        ): CAIPAddress {
            val chainId = CAIPChainID.fromChainType(chainType, network)
            return CAIPAddress(chainId, address)
        }
    }

    private fun validateEthereumAddress(address: String): CAIPResult<Boolean> {
        val isValid = address.startsWith("0x") &&
                address.length == 42 &&
                address.drop(2).all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
        return CAIPResult.Success(isValid)
    }

    private fun validateCosmosAddress(address: String): CAIPResult<Boolean> {
        val isValid = address.startsWith("cosmos") && address.length == 45
        return CAIPResult.Success(isValid)
    }

    private fun validateSolanaAddress(address: String): CAIPResult<Boolean> {
        val isValid = address.length in 32..44 && address.matches(Regex("[1-9A-HJ-NP-Za-km-z]+"))
        return CAIPResult.Success(isValid)
    }

    private fun validateBitcoinAddress(address: String): CAIPResult<Boolean> {
        val isValid = (address.startsWith("1") || address.startsWith("3") ||
                address.startsWith("bc1")) && address.length in 26..62
        return CAIPResult.Success(isValid)
    }

    private fun validatePolkadotAddress(address: String): CAIPResult<Boolean> {
        val isValid = address.length >= 47
        return CAIPResult.Success(isValid)
    }
}

/**
 * CAIP-19: Asset Type and Asset ID Specification
 *
 * Format: namespace:chain_id/asset_namespace:asset_reference
 *
 * Examples:
 * - eip155:1/slip44:60 (ETH on Ethereum)
 * - eip155:1/erc20:0xa0b86a33e6776bb5b4e8a8e7b4a9b23ef4b50c6b (ERC20 token)
 * - eip155:1/erc721:0x06012c8cf97bead5deae237070f9587f8e7a266d/771769 (CryptoKitty NFT)
 * - cosmos:cosmoshub-4/slip44:118 (ATOM)
 * - solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp/slip44:501 (SOL)
 *
 * @see <a href="https://github.com/ChainAgnostic/CAIPs/blob/main/CAIPs/caip-19.md">CAIP-19 Specification</a>
 */
data class CAIPAsset(
    val chainId: CAIPChainID,
    val assetNamespace: String,
    val assetReference: String
) {
    /**
     * Convert to CAIP-19 string format
     */
    fun toCAIPString(): String = "${chainId.toCAIPString()}/$assetNamespace:$assetReference"

    /**
     * Check if this is a native token (identified by slip44)
     */
    fun isNativeToken(): Boolean = assetNamespace == "slip44"

    /**
     * Check if this is an ERC20 token
     */
    fun isERC20Token(): Boolean = assetNamespace == "erc20"

    /**
     * Check if this is an NFT
     */
    fun isNFT(): Boolean = assetNamespace in listOf("erc721", "erc1155", "spl-nft")

    /**
     * Check if this is an SPL token (Solana)
     */
    fun isSPLToken(): Boolean = assetNamespace == "spl-token"

    /**
     * Get the asset symbol (requires additional data source for tokens)
     */
    fun getSymbol(): String {
        return when {
            isNativeToken() -> when (chainId.namespace) {
                "eip155" -> when (chainId.reference) {
                    "1" -> "ETH"
                    "137" -> "MATIC"
                    "56" -> "BNB"
                    "25" -> "CRO"
                    "42161" -> "ETH"
                    "10" -> "ETH"
                    "43114" -> "AVAX"
                    "8453" -> "ETH"
                    "250" -> "FTM"
                    else -> "ETH"
                }
                "cosmos" -> "ATOM"
                "solana" -> "SOL"
                "bip122" -> "BTC"
                "polkadot" -> "DOT"
                "cardano" -> "ADA"
                "tron" -> "TRX"
                "monero" -> "XMR"
                else -> "UNKNOWN"
            }
            else -> extractTokenSymbol() ?: "TOKEN"
        }
    }

    private fun extractTokenSymbol(): String? {
        return when (assetNamespace) {
            "erc20" -> "ERC20"
            "erc721" -> "NFT"
            "erc1155" -> "NFT"
            "spl-token" -> "SPL"
            "spl-nft" -> "NFT"
            "cw20" -> "CW20"
            else -> null
        }
    }

    companion object {
        /**
         * Parse from CAIP-19 string format
         */
        fun parse(caipString: String): CAIPResult<CAIPAsset> {
            val mainParts = caipString.split("/")
            if (mainParts.size != 2) {
                return CAIPResult.Failure(IllegalArgumentException("Invalid CAIP-19 format: $caipString"))
            }

            val chainIdResult = CAIPChainID.parse(mainParts[0])
            if (chainIdResult.isFailure()) {
                return CAIPResult.Failure((chainIdResult as CAIPResult.Failure).exception)
            }

            val assetParts = mainParts[1].split(":", limit = 2)
            if (assetParts.size != 2) {
                return CAIPResult.Failure(IllegalArgumentException("Invalid asset format in CAIP-19: $caipString"))
            }

            return CAIPResult.Success(
                CAIPAsset(
                    chainId = chainIdResult.getOrThrow(),
                    assetNamespace = assetParts[0],
                    assetReference = assetParts[1]
                )
            )
        }

        /**
         * Create a native token asset
         */
        fun createNativeAsset(chainType: CAIPChainType, network: String = "mainnet"): CAIPAsset {
            val chainId = CAIPChainID.fromChainType(chainType, network)
            return CAIPAsset(
                chainId = chainId,
                assetNamespace = "slip44",
                assetReference = chainType.slip44CoinType.toString()
            )
        }

        /**
         * Create an ERC20 token asset
         */
        fun createERC20Asset(
            contractAddress: String,
            chainType: CAIPChainType,
            network: String = "mainnet"
        ): CAIPAsset {
            val chainId = CAIPChainID.fromChainType(chainType, network)
            return CAIPAsset(
                chainId = chainId,
                assetNamespace = "erc20",
                assetReference = contractAddress.lowercase()
            )
        }

        /**
         * Create an NFT asset
         */
        fun createNFTAsset(
            contractAddress: String,
            tokenId: String,
            chainType: CAIPChainType,
            standard: String = "erc721",
            network: String = "mainnet"
        ): CAIPAsset {
            val chainId = CAIPChainID.fromChainType(chainType, network)
            return CAIPAsset(
                chainId = chainId,
                assetNamespace = standard,
                assetReference = "${contractAddress.lowercase()}/$tokenId"
            )
        }

        /**
         * Create an SPL token asset (Solana)
         */
        fun createSPLTokenAsset(
            mintAddress: String,
            network: String = "mainnet"
        ): CAIPAsset {
            val chainId = CAIPChainID.fromChainType(CAIPChainType.SOLANA, network)
            return CAIPAsset(
                chainId = chainId,
                assetNamespace = "spl-token",
                assetReference = mintAddress
            )
        }
    }
}

/**
 * CAIP Transaction Status
 */
enum class CAIPTransactionStatus {
    PENDING,
    CONFIRMED,
    FAILED,
    CANCELLED
}

/**
 * CAIP Address Type
 */
enum class CAIPAddressType {
    EOA,            // Externally Owned Account
    CONTRACT,       // Smart Contract
    MULTISIG,       // Multi-signature wallet
    VALIDATOR,      // Validator node
    SYSTEM,         // System account
    UNKNOWN
}

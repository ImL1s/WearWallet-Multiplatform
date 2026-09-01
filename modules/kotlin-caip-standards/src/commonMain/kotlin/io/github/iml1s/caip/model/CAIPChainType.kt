package io.github.iml1s.caip.model

/**
 * Supported blockchain chain types for CAIP standards.
 *
 * This enum provides a simplified representation of blockchain networks
 * compatible with CAIP (Chain Agnostic Improvement Proposals) standards.
 *
 * Each chain type includes:
 * - CAIP namespace (e.g., "eip155" for EVM chains)
 * - Symbol (e.g., "ETH" for Ethereum)
 * - SLIP-44 coin type for BIP-44 derivation paths
 */
enum class CAIPChainType(
    val namespace: String,
    val symbol: String,
    val slip44CoinType: Int,
    val displayName: String
) {
    // EVM-compatible chains (eip155 namespace)
    ETHEREUM("eip155", "ETH", 60, "Ethereum"),
    BSC("eip155", "BNB", 60, "BNB Smart Chain"),
    POLYGON("eip155", "MATIC", 60, "Polygon"),
    AVALANCHE("eip155", "AVAX", 60, "Avalanche"),
    ARBITRUM("eip155", "ETH", 60, "Arbitrum"),
    OPTIMISM("eip155", "ETH", 60, "Optimism"),
    CRONOS("eip155", "CRO", 60, "Cronos"),
    BASE("eip155", "ETH", 60, "Base"),
    FANTOM("eip155", "FTM", 60, "Fantom"),
    CELO("eip155", "CELO", 60, "Celo"),
    MOONBEAM("eip155", "GLMR", 60, "Moonbeam"),

    // Bitcoin-compatible chains (bip122 namespace)
    BITCOIN("bip122", "BTC", 0, "Bitcoin"),
    BITCOIN_CASH("bip122", "BCH", 145, "Bitcoin Cash"),
    LITECOIN("bip122", "LTC", 2, "Litecoin"),
    DOGECOIN("bip122", "DOGE", 3, "Dogecoin"),

    // Other chain namespaces
    SOLANA("solana", "SOL", 501, "Solana"),
    POLKADOT("polkadot", "DOT", 354, "Polkadot"),
    CARDANO("cardano", "ADA", 1815, "Cardano"),
    TRON("tron", "TRX", 195, "TRON"),
    MONERO("monero", "XMR", 128, "Monero");

    companion object {
        /**
         * Get chain type by namespace
         */
        fun fromNamespace(namespace: String): List<CAIPChainType> {
            return entries.filter { it.namespace == namespace }
        }

        /**
         * Get chain type by symbol
         */
        fun fromSymbol(symbol: String): CAIPChainType? {
            return entries.find { it.symbol.equals(symbol, ignoreCase = true) }
        }

        /**
         * Get chain type by SLIP-44 coin type
         */
        fun fromSlip44(coinType: Int): List<CAIPChainType> {
            return entries.filter { it.slip44CoinType == coinType }
        }

        /**
         * Check if a namespace is supported
         */
        fun isNamespaceSupported(namespace: String): Boolean {
            return entries.any { it.namespace == namespace }
        }

        /**
         * Get all supported namespaces
         */
        fun getSupportedNamespaces(): Set<String> {
            return entries.map { it.namespace }.toSet()
        }

        /**
         * Get all EVM-compatible chain types
         */
        fun getEVMChains(): List<CAIPChainType> {
            return entries.filter { it.namespace == "eip155" }
        }

        /**
         * Get all Bitcoin-compatible chain types
         */
        fun getBitcoinChains(): List<CAIPChainType> {
            return entries.filter { it.namespace == "bip122" }
        }
    }
}

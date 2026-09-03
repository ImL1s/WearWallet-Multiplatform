package com.cbstudio.wearwallet.core.rango.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Rango Metadata API Response Models
 * 
 * These models represent the response from /basic/meta endpoint
 * which provides all supported blockchains, tokens, and swappers.
 */

// ============================================
// Main Metadata Response
// ============================================

@Serializable
data class RangoMetaResponse(
    val blockchains: List<RangoBlockchain> = emptyList(),
    val tokens: List<RangoTokenMeta> = emptyList(),
    val swappers: List<RangoSwapperMeta> = emptyList()
)

// ============================================
// Blockchain Models
// ============================================

@Serializable
data class RangoBlockchain(
    val name: String,                         // "BSC" - used for API calls
    val displayName: String = "",             // "BNB Smart Chain"
    val shortName: String = "",               // "BSC"
    val chainId: String? = null,              // "56" for BSC
    val type: String = "",                    // "EVM", "COSMOS", "TRANSFER"
    val enabled: Boolean = true,              // Whether chain is available
    val logo: String? = null,                 // Icon URL
    val color: String? = null,                // Brand color (#F0B90B)
    val defaultDecimals: Int = 18,            // Default decimal places
    val sort: Int = 0,                        // Sort order
    val feeAssets: List<RangoFeeAsset> = emptyList(),   // Gas tokens
    val addressPatterns: List<String> = emptyList(),     // Address regex patterns
    val info: RangoBlockchainInfo? = null     // Explorer and RPC info
)

@Serializable
data class RangoFeeAsset(
    val blockchain: String = "",
    val symbol: String = "",
    val address: String? = null
)

@Serializable
data class RangoBlockchainInfo(
    val infoType: String? = null,             // "EvmMetaInfo", "CosmosMetaInfo"
    val chainName: String? = null,            // "BSC Mainnet"
    val blockExplorerUrls: List<String> = emptyList(),
    val addressUrl: String? = null,           // "https://bscscan.com/address/{wallet}"
    val transactionUrl: String? = null,       // "https://bscscan.com/tx/{txHash}"
    val rpcUrls: List<String> = emptyList(),
    val nativeCurrency: RangoNativeCurrency? = null
)

@Serializable
data class RangoNativeCurrency(
    val name: String = "",
    val symbol: String = "",
    val decimals: Int = 18
)

// ============================================
// Token Models
// ============================================

@Serializable
data class RangoTokenMeta(
    val blockchain: String = "",              // "BSC"
    val symbol: String = "",                  // "USDT"
    val name: String? = null,                 // "Tether USD"
    val address: String? = null,              // Contract address (null = native)
    val decimals: Int = 18,                   // Token decimals
    val chainId: String? = null,              // "56"
    val isPopular: Boolean = false,           // Is popular token
    val image: String? = null,                // Icon URL
    val blockchainImage: String? = null,      // Blockchain icon
    val usdPrice: Double? = null,             // Current USD price
    val supportedSwappers: List<String> = emptyList()  // Supported swapper IDs
) {
    /**
     * Check if this is a native token (no contract address)
     */
    val isNative: Boolean
        get() = address.isNullOrEmpty()
    
    /**
     * Format as Rango asset string
     * Native: "BSC.BNB"
     * Token: "BSC--0x..."
     */
    fun toAssetString(): String {
        return if (isNative) {
            "$blockchain.$symbol"
        } else {
            "$blockchain--$address"
        }
    }
}

// ============================================
// Swapper (DEX/Bridge) Models
// ============================================

@Serializable
data class RangoSwapperMeta(
    val id: String = "",                      // "OneInchBsc"
    val title: String = "",                   // "1Inch BSC"
    val logo: String? = null,                 // Icon URL
    val swapperGroup: String? = null,         // "1Inch"
    val types: List<String> = emptyList(),    // ["DEX"] or ["BRIDGE"]
    val enabled: Boolean = true               // Whether swapper is available
) {
    val isDex: Boolean
        get() = types.contains("DEX")
    
    val isBridge: Boolean
        get() = types.contains("BRIDGE")
}

// ============================================
// Metadata Request Parameters
// ============================================

data class RangoMetaRequest(
    val blockchains: List<String>? = null,    // Filter to specific chains
    val blockchainsExclude: Boolean = false,  // Exclude specified chains
    val swappers: List<String>? = null,       // Filter to specific swappers
    val swappersExclude: Boolean = false,     // Exclude specified swappers
    val transactionTypes: List<String>? = null, // "EVM", "COSMOS", "TRANSFER"
    val excludeNonPopulars: Boolean = false,  // Only popular tokens
    val excludeSecondaries: Boolean = false   // Exclude secondary token lists
)

// ============================================
// Convenience Extensions
// ============================================

/**
 * Filter blockchains by type
 */
fun List<RangoBlockchain>.filterByType(type: String): List<RangoBlockchain> {
    return filter { it.type.equals(type, ignoreCase = true) }
}

/**
 * Get only enabled blockchains
 */
fun List<RangoBlockchain>.enabledOnly(): List<RangoBlockchain> {
    return filter { it.enabled }
}

/**
 * Get only EVM chains
 */
fun List<RangoBlockchain>.evmChains(): List<RangoBlockchain> {
    return filterByType("EVM")
}

/**
 * Find blockchain by name
 */
fun List<RangoBlockchain>.findByName(name: String): RangoBlockchain? {
    return find { it.name.equals(name, ignoreCase = true) }
}

/**
 * Find blockchain by chainId
 */
fun List<RangoBlockchain>.findByChainId(chainId: String): RangoBlockchain? {
    return find { it.chainId == chainId }
}

/**
 * Get tokens for a specific blockchain
 */
fun List<RangoTokenMeta>.forBlockchain(blockchain: String): List<RangoTokenMeta> {
    return filter { it.blockchain.equals(blockchain, ignoreCase = true) }
}

/**
 * Get popular tokens only
 */
fun List<RangoTokenMeta>.popularOnly(): List<RangoTokenMeta> {
    return filter { it.isPopular }
}

/**
 * Find token by address
 */
fun List<RangoTokenMeta>.findByAddress(blockchain: String, address: String?): RangoTokenMeta? {
    return find { 
        it.blockchain.equals(blockchain, ignoreCase = true) && 
        it.address.equals(address, ignoreCase = true)
    }
}

/**
 * Find native token for blockchain
 */
fun List<RangoTokenMeta>.findNativeToken(blockchain: String): RangoTokenMeta? {
    return find { 
        it.blockchain.equals(blockchain, ignoreCase = true) && it.isNative
    }
}

package com.cbstudio.wearwallet.core.rango.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Rango Quote Response
 * 
 * Response from /basic/quote endpoint.
 */
@Serializable
data class RangoQuoteResponse(
    val requestId: String = "",
    val resultType: String = "", // OK, HIGH_IMPACT, INPUT_LIMIT_ISSUE, NO_ROUTE
    val route: RangoRoute? = null,
    val error: String? = null,
    val errorCode: String? = null,
    val traceId: String? = null
)

@Serializable
data class RangoRoute(
    val outputAmount: String? = null,
    val outputAmountMin: String? = null,
    val outputAmountUsd: Double? = null,
    val feeUsd: Double? = null,
    val estimatedTimeInSeconds: Int? = null,
    val swapper: RangoSwapper? = null,
    val path: List<RangoPathStep>? = null,
    val fee: List<RangoFee>? = null,
    val amountRestriction: RangoAmountRestriction? = null,
    val from: RangoAsset? = null,
    val to: RangoAsset? = null
)

@Serializable
data class RangoAsset(
    val blockchain: String? = null,
    val address: String? = null,
    val symbol: String? = null,
    val name: String? = null,
    val decimals: Int? = null,
    val image: String? = null,
    val usdPrice: Double? = null
)

@Serializable
data class RangoSwapper(
    val id: String = "",
    val title: String = "",
    val logo: String? = null,
    val swapperGroup: String? = null,
    val types: List<String>? = null
)

@Serializable
data class RangoPathStep(
    val from: RangoAsset? = null,
    val to: RangoAsset? = null,
    val swapperId: String? = null,
    val swapperType: String? = null,
    val expectedOutput: String? = null
)

@Serializable
data class RangoFee(
    val name: String? = null,
    val token: RangoAsset? = null,
    val expenseType: String? = null, // FROM_SOURCE_WALLET, DECREASE_FROM_OUTPUT
    val amount: String? = null,
    val price: Double? = null
)

@Serializable
data class RangoAmountRestriction(
    val min: String? = null,
    val max: String? = null,
    val type: String? = null // INCLUSIVE, EXCLUSIVE
)

/**
 * Rango Swap Response
 * 
 * Response from /basic/swap endpoint.
 */
@Serializable
data class RangoSwapResponse(
    val requestId: String? = null,
    val resultType: String? = null,
    val route: RangoRoute? = null,
    val error: String? = null,
    @SerialName("tx")
    val transaction: RangoTransaction? = null
)

@Serializable
data class RangoTransaction(
    val type: String? = null, // EVM, COSMOS, TRANSFER, etc.
    val blockChain: String? = null,
    @SerialName("txTo")
    val to: String? = null,
    @SerialName("txData")
    val data: String? = null,
    val value: String? = null,
    val gasLimit: String? = null,
    val gasPrice: String? = null,
    val maxFeePerGas: String? = null,
    val maxPriorityFeePerGas: String? = null,
    val nonce: String? = null,
    val approveTo: String? = null,
    val approveData: String? = null,
    val isApprovalTx: Boolean? = null
)

/**
 * Rango Status Response
 * 
 * Response from /basic/status endpoint.
 */
@Serializable
data class RangoStatusResponse(
    val status: String? = null, // running, success, failed
    val error: String? = null,
    val bridgeData: RangoBridgeData? = null,
    val output: RangoAsset? = null,
    val explorerUrl: List<RangoExplorerUrl>? = null
)

@Serializable
data class RangoBridgeData(
    val srcChainId: String? = null,
    val destChainId: String? = null,
    val srcTxHash: String? = null,
    val destTxHash: String? = null
)

@Serializable
data class RangoExplorerUrl(
    val url: String? = null,
    val description: String? = null
)

// Legacy models for compatibility
@Serializable
data class RangoToken(
    val symbol: String = "",
    val address: String? = null,
    val decimals: Int = 18,
    val img: String? = null,
    val chainId: String? = null,
    val name: String? = null,
    val isNative: Boolean = false
)

@Serializable
data class RangoQuoteRequest(
    val from: RangoToken,
    val to: RangoToken,
    val amount: String,
    val slippage: Double = 1.0,
    val affiliateRef: String? = null,
    val affiliatePercent: Double? = null,
    val affiliateWallets: Map<String, String>? = null
)

@Serializable
data class RangoSwapRequest(
    val requestId: String,
    val step: Int = 1
)
